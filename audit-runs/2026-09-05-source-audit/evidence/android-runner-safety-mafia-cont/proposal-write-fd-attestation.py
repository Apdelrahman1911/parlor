#!/usr/bin/env python3
"""Audit-only Android cycle; root alone may execute after independent review.

Synthetic signing and all Android homes are task-owned. No Store keys, existing
AVDs, device discovery, global cache deletion, or implicit SDK installation.
SIGKILL/host death cannot be finalized in-process; a partial ownership receipt is
persisted before native/build execution for manual recovery in that situation.
"""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from run_gradle_cycle import ROOT, OUT, identity

NAME = 'android-managed-01'
SDK = Path('/Users/abdelrahman/Library/Android/sdk')
PASSWORD = 'parlor-managed-device-only'
EXPECTED_CASES = {
    ('com.parlor.app.ReleaseRuntimeSmokeTest',
     'testReleaseBuildLaunchesCanonicalActivityWithoutDebuggableFlag'),
    ('com.parlor.app.ReleaseRuntimeSmokeTest',
     'testReleaseBuildCanAcquireDeclaredMulticastLock'),
    ('com.parlor.app.MainActivityColdStartTest',
     'testColdStartDisplaysContentWhileSettingsIoIsBlocked'),
}


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sanitized(value):
    return str(value).replace(PASSWORD, '[EPHEMERAL_TEST_PASSWORD]')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def write_json(path, value):
    # Only task-owned evidence paths are replaced; old cycles are refused.
    partial = path.with_name(path.name + '.writing')
    partial.write_text(json.dumps(value, indent=2) + '\n')
    partial.replace(path)


def snapshot_processes():
    result = subprocess.run(
        ['ps', '-axo', 'pid=,ppid=,pgid=,lstart=,command='], text=True,
        capture_output=True, timeout=15, env={**os.environ, 'LC_ALL': 'C'},
    )
    if result.returncode:
        raise RuntimeError('Cannot establish process ownership from ps')
    rows = {}
    for line in result.stdout.splitlines():
        parts = line.split(None, 8)
        if len(parts) == 9 and all(x.isdigit() for x in parts[:3]):
            pid, parent, group = map(int, parts[:3])
            rows[pid] = dict(pid=pid, ppid=parent, pgid=group,
                             start=' '.join(parts[3:8]), command=parts[8])
    if os.getpid() not in rows:
        raise RuntimeError('Process snapshot is incomplete')
    return rows


def lsof_pids(arguments):
    result = subprocess.run(
        ['/usr/sbin/lsof', '-nP', '-w', '-t', *arguments], text=True,
        capture_output=True, timeout=30,
    )
    # lsof returns1 for no matches. An actual diagnostic is not an empty scan.
    if result.returncode not in (0, 1) or result.stderr.strip():
        raise RuntimeError('Ownership lsof failed: ' + sanitized(result.stderr[:500]))
    lines = result.stdout.splitlines()
    if any(not line.isdigit() for line in lines):
        raise RuntimeError('Unexpected lsof ownership output')
    return {int(line) for line in lines}


def port_listeners(port):
    return lsof_pids(['-iTCP:' + str(port), '-sTCP:LISTEN'])


def launch_log_writers(path):
    """Only inherited writable stdout/stderr proves launch-log ownership.

    Merely opening a log in an editor/viewer is not task-process ownership.
    Compare device/inode as well as the selected path; fail closed on malformed
    lsof output. This routine never opens or reads log contents.
    """
    stat = path.stat()
    result = subprocess.run(
        ['/usr/sbin/lsof', '-nP', '-w', '-FpfaiD', str(path)], text=True,
        capture_output=True, timeout=30,
    )
    if result.returncode not in (0, 1) or result.stderr.strip():
        raise RuntimeError('Launch-log FD ownership query failed')
    writers, pid, fields = set(), None, {}

    def accept_descriptor():
        if pid is None or fields.get('f') not in ('1', '2') or fields.get('a') not in ('w', 'u'):
            return
        try:
            matches = int(fields['i']) == stat.st_ino and int(fields['D'], 16) == stat.st_dev
        except (KeyError, ValueError) as error:
            raise RuntimeError('Missing/invalid writable-log file identity') from error
        if matches:
            writers.add(pid)

    for line in result.stdout.splitlines():
        if not line:
            continue
        tag, value = line[0], line[1:]
        if tag == 'p':
            accept_descriptor()
            pid, fields = int(value), {}
        elif tag == 'f':
            accept_descriptor()
            fields = {'f': value}
        elif tag in ('a', 'i', 'D'):
            fields[tag] = value
        else:
            raise RuntimeError('Unexpected launch-log ownership field')
    accept_descriptor()
    return writers


