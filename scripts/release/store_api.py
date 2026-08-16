#!/usr/bin/env python3
"""Minimal, fail-closed Google Play and App Store Connect release client.

Every mutating command is a dry run unless --execute is supplied. Mutating HTTP
requests are never retried automatically; callers resume from verified Store
readback and immutable receipts instead of risking duplicate operations.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import http.client
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Callable

from release_tool import (
    ReleaseError,
    atomic_write_json,
    fail,
    load_json,
    parse_timestamp,
    policy,
    require_positive_int,
    require_sha,
    require_sha256,
    sha256_file,
    utc_now,
    validate_manifest,
    validate_receipt,
    validate_source_record,
    validate_artifact_descriptor,
)


GOOGLE_API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
GOOGLE_UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
APPLE_API = "https://api.appstoreconnect.apple.com/v1"
MAX_RESPONSE_BYTES = 4 * 1024 * 1024
SAFE_RETRY_DELAYS = (1, 2, 4)
STORE_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
APPLE_REVIEW_READY = "READY_FOR_REVIEW"
APPLE_SUBMITTED_REVIEW_STATES = {
    "WAITING_FOR_REVIEW",
    "IN_REVIEW",
    "UNRESOLVED_ISSUES",
    "CANCELING",
    "COMPLETING",
    "COMPLETE",
}


class RejectRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Store credentials must never follow an unexpected HTTP redirect."""

    def redirect_request(self, request: Any, file_pointer: Any, code: int, message: str, headers: Any, new_url: str) -> None:
        return None


def open_store_request(request: urllib.request.Request, timeout: int) -> Any:
    return urllib.request.build_opener(RejectRedirectHandler()).open(request, timeout=timeout)


def require_store_id(value: Any, label: str) -> str:
    if not isinstance(value, str):
        fail(f"{label} has an invalid format")
    identifier = value
    if not STORE_ID_RE.fullmatch(identifier):
        fail(f"{label} has an invalid format")
    return identifier


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def json_bytes(value: Any) -> bytes:
    return json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")


def read_bounded(response: Any) -> bytes:
    content_length = response.headers.get("Content-Length")
    if content_length:
        try:
            declared_length = int(content_length)
        except (TypeError, ValueError):
            fail("Store API returned an invalid Content-Length header")
        if declared_length < 0 or declared_length > MAX_RESPONSE_BYTES:
            fail("Store API response exceeds the 4 MiB safety limit")
    payload = response.read(MAX_RESPONSE_BYTES + 1)
    if len(payload) > MAX_RESPONSE_BYTES:
        fail("Store API response exceeds the 4 MiB safety limit")
    return payload


def decode_json_response(payload: bytes, label: str) -> dict[str, Any]:
    if not payload:
        return {}
    try:
        value = json.loads(payload)
    except json.JSONDecodeError:
        fail(f"{label} returned malformed JSON")
    if not isinstance(value, dict):
        fail(f"{label} returned a non-object JSON response")
    return value


def openssl_sign(key_pem: str, payload: bytes, algorithm: str) -> bytes:
    with tempfile.TemporaryDirectory(prefix="parlor-sign-") as temporary_dir:
        key_path = Path(temporary_dir) / "key.pem"
        input_path = Path(temporary_dir) / "input"
        signature_path = Path(temporary_dir) / "signature"
        key_path.write_text(key_pem, encoding="utf-8")
        input_path.write_bytes(payload)
        os.chmod(key_path, 0o600)
        process = subprocess.run(
            ["openssl", "dgst", f"-{algorithm}", "-sign", str(key_path), "-out", str(signature_path), str(input_path)],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if process.returncode != 0:
            fail("OpenSSL could not sign the Store authentication assertion")
        return signature_path.read_bytes()


def der_ecdsa_to_raw(signature: bytes, component_size: int = 32) -> bytes:
    """Convert a DER ECDSA signature to the JWT ES256 r||s representation."""
    cursor = 0

    def read_length() -> int:
        nonlocal cursor
        if cursor >= len(signature):
            fail("Invalid DER signature length")
        first = signature[cursor]
        cursor += 1
        if first < 0x80:
            return first
        count = first & 0x7F
        if count == 0 or count > 2 or cursor + count > len(signature):
            fail("Invalid DER signature length")
        result = int.from_bytes(signature[cursor : cursor + count], "big")
        cursor += count
        return result

    if not signature or signature[cursor] != 0x30:
        fail("OpenSSL returned an invalid ECDSA signature")
    cursor += 1
    sequence_length = read_length()
    sequence_end = cursor + sequence_length
    components: list[bytes] = []
    for _ in range(2):
        if cursor >= len(signature) or signature[cursor] != 0x02:
            fail("OpenSSL returned an invalid ECDSA integer")
        cursor += 1
        length = read_length()
        integer = signature[cursor : cursor + length]
        cursor += length
        while len(integer) > component_size and integer[0] == 0:
            integer = integer[1:]
        if len(integer) > component_size:
            fail("ECDSA signature integer is too large")
        components.append(integer.rjust(component_size, b"\0"))
    if cursor != sequence_end or sequence_end != len(signature):
        fail("OpenSSL returned trailing ECDSA signature data")
    return b"".join(components)


class JsonClient:
    def __init__(self, base_url: str, token_provider: Callable[[], str]) -> None:
        self.base_url = base_url.rstrip("/")
        self.token_provider = token_provider

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        *,
        safe_retry: bool = False,
        expected: tuple[int, ...] = (200,),
    ) -> dict[str, Any]:
        if not path.startswith("/"):
            fail("Store API path must be absolute")
        payload = json_bytes(body) if body is not None else None
        attempts = len(SAFE_RETRY_DELAYS) + 1 if safe_retry else 1
        for attempt in range(attempts):
            request = urllib.request.Request(
                self.base_url + path,
                data=payload,
                method=method,
                headers={
                    "Accept": "application/json",
                    "Authorization": f"Bearer {self.token_provider()}",
                    "Content-Type": "application/json",
                    "User-Agent": "parlor-store-release/1",
                },
            )
            try:
                with open_store_request(request, timeout=60) as response:
                    raw = read_bounded(response)
                    if response.status not in expected:
                        fail(f"Store API {method} returned HTTP {response.status}")
                    return decode_json_response(raw, "Store API")
            except urllib.error.HTTPError as error:
                retryable = error.code == 429 or 500 <= error.code <= 599
                error.close()
                if safe_retry and retryable and attempt + 1 < attempts:
                    time.sleep(SAFE_RETRY_DELAYS[attempt])
                    continue
                fail(f"Store API {method} returned HTTP {error.code}")
            except urllib.error.URLError:
                if safe_retry and attempt + 1 < attempts:
                    time.sleep(SAFE_RETRY_DELAYS[attempt])
                    continue
                fail(f"Store API {method} request failed")
        fail("Store API retry policy exhausted")


