#!/usr/bin/env python3
"""Root-lane-only synthetic Release APK build; never accepts Store credentials.

Use only through this campaign's run_gradle_cycle.py --command. The outer lane
binds dirty source and owns build-output cleanup; this helper immediately stops
Gradle, verifies/retains two disposable-signed APKs, and removes its private key.
"""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import uuid


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
JDK = Path('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home').resolve()
SDK = Path('/Users/abdelrahman/Library/Android/sdk').resolve()
PROCESS_CONTROL = HERE.parent / 'android-arm64-runner/darwin_owned_processes.py'
PINS = {
    ROOT / 'composeApp/build.gradle.kts': 'c23525d6b06f9b536fb5e8ef6be68c653952fa9764e276f831772b864621fdd5',
    CAMPAIGN / 'run_gradle_cycle.py': '172c4f3162dc6a6b20e912e6551eaca9f33a32ba3d0d6f150ad8becfd30865ea',
    PROCESS_CONTROL: '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378',
}
MAX_APK_BYTES = 512 * 1024 * 1024
MAX_LOG_BYTES = 16 * 1024 * 1024
EXPECTED = {
    'release': ('com.parlor.app', 'release.apk'),
    'releaseAndroidTest': ('com.parlor.app.test', 'release-androidTest.apk'),
}


def sha256(path):
    state = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            state.update(block)
    return state.hexdigest()


def regular_file(path, limit):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
        raise RuntimeError('Missing/unsafe/oversized task file: ' + str(path))
    return info


def owned_directory(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        raise RuntimeError('Task directory ownership changed: ' + str(path))
    return info.st_dev, info.st_ino, info.st_uid


def bindings():
    result = {}
    for path, expected in PINS.items():
        regular_file(path, 1024 * 1024)
        if sha256(path) != expected:
            raise RuntimeError('Pinned control/source drift: ' + str(path))
        result[str(path)] = expected
    for path in (Path(__file__), JDK / 'release', JDK / 'bin/java', JDK / 'bin/keytool',
                 SDK / 'build-tools/36.0.0/apksigner', SDK / 'build-tools/36.0.0/lib/apksigner.jar'):
        result[str(path)] = sha256(path)
    return result


def inspect_outer_lane(cycle_name):
    if not re.fullmatch(r'[A-Za-z0-9-]+', cycle_name):
        raise RuntimeError('Invalid outer cycle name')
    cycle = CAMPAIGN / 'evidence' / cycle_name
    if Path(os.environ.get('TMPDIR', '')).resolve() != (cycle / 'scratch/tmp').resolve():
        raise RuntimeError('Must be invoked by the matching campaign build lane')
    with (CAMPAIGN / 'build-lane.lock').open('r') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass  # The outer process must retain its lane until this command exits.
        else:
            fcntl.flock(lock, fcntl.LOCK_UN)
            raise RuntimeError('No active outer build-lane owner')
    path = cycle / 'receipt.json'
    regular_file(path, 8 * 1024 * 1024)
    receipt = json.loads(path.read_text())
    if receipt.get('cycle') != cycle_name or receipt.get('status') != 'RUNNING':
        raise RuntimeError('Outer receipt does not describe this active cycle')
    if str(Path(__file__).resolve()) not in receipt.get('command', []):
        raise RuntimeError('Outer command does not bind this helper')
    source = receipt['source_before']
    for key, length in (('commit', 40), ('tree', 40), ('diff_sha256', 64), ('source_manifest_sha256', 64)):
        if not re.fullmatch('[0-9a-f]{' + str(length) + '}', source.get(key, '')):
            raise RuntimeError('Missing exact outer source identity: ' + key)
    if (ROOT / 'composeApp/build').exists() or (ROOT / 'composeApp/build').is_symlink():
        raise RuntimeError('Pre-existing APK build output is not owned by this command')
    return cycle, {key: source[key] for key in ('commit', 'tree', 'diff_sha256', 'source_manifest_sha256')}


def discover_apks(output_root):
    """Read AGP's actual metadata; never guess an APK name or accept stale splits."""
    found = {}
    metadata_paths = list(output_root.rglob('output-metadata.json'))
    if len(metadata_paths) != 2:
        raise RuntimeError('Expected exactly two APK output metadata records')
    for metadata_path in metadata_paths:
        regular_file(metadata_path, 128 * 1024)
        if metadata_path.resolve() != metadata_path.absolute():
            raise RuntimeError('Symlinked APK metadata ancestry rejected')
        metadata = json.loads(metadata_path.read_text())
        variant = metadata.get('variantName')
        if variant not in EXPECTED or variant in found:
            raise RuntimeError('Unexpected or duplicate APK variant')
        if metadata.get('artifactType', {}).get('type') != 'APK':
            raise RuntimeError('Output metadata is not an APK artifact')
        if metadata.get('applicationId') != EXPECTED[variant][0]:
            raise RuntimeError('APK metadata application identity mismatch')
        elements = metadata.get('elements')
        if not isinstance(elements, list) or len(elements) != 1:
            raise RuntimeError('Exactly one APK per variant is required')
        element = elements[0]
        if element.get('type') != 'SINGLE' or element.get('filters') != []:
            raise RuntimeError('Unexpected split/filtered APK output')
        name = element.get('outputFile')
        if not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9_.-]+\.apk', name):
            raise RuntimeError('Unsafe APK output filename')
        path = metadata_path.parent / name
        info = regular_file(path, MAX_APK_BYTES)
        if info.st_size == 0:
            raise RuntimeError('Empty APK output')
        found[variant] = {'path': path, 'metadata_sha256': sha256(metadata_path),
                          'application_id': metadata['applicationId']}
    all_apks = set(output_root.rglob('*.apk'))
    if set(found) != set(EXPECTED) or all_apks != {item['path'] for item in found.values()}:
        raise RuntimeError('Extra/missing APK output rejected')
    return found


