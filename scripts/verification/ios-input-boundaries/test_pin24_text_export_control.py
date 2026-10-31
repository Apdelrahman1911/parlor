"""Corrected isolated failure-owner controls; fixtures are not native evidence."""
import json
from pathlib import Path
import tempfile
import unittest

import pin02_capture
import pin24_text_export_control as isolated
import test_pin23_text_session_boundary as previous
import run_pin03


def evidence(out):
    state=previous.evidence(out);state['profile']=isolated.PROFILE;row=state['arms'][0];dest=out/'compose'
    row['methods']={isolated.text.CONTROL:'Failed'}
    summary=json.loads((dest/'summary.json').read_bytes());summary.update(totalTestCount=1,passedTests=0)
    (dest/'summary.json').write_text(json.dumps(summary))
    tests=json.loads((dest/'tests.json').read_bytes());tests['testNodes']=[n for n in tests['testNodes'] if n['nodeIdentifier']==isolated.text.CONTROL]
    (dest/'tests.json').write_text(json.dumps(tests))
    for c in state['commands']:
        if c['label']=='compose-tests':c['command']=isolated.test_command(['xcodebuild'],Path('/owned/tests.xcresult'))
    return state


class IsolatedTextExportControls(unittest.TestCase):
    def test_only_existing_isolated_control_executes_and_original_cohort_is_not_replayed(self):
        self.assertEqual(isolated.test_command(['xcodebuild','-quiet'],Path('/owned/result')),
            ['xcodebuild','test-without-building','-resultBundlePath','/owned/result',
             '-only-testing:iosAppUITests/'+isolated.text.CONTROL.removesuffix('()')])
        renderer=isolated.renderer_for_profile(isolated.PROFILE,isolated.SOURCE,isolated.text.motion.pair.RUNTIME,isolated.text.motion.pair.DEVICE)
        self.assertEqual(renderer({p:isolated.text.motion.pair.fixture(p) for p in isolated.text.motion.pair.MODIFIED},isolated.text.motion.pair.INPUTS,arm='compose'),previous.plan())
        with self.assertRaises(ValueError):run_pin03.main(text_export_control=True,text_boundary=True)
        self.assertEqual(run_pin03.completed_exit_code(isolated.RESULT),0)
        self.assertEqual(run_pin03.completed_exit_code('PUBLIC_NAME_QUALIFIED'),1)

    def test_complete_sentinel_failure_requires_correct_owner_actual_callsite_and_all_companions(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve();state=evidence(out)
            self.assertEqual(isolated.accept_native_export(out,state),isolated.RESULT)
            log=out/'compose/test.log';raw=log.read_text();driver=previous.plan()[pin02_capture.DRIVER]
            sentinel=next(i for i,s in enumerate(driver.splitlines(),1) if 'equals: "PIN23 isolated export sentinel"' in s)
            first=next(i for i,s in enumerate(driver.splitlines(),1) if 'assertTextValue(name, in: app, equals: "Table Test"' in s)
            for wrong in (raw.replace(f'.swift:{sentinel}:',f'.swift:{first}:'),raw+raw,''):
                log.write_text(wrong)
                with self.assertRaises(ValueError):isolated.accept_native_export(out,state)
            log.write_text(raw)
            state['arms'][0]['captures']=[]
            with self.assertRaises(ValueError):isolated.accept_native_export(out,state)


if __name__=='__main__':unittest.main()
