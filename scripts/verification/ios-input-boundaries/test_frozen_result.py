"""Frozen-artifact and extraction controls; no native calls or GitHub writes."""
import hashlib
import io
import json
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import review_frozen_result as review


def metadata():
    run = dict(id=review.RUN, head_sha=review.HEAD, run_attempt=1, status='completed', event='push',
               path='.github/workflows/ios-input-investigation.yml',
               head_branch='diagnostic/ios-input-probe-2026-10-29',
               repository={'full_name': review.REPOSITORY}, head_repository={'full_name': review.REPOSITORY})
    artifact = dict(id=review.ARTIFACT, name=f'ios-input-investigation-{review.RUN}-1', expired=False,
                    size_in_bytes=review.BYTES, digest='sha256:' + review.DIGEST,
                    workflow_run={'id': review.RUN, 'head_sha': review.HEAD})
    return run, artifact


def archive(extra=None, state_change=None):
    state = dict(source=review.SOURCE, harness=review.HEAD,
                 original_inputs={review.DRIVER: review.DRIVER_SHA256}, arms=[
        dict(arm='original', result='JOURNEYS_FAILED', test_exit=65, cleanup='PASS'), {}, {}])
    if state_change:
        state_change(state)
    summary = dict(totalTestCount=2, failedTests=1, passedTests=1, skippedTests=0,
                   testFailures=[{'testIdentifierString': review.METHOD}])
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as z:
        z.writestr('state.json', json.dumps(state))
        z.writestr('original/inputs.json', json.dumps(state['original_inputs']))
        z.writestr('original/summary.json', json.dumps(summary))
        z.writestr('original/tests.xcresult/Data/public', b'original failed result')
        z.writestr('compose/tests.xcresult/ignored', b'not extracted')
        if extra:
            z.writestr(extra, b'unsafe')
    return buffer.getvalue()


