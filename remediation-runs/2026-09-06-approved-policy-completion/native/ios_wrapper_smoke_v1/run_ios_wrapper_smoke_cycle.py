#!/usr/bin/env python3
"""Source-identical application/test wrapper smoke; root's sole build lane.

All application and test bytes come unchanged from an explicitly frozen manifest.
Only the copied Xcode Gradle phase changes to preserve immediate isolated stops.
No observers, synthetic language invocation, private data reads or new UI tests.
Root executes only after independent review. This smoke is not the DS-C01 matrix.
"""
import datetime
import difflib
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import plistlib
import re
import shlex
import shutil
import signal
import subprocess
import sys
import hashlib
import tempfile
import time

HERE = Path(__file__).resolve().parent
RUN = HERE.parents[1]
REPOSITORY = RUN.parents[1]
# Imported immutable helpers are separately hashed review controls. The root
# remediation identity hashes current dirty/untracked source, NOT just HEAD.
sys.path.insert(0, str(RUN))
sys.path.append(str(REPOSITORY / 'audit-runs/2026-09-05-source-audit'))
from run_gradle_cycle import ROOT, OUT, identity, owned_outputs
from run_android_managed_cycle import Ownership, snapshot_processes, lsof_pids, digest, write_json
from secondary_fifo import SecondaryFifoLedger
from copied_sources import create_source_copy, inspect_copied_inputs_after_build

FIXTURE = HERE
APP_ID = 'com.parlor.app.debug'
EXPECTED_TEST = 'IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()'
EXPECTED_TESTS = {EXPECTED_TEST} | {'ComposeContainerViewControllerTests/' + name + '()' for name in (
    'testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView',
    'testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections',
    'testSystemDecorationAndOrientationPoliciesStillBelongToTheChild',
    'testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce',
)}


def normalized_identity():
    return json.loads(json.dumps(identity()))


def control_files():
    return [Path(__file__).resolve(), RUN / 'run_gradle_cycle.py',
            ROOT / 'audit-runs/2026-09-05-source-audit/run_android_managed_cycle.py',
            HERE.parent / 'dsc01_apphost_v6/run_dsc01_apphost_cycle.py'] + [
        HERE / name for name in ('secondary_fifo.py', 'test_secondary_fifo.py',
        'copied-kotlin-phase.sh.in', 'source-bindings.json', 'copied_sources.py',
        'bind_source.py', 'test_smoke_contract.py')]


def control_manifest():
    result = []
    for path in control_files():
        if not path.is_file() or path.is_symlink():
            raise RuntimeError('Missing or symlinked reviewed harness control')
        result.append(dict(path=str(path.relative_to(ROOT)), sha256=digest(path)))
    return result


def control_hash():
    return hashlib.sha256(json.dumps(control_manifest(), separators=(',', ':')).encode()).hexdigest()


def process_uid(pid):
    result = subprocess.run(['ps', '-o', 'uid=', '-p', str(pid)], text=True,
                            capture_output=True, timeout=10)
    value = result.stdout.strip()
    if result.returncode or result.stderr.strip() or not value.isdigit():
        raise RuntimeError('Cannot verify task-owned Apple worker UID')
    return int(value)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


@contextmanager
def defer_parent_signals():
    """Defer interruption only in this Python parent, never block child masks.

    Caught signal dispositions reset on exec. Blocking a signal around Popen
    would instead leak that mask into Gradle/Xcode and impede orderly shutdown.
    """
    pending = []

    def defer(sig, _frame):
        if len(pending) < 16:
            pending.append(sig)

    old = {s: signal.signal(s, defer) for s in (signal.SIGINT, signal.SIGTERM)}
    try:
        yield
    finally:
        for sig, handler in old.items():
            signal.signal(sig, handler)
        for sig in pending:
            handler = old[sig]
            if callable(handler):
                handler(sig, None)
            elif handler != signal.SIG_IGN:
                raise KeyboardInterrupt('deferred signal ' + str(sig))


