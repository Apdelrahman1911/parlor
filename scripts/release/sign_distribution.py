#!/usr/bin/env python3
"""Protected, validation-only distribution signing. No Store or Release upload."""
from __future__ import annotations

import argparse
import base64
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import shlex
import shutil
import signal
import sys
import tempfile

from scripts.release import desktop_package as packages
from scripts.release.distribution_image import NATIVE_NAMES, app_directory, inventory_digest, verify_no_embedded_native
from scripts.release.github_distribution import (
    GitHub, OUT, ROOT, PLATFORMS, SIGNING_ENVIRONMENTS, canonical, digest, filename, load,
    require, require_approval, run, source, version,
)

MACHO_MAGIC = {b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf", b"\xce\xfa\xed\xfe", b"\xfe\xed\xfa\xce",
               b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca", b"\xca\xfe\xba\xbf", b"\xbf\xba\xfe\xca"}
JVM_ENTITLEMENTS = {"com.apple.security.cs.allow-jit": True, "com.apple.security.cs.allow-unsigned-executable-memory": True}


def secret(name: str) -> str:
    value = os.environ.get(name, "")
    require(bool(value) and len(value) <= 16 * 1024 * 1024, f"Required protected signing secret is missing: {name}")
    return value


def decode_secret(name: str, path: Path) -> None:
    try:
        data = base64.b64decode(secret(name), validate=True)
    except ValueError:
        raise RuntimeError("Signing material is not valid base64") from None
    require(0 < len(data) <= 10 * 1024 * 1024 and not path.exists(), "Invalid private signing material")
    with path.open("xb") as stream:
        os.chmod(path, 0o600)
        stream.write(data)


def certificate_pin() -> str:
    value = os.environ.get("GH_DIST_CERT_SHA256", "")
    require(re.fullmatch(r"[0-9a-f]{64}", value) is not None, "Protected expected signing certificate pin is missing")
    return value


def verify_android(path: Path, pin: str) -> None:
    packages.inspect_apk(path)
    text = run([packages.android_tool("apksigner"), "verify", "--verbose", "--print-certs", "--Werr", str(path)])
    pins = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)$", text, re.M)
    require(pins == [pin], "APK signer differs from the approved certificate")
    require("Verified using v2 scheme (APK Signature Scheme v2): true" in text
            and "Verified using v3 scheme (APK Signature Scheme v3): true" in text, "APK requires v2 and v3 signatures")
    require("Android Debug" not in text and "AndroidDebugKey" not in text, "Debug signing is forbidden")
    run([packages.android_tool("zipalign"), "-c", "-P", "16", "4", str(path)])


