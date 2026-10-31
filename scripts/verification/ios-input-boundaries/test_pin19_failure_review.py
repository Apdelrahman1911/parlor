"""Fabricated rejection controls, never native failure/root-cause verification."""
import copy
import hashlib
import io
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import review_pin19_failure as review

sys.path.insert(0, str(review.ROOT))
from scripts.release import workflow_contract as workflow


def results():
    device=dict(deviceId=review.SIMULATOR,architecture='arm64',platform='iOS Simulator',
                osVersion='18.5',modelName='iPhone 16 Pro')
    summary=dict(totalTestCount=1,failedTests=1,passedTests=0,skippedTests=0,expectedFailures=0,
                 result='Failed',testFailures=[dict(testIdentifierString=review.METHOD,
                 failureText='failed - '+review.FAILURE)],devicesAndConfigurations=[dict(device=device)])
    tests=dict(devices=[device],testNodes=[dict(nodeType='Test Case',nodeIdentifier=review.METHOD,
                                               result='Failed')])
    return summary,tests


def metadata():
    run=dict(id=review.RUN,head_sha=review.HARNESS,run_attempt=1,status='completed',conclusion='failure',event='push',
        head_branch='diagnostic/ios-settled-catalog-idle-2026-10-31',path='.github/workflows/ios-native-root-observations.yml',
        repository=dict(full_name=review.REPOSITORY),head_repository=dict(full_name=review.REPOSITORY))
    artifact=dict(id=review.ARTIFACT,name='native-settled-catalog-idle-41234285221-1',expired=False,
        size_in_bytes=review.BYTES,digest='sha256:'+review.DIGEST,workflow_run=dict(id=review.RUN,head_sha=review.HARNESS))
    return run,artifact


def activities():
    return dict(testIdentifier=review.METHOD,activities=[dict(title='Swipe up Application'),
        dict(title='Find the "Open Word Impostor" Button'),
        dict(title='failed - '+review.FAILURE,isAssociatedWithFailure=True)])


def archive(extra=None):
    summary,tests=results()
    state=dict(harness=review.HARNESS,source=review.SOURCE,source_tree=review.TREE,
        profile='synthetic-settled-catalog-idle',result='NOT_QUALIFIED',
        arms=[dict(arm='native',build_exit=0,test_exit=65,cleanup='PASS',simulator=review.SIMULATOR)])
    raw=io.BytesIO()
    with zipfile.ZipFile(raw,'w') as z:
        for name,value in [('state.json',state),('native/summary.json',summary),('native/tests.json',tests),('native/inputs.json',{})]:
            z.writestr(name,json.dumps(value))
        z.writestr('native/tests.xcresult/Data/original-object',b'owned original fixture')
        if extra:extra(z)
    return raw.getvalue()


