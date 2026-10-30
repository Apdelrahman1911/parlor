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

import review_public_back_commit as review
import review_frozen_result as frozen

SAMPLE = ('Public control settlement: at=1.0, querySeconds=2.0, '
          'frame=Optional((24.0, 86.0, 48.0, 48.0)), enabled=true, stable=false, hittable=nil\n')
LOG_HASH = hashlib.sha256(SAMPLE.encode()).hexdigest()


def metadata():
    run = dict(id=review.RUN, head_sha=review.SOURCE, run_attempt=1, status='completed',
               conclusion='failure', event='pull_request',
               path='.github/workflows/production-verification.yml',
               head_branch='fix/supplementary-name-controls-2026-10-30',
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


class PublicBackCommitReviewTests(unittest.TestCase):
    def query_activity(self, extra):
        return dict(testIdentifier=review.METHOD, children=[
            dict(title="Type 'Table Test' into \"Your name\" TextView"),
            dict(title="Type '\n' into \"Your name\" TextView"),
            *extra,
            dict(title='Failed public control settlement before tap'),
            dict(title='XCTAssertEqual failed: ' + review.FAILURE, isAssociatedWithFailure=True)])

    def test_original_back_queries_retry_and_errors_export_only_fixed_categories_and_numbers(self):
        original = self.query_activity([
            dict(title='Find the "Return to the home screen." Button', startTime=1.0, finishTime=2.0, duration=1.0),
            dict(title='Find the "Return to the home screen." Button (retry 1)'),
            dict(title='Find the "Return to the home screen." Button (retry 2)'),
            dict(title='Failed to get matching snapshot: No matches found; XCTestErrorDomain Code=1001; private=SECRET'),
            dict(title='Checking existence of `"Return to the home screen." Button`'),
            dict(title='Tap "Return to the home screen." Button'),
            dict(title='Find the "Private button SECRET" Button', startTime=float('nan'))])
        result = review.public_back_query_observations(original, review.METHOD)
        self.assertEqual([r['kind'] for r in result['queries']], ['resolve', 'resolve', 'resolve', 'exists', 'tap'])
        self.assertEqual([r['retry'] for r in result['queries']], [0, 1, 2, 0, 0])
        self.assertEqual(result['queries'][0]['timing'], dict(startTime=1.0, finishTime=2.0, duration=1.0))
        self.assertEqual(result['xctest_error_codes'], [1001])
        self.assertEqual(result['error_marker_counts']['no_matches'], 1)
        self.assertEqual(result['error_marker_counts']['snapshot_failure'], 1)
        self.assertNotIn('SECRET', json.dumps(result))
        self.assertNotIn('Return to the home screen', json.dumps(result))

    def test_absent_or_iso_timing_does_not_become_success_or_clock_alignment(self):
        activity = self.query_activity([dict(title='Find the "Return to the home screen." Button',
                                            startTime='2026-10-30T11:05:22Z')])
        result = review.public_back_query_observations(activity, review.METHOD)
        self.assertEqual(result['queries'][0]['timing'], dict(startTime=None, finishTime=None, duration=None))
        self.assertEqual(result['query_time_field_counts'], dict(startTime=1, finishTime=0, duration=0))
        self.assertEqual(result['xctest_error_codes'], [])
        self.assertNotIn('2026-', json.dumps(result))
        self.assertNotIn('success', json.dumps(result).lower())

    def test_query_numbers_bounds_and_exact_public_eligibility_fail_closed(self):
        for value in (True, -1, float('nan'), float('inf'), 10 ** 12 + 1, {}, 'x' * 65):
            with self.subTest(value=value), self.assertRaises(ValueError):
                review.public_back_query_observations(self.query_activity([
                    dict(title='Find the "Return to the home screen." Button', duration=value)]), review.METHOD)
        with self.assertRaises(ValueError):
            review.public_back_query_observations(self.query_activity([
                dict(title='Find the "Return to the home screen." Button')] * 65), review.METHOD)
        with self.assertRaises(ValueError):
            review.public_back_query_observations(self.query_activity([
                dict(title='Type private SECRET')]), review.METHOD)
        with self.assertRaises(ValueError):
            review.public_back_query_observations(self.query_activity([]), 'foreign')

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
        for method in ('', '*', 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()', 'IOSAppLaunchUITests', None):
            with self.assertRaises(ValueError):
                review.attachment_command(result, out, method)

    def test_private_typing_or_unidentified_failure_cannot_admit_public_export(self):
        activity = dict(testIdentifier=review.METHOD, children=[*[dict(title=x) for x in review.TYPED_TITLES],
                        dict(title=review.TITLE),
                        dict(title='XCTAssertEqual failed: ' + review.FAILURE, isAssociatedWithFailure=True)])
        review.verify_public_back_failure(activity, review.METHOD)
        for changed in (activity | dict(testIdentifier='foreign'),
                        activity | dict(children=activity['children'] + [dict(title='Type private value')]),
                        activity | dict(children=activity['children'] + [dict(title=review.TYPED_TITLES[0])]),
                        activity | dict(children=activity['children'][1:]),
                        activity | dict(children=[])):
            with self.assertRaises(ValueError):
                review.verify_public_back_failure(changed, review.METHOD)

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


    def test_exact_six_field_observations_only_and_no_private_values(self):
        self.assertEqual(review.public_back_lines(SAMPLE), [SAMPLE.strip()])
        for bad in (SAMPLE.replace('at=1.0', 'at=nan'), SAMPLE.replace('at=1.0', 'at=1e999'),
                    SAMPLE.replace('at=1.0', 'at=-1'), SAMPLE.strip() + ', value=secret',
                    SAMPLE.replace('enabled=true', 'enabled=1'), SAMPLE.replace('24.0', '1e999'),
                    SAMPLE.replace('hittable=nil', 'hittable=Optional(private)')):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                review.public_back_lines(bad)
        self.assertEqual(review.public_back_lines('source quoting ' + SAMPLE), [])

    def test_observation_count_and_size_are_bounded(self):
        with self.assertRaises(ValueError):
            review.public_back_lines(SAMPLE + SAMPLE)
        part = SAMPLE.strip().removeprefix('Public control settlement: ')
        with self.assertRaises(ValueError):
            review.public_back_lines('Public control settlement: ' + '; '.join([part] * 13))
        with self.assertRaises(ValueError):
            review.public_back_lines('a' * (1024 * 1024 + 1))

    def test_older_readers_and_private_typing_guard_remain_unchanged(self):
        activity = dict(testIdentifier=frozen.METHOD, children=[dict(title=review.TITLE),
            dict(title='Type private value'),
            dict(title='XCTAssertEqual failed: ' + review.FAILURE, isAssociatedWithFailure=True)])
        with self.assertRaises(ValueError):
            review.attachments.verify_public_failure(activity, frozen.METHOD)

    def test_long_public_journey_does_not_retain_an_unbounded_title_ledger(self):
        activity = dict(testIdentifier=review.METHOD, children=[
            *[dict(title="Find unrelated public control") for _ in range(512)],
            *[dict(title=x) for x in review.TYPED_TITLES], dict(title=review.TITLE),
            dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)])
        review.verify_public_back_failure(activity, review.METHOD)
        for title in ("Type private value", review.TYPED_TITLES[0]):
            changed = activity | dict(children=activity['children'] + [dict(title=title)])
            with self.assertRaises(ValueError):
                review.verify_public_back_failure(changed, review.METHOD)

    def test_traversal_depth_count_title_size_and_duplicate_failure_remain_bounded(self):
        activity = dict(testIdentifier=review.METHOD, children=[
            *[dict(title=x) for x in review.TYPED_TITLES], dict(title=review.TITLE),
            dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)])
        deep = dict(title="Find public control")
        for _ in range(25):
            deep = dict(children=[deep])
        for extras in ([deep], [dict(title="x" * 4097)],
                       [dict(title="Find control") for _ in range(16384)],
                       [dict(title=review.TITLE)],
                       [dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)]):
            with self.assertRaises(ValueError):
                review.verify_public_back_failure(activity | dict(children=activity['children'] + extras), review.METHOD)

    def test_unresolved_native_schema_retains_only_fixed_public_numeric_counters(self):
        activity = dict(testIdentifier=review.METHOD, children=[
            *[dict(title=x) for x in review.TYPED_TITLES],
            dict(name=review.TITLE, unrelated="private value must not be retained"),
            dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)])
        with self.assertRaises(review.BackEligibilityError) as caught:
            review.verify_public_back_failure(activity, review.METHOD)
        metadata = caught.exception.metadata
        self.assertEqual(set(metadata), {"nodes", "screenshot_title_count", "failure_title_count",
                                         "synthetic_typing_title_count", "fixed_public_marker_fields"})
        self.assertEqual(metadata["screenshot_title_count"], 0)
        self.assertEqual(metadata["failure_title_count"], 1)
        self.assertEqual(metadata["synthetic_typing_title_count"], 2)
        self.assertEqual(metadata["fixed_public_marker_fields"]["name"], 1)
        for secret in ("private value", review.TITLE, *review.TYPED_TITLES):
            self.assertNotIn(secret, json.dumps(metadata))

    def test_exact_original_attachment_name_is_not_derived_from_the_reader_constant(self):
        expected = 'Failed public control settlement before tap'
        self.assertEqual(review.TITLE, expected)
        self.assertEqual(review.TITLE, review.attachments.TITLE)
        activity = dict(testIdentifier=review.METHOD, children=[
            *[dict(title=x) for x in review.TYPED_TITLES], dict(title=expected),
            dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)])
        review.verify_public_back_failure(activity, review.METHOD)
        for title in ("prefix " + expected, expected + " private", "Failed public Back commit"):
            changed = activity | dict(children=[
                *[dict(title=x) for x in review.TYPED_TITLES], dict(title=title),
                dict(title="XCTAssertEqual failed: " + review.FAILURE, isAssociatedWithFailure=True)])
            with self.assertRaises(ValueError):
                review.verify_public_back_failure(changed, review.METHOD)
        with self.assertRaises(ValueError):
            review.verify_public_back_failure(activity | dict(children=activity['children'] + [dict(title=expected)]), review.METHOD)

if __name__ == '__main__':
    unittest.main()
