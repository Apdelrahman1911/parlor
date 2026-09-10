"""Owned/mocked metadata only; no sudo, live proc census, or privilege changes."""
from __future__ import annotations

import importlib.util
import io
import json
import os
import stat
import subprocess
import sys
import unittest
from contextlib import contextmanager
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import call, patch

ROOT = Path(__file__).resolve().parents[3]
modules = []
for name in ("verification_hygiene", "linux_exe_reader"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts/ci" / (name + ".py"))
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    modules.append(module)
hygiene, reader = modules


@unittest.skipUnless(sys.platform == "linux", "Linux-only fixed reader")
class LinuxExeReaderTest(unittest.TestCase):
    def setUp(self) -> None:
        for mock in (patch.object(reader.os, "getuid", return_value=0),
                     patch.object(reader.os, "geteuid", return_value=0),
                     patch.dict(os.environ, {"SUDO_UID": "1001"})):
            mock.start()
            self.addCleanup(mock.stop)
        self.identity = (123, (1001,) * 4)
        self.metadata = os.stat_result((stat.S_IFREG | 0o755, 45, 67, 1, 1001, 1001, 0, 0, 0, 0))

    @contextmanager
    def accesses(self, identities=None, paths=None, metadata=None):
        with patch.object(reader.os, "open", side_effect=[10, 11]) as opened, \
                patch.object(reader.os, "close") as closed, \
                patch.object(reader, "identity", side_effect=identities or [self.identity, self.identity]), \
                patch.object(reader.os, "readlink", side_effect=paths or ["/fixed/executable"] * 2) as links, \
                patch.object(reader.os, "stat", side_effect=metadata or [self.metadata] * 2) as stats:
            yield opened, closed, links, stats

    def test_one_pinned_numeric_directory_and_exact_read_only_metadata(self) -> None:
        with self.accesses() as (opened, closed, links, stats):
            value = reader.read_executable(1001, 101, 123)
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW
        self.assertEqual(opened.call_args_list, [call("/proc", flags), call("101", flags, dir_fd=10)])
        self.assertEqual(closed.call_args_list, [call(10), call(11)])
        self.assertEqual(links.call_args_list, [call("exe", dir_fd=11)] * 2)
        self.assertEqual(stats.call_args_list, [call("exe", dir_fd=11, follow_symlinks=True)] * 2)
        self.assertEqual((value["uid"], value["pid"], value["starttime"]), (1001, 101, 123))
        self.assertEqual(value["observations"][0], value["observations"][1])

    def test_invalid_caller_uid_pid_and_lifetime_never_open_proc(self) -> None:
        requests = [(0, 101, 123), (1002, 101, 123), (True, 101, 123), (1001, 0, 123),
                    (1001, 2**31, 123), (1001, "../101", 123), (1001, 101, -1), (1001, 101, True), (1001, 101, 2**64)]
        with patch.object(reader.os, "open") as opened:
            for request in requests:
                with self.subTest(request=request), self.assertRaises(reader.ReaderError):
                    reader.read_executable(*request)
            for value in ("", "0", "01001", "1002", "1001\n"):
                with self.subTest(sudo_uid=value), patch.dict(os.environ, {"SUDO_UID": value}), self.assertRaises(reader.ReaderError):
                    reader.read_executable(1001, 101, 123)
            for uid in (0, 1001):
                with patch.object(reader.os, "geteuid", return_value=1001), self.assertRaises(reader.ReaderError):
                    reader.read_executable(uid, 101, 123)
            opened.assert_not_called()
        for text in ("../101", "-1", "+1", "01", " 1", "1\n", "9" * 21):
            with self.subTest(decimal=text), self.assertRaises(reader.ReaderError):
                reader.decimal(text, 0, 2**64)

    def test_changed_or_mixed_uid_and_lifetime_fail_before_emitting_paths(self) -> None:
        for changed in ((124, (1001,) * 4), (123, (0,) * 4), (123, (1001, 0, 1001, 1001)), (123, (1002,) * 4)):
            for before in (True, False):
                with self.subTest(changed=changed, before=before), self.accesses(
                        identities=[changed, self.identity] if before else [self.identity, changed]) as (_, closed, links, _):
                    with self.assertRaisesRegex(reader.ReaderError, "PROC_LIFETIME_OR_UID_CHANGED"):
                        reader.read_executable(1001, 101, 123)
                    self.assertEqual(links.call_count, 0 if before else 2)
                    self.assertIn(call(11), closed.call_args_list)

    def test_deleted_nonregular_changed_or_oversized_executable_fails(self) -> None:
        for paths, metadata in ((["/old", "/new"], None), (["relative"] * 2, None),
                (["/private (deleted)"] * 2, None), (["/" + "x" * 4096] * 2, None),
                (None, [self.metadata, os.stat_result((stat.S_IFREG, 46, 67, 1, 0, 0, 0, 0, 0, 0))]),
                (None, [os.stat_result((stat.S_IFDIR, 45, 67, 1, 0, 0, 0, 0, 0, 0))] * 2)):
            with self.subTest(paths=paths is not None), self.accesses(paths=paths, metadata=metadata), \
                    self.assertRaisesRegex(reader.ReaderError, "PROC_EXECUTABLE_AMBIGUOUS"):
                reader.read_executable(1001, 101, 123)
        for error in (PermissionError("private path"), FileNotFoundError("private path")):
            with self.accesses(paths=[error]), self.assertRaises(type(error)):
                reader.read_executable(1001, 101, 123)

    def test_owned_identity_records_are_bounded_and_not_followed(self) -> None:
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            tail = ["S"] + ["0"] * 49
            tail[19] = "123"
            good_stat = b"101 (name ) with\nparen) " + " ".join(tail).encode()
            good_status = b"Uid:\t1001\t1001\t1001\t1001\n"
            descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
            try:
                for name, data in (("stat", b"bad"), ("status", b"Uid: 1 2\n"),
                                   ("status", b"x" * (reader.MAX_RECORD_BYTES + 1))):
                    (directory / "stat").write_bytes(good_stat)
                    (directory / "status").write_bytes(good_status)
                    self.assertEqual(reader.identity(descriptor, 101), self.identity)
                    (directory / name).write_bytes(data)
                    with self.assertRaises(reader.ReaderError):
                        reader.identity(descriptor, 101)
                (directory / "stat").unlink()
                (directory / "stat").symlink_to(directory / "status")
                with self.assertRaises(OSError):
                    reader.identity(descriptor, 101)
            finally:
                os.close(descriptor)

    def test_cli_has_own_deadline_and_never_emits_exception_message(self) -> None:
        for arguments in (["1001", "101", "123"], ["1001", "../private-path", "123"]):
            stream = io.TextIOWrapper(io.BytesIO(), encoding="utf-8")
            with patch.object(reader.sys, "argv", ["linux_exe_reader.py", *arguments]), \
                    patch.object(reader.sys, "stdout", stream), patch.object(reader.signal, "signal"), \
                    patch.object(reader.signal, "setitimer") as timer, \
                    patch.object(reader, "read_executable", side_effect=PermissionError("private-path")):
                self.assertEqual(reader.main(), 1)
                stream.flush()
                self.assertNotIn(b"private-path", stream.buffer.getvalue())
                self.assertEqual(timer.call_args_list, [call(reader.signal.ITIMER_REAL, reader.SECONDS), call(reader.signal.ITIMER_REAL, 0)])
        with self.assertRaisesRegex(reader.ReaderError, "READER_DEADLINE"):
            reader.expired(None, None)


@unittest.skipUnless(sys.platform == "linux", "Linux-only reader client")
class LinuxExeReaderClientTest(unittest.TestCase):
    def setUp(self) -> None:
        for mock in (patch.object(hygiene.os, "getuid", return_value=1001),
                     patch.object(hygiene.os, "geteuid", return_value=1001),
                     patch.object(hygiene.time, "monotonic", return_value=10.0)):
            mock.start()
            self.addCleanup(mock.stop)
        row = {"path": "/fixed/executable", "device": 67, "inode": 45, "mode": stat.S_IFREG | 0o755}
        self.response = {"schema": 1, "uid": 1001, "pid": 101, "starttime": 123, "observations": [dict(row), dict(row)]}

    def test_fixed_isolated_command_original_uid_and_bounded_private_protocol(self) -> None:
        completed = subprocess.CompletedProcess([], 0, json.dumps(self.response).encode())
        with patch.object(hygiene.subprocess, "run", return_value=completed) as run:
            value = hygiene.android_privileged_executable(1001, 101, 123, 15.0)
        self.assertEqual(value, ("/fixed/executable", (67, 45, stat.S_IFREG | 0o755)) * 2)
        self.assertEqual(run.call_args.args[0], ["/usr/bin/sudo", "-n", "--", "/usr/bin/python3", "-I", "-S", "-B",
            str(ROOT / "scripts/ci/linux_exe_reader.py"), "1001", "101", "123"])
        self.assertEqual(run.call_args.kwargs, dict(stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, cwd="/", env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"}, timeout=2.0, check=False))

    def test_denied_missing_timeout_or_expired_helper_cannot_be_absence(self) -> None:
        for error, code in ((PermissionError("private-path"), "PROC_EXE_READER_UNAVAILABLE"),
                            (subprocess.TimeoutExpired(["private-path"], 2), "PROC_EXE_READER_TIMEOUT")):
            with patch.object(hygiene.subprocess, "run", side_effect=error), self.assertRaisesRegex(hygiene.AndroidAuditError, code):
                hygiene.android_privileged_executable(1001, 101, 123, 15.0)
        with patch.object(hygiene.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, b"private-path")), \
                self.assertRaisesRegex(hygiene.AndroidAuditError, "PROC_EXE_READER_FAILED"):
            hygiene.android_privileged_executable(1001, 101, 123, 15.0)
        with patch.object(hygiene.subprocess, "run") as run, self.assertRaisesRegex(hygiene.AndroidAuditError, "PROC_SCAN_DEADLINE"):
            hygiene.android_privileged_executable(1001, 101, 123, 9.0)
        run.assert_not_called()
        with patch.object(hygiene.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, json.dumps(self.response).encode())), \
                patch.object(hygiene.time, "monotonic", side_effect=[10.0, 16.0]), \
                self.assertRaisesRegex(hygiene.AndroidAuditError, "PROC_SCAN_DEADLINE"):
            hygiene.android_privileged_executable(1001, 101, 123, 15.0)

    def test_malformed_oversized_wrong_identity_and_changed_metadata_rejected(self) -> None:
        payloads = [b"not json private-path", b"x" * (hygiene.ANDROID_EXE_READER_MAX_BYTES + 1), b"\xff",
                    json.dumps(self.response).replace('"uid": 1001', '"uid": 0, "uid": 1001').encode()]
        for key, value in (("uid", 0), ("pid", 102), ("starttime", 124), ("schema", True), ("uid", True), ("extra", "private")):
            payloads.append(json.dumps({**self.response, key: value}).encode())
        for key, value in (("path", "relative"), ("path", "/gone (deleted)"), ("path", "/" + "x" * 4096),
                           ("path", "/nul\0path"), ("device", True), ("inode", -1), ("mode", stat.S_IFDIR)):
            response = json.loads(json.dumps(self.response))
            response["observations"][0][key] = value
            payloads.append(json.dumps(response).encode())
        for payload in payloads:
            with self.subTest(bytes=len(payload)), patch.object(hygiene.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, payload)), \
                    self.assertRaisesRegex(hygiene.AndroidAuditError, "PROC_EXE_READER_RESPONSE_INVALID"):
                hygiene.android_privileged_executable(1001, 101, 123, 15.0)

    def test_invalid_request_or_redirected_helper_never_invokes_sudo(self) -> None:
        with patch.object(hygiene.subprocess, "run") as run:
            for request in ((0, 101, 123), (1002, 101, 123), (1001, True, 123), (1001, 101, "123")):
                with self.subTest(request=request), self.assertRaises(hygiene.AndroidAuditError):
                    hygiene.android_privileged_executable(*request, 15.0)
            with patch.object(Path, "resolve", return_value=Path("/private-redirect")), self.assertRaisesRegex(hygiene.AndroidAuditError, "PROC_EXE_READER_SOURCE_INVALID"):
                hygiene.android_privileged_executable(1001, 101, 123, 15.0)
            run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
