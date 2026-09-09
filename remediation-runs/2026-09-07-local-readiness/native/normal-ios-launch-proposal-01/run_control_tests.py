#!/usr/bin/env python3
"""Root-only control driver, including synthetic C compilation and fixtures.

No Darwin ABI, real process query, application, simulator, or Store proof is produced."""
import ast
import json
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import run_normal_ios_launch as control

EXPECTED_COUNT = 204


def declared_tests():
    ids = []
    for path in sorted(HERE.glob('test_*.py')):
        parsed = ast.parse(path.read_text(), filename=str(path))
        for node in parsed.body:
            if isinstance(node, ast.ClassDef):
                for method in node.body:
                    if isinstance(method, ast.FunctionDef) and method.name.startswith('test_'):
                        ids.append(path.stem + '.' + node.name + '.' + method.name)
    return sorted(ids)


def tests_in(suite):
    for entry in suite:
        if isinstance(entry, unittest.TestSuite):
            yield from tests_in(entry)
        else:
            yield entry


def main():
    if len(sys.argv) != 1:
        raise RuntimeError('This frozen pure driver takes no runtime arguments')
    before = control.control_manifest()
    declared = declared_tests()
    suite = unittest.defaultTestLoader.discover(str(HERE), pattern='test_*.py')
    actual = sorted(test.id() for test in tests_in(suite))
    if len(actual) != EXPECTED_COUNT or actual != declared or len(set(actual)) != len(actual):
        raise RuntimeError('Discovery differs from the frozen count or actual source signatures')
    imports = {name: str(Path(module.__file__).resolve()) for name, module in sys.modules.items()
               if getattr(module, '__file__', None) and
               Path(module.__file__).resolve().parent in (HERE, control.SUPPORT)}
    bound = {str(control.ROOT / row['path']) for row in before}
    if not imports or not set(imports.values()) <= bound:
        raise RuntimeError('Imported control module is not bound to its reviewed bytes')
    print(json.dumps(dict(kind='SYNTHETIC_CONTROL_TESTS_NOT_APP_RUNTIME', before=before,
                          actual_imports=imports, declared_ids=declared, discovered_ids=actual,
                          expected_tests=EXPECTED_COUNT), sort_keys=True), flush=True)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    after = control.control_manifest()
    print(json.dumps(dict(kind='SYNTHETIC_CONTROL_TESTS_NOT_APP_RUNTIME', after=after,
                          source_unchanged=before == after, tests=result.testsRun,
                          failures=len(result.failures), errors=len(result.errors),
                          skips=len(result.skipped)), sort_keys=True), flush=True)
    return 0 if (result.wasSuccessful() and result.testsRun == EXPECTED_COUNT and
                 not result.skipped and before == after) else 1


if __name__ == '__main__':
    raise SystemExit(main())
