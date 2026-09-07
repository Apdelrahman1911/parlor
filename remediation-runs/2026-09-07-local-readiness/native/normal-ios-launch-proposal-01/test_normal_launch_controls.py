"""Synthetic guard tests only; no simulator, native process, Gradle or XCTest.

The XCTest/sample/vmmap fixtures describe an accepted receipt, not an actual
observation. Root executes this suite in the one coordinated validation lane.
"""
import copy
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import normal_launch_receipts as receipts
import external_image_provenance as provenance
import normal_source_copy as source_copy
import run_normal_ios_launch as runner

DEVICE = '11111111-2222-3333-4444-555555555555'
EXE = Path('/owned/device/Parlor.app/Parlor')
PID = 43210


def marker_rows():
    rows = []
    for index in range(8):
        uuid = f'{index + 1:08x}-2222-3333-4444-555555555555'
        rows.append(dict(event='begin', observationID=uuid))
        for sample in range(6):
            rows.append(dict(event='sample', observationID=uuid, sample=sample,
                             elapsedSeconds=sample * 2.1, foreground=True, homeExists=True, alertVisible=False))
        rows.append(dict(event='complete', observationID=uuid, elapsedSeconds=11.0))
    return rows


def markers(rows):
    return [receipts.PREFIX + json.dumps(row) + '\n' for row in rows]


def xctest_fixture(total=1):
    device = dict(deviceId=DEVICE, architecture='arm64', platform='iOS Simulator')
    plan = dict(configurationId='1', configurationName='Default')
    summary = dict(result='Passed', totalTestCount=total, passedTests=total, failedTests=0,
                   skippedTests=0, expectedFailures=0, testFailures=[],
                   devicesAndConfigurations=[dict(device=device, testPlanConfiguration=plan,
                       passedTests=total, failedTests=0, skippedTests=0, expectedFailures=0)])
    tests = dict(devices=[device], testPlanConfigurations=[plan], testNodes=[dict(nodeType='UI test bundle',
        name='iosAppUITests', children=[dict(nodeType='Test Case', name='Normal-source launch',
            nodeIdentifier=receipts.METHOD, result='Passed')])])
    details = dict(testIdentifier=receipts.METHOD, testResult='Passed', devices=[device],
        testPlanConfigurations=[plan], testRuns=[dict(nodeType='Repetition', name='Repetition ' + str(index + 1),
            result='Passed', children=[dict(nodeType='Test Case Run', name='Owned iPhone', result='Passed',
                durationInSeconds=15.0)]) for index in range(8)])
    return copy.deepcopy((summary, tests, details))


def artifact_fixture():
    result = {}
    for index, (relative, kind) in enumerate((('Parlor', 'launcher'), ('Parlor.debug.dylib', 'debug-dylib'),
                                            ('Frameworks/ComposeApp.framework/ComposeApp', 'compose-framework'))):
        result[str(EXE.parent / relative)] = dict(origin='installed-app', relative_path=relative,
            sha256=str(index + 1) * 64, bytes=8192, uuid=f'{index + 1:08x}-2222-3333-4444-555555555555', kind=kind)
    return result


def sample_fixture(artifacts=None):
    artifacts = artifact_fixture() if artifacts is None else artifacts
    text = f'Process:         Parlor [{PID}]\nPath:            {EXE}\nCode Type:       ARM-64\n\nBinary Images:\n'
    for index, (path, item) in enumerate(artifacts.items()):
        start = (index + 1) * 0x100000
        text += f'0x{start:x} - 0x{start + 0x3fff:x} +{Path(path).name} (1.0 - 1) <{item["uuid"]}> {path}\n'
    text += '0x9990000 - 0x999ffff /usr/lib/other (1) <99999999-2222-3333-4444-555555555555> /usr/lib/other\n'
    return text


def vmmap_fixture(artifacts=None):
    artifacts = artifact_fixture() if artifacts is None else artifacts
    text = f'Process:         Parlor [{PID}]\nPath:            {EXE}\n'
    for index, path in enumerate(artifacts):
        start = (index + 1) * 0x100000
        text += f'__TEXT                 {start:x}-{start + 0x2000:x} [ 8K] r-x/r-x SM=COW  {path}\n'
    return text


@dataclass(frozen=True)
class Record:
    pid: int = PID
    uid: int = os.getuid()
    started: tuple = (1000, 500000)
    command: str = str(EXE)
    token: tuple = (0, 0, 0, 0, 0, PID, 0, 1)

    @property
    def lifetime(self):
        return self.pid, self.uid, self.started


