"""Fail-closed controls for the immutable two-failure public-only reader."""
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import review_public_setup_pair_commit as review

WORKFLOW = '.github/workflows/ios-public-setup-pair-review.yml'
WORKFLOW_SHA256 = 'c949768c410c07d91c249f1e6044165224e275106314fff736454d1f81961688'
BINDINGS = {
    'scripts/verification/ios-input-boundaries/review_public_setup_pair_commit.py':
        '4c39679c5a5fb0e8e4a65e1e614cb666b0d743b543e711c1a35a81dcaf6393af',
    'scripts/verification/ios-input-boundaries/review_public_catalog_commit.py':
        '97c3d176a3adf366156c6aa5bb12f246e6956e4def075e94e0c2f45aa627fe0e',
    'scripts/verification/ios-input-boundaries/review_frozen_result.py':
        '41ba58371a707f1190b86997b1b2e856f5ea5cb86d16eabe993aeec471b5bd22',
    'scripts/verification/ios-input-boundaries/public_attachments.py':
        'ad93b59659be452750d8b7cc5aa02c42547048c364557d0251f913a936729ec8',
    'scripts/ci/native_process_probe.py':
        'f6e31687332680e5e41af99d7bb05ce34898993186386f720a885de379fd7681',
}


def verify_workflow(raw):
    review.require(hashlib.sha256(raw).hexdigest() == WORKFLOW_SHA256)


def metadata():
    run = dict(id=review.RUN, head_sha=review.SOURCE, run_attempt=1, status='completed',
               conclusion='failure', event='pull_request',
               path='.github/workflows/production-verification.yml',
               head_branch='fix/authored-display-scalars-2026-10-30',
               repository={'full_name': review.REPOSITORY}, head_repository={'full_name': review.REPOSITORY})
    artifact = dict(id=review.ARTIFACT, name='ios-runtime-verification', expired=False,
                    size_in_bytes=review.BYTES, digest='sha256:' + review.DIGEST,
                    workflow_run={'id': review.RUN, 'head_sha': review.SOURCE})
    return run, artifact


def results():
    device = dict(deviceId=review.UDID, architecture='arm64', platform='iOS Simulator',
                  osVersion='18.5', modelName='iPhone 16 Pro', deviceName=review.DEVICE)
    summary = dict(totalTestCount=23, passedTests=21, failedTests=2, skippedTests=0,
                   expectedFailures=0, result='Failed',
                   testFailures=[dict(testIdentifierString=m) for m in review.FAILURES],
                   devicesAndConfigurations=[dict(device=device)])
    cases = [dict(nodeType='Test Case', nodeIdentifier=m,
                  result='Failed' if m in review.FAILURES else 'Passed') for m in sorted(review.EXPECTED)]
    return summary, dict(devices=[device], testNodes=[dict(children=cases)])


def activities(method):
    _, title, failure, tap, _ = review.FAILURES[method]
    return dict(testIdentifier=method, children=[
        *[dict(title=value) for value in review.PUBLIC_SETUP_TAPS[method]], dict(title=title),
        dict(title='XCTAssertEqual failed: ' + failure, isAssociatedWithFailure=True)])


def archive_fixture(extra=None, change_claim=False):
    claim = dict(source=dict(head=review.MERGE, tree=review.TREE),
                 task=dict(GITHUB_RUN_ID=str(review.RUN), GITHUB_RUN_ATTEMPT='1', GITHUB_JOB='ios'),
                 cycle='apple-ui')
    if change_claim:
        claim['source']['tree'] = 'f' * 40
    claim_raw = json.dumps(claim).encode()
    log = (b'Public control settlement: at=1.0, querySeconds=2.0, '
           b'frame=Optional((24.0, 86.0, 48.0, 48.0)), enabled=true, stable=false, hittable=nil\n')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as z:
        z.writestr(review.CLAIM, claim_raw)
        z.writestr(review.PREFIX + 'commit.txt', review.MERGE + '\n')
        z.writestr(review.PREFIX + 'xcode-version.txt', 'Xcode 26.3\nBuild version 17C529\n')
        z.writestr(review.PREFIX + 'xcode-ui-test.log', log)
        z.writestr(review.RESULT_PREFIX + 'Data/one', b'original opaque object')
        z.writestr(review.PREFIX + 'never-export.log', b'not exported')
        if extra:
            z.writestr(extra, b'foreign object')
    return buffer.getvalue(), hashlib.sha256(claim_raw).hexdigest(), hashlib.sha256(log).hexdigest()


