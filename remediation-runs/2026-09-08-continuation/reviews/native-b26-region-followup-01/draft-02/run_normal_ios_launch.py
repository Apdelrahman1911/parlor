#!/usr/bin/env python3
"""Root-only execution lane: unchanged app source, eight ten-second UI launches.

Campaign draft. Requires a later explicit source binding and independently
reviewed control hash. Never call the instrumented readiness runner's main().
"""
import datetime
import difflib
import fcntl
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import pwd
import re
import shlex
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SUPPORT = ROOT / 'scripts/verification/ios-readiness'
DARWIN = ROOT / ('remediation-runs/2026-09-07-local-readiness/evidence/'
                 'release-content-followup/android-arm64-runner/darwin_owned_processes.py')
DARWIN_SHA = '2f2ad2a5d68cea999709a4c3c3e384cd9e6767258ad34aed4181ee2118962378'
sys.path.insert(0, str(SUPPORT))
sys.path.insert(0, str(HERE))

from owned_lane import digest, identity, owned_outputs, snapshot_processes, write_json
from run_ios_readiness import AppHostOwnership, checked_binding_path, defer_parent_signals
from copied_sources import create_source_copy, inspect_copied_inputs_after_build, owned_simulator_name
from simulator_signing import (selected_mode, signing_overrides, render_owned_kotlin_phase,
                               read_phase_receipt, RECEIPT_NAME)
from toolchain_profiles import (LOCAL as LOCAL_TOOLCHAIN, profile as toolchain_profile,
                                selected_toolchain, developer_environment, validate_observation)
from artifact_inventory import inventory_bundle, parse_dwarfdump_uuids, FRAMEWORK_PATH
from normal_source_copy import transform_copy, inventory_copy, CHANGED, PROJECT
from normal_launch_receipts import read_json, verify_markers, verify_xctest, METHOD, SELECTOR, UUID
from external_image_provenance import (APP_ID, parse_launch_pid, attest_target, unchanged_target,
                                       parse_sample, bind_vmmap, header_diagnostic, OwnedToolPaths, LIMITATION)
from external_image_diagnostics import image_diagnostic, vmmap_command_diagnostic
from kernel_image_regions import bind_regions, selected_observer, LIMITATION as KERNEL_LIMITATION
import image_text_layout_diagnostic as image_layout
from simulator_lifecycle import (OwnedSimulatorLifecycle, selected_lifecycle, validate_selection,
                                  DIRECT as DIRECT_LIFECYCLE, LEGACY as LEGACY_LIFECYCLE)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def owned_tool_paths(artifacts, executable, device_uuid, attested_launcher):
    # This context comes from the OS account database, the lane's fresh device,
    # and a kernel-attested launcher, never the tool's lossy display or HOME.
    uid = os.getuid()
    if uid != os.geteuid():
        raise RuntimeError('Elevated account context cannot authorize a tool path alias')
    account = pwd.getpwuid(uid)
    if (account.pw_uid != uid or attested_launcher.uid != uid or
            attested_launcher.command != str(executable) or executable.name != 'Parlor'):
        raise RuntimeError('Alias context does not match the kernel-attested current-user launcher')
    return OwnedToolPaths(artifacts, account.pw_dir, device_uuid, executable.parent)


def normalized_identity():
    return json.loads(json.dumps(identity()))


def control_manifest(binding=None):
    paths = []
    for directory in (HERE, SUPPORT):
        paths += [path for path in directory.iterdir() if path.is_file() and
                  (path.suffix in {'.py', '.in', '.md'} or path.name == 'inherited-controls.json')]
    paths += [DARWIN] + ([] if binding is None else [binding])
    rows = []
    for path in sorted(paths):
        if path.is_symlink() or path.resolve(strict=True) != path or not path.is_file():
            raise RuntimeError('Missing or redirected control input')
        rows.append(dict(path=str(path.relative_to(ROOT)), sha256=digest(path)))
    return rows


def control_hash(binding=None):
    return hashlib.sha256(json.dumps(control_manifest(binding), separators=(',', ':')).encode()).hexdigest()


def darwin_backend():
    if digest(DARWIN) != DARWIN_SHA:
        raise RuntimeError('Unreviewed Darwin identity helper; no external app observation authorized')
    name = '_normal_source_darwin_identity'
    specification = importlib.util.spec_from_file_location(name, DARWIN)
    module = importlib.util.module_from_spec(specification)
    sys.modules[name] = module
    specification.loader.exec_module(module)
    return module.DarwinBackend()


def xcode_arguments(project, sdk, uuid, temporary, signing):
    return ['xcodebuild', '-project', str(project.parent), '-scheme', 'iosApp',
            '-configuration', 'Debug', '-sdk', sdk, '-destination', 'id=' + uuid,
            '-derivedDataPath', str(temporary / 'DerivedData'),
            '-resultBundlePath', str(temporary / 'Results.xcresult'),
            '-only-testing:' + SELECTOR, '-test-iterations', '8',
            '-test-repetition-relaunch-enabled', 'YES', '-parallel-testing-enabled', 'NO',
            '-maximum-concurrent-test-simulator-destinations', '1',
            '-disable-concurrent-destination-testing', '-jobs', '1',
            '-test-timeouts-enabled', 'YES', '-default-test-execution-time-allowance', '120',
            '-maximum-test-execution-time-allowance', '240',
            *[key + '=' + value for key, value in signing.items()],
            'ONLY_ACTIVE_ARCH=YES', 'COMPILER_INDEX_STORE_ENABLE=NO', 'test']


