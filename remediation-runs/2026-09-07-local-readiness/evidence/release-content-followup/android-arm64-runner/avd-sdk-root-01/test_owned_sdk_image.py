"""DRAFT pure tests; fake clones are not APFS/CLI/emulator runtime evidence."""
import ctypes
import errno
import hashlib
import os
import shutil
import stat
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import owned_sdk_image as control


class FakeOwned:
    def __init__(self, path):
        self.path = path
        self.path.mkdir()
        self.valid = True

    def attest(self):
        if not self.valid:
            raise RuntimeError("Ownership lost")


class SdkRootTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="parlor-sdk-pure-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.sdk = self.base / "installed SDK"
        self.image = self.sdk / control.IMAGE_RELATIVE
        self.image.mkdir(parents=True)
        (self.image / "package.xml").write_bytes(b"<public-fixture/>")
        (self.image / "source.properties").write_bytes(b"Pkg.Revision=9\n")
        (self.image / "system.img").write_bytes(b"synthetic image")
        (self.image / "data").mkdir()
        (self.image / "data/empty_data_disk").write_bytes(b"synthetic data")
        self.emulator = self.sdk / control.EMULATOR_RELATIVE
        self.emulator.mkdir()
        emulator_files = {
            "package.xml": b"<authentic-emulator-synthetic-fixture/>",
            "source.properties": b"Pkg.Revision=36.6.11\nPkg.Path=emulator\n",
            "lib/hardware-properties.ini": b"name=hw.ramSize\ntype=integer\ndefault=2048\n",
            "emulator": b"synthetic emulator; never executed",
            "mksdcard": b"synthetic mksdcard; never executed",
            "qemu/darwin-aarch64/qemu-system-aarch64": b"synthetic complete package extra tool",
            "NOTICE.txt": b"synthetic public notice",
        }
        for name, payload in emulator_files.items():
            path = self.emulator / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
            path.chmod(0o755 if name in control.EMULATOR_EXECUTABLES or name.startswith("qemu/") else 0o644)
        self.original_emulator_pins = dict(control.EMULATOR_HASHES)
        emulator_pins = {name: hashlib.sha256((self.emulator / name).read_bytes()).hexdigest()
                         for name in control.EMULATOR_HASHES}
        self.owned = FakeOwned(self.base / "owned space ; literal")
        for name in ("home", "tmp"):
            (self.owned.path / name).mkdir()
        expected = {name: hashlib.sha256((self.image / name).read_bytes()).hexdigest()
                    for name in control.METADATA_HASHES}
        for patcher in (patch.object(control, "METADATA_HASHES", expected),
                        patch.object(control, "EMULATOR_HASHES", emulator_pins),
                        patch.object(control, "MIN_FREE_BYTES", 0)):
            patcher.start()
            self.addCleanup(patcher.stop)

    @staticmethod
    def fake_clone(source_fd, parent_fd, name):
        # A bounded synthetic copy ONLY IN TESTS, never a native fallback.
        os.lseek(source_fd, 0, os.SEEK_SET)
        destination = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600, dir_fd=parent_fd)
        with os.fdopen(destination, "wb") as stream:
            stream.write(os.read(source_fd, 4096))

    def clone(self, clone_one=None, checkpoint=lambda: None):
        return control.clone_installed_image(self.sdk, self.owned, checkpoint,
                                             clone_one=clone_one or self.fake_clone)

    def test_clone_records_all_files_with_equal_bytes_and_distinct_inodes(self):
        before = control._inventory(self.image)
        result = self.clone()
        self.assertEqual(before, control._inventory(self.image))
        self.assertEqual(4, len(result["files"]))
        self.assertEqual(str(self.owned.path / "sdk"), result["owned_sdk"])
        for item in result["files"]:
            source = self.image / item["path"]
            target = Path(result["owned_image"]) / item["path"]
            self.assertEqual(source.read_bytes(), target.read_bytes())
            self.assertNotEqual(source.stat().st_ino, target.stat().st_ino)
        # Scanner-like writes touch only the owned metadata and SDK root.
        (Path(result["owned_sdk"]) / ".knownPackages").write_bytes(b"owned")
        (Path(result["owned_image"]) / "package.xml").write_bytes(b"owned replacement")
        self.assertEqual(b"<public-fixture/>", (self.image / "package.xml").read_bytes())
        self.assertFalse((self.sdk / ".knownPackages").exists())

    def test_error_propagates_without_copy_fallback_or_source_cleanup(self):
        before = control._inventory(self.image)
        fail = Mock(side_effect=OSError(errno.ENOTSUP, "unsupported clone"))
        with self.assertRaises(OSError):
            self.clone(fail)
        self.assertEqual(1, fail.call_count)
        self.assertEqual(before, control._inventory(self.image))
        self.assertTrue((self.owned.path / "sdk").is_dir())

    def test_existing_owned_sdk_is_never_reused(self):
        (self.owned.path / "sdk").mkdir()
        (self.owned.path / "sdk/user-marker").write_text("preserve")
        with self.assertRaises(FileExistsError):
            self.clone()
        self.assertEqual("preserve", (self.owned.path / "sdk/user-marker").read_text())

    def test_symlinks_and_hardlinks_in_source_are_rejected_before_clone(self):
        clone = Mock()
        link = self.image / "escape"
        link.symlink_to(self.base)
        with self.assertRaises(ValueError):
            self.clone(clone)
        link.unlink()
        os.link(self.image / "system.img", link)
        with self.assertRaises(ValueError):
            self.clone(clone)
        clone.assert_not_called()

    def test_special_file_and_unbounded_inventory_are_rejected(self):
        special = self.image / "pipe"
        os.mkfifo(special)
        with self.assertRaises(ValueError):
            self.clone()
        special.unlink()
        with patch.object(control, "MAX_ENTRIES", 2), self.assertRaises(ValueError):
            self.clone()
        with patch.object(control, "MAX_IMAGE_BYTES", 1), self.assertRaises(ValueError):
            self.clone()

    def test_metadata_drift_and_missing_metadata_fail_closed(self):
        (self.image / "package.xml").write_text("wrong revision")
        with self.assertRaisesRegex(ValueError, "metadata differs"):
            self.clone()

    def test_missing_metadata_fails_instead_of_accepting_partial_tree(self):
        (self.image / "package.xml").unlink()
        with self.assertRaisesRegex(ValueError, "metadata is absent"):
            self.clone()

    def test_incorrect_clone_bytes_fail(self):
        def corrupt(source_fd, parent_fd, name):
            self.fake_clone(source_fd, parent_fd, name)
            fd = os.open(name, os.O_WRONLY | os.O_TRUNC, dir_fd=parent_fd)
            os.close(fd)
        with self.assertRaises(ValueError):
            self.clone(corrupt)

    def test_source_mutation_during_clone_fails(self):
        def mutate(source_fd, parent_fd, name):
            self.fake_clone(source_fd, parent_fd, name)
            (self.image / "source.properties").write_bytes(b"changed public fixture")
        with self.assertRaises(ValueError):
            self.clone(mutate)

    def test_cancel_and_owner_loss_fail_without_changing_installed_sdk(self):
        before = control._inventory(self.image)
        def cancel():
            raise InterruptedError("cancelled")
        with self.assertRaises(InterruptedError):
            self.clone(checkpoint=cancel)
        self.assertEqual(before, control._inventory(self.image))
        self.owned.valid = False
        with self.assertRaisesRegex(RuntimeError, "Ownership lost"):
            self.clone()

    def test_low_disk_fails_before_destination_creation(self):
        usage = Mock(free=1)
        with patch.object(control.shutil, "disk_usage", return_value=usage), \
                patch.object(control, "MIN_FREE_BYTES", 2), self.assertRaises(RuntimeError):
            self.clone()
        self.assertFalse((self.owned.path / "sdk").exists())

    def test_native_abi_is_exact_and_error_has_no_fallback(self):
        fake = Mock()
        fake.fclonefileat.return_value = -1
        ctypes.set_errno(errno.ENOTSUP)
        with patch.object(control.platform, "system", return_value="Darwin"), \
                patch.object(control.ctypes, "CDLL", return_value=fake) as load:
            with self.assertRaises(OSError) as caught:
                control._clone_open_file(10, 11, "system.img")
        self.assertEqual(errno.ENOTSUP, caught.exception.errno)
        load.assert_called_once_with("/usr/lib/libSystem.B.dylib", use_errno=True)
        fake.fclonefileat.assert_called_once_with(10, 11, b"system.img", 3)
        self.assertEqual(ctypes.c_int, fake.fclonefileat.restype)
        self.assertEqual([ctypes.c_int, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint32],
                         fake.fclonefileat.argtypes)

    def test_explicit_java_sdk_and_environment_are_not_shell_parsed(self):
        result = self.clone()
        cli = self.base / "CLI 20 ; quoted"
        (cli / "bin").mkdir(parents=True)
        (cli / "lib").mkdir()
        (cli / "bin/avdmanager").write_bytes(b"public wrapper fixture")
        (cli / "lib/avdmanager-classpath.jar").write_bytes(b"classpath fixture")
        metadata = cli / "source.properties"
        metadata.write_bytes(b"Pkg.Revision=20.0\n")
        java = self.base / "java 21"
        java.write_bytes(b"public executable fixture")
        env = {key: "hostile fixture" for key in control.JAVA_ENV_OPTIONS}
        env.update({key: "conflicting deprecated fixture" for key in control.DEPRECATED_PREFS_ENV_OPTIONS})
        env.update(HOME=str(self.owned.path / "home"), ANDROID_HOME=str(self.sdk),
                   ANDROID_SDK_ROOT=str(self.sdk), ANDROID_USER_HOME=str(self.owned.path / "android-user"))
        pinned = hashlib.sha256(metadata.read_bytes()).hexdigest()
        with patch.object(control, "CLI20_PROPERTIES_SHA256", pinned):
            argv, effective = control.cli20_invocation(java, cli / "bin/avdmanager", self.owned,
                                                       result["owned_sdk"], env, ["list", "avd"])
        properties = [part for part in argv if part.startswith("-Dcom.android.sdkmanager.toolsdir=")]
        self.assertEqual(1, len(properties))
        toolsdir = Path(properties[0].split("=", 1)[1])
        self.assertEqual(Path(result["owned_sdk"]), toolsdir.parent.parent)
        self.assertEqual(str(java), argv[0])
        self.assertEqual(["list", "avd"], argv[-2:])
        self.assertEqual(str(cli / "lib/avdmanager-classpath.jar"), argv[argv.index("-classpath") + 1])
        self.assertEqual(str(self.owned.path / "sdk"), effective["ANDROID_HOME"])
        self.assertEqual(effective["ANDROID_HOME"], effective["ANDROID_SDK_ROOT"])
        self.assertTrue(set(control.JAVA_ENV_OPTIONS).isdisjoint(effective))
        self.assertTrue(set(control.DEPRECATED_PREFS_ENV_OPTIONS).isdisjoint(effective))
        self.assertEqual(env["ANDROID_USER_HOME"], effective["ANDROID_USER_HOME"])
        self.assertTrue(set(control.DEPRECATED_PREFS_ENV_OPTIONS) <= set(env))
        self.assertEqual(str(self.sdk), env["ANDROID_HOME"])

    def test_unexpected_config_paths_rejected_not_rewritten(self):
        result = self.clone()
        correct = {"image.sysdir.1": control.IMAGE_RELATIVE.as_posix() + "/"}
        self.assertEqual(result["owned_image"], control.validate_created_image_path(correct, result["owned_sdk"]))
        for wrong in (str(self.image), "../system.img", "/tmp/unowned/", ""):
            with self.assertRaises(ValueError):
                control.validate_created_image_path({"image.sysdir.1": wrong}, result["owned_sdk"])
        with self.assertRaises(ValueError):
            control.validate_created_image_path(dict(correct, **{"image.sysdir.2": ""}), result["owned_sdk"])


