"""Exact retained failure intake controls; no app/native work on this host."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import review_main_failure as r
import public_attachments as a


def metadata():
    run = dict(id=r.RUN, head_sha=r.SOURCE, run_attempt=1, status='completed',
               conclusion='failure', event='push', path='.github/workflows/production-verification.yml',
               head_branch='main', repository={'full_name': r.REPOSITORY},
               head_repository={'full_name': r.REPOSITORY})
    artifact = dict(id=r.ARTIFACT, name='ios-runtime-verification', expired=False,
                    size_in_bytes=r.BYTES, digest='sha256:' + r.DIGEST,
                    workflow_run=dict(id=r.RUN, head_sha=r.SOURCE))
    return run, artifact


def activity():
    return dict(testIdentifier=r.METHOD, children=[
        dict(title='Swipe up Application'),
        dict(title='Find the "افتح الدومنة المصرية" Button', startTime=1.0, finishTime=2.0),
        dict(title=a.TITLE),
        dict(title='XCTAssertEqual failed: Public setup control must stop scrolling before it is tapped',
             isAssociatedWithFailure=True)])


class MainFailureControls(unittest.TestCase):
    def test_exact_main_and_artifact_metadata_is_accepted(self):
        r.validate_metadata(*metadata())

    def test_changed_source_attempt_event_branch_or_outcome_is_rejected(self):
        for field, value in [('head_sha','0'*40), ('run_attempt',2), ('event','pull_request'),
                             ('head_branch','testing'), ('status','in_progress'), ('conclusion','success')]:
            run, artifact = metadata(); run[field] = value
            with self.assertRaises(ValueError): r.validate_metadata(run, artifact)

    def test_changed_artifact_identity_digest_size_or_ownership_is_rejected(self):
        for field, value in [('id',1), ('digest','sha256:'+'0'*64), ('size_in_bytes',1),
                             ('expired',True), ('workflow_run',dict(id=r.RUN,head_sha='0'*40))]:
            run, artifact = metadata(); artifact[field] = value
            with self.assertRaises(ValueError): r.validate_metadata(run, artifact)

    def test_first_catalog_query_is_public_and_arbitrary_payload_is_not_exported(self):
        raw = activity(); raw['children'][1]['value'] = 'private-never-export'
        raw['children'][1]['title'] += ' private-never-export'
        receipt = r.public_activity_receipt(raw)
        self.assertEqual((receipt['typing'],receipt['opening_taps'],receipt['upward_swipes']),(0,0,1))
        self.assertNotIn('private-never-export', json.dumps(receipt))
        self.assertEqual(receipt['timeline'][1]['timing'],dict(startTime=1.0,finishTime=2.0))

    def test_typed_opened_repeated_swipe_or_foreign_method_is_not_public(self):
        for title in ('Type secret', 'Tap room', 'Swipe up Application'):
            raw = activity(); raw['children'].append(dict(title=title))
            with self.assertRaises(ValueError): r.public_activity_receipt(raw)
        raw = activity(); raw['testIdentifier'] = 'Foreign()'
        with self.assertRaises(ValueError): r.public_activity_receipt(raw)

    def test_wrong_failure_or_missing_public_game_rejects(self):
        raw = activity(); raw['children'][-1]['title'] = 'XCTAssertEqual failed: different'
        with self.assertRaises(ValueError): r.public_activity_receipt(raw)
        raw = activity(); raw['children'][1]['title'] = 'Find other'
        with self.assertRaises(ValueError): r.public_activity_receipt(raw)

    def test_archive_bytes_are_digest_bound_before_any_extraction(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            with self.assertRaises(ValueError): r.unpack(b'foreign ZIP',out)
            self.assertEqual(list(out.iterdir()),[])

    def test_actual_swift_roster_is_source_pinned_and_not_older_23_case_roster(self):
        source = (r.ROOT/'iosApp/iosAppUITests/IOSAppLaunchUITests.swift').read_bytes()
        self.assertEqual(hashlib.sha256(source).hexdigest(),r.DRIVER_SHA256)
        self.assertEqual(len(r.EXPECTED),26)
        self.assertIn(r.METHOD,r.EXPECTED)
        self.assertIn('IOSAppLaunchUITests/testPostDoneNameAndRoomCodeEditsInArabic()',r.EXPECTED)

    def test_workflow_is_hash_bound_and_standard_free_read_only(self):
        import sys
        sys.path.insert(0,str(r.ROOT))
        from scripts.release import workflow_contract as w
        text=(r.ROOT/'.github/workflows/ios-main-failure-readback.yml').read_text()
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest(),w.MAIN_FAILURE_READBACK_SHA256)
        for token in ('runs-on: macos-15','actions: read','persist-credentials: false','if: always()'):
            self.assertIn(token,text)
        for token in ('gradlew','simctl','workflow_dispatch','secrets.','contents: write','actions/cache'):
            self.assertNotIn(token,text)

    def test_exact_26_outcomes_and_owned_185_device_are_required(self):
        device=dict(deviceId=r.UDID, architecture='arm64', platform='iOS Simulator',
                    osVersion='18.5', modelName='iPhone 16 Pro', deviceName=r.DEVICE)
        summary=dict(totalTestCount=26, passedTests=25, failedTests=1, skippedTests=0,
                     expectedFailures=0, result='Failed',
                     testFailures=[dict(testIdentifierString=r.METHOD)],
                     devicesAndConfigurations=[dict(device=device)])
        tests=dict(devices=[device],testNodes=[dict(nodeType='Test Case',nodeIdentifier=m,
                   result='Failed' if m==r.METHOD else 'Passed') for m in r.EXPECTED])
        self.assertEqual(r.verify_results(summary,tests)['counts']['failedTests'],1)
        summary['passedTests']=26
        with self.assertRaises(ValueError): r.verify_results(summary,tests)
        summary['passedTests']=25; device['osVersion']='26.2'
        with self.assertRaises(ValueError): r.verify_results(summary,tests)
        device['osVersion']='18.5'; tests['testNodes'].pop()
        with self.assertRaises(ValueError): r.verify_results(summary,tests)
