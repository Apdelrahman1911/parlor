"""Portable boundaries only; no actual recording capability attested locally."""
import os
import json
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import profiler_attach_control as c


class ProfilerAttachControls(unittest.TestCase):
    def test_exact_target_recording_and_offline_exports_never_all_processes_or_privacy_bypass(self):
        p=Path('/owned/control');self.assertEqual(c.record_command(42,p/'owned.trace'),[
            c.PROFILER,'record','--template','Time Profiler','--attach','42',
            '--time-limit','1s','--output','/owned/control/owned.trace'])
        for pid in (0,-1,True,'42'):
            with self.assertRaises(ValueError):c.record_command(pid,p)
        self.assertIn('--toc',c.export_command(p,p/'toc.xml',toc=True))
        self.assertIn(c.XPATH,c.export_command(p,p/'samples.xml'))
        self.assertNotIn('--no-prompt',c.record_command(42,p));self.assertNotIn('--all-processes',c.record_command(42,p))

    def test_only_explicit_clean_xcode_environment(self):
        with patch.dict(os.environ,GH_TOKEN='not-forwarded',DYLD_INSERT_LIBRARIES='not-forwarded'):
            env=c.environment();self.assertEqual(set(env)-{'HOME','TMPDIR'},{'PATH','LC_ALL','DEVELOPER_DIR'})
            self.assertEqual(env['DEVELOPER_DIR'],c.DEVELOPER)

    def test_linux_or_existing_output_never_starts_a_target(self):
        with patch.object(c.sys,'platform','linux'),patch.object(c.subprocess,'Popen') as child,self.assertRaises(RuntimeError):
            c.control(Path('/unavailable'))
        child.assert_not_called()
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'),patch.object(c.subprocess,'Popen') as child,self.assertRaises(ValueError):
            c.control(Path(d).resolve())
        child.assert_not_called()

    def test_xml_never_accepts_redirected_empty_oversized_or_entity_sources(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d).resolve()/'samples.xml'
            for raw in (b'',b'x'*(c.MAX_XML+1),b'<!DOCTYPE root><root/>',b'<!ENTITY value SYSTEM "file:///private"><root/>'):
                p.write_bytes(raw)
                with self.assertRaises(ValueError):c.xml(p)
            p.write_bytes(b'<root><row/></root>');self.assertEqual(c.xml(p)[1].tag,'root')
            link=p.parent/'link.xml';link.symlink_to(p)
            with self.assertRaises(ValueError):c.xml(link)

    def test_failed_recording_reaps_exact_owned_busy_target_before_offline_work(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            child=Mock(pid=42);child.poll.side_effect=[None,-15];child.wait.return_value=-15
            target=Path(d).resolve()/'control'
            def fail(directory,receipt,label,argv):
                if label in ('developer-status','first-launch-status'):return
                if label=='ready-path':
                    (directory/'ready-path.log').write_bytes(c.PROFILER.encode()+b'\n');return
                if label=='ready-help':
                    (directory/'ready-help.log').write_bytes(b'--attach');return
                self.assertEqual(label,'record');self.assertEqual(argv,c.record_command(42,target/'owned.trace'))
                raise ValueError('actual recording failed')
            with patch.object(c.subprocess,'Popen',return_value=child) as spawn,patch.object(c,'command',side_effect=fail),self.assertRaises(ValueError):
                c.control(target)
            self.assertEqual(spawn.call_args.args[0],['/usr/bin/yes']);child.terminate.assert_called_once()
            value=json.loads((target/'receipt.json').read_text());self.assertTrue(value['owned_target_reaped'])
            self.assertFalse(value['actual_target_samples_verified']);self.assertIn('error',value)

    def test_timeout_kills_and_reaps_only_exact_owned_profiler_child_and_preserves_failure(self):
        with tempfile.TemporaryDirectory() as d:
            target=Path(d).resolve();receipt={'commands':[]};argv=c.record_command(42,target/'owned.trace')
            child=Mock(pid=43);child.wait.side_effect=[subprocess.TimeoutExpired(argv,8),-9];child.poll.side_effect=[None,-9]
            with patch.object(c.subprocess,'Popen',return_value=child),self.assertRaises(subprocess.TimeoutExpired):
                c.command(target,receipt,'record',argv)
            child.kill.assert_called_once();self.assertTrue(receipt['commands'][0]['owned_child_reaped'])
            self.assertEqual(receipt['commands'][0]['status'],'STARTED');self.assertEqual(receipt['commands'][0]['owned_command_pid'],43)
            self.assertLessEqual(child.wait.call_args_list[0].kwargs['timeout'],8)

    def test_readiness_must_identify_exact_tool_before_any_busy_target_is_started(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'),patch.object(c.subprocess,'Popen') as target:
            out=Path(d).resolve()/'control'
            def foreign(directory,receipt,label,argv):
                self.assertEqual(label,'ready-path');self.assertEqual(argv,['/usr/bin/xcrun','--find','xctrace'])
                (directory/'ready-path.log').write_bytes(b'/different/tool\n')
            with patch.object(c,'command',side_effect=foreign),self.assertRaisesRegex(ValueError,'exact installed'):
                c.control(out)
            target.assert_not_called();self.assertNotIn('owned_target_pid',json.loads((out/'receipt.json').read_text()))

    def test_failure_log_is_exact_owned_recorder_pid_with_default_privacy_not_global_or_private_data(self):
        self.assertEqual(c.startup_log_command(42),['/usr/bin/log','show','--style','compact',
            '--last','1m','--info','--predicate','processID == 42'])
        for pid in (0,-1,True,'42 OR true'):
            with self.assertRaises(ValueError):c.startup_log_command(pid)
        self.assertNotIn('--private',c.startup_log_command(42))
