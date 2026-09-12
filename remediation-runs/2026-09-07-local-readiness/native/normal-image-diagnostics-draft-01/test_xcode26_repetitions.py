"""Actual Xcode 26.5 receipt shape plus synthetic rejection variants.

The sanitized fixture comes from ios-readiness-06: one method, eight destination
runs, and eight leaf Repetition nodes in BOTH XCResult queries. Parsing that
receipt does not run the app or complete the interrupted ninth-launch gate.
"""
import copy
from pathlib import Path
import unittest

import normal_launch_receipts as receipts

DEVICE = '11111111-2222-3333-4444-555555555555'


def fixture():
    data = receipts.read_json(Path(__file__).with_name('xcresult-26.5-repetitions.json.in').read_text())
    return data['summary'], data['tests'], data['details']


def case(tests):
    return next(node for node in receipts.all_nodes(tests['testNodes']) if node['nodeType'] == 'Test Case')


class ObservedXcodeRepetitionTests(unittest.TestCase):
    def test_observed_one_method_eight_destination_leaf_repetitions_pass(self):
        result = receipts.verify_xctest(*fixture(), DEVICE)
        self.assertEqual(1, result['reported_summary_total'])
        self.assertEqual(8, result['executed_repetitions'])
        self.assertEqual(8, len(result['actual_run_identities']))
        self.assertEqual(8, result['reported_destination_passes'])
        self.assertEqual('identified-leaf-repetitions', result['repetition_layout'])

    def test_leaf_layout_requires_the_observed_single_method_summary(self):
        summary, tests, details = fixture()
        summary.update(totalTestCount=8, passedTests=8)
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_direct_repetition_counts_must_match_both_queries(self):
        for count in (7, 9):
            summary, tests, details = fixture()
            nodes = details['testRuns']
            if count == 7:
                nodes.pop()
            else:
                nodes.append(dict(nodes[-1], name='Repetition 9', nodeIdentifier='9'))
            case(tests)['children'] = copy.deepcopy(nodes)
            with self.subTest(count=count), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_duplicate_repetition_ids_fail_despite_distinct_names(self):
        summary, tests, details = fixture()
        details['testRuns'][1]['nodeIdentifier'] = '1'
        case(tests)['children'] = copy.deepcopy(details['testRuns'])
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_reordered_repetitions_fail(self):
        summary, tests, details = fixture()
        details['testRuns'][0], details['testRuns'][1] = details['testRuns'][1], details['testRuns'][0]
        case(tests)['children'] = copy.deepcopy(details['testRuns'])
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_destination_execution_count_is_not_method_count(self):
        for count in (True, 0, 1, 7, 9):
            summary, tests, details = fixture()
            summary['devicesAndConfigurations'][0]['passedTests'] = count
            with self.subTest(count=count), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_leaf_repetition_identity_is_required(self):
        for field in ('nodeIdentifier', 'name'):
            summary, tests, details = fixture()
            details['testRuns'][0].pop(field)
            case(tests)['children'] = copy.deepcopy(details['testRuns'])
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_independent_query_duration_must_agree(self):
        summary, tests, details = fixture()
        case(tests)['children'][0]['durationInSeconds'] += 1
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_missing_tree_repetitions_cannot_be_replaced_with_details(self):
        summary, tests, details = fixture()
        case(tests).pop('children')
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_nonpassing_repetition_or_summary_is_never_accepted(self):
        for result in ('Failed', 'Skipped', 'Expected Failure', 'unknown'):
            summary, tests, details = fixture()
            details['testRuns'][3]['result'] = result
            with self.subTest(result=result), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_repetition_durations_are_bounded_and_not_boolean(self):
        for duration in (True, 0, 9.9, 241, float('nan'), float('inf')):
            summary, tests, details = fixture()
            details['testRuns'][0]['durationInSeconds'] = duration
            case(tests)['children'] = copy.deepcopy(details['testRuns'])
            with self.subTest(duration=duration), self.assertRaises(RuntimeError):
                receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_extra_repetition_outside_selected_case_is_rejected(self):
        summary, tests, details = fixture()
        tests['testNodes'].append(copy.deepcopy(details['testRuns'][0]))
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)

    def test_mixed_leaf_and_nested_run_layout_is_rejected(self):
        summary, tests, details = fixture()
        details['testRuns'][0]['children'] = [dict(nodeType='Test Case Run', name='Owned iPhone',
            result='Passed', durationInSeconds=15.0)]
        case(tests)['children'] = copy.deepcopy(details['testRuns'])
        with self.assertRaises(RuntimeError):
            receipts.verify_xctest(summary, tests, details, DEVICE)
