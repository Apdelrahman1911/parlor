"""Combined-process rejection controls; fixtures are not native observations."""
import copy
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

import pin06_public_setup_pair as pair
import pin13_original_cohort as cohort
import pin14_original_resources as resources
import pin15_combined_xcode as combined
import run_pin03

old = sys.modules.get('_parlor_original_resource_controls') or importlib.import_module('test_pin14_original_resources')


def evidence(out, failed=False):
    state = old.evidence(out, failed)
    state['profile'] = combined.PROFILE
    state['build_profile'].update(combined_xcode=True, combined_timeout_seconds=3600,
                                 product_manifest_stage='after-combined-command')
    row = state['arms'][0]
    row.update(combined_exit=row['test_exit'], product_manifest_stage='after-combined-command',
               build_success_evidence='all23-executed-native-outcomes')
    row['kernel_resources'].pop('after-build')
    base = ['/usr/bin/xcodebuild', '-project', 'iosApp/iosApp.xcodeproj', '-scheme', 'iosApp',
            '-configuration', 'Debug', '-sdk', 'iphonesimulator',
            '-destination', 'platform=iOS Simulator,id=' + row['simulator'],
            '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1',
            '-destination-timeout', '120']
    state['commands'] = [p for p in state['commands'] if p['label'] not in ('compose-build', 'compose-tests')]
    state['commands'].append(dict(label='compose-build-and-tests', exit_code=row['test_exit'],
        command=combined.test_command(base, out/'compose/tests.xcresult'), timeout_seconds=3600,
        status='EXITED', direct_child_reaped=True, signals=[]))
    return state


class CombinedXcodeControls(unittest.TestCase):
    def test_application_and_entire_driver_are_byte_identical_to_accepted_pin14(self):
        args = ({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS)
        self.assertEqual(combined.renderer_for_profile(combined.PROFILE, combined.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose'),
            resources.renderer_for_profile(resources.PROFILE, resources.SOURCE, pair.RUNTIME, pair.DEVICE)(*args, arm='compose'))
        self.assertIs(combined.verify_results, cohort.verify_results)
        self.assertIs(combined.build_environment, resources.build_environment)

    def test_exact_case_order_and_single_combined_command_without_retry_or_timeout_changes(self):
        original = cohort.test_command(['/usr/bin/xcodebuild', '-quiet'], Path('/result'))
        command = combined.test_command(['/usr/bin/xcodebuild', '-quiet'], Path('/result'))
        self.assertEqual(command, ['test' if p == 'test-without-building' else p for p in original])
        self.assertEqual(command.count('test'), 1)
        self.assertEqual(run_pin03.completed_exit_code('COMBINED_XCODE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'), 0)
        self.assertEqual(run_pin03.completed_exit_code('COMBINED_XCODE_QUALIFIED'), 1)

    def test_complete_original_success_or_retained_target_failure_requires_all_custody(self):
        for failed in (False, True):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                self.assertEqual(combined.accept_native_export(out, evidence(out, failed)),
                                 'COMBINED_XCODE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')

    def test_replayed_separate_unknown_duplicate_longer_or_retargeted_commands_reject(self):
        def argv(state):
            return next(p for p in state['commands'] if p['label'] == 'compose-build-and-tests')['command']
        mutations = (
            lambda s: s['commands'].append(dict(label='compose-build', exit_code=0)),
            lambda s: s['commands'].append(copy.deepcopy(s['commands'][-1])),
            lambda s: s['commands'][-1].update(exit_code=1),
            lambda s: s['commands'][-1].update(timeout_seconds=3601),
            lambda s: s['commands'][-1].update(direct_child_reaped=False),
            lambda s: argv(s).append('-retry-tests-on-failure'),
            lambda s: argv(s).append('test-without-building'),
            lambda s: argv(s).append('-only-testing:iosAppUITests/Foreign/testUnknown'),
            lambda s: argv(s).__setitem__(argv(s).index('Debug'), 'Release'),
            lambda s: s['arms'][0].update(product_manifest_stage='before-tests'),
            lambda s: s['arms'][0].update(build_success_evidence='assumed'),
            lambda s: s['build_profile'].update(combined_xcode=False),
            lambda s: s['arms'][0]['kernel_resources'].pop('after-tests'),
        )
        for mutation in mutations:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out); mutation(state)
                with self.assertRaises(ValueError): combined.accept_native_export(out, state)

    def test_wrong_profile_source_arm_native_order_or_original_evidence_rejects(self):
        for profile, source, runtime, device in (
            (resources.PROFILE, combined.SOURCE, pair.RUNTIME, pair.DEVICE),
            (combined.PROFILE, 'a'*40, pair.RUNTIME, pair.DEVICE),
            (combined.PROFILE, combined.SOURCE, 'com.apple.CoreSimulator.SimRuntime.iOS-26-2', pair.DEVICE),
        ):
            with self.assertRaises(ValueError): combined.renderer_for_profile(profile, source, runtime, device)
        for mutation in (
            lambda o, s: (o/'compose/test.log').write_text(''),
            lambda o, s: (o/'compose/product.json').write_text('{}'),
            lambda o, s: next((o/'compose').glob('native-root-*.json')).unlink(),
        ):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve(); state = evidence(out, True); mutation(out, state)
                with self.assertRaises((ValueError, FileNotFoundError)): combined.accept_native_export(out, state)


if __name__ == '__main__':
    unittest.main()
