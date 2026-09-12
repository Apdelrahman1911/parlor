#!/usr/bin/env python3
"""Brackets current standalone controls, actual discovery, imported bytes, and outcomes."""
from pathlib import Path
import ast
import hashlib
import json
import sys
import unittest

ROOT = Path(__file__).resolve().parents[3]
DIRECTORY = ROOT / 'scripts/verification/ios-readiness'

def fingerprint():
    files = sorted(p for p in DIRECTORY.iterdir() if p.is_file() and p.suffix in {'.py', '.in', '.md', '.json'})
    if any(p.is_symlink() for p in files):
        raise RuntimeError('Redirected control')
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}

def flatten(suite):
    for test in suite:
        if isinstance(test, unittest.TestSuite):
            yield from flatten(test)
        else:
            yield test.id()

before = fingerprint()
expected = set()
for path in sorted(DIRECTORY.glob('test_*.py')):
    for node in ast.parse(path.read_text()).body:
        if isinstance(node, ast.ClassDef):
            for item in node.body:
                if isinstance(item, ast.FunctionDef) and item.name.startswith('test_'):
                    expected.add(f'{path.stem}.{node.name}.{item.name}')
if len(expected) != 98:
    raise RuntimeError('Unreviewed declared test count')
sys.path.insert(0, str(DIRECTORY))
suite = unittest.defaultTestLoader.discover(str(DIRECTORY), pattern='test_*.py')
actual = list(flatten(suite))
if len(actual) != 98 or set(actual) != expected:
    raise RuntimeError('Missing, duplicate or unreviewed actual tests')
imports = {name: str(Path(module.__file__).resolve()) for name, module in sys.modules.items()
           if getattr(module, '__file__', None) and Path(module.__file__).resolve().parent == DIRECTORY}
if any(imports.get(path.stem) != str(path) for path in DIRECTORY.glob('test_*.py')):
    raise RuntimeError('Imported test origin drift')
print(json.dumps({'before': before, 'imports': imports, 'actual_ids': actual}, sort_keys=True), flush=True)
result = unittest.TextTestRunner(verbosity=2).run(suite)
after = fingerprint()
print(json.dumps({'after': after, 'unchanged': before == after, 'tests': result.testsRun,
                  'failures': len(result.failures), 'errors': len(result.errors),
                  'skips': len(result.skipped)}, sort_keys=True), flush=True)
raise SystemExit(0 if result.wasSuccessful() and result.testsRun == 98 and not result.skipped and before == after else 1)
