"""Selection/custody controls only; no replay of passed Apple adapter controls."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import pin59_keyboard_sessions as p
import run_pin59
import test_pin58_native_ax_returns as seed


def plan():
    leaf = p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE, p.SOURCE, p.RUNTIME, p.DEVICE)(
        {s: leaf.pair.fixture(s) for s in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')


def evidence(out, failed=()):
    state = seed.evidence(out, p.native.CASES[:len(failed)])
    d = out / 'compose'
    row = state['arms'][0]
    state['profile'] = p.PROFILE
    (d / 'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {name: hashlib.sha256(value.encode()).hexdigest() for name, value in plan().items()}))
    row['methods'] = {case: 'Failed' if case in failed else 'Passed' for case in p.CASES}
    row['attachment_test_ids'] = sorted(failed)
    summary = p.load(d / 'summary.json')
    summary.update(totalTestCount=12, passedTests=12-len(failed), failedTests=len(failed),
        testFailures=[dict(testIdentifierString=case, targetName='iosAppUITests',
            failureText='Retained original keyboard or public-control assertion') for case in sorted(failed)])
    tests = p.load(d / 'tests.json')
    tests['testNodes'] = [dict(nodeType='Test Suite', children=[dict(nodeType='Test Case',
        nodeIdentifier=case, result=value, durationInSeconds=100) for case, value in row['methods'].items()])]
    for name, value in (('summary', summary), ('tests', tests)):
        (d / (name + '.json')).write_text(json.dumps(value))
    call = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    argv = call['command']
    call['command'] = p.test_command(argv[:argv.index('test-without-building')], d / 'tests.xcresult')
    (d / 'test.log').write_text(seed.seed.starts(p.CASES))
    return state


class KeyboardSessionTests(unittest.TestCase):
    def test_exact_pin58_adapter_and_original_keyboard_launch_failure_ownership_only(self):
        new, old = plan(), seed.plan()
        driver = p.native.prior.pin02_capture.DRIVER
        self.assertEqual({name for name in new if new[name] != old[name]}, {driver})
        self.assertEqual(p.keyboard_launch_owner(new[driver], restore=True), old[driver])
        self.assertEqual(new[driver].count('PIN56Configure(app)'), old[driver].count('PIN56Configure(app)') + 1)
        with self.assertRaises(ValueError):
            p.keyboard_launch_owner(new[driver])
        with self.assertRaises(ValueError):
            p.keyboard_launch_owner(old[driver], restore=True)
        self.assertEqual(len(p.CASES), 12)
        self.assertEqual(p.CASES, tuple(sorted(p.CASES)))
        self.assertEqual(p.CASES[2:], p.native.CASES)
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, p.native.TEST_TIMEOUT_SECONDS)
        self.assertEqual(p.ARMS, ('compose',))
        p.require_capability()
        with self.assertRaises(ValueError):
            p.renderer_for_profile('wrong', p.SOURCE, p.RUNTIME, p.DEVICE)

    def test_exact_original_keyboard_methods_then_ten_samples_once_no_repair_or_retry(self):
        command = p.test_command(['/usr/bin/xcodebuild', '-quiet'], Path('/fixture/result.xcresult'))
        self.assertEqual([a for a in command if a.startswith('-only-testing:')],
            ['-only-testing:' + test for test in p.TESTS])
        self.assertEqual(command.count('test-without-building'), 1)
        self.assertNotIn('-quiet', command)
        self.assertFalse(any(a.startswith(('-skip-testing', '-retry-tests', '-test-iterations',
            '-run-tests-until-failure')) for a in command))
        self.assertEqual(run_pin59.run_pin03.completed_exit_code(p.RESULT), 1)

    def test_all_pass_and_failures_of_either_original_keyboard_or_setup_are_retained(self):
        for failed in ((), p.CASES[:1], p.CASES[:2], p.CASES[2:4], p.CASES[:10]):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, failed)
                self.assertEqual(p.accept_native_export(out, state), p.RESULT)
                receipt = p.load(out / 'input-intermittency.json')
                self.assertEqual(receipt['methods'], state['arms'][0]['methods'])
                self.assertFalse(receipt['application_root_cause'])
                self.assertFalse(receipt['shipping_qualified'])
                self.assertFalse(receipt['physical_verified'])

    def test_wrong_source_missing_counts_skips_duplicate_or_unexecuted_results_reject(self):
        changes = (
            lambda s, t: s.update(totalTestCount=10),
            lambda s, t: s.update(passedTests=True),
            lambda s, t: s.update(skippedTests=1),
            lambda s, t: s.update(expectedFailures=1),
            lambda s, t: t['testNodes'][0]['children'].pop(),
            lambda s, t: t['testNodes'][0]['children'].__setitem__(1,
                copy.deepcopy(t['testNodes'][0]['children'][0])),
            lambda s, t: t['testNodes'][0]['children'][0].update(result='Skipped'),
            lambda s, t: t['testNodes'][0]['children'][0].update(durationInSeconds=0),
            lambda s, t: t['testNodes'][0]['children'][0].update(durationInSeconds=float('nan')),
            lambda s, t: t['devices'][0].update(osVersion='18.5'),
        )
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = evidence(out)
            row = state['arms'][0]
            summary, tests = p.load(out / 'compose/summary.json'), p.load(out / 'compose/tests.json')
            for change in changes:
                with self.subTest(change=change):
                    s, t = copy.deepcopy(summary), copy.deepcopy(tests)
                    change(s, t)
                    with self.assertRaises(ValueError):
                        p.verify_results(s, t, row['simulator'], row['test_exit'])

    def test_custody_cleanup_retries_assertions_and_order_cannot_be_waived(self):
        changes = (
            lambda o, s: s.update(source='0'*40),
            lambda o, s: s['arms'][0].update(cleanup='FAIL'),
            lambda o, s: s['build_profile'].update(test_timeout_seconds=p.TEST_TIMEOUT_SECONDS+1),
            lambda o, s: next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure'),
            lambda o, s: (o / 'compose/test.log').write_text(seed.seed.starts(p.CASES[::-1])),
            lambda o, s: s['arms'][0].update(attachment_test_ids=[]),
        )
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, p.CASES[:1])
                change(out, state)
                with self.assertRaises(ValueError):
                    p.accept_native_export(out, state)

    def test_admission_restores_prior_selection_even_on_failure(self):
        keys = ('PROFILE', 'RESULT', 'CASES', 'TESTS', 'renderer_for_profile', 'verify_results', 'decode')
        saved = {key: getattr(p.native.prior, key) for key in keys}
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = evidence(out)
            state['arms'][0]['cleanup'] = 'FAIL'
            with self.assertRaises(ValueError):
                p.accept_native_export(out, state)
        for key, value in saved.items():
            self.assertIs(getattr(p.native.prior, key), value)


if __name__ == '__main__':
    unittest.main()
