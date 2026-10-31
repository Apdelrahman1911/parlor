"""Source-bound current observation controls, not a native execution claim."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import current_boundary_adapter as current
import pin02_capture
import pin06_public_setup_pair as pair
import run_pin03


def originals():
    return {p: pair.fixture(p) for p in pair.MODIFIED}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value))


def evidence(out):
    renderer = current.renderer_for_profile(current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE)
    state = dict(profile=current.PROFILE, source=current.SOURCE, source_tree=current.TREE,
                 runtime=pair.RUNTIME, device=pair.DEVICE, debug_bundle='me.parlor.ios.debug',
                 xcode='Xcode 26.3\nBuild version 17C529\n', sdk='26.2',
                 original_inputs=pair.INPUTS, arms=[], commands=[])
    for index, arm in enumerate(current.ARMS):
        dest = out / arm; device_id = '00000000-0000-4000-8000-00000000000' + str(index + 1)
        device = dict(deviceId=device_id, architecture='arm64', platform='iOS Simulator',
                      modelName='iPhone 16 Pro', osVersion='18.5')
        cases = [dict(nodeType='Test Case', nodeIdentifier=t.removeprefix('iosAppUITests/') + '()',
                      result='Passed') for t in run_pin03.TESTS]
        summary = dict(totalTestCount=2, failedTests=0, passedTests=2, skippedTests=0,
                       expectedFailures=0, result='Passed', devicesAndConfigurations=[dict(device=device)])
        tests = dict(devices=[device], testNodes=cases)
        write(dest / 'summary.json', summary); write(dest / 'tests.json', tests)
        plan = renderer(originals(), pair.INPUTS, arm=arm)
        write(dest / 'inputs.json', pair.INPUTS | {p: hashlib.sha256(raw.encode()).hexdigest() for p, raw in plan.items()})
        write(dest / 'product.json', {'iosApp': 'a' * 64})
        write(dest / 'cleanup.json', dict(gradle_exit=0, workers_before=dict(result='PASS'),
              workers_after=dict(result='PASS'), simulator=dict(result='PASS', udid=device_id)))
        state['arms'].append(dict(arm=arm, build_exit=0, cleanup='PASS', test_exit=0,
              simulator=device_id, methods={c['nodeIdentifier']: 'Passed' for c in cases},
              result='JOURNEYS_PASSED', captures=[], product_manifest_sha256=
              hashlib.sha256((dest / 'product.json').read_bytes()).hexdigest()))
        state['commands'].extend(dict(label=label, exit_code=0, status='EXITED', direct_child_reaped=True)
              for label in (arm + '-build', arm + '-tests', arm + '-gradle-stop'))
    return state


class CurrentBoundaryAdapterTests(unittest.TestCase):
    def test_exact_current_source_and_observed_arms_no_original_or_synthetic_fault(self):
        renderer = current.renderer_for_profile(current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE)
        for arm in current.ARMS:
            baseline = pair.render(originals(), pair.INPUTS, arm=arm)
            plan = renderer(originals(), pair.INPUTS, arm=arm)
            self.assertEqual(set(plan), set(baseline))
            self.assertEqual({p for p in plan if plan[p] != baseline[p]}, {pin02_capture.DRIVER})
            self.assertEqual(plan[pin02_capture.DRIVER].replace(current.OWNER_CALL, pair.OWNER_CALL),
                             baseline[pin02_capture.DRIVER])
        for arm in ('original', 'synthetic', None):
            with self.assertRaises(ValueError):
                renderer(originals(), pair.INPUTS, arm=arm)
        values = [current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE]
        for index in range(4):
            changed = values.copy(); changed[index] += '-changed'
            with self.assertRaises(ValueError):
                current.renderer_for_profile(*changed)

    def test_all_original_six_localized_catalog_prefixes_bound_exactly(self):
        driver = originals()[pin02_capture.DRIVER]
        for prefix in current.PREFIXES:
            self.assertIn('"' + prefix + '"', driver)
            self.assertEqual(current.OWNER_CALL.count('"' + prefix + '"'), 1)
        self.assertEqual(current.OWNER_CALL.count('? app : nil'), 1)
        self.assertEqual(current.test_command(['xcodebuild'], Path('/r')), run_pin03.test_command(['xcodebuild'], Path('/r')))
        for forbidden in ('tap()', 'typeText', 'swipe', 'XCTExpectFailure', 'timeout:', '|| true'):
            self.assertNotIn(forbidden, current.OWNER_CALL)

    def test_successful_paths_queries_taps_predicates_and_deadlines_remain_exact(self):
        renderer = current.renderer_for_profile(current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE)
        plan = renderer(originals(), pair.INPUTS, arm='native')[pin02_capture.DRIVER]
        baseline = pair.render(originals(), pair.INPUTS, arm='native')[pin02_capture.DRIVER]
        for anchor in ('control.tap()', 'app.swipeUp(velocity: .slow)', 'timeout: 10)',
                       'destination.exists && !source.exists', 'PIN02FailureRequest.captureAfterFailedTransition(app)',
                       'Public setup control must stop scrolling before it is tapped',
                       'One public setup tap must remove the source and expose its destination',
                       'Public setup must not request network access', 'continueAfterFailure = false'):
            self.assertEqual(plan.count(anchor), baseline.count(anchor), anchor)
        self.assertIn(pair.CAPTURE, plan)

    def test_source_mutation_rejects_before_build(self):
        renderer = current.renderer_for_profile(current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE)
        for path in pair.MODIFIED:
            changed = originals(); changed[path] += '\n'
            with self.assertRaises(ValueError):
                renderer(changed, pair.INPUTS | {path: hashlib.sha256(changed[path].encode()).hexdigest()}, arm='native')

    def test_complete_software_observation_is_not_app_qualification(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            self.assertEqual(current.accept_native_export(out, evidence(out)),
                             'CURRENT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_foreign_source_missing_arm_changed_inputs_product_or_unreaped_command_reject(self):
        mutations = [lambda out, s: s.update(source=pair.SOURCE), lambda out, s: s.update(arms=s['arms'][:1]),
                     lambda out, s: s.update(debug_bundle='foreign.debug'),
                     lambda out, s: write(out / 'native/inputs.json', {}),
                     lambda out, s: write(out / 'native/product.json', {'changed': 'b' * 64}),
                     lambda out, s: s['commands'][0].update(direct_child_reaped=False),
                     lambda out, s: s['arms'][1].update(cleanup='FAIL')]
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutate(out, state)
                with self.assertRaises(ValueError):
                    current.accept_native_export(out, state)

    def test_earlier_failed_method_without_real_export_cannot_complete_collection(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve(); state = evidence(out); row = state['arms'][0]
            summary = json.loads((out / 'compose/summary.json').read_bytes())
            tests = json.loads((out / 'compose/tests.json').read_bytes())
            tests['testNodes'][0]['result'] = 'Failed'
            summary.update(result='Failed', failedTests=1, passedTests=1)
            write(out / 'compose/summary.json', summary); write(out / 'compose/tests.json', tests)
            row.update(test_exit=65, result='JOURNEYS_FAILED', methods={p['nodeIdentifier']: p['result'] for p in tests['testNodes']})
            state['commands'][1]['exit_code'] = 65
            with self.assertRaisesRegex(ValueError, 'export per failed method'):
                current.accept_native_export(out, state)

    def test_failed_method_requires_matching_once_only_ack_and_complete_buffer(self):
        for mutation in ('valid-software-fixture', 'missing-ack', 'duplicate-request', 'private-field'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); row = state['arms'][0]; dest = out / 'compose'
                summary = json.loads((dest / 'summary.json').read_bytes())
                tests = json.loads((dest / 'tests.json').read_bytes()); tests['testNodes'][0]['result'] = 'Failed'
                summary.update(result='Failed', failedTests=1, passedTests=1)
                write(dest / 'summary.json', summary); write(dest / 'tests.json', tests)
                failed = tests['testNodes'][0]['nodeIdentifier']
                token = '00000000-0000-4000-8000-000000000009'
                request = dict(schema=1, token=token, ackObserved=True, originalTransitionFailed=True)
                write(dest / 'attachments/failed-01/request.json', request)
                capture = dict(schema=1, token=token, simulator=row['simulator'], bundle=state['debug_bundle'],
                    boot='00000000-0000-4000-8000-000000000008', process=100, installedMs=1, capturedMs=20,
                    snapshot=dict(schema=1, nativeMode=0, overflow=0, invalid=0,
                                  rows=[[0,4,0,6,0,0,0,0,0,0]]))
                path = dest / ('capture-' + token + '.json'); write(path, capture)
                row.update(test_exit=65, result='JOURNEYS_FAILED', attachment_test_ids=[failed],
                    methods={p['nodeIdentifier']: p['result'] for p in tests['testNodes']},
                    captures=[run_pin03.decode_capture(path.read_bytes(), {token}, row['simulator'], state['debug_bundle'])])
                state['commands'][1]['exit_code'] = 65
                if mutation == 'missing-ack':
                    request['ackObserved'] = False; write(dest / 'attachments/failed-01/request.json', request)
                elif mutation == 'duplicate-request':
                    write(dest / 'attachments/failed-01/duplicate.json', request)
                elif mutation == 'private-field':
                    capture['snapshot']['private'] = 'forbidden'; write(path, capture)
                if mutation == 'valid-software-fixture':
                    self.assertEqual(current.accept_native_export(out, state),
                                     'CURRENT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')
                else:
                    with self.assertRaises(ValueError):
                        current.accept_native_export(out, state)


if __name__ == '__main__':
    unittest.main()
