"""Pure software controls: fabricated fixtures are not UIKit execution evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import sys
import importlib

import current_boundary_adapter as current
import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as native
import pin09_native_boundary as boundary
import run_pin03
earlier = sys.modules.get('_parlor_current_boundary_controls') or importlib.import_module('test_current_boundary_adapter')
old_fixture = sys.modules.get('_parlor_native_failure_export_controls') or importlib.import_module('test_pin07_native_adapter')
native_fixture = sys.modules.get('_parlor_native_accessibility_controls') or importlib.import_module('test_pin08_accessibility')


def evidence(out, *, failed=False):
    # Construct a fresh software fixture, not relabeled actual/native results.
    state = earlier.evidence(out)
    state.update(profile=boundary.PROFILE, arms=state['arms'][:1])
    state['commands'] = [c for c in state['commands'] if c['label'].startswith('compose-')]
    row = state['arms'][0]; dest = out / 'compose'
    plan = boundary.renderer_for_profile(boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE)(
        earlier.originals(), pair.INPUTS, arm='compose')
    earlier.write(dest / 'inputs.json', pair.INPUTS |
        {p: hashlib.sha256(v.encode()).hexdigest() for p, v in plan.items()})
    if not failed:
        return state
    summary = json.loads((dest / 'summary.json').read_bytes())
    tests = json.loads((dest / 'tests.json').read_bytes())
    method = tests['testNodes'][0]['nodeIdentifier']
    tests['testNodes'][0]['result'] = 'Failed'
    summary.update(failedTests=1, passedTests=1, result='Failed')
    earlier.write(dest / 'summary.json', summary); earlier.write(dest / 'tests.json', tests)
    row.update(test_exit=65, result='JOURNEYS_FAILED', attachment_test_ids=[method])
    row['methods'][method] = 'Failed'
    for command in state['commands']:
        if command['label'] == 'compose-tests':
            command['exit_code'] = 65
    state['commands'] = [c for c in state['commands'] if c['label'].startswith('compose-')]
    token = old_fixture.TOKEN
    public_snapshot = old_fixture.snapshot() | {'nativeMode': 0}
    public_snapshot['rows'] = [r for r in public_snapshot['rows'] if r[1] != 1]
    capture = dict(schema=1, token=token, simulator=row['simulator'], bundle=state['debug_bundle'],
                   boot='00000000-0000-4000-8000-000000000003', process=100,
                   installedMs=1, capturedMs=20, snapshot=public_snapshot)
    earlier.write(dest / ('capture-' + token + '.json'), capture)
    row['captures'] = [run_pin03.decode_capture((dest / ('capture-' + token + '.json')).read_bytes(),
        {token}, row['simulator'], state['debug_bundle'])]
    earlier.write(dest / 'attachments/failed-01/request.json', dict(schema=1, token=token,
        ackObserved=True, originalTransitionFailed=True))
    companion = native_fixture.fixture() | dict(token=token, simulator=row['simulator'], capturedMs=19)
    path = dest / ('native-accessibility-' + token + '.json'); earlier.write(path, companion)
    row['companion_captures'] = [native.decode_native(path.read_bytes(), token=token,
        simulator=row['simulator'], bundle=state['debug_bundle'], legacy=capture)]
    return state


class NativeBoundaryControls(unittest.TestCase):
    def test_exact_current_profile_and_single_no_recognizer_arm(self):
        args = [boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE]
        renderer = boundary.renderer_for_profile(*args)
        self.assertEqual(boundary.ARMS, ('compose',))
        for arm in ('original', 'native', 'synthetic', None):
            with self.assertRaises(ValueError):
                renderer(earlier.originals(), pair.INPUTS, arm=arm)
        for i in range(4):
            changed = args.copy(); changed[i] += '-changed'
            with self.assertRaises(ValueError):
                boundary.renderer_for_profile(*changed)

    def test_inverse_restoration_preserves_original_driver_and_all_other_observed_inputs(self):
        baseline = current.renderer_for_profile(current.PROFILE, current.SOURCE, pair.RUNTIME, pair.DEVICE)(
            earlier.originals(), pair.INPUTS, arm='compose')
        plan = boundary.renderer_for_profile(boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE)(
            earlier.originals(), pair.INPUTS, arm='compose')
        self.assertEqual(set(plan), set(baseline))
        self.assertEqual({p for p in plan if plan[p] != baseline[p]}, {pin02_capture.CONTAINER})
        template = '\n' + (pair.HERE / native.TEMPLATE).read_text()
        self.assertTrue(plan[pin02_capture.CONTAINER].endswith(template))
        container = plan[pin02_capture.CONTAINER][:-len(template)].replace(native.INSTALL,
            '        pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN01InputSnapshot() })')
        container = container.replace('\n    private var pin08Accessibility: PIN08AccessibilityObserver?', '')
        self.assertEqual(container, baseline[pin02_capture.CONTAINER])
        self.assertNotIn('PIN01TouchObserver', plan[pin02_capture.CONTAINER])
        self.assertEqual(plan[pin02_capture.DRIVER], baseline[pin02_capture.DRIVER])
        self.assertNotIn('testPIN07Synthetic', plan[pin02_capture.DRIVER])
        self.assertNotIn('testPIN08Synthetic', plan[pin02_capture.DRIVER])

    def test_exact_original_commands_and_failed_only_exports(self):
        self.assertEqual(boundary.test_command(['xcodebuild'], Path('/r')),
                         run_pin03.test_command(['xcodebuild'], Path('/r')))
        command = ' '.join(boundary.test_command(['xcodebuild'], Path('/r')))
        for forbidden in ('iterations', 'retry', 'PIN07', 'PIN08', 'skip-testing'):
            self.assertNotIn(forbidden, command)
        methods = {t.removeprefix('iosAppUITests/') + '()': 'Passed' for t in run_pin03.TESTS}
        with self.assertRaises(ValueError):
            boundary.failed_method_exports(Path('/r'), Path('/d'), methods)

    def test_multiple_explicit_entry_points_reject_before_argument_or_platform_checks(self):
        for other in ('synthetic', 'current', 'accessibility'):
            with self.assertRaises(ValueError):
                run_pin03.main(native_boundary=True, **{other: True})

    def test_source_and_template_mutations_reject_before_execution(self):
        renderer = boundary.renderer_for_profile(boundary.PROFILE, boundary.SOURCE, pair.RUNTIME, pair.DEVICE)
        for p in pair.MODIFIED:
            sources = earlier.originals(); sources[p] += '\n'
            with self.assertRaises(ValueError):
                renderer(sources, pair.INPUTS, arm='compose')

    def test_successful_software_fixture_has_zero_exports_and_is_not_qualification(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            self.assertEqual(boundary.accept_native_export(out, evidence(out)),
                             'NATIVE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_failed_software_fixture_requires_both_original_and_native_companions(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            self.assertEqual(boundary.accept_native_export(out, evidence(out, failed=True)),
                             'NATIVE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_success_cannot_export_or_accept_companion_rows(self):
        for mutate in (
            lambda o,s: earlier.write(o / 'compose/native-accessibility-foreign.json', {}),
            lambda o,s: s['arms'][0].update(companion_captures=[{}]),
            lambda o,s: earlier.write(o / 'compose/capture-foreign.json', {}),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutate(out, state)
                with self.assertRaises((ValueError, KeyError)):
                    boundary.accept_native_export(out, state)

    def test_foreign_source_runtime_mode_arm_inputs_and_unreaped_children_reject(self):
        for mutate in (
            lambda o,s: s.update(profile=current.PROFILE), lambda o,s: s.update(source=pair.SOURCE),
            lambda o,s: s.update(runtime='com.apple.CoreSimulator.SimRuntime.iOS-26-2'),
            lambda o,s: s['arms'][0].update(arm='native'), lambda o,s: s['arms'][0].update(cleanup='FAIL'),
            lambda o,s: s['commands'][0].update(direct_child_reaped=False),
            lambda o,s: earlier.write(o / 'compose/inputs.json', {}),
            lambda o,s: earlier.write(o / 'compose/product.json', {'foreign': 'a' * 64}),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutate(out, state)
                with self.assertRaises((ValueError, KeyError)):
                    boundary.accept_native_export(out, state)

    def test_missing_duplicate_tampered_or_incomplete_failed_native_companion_reject(self):
        for mutate in (
            lambda o,s: next((o / 'compose').glob('native-accessibility-*.json')).unlink(),
            lambda o,s: earlier.write(o / 'compose/native-accessibility-duplicate.json', {}),
            lambda o,s: s['arms'][0].update(companion_captures=[]),
            lambda o,s: earlier.write(next((o / 'compose').glob('native-accessibility-*.json')), {}),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out, failed=True); mutate(out, state)
                with self.assertRaises((ValueError, KeyError, FileNotFoundError)):
                    boundary.accept_native_export(out, state)


if __name__ == '__main__':
    unittest.main()
