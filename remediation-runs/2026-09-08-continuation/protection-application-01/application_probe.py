#!/usr/bin/env python3
"""One copied-app storage diagnostic; reuse existing source/lifecycle finalizer.

No execution on import. Neither collection status nor XCTest success means
strict Complete, normal Debug provenance, game/runtime qualification, or Store.
"""
import difflib
import fcntl
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import stat
import sys
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NORMAL = ROOT / 'remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/run_normal_ios_launch.py'
NORMAL_SHA = '766087ddf72b50f51b1c4072c64afb1a7dd231f047d26e534817cddf9be295bf'
SHARED = HERE.parent / 'protection-diagnostic-01'
sys.path.insert(0, str(HERE))
from application_receipts import COLLECTED, metadata_only, require, validate_application, validate_host

require(NORMAL.resolve() == NORMAL and hashlib.sha256(NORMAL.read_bytes()).hexdigest() == NORMAL_SHA, 'base-drift')
_spec = importlib.util.spec_from_file_location('_protection_application_private_normal_lane', NORMAL)
normal = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(normal)
_base_manifest = normal.control_manifest

PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
APP = 'iosApp/iosApp/iOSApp.swift'
UI_TEST = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
METHOD = 'IOSAppLaunchUITests/testActualFilesystemProtectionObservation()'
KOTLIN = 'composeApp/src/iosMain/kotlin/com/parlor/app/storage/ProtectionApplicationProbe.kt'
SAMPLER = 'iosApp/iosApp/ProtectionSampler.m'
ADDITIONS = {KOTLIN: HERE / 'ProtectionApplicationProbe.kt.in',
    SAMPLER: SHARED / 'ProtectionSampler.m', 'iosApp/iosApp/ProtectionSampler.h': SHARED / 'ProtectionSampler.h',
    'iosApp/iosApp/ProtectionApplicationBridge.h': HERE / 'ProtectionApplicationBridge.h.in'}
UNCHANGED_STORAGE = ('IosSnapshotFileSystem.kt', 'IosSnapshotKeychain.kt')
RECORDS = ('parlor-protection-application.json',) + tuple(
    'parlor-protection-application-sample-%02d.json' % n for n in range(1, 7))
HOST_SDK_MACRO_NAMES = ('F_GETPROTECTIONCLASS', 'F_SETPROTECTIONCLASS', 'ATTR_CMN_RETURNED_ATTRS',
    'ATTR_CMN_DATA_PROTECT_FLAGS', 'ATTR_BIT_MAP_COUNT', 'MNT_CPROTECT', 'PROTECTION_CLASS_A')


def parse_host_sdk_macros(raw):
    """Only named definitions are text; unrelated SDK bytes need not be UTF-8."""
    require(type(raw) is bytes and 0 < len(raw) <= 4 * 1024 * 1024, 'host-sdk-macro-output')
    lines, macros = raw.split(b'\n'), {}
    for name in HOST_SDK_MACRO_NAMES:
        prefix = b'#define ' + name.encode('ascii')
        # Recognize the exact name, not a longer macro sharing its prefix.
        # An empty/function-like/malformed named definition is not absence.
        selected = [line[len(prefix):] for line in lines if line.startswith(prefix) and
                    line[len(prefix):len(prefix) + 1] in (b'', b' ', b'\t', b'(')]
        require(len(selected) <= 1 and all(value.startswith(b' ') and 0 < len(value[1:]) <= 256 and
                all(32 <= byte < 127 for byte in value[1:]) for value in selected), 'host-sdk-macro')
        macros[name] = dict(available=bool(selected),
            definition=selected[0][1:].decode('ascii') if selected else None)
    return macros


def control_manifest(binding=None):
    rows = _base_manifest(binding)
    extra = [path for path in HERE.iterdir() if path.is_file() and path.suffix in {'.py', '.in', '.md'}]
    extra += [SHARED / name for name in ('ProtectionSampler.h', 'ProtectionSampler.m')]
    extra += [ROOT / path for path in ('.github/workflows/production-verification.yml', 'scripts/ci/native_continuation.py')]
    for path in extra:
        require(path.resolve() == path and path.is_file() and not path.is_symlink(), 'control-path')
        rows.append(dict(path=str(path.relative_to(ROOT)), sha256=normal.digest(path)))
    require(len({row['path'] for row in rows}) == len(rows), 'duplicate-control')
    return sorted(rows, key=lambda row: row['path'])