def isolated_environment(parent, java, temporary, android_sdk, mode, toolchain=LOCAL_TOOLCHAIN):
    if (not Path(java).is_absolute() or '\n' in java or '\r' in java or
            Path(java).name != 'Home' or not temporary.is_absolute() or not android_sdk.is_absolute()):
        raise RuntimeError('Unexpected public JDK/owned workspace/SDK location')
    environment = {key: parent[key] for key in ('HOME', 'USER', 'LOGNAME') if key in parent}
    environment.update(PATH=java + '/bin:/usr/bin:/bin:/usr/sbin:/sbin', JAVA_HOME=java,
        LANG='en_US.UTF-8', LC_ALL='C', GRADLE_USER_HOME=str(temporary / 'gradle-home'),
        TMPDIR=str(temporary / 'tmp') + '/', PYTHONDONTWRITEBYTECODE='1', CONFIGURATION='Debug',
        ANDROID_HOME=str(android_sdk), ANDROID_SDK_ROOT=str(android_sdk))
    environment.update(developer_environment(toolchain, parent))
    environment.update(signing_overrides(mode))
    jvm = '-Xmx6g -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Djava.io.tmpdir=' + str(temporary / 'tmp')
    options = ['-Dorg.gradle.jvmargs=' + jvm, '-Dorg.gradle.parallel=false', '-Dorg.gradle.workers.max=1',
               '-Dorg.gradle.configuration-cache=false', '-Dorg.gradle.caching=false',
               '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process']
    options += ['-Dorg.gradle.project.parlor.android.signing.' + key + '='
                for key in ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')]
    environment['GRADLE_OPTS'] = shlex.join(options)
    return environment


class Lane:
    def __init__(self, name, binding, approved, mode, image_observer='vmmap', toolchain=LOCAL_TOOLCHAIN,
                 lifecycle_mode=LEGACY_LIFECYCLE):
        validate_selection(lifecycle_mode, toolchain)
        self.lifecycle_mode, self.lifecycle = lifecycle_mode, None
        if image_observer not in {'vmmap', 'libproc'}:
            raise RuntimeError('Unknown explicit image observer')
        self.image_observer = image_observer
        self.toolchain = toolchain_profile(toolchain)
        self.name, self.binding, self.approved, self.mode = name, binding, approved, mode
        self.destination = binding.parent / 'evidence' / name
        parent = self.destination.parent
        if parent.is_symlink() or parent.resolve() != parent:
            raise RuntimeError('Evidence destination traverses a symbolic link')
        parent.mkdir(exist_ok=True)
        self.destination.mkdir(exist_ok=False)
        self.receipt = dict(schema_version=1, cycle=name, started_at=now(), status='RUNNING',
            execution_kind='normal-source-fixed-eight-plus-separate-public-tool-provenance',
            signing_mode=mode, image_observer=image_observer, approved_control_sha256=approved, commands=[], gradle_stops=[],
            toolchain_profile=self.toolchain['name'], simulator_lifecycle_mode=lifecycle_mode, build_attempted=False,
            postbuild_evidence_preserved=False,
            runtime_evidence_status='NOT_RUN', provenance_status='NOT_RUN',
            notice_package_status='NOT_RUN', cleanup_status='BLOCKED',
            scope='Eight fixed English XCTest repetitions observing unchanged production app source '
                  'through normal Home accessibility for at least ten post-home seconds each. '
                  'One additional simctl launch observes image provenance separately. No settings '
                  'seed, app probe, game/session, physical device, historical-crash diagnosis or Store proof.',
            source_before=None)
        self.temporary = self.owner = self.environment = self.uuid = self.copy_manifest = None
        self.allocation_identity = None
        self.baseline = {}
        self.links, self.errors = [], []
        self.watched_outputs = []
        self.gradle_attempted = False
        self.original_outputs = owned_outputs() + [ROOT / 'iosApp/build']

    def save(self):
        write_json(self.destination / 'receipt.json', self.receipt)

    def stage(self, name, function):
        try:
            value = function()
            if type(value) is int and value != 0:
                raise RuntimeError('Finalization operation returned a nonzero status')
            self.receipt.setdefault('finalization_stages', []).append(dict(stage=name, status='PASS', at=now()))
            return value
        except BaseException as error:
            self.errors.append(dict(stage=name, type=type(error).__name__, message=str(error)[:500]))
            self.receipt.setdefault('finalization_stages', []).append(dict(stage=name, status='FAIL', at=now()))
            return None

    def command(self, arguments, filename, timeout=120, cwd=None, output=None, limit=4 * 1024 * 1024,
                *, capture_output_identity=False):
        path = self.destination / filename if output is None else output
        entry = dict(command=list(map(str, arguments)), started_at=now(),
                     log=filename if output is None else 'temporary-only/' + path.name)
        self.receipt['commands'].append(entry)
        self.save()
        child = None
        with path.open('xb') as stream:
            try:
                if capture_output_identity:
                    opened = os.fstat(stream.fileno())
                    entry['output_file_identity'] = dict(device=opened.st_dev, inode=opened.st_ino,
                        uid=opened.st_uid, file_type=stat.S_IFMT(opened.st_mode))
                with defer_parent_signals():
                    child = subprocess.Popen(entry['command'], cwd=cwd or ROOT, env=self.environment,
                                             stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
                    self.owner.register(child, 'command')
                deadline = time.monotonic() + timeout
                while child.poll() is None:
                    self.owner.refresh()
                    if time.monotonic() >= deadline:
                        raise subprocess.TimeoutExpired(entry['command'], timeout)
                    if path.stat().st_size > limit:
                        raise RuntimeError('Owned command exceeded its output budget')
                    self.check_watched_outputs()
                    time.sleep(0.25)
                entry['exit_code'] = child.returncode
                if path.stat().st_size > limit:
                    raise RuntimeError('Owned command exceeded its output budget')
            except BaseException as error:
                entry['interrupted_or_failed'] = True
                entry['primary_error'] = dict(type=type(error).__name__, message=str(error)[:800])
                if child is not None:
                    cleanup_stage = 'stop-owned-command-workers'
                    try:
                        self.owner.stop(lambda item: item['role'] == 'command')
                        cleanup_stage = 'read-owned-command-exit'
                        entry['exit_code'] = child.poll()
                    except BaseException as cleanup_error:
                        entry['command_cleanup_error'] = dict(stage=cleanup_stage,
                            type=type(cleanup_error).__name__, message=str(cleanup_error)[:800])
                raise
            finally:
                entry['finished_at'] = now()
                self.save()
        if (entry['exit_code'] != 0 and len(entry['command']) == 3 and
                entry['command'][:2] == ['/usr/bin/vmmap', '-w']):
            try:
                self.collect_vmmap_command_failure(path, int(entry['command'][2]), entry['exit_code'])
            except BaseException as diagnostic_error:
                # The command already failed. Keep its nonzero result and only
                # the closed diagnostic error type, including interruptions.
                self.receipt['vmmap_command_diagnostic_error_type'] = type(diagnostic_error).__name__
        return entry['exit_code']

    def collect_vmmap_command_failure(self, path, pid, exit_code):
        if path != self.temporary / 'owned-app.vmmap.txt' or path.resolve() != path:
            raise RuntimeError('Failed-vmmap output is not the exact owned temporary file')
        before = path.lstat()
        if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid() or before.st_nlink != 1 or
                not 0 <= before.st_size <= 16 * 1024 * 1024):
            raise RuntimeError('Failed-vmmap output changed type, owner or bound')
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        try:
            opened = os.fstat(fd)
            if (opened.st_dev, opened.st_ino, opened.st_size) != (before.st_dev, before.st_ino, before.st_size):
                raise RuntimeError('Failed-vmmap output was replaced')
            with os.fdopen(fd, 'rb', closefd=False) as source:
                raw = source.read(16 * 1024 * 1024 + 1)
            after = os.fstat(fd)
            if (len(raw) != opened.st_size or opened.st_size != after.st_size or
                    opened.st_mtime_ns != after.st_mtime_ns):
                raise RuntimeError('Failed-vmmap output changed while reading')
        finally:
            os.close(fd)
        name = 'vmmap-command-failure.json'
        write_json(self.destination / name, vmmap_command_diagnostic(raw, pid, exit_code))
        self.receipt['vmmap_command_failure_sha256'] = digest(self.destination / name)

    def check_watched_outputs(self):
        for path, maximum in self.watched_outputs:
            if path.is_symlink() or path.exists() and (not path.is_file() or path.stat().st_size > maximum):
                raise RuntimeError('Owned temporary observation output exceeded its budget or changed type')

    def require(self, *args, **kwargs):
        if self.command(*args, **kwargs) != 0:
            raise RuntimeError('Required owned command failed; consult its retained receipt')

    def simulator_command(self, arguments, filename, timeout=120):
        if self.lifecycle is None:
            return self.command(arguments, filename, timeout)
        return self.lifecycle.command(arguments, filename, timeout)  # No automatic fallback.

    def stop_gradle(self, label):
        code = self.command(['./gradlew', '--stop'], label + '.log', timeout=90, cwd=self.temporary / 'copy')
        self.receipt['gradle_stops'].append(dict(label=label, exit_code=code, at=now()))
        if code:
            raise RuntimeError('Required isolated Gradle stop failed')

    def simulator_metadata(self, label):
        if self.lifecycle is not None:
            return self.lifecycle.metadata(label)
        # Listing needs the public inventory, but no unrelated device metadata
        # survives in evidence. This temporary raw file is deleted on every path.
        raw = self.temporary / (label + '.json')
        try:
            self.require(['xcrun', 'simctl', 'list', 'devices', '-j'], label,
                         timeout=45, output=raw, limit=2 * 1024 * 1024)
            data = read_json(raw.read_text(), maximum=2 * 1024 * 1024)
            matches = [dict(udid=item['udid'], name=item['name'], state=item['state'], runtime=runtime)
                       for runtime, rows in data['devices'].items() for item in rows
                       if item.get('name') == self.receipt['owned_device_name']]
            if (len(matches) > 1 or (matches and
                    (UUID.fullmatch(matches[0]['udid']) is None or
                     matches[0]['runtime'] != self.toolchain['runtime'] or
                     self.uuid is not None and matches[0]['udid'] != self.uuid))):
                raise RuntimeError('Ambiguous or changed synthetic simulator identity')
            write_json(self.destination / (label + '.json'), dict(matches=matches))
            return matches[0] if matches else None
        finally:
            if raw.is_file() and not raw.is_symlink():
                raw.unlink()

    def allocate_temporary(self):
        if self.temporary is not None or self.owner is not None or self.allocation_identity is not None:
            raise RuntimeError('Cannot replace an existing task allocation')
        # Canonicalize the parent before allocation: macOS may return a /var
        # alias. From mkdtemp through in-memory cleanup ownership, defer signals.
        # A receipt write or owner-constructor error must not strand an otherwise
        # verified allocation. No subprocess is started by this transaction.
        parent = Path(tempfile.gettempdir()).resolve(strict=True)
        with defer_parent_signals():
            self.temporary = Path(tempfile.mkdtemp(prefix='parlor-audit-' + self.name + '-', dir=parent))
            self.receipt['allocated_temporary_path'] = str(self.temporary)
            state = self.temporary.lstat()
            if (self.temporary.parent != parent or self.temporary.resolve(strict=True) != self.temporary or
                    not stat.S_ISDIR(state.st_mode) or state.st_uid != os.getuid()):
                raise RuntimeError('New task allocation is not a canonical owned directory')
            self.allocation_identity = (state.st_dev, state.st_ino, state.st_uid)
            self.receipt.update(owned_temporary_directory=str(self.temporary),
                                temporary_allocation_identity=list(self.allocation_identity))
            self.receipt['owned_device_name'] = owned_simulator_name(self.temporary)
            self.owner = AppHostOwnership(self.baseline, self.temporary, [], self.destination)
            self.save()

    def prepare(self):
        self.save()
        self.receipt['source_before'] = normalized_identity()
        self.bindings = read_json(self.binding.read_text(), maximum=8 * 1024 * 1024)
        if (self.bindings.get('repository') != str(ROOT) or self.bindings.get('schema_version') != 3 or
                self.bindings.get('source_identity') != self.receipt['source_before']):
            raise RuntimeError('Actual dirty/untracked source differs from the later frozen source binding')
        if any(path.exists() or path.is_symlink() for path in self.original_outputs):
            raise RuntimeError('Pre-existing repository build outputs are not task-owned; do not compete')
        self.baseline = snapshot_processes()
        if any('org.gradle.launcher.daemon.bootstrap.GradleDaemon' in row['command'] or
               row['command'].split()[0].endswith('/xcodebuild') for row in self.baseline.values()):
            raise RuntimeError('Pre-existing Gradle/Xcode lane is active; never stop another task')
        self.allocate_temporary()
        for name in ('copy', 'tmp', 'gradle-home'):
            (self.temporary / name).mkdir(mode=0o700)
        cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).resolve(strict=True)
        for name in ('caches', 'wrapper'):
            target = cache / name
            if not target.is_dir():
                raise RuntimeError('Existing dependency/distribution cache absent')
            link = self.temporary / 'gradle-home' / name
            link.symlink_to(target, target_is_directory=True)
            self.links.append(dict(link=str(link), target=str(target)))
            self.receipt['shared_cache_links'] = self.links
            self.save()
        # No inherited app-probe, loader, signing, credential or Gradle options.
        self.environment = {key: os.environ[key] for key in ('HOME', 'USER', 'LOGNAME') if key in os.environ}
        self.environment.update(PATH='/usr/bin:/bin:/usr/sbin:/sbin', LC_ALL='C')
        self.environment.update(developer_environment(self.toolchain['name'], os.environ))
        self.require(['/usr/libexec/java_home', '-v', '21'], 'java-home.log', timeout=15)
        java = (self.destination / 'java-home.log').read_text().strip()
        sdk = Path.home() / 'Library/Android/sdk'
        if not sdk.is_dir():
            raise RuntimeError('Public SDK installation absent; never copy private local.properties')
        self.environment = isolated_environment(os.environ, java, self.temporary, sdk, self.mode,
                                                self.toolchain['name'])
        self.signing = signing_overrides(self.mode)
        self.require(['xcodebuild', '-version'], 'xcode-version.log')
        self.require(['/usr/bin/sw_vers'], 'macos-version.log')
        self.require(['/usr/bin/uname', '-sr'], 'darwin-version.log')
        self.require(['/usr/bin/uname', '-m'], 'host-architecture.log')
        self.require(['xcode-select', '-p'], 'selected-developer.log')
        self.require([java + '/bin/java', '-version'], 'java-version.log')
        self.require(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version'], 'sdk-version.log')
        observation = validate_observation(self.toolchain['name'],
            (self.destination / 'xcode-version.log').read_text(),
            (self.destination / 'sdk-version.log').read_text(),
            (self.destination / 'selected-developer.log').read_text(),
            (self.destination / 'host-architecture.log').read_text())
        self.environment['SDK_NAME'] = observation['sdk_name']
        self.receipt['toolchain_observation'] = observation
        self.receipt['toolchain_scope'] = observation['scope']
        self.receipt['sdk_name'] = self.environment['SDK_NAME']
        write_json(self.destination / 'input-manifest.json', dict(source=self.receipt['source_before'],
                   controls=control_manifest(self.binding), approved_control_sha256=self.approved))
        copy = self.temporary / 'copy'
        create_source_copy(ROOT, copy, self.bindings)
        phase = render_owned_kotlin_phase((SUPPORT / 'copied-kotlin-phase.sh.in').read_text(),
                                          self.temporary, self.destination, self.environment['SDK_NAME'], self.mode)
        transform_copy(copy, (HERE / 'NormalSourceLaunchUITests.swift.in').read_text(), phase)
        self.copy_manifest = inventory_copy(copy, self.bindings, digest)
        write_json(self.destination / 'copied-source-manifest.json', self.copy_manifest)
        diff = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
                    (copy / path).read_text().splitlines(True), fromfile='original/' + path,
                    tofile='normal-source-copy/' + path)) for path in sorted(CHANGED))
        (self.destination / 'copied-source.diff').write_text(diff)
        self.receipt.update(copied_source_diff_sha256=digest(self.destination / 'copied-source.diff'),
                            copied_source_manifest_sha256=digest(self.destination / 'copied-source-manifest.json'),
                            application_source_transformations=0, ui_test_source_transformations=1,
                            copied_build_phase_transformations=1)
        if self.lifecycle_mode == DIRECT_LIFECYCLE:
            self.lifecycle = OwnedSimulatorLifecycle(root=ROOT, temporary=self.temporary, evidence=self.destination,
                cycle=self.name, name=self.receipt['owned_device_name'], toolchain=self.toolchain['name'],
                source=self.receipt['source_before'], approved=self.approved, receipt=self.receipt, save=self.save,
                current_bindings=lambda: (normalized_identity(), control_hash(self.binding)), environment=self.environment)
        if self.simulator_metadata('owned-device-before-create') is not None:
            raise RuntimeError('Unique newly allocated simulator name already exists')
        self.receipt['simulator_creation_attempted'] = True
        self.save()
        if self.simulator_command(['xcrun', 'simctl', 'create', self.receipt['owned_device_name'],
                      'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro',
                      self.toolchain['runtime']], 'create.log'):
            raise RuntimeError('New simulator creation failed')
        value = (self.destination / 'create.log').read_text().strip()
        if UUID.fullmatch(value) is None:
            raise RuntimeError('New simulator did not return an exact UUID')
        self.uuid = value
        self.receipt['owned_uuid'] = value
        if self.simulator_metadata('owned-device-after-create') is None:
            raise RuntimeError('Created UUID is not registered under our exact owned name')
        if (self.simulator_command(['xcrun', 'simctl', 'boot', self.uuid], 'boot.log') or
                self.simulator_command(['xcrun', 'simctl', 'bootstatus', self.uuid, '-b'], 'bootstatus.log', timeout=300)):
            raise RuntimeError('Owned simulator boot failed')

    def run_xctest(self):
        self.gradle_attempted = True
        self.receipt['build_attempted'] = True
        self.receipt['runtime_evidence_status'] = 'RUNNING'
        self.receipt['temporary_artifact_retention_reason'] = (
            'Retain this one build only through XCTest extraction, installed-file comparison and '
            'the separate image-provenance observation. Delete its copy/build/DerivedData immediately afterward.')
        self.save()
        try:
            if self.lifecycle is not None:
                self.lifecycle.mark_build_attempted()
            code = self.command(xcode_arguments(self.temporary / 'copy' / PROJECT,
                                self.environment['SDK_NAME'], self.uuid, self.temporary, self.signing),
                                'xcodebuild.log', timeout=2700, limit=64 * 1024 * 1024)
            self.receipt['xcodebuild_exit_code'] = code
        finally:
            self.stop_gradle('stop-xcode-immediate')
        results = self.temporary / 'Results.xcresult'
        if not results.is_dir() or results.is_symlink():
            raise RuntimeError('No current-cycle XCResult was produced')
        exits = {}
        for view in ('summary', 'tests', 'test-details'):
            arguments = ['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', results]
            if view == 'test-details':
                arguments += ['--test-id', METHOD]
            exits[view] = self.command(arguments, 'xcresult-' + view + '.json')
        self.receipt['xcresult_extraction_exit_codes'] = exits
        if any(exits.values()):
            raise RuntimeError('Actual XCTest extraction failed; never infer success from marker output')
        # This acknowledges retained raw structured views, never runtime success.
        self.receipt['postbuild_evidence_preserved'] = True
        try:
            self.save()
        except BaseException:
            self.receipt['postbuild_evidence_preserved'] = False
            raise
        self.receipt['xctest'] = verify_xctest(*[read_json((self.destination / ('xcresult-' + view + '.json')).read_text())
                                                for view in ('summary', 'tests', 'test-details')], self.uuid)
        with (self.destination / 'xcodebuild.log').open() as stream:
            marker_receipt = verify_markers(stream)
        write_json(self.destination / 'normal-launch-observations.json', marker_receipt)
        self.receipt['launch_observations_sha256'] = digest(self.destination / 'normal-launch-observations.json')
        stop = self.destination / 'embedded-gradle-stop.txt'
        if not stop.is_file() or stop.read_text() != 'build_exit=0\nstop_exit=0\n':
            raise RuntimeError('Copied app embedding did not attest successful build and immediate Gradle stop')
        phase = read_phase_receipt(self.destination / RECEIPT_NAME, self.mode,
                                   self.environment['SDK_NAME'], self.temporary / 'copy')
        self.receipt['effective_app_phase'] = dict(mode=phase['mode'], sha256=digest(self.destination / RECEIPT_NAME))
        self.receipt['embedded_gradle_stop_status'] = 'PASS'
        if code:
            raise RuntimeError('Failed xcodebuild cannot become successful because extracted tests passed')
        self.receipt['runtime_evidence_status'] = 'PASS'

    def verify_notice_packages(self, built, installed):
        # The caller first binds both canonical directories to our owned build
        # and simulator. Execute the same source-bound verifier copied with the
        # app; retain its compact byte receipts before deleting either package.
        self.receipt['notice_package_status'] = 'RUNNING'
        self.receipt['notice_packages'] = []
        copy = self.temporary / 'copy'
        for origin, app in (('built', built), ('installed', installed)):
            filename = origin + '-notice-package.json'
            self.require(['/usr/bin/python3', '-B', copy / 'scripts/verification/third_party_notices.py',
                          '--root', copy, '--package', app, '--json'], filename, limit=1024 * 1024)
            report = read_json((self.destination / filename).read_text(), maximum=1024 * 1024)
            if not isinstance(report, dict) or report.get('status') != 'PASS' or not report.get('package'):
                raise RuntimeError('Packaged-notice verification did not provide a successful package receipt')
            self.receipt['notice_packages'].append(dict(origin=origin, file=filename,
                                                       sha256=digest(self.destination / filename)))
        self.receipt['notice_package_status'] = 'PASS'

    def artifact_inventory(self):
        app = self.temporary / 'DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app'
        if app.is_symlink() or app.resolve(strict=True) != app:
            raise RuntimeError('Refuse an app artifact redirected outside the owned DerivedData tree')
        built = inventory_bundle(app)
        self.require(['xcrun', 'simctl', 'get_app_container', self.uuid, APP_ID, 'app'], 'installed-app-path.log')
        raw = (self.destination / 'installed-app-path.log').read_text().strip()
        owned_device = (Path.home() / 'Library/Developer/CoreSimulator/Devices' / self.uuid).resolve(strict=True)
        installed = Path(raw)
        if (not installed.is_absolute() or installed.is_symlink() or installed.resolve(strict=True) != installed or
                not installed.is_dir()):
            raise RuntimeError('Installed app path is not canonical and task-owned')
        installed.relative_to(owned_device)
        actual = inventory_bundle(installed)
        if built != actual:
            raise RuntimeError('Installed native bundle differs from this exact built application')
        self.verify_notice_packages(app, installed)
        write_json(self.destination / 'built-installed-binary-inventory.json', built)
        self.receipt['built_installed_native_inventory_sha256'] = digest(self.destination / 'built-installed-binary-inventory.json')
        executable = built['bundle_identity']['CFBundleExecutable']
        artifacts, seen, ordinal = {}, set(), 0
        for origin, root in (('installed-app', installed), ('owned-derived-app', app)):
            for row in built['images']:
                path = root / row['resolved_path']
                if path in seen:
                    continue
                seen.add(path)
                ordinal += 1
                filename = 'uuid-' + str(ordinal).zfill(3) + '.log'
                self.require(['xcrun', 'dwarfdump', '--uuid', path], filename)
                uuids = parse_dwarfdump_uuids((self.destination / filename).read_text(), path)
                if len(uuids) != 1 or uuids[0]['architecture'] != 'arm64' or digest(path) != row['sha256']:
                    raise RuntimeError('Owned image changed or is not the one ARM64 simulator artifact')
                kind = ('launcher' if row['resolved_path'] == executable else
                        'debug-dylib' if row['resolved_path'] == executable + '.debug.dylib' else
                        'compose-framework' if row['resolved_path'] == FRAMEWORK_PATH else 'other-app-image')
                artifacts[str(path)] = dict(origin=origin, relative_path=row['resolved_path'],
                    sha256=row['sha256'], bytes=row['bytes'], uuid=uuids[0]['uuid'], kind=kind)
        build = self.temporary / 'copy/composeApp/build'
        for relative in ('bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp',
                         'xcode-frameworks/Debug/' + self.environment['SDK_NAME'] + '/ComposeApp.framework/ComposeApp'):
            path = build / relative
            if not path.exists():
                continue
            resolved = path.resolve(strict=True)
            resolved.relative_to(build)
            if resolved in seen:
                continue
            if not resolved.is_file() or not 0 < resolved.stat().st_size <= 512 * 1024 * 1024:
                raise RuntimeError('Invalid owned framework artifact size')
            seen.add(resolved)
            ordinal += 1
            filename = 'uuid-' + str(ordinal).zfill(3) + '.log'
            self.require(['xcrun', 'dwarfdump', '--uuid', resolved], filename)
            uuids = parse_dwarfdump_uuids((self.destination / filename).read_text(), resolved)
            if len(uuids) != 1 or uuids[0]['architecture'] != 'arm64':
                raise RuntimeError('Owned build framework is not ARM64 simulator-only')
            artifacts[str(resolved)] = dict(origin='owned-copy-build', relative_path=str(resolved.relative_to(build)),
                sha256=digest(resolved), bytes=resolved.stat().st_size, uuid=uuids[0]['uuid'], kind='compose-framework')
        if self.mode == 'adhoc':
            self.require(['/usr/bin/codesign', '--verify', '--strict', '--verbose=2', app], 'built-app-signature.log')
            self.require(['/usr/bin/codesign', '--verify', '--strict', '--verbose=2', installed], 'installed-app-signature.log')
        write_json(self.destination / 'external-image-artifact-bindings.json', artifacts)
        return installed / executable, artifacts

    def prepare_region_helper(self):
        helper = self.temporary / 'kernel-image-region'
        if helper.exists() or helper.is_symlink():
            raise RuntimeError('Refuse stale region observer output')
        self.require(['xcrun', '--sdk', 'macosx', 'clang', '-x', 'c', '-std=c11', '-Wall', '-Wextra',
                      '-Werror', '-arch', 'arm64', HERE / 'kernel_image_region.c.in', '-lproc', '-o', helper],
                     'region-helper-compile.log', timeout=60, limit=262144)
        if (helper.is_symlink() or helper.resolve(strict=True) != helper or not helper.is_file() or
                not 0 < helper.stat().st_size <= 1024 * 1024):
            raise RuntimeError('Invalid task-owned region observer executable')
        self.receipt['kernel_region_helper_sha256'] = digest(helper)
        return helper

    def observe_kernel_regions(self, helper, before, backend, pid, selected):
        observations = {}
        for ordinal, (path, row) in enumerate(sorted(selected.items()), 1):
            unchanged_target(before, backend.read(pid))
            if (helper.is_symlink() or helper.resolve(strict=True) != helper or not helper.is_file() or
                    digest(helper) != self.receipt['kernel_region_helper_sha256']):
                raise RuntimeError('Region observer changed after compilation')
            filename = 'kernel-region-' + str(ordinal).zfill(2) + '.json'
            arguments = [helper, str(pid), str(row['sample_start']), str(row['sample_end_inclusive'] + 1), path]
            command_index = len(self.receipt.get('commands', []))
            try:
                self.require(arguments, filename, timeout=10, limit=4096)
            except RuntimeError:
                # Only a normally completed helper failure may add on-disk
                # diagnostics. Never do more work after timeout/cancellation,
                # output-budget failure, or an incomplete command record.
                commands = self.receipt.get('commands', [])
                entry = commands[-1] if len(commands) == command_index + 1 else {}
                if (entry.get('command') == list(map(str, arguments)) and entry.get('log') == filename and
                        type(entry.get('exit_code')) is int and entry['exit_code'] == 1 and
                        isinstance(entry.get('finished_at'), str) and bool(entry['finished_at']) and
                        not any(key in entry for key in ('interrupted_or_failed', 'primary_error', 'command_cleanup_error'))):
                    try:
                        self.collect_kernel_region_layout_failure(helper, before, backend, pid, path, row, filename)
                    except BaseException as diagnostic_error:
                        self.receipt['kernel_region_layout_diagnostic_error_type'] = type(diagnostic_error).__name__
                raise  # Preserve the identical primary failure; never resume the image loop.
            unchanged_target(before, backend.read(pid))
            if (helper.is_symlink() or helper.resolve(strict=True) != helper or not helper.is_file() or
                    digest(helper) != self.receipt['kernel_region_helper_sha256']):
                raise RuntimeError('Region observer changed during query')
            observations[path] = read_json((self.destination / filename).read_text(), maximum=4096)
        return bind_regions(pid, selected, observations)

    def collect_kernel_region_layout_failure(self, helper, before, backend, pid, path, selected, filename):
        # No new process mode: one failure-only native file query in the existing
        # ninth-launch lane. All acceptance code and normal PASS output stay put.
        self.check_watched_outputs()
        unchanged_target(before, backend.read(pid))
        failure_path = self.destination / filename
        failure_raw, failure_sha, failure_identity = image_layout.owned_bytes(failure_path, 4096, capture=True)
        failure = read_json(failure_raw.decode('utf-8'), maximum=4096)
        image_layout.span_failure(failure, selected)
        _raw, helper_sha, helper_identity = image_layout.owned_bytes(helper, 1024 * 1024)
        if helper_sha != self.receipt['kernel_region_helper_sha256']:
            raise RuntimeError('Region observer changed before failure-only layout query')
        artifact_identity = image_layout.artifact_fingerprint(path, selected)
        source = self.receipt.get('source_before')
        if (not isinstance(source, dict) or any(not isinstance(source.get(key), str) for key in
                ('commit', 'tree', 'source_manifest_sha256')) or
                not isinstance(self.receipt.get('approved_control_sha256'), str)):
            raise RuntimeError('Missing source/control context for layout diagnostic')
        page_size = image_layout.validate_host_page_size(os.sysconf('SC_PAGE_SIZE'))
        stem = filename.removesuffix('.json') + '-layout'
        raw_path, result_path = self.temporary / (stem + '.txt'), self.destination / (stem + '.json')
        if any(candidate.exists() or candidate.is_symlink() for candidate in (raw_path, result_path)):
            raise RuntimeError('Refuse stale native layout diagnostic output')
        arguments = ['xcrun', 'otool', '-arch', 'arm64', '-l', path]
        command_index = len(self.receipt['commands'])
        try:
            self.require(arguments, stem, timeout=10, output=raw_path, limit=image_layout.MAX_OUTPUT_BYTES,
                         capture_output_identity=True)
            # Stop immediately if target ownership changed; never perform a
            # second process query or publish a bound diagnostic after that.
            unchanged_target(before, backend.read(pid))
            self.check_watched_outputs()
            _raw, current_helper_sha, current_helper_identity = image_layout.owned_bytes(helper, 1024 * 1024)
            if (current_helper_sha, current_helper_identity) != (helper_sha, helper_identity):
                raise RuntimeError('Region observer changed during layout query')
            if image_layout.artifact_fingerprint(path, selected) != artifact_identity:
                raise RuntimeError('Selected artifact changed during layout query')
            native_raw, _native_sha, native_identity = image_layout.owned_bytes(
                raw_path, image_layout.MAX_OUTPUT_BYTES, capture=True)
            created_output = self.receipt['commands'][command_index].get('output_file_identity')
            if created_output != dict(device=native_identity[0], inode=native_identity[1],
                    uid=native_identity[3], file_type=stat.S_IFMT(native_identity[4])):
                raise RuntimeError('Native layout output replaced after owned command')
            _raw, current_failure_sha, current_failure_identity = image_layout.owned_bytes(failure_path, 4096)
            if (current_failure_sha, current_failure_identity) != (failure_sha, failure_identity):
                raise RuntimeError('Original region failure changed during layout query')
            value = image_layout.make_diagnostic(native_raw, path, selected, failure, page_size)
            unchanged_target(before, backend.read(pid))
            value.update(source={key: source[key] for key in ('commit', 'tree', 'source_manifest_sha256')},
                approved_control_sha256=self.receipt['approved_control_sha256'],
                original_failure_file=filename, original_failure_sha256=failure_sha,
                kernel_helper_sha256=helper_sha, artifact_rehashed_before_and_after=True,
                helper_unchanged=True, lifetime_before_and_after_verified=True)
            write_json(result_path, value)
            self.receipt['kernel_region_layout_failure'] = dict(file=result_path.name,
                sha256=digest(result_path), status='OBSERVED_NOT_PROVENANCE')
        finally:
            # finished_at is written even when stopping a worker fails. Only a
            # resolved command exit without cleanup error authorizes removal of
            # the exact file created by its exclusive-open descriptor. Otherwise
            # leave raw output/replacements to the existing owned finalizer.
            commands = self.receipt.get('commands', [])
            entry = commands[command_index] if len(commands) == command_index + 1 else {}
            if (entry.get('command') == list(map(str, arguments)) and
                    entry.get('log') == 'temporary-only/' + raw_path.name and entry.get('finished_at') and
                    'command_cleanup_error' not in entry and type(entry.get('exit_code')) is int and
                    raw_path.is_file() and not raw_path.is_symlink() and raw_path.resolve() == raw_path):
                current = raw_path.lstat()
                if (stat.S_ISREG(current.st_mode) and current.st_uid == os.getuid() and
                        entry.get('output_file_identity') == dict(device=current.st_dev, inode=current.st_ino,
                            uid=current.st_uid, file_type=stat.S_IFMT(current.st_mode))):
                    raw_path.unlink()
            self.receipt['kernel_region_layout_raw_removed'] = not raw_path.exists() and not raw_path.is_symlink()

    def observe_external_images(self):
        executable, artifacts = self.artifact_inventory()
        backend = darwin_backend()  # Actual host capability must work; no guessed entitlements/fallback.
        self.receipt['provenance_status'] = 'RUNNING'
        observer = getattr(self, 'image_observer', 'vmmap')
        helper = self.prepare_region_helper() if observer == 'libproc' else None
        raw_sample = self.temporary / 'owned-app.sample.txt'
        raw_vmmap = self.temporary / 'owned-app.vmmap.txt'
        stdout, stderr = self.temporary / 'owned-app.stdout', self.temporary / 'owned-app.stderr'
        self.watched_outputs = [(stdout, 1024 * 1024), (stderr, 1024 * 1024),
                                (raw_sample, 16 * 1024 * 1024), (raw_vmmap, 16 * 1024 * 1024)]
        begin = time.time()
        self.require(['xcrun', 'simctl', 'launch', '--terminate-running-process',
                      '--stdout=' + str(stdout), '--stderr=' + str(stderr), self.uuid, APP_ID,
                      '-AppleLanguages', '(en)', '-AppleLocale', 'en_US'], 'public-launch.log', timeout=60)
        end = time.time()
        pid = parse_launch_pid((self.destination / 'public-launch.log').read_text())
        before = attest_target(backend.read(pid), pid, executable, (begin, end), self.baseline)
        self.receipt['external_launch_identity'] = unchanged_target(before, backend.read(pid))
        tool_paths = owned_tool_paths(artifacts, executable, self.uuid, before)
        # Permit a bounded ordinary launch interval, not an app-internal latch.
        # This launch is not credited as an additional passing UI repetition.
        for _ in range(48):
            time.sleep(0.25)
            self.check_watched_outputs()
        unchanged_target(before, backend.read(pid))
        try:
            self.require(['/usr/bin/sample', str(pid), '1', '10', '-file', raw_sample],
                         'sample-command.log', timeout=45, limit=65536)
            middle = unchanged_target(before, backend.read(pid))
            self.receipt['sample_command_lifetime_verified'] = True
            if (not raw_sample.is_file() or raw_sample.is_symlink() or
                    not 0 < raw_sample.stat().st_size <= 16 * 1024 * 1024):
                raise RuntimeError('Missing or unbounded public sample image evidence')
            try:
                selected = self.parse_external_observation('sample', raw_sample, pid, executable,
                    lambda raw: parse_sample(raw, pid, executable, artifacts, tool_paths=tool_paths),
                    artifacts=artifacts, tool_paths=tool_paths)
            except RuntimeError:
                # A rejected sample never supplies selected images. The one
                # sibling tool attempt is diagnostic-only and cannot rescue it.
                self.receipt['provenance_status'] = 'FAIL'
                if observer == 'libproc':
                    raise  # Explicit observer never falls back to another method.
                try:
                    self.observe_failure_only_vmmap(before, backend, pid, executable,
                                                    artifacts, tool_paths, raw_vmmap)
                except BaseException as diagnostic_error:
                    self.receipt['failure_only_vmmap_error_type'] = type(diagnostic_error).__name__
                raise  # Preserve the original sample exception, including its identity.
            if observer == 'libproc':
                bound = self.observe_kernel_regions(helper, before, backend, pid, selected)
                after = unchanged_target(before, backend.read(pid))
            else:
                self.require(['/usr/bin/vmmap', '-w', str(pid)], 'vmmap', timeout=45,
                             output=raw_vmmap, limit=16 * 1024 * 1024)
                after = unchanged_target(before, backend.read(pid))
                bound = self.parse_external_observation('vmmap', raw_vmmap, pid, executable,
                    lambda raw: bind_vmmap(raw, pid, executable, selected, tool_paths=tool_paths),
                    artifacts=artifacts, tool_paths=tool_paths, selected=selected)
            self.check_watched_outputs()
            # File bytes remain tied to the prior installed/current-build image
            # inventory. This does not claim the mapped pages were rehashed.
            for path, row in selected.items():
                target = Path(path)
                if (target.is_symlink() or target.resolve(strict=True) != target or
                        target.stat().st_size != row['bytes'] or digest(target) != row['sha256']):
                    raise RuntimeError('An observed owned artifact changed during external inspection')
            bound.update(attestations=[self.receipt['external_launch_identity'], middle, after],
                         temporary_sample_sha256=digest(raw_sample), image_observer=observer)
            if observer == 'vmmap':
                bound['temporary_vmmap_sha256'] = digest(raw_vmmap)
            else:
                bound['kernel_region_helper_sha256'] = self.receipt['kernel_region_helper_sha256']
            write_json(self.destination / 'separate-external-image-provenance.json', bound)
            self.receipt['provenance_sha256'] = digest(self.destination / 'separate-external-image-provenance.json')
            self.receipt['provenance_status'] = 'PASS'
        finally:
            for path in (raw_sample, raw_vmmap):
                if path.is_file() and not path.is_symlink():
                    path.unlink()
            self.receipt['raw_external_stack_mapping_files_removed'] = all(not path.exists() for path in (raw_sample, raw_vmmap))
            self.receipt['external_provenance_limitation'] = KERNEL_LIMITATION if observer == 'libproc' else LIMITATION

    def parse_external_observation(self, tool, path, pid, executable, parse, *,
                                   artifacts=None, tool_paths=None, selected=None):
        if tool not in {'sample', 'vmmap'}:
            raise RuntimeError('Unknown external diagnostic producer')
        raw = path.read_text()
        try:
            return parse(raw)
        except RuntimeError:
            # A failed parser keeps its original failure. Preserve closed format
            # metadata before the existing finalizer destroys raw stacks/maps.
            # Do not retain an unknown native path, process name, stack or symbol.
            try:
                self.collect_external_failure_diagnostics(tool, raw, pid, executable,
                                                         artifacts, tool_paths, selected)
            except BaseException as diagnostic_interrupt:
                # The primary parser is already failed. Record a cancellation
                # without replacing it or starting further optional native work.
                self.receipt[tool + '_failure_diagnostics_interrupted'] = True
                self.receipt[tool + '_failure_diagnostics_interrupt_type'] = type(diagnostic_interrupt).__name__
            raise

    def collect_external_failure_diagnostics(self, tool, raw, pid, executable,
                                             artifacts, tool_paths, selected):
        try:
            name = tool + '-header-failure.json'
            write_json(self.destination / name, header_diagnostic(raw, pid, executable))
            self.receipt[tool + '_header_failure_sha256'] = digest(self.destination / name)
        except Exception as diagnostic_error:
            self.receipt[tool + '_header_diagnostic_error'] = type(diagnostic_error).__name__
        if artifacts is not None:
            try:
                name = tool + '-images-failure.json'
                value = image_diagnostic(raw, tool, artifacts, tool_paths=tool_paths, selected=selected)
                write_json(self.destination / name, value)
                self.receipt[tool + '_images_failure_sha256'] = digest(self.destination / name)
            except Exception as diagnostic_error:
                self.receipt[tool + '_images_diagnostic_error'] = type(diagnostic_error).__name__

    def observe_failure_only_vmmap(self, before, backend, pid, executable,
                                  artifacts, tool_paths, raw_vmmap):
        observation = dict(kind='FAILURE_ONLY_VMMAP_AFTER_SAMPLE_REJECTION', proves_provenance=False,
            status='NOT_STARTED', sample_selection_available=False,
            original_sample_failure_preserved=True, command_attempted=False, command_succeeded=False,
            lifetime_before_verified=False, lifetime_after_verified=False)
        self.receipt['failure_only_vmmap_after_sample'] = observation
        if self.receipt.get('sample_failure_diagnostics_interrupted'):
            observation['status'] = 'NOT_RUN_PRIOR_DIAGNOSTIC_INTERRUPTION'
            return
        try:
            self.check_watched_outputs()
            unchanged_target(before, backend.read(pid))
            observation['lifetime_before_verified'] = True
        except BaseException as error:
            observation.update(status='NOT_RUN_PREFLIGHT_FAILED', preflight_error_type=type(error).__name__)
            return
        try:
            observation.update(status='COMMAND_RUNNING', command_attempted=True)
            self.require(['/usr/bin/vmmap', '-w', str(pid)], 'vmmap-failure-only', timeout=45,
                         output=raw_vmmap, limit=16 * 1024 * 1024)
            observation['command_succeeded'] = True
        except BaseException as error:
            observation['command_error_type'] = type(error).__name__
        finally:
            try:
                unchanged_target(before, backend.read(pid))
                observation['lifetime_after_verified'] = True
            except BaseException as error:
                observation['postflight_error_type'] = type(error).__name__
        if not observation['command_succeeded'] or not observation['lifetime_after_verified']:
            observation['status'] = 'FAILED_COMMAND_OR_LIFETIME'
            return
        try:
            self.check_watched_outputs()
            if (not raw_vmmap.is_file() or raw_vmmap.is_symlink() or
                    not 0 < raw_vmmap.stat().st_size <= 16 * 1024 * 1024):
                raise RuntimeError('Missing or unbounded failure-only mapping observation')
            # No bind_vmmap call and no selected-image result: only closed
            # diagnostics, even if vmmap alone appears plausible.
            self.collect_external_failure_diagnostics('vmmap', raw_vmmap.read_text(), pid, executable,
                                                     artifacts, tool_paths, None)
            observation['status'] = ('COLLECTED_FAILURE_ONLY_NOT_PROVENANCE'
                if 'vmmap_images_failure_sha256' in self.receipt and 'vmmap_header_failure_sha256' in self.receipt
                else 'FAILED_DIAGNOSTIC_COLLECTION')
        except BaseException as error:
            observation.update(status='FAILED_DIAGNOSTIC_COLLECTION', diagnostic_error_type=type(error).__name__)

    def shutdown_device(self):
        if self.lifecycle is not None:
            return self.lifecycle.shutdown()
        info = self.simulator_metadata('owned-device-before-shutdown')
        if info is None or info['state'] == 'Shutdown':
            return
        self.require(['xcrun', 'simctl', 'shutdown', self.uuid], 'shutdown.log')
        after = self.simulator_metadata('owned-device-after-shutdown')
        if after is None or after['state'] != 'Shutdown':
            raise RuntimeError('Owned simulator did not reach Shutdown')

    def delete_device(self):
        if self.lifecycle is not None:
            return self.lifecycle.delete()
        info = self.simulator_metadata('owned-device-before-delete')
        if info is not None:
            if info['state'] != 'Shutdown':
                raise RuntimeError('Do not delete a still-running owned simulator')
            self.require(['xcrun', 'simctl', 'delete', self.uuid], 'delete.log')
        after = self.simulator_metadata('owned-device-after-delete')
        path = Path.home() / 'Library/Developer/CoreSimulator/Devices' / self.uuid
        self.receipt['owned_device_absent'] = after is None and not path.exists() and not path.is_symlink()
        if not self.receipt['owned_device_absent']:
            raise RuntimeError('Owned simulator metadata or directory remains')

    def verify_workers(self):
        live = self.owner.refresh(inspect_files=True) if self.owner is not None else []
        unknown = self.owner.unknown_holders if self.owner is not None else []
        self.receipt['owned_processes_remaining'] = self.owner.receipt_members(live) if self.owner is not None else []
        self.receipt['unknown_holders'] = self.owner.receipt_members(unknown) if self.owner is not None else []
        self.receipt['ownership_observed'] = self.owner.receipt_members() if self.owner is not None else []
        if live or unknown:
            raise RuntimeError('Live owned/unknown holders require targeted cleanup; retain task files')
        return True

    def verify_copy(self):
        value = inspect_copied_inputs_after_build(self.temporary / 'copy', self.copy_manifest)
        write_json(self.destination / 'copied-inputs-after-build.json', value)
        self.receipt['copied_sources_unchanged'] = value['unchanged']
        if not value['unchanged']:
            raise RuntimeError('Build mutated/added/removed a copied input; source binding invalidated')

    def remove_temporary(self):
        if self.temporary.is_symlink() or self.temporary.resolve(strict=True) != self.temporary:
            raise RuntimeError('Refuse removal of a redirected task allocation')
        state = self.temporary.lstat()
        if (not stat.S_ISDIR(state.st_mode) or state.st_uid != os.getuid() or
                (state.st_dev, state.st_ino, state.st_uid) != self.allocation_identity):
            raise RuntimeError('Task temporary allocation identity changed')
        for row in self.links:
            link = Path(row['link'])
            if not link.is_symlink() or str(link.resolve(strict=True)) != row['target']:
                raise RuntimeError('Shared cache link changed; preserve allocation for ownership review')
            link.unlink()
        shutil.rmtree(self.temporary)  # Exact mkdtemp allocation; never original outputs/global caches.

    def compact_log(self):
        path = self.destination / 'xcodebuild.log'
        if not path.is_file() or path.is_symlink():
            return
        self.receipt['xcodebuild_log_uncompressed_sha256'] = digest(path)
        with path.open('rb') as source, (self.destination / 'xcodebuild.log.gz').open('xb') as output:
            with gzip.GzipFile(fileobj=output, mode='wb', mtime=0) as zipped:
                shutil.copyfileobj(source, zipped, length=1024 * 1024)
        path.unlink()

    def finalize(self):
        if self.gradle_attempted and self.owner is not None:
            self.stage('final-isolated-gradle-stop', lambda: self.stop_gradle('stop-final'))
        if self.lifecycle is not None:
            info = self.stage('recover-journaled-owned-simulator-identity', self.lifecycle.recover)
            if isinstance(info, dict):
                self.uuid = info['udid']
                self.receipt['owned_uuid'] = self.uuid
        if self.lifecycle is None and self.uuid is None and self.receipt.get('simulator_creation_attempted'):
            info = self.stage('recover-owned-simulator-identity', lambda: self.simulator_metadata('owned-device-recovery'))
            if isinstance(info, dict):
                self.uuid = info['udid']
                self.receipt['owned_uuid'] = self.uuid
        if self.uuid is not None:
            device_cleanup_allowed = self.lifecycle is None or self.stage('authorize-journaled-device-cleanup',
                lambda: self.lifecycle.prepare_cleanup(self.owner, self.gradle_attempted)) is True
            if device_cleanup_allowed:
                self.stage('shutdown-owned-simulator-and-app', self.shutdown_device)
                self.stage('delete-owned-simulator', self.delete_device)
        if self.lifecycle is not None:
            self.stage('finalize-direct-simulator-lifecycle', self.lifecycle.finish)
        if self.owner is not None:
            self.stage('stop-owned-command-workers', self.owner.stop)
            self.receipt['secondary_cleanup'] = self.stage('cleanup-attested-secondary-fifos', self.owner.secondary.cleanup)
            self.receipt['secondary_attestation_errors'] = list(self.owner.secondary_errors.values())
            if self.owner.secondary_errors:
                self.errors.append(dict(stage='secondary-attestation-completeness', type='UnattestedSecondaryPath'))
            self.stage('preserve-secondary-fifo-final-ledger', lambda: write_json(
                self.destination / 'secondary-fifo-final.json', self.owner.secondary.dump()))
        safe = self.stage('verify-workers-before-file-removal', self.verify_workers) is True
        preservation_safe = self.lifecycle is None or not self.gradle_attempted or self.receipt.get('postbuild_evidence_preserved') is True
        if self.copy_manifest is not None and safe:
            self.stage('verify-copied-inputs-after-workers-stop', self.verify_copy)
        self.stage('compact-required-xcode-evidence', self.compact_log)
        if self.temporary is not None and safe and preservation_safe:
            self.stage('remove-exact-owned-copy-build-deriveddata-temp', self.remove_temporary)
        self.receipt['temporary_directory_removed'] = self.temporary is None or (
            not self.temporary.exists() and not self.temporary.is_symlink())
        self.receipt['source_after'] = self.stage('read-final-source-identity', normalized_identity)
        self.receipt['controls_after_sha256'] = self.stage('read-final-control-identity', lambda: control_hash(self.binding))
        self.receipt['source_unchanged'] = self.receipt['source_after'] is not None and self.receipt['source_after'] == self.receipt['source_before']
        self.receipt['controls_unchanged'] = self.receipt['controls_after_sha256'] == self.approved
        self.receipt['original_outputs_preserved'] = [str(path.relative_to(ROOT)) for path in self.original_outputs
                                                     if path.exists() or path.is_symlink()]
        self.receipt['cleanup_errors'] = self.errors
        self.receipt['cleanup_status'] = 'PASS' if safe and not self.errors and self.receipt['temporary_directory_removed'] else 'FAIL'
        self.receipt['cleanup_method'] = ('Immediate copied-wrapper Gradle stop; exact owned simulator '
            'shutdown/delete; reused descendant/JVM ownership and secondary-FIFO attestation; '
            'unknown-holder checks; exact copied-input verification; unlink shared-cache pointers '
            'without deleting global caches; remove only this allocation including generated build and DerivedData.')
        complete = (self.receipt['runtime_evidence_status'] == self.receipt['provenance_status'] == 'PASS' and
                    self.receipt['notice_package_status'] == 'PASS' and
                    self.receipt['cleanup_status'] == 'PASS' and not self.receipt.get('error') and
                    self.receipt['source_unchanged'] and self.receipt['controls_unchanged'] and
                    self.receipt.get('copied_sources_unchanged') is True and not self.receipt['original_outputs_preserved'])
        self.receipt['status'] = 'PASS' if complete else 'FAIL'

    def run(self):
        try:
            self.prepare()
            self.run_xctest()
            self.observe_external_images()
        except BaseException as error:
            for field in ('runtime_evidence_status', 'provenance_status', 'notice_package_status'):
                if self.receipt[field] == 'RUNNING':
                    self.receipt[field] = 'FAIL'
            self.receipt['error'] = dict(type=type(error).__name__, message=str(error)[:800])
        finally:
            # Reuse deferred Python-parent signal handlers, never inherited
            # blocked child masks. Even interruption cannot skip owned cleanup.
            # A full diagnostic file must not veto the shutdown commands that
            # stop its producer. Evidence-budget failure already prevents PASS.
            self.watched_outputs = []
            with defer_parent_signals():
                try:
                    self.finalize()
                except BaseException as error:
                    self.receipt.update(status='FAIL', cleanup_status='FAIL', finalizer_error=type(error).__name__)
                finally:
                    self.receipt['finished_at'] = now()
                    self.save()
                    print(json.dumps({key: self.receipt.get(key) for key in
                          ('cycle', 'status', 'runtime_evidence_status', 'provenance_status', 'cleanup_status',
                           'source_unchanged', 'controls_unchanged', 'temporary_directory_removed', 'error')}, indent=2), flush=True)
        return 0 if self.receipt['status'] == 'PASS' else 1


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if len(arguments) == 2 and arguments[0] == '--control-manifest':
        binding = checked_binding_path(arguments[1])
        print(json.dumps(dict(control_sha256=control_hash(binding), files=control_manifest(binding)), indent=2))
        return 0  # Source/control observation only. No allocation or native execution.
    if len(arguments) not in (3, 4, 5, 6, 7) or re.fullmatch(r'ios-readiness-[0-9]{2}', arguments[0]) is None:
        raise SystemExit('Usage: run_normal_ios_launch.py ios-readiness-NN SOURCE_BINDING REVIEWED_CONTROL_SHA [--simulator-signing=adhoc] [--image-observer=libproc] [--toolchain=qualified-xcode-26.3] [--simulator-lifecycle=direct-owned-v1]')
    name, binding, approved = arguments[0], checked_binding_path(arguments[1]), arguments[2]
    toolchain, remaining = selected_toolchain(arguments[3:])
    lifecycle_mode, remaining = selected_lifecycle(remaining, toolchain)
    observer, signing = selected_observer(remaining)
    mode = selected_mode(signing)
    if re.fullmatch(r'[a-f0-9]{64}', approved) is None or approved != control_hash(binding):
        raise RuntimeError('Control bytes differ from the explicit independent execution review')
    with (binding.parent / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return Lane(name, binding, approved, mode, observer, toolchain, lifecycle_mode).run()


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
