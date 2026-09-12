#!/usr/bin/env python3
"""One source-bound, non-app hosted process observation. Never an ownership fix.

No native work on import. Only the explicit reviewed dispatch may allocate one
simulator. Raw global argv, settings and command errors are never persisted.
"""
from __future__ import annotations

import ast
import json
import os
from pathlib import Path
import plistlib
import re
import selectors
import shutil
import signal
import stat
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.ci import native_continuation as native
from scripts.ci import owned_ci_simulator as simulator

SCOPE = "native-process-probe"
MAX_OUTPUT = 2 * 1024 * 1024
OBSERVATION_SECONDS = 300
ORIGINAL_PS_SECONDS = 15
PROTECTION_RUNTIME_LIST = ("/usr/bin/xcrun", "simctl", "list", "runtimes", "--json")
PROTECTION_RUNTIME_SECONDS = 90
PROTECTION_SPAWN_SECONDS = 40
PLATFORM_ENUMERATION_SECONDS = {
    ("/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"): 120,
    ("/usr/bin/xcrun", "simctl", "list", "devicetypes", "--json"): 120,
}
CONTROL_PATHS = (
    ".github/workflows/production-verification.yml", "scripts/ci/native_process_probe.py",
    "scripts/ci/native_process_metadata.py", "scripts/ci/native_continuation.py",
    "scripts/ci/darwin_worker_identity.py", "scripts/ci/owned_ci_simulator.py",
    "scripts/ci/verification_hygiene.py", "scripts/ci/NATIVE_CONTINUATION.md",
    "scripts/verification/ios-readiness/toolchain_profiles.py",
    "scripts/verification/ios-readiness/owned_lane.py", "gradlew",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
)
ORIGINAL_PS = ["ps", "-axo", "pid=,ppid=,pgid=,lstart=,command="]
METADATA_PS = ["ps", "-axo", "pid=,ppid=,pgid=,lstart=,ucomm="]
VM_FIELDS = frozenset(("Pages free", "Pages active", "Pages inactive", "Pages speculative",
    "Pages throttled", "Pages wired down", "Pages purgeable", "Pages occupied by compressor",
    "Pages stored in compressor", "Pageins", "Pageouts", "Swapins", "Swapouts"))


def controls(root=ROOT):
    rows = [dict(path=name, sha256=native.sha(native.file_bytes(root / name))) for name in sorted(CONTROL_PATHS)]
    return dict(files=rows, control_sha256=native.sha(json.dumps(rows, separators=(",", ":")).encode()))


def original_seam(root=ROOT):
    path = root / "scripts/verification/ios-readiness/owned_lane.py"
    raw = native.file_bytes(path)
    function = next(node for node in ast.parse(raw).body if isinstance(node, ast.FunctionDef) and node.name == "snapshot_processes")
    calls = [node for node in ast.walk(function) if isinstance(node, ast.Call) and
             isinstance(node.func, ast.Attribute) and isinstance(node.func.value, ast.Name) and
             node.func.value.id == "subprocess" and node.func.attr == "run"]
    native.require(len(calls) == 1 and ast.literal_eval(calls[0].args[0]) == ORIGINAL_PS and
        [ast.literal_eval(item.value) for item in calls[0].keywords if item.arg == "timeout"] == [ORIGINAL_PS_SECONDS],
        "original-ps-command-or-timeout-seam-differs")
    return dict(path=str(path.relative_to(root)), sha256=native.sha(raw), command=ORIGINAL_PS,
                timeout_seconds=ORIGINAL_PS_SECONDS, source_imported_or_modified=False)


def child_environment(inherited):
    # No wildcard, GitHub/Actions token, tracking token, Python path, DYLD hook,
    # JAVA_TOOL_OPTIONS, signing credential, or arbitrary Gradle option survives.
    allowed = ("HOME", "USER", "LOGNAME", "PATH", "TMPDIR", "DEVELOPER_DIR", "JAVA_HOME",
               "ANDROID_HOME", "ANDROID_SDK_ROOT")
    value = {key: inherited[key] for key in allowed if key in inherited}
    native.require(value.get("PATH") and value.get("HOME"), "missing-public-host-environment")
    value.update(LC_ALL="C", LANG="en_US.UTF-8", PYTHONDONTWRITEBYTECODE="1")
    return value


def executable_identity(path):
    path = Path(path)
    before = path.lstat()
    identity = lambda value: (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns,
                              value.st_mode, value.st_uid, value.st_nlink)
    native.require(path.resolve() == path and stat.S_ISREG(before.st_mode) and before.st_uid == 0 and
                   before.st_mode & stat.S_IXUSR and not before.st_mode & 0o022 and
                   0 < before.st_size <= 32 * 1024 * 1024, "unsafe-ps-executable")
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as stream:
        opened = os.fstat(stream.fileno())
        native.require(identity(opened) == identity(before), "ps-replaced-before-open")
        raw = stream.read(32 * 1024 * 1024 + 1)
        native.require(identity(os.fstat(stream.fileno())) == identity(opened), "ps-changed-during-read")
    native.require(identity(path.lstat()) == identity(before) and len(raw) == before.st_size,
                   "ps-executable-changed-during-read")
    return dict(resolved_path=str(path), sha256=native.sha(raw), device=before.st_dev, inode=before.st_ino,
                uid=before.st_uid, mode=before.st_mode, links=before.st_nlink,
                bytes=before.st_size, modified_ns=before.st_mtime_ns)


