"""One owned Simulator keyboard-service launch, not an app/input qualification.

The previous live log predicate produced no events. Select the actual service
using native BSD identity + bounded argv, not an assumed log-stream contract.
Never restart an already running service, signal its PID, or warm up a test.
"""
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple, verification_hygiene as common, owned_ci_simulator as sim
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend

LABEL = "com.apple.TextInput.kbd"


def argv_only(raw):
    """Public KERN_PROCARGS2 layout. Never return, save or print environment."""
    if not 5 <= len(raw) <= 1024 * 1024 or not raw.endswith(b"\0"):
        raise ValueError("Invalid bounded native argument record")
    count = struct.unpack_from("=i", raw)[0]
    if not 1 <= count <= 4096:
        raise ValueError("Invalid argument count")
    offset = raw.index(b"\0", 4) + 1
    while offset < len(raw) and raw[offset] == 0:
        offset += 1
    args = []
    for _ in range(count):
        end = raw.index(b"\0", offset)
        args.append(raw[offset:end])
        offset = end + 1
    return args


def basic_key(info):
    return None if info is None else (info.pid, info.uid, info.ppid, info.start_sec, info.start_usec, bytes(info.comm))


def own_child(info, parent, since):
    return (info is not None and info.ppid == parent.pid and info.uid == parent.uid and
            info.start_sec + info.start_usec / 1_000_000 >= since)


def service_fields(raw):
    """Only public program/PID/state fields; no environment or full config export."""
    text = raw.decode("utf-8")
    if len(raw) > 1024 * 1024:
        raise ValueError("Oversized service configuration")
    result = {}
    for key in ("program", "pid", "state"):
        values = re.findall(r"^\s*" + key + r" = ([^\n]+)$", text, re.MULTILINE)
        if len(values) > 1:
            raise ValueError("Ambiguous service field")
        if values:
            result[key] = values[0].strip()
    if "pid" in result and not re.fullmatch(r"[1-9][0-9]*", result["pid"]):
        raise ValueError("Invalid service PID")
    return result


def save(out, name, value):
    common.write_new(out / (name + ".json"), value)


def query(out, label, argv, timeout=20, retain=True):
    start = time.time()
    try:
        p = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=False)
        code, stdout, stderr = p.returncode, p.stdout, p.stderr
    except subprocess.TimeoutExpired as error:
        code, stdout, stderr = None, error.stdout or b"", error.stderr or b""
    if retain:
        (out / (label + ".stdout")).write_bytes(stdout)
    (out / (label + ".stderr")).write_bytes(stderr)
    save(out, label, dict(argv=argv, exit_code=code, start_epoch=start, end_epoch=time.time(),
                         stdout_sha256=hashlib.sha256(stdout).hexdigest(), direct_child_reaped=True))
    return code, stdout


def native_args(backend, pid):
    mib = (ctypes.c_int * 3)(1, 49, pid)
    buf = ctypes.create_string_buffer(backend.argument_capacity)
    size = ctypes.c_size_t(len(buf))
    if backend.lib.sysctl(mib, 3, buf, ctypes.byref(size), None, 0) != 0:
        raise RuntimeError("Native proxy argv unavailable")
    if not 0 < size.value <= len(buf):
        raise RuntimeError("Native argv capacity changed")
    return argv_only(buf.raw[:size.value])


