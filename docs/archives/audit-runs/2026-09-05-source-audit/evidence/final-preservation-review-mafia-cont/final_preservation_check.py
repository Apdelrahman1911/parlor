#!/usr/bin/env python3
"""Read-only source/owned-output/process comparison. Does not stop other work."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RECEIPT = HERE / 'evidence/final-preservation-and-hygiene.json'

def save_receipt(value):
    partial = RECEIPT.with_name(RECEIPT.name + '.writing')
    partial.write_text(json.dumps(value, indent=2) + '\n')
    partial.replace(RECEIPT)

checkpoint = dict(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  phase='RUNNING', source_preservation_status='BLOCKED', cleanup_status='BLOCKED',
                  reason='Final comparison started; no previous PASS is valid for this invocation.')
save_receipt(checkpoint)
try:
    baseline = json.loads((HERE / 'baseline.json').read_text())
    subprocess.run(['/usr/bin/python3', '-B', str(HERE / 'assemble_coverage.py')], cwd=ROOT, check=True)
    state = json.loads((HERE / 'evidence/final-state.json').read_text())
    coverage = json.loads((HERE / 'coverage/summary.json').read_text())

    outputs = [ROOT / 'build', ROOT / 'composeApp/build', ROOT / 'build-logic/build', ROOT / 'build-logic/convention/build']
    outputs += [p.parent / 'build' for pattern in ['shared/*/build.gradle.kts', 'game-modes/*/build.gradle.kts'] for p in ROOT.glob(pattern)]
    temporaries, devices, input_receipts, running_receipts = set(), set(), [], []
    attested_processes, android_ports = {}, []

    def visit(value):
        if isinstance(value, dict):
            if value.get('owned_uuid'):
                devices.add(value['owned_uuid'])
            if all(key in value for key in ['pid', 'start', 'role', 'proof']):
                attested_processes[(int(value['pid']), value['start'])] = {
                    key: value[key] for key in ['pid', 'start', 'role', 'proof']
                }
            if value.get('execution_kind') == 'gradle-via-checked-in-disposable-signing-harness' and value.get('adb_port'):
                android_ports.append(dict(cycle=value.get('cycle'), port=int(value['adb_port']),
                                          owned_pid=value.get('adb_owned_pid')))
            for v in value.values():
                visit(v)
        elif isinstance(value, list):
            for v in value:
                visit(v)
        elif isinstance(value, str) and value.startswith(('/var/folders/', '/private/var/folders/', '/tmp/')):
            parts = Path(value).parts
            for i, part in enumerate(parts):
                if part.startswith('parlor-audit-'):
                    temporaries.add(str(Path(*parts[:i + 1])))
                    break

    for file in sorted((HERE / 'evidence').glob('*/receipt.json')):
        value = json.loads(file.read_text())
        visit(value)
        input_receipts.append(dict(path=file.relative_to(HERE).as_posix(), sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
        if isinstance(value, dict) and value.get('status') in ['RUNNING', 'PREPARING']:
            running_receipts.append(file.relative_to(HERE).as_posix())

    process_output = subprocess.check_output(['ps', '-axo', 'pid=,ppid=,lstart=,command='], text=True,
                                             env={**os.environ, 'LC_ALL': 'C'})
    owned_matches, gradle8, unrelated_workers = [], [], []
    current_processes, attested_still_alive = {}, []
    for line in process_output.splitlines():
        fields = line.split(None, 7)
        if len(fields) != 8:
            continue
        pid, parent, started, command = int(fields[0]), int(fields[1]), ' '.join(fields[2:7]), fields[7]
        record = dict(pid=pid, parent=parent, started=started)
        current_processes[pid] = record
        if (pid, started) in attested_processes:
            attested_still_alive.append(dict(record, ownership=attested_processes[(pid, started)]))
        matches = [v for v in devices | temporaries if v in command]
        if matches:
            owned_matches.append(dict(record, matching_owned_identifiers=matches))
        daemon = re.search(r'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon\s+(\S+)', command)
        if daemon:
            record['worker'] = 'GradleDaemon'
            record['version'] = daemon.group(1)
            (gradle8 if daemon.group(1) == '8.13' else unrelated_workers).append(record)
        elif 'KotlinCompileDaemon' in command:
            unrelated_workers.append(dict(record, worker='KotlinCompileDaemon', ownership='not task-attested; preserved'))

    if os.getpid() not in current_processes:
        raise RuntimeError('Process metadata is incomplete; no cleanup PASS can be inferred')

    adb_port_checks, port_check_errors = [], []
    for port in android_ports:
        result = subprocess.run(['/usr/sbin/lsof', '-nP', '-w', '-t', '-iTCP:' + str(port['port']), '-sTCP:LISTEN'],
                                text=True, capture_output=True, timeout=30)
        lines = result.stdout.splitlines()
        if result.returncode not in (0, 1) or result.stderr.strip() or any(not line.isdigit() for line in lines):
            port_check_errors.append(dict(cycle=port['cycle'], exit_code=result.returncode,
                                          error='ADB-listener metadata could not be verified'))
            continue
        listeners = [int(line) for line in lines]
        # A newly reused port is not permission to terminate another task. Exact
        # owned PID/start matches above, not the port number alone, identify leaks.
        adb_port_checks.append(dict(port, listener_pids=listeners,
                                   listeners=[current_processes.get(pid, dict(pid=pid, state='not-in-earlier-process-snapshot')) for pid in listeners],
                                   action='Read-only; no server stop or connection attempted'))

    remaining_outputs = [str(p.relative_to(ROOT)) for p in outputs if p.exists() or p.is_symlink()]
    remaining_temp = [p for p in sorted(temporaries) if Path(p).exists() or Path(p).is_symlink()]
    device_records = [dict(uuid=u, device_directory_exists=(Path.home() / 'Library/Developer/CoreSimulator/Devices' / u).exists()) for u in sorted(devices)]
    source_ok = not coverage['errors'] and not state['tracked_status'] and not state['changed_hashes'] and not state['new_non_audit_inventory_paths'] and not state['removed_non_audit_inventory_paths'] and all(state[k] for k in ['refs_unchanged', 'stashes_unchanged', 'baseline_untracked_listing_unchanged']) and state['diff_check_exit_code'] == 0 and all(state[k] == baseline[k] for k in ['branch', 'commit', 'tree'])
    cleanup_ok = not (remaining_outputs or remaining_temp or owned_matches or attested_still_alive or
                     port_check_errors or any(check['listener_pids'] for check in adb_port_checks) or gradle8 or running_receipts or any(v['device_directory_exists'] for v in device_records))
    receipt = dict(
        recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        source_preservation_status='PASS' if source_ok else 'FAIL',
        cleanup_status='PASS' if cleanup_ok else 'BLOCKED',
        source_comparison=state, coverage_errors=coverage['errors'],
        inspected_cycle_receipts=input_receipts, unfinished_cycle_receipts=running_receipts,
        output_directories_checked=[str(p.relative_to(ROOT)) for p in outputs], remaining_output_directories=remaining_outputs,
        owned_temporary_roots_checked=sorted(temporaries), remaining_owned_temporaries=remaining_temp,
        owned_simulator_directories=device_records, current_owned_process_matches=owned_matches,
        exact_pid_start_identities_checked=list(attested_processes.values()),
        exact_owned_processes_still_alive=attested_still_alive,
        owned_android_adb_port_checks=adb_port_checks, android_port_check_errors=port_check_errors,
        current_gradle_8_13_processes=gradle8, unrelated_workers_not_stopped=unrelated_workers,
        disk_available_bytes=shutil.disk_usage(ROOT).free,
        retained_audit_file_bytes=sum(p.stat().st_size for p in HERE.rglob('*') if p.is_file()),
        actions='Read-only metadata/process/hash comparison. No process terminated or source/cache/evidence removed by this checker.',
        limits=[
            'No private/local-state/previous-audit byte hashes were obtained; excluded contents are not claimed byte-attested.',
            'No start-time/parent attestation exists for every pre-existing unrelated worker; they were not stopped.',
            'Simulator existence check covers only exact task-created UUID directories and recorded process markers; no other simulator metadata or player data read.',
            'Reports whose retained path contains build are required compact evidence, not live generated outputs.',
            'A Gradle8.13 process without task ownership would block final cleanup assertion, not authorize terminating another task.',
        ])
    receipt['phase'] = 'COMPLETE'
    save_receipt(receipt)
    print(json.dumps({k: receipt[k] for k in ['source_preservation_status', 'cleanup_status', 'remaining_output_directories', 'remaining_owned_temporaries', 'current_owned_process_matches', 'current_gradle_8_13_processes', 'retained_audit_file_bytes']}, indent=2))
except BaseException as error:
    checkpoint.update(recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                      phase='FAILED', error_type=type(error).__name__,
                      reason='Comparison could not complete; no source-preservation or cleanup PASS inferred.')
    save_receipt(checkpoint)
    raise
raise SystemExit(0 if source_ok and cleanup_ok else 1)