class MarkerTests(unittest.TestCase):
    def test_eight_complete_trains_are_48_samples_not_48_tests(self):
        result = receipts.verify_markers(markers(marker_rows()))
        self.assertEqual((8, 48), (result['completed_observations'], result['total_samples']))

    def test_seven_launches_fail(self):
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(marker_rows()[:-8]))

    def test_ninth_attempt_fails_even_if_it_is_incomplete(self):
        rows = marker_rows() + [dict(event='begin', observationID='99999999-2222-3333-4444-555555555555')]
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_duplicate_lifetime_fails(self):
        rows = marker_rows()
        for row in rows[8:16]:
            row['observationID'] = rows[0]['observationID']
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_interleaved_trains_fail(self):
        rows = marker_rows()
        rows[1], rows[8] = rows[8], rows[1]
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_out_of_order_or_repeated_sample_fails(self):
        rows = marker_rows()
        rows[3]['sample'] = 1
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_final_sample_must_itself_reach_ten_seconds(self):
        rows = marker_rows()
        rows[6]['elapsedSeconds'] = 9.9
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_complete_cannot_move_back_before_last_sample(self):
        rows = marker_rows()
        rows[7]['elapsedSeconds'] = 10.1
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_boolean_duration_is_not_a_number(self):
        rows = marker_rows()
        rows[1]['elapsedSeconds'] = True
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_nonfinite_json_fails(self):
        for value in ('NaN', 'Infinity', '-Infinity'):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                receipts.read_json('{"value":' + value + '}')

    def test_duplicate_json_keys_fail(self):
        with self.assertRaises(RuntimeError):
            receipts.read_json('{"event":"begin","event":"complete"}')

    def test_failing_foreground_home_alert_observations_fail(self):
        for field, value in (('foreground', False), ('homeExists', False), ('alertVisible', True), ('foreground', 1)):
            rows = marker_rows()
            rows[2][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(RuntimeError):
                receipts.verify_markers(markers(rows))

    def test_unrecognized_marker_field_fails(self):
        rows = marker_rows()
        rows[1]['appSeed'] = 'must never be accepted'
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))

    def test_clock_regression_fails(self):
        rows = marker_rows()
        rows[3]['elapsedSeconds'] = 0
        with self.assertRaises(RuntimeError):
            receipts.verify_markers(markers(rows))


class XCTestTests(unittest.TestCase):
    def test_actual_runs_not_aggregate_method_count_establish_eight(self):
        for total in (1, 8):
            with self.subTest(total=total):
                result = receipts.verify_xctest(*xctest_fixture(total), DEVICE)
                self.assertEqual(8, result['executed_repetitions'])
                self.assertEqual(total, result['reported_summary_total'])

    def test_aggregate_without_actual_runs_fails(self):
        summary, tests, details = xctest_fixture(8)
        details['testRuns'] = []
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_seventh_or_ninth_actual_run_fails(self):
        for delta in (-1, 1):
            summary, tests, details = xctest_fixture()
            if delta < 0:
                details['testRuns'].pop()
            else:
                details['testRuns'].append(copy.deepcopy(details['testRuns'][0]))
            with self.subTest(delta=delta), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_duplicate_run_hierarchy_fails(self):
        summary, tests, details = xctest_fixture()
        details['testRuns'][1] = copy.deepcopy(details['testRuns'][0])
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_repeated_leaf_names_beneath_distinct_repetitions_are_valid(self):
        result = receipts.verify_xctest(*xctest_fixture(), DEVICE)
        self.assertEqual(8, len(result['actual_run_identities']))

    def test_nested_actual_runs_cannot_inflate_count(self):
        summary, tests, details = xctest_fixture()
        parent = details['testRuns'][0]['children'][0]
        parent['children'] = [copy.deepcopy(parent)]
        details['testRuns'].pop()
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_wrong_selected_method_fails(self):
        summary, tests, details = xctest_fixture()
        details['testIdentifier'] = 'SomeOtherTest/test()'
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_additional_test_case_cannot_hide_in_summary(self):
        summary, tests, details = xctest_fixture()
        tests['testNodes'][0]['children'].append(dict(nodeType='Test Case', name='Other',
                                                     nodeIdentifier='Other/test()', result='Passed'))
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_any_skip_failure_expected_failure_or_unknown_fails(self):
        for result in ('Failed', 'Skipped', 'Expected Failure', 'unknown'):
            summary, tests, details = xctest_fixture()
            details['testRuns'][0]['children'][0]['result'] = result
            with self.subTest(result=result), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_aggregate_skip_even_with_passed_case_nodes_fails(self):
        summary, tests, details = xctest_fixture()
        summary['skippedTests'] = 1
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_wrong_device_architecture_platform_and_configuration_fail(self):
        for field, value in (('deviceId', 'other'), ('architecture', 'x86_64'), ('platform', 'iOS')):
            summary, tests, details = xctest_fixture()
            details['devices'][0][field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)
        summary, tests, details = xctest_fixture()
        details['testPlanConfigurations'] = [dict(configurationId='other')]
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_too_short_actual_run_fails_even_with_good_markers(self):
        summary, tests, details = xctest_fixture()
        details['testRuns'][4]['children'][0]['durationInSeconds'] = 9.9
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_boolean_count_fails(self):
        summary, tests, details = xctest_fixture()
        summary['totalTestCount'] = True
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_unknown_result_structure_fails_closed(self):
        summary, tests, details = xctest_fixture()
        details['testRuns'][0]['nodeType'] = 'Undocumented Approximate Run'
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)