class PIN19FailureReviewControls(unittest.TestCase):
    def test_source_renderer_receives_only_declared_bindings_not_entire_manifest(self):
        # Actual frozen application input graph, no app compilation/execution.
        archive = review.subprocess.check_output(['git', 'archive', review.SOURCE, *review.run_pin03.ROOTS,
            *(p.rstrip('/') for p in review.run_pin03.PREFIXES)], cwd=review.ROOT)
        complete, sources = {}, {}
        import tarfile
        with tarfile.open(fileobj=io.BytesIO(archive), mode='r:') as tar:
            for e in tar:
                if e.isdir() or not review.run_pin03.selected(e.name):continue
                raw=tar.extractfile(e).read();complete[e.name]=review.sha(raw)
                if e.name in review.pair.MODIFIED:sources[e.name]=raw.decode()
        plan=review.idle.renderer_for_profile(review.idle.PROFILE,review.SOURCE,review.pair.RUNTIME,review.pair.DEVICE)(
            sources,{n:complete[n] for n in review.pair.MODIFIED},arm='native')
        observed=complete|{n:review.sha(v.encode()) for n,v in plan.items()}
        binding=review.validate_source_inputs(dict(original_inputs=complete),observed)
        self.assertEqual(binding['original_input_count'],1214)
        self.assertEqual(binding['observed_input_count'],1215)
        bad=dict(observed);bad[next(iter(bad))]='0'*64
        with self.assertRaises(ValueError):review.validate_source_inputs(dict(original_inputs=complete),bad)

    def test_exact_original_failure_metadata_and_methods(self):
        review.validate_metadata(*metadata());review.verify_results(*results())

    def test_changed_run_attempt_source_repository_or_terminal_result_rejects(self):
        for change in (lambda r:r.update(id=1),lambda r:r.update(head_sha='0'*40),
            lambda r:r.update(run_attempt=2),lambda r:r.update(status='in_progress'),
            lambda r:r.update(conclusion='success'),lambda r:r.update(event='pull_request'),
            lambda r:r.update(head_branch='main'),lambda r:r['head_repository'].update(full_name='foreign/repo')):
            run,a=metadata();change(run)
            with self.subTest(change=change),self.assertRaises(ValueError):review.validate_metadata(run,a)

    def test_wrong_expired_mutable_or_foreign_artifact_rejects(self):
        for change in (lambda a:a.update(id=1),lambda a:a.update(name='ios-runtime-verification'),
            lambda a:a.update(expired=True),lambda a:a.update(size_in_bytes=1),lambda a:a.update(digest='sha256:'+'0'*64),
            lambda a:a['workflow_run'].update(id=1),lambda a:a['workflow_run'].update(head_sha='0'*40)):
            r,a=metadata();change(a)
            with self.subTest(change=change),self.assertRaises(ValueError):review.validate_metadata(r,a)

    def test_skipped_duplicate_foreign_passed_or_different_failure_rejects(self):
        for change in (lambda s,t:s.update(skippedTests=1),lambda s,t:s.update(failedTests=True),
            lambda s,t:s.update(result='Passed'),lambda s,t:s['testFailures'][0].update(failureText='forced Host failure'),
            lambda s,t:t['testNodes'].append(t['testNodes'][0]),lambda s,t:t['testNodes'][0].update(result='Passed'),
            lambda s,t:t['devices'][0].update(deviceId='foreign')):
            s,t=copy.deepcopy(results());change(s,t)
            with self.subTest(change=change),self.assertRaises(ValueError):review.verify_results(s,t)

    def test_exact_public_pre_stimulus_failure_has_no_input(self):
        r=review.public_observations(activities())
        self.assertEqual(r['counts']['taps'],0);self.assertEqual(r['counts']['typing'],0)
        self.assertEqual(r['counts']['failures'],1);self.assertEqual(r['counts']['swipes'],1)

    def test_typing_tap_foreign_failure_and_duplicate_assertion_forbid_export(self):
        for change in (lambda a:a.update(testIdentifier='foreign'),
            lambda a:a['activities'].append(dict(title="Type 'private' into field")),
            lambda a:a['activities'].append(dict(title='Tap "Open Word Impostor" Button')),
            lambda a:a['activities'].append(a['activities'][-1]),
            lambda a:a['activities'][-1].update(title='different failed assertion')):
            a=activities();change(a)
            with self.subTest(change=change),self.assertRaises(ValueError):review.public_observations(a)

    def test_activity_traversal_depth_title_length_and_node_count_are_bounded(self):
        for a in (dict(testIdentifier=review.METHOD,activities=[dict(title='x'*4097)]),
                  dict(testIdentifier=review.METHOD,activities=[None]*16385)):
            with self.assertRaises(ValueError):review.public_observations(a)
        a=activities()
        for _ in range(25):a=dict(child=a)
        a['testIdentifier']=review.METHOD
        with self.assertRaises(ValueError):review.public_observations(a)

    def test_error_metadata_is_fixed_numeric_not_raw_titles(self):
        a=activities();a['activities'].append(dict(title='No matches found: XCTestErrorDomain Code=4 private-marker'))
        r=review.public_observations(a)
        self.assertEqual(r['error_markers']['no_matches'],1);self.assertEqual(r['xctest_error_codes'],[4])
        self.assertNotIn('private-marker',json.dumps(r))

    def test_duplicate_and_nonfinite_json_fail_closed(self):
        for raw in (b'{"x":1,"x":2}',b'{"x":NaN}',b'{"x":Infinity}'):
            with self.assertRaises(ValueError):review.load(raw)

    def test_complete_archive_extraction_is_original_result_only(self):
        raw=archive()
        with tempfile.TemporaryDirectory() as temp,patch.object(review,'BYTES',len(raw)),patch.object(review,'DIGEST',hashlib.sha256(raw).hexdigest()),patch.object(review,'validate_source_inputs',return_value={'fixture':True}):
            s,t,m,b=review.unpack(raw,Path(temp));self.assertEqual(len(m),1);self.assertEqual(b,{'fixture':True})
            self.assertFalse((Path(temp)/'state.json').exists())
            review.verify_results(s,t)

    def test_unsafe_duplicate_link_and_absolute_zip_members_fail_before_copy(self):
        def link(z):
            e=zipfile.ZipInfo('unsafe');e.external_attr=(stat.S_IFLNK|0o777)<<16;z.writestr(e,'target')
        for extra in (lambda z:z.writestr('../escaped','x'),lambda z:z.writestr('/absolute','x'),
                      lambda z:z.writestr('state.json','{}'),link):
            raw=archive(extra)
            with tempfile.TemporaryDirectory() as temp,patch.object(review,'BYTES',len(raw)),patch.object(review,'DIGEST',hashlib.sha256(raw).hexdigest()):
                with self.subTest(extra=extra),self.assertRaises(ValueError):review.unpack(raw,Path(temp))
                self.assertFalse(any(Path(temp).iterdir()))

    def test_archive_digest_size_existing_destination_and_expansion_reject(self):
        raw=archive()
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):review.unpack(raw,Path(temp))
            with patch.object(review,'BYTES',len(raw)),patch.object(review,'DIGEST',hashlib.sha256(raw).hexdigest()),patch.object(review,'MAX_EXPANDED',1):
                with self.assertRaises(ValueError):review.unpack(raw,Path(temp))
            (Path(temp)/'existing').write_text('x')
            with patch.object(review,'BYTES',len(raw)),patch.object(review,'DIGEST',hashlib.sha256(raw).hexdigest()):
                with self.assertRaises(ValueError):review.unpack(raw,Path(temp))

    def test_workflow_is_exact_free_readonly_no_device_no_public_raw_logs(self):
        text=(workflow.WORKFLOWS/workflow.SETTLED_FAILURE_REVIEW).read_text()
        workflow.verify_settled_failure_review(text)
        for before,after in (('runs-on: macos-15','runs-on: macos-15-large'),('contents: read','contents: write'),
            ('actions: read','actions: write'),('persist-credentials: false','persist-credentials: true'),
            ('cancel-in-progress: false','cancel-in-progress: true'),('timeout-minutes: 10','timeout-minutes: 30'),
            ('branches: [diagnostic/ios-settled-failure-readback-2026-10-31]','branches: [main]'),
            ('/receipt.json','/**'),('review_pin19_failure.py','run_pin19.py')):
            self.assertIn(before,text)
            with self.subTest(before=before),self.assertRaises(RuntimeError):workflow.verify_settled_failure_review(text.replace(before,after))


if __name__=='__main__':unittest.main()
