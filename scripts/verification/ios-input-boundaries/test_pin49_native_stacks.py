import hashlib
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import pin49_native_stacks as c

SIM = '12345678-1234-1234-1234-123456789ABC'
INSTALL = 'ABCDE123-1234-1234-1234-123456789ABC'


class NativeStacksControls(unittest.TestCase):
    def test_same_original_commands_deadlines_and_errors_are_forwarded_without_observer_elsewhere(self):
        capture = Mock(return_value=('actual', b'out', b'err'))
        with patch.object(c, 'NativeSampler') as observer:
            self.assertEqual(c.execute_observed(capture, ['original'], 'compose-build', 2700),
                             ('actual', b'out', b'err'))
            observer.assert_not_called()
            capture.assert_called_once_with(['original'], 'compose-build', 2700)

    def test_only_exact_owned_test_command_can_be_observed_and_actual_exit65_is_not_changed(self):
        with TemporaryDirectory() as d:
            dest = Path(d).resolve(strict=True)/'compose'; dest.mkdir()
            (dest/'product.json').write_text(json.dumps({'Parlor': 'a'*64}))
            argv = ['/bin/bash', '--', '/usr/bin/xcodebuild', '-destination',
                    'platform=iOS Simulator,id='+SIM, 'test-without-building']
            kwargs = {'environment': {'PARLOR_INPUT_LOG': str(dest/'test.log')}}
            capture = Mock(return_value=({'exit_code': 65}, b'out', b'err'))
            with patch.object(c, 'NativeSampler') as observer:
                self.assertEqual(c.execute_observed(capture, argv, 'compose-tests', 900, **kwargs),
                                 ({'exit_code': 65}, b'out', b'err'))
                capture.assert_called_once_with(argv, 'compose-tests', 900, **kwargs)
                observer.assert_called_once_with(dest/'native-stacks', SIM, 'a'*64)
                with self.assertRaises(ValueError):
                    c.execute_observed(capture, argv, 'compose-tests', 1200, **kwargs)
                self.assertEqual(capture.call_count, 1)

    def test_stack_report_requires_actual_pid_and_thread_graph_not_fake_file_or_other_process(self):
        with TemporaryDirectory() as d:
            path = Path(d).resolve(strict=True)/'sample.txt'
            for raw in (b'', b'Analysis of sampling Parlor (pid 24)\nCall graph:\nThread_1',
                        b'Analysis of sampling Parlor (pid 23)\nNo samples'):
                path.write_bytes(raw)
                with self.assertRaises(ValueError): c.read_sample(path, 23)
            raw = b'Analysis of sampling Parlor (pid 23) every 10 milliseconds\nCall graph:\nThread_1 kfun:CPU'
            path.write_bytes(raw)
            self.assertEqual(c.read_sample(path, 23), {'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)})
            link = Path(d).resolve(strict=True)/'link'; link.symlink_to(path)
            with self.assertRaises(ValueError): c.read_sample(link, 23)

    def test_only_exact_owned_installed_debug_executable_admitted_never_other_device_bundle_or_bytes(self):
        with TemporaryDirectory() as d:
            home = Path(d).resolve(strict=True)
            app = home/'Library/Developer/CoreSimulator/Devices'/SIM/'data/Containers/Bundle/Application'/INSTALL/'Parlor.app'
            app.mkdir(parents=True)
            exe = app/'Parlor'; exe.write_bytes(b'actual-built-image')
            info = app/'Info.plist'
            info.write_bytes(plistlib.dumps({'CFBundleIdentifier': c.BUNDLE, 'CFBundleExecutable': 'Parlor'}))
            digest = hashlib.sha256(exe.read_bytes()).hexdigest()
            raw = ('p23\nftxt\nn'+str(exe)+'\nn/System/Library/A.framework/A\n').encode()
            self.assertEqual(c.owned_images(raw, SIM, digest, home), [{'image': str(exe), 'executable_sha256': digest}])
            self.assertEqual(c.owned_images(raw, INSTALL, digest, home), [])
            # Actual lsof filename field is n; descriptor-shaped/fabricated f paths cannot admit an image.
            self.assertEqual(c.owned_images(raw.replace(b'\nn/', b'\nf/'), SIM, digest, home), [])
            with self.assertRaises(ValueError): c.owned_images(raw, SIM, 'a'*64, home)
            info.write_bytes(plistlib.dumps({'CFBundleIdentifier': 'com.parlor.app', 'CFBundleExecutable': 'Parlor'}))
            with self.assertRaises(ValueError): c.owned_images(raw, SIM, digest, home)

    def test_tool_never_inherits_credentials_or_injection_and_commands_cannot_request_longer_sampling(self):
        with patch.dict(os.environ, {'GITHUB_TOKEN': 'secret', 'DYLD_INSERT_LIBRARIES': 'hook', 'HOME': '/owned'}):
            self.assertNotIn('GITHUB_TOKEN', c.environment())
            self.assertNotIn('DYLD_INSERT_LIBRARIES', c.environment())
        self.assertEqual(c.sample_command(23, Path('/owned/sample')), ['/usr/bin/sample', '23', '5', '10', '-mayDie', '-file', '/owned/sample'])
        for pid, seconds in ((False, 5), (0, 5), (-1, 5), (23, 30)):
            with self.assertRaises(ValueError): c.sample_command(pid, Path('/owned/sample'), seconds)

    def test_source_never_changes_original_driver_or_queries_or_samples_all_processes(self):
        import run_pin49
        text = Path(run_pin49.__file__).read_text()
        self.assertIn('run_pin03.main(failure_runloop=True)', text)
        self.assertIn('QUERY_PROFILE', text)
        for forbidden in ('renderer_for_profile =', 'typeText(', '.tap()', '-retry-tests', '--all-processes', 'sudo'):
            self.assertNotIn(forbidden, text)

    def test_sampler_observation_failure_is_not_a_success_and_all_child_errors_are_preserved(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                sampler.error = 'ActualAttachFailure'
                sampler.thread = Mock(); sampler.thread.is_alive.return_value = False
                with self.assertRaises(ValueError): sampler.__exit__(None, None, None)
                saved = json.loads((sampler.directory/'samples.json').read_text())
                self.assertEqual(saved['error'], 'ActualAttachFailure')
                self.assertFalse(saved['shipping_qualified'])
                self.assertFalse(saved['native_timing_neutrality'])

    def test_live_native_sampler_failure_cannot_be_treated_as_normal_app_termination(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                run = Mock(side_effect=[subprocess.CompletedProcess([], 1), subprocess.CompletedProcess([], 0, stdout=b'23\n')])
                with patch.object(c.subprocess, 'run', run), self.assertRaises(ValueError):
                    sampler._sample({'pid': 23, 'image': '/owned/Parlor', 'executable_sha256': 'a'*64})
                self.assertEqual(sampler.rows[0]['exit'], 1)
                self.assertNotEqual(sampler.rows[0]['status'], 'SAMPLED')

    def test_real_path_readback_rejects_changed_report_and_accepts_only_explicit_producer_directory(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                path = sampler.directory/'sample-001.txt'
                path.write_bytes(b'Analysis of sampling Parlor (pid 23)\nCall graph:\nThread_1')
                sampler.discoveries = 1
                sampler.rows = [{'index': 1, 'report': path.name, 'pid': 23, 'image': '/owned/Parlor',
                    'executable_sha256': 'a'*64, 'started_ms': sampler.started_ms+1,
                    'ended_ms': sampler.started_ms+5001, 'started_unix': 1, 'ended_unix': 6,
                    'command': c.sample_command(23, path), 'sampler_child_reaped': True,
                    'exit': 0, 'status': 'SAMPLED', **c.read_sample(path, 23)}]
                sampler._checkpoint()
                self.assertEqual(c.verify_receipt(sampler.directory, SIM, 'a'*64)['sampled_reports'], 1)
                with self.assertRaises(ValueError):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64, producer_directory=Path('/different'))
                path.write_bytes(b'Analysis of sampling Parlor (pid 23)\nCall graph:\nThread_2')
                with self.assertRaises(ValueError): c.verify_receipt(sampler.directory, SIM, 'a'*64)

    @unittest.skipUnless(sys.platform == 'darwin', 'Actual Apple OS sampler unavailable locally')
    def test_actual_os_tool_control_reaps_child_and_retains_real_stack_report(self):
        with TemporaryDirectory() as d:
            # CI retains real capability bytes; a local Mac smoke test owns its temp output.
            directory = Path(os.environ['PARLOR_PIN49_CONTROL']) if 'PARLOR_PIN49_CONTROL' in os.environ else Path(d).resolve(strict=True)/'control'
            value = c.tool_control(directory)
            self.assertEqual(value['exit'], 0)
            self.assertTrue(value['child_reaped'])
            self.assertFalse(value['simulator_attach_verified'])
