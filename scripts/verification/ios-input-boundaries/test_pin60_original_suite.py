"""Changed-selection controls; no replay of already verified Apple probes."""
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

import pin60_original_suite as p
import run_pin60
import test_pin59_keyboard_sessions as seed


def plan():
    leaf = p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE, p.SOURCE, p.RUNTIME, p.DEVICE)(
        {s: leaf.pair.fixture(s) for s in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')


def evidence(out, failed=()):
    state = seed.evidence(out, seed.p.CASES[:len(failed)])
    d = out / 'compose'
    row = state['arms'][0]
    state['profile'] = p.PROFILE
    (d / 'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {name: hashlib.sha256(value.encode()).hexdigest() for name, value in plan().items()}))
    row['methods'] = {case: 'Failed' if case in failed else 'Passed' for case in p.CASES}
    row['attachment_test_ids'] = sorted(failed)
    summary = p.load(d / 'summary.json')
    summary.update(totalTestCount=23, passedTests=23-len(failed), failedTests=len(failed),
        testFailures=[dict(testIdentifierString=case, targetName='iosAppUITests',
            failureText='Retained original assertion') for case in sorted(failed)])
    tests = p.load(d / 'tests.json')
    tests['testNodes'] = [dict(nodeType='Test Suite', children=[dict(nodeType='Test Case',
        nodeIdentifier=case, result=value, durationInSeconds=100) for case, value in row['methods'].items()])]
    for name, value in (('summary', summary), ('tests', tests)):
        (d / (name + '.json')).write_text(json.dumps(value))
    call = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    argv = call['command']
    call['command'] = p.test_command(argv[:argv.index('test-without-building')], d / 'tests.xcresult')
    (d / 'test.log').write_text(seed.seed.seed.starts(p.CASES))
    return state


class OriginalSuiteTests(unittest.TestCase):
    def test_full_frozen_original_source_roster_once_in_original_order(self):
        root = Path(__file__).resolve().parents[3]
        found = []
        for name in ('ComposeContainerViewControllerTests.swift', 'IOSAppLaunchUITests.swift'):
            source = subprocess.check_output(['git', 'show', p.SOURCE + ':iosApp/iosAppUITests/' + name], cwd=root).decode()
            cls = None
            for line in source.splitlines():
                match = re.match(r'final class (\w+): XCTestCase', line)
                if match:
                    cls = match[1]
                match = re.match(r'\s+func (test\w+)\(', line)
                if match:
                    self.assertIsNotNone(cls)
                    found.append(cls + '/' + match[1] + '()')
        self.assertEqual(tuple(sorted(found)), p.CASES)
        self.assertEqual(len(set(p.CASES)), 23)

    def test_only_wrapper_removal_no_adapter_or_original_action_changes(self):
        old, new = seed.plan(), plan()
        driver = p.prior.native.prior.pin02_capture.DRIVER
        self.assertEqual({name for name in old if old[name] != new[name]}, {driver})
        self.assertEqual(p.prior.native.prior.stress.extend_driver(new[driver]), old[driver])
        self.assertNotIn('func testPIN30Sample', new[driver])
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, p.prior.TEST_TIMEOUT_SECONDS)

    def test_selection_never_admits_retries_skips_or_completed_qualification(self):
        command = p.test_command(['/usr/bin/xcodebuild', '-quiet'], Path('/fixture/result'))
        self.assertEqual([a for a in command if a.startswith('-only-testing:')], ['-only-testing:' + t for t in p.TESTS])
        self.assertEqual(command.count('test-without-building'), 1)
        self.assertNotIn('-quiet', command)
        self.assertFalse(any(a.startswith(('-skip-testing', '-retry-tests', '-test-iterations', '-run-tests-until-failure')) for a in command))
        self.assertEqual(run_pin60.run_pin03.completed_exit_code(p.RESULT), 1)

    def test_source_plan_custody_and_all_original_failures_remain_required(self):
        for failed in ((), (p.CASES[4],), (p.CASES[4], p.CASES[7])):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, failed)
                self.assertEqual(p.accept_native_export(out, state), p.RESULT)
                receipt = p.load(out / 'input-intermittency.json')
                self.assertEqual(receipt['methods'], state['arms'][0]['methods'])
                self.assertFalse(receipt['application_root_cause'])
                self.assertFalse(receipt['shipping_qualified'])

    def test_wrong_counts_missing_methods_skips_runtime_order_and_cleanup_reject(self):
        changes = (
            lambda o,s: s.update(source='0'*40),
            lambda o,s: s['arms'][0].update(cleanup='FAIL'),
            lambda o,s: (o/'compose/test.log').write_text(seed.seed.seed.starts(p.CASES[::-1])),
            lambda o,s: next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure'),
            lambda o,s: (o/'compose/summary.json').write_text(json.dumps(p.load(o/'compose/summary.json') | {'skippedTests':1})),
            lambda o,s: (o/'compose/summary.json').write_text(json.dumps(p.load(o/'compose/summary.json') | {'totalTestCount':12})),
        )
        saved = {k:getattr(p.prior,k) for k in ('PROFILE','RESULT','CASES','TESTS','renderer_for_profile')}
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, (p.CASES[4],))
                change(out,state)
                with self.assertRaises(ValueError):
                    p.accept_native_export(out,state)
                for key,value in saved.items():
                    self.assertIs(getattr(p.prior,key),value)

    def test_duplicate_unexecuted_or_wrong_runtime_results_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            summary,tests=p.load(out/'compose/summary.json'),p.load(out/'compose/tests.json')
            for change in (
                lambda t:t['testNodes'][0]['children'].pop(),
                lambda t:t['testNodes'][0]['children'].__setitem__(1,copy.deepcopy(t['testNodes'][0]['children'][0])),
                lambda t:t['testNodes'][0]['children'][0].update(durationInSeconds=0),
                lambda t:t['devices'][0].update(osVersion='18.5'),
            ):
                mutated=copy.deepcopy(tests);change(mutated)
                with self.assertRaises(ValueError):p.verify_results(summary,mutated,state['arms'][0]['simulator'],0)


if __name__ == '__main__':
    unittest.main()