def ps_summary(raw, row):
    count, malformed, own_seen, observer_seen = 0, 0, False, False
    for line in raw.splitlines():
        parts = line.split(None, 8)
        if len(parts) != 9 or not all(value.isdigit() for value in parts[:3]):
            malformed += 1
            continue
        count += 1
        own_seen = own_seen or int(parts[0]) == os.getpid()
        observer_seen = observer_seen or int(parts[0]) == row.get("owned_pid")
    return dict(rows=count, malformed_rows=malformed, coordinator_seen=own_seen,
                observer_seen=observer_seen, complete=(row.get("status") == "EXITED" and
                    row.get("exit_code") == 0 and not row.get("stderr_bytes") and not malformed and
                    row.get("direct_child_reaped") is True and not row.get("child_cleanup_error_type") and
                    0 < count <= 4096 and own_seen and observer_seen),
                scope="Counts only; no global PID/argv rows retained, no ownership or atomic-snapshot claim.")


def stack_frames(raw):
    # sample's headers/binary paths can include command lines. Keep only numeric
    # call-graph frames with a symbol and module, stopping before image listings.
    text = raw.decode("utf-8", errors="replace")
    graph = text.split("Call graph:", 1)
    if len(graph) != 2:
        return []
    result = []
    for line in graph[1].split("Total number in stack", 1)[0].split("Binary Images:", 1)[0].splitlines():
        if (len(line) <= 512 and re.fullmatch(r"[ +!:|]*[0-9]+[ ]+[A-Za-z_~][A-Za-z0-9_~:.$<> +*(),=-]*"
                                             r"\(in [A-Za-z0-9_.+-]+\)[ A-Za-z0-9_+()\[\]x.:-]*", line)):
            result.append(line)
            if len(result) == 512:
                break
    return result


