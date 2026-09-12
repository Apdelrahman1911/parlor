#!/usr/bin/env python3
"""One public Settings-sheet observation; never Parlor runtime qualification.

Import allocates no native resources. A private, immutable normal-Lane module
supplies all source, process, simulator and finalizer guards without changing B.
"""
import difflib
import fcntl
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import signal
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NORMAL = ROOT / 'remediation-runs/2026-09-07-local-readiness/native/normal-ios-launch-proposal-01/run_normal_ios_launch.py'
NORMAL_SHA = '766087ddf72b50f51b1c4072c64afb1a7dd231f047d26e534817cddf9be295bf'
METHOD = 'IOSAppLaunchUITests/testSettingsPrimaryLanguageSheet()'
CAPTURED = 'SETTINGS_SHEET_CAPTURED_NOT_APP_QUALIFICATION'
UI_TEST = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
SCHEME = 'iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme'


def require(value, code):
    if not value:
        raise RuntimeError('settings-sheet-' + code)


require(NORMAL.resolve() == NORMAL and hashlib.sha256(NORMAL.read_bytes()).hexdigest() == NORMAL_SHA, 'base-drift')
_spec = importlib.util.spec_from_file_location('_settings_sheet_private_normal_lane', NORMAL)
normal = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(normal)
_base_manifest = normal.control_manifest


def control_manifest(binding=None):
    rows = _base_manifest(binding)  # Retain the original callable, not a recursive replacement.
    extra = [p for p in HERE.iterdir() if p.is_file() and p.suffix in {'.py', '.in', '.md'}]
    extra += [ROOT / p for p in ('.github/workflows/production-verification.yml', 'scripts/ci/native_continuation.py')]
    for path in extra:
        require(path.resolve() == path and path.is_file() and not path.is_symlink(), 'control-path')
        rows.append(dict(path=str(path.relative_to(ROOT)), sha256=normal.digest(path)))
    require(len({row['path'] for row in rows}) == len(rows), 'duplicate-control')
    return sorted(rows, key=lambda row: row['path'])


def control_hash(binding=None):
    return hashlib.sha256(json.dumps(control_manifest(binding), separators=(',', ':')).encode()).hexdigest()


# Every inherited prepare/finalize/lifecycle callback sees this same aggregate.
# This module is private to this diagnostic process; normal's entry point is unchanged.
normal.control_manifest, normal.control_hash = control_manifest, control_hash


def detach_project(text):
    for needle, count in (
        ('\t\t\t\t7555FF76242A565900829871 /* iosApp */,\n', 1),
        ('\t\t\t\tB14500012C00000100000009 /* PBXTargetDependency */,\n', 1),
        ('TestTargetID = 7555FF76242A565900829871;', 1),
        ('\t\t\t\tTEST_TARGET_NAME = iosApp;\n', 2),
        ('\t\t\t\tD5C010012C00000100000003 /* ComposeContainerViewController.swift in Sources */,\n', 1),
        ('\t\t\t\tD5C010012C00000100000005 /* ComposeContainerViewControllerTests.swift in Sources */,\n', 1),
    ):
        require(text.count(needle) == count, 'project-anchor-drift')
        text = text.replace(needle, '')
    return text