class FrozenResultControls(unittest.TestCase):
    def test_log_reads_use_the_supported_get_log_not_nonexistent_export_logs(self):
        for kind in ('action', 'console'):
            self.assertEqual(review.log_command(Path('/owned/result.xcresult'), kind),
                ['get', 'log', '--path', '/owned/result.xcresult', '--type', kind])
        for kind in ('build', 'all', '--retry', None):
            with self.assertRaises(ValueError):
                review.log_command(Path('/owned/result.xcresult'), kind)

    def test_exact_frozen_driver_six_field_output_is_not_confused_with_newer_snapshot_driver(self):
        # 841a0b06 used sequential queries, not snapshot(); there is no error-code field.
        sample = ('at=1.0, querySeconds=2.0, frame=nil, enabled=false, '
                  'stable=false, hittable=nil')
        line = 'Public control settlement: ' + sample
        self.assertEqual(review.settlement_lines({'emittedOutput': line}), [line])
        with self.assertRaises(ValueError):
            review.settlement_lines({'emittedOutput': line + ', snapshotErrorCode=nil'})

    def test_schema_is_bound_to_the_actual_frozen_driver_bytes_and_emit_fields(self):
        raw = subprocess.check_output(['git', 'show', review.SOURCE + ':' + review.DRIVER], cwd=review.ROOT)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), review.DRIVER_SHA256)
        driver = raw.decode()
        body = driver.split('private func tapSettledControl(', 1)[1].split('private func verifyKeyboardCycle(', 1)[0]
        for field in ('at=', 'querySeconds=', 'frame=', 'enabled=', 'stable=', 'hittable='):
            self.assertEqual(body.count(field), 1)
        self.assertNotIn('snapshotErrorCode=', body)
        self.assertNotIn('control.snapshot()', body)

    def test_stderr_tokens_are_closed_vocabulary_and_failures_still_do_not_qualify(self):
        raw = b'Error: No console log found. /private/secret/path credential=abcd1234'
        summary = review.stderr_summary(raw)
        self.assertEqual(summary['bytes'], len(raw))
        self.assertEqual(summary['sha256'], hashlib.sha256(raw).hexdigest())
        self.assertEqual(summary['tokens'][:7], ['error', ':', 'no', 'console', 'log', 'found', '.'])
        for secret in ('private', 'secret', 'path', 'credential', 'abcd1234'):
            self.assertNotIn(secret, json.dumps(summary))
        self.assertFalse(review.stderr_summary(b'x' * 4097)['bounded'])
        self.assertEqual(review.stderr_summary(b'x' * 4097)['tokens'], [])
        row = dict(exit_code=1, direct_child_reaped=True)
        calls = []
        sample = 'Public control settlement: at=1.0, querySeconds=2.0, frame=nil, enabled=false, stable=false, hittable=nil'
        def read(kind):
            calls.append(kind)
            if kind == 'console':
                raise review.FrozenLogReadError(row, raw)
            return {'emittedOutput': sample}
        result = review.collect_log_samples(read)
        self.assertEqual(calls, ['action', 'console'])
        self.assertEqual(result['lines'], [sample])
        self.assertEqual(result['errors']['console']['command']['stderr'], summary)
        self.assertFalse(result['complete'])

    def test_only_bounded_public_numeric_settlement_grammar_can_be_retained(self):
        sample = ('at=1.2, querySeconds=0.5, frame=Optional((24.0, 200.0, 100.0, 48.0)), '
                  'enabled=true, stable=false, hittable=nil')
        line = 'Public control settlement: ' + sample
        self.assertEqual(review.settlement_lines({'items': [{'message': line}]}), [line])
        self.assertEqual(review.settlement_lines({'message': 'unrelated arbitrary text'}), [])
        for changed in (sample + ', secret=private', sample.replace('1.2', 'nan'),
                        sample.replace('hittable=nil', 'hittable=private'),
                        sample.replace('Optional((24.0, 200.0, 100.0, 48.0))', 'Optional(())'),
                        '; '.join([sample] * 13)):
            with self.assertRaises(ValueError):
                review.settlement_lines({'message': 'Public control settlement: ' + changed})
        with self.assertRaises(ValueError):
            review.settlement_lines([line] * 17)

    def test_quoted_source_marker_is_not_misidentified_as_emitted_public_output(self):
        source = '            print("Public control settlement: " + samples.joined(separator: "; "))'
        sample = ('at=1.0, querySeconds=2.0, frame=nil, enabled=false, '
                  'stable=false, hittable=nil')
        line = 'Public control settlement: ' + sample
        self.assertEqual(review.settlement_lines({'source': source, 'emittedOutput': line}), [line])
        self.assertEqual(review.settlement_lines({'source': source}), [])

    def test_rejected_candidate_metadata_never_contains_the_rejected_payload(self):
        with self.assertRaises(review.SettlementSampleError) as caught:
            review.settlement_lines({'emittedOutput': 'Public control settlement: secret=private'})
        self.assertEqual(caught.exception.metadata, dict(bytes=14, samples=1, field='emittedOutput'))
        self.assertNotIn('private', str(caught.exception))

    def test_rejected_action_view_does_not_suppress_console_and_cannot_qualify(self):
        calls = []
        sample = ('Public control settlement: at=1.0, querySeconds=2.0, frame=nil, '
                  'enabled=false, stable=false, hittable=nil')
        def read(kind):
            calls.append(kind)
            return {'text': 'Public control settlement: malformed'} if kind == 'action' else {'text': sample}
        result = review.collect_log_samples(read)
        self.assertEqual(calls, ['action', 'console'])
        self.assertEqual(result['lines'], [sample])
        self.assertEqual(list(result['errors']), ['action'])
        self.assertFalse(result['complete'])

    def test_empty_and_failed_reads_remain_incomplete_after_both_declared_views(self):
        self.assertFalse(review.collect_log_samples(lambda _: {'items': []})['complete'])
        calls = []
        def failed(kind):
            calls.append(kind)
            raise ValueError('simulated read failure')
        result = review.collect_log_samples(failed)
        self.assertEqual(calls, ['action', 'console'])
        self.assertFalse(result['complete'])
        self.assertEqual(list(result['errors']), ['action', 'console'])

    def test_actual_console_absence_is_explicit_and_cannot_substitute_for_action_output(self):
        sample = 'Public control settlement: at=1.0, querySeconds=2.0, frame=nil, enabled=false, stable=false, hittable=nil'
        row = dict(status='EXITED', exit_code=1, direct_child_reaped=True)
        stderr = b'Error: No console log available\n'
        self.assertEqual(hashlib.sha256(stderr).hexdigest(),
                         '7edd4c9be5a9e23098ec98cc71122a9fbd83e1dead265129a4869665f21454c5')
        calls = []
        def read(kind):
            calls.append(kind)
            if kind == 'console':
                return review.log_payload(row, b'', stderr, kind)
            return {'emittedOutput': sample}
        result = review.collect_log_samples(read)
        self.assertEqual(calls, ['action', 'console'])
        self.assertTrue(result['complete'])
        self.assertEqual(result['errors'], {})
        self.assertEqual(result['counts'], {'action': 1, 'console': 0})
        self.assertEqual(result['unavailable']['console']['stderr']['sha256'], hashlib.sha256(stderr).hexdigest())
        def no_action(kind):
            return {'items': []} if kind == 'action' else review.log_payload(row, b'', stderr, kind)
        self.assertFalse(review.collect_log_samples(no_action)['complete'])
        self.assertFalse(review.collect_log_samples(lambda kind:
            {'items': []} if kind == 'action' else {'emittedOutput': sample})['complete'])

    def test_console_absence_requires_exact_error_exit_empty_stdout_and_owned_completion(self):
        row = dict(status='EXITED', exit_code=1, direct_child_reaped=True)
        stderr = b'Error: No console log available\n'
        for changed, raw, error, kind in (
            (row, b'', stderr, 'action'),
            (row | {'status': 'TIMEOUT'}, b'', stderr, 'console'),
            (row | {'direct_child_reaped': False}, b'', stderr, 'console'),
            (row | {'exit_code': 2}, b'', stderr, 'console'),
            (row, b'partial stdout', stderr, 'console'),
            (row, b'', stderr.rstrip(), 'console'),
            (row, b'', stderr + b'private suffix', 'console'),
            (row, b'', b'Error: Result bundle is missing\n', 'console'),
            (row | {'exit_code': 0}, b'{}', stderr, 'console'),
        ):
            with self.subTest(kind=kind, status=changed['status']), self.assertRaises(review.FrozenLogReadError) as caught:
                review.log_payload(changed, raw, error, kind)
            self.assertNotIsInstance(caught.exception, review.ConsoleLogUnavailable)
        success = dict(status='EXITED', exit_code=0, direct_child_reaped=True)
        self.assertEqual(review.log_payload(success, b'{"items": []}', b'', 'action'), {'items': []})
        with self.assertRaises(ValueError):
            review.log_payload(success, b'not JSON', b'', 'action')

    def test_metadata_accepts_only_the_exact_completed_producer_and_artifact(self):
        run, artifact = metadata()
        review.validate_metadata(run, artifact)
        for field, value in (('id', 1), ('head_sha', 'a' * 40), ('run_attempt', 2), ('status', 'in_progress'),
                             ('event', 'pull_request'), ('head_branch', 'main'), ('repository', {'full_name': 'foreign'})):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.validate_metadata(run | {field: value}, artifact)
        for field, value in (('id', 1), ('expired', True), ('digest', 'sha256:' + 'a' * 64),
                             ('size_in_bytes', 1), ('workflow_run', {'id': 1, 'head_sha': review.HEAD})):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.validate_metadata(run, artifact | {field: value})

    def test_extraction_retains_only_original_result_bytes_and_hashes(self):
        raw = archive()
        with tempfile.TemporaryDirectory() as tmp, patch.object(review, 'DIGEST', hashlib.sha256(raw).hexdigest()):
            root = Path(tmp)
            state, summary, manifest = review.unpack(raw, root)
            self.assertEqual(summary['failedTests'], 1)
            self.assertEqual(state['harness'], review.HEAD)
            self.assertEqual(list(manifest), ['original/tests.xcresult/Data/public'])
            self.assertEqual((root / next(iter(manifest))).read_bytes(), b'original failed result')
            self.assertFalse((root / 'compose').exists())
            with self.assertRaises(ValueError):
                review.unpack(raw, root)

    def test_bad_digest_source_and_original_result_cannot_be_reclassified(self):
        for change in (None, lambda s: s.update(source='a' * 40),
                       lambda s: s['arms'][0].update(result='JOURNEYS_PASSED'),
                       lambda s: s['arms'][0].update(cleanup='FAIL'),
                       lambda s: s['original_inputs'].update({review.DRIVER: 'a' * 64})):
            raw = archive(state_change=change)
            digest = 'a' * 64 if change is None else hashlib.sha256(raw).hexdigest()
            with tempfile.TemporaryDirectory() as tmp, patch.object(review, 'DIGEST', digest), self.assertRaises(ValueError):
                review.unpack(raw, Path(tmp))

    def test_unsafe_paths_links_and_size_bounds_reject_before_writing_any_input(self):
        link = zipfile.ZipInfo('original/tests.xcresult/link')
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        for extra in ('../outside', '/absolute', 'original//ambiguous', link):
            raw = archive(extra=extra)
            with tempfile.TemporaryDirectory() as tmp, patch.object(review, 'DIGEST', hashlib.sha256(raw).hexdigest()):
                with self.assertRaises(ValueError):
                    review.unpack(raw, Path(tmp))
                self.assertEqual(list(Path(tmp).iterdir()), [])
        raw = archive()
        with tempfile.TemporaryDirectory() as tmp, patch.object(review, 'MAX_BYTES', 1), self.assertRaises(ValueError):
            review.unpack(raw, Path(tmp))


if __name__ == '__main__':
    unittest.main()
