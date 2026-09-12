#!/usr/bin/env python3
"""Bounded cleanup for the fresh, task-owned GitHub verification checkout.

This is not a local-worktree cleaner and never kills arbitrary worker processes.
Initialize before output exists; only successful evidence upload authorizes cleanup.
"""
from __future__ import annotations

import errno
import json
import hashlib
import os
import re
import shutil
import stat
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath


APPLE_JOB_CYCLES = {
    "ios": ("apple-aggregate", "apple-ui"),
    "ios-release": ("apple-aggregate", "apple-wrapper"),
}
APPLE_CYCLES = tuple(dict.fromkeys(cycle for cycles in APPLE_JOB_CYCLES.values() for cycle in cycles))


def apple_cycles(task: dict) -> tuple[str, ...]:
    """Closed full-verification jobs only; no prefix admission or Darwin import."""
    job = task.get("GITHUB_JOB")
    if not isinstance(job, str) or job not in APPLE_JOB_CYCLES:
        raise RuntimeError("Unknown Apple verification job")
    return APPLE_JOB_CYCLES[job]


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat()


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True)


def output_roots(tracked: list[str]) -> list[str]:
    roots = {"build", "iosApp/build"}
    for name in tracked:
        path = PurePosixPath(name)
        if path.name in {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}:
            roots.add(str(path.parent / "build"))
        if path.suffix == ".py":
            roots.add(str(path.parent / "__pycache__"))
    for output in roots:
        if any(name == output or name.startswith(output + "/") for name in tracked):
            raise RuntimeError(f"Refusing a generated-output root containing tracked files: {output}")
    return sorted(roots)


def checked_path(root: Path, relative: str) -> Path:
    path = PurePosixPath(relative)
    if path.is_absolute() or not path.parts or any(part in {".", ".."} for part in path.parts):
        raise RuntimeError("Invalid output claim")
    target = root.joinpath(*path.parts)
    current = root
    for part in path.parts:
        current = current / part
        if current.is_symlink():
            raise RuntimeError(f"Refusing symlink output path: {relative}")
    if target.resolve() != target.absolute():
        raise RuntimeError(f"Refusing redirected output path: {relative}")
    return target


def source_identity(root: Path) -> dict:
    if Path(git(root, "rev-parse", "--show-toplevel").strip()).resolve() != root:
        raise RuntimeError("Not the checkout root")
    tracked = git(root, "ls-files", "-z").split("\0")
    tracked = [name for name in tracked if name]
    return {
        "root": str(root),
        "head": git(root, "rev-parse", "HEAD").strip(),
        "tree": git(root, "rev-parse", "HEAD^{tree}").strip(),
        "outputs": output_roots(tracked),
    }


def write_new(path: Path, value: dict) -> None:
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())


def prepare(root: Path, claim_path: Path, task: dict) -> dict:
    job = task.get("GITHUB_JOB", "")
    if isinstance(job, str) and job.startswith("ios"):
        apple_cycles(task)  # Unknown Apple-like jobs cannot acquire generic cleanup authority.
    claim = source_identity(root)
    if git(root, "status", "--porcelain").strip():
        raise RuntimeError("Verification must begin from a clean checkout")
    for relative in claim["outputs"]:
        path = checked_path(root, relative)
        if path.exists():
            raise RuntimeError(f"Pre-existing output is not task-owned: {relative}")
    if task.get("GITHUB_JOB") == "desktop-android":
        claim["android_sdk"] = android_sdk_binding()[1]
    claim.update(task=task, prepared_at=timestamp(), retention_reason=(
        "Same-job follow-up builds/linkage and artifact inspection consume these outputs; "
        "the final upload must succeed with an artifact ID and SHA-256 before exact cleanup. "
        "Upload failure retains outputs and reports cleanup failure rather than discarding evidence."
    ))
    write_new(claim_path, claim)
    return claim


def gradle_stop_command(windows: bool = os.name == "nt") -> list[str]:
    if windows:
        return [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", "gradlew.bat", "--stop"]
    return ["./gradlew", "--stop"]


def stop_gradle(root: Path) -> dict:
    command = gradle_stop_command()
    result = {"command": command, "started_at": timestamp()}
    try:
        process = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, timeout=90, check=False)
        result.update(exit_code=process.returncode, output=process.stdout[-16000:])
    except (OSError, subprocess.TimeoutExpired) as error:
        result.update(exit_code=1, error=type(error).__name__)
    result["finished_at"] = timestamp()
    return result


