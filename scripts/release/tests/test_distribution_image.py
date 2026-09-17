from __future__ import annotations

import hashlib
from pathlib import Path
import plistlib
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.release import distribution_image as image
from scripts.release import desktop_package as packages


class DistributionImageTest(unittest.TestCase):
    def fixture(self, root, platform="macos-arm64", extra=None):
        app = root / "App"
        directory = image.app_directory(app, platform)
        directory.mkdir(parents=True)
        (directory / "Parlor.cfg").write_text("[Application]\napp.mainclass=fixture\n[JavaOptions]\njava-options=-Dfixture=yes\n")
        jar = directory / f"skiko-awt-runtime-{platform}-0.9.37.4-packaged.jar"
        names = [image.NATIVE_NAMES[platform]]
        if platform.startswith("macos-"):
            names = [image.NATIVE_NAMES["macos-arm64"], image.NATIVE_NAMES["macos-x64"]]
        original = root / "original.jar"
        with zipfile.ZipFile(original, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Synthetic test fixture")
            for name in names:
                content = ("fixture-" + name).encode()
                archive.writestr(name, content)
                archive.writestr(name + ".sha256", hashlib.sha256(content).hexdigest())
            if extra:
                archive.writestr(extra, "unexpected fixture")
        shutil.copyfile(original, jar)
        return app, directory, original, jar

    def test_only_selected_native_is_extracted_from_pinned_bytes_and_no_native_remains_in_jars(self):
        for platform in image.NATIVE_NAMES:
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                app, directory, original, jar = self.fixture(root, platform)
                before = original.read_bytes()
                with patch.object(image, "verified_skiko_jar", return_value=original):
                    receipt = image.prepare_skiko(app, platform)
                native = directory / image.NATIVE_NAMES[platform]
                self.assertEqual(receipt["native_sha256"], hashlib.sha256(native.read_bytes()).hexdigest())
                self.assertEqual(before, original.read_bytes(), "Dependency cache must never be rewritten")
                with zipfile.ZipFile(jar) as archive:
                    self.assertEqual(archive.namelist(), ["META-INF/MANIFEST.MF"])
                self.assertIn("java-options=-Dskiko.library.path=$APPDIR", (directory / "Parlor.cfg").read_text())
                image.verify_no_embedded_native(app)

    def test_compose_changed_native_bytes_are_replaced_with_pinned_original_not_just_rehashed(self):
        with tempfile.TemporaryDirectory() as temporary:
            app, directory, original, jar = self.fixture(Path(temporary))
            name = image.NATIVE_NAMES["macos-arm64"]
            (directory / name).write_bytes(b"compose-transformed-bytes")
            (directory / (name + ".sha256")).write_text("0" * 64)
            with patch.object(image, "verified_skiko_jar", return_value=original):
                image.prepare_skiko(app, "macos-arm64")
            with zipfile.ZipFile(original) as archive:
                self.assertEqual((directory / name).read_bytes(), archive.read(name))

    def test_signed_or_unknown_native_jars_are_never_normalized(self):
        for extra in ("META-INF/PUBLISHER.RSA", "surprise.dylib"):
            with tempfile.TemporaryDirectory() as temporary:
                app, directory, original, jar = self.fixture(Path(temporary), extra=extra)
                with patch.object(image, "verified_skiko_jar", return_value=original), self.assertRaises(RuntimeError):
                    image.prepare_skiko(app, "macos-arm64")

    def test_non_native_vendor_resource_mutation_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            app, directory, original, jar = self.fixture(Path(temporary))
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/MANIFEST.MF", "tampered")
            with patch.object(image, "verified_skiko_jar", return_value=original), self.assertRaisesRegex(RuntimeError, "non-native vendor"):
                image.prepare_skiko(app, "macos-arm64")

    def test_installer_must_preserve_every_resource_not_only_launcher(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, installed = root / "source", root / "installed"
            source.mkdir()
            (source / "launcher").write_bytes(b"fixture")
            (source / "private-policy").write_bytes(b"fixture-policy")
            (source / ".jpackage.xml").write_bytes(b"noninstalled-build-recipe")
            shutil.copytree(source, installed)
            (installed / ".jpackage.xml").unlink()
            image.require_same_image(source, installed)
            (installed / "private-policy").write_bytes(b"tampered-policy")
            with self.assertRaisesRegex(RuntimeError, "Installed payload"):
                image.require_same_image(source, installed)

    def test_image_symlinks_cannot_escape_the_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "app"
            app.mkdir()
            (app / "notice").write_bytes(b"fixture")
            (app / "legal-alias").symlink_to("notice")
            image.inventory(app)
            (root / "outside").write_bytes(b"outside")
            (app / "escape").symlink_to("../outside")
            with self.assertRaisesRegex(RuntimeError, "escapes"):
                image.inventory(app)


class DistributionMetadataTest(unittest.TestCase):
    def test_mac_version_build_and_minimum_os_are_verified_not_rewritten(self):
        expected = {"CFBundleIdentifier": "me.parlor.desktop", "CFBundleShortVersionString": packages.version()["name"],
                    "CFBundleVersion": str(packages.version()["build"]), "LSMinimumSystemVersion": "11.0",
                    "NSBonjourServices": ["_p2pkit2._tcp"], "NSLocalNetworkUsageDescription": "Synthetic LAN rationale"}
        with tempfile.TemporaryDirectory() as temporary:
            app = Path(temporary) / "Parlor.app"
            (app / "Contents").mkdir(parents=True)
            plist = app / "Contents/Info.plist"
            plist.write_bytes(plistlib.dumps(expected))
            packages.inspect_mac_metadata(app)
            for key, value in (("CFBundleIdentifier", "wrong"), ("CFBundleShortVersionString", "0.0.0"),
                               ("CFBundleVersion", "0"), ("LSMinimumSystemVersion", "10.13"), ("NSBonjourServices", [])):
                plist.write_bytes(plistlib.dumps({**expected, key: value}))
                before = plist.read_bytes()
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    packages.inspect_mac_metadata(app)
                self.assertEqual(plist.read_bytes(), before)

    def test_msi_identity_version_and_upgrade_code_must_match(self):
        expected = {"ProductName": "Parlor", "ProductVersion": packages.version()["name"],
                    "UpgradeCode": "{" + packages.WINDOWS_UPGRADE_UUID.upper() + "}"}
        with patch.object(packages, "windows_installer_properties", return_value=expected) as properties:
            packages.inspect_windows_metadata(Path("synthetic.msi"))
            for key in expected:
                properties.return_value = {**expected, key: "incorrect"}
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    packages.inspect_windows_metadata(Path("synthetic.msi"))

    def test_debian_version_contains_the_source_build_revision(self):
        with patch.object(packages, "run", side_effect=["parlor", "amd64", packages.version()["name"]]):
            with self.assertRaisesRegex(RuntimeError, "Debian version/build differs"):
                packages.inspect_installer("linux-x64", Path("synthetic.deb"))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app, work = root / "app", root / "work"
            app.mkdir()
            work.mkdir()

            def package(command, **kwargs):
                self.assertIn("--linux-app-release", command)
                self.assertEqual(command[command.index("--linux-app-release") + 1], str(packages.version()["build"]))
                (work / "installer/test.deb").write_bytes(b"synthetic-deb")

            with patch.object(packages, "image_path", return_value=app), patch.object(packages, "WORK", work), \
                    patch.object(packages, "OUT", root), patch.object(packages, "java_tool", return_value="jpackage"), \
                    patch.object(packages, "run", side_effect=package):
                self.assertEqual(packages.package("linux-x64").read_bytes(), b"synthetic-deb")


if __name__ == "__main__":
    unittest.main()
