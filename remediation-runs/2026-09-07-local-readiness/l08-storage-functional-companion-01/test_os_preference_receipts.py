"""Exact OS-representation parser fixtures, not execution of Settings/XCTest.

The action snippets below test only this additive sequence binding. The runner
must also execute the complete public-action and pane-identity validators.
"""
import copy
import json
import unittest

import os_preference_receipts as receipts
from test_native_readiness import TOKEN, event, preferences


def fixture(present=True):
    boot1, boot2, boot3 = [f'{value:08x}-1234-5678-9000-000000000001' for value in (1, 2, 3)]
    previous = ['ar-EG', 'en'] if present else []
    original, restored, explicit = preferences(), preferences('ar', previous), preferences('en', previous, owned=True)
    events = [event(1, boot1, 'before_main', original), event(2, boot1, preference=original),
              event(3, boot1, preference=original, os_status='opened'),
              event(4, boot2, 'before_main', restored, os_status='opened'),
              event(5, boot2, preference=restored, os_status='opened'),
              event(6, boot2, preference=restored, os_status='opened', settings=True),
              event(7, boot2, preference=explicit, os_status='opened', settings=True),
              event(8, boot2, preference=restored, os_status='opened', settings=True),
              event(9, boot3, 'before_main', restored, os_status='opened'),
              event(10, boot3, preference=restored, os_status='opened', settings=True),
              event(11, boot3, preference=restored, os_status='verified-arabic', settings=True),
              event(12, boot3, 'complete', restored, os_status='verified-arabic', settings=True)]
    # A distinct dictionary per event is important for mutation/counter-evidence tests.
    report = copy.deepcopy(dict(schemaVersion=6, runToken=TOKEN, scenario='os', completed=True, observations=events))
    stages = [dict(schemaVersion=1, stage=stage, beforeChoiceSequence=2, beforeChoiceBoot=boot1,
                   baselineSequence=5, baselineBoot=boot2, sequence=sequence, boot=boot,
                   hasAppOverride=present, appLanguages=previous[:])
              for stage, sequence, boot in zip(receipts.STAGES, (5, 7, 8, 10), (boot2, boot2, boot2, boot3))]
    return report, stages


def log(stages, action=True, repeated=False):
    lines = ['DSC01_OS_PREFERENCE_DECISION_CONTRACT ' + json.dumps(receipts.CONTRACT)]
    row = 'DSC01_OS_APP_INTERACTION ' + json.dumps(dict(stage='arabic', status='after-activation', fields=dict(control='arabic')))
    if action: lines.append(row)
    if repeated: lines.append(row)
    lines += ['DSC01_OS_PREFERENCE_BASELINE ' + json.dumps(value) for value in stages]
    lines.append('DSC01_OS_APP_INTERACTION ' + json.dumps(dict(stage='markerControls', status='before-activation', fields={})))
    return '\n'.join(lines)


class ExactOSPreferenceTests(unittest.TestCase):
    def test_present_arabic_preference_is_preserved_through_english_system_and_restart(self):
        report, stages = fixture()
        result = receipts.verify_os(report, log(stages))
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(result['observed_os_preference_representation'], 'PRESENT')
        self.assertTrue(result['actual_present_os_preference_coverage'])
        self.assertTrue(result['original_present_oracle_executed'])

    def test_observed_absence_is_never_reported_as_present_coverage(self):
        report, stages = fixture(present=False)
        result = receipts.verify_os(report, log(stages))
        self.assertEqual(result['observed_os_preference_representation'], 'ABSENT')
        self.assertFalse(result['actual_present_os_preference_coverage'])
        self.assertFalse(result['original_present_oracle_executed'])

    def test_forged_previous_array_or_missing_restarted_before_app_value_fails(self):
        for sequence, field, value in (
            (7, 'ownerPrevious', ['ar']), (7, 'ownerPreviousPresent', False),
            (8, 'appLanguages', ['ar']), (9, 'appLanguages', []),
            (5, 'preferredLanguage', 'en'), (4, 'ownerPresent', True),
        ):
            report, stages = fixture(); report['observations'][sequence - 1]['preferences'][field] = value
            with self.subTest(sequence=sequence, field=field), self.assertRaises(RuntimeError):
                receipts.verify_os(report, log(stages))

    def test_baseline_cannot_rebase_or_reorder_and_one_real_action_receipt_remains_required(self):
        for mutation in (
            lambda stages: stages[1].update(appLanguages=['ar']),
            lambda stages: stages[1].update(hasAppOverride=False),
            lambda stages: stages.reverse(),
            lambda stages: stages[1].update(sequence=True),
            lambda stages: stages[0].update(beforeChoiceBoot=stages[0]['boot']),
            lambda stages: stages[3].update(boot=stages[0]['boot']),
        ):
            report, stages = fixture(); mutation(stages)
            with self.assertRaises(RuntimeError): receipts.verify_os(report, log(stages))
        report, stages = fixture()
        with self.assertRaises(RuntimeError): receipts.verify_os(report, log(stages, action=False))
        with self.assertRaises(RuntimeError): receipts.verify_os(report, log(stages, repeated=True))

    def test_unavailable_os_selection_remains_blocked_and_may_not_invent_a_baseline(self):
        report, stages = fixture()
        for event_ in report['observations'][-2:]: event_['osDisposition'] = 'selection-unavailable'
        result = receipts.verify_os(report, log([]))
        self.assertEqual(result['status'], 'BLOCKED')
        with self.assertRaises(RuntimeError): receipts.verify_os(report, log(stages))


if __name__ == '__main__':
    unittest.main()
