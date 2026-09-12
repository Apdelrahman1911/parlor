#!/usr/bin/env python3
"""Only invoke inside the root's exclusive run_gradle_cycle.py --command lane.

The inner finally stops Gradle immediately before a build-free Java21 identity
check. The outer lane retains the compiled classes only through that check,
then collects reports and cleans ALL owned build outputs even on failure.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile

from classpath_validation import validate_classpath


HELPERS = Path(__file__).resolve().parent
REPO = HELPERS.parents[4]
CASES = Path('game-modes/whodunit/src/commonMain/composeResources/files/cases')
EARLY = '3625d0663ba6eb51338cbd5f9dc45f859ec18846'
PREVIOUS = '85ded00435b4f8327ce32d3b7f5062c4438ae174'
PINNED = {
    EARLY: {
        'last-dinner': ('1.0.0', 'a8593bdab776bb5dc1723b6fb562f37009aa2e02ddd11fc7e848284916452f26'),
        'layla-halabi': ('1.0.0', 'b98ca82b8cc2334e4f241f6bcefad96c7846f15096f3a043c4126911404345a5'),
        'jasmine-ring': ('1.0.0', '5cd26321aecef56a82afb56ec61f8ebad77329de39f413cb59b26de766d0d714'),
        'khan-el-khalili': ('1.0.0', '0e690e0129c9b76fb772952b4e888e59e81610597b9294c52e8f656144393b9c'),
    },
    PREVIOUS: {
        'last-dinner': ('1.0.1', 'f3b71cc74aaee28dcf7845b750ca773229047388a391ba9294be89efab173a05'),
        'layla-halabi': ('1.0.1', '953d5b1dae0aa26b9901e0e044f279b94fd64d4a6d8a66371a9c6577672a3cfc'),
        'jasmine-ring': ('1.0.1', 'd0aab1123c08f7fb919f00f7ecc8eeb9e1ed2d5daf03df169ea90489c63199c7'),
        'khan-el-khalili': ('1.0.1', 'c9979500738d7b15812b286300714db1943824aa44c29656dc6bebea72e979a0'),
        'iskenderia-corniche': ('1.0.0', 'b492eddff0ccfdc3142a60ff2ee6da22566cbb39ab8717c2219e8875ac66733d'),
        'saidi-inheritance': ('1.0.0', '5d333bfb59ebf49586f5ca15a26ba55206b6d8aa9de505ca8c3e4738ffebf8b6'),
        'zamalek-ramadan': ('1.0.0', 'b3fa57ea89c675bc3c5185a64adcbf0a99b4d640d6dff6fa206b58ed2c070d91'),
    },
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def controls():
    names = ('run_with_identity.py', 'capture-runtime-classpath.init.gradle', 'VerifyContentIdentity.java',
             'classpath_validation.py', 'test_classpath_validation.py')
    return [{'path': str(HELPERS / name), 'sha256': sha(HELPERS / name)} for name in names]


def run_logged(command, path):
    with path.open('x') as log:
        return subprocess.run(command, cwd=REPO, stdout=log, stderr=subprocess.STDOUT, check=False).returncode


def prepare_manifest(scratch, evidence):
    rows = []
    source_records = []
    groups = [(ref, records) for ref, records in PINNED.items()]
    current = {
        case: ('1.0.2' if case in PINNED[EARLY] else '1.0.1', '-')
        for case in PINNED[PREVIOUS]
    }
    groups.append(('current', current))
    for ref, records in groups:
        for case, (version, digest) in sorted(records.items()):
            relative = CASES / (case + '.json')
            if ref == 'current':
                original = REPO / relative
                if original.is_symlink() or not original.is_file():
                    raise RuntimeError('Refusing missing/symlinked current resource')
                data = original.read_bytes()
            else:
                data = subprocess.check_output(['git', 'show', f'{ref}:{relative}'], cwd=REPO)
            target = scratch / (ref + '-' + case + '.json')
            with target.open('xb') as handle:
                handle.write(data)
            byte_hash = hashlib.sha256(data).hexdigest()
            row = (ref, case, str(target), byte_hash, version, digest)
            if any('\t' in value or '\n' in value for value in row):
                raise RuntimeError('Unsafe manifest value')
            rows.append('\t'.join(row))
            source_records.append({'revision': ref, 'resource': str(relative), 'case_id': case,
                                   'byte_sha256': byte_hash, 'expected_version': version,
                                   'expected_canonical_digest': digest})
    manifest = evidence / 'input-manifest.tsv'
    with manifest.open('x') as handle:
        handle.write('\n'.join(rows) + '\n')
    return manifest, source_records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('gradle_arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if not args.evidence_dir.is_absolute():
        raise SystemExit('Evidence directory must be absolute')
    evidence = args.evidence_dir
    expected_base = REPO / 'remediation-runs/2026-09-07-local-readiness/evidence'
    if not evidence.resolve().is_relative_to(expected_base.resolve()):
        raise SystemExit('Evidence must stay under this campaign')
    tasks = args.gradle_arguments
    if tasks and tasks[0] == '--':
        tasks = tasks[1:]
    if ':game-modes:whodunit:desktopTest' not in tasks:
        raise SystemExit('Explicit Whodunit desktopTest selection is required')
    if any('verification-metadata' in arg or 'dependency-verification' in arg
           or arg.startswith(('-I', '--init-script')) for arg in tasks):
        raise SystemExit('Verification/init controls belong to this wrapper')
    evidence.mkdir(exist_ok=False)
    receipt = {'started_at': now(), 'status': 'RUNNING', 'controls_before': controls(),
               'gradle_arguments': tasks, 'production_mutations': False,
               'outer_lane_required': 'run_gradle_cycle.py exclusive --command ownership and cleanup'}
    scratch = Path(tempfile.mkdtemp(prefix='identity-inputs-', dir=evidence))
    status = 1
    previous_handler = signal.getsignal(signal.SIGTERM)

    def interrupted(signum, frame):
        raise KeyboardInterrupt(f'signal {signum}')

    signal.signal(signal.SIGTERM, interrupted)
    try:
        classpath = evidence / 'desktop-test-classpath.txt'
        java_home = Path(os.environ['JAVA_HOME'])
        receipt['java_home'] = str(java_home)
        manifest, records = prepare_manifest(scratch, evidence)
        receipt['inputs'] = records
        command = ['./gradlew', *tasks, '--no-daemon', '--no-parallel', '--max-workers=1',
                   '--no-configuration-cache', '--dependency-verification=strict',
                   '-Pkotlin.compiler.execution.strategy=in-process',
                   '-Pparlor.android.signing.storeFile=', '-Pparlor.android.signing.storePassword=',
                   '-Pparlor.android.signing.keyAlias=', '-Pparlor.android.signing.keyPassword=',
                   '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC '
                   f'-Dparlor.remediation.cycle={evidence.parent.name}',
                   '-I', str(HELPERS / 'capture-runtime-classpath.init.gradle'),
                   f'-Dparlor.audit.whodunit.classpath={classpath}']
        receipt['gradle_command'] = command
        try:
            receipt['gradle_exit_code'] = run_logged(command, evidence / 'focused-gradle.log')
        finally:
            receipt['gradle_finished_at'] = now()
            # Defer a second signal while stopping; outer lane also finalizes.
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            signal.signal(signal.SIGINT, signal.SIG_IGN)
            receipt['immediate_stop_exit_code'] = run_logged(
                ['./gradlew', '--stop'], evidence / 'immediate-gradle-stop.log')
            receipt['gradle_stopped_at'] = now()
            signal.signal(signal.SIGTERM, interrupted)
            signal.signal(signal.SIGINT, signal.default_int_handler)
        if receipt['gradle_exit_code'] != 0 or receipt['immediate_stop_exit_code'] != 0:
            raise RuntimeError('Build or mandatory immediate stop failed; Java check NOT RUN')
        if not classpath.is_file() or classpath.is_symlink():
            raise RuntimeError('Test task did not execute/capture its exact runtime classpath')
        cp = classpath.read_text()
        state_path = Path(str(classpath) + '.state.json')
        if not state_path.is_file() or state_path.is_symlink():
            raise RuntimeError('Missing per-entry/runtime-task classpath capture')
        receipt['classpath_validation'] = validate_classpath(cp, json.loads(state_path.read_text()), REPO)
        receipt['classpath_sha256'] = sha(classpath)
        receipt['classpath_state_sha256'] = sha(state_path)
        java_command = [str(java_home / 'bin/java'), '--class-path', cp,
                        str(HELPERS / 'VerifyContentIdentity.java'), str(manifest)]
        receipt['java_command'] = java_command
        receipt['java_exit_code'] = run_logged(java_command, evidence / 'canonical-identities.tsv')
        if receipt['java_exit_code'] != 0:
            raise RuntimeError('Java production canonical-identity verification failed')
        rows = [line.split('\t') for line in (evidence / 'canonical-identities.tsv').read_text().splitlines()]
        if len(rows) != 18 or any(len(row) != 5 for row in rows):
            raise RuntimeError('Expected exactly 18 canonical identity receipts')
        canonical = {(row[0], row[1]): row for row in rows}
        if len(canonical) != 18:
            raise RuntimeError('Duplicate canonical identity receipt')
        for item in records:
            record = canonical[(item['revision'], item['case_id'])]
            if record[2:4] != [item['byte_sha256'], item['expected_version']]:
                raise RuntimeError('Receipt bytes/version differ')
            if item['revision'] != 'current' and record[4] != item['expected_canonical_digest']:
                raise RuntimeError('Historical production digest differs from regression pin')
        for case in PINNED[PREVIOUS]:
            current_digest = canonical[('current', case)][4]
            for old in PINNED.values():
                if case in old and current_digest == old[case][1]:
                    raise RuntimeError('Current story reused a retired canonical identity')
        receipt['status'] = 'PASS'
        receipt['verified_records'] = 18
        status = 0
    except BaseException as error:
        receipt['status'] = 'FAIL'
        receipt['error'] = f'{type(error).__name__}: {error}'
    finally:
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        receipt['controls_after'] = controls()
        if receipt['controls_before'] != receipt['controls_after']:
            receipt['status'] = 'FAIL'
            receipt['control_mutation'] = True
            status = 1
        # Only the uniquely created scratch subtree; no build/source/global cleanup.
        shutil.rmtree(scratch)
        receipt['scratch_removed'] = not scratch.exists()
        receipt['finished_at'] = now()
        with (evidence / 'identity-receipt.json').open('x') as handle:
            json.dump(receipt, handle, indent=2)
            handle.write('\n')
        signal.signal(signal.SIGTERM, previous_handler)
        signal.signal(signal.SIGINT, signal.default_int_handler)
    return status


if __name__ == '__main__':
    sys.exit(main())