class ExternalProvenanceTests(unittest.TestCase):
    def test_exact_public_launch_pid_is_required(self):
        self.assertEqual(PID, provenance.parse_launch_pid(f'{provenance.APP_ID}: {PID}\n'))
        for text in (f'other: {PID}\n', f'{provenance.APP_ID}: 0\n', f'{provenance.APP_ID}: {PID}\nextra',
                     f'{provenance.APP_ID}: 1\n', f' {provenance.APP_ID}: {PID}\n'):
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                provenance.parse_launch_pid(text)

    def test_exact_new_target_attestation(self):
        record = Record()
        self.assertEqual(record, provenance.attest_target(record, PID, EXE, (1000.0, 1001.0), {}))
        self.assertTrue(provenance.unchanged_target(record, record)['bracketed_lifetime_unchanged'])

    def test_wrong_executable_uid_old_lifetime_or_preexisting_pid_fails(self):
        for record, baseline in ((Record(command='/unrelated/process'), {}),
                                 (Record(uid=os.getuid() + 1), {}), (Record(started=(999, 0)), {}),
                                 (Record(), {PID: {}})):
            with self.subTest(record=record), self.assertRaises(RuntimeError):
                provenance.attest_target(record, PID, EXE, (1000.0, 1001.0), baseline)

    def test_disappearance_reuse_and_token_change_fail(self):
        for after in (None, Record(started=(1001, 0)), Record(command='/owned/other'),
                      Record(token=(0, 0, 0, 0, 0, PID, 0, 2))):
            with self.subTest(after=after), self.assertRaises(RuntimeError):
                provenance.unchanged_target(Record(), after)

    def test_tool_images_and_addresses_bind_only_allowed_artifacts(self):
        selected = provenance.parse_sample(sample_fixture(), PID, EXE, artifact_fixture())
        result = provenance.bind_vmmap(vmmap_fixture(), PID, EXE, selected)
        self.assertEqual(3, len(result['images']))
        self.assertNotIn('/usr/lib/other', json.dumps(result))
        self.assertIn('not per-repetition image proof', result['limitation'])

    def test_wrong_tool_pid_or_executable_fails(self):
        for raw in (sample_fixture().replace(f'[{PID}]', '[1]'),
                    sample_fixture().replace('Path:            ' + str(EXE), 'Path:            /unrelated/app')):
            with self.subTest(raw=raw[:60]), self.assertRaises(RuntimeError):
                provenance.parse_sample(raw, PID, EXE, artifact_fixture())

    def test_missing_binary_images_is_not_disk_provenance(self):
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(sample_fixture().replace('Binary Images:', 'No images:'),
                                    PID, EXE, artifact_fixture())

    def test_wrong_runtime_uuid_fails(self):
        raw = sample_fixture().replace('00000003-2222-3333-4444-555555555555',
                                       '99999999-2222-3333-4444-555555555555')
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(raw, PID, EXE, artifact_fixture())

    def test_unbound_compose_framework_cannot_be_silently_ignored(self):
        raw = sample_fixture().replace('/owned/device/Parlor.app/Frameworks/ComposeApp.framework/ComposeApp',
                                       '/unowned/build/ComposeApp.framework/ComposeApp')
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(raw, PID, EXE, artifact_fixture())

    def test_known_build_framework_remains_distinct_from_installed(self):
        artifacts = artifact_fixture()
        installed = str(EXE.parent / 'Frameworks/ComposeApp.framework/ComposeApp')
        path = '/owned/copy/composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp'
        row = artifacts[installed]
        artifacts[path] = dict(row, origin='owned-copy-build', relative_path='bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp')
        observed = {key: row for key, row in artifacts.items() if key != installed}
        selected = provenance.parse_sample(sample_fixture(observed), PID, EXE, artifacts)
        result = provenance.bind_vmmap(vmmap_fixture(observed), PID, EXE, selected)
        self.assertEqual('owned-copy-build', next(row for row in result['images'] if row['kind'] == 'compose-framework')['origin'])

    def test_copied_framework_uuid_must_match_embedded_but_signing_bytes_remain_distinct(self):
        artifacts = artifact_fixture()
        installed = str(EXE.parent / 'Frameworks/ComposeApp.framework/ComposeApp')
        path = '/owned/copy/composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp'
        artifacts[path] = dict(artifacts[installed], origin='owned-copy-build', sha256='f' * 64,
            relative_path='bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp')
        observed = {key: row for key, row in artifacts.items() if key != installed}
        selected = provenance.parse_sample(sample_fixture(observed), PID, EXE, artifacts)
        self.assertFalse(selected[path]['same_file_bytes_as_embedded'])
        artifacts[path]['uuid'] = '99999999-2222-3333-4444-555555555555'
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(sample_fixture(observed), PID, EXE, artifacts)

    def test_duplicate_framework_or_image_fails(self):
        raw = sample_fixture()
        row = next(line for line in raw.splitlines() if '> ' + str(EXE) in line)
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(raw + row + '\n', PID, EXE, artifact_fixture())

    def test_vmmap_without_all_text_mappings_fails(self):
        selected = provenance.parse_sample(sample_fixture(), PID, EXE, artifact_fixture())
        raw = '\n'.join(vmmap_fixture().splitlines()[:-1]) + '\n'
        with self.assertRaises(RuntimeError):
            provenance.bind_vmmap(raw, PID, EXE, selected)

    def test_vmmap_must_map_same_load_address(self):
        selected = provenance.parse_sample(sample_fixture(), PID, EXE, artifact_fixture())
        raw = vmmap_fixture().replace('300000-302000', '301000-303000')
        with self.assertRaises(RuntimeError):
            provenance.bind_vmmap(raw, PID, EXE, selected)

    def test_vmmap_mapping_cannot_exceed_sample_image_range(self):
        selected = provenance.parse_sample(sample_fixture(), PID, EXE, artifact_fixture())
        raw = vmmap_fixture().replace('300000-302000', '300000-309000')
        with self.assertRaises(RuntimeError):
            provenance.bind_vmmap(raw, PID, EXE, selected)


