#!/usr/bin/env python3
"""Fixed focused control packet with explicit input hashes; no application proof.

Run only through the existing coordinated lane. Each child is a reviewed control
driver, not a native/build entry point. The outer lane owns stop/worker cleanup.
"""
import hashlib
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[3]
C = Path('remediation-runs/2026-09-07-local-readiness')
N = Path('remediation-runs/2026-09-08-continuation')
DIRECTORIES = (
    C / 'l08-storage-functional-companion-02',
    C / 'native/normal-ios-launch-proposal-01',
    C / 'native/l08-app-foundation-draft-01',
    C / 'native/l08-app-foundation-composition-01',
    Path('scripts/verification/ios-readiness'), N / 'controls',
    C / 'reviews/dependency-candidate-consumer-03',
)
EXTRA = (
    C / 'run_gradle_cycle.py',
    C / 'reviews/run_candidate_input_01.py',
    C / 'reviews/run_schema_consumer_controls_01.py',
    C / 'reviews/hosted-native-portability-01/draft-freeze-relative.json',
    C / 'reviews/l08-native-foundation-control-03/run_control.py',
    C / 'reviews/l08-native-foundation-control-03/FoundationProtectionControl.m',
    C / 'evidence/release-content-followup/android-arm64-runner/darwin_owned_processes.py',
    N / 'reviews/schema-base-prerequisites-research-01.json',
)
COMMANDS = (
    ('packet', [str(N / 'controls/test_continuation_packet.py')]),
    ('lane', [str(N / 'controls/test_gradle_lane_portability.py')]),
    ('single-tap', [str(N / 'controls/test_single_tap_fixture_review.py')]),
    ('schema-bootstrap', [str(N / 'controls/test_schema_base_bootstrap.py')]),
    ('toolchain', ['scripts/verification/ios-readiness/test_toolchain_profiles.py', '-v']),
    ('functional-copy', ['-m', 'unittest', 'discover', '-s',
        str(C / 'l08-storage-functional-companion-02'), '-p', 'test_l08_functional_copy.py', '-v']),
    ('composition', [str(C / 'native/l08-app-foundation-composition-01/run_controls.py')]),
    ('normal', [str(C / 'native/normal-ios-launch-proposal-01/run_control_tests.py')]),
    ('schema-closure', [str(N / 'controls/run_candidate_with_schema.py'), '--controls']),
)
FINALIZING = False
LAUNCHING = False
STOPPING_CHILD = False
DEFERRED_SIGNALS = []
PENDING_SIGNALS = []


