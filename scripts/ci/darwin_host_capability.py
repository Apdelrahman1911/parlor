"""Explicit hosted ABI/capability control; no application, build, or global kill.

Only one direct /bin/sleep child is launched with the existing runner marker.
Native success requires stable identity, exact marker and audit-token SIGTERM.
Defensive Popen-handle cleanup cannot turn a failed native check into success.
Importing this module performs no host, process, signal or filesystem operation.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

from scripts.ci.darwin_worker_identity import DarwinWorkerBackend

SCOPE = "darwin-host-capability"
REPOSITORY = "Apdelrahman1911/parlor"
MARKER = re.compile(r"github_[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")


def require(condition: bool, code: str) -> None:
    if not condition:
        raise RuntimeError(code)


def observe(backend, tracking: str, interrupted, spawn=subprocess.Popen) -> dict:
    """Bound one real unreaped child; persist only public statuses, never identity."""
    child = None
    row = dict(result="FAIL", stage="admission", native_signal=False,
               direct_child_reaped=False, fallback_cleanup=False, interrupted=False)
    try:
        require(MARKER.fullmatch(tracking) is not None, "runner-marker-required")
        require(not interrupted(), "control-interrupted")
        row["stage"] = "spawn"
        # No credentials, hooks, arbitrary options, or synthetic tracking marker.
        environment = dict(PATH="/usr/bin:/bin", LC_ALL="C", RUNNER_TRACKING_ID=tracking)
        child = spawn(["/bin/sleep", "30"], env=environment, stdin=subprocess.DEVNULL,
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, close_fds=True)
        require(not interrupted(), "control-interrupted")
        row["stage"] = "native-identity"
        identity = backend.read(child.pid)
        require(identity is not None and identity.pid == child.pid and identity.uid == os.getuid() and
                len(identity.token) == 8 and identity.token[5] == child.pid and
                identity.started[0] > 0 and 0 <= identity.started[1] < 1000000 and
                identity.command == "/bin/sleep", "direct-child-identity-invalid")
        row["stage"] = "native-tracking"
        require(backend.tracking_id(identity) == tracking, "direct-child-marker-invalid")
        require(backend.read(child.pid) == identity, "direct-child-identity-changed")
        require(not interrupted(), "control-interrupted")
        row["stage"] = "native-signal"
        require(backend.signal(identity, signal.SIGTERM), "native-signal-declined")
        row["native_signal"] = True
        row["stage"] = "reap"
        require(child.wait(timeout=5) == -signal.SIGTERM, "direct-child-exit-invalid")
        row["direct_child_reaped"] = True
        require(not interrupted(), "control-interrupted")
        row.update(result="PASS", stage="complete")
    except BaseException as error:
        # Native exception strings can include private metadata; never persist them.
        row["error_type"] = type(error).__name__
    finally:
        row["interrupted"] = bool(interrupted())
        if row["interrupted"]:
            row["result"] = "FAIL"
        if child is not None:
            try:
                if child.poll() is None:
                    row.update(result="FAIL", fallback_cleanup=True)
                    # Only this actual unreaped Popen handle, never a supplied PID/PGID.
                    child.terminate()
                    try:
                        child.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=2)
                row["direct_child_reaped"] = child.returncode is not None
                if not row["direct_child_reaped"]:
                    row["result"] = "FAIL"
            except BaseException as error:
                row.update(result="FAIL", direct_child_reaped=False, cleanup_error_type=type(error).__name__)
    return row


def git(root: Path, *args: str) -> str:
    value = subprocess.run(["git", "-C", str(root), *args], check=True, text=True,
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15)
    return value.stdout.strip()


def context(environment, root: Path) -> tuple[Path, dict]:
    require(environment.get("GITHUB_ACTIONS") == "true" and
            environment.get("RUNNER_ENVIRONMENT") == "github-hosted" and
            environment.get("GITHUB_REPOSITORY") == REPOSITORY and
            environment.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and
            environment.get("GITHUB_JOB") == SCOPE and
            environment.get("PARLOR_DISPATCH_SCOPE") == SCOPE, "explicit-hosted-control-required")
    architecture = environment.get("PARLOR_EXPECTED_ARCHITECTURE")
    require(architecture in {"arm64", "x86_64"}, "reviewed-architecture-required")
    task = {name: environment.get(name, "") for name in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")}
    require(all(re.fullmatch(r"[1-9][0-9]{0,19}", value) for value in task.values()), "run-identity-required")
    source = environment.get("PARLOR_FROZEN_SOURCE_SHA", "")
    require(re.fullmatch(r"[0-9a-f]{40}", source) is not None and
            source == environment.get("GITHUB_SHA") == environment.get("GITHUB_WORKFLOW_SHA"), "frozen-source-required")
    require(str(root) == environment.get("GITHUB_WORKSPACE") and
            git(root, "rev-parse", "--show-toplevel") == str(root) and
            git(root, "rev-parse", "HEAD") == source and
            not git(root, "status", "--porcelain", "--untracked-files=all"), "unchanged-source-required")
    tree = git(root, "rev-parse", "HEAD^{tree}")
    require(re.fullmatch(r"[0-9a-f]{40}", tree) is not None, "source-tree-required")
    temporary = Path(environment.get("RUNNER_TEMP", ""))
    require(temporary.is_absolute() and temporary.is_dir() and not temporary.is_symlink() and
            temporary.resolve(strict=True) == temporary and
            temporary.stat().st_uid == os.getuid() and temporary != root and root not in temporary.parents,
            "canonical-external-runner-temp-required")
    require(MARKER.fullmatch(environment.get("RUNNER_TRACKING_ID", "")) is not None,
            "runner-marker-required")
    output = temporary / ("parlor-darwin-capability-" + task["GITHUB_RUN_ID"] + "-" +
                          task["GITHUB_RUN_ATTEMPT"] + "-" + architecture + ".json")
    require(not output.exists() and not output.is_symlink(), "fresh-receipt-required")
    return output, dict(schema=1, source=dict(head=source, tree=tree), task=task,
                        expected_architecture=architecture)


def main() -> int:
    interrupted = []
    handlers = {}
    receipt = None
    try:
        root = Path(__file__).resolve().parents[2]
        output, receipt = context(os.environ, root)
        for signum in (signal.SIGINT, signal.SIGTERM):
            handlers[signum] = signal.getsignal(signum)
            signal.signal(signum, lambda signum, _frame: interrupted.append(signum) if not interrupted else None)
        receipt["result"] = "FAIL"
        try:
            backend = DarwinWorkerBackend()  # Actual native self-capability, never a synthetic admission.
            receipt["abi"] = backend.host_abi
            require(backend.host_abi["architecture"] == receipt["expected_architecture"], "runner-architecture-mismatch")
            receipt["control"] = observe(backend, os.environ["RUNNER_TRACKING_ID"], lambda: bool(interrupted))
            receipt["result"] = receipt["control"]["result"]
            require(git(root, "rev-parse", "HEAD") == receipt["source"]["head"] and
                    git(root, "rev-parse", "HEAD^{tree}") == receipt["source"]["tree"] and
                    not git(root, "status", "--porcelain", "--untracked-files=all"), "source-changed-during-control")
        except BaseException as error:
            receipt.update(result="FAIL", error_type=type(error).__name__)
        if interrupted:
            receipt.update(result="FAIL", interrupted=True)
        # Exclusive creation; no deletion, stale receipt reuse, or private metadata.
        with output.open("x", encoding="utf-8") as stream:
            json.dump(receipt, stream, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        print(json.dumps(receipt, sort_keys=True))
        return 0 if receipt["result"] == "PASS" and not interrupted else 1
    except BaseException as error:
        print(json.dumps(dict(result="FAIL", stage="context-or-receipt", error_type=type(error).__name__)))
        return 1
    finally:
        for signum, handler in handlers.items():
            signal.signal(signum, handler)


if __name__ == "__main__":
    raise SystemExit(main())
