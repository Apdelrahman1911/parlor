import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import review_retained_host as review


def fixture():
    rows = []
    def add(second, message, micros, pid=review.BACKBOARD, category='TouchEvents', subsystem=''):
        rows.append(dict(processID=pid, threadID=1, category=category, subsystem=subsystem,
            timestamp=second + f'.{micros:06}Z', eventMessage=message))
    for name, (second, sender) in review.COHORTS.items():
        add(second, 'init: <BKDirectTouchState: 0xa; senderID: 0x' + sender, 10)
        if name == 'catalog':
            add(second, 'contact 2 presence: none', 30)
        else:
            add(second, '*** unknown digitizer senderID: 0x' + sender + ' -- initializing now', 11)
            add(second, 'init: <BKDirectTouchState: 0xb; senderID: 0x' + sender, 12)
            for i in range(7):
                add(second, "didn't see a previous touch down or range-in event for pathIndex:2; skipping event", 20+i)
            add(second, 'canceling paths contacts: [pathIndex: 2; touchIdentifier: 17; touching; '
                'contextID: 0x' + review.WINDOW, 30)
        stats = '\n    down: 1,\n    up: 1,\n    rangeIn: 1,\n    rangeOut: 1,'
        if name == 'host': stats += '\n    soft cancel: 1,'
        add(second, 'touchstats ' + sender + '-<main>' + stats, 50)
    for micros in (15, 40):
        add(review.COHORTS['host'][0], 'Sending UIEvent type: 0; subtype: 0; to window: <UIWindow: 0xabc>; '
            'contextId: 0x' + review.WINDOW, micros, review.APP, 'EventDispatch', 'com.apple.UIKit')
    return rows


class RetainedHostTests(unittest.TestCase):
    def reduce(self, rows):
        return review.reduce_rows(json.dumps(rows).encode(), review.SPEC)

    def test_matched_original_failure_and_success_preserve_cancellation_distinction(self):
        result = self.reduce(fixture())
        self.assertEqual(result['cohorts']['catalog']['stats']['soft_cancel'], 0)
        self.assertEqual(result['cohorts']['host']['stats']['soft_cancel'], 1)
        self.assertEqual(result['cohorts']['host']['initialized_states'], 2)
        self.assertTrue(result['simulator_hid_cancellation_confirmed'])
        self.assertFalse(result['normal_host_touch_end_established'])
        self.assertFalse(result['compose_callback_established'])
        self.assertFalse(result['internal_hid_race_cause_established'])

    def test_counterexample_missing_discard_cancel_release_or_second_initialization_rejects(self):
        original = fixture()
        for token in ('skipping event', 'canceling paths', 'presence: none', '0xb;', 'soft cancel'):
            rows = copy.deepcopy(original)
            index = next(i for i, r in enumerate(rows) if token in r['eventMessage'])
            rows[index]['eventMessage'] = 'unclassified'
            with self.subTest(token=token), self.assertRaises(ValueError): self.reduce(rows)

    def test_cleanup_up_count_is_not_misclassified_as_normal_release(self):
        rows = fixture()
        for row in rows:
            if 'soft cancel: 1' in row['eventMessage']:
                row['eventMessage'] = row['eventMessage'].replace('soft cancel: 1', 'soft cancel: 0')
        with self.assertRaises(ValueError): self.reduce(rows)

    def test_offline_sign_extension_and_non_native_pointer_rendering_are_rejected(self):
        for token, replacement in (('0x' + review.WINDOW, '0xFFFFFFFF' + review.WINDOW),
                                   ('<UIWindow: 0xabc>', '<UIWindow: ABC>')):
            rows = fixture()
            for row in rows:
                if row['processID'] == review.APP:
                    row['eventMessage'] = row['eventMessage'].replace(token, replacement)
            with self.subTest(token=token), self.assertRaises(ValueError): self.reduce(rows)

    def test_wrong_sender_window_process_or_duplicate_statistics_rejects(self):
        for modify in ('sender', 'window', 'pid', 'duplicate'):
            rows = fixture()
            if modify == 'sender':
                for row in rows: row['eventMessage'] = row['eventMessage'].replace('100C27DD00012', 'BAD')
            elif modify == 'window':
                for row in rows: row['eventMessage'] = row['eventMessage'].replace(review.WINDOW, 'BAD')
            elif modify == 'pid':
                for row in rows: row['processID'] = 1
            else: rows.append(copy.deepcopy(rows[-3]))
            with self.subTest(modify=modify), self.assertRaises(ValueError): self.reduce(rows)

    def test_unknown_private_rows_are_never_copied_into_numeric_receipt(self):
        rows = fixture() + [dict(processID=1, eventMessage='private input', timestamp='unread')]
        encoded = json.dumps(self.reduce(rows))
        for token in ('private', 'eventMessage', '0xa', review.WINDOW, 'senderID', 'BKDirectTouchState'):
            self.assertNotIn(token, encoded)

    def test_original_failure_metadata_is_pinned_and_never_qualified(self):
        self.assertEqual(review.SPEC['run'], 41135053853)
        self.assertEqual(review.SPEC['conclusion'], 'failure')
        self.assertEqual(review.SPEC['artifact'], 13018521901)
        self.assertNotIn('typeText', review.predicate(review.SPEC))

    def test_unbounded_wrong_type_and_missing_rows_reject(self):
        for raw in (b'{}', b'[]', b' ' * (2*1024**2 + 1), json.dumps([{}]*2001).encode()):
            with self.assertRaises(ValueError): review.reduce_rows(raw, review.SPEC)


if __name__ == '__main__':
    unittest.main()
