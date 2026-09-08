"""Synthetic failure-only diagnostics, not observations of an iOS app."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import external_image_provenance as provenance
import run_normal_ios_launch as runner

EXE = Path('/Users/fixture/Library/owned-device/Parlor.app/Parlor')
PID = 32109


def raw(path=EXE, pid=PID):
    return f'Process:         Parlor [{pid}]\nPath:            {path}\n\nUNRELATED_STACK_SECRET\n'


class HeaderDiagnosticTests(unittest.TestCase):
    def test_exact_headers_are_only_diagnostics_and_do_not_retain_paths_or_stacks(self):
        value = provenance.header_diagnostic(raw(), PID, EXE)
        self.assertTrue(value['original_header_predicate_matches'])
        self.assertFalse(value['proves_provenance'])
        self.assertIn('decoded_text_sha256', value)
        self.assertNotIn('raw_observation_sha256', value)
        self.assertEqual(1, value['path_headers'])
        self.assertTrue(value['paths'][0]['exact_attested_path'])
        encoded = json.dumps(value)
        for excluded in ('UNRELATED_STACK_SECRET', str(EXE), 'fixture', 'Parlor'):
            self.assertNotIn(excluded, encoded)

    def test_public_redaction_shapes_remain_rejected_by_original_header_validator(self):
        value = raw('/Users/USER/*/Parlor.app/Parlor')
        result = provenance.header_diagnostic(value, PID, EXE)
        self.assertFalse(result['original_header_predicate_matches'])
        self.assertIn({'public_marker': 'USER'}, result['paths'][0]['component_shape'])
        self.assertIn({'public_marker': '*'}, result['paths'][0]['component_shape'])
        with self.assertRaises(RuntimeError): provenance.verify_header(value, PID, EXE)

    def test_unknown_text_components_are_hashed_instead_of_disclosed(self):
        value = raw('/Users/OTHER_PRIVATE_NAME/secret-folder/Parlor.app/Parlor')
        value = value.replace('Parlor [', 'PRIVATE_PROCESS_NAME [')
        result = provenance.header_diagnostic(value, PID, EXE)
        encoded = json.dumps(result)
        for excluded in ('OTHER_PRIVATE_NAME', 'secret-folder', 'PRIVATE_PROCESS_NAME'):
            self.assertNotIn(excluded, encoded)
        self.assertFalse(result['original_header_predicate_matches'])
        self.assertTrue(result['parsed_pids_match_exact_target'])

    def test_duplicate_missing_and_wrong_pid_headers_do_not_become_success(self):
        for text in (raw(pid=1), raw() + raw(), raw().replace('Path:', 'Missing:'),
                     raw().replace('Process:', 'Missing:')):
            with self.subTest(text=text):
                self.assertFalse(provenance.header_diagnostic(text, PID, EXE)['original_header_predicate_matches'])
                with self.assertRaises(RuntimeError): provenance.verify_header(text, PID, EXE)

    def test_diagnostic_input_headers_and_component_counts_remain_bounded(self):
        for text in (None, '', raw() * 9, raw('/' + 'x' * 4096), raw('/' * 129)):
            with self.subTest(text=str(text)[:50]), self.assertRaises(RuntimeError):
                provenance.header_diagnostic(text, PID, EXE)
        with self.assertRaises(RuntimeError): provenance.header_diagnostic(raw(), PID, EXE, budget=4)

    def test_runner_keeps_parser_failure_and_only_preserves_sanitized_receipt(self):
        with tempfile.TemporaryDirectory(prefix='parlor-header-fixture-') as temp:
            destination = Path(temp).resolve()
            path = destination / 'owned-raw.txt'
            path.write_text(raw('/Users/USER/*/Parlor.app/Parlor'))
            lane = object.__new__(runner.Lane)
            lane.destination, lane.receipt = destination, {}
            failure = RuntimeError('original parser rejection')
            parse = Mock(side_effect=failure)
            with self.assertRaises(RuntimeError) as caught:
                lane.parse_external_observation('sample', path, PID, EXE, parse)
            self.assertIs(failure, caught.exception)
            record = destination / 'sample-header-failure.json'
            self.assertTrue(record.is_file())
            self.assertEqual(runner.digest(record), lane.receipt['sample_header_failure_sha256'])
            self.assertFalse(json.loads(record.read_text())['proves_provenance'])
            self.assertNotIn('provenance_status', lane.receipt)

    def test_diagnostic_write_failure_cannot_replace_or_hide_original_failure(self):
        lane = object.__new__(runner.Lane)
        lane.destination, lane.receipt = Path('/not-written'), {}
        failure = RuntimeError('original parser rejection')
        with patch.object(Path, 'read_text', return_value=raw()), \
             patch.object(runner, 'write_json', side_effect=OSError('private diagnostic error')):
            with self.assertRaises(RuntimeError) as caught:
                lane.parse_external_observation('vmmap', Path('/unused'), PID, EXE, Mock(side_effect=failure))
        self.assertIs(failure, caught.exception)
        self.assertEqual({'vmmap_header_diagnostic_error': 'OSError'}, lane.receipt)

    def test_successful_parser_does_not_emit_or_consume_diagnostics(self):
        lane = object.__new__(runner.Lane)
        lane.receipt = {}
        result = object()
        with patch.object(Path, 'read_text', return_value=raw()), patch.object(runner, 'header_diagnostic') as diagnostic:
            self.assertIs(result, lane.parse_external_observation('sample', Path('/unused'), PID, EXE,
                                                                  Mock(return_value=result)))
        diagnostic.assert_not_called()
        self.assertEqual({}, lane.receipt)


if __name__ == '__main__':
    unittest.main()
