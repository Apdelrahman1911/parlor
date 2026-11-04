import importlib.util
from pathlib import Path
import struct
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("kbd_launch", Path(__file__).with_name("launch.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class NativeKeyboardLaunchControls(unittest.TestCase):
    def test_argv_parser_excludes_environment_and_handles_native_padding(self):
        raw = struct.pack("=i", 2) + b"/runtime/xpcproxy_sim\0\0\0/runtime/xpcproxy_sim\0com.apple.TextInput.kbd\0SECRET=not-exported\0"
        self.assertEqual(probe.argv_only(raw), [b"/runtime/xpcproxy_sim", b"com.apple.TextInput.kbd"])

    def test_invalid_count_truncation_and_missing_terminator_reject(self):
        for raw in (b"", struct.pack("=i", 0) + b"a\0", struct.pack("=i", 4097) + b"a\0",
                    struct.pack("=i", 2) + b"x\0\0x\0", struct.pack("=i", 1) + b"x\0x"):
            with self.assertRaises(ValueError):
                probe.argv_only(raw)

    def test_native_child_must_match_uid_parent_and_creation_boundary(self):
        parent = SimpleNamespace(pid=1888, uid=501)
        info = SimpleNamespace(pid=2000, ppid=1888, uid=501, start_sec=100, start_usec=0, comm=b"xpcproxy_sim")
        self.assertTrue(probe.own_child(info, parent, 99))
        self.assertFalse(probe.own_child(info, parent, 101))
        for key, value in (("ppid", 1887), ("uid", 502)):
            other = SimpleNamespace(**(vars(info) | {key:value}))
            self.assertFalse(probe.own_child(other, parent, 99))
        self.assertFalse(probe.own_child(None, parent, 99))

    def test_pid_reuse_exec_and_parent_change_break_basic_identity(self):
        info = SimpleNamespace(pid=2000, uid=501, ppid=1888, start_sec=100, start_usec=0, comm=b"xpcproxy_sim")
        for key, value in (("start_usec", 1), ("comm", b"kbd"), ("ppid", 1887)):
            other = SimpleNamespace(**(vars(info) | {key:value}))
            self.assertNotEqual(probe.basic_key(info), probe.basic_key(other))
        self.assertIsNone(probe.basic_key(None))

    def test_service_reader_exports_only_program_pid_and_state(self):
        raw = b"user/501/com.apple.TextInput.kbd = {\n\tprogram = /usr/libexec/kbd\n\tstate = running\n\tpid = 2000\n\tenvironment = {\n\t\tSECRET = excluded\n\t}\n}\n"
        self.assertEqual(probe.service_fields(raw), dict(program="/usr/libexec/kbd",state="running",pid="2000"))
        self.assertEqual(probe.service_fields(b"state = not running\nprogram = /usr/libexec/kbd\n"), dict(state="not running",program="/usr/libexec/kbd"))

    def test_ambiguous_invalid_pid_or_oversized_native_output_never_qualifies(self):
        for raw in (b"pid = 2000\npid = 2001\n", b"pid = nope\n", b"pid = 0\n", b"x"*(1024*1024+1)):
            with self.assertRaises(ValueError):
                probe.service_fields(raw)


if __name__ == "__main__":
    unittest.main()
