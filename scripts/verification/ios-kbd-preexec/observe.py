#!/usr/bin/env python3
"""One unchanged public EN/AR run; one delayed, owned keyboard proxy stack.

No app adapter, event replay, readiness warmup, assertion change or production
qualification. Profiling may affect scheduling; a passing run is not causality.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple
from scripts.ci import owned_ci_simulator as sim
from scripts.ci import verification_hygiene as common
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend

BASE = "811dfde660fb0dfd7c6e0d7540989f5001d13e54"
TESTS = tuple("iosAppUITests/IOSAppLaunchUITests/" + name for name in (
    "testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish",
    "testAdditionalGamesExposeOnlyMultiplayerSetupInArabic"))
DELAY = 5.0  # Stack trigger, NOT a test deadline or an input wait.
MAX_LOG = 16 * 1024 * 1024


def target_event(row, launcher, uid):
    """Native ndjson only; decoder-derived message/pid aliases are not native proof."""
    if not isinstance(row, dict) or row.get("processID") != launcher:
        return None
    subsystem = row.get("subsystem")
    message = row.get("eventMessage")
    if not isinstance(subsystem, str) or not isinstance(message, str):
        return None
    job = re.fullmatch(r"user/" + str(uid) + r"/com\.apple\.TextInput\.kbd \[([1-9][0-9]*)\]", subsystem)
    if not job:
        return None
    pid = int(job[1])
    if message == f"xpcproxy_sim spawned with pid {pid}":
        return pid, "spawn"
    if message == "service state: running":
        return pid, "running"
    return None


def eligible(identity, basic, launcher, runtime_root, not_before, now):
    """Reject PID reuse, foreign parent/runtime, and an already-exec'd keyboard."""
    if identity is None or basic is None:
        return False
    started = identity.started[0] + identity.started[1] / 1_000_000
    return (basic.pid == identity.pid and basic.ppid == launcher.pid and
            basic.uid == identity.uid == launcher.uid and
            (basic.start_sec, basic.start_usec) == identity.started and
            identity.command == str(runtime_root / "usr/libexec/xpcproxy_sim") and
            not_before <= started <= now - DELAY)


def exact_roster(log):
    expected = {test.rsplit("/", 1)[1] for test in TESTS}
    starts = re.findall(r"Test Case '-\[iosAppUITests\.IOSAppLaunchUITests (test\w+)\]' started\.", log)
    passes = re.findall(r"Test Case '-\[iosAppUITests\.IOSAppLaunchUITests (test\w+)\]' passed \(([\d.]+) seconds\)\.", log)
    return (len(starts) == len(passes) == len(expected) and set(starts) == expected and
            {name for name, _ in passes} == expected and all(float(seconds) > 0 for _, seconds in passes))


def record(path, value):
    common.write_new(path, value)


def command(argv, out, label, timeout=120):
    started = time.time()
    try:
        result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=timeout, check=False)
        code, stdout, stderr = result.returncode, result.stdout, result.stderr
        status = "EXITED"
    except subprocess.TimeoutExpired as error:
        # subprocess.run kills and reaps its direct child. The native sampling
        # tool additionally owns an advertised timelimit; no PID/PGID kill.
        code, stdout, stderr = None, error.stdout or b"", error.stderr or b""
        status = "TIMED_OUT"
    (out / (label + ".stdout")).write_bytes(stdout)
    (out / (label + ".stderr")).write_bytes(stderr)
    record(out / (label + ".json"), dict(argv=argv, status=status, exit_code=code,
           started_epoch=started, finished_epoch=time.time(), direct_child_reaped=True))
    return code, stdout, stderr


def source_check():
    # Only the observer and its isolated workflow differ from independently
    # accepted main. Shipping Kotlin/Swift, all original tests and build pins
    # are unchanged. No historical Debug product substitution.
    changed = common.git(ROOT, "diff", "--name-only", BASE, "HEAD").splitlines()
    if not changed or any(not (p.startswith("scripts/verification/ios-kbd-preexec/") or
            p == ".github/workflows/ios-kbd-preexec.yml") for p in changed):
        raise RuntimeError("Unreviewed app/test/build input change")
    if common.git(ROOT, "status", "--porcelain").strip():
        raise RuntimeError("Dirty diagnostic source")


