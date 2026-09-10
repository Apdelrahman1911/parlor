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
import time
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
SHEET_RUNNERS = {"settings_sheet": "scripts/verification/ios-settings-sheet/settings_sheet_probe.py"}
SHEET_SELECTION = "settings-sheet-only"
SHEET_CAPTURED = "SETTINGS_SHEET_CAPTURED_NOT_APP_QUALIFICATION"
CYCLES = {"l08": "ios-readiness-33", "normal": "ios-readiness-34", "settings_sheet": "ios-readiness-35"}
NATIVE_SELECTIONS = {"paired": ("l08", "normal"), "l08-only": ("l08",), SHEET_SELECTION: ("settings_sheet",)}
LIFECYCLE_MODE = "direct-owned-v1"
NATIVE_JOB_SECONDS = 240 * 60
NATIVE_WAIT_SECONDS = 6000
NATIVE_GRACE_SECONDS = 600
NATIVE_FINISH_RESERVE_SECONDS = 600
JOB_CLOCK_ENV = "PARLOR_NATIVE_JOB_CLOCK"
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
    require(requested in {"full", "native-preflight", "native-evidence", "native-process-probe"}, "unknown-verification-scope")
    return requested


def effective_native_selection(scope, requested):
    selection = "paired" if requested in (None, "") else requested
    require(isinstance(selection, str) and selection in NATIVE_SELECTIONS, "unknown-native-selection")
    require(selection == "paired" or scope == "native-evidence" or
            selection == SHEET_SELECTION and scope == "native-preflight", "l08-only-requires-native-evidence")
    return selection


def context(env, root=ROOT, execute=None):
    execute = command if execute is None else execute
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
    git = lambda *args: execute(["git", *args], root).decode().strip()
    require(git("rev-parse", "--show-toplevel") == str(root) and git("rev-parse", "HEAD") == commit and
            git("branch", "--show-current") == BRANCH and git("rev-parse", "--is-shallow-repository") == "false",
            "frozen-branch-or-full-history-mismatch")
    require(git("status", "--porcelain=v1", "--untracked-files=no") == "", "tracked-checkout-not-clean")
    return dict(repository=REPOSITORY, branch=BRANCH, head_sha=commit, tree=git("rev-parse", "HEAD^{tree}"),
                workflow=WORKFLOW, root=str(root), run_id=int(env["GITHUB_RUN_ID"]),
                run_attempt=int(env["GITHUB_RUN_ATTEMPT"]))