def approved_certificate(output, expected):
    digests = re.findall(r'^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})$', output, re.M)
    counts = re.findall(r'^Number of signers: (\d+)$', output, re.M)
    if counts != ['1'] or len(digests) != 1 or digests[0][0] != '1' or digests[0][1].lower() != expected:
        raise RuntimeError('APK does not have exactly the expected disposable signer')
    return expected


def process_registry():
    name = 'parlor_apk_build_owned_processes'
    spec = importlib.util.spec_from_file_location(name, PROCESS_CONTROL)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module.OwnedProcesses()


class Commands:
    def __init__(self, registry, log_dir, environment, receipt):
        self.registry, self.log_dir, self.environment = registry, log_dir, environment
        self.receipt, self.signal = receipt, None
        self.active_gradle = None

    def check_cancelled(self):
        if self.signal is not None:
            raise InterruptedError('Cancelled by signal ' + str(self.signal))

    def run(self, label, command, timeout=45, *, gradle=False, cleanup=False):
        if not cleanup:
            self.check_cancelled()
        if len(self.registry.launches) >= 32:
            raise RuntimeError('Command ceiling exceeded before worker creation')
        log = self.log_dir / (label + '.log')
        entry = {'label': label, 'command': command, 'started_epoch': time.time(), 'log': str(log)}
        self.receipt['commands'].append(entry)
        print('START ' + label, flush=True)
        process = None
        try:
            with log.open('xb') as output:
                # The signal handler only records cancellation; it cannot interrupt
                # Popen-to-registration ownership transfer or the cleanup commands.
                process = subprocess.Popen(command, cwd=ROOT, env=self.environment,
                                           stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT,
                                           start_new_session=True)
                launch = self.registry.register(process, label, command)
                if gradle:
                    self.active_gradle = launch
                deadline = time.monotonic() + timeout
                while True:
                    if not cleanup:
                        self.check_cancelled()
                    code = launch.poll()
                    if code is not None:
                        break
                    if time.monotonic() >= deadline:
                        raise TimeoutError('Bounded command timeout: ' + label)
                    if log.stat().st_size > MAX_LOG_BYTES:
                        raise RuntimeError('Command log bound exceeded: ' + label)
                    time.sleep(0.1)
                entry['exit_code'] = code
                # Keep the completed Gradle leader waitable until --stop completes;
                # its single-use daemon may still be exiting in the same group.
                if not gradle:
                    self.registry.finish(launch)
            if log.stat().st_size > MAX_LOG_BYTES:
                raise RuntimeError('Command log bound exceeded: ' + label)
            if code != 0:
                raise RuntimeError(f'{label} failed with exit {code}; inspect {log.name}')
            return log
        finally:
            entry['finished_epoch'] = time.time()
            print('END ' + label + ': ' + str(entry.get('exit_code', 'interrupted')), flush=True)

    def stop_gradle(self):
        # If an interrupted build is still active, finish only its attested tree
        # first. Never ask another task's busy Gradle worker to stop.
        launch = self.active_gradle
        try:
            if launch is not None and launch.poll() is None:
                self.registry.stop(launch, term_seconds=3, kill_seconds=3)
        finally:
            try:
                self.run('immediate-gradle-stop', [str(ROOT / 'gradlew'), '--stop'],
                         timeout=15, cleanup=True)
            finally:
                if launch is not None and not launch.retired:
                    self.registry.finish(launch)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cycle', required=True)
    parser.add_argument('--artifact-dir', required=True, type=Path)
    args = parser.parse_args()
    initial = bindings()
    cycle, source = inspect_outer_lane(args.cycle)
    if not shutil.rmtree.avoids_symlink_attacks:
        raise RuntimeError('FD-safe owned scratch removal unavailable')
    stage = args.artifact_dir.absolute()
    if stage.parent != HERE.parent or stage.name in ('', '.', '..'):
        raise RuntimeError('APK stage must be a fresh direct sibling of this helper directory')
    old_umask = os.umask(0o077)
    stage.mkdir(mode=0o700, parents=False, exist_ok=False)
    stage_identity = owned_directory(stage)
    receipt = {'status': 'RUNNING', 'scope': 'Disposable-signed APK packaging; NOT Store signing/runtime proof',
               'author': '/root/release_fix_review', 'started_epoch': time.time(), 'source_before': source,
               'outer_cycle_receipt': str(cycle / 'receipt.json'), 'control_before': initial,
               'stage_identity': stage_identity, 'stage_nonce': str(uuid.uuid4()), 'commands': [], 'artifacts': [],
               'retention': 'Keep the two APKs only through the following owned ARM64 runtime/inspection',
               'acceptance': 'Requires outer cycle PASS, unchanged complete source manifest, and matching APK hashes'}
    receipt_path = stage / 'receipt.json'
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')
    scratch = None
    registry = commands = None
    scratch_identity = marker = None
    handlers = {}
    try:
        registry = process_registry()
        scratch = Path(tempfile.mkdtemp(prefix='android-signing-', dir=os.environ['TMPDIR']))
        scratch_identity = owned_directory(scratch)
        marker = {'nonce': str(uuid.uuid4()), 'identity': scratch_identity}
        (scratch / 'owner.json').write_text(json.dumps(marker))
        receipt['owned_scratch'] = str(scratch)
        (scratch / 'tmp').mkdir()
        (scratch / 'android-home').mkdir()
        logs = cycle / 'android-apk-helper-logs'
        logs.mkdir(exist_ok=False)
        env = os.environ.copy()
        for name in list(env):
            if name.startswith(('PARLOR_ANDROID_', 'MOBILE_RELEASE_',
                                'ORG_GRADLE_PROJECT_android.injected.signing.',
                                'ORG_GRADLE_PROJECT_parlor.android.signing.')):
                env.pop(name)
        for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'GRADLE_OPTS'):
            env.pop(name, None)
        key = scratch / 'disposable.p12'
        public = scratch / 'public.der'
        password = secrets.token_urlsafe(32)
        alias = 'parlor-disposable-android-runtime'
        signing = {'store.file': str(key), 'store.password': password,
                   'key.alias': alias, 'key.password': password}
        # Both the repository's release config and AGP's existing managed-device
        # injection get the SAME disposable key. Passwords are env-only, never CLI.
        env.update({'PARLOR_ANDROID_KEYSTORE_PATH': str(key), 'PARLOR_ANDROID_KEYSTORE_PASSWORD': password,
                    'PARLOR_ANDROID_KEY_ALIAS': alias, 'PARLOR_ANDROID_KEY_PASSWORD': password,
                    'MOBILE_RELEASE_REQUIRE_SIGNING': 'true', 'JAVA_HOME': str(JDK),
                    'PATH': str(JDK / 'bin') + ':/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin',
                    'ANDROID_HOME': str(SDK), 'ANDROID_SDK_ROOT': str(SDK),
                    'ANDROID_USER_HOME': str(scratch / 'android-home'), 'TMPDIR': str(scratch / 'tmp'),
                    'LANG': 'C', 'LC_ALL': 'C', 'PYTHONDONTWRITEBYTECODE': '1'})
        env.update({'ORG_GRADLE_PROJECT_android.injected.signing.' + name: value
                    for name, value in signing.items()})
        commands = Commands(registry, logs, env, receipt)
        for signum in (signal.SIGINT, signal.SIGTERM):
            handlers[signum] = signal.signal(signum, lambda received, _frame: setattr(commands, 'signal', received))
        commands.run('jdk-version', [str(JDK / 'bin/java'), '-version'])
        commands.run('create-disposable-key', [str(JDK / 'bin/keytool'), '-J-Xmx128m', '-genkeypair',
                     '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048', '-sigalg', 'SHA256withRSA',
                     '-validity', '2', '-dname', 'CN=Disposable Parlor Android Runtime Fixture',
                     '-keystore', str(key), '-storetype', 'PKCS12',
                     '-storepass:env', 'PARLOR_ANDROID_KEYSTORE_PASSWORD',
                     '-keypass:env', 'PARLOR_ANDROID_KEY_PASSWORD', '-noprompt'])
        commands.run('export-disposable-certificate', [str(JDK / 'bin/keytool'), '-J-Xmx128m', '-exportcert',
                     '-alias', alias, '-keystore', str(key), '-storepass:env', 'PARLOR_ANDROID_KEYSTORE_PASSWORD',
                     '-file', str(public)])
        expected = sha256(public)
        receipt['disposable_certificate_sha256'] = expected
        heap = os.environ.get('PARLOR_REMEDIATION_GRADLE_HEAP', '3g')
        if heap not in ('3g', '6g'):
            raise RuntimeError('Unreviewed Gradle heap bound')
        try:
            commands.run('assemble-release-apks', [str(ROOT / 'gradlew'),
                         ':composeApp:assembleRelease', ':composeApp:assembleReleaseAndroidTest',
                         '--dependency-verification=strict', '--no-daemon', '--no-parallel',
                         '--max-workers=1', '--no-configuration-cache', '--no-build-cache', '--console=plain',
                         '-Pkotlin.compiler.execution.strategy=in-process',
                         f'-Dorg.gradle.jvmargs=-Xmx{heap} -Dfile.encoding=UTF-8 -XX:+UseParallelGC '
                         f'-Dparlor.remediation.cycle={args.cycle}'], timeout=3600, gradle=True)
        finally:
            # Mandatory after success, failure, timeout or cancellation; no APK
            # copying, verification or unrelated operation precedes this stop.
            commands.stop_gradle()
        key.unlink()  # Verification uses only the exported public fingerprint.
        commands.check_cancelled()
        apks = discover_apks(ROOT / 'composeApp/build/outputs/apk')
        for variant, item in apks.items():
            log = commands.run('verify-' + variant, [str(SDK / 'build-tools/36.0.0/apksigner'),
                               '-JXmx256M', 'verify', '--verbose', '--print-certs', str(item['path'])])
            regular_file(log, 1024 * 1024)
            item['signer_sha256'] = approved_certificate(log.read_text(), expected)
        for variant, item in apks.items():
            commands.check_cancelled()
            source_path = item['path']
            destination = stage / EXPECTED[variant][1]
            digest = sha256(source_path)
            with source_path.open('rb') as input_file, destination.open('xb') as output:
                shutil.copyfileobj(input_file, output, length=1024 * 1024)
            if sha256(destination) != digest:
                raise RuntimeError('APK changed during retention')
            receipt['artifacts'].append({'variant': variant, 'path': str(destination),
                                         'sha256': digest, 'bytes': destination.stat().st_size,
                                         'signer_sha256': item['signer_sha256'],
                                         'application_id': item['application_id'],
                                         'metadata_sha256': item['metadata_sha256']})
        commands.check_cancelled()
        receipt['status'] = 'PASS'
    except BaseException as error:
        receipt['status'] = 'FAIL'
        receipt['failure'] = type(error).__name__ + ': ' + str(error)
    finally:
        errors = []
        if registry is not None:
            try:
                errors.extend(registry.shutdown())
            except BaseException as error:
                errors.append({'owned_worker_cleanup': str(error)})
            receipt['workers'] = registry.receipts()
            receipt['token_signals'] = registry.events
        if scratch is not None and not errors:
            try:
                if owned_directory(scratch) != scratch_identity:
                    raise RuntimeError('Scratch inode/UID changed; preserved')
                if marker is not None:
                    fd = os.open(scratch / 'owner.json', os.O_RDONLY | os.O_NOFOLLOW)
                    with os.fdopen(fd) as source_file:
                        if json.loads(source_file.read(1024)) != json.loads(json.dumps(marker)):
                            raise RuntimeError('Scratch ownership marker changed; preserved')
                shutil.rmtree(scratch)
                receipt['private_key_and_scratch_removed'] = not scratch.exists()
            except BaseException as error:
                errors.append({'scratch_cleanup': str(error)})
        try:
            receipt['control_after'] = bindings()
            if receipt['control_after'] != initial:
                raise RuntimeError('Build helper/control/source binding changed')
        except BaseException as error:
            errors.append({'control_binding': str(error)})
        for entry in receipt['commands']:
            log = Path(entry['log'])
            try:
                if log.exists():
                    if log.stat().st_size > MAX_LOG_BYTES:
                        with log.open('r+b') as output:
                            output.truncate(MAX_LOG_BYTES)
                        errors.append({'bounded_log_truncated': entry['label']})
                    entry['sha256'] = sha256(log)
            except BaseException as error:
                errors.append({'log_finalization': entry['label'], 'error': str(error)})
        if commands is not None and commands.signal is not None:
            errors.append({'cancelled_by_signal': commands.signal})
        if errors:
            receipt['status'] = 'FAIL'
        stage_safe = False
        try:
            if owned_directory(stage) != stage_identity:
                raise RuntimeError('Artifact stage ownership changed; do not overwrite or delete it')
            stage_safe = True
        except BaseException as error:
            errors.append({'stage_ownership': str(error)})
            receipt['status'] = 'FAIL'
        if stage_safe and receipt['status'] != 'PASS':
            # These exact synthetic APK names are owned by this newly made stage.
            # The source APKs/build outputs remain for the outer evidence finalizer.
            for _, filename in EXPECTED.values():
                path = stage / filename
                try:
                    if path.exists() or path.is_symlink():
                        regular_file(path, MAX_APK_BYTES)
                        path.unlink()
                except BaseException as error:
                    errors.append({'failed_stage_cleanup': str(path), 'error': str(error)})
            receipt['retention'] = 'Helper failed: staged APK cleanup attempted; inspect cleanup errors and outer receipt'
        receipt['cleanup_errors'] = errors
        receipt['finished_epoch'] = time.time()
        if stage_safe:
            try:
                regular_file(receipt_path, 8 * 1024 * 1024)
                receipt_path.write_text(json.dumps(receipt, indent=2) + '\n')
            except BaseException as error:
                errors.append({'receipt_write': str(error)})
                receipt['status'] = 'FAIL'
        if not stage_safe or any('receipt_write' in item for item in errors):
            print(json.dumps({'status': 'FAIL', 'receipt_retained_in_outer_log': receipt}), flush=True)
        for signum, handler in handlers.items():
            signal.signal(signum, handler)
        os.umask(old_umask)
    print(receipt['status'] + ': ' + str(receipt_path), flush=True)
    return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