def uploaded_evidence_is_retained(upload: dict) -> bool:
    artifact_id = upload.get("artifact_id")
    digest = upload.get("artifact_digest")
    return (upload.get("outcome") == "success" and isinstance(artifact_id, str) and
            re.fullmatch(r"[1-9][0-9]*", artifact_id) is not None and isinstance(digest, str) and
            re.fullmatch(r"[0-9a-fA-F]{64}", digest) is not None)


class AndroidAuditError(RuntimeError):
    """A fixed diagnostic code, never a process argument or filesystem path."""


ANDROID_PROC_MAX_ENTRIES = 4096
ANDROID_PROC_MAX_BYTES = 8192
ANDROID_PROC_SECONDS = 5.0
ANDROID_EXE_READER_UNPRIVILEGED = "unprivileged"
ANDROID_EXE_READER_SUDO = "sudo-proc-exe-v2"
ANDROID_EXE_READERS = (ANDROID_EXE_READER_UNPRIVILEGED, ANDROID_EXE_READER_SUDO)
ANDROID_EXE_READER_MAX_BYTES = 65536
ANDROID_EXE_READER_SECONDS = 2.0  # Includes sudo/Python startup; helper itself has a 1s timer.


def android_sdk_binding() -> tuple[Path, dict]:
    if sys.platform != "linux":
        raise AndroidAuditError("LINUX_REQUIRED")
    uid = os.getuid()
    if uid <= 0 or uid != os.geteuid():
        raise AndroidAuditError("NONROOT_MATCHING_UID_REQUIRED")
    home = os.environ.get("ANDROID_HOME", "")
    if not home or len(os.fsencode(home)) > 4096 or not Path(home).is_absolute():
        raise AndroidAuditError("ABSOLUTE_ANDROID_HOME_REQUIRED")
    sdk = Path(home).resolve(strict=True)
    alternate = os.environ.get("ANDROID_SDK_ROOT", "")
    if (sdk == Path("/") or not sdk.is_dir() or alternate and
            (not Path(alternate).is_absolute() or Path(alternate).resolve(strict=True) != sdk)):
        raise AndroidAuditError("SDK_ROOT_AMBIGUOUS")
    identity = sdk.stat()
    return sdk, {"sdk_path_sha256": hashlib.sha256(os.fsencode(sdk)).hexdigest(),
                 "sdk_device": identity.st_dev, "sdk_inode": identity.st_ino, "uid": uid}


def android_proc_bytes(path: Path) -> bytes:
    with path.open("rb") as stream:
        value = stream.read(ANDROID_PROC_MAX_BYTES + 1)
    if len(value) > ANDROID_PROC_MAX_BYTES:
        raise AndroidAuditError("PROC_RECORD_LIMIT")
    return value


def android_proc_identity(process: Path, pid: int, diagnostic: dict | None = None,
                          *, after: bool = False) -> tuple[int, tuple[int, ...]]:
    if diagnostic is not None:
        diagnostic["operation"] = "after_stat" if after else "before_stat"
    prefix, separator, tail = android_proc_bytes(process / "stat").rpartition(b") ")
    fields = tail.split()
    if (not separator or not prefix.startswith(str(pid).encode("ascii") + b" (") or
            len(fields) < 20 or re.fullmatch(rb"[RSDZTWtXxKPI]", fields[0]) is None or
            not fields[19].isdigit() or not 0 <= int(fields[19]) < 2**64):
        raise AndroidAuditError("PROC_STAT_INVALID")
    if diagnostic is not None:
        diagnostic["operation"] = "after_status" if after else "before_status"
    lines = [line for line in android_proc_bytes(process / "status").splitlines() if line.startswith(b"Uid:")]
    match = re.fullmatch(rb"Uid:\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s*", lines[0]) if len(lines) == 1 else None
    if not match or any(int(value) >= 2**32 for value in match.groups()):
        raise AndroidAuditError("PROC_UID_INVALID")
    return int(fields[19]), tuple(int(value) for value in match.groups())


