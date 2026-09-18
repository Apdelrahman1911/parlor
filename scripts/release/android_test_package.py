#!/usr/bin/env python3
"""Installable, isolated GitHub test APK. Never uses a Store or Debug identity/key."""
from __future__ import annotations

import os
from pathlib import Path
import re
import secrets
import shutil
import tempfile

from scripts.release import desktop_package as packages
from scripts.release.github_distribution import ROOT, digest, require, run, version
from scripts.release.sign_distribution import retire_on_sigterm

APPLICATION_ID = "me.parlor.android.test"
LABEL = "Parlor Test"
ALIAS = "parlor-github-test"
PASSWORD_ENV = "PARLOR_EPHEMERAL_TEST_PASSWORD"


def inspect(path: Path) -> None:
    manifest = run([packages.android_tool("apkanalyzer"), "manifest", "print", str(path)])
    packages.inspect_manifest(manifest.encode(), version()["name"] + "-test", version()["build"],
                              application_id=APPLICATION_ID)
    badging = run([packages.android_tool("aapt2"), "dump", "badging", str(path)])
    require(re.findall(r"^application-label:'([^']*)'$", badging, re.M) == [LABEL], "APK test branding differs")
    packages.inspect_notices([path], apk=True)


def verify_signature(path: Path, pin: str) -> None:
    require(re.fullmatch(r"[0-9a-f]{64}", pin) is not None, "Invalid test certificate digest")
    inspect(path)
    result = run([packages.android_tool("apksigner"), "verify", "--verbose", "--print-certs", "--Werr", str(path)])
    require(re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)$", result, re.M) == [pin],
            "APK differs from this build's disposable test certificate")
    require("Verified using v2 scheme (APK Signature Scheme v2): true" in result
            and "Verified using v3 scheme (APK Signature Scheme v3): true" in result, "Test APK needs v2/v3 installation signatures")
    run([packages.android_tool("zipalign"), "-c", "-P", "16", "4", str(path)])


@retire_on_sigterm()
def sign_for_testing(unsigned: Path, destination: Path) -> str:
    """A disposable signature makes Android installable; it is not publisher trust.

    The private key lives only in a private temporary directory for this call.
    No key, password, Debug keystore or production signing material is retained.
    Consequently different test builds require uninstall/reinstall, documented
    in every public prerelease. Only the certificate digest leaves this scope.
    """
    require(not destination.exists() and not destination.is_symlink(), "Refusing to replace a test APK")
    inspect(unsigned)  # Cannot sign the canonical Store or local Debug identity.
    with tempfile.TemporaryDirectory(prefix="parlor-disposable-test-key-") as temporary:
        scratch = Path(temporary)
        env = {key: value for key, value in os.environ.items()
               if not key.startswith(("GH_DIST_", "PARLOR_ANDROID_KEY", "MOBILE_RELEASE_"))
               and key not in {"GH_TOKEN", "GITHUB_TOKEN"}}
        env[PASSWORD_ENV] = secrets.token_urlsafe(36)
        key, certificate = scratch / "test.p12", scratch / "test.der"
        run([packages.java_tool("keytool"), "-genkeypair", "-noprompt", "-storetype", "PKCS12",
             "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "365",
             "-dname", "CN=Parlor GitHub Test Only,O=Parlor Test Builds", "-alias", ALIAS,
             "-keystore", str(key), "-storepass:env", PASSWORD_ENV, "-keypass:env", PASSWORD_ENV], env=env)
        os.chmod(key, 0o600)
        run([packages.java_tool("keytool"), "-exportcert", "-keystore", str(key), "-alias", ALIAS,
             "-storepass:env", PASSWORD_ENV, "-file", str(certificate)], env=env)
        pin = digest(certificate)
        aligned, signed = scratch / "aligned.apk", scratch / "signed.apk"
        run([packages.android_tool("zipalign"), "-P", "16", "4", str(unsigned), str(aligned)])
        run([packages.android_tool("apksigner"), "sign", "--ks", str(key), "--ks-key-alias", ALIAS,
             "--ks-pass", "env:" + PASSWORD_ENV, "--key-pass", "env:" + PASSWORD_ENV,
             "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
             "--v4-signing-enabled", "false", "--out", str(signed), str(aligned)], env=env)
        verify_signature(signed, pin)
        with signed.open("rb") as source, destination.open("xb") as output:
            shutil.copyfileobj(source, output)
    verify_signature(destination, pin)
    return pin


def build(destination: Path) -> dict:
    packages.check_host("android")
    require(not destination.exists(), "Test APK already exists")
    packages.gradle([":composeApp:verifyApplicationIdentities", ":composeApp:testGithubTestUnitTest",
                     ":composeApp:lintGithubTest", ":composeApp:assembleGithubTest"])
    candidates = list((ROOT / "composeApp/build/outputs/apk/githubTest").glob("*.apk"))
    require(len(candidates) == 1, "Expected exactly one isolated GitHub test APK")
    pin = sign_for_testing(candidates[0], destination)
    from scripts.release.android_test_smoke import verify_install
    verify_install(destination)
    verify_signature(destination, pin)
    return {"passed": True, "artifact_sha256": digest(destination), "package_inspection": True,
            "signing": "disposable-test-key", "certificate_sha256": pin, "application_id": APPLICATION_ID,
            "install_launch_smoke": True}
