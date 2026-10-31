"""Full-cohort rejection fixtures, not actual native execution."""
import hashlib
import importlib
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest

import current_boundary_adapter as current
import pin02_capture
import run_pin03
import pin06_public_setup_pair as pair
import pin12_public_boundaries as public
import pin13_original_cohort as cohort

old = sys.modules.get('_parlor_public_failure_boundary_controls') or importlib.import_module('test_pin12_public_boundaries')


def evidence(out, *, failed=False):
    state = old.evidence(out, failed=failed); state['profile'] = cohort.PROFILE
    dest = out/'compose'; summary = json.loads((dest/'summary.json').read_bytes())
    tests = json.loads((dest/'tests.json').read_bytes())
    previous = {p['nodeIdentifier']: p['result'] for p in tests['testNodes']}
    tests['testNodes'] = [dict(nodeType='Test Case', nodeIdentifier=case,
                             result=previous.get(case, 'Passed')) for case in cohort.CASES]
    summary.update(totalTestCount=23, passedTests=23-int(failed), failedTests=int(failed))
    for name,value in (('summary.json',summary),('tests.json',tests)):
        (dest/name).write_text(json.dumps(value))
    (dest/'test.log').write_text(''.join("Test Case '-[iosAppUITests." + case.replace('/', ' ').removesuffix('()')
        + "]' started.\n" for case in cohort.CASES))
    return state


class OriginalCohortControls(unittest.TestCase):
    def test_full_cohort_accepted_diagnostic_label_has_zero_process_exit_only_after_acceptance(self):
        self.assertEqual(run_pin03.completed_exit_code(
            'ORIGINAL_FULL_COHORT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'), 0)
        self.assertEqual(run_pin03.completed_exit_code('ORIGINAL_COHORT_QUALIFIED'), 1)

    def test_all23_cases_match_original_bound_source_without_adding_or_skipping_a_case(self):
        driver = pair.fixture(pin02_capture.DRIVER)
        path = pair.HERE.parents[2]/'iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift'
        raw = path.read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(),
                         'a4730b77ad93d01df60a4b6fde558bfea264f71b19ee5aa6c4a08d33480eacc8')
        found = {}
        for source in (driver,raw.decode()):
            classes = list(re.finditer(r'^final class ([A-Za-z0-9_]+): XCTestCase',source,re.M))
            for index, match in enumerate(classes):
                end = classes[index+1].start() if index+1<len(classes) else len(source)
                found[match.group(1)] = tuple(re.findall(r'\bfunc (test[A-Za-z0-9_]+)\(',source[match.end():end]))
        self.assertEqual({k:set(v) for k,v in found.items()}, {k:set(v) for k,v in cohort.METHODS.items()})
        self.assertEqual(len(set(cohort.CASES)), 23)

    def test_copied_product_and_entire_original_driver_are_identical_to_pin12(self):
        args = ({p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS)
        self.assertEqual(cohort.renderer_for_profile(cohort.PROFILE,cohort.SOURCE,pair.RUNTIME,pair.DEVICE)(*args,arm='compose'),
            public.renderer_for_profile(public.PROFILE,public.SOURCE,pair.RUNTIME,pair.DEVICE)(*args,arm='compose'))
        command = cohort.test_command(['xcodebuild', '-quiet'],Path('/r'))
        self.assertEqual(command[:4], ['xcodebuild','test-without-building','-resultBundlePath','/r'])
        selected = [v.removeprefix('-only-testing:iosAppUITests/')+'()' for v in command[4:]]
        self.assertEqual(tuple(selected),cohort.CASES)
        self.assertNotIn('-retry-tests-on-failure', command)
        self.assertNotIn('-quiet', command)

    def test_original_success_and_target_failure_require_full_cohort_and_unchanged_complete_custody(self):
        for failed in (False,True):
            with tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve(); state=evidence(out,failed=failed)
                self.assertEqual(cohort.accept_native_export(out,state),
                    'ORIGINAL_FULL_COHORT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')
                with self.assertRaises(ValueError):
                    current.verify_results(json.loads((out/'compose/summary.json').read_bytes()),
                        json.loads((out/'compose/tests.json').read_bytes()),state['arms'][0]['simulator'],state['arms'][0]['test_exit'])

    def test_skips_expected_failures_duplicate_missing_unknown_cases_wrong_device_and_exit_reject(self):
        mutations = (
            lambda s,t: s.update(skippedTests=1), lambda s,t: s.update(expectedFailures=1),
            lambda s,t: s.update(totalTestCount=True), lambda s,t: s.update(totalTestCount=2),
            lambda s,t: t['testNodes'].pop(), lambda s,t: t['testNodes'].append(t['testNodes'][0]),
            lambda s,t: t['testNodes'][0].update(nodeIdentifier='Foreign/testUnknown()'),
            lambda s,t: t['devices'][0].update(deviceId='foreign'),
            lambda s,t: t['devices'][0].update(osVersion='26.2'),
            lambda s,t: s.update(result='Failed'),
        )
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve(); state=evidence(out)
                summary=json.loads((out/'compose/summary.json').read_bytes());tests=json.loads((out/'compose/tests.json').read_bytes())
                mutate(summary,tests)
                with self.assertRaises(ValueError):cohort.verify_results(summary,tests,state['arms'][0]['simulator'],0)

    def test_non_target_failure_is_not_waived_even_when_both_public_targets_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            summary=json.loads((out/'compose/summary.json').read_bytes());tests=json.loads((out/'compose/tests.json').read_bytes())
            summary.update(passedTests=22,failedTests=1,result='Failed');tests['testNodes'][0]['result']='Failed'
            with self.assertRaisesRegex(ValueError,'cannot be waived'):
                cohort.verify_results(summary,tests,state['arms'][0]['simulator'],65)

    def test_changed_prelude_order_missing_failed_companion_and_unreaped_child_reject(self):
        for mutate in (
            lambda o,s: (o/'compose/test.log').write_text(''),
            lambda o,s: (o/'compose/test.log').write_text((o/'compose/test.log').read_text().replace(
                "ComposeContainerViewControllerTests testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections",
                "ComposeContainerViewControllerTests testForeign")),
            lambda o,s: next((o/'compose').glob('native-root-*.json')).unlink(),
            lambda o,s: s['commands'][0].update(direct_child_reaped=False),
        ):
            with tempfile.TemporaryDirectory() as temporary:
                out=Path(temporary).resolve();state=evidence(out,failed=True);mutate(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)):cohort.accept_native_export(out,state)


if __name__=='__main__':
    unittest.main()