class GoogleClient(JsonClient):
    def __init__(self, credentials_path: Path) -> None:
        credentials = load_json(credentials_path)
        required = {"client_email", "private_key", "token_uri"}
        if not required.issubset(credentials):
            fail("Google service-account JSON lacks required fields")
        if credentials["token_uri"] != "https://oauth2.googleapis.com/token":
            fail("Google service-account token URI is not the official endpoint")
        self.credentials = credentials
        self._token = ""
        self._token_expires = 0
        super().__init__(GOOGLE_API, self.access_token)

    def access_token(self) -> str:
        now = int(time.time())
        if self._token and now + 60 < self._token_expires:
            return self._token
        header = b64url(json_bytes({"alg": "RS256", "typ": "JWT"}))
        claims = b64url(
            json_bytes(
                {
                    "iss": self.credentials["client_email"],
                    "scope": "https://www.googleapis.com/auth/androidpublisher",
                    "aud": self.credentials["token_uri"],
                    "iat": now,
                    "exp": now + 3600,
                }
            )
        )
        unsigned = f"{header}.{claims}".encode("ascii")
        assertion = f"{header}.{claims}.{b64url(openssl_sign(self.credentials['private_key'], unsigned, 'sha256'))}"
        encoded = urllib.parse.urlencode(
            {
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": assertion,
            }
        ).encode("ascii")
        request = urllib.request.Request(
            self.credentials["token_uri"],
            data=encoded,
            method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded", "User-Agent": "parlor-store-release/1"},
        )
        try:
            with open_store_request(request, timeout=30) as response:
                result = decode_json_response(read_bounded(response), "Google OAuth")
        except urllib.error.HTTPError as error:
            error.close()
            fail("Google OAuth authentication failed")
        except urllib.error.URLError:
            fail("Google OAuth authentication failed")
        token = result.get("access_token")
        expires = result.get("expires_in")
        if not isinstance(token, str) or not token or not isinstance(expires, int) or expires < 60:
            fail("Google OAuth returned an invalid access token response")
        self._token = token
        self._token_expires = now + expires
        return token

    def upload_bundle(self, package: str, edit_id: str, bundle: Path) -> dict[str, Any]:
        if not bundle.is_file() or bundle.is_symlink():
            fail("Android bundle must be one regular, non-symlink file")
        parsed = urllib.parse.urlparse(
            f"{GOOGLE_UPLOAD_API}/applications/{urllib.parse.quote(package, safe='')}/edits/{urllib.parse.quote(edit_id, safe='')}/bundles?uploadType=media"
        )
        connection = http.client.HTTPSConnection(parsed.hostname, parsed.port or 443, timeout=600)
        try:
            connection.putrequest("POST", parsed.path + "?" + parsed.query)
            connection.putheader("Authorization", f"Bearer {self.access_token()}")
            connection.putheader("Content-Type", "application/octet-stream")
            connection.putheader("Content-Length", str(bundle.stat().st_size))
            connection.putheader("User-Agent", "parlor-store-release/1")
            connection.endheaders()
            with bundle.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    connection.send(block)
            response = connection.getresponse()
            payload = read_bounded(response)
            if response.status not in (200, 201):
                fail(f"Google Play bundle upload returned HTTP {response.status}")
            return decode_json_response(payload, "Google Play bundle upload")
        except OSError:
            fail("Google Play bundle upload failed")
        finally:
            connection.close()

    def insert_edit(self, package: str) -> str:
        result = self.request("POST", f"/applications/{quote(package)}/edits", {}, expected=(200, 201))
        edit_id = result.get("id")
        if not isinstance(edit_id, str) or not edit_id:
            fail("Google Play did not return an edit ID")
        return edit_id

    def delete_edit(self, package: str, edit_id: str) -> None:
        self.request("DELETE", f"/applications/{quote(package)}/edits/{quote(edit_id)}", expected=(200, 204))

    def list_tracks(self, package: str, edit_id: str) -> list[dict[str, Any]]:
        result = self.request(
            "GET",
            f"/applications/{quote(package)}/edits/{quote(edit_id)}/tracks",
            safe_retry=True,
        )
        tracks = result.get("tracks", [])
        if not isinstance(tracks, list):
            fail("Google Play returned an invalid track list")
        return [item for item in tracks if isinstance(item, dict)]

    def list_bundles(self, package: str, edit_id: str) -> list[dict[str, Any]]:
        result = self.request(
            "GET",
            f"/applications/{quote(package)}/edits/{quote(edit_id)}/bundles",
            safe_retry=True,
        )
        bundles = result.get("bundles", [])
        if not isinstance(bundles, list):
            fail("Google Play returned an invalid app-bundle list")
        return [item for item in bundles if isinstance(item, dict)]

    def get_track(self, package: str, edit_id: str, track: str) -> dict[str, Any]:
        return self.request(
            "GET",
            f"/applications/{quote(package)}/edits/{quote(edit_id)}/tracks/{quote(track)}",
            safe_retry=True,
        )

    def set_track(self, package: str, edit_id: str, track: str, version_code: int, release_name: str) -> dict[str, Any]:
        body = {
            "track": track,
            "releases": [
                {
                    "name": release_name,
                    "status": "completed",
                    "versionCodes": [str(version_code)],
                }
            ],
        }
        return self.request(
            "PUT",
            f"/applications/{quote(package)}/edits/{quote(edit_id)}/tracks/{quote(track)}",
            body,
        )

    def validate_edit(self, package: str, edit_id: str) -> None:
        self.request("POST", f"/applications/{quote(package)}/edits/{quote(edit_id)}:validate", {}, expected=(200,))

    def commit_edit(self, package: str, edit_id: str) -> None:
        self.request("POST", f"/applications/{quote(package)}/edits/{quote(edit_id)}:commit", {}, expected=(200,))

    def read_tracks(self, package: str) -> list[dict[str, Any]]:
        edit_id = self.insert_edit(package)
        try:
            return self.list_tracks(package, edit_id)
        finally:
            self.delete_edit(package, edit_id)

    def read_inventory(self, package: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        edit_id = self.insert_edit(package)
        try:
            return self.list_tracks(package, edit_id), self.list_bundles(package, edit_id)
        finally:
            self.delete_edit(package, edit_id)


def quote(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def track_has_version(track: dict[str, Any], version_code: int) -> bool:
    expected = str(version_code)
    releases = track.get("releases", [])
    return any(expected in [str(code) for code in release.get("versionCodes", [])] for release in releases if isinstance(release, dict))


def completed_release_for_version(track: dict[str, Any], version_code: int, label: str) -> dict[str, Any]:
    expected = str(version_code)
    releases = track.get("releases", [])
    if not isinstance(releases, list):
        fail(f"{label} has an invalid release list")
    matches = [
        release
        for release in releases
        if isinstance(release, dict)
        and expected in [str(code) for code in release.get("versionCodes", [])]
    ]
    if len(matches) != 1:
        fail(f"{label} does not contain exactly one release for the candidate version")
    release = matches[0]
    if release.get("status") != "completed":
        fail(f"{label} candidate release is not completed")
    return release


def find_track(tracks: list[dict[str, Any]], name: str) -> dict[str, Any] | None:
    matches = [item for item in tracks if item.get("track") == name]
    if len(matches) > 1:
        fail(f"Google Play returned duplicate records for track {name!r}")
    return matches[0] if matches else None


def bundle_for_version(bundles: list[dict[str, Any]], version_code: int) -> dict[str, Any] | None:
    matches = []
    for bundle in bundles:
        code = require_positive_int(bundle.get("versionCode"), "Google Play bundle version code")
        require_sha256(bundle.get("sha256"), "Google Play bundle SHA-256")
        if code == version_code:
            matches.append(bundle)
    if len(matches) > 1:
        fail("Google Play returned duplicate bundles for one version code")
    return matches[0] if matches else None


def require_candidate_bundle_digest(
    bundles: list[dict[str, Any]],
    version_code: int,
    artifact_sha256: str,
) -> dict[str, Any]:
    bundle = bundle_for_version(bundles, version_code)
    if bundle is None:
        fail("Google Play app-bundle inventory does not contain the candidate version")
    if require_sha256(bundle.get("sha256"), "Google Play bundle SHA-256") != artifact_sha256:
        fail("Google Play candidate version does not match the immutable AAB digest")
    return bundle


def require_internal_candidate_readback(
    tracks: list[dict[str, Any]],
    bundles: list[dict[str, Any]],
    internal_track: str,
    version_code: int,
    artifact_sha256: str,
) -> dict[str, Any]:
    locations = [str(track.get("track")) for track in tracks if track_has_version(track, version_code)]
    if locations != [internal_track]:
        fail("Candidate version is not present exactly once on the Google Play internal track")
    track = find_track(tracks, internal_track)
    if track is None:
        fail("Google Play internal track is missing during candidate readback")
    release = completed_release_for_version(track, version_code, "Google Play internal track")
    require_candidate_bundle_digest(bundles, version_code, artifact_sha256)
    return release


def require_replaceable_destination(track: dict[str, Any], label: str) -> None:
    """Reject promotion over a staged or structurally unusual Store release.

    tracks.update replaces the destination release list.  A blind update could
    cancel an in-progress rollout or discard a multi-version device-targeted
    release.  Parlor's automated path supports only an empty destination or one
    ordinary completed release; every other shape requires an explicit Console
    decision before automation can continue.
    """
    releases = track.get("releases", [])
    if not isinstance(releases, list) or any(not isinstance(item, dict) for item in releases):
        fail(f"{label} has an invalid release list")
    if len(releases) > 1:
        fail(f"{label} has multiple releases and cannot be replaced automatically")
    if not releases:
        return
    release = releases[0]
    if release.get("status") != "completed":
        fail(f"{label} has an active draft/staged/halted release")
    version_codes = release.get("versionCodes", [])
    if not isinstance(version_codes, list) or len(version_codes) != 1:
        fail(f"{label} has a multi-version or invalid completed release")
    require_positive_int(version_codes[0], f"{label} existing version code")


def ensure_version_unique(
    tracks: list[dict[str, Any]],
    version_code: int,
    bundles: list[dict[str, Any]] | None = None,
) -> None:
    locations = [str(track.get("track")) for track in tracks if track_has_version(track, version_code)]
    if locations:
        fail("Android version code already exists in Google Play without a trusted candidate receipt")
    if bundle_for_version(bundles or [], version_code) is not None:
        fail("Android version code already exists in Google Play's app-bundle inventory")


def google_check_unique_execute(args: argparse.Namespace) -> None:
    package = require_store_id(args.package, "Google Play package")
    if package != policy()["applications"]["android"]["store_application_id"]:
        fail("Google Play package is not the canonical Store identity")
    version_code = require_positive_int(args.version_code, "Android version code")
    tracks, bundles = GoogleClient(Path(args.credentials)).read_inventory(package)
    ensure_version_unique(tracks, version_code, bundles)


def google_internal_execute(args: argparse.Namespace) -> dict[str, Any]:
    source = load_json(Path(args.source))
    artifact = load_json(Path(args.artifact))
    validate_source_record(source)
    validate_artifact_descriptor(artifact, "android")
    package = source["applications"]["android_application_id"]
    version_code = source["version"]["android_version_code"]
    candidate_sha = source["source"]["commit_sha"]
    if package != args.package:
        fail("Google Play package argument does not match the canonical source record")
    if artifact["candidate_commit_sha"] != candidate_sha:
        fail("Android artifact belongs to another candidate")
    if artifact["sha256"] != require_sha256(args.artifact_sha256, "Android artifact SHA-256"):
        fail("Android artifact digest changed before upload")
    bundle = Path(args.bundle)
    if not bundle.is_file() or bundle.is_symlink() or sha256_file(bundle) != artifact["sha256"]:
        fail("Android bundle bytes do not match the validated artifact descriptor")
    internal_track = policy()["applications"]["android"]["internal_track"]
    client = GoogleClient(Path(args.credentials))
    prior = load_json(Path(args.prior_receipt)) if args.prior_receipt else None
    if prior is not None:
        validate_receipt(prior, "android", source, artifact)
        tracks, bundles = client.read_inventory(package)
        require_internal_candidate_readback(
            tracks,
            bundles,
            internal_track,
            version_code,
            artifact["sha256"],
        )
        return {**prior, "read_back_at": utc_now(), "resumed_without_upload": True}
    tracks, bundles = client.read_inventory(package)
    existing_bundle = bundle_for_version(bundles, version_code)
    if existing_bundle is not None:
        if not getattr(args, "recover_only", False):
            fail("Android version code already exists without this candidate run's trusted upload intent")
        release = require_internal_candidate_readback(
            tracks,
            bundles,
            internal_track,
            version_code,
            artifact["sha256"],
        )
        release_name = str(release.get("name", ""))
        if not release_name:
            fail("Recovered Google Play internal release has no release name")
        return {
            "schema_version": 1,
            "platform": "android",
            "operation": "internal_upload",
            "candidate_commit_sha": candidate_sha,
            "artifact_sha256": artifact["sha256"],
            "package_name": package,
            "version_code": version_code,
            "track": internal_track,
            "release_name": release_name,
            "release_status": "completed",
            "edit_id": None,
            "store_bundle_sha256": artifact["sha256"],
            "upload_evidence": "store_sha256_readback",
            "state": "internal_track_committed",
            "read_back_at": utc_now(),
            "resumed_without_upload": True,
        }
    if getattr(args, "recover_only", False):
        fail(
            "A prior Google Play upload may have started, but exact committed "
            "candidate bytes are not available for safe readback; refuse another upload"
        )
    ensure_version_unique(tracks, version_code, bundles)
    edit_id = client.insert_edit(package)
    committed = False
    try:
        uploaded = client.upload_bundle(package, edit_id, bundle)
        uploaded_code = require_positive_int(uploaded.get("versionCode"), "uploaded Google Play version code")
        if uploaded_code != version_code:
            fail("Google Play assigned a version code that differs from the candidate")
        if require_sha256(uploaded.get("sha256"), "uploaded Google Play bundle SHA-256") != artifact["sha256"]:
            fail("Google Play reported a SHA-256 that differs from the uploaded candidate")
        release_name = f"Parlor {source['version']['marketing_version']} ({version_code})"
        client.set_track(package, edit_id, internal_track, version_code, release_name)
        client.validate_edit(package, edit_id)
        client.commit_edit(package, edit_id)
        committed = True
    finally:
        if not committed:
            try:
                client.delete_edit(package, edit_id)
            except ReleaseError as cleanup_error:
                print(f"Google Play edit cleanup warning: {cleanup_error}", file=sys.stderr)
    tracks, bundles = client.read_inventory(package)
    require_internal_candidate_readback(
        tracks,
        bundles,
        internal_track,
        version_code,
        artifact["sha256"],
    )
    return {
        "schema_version": 1,
        "platform": "android",
        "operation": "internal_upload",
        "candidate_commit_sha": candidate_sha,
        "artifact_sha256": artifact["sha256"],
        "package_name": package,
        "version_code": version_code,
        "track": internal_track,
        "release_name": release_name,
        "release_status": "completed",
        "edit_id": edit_id,
        "store_bundle_sha256": artifact["sha256"],
        "upload_evidence": "committed_edit",
        # A committed Play track proves Store state, not review completion or
        # tester installation. Those remain separate console/device evidence.
        "state": "internal_track_committed",
        "read_back_at": utc_now(),
        "resumed_without_upload": False,
    }


def google_promote_execute(args: argparse.Namespace) -> dict[str, Any]:
    manifest = load_json(Path(args.manifest))
    validate_manifest(manifest)
    package = manifest["applications"]["android_application_id"]
    version_code = manifest["version"]["android_version_code"]
    candidate_sha = manifest["source"]["commit_sha"]
    artifact_sha = manifest["artifacts"]["android"]["sha256"]
    if args.package != package:
        fail("Google Play package argument does not match the candidate")
    source_track = require_store_id(args.source_track, "Google Play source track")
    destination_track = require_store_id(args.destination_track, "Google Play destination track")
    if destination_track in {"", source_track}:
        fail("Google Play source and destination tracks must be distinct")
    if args.operation == "external" and destination_track == policy()["applications"]["android"]["production_track"]:
        fail("External-testing promotion cannot target production")
    if args.operation == "production" and destination_track != policy()["applications"]["android"]["production_track"]:
        fail("Production promotion must target the production track")
    client = GoogleClient(Path(args.credentials))
    tracks, bundles = client.read_inventory(package)
    require_candidate_bundle_digest(bundles, version_code, artifact_sha)
    source = find_track(tracks, source_track)
    if source is None:
        fail("Candidate version is not present on the required source track")
    completed_release_for_version(source, version_code, "Google Play source track")
    destination = find_track(tracks, destination_track)
    if destination is None:
        fail("Configured Google Play destination track does not exist")
    require_replaceable_destination(destination, "Google Play destination track")
    if track_has_version(destination, version_code):
        completed_release_for_version(destination, version_code, "Google Play destination track")
        return google_promotion_receipt(
            manifest,
            args.operation,
            source_track,
            destination_track,
            "already_present",
            "",
        )
    edit_id = client.insert_edit(package)
    committed = False
    try:
        release_name = f"Parlor {manifest['version']['marketing_version']} ({version_code})"
        client.set_track(package, edit_id, destination_track, version_code, release_name)
        client.validate_edit(package, edit_id)
        client.commit_edit(package, edit_id)
        committed = True
    finally:
        if not committed:
            try:
                client.delete_edit(package, edit_id)
            except ReleaseError as cleanup_error:
                print(f"Google Play edit cleanup warning: {cleanup_error}", file=sys.stderr)
    readback_tracks, readback_bundles = client.read_inventory(package)
    require_candidate_bundle_digest(readback_bundles, version_code, artifact_sha)
    readback = find_track(readback_tracks, destination_track)
    if readback is None:
        fail("Google Play promotion committed but destination readback did not contain the candidate")
    completed_release_for_version(readback, version_code, "Google Play destination track")
    return google_promotion_receipt(manifest, args.operation, source_track, destination_track, "committed", edit_id)


def google_promotion_receipt(
    manifest: dict[str, Any], operation: str, source_track: str, destination_track: str, result: str, edit_id: str
) -> dict[str, Any]:
    state = "external_track_committed" if operation == "external" else "production_track_committed"
    return {
        "schema_version": 1,
        "platform": "android",
        "operation": f"{operation}_promotion",
        "candidate_commit_sha": manifest["source"]["commit_sha"],
        "artifact_sha256": manifest["artifacts"]["android"]["sha256"],
        "package_name": manifest["applications"]["android_application_id"],
        "version_code": manifest["version"]["android_version_code"],
        "source_track": source_track,
        "destination_track": destination_track,
        "release_status": "completed",
        "edit_id": edit_id,
        "result": result,
        "state": state,
        "read_back_at": utc_now(),
    }


class AppleClient(JsonClient):
    def __init__(self, issuer_id: str, key_id: str, private_key_path: Path) -> None:
        if not re.fullmatch(
            r"[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}",
            issuer_id,
        ):
            fail("App Store Connect issuer ID has an invalid UUID format")
        if not re.fullmatch(r"[A-Z0-9]{10}", key_id):
            fail("App Store Connect API key ID has an invalid format")
        try:
            private_key = private_key_path.read_text(encoding="utf-8")
        except OSError:
            fail("Cannot read the App Store Connect private key")
        if "PRIVATE KEY" not in private_key:
            fail("App Store Connect private key is not PEM encoded")
        self.issuer_id = issuer_id
        self.key_id = key_id
        self.private_key = private_key
        self._token = ""
        self._expires = 0
        super().__init__(APPLE_API, self.access_token)

    def access_token(self) -> str:
        now = int(time.time())
        if self._token and now + 30 < self._expires:
            return self._token
        header = b64url(json_bytes({"alg": "ES256", "kid": self.key_id, "typ": "JWT"}))
        claims = b64url(json_bytes({"iss": self.issuer_id, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}))
        unsigned = f"{header}.{claims}".encode("ascii")
        signature = der_ecdsa_to_raw(openssl_sign(self.private_key, unsigned, "sha256"))
        self._token = f"{header}.{claims}.{b64url(signature)}"
        self._expires = now + 600
        return self._token

    def build_query(self, app_id: str, build_number: str) -> dict[str, Any]:
        query = urllib.parse.urlencode(
            {
                "filter[app]": app_id,
                "filter[version]": build_number,
                "include": "preReleaseVersion",
                "limit": "200",
            }
        )
        return self.request("GET", f"/builds?{query}", safe_retry=True)

    def app(self, app_id: str) -> dict[str, Any]:
        return self.request("GET", f"/apps/{quote(app_id)}", safe_retry=True)

    def paginated_data(self, path: str, *, max_pages: int = 10, max_items: int = 1000) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        next_path = path
        expected = urllib.parse.urlparse(APPLE_API)
        for _ in range(max_pages):
            page = self.request("GET", next_path, safe_retry=True)
            data = page.get("data", [])
            if not isinstance(data, list) or any(not isinstance(item, dict) for item in data):
                fail("App Store Connect returned invalid paginated data")
            result.extend(data)
            if len(result) > max_items:
                fail("App Store Connect response exceeds the reviewed pagination bound")
            next_url = page.get("links", {}).get("next") if isinstance(page.get("links", {}), dict) else None
            if not next_url:
                return result
            parsed = urllib.parse.urlparse(str(next_url))
            if parsed.scheme != "https" or parsed.netloc != expected.netloc or not parsed.path.startswith(expected.path + "/"):
                fail("App Store Connect returned an unsafe pagination URL")
            next_path = parsed.path.removeprefix(expected.path) + (f"?{parsed.query}" if parsed.query else "")
        fail("App Store Connect response exceeds the reviewed pagination page bound")

    def find_processed_build(
        self, app_id: str, marketing_version: str, build_number: str, max_polls: int, poll_seconds: int
    ) -> dict[str, Any]:
        for attempt in range(max_polls):
            response = self.build_query(app_id, build_number)
            builds = response.get("data", [])
            if not isinstance(builds, list):
                fail("App Store Connect returned an invalid build list")
            if len(builds) > 1:
                fail("App Store Connect returned duplicate builds for one app/build number")
            if builds:
                build = builds[0]
                included = response.get("included", [])
                prerelease_ids = {
                    item.get("id"): item.get("attributes", {}).get("version")
                    for item in included
                    if item.get("type") == "preReleaseVersions"
                }
                relationship_id = (
                    build.get("relationships", {}).get("preReleaseVersion", {}).get("data", {}) or {}
                ).get("id")
                if prerelease_ids.get(relationship_id) != marketing_version:
                    fail("Processed App Store Connect build has the wrong marketing version")
                processing = build.get("attributes", {}).get("processingState")
                if processing == "VALID":
                    validate_store_eligible_apple_build(build, build_number)
                    return build
                if processing in {"FAILED", "INVALID"}:
                    fail("App Store Connect rejected the uploaded build during processing")
            if attempt + 1 < max_polls:
                time.sleep(poll_seconds)
        fail("App Store Connect build processing deadline expired")

    def assert_build_absent(self, app_id: str, build_number: str) -> None:
        response = self.build_query(app_id, build_number)
        data = response.get("data", [])
        if data:
            fail("iOS build number already exists without a trusted candidate receipt")

    def build(self, build_id: str) -> dict[str, Any]:
        return self.request("GET", f"/builds/{quote(build_id)}", safe_retry=True)

    def group(self, group_id: str) -> dict[str, Any]:
        return self.request("GET", f"/betaGroups/{quote(group_id)}", safe_retry=True)

    def group_app(self, group_id: str) -> dict[str, Any]:
        return self.request(
            "GET",
            f"/betaGroups/{quote(group_id)}/relationships/app",
            safe_retry=True,
        )

    def build_groups(self, build_id: str) -> list[dict[str, Any]]:
        query = urllib.parse.urlencode({"filter[builds]": build_id, "limit": "200"})
        return self.paginated_data(f"/betaGroups?{query}")

    def add_build_to_group(self, build_id: str, group_id: str) -> bool:
        if any(item.get("id") == group_id for item in self.build_groups(build_id)):
            return False
        self.request(
            "POST",
            f"/betaGroups/{quote(group_id)}/relationships/builds",
            {"data": [{"type": "builds", "id": build_id}]},
            expected=(200, 201, 204),
        )
        if not any(item.get("id") == group_id for item in self.build_groups(build_id)):
            fail("App Store Connect did not retain the beta-group association")
        return True

    def beta_review(self, build_id: str) -> dict[str, Any] | None:
        query = urllib.parse.urlencode({"filter[build]": build_id, "limit": "200"})
        result = self.request("GET", f"/betaAppReviewSubmissions?{query}", safe_retry=True)
        data = result.get("data", [])
        if not isinstance(data, list) or len(data) > 1:
            fail("App Store Connect returned an invalid beta-review list")
        return data[0] if data else None

    def submit_beta_review(self, build_id: str) -> dict[str, Any]:
        existing = self.beta_review(build_id)
        if existing:
            return existing
        return self.request(
            "POST",
            "/betaAppReviewSubmissions",
            {
                "data": {
                    "type": "betaAppReviewSubmissions",
                    "relationships": {"build": {"data": {"type": "builds", "id": build_id}}},
                }
            },
            expected=(200, 201),
        ).get("data", {})

    def app_store_version(self, version_id: str) -> dict[str, Any]:
        return self.request("GET", f"/appStoreVersions/{quote(version_id)}?include=app,build", safe_retry=True)

    def attach_build(self, version_id: str, build_id: str) -> bool:
        current = self.request(
            "GET", f"/appStoreVersions/{quote(version_id)}/relationships/build", safe_retry=True
        ).get("data")
        if isinstance(current, dict) and current.get("id") == build_id:
            return False
        if current:
            fail("App Store version is already attached to a different build")
        self.request(
            "PATCH",
            f"/appStoreVersions/{quote(version_id)}/relationships/build",
            {"data": {"type": "builds", "id": build_id}},
            expected=(200, 204),
        )
        readback = self.request(
            "GET", f"/appStoreVersions/{quote(version_id)}/relationships/build", safe_retry=True
        ).get("data")
        if not isinstance(readback, dict) or readback.get("id") != build_id:
            fail("App Store version build attachment failed readback")
        return True

    def list_review_submissions(self, app_id: str) -> list[dict[str, Any]]:
        return self.paginated_data(f"/apps/{quote(app_id)}/reviewSubmissions?limit=200")

    def submission_items(self, submission_id: str) -> list[dict[str, Any]]:
        result = self.request(
            "GET",
            f"/reviewSubmissions/{quote(submission_id)}/items?include=appStoreVersion&limit=200",
            safe_retry=True,
        )
        data = result.get("data", [])
        if not isinstance(data, list):
            fail("App Store Connect returned invalid review-submission items")
        return [item for item in data if isinstance(item, dict)]

    def submission_for_version(self, app_id: str, version_id: str) -> dict[str, Any] | None:
        candidates = []
        for submission in self.list_review_submissions(app_id):
            for item in self.submission_items(str(submission.get("id"))):
                relationship = item.get("relationships", {}).get("appStoreVersion", {}).get("data")
                if isinstance(relationship, dict) and relationship.get("id") == version_id:
                    candidates.append(submission)
        if len(candidates) > 1:
            fail("Multiple review submissions reference the same App Store version")
        return candidates[0] if candidates else None

    def reusable_empty_review_submission(self, app_id: str) -> dict[str, Any] | None:
        """Recover one unbound draft without creating a duplicate submission.

        A network failure can occur after App Store Connect creates a review
        submission but before the version item is attached.  Such a draft has
        no candidate identifier to bind it to.  Reuse exactly one empty
        READY_FOR_REVIEW draft; fail closed around any other active submission
        rather than appending this release to unrelated Store state.
        """
        reusable: list[dict[str, Any]] = []
        active_states = {APPLE_REVIEW_READY, *(APPLE_SUBMITTED_REVIEW_STATES - {"COMPLETE"})}
        for submission in self.list_review_submissions(app_id):
            submission_id = require_store_id(submission.get("id"), "review submission ID")
            state = submission.get("attributes", {}).get("state")
            if state == "COMPLETE":
                continue
            if state not in active_states:
                fail("App Store Connect returned an unknown production review state")
            items = self.submission_items(submission_id)
            if state == APPLE_REVIEW_READY and not items:
                reusable.append(submission)
                continue
            fail("Another active App Store review submission must be resolved before this candidate")
        if len(reusable) > 1:
            fail("Multiple unbound App Store review submissions require manual reconciliation")
        return reusable[0] if reusable else None

    def create_and_submit_review(self, app_id: str, version_id: str) -> dict[str, Any]:
        existing = self.submission_for_version(app_id, version_id)
        if existing:
            attributes = existing.get("attributes", {})
            state = attributes.get("state")
            if state in APPLE_SUBMITTED_REVIEW_STATES:
                return existing
            if state != APPLE_REVIEW_READY:
                fail("App Store Connect returned an unknown production review state")
            submission_id = str(existing.get("id", ""))
            if not submission_id:
                fail("Existing review submission has no ID")
            self.request(
                "PATCH",
                f"/reviewSubmissions/{quote(submission_id)}",
                {
                    "data": {
                        "type": "reviewSubmissions",
                        "id": submission_id,
                        "attributes": {"submitted": True},
                    }
                },
                expected=(200,),
            )
            readback = self.submission_for_version(app_id, version_id)
            if readback is None or str(readback.get("id", "")) != submission_id:
                fail("App Store Connect did not retain the production review submission")
            if readback.get("attributes", {}).get("state") not in APPLE_SUBMITTED_REVIEW_STATES:
                fail("App Store Connect readback does not show a submitted production review")
            return readback
        submission = self.reusable_empty_review_submission(app_id)
        if submission is None:
            submission = self.request(
                "POST",
                "/reviewSubmissions",
                {
                    "data": {
                        "type": "reviewSubmissions",
                        "attributes": {"platform": "IOS"},
                        "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
                    }
                },
                expected=(200, 201),
            ).get("data", {})
        submission_id = str(submission.get("id", ""))
        if not submission_id:
            fail("App Store Connect did not return a review-submission ID")
        self.request(
            "POST",
            "/reviewSubmissionItems",
            {
                "data": {
                    "type": "reviewSubmissionItems",
                    "relationships": {
                        "reviewSubmission": {
                            "data": {"type": "reviewSubmissions", "id": submission_id}
                        },
                        "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version_id}},
                    },
                }
            },
            expected=(200, 201),
        )
        self.request(
            "PATCH",
            f"/reviewSubmissions/{quote(submission_id)}",
            {
                "data": {
                    "type": "reviewSubmissions",
                    "id": submission_id,
                    "attributes": {"submitted": True},
                }
            },
            expected=(200,),
        )
        readback = self.submission_for_version(app_id, version_id)
        if readback is None or str(readback.get("id", "")) != submission_id:
            fail("App Store Connect did not retain the production review submission")
        if readback.get("attributes", {}).get("state") not in APPLE_SUBMITTED_REVIEW_STATES:
            fail("App Store Connect readback does not show a submitted production review")
        return readback


def apple_client(args: argparse.Namespace) -> AppleClient:
    return AppleClient(args.issuer_id, args.key_id, Path(args.private_key))


def validate_apple_manifest_args(manifest: dict[str, Any], args: argparse.Namespace) -> dict[str, Any]:
    validate_manifest(manifest)
    app_id = require_store_id(args.app_id, "App Store Connect app ID")
    if args.bundle_id != manifest["applications"]["ios_bundle_id"]:
        fail("App Store Connect app/bundle identity is missing or mismatched")
    receipt = manifest["stores"]["app_store_connect"]
    if app_id != receipt["app_id"]:
        fail("App Store Connect app ID differs from the candidate's uploaded app")
    return receipt


def validate_apple_app(client: AppleClient, app_id: str, expected_bundle_id: str) -> None:
    app = client.app(app_id).get("data", {})
    if app.get("id") != app_id or app.get("attributes", {}).get("bundleId") != expected_bundle_id:
        fail("App Store Connect app ID does not resolve to the candidate Bundle ID")


def validate_store_eligible_apple_build(build: dict[str, Any], expected_build_number: str) -> dict[str, Any]:
    """Require a processed build that can reach external TestFlight and the App Store."""
    if not isinstance(build, dict):
        fail("App Store Connect returned an invalid build")
    require_store_id(build.get("id"), "App Store Connect build ID")
    attributes = build.get("attributes")
    if not isinstance(attributes, dict):
        fail("App Store Connect build has invalid attributes")
    if str(attributes.get("version", "")) != str(require_positive_int(expected_build_number, "iOS build number")):
        fail("App Store Connect build number differs from the candidate")
    if attributes.get("processingState") != "VALID":
        fail("App Store Connect build is not fully processed")
    if attributes.get("expired") is not False:
        fail("App Store Connect build is expired or has no affirmative non-expired readback")
    if attributes.get("buildAudienceType") != "APP_STORE_ELIGIBLE":
        fail("App Store Connect build is not eligible for external TestFlight and App Store release")
    if not isinstance(attributes.get("usesNonExemptEncryption"), bool):
        fail("App Store Connect has no resolved export-compliance value for the build")
    minimum_os = str(attributes.get("minOsVersion", ""))
    expected_minimum_os = str(policy()["toolchains"]["apple"]["deployment_target"])
    version_pattern = re.compile(r"^[0-9]+(?:\.[0-9]+){0,2}$")
    if not version_pattern.fullmatch(minimum_os) or not version_pattern.fullmatch(expected_minimum_os):
        fail("App Store Connect build minimum OS has an invalid format")
    normalized_minimum = tuple(int(part) for part in minimum_os.split(".")) + (0,) * (3 - len(minimum_os.split(".")))
    normalized_expected = tuple(int(part) for part in expected_minimum_os.split(".")) + (0,) * (
        3 - len(expected_minimum_os.split("."))
    )
    if normalized_minimum != normalized_expected:
        fail("App Store Connect build minimum OS differs from the release policy")
    parse_timestamp(attributes.get("uploadedDate"), "App Store Connect build upload date")
    expiration = parse_timestamp(attributes.get("expirationDate"), "App Store Connect build expiration date")
    expiration_time = dt.datetime.fromisoformat(expiration.replace("Z", "+00:00"))
    if expiration_time <= dt.datetime.now(dt.timezone.utc):
        fail("App Store Connect build expiration date is not in the future")
    return attributes


def validate_beta_group(
    response: dict[str, Any],
    app_linkage_response: dict[str, Any],
    group_id: str,
    app_id: str,
    *,
    internal: bool,
) -> None:
    group = response.get("data", {})
    related_app = app_linkage_response.get("data", {})
    if (
        group.get("id") != group_id
        or group.get("attributes", {}).get("isInternalGroup") is not internal
        or not isinstance(related_app, dict)
        or related_app.get("type") != "apps"
        or related_app.get("id") != app_id
    ):
        kind = "internal" if internal else "external"
        fail(f"Configured TestFlight {kind} group does not belong to the candidate app")


def validate_apple_upload_transport(path: Path, artifact: dict[str, Any], request_id: str) -> dict[str, Any]:
    transport = load_json(path)
    required = {
        "schema_version",
        "transport",
        "accepted",
        "upload_request_id",
        "response_sha256",
        "artifact_sha256",
    }
    if set(transport) != required:
        fail("Apple upload-transport receipt has unsupported or missing fields")
    if transport["schema_version"] != 1 or transport["transport"] != "xcrun-altool" or transport["accepted"] is not True:
        fail("Apple upload-transport receipt is not an accepted altool upload")
    if not request_id or transport["upload_request_id"] != request_id:
        fail("Apple upload request ID differs from its transport receipt")
    if require_sha256(transport["artifact_sha256"], "Apple upload artifact SHA-256") != artifact["sha256"]:
        fail("Apple upload transport belongs to different IPA bytes")
    require_sha256(transport["response_sha256"], "Apple upload response SHA-256")
    return transport


def apple_internal_execute(args: argparse.Namespace) -> dict[str, Any]:
    source = load_json(Path(args.source))
    artifact = load_json(Path(args.artifact))
    validate_source_record(source)
    validate_artifact_descriptor(artifact, "ios")
    if artifact["candidate_commit_sha"] != source["source"]["commit_sha"]:
        fail("iOS artifact belongs to another candidate")
    if artifact["sha256"] != require_sha256(args.artifact_sha256, "iOS artifact SHA-256"):
        fail("iOS artifact digest changed before upload")
    client = apple_client(args)
    validate_apple_app(client, args.app_id, source["applications"]["ios_bundle_id"])
    internal_group_id = require_store_id(args.internal_group_id, "TestFlight internal group ID")
    validate_beta_group(
        client.group(internal_group_id),
        client.group_app(internal_group_id),
        internal_group_id,
        args.app_id,
        internal=True,
    )
    prior = load_json(Path(args.prior_receipt)) if args.prior_receipt else None
    if prior is not None:
        validate_receipt(prior, "ios", source, artifact)
        if prior["app_id"] != args.app_id or prior["internal_group_id"] != internal_group_id:
            fail("Trusted Apple receipt belongs to different app/group configuration")
    else:
        if not args.upload_transport_receipt:
            fail("A trusted Apple upload-transport receipt is required for a new internal build")
        validate_apple_upload_transport(Path(args.upload_transport_receipt), artifact, args.upload_request_id)
    build = client.find_processed_build(
        args.app_id,
        source["version"]["marketing_version"],
        source["version"]["ios_build_number"],
        args.max_polls,
        args.poll_seconds,
    )
    build_id = str(build.get("id", ""))
    build_attributes = validate_store_eligible_apple_build(
        build,
        source["version"]["ios_build_number"],
    )
    if prior is not None and prior.get("build_id") != build_id:
        fail("Trusted Apple receipt points to a different processed build")
    client.add_build_to_group(build_id, internal_group_id)
    if not any(item.get("id") == internal_group_id for item in client.build_groups(build_id)):
        fail("Processed iOS build is not available to the configured internal group")
    return {
        "schema_version": 1,
        "platform": "ios",
        "operation": "internal_upload",
        "candidate_commit_sha": source["source"]["commit_sha"],
        "artifact_sha256": artifact["sha256"],
        "bundle_id": source["applications"]["ios_bundle_id"],
        "marketing_version": source["version"]["marketing_version"],
        "build_number": source["version"]["ios_build_number"],
        "app_id": args.app_id,
        "build_id": build_id,
        "upload_request_id": args.upload_request_id if prior is None else prior.get("upload_request_id", ""),
        "internal_group_id": internal_group_id,
        "processing_state": "VALID",
        "build_audience_type": build_attributes["buildAudienceType"],
        "uses_non_exempt_encryption": build_attributes["usesNonExemptEncryption"],
        "expired": build_attributes["expired"],
        "state": "available_to_internal_testers",
        "read_back_at": utc_now(),
        "resumed_without_upload": prior is not None,
    }


def apple_external_execute(args: argparse.Namespace) -> dict[str, Any]:
    manifest = load_json(Path(args.manifest))
    receipt = validate_apple_manifest_args(manifest, args)
    client = apple_client(args)
    validate_apple_app(client, args.app_id, manifest["applications"]["ios_bundle_id"])
    external_group_id = require_store_id(args.external_group_id, "TestFlight external group ID")
    validate_beta_group(
        client.group(external_group_id),
        client.group_app(external_group_id),
        external_group_id,
        args.app_id,
        internal=False,
    )
    build_id = str(receipt["build_id"])
    current_build = client.build(build_id).get("data", {})
    validate_store_eligible_apple_build(current_build, manifest["version"]["ios_build_number"])
    client.add_build_to_group(build_id, external_group_id)
    client.submit_beta_review(build_id)
    review = client.beta_review(build_id)
    if review is None:
        fail("App Store Connect did not retain the Beta App Review submission")
    review_state = review.get("attributes", {}).get("betaReviewState")
    if review_state not in {"WAITING_FOR_REVIEW", "IN_REVIEW", "APPROVED", "REJECTED"}:
        fail("App Store Connect returned an unknown beta-review state")
    state = {
        "WAITING_FOR_REVIEW": "submitted_for_external_testing",
        "IN_REVIEW": "beta_app_review",
        "APPROVED": "available_to_external_testers",
        "REJECTED": "rejected",
    }[review_state]
    return {
        "schema_version": 1,
        "platform": "ios",
        "operation": "external_promotion",
        "candidate_commit_sha": manifest["source"]["commit_sha"],
        "artifact_sha256": manifest["artifacts"]["ios"]["sha256"],
        "bundle_id": manifest["applications"]["ios_bundle_id"],
        "build_number": manifest["version"]["ios_build_number"],
        "build_id": build_id,
        "external_group_id": external_group_id,
        "beta_review_submission_id": str(review.get("id", "")),
        "beta_review_state": review_state,
        "state": state,
        "read_back_at": utc_now(),
    }


def validate_external_receipt(path: Path, manifest: dict[str, Any], platform: str) -> dict[str, Any]:
    receipt = load_json(path)
    common = {
        "schema_version",
        "platform",
        "operation",
        "candidate_commit_sha",
        "artifact_sha256",
        "state",
        "read_back_at",
    }
    platform_fields = (
        {
            "package_name",
            "version_code",
            "source_track",
            "destination_track",
            "release_status",
            "edit_id",
            "result",
        }
        if platform == "android"
        else {
            "bundle_id",
            "build_number",
            "build_id",
            "external_group_id",
            "beta_review_submission_id",
            "beta_review_state",
        }
    )
    if set(receipt) != common | platform_fields:
        fail("External-testing receipt has unsupported or missing fields")
    if receipt.get("schema_version") != 1 or receipt.get("platform") != platform:
        fail("External-testing receipt schema/platform mismatch")
    if receipt.get("operation") != "external_promotion":
        fail("Receipt is not an external-testing promotion receipt")
    if receipt.get("candidate_commit_sha") != manifest["source"]["commit_sha"]:
        fail("External-testing receipt belongs to another candidate")
    if receipt.get("artifact_sha256") != manifest["artifacts"][platform]["sha256"]:
        fail("External-testing receipt belongs to different artifact bytes")
    expected_state = "external_track_committed" if platform == "android" else "available_to_external_testers"
    if receipt.get("state") != expected_state:
        fail("Candidate has not reached the required external-testing Store state")
    parse_timestamp(receipt.get("read_back_at"), "external-testing receipt readback time")
    if platform == "android":
        if receipt["package_name"] != manifest["applications"]["android_application_id"]:
            fail("External-testing receipt package mismatch")
        if require_positive_int(receipt["version_code"], "external receipt version code") != manifest["version"]["android_version_code"]:
            fail("External-testing receipt version-code mismatch")
        if receipt["source_track"] != policy()["applications"]["android"]["internal_track"]:
            fail("External-testing receipt did not promote from the internal track")
        if receipt["destination_track"] in {"", "internal", "production"}:
            fail("External-testing receipt has an invalid destination track")
        if receipt["release_status"] != "completed":
            fail("External-testing receipt is not for a completed Play release")
        if receipt["result"] not in {"committed", "already_present"}:
            fail("External-testing receipt has an invalid Play promotion result")
        if receipt["result"] == "committed" and not str(receipt["edit_id"]):
            fail("Committed Play promotion receipt has no edit identity")
    else:
        internal = manifest["stores"]["app_store_connect"]
        if receipt["bundle_id"] != manifest["applications"]["ios_bundle_id"]:
            fail("External-testing receipt Bundle ID mismatch")
        if str(receipt["build_number"]) != manifest["version"]["ios_build_number"]:
            fail("External-testing receipt build-number mismatch")
        if receipt["build_id"] != internal["build_id"]:
            fail("External-testing receipt refers to a different App Store Connect build")
        if receipt["beta_review_state"] != "APPROVED":
            fail("External TestFlight build has not passed Beta App Review")
        if not str(receipt["external_group_id"]) or not str(receipt["beta_review_submission_id"]):
            fail("External TestFlight receipt lacks group/review identity")
    return receipt


def build_external_evidence(
    manifest: dict[str, Any],
    manifest_sha256: str,
    platform_choice: str,
    android_receipt_path: Path | None,
    ios_receipt_path: Path | None,
    run_id: Any,
    run_attempt: Any,
    workflow_source_sha: str,
    created_at: str,
) -> dict[str, Any]:
    """Aggregate platform receipts from any attempt into one successful-run record."""
    validate_manifest(manifest)
    selected = {
        "android": ("android",),
        "ios": ("ios",),
        "both": ("android", "ios"),
    }.get(platform_choice)
    if selected is None:
        fail("External evidence has an invalid platform selection")
    paths = {"android": android_receipt_path, "ios": ios_receipt_path}
    receipts: dict[str, Any] = {}
    for platform in selected:
        path = paths[platform]
        if path is None:
            fail(f"External evidence is missing the selected {platform} receipt")
        receipts[platform] = validate_external_receipt(path, manifest, platform)
    return {
        "schema_version": 1,
        "repository": manifest["repository"],
        "candidate_commit_sha": manifest["source"]["commit_sha"],
        "candidate_manifest_sha256": require_sha256(manifest_sha256, "candidate manifest SHA-256"),
        "workflow_run_id": str(require_positive_int(run_id, "external workflow run ID")),
        "workflow_run_attempt": require_positive_int(run_attempt, "external workflow run attempt"),
        "workflow_source_sha": require_sha(workflow_source_sha, "external workflow source SHA"),
        "platforms": list(selected),
        "receipts": receipts,
        "created_at": parse_timestamp(created_at, "external evidence creation time"),
    }


def create_external_evidence(
    manifest_path: Path,
    platform_choice: str,
    android_receipt_path: Path | None,
    ios_receipt_path: Path | None,
    run_id: Any,
    run_attempt: Any,
    workflow_source_sha: str,
    created_at: str,
) -> dict[str, Any]:
    manifest = load_json(manifest_path)
    value = build_external_evidence(
        manifest,
        sha256_file(manifest_path),
        platform_choice,
        android_receipt_path,
        ios_receipt_path,
        run_id,
        run_attempt,
        workflow_source_sha,
        created_at,
    )
    return value


def validate_external_evidence(
    evidence: dict[str, Any],
    manifest_path: Path,
    expected_run_id: Any,
    expected_run_attempt: Any,
    expected_source_sha: str,
) -> dict[str, Any]:
    required = {
        "schema_version",
        "repository",
        "candidate_commit_sha",
        "candidate_manifest_sha256",
        "workflow_run_id",
        "workflow_run_attempt",
        "workflow_source_sha",
        "platforms",
        "receipts",
        "created_at",
    }
    if set(evidence) != required:
        fail("External evidence has unsupported or missing fields")
    manifest = load_json(manifest_path)
    validate_manifest(manifest)
    if evidence["schema_version"] != 1 or evidence["repository"] != manifest["repository"]:
        fail("External evidence schema/repository mismatch")
    if evidence["candidate_commit_sha"] != manifest["source"]["commit_sha"]:
        fail("External evidence belongs to another candidate")
    if require_sha256(evidence["candidate_manifest_sha256"], "candidate manifest SHA-256") != sha256_file(manifest_path):
        fail("External evidence belongs to different candidate-manifest bytes")
    if str(require_positive_int(evidence["workflow_run_id"], "external workflow run ID")) != str(
        require_positive_int(expected_run_id, "expected external workflow run ID")
    ):
        fail("External evidence workflow run ID mismatch")
    if require_positive_int(evidence["workflow_run_attempt"], "external workflow run attempt") != require_positive_int(
        expected_run_attempt,
        "expected external workflow run attempt",
    ):
        fail("External evidence workflow run attempt mismatch")
    if require_sha(evidence["workflow_source_sha"], "external workflow source SHA") != require_sha(
        expected_source_sha,
        "expected external workflow source SHA",
    ):
        fail("External evidence workflow source SHA mismatch")
    platforms = evidence["platforms"]
    receipts = evidence["receipts"]
    if platforms not in (["android"], ["ios"], ["android", "ios"]):
        fail("External evidence has an invalid platform list")
    if not isinstance(receipts, dict) or set(receipts) != set(platforms):
        fail("External evidence receipt keys differ from its platform list")
    parse_timestamp(evidence["created_at"], "external evidence creation time")
    for platform in platforms:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8") as receipt_file:
            json.dump(receipts[platform], receipt_file)
            receipt_file.flush()
            validate_external_receipt(Path(receipt_file.name), manifest, platform)
    return evidence


def apple_production_execute(args: argparse.Namespace) -> dict[str, Any]:
    manifest = load_json(Path(args.manifest))
    internal = validate_apple_manifest_args(manifest, args)
    external = validate_external_receipt(Path(args.external_receipt), manifest, "ios")
    client = apple_client(args)
    validate_apple_app(client, args.app_id, manifest["applications"]["ios_bundle_id"])
    build_id = str(internal["build_id"])
    current_build = client.build(build_id).get("data", {})
    validate_store_eligible_apple_build(current_build, manifest["version"]["ios_build_number"])
    if not any(item.get("id") == external["external_group_id"] for item in client.build_groups(build_id)):
        fail("The tested build is no longer associated with the approved external TestFlight group")
    beta_review = client.beta_review(build_id)
    if beta_review is None or beta_review.get("attributes", {}).get("betaReviewState") != "APPROVED":
        fail("The tested build is not currently approved for external TestFlight testing")
    version_id = require_store_id(args.app_store_version_id, "App Store Connect version ID")
    response = client.app_store_version(version_id)
    version = response.get("data", {})
    if version.get("attributes", {}).get("versionString") != manifest["version"]["marketing_version"]:
        fail("App Store production version has the wrong marketing version")
    if version.get("attributes", {}).get("platform") != "IOS":
        fail("App Store production version is not an iOS version")
    included = response.get("included", [])
    app_ids = [item.get("id") for item in included if item.get("type") == "apps"]
    if args.app_id not in app_ids:
        fail("App Store production version belongs to another application")
    attached = client.attach_build(version_id, build_id)
    submission_id = ""
    review_state = ""
    if args.submit:
        submission = client.create_and_submit_review(args.app_id, version_id)
        submission_id = str(submission.get("id", ""))
        if not submission_id:
            fail("App Store Connect did not return a production review-submission ID")
        review_state = str(submission.get("attributes", {}).get("state", ""))
        if review_state not in APPLE_SUBMITTED_REVIEW_STATES:
            fail("App Store Connect did not return a submitted production review state")
    state_by_review = {
        "WAITING_FOR_REVIEW": "submitted_for_production_review",
        "IN_REVIEW": "in_production_review",
        "UNRESOLVED_ISSUES": "production_review_has_unresolved_issues",
        "CANCELING": "production_review_canceling",
        "COMPLETING": "production_review_completing",
        "COMPLETE": "production_review_complete",
    }
    return {
        "schema_version": 1,
        "platform": "ios",
        "operation": "production_submission" if args.submit else "production_attachment",
        "candidate_commit_sha": manifest["source"]["commit_sha"],
        "artifact_sha256": manifest["artifacts"]["ios"]["sha256"],
        "bundle_id": manifest["applications"]["ios_bundle_id"],
        "build_number": manifest["version"]["ios_build_number"],
        "build_id": build_id,
        "app_store_version_id": version_id,
        "review_submission_id": submission_id,
        "review_state": review_state,
        "attachment_changed": attached,
        "state": state_by_review[review_state] if args.submit else "attached_to_app_store_version",
        "read_back_at": utc_now(),
    }


def validation_plan(args: argparse.Namespace) -> dict[str, Any]:
    manifest = load_json(Path(args.manifest)) if getattr(args, "manifest", None) else None
    if manifest is not None:
        validate_manifest(manifest)
        candidate_sha = manifest["source"]["commit_sha"]
        platform = "android" if args.command.startswith("google") else "ios"
        artifact_sha = manifest["artifacts"][platform]["sha256"]
        if platform == "android":
            if args.package != manifest["applications"]["android_application_id"]:
                fail("Google Play package argument does not match the candidate")
            source_track = require_store_id(args.source_track, "Google Play source track")
            destination_track = require_store_id(args.destination_track, "Google Play destination track")
            if destination_track == source_track:
                fail("Google Play source and destination tracks must be distinct")
            if args.operation == "external" and destination_track == policy()["applications"]["android"]["production_track"]:
                fail("External-testing promotion cannot target production")
            if args.operation == "production":
                if destination_track != policy()["applications"]["android"]["production_track"]:
                    fail("Production promotion must target the production track")
                if not args.external_receipt:
                    fail("Google production promotion requires an external-testing receipt")
                external = validate_external_receipt(Path(args.external_receipt), manifest, "android")
                if external["destination_track"] != source_track:
                    fail("Google production source track differs from the tested external track")
        else:
            validate_apple_manifest_args(manifest, args)
            if args.command == "apple-external":
                require_store_id(args.external_group_id, "TestFlight external group ID")
            if args.command == "apple-production":
                require_store_id(args.app_store_version_id, "App Store Connect version ID")
                validate_external_receipt(Path(args.external_receipt), manifest, "ios")
    else:
        source = load_json(Path(args.source))
        artifact = load_json(Path(args.artifact))
        validate_source_record(source)
        platform = "android" if args.command.startswith("google") else "ios"
        validate_artifact_descriptor(artifact, platform)
        if artifact["candidate_commit_sha"] != source["source"]["commit_sha"]:
            fail(f"{platform} artifact belongs to another candidate")
        if artifact["marketing_version"] != source["version"]["marketing_version"]:
            fail(f"{platform} artifact marketing version differs from source")
        expected_build = (
            source["version"]["android_version_code"]
            if platform == "android"
            else source["version"]["ios_build_number"]
        )
        if str(artifact["build_number"]) != str(expected_build):
            fail(f"{platform} artifact build number differs from source")
        if artifact["sha256"] != require_sha256(args.artifact_sha256, f"{platform} artifact SHA-256"):
            fail(f"{platform} artifact digest changed before upload")
        if platform == "android":
            if args.package != source["applications"]["android_application_id"]:
                fail("Google Play package argument does not match the canonical source record")
            bundle = Path(args.bundle)
            if not bundle.is_file() or bundle.is_symlink() or sha256_file(bundle) != artifact["sha256"]:
                fail("Android bundle bytes do not match the validated artifact descriptor")
        else:
            require_store_id(args.app_id, "App Store Connect app ID")
            require_store_id(args.internal_group_id, "TestFlight internal group ID")
        candidate_sha = source["source"]["commit_sha"]
        artifact_sha = artifact["sha256"]
    return {
        "schema_version": 1,
        "mode": "validation_only",
        "mutation_performed": False,
        "command": args.command,
        "candidate_commit_sha": candidate_sha,
        "artifact_sha256": artifact_sha,
        "created_at": utc_now(),
    }


def add_apple_auth(arguments: argparse.ArgumentParser) -> None:
    arguments.add_argument("--issuer-id", default="")
    arguments.add_argument("--key-id", default="")
    arguments.add_argument("--private-key", default="")


def add_execute_output(arguments: argparse.ArgumentParser) -> None:
    arguments.add_argument("--execute", action="store_true")
    arguments.add_argument("--output", required=True)


def build_parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    google_internal = commands.add_parser("google-internal")
    google_internal.add_argument("--source", required=True)
    google_internal.add_argument("--artifact", required=True)
    google_internal.add_argument("--bundle", required=True)
    google_internal.add_argument("--artifact-sha256", required=True)
    google_internal.add_argument("--package", required=True)
    google_internal.add_argument("--credentials", required=False)
    google_internal.add_argument("--prior-receipt")
    google_internal.add_argument(
        "--recover-only",
        action="store_true",
        help="read back a possibly completed prior upload and never send the AAB",
    )
    add_execute_output(google_internal)

    google_unique = commands.add_parser("google-check-unique")
    google_unique.add_argument("--package", required=True)
    google_unique.add_argument("--version-code", required=True)
    google_unique.add_argument("--credentials", required=False)
    google_unique.add_argument("--execute", action="store_true")

    google_promote = commands.add_parser("google-promote")
    google_promote.add_argument("--manifest", required=True)
    google_promote.add_argument("--operation", required=True, choices=("external", "production"))
    google_promote.add_argument("--source-track", required=True)
    google_promote.add_argument("--destination-track", required=True)
    google_promote.add_argument("--package", required=True)
    google_promote.add_argument("--credentials", required=False)
    google_promote.add_argument("--external-receipt")
    add_execute_output(google_promote)

    apple_unique = commands.add_parser("apple-check-unique")
    apple_unique.add_argument("--app-id", required=True)
    apple_unique.add_argument("--bundle-id", required=True)
    apple_unique.add_argument("--build-number", required=True)
    add_apple_auth(apple_unique)
    apple_unique.add_argument("--execute", action="store_true")

    apple_internal = commands.add_parser("apple-internal")
    apple_internal.add_argument("--source", required=True)
    apple_internal.add_argument("--artifact", required=True)
    apple_internal.add_argument("--artifact-sha256", required=True)
    apple_internal.add_argument("--app-id", required=True)
    apple_internal.add_argument("--internal-group-id", required=True)
    apple_internal.add_argument("--upload-request-id", default="")
    apple_internal.add_argument("--upload-transport-receipt")
    apple_internal.add_argument("--prior-receipt")
    apple_internal.add_argument("--max-polls", type=int, default=60)
    apple_internal.add_argument("--poll-seconds", type=int, default=30)
    add_apple_auth(apple_internal)
    add_execute_output(apple_internal)

    apple_external = commands.add_parser("apple-external")
    apple_external.add_argument("--manifest", required=True)
    apple_external.add_argument("--app-id", required=True)
    apple_external.add_argument("--bundle-id", required=True)
    apple_external.add_argument("--external-group-id", required=True)
    add_apple_auth(apple_external)
    add_execute_output(apple_external)

    apple_production = commands.add_parser("apple-production")
    apple_production.add_argument("--manifest", required=True)
    apple_production.add_argument("--external-receipt", required=True)
    apple_production.add_argument("--app-id", required=True)
    apple_production.add_argument("--bundle-id", required=True)
    apple_production.add_argument("--app-store-version-id", required=True)
    apple_production.add_argument("--submit", action="store_true")
    add_apple_auth(apple_production)
    add_execute_output(apple_production)

    validate_external = commands.add_parser("validate-external-receipt")
    validate_external.add_argument("--manifest", required=True)
    validate_external.add_argument("--receipt", required=True)
    validate_external.add_argument("--platform", required=True, choices=("android", "ios"))

    create_external = commands.add_parser("create-external-evidence")
    create_external.add_argument("--manifest", required=True)
    create_external.add_argument("--platform", required=True, choices=("android", "ios", "both"))
    create_external.add_argument("--android-receipt")
    create_external.add_argument("--ios-receipt")
    create_external.add_argument("--run-id", required=True)
    create_external.add_argument("--run-attempt", required=True)
    create_external.add_argument("--workflow-source-sha", required=True)
    create_external.add_argument("--created-at", default=None)
    create_external.add_argument("--output", required=True)

    validate_evidence = commands.add_parser("validate-external-evidence")
    validate_evidence.add_argument("--manifest", required=True)
    validate_evidence.add_argument("--evidence", required=True)
    validate_evidence.add_argument("--run-id", required=True)
    validate_evidence.add_argument("--run-attempt", required=True)
    validate_evidence.add_argument("--workflow-source-sha", required=True)
    validate_evidence.add_argument("--require-platform", choices=("android", "ios"))
    validate_evidence.add_argument("--receipt-output")

    validate_upload = commands.add_parser("validate-apple-upload-transport")
    validate_upload.add_argument("--artifact", required=True)
    validate_upload.add_argument("--receipt", required=True)
    validate_upload.add_argument("--upload-request-id", required=True)
    return root


def main() -> int:
    args = build_parser().parse_args()
    if args.command == "validate-external-receipt":
        candidate = load_json(Path(args.manifest))
        validate_manifest(candidate)
        validate_external_receipt(Path(args.receipt), candidate, args.platform)
        return 0
    if args.command == "create-external-evidence":
        value = create_external_evidence(
            Path(args.manifest),
            args.platform,
            Path(args.android_receipt) if args.android_receipt else None,
            Path(args.ios_receipt) if args.ios_receipt else None,
            args.run_id,
            args.run_attempt,
            args.workflow_source_sha,
            args.created_at or utc_now(),
        )
        atomic_write_json(Path(args.output), value)
        validate_external_evidence(
            load_json(Path(args.output)),
            Path(args.manifest),
            args.run_id,
            args.run_attempt,
            args.workflow_source_sha,
        )
        return 0
    if args.command == "validate-external-evidence":
        value = validate_external_evidence(
            load_json(Path(args.evidence)),
            Path(args.manifest),
            args.run_id,
            args.run_attempt,
            args.workflow_source_sha,
        )
        if args.require_platform:
            if args.require_platform not in value["platforms"]:
                fail(f"External evidence does not include {args.require_platform}")
            if not args.receipt_output:
                fail("A receipt output is required when selecting a platform")
            atomic_write_json(Path(args.receipt_output), value["receipts"][args.require_platform])
        elif args.receipt_output:
            fail("A receipt output requires --require-platform")
        return 0
    if args.command == "validate-apple-upload-transport":
        artifact = load_json(Path(args.artifact))
        validate_artifact_descriptor(artifact, "ios")
        validate_apple_upload_transport(Path(args.receipt), artifact, args.upload_request_id)
        return 0
    if args.command == "apple-check-unique":
        if not args.execute:
            require_store_id(args.app_id, "App Store Connect app ID")
            if args.bundle_id != policy()["applications"]["ios"]["store_bundle_id"]:
                fail("App Store Connect Bundle ID is not the canonical Store identity")
            require_positive_int(args.build_number, "iOS build number")
            print("validation-only: no App Store Connect request was made")
            return 0
        client = apple_client(args)
        app_id = require_store_id(args.app_id, "App Store Connect app ID")
        if args.bundle_id != policy()["applications"]["ios"]["store_bundle_id"]:
            fail("App Store Connect Bundle ID is not the canonical Store identity")
        validate_apple_app(client, app_id, args.bundle_id)
        client.assert_build_absent(
            app_id,
            str(require_positive_int(args.build_number, "iOS build number")),
        )
        return 0
    if args.command == "google-check-unique":
        package = require_store_id(args.package, "Google Play package")
        if package != policy()["applications"]["android"]["store_application_id"]:
            fail("Google Play package is not the canonical Store identity")
        require_positive_int(args.version_code, "Android version code")
        if not args.execute:
            print("validation-only: no Google Play request was made")
            return 0
        if not args.credentials:
            fail("Google Play credentials are required in execute mode")
        google_check_unique_execute(args)
        return 0
    if not args.execute:
        atomic_write_json(Path(args.output), validation_plan(args))
        return 0
    if args.command.startswith("google") and not args.credentials:
        fail("Google Play credentials are required in execute mode")
    if args.command == "google-internal":
        result = google_internal_execute(args)
    elif args.command == "google-promote":
        manifest = load_json(Path(args.manifest))
        validate_manifest(manifest)
        if args.operation == "production":
            if not args.external_receipt:
                fail("Google production promotion requires an external-testing receipt")
            external = validate_external_receipt(Path(args.external_receipt), manifest, "android")
            if external["destination_track"] != args.source_track:
                fail("Google production source track differs from the tested external track")
        result = google_promote_execute(args)
    elif args.command == "apple-internal":
        if args.max_polls < 1 or args.max_polls > 120 or args.poll_seconds < 1 or args.poll_seconds > 120:
            fail("Apple processing poll bounds are outside the reviewed policy")
        result = apple_internal_execute(args)
    elif args.command == "apple-external":
        result = apple_external_execute(args)
    elif args.command == "apple-production":
        result = apple_production_execute(args)
    else:
        fail("Unsupported Store operation")
    atomic_write_json(Path(args.output), result)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReleaseError as error:
        print(f"store operation failed: {error}", file=sys.stderr)
        raise SystemExit(2)
