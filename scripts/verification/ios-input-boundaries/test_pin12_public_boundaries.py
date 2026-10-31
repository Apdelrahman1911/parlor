"""Software source/custody controls, not native original failure evidence."""
import hashlib
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

import pin02_capture
import run_pin03
import pin06_public_setup_pair as pair
import pin11_root_observations as observed
import pin11_public_failure_coverage as coverage
import pin12_public_boundaries as public

old = sys.modules.get('_parlor_root_observation_controls') or importlib.import_module('test_pin11_root_observations')


def evidence(out, *, failed=False):
    state = old.evidence(out, failed=failed)
    state['profile'] = public.PROFILE
    plan = public.renderer_for_profile(public.PROFILE, public.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='compose')
    (out/'compose/inputs.json').write_text(json.dumps(pair.INPUTS | {
        p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()}))
    return state


class PublicBoundaryControls(unittest.TestCase):
    def test_accepted_public_profile_exits_zero_without_qualifying_application_failures(self):
        self.assertEqual(run_pin03.completed_exit_code(
            'PUBLIC_FAILURE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'), 0)
        for result in ('INCOMPLETE', 'NOT_QUALIFIED', 'JOURNEYS_FAILED', 'JOURNEYS_PASSED',
                       'PRODUCTION_QUALIFIED', 'unknown', None, True, {}):
            self.assertEqual(run_pin03.completed_exit_code(result), 1)
        for failed in (False, True):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve()
                state = evidence(out, failed=failed)
                label = public.accept_native_export(out, state)
                self.assertEqual(run_pin03.completed_exit_code(label), 0)
                self.assertEqual(state['arms'][0]['test_exit'], 65 if failed else 0)

    def test_separate_profile_has_exact_inverse_and_unchanged_original_xctest_selection(self):
        args = ({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS)
        plan = public.renderer_for_profile(public.PROFILE, public.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose')
        self.assertEqual(coverage.restore(plan),
            observed.renderer_for_profile(observed.PROFILE, observed.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose'))
        self.assertEqual(public.test_command(['xcodebuild'], Path('/r')),
                         observed.test_command(['xcodebuild'], Path('/r')))
        self.assertIn('override func record(_ issue: XCTIssue)', plan[pin02_capture.DRIVER])
        self.assertNotIn('PIN01TouchObserver', plan[pin02_capture.CONTAINER])

    def test_success_has_no_exports_and_original_failure_requires_complete_source_bound_custody(self):
        for failed in (False, True):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve()
                self.assertEqual(public.accept_native_export(out, evidence(out, failed=failed)),
                    'PUBLIC_FAILURE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_changed_source_or_profile_or_arm_rejects(self):
        for profile, source, runtime, device in (
            (observed.PROFILE, public.SOURCE, pair.RUNTIME, pair.DEVICE),
            (public.PROFILE, 'a'*40, pair.RUNTIME, pair.DEVICE),
            (public.PROFILE, public.SOURCE, 'com.apple.CoreSimulator.SimRuntime.iOS-26-2', pair.DEVICE),
        ):
            with self.assertRaises(ValueError): public.renderer_for_profile(profile, source, runtime, device)
        render = public.renderer_for_profile(public.PROFILE, public.SOURCE, pair.RUNTIME, pair.DEVICE)
        for arm in ('original', 'native'):
            with self.assertRaises(ValueError):
                render({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm=arm)

    def test_missing_or_duplicate_failed_companion_or_success_export_or_unreaped_command_rejects(self):
        for failed, mutate in (
            (True, lambda o,s: next((o/'compose').glob('native-root-*.json')).unlink()),
            (True, lambda o,s: (o/'compose/native-accessibility-foreign.json').write_text('{}')),
            (False, lambda o,s: (o/'compose/native-root-foreign.json').write_text('{}')),
            (True, lambda o,s: s['commands'][0].update(direct_child_reaped=False)),
        ):
            with tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary).resolve(); state = evidence(out, failed=failed); mutate(out,state)
                with self.assertRaises((ValueError, FileNotFoundError)): public.accept_native_export(out,state)


if __name__ == '__main__':
    unittest.main()
