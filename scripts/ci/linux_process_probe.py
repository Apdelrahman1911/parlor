#!/usr/bin/env python3
"""Opt-in Linux procfs verification and focused Android-cleanup admission.

The explicitly selected read-only executable reader never relaxes absence checks.
The host sample stays separate from unprivileged and privileged owned controls.
This module never builds or launches an emulator. Importing it does not observe
processes or allocate resources; the Android workflow owns its separate smoke run.
"""
from __future__ import annotations

import errno
import hashlib
import json
import os
import platform
import re
import select
import signal
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.ci import verification_hygiene as hygiene

SCOPE = "linux-process-probe"
ANDROID_CLEANUP_SCOPE = "android-cleanup-only"
REPOSITORY, BRANCH = "Apdelrahman1911/parlor", "fix/local-readiness-2026-09-07"
WORKFLOW = ".github/workflows/production-verification.yml"
EXE_READER = "sudo-proc-exe-v2"
CONTROL_MODES = ((1, "unprivileged"), (0, "unprivileged"), (0, EXE_READER))
CONTROL_PATHS = (WORKFLOW, "scripts/ci/linux_process_probe.py", "scripts/ci/verification_hygiene.py",
    "scripts/ci/linux_exe_reader.py", "scripts/release/tests/test_linux_exe_reader.py",
    "scripts/release/workflow_contract.py", "scripts/release/tests/test_linux_process_probe.py",
    "scripts/release/tests/test_ci_verification_hygiene.py", "scripts/release/tests/test_workflow_contract.py",
    "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
ANDROID_CLEANUP_EXTRA_CONTROLS = (
    "scripts/android/run_release_managed_device_smoke.sh", "scripts/android/supervise_managed_device.py",
    "scripts/release/tests/test_android_managed_device_supervision.py", "build.gradle.kts", "composeApp/build.gradle.kts",
    "config/release-policy.json", "gradle.properties", "gradle/libs.versions.toml",
)
CHILD = '''import ctypes, json, os, signal, sys
signal.alarm(12)
uid, mode = os.getuid(), int(sys.argv[1])
if uid <= 0 or uid != os.geteuid() or mode not in (0, 1): os._exit(2)
libc = ctypes.CDLL(None, use_errno=True)
libc.prctl.argtypes = [ctypes.c_int] + [ctypes.c_ulong] * 4
libc.prctl.restype = ctypes.c_int
if libc.prctl(4, mode, 0, 0, 0) != 0: os._exit(3)
observed = libc.prctl(3, 0, 0, 0, 0)
if observed != mode: os._exit(4)
os.write(1, (json.dumps(dict(pid=os.getpid(), uid=uid, dumpable=observed)) + "\\n").encode())
sys.stdin.buffer.read(1)
os._exit(0)
'''


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def file_bytes(path):
    before = path.lstat()
    require(path.resolve() == path and stat.S_ISREG(before.st_mode), "untrusted-probe-file")
    with path.open("rb") as stream:
        raw = stream.read(1024 * 1024 + 1)
    identity = lambda value: (value.st_dev, value.st_ino, value.st_mode, value.st_size, value.st_mtime_ns, value.st_ctime_ns)
    require(len(raw) <= 1024 * 1024 and identity(before) == identity(path.lstat()), "probe-file-changed-or-oversized")
    return raw


def controls(root=ROOT, *, scope=SCOPE):
    require(type(scope) is str and scope in {SCOPE, ANDROID_CLEANUP_SCOPE}, "invalid-linux-control-scope")
    paths = CONTROL_PATHS + (ANDROID_CLEANUP_EXTRA_CONTROLS if scope == ANDROID_CLEANUP_SCOPE else ())
    rows = [dict(path=name, sha256=hashlib.sha256(file_bytes(root / name)).hexdigest())
            for name in sorted(paths)]
    return dict(files=rows, control_sha256=hashlib.sha256(json.dumps(rows, separators=(",", ":")).encode()).hexdigest())


def admit(*, scope=SCOPE):
    require(type(scope) is str and scope in {SCOPE, ANDROID_CLEANUP_SCOPE}, "invalid-linux-admission-scope")
    root, prefix, task = hygiene.context()
    env = os.environ
    expected = dict(GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_REPOSITORY=REPOSITORY,
        GITHUB_REF="refs/heads/" + BRANCH, GITHUB_JOB="desktop-android", RUNNER_OS="Linux", RUNNER_ARCH="X64",
        GITHUB_WORKFLOW_REF=REPOSITORY + "/" + WORKFLOW + "@refs/heads/" + BRANCH)
    require(root == ROOT and all(env.get(key) == value for key, value in expected.items()), "unexpected-probe-context")
    require(all(re.fullmatch(r"[1-9][0-9]{0,19}", task[key]) for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")),
            "invalid-probe-run-identity")
    raw = env.get("PARLOR_LINUX_PROBE_INPUTS", "")
    require(len(raw) <= 8192, "probe-input-limit")
    inputs = json.loads(raw)
    allowed = {"verification_scope", "native_selection", "frozen_source_sha", "approved_probe_control_sha256",
               "preflight_run_id", "preflight_run_attempt", "preflight_artifact_id", "preflight_artifact_sha256",
               "approved_l08_control_sha256", "approved_normal_control_sha256"}
    require(isinstance(inputs, dict) and set(inputs) <= allowed and all(type(v) is str for v in inputs.values()),
            "invalid-probe-inputs")
    commit = inputs.get("frozen_source_sha", "")
    require(inputs.get("verification_scope") == scope and re.fullmatch(r"[0-9a-f]{40}", commit) and
            commit == env.get("GITHUB_SHA") == env.get("GITHUB_WORKFLOW_SHA"), "unbound-probe-source")
    require(inputs.get("native_selection", "") in ("", "paired") and not any(inputs.get(key) for key in
            allowed - {"verification_scope", "native_selection", "frozen_source_sha", "approved_probe_control_sha256"}),
            "unrelated-probe-input")
    source = hygiene.source_identity(root)
    require(source["head"] == commit and hygiene.git(root, "branch", "--show-current").strip() == BRANCH and
            hygiene.git(root, "rev-parse", "--is-shallow-repository").strip() == "false" and
            not hygiene.git(root, "status", "--porcelain").strip(), "unclean-or-unbound-probe-checkout")
    release = platform.freedesktop_os_release()
    require(sys.platform == "linux" and platform.machine() == "x86_64" and
            release.get("ID") == "ubuntu" and release.get("VERSION_ID") == "24.04", "qualified-linux-host-required")
    manifest, sdk = controls(root, scope=scope), hygiene.android_sdk_binding()[1]
    require(inputs.get("approved_probe_control_sha256") == manifest["control_sha256"], "unapproved-probe-controls")
    proof = dict(verification_scope=scope, task=task, source={key: source[key] for key in ("root", "head", "tree")}, controls=manifest,
                 android_sdk=sdk, platform=dict(os="ubuntu-24.04", machine=platform.machine(),
                    kernel=platform.release(), python=sys.version.split()[0]))
    return root, prefix, task, source, proof


def owned_dumpability_control(parent: Path, binding: dict, dumpable: int, *, reader="unprivileged") -> dict:
    """Real nonroot Linux control; no GitHub impersonation is needed by local callers.

    Caller owns the outer evidence/Gradle-stop lane. Only this observer and its
    direct child are enumerated, through owned symlinks to real /proc entries.
    """
    require(type(dumpable) is int and (dumpable, reader) in CONTROL_MODES and
            hygiene.android_sdk_binding()[1] == binding, "invalid-live-control-binding")
    require(parent.resolve() == parent and parent.is_dir() and parent.stat().st_uid == os.getuid(), "unowned-control-parent")
    row = dict(kind="LIVE_LINUX_CONTROL_SYNTHETIC_ENUMERATION", dumpable=dumpable, reader=reader, result="CONTROL_FAIL",
               started_at=hygiene.timestamp(), child_reaped=False, fixture_removed=False, errors=[],
               scope="Only self/direct-child enumeration with real procfs reads; not a host inventory or emulator runtime.")
    child, fixture, identity, links, previous, interrupted = None, None, None, {}, {}, []
    try:
        fixture = Path(tempfile.mkdtemp(prefix="parlor-linux-proc-control-", dir=parent))
        identity = fixture.lstat()
        row["fixture_basename"] = fixture.name
        for number in (signal.SIGINT, signal.SIGTERM):
            previous[number] = signal.signal(number, lambda signum, _frame: interrupted.append(signum) if not interrupted else None)
        child = subprocess.Popen(["/usr/bin/python3", "-I", "-B", "-c", CHILD, str(dumpable)],
            cwd="/", env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8"}, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, close_fds=True, start_new_session=True)
        row["owned_pid"] = child.pid
        require(select.select([child.stdout], [], [], 3)[0] and not interrupted, "control-readiness-timeout-or-interrupt")
        ready = os.read(child.stdout.fileno(), 256)
        require(ready.endswith(b"\n") and len(ready) < 256 and all(type(v) is int for v in json.loads(ready).values()) and json.loads(ready) ==
                dict(pid=child.pid, uid=binding["uid"], dumpable=dumpable), "control-readiness-mismatch")
        row["readiness"] = json.loads(ready)  # Only our direct child's fixed PR_GET_DUMPABLE response.
        for pid in (os.getpid(), child.pid):
            target = "/proc/" + str(pid)
            link = fixture / str(pid)
            link.symlink_to(target)
            links[link] = target
        row["audit"] = hygiene.audit_android_emulator_absence(binding, proc=fixture, reader=reader)
        require(not interrupted, "control-interrupted")
        audit = row["audit"]
        expected = (audit.get("result") == "PASS" and audit.get("errors") == [] and audit.get("sampled") == 2 and
                    audit.get("selected_uid") == 2) if dumpable or reader == EXE_READER else (
                    audit.get("result") == "FAIL" and audit.get("errors") == [{"code": "PermissionError"}] and
                    audit.get("failure_context") == dict(operation="before_exe_readlink", selection=True, errno=errno.EACCES))
        require(expected and audit.get("enumerated") == 2 and audit.get("matching_executables") == 0,
                "live-control-expectation-not-observed")
        require(audit.get("mixed_uid_samples") == 0 and audit.get("mixed_uid_privileged_samples") == 0,
                "unexpected-mixed-uid-owned-control")
        if reader == EXE_READER:
            require(audit.get("exe_reader") == dict(mode=EXE_READER, eacces_denials=1, privileged_reads=1),
                    "live-privileged-read-not-observed")
        row["result"] = "CONTROL_PASS"
    except BaseException as error:
        row["errors"].append(dict(stage="observation", error_type=type(error).__name__))
    finally:
        if child is not None:
            try:
                try:
                    child.stdin.close()  # EOF is the normal retirement command.
                except OSError as error:
                    row["errors"].append(dict(stage="stdin-close", error_type=type(error).__name__))
                try:
                    child.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    row["errors"].append(dict(stage="retirement", error_type="ChildTimeout"))
                    child.terminate()
                    try:
                        child.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=2)
                row.update(child_reaped=child.returncode is not None, child_exit_code=child.returncode)
                require(child.returncode == 0, "control-child-failed")
            except BaseException as error:
                row["errors"].append(dict(stage="retirement", error_type=type(error).__name__))
            finally:
                try:
                    child.stdout.close()
                except OSError as error:
                    row["errors"].append(dict(stage="stdout-close", error_type=type(error).__name__))
        else:
            row["child_reaped"] = True  # No child was created.
        try:
            if fixture is not None:
                current = fixture.lstat()
                require(identity is not None and (current.st_dev, current.st_ino, current.st_uid) ==
                        (identity.st_dev, identity.st_ino, identity.st_uid) and stat.S_ISDIR(current.st_mode) and
                        row["child_reaped"] and set(fixture.iterdir()) == set(links), "control-custody-changed-or-child-unreaped")
                require(all(path.is_symlink() and os.readlink(path) == target for path, target in links.items()),
                        "control-link-changed")
                for path in links:
                    path.unlink()
                fixture.rmdir()
            row["fixture_removed"] = True
        except BaseException as error:
            row["errors"].append(dict(stage="fixture-cleanup", error_type=type(error).__name__))
        for number, handler in previous.items():
            try:
                signal.signal(number, handler)
            except OSError as error:
                row["errors"].append(dict(stage="signal-restore", error_type=type(error).__name__))
        if row["errors"] or interrupted:
            row["result"] = "CONTROL_FAIL"
        row.update(interrupted=bool(interrupted), finished_at=hygiene.timestamp())
    return row


def run():
    root, prefix, task = hygiene.context()
    row = dict(kind="BARE_HOST_LINUX_PROC_DISCRIMINATOR_NOT_APP_QUALIFICATION", result="FAIL", task=task,
               started_at=hygiene.timestamp(), binding=None, reader=EXE_READER,
               host_audit={"result": "NOT_RUN"}, controls=[], errors=[],
               scope="No build/emulator/managed-device execution; cannot repair previous cleanup or combined qualification.")
    try:
        _, _, _, source, row["binding"] = admit()
        claim = json.loads(file_bytes(Path(str(prefix) + "-ownership.json")))
        require(os.environ.get("PARLOR_VERIFICATION_PREPARE_OUTCOME") == "success" and claim.get("task") == task and
                all(claim.get(key) == value for key, value in source.items()) and
                claim.get("android_sdk") == row["binding"]["android_sdk"], "probe-ownership-not-prepared")
        row["host_audit"] = hygiene.audit_android_emulator_absence(claim["android_sdk"], reader=EXE_READER)
        for dumpable, reader in CONTROL_MODES:
            row["controls"].append(owned_dumpability_control(prefix.parent, claim["android_sdk"], dumpable, reader=reader))
            require(row["controls"][-1]["child_reaped"] and row["controls"][-1]["fixture_removed"], "control-resources-not-retired")
        require(row["binding"] == admit()[4], "probe-source-controls-or-sdk-changed")
        if (row["host_audit"]["result"] == "PASS" and
                row["host_audit"].get("exe_reader", {}).get("mode") == EXE_READER and
                all(item["result"] == "CONTROL_PASS" for item in row["controls"])):
            row["result"] = "PASS"
    except BaseException as error:
        row["errors"].append({"error_type": type(error).__name__})
    finally:
        row["finished_at"] = hygiene.timestamp()
        try:
            hygiene.write_new(Path(str(prefix) + "-linux-probe.json"), row)
            print(json.dumps(row))
        finally:  # Stop even if preserving the observation fails.
            stopped = dict(task=task, binding=row["binding"], **hygiene.stop_gradle(root))
            hygiene.write_new(Path(str(prefix) + "-stop-linux-probe.json"), stopped)
            print(json.dumps(stopped))
    return 0 if row["result"] == "PASS" and stopped["exit_code"] == 0 else 1


def cleanup():
    root, prefix, task = hygiene.context()
    try:
        proof = admit()[4]
        observation = json.loads(file_bytes(Path(str(prefix) + "-linux-probe.json")))
        stopped = json.loads(file_bytes(Path(str(prefix) + "-stop-linux-probe.json")))
        rows = observation["controls"]
        require(observation.get("binding") == proof and observation.get("reader") == EXE_READER and
                stopped.get("binding") == proof and
                stopped.get("task") == task and stopped.get("exit_code") == 0 and
                tuple((item.get("dumpable"), item.get("reader")) for item in rows) == CONTROL_MODES and
                all(item.get("child_reaped") is True and item.get("fixture_removed") is True for item in rows),
                "probe-resource-retirement-or-binding-incomplete")
        upload = {key: os.environ.get(variable, "") for key, variable in (
            ("outcome", "PARLOR_VERIFICATION_UPLOAD_OUTCOME"), ("artifact_id", "PARLOR_VERIFICATION_ARTIFACT_ID"),
            ("artifact_digest", "PARLOR_VERIFICATION_ARTIFACT_DIGEST"))}
        receipt = hygiene.cleanup(root, Path(str(prefix) + "-ownership.json"), task, upload,
                                  os.environ.get("PARLOR_VERIFICATION_PREPARE_OUTCOME", ""), android_reader=EXE_READER)
    except BaseException as error:
        receipt = dict(task=task, result="FAIL", removed=[], errors=[{"error_type": type(error).__name__}],
                       scope="Probe pre-cleanup guard failed; no output deletion authorized.", gradle_stop=hygiene.stop_gradle(root))
    print(json.dumps(receipt))
    hygiene.write_new(Path(str(prefix) + "-cleanup.json"), receipt)
    return 0 if receipt["result"] == "PASS" else 1


if __name__ == "__main__":
    require(len(sys.argv) == 2 and sys.argv[1] in {
        "controls", "validate", "run", "cleanup", "controls-android-cleanup", "validate-android-cleanup",
    }, "invalid-probe-mode")
    scope = ANDROID_CLEANUP_SCOPE if sys.argv[1].endswith("-android-cleanup") else SCOPE
    if sys.argv[1] in {"controls", "controls-android-cleanup"}:
        print(json.dumps(controls(scope=scope), indent=2))
    elif sys.argv[1] in {"validate", "validate-android-cleanup"}:
        print(json.dumps(admit(scope=scope)[4]))
    else:
        raise SystemExit(run() if sys.argv[1] == "run" else cleanup())
