"""Fail-closed native collector controls. Fixtures are NOT native execution."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pin07_native_adapter as adapter
import pin07_failure_control as control
import pin06_public_setup_pair as pair
import run_pin03
def snapshot():
    # Synthetic fixture, not native observations.
    return dict(schema=1, nativeMode=1, overflow=0, invalid=0, rows=[
        [0,4,0,6,0,0,0,0,0,0], [1,3,3,0,0,0,0,0,0,0], [2,4,0,0,0,0,0,0,0,0],
        [3,1,0,1,1,1,0,0,0,0], [4,2,1,0,1,1,0,0,0,0], [5,1,0,3,1,0,0,0,0,0],
        [6,2,1,1,1,0,0,0,0,0], [7,3,1,0,0,0,0,0,0,0]])

DEVICE = '00000000-0000-4000-8000-000000000001'
TOKEN = '00000000-0000-4000-8000-000000000002'
BUNDLE = 'me.parlor.ios.debug'


def results():
    device = dict(deviceId=DEVICE, architecture='arm64', platform='iOS Simulator',
                  modelName='iPhone 16 Pro', osVersion='18.5')
    summary = dict(totalTestCount=1, failedTests=1, passedTests=0, skippedTests=0,
                   expectedFailures=0, result='Failed', devicesAndConfigurations=[dict(device=device)],
                   testFailures=[dict(testIdentifierString=adapter.METHOD, targetName='iosAppUITests',
                                      failureText=adapter.FAILURE)])
    tests = dict(devices=[device], testNodes=[dict(nodeType='Test Case',
                 nodeIdentifier=adapter.METHOD, result='Failed')])
    return summary, tests


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value))


def evidence(out):
    dest = out / 'native'
    summary, tests = results()
    write(dest / 'summary.json', summary)
    write(dest / 'tests.json', tests)
    plan = control.render({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS,
                          profile=control.PROFILE)
    write(dest / 'inputs.json', pair.INPUTS | {
        p: hashlib.sha256(raw.encode()).hexdigest() for p, raw in plan.items()})
    write(dest / 'product.json', {'iosApp': 'a' * 64})
    write(dest / 'attachments/failed-01/request.json', dict(schema=1, token=TOKEN,
          ackObserved=True, originalTransitionFailed=True))
    capture = dict(schema=1, token=TOKEN, simulator=DEVICE, bundle=BUNDLE,
                   boot='00000000-0000-4000-8000-000000000003', process=100,
                   installedMs=1, capturedMs=20, snapshot=snapshot())
    path = dest / ('capture-' + TOKEN + '.json')
    write(path, capture)
    write(dest / 'cleanup.json', dict(gradle_exit=0, workers_before=dict(result='PASS'),
          workers_after=dict(result='PASS'), simulator=dict(result='PASS', udid=DEVICE)))
    row = dict(arm='native', build_exit=0, test_exit=65, result='JOURNEYS_FAILED',
               cleanup='PASS', simulator=DEVICE, methods={adapter.METHOD: 'Failed'},
               attachment_test_ids=[adapter.METHOD],
               product_manifest_sha256=hashlib.sha256((dest / 'product.json').read_bytes()).hexdigest(),
               captures=[run_pin03.decode_capture(path.read_bytes(), {TOKEN}, DEVICE, BUNDLE)])
    return dict(profile=adapter.PROFILE, source=pair.SOURCE, source_tree=pair.TREE,
                runtime=pair.RUNTIME, device=pair.DEVICE, xcode='Xcode 26.3\nBuild version 17C529\n',
                sdk='26.2', debug_bundle=BUNDLE, original_inputs=pair.INPUTS, arms=[row],
                commands=[dict(label=label, exit_code=code, status='EXITED', direct_child_reaped=True)
                          for label, code in (('native-build', 0), ('native-tests', 65),
                                              ('native-gradle-stop', 0))])


class NativeFailureExportAdapterTests(unittest.TestCase):
    def test_exact_synthetic_profile_and_native_arm_only(self):
        renderer = adapter.renderer_for_profile(adapter.PROFILE, pair.SOURCE, pair.RUNTIME, pair.DEVICE)
        original = {p: pair.fixture(p) for p in pair.MODIFIED}
        self.assertEqual(renderer(original, pair.INPUTS, arm='native'),
                         control.render(original, pair.INPUTS, profile=control.PROFILE))
        for arm in ('original', 'compose'):
            with self.assertRaises(ValueError):
                renderer(original, pair.INPUTS, arm=arm)
        values = [adapter.PROFILE, pair.SOURCE, pair.RUNTIME, pair.DEVICE]
        for index in range(4):
            changed = values.copy(); changed[index] += '-changed'
            with self.assertRaises(ValueError):
                adapter.renderer_for_profile(*changed)

    def test_one_added_method_only_no_replay_or_production_selection(self):
        base = ['xcodebuild', '-parallel-testing-enabled', 'NO']
        self.assertEqual(adapter.test_command(base, Path('/r')), [*base, 'test-without-building',
                         '-resultBundlePath', '/r', '-only-testing:iosAppUITests/' + adapter.METHOD[:-2]])
        self.assertEqual(len(adapter.failed_method_exports(Path('/r'), Path('/d'),
                         {adapter.METHOD: 'Failed'})), 1)
        with self.assertRaises(ValueError):
            adapter.failed_method_exports(Path('/r'), Path('/d'), {adapter.METHOD: 'Passed'})

    def test_original_failed_exit_method_assertion_and_device_required(self):
        summary, tests = results()
        self.assertEqual(adapter.verify_results(summary, tests, DEVICE, 65), {adapter.METHOD: 'Failed'})
        for code in (0, 1, 64, 66, True):
            with self.subTest(code=code), self.assertRaises(ValueError):
                adapter.verify_results(summary, tests, DEVICE, code)
        mutations = [lambda s: s.update(failedTests=0), lambda s: s.update(skippedTests=1),
                     lambda s: s.update(expectedFailures=1), lambda s: s.update(totalTestCount=2),
                     lambda s: s['testFailures'][0].update(failureText='earlier pre-tap failure'),
                     lambda s: s['testFailures'].append(s['testFailures'][0]),
                     lambda s: s['devicesAndConfigurations'][0]['device'].update(osVersion='26.2')]
        for mutate in mutations:
            changed = copy.deepcopy(summary); mutate(changed)
            with self.assertRaises(ValueError):
                adapter.verify_results(changed, tests, DEVICE, 65)
        changed = copy.deepcopy(tests); changed['testNodes'][0]['nodeIdentifier'] = 'foreign()'
        with self.assertRaises(ValueError):
            adapter.verify_results(summary, changed, DEVICE, 65)

    def test_complete_software_fixture_is_capture_only_never_application_qualification(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            self.assertEqual(adapter.accept_native_export(out, evidence(out)),
                             'SYNTHETIC_FAILED_EXPORT_CAPTURE_ONLY')

    def test_missing_changed_and_duplicate_ack_export_or_source_reject(self):
        mutations = [
            lambda out, s: write(out / 'native/attachments/failed-01/request.json',
                                 dict(schema=1, token=TOKEN, ackObserved=False, originalTransitionFailed=True)),
            lambda out, s: write(out / 'native/attachments/failed-01/duplicate.json',
                                 dict(schema=1, token=TOKEN, ackObserved=True, originalTransitionFailed=True)),
            lambda out, s: (out / 'native' / ('capture-' + TOKEN + '.json')).unlink(),
            lambda out, s: s.update(original_inputs={}),
            lambda out, s: s.update(debug_bundle='foreign.debug'),
            lambda out, s: write(out / 'native/inputs.json', {}),
            lambda out, s: write(out / 'native/product.json', {'changed': 'b' * 64}),
            lambda out, s: write(out / 'native/cleanup.json', dict(gradle_exit=1)),
        ]
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutate(out, state)
                with self.assertRaises((ValueError, KeyError)):
                    adapter.accept_native_export(out, state)

    def test_unreaped_cancelled_duplicate_or_missing_commands_reject(self):
        for mutation in (lambda s: s.update(commands=[]),
                         lambda s: s['commands'][0].update(direct_child_reaped=False),
                         lambda s: s['commands'][1].update(exit_code=0),
                         lambda s: s['commands'][1].update(status='TIMED_OUT'),
                         lambda s: s['commands'].append(s['commands'][1]),
                         lambda s: s['arms'][0].update(cleanup='FAIL')):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutation(state)
                with self.assertRaises(ValueError):
                    adapter.accept_native_export(out, state)

    def test_evidence_reader_rejects_duplicates_links_and_oversize(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(); path = root / 'evidence.json'
            path.write_text('{"x":1,"x":2}')
            with self.assertRaises(ValueError):
                adapter.load(path)
            path.write_text('{}'); link = root / 'link'; link.symlink_to(path)
            with self.assertRaises(ValueError):
                adapter.load(link)
            with self.assertRaises(ValueError):
                adapter.load(path, 1)


if __name__ == '__main__':
    unittest.main()
