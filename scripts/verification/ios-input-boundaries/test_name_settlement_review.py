"""Software controls for the separate immutable production-failure reader."""
import ast
import hashlib
import io
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import review_name_settlement as review
import review_frozen_result as frozen

SAMPLE = ('Public control settlement: at=1.0, querySeconds=2.0, frame=nil, '
          'enabled=false, stable=false, hittable=nil\n')
LOG_HASH = hashlib.sha256(SAMPLE.encode()).hexdigest()


def metadata():
    run = dict(id=review.RUN, head_sha=review.SOURCE, run_attempt=1, status='completed',
               conclusion='failure', event='pull_request',
               path='.github/workflows/production-verification.yml',
               head_branch='fix/input-diagnostic-integration-2026-10-29',
               repository={'full_name': review.REPOSITORY}, head_repository={'full_name': review.REPOSITORY})
    artifact = dict(id=review.ARTIFACT, name='ios-runtime-verification', expired=False,
                    size_in_bytes=review.BYTES, digest='sha256:' + review.DIGEST,
                    workflow_run={'id': review.RUN, 'head_sha': review.SOURCE})
    return run, artifact


def archive(extra=None, claim_change=None):
    claim = dict(source=dict(head=review.MERGE, tree=review.TREE),
                 task=dict(GITHUB_RUN_ID=str(review.RUN), GITHUB_RUN_ATTEMPT='1', GITHUB_JOB='ios'),
                 cycle='apple-ui')
    if claim_change:
        claim_change(claim)
    claimraw = json.dumps(claim).encode()
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as z:
        z.writestr(review.CLAIM, claimraw)
        z.writestr(review.PREFIX + 'commit.txt', review.MERGE + '\n')
        z.writestr(review.PREFIX + 'xcode-version.txt', 'Xcode 26.3\nBuild version 17C529\n')
        z.writestr(review.PREFIX + 'xcode-ui-test.log', SAMPLE)
        z.writestr(review.RESULT_PREFIX + 'Data/original', b'original opaque object')
        z.writestr(review.PREFIX + 'do-not-extract.log', b'not public export')
        if extra:
            z.writestr(extra, b'untrusted object')
    return buffer.getvalue(), hashlib.sha256(claimraw).hexdigest()


def results():
    device = dict(deviceId=review.UDID, architecture='arm64', platform='iOS Simulator',
                  osVersion='18.5', modelName='iPhone 16 Pro', deviceName=review.DEVICE)
    summary = dict(totalTestCount=23, passedTests=22, failedTests=1, skippedTests=0,
                   expectedFailures=0, result='Failed',
                   testFailures=[dict(testIdentifierString=review.METHOD)],
                   devicesAndConfigurations=[dict(device=device)])
    cases = [dict(nodeType='Test Case', nodeIdentifier=review.METHOD, result='Failed')]
    cases += [dict(nodeType='Test Case', nodeIdentifier=method, result='Passed')
              for method in sorted(review.EXPECTED - {review.METHOD})]
    return summary, dict(devices=[device], testNodes=[dict(children=cases)])


