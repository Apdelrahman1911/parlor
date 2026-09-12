#!/usr/bin/env python3
"""Audit-only same-app recovery rerun; root's single lane, no production edits.

The additive fixture and this runner require independent review before execution.
Two related Gradle invocations may retain required framework/compiler outputs only
until the copied Swift wrapper has linked/run. Each invocation immediately stops
its isolated daemon registry. Finalization removes only attested task outputs.
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
import tempfile
import time

from run_gradle_cycle import ROOT, OUT, identity, owned_outputs
from run_android_managed_cycle import Ownership, snapshot_processes, digest, write_json

NAME = 'iosr1-apphost-01'
FIXTURE = OUT / 'reproducers/iosr1_apphost'
INIT = OUT / 'reproducers/iosr1_apphost.init.gradle'
APP_ID = 'com.parlor.app.debug'
EXPECTED_TEST = 'IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution()'


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


def apply_one_file_patch(original, patch):
    """Exact-context patch of one allowlisted temporary copy, never Git/source."""
    old = original.splitlines(keepends=True)
    lines = patch.splitlines(keepends=True)
    result, position, index = [], 0, 2
    if not lines[0].startswith('--- a/') or not lines[1].startswith('+++ b/'):
        raise RuntimeError('Unexpected patch header')
    while index < len(lines):
        match = re.fullmatch(r'@@ -(\d+),(\d+) \+(\d+),(\d+) @@\n', lines[index])
        if not match:
            raise RuntimeError('Unexpected copied-wrapper patch syntax')
        start, count, _, new_count = map(int, match.groups())
        if start - 1 < position:
            raise RuntimeError('Overlapping copied-wrapper patch')
        result.extend(old[position:start - 1]); position = start - 1
        index += 1
        consumed, produced = 0, 0
        while index < len(lines) and not lines[index].startswith('@@ '):
            line = lines[index]; index += 1
            if line[0] in ' -':
                if position >= len(old) or old[position] != line[1:]:
                    raise RuntimeError('Copied-wrapper patch context mismatch')
                position += 1; consumed += 1
            if line[0] in ' +':
                result.append(line[1:]); produced += 1
            elif line[0] != '-':
                raise RuntimeError('Unexpected copied-wrapper patch line')
        if (consumed, produced) != (count, new_count):
            raise RuntimeError('Copied-wrapper patch hunk counts differ')
    result.extend(old[position:])
    return ''.join(result)


def verify_xctest(summary, tests, uuid):
    expected = dict(result='Passed', totalTestCount=1, passedTests=1,
                    failedTests=0, skippedTests=0, expectedFailures=0)
    if any(summary.get(key) != value for key, value in expected.items()) or summary.get('testFailures'):
        raise RuntimeError('Actual XCTest summary is not exactly one successful, unskipped test')
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
    if len(cases) != 1 or cases[0].get('nodeIdentifier') != EXPECTED_TEST or cases[0].get('result') != 'Passed':
        raise RuntimeError('The exact audited XCTest method did not pass')
    return dict(method=EXPECTED_TEST, result='Passed', total=1, skipped=0, failures=0, device_uuid=uuid)


def verify_probe(report):
    """Reject unexpected fields, not just a success-shaped top-level marker."""
    required = {'schema_version', 'probe_kind', 'harness_status',
                'original_home_invocation_intercepted', 'local', 'multiplayer', 'combined', 'native_corroboration'}
    if (set(report) != required or report['schema_version'] != 1 or
            report['probe_kind'] != 'production_koin_rerun' or
            report['harness_status'] != 'observation_complete' or
            report['original_home_invocation_intercepted'] is not False):
        raise RuntimeError('No complete, strictly sanitized diagnostic observation')
    local = report['local']
    multiplayer = report['multiplayer']

    def failure(value, categories):
        return (set(value) == {'kind', 'error_category'} and value['kind'] == 'failure' and
                value['error_category'] in categories)

    if local != {'kind': 'success_empty', 'entry_count': 0} and not failure(local, {
            'not_found', 'corrupted_data', 'io_error', 'disk_full', 'permission_denied', 'unknown_data_error'}):
        raise RuntimeError('Unexpected local source observation')
    if multiplayer != {'kind': 'success_null'} and not failure(multiplayer, {
            'secure_storage_unavailable', 'incompatible_protocol', 'other_net_error'}):
        raise RuntimeError('Unexpected multiplayer source observation')
    combined = report['combined']
    unavailable = local['kind'] == 'failure' or multiplayer['kind'] == 'failure'
    if (set(combined) != {'has_unavailable_source', 'local_count', 'has_multiplayer'} or
            combined['has_unavailable_source'] is not unavailable or
            type(combined['local_count']) is not int or combined['local_count'] != 0 or
            combined['has_multiplayer'] is not False):
        raise RuntimeError('Combined result differs from the observed production source outcomes')
    native = report['native_corroboration']
    native_required = (local == {'kind': 'success_empty', 'entry_count': 0} and
                       multiplayer == {'kind': 'failure', 'error_category': 'secure_storage_unavailable'})
    if not native_required:
        if native != {'kind': 'not_applicable_to_result'}:
            raise RuntimeError('Native corroboration must not run outside its source-result guard')
        return
    native_keys = {'origin', 'original_production_status', 'os_status', 'result_present',
                   'constant_not_found', 'constant_missing_entitlement'}
    if (set(native) != native_keys or native['origin'] != 'subsequent_same_app_equivalent_read' or
            native['original_production_status'] is not False or native['result_present'] is not False or
            any(type(native[key]) is not int for key in ['os_status', 'constant_not_found', 'constant_missing_entitlement'])):
        raise RuntimeError('Unexpected subsequent-read corroboration; never attribute it to the original invocation')


class AppHostOwnership(Ownership):
    def refresh(self, inspect_files=False, include_outputs=False):
        super().refresh(inspect_files=inspect_files, include_outputs=include_outputs)
        # A developer executable holding an output may be a shared OS/Xcode
        # service. Never adopt it on that basis. Only an exact task-temporary
        # argv binding, in addition to its open file, permits this fallback;
        # ordinary owned descendants/groups are already handled by the parent.
        if inspect_files:
            for item in list(self.unknown_holders):
                if self.baseline.get(item['pid']) == item['start']:
                    continue
                if str(self.temporary) + '/' in item['command']:
                    self.remember(item, 'owned-xcode-worker',
                                  'New worker has exact task-temporary argv binding and an owned output FD')
            self.unknown_holders = [item for item in self.unknown_holders
                                    if item['pid'] not in self.members or self.members[item['pid']]['start'] != item['start']]
        return [item for pid, item in self.members.items()
                if pid in self.last and item['start'] == self.last[pid]['start']]


def main():
    dest = OUT / 'evidence' / NAME
    with (OUT / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        dest.mkdir(parents=True, exist_ok=False)
        receipt = dict(cycle=NAME, started_at=now(), status='RUNNING',
                       execution_kind='source-aware-unsigned-ios-apphost-diagnostic', commands=[],
                       runtime_evidence_status='NOT_RUN', cleanup_status='BLOCKED',
                       scope='Original production Koin/store/loader rerun in augmented copied wrapper on a fresh owned simulator; no Store/device proof.')
        temp, owner, env, uuid = None, None, None, None
        links, errors, outputs = [], [], owned_outputs() + [ROOT / 'iosApp/build']
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
            receipt['source_before'] = identity()
            if any(p.exists() or p.is_symlink() for p in outputs):
                raise RuntimeError('Pre-existing outputs; ownership review required')
            eligible = True
            baseline = snapshot_processes()
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
                       PARLOR_AUDIT_IOSR1_APPHOST='1', CONFIGURATION='Debug',
                       CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO', CODE_SIGN_IDENTITY='',
                       DEVELOPMENT_TEAM='', EXPANDED_CODE_SIGN_IDENTITY='')
            jvm = '-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Djava.io.tmpdir=' + str(temp / 'tmp')
            opts = ['-Dorg.gradle.jvmargs=' + jvm, '-Dorg.gradle.parallel=false', '-Dorg.gradle.workers.max=1',
                    '-Dorg.gradle.configuration-cache=false', '-Dorg.gradle.caching=false',
                    '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process']
            opts += ['-Dorg.gradle.project.parlor.android.signing.' + key + '=' for key in
                     ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')]
            env['GRADLE_OPTS'] = shlex.join(opts)
            if command(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version'], 'sdk-version.log'):
                raise RuntimeError('SDK version unavailable')
            sdk_version = (dest / 'sdk-version.log').read_text().strip()
            if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*', sdk_version):
                raise RuntimeError('Unexpected SDK version')
            env['SDK_NAME'] = 'iphonesimulator' + sdk_version
            receipt['sdk_name'] = env['SDK_NAME']
            manifest = json.loads((FIXTURE / 'copy-adjustments.json').read_text())
            bindings = json.loads((FIXTURE / 'source-bindings.json').read_text())
            expected = dict(commit=bindings['commit'], tree=bindings['tree'], branch=bindings['branch'], tracked_status='')
            if receipt['source_before'] != expected:
                raise RuntimeError('Source identity differs from independently reviewed fixture')
            for entry in bindings['original_files'] + bindings['additive_kotlin_files']:
                p = ROOT / entry['path']
                if p.is_symlink() or digest(p) != entry['sha256']:
                    raise RuntimeError('Source-bound diagnostic input changed')
            inputs = [Path(__file__), OUT / 'run_gradle_cycle.py', OUT / 'run_android_managed_cycle.py', INIT]
            inputs += sorted(p for p in FIXTURE.iterdir() if p.is_file())
            write_json(dest / 'input-manifest.json', dict(source=receipt['source_before'],
                       files=[dict(path=str(p.relative_to(ROOT)), sha256=digest(p)) for p in inputs]))
            for entry in manifest['copy_only']:
                source = ROOT / entry['path']; target = temp / 'copy' / entry['path']
                if source.is_symlink() or digest(source) != entry['sha256']:
                    raise RuntimeError('Allowlisted copy input changed')
                target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(source, target)
            save()
            # First produce and inspect the actual export. Keep these exact
            # outputs solely for the following Swift link/runtime verification.
            gradle_attempted = True
            try:
                code = command(['./gradlew', ':composeApp:linkDebugFrameworkIosSimulatorArm64',
                                '--no-daemon', '--no-parallel', '--max-workers=1', '--no-build-cache',
                                '--no-configuration-cache', '--dependency-verification=strict',
                                '--console=plain', '-I', INIT], 'framework.log', 1800)
                receipt['framework_exit_code'] = code
            finally:
                stop_gradle('stop-framework-immediate')
            if code:
                raise RuntimeError('Diagnostic framework compilation failed; not a Parlor runtime result')
            receipt['temporary_artifact_retention_reason'] = 'Current-cycle framework/header/compiler outputs required by copied Swift link/runtime; clean after that verification.'
            header = ROOT / 'composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/Headers/ComposeApp.h'
            if header.is_symlink() or not header.is_file() or header.stat().st_size > 4 * 1024 * 1024:
                raise RuntimeError('No bounded generated framework header')
            # Preserve actual generated evidence BEFORE assumptions about export
            # syntax/signatures can fail and finalization removes build outputs.
            shutil.copyfile(header, dest / 'ComposeApp.generated.h')
            receipt['header_sha256'] = digest(header)
            save()
            text = header.read_text()
            match = re.search(r'__attribute__\(\(swift_name\("IOSR1AppHostProbe"\)\)\)\s*@interface [^\n]+.*?\n@end', text, re.S)
            if not match:
                raise RuntimeError('No verified Swift probe class in compiled export')
            export = match.group(0)
            for required in ('swift_name("shared")', 'swift_name("start(onJson:)")', 'swift_name("cancel()")', 'NSString'):
                if required not in export:
                    raise RuntimeError('Unverified Swift probe signature; retain header evidence')
            (dest / 'probe-export.h').write_text(export + '\n')
            # A Kotlin function-typed Unit may use a KotlinUnit object instead
            # of a void block; do not assume the Swift closure return contract.
            if not re.search(r'-\s*\(void\)startOnJson:\(void\s*\(\^\)\(NSString\s*\*\)\)onJson', export):
                raise RuntimeError('Unreviewed callback block return; inspect retained generated header before adapting Swift')
            for rel, patch_name in [('iosApp/iosApp/ContentView.swift', 'ContentView.swift.patch.in'),
                                    ('iosApp/iosAppUITests/IOSAppLaunchUITests.swift', 'IOSAppLaunchUITests.swift.patch.in')]:
                path = temp / 'copy' / rel
                updated = apply_one_file_patch(path.read_text(), (FIXTURE / patch_name).read_text())
                if 'ContentView' in rel:
                    updated = updated.replace('__PARLOR_AUDIT_IOSR1_START_CALL__', 'IOSR1AppHostProbe.shared.start')
                    updated = updated.replace('__PARLOR_AUDIT_IOSR1_CANCEL_CALL__', 'IOSR1AppHostProbe.shared.cancel')
                    updated = '\n'.join(line for line in updated.splitlines() if not line.startswith('#error("Audit template:')) + '\n'
                else:
                    updated = updated.replace('"--parlor-audit-iosr1"]', '"--parlor-audit-iosr1", "--parlor-audit-iosr1-native"]')
                if '__PARLOR_AUDIT_' in updated or '#error(' in updated:
                    raise RuntimeError('Unresolved diagnostic template token')
                path.write_text(updated)
            project = temp / 'copy/iosApp/iosApp.xcodeproj/project.pbxproj'
            pbx = project.read_text()
            old_search = '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
            if pbx.count(old_search) != 2:
                raise RuntimeError('Unexpected framework search bindings')
            pbx = pbx.replace(old_search, str(ROOT / 'composeApp/build/xcode-frameworks') + '/$(CONFIGURATION)/$(SDK_NAME)')
            phase = (FIXTURE / 'copied-kotlin-phase.sh.in').read_text()
            phase = phase.replace('__PARLOR_SOURCE_ROOT__', str(ROOT)).replace('__PARLOR_IOSR1_INIT_SCRIPT__', str(INIT))
            # Exact phase receipt before Swift compilation. The copied phase has
            # already stopped its isolated daemon; this does not fix production IOS-B1.
            phase = phase.replace('stop_status=$?', 'stop_status=$?\nprintf "build_exit=%s\\nstop_exit=%s\\n" "$build_status" "$stop_status" > ' + shlex.quote(str(dest / 'embedded-gradle-stop.txt')))
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
            changed = ['iosApp/iosApp/ContentView.swift', 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift', 'iosApp/iosApp.xcodeproj/project.pbxproj']
            (dest / 'copied-wrapper.diff').write_text(''.join(''.join(difflib.unified_diff((ROOT / p).read_text().splitlines(True),
                (temp / 'copy' / p).read_text().splitlines(True), fromfile='original/' + p, tofile='audit-copy/' + p)) for p in changed))
            write_json(dest / 'copied-wrapper-manifest.json', [dict(path=e['path'], original_sha256=e['sha256'],
                copied_sha256=digest(temp / 'copy' / e['path'])) for e in manifest['copy_only']])
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
            try:
                xcode = command(['xcodebuild', '-project', project.parent, '-scheme', 'iosApp',
                    '-configuration', 'Debug', '-sdk', 'iphonesimulator', '-destination', 'id=' + uuid,
                    '-derivedDataPath', temp / 'DerivedData', '-resultBundlePath', results,
                    '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
                    '-disable-concurrent-destination-testing', '-jobs', '1', '-test-timeouts-enabled', 'YES',
                    '-default-test-execution-time-allowance', '90', '-maximum-test-execution-time-allowance', '180',
                    'CODE_SIGNING_ALLOWED=NO', 'CODE_SIGNING_REQUIRED=NO', 'CODE_SIGN_IDENTITY=', 'DEVELOPMENT_TEAM=',
                    'ONLY_ACTIVE_ARCH=YES', 'COMPILER_INDEX_STORE_ENABLE=NO', 'test'], 'xcodebuild.log', 1800)
                receipt['xcodebuild_exit_code'] = xcode
            finally:
                stop_gradle('stop-xcode-immediate')
            if results.exists():
                for view in ('summary', 'tests'):
                    if command(['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', results], 'xcresult-' + view + '.json'):
                        raise RuntimeError('Cannot extract actual XCTest ' + view)
                # Export only attachments produced by this synthetic Home/probe test.
                command(['xcrun', 'xcresulttool', 'export', 'attachments', '--path', results,
                         '--output-path', dest / 'attachments'], 'attachments.log')
                receipt['xctest'] = verify_xctest(
                    json.loads((dest / 'xcresult-summary.json').read_text()),
                    json.loads((dest / 'xcresult-tests.json').read_text()), uuid)
            else:
                raise RuntimeError('No actual XCTest result bundle')
            embedded_stop = dest / 'embedded-gradle-stop.txt'
            if not embedded_stop.is_file() or embedded_stop.read_text() != 'build_exit=0\nstop_exit=0\n':
                raise RuntimeError('No successful immediate stop receipt from copied nested Gradle phase')
            receipt['embedded_gradle_stop_status'] = 'PASS'
            app = temp / 'DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app'
            if app.is_dir():
                info = plistlib.loads((app / 'Info.plist').read_bytes())
                receipt['app_identity'] = {k: info.get(k) for k in ('CFBundleIdentifier', 'CFBundleVersion', 'CFBundleShortVersionString', 'MinimumOSVersion')}
                receipt['artifacts'] = [dict(path=str(p.relative_to(app)), bytes=p.stat().st_size, sha256=digest(p))
                    for p in [app / 'Parlor', app / 'Frameworks/ComposeApp.framework/ComposeApp'] if p.is_file()]
            if command(['xcrun', 'simctl', 'get_app_container', uuid, APP_ID, 'data'], 'owned-container.log') == 0:
                container = Path((dest / 'owned-container.log').read_text().strip()).resolve()
                owned_device = Path.home() / 'Library/Developer/CoreSimulator/Devices' / uuid
                container.relative_to(owned_device.resolve())
                result = container / 'tmp/parlor-audit-iosr1-result.json'
                if result.is_symlink() or result.resolve().parent != (container / 'tmp').resolve() or not result.is_file() or result.stat().st_size > 8192:
                    raise RuntimeError('No bounded owned diagnostic result')
                shutil.copyfile(result, dest / 'probe-result.json')
                report = json.loads(result.read_text())
                verify_probe(report)
                receipt['probe_harness_status'] = report.get('harness_status')
            receipt['runtime_evidence_status'] = 'PASS' if (
                xcode == 0 and receipt.get('xctest', {}).get('result') == 'Passed' and
                receipt.get('embedded_gradle_stop_status') == 'PASS' and
                receipt.get('probe_harness_status') == 'observation_complete') else 'FAIL'
            if receipt['runtime_evidence_status'] != 'PASS':
                raise RuntimeError('No complete same-app diagnostic; inspect retained evidence, not an inferred application failure')
        except BaseException as error:
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
                    # XCTest itself defers app.terminate(). Shutting down the
                    # exact owned simulator also stops apps after test failure;
                    # no misleading nonzero 'nothing to terminate' success.
                    stage('shutdown-owned-simulator-and-app', shutdown_owned_device)
                    stage('delete-owned-simulator', delete_owned_device)
                if owner is not None:
                    stage('stop-owned-command-workers', owner.stop)
                def verify_workers():
                    live = owner.refresh(inspect_files=True, include_outputs=True) if owner is not None else []
                    receipt['ownership_observed'] = owner.receipt_members() if owner is not None else []
                    receipt['owned_processes_remaining'] = owner.receipt_members(live) if owner is not None else []
                    receipt['unknown_holders'] = owner.receipt_members(owner.unknown_holders) if owner is not None else []
                    if live or (owner is not None and owner.unknown_holders):
                        raise RuntimeError('Owned/unknown file holders remain; retain outputs for targeted cleanup')
                    return True
                safe = stage('verify-workers-before-file-removal', verify_workers) is True
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
                    source_after=stage('read-final-source-identity', identity), cleanup_errors=errors, deferred_signals=deferred,
                    cleanup_method='Immediate isolated Gradle stops; owned simulator shutdown/delete; PID/start attestation; exact generated outputs and temporary copy/DerivedData removal; cache symlinks unlinked only.')
                receipt['source_unchanged'] = receipt.get('source_after') is not None and receipt.get('source_after') == receipt.get('source_before')
                receipt['cleanup_status'] = 'PASS' if safe and not errors and not receipt['remaining_outputs'] and receipt['temporary_directory_removed'] else 'FAIL'
                receipt['status'] = 'PASS' if receipt.get('runtime_evidence_status') == 'PASS' and not receipt.get('error') and receipt['cleanup_status'] == 'PASS' and receipt['source_unchanged'] else 'FAIL'
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