class Commands:
    """Bound pipes while running; own only actual direct, unreaped Popen handles."""
    def __init__(self, environment, deadline, persist=lambda: None):
        self.environment, self.deadline, self.persist = environment, deadline, persist
        self.rows, self.handles, self.signals = [], [], []
        self.protection_spawn_admission = None  # Installed only by the bound ProtectionProbe driver; consumed once.
        self.finalizing = False
        self.preservation = dict(failures=0, errors=[])

    def preservation_error(self, operation, error):
        self.preservation["failures"] += 1
        if len(self.preservation["errors"]) < 16:
            self.preservation["errors"].append(dict(operation=operation, error_type=type(error).__name__))

    def checkpoint(self, required=False):
        # An evidence-write failure is sticky, bounded, and never raw exception
        # text. It prohibits new observations, but cannot abort later cleanup.
        written = False
        try:
            written = self.persist() is not False
            if not written:
                raise OSError("state-preservation-declined")
        except BaseException as error:
            self.preservation_error("state-checkpoint", error)
        if required and not self.finalizing:
            native.require(written and not self.preservation["failures"], "probe-evidence-preservation-failed")
        return written

    def interrupted(self, signum, _frame):
        # Do not raise in the Popen/assignment window or leak a blocked signal
        # mask to children. The next bounded read notices this flag.
        if len(self.signals) < 16:
            self.signals.append(signum)

    def retire(self, child, row):
        try:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=2)
            row["exit_code"] = child.returncode
            row["direct_child_reaped"] = child.returncode is not None
        except BaseException as error:
            row["child_cleanup_error_type"] = type(error).__name__
            row["direct_child_reaped"] = False

    def capture(self, arguments, label, timeout=15, *, sample=False, sample_runtime=False, sample_protection=False,
                executable=None, environment=None, root=ROOT):
        # Never shorten a 15s original observation to fit the overall budget.
        # Refuse another launch and preserve SKIPPED_BUDGET instead.
        row = dict(label=label, command=list(map(str, arguments)), status="NOT_STARTED", timeout_seconds=timeout,
                   started_at=native.now())
        native.require(len(self.rows) < 96, "probe-command-count-limit")
        self.rows.append(row)
        native.require(not sample or (list(arguments) == ORIGINAL_PS and timeout == ORIGINAL_PS_SECONDS),
                       "only-original-ps-may-request-owned-sample")
        # PD04's empty runtime-list timeout needs an observation, not a retry or
        # a larger command budget. This opt-in cannot substitute another image
        # or widen the original ps sampling seam.
        native.require(type(sample_runtime) is bool and (not sample_runtime or (
            not sample and tuple(arguments) == PROTECTION_RUNTIME_LIST and
            type(timeout) is int and timeout == PROTECTION_RUNTIME_SECONDS and
            executable is None and environment is None)),
            "only-exact-runtime-list-may-request-owned-sample")
        native.require(type(sample_protection) is bool, "only-admitted-protection-spawn-may-request-owned-sample")
        if sample_protection:
            admitted = self.protection_spawn_admission
            native.require(type(admitted) is tuple and len(admitted) == 5 and
                all(isinstance(value, str) for value in admitted) and tuple(arguments) == admitted and
                admitted[:3] == ("/usr/bin/xcrun", "simctl", "spawn") and simulator.UUID.fullmatch(admitted[3]),
                "only-admitted-protection-spawn-may-request-owned-sample")
            image = Path(admitted[4])
            native.require(not sample and not sample_runtime and label == "native-observation" and
                type(timeout) is int and timeout == PROTECTION_SPAWN_SECONDS and executable is None and root == ROOT and
                image.is_absolute() and str(image) == admitted[4] and ".." not in image.parts and
                image.name == "ProtectionProbe" and image.parent.name == "resources" and
                re.fullmatch(r"parlor-protection-probe-[1-9][0-9]*-[1-9][0-9]*", image.parent.parent.name) and
                self.environment.get("TMPDIR") == str(image.parent / "tmp") + "/" and
                "SIMCTL_CHILD_TMPDIR" not in self.environment and
                environment == {**self.environment, "SIMCTL_CHILD_TMPDIR": self.environment["TMPDIR"]},
                "only-admitted-protection-spawn-may-request-owned-sample")
            self.protection_spawn_admission = ()  # Also consumed when the unchanged overall budget refuses launch.
        observe_timeout = sample or sample_runtime or sample_protection
        if time.monotonic() + timeout + (12 if observe_timeout else 4) > self.deadline:
            row["status"] = "SKIPPED_BUDGET"
            self.checkpoint()
            return row, b"", b""
        if self.signals and not self.finalizing:
            row["status"] = "INTERRUPTED_BEFORE_LAUNCH"
            self.checkpoint()
            return row, b"", b""
        buffers = [bytearray(), bytearray()]
        child, selector, started = None, selectors.DefaultSelector(), time.monotonic()
        try:
            self.checkpoint(required=True)  # Durable intent before launch; only known arguments.
            child = subprocess.Popen(row["command"], executable=executable, cwd=root,
                env=self.environment if environment is None else environment, stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True, close_fds=True)
            started = time.monotonic()  # The 15s observation starts at launch, not before intent fsync.
            self.handles.append((child, row))
            row.update(owned_pid=child.pid, ownership="direct-unreaped-Popen", status="RUNNING")
            self.checkpoint(required=True)
            for index, pipe in enumerate((child.stdout, child.stderr)):
                os.set_blocking(pipe.fileno(), False)
                selector.register(pipe, selectors.EVENT_READ, index)
            end = started + timeout
            while selector.get_map():
                if self.signals and not self.finalizing:
                    row["status"] = "INTERRUPTED"
                    break
                if time.monotonic() >= end:
                    row.update(status="TIMEOUT", timeout_observed_at=native.now(),
                               observation_elapsed_seconds=round(time.monotonic() - started, 3))
                    self.checkpoint(required=True)  # Latch failure BEFORE any separate sample.
                    break
                for key, _ in selector.select(timeout=min(0.05, max(0, end - time.monotonic()))):
                    block = os.read(key.fileobj.fileno(), 65536)
                    if not block:
                        selector.unregister(key.fileobj)
                        continue
                    if sum(map(len, buffers)) + len(block) > MAX_OUTPUT:
                        row["status"] = "OUTPUT_LIMIT"
                        break
                    buffers[key.data].extend(block)
                if row["status"] == "OUTPUT_LIMIT":
                    break
            if row["status"] == "RUNNING":
                try:
                    child.wait(timeout=max(0.001, end - time.monotonic()))
                    row["status"] = "EXITED"
                except subprocess.TimeoutExpired:
                    row.update(status="TIMEOUT", timeout_observed_at=native.now(),
                               observation_elapsed_seconds=round(time.monotonic() - started, 3))
            if row["status"] == "TIMEOUT" and observe_timeout and child.poll() is None:
                self.checkpoint(required=True)
                # Keep the handle unreaped throughout sampling: exit leaves our
                # zombie, not a reusable PID. Sampling never changes TIMEOUT.
                observation, out, _ = self.capture(
                    ["/usr/bin/sample", str(child.pid), "1", "10", "-file", "/dev/stdout"],
                    label + "-owned-stack", timeout=4)
                frames = stack_frames(out)
                row["stack_observation"] = dict(command_label=observation["label"],
                    status=observation["status"], exit_code=observation.get("exit_code"),
                    target_owned_unreaped_pid=child.pid, frames=frames,
                    usable=(observation.get("status") == "EXITED" and observation.get("exit_code") == 0 and
                            observation.get("direct_child_reaped") is True and
                            not observation.get("child_cleanup_error_type") and bool(frames)),
                    scope=("Symbols only from admitted timed-out direct ProtectionProbe simctl-spawn command child; "
                           "not its simulator descendants; no argv or image paths." if sample_protection else
                           "Symbols only from exact timed-out direct runtime-list command child; no argv or image paths."
                           if sample_runtime else
                           "Symbols only from exact timed-out direct ps child; no argv or image paths."))
        except BaseException as error:
            if row["status"] in {"TIMEOUT", "INTERRUPTED", "OUTPUT_LIMIT"}:
                row["secondary_error_type"] = type(error).__name__
            else:
                row.update(status="FAILED", error_type=type(error).__name__)
        finally:
            if child is not None:
                self.retire(child, row)
                for pipe in (child.stdout, child.stderr):
                    try:
                        pipe.close()
                    except BaseException as error:
                        row["pipe_close_error_type"] = type(error).__name__
            try:
                selector.close()
            except BaseException as error:
                row["selector_close_error_type"] = type(error).__name__
            row.update(elapsed_seconds=round(time.monotonic() - started, 3), finished_at=native.now(),
                       stdout_bytes=len(buffers[0]), stderr_bytes=len(buffers[1]))
            self.checkpoint()
        return row, bytes(buffers[0]), bytes(buffers[1])

    def execute(self, arguments, root=ROOT, timeout=None):
        command = list(map(str, arguments))
        # Restore the shared qualifier's 120s allowance only for these exact
        # simulator enumerations. Run34232325670 exposed the injected 20s cap;
        # it did not establish why the first runtime listing exceeded 20s.
        ceiling = PLATFORM_ENUMERATION_SECONDS.get(tuple(command), 20)
        timeout = ceiling if timeout is None else timeout
        native.require(type(timeout) in (int, float) and 0 < timeout <= ceiling,
                       "unreviewed-probe-command-timeout")
        if self.finalizing and command and command[0] == "git":
            # Preserve the existing bounded source-reconciliation cleanup lane.
            timeout = min(timeout, self.deadline - time.monotonic() - 4.1)
            native.require(timeout > 0, "probe-command-budget-exhausted")
        # capture refuses a launch when the complete allowance plus retirement
        # cannot fit; observation/platform deadlines are never shortened to fit.
        row, out, _ = self.capture(command, "binding-or-platform", timeout, root=root)
        native.require(row["status"] == "EXITED" and row.get("exit_code") == 0 and
                       row.get("direct_child_reaped"), "required-probe-command-failed")
        return out


