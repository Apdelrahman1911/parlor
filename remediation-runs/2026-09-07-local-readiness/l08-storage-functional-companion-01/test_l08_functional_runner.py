"""Synthetic companion orchestration tests; no Gradle, Xcode or Simulator execution."""
import ast
import copy
import importlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import l08_functional_receipts as receipts
import l08_functional_runner as helpers
import run_ios_readiness as runner
from test_l08_functional_receipts import failed_record, matrix
from test_l08_host_receipts import host_matrix
from test_native_readiness import TOKEN, boot_result, framework_inventory, framework_observation, PRODUCT_PATH

HERE = Path(__file__).resolve().parent
CANONICAL = HERE.parents[2] / 'scripts/verification/ios-readiness'


def xctest():
    device = dict(deviceId=TOKEN, architecture='arm64', platform='iOS Simulator')
    summary = dict(result='Passed', totalTestCount=5, passedTests=5, failedTests=0,
                   skippedTests=0, expectedFailures=0, testFailures=[],
                   devicesAndConfigurations=[dict(device=copy.deepcopy(device))])
    tests = dict(devices=[copy.deepcopy(device)], testNodes=[dict(nodeType='Test Suite', children=[
        dict(nodeType='Test Case', nodeIdentifier=name, result='Passed') for name in sorted(runner.EXPECTED_TESTS)])])
    return summary, tests


def receipt(complete='FAIL'):
    return dict(runtime_evidence_status='FUNCTIONAL_COMPANION_VERIFIED', xcodebuild_exit_code=0,
        xctest=dict(result='Passed'), embedded_gradle_stop_status='PASS', probe_harness_status='observation_complete',
        l08_storage_functional=dict(kind=receipts.KIND, status='FUNCTIONAL_MATRIX_VERIFIED', functional_gate='PASS',
            complete_comparison_gate=complete, original_strict_l08='NOT_SATISFIED_BY_COMPANION'),
        l08_host=dict(status='PASS'), native_readiness=dict(storage_health='PASS'),
        os_settings_gate=dict(status='PASS', actual_present_os_preference_coverage=True),
        cleanup_status='PASS', source_unchanged=True, controls_unchanged=True, copied_sources_unchanged=True)


def filename(row):
    if 'scenario' not in row:
        return 'parlor-native-readiness-boot-%d.json' % row['boot_ordinal']
    return 'parlor-%s-%d-%s.json' % (row['scenario'], row['boot_ordinal'], row['action'])


def write_rows(root, rows):
    for row in rows:
        with (root / filename(row)).open('x') as stream:
            stream.write(json.dumps(row))


