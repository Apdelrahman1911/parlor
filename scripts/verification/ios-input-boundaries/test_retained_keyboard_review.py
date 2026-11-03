import copy
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import review_retained_keyboard as review


def rows():
    result = []
    for kind, time in [('proxy-spawn', 1), ('xpcproxy', 2), ('running', 30), ('daemon-running', 31)]:
        message = dict([('proxy-spawn', 'xpcproxy_sim spawned with pid 51221'),
                        ('xpcproxy', 'service state: xpcproxy'), ('running', 'service state: running'),
                        ('daemon-running', 'Successfully spawned kbd[51221] because ipc (mach)')])[kind]
        result.append(dict(processID=100, threadID=200, subsystem='user/501/com.apple.TextInput.kbd [51221]',
            timestamp=f'2026-10-30 02:11:{time:02}.000000+0000', eventMessage=message))
    for time, message in [(3,'Keyboard receives keyEvent type: 4; subtype: 0'), (32,'Keyboard inserts text')]:
        for i in range(10):
            result.append(dict(processID=49787, threadID=136986, subsystem='com.apple.UIKit', category='KeyboardUI',
                timestamp=f'2026-10-30 02:11:{time:02}.{i:06}+0000', eventMessage=message))
    return result


class RetainedKeyboardTests(unittest.TestCase):
    def test_native_utc_precision_is_exact_and_never_float_rounded(self):
        self.assertEqual(review.timestamp_us('2026-10-30 02:11:41.333998+0000'), 1793326301333998)
        self.assertEqual(review.timestamp_us('2026-10-30T02:11:41.333998Z'), 1793326301333998)

    def test_non_utc_unbounded_missing_or_invalid_time_rejects(self):
        for value in ['2026-10-30 02:11:41.333998+0100','2026-10-30 02:11:41.333998999+0000',
                      '2026-10-30 02:11:41','private',None]:
            with self.assertRaises(ValueError): review.timestamp_us(value)

    def test_exact_native_rows_preserve_original_boundary_without_text(self):
        actual = review.reduce_rows(json.dumps(rows()).encode(), review.SPECS[1])
        self.assertEqual(actual['applications'][0]['events'],10)
        self.assertEqual(actual['applications'][0]['first_event_to_first_insertion_us'],29000000)
        self.assertEqual(actual['proxy_to_daemon_running_us'],30000000)
        self.assertTrue(actual['applications'][0]['same_thread'])
        self.assertNotIn('eventMessage', json.dumps(actual))
        self.assertNotIn('Keyboard inserts text', json.dumps(actual))

    def test_wrong_app_missing_event_duplicate_and_malformed_reject(self):
        for mutate in [lambda r:r.pop(),lambda r:r.append(r[-1]),
                       lambda r:r[-1].update(processID=1), lambda r:r[-1].update(threadID='136986'),
                       lambda r:r[0].update(eventMessage='private')]:
            r=rows();mutate(r)
            with self.assertRaises(ValueError):review.reduce_rows(json.dumps(r).encode(),review.SPECS[1])

    def test_arbitrary_private_rows_do_not_enter_public_receipt(self):
        r=rows()+[dict(processID=49787,threadID=136986,subsystem='com.apple.UIKit',category='KeyboardUI',
                      eventMessage='secret typed value',timestamp='never read')]
        self.assertNotIn('secret',json.dumps(review.reduce_rows(json.dumps(r).encode(),review.SPECS[1])))

    def test_unknown_haptic_thread_is_not_attributed_to_insertion(self):
        r=rows()+[dict(processID=49787,threadID=900,subsystem='com.apple.UIKit',category='UIKBFeedbackGenerator',
                      eventMessage='Keyboard inserts text',timestamp='never read')]
        self.assertTrue(review.reduce_rows(json.dumps(r).encode(),review.SPECS[1])['applications'][0]['same_thread'])

    def test_distinct_native_insertion_thread_remains_distinct(self):
        r=rows();r[-1]['threadID']=100
        self.assertFalse(review.reduce_rows(json.dumps(r).encode(),review.SPECS[1])['applications'][0]['same_thread'])

    def test_oversized_wrong_type_or_too_many_rows_reject(self):
        for raw in [b' '* (2*1024**2+1), b'{}', json.dumps([{}]*2001).encode()]:
            with self.assertRaises(ValueError):review.reduce_rows(raw,review.SPECS[1])

    def test_manifests_are_the_two_complete_fixed_original_archives(self):
        r=json.loads((HERE/'retained_keyboard_manifests.json').read_text())
        self.assertEqual({k:len(v) for k,v in r.items()},{'41502134475':276,'40987141694':343})
        for manifest in r.values():
            self.assertIn('Info.plist',manifest)
            self.assertLess(sum(v['bytes'] for v in manifest.values()),512*1024**2)
            for name,entry in manifest.items():
                self.assertNotIn('..',Path(name).parts)
                self.assertNotIn('Users/',name)
                self.assertRegex(entry['sha256'],r'^[0-9a-f]{64}$')

    def test_directory_manifest_rejects_link_and_reports_exact_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'a').write_bytes(b'trace')
            self.assertEqual(review.directory_manifest(root),{'a':dict(sha256=review.sha(b'trace'),bytes=5)})
            (root/'link').symlink_to(root/'a')
            with self.assertRaises(ValueError):review.directory_manifest(root)

    def test_metadata_keeps_expected_failure_and_push_success_distinct(self):
        for spec in review.SPECS:
            run=dict(id=spec['run'],status='completed',conclusion=spec['conclusion'],run_attempt=1,
                head_sha=spec['head'],event=spec['event'],path=spec['workflow'],
                repository={'full_name':review.REPO},head_repository={'full_name':review.REPO})
            artifact=dict(id=spec['artifact'],expired=False,size_in_bytes=spec['bytes'],name=spec['artifact_name'],
                digest='sha256:'+spec['digest'],workflow_run={'id':spec['run'],'head_sha':spec['head']})
            review.validate_metadata(run,artifact,spec)
            for key,value in [('conclusion','cancelled'),('run_attempt',2),('head_sha','0'*40),('event','workflow_dispatch')]:
                changed=copy.deepcopy(run);changed[key]=value
                with self.assertRaises(ValueError):review.validate_metadata(changed,artifact,spec)
            changed=copy.deepcopy(artifact);changed['expired']=True
            with self.assertRaises(ValueError):review.validate_metadata(run,changed,spec)

    def test_unsafe_or_duplicate_zip_entries_reject_before_extraction(self):
        for name in ['../private','/private','a/../private','a\\private','a\nprivate']:
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);archive=root/'a.zip';out=root/'out';out.mkdir()
                with zipfile.ZipFile(archive,'w') as z:z.writestr(name,b'raw')
                spec=dict(review.SPECS[1],bytes=archive.stat().st_size,digest=review.sha(archive.read_bytes()))
                with self.assertRaises(ValueError):review.unpack(archive,out,spec)
                self.assertEqual(list(out.iterdir()),[])

    def test_zip_links_and_unbound_artifact_bytes_reject(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);archive=root/'a.zip';out=root/'out';out.mkdir()
            link=zipfile.ZipInfo('a');link.external_attr=(stat.S_IFLNK|0o777)<<16
            with zipfile.ZipFile(archive,'w') as z:z.writestr(link,b'../private')
            spec=dict(review.SPECS[1],bytes=archive.stat().st_size,digest=review.sha(archive.read_bytes()))
            with self.assertRaises(ValueError):review.unpack(archive,out,spec)
            spec['digest']='0'*64
            with self.assertRaises(ValueError):review.unpack(archive,out,spec)

    def test_workflow_uploads_only_closed_receipt_and_cannot_run_application(self):
        s=(review.ROOT/'.github/workflows/ios-retained-keyboard-review.yml').read_text()
        self.assertIn('runs-on: macos-15',s)
        self.assertIn('timeout-minutes: 10',s)
        self.assertIn('contents: read',s)
        self.assertIn('actions: read',s)
        self.assertNotIn('contents: write',s)
        self.assertNotIn('xcodebuild',s)
        self.assertNotIn('gradlew',s)
        self.assertNotIn('simctl',s)
        self.assertEqual([l for l in s.splitlines() if l.strip().startswith('path:')],
            ['          path: ${{ runner.temp }}/parlor-retained-keyboard-${{ github.run_id }}-${{ github.run_attempt }}/receipt.json'])

if __name__ == '__main__':unittest.main()
