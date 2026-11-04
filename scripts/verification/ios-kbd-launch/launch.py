"""One observed native service-print wait, not an app/input qualification.

The previous live log predicate produced no events. Select the actual service
using native BSD identity + bounded argv, not an assumed log-stream contract.
Never activate a service, signal its PID, extend the20s query, or warm up a test.
"""
import ctypes
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import plistlib
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).parent))
from scripts.ci import apple_verification_hygiene as apple, verification_hygiene as common, owned_ci_simulator as sim
from scripts.ci.darwin_worker_identity import DarwinWorkerBackend
import private_profiler

LABEL = "com.apple.TextInput.kbd"
# Exact forwarded executable observed in41901582085, not a prefix/name guess.
CORE_SIMCTL = "/Library/Developer/PrivateFrameworks/CoreSimulator.framework/Versions/A/Resources/bin/simctl"
SDK_STUB_SHA256 = "8e4ed565409890f1fd518099f98bb6ccac7419605574d802ac7356fd34f63a5b"


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


def attested_dispatchers(out):
    """Admit the observed installed forwarding binary only after Apple verification.

    The selected Xcode simctl stub and native CoreSimulator simctl are distinct
    files in41901582085. Resolve neither by basename nor a framework-wide grant.
    """
    native = str(Path(CORE_SIMCTL).resolve(strict=True))
    code, _ = query(out, "verify-native-dispatcher", ["/usr/bin/codesign", "--verify", "--strict",
                    "-R", "=anchor apple", native], 10)
    if code != 0:
        raise RuntimeError("Observed native simctl did not pass Apple signature verification; no profile grant")
    tools = tuple(str(Path(tool).resolve(strict=True)) for tool in (
        "/usr/bin/xcrun", os.environ["DEVELOPER_DIR"] + "/usr/bin/simctl", native))
    save(out, "trusted-dispatcher-tools", dict(executables=tools, matching="samefile",
         toolchain=os.environ["DEVELOPER_DIR"], native_signature_requirement="anchor apple",
         files=[dict(path=tool, bytes=Path(tool).stat().st_size,
                     sha256=hashlib.sha256(Path(tool).read_bytes()).hexdigest()) for tool in tools]))
    stub = Path(tools[1]).read_bytes()
    # Only this exact observed public SDK stub may be exported, never arbitrary
    # executable/source or process-environment content.
    if hashlib.sha256(stub).hexdigest() == SDK_STUB_SHA256:
        (out / "observed-simctl-stub.sh").write_bytes(stub)
    return tools


def initialized_native_query_tool(out, tools, observed_version):
    # The actual hash-pinned SDK shim always execs this forwarder after its
    # first-launch check, even if installed CoreSimulator is newer. Equality
    # is not established as an Apple compatibility requirement.
    if hashlib.sha256(Path(tools[1]).read_bytes()).hexdigest() != SDK_STUB_SHA256:
        raise RuntimeError("Selected SDK shim changed; no direct query grant")
    code = None
    strategy = "matching-version-direct-exec-declared-by-sdk-shim"
    if observed_version != "1051.17.8":
        strategy = "explicit-selected-xcode-initialization-readback"
        code, _ = query(out, "xcode-first-launch-status", [
            os.environ["DEVELOPER_DIR"] + "/usr/bin/xcodebuild", "-checkFirstLaunchStatus"], 20)
        if code is None:
            raise RuntimeError("Selected Xcode initialization status is UNKNOWN after timeout; no automatic repair")
        if code != 0:
            raise RuntimeError("Selected Xcode is not initialized; no native query or automatic repair")
    save(out, "native-query-grant", dict(executable=tools[2],
         selected_sdk_stub_sha256=SDK_STUB_SHA256, initialization_exit_code=code,
         observed_core_version=observed_version, strategy=strategy,
         provenance="exact forwarder in observed SDK shim plus prior Apple signature verification",
         version_equality_required=False, mutating_first_launch_command_executed=False))
    return tools[2]


def is_declared_script(info, original, path, args, argv, tools, python_pid):
    if not is_direct_dispatcher(info, original, path, ("/bin/bash", "/bin/sh"), python_pid):
        return False
    expected = [os.fsencode(path), os.fsencode(tools[1]), *(os.fsencode(arg) for arg in argv[2:])]
    return args == expected


