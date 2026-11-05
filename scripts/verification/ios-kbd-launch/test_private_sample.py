"""Sample alternative privacy and exact-target controls; not native evidence."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import launch
import private_sample as sample
import test_first_pending


class PrivateSampleControls(unittest.TestCase):
    def fixture(self):
        return (b"Analysis: SYNTHETIC_SECRET\nProcess: simctl [42]\nPath: /native/simctl\n"
                b"Command: SYNTHETIC_SECRET --credential=SYNTHETIC_SECRET\nCall graph:\n"
                b"    10 mach_msg2_trap (in libsystem_kernel.dylib) + 8 [0x123]\n"
                b"SYNTHETIC_SECRET /path\nTotal number in stack\nSYNTHETIC_SECRET\nBinary Images:\nSYNTHETIC_SECRET\n")

    def test_documented_exact_pid_command_avoids_sudo_eager_symbols_and_default_files(self):
        self.assertEqual(sample.capture_command(42, None),
                         ["/usr/bin/sample", "42", "1", "10", "-file", "/dev/stdout"])
        for bad in (True, False, "42", 0, -1):
            with self.assertRaises(ValueError): sample.capture_command(bad, None)

    def test_exact_sample_headers_and_filtered_frames_exclude_private_metadata(self):
        safe, receipt = sample.target_frames(self.fixture(), 42, "/native/simctl")
        self.assertNotIn(b"SYNTHETIC_SECRET", safe)
        self.assertEqual(receipt["frames"], 1)
        self.assertFalse(receipt["raw_metadata_uploaded"])
        self.assertFalse(receipt["original_input_causality"])
        self.assertEqual(receipt["native_tool"], "sample")

    def test_foreign_duplicate_missing_unbounded_or_no_address_reports_reject(self):
        for raw in (b"", b"x" * (sample.MAX_TEXT + 1),
                    self.fixture().replace(b"[42]", b"[43]"),
                    self.fixture() + b"Process: foreign [7]\n",
                    self.fixture().replace(b"/native/simctl", b"/foreign/simctl"),
                    self.fixture().replace(b"[0x123]", b"[not-an-address]"),
                    self.fixture().replace(b"Call graph:", b"not-a-graph")):
            with self.assertRaises(ValueError): sample.target_frames(raw, 42, "/native/simctl")

    def test_file_backed_capture_and_direct_decode_publish_only_filtered_frames(self):
        with TemporaryDirectory() as d:
            out = Path(d).resolve() / "public"; out.mkdir()
            child = SimpleNamespace(pid=99, wait=lambda timeout: 0, poll=lambda: 0)
            def execute(argv, **streams):
                self.assertEqual(argv, sample.capture_command(42, None))
                for stream in streams.values():
                    self.assertNotEqual(Path(stream.name).parent, out)
                streams["stdout"].write(self.fixture()); streams["stdout"].flush()
                streams["stderr"].write(b"SYNTHETIC_SECRET error"); streams["stderr"].flush()
                return child
            with patch.dict(launch.os.environ, RUNNER_TEMP=d), \
                 patch.object(launch.subprocess, "Popen", side_effect=execute):
                code = launch.profile_command(out, sample.capture_command(42, None),
                                              deadline=launch.time.monotonic()+20, report_format="sample")
            self.assertEqual(code, 0)
            private = launch.profile_command.private_capture
            with patch.object(launch.subprocess, "Popen", side_effect=AssertionError("No offline subprocess")):
                receipt = sample.decode(private, out, 42, "/native/simctl")
            for f in out.iterdir(): self.assertNotIn(b"SYNTHETIC_SECRET", f.read_bytes())
            self.assertEqual(receipt["frames"], 1)
            self.assertEqual(json.loads((out / "query-private-capture.json").read_text())["native_mode"],
                             "DOCUMENTED_SAMPLE_PRIVATE_FILE_STDOUT")
            launch.profile_command.private_capture = None

    def test_decode_still_refuses_redirected_report(self):
        with TemporaryDirectory() as d:
            private = Path(d).resolve()/"private"; private.mkdir()
            out = Path(d).resolve()/"public"; out.mkdir()
            raw = Path(d).resolve()/"raw"; raw.write_bytes(self.fixture())
            (private/"record.opaque-output").symlink_to(raw)
            with self.assertRaises(ValueError): sample.decode(private, out, 42, "/native/simctl")
            self.assertEqual(list(out.iterdir()), [])

    def test_unknown_format_refuses_launch(self):
        with TemporaryDirectory() as d, patch.object(launch.subprocess, "Popen") as opened:
            with self.assertRaises(ValueError):
                launch.profile_command(Path(d), ["arbitrary"], deadline=launch.time.monotonic()+20,
                                       report_format="arbitrary")
            opened.assert_not_called()

    def test_actual_sample_selection_keeps_query_deadline_and_one_attempt(self):
        control = test_first_pending.FirstPendingControls()
        _, record, target, captures, opens, _, end = control.execute(report_format="sample")
        self.assertEqual((captures, opens, end), (1, 1, 1020.0))
        self.assertEqual(record["status"], "TIMED_OUT")
        self.assertEqual(target["scope"], "direct-query-dispatcher-not-keyboard")
        self.assertEqual(control.last_profile_call.kwargs["report_format"], "sample")
        self.assertEqual(control.last_profile_call.args[1], sample.capture_command(123, None))
        self.assertTrue(record["direct_child_reaped"])


if __name__ == "__main__": unittest.main()