def inspect_smoke_manifest(copy_root, bindings):
    """Exactly one build-phase edit; every application/test input stays identical."""
    if any(path.is_symlink() for path in copy_root.rglob('*')):
        raise RuntimeError('No symbolic links are permitted in the source-only build copy')
    phase_project = 'iosApp/iosApp.xcodeproj/project.pbxproj'
    result, changed = [], set()
    entries = bindings['copy_only']
    if len(entries) != len({entry['path'] for entry in entries}):
        raise RuntimeError('Duplicate source binding path')
    for entry in entries:
        current = digest(copy_root / entry['path'])
        if current != entry['sha256']:
            changed.add(entry['path'])
        result.append(dict(path=entry['path'], original_sha256=entry['sha256'], copied_sha256=current))
    if changed != {phase_project}:
        raise RuntimeError('Smoke permits only the reviewed copied Gradle phase edit')
    actual = {str(path.relative_to(copy_root)) for path in copy_root.rglob('*') if path.is_file()}
    if actual != {entry['path'] for entry in result}:
        raise RuntimeError('Unregistered source additions/removals in the smoke copy')
    return result


def verify_xctest(summary, tests, uuid):
    expected = dict(result='Passed', totalTestCount=5, passedTests=5,
                    failedTests=0, skippedTests=0, expectedFailures=0)
    if any(summary.get(key) != value for key, value in expected.items()) or summary.get('testFailures'):
        raise RuntimeError('Actual XCTest summary is not exactly five successful, unskipped tests')
    configurations = summary.get('devicesAndConfigurations', [])
    devices = tests.get('devices', [])
    if (len(configurations) != 1 or len(devices) != 1 or
            any(device.get('deviceId') != uuid or device.get('architecture') != 'arm64' or
                device.get('platform') != 'iOS Simulator'
                for device in [configurations[0].get('device', {}), devices[0]])):
        raise RuntimeError('XCTest did not run exclusively on the owned ARM64 simulator')
    cases = []

    def visit(nodes):
        for node in nodes:
            if node.get('nodeType') == 'Test Case':
                cases.append(node)
            visit(node.get('children', []))

    visit(tests.get('testNodes', []))
    if (len(cases) != 5 or {case.get('nodeIdentifier') for case in cases} != EXPECTED_TESTS or
            any(case.get('result') != 'Passed' for case in cases)):
        raise RuntimeError('Every exact production-container XCTest and original cold-launch test must pass')
    return dict(method=EXPECTED_TEST, methods=sorted(EXPECTED_TESTS), result='Passed', total=5,
                skipped=0, failures=0, device_uuid=uuid)


class AppHostOwnership(Ownership):
    def __init__(self, baseline, temporary, outputs, dest):
        super().__init__(baseline, temporary, outputs, dest)
        self.secondary_errors = {}
        self.secondary_error_file = dest / 'secondary-fifo-errors.json'
        self.secondary = SecondaryFifoLedger(time.time(),
            lambda value: write_json(dest / 'secondary-fifo-ownership.json', value),
            process_uid, snapshot_processes, lsof_pids, owned_primary=temporary)

    def refresh(self, inspect_files=False, include_outputs=False):
        # Use observed Popen descendants/process groups and the explicit
        # task-only JVM tmpdir argument. Do NOT use the parent's Android/JVM
        # open-file adoption: a viewer or shared service may read our artifacts.
        super().refresh(inspect_files=False, include_outputs=False)
        for process in list(self.members.values()):
            if self.last.get(process['pid'], {}).get('start') == process['start']:
                key = str(process['pid']) + ':' + process['start']
                if key not in self.secondary_errors:
                    try:
                        self.secondary.observe(process, self.members, self.last)
                    except Exception as error:
                        # Attestation failure is never cleanup success, but must
                        # not inhibit PID-owned worker stop/finalization itself.
                        self.secondary_errors[key] = dict(pid=process['pid'], start=process['start'],
                            error_type=type(error).__name__, message=str(error)[:300])
                        write_json(self.secondary_error_file, list(self.secondary_errors.values()))
        if inspect_files:
            holders = set()
            for root in [self.temporary] + (self.outputs if include_outputs else []):
                if root.is_symlink():
                    raise RuntimeError('Refuse ownership scan through output symlink')
                if root.is_dir():
                    holders.update(lsof_pids(['+D', str(root)]))
            self.unknown_holders = [self.last[pid] for pid in holders
                                    if pid in self.last and pid != os.getpid() and
                                    (pid not in self.members or self.members[pid]['start'] != self.last[pid]['start'])]
        return [item for pid, item in self.members.items()
                if pid in self.last and item['start'] == self.last[pid]['start']]


