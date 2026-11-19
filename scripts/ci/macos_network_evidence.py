"""Observe hosted macOS DNS only after a failed verification step; never retry it."""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


PUBLIC_HOSTS = ("services.gradle.org", "github.com")
OBSERVATION_TIMEOUT_SECONDS = 5
MAX_OUTPUT_BYTES = 32768
TASK_KEYS = ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "RUNNER_OS", "RUNNER_ARCH")
REQUIRED_ENVIRONMENT = dict(
    GITHUB_ACTIONS="true", RUNNER_ENVIRONMENT="github-hosted", RUNNER_OS="macOS",
    RUNNER_ARCH="X64", GITHUB_REPOSITORY="Apdelrahman1911/parlor",
    GITHUB_JOB="desktop-macos-x64", PARLOR_FAILED_STEP_OUTCOME="failure",
)
SOURCE_FILES = (".github/workflows/production-verification.yml", "scripts/ci/macos_network_evidence.py")
RESOLVER_CODE = (
    "import json, socket, sys; "
    "print(json.dumps(sorted({entry[4][0] for entry in "
    "socket.getaddrinfo(sys.argv[1], 443, type=socket.SOCK_STREAM)})[:16]))"
)


def runner_task(environment: dict[str, str]) -> dict[str, str]:
    if any(environment.get(name) != value for name, value in REQUIRED_ENVIRONMENT.items()):
        raise RuntimeError("network evidence requires the failed hosted macOS x64 verification step")
    for name in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"):
        if not re.fullmatch(r"[1-9][0-9]{0,19}", environment.get(name, "")):
            raise RuntimeError("invalid run identity")
    return {name: environment[name] for name in TASK_KEYS}


def commands() -> tuple[tuple[str, ...], ...]:
    return (("/usr/sbin/scutil", "--dns"),) + tuple(
        (sys.executable, "-I", "-c", RESOLVER_CODE, host) for host in PUBLIC_HOSTS
    )


def observe(command: tuple[str, ...], root: Path, temporary_root: Path) -> dict:
    started = time.monotonic()
    result = {"command": list(command), "timeout_seconds": OBSERVATION_TIMEOUT_SECONDS}
    with tempfile.TemporaryFile(dir=temporary_root) as stdout, tempfile.TemporaryFile(dir=temporary_root) as stderr:
        try:
            completed = subprocess.run(command, cwd=root, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                                       timeout=OBSERVATION_TIMEOUT_SECONDS, check=False)
            result.update(status="COMPLETED", exit_code=completed.returncode)
        except subprocess.TimeoutExpired:
            result.update(status="TIMEOUT", exit_code=None)
        except OSError as error:
            result.update(status="UNAVAILABLE", exit_code=None, error_type=type(error).__name__, error_errno=error.errno)
        for name, stream in (("stdout", stdout), ("stderr", stderr)):
            stream.seek(0)
            raw = stream.read(MAX_OUTPUT_BYTES + 1)
            result[name] = raw[:MAX_OUTPUT_BYTES].decode("utf-8", errors="replace")
            result[name + "_truncated"] = len(raw) > MAX_OUTPUT_BYTES
    result["elapsed_seconds"] = time.monotonic() - started
    return result


def git_source(root: Path) -> dict:
    subprocess.run(("git", "diff", "--quiet", "HEAD"), cwd=root, stdin=subprocess.DEVNULL,
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=5, check=True)
    source = subprocess.run(("git", "rev-parse", "HEAD", "HEAD^{tree}"), cwd=root, stdin=subprocess.DEVNULL,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=5, check=True)
    values = source.stdout.decode("ascii").splitlines()
    if len(values) != 2 or not all(re.fullmatch(r"[0-9a-f]{40}", value) for value in values):
        raise RuntimeError("invalid source identity")
    return dict(zip(("head", "tree"), values))


def collect(environment: dict[str, str], root: Path) -> Path:
    task = runner_task(environment)
    temporary_root = Path(environment.get("RUNNER_TEMP", ""))
    if (not temporary_root.is_absolute() or not temporary_root.is_dir() or
            temporary_root.is_symlink() or temporary_root.resolve() != temporary_root):
        raise RuntimeError("network evidence requires the canonical existing runner temporary directory")
    source = git_source(root)
    files = {}
    for name in SOURCE_FILES:
        path = root / name
        if path.is_symlink() or not path.is_file():
            raise RuntimeError("network evidence source is missing or linked")
        files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    source["files"] = files
    target = temporary_root / (f"parlor-verification-{task['GITHUB_RUN_ID']}-{task['GITHUB_RUN_ATTEMPT']}-"
                               f"{task['GITHUB_JOB']}-network.json")
    with target.open("x", encoding="utf-8") as output:
        receipt = dict(result="OBSERVATION_ONLY_NOT_QUALIFICATION", task=task, source=source,
                       started_at=datetime.now(timezone.utc).isoformat(), original_step_outcome="failure",
                       original_failed_lookup_captured=False,
                       scope="Post-failure DNS snapshot only; not the original failed lookup, a cause, retry or qualification.")
        receipt["observations"] = [observe(command, root, temporary_root) for command in commands()]
        receipt["finished_at"] = datetime.now(timezone.utc).isoformat()
        json.dump(receipt, output, indent=2)
        output.write("\n")
    return target


def main() -> int:
    try:
        if sys.platform != "darwin" or len(sys.argv) != 1:
            raise RuntimeError("network evidence requires macOS and no command-line arguments")
        target = collect(dict(os.environ), Path(__file__).resolve().parents[2])
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print("Network evidence was not collected:", type(error).__name__)
        return 1
    print("Retained post-failure observation only:", target.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
