"""Synthetic strict-parser regressions; root runs them, never native/device evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import l08_functional_receipts as receipts
import l08_receipts as strict
from test_l08_receipts import matrix as strict_matrix
from test_native_readiness import TOKEN, framework_inventory


def reading(value='missing', error=None):
    return dict(query='returned', dictionary_present=value != 'unavailable',
                key_present=value not in {'missing', 'unavailable'}, value=value,
                native_error=error if error is not None else dict(present=False, domain='none', code=0))


def matrix(value='missing'):
    records, scenario, log = strict_matrix()
    scenario['scenario'] = receipts.SCENARIO
    for record in records:
        record['scenario'] = receipts.SCENARIO
        observation = record['observation']
        del observation['status']
        del observation['checks']
        observation.update(kind=receipts.KIND, functional_status='PASS',
            functional_checks={key: True for key in receipts.FUNCTIONAL_CHECKS[record['action']]},
            complete_comparisons=[dict(row, attributes=reading(value), comparison='PASS' if value == 'complete' else 'FAIL')
                                  for row in receipts.comparison_plan(record['boot_ordinal'], record['action'])])
    return records, scenario, log.replace('PARLOR_L08_STORAGE_BOOT ', receipts.BOOT_PREFIX)


def failed_record(boot=1, action='save', retained=1):
    source = next(row for row in matrix()[0] if row['boot_ordinal'] == boot and row['action'] == action)
    value = source['observation']
    del value['functional_checks']
    value.update(functional_status='FAIL', stage='metadata-encrypted-header', reason='fixture_or_boundary_failure')
    value['complete_comparisons'] = value['complete_comparisons'][:retained]
    return source


class L08FunctionalReceiptTests(unittest.TestCase):
    def verify(self, records=None, scenario=None, log=None):
        defaults = matrix()
        return receipts.verify_storage_functional(records if records is not None else defaults[0],
            scenario if scenario is not None else defaults[1], TOKEN, log if log is not None else defaults[2],
            'disabled', {}, {}, [framework_inventory()])

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_missing_complete_is_explicit_failure_after_every_functional_obligation(self, bind):
        result = self.verify()
        self.assertEqual(result['functional_gate'], 'PASS')
        self.assertEqual(result['status'], 'FUNCTIONAL_MATRIX_VERIFIED')
        self.assertEqual(result['complete_comparison_gate'], 'FAIL')
        self.assertEqual(result['complete_comparison_count'], 26)
        self.assertEqual(result['successful_complete_comparisons'], 0)
        self.assertEqual(len(result['unsuccessful_complete_comparisons']), 26)
        self.assertTrue(all(row['observed_value'] == 'missing' for row in result['unsuccessful_complete_comparisons']))
        self.assertEqual(result['original_strict_l08'], 'NOT_SATISFIED_BY_COMPANION')
        self.assertFalse(result['physical_or_store_evidence'])
        self.assertEqual(bind.call_count, 13)
        self.assertTrue(all(len(call.args[2]) == 1 for call in bind.call_args_list))

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_matching_complete_is_not_original_l08_execution(self, _bind):
        result = self.verify(records=matrix('complete')[0])
        self.assertEqual(result['complete_comparison_gate'], 'PASS')
        self.assertEqual(result['successful_complete_comparisons'], 26)
        self.assertEqual(result['unsuccessful_complete_comparisons'], [])
        self.assertNotEqual(result['status'], 'PASS')
        self.assertEqual(result['original_strict_l08'], 'NOT_SATISFIED_BY_COMPANION')

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_each_actual_noncomplete_value_remains_an_unsuccessful_comparison(self, _bind):
        for value in ('missing', 'unavailable', 'null', 'none', 'unless-open', 'until-first-authentication',
                      'when-user-inactive', 'other-string', 'non-string'):
            with self.subTest(value=value):
                result = self.verify(records=matrix(value)[0])
                self.assertEqual(result['complete_comparison_gate'], 'FAIL')
                self.assertEqual(result['successful_complete_comparisons'], 0)

    def test_exact_six_sites_thirteen_checkpoints_and_twenty_six_comparisons(self):
        records = matrix()[0]
        rows = [row for record in records for row in record['observation']['complete_comparisons']]
        self.assertEqual(len(records), 20)
        self.assertEqual(len(rows), 26)
        self.assertEqual({row['site'] for row in rows}, {
            'save', 'load', 'dual-before-legacy-seed', 'dual-after-tag-damage', 'legacy-load-dual', 'retained-dual'})
        for boot, action in ((9, 'legacy-seed'), (10, 'legacy-load')):
            selected = next(record for record in records if record['boot_ordinal'] == boot and record['action'] == action)
            self.assertEqual(len(selected['observation']['complete_comparisons']), 4)
        self.assertEqual(len(receipts.functional_result_names()), 20)
        self.assertFalse(set(receipts.functional_result_names()) & set(strict.storage_result_names()))

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_missing_reordered_reused_extra_and_unfinished_processes_fail(self, _bind):
        records, scenario, log = matrix()
        for rows in (records[:-1], records + [records[-1]], [records[1], records[0]] + records[2:]):
            with self.assertRaises(RuntimeError): self.verify(records=rows)
        wrong = copy.deepcopy(records); wrong[1]['process_boot'] = wrong[0]['process_boot']
        with self.assertRaises(RuntimeError): self.verify(records=wrong)
        for change in (lambda value: value.update(completed=False),
                       lambda value: value['observations'][2].update(boot=value['observations'][0]['boot'])):
            bad = copy.deepcopy(scenario); change(bad)
            with self.assertRaises(RuntimeError): self.verify(scenario=bad)
        for altered in ('', log + '\n' + log.splitlines()[0], log.replace('"foreground": true', '"foreground": false', 1),
                        log.replace('"alert_present": false', '"alert_present": true', 1),
                        log.replace(receipts.BOOT_PREFIX, 'PARLOR_L08_STORAGE_BOOT ')):
            with self.assertRaises(RuntimeError): self.verify(log=altered)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_every_functional_guard_false_missing_wrong_type_or_unknown_is_rejected(self, _bind):
        base = matrix()[0]
        for index, record in enumerate(base):
            for key in record['observation']['functional_checks']:
                for replacement in (False, 1, None):
                    rows = copy.deepcopy(base); rows[index]['observation']['functional_checks'][key] = replacement
                    with self.subTest(index=index, key=key, value=replacement), self.assertRaises(RuntimeError):
                        self.verify(records=rows)
                rows = copy.deepcopy(base); del rows[index]['observation']['functional_checks'][key]
                with self.assertRaises(RuntimeError): self.verify(records=rows)
            rows = copy.deepcopy(base); rows[index]['observation']['functional_checks']['unknown'] = True
            with self.assertRaises(RuntimeError): self.verify(records=rows)

    @mock.patch.object(receipts, 'bind_loaded_images')
    def test_every_comparison_is_required_ordered_and_cannot_imply_a_fake_pass(self, _bind):
        base = matrix()[0]
        for index, record in enumerate(base):
            for row_index, row in enumerate(record['observation']['complete_comparisons']):
                for key, replacement in (('comparison', 'PASS'), ('ordinal', True), ('site', 'save-again'),
                                         ('alias', 'another-seat'), ('target', 'other'), ('secret', 'synthetic')):
                    altered = copy.deepcopy(base)
                    altered[index]['observation']['complete_comparisons'][row_index][key] = replacement
                    with self.subTest(index=index, row=row_index, key=key), self.assertRaises(RuntimeError):
                        self.verify(records=altered)
                altered = copy.deepcopy(base); del altered[index]['observation']['complete_comparisons'][row_index]
                with self.assertRaises(RuntimeError): self.verify(records=altered)
            altered = copy.deepcopy(base)
            altered[index]['observation']['complete_comparisons'].append(copy.deepcopy(base[0]['observation']['complete_comparisons'][0]))
            with self.assertRaises(RuntimeError): self.verify(records=altered)

    def test_getter_shapes_strict_booleans_and_error_codes_are_not_coerced(self):
        base = matrix()[0][0]
        changes = [lambda value: value.update(query='observer-exception'),
                   lambda value: value.update(key_present=True), lambda value: value.update(value='complete'),
                   lambda value: value.update(volume_support='supported'),
                   lambda value: value['native_error'].update(description='not-allowed'),
                   lambda value: value['native_error'].update(code=2 ** 63),
                   lambda value: value['native_error'].update(code=True)]
        for key in ('dictionary_present', 'key_present'):
            for integer in (0, 1):
                changes.append(lambda value, key=key, integer=integer: value.update({key: integer}))
        for integer in (0, 1):
            changes.append(lambda value, integer=integer: value['native_error'].update(present=integer))
        for index, change in enumerate(changes):
            record = copy.deepcopy(base); change(record['observation']['complete_comparisons'][0]['attributes'])
            with self.subTest(index=index), self.assertRaises(RuntimeError):
                receipts.validate_preservable_functional_operation(record)
        valid = copy.deepcopy(base)
        valid['observation']['complete_comparisons'][0]['attributes']['native_error'] = dict(
            present=True, domain='posix', code=-(2 ** 63))
        receipts.validate_preservable_functional_operation(valid)

    def test_failure_retains_only_a_valid_completed_prefix_without_becoming_success(self):
        for count in range(5):
            row = failed_record(9, 'legacy-seed', retained=count)
            receipts.validate_preservable_functional_operation(row)
            for reason in ('cancelled', 'fixture_or_boundary_failure'):
                row['observation']['reason'] = reason
                receipts.validate_preservable_functional_operation(row)
        bad = failed_record(9, 'legacy-seed', retained=2)
        bad['observation']['complete_comparisons'].reverse()
        with self.assertRaises(RuntimeError): receipts.validate_preservable_functional_operation(bad)
        for reason in (None, [], {}, 1):
            bad = failed_record(); bad['observation']['reason'] = reason
            with self.assertRaises(RuntimeError): receipts.validate_preservable_functional_operation(bad)
        records = matrix()[0]; records[0] = failed_record()
        with self.assertRaises(RuntimeError): self.verify(records=records)

    def test_original_strict_parser_rejects_companion_even_when_all_comparisons_match(self):
        for value in ('complete', 'missing'):
            rows, scenario, log = matrix(value)
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                strict.verify_storage(rows, scenario, TOKEN, log, 'disabled', {}, {}, [framework_inventory()])
            with self.assertRaises(RuntimeError): strict.validate_preservable_operation(rows[0], 'l08-storage')
            forged = copy.deepcopy(rows[0]); forged['scenario'] = 'l08-storage'
            with self.assertRaises(RuntimeError): strict.validate_preservable_operation(forged, 'l08-storage')
        with self.assertRaises(RuntimeError): receipts.validate_preservable_functional_operation(strict_matrix()[0][0])

    @mock.patch.object(receipts, 'bind_loaded_images', side_effect=RuntimeError('actual artifact mismatch'))
    def test_real_artifact_binding_failure_is_not_swallowed(self, _bind):
        with self.assertRaisesRegex(RuntimeError, 'artifact mismatch'): self.verify()

    def test_context_signing_identity_and_secret_fields_are_closed(self):
        base = matrix()[0][0]
        for key, replacement in (('schema_version', True), ('boot_ordinal', True), ('run_token', 'wrong'),
                                 ('signing_mode', 'store'), ('signing_mode', []), ('signing_mode', {}),
                                 ('scenario', 'l08-storage'), ('seed', 'synthetic')):
            row = copy.deepcopy(base); row[key] = replacement
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                receipts.validate_preservable_functional_operation(row)
        for key, replacement in (('schema_version', True), ('command_ordinal', True), ('run_token', TOKEN + '0'),
                                 ('physical_or_store_evidence', True), ('kind', 'l08-storage'), ('role_map', {})):
            row = copy.deepcopy(base); row['observation'][key] = replacement
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                receipts.validate_preservable_functional_operation(row)

    def test_preservation_is_exclusive_bounded_safe_and_never_invents_missing_steps(self):
        with tempfile.TemporaryDirectory(prefix='parlor-functional-evidence-') as raw:
            root = Path(raw).resolve(); parent = root / 'app/tmp'; parent.mkdir(parents=True)
            dest = root / 'evidence'; dest.mkdir()
            name = receipts.functional_result_names()[0]
            row = failed_record()
            path = parent / name; path.write_text(json.dumps(row))
            self.assertEqual(receipts.preserve_functional_evidence(parent.parent, dest), [name])
            self.assertEqual(json.loads((dest / name).read_text()), row)
            self.assertEqual(len(list(dest.iterdir())), 1)
            with self.assertRaises(FileExistsError): receipts.preserve_functional_evidence(parent.parent, dest)
            for data in ('{}', '{"a":1,"a":2}', ' ' * 16385):
                path.write_text(data)
                with self.assertRaises(RuntimeError): receipts.preserve_functional_evidence(parent.parent, dest)
            path.unlink(); path.symlink_to(dest / name)
            with self.assertRaises(RuntimeError): receipts.preserve_functional_evidence(parent.parent, dest)

    def test_four_comparison_records_fit_original_inner_and_outer_limits(self):
        self.assertEqual((receipts.INNER_MAXIMUM, receipts.OUTER_MAXIMUM), (4096, 16384))
        for row in matrix('until-first-authentication')[0]:
            for comparison in row['observation']['complete_comparisons']:
                comparison['attributes']['native_error'] = dict(present=True, domain='cocoa', code=-(2 ** 63))
            receipts.validate_preservable_functional_operation(row)
            inner = json.dumps(row['observation'], separators=(',', ':'), ensure_ascii=False).encode()
            outer = json.dumps(row, separators=(',', ':'), ensure_ascii=False).encode()
            self.assertLessEqual(len(inner), 4096)
            self.assertLessEqual(len(outer), 16384)


if __name__ == '__main__': unittest.main()
