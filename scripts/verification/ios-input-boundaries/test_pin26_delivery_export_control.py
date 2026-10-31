"""Source-specific isolated intake/acceptance controls, not Simulator evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin26_delivery_export_control as isolated
import run_pin03
import test_pin24_text_export_control as previous
import test_pin25_native_input_delivery as native


def evidence(out):
    state=previous.evidence(out);state['profile']=isolated.PROFILE;dest=out/'compose'
    (dest/'inputs.json').write_text(json.dumps(state['original_inputs']|
        {p:hashlib.sha256(v.encode()).hexdigest() for p,v in native.plan().items()}))
    legacy=json.loads(next(dest.glob('capture-*.json')).read_bytes())
    value=native.fixture();value.update(token=legacy['token'],simulator=state['arms'][0]['simulator'],
        process=legacy['process'],capturedMs=legacy['capturedMs'])
    (dest/('native-input-'+legacy['token']+'.json')).write_text(json.dumps(value))
    return state


class IsolatedNativeDeliveryControls(unittest.TestCase):
    def test_no_passing_journey_replay_and_same_unchanged_native_application_plan(self):
        renderer=isolated.renderer_for_profile(isolated.PROFILE,isolated.SOURCE,
            isolated.delivery.text.motion.pair.RUNTIME,isolated.delivery.text.motion.pair.DEVICE)
        self.assertEqual(renderer({p:isolated.delivery.text.motion.pair.fixture(p)
            for p in isolated.delivery.text.motion.pair.MODIFIED},isolated.delivery.text.motion.pair.INPUTS,arm='compose'),native.plan())
        selected=isolated.test_command(['xcodebuild','-quiet'],Path('/owned/result'))
        self.assertEqual([p for p in selected if p.startswith('-only-testing:')],
            ['-only-testing:iosAppUITests/'+isolated.CASES[0].removesuffix('()')])
        self.assertEqual(isolated.CASES,(isolated.delivery.text.CONTROL,))
        self.assertEqual(run_pin03.completed_exit_code(isolated.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('NATIVE_INPUT_QUALIFIED'),1)
        with self.assertRaises(ValueError):run_pin03.main(native_input_delivery=True,native_input_export_control=True)

    def test_only_complete_failure_export_original_assertion_and_native_calls_are_accepted(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(isolated.accept_native_export(out,state),isolated.RESULT)
            p=next((out/'compose').glob('native-input-*.json'));raw=p.read_bytes()
            for change in (lambda v:v.update(installed=False,installation=[50000,0,1],rows=[]),
                lambda v:v.update(rows=[]),lambda v:v.update(overflow=1),
                lambda v:v.update(rows=[[1,1,0,0],[2,1,1,0]])):
                value=json.loads(raw);change(value);p.write_text(json.dumps(value))
                with self.assertRaises(ValueError):isolated.accept_native_export(out,state)
            p.write_bytes(raw)
            state['arms'][0]['methods'][isolated.CASES[0]]='Passed'
            with self.assertRaises(ValueError):isolated.accept_native_export(out,state)


if __name__=='__main__':unittest.main()
