"""Synthetic integration contracts; not an emulator or native clone claim."""
import ast
import errno
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import owned_arm64_smoke_sdk as runner
import owned_sdk_image as adapter


class RootIntegrationTests(unittest.TestCase):
    def test_native_probe_closes_source_if_directory_open_fails(self):
        owned = runner.OwnedDirectory()
        original_open = os.open
        acquired = []
        def opening(path, flags):
            if Path(path).name == "native-clone-probe":
                raise OSError(errno.EMFILE, "synthetic second-open failure")
            fd = original_open(path, flags)
            acquired.append(fd)
            return fd
        try:
            with patch.object(runner.os, "open", side_effect=opening):
                with self.assertRaises(OSError):
                    runner.native_clone_probe(owned)
            self.assertEqual(1, len(acquired))
            for fd in acquired:
                with self.assertRaises(OSError) as error:
                    os.fstat(fd)
                self.assertEqual(errno.EBADF, error.exception.errno)
        finally:
            owned.remove()

    def test_native_probe_closes_both_descriptors_if_clone_fails(self):
        owned = runner.OwnedDirectory()
        acquired = []
        def clone(source_fd, parent_fd, _name):
            acquired.extend((source_fd, parent_fd))
            raise OSError(errno.ENOTSUP, "synthetic clone failure")
        try:
            with patch.object(runner, "_clone_open_file", side_effect=clone):
                with self.assertRaises(OSError):
                    runner.native_clone_probe(owned)
            self.assertEqual(2, len(acquired))
            for fd in acquired:
                with self.assertRaises(OSError) as error:
                    os.fstat(fd)
                self.assertEqual(errno.EBADF, error.exception.errno)
        finally:
            owned.remove()

    def test_preserved_shared_runner_stays_exactly_bound(self):
        self.assertEqual(
            "38ea4c0000a2c426f8475b4616e77ee0d8bb25bb424dc7516348c2095cd08b48",
            hashlib.sha256((HERE.parent / "owned_arm64_smoke.py").read_bytes()).hexdigest(),
        )

    def test_binding_includes_derived_runner_adapter_and_native_owner(self):
        binding = runner.control_binding()
        self.assertEqual({"owned_arm64_smoke_sdk.py", "darwin_owned_processes.py",
                          "avd-sdk-root-01/owned_sdk_image.py"},
                         {row["path"] for row in binding["files"]})
        self.assertEqual(binding["sha256"], hashlib.sha256(
            json.dumps(binding["files"], sort_keys=True).encode()).hexdigest())
        for row in binding["files"]:
            self.assertEqual(row["sha256"], runner.sha256(HERE.parent / row["path"]))

    def test_metadata_observation_is_read_only_and_records_absence_and_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image = root / adapter.IMAGE_RELATIVE
            image.mkdir(parents=True)
            (image / "package.xml").write_text("synthetic package")
            (image / "source.properties").write_text("synthetic metadata")
            before = runner.sdk_metadata(root)
            self.assertFalse(before[0]["present"])
            self.assertEqual(before, runner.sdk_metadata(root))
            (root / ".knownPackages").write_text("synthetic index")
            self.assertNotEqual(before, runner.sdk_metadata(root))
            (root / ".knownPackages").unlink()
            (root / ".knownPackages").symlink_to(image / "package.xml")
            with self.assertRaises(RuntimeError):
                runner.sdk_metadata(root)

    def test_checkpoint_preserves_cancellation_without_starting_commands(self):
        with patch.object(runner, "CANCELLED", True):
            with self.assertRaises(InterruptedError):
                runner.checkpoint()
        with patch.object(runner, "CANCELLED", False):
            runner.checkpoint()

    def test_mid_clone_cancellation_preserves_source_and_owned_finalizer_removes_partial_copy(self):
        with tempfile.TemporaryDirectory() as temporary:
            sdk = Path(temporary)
            image = sdk / adapter.IMAGE_RELATIVE
            image.mkdir(parents=True)
            for name in ("package.xml", "source.properties", "system.img"):
                (image / name).write_bytes(b"synthetic owned fixture")
            before = {p.name: p.read_bytes() for p in image.iterdir()}
            metadata = {name: hashlib.sha256(before[name]).hexdigest()
                        for name in adapter.METADATA_HASHES}
            emulator = sdk / adapter.EMULATOR_RELATIVE
            emulator.mkdir()
            for name in adapter.EMULATOR_HASHES:
                path = emulator / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"synthetic complete-emulator fixture")
                if name in adapter.EMULATOR_EXECUTABLES:
                    path.chmod(0o755)
            emulator_metadata = {name: hashlib.sha256((emulator / name).read_bytes()).hexdigest()
                                 for name in adapter.EMULATOR_HASHES}
            emulator_before = adapter._inventory(emulator)
            owned = runner.OwnedDirectory()
            calls = []
            def clone(source_fd, parent_fd, name):
                os.lseek(source_fd, 0, os.SEEK_SET)
                target = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600, dir_fd=parent_fd)
                with os.fdopen(target, "wb") as stream:
                    stream.write(os.read(source_fd, 4096))
                calls.append(name)
            def cancelled_after_clone():
                if calls:
                    raise InterruptedError("synthetic interruption after first clone")
            try:
                with patch.object(adapter, "METADATA_HASHES", metadata), \
                        patch.object(adapter, "EMULATOR_HASHES", emulator_metadata), \
                        patch.object(adapter, "MIN_FREE_BYTES", 0):
                    with self.assertRaises(InterruptedError):
                        adapter.clone_installed_image(sdk, owned, cancelled_after_clone, clone_one=clone)
                self.assertEqual(1, len(calls))
                self.assertTrue((owned.path / "sdk").exists())
                self.assertEqual(before, {p.name: p.read_bytes() for p in image.iterdir()})
                self.assertEqual(emulator_before, adapter._inventory(emulator))
            finally:
                owned.remove()
            self.assertFalse(owned.path.exists())

    def test_same_method_matrix_and_owned_shutdown_remain_in_actual_run(self):
        source = (HERE.parent / "owned_arm64_smoke_sdk.py").read_text()
        parsed = ast.parse(source)
        run = next(node for node in parsed.body if isinstance(node, ast.FunctionDef) and node.name == "run")
        calls = [node for node in ast.walk(run) if isinstance(node, ast.Call)]
        names = {node.func.id for node in calls if isinstance(node.func, ast.Name)}
        self.assertTrue({"clone_installed_image", "cli20_invocation", "validate_created_image_path",
                         "native_clone_probe", "sdk_metadata", "stop_owned_adb_socket"} <= names)
        emulator_calls = [node for node in calls if isinstance(node.func, ast.Attribute) and
                          node.func.attr == "worker" and node.args and
                          isinstance(node.args[0], ast.Constant) and node.args[0].value == "emulator"]
        self.assertEqual(1, len(emulator_calls))
        options = emulator_calls[0].args[1].elts
        i = next(i for i, node in enumerate(options) if isinstance(node, ast.Constant) and node.value == "-sysdir")
        self.assertIsInstance(options[i + 1], ast.Name)
        self.assertEqual("owned_image", options[i + 1].id)
        self.assertEqual(3, sum(map(len, runner.CLASSES.values())))


if __name__ == "__main__":
    unittest.main()
