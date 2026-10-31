"""Software control fixtures, not original application or native root evidence."""
import hashlib
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

import pin02_capture
import pin06_public_setup_pair as pair
import pin09_native_boundary as boundary
import pin10_root_boundary as root
import pin11_root_observations as observed

old=sys.modules.get('_parlor_native_boundary_controls') or importlib.import_module('test_pin09_native_boundary')
root_fixture=sys.modules.get('_parlor_root_boundary_controls') or importlib.import_module('test_pin10_root_boundary')


def evidence(out, *, failed=False):
    state=old.evidence(out,failed=failed);state['profile']=observed.PROFILE
    dest=out/'compose'
    plan=observed.renderer_for_profile(observed.PROFILE,observed.SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')
    (dest/'inputs.json').write_text(json.dumps(pair.INPUTS|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()}))
    if failed:
        legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes());value=root_fixture.fixture()
        value.update(token=legacy['token'],simulator=legacy['simulator'])
        path=dest/('native-root-'+value['token']+'.json');path.write_text(json.dumps(value))
        state['arms'][0]['root_companion_captures']=[root.decode_root(path.read_bytes(),token=value['token'],
            simulator=value['simulator'],bundle=value['bundle'],legacy=legacy)]
    return state


class RootObservationControls(unittest.TestCase):
    def test_original_driver_actions_methods_and_no_touch_recognizer_remain_exact(self):
        originals={p:pair.fixture(p) for p in pair.MODIFIED}
        base=boundary.renderer_for_profile(boundary.PROFILE,boundary.SOURCE,pair.RUNTIME,pair.DEVICE)(
            originals,pair.INPUTS,arm='compose')
        plan=observed.renderer_for_profile(observed.PROFILE,observed.SOURCE,pair.RUNTIME,pair.DEVICE)(
            originals,pair.INPUTS,arm='compose')
        driver=plan[pin02_capture.DRIVER]
        self.assertEqual(driver.replace(observed.FAILURE_CLOCK_LOG,observed.OLD_FAILURE_LOG),
                         base[pin02_capture.DRIVER])
        self.assertEqual(set(p for p in plan if plan[p]!=base[p]),
                         {pin02_capture.CONTAINER,pin02_capture.DRIVER})
        self.assertIn('if result != .completed {',driver)
        self.assertIn('uptimeStartedMs=\\(started * 1000)',driver)
        self.assertEqual(driver.count('ProcessInfo.processInfo.systemUptime'),
                         base[pin02_capture.DRIVER].count('ProcessInfo.processInfo.systemUptime'))
        self.assertNotIn('PIN01TouchObserver',plan[pin02_capture.CONTAINER])
        self.assertEqual(observed.test_command(['xcodebuild'],Path('/r')),boundary.test_command(['xcodebuild'],Path('/r')))

    def test_success_has_no_root_export_and_failure_requires_both_old_and_new_evidence(self):
        for failed in (False,True):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve()
                self.assertEqual(observed.accept_native_export(out,evidence(out,failed=failed)),
                    'ROOT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_wrong_profile_and_original_or_native_arm_reject_before_execution(self):
        with self.assertRaises(ValueError):
            observed.renderer_for_profile(boundary.PROFILE,observed.SOURCE,pair.RUNTIME,pair.DEVICE)
        for arm in ('original','native'):
            with self.assertRaises(ValueError):
                observed.renderer_for_profile(observed.PROFILE,observed.SOURCE,pair.RUNTIME,pair.DEVICE)(
                    {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm=arm)

    def test_failed_missing_tampered_duplicate_or_unowned_root_rejects(self):
        for mutate in (lambda o,s: next((o/'compose').glob('native-root-*.json')).unlink(),
            lambda o,s: next((o/'compose').glob('native-root-*.json')).write_text('{}'),
            lambda o,s: (o/'compose/native-root-foreign.json').write_text('{}'),
            lambda o,s: s['arms'][0].update(root_companion_captures=[]),
            lambda o,s: s['commands'][0].update(direct_child_reaped=False)):
            with tempfile.TemporaryDirectory() as tmp:
                out=Path(tmp).resolve();state=evidence(out,failed=True);mutate(out,state)
                with self.assertRaises((ValueError,FileNotFoundError)):observed.accept_native_export(out,state)

    def test_successful_export_is_never_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp).resolve();state=evidence(out)
            (out/'compose/native-root-foreign.json').write_text('{}')
            with self.assertRaises(ValueError):observed.accept_native_export(out,state)


if __name__ == '__main__':
    unittest.main()