def control_hash(binding=None):
    return hashlib.sha256(json.dumps(control_manifest(binding), separators=(',', ':')).encode()).hexdigest()


normal.control_manifest, normal.control_hash = control_manifest, control_hash


def once(text, old, new):
    require(text.count(old) == 1, 'copy-anchor')
    return text.replace(old, new)


def render(text, values):
    for name, value in values.items():
        token = '__PROTECTION_' + name + '__'
        require(token in text, 'missing-template-binding')
        text = text.replace(token, json.dumps(value))
    require('__PROTECTION_' not in text, 'unexpanded-template-binding')
    return text


def transform_project(text):
    require('SWIFT_OBJC_BRIDGING_HEADER' not in text and 'F08A01002C000001' not in text, 'existing-bridge')
    text = once(text, '/* Begin PBXBuildFile section */', '''/* Begin PBXBuildFile section */
		F08A01002C00000100000001 /* ProtectionSampler.m in Sources */ = {isa = PBXBuildFile; fileRef = F08A01002C00000100000002 /* ProtectionSampler.m */; settings = {COMPILER_FLAGS = "-Wall -Wextra -Werror"; }; };''')
    text = once(text, '/* Begin PBXFileReference section */', '''/* Begin PBXFileReference section */
		F08A01002C00000100000002 /* ProtectionSampler.m */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.objc; path = ProtectionSampler.m; sourceTree = "<group>"; };
		F08A01002C00000100000003 /* ProtectionSampler.h */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.h; path = ProtectionSampler.h; sourceTree = "<group>"; };
		F08A01002C00000100000004 /* ProtectionApplicationBridge.h */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.h; path = ProtectionApplicationBridge.h; sourceTree = "<group>"; };''')
    text = once(text, '\t\t\t\tAB1D03C62B0E0FA8002A1234 /* ContentView.swift */,',
        '\t\t\t\tAB1D03C62B0E0FA8002A1234 /* ContentView.swift */,\n'
        '\t\t\t\tF08A01002C00000100000002 /* ProtectionSampler.m */,\n'
        '\t\t\t\tF08A01002C00000100000003 /* ProtectionSampler.h */,\n'
        '\t\t\t\tF08A01002C00000100000004 /* ProtectionApplicationBridge.h */,')
    text = once(text, '\t\t\t\tAB1D03C72B0E0FA8002A1234 /* ContentView.swift in Sources */,',
        '\t\t\t\tAB1D03C72B0E0FA8002A1234 /* ContentView.swift in Sources */,\n'
        '\t\t\t\tF08A01002C00000100000001 /* ProtectionSampler.m in Sources */,')
    return once(text, '\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID).debug";',
        '\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID).debug";\n'
        '\t\t\t\tSWIFT_OBJC_BRIDGING_HEADER = "$(SRCROOT)/iosApp/ProtectionApplicationBridge.h";')


def bounded_metadata(path):
    before = path.lstat()
    require(path.resolve(strict=True) == path and stat.S_ISREG(before.st_mode) and before.st_uid == os.getuid()
            and before.st_nlink == 1 and 0 < before.st_size <= 131072, 'owned-metadata-file')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        opened = os.fstat(fd)
        require((opened.st_dev, opened.st_ino, opened.st_size) == (before.st_dev, before.st_ino, before.st_size), 'metadata-replaced')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            raw = stream.read(131073)
        after = os.fstat(fd)
        require(len(raw) == opened.st_size == after.st_size and opened.st_mtime_ns == after.st_mtime_ns, 'metadata-mutated')
    finally:
        os.close(fd)
    value = normal.read_json(raw.decode(), maximum=131072)
    metadata_only(value)
    return raw, value


