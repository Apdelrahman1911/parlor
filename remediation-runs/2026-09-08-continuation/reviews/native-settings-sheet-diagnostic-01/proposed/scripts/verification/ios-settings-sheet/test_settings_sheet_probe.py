"""Small inert controls; synthetic labels never claim observed Apple UI/runtime."""
import copy
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('settings_sheet_probe_test_subject', HERE / 'settings_sheet_probe.py')
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)
UUID = '11111111-2222-3333-4444-555555555555'


def capture_fixture():
    device = dict(deviceId=UUID, architecture='arm64', platform='iOS Simulator')
    summary = dict(result='Passed', totalTestCount=1, passedTests=1, failedTests=0,
                   skippedTests=0, expectedFailures=0, devicesAndConfigurations=[dict(device=device)])
    tests = dict(devices=[device], testNodes=[dict(nodeType='Test Case', nodeIdentifier=probe.METHOD, result='Passed')])
    value = dict(schemaVersion=1, simulator=UUID, labels=['SYNTHETIC_A', 'SYNTHETIC_B', 'SYNTHETIC_C'], selectionTapped=False)
    return summary, tests, value


class SettingsSheetControlsTest(unittest.TestCase):
    def test_private_binding_includes_every_unchanged_normal_control(self):
        inherited, combined = probe._base_manifest(), probe.control_manifest()
        self.assertTrue(all(row in combined for row in inherited))
        self.assertGreater(len(combined), len(inherited))
        self.assertIs(probe.normal.control_manifest, probe.control_manifest)
        self.assertEqual(probe.normal.control_hash(), probe.control_hash())
        self.assertEqual(probe.normal.digest(probe.NORMAL), probe.NORMAL_SHA)
        self.assertNotIn(str(HERE / 'settings_sheet_probe.py'), {str(probe.ROOT / row['path']) for row in inherited})

    def test_project_and_scheme_build_only_existing_test_target_and_reject_drift(self):
        original = (probe.ROOT / probe.PROJECT).read_text()
        detached = probe.detach_project(original)
        targets = detached.split('\n\t\t\ttargets = (', 1)[1].split(');', 1)[0]
        self.assertNotIn('/* iosApp */', targets)
        self.assertEqual(targets.count('/* iosAppUITests */'), 1)
        self.assertNotIn('TEST_TARGET_NAME =', detached)
        self.assertNotIn('TestTargetID =', detached)
        for changed in (original.replace('TestTargetID = ', 'UnexpectedTargetID = '),
                        original.replace('TestTargetID = ', 'TestTargetID = 7555FF76242A565900829871; TestTargetID = ')):
            with self.assertRaises(RuntimeError): probe.detach_project(changed)
        scheme = ET.parse(HERE / 'SettingsSheet.xcscheme.in').getroot()
        self.assertEqual(scheme.find('BuildAction').get('buildImplicitDependencies'), 'NO')
        refs = scheme.findall('.//BuildableReference')
        self.assertEqual(len(refs), 2)
        self.assertTrue(all(ref.get('BlueprintIdentifier') == 'B14500012C0000010000000A' for ref in refs))
        self.assertIsNone(scheme.find('LaunchAction'))
        swift = (HERE / 'SettingsSheetUITest.swift.in').read_text()
        tail = swift.split('// Observe only the unique sheet', 1)[1]
        self.assertNotIn('.tap(', tail)
        self.assertNotIn('debugDescription', swift)
        self.assertNotIn('screenshot', swift)
        self.assertIn('guard disposition == .requiresArabic', swift)
        self.assertEqual(swift.count('__PARLOR_EXPECTED_SIMULATOR_UUID__'), 1)
        self.assertEqual(swift.count('__PARLOR_EXPECTED_SIMULATOR_NAME__'), 1)

    def test_exact_capture_rejects_forgery_excess_labels_and_wrong_actual_test(self):
        summary, tests, value = capture_fixture()
        log = lambda row: 'SETTINGS_SHEET_CAPTURE ' + json.dumps(row) + '\n'
        self.assertEqual(probe.validate_capture(summary, tests, log(value), UUID), value)
        for field, replacement in (('schemaVersion', True), ('simulator', 'unowned'), ('selectionTapped', True),
            ('labels', ['A'] * 4), ('labels', ['A'] * 2), ('labels', ['', 'B', 'C']),
            ('labels', ['x' * 129, 'B', 'C']), ('labels', ['A\nB', 'B', 'C'])):
            changed = {**value, field: replacement}
            with self.subTest(field=field, replacement=replacement), self.assertRaises(RuntimeError):
                probe.validate_capture(summary, tests, log(changed), UUID)
        for bad_log in (log(value) * 2, '', log(value).replace('"schemaVersion": 1', '"schemaVersion": 1, "schemaVersion": 1')):
            with self.assertRaises(RuntimeError): probe.validate_capture(summary, tests, bad_log, UUID)
        for mutation in ('skip', 'wrong-method', 'second-device', 'boolean-count'):
            s, t = copy.deepcopy(summary), copy.deepcopy(tests)
            if mutation == 'skip': s['skippedTests'] = 1
            elif mutation == 'wrong-method': t['testNodes'][0]['nodeIdentifier'] = 'Other/test()'
            elif mutation == 'second-device': t['devices'] *= 2
            else: s['passedTests'] = True
            with self.subTest(mutation=mutation), self.assertRaises(RuntimeError):
                probe.validate_capture(s, t, log(value), UUID)

    def test_missing_results_are_retention_only_and_failed_ack_is_not_cleanup_permission(self):
        with TemporaryDirectory() as raw:
            lane = object.__new__(probe.SheetLane)
            lane.temporary = Path(raw); lane.destination = Path(raw) / 'evidence'; lane.destination.mkdir()
            (lane.destination / 'xcodebuild.log').write_text('synthetic compiler failure\n')
            lane.receipt = dict(diagnostic_status='NOT_RUN', postbuild_evidence_preserved=False)
            lane.command, lane.save = Mock(), Mock()
            state = lane.preserve()
            self.assertFalse(state['xcresult_present'])
            self.assertTrue(lane.receipt['postbuild_evidence_preserved'])
            self.assertEqual(lane.receipt['diagnostic_status'], 'NOT_RUN')
            lane.command.assert_not_called()
            lane.save.side_effect = OSError('synthetic ack failure')
            with self.assertRaises(OSError): lane.preserve()
            self.assertFalse(lane.receipt['postbuild_evidence_preserved'])

    def test_failed_swift_build_marks_real_barrier_stops_and_preserves_before_reraising(self):
        lane = object.__new__(probe.SheetLane)
        lane.temporary = Path('/synthetic-owned-no-file-access'); lane.environment = dict(SDK_NAME='iphonesimulator26.2')
        lane.uuid, lane.signing, lane.errors = UUID, {}, []
        lane.receipt = dict(runtime_evidence_status='NOT_RUN', provenance_status='NOT_RUN', notice_package_status='NOT_RUN')
        lane.save, lane.stop_gradle, lane.preserve = Mock(), Mock(), Mock(return_value={})
        lane.lifecycle = SimpleNamespace(mark_build_attempted=Mock())
        lane.command = Mock(side_effect=TimeoutError('synthetic owned build timeout'))
        with self.assertRaises(TimeoutError): lane.run_xctest()
        self.assertTrue(lane.gradle_attempted and lane.receipt['build_attempted'])
        lane.lifecycle.mark_build_attempted.assert_called_once_with()
        lane.stop_gradle.assert_called_once_with('stop-xcode-immediate')
        lane.preserve.assert_called_once_with()
        self.assertEqual(lane.receipt['runtime_evidence_status'], 'NOT_RUN')


if __name__ == '__main__':
    unittest.main()