def validate_capture(summary, tests, log, uuid):
    expected = dict(result='Passed', totalTestCount=1, passedTests=1, failedTests=0, skippedTests=0, expectedFailures=0)
    require(all(type(summary.get(k)) is type(v) and summary[k] == v for k, v in expected.items()) and not summary.get('testFailures'), 'xctest-summary')
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == 1 and all(d.get('deviceId') == uuid and
        d.get('architecture') == 'arm64' and d.get('platform') == 'iOS Simulator'
        for d in (configs[0].get('device', {}), devices[0])), 'xctest-device')
    pending, cases, visited = list(tests.get('testNodes', [])), [], 0
    while pending:
        item = pending.pop(); visited += 1
        require(visited <= 512, 'xctest-node-budget')
        if item.get('nodeType') == 'Test Case': cases.append(item)
        pending.extend(item.get('children', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == METHOD and cases[0].get('result') == 'Passed', 'xctest-method')
    rows = [line.split('SETTINGS_SHEET_CAPTURE ', 1)[1] for line in log.splitlines() if line.startswith('SETTINGS_SHEET_CAPTURE ')]
    require(len(rows) == 1 and len(rows[0].encode()) <= 2048, 'capture-count-or-budget')
    value = normal.read_json(rows[0], maximum=2048)
    require(isinstance(value, dict) and set(value) == {'schemaVersion', 'simulator', 'labels', 'selectionTapped'} and
        type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and value['simulator'] == uuid and value['selectionTapped'] is False and
        isinstance(value['labels'], list) and len(value['labels']) == 3 and
        all(isinstance(label, str) and 0 < len(label.encode()) <= 128 and not any(ord(c) < 32 or ord(c) == 127 for c in label)
            for label in value['labels']), 'capture-shape')
    return value


class SheetLane(normal.Lane):
    def prepare(self):
        self.receipt.update(execution_kind='public-settings-sheet-diagnostic-only', diagnostic_status='NOT_RUN', image_observer='NOT_RUN_DIAGNOSTIC_ONLY',
            scope='Fresh owned Settings public-language flow; three capped modal action labels only. No selection tap, Parlor build/runtime, provenance, protection or Store claim.')
        super().prepare()
        copy = self.temporary / 'copy'
        swift = (HERE / 'SettingsSheetUITest.swift.in').read_text()
        for token, value in (('__PARLOR_EXPECTED_SIMULATOR_NAME__', self.receipt['owned_device_name']),
                             ('__PARLOR_EXPECTED_SIMULATOR_UUID__', self.uuid)):
            require(swift.count(token) == 1, 'fixture-binding')
            swift = swift.replace(token, json.dumps(value))
        (copy / UI_TEST).write_text(swift)
        (copy / PROJECT).write_text(detach_project((copy / PROJECT).read_text()))
        (copy / SCHEME).write_text((HERE / 'SettingsSheet.xcscheme.in').read_text())
        changed = {UI_TEST, PROJECT, SCHEME}
        for row in self.copy_manifest:
            observed = normal.digest(copy / row['path'])
            require((observed != row['copied_sha256']) == (row['path'] in changed), 'copy-change-set')
            row['copied_sha256'] = observed
        require(changed <= {row['path'] for row in self.copy_manifest}, 'copy-input-missing')
        normal.write_json(self.destination / 'copied-source-manifest.json', self.copy_manifest)
        diff = ''.join(''.join(difflib.unified_diff((ROOT / p).read_text().splitlines(True),
            (copy / p).read_text().splitlines(True), fromfile='original/' + p, tofile='settings-sheet-copy/' + p)) for p in sorted(changed))
        (self.destination / 'copied-source.diff').write_text(diff)
        self.receipt.update(copied_source_manifest_sha256=normal.digest(self.destination / 'copied-source-manifest.json'),
            copied_source_diff_sha256=normal.digest(self.destination / 'copied-source.diff'), diagnostic_changed_inputs=sorted(changed))
        self.save()

    def preserve(self):
        self.receipt.update(postbuild_evidence_preserved=False, parlor_outputs_absent=False)
        results = self.temporary / 'Results.xcresult'
        require(not results.is_symlink(), 'redirected-result')
        log = self.destination / 'xcodebuild.log'
        require(log.is_file() and not log.is_symlink() and log.stat().st_size <= 8 * 1024 * 1024, 'build-log-retention')
        state = dict(xcode_log_sha256=normal.digest(log), xcresult_present=results.exists(), extraction={})
        if results.exists():
            require(results.is_dir(), 'result-type')
            for view in ('summary', 'tests'):
                code = self.command(['xcrun', 'xcresulttool', 'get', 'test-results', view, '--path', results], 'xcresult-' + view + '.json')
                state['extraction'][view] = code
        normal.write_json(self.destination / 'diagnostic-retention.json', state)
        require(all(not path.exists() and not path.is_symlink() for path in (
            self.temporary / 'copy/composeApp/build',
            self.temporary / 'DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app')), 'unexpected-app-build')
        self.receipt['parlor_outputs_absent'] = True
        # Missing/failed XCTest extraction is retained as such, NEVER capture success.
        # No Parlor app/container/phase receipt is expected in this diagnostic.
        self.receipt['postbuild_evidence_preserved'] = True
        try: self.save()
        except BaseException:
            self.receipt['postbuild_evidence_preserved'] = False
            raise
        return state

    def run_xctest(self):
        self.gradle_attempted = True  # Real Swift build: retain the original worker/destruction barrier.
        self.receipt.update(build_attempted=True, diagnostic_status='RUNNING')
        self.save()
        arguments = normal.xcode_arguments(self.temporary / 'copy' / PROJECT,
            self.environment['SDK_NAME'], self.uuid, self.temporary, self.signing)
        arguments[arguments.index('-only-testing:' + normal.SELECTOR)] = '-only-testing:iosAppUITests/' + METHOD.removesuffix('()')
        for key in ('-test-iterations', '-test-repetition-relaunch-enabled'):
            index = arguments.index(key)
            del arguments[index:index + 2]  # Single default execution, not XCTest repetition mode.
        for key, value in (('-default-test-execution-time-allowance', '180'), ('-maximum-test-execution-time-allowance', '240')):
            arguments[arguments.index(key) + 1] = value
        primary = None
        try:
            self.lifecycle.mark_build_attempted()
            self.receipt['xcodebuild_exit_code'] = self.command(arguments, 'xcodebuild.log', timeout=600, limit=8 * 1024 * 1024)
        except BaseException as error:
            primary = error
        finally:
            self.stage('stop-xcode-immediate', lambda: self.stop_gradle('stop-xcode-immediate'))
            retained = self.stage('preserve-settings-sheet-evidence', self.preserve)
        if primary is not None: raise primary
        require(not self.errors and self.receipt.get('xcodebuild_exit_code') == 0 and
            retained['extraction'] == {'summary': 0, 'tests': 0}, 'incomplete-native-execution')
        value = validate_capture(*[normal.read_json((self.destination / ('xcresult-' + view + '.json')).read_text())
            for view in ('summary', 'tests')], (self.destination / 'xcodebuild.log').read_text(), self.uuid)
        normal.write_json(self.destination / 'public-sheet-actions.json', value)
        self.receipt['diagnostic_status'] = 'CAPTURED'

    def observe_external_images(self):
        return  # No normal B launch, notice or libproc observation; their statuses remain NOT_RUN.

    def finalize(self):
        if self.receipt.get('diagnostic_status') == 'RUNNING':
            self.receipt['diagnostic_status'] = 'FAIL'
        super().finalize()  # Every original ownership/source/cleanup guard runs, including real build barrier.
        r = self.receipt
        complete = (r.get('diagnostic_status') == 'CAPTURED' and r['cleanup_status'] == 'PASS' and not r.get('error') and
            r['source_unchanged'] and r['controls_unchanged'] and r.get('copied_sources_unchanged') is True and
            not r['original_outputs_preserved'] and all(r[key] == 'NOT_RUN' for key in
                ('runtime_evidence_status', 'provenance_status', 'notice_package_status')))
        r['status'] = CAPTURED if complete else 'FAIL'


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if len(arguments) == 2 and arguments[0] == '--control-manifest':
        binding = normal.checked_binding_path(arguments[1])
        print(json.dumps(dict(control_sha256=control_hash(binding), files=control_manifest(binding)), indent=2)); return 0
    require(len(arguments) == 6 and re.fullmatch(r'ios-readiness-[0-9]{2}', arguments[0]) and arguments[3:] ==
        ['--simulator-signing=adhoc', '--toolchain=qualified-xcode-26.3', '--simulator-lifecycle=direct-owned-v1'], 'explicit-qualified-invocation')
    name, binding, approved = arguments[0], normal.checked_binding_path(arguments[1]), arguments[2]
    require(approved == control_hash(binding), 'independent-control-approval')
    with (binding.parent / 'build-lane.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        lane = SheetLane(name, binding, approved, 'adhoc', toolchain='qualified-xcode-26.3', lifecycle_mode='direct-owned-v1')
        lane.run()
        return 0 if lane.receipt['status'] == CAPTURED else 1


if __name__ == '__main__':
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
