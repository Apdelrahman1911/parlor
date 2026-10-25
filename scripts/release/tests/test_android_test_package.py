from __future__ import annotations

import json
import os
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.release import android_test_package as package
from scripts.release import android_test_smoke as smoke
from scripts.release.tests.test_android_release_artifacts import manifest


class AndroidPublicTestPackageTest(unittest.TestCase):
    def test_test_identity_is_explicit_and_never_accepted_by_store_default(self):
        raw = manifest().replace(b"me.parlor.android", b"me.parlor.android.test")
        inspect = package.packages.inspect_manifest
        self.assertEqual(inspect(raw, "1.2.3", 42, application_id=package.APPLICATION_ID)["application_id"], package.APPLICATION_ID)
        with self.assertRaisesRegex(ValueError, "identity"):
            inspect(raw, "1.2.3", 42)
        with self.assertRaisesRegex(ValueError, "identity"):
            inspect(manifest(), "1.2.3", 42, application_id=package.APPLICATION_ID)
        for identity in ("me.parlor.android.debug", "attacker.copy", ""):
            with self.subTest(identity=identity), self.assertRaises(ValueError):
                inspect(raw, "1.2.3", 42, application_id=identity)

    def test_public_test_keeps_nondebuggable_normal_install_and_privacy_policy(self):
        raw = manifest().replace(b"me.parlor.android", b"me.parlor.android.test")
        changes = (
            (b'<application ', b'<application android:debuggable="true" '),
            (b'<application ', b'<application android:testOnly="true" '),
            (b'android:allowBackup="false"', b'android:allowBackup="true"'),
            (b'android:usesCleartextTraffic="false"', b'android:usesCleartextTraffic="true"'),
            (b'android.permission.INTERNET', b'android.permission.CAMERA'),
        )
        for before, after in changes:
            with self.subTest(after=after), self.assertRaises(ValueError):
                package.packages.inspect_manifest(raw.replace(before, after), "1.2.3", 42, application_id=package.APPLICATION_ID)

    def test_final_apk_requires_exact_disposable_signer_v2_v3_and_alignment(self):
        pin = "c" * 64
        good = (f"Signer #1 certificate SHA-256 digest: {pin}\n"
                "Verified using v2 scheme (APK Signature Scheme v2): true\n"
                "Verified using v3 scheme (APK Signature Scheme v3): true\n")
        with patch.object(package, "inspect"), patch.object(package.packages, "android_tool", side_effect=lambda name: name):
            with patch.object(package, "run", return_value=good) as run:
                package.verify_signature(Path("test.apk"), pin)
                self.assertEqual(run.call_args.args[0][:4], ["zipalign", "-c", "-P", "16"])
            for output in (good.replace(pin, "d" * 64), good.replace("true", "false", 1), good + f"Signer #2 certificate SHA-256 digest: {pin}\n"):
                with patch.object(package, "run", return_value=output), self.assertRaises(RuntimeError):
                    package.verify_signature(Path("test.apk"), pin)

    def test_launcher_branding_is_verified_in_the_packaged_apk(self):
        with patch.object(package.packages, "android_tool", side_effect=lambda x: x), \
             patch.object(package.packages, "inspect_manifest"), patch.object(package.packages, "inspect_notices"):
            with patch.object(package, "run", side_effect=["manifest", "application-label:'Parlor Test'\n"]):
                package.inspect(Path("test.apk"))
            for label in ("Parlor", "Parlor Debug", "Other"):
                with patch.object(package, "run", side_effect=["manifest", f"application-label:'{label}'\n"]), self.assertRaises(RuntimeError):
                    package.inspect(Path("test.apk"))

    def test_disposable_key_is_private_scoped_retired_and_never_in_command_arguments(self):
        for failure in (None, "export", "sign"):
            commands, keys = [], []
            def run(command, **kwargs):
                commands.append(command)
                env = kwargs.get("env")
                if env:
                    self.assertNotIn("GH_TOKEN", env)
                    self.assertNotIn("GH_DIST_ANDROID_KEYSTORE_PASSWORD", env)
                    self.assertTrue(env[package.PASSWORD_ENV])
                    self.assertNotIn(env[package.PASSWORD_ENV], command)
                if "-genkeypair" in command:
                    key = Path(command[command.index("-keystore") + 1])
                    key.write_bytes(b"synthetic private key")
                    keys.append(key)
                elif "-exportcert" in command:
                    self.assertEqual(keys[0].stat().st_mode & 0o777, 0o600)
                    if failure == "export":
                        raise RuntimeError("synthetic export failure")
                    Path(command[command.index("-file") + 1]).write_bytes(b"synthetic public certificate")
                elif command[0] == "zipalign":
                    Path(command[-1]).write_bytes(b"aligned fixture")
                elif command[0] == "apksigner":
                    if failure == "sign":
                        raise RuntimeError("synthetic signing failure")
                    Path(command[command.index("--out") + 1]).write_bytes(b"signed fixture")
                return ""
            with tempfile.TemporaryDirectory() as temporary, \
                 patch.object(package, "inspect"), patch.object(package, "verify_signature"), \
                 patch.object(package.packages, "java_tool", return_value="keytool"), \
                 patch.object(package.packages, "android_tool", side_effect=lambda name: name), \
                 patch.object(package, "run", side_effect=run), \
                 patch.dict(os.environ, {"GH_TOKEN": "synthetic-token", "GH_DIST_ANDROID_KEYSTORE_PASSWORD": "synthetic-secret"}):
                destination = Path(temporary) / "test.apk"
                if failure:
                    with self.assertRaises(RuntimeError):
                        package.sign_for_testing(Path("unsigned.apk"), destination)
                    self.assertFalse(destination.exists())
                else:
                    pin = package.sign_for_testing(Path("unsigned.apk"), destination)
                    self.assertRegex(pin, r"^[a-f0-9]{64}$")
                    self.assertEqual(destination.read_bytes(), b"signed fixture")
                self.assertEqual(len(keys), 1)
                self.assertFalse(keys[0].parent.exists())
                self.assertFalse(any("AndroidDebugKey" in part for command in commands for part in command))

    def test_wrong_identity_is_rejected_before_any_key_is_generated(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(package, "inspect", side_effect=ValueError("Wrong identity")), \
             patch.object(package, "run") as run:
            with self.assertRaises(ValueError):
                package.sign_for_testing(Path("store.apk"), Path(temporary) / "test.apk")
            run.assert_not_called()

    def test_smoke_refuses_local_devices_and_never_takes_an_existing_adb_port(self):
        with patch.object(package.packages, "check_host"), patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), self.assertRaises(RuntimeError):
            smoke.verify_install(Path("test.apk"))
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            with self.assertRaises(OSError):
                smoke.unused_ports((server.getsockname()[1],))
            self.assertGreater(server.fileno(), 0)

    def test_build_requires_release_shrinking_and_normal_install_smoke_before_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "composeApp/build/outputs/apk/githubTest"
            directory.mkdir(parents=True)
            (directory / "unsigned.apk").write_bytes(b"unsigned fixture")
            def sign(_source, destination):
                destination.write_bytes(b"signed fixture")
                return "c" * 64
            with patch.object(package, "ROOT", root), patch.object(package.packages, "check_host"), \
                 patch.object(package.packages, "gradle") as gradle, patch.object(package, "sign_for_testing", side_effect=sign), \
                 patch.object(package, "verify_signature"), patch.object(smoke, "verify_install") as install:
                receipt = package.build(root / "test.apk")
                self.assertTrue(receipt["install_launch_smoke"])
                install.assert_called_once_with(root / "test.apk")
                tasks = gradle.call_args.args[0]
                self.assertIn(":composeApp:assembleGithubTest", tasks)
                self.assertIn(":composeApp:testGithubTestUnitTest", tasks)
                self.assertIn(":composeApp:lintGithubTest", tasks)
                install.side_effect = RuntimeError("cannot install normally")
                with self.assertRaises(RuntimeError):
                    package.build(root / "failed.apk")

    def test_smoke_checks_normal_install_launch_and_liveness_and_retires_only_its_resources(self):
        for failure in (None, "boot", "install", "launch", "liveness", "shutdown"):
            with tempfile.TemporaryDirectory() as temporary, self.subTest(failure=failure):
                root = Path(temporary)
                (root / "config").mkdir()
                (root / "config/release-policy.json").write_text(json.dumps({"toolchains": {"android_managed_device": {
                    "system_image_package": "system-images;android-35;google_apis;x86_64", "system_image_revision": "9",
                }}}))
                sdk = root / "sdk"
                image = sdk / "system-images/android-35/google_apis/x86_64"
                image.mkdir(parents=True)
                (image / "source.properties").write_text("Pkg.Revision = 9\n")
                kvm = root / "kvm"
                kvm.touch()
                commands, homes = [], []

                def run(command, **kwargs):
                    commands.append(command)
                    env = kwargs["env"]
                    homes.append(Path(env["ANDROID_AVD_HOME"]).parent)
                    self.assertEqual(env["ANDROID_ADB_SERVER_PORT"], "5038")
                    self.assertEqual(env["ADB_SERVER_SOCKET"], "tcp:localhost:5038")
                    self.assertNotIn("-t", command)
                    if Path(command[0]).name == "adb":
                        self.assertEqual(command[1:3], ["-P", "5038"])
                        if command[-1] not in {"start-server", "kill-server"}:
                            self.assertEqual(command[3:5], ["-s", "emulator-5660"])
                    if "getprop" in command:
                        return "1\n"
                    if "install" in command:
                        return "INSTALL_FAILED\n" if failure == "install" else "Success\n"
                    if "start" in command:
                        return "Error: failed\n" if failure == "launch" else "Status: ok\n"
                    if "pidof" in command:
                        return "" if failure == "liveness" else "1234\n"
                    return ""

                process = Mock()
                process.poll.return_value = 1 if failure == "boot" else None
                if failure == "shutdown":
                    process.wait.side_effect = smoke.subprocess.TimeoutExpired("owned emulator", 15)
                with patch.object(smoke, "ROOT", root), patch.object(smoke.packages, "check_host"), \
                     patch.object(smoke, "Path", side_effect=lambda p: kvm if str(p) == "/dev/kvm" else Path(p)), \
                     patch.dict(os.environ, {"ANDROID_HOME": str(sdk), "GITHUB_ACTIONS": "true"}), \
                     patch.object(smoke, "unused_ports"), patch.object(smoke.os, "access", return_value=True), \
                     patch.object(smoke.time, "sleep"), patch.object(smoke, "run", side_effect=run), \
                     patch.object(smoke.subprocess, "Popen", return_value=process) as start:
                    if failure:
                        with self.assertRaises((RuntimeError, smoke.subprocess.TimeoutExpired)):
                            smoke.verify_install(root / "test.apk")
                    else:
                        smoke.verify_install(root / "test.apk")
                    self.assertIn("-no-window", start.call_args.args[0])
                    self.assertIn("5660", start.call_args.args[0])
                self.assertEqual(commands[-1], [str(sdk / "platform-tools/adb"), "-P", "5038", "kill-server"])
                self.assertTrue(homes)
                self.assertEqual(len(set(homes)), 1)
                self.assertFalse(homes[0].exists())
                if failure != "boot":
                    process.terminate.assert_called_once()
                if failure == "shutdown":
                    process.kill.assert_called_once()


if __name__ == "__main__":
    unittest.main()