def sign_android(path: Path, temporary: Path) -> dict:
    pin = certificate_pin()
    key = temporary / "signing.keystore"
    decode_secret("GH_DIST_ANDROID_KEYSTORE_BASE64", key)
    alias = secret("GH_DIST_ANDROID_KEY_ALIAS")
    require(re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", alias) is not None and alias.lower() != "androiddebugkey", "Invalid release-key alias")
    secret("GH_DIST_ANDROID_KEYSTORE_PASSWORD")
    secret("GH_DIST_ANDROID_KEY_PASSWORD")
    cert = temporary / "certificate.der"
    run([packages.java_tool("keytool"), "-exportcert", "-keystore", str(key), "-alias", alias,
         "-storepass:env", "GH_DIST_ANDROID_KEYSTORE_PASSWORD", "-file", str(cert)])
    require(digest(cert) == pin, "Android key does not match the protected public certificate pin")
    run(["openssl", "x509", "-inform", "DER", "-in", str(cert), "-checkend", "2592000", "-noout"])
    aligned = temporary / "aligned.apk"
    signed = temporary / "signed.apk"
    run([packages.android_tool("zipalign"), "-P", "16", "4", str(path), str(aligned)])
    run([packages.android_tool("apksigner"), "sign", "--ks", str(key), "--ks-key-alias", alias,
         "--ks-pass", "env:GH_DIST_ANDROID_KEYSTORE_PASSWORD", "--key-pass", "env:GH_DIST_ANDROID_KEY_PASSWORD",
         "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
         "--v4-signing-enabled", "false", "--out", str(signed), str(aligned)])
    verify_android(signed, pin)
    shutil.copyfile(signed, path)
    verify_android(path, pin)
    return {"signing": "android-apk", "certificate_sha256": pin}


@contextmanager
def mac_keychain(temporary: Path):
    """Install traps before import; restore both OS defaults even on forced failure."""
    previous_list = shlex.split(run(["security", "list-keychains", "-d", "user"]))
    previous_default = shlex.split(run(["security", "default-keychain", "-d", "user"]))
    require(bool(previous_list) and len(previous_default) == 1, "Cannot record original keychain preferences")
    keychain = temporary / "publisher.keychain-db"
    password = secrets.token_urlsafe(36)
    created = False
    try:
        run(["security", "create-keychain", "-p", password, str(keychain)])
        created = True
        run(["security", "set-keychain-settings", "-lut", "21600", str(keychain)])
        run(["security", "unlock-keychain", "-p", password, str(keychain)])
        cert = temporary / "publisher.p12"
        decode_secret("GH_DIST_MACOS_CERTIFICATE_BASE64", cert)
        run(["security", "import", str(cert), "-k", str(keychain), "-P", secret("GH_DIST_MACOS_CERTIFICATE_PASSWORD"),
             "-T", "/usr/bin/codesign"])
        run(["security", "set-key-partition-list", "-S", "apple-tool:,apple:,codesign:", "-s", "-k", password, str(keychain)])
        pem = temporary / "publisher.pem"
        der = temporary / "publisher.der"
        run(["openssl", "pkcs12", "-in", str(cert), "-clcerts", "-nokeys", "-passin", "env:GH_DIST_MACOS_CERTIFICATE_PASSWORD", "-out", str(pem)])
        require(pem.read_text().count("-----BEGIN CERTIFICATE-----") == 1, "Expected one Developer ID leaf certificate")
        run(["openssl", "x509", "-in", str(pem), "-outform", "DER", "-out", str(der)])
        pin = certificate_pin()
        require(digest(der) == pin, "Developer ID certificate differs from the protected pin")
        run(["openssl", "x509", "-in", str(pem), "-checkend", "2592000", "-noout"])
        description = run(["openssl", "x509", "-in", str(pem), "-subject", "-nameopt", "RFC2253", "-text", "-noout"])
        team = os.environ.get("GH_DIST_MACOS_TEAM_ID", "")
        require(re.fullmatch(r"[A-Z0-9]{10}", team) is not None and f"OU={team}," in description
                and "CN=Developer ID Application:" in description and "1.2.840.113635.100.6.1.13" in description,
                "Certificate is not the approved team's Developer ID Application identity")
        run(["security", "verify-cert", "-c", str(pem), "-p", "codeSign", "-R", "online"])
        identity = hashlib.sha1(der.read_bytes()).hexdigest().upper()  # security's selector, NOT our integrity digest.
        available = run(["security", "find-identity", "-v", "-p", "codesigning", str(keychain)])
        require(identity in available, "Developer ID certificate has no usable matching private key")
        yield str(keychain), identity, pin, team
    finally:
        errors = []
        if created or keychain.exists():
            for command in (["security", "list-keychains", "-d", "user", "-s", *previous_list],
                            ["security", "default-keychain", "-d", "user", "-s", previous_default[0]],
                            ["security", "delete-keychain", str(keychain)]):
                try:
                    run(command)
                except (RuntimeError, OSError):
                    errors.append(True)
        require(not errors and not keychain.exists(), "Ephemeral signing keychain cleanup failed")


def macho_files(image: Path) -> list[Path]:
    result = []
    for path in image.rglob("*"):
        if path.is_file() and not path.is_symlink():
            with path.open("rb") as stream:
                if stream.read(4) in MACHO_MAGIC:
                    result.append(path)
    require(0 < len(result) < 1000, "Invalid native macOS hierarchy")
    return sorted(result, key=lambda path: (-len(path.parts), str(path)))


def signature_flags(text: str) -> int:
    match = re.search(r"\bflags=0x([0-9a-fA-F]+)\b", text)
    require(match is not None, "Native signature lacks structural flags")
    return int(match[1], 16)


def verify_mac_signature(path: Path, temporary: Path, pin: str, team: str, runtime: bool = True) -> None:
    run(["codesign", "--verify", "--strict", "-R", "anchor apple generic", str(path)])
    text = run(["codesign", "--display", "--verbose=4", str(path)])
    flags = signature_flags(text)
    require(not flags & 2 and (not runtime or flags & 0x10000 != 0), "Ad-hoc or non-hardened production signature")
    require(f"TeamIdentifier={team}" in text.splitlines() and re.search(r"(?m)^Timestamp=.+$", text) is not None,
            "Native team or secure timestamp differs")
    prefix = temporary / "verified-leaf-"
    run(["codesign", "--display", "--extract-certificates", str(prefix), str(path)])
    require(digest(Path(str(prefix) + "0")) == pin, "Native signature certificate differs")
    for cert in temporary.glob("verified-leaf-*"):
        cert.unlink()
    entitlements = run(["codesign", "--display", "--entitlements", ":-", str(path)])
    start, end = entitlements.find("<?xml"), entitlements.find("</plist>")
    if start >= 0:
        require(end > start, "Malformed native entitlements")
        actual = plistlib.loads(entitlements[start:end + len("</plist>")].encode())
        require(isinstance(actual, dict) and all(key in JVM_ENTITLEMENTS and value is True for key, value in actual.items()),
                "Unexpected production entitlement (debug/library-validation bypass is forbidden)")


def notarize(path: Path, temporary: Path) -> None:
    key = temporary / "notary.p8"
    if not key.exists():
        decode_secret("GH_DIST_APPLE_API_KEY_BASE64", key)
    key_id, issuer = secret("GH_DIST_APPLE_API_KEY_ID"), secret("GH_DIST_APPLE_API_ISSUER_ID")
    require(re.fullmatch(r"[A-Z0-9]{10}", key_id) is not None and
            re.fullmatch(r"[0-9a-fA-F-]{36}", issuer) is not None, "Invalid notarization credential identifiers")
    response = json.loads(run(["xcrun", "notarytool", "submit", str(path), "--key", str(key), "--key-id", key_id,
                               "--issuer", issuer, "--wait", "--timeout", "30m", "--output-format", "json"], timeout=1860))
    require(response.get("status") == "Accepted", "Notarization was not Accepted; no publication is permitted")


def verify_mac_image(image: Path, temporary: Path, pin: str, team: str) -> None:
    verify_no_embedded_native(image)
    for path in macho_files(image):
        verify_mac_signature(path, temporary, pin, team)
    verify_mac_signature(image / "Contents/runtime", temporary, pin, team)
    verify_mac_signature(image, temporary, pin, team)
    run(["xcrun", "stapler", "validate", str(image)])
    run(["spctl", "--assess", "--type", "execute", str(image)])


def sign_mac(platform: str, temporary: Path) -> dict:
    image = packages.image_path(platform)
    with mac_keychain(temporary) as (keychain, identity, pin, team):
        entitlements = temporary / "jvm-entitlements.plist"
        entitlements.write_bytes(plistlib.dumps(JVM_ENTITLEMENTS))
        files = macho_files(image)
        bundles = [image / "Contents/runtime", image]
        # Capture/verify original signatures before modifying a containing bundle.
        states = {}
        for path in files + bundles:
            metadata = run(["codesign", "--display", "--verbose=4", str(path)], success_codes=(0, 1))
            if "code object is not signed at all" in metadata:
                states[path] = "unsigned"
            else:
                run(["codesign", "--verify", "--strict", str(path)])
                states[path] = "adhoc" if signature_flags(metadata) & 2 else "vendor"
                if states[path] == "vendor":
                    # Preserve valid vendor signatures. A foreign team cannot be
                    # loaded by this deliberately library-validated runtime.
                    verify_mac_signature(path, temporary, pin, team)
        for path in files:
            arches = set(run(["lipo", "-archs", str(path)]).strip().split())
            expected = "arm64" if platform == "macos-arm64" else "x86_64"
            require(expected in arches and arches <= {"arm64", "x86_64"}, "Wrong packaged Mach-O architecture")
        for path in files + bundles:
            if states[path] == "vendor":
                continue
            command = ["codesign", "--force", "--sign", identity, "--keychain", keychain, "--options", "runtime", "--timestamp"]
            if path in bundles or path.suffix not in {".dylib", ".jnilib"}:
                command += ["--entitlements", str(entitlements)]
            # The native checksum is a resource sealed by the outer app signature.
            if path == image:
                native = app_directory(image, platform) / NATIVE_NAMES[platform]
                native.with_name(native.name + ".sha256").write_text(digest(native), encoding="ascii")
            run(command + [str(path)])
            verify_mac_signature(path, temporary, pin, team)
        request = temporary / "notary-app.zip"
        run(["ditto", "-c", "-k", "--keepParent", str(image), str(request)], timeout=600)
        notarize(request, temporary)
        request.unlink()
        run(["xcrun", "stapler", "staple", str(image)], timeout=180)
        verify_mac_image(image, temporary, pin, team)
        final = packages.package(platform)
        run(["codesign", "--sign", identity, "--keychain", keychain, "--timestamp", str(final)])
        verify_mac_signature(final, temporary, pin, team, runtime=False)
        notarize(final, temporary)
        run(["xcrun", "stapler", "staple", str(final)], timeout=180)
        run(["xcrun", "stapler", "validate", str(final)])
        run(["spctl", "--assess", "--type", "open", "--context", "context:primary-signature", str(final)])
        verify_mac_signature(final, temporary, pin, team, runtime=False)
        packages.inspect_installer(platform, final, lambda app: verify_mac_image(app, temporary, pin, team))
    return {"signing": "developer-id-notarized", "certificate_sha256": pin, "team_id": team,
            "notarization": "Accepted", "stapled": True, "gatekeeper": True, "nested_signatures": True}


def sign_windows(temporary: Path) -> dict:
    require(os.environ.get("GH_DIST_WINDOWS_PFX_APPROVED_SHA") == os.environ.get("GITHUB_SHA"),
            "An exportable Windows PFX needs explicit source-bound owner authorization")
    decode_secret("GH_DIST_WINDOWS_PFX_BASE64", temporary / "publisher.pfx")
    secret("GH_DIST_WINDOWS_PFX_PASSWORD")
    pin = certificate_pin()
    env = {**os.environ, "GH_DIST_PRIVATE_DIRECTORY": str(temporary)}
    script = ROOT / "scripts/release/sign_windows_distribution.ps1"
    run(["pwsh", "-NoProfile", "-NonInteractive", "-File", str(script), "-Operation", "SignImage"], timeout=1800, env=env)
    final = packages.package("windows-x64")
    run(["pwsh", "-NoProfile", "-NonInteractive", "-File", str(script), "-Operation", "SignInstaller"], timeout=300, env=env)

    def verify(image: Path):
        run(["pwsh", "-NoProfile", "-NonInteractive", "-File", str(script), "-Operation", "VerifyImage", "-Image", str(image)],
            timeout=600, env=env)
    packages.inspect_installer("windows-x64", final, verify)
    return {"signing": "authenticode-timestamped", "certificate_sha256": pin, "timestamped": True, "nested_signatures": True}


@contextmanager
def retire_on_sigterm():
    def interrupted(_signal, _frame):
        raise RuntimeError("Signing interrupted; retiring private material")
    previous = signal.signal(signal.SIGTERM, interrupted)
    try:
        yield
    finally:
        signal.signal(signal.SIGTERM, previous)


@retire_on_sigterm()
def sign(platform: str) -> None:
    require(os.environ.get("GH_DIST_MODE") == "candidate", "Signing is validation-only candidate work, never rehearsal/publication")
    packages.check_host(platform)
    acceptance = require_approval(GitHub(), SIGNING_ENVIRONMENTS[platform], source())
    final = packages.WORK / filename(platform, version())
    before = load(OUT / "validation.json")
    require(before.get("signing") == "unsigned-rehearsal" and before.get("artifact_sha256") == digest(final)
            and before.get("passed") is True and before.get("package_inspection") is True, "Build receipt or unsigned bytes differ")
    if platform != "android":
        require(load(OUT / "prepared-image.json")["image_sha256"] == before.get("image_sha256")
                == inventory_digest(packages.image_path(platform)), "Native image changed after its build/probe")
    (OUT / "validation.json").unlink()  # No stale passing receipt can survive a signing failure.
    try:
        with tempfile.TemporaryDirectory(prefix="parlor-private-signing-") as temporary:
            scratch = Path(temporary)
            if platform == "android":
                receipt = sign_android(final, scratch)
            elif platform.startswith("macos-"):
                receipt = sign_mac(platform, scratch)
            elif platform == "windows-x64":
                receipt = sign_windows(scratch)
            else:
                packages.inspect_installer(platform, final)
                receipt = {"signing": "checksum-and-provenance"}
        receipt.update({"passed": True, "artifact_sha256": digest(final), "package_inspection": True, "acceptance": acceptance})
        if platform != "android":
            receipt["image_sha256"] = inventory_digest(packages.image_path(platform))
        (OUT / "validation.json").write_bytes(canonical(receipt))
    except BaseException:
        cleanup_candidate(platform)
        raise


def cleanup_candidate(platform: str) -> None:
    """Only disposable outputs of this job; never an installed app, cache or user data."""
    for path in (packages.WORK, OUT / "frozen", packages.image_path(platform)):
        require(path.is_relative_to(ROOT / "build") or path.is_relative_to(ROOT / "composeApp/build/compose/binaries/main/app"),
                "Unexpected cleanup scope")
        require(not path.is_symlink(), "Refusing redirected signing cleanup")
        if path.exists():
            shutil.rmtree(path)
    (OUT / "validation.json").unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["sign", "cleanup"])
    parser.add_argument("--platform", choices=list(PLATFORMS), required=True)
    args = parser.parse_args()
    if args.operation == "cleanup":
        cleanup_candidate(args.platform)
    else:
        sign(args.platform)


if __name__ == "__main__":
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        safe = str(error) if type(error) is RuntimeError else type(error).__name__
        print(f"Distribution signing failed: {safe}", file=sys.stderr)
        raise SystemExit(2)
