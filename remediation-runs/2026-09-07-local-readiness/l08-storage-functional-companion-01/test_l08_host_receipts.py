"""Strict parser regressions. All fixtures synthetic; root owns test execution."""
import copy
import json
import unittest
from unittest import mock

import l08_receipts as receipts
from test_native_readiness import (TOKEN, IMAGES, event, preferences, framework_observation,
                                   framework_inventory, loader_environment, PRODUCT_PATH)


def host_matrix():
    records, launches = [], []
    scenario = dict(schemaVersion=6, runToken=TOKEN, scenario='l08-host', completed=True, observations=[])
    for boot in range(1, 4):
        process = '%08x-1234-5678-9000-000000000017' % boot
        scenario['observations'].append(event(len(scenario['observations']) + 1, process, 'before_main'))
        for index, action in enumerate(receipts.HOST_PLAN, 1):
            language = receipts._host_language(index)
            pref = preferences() if language == 'system' else preferences(language, owned=True)
            observed = event(len(scenario['observations']) + 1, process, preference=pref)
            cycles = receipts._host_cycles(index)
            composition = json.loads(observed['composition'])
            composition.update(backgroundCallbacks=cycles, foregroundCallbacks=cycles, inactiveCallbacks=cycles)
            observed['composition'] = json.dumps(composition)
            scenario['observations'].append(observed)
            records.append(dict(schema_version=1, scenario='l08-host', signing_mode='disabled', run_token=TOKEN,
                process_boot=process, boot_ordinal=boot, action=action, loaded_app_images=IMAGES[:],
                loaded_compose_framework=framework_observation(), loader_environment=loader_environment(),
                observation_sequence=observed['ordinal'],
                observation=dict(schema_version=1, status='PASS', run_token=TOKEN, boot_ordinal=boot,
                    command_ordinal=index, action=action, variant=receipts.HOST_VARIANTS[boot - 1],
                    checks={key: True for key in receipts.host_checks(action)}, physical_or_store_evidence=False,
                    host=dict(mounts=1 if index < 9 else 2,
                        disposals=2 if action == 'leave' else 1 if index >= 9 else 0,
                        direction='rtl' if language == 'ar' else 'ltr', background_cycles=cycles,
                        foreground_cycles=cycles, suspended_checks=cycles, resuming_checks=cycles,
                        room_state='none' if action == 'leave' else 'resuming' if action.startswith('cycle-') else 'active',
                        worker_failure=False))))
        launches.append(dict(schema_version=1, boot_ordinal=boot, process_boot=process, actions=list(receipts.HOST_PLAN),
            foreground=True, alert_present=False, run_token=TOKEN))
    scenario['observations'].append(event(len(scenario['observations']) + 1, process, 'complete'))
    log = '\n'.join('PARLOR_L08_HOST_BOOT ' + json.dumps(row) for row in launches)
    return records, scenario, log


