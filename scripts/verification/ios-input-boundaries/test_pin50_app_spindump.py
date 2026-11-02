"""Portable app-capture ownership/ordering controls; fixtures are not Mac evidence."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import pin50_app_spindump as c
from test_pin49_native_stacks import SIM, INSTALL, ORIGINAL_MARKER, METRICS


def report(image, pid=23):
    return ('Command: Parlor\nPath: '+image+'\nProcess: Parlor ['+str(pid)+']\nPath: '+image+
            '\nThread 0x123    100 samples\n 100 frame [0x123] 1-100\n').encode()


def sampler(root):
    with patch.dict(os.environ, HOME=str(root)):
        (root/'compose').mkdir()
        s = c.AppSpindump(root/'compose/native-stacks', SIM, 'a'*64, original_log=root/'compose/test.log')
    return s


def successful_fixture(root):
    s = sampler(root)
    (root/'compose/test.log').write_bytes(ORIGINAL_MARKER)
    s.activation = dict(**c.base.original_home_marker(ORIGINAL_MARKER), observed_ms=s.started_ms, observed_unix=1.0,
                        kernel_requests_before_activation=0)
    s.discoveries = 1
    s.original_returned_unix = 10.0; s.offline_finished_unix = 11.0
    image = str(root/'Library/Developer/CoreSimulator/Devices'/SIM/'data/Containers/Bundle/Application'/INSTALL/'Parlor.app/Parlor')
    stage = s.private/'capture-001';stage.mkdir()
    p = s.directory/'sample-001.txt';p.write_bytes(report(image))
    for label in ('record','analysis'):(s.directory/('sample-001-'+label+'.log')).write_bytes(b'')
    meta = dict(bytes=16, sha256=hashlib.sha256(b'opaque-fixture!!').hexdigest(), uploaded=False)
    s.private_bytes = 16
    s.rows = [dict(index=1,pid=23,image=image,executable_sha256='a'*64,private_directory=str(stage),
              started_ms=s.started_ms+1,ended_ms=s.started_ms+2001,started_unix=2.0,ended_unix=4.0,
              status='SAMPLED',report=p.name,report_metadata=c.read_report(p,23,image),raw_metadata=meta,
              private_capture_retired=True,private_output={'record':dict(**meta,bounded=True)},
              commands=[dict(label=label,command=command,started_unix=start,ended_unix=end,seconds=end-start,
                        status='COMPLETE',exit=0,owned_command_pid=42+i,owned_child_reaped=True)
                        for i,(label,command,start,end) in enumerate([
                            ('record',c.native.capture_command(23,stage/c.native.PRIVATE_CAPTURE),2.0,3.0),
                            ('analysis',c.native.analysis_command(stage/c.native.PRIVATE_CAPTURE,p),10.1,10.9)])])]
    s.kernel = Mock()
    s.kernel.receipt.return_value = dict(schema=1,pid=51,
        ready=dict(kind='ready',schema=1,selector='PROC_UID_ONLY',simulator=SIM,own_process_seen=True,helper_pid=51,
                   name_prefilter='Parlor',paths=[],**METRICS),requests=1,startup_seconds=0.1,response_seconds=[0.1],
        exit=0,child_reaped=True,response_metrics=[METRICS],name_prefilter='Parlor',started_unix=0.1,ready_unix=0.2,
        started_before_original_build=True,started_before_original_command=True,reply_timeout_seconds=3)
    s._checkpoint()
    return s


class AppSpindumpControls(unittest.TestCase):
    def test_exact_native_commands_and_unchanged_sample_budgets(self):
        argv=c.native.capture_command(23,Path('/private/record.opaque-output'))
        self.assertEqual(argv,['/usr/bin/sudo','-n','/usr/sbin/spindump','23','1','10','-onlyTarget',
                               '-noText','-noProcessingWhileSampling','-timelimit','5','-noFile'])
        self.assertEqual((c.base.MAX_SAMPLES,c.base.SAMPLE_CADENCE_SECONDS,c.base.DISCOVERY_CADENCE_SECONDS),(180,5,1))

    def test_report_requires_exact_sole_app_pid_path_threads_and_frames(self):
        with TemporaryDirectory() as d:
            p=Path(d).resolve()/'report.txt';image='/owned/Parlor.app/Parlor'
            p.write_bytes(report(image));self.assertEqual(c.read_report(p,23,image)['thread_rows'],1)
            for data in (b'',report(image,24),report(image).replace(b'Parlor [23]',b'yes [23]'),
                         report(image).replace(b'Process: Parlor',b'Process: foreign [2]\nProcess: Parlor'),
                         report(image).replace(b'/owned/',b'/foreign/'),report(image).replace(b'Thread 0x123',b'No thread'),
                         report(image).replace(b'[0x123]',b'no frame')):
                p.write_bytes(data)
                with self.assertRaises(ValueError):c.read_report(p,23,image)
            p.write_bytes(report(image));link=p.with_name('link');link.symlink_to(p)
            with self.assertRaises(ValueError):c.read_report(link,23,image)

    def test_private_staging_is_exclusive_and_outside_the_public_sample_folder(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve());self.assertFalse(s.private.is_relative_to(s.directory))
            self.assertEqual(s.private.stat().st_mode & 0o777,0o700)
            with patch.dict(os.environ,HOME=d),self.assertRaises(ValueError):
                c.AppSpindump(Path(d).resolve()/'compose/other-stacks',SIM,'a'*64)

    def test_live_capture_is_private_and_never_analyzed_during_original_execution(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve())
            def command(stage,row,label,argv):
                self.assertEqual(label,'record')
                (stage/c.native.PRIVATE_CAPTURE).write_bytes(b'opaque-fixture')
                (stage/'record.log').write_bytes(b'Sampling completed, processing symbols...\n')
            with patch.object(c.native,'command',side_effect=command):
                s._sample(dict(pid=23,image='/owned/Parlor',executable_sha256='a'*64))
            self.assertEqual(s.rows[0]['status'],'CAPTURED_PENDING_OFFLINE_ANALYSIS')
            self.assertFalse(list(s.directory.glob('*.txt')));self.assertFalse(list(s.directory.glob('*.opaque-output')))
            self.assertFalse(s.rows[0]['raw_metadata']['uploaded'])

    def test_failed_capture_preserves_error_and_does_not_make_stack_or_success(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve())
            with patch.object(c.native,'command',side_effect=ValueError('actual attach failure')),self.assertRaisesRegex(ValueError,'actual attach failure'):
                s._sample(dict(pid=23,image='/owned/Parlor',executable_sha256='a'*64))
            self.assertEqual(s.rows[0]['status'],'STARTED');self.assertIn('ended_ms',s.rows[0])
            with patch.object(c.native,'command') as analysis:s._analyze()
            analysis.assert_not_called()
            with self.assertRaises(ValueError):c.verify_receipt(s.directory,SIM,'a'*64)

    def test_storage_limit_fails_closed_without_reducing_original_tests_or_budgets(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve());s.private_bytes=c.MAX_PRIVATE_TOTAL
            def command(stage,row,label,argv):(stage/c.native.PRIVATE_CAPTURE).write_bytes(b'x')
            with patch.object(c.native,'command',side_effect=command),self.assertRaisesRegex(ValueError,'aggregate'):
                s._sample(dict(pid=23,image='/owned/Parlor',executable_sha256='a'*64))
            self.assertEqual(s.rows[0]['status'],'STARTED')

    def test_observer_stop_and_helper_reaping_precede_offline_analysis_after_original_return(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve());events=[]
            s.thread=Mock();s.thread.is_alive.return_value=False
            s.thread.join.side_effect=lambda **_:events.append('join')
            s.kernel=Mock();s.kernel.receipt.return_value={}
            s.kernel.close.side_effect=lambda **_:events.append('reap')
            with patch.object(s,'_analyze',side_effect=lambda:events.append('analysis')):
                s.__exit__(None,None,None)
            self.assertEqual(events,['join','reap','analysis']);self.assertGreater(s.original_returned_unix,0)
            s.thread.join.assert_called_once_with(timeout=12)

    def test_offline_failure_preserves_capture_without_success_or_opaque_retirement(self):
        with TemporaryDirectory() as d:
            s=sampler(Path(d).resolve());s.original_returned_unix=1
            def record(stage,row,label,argv):(stage/c.native.PRIVATE_CAPTURE).write_bytes(b'opaque-fixture')
            with patch.object(c.native,'command',side_effect=record):s._sample(dict(pid=23,image='/owned/Parlor',executable_sha256='a'*64))
            with patch.object(c.native,'command',side_effect=ValueError('actual format failure')):s._analyze()
            self.assertIn('actual format failure',s.error);self.assertNotEqual(s.rows[0]['status'],'SAMPLED')
            self.assertTrue((s.private/'capture-001'/c.native.PRIVATE_CAPTURE).exists())

    def test_forward_original_return_exit65_unchanged_and_never_observe_build_or_other_commands(self):
        capture=Mock(return_value=({'exit_code':65},b'out',b'err'))
        with patch.object(c,'AppSpindump') as observer:
            self.assertEqual(c.execute_observed(capture,['original'],'compose-build',2700),({'exit_code':65},b'out',b'err'))
            observer.assert_not_called()
        with TemporaryDirectory() as d:
            root=Path(d).resolve();(root/'compose').mkdir();(root/'compose/product.json').write_text(json.dumps({'Parlor':'a'*64}))
            kwargs={'environment':{'PARLOR_INPUT_LOG':str(root/'compose/test.log')}}
            argv=['platform=iOS Simulator,id='+SIM];helper=Mock()
            with patch.object(c,'AppSpindump') as observer:
                self.assertEqual(c.execute_observed(capture,argv,'compose-tests',900,kernel_channel=helper,**kwargs),({'exit_code':65},b'out',b'err'))
                for timeout in (901,1200):
                    with self.assertRaises(ValueError):c.execute_observed(capture,argv,'compose-tests',timeout,kernel_channel=helper,**kwargs)
                with self.assertRaises(ValueError):c.execute_observed(capture,argv,'compose-tests',900,**kwargs)
                self.assertEqual(observer.call_count,1)

    def test_real_readback_accepts_only_exact_bytes_producer_commands_and_post_test_analysis(self):
        with TemporaryDirectory() as d:
            s=successful_fixture(Path(d).resolve())
            self.assertEqual(c.verify_receipt(s.directory,SIM,'a'*64,build_started_at='1970-01-01T00:00:01+00:00')['sampled_reports'],1)
            with self.assertRaises(ValueError):c.verify_receipt(s.directory,SIM,'a'*64,producer_directory=Path('/other'))
            p=s.directory/'sample-001.txt';p.write_bytes(p.read_bytes()+b'changed')
            with self.assertRaises(ValueError):c.verify_receipt(s.directory,SIM,'a'*64)

    def test_metadata_or_command_order_mutations_never_admit_false_native_evidence(self):
        mutations=[lambda r:r.update(error='actual failure'),lambda r:r.update(schema=1),lambda r:r.update(shipping_qualified=True),
            lambda r:r.update(private_bytes=17),lambda r:r['samples'][0]['raw_metadata'].update(uploaded=True),
            lambda r:r['samples'][0]['commands'][0]['command'].remove('-onlyTarget'),
            lambda r:r['samples'][0]['commands'][1].update(started_unix=3),
            lambda r:r['samples'][0]['commands'][0].update(seconds=8),
            lambda r:r['samples'][0]['commands'][1].update(owned_child_reaped=False),
            lambda r:r['samples'][0].update(private_capture_retired=False)]
        for mutate in mutations:
            with TemporaryDirectory() as d:
                s=successful_fixture(Path(d).resolve());p=s.directory/'samples.json';r=json.loads(p.read_text());mutate(r);p.write_text(json.dumps(r))
                with self.assertRaises(ValueError):c.verify_receipt(s.directory,SIM,'a'*64)

    def test_empty_owned_public_log_allowed_but_redirected_unknown_or_opaque_logs_reject(self):
        with TemporaryDirectory() as d:
            s=successful_fixture(Path(d).resolve());p=s.directory/'sample-001-record.log'
            self.assertEqual(c.read_log(p),b'')
            for data in (b'Unknown private message\n',b'Spindump binary format\nOPAQUE'):
                p.write_bytes(data)
                with self.assertRaises(ValueError):c.verify_receipt(s.directory,SIM,'a'*64)
            link=p.with_name('link');link.symlink_to(p)
            with self.assertRaises(ValueError):c.read_log(link)

    def test_new_runner_keeps_original_profile_and_restores_command_dispatch_even_on_failure(self):
        import run_pin50,run_pin03,pin47_failure_runloop
        original=run_pin03.Commands
        with patch.object(run_pin50.sys,'argv',['run_pin50.py','--profile',pin47_failure_runloop.QUERY_PROFILE]), \
                patch.object(run_pin03,'main',side_effect=ValueError('original failure')) as main,self.assertRaisesRegex(ValueError,'original failure'):
            run_pin50.main()
        self.assertIs(run_pin03.Commands,original);main.assert_called_once_with(failure_runloop=True)

    def test_prebuild_coordinator_starts_one_child_before_original_build_and_reuses_it_for_tests(self):
        self.prebuild_control(fail_ready=False)

    def test_failed_prebuild_readiness_preserves_failure_and_cannot_start_compilation(self):
        self.prebuild_control(fail_ready=True)

    def prebuild_control(self, *, fail_ready):
        import run_pin50, run_pin03, pin47_failure_runloop
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
            args = ['run_pin50.py','--profile',pin47_failure_runloop.QUERY_PROFILE,'--output',str(output)]
            with patch.object(sys, 'argv', args), patch.object(run_pin03, 'Commands', OriginalCommands), \
                    patch.object(run_pin03, 'main', side_effect=original_main), \
                    patch.object(c.base.kernel, 'KernelChannel', return_value=helper), \
                    patch.object(c, 'execute_observed', side_effect=observed):
                if fail_ready:
                    with self.assertRaises(subprocess.TimeoutExpired): run_pin50.main()
                    self.assertEqual(events, ['reaped'])
                else:
                    self.assertEqual(run_pin50.main(), 1)
                    self.assertEqual(events, ['ready','compose-build','compose-tests','reaped'])
                self.assertIs(run_pin03.Commands, OriginalCommands)
            self.assertEqual(json.loads((output/'compose/kernel-prebuild.json').read_text()), {'portable_fixture_only': True})

    def test_source_and_uploads_never_add_app_actions_queries_or_opaque_payloads(self):
        import run_pin50
        text=Path(run_pin50.__file__).read_text()
        for token in ('typeText(','.tap()','-retry-tests','renderer_for_profile =','--all-processes'):
            self.assertNotIn(token,text)
        workflow=Path(__file__).resolve().parents[3]/'.github/workflows/ios-app-spindump-boundary.yml'
        upload=workflow.read_text().split('          path: |',2)[-1]
        self.assertNotIn('private-native-captures',upload);self.assertNotIn('opaque',upload)


if __name__=='__main__':unittest.main()