class L08FunctionalRunnerTests(unittest.TestCase):
    def test_imports_remain_read_only(self):
        with mock.patch('subprocess.Popen', side_effect=AssertionError('Import launched a worker')), \
                mock.patch('pathlib.Path.write_text', side_effect=AssertionError('Import wrote output')):
            importlib.reload(helpers)
            importlib.reload(runner)
        self.assertIsNone(runner.BINDING)
        with self.assertRaises(RuntimeError): runner.control_manifest()

    def test_exact_five_discovered_tests_include_only_the_distinct_companion_method(self):
        summary, tests = xctest()
        result = runner.verify_xctest(summary, tests, TOKEN)
        self.assertEqual(result['total'], 5)
        self.assertEqual(result['method'], 'IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSWithL08FunctionalObservation()')
        self.assertEqual(result['methods'], sorted(runner.EXPECTED_TESTS))
        self.assertEqual(sum(name.startswith('ComposeContainerViewControllerTests/') for name in result['methods']), 4)
        self.assertEqual(result['skipped'], 0)
        for key, value in (('result', 'Failed'), ('totalTestCount', 4), ('passedTests', 4),
                           ('failedTests', 1), ('skippedTests', 1), ('expectedFailures', 1)):
            changed = copy.deepcopy(summary); changed[key] = value
            with self.subTest(summary=key), self.assertRaises(RuntimeError): runner.verify_xctest(changed, tests, TOKEN)
        for key, value in (('deviceId', TOKEN + '0'), ('architecture', 'x86_64'), ('platform', 'iOS')):
            for source in ('summary', 'tests'):
                left, right = copy.deepcopy(summary), copy.deepcopy(tests)
                target = left['devicesAndConfigurations'][0]['device'] if source == 'summary' else right['devices'][0]
                target[key] = value
                with self.subTest(source=source, key=key), self.assertRaises(RuntimeError):
                    runner.verify_xctest(left, right, TOKEN)
        changed = copy.deepcopy(tests)
        case = next(row for row in changed['testNodes'][0]['children'] if row['nodeIdentifier'] == runner.EXPECTED_TEST)
        case['nodeIdentifier'] = 'IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSInvestigation()'
        with self.assertRaises(RuntimeError): runner.verify_xctest(summary, changed, TOKEN)
        for mutate in (lambda rows: rows.pop(), lambda rows: rows.append(copy.deepcopy(rows[0])),
                       lambda rows: rows[0].update(result='Skipped')):
            changed = copy.deepcopy(tests); mutate(changed['testNodes'][0]['children'])
            with self.assertRaises(RuntimeError): runner.verify_xctest(summary, changed, TOKEN)

    def test_available_failure_rows_are_retained_without_accepting_missing_scenarios(self):
        values = [boot_result(1), failed_record(9, 'legacy-seed', retained=2), host_matrix()[0][0]]
        values[-1]['observation'] = dict(schema_version=1, status='FAIL', run_token=TOKEN,
            boot_ordinal=1, action='prepare', stage='context', reason='cancelled')
        with tempfile.TemporaryDirectory(prefix='parlor-functional-available-') as raw:
            root = Path(raw).resolve()
            self.assertEqual(helpers.available_run_records(root, 'disabled'), [])
            write_rows(root, values)
            actual = helpers.available_run_records(root, 'disabled')
            self.assertEqual(actual, values)
            with mock.patch.object(helpers, 'bind_loaded_images', return_value=dict(status='PASS')) as binder:
                result = helpers.bind_available_images(actual, {}, {}, [framework_inventory()])
            self.assertEqual(binder.call_count, 3)
            self.assertEqual(result['status'], 'AVAILABLE_OBSERVATIONS_IMAGE_BOUND')
            self.assertIs(result['matrix_verified'], False)
            self.assertEqual((result['observation_rows'], result['process_groups']), (3, 3))
            self.assertFalse(helpers.runtime_complete(dict(available_observation_image_binding=result), 0))
            self.assertEqual(helpers.classify_final_companion(dict(available_observation_image_binding=result))['status'], 'FAIL')

    def test_complete_available_inventory_has_seventy_rows_and_twenty_four_cold_processes(self):
        values = [boot_result(boot) for boot in range(1, 9)] + matrix()[0] + host_matrix()[0]
        with tempfile.TemporaryDirectory(prefix='parlor-functional-all-rows-') as raw:
            root = Path(raw).resolve(); write_rows(root, values)
            actual = helpers.available_run_records(root, 'disabled')
            self.assertEqual(actual, values)
            with mock.patch.object(helpers, 'bind_loaded_images', return_value=dict(status='PASS')) as binder:
                result = helpers.bind_available_images(actual, {}, {}, [framework_inventory()])
            self.assertEqual(result['observation_rows'], 70)
            self.assertEqual(result['process_groups'], 24)
            self.assertEqual(binder.call_count, 24)
            self.assertTrue(all(len(call.args[2]) == 1 for call in binder.call_args_list))
            self.assertIs(result['matrix_verified'], False)

    def test_available_rows_reject_cross_token_wrong_filename_and_malformed_or_unsafe_results(self):
        original = failed_record()
        with tempfile.TemporaryDirectory(prefix='parlor-functional-available-bad-') as raw:
            root = Path(raw).resolve(); path = root / filename(original)
            for data in ('{}', '{"a":1,"a":2}', ' ' * 16385):
                path.write_text(data)
                with self.assertRaises(RuntimeError): helpers.available_run_records(root, 'disabled')
            wrong = copy.deepcopy(original); wrong['action'] = 'load'; path.write_text(json.dumps(wrong))
            with self.assertRaises(RuntimeError): helpers.available_run_records(root, 'disabled')
            wrong = copy.deepcopy(original); wrong['signing_mode'] = 'adhoc'; path.write_text(json.dumps(wrong))
            with self.assertRaises(RuntimeError): helpers.available_run_records(root, 'disabled')
            for mode in ('store', None, [], {}):
                with self.subTest(mode=mode), self.assertRaises(RuntimeError): helpers.available_run_records(root, mode)
            row = copy.deepcopy(original)
            row['run_token'] = row['observation']['run_token'] = 'feedbeef-1234-5678-9000-000000000001'
            path.write_text(json.dumps(row)); write_rows(root, [boot_result(1)])
            with self.assertRaises(RuntimeError): helpers.available_run_records(root, 'disabled')
            path.unlink(); target = root / 'synthetic-target.json'; target.write_text(json.dumps(original))
            path.symlink_to(target)
            with self.assertRaises(RuntimeError): helpers.available_run_records(root, 'disabled')

    @mock.patch.object(helpers, 'bind_loaded_images', return_value=dict(status='PASS'))
    def test_binding_rejects_cross_token_duplicate_process_and_inconsistent_same_process_images(self, _binder):
        original = [boot_result(1), matrix()[0][0], matrix()[0][1], matrix()[0][2]]
        changes = [
            lambda rows: rows[1].update(process_boot=rows[0]['process_boot']),
            lambda rows: rows[2].update(process_boot=rows[1]['process_boot']),
            lambda rows: rows[3].update(loaded_app_images=rows[3]['loaded_app_images'][:-1]),
            lambda rows: rows[3]['loader_environment']['DYLD_FRAMEWORK_PATH'].update(present=True, redacted_entry_count=1),
            lambda rows: rows[1].update(signing_mode='adhoc'),
            lambda rows: rows[1].update(scenario='not-this-matrix'),
        ]
        for change in changes:
            values = copy.deepcopy(original); change(values)
            with self.assertRaises(RuntimeError): helpers.bind_available_images(values, {}, {}, [framework_inventory()])
        values = copy.deepcopy(original)
        values[1]['run_token'] = values[1]['observation']['run_token'] = 'feedbeef-1234-5678-9000-000000000001'
        with self.assertRaises(RuntimeError): helpers.bind_available_images(values, {}, {}, [framework_inventory()])
        for invalid in ([], [None], original * 18, [original[1], original[1]]):
            with self.assertRaises(RuntimeError): helpers.bind_available_images(invalid, {}, {}, [framework_inventory()])

    def test_real_image_binding_errors_and_unobserved_inventory_are_never_swallowed(self):
        values = [failed_record()]
        with mock.patch.object(helpers, 'bind_loaded_images', side_effect=RuntimeError('real image mismatch')):
            with self.assertRaisesRegex(RuntimeError, 'real image mismatch'):
                helpers.bind_available_images(values, {}, {}, [framework_inventory()])
        extra = framework_inventory(framework_observation('task-owned-build-product', PRODUCT_PATH))
        for inventories in ([], [framework_inventory(), framework_inventory()], [framework_inventory(), extra]):
            with self.assertRaises(RuntimeError): helpers.bind_available_images(values, {}, {}, inventories)

    def test_metadata_failures_allow_independent_functional_subgates_but_never_overall_pass(self):
        for value in ('PASS', 'FAIL'):
            row = receipt(value)
            self.assertTrue(helpers.runtime_complete(row, 0))
            result = helpers.classify_final_companion(row)
            self.assertEqual(result['status'], 'PARTIALLY_VERIFIED')
            self.assertEqual(result['strict_l08_gate'], 'NOT_SATISFIED_BY_COMPANION')
            self.assertIn('Original strict L08', result['remaining_obligations'][0])
            self.assertEqual(any('remain unsuccessful' in item for item in result['remaining_obligations']), value == 'FAIL')
        text = (HERE / 'run_ios_readiness.py').read_text()
        self.assertLess(text.index("receipt['l08_storage_functional'] = verify_storage_functional("),
                        text.index("receipt['l08_host'] = verify_host("))
        between = text[text.index("receipt['l08_storage_functional'] = verify_storage_functional("):
                       text.index("receipt['l08_host'] = verify_host(")]
        self.assertNotIn('complete_comparison_gate', between)

    def test_genuine_runtime_cleanup_or_source_failures_cannot_become_partial_success(self):
        for value in (False, True, None, 1, -1, '0'):
            self.assertFalse(helpers.runtime_complete(receipt(), value))
        runtime_changes = (
            ('xctest', 'result', 'Failed'), ('l08_host', 'status', 'FAIL'),
            ('l08_storage_functional', 'functional_gate', 'FAIL'),
            ('l08_storage_functional', 'status', 'PARTIAL'),
            ('l08_storage_functional', 'original_strict_l08', 'PASS'),
            ('l08_storage_functional', 'complete_comparison_gate', 'UNOBSERVED'),
            ('l08_storage_functional', 'complete_comparison_gate', []),
        )
        for section, key, value in runtime_changes:
            changed = receipt(); changed[section][key] = value
            self.assertFalse(helpers.runtime_complete(changed, 0))
            self.assertEqual(helpers.classify_final_companion(changed)['status'], 'FAIL')
        for key, value in (('runtime_evidence_status', 'PASS'), ('xcodebuild_exit_code', 1),
                ('cleanup_status', 'FAIL'), ('embedded_gradle_stop_status', 'FAIL'),
                ('probe_harness_status', 'incomplete'), ('source_unchanged', False),
                ('controls_unchanged', False), ('copied_sources_unchanged', False),
                ('source_unchanged', 1), ('error', dict(type='SyntheticFailure'))):
            changed = receipt(); changed[key] = value
            self.assertEqual(helpers.classify_final_companion(changed)['status'], 'FAIL')
        for section in ('l08_storage_functional', 'xctest', 'l08_host', 'native_readiness', 'os_settings_gate'):
            changed = receipt(); changed[section] = None
            self.assertEqual(helpers.classify_final_companion(changed)['status'], 'FAIL')
        for invalid in (None, [], {}, False):
            self.assertFalse(helpers.runtime_complete(invalid, 0))
            self.assertEqual(helpers.classify_final_companion(invalid)['status'], 'FAIL')

    def test_native_storage_failure_stays_failed_and_other_missing_obligations_stay_explicit(self):
        changed = receipt(); changed['native_readiness']['storage_health'] = 'FAIL'
        result = helpers.classify_final_companion(changed)
        self.assertEqual(result['status'], 'FAIL')
        self.assertIn('not automatically an application defect', result['readiness_gate_failure'])
        changed = receipt(); changed['native_readiness']['storage_health'] = 'BLOCKED'
        changed['os_settings_gate']['actual_present_os_preference_coverage'] = False
        result = helpers.classify_final_companion(changed)
        self.assertEqual(result['status'], 'PARTIALLY_VERIFIED')
        self.assertTrue(any('storage-health' in item for item in result['remaining_obligations']))
        self.assertTrue(any('OS-managed' in item for item in result['remaining_obligations']))

    def test_original_ownership_build_stop_and_cleanup_implementations_are_unchanged(self):
        original = (CANONICAL / 'run_ios_readiness.py').read_text()
        candidate = (HERE / 'run_ios_readiness.py').read_text()
        left, right = ast.parse(original), ast.parse(candidate)
        def functions(tree):
            return {node.name: ast.dump(node, include_attributes=False) for node in ast.walk(tree)
                    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name != 'main'}
        self.assertEqual(functions(left), functions(right))
        # Full unchanged finalizer region through source/cleanup calculation, not just a stop substring.
        first = '        finally:\n            deferred = []'
        last = "                receipt['status'] = 'PASS' if receipt.get('runtime_evidence_status')"
        end = '                receipt.update(classify_final_companion(receipt))'
        self.assertEqual(original[original.index(first):original.index(last)],
                         candidate[candidate.index(first):candidate.index(end)])
        build = candidate.index("xcode = command(['xcodebuild'")
        stop = candidate.index("stop_gradle('stop-xcode-immediate')", build)
        self.assertIn('finally:', candidate[build:stop])
        preserve = candidate.index('preserve_functional_evidence(container, dest)')
        inventory = candidate.index('available_records = available_run_records(dest, mode)')
        binding = candidate.index("receipt['available_observation_image_binding'] = bind_available_images(")
        outcome = candidate.index("receipt['xctest'] = verify_xctest(")
        self.assertLess(stop, preserve)
        self.assertLess(preserve, inventory)
        self.assertLess(inventory, binding)
        self.assertLess(binding, outcome)
        self.assertIn("'-parallel-testing-enabled', 'NO'", candidate)
        self.assertIn("'-jobs', '1'", candidate)
        self.assertIn("if owner.secondary_errors:", candidate)
        self.assertIn("'cleanup-attested-secondary-fifos', owner.secondary.cleanup", candidate)
        self.assertIn('RuntimeError(\'Explicit simulator ad-hoc artifact signature inspection failed', candidate)


if __name__ == '__main__': unittest.main()
