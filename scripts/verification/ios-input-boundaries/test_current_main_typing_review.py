"""Controls for one source-frozen archive read; not application acceptance."""
import copy
import json
from pathlib import Path
import unittest

import review_current_main_typing as review


class CurrentMainTypingReviewTest(unittest.TestCase):
    def metadata(self):
        spec = review.SPECS[0]
        run = dict(id=spec['run'], status='completed', conclusion='failure', run_attempt=1,
                   head_sha=spec['head'], event='push', path=spec['workflow'],
                   repository=dict(full_name=review.REPO), head_repository=dict(full_name=review.REPO))
        artifact = dict(id=spec['artifact'], expired=False, size_in_bytes=spec['bytes'],
                        name=spec['artifact_name'], digest='sha256:' + spec['digest'],
                        workflow_run=dict(id=spec['run'], head_sha=spec['head']))
        return run, artifact, spec

    def test_exact_new_failed_main_not_old_successful_archives(self):
        run, artifact, spec = self.metadata()
        self.assertEqual(len(review.SPECS), 1)
        review.validate_metadata(run, artifact, spec)
        self.assertEqual(spec['apps'], [41116])
        self.assertEqual(spec['start'], '2026-11-05 13:16:17')
        self.assertEqual(spec['end'], '2026-11-05 13:16:58')

    def test_foreign_source_event_attempt_or_result_rejects(self):
        run, artifact, spec = self.metadata()
        for key, value in [('id', 41502134475), ('head_sha', '0' * 40), ('event', 'pull_request'),
                           ('run_attempt', 2), ('status', 'in_progress'), ('conclusion', 'success')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                review.validate_metadata(dict(run, **{key: value}), artifact, spec)

    def test_foreign_or_expired_artifact_rejects(self):
        run, artifact, spec = self.metadata()
        for key, value in [('id', 13170523744), ('expired', True), ('size_in_bytes', 1),
                           ('digest', 'sha256:' + '0' * 64), ('name', 'other')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                review.validate_metadata(run, dict(artifact, **{key: value}), spec)
        invalid = copy.deepcopy(artifact)
        invalid['workflow_run']['head_sha'] = '0' * 40
        with self.assertRaises(ValueError):
            review.validate_metadata(run, invalid, spec)

    def test_frozen_manifest_is_bounded_and_includes_original_failure_payloads(self):
        manifests = json.loads(Path(review.__file__).with_name('current_main_typing_manifest.json').read_text())
        self.assertEqual(set(manifests), {'42030154806'})
        manifest = manifests['42030154806']
        self.assertEqual(len(manifest), 1283)
        self.assertIn('Info.plist', manifest)
        self.assertEqual(manifest['Data/data.0~2C2OZ4IneHKmh9uzIsKb1ceu7jtUu3MQGsa8fBH97DKFh6T8oCUVNo_oUZduA4k6h6ITLgM7S23uWuRyBuNV9w=='],
                         '58dcf92a39454302bd91b7929dcc10efbe432d839e6c004b7ebefbf0275284e2')
        self.assertTrue(all(len(digest) == 64 and '..' not in Path(name).parts and
                            not Path(name).is_absolute() for name, digest in manifest.items()))

    def native_rows(self):
        spec = review.SPECS[0]
        def row(second, pid, message, subsystem='com.apple.UIKit', category='KeyboardUI'):
            return dict(timestamp='2026-11-05 13:16:' + second + '+0000', processID=pid,
                        threadID=100, eventMessage=message, subsystem=subsystem, category=category)
        rows = [row('21.%06d' % (200000 + i * 5000), spec['apps'][0],
                    'Keyboard receives keyEvent type: 4; subtype: 0') for i in range(10)]
        rows += [row('53.%06d' % (100000 + i * 5000), spec['apps'][0],
                     'Keyboard inserts text') for i in range(10)]
        rows += [row(second, 101, message, 'com.apple.TextInput.kbd') for second, message in (
            ('18.000000', 'service state: spawning'), ('18.005000', 'xpcproxy_sim spawned with pid 102'),
            ('18.006000', 'service state: xpcproxy'), ('53.000000', 'Successfully spawned kbd[102] because ipc (mach)'),
            ('53.001000', 'service state: running'))]
        return rows

    def test_exact_native_records_reduce_to_numeric_facts_without_text(self):
        rows = self.native_rows()
        rows.append(dict(timestamp='2026-11-05 13:16:24.000000+0000', processID=41116,
                         threadID=100, eventMessage='DO_NOT_EXPORT_INPUT', subsystem='unknown'))
        reduced = review.reduce_rows(json.dumps(rows).encode(), review.SPECS[0])
        self.assertEqual(reduced['applications'][0]['events'], 10)
        self.assertEqual(reduced['applications'][0]['insertions'], 10)
        self.assertEqual(reduced['proxy_to_daemon_running_us'], 34995000)
        encoded = json.dumps(reduced)
        self.assertNotIn('DO_NOT_EXPORT_INPUT', encoded)
        self.assertNotIn('eventMessage', encoded)
        self.assertNotIn('timestamp', encoded)

    def test_missing_duplicate_or_foreign_native_records_never_become_success(self):
        rows = self.native_rows()
        for invalid in (rows[1:], rows + [rows[0]], [dict(r, processID=999) for r in rows]):
            with self.subTest(size=len(invalid)), self.assertRaises(ValueError):
                review.reduce_rows(json.dumps(invalid).encode(), review.SPECS[0])

    def test_non_utc_and_unbounded_native_output_rejects(self):
        with self.assertRaises(ValueError):
            review.timestamp_us('2026-11-05 13:16:21+0100')
        with self.assertRaises(ValueError):
            review.reduce_rows(b' ' * (2 * 1024**2 + 1), review.SPECS[0])

    def test_exact_security_admission_rejects_any_changed_companion(self):
        import sys
        import tempfile
        sys.path.insert(0, str(review.ROOT))
        from scripts.release import workflow_contract as contract
        contract.verify_current_main_typing_review()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in contract.CURRENT_MAIN_TYPING_CONTROLS:
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((review.ROOT / name).read_bytes())
            contract.verify_current_main_typing_review(root)
            for name in contract.CURRENT_MAIN_TYPING_CONTROLS:
                target = root / name
                original = target.read_bytes()
                target.write_bytes(original + b'\n# unreviewed change\n')
                with self.subTest(name=name), self.assertRaises(RuntimeError):
                    contract.verify_current_main_typing_review(root)
                target.write_bytes(original)

    def test_read_only_runner_and_receipt_only_publication(self):
        source = Path(review.__file__).read_text()
        workflow = (review.ROOT / '.github/workflows/ios-main-typing-archive-review.yml').read_text()
        self.assertIn('runs-on: macos-15', workflow)
        self.assertIn('timeout-minutes: 10', workflow)
        self.assertIn('actions: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn('/receipt.json', workflow.split('path: ')[-1])
        self.assertNotIn('./gradlew', workflow)
        self.assertNotIn('typeText(', source)
        self.assertNotIn('simctl', source)
        self.assertIn("manifest == expected[str(spec['run'])]", source)
        self.assertIn("native_archive_status='UNAVAILABLE_IN_ORIGINAL_RESULT'", source)
        self.assertIn("application_executed=False, input_replayed=False, shipping_qualified=False", source)


if __name__ == '__main__':
    unittest.main()