def qualified_platform(root=ROOT, execute=None, environment=None):
    execute = command if execute is None else execute
    environment = os.environ if environment is None else environment
    require(platform.system() == "Darwin" and platform.machine() == "arm64", "qualified-arm64-macos-required")
    specification = importlib.util.spec_from_file_location("_native_ci_toolchain", root / SUPPORT / "toolchain_profiles.py")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    module.developer_environment(PROFILE, environment)
    value = module.validate_observation(PROFILE,
        execute(["/usr/bin/xcodebuild", "-version"]).decode(),
        execute(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"]).decode(),
        execute(["/usr/bin/xcode-select", "-p"]).decode(), platform.machine())
    require(execute(["/usr/bin/xcrun", "--sdk", "iphoneos", "--show-sdk-version"]).decode().strip() == value["sdk"],
            "qualified-device-sdk-mismatch")
    sdk_path = Path(execute(["/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"]).decode().strip())
    require(sdk_path.is_absolute() and sdk_path.is_dir() and
            Path(value["developer_dir"]).resolve() in sdk_path.resolve().parents, "sdk-path-outside-qualified-xcode")
    runtimes = decode(execute(["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"]))
    matches = [row for row in runtimes["runtimes"] if row.get("identifier") == value["runtime"]]
    require(len(matches) == 1 and matches[0].get("isAvailable") is True and matches[0].get("version") == value["sdk"],
            "qualified-runtime-unavailable")
    device = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
    types = decode(execute(["/usr/bin/xcrun", "simctl", "list", "devicetypes", "--json"]))
    require(len([row for row in types["devicetypes"] if row.get("identifier") == device]) == 1,
            "qualified-device-type-unavailable")
    android = Path.home() / "Library/Android/sdk"
    require(android.is_dir() and android.resolve() == android and
            all(not environment.get(key) or Path(environment[key]).resolve() == android
                for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT")), "unexpected-public-android-sdk")
    java = Path(execute(["/usr/libexec/java_home", "-v", "21"]).decode().strip())
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


def controls(binding, root=ROOT, runners=RUNNERS):
    result = {}
    for label, runner in runners.items():
        raw = command(["/usr/bin/python3", "-B", root / runner, "--control-manifest", binding], root)
        result[label] = (raw, validate_controls(decode(raw), label))
    return result


def unpack_preflight(raw, members=PREFLIGHT_FILES):
    require(0 < len(raw) <= MAX_ZIP, "unbounded-preflight-zip")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(len(entries) == len(members) and {row.filename for row in entries} == members,
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


def validate_package(files, expected, current, root=ROOT, runners=RUNNERS):
    require(set(files) == {"preflight.json", BINDING_NAME, *[label + "-controls.json" for label in runners]},
            "preflight-control-domain-mismatch")
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
    for label in runners:
        manifest = validate_controls(decode(files[label + "-controls.json"]), label)
        require(manifest["control_sha256"] == expected[label + "_sha256"] == preflight["control_sha256"][label],
                "independent-control-approval-mismatch")
        require({"path": CAMPAIGN + "/" + BINDING_NAME, "sha256": sha(files[BINDING_NAME])} in manifest["files"],
                "control-manifest-does-not-bind-source-file")
    return preflight


def canonical_sha(value):
    return sha(json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode())


def direct_lifecycle_is_safe(receipt, approved, source):
    """Closed cleanup schema, not a new runtime verdict or legacy reclassification."""
    try:
        value = receipt["simulator_lifecycle"]
        journal, children, rows = value["journal"], value["direct_children"], value["commands"]
        uuid = receipt["owned_uuid"]
        name = receipt["owned_device_name"]
        if not (receipt["simulator_lifecycle_mode"] == value["mode"] == LIFECYCLE_MODE and
                type(value["schema_version"]) is int and value["schema_version"] == 1 and
                value["cycle"] == receipt["cycle"] in CYCLES.values() and
                value["source_sha256"] == canonical_sha(source) and value["control_sha256"] == approved and
                value["cleanup_status"] == "PASS" and value["evidence_failed"] is False and
                value["creation_intent"] is True and value["creation_dispatched"] is True and
                value["creation_outcome"] in {"EXITED0_AND_IDENTITY_VERIFIED", "INTERRUPTED_OR_FAILED"} and
                re.fullmatch(r"[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}", uuid) and
                re.fullmatch(r"Parlor-Audit-parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}", name) and
                value["owned_uuid"] == uuid and value["owned_device_absent"] is receipt["owned_device_absent"] is True and
                journal["path"] == "simulator-lifecycle.jsonl" and HEX64.fullmatch(journal["sha256"]) and
                type(journal["bytes"]) is int and 0 < journal["bytes"] <= 1024 * 1024 and
                isinstance(journal["identity"], list) and len(journal["identity"]) == 3 and
                all(type(part) is int and part >= 0 for part in journal["identity"]) and journal["identity"][1] > 0 and
                type(value["cleanup_budget_seconds"]) is int and value["cleanup_budget_seconds"] == 480 and
                isinstance(rows, list) and 4 <= len(rows) <= 96 and
                type(receipt["build_attempted"]) is bool and value["build_attempted"] is receipt["build_attempted"]):
            return False
        started, operations = 0, []
        for index, row in enumerate(rows, 1):
            operation = row["operation"]
            if operation == "inventory":
                expected = ["list", "devices", "-j"]
            elif operation == "create":
                expected = ["create", name, "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
                            "com.apple.CoreSimulator.SimRuntime.iOS-26-2"]
            elif operation in {"boot", "bootstatus", "shutdown", "delete"}:
                expected = [operation, uuid] + (["-b"] if operation == "bootstatus" else [])
            else:
                return False
            if not (type(row["ordinal"]) is int and row["ordinal"] == index and
                    row["command"] == ["/usr/bin/xcrun", "simctl"] + expected and
                    type(row["timeout_seconds"]) is int and
                    row["timeout_seconds"] == (45 if operation == "inventory" else 300 if operation == "bootstatus" else 120) and
                    type(row["cleanup"]) is bool and type(row["handle_registered"]) is bool and
                    row["secondary_errors"] == [] and row["status"] in {"EXITED0", "FAILED"}):
                return False
            if not (all(isinstance(row[key], str) and 0 < len(row[key]) <= 64 for key in ("started_at", "finished_at")) and
                    type(row["elapsed_seconds"]) in (int, float) and 0 <= row["elapsed_seconds"] <= 6600 and
                    all(type(row[key]) is int and 0 <= row[key] <= 2 * 1024 * 1024 + 65536
                        for key in ("stdout_bytes", "stderr_bytes")) and
                    all(isinstance(row[key], str) and HEX64.fullmatch(row[key]) for key in ("stdout_sha256", "stderr_sha256"))):
                return False
            if row["handle_registered"]:
                started += 1
                if not (row["reaped"] is True and type(row["exit_code"]) is int and
                        type(row["owned_pid"]) is int and row["owned_pid"] > 0):
                    return False
            if row["status"] == "EXITED0":
                if not row["handle_registered"] or row["exit_code"] != 0 or row.get("primary_error"):
                    return False
            elif row["cleanup"] or not isinstance(row.get("primary_error"), dict):
                return False
            operations.append(operation)
        if not (started >= 4 and children == dict(started=started, reaped=started, unreaped=0, status="PASS") and
                operations.count("create") == operations.count("delete") == 1 and
                operations.count("shutdown") <= 1 and rows[-1]["operation"] == "inventory" and
                [row for row in receipt["commands"] if row.get("operation") is not None] == rows):
            return False
        barrier = value["postbuild_barrier"]
        if value["build_attempted"]:
            count = barrier["direct_build_handles"]
            return (barrier["status"] == "PASS" and barrier["build_attempted"] is True and
                    receipt["postbuild_evidence_preserved"] is True and barrier["evidence_preserved"] is True and
                    barrier["strict_stop"] is True and barrier["fresh_strict_refresh"] is True and
                    type(count) is int and 0 <= count <= 4096 and barrier["direct_build_handles_reaped"] == count)
        return barrier == dict(status="NOT_REQUIRED_PREBUILD", build_attempted=False)
    except (KeyError, TypeError, ValueError, AttributeError):
        return False


def lifecycle_journal_matches(raw, receipt, root, evidence):
    """Bind the actual retained journal, including intent, result and final event."""
    try:
        value = receipt["simulator_lifecycle"]
        if len(raw) != value["journal"]["bytes"] or sha(raw) != value["journal"]["sha256"]:
            return False
        lines = raw.splitlines()
        if not 5 <= len(lines) <= 512:
            return False
        events = [decode(line) for line in lines]
        if sum(item.get("event") == "allocation" for item in events) != 1:
            return False
        header = events[0]["intent"]
        temporary = header["temporary"]
        temporary_path = Path(receipt["owned_temporary_directory"])
        if not (events[0]["event"] == "allocation" and header["schema_version"] == 1 and
                header["mode"] == LIFECYCLE_MODE and header["cycle"] == receipt["cycle"] and
                header["control_sha256"] == value["control_sha256"] and header["source_sha256"] == value["source_sha256"] and
                header["name"] == receipt["owned_device_name"] and HEX64.fullmatch(header["nonce"]) and
                header["repository"] == str(root) and header["evidence"] == value["evidence_custody"] ==
                dict(path=str(evidence), **custody(evidence)) and
                temporary == value["temporary_custody"] and isinstance(temporary, dict) and
                set(temporary) == {"path", "device", "inode", "uid"} and
                all(type(temporary[key]) is int and temporary[key] >= 0 for key in ("device", "inode", "uid")) and
                temporary["inode"] > 0 and temporary["uid"] == os.getuid() and
                temporary["path"] == str(temporary_path) and temporary_path.is_absolute() and
                temporary_path.resolve() == temporary_path and
                header["name"] == "Parlor-Audit-" + temporary_path.name and
                temporary_path.name.startswith("parlor-audit-" + receipt["cycle"] + "-") and
                header["runtime"] == "com.apple.CoreSimulator.SimRuntime.iOS-26-2" and
                header["device_type"] == "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro" and
                header["developer_dir"] == "/Applications/Xcode_26.3.app/Contents/Developer"):
            return False
        plans = [item for item in events if item["event"] == "pre-create-intent"]
        finals = [item for item in events if item["event"] == "lifecycle-finalized"]
        deleted = [item for item in events if item["event"] == "delete-and-absence-verified"]
        if not (len(plans) == len(finals) == len(deleted) == 1 and finals[0] == events[-1] and
                finals[0]["owned_uuid"] == deleted[0]["uuid"] == value["owned_uuid"] and
                finals[0]["owned_device_absent"] is deleted[0]["metadata_absent"] is deleted[0]["directory_absent"] is True and
                finals[0]["postbuild_barrier"] == value["postbuild_barrier"] and
                finals[0]["direct_children_reaped"] == value["direct_children"]["reaped"] and
                finals[0]["commands_sha256"] == canonical_sha(value["commands"])):
            return False
        baseline = plans[0]["baseline_uuid_sha256"]
        if not (isinstance(baseline, list) and len(baseline) <= 2048 and
                all(isinstance(item, str) and HEX64.fullmatch(item) for item in baseline) and
                baseline == sorted(set(baseline)) and sha(value["owned_uuid"].encode()) not in baseline and
                plans[0]["name"] == header["name"] and plans[0]["runtime"] == header["runtime"] and
                plans[0]["device_type"] == header["device_type"]):
            return False
        intents = [item["row"] for item in events if item["event"] == "command-intent"]
        results = [item["row"] for item in events if item["event"] == "command-result"]
        launched = [item for item in events if item["event"] == "command-launched"]
        if len(intents) != len(value["commands"]) or results != value["commands"]:
            return False
        for row, intent in zip(value["commands"], intents):
            if any(row[key] != intent[key] for key in ("ordinal", "operation", "command", "timeout_seconds", "cleanup")):
                return False
        expected_launched = [dict(event="command-launched", ordinal=row["ordinal"], owned_pid=row["owned_pid"])
                             for row in value["commands"] if row["handle_registered"]]
        if launched != expected_launched:
            return False
        outcomes = [event for event in events if event["event"] == "create-outcome"]
        expected_outcomes = ([dict(event="create-outcome", status="EXITED0_AND_IDENTITY_VERIFIED", uuid=value["owned_uuid"])]
                             if value["creation_outcome"] == "EXITED0_AND_IDENTITY_VERIFIED" else [])
        if outcomes != expected_outcomes:
            return False
        # A reordered journal with a newly computed digest must not turn a
        # post-hoc intent or stale state observation into destructive authority.
        allowed = {"allocation", "pre-create-intent", "command-intent", "command-launched", "command-result",
                   "metadata", "create-outcome", "recovered-for-cleanup-only", "build-attempted",
                   "destructive-cleanup-barrier", "shutdown-verified", "delete-and-absence-verified", "lifecycle-finalized"}
        active, ordinal, plan_seen, barrier_seen, build_seen = None, 0, False, False, False
        latest_metadata = None
        for position, event in enumerate(events):
            kind = event["event"]
            if kind not in allowed:
                return False
            if kind == "pre-create-intent":
                if plan_seen or active is not None:
                    return False
                plan_seen = True
            elif kind == "build-attempted":
                if build_seen or barrier_seen:
                    return False
                build_seen = True
            elif kind == "destructive-cleanup-barrier":
                if barrier_seen or active is not None or event["result"] != value["postbuild_barrier"]:
                    return False
                if build_seen is not value["build_attempted"]:
                    return False
                barrier_seen = True
            elif kind == "metadata":
                if active is not None:
                    return False
                latest_metadata = event
            elif kind == "command-intent":
                row = event["row"]
                if active is not None or row["ordinal"] != ordinal + 1:
                    return False
                ordinal += 1
                active = row["ordinal"]
                if row["operation"] == "create" and not plan_seen:
                    return False
                if row["operation"] in {"shutdown", "delete"} and not barrier_seen:
                    return False
                if row["operation"] == "delete" and not (position >= 2 and latest_metadata and
                        events[position - 1] == latest_metadata and
                        events[position - 2]["event"] == "command-result" and
                        events[position - 2]["row"]["operation"] == "inventory" and
                        events[position - 2]["row"]["status"] == "EXITED0" and
                        latest_metadata["label"] == "owned-device-before-delete" and
                        latest_metadata["match"] == dict(udid=value["owned_uuid"], name=header["name"],
                            state="Shutdown", runtime=header["runtime"], device_type=header["device_type"], available=True)):
                    return False
            elif kind == "command-launched":
                if event["ordinal"] != active:
                    return False
            elif kind == "command-result":
                if event["row"]["ordinal"] != active:
                    return False
                active = None
            elif kind == "delete-and-absence-verified":
                if not (latest_metadata and latest_metadata["label"] == "owned-device-after-delete" and
                        latest_metadata["match"] is None):
                    return False
        return active is None and barrier_seen and build_seen is value["build_attempted"]
    except (KeyError, TypeError, ValueError, AttributeError, RuntimeError, OSError):
        return False


def cleanup_is_safe(receipt, approved, source, *, lifecycle_mode=None):
    if not isinstance(receipt, dict):
        return False
    stops = receipt.get("gradle_stops", [])
    commands = receipt.get("commands", [])
    stages = receipt.get("finalization_stages", [])
    if (not isinstance(commands, list) or any(not isinstance(row, dict) or
            not isinstance(row.get("command"), list) or not all(isinstance(arg, str) for arg in row["command"])
            for row in commands) or not isinstance(stages, list) or any(not isinstance(row, dict) for row in stages)):
        return False
    attempted = (receipt.get("build_attempted") is True or "xcodebuild_exit_code" in receipt or any(
        row.get("command") and Path(str(row["command"][0])).name == "xcodebuild" and
        any(arg in {"test", "build"} for arg in row["command"][1:])
        for row in receipt.get("commands", [])))
    if (not isinstance(stops, list) or any(not isinstance(row, dict) or row.get("exit_code") != 0 for row in stops) or
            attempted and (len(stops) != 2 or {row.get("label") for row in stops} != {"stop-xcode-immediate", "stop-final"})):
        return False
    if lifecycle_mode is not None and (lifecycle_mode != LIFECYCLE_MODE or
            not direct_lifecycle_is_safe(receipt, approved, source) or
            receipt.get("build_attempted") is not attempted):
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


def inner_execution_completed(receipt):
    """Admission is separate from cleanup: a safely retired timeout is still a timeout.

    AppHost emits type/message; the direct lifecycle emits type/code. Its timeout
    can be RuntimeError/command-timeout, not just subprocess.TimeoutExpired.
    Ordinary strict/assertion failure may advance B, but interruption may not.
    """
    if not isinstance(receipt, dict):
        return False

    def ordinary_error(value, staged=False):
        if not isinstance(value, dict):
            return False
        keys = set(value) - ({"stage"} if staged else set())
        if keys not in ({"type", "message"}, {"type", "code"}):
            return False
        if staged and (not isinstance(value.get("stage"), str) or not 1 <= len(value["stage"]) <= 256):
            return False
        kind = value.get("type")
        if (not isinstance(kind, str) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,127}", kind) or
                kind in {"TimeoutExpired", "TimeoutError", "KeyboardInterrupt", "SystemExit",
                         "CancelledError", "NativeInterrupted"}):
            return False
        if "code" in value:
            code = value["code"]
            return (isinstance(code, str) and re.fullmatch(r"[a-z][a-z-]{0,95}", code) is not None and
                    code not in {"command-timeout", "cancelled"})
        message = value["message"]
        return (isinstance(message, str) and len(message) <= 800 and
                message not in {"direct-simulator-command-timeout", "direct-simulator-cancelled"})

    def error_list(value):
        return (isinstance(value, list) and len(value) <= 256 and
                all(ordinary_error(row, staged=True) for row in value))

    if "error" in receipt and not ordinary_error(receipt["error"]):
        return False
    for key in ("interrupted", "timed_out"):
        if key in receipt and receipt[key] is not False:
            return False
    if "deferred_signals" in receipt and receipt["deferred_signals"] != []:
        return False
    if "postbuild_secondary_errors" in receipt and not error_list(receipt["postbuild_secondary_errors"]):
        return False
    if "raw_postbuild_preservation" in receipt:
        state = receipt["raw_postbuild_preservation"]
        if (not isinstance(state, dict) or not isinstance(state.get("status"), str) or
                state["status"] not in {"NOT_RUN", "RUNNING", "COMPLETE", "FAIL"} or
                "errors" in state and not error_list(state["errors"])):
            return False
    commands = receipt.get("commands")
    if not isinstance(commands, list) or len(commands) > 4096:
        return False
    for row in commands:
        if (not isinstance(row, dict) or not isinstance(row.get("command"), list) or not row["command"] or
                not all(isinstance(argument, str) for argument in row["command"])):
            return False
        if "exit_code" in row and (type(row["exit_code"]) is not int or row["exit_code"] < 0):
            return False  # A signaled child need not have raised in its command wrapper.
        for key in ("interrupted", "timed_out"):
            if key in row and row[key] is not False:
                return False
        if "interrupted_or_failed" in row:
            marker = row["interrupted_or_failed"]
            if (type(marker) is not bool or marker and "primary_error" not in row or
                    not marker and "primary_error" in row):
                return False
        for key in ("primary_error", "command_persistence_error"):
            if key in row and not ordinary_error(row[key]):
                return False
        if "command_cleanup_error" in row and not ordinary_error(row["command_cleanup_error"], staged=True):
            return False
        if "secondary_errors" in row and not error_list(row["secondary_errors"]):
            return False
    return True


def native_job_clock_ns():
    # Unlike time.monotonic() on older macOS Python, this is the same explicit
    # kernel clock in the workflow's first step and this separate process.
    value = time.clock_gettime_ns(time.CLOCK_MONOTONIC_RAW)
    require(type(value) is int and value > 0, "invalid-native-job-kernel-clock")
    return value


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
        self.selection = effective_native_selection(self.scope, env.get("PARLOR_NATIVE_SELECTION"))
        require(self.scope in {"native-preflight", "native-evidence"}, "scope-is-not-an-app-native-continuation")
        self.context = context(env, root)
        temporary = Path(env["RUNNER_TEMP"]).resolve(strict=True)
        require(root not in temporary.parents and temporary != root, "native-custody-must-be-outside-checkout")
        self.base = temporary / "parlor-native-{}-{}".format(self.context["run_id"], self.context["run_attempt"])
        self.bundle, self.binding = self.base / "bundle", root / CAMPAIGN / BINDING_NAME
        self.cleanup_path = self.base.with_name(self.base.name + "-cleanup.json")
        self.state = None

    @property
    def runners(self):
        return SHEET_RUNNERS if self.selection == SHEET_SELECTION else RUNNERS

    @property
    def selected_lanes(self):
        return NATIVE_SELECTIONS[self.selection]

    @property
    def unselected_lanes(self):
        return {label: "NOT_RUN_THIS_RUN" for label in RUNNERS if label not in self.selected_lanes}

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
        manifests = controls(self.binding, self.root, self.runners)
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
        approvals = (("settings_sheet_sha256", "PARLOR_APPROVED_PROBE_CONTROL_SHA256"),) if self.selection == SHEET_SELECTION else (
            ("l08_sha256", "PARLOR_APPROVED_L08_CONTROL_SHA256"), ("normal_sha256", "PARLOR_APPROVED_NORMAL_CONTROL_SHA256"))
        for key, variable in (("artifact_sha256", "PARLOR_PREFLIGHT_ARTIFACT_SHA256"), *approvals):
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
        files = unpack_preflight(raw, {"preflight.json", BINDING_NAME, *[label + "-controls.json" for label in self.runners]})
        preflight = validate_package(files, expected, self.context, self.root, self.runners)
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
        for label, (observed, _) in controls(self.binding, self.root, self.runners).items():
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
                    entry["exit_code"] = child.wait(timeout=900 if self.selection == SHEET_SELECTION else NATIVE_WAIT_SECONDS)
                except (subprocess.TimeoutExpired, NativeInterrupted) as error:
                    finishing = True
                    entry["timed_out"] = isinstance(error, subprocess.TimeoutExpired)
                    child.send_signal(signal.SIGTERM)
                    try:
                        entry["exit_code"] = child.wait(timeout=NATIVE_GRACE_SECONDS)
                    except subprocess.TimeoutExpired:
                        self.save()
                        raise RuntimeError("native-finalizer-did-not-finish-retain-all-custody")
        finally:
            for item, handler in prior.items():
                signal.signal(item, handler)
        entry.update(status="FINISHED", finished_at=now())

    def admit_native(self, label):
        """Do not start a child unless its full unchanged wait/grace can fit."""
        require(label in self.selected_lanes, "native-lane-not-selected")
        wait_seconds = 900 if self.selection == SHEET_SELECTION else NATIVE_WAIT_SECONDS
        job_seconds = 40 * 60 if self.selection == SHEET_SELECTION else NATIVE_JOB_SECONDS
        required = wait_seconds + NATIVE_GRACE_SECONDS + NATIVE_FINISH_RESERVE_SECONDS
        admission = dict(cycle=CYCLES[label], status="DENIED_NOT_RUN", job_budget_seconds=job_seconds,
                         child_wait_seconds=wait_seconds, finalizer_grace_seconds=NATIVE_GRACE_SECONDS,
                         evidence_cleanup_reserve_seconds=NATIVE_FINISH_RESERVE_SECONDS, required_seconds=required)
        self.state.setdefault("native_admissions", {})[label] = admission
        try:
            require(self.scope == "native-evidence", "job-budget-is-native-evidence-only")
            raw = self.env.get(JOB_CLOCK_ENV)
            require(isinstance(raw, str) and 0 < len(raw) <= 1024, "missing-or-unbounded-native-job-clock")
            clock = decode(raw.encode())
            require(isinstance(clock, dict) and set(clock) == {
                "clock", "start_ns", "run_id", "run_attempt", "job", "head_sha"}, "malformed-native-job-clock")
            require(clock["clock"] == "CLOCK_MONOTONIC_RAW" and type(clock["start_ns"]) is int and
                    0 < clock["start_ns"] < 10 ** 20 and type(clock["run_id"]) is int and
                    type(clock["run_attempt"]) is int and clock["run_id"] == self.context["run_id"] and
                    clock["run_attempt"] == self.context["run_attempt"] and clock["job"] == "ios" and
                    clock["head_sha"] == self.context["head_sha"], "unbound-native-job-clock")
            current = native_job_clock_ns()
            require(current >= clock["start_ns"], "native-job-clock-is-in-the-future")
            remaining = job_seconds * 10 ** 9 - (current - clock["start_ns"])
            admission.update(clock=clock, observed_ns=current, remaining_seconds=remaining // 10 ** 9)
            require(remaining >= required * 10 ** 9, "insufficient-complete-native-lane-budget")
            admission["status"] = "ADMITTED"
        except BaseException as error:
            admission["error_type"] = type(error).__name__
            admission["reason"] = str(error)[:200] if type(error) is RuntimeError else "native-job-clock-unavailable-or-invalid"
            self.save()
            raise
        self.save()

    def run_native(self, label, approved, source):
        require(label in self.selected_lanes, "native-lane-not-selected")
        cycle = CYCLES[label]
        destination = self.root / CAMPAIGN / "evidence" / cycle
        require(not destination.exists() and not destination.is_symlink(), "never-reuse-a-native-cycle")
        # A denied B has no child/canonical receipt and must not overwrite A's
        # honestly established cleanup safety or prevent uploaded-custody cleanup.
        self.admit_native(label)
        arguments = ["/usr/bin/python3", "-B", str(self.root / self.runners[label]), cycle, str(self.binding), approved,
                     "--simulator-signing=adhoc", "--toolchain=" + PROFILE,
                     "--simulator-lifecycle=" + LIFECYCLE_MODE]
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
        safe = cleanup_is_safe(receipt, approved, source, lifecycle_mode=LIFECYCLE_MODE)
        if safe:
            journal = destination / "simulator-lifecycle.jsonl"
            raw = file_bytes(journal, 1024 * 1024)
            info = journal.lstat()
            safe = (lifecycle_journal_matches(raw, receipt, self.root, destination) and
                    receipt["simulator_lifecycle"]["journal"]["identity"] == [info.st_dev, info.st_ino, info.st_uid])
        entry.update(receipt_status=receipt.get("status"), cleanup_safe=safe,
                     receipt_sha256=sha(file_bytes(destination / "receipt.json", 8 * 1024 * 1024)))
        self.state["cleanup_safe"] = safe
        self.save()
        require(not entry.get("interrupted") and not entry.get("timed_out") and
                type(entry.get("exit_code")) is int and entry["exit_code"] >= 0, "native-run-interrupted-or-timed-out")
        require(inner_execution_completed(receipt), "native-inner-run-interrupted-timed-out-or-malformed")
        require(safe, "native-cleanup-or-source-identity-unsafe-do-not-start-another-cycle")
        require(receipt.get("cycle") == cycle and receipt.get("signing_mode") == "adhoc" and
                receipt.get("toolchain_profile") == PROFILE and
                receipt.get("simulator_lifecycle_mode") == LIFECYCLE_MODE and
                (label != "normal" or receipt.get("image_observer") == "libproc"), "native-cycle-mode-profile-mismatch")
        if label == "settings_sheet":
            require(receipt.get("execution_kind") == "public-settings-sheet-diagnostic-only" and all(receipt.get(key) == "NOT_RUN"
                for key in ("runtime_evidence_status", "provenance_status", "notice_package_status")), "diagnostic-is-not-app-qualification")
        return entry["exit_code"], receipt.get("status")

    def evidence(self):
        expected, source = self.fetch_preflight()
        self.bootstrap_cache_directories()
        # Paired A partial/strict failure is retained; it does not prevent the independent
        # normal-source B observation if (and only if) A's actual cleanup is safe.
        results = [self.run_native(label, expected[label + "_sha256"], source) for label in self.selected_lanes]
        if self.selection == SHEET_SELECTION:
            captured = results == [(0, SHEET_CAPTURED)]
            self.state["status"] = SHEET_CAPTURED if captured else "SETTINGS_SHEET_NOT_CAPTURED"
            return 0 if captured else 2  # Diagnostic capture is never A/B or combined qualification.
        passed = all(code == 0 and status == "PASS" for code, status in results)
        if self.selection == "paired":
            self.state["status"] = "PASS" if passed else "NOT_READY"
        else:
            # No B execution, receipt or qualification is synthesized here.
            self.state["status"] = "L08_ONLY_PASS" if passed else "L08_ONLY_NOT_READY"
        return 0 if passed else 2

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
        self.state = dict(schema_version=1, context=self.context, scope=self.scope, selection=self.selection,
            unselected_lanes=self.unselected_lanes, base_custody=custody(self.base),
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
            print(json.dumps({key: self.state.get(key) for key in ("scope", "selection", "unselected_lanes", "status", "cleanup_safe", "error")}, indent=2))
        return code

    def cleanup(self):
        receipt = dict(schema_version=1, context=self.context, scope=self.scope, selection=self.selection,
                       unselected_lanes=self.unselected_lanes, started_at=now(), status="FAIL", removed=[], errors=[])
        try:
            state = decode(file_bytes(self.base / "state.json", 8 * 1024 * 1024))
            require(state.get("context") == self.context and state.get("base_custody") == custody(self.base),
                    "cleanup-custody-or-source-mismatch")
            require(state.get("scope") == self.scope and state.get("selection") == self.selection and
                    state.get("unselected_lanes") == self.unselected_lanes, "cleanup-scope-or-selection-mismatch")
            for key in ("runs", "native_admissions", "preservation"):
                claims = state.get(key, {})
                require(isinstance(claims, dict) and set(claims) <= set(self.selected_lanes),
                        "cleanup-unselected-native-lane")
            require(self.env.get("PARLOR_NATIVE_UPLOAD_OUTCOME") == "success" and
                    POSITIVE.fullmatch(self.env.get("PARLOR_NATIVE_ARTIFACT_ID", "")) and
                    HEX64.fullmatch(self.env.get("PARLOR_NATIVE_ARTIFACT_DIGEST", "")),
                    "upload-custody-unconfirmed-retain-last-local-evidence")
            receipt["upload"] = {"artifact_id": self.env["PARLOR_NATIVE_ARTIFACT_ID"],
                                 "sha256": self.env["PARLOR_NATIVE_ARTIFACT_DIGEST"]}
            require(state.get("cleanup_safe") is True, "native-cleanup-unsafe-retain-owned-evidence")
            require(tree_manifest(self.bundle) == state.get("bundle_manifest"), "uploaded-evidence-changed")
            for relative, claim in state["directories"].items():
                require(relative in {CAMPAIGN + "/evidence/" + CYCLES[label] for label in self.selected_lanes},
                        "unexpected-cleanup-directory")
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
        scope = effective_scope(os.environ.get("GITHUB_EVENT_NAME"), os.environ.get("PARLOR_DISPATCH_SCOPE"))
        effective_native_selection(scope, os.environ.get("PARLOR_NATIVE_SELECTION"))
        print(scope)
        return 0
    lane = Continuation(os.environ)
    return lane.run() if arguments[0] == "run" else lane.cleanup()


if __name__ == "__main__":
    def interrupted(signum, _frame):
        raise KeyboardInterrupt("signal " + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