def validate_xctest(summary, tests, device):
    expected = dict(result='Passed', totalTestCount=1, passedTests=1, failedTests=0, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(key)) is type(value) and summary[key] == value for key, value in expected.items()) and
            not summary.get('testFailures'), 'xctest-summary')
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == 1 and all(row.get('deviceId') == device and row.get('architecture') == 'arm64'
        and row.get('platform') == 'iOS Simulator' for row in (configs[0].get('device', {}), devices[0])), 'xctest-device')
    pending, cases, count = list(tests.get('testNodes', [])), [], 0
    while pending:
        row = pending.pop(); count += 1
        require(count <= 512, 'xctest-node-budget')
        if row.get('nodeType') == 'Test Case': cases.append(row)
        pending.extend(row.get('children', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == METHOD and cases[0].get('result') == 'Passed', 'xctest-method')


class ApplicationLane(normal.Lane):
    def prepare(self):
        self.receipt.update(execution_kind='actual-filesystem-protection-diagnostic-only', diagnostic_status='NOT_RUN',
            image_observer='NOT_RUN_DIAGNOSTIC_ONLY',
            scope='One copied Debug app invokes byte-identical IosSnapshotFileSystem/Keychain with public synthetic bytes, '
                  'real backup exclusion and atomic overwrite. Metadata collection and separate host-native same-inode reads '
                  'only; not strict L08, GameSnapshot validation, normal B, combined D, physical hardware or Store qualification.')
        super().prepare()
        self.token = str(uuid.uuid4())
        copy = self.temporary / 'copy'
        values = dict(TOKEN=self.token, DEVICE=self.uuid, CONTROLS=self.approved,
                      SOURCE=self.receipt['source_before']['source_manifest_sha256'])
        app = once((copy / APP).read_text(), '            ContentView()', '            ProtectionApplicationView()')
        app += '\n' + render((HERE / 'ProtectionApplicationLaunch.swift.in').read_text(), values)
        (copy / APP).write_text(app)
        (copy / PROJECT).write_text(transform_project((copy / PROJECT).read_text()))
        (copy / UI_TEST).write_text(render((HERE / 'ProtectionApplicationUITest.swift.in').read_text(),
            dict(TOKEN=self.token, DEVICE=self.uuid, DEVICE_NAME=self.receipt['owned_device_name'])))
        for relative, source in ADDITIONS.items():
            target = copy / relative
            require(not target.exists() and not target.is_symlink() and target.parent.resolve() == target.parent, 'addition-path')
            with target.open('xb') as output:
                output.write(source.read_bytes())
        changed = {APP, PROJECT, UI_TEST}
        for row in self.copy_manifest:
            observed = normal.digest(copy / row['path'])
            require((observed != row['copied_sha256']) == (row['path'] in changed), 'copy-change-set')
            row['copied_sha256'] = observed
        require(changed <= {row['path'] for row in self.copy_manifest}, 'copy-input-missing')
        self.copy_manifest += [dict(path=path, original_sha256=None, copied_sha256=normal.digest(copy / path)) for path in ADDITIONS]
        require(normal.inspect_copied_inputs_after_build(copy, self.copy_manifest)['unchanged'], 'copy-inventory')
        for name in UNCHANGED_STORAGE:
            relative = 'composeApp/src/iosMain/kotlin/com/parlor/app/storage/' + name
            require(normal.digest(copy / relative) == normal.digest(ROOT / relative), 'production-storage-transformation')
        normal.write_json(self.destination / 'copied-source-manifest.json', self.copy_manifest)
        diff = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
            (copy / path).read_text().splitlines(True), fromfile='original/' + path, tofile='protection-copy/' + path)) for path in sorted(changed))
        diff += ''.join(''.join(difflib.unified_diff([], (copy / path).read_text().splitlines(True),
            fromfile='/dev/null', tofile='protection-copy/' + path)) for path in sorted(ADDITIONS))
        (self.destination / 'copied-source.diff').write_text(diff)
        self.receipt.update(run_token=self.token, application_source_transformations=1,
            diagnostic_changed_inputs=sorted(changed), diagnostic_added_inputs=sorted(ADDITIONS),
            copied_source_manifest_sha256=normal.digest(self.destination / 'copied-source-manifest.json'),
            copied_source_diff_sha256=normal.digest(self.destination / 'copied-source.diff'))
        self.save()

    def preserve(self):
        self.receipt['postbuild_evidence_preserved'] = False
        state = dict(extraction={}, records=[], errors=[])
        self.data_container = None
        results = self.temporary / 'Results.xcresult'
        require(not results.is_symlink(), 'redirected-xcresult')
        log = self.destination / 'xcodebuild.log'
        require(log.is_file() and not log.is_symlink() and log.stat().st_size <= 64 * 1024 * 1024, 'build-log-retention')
        state['xcode_log_sha256'] = normal.digest(log)
        if results.is_dir():
            for view in ('summary', 'tests'):
                try:
                    state['extraction'][view] = self.command(['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', results],
                        'xcresult-' + view + '.json')
                except BaseException as error:
                    state['errors'].append(dict(stage='xcresult-' + view, type=type(error).__name__))
        try:
            code = self.command(['xcrun', 'simctl', 'get_app_container', self.uuid, 'com.parlor.app.debug', 'data'], 'app-data-container.log')
            state['container_query_exit'] = code
            if code == 0:
                raw = (self.destination / 'app-data-container.log').read_text().strip()
                path = Path(raw)
                parent = (Path.home() / 'Library/Developer/CoreSimulator/Devices' / self.uuid /
                    'data/Containers/Data/Application').resolve(strict=True)
                require(path.is_absolute() and path.parent == parent and path.resolve(strict=True) == path and
                        path.is_dir() and not path.is_symlink() and path.stat().st_uid == os.getuid() and
                        normal.UUID.fullmatch(path.name), 'owned-app-container')
                self.data_container = path
                for name in RECORDS:
                    item = dict(name=name, status='ABSENT')
                    source = path / 'tmp' / name
                    try:
                        if source.exists() or source.is_symlink():
                            encoded, _ = bounded_metadata(source)
                            with (self.destination / name).open('xb') as output:
                                output.write(encoded)
                            item.update(status='RETAINED_CLOSED_METADATA', sha256=hashlib.sha256(encoded).hexdigest(), bytes=len(encoded))
                    except BaseException as error:
                        item.update(status='FAILED_REDACTED', error_type=type(error).__name__)
                        state['errors'].append(dict(stage=name, type=type(error).__name__))
                    state['records'].append(item)
        except BaseException as error:
            state['errors'].append(dict(stage='app-container-retention', type=type(error).__name__))
        normal.write_json(self.destination / 'protection-retention.json', state)
        # This acknowledges all available closed records/absence/failure receipts,
        # not successful collection. It allows safe cleanup after a failed build.
        self.receipt['postbuild_evidence_preserved'] = True
        try: self.save()
        except BaseException:
            self.receipt['postbuild_evidence_preserved'] = False
            raise
        return state

    def run_xctest(self):
        self.gradle_attempted = True
        self.receipt.update(build_attempted=True, diagnostic_status='RUNNING')
        self.save()
        arguments = normal.xcode_arguments(self.temporary / 'copy' / PROJECT,
            self.environment['SDK_NAME'], self.uuid, self.temporary, self.signing)
        arguments[arguments.index('-only-testing:' + normal.SELECTOR)] = '-only-testing:iosAppUITests/' + METHOD.removesuffix('()')
        for key in ('-test-iterations', '-test-repetition-relaunch-enabled'):
            index = arguments.index(key)
            del arguments[index:index + 2]
        primary = None
        try:
            self.lifecycle.mark_build_attempted()
            self.receipt['xcodebuild_exit_code'] = self.command(arguments, 'xcodebuild.log', timeout=2700, limit=64 * 1024 * 1024)
        except BaseException as error:
            primary = error
        finally:
            self.stage('stop-xcode-immediate', lambda: self.stop_gradle('stop-xcode-immediate'))
            retained = self.stage('preserve-protection-application-evidence', self.preserve)
        if primary is not None: raise primary
        require(retained is not None and not retained['errors'] and retained['extraction'] == {'summary': 0, 'tests': 0}
                and not self.errors and self.receipt.get('xcodebuild_exit_code') == 0, 'native-execution')
        validate_xctest(*[normal.read_json((self.destination / ('xcresult-' + view + '.json')).read_text())
            for view in ('summary', 'tests')], self.uuid)
        require(all(row['status'] == 'RETAINED_CLOSED_METADATA' for row in retained['records']) and len(retained['records']) == 7, 'all-records-retained')
        self.final, *self.samples = [normal.read_json((self.destination / name).read_text()) for name in RECORDS]
        summary = validate_application(self.final, self.samples, token=self.token, device=self.uuid,
            source=self.receipt['source_before']['source_manifest_sha256'], controls=self.approved)
        require(self.data_container.name == self.final['container_id'] and self.final['uid'] == os.getuid() == os.geteuid(), 'application-container-binding')
        normal.write_json(self.destination / 'protection-application-validation.json', summary)
        self.receipt['strict_protection_status'] = summary['strict_status']
        stop = self.destination / 'embedded-gradle-stop.txt'
        require(stop.is_file() and stop.read_text() == 'build_exit=0\nstop_exit=0\n', 'embedded-gradle-stop')
        phase = normal.read_phase_receipt(self.destination / normal.RECEIPT_NAME, self.mode, self.environment['SDK_NAME'], self.temporary / 'copy')
        self.receipt['effective_app_phase'] = dict(mode=phase['mode'], sha256=normal.digest(self.destination / normal.RECEIPT_NAME))
        self.receipt['embedded_gradle_stop_status'] = 'PASS'
        self.receipt['diagnostic_status'] = 'APP_CAPTURED'

    def verify_notice_packages(self, built, installed):
        return  # No additional C/notice qualification; status stays NOT_RUN.

    def observe_external_images(self):
        # No normal eight-launch or libproc B route. Bind only the executed hook
        # and sampler UUIDs to the exact current built/installed Debug artifacts.
        _, artifacts = self.artifact_inventory()
        images = self.final['images']
        for image in (images['kotlin_marker']['implementation'], images['native_sampler']):
            require(image['platforms'] == [7] and any(row['uuid'] == image['uuid'] and
                Path(path).name == image['image_basename'] for path, row in artifacts.items()), 'actual-loaded-image-binding')
        source = self.temporary / 'ProtectionApplicationRead.m'
        source.write_text(render((HERE / 'ProtectionApplicationRead.m.in').read_text(), dict(TOKEN=self.token, DEVICE=self.uuid,
            CONTROLS=self.approved, SOURCE=self.receipt['source_before']['source_manifest_sha256'])))
        helper = self.temporary / 'protection-application-read'
        include = self.temporary / 'copy/iosApp/iosApp'
        self.require(['xcrun', '--sdk', 'macosx', '--show-sdk-version'], 'host-reader-sdk-version.log')
        self.preserve_host_sdk(source, include)
        self.require(['xcrun', '--sdk', 'macosx', 'clang', '-x', 'objective-c', '-std=gnu11', '-fobjc-arc',
            '-Wall', '-Wextra', '-Werror', '-arch', 'arm64', '-I', include, source, include / 'ProtectionSampler.m',
            '-framework', 'Foundation', '-o', helper], 'host-reader-compile.log', timeout=90, limit=1024 * 1024)
        require(helper.is_file() and not helper.is_symlink() and helper.resolve() == helper and helper.stat().st_size <= 1024 * 1024, 'host-reader-artifact')
        before = helper.lstat()
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_uid == os.getuid(), 'host-reader-owner')
        self.receipt['host_reader_source_sha256'] = normal.digest(source)
        self.receipt['host_reader_binary_sha256'] = normal.digest(helper)
        self.require(['xcrun', 'dwarfdump', '--uuid', helper], 'host-reader-uuid.log')
        uuids = normal.parse_dwarfdump_uuids((self.destination / 'host-reader-uuid.log').read_text(), helper)
        require(len(uuids) == 1 and uuids[0]['architecture'] == 'arm64', 'host-reader-uuid')
        directory = self.samples[1]['native']['identity']
        file = self.samples[5]['native']['identity']
        code = self.command([helper, self.data_container, str(directory['device']), str(directory['inode']),
            str(file['device']), str(file['inode'])], 'protection-application-host-reader.json', timeout=30, limit=65536)
        after = helper.lstat()
        require(not helper.is_symlink() and (after.st_dev, after.st_ino, after.st_mode, after.st_uid, after.st_nlink, after.st_size, after.st_mtime_ns) ==
            (before.st_dev, before.st_ino, before.st_mode, before.st_uid, before.st_nlink, before.st_size, before.st_mtime_ns) and
            normal.digest(helper) == self.receipt['host_reader_binary_sha256'], 'host-reader-changed')
        require(code == 0, 'host-reader-execution')
        host = normal.read_json((self.destination / 'protection-application-host-reader.json').read_text(), maximum=65536)
        require(host.get('reader_image', {}).get('uuid') == uuids[0]['uuid'], 'host-loaded-built-uuid')
        self.receipt['host_reader_runtime_binding'] = dict(uuid=uuids[0]['uuid'], unchanged=True,
            device=before.st_dev, inode=before.st_ino, bytes=before.st_size, sha256=self.receipt['host_reader_binary_sha256'])
        normal.write_json(self.destination / 'protection-runtime-host-comparison.json', validate_host(host, self.final, self.samples))
        self.receipt['diagnostic_status'] = COLLECTED

    def preserve_host_sdk(self, source, include):
        self.require(['xcrun', '--sdk', 'macosx', '--show-sdk-path'], 'host-reader-sdk-path.log')
        sdk = Path((self.destination / 'host-reader-sdk-path.log').read_text().strip()).resolve(strict=True)
        require(sdk.is_dir() and sdk.is_relative_to(Path(self.environment['DEVELOPER_DIR']).resolve(strict=True)), 'host-sdk-root')
        headers = []
        for relative in ('usr/include/sys/fcntl.h', 'usr/include/sys/attr.h', 'usr/include/sys/mount.h'):
            path = sdk / relative
            require(path.resolve(strict=True) == path and path.is_file() and path.stat().st_size <= 512 * 1024, 'host-sdk-header')
            headers.append(dict(path=relative, sha256=normal.digest(path), bytes=path.stat().st_size))
        output = self.temporary / 'host-sdk-macros.txt'
        self.require(['xcrun', '--sdk', 'macosx', 'clang', '-x', 'objective-c', '-std=gnu11', '-fobjc-arc',
            '-arch', 'arm64', '-I', include, '-dM', '-E', include / 'ProtectionSampler.m'], 'host-sdk-macros', output=output, limit=4 * 1024 * 1024)
        raw = output.read_bytes()
        require(0 < len(raw) <= 4 * 1024 * 1024, 'host-sdk-macro-output')
        raw_sha256 = hashlib.sha256(raw).hexdigest()
        compressed = gzip.compress(raw, mtime=0)
        archive = self.destination / 'host-reader-sdk-macros.raw.gz'
        with archive.open('xb') as stream:
            stream.write(compressed)
        # Preserve exact bytes/identity before parsing, including on a selected
        # definition failure; never discard or replace undecodable SDK bytes.
        normal.write_json(self.destination / 'host-reader-sdk-macros-identity.json', dict(schema_version=1,
            raw_sha256=raw_sha256, raw_bytes=len(raw), artifact=archive.name,
            artifact_sha256=hashlib.sha256(compressed).hexdigest(), artifact_bytes=len(compressed)))
        macros = parse_host_sdk_macros(raw)
        normal.write_json(self.destination / 'host-reader-sdk-bindings.json', dict(schema_version=1,
            sdk_version=(self.destination / 'host-reader-sdk-version.log').read_text().strip(), headers=headers,
            macros=macros, preprocessor_sha256=raw_sha256, source_sha256=normal.digest(source),
            sampler_sha256=normal.digest(include / 'ProtectionSampler.m')))
        output.unlink()  # Exact owned duplicate; lossless compressed bytes retained.

    def finalize(self):
        if self.receipt.get('diagnostic_status') in ('RUNNING', 'APP_CAPTURED'):
            self.receipt['diagnostic_status'] = 'FAIL'
        super().finalize()
        r = self.receipt
        complete = (r.get('diagnostic_status') == COLLECTED and r['cleanup_status'] == 'PASS' and not r.get('error') and
            r['source_unchanged'] and r['controls_unchanged'] and r.get('copied_sources_unchanged') is True and
            not r['original_outputs_preserved'] and all(r[key] == 'NOT_RUN' for key in
                ('runtime_evidence_status', 'provenance_status', 'notice_package_status')))
        r['status'] = COLLECTED if complete else 'FAIL'


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if len(arguments) == 2 and arguments[0] == '--control-manifest':
        binding = normal.checked_binding_path(arguments[1])
        print(json.dumps(dict(control_sha256=control_hash(binding), files=control_manifest(binding)), indent=2))
        return 0
    require(len(arguments) == 6 and re.fullmatch(r'ios-readiness-[0-9]{2}', arguments[0]) and arguments[3:] ==
        ['--simulator-signing=adhoc', '--toolchain=qualified-xcode-26.3', '--simulator-lifecycle=direct-owned-v1'], 'explicit-qualified-invocation')
    name, binding, approved = arguments[0], normal.checked_binding_path(arguments[1]), arguments[2]
    require(approved == control_hash(binding), 'independent-control-approval')
    with (binding.parent / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        lane = ApplicationLane(name, binding, approved, 'adhoc', toolchain='qualified-xcode-26.3', lifecycle_mode='direct-owned-v1')
        lane.run()
        return 0 if lane.receipt['status'] == COLLECTED else 1


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
