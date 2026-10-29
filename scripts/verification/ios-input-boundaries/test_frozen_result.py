"""Frozen-artifact and extraction controls; no native calls or GitHub writes."""
import hashlib
import io
import json
from pathlib import Path
import stat
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
    state = dict(source=review.SOURCE, harness=review.HEAD, arms=[
        dict(arm='original', result='JOURNEYS_FAILED', test_exit=65, cleanup='PASS'), {}, {}])
    if state_change:
        state_change(state)
    summary = dict(totalTestCount=2, failedTests=1, passedTests=1, skippedTests=0,
                   testFailures=[{'testIdentifierString': review.METHOD}])
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as z:
        z.writestr('state.json', json.dumps(state))
        z.writestr('original/summary.json', json.dumps(summary))
        z.writestr('original/tests.xcresult/Data/public', b'original failed result')
        z.writestr('compose/tests.xcresult/ignored', b'not extracted')
        if extra:
            z.writestr(extra, b'unsafe')
    return buffer.getvalue()


class FrozenResultControls(unittest.TestCase):
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
                       lambda s: s['arms'][0].update(cleanup='FAIL')):
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