def select_script_query_child(children, direct, argv, tools):
    """Only a fresh immediate child executing the exact declared native query."""
    parent, _ = direct
    since = parent.start_sec + parent.start_usec / 1_000_000
    matches = []
    for info, path, child_args in children:
        try:
            same_native = os.path.samefile(path, tools[2])
        except OSError:
            same_native = False
        if (own_child(info, parent, since) and same_native and
                child_args and child_args[0] in [os.fsencode(tool) for tool in tools[1:]] and
                child_args[1:] == [os.fsencode(arg) for arg in argv[2:]]):
            matches.append((info, path))
    if len(matches) > 1:
        raise RuntimeError("Ambiguous declared native query children; no profile grant")
    if matches:
        return matches[0], "owned-native-query-child-not-keyboard"
    return direct, "owned-query-script-interpreter-not-keyboard"


def script_children(backend, parent, since, deadline):
    children = []
    for pid in backend._pids():
        if time.monotonic() >= deadline:
            break
        info = backend._basic(pid)
        if not own_child(info, parent, since):
            continue
        try:
            path = backend._path(pid)
            args = native_args(backend, pid)
        except RuntimeError:
            if backend._basic(pid) is None:
                continue  # A proved exit is not a permission or argv failure.
            raise
        if basic_key(backend._basic(pid)) == basic_key(info):
            children.append((info, path, args))
    return children


def profile_command(out, argv, *, deadline):
    """File-backed streams cannot wait on a descendant's pipe EOF.

    Cancel only our direct profiler caller with TERM first. Never signal the
    service/sampled PID or a process group.
    Direct reaping alone is not a claim about privileged descendant termination.
    """
    # Capture one second with the independently admitted target-only spindump;
    # its native5s time limit fits within the retained8s caller ceiling.
    # Allow1s for TERM/reaping inside the query's
    # original deadline. Do not start a capture that cannot fit that budget.
    timeout = min(8, deadline - time.monotonic() - 1.25)
    if timeout < 5.5:
        save(out, "query-sample", dict(argv=argv, status="NOT_STARTED_INSUFFICIENT_QUERY_BUDGET",
             exit_code=None, original_deadline_seconds=20, sampled_pid_signalled=False))
        return None
    started, code, outcome = time.time(), None, "INCOMPLETE"
    profile_command.private_capture = None
    private = Path(tempfile.mkdtemp(prefix="parlor-native-private-", dir=os.environ.get("RUNNER_TEMP"))).resolve(strict=True)
    # The known-working -noFile mode writes opaque system metadata to stdout.
    # Never put either unfiltered stream inside the uploaded evidence tree.
    with (private / "record.opaque-output").open("x+b") as stdout, (private / "record.stderr").open("x+b") as stderr:
        child = subprocess.Popen(argv, stdout=stdout, stderr=stderr)
        try:
            code = child.wait(timeout=timeout)
            outcome = "EXITED"
        except subprocess.TimeoutExpired:
            outcome = "TIMED_OUT"
            child.terminate()
            try:
                child.wait(timeout=max(0, min(1, deadline - time.monotonic())))
            except subprocess.TimeoutExpired:
                child.kill()
                try:
                    child.wait(timeout=max(0, min(1, deadline - time.monotonic())))
                except subprocess.TimeoutExpired:
                    outcome = "TIMED_OUT_DIRECT_REAP_INCOMPLETE"
        finally:
            stdout.flush()
            raw = os.pread(stdout.fileno(), private_profiler.MAX_RAW + 1, 0)
            error = os.pread(stderr.fileno(), private_profiler.MAX_TEXT + 1, 0)
            bounded = 0 < len(raw) <= private_profiler.MAX_RAW
            safe = private_profiler.public_log(raw) if bounded else b""
            if len(error) <= private_profiler.MAX_TEXT:
                safe += private_profiler.public_log(error)
            (out / "query-profiler.log").write_bytes(safe)
            save(out, "query-private-capture", dict(
                observed_file_bytes=(private / "record.opaque-output").stat().st_size,
                captured_prefix_bytes=len(raw),
                captured_prefix_sha256=hashlib.sha256(raw).hexdigest() if bounded else None,
                bounded=bounded, raw_metadata_uploaded=False, unfiltered_stderr_uploaded=False,
                native_mode="REUSED_41423553336_PRIVATE_STDOUT_NOFILE",
                independent_reference="c064c2bfa7db8f63207908d287a2de0d6389e197"))
            # Internal-only locator, never serialized or published.
            profile_command.private_capture = private if code == 0 and bounded else None
            save(out, "query-sample", dict(argv=argv, exit_code=code, status=outcome,
                 start_epoch=started, end_epoch=time.time(), caller_pid=child.pid,
                 caller_timeout_seconds=timeout, original_deadline_seconds=20,
                 direct_child_reaped=child.poll() is not None,
                 descendant_termination="NOT_INDEPENDENTLY_VERIFIED", sampled_pid_signalled=False))
    return code


