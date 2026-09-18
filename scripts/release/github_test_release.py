#!/usr/bin/env python3
"""Explicit public testing prereleases, separate from protected signed releases."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import re
import shutil
import sys

from scripts.release import github_distribution as dist

ROOT = dist.ROOT
OUT = ROOT / "build/github-test-release"
WORKFLOW = ".github/workflows/github-test-release.yml"
MODE = "public-test"
MANIFEST = "test-release-manifest.json"
REQUIREMENTS = {
    "android": "disposable-test-key", "macos-arm64": "adhoc-unnotarized", "macos-x64": "adhoc-unnotarized",
    "windows-x64": "unsigned", "linux-x64": "checksum-only",
}


def filename(platform: str) -> str:
    dist.require(platform in dist.PLATFORMS, "Unknown test platform")
    value = dist.version()
    dist.tag_name(value)
    return f"Parlor-Test-{value['name']}-b{value['build']}-{platform}.{dist.PLATFORMS[platform]}"


def artifact_name(platform: str) -> str:
    dist.require(platform in dist.PLATFORMS or platform == "bundle", "Unknown test artifact")
    return f"gh-test-{dist.tag_name(dist.version())}-{platform}"


def tag_name(run_id: int) -> str:
    dist.require(type(run_id) is int and run_id > 0, "Invalid test build run")
    return dist.tag_name(dist.version()).replace("github-v", "github-test-v", 1) + f"-r{run_id}"


def require_dispatch(mode: str) -> dict:
    current = dist.source()
    dist.require(mode in {"build", "publish"} and os.environ.get("GH_TEST_INPUT_MODE") == mode,
                 "An explicit test operation is required")
    ref = os.environ.get("GITHUB_REF")
    dist.require(os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
                 and ref in {"refs/heads/main", "refs/heads/feat/last-light"}
                 and os.environ.get("GITHUB_WORKFLOW_REF") == f"{dist.REPOSITORY}/{WORKFLOW}@{ref}",
                 "Public testing requires a maintainer dispatch on the reviewed repository workflow/branch")
    if mode == "publish":
        dist.require(os.environ.get("GH_TEST_PUBLISH_ACK") == "true",
                     "Publication requires explicit acknowledgement of unsigned test-build limitations")
    return current


def preflight() -> None:
    mode = os.environ.get("GH_TEST_INPUT_MODE", "")
    current = require_dispatch(mode)
    selected = os.environ.get("GH_TEST_INPUT_RUN", "")
    if mode == "publish":
        dist.require(re.fullmatch(r"[1-9][0-9]*", selected) is not None, "Select the exact successful test build run")
        api = dist.GitHub()
        dist.require_verification(api, current["commit"])
        require_build_run(api, int(selected), current)
    else:
        dist.require(not selected, "A build cannot select or replace a previous run")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"mode={mode}\nversion_tag={dist.tag_name(dist.version())}\n")


def assert_new(platform: str) -> None:
    require_dispatch("build")
    api = dist.GitHub()
    dist.require(not any(a.get("name") == artifact_name(platform) for a in api.artifacts(int(os.environ["GITHUB_RUN_ID"]))),
                 "Test platform is already frozen. Rerun failed jobs only; never rebuild or replace published bytes")


def validate_descriptor(value: dict, directory: Path, current: dict, run_id: int) -> None:
    dist.validate_source(current)
    keys = {"schema", "repository", "source", "version", "mode", "platform", "build_run", "run_attempt", "created_at", "artifact", "validation"}
    dist.require(set(value) == keys and type(value["schema"]) is int and value["schema"] == 1, "Invalid test descriptor fields")
    dist.require(value["repository"] == dist.REPOSITORY and value["source"] == current and value["version"] == dist.version(),
                 "Test artifact source or version differs")
    dist.require(value["mode"] == MODE and value["platform"] in dist.PLATFORMS, "Not a public-test artifact")
    dist.require(type(value["build_run"]) is int and value["build_run"] == run_id and run_id > 0,
                 "Test build run differs")
    dist.require(type(value["run_attempt"]) is int and value["run_attempt"] > 0, "Invalid test build attempt")
    dist.require(isinstance(value["created_at"], str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", value["created_at"]),
                 "Invalid test artifact timestamp")
    datetime.strptime(value["created_at"], "%Y-%m-%dT%H:%M:%SZ")
    platform, record = value["platform"], value["artifact"]
    dist.require(set(record) == {"filename", "sha256", "bytes"} and record["filename"] == filename(platform), "Test artifact filename differs")
    binary = directory / record["filename"]
    dist.require(type(record["bytes"]) is int and binary.stat().st_size == record["bytes"] and dist.digest(binary) == record["sha256"],
                 "Test artifact bytes differ")
    validation = value["validation"]
    required = {"passed", "signing", "package_inspection", "artifact_sha256"}
    required |= {"certificate_sha256", "application_id", "install_launch_smoke"} if platform == "android" else {"image_sha256"}
    dist.require(set(validation) == required and validation["passed"] is True and validation["package_inspection"] is True,
                 "Test artifact inspection is missing or contains unreviewed fields")
    dist.require(validation["signing"] == REQUIREMENTS[platform] and validation["artifact_sha256"] == record["sha256"],
                 "Test signing policy or inspected bytes differ")
    if platform == "android":
        dist.require(validation["application_id"] == "me.parlor.android.test" and validation["install_launch_smoke"] is True,
                     "Android public tests must be isolated and normally installable")
        digest = validation["certificate_sha256"]
    else:
        digest = validation["image_sha256"]
    dist.require(isinstance(digest, str) and re.fullmatch(r"[0-9a-f]{64}", digest), "Missing test certificate/image custody")


def build(platform: str) -> None:
    current = require_dispatch("build")
    dist.require(platform in dist.PLATFORMS, "Unknown public-test platform")
    frozen, work = OUT / "frozen", OUT / "work"
    dist.require(not frozen.exists() and not work.exists(), "Refusing to overwrite test build outputs")
    work.mkdir(parents=True)
    binary = work / filename(platform)
    if platform == "android":
        from scripts.release import android_test_package
        validation = android_test_package.build(binary)
    else:
        from scripts.release import desktop_package as packages
        packages.build(platform)  # Preserve the already-reviewed native preparation/probe/installed-image checks.
        before = dist.load(dist.OUT / "validation.json")
        original = packages.WORK / dist.filename(platform, dist.version())
        dist.require(before["passed"] is True and before["package_inspection"] is True
                     and before["signing"] == "unsigned-rehearsal" and before["artifact_sha256"] == dist.digest(original),
                     "Native test build is not an inspected unsigned rehearsal")
        shutil.copyfile(original, binary)
        validation = {**before, "signing": REQUIREMENTS[platform]}
    dist.require(dist.source() == current, "Source changed during the test build")
    descriptor = {"schema": 1, "repository": dist.REPOSITORY, "source": current, "version": dist.version(), "mode": MODE,
                  "platform": platform, "build_run": int(os.environ["GITHUB_RUN_ID"]),
                  "run_attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
                  "created_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                  "artifact": {"filename": binary.name, "sha256": dist.digest(binary), "bytes": binary.stat().st_size},
                  "validation": validation}
    validate_descriptor(descriptor, work, current, descriptor["build_run"])
    frozen.mkdir()
    shutil.copyfile(binary, frozen / binary.name)
    (frozen / f"{platform}.json").write_bytes(dist.canonical(descriptor))
    validate_descriptor(descriptor, frozen, current, descriptor["build_run"])
    print(f"Frozen public test artifact (not production signed): {binary.name}")


def attest_verified(path: Path, sha: str) -> None:
    dist.run(["gh", "attestation", "verify", str(path), "--repo", dist.REPOSITORY,
              "--signer-workflow", f"{dist.REPOSITORY}/{WORKFLOW}", "--source-digest", sha, "--signer-digest", sha,
              "--deny-self-hosted-runners"], timeout=180)


def bundle_files() -> set[str]:
    return {filename(platform) for platform in dist.PLATFORMS} | {MANIFEST, "SHA256SUMS"}


def checksums(directory: Path) -> str:
    return "".join(f"{dist.digest(path)}  {path.name}\n" for path in sorted(directory.iterdir()) if path.name != "SHA256SUMS")


def validate_bundle(directory: Path, current: dict, run_id: int) -> dict:
    value = dist.load(directory / MANIFEST)
    dist.require(set(value) == {"schema", "repository", "source", "version", "mode", "build_run", "artifacts"}, "Test bundle fields differ")
    dist.require(type(value["schema"]) is int and value["schema"] == 1 and value["repository"] == dist.REPOSITORY and value["mode"] == MODE,
                 "Not a public-test bundle")
    dist.require(value["source"] == current and value["version"] == dist.version()
                 and type(value["build_run"]) is int and value["build_run"] == run_id, "Test bundle custody differs")
    records = value["artifacts"]
    dist.require(isinstance(records, list) and len(records) == len(dist.PLATFORMS)
                 and {r["platform"] for r in records} == set(dist.PLATFORMS), "Incomplete test platform coverage")
    for record in records:
        validate_descriptor(record, directory, current, run_id)
    dist.require({path.name for path in directory.iterdir()} == bundle_files(), "Unexpected public test files")
    sums = directory / "SHA256SUMS"
    dist.digest(sums)
    dist.require(sums.stat().st_size < dist.MAX_JSON and sums.read_text(encoding="ascii") == checksums(directory), "Test bundle checksums differ")
    return value


def seal() -> None:
    current = require_dispatch("build")
    api, run_id = dist.GitHub(), int(os.environ["GITHUB_RUN_ID"])
    bundle = OUT / "bundle"
    dist.require(not bundle.exists(), "Test bundle is immutable")
    bundle.mkdir(parents=True)
    records = []
    for platform in dist.PLATFORMS:
        directory = OUT / "downloads" / platform
        dist.fetch_artifact(api, run_id, artifact_name(platform), directory, {filename(platform), f"{platform}.json"})
        record = dist.load(directory / f"{platform}.json")
        validate_descriptor(record, directory, current, run_id)
        for path in directory.iterdir():
            attest_verified(path, current["commit"])
        shutil.copyfile(directory / filename(platform), bundle / filename(platform))
        records.append(record)
    manifest = {"schema": 1, "repository": dist.REPOSITORY, "source": current, "version": dist.version(),
                "mode": MODE, "build_run": run_id, "artifacts": records}
    (bundle / MANIFEST).write_bytes(dist.canonical(manifest))
    (bundle / "SHA256SUMS").write_text(checksums(bundle), encoding="ascii")
    validate_bundle(bundle, current, run_id)


def require_build_run(api: dist.GitHub, run_id: int, current: dict) -> None:
    record = api.request(f"/actions/runs/{run_id}")
    dist.require(record.get("head_sha") == current["commit"] and record.get("status") == "completed"
                 and record.get("conclusion") == "success" and record.get("event") == "workflow_dispatch"
                 and record.get("head_repository", {}).get("full_name") == dist.REPOSITORY
                 and record.get("head_branch") in {"main", "feat/last-light"}
                 and record.get("path", "").split("@", 1)[0] == WORKFLOW, "Untrusted, stale or incomplete public-test build run")
    jobs = api.request(f"/actions/runs/{run_id}/jobs?filter=latest&per_page=100")
    expected = {f"Build public test {platform}" for platform in dist.PLATFORMS} | {"Seal public test bundle"}
    dist.require(jobs["total_count"] <= 100 and expected <= {j["name"] for j in jobs["jobs"] if j.get("conclusion") == "success"},
                 "Every public-test platform and sealing job must pass")


def require_tag(api: dist.GitHub, current: dict, run_id: int, *, create: bool = False) -> None:
    tag = tag_name(run_id)
    record = api.request(f"/git/ref/tags/{tag}", missing=True)
    if record is None and create:
        api.request("/git/refs", "POST", {"ref": f"refs/tags/{tag}", "sha": current["commit"]})
        record = api.request(f"/git/ref/tags/{tag}")
    dist.require(record is not None and record.get("object") == {"type": "commit", "sha": current["commit"],
                 "url": f"https://api.github.com/repos/{dist.REPOSITORY}/git/commits/{current['commit']}"},
                 "Test tag is missing or does not point directly to the exact source; never move a tag")


def release_body(current: dict, build_run: int, verification_run: int) -> str:
    return ("## Experimental public test builds — NOT a production release\n\n"
            "These downloads are intentionally not publisher-signed/notarized production apps. Use test data only. "
            "Same-Wi-Fi/LAN play only; no Internet matchmaking or host migration. Physical multiplayer/accessibility acceptance is not claimed.\n\n"
            "### Downloads and installation\n"
            "- **Android 8+:** install the Android APK as **Parlor Test** (`me.parlor.android.test`). Android requires a signature, "
            "so this APK uses a disposable test key, not the Store or Debug key. Allow installation from your browser when prompted. "
            "Different test releases require uninstalling the old Parlor Test first; this deletes its local sessions. Store/Debug apps stay separate.\n"
            "- **macOS 11+:** choose `macos-arm64` for Apple Silicon or `macos-x64` for Intel, open the DMG and copy Parlor to Applications. "
            "The app is ad-hoc signed, NOT Apple-notarized. If macOS blocks it, verify the checksum first, then use System Settings → Privacy & Security → Open Anyway for this app only. "
            "Do not disable Gatekeeper globally; managed devices may prohibit unsigned apps.\n"
            "- **Windows x64:** run the MSI. It is unsigned and may show an unknown-publisher/SmartScreen warning. "
            "Proceed only if you trust this repository and have checked the hash; do not disable Windows security.\n"
            "- **Linux x64 (Debian/Ubuntu):** install the DEB with `sudo apt install ./Parlor-Test-*-linux-x64.deb`. "
            "The package is checksum/attestation verified, not publisher-signed. Other distributions are not packaged here.\n\n"
            "Desktop installers use the normal Parlor app name, installation and storage locations. Do not overwrite an important existing install; "
            "use a separate OS account for isolated testing. Desktop snapshot keys are accessible to same-user processes and cold-start rejoin credential recovery is not supported. "
            "For the same desktop version, uninstall the previous test package before installing a different build.\n\n"
            "No iOS app or Store upload is included. The existing iOS A37 protection finding remains unresolved.\n\n"
            f"Verify downloads with `SHA256SUMS` and `{MANIFEST}`. The certificate digest in the Android record is a test-key fingerprint, not production trust.\n\n"
            f"Source: `{current['commit']}`\nTree: `{current['tree']}`\n"
            f"Frozen test build: https://github.com/{dist.REPOSITORY}/actions/runs/{build_run}\n"
            f"All six automated verification jobs: https://github.com/{dist.REPOSITORY}/actions/runs/{verification_run}\n")


def publish_files(api: dist.GitHub, directory: Path, current: dict, run_id: int) -> dict:
    dist.require(require_dispatch("publish") == current and str(run_id) == os.environ.get("GH_TEST_INPUT_RUN"),
                 "Publication authority, current source or explicitly selected test run changed")
    validate_bundle(directory, current, run_id)
    verification_run = dist.require_verification(api, current["commit"])
    require_build_run(api, run_id, current)
    tag = tag_name(run_id)
    title = f"Parlor {dist.version()['name']} — unsigned test build {run_id}"
    body = release_body(current, run_id, verification_run)
    def identity(record: dict) -> None:
        dist.require(record["tag_name"] == tag and record["name"] == title and record["body"] == body and record["prerelease"] is True,
                     "Existing release differs or is not a testing prerelease; refusing overwrite")

    release = api.request(f"/releases/tags/{tag}", missing=True)
    if release is not None:
        identity(release)
    # An existing release must already have its exact immutable tag. Never
    # reconstruct a deleted tag or mutate Git before checking release identity.
    require_tag(api, current, run_id, create=release is None)
    if release is None:
        release = api.request("/releases", "POST", {"tag_name": tag, "target_commitish": current["commit"], "name": title,
                              "body": body, "draft": True, "prerelease": True, "make_latest": "false"})
    identity(release)
    release_id = int(release["id"])
    expected = {path.name: path for path in directory.iterdir()}
    assets = release["assets"]
    dist.require(len(assets) == len({a["name"] for a in assets}) and {a["name"] for a in assets} <= expected.keys(), "Unexpected test release assets")
    for name, local in expected.items():
        found = [asset for asset in assets if asset["name"] == name]
        if found:
            dist.verify_remote_asset(api, found[0], local)
        else:
            dist.require(release["draft"] is True, "A visible test release is incomplete; never silently mutate it")
            api.upload(release_id, local)
    ready = api.request(f"/releases/{release_id}")
    identity(ready)
    dist.require(len(ready["assets"]) == len(expected) and {a["name"] for a in ready["assets"]} == expected.keys(), "Incomplete test release upload")
    for asset in ready["assets"]:
        dist.verify_remote_asset(api, asset, expected[asset["name"]])
    require_tag(api, current, run_id)
    if ready["draft"]:
        api.request(f"/releases/{release_id}", "PATCH", {"draft": False, "prerelease": True, "make_latest": "false"})
    final = api.request(f"/releases/{release_id}")
    identity(final)
    dist.require(final["draft"] is False and final["html_url"] == f"https://github.com/{dist.REPOSITORY}/releases/tag/{tag}",
                 "Test release is not publicly visible")
    dist.require(len(final["assets"]) == len(expected) and {a["name"] for a in final["assets"]} == expected.keys(), "Public test assets differ")
    for asset in final["assets"]:
        dist.verify_remote_asset(api, asset, expected[asset["name"]])
    return final


def publish(run_id: int) -> None:
    current = require_dispatch("publish")
    dist.require(str(run_id) == os.environ.get("GH_TEST_INPUT_RUN"), "Publication must use the explicitly selected test run")
    api = dist.GitHub()
    require_build_run(api, run_id, current)
    dist.require_verification(api, current["commit"])
    directory = OUT / "publication"
    dist.fetch_artifact(api, run_id, artifact_name("bundle"), directory, bundle_files())
    validate_bundle(directory, current, run_id)
    for path in directory.iterdir():
        attest_verified(path, current["commit"])
    released = publish_files(api, directory, current, run_id)
    print(f"Verified public TEST prerelease (not production signed): {released['html_url']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("preflight", "assert-new", "build", "seal", "publish"))
    parser.add_argument("--platform", choices=tuple(dist.PLATFORMS))
    parser.add_argument("--run", type=int)
    args = parser.parse_args()
    if args.operation in {"assert-new", "build"}:
        dist.require(args.platform is not None, "Platform required")
        (assert_new if args.operation == "assert-new" else build)(args.platform)
    elif args.operation == "publish":
        dist.require(args.run is not None, "Frozen test build run required")
        publish(args.run)
    else:
        (preflight if args.operation == "preflight" else seal)()


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, KeyError, TypeError) as error:
        print(f"Public test release failed: {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(2)
