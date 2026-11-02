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
METRICS = dict(enumerated=1, disappeared=0, name_queries=1, path_lookups=0,
               collection_seconds=0.001, list_seconds=0.0001, name_seconds=0.0001, path_seconds=0.0)
ORIGINAL_MARKER = (c.FIRST_METHOD + b'\n    t =     1.15s     Launch me.parlor.ios.debug\n'
                   b'    t =    25.40s Waiting 30.0s for "parlor-home-brand" StaticText to exist\n')


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
                observer.assert_called_once_with(dest/'native-stacks', SIM, 'a'*64, original_log=dest/'test.log')
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
        self.assertEqual(c.sample_command(23, Path('/owned/sample')), ['/usr/bin/sample', '23', '1', '10', '-file', '/owned/sample'])
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
                child = Mock(pid=42); child.wait.return_value=1; child.poll.return_value=1
                with patch.object(c.subprocess, 'Popen', return_value=child), patch.object(sampler, '_discover', return_value=[{'pid': 23}]), self.assertRaises(ValueError):
                    sampler._sample({'pid': 23, 'image': '/owned/Parlor', 'executable_sha256': 'a'*64})
                self.assertEqual(sampler.rows[0]['exit'], 1)
                self.assertNotEqual(sampler.rows[0]['status'], 'SAMPLED')

    def test_fast_owned_sample_has_no_extra_tool_observation(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler=c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            child=Mock(pid=42);child.wait.return_value=0;child.poll.return_value=0
            row=dict(index=1,command=c.sample_command(23,sampler.directory/'sample-001.txt'))
            with patch.object(c.subprocess,'Popen',return_value=child), patch.object(c.subprocess,'run') as extra:
                self.assertEqual(sampler._run_owned_sample(row,io.BytesIO()),0)
            extra.assert_not_called();child.kill.assert_not_called()
            self.assertTrue(row['sampler_child_reaped']);self.assertIsNone(row['collector_stack'])

    def test_slow_first_sample_observes_only_its_owned_tool_pid_and_keeps_eight_second_budget(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler=c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            row=dict(index=1,command=c.sample_command(23,sampler.directory/'sample-001.txt'))
            child=Mock(pid=42);child.wait.side_effect=[subprocess.TimeoutExpired(row['command'],2),0,0]
            child.poll.side_effect=[None,0,0]
            def observe(command,**kwargs):
                self.assertEqual(command,c.sample_command(42,sampler.directory/'collector-001.txt'))
                self.assertLessEqual(kwargs['timeout'],3)
                Path(command[-1]).write_bytes(b'Analysis of sampling sample (pid 42)\nCall graph:\nThread_1')
                return subprocess.CompletedProcess(command,0)
            with patch.object(c.subprocess,'Popen',return_value=child),patch.object(c.subprocess,'run',side_effect=observe) as extra:
                self.assertEqual(sampler._run_owned_sample(row,io.BytesIO()),0)
            extra.assert_called_once();self.assertEqual(row['collector_stack']['pid'],42)
            self.assertEqual(row['collector_stack']['status'],'SAMPLED')
            self.assertTrue(row['sampler_child_reaped'])
            self.assertLessEqual(child.wait.call_args_list[1].kwargs['timeout'],8)

    def test_late_observer_cannot_extend_primary_deadline_even_if_tool_already_exited(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler=c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            row=dict(index=1,command=c.sample_command(23,sampler.directory/'sample-001.txt'))
            child=Mock(pid=42);child.wait.side_effect=[subprocess.TimeoutExpired(row['command'],2),0]
            child.poll.side_effect=[None,0,0]
            def observe(command,**kwargs):
                Path(command[-1]).write_bytes(b'Analysis of sampling sample (pid 42)\nCall graph:\nThread_1')
                return subprocess.CompletedProcess(command,0)
            with patch.object(c.subprocess,'Popen',return_value=child),patch.object(c.subprocess,'run',side_effect=observe), \
                    patch.object(c.time,'monotonic',side_effect=[0,0.1,2,2.1,2.2,8.5,8.6]),self.assertRaises(subprocess.TimeoutExpired):
                sampler._run_owned_sample(row,io.BytesIO())
            self.assertEqual(len(child.wait.call_args_list),2)
            self.assertTrue(row['sampler_child_reaped'])

    def test_failed_auxiliary_is_retained_without_discarding_original_primary_window(self):
        with TemporaryDirectory() as d, patch.dict(os.environ, HOME=d):
            sampler=c.NativeSampler(Path(d).resolve()/'samples', SIM, 'a'*64)
            row=dict(index=1,command=c.sample_command(23,sampler.directory/'sample-001.txt'))
            child=Mock(pid=42);child.wait.side_effect=[subprocess.TimeoutExpired(row['command'],2),0,0]
            child.poll.side_effect=[None,0,0]
            with patch.object(c.subprocess,'Popen',return_value=child),patch.object(c.subprocess,'run',side_effect=subprocess.TimeoutExpired('owned auxiliary',3)):
                self.assertEqual(sampler._run_owned_sample(row,io.BytesIO()),0)
            self.assertEqual(row['collector_stack']['status'],'FAILED')
            self.assertTrue(row['collector_stack']['child_reaped'])
            self.assertTrue(row['sampler_child_reaped'])
            self.assertLessEqual(child.wait.call_args_list[1].kwargs['timeout'],8)

    def test_real_path_readback_rejects_changed_report_and_accepts_only_explicit_producer_directory(self):
        with TemporaryDirectory() as d:
            with patch.dict(os.environ, HOME=d):
                sampler = c.NativeSampler(Path(d).resolve(strict=True)/'samples', SIM, 'a'*64)
                (sampler.directory.parent/'test.log').write_bytes(ORIGINAL_MARKER)
                sampler.activation = dict(**c.original_home_marker(ORIGINAL_MARKER), observed_ms=sampler.started_ms,
                                          observed_unix=1.0, kernel_requests_before_activation=0)
                path = sampler.directory/'sample-001.txt'
                path.write_bytes(b'Analysis of sampling Parlor (pid 23)\nCall graph:\nThread_1')
                sampler.discoveries = 1
                sampler.rows = [{'index': 1, 'report': path.name, 'pid': 23, 'image': '/owned/Parlor',
                    'sampler_pid': 42, 'sampler_deadline_seconds': 8, 'collector_stack': None,
                    'executable_sha256': 'a'*64, 'started_ms': sampler.started_ms+1,
                    'ended_ms': sampler.started_ms+5001, 'started_unix': 1, 'ended_unix': 6,
                    'sampler_started_ms': sampler.started_ms+2, 'sampler_observed_end_ms': sampler.started_ms+5000,
                    'command': c.sample_command(23, path), 'sampler_child_reaped': True,
                    'exit': 0, 'status': 'SAMPLED', **c.read_sample(path, 23)}]
                sampler.kernel = Mock()
                sampler.kernel.receipt.return_value = dict(schema=1, pid=42,
                    ready=dict(kind='ready', schema=1, selector='PROC_UID_ONLY', simulator=SIM,
                               own_process_seen=True, helper_pid=42, name_prefilter='Parlor', paths=[], **METRICS), requests=1,
                    startup_seconds=0.1, response_seconds=[0.1], exit=0, child_reaped=True,
                    response_metrics=[METRICS], name_prefilter='Parlor',
                    started_unix=1.0, ready_unix=2.0, started_before_original_build=True,
                    started_before_original_command=True, reply_timeout_seconds=3)
                sampler._checkpoint()
                self.assertEqual(c.verify_receipt(sampler.directory, SIM, 'a'*64)['sampled_reports'], 1)
                collector=sampler.directory/'collector-001.txt'
                collector.write_bytes(b'Analysis of sampling sample (pid 42)\nCall graph:\nThread_1')
                observed=dict(pid=42,command=c.sample_command(42,collector),report=collector.name,
                    started_ms=sampler.started_ms+2003,ended_ms=sampler.started_ms+3500,
                    started_unix=3,ended_unix=4,deadline_seconds=3,status='SAMPLED',exit=0,child_reaped=True,
                    **c.read_sample(collector,42))
                sampler.rows[0]['collector_stack']=observed;sampler._checkpoint()
                self.assertEqual(c.verify_receipt(sampler.directory,SIM,'a'*64)['sampled_reports'],1)
                for invalid in ({'pid':43},{'command':c.sample_command(43,collector)},{'status':'FAILED'},
                                {'child_reaped':False},{'sha256':'b'*64},{'deadline_seconds':4},
                                {'started_ms':sampler.started_ms+2},{'ended_ms':sampler.started_ms+5004}):
                    sampler.rows[0]['collector_stack']=observed|invalid;sampler._checkpoint()
                    with self.assertRaises(ValueError):c.verify_receipt(sampler.directory,SIM,'a'*64)
                sampler.rows[0]['collector_stack']=None;sampler._checkpoint()
                with self.assertRaisesRegex(ValueError,'All actual owned collector stacks retained'):
                    c.verify_receipt(sampler.directory,SIM,'a'*64)
                collector.unlink();sampler._checkpoint()
                # Removing eager symbol lookup must not admit vanished-app
                # annotations as a substitute for actual native stack reports.
                sampler.rows[0].update(status='APPLICATION_EXITED_DURING_SAMPLE', exit=1)
                sampler._checkpoint()
                with self.assertRaisesRegex(ValueError, 'All actual native stacks retained'):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64)
                sampler.rows[0].update(status='SAMPLED', exit=0)
                sampler._checkpoint()
                self.assertEqual(c.verify_receipt(sampler.directory, SIM, 'a'*64,
                    build_started_at='1970-01-01T00:00:03+00:00')['sampled_reports'], 1)
                with self.assertRaises(ValueError):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64,
                        build_started_at='1970-01-01T00:00:01+00:00')
                with self.assertRaises(ValueError):
                    c.verify_receipt(sampler.directory, SIM, 'a'*64, producer_directory=Path('/different'))
                path.write_bytes(b'Analysis of sampling Parlor (pid 23)\nCall graph:\nThread_2')
                with self.assertRaises(ValueError): c.verify_receipt(sampler.directory, SIM, 'a'*64)

    def test_observer_log_activation_requires_original_first_launch_not_later_method_or_synthetic_marker(self):
        self.assertIsNone(c.original_home_marker(b'Xcode cold startup\n'))
        self.assertIsNone(c.original_home_marker(ORIGINAL_MARKER[:ORIGINAL_MARKER.index(b'Waiting')]))
        marker = c.original_home_marker(ORIGINAL_MARKER)
        self.assertEqual(marker['kind'], 'original-first-home-waiter')
        self.assertEqual(marker['log_prefix_sha256'], hashlib.sha256(ORIGINAL_MARKER).hexdigest())
        for raw in (ORIGINAL_MARKER.replace(b'Launch me.parlor.ios.debug', b'Launch foreign'),
                    ORIGINAL_MARKER.replace(b'    t =    25.40s', b"Test Case '-[foreign next]' started.\n    t =    25.40s")):
            with self.assertRaises(ValueError): c.original_home_marker(raw)
        with self.assertRaises(ValueError): c.original_home_marker(b'x'*(c.MAX_TEST_LOG+1))

    def test_live_original_log_reads_bounded_prefix_not_symlink_or_other_file_type(self):
        with TemporaryDirectory() as d:
            path=Path(d).resolve()/'test.log'
            self.assertEqual(c.read_live_log(path), b'')
            path.write_bytes(ORIGINAL_MARKER)
            self.assertEqual(c.read_live_log(path), ORIGINAL_MARKER)
            link=path.with_name('alias');link.symlink_to(path)
            with self.assertRaises(ValueError):c.read_live_log(link)
            path.write_bytes(b'x'*(c.MAX_TEST_LOG+1))
            with self.assertRaises(ValueError):c.read_live_log(path)

    def test_prebuild_helper_is_idle_until_existing_first_launch_log_without_app_query_or_wait_extension(self):
        with TemporaryDirectory() as d, patch.dict(os.environ,HOME=d):
            root=Path(d).resolve();log=root/'test.log'
            sampler=c.NativeSampler(root/'samples',SIM,'a'*64,original_log=log)
            sampler.kernel=Mock();sampler.kernel.requests=0
            sampler.kernel.receipt.return_value={'portable_fixture_only':True}
            sampler.stop=Mock();sampler.stop.is_set.side_effect=[False,False,True]
            with patch.object(c,'read_live_log',side_effect=[b'Xcode startup\n',ORIGINAL_MARKER]), \
                    patch.object(sampler,'_discover') as discover:
                sampler._observe()
            discover.assert_not_called()
            self.assertEqual(sampler.activation['kernel_requests_before_activation'],0)
            sampler.stop.wait.assert_called_once_with(1)

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
        def name(pid, buffer, size):
            buffer.value = b'Python' if pid == own else b'Parlor'
            return len(buffer.value)
        proc.proc_name.side_effect = name
        proc.proc_pidpath.side_effect = pidpath
        receipt = paths.collect(proc, SIM)
        self.assertTrue(receipt['own_process_seen'])
        self.assertEqual(receipt['paths'][0]['pid'], 123)
        self.assertEqual((receipt['name_queries'], receipt['path_lookups']), (2, 1))
        proc.proc_pidpath.assert_called_once()
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

    def test_name_filter_avoids_unrelated_paths_but_never_admits_spoofed_or_wrong_device_images(self):
        import pin49_process_paths as paths
        own = os.getpid(); proc = Mock()
        def listpids(selector, uid, values, size):
            values[0], values[1], values[2], values[3] = own, 123, 124, 125
            return 16
        def name(pid, buffer, size):
            buffer.value = b'Python' if pid == own else b'runner-service' if pid == 123 else b'Parlor'
            return len(buffer.value)
        def path(pid, buffer, size):
            self.assertIn(pid, (124, 125))
            buffer.value = (b'/unrelated/Parlor' if pid == 124 else
                ('/owned/Library/Developer/CoreSimulator/Devices/'+INSTALL+
                 '/data/Containers/Bundle/Application/'+INSTALL+'/Parlor.app/Parlor').encode())
            return len(buffer.value)
        proc.proc_listpids.side_effect = listpids
        proc.proc_name.side_effect = name; proc.proc_pidpath.side_effect = path
        receipt = paths.collect(proc, SIM)
        self.assertEqual(receipt['paths'], [])
        self.assertEqual((receipt['name_queries'], receipt['path_lookups']), (4, 2))
        self.assertEqual(proc.proc_pidpath.call_count, 2)
        self.assertNotIn('runner-service', json.dumps(receipt))
        channel.discovery_metrics(receipt)

    def test_malformed_denied_names_and_short_native_paths_fail_closed_not_skip_or_retry(self):
        import pin49_process_paths as paths
        own = os.getpid(); proc = Mock()
        def listpids(selector, uid, values, size):
            values[0], values[1] = own, 123
            return 8
        proc.proc_listpids.side_effect = listpids
        for count, raw in ((0, b''), (64, b'Parlor'), (5, b'Parlor')):
            def name(pid, buffer, size):
                buffer.value = b'Python' if pid == own else raw
                return len(buffer.value) if pid == own else count
            proc.proc_name.side_effect = name
            with self.assertRaises(ValueError): paths.collect(proc, SIM)
            proc.proc_pidpath.assert_not_called()
        def name(pid, buffer, size):
            buffer.value = b'Python' if pid == own else b'Parlor'
            return len(buffer.value)
        proc.proc_name.side_effect = name; proc.proc_pidpath.return_value = 0
        with self.assertRaises(ValueError): paths.collect(proc, SIM)
        proc.proc_pidpath.assert_called_once()

    def test_collection_metrics_cannot_waive_deadline_or_fabricate_partial_counters(self):
        valid = dict(name_prefilter='Parlor', paths=[], **METRICS)
        for field, value in [('name_prefilter','Python'), ('name_queries',2), ('path_lookups',2),
                             ('enumerated',4097), ('collection_seconds',3), ('path_seconds',float('nan')),
                             ('name_seconds',0.1), ('disappeared',True)]:
            with self.assertRaises(ValueError): channel.discovery_metrics(valid | {field:value})

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
          helper_pid=os.getpid(),enumerated=1,disappeared=0,paths=[],name_prefilter='Parlor',
          name_queries=1,path_lookups=0,collection_seconds=0.001,list_seconds=0.0001,
          name_seconds=0.0001,path_seconds=0.0)
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
