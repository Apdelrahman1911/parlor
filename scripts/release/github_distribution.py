#!/usr/bin/env python3
"""Immutable GitHub distribution custody. Separate from (disabled) Store delivery."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import http.client
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

from scripts.release import release_tool

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/github-distribution"
REPOSITORY = "Apdelrahman1911/parlor"
WORKFLOW = ".github/workflows/github-distribution.yml"
PLATFORMS = {"android": "apk", "macos-arm64": "dmg", "macos-x64": "dmg", "windows-x64": "msi", "linux-x64": "deb"}
MAX_FILE = 1024 * 1024 * 1024
MAX_ARCHIVE = 2 * MAX_FILE
MAX_JSON = 1024 * 1024
REQUIRED_CHECKS = {
    "Common, desktop, and Android release", "Desktop strict verification (Linux arm64)",
    "Desktop and Kotlin Native strict verification (macOS x64)",
    "Desktop, Kotlin Native, and Android resources strict verification (Windows x64)",
    "iOS simulator runtime and Swift host", "iOS release frameworks and Swift wrapper",
}
SIGNING_ENVIRONMENTS = {"android": "github-sign-android", "macos-arm64": "github-sign-macos",
                        "macos-x64": "github-sign-macos", "windows-x64": "github-sign-windows", "linux-x64": "github-sign-linux"}


def require(value: bool, message: str) -> None:
    if not value:
        raise RuntimeError(message)


def canonical(value: dict) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode()


def digest(path: Path) -> str:
    require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= MAX_FILE, "Invalid artifact file")
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def load(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_JSON, "Invalid descriptor file")
    raw = path.read_bytes()
    value = json.loads(raw)
    require(isinstance(value, dict) and canonical(value) == raw, "Descriptor must be canonical JSON")
    return value


def version() -> dict:
    name, build = release_tool.version_values()
    return {"name": name, "build": build}


def tag_name(value: dict) -> str:
    require(set(value) == {"name", "build"}, "Version keys differ")
    installer_version(value["name"])
    require(type(value["build"]) is int and 0 < value["build"] < 2_100_000_000, "Invalid build number")
    return f"github-v{value['name']}-b{value['build']}"


def installer_version(name: str) -> tuple[int, int, int]:
    require(isinstance(name, str) and re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", name) is not None,
            "Invalid canonical installer version")
    components = tuple(int(part) for part in name.split("."))
    require(all(value <= bound for value, bound in zip(components, (255, 255, 65535))), "Version exceeds MSI product-version limits")
    return components


def require_new_release_version(api: GitHub, value: dict) -> None:
    """MSI upgrades compare marketing versions, while Android compares build codes.

    New public installers must increase BOTH. Labels order versions here; the
    independently verified manifest/tag/attestations still prove provenance.
    Include drafts to reserve their versions and never silently supersede them.
    """
    current_tag = tag_name(value)
    for page in range(1, 11):
        releases = api.request(f"/releases?per_page=100&page={page}")
        require(isinstance(releases, list) and len(releases) <= 100, "Invalid release inventory")
        for release in releases:
            tag = release.get("tag_name", "")
            if not tag.startswith("github-v") or tag == current_tag:
                continue
            match = re.fullmatch(r"github-v([0-9]+\.[0-9]+\.[0-9]+)-b([1-9][0-9]*)", tag)
            require(match is not None, "Unrecognized GitHub distribution version tag")
            require(installer_version(value["name"]) > installer_version(match[1]) and value["build"] > int(match[2]),
                    "A new GitHub release must increase both marketing version (MSI upgrades) and build number (Android upgrades)")
        if len(releases) < 100:
            return
    raise RuntimeError("Release inventory exceeds its bounded review window")


def filename(platform: str, value: dict) -> str:
    tag_name(value)
    require(platform in PLATFORMS, "Unknown distribution platform")
    return f"Parlor-{value['name']}-b{value['build']}-{platform}.{PLATFORMS[platform]}"


def source(local: bool = False) -> dict:
    sha = release_tool.run_git("rev-parse", "HEAD")
    tree = release_tool.run_git("rev-parse", "HEAD^{tree}")
    dirty = bool(release_tool.run_git("status", "--porcelain"))
    if not local:
        require(not dirty, "Distribution requires a clean source checkout")
        require(sha == os.environ.get("GITHUB_SHA") == os.environ.get("GITHUB_WORKFLOW_SHA"), "Source and workflow SHA differ")
        require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Unexpected repository")
    return {"commit": sha, "tree": tree, "dirty": dirty}


def validate_source(value: dict) -> None:
    require(isinstance(value, dict) and set(value) == {"commit", "tree", "dirty"}, "Source fields differ")
    require(value["dirty"] is False and all(isinstance(value[key], str) and re.fullmatch(r"[0-9a-f]{40}", value[key])
                                           for key in ("commit", "tree")), "Source is not an immutable clean commit/tree")


def run(command: list[str], *, timeout: int = 120, env: dict | None = None, success_codes: tuple[int, ...] = (0,)) -> str:
    # Never echo a signing command, its environment, or raw failure output.
    with tempfile.TemporaryFile() as output:
        process = None
        try:
            process = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, env=env)
            started = time.monotonic()
            while process.poll() is None:
                require(time.monotonic() - started < timeout, "Distribution tool exceeded its time bound")
                require(os.fstat(output.fileno()).st_size <= 16 * MAX_JSON, "Tool output exceeds its bound")
                time.sleep(0.05)
            require(os.fstat(output.fileno()).st_size <= 16 * MAX_JSON, "Tool output exceeds its bound")
            require(process.returncode in success_codes, f"Distribution tool failed: {Path(command[0]).name} (exit {process.returncode})")
            output.seek(0)
            text = output.read().decode("utf-8", errors="replace")
        except (OSError, subprocess.SubprocessError):
            raise RuntimeError("Distribution tool could not execute; private arguments/output withheld") from None
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
    return text


class GitHub:
    def __init__(self) -> None:
        self.token = os.environ.get("GH_TOKEN", "")
        require(bool(self.token), "A scoped GitHub token is required")

    def request(self, path: str, method: str = "GET", body: dict | None = None, missing: bool = False):
        require(path.startswith("/") and ".." not in path, "Invalid API path")
        data = canonical(body) if body is not None else None
        request = urllib.request.Request(f"https://api.github.com/repos/{REPOSITORY}{path}", data=data, method=method,
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                     "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28"})
        try:
            with release_tool.github_opener().open(request, timeout=60) as response:
                raw = response.read(MAX_JSON + 1)
                require(len(raw) <= MAX_JSON, "GitHub response exceeds its bound")
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            code = error.code
            error.close()
            if missing and code == 404:
                return None
            raise RuntimeError(f"GitHub request failed (HTTP {code}); no mutation was retried") from None
        except urllib.error.URLError:
            raise RuntimeError("GitHub request failed; no mutation was retried") from None

    def artifacts(self, run_id: int) -> list[dict]:
        page = self.request(f"/actions/runs/{run_id}/artifacts?per_page=100")
        require(page["total_count"] <= 100, "Too many artifacts in selected run")
        return page["artifacts"]

    def download(self, path: str, destination: Path, maximum: int = MAX_FILE) -> None:
        require(path.startswith("/") and ".." not in path, "Invalid download API path")
        require(not destination.exists(), "Refusing to overwrite download")
        url = f"https://api.github.com/repos/{REPOSITORY}{path}"
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {self.token}", "Accept": "application/octet-stream"})
        try:
            with release_tool.github_opener().open(request, timeout=180) as response, destination.open("xb") as output:
                total = 0
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    total += len(block)
                    require(total <= maximum, "Download exceeds its bound")
                    output.write(block)
        except (urllib.error.HTTPError, urllib.error.URLError):
            raise RuntimeError("GitHub download failed") from None

    def upload(self, release_id: int, path: Path) -> None:
        digest(path)
        connection = http.client.HTTPSConnection("uploads.github.com", timeout=300)
        try:
            route = f"/repos/{REPOSITORY}/releases/{release_id}/assets?name={urllib.parse.quote(path.name, safe='')}"
            connection.putrequest("POST", route)
            connection.putheader("Authorization", f"Bearer {self.token}")
            connection.putheader("Content-Type", "application/octet-stream")
            connection.putheader("Content-Length", str(path.stat().st_size))
            connection.endheaders()
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    connection.send(block)
            response = connection.getresponse()
            require(response.status == 201, f"GitHub asset upload failed (HTTP {response.status}); retry via readback only")
            require(len(response.read(MAX_JSON + 1)) <= MAX_JSON, "Upload response exceeds its bound")
        finally:
            connection.close()


def artifact_name(mode: str, platform: str) -> str:
    return f"ghdist-{mode}-{tag_name(version())}-{platform}"


def validate_descriptor(value: dict, directory: Path, expected_source: dict, expected_mode: str, run_id: int) -> None:
    validate_source(expected_source)
    keys = {"schema", "repository", "source", "version", "platform", "mode", "run_id", "run_attempt", "created_at", "artifact", "validation"}
    require(set(value) == keys and type(value["schema"]) is int and value["schema"] == 1, "Unsupported distribution descriptor")
    require(value["repository"] == REPOSITORY and value["source"] == expected_source, "Artifact source differs")
    require(expected_source.get("dirty") is False, "Dirty source is not releasable")
    require(value["mode"] == expected_mode and expected_mode in {"candidate", "rehearsal"}, "Artifact mode differs")
    require(type(value["run_id"]) is int and value["run_id"] == run_id and run_id > 0, "Artifact run differs")
    require(type(value["run_attempt"]) is int and value["run_attempt"] > 0, "Invalid build attempt")
    require(isinstance(value["created_at"], str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", value["created_at"]),
            "Invalid audit timestamp")
    datetime.strptime(value["created_at"], "%Y-%m-%dT%H:%M:%SZ")  # Audit only; never provenance authority.
    require(value["version"] == version(), "Artifact version differs")
    require(value["platform"] in PLATFORMS, "Unsupported artifact platform")
    record = value["artifact"]
    require(set(record) == {"filename", "sha256", "bytes"}, "Artifact fields differ")
    require(record["filename"] == filename(value["platform"], value["version"]), "Artifact filename differs")
    binary = directory / record["filename"]
    require(type(record["bytes"]) is int and binary.stat().st_size == record["bytes"], "Artifact size differs")
    require(digest(binary) == record["sha256"], "Artifact digest differs")
    validation = value["validation"]
    require(isinstance(validation, dict) and validation.get("passed") is True, "Artifact validation did not pass")
    validation_keys = {"passed", "artifact_sha256", "package_inspection", "signing"}
    if value["platform"] != "android":
        validation_keys.add("image_sha256")
    if expected_mode == "candidate":
        validation_keys.add("acceptance")
        if value["platform"] != "linux-x64":
            validation_keys.add("certificate_sha256")
        if value["platform"].startswith("macos-"):
            validation_keys |= {"team_id", "notarization", "stapled", "gatekeeper", "nested_signatures"}
        elif value["platform"] == "windows-x64":
            validation_keys |= {"timestamped", "nested_signatures"}
    require(set(validation) == validation_keys, "Unreviewed or missing validation fields; never expose private signing diagnostics")
    require(validation.get("artifact_sha256") == record["sha256"] and validation.get("package_inspection") is True,
            "Validation is not bound to the inspected final bytes")
    if value["platform"] != "android":
        require(re.fullmatch(r"[0-9a-f]{64}", validation.get("image_sha256", "")) is not None, "Missing complete installed-image custody")
    required = "unsigned-rehearsal" if expected_mode == "rehearsal" else {
        "android": "android-apk", "macos-arm64": "developer-id-notarized", "macos-x64": "developer-id-notarized",
        "windows-x64": "authenticode-timestamped", "linux-x64": "checksum-and-provenance",
    }[value["platform"]]
    require(validation.get("signing") == required, "Signing policy differs")
    if expected_mode == "candidate" and value["platform"] != "linux-x64":
        require(re.fullmatch(r"[0-9a-f]{64}", validation.get("certificate_sha256", "")) is not None, "Missing signing certificate pin")
    if expected_mode == "candidate":
        acceptance = validation.get("acceptance", {})
        require(set(acceptance) == {"source", "reference"} and acceptance.get("source") == expected_source["commit"]
                and valid_acceptance_reference(acceptance.get("reference", "")),
                "Missing exact-source physical/distribution acceptance")
        if value["platform"].startswith("macos-"):
            require(validation.get("notarization") == "Accepted" and validation.get("stapled") is True
                    and validation.get("gatekeeper") is True and validation.get("nested_signatures") is True,
                    "Incomplete Developer ID/notarization validation")
            require(re.fullmatch(r"[A-Z0-9]{10}", validation.get("team_id", "")) is not None, "Missing Developer ID team")
        if value["platform"] == "windows-x64":
            require(validation.get("timestamped") is True and validation.get("nested_signatures") is True,
                    "Incomplete timestamped Windows validation")


def valid_acceptance_reference(value: str) -> bool:
    return isinstance(value, str) and re.fullmatch(r"https://github\.com/Apdelrahman1911/parlor/issues/[1-9][0-9]*(?:#issuecomment-[0-9]+)?", value) is not None


def require_environment(api: GitHub, name: str) -> None:
    require(name in set(SIGNING_ENVIRONMENTS.values()) | {"github-publish"}, "Unexpected protected environment")
    data = api.request(f"/environments/{name}")
    rules = [rule for rule in data.get("protection_rules", []) if rule.get("type") == "required_reviewers"]
    require(len(rules) == 1 and rules[0].get("prevent_self_review") is True and bool(rules[0].get("reviewers")),
            "Distribution environment needs reviewers and prevention of self-review")
    require(data.get("can_admins_bypass") is False, "Distribution environment cannot allow admin bypass")
    require(data.get("deployment_branch_policy") == {"protected_branches": False, "custom_branch_policies": True},
            "Distribution environment must restrict deployment refs explicitly")
    policies = api.request(f"/environments/{name}/deployment-branch-policies?per_page=100")
    selected = {(row.get("type"), row.get("name")) for row in policies.get("branch_policies", [])}
    allowed = {("tag", "github-v*")} if name == "github-publish" else {("branch", "main"), ("branch", "feat/last-light")}
    require(0 < policies.get("total_count", 0) == len(selected) and selected <= allowed,
            "Distribution environment has unreviewed/empty deployment refs")
    current = ("tag" if os.environ.get("GITHUB_REF_TYPE") == "tag" else "branch", os.environ.get("GITHUB_REF_NAME"))
    if name == os.environ.get("GH_DIST_ENVIRONMENT"):
        require(current in selected or (name == "github-publish" and current == ("tag", tag_name(version()))),
                "Current ref is outside the protected deployment policy")


def require_approval(api: GitHub, name: str, current: dict) -> dict:
    require(os.environ.get("GH_DIST_ENVIRONMENT") == name, "Job is not bound to the expected protected environment")
    require_environment(api, name)
    require(os.environ.get("GH_DIST_APPROVED_SHA") == current["commit"], "Protected approval must pin the exact source SHA")
    require(os.environ.get("GH_DIST_ACCEPTANCE_SHA") == current["commit"] and
            valid_acceptance_reference(os.environ.get("GH_DIST_ACCEPTANCE_REFERENCE", "")),
            "Owner physical/distribution acceptance must cover this exact source")
    return {"source": current["commit"], "reference": os.environ["GH_DIST_ACCEPTANCE_REFERENCE"]}


def require_certificate_pins(manifest: dict) -> None:
    for record in manifest["artifacts"]:
        platform = record["platform"]
        if platform == "linux-x64":
            continue
        family = "MACOS" if platform.startswith("macos-") else "WINDOWS" if platform.startswith("windows-") else "ANDROID"
        expected = os.environ.get(f"GH_DIST_{family}_CERT_SHA256", "")
        require(re.fullmatch(r"[0-9a-f]{64}", expected) is not None and expected == record["validation"]["certificate_sha256"],
                "Signed artifact differs from the protected publication certificate pin")
        if family == "MACOS":
            require(record["validation"]["team_id"] == os.environ.get("GH_DIST_MACOS_TEAM_ID"), "Developer ID team differs from publication policy")


def extract_archive(archive: Path, directory: Path, allowed: set[str]) -> None:
    require(not directory.exists(), "Refusing to overwrite extracted bundle")
    with zipfile.ZipFile(archive) as zipped:
        entries = zipped.infolist()
        require({e.filename for e in entries} == allowed and len(entries) == len(allowed), "Unexpected or duplicate artifact paths")
        require(sum(e.file_size for e in entries) <= MAX_ARCHIVE, "Artifact expands beyond its bound")
        for entry in entries:
            require(Path(entry.filename).name == entry.filename and "\\" not in entry.filename, "Unsafe archive path")
            require(not entry.is_dir() and not entry.flag_bits & 1 and entry.file_size <= MAX_FILE, "Invalid archive member")
            require(stat.S_IFMT(entry.external_attr >> 16) in {0, stat.S_IFREG}, "Nonregular archive entry")
        directory.mkdir(parents=True)
        for entry in entries:
            with zipped.open(entry) as stream, (directory / entry.filename).open("xb") as output:
                shutil.copyfileobj(stream, output, 1024 * 1024)


def fetch_artifact(api: GitHub, run_id: int, name: str, destination: Path, allowed: set[str]) -> None:
    matches = [a for a in api.artifacts(run_id) if a.get("name") == name]
    require(len(matches) == 1 and matches[0].get("expired") is False, "Frozen artifact is missing, expired, or ambiguous")
    item = matches[0]
    require(0 < item["size_in_bytes"] <= MAX_ARCHIVE, "Frozen archive size is invalid")
    with tempfile.TemporaryDirectory(prefix="parlor-distribution-") as temporary:
        archive = Path(temporary) / "artifact.zip"
        api.download(f"/actions/artifacts/{int(item['id'])}/zip", archive, MAX_ARCHIVE)
        require("sha256:" + release_tool.sha256_file(archive) == item.get("digest"), "GitHub archive digest mismatch")
        extract_archive(archive, destination, allowed)


def attest_verified(path: Path, sha: str) -> None:
    run(["gh", "attestation", "verify", str(path), "--repo", REPOSITORY,
         "--signer-workflow", f"{REPOSITORY}/{WORKFLOW}", "--source-digest", sha, "--signer-digest", sha,
         "--deny-self-hosted-runners"], timeout=180)


def require_verification(api: GitHub, sha: str) -> int:
    runs = api.request(f"/actions/workflows/production-verification.yml/runs?head_sha={sha}&per_page=30")["workflow_runs"]
    for record in runs:
        if record.get("head_sha") != sha or record.get("conclusion") != "success" or record.get("event") not in {"push", "workflow_dispatch"}:
            continue
        require(record.get("head_repository", {}).get("full_name") == REPOSITORY, "Verification is from another repository")
        jobs = api.request(f"/actions/runs/{int(record['id'])}/jobs?filter=latest&per_page=100")
        require(jobs["total_count"] <= 100, "Too many verification jobs")
        passed = {job["name"] for job in jobs["jobs"] if job.get("conclusion") == "success"}
        if REQUIRED_CHECKS <= passed:
            return int(record["id"])
    raise RuntimeError("The exact source needs all six successful full production-verification jobs; focused or historical runs cannot qualify it")


def require_tag(api: GitHub, sha: str) -> None:
    tag = tag_name(version())
    require(os.environ.get("GITHUB_REF") == f"refs/tags/{tag}", "Publication must run from the exact version/build tag")
    obj = api.request(f"/git/ref/tags/{tag}")["object"]
    for _ in range(5):
        if obj.get("type") == "commit":
            require(obj.get("sha") == sha, "Release tag does not resolve to the frozen source")
            return
        require(obj.get("type") == "tag", "Unsupported tag object")
        obj = api.request(f"/git/tags/{obj['sha']}")["object"]
    raise RuntimeError("Excessively nested release tag")


def preflight() -> None:
    current = source()
    mode = "publish" if os.environ.get("GITHUB_REF_TYPE") == "tag" else os.environ.get("GH_DIST_INPUT_MODE") or "rehearsal"
    require(mode in {"rehearsal", "candidate", "publish"}, "Unsupported distribution operation")
    api = GitHub()
    if mode != "rehearsal":
        require_verification(api, current["commit"])
    candidate = os.environ.get("GH_DIST_CANDIDATE_RUN", "")
    if mode == "publish":
        require_tag(api, current["commit"])
        if not candidate:
            runs = api.request(f"/actions/workflows/github-distribution.yml/runs?head_sha={current['commit']}&per_page=30")["workflow_runs"]
            matches = []
            for item in runs:
                if item.get("conclusion") == "success" and any(a.get("name") == artifact_name("candidate", "bundle")
                        for a in api.artifacts(int(item["id"]))):
                    matches.append(str(item["id"]))
            require(len(matches) == 1, "Tag publication requires one unambiguous signed candidate; select its run explicitly on dispatch")
            candidate = matches[0]
        require(re.fullmatch(r"[1-9][0-9]*", candidate) is not None, "Invalid signed candidate run")
    elif mode == "candidate":
        require(os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and os.environ.get("GITHUB_REF_NAME") in {"main", "feat/last-light"},
                "Signing needs explicit dispatch on a reviewed source branch")
        for name in set(SIGNING_ENVIRONMENTS.values()) | {"github-publish"}:
            require_environment(api, name)
        require_new_release_version(api, version())
        existing = release_tool.list_repository_artifacts(REPOSITORY, api.token)
        prefix = artifact_name("candidate", "")
        require(not any(a.get("name", "").startswith(prefix) and str(a.get("workflow_run", {}).get("id")) != os.environ["GITHUB_RUN_ID"]
                        for a in existing), "This version/build already has frozen candidate work; resume failed jobs or allocate a new reviewed build number")
        require(api.request(f"/releases/tags/{tag_name(version())}", missing=True) is None, "Version/build already has a release")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"mode={mode}\ncandidate_run={candidate}\nversion_tag={tag_name(version())}\n")


def assert_new(platform: str) -> None:
    source()
    api = GitHub()
    mode = os.environ["GH_DIST_MODE"]
    require(not any(a.get("name") == artifact_name(mode, platform) for a in api.artifacts(int(os.environ["GITHUB_RUN_ID"]))),
            "A frozen platform artifact already exists. Rerun failed jobs only; never rebuild or overwrite it")
    if mode == "candidate":
        require_approval(api, SIGNING_ENVIRONMENTS[platform], source())


def freeze(platform: str, local: bool) -> None:
    mode = "rehearsal" if local else os.environ["GH_DIST_MODE"]
    require(mode in {"rehearsal", "candidate"}, "Publication cannot build or freeze")
    output = OUT / "frozen"
    require(not output.exists(), "Frozen output already exists")
    output.mkdir(parents=True)
    binary = OUT / "work" / filename(platform, version())
    validation = load(OUT / "validation.json")
    value = {"schema": 1, "repository": REPOSITORY, "source": source(local), "version": version(), "platform": platform,
             "mode": mode, "run_id": int(os.environ.get("GITHUB_RUN_ID", "0")),
             "run_attempt": int(os.environ.get("GITHUB_RUN_ATTEMPT", "1")),
             "created_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
             "artifact": {"filename": binary.name, "sha256": digest(binary), "bytes": binary.stat().st_size}, "validation": validation}
    shutil.copyfile(binary, output / binary.name)
    (output / f"{platform}.json").write_bytes(canonical(value))
    if not local:
        validate_descriptor(value, output, source(), mode, int(os.environ["GITHUB_RUN_ID"]))


def seal() -> None:
    mode = os.environ["GH_DIST_MODE"]
    require(mode in {"candidate", "rehearsal"}, "Unsupported seal mode")
    current = source()
    api = GitHub()
    run_id = int(os.environ["GITHUB_RUN_ID"])
    output = OUT / "bundle"
    require(not output.exists(), "Bundle is immutable")
    output.mkdir(parents=True)
    descriptors = []
    for platform in PLATFORMS:
        directory = OUT / "downloads" / platform
        fetch_artifact(api, run_id, artifact_name(mode, platform), directory, {filename(platform, version()), f"{platform}.json"})
        descriptor_path = directory / f"{platform}.json"
        descriptor = load(descriptor_path)
        validate_descriptor(descriptor, directory, current, mode, run_id)
        for path in directory.iterdir():
            attest_verified(path, current["commit"])
        shutil.copyfile(directory / descriptor["artifact"]["filename"], output / descriptor["artifact"]["filename"])
        descriptors.append(descriptor)
    manifest = {"schema": 1, "repository": REPOSITORY, "source": current, "version": version(), "mode": mode,
                "candidate_run": run_id, "artifacts": descriptors}
    (output / "release-manifest.json").write_bytes(canonical(manifest))
    checksums = "".join(f"{digest(p)}  {p.name}\n" for p in sorted(output.iterdir()))
    (output / "SHA256SUMS").write_text(checksums, encoding="ascii")


def validate_bundle(directory: Path, current: dict, run_id: int) -> dict:
    manifest = load(directory / "release-manifest.json")
    require(set(manifest) == {"schema", "repository", "source", "version", "mode", "candidate_run", "artifacts"}, "Bundle fields differ")
    require(type(manifest["schema"]) is int and manifest["schema"] == 1 and manifest["repository"] == REPOSITORY
            and manifest["mode"] == "candidate", "Not a signed candidate")
    require(type(manifest["candidate_run"]) is int, "Candidate run must be a numeric run identifier")
    require(manifest["source"] == current and manifest["candidate_run"] == run_id and manifest["version"] == version(), "Candidate custody differs")
    records = manifest["artifacts"]
    require(len(records) == len(PLATFORMS) and {r["platform"] for r in records} == set(PLATFORMS), "Incomplete platform coverage")
    for record in records:
        validate_descriptor(record, directory, current, "candidate", run_id)
    expected = {filename(p, version()) for p in PLATFORMS} | {"release-manifest.json", "SHA256SUMS"}
    require({p.name for p in directory.iterdir()} == expected, "Unexpected public bundle files")
    checksums = "".join(f"{digest(p)}  {p.name}\n" for p in sorted(directory.iterdir()) if p.name != "SHA256SUMS")
    require((directory / "SHA256SUMS").read_text(encoding="ascii") == checksums, "Bundle checksum file differs")
    return manifest


def publish_files(api: GitHub, directory: Path, current: dict, run_id: int) -> dict:
    validate_bundle(directory, current, run_id)
    require_tag(api, current["commit"])
    tag = tag_name(version())
    title = f"Parlor {version()['name']} (build {version()['build']})"
    body = (f"Android APK, macOS DMGs, Windows MSI and Linux DEB. Same-LAN play only.\n\n"
            f"Source: `{current['commit']}`\nTree: `{current['tree']}`\n"
            f"Frozen candidate: https://github.com/{REPOSITORY}/actions/runs/{run_id}\n\n"
            "Verify downloads using SHA256SUMS and release-manifest.json. This is GitHub distribution, not Store publication.")
    release = api.request(f"/releases/tags/{tag}", missing=True)
    if release is None or release.get("draft") is True:
        require_new_release_version(api, version())
    if release is None:
        release = api.request("/releases", "POST", {"tag_name": tag, "target_commitish": current["commit"],
                              "name": title, "body": body, "draft": True, "prerelease": False})
    def require_identity(record: dict) -> None:
        require(record["tag_name"] == tag and record["name"] == title and record["body"] == body and record["prerelease"] is False,
                "Existing release belongs to another candidate or was modified; refusing overwrite")

    require_identity(release)
    release_id = int(release["id"])
    expected = {p.name: p for p in directory.iterdir()}
    assets = release["assets"]
    require(len(assets) == len({a["name"] for a in assets}) and {a["name"] for a in assets} <= expected.keys(), "Unexpected release assets")
    for name, local in expected.items():
        found = [a for a in assets if a["name"] == name]
        if found:
            verify_remote_asset(api, found[0], local)
        else:
            require(release["draft"] is True, "A visible release is incomplete; do not silently change it")
            api.upload(release_id, local)
    ready = api.request(f"/releases/{release_id}")
    require_identity(ready)
    require({a["name"] for a in ready["assets"]} == expected.keys() and len(ready["assets"]) == len(expected), "Release upload is incomplete")
    for asset in ready["assets"]:
        verify_remote_asset(api, asset, expected[asset["name"]])
    require_tag(api, current["commit"])
    if ready["draft"]:
        require_new_release_version(api, version())
        api.request(f"/releases/{release_id}", "PATCH", {"draft": False})
    final = api.request(f"/releases/{release_id}")
    require_identity(final)
    require(final["draft"] is False and final["html_url"] == f"https://github.com/{REPOSITORY}/releases/tag/{tag}", "Release is not publicly visible")
    require({a["name"] for a in final["assets"]} == expected.keys() and len(final["assets"]) == len(expected), "Public asset readback differs")
    for asset in final["assets"]:
        verify_remote_asset(api, asset, expected[asset["name"]])
    return final


def verify_remote_asset(api: GitHub, asset: dict, local: Path) -> None:
    require(asset["size"] == local.stat().st_size, "Existing release asset size conflicts; no overwrite")
    with tempfile.TemporaryDirectory(prefix="parlor-release-readback-") as temporary:
        path = Path(temporary) / "asset"
        api.download(f"/releases/assets/{int(asset['id'])}", path)
        require(digest(path) == digest(local), "Existing release asset bytes conflict; no overwrite")


def publish(candidate: int) -> None:
    current = source()
    api = GitHub()
    require_approval(api, "github-publish", current)
    require_verification(api, current["commit"])
    record = api.request(f"/actions/runs/{candidate}")
    require(record.get("head_sha") == current["commit"] and record.get("conclusion") == "success" and
            record.get("head_repository", {}).get("full_name") == REPOSITORY and
            record.get("path", "").split("@", 1)[0] == WORKFLOW, "Untrusted or incomplete signed candidate run")
    directory = OUT / "publication"
    fetch_artifact(api, candidate, artifact_name("candidate", "bundle"), directory,
                   {filename(p, version()) for p in PLATFORMS} | {"release-manifest.json", "SHA256SUMS"})
    manifest = validate_bundle(directory, current, candidate)
    require_certificate_pins(manifest)
    attest_verified(directory / "release-manifest.json", current["commit"])
    for platform in PLATFORMS:
        attest_verified(directory / filename(platform, version()), current["commit"])
    released = publish_files(api, directory, current, candidate)
    print(f"Verified public release: {released['html_url']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["preflight", "assert-new", "freeze", "seal", "publish"])
    parser.add_argument("--platform", choices=list(PLATFORMS))
    parser.add_argument("--candidate", type=int)
    parser.add_argument("--local", action="store_true", help="Unsigned dirty-tree build evidence only; never publishable")
    args = parser.parse_args()
    if args.operation == "preflight":
        preflight()
    elif args.operation == "assert-new":
        require(args.platform is not None, "Platform required")
        assert_new(args.platform)
    elif args.operation == "freeze":
        require(args.platform is not None, "Platform required")
        freeze(args.platform, args.local)
    elif args.operation == "seal":
        seal()
    else:
        require(args.candidate is not None and args.candidate > 0 and not args.local, "Frozen candidate required")
        publish(args.candidate)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        safe = str(error) if type(error) is RuntimeError else type(error).__name__
        print(f"GitHub distribution failed: {safe}", file=sys.stderr)
        raise SystemExit(2)
