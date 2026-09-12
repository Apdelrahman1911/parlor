"""Unsigned package checks: synthetic fixtures, never Store-signing evidence."""
import io
import json
from pathlib import Path
import shutil
import struct
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/verification"))
import android_release_artifacts as artifacts


def manifest():
    permissions = "".join('<uses-permission android:name="' + name + '"/>' for name in (
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "com.parlor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
    return ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.parlor.app" '
            'android:versionName="1.2.3" android:versionCode="42">' + permissions +
            '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>'
            '<application android:name="com.parlor.app.ParlorApplication" android:allowBackup="false" '
            'android:usesCleartextTraffic="false"><activity android:name="com.parlor.app.MainActivity" '
            'android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/>'
            '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>'
            '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true" '
            'android:permission="android.permission.DUMP"/></application></manifest>').encode()


def elf(abi):
    header = bytearray(112)
    header[:7] = b"\x7fELF" + bytes([artifacts.NATIVE[abi][0], 1, 1])
    struct.pack_into("<HH", header, 16, 3, artifacts.NATIVE[abi][1])
    return bytes(header)


class AndroidReleaseArtifactsTest(unittest.TestCase):
    def test_manifest_validates_actual_required_policy(self):
        value = artifacts.inspect_manifest(manifest(), "1.2.3", 42)
        self.assertEqual("com.parlor.app", value["application_id"])
        self.assertEqual(5, len(value["permissions"]))

    def test_manifest_rejects_identity_version_sdk_and_privacy_drift(self):
        for before, after in ((b'package="com.parlor.app"', b'package="com.parlor.app.debug"'),
                              (b'versionCode="42"', b'versionCode="43"'),
                              (b'versionName="1.2.3"', b'versionName="1.2.4"'),
                              (b'minSdkVersion="26"', b'minSdkVersion="25"'),
                              (b'targetSdkVersion="36"', b'targetSdkVersion="35"'),
                              (b'allowBackup="false"', b'allowBackup="true"'),
                              (b'usesCleartextTraffic="false"', b'usesCleartextTraffic="true"'),
                              (b'<application ', b'<application android:debuggable="true" '),
                              (b'<application ', b'<application android:testOnly="true" ')):
            with self.subTest(after=after), self.assertRaises(ValueError):
                artifacts.inspect_manifest(manifest().replace(before, after), "1.2.3", 42)

    def test_manifest_rejects_new_components_permissions_and_launcher(self):
        for before, after in ((b'</application>', b'<service android:name="extra" android:exported="true"/></application>'),
                              (b'</manifest>', b'<uses-permission android:name="android.permission.CAMERA"/></manifest>'),
                              (b'</manifest>', b'<uses-permission-sdk-23 android:name="android.permission.CAMERA"/></manifest>'),
                              (b'android.intent.category.LAUNCHER', b'android.intent.category.DEFAULT')):
            with self.subTest(after=after), self.assertRaises(ValueError):
                artifacts.inspect_manifest(manifest().replace(before, after), "1.2.3", 42)

    def test_manifest_rejects_dtd_and_overlarge_output(self):
        for raw in (b'<!DOCTYPE manifest>' + manifest(), b' ' * (1024 * 1024 + 1)):
            with self.assertRaises(ValueError):
                artifacts.inspect_manifest(raw, "1.2.3", 42)

    def test_each_native_abi_has_the_right_elf_identity(self):
        for abi in artifacts.NATIVE:
            self.assertEqual(abi, artifacts.inspect_elf(elf(abi), f"base/lib/{abi}/libandroidx.graphics.path.so")["abi"])

    def test_native_rejects_wrong_cpu_type_location_and_extra_library(self):
        for path in ("base/lib/x86/libandroidx.graphics.path.so", "base/assets/native.so",
                     "base/lib/arm64-v8a/libextra.so"):
            with self.assertRaises(ValueError):
                artifacts.inspect_elf(elf("arm64-v8a"), path)
        for header in (b'\x7fELF', elf("arm64-v8a").replace(b'\x03\x00', b'\x02\x00', 1)):
            with self.assertRaises(ValueError):
                artifacts.inspect_elf(header, "base/lib/arm64-v8a/libandroidx.graphics.path.so")

    def test_dex_counts_every_defined_descriptor(self):
        value = artifacts.inspect_dex_output(io.StringIO(
            "Class #0\n  Class descriptor  : 'Lcom/parlor/app/MainActivity;'\n"
            "  Class descriptor  : 'La/a;'\n"), 2)
        self.assertEqual(2, value["class_definitions"])
        self.assertTrue(value["contains_main_activity"])

    def test_dex_rejects_missing_duplicate_and_forbidden_classes(self):
        descriptor = "  Class descriptor  : 'La/a;'\n"
        for text, count in ((descriptor, 2), (descriptor * 2, 2),
                            ("  Class descriptor  : 'Lcom/parlor/networking/testing/InMemoryRoomBus;'\n", 1),
                            ("  Class descriptor  : 'Lio/ktor/client/engine/mock/MockEngine;'\n", 1)):
            with self.assertRaises(ValueError):
                artifacts.inspect_dex_output(io.StringIO(text), count)

    def test_mapping_checks_original_names_even_when_obfuscated(self):
        valid = "# compiler: R8\ncom.parlor.app.MainActivity -> com.parlor.app.MainActivity:\n    void onCreate() -> a\n"
        self.assertEqual(1, artifacts.inspect_mapping(io.StringIO(valid))["class_mappings"])
        for forbidden in artifacts.FORBIDDEN:
            with self.subTest(forbidden=forbidden), self.assertRaises(ValueError):
                artifacts.inspect_mapping(io.StringIO(valid + forbidden + "Example -> x:\n"))

    def test_mapping_rejects_empty_unrecognized_and_unbounded_lines(self):
        for data in ("", "malformed class\n", "a" * 65537):
            with self.assertRaises(ValueError):
                artifacts.inspect_mapping(io.StringIO(data))

    def test_regular_input_rejects_symlink_and_empty_file(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            target = root / "target"
            target.write_bytes(b"fixture")
            link = root / "link"
            link.symlink_to(target)
            with self.assertRaises(ValueError):
                artifacts.regular(link, 100)
            target.write_bytes(b"")
            with self.assertRaises(ValueError):
                artifacts.regular(target, 100)

    def test_owned_tool_failure_and_timeout_are_reaped(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            with self.assertRaisesRegex(ValueError, "failed"):
                artifacts.run_tool([sys.executable, "-c", "raise SystemExit(7)"], root / "failure")
            with self.assertRaisesRegex(ValueError, "timed out"):
                artifacts.run_tool([sys.executable, "-c", "import time;time.sleep(20)"], root / "timeout", timeout=0.1)

    def test_immediate_post_spawn_failure_and_cancellation_reap_exact_owned_child(self):
        for failure in (RuntimeError("synthetic clock failure"), KeyboardInterrupt("synthetic cancel")):
            with self.subTest(failure=type(failure).__name__), TemporaryDirectory() as temporary:
                process = Mock()
                process.poll.return_value = None
                process.wait.return_value = 0
                with patch.object(artifacts.subprocess, "Popen", return_value=process), \
                     patch.object(artifacts.time, "monotonic", side_effect=failure), \
                     self.assertRaises(type(failure)):
                    artifacts.run_tool(["synthetic-tool"], Path(temporary) / "output")
                process.terminate.assert_called_once_with()
                process.wait.assert_called_once_with(timeout=5)
                process.kill.assert_not_called()

    def test_stubborn_owned_child_is_killed_then_reaped_after_cancel(self):
        with TemporaryDirectory() as temporary:
            process = Mock()
            process.poll.return_value = None
            process.wait.side_effect = [artifacts.subprocess.TimeoutExpired("owned", 5), 0]
            with patch.object(artifacts.subprocess, "Popen", return_value=process), \
                 patch.object(artifacts.time, "monotonic", side_effect=KeyboardInterrupt()), \
                 self.assertRaises(KeyboardInterrupt):
                artifacts.run_tool(["synthetic-tool"], Path(temporary) / "output")
            process.terminate.assert_called_once_with()
            process.kill.assert_called_once_with()
            self.assertEqual(2, process.wait.call_count)

    def test_tool_output_bound_is_enforced(self):
        with TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "limit"):
                artifacts.run_tool([sys.executable, "-c", "print('x'*1000)"], Path(temporary) / "output", limit=10)

    def test_integrated_fixture_uses_real_notice_resource_checks_and_independent_tools(self):
        self._integrated()

    def test_integrated_fixture_rejects_changed_story_and_unsigned_policy(self):
        for mutation in ("story", "signature", "mapping", "native"):
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                self._integrated(mutation)

    def _integrated(self, mutation=None):
        with TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            for relative in ("config/third-party-notices.json", "gradle/libs.versions.toml", "composeApp/build.gradle.kts"):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / relative, target)
            for relative in (artifacts.notices.RESOURCE_DIRECTORY,
                             "game-modes/whodunit/src/commonMain/composeResources/files/cases",
                             "shared/design-system/src/commonMain/composeResources/font"):
                shutil.copytree(ROOT / relative, root / relative)
            bundletool = root / "bundletool.jar"
            bundletool.write_bytes(b"synthetic tool; never executed")
            policy = {"tools": {"bundletool": {"sha256": artifacts.sha256(bundletool)}}}
            (root / "config/release-policy.json").write_text(json.dumps(policy))
            (root / "config/parlor-version.xcconfig").write_text("PARLOR_VERSION_NAME = 1.2.3\nPARLOR_BUILD_NUMBER = 42\n")
            dexdump = root / "36.0.0/dexdump"
            dexdump.parent.mkdir()
            dexdump.write_bytes(b"synthetic tool; never executed")
            dexdump.chmod(0o755)
            (dexdump.parent / "source.properties").write_text("Pkg.Revision=36.0.0\n")
            entries = {name: b"synthetic protobuf/metadata" for name in artifacts.METADATA}
            entries["BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"] = (
                b"com.parlor.app.MainActivity -> com.parlor.app.MainActivity:\n" +
                (b"org.junit.Test -> a:\n" if mutation == "mapping" else b""))
            header = bytearray(112)
            header[:8] = b"dex\n039\0"
            struct.pack_into("<I", header, 96, 1)
            entries["base/dex/classes.dex"] = bytes(header)
            for abi in artifacts.NATIVE:
                entries[f"base/lib/{abi}/libandroidx.graphics.path.so"] = elf(abi)
            if mutation == "native":
                entries["base/lib/arm64-v8a/libextra.so"] = elf("arm64-v8a")
            for source_dir, prefix in (("game-modes/whodunit/src/commonMain/composeResources/files/cases",
                                       "base/assets/composeResources/com.parlor.games.whodunit.resources/files/cases"),
                                      ("shared/design-system/src/commonMain/composeResources/font",
                                       "base/assets/composeResources/parlor.shared.design_system.generated.resources/font"),
                                      (artifacts.notices.RESOURCE_DIRECTORY, artifacts.notices.AAB_PREFIX)):
                for source in (root / source_dir).iterdir():
                    entries[prefix + "/" + source.name] = source.read_bytes()
            if mutation == "story":
                entries[next(name for name in entries if "/files/cases/" in name)] = b"changed"
            if mutation == "signature":
                entries["META-INF/FIXTURE.RSA"] = b"not a real signature"
            aab = root / "fixture.aab"
            with zipfile.ZipFile(aab, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for name, raw in entries.items():
                    archive.writestr(name, raw)

            calls = []
            def synthetic_tool(command, output, *args, **kwargs):
                calls.append(command)
                if "manifest" in command:
                    output.write_bytes(manifest())
                elif command[0] == str(dexdump):
                    output.write_text("  Class descriptor  : 'Lcom/parlor/app/MainActivity;'\n")
                else:
                    output.write_text("synthetic validate result")

            with patch.object(artifacts, "run_tool", side_effect=synthetic_tool):
                value = artifacts.inspect(root, aab, bundletool, dexdump)
            self.assertEqual("PASS", value["status"])
            self.assertEqual(26, value["notices"]["package"]["resource_count"])
            self.assertEqual(4, len(value["native_images"]))
            self.assertEqual(9, len(value["raw_resources"]))
            self.assertEqual(3, len(calls))
            self.assertNotIn("-i", calls[-1])  # Never ignore a DEX checksum failure.
            self.assertNotIn("-j", calls[-1])  # Never disable DEX verification.
