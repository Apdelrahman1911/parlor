"""One observed native service-print wait, not an app/input qualification.

The previous live log predicate produced no events. Select the actual service
using native BSD identity + bounded argv, not an assumed log-stream contract.
Never activate a service, signal its PID, extend the20s query, or warm up a test.
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


def select_query_target(children, direct, parent, since, runtime_root):
    matches = [(info, path) for info, path in children if own_child(info, parent, since) and
               Path(path) in (runtime_root / "bin/launchctl", runtime_root / "usr/bin/launchctl")]
    if len(matches) > 1:
        raise RuntimeError("Ambiguous native print command child; no profile grant")
    if matches:
        return matches[0], "owned-simulator-launchctl"
    if direct is None or direct[0].uid != parent.uid or direct[0].start_sec < int(since) - 1:
        raise RuntimeError("Direct query dispatcher lifetime unavailable")
    return direct, "direct-query-dispatcher-not-keyboard"


def is_direct_dispatcher(info, original, path, trusted_tools, python_pid):
    """Attest an exact child/executable, allowing xcrun's exec and symlinks.

    proc_pidpath may return the canonical executable rather than the path xcrun
    selected. Match file identity, never a basename, prefix or arbitrary path.
    """
    if (info is None or original is None or
            basic_key(info)[:-1] != basic_key(original)[:-1] or
            info.ppid != python_pid):
        return False
    try:
        return any(os.path.samefile(path, tool) for tool in trusted_tools)
    except OSError:
        return False


def observe_print(out, argv, backend, parent, runtime_root, *, label="service-before", capture_state):
    trusted_tools = tuple(str(Path(tool).resolve(strict=True)) for tool in (
        "/usr/bin/xcrun", os.environ["DEVELOPER_DIR"] + "/usr/bin/simctl"))
    if not (out / "trusted-dispatcher-tools.json").exists():
        save(out, "trusted-dispatcher-tools", dict(executables=trusted_tools,
             matching="samefile", toolchain=os.environ["DEVELOPER_DIR"]))
    started, deadline, capture = time.time(), time.monotonic() + 20, None
    child = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    original = backend._basic(child.pid)
    stdout, stderr, code, status = b"", b"", None, "INCOMPLETE"
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                child.kill()
                stdout, stderr = child.communicate()
                status = "TIMED_OUT"
                break
            try:
                stdout, stderr = child.communicate(timeout=min(0.25, remaining))
                code, status = child.returncode, "EXITED"
                break
            except subprocess.TimeoutExpired:
                pass
            if capture_state["attempted"] or time.time() - started < 5:
                continue
            candidates = []
            # A stalled managerpid cannot supply a Simulator parent. Profile
            # only our attested direct dispatcher, never guess a keyboard PID.
            if parent is not None:
                for pid in backend._pids():
                    info = backend._basic(pid)
                    if not own_child(info, parent, started - 0.1) or bytes(info.comm) != b"launchctl":
                        continue
                    try:
                        path = backend._path(pid)
                    except RuntimeError:
                        if backend._basic(pid) is None:
                            continue
                        raise
                    if basic_key(backend._basic(pid)) == basic_key(info):
                        candidates.append((info, path))
            direct_info = backend._basic(child.pid)
            direct_path = backend._path(child.pid)
            recognized = is_direct_dispatcher(direct_info, original, direct_path, trusted_tools, os.getpid())
            save(out, "dispatcher-check", dict(pid=child.pid, native_executable=direct_path,
                 original_identity=basic_key(original)[:-1] if original is not None else None,
                 current_identity=basic_key(direct_info)[:-1] if direct_info is not None else None,
                 trusted_executables=trusted_tools, recognized=recognized))
            if not recognized:
                raise RuntimeError("Unrecognized direct native query dispatcher; recorded identity is not a profile grant")
            direct = (direct_info, direct_path)
            if parent is None:
                (target, path), scope = direct, "direct-query-dispatcher-not-keyboard"
            else:
                (target, path), scope = select_query_target(candidates, direct, parent, started - 0.1, runtime_root)
            save(out, "target-before", dict(pid=target.pid, uid=target.uid, parent=target.ppid,
                 start=[target.start_sec,target.start_usec], executable=path, scope=scope,
                 observed_epoch=time.time(), query_argv=argv, original_deadline_seconds=20))
            sample = ["/usr/bin/sudo", "-n", "/usr/sbin/spindump", str(target.pid), "1", "10",
                      "-onlyTarget", "-noText", "-noProcessingWhileSampling", "-timelimit", "8",
                      "-o", str(out / "query.raw.spindump")]
            capture_state["attempted"] = True  # One profile attempt for the whole run, including failures.
            sample_code, _ = query(out, "spindump", sample, 12)
            later = backend._basic(target.pid)
            capture = dict(pid=target.pid, scope=scope, exit_code=sample_code,
                           after_start=None if later is None else [later.start_sec,later.start_usec],
                           causal_fix=False, original_deadline_seconds=20)
    finally:
        if child.poll() is None:
            child.kill()
            stdout, stderr = child.communicate()
        (out / (label + ".stderr")).write_bytes(stderr)
        if label == "managerpid":
            (out / (label + ".stdout")).write_bytes(stdout)
        save(out, label, dict(argv=argv, status=status, exit_code=code,
             start_epoch=started, end_epoch=time.time(), stdout_sha256=hashlib.sha256(stdout).hexdigest(),
             direct_child_reaped=True, capture=capture, service_activation=False))
    return code, stdout


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
    backend, capture_state = DarwinWorkerBackend(), {"attempted": False}
    code, raw = observe_print(out, ["/usr/bin/xcrun", "simctl", "spawn", udid, "launchctl", "managerpid"],
                              backend, None, None, label="managerpid", capture_state=capture_state)
    if code is None:
        raise RuntimeError("Native managerpid timed out; no keyboard/service identity or app causality established")
    if code != 0 or not re.fullmatch(rb"[1-9][0-9]*", raw.strip()):
        raise RuntimeError("Native managerpid exited without a valid exact Simulator PID")
    parent = backend._basic(int(raw))
    path = backend._path(int(raw))
    if (parent is None or parent.uid != os.getuid() or parent.start_sec < int(boot_time) - 1 or
            not path.startswith("/Library/Developer/CoreSimulator/Volumes/") or not path.endswith("/RuntimeRoot/sbin/launchd_sim")):
        raise RuntimeError("Manager is not the fresh owned native launchd_sim")
    parent_key = basic_key(parent)
    save(out, "parent", dict(pid=parent.pid, uid=parent.uid, start=[parent.start_sec,parent.start_usec], path=path))
    target = f"user/{os.getuid()}/{LABEL}"
    control = ["/usr/bin/xcrun", "simctl", "spawn", udid, "launchctl"]
    # Run the exact observed failing read once, unchanged20s platform deadline.
    # Do not equate a timeout with a missing service or issue a kickstart.
    code, raw = observe_print(out, [*control, "print", target], backend, parent, Path(path).parent.parent,
                              capture_state=capture_state)
    if code is None:
        raise RuntimeError("Native service print timed out; service existence and app causality remain unknown")
    if code != 0:
        raise RuntimeError("Native service print exited unsuccessfully; no alternate domain or service activation")
    fields = service_fields(raw)
    save(out, "service-fields-before", fields)
    save(out, "result", dict(result="NATIVE_PRINT_RETURNED_NOT_APP_QUALIFICATION", fields=fields,
         app_qualified=False, physical_verified=False, neutrality_verified=False, service_activation=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
