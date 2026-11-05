"""One supported cache-completion experiment, not an input or production fix.

Apple documents this command under Simulator issue 170836966 in Xcode 26.4
release notes. Pinned 26.3 advertises it too; applicability to Parlor's original
keyboard/HID failures remains unproven. Never force, remove, retry, boot, or
generate input. The read-only intake is reused unchanged from 9d08c4e6.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'ios-runtime-cache-intake'))
import intake

COMMAND = ('xcrun', 'simctl', 'runtime', 'dyld_shared_cache', 'update', '--all')
TIMEOUT_SECONDS = 300
MAX_OUTPUT_BYTES = 4 * 1024 * 1024
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'


def require_preflight(receipt: dict, runtimes: dict) -> None:
    if receipt.get('status') != 'COLLECTED_READ_ONLY_NOT_RUNTIME_QUALIFICATION':
        raise ValueError('Completed unchanged read-only preflight required')
    rows = receipt.get('commands', [])
    if len(rows) != len(intake.COMMANDS) or {
        row.get('label') for row in rows
    } != set(intake.COMMANDS) or any(
        row.get('argv') != intake.COMMANDS.get(row.get('label')) or
        type(row.get('exit_code')) is not int or row['exit_code'] != 0
        for row in rows
    ):
        raise ValueError('All exact native preflight commands must succeed')
    if 'update' not in receipt.get('advertised_cache_operations', []):
        raise ValueError('The actual pinned tool must advertise cache update')
    matches = [r for r in runtimes.get('runtimes', []) if r.get('identifier') == RUNTIME]
    if len(matches) != 1 or matches[0].get('isAvailable') is not True or \
            matches[0].get('version') != '26.2' or matches[0].get('buildversion') != '23C54':
        raise ValueError('Exact original installed iOS 26.2/23C54 runtime required')


def complete_once(target: Path) -> dict:
    """Reap only the command we started, preserving timeout/nonzero outcomes."""
    stdout, stderr = target / 'completion.stdout', target / 'completion.stderr'
    row = dict(argv=list(COMMAND), timeout_seconds=TIMEOUT_SECONDS,
               force=False, retries=0, timed_out=False)
    started = time.monotonic()
    with stdout.open('xb') as out, stderr.open('xb') as err:
        process = subprocess.Popen(COMMAND, stdout=out, stderr=err,
                                   stdin=subprocess.DEVNULL, start_new_session=True)
        row['pid'] = process.pid
        try:
            process.wait(timeout=TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            row['timed_out'] = True
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
        finally:
            row['exit_code'] = process.returncode
            row['reaped'] = process.returncode is not None
            row['elapsed_seconds'] = time.monotonic() - started
    row['output_bytes'] = stdout.stat().st_size + stderr.stat().st_size
    row['stdout_sha256'] = hashlib.sha256(stdout.read_bytes()).hexdigest()
    row['stderr_sha256'] = hashlib.sha256(stderr.read_bytes()).hexdigest()
    row['completed'] = row['reaped'] and not row['timed_out'] and \
        row['exit_code'] == 0 and row['output_bytes'] <= MAX_OUTPUT_BYTES
    return row


def main() -> None:
    # Intake validates macOS, clean source, unchanged AGENTS, exact Xcode/tool
    # paths, owned new output, native usage, and actual runtime inventories.
    target = Path(os.environ['PARLOR_CACHE_PREPARATION_OUTPUT'])
    os.environ['PARLOR_CACHE_INTAKE_OUTPUT'] = str(target)
    intake.main()
    preflight = json.loads((target / 'receipt.json').read_text())
    runtimes = json.loads((target / 'available-runtimes.stdout').read_text())
    require_preflight(preflight, runtimes)
    receipt = dict(status='INCOMPLETE', head=preflight['head'], tree=preflight['tree'],
                   run=preflight['run'], attempt=preflight['attempt'],
                   read_only_preflight_sha256=hashlib.sha256(
                       (target / 'receipt.json').read_bytes()).hexdigest(),
                   explicit_cache_completion_requested=True,
                   application_executed=False, input_generated=False,
                   production_qualified=False, original_causality_proven=False,
                   reference='https://developer.apple.com/documentation/xcode-release-notes/xcode-26_4-release-notes',
                   limitation='Command completion is not cache-change evidence, historical causality, or an input-test pass.')
    def save() -> None:
        temporary = target / 'preparation.tmp'
        temporary.write_text(json.dumps(receipt, indent=2) + '\n')
        temporary.replace(target / 'preparation.json')
    save()
    try:
        receipt['completion'] = complete_once(target)
        if not receipt['completion']['completed']:
            raise RuntimeError('Supported cache-completion command did not qualify')
        receipt['status'] = 'CACHE_COMMAND_COMPLETED_NOT_INPUT_QUALIFICATION'
    except Exception as error:
        receipt.update(status='FAILED_CACHE_COMPLETION', error=type(error).__name__ + ': ' + str(error))
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