class SimBackend:
    def __init__(self, commands):
        self.commands = commands

    def run(self, *arguments):
        # Run18's successful bootstatus took92.34s; this is not the ps limit.
        timeout = 120 if arguments[0] == "bootstatus" else 20
        row, out, _ = self.commands.capture(["/usr/bin/xcrun", "simctl", *arguments], "simctl-" + arguments[0], timeout)
        native.require(row["status"] == "EXITED" and row.get("direct_child_reaped"), "simctl-command-not-completed")
        return row["exit_code"], out.decode("utf-8")

    def inventory(self):
        code, text = self.run("list", "devices", "--json")
        native.require(code == 0, "simulator-inventory-failed")
        return native.decode(text)


class Probe:
    def __init__(self, env, root=ROOT, commands=None):
        self.env, self.root = env, root
        native.require(env.get("PARLOR_DISPATCH_SCOPE") == SCOPE, "explicit-process-probe-scope-required")
        self.commands = commands if commands is not None else Commands(child_environment(env), time.monotonic() + OBSERVATION_SECONDS)
        self.context = native.context(env, root, execute=self.commands.execute)
        self.approval = env.get("PARLOR_APPROVED_PROBE_CONTROL_SHA256", "")
        self.control = controls(root)
        native.require(native.HEX64.fullmatch(self.approval) and self.approval == self.control["control_sha256"],
                       "independent-process-probe-control-approval-mismatch")
        temp = Path(env["RUNNER_TEMP"]).resolve(strict=True)
        native.require(root != temp and root not in temp.parents and temp not in root.parents,
                       "probe-custody-must-be-outside-checkout")
        self.base = temp / "parlor-process-probe-{}-{}".format(self.context["run_id"], self.context["run_attempt"])
        self.bundle, self.resources = self.base / "bundle", self.base / "resources"
        self.cleanup_path = self.base.with_name(self.base.name + "-cleanup.json")
        self.state = None
        self.claimed_base = None
        self.claimed_resources = None

    def save(self):
        if self.state is not None:
            partial = self.base / "state.writing"
            native.write_new(partial, native.json_bytes(self.state))
            partial.replace(self.base / "state.json")

    def stage(self, label, callback, cleanup=False):
        entry = dict(label=label, status="INCOMPLETE", started_at=native.now())
        self.state["cleanup_stages" if cleanup else "stages"].append(entry)
        try:
            self.commands.checkpoint(required=not cleanup)
            result = callback()
            entry.update(status="COMPLETE", result=result)
            return result
        except BaseException as error:
            entry.update(status="FAILED", error_type=type(error).__name__)
            return None
        finally:
            entry["finished_at"] = native.now()
            self.commands.checkpoint()

    def ps_identity(self):
        located = shutil.which("ps", path=self.commands.environment["PATH"])
        native.require(located is not None, "path-ps-not-found")
        path = Path(located).resolve(strict=True)
        self.ps_file = executable_identity(path)
        self.ps = str(path)
        result = dict(**self.ps_file, located_path=located,
                      path_search_sha256=native.sha(self.commands.environment["PATH"].encode()))
        for label, args in (("signature", ["-d", "--verbose=4"]),
                            ("entitlements", ["-d", "--entitlements", ":-"]),
                            ("signature_verify", ["--verify", "--strict"])):
            row, out, err = self.commands.capture(["/usr/bin/codesign", *args, self.ps], label, 10)
            item = dict(command_label=label, status=row["status"], exit_code=row.get("exit_code"),
                        stdout_sha256=native.sha(out), stderr_sha256=native.sha(err))
            if label == "signature":
                prefixes = ("Executable=", "Identifier=", "Format=", "CodeDirectory ", "Platform identifier=",
                            "CDHash=", "Hash type=", "Authority=", "TeamIdentifier=", "Signature size=", "Runtime Version=")
                item["fields"] = [line for line in (out + err).decode("utf-8", errors="replace").splitlines()
                                  if len(line) <= 512 and line.startswith(prefixes)][:32]
            if label == "entitlements":
                candidates = re.findall(rb"<\?xml\b.*?</plist>", out + err, re.DOTALL)
                native.require(len(candidates) <= 1, "ambiguous-ps-entitlements")
                item["entitlements"] = plistlib.loads(candidates[0]) if candidates else None
                item["meaning"] = "Observed codesign output; null is not an asserted empty entitlement set."
            result[label] = item
        return result

    def metrics(self):
        values = self.commands.execute(["/usr/sbin/sysctl", "-n", "hw.ncpu", "hw.memsize", "hw.physicalcpu", "hw.logicalcpu"])
        parts = values.splitlines()
        native.require(len(parts) == 4 and all(row.isdigit() for row in parts), "unexpected-numeric-host-metrics")
        vm = self.commands.execute(["/usr/bin/vm_stat"]).decode("ascii")
        header = re.search(r"page size of ([0-9]+) bytes", vm)
        native.require(header is not None, "missing-vm-page-size")
        pages = {}
        for line in vm.splitlines()[1:]:
            match = re.fullmatch(r"([A-Za-z ]+):\s+([0-9]+)\.", line)
            if match and match[1] in VM_FIELDS:
                pages[match[1]] = int(match[2])
        native.require({"Pages free", "Pages active", "Pages inactive"} <= set(pages), "incomplete-vm-stat-metrics")
        return dict(cpu=dict(zip(("ncpu", "memory_bytes", "physical_cpu", "logical_cpu"), map(int, parts))),
                    vm_page_bytes=int(header[1]), vm=pages)

    def observations(self, phase, xcode=None):
        rows = []
        programs = (("original-ps", ORIGINAL_PS, True), ("metadata-ps", METADATA_PS, False),
                    ("libproc-metadata", ["/usr/bin/python3", "-B", str(self.root / "scripts/ci/native_process_metadata.py"), "observe"], False))
        for name, arguments, sample in programs:
            overlap = {"live_before": xcode.poll() is None} if xcode is not None else None
            row, out, _ = self.commands.capture(arguments, phase + "-" + name, ORIGINAL_PS_SECONDS,
                sample=sample, executable=self.ps if name.endswith("ps") else None)
            if overlap is not None:
                overlap["live_after"] = xcode.poll() is None
                row["nonbuilding_xcode_overlap"] = overlap
            if name.endswith("ps"):
                summary = ps_summary(out, row)
            elif row["status"] == "EXITED" and row.get("exit_code") == 0:
                summary = native.decode(out)
                native.require(summary.get("kind") == "LIBPROC_METADATA_OBSERVATION_ONLY", "invalid-metadata-child-result")
            else:
                summary = {"complete": False, "scope": "Native metadata child did not complete; no absence claim."}
            row["observation"] = summary
            rows.append(row["label"])
            native.require(all(item.get("direct_child_reaped") is True for child, item in self.commands.handles
                               if child is not xcode), "unretired-probe-child-no-next-observation")
            self.commands.checkpoint(required=True)
        return rows

    def allocate_simulator(self):
        inventory = simulator.devices(self.backend.inventory())
        plan = dict(**self.claim, name="Parlor-ci-" + self.claim["nonce"],
                    runtime=self.platform["runtime"], device_type=self.platform["device_type"],
                    baseline=sorted(row["udid"] for row in inventory), planned_at=native.now())
        native.require(not any(row.get("name") == plan["name"] for row in inventory), "pre-existing-probe-simulator")
        native.write_new(simulator.paths(self.prefix)[0], native.json_bytes(plan))
        code, output = self.backend.run("create", plan["name"], plan["device_type"], plan["runtime"])
        result = dict(task=self.claim["task"], nonce=self.claim["nonce"], exit_code=code, udid=None)
        if code == 0 and simulator.UUID.fullmatch(output.strip()):
            result["udid"] = simulator.checked_uuid(output.strip())
        native.write_new(simulator.paths(self.prefix)[1], native.json_bytes(result))
        native.require(code == 0 and result["udid"], "probe-simulator-create-failed")
        device = simulator.owned_device(plan, result, simulator.devices(self.backend.inventory()))
        native.require(device is not None, "probe-simulator-not-in-inventory")
        self.state["owned_simulator_uuid"] = device["udid"]
        self.commands.checkpoint(required=True)
        code, _ = self.backend.run("boot", device["udid"])
        native.require(code == 0, "probe-simulator-boot-failed")
        code, _ = self.backend.run("bootstatus", device["udid"], "-b")
        native.require(code == 0, "probe-simulator-bootstatus-failed")
        return dict(uuid=device["udid"], name=plan["name"], runtime=plan["runtime"], boot_completed=True)

    def nonbuilding_overlap(self):
        native.require(time.monotonic() + 64 <= self.commands.deadline, "insufficient-budget-for-single-nonbuilding-overlap")
        derived = self.resources / "DerivedData"
        derived.mkdir(mode=0o700)
        arguments = ["/usr/bin/xcodebuild", "-project", str(self.root / "iosApp/iosApp.xcodeproj"),
            "-scheme", "iosApp", "-configuration", "Debug", "-sdk", "iphonesimulator",
            "-destination", "generic/platform=iOS Simulator", "-derivedDataPath", str(derived),
            "-disableAutomaticPackageResolution", "-skipPackageUpdates", "-showBuildSettings"]
        row = dict(label="nonbuilding-xcode-settings", command=arguments, status="NOT_STARTED",
                   timeout_seconds=60, started_at=native.now(), output_policy="DEVNULL; no build settings retained")
        self.commands.rows.append(row)
        self.state["derived_data_custody"] = native.custody(derived)
        self.commands.checkpoint(required=True)
        child, started, previous_deadline = None, time.monotonic(), self.commands.deadline
        try:
            native.require(not self.commands.signals, "interrupted-before-nonbuilding-xcode")
            child = subprocess.Popen(arguments, cwd=self.root, env=self.commands.environment,
                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                close_fds=True, start_new_session=True)
            self.commands.handles.append((child, row))
            row.update(status="RUNNING", owned_pid=child.pid, ownership="direct-unreaped-Popen")
            self.commands.checkpoint(required=True)
            self.commands.deadline = min(previous_deadline, started + 60)
            labels = self.observations("nonbuilding-xcode", child)
            while child.poll() is None:
                if self.commands.signals:
                    row["status"] = "INTERRUPTED"
                    break
                remaining = started + 60 - time.monotonic()
                if remaining <= 0:
                    row["status"] = "TIMEOUT"
                    break
                try:
                    child.wait(timeout=min(0.1, remaining))
                except subprocess.TimeoutExpired:
                    pass
            if row["status"] == "RUNNING":
                row["status"] = "EXITED"
            return dict(observations=labels, scope="ShowBuildSettings only; no build/test or actool/Kotlin workload reproduced by assertion.")
        finally:
            if child is not None:
                self.commands.retire(child, row)
            row["elapsed_seconds"] = round(time.monotonic() - started, 3)
            self.commands.deadline = previous_deadline
            self.commands.checkpoint()

    def stop_gradle(self, label):
        environment = {**self.commands.environment, "GRADLE_USER_HOME": str(self.resources / "gradle-home"),
                       "GRADLE_OPTS": "-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process"}
        # Cold A17 stop downloaded the wrapper and took54.705s; warm B18
        # immediate stop took5.113s. Cleanup bounds do not change the ps15s limit.
        timeout = 65 if label == "stop-immediate" else 30
        row, _, _ = self.commands.capture([str(self.root / "gradlew"), "--stop"], label, timeout,
                                          environment=environment, root=self.resources)
        native.require(row["status"] == "EXITED" and row.get("exit_code") == 0 and row.get("direct_child_reaped"),
                       "isolated-gradle-stop-failed")
        return dict(exit_code=0, isolated_registry=True, no_build_or_test_task=True)

    def clean_resources(self):
        native.require(all(row.get("direct_child_reaped") for _, row in self.commands.handles), "direct-child-remains")
        native.require(native.custody(self.resources) == self.state["resources_custody"], "probe-resource-custody-changed")
        row, out, err = self.commands.capture(["/usr/sbin/lsof", "-nP", "-t", "+w", "+D", str(self.resources)], "resource-holders", 10)
        native.require(row["status"] == "EXITED" and row.get("exit_code") == 1 and
                       row.get("direct_child_reaped") is True and not row.get("child_cleanup_error_type") and
                       not err.strip() and not out.strip(),
                       "probe-resource-holders-or-inspection-error-retain-resources")
        derived = self.resources / "DerivedData"
        if derived.exists() or derived.is_symlink():
            native.require(native.custody(derived) == self.state.get("derived_data_custody"), "derived-data-custody-changed")
        native.require(shutil.rmtree.avoids_symlink_attacks, "descriptor-safe-resource-cleanup-required")
        shutil.rmtree(self.resources)  # Only this exclusive root; cache symlink is never followed.
        return {"resources_removed": not self.resources.exists(), "global_cache_deleted": False}

    def run(self):
        native.require(self.commands.execute(["git", "status", "--porcelain=v1", "--untracked-files=all"], self.root).strip() == b"",
                       "process-probe-needs-fresh-clean-checkout")
        self.base.mkdir(mode=0o700)
        self.claimed_base = native.custody(self.base)
        self.bundle.mkdir(mode=0o700)
        self.resources.mkdir(mode=0o700)
        self.claimed_resources = native.custody(self.resources)
        self.claim = dict(task=self.context, cycle=SCOPE, nonce=uuid.uuid4().hex)
        self.prefix, self.backend = self.bundle / "owned", SimBackend(self.commands)
        self.state = dict(schema_version=1, kind="NATIVE_PROCESS_PROBE_NOT_APP_EVIDENCE", context=self.context,
            control_manifest=self.control, approved_control_sha256=self.approval, base_custody=native.custody(self.base),
            resources_custody=self.claimed_resources, claim=self.claim, status="INCOMPLETE",
            started_at=native.now(), stages=[], cleanup_stages=[], commands=self.commands.rows,
            preservation=self.commands.preservation,
            original_process_source_seam=original_seam(self.root),
            observation_budget_seconds=300, cleanup_budget_seconds=225,
            scope="No application build, XCTest, A/B libproc provenance, protection, Store or device qualification.")
        self.commands.persist = self.save
        initialized = False
        try:
            self.commands.checkpoint(required=True)
            (self.resources / "gradle-home").mkdir(mode=0o700)
            cache = Path.home() / ".gradle" / "wrapper"
            created = []
            for path in (cache.parent, cache):
                if not path.exists():
                    path.mkdir(mode=0o700)
                    created.append(str(path))
                native.require(path.resolve() == path and path.is_dir() and path.stat().st_uid == os.getuid(), "unsafe-public-wrapper-cache")
            (self.resources / "gradle-home/wrapper").symlink_to(cache, target_is_directory=True)
            self.state["public_wrapper_cache"] = dict(path=str(cache), created=created, policy="Retain global distribution cache; isolated daemon registry.")
            initialized = True
            self.platform = self.stage("qualified-platform", lambda: native.qualified_platform(
                self.root, execute=self.commands.execute, environment=self.commands.environment))
            native.require(self.platform is not None, "qualified-platform-not-complete")
            self.state["toolchain"] = self.platform
            self.commands.environment["JAVA_HOME"] = self.platform["java_home"]
            for key, option in (("os_version", "-productVersion"), ("os_build", "-buildVersion")):
                value = self.commands.execute(["/usr/bin/sw_vers", option]).decode().strip()
                native.require(re.fullmatch("[A-Za-z0-9.]{1,32}", value), "invalid-host-version")
                self.state[key] = value
            native.require(self.stage("installed-ps-identity", self.ps_identity) is not None, "installed-ps-observation-failed")
            self.stage("before-boot-metrics", self.metrics)
            self.stage("before-boot-process-observations", lambda: self.observations("before-boot"))
            native.require(self.stage("one-owned-simulator-boot", self.allocate_simulator) is not None, "owned-simulator-boot-not-complete")
            self.stage("after-boot-metrics", self.metrics)
            self.stage("after-boot-process-observations", lambda: self.observations("after-boot"))
            self.stage("single-nonbuilding-xcode-overlap", self.nonbuilding_overlap)
        except BaseException as error:
            self.state["primary_error_type"] = type(error).__name__
        finally:
            # Cleanup is a separate budget, not a hard kill at the observation
            # deadline. Every independent stage is attempted despite failures.
            self.commands.finalizing = True
            self.commands.deadline = time.monotonic() + 70
            if initialized:
                self.stage("stop-immediate", lambda: self.stop_gradle("stop-immediate"), cleanup=True)
            self.commands.deadline = time.monotonic() + 85
            for child, row in self.commands.handles:
                self.commands.retire(child, row)
            def retire_simulator():
                result = simulator.cleanup(self.prefix, self.claim, self.backend)
                owned = result.get("udid")
                if owned:
                    path = Path.home() / "Library/Developer/CoreSimulator/Devices" / simulator.checked_uuid(owned)
                    native.require(not path.exists() and not path.is_symlink(), "owned-simulator-directory-remains")
                return result
            self.stage("owned-simulator-retirement", retire_simulator, cleanup=True)
            self.commands.deadline = time.monotonic() + 35
            if initialized:
                self.stage("stop-final", lambda: self.stop_gradle("stop-final"), cleanup=True)
            self.commands.deadline = time.monotonic() + 15
            self.stage("exact-owned-resource-cleanup", self.clean_resources, cleanup=True)
            self.commands.deadline = time.monotonic() + 20
            def source_after():
                native.require(native.context(self.env, self.root, execute=self.commands.execute) == self.context and
                    controls(self.root) == self.control and self.commands.execute(
                        ["git", "status", "--porcelain=v1", "--untracked-files=all"], self.root).strip() == b"", "probe-source-or-controls-changed")
                if hasattr(self, "ps_file"):
                    native.require(executable_identity(self.ps) == self.ps_file, "ps-executable-changed-after-observations")
                return dict(source_unchanged=True, controls_unchanged=True)
            self.stage("source-control-reconciliation", source_after, cleanup=True)
            self.state.update(finished_at=native.now(), signals=self.commands.signals,
                cleanup_safe=all(row["status"] == "COMPLETE" for row in self.state["cleanup_stages"]))
            self.finish_evidence()
        print(json.dumps({key: self.state[key] for key in ("kind", "status", "cleanup_safe", "preservation")}))
        return 2 if self.failed() else 0

    def failed(self):
        return bool(self.state.get("primary_error_type") or self.commands.signals or
            self.commands.preservation["failures"] or not self.state["cleanup_safe"] or
            any(row["status"] != "COMPLETE" for row in self.state["stages"]) or
            any(row["status"] != "EXITED" or row.get("exit_code") != 0 for row in self.commands.rows
                if row["label"] != "resource-holders") or
            any(not row.get("direct_child_reaped") or row.get("child_cleanup_error_type") or
                row.get("pipe_close_error_type") or row.get("selector_close_error_type")
                for _, row in self.commands.handles) or
            any(row.get("observation", {}).get("complete") is False for row in self.commands.rows))

    def finish_evidence(self):
        def status():
            self.state["status"] = ("OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE" if self.failed()
                                    else "OBSERVATIONS_COLLECTED_NOT_RUNTIME_EVIDENCE")
        status()
        try:
            native.write_new(self.bundle / "probe.json", native.json_bytes(self.state))
            self.state["bundle_manifest"] = native.tree_manifest(self.bundle)
        except BaseException as error:
            self.commands.preservation_error("final-bundle", error)
        status()
        self.commands.checkpoint()
        status()
        if self.commands.preservation["failures"]:
            # Additive last chance; never overwrite a partial/failed receipt or
            # claim that a failed state write or missing bundle was preserved.
            try:
                native.write_new(self.bundle / "preservation-failure.json", native.json_bytes(dict(
                    kind=self.state["kind"], status=self.state["status"], preservation=self.commands.preservation,
                    scope="Evidence preservation failed; retained bytes may be incomplete. No runtime or custody-cleanup pass.")))
            except BaseException as error:
                self.commands.preservation_error("last-chance-receipt", error)

    def cleanup(self):
        receipt = dict(schema_version=1, context=self.context, kind="PROCESS_PROBE_CUSTODY_CLEANUP",
                       status="FAILED", started_at=native.now())
        try:
            state = native.decode(native.file_bytes(self.base / "state.json"))
            native.require(state.get("context") == self.context and state.get("control_manifest") == self.control and
                state.get("base_custody") == native.custody(self.base), "process-probe-cleanup-binding-changed")
            native.require(self.env.get("PARLOR_PROBE_UPLOAD_OUTCOME") == "success" and
                native.POSITIVE.fullmatch(self.env.get("PARLOR_PROBE_ARTIFACT_ID", "")) and
                native.HEX64.fullmatch(self.env.get("PARLOR_PROBE_ARTIFACT_DIGEST", "")), "probe-upload-custody-missing-retain-evidence")
            receipt["upload"] = dict(artifact_id=self.env["PARLOR_PROBE_ARTIFACT_ID"], sha256=self.env["PARLOR_PROBE_ARTIFACT_DIGEST"])
            native.require(state.get("cleanup_safe") is True and
                state.get("preservation") == {"failures": 0, "errors": []} and not self.resources.exists() and
                native.tree_manifest(self.bundle) == state.get("bundle_manifest"), "probe-cleanup-incomplete-or-evidence-changed")
            native.require({path.name for path in self.base.iterdir()} == {"bundle", "state.json"}, "unexpected-probe-staging-child")
            shutil.rmtree(self.base)
            native.require(not self.base.exists(), "probe-staging-remains")
            receipt.update(status="COMPLETE", removed_owned_staging=True, probe_status=state["status"],
                           scope="Custody cleanup only; probe status is preserved, not an application result.")
        except BaseException as error:
            receipt["error_type"] = type(error).__name__
        receipt["finished_at"] = native.now()
        native.write_new(self.cleanup_path, native.json_bytes(receipt))
        print(json.dumps(receipt))
        return 0 if receipt["status"] == "COMPLETE" else 1