class Ownership:
    """Keep PID + start-time identity; never kill by AVD name or port alone."""
    def __init__(self, baseline, temporary, outputs, dest):
        self.baseline = {pid: item['start'] for pid, item in baseline.items()}
        self.temporary = temporary
        self.outputs = outputs
        self.members = {}
        self.last = baseline
        self.unknown_holders = []
        self.handles = []
        self.handle_roles = {}
        self.launch_logs = [dest / name for name in ('adb-server.log', 'gradle.log', 'stop.log')]

    def remember(self, item, role, proof):
        if item['pid'] == os.getpid() or self.baseline.get(item['pid']) == item['start']:
            raise RuntimeError('Refusing ownership of a pre-existing process')
        if len(self.members) >= 1024 and item['pid'] not in self.members:
            raise RuntimeError('Task process ownership ledger limit reached')
        self.members[item['pid']] = {**item, 'role': role, 'proof': proof}

    def register(self, child, role):
        self.handles.append(child)
        self.handle_roles[child.pid] = role
        current = snapshot_processes()
        item = current.get(child.pid)
        if item is not None:
            self.remember(item, role, 'Popen PID observed immediately after owned launch')
        self.last = current

    def refresh(self, inspect_files=False, include_outputs=False):
        current = snapshot_processes()
        # If the first metadata query failed after Popen, an unreaped live child
        # handle still proves ownership; recover that proof before termination.
        for child in self.handles:
            if child.poll() is None and child.pid in current:
                self.remember(current[child.pid], self.handle_roles[child.pid],
                              'Identity recovered from still-live owned Popen child')
        # Repeatedly follow current, identity-checked ancestors/process groups.
        while True:
            alive = {pid: item for pid, item in self.members.items()
                     if pid in current and current[pid]['start'] == item['start']}
            groups = {current[pid]['pgid']: item for pid, item in alive.items()}
            additions = []
            for pid, item in current.items():
                if pid == os.getpid() or pid in alive or self.baseline.get(pid) == item['start']:
                    continue
                owner = alive.get(item['ppid']) or groups.get(item['pgid'])
                if owner is not None:
                    additions.append((item, owner['role'], 'Observed child/group of identity-checked task process'))
            if not additions:
                break
            for args in additions:
                self.remember(*args)
        if inspect_files:
            holders = set()
            roots = [self.temporary] + (self.outputs if include_outputs else [])
            for root in roots:
                if root.is_symlink():
                    raise RuntimeError('Refusing ownership scan through output symlink')
                if root.is_dir():
                    holders.update(lsof_pids(['+D', str(root)]))
            log_holders = set()
            for path in self.launch_logs:
                if path.is_file() and not path.is_symlink():
                    log_holders.update(launch_log_writers(path))
            # Detached emulator/crashpad workers may carry only -avd NAME, not
            # the owned path in argv. Exact open-file ownership supplies proof.
            # Launch stdout also proves ownership if Popen was interrupted after
            # fork but before registration. Audit log readers are never killed.
            for pid in holders | log_holders:
                item = current.get(pid)
                if item is None or pid == os.getpid() or pid in self.members:
                    continue
                native = str(SDK / 'emulator') + '/' in item['command']
                adb = str(SDK / 'platform-tools/adb') in item['command']
                java = '/bin/java' in item['command'] or '/bin/keytool' in item['command']
                harness = 'bash scripts/android/run_release_managed_device_smoke.sh' in item['command']
                if self.baseline.get(pid) != item['start'] and (native or adb or java or harness):
                    self.remember(item, 'detached-task-worker',
                                  'New SDK/JVM/harness PID has exact task-root or launch-log FD open')
            self.unknown_holders = [current[pid] for pid in holders
                                    if pid in current and pid != os.getpid()
                                    and (pid not in self.members or
                                         self.members[pid]['start'] != current[pid]['start'])]
        # A new detached Gradle worker carries our unique java.io.tmpdir even
        # before it opens files. Do not use a generic repository-name match.
        for pid, item in current.items():
            if (pid != os.getpid() and self.baseline.get(pid) != item['start']
                    and pid not in self.members and '/bin/java' in item['command']
                    and '-Djava.io.tmpdir=' + str(self.temporary / 'tmp') in item['command']):
                self.remember(item, 'detached-task-worker', 'Exact task-only JVM temp-directory argument')
        self.last = current
        return [item for pid, item in self.members.items()
                if pid in current and item['start'] == current[pid]['start']]

    def signal_owned(self, item, signum):
        current = snapshot_processes().get(item['pid'])
        if current is None or current['start'] != item['start']:
            return
        try:
            os.kill(item['pid'], signum)
        except ProcessLookupError:
            pass

    def stop(self, selected=lambda item: True):
        # Never remove a live worker's files; discover children during shutdown
        # and escalate only verified, task-owned identities after a bounded wait.
        for signum, seconds in ((signal.SIGTERM, 20), (signal.SIGKILL, 10)):
            deadline = time.monotonic() + seconds
            signaled = set()
            while time.monotonic() < deadline:
                live = [item for item in self.refresh(inspect_files=True) if selected(item)]
                if not live:
                    return
                for item in live:
                    key = (item['pid'], item['start'])
                    if key not in signaled:
                        self.signal_owned(item, signum)
                        signaled.add(key)
                for child in self.handles:
                    child.poll()  # reap our direct children; zombies are not leaks
                time.sleep(0.25)
        live = [item for item in self.refresh(inspect_files=True) if selected(item)]
        if live:
            raise RuntimeError('Verified task workers did not terminate: ' + str([x['pid'] for x in live]))

    def receipt_members(self, items=None):
        return [{**item, 'command': sanitized(item['command'])[:1600]}
                for item in (self.members.values() if items is None else items)]


