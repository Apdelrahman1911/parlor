#!/usr/bin/env python3
"""Opt-in, source-bound A/B continuation in the existing verification workflow.

Preflight observes controls, never builds. A later independently approved dispatch
consumes that exact artifact. The canonical native runners remain the sole owners
of Gradle, copies, processes and simulators; this adapter never relaxes their gates.
"""
from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import os
import platform
import re
import shutil
import signal
import stat
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "Apdelrahman1911/parlor"
BRANCH = "fix/local-readiness-2026-09-07"
WORKFLOW = ".github/workflows/production-verification.yml"
CAMPAIGN = "remediation-runs/2026-09-08-continuation"
BINDING_NAME = "ios-readiness-source-ci.json"
PROFILE = "qualified-xcode-26.3"
SUPPORT = "scripts/verification/ios-readiness"
OLD_CAMPAIGN = "remediation-runs/2026-09-07-local-readiness"
RUNNERS = {
    "l08": OLD_CAMPAIGN + "/native/l08-app-foundation-composition-01/compose_runner.py",
    "normal": OLD_CAMPAIGN + "/native/normal-ios-launch-proposal-01/run_normal_ios_launch.py",
}
CYCLES = {"l08": "ios-readiness-17", "normal": "ios-readiness-18"}
PREFLIGHT_FILES = {"preflight.json", BINDING_NAME, "l08-controls.json", "normal-controls.json"}
HEX40 = re.compile(r"[0-9a-f]{40}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
POSITIVE = re.compile(r"[1-9][0-9]{0,19}\Z")
MAX_FILE = 4 * 1024 * 1024
MAX_ZIP = 8 * 1024 * 1024


class NativeInterrupted(Exception):
    """Leave a running child's wait without bypassing its owned finalizer."""


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def now():
    return datetime.now(timezone.utc).isoformat()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, indent=2) + "\n").encode()


def decode(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "duplicate-json-key")
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique)


def file_bytes(path, maximum=MAX_FILE):
    path = Path(path)
    before = path.lstat()
    require(path.resolve() == path and stat.S_ISREG(before.st_mode) and
            before.st_uid == os.getuid() and before.st_nlink == 1 and
            0 <= before.st_size <= maximum, "unsafe-or-unbounded-file")
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as source:
        opened = os.fstat(source.fileno())
        identity = lambda value: (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns,
                                  value.st_mode, value.st_uid, value.st_nlink)
        require(identity(opened) == identity(before), "file-replaced-before-open")
        raw = source.read(maximum + 1)
        require(identity(os.fstat(source.fileno())) == identity(opened), "file-changed-while-open")
    after = path.lstat()
    require(identity(before) == identity(after) and len(raw) == before.st_size, "file-changed-during-read")
    return raw


def write_new(path, raw):
    require(path.parent.resolve() == path.parent, "redirected-output-parent")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(raw)
        output.flush()
        os.fsync(output.fileno())


def custody(path):
    value = path.lstat()
    require(path.resolve() == path and not path.is_symlink() and value.st_uid == os.getuid(),
            "unowned-or-redirected-path")
    return {"device": value.st_dev, "inode": value.st_ino, "uid": value.st_uid}