class SdkPackageTests(unittest.TestCase):
    # Reuse fixtures/helpers, not inherited test methods (no duplicate evidence).
    setUp = SdkRootTests.setUp
    fake_clone = staticmethod(SdkRootTests.fake_clone)
    clone = SdkRootTests.clone

    def test_complete_emulator_package_and_image_are_cloned_not_metadata_facades(self):
        before = {str(path): control._inventory(path) for path in (self.image, self.emulator)}
        result = self.clone()
        self.assertEqual([control.IMAGE_RELATIVE.as_posix(), "emulator"],
                         [package["path"] for package in result["packages"]])
        self.assertEqual(sum(len(item[0]) for item in before.values()), result["selected_entries"])
        self.assertEqual(sum(item[1] for item in before.values()), result["selected_logical_bytes"])
        self.assertEqual(result["packages"][0]["files"], result["files"])
        self.assertEqual(str(self.owned.path / "sdk/emulator"), result["owned_emulator"])
        for package in result["packages"]:
            source, destination = Path(package["source"]), Path(package["destination"])
            self.assertEqual(set(control._inventory(source)[0]), set(control._inventory(destination)[0]))
            for item in package["files"]:
                original, cloned = source / item["path"], destination / item["path"]
                self.assertEqual(original.read_bytes(), cloned.read_bytes())
                self.assertNotEqual(original.stat().st_ino, cloned.stat().st_ino)
                self.assertEqual(1, cloned.stat().st_nlink)
                self.assertEqual(hashlib.sha256(cloned.read_bytes()).hexdigest(), item["sha256"])
        copied = Path(result["owned_emulator"])
        self.assertTrue((copied / "qemu/darwin-aarch64/qemu-system-aarch64").is_file())
        (copied / "package.xml").write_bytes(b"synthetic owned scanner replacement")
        (Path(result["owned_sdk"]) / ".knownPackages").write_bytes(b"owned scanner index")
        self.assertFalse((self.sdk / ".knownPackages").exists())
        for source in (self.image, self.emulator):
            self.assertEqual(before[str(source)], control._inventory(source))

    def test_selected_packages_share_one_entry_ceiling_before_any_clone(self):
        sizes = [len(control._inventory(path)[0]) for path in (self.image, self.emulator)]
        clone = Mock()
        with patch.object(control, "MAX_ENTRIES", max(sizes)), self.assertRaisesRegex(ValueError, "shared entry/size"):
            self.clone(clone)
        clone.assert_not_called()
        self.assertFalse((self.owned.path / "sdk").exists())

    def test_selected_packages_share_one_logical_size_ceiling_before_any_clone(self):
        sizes = [control._inventory(path)[1] for path in (self.image, self.emulator)]
        clone = Mock()
        with patch.object(control, "MAX_IMAGE_BYTES", max(sizes)), self.assertRaisesRegex(ValueError, "shared entry/size"):
            self.clone(clone)
        clone.assert_not_called()
        self.assertFalse((self.owned.path / "sdk").exists())

    def test_missing_emulator_package_is_not_replaced_with_synthetic_metadata(self):
        shutil.rmtree(self.emulator)  # This test's TemporaryDirectory fixture only.
        clone = Mock()
        with self.assertRaises(FileNotFoundError):
            self.clone(clone)
        clone.assert_not_called()
        self.assertFalse((self.owned.path / "sdk").exists())

    def test_every_required_emulator_file_must_exist_before_clone(self):
        clone = Mock()
        for name in control.EMULATOR_HASHES:
            path = self.emulator / name
            payload, mode = path.read_bytes(), stat.S_IMODE(path.stat().st_mode)
            path.unlink()
            try:
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, "metadata is absent"):
                    self.clone(clone)
            finally:
                path.write_bytes(payload)
                path.chmod(mode)
        clone.assert_not_called()
        self.assertFalse((self.owned.path / "sdk").exists())

    def test_every_required_emulator_file_digest_is_checked(self):
        for index, name in enumerate(control.EMULATOR_HASHES):
            self.owned = FakeOwned(self.base / ("owned-pin-" + str(index)))
            path = self.emulator / name
            payload = path.read_bytes()
            path.write_bytes(b"wrong authentic-package fixture digest")
            try:
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, "metadata differs"):
                    self.clone()
            finally:
                path.write_bytes(payload)

    def test_required_metadata_cannot_be_a_directory(self):
        (self.emulator / "package.xml").unlink()
        (self.emulator / "package.xml").mkdir()
        clone = Mock()
        with self.assertRaisesRegex(ValueError, "metadata is absent or not regular"):
            self.clone(clone)
        clone.assert_not_called()

    def test_both_required_emulator_tools_need_owner_execute_permission(self):
        clone = Mock()
        for name in control.EMULATOR_EXECUTABLES:
            tool = self.emulator / name
            tool.chmod(0o644)
            try:
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, "lacks owner execute"):
                    self.clone(clone)
            finally:
                tool.chmod(0o755)
        clone.assert_not_called()

    def test_permissions_preserve_safe_rwx_and_strip_setid_sticky_and_group_write(self):
        tool = self.emulator / "emulator"
        extra = self.emulator / "NOTICE.txt"
        tool.chmod(0o7777)
        extra.chmod(0o666)
        source_mode = stat.S_IMODE(tool.stat().st_mode)
        result = self.clone()
        target = Path(result["owned_emulator"])
        self.assertEqual(0o755, stat.S_IMODE((target / "emulator").stat().st_mode))
        self.assertEqual(0o644, stat.S_IMODE((target / "NOTICE.txt").stat().st_mode))
        for name in control.EMULATOR_EXECUTABLES:
            self.assertTrue((target / name).stat().st_mode & stat.S_IXUSR)
        self.assertEqual(source_mode, stat.S_IMODE(tool.stat().st_mode))
        for package in result["packages"]:
            for item in package["files"]:
                mode = stat.S_IMODE((Path(package["destination"]) / item["path"]).stat().st_mode)
                self.assertEqual(0, mode & 0o7022)
                self.assertEqual(format(mode, "04o"), item["mode"])

    def test_emulator_symlinks_hardlinks_and_special_files_are_not_cloned(self):
        clone = Mock()
        unwanted = self.emulator / "unwanted"
        unwanted.symlink_to(self.base)
        with self.assertRaises(ValueError):
            self.clone(clone)
        unwanted.unlink()
        os.link(self.emulator / "emulator", unwanted)
        with self.assertRaises(ValueError):
            self.clone(clone)
        unwanted.unlink()
        os.mkfifo(unwanted)
        with self.assertRaises(ValueError):
            self.clone(clone)
        clone.assert_not_called()

    def test_emulator_package_root_cannot_be_a_symlink(self):
        real = self.sdk / "unselected-emulator"
        self.emulator.rename(real)
        self.emulator.symlink_to(real)
        clone = Mock()
        with self.assertRaisesRegex(ValueError, "path contains symlinks"):
            self.clone(clone)
        clone.assert_not_called()

    def test_cancel_during_emulator_clone_preserves_sources_and_retains_owned_partial(self):
        before = [control._inventory(path) for path in (self.image, self.emulator)]
        image_files = sum(stat.S_ISREG(info[2]) for info in before[0][0].values())
        calls = []
        def clone(source_fd, parent_fd, name):
            self.fake_clone(source_fd, parent_fd, name)
            calls.append(name)
        def checkpoint():
            if len(calls) > image_files:
                raise InterruptedError("synthetic cancellation after an emulator file clone")
        with self.assertRaises(InterruptedError):
            self.clone(clone, checkpoint)
        self.assertEqual(image_files + 1, len(calls))
        self.assertEqual(before, [control._inventory(path) for path in (self.image, self.emulator)])
        self.assertTrue((self.owned.path / "sdk/emulator").is_dir())
        # The owning runner's finally removes this partial tree; no source deletion.

    def test_emulator_source_mutation_during_clone_fails(self):
        def mutate(source_fd, parent_fd, name):
            self.fake_clone(source_fd, parent_fd, name)
            if name == "emulator":
                (self.emulator / "source.properties").write_bytes(b"mutated synthetic source")
        with self.assertRaises(ValueError):
            self.clone(mutate)

    def test_completed_image_source_is_rechecked_after_emulator_clone(self):
        def mutate(source_fd, parent_fd, name):
            self.fake_clone(source_fd, parent_fd, name)
            if name == "emulator":
                (self.image / "source.properties").write_bytes(b"mutated completed first package")
        with self.assertRaisesRegex(ValueError, "changed during selected SDK clone"):
            self.clone(mutate)

    def test_native_failure_in_second_package_has_no_copy_fallback(self):
        before = [control._inventory(path) for path in (self.image, self.emulator)]
        image_files = sum(stat.S_ISREG(info[2]) for info in before[0][0].values())
        calls = []
        def fail_second(source_fd, parent_fd, name):
            calls.append(name)
            if len(calls) > image_files:
                raise OSError(errno.ENOTSUP, "synthetic emulator CoW failure")
            self.fake_clone(source_fd, parent_fd, name)
        with self.assertRaises(OSError):
            self.clone(fail_second)
        self.assertEqual(image_files + 1, len(calls))
        self.assertEqual(before, [control._inventory(path) for path in (self.image, self.emulator)])

    def test_real_package_pins_and_shared_resource_limits_are_explicit(self):
        self.assertEqual({
            "package.xml": "5c06a9b6ea0d9f436d351e8a9b2420619082b30222d650643a572ca5dd6ab7c8",
            "source.properties": "a0cdf354b08f5d21b426dd6d8d58a3c6e5d1527d84740bba1df88b4249ca32e4",
            "lib/hardware-properties.ini": "13a89cc7cfa84c5fe48cd7eb8d296bca321dcd0d90f7bcb751efd963ae2c653d",
            "emulator": "c12c8d72da94cb5abd7f9d2578923d05ff94ea827f0ee593c6e563c96edc0d42",
            "mksdcard": "76352e7a02e3e1cd3b577029d0fd59e312e3931aa6774776a941fe4a979c8eca",
        }, self.original_emulator_pins)
        self.assertEqual(512, control.MAX_ENTRIES)
        self.assertEqual(6 * 1024 ** 3, control.MAX_IMAGE_BYTES)
        self.assertEqual(Path("emulator"), control.EMULATOR_RELATIVE)


if __name__ == "__main__":
    unittest.main()
