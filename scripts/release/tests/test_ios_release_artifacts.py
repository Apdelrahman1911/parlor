"""Disposable package/Git fixtures; native metadata is injected, never executed."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest import mock

from scripts.verification import ios_release_artifacts as artifacts


CASE = "game-modes/whodunit/src/commonMain/composeResources/files/cases/synthetic.json"
FONT = "shared/design-system/src/commonMain/composeResources/font/synthetic.ttf"
IDENTITIES = {"store_bundle_id": "com.parlor.app", "debug_bundle_id": "com.parlor.app.debug",
              "store_identity_ownership": {"status": "blocked", "reason": "public_store_collision",
                                           "verified_at": None, "verification_reference": None}}
SYNTHETIC_UUID = "01234567-89ab-cdef-0123-456789abcdef"


class ReleaseArtifactTest(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix="parlor-release-artifact-test-")
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()
        self.app = self.root / "Parlor.app"
        self.app.mkdir()
        self.source = self.root / "source"
        self.source.mkdir()
        self.source_paths = [CASE, FONT]
        self.probed = []
        self.info = {"CFBundleIdentifier": "com.parlor.app", "CFBundleExecutable": "Parlor",
                     "CFBundleShortVersionString": "1.2.3", "CFBundleVersion": "7", "MinimumOSVersion": "16.0",
                     "CFBundleSupportedPlatforms": ["iPhoneSimulator"], "DTPlatformName": "iphonesimulator",
                     "CFBundleLocalizations": ["en", "ar"], "NSBonjourServices": ["_p2pkit2._tcp"],
                     "NSLocalNetworkUsageDescription": "Synthetic local network declaration"}
        self.write(self.app, "Info.plist", plistlib.dumps(self.info))
        self.write(self.source, "iosApp/iosApp/Info.plist", plistlib.dumps(self.info))
        privacy = plistlib.dumps({"NSPrivacyTracking": False, "NSPrivacyCollectedDataTypes": []})
        self.write(self.app, "PrivacyInfo.xcprivacy", privacy)
        self.write(self.source, "iosApp/iosApp/PrivacyInfo.xcprivacy", privacy)
        self.write(self.source, "config/parlor-version.xcconfig", b"PARLOR_VERSION_NAME = 1.2.3\nPARLOR_BUILD_NUMBER = 7\n")
        self.write(self.source, "config/release-policy.json", json.dumps({"applications": {"ios": IDENTITIES}}).encode())
        for relative, value in ((CASE, b'{"synthetic":true}\n'), (FONT, b"synthetic font, not executable")):
            self.write(self.source, relative, value)
            self.write(self.app, artifacts.raw_resource_path(relative), value)
        for relative in sorted(artifacts.REQUIRED_NATIVE):
            self.native(relative)

    @staticmethod
    def write(root, relative, content):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def native(self, relative):
        path = self.write(self.app, relative, bytes.fromhex("cffaedfe") + b"synthetic header, not machine code")
        path.chmod(0o755)

    def probe(self, path):
        self.probed.append(path.relative_to(self.app).as_posix())
        kind = "executable" if path.name == "Parlor" else "dynamically linked shared library"
        return {"file": "Mach-O 64-bit " + kind + " arm64\n", "archs": "arm64\n",
                "build": "Load command 1\n platform IOSSIMULATOR\n minos 15.0\n sdk 26.3\n",
                "uuid": "UUID: " + SYNTHETIC_UUID + " (arm64) " + str(path) + "\n"}

    def inspect(self, probe=None, source_paths=None):
        return artifacts.inspect_release_artifacts(self.app, self.source, probe or self.probe,
                                                  self.source_paths if source_paths is None else source_paths)

    def identity(self):
        return {"head": "a" * 40, "tree": "b" * 40, "diff_sha256": "c" * 64,
                "source_manifest_sha256": "d" * 64, "excluded_paths": 0,
                "files": [artifacts.file_record(self.source / path, path) for path in self.source_paths]}

    def test_every_file_and_extra_debug_dylib_is_inventoried_with_uuid(self):
        self.native("Parlor.debug.dylib")
        result = self.inspect()
        self.assertEqual(artifacts.REQUIRED_NATIVE | {"Parlor.debug.dylib"}, set(self.probed))
        self.assertEqual("PASS", result["status"])
        self.assertEqual(3, len(result["native_binaries"]))
        self.assertTrue(all(row["uuid"] == SYNTHETIC_UUID for row in result["native_binaries"]))
        self.assertEqual(len([path for path in self.app.rglob("*") if path.is_file()]), result["file_count"])
        for row in result["files"]:
            self.assertEqual(hashlib.sha256((self.app / row["path"]).read_bytes()).hexdigest(), row["sha256"])
        self.assertEqual(2, len(result["raw_resource_matches"]))
        self.assertIn("No runtime", result["limitation"])
        self.assertEqual("blocked", result["identity_policy"]["store_identity_ownership"]["status"])

    def test_framework_deployment_floor_may_be_lower_than_app_floor(self):
        result = self.inspect()
        self.assertEqual("16.0", result["app_identity"]["MinimumOSVersion"])
        self.assertTrue(all(row["minimum_os_versions"] == ["15.0"] for row in result["native_binaries"]))

    def test_wrong_native_architecture_platform_floor_and_output_shape_fail(self):
        path = self.app / "Parlor"
        original = self.probe(path)
        for key, value in (("file", "data"), ("archs", "x86_64"), ("archs", "arm64 x86_64"),
                           ("build", "platform IOS\n minos 16.0\n"),
                           ("build", "platform IOSSIMULATOR\n minos 17.0\n"),
                           ("build", "platform IOSSIMULATOR\n"), ("build", "minos 16.0\n"),
                           ("file", "x" * (artifacts.MAX_TOOL_BYTES + 1))):
            changed = dict(original, **{key: value})
            with self.subTest(key=key, value=value[:60]), self.assertRaises(RuntimeError):
                artifacts.verify_native_probe("Parlor", path, changed)
        for value in (None, {}, dict(original, unexpected="extra")):
            with self.subTest(value=type(value).__name__), self.assertRaises(RuntimeError):
                artifacts.verify_native_probe("Parlor", path, value)

    def test_uuid_is_single_arm64_and_bound_to_actual_path(self):
        path = self.app / "Parlor"
        original = self.probe(path)
        for value in ("", original["uuid"] * 2, original["uuid"].replace("arm64", "x86_64"),
                      original["uuid"].replace(str(path), str(path) + ".stale"),
                      original["uuid"].replace(SYNTHETIC_UUID, "not-a-uuid")):
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, "UUID"):
                artifacts.verify_native_probe("Parlor", path, dict(original, uuid=value))

    def test_required_native_must_exist_and_have_macho_header(self):
        path = self.app / "Frameworks/ComposeApp.framework/ComposeApp"
        path.unlink()
        with self.assertRaisesRegex(RuntimeError, "framework are required"):
            self.inspect()
        self.write(self.app, "Frameworks/ComposeApp.framework/ComposeApp", b"not Mach-O")
        with self.assertRaisesRegex(RuntimeError, "recognized Mach-O"):
            self.inspect()

    def test_non_macho_extra_native_file_is_not_hidden_as_resource(self):
        self.write(self.app, "Frameworks/unexpected.a", b"synthetic archive")
        with self.assertRaisesRegex(RuntimeError, "recognized Mach-O"):
            self.inspect()

    def test_release_identity_version_platform_deployment_and_language_are_checked(self):
        for key, value in (("CFBundleIdentifier", "com.parlor.app.debug"), ("CFBundleVersion", "8"),
                           ("CFBundleShortVersionString", "1.2.4"), ("MinimumOSVersion", "15.0"),
                           ("CFBundleExecutable", "Other"), ("CFBundleSupportedPlatforms", ["iPhoneOS"]),
                           ("DTPlatformName", "iphoneos"), ("CFBundleLocalizations", ["en"]),
                           ("NSBonjourServices", ["_wrong._tcp"]), ("NSLocalNetworkUsageDescription", "changed")):
            with self.subTest(key=key):
                self.write(self.app, "Info.plist", plistlib.dumps(dict(self.info, **{key: value})))
                with self.assertRaises(RuntimeError):
                    self.inspect()
        self.write(self.app, "Info.plist", plistlib.dumps(self.info))

    def test_known_identity_collision_cannot_be_promoted_by_package_inspector(self):
        changed = dict(IDENTITIES, store_identity_ownership={"status": "verified"})
        self.write(self.source, "config/release-policy.json", json.dumps({"applications": {"ios": changed}}).encode())
        with self.assertRaisesRegex(RuntimeError, "ownership policy"):
            self.inspect()

    def test_duplicate_version_assignments_fail(self):
        path = self.source / "config/parlor-version.xcconfig"
        path.write_bytes(path.read_bytes() + b"PARLOR_BUILD_NUMBER = 7\n")
        with self.assertRaisesRegex(RuntimeError, "one source xcconfig"):
            self.inspect()

    def test_privacy_structural_match_accepts_binary_plist_but_not_changed_declaration(self):
        privacy = plistlib.loads((self.source / "iosApp/iosApp/PrivacyInfo.xcprivacy").read_bytes())
        self.write(self.app, "PrivacyInfo.xcprivacy", plistlib.dumps(privacy, fmt=plistlib.FMT_BINARY))
        result = self.inspect()
        self.assertTrue(result["privacy_structurally_matches_source"])
        self.assertNotEqual(result["source_privacy_sha256"], result["shipped_privacy_sha256"])
        self.write(self.app, "PrivacyInfo.xcprivacy", plistlib.dumps(dict(privacy, NSPrivacyTracking=True)))
        with self.assertRaisesRegex(RuntimeError, "privacy manifest"):
            self.inspect()

    def test_exact_case_and_font_bytes_are_checked_in_shipping_namespaces(self):
        for relative in (CASE, FONT):
            with self.subTest(relative=relative):
                path = self.app / artifacts.raw_resource_path(relative)
                expected = path.read_bytes()
                path.write_bytes(b"changed bytes")
                with self.assertRaisesRegex(RuntimeError, "Exact raw case/font bytes"):
                    self.inspect()
                path.write_bytes(expected)
        path = self.app / artifacts.raw_resource_path(CASE)
        self.write(self.app, "wrong-directory/synthetic.json", path.read_bytes())
        path.unlink()
        with self.assertRaisesRegex(RuntimeError, "shipping Compose namespace"):
            self.inspect()

    def test_missing_source_categories_or_unknown_font_namespace_fail(self):
        for paths in ([], [CASE], [FONT]):
            with self.subTest(paths=paths), self.assertRaisesRegex(RuntimeError, "source bindings"):
                self.inspect(source_paths=paths)
        with self.assertRaisesRegex(RuntimeError, "unreviewed Compose"):
            artifacts.raw_resource_path("new-game/src/commonMain/composeResources/font/new.ttf")

    def test_symlinked_package_or_resource_is_rejected(self):
        alias = self.root / "alias.app"
        alias.symlink_to(self.app, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "canonical"):
            artifacts.inspect_release_artifacts(alias, self.source, self.probe, self.source_paths)
        (self.app / "alias").symlink_to(self.app / "Info.plist")
        with self.assertRaisesRegex(RuntimeError, "symlink"):
            self.inspect()

    def test_private_material_is_rejected_before_reading_file_bytes(self):
        self.write(self.app, "embedded.mobileprovision", b"synthetic forbidden fixture")
        with mock.patch.object(artifacts, "file_record") as read:
            with self.assertRaisesRegex(RuntimeError, "signing/private material"):
                self.inspect()
            read.assert_not_called()

    def test_unreviewed_extension_and_nonregular_entry_fail(self):
        extension = self.app / "PlugIns/Unreviewed.appex"
        extension.mkdir(parents=True)
        with self.assertRaisesRegex(RuntimeError, "app extension"):
            self.inspect()
        extension.rmdir()
        if hasattr(os, "mkfifo"):
            os.mkfifo(self.app / "synthetic-fifo")
            with self.assertRaisesRegex(RuntimeError, "nonregular"):
                self.inspect()

    def test_file_total_and_plist_bounds_fail_without_large_fixture(self):
        for name in ("MAX_FILES", "MAX_TOTAL_BYTES", "MAX_PLIST_BYTES"):
            with self.subTest(name=name), mock.patch.object(artifacts, name, 1), self.assertRaises(RuntimeError):
                self.inspect()

    def test_package_mutation_during_native_inspection_is_rejected(self):
        def changed(path):
            self.write(self.app, "late-resource.txt", b"unexpected concurrent writer")
            return self.probe(path)
        with self.assertRaisesRegex(RuntimeError, "Package changed"):
            self.inspect(probe=changed)

    def test_full_verifier_binds_source_and_does_not_invalidate_for_excluded_review_evidence(self):
        before = self.identity()
        after = dict(before, excluded_paths=9)
        reader = mock.Mock(side_effect=[before, after])
        result = artifacts.verify_package(self.app, self.source, binary_probe=self.probe, identity_reader=reader)
        self.assertEqual(before, result["source"])
        self.assertTrue(result["source_unchanged_during_inspection"])
        self.assertEqual(2, reader.call_count)
        self.assertEqual(hashlib.sha256(Path(artifacts.__file__).read_bytes()).hexdigest(), result["inspector_sha256"])

    def test_changed_source_identity_cannot_pass(self):
        before = self.identity()
        reader = mock.Mock(side_effect=[before, dict(before, source_manifest_sha256="e" * 64)])
        with self.assertRaisesRegex(RuntimeError, "Source changed"):
            artifacts.verify_package(self.app, self.source, binary_probe=self.probe, identity_reader=reader)

    def test_cli_executes_real_package_checks_with_injected_native_metadata(self):
        output = io.StringIO()
        with mock.patch.object(artifacts, "source_identity", return_value=self.identity()), mock.patch.object(
                artifacts, "native_probe", side_effect=self.probe), contextlib.redirect_stdout(output):
            status = artifacts.main(["--app", str(self.app), "--source", str(self.source), "--json"])
        self.assertEqual(0, status)
        report = json.loads(output.getvalue())
        self.assertEqual("PASS", report["status"])
        self.assertEqual(2, len(report["raw_resource_matches"]))
        self.assertEqual(artifacts.REQUIRED_NATIVE, {row["path"] for row in report["native_binaries"]})
        self.assertTrue(report["source_unchanged_during_inspection"])


class NativeCommandAndCliTest(unittest.TestCase):
    def test_native_probe_uses_only_fixed_readonly_commands(self):
        path = Path("/synthetic/Parlor.app/Parlor")
        with mock.patch.object(artifacts.sys, "platform", "darwin"), mock.patch.object(
                artifacts, "bounded_command", return_value=b"synthetic output\n") as run:
            result = artifacts.native_probe(path)
        self.assertEqual({"file", "archs", "build", "uuid"}, set(result))
        self.assertEqual([
            mock.call(["/usr/bin/file", "-b", str(path)]),
            mock.call(["/usr/bin/xcrun", "lipo", "-archs", str(path)]),
            mock.call(["/usr/bin/xcrun", "vtool", "-show-build", str(path)]),
            mock.call(["/usr/bin/xcrun", "dwarfdump", "--uuid", str(path)]),
        ], run.call_args_list)

    def test_nonmac_native_probe_cannot_claim_execution(self):
        with mock.patch.object(artifacts.sys, "platform", "linux"), mock.patch.object(artifacts, "bounded_command") as run:
            with self.assertRaisesRegex(RuntimeError, "requires macOS"):
                artifacts.native_probe(Path("/fixture"))
            run.assert_not_called()

    def test_command_output_exit_and_timeout_guards_close_owned_spool(self):
        streams = []

        def command(arguments, **options):
            self.assertEqual(30, options["timeout"])
            self.assertFalse(options["check"])
            self.assertEqual(subprocess.STDOUT, options["stderr"])
            streams.append(options["stdout"])
            options["stdout"].write(b"1234")
            return subprocess.CompletedProcess(arguments, 0)

        with mock.patch.object(artifacts.subprocess, "run", side_effect=command):
            self.assertEqual(b"1234", artifacts.bounded_command(["synthetic"], limit=4))
            with self.assertRaisesRegex(RuntimeError, "acceptance bound"):
                artifacts.bounded_command(["synthetic"], limit=3)
        self.assertTrue(all(stream.closed for stream in streams))
        with mock.patch.object(artifacts.subprocess, "run", return_value=subprocess.CompletedProcess([], 7)):
            with self.assertRaisesRegex(RuntimeError, "command failed"):
                artifacts.bounded_command(["synthetic"])
        with mock.patch.object(artifacts.subprocess, "run", side_effect=subprocess.TimeoutExpired([], 30)):
            with self.assertRaises(subprocess.TimeoutExpired):
                artifacts.bounded_command(["synthetic"])

    def test_cli_returns_nonzero_json_on_verification_failure(self):
        output = io.StringIO()
        with mock.patch.object(artifacts, "verify_package", side_effect=RuntimeError("synthetic rejection")), contextlib.redirect_stdout(output):
            status = artifacts.main(["--app", "/fixture.app", "--source", "/fixture", "--json"])
        self.assertEqual(2, status)
        report = json.loads(output.getvalue())
        self.assertEqual("FAIL", report["status"])
        self.assertNotIn("files", report)

    def test_cli_rejects_oversized_success_report(self):
        output = io.StringIO()
        with mock.patch.object(artifacts, "MAX_REPORT_BYTES", 1), mock.patch.object(
                artifacts, "verify_package", return_value={"status": "PASS"}), contextlib.redirect_stdout(output):
            status = artifacts.main(["--app", "/fixture.app", "--source", "/fixture", "--json"])
        self.assertEqual(2, status)
        self.assertEqual("FAIL", json.loads(output.getvalue())["status"])


class GitSourceIdentityTest(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix="parlor-release-source-git-")
        self.addCleanup(allocation.cleanup)
        self.root = Path(allocation.name).resolve()
        self.environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        self.environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT="0")
        self.git("init", "--quiet")
        self.git("config", "user.name", "Synthetic fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.source = self.root / "composeApp/src/commonMain/kotlin/Synthetic.kt"
        self.source.parent.mkdir(parents=True)
        self.source.write_bytes(b"// synthetic first version\n")
        self.git("add", "--", "composeApp")
        self.git("-c", "commit.gpgsign=false", "commit", "--quiet", "--no-gpg-sign", "-m", "Synthetic fixture")

    def git(self, *arguments):
        return subprocess.check_output(["git", "-C", str(self.root), *arguments],
                                       env=self.environment, stderr=subprocess.STDOUT, timeout=30)

    def test_head_tree_and_dirty_and_untracked_build_input_hashes_are_actual(self):
        before = artifacts.source_identity(self.root)
        self.assertEqual(self.git("rev-parse", "HEAD").decode().strip(), before["head"])
        self.assertEqual(self.git("rev-parse", "HEAD^{tree}").decode().strip(), before["tree"])
        self.assertEqual(hashlib.sha256(b"").hexdigest(), before["diff_sha256"])
        self.source.write_bytes(b"// synthetic second version\n")
        untracked = self.source.with_name("NewResource.kt")
        untracked.write_bytes(b"// synthetic untracked build input\n")
        after = artifacts.source_identity(self.root)
        self.assertNotEqual(before["diff_sha256"], after["diff_sha256"])
        self.assertNotEqual(before["source_manifest_sha256"], after["source_manifest_sha256"])
        self.assertEqual([untracked.relative_to(self.root).as_posix()], after["untracked_included"])
        hashes = {row["path"]: row["sha256"] for row in after["files"]}
        self.assertEqual(hashlib.sha256(untracked.read_bytes()).hexdigest(), hashes[untracked.relative_to(self.root).as_posix()])

    def test_protected_and_unrelated_untracked_files_are_not_read(self):
        for relative in ("config/fixture.p12", "local.properties", "personal-notes.txt", "remediation-runs/evidence.txt"):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"synthetic protected fixture, no real private data")
        self.git("add", "--", "config/fixture.p12")
        result = artifacts.source_identity(self.root)
        self.assertEqual([self.source.relative_to(self.root).as_posix()], [row["path"] for row in result["files"]])
        self.assertEqual(4, result["excluded_paths"])

    def test_untracked_symlinked_build_input_is_rejected(self):
        self.source.with_name("Link.kt").symlink_to(self.source)
        with self.assertRaisesRegex(RuntimeError, "redirected"):
            artifacts.source_identity(self.root)

    def test_subdirectory_cannot_masquerade_as_full_source_checkout(self):
        with self.assertRaisesRegex(RuntimeError, "exact Git checkout root"):
            artifacts.source_identity(self.root / "composeApp")
