"""Parser regressions only. Root runs them in the coordinated lane."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import l08_receipts as receipts
from test_native_readiness import TOKEN, IMAGES, event, framework_observation, framework_inventory, loader_environment


def matrix():
    records, launches = [], []
    scenario = dict(schemaVersion=6, runToken=TOKEN, scenario='l08-storage', completed=True, observations=[])
    for boot, actions in receipts.STORAGE_PLAN.items():
        process = '%08x-1234-5678-9000-000000000009' % boot
        scenario['observations'].append(event(len(scenario['observations']) + 1, process, 'before_main'))
        scenario['observations'].append(event(len(scenario['observations']) + 1, process))
        for ordinal, action in enumerate(actions, 1):
            records.append(dict(schema_version=1, scenario='l08-storage', signing_mode='disabled', run_token=TOKEN,
                process_boot=process, boot_ordinal=boot, action=action, loaded_app_images=IMAGES[:],
                loaded_compose_framework=framework_observation(), loader_environment=loader_environment(),
                observation=dict(schema_version=1, status='PASS', boot_ordinal=boot, command_ordinal=ordinal,
                    run_token=TOKEN, action=action, checks={key: True for key in receipts.STORAGE_CHECKS[action]},
                    physical_or_store_evidence=False)))
        launches.append(dict(schema_version=1, boot_ordinal=boot, process_boot=process, actions=list(actions),
            foreground=True, alert_present=False, run_token=TOKEN))
    scenario['observations'].append(event(len(scenario['observations']) + 1, process, 'complete'))
    return records, scenario, '\n'.join('PARLOR_L08_STORAGE_BOOT ' + json.dumps(row) for row in launches)


class L08ReceiptTests(unittest.TestCase):
    def verify(self, records=None, scenario=None, log=None):
        defaults = matrix()
        return receipts.verify_storage(records if records is not None else defaults[0],
            scenario if scenario is not None else defaults[1], TOKEN, log if log is not None else defaults[2],
            'disabled', {}, {}, [framework_inventory()])

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_complete_parser_fixture_demands_thirteen_independent_real_artifact_binding_calls(self, bind):
        result = self.verify()
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(bind.call_count, 13)
        self.assertTrue(all(len(call.args[2]) == 1 for call in bind.call_args_list))
        self.assertFalse(result['physical_or_store_evidence'])
        self.assertEqual(result['mafia_on_consecutive_nights'], 4)

    @mock.patch.object(receipts, 'bind_loaded_images', side_effect=RuntimeError('actual artifact mismatch'))
    def test_actual_artifact_binding_failure_is_not_swallowed(self, _bind):
        with self.assertRaises(RuntimeError): self.verify()

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_missing_or_extra_operation_cannot_pass(self, _bind):
        records, _, _ = matrix()
        for rows in (records[:-1], records + [records[-1]]):
            with self.subTest(count=len(rows)), self.assertRaises(RuntimeError): self.verify(records=rows)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_reordered_operations_and_wrong_process_cannot_pass(self, _bind):
        rows, _, _ = matrix()
        swapped = copy.deepcopy(rows); swapped[1], swapped[2] = swapped[2], swapped[1]
        wrong = copy.deepcopy(rows); wrong[1]['process_boot'] = rows[0]['process_boot']
        for records in (swapped, wrong):
            with self.assertRaises(RuntimeError): self.verify(records=records)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_every_individual_invariant_false_missing_unknown_or_nonboolean_is_rejected(self, _bind):
        base, _, _ = matrix()
        for index, record in enumerate(base):
            for key in record['observation']['checks']:
                for replacement in (False, 1, None):
                    rows = copy.deepcopy(base); rows[index]['observation']['checks'][key] = replacement
                    with self.subTest(index=index, key=key, value=replacement), self.assertRaises(RuntimeError):
                        self.verify(records=rows)
                rows = copy.deepcopy(base); del rows[index]['observation']['checks'][key]
                with self.assertRaises(RuntimeError): self.verify(records=rows)
            rows = copy.deepcopy(base); rows[index]['observation']['checks']['invented'] = True
            with self.assertRaises(RuntimeError): self.verify(records=rows)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_context_strictness_and_secret_fields(self, _bind):
        base, _, _ = matrix()
        for target, key, value in ((0, 'schema_version', True), (0, 'boot_ordinal', True),
                (1, 'run_token', 'not-this-run'), (1, 'signing_mode', 'store'), (1, 'seed', 'do-not-record')):
            rows = copy.deepcopy(base); rows[target][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(records=rows)
        for key, value in (('schema_version', True), ('command_ordinal', True), ('status', 'BLOCKED'),
                           ('physical_or_store_evidence', True), ('private_state', {})):
            rows = copy.deepcopy(base); rows[0]['observation'][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(records=rows)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_failed_boundary_record_and_failed_or_missing_xctest_are_rejected(self, _bind):
        rows, _, log = matrix()
        rows[0]['observation'] = dict(status='FAIL', reason='fixture_or_boundary_failure')
        with self.assertRaises(RuntimeError): self.verify(records=rows)
        for changed in ('', log + '\n' + log.splitlines()[0], log.replace('"foreground": true', '"foreground": false', 1),
                        log.replace('"alert_present": false', '"alert_present": true', 1)):
            with self.assertRaises(RuntimeError): self.verify(log=changed)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_completed_scenario_and_unique_boots_are_required(self, _bind):
        _, scenario, _ = matrix()
        bad = copy.deepcopy(scenario); bad['completed'] = False
        with self.assertRaises(RuntimeError): self.verify(scenario=bad)
        bad = copy.deepcopy(scenario); bad['observations'][2]['boot'] = bad['observations'][0]['boot']
        with self.assertRaises(RuntimeError): self.verify(scenario=bad)

    def test_result_reader_bounds_duplicate_fields_and_symlink(self):
        with tempfile.TemporaryDirectory(prefix='parlor-l08-parser-') as raw:
            root = Path(raw).resolve()
            good = root / 'owned.json'; good.write_text('{"status":"synthetic"}')
            self.assertEqual(receipts.read_owned_result(good, root), dict(status='synthetic'))
            for payload in (b'', b'{"a":1,"a":2}', b'"not-an-object"', b'\xff', b' ' * 16385):
                good.write_bytes(payload)
                with self.assertRaises(RuntimeError): receipts.read_owned_result(good, root)
            good.write_text('{}')
            link = root / 'link.json'; link.symlink_to(good)
            with self.assertRaises(RuntimeError): receipts.read_owned_result(link, root)
            with self.assertRaises(RuntimeError): receipts.read_owned_result(good, root / 'other')

    def test_result_name_inventory_is_closed_unique_and_matches_twenty_operations(self):
        names = receipts.storage_result_names()
        self.assertEqual(len(names), 20)
        self.assertEqual(len(set(names)), len(names))
        self.assertTrue(all(name.startswith('parlor-l08-storage-') and name.endswith('.json') for name in names))

    def test_both_success_and_closed_failure_observations_are_sanitized_before_preservation(self):
        from test_l08_host_receipts import host_matrix
        for scenario, value in (('l08-storage', matrix()[0][0]), ('l08-host', host_matrix()[0][0])):
            receipts.validate_preservable_operation(value, scenario)
            failed = copy.deepcopy(value)
            failed['observation'] = dict(schema_version=1, status='FAIL', run_token=TOKEN,
                boot_ordinal=failed['boot_ordinal'], action=failed['action'], stage='context', reason='cancelled')
            receipts.validate_preservable_operation(failed, scenario)
            failed['observation']['private_state'] = 'must-not-be-retained'
            with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(failed, scenario)
            changed = copy.deepcopy(value); changed['observation']['private_state'] = 'must-not-be-retained'
            with self.assertRaises(RuntimeError): receipts.validate_preservable_operation(changed, scenario)

    def test_unknown_sensitive_fields_never_reach_durable_evidence(self):
        with tempfile.TemporaryDirectory(prefix='parlor-l08-preserve-reject-') as raw:
            root = Path(raw).resolve(); container = root / 'app'; (container / 'tmp').mkdir(parents=True)
            evidence = root / 'evidence'; evidence.mkdir()
            value = matrix()[0][0]; value['observation']['private_role_map'] = 'synthetic-rejection-only'
            name = receipts.storage_result_names()[0]
            (container / 'tmp' / name).write_text(json.dumps(value))
            with self.assertRaises(RuntimeError): receipts.preserve_l08_evidence(container, evidence)
            self.assertEqual(list(evidence.iterdir()), [])

    def test_closed_failure_preservation_is_exclusive_and_missing_steps_stay_missing(self):
        with tempfile.TemporaryDirectory(prefix='parlor-l08-preserve-failure-') as raw:
            root = Path(raw).resolve(); container = root / 'app'; (container / 'tmp').mkdir(parents=True)
            evidence = root / 'evidence'; evidence.mkdir()
            value = matrix()[0][0]
            value['observation'] = dict(schema_version=1, status='FAIL', run_token=TOKEN,
                boot_ordinal=1, action='save', stage='real-store-save', reason='fixture_or_boundary_failure')
            name = receipts.storage_result_names()[0]
            (container / 'tmp' / name).write_text(json.dumps(value))
            self.assertEqual(receipts.preserve_l08_evidence(container, evidence), [name])
            self.assertEqual(json.loads((evidence / name).read_text()), value)
            self.assertEqual(len(list(evidence.iterdir())), 1)
            with self.assertRaises(FileExistsError): receipts.preserve_l08_evidence(container, evidence)


if __name__ == '__main__': unittest.main()