def android_privileged_executable(uid: int, pid: int, starttime: int, uids: tuple[int, ...], deadline: float) -> tuple:
    """One fixed helper, private bounded protocol; no caller-supplied command/path."""
    if (sys.platform != "linux" or type(uid) is not int or not 0 < uid < 2**32 or
            uid != os.getuid() or uid != os.geteuid() or type(pid) is not int or not 0 < pid < 2**31 or
            type(starttime) is not int or not 0 <= starttime < 2**64 or
            type(uids) is not tuple or len(uids) != 4 or
            any(type(value) is not int or not 0 <= value < 2**32 for value in uids) or uid not in uids):
        raise AndroidAuditError("PROC_EXE_READER_CALLER_INVALID")
    helper = Path(__file__).absolute().with_name("linux_exe_reader.py")
    if helper.resolve(strict=True) != helper or not helper.is_file():
        raise AndroidAuditError("PROC_EXE_READER_SOURCE_INVALID")
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise AndroidAuditError("PROC_SCAN_DEADLINE")
    try:
        response = subprocess.run(
            ["/usr/bin/sudo", "-n", "--", "/usr/bin/python3", "-I", "-S", "-B", str(helper),
             str(uid), str(pid), str(starttime), *(str(value) for value in uids)],
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            cwd="/", env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"},
            timeout=min(ANDROID_EXE_READER_SECONDS, remaining), check=False,
        )
    except subprocess.TimeoutExpired:
        raise AndroidAuditError("PROC_EXE_READER_TIMEOUT") from None
    except OSError:
        raise AndroidAuditError("PROC_EXE_READER_UNAVAILABLE") from None
    if time.monotonic() > deadline:
        raise AndroidAuditError("PROC_SCAN_DEADLINE")
    if response.returncode != 0:
        raise AndroidAuditError("PROC_EXE_READER_FAILED")
    # The fixed helper bounds bytes before writing; never log stdout/stderr/argv.
    if not isinstance(response.stdout, bytes) or len(response.stdout) > ANDROID_EXE_READER_MAX_BYTES:
        raise AndroidAuditError("PROC_EXE_READER_RESPONSE_INVALID")

    def unique(pairs):
        value = dict(pairs)
        if len(value) != len(pairs):
            raise ValueError("duplicate field")
        return value

    try:
        value = json.loads(response.stdout, object_pairs_hook=unique)
        if (not isinstance(value, dict) or set(value) != {"schema", "uid", "pid", "starttime", "uids", "observations"} or
                any(type(value[name]) is not int or value[name] != wanted
                    for name, wanted in (("schema", 2), ("uid", uid), ("pid", pid), ("starttime", starttime))) or
                not isinstance(value["uids"], list) or len(value["uids"]) != 4 or
                any(type(item) is not int or not 0 <= item < 2**32 for item in value["uids"]) or
                tuple(value["uids"]) != uids or
                not isinstance(value["observations"], list) or len(value["observations"]) != 2):
            raise ValueError("invalid identity")
        for row in value["observations"]:
            if (not isinstance(row, dict) or set(row) != {"path", "device", "inode", "mode"} or
                    not isinstance(row["path"], str) or "\0" in row["path"] or
                    not Path(row["path"]).is_absolute() or len(os.fsencode(row["path"])) > 4096 or
                    row["path"].endswith(" (deleted)") or
                    any(type(row[name]) is not int or not 0 <= row[name] < limit
                        for name, limit in (("device", 2**64), ("inode", 2**64), ("mode", 2**32))) or
                    not stat.S_ISREG(row["mode"])):
                raise ValueError("invalid executable")
        first, last = value["observations"]
        if first != last:
            raise ValueError("changed executable")
    except (ValueError, TypeError, KeyError, RecursionError):
        raise AndroidAuditError("PROC_EXE_READER_RESPONSE_INVALID") from None
    return (first["path"], (first["device"], first["inode"], first["mode"]),
            last["path"], (last["device"], last["inode"], last["mode"]))