class HostReceiptTests(unittest.TestCase):
    def verify(self, records=None, scenario=None, log=None, inventories=None):
        base = host_matrix()
        return receipts.verify_host(base[0] if records is None else records, base[1] if scenario is None else scenario,
            TOKEN, base[2] if log is None else log, 'disabled', {}, {},
            [framework_inventory()] if inventories is None else inventories)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_complete_parser_fixture_requires_three_independent_native_artifact_bindings(self, binding):
        result = self.verify()
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(binding.call_count, 3)
        self.assertEqual(result['actual_background_foreground_cycles'], 9)
        self.assertEqual(result['suspension_and_resuming_guards'], 18)
        self.assertFalse(result['physical_or_store_evidence'])
        self.assertFalse(result['real_p2pkit_lan_evidence'])
        self.assertEqual(len(receipts.host_result_names()), 42)
        self.assertEqual(len(set(receipts.host_result_names())), 42)

    @mock.patch.object(receipts, 'bind_loaded_images', side_effect=RuntimeError('real binding failed'))
    def test_artifact_mismatch_is_not_swallowed(self, _binding):
        with self.assertRaises(RuntimeError): self.verify()

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_missing_extra_failed_or_reordered_operations_cannot_pass(self, _binding):
        records, _, _ = host_matrix()
        swapped = copy.deepcopy(records); swapped[1], swapped[2] = swapped[2], swapped[1]
        failed = copy.deepcopy(records); failed[2]['observation'] = dict(status='FAIL')
        for rows in (records[:-1], records + [records[-1]], swapped, failed):
            with self.assertRaises(RuntimeError): self.verify(records=rows)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_every_source_invariant_false_missing_unknown_or_nonboolean_is_rejected(self, _binding):
        base, _, _ = host_matrix()
        for index, record in enumerate(base):
            for key in record['observation']['checks']:
                for value in (False, 1, None):
                    changed = copy.deepcopy(base); changed[index]['observation']['checks'][key] = value
                    with self.subTest(index=index, key=key, value=value), self.assertRaises(RuntimeError):
                        self.verify(records=changed)
                changed = copy.deepcopy(base); del changed[index]['observation']['checks'][key]
                with self.assertRaises(RuntimeError): self.verify(records=changed)
        changed = copy.deepcopy(base); changed[0]['observation']['checks']['not-a-check'] = True
        with self.assertRaises(RuntimeError): self.verify(records=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_source_identity_scopes_and_secret_fields_are_closed(self, _binding):
        base, _, _ = host_matrix()
        for key, value in (('schema_version', True), ('signing_mode', 'store'), ('run_token', 'other'),
                           ('boot_ordinal', True), ('process_boot', TOKEN), ('private_role_map', {})):
            changed = copy.deepcopy(base); changed[0][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(records=changed)
        for key, value in (('schema_version', True), ('command_ordinal', True), ('variant', 'unknown'),
                           ('status', 'BLOCKED'), ('physical_or_store_evidence', True), ('private_seed', 0)):
            changed = copy.deepcopy(base); changed[0]['observation'][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(records=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_all_runtime_counters_and_worker_errors_are_verified(self, _binding):
        base, _, _ = host_matrix()
        for index in range(len(base)):
            for key in receipts.HOST_COUNTERS:
                for replacement in (True, -1, base[index]['observation']['host'][key] + 1):
                    changed = copy.deepcopy(base); changed[index]['observation']['host'][key] = replacement
                    with self.subTest(index=index, key=key), self.assertRaises(RuntimeError): self.verify(records=changed)
            for key, replacement in (('worker_failure', True), ('room_state', 'suspended'), ('role_map', {})):
                changed = copy.deepcopy(base); changed[index]['observation']['host'][key] = replacement
                with self.assertRaises(RuntimeError): self.verify(records=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_native_observation_must_be_unique_correct_order_and_same_boot(self, _binding):
        base, _, _ = host_matrix()
        for value in (True, 0, base[0]['observation_sequence'], base[14]['observation_sequence']):
            changed = copy.deepcopy(base); changed[1]['observation_sequence'] = value
            with self.assertRaises(RuntimeError): self.verify(records=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_actual_uikit_callbacks_cannot_be_replaced_by_synthetic_counts(self, _binding):
        records, scenario, _ = host_matrix()
        for key in ('backgroundCallbacks', 'foregroundCallbacks'):
            changed = copy.deepcopy(scenario)
            sequence = records[3]['observation_sequence']
            value = json.loads(changed['observations'][sequence - 1]['composition'])
            value[key] = 0
            changed['observations'][sequence - 1]['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): self.verify(scenario=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_actual_native_compose_preferences_and_system_owner_must_all_agree(self, _binding):
        records, scenario, _ = host_matrix()
        for field, value in (('nativeDirection', 'force_ltr'), ('preferences', preferences())):
            changed = copy.deepcopy(scenario)
            changed['observations'][records[5]['observation_sequence'] - 1][field] = value
            with self.assertRaises(RuntimeError): self.verify(scenario=changed)
        changed = copy.deepcopy(records); changed[5]['observation']['host']['direction'] = 'ltr'
        with self.assertRaises(RuntimeError): self.verify(records=changed)
        changed = copy.deepcopy(scenario)
        changed['observations'][records[9]['observation_sequence'] - 1]['preferences'] = preferences('en', owned=True)
        with self.assertRaises(RuntimeError): self.verify(scenario=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_xctest_missing_duplicate_different_boot_or_failure_cannot_pass(self, _binding):
        _, _, log = host_matrix()
        for changed in ('', log + '\n' + log.splitlines()[0], log.replace('"foreground": true', '"foreground": false', 1),
                        log.replace('"alert_present": false', '"alert_present": true', 1),
                        log.replace('00000001-1234-5678-9000-000000000017', TOKEN, 1)):
            with self.assertRaises(RuntimeError): self.verify(log=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_unobserved_missing_or_duplicate_framework_rows_fail_before_filtering(self, _binding):
        embedded = framework_inventory()
        unrelated = framework_inventory(framework_observation('owned-copy-build', PRODUCT_PATH))
        for inventories in ([], [embedded, embedded], [embedded, unrelated]):
            with self.assertRaises(RuntimeError): self.verify(inventories=inventories)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_each_process_gets_only_its_observed_framework_plus_exact_embedded_inventory(self, binding):
        records, _, _ = host_matrix()
        product = framework_observation('owned-copy-build', PRODUCT_PATH)
        for row in records[14:28]:
            row['loaded_compose_framework'] = product
            row['loaded_app_images'] = [path for path in IMAGES if path != receipts.FRAMEWORK_PATH]
        inventories = [framework_inventory(), framework_inventory(product)]
        self.verify(records=records, inventories=inventories)
        self.assertEqual([len(call.args[3]) for call in binding.call_args_list], [1, 2, 1])
        subset = receipts.framework_subset(records[:14], records, inventories)
        self.assertEqual(subset, [framework_inventory()])
        with self.assertRaises(RuntimeError): receipts.framework_subset(copy.deepcopy(records[:14]), records, inventories)


if __name__ == '__main__': unittest.main()