class NameSettlementReviewTests(unittest.TestCase):
    def test_exact_failed_production_run_and_immutable_artifact_only(self):
        run, artifact = metadata()
        review.validate_metadata(run, artifact)
        for field, value in (('id', 1), ('head_sha', 'a' * 40), ('conclusion', 'success'),
                             ('run_attempt', 2), ('status', 'in_progress'), ('event', 'push'),
                             ('path', '.github/workflows/ios-input-investigation.yml'),
                             ('head_branch', 'main'), ('repository', dict(full_name='foreign'))):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.validate_metadata(run | {field: value}, artifact)
        for field, value in (('id', 1), ('name', 'foreign'), ('expired', True),
                             ('size_in_bytes', 1), ('digest', 'sha256:' + 'a' * 64),
                             ('workflow_run', dict(id=1, head_sha=review.SOURCE))):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.validate_metadata(run, artifact | {field: value})

    def test_extraction_preserves_original_objects_and_excludes_other_artifact_inputs(self):
        raw, claimhash = archive()
        with tempfile.TemporaryDirectory() as tmp, patch.multiple(review, BYTES=len(raw),
                DIGEST=hashlib.sha256(raw).hexdigest(), CLAIM_HASH=claimhash, LOG_HASH=LOG_HASH):
            root = Path(tmp).resolve()
            manifest, lines = review.unpack(raw, root)
            self.assertEqual(lines, [SAMPLE.strip()])
            self.assertEqual(manifest, {'ios-ui-tests.xcresult/Data/original':
                             hashlib.sha256(b'original opaque object').hexdigest()})
            self.assertEqual((root / next(iter(manifest))).read_bytes(), b'original opaque object')
            self.assertFalse((root / 'do-not-extract.log').exists())
            with self.assertRaises(ValueError):
                review.unpack(raw, root)

    def test_source_tree_merge_and_task_mismatches_fail_before_copy(self):
        changes = (lambda c: c['source'].update(head='a' * 40),
                   lambda c: c['source'].update(tree='a' * 40),
                   lambda c: c['task'].update(GITHUB_RUN_ATTEMPT='2'),
                   lambda c: c.update(cycle='foreign'))
        for change in changes:
            raw, claimhash = archive(claim_change=change)
            with tempfile.TemporaryDirectory() as tmp, patch.multiple(review, BYTES=len(raw),
                    DIGEST=hashlib.sha256(raw).hexdigest(), CLAIM_HASH=claimhash, LOG_HASH=LOG_HASH), self.assertRaises(ValueError):
                review.unpack(raw, Path(tmp).resolve())
            # No source/claim normalization or current-source fallback is used.

    def test_digest_and_length_are_both_required(self):
        raw, claimhash = archive()
        for size, digest in ((len(raw) + 1, hashlib.sha256(raw).hexdigest()), (len(raw), 'a' * 64)):
            with tempfile.TemporaryDirectory() as tmp, patch.multiple(review, BYTES=size,
                    DIGEST=digest, CLAIM_HASH=claimhash, LOG_HASH=LOG_HASH), self.assertRaises(ValueError):
                review.unpack(raw, Path(tmp).resolve())

    def test_unsafe_zip_paths_links_duplicates_and_total_size_fail_before_copy(self):
        link = zipfile.ZipInfo(review.RESULT_PREFIX + 'link')
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        for extra in ('../escape', '/absolute', 'ambiguous//path', link, review.CLAIM):
            raw, claimhash = archive(extra=extra)
            with tempfile.TemporaryDirectory() as tmp, patch.multiple(review, BYTES=len(raw),
                    DIGEST=hashlib.sha256(raw).hexdigest(), CLAIM_HASH=claimhash, LOG_HASH=LOG_HASH):
                root = Path(tmp).resolve()
                with self.assertRaises(ValueError):
                    review.unpack(raw, root)
                self.assertEqual(list(root.iterdir()), [])
        raw, claimhash = archive()
        with tempfile.TemporaryDirectory() as tmp, patch.multiple(review, BYTES=len(raw),
                DIGEST=hashlib.sha256(raw).hexdigest(), CLAIM_HASH=claimhash, LOG_HASH=LOG_HASH, MAX_BYTES=1), self.assertRaises(ValueError):
            review.unpack(raw, Path(tmp).resolve())

    def test_original_failure_counts_methods_and_actual_runtime_remain_failed(self):
        summary, tests = results()
        self.assertEqual(review.verify_results(summary, tests)['methods'][review.METHOD], 'Failed')
        for field, value in (('totalTestCount', 22), ('passedTests', 23), ('failedTests', 0),
                             ('failedTests', True), ('skippedTests', 1), ('expectedFailures', 1),
                             ('result', 'Passed'), ('testFailures', [dict(testIdentifierString='foreign')])):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.verify_results(summary | {field: value}, tests)
        for field, value in (('deviceId', 'foreign'), ('architecture', 'x86_64'),
                             ('osVersion', '26.2'), ('modelName', 'iPhone 17 Pro'), ('deviceName', 'foreign')):
            changed = json.loads(json.dumps(tests))
            changed['devices'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.verify_results(summary, changed)

    def test_duplicate_missing_and_changed_actual_cases_are_not_accepted(self):
        summary, tests = results()
        for mutation in ('duplicate', 'missing', 'passed-failure', 'extra', 'foreign'):
            changed = json.loads(json.dumps(tests)); cases = changed['testNodes'][0]['children']
            if mutation == 'duplicate':
                cases[-1]['nodeIdentifier'] = cases[-2]['nodeIdentifier']
            elif mutation == 'missing':
                cases.pop()
            elif mutation == 'passed-failure':
                cases[0]['result'] = 'Passed'
            elif mutation == 'foreign':
                cases[-1]['nodeIdentifier'] = 'Foreign/testRenamed()'
            else:
                cases.append(cases[0])
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                review.verify_results(summary, changed)

    def test_export_is_exact_english_failed_method_not_whole_result_or_other_investigation(self):
        result, out = Path('/owned/result.xcresult'), Path('/owned/export')
        self.assertEqual(review.attachment_command(result, out, review.METHOD),
                         ['export', 'attachments', '--path', str(result), '--output-path', str(out),
                          '--test-id', review.METHOD])
        for method in ('', '*', frozen.METHOD, 'IOSAppLaunchUITests', None):
            with self.assertRaises(ValueError):
                review.attachment_command(result, out, method)

    def test_no_typed_private_or_unidentified_failure_can_admit_public_export(self):
        activity = dict(testIdentifier=review.METHOD, children=[dict(title=review.attachments.TITLE),
                        dict(title='XCTAssertEqual failed: Public setup control must stop scrolling before it is tapped',
                             isAssociatedWithFailure=True)])
        review.attachments.verify_public_failure(activity, review.METHOD)
        for changed in (activity | dict(testIdentifier=frozen.METHOD),
                        activity | dict(children=activity['children'] + [dict(title='Type private value')]),
                        activity | dict(children=[])):
            with self.assertRaises(ValueError):
                review.attachments.verify_public_failure(changed, review.METHOD)

    def test_png_hash_requires_independent_original_object_binding(self):
        rows, manifest = [dict(sha256='a' * 64)], {'ios-ui-tests.xcresult/Data/original': 'a' * 64}
        review.validate_screenshots(rows, manifest)
        for changed in ([], [dict(sha256='b' * 64)], rows * 9):
            with self.assertRaises(ValueError):
                review.validate_screenshots(changed, manifest)

    def test_existing_six_field_schema_remains_separate_from_native_causality(self):
        line = ('Public control settlement: at=1.0, querySeconds=2.0, frame=nil, '
                'enabled=false, stable=false, hittable=nil')
        self.assertEqual(frozen.settlement_lines(dict(emittedOutput=line)), [line])
        with self.assertRaises(ValueError):
            frozen.settlement_lines(dict(emittedOutput=line + ', private=secret'))
        raw = Path(review.__file__).read_text()
        ast.parse(raw)
        self.assertIn('production_qualified=False, app_execution=False', raw)
        for forbidden in ('simctl', 'test-without-building', 'build-for-testing', 'mavenLocal'):
            self.assertNotIn(forbidden, raw)


if __name__ == '__main__':
    unittest.main()