def audit_android_emulator_absence(expected: dict | None, proc: Path = Path("/proc"),
                                   *, reader: str = ANDROID_EXE_READER_UNPRIVILEGED) -> dict:
    """Read-only, non-atomic sample; does not establish process ownership or exit."""
    receipt = {"result": "FAIL", "started_at": timestamp(), "enumerated": 0, "sampled": 0,
               "selected_uid": 0, "matching_executables": 0, "errors": [],
               "mixed_uid_samples": 0, "mixed_uid_privileged_samples": 0,
               "exe_reader": {"mode": reader if type(reader) is str and reader in ANDROID_EXE_READERS else "invalid",
                              "eacces_denials": 0, "privileged_reads": 0},
               "scope": "Enumerated visible selected-UID executable paths beneath bound SDK/emulator only; "
                        "not termination, task-process ownership, AVD retirement, other SDKs/UIDs, or continuous absence"}
    deadline = time.monotonic() + ANDROID_PROC_SECONDS
    # Failure-only metadata; selection is unknown until the full first identity.
    # No PID, process name, executable path, argv, or exception message is retained.
    diagnostic = {"operation": None, "selection": "unknown"}

    def budget() -> None:
        if time.monotonic() > deadline:
            raise AndroidAuditError("PROC_SCAN_DEADLINE")

    try:
        if type(reader) is not str or reader not in ANDROID_EXE_READERS:
            raise AndroidAuditError("ANDROID_EXE_READER_INVALID")
        sdk, binding = android_sdk_binding()
        if binding != expected:
            raise AndroidAuditError("SDK_UID_BINDING_CHANGED")
        receipt["binding"] = binding
        emulator = sdk / "emulator"
        if emulator.resolve(strict=True) != emulator or not emulator.is_dir():
            raise AndroidAuditError("EMULATOR_DIRECTORY_REDIRECTED")
        directory = emulator.stat()
        pids, saw_self = [], False
        with os.scandir(proc) as entries:
            for index, entry in enumerate(entries):
                budget()
                if index >= ANDROID_PROC_MAX_ENTRIES:
                    raise AndroidAuditError("PROC_ENTRY_LIMIT")
                if re.fullmatch(r"[1-9][0-9]*", entry.name):
                    if len(entry.name) > 10 or int(entry.name) >= 2**31:
                        raise AndroidAuditError("PROC_PID_INVALID")
                    pids.append(int(entry.name))
        receipt["enumerated"] = len(pids)
        for pid in sorted(pids):
            budget()
            process = proc / str(pid)
            diagnostic.update(operation=None, selection="unknown")
            before = android_proc_identity(process, pid, diagnostic)
            selected = binding["uid"] in before[1]
            diagnostic["selection"] = selected
            mixed = selected and before[1] != (binding["uid"],) * 4
            privileged = False
            matches = False
            if selected:
                # Explicit v2 samples the exact ordered credential tuple, not ownership.
                # The default still refuses mixed credentials; neither mode selects UID 0.
                if mixed and reader != ANDROID_EXE_READER_SUDO:
                    raise AndroidAuditError("PROC_UID_AMBIGUOUS")
                diagnostic["operation"] = "before_exe_readlink"
                try:
                    executable = os.readlink(process / "exe")
                except PermissionError as error:
                    if error.errno == errno.EACCES:
                        receipt["exe_reader"]["eacces_denials"] += 1
                    if reader != ANDROID_EXE_READER_SUDO or error.errno != errno.EACCES:
                        raise
                    diagnostic["operation"] = None  # Helper failure is not a new unprivileged read failure.
                    executable, first, after, last = android_privileged_executable(binding["uid"], pid, before[0], before[1], deadline)
                    privileged = True
                    receipt["exe_reader"]["privileged_reads"] += 1
                else:
                    diagnostic["operation"] = "before_exe_stat"
                    first_stat = (process / "exe").stat()
                    first = (first_stat.st_dev, first_stat.st_ino, first_stat.st_mode)
                    diagnostic["operation"] = "after_exe_readlink"
                    after = os.readlink(process / "exe")
                    diagnostic["operation"] = "after_exe_stat"
                    last_stat = (process / "exe").stat()
                    last = (last_stat.st_dev, last_stat.st_ino, last_stat.st_mode)
                if (executable != after or first[:2] != last[:2] or
                        not stat.S_ISREG(first[2]) or not Path(executable).is_absolute() or
                        len(os.fsencode(executable)) > 4096 or executable.endswith(" (deleted)")):
                    raise AndroidAuditError("PROC_EXECUTABLE_AMBIGUOUS")
                matches = emulator in Path(executable).parents
            if before != android_proc_identity(process, pid, diagnostic, after=True):
                raise AndroidAuditError("PROC_LIFETIME_OR_UID_CHANGED")
            diagnostic["operation"] = None  # Later SDK/budget failures are not process reads.
            receipt["sampled"] += 1
            receipt["selected_uid"] += int(selected)
            receipt["mixed_uid_samples"] += int(mixed)
            receipt["mixed_uid_privileged_samples"] += int(mixed and privileged)
            saw_self = saw_self or (pid == os.getpid() and selected)
            if matches:
                receipt["matching_executables"] += 1
                raise AndroidAuditError("SDK_EMULATOR_EXECUTABLE_OBSERVED")
        final_directory = emulator.stat()
        if (android_sdk_binding()[1] != binding or emulator.resolve(strict=True) != emulator or
                (directory.st_dev, directory.st_ino) != (final_directory.st_dev, final_directory.st_ino)):
            raise AndroidAuditError("SDK_UID_BINDING_CHANGED")
        budget()
        if not saw_self:
            raise AndroidAuditError("OBSERVER_PID_NOT_SAMPLED")
        receipt["result"] = "PASS"
    except (OSError, RuntimeError, ValueError, TypeError) as error:
        receipt["errors"].append({"code": str(error) if isinstance(error, AndroidAuditError) else type(error).__name__})
        if isinstance(error, OSError) and diagnostic["operation"] is not None:
            number = error.errno
            receipt["failure_context"] = {**diagnostic,
                "errno": number if type(number) is int and 1 <= number <= 4095 else None}
    receipt["finished_at"] = timestamp()
    return receipt


