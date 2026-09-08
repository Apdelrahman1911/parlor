"""Pure host tests. No emulator, Gradle, adb daemon, or signing process starts."""
from pathlib import Path
import shutil
import socket
import tempfile
import unittest
from unittest import mock

from owned_arm64_smoke import (
    OwnedDirectory, properties, stop_owned_adb_socket, validate_instrumentation,
)


CLASS = "com.parlor.app.MainActivityColdStartTest"
METHOD = "testColdStartDisplaysContentWhileSettingsIoIsBlocked"


def status(code, method=METHOD, class_name=CLASS, count=1):
    return (f"INSTRUMENTATION_STATUS: class={class_name}\n"
            f"INSTRUMENTATION_STATUS: test={method}\n"
            f"INSTRUMENTATION_STATUS: numtests={count}\n"
            f"INSTRUMENTATION_STATUS_CODE: {code}\n")


class InstrumentationReceiptTest(unittest.TestCase):
    def validate(self, text):
        return validate_instrumentation(text, CLASS, {METHOD})

    def test_actual_started_and_completed_descriptor_passes(self):
        result = self.validate(status(1) + status(0) + "INSTRUMENTATION_CODE: -1\n")
        self.assertEqual([{"class": CLASS, "method": METHOD}], result)

    def test_zero_adb_exit_and_ok_summary_without_descriptors_cannot_pass(self):
        with self.assertRaises(ValueError):
            self.validate("OK (1 test)\nINSTRUMENTATION_CODE: -1\n")

    def test_instrumentation_success_without_completed_test_cannot_pass(self):
        with self.assertRaises(ValueError):
            self.validate(status(1) + "INSTRUMENTATION_CODE: -1\n")

    def test_skips_ignored_errors_and_failures_cannot_pass(self):
        for code in (-1, -2, -3, -4, 2):
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.validate(status(1) + status(code) + "INSTRUMENTATION_CODE: -1\n")

    def test_wrong_class_wrong_method_and_wrong_count_cannot_pass(self):
        for changed in ({"class_name": "AnotherClass"}, {"method": "AnotherTest"}, {"count": 2}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.validate(status(1, **changed) + status(0, **changed) + "INSTRUMENTATION_CODE: -1\n")

    def test_duplicate_descriptors_cannot_pass(self):
        with self.assertRaises(ValueError):
            self.validate(status(1) + status(0) + status(1) + status(0) + "INSTRUMENTATION_CODE: -1\n")

    def test_out_of_order_completion_cannot_pass(self):
        with self.assertRaises(ValueError):
            self.validate(status(0) + status(1) + "INSTRUMENTATION_CODE: -1\n")

    def test_crash_after_descriptors_and_non_success_exit_cannot_pass(self):
        for tail in ("INSTRUMENTATION_FAILED: process crash\nINSTRUMENTATION_CODE: -1\n",
                     "INSTRUMENTATION_RESULT: shortMsg=Process crashed\nINSTRUMENTATION_CODE: -1\n",
                     "INSTRUMENTATION_CODE: 0\n", "", "INSTRUMENTATION_CODE: -1\nINSTRUMENTATION_CODE: -1\n"):
            with self.subTest(tail=tail), self.assertRaises(ValueError):
                self.validate(status(1) + status(0) + tail)


class OwnedOutputTest(unittest.TestCase):
    def test_marker_open_failure_removes_only_new_empty_directory(self):
        created = []
        original_mkdtemp = tempfile.mkdtemp

        def capture_directory(*args, **kwargs):
            path = original_mkdtemp(*args, **kwargs)
            created.append(Path(path))
            self.addCleanup(lambda: shutil.rmtree(path) if Path(path).exists() else None)
            return path

        with mock.patch("owned_arm64_smoke.tempfile.mkdtemp", side_effect=capture_directory), \
                mock.patch.object(Path, "open", side_effect=OSError("synthetic write failure")):
            with self.assertRaisesRegex(OSError, "synthetic write failure"):
                OwnedDirectory()
        self.assertEqual(1, len(created))
        self.assertFalse(created[0].exists())

    def test_partial_marker_write_failure_removes_attested_marker_and_empty_root(self):
        created = []
        original_mkdtemp = tempfile.mkdtemp
        original_open = Path.open

        def capture_directory(*args, **kwargs):
            path = original_mkdtemp(*args, **kwargs)
            created.append(Path(path))
            self.addCleanup(lambda: shutil.rmtree(path) if Path(path).exists() else None)
            return path

        class FailedWriter:
            def __init__(self, stream):
                self.stream = stream

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                self.stream.close()

            def fileno(self):
                return self.stream.fileno()

            def write(self, value):
                self.stream.write(value[:4])
                raise OSError("synthetic partial marker")

        def failed_marker(path, *args, **kwargs):
            stream = original_open(path, *args, **kwargs)
            return FailedWriter(stream) if path.name == ".parlor-owner" else stream

        with mock.patch("owned_arm64_smoke.tempfile.mkdtemp", side_effect=capture_directory), \
                mock.patch.object(Path, "open", failed_marker):
            with self.assertRaisesRegex(OSError, "synthetic partial marker"):
                OwnedDirectory()
        self.assertEqual(1, len(created))
        self.assertFalse(created[0].exists())

    def test_failed_initialization_preserves_unexpected_child_and_reports_root(self):
        created = []
        original_mkdtemp = tempfile.mkdtemp
        original_open = Path.open

        def capture_directory(*args, **kwargs):
            path = original_mkdtemp(*args, **kwargs)
            created.append(Path(path))
            self.addCleanup(shutil.rmtree, path)
            return path

        def failed_marker(path, *args, **kwargs):
            if path.name == ".parlor-owner":
                with original_open(path.parent / "unexpected-user-file", "w") as stream:
                    stream.write("preserve")
                raise OSError("synthetic write failure")
            return original_open(path, *args, **kwargs)

        with mock.patch("owned_arm64_smoke.tempfile.mkdtemp", side_effect=capture_directory), \
                mock.patch.object(Path, "open", failed_marker):
            with self.assertRaisesRegex(RuntimeError, "preserved") as result:
                OwnedDirectory()
        self.assertIn(str(created[0].resolve()), str(result.exception))
        self.assertEqual("preserve", (created[0] / "unexpected-user-file").read_text())

    def test_attested_temporary_tree_is_removed(self):
        owned = OwnedDirectory()
        self.addCleanup(lambda: shutil.rmtree(owned.path) if owned.path.exists() else None)
        (owned.path / "generated.img").write_text("synthetic")
        owned.remove()
        self.assertFalse(owned.path.exists())

    def test_changed_marker_preserves_tree(self):
        owned = OwnedDirectory()
        self.addCleanup(shutil.rmtree, owned.path)
        (owned.path / ".parlor-owner").write_text("not-the-created-token")
        with self.assertRaises(RuntimeError):
            owned.remove()
        self.assertTrue(owned.path.exists())

    def test_symlinked_marker_cannot_authorize_cleanup(self):
        owned = OwnedDirectory()
        self.addCleanup(shutil.rmtree, owned.path)
        marker = owned.path / ".parlor-owner"
        target = owned.path / "copied-marker"
        target.write_text(owned.token)
        marker.unlink()
        marker.symlink_to(target)
        with self.assertRaises(RuntimeError):
            owned.remove()
        self.assertTrue(owned.path.exists())

    def test_child_symlink_does_not_delete_external_target(self):
        owned = OwnedDirectory()
        self.addCleanup(lambda: shutil.rmtree(owned.path) if owned.path.exists() else None)
        with tempfile.TemporaryDirectory(prefix="parlor-fixture-external-") as external:
            sentinel = Path(external) / "source.txt"
            sentinel.write_text("preserve")
            (owned.path / "outside").symlink_to(Path(external), target_is_directory=True)
            owned.remove()
            self.assertEqual("preserve", sentinel.read_text())

    def test_property_duplicate_is_not_silently_normalized(self):
        with tempfile.TemporaryDirectory(prefix="parlor-properties-") as directory:
            path = Path(directory) / "source.properties"
            path.write_text("# comment\nPkg.Revision=9\nPkg.Revision=10\n")
            with self.assertRaises(ValueError):
                properties(path)


class OwnedAdbShutdownTest(unittest.TestCase):
    def owned(self, socket_node=True):
        owned = OwnedDirectory()
        self.addCleanup(lambda: shutil.rmtree(owned.path) if owned.path.exists() else None)
        if socket_node:
            # Filesystem socket node only: no listener, thread, or adb server.
            node = socket.socket(socket.AF_UNIX)
            self.addCleanup(node.close)
            node.bind(str(owned.path / 'adb.sock'))
        return owned

    def test_missing_endpoint_cannot_create_a_socket_or_start_a_server(self):
        owned = self.owned(socket_node=False)
        with mock.patch('owned_arm64_smoke.socket.socket') as client, \
                mock.patch('owned_arm64_smoke.subprocess.Popen') as process:
            self.assertFalse(stop_owned_adb_socket(owned))
        client.assert_not_called()
        process.assert_not_called()

    def test_refused_owned_endpoint_returns_without_autostart(self):
        owned = self.owned()
        client = mock.Mock()
        client.connect.side_effect = ConnectionRefusedError()
        with mock.patch('owned_arm64_smoke.socket.socket', return_value=client), \
                mock.patch('owned_arm64_smoke.subprocess.Popen') as process:
            self.assertFalse(stop_owned_adb_socket(owned))
        process.assert_not_called()
        client.sendall.assert_not_called()
        client.close.assert_called_once()

    def test_exact_host_kill_packet_and_fragmented_okay_response(self):
        owned = self.owned()
        client = mock.Mock()
        client.recv.side_effect = [b'OK', b'AY']
        with mock.patch('owned_arm64_smoke.socket.socket', return_value=client), \
                mock.patch('owned_arm64_smoke.subprocess.Popen') as process:
            self.assertTrue(stop_owned_adb_socket(owned))
        client.connect.assert_called_once_with(str(owned.path / 'adb.sock'))
        client.sendall.assert_called_once_with(b'0009host:kill')
        client.close.assert_called_once()
        process.assert_not_called()

    def test_bad_acknowledgement_is_a_cleanup_failure_not_a_pass(self):
        owned = self.owned()
        client = mock.Mock()
        client.recv.return_value = b'FAIL'
        with mock.patch('owned_arm64_smoke.socket.socket', return_value=client):
            with self.assertRaisesRegex(RuntimeError, 'acknowledge'):
                stop_owned_adb_socket(owned)
        client.close.assert_called_once()

    def test_symlink_cannot_redirect_shutdown_to_another_endpoint(self):
        owned = self.owned(socket_node=False)
        target = owned.path / 'not-a-socket'
        target.write_text('preserve')
        (owned.path / 'adb.sock').symlink_to(target)
        with mock.patch('owned_arm64_smoke.socket.socket') as client:
            with self.assertRaisesRegex(RuntimeError, 'socket identity'):
                stop_owned_adb_socket(owned)
        client.assert_not_called()
        self.assertEqual('preserve', target.read_text())


if __name__ == "__main__":
    unittest.main()
