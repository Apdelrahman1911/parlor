#!/usr/bin/env python3
"""Bounded cleanup for the fresh, task-owned GitHub verification checkout.

This is not a local-worktree cleaner and never kills arbitrary worker processes.
Initialize before output exists; only successful evidence upload authorizes cleanup.
"""
from __future__ import annotations

import json
import hashlib
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath


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
    claim = source_identity(root)
    if git(root, "status", "--porcelain").strip():
        raise RuntimeError("Verification must begin from a clean checkout")
    for relative in claim["outputs"]:
        path = checked_path(root, relative)
        if path.exists():
            raise RuntimeError(f"Pre-existing output is not task-owned: {relative}")
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


def verify_apple_cleanup(root: Path, claim_path: Path, task: dict, outcomes: dict | None) -> dict:
    """Current step outcomes AND current, source-bound receipts gate Apple deletion."""
    cycles = ("apple-aggregate", "apple-ui", "apple-wrapper")
    if not isinstance(outcomes, dict) or set(outcomes) != set(cycles):
        raise RuntimeError("Missing complete Apple cycle outcomes; retain outputs")
    suffix = "-ownership.json"
    if not str(claim_path).endswith(suffix):
        raise RuntimeError("Invalid Apple ownership receipt prefix")
    prefix = str(claim_path)[:-len(suffix)]
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
            apple_outcomes: dict | None = None) -> dict:
    receipt = {"task": task, "started_at": timestamp(), "removed": [], "not_created": [],
               "retained": [], "upload": upload, "preparation_outcome": preparation_outcome, "errors": []}
    receipt["gradle_stop"] = stop_gradle(root)
    try:
        if receipt["gradle_stop"]["exit_code"] != 0:
            raise RuntimeError("Gradle stop failed; do not race active workers by deleting outputs")
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
        if task.get("GITHUB_JOB") == "ios":
            receipt["apple_cleanup"] = verify_apple_cleanup(root, claim_path, task, apple_outcomes)
        if not uploaded_evidence_is_retained(upload):
            raise RuntimeError("No successful nonempty verification upload; preserve required evidence outputs")
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
                                 task.get("GITHUB_JOB") == "ios" else
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
        prepare(root, claim_path, task)
        return 0
    if mode == "stop":
        label = sys.argv[2]
        if not re.fullmatch(r"[a-z0-9-]+", label):
            raise RuntimeError("Invalid cycle label")
        result = {"task": task, **stop_gradle(root)}
        write_new(Path(str(prefix) + "-stop-" + label + ".json"), result)
        print(json.dumps(result))
        return 0 if result["exit_code"] == 0 else 1
    if mode == "cleanup":
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
                          os.environ.get("PARLOR_VERIFICATION_PREPARE_OUTCOME", ""), apple_outcomes)
        # Keep failure evidence in the job log even if a historical receipt prevents replacement.
        print(json.dumps(receipt))
        write_new(Path(str(prefix) + "-cleanup.json"), receipt)
        return 0 if receipt["result"] == "PASS" else 1
    raise RuntimeError("Unknown hygiene mode")


if __name__ == "__main__":
    raise SystemExit(main())
