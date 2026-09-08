#!/usr/bin/env python3
"""Pure tests in a disposable full input copy; never native/runtime evidence."""
import ast
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
BINDING = CAMPAIGN / 'ios-readiness-source-06.json'
DRAFT = CAMPAIGN / 'drafts/l08-save-diagnostics-01'
PREFIX = 'scripts/verification/ios-readiness/'
EXPECTED_DRAFT = {
    'L08StorageProbe.kt.in': '28f4158cc701f1355a9320da1edd9a6d7825cf76828b2b0472ea194611750b75',
    'l08_receipts.py': '3cab6914886db081ba883629242d33a262158c0106e8658bc7b777387826f867',
    'test_l08_diagnostics.py': '97d6bb76316151b850e2e85ed84aac5524743184a78c499fb9b2c7db1bf9980a',
}
MODULES = ('test_l08_receipts', 'test_l08_host_receipts', 'test_l08_copy', 'test_l08_diagnostics')
EXPECTED_TESTS = 43


def digest(path):
    if path.is_symlink() or path.resolve(strict=True) != path.absolute() or not path.is_file():
        raise RuntimeError('Input is not an exact nonsymlink regular file')
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tests_in(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from tests_in(item)
        else:
            yield item


def main():
    if len(sys.argv) != 1:
        raise RuntimeError('Frozen pure driver takes no arguments')
    if digest(BINDING) != '1d7165a7029165a5450919c2771e09d51f206d124e41b082b52eec22f76b73fa':
        raise RuntimeError('Historical input binding changed')
    binding = json.loads(BINDING.read_text())
    originals = {row['path']: row['sha256'] for row in binding['copy_only']}
    if len(originals) != 738:
        raise RuntimeError('Unexpected bound input inventory')
    for path, expected in originals.items():
        pure = PurePosixPath(path)
        if pure.is_absolute() or '..' in pure.parts or str(pure) != path or digest(ROOT / path) != expected:
            raise RuntimeError('Current input differs from the explicit historical copy')
    for name, expected in EXPECTED_DRAFT.items():
        if digest(DRAFT / name) != expected:
            raise RuntimeError('Unreviewed diagnostic draft')
    expected_copy = dict(originals)
    expected_copy.update({PREFIX + name: value for name, value in EXPECTED_DRAFT.items()})
    with tempfile.TemporaryDirectory(prefix='parlor-l08-diagnostics-pure-') as raw:
        copied_root = Path(raw).resolve(strict=True)
        for path in originals:
            target = copied_root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / path, target)
        for name in EXPECTED_DRAFT:
            shutil.copyfile(DRAFT / name, copied_root / PREFIX / name)
        if {path: digest(copied_root / path) for path in expected_copy} != expected_copy:
            raise RuntimeError('Copied test inputs do not match reviewed bytes')
        controls = copied_root / PREFIX
        declared = sorted(module + '.' + node.name + '.' + method.name
                          for module in MODULES
                          for node in ast.parse((controls / (module + '.py')).read_text()).body
                          if isinstance(node, ast.ClassDef)
                          for method in node.body
                          if isinstance(method, ast.FunctionDef) and method.name.startswith('test_'))
        sys.path.insert(0, str(controls))
        suite = unittest.defaultTestLoader.loadTestsFromNames(MODULES)
        actual = sorted(test.id() for test in tests_in(suite))
        imports = {name: str(Path(module.__file__).resolve().relative_to(copied_root))
                   for name, module in sys.modules.items() if getattr(module, '__file__', None)
                   and Path(module.__file__).resolve().is_relative_to(copied_root)}
        if (len(actual) != EXPECTED_TESTS or actual != declared or len(set(actual)) != EXPECTED_TESTS
                or not imports or not set(imports.values()) <= set(expected_copy)
                or any(imports.get(name) != PREFIX + name + '.py' for name in (*MODULES, 'l08_receipts'))):
            raise RuntimeError('Actual discovery or copied imports are not bound')
        print(json.dumps(dict(kind='PURE_CONTROL_TESTS_NOT_APP_RUNTIME', expected_tests=EXPECTED_TESTS,
                              copied_inputs=expected_copy, imported_modules=imports,
                              declared_ids=declared, discovered_ids=actual)), flush=True)
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        unchanged = all(digest(copied_root / path) == value for path, value in expected_copy.items())
        originals_unchanged = all(digest(ROOT / path) == value for path, value in originals.items())
        success = (result.wasSuccessful() and result.testsRun == EXPECTED_TESTS
                   and not result.skipped and unchanged and originals_unchanged)
        print(json.dumps(dict(tests=result.testsRun, failures=len(result.failures),
                              errors=len(result.errors), skips=len(result.skipped),
                              copied_inputs_unchanged=unchanged, original_inputs_unchanged=originals_unchanged)), flush=True)
    print(json.dumps(dict(exact_owned_copy_removed=not copied_root.exists())), flush=True)
    return 0 if success and not copied_root.exists() else 1


if __name__ == '__main__':
    raise SystemExit(main())