def observe_print(out, argv, backend, parent, runtime_root, *, label="service-before", capture_state, trusted_tools):
    started, deadline, capture = time.time(), time.monotonic() + 20, None
    with tempfile.TemporaryFile() as stdout_file, tempfile.TemporaryFile() as stderr_file:
        child = subprocess.Popen(argv, stdout=stdout_file, stderr=stderr_file)
        original = backend._basic(child.pid)
        stdout, stderr, code, status = b"", b"", None, "INCOMPLETE"
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    status = "TIMED_OUT"
                    break
                try:
                    child.wait(timeout=min(0.25, remaining))
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
                if direct_info is None:
                    continue  # Exited during selection; collect its actual result.
                try:
                    direct_path = backend._path(child.pid)
                except RuntimeError:
                    if backend._basic(child.pid) is None:
                        continue
                    raise
                recognized = is_direct_dispatcher(direct_info, original, direct_path, trusted_tools, os.getpid())
                script = False
                direct_args = []
                if not recognized:
                    try:
                        direct_args = native_args(backend, child.pid)
                    except RuntimeError:
                        if backend._basic(child.pid) is None:
                            continue
                        raise
                    script = is_declared_script(direct_info, original, direct_path, direct_args, argv, trusted_tools, os.getpid())
                    recognized = script
                if basic_key(backend._basic(child.pid)) != basic_key(direct_info):
                    continue  # No grant from an observation spanning exit/exec.
                dispatcher_check = dict(pid=child.pid, native_executable=direct_path,
                     declared_script=script,
                     original_identity=basic_key(original)[:-1] if original is not None else None,
                     current_identity=basic_key(direct_info)[:-1] if direct_info is not None else None,
                     trusted_executables=trusted_tools, recognized=recognized)
                if not recognized:
                    save(out, "dispatcher-check", dispatcher_check)
                    raise RuntimeError("Unrecognized direct native query dispatcher; recorded identity is not a profile grant")
                direct = (direct_info, direct_path)
                if script:
                    children = script_children(backend, direct_info, started - 0.1, deadline)
                    if basic_key(backend._basic(child.pid)) != basic_key(direct_info):
                        continue  # Collect exited query; never sample a stale PID.
                    (target, path), scope = select_script_query_child(children, direct, argv, trusted_tools)
                elif parent is None:
                    (target, path), scope = direct, "direct-query-dispatcher-not-keyboard"
                else:
                    (target, path), scope = select_query_target(candidates, direct, parent, started - 0.1, runtime_root)
                if basic_key(backend._basic(target.pid)) != basic_key(target):
                    continue  # Never grant capture from a stale selected lifetime.
                save(out, "dispatcher-check", dispatcher_check)
                save(out, "target-before", dict(pid=target.pid, uid=target.uid, parent=target.ppid,
                     start=[target.start_sec,target.start_usec], executable=path, scope=scope,
                     observed_epoch=time.time(), query_argv=argv, original_deadline_seconds=20))
                sample = private_profiler.capture_command(target.pid, None)
                capture_state["attempted"] = True  # One profile attempt for the whole run, including failures.
                sample_code = profile_command(out, sample, deadline=deadline)
                later = backend._basic(target.pid)
                capture = dict(pid=target.pid, scope=scope, exit_code=sample_code,
                               after_start=None if later is None else [later.start_sec,later.start_usec],
                               causal_fix=False, original_deadline_seconds=20)
        finally:
            if child.poll() is None:
                child.kill()
            try:
                child.wait(timeout=1)
            except subprocess.TimeoutExpired:
                pass  # Retain an incomplete direct reap; never block on pipe EOF.
            # pread does not alter a writer's shared offset and never waits for EOF.
            stdout = os.pread(stdout_file.fileno(), 1024 * 1024 + 1, 0)
            stderr = os.pread(stderr_file.fileno(), 1024 * 1024 + 1, 0)
            oversized = len(stdout) > 1024 * 1024 or len(stderr) > 1024 * 1024
            if oversized:
                stdout, stderr, status = b"", b"", "INCOMPLETE_OVERSIZED_OUTPUT"
            (out / (label + ".stderr")).write_bytes(stderr)
            if label == "managerpid":
                (out / (label + ".stdout")).write_bytes(stdout)
            save(out, label, dict(argv=argv, status=status, exit_code=code,
                 start_epoch=started, end_epoch=time.time(), stdout_sha256=hashlib.sha256(stdout).hexdigest(),
                 direct_child_reaped=child.poll() is not None, capture=capture, service_activation=False,
                 stream_transport="PRIVATE_TEMPORARY_FILES_NOT_PIPE_EOF", cleanup_wait_limit_seconds=1,
                 descendant_termination="NOT_INDEPENDENTLY_VERIFIED"))
            private = getattr(profile_command, "private_capture", None)
            if isinstance(private, Path) and capture is not None:
                try:
                    decoded = private_profiler.decode(private, out, target.pid, path)
                    save(out, "query-native-frames", decoded)
                except (OSError, ValueError, subprocess.TimeoutExpired):
                    save(out, "query-native-frames", dict(result="OFFLINE_DECODE_REJECTED",
                         raw_metadata_uploaded=False, original_input_causality=False))
                finally:
                    profile_command.private_capture = None
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
         runner_label=os.environ["PARLOR_NATIVE_RUNNER_LABEL"], macos=platform.mac_ver()[0],
         architecture=platform.machine(),
         runtime=plan["runtime"], device_type=plan["device_type"], app_built=False,
         input_executed=False, application_qualified=False, physical_verified=False))
    udid, boot_time = device["udid"], time.time()
    for label, args in (("boot", ["boot", udid]), ("bootstatus", ["bootstatus", udid, "-b"])):
        code, _ = query(out, label, ["/usr/bin/xcrun", "simctl", *args], 180)
        if code != 0:
            raise RuntimeError("Owned simulator boot failed")
    trusted_tools = attested_dispatchers(out)
    # Public installed metadata only; the stub invokes -runFirstLaunch on a
    # version mismatch. Do not run that mutating operation or infer a mismatch.
    metadata = Path(trusted_tools[2]).parents[1] / "Info.plist"
    if not 0 < metadata.stat().st_size <= 128 * 1024:
        raise RuntimeError("Installed CoreSimulator metadata exceeds its bound")
    version = plistlib.loads(metadata.read_bytes()).get("CFBundleVersion")
    if not isinstance(version, str) or not re.fullmatch(r"[0-9]{1,4}(?:\.[0-9]{1,3}){0,3}", version):
        raise RuntimeError("Installed CoreSimulator version is not a bounded public version")
    save(out, "installed-coresimulator-version", dict(path=str(metadata),
         expected_by_observed_stub="1051.17.8", observed=version,
         matches_observed_stub=version == "1051.17.8", explicit_first_launch_command_executed=False,
         prior_shim_branch_execution="NOT_OBSERVED"))
    native_query_tool = initialized_native_query_tool(out, trusted_tools, version)
    backend, capture_state = DarwinWorkerBackend(), {"attempted": False}
    code, raw = observe_print(out, [native_query_tool, "spawn", udid, "launchctl", "managerpid"],
                              backend, None, None, label="managerpid", capture_state=capture_state, trusted_tools=trusted_tools)
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
    control = [native_query_tool, "spawn", udid, "launchctl"]
    # Run the exact observed failing read once, unchanged20s platform deadline.
    # Do not equate a timeout with a missing service or issue a kickstart.
    code, raw = observe_print(out, [*control, "print", target], backend, parent, Path(path).parent.parent,
                              capture_state=capture_state, trusted_tools=trusted_tools)
    if code is None:
        raise RuntimeError("Native service print timed out; service existence and app causality remain unknown")
    if code != 0:
        raise RuntimeError("Native service print exited unsuccessfully; no alternate domain or service activation")
    if basic_key(backend._basic(parent.pid)) != parent_key:
        raise RuntimeError("Owned Simulator manager generation changed during service observation")
    fields = service_fields(raw)
    save(out, "service-fields-before", fields)
    save(out, "result", dict(result="NATIVE_PRINT_RETURNED_NOT_APP_QUALIFICATION", fields=fields,
         app_qualified=False, physical_verified=False, neutrality_verified=False, service_activation=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
