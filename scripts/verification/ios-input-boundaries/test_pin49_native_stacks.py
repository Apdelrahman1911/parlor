import hashlib
import ctypes
import io
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
import pin49_kernel_channel as channel

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
        self.assertEqual(c.sample_command(23, Path('/owned/sample')), ['/usr/bin/sample', '23', '1', '10', '-mayDie', '-file', '/owned/sample'])
        self.assertEqual((c.SAMPLE_CADENCE_SECONDS, c.DISCOVERY_CADENCE_SECONDS), (5, 1))
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
                sampler.__exit__(None, None, None)
                with self.assertRaises(ValueError): c.verify_receipt(sampler.directory, SIM, 'a'*64)
                saved = json.loads((sampler.directory/'samples.json').read_text())
                self.assertEqual(saved['error'], 'ActualAttachFailure')
                self.assertFalse(saved['shipping_qualified'])
                self.assertFalse(saved['native_timing_neutrality'])

    def test_live_native_sampler_failure_cannot_be_treated_as_normal_app_termination(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                run = Mock(return_value=subprocess.CompletedProcess([], 1))
                with patch.object(c.subprocess, 'run', run), patch.object(sampler, '_discover', return_value=[{'pid': 23}]), self.assertRaises(ValueError):
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
                sampler.kernel = Mock()
                sampler.kernel.receipt.return_value = dict(schema=1, pid=42,
                    ready=dict(kind='ready', schema=1, selector='PROC_UID_ONLY', simulator=SIM,
                               own_process_seen=True, helper_pid=42), requests=1,
                    startup_seconds=0.1, response_seconds=[0.1], exit=0, child_reaped=True,
                    started_unix=1.0, ready_unix=2.0, started_before_original_build=True,
                    started_before_original_command=True, reply_timeout_seconds=3)
                sampler._checkpoint()
                self.assertEqual(c.verify_receipt(sampler.directory, SIM, 'a'*64)['sampled_reports'], 1)
                self.assertEqual(c.verify_receipt(sampler.directory, SIM, 'a'*64,
                    build_started_at='1970-01-01T00:00:03+00:00')['sampled_reports'], 1)
                with self.assertRaises(ValueError):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64,
                        build_started_at='1970-01-01T00:00:01+00:00')
                with self.assertRaises(ValueError):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64, producer_directory=Path('/different'))
                path.write_bytes(b'Analysis of sampling Parlor (pid 23)\nCall graph:\nThread_2')
                with self.assertRaises(ValueError): c.verify_receipt(sampler.directory, SIM, 'a'*64)

    def test_current_uid_kernel_paths_filter_other_devices_and_no_arguments_or_root_user_scan(self):
        import pin49_process_paths as paths
        own = os.getpid()
        proc = Mock()
        def listpids(selector, uid, values, size):
            self.assertEqual((selector, uid), (4, os.geteuid()))
            values[0], values[1], values[2] = own, 123, 0
            return 12
        def pidpath(pid, buffer, size):
            raw = (b'/owned/Python' if pid == own else
                ('/owned/Library/Developer/CoreSimulator/Devices/'+SIM+'/data/Containers/Bundle/Application/'+INSTALL+'/Parlor.app/Parlor').encode())
            buffer.value = raw
            return len(raw)
        proc.proc_listpids.side_effect = listpids
        proc.proc_pidpath.side_effect = pidpath
        receipt = paths.collect(proc, SIM)
        self.assertTrue(receipt['own_process_seen'])
        self.assertEqual(receipt['paths'][0]['pid'], 123)
        self.assertEqual(paths.collect(proc, INSTALL)['paths'], [])
        proc.proc_listpids.return_value = 4097*4; proc.proc_listpids.side_effect = None
        with self.assertRaises(ValueError): paths.collect(proc, SIM)

    def test_observer_timeout_still_fails_receipt_but_does_not_discard_original_test_exit(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                sampler.error = 'TimeoutExpired'
                with patch.object(sampler, '_discover', side_effect=subprocess.TimeoutExpired('native-child', 3)):
                    sampler.thread.start()  # Portable observer error, no fake Apple child startup.
                    sampler.thread.join(timeout=1)
                sampler.__exit__(None, None, None)
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
            import pin49_process_paths as paths
            metadata = paths.collect(paths.load_proc(), SIM)
            self.assertTrue(metadata['own_process_seen'])
            self.assertEqual(metadata['selector'], 'PROC_UID_ONLY')
            with (directory/'kernel-path-control.json').open('x') as out:
                json.dump(metadata, out, indent=2)
            child = channel.KernelChannel(SIM, c.environment())
            try:
                child.start()
                first, second = child.discover(), child.discover()
                self.assertEqual(first['helper_pid'], second['helper_pid'])
            finally:
                child.close()
            receipt = child.receipt()
            channel.verify(receipt, SIM, 2)
            with (directory/'kernel-child-control.json').open('x') as out:
                json.dump(receipt, out, indent=2)


FAKE_CHILD = '''import json,os,sys,time
sim,mode=sys.argv[1:]
base=dict(schema=1,selector='PROC_UID_ONLY',simulator=sim,own_process_seen=True,
          helper_pid=os.getpid(),enumerated=1,disappeared=0,paths=[])
print(json.dumps(dict(kind='ready',**base)),flush=True)
for index,line in enumerate(sys.stdin,1):
 if mode=='timeout':time.sleep(10)
 if mode=='oversize':print('x'*65537,flush=True);continue
 if mode=='exit':sys.exit(7)
 print(json.dumps(dict(kind='paths',index=index+1 if mode=='stale' else index,**base)),flush=True)
'''


class KernelChannelControls(unittest.TestCase):
    def owned(self, mode='normal'):
        child = channel.KernelChannel(SIM, c.environment())
        # Hermetic protocol child is explicitly NOT libproc/native evidence.
        with patch.object(channel, 'command', return_value=[sys.executable, '-u', '-c', FAKE_CHILD, SIM, mode]):
            child.start()
        return child

    def test_one_prestarted_process_handles_serial_requests_and_exits_on_eof(self):
        child = self.owned()
        try:
            first, second = child.discover(), child.discover()
            self.assertEqual(first['helper_pid'], second['helper_pid'])
            self.assertEqual([first['index'], second['index']], [1, 2])
            with self.assertRaises(ValueError): child.start()
        finally: child.close()
        channel.verify(child.receipt(), SIM, 2)
        self.assertEqual(child.exit, 0)
        self.assertTrue(child.reaped)

    def test_stale_oversized_and_ended_replies_fail_closed_and_reap_owned_child(self):
        for mode in ('stale', 'oversize', 'exit'):
            child = self.owned(mode)
            with self.assertRaises(ValueError): child.discover()
            self.assertTrue(child.reaped)
            with self.assertRaises(ValueError): child.discover()
            with self.assertRaises((ValueError, KeyError)): channel.verify(child.receipt(), SIM, 1)

    def test_reply_timeout_remains_bounded_and_aborts_reaps_without_retry(self):
        child = self.owned('timeout')
        with patch.object(channel, 'TIMEOUT', 0.05), self.assertRaises(subprocess.TimeoutExpired):
            child.discover()
        self.assertEqual(child.requests, 1)
        self.assertTrue(child.reaped)
        self.assertNotEqual(child.exit, 0)

    def test_production_command_has_exact_owned_simulator_no_loader_or_longer_timeout(self):
        self.assertEqual(channel.TIMEOUT, 3)
        self.assertEqual(channel.command(SIM)[-2:], [SIM, '--serve'])
        self.assertEqual(channel.command(SIM)[:2], [sys.executable, '-B'])

    def test_duplicate_protocol_keys_and_changed_custody_are_rejected(self):
        with self.assertRaises(ValueError): channel.duplicate([('schema', 1), ('schema', 1)])
        child = self.owned()
        try: child.discover()
        finally: child.close()
        for field,value in [('exit', 1), ('child_reaped', False), ('reply_timeout_seconds', 4),
                            ('started_before_original_command', False), ('response_seconds', [3.1])]:
            receipt = child.receipt();receipt[field] = value
            with self.assertRaises(ValueError): channel.verify(receipt, SIM, 1)

    def test_native_server_rejects_unrecognized_request_before_any_discovery(self):
        import pin49_process_paths as paths
        result = dict(schema=1,selector='PROC_UID_ONLY',simulator=SIM,own_process_seen=True,
                      enumerated=1,disappeared=0,paths=[])
        for raw in (b'LONGREQUEST\n', b'DISCOVER!\n'):
            with patch.object(paths, 'collect', return_value=result) as collect, self.assertRaises(ValueError):
                paths.serve(Mock(), SIM, io.BytesIO(raw), Mock())
            self.assertEqual(collect.call_count, 1)  # Readiness only; no unauthorized request.

    def test_native_readiness_precedes_original_command_and_helper_reaping_precedes_cleanup(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler = c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            events = [];sampler.thread = Mock();sampler.thread.is_alive.return_value = False
            sampler.thread.start.side_effect = lambda: events.append('observe')
            helper = Mock();helper.receipt.return_value = {'portable_fixture_only': True}
            helper.start.side_effect = lambda: events.append('ready')
            helper.close.side_effect = lambda **_: events.append('reaped')
            with patch.object(c.kernel, 'KernelChannel', return_value=helper):
                with sampler: events.append('unchanged-original-command')
            events.append('owned-simulator-cleanup')
            self.assertEqual(events, ['ready','observe','unchanged-original-command','reaped','owned-simulator-cleanup'])

    def test_failed_native_readiness_cannot_launch_original_command_or_observer(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler = c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            sampler.thread = Mock()
            helper = Mock();helper.receipt.return_value = {'portable_fixture_only': True}
            helper.start.side_effect = subprocess.TimeoutExpired('owned-ready', 3)
            with patch.object(c.kernel, 'KernelChannel', return_value=helper), self.assertRaises(subprocess.TimeoutExpired):
                with sampler: self.fail('Original command must not start without native readiness')
            sampler.thread.start.assert_not_called()
            helper.close.assert_called_once_with(abort=True)

    def test_response_ready_before_poll_but_observed_after_deadline_is_not_accepted(self):
        child = self.owned()
        try:
            # A ready fd does not prove timely observation if the host observer
            # was descheduled beyond its deadline. No real Apple inference.
            with patch.object(channel.select, 'select', return_value=([child.child.stdout], [], [])), \
                    patch.object(channel.time, 'monotonic', side_effect=[1.0, 3.231]), \
                    patch.object(channel.os, 'read') as read, self.assertRaises(subprocess.TimeoutExpired):
                child._read(3.0)
            read.assert_not_called()
        finally: child.close()

    def test_prebuild_coordinator_starts_one_child_before_original_build_and_reuses_it_for_tests(self):
        self.prebuild_control(fail_ready=False)

    def test_failed_prebuild_readiness_preserves_failure_and_cannot_start_compilation(self):
        self.prebuild_control(fail_ready=True)

    def prebuild_control(self, *, fail_ready):
        import run_pin49, run_pin03, pin47_failure_runloop
        events = []
        class OriginalCommands:
            def capture(inner, arguments, label, timeout, **kwargs):
                events.append(label)
                return ({'exit_code': 65}, b'actual', b'')
        helper = Mock();helper.receipt.return_value = {'portable_fixture_only': True}
        helper.start.side_effect = subprocess.TimeoutExpired('prebuild-ready', 3) if fail_ready else lambda: events.append('ready')
        helper.close.side_effect = lambda: events.append('reaped')
        def observed(capture, arguments, label, timeout, **kwargs):
            if label == 'compose-tests': self.assertIs(kwargs.pop('kernel_channel'), helper)
            return capture(arguments, label, timeout, **kwargs)
        def original_main(**kwargs):
            self.assertEqual(kwargs, {'failure_runloop': True})
            commands = run_pin03.Commands()
            for label, timeout in [('compose-build',2700),('compose-tests',900)]:
                commands.capture(['platform=iOS Simulator,id='+SIM], label, timeout)
            return 1  # Explicit failed native fixture; no invented acceptance.
        with TemporaryDirectory() as d:
            output = Path(d).resolve();(output/'compose').mkdir()
            args = ['run_pin49.py','--profile',pin47_failure_runloop.QUERY_PROFILE,'--output',str(output)]
            with patch.object(sys, 'argv', args), patch.object(run_pin03, 'Commands', OriginalCommands), \
                    patch.object(run_pin03, 'main', side_effect=original_main), \
                    patch.object(c.kernel, 'KernelChannel', return_value=helper), \
                    patch.object(c, 'execute_observed', side_effect=observed):
                if fail_ready:
                    with self.assertRaises(subprocess.TimeoutExpired): run_pin49.main()
                    self.assertEqual(events, ['reaped'])
                else:
                    self.assertEqual(run_pin49.main(), 1)
                    self.assertEqual(events, ['ready','compose-build','compose-tests','reaped'])
                self.assertIs(run_pin03.Commands, OriginalCommands)
            self.assertEqual(json.loads((output/'compose/kernel-prebuild.json').read_text()), {'portable_fixture_only': True})
