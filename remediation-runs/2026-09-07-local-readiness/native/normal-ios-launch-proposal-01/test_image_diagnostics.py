"""Pure synthetic format/negative/mutation controls; not native observations."""
import copy
import hashlib
import inspect
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import external_image_diagnostics as diagnostic
import external_image_provenance as provenance
from test_owned_tool_paths import (APP, CONTAINER, DEVICE, EXE, HOME, PID, RELATIVES,
                                 alias, header, image_row, inventory, sample, vmmap)


def artifact_index(artifacts, path):
    return sorted(artifacts).index(str(path))


class ImageDiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.artifacts = inventory()
        self.policy = provenance.OwnedToolPaths(self.artifacts, HOME, DEVICE, APP)

    def describe(self, raw, tool='sample', **options):
        return diagnostic.image_diagnostic(raw, tool, self.artifacts, tool_paths=self.policy, **options)

    def assert_failure_only_private(self, result, *secrets):
        self.assertEqual('FAILURE_ONLY_EXTERNAL_IMAGE_FORMAT', result['kind'])
        self.assertIs(result['proves_provenance'], False)
        self.assertNotIn('status', result)
        text = json.dumps(result)
        self.assertLessEqual(len((json.dumps(result, indent=2) + '\n').encode()), diagnostic.MAX_OUTPUT_BYTES)
        for secret in (*self.artifacts.keys(), HOME, *secrets):
            self.assertNotIn(secret, text)
        for row in result['candidates']:
            self.assertIn(row['image_name'], diagnostic.IMAGE_NAMES | {None})

    def test_exact_rows_report_only_inventory_indexes_and_equality_not_authority(self):
        result = self.describe(sample())
        self.assert_failure_only_private(result)
        self.assertEqual(3, len(result['candidates']))
        self.assertEqual('SORTED_EXACT_INVENTORY_PATHS', result['artifact_index_order'])
        value_hash = hashlib.sha256(json.dumps(self.artifacts, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        self.assertEqual(value_hash, result['inventory_value_sha256'])
        for row, path in zip(result['candidates'], self.artifacts):
            index = artifact_index(self.artifacts, path)
            self.assertEqual([index], row['path']['exact_artifact_indexes'])
            self.assertEqual([], row['path']['owned_presentation_artifact_indexes'])
            self.assertEqual('EXACT_BOUND', row['path']['classification'])
            self.assertEqual([index], row['uuid']['matching_artifact_indexes'])
            self.assertTrue(row['uuid']['matches_bound_path_uuid'])
            self.assertTrue(row['interval']['ordered_nonzero'])

    def test_user_only_existing_policy_remains_explicit_and_default_stays_exact(self):
        result = self.describe(sample(redacted=True))
        self.assert_failure_only_private(result)
        for row, path in zip(result['candidates'], self.artifacts):
            self.assertEqual([], row['path']['exact_artifact_indexes'])
            self.assertEqual([artifact_index(self.artifacts, path)], row['path']['owned_presentation_artifact_indexes'])
            self.assertEqual('OWNED_USER_PRESENTATION', row['path']['classification'])
        default = diagnostic.image_diagnostic(sample(redacted=True), 'sample', self.artifacts)
        self.assertTrue(all(row['path']['classification'] == 'UNBOUND' for row in default['candidates']))
        with self.assertRaises(RuntimeError): provenance.parse_sample(sample(redacted=True), PID, EXE, self.artifacts)

    def test_foreign_user_container_and_copied_origin_never_become_bound(self):
        framework = str(APP / RELATIVES[2])
        for foreign in (framework.replace(HOME, '/Users/PRIVATE_OTHER_USER'),
                        framework.replace(CONTAINER, '99999999-aaaa-bbbb-cccc-dddddddddddd'),
                        '/unowned/PRIVATE_BUILD/ComposeApp.framework/ComposeApp',
                        '/unowned/PRIVATE FOLDER/ComposeApp.framework/ComposeApp'):
            raw = sample().replace(framework, foreign)
            result = self.describe(raw)
            self.assert_failure_only_private(result, 'PRIVATE_OTHER_USER', 'PRIVATE_BUILD', 'PRIVATE FOLDER', foreign)
            row = result['candidates'][-1]
            self.assertEqual('UNBOUND', row['path']['classification'])
            self.assertEqual([], row['path']['exact_artifact_indexes'])
            self.assertEqual([], row['path']['owned_presentation_artifact_indexes'])
            self.assertEqual([artifact_index(self.artifacts, framework)], row['uuid']['matching_artifact_indexes'])
            self.assertFalse(row['uuid']['matches_bound_path_uuid'])
            with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_unknown_components_are_hashed_and_known_component_indexes_are_exact(self):
        raw = sample().replace(str(APP / RELATIVES[2]), '/Users/PRIVATE_SENTINEL/*/ComposeApp')
        result = self.describe(raw)
        self.assert_failure_only_private(result, 'PRIVATE_SENTINEL')
        shape = result['candidates'][-1]['path']['component_shape']
        self.assertIn({'public_marker': '*'}, shape)
        self.assertIn(dict(unknown_sha256=hashlib.sha256(b'PRIVATE_SENTINEL').hexdigest(), bytes=16), shape)
        expected = str(APP / RELATIVES[2]).split('/')
        matched = shape[-1]['known_components']
        self.assertEqual([dict(artifact_index=artifact_index(self.artifacts, APP / RELATIVES[2]),
                               component_indexes=[expected.index('ComposeApp')])], matched)

    def test_unknown_uuid_never_escapes_and_does_not_match_known_inventory(self):
        unknown = '99999999-aaaa-bbbb-cccc-dddddddddddd'
        raw = sample().replace(self.artifacts[str(EXE)]['uuid'], unknown)
        result = self.describe(raw)
        self.assert_failure_only_private(result, unknown)
        self.assertTrue(result['candidates'][0]['uuid']['syntax_matches'])
        self.assertEqual([], result['candidates'][0]['uuid']['matching_artifact_indexes'])
        self.assertFalse(result['candidates'][0]['uuid']['matches_bound_path_uuid'])
        with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_malformed_uuid_address_and_owned_trailing_text_are_diagnostic_not_repaired(self):
        valid = image_row(alias(EXE), 0, self.artifacts[str(EXE)])
        for malformed in (valid.replace('<00000001-', '<INVALID-'), valid.replace('0x100000', 'INVALID'),
                          'malformed ' + alias(EXE) + ' PRIVATE_TRAILING_SYMBOL\n'):
            raw = sample(redacted=True) + malformed
            result = self.describe(raw)
            self.assert_failure_only_private(result, 'PRIVATE_TRAILING_SYMBOL')
            self.assertFalse(result['candidates'][-1]['strict_row_syntax_matches'])
            self.assertTrue(result['candidates'][-1]['contains_bound_form_artifact_indexes'])
            with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_path_redaction_angle_markers_cannot_corrupt_recognized_uuid_metadata(self):
        framework = str(APP / RELATIVES[2])
        raw = sample().replace(framework, '/Users/<redacted>/*/ComposeApp')
        result = self.describe(raw)
        row = result['candidates'][-1]
        self.assertTrue(row['strict_row_syntax_matches'])
        self.assertTrue(row['uuid']['syntax_matches'])
        self.assertEqual([artifact_index(self.artifacts, framework)], row['uuid']['matching_artifact_indexes'])
        self.assertEqual('UNBOUND', row['path']['classification'])

    def test_duplicate_raw_alias_rows_retain_both_candidates_without_deduplication(self):
        raw = sample(redacted=True) + image_row(str(EXE), 0, self.artifacts[str(EXE)])
        result = self.describe(raw)
        self.assertEqual(4, len(result['candidates']))
        first, last = result['candidates'][0], result['candidates'][-1]
        self.assertEqual(first['path']['owned_presentation_artifact_indexes'], last['path']['exact_artifact_indexes'])
        with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_missing_or_duplicate_table_never_scans_other_sections(self):
        for raw in (sample().replace('Binary Images:', 'OTHER_SECTION:'), sample() + '\nBinary Images:\n' + sample()):
            result = self.describe(raw)
            self.assertEqual('NOT_SCANNED_MISSING_OR_AMBIGUOUS_TABLE', result['scan_status'])
            self.assertEqual([], result['candidates'])
            self.assertEqual(0, result['lines_scanned'])
            with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_pre_table_owned_stack_ignored_post_table_malformed_owned_row_recorded(self):
        stack = 'PRIVATE_STACK PRIVATE_SYMBOL ' + str(EXE) + '\n'
        raw = sample().replace('\nBinary Images:\n', '\n' + stack + '\nBinary Images:\n') + 'malformed ' + str(EXE) + '\n'
        result = self.describe(raw)
        self.assert_failure_only_private(result, 'PRIVATE_STACK', 'PRIVATE_SYMBOL')
        self.assertEqual(4, len(result['candidates']))
        self.assertFalse(result['candidates'][-1]['strict_row_syntax_matches'])

    def test_unrelated_rows_and_named_symbols_outside_relevant_image_paths_are_not_retained(self):
        extra = '0xabc - 0xfff +Parlor (PRIVATE_SYMBOL) <99999999-2222-3333-4444-555555555555> /PRIVATE_SYSTEM/libOther\n'
        raw = sample() + extra
        result = self.describe(raw)
        self.assert_failure_only_private(result, 'PRIVATE_SYSTEM', 'PRIVATE_SYMBOL', '/PRIVATE_SYSTEM/libOther')
        self.assertEqual(3, len(result['candidates']))

    def test_zero_reversed_and_overwide_intervals_are_visible_but_still_rejected(self):
        for start, end in (('0x0', '0x103fff'), ('0x104000', '0x103fff'), ('0x10000000000000000', '0x10000000000001000')):
            raw = sample().replace('0x100000 - 0x103fff', start + ' - ' + end)
            result = self.describe(raw)
            self.assertFalse(result['candidates'][0]['interval']['ordered_nonzero'])
            with self.assertRaises(RuntimeError): provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)
        self.assertIsNone(result['candidates'][0]['interval']['start'])
        self.assertIsNone(result['candidates'][0]['interval']['end'])

    def test_copied_framework_uuid_relation_keeps_origins_and_different_bytes_distinct(self):
        artifacts = copy.deepcopy(self.artifacts)
        embedded = str(APP / RELATIVES[2])
        copied = HOME + '/Projects/PRIVATE_COPY/ComposeApp.framework/ComposeApp'
        artifacts[copied] = dict(artifacts[embedded], origin='owned-copy-build', sha256='f' * 64)
        policy = provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
        observed = {path: row for path, row in artifacts.items() if path != embedded}
        raw = sample(observed)
        result = diagnostic.image_diagnostic(raw, 'sample', artifacts, tool_paths=policy)
        self.assert_failure_only_private(result, copied, 'PRIVATE_COPY')
        row = result['candidates'][-1]
        self.assertEqual([artifact_index(artifacts, copied)], row['path']['exact_artifact_indexes'])
        self.assertEqual(sorted([artifact_index(artifacts, copied), artifact_index(artifacts, embedded)]), row['uuid']['matching_artifact_indexes'])
        selected = provenance.parse_sample(raw, PID, EXE, artifacts, tool_paths=policy)
        self.assertFalse(selected[copied]['same_file_bytes_as_embedded'])
        self.assertEqual('owned-copy-build', selected[copied]['origin'])

    def test_vmmap_without_successful_sample_has_no_selected_or_uuid_claim(self):
        result = self.describe(vmmap(redacted=True), 'vmmap')
        self.assert_failure_only_private(result)
        self.assertFalse(result['sample_selection_available'])
        self.assertEqual(3, len(result['candidates']))
        for row in result['candidates']:
            self.assertEqual([], row['successful_sample_interval_relations'])
            self.assertNotIn('uuid', row)
        with self.assertRaises(RuntimeError): self.describe(vmmap(), 'vmmap', selected={})

    def test_vmmap_compares_only_to_supplied_successful_sample_ranges(self):
        selected = provenance.parse_sample(sample(), PID, EXE, self.artifacts, tool_paths=self.policy)
        result = self.describe(vmmap(), 'vmmap', selected=selected)
        self.assertTrue(result['sample_selection_available'])
        for row in result['candidates']:
            self.assertTrue(row['successful_sample_interval_relations'][0]['same_start'])
            self.assertTrue(row['successful_sample_interval_relations'][0]['end_within_sample'])
        for span, key in (('301000-303000', 'same_start'), ('300000-309000', 'end_within_sample')):
            raw = vmmap().replace('300000-302000', span)
            result = self.describe(raw, 'vmmap', selected=selected)
            self.assertFalse(result['candidates'][-1]['successful_sample_interval_relations'][0][key])
            with self.assertRaises(RuntimeError): provenance.bind_vmmap(raw, PID, EXE, selected, tool_paths=self.policy)
        boundary_sample = sample().replace('0x300000 - 0x303fff', '0xffffffffffffe000 - 0xffffffffffffffff')
        selected = provenance.parse_sample(boundary_sample, PID, EXE, self.artifacts, tool_paths=self.policy)
        boundary_vmmap = vmmap().replace('300000-302000', 'ffffffffffffe000-10000000000000000')
        self.assertEqual('PASS', provenance.bind_vmmap(boundary_vmmap, PID, EXE, selected, tool_paths=self.policy)['status'])
        result = self.describe(boundary_vmmap, 'vmmap', selected=selected)
        self.assertEqual(2**64, result['candidates'][-1]['interval']['end'])
        self.assertTrue(result['candidates'][-1]['successful_sample_interval_relations'][0]['end_within_sample'])

    def test_vmmap_unknown_missing_duplicate_and_malformed_text_rows_remain_rejected(self):
        selected = provenance.parse_sample(sample(redacted=True), PID, EXE, self.artifacts, tool_paths=self.policy)
        raw = vmmap(redacted=True)
        variants = [raw.replace('300000-302000', 'INVALID-RANGE'),
                    raw.replace(alias(APP / RELATIVES[2]), '/unowned/PRIVATE_FRAMEWORK/ComposeApp'),
                    raw + vmmap().splitlines()[2] + '\n', '\n'.join(raw.splitlines()[:-1]) + '\n']
        for text in variants:
            result = self.describe(text, 'vmmap', selected=selected)
            self.assert_failure_only_private(result, 'PRIVATE_FRAMEWORK')
            with self.assertRaises(RuntimeError): provenance.bind_vmmap(text, PID, EXE, selected, tool_paths=self.policy)
        self.assertEqual(2, len(result['candidates']))
        for prefix, kind in (('__TEXT_EXEC ', 'ALTERNATE_TEXT_EXEC'), ('__TEXT:', 'UNKNOWN_TEXT_PREFIX'),
                             ('__TEXT_PRIVATE_PREFIX ', 'UNKNOWN_TEXT_PREFIX')):
            text = raw.replace('__TEXT ', prefix)
            result = self.describe(text, 'vmmap', selected=selected)
            self.assert_failure_only_private(result, 'PRIVATE_PREFIX')
            self.assertEqual(3, len(result['candidates']))
            self.assertTrue(all(row['mapping_kind'] == kind for row in result['candidates']))
            self.assertTrue(all(not row['interval']['syntax_matches'] for row in result['candidates']))
            with self.assertRaises(RuntimeError): provenance.bind_vmmap(text, PID, EXE, selected, tool_paths=self.policy)

    def test_vmmap_non_text_maps_and_unrelated_private_paths_are_excluded(self):
        raw = vmmap() + '__DATA 100000-101000 [4K] rw-/rw- /PRIVATE_DATA/Parlor\n' + \
            '__TEXT 200000-201000 [4K] r-x/r-x /PRIVATE_SYSTEM/libOther\n'
        result = self.describe(raw, 'vmmap')
        self.assert_failure_only_private(result, 'PRIVATE_DATA', 'PRIVATE_SYSTEM')
        self.assertEqual(3, len(result['candidates']))

    def test_input_tool_context_and_output_budgets_cannot_be_weakened(self):
        for raw, options in ((None, {}), ('', {}), (sample(), {'budget': 4}),
                             (sample(), {'budget': diagnostic.MAX_INPUT_BYTES + 1}),
                             (sample(), {'output_budget': 4}),
                             (sample(), {'output_budget': diagnostic.MAX_OUTPUT_BYTES + 1}),
                             (sample(), {'budget': True})):
            with self.subTest(options=options), self.assertRaises(RuntimeError): self.describe(raw, **options)
        with self.assertRaises(RuntimeError): self.describe(sample(), 'other')
        with self.assertRaises(RuntimeError): self.describe(sample(), selected={})

    def test_relevant_row_line_path_and_component_limits_are_explicit(self):
        row = image_row(str(EXE), 0, self.artifacts[str(EXE)])
        cases = [header() + '\nBinary Images:\n' + row * (diagnostic.MAX_CANDIDATES + 1),
                 header() + '\nBinary Images:\n' + '\n' * (diagnostic.MAX_LINES + 1),
                 sample() + 'x' * (diagnostic.MAX_LINE_BYTES + 1),
                 sample().replace(str(APP / RELATIVES[2]), '/' + 'x' * 4096 + '/ComposeApp'),
                 sample().replace(str(APP / RELATIVES[2]), '/' + 'x/' * 128 + 'ComposeApp')]
        for text in cases:
            with self.subTest(size=len(text)), self.assertRaises(RuntimeError): self.describe(text)

    def test_repeated_known_components_cannot_amplify_diagnostic_memory_without_bound(self):
        artifacts = {('/' + 'same/' * 70 + Path(path).name): row for path, row in self.artifacts.items()}
        target = next(iter(artifacts))
        raw = header() + '\nBinary Images:\n' + image_row(target, 0, artifacts[target])
        with self.assertRaisesRegex(RuntimeError, 'component relationship budget'):
            diagnostic.image_diagnostic(raw, 'sample', artifacts)

    def test_literal_asterisk_temp_path_is_not_an_authorized_copy_or_derived_alias(self):
        framework = str(APP / RELATIVES[2])
        for origin in ('owned-copy-build', 'owned-derived-app'):
            artifacts = copy.deepcopy(self.artifacts)
            owned = '/private/var/folders/aa/KNOWN_TEMP_ROOT/T/owned-copy/ComposeApp.framework/ComposeApp'
            artifacts[owned] = dict(artifacts[framework], origin=origin)
            policy = provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
            presented = '/private/var/folders/*/owned-copy/ComposeApp.framework/ComposeApp'
            observed = sample().replace(framework, presented)
            result = diagnostic.image_diagnostic(observed, 'sample', artifacts, tool_paths=policy)
            row = result['candidates'][-1]
            self.assertEqual('UNBOUND', row['path']['classification'])
            self.assertIn({'public_marker': '*'}, row['path']['component_shape'])
            self.assertIsNone(policy.resolve(presented))
            self.assertNotIn('KNOWN_TEMP_ROOT', json.dumps(result))
            with self.assertRaises(RuntimeError): provenance.parse_sample(observed, PID, EXE, artifacts, tool_paths=policy)

    def test_changed_inventory_and_invalid_sample_references_are_not_trusted(self):
        altered = copy.deepcopy(self.artifacts)
        altered[str(EXE)]['sha256'] = 'f' * 64
        with self.assertRaises(RuntimeError): diagnostic.image_diagnostic(sample(), 'sample', altered, tool_paths=self.policy)
        with self.assertRaises(RuntimeError): diagnostic.image_diagnostic(sample(), 'sample', self.artifacts, tool_paths=object())
        selected = provenance.parse_sample(sample(), PID, EXE, self.artifacts)
        for field, value in (('sha256', 'f' * 64), ('sample_start', True), ('sample_end_inclusive', -1)):
            bad = copy.deepcopy(selected); bad[str(EXE)][field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError): self.describe(vmmap(), 'vmmap', selected=bad)

    def test_collector_neither_mutates_inputs_nor_queries_files_processes_or_accounts(self):
        before = copy.deepcopy((self.artifacts, self.policy.aliases))
        with patch.object(Path, 'resolve', side_effect=AssertionError('no filesystem query')), \
             patch.object(Path, 'stat', side_effect=AssertionError('no filesystem query')), \
             patch.object(Path, 'read_text', side_effect=AssertionError('no filesystem query')), \
             patch('subprocess.run', side_effect=AssertionError('no native invocation')), \
             patch('subprocess.Popen', side_effect=AssertionError('no native invocation')), \
             patch('pwd.getpwuid', side_effect=AssertionError('no account query')), \
             patch.object(provenance.OwnedToolPaths, '__init__', side_effect=AssertionError('no new authority')):
            result = self.describe(sample())
        self.assertEqual(before, (self.artifacts, self.policy.aliases))
        self.assert_failure_only_private(result)

    def test_failure_only_contract_kills_provenance_true_mutation(self):
        source = inspect.getsource(diagnostic.image_diagnostic)
        self.assertEqual(1, source.count('proves_provenance=False'))
        namespace = dict(vars(diagnostic))
        exec(compile(source.replace('proves_provenance=False', 'proves_provenance=True'), '<synthetic-marker-mutation>', 'exec'), namespace)
        mutated = namespace['image_diagnostic'](sample(), 'sample', self.artifacts, tool_paths=self.policy)
        with self.assertRaises(AssertionError): self.assert_failure_only_private(mutated)

    def test_privacy_contract_kills_unknown_component_plaintext_mutation(self):
        source = inspect.getsource(diagnostic._path_shape)
        original = 'unknown_sha256=hashlib.sha256(part.encode()).hexdigest()'
        self.assertEqual(1, source.count(original))
        namespace = dict(vars(diagnostic))
        exec(compile(source.replace(original, 'unknown_plaintext=part'), '<synthetic-privacy-mutation>', 'exec'), namespace)
        with patch.object(diagnostic, '_path_shape', namespace['_path_shape']):
            mutated = self.describe(sample().replace(str(APP / RELATIVES[2]), '/PRIVATE_COMPONENT/ComposeApp'))
        with self.assertRaises(AssertionError): self.assert_failure_only_private(mutated, 'PRIVATE_COMPONENT')


if __name__ == '__main__':
    unittest.main()