def verify_apple_cleanup(root: Path, claim_path: Path, task: dict, outcomes: dict | None) -> dict:
    """Current step outcomes AND current, source-bound receipts gate Apple deletion."""
    cycles = apple_cycles(task)
    if not isinstance(outcomes, dict) or set(outcomes) != set(cycles):
        raise RuntimeError("Missing complete Apple cycle outcomes; retain outputs")
    suffix = "-ownership.json"
    if not str(claim_path).endswith(suffix):
        raise RuntimeError("Invalid Apple ownership receipt prefix")
    prefix = str(claim_path)[:-len(suffix)]
    # A cycle assigned to the other Apple job must not leave unaccounted resources.
    # Exact known paths only; never scan, adopt, or delete another job's receipts.
    for cycle in APPLE_CYCLES:
        if cycle not in cycles:
            unexpected = [Path(prefix + "-stop-" + cycle + ".json"),
                          *(Path(prefix + "-" + cycle + ending) for ending in (
                              "-ownership.json", "-simulator-plan.json", "-simulator-create.json", "-simulator-adopted.json"))]
            if any(path.exists() or path.is_symlink() for path in unexpected):
                raise RuntimeError("Unassigned Apple cycle has resource or cleanup receipts; retain outputs")
    current = source_identity(root)
    expected_source = {key: current[key] for key in ("root", "head", "tree")}
    retained = {}
    for cycle in cycles:
        outcome = outcomes[cycle]
        path = Path(prefix + "-stop-" + cycle + ".json")
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024 * 1024:
            raise RuntimeError("No bounded current Apple cleanup receipt")
        value = json.loads(path.read_text(encoding="utf-8"))
        if (not isinstance(value, dict) or not isinstance(outcome, dict) or
                outcome.get("finish") != "success" or value.get("schema") != 1 or
                value.get("cycle") != cycle or value.get("task") != task or
                value.get("source") != expected_source or value.get("errors") != [] or
                value.get("gradle_stop", {}).get("exit_code") != 0 or
                value.get("prepare_outcome") != outcome.get("prepare") or
                value.get("run_outcome") != outcome.get("run")):
            raise RuntimeError("Apple cleanup outcome/receipt/source mismatch")
        owned_claim = Path(prefix + "-" + cycle + "-ownership.json")
        if value.get("result") == "NOT_RUN":
            if (outcome.get("prepare") != "skipped" or outcome.get("run") != "skipped" or
                    owned_claim.exists() or owned_claim.is_symlink()):
                raise RuntimeError("Apple NOT_RUN receipt has a conflicting resource claim")
        elif value.get("result") == "PASS":
            if (outcome.get("prepare") != "success" or
                    value.get("workers_before_simulator", {}).get("result") != "PASS" or
                    value.get("workers", {}).get("result") != "PASS" or
                    value.get("simulator", {}).get("result") not in {"PASS", "NOT_CREATED", "NOT_APPLICABLE"} or
                    cycle != "apple-ui" and value.get("simulator", {}).get("result") != "NOT_APPLICABLE" or
                    cycle == "apple-ui" and outcome.get("run") == "success" and
                    value.get("simulator", {}).get("result") != "PASS" or
                    owned_claim.is_symlink() or not owned_claim.is_file() or
                    owned_claim.stat().st_size > 1024 * 1024 or
                    value.get("claim_sha256") != hashlib.sha256(owned_claim.read_bytes()).hexdigest()):
                raise RuntimeError("Apple native/device cleanup is incomplete; retain outputs")
        else:
            raise RuntimeError("Apple cleanup did not pass; retain outputs")
        retained[cycle] = {"result": value["result"], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    return retained


def cleanup(root: Path, claim_path: Path, task: dict, upload: dict, preparation_outcome: str,
            apple_outcomes: dict | None = None, *, android_reader: str = ANDROID_EXE_READER_UNPRIVILEGED) -> dict:
    receipt = {"task": task, "started_at": timestamp(), "removed": [], "not_created": [],
               "retained": [], "upload": upload, "preparation_outcome": preparation_outcome, "errors": []}
    receipt["gradle_stop"] = stop_gradle(root)
    try:
        if receipt["gradle_stop"]["exit_code"] != 0:
            raise RuntimeError("Gradle stop failed; do not race active workers by deleting outputs")
        if (type(android_reader) is not str or android_reader not in ANDROID_EXE_READERS or
                android_reader != ANDROID_EXE_READER_UNPRIVILEGED and task.get("GITHUB_JOB") != "desktop-android"):
            raise RuntimeError("Invalid Android executable reader mode; retain outputs")
        job = task.get("GITHUB_JOB", "")
        if isinstance(job, str) and job.startswith("ios"):
            apple_cycles(task)  # Includes explicit rejection of the separate ios-protection-probe lane.
        if preparation_outcome != "success":
            raise RuntimeError("Fresh ownership preparation did not succeed; preserve all pre-existing outputs")
        if claim_path.is_symlink() or not claim_path.is_file():
            raise RuntimeError("No trustworthy pre-build ownership claim")
        claim = json.loads(claim_path.read_text(encoding="utf-8"))
        if not isinstance(claim, dict):
            raise RuntimeError("Invalid ownership claim")
        current = source_identity(root)
        if claim.get("task") != task or any(claim.get(key) != value for key, value in current.items()):
            raise RuntimeError("Task/source/output ownership changed; refuse cleanup")
        if git(root, "status", "--porcelain").strip():
            raise RuntimeError("Working-tree source changed; refuse cleanup")
        # Preflight every path before deleting any output, so a redirected sibling fails closed.
        paths = [(relative, checked_path(root, relative)) for relative in current["outputs"]]
        for relative, path in paths:
            if not path.exists():
                receipt["not_created"].append(relative)
                continue
            if not path.is_dir():
                raise RuntimeError(f"Claimed output is not a directory: {relative}")
            receipt["retained"].append(relative)
        if task.get("GITHUB_JOB") in APPLE_JOB_CYCLES:
            receipt["apple_cleanup"] = verify_apple_cleanup(root, claim_path, task, apple_outcomes)
        if not uploaded_evidence_is_retained(upload):
            raise RuntimeError("No successful nonempty verification upload; preserve required evidence outputs")
        if task.get("GITHUB_JOB") == "desktop-android":
            receipt["android_emulator_absence"] = audit_android_emulator_absence(claim.get("android_sdk"), reader=android_reader)
            receipt["android_emulator_absence"]["source"] = {key: current[key] for key in ("head", "tree")}
            if receipt["android_emulator_absence"]["result"] != "PASS":
                raise RuntimeError("Android sampled emulator absence did not pass; retain outputs")
        for relative, path in paths:
            if not path.exists():
                continue
            try:
                shutil.rmtree(path)
                if path.exists() or path.is_symlink():
                    raise RuntimeError("Output remains after cleanup")
                receipt["removed"].append(relative)
                receipt["retained"].remove(relative)
            except OSError as error:
                receipt["errors"].append({"path": relative, "error": type(error).__name__})
    except (OSError, ValueError, RuntimeError, TypeError, AttributeError, subprocess.SubprocessError) as error:
        receipt["errors"].append({"error": str(error)})
    receipt.update(result="FAIL" if receipt["errors"] else "PASS", finished_at=timestamp(),
                   scope="Only attested checkout build/Python-cache directories; no global caches or source",
                   worker_scope=("Gradle plus source-bound Apple native/device cleanup receipts" if
                                 task.get("GITHUB_JOB") in APPLE_JOB_CYCLES else
                                 "Gradle stop; Android receipt, when present, covers only sampled selected-UID "
                                 "SDK/emulator executable-path absence, not termination or AVD retirement" if
                                 task.get("GITHUB_JOB") == "desktop-android" else
                                 "Gradle stop; workflow pins in-process Kotlin compiler execution. "
                                 "Other native/emulator worker termination needs separate evidence"))
    return receipt


def context() -> tuple[Path, Path, dict]:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("This helper is restricted to the isolated GitHub verification job")
    task = {key: os.environ[key] for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB")}
    if not all(re.fullmatch(r"[A-Za-z0-9_-]+", value) for value in task.values()):
        raise RuntimeError("Unsafe task identity")
    root = Path(os.environ["GITHUB_WORKSPACE"]).resolve(strict=True)
    if root != Path.cwd().resolve():
        raise RuntimeError("Run only from the task checkout root")
    temporary = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    if temporary == root or root in temporary.parents:
        raise RuntimeError("Receipts must survive checkout-output cleanup")
    prefix = "parlor-verification-" + "-".join(task.values())
    return root, temporary / prefix, task


def main() -> int:
    root, prefix, task = context()
    claim_path = Path(str(prefix) + "-ownership.json")
    mode = sys.argv[1]
    if mode == "prepare":
        if len(sys.argv) != 2:
            raise RuntimeError("Invalid prepare arguments")
        prepare(root, claim_path, task)
        return 0
    if mode == "stop":
        if len(sys.argv) != 3:
            raise RuntimeError("Invalid stop arguments")
        label = sys.argv[2]
        if not re.fullmatch(r"[a-z0-9-]+", label):
            raise RuntimeError("Invalid cycle label")
        result = {"task": task, **stop_gradle(root)}
        write_new(Path(str(prefix) + "-stop-" + label + ".json"), result)
        print(json.dumps(result))
        return 0 if result["exit_code"] == 0 else 1
    if mode == "cleanup":
        # Invalid/duplicate/unknown flags reach cleanup's failure path after Gradle stop.
        reader = ANDROID_EXE_READER_UNPRIVILEGED
        if len(sys.argv) != 2:
            reader = (sys.argv[2].removeprefix("--android-reader=") if len(sys.argv) == 3 and
                      sys.argv[2].startswith("--android-reader=") else "invalid")
        upload = {
            "outcome": os.environ.get("PARLOR_VERIFICATION_UPLOAD_OUTCOME", ""),
            "artifact_id": os.environ.get("PARLOR_VERIFICATION_ARTIFACT_ID", ""),
            "artifact_digest": os.environ.get("PARLOR_VERIFICATION_ARTIFACT_DIGEST", ""),
        }
        try:
            apple_outcomes = json.loads(os.environ.get("PARLOR_APPLE_CYCLE_OUTCOMES", "null"))
        except ValueError:
            apple_outcomes = None  # cleanup still stops Gradle before refusing deletion.
        receipt = cleanup(root, claim_path, task, upload,
                          os.environ.get("PARLOR_VERIFICATION_PREPARE_OUTCOME", ""), apple_outcomes,
                          android_reader=reader)
        # Keep failure evidence in the job log even if a historical receipt prevents replacement.
        print(json.dumps(receipt))
        write_new(Path(str(prefix) + "-cleanup.json"), receipt)
        return 0 if receipt["result"] == "PASS" else 1
    raise RuntimeError("Unknown hygiene mode")


if __name__ == "__main__":
    raise SystemExit(main())
