#!/usr/bin/env python3
"""Fail-closed provenance and candidate-manifest tooling for Parlor releases."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = ROOT / "config" / "release-policy.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
ANDROID_APPLICATION_ID_RE = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")
APPLE_BUNDLE_ID_RE = re.compile(r"^[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$")
MAX_CONTROL_ARTIFACT_BYTES = 1024 * 1024
MAX_BINARY_ARTIFACT_BYTES = 2 * 1024 * 1024 * 1024


class ReleaseError(RuntimeError):
    """A release invariant failed without exposing a sensitive value."""


def fail(message: str) -> None:
    raise ReleaseError(message)


def run_git(*args: str, cwd: Path | None = None, check: bool = True) -> str:
    working_directory = ROOT if cwd is None else cwd
    process = subprocess.run(
        ["git", *args],
        cwd=working_directory,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and process.returncode != 0:
        fail(f"Git command failed: git {' '.join(args)}")
    return process.stdout.strip()


def load_json(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_text(encoding="utf-8")
        value = json.loads(raw)
    except (OSError, json.JSONDecodeError) as error:
        fail(f"Cannot read valid JSON from {path}: {type(error).__name__}")
    if not isinstance(value, dict):
        fail(f"Expected a JSON object in {path}")
    return value


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.parent.is_symlink():
        fail("Refusing to write release evidence through a symbolic-link directory")
    data = (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=True) + "\n").encode()
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temporary:
        temporary.write(data)
        temporary.flush()
        os.fsync(temporary.fileno())
        temporary_path = Path(temporary.name)
    try:
        os.chmod(temporary_path, 0o600)
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def atomic_write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.parent.is_symlink():
        fail("Refusing to write release evidence through a symbolic-link directory")
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temporary:
        temporary.write(data)
        temporary.flush()
        os.fsync(temporary.fileno())
        temporary_path = Path(temporary.name)
    try:
        os.chmod(temporary_path, 0o600)
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def require_keys(value: dict[str, Any], required: set[str], allowed: set[str], label: str) -> None:
    missing = required - value.keys()
    extra = value.keys() - allowed
    if missing:
        fail(f"{label} is missing fields: {', '.join(sorted(missing))}")
    if extra:
        fail(f"{label} contains unsupported fields: {', '.join(sorted(extra))}")


def require_sha(value: Any, label: str) -> str:
    normalized = str(value).lower()
    if not SHA_RE.fullmatch(normalized):
        fail(f"{label} must be a full lowercase 40-character Git SHA")
    return normalized


def require_sha256(value: Any, label: str) -> str:
    normalized = str(value).strip().lower()
    if normalized.startswith("sha256:"):
        normalized = normalized.removeprefix("sha256:")
    normalized = normalized.replace(":", "")
    if not SHA256_RE.fullmatch(normalized):
        fail(f"{label} must be a 64-character SHA-256 digest")
    return normalized


def require_positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool):
        fail(f"{label} must be a positive integer")
    try:
        number = int(value)
    except (TypeError, ValueError):
        fail(f"{label} must be a positive integer")
    if number < 1 or str(number) != str(value):
        fail(f"{label} must be a canonical positive integer")
    return number


def parse_timestamp(value: Any, label: str) -> str:
    text = str(value)
    try:
        parsed = dt.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        fail(f"{label} must be an ISO-8601 timestamp")
    if parsed.tzinfo is None:
        fail(f"{label} must include a timezone")
    return text


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def policy() -> dict[str, Any]:
    value = load_json(POLICY_PATH)
    if value.get("schema_version") != 1:
        fail("Unsupported release policy schema")
    return value


def assert_store_identity_approved(platform: str) -> None:
    """Fail before signing or Store access unless ownership was API-verified."""
    selected = ("android", "ios") if platform == "both" else (platform,)
    if any(item not in {"android", "ios"} for item in selected):
        fail("Store identity platform must be android, ios, or both")

    applications = policy().get("applications")
    if not isinstance(applications, dict):
        fail("Release policy does not define application identities")
    for item in selected:
        application = applications.get(item)
        if not isinstance(application, dict):
            fail(f"Release policy does not define the {item} application")
        identity_key = "store_application_id" if item == "android" else "store_bundle_id"
        identity = application.get(identity_key)
        identity_pattern = ANDROID_APPLICATION_ID_RE if item == "android" else APPLE_BUNDLE_ID_RE
        if not isinstance(identity, str) or not identity_pattern.fullmatch(identity):
            fail(f"{item} Store identity has an invalid format")
        if identity == "com.parlor.app":
            fail(f"{item} Store identity has a known public Store collision")
        approval = application.get("store_identity_ownership")
        if not isinstance(approval, dict):
            fail(f"{item} Store identity ownership approval is missing")
        require_keys(
            approval,
            {"status", "reason", "verified_at", "verification_reference"},
            {"status", "reason", "verified_at", "verification_reference"},
            f"{item} Store identity ownership approval",
        )
        if approval["status"] != "verified":
            fail(f"{item} Store identity ownership is not verified")
        if approval["reason"] is not None:
            fail(f"{item} verified Store identity must not retain a blocking reason")
        parse_timestamp(approval["verified_at"], f"{item} Store identity verification time")
        reference = approval["verification_reference"]
        if not isinstance(reference, str) or not reference.strip() or len(reference) > 512:
            fail(f"{item} Store identity verification reference is invalid")


def version_values() -> tuple[str, int]:
    configured_path = ROOT / policy()["version_source"]
    assignments: dict[str, str] = {}
    for line in configured_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("//"):
            continue
        if "=" not in stripped:
            fail(f"Invalid version assignment in {configured_path}")
        key, raw_value = (part.strip() for part in stripped.split("=", 1))
        if key in assignments:
            fail(f"Duplicate version key {key}")
        assignments[key] = raw_value
    if set(assignments) != {"PARLOR_VERSION_NAME", "PARLOR_BUILD_NUMBER"}:
        fail("Version source must contain exactly PARLOR_VERSION_NAME and PARLOR_BUILD_NUMBER")
    marketing = assignments["PARLOR_VERSION_NAME"]
    if not VERSION_RE.fullmatch(marketing):
        fail("PARLOR_VERSION_NAME must be a three-component numeric version")
    build = require_positive_int(assignments["PARLOR_BUILD_NUMBER"], "PARLOR_BUILD_NUMBER")
    return marketing, build


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def source_record(candidate_sha: str, repository_id: str, require_clean: bool) -> dict[str, Any]:
    candidate = require_sha(candidate_sha, "candidate SHA")
    if run_git("rev-parse", "HEAD") != candidate:
        fail("Candidate SHA does not equal the checked-out HEAD")
    if require_clean and run_git("status", "--porcelain=v1", "--untracked-files=all"):
        fail("Candidate checkout is not clean")
    tree = require_sha(run_git("rev-parse", f"{candidate}^{{tree}}"), "candidate tree SHA")
    repo_id = str(require_positive_int(repository_id, "repository ID"))
    marketing, build_number = version_values()
    configured = policy()
    return {
        "schema_version": 1,
        "repository": {
            "full_name": configured["repository"]["full_name"],
            "id": repo_id,
        },
        "source": {"commit_sha": candidate, "tree_sha": tree},
        "version": {
            "marketing_version": marketing,
            "android_version_code": build_number,
            "ios_build_number": str(build_number),
        },
        "applications": {
            "android_application_id": configured["applications"]["android"]["store_application_id"],
            "ios_bundle_id": configured["applications"]["ios"]["store_bundle_id"],
        },
    }


def validate_source_record(value: dict[str, Any]) -> None:
    require_keys(
        value,
        {"schema_version", "repository", "source", "version", "applications"},
        {"schema_version", "repository", "source", "version", "applications"},
        "source record",
    )
    if value["schema_version"] != 1:
        fail("Unsupported source-record schema")
    configured = policy()
    repository = value["repository"]
    require_keys(repository, {"full_name", "id"}, {"full_name", "id"}, "repository identity")
    if repository["full_name"] != configured["repository"]["full_name"]:
        fail("Repository name does not match release policy")
    require_positive_int(repository["id"], "repository ID")
    source = value["source"]
    require_keys(source, {"commit_sha", "tree_sha"}, {"commit_sha", "tree_sha"}, "source")
    require_sha(source["commit_sha"], "candidate SHA")
    require_sha(source["tree_sha"], "tree SHA")
    version = value["version"]
    require_keys(
        version,
        {"marketing_version", "android_version_code", "ios_build_number"},
        {"marketing_version", "android_version_code", "ios_build_number"},
        "version",
    )
    if not VERSION_RE.fullmatch(str(version["marketing_version"])):
        fail("Invalid marketing version")
    android_build = require_positive_int(version["android_version_code"], "Android version code")
    ios_build = require_positive_int(version["ios_build_number"], "iOS build number")
    if android_build != ios_build:
        fail("Android version code and iOS build number must match the shared version source")
    applications = value["applications"]
    require_keys(
        applications,
        {"android_application_id", "ios_bundle_id"},
        {"android_application_id", "ios_bundle_id"},
        "application identities",
    )
    if applications["android_application_id"] != configured["applications"]["android"]["store_application_id"]:
        fail("Android application ID is not the canonical Store identity")
    if applications["ios_bundle_id"] != configured["applications"]["ios"]["store_bundle_id"]:
        fail("iOS bundle ID is not the canonical Store identity")


def candidate_claim(
    source: dict[str, Any],
    run_id: Any,
    run_attempt: Any,
    created_at: str,
) -> dict[str, Any]:
    """Reserve one reviewed Store build number before signed bytes are created."""
    validate_source_record(source)
    return {
        "schema_version": 1,
        "repository": source["repository"],
        "candidate_commit_sha": source["source"]["commit_sha"],
        "candidate_tree_sha": source["source"]["tree_sha"],
        "marketing_version": source["version"]["marketing_version"],
        "android_version_code": source["version"]["android_version_code"],
        "ios_build_number": source["version"]["ios_build_number"],
        "workflow_run_id": str(require_positive_int(run_id, "workflow run ID")),
        "workflow_run_attempt": require_positive_int(run_attempt, "workflow run attempt"),
        "created_at": parse_timestamp(created_at, "candidate claim time"),
    }


def validate_artifact_descriptor(value: dict[str, Any], expected_platform: str | None = None) -> None:
    allowed = {
        "schema_version",
        "platform",
        "candidate_commit_sha",
        "identity",
        "marketing_version",
        "build_number",
        "filename",
        "size_bytes",
        "sha256",
        "signing_fingerprint_sha256",
        "github_artifact",
        "attestation",
        "validation",
    }
    require_keys(value, allowed, allowed, "artifact descriptor")
    if value["schema_version"] != 1:
        fail("Unsupported artifact descriptor schema")
    platform = value["platform"]
    if platform not in {"android", "ios"} or (expected_platform and platform != expected_platform):
        fail("Artifact descriptor platform mismatch")
    require_sha(value["candidate_commit_sha"], "artifact candidate SHA")
    require_positive_int(value["build_number"], "artifact build number")
    if not VERSION_RE.fullmatch(str(value["marketing_version"])):
        fail("Artifact marketing version is invalid")
    filename = str(value["filename"])
    if not filename or filename != Path(filename).name:
        fail("Artifact filename must be a plain filename")
    expected_suffix = ".aab" if platform == "android" else ".ipa"
    if not filename.lower().endswith(expected_suffix):
        fail(f"{platform} artifact filename has the wrong extension")
    size = require_positive_int(value["size_bytes"], "artifact size")
    limit_key = "android_artifact_bytes" if platform == "android" else "ios_artifact_bytes"
    if size > require_positive_int(policy()["limits"][limit_key], f"{platform} artifact limit"):
        fail(f"{platform} artifact exceeds the release-policy size bound")
    require_sha256(value["sha256"], "artifact SHA-256")
    require_sha256(value["signing_fingerprint_sha256"], "signing fingerprint")
    configured = policy()["applications"][platform]
    expected_identity = configured["store_application_id"] if platform == "android" else configured["store_bundle_id"]
    if value["identity"] != expected_identity:
        fail(f"{platform} artifact uses a non-Store identity")
    github_artifact = value["github_artifact"]
    require_keys(
        github_artifact,
        {"id", "name", "url", "archive_sha256"},
        {"id", "name", "url", "archive_sha256"},
        "GitHub artifact",
    )
    require_positive_int(github_artifact["id"], "GitHub artifact ID")
    artifact_name = str(github_artifact["name"])
    expected_name = re.compile(
        rf"^parlor-candidate-{re.escape(value['candidate_commit_sha'])}-{platform}-attempt-[1-9][0-9]*$"
    )
    if len(artifact_name) > 240 or not expected_name.fullmatch(artifact_name):
        fail("GitHub artifact name is invalid")
    expected_artifact_url = re.compile(
        rf"^https://github\.com/{re.escape(policy()['repository']['full_name'])}/actions/runs/[1-9][0-9]*/artifacts/"
        rf"{re.escape(str(github_artifact['id']))}$"
    )
    if not expected_artifact_url.fullmatch(str(github_artifact["url"])):
        fail("GitHub artifact URL is not a github.com URL")
    require_sha256(github_artifact["archive_sha256"], "GitHub artifact archive digest")
    attestation = value["attestation"]
    require_keys(attestation, {"id", "url"}, {"id", "url"}, "attestation")
    expected_attestation_prefix = f"https://github.com/{policy()['repository']['full_name']}/attestations/"
    if not str(attestation["id"]) or not str(attestation["url"]).startswith(expected_attestation_prefix):
        fail("Artifact attestation identity is invalid")
    if not isinstance(value["validation"], dict) or not value["validation"]:
        fail("Artifact validation evidence is missing")


def validate_receipt(value: dict[str, Any], platform: str, source: dict[str, Any], artifact: dict[str, Any]) -> None:
    common = {
        "schema_version",
        "platform",
        "operation",
        "candidate_commit_sha",
        "artifact_sha256",
        "state",
        "read_back_at",
    }
    platform_required = (
        {
            "package_name",
            "version_code",
            "track",
            "release_name",
            "release_status",
            "edit_id",
            "store_bundle_sha256",
            "upload_evidence",
            "resumed_without_upload",
        }
        if platform == "android"
        else {
            "bundle_id",
            "marketing_version",
            "build_number",
            "app_id",
            "build_id",
            "upload_request_id",
            "internal_group_id",
            "processing_state",
            "build_audience_type",
            "uses_non_exempt_encryption",
            "expired",
            "resumed_without_upload",
        }
    )
    allowed = common | platform_required
    require_keys(value, common | platform_required, allowed, f"{platform} Store receipt")
    if value["schema_version"] != 1 or value["platform"] != platform:
        fail(f"{platform} receipt schema or platform mismatch")
    if require_sha(value["candidate_commit_sha"], "receipt candidate SHA") != source["source"]["commit_sha"]:
        fail(f"{platform} receipt belongs to another candidate")
    if require_sha256(value["artifact_sha256"], "receipt artifact SHA-256") != artifact["sha256"]:
        fail(f"{platform} receipt belongs to different artifact bytes")
    parse_timestamp(value["read_back_at"], "receipt readback time")
    version = source["version"]
    applications = source["applications"]
    if platform == "android":
        if value.get("operation") != "internal_upload" or value.get("state") != "internal_track_committed":
            fail("Google Play receipt is not a committed internal-track upload")
        if value.get("package_name") != applications["android_application_id"]:
            fail("Google Play receipt package mismatch")
        if require_positive_int(value.get("version_code"), "receipt version code") != version["android_version_code"]:
            fail("Google Play receipt version-code mismatch")
        if value.get("track") != policy()["applications"]["android"]["internal_track"]:
            fail("Google Play receipt is not for the configured internal track")
        if value.get("release_status") != "completed" or not str(value.get("release_name", "")):
            fail("Google Play receipt lacks the committed release identity")
        if require_sha256(value.get("store_bundle_sha256"), "Store bundle SHA-256") != artifact["sha256"]:
            fail("Google Play receipt lacks exact Store bundle-digest evidence")
        upload_evidence = value.get("upload_evidence")
        if upload_evidence == "committed_edit":
            if not str(value.get("edit_id", "")):
                fail("Google Play committed-edit receipt lacks its edit identity")
        elif upload_evidence == "store_sha256_readback":
            if value.get("edit_id") is not None or value.get("resumed_without_upload") is not True:
                fail("Google Play digest-recovery receipt has inconsistent recovery evidence")
        else:
            fail("Google Play receipt has an unsupported upload-evidence mode")
    else:
        if value.get("operation") != "internal_upload" or value.get("state") != "available_to_internal_testers":
            fail("App Store Connect receipt is not available to the internal TestFlight group")
        if value.get("bundle_id") != applications["ios_bundle_id"]:
            fail("App Store Connect receipt bundle mismatch")
        if str(value.get("build_number")) != version["ios_build_number"]:
            fail("App Store Connect receipt build-number mismatch")
        if value.get("marketing_version") != version["marketing_version"]:
            fail("App Store Connect receipt marketing-version mismatch")
        if not str(value.get("build_id", "")):
            fail("App Store Connect receipt has no processed build ID")
        if not str(value.get("app_id", "")) or not str(value.get("internal_group_id", "")):
            fail("App Store Connect receipt lacks app/internal-group identity")
        if not str(value.get("upload_request_id", "")):
            fail("App Store Connect receipt lacks the accepted upload request identity")
        if value.get("processing_state") != "VALID":
            fail("App Store Connect receipt is not for a processed build")
        if value.get("build_audience_type") != "APP_STORE_ELIGIBLE":
            fail("App Store Connect receipt is not for an App Store eligible build")
        if not isinstance(value.get("uses_non_exempt_encryption"), bool):
            fail("App Store Connect receipt lacks a resolved export-compliance value")
        if value.get("expired") is not False:
            fail("App Store Connect receipt is for an expired or indeterminate build")
    if not isinstance(value.get("resumed_without_upload"), bool):
        fail(f"{platform} receipt has an invalid resume marker")


def build_manifest(
    source: dict[str, Any],
    android: dict[str, Any],
    ios: dict[str, Any],
    google: dict[str, Any],
    apple: dict[str, Any],
    run_id: str,
    run_attempt: int,
    created_at: str,
) -> dict[str, Any]:
    validate_source_record(source)
    validate_artifact_descriptor(android, "android")
    validate_artifact_descriptor(ios, "ios")
    for artifact in (android, ios):
        if artifact["candidate_commit_sha"] != source["source"]["commit_sha"]:
            fail("Artifact belongs to a different candidate commit")
        if artifact["marketing_version"] != source["version"]["marketing_version"]:
            fail("Artifact marketing version differs from source")
    if android["build_number"] != source["version"]["android_version_code"]:
        fail("Android artifact version code differs from source")
    if str(ios["build_number"]) != source["version"]["ios_build_number"]:
        fail("iOS artifact build number differs from source")
    validate_receipt(google, "android", source, android)
    validate_receipt(apple, "ios", source, ios)
    return {
        "schema_version": 1,
        "repository": source["repository"],
        "source": source["source"],
        "version": source["version"],
        "build": {
            "workflow_run_id": str(require_positive_int(run_id, "workflow run ID")),
            "workflow_run_attempt": require_positive_int(run_attempt, "workflow run attempt"),
            "created_at": parse_timestamp(created_at, "candidate creation time"),
        },
        "applications": source["applications"],
        "artifacts": {"android": strip_descriptor(android), "ios": strip_descriptor(ios)},
        "stores": {"google_play": google, "app_store_connect": apple},
    }


def strip_descriptor(value: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value[key]
        for key in (
            "filename",
            "size_bytes",
            "sha256",
            "signing_fingerprint_sha256",
            "github_artifact",
            "attestation",
        )
    }


def validate_manifest(value: dict[str, Any]) -> None:
    required = {"schema_version", "repository", "source", "version", "build", "applications", "artifacts", "stores"}
    require_keys(value, required, required, "candidate manifest")
    if value["schema_version"] != 1:
        fail("Unsupported candidate manifest schema")
    source = {key: value[key] for key in ("schema_version", "repository", "source", "version", "applications")}
    validate_source_record(source)
    build = value["build"]
    require_keys(
        build,
        {"workflow_run_id", "workflow_run_attempt", "created_at"},
        {"workflow_run_id", "workflow_run_attempt", "created_at"},
        "candidate build",
    )
    require_positive_int(build["workflow_run_id"], "workflow run ID")
    require_positive_int(build["workflow_run_attempt"], "workflow run attempt")
    parse_timestamp(build["created_at"], "candidate creation time")
    artifacts = value["artifacts"]
    require_keys(artifacts, {"android", "ios"}, {"android", "ios"}, "candidate artifacts")
    stores = value["stores"]
    require_keys(stores, {"google_play", "app_store_connect"}, {"google_play", "app_store_connect"}, "candidate Store receipts")
    for platform, key in (("android", "google_play"), ("ios", "app_store_connect")):
        artifact = artifacts[platform]
        required_artifact = {
            "filename",
            "size_bytes",
            "sha256",
            "signing_fingerprint_sha256",
            "github_artifact",
            "attestation",
        }
        require_keys(artifact, required_artifact, required_artifact, f"{platform} artifact")
        synthetic = {
            "schema_version": 1,
            "platform": platform,
            "candidate_commit_sha": value["source"]["commit_sha"],
            "identity": value["applications"]["android_application_id" if platform == "android" else "ios_bundle_id"],
            "marketing_version": value["version"]["marketing_version"],
            "build_number": value["version"]["android_version_code" if platform == "android" else "ios_build_number"],
            **artifact,
            "validation": {"manifest": True},
        }
        validate_artifact_descriptor(synthetic, platform)
        validate_receipt(stores[key], platform, source, artifact)


def verify_source(manifest: dict[str, Any], expected_repository: str, expected_repository_id: str) -> str:
    validate_manifest(manifest)
    if manifest["repository"]["full_name"] != expected_repository:
        fail("Candidate manifest repository name does not match this workflow repository")
    if manifest["repository"]["id"] != str(require_positive_int(expected_repository_id, "repository ID")):
        fail("Candidate manifest repository ID does not match this workflow repository")
    if run_git("status", "--porcelain=v1", "--untracked-files=all"):
        fail("Promotion checkout is not clean")
    candidate = manifest["source"]["commit_sha"]
    expected_tree = manifest["source"]["tree_sha"]
    candidate_tree = require_sha(run_git("rev-parse", f"{candidate}^{{tree}}"), "candidate commit tree")
    if candidate_tree != expected_tree:
        fail("Recorded candidate tree does not match the candidate commit")
    head = require_sha(run_git("rev-parse", "HEAD"), "promotion HEAD")
    head_tree = require_sha(run_git("rev-parse", "HEAD^{tree}"), "promotion tree")
    if head_tree != expected_tree:
        fail("Promotion source tree differs from the tested candidate tree")
    if head == candidate:
        return "exact-commit"
    merge_base = run_git("merge-base", candidate, head, check=False)
    if not SHA_RE.fullmatch(merge_base):
        fail("Equal trees come from unrelated Git histories")
    return "shared-history-equal-tree"


def descriptor_from_args(args: argparse.Namespace) -> dict[str, Any]:
    artifact_path = Path(args.file).resolve()
    if not artifact_path.is_file() or artifact_path.is_symlink():
        fail("Artifact path must identify one regular, non-symlink file")
    validation = load_json(Path(args.validation))
    value = {
        "schema_version": 1,
        "platform": args.platform,
        "candidate_commit_sha": require_sha(args.candidate_sha, "candidate SHA"),
        "identity": args.identity,
        "marketing_version": args.marketing_version,
        "build_number": str(args.build_number) if args.platform == "ios" else int(args.build_number),
        "filename": artifact_path.name,
        "size_bytes": artifact_path.stat().st_size,
        "sha256": sha256_file(artifact_path),
        "signing_fingerprint_sha256": require_sha256(args.signing_fingerprint, "signing fingerprint"),
        "github_artifact": {
            "id": str(require_positive_int(args.github_artifact_id, "GitHub artifact ID")),
            "name": args.github_artifact_name,
            "url": args.github_artifact_url,
            "archive_sha256": require_sha256(args.github_artifact_digest, "GitHub artifact archive digest"),
        },
        "attestation": {"id": args.attestation_id, "url": args.attestation_url},
        "validation": validation,
    }
    validate_artifact_descriptor(value, args.platform)
    return value


class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Follow GitHub's signed artifact redirect without forwarding its token."""

    def redirect_request(self, request: Any, file_pointer: Any, code: int, message: str, headers: Any, new_url: str) -> Any:
        parsed = urllib.parse.urlparse(new_url)
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
        ):
            return None
        redirected = super().redirect_request(request, file_pointer, code, message, headers, new_url)
        if redirected is not None:
            old_host = urllib.parse.urlparse(request.full_url).hostname
            new_host = parsed.hostname
            if old_host != new_host:
                redirected.remove_header("Authorization")
        return redirected


