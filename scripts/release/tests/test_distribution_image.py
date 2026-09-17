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
    def test_linux_materializes_only_internal_jdk_legal_aliases_before_installer_comparison(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "app"
            legal = app / "lib/runtime/legal"
            (legal / "java.base").mkdir(parents=True)
            (legal / "java.desktop").mkdir()
            source = legal / "java.base/LICENSE"
            source.write_bytes(b"synthetic vendor license")
            alias = legal / "java.desktop/LICENSE"
            alias.symlink_to("../java.base/LICENSE")
            shutil.copytree(app, root / "before", symlinks=False)
            with self.assertRaisesRegex(RuntimeError, "changed.*LICENSE"):
                image.require_same_image(app, root / "before")
            records = image.materialize_linux_runtime_notices(app, "linux-x64")
            self.assertEqual(len(records), 1)
            self.assertEqual(records[0]["original_link"], "../java.base/LICENSE")
            self.assertFalse(alias.is_symlink())
            self.assertEqual(alias.read_bytes(), source.read_bytes())
            self.assertEqual(source.read_bytes(), b"synthetic vendor license")
            image.require_same_image(app, root / "before")

    def test_runtime_notice_alias_cannot_read_other_application_files_or_escape_image(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "app"
            legal = app / "lib/runtime/legal"
            legal.mkdir(parents=True)
            target = app / "other-file"
            target.write_bytes(b"must not be read as a notice")
            alias = legal / "LICENSE"
            alias.symlink_to(target)
            with self.assertRaisesRegex(RuntimeError, "escapes legal"):
                image.materialize_linux_runtime_notices(app, "linux-x64")
            self.assertTrue(alias.is_symlink())
            self.assertEqual(image.materialize_linux_runtime_notices(app, "macos-arm64"), [])

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
                if name == image.NATIVE_NAMES[platform]:
                    (directory / name).write_bytes(content)
            if platform == "windows-x64":
                data = b"synthetic ICU data"
                archive.writestr("icudtl.dat", data)
                (directory / "icudtl.dat").write_bytes(data)
            if extra:
                archive.writestr(extra, "unexpected fixture")
        shutil.copyfile(original, jar)
        return app, directory, original, jar

    def test_windows_icu_data_must_remain_external_and_byte_identical_to_pinned_resource(self):
        with tempfile.TemporaryDirectory() as temporary:
            app, directory, original, jar = self.fixture(Path(temporary), "windows-x64")
            with patch.object(image, "verified_skiko_jar", return_value=original):
                receipt = image.prepare_skiko(app, "windows-x64")
                self.assertEqual(receipt["auxiliary_sha256"], {"icudtl.dat": hashlib.sha256(b"synthetic ICU data").hexdigest()})
                with zipfile.ZipFile(jar) as archive:
                    self.assertNotIn("icudtl.dat", archive.namelist())
                (directory / "icudtl.dat").write_bytes(b"changed auxiliary data")
                with self.assertRaisesRegex(RuntimeError, "auxiliary data differs"):
                    image.prepare_skiko(app, "windows-x64")

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

    def test_invalid_generated_native_bytes_are_never_rehashed_or_repaired(self):
        with tempfile.TemporaryDirectory() as temporary:
            app, directory, original, jar = self.fixture(Path(temporary))
            name = image.NATIVE_NAMES["macos-arm64"]
            (directory / name).write_bytes(b"compose-transformed-bytes")
            (directory / (name + ".sha256")).write_text("0" * 64)
            with patch.object(image, "verified_skiko_jar", return_value=original), \
                    patch.object(image, "run", side_effect=RuntimeError("invalid native signature")):
                with self.assertRaisesRegex(RuntimeError, "invalid native signature"):
                    image.prepare_skiko(app, "macos-arm64")
            self.assertEqual((directory / name).read_bytes(), b"compose-transformed-bytes")
            self.assertEqual((directory / (name + ".sha256")).read_text(), "0" * 64)

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


class NativeEquivalenceTest(unittest.TestCase):
    @staticmethod
    def native_tool(command, **kwargs):
        path = Path(command[-1])
        signature, _, payload = path.read_bytes().partition(b"|")
        if "--display" in command:
            if signature == b"UNSIGNED":
                return f"{path}: code object is not signed at all\n"
            return "CodeDirectory flags=0x0(none)" if signature == b"VENDOR" else "CodeDirectory flags=0x2(adhoc)"
        if "--verify" in command and signature == b"INVALID":
            raise RuntimeError("invalid native signature")
        if "--remove-signature" in command:
            path.write_bytes(b"UNSIGNED|" + payload)
        if "--sign" in command:
            path.write_bytes(b"ADHOC|" + payload)
        return ""

    def test_signature_only_transformation_is_proven_on_disposable_copies_and_inputs_are_preserved(self):
        for platform, signature in (("macos-arm64", b"ADHOC"), ("macos-x64", b"UNSIGNED")):
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as temporary:
                native = Path(temporary) / "native.dylib"
                original = b"VENDOR|reviewed-native-payload"
                generated = signature + b"|reviewed-native-payload"
                native.write_bytes(generated)
                with patch.object(image, "run", side_effect=self.native_tool) as run:
                    receipt = image.verify_compose_native(native, original, platform)
                self.assertEqual(receipt["method"], "disposable-signature-roundtrip")
                self.assertEqual(receipt["original_sha256"], hashlib.sha256(original).hexdigest())
                self.assertEqual(native.read_bytes(), generated)
                mutations = [Path(call.args[0][-1]) for call in run.call_args_list
                             if "--remove-signature" in call.args[0] or "--sign" in call.args[0]]
                self.assertTrue(mutations)
                self.assertNotIn(native, mutations)
                self.assertTrue(all(not path.exists() for path in mutations), "Verification copies must always be retired")

    def test_valid_vendor_bytes_are_preserved_without_any_codesign_mutation(self):
        with tempfile.TemporaryDirectory() as temporary:
            native = Path(temporary) / "native.dylib"
            original = b"VENDOR|reviewed-native-payload"
            native.write_bytes(original)
            with patch.object(image, "run") as run:
                receipt = image.verify_compose_native(native, original, "macos-arm64")
            self.assertEqual(receipt["method"], "exact-pinned-bytes")
            run.assert_not_called()
            self.assertEqual(native.read_bytes(), original)

    def test_changed_code_invalid_signatures_foreign_signers_and_arm_unsigned_are_rejected_without_repair(self):
        examples = [
            ("macos-arm64", b"ADHOC|changed-code", "executable payload differs"),
            ("macos-arm64", b"INVALID|reviewed-native-payload", "invalid native signature"),
            ("macos-arm64", b"VENDOR|changed-vendor", "non-ad-hoc"),
            ("macos-arm64", b"UNSIGNED|reviewed-native-payload", "Only Intel"),
            ("windows-x64", b"ADHOC|reviewed-native-payload", "differs from the pinned JAR"),
        ]
        for platform, generated, error in examples:
            with self.subTest(platform=platform, error=error), tempfile.TemporaryDirectory() as temporary:
                native = Path(temporary) / "native.dylib"
                native.write_bytes(generated)
                with patch.object(image, "run", side_effect=self.native_tool) as run, self.assertRaisesRegex(RuntimeError, error):
                    image.verify_compose_native(native, b"VENDOR|reviewed-native-payload", platform)
                self.assertEqual(native.read_bytes(), generated)
                scratch = [Path(call.args[0][-1]) for call in run.call_args_list if Path(call.args[0][-1]) != native]
                self.assertTrue(all(not path.exists() for path in scratch), "Failure must retire disposable copies too")


class DistributionMetadataTest(unittest.TestCase):
    def test_mac_only_fresh_unsigned_intel_or_valid_adhoc_image_can_be_prepared(self):
        with tempfile.TemporaryDirectory() as temporary:
            app = Path(temporary) / "Parlor.app"
            (app / "Contents").mkdir(parents=True)
            unsigned = f"{app}: code object is not signed at all\n"
            with patch.object(packages, "run", return_value=unsigned) as run:
                packages.require_mac_rehearsal_image(app, "macos-x64")
                self.assertEqual(run.call_count, 1)
                with self.assertRaisesRegex(RuntimeError, "fresh unsigned Intel"):
                    packages.require_mac_rehearsal_image(app, "macos-arm64")
                seal = app / "Contents/_CodeSignature"
                seal.mkdir()
                packages.require_mac_rehearsal_image(app, "macos-x64")
                (seal / "CodeResources").write_bytes(b"must not be repaired")
                with self.assertRaisesRegex(RuntimeError, "fresh unsigned Intel"):
                    packages.require_mac_rehearsal_image(app, "macos-x64")
                (seal / "CodeResources").unlink()
                seal.rmdir()
                seal.symlink_to(app / "missing-seal")
                with self.assertRaisesRegex(RuntimeError, "fresh unsigned Intel"):
                    packages.require_mac_rehearsal_image(app, "macos-x64")
                seal.unlink()
            for platform in ("macos-x64", "macos-arm64"):
                with patch.object(packages, "run", side_effect=["CodeDirectory flags=0x10002(adhoc,runtime)", ""]) as run:
                    packages.require_mac_rehearsal_image(app, platform)
                    self.assertIn("--verify", run.call_args_list[1].args[0])
                with patch.object(packages, "run", side_effect=["CodeDirectory flags=0x10000(runtime)", ""]):
                    with self.assertRaisesRegex(RuntimeError, "not a signed release"):
                        packages.require_mac_rehearsal_image(app, platform)
                with patch.object(packages, "run", side_effect=["CodeDirectory flags=0x10002(adhoc,runtime)", RuntimeError("invalid seal")]):
                    with self.assertRaisesRegex(RuntimeError, "invalid seal"):
                        packages.require_mac_rehearsal_image(app, platform)

    def test_mac_packaging_always_retires_the_scoped_stage_and_applications_alias(self):
        for fail in (False, True):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                app, work = root / "Parlor.app", root / "work"
                app.mkdir()
                (app / "payload").write_text("synthetic-app")
                work.mkdir()
                stages = []

                def native(command, **kwargs):
                    if "-srcfolder" in command:
                        stage = Path(command[command.index("-srcfolder") + 1])
                        self.assertEqual((stage / "Parlor.app/payload").read_text(), "synthetic-app")
                        self.assertTrue((stage / "Applications").is_symlink())
                        stages.append(stage)
                        if fail:
                            raise RuntimeError("synthetic native packaging failure")
                        Path(command[-1]).write_bytes(b"synthetic-dmg")

                with patch.object(packages, "image_path", return_value=app), patch.object(packages, "WORK", work), \
                        patch.object(packages, "OUT", root), patch.object(packages, "run", side_effect=native):
                    if fail:
                        with self.assertRaisesRegex(RuntimeError, "synthetic native packaging failure"):
                            packages.package("macos-arm64")
                    else:
                        self.assertEqual(packages.package("macos-arm64").read_bytes(), b"synthetic-dmg")
                self.assertEqual(len(stages), 1)
                self.assertFalse(stages[0].exists())
                self.assertEqual((app / "payload").read_text(), "synthetic-app")

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
