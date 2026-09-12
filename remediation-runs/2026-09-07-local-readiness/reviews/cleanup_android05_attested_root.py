#!/usr/bin/env python3
"""One recorded failed-cycle allocation only; no PID signal or global ADB client."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
CONTROL = CAMPAIGN / 'evidence/release-content-followup/android-arm64-runner'
ROOT = Path('/private/tmp/parlor-arm64-9x6snv5r')
ATTESTATION = HERE / 'android05-leftover-readonly-attestation-01.json'
RECEIPT = HERE / 'android05-attested-cleanup-result-01.json'
EXPECTED_ROOT = (16777232, 84819622)
EXPECTED_MARKER = (16777232, 84819623)
EXPECTED_SOCKET = (16777232, 84820406)
TOKEN = '80c2edfb3a53452cb7b0ca448cf479e0'
PID = 19243


def check_path(path, expected, kind):
    info = path.lstat()
    if (path.is_symlink() or path.resolve(strict=True) != path or
            (info.st_dev, info.st_ino) != expected or info.st_uid != os.getuid() or
            not kind(info.st_mode)):
        raise RuntimeError('Recorded task allocation identity differs; preserve it')
    return info


def main():
    result = dict(started_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  status='FAIL', original_cycle_status='FAIL — preserved unchanged',
                  scope='Only recorded owned private ADB socket and its completed-cycle allocation')
    if RECEIPT.exists():
        raise RuntimeError('Never overwrite a cleanup receipt')
    try:
        for relative, expected in (
            ('owned_arm64_smoke_sdk.py', '3a425d8dafac853ac3f8c189a68e4951eb1874e79c57f458825d1145d0289991'),
            ('darwin_owned_processes.py', '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378'),
        ):
            path = CONTROL / relative
            if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise RuntimeError('Reviewed cleanup dependency changed')
        sys.path.insert(0, str(CONTROL))
        import owned_arm64_smoke_sdk as control
        import darwin_owned_processes as native
        attestation = json.loads(ATTESTATION.read_text())
        original = json.loads((CAMPAIGN / 'evidence/release-content-followup/android-runtime-05/run.json').read_text())
        if (original['temporary_root'] != str(ROOT) or
                original['device_attestation']['ro.boot.qemu.avd_name'] != 'parlor_arm64_' + TOKEN or
                attestation['expected_token'] != TOKEN or len(original['tests']) != 3):
            raise RuntimeError('Recorded allocation is not the completed synthetic runtime')
        root_info = check_path(ROOT, EXPECTED_ROOT, stat.S_ISDIR)
        marker_info = check_path(ROOT / '.parlor-owner', EXPECTED_MARKER, stat.S_ISREG)
        check_path(ROOT / 'adb.sock', EXPECTED_SOCKET, stat.S_ISSOCK)
        if root_info.st_mode & 0o077 or marker_info.st_nlink != 1 or (ROOT / '.parlor-owner').read_text() != TOKEN:
            raise RuntimeError('Private allocation or exact marker no longer agrees')
        backend = native.DarwinBackend()
        expected = attestation['process']
        observed = backend.read(PID)
        if observed is None or json.loads(json.dumps(vars(observed))) != expected:
            raise RuntimeError('Recorded private server lifetime changed; no shutdown authorized')
        owners = subprocess.run(['/usr/sbin/lsof', '-n', '-a', '-p', str(PID), '-U'],
                                capture_output=True, text=True, timeout=15)
        if owners.returncode != 0 or str(ROOT / 'adb.sock') not in owners.stdout:
            raise RuntimeError('Recorded private server no longer holds the owned socket')
        # Recheck the exact lifetime and endpoint immediately before the
        # existing reviewed direct protocol operation. It cannot autostart ADB.
        if backend.read(PID) != observed:
            raise RuntimeError('Private server lifetime changed before shutdown')
        owned = control.OwnedDirectory.__new__(control.OwnedDirectory)
        owned.path, owned.token = ROOT, TOKEN
        owned.identity, owned.marker_identity = EXPECTED_ROOT, EXPECTED_MARKER
        check_path(ROOT / 'adb.sock', EXPECTED_SOCKET, stat.S_ISSOCK)
        result['acknowledged'] = control.stop_owned_adb_socket(owned)
        deadline = time.monotonic() + 10
        while backend.read(PID) == observed or control.socket_is_live(ROOT / 'adb.sock'):
            if time.monotonic() >= deadline:
                raise RuntimeError('Recorded server or owned socket remains live')
            time.sleep(0.1)
        holders = subprocess.run(['/usr/sbin/lsof', '-n', '+D', str(ROOT)],
                                 capture_output=True, text=True, timeout=20)
        if holders.returncode != 1 or holders.stdout.strip() or holders.stderr.strip():
            raise RuntimeError('Unexpected allocation holder or unreadable ownership inventory')
        check_path(ROOT, EXPECTED_ROOT, stat.S_ISDIR)
        check_path(ROOT / '.parlor-owner', EXPECTED_MARKER, stat.S_ISREG)
        owned.remove()
        result.update(status='PASS', task_allocation_removed=not ROOT.exists(),
                      owned_socket_live=False, recorded_server_lifetime_gone=True,
                      global_adb_client_invoked=False, pid_signals_sent=False)
    except BaseException as error:
        result['error'] = type(error).__name__ + ': ' + str(error)
    finally:
        result['finished_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with RECEIPT.open('x') as stream:
            json.dump(result, stream, indent=2)
            stream.write('\n')
    print(json.dumps(result, indent=2))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
