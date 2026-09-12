#!/usr/bin/env python3
"""Bounded pure-control test batch. No app, Gradle or native tool execution."""
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
PATHS = [
    'scripts/release/tests/test_third_party_notices.py',
    'scripts/release/tests/test_notice_git_bytes.py',
    'scripts/release/tests/test_android_release_artifacts.py',
    'scripts/release/tests/test_ios_release_artifacts.py',
    'scripts/release/tests/test_workflow_contract.py',
    'remediation-runs/2026-09-07-local-readiness/reviews/test_aab_package_inspection_02.py',
    'remediation-runs/2026-09-07-local-readiness/reviews/test_build_lane_group_retirement.py',
]


def flatten(suite):
    for test in suite:
        if isinstance(test, unittest.TestSuite):
            yield from flatten(test)
        else:
            yield test


def main():
    driver = Path(__file__).resolve()
    driver_hash = hashlib.sha256(driver.read_bytes()).hexdigest()
    manifests, suite = [], unittest.TestSuite()
    for index, relative in enumerate(PATHS):
        path = ROOT / relative
        code = path.read_bytes()
        parsed = ast.parse(code, filename=relative)
        module_name = 'parlor_package_control_' + str(index)
        declarations = [module_name + '.' + node.name + '.' + method.name
                        for node in parsed.body if isinstance(node, ast.ClassDef)
                        for method in node.body if isinstance(method, ast.FunctionDef) and method.name.startswith('test_')]
        spec = importlib.util.spec_from_file_location(module_name, path)
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        spec.loader.exec_module(module)
        tests = unittest.defaultTestLoader.loadTestsFromModule(module)
        actual = [test.id() for test in flatten(tests)]
        if not declarations or len(set(declarations)) != len(declarations) or sorted(declarations) != sorted(actual):
            raise RuntimeError('Discovered tests differ from reviewed signatures: ' + relative)
        manifests.append({'path': relative, 'sha256': hashlib.sha256(code).hexdigest(), 'tests': sorted(actual)})
        suite.addTests(tests)
    print(json.dumps({'kind': 'PURE_CONTROL_TESTS_NOT_NATIVE_RUNTIME', 'inputs': manifests,
                      'driver_path': str(driver.relative_to(ROOT)), 'driver_sha256': driver_hash}, sort_keys=True), flush=True)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    unchanged = all(hashlib.sha256((ROOT / row['path']).read_bytes()).hexdigest() == row['sha256'] for row in manifests)
    driver_unchanged = hashlib.sha256(driver.read_bytes()).hexdigest() == driver_hash
    value = {'tests': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors),
             'skips': len(result.skipped), 'source_unchanged': unchanged, 'driver_unchanged': driver_unchanged}
    print(json.dumps(value, sort_keys=True), flush=True)
    return 0 if result.wasSuccessful() and not result.skipped and unchanged and driver_unchanged else 1


if __name__ == '__main__':
    raise SystemExit(main())