class PublicSetupPairReviewTests(unittest.TestCase):
    def test_real_workflow_and_all_companion_bytes_match_reviewed_bindings(self):
        verify_workflow((review.ROOT / WORKFLOW).read_bytes())
        for path, expected in BINDINGS.items():
            target = review.ROOT / path
            self.assertFalse(target.is_symlink())
            self.assertEqual(hashlib.sha256(target.read_bytes()).hexdigest(), expected)

    def test_paid_runner_permissions_arbitrary_dispatch_upload_and_retry_mutants_are_rejected(self):
        raw = (review.ROOT / WORKFLOW).read_bytes()
        for old, new in [('macos-15', 'macos-15-large'), ('actions: read', 'actions: write'),
                         ('contents: read', 'contents: write'), ('  push:', '  workflow_dispatch:'),
                         ('cancel-in-progress: false', 'cancel-in-progress: true'),
                         ('persist-credentials: false', 'persist-credentials: true'),
                         ('/receipt.json', '/**'), ('timeout-minutes: 10', 'timeout-minutes: 30'),
                         ('review_public_setup_pair_commit.py', 'review_public_catalog_commit.py')]:
            self.assertIn(old.encode(), raw)
            with self.subTest(old=old), self.assertRaises(ValueError):
                verify_workflow(raw.replace(old.encode(), new.encode()))

    def test_exact_original_run_and_artifact_metadata_pass(self):
        review.validate_metadata(*metadata())

    def test_unfinished_retried_success_foreign_source_or_artifact_metadata_is_rejected(self):
        for which, key, value in [(0, 'status', 'in_progress'), (0, 'conclusion', 'success'),
                                 (0, 'run_attempt', 2), (0, 'head_sha', 'f' * 40),
                                 (0, 'head_branch', 'main'), (1, 'expired', True),
                                 (1, 'id', review.ARTIFACT + 1), (1, 'digest', 'sha256:' + '0' * 64)]:
            rows = metadata(); rows[which][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                review.validate_metadata(*rows)

    def test_all_23_official_methods_and_both_failures_match_exactly(self):
        result = review.verify_results(*results())
        self.assertEqual(len(result['methods']), 23)
        self.assertEqual(sum(v == 'Failed' for v in result['methods'].values()), 2)

    def test_partial_passes_missing_duplicate_or_relabeled_methods_and_wrong_device_are_rejected(self):
        summary, tests = results()
        candidates = []
        changed = copy.deepcopy(summary); changed['failedTests'] = 1; candidates.append((changed, tests))
        changed = copy.deepcopy(tests); changed['testNodes'][0]['children'].pop(); candidates.append((summary, changed))
        changed = copy.deepcopy(tests); changed['testNodes'][0]['children'][0]['nodeIdentifier'] = 'Foreign/test()'
        candidates.append((summary, changed))
        changed = copy.deepcopy(tests); changed['devices'][0]['osVersion'] = '26.2'; candidates.append((summary, changed))
        for rows in candidates:
            with self.subTest(rows=rows[0]['failedTests']), self.assertRaises(ValueError):
                review.verify_results(*rows)

    def test_each_exact_public_failing_iteration_is_eligible_without_causal_claim(self):
        for method in review.FAILURES:
            result = review.verify_public_setup_failure(activities(method), method)
            self.assertEqual(result['failing_game'], 'ghamza')
            self.assertNotIn('Passed', json.dumps(result))
            self.assertEqual(result['exact_public_setup_taps'], len(review.PUBLIC_SETUP_TAPS[method]))
            self.assertEqual(result['catalog_openings'], 1 if method == review.METHOD else 2)

    def test_only_fixed_earlier_synthetic_input_is_eligible(self):
        for method, (_, _, _, _, field) in review.FAILURES.items():
            data = activities(method)
            data['children'].insert(0, dict(title=f'Type \'Table Test\' into "{field}" TextView'))
            self.assertEqual(review.verify_public_setup_failure(data, method)['earlier_synthetic_typing_count'], 1)

    def test_private_unknown_or_post_game_typing_missing_markers_and_duplicate_game_taps_are_rejected(self):
        for method in review.FAILURES:
            candidates = []
            data = activities(method); data['children'].insert(0, dict(title='Type PRIVATE into field')); candidates.append(data)
            data = activities(method); data['children'].insert(1, dict(title='Type PRIVATE into field')); candidates.append(data)
            data = activities(method); data['children'].pop(1); candidates.append(data)
            data = activities(method); data['children'].insert(0, dict(data['children'][0])); candidates.append(data)
            data = activities(method); data['testIdentifier'] = 'Foreign/test()'; candidates.append(data)
            for data in candidates:
                with self.subTest(method=method), self.assertRaises(ValueError):
                    review.verify_public_setup_failure(data, method)

    def test_english_host_then_join_requires_both_catalog_openings_in_exact_order(self):
        method = next(m for m in review.FAILURES if m != review.METHOD)
        data = activities(method)
        self.assertEqual(review.verify_public_setup_failure(data, method)['catalog_openings'], 2)
        for first, second in ((0, 1), (2, 3), (4, 5)):
            changed = copy.deepcopy(data)
            changed['children'][first], changed['children'][second] = (
                changed['children'][second], changed['children'][first])
            with self.subTest(swapped=(first, second)), self.assertRaises(ValueError):
                review.verify_public_setup_failure(changed, method)
        for index in range(6):
            changed = copy.deepcopy(data); changed['children'].pop(index)
            with self.subTest(missing=index), self.assertRaises(ValueError):
                review.verify_public_setup_failure(changed, method)

    def test_public_tap_titles_match_exact_original_producer_log_not_prefix_only(self):
        for method in review.FAILURES:
            for index in range(len(review.PUBLIC_SETUP_TAPS[method])):
                for suffix in (' unexpected', ' private payload'):
                    data = activities(method); data['children'][index]['title'] += suffix
                    with self.subTest(method=method, index=index, suffix=suffix), self.assertRaises(ValueError):
                        review.verify_public_setup_failure(data, method)

    def test_attachment_export_is_always_one_of_the_two_exact_public_methods(self):
        for method in review.FAILURES:
            self.assertEqual(review.attachment_command(Path('result'), Path('out'), method)[-1], method)
        with self.assertRaises(ValueError):
            review.attachment_command(Path('result'), Path('out'), 'Foreign/test()')

    def test_original_objects_must_bind_every_exported_screenshot(self):
        review.validate_screenshots([dict(sha256='a')], {'original': 'a'})
        for rows in ([], [dict(sha256='foreign')], [dict(sha256='a')] * 9):
            with self.assertRaises(ValueError):
                review.validate_screenshots(rows, {'original': 'a'})

    def unpack_fixture(self, raw, claim_hash, log_hash, destination):
        with patch.multiple(review, BYTES=len(raw), DIGEST=hashlib.sha256(raw).hexdigest(),
                            CLAIM_HASH=claim_hash, LOG_HASH=log_hash):
            return review.unpack(raw, destination)

    def test_hashed_archive_extracts_only_original_result_objects(self):
        raw, claim_hash, log_hash = archive_fixture()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, lines = self.unpack_fixture(raw, claim_hash, log_hash, root)
            self.assertEqual(list(manifest), ['ios-ui-tests.xcresult/Data/one'])
            self.assertEqual(len(lines), 1)
            self.assertFalse((root / 'never-export.log').exists())

    def test_traversal_and_foreign_source_claim_are_rejected_before_extraction(self):
        for extra, changed in [('../escape', False), (None, True)]:
            raw, claim_hash, log_hash = archive_fixture(extra, changed)
            with tempfile.TemporaryDirectory() as tmp, self.assertRaises(ValueError):
                self.unpack_fixture(raw, claim_hash, log_hash, Path(tmp))

    def test_wrong_archive_hash_and_existing_destination_fail_closed(self):
        raw, claim_hash, log_hash = archive_fixture()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch.multiple(review, BYTES=len(raw), DIGEST='0' * 64), self.assertRaises(ValueError):
                review.unpack(raw, root)
            (root / 'existing').write_text('preserve')
            with self.assertRaises(ValueError):
                self.unpack_fixture(raw, claim_hash, log_hash, root)
            self.assertEqual((root / 'existing').read_text(), 'preserve')


if __name__ == '__main__':
    unittest.main()
