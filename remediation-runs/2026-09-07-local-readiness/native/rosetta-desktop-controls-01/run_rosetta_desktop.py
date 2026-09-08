#!/usr/bin/env python3
"""ROOT LANE ONLY: public checksum-pinned x64 JDK21, productionDesktopCheck, owned cleanup."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import uuid

from safe_jdk import download, extract, owned_identity, SHA256, SIZE


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parents[1]
ROOT = CAMPAIGN.parents[1]
PROCESS_CONTROL = CAMPAIGN / 'evidence/release-content-followup/android-arm64-runner/darwin_owned_processes.py'
PROCESS_SHA = '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378'
METADATA = CAMPAIGN / 'evidence/release-content-followup/official-jdk-metadata-alternative.json'
MAX_LOG = 64 * 1024 * 1024


def digest(path):
    state = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            state.update(block)
    return state.hexdigest()


def control_binding():
    paths = [HERE / name for name in ('run_rosetta_desktop.py', 'safe_jdk.py',
             'ArchitectureProbe.java', 'rosetta.init.gradle', 'test_controls.py', 'README.md')]
    paths += [PROCESS_CONTROL, METADATA, CAMPAIGN / 'run_gradle_cycle.py']
    if any(p.is_symlink() or not p.is_file() for p in paths):
        raise RuntimeError('A reviewed Rosetta control is absent/symlinked')
    rows = [(str(p.relative_to(ROOT)), digest(p)) for p in sorted(paths)]
    return {'files': rows, 'sha256': hashlib.sha256(json.dumps(rows, separators=(',', ':')).encode()).hexdigest()}


def gradle_command(jdk, cycle):
    return ['./gradlew', 'productionDesktopCheck', '--no-daemon', '--no-parallel', '--max-workers=1',
            '--no-configuration-cache', '--no-build-cache', '--rerun-tasks', '--console=plain',
            '--dependency-verification=strict', '-I', str(HERE / 'rosetta.init.gradle'),
            '-Dorg.gradle.java.home=' + str(jdk),
            '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC '
            '-Dparlor.remediation.cycle=' + cycle,
            '-Porg.gradle.java.installations.paths=' + str(jdk),
            '-Porg.gradle.java.installations.fromEnv=',
            '-Porg.gradle.java.installations.auto-detect=false',
            '-Porg.gradle.java.installations.auto-download=false',
            '-Pkotlin.compiler.execution.strategy=in-process',
            '-Pparlor.android.signing.storeFile=', '-Pparlor.android.signing.storePassword=',
            '-Pparlor.android.signing.keyAlias=', '-Pparlor.android.signing.keyPassword=']


def load_registry():
    if digest(PROCESS_CONTROL) != PROCESS_SHA:
        raise RuntimeError('Reviewed Darwin process controller changed')
    spec = importlib.util.spec_from_file_location('rosetta_owned_processes', PROCESS_CONTROL)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module.OwnedProcesses()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cycle', required=True)
    parser.add_argument('--approved-control-sha256', required=True)
    args = parser.parse_args()
    if not args.cycle.replace('-', '').isalnum():
        raise RuntimeError('Unsafe cycle name')
    controls = control_binding()
    if controls['sha256'] != args.approved_control_sha256:
        raise RuntimeError('Rosetta controls do not match independently approved binding')
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise RuntimeError('This control is specifically for an Apple Silicon macOS host')
    evidence = CAMPAIGN / 'evidence' / args.cycle
    lane = json.loads((evidence / 'receipt.json').read_text())
    if (lane.get('cycle') != args.cycle or lane.get('status') != 'RUNNING'
            or lane.get('outputs_before') != [] or os.getcwd() != str(ROOT)
            or Path(os.environ['TMPDIR']) != evidence / 'scratch/tmp'):
        raise RuntimeError('Use only the existing root-owned run_gradle_cycle.py --command lane')
    if any((p / 'gradle/gradle-daemon-jvm.properties').exists() for p in (ROOT, ROOT / 'build-logic')):
        raise RuntimeError('Daemon JVM criteria need a separate review; do not override them silently')
    if not shutil.rmtree.avoids_symlink_attacks or shutil.disk_usage(ROOT).free < 8 * 1024**3:
        raise RuntimeError('FD-safe cleanup and at least8GiB of free disk are required before download/build')
    package = next(p for p in json.loads(METADATA.read_text())['packages'] if p['name'].startswith('OpenJDK21U-jdk_x64_mac_'))
    if package['size'] != SIZE or package['digest'] != 'sha256:' + SHA256:
        raise RuntimeError('Official JDK size/digest receipt disagrees')
    target = evidence / 'rosetta-receipt.json'
    receipt = {'author': '/root/native_fix_review', 'started_epoch': time.time(), 'status': 'RUNNING',
               'scope': 'macOS x64 JVM Desktop graph under Rosetta; NOT Intel hardware or native iOS',
               'source_before': lane['source_before'], 'before_controls': controls,
               'commands': [], 'cleanup_errors': []}
    with target.open('x') as stream:
        json.dump(receipt, stream, indent=2)
    scratch, identity, marker, registry, jdk, build = None, None, None, None, None, None
    build_attempted, creating, interrupted_signal, finalizing = False, False, None, False
    marker_written = False
    handlers = {}

    def cancelled(signum, _frame):
        nonlocal interrupted_signal
        interrupted_signal = signum
        if not creating:
            raise InterruptedError('Root lane cancelled Rosetta cycle')

    def execute(label, command, timeout=60, keep_waitable=False):
        nonlocal creating
        if len(registry.launches) >= 32:
            raise RuntimeError('Rosetta command ceiling exceeded')
        log = evidence / (label + '.log')
        row = {'label': label, 'command': command, 'started_epoch': time.time(), 'log': str(log)}
        receipt['commands'].append(row)
        with log.open('xb') as output:
            creating = True
            try:
                worker = subprocess.Popen(command, cwd=ROOT, env=environment, stdin=subprocess.DEVNULL,
                                          stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
                launch = registry.register(worker, label, command)
            finally:
                creating = False
            if interrupted_signal is not None and not finalizing:
                raise InterruptedError('Cancelled while creating Rosetta worker')
            deadline = time.monotonic() + timeout
            while True:
                code = launch.poll()
                if code is not None:
                    break
                if time.monotonic() >= deadline or log.stat().st_size > MAX_LOG:
                    raise TimeoutError('Rosetta command exceeded runtime/log bound: ' + label)
                time.sleep(0.1)
            row.update(exit_code=code, finished_epoch=time.time())
        if log.stat().st_size > MAX_LOG:
            raise RuntimeError('Rosetta completed-command log exceeded bound')
        if not keep_waitable:
            registry.finish(launch)
        return code, launch, log

    def required(label, command):
        code, _launch, log = execute(label, command)
        if code:
            raise RuntimeError('Public runtime probe failed: ' + label)
        return log

    try:
        for sig in (signal.SIGINT, signal.SIGTERM):
            handlers[sig] = signal.signal(sig, cancelled)
        registry = load_registry()
        creating = True
        try:
            scratch = Path(tempfile.mkdtemp(prefix='parlor-rosetta-', dir='/private/tmp'))
            identity = owned_identity(scratch)
            marker = {'identity': list(identity), 'nonce': str(uuid.uuid4())}
            (scratch / 'owner.json').write_text(json.dumps(marker))
            marker_written = True
        finally:
            creating = False
        receipt.update(owned_scratch=str(scratch), owned_identity=list(identity))
        target.write_text(json.dumps(receipt, indent=2) + '\n')
        if interrupted_signal is not None:
            raise InterruptedError('Root lane cancelled during scratch ownership capture')
        archive = scratch / 'jdk.tar.gz'
        receipt['download'] = download(archive)
        receipt['extraction'] = extract(archive, scratch / 'jdk')
        homes = list((scratch / 'jdk').glob('*/Contents/Home'))
        if len(homes) != 1:
            raise RuntimeError('Unexpected Temurin macOS bundle layout')
        jdk = homes[0].resolve(strict=True)
        if not jdk.is_relative_to(scratch) or not (jdk / 'bin/java').is_file():
            raise RuntimeError('Extracted JDK escaped owned scratch or lacks java')
        receipt['jdk_files'] = {name: digest(jdk / name) for name in
                                ('release', 'bin/java', 'bin/javac', 'lib/security/cacerts', 'conf/security/java.security')}
        environment = os.environ.copy()
        for key in list(environment):
            if key.startswith(('PARLOR_ANDROID_', 'MOBILE_RELEASE_')) or key in (
                    'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'GRADLE_OPTS'):
                environment.pop(key)
        (scratch / 'tmp').mkdir(mode=0o700)
        environment.update(JAVA_HOME=str(jdk), PARLOR_ROSETTA_JDK=str(jdk), PARLOR_ROSETTA_CYCLE=args.cycle,
                           PATH=str(jdk / 'bin') + ':/usr/bin:/bin:/usr/sbin:/sbin:' + environment.get('PATH', ''),
                           TMPDIR=str(scratch / 'tmp'), PYTHONDONTWRITEBYTECODE='1')
        java = str(jdk / 'bin/java')
        arch = required('rosetta-java-mach-o', ['/usr/bin/lipo', '-archs', java]).read_text().strip()
        if arch != 'x86_64':
            raise RuntimeError('Downloaded java is not an exclusively x86_64 Mach-O')
        receipt['java_mach_o_architecture'] = arch
        required('rosetta-java-runtime', [java, '-Xmx128m', '-Djava.io.tmpdir=' + str(scratch / 'tmp'),
                                        str(HERE / 'ArchitectureProbe.java')])
        build_attempted = True
        code, build, _log = execute('rosetta-desktop-build', gradle_command(jdk, args.cycle),
                                   timeout=7200, keep_waitable=True)
        receipt['build_exit_code'] = code
    except BaseException as error:
        receipt['failure'] = type(error).__name__ + ': ' + str(error)
    finally:
        finalizing = True
        for sig in handlers:
            signal.signal(sig, lambda *_: None)
        errors = receipt['cleanup_errors']
        workers_clean = registry is not None
        if registry is not None and 'failure' in receipt:
            # A timed-out/cancelled build may still have a live direct launcher.
            # Stop only its attested descendants before invoking Gradle --stop.
            try:
                failures = registry.shutdown()
                errors.extend(failures)
                workers_clean = not failures
            except BaseException as error:
                errors.append('Interrupted workers: ' + str(error))
                workers_clean = False
        if build_attempted:
            try:
                # Stop immediately after the build; leave XML and module outputs
                # for the outer lane's required capture/cleanup finalizer.
                stop, _launch, _log = execute('rosetta-gradle-stop',
                    ['./gradlew', '--stop', '-Dorg.gradle.java.home=' + str(jdk)], timeout=90)
                receipt['stop_exit_code'] = stop
                if stop:
                    errors.append('x64 Gradle stop returned nonzero')
            except BaseException as error:
                errors.append('x64 Gradle stop: ' + str(error))
        if registry is not None:
            try:
                failures = registry.shutdown()
                errors.extend(failures)
                workers_clean = workers_clean and not failures
            except BaseException as error:
                errors.append('Owned worker shutdown: ' + str(error))
                workers_clean = False
            receipt.update(workers=registry.receipts(), token_signals=registry.events)
        try:
            receipt['after_controls'] = control_binding()
            if receipt['after_controls'] != controls:
                errors.append('Rosetta controls changed during execution')
        except BaseException as error:
            errors.append('Final control binding: ' + str(error))
        if scratch is not None and workers_clean:
            try:
                if owned_identity(scratch) != identity:
                    raise RuntimeError('Task scratch inode/UID changed; preserving')
                if marker_written:
                    fd = os.open(scratch / 'owner.json', os.O_RDONLY | os.O_NOFOLLOW)
                    with os.fdopen(fd) as stream:
                        if json.loads(stream.read(1024)) != marker:
                            raise RuntimeError('Task scratch marker changed; preserving')
                shutil.rmtree(scratch)
                receipt['scratch_removed'] = not os.path.lexists(scratch)
            except BaseException as error:
                errors.append('Scratch cleanup: ' + str(error))
        receipt.update(finished_epoch=time.time(), interrupted_signal=interrupted_signal,
                       module_outputs='Required XML/evidence remain for outer run_gradle_cycle.py capture then cleanup')
        receipt['status'] = 'PASS' if (receipt.get('build_exit_code') == 0 and receipt.get('stop_exit_code') == 0
                                     and not errors and receipt.get('scratch_removed')
                                     and not interrupted_signal and 'failure' not in receipt) else 'FAIL'
        for command in receipt['commands']:
            worker = next((w for w in receipt.get('workers', []) if w['label'] == command['label']), None)
            if worker is not None:
                command['exit_code_after_cleanup'] = worker['exit_code']
            log = Path(command['log'])
            if log.is_file():
                if log.stat().st_size > MAX_LOG:
                    command['truncated_from_bytes'] = log.stat().st_size
                    with log.open('r+b') as stream:
                        stream.truncate(MAX_LOG)
                    receipt['status'] = 'FAIL'
                command['log_sha256'] = digest(log)
        target.write_text(json.dumps(receipt, indent=2) + '\n')
        for sig, handler in handlers.items():
            signal.signal(sig, handler)
    print(receipt['status'] + ': ' + str(target), flush=True)
    return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
