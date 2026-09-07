"""Synthetic coordinator contracts, not Android/Java/Gradle runtime evidence.

The historical notice-only _01 draft is retained unchanged. Its four assertions
are repeated here with only the new full-package boundary mocked. The full
boundary, artifact identity, and finalization are separately exercised below.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import zipfile

CONTROL = Path(__file__).resolve().parents[1] / 'run_gradle_cycle.py'
SPEC = importlib.util.spec_from_file_location('aab_package_cycle02', CONTROL)
cycle = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cycle)


class PackageBoundaryTests(unittest.TestCase):
    def setUp(self):
        allocation = TemporaryDirectory(prefix='parlor-aab-package-test-')
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()
        self.dest = self.root / 'evidence'
        (self.dest / 'scratch').mkdir(parents=True)
        (self.dest / 'artifacts').mkdir()
        self.app = self.root / 'composeApp/build/outputs/bundle/release/fixture.aab'
        self.app.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.app, 'w') as archive:
            archive.writestr('base/dex/classes.dex', b'synthetic, not executable')
        self.digest = hashlib.sha256(self.app.read_bytes()).hexdigest()
        self.tool = self.dest / 'scratch/bundletool-1.jar'
        self.policy = self.root / 'config/release-policy.json'
        self.policy.parent.mkdir()
        self.policy.write_text(json.dumps({'tools': {'bundletool': {'url': 'https://example.invalid/fixture.jar'}}}))

    def inspect_notice(self, data, code=0, full_error=None, digest=None):
        def command(args, **options):
            self.assertEqual(['/usr/bin/python3', '-B', str(self.root / 'scripts/verification/third_party_notices.py'),
                              '--root', str(self.root), '--package', str(self.app), '--json'], args)
            self.assertEqual(60, options['timeout'])
            options['stdout'].write(data)
            return SimpleNamespace(returncode=code)
        with patch.object(cycle, 'ROOT', self.root), patch.object(cycle.subprocess, 'run', side_effect=command) as run, \
                patch.object(cycle, 'inspect_android_package', side_effect=full_error,
                             return_value={'artifact_sha256': digest or self.digest}) as full:
            cycle.inspect_generated_artifacts(self.dest, {'synthetic': 'env'})
            run.assert_called_once()
            full.assert_called_once_with(self.app, self.dest, 1, {'synthetic': 'env'})

    def test_success_keeps_byte_bound_notice_and_full_receipt_before_cleanup(self):
        self.inspect_notice(b'{"status":"PASS","package":{"verified":26}}')
        records = json.loads((self.dest / 'artifact-receipts.json').read_text())
        self.assertEqual(1, len(records))
        record = records[0]
        report = self.dest / record['notice_receipt']
        self.assertEqual(hashlib.sha256(report.read_bytes()).hexdigest(), record['notice_receipt_sha256'])
        self.assertEqual(self.digest, record['sha256'])
        self.assertEqual(self.digest, record['complete_package_receipt']['artifact_sha256'])
        self.assertTrue(self.app.is_file())

    def test_failed_notice_keeps_artifact_and_failure_report(self):
        with self.assertRaisesRegex(RuntimeError, 'retain artifact'):
            self.inspect_notice(b'{"status":"FAIL"}', code=2)
        self.assertTrue(self.app.is_file())
        self.assertEqual(b'{"status":"FAIL"}', (self.dest / 'artifacts/android-notices-1.json').read_bytes())

    def test_source_only_success_cannot_satisfy_package_gate(self):
        with self.assertRaisesRegex(RuntimeError, 'no successful package'):
            self.inspect_notice(b'{"status":"PASS"}')
        self.assertTrue(self.app.is_file())

    def test_oversized_notice_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'inspection failed'):
            self.inspect_notice(b' ' * (1024 * 1024 + 1))

    def test_full_gate_failure_cannot_be_hidden_by_notice_success(self):
        with self.assertRaisesRegex(RuntimeError, 'synthetic full gate failed'):
            self.inspect_notice(b'{"status":"PASS","package":true}', full_error=RuntimeError('synthetic full gate failed'))
        self.assertTrue(self.app.is_file())
        self.assertFalse((self.dest / 'artifact-receipts.json').exists())

    def test_full_receipt_must_bind_exact_artifact_digest(self):
        with self.assertRaisesRegex(RuntimeError, 'same inspected artifact'):
            self.inspect_notice(b'{"status":"PASS","package":true}', digest='f' * 64)
        self.assertTrue(self.app.is_file())

    def full(self, data=None, code=0, download_error=None):
        def download(args, **options):
            self.assertEqual('curl', args[0])
            self.assertIn('--proto', args)
            self.assertIn('=https', args)
            self.assertEqual(650, options['timeout'])
            self.assertEqual('300', args[args.index('--max-time') + 1])
            self.assertEqual('1', args[args.index('--retry') + 1])
            self.tool.write_bytes(b'disposable synthetic tool, never executed')
            if download_error:
                raise download_error
        def invoke(args, report, env, timeout):
            self.assertEqual('/usr/bin/python3', args[0])
            self.assertEqual(str(self.root / 'scripts/verification/android_release_artifacts.py'), args[2])
            self.assertEqual(str(self.app), args[args.index('--package') + 1])
            self.assertEqual(str(self.tool), args[args.index('--bundletool') + 1])
            self.assertEqual({'JAVA_HOME': 'synthetic JDK21'}, env)
            self.assertEqual(600, timeout)
            report.write_bytes(data if data is not None else json.dumps({'status': 'PASS', 'artifact_sha256': self.digest}).encode())
            return code
        with patch.object(cycle, 'ROOT', self.root), patch.object(cycle.subprocess, 'run', side_effect=download), \
                patch.object(cycle, 'invoke', side_effect=invoke):
            return cycle.inspect_android_package(self.app, self.dest, 1, {'JAVA_HOME': 'synthetic JDK21'})

    def test_full_gate_uses_tracked_invoke_and_removes_tool_after_success(self):
        result = self.full()
        self.assertEqual(self.digest, result['artifact_sha256'])
        self.assertFalse(self.tool.exists())
        self.assertEqual(hashlib.sha256((self.dest / result['path']).read_bytes()).hexdigest(), result['sha256'])

    def test_full_gate_nonzero_retains_report_but_removes_tool(self):
        with self.assertRaisesRegex(RuntimeError, 'inspection failed'):
            self.full(b'{"status":"FAIL"}', code=1)
        self.assertEqual(b'{"status":"FAIL"}', (self.dest / 'artifacts/android-package-1.json').read_bytes())
        self.assertFalse(self.tool.exists())

    def test_full_gate_requires_artifact_success_and_bounded_report(self):
        for data, pattern in ((b'{"status":"PASS"}', 'artifact-bound'),
                              (b' ' * (4 * 1024 * 1024 + 1), 'inspection failed')):
            with self.subTest(pattern=pattern), self.assertRaisesRegex(RuntimeError, pattern):
                self.full(data)
            self.assertFalse(self.tool.exists())
            (self.dest / 'artifacts/android-package-1.json').unlink()

    def test_failed_or_cancelled_download_cleans_only_its_tool(self):
        sentinel = self.dest / 'scratch/unrelated.txt'
        sentinel.write_bytes(b'preserve')
        for error in (subprocess.TimeoutExpired('synthetic', 650), KeyboardInterrupt()):
            with self.subTest(error=type(error).__name__), self.assertRaises(type(error)):
                self.full(download_error=error)
            self.assertFalse(self.tool.exists())
            self.assertEqual(b'preserve', sentinel.read_bytes())

    def test_preexisting_tool_is_not_read_removed_or_overwritten(self):
        self.tool.write_bytes(b'preserve pre-existing bytes')
        with self.assertRaisesRegex(RuntimeError, 'pre-existing task tool'):
            self.full()
        self.assertEqual(b'preserve pre-existing bytes', self.tool.read_bytes())


class FinalizationTests(unittest.TestCase):
    def exercise(self, error=None, artifact_workers=None):
        with TemporaryDirectory(prefix='parlor-cycle-finalizer-test-') as temporary:
            root = Path(temporary).resolve()
            out = root / 'campaign'
            out.mkdir()
            output = root / 'module/build'
            order = []
            def invoke(command, logfile, env):
                if command == ['synthetic-task']:
                    output.mkdir(parents=True)
                    (output / 'required.bin').write_bytes(b'synthetic evidence')
                    order.append('build')
                else:
                    self.assertEqual(['./gradlew', '--stop'], command)
                    order.append('stop')
                logfile.write_text('synthetic command, no workers started\n')
                return 0
            def collect(*_args):
                order.append('reports')
            def inspect(*_args):
                order.append('artifact')
                if error:
                    raise error
            def workers():
                order.append('workers')
                return artifact_workers if order.count('workers') == 2 and artifact_workers else {'remaining_owned_workers': []}
            identity = {'source_manifest_sha256': 'synthetic identity'}
            with patch.object(cycle, 'ROOT', root), patch.object(cycle, 'OUT', out), \
                    patch.object(cycle, 'owned_outputs', return_value=[output]), \
                    patch.object(cycle, 'processes', return_value={}), \
                    patch.object(cycle, 'identity', return_value=identity), \
                    patch.object(cycle, 'runner_identity', return_value={'manifest_sha256': 'synthetic controls'}), \
                    patch.object(cycle.subprocess, 'check_output', return_value='/synthetic/jdk21\n'), \
                    patch.object(cycle, 'invoke', side_effect=invoke), \
                    patch.object(cycle, 'collect_reports', side_effect=collect), \
                    patch.object(cycle, 'inspect_generated_artifacts', side_effect=inspect), \
                    patch.object(cycle, 'stop_owned_workers', side_effect=workers), \
                    patch.object(cycle, 'DEFERRED_SIGNALS', []), \
                    patch.object(cycle.sys, 'argv', ['run_gradle_cycle.py', 'synthetic', '--command', 'synthetic-task']), \
                    patch.dict(os.environ, {'PARLOR_REMEDIATION_GRADLE_HEAP': '3g'}), contextlib.redirect_stdout(io.StringIO()):
                status = cycle.main()
            self.assertEqual(['build', 'stop', 'workers', 'reports', 'artifact', 'workers'], order)
            receipt = json.loads((out / 'evidence/synthetic/receipt.json').read_text())
            if error or artifact_workers:
                self.assertNotEqual(0, status)
                self.assertEqual('FAIL', receipt['status'])
                self.assertTrue((output / 'required.bin').is_file())
                self.assertEqual(['module/build'], receipt['remaining_outputs'])
            else:
                self.assertEqual(0, status)
                self.assertEqual('PASS', receipt['status'])
                self.assertFalse(output.exists())
                self.assertFalse((out / 'evidence/synthetic/scratch').exists())
            self.assertIsNotNone(receipt['artifact_workers'])

    def test_success_retires_postinspection_workers_before_output_cleanup(self):
        self.exercise()

    def test_failed_inspection_still_retires_workers_and_retains_required_evidence(self):
        self.exercise(error=RuntimeError('synthetic inspector failure'))

    def test_surviving_artifact_worker_prevents_output_deletion(self):
        self.exercise(artifact_workers={'remaining_owned_workers': [{'pid': 1, 'synthetic': True}]})

    def test_postspawn_clock_failure_terminates_and_reaps_owned_group(self):
        process = Mock(pid=41000)
        process.poll.return_value = None
        with TemporaryDirectory(prefix='parlor-cycle-postspawn-test-') as directory, \
                patch.object(cycle.subprocess, 'Popen', return_value=process), \
                patch.object(cycle.time, 'monotonic', side_effect=KeyboardInterrupt()), \
                patch.object(cycle.os, 'killpg') as kill:
            with self.assertRaises(KeyboardInterrupt):
                cycle.invoke(['never-executed'], Path(directory) / 'log', {})
            kill.assert_called_once_with(41000, cycle.signal.SIGTERM)
            process.wait.assert_called_once_with(timeout=30)
            self.assertNotIn(41000, cycle.OWNED_GROUPS)


if __name__ == '__main__':
    unittest.main(verbosity=2)