def main():
    source_check()
    root, prefix, task = common.context()
    if root != ROOT or task["GITHUB_JOB"] != "ios":
        raise RuntimeError("Observer requires actual ios ownership job")
    owned = apple.cycle_prefix(prefix, "apple-ui")
    claim = apple.validate_claim(ROOT, owned, task, "apple-ui", apple.configuration(ROOT))
    plan = sim.read_record(sim.paths(owned)[0])
    created = sim.read_record(sim.paths(owned)[1])
    device = sim.owned_device(plan, created, sim.devices(sim.SimctlBackend().inventory()))
    if device is None or device["state"] != "Shutdown" or plan["task"] != task or plan["nonce"] != claim["nonce"]:
        raise RuntimeError("Fresh task-journalled Shutdown simulator required")
    if (plan["runtime"], plan["device_type"]) != (
            "com.apple.CoreSimulator.SimRuntime.iOS-26-2",
            "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"):
        raise RuntimeError("Exact original runtime/device required; no fallback")
    out = ROOT / "build/ci-evidence/kbd-preexec"
    out.mkdir(parents=True, exist_ok=False)
    record(out / "source.json", dict(source=apple.source(ROOT), task=task,
           base=BASE, simulator=device["udid"], runtime=plan["runtime"],
           device_type=plan["device_type"], app_changed=False, assertions_changed=False,
           physical_verified=False, diagnostic_neutrality_verified=False))
    udid = device["udid"]
    not_before = time.time()
    for label, args in (("boot", ["boot", udid]), ("bootstatus", ["bootstatus", udid, "-b"])):
        code, _, _ = command(["/usr/bin/xcrun", "simctl", *args], out, label, 180)
        if code != 0:
            raise RuntimeError("Owned simulator " + label + " failed")
    code, stdout, stderr = command(["/usr/bin/xcrun", "simctl", "spawn", udid,
                                   "launchctl", "managerpid"], out, "managerpid", 15)
    value = (stdout + stderr).strip()
    if code != 0 or not re.fullmatch(rb"[1-9][0-9]*", value):
        raise RuntimeError("No exact native simulator manager PID; no guessed fallback")
    backend = DarwinWorkerBackend()
    launcher = backend.read(int(value))
    if launcher is None or launcher.started[0] < int(not_before) - 1:
        raise RuntimeError("Simulator manager predates fresh owned boot")
    path = Path(launcher.command)
    if not str(path).startswith("/Library/Developer/CoreSimulator/Volumes/") or not str(path).endswith("/RuntimeRoot/sbin/launchd_sim"):
        raise RuntimeError("Manager PID is not the runtime's launchd_sim")
    runtime_root = path.parent.parent
    record(out / "launcher.json", dict(pid=launcher.pid, started=launcher.started,
           executable=launcher.command, uid=launcher.uid, boot_not_before=not_before))
    # Host log stream: original launchd_sim events were in the host archive.
    # Predicate excludes application/player text and foreign launchd instances.
    predicate = f'processID == {launcher.pid} AND subsystem CONTAINS "com.apple.TextInput.kbd"'
    argv = ["/usr/bin/log", "stream", "--style", "ndjson", "--level", "debug", "--predicate", predicate]
    pending, seen, rows, capture, errors = {}, set(), 0, None, []
    xcode = ["/usr/bin/xcodebuild", "-project", "iosApp/iosApp.xcodeproj", "-scheme", "iosApp",
             "-configuration", "Debug", "-sdk", "iphonesimulator", "-destination", f"platform=iOS Simulator,id={udid}",
             "-parallel-testing-enabled", "NO", "-maximum-concurrent-test-simulator-destinations", "1",
             "-destination-timeout", "120", "-derivedDataPath", "build/xcode-derived-data",
             "-resultBundlePath", "build/ci-evidence/ios-ui-tests.xcresult",
             *("-only-testing:" + test for test in TESTS), "test"]
    record(out / "commands.json", dict(log_stream=argv, xcode=xcode,
           sampling="At most one own-runtime proxy still pre-exec after five seconds; no warmup/retry"))
    with (out / "launchd.ndjson").open("xb") as log, (out / "launchd.stderr").open("xb") as err, \
            (out / "xcode-ui-test.log").open("xb") as testlog:
        stream = subprocess.Popen(argv, stdout=log, stderr=err)
        test = None
        try:
            test = subprocess.Popen(xcode, stdout=testlog, stderr=subprocess.STDOUT, cwd=ROOT)
            deadline = time.monotonic() + 75 * 60
            with (out / "launchd.ndjson").open("rb") as reader:
                while test.poll() is None:
                    if stream.poll() is not None or (out / "launchd.ndjson").stat().st_size > MAX_LOG or time.monotonic() > deadline:
                        raise RuntimeError("Observer stopped, exceeded bound, or test process timed out")
                    while True:
                        offset = reader.tell()
                        line = reader.readline(MAX_LOG + 1)
                        if not line.endswith(b"\n"):
                            reader.seek(offset)
                            break
                        if len(line) > MAX_LOG or reader.tell() > MAX_LOG:
                            raise RuntimeError("Native log byte ceiling exceeded")
                        if not line.startswith(b"{"):
                            continue  # Raw native banner remains retained, not a JSON event.
                        rows += 1
                        if rows > 10_000:
                            raise RuntimeError("Native event ceiling exceeded")
                        event = target_event(json.loads(line), launcher.pid, launcher.uid)
                        if event:
                            pid, state = event
                            if state == "spawn":
                                seen.add(pid)
                                if len(seen) > 8:
                                    raise RuntimeError("Unexpected keyboard generation count")
                                pending[pid] = True
                            else:
                                pending.pop(pid, None)
                    if capture is None:
                        for pid in pending:
                            try:
                                basic = backend._basic(pid)
                                if basic is None or basic.start_sec + basic.start_usec / 1_000_000 > time.time() - DELAY:
                                    continue
                                identity = backend.read(pid)
                                if not eligible(identity, basic, launcher, runtime_root, not_before, time.time()):
                                    continue
                                if backend.read(launcher.pid) != launcher or backend.read(pid) != identity:
                                    raise RuntimeError("Owned launcher/target identity changed before capture")
                                # Installed spindump manual accepted in41420769370.
                                # Raw-only target sampling avoids sample's observed
                                # symbol-processing hang. No repeated/global profile.
                                sample = ["/usr/bin/sudo", "-n", "/usr/sbin/spindump", str(pid), "1", "10",
                                          "-onlyTarget", "-noText", "-noProcessingWhileSampling",
                                          "-timelimit", "8", "-o", str(out / "proxy.raw.spindump")]
                                if not Path(sample[2]).is_file():
                                    raise RuntimeError("Installed spindump executable unavailable")
                                record(out / "target-before.json", dict(pid=pid, started=identity.started,
                                       executable=identity.command, parent=launcher.pid, observed_epoch=time.time()))
                                code, _, _ = command(sample, out, "spindump", 12)
                                later = backend.read(pid)
                                capture = dict(pid=pid, exit_code=code, after_executable=None if later is None else later.command,
                                               after_started=None if later is None else later.started,
                                               causal_fix=False, neutrality=False)
                                if code != 0 or not (out / "proxy.raw.spindump").is_file():
                                    errors.append("Target-only raw stack not successfully captured; retain exact native outcome")
                            except (OSError, RuntimeError) as failure:
                                # An attach/capability failure must not abort or
                                # relaunch the unchanged application tests.
                                errors.append(str(failure)[:512])
                                capture = dict(pid=pid, status="OBSERVATION_FAILED", causal_fix=False)
                            break
                    time.sleep(0.25)
            code = test.wait()
            if not exact_roster((out / "xcode-ui-test.log").read_text()):
                errors.append("Exact two unchanged test starts/passes not satisfied; no empty-suite qualification")
        finally:
            # Only direct Popen children; never a foreign numeric PID/PGID.
            for child in (test, stream):
                if child is not None and child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait()
            record(out / "observer.json", dict(native_rows=rows, keyboard_pids=sorted(seen),
                   capture=capture, errors=errors, log_exit=stream.returncode,
                   test_exit=None if test is None else test.returncode, direct_children_reaped=True,
                   result="STACK_CAPTURED_NOT_CAUSAL_QUALIFICATION" if capture and not errors else "NO_ACCEPTED_STACK",
                   app_tests_qualified=False, physical_verified=False))
    if code != 0 or errors:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