def run_logged(command, name, env, dest, owner, timeout):
    with (dest / name).open('w') as log:
        child = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log,
                                 stderr=subprocess.STDOUT, start_new_session=True)
        owner.register(child, 'finalizer-command')
        try:
            return child.wait(timeout=timeout)
        except BaseException:
            owner.stop(lambda item: item['role'] == 'finalizer-command')
            child.poll()
            raise


def collect_evidence(dest, outputs):
    """Copy every compact report first; isolate copy/parse/hash failures."""
    errors, copied, unit, instrumented, artifacts = [], [], [], [], []
    for output in outputs:
        for pattern in ('test-results/**/TEST-*.xml', 'reports/detekt/*.xml',
                        'reports/lint-results-*.xml', 'outputs/androidTest-results/**/*.xml'):
            for path in sorted(output.glob(pattern)):
                relative = path.relative_to(ROOT)
                target = dest / 'reports' / relative
                try:
                    if path.is_symlink():
                        raise RuntimeError('Refusing report symlink')
                    if path.stat().st_size > 16 * 1024 * 1024:
                        raise RuntimeError('Report exceeds compact evidence limit')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(path, target)
                    copied.append((relative, target))
                except Exception as error:
                    errors.append(str(relative) + ': ' + sanitized(error))
    for relative, target in copied:
        if 'test-results' not in relative.parts and 'androidTest-results' not in relative.parts:
            continue
        try:
            xml = ET.parse(target).getroot()
            suites = [xml] if xml.tag == 'testsuite' else list(xml.iter('testsuite'))
            for suite in suites:
                entry = dict(file=str(relative), suite=suite.get('name'),
                             tests=suite.get('tests'), failures=suite.get('failures'),
                             errors=suite.get('errors'), skipped=suite.get('skipped'),
                             cases=[dict(name=x.get('name'), classname=x.get('classname'),
                                         failed=x.find('failure') is not None or x.find('error') is not None,
                                         skipped=x.find('skipped') is not None)
                                    for x in suite.findall('testcase')])
                declared = {key: int(suite.get(key, '0'))
                            for key in ('tests', 'failures', 'errors', 'skipped')}
                cases = entry['cases']
                if (declared['tests'] != len(cases)
                        or declared['failures'] + declared['errors'] != sum(case['failed'] for case in cases)
                        or declared['skipped'] != sum(case['skipped'] for case in cases)):
                    errors.append(str(relative) + ': JUnit counters disagree with retained cases')
                (instrumented if 'androidTest-results' in relative.parts else unit).append(entry)
        except Exception as error:
            errors.append(str(relative) + ' parse: ' + sanitized(error))
    for path in sorted((ROOT / 'composeApp/build/outputs').glob('**/*.apk')):
        try:
            if path.is_symlink():
                raise RuntimeError('Refusing artifact symlink')
            artifacts.append(dict(path=str(path.relative_to(ROOT)), bytes=path.stat().st_size,
                                  sha256=digest(path)))
        except Exception as error:
            errors.append(str(path.relative_to(ROOT)) + ' hash: ' + sanitized(error))
    for filename, data in [('test-receipts.json', unit),
                           ('instrumented-test-receipts.json', instrumented),
                           ('artifact-receipts.json', artifacts)]:
        try:
            write_json(dest / filename, data)
        except Exception as error:
            errors.append(filename + ': ' + sanitized(error))
    cases = [case for suite in instrumented for case in suite['cases']]
    actual = {(case['classname'], case['name']) for case in cases
              if not case['failed'] and not case['skipped']}
    return dict(report_errors=errors, instrumented_cases=len(cases),
                failed_cases=sum(case['failed'] for case in cases),
                skipped_cases=sum(case['skipped'] for case in cases),
                missing_expected_cases=[list(case) for case in sorted(EXPECTED_CASES - actual)],
                artifacts_hashed=len(artifacts))


