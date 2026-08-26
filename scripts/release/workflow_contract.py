#!/usr/bin/env python3
"""Static security contract for Parlor's GitHub Actions release workflows."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
EXPECTED = {
    "production-verification.yml",
    "testing-candidate.yml",
    "testing-external-promotion.yml",
    "production-promotion.yml",
}
STORE_WORKFLOWS = EXPECTED - {"production-verification.yml"}
PROMOTION_WORKFLOWS = {"testing-external-promotion.yml", "production-promotion.yml"}
FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
ALLOWED_ACTIONS = {
    "actions/checkout",
    "actions/setup-java",
    "actions/upload-artifact",
    "actions/attest-build-provenance",
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def load_files() -> dict[str, str]:
    actual = {path.name for path in WORKFLOWS.glob("*.yml")} | {path.name for path in WORKFLOWS.glob("*.yaml")}
    missing = EXPECTED - actual
    if missing:
        fail(f"Missing required release workflows: {', '.join(sorted(missing))}")
    unexpected = actual - EXPECTED
    if unexpected:
        fail(f"Unreviewed workflow files are forbidden: {', '.join(sorted(unexpected))}")
    return {name: (WORKFLOWS / name).read_text(encoding="utf-8") for name in EXPECTED}


def verify_action_pins(name: str, text: str) -> None:
    for line_number, line in enumerate(text.splitlines(), 1):
        match = re.search(r"\buses:\s*([^\s#]+)", line)
        if not match:
            continue
        reference = match.group(1).strip("'\"")
        if reference.startswith("./"):
            continue
        if "@" not in reference:
            fail(f"{name}:{line_number}: Action has no immutable reference")
        action, revision = reference.rsplit("@", 1)
        if not action or not FULL_SHA.fullmatch(revision):
            fail(f"{name}:{line_number}: third-party Action is not pinned to a full commit SHA")
        if action not in ALLOWED_ACTIONS:
            fail(f"{name}:{line_number}: Action {action!r} is outside the reviewed allowlist")


def verify_common(name: str, text: str) -> None:
    verify_action_pins(name, text)
    forbidden = ("pull_request_target:", "continue-on-error: true", "persist-credentials: true")
    for token in forbidden:
        if token in text:
            fail(f"{name}: forbidden workflow construct: {token}")
    if re.search(r"echo[^\n]*\$\{\{\s*secrets\.", text, re.IGNORECASE):
        fail(f"{name}: possible direct secret echo")
    if "timeout-minutes:" not in text:
        fail(f"{name}: every workflow must bound job duration")
    if "permissions:" not in text or "contents: read" not in text:
        fail(f"{name}: missing least-privilege baseline permissions")


def verify_android_runtime_smoke(name: str, text: str) -> None:
    required = (
        "scripts/android/run_release_managed_device_smoke.sh",
        "system-images;android-35;google_apis;x86_64",
        "test -c /dev/kvm",
        "sudo chmod 0666 /dev/kvm",
        ".toolchains.android_managed_device.system_image_revision",
        "**/build/reports/androidTests/managedDevice/",
        "**/build/outputs/androidTest-results/managedDevice/",
    )
    for token in required:
        if token not in text:
            fail(f"{name}: release managed-device smoke gate is missing {token!r}")


def verify_validation(text: str) -> None:
    if "secrets." in text:
        fail("main/PR validation workflow must not reference secrets")
    if "main" not in text or "testing" not in text or "release" not in text:
        fail("validation workflow must qualify all protected branches")
    if "productionReleaseAutomationCheck" not in text:
        fail("validation workflow does not enforce release-system tests")
    verify_android_runtime_smoke("production-verification.yml", text)
    apple_test_step = text.split(
        "- name: Run iOS simulator tests, Apple static analysis, and release linkage gates",
        1,
    )[-1].split("- name: Validate iOS plist and privacy manifest", 1)[0]
    if "./gradlew productionIosSimulatorRuntimeTests productionAppleCheck" not in apple_test_step:
        fail("validation workflow does not enforce the dedicated executable iOS simulator test aggregate")
    if "./gradlew allTests" in apple_test_step:
        fail("Apple validation duplicates the Linux common/desktop/Android test aggregate")
    app_launch_marker = "- name: Launch Swift host and Compose root on iOS Simulator"
    swift_release_marker = "- name: Build unsigned Swift Release wrapper"
    if app_launch_marker not in text or swift_release_marker not in text:
        fail("validation workflow does not run the iOS app-launch UI test")
    app_launch_step = text.split(app_launch_marker, 1)[1].split(swift_release_marker, 1)[0]
    required_app_launch_contract = (
        "xcrun simctl list devices available --json",
        "-project iosApp/iosApp.xcodeproj",
        "-scheme iosApp",
        "-configuration Debug",
        "-sdk iphonesimulator",
        'platform=iOS Simulator,id=$simulator_udid',
        "-resultBundlePath build/ci-evidence/ios-ui-tests.xcresult",
        "test | tee build/ci-evidence/xcode-ui-test.log",
    )
    for token in required_app_launch_contract:
        if token not in app_launch_step:
            fail(f"validation workflow iOS app-launch test lacks {token!r}")
    if '$1 ~ /PRODUCT_BUNDLE_IDENTIFIER$/' in text:
        fail("validation workflow can confuse the Mac Catalyst derivation flag with the Bundle ID")
    if text.count('key == "PRODUCT_BUNDLE_IDENTIFIER"') != 2:
        fail("validation workflow does not parse the exact Xcode Bundle-ID build setting")
    required_apple_toolchain = (
        "/Applications/Xcode_26.3.app/Contents/Developer",
        'test "$(sed -n \'1p\' build/ci-evidence/xcode-version.txt)" = "Xcode 26.3"',
        'test "$(sed -n \'2p\' build/ci-evidence/xcode-version.txt)" = "Build version 17C529"',
        ".toolchains.apple.minimum_ios_sdk_major",
    )
    for token in required_apple_toolchain:
        if token not in text:
            fail(f"validation workflow does not pin the reviewed Apple toolchain: {token!r}")


def verify_store_workflow(name: str, text: str) -> None:
    if "workflow_dispatch:" not in text:
        fail(f"{name}: Store mutation must require workflow_dispatch")
    if re.search(r"(?m)^\s{0,4}(push|pull_request):", text):
        fail(f"{name}: Store workflow must never run automatically from push/PR")
    if "cancel-in-progress: false" not in text:
        fail(f"{name}: Store mutation concurrency must not cancel in progress")
    if "environment:" not in text:
        fail(f"{name}: Store jobs must use protected environments")
    if "validation_only" not in text and "publish" not in text and "execute" not in text:
        fail(f"{name}: Store workflow lacks explicit mutation mode")
    if "assert-store-identity-approved" not in text:
        fail(f"{name}: Store workflow does not fail closed on unverified Store identity ownership")
    preflight = text.split("\n  android:", 1)[0]
    if "secrets." in preflight:
        fail(f"{name}: preflight code can access Store credentials before protected-environment approval")


def verify_store_serialization(files: dict[str, str]) -> None:
    android_group = "group: parlor-google-play-production-identity"
    ios_group = "group: parlor-app-store-connect-production-identity"
    for name in STORE_WORKFLOWS:
        text = files[name]
        if text.count(android_group) != 1 or text.count(ios_group) != 1:
            fail(f"{name}: Store jobs do not share the reviewed cross-workflow platform locks")


def verify_promotions(name: str, text: str) -> None:
    forbidden_build_tokens = (
        "./gradlew",
        "gradle ",
        "xcodebuild",
        "bundleRelease",
        "assembleRelease",
        "build_ios_candidate",
        "jarsigner",
        "codesign",
    )
    for token in forbidden_build_tokens:
        if token in text:
            fail(f"{name}: promotion workflow contains build/sign command {token!r}")
    if "fetch-artifact" not in text or "verify-source" not in text:
        fail(f"{name}: promotion does not load provenance and verify source")
    if "candidate_run_id" not in text or "candidate_run_attempt" not in text:
        fail(f"{name}: candidate selection is not exact")
    required_attestation_policy = (
        "gh attestation verify build/release-promotion/candidate-manifest.json",
        "--signer-workflow",
        "--source-digest",
        "--deny-self-hosted-runners",
    )
    for token in required_attestation_policy:
        if token not in text:
            fail(f"{name}: candidate provenance verification lacks {token!r}")
    required_candidate_run_binding = (
        "workflow-run-record",
        "--expected-workflow .github/workflows/testing-candidate.yml",
        "--expected-branch testing",
        "candidate-workflow-run.json",
    )
    for token in required_candidate_run_binding:
        if token not in text:
            fail(f"{name}: candidate evidence is not bound to its exact workflow attempt: {token!r}")


def verify_production(text: str) -> None:
    if "platform:" not in text or not all(value in text for value in ("android", "ios", "both")):
        fail("production workflow lacks explicit android/ios/both selection")
    if "refs/heads/release" not in text:
        fail("production workflow does not enforce the release branch")
    if "production-android" not in text or "production-ios" not in text:
        fail("production platforms do not have independent protected environments")
    if "external-receipt" not in text or "external_run_id" not in text:
        fail("production workflow does not require external-testing evidence")
    required_external_run_binding = (
        "workflow-run-record",
        "--expected-workflow .github/workflows/testing-external-promotion.yml",
        "--expected-branch testing",
        "external_source_sha",
    )
    for token in required_external_run_binding:
        if token not in text:
            fail(f"production workflow does not bind external evidence to its workflow run: {token!r}")
    marker = "gh attestation verify build/release-promotion/external-evidence.json"
    if marker not in text:
        fail("production workflow does not verify canonical external-testing evidence")
    evidence_block = text[text.index(marker) : text.index(marker) + 500]
    if '--source-digest "$external_source_sha"' not in evidence_block:
        fail("production workflow verifies external evidence against the wrong workflow source")
    for token in (
        "validate-external-evidence",
        "external_evidence_b64",
        "--require-platform android",
        "--require-platform ios",
    ):
        if token not in text:
            fail(f"production workflow does not consume canonical external evidence: {token!r}")
    for receipt in ("production-android-receipt.json", "production-ios-receipt.json"):
        marker = f"subject-path: build/release-promotion/{receipt}"
        if marker not in text:
            fail(f"production workflow does not attest {receipt}")


def verify_candidate(text: str) -> None:
    if "group: parlor-store-candidate-claim" not in text:
        fail("candidate workflow does not serialize the claim check/create transaction")
    if "group: parlor-store-candidate-${{ inputs.candidate_sha }}" in text:
        fail("candidate claim lock is incorrectly scoped per SHA and permits build-number races")
    if "refs/heads/testing" not in text:
        fail("candidate workflow does not enforce the testing branch")
    preflight = text[text.index("  preflight:") : text.index("  android:")]
    if "environment: testing-candidate" not in preflight:
        fail("candidate claim is not protected by the testing-candidate environment")
    if "secrets." in preflight:
        fail("candidate control/preflight job must not receive or reference Store secrets")
    verify_android_runtime_smoke("testing-candidate.yml preflight", preflight)
    if "candidate_sha" not in text or "^[0-9a-f]{40}$" not in text:
        fail("candidate workflow does not require an exact full commit SHA")
    if "testing-android" not in text or "testing-ios" not in text:
        fail("candidate platforms do not have independent protected environments")
    if "attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8" not in text:
        fail("candidate workflow does not create pinned GitHub artifact attestations")
    if "retention-days: 90" not in text:
        fail("candidate evidence retention is shorter than the release policy")
    if "--max-filesize 209715200" not in text:
        fail("candidate bundletool download is not bounded")
    if "--configuration releaseRuntimeClasspath" not in text:
        fail("candidate dependency evidence is not scoped to the Android release runtime")
    if "Retain Android dependency and deep-validation evidence" not in text:
        fail("candidate workflow does not retain Android dependency/validation evidence")
    required_apple_toolchain = (
        "/Applications/Xcode_26.3.app/Contents/Developer",
        'test "$(xcodebuild -version | sed -n \'1p\')" = "Xcode 26.3"',
        'test "$(xcodebuild -version | sed -n \'2p\')" = "Build version 17C529"',
        ".toolchains.apple.minimum_ios_sdk_major",
    )
    for token in required_apple_toolchain:
        if token not in text:
            fail(f"candidate workflow does not pin the reviewed Apple toolchain: {token!r}")
    required_recovery = (
        "assert-candidate-claim-exclusive",
        "create-candidate-claim",
        "parlor-candidate-claim-build-",
        "google-check-unique",
        "Reject a reused App Store build number before compiling or signing",
        "fetch-binary-artifact",
        "assert-artifact-prefix-absent",
        "gh attestation verify",
        "android-descriptor-attempt-",
        "ios-descriptor-attempt-",
        "ios-upload-transport-attempt-",
        "android-mutation-intent-attempt-",
        "ios-mutation-intent-attempt-",
        "validate-mutation-intent",
        "--recover-only",
        "Refuse an indeterminate Apple upload instead of sending the IPA twice",
        "validate-apple-upload-transport",
        "--source-digest \"$CANDIDATE_SHA\"",
        "--deny-self-hosted-runners",
    )
    for token in required_recovery:
        if token not in text:
            fail(f"candidate workflow lacks build-once recovery control {token!r}")
    android_checkpoint = text.index("Checkpoint immutable Android artifact descriptor")
    android_intent = text.index("Checkpoint Android Store mutation intent")
    android_store = text.index("Upload once to Google Play internal testing")
    ios_checkpoint = text.index("Checkpoint immutable iOS artifact descriptor")
    ios_intent = text.index("Checkpoint Apple Store mutation intent")
    ios_store = text.index("Upload the exact IPA once")
    ios_transport = text.index("Checkpoint accepted Apple upload transport")
    ios_readback = text.index("Read back internal TestFlight state")
    claim = text.index("Reserve this version/build before either signed binary is created")
    android_unique = text.index("Reject a reused Google Play version before compiling or signing")
    android_build = text.index("Build and sign the only Android Store candidate")
    ios_unique = text.index("Reject a reused App Store build number before compiling or signing")
    ios_build = text.index("Create the only signed iOS archive and exported IPA")
    if not (
        claim < android_build
        and claim < ios_build
        and android_unique < android_build
        and ios_unique < ios_build
        and
        android_checkpoint < android_intent < android_store
        and ios_checkpoint < ios_intent < ios_store < ios_transport < ios_readback
    ):
        fail("candidate durable checkpoints are not ordered before their Store mutations/readback")


def verify_external_receipt_attestations(text: str) -> None:
    for receipt in ("external-android-receipt.json", "external-ios-receipt.json"):
        marker = f"subject-path: build/release-promotion/{receipt}"
        if marker not in text:
            fail(f"external-testing workflow does not attest {receipt}")
    if "Require actual external TestFlight availability" not in text or "available_to_external_testers" not in text:
        fail("external-testing workflow can succeed before the Apple build is externally available")
    for token in (
        "Seal one successful external-testing evidence record",
        "create-external-evidence",
        "parlor-external-evidence-",
        "subject-path: build/release-promotion/external-evidence.json",
    ):
        if token not in text:
            fail(f"external-testing workflow cannot safely aggregate partial rerun receipts: {token!r}")


def verify_policy() -> None:
    policy = json.loads((ROOT / "config" / "release-policy.json").read_text(encoding="utf-8"))
    android = policy["applications"]["android"]
    ios = policy["applications"]["ios"]
    android_identity_pattern = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")
    apple_identity_pattern = re.compile(r"^[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$")
    if not android_identity_pattern.fullmatch(str(android["store_application_id"])):
        fail("release policy has an invalid Android Store identity")
    if not apple_identity_pattern.fullmatch(str(ios["store_bundle_id"])):
        fail("release policy has an invalid iOS Store identity")
    managed_device = policy.get("toolchains", {}).get("android_managed_device")
    expected_managed_device = {
        "device": "Pixel 2",
        "api_level": 35,
        "system_image_source": "google",
        "system_image_package": "system-images;android-35;google_apis;x86_64",
        "system_image_revision": "9",
        "abi": "x86_64",
    }
    if managed_device != expected_managed_device:
        fail("release policy does not pin the reviewed Android managed device")
    if android["debug_application_id"] == android["store_application_id"]:
        fail("Android Debug identity is not isolated")
    if ios["debug_bundle_id"] == ios["store_bundle_id"]:
        fail("iOS Debug identity is not isolated")
    for platform, application in (("android", android), ("ios", ios)):
        approval = application.get("store_identity_ownership")
        expected_fields = {"status", "reason", "verified_at", "verification_reference"}
        if not isinstance(approval, dict) or set(approval) != expected_fields:
            fail(f"{platform} Store identity ownership approval is malformed")
        if approval["status"] not in {"blocked", "verified"}:
            fail(f"{platform} Store identity ownership status is invalid")
        if approval["status"] == "blocked":
            if not isinstance(approval["reason"], str) or not approval["reason"]:
                fail(f"{platform} blocked Store identity lacks a reason")
            if approval["verified_at"] is not None or approval["verification_reference"] is not None:
                fail(f"{platform} blocked Store identity contains false verification evidence")
        else:
            if approval["reason"] is not None:
                fail(f"{platform} verified Store identity retains a blocking reason")
            if not isinstance(approval["verified_at"], str) or not approval["verified_at"]:
                fail(f"{platform} verified Store identity lacks a timestamp")
            reference = approval["verification_reference"]
            if not isinstance(reference, str) or not reference or len(reference) > 512:
                fail(f"{platform} verified Store identity lacks bounded evidence")
    # Both public Stores currently assign this reverse-DNS identifier to an
    # unrelated application. It may never be approved by changing only a flag.
    if android["store_application_id"] == "com.parlor.app" and android["store_identity_ownership"]["status"] != "blocked":
        fail("known-colliding Android Store identity was marked verified")
    if ios["store_bundle_id"] == "com.parlor.app" and ios["store_identity_ownership"]["status"] != "blocked":
        fail("known-colliding iOS Store identity was marked verified")
    manifest_schema = json.loads((ROOT / "config/candidate-manifest.schema.json").read_text(encoding="utf-8"))
    manifest_applications = manifest_schema["properties"]["applications"]["properties"]
    google_receipt = manifest_schema["$defs"]["googleReceipt"]["allOf"][1]["properties"]
    apple_receipt = manifest_schema["$defs"]["appleReceipt"]["allOf"][1]["properties"]
    if manifest_applications["android_application_id"].get("const") != android["store_application_id"]:
        fail("candidate manifest schema Android identity drifted from release policy")
    if manifest_applications["ios_bundle_id"].get("const") != ios["store_bundle_id"]:
        fail("candidate manifest schema iOS identity drifted from release policy")
    if google_receipt["package_name"].get("const") != android["store_application_id"]:
        fail("candidate manifest Google receipt identity drifted from release policy")
    if apple_receipt["bundle_id"].get("const") != ios["store_bundle_id"]:
        fail("candidate manifest Apple receipt identity drifted from release policy")
    expected_environments = {
        "candidate_control": "testing-candidate",
        "candidate_android": "testing-android",
        "candidate_ios": "testing-ios",
        "external_android": "external-testing-android",
        "external_ios": "external-testing-ios",
        "production_android": "production-android",
        "production_ios": "production-ios",
    }
    if policy.get("github", {}).get("environments") != expected_environments:
        fail("protected release environments differ from the reviewed authorization model")
    apple_toolchain = policy.get("toolchains", {}).get("apple", {})
    if apple_toolchain != {
        "xcode_version": "26.3",
        "xcode_build": "17C529",
        "developer_dir": "/Applications/Xcode_26.3.app/Contents/Developer",
        "minimum_ios_sdk_major": 26,
        "deployment_target": "16.0",
    }:
        fail("Apple Store toolchain policy differs from the reviewed Xcode/SDK/deployment contract")
    bundletool = policy["tools"]["bundletool"]
    if not bundletool["url"].startswith("https://github.com/google/bundletool/releases/download/"):
        fail("bundletool URL is not the official release location")
    if not re.fullmatch(r"[0-9a-f]{64}", bundletool["sha256"]):
        fail("bundletool is not pinned by SHA-256")
    expected_platforms = {"linux-amd64", "linux-arm64", "darwin-amd64", "darwin-arm64"}
    for tool in ("actionlint", "shellcheck"):
        artifacts = policy["tools"][tool].get("artifacts", {})
        if set(artifacts) != expected_platforms:
            fail(f"{tool} does not pin every supported CI host architecture")
        for platform, release in artifacts.items():
            owner_path = "rhysd/actionlint" if tool == "actionlint" else "koalaman/shellcheck"
            expected_prefix = f"https://github.com/{owner_path}/releases/download/"
            if not str(release.get("url", "")).startswith(expected_prefix):
                fail(f"{tool} {platform} URL is not an official HTTPS GitHub release")
            if not re.fullmatch(r"[0-9a-f]{64}", str(release.get("sha256", ""))):
                fail(f"{tool} {platform} is not pinned by SHA-256")
    expected_limits = {
        "control_artifact_bytes": 1_048_576,
        "android_artifact_bytes": 536_870_912,
        "ios_artifact_bytes": 2_147_483_648,
        "github_binary_archive_bytes": 2_147_483_648,
        "archive_entry_count": 100_000,
    }
    if policy.get("limits") != expected_limits:
        fail("release artifact safety limits differ from the reviewed policy")


def verify_tool_downloader(script: str) -> None:
    if re.search(r"\|\|\s+return(?:\s|$)", script):
        fail("release-tool download/extraction can lose the failing command status")
    if script.count("return 2") != 3:
        fail("release-tool download/extraction can lose the failing command status")
    for token in (
        "could not download pinned $tool release",
        "pinned $tool release digest mismatch",
        "could not safely extract pinned $tool release",
    ):
        if token not in script:
            fail(f"release-tool downloader is not fail-closed: {token!r}")


def verify_signing_scripts() -> None:
    build = (ROOT / "scripts" / "release" / "build_ios_candidate.sh").read_text(encoding="utf-8")
    validator = (ROOT / "scripts" / "release" / "validate_ios_artifact.sh").read_text(encoding="utf-8")
    store_client = (ROOT / "scripts" / "release" / "store_api.py").read_text(encoding="utf-8")
    release_tool = (ROOT / "scripts" / "release" / "release_tool.py").read_text(encoding="utf-8")
    release_system_validator = (
        ROOT / "scripts" / "release" / "validate_release_system.sh"
    ).read_text(encoding="utf-8")
    verify_tool_downloader(release_system_validator)
    for name, script in (
        ("build_ios_candidate.sh", build),
        (
            "upload_ios_candidate.sh",
            (ROOT / "scripts" / "release" / "upload_ios_candidate.sh").read_text(encoding="utf-8"),
        ),
    ):
        if "assert-store-identity-approved --platform ios" not in script:
            fail(f"{name} can use Apple signing/Store credentials before identity ownership approval")
    cleanup_contract = (
        "cleanup_status=0",
        "could not restore the original user keychain search list",
        "could not restore the original default keychain",
        "could not delete the ephemeral signing keychain",
        "trap 'cleanup $?' EXIT",
        "trap 'exit 130' INT",
        "trap 'exit 143' TERM",
        'exit "$cleanup_status"',
    )
    for token in cleanup_contract:
        if token not in build:
            fail(f"iOS signing cleanup is not fail-closed: {token!r}")
    for token in (
        "unset PARLOR_APPLE_CERTIFICATE_PASSWORD",
        "unset PARLOR_APPLE_CERTIFICATE_P12_PATH",
        "unset PARLOR_APPLE_PROFILE_PATH",
    ):
        if token not in build:
            fail(f"iOS signing source secret remains visible to Xcode: {token!r}")
    validator_contract = (
        '"toolchains"]["apple"]["xcode_build"',
        '"toolchains"]["apple"]["minimum_ios_sdk_major"',
        '"toolchains"]["apple"]["deployment_target"',
        "DTXcodeBuild",
        "DTSDKName",
        "MinimumOSVersion",
        '"xcrun", "vtool", "-show-build"',
        'platform != "IOS"',
        "macho_minimum_os_versions",
    )
    for token in validator_contract:
        if token not in validator:
            fail(f"iOS artifact validation lacks the release-toolchain contract: {token!r}")
    for token in (
        "require_internal_candidate_readback",
        "store_sha256_readback",
        "store_bundle_sha256",
        "Google Play reported a SHA-256 that differs from the uploaded candidate",
    ):
        if token not in store_client and token not in release_tool:
            fail(f"Google Play partial-success recovery lacks exact digest evidence: {token!r}")
    if 'urllib.parse.urlencode({"filter[builds]": build_id, "limit": "200"})' not in store_client:
        fail("App Store Connect beta-group readback does not use the supported build filter")
    if '/builds/{quote(build_id)}/betaGroups' in store_client:
        fail("App Store Connect client uses a nonexistent build beta-groups endpoint")
    if '/betaGroups/{quote(group_id)}/relationships/app' not in store_client:
        fail("App Store Connect beta-group validation lacks authoritative app linkage readback")
    for token in (
        "validate_store_eligible_apple_build",
        "APP_STORE_ELIGIBLE",
        "usesNonExemptEncryption",
        'attributes.get("expired") is not False',
    ):
        if token not in store_client:
            fail(f"App Store Connect build eligibility readback is incomplete: {token!r}")
    for name in (
        "upload_ios_candidate.sh",
        "validate_android_artifact.sh",
        "validate_ios_artifact.sh",
        "validate_release_system.sh",
    ):
        script = (ROOT / "scripts" / "release" / name).read_text(encoding="utf-8")
        for token in ("trap 'cleanup $?' EXIT", "trap 'exit 130' INT", "trap 'exit 143' TERM"):
            if token not in script:
                fail(f"{name} does not preserve exit/signal status during cleanup: {token!r}")
    upload = (ROOT / "scripts" / "release" / "upload_ios_candidate.sh").read_text(encoding="utf-8")
    if 'export API_PRIVATE_KEYS_DIR="$temporary_dir/private_keys"' not in upload or "export HOME=" in upload:
        fail("Apple upload does not use the supported scoped API-key directory")
    if 'rm -f "$raw_log"' not in upload:
        fail("Apple upload can retain its raw response after completion")


def verify_android_runtime_script() -> None:
    script = (ROOT / "scripts" / "android" / "run_release_managed_device_smoke.sh").read_text(
        encoding="utf-8"
    )
    required = (
        "productionAndroidRuntimeCheck",
        "--dependency-verification=strict",
        "--no-daemon",
        "--max-workers=2",
        "android.injected.signing.store.file",
        "android.injected.signing.store.password",
        "android.injected.signing.key.alias",
        "android.injected.signing.key.password",
        "trap 'cleanup $?' EXIT",
        "trap 'exit 130' INT",
        "trap 'exit 143' TERM",
        '"$repo_root/gradlew" --stop',
    )
    for token in required:
        if token not in script:
            fail(f"Android managed-device runner is missing {token!r}")
    for production_secret in (
        "PARLOR_ANDROID_KEYSTORE_PATH",
        "PARLOR_ANDROID_KEYSTORE_PASSWORD",
        "PARLOR_ANDROID_KEY_ALIAS",
        "PARLOR_ANDROID_KEY_PASSWORD",
    ):
        if production_secret in script:
            fail("Android managed-device runner must not consume production signing material")


def main() -> int:
    files = load_files()
    verify_policy()
    verify_signing_scripts()
    verify_android_runtime_script()
    for name, text in files.items():
        verify_common(name, text)
    verify_validation(files["production-verification.yml"])
    for name in STORE_WORKFLOWS:
        verify_store_workflow(name, files[name])
    verify_store_serialization(files)
    for name in PROMOTION_WORKFLOWS:
        verify_promotions(name, files[name])
    verify_candidate(files["testing-candidate.yml"])
    verify_external_receipt_attestations(files["testing-external-promotion.yml"])
    verify_production(files["production-promotion.yml"])
    print("release workflow contract: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"release workflow contract: FAIL: {error}", file=sys.stderr)
        raise SystemExit(2)