def main():
    if len(sys.argv) != 3 or not re.fullmatch(r'ios-wrapper-smoke-[0-9]{2}', sys.argv[1]):
        raise SystemExit('Usage: run_ios_wrapper_smoke_cycle.py ios-wrapper-smoke-NN INDEPENDENTLY_REVIEWED_CONTROL_SHA256')
    NAME = sys.argv[1]
    approved = sys.argv[2]
    if not re.fullmatch('[a-f0-9]{64}', approved) or approved != control_hash():
        raise SystemExit('Control hashes differ from explicit independent execution review')
    dest = OUT / 'evidence' / NAME
    with (OUT / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        dest.mkdir(parents=True, exist_ok=False)
        receipt = dict(cycle=NAME, started_at=now(), status='RUNNING',
                       execution_kind='manifest-owned-copy-unsigned-ios-source-identical-wrapper-smoke', commands=[],
                       runtime_evidence_status='NOT_RUN', cleanup_status='BLOCKED',
                       scope='One unchanged cold-launch/home-brand/no-alert/foreground UI test plus four unchanged production-container UIKit tests. No Kotlin/Swift application or test instrumentation; copied Gradle stop phase only. Not language ownership, active-session retention, Settings, physical LAN, full-game, signed-release or Store proof.', approved_control_sha256=approved)
        temp, owner, env, uuid = None, None, None, None
        # This iteration compiles only inside temp/copy. Original-repository
        # outputs are never task-owned and must never be deleted by this run.
        original_outputs = owned_outputs() + [ROOT / 'iosApp/build']
        links, errors, outputs = [], [], []
        copied_source_manifest = None
        eligible, gradle_attempted = False, False

        def save():
            write_json(dest / 'receipt.json', receipt)

        def stage(label, fn):
            try:
                value = fn()
                if type(value) is int and value != 0:
                    raise RuntimeError('Finalization command returned exit ' + str(value))
                receipt.setdefault('finalization_stages', []).append(dict(stage=label, status='PASS', at=now()))
                return value
            except BaseException as error:
                errors.append(dict(stage=label, error_type=type(error).__name__, message=str(error)[:600]))
                receipt.setdefault('finalization_stages', []).append(dict(stage=label, status='FAIL', at=now()))
                return None

        def command(args, filename, timeout=120):
            entry = dict(command=[str(a) for a in args], started_at=now(), log=filename)
            receipt['commands'].append(entry); save()
            with (dest / filename).open('w') as log:
                child = None
                try:
                    with defer_parent_signals():
                        child = subprocess.Popen(entry['command'], cwd=ROOT, env=env, stdout=log,
                                                 stderr=subprocess.STDOUT, start_new_session=True)
                        owner.register(child, 'command')
                    deadline = time.monotonic() + timeout
                    while child.poll() is None:
                        owner.refresh()
                        if time.monotonic() >= deadline:
                            raise subprocess.TimeoutExpired(entry['command'], timeout)
                        time.sleep(0.25)
                    entry['exit_code'] = child.returncode
                except BaseException:
                    # All direct command groups are task-owned; no global pkill.
                    if child is not None:
                        owner.stop(lambda item: item['role'] == 'command')
                        entry['exit_code'] = child.poll()
                    entry['interrupted_or_failed'] = True
                    raise
                finally:
                    entry['finished_at'] = now(); save()
            return entry['exit_code']

        def stop_gradle(label):
            code = command(['./gradlew', '--stop'], label + '.log', 90)
            receipt.setdefault('gradle_stops', []).append(dict(label=label, exit_code=code, at=now()))
            if code:
                raise RuntimeError('Required isolated Gradle stop failed')

        def own_simulator_metadata(label):
            # Device names/UUIDs are non-player metadata, but retain ONLY our
            # unique name. Never log unrelated simulator metadata or containers.
            args = ['xcrun', 'simctl', 'list', 'devices', '-j']
            entry = dict(command=args, started_at=now(), log=label + '.json',
                         retention='Only exact synthetic owned-name matches retained')
            receipt['commands'].append(entry); save()
            child = None
            try:
                with defer_parent_signals():
                    child = subprocess.Popen(args, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                             stderr=subprocess.PIPE, start_new_session=True)
                    owner.register(child, 'command')
                stdout, stderr = child.communicate(timeout=45)
                entry['exit_code'] = child.returncode
                if child.returncode or stderr.strip() or len(stdout) > 2 * 1024 * 1024:
                    raise RuntimeError('Cannot verify own simulator metadata; raw unrelated data not retained')
                parsed = json.loads(stdout)
                matches = [dict(udid=d['udid'], name=d['name'], state=d['state'], runtime=runtime)
                           for runtime, rows in parsed['devices'].items() for d in rows
                           if d.get('name') == receipt['owned_device_name']]
                if len(matches) > 1:
                    raise RuntimeError('Ambiguous synthetic simulator identity; no deletion authorized')
                if matches and not re.fullmatch(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}', matches[0]['udid']):
                    raise RuntimeError('Unexpected synthetic simulator UUID')
                if matches and uuid is not None and matches[0]['udid'] != uuid:
                    raise RuntimeError('Synthetic simulator identity changed')
                write_json(dest / (label + '.json'), dict(matches=matches))
                return matches[0] if matches else None
            except BaseException:
                if child is not None:
                    owner.stop(lambda item: item['role'] == 'command')
                raise
            finally:
                entry['finished_at'] = now(); save()

        def shutdown_owned_device():
            info = own_simulator_metadata('owned-device-before-shutdown')
            if info is None or info['state'] == 'Shutdown':
                receipt['shutdown_disposition'] = 'already_absent' if info is None else 'already_shutdown'
                return
            code = command(['xcrun', 'simctl', 'shutdown', uuid], 'shutdown.log')
            receipt['shutdown_exit_code'] = code
            after = own_simulator_metadata('owned-device-after-shutdown')
            if code != 0 or after is None or after['state'] != 'Shutdown':
                raise RuntimeError('Owned simulator shutdown did not complete successfully')

        def delete_owned_device():
            info = own_simulator_metadata('owned-device-before-delete')
            if info is not None:
                if info['state'] != 'Shutdown':
                    raise RuntimeError('Refuse deletion before owned simulator is shut down')
                code = command(['xcrun', 'simctl', 'delete', uuid], 'delete.log')
                receipt['delete_exit_code'] = code
                if code != 0:
                    raise RuntimeError('Owned simulator deletion command failed')
            after = own_simulator_metadata('owned-device-after-delete')
            path = Path.home() / 'Library/Developer/CoreSimulator/Devices' / uuid
            receipt['owned_device_absent'] = after is None and not path.exists() and not path.is_symlink()
            if not receipt['owned_device_absent']:
                raise RuntimeError('Owned simulator metadata or directory remains')

        try:
            save()  # Durable incomplete receipt before any allocation/native work.
            receipt['source_before'] = normalized_identity()
            if any(p.exists() or p.is_symlink() for p in original_outputs):
                raise RuntimeError('Pre-existing outputs; ownership review required')
            eligible = True
            baseline = snapshot_processes()
            if any('org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.13' in item['command'] or
                   item['command'].split()[0].endswith('/xcodebuild') for item in baseline.values()):
                raise RuntimeError('Pre-existing Gradle/Xcode worker: do not compete or stop another task')
            with defer_parent_signals():
                raw_temp = tempfile.mkdtemp(prefix='parlor-audit-' + NAME + '-')
                temp = Path(raw_temp)
                receipt['allocated_temporary_path'] = raw_temp
                receipt['owned_temporary_directory'] = str(temp)
                save()
            # If canonicalization fails, temp and its durable raw-path receipt
            # already exist, so the finalizer still owns the allocation.
            temp = temp.resolve()
            receipt['owned_temporary_directory'] = str(temp)
            receipt['owned_device_name'] = 'Parlor-Audit-' + temp.name
            save()
            owner = AppHostOwnership(baseline, temp, outputs, dest)
            owner.launch_logs += [dest / n for n in ('framework.log', 'xcodebuild.log')]
            for sub in ('tmp', 'gradle-home', 'copy'):
                (temp / sub).mkdir(mode=0o700)
            cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).resolve()
            for name in ('caches', 'wrapper'):
                target = cache / name
                if not target.is_dir():
                    raise RuntimeError('Existing dependency/distribution cache absent')
                link = temp / 'gradle-home' / name; link.symlink_to(target, target_is_directory=True)
                links.append(dict(link=str(link), target=str(target)))
                receipt['shared_cache_links'] = links
                save()
            receipt['shared_cache_links'] = links
            java = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True, timeout=15).strip()
            env = {k: os.environ[k] for k in ('HOME', 'USER', 'LOGNAME') if k in os.environ}
            env.update(PATH=java + '/bin:/usr/bin:/bin:/usr/sbin:/sbin', JAVA_HOME=java,
                       LANG='en_US.UTF-8', LC_ALL='C', GRADLE_USER_HOME=str(temp / 'gradle-home'),
                       TMPDIR=str(temp / 'tmp') + '/', PYTHONDONTWRITEBYTECODE='1',
                       CONFIGURATION='Debug',
                       CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO', CODE_SIGN_IDENTITY='',
                       DEVELOPMENT_TEAM='')
            # KGP2.4.10 treats even an empty EXPANDED_CODE_SIGN_IDENTITY as an
            # instruction to call codesign. Absence, not a blank identity, is
            # required for this unsigned audit. No real/ad-hoc identity supplied.
            receipt['expanded_signing_identity_policy'] = 'omitted from whitelisted environment; unsigned simulator only'
            jvm = '-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Djava.io.tmpdir=' + str(temp / 'tmp')
            opts = ['-Dorg.gradle.jvmargs=' + jvm, '-Dorg.gradle.parallel=false', '-Dorg.gradle.workers.max=1',
                    '-Dorg.gradle.configuration-cache=false', '-Dorg.gradle.caching=false',
                    '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process']
            opts += ['-Dorg.gradle.project.parlor.android.signing.' + key + '=' for key in
                     ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')]
            env['GRADLE_OPTS'] = shlex.join(opts)
            android_sdk = Path.home() / 'Library/Android/sdk'
            if not android_sdk.is_dir():
                raise RuntimeError('Known public SDK installation absent; never copy private local.properties')
            env['ANDROID_HOME'] = str(android_sdk)
            env['ANDROID_SDK_ROOT'] = str(android_sdk)
            if command(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version'], 'sdk-version.log'):
                raise RuntimeError('SDK version unavailable')
            sdk_version = (dest / 'sdk-version.log').read_text().strip()
            if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*', sdk_version):
                raise RuntimeError('Unexpected SDK version')
            env['SDK_NAME'] = 'iphonesimulator' + sdk_version
            receipt['sdk_name'] = env['SDK_NAME']
            bindings = json.loads((FIXTURE / 'source-bindings.json').read_text())
            if receipt['source_before'] != bindings['source_identity']:
                raise RuntimeError('Dirty/untracked source differs from independently reviewed fixture binding')
            if bindings['repository'] != str(ROOT) or bindings['schema_version'] != 3:
                raise RuntimeError('Unexpected source-identical smoke source binding')
            for entry in bindings['copy_only']:
                p = ROOT / entry['path']
                if p.is_symlink() or p.resolve() != p.absolute() or digest(p) != entry['sha256']:
                    raise RuntimeError('Source-bound temporary-wrapper input changed')
            write_json(dest / 'input-manifest.json', dict(source=receipt['source_before'],
                       control_sha256=approved, files=control_manifest()))
            manifest = bindings
            create_source_copy(ROOT, temp / 'copy', manifest)
            save()
            # Keep every Kotlin/Swift/test/resource byte unchanged. Only the
            # copied build phase gains the already-reviewed immediate Gradle stop.
            project = temp / 'copy/iosApp/iosApp.xcodeproj/project.pbxproj'
            pbx = project.read_text()
            old_search = '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
            if pbx.count(old_search) != 2:
                raise RuntimeError('Unexpected framework search bindings')
            # Existing relative search paths now correctly target this same
            # manifest-owned copied graph; never link an original/stale framework.
            phase = (FIXTURE / 'copied-kotlin-phase.sh.in').read_text()
            phase = phase.replace('__PARLOR_SOURCE_ROOT__', str(temp / 'copy')).replace('__PARLOR_STOP_RECEIPT__', str(dest / 'embedded-gradle-stop.txt'))
            if '__PARLOR_' in phase:
                raise RuntimeError('Unresolved copied phase token')
            pattern = r'(shellScript = )"(?:[^"\\]|\\.)*";'
            candidates = list(re.finditer(pattern, pbx))
            if len(candidates) != 1:
                raise RuntimeError('Unexpected Xcode script phase count')
            candidate = candidates[0]
            if 'embedAndSignAppleFrameworkForXcode' not in candidate.group(0):
                raise RuntimeError('Wrong Xcode phase target')
            pbx = pbx[:candidate.start()] + 'shellScript = ' + json.dumps(phase) + ';' + pbx[candidate.end():]
            project.write_text(pbx)
            changes = ['iosApp/iosApp.xcodeproj/project.pbxproj']
            diff = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
                (temp / 'copy' / path).read_text().splitlines(True),
                fromfile='original/' + path, tofile='audit-copy/' + path)) for path in changes)
            (dest / 'copied-source.diff').write_text(diff)
            copied_source_manifest = inspect_smoke_manifest(temp / 'copy', manifest)
            write_json(dest / 'copied-source-manifest.json', copied_source_manifest)
            receipt['copied_source_diff_sha256'] = digest(dest / 'copied-source.diff')
            receipt['copied_source_manifest_sha256'] = digest(dest / 'copied-source-manifest.json')
            receipt['copy_only_build_root'] = str(temp / 'copy')
            receipt['simulator_creation_attempted'] = True; save()
            if command(['xcrun', 'simctl', 'create', receipt['owned_device_name'],
                        'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro',
                        'com.apple.CoreSimulator.SimRuntime.iOS-26-5'], 'create.log'):
                raise RuntimeError('New simulator creation failed')
            uuid = (dest / 'create.log').read_text().strip()
            if not re.fullmatch(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}', uuid):
                uuid = None; raise RuntimeError('Simulator ownership UUID not available')
            receipt['owned_uuid'] = uuid; save()
            if command(['xcrun', 'simctl', 'boot', uuid], 'boot.log') or command(['xcrun', 'simctl', 'bootstatus', uuid, '-b'], 'bootstatus.log', 300):
                raise RuntimeError('Owned simulator boot failed')
            results = temp / 'Results.xcresult'
            gradle_attempted = True
            receipt['temporary_artifact_retention_reason'] = 'Only current-cycle framework outputs needed during copied Swift link/runtime; remove after result extraction.'
            receipt['runtime_evidence_status'] = 'RUNNING'
            receipt['runtime_attempted_at'] = now()
            save()
            try:
                xcode = command(['xcodebuild', '-project', project.parent, '-scheme', 'iosApp',
                    '-configuration', 'Debug', '-sdk', 'iphonesimulator', '-destination', 'id=' + uuid,
                    '-derivedDataPath', temp / 'DerivedData', '-resultBundlePath', results,
                    '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
                    '-disable-concurrent-destination-testing', '-jobs', '1', '-test-timeouts-enabled', 'YES',
                    '-default-test-execution-time-allowance', '720', '-maximum-test-execution-time-allowance', '900',
                    'CODE_SIGNING_ALLOWED=NO', 'CODE_SIGNING_REQUIRED=NO', 'CODE_SIGN_IDENTITY=', 'DEVELOPMENT_TEAM=',
                    'ONLY_ACTIVE_ARCH=YES', 'COMPILER_INDEX_STORE_ENABLE=NO',
                    *['-only-testing:iosAppUITests/' + method[:-2] for method in sorted(EXPECTED_TESTS)],
                    'test'], 'xcodebuild.log', 2100)
                receipt['xcodebuild_exit_code'] = xcode
            finally:
                stop_gradle('stop-xcode-immediate')
            if results.exists():
                receipt['xcresult_extraction_exit_codes'] = {}
                for view in ('summary', 'tests'):
                    receipt['xcresult_extraction_exit_codes'][view] = command(
                        ['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', results],
                        'xcresult-' + view + '.json')
                # No screenshot/debug-description or application-data export:
                # retain only structured XCTest and artifact/source identities.
            app = temp / 'DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app'
            if app.is_dir():
                info = plistlib.loads((app / 'Info.plist').read_bytes())
                receipt['app_identity'] = {k: info.get(k) for k in ('CFBundleIdentifier', 'CFBundleVersion', 'CFBundleShortVersionString', 'MinimumOSVersion')}
                receipt['artifacts'] = [dict(path=str(p.relative_to(app)), bytes=p.stat().st_size, sha256=digest(p))
                    for p in [app / 'Parlor', app / 'Frameworks/ComposeApp.framework/ComposeApp'] if p.is_file()]
            if receipt.get('app_identity', {}).get('CFBundleIdentifier') != APP_ID or len(receipt.get('artifacts', [])) != 2:
                raise RuntimeError('No exact Debug application/framework artifact identity captured')
            if not results.exists() or receipt.get('xcresult_extraction_exit_codes') != {'summary': 0, 'tests': 0}:
                raise RuntimeError('No complete actual XCTest result extraction')
            receipt['xctest'] = verify_xctest(
                json.loads((dest / 'xcresult-summary.json').read_text()),
                json.loads((dest / 'xcresult-tests.json').read_text()), uuid)
            embedded_stop = dest / 'embedded-gradle-stop.txt'
            if not embedded_stop.is_file() or embedded_stop.read_text() != 'build_exit=0\nstop_exit=0\n':
                raise RuntimeError('No successful immediate stop receipt from copied nested Gradle phase')
            receipt['embedded_gradle_stop_status'] = 'PASS'
            receipt['smoke_qualification'] = 'Uninstrumented application and unchanged original tests; copied build phase only'
            if owner.secondary_errors:
                raise RuntimeError('Apple secondary-path attestation incomplete; inspect exact ledger, never claim cleanup success')
            receipt['runtime_evidence_status'] = 'PASS' if (
                xcode == 0 and receipt.get('xctest', {}).get('result') == 'Passed' and
                receipt.get('embedded_gradle_stop_status') == 'PASS') else 'FAIL'
            if receipt['runtime_evidence_status'] != 'PASS':
                raise RuntimeError('No complete source-identical wrapper smoke; inspect retained evidence, not an inferred application failure')
        except BaseException as error:
            # Failed validation after an attempted runtime is not an unexecuted
            # check. This records an evidence failure, not an application defect.
            if receipt['runtime_evidence_status'] != 'NOT_RUN':
                receipt['runtime_evidence_status'] = 'FAIL'
            receipt['error'] = dict(type=type(error).__name__, message=str(error)[:800])
        finally:
            deferred = []
            def defer(sig, _frame):
                if len(deferred) < 16: deferred.append(sig)
            prior_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            prior_handlers = {s: signal.signal(s, defer) for s in (signal.SIGINT, signal.SIGTERM)}
            signal.pthread_sigmask(signal.SIG_SETMASK, prior_mask)
            try:
                if gradle_attempted and owner is not None:
                    stage('final-isolated-gradle-stop', lambda: stop_gradle('stop-final'))
                # Recover a successfully printed UUID even if interrupted between command/assignment.
                if uuid is None and (dest / 'create.log').exists():
                    value = (dest / 'create.log').read_text().strip()
                    if re.fullmatch(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}', value):
                        uuid = value; receipt['owned_uuid'] = uuid
                # A killed/interrupted create may register the unique device
                # before printing its UUID. Query only that synthetic name and
                # retain no metadata belonging to other simulator owners.
                if uuid is None and receipt.get('simulator_creation_attempted'):
                    recovered = stage('recover-owned-simulator-identity',
                                      lambda: own_simulator_metadata('owned-device-recovery'))
                    if isinstance(recovered, dict):
                        uuid = recovered['udid']; receipt['owned_uuid'] = uuid
                if uuid is not None:
                    # The unchanged launch test does not add teardown code.
                    # Shutting down this exact owned simulator stops its app even
                    # after failure; no unowned app/process is affected.
                    stage('shutdown-owned-simulator-and-app', shutdown_owned_device)
                    stage('delete-owned-simulator', delete_owned_device)
                if owner is not None:
                    stage('stop-owned-command-workers', owner.stop)
                    receipt['secondary_cleanup'] = stage('cleanup-attested-secondary-fifos', owner.secondary.cleanup)
                    receipt['secondary_attestation_errors'] = list(owner.secondary_errors.values())
                    if owner.secondary_errors:
                        errors.append(dict(stage='secondary-attestation-completeness',
                                           error_type='UnattestedSecondaryPath',
                                           message='Known attestation errors require targeted ownership review; no success claim'))
                    write_json(dest / 'secondary-fifo-ownership-final.json', owner.secondary.dump())
                def verify_workers():
                    live = owner.refresh(inspect_files=True, include_outputs=True) if owner is not None else []
                    receipt['ownership_observed'] = owner.receipt_members() if owner is not None else []
                    receipt['owned_processes_remaining'] = owner.receipt_members(live) if owner is not None else []
                    receipt['unknown_holders'] = owner.receipt_members(owner.unknown_holders) if owner is not None else []
                    if live or (owner is not None and owner.unknown_holders):
                        raise RuntimeError('Owned/unknown file holders remain; retain outputs for targeted cleanup')
                    return True
                safe = stage('verify-workers-before-file-removal', verify_workers) is True
                receipt['copied_sources_unchanged'] = False
                if copied_source_manifest is not None and temp is not None and safe:
                    def verify_copied_inputs():
                        after = inspect_copied_inputs_after_build(temp / 'copy', copied_source_manifest)
                        write_json(dest / 'copied-source-inputs-after-build.json', after)
                        receipt['copied_sources_unchanged'] = after['unchanged']
                        receipt['copied_source_inputs_after_build_sha256'] = digest(dest / 'copied-source-inputs-after-build.json')
                        if not after['unchanged']:
                            raise RuntimeError('Build changed/added/removed a copied source input; retain mismatch evidence, not a passing source binding')
                        return True
                    stage('verify-copied-input-identity-after-workers-stop', verify_copied_inputs)
                removed = []
                if safe and eligible and gradle_attempted:
                    for output in outputs:
                        def remove(output=output):
                            if output.is_symlink(): raise RuntimeError('Refuse output symlink cleanup')
                            if output.exists(): shutil.rmtree(output); removed.append(str(output.relative_to(ROOT)))
                        stage('remove-' + str(output.relative_to(ROOT)), remove)
                if temp is not None and safe:
                    def remove_temp():
                        if temp.is_symlink(): raise RuntimeError('Refuse temp symlink cleanup')
                        for item in links:
                            link = Path(item['link'])
                            if not link.is_symlink() or str(link.resolve()) != item['target']:
                                raise RuntimeError('Shared cache link changed; preserve task root')
                            link.unlink()
                        shutil.rmtree(temp)
                    stage('remove-owned-copy-derived-data-home-temp', remove_temp)
                receipt.update(removed_outputs=removed, remaining_outputs=[str(p.relative_to(ROOT)) for p in outputs if p.exists() or p.is_symlink()],
                    temporary_directory_removed=temp is None or (not temp.exists() and not temp.is_symlink()), cleanup_completed_at=now(),
                    source_after=stage('read-final-source-identity', normalized_identity), cleanup_errors=errors, deferred_signals=deferred,
                    cleanup_method='Immediate isolated Gradle stops; owned simulator shutdown/delete; PID/start attestation; exact attested external FIFOs and empty parents; exact generated outputs and temporary copy/DerivedData removal; cache symlinks unlinked only.')
                receipt['controls_after_sha256'] = control_hash()
                receipt['controls_unchanged'] = receipt['controls_after_sha256'] == approved
                receipt['source_unchanged'] = receipt.get('source_after') is not None and receipt.get('source_after') == receipt.get('source_before')
                receipt['cleanup_status'] = 'PASS' if safe and not errors and not receipt['remaining_outputs'] and receipt['temporary_directory_removed'] else 'FAIL'
                receipt['status'] = 'PASS' if receipt.get('runtime_evidence_status') == 'PASS' and not receipt.get('error') and receipt['cleanup_status'] == 'PASS' and receipt['source_unchanged'] and receipt['controls_unchanged'] and receipt['copied_sources_unchanged'] else 'FAIL'
                receipt['unexpected_original_outputs_preserved'] = [str(p.relative_to(ROOT)) for p in original_outputs
                                                                    if p.exists() or p.is_symlink()]
                if receipt['unexpected_original_outputs_preserved']:
                    receipt['status'] = 'FAIL'
            except BaseException as error:
                receipt.update(status='FAIL', cleanup_status='FAIL', finalizer_error=type(error).__name__)
            finally:
                receipt['finished_at'] = now(); receipt['cleanup_errors'] = errors
                try:
                    save()
                    print(json.dumps(receipt, indent=2), flush=True)
                finally:
                    signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
                    for s, h in prior_handlers.items(): signal.signal(s, h)
                    signal.pthread_sigmask(signal.SIG_SETMASK, prior_mask)
        return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
