#!/usr/bin/env python3
"""Bound pure-control regressions; not native/app runtime verification."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
FOLLOWUP = CAMPAIGN / 'evidence/release-content-followup'
SUITES = {
    'android': (FOLLOWUP / 'android-arm64-runner', 61),
    'apk': (FOLLOWUP / 'android-apk-build-helper', 17),
    'rosetta': (CAMPAIGN / 'native/rosetta-desktop-controls-01', 20),
    'ios': (ROOT / 'scripts/verification/ios-readiness', 88),
}


def fingerprint(directory):
    files = sorted(path for path in directory.iterdir() if path.is_file()
                   and path.suffix in {'.py', '.in', '.json', '.md'})
    if any(path.is_symlink() for path in files):
        raise RuntimeError('Refusing a symlinked control')
    return {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}


def run(name):
    directory, expected = SUITES[name]
    before = fingerprint(directory)
    process_control = FOLLOWUP / 'android-arm64-runner/darwin_owned_processes.py'
    expected_process = '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378'
    if hashlib.sha256(process_control.read_bytes()).hexdigest() != expected_process:
        raise RuntimeError('Unreviewed shared native ownership control')
    sys.path.insert(0, str(directory))
    suite = unittest.defaultTestLoader.discover(str(directory), pattern='test_*.py')
    imports = {name: str(Path(module.__file__).resolve()) for name, module in sys.modules.items()
               if getattr(module, '__file__', None)
               and Path(module.__file__).resolve().parent == directory}
    if not imports or suite.countTestCases() != expected:
        raise RuntimeError('Missing actual imports or unexpected discovery count')
    print(json.dumps(dict(suite=name, before=before, imports=imports,
                          expected_tests=expected), sort_keys=True), flush=True)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    after = fingerprint(directory)
    print(json.dumps(dict(suite=name, after=after, source_unchanged=before == after,
                          tests=result.testsRun, failures=len(result.failures),
                          errors=len(result.errors), skips=len(result.skipped)), sort_keys=True), flush=True)
    return 0 if (result.wasSuccessful() and result.testsRun == expected
                 and not result.skipped and before == after) else 1


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--run' and sys.argv[2] in SUITES:
        raise SystemExit(run(sys.argv[2]))
    if len(sys.argv) != 1:
        raise SystemExit('Unexpected arguments')
    outcomes = {name: subprocess.run([sys.executable, '-B', str(Path(__file__).resolve()),
                                      '--run', name], check=False).returncode for name in SUITES}
    print(json.dumps(dict(pure_control_results=outcomes)), flush=True)
    raise SystemExit(0 if all(code == 0 for code in outcomes.values()) else 1)
