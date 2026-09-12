#!/usr/bin/env python3
"""Root-only pure controls, with one creation-attested external temporary parent."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import stat
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
DRAFT = HERE.parent / 'l08-app-foundation-draft-01'
EXPECTED_TESTS = 55


def external_parent():
    if sys.platform not in ('darwin', 'linux'):
        raise RuntimeError('Only reviewed Darwin/Linux pure-control hosts are supported')
    path = Path('/private/tmp' if sys.platform == 'darwin' else '/tmp')
    canonical = path.resolve(strict=True)
    if not canonical.is_dir() or HERE.parents[3] == canonical or HERE.parents[3] in canonical.parents:
        raise RuntimeError('Pure-control temporary parent must be an external directory')
    return canonical


def with_external_tmp(work):
    """No native tools/workers. Always restore environment and remove only this allocation."""
    parent = None
    custody = None
    previous_env, previous_cache = os.environ.get('TMPDIR'), tempfile.tempdir
    selected_parent = external_parent()
    try:
        mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
        try:
            parent = Path(tempfile.mkdtemp(prefix='parlor-app-foundation-controls-', dir=selected_parent))
            value = parent.lstat()  # Immediately after exclusive allocation, before any further work.
            custody = dict(device=value.st_dev, inode=value.st_ino, uid=value.st_uid)
            if not stat.S_ISDIR(value.st_mode) or value.st_uid != os.getuid() or stat.S_IMODE(value.st_mode) != 0o700:
                raise RuntimeError('Unexpected newly allocated control parent')
            canonical = parent.resolve(strict=True)
            after = canonical.lstat()
            if canonical != parent or (after.st_dev, after.st_ino, after.st_uid) != (value.st_dev, value.st_ino, value.st_uid):
                raise RuntimeError('Allocated control parent changed during canonicalization')
            print('PARLOR_APP_FOUNDATION_CONTROL_ALLOCATION ' + json.dumps(dict(path=str(parent), **custody)), flush=True)
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, mask)
        scratch = parent / 'tmp'
        scratch.mkdir(mode=0o700)
        os.environ['TMPDIR'] = str(scratch) + '/'
        tempfile.tempdir = str(scratch)
        return work(scratch)
    finally:
        mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
        cleanup = dict(status='NOT_ALLOCATED', parent=None if parent is None else str(parent), workers_started=0)
        try:
            if previous_env is None:
                os.environ.pop('TMPDIR', None)
            else:
                os.environ['TMPDIR'] = previous_env
            tempfile.tempdir = previous_cache
            if parent is not None:
                value = parent.lstat()
                if (custody is None or parent.is_symlink() or parent.resolve() != parent or
                        parent.parent != selected_parent or not parent.name.startswith('parlor-app-foundation-controls-') or
                        not stat.S_ISDIR(value.st_mode) or stat.S_IMODE(value.st_mode) != 0o700 or
                        dict(device=value.st_dev, inode=value.st_ino, uid=value.st_uid) != custody):
                    raise RuntimeError('Control-parent ownership changed; retain rather than adopt/delete')
                shutil.rmtree(parent)  # Exact fresh inode, never another task/profile/prefix match.
                if parent.exists() or parent.is_symlink():
                    raise RuntimeError('Owned external control parent remains')
                cleanup.update(status='PASS', parent_removed=True, custody=custody)
        except BaseException as error:
            cleanup.update(status='FAIL', error_type=type(error).__name__)
            raise
        finally:
            print('PARLOR_APP_FOUNDATION_CONTROL_CLEANUP ' + json.dumps(cleanup), flush=True)
            signal.pthread_sigmask(signal.SIG_SETMASK, mask)


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def run(_scratch):
    paths = [('l08_app_foundation_draft_controls', DRAFT / 'test_app_foundation.py'),
             ('l08_app_foundation_composition_controls', HERE / 'test_composition.py')]
    modules = [load_module(name, path) for name, path in paths]
    suite = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromModule(module) for module in modules)
    def identifiers(node):
        return [identifier for test in node for identifier in (identifiers(test) if isinstance(test, unittest.TestSuite) else [test.id()])]
    discovered = identifiers(suite)
    if len(discovered) != EXPECTED_TESTS or len(set(discovered)) != EXPECTED_TESTS:
        raise RuntimeError('Frozen Foundation/composition discovery differs from reviewed 55')
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print('PARLOR_APP_FOUNDATION_CONTROL_RESULTS ' + json.dumps(dict(
        discovered=discovered, tests_run=result.testsRun, failures=len(result.failures), errors=len(result.errors),
        skipped=[dict(test=test.id(), reason=reason) for test, reason in result.skipped],
        input_tests=[dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for _, path in paths],
        native_execution=False)), flush=True)
    return 0 if result.wasSuccessful() and result.testsRun == EXPECTED_TESTS and not result.skipped else 1


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(with_external_tmp(run))