def main():
    from run_gradle_cycle import owned_outputs
    dest = OUT / 'evidence' / NAME
    if dest.exists():
        raise SystemExit('Refusing to overwrite an execution receipt')
    with (OUT / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        dest.mkdir(parents=True)
        receipt = dict(cycle=NAME, started_at=now(), status='RUNNING',
                       execution_kind='gradle-via-checked-in-disposable-signing-harness',
                       signing='Synthetic two-day test key only; no real signing/Store operations.')
        temporary, owner, child, adb, env = None, None, None, None, None
        outputs, eligible_outputs, gradle_started = [], False, False
        cache_links = []
        errors, interrupted = [], []

        def save():
            write_json(dest / 'receipt.json', receipt)

        def stage(name, operation):
            try:
                result = operation()
                receipt.setdefault('finalization_stages', []).append(dict(stage=name, status='PASS', at=now()))
                return result
            except BaseException as error:
                errors.append(dict(stage=name, error_type=type(error).__name__, error=sanitized(error)[:1200]))
                receipt.setdefault('finalization_stages', []).append(dict(stage=name, status='FAIL', at=now()))
                return None

        try:
            # The outer finally covers allocation and every following setup step.
            receipt['source_before'] = identity()
            outputs = owned_outputs()
            existing = [str(path.relative_to(ROOT)) for path in outputs if path.exists() or path.is_symlink()]
            receipt['outputs_before'] = existing
            if existing:
                raise RuntimeError('Pre-existing outputs require ownership review; none will be removed')
            eligible_outputs = True
            baseline = snapshot_processes()
            for required in ('emulator/emulator', 'platform-tools/adb',
                             'system-images/android-35/google_apis/arm64-v8a/package.xml'):
                if not (SDK / required).is_file():
                    raise RuntimeError('Required existing SDK component absent: ' + required)
            cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).expanduser().resolve()
            java = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True, timeout=15).strip()
            temporary = Path(tempfile.mkdtemp(prefix='parlor-audit-android-managed-01-'))
            # Retain the allocated path before resolution/mkdir/receipt I/O can fail.
            receipt['owned_temporary_directory'] = str(temporary)
            temporary = temporary.resolve()
            receipt['owned_temporary_directory'] = str(temporary)
            for path in (temporary / 'home/.android', temporary / 'avd', temporary / 'tmp',
                         temporary / 'gradle-home'):
                path.mkdir(parents=True, mode=0o700)
            # Share only existing dependency/distribution bytes. The daemon
            # registry is isolated, so wrapper --stop cannot kill another task's
            # same-version daemon. Global properties/init/signing are not read.
            receipt['shared_gradle_cache_links'] = cache_links
            for name in ('caches', 'wrapper'):
                target = cache / name
                if not target.is_dir():
                    raise RuntimeError('Required existing shared Gradle cache absent: ' + name)
                link = temporary / 'gradle-home' / name
                link.symlink_to(target, target_is_directory=True)
                cache_links.append(dict(link=str(link), target=str(target)))
            owner = Ownership(baseline, temporary, outputs, dest)
            env = {key: os.environ[key] for key in ('USER', 'LOGNAME', 'PATH', 'LANG', 'SHELL') if key in os.environ}
            env.update(HOME=str(temporary / 'home'), GRADLE_USER_HOME=str(temporary / 'gradle-home'), JAVA_HOME=java,
                       PATH=java + '/bin:' + env.get('PATH', ''), LC_ALL='C',
                       ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK),
                       ANDROID_USER_HOME=str(temporary / 'home/.android'),
                       ANDROID_EMULATOR_HOME=str(temporary / 'home/.android'),
                       ANDROID_AVD_HOME=str(temporary / 'avd'),
                       ADB_USB='0', ADB_MDNS='0', ADB_EMU='0',
                       TMPDIR=str(temporary / 'tmp') + '/', PYTHONDONTWRITEBYTECODE='1')
            jvm = '-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Djava.io.tmpdir=' + str(temporary / 'tmp')
            opts = ['-Dorg.gradle.jvmargs=' + jvm, '-Dorg.gradle.parallel=false',
                    '-Dorg.gradle.caching=false', '-Dorg.gradle.configuration-cache=false',
                    '-Dorg.gradle.project.android.builder.sdkDownload=false',
                    '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process']
            opts += ['-Dorg.gradle.project.parlor.android.signing.' + key + '=' for key in
                     ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')]
            env['GRADLE_OPTS'] = shlex.join(opts)
            script = ROOT / 'scripts/android/run_release_managed_device_smoke.sh'
            receipt.update(command=['bash', str(script.relative_to(ROOT))],
                           baseline_process_count=len(baseline), script_sha256=digest(script),
                           environment_policy='Task HOME/Android/AVD/TMP/Gradle registry; only existing caches+wrapper symlinked; USB/mDNS/emulator discovery off; owned foreground ADB; JDK21;3g;strict wrapper;no implicit SDK downloads;real signing blank.',
                           platform_qualification='Darwin/ARM64 API35 existing image; not Linux/x86_64 CI or real device.')
            inputs = [Path(__file__).resolve(), OUT / 'run_gradle_cycle.py', script, ROOT / 'gradlew',
                      ROOT / 'composeApp/build.gradle.kts', ROOT / 'build.gradle.kts',
                      ROOT / 'composeApp/src/androidInstrumentedTest/java/com/parlor/app/ReleaseRuntimeSmokeTest.java',
                      ROOT / 'composeApp/src/androidInstrumentedTest/kotlin/com/parlor/app/MainActivityColdStartTest.kt']
            manifest = dict(recorded_at=now(), source=receipt['source_before'],
                            files=[dict(path=str(path.relative_to(ROOT)), sha256=digest(path)) for path in inputs])
            write_json(dest / 'input-manifest.json', manifest)
            receipt['input_manifest_sha256'] = digest(dest / 'input-manifest.json')
            save()
            with socket.socket() as reservation:
                reservation.bind(('127.0.0.1', 0))
                port = reservation.getsockname()[1]
                env.update(ANDROID_ADB_SERVER_PORT=str(port), ADB_SERVER_SOCKET='tcp:127.0.0.1:' + str(port))
                receipt['adb_port'] = port
            # A competing bind is harmless: never kill by port. Require our exact
            # foreground PID to own the listener before the build may start.
            with (dest / 'adb-server.log').open('w') as log:
                adb = subprocess.Popen([str(SDK / 'platform-tools/adb'), '-L',
                                        'tcp:127.0.0.1:' + str(port), 'server', 'nodaemon'],
                                       cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            owner.register(adb, 'adb-server')
            receipt['adb_owned_pid'] = adb.pid
            deadline = time.monotonic() + 20
            while True:
                listeners = port_listeners(port)
                if adb.poll() is not None:
                    raise RuntimeError('Owned foreground ADB server exited before readiness')
                if listeners == {adb.pid}:
                    break
                if listeners or time.monotonic() >= deadline:
                    raise RuntimeError('ADB port ownership could not be attested; no foreign server will be killed')
                time.sleep(0.1)
            receipt['adb_listener_attested'] = True
            receipt['ownership_before_build'] = owner.receipt_members()
            save()
            with (dest / 'gradle.log').open('w') as log:
                # Mark the attempt before launch so an interruption immediately
                # after Popen cannot suppress the required wrapper stop.
                gradle_started = True
                child = subprocess.Popen(receipt['command'], cwd=ROOT, env=env, stdout=log,
                                         stderr=subprocess.STDOUT, start_new_session=True)
                owner.register(child, 'gradle')
                receipt['owned_build_pid'] = child.pid
                save()
                deadline, next_files, next_save = time.monotonic() + 1500, 0, 0
                while child.poll() is None:
                    instant = time.monotonic()
                    owner.refresh(inspect_files=instant >= next_files)
                    if instant >= next_files:
                        next_files = instant + 5
                        if adb.poll() is not None or port_listeners(port) != {adb.pid}:
                            raise RuntimeError('Owned ADB server/listener changed during the build')
                    if instant >= next_save:
                        receipt['ownership_observed'] = owner.receipt_members()
                        receipt['last_observed_at'] = now()
                        save()
                        next_save = instant + 15
                    if instant >= deadline:
                        raise subprocess.TimeoutExpired(receipt['command'], 1500)
                    time.sleep(0.25)
                receipt['exit_code'] = child.returncode
                receipt['finished_at'] = now()
        except BaseException as error:
            receipt['error'] = dict(type=type(error).__name__, message=sanitized(error)[:1600])
            if isinstance(error, (KeyboardInterrupt, subprocess.TimeoutExpired)):
                receipt['interruption'] = type(error).__name__
        finally:
            # Defer repeated INT/TERM to a bounded ledger; do not let a second
            # cancellation bypass stop, owned-worker shutdown or final receipt.
            def defer(signum, _frame):
                if len(interrupted) < 16:
                    interrupted.append(signum)
            old_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            old_handlers = {signum: signal.signal(signum, defer) for signum in (signal.SIGINT, signal.SIGTERM)}
            signal.pthread_sigmask(signal.SIG_SETMASK, old_mask)
            try:
                if child is not None and child.poll() is None and owner is not None:
                    stage('terminate-interrupted-build', lambda: owner.stop(lambda item: item['role'] == 'gradle'))
                    receipt['exit_code'] = child.poll()
                receipt.setdefault('finished_at', now())
                if gradle_started and owner is not None and env is not None:
                    def stop_gradle():
                        code = run_logged(['./gradlew', '--stop'], 'stop.log', env, dest, owner, 90)
                        receipt['stop_exit_code'] = code
                        if code:
                            raise RuntimeError('Required Gradle stop failed with exit ' + str(code))
                    stage('immediate-gradle-stop', stop_gradle)
                    receipt['stopped_at'] = now()
                else:
                    receipt['stop_not_required'] = 'No Gradle process was launched in this attempt'
                if owner is not None:
                    stage('stop-task-workers-before-output-removal', lambda: owner.stop(lambda item: item['role'] != 'adb-server'))
                    # PID/start-checked signal is safer than adb kill-server to a
                    # port which another unrelated process could have acquired.
                    stage('stop-owned-adb-before-temp-removal', owner.stop)
                    receipt['adb_stop_method'] = 'TERM then bounded KILL of identity-checked owned PIDs; never kill a port/AVD name'
                    receipt['adb_process_exit_code'] = adb.poll() if adb is not None else None
                if eligible_outputs and gradle_started:
                    evidence = stage('collect-required-evidence', lambda: collect_evidence(dest, outputs))
                    receipt['verification'] = evidence
                    if evidence is not None and evidence['report_errors']:
                        errors.append(dict(stage='collect-required-evidence', error='Some evidence collection failed; see verification.report_errors'))
                cleanup_safe = owner is None
                if owner is not None:
                    def verify_workers():
                        live = owner.refresh(inspect_files=True, include_outputs=True)
                        receipt['ownership_observed'] = owner.receipt_members()
                        receipt['owned_processes_remaining'] = owner.receipt_members(live)
                        receipt['unattributed_open_file_holders'] = owner.receipt_members(owner.unknown_holders)
                        if live or owner.unknown_holders:
                            raise RuntimeError('Workers/open-file holders remain; preserve outputs and task root for targeted cleanup')
                        return True
                    cleanup_safe = stage('verify-no-live-workers-or-unattributed-holders', verify_workers) is True
                removed = []
                if cleanup_safe and eligible_outputs and gradle_started:
                    for path in outputs:
                        def remove_output(path=path):
                            if path.is_symlink():
                                raise RuntimeError('Refusing output symlink')
                            if path.exists():
                                shutil.rmtree(path)
                                removed.append(str(path.relative_to(ROOT)))
                        stage('remove-' + str(path.relative_to(ROOT)), remove_output)
                if temporary is not None and cleanup_safe:
                    def remove_temporary():
                        if temporary.is_symlink():
                            raise RuntimeError('Refusing task-root symlink')
                        # Explicitly unlink only our cache pointers; never follow
                        # them during tree removal or delete their targets.
                        for item in cache_links:
                            link = Path(item['link'])
                            if not link.is_symlink():
                                raise RuntimeError('Owned cache symlink unexpectedly replaced; preserve task root')
                            link.unlink()
                        receipt['shared_gradle_cache_links_unlinked'] = len(cache_links)
                        if temporary.exists():
                            shutil.rmtree(temporary)
                    stage('remove-owned-android-home-avd-temp-and-test-keys', remove_temporary)
                if not cleanup_safe:
                    receipt['retention_reason'] = 'Live/unknown worker ownership not settled; no filesystem removal attempted'
                receipt.update(removed_outputs=removed,
                               remaining_outputs=[str(path.relative_to(ROOT)) for path in outputs if path.exists() or path.is_symlink()],
                               temporary_directory_removed=temporary is None or not temporary.exists(),
                               cleanup_method='Identity-checked worker termination; exact task-created build/AVD/home/tmp removal; global caches and pre-existing user state preserved.',
                               cleanup_completed_at=now(), cleanup_interrupts_deferred=interrupted)
                receipt['source_after'] = stage('record-final-source-identity', identity)
                def sanitize_log():
                    path = dest / 'gradle.log'
                    if path.exists():
                        replacement = path.with_suffix('.sanitized')
                        with path.open(errors='replace') as source, replacement.open('w') as target:
                            for line in source:
                                target.write(sanitized(line))
                        replacement.replace(path)
                stage('sanitize-test-password-log', sanitize_log)
                receipt['cleanup_errors'] = errors
                receipt['cleanup_status'] = 'PASS' if (not errors and cleanup_safe
                    and receipt['temporary_directory_removed'] and not receipt['remaining_outputs']
                    and (not gradle_started or receipt.get('stop_exit_code') == 0)) else 'FAIL'
                result = receipt.get('verification') or {}
                receipt['runtime_evidence_status'] = 'PASS' if (
                    receipt.get('exit_code') == 0 and not receipt.get('error')
                    and not result.get('report_errors', ['missing evidence'])
                    and result.get('instrumented_cases', 0) >= len(EXPECTED_CASES)
                    and result.get('failed_cases') == 0 and result.get('skipped_cases') == 0
                    and result.get('missing_expected_cases') == [] and result.get('artifacts_hashed', 0) > 0
                ) else 'FAIL'
                receipt['status'] = 'PASS' if (receipt['runtime_evidence_status'] == 'PASS'
                    and receipt['cleanup_status'] == 'PASS') else 'FAIL'
            except BaseException as error:
                # Even an unexpected failure in finalizer bookkeeping must
                # leave an honest partial receipt, not the original RUNNING one.
                errors.append(dict(stage='outer-finalizer', error_type=type(error).__name__,
                                   error=sanitized(error)[:1200]))
                receipt.update(status='FAIL', cleanup_status='FAIL')
            finally:
                receipt['cleanup_errors'] = errors
                receipt['cleanup_interrupts_deferred'] = interrupted
                receipt.setdefault('cleanup_completed_at', now())
                receipt.setdefault('cleanup_status', 'FAIL')
                receipt.setdefault('runtime_evidence_status', 'FAIL')
                if receipt.get('status') == 'RUNNING':
                    receipt['status'] = 'FAIL'
                try:
                    save()
                except BaseException as error:
                    receipt['status'] = 'FAIL'
                    receipt['receipt_write_error'] = type(error).__name__
                try:
                    print(json.dumps(receipt, indent=2), flush=True)
                except (BrokenPipeError, OSError):
                    pass  # The on-disk receipt is authoritative if stdout closes.
                signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
                for signum, handler in old_handlers.items():
                    signal.signal(signum, handler)
                signal.pthread_sigmask(signal.SIG_SETMASK, old_mask)
        return 0 if receipt.get('status') == 'PASS' else 1


if __name__ == '__main__':
    def interrupted_signal(signum, _frame):
        raise KeyboardInterrupt('signal ' + str(signum))
    signal.signal(signal.SIGTERM, interrupted_signal)
    raise SystemExit(main())
