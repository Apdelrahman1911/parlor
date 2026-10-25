from __future__ import annotations

import base64
import hashlib
import os
from pathlib import Path
import signal
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.release import sign_distribution as signing
from scripts.release import github_distribution as dist


class DistributionSigningTest(unittest.TestCase):
    def test_decoded_material_is_scoped_owner_only_and_invalid_base64_is_not_echoed(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "secret"
            with patch.dict(os.environ, {"FIXTURE_KEY": base64.b64encode(b"synthetic private fixture").decode()}, clear=True):
                signing.decode_secret("FIXTURE_KEY", path)
                self.assertEqual(path.read_bytes(), b"synthetic private fixture")
                if os.name != "nt":
                    self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                with self.assertRaises(RuntimeError):
                    signing.decode_secret("FIXTURE_KEY", path)
            marker = "NEVER_ECHO_PRIVATE_INVALID_BASE64"
            with patch.dict(os.environ, {"FIXTURE_KEY": marker}, clear=True), self.assertRaises(RuntimeError) as error:
                signing.decode_secret("FIXTURE_KEY", Path(temporary) / "bad")
            self.assertNotIn(marker, str(error.exception))

    def keychain_fake(self, temporary, fail_after_import=False, fail_cleanup=False):
        calls = []
        certificate = b"synthetic public certificate fixture"
        identity = hashlib.sha1(certificate).hexdigest().upper()

        def fake(command, **kwargs):
            calls.append(command)
            if command[:4] == ["security", "list-keychains", "-d", "user"] and "-s" not in command:
                return '"/original/login.keychain-db"\n"/original/system.keychain"'
            if command[:4] == ["security", "default-keychain", "-d", "user"] and "-s" not in command:
                return '"/original/login.keychain-db"'
            if command[:2] == ["security", "create-keychain"]:
                Path(command[-1]).write_bytes(b"private fixture")
            if command[:2] == ["security", "import"] and fail_after_import:
                raise RuntimeError("injected after import")
            if command[:2] == ["security", "delete-keychain"]:
                if fail_cleanup:
                    raise RuntimeError("injected cleanup failure")
                Path(command[-1]).unlink()
            if command[:2] == ["openssl", "pkcs12"]:
                Path(command[-1]).write_text("-----BEGIN CERTIFICATE-----\nfixture\n-----END CERTIFICATE-----\n")
            if command[:2] == ["openssl", "x509"]:
                if "-outform" in command:
                    Path(command[-1]).write_bytes(certificate)
                if "-subject" in command:
                    return "subject=OU=TEAM1234AA,CN=Developer ID Application: Fixture\n1.2.840.113635.100.6.1.13"
            if command[:2] == ["security", "find-identity"]:
                return identity
            return ""
        return fake, calls, hashlib.sha256(certificate).hexdigest()

    def test_keychain_preferences_and_private_key_are_retired_after_import_or_later_failure(self):
        for point in ("import", "body", "success"):
            with self.subTest(point=point), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                fake, calls, pin = self.keychain_fake(root, fail_after_import=point == "import")
                env = {"GH_DIST_MACOS_CERTIFICATE_BASE64": base64.b64encode(b"fixture").decode(),
                       "GH_DIST_MACOS_CERTIFICATE_PASSWORD": "private fixture password", "GH_DIST_CERT_SHA256": pin,
                       "GH_DIST_MACOS_TEAM_ID": "TEAM1234AA"}
                with patch.object(signing, "run", side_effect=fake), patch.dict(os.environ, env, clear=True):
                    if point == "success":
                        with signing.mac_keychain(root) as (_, _, actual_pin, _):
                            self.assertEqual(actual_pin, pin)
                    else:
                        with self.assertRaisesRegex(RuntimeError, "injected"):
                            with signing.mac_keychain(root):
                                raise RuntimeError("injected after signing")
                self.assertIn(["security", "list-keychains", "-d", "user", "-s", "/original/login.keychain-db", "/original/system.keychain"], calls)
                self.assertIn(["security", "default-keychain", "-d", "user", "-s", "/original/login.keychain-db"], calls)
                self.assertFalse((root / "publisher.keychain-db").exists())

    def test_cleanup_failure_is_never_reported_as_success(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake, calls, pin = self.keychain_fake(root, fail_cleanup=True)
            env = {"GH_DIST_MACOS_CERTIFICATE_BASE64": base64.b64encode(b"fixture").decode(),
                   "GH_DIST_MACOS_CERTIFICATE_PASSWORD": "fixture", "GH_DIST_CERT_SHA256": pin, "GH_DIST_MACOS_TEAM_ID": "TEAM1234AA"}
            with patch.object(signing, "run", side_effect=fake), patch.dict(os.environ, env, clear=True), \
                    self.assertRaisesRegex(RuntimeError, "cleanup failed"):
                with signing.mac_keychain(root):
                    pass

    def test_wrong_developer_id_pin_is_rejected_before_any_code_signing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake, calls, pin = self.keychain_fake(root)
            env = {"GH_DIST_MACOS_CERTIFICATE_BASE64": base64.b64encode(b"fixture").decode(),
                   "GH_DIST_MACOS_CERTIFICATE_PASSWORD": "fixture", "GH_DIST_CERT_SHA256": "0" * 64,
                   "GH_DIST_MACOS_TEAM_ID": "TEAM1234AA"}
            with patch.object(signing, "run", side_effect=fake), patch.dict(os.environ, env, clear=True), \
                    self.assertRaisesRegex(RuntimeError, "protected pin"):
                with signing.mac_keychain(root):
                    self.fail("Mismatched signing identity must not become usable")
            self.assertFalse(any(call[0] == "codesign" for call in calls))
            self.assertFalse((root / "publisher.keychain-db").exists())

    def test_flags_are_structural_and_dont_confuse_combined_runtime_adhoc(self):
        self.assertEqual(signing.signature_flags("CodeDirectory flags=0x10002(adhoc,runtime)"), 0x10002)
        self.assertEqual(signing.signature_flags("CodeDirectory flags=0x10000(runtime)"), 0x10000)
        with self.assertRaises(RuntimeError):
            signing.signature_flags("not a signature")

    def test_notarization_submission_or_in_progress_never_counts_as_accepted(self):
        env = {"GH_DIST_APPLE_API_KEY_BASE64": base64.b64encode(b"synthetic fixture").decode(),
               "GH_DIST_APPLE_API_KEY_ID": "KEY1234567", "GH_DIST_APPLE_API_ISSUER_ID": "11111111-1111-1111-1111-111111111111"}
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, env, clear=True):
            root = Path(temporary)
            for status in ("Submitted", "In Progress", "Invalid", "Rejected"):
                with patch.object(signing, "run", return_value='{"status": "' + status + '"}'), self.assertRaises(RuntimeError):
                    signing.notarize(root / "fixture.zip", root)
            with patch.object(signing, "run", return_value='{"status": "Accepted"}'):
                signing.notarize(root / "fixture.zip", root)

    def test_sigterm_runs_context_cleanup_and_restores_original_handler(self):
        before = signal.getsignal(signal.SIGTERM)
        retired = []
        with self.assertRaises(RuntimeError):
            with signing.retire_on_sigterm():
                try:
                    signal.getsignal(signal.SIGTERM)(signal.SIGTERM, None)
                finally:
                    retired.append(True)
        self.assertEqual(retired, [True])
        self.assertEqual(signal.getsignal(signal.SIGTERM), before)

    def test_failed_signing_removes_owned_outputs_and_secrets_but_preserves_other_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            out = root / "build/github-distribution"
            work = out / "work"
            work.mkdir(parents=True)
            binary = work / dist.filename("android", dist.version())
            binary.write_bytes(b"synthetic fixture unsigned APK")
            receipt = {"signing": "unsigned-rehearsal", "artifact_sha256": dist.digest(binary), "passed": True, "package_inspection": True}
            (out / "validation.json").write_bytes(dist.canonical(receipt))
            preserved = root / "other-user-work"
            preserved.write_bytes(b"preserve")
            app = root / "composeApp/build/compose/binaries/main/app/Parlor"
            private_directories = []

            def fail(_path, private):
                private_directories.append(private)
                (private / "fixture.p12").write_bytes(b"synthetic private fixture")
                raise RuntimeError("injected partial signing failure")

            with patch.object(signing, "ROOT", root), patch.object(signing, "OUT", out), patch.object(signing.packages, "WORK", work), \
                    patch.object(signing.packages, "image_path", return_value=app), patch.object(signing.packages, "check_host"), \
                    patch.object(signing, "source", return_value={"commit": "a" * 40}), patch.object(signing, "GitHub"), \
                    patch.object(signing, "require_approval", return_value={}), patch.object(signing, "sign_android", side_effect=fail), \
                    patch.dict(os.environ, {"GH_DIST_MODE": "candidate"}, clear=True), self.assertRaisesRegex(RuntimeError, "partial signing"):
                signing.sign("android")
            self.assertFalse(work.exists())
            self.assertFalse((out / "validation.json").exists())
            self.assertTrue(all(not path.exists() for path in private_directories))
            self.assertEqual(preserved.read_bytes(), b"preserve")

    def test_windows_credentials_use_ephemeral_storage_and_independent_timestamped_verification(self):
        script = (signing.ROOT / "scripts/release/sign_windows_distribution.ps1").read_text()
        for required in ("EphemeralKeySet", "Get-AuthenticodeSignature", "TimeStamperCertificate", "RevocationMode",
                         "1.3.6.1.5.5.7.3.3", "GH_DIST_WINDOWS_PFX_APPROVED_SHA", "ArgumentList.Add",
                         "'NotSigned'", "'verify', '/pa', '/all'", "finally", "$certificate.Dispose()"):
            self.assertIn(required, script)
        for forbidden in ("Import-PfxCertificate", "Write-Host $env", "Write-Output $env", "Start-Transcript", "Write-Error $_"):
            self.assertNotIn(forbidden, script)


if __name__ == "__main__":
    unittest.main()