def main():
    root, prefix, task = common.context()
    if root != ROOT or task["GITHUB_JOB"] != "ios" or common.git(ROOT, "status", "--porcelain").strip():
        raise RuntimeError("Clean actual ios ownership context required")
    owned = apple.cycle_prefix(prefix, "apple-ui")
    claim = apple.validate_claim(ROOT, owned, task, "apple-ui", apple.configuration(ROOT))
    plan, created = (sim.read_record(path) for path in sim.paths(owned))
    device = sim.owned_device(plan, created, sim.devices(sim.SimctlBackend().inventory()))
    if device is None or device["state"] != "Shutdown" or plan["nonce"] != claim["nonce"]:
        raise RuntimeError("Fresh owned Shutdown simulator required")
    if (plan["runtime"], plan["device_type"]) != (
            "com.apple.CoreSimulator.SimRuntime.iOS-26-2", "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"):
        raise RuntimeError("Exact original runtime and device type required")
    out = ROOT / "build/ci-evidence/kbd-launch"
    out.mkdir(parents=True, exist_ok=False)
    save(out, "source", dict(source=apple.source(ROOT), task=task, simulator=device["udid"],
         runtime=plan["runtime"], device_type=plan["device_type"], app_built=False,
         input_executed=False, application_qualified=False, physical_verified=False))
    udid, boot_time = device["udid"], time.time()
    for label, args in (("boot", ["boot", udid]), ("bootstatus", ["bootstatus", udid, "-b"])):
        code, _ = query(out, label, ["/usr/bin/xcrun", "simctl", *args], 180)
        if code != 0:
            raise RuntimeError("Owned simulator boot failed")
    code, raw = query(out, "managerpid", ["/usr/bin/xcrun", "simctl", "spawn", udid, "launchctl", "managerpid"])
    if code != 0 or not re.fullmatch(rb"[1-9][0-9]*", raw.strip()):
        raise RuntimeError("Exact Simulator manager PID unavailable")
    backend = DarwinWorkerBackend()
    parent = backend._basic(int(raw))
    path = backend._path(int(raw))
    if (parent is None or parent.uid != os.getuid() or parent.start_sec < int(boot_time) - 1 or
            not path.startswith("/Library/Developer/CoreSimulator/Volumes/") or not path.endswith("/RuntimeRoot/sbin/launchd_sim")):
        raise RuntimeError("Manager is not the fresh owned native launchd_sim")
    parent_key = basic_key(parent)
    save(out, "parent", dict(pid=parent.pid, uid=parent.uid, start=[parent.start_sec,parent.start_usec], path=path))
    target = f"user/{os.getuid()}/{LABEL}"
    control = ["/usr/bin/xcrun", "simctl", "spawn", udid, "launchctl"]
    code, raw = query(out, "service-before", [*control, "print", target], retain=False)
    if code != 0:
        raise RuntimeError("Actual keyboard service not available; no guessed alternate domain")
    fields = service_fields(raw)
    save(out, "service-fields-before", fields)
    if "pid" in fields:
        save(out, "result", dict(result="ALREADY_RUNNING_NOT_RESTARTED", app_qualified=False, stack=None))
        return 0
    if Path(fields.get("program", "")).name != "kbd":
        raise RuntimeError("Printed service does not identify the keyboard program")
    start, seen, capture = time.time(), set(), None
    with (out / "kickstart.stdout").open("xb") as stdout, (out / "kickstart.stderr").open("xb") as stderr:
        # Documented service-only activation on this fresh isolated profile.
        # No -k restart, unrelated service, app launch or input warmup.
        kick = subprocess.Popen([*control, "kickstart", target], stdout=stdout, stderr=stderr)
        try:
            deadline = time.monotonic() + 35
            while time.monotonic() < deadline:
                if basic_key(backend._basic(parent.pid)) != parent_key:
                    raise RuntimeError("Native owned manager generation changed")
                for pid in backend._pids():
                    info = backend._basic(pid)
                    if not own_child(info, parent, start - 0.1):
                        continue
                    try:
                        executable = backend._path(pid)
                    except RuntimeError:
                        if backend._basic(pid) is None:
                            continue
                        raise
                    if Path(executable).name != "xpcproxy_sim":
                        continue
                    try:
                        args = native_args(backend, pid)
                    except (RuntimeError, ValueError):
                        if backend._basic(pid) is None:
                            continue
                        raise
                    if basic_key(backend._basic(pid)) != basic_key(info):
                        continue
                    if len(args) < 2 or args[1] != LABEL.encode():
                        continue
                    seen.add(pid)
                    if len(seen) > 8:
                        raise RuntimeError("Keyboard generation ceiling exceeded")
                    age = time.time() - (info.start_sec + info.start_usec / 1_000_000)
                    if age < 5 or capture is not None:
                        continue
                    save(out, "target-before", dict(pid=pid, uid=info.uid, parent=info.ppid,
                         start=[info.start_sec, info.start_usec], executable=executable,
                         argv_label=LABEL, age_seconds=age, observed_epoch=time.time()))
                    sample = ["/usr/bin/sudo", "-n", "/usr/sbin/spindump", str(pid), "1", "10",
                              "-onlyTarget", "-noText", "-noProcessingWhileSampling", "-timelimit", "8",
                              "-o", str(out / "proxy.raw.spindump")]
                    code, _ = query(out, "spindump", sample, 12)
                    later = backend._basic(pid)
                    capture = dict(pid=pid, exit_code=code, after_start=None if later is None else [later.start_sec,later.start_usec],
                                   after_executable=None if later is None else backend._path(pid), causal_fix=False)
                if kick.poll() is not None and time.time() - start >= 15:
                    break
                time.sleep(0.25)
        finally:
            if kick.poll() is None:
                kick.terminate()
                try:
                    kick.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    kick.kill();kick.wait()
            save(out, "activation", dict(argv=[*control,"kickstart",target], exit_code=kick.returncode,
                 start_epoch=start, end_epoch=time.time(), direct_child_reaped=True,
                 keyboard_proxies=sorted(seen), capture=capture))
    code, raw = query(out, "service-after", [*control,"print",target], retain=False)
    if code != 0:
        raise RuntimeError("Native final service state unavailable")
    fields = service_fields(raw)
    save(out, "service-fields-after", fields)
    good_stack = capture is not None and capture["exit_code"] == 0 and (out/"proxy.raw.spindump").is_file()
    save(out, "result", dict(result="SERVICE_ONLY_STACK_NOT_APP_CAUSALITY" if good_stack else "NO_ACCEPTED_STACK_SERVICE_ONLY",
         stack=capture, app_qualified=False, physical_verified=False, neutrality_verified=False))
    return 0 if kick.returncode == 0 and "pid" in fields and (capture is None or good_stack) else 1


if __name__ == "__main__":
    raise SystemExit(main())