def preserve_entry_failure(env, commands, lane, error):
    """Best-effort *additive* receipt; never claim unvalidated-source cleanup.

    The enclosing main finally already retired direct handles. If setup failed
    before run's native finalizer, its only possible resource is an empty,
    exclusively created directory: rmdir that exact attested empty directory,
    never recursively delete unknown contents or an unclaimed existing base.
    """
    try:
        if lane is None:
            native.require(all(native.POSITIVE.fullmatch(env.get(key, "")) for key in
                ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")), "missing-entry-run-identity")
            temp = Path(env["RUNNER_TEMP"]).resolve(strict=True)
            native.require(temp != ROOT and ROOT not in temp.parents and temp not in ROOT.parents,
                           "unsafe-entry-evidence-parent")
            base = temp / ("parlor-process-probe-" + env["GITHUB_RUN_ID"] + "-" + env["GITHUB_RUN_ATTEMPT"])
            base.mkdir(mode=0o700)  # Never adopt a pre-existing evidence directory.
            bundle = base / "bundle"
            bundle.mkdir(mode=0o700)
        else:
            native.require(lane.claimed_base is not None and native.custody(lane.base) == lane.claimed_base,
                           "no-attested-entry-evidence-directory")
            base, bundle = lane.base, lane.bundle
            if not bundle.exists():
                bundle.mkdir(mode=0o700)
            if lane.claimed_resources is not None and lane.resources.exists() and lane.state is None:
                native.require(native.custody(lane.resources) == lane.claimed_resources, "entry-resource-custody-changed")
                lane.resources.rmdir()  # Refuses unknown contents; no native work began here.
        native.write_new(bundle / "entry-failure.json", native.json_bytes(dict(
            kind="NATIVE_PROCESS_PROBE_NOT_APP_EVIDENCE", status="ENTRY_FAILED", error_type=type(error).__name__,
            context=lane.context if lane is not None else None, source_binding_completed=lane is not None,
            run_id=env.get("GITHUB_RUN_ID"), run_attempt=env.get("GITHUB_RUN_ATTEMPT"),
            direct_commands=commands.rows, signals=commands.signals,
            scope="Initialization/finalization failure; no successful cleanup or application claim.")))
    except BaseException:
        return False  # Never print raw filesystem or subprocess exception text.
    return True


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    native.require(len(arguments) == 1 and arguments[0] in {"controls", "run", "cleanup"}, "invalid-process-probe-command")
    if arguments[0] == "controls":
        print(json.dumps(controls(), indent=2))
        return 0
    commands = Commands(child_environment(os.environ), time.monotonic() + OBSERVATION_SECONDS)
    # Lifetime protection starts BEFORE context's first Git Popen and all
    # allocation. Signal handlers never raise across Popen/assignment windows.
    previous = {item: signal.signal(item, commands.interrupted) for item in (signal.SIGINT, signal.SIGTERM)}
    lane, failure = None, None
    try:
        lane = Probe(os.environ, commands=commands)
        return lane.run() if arguments[0] == "run" else lane.cleanup()
    except BaseException as error:
        failure = error
        raise
    finally:
        commands.finalizing = True
        for child, row in commands.handles:
            commands.retire(child, row)
        if failure is not None and arguments[0] == "run":
            preserve_entry_failure(os.environ, commands, lane, failure)
        for item, handler in previous.items():
            signal.signal(item, handler)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps(dict(kind="NATIVE_PROCESS_PROBE_NOT_APP_EVIDENCE", status="ENTRY_FAILED", error_type=type(error).__name__)))
        raise SystemExit(1)