def github_opener() -> Any:
    return urllib.request.build_opener(SafeRedirectHandler())


def github_api_request(url: str, token: str) -> Any:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "parlor-release-provenance/1",
        },
    )
    try:
        return github_opener().open(request, timeout=60)
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        fail(f"GitHub API returned HTTP {status}")
    except urllib.error.URLError:
        fail("GitHub API request failed")


def validate_github_coordinates(repository: str, run_id: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        fail("GitHub repository must be an owner/name pair")
    require_positive_int(run_id, "workflow run ID")


def workflow_run_record(
    repository: str,
    repository_id: str,
    run_id: str,
    run_attempt: str,
    expected_workflow: str,
    expected_branch: str,
    token: str,
) -> dict[str, Any]:
    """Bind downloaded evidence to one successful protected workflow run.

    Artifact names and receipt contents are not enough on their own: the
    attestation's source digest is the dispatch commit of the workflow that
    produced the receipt, which can legitimately differ from the older app
    candidate commit.  Record and validate that source explicitly.
    """
    validate_github_coordinates(repository, run_id)
    repo_id = str(require_positive_int(repository_id, "repository ID"))
    attempt = require_positive_int(run_attempt, "workflow run attempt")
    if not re.fullmatch(r"\.github/workflows/[A-Za-z0-9_.-]+\.ya?ml", expected_workflow):
        fail("Expected workflow path is invalid")
    if not re.fullmatch(r"[A-Za-z0-9._/-]+", expected_branch) or expected_branch.startswith("/"):
        fail("Expected workflow branch is invalid")
    try:
        value = json.loads(
            github_request(
                f"https://api.github.com/repos/{repository}/actions/runs/"
                f"{require_positive_int(run_id, 'workflow run ID')}/attempts/{attempt}",
                token,
            )
        )
    except json.JSONDecodeError:
        fail("GitHub returned malformed workflow-run JSON")
    if not isinstance(value, dict):
        fail("GitHub returned an invalid workflow-run record")
    if require_positive_int(value.get("id"), "GitHub workflow run ID") != int(run_id):
        fail("GitHub workflow run ID differs from the selected run")
    if require_positive_int(value.get("run_attempt"), "GitHub workflow run attempt") != attempt:
        fail("GitHub workflow run attempt differs from the selected attempt")
    if value.get("event") != "workflow_dispatch":
        fail("Selected Store evidence was not produced by a manual workflow dispatch")
    if value.get("status") != "completed" or value.get("conclusion") != "success":
        fail("Selected Store workflow run is not completed successfully")
    if value.get("head_branch") != expected_branch:
        fail("Selected Store workflow ran from the wrong protected branch")
    workflow_path = str(value.get("path", "")).split("@", 1)[0]
    if workflow_path != expected_workflow:
        fail("Selected Store evidence was produced by a different workflow")
    head_repository = value.get("head_repository")
    if not isinstance(head_repository, dict):
        fail("Selected Store workflow has no source-repository identity")
    if head_repository.get("full_name") != repository:
        fail("Selected Store workflow came from another repository")
    if str(require_positive_int(head_repository.get("id"), "workflow source repository ID")) != repo_id:
        fail("Selected Store workflow repository ID differs from this repository")
    return {
        "schema_version": 1,
        "repository": {"full_name": repository, "id": repo_id},
        "workflow_run_id": str(int(run_id)),
        "workflow_run_attempt": attempt,
        "workflow_path": workflow_path,
        "event": "workflow_dispatch",
        "head_branch": expected_branch,
        "head_sha": require_sha(value.get("head_sha"), "workflow source SHA"),
        "status": "completed",
        "conclusion": "success",
    }


def list_run_artifacts(repository: str, run_id: str, token: str) -> list[dict[str, Any]]:
    validate_github_coordinates(repository, run_id)
    result: list[dict[str, Any]] = []
    for page in range(1, 11):
        listing_url = (
            f"https://api.github.com/repos/{repository}/actions/runs/{run_id}/artifacts"
            f"?per_page=100&page={page}"
        )
        try:
            listing = json.loads(github_request(listing_url, token))
        except json.JSONDecodeError:
            fail("GitHub returned malformed artifact-list JSON")
        artifacts = listing.get("artifacts", [])
        if not isinstance(artifacts, list) or any(not isinstance(item, dict) for item in artifacts):
            fail("GitHub returned an invalid artifact list")
        result.extend(artifacts)
        if len(artifacts) < 100:
            return result
    fail("GitHub workflow run exceeds the reviewed 1,000-artifact recovery bound")


def list_repository_artifacts(repository: str, token: str) -> list[dict[str, Any]]:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        fail("GitHub repository name is invalid")
    result: list[dict[str, Any]] = []
    for page in range(1, 11):
        listing_url = (
            f"https://api.github.com/repos/{repository}/actions/artifacts"
            f"?per_page=100&page={page}"
        )
        try:
            listing = json.loads(github_request(listing_url, token))
        except json.JSONDecodeError:
            fail("GitHub returned malformed repository artifact-list JSON")
        artifacts = listing.get("artifacts", [])
        if not isinstance(artifacts, list) or any(not isinstance(item, dict) for item in artifacts):
            fail("GitHub returned an invalid repository artifact list")
        result.extend(artifacts)
        if len(artifacts) < 100:
            return result
    fail("GitHub repository exceeds the reviewed 1,000-artifact candidate-claim bound")


def candidate_run_is_trusted(
    repository: str,
    repository_id: str,
    run_id: int,
    token: str,
) -> bool:
    payload = github_request(
        f"https://api.github.com/repos/{repository}/actions/runs/{run_id}",
        token,
    )
    try:
        value = json.loads(payload)
    except json.JSONDecodeError:
        fail("GitHub returned malformed candidate workflow-run JSON")
    if not isinstance(value, dict):
        fail("GitHub returned invalid candidate workflow-run metadata")
    head_repository = value.get("head_repository")
    return bool(
        value.get("id") == run_id
        and value.get("event") == "workflow_dispatch"
        and str(value.get("path", "")).split("@", 1)[0] == ".github/workflows/testing-candidate.yml"
        and value.get("head_branch") == "testing"
        and isinstance(value.get("head_sha"), str)
        and SHA_RE.fullmatch(value["head_sha"]) is not None
        and isinstance(head_repository, dict)
        and str(head_repository.get("id")) == repository_id
        and head_repository.get("full_name") == repository
    )


def assert_candidate_claim_exclusive(
    repository: str,
    repository_id: str,
    current_run_id: str,
    source: dict[str, Any],
    token: str,
) -> None:
    """Reject a second workflow run for a build number already claimed by testing."""
    validate_source_record(source)
    if source["repository"] != {"full_name": repository, "id": repository_id}:
        fail("Candidate-claim repository identity differs from the source record")
    run_id = require_positive_int(current_run_id, "current workflow run ID")
    build_number = source["version"]["android_version_code"]
    prefix = f"parlor-candidate-claim-build-{build_number}-run-"
    matches = [
        item
        for item in list_repository_artifacts(repository, token)
        if str(item.get("name", "")).startswith(prefix)
    ]
    if len(matches) > 20:
        fail("Candidate build number has an unreasonable number of claim artifacts")
    checked_runs: set[int] = set()
    for item in matches:
        workflow = item.get("workflow_run")
        if not isinstance(workflow, dict):
            fail("Candidate claim artifact lacks workflow-run provenance")
        claimed_run_id = require_positive_int(workflow.get("id"), "candidate claim workflow run ID")
        if claimed_run_id == run_id or claimed_run_id in checked_runs:
            continue
        checked_runs.add(claimed_run_id)
        if candidate_run_is_trusted(
            repository,
            repository_id,
            claimed_run_id,
            token,
        ):
            fail(
                "This Store build number was already claimed by another protected candidate run; "
                "rerun that workflow or review a new version/build number"
            )


def github_request(url: str, token: str) -> bytes:
    with github_api_request(url, token) as response:
        if response.status != 200:
            fail(f"GitHub API returned HTTP {response.status}")
        payload = response.read(MAX_CONTROL_ARTIFACT_BYTES * 2)
        if len(payload) > MAX_CONTROL_ARTIFACT_BYTES:
            fail("GitHub control response exceeds the 1 MiB safety limit")
        return payload


def safe_control_artifact(zip_bytes: bytes, output: Path, expected_name: str) -> None:
    if len(zip_bytes) > MAX_CONTROL_ARTIFACT_BYTES:
        fail("Control artifact archive exceeds the 1 MiB safety limit")
    with tempfile.NamedTemporaryFile() as temporary:
        temporary.write(zip_bytes)
        temporary.flush()
        with zipfile.ZipFile(temporary.name) as archive:
            members = archive.infolist()
            if len(members) > 4:
                fail("Control artifact contains too many files")
            matches = []
            total_size = 0
            for member in members:
                path = PurePosixPath(member.filename)
                if path.is_absolute() or ".." in path.parts:
                    fail("Control artifact contains an unsafe path")
                if stat.S_ISLNK(member.external_attr >> 16):
                    fail("Control artifact contains a symbolic link")
                total_size += member.file_size
                if member.file_size > MAX_CONTROL_ARTIFACT_BYTES or total_size > MAX_CONTROL_ARTIFACT_BYTES:
                    fail("Control artifact expands beyond the 1 MiB safety limit")
                if path.name == expected_name:
                    matches.append(member)
            if len(matches) != 1:
                fail(f"Control artifact must contain exactly one {expected_name}")
            payload = archive.read(matches[0])
    atomic_write_bytes(output, payload)


def fetch_artifact(repository: str, run_id: str, token: str, output: Path, expected_name: str, artifact_name: str | None, prefix: str | None) -> None:
    artifacts = list_run_artifacts(repository, run_id, token)
    if artifact_name is not None:
        matches = [item for item in artifacts if item.get("name") == artifact_name and not item.get("expired")]
    else:
        matches = [item for item in artifacts if str(item.get("name", "")).startswith(str(prefix)) and not item.get("expired")]
        matches.sort(key=lambda item: (item.get("created_at", ""), int(item.get("id", 0))), reverse=True)
        matches = matches[:1]
    if not matches:
        raise SystemExit(3)
    if len(matches) != 1:
        fail("GitHub artifact selection is ambiguous")
    archive_url = matches[0].get("archive_download_url")
    if not str(archive_url).startswith("https://api.github.com/"):
        fail("GitHub artifact download URL is invalid")
    safe_control_artifact(github_request(str(archive_url), token), output, expected_name)


def assert_artifact_prefix_absent(repository: str, run_id: str, token: str, prefix: str) -> None:
    if not prefix or len(prefix) > 240 or any(character in prefix for character in "\r\n"):
        fail("GitHub artifact prefix is invalid")
    matches = [
        item
        for item in list_run_artifacts(repository, run_id, token)
        if str(item.get("name", "")).startswith(prefix)
    ]
    if matches:
        fail(
            "An immutable candidate artifact exists or expired without usable recovery evidence; "
            "refuse to rebuild and create a new reviewed candidate"
        )


def platform_state(source: dict[str, Any], artifact: dict[str, Any], receipt: dict[str, Any], platform: str) -> dict[str, Any]:
    validate_source_record(source)
    validate_artifact_descriptor(artifact, platform)
    validate_receipt(receipt, platform, source, artifact)
    return {
        "schema_version": 1,
        "platform": platform,
        "candidate_commit_sha": source["source"]["commit_sha"],
        "artifact": artifact,
        "receipt": receipt,
    }


def validate_platform_state(value: dict[str, Any], source: dict[str, Any], platform: str) -> None:
    require_keys(
        value,
        {"schema_version", "platform", "candidate_commit_sha", "artifact", "receipt"},
        {"schema_version", "platform", "candidate_commit_sha", "artifact", "receipt"},
        "platform state",
    )
    if value["schema_version"] != 1 or value["platform"] != platform:
        fail("Platform state schema/platform mismatch")
    if value["candidate_commit_sha"] != source["source"]["commit_sha"]:
        fail("Platform state belongs to another candidate")
    validate_artifact_descriptor(value["artifact"], platform)
    validate_receipt(value["receipt"], platform, source, value["artifact"])


def mutation_intent(
    source: dict[str, Any],
    artifact: dict[str, Any],
    platform: str,
    created_at: str,
) -> dict[str, Any]:
    """Seal the last durable checkpoint before the first Store mutation.

    The marker deliberately contains no credential or Store response.  Its
    existence means a previous workflow attempt may have sent the upload even
    when no response receipt survived.  A rerun may therefore perform exact
    Store readback, but it must never send the binary again.
    """
    validate_source_record(source)
    validate_artifact_descriptor(artifact, platform)
    if artifact["candidate_commit_sha"] != source["source"]["commit_sha"]:
        fail("Store mutation intent artifact belongs to another candidate")
    expected_identity = source["applications"][
        "android_application_id" if platform == "android" else "ios_bundle_id"
    ]
    if artifact["identity"] != expected_identity:
        fail("Store mutation intent artifact identity differs from source")
    return {
        "schema_version": 1,
        "platform": platform,
        "candidate_commit_sha": source["source"]["commit_sha"],
        "artifact_sha256": artifact["sha256"],
        "created_at": parse_timestamp(created_at, "Store mutation intent time"),
    }


def validate_mutation_intent(
    value: dict[str, Any],
    source: dict[str, Any],
    artifact: dict[str, Any],
    platform: str,
) -> None:
    required = {
        "schema_version",
        "platform",
        "candidate_commit_sha",
        "artifact_sha256",
        "created_at",
    }
    require_keys(value, required, required, "Store mutation intent")
    expected = mutation_intent(source, artifact, platform, value["created_at"])
    if value != expected:
        fail("Store mutation intent differs from the exact candidate artifact")


def download_binary_artifact(
    repository: str,
    run_id: str,
    token: str,
    descriptor: dict[str, Any],
    output: Path,
) -> None:
    validate_artifact_descriptor(descriptor)
    github_artifact = descriptor["github_artifact"]
    matches = [
        item
        for item in list_run_artifacts(repository, run_id, token)
        if item.get("name") == github_artifact["name"]
        and str(item.get("id")) == github_artifact["id"]
        and not item.get("expired")
    ]
    if len(matches) != 1:
        fail("Signed binary artifact is missing, expired, or ambiguous")
    archive_url = matches[0].get("archive_download_url")
    if not str(archive_url).startswith("https://api.github.com/"):
        fail("Signed binary artifact URL is invalid")
    with tempfile.NamedTemporaryFile(prefix="parlor-binary-artifact-", suffix=".zip") as archive_file:
        digest = hashlib.sha256()
        written = 0
        with github_api_request(str(archive_url), token) as response:
            if response.status != 200:
                fail(f"GitHub artifact download returned HTTP {response.status}")
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                written += len(block)
                if written > MAX_BINARY_ARTIFACT_BYTES:
                    fail("Signed binary artifact archive exceeds the 2 GiB safety limit")
                digest.update(block)
                archive_file.write(block)
        archive_file.flush()
        if digest.hexdigest() != require_sha256(github_artifact["archive_sha256"], "artifact archive digest"):
            fail("Downloaded GitHub artifact archive digest mismatch")
        with zipfile.ZipFile(archive_file.name) as archive:
            members = archive.infolist()
            if len(members) > 100:
                fail("Signed binary artifact contains too many files")
            selected = []
            total_size = 0
            for member in members:
                path = PurePosixPath(member.filename)
                if path.is_absolute() or ".." in path.parts or stat.S_ISLNK(member.external_attr >> 16):
                    fail("Signed binary artifact contains an unsafe path")
                total_size += member.file_size
                if total_size > MAX_BINARY_ARTIFACT_BYTES:
                    fail("Signed binary artifact expands beyond the 2 GiB safety limit")
                if path.name == descriptor["filename"]:
                    selected.append(member)
            if len(selected) != 1:
                fail("Signed binary artifact does not contain exactly the recorded binary")
            member = selected[0]
            if member.file_size != descriptor["size_bytes"]:
                fail("Recovered signed binary size differs from its descriptor")
            output.parent.mkdir(parents=True, exist_ok=True)
            if output.exists() or output.is_symlink() or output.parent.is_symlink():
                fail("Refusing to overwrite an existing recovered binary path")
            extracted_digest = hashlib.sha256()
            with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temporary_output:
                temporary_path = Path(temporary_output.name)
                try:
                    with archive.open(member) as source_stream:
                        while True:
                            block = source_stream.read(1024 * 1024)
                            if not block:
                                break
                            extracted_digest.update(block)
                            temporary_output.write(block)
                    temporary_output.flush()
                    os.fsync(temporary_output.fileno())
                    if extracted_digest.hexdigest() != descriptor["sha256"]:
                        fail("Recovered signed binary digest differs from its descriptor")
                    os.chmod(temporary_path, 0o600)
                    os.replace(temporary_path, output)
                finally:
                    temporary_path.unlink(missing_ok=True)


def encode_file(path: Path) -> str:
    data = path.read_bytes()
    if len(data) > MAX_CONTROL_ARTIFACT_BYTES:
        fail("Control file is too large to pass between jobs")
    return base64.b64encode(data).decode("ascii")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    version = commands.add_parser("version", help="print the canonical marketing/build version as JSON")
    version.add_argument("--output", required=True)

    identity = commands.add_parser(
        "assert-store-identity-approved",
        help="refuse signing and Store operations until canonical identity ownership is verified",
    )
    identity.add_argument("--platform", required=True, choices=("android", "ios", "both"))

    source = commands.add_parser("source-record", help="freeze an exact clean candidate source")
    source.add_argument("--candidate-sha", required=True)
    source.add_argument("--repository-id", required=True)
    source.add_argument("--output", required=True)
    source.add_argument("--allow-dirty", action="store_true")

    claim = commands.add_parser(
        "create-candidate-claim",
        help="reserve one protected Store build number before signing",
    )
    claim.add_argument("--source", required=True)
    claim.add_argument("--run-id", required=True)
    claim.add_argument("--run-attempt", required=True)
    claim.add_argument("--created-at", default=None)
    claim.add_argument("--output", required=True)

    claim_exclusive = commands.add_parser(
        "assert-candidate-claim-exclusive",
        help="refuse a second candidate run for an already claimed Store build number",
    )
    claim_exclusive.add_argument("--repository", required=True)
    claim_exclusive.add_argument("--repository-id", required=True)
    claim_exclusive.add_argument("--run-id", required=True)
    claim_exclusive.add_argument("--source", required=True)
    claim_exclusive.add_argument("--token-env", default="GITHUB_TOKEN")

    descriptor = commands.add_parser("artifact-descriptor", help="bind a validated signed artifact to GitHub provenance")
    descriptor.add_argument("--platform", required=True, choices=("android", "ios"))
    descriptor.add_argument("--candidate-sha", required=True)
    descriptor.add_argument("--identity", required=True)
    descriptor.add_argument("--marketing-version", required=True)
    descriptor.add_argument("--build-number", required=True)
    descriptor.add_argument("--file", required=True)
    descriptor.add_argument("--signing-fingerprint", required=True)
    descriptor.add_argument("--github-artifact-id", required=True)
    descriptor.add_argument("--github-artifact-name", required=True)
    descriptor.add_argument("--github-artifact-url", required=True)
    descriptor.add_argument("--github-artifact-digest", required=True)
    descriptor.add_argument("--attestation-id", required=True)
    descriptor.add_argument("--attestation-url", required=True)
    descriptor.add_argument("--validation", required=True)
    descriptor.add_argument("--output", required=True)

    manifest = commands.add_parser("create-manifest", help="create a promotable immutable candidate manifest")
    manifest.add_argument("--source", required=True)
    manifest.add_argument("--android-artifact", required=True)
    manifest.add_argument("--ios-artifact", required=True)
    manifest.add_argument("--google-receipt", required=True)
    manifest.add_argument("--apple-receipt", required=True)
    manifest.add_argument("--run-id", required=True)
    manifest.add_argument("--run-attempt", required=True, type=int)
    manifest.add_argument("--created-at", default=None)
    manifest.add_argument("--output", required=True)

    validate = commands.add_parser("validate-manifest", help="validate a candidate manifest")
    validate.add_argument("--manifest", required=True)

    verify = commands.add_parser("verify-source", help="verify promotion source and history against a candidate")
    verify.add_argument("--manifest", required=True)
    verify.add_argument("--repository", required=True)
    verify.add_argument("--repository-id", required=True)

    receipt = commands.add_parser("validate-receipt", help="validate a receipt against a source/artifact descriptor")
    receipt.add_argument("--platform", required=True, choices=("android", "ios"))
    receipt.add_argument("--source", required=True)
    receipt.add_argument("--artifact", required=True)
    receipt.add_argument("--receipt", required=True)

    fetch = commands.add_parser("fetch-artifact", help="download one small immutable GitHub control artifact")
    fetch.add_argument("--repository", required=True)
    fetch.add_argument("--run-id", required=True)
    selection = fetch.add_mutually_exclusive_group(required=True)
    selection.add_argument("--artifact-name")
    selection.add_argument("--artifact-prefix")
    fetch.add_argument("--expected-file", required=True)
    fetch.add_argument("--output", required=True)
    fetch.add_argument("--token-env", default="GITHUB_TOKEN")

    absent = commands.add_parser(
        "assert-artifact-prefix-absent",
        help="refuse a rebuild when an orphan immutable artifact already exists",
    )
    absent.add_argument("--repository", required=True)
    absent.add_argument("--run-id", required=True)
    absent.add_argument("--artifact-prefix", required=True)
    absent.add_argument("--token-env", default="GITHUB_TOKEN")

    state = commands.add_parser("create-platform-state", help="seal a platform descriptor and Store receipt for reruns")
    state.add_argument("--platform", required=True, choices=("android", "ios"))
    state.add_argument("--source", required=True)
    state.add_argument("--artifact", required=True)
    state.add_argument("--receipt", required=True)
    state.add_argument("--output", required=True)

    restore = commands.add_parser("restore-platform-state", help="validate and split a recovered platform state")
    restore.add_argument("--platform", required=True, choices=("android", "ios"))
    restore.add_argument("--source", required=True)
    restore.add_argument("--state", required=True)
    restore.add_argument("--artifact-output", required=True)
    restore.add_argument("--receipt-output", required=True)

    create_intent = commands.add_parser(
        "create-mutation-intent",
        help="seal the last durable checkpoint before a Store upload may start",
    )
    create_intent.add_argument("--platform", required=True, choices=("android", "ios"))
    create_intent.add_argument("--source", required=True)
    create_intent.add_argument("--artifact", required=True)
    create_intent.add_argument("--created-at", default=None)
    create_intent.add_argument("--output", required=True)

    validate_intent = commands.add_parser(
        "validate-mutation-intent",
        help="bind a recovered pre-upload checkpoint to exact candidate bytes",
    )
    validate_intent.add_argument("--platform", required=True, choices=("android", "ios"))
    validate_intent.add_argument("--source", required=True)
    validate_intent.add_argument("--artifact", required=True)
    validate_intent.add_argument("--intent", required=True)

    binary = commands.add_parser("fetch-binary-artifact", help="recover exact signed bytes without rebuilding")
    binary.add_argument("--repository", required=True)
    binary.add_argument("--run-id", required=True)
    binary.add_argument("--descriptor", required=True)
    binary.add_argument("--output", required=True)
    binary.add_argument("--token-env", default="GITHUB_TOKEN")

    workflow_run = commands.add_parser(
        "workflow-run-record",
        help="bind Store evidence to one successful protected workflow run",
    )
    workflow_run.add_argument("--repository", required=True)
    workflow_run.add_argument("--repository-id", required=True)
    workflow_run.add_argument("--run-id", required=True)
    workflow_run.add_argument("--run-attempt", required=True)
    workflow_run.add_argument("--expected-workflow", required=True)
    workflow_run.add_argument("--expected-branch", required=True)
    workflow_run.add_argument("--output", required=True)
    workflow_run.add_argument("--token-env", default="GITHUB_TOKEN")

    encode = commands.add_parser("base64", help="base64-encode a bounded control file")
    encode.add_argument("--file", required=True)
    return root


def main() -> int:
    args = parser().parse_args()
    if args.command == "version":
        marketing, build = version_values()
        atomic_write_json(Path(args.output), {"marketing_version": marketing, "build_number": build})
    elif args.command == "assert-store-identity-approved":
        assert_store_identity_approved(args.platform)
    elif args.command == "source-record":
        atomic_write_json(Path(args.output), source_record(args.candidate_sha, args.repository_id, not args.allow_dirty))
    elif args.command == "create-candidate-claim":
        atomic_write_json(
            Path(args.output),
            candidate_claim(
                load_json(Path(args.source)),
                args.run_id,
                args.run_attempt,
                args.created_at or utc_now(),
            ),
        )
    elif args.command == "assert-candidate-claim-exclusive":
        token = os.environ.get(args.token_env, "")
        if not token:
            fail(f"Required GitHub token environment variable {args.token_env} is unavailable")
        assert_candidate_claim_exclusive(
            args.repository,
            str(require_positive_int(args.repository_id, "repository ID")),
            args.run_id,
            load_json(Path(args.source)),
            token,
        )
    elif args.command == "artifact-descriptor":
        atomic_write_json(Path(args.output), descriptor_from_args(args))
    elif args.command == "create-manifest":
        value = build_manifest(
            load_json(Path(args.source)),
            load_json(Path(args.android_artifact)),
            load_json(Path(args.ios_artifact)),
            load_json(Path(args.google_receipt)),
            load_json(Path(args.apple_receipt)),
            args.run_id,
            args.run_attempt,
            args.created_at or utc_now(),
        )
        atomic_write_json(Path(args.output), value)
        validate_manifest(load_json(Path(args.output)))
    elif args.command == "validate-manifest":
        validate_manifest(load_json(Path(args.manifest)))
    elif args.command == "verify-source":
        mode = verify_source(load_json(Path(args.manifest)), args.repository, args.repository_id)
        print(mode)
    elif args.command == "validate-receipt":
        source = load_json(Path(args.source))
        artifact = load_json(Path(args.artifact))
        validate_source_record(source)
        validate_artifact_descriptor(artifact, args.platform)
        validate_receipt(load_json(Path(args.receipt)), args.platform, source, artifact)
    elif args.command == "fetch-artifact":
        token = os.environ.get(args.token_env, "")
        if not token:
            fail(f"Required GitHub token environment variable {args.token_env} is unavailable")
        fetch_artifact(
            args.repository,
            args.run_id,
            token,
            Path(args.output),
            args.expected_file,
            args.artifact_name,
            args.artifact_prefix,
        )
    elif args.command == "assert-artifact-prefix-absent":
        token = os.environ.get(args.token_env, "")
        if not token:
            fail(f"Required GitHub token environment variable {args.token_env} is unavailable")
        assert_artifact_prefix_absent(args.repository, args.run_id, token, args.artifact_prefix)
    elif args.command == "create-platform-state":
        atomic_write_json(
            Path(args.output),
            platform_state(
                load_json(Path(args.source)),
                load_json(Path(args.artifact)),
                load_json(Path(args.receipt)),
                args.platform,
            ),
        )
    elif args.command == "restore-platform-state":
        source = load_json(Path(args.source))
        state_value = load_json(Path(args.state))
        validate_source_record(source)
        validate_platform_state(state_value, source, args.platform)
        atomic_write_json(Path(args.artifact_output), state_value["artifact"])
        atomic_write_json(Path(args.receipt_output), state_value["receipt"])
    elif args.command == "create-mutation-intent":
        atomic_write_json(
            Path(args.output),
            mutation_intent(
                load_json(Path(args.source)),
                load_json(Path(args.artifact)),
                args.platform,
                args.created_at or utc_now(),
            ),
        )
    elif args.command == "validate-mutation-intent":
        validate_mutation_intent(
            load_json(Path(args.intent)),
            load_json(Path(args.source)),
            load_json(Path(args.artifact)),
            args.platform,
        )
    elif args.command == "fetch-binary-artifact":
        token = os.environ.get(args.token_env, "")
        if not token:
            fail(f"Required GitHub token environment variable {args.token_env} is unavailable")
        download_binary_artifact(
            args.repository,
            args.run_id,
            token,
            load_json(Path(args.descriptor)),
            Path(args.output),
        )
    elif args.command == "workflow-run-record":
        token = os.environ.get(args.token_env, "")
        if not token:
            fail(f"Required GitHub token environment variable {args.token_env} is unavailable")
        atomic_write_json(
            Path(args.output),
            workflow_run_record(
                args.repository,
                args.repository_id,
                args.run_id,
                args.run_attempt,
                args.expected_workflow,
                args.expected_branch,
                token,
            ),
        )
    elif args.command == "base64":
        print(encode_file(Path(args.file)))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReleaseError as error:
        print(f"release validation failed: {error}", file=sys.stderr)
        raise SystemExit(2)