def command(arguments, root=ROOT, timeout=120):
    # The read-only GitHub token is needed by the downloader, not by child tools.
    environment = {key: value for key, value in os.environ.items()
                   if key != "PARLOR_ACTIONS_READ_TOKEN"}
    result = subprocess.run(list(map(str, arguments)), cwd=root, env=environment,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    require(len(result.stdout) <= MAX_FILE and len(result.stderr) <= MAX_FILE,
            "control-command-output-limit")
    require(result.returncode == 0, "control-command-failed: " + Path(str(arguments[0])).name)
    return result.stdout


def effective_scope(event, requested):
    if event != "workflow_dispatch":
        return "full"
    require(requested in {"full", "native-preflight", "native-evidence"}, "unknown-verification-scope")
    return requested


def context(env, root=ROOT):
    require(env.get("GITHUB_ACTIONS") == "true" and env.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and env.get("GITHUB_JOB") == "ios", "focused-mode-needs-ios-dispatch")
    require(env.get("GITHUB_REPOSITORY") == REPOSITORY and
            env.get("GITHUB_REF") == "refs/heads/" + BRANCH and
            env.get("GITHUB_WORKFLOW_REF") == REPOSITORY + "/" + WORKFLOW + "@refs/heads/" + BRANCH,
            "unexpected-repository-branch-or-workflow")
    commit = env.get("PARLOR_FROZEN_SOURCE_SHA", "")
    require(HEX40.fullmatch(commit) and commit == env.get("GITHUB_SHA") == env.get("GITHUB_WORKFLOW_SHA"),
            "dispatch-is-not-the-explicit-frozen-source")
    require(all(POSITIVE.fullmatch(env.get(key, "")) for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")),
            "invalid-run-identity")
    require(root == root.resolve() and Path(env.get("GITHUB_WORKSPACE", "")).resolve() == root,
            "unexpected-checkout-root")
    git = lambda *args: command(["git", *args], root).decode().strip()
    require(git("rev-parse", "--show-toplevel") == str(root) and git("rev-parse", "HEAD") == commit and
            git("branch", "--show-current") == BRANCH and git("rev-parse", "--is-shallow-repository") == "false",
            "frozen-branch-or-full-history-mismatch")
    require(git("status", "--porcelain=v1", "--untracked-files=no") == "", "tracked-checkout-not-clean")
    return dict(repository=REPOSITORY, branch=BRANCH, head_sha=commit, tree=git("rev-parse", "HEAD^{tree}"),
                workflow=WORKFLOW, root=str(root), run_id=int(env["GITHUB_RUN_ID"]),
                run_attempt=int(env["GITHUB_RUN_ATTEMPT"]))


def qualified_platform(root=ROOT):
    require(platform.system() == "Darwin" and platform.machine() == "arm64", "qualified-arm64-macos-required")
    specification = importlib.util.spec_from_file_location("_native_ci_toolchain", root / SUPPORT / "toolchain_profiles.py")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    module.developer_environment(PROFILE, os.environ)
    value = module.validate_observation(PROFILE,
        command(["/usr/bin/xcodebuild", "-version"]).decode(),
        command(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"]).decode(),
        command(["/usr/bin/xcode-select", "-p"]).decode(), platform.machine())
    require(command(["/usr/bin/xcrun", "--sdk", "iphoneos", "--show-sdk-version"]).decode().strip() == value["sdk"],
            "qualified-device-sdk-mismatch")
    sdk_path = Path(command(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"]).decode().strip())
    require(sdk_path.is_absolute() and sdk_path.is_dir() and
            Path(value["developer_dir"]).resolve() in sdk_path.resolve().parents, "sdk-path-outside-qualified-xcode")
    runtimes = decode(command(["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"]))
    matches = [row for row in runtimes["runtimes"] if row.get("identifier") == value["runtime"]]
    require(len(matches) == 1 and matches[0].get("isAvailable") is True and matches[0].get("version") == value["sdk"],
            "qualified-runtime-unavailable")
    device = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
    types = decode(command(["/usr/bin/xcrun", "simctl", "list", "devicetypes", "--json"]))
    require(len([row for row in types["devicetypes"] if row.get("identifier") == device]) == 1,
            "qualified-device-type-unavailable")
    android = Path.home() / "Library/Android/sdk"
    require(android.is_dir() and android.resolve() == android and
            all(not os.environ.get(key) or Path(os.environ[key]).resolve() == android
                for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT")), "unexpected-public-android-sdk")
    java = Path(command(["/usr/libexec/java_home", "-v", "21"]).decode().strip())
    require(java.is_absolute() and java.name == "Home" and (java / "bin/java").is_file(), "public-jdk21-unavailable")
    value.update(runtime_build=matches[0].get("buildversion"), device_type=device,
                 simulator_sdk_path=str(sdk_path), android_sdk=str(android), java_home=str(java),
                 runtime_scope="Toolchain observation only; no application/ABI/protection/runtime claim")
    return value


def describe(root=ROOT):
    return decode(command(["/usr/bin/python3", "-B", root / SUPPORT / "bind_source.py", "--describe"], root))


def validate_controls(value, label):
    keys = {"control_sha256", "files"} | ({"generated_runner_sha256"} if label == "l08" else set())
    require(isinstance(value, dict) and set(value) == keys and isinstance(value["files"], list)
            and 1 <= len(value["files"]) <= 512, "invalid-control-manifest")
    paths = []
    for row in value["files"]:
        require(isinstance(row, dict) and set(row) == {"path", "sha256"}, "invalid-control-row")
        name = row["path"]
        require(isinstance(name, str) and name and not name.startswith("/") and "\\" not in name and
                all(part not in {"", ".", ".."} for part in name.split("/")) and
                isinstance(row["sha256"], str) and HEX64.fullmatch(row["sha256"]), "unsafe-control-path-or-hash")
        paths.append(name)
    require(len(set(paths)) == len(paths) and value["control_sha256"] ==
            sha(json.dumps(value["files"], separators=(",", ":")).encode()), "control-hash-mismatch")
    if label == "l08":
        require(isinstance(value["generated_runner_sha256"], str) and HEX64.fullmatch(value["generated_runner_sha256"]),
                "missing-composed-runner-hash")
    return value


def controls(binding, root=ROOT):
    result = {}
    for label, runner in RUNNERS.items():
        raw = command(["/usr/bin/python3", "-B", root / runner, "--control-manifest", binding], root)
        result[label] = (raw, validate_controls(decode(raw), label))
    return result


def unpack_preflight(raw):
    require(0 < len(raw) <= MAX_ZIP, "unbounded-preflight-zip")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(len(entries) == len(PREFLIGHT_FILES) and {row.filename for row in entries} == PREFLIGHT_FILES,
                "unexpected-or-duplicate-preflight-zip-members")
        require(sum(row.file_size for row in entries) <= MAX_ZIP, "unbounded-preflight-contents")
        result = {}
        for row in entries:
            mode = row.external_attr >> 16
            require(not row.is_dir() and not row.flag_bits & 1 and
                    stat.S_IFMT(mode) in {0, stat.S_IFREG} and 0 < row.file_size <= MAX_FILE and
                    row.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}, "unsafe-preflight-zip-member")
            with archive.open(row) as source:
                value = source.read(MAX_FILE + 1)
            require(len(value) == row.file_size, "preflight-member-length-mismatch")
            result[row.filename] = value
        return result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


def read_response(response, maximum):
    length = response.headers.get("Content-Length")
    require(length is None or length.isdigit() and int(length) <= maximum, "http-content-length-limit")
    raw = response.read(maximum + 1)
    require(len(raw) <= maximum, "http-body-size-limit")
    return raw


def api_request(endpoint, token):
    require(endpoint.startswith("/repos/" + REPOSITORY + "/actions/") and ".." not in endpoint and
            "#" not in endpoint and isinstance(token, str) and bool(token), "invalid-read-only-api-request")
    request = urllib.request.Request("https://api.github.com" + endpoint,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28"}, method="GET")
    return urllib.request.build_opener(NoRedirect).open(request, timeout=60)


def api_json(endpoint, token):
    with api_request(endpoint, token) as response:
        require(response.status == 200, "unexpected-api-status")
        return decode(read_response(response, MAX_FILE))


def download_zip(endpoint, token):
    try:
        response = api_request(endpoint, token)
    except urllib.error.HTTPError as response:
        require(response.code == 302, "artifact-api-did-not-provide-signed-redirect")
        location = response.headers.get("Location", "")
    else:
        response.close()
        raise RuntimeError("artifact-api-did-not-provide-signed-redirect")
    url = urllib.parse.urlsplit(location)
    require(url.scheme == "https" and url.hostname is not None and
            url.hostname.endswith(".blob.core.windows.net") and url.port in {None, 443} and
            not url.username and not url.password and not url.fragment, "unapproved-artifact-redirect")
    # NEVER forward the API Authorization header or retain this signed URL.
    request = urllib.request.Request(location, method="GET")
    with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
        require(response.status == 200, "unexpected-artifact-download-status")
        return read_response(response, MAX_ZIP)


def validate_producer(run, workflow, artifact, expected):
    require(isinstance(run, dict) and run.get("id") == expected["run_id"] and
            type(run.get("run_attempt")) is int and run["run_attempt"] == expected["run_attempt"] and
            run.get("event") == "workflow_dispatch" and run.get("status") == "completed" and
            run.get("conclusion") == "success" and run.get("head_sha") == expected["head_sha"] and
            run.get("head_branch") == BRANCH and run.get("path") == WORKFLOW,
            "preflight-run-source-attempt-or-result-mismatch")
    require(run.get("repository", {}).get("full_name") == REPOSITORY and
            run.get("head_repository", {}).get("full_name") == REPOSITORY and
            type(run["repository"].get("id")) is int and
            run["repository"]["id"] == run["head_repository"].get("id"), "preflight-repository-mismatch")
    require(isinstance(workflow, dict) and workflow.get("path") == WORKFLOW and workflow.get("state") == "active"
            and type(workflow.get("id")) is int and workflow["id"] == run.get("workflow_id"),
            "preflight-workflow-identity-mismatch")
    name = "native-preflight-{}-{}".format(expected["run_id"], expected["run_attempt"])
    require(isinstance(artifact, dict) and artifact.get("id") == expected["artifact_id"] and
            artifact.get("name") == name and artifact.get("expired") is False and
            artifact.get("digest") == "sha256:" + expected["artifact_sha256"] and
            type(artifact.get("size_in_bytes")) is int and 0 < artifact["size_in_bytes"] <= MAX_ZIP,
            "preflight-artifact-identity-or-digest-mismatch")
    owner = artifact.get("workflow_run", {})
    require(owner.get("id") == expected["run_id"] and owner.get("head_sha") == expected["head_sha"] and
            owner.get("head_branch") == BRANCH and owner.get("repository_id") == run["repository"]["id"] and
            owner.get("head_repository_id") == run["repository"]["id"], "artifact-run-ownership-mismatch")


def producer_evidence(run, workflow, artifact):
    """Only checked public identities, never API/signed URLs, token or actor data."""
    observed = {"run": {key: run[key] for key in ("id", "run_attempt", "event", "status", "conclusion",
                "head_sha", "head_branch", "path", "workflow_id")},
        "workflow": {key: workflow[key] for key in ("id", "path", "state")},
        "artifact": {key: artifact[key] for key in ("id", "name", "expired", "digest", "size_in_bytes")}}
    for key in ("repository", "head_repository"):
        observed["run"][key] = {field: run[key][field] for field in ("id", "full_name")}
    observed["artifact"]["workflow_run"] = {key: artifact["workflow_run"][key] for key in
        ("id", "head_sha", "head_branch", "repository_id", "head_repository_id")}
    return observed


def validate_package(files, expected, current, root=ROOT):
    preflight = decode(files["preflight.json"])
    require(preflight.get("schema_version") == 1 and preflight.get("kind") == "NATIVE_CONTINUATION_PREFLIGHT"
            and preflight.get("status") == "REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE", "not-a-successful-preflight-package")
    wanted = {**current, "run_id": expected["run_id"], "run_attempt": expected["run_attempt"]}
    require(preflight.get("context") == wanted and preflight.get("binding_relative") == CAMPAIGN + "/" + BINDING_NAME,
            "preflight-source-root-or-producer-mismatch")
    require(preflight.get("files") == {name: sha(raw) for name, raw in files.items() if name != "preflight.json"},
            "preflight-member-digest-mismatch")
    binding = decode(files[BINDING_NAME])
    require(binding.get("schema_version") == 3 and binding.get("repository") == str(root) and
            binding.get("binding_status") == "REVIEW_REQUIRED_BOUND", "invalid-official-source-binding")
    observation = preflight.get("source_observation")
    require(isinstance(observation, dict) and
            {key: binding["source_identity"].get(key) for key in observation} == observation and
            observation.get("commit") == current["head_sha"] and observation.get("tree") == current["tree"] and
            observation.get("branch") == BRANCH and observation.get("diff_sha256") == sha(b"") and
            binding["source_identity"].get("tracked_status") == "", "binding-source-does-not-match-preflight")
    for label in RUNNERS:
        manifest = validate_controls(decode(files[label + "-controls.json"]), label)
        require(manifest["control_sha256"] == expected[label + "_sha256"] == preflight["control_sha256"][label],
                "independent-control-approval-mismatch")
        require({"path": CAMPAIGN + "/" + BINDING_NAME, "sha256": sha(files[BINDING_NAME])} in manifest["files"],
                "control-manifest-does-not-bind-source-file")
    return preflight


def cleanup_is_safe(receipt, approved, source):
    if not isinstance(receipt, dict):
        return False
    stops = receipt.get("gradle_stops", [])
    attempted = ("xcodebuild_exit_code" in receipt or any(
        row.get("command") and Path(str(row["command"][0])).name == "xcodebuild" and
        any(arg in {"test", "build"} for arg in row["command"][1:])
        for row in receipt.get("commands", [])))
    if (not isinstance(stops, list) or any(not isinstance(row, dict) or row.get("exit_code") != 0 for row in stops) or
            attempted and (len(stops) != 2 or {row.get("label") for row in stops} != {"stop-xcode-immediate", "stop-final"})):
        return False
    return (isinstance(receipt, dict) and receipt.get("cleanup_status") == "PASS" and
            receipt.get("cleanup_errors") == [] and not receipt.get("finalizer_error") and
            receipt.get("temporary_directory_removed") is True and receipt.get("source_unchanged") is True and
            receipt.get("controls_unchanged") is True and receipt.get("controls_after_sha256") == approved and
            receipt.get("approved_control_sha256") == approved and
            receipt.get("source_before") == source == receipt.get("source_after") and
            not receipt.get("remaining_outputs") and not receipt.get("original_outputs_preserved") and
            not receipt.get("unexpected_original_outputs_preserved") and
            receipt.get("owned_processes_remaining") == [] and receipt.get("unknown_holders") == [] and
            not receipt.get("secondary_attestation_errors") and
            all(row.get("status") == "PASS" for row in receipt.get("finalization_stages", [])) and
            (not receipt.get("owned_uuid") or receipt.get("owned_device_absent") is True) and
            (not attempted or receipt.get("copied_sources_unchanged") is True))


def walk_failure(error):
    raise error  # os.walk's default silently omits failed directory reads.


def tree_manifest(path):
    require(path.is_dir() and path.resolve() == path, "unsafe-evidence-directory")
    result, total, directories = {}, 0, 0
    for base, dirs, files in os.walk(path, followlinks=False, onerror=walk_failure):
        directories += 1
        require(directories <= 256, "native-evidence-directory-budget-exceeded")
        require(all(not (Path(base) / name).is_symlink() for name in dirs), "symlink-evidence-directory")
        for name in files:
            file = Path(base) / name
            raw = file_bytes(file, 64 * 1024 * 1024)
            total += len(raw)
            result[str(file.relative_to(path))] = sha(raw)
            require(len(result) <= 4096 and total <= 256 * 1024 * 1024, "native-evidence-budget-exceeded")
    return result


def preserve_native_evidence(source, target):
    """Preserve raw bounded evidence BEFORE receipt parsing, including failures.

    Partial preservation never authorizes deletion or another cycle. An invalid
    receipt remains raw evidence, not a successful runtime/cleanup observation.
    """
    result = dict(status="INCOMPLETE", files={}, errors=[])
    if not source.is_dir() or source.resolve() != source:
        result["errors"].append("missing-or-redirected-native-evidence")
        return result
    result["custody"] = custody(source)
    target.mkdir(mode=0o700)
    count, total, directories = 0, 0, 0
    walk = os.walk(source, followlinks=False, onerror=walk_failure)
    while True:
        try:
            base, dirs, files = next(walk)
        except StopIteration:
            break
        except OSError as error:
            result["errors"].append({"type": type(error).__name__, "reason": "directory-read-failed"})
            return result
        directories += 1
        if directories > 256 or len(result["errors"]) >= 128:
            result["errors"].append("native-evidence-traversal-budget-exhausted")
            return result
        for name in list(dirs):
            if (Path(base) / name).is_symlink():
                if len(result["errors"]) >= 128:
                    result["errors"].append("native-evidence-directory-error-budget-exhausted")
                    return result
                result["errors"].append("symlink-directory-refused")
                dirs.remove(name)
        for name in sorted(files, key=lambda value: (value != "receipt.json", value)):
            path = Path(base) / name
            relative = str(path.relative_to(source))
            count += 1
            if count > 4096 or len(result["errors"]) >= 128:
                result["errors"].append("native-evidence-file-or-error-budget-exhausted")
                return result
            try:
                raw = file_bytes(path, 64 * 1024 * 1024)
                total += len(raw)
                if total > 256 * 1024 * 1024:
                    result["errors"].append("native-evidence-total-byte-limit")
                    return result
                output = target / relative
                output.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                write_new(output, raw)
                result["files"][relative] = sha(raw)
            except (OSError, RuntimeError) as error:
                result["errors"].append({"file": relative, "type": type(error).__name__})
    try:
        require(not result["errors"] and tree_manifest(source) == tree_manifest(target) == result["files"] and
                custody(source) == result["custody"], "native-evidence-preservation-incomplete-or-changed")
        result["status"] = "COMPLETE"
    except (OSError, RuntimeError) as error:
        result["errors"].append({"type": type(error).__name__})
    return result


class Continuation:
    def __init__(self, env, root=ROOT):
        self.env, self.root = env, root
        self.scope = effective_scope(env.get("GITHUB_EVENT_NAME"), env.get("PARLOR_DISPATCH_SCOPE"))
        require(self.scope != "full", "full-verification-is-not-a-native-continuation")
        self.context = context(env, root)
        temporary = Path(env["RUNNER_TEMP"]).resolve(strict=True)
        require(root not in temporary.parents and temporary != root, "native-custody-must-be-outside-checkout")
        self.base = temporary / "parlor-native-{}-{}".format(self.context["run_id"], self.context["run_attempt"])
        self.bundle, self.binding = self.base / "bundle", root / CAMPAIGN / BINDING_NAME
        self.cleanup_path = self.base.with_name(self.base.name + "-cleanup.json")
        self.state = None

    def save(self):
        path = self.base / "state.json"
        partial = self.base / "state.writing"
        write_new(partial, json_bytes(self.state))
        partial.replace(path)

    def claim_file(self, path):
        self.state["files"][str(path.relative_to(self.root))] = {"custody": custody(path), "sha256": sha(file_bytes(path))}
        self.save()

    def preflight(self):
        before = describe(self.root)
        command(["/usr/bin/python3", "-B", self.root / SUPPORT / "bind_source.py", self.binding,
                 before["source_manifest_sha256"], before["diff_sha256"]], self.root)
        self.claim_file(self.binding)
        manifests = controls(self.binding, self.root)
        write_new(self.bundle / BINDING_NAME, file_bytes(self.binding))
        for label, (raw, _) in manifests.items():
            write_new(self.bundle / (label + "-controls.json"), raw)
        require(before == describe(self.root), "preflight-source-changed")
        value = dict(schema_version=1, kind="NATIVE_CONTINUATION_PREFLIGHT",
            status="REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE", context=self.context, source_observation=before,
            binding_relative=str(self.binding.relative_to(self.root)), toolchain=self.state["toolchain"],
            files={path.name: sha(file_bytes(path)) for path in self.bundle.iterdir()},
            control_sha256={label: item[1]["control_sha256"] for label, item in manifests.items()},
            scope="No application build, XCTest, libproc ABI/query or storage/protection validation executed.")
        write_new(self.bundle / "preflight.json", json_bytes(value))
        self.state.update(status="PREFLIGHT_REVIEW_REQUIRED", cleanup_safe=True)
        return 0

    def fetch_preflight(self):
        expected = {"head_sha": self.context["head_sha"]}
        for key in ("run_id", "run_attempt", "artifact_id"):
            raw = self.env.get("PARLOR_PREFLIGHT_" + key.upper(), "")
            require(POSITIVE.fullmatch(raw), "missing-explicit-preflight-identity")
            expected[key] = int(raw)
        for key, variable in (("artifact_sha256", "PARLOR_PREFLIGHT_ARTIFACT_SHA256"),
                              ("l08_sha256", "PARLOR_APPROVED_L08_CONTROL_SHA256"),
                              ("normal_sha256", "PARLOR_APPROVED_NORMAL_CONTROL_SHA256")):
            raw = self.env.get(variable, "")
            require(HEX64.fullmatch(raw), "missing-independent-preflight-or-control-hash")
            expected[key] = raw
        require(expected["run_id"] != self.context["run_id"], "preflight-must-be-a-separate-reviewed-run")
        token = self.env.get("PARLOR_ACTIONS_READ_TOKEN", "")
        prefix = "/repos/" + REPOSITORY + "/actions/"
        run = api_json(prefix + "runs/{}/attempts/{}".format(expected["run_id"], expected["run_attempt"]), token)
        workflow = api_json(prefix + "workflows/production-verification.yml", token)
        artifact = api_json(prefix + "artifacts/" + str(expected["artifact_id"]), token)
        validate_producer(run, workflow, artifact, expected)
        raw = download_zip(prefix + "artifacts/{}/zip".format(expected["artifact_id"]), token)
        require(sha(raw) == expected["artifact_sha256"], "downloaded-preflight-zip-digest-mismatch")
        files = unpack_preflight(raw)
        preflight = validate_package(files, expected, self.context, self.root)
        require(preflight["source_observation"] == describe(self.root), "live-source-does-not-match-preflight")
        require(preflight["toolchain"] == self.state["toolchain"], "native-host-toolchain-or-paths-differ-from-preflight")
        retained = self.bundle / "reviewed-preflight"
        retained.mkdir(mode=0o700)
        for name, value in files.items():
            write_new(retained / name, value)
        write_new(self.bundle / "preflight-api-binding.json", json_bytes(dict(expected=expected,
            observed=producer_evidence(run, workflow, artifact), downloaded_sha256=sha(raw))))
        write_new(self.binding, files[BINDING_NAME])
        self.claim_file(self.binding)
        for label, (observed, _) in controls(self.binding, self.root).items():
            require(observed == files[label + "-controls.json"], "live-control-manifest-differs-from-review")
        return expected, decode(files[BINDING_NAME])["source_identity"]

    def bootstrap_cache_directories(self):
        cache = Path(self.env.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))).absolute()
        require(cache.resolve() == cache and cache.parent.is_dir(), "redirected-global-dependency-cache")
        created = []
        for path in (cache, cache / "caches", cache / "wrapper"):
            if not path.exists():
                path.mkdir(mode=0o700)
                created.append(str(path))
            require(path.is_dir() and path.resolve() == path and path.stat().st_uid == os.getuid(),
                    "invalid-dependency-cache-directory")
        self.state["cache_bootstrap"] = dict(created=created, policy="Dependency/distribution cache only; never delete global caches.")
        self.save()

    def invoke_native(self, arguments, log, entry):
        child, finishing, prior = None, False, {}
        def forward(signum, _frame):
            entry["interrupted"] = signum
            if child is not None:
                if not finishing:
                    raise NativeInterrupted()
                if child.poll() is None:
                    child.send_signal(signal.SIGTERM)
        try:
            # Install BEFORE Popen: a signal in its launch/assignment window is
            # queued, then replayed to the exact returned child. No blocked mask
            # is inherited by Xcode/Gradle/native subprocesses.
            for item in (signal.SIGINT, signal.SIGTERM):
                prior[item] = signal.signal(item, forward)
            with log.open("xb") as output:
                # Internal native commands already have bounded deadlines. A cancellation
                # is forwarded, not a subprocess timeout/SIGKILL that skips their finalizer.
                try:
                    child = subprocess.Popen(arguments, cwd=self.root,
                        env={key: value for key, value in self.env.items() if key != "PARLOR_ACTIONS_READ_TOKEN"},
                        stdout=output, stderr=subprocess.STDOUT)
                    if entry.get("interrupted"):
                        raise NativeInterrupted()
                    entry["exit_code"] = child.wait(timeout=6000)
                except (subprocess.TimeoutExpired, NativeInterrupted) as error:
                    finishing = True
                    entry["timed_out"] = isinstance(error, subprocess.TimeoutExpired)
                    child.send_signal(signal.SIGTERM)
                    try:
                        entry["exit_code"] = child.wait(timeout=600)
                    except subprocess.TimeoutExpired:
                        self.save()
                        raise RuntimeError("native-finalizer-did-not-finish-retain-all-custody")
        finally:
            for item, handler in prior.items():
                signal.signal(item, handler)
        entry.update(status="FINISHED", finished_at=now())

    def run_native(self, label, approved, source):
        cycle = CYCLES[label]
        destination = self.root / CAMPAIGN / "evidence" / cycle
        require(not destination.exists() and not destination.is_symlink(), "never-reuse-a-native-cycle")
        arguments = ["/usr/bin/python3", "-B", str(self.root / RUNNERS[label]), cycle, str(self.binding), approved,
                     "--simulator-signing=adhoc", "--toolchain=" + PROFILE]
        if label == "normal":
            arguments.append("--image-observer=libproc")
        entry = dict(cycle=cycle, command=arguments, started_at=now(), status="RUNNING")
        self.state["runs"][label] = entry
        self.state["cleanup_safe"] = False
        self.save()
        try:
            self.invoke_native(arguments, self.bundle / (label + "-runner.log"), entry)
        finally:
            # Even malformed, missing, failing or interrupted native receipts must
            # survive the ephemeral CI host; qualification only starts afterward.
            preserved = preserve_native_evidence(destination, self.bundle / cycle)
            self.state.setdefault("preservation", {})[label] = preserved
            if preserved["status"] == "COMPLETE":
                self.state["directories"][str(destination.relative_to(self.root))] = {
                    "custody": preserved["custody"], "manifest": preserved["files"]}
            lock = self.root / CAMPAIGN / "build-lane.lock"
            if lock.exists() and str(lock.relative_to(self.root)) not in self.state["files"]:
                self.claim_file(lock)
            self.save()
        require(preserved["status"] == "COMPLETE", "native-evidence-not-fully-preserved-retain-canonical-files")
        receipt = decode(file_bytes(destination / "receipt.json", 8 * 1024 * 1024))
        safe = cleanup_is_safe(receipt, approved, source)
        entry.update(receipt_status=receipt.get("status"), cleanup_safe=safe,
                     receipt_sha256=sha(file_bytes(destination / "receipt.json", 8 * 1024 * 1024)))
        self.state["cleanup_safe"] = safe
        self.save()
        require(not entry.get("interrupted") and not entry.get("timed_out"), "native-run-interrupted-or-timed-out")
        require(safe, "native-cleanup-or-source-identity-unsafe-do-not-start-another-cycle")
        require(receipt.get("cycle") == cycle and receipt.get("signing_mode") == "adhoc" and
                receipt.get("toolchain_profile") == PROFILE and
                (label != "normal" or receipt.get("image_observer") == "libproc"), "native-cycle-mode-profile-mismatch")
        return entry["exit_code"], receipt.get("status")

    def evidence(self):
        expected, source = self.fetch_preflight()
        self.bootstrap_cache_directories()
        # A partial/strict failure is retained; it does not prevent the independent
        # normal-source B observation if (and only if) A's actual cleanup is safe.
        results = [self.run_native(label, expected[label + "_sha256"], source) for label in RUNNERS]
        self.state["status"] = "PASS" if all(code == 0 and status == "PASS" for code, status in results) else "NOT_READY"
        return 0 if self.state["status"] == "PASS" else 2

    def run(self):
        require(command(["git", "status", "--porcelain=v1", "--untracked-files=all"], self.root).strip() == b"",
                "native-preflight-needs-fully-clean-checkout")
        require(self.binding.parent.is_dir() and self.binding.parent.resolve() == self.binding.parent,
                "campaign-must-already-exist-in-frozen-source")
        for path in (self.binding, self.binding.parent / "build-lane.lock", *[
                self.binding.parent / "evidence" / name for name in CYCLES.values()]):
            require(not path.exists() and not path.is_symlink(), "pre-existing-continuation-output")
        self.base.mkdir(mode=0o700)
        self.bundle.mkdir(mode=0o700)
        self.state = dict(schema_version=1, context=self.context, scope=self.scope, base_custody=custody(self.base),
            status="RUNNING", started_at=now(), files={}, directories={}, runs={}, cleanup_safe=True)
        self.save()
        code = 1
        try:
            self.state["toolchain"] = qualified_platform(self.root)
            code = self.preflight() if self.scope == "native-preflight" else self.evidence()
        except BaseException as error:
            self.state.update(status="FAIL", error_type=type(error).__name__)
            # API exception strings can contain signed URLs; retain only closed local errors.
            if type(error) is RuntimeError:
                self.state["error"] = str(error)[:500]
            write_new(self.bundle / "failure.json", json_bytes({key: self.state[key] for key in
                      ("status", "error_type", "error") if key in self.state}))
        finally:
            self.state["finished_at"] = now()
            self.save()
            if self.scope == "native-evidence":
                write_new(self.bundle / "continuation.json", json_bytes(self.state))
            self.state["bundle_manifest"] = tree_manifest(self.bundle)
            self.save()
            print(json.dumps({key: self.state.get(key) for key in ("scope", "status", "cleanup_safe", "error")}, indent=2))
        return code

    def cleanup(self):
        receipt = dict(schema_version=1, context=self.context, started_at=now(), status="FAIL", removed=[], errors=[])
        try:
            state = decode(file_bytes(self.base / "state.json", 8 * 1024 * 1024))
            require(state.get("context") == self.context and state.get("base_custody") == custody(self.base),
                    "cleanup-custody-or-source-mismatch")
            require(self.env.get("PARLOR_NATIVE_UPLOAD_OUTCOME") == "success" and
                    POSITIVE.fullmatch(self.env.get("PARLOR_NATIVE_ARTIFACT_ID", "")) and
                    HEX64.fullmatch(self.env.get("PARLOR_NATIVE_ARTIFACT_DIGEST", "")),
                    "upload-custody-unconfirmed-retain-last-local-evidence")
            receipt["upload"] = {"artifact_id": self.env["PARLOR_NATIVE_ARTIFACT_ID"],
                                 "sha256": self.env["PARLOR_NATIVE_ARTIFACT_DIGEST"]}
            require(state.get("cleanup_safe") is True, "native-cleanup-unsafe-retain-owned-evidence")
            require(tree_manifest(self.bundle) == state.get("bundle_manifest"), "uploaded-evidence-changed")
            for relative, claim in state["directories"].items():
                require(relative in {CAMPAIGN + "/evidence/" + name for name in CYCLES.values()}, "unexpected-cleanup-directory")
                path = self.root / relative
                require(custody(path) == claim["custody"] and tree_manifest(path) == claim["manifest"],
                        "native-evidence-custody-changed")
            for relative, claim in state["files"].items():
                require(relative in {CAMPAIGN + "/" + BINDING_NAME, CAMPAIGN + "/build-lane.lock"}, "unexpected-cleanup-file")
                path = self.root / relative
                require(custody(path) == claim["custody"] and sha(file_bytes(path)) == claim["sha256"],
                        "native-input-custody-changed")
            for relative in state["directories"]:
                shutil.rmtree(self.root / relative)
                receipt["removed"].append(relative)
            for relative in state["files"]:
                (self.root / relative).unlink()
                receipt["removed"].append(relative)
            require(command(["git", "status", "--porcelain=v1", "--untracked-files=all"], self.root).strip() == b"",
                    "final-checkout-is-not-clean")
            receipt.update(status="PASS", native_runs=state["runs"], cache_policy=state.get("cache_bootstrap"),
                stop_policy="Canonical native receipts attest immediate/final isolated Gradle stops; preflight builds nothing.",
                source_unchanged=True)
            shutil.rmtree(self.base)
            require(not self.base.exists(), "owned-artifact-staging-remains")
        except BaseException as error:
            receipt["status"] = "FAIL"
            receipt["errors"].append(dict(type=type(error).__name__, reason=str(error)[:500] if type(error) is RuntimeError else "cleanup-failed"))
        receipt["finished_at"] = now()
        write_new(self.cleanup_path, json_bytes(receipt))
        print(json.dumps(receipt, indent=2))
        return 0 if receipt["status"] == "PASS" else 1


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    require(len(arguments) == 1 and arguments[0] in {"validate-scope", "run", "cleanup"}, "invalid-continuation-command")
    if arguments[0] == "validate-scope":
        print(effective_scope(os.environ.get("GITHUB_EVENT_NAME"), os.environ.get("PARLOR_DISPATCH_SCOPE")))
        return 0
    lane = Continuation(os.environ)
    return lane.run() if arguments[0] == "run" else lane.cleanup()


if __name__ == "__main__":
    def interrupted(signum, _frame):
        raise KeyboardInterrupt("signal " + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