class CopiedSourceTests(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-normal-control-fixture-')
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()
        self.originals = {
            source_copy.UI_TEST: 'Original test\n',
            source_copy.PROJECT: 'shellScript = "./gradlew :composeApp:embedAndSignAppleFrameworkForXcode";\n' +
                '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)\n' * 2,
            'iosApp/iosApp/iOSApp.swift': 'Original production entry\n',
            'composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt': 'Original production controller\n',
        }
        for relative, text in self.originals.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        self.bindings = dict(copy_only=[dict(path=path, sha256=self.digest(self.root / path)) for path in self.originals])

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def transform(self):
        source_copy.transform_copy(self.root, 'Only the XCTest observer changed\n', 'set -u\n./gradlew owned\n')

    def test_only_ui_test_and_owned_phase_may_change(self):
        self.transform()
        rows = source_copy.inventory_copy(self.root, self.bindings, self.digest)
        changed = {row['path'] for row in rows if row['original_sha256'] != row['copied_sha256']}
        self.assertEqual(source_copy.CHANGED, changed)

    def test_app_instrumentation_is_rejected(self):
        self.transform()
        (self.root / 'iosApp/iosApp/iOSApp.swift').write_text('Unexpected init probe\n')
        with self.assertRaises(RuntimeError):
            source_copy.inventory_copy(self.root, self.bindings, self.digest)

    def test_kotlin_instrumentation_is_rejected(self):
        self.transform()
        (self.root / 'composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt').write_text('Probe\n')
        with self.assertRaises(RuntimeError):
            source_copy.inventory_copy(self.root, self.bindings, self.digest)

    def test_unregistered_addition_is_rejected(self):
        self.transform()
        (self.root / 'iosApp/iosApp/ExtraObserver.swift').write_text('Probe\n')
        with self.assertRaises(RuntimeError):
            source_copy.inventory_copy(self.root, self.bindings, self.digest)

    def test_missing_input_and_redirected_input_are_rejected(self):
        self.transform()
        target = self.root / 'iosApp/iosApp/iOSApp.swift'
        target.unlink()
        with self.assertRaises(RuntimeError):
            source_copy.inventory_copy(self.root, self.bindings, self.digest)
        target.symlink_to(self.root / source_copy.UI_TEST)
        with self.assertRaises(RuntimeError):
            source_copy.inventory_copy(self.root, self.bindings, self.digest)

    def test_ambiguous_or_wrong_shell_phase_is_rejected(self):
        path = self.root / source_copy.PROJECT
        path.write_text(path.read_text() + 'shellScript = "other";\n')
        with self.assertRaises(RuntimeError):
            self.transform()


class RunnerGuards(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-normal-runner-fixture-')
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()

    def lane(self):
        value = object.__new__(runner.Lane)
        value.temporary = self.root / 'owned'
        value.temporary.mkdir()
        state = value.temporary.stat()
        value.allocation_identity = (state.st_dev, state.st_ino, state.st_uid)
        value.destination = self.root / 'evidence'
        value.destination.mkdir()
        value.links, value.errors, value.original_outputs, value.watched_outputs = [], [], [], []
        value.uuid, value.binding, value.copy_manifest = DEVICE, self.root / 'binding.json', None
        value.owner = Mock()
        value.owner.refresh.return_value = []
        value.owner.unknown_holders = []
        value.owner.secondary_errors = {}
        value.owner.receipt_members.return_value = []
        value.owner.secondary.dump.return_value = {}
        value.owner.secondary.cleanup.return_value = {'status': 'PASS'}
        value.owner.stop.return_value = None
        value.gradle_attempted, value.approved = True, 'a' * 64
        value.stop_gradle, value.shutdown_device, value.delete_device = Mock(), Mock(), Mock()
        value.stop_gradle.return_value = value.shutdown_device.return_value = value.delete_device.return_value = None
        value.receipt = dict(runtime_evidence_status='PASS', provenance_status='PASS',
                             source_before={'frozen': True}, copied_sources_unchanged=True, gradle_stops=[])
        value.environment = dict(SDK_NAME='iphonesimulator26.5')
        value.signing = runner.signing_overrides('disabled')
        return value

    def unallocated_lane(self):
        lane = self.lane()
        lane.temporary.rmdir()  # This test's empty synthetic allocation only.
        owner = lane.owner
        lane.temporary = lane.owner = lane.allocation_identity = lane.uuid = None
        lane.name, lane.baseline, lane.gradle_attempted = 'ios-readiness-99', {}, False
        lane.receipt.update(runtime_evidence_status='NOT_RUN', provenance_status='NOT_RUN')
        lane.save = Mock()
        return lane, owner

    def finish(self, lane):
        with patch.object(runner, 'normalized_identity', return_value={'frozen': True}), \
                patch.object(runner, 'control_hash', return_value=lane.approved):
            lane.finalize()

    def test_allocation_canonicalizes_parent_before_mkdtemp_and_retains_exact_identity(self):
        lane, owner = self.unallocated_lane()
        alias = self.root / 'temp-alias'
        alias.symlink_to(self.root, target_is_directory=True)
        with patch.object(runner.tempfile, 'gettempdir', return_value=str(alias)), \
                patch.object(runner, 'AppHostOwnership', return_value=owner) as construct:
            lane.allocate_temporary()
        self.assertEqual(self.root, lane.temporary.parent)
        self.assertEqual(lane.temporary, lane.temporary.resolve(strict=True))
        state = lane.temporary.lstat()
        self.assertEqual((state.st_dev, state.st_ino, state.st_uid), lane.allocation_identity)
        self.assertEqual(list(lane.allocation_identity), lane.receipt['temporary_allocation_identity'])
        construct.assert_called_once_with({}, lane.temporary, [], lane.destination)
        lane.save.assert_called_once()
        self.finish(lane)
        self.assertFalse(lane.temporary.exists())
        self.assertTrue(alias.is_symlink())  # Unrelated parent alias is not ours to remove.
        self.assertEqual('PASS', lane.receipt['cleanup_status'])
        self.assertEqual('FAIL', lane.receipt['status'])  # No native run occurred.

    def test_deferred_interrupt_after_mkdtemp_keeps_finalizer_ownership(self):
        lane, owner = self.unallocated_lane()
        mkdtemp = runner.tempfile.mkdtemp
        old_handler = signal.getsignal(signal.SIGINT)
        def allocated_then_interrupt(*args, **kwargs):
            path = mkdtemp(*args, **kwargs)
            handler = signal.getsignal(signal.SIGINT)
            self.assertIsNot(handler, signal.default_int_handler)
            handler(signal.SIGINT, None)  # Invoke the installed deferrer; never signal a process.
            return path
        try:
            signal.signal(signal.SIGINT, signal.default_int_handler)
            with patch.object(runner.tempfile, 'gettempdir', return_value=str(self.root)), \
                    patch.object(runner.tempfile, 'mkdtemp', side_effect=allocated_then_interrupt), \
                    patch.object(runner, 'AppHostOwnership', return_value=owner), \
                    self.assertRaises(KeyboardInterrupt):
                lane.allocate_temporary()
            self.assertIs(signal.getsignal(signal.SIGINT), signal.default_int_handler)
        finally:
            signal.signal(signal.SIGINT, old_handler)
        self.assertIs(owner, lane.owner)
        self.assertIsNotNone(lane.allocation_identity)
        self.assertEqual(lane.temporary, lane.temporary.resolve(strict=True))
        self.finish(lane)
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('PASS', lane.receipt['cleanup_status'])
        lane.stop_gradle.assert_not_called()
        lane.shutdown_device.assert_not_called()

    def test_allocation_receipt_failure_does_not_strand_owned_directory(self):
        lane, owner = self.unallocated_lane()
        lane.save.side_effect = OSError('synthetic receipt write failure')
        with patch.object(runner.tempfile, 'gettempdir', return_value=str(self.root)), \
                patch.object(runner, 'AppHostOwnership', return_value=owner), self.assertRaises(OSError):
            lane.allocate_temporary()
        self.assertIs(owner, lane.owner)
        self.assertIsNotNone(lane.allocation_identity)
        self.finish(lane)
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('PASS', lane.receipt['cleanup_status'])

    def test_owner_constructor_failure_retains_preworker_cleanup_identity(self):
        lane, _ = self.unallocated_lane()
        with patch.object(runner.tempfile, 'gettempdir', return_value=str(self.root)), \
                patch.object(runner, 'AppHostOwnership', side_effect=RuntimeError('synthetic owner failure')), \
                self.assertRaises(RuntimeError):
            lane.allocate_temporary()
        self.assertIsNone(lane.owner)
        self.assertIsNotNone(lane.allocation_identity)
        lane.save.assert_not_called()
        self.finish(lane)
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('PASS', lane.receipt['cleanup_status'])

    def test_owned_name_failure_retains_preowner_cleanup_identity(self):
        lane, _ = self.unallocated_lane()
        with patch.object(runner.tempfile, 'gettempdir', return_value=str(self.root)), \
                patch.object(runner, 'owned_simulator_name', side_effect=RuntimeError('synthetic name failure')), \
                patch.object(runner, 'AppHostOwnership') as construct, self.assertRaises(RuntimeError):
            lane.allocate_temporary()
        construct.assert_not_called()
        self.assertIsNone(lane.owner)
        self.assertEqual(list(lane.allocation_identity), lane.receipt['temporary_allocation_identity'])
        self.finish(lane)
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('PASS', lane.receipt['cleanup_status'])

    def test_mkdtemp_failure_has_no_allocation_or_cleanup_target(self):
        lane, _ = self.unallocated_lane()
        with patch.object(runner.tempfile, 'gettempdir', return_value=str(self.root)), \
                patch.object(runner.tempfile, 'mkdtemp', side_effect=OSError('synthetic allocation failure')), \
                patch.object(runner, 'AppHostOwnership') as construct, self.assertRaises(OSError):
            lane.allocate_temporary()
        construct.assert_not_called()
        self.assertIsNone(lane.temporary)
        self.assertIsNone(lane.allocation_identity)
        self.finish(lane)
        self.assertEqual('PASS', lane.receipt['cleanup_status'])
        self.assertTrue(lane.receipt['temporary_directory_removed'])

    def test_existing_allocation_cannot_be_replaced(self):
        lane = self.lane()
        original, identity = lane.temporary, lane.allocation_identity
        with patch.object(runner.tempfile, 'mkdtemp') as allocate, self.assertRaises(RuntimeError):
            lane.allocate_temporary()
        allocate.assert_not_called()
        self.assertEqual(original, lane.temporary)
        self.assertEqual(identity, lane.allocation_identity)
        self.finish(lane)
        self.assertFalse(original.exists())

    def test_fixed_test_arguments_have_no_retry_or_parallel_escape(self):
        args = runner.xcode_arguments(self.root / source_copy.PROJECT, 'iphonesimulator26.5', DEVICE,
                                       self.root, runner.signing_overrides('disabled'))
        self.assertEqual('8', args[args.index('-test-iterations') + 1])
        self.assertEqual('YES', args[args.index('-test-repetition-relaunch-enabled') + 1])
        self.assertEqual(['-only-testing:' + receipts.SELECTOR], [arg for arg in args if arg.startswith('-only-testing:')])
        self.assertEqual('NO', args[args.index('-parallel-testing-enabled') + 1])
        self.assertEqual('1', args[args.index('-jobs') + 1])
        self.assertFalse(any('retry' in arg or 'until-failure' in arg or 'ProvisioningUpdates' in arg for arg in args))

    def test_environment_drops_probes_loader_credentials_and_foreign_gradle_options(self):
        inherited = dict(HOME='/owned/home', USER='fixture', LOGNAME='fixture',
                         DYLD_INSERT_LIBRARIES='/forbidden', SIMCTL_CHILD_SECRET='secret',
                         PARLOR_PROBE='1', EXPANDED_CODE_SIGN_IDENTITY='foreign',
                         GRADLE_OPTS='unsafe', GOOGLE_CREDENTIALS='secret')
        env = runner.isolated_environment(inherited, '/public/jdk/Contents/Home', self.root, self.root / 'sdk', 'disabled')
        self.assertEqual('fixture', env['USER'])
        self.assertFalse(set(inherited) - {'HOME', 'USER', 'LOGNAME', 'GRADLE_OPTS'} & set(env))
        options = shlex.split(env['GRADLE_OPTS'])
        self.assertIn('-Dorg.gradle.workers.max=1', options)
        self.assertTrue(any('-Djava.io.tmpdir=' + str(self.root / 'tmp') in option for option in options))
        for field in ('storeFile', 'storePassword', 'keyAlias', 'keyPassword'):
            self.assertIn('-Dorg.gradle.project.parlor.android.signing.' + field + '=', options)

    def test_adhoc_is_explicit_and_does_not_inherit_a_real_identity(self):
        env = runner.isolated_environment({}, '/public/jdk/Contents/Home', self.root, self.root / 'sdk', 'adhoc')
        self.assertEqual('-', env['CODE_SIGN_IDENTITY'])
        self.assertEqual('', env['DEVELOPMENT_TEAM'])
        self.assertNotIn('EXPANDED_CODE_SIGN_IDENTITY', env)

    def test_failed_xcode_attempt_immediately_stops_gradle(self):
        lane = self.lane()
        lane.save = Mock()
        lane.command = Mock(side_effect=RuntimeError('synthetic build failure'))
        with self.assertRaises(RuntimeError):
            lane.run_xctest()
        lane.stop_gradle.assert_called_once_with('stop-xcode-immediate')

    def test_interrupted_xcode_attempt_immediately_stops_gradle(self):
        lane = self.lane()
        lane.save = Mock()
        lane.command = Mock(side_effect=KeyboardInterrupt())
        with self.assertRaises(KeyboardInterrupt):
            lane.run_xctest()
        lane.stop_gradle.assert_called_once_with('stop-xcode-immediate')

    def test_timed_out_xcode_attempt_immediately_stops_gradle(self):
        lane = self.lane()
        lane.save = Mock()
        lane.command = Mock(side_effect=subprocess.TimeoutExpired('fixture', 1))
        with self.assertRaises(subprocess.TimeoutExpired):
            lane.run_xctest()
        lane.stop_gradle.assert_called_once_with('stop-xcode-immediate')

    def test_finalization_removes_only_exact_task_allocation(self):
        lane = self.lane()
        sibling = self.root / 'unrelated-build'
        sibling.mkdir()
        (sibling / 'keep').write_text('user work')
        self.finish(lane)
        lane.stop_gradle.assert_called_once_with('stop-final')
        lane.shutdown_device.assert_called_once()
        lane.delete_device.assert_called_once()
        lane.owner.stop.assert_called_once()
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('user work', (sibling / 'keep').read_text())
        self.assertEqual('PASS', lane.receipt['cleanup_status'])

    def test_failed_gradle_stop_does_not_skip_device_workers_and_file_cleanup(self):
        lane = self.lane()
        lane.stop_gradle.side_effect = RuntimeError('synthetic stop failure')
        self.finish(lane)
        lane.shutdown_device.assert_called_once()
        lane.owner.stop.assert_called_once()
        self.assertFalse(lane.temporary.exists())
        self.assertEqual('FAIL', lane.receipt['cleanup_status'])
        self.assertEqual('FAIL', lane.receipt['status'])

    def test_unknown_holders_preserve_allocation_and_fail_cleanup(self):
        lane = self.lane()
        lane.owner.unknown_holders = [{'pid': 1}]
        self.finish(lane)
        self.assertTrue(lane.temporary.exists())
        self.assertEqual('FAIL', lane.receipt['cleanup_status'])

    def test_secondary_path_attestation_failure_is_not_cleanup_success(self):
        lane = self.lane()
        lane.owner.secondary_errors = {'synthetic': {'type': 'unattested'}}
        self.finish(lane)
        lane.owner.stop.assert_called_once()
        self.assertEqual('FAIL', lane.receipt['cleanup_status'])

    def test_cache_link_cleanup_does_not_remove_dependency_cache(self):
        lane = self.lane()
        cache = self.root / 'shared-cache'
        cache.mkdir()
        (cache / 'keep').write_text('dependency')
        link = lane.temporary / 'cache-link'
        link.symlink_to(cache, target_is_directory=True)
        lane.links = [dict(link=str(link), target=str(cache))]
        self.finish(lane)
        self.assertEqual('dependency', (cache / 'keep').read_text())
        self.assertFalse(link.exists())

    def test_changed_cache_link_preserves_allocation(self):
        lane = self.lane()
        original, replacement = self.root / 'cache-one', self.root / 'cache-two'
        original.mkdir()
        replacement.mkdir()
        link = lane.temporary / 'cache-link'
        link.symlink_to(replacement, target_is_directory=True)
        lane.links = [dict(link=str(link), target=str(original))]
        self.finish(lane)
        self.assertTrue(lane.temporary.exists())
        self.assertTrue(replacement.exists())
        self.assertEqual('FAIL', lane.receipt['cleanup_status'])

    def test_replaced_allocation_inode_refuses_removal(self):
        lane = self.lane()
        lane.allocation_identity = (-1, -1, -1)
        with self.assertRaises(RuntimeError):
            lane.remove_temporary()
        self.assertTrue(lane.temporary.exists())

    def test_source_drift_invalidates_overall_result(self):
        lane = self.lane()
        with patch.object(runner, 'normalized_identity', return_value={'changed': True}), \
                patch.object(runner, 'control_hash', return_value=lane.approved):
            lane.finalize()
        self.assertEqual('PASS', lane.receipt['cleanup_status'])
        self.assertEqual('FAIL', lane.receipt['status'])

    def test_control_drift_invalidates_overall_result(self):
        lane = self.lane()
        with patch.object(runner, 'normalized_identity', return_value={'frozen': True}), \
                patch.object(runner, 'control_hash', return_value='b' * 64):
            lane.finalize()
        self.assertEqual('FAIL', lane.receipt['status'])

    def test_preexisting_original_output_is_preserved_not_deleted(self):
        lane = self.lane()
        # finalize only records original outputs; prepare rejects them before a
        # build. A fake path beneath ROOT allows the production relative receipt.
        path = runner.ROOT / 'build'
        lane.original_outputs = [path]
        original_exists = Path.exists
        def exists(value):
            return True if value == path else original_exists(value)
        with patch.object(Path, 'exists', exists):
            self.finish(lane)
        self.assertEqual(['build'], lane.receipt['original_outputs_preserved'])
        self.assertEqual('FAIL', lane.receipt['status'])

    def test_oversized_watch_file_is_rejected(self):
        lane = self.lane()
        path = lane.temporary / 'owned-output'
        path.write_bytes(b'12345')
        lane.watched_outputs = [(path, 4)]
        with self.assertRaises(RuntimeError):
            lane.check_watched_outputs()

    def test_failed_observation_releases_watch_before_final_shutdown(self):
        lane = self.lane()
        lane.receipt['runtime_evidence_status'] = 'NOT_RUN'
        lane.receipt['provenance_status'] = 'NOT_RUN'
        lane.prepare = Mock(side_effect=RuntimeError('synthetic observation failure'))
        lane.watched_outputs = [(lane.temporary / 'full', 0)]
        lane.finalize = Mock(side_effect=lambda: self.assertEqual([], lane.watched_outputs))
        lane.save = Mock()
        lane.name = 'fixture'
        # This asserts finalization reachability only. The stub cannot produce a
        # valid runtime PASS; the receipt remains failed through run()'s error.
        lane.receipt['status'] = 'FAIL'
        with patch('builtins.print'):
            self.assertEqual(1, lane.run())
        lane.finalize.assert_called_once()


if __name__ == '__main__':
    unittest.main(verbosity=2)
