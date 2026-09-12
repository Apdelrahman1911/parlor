#!/usr/bin/env python3
"""Bounded real-Darwin probe; never starts adb, Gradle, or an emulator."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

BASE = Path(__file__).resolve().parents[1]
CONTROLS = BASE / 'evidence/release-content-followup/android-arm64-runner'
sys.path.insert(0, str(CONTROLS))
from darwin_owned_processes import OwnedProcesses

INTERRUPTS = []


def request_cancel(signum, _frame):
    # A signal must not split Popen creation from ownership registration or
    # interrupt the bounded cleanup/receipt path on a repeated cancellation.
    INTERRUPTS.append(signum)


def main():
    signal.signal(signal.SIGTERM, request_cancel)
    signal.signal(signal.SIGINT, request_cancel)
    destination = BASE / 'evidence/android-native-ownership-probe-01'
    destination.mkdir(exist_ok=False)
    receipt = {'started_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'status': 'FAIL', 'scenarios': [], 'pid': os.getpid(),
               'probe_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               'control_sha256': hashlib.sha256((CONTROLS / 'darwin_owned_processes.py').read_bytes()).hexdigest()}
    registry = None
    last_spawn = None
    try:
        # Backend read-only capability/ABI check happens BEFORE any child exists.
        registry = OwnedProcesses()
        # Every launched fixture self-terminates within fifteen seconds even if
        # native ownership acquisition is denied. No numeric-signal fallback.
        commands = [
            ('ordinary', [sys.executable, '-B', '-c', 'import time; time.sleep(12)']),
            ('exec', [sys.executable, '-B', '-c',
                      'import os,time; time.sleep(.2); os.execl("/bin/sleep","sleep","12")']),
            ('detached-child', [sys.executable, '-B', '-c',
                               'import subprocess,time; subprocess.Popen(["/bin/sleep","12"],'
                               'start_new_session=True); time.sleep(2)']),
        ]
        for name, command in commands:
            if INTERRUPTS:
                raise InterruptedError('Cancellation before owned spawn')
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                       stderr=subprocess.DEVNULL, start_new_session=True)
            last_spawn = time.monotonic()
            launch = registry.register(process, name, command)
            deadline = time.monotonic() + 1.2
            while time.monotonic() < deadline:
                if INTERRUPTS:
                    raise InterruptedError('Cancellation during owned probe')
                registry.refresh()
                time.sleep(.05)
            before = registry.live(launch.pid)
            if name == 'detached-child':
                assert any(row.pid != launch.pid and row.group != launch.pid for row in before), before
            elif name == 'exec':
                assert any(row.pid == launch.pid and row.command == '/bin/sleep' for row in before), before
            else:
                assert any(row.pid == launch.pid for row in before), before
            registry.stop(launch, term_seconds=3, kill_seconds=3)
            assert launch.retired and process.returncode is not None
            assert not registry.live(launch.pid)
            receipt['scenarios'].append({'name': name, 'status': 'PASS', 'pid': launch.pid,
                                         'attested_pids': [row.pid for row in before],
                                         'exit_code': process.returncode})
        receipt['status'] = 'PASS'
    except BaseException as error:
        receipt['failure'] = f'{type(error).__name__}: {error}'
    finally:
        if registry is not None:
            try:
                receipt['cleanup_errors'] = registry.shutdown()
                receipt['remaining_owned_workers'] = [row.pid for row in registry.live()]
                receipt['signals'] = registry.events
                receipt['launches'] = registry.receipts()
                if receipt['cleanup_errors'] or receipt['remaining_owned_workers']:
                    receipt['status'] = 'FAIL'
            except BaseException as error:
                receipt['status'] = 'FAIL'
                receipt['cleanup_failure'] = f'{type(error).__name__}: {error}'
            if receipt['status'] == 'FAIL' and last_spawn is not None:
                # Keep failed attestation honest, but outlive the independent
                # self-exit deadlines before returning control to another lane.
                time.sleep(max(0, last_spawn + 16 - time.monotonic()))
                try:
                    receipt['after_deadline_cleanup_errors'] = registry.shutdown()
                    receipt['after_deadline_workers'] = [row.pid for row in registry.live()]
                except BaseException as error:
                    receipt['after_deadline_failure'] = f'{type(error).__name__}: {error}'
        receipt['finished_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        receipt['interrupts'] = INTERRUPTS
        if INTERRUPTS:
            receipt['status'] = 'FAIL'
        (destination / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))
    return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
