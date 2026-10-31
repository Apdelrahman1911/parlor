"""Portable isolated-control controls; no UIKit or app qualification claim."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import pin20_catalog_motion as motion
import pin21_motion_export_control as control
import run_pin03
import test_pin20_catalog_motion as previous


def evidence(out):
    state = previous.evidence(out); state['profile'] = control.PROFILE
    row = state['arms'][0]; row['methods'] = {motion.CONTROL: 'Failed'}
    dest = out / 'compose'
    summary = json.loads((dest / 'summary.json').read_bytes())
    summary.update(totalTestCount=1, passedTests=0)
    (dest / 'summary.json').write_text(json.dumps(summary))
    tests = json.loads((dest / 'tests.json').read_bytes())
    tests['testNodes'] = [n for n in tests['testNodes'] if n['nodeIdentifier'] == motion.CONTROL]
    (dest / 'tests.json').write_text(json.dumps(tests))
    command = next(c for c in state['commands'] if c['label'] == 'compose-tests')
    command['command'] = control.test_command(['xcodebuild'], Path('/owned/tests.xcresult'))
    return state


class IsolatedMotionControls(unittest.TestCase):
    def test_source_product_is_identical_to_prior_motion_copy_but_only_control_executes(self):
        renderer = control.renderer_for_profile(control.PROFILE, control.SOURCE, motion.pair.RUNTIME, motion.pair.DEVICE)
        self.assertEqual(renderer({p: motion.pair.fixture(p) for p in motion.pair.MODIFIED},
            motion.pair.INPUTS, arm='compose'), previous.plan())
        command = control.test_command(['xcodebuild', '-quiet'], Path('/owned/tests.xcresult'))
        self.assertEqual([c for c in command if c.startswith('-only-testing:')],
            ['-only-testing:iosAppUITests/' + motion.CONTROL.removesuffix('()')])
        self.assertNotIn('-quiet', command)
        self.assertEqual(run_pin03.completed_exit_code(control.RESULT), 0)
        self.assertEqual(run_pin03.completed_exit_code('ISOLATED_CATALOG_MOTION_QUALIFIED'), 1)
        for profile in ('catalog', motion.PROFILE):
            with self.assertRaises(ValueError):
                control.renderer_for_profile(profile, control.SOURCE, motion.pair.RUNTIME, motion.pair.DEVICE)
        with self.assertRaises(ValueError): run_pin03.main(motion_control=True, public_motion=True)

    def test_exact_failed_control_and_all_custody_are_required_without_natural_journey_replay(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary).resolve(); state = evidence(out)
            self.assertEqual(control.accept_native_export(out, state), control.RESULT)
            for change in (lambda s: s.update(profile=motion.PROFILE),
                lambda s: s['arms'][0].update(motion_companion_captures=[]),
                lambda s: s['arms'][0].update(cleanup='FAIL'),
                lambda s: s['arms'][0]['methods'].update({motion.CASES[0]: 'Passed'}),
                lambda s: next(c for c in s['commands'] if c['label']=='compose-tests')['command'].append(
                    '-only-testing:iosAppUITests/' + motion.CASES[0].removesuffix('()'))):
                wrong = copy.deepcopy(state); change(wrong)
                with self.assertRaises(ValueError): control.accept_native_export(out, wrong)
            summary = json.loads((out / 'compose/summary.json').read_bytes())
            tests = json.loads((out / 'compose/tests.json').read_bytes()); simulator = state['arms'][0]['simulator']
            for change in (lambda s: s.update(skippedTests=1), lambda s: s.update(totalTestCount=3),
                lambda s: s['testFailures'][0].update(failureText='Public setup control is not reachable')):
                wrong = copy.deepcopy(summary); change(wrong)
                with self.assertRaises(ValueError): control.verify_results(wrong, tests, simulator, 65)
            with self.assertRaises(ValueError): control.verify_results(summary, tests, simulator, 0)
            with self.assertRaises(ValueError): motion.verify_results(summary, tests, simulator, 65)


if __name__ == '__main__': unittest.main()
