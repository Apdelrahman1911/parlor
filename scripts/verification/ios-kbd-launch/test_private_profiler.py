"""Private native capture safety; fixtures do not establish hosted capability."""
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import private_profiler as p
import launch as launcher


class PrivateProfilerControls(unittest.TestCase):
    def test_reuses_exact_previously_admitted_target_only_capture_command(self):
        expected = ['/usr/bin/sudo', '-n', '/usr/sbin/spindump', '42', '1', '10',
                    '-onlyTarget', '-noText', '-noProcessingWhileSampling', '-timelimit', '5', '-noFile']
        self.assertEqual(p.capture_command(42, None), expected)
        for bad in [True, False, '42', 0, -1]:
            with self.assertRaises(ValueError): p.capture_command(bad, None)
        self.assertNotIn('-o', expected)

    def test_unknown_and_binary_cli_output_is_not_exported(self):
        raw = b'Sampling simctl [42] for 1 seconds\nSpindump binary format\nPRIVATE\x00\xff'
        self.assertEqual(p.public_log(raw), b'Sampling simctl [42] for 1 seconds\n')
        self.assertEqual(p.public_log(b'private env or unrecognized error\n'), b'')

    def test_live_caller_streams_and_binary_fallback_never_enter_artifact_directory(self):
        with TemporaryDirectory() as d:
            out = Path(d)/'public'; out.mkdir()
            child = SimpleNamespace(pid=99, wait=lambda timeout: 0, poll=lambda: 0)
            def launch(argv, **streams):
                for stream in streams.values():
                    self.assertNotEqual(Path(stream.name).parent, out)
                streams['stdout'].write(b'Sampling simctl [42] for 1 seconds\nSpindump binary format\nSYNTHETIC_PRIVATE_PAYLOAD\x00\xff')
                streams['stdout'].flush()
                streams['stderr'].write(b'SYNTHETIC_PRIVATE_ERROR\n')
                streams['stderr'].flush()
                return child
            with patch.dict(launcher.os.environ, RUNNER_TEMP=d), patch.object(launcher.subprocess, 'Popen', side_effect=launch):
                code = launcher.profile_command(out, p.capture_command(42, None), deadline=launcher.time.monotonic()+20)
            self.assertEqual(code, 0)
            self.assertIsInstance(launcher.profile_command.private_capture, Path)
            for f in out.iterdir():
                self.assertNotIn(b'SYNTHETIC_PRIVATE_', f.read_bytes())
            self.assertEqual((out/'query-profiler.log').read_bytes(), b'Sampling simctl [42] for 1 seconds\n')
            launcher.profile_command.private_capture = None

    def fixture(self):
        return (b'Country Code: PRIVATE\nProcess: simctl [42]\nPath: /native/simctl\n'
                b'Preferred User Language: PRIVATE\nThread 0x123 PRIVATE QUEUE\n'
                b'  10 mach_msg + 8 (libsystem_kernel.dylib + 12) [0x123] 1-10\n'
                b'  Binary Images:\nPRIVATE IMAGE INVENTORY\n')

    def test_exports_only_exact_target_native_threads_and_frames_not_inventory(self):
        safe, receipt = p.target_frames(self.fixture(), 42, '/native/simctl')
        self.assertNotIn(b'PRIVATE', safe)
        self.assertEqual(receipt['threads'], 1)
        self.assertEqual(receipt['frames'], 1)
        self.assertFalse(receipt['host_inventory_uploaded'])
        self.assertFalse(receipt['raw_metadata_uploaded'])
        self.assertFalse(receipt['original_input_causality'])

    def test_foreign_duplicate_empty_oversized_and_wrong_executable_reports_rejected(self):
        for raw in [b'', b'x'*(p.MAX_TEXT+1), self.fixture().replace(b'[42]', b'[43]'),
                    self.fixture()+b'Process: foreign [7]\n',
                    self.fixture().replace(b'/native/simctl', b'/foreign/simctl'),
                    self.fixture().replace(b'[0x123]', b'[no actual address]')]:
            with self.assertRaises(ValueError): p.target_frames(raw, 42, '/native/simctl')

    def test_raw_and_analysis_output_remain_private_while_only_safe_report_is_exported(self):
        with TemporaryDirectory() as d:
            private = Path(d)/'private'; private.mkdir()
            out = Path(d)/'public'; out.mkdir()
            (private/'record.opaque-output').write_bytes(b'opaque private metadata')
            child = SimpleNamespace(wait=lambda timeout: 0, poll=lambda: 0)
            def launch(argv, **streams):
                self.assertNotEqual(streams['stdout'], subprocess.PIPE)
                self.assertNotEqual(streams['stderr'], subprocess.PIPE)
                self.assertEqual(Path(streams['stdout'].name).parent, private)
                (private/'report.txt').write_bytes(self.fixture())
                streams['stdout'].write(b'private decoder output')
                return child
            with patch.object(p.subprocess, 'Popen', side_effect=launch):
                receipt = p.decode(private, out, 42, '/native/simctl')
            self.assertEqual([f.name for f in out.iterdir()], ['query-native-frames.txt'])
            self.assertNotIn(b'private', (out/'query-native-frames.txt').read_bytes().lower())
            self.assertEqual(receipt['pid'], 42)

    def test_native_decoder_failure_cannot_publish_a_report(self):
        with TemporaryDirectory() as d:
            private = Path(d)/'private'; private.mkdir()
            out = Path(d)/'public'; out.mkdir()
            (private/'record.opaque-output').write_bytes(b'private metadata')
            child = SimpleNamespace(wait=lambda timeout: 69, poll=lambda: 69)
            with patch.object(p.subprocess, 'Popen', return_value=child):
                with self.assertRaises(ValueError): p.decode(private, out, 42, '/native/simctl')
            self.assertEqual(list(out.iterdir()), [])

    def test_missing_or_redirected_native_report_is_rejected(self):
        with TemporaryDirectory() as d:
            f = Path(d)/'text'; f.write_bytes(self.fixture())
            link = Path(d)/'link'; link.symlink_to(f)
            with self.assertRaises(ValueError): p.owned_file(link, p.MAX_TEXT)
            with self.assertRaises(ValueError): p.owned_file(f, 1)


if __name__ == '__main__': unittest.main()
