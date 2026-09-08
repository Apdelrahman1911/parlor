#!/usr/bin/env python3
"""Root-only pure controls; reconcile declarations, discovery and actual execution."""
import ast
import json
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import run_vmmap_capability as probe

EXPECTED_COUNT = 40


def declared_tests():
    ids = []
    for path in sorted(HERE.glob('test_*.py')):
        for node in ast.parse(path.read_text(), filename=str(path)).body:
            if isinstance(node, ast.ClassDef):
                for method in node.body:
                    if isinstance(method, ast.FunctionDef) and method.name.startswith('test_'):
                        ids.append(path.stem + '.' + node.name + '.' + method.name)
    return sorted(ids)


def tests_in(suite):
    for test in suite:
        if isinstance(test, unittest.TestSuite):
            yield from tests_in(test)
        else:
            yield test


class RecordedResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.started_ids, self.success_ids = [], []

    def startTest(self, test):
        self.started_ids.append(test.id())
        super().startTest(test)

    def addSuccess(self, test):
        self.success_ids.append(test.id())
        super().addSuccess(test)


def main():
    if len(sys.argv) != 1:
        raise RuntimeError('The frozen pure driver takes no arguments')
    before = probe.control_manifest()
    declared = declared_tests()
    suite = unittest.defaultTestLoader.discover(str(HERE), pattern='test_*.py')
    discovered = sorted(test.id() for test in tests_in(suite))
    if len(discovered) != EXPECTED_COUNT or discovered != declared or len(set(discovered)) != len(discovered):
        raise RuntimeError('AST, discovery, uniqueness, and frozen test count must agree')
    imports = {name: str(Path(module.__file__).resolve()) for name, module in sys.modules.items()
               if getattr(module, '__file__', None) and Path(module.__file__).resolve().parent == HERE}
    bound = {str(HERE / row['path']) for row in before['files']}
    if (not set(imports.values()) <= bound or
            imports.get('run_vmmap_capability') != str(HERE / 'run_vmmap_capability.py') or
            imports.get('test_vmmap_capability') != str(HERE / 'test_vmmap_capability.py')):
        raise RuntimeError('A test/control import is not bound to reviewed local bytes')
    print(json.dumps({'kind': 'PURE_VMMAP_CONTROLS_NOT_NATIVE_EVIDENCE', 'before': before,
                      'actual_imports': imports, 'declared_ids': declared,
                      'discovered_ids': discovered, 'expected_tests': EXPECTED_COUNT}, sort_keys=True), flush=True)
    result = unittest.TextTestRunner(verbosity=2, resultclass=RecordedResult).run(suite)
    after = probe.control_manifest()
    exact = sorted(result.started_ids) == discovered and sorted(result.success_ids) == discovered
    print(json.dumps({'kind': 'PURE_VMMAP_CONTROLS_NOT_NATIVE_EVIDENCE', 'after': after,
                      'controls_unchanged': before == after, 'tests': result.testsRun,
                      'executed_ids': result.started_ids, 'successful_ids': result.success_ids,
                      'failures': len(result.failures), 'errors': len(result.errors),
                      'skips': len(result.skipped), 'exact_execution': exact}, sort_keys=True), flush=True)
    return 0 if result.wasSuccessful() and not result.skipped and exact and before == after else 1


if __name__ == '__main__':
    raise SystemExit(main())
