"""Portable fixed-cohort/custody fixtures; never native or application evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin57_input_intermittency as p
import run_pin57
import test_pin56_delivery_boundary as previous


def plan():
    leaf = p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE, p.SOURCE, p.RUNTIME, p.DEVICE)(
        {s: leaf.pair.fixture(s) for s in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')


def starts(cases):
    return ''.join("Test Case '-[iosAppUITests." + c.split('/')[0] + ' ' +
        c.split('/')[1].removesuffix('()') + "]' started.\n" for c in cases)


def evidence(out, failed=(), failure_text='Original full public value failed'):
    """Replace the seed control cohort; captures belong only to fixture failures."""
    state = previous.evidence(out)
    d = out/'compose'
    row = state['arms'][0]
    prototype = p.load(next(d.glob('native-text-*.json')))
    legacy = p.load(next(d.glob('capture-*.json')))
    state['profile'] = p.PROFILE
    methods = {c: 'Failed' if c in failed else 'Passed' for c in p.CASES}
    row.update(methods=methods, test_exit=65 if failed else 0,
        result='JOURNEYS_FAILED' if failed else 'JOURNEYS_PASSED',
        attachment_test_ids=sorted(failed), captures=[], companion_captures=[])
    summary = p.load(d/'summary.json')
    summary.update(totalTestCount=10, passedTests=10-len(failed), failedTests=len(failed),
        skippedTests=0, expectedFailures=0, result='Failed' if failed else 'Passed',
        testFailures=[dict(testIdentifierString=c, targetName='iosAppUITests', failureText=failure_text)
            for c in sorted(failed)])
    tests = p.load(d/'tests.json')
    tests['testNodes'] = [dict(nodeType='Test Suite', children=[
        dict(nodeType='Test Case', nodeIdentifier=c, result=v, durationInSeconds=100)
        for c, v in methods.items()])]
    for name, value in (('summary', summary), ('tests', tests)):
        (d/(name+'.json')).write_text(json.dumps(value))
    (d/'inputs.json').write_text(json.dumps(state['original_inputs'] |
        {s: hashlib.sha256(v.encode()).hexdigest() for s, v in plan().items()}))
    call = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    command = call['command']
    call.update(command=p.test_command(command[:command.index('test-without-building')], d/'tests.xcresult'),
        exit_code=row['test_exit'], timeout_seconds=p.TEST_TIMEOUT_SECONDS)
    state['build_profile']['test_timeout_seconds'] = p.TEST_TIMEOUT_SECONDS
    (d/'test.log').write_text(starts(p.CASES))
    for prefix in ('native-text-', 'capture-'):
        for path in d.glob(prefix+'*.json'):
            path.unlink()
    shutil.rmtree(d/'attachments')
    (d/'attachments').mkdir()
    for i, case in enumerate(sorted(failed), 1):
        token = f'00000000-0000-0000-0000-{i:012d}'
        raw, env = copy.deepcopy(prototype), copy.deepcopy(legacy)
        raw['token'] = env['token'] = token
        raw['focus'].update(editorsAlive=[False], rows=[])
        raw['deliveryState'].update(editorsAlive=[False], threads=0, rows=[],
            current=[dict(available=False, firstResponder=None, ancestors=[])])
        request = d/'attachments'/('failed-%02d'%i)/'request.json'
        request.parent.mkdir()
        request.write_text(json.dumps(dict(schema=1, token=token, ackObserved=True,
            originalTransitionFailed=True)))
        encoded = json.dumps(env).encode()
        (d/('capture-'+token+'.json')).write_bytes(encoded)
        row['captures'].append(p.run_pin03.decode_capture(encoded, {token}, row['simulator'], state['debug_bundle']))
        encoded = json.dumps(raw).encode()
        (d/('native-text-'+token+'.json')).write_bytes(encoded)
        row['companion_captures'].append(p.decode(encoded, token=token, simulator=row['simulator'],
            bundle=state['debug_bundle'], legacy=env, require_control=False))
    return state


class InputIntermittencyTests(unittest.TestCase):
    def test_plan_is_exact_pin56_adapter_plus_ten_restorable_wrappers(self):
        old, current = previous.plan(), plan()
        self.assertEqual(set(current), set(old))
        driver = p.pin02_capture.DRIVER
        self.assertEqual({s for s in old if current[s] != old[s]}, {driver})
        self.assertEqual(p.stress.restore_driver(current[driver]), old[driver])
        self.assertEqual(current[driver].count('func testPIN30Sample'), 10)
        self.assertEqual(current[driver].count('name.typeText("Table Test")'), old[driver].count('name.typeText("Table Test")'))
        self.assertEqual(p.ARMS, ('compose',))
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, 5 * p.p.TEST_TIMEOUT_SECONDS)
        self.assertEqual(p.CASES, tuple('IOSAppLaunchUITests/testPIN30Sample%02d%s()' % (i, language)
            for i in range(1, 6) for language in ('Arabic', 'English')))
        with self.assertRaises(ValueError):
            p.stress.extend_driver(current[driver])
        with self.assertRaises(ValueError):
            p.renderer_for_profile('wrong-profile', p.SOURCE, p.RUNTIME, p.DEVICE)

    def test_command_selects_all_ten_once_without_control_retry_or_skip(self):
        command = p.test_command(['/usr/bin/xcodebuild', '-quiet'], Path('/fixture/result.xcresult'))
        self.assertEqual([a for a in command if a.startswith('-only-testing:')],
            ['-only-testing:'+test for test in p.TESTS])
        self.assertEqual(command.count('test-without-building'), 1)
        self.assertNotIn('-quiet', command)
        self.assertFalse(any(p.p.CONTROL_CASE.removesuffix('()') in a for a in command))
        self.assertFalse(any(a.startswith(('-skip-testing', '-retry-tests', '-test-iterations',
            '-run-tests-until-failure')) for a in command))

    def test_exact_results_accept_all_pass_and_preserve_natural_input_or_public_control_failures(self):
        for failed, text in (((), ''), (p.CASES[:1], 'Original full public value failed'),
                             (p.CASES[1:3], 'Original public control failed'),
                             (p.CASES, 'Original full public value failed')):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, failed, text)
                row = state['arms'][0]
                summary, tests = p.load(out/'compose/summary.json'), p.load(out/'compose/tests.json')
                self.assertEqual(p.verify_results(summary, tests, row['simulator'], row['test_exit']), row['methods'])
                self.assertEqual([v['failureText'] for v in summary['testFailures']], [text]*len(failed))

    def test_result_counts_skips_duplicates_missing_cases_and_nonexecuted_outcomes_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = evidence(out)
            summary, tests = p.load(out/'compose/summary.json'), p.load(out/'compose/tests.json')
            changes = (
                lambda s, t: s.update(totalTestCount=9),
                lambda s, t: s.update(passedTests=True),
                lambda s, t: s.update(skippedTests=1),
                lambda s, t: s.update(expectedFailures=1),
                lambda s, t: t['testNodes'][0]['children'].pop(),
                lambda s, t: t['testNodes'][0]['children'].__setitem__(1, copy.deepcopy(t['testNodes'][0]['children'][0])),
                lambda s, t: t['testNodes'][0]['children'][0].update(result='Skipped'),
                lambda s, t: t['testNodes'][0]['children'][0].update(durationInSeconds=0),
                lambda s, t: t['devices'][0].update(architecture='x86_64'),
            )
            for change in changes:
                s, t = copy.deepcopy(summary), copy.deepcopy(tests)
                change(s, t)
                with self.subTest(change=change), self.assertRaises(ValueError):
                    p.verify_results(s, t, state['arms'][0]['simulator'], 0)
            for code in (65, True, 1):
                with self.subTest(code=code), self.assertRaises(ValueError):
                    p.verify_results(summary, tests, state['arms'][0]['simulator'], code)

    def test_each_failed_sample_requires_matching_owned_failure_details(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = evidence(out, p.CASES[:2])
            summary, tests = p.load(out/'compose/summary.json'), p.load(out/'compose/tests.json')
            changes = (lambda s: s['testFailures'].pop(),
                       lambda s: s['testFailures'][0].update(targetName='otherTests'),
                       lambda s: s['testFailures'][0].update(failureText=''),
                       lambda s: s['testFailures'][1].update(testIdentifierString=p.CASES[0]))
            for change in changes:
                altered = copy.deepcopy(summary)
                change(altered)
                with self.subTest(change=change), self.assertRaises(ValueError):
                    p.verify_results(altered, tests, state['arms'][0]['simulator'], 65)

    def test_exports_only_failed_wrappers_and_never_successful_or_native_control_cases(self):
        outcomes = {c: 'Failed' if c in p.CASES[1:3] else 'Passed' for c in reversed(p.CASES)}
        exports = p.failed_method_exports(Path('/fixture/result'), Path('/fixture/attachments'), outcomes)
        self.assertEqual([command[-1] for _, command in exports], sorted(p.CASES[1:3]))
        self.assertEqual([directory.name for directory, _ in exports], ['failed-01', 'failed-02'])
        self.assertEqual(p.failed_method_exports(Path('/fixture/result'), Path('/fixture/attachments'),
            dict.fromkeys(p.CASES, 'Passed')), [])
        for bad in (dict.fromkeys(p.CASES[:-1], 'Passed'), outcomes | {p.p.CONTROL_CASE: 'Failed'},
                    outcomes | {p.CASES[0]: 'Skipped'}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                p.failed_method_exports(Path('/fixture/result'), Path('/fixture/attachments'), bad)

    def test_fixture_admission_preserves_all_results_and_failure_only_capture_custody(self):
        for failed in ((), p.CASES[:1], p.CASES[1:3], p.CASES):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, failed)
                with patch.object(p, 'require_capability'):
                    self.assertEqual(p.accept_native_export(out, state), p.RESULT)
                    self.assertEqual(p.accept_native_export(out, state), p.RESULT)
                receipt = p.load(out/'input-intermittency.json')
                self.assertEqual(receipt['methods'], state['arms'][0]['methods'])
                self.assertEqual(set(receipt['failed_sample_tokens'].values()), set(failed))
                self.assertEqual(len(receipt['native']), len(failed))
                self.assertFalse(receipt['root_queries_added'])
                for key in ('application_root_cause', 'shipping_qualified', 'physical_verified', 'native_timing_neutrality'):
                    self.assertFalse(receipt[key])

    def test_retained_start_log_requires_every_wrapper_once_in_declared_order(self):
        for cases in (p.CASES[::-1], p.CASES[:-1], p.CASES+p.CASES[:1],
                      p.CASES[:1]+p.CASES[:1]+p.CASES[2:]):
            with self.subTest(cases=cases), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out)
                (out/'compose/test.log').write_text(starts(cases))
                with patch.object(p, 'require_capability'), self.assertRaises(ValueError):
                    p.accept_native_export(out, state)

    def test_private_or_success_path_capture_and_missing_failed_capture_reject(self):
        changes = (
            lambda o, s: next((o/'compose').glob('native-text-*.json')).unlink(),
            lambda o, s: (o/'compose/native-root-invented.json').write_text('{}'),
            lambda o, s: (o/'compose/capture-unowned.json').write_text('{}'),
            lambda o, s: (o/'compose').joinpath(next((o/'compose').glob('native-text-*.json')).name).write_text(
                json.dumps(p.load(next((o/'compose').glob('native-text-*.json'))) | {'privateText': 'fixture-only'})),
        )
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, p.CASES[:1])
                change(out, state)
                with patch.object(p, 'require_capability'), self.assertRaises((ValueError, FileNotFoundError)):
                    p.accept_native_export(out, state)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = evidence(out)
            (out/'compose/capture-invented.json').write_text('{}')
            with patch.object(p, 'require_capability'), self.assertRaises(ValueError):
                p.accept_native_export(out, state)

    def test_cleanup_command_custody_retries_and_budget_mutations_reject(self):
        changes = (lambda o, s: s['arms'][0].update(cleanup='FAIL'),
                   lambda o, s: s['commands'].append(copy.deepcopy(s['commands'][0])),
                   lambda o, s: s['commands'][0].update(direct_child_reaped=False),
                   lambda o, s: s['build_profile'].update(test_timeout_seconds=p.TEST_TIMEOUT_SECONDS+1),
                   lambda o, s: next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append('-retry-tests-on-failure'),
                   lambda o, s: (o/'compose/cleanup.json').write_text(json.dumps(p.load(o/'compose/cleanup.json') | {'gradle_exit': 1})))
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out)
                change(out, state)
                with patch.object(p, 'require_capability'), self.assertRaises(ValueError):
                    p.accept_native_export(out, state)

    def test_dispatcher_restores_prior_globals_on_success_runner_failure_or_rejected_admission(self):
        profiles, select = run_pin57.prior.PROFILES, run_pin57.prior.select_profile
        for mode in ('success', 'runner-error', 'wrong-exit', 'wrong-result', 'admission-error'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = {'result': p.RESULT if mode != 'wrong-result' else 'REJECTED'}
                (out/'state.json').write_text(json.dumps(state))
                def runner(**kwargs):
                    self.assertEqual(kwargs, {'failure_runloop': True})
                    self.assertEqual(run_pin57.prior.PROFILES, (*profiles, p.PROFILE))
                    self.assertIs(run_pin57.prior.select_profile(p.PROFILE), p)
                    if mode == 'runner-error':
                        raise RuntimeError('fixture runner failure')
                    return 0 if mode == 'wrong-exit' else 1
                with patch.object(run_pin57.prior, 'require_capability_binding'), \
                     patch.object(p, 'require_capability'), \
                     patch.object(run_pin57.sys, 'argv', ['run', '--profile', p.PROFILE, '--output', str(out)]), \
                     patch.object(run_pin57.run_pin03, 'main', side_effect=runner), \
                     patch.object(p, 'accept_native_export', side_effect=ValueError('fixture admission failure')
                         if mode == 'admission-error' else None, return_value=p.RESULT) as admission:
                    if mode == 'success':
                        self.assertEqual(run_pin57.main(), 0)
                        admission.assert_called_once_with(out, state)
                    else:
                        with self.assertRaises((RuntimeError, ValueError)):
                            run_pin57.main()
                self.assertEqual(run_pin57.prior.PROFILES, profiles)
                self.assertIs(run_pin57.prior.select_profile, select)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple Swift parser unavailable on Linux')
    def test_actual_apple_fixed_wrapper_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            source = out/'driver.swift'
            source.write_text(plan()[p.pin02_capture.DRIVER])
            parsed = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-frontend', '-parse', '-D', 'DEBUG',
                '-target', 'arm64-apple-ios26.2-simulator', str(source)], capture_output=True, text=True, timeout=120)
            self.assertEqual(parsed.returncode, 0, parsed.stdout+parsed.stderr)


if __name__ == '__main__':
    unittest.main()
