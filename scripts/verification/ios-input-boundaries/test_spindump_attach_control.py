"""Portable ownership/security controls, not actual Mac attachment evidence."""
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock,patch

import spindump_attach_control as c


class SpindumpAttachControls(unittest.TestCase):
    def test_exact_target_only_binary_capture_and_offline_timeline_no_permission_bypass(self):
        raw=Path('/owned/control/owned.raw');text=raw.with_name('report.txt')
        self.assertEqual(c.capture_command(42,raw),['/usr/bin/sudo','-n',c.TOOL,'42','1','10','-onlyTarget',
            '-noText','-noProcessingWhileSampling','-timelimit','5','-noFile'])
        self.assertEqual(c.analysis_command(raw,text),[c.TOOL,'-i',str(raw),'-timeline','-noBinary','-o',str(text)])
        for pid in (0,-1,True,'42'):
            with self.assertRaises(ValueError):c.capture_command(pid,raw)
        for flag in ('-E','-S','-proc','-notarget','-sampleWithoutTarget','-wait','-nothrottle'):
            self.assertNotIn(flag,c.capture_command(42,raw))

    def test_clean_environment_does_not_forward_credentials_or_injection(self):
        with patch.dict(os.environ,GH_TOKEN='not-forwarded',DYLD_INSERT_LIBRARIES='not-forwarded'):
            self.assertEqual(set(c.environment())-{'HOME','TMPDIR'},{'PATH','LC_ALL','DEVELOPER_DIR'})

    def test_non_mac_or_existing_output_never_creates_target(self):
        with patch.object(c.sys,'platform','linux'),patch.object(c.subprocess,'Popen') as target,self.assertRaises(RuntimeError):
            c.control(Path('/unavailable'))
        target.assert_not_called()
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'),patch.object(c.subprocess,'Popen') as target,self.assertRaises(ValueError):
            c.control(Path(d).resolve())
        target.assert_not_called()

    def test_empty_redirected_oversized_and_other_process_report_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d).resolve()/'report.txt'
            for raw in (b'',b'x'*(c.MAX_TEXT+1),b'Process: yes [43]\nThread 0x123\n1 frame [0x123]',
                        b'Process: yes [42]\nNo samples',
                        b'Process: yes [42]\nProcess: foreign [24]\nThread 0x123\n1 frame [0x123]'):
                p.write_bytes(raw)
                with self.assertRaises(ValueError):c.read_report(p,42)
            p.write_bytes(b'Process: yes [42]\nThread 0x123\n1 frame [0x123]')
            self.assertEqual(c.read_report(p,42)['bytes'],p.stat().st_size)
            link=p.with_name('link');link.symlink_to(p)
            with self.assertRaises(ValueError):c.read_report(link,42)

    def test_failed_recording_reaps_busy_target_and_preserves_failure_without_offline_analysis(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            child=Mock(pid=42);child.poll.side_effect=[None,-15];child.wait.return_value=-15
            out=Path(d).resolve()/'control'
            with patch.object(c.subprocess,'Popen',return_value=child),patch.object(c,'command',side_effect=ValueError('actual permission or recording failure')) as command,self.assertRaises(ValueError):
                c.control(out)
            child.terminate.assert_called_once();self.assertEqual(command.call_count,1)
            value=json.loads((out/'receipt.json').read_text());self.assertTrue(value['owned_target_reaped'])
            self.assertIn('actual permission or recording failure',value['error']);self.assertNotIn('result',value)

    def test_raw_capture_is_not_uploaded_or_substituted_for_actual_stack_report(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c.sys,'platform','darwin'):
            out=Path(d).resolve()/'control';child=Mock(pid=42);child.poll.return_value=-15
            def incomplete(directory,receipt,label,argv):
                if label=='record':(directory/c.PRIVATE_CAPTURE).write_bytes(b'opaque fixture')
                else:(directory/'report.txt').write_bytes(b'no actual stacks')
            with patch.object(c.subprocess,'Popen',return_value=child),patch.object(c,'command',side_effect=incomplete),self.assertRaises(ValueError):
                c.control(out)
            value=json.loads((out/'receipt.json').read_text());self.assertFalse(value['raw_metadata']['uploaded'])
            self.assertFalse(value['attachment_independently_verified']);self.assertNotIn('result',value)

    def test_native_binary_fallback_and_unknown_lines_never_enter_public_artifact_log(self):
        raw=b'Sampling yes [42] for 1 seconds\nSampling completed, processing symbols...\nSpindump binary format\nOPAQUE\n'
        self.assertEqual(c.public_log(raw),b'Sampling yes [42] for 1 seconds\nSampling completed, processing symbols...\n')
        self.assertEqual(c.public_log(b'Unknown secret\n\x00\xffbinary'),b'')

    def test_failed_native_command_output_stays_private_and_original_error_is_not_waived(self):
        with tempfile.TemporaryDirectory() as d:
            out=Path(d).resolve();receipt={}
            def failure(directory,receipt,label,argv,*,opaque):
                self.assertTrue(opaque)
                (directory/(label+'.opaque-output')).write_bytes(b'Sampling completed, processing symbols...\nSpindump binary format\nOPAQUE\n')
                raise ValueError('actual command failure')
            with patch.object(c,'native_command',side_effect=failure),self.assertRaisesRegex(ValueError,'actual command failure'):
                c.command(out,receipt,'record',c.capture_command(42,out/c.PRIVATE_CAPTURE))
            self.assertNotIn(b'OPAQUE',(out/'record.log').read_bytes())
            self.assertFalse(receipt['private_output']['record']['uploaded'])