def manifest():
    paths = set(EXTRA)
    for relative in DIRECTORIES:
        for path in (ROOT / relative).rglob('*'):
            if path.is_symlink():
                raise RuntimeError('Redirected control input')
            if path.is_file() and path.suffix in {'.py', '.in', '.md', '.json', '.m'}:
                paths.add(path.relative_to(ROOT))
    rows = []
    for relative in sorted(paths):
        path = ROOT / relative
        value = path.lstat()
        if path.resolve() != path or not stat.S_ISREG(value.st_mode) or value.st_size > 4 * 1024 * 1024:
            raise RuntimeError('Invalid or unbounded control input')
        rows.append({'path': str(relative), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    return rows


def write_new(path, value):
    with path.open('x') as output:
        json.dump(value, output, indent=2)
        output.write('\n')


def owned_destination():
    temporary = Path(os.environ['TMPDIR']).absolute()
    if (temporary.resolve() != temporary or temporary.name != 'tmp' or
            temporary.parent.name != 'scratch' or temporary.parents[2] != ROOT / C / 'evidence'):
        raise RuntimeError('Coordinated owned lane required')
    destination = temporary.parents[1]
    for path in (temporary, temporary.parent, destination):
        value = path.lstat()
        if not stat.S_ISDIR(value.st_mode) or value.st_uid != os.getuid() or path.resolve() != path:
            raise RuntimeError('Coordinated directory custody required')
    receipt_path = destination / 'receipt.json'
    descriptor = os.open(receipt_path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, 'rb') as source:
        value = os.fstat(source.fileno())
        if not stat.S_ISREG(value.st_mode) or value.st_uid != os.getuid() or value.st_size > 4 * 1024 * 1024:
            raise RuntimeError('Invalid running lane receipt')
        receipt = json.load(source)
    if (receipt.get('cycle') != destination.name or receipt.get('status') != 'RUNNING' or
            receipt.get('command') != ['/usr/bin/python3', '-B', str(N / 'controls/run_continuation_controls.py')]):
        raise RuntimeError('This packet must be the running lane command')
    return destination


def stop_child(process, row):
    global STOPPING_CHILD
    if process is None or process.poll() is not None:
        return
    STOPPING_CHILD = True
    try:
        row['shutdown'] = {'signal': 'SIGTERM', 'escalated': False}
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            row['shutdown']['escalated'] = True
            # Composition controls attest an external temporary parent in their
            # raw log. SIGKILL cannot run that child's finalizer; outer scratch
            # cleanup alone would not attest retirement of that allocation.
            row['shutdown']['external_cleanup_requires_inspection'] = row['name'] == 'composition'
            process.kill()
            process.wait(timeout=5)
        row['shutdown']['exit_code'] = process.returncode
    finally:
        STOPPING_CHILD = False


def run_command(destination, name, arguments, results):
    global LAUNCHING
    command = ['/usr/bin/python3', '-B', *arguments]
    row = {'name': name, 'command': command, 'log': name + '.log',
           'status': 'PENDING', 'exit_code': None}
    results.append(row)  # Preserve attempted children even if allocation/launch fails.
    process = None
    try:
        with (destination / row['log']).open('xb') as output:
            LAUNCHING = True
            try:
                process = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
            finally:
                LAUNCHING = False
            if PENDING_SIGNALS:
                raise KeyboardInterrupt('Signal during child launch')
            row['status'] = 'RUNNING'
            row['exit_code'] = process.wait(timeout=600)
            row['status'] = 'PASS' if row['exit_code'] == 0 else 'FAIL'
    except BaseException as exception:
        row['status'] = 'ERROR'
        row['error_type'] = type(exception).__name__
        raise
    finally:
        try:
            stop_child(process, row)
        except BaseException as exception:
            row['shutdown_error_type'] = type(exception).__name__
            row['status'] = 'ERROR'
            raise
        finally:
            print(json.dumps(row), flush=True)


def main():
    global FINALIZING
    if len(sys.argv) != 1 or not sys.dont_write_bytecode:
        raise RuntimeError('This fixed control packet requires -B and no arguments')
    destination = owned_destination()
    before = after = None
    results = []
    error = None
    postflight_error = None
    try:
        before = manifest()
        write_new(destination / 'control-inputs-before.json', before)
        for name, arguments in COMMANDS:
            run_command(destination, name, arguments, results)
    except BaseException as exception:
        error = type(exception).__name__
        raise
    finally:
        FINALIZING = True
        try:
            after = manifest()
            write_new(destination / 'control-inputs-after.json', after)
        except BaseException as exception:
            postflight_error = type(exception).__name__
        write_new(destination / 'focused-command-results.json', {
            'scope': 'Control tests only; not application, simulator, libproc ABI or Store proof',
            'commands': results, 'error_type': error,
            'postflight_manifest_error': postflight_error,
            'inputs_unchanged': before is not None and before == after and postflight_error is None,
            'pending_signals': PENDING_SIGNALS, 'deferred_signals': DEFERRED_SIGNALS,
        })
    return 0 if (len(results) == len(COMMANDS) and all(row['status'] == 'PASS' for row in results)
                 and before is not None and before == after and postflight_error is None
                 and not PENDING_SIGNALS and not DEFERRED_SIGNALS) else 1


if __name__ == '__main__':
    def interrupted(signum, _frame):
        if FINALIZING or STOPPING_CHILD:
            DEFERRED_SIGNALS.append(signum)
        elif LAUNCHING:
            PENDING_SIGNALS.append(signum)
        else:
            raise KeyboardInterrupt('signal ' + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(main())
