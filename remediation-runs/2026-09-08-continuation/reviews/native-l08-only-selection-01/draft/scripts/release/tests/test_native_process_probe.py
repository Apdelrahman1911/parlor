"""Synthetic Linux controls only; no Apple subprocess or application is run."""
from __future__ import annotations

import ast
from contextlib import ExitStack
import ctypes
import errno
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts/ci"))
from scripts.ci import native_process_probe as probe
from scripts.ci import native_continuation as native
import native_process_metadata as metadata


class Clock:
    value = 0

    def __call__(self):
        return self.value


class Child:
    def __init__(self):
        self.pid, self.returncode, self.waits, self.signals = 12345, None, [], []
        self.stdout = Mock(fileno=lambda: 100)
        self.stderr = Mock(fileno=lambda: 101)

    def poll(self):
        return self.returncode

    def wait(self, timeout):
        self.waits.append(timeout)
        self.returncode = 0
        return 0

    def terminate(self):
        self.signals.append("TERM")

    def kill(self):
        self.signals.append("KILL")


class Selector:
    def __init__(self, clock, stalled=False):
        self.clock, self.stalled, self.pipes = clock, stalled, {}

    def register(self, pipe, _events, index):
        self.pipes[pipe.fileno()] = SimpleNamespace(fileobj=pipe, data=index)

    def unregister(self, pipe):
        del self.pipes[pipe.fileno()]

    def get_map(self):
        return self.pipes

    def select(self, timeout):
        self.clock.value += 1
        return [] if self.stalled else [(next(iter(self.pipes.values())), 1)]

    def close(self):
        pass


def cleanup_fixture(base):
    lane = object.__new__(probe.Probe)
    lane.context, lane.control = {"head_sha": "a" * 40}, {"control_sha256": "b" * 64}
    lane.base, lane.bundle, lane.resources = base, base / "bundle", base / "resources"
    lane.cleanup_path = base.with_name(base.name + "-cleanup.json")
    lane.env = dict(PARLOR_PROBE_UPLOAD_OUTCOME="success", PARLOR_PROBE_ARTIFACT_ID="100",
                    PARLOR_PROBE_ARTIFACT_DIGEST="c" * 64)
    lane.bundle.mkdir(parents=True)
    native.write_new(lane.bundle / "probe.json", b'{"status":"OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE"}\n')
    state = dict(context=lane.context, control_manifest=lane.control, base_custody=native.custody(base),
                 cleanup_safe=True, bundle_manifest=native.tree_manifest(lane.bundle),
                 preservation={"failures": 0, "errors": []},
                 status="OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE")
    native.write_new(base / "state.json", native.json_bytes(state))
    return lane


class ProbeSourceContractTest(unittest.TestCase):
    def test_original_command_and_timeout_are_bound_without_importing_runner(self):
        value = probe.original_seam()
        self.assertEqual(value["command"], ["ps", "-axo", "pid=,ppid=,pgid=,lstart=,command="])
        self.assertEqual(value["timeout_seconds"], 15)
        self.assertFalse(value["source_imported_or_modified"])

    def test_changed_original_timeout_or_fields_cannot_be_silently_compared(self):
        raw = native.file_bytes(ROOT / "scripts/verification/ios-readiness/owned_lane.py")
        for old, new in ((b"timeout=15", b"timeout=30"), (b"lstart=,command=", b"lstart=,comm=")):
            with self.subTest(old=old), patch.object(native, "file_bytes", return_value=raw.replace(old, new, 1)):
                with self.assertRaisesRegex(RuntimeError, "original-ps-command"):
                    probe.original_seam()

    def test_complete_runtime_closure_and_control_hash_are_explicit(self):
        result = probe.controls()
        self.assertEqual(result["control_sha256"], native.sha(json.dumps(result["files"], separators=(",", ":")).encode()))
        self.assertEqual(len(result["files"]), len(set(row["path"] for row in result["files"])))
        for path in ("scripts/ci/darwin_worker_identity.py", "scripts/ci/owned_ci_simulator.py",
                     "scripts/ci/verification_hygiene.py", "scripts/ci/native_continuation.py",
                     "scripts/verification/ios-readiness/owned_lane.py", "scripts/verification/ios-readiness/toolchain_profiles.py"):
            self.assertIn(path, probe.CONTROL_PATHS)

    def test_environment_is_an_allowlist_not_only_one_token_exclusion(self):
        env = dict(HOME="/safe/home", PATH="/usr/bin:/bin", DEVELOPER_DIR="/Applications/Xcode.app",
                   PARLOR_ACTIONS_READ_TOKEN="private", GITHUB_TOKEN="private", ACTIONS_RUNTIME_TOKEN="private",
                   RUNNER_TRACKING_ID="private", DYLD_INSERT_LIBRARIES="private", PYTHONPATH="private",
                   JAVA_TOOL_OPTIONS="private", GRADLE_OPTS="private", RANDOM_SECRET="private")
        actual = probe.child_environment(env)
        self.assertEqual(actual, dict(HOME="/safe/home", PATH="/usr/bin:/bin", DEVELOPER_DIR="/Applications/Xcode.app",
                                     LANG="en_US.UTF-8", LC_ALL="C", PYTHONDONTWRITEBYTECODE="1"))

    def test_full_non_app_scope_is_explicit_and_cycles_do_not_reuse_failed_labels(self):
        self.assertEqual(native.effective_scope("workflow_dispatch", probe.SCOPE), probe.SCOPE)
        self.assertEqual(native.effective_scope("pull_request", probe.SCOPE), "full")
        self.assertEqual(native.CYCLES, {"l08": "ios-readiness-31", "normal": "ios-readiness-32"})
        with self.assertRaisesRegex(RuntimeError, "not-an-app-native-continuation"):
            native.Continuation(dict(GITHUB_EVENT_NAME="workflow_dispatch", PARLOR_DISPATCH_SCOPE=probe.SCOPE))

    def test_nonbuilding_xcode_arguments_have_no_build_or_test_action(self):
        tree = ast.parse((ROOT / "scripts/ci/native_process_probe.py").read_text())
        method = next(node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef) and node.name == "nonbuilding_overlap")
        constants = [node.value for node in ast.walk(method) if isinstance(node, ast.Constant) and isinstance(node.value, str)]
        for required in ("-showBuildSettings", "-disableAutomaticPackageResolution", "-skipPackageUpdates",
                         "-derivedDataPath", "-scheme", "iosApp", "-configuration", "Debug"):
            self.assertIn(required, constants)
        self.assertFalse({"build", "test", "archive", "-allowProvisioningUpdates"} & set(constants))


class ProbeBoundedCaptureTest(unittest.TestCase):
    def capture(self, *, stalled=False, output=b"", sampler=None, persist=lambda: None):
        clock, child = Clock(), Child()
        lane = probe.Commands({"PATH": "/usr/bin:/bin", "HOME": "/safe/home"}, 100, persist=persist)
        selector = Selector(clock, stalled)
        values = {100: iter([output, b""]), 101: iter([b""])}
        original = lane.capture
        def dispatch(arguments, *args, **kwargs):
            if arguments[0] == "/usr/bin/sample":
                self.assertIsNone(child.returncode)
                self.assertEqual(child.waits, [])
                self.assertEqual(arguments, ["/usr/bin/sample", "12345", "1", "10", "-file", "/dev/stdout"])
                if sampler is not None:
                    return sampler(clock, child)
                return {"label": "sample", "status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"Call graph:\n    10 sysctl (in libsystem_c.dylib) + 20 [0x123]\n", b""
            return original(arguments, *args, **kwargs)
        with patch.object(probe.time, "monotonic", side_effect=clock), \
                patch.object(probe.selectors, "DefaultSelector", return_value=selector), \
                patch.object(probe.subprocess, "Popen", return_value=child) as popen, \
                patch.object(probe.os, "set_blocking"), \
                patch.object(probe.os, "read", side_effect=lambda fd, _size: next(values[fd])), \
                patch.object(lane, "capture", side_effect=dispatch):
            row, out, err = lane.capture(probe.ORIGINAL_PS, "original", 15, sample=stalled)
        return lane, child, row, out, err, popen

    def test_capture_drains_bounded_pipes_and_never_persists_raw_bytes(self):
        _, child, row, out, _, popen = self.capture(output=b"PRIVATE ARGV SECRET\n")
        self.assertEqual(out, b"PRIVATE ARGV SECRET\n")
        self.assertEqual(row["status"], "EXITED")
        self.assertTrue(row["direct_child_reaped"])
        self.assertNotIn("PRIVATE", json.dumps(row))
        self.assertEqual(popen.call_args.kwargs["env"], {"PATH": "/usr/bin:/bin", "HOME": "/safe/home"})
        self.assertEqual(child.signals, [])

    def test_output_limit_is_enforced_before_unbounded_accumulation(self):
        with patch.object(probe, "MAX_OUTPUT", 8):
            _, child, row, out, _, _ = self.capture(output=b"private" * 100)
        self.assertEqual(row["status"], "OUTPUT_LIMIT")
        self.assertEqual(out, b"")
        self.assertEqual(child.signals, ["TERM"])

    def test_timeout_is_latched_before_owned_sample_even_if_ps_exits_during_sample(self):
        def completed_during_sample(clock, child):
            clock.value += 3
            # It remains unreaped for the sample, then normal child retirement
            # may observe that it exited. This must NOT turn timeout into success.
            child.returncode = 0
            return {"label": "sample", "status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"Call graph:\n    10 sysctl (in libsystem_c.dylib) + 20 [0x123]\n", b""
        _, child, row, _, _, _ = self.capture(stalled=True, sampler=completed_during_sample)
        self.assertEqual(row["status"], "TIMEOUT")
        self.assertEqual(row["observation_elapsed_seconds"], 15)
        self.assertEqual(row["elapsed_seconds"], 18)
        self.assertEqual(row["exit_code"], 0)
        self.assertEqual(row["stack_observation"]["target_owned_unreaped_pid"], child.pid)
        self.assertTrue(row["stack_observation"]["usable"])

    def test_failed_or_unreaped_sample_cannot_claim_usable_frames(self):
        for change in ({"status": "TIMEOUT"}, {"direct_child_reaped": False}, {"child_cleanup_error_type": "OSError"}):
            def sample(_clock, _child):
                return {"label": "sample", "status": "EXITED", "exit_code": 0, "direct_child_reaped": True, **change}, \
                    b"Call graph:\n    10 sysctl (in libsystem_c.dylib) + 20 [0x123]\n", b""
            with self.subTest(change=change):
                _, _, row, _, _, _ = self.capture(stalled=True, sampler=sample)
                self.assertFalse(row["stack_observation"]["usable"])
                self.assertEqual(row["status"], "TIMEOUT")

    def test_sample_exception_cannot_mask_primary_timeout_or_disclose_text(self):
        def broken(_clock, _child):
            raise RuntimeError("PRIVATE GLOBAL ARGV")
        _, _, row, _, _, _ = self.capture(stalled=True, sampler=broken)
        self.assertEqual(row["status"], "TIMEOUT")
        self.assertEqual(row["secondary_error_type"], "RuntimeError")
        self.assertNotIn("PRIVATE", json.dumps(row))

    def test_budget_refuses_launch_rather_than_shortening_fifteen_seconds(self):
        lane = probe.Commands({}, 26)
        with patch.object(probe.time, "monotonic", return_value=0), patch.object(probe.subprocess, "Popen") as launch:
            row, _, _ = lane.capture(probe.ORIGINAL_PS, "original", 15, sample=True)
        self.assertEqual(row["status"], "SKIPPED_BUDGET")
        self.assertEqual(row["timeout_seconds"], 15)
        launch.assert_not_called()

    def test_signal_before_launch_does_not_launch_or_block_child_masks(self):
        lane = probe.Commands({}, float("inf"))
        lane.interrupted(15, None)
        with patch.object(probe.subprocess, "Popen") as launch:
            row, _, _ = lane.capture(probe.ORIGINAL_PS, "original", 15, sample=True)
        self.assertEqual(row["status"], "INTERRUPTED_BEFORE_LAUNCH")
        launch.assert_not_called()

    def test_only_original_ps_may_be_sampled(self):
        lane = probe.Commands({}, float("inf"))
        with self.assertRaisesRegex(RuntimeError, "only-original-ps"), patch.object(probe.subprocess, "Popen") as launch:
            lane.capture(probe.METADATA_PS, "metadata", 15, sample=True)
        launch.assert_not_called()

    def test_failed_durable_intent_prevents_launch_and_never_exports_exception_text(self):
        lane, _, row, _, _, launch = self.capture(persist=Mock(side_effect=OSError("PRIVATE WRITE PATH")))
        launch.assert_not_called()
        self.assertEqual(row["status"], "FAILED")
        self.assertGreater(lane.preservation["failures"], 0)
        self.assertNotIn("PRIVATE", json.dumps(lane.preservation))

    def test_postlaunch_and_final_write_failures_still_retire_direct_child(self):
        lane, child, row, _, _, launch = self.capture(persist=Mock(side_effect=[None, OSError("private"), OSError("private")]))
        launch.assert_called_once()
        self.assertEqual(row["status"], "FAILED")
        self.assertTrue(row["direct_child_reaped"])
        self.assertEqual(child.signals, ["TERM"])
        self.assertEqual(lane.preservation["failures"], 2)

    def test_cleanup_capture_continues_when_receipt_writes_fail(self):
        lane = probe.Commands({}, float("inf"), persist=Mock(side_effect=OSError("private")))
        lane.finalizing = True
        with patch.object(probe.subprocess, "Popen", side_effect=OSError("synthetic missing cleanup executable")) as launch:
            row, _, _ = lane.capture(["/reviewed/gradlew", "--stop"], "cleanup", 15)
        launch.assert_called_once()
        self.assertEqual(row["status"], "FAILED")
        self.assertGreater(lane.preservation["failures"], 0)

    def test_required_reconciliation_commands_fit_twenty_second_cleanup_stage(self):
        lane = probe.Commands({}, 20)
        lane.finalizing = True
        result = ({"status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"bound", b"")
        with patch.object(probe.time, "monotonic", return_value=0), patch.object(lane, "capture", return_value=result) as capture:
            self.assertEqual(lane.execute(["git", "rev-parse", "HEAD"]), b"bound")
        self.assertAlmostEqual(capture.call_args.args[2], 15.9)
        self.assertEqual(probe.ORIGINAL_PS_SECONDS, 15)
        lane.deadline = 4
        with patch.object(probe.time, "monotonic", return_value=0), patch.object(lane, "capture") as capture:
            with self.assertRaisesRegex(RuntimeError, "budget-exhausted"):
                lane.execute(["git", "status"])
        capture.assert_not_called()

    def test_only_exact_qualified_simulator_enumerations_restore_one_hundred_twenty_seconds(self):
        expected = {
            ("/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"): 120,
            ("/usr/bin/xcrun", "simctl", "list", "devicetypes", "--json"): 120,
        }
        self.assertEqual(probe.PLATFORM_ENUMERATION_SECONDS, expected)
        ordinary = [
            ("/usr/bin/xcrun", "simctl", "list", "devices", "--json"),
            ("/usr/bin/xcrun", "simctl", "list", "runtimes", "--json", "available"),
            ("/usr/bin/xcodebuild", "-version"), ("git", "status"),
        ]
        result = ({"status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"bound", b"")
        for command, seconds in [*expected.items(), *((item, 20) for item in ordinary)]:
            with self.subTest(command=command):
                lane = probe.Commands({}, 300)
                with patch.object(lane, "capture", return_value=result) as capture:
                    self.assertEqual(lane.execute(command), b"bound")
                capture.assert_called_once_with(list(command), "binding-or-platform", seconds, root=probe.ROOT)
        self.assertEqual(probe.ORIGINAL_PS_SECONDS, 15)
        self.assertEqual(probe.OBSERVATION_SECONDS, 300)

    def test_explicit_command_timeout_is_honored_or_rejected_not_silently_capped(self):
        lane = probe.Commands({}, 300)
        command = ["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"]
        result = ({"status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"bound", b"")
        for seconds in (10, 120):
            with patch.object(lane, "capture", return_value=result) as capture:
                self.assertEqual(lane.execute(command, timeout=seconds), b"bound")
            self.assertEqual(capture.call_args.args[2], seconds)
        for args, seconds in ((command, 121), (["git", "status"], 120), (command, 0),
                              (command, True), (command, float("nan")), (command, float("inf"))):
            with self.subTest(args=args, seconds=seconds), patch.object(lane, "capture") as capture:
                with self.assertRaisesRegex(RuntimeError, "unreviewed-probe-command-timeout"):
                    lane.execute(args, timeout=seconds)
                capture.assert_not_called()

    def test_qualified_enumeration_without_full_budget_is_skipped_not_shortened_or_retried(self):
        for command in probe.PLATFORM_ENUMERATION_SECONDS:
            lane = probe.Commands({}, 123.9)  # 120s query plus the existing 4s retirement reserve cannot fit.
            with self.subTest(command=command), patch.object(probe.time, "monotonic", return_value=0), \
                    patch.object(probe.subprocess, "Popen") as launch:
                with self.assertRaisesRegex(RuntimeError, "required-probe-command-failed"):
                    lane.execute(command)
            launch.assert_not_called()
            self.assertEqual(len(lane.rows), 1)
            self.assertEqual(lane.rows[0]["status"], "SKIPPED_BUDGET")
            self.assertEqual(lane.rows[0]["timeout_seconds"], 120)

    def test_qualified_enumeration_timeout_with_partial_bytes_stays_failed_without_retry(self):
        lane = probe.Commands({}, 300)
        result = ({"status": "TIMEOUT", "exit_code": 0, "direct_child_reaped": True}, b'{"runtimes":[]}', b"")
        with patch.object(lane, "capture", return_value=result) as capture:
            with self.assertRaisesRegex(RuntimeError, "required-probe-command-failed"):
                lane.execute(["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"])
        capture.assert_called_once()

    def test_real_qualifier_routes_both_enumeration_budgets_and_rejects_timeout_at_either_seam(self):
        developer = Path("/Applications/Xcode_26.3.app/Contents/Developer")
        sdk = developer / "Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.2.sdk"
        runtime = ("/usr/bin/xcrun", "simctl", "list", "runtimes", "--json")
        types = ("/usr/bin/xcrun", "simctl", "list", "devicetypes", "--json")
        original_is_dir = Path.is_dir
        with TemporaryDirectory() as raw:
            home, java = Path(raw).resolve() / "home", Path(raw).resolve() / "jdk/Home"
            (home / "Library/Android/sdk").mkdir(parents=True)
            (java / "bin").mkdir(parents=True)
            (java / "bin/java").touch()
            output = {
                ("/usr/bin/xcodebuild", "-version"): b"Xcode 26.3\nBuild version 17C529\n",
                ("/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"): b"26.2\n",
                ("/usr/bin/xcode-select", "-p"): str(developer).encode(),
                ("/usr/bin/xcrun", "--sdk", "iphoneos", "--show-sdk-version"): b"26.2\n",
                ("/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"): str(sdk).encode(),
                runtime: json.dumps({"runtimes": [{"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26-2",
                    "isAvailable": True, "version": "26.2", "buildversion": "23C54"}]}).encode(),
                types: json.dumps({"devicetypes": [{"identifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"}]}).encode(),
                ("/usr/libexec/java_home", "-v", "21"): str(java).encode(),
            }
            for failed in (None, runtime, types):
                lane = probe.Commands({}, 300)
                def captured(args, _label, timeout, **_kwargs):
                    command = tuple(args)
                    self.assertEqual(timeout, 120 if command in (runtime, types) else 20)
                    # Even complete-looking retained bytes cannot rescue a timeout.
                    return {"status": "TIMEOUT" if command == failed else "EXITED", "exit_code": 0,
                            "direct_child_reaped": True}, output[command], b""
                with self.subTest(failed=failed), patch.object(native.platform, "system", return_value="Darwin"), \
                        patch.object(native.platform, "machine", return_value="arm64"), \
                        patch.object(Path, "home", return_value=home), \
                        patch.object(Path, "is_dir", lambda path: path == sdk or original_is_dir(path)), \
                        patch.object(lane, "capture", side_effect=captured) as capture, \
                        patch.object(native, "command", side_effect=AssertionError("default executor forbidden")) as default, \
                        patch.object(probe.subprocess, "Popen") as launch:
                    if failed is None:
                        value = native.qualified_platform(execute=lane.execute, environment={"DEVELOPER_DIR": str(developer)})
                        self.assertEqual(value["runtime_build"], "23C54")
                        self.assertIn("no application/ABI/protection/runtime claim", value["runtime_scope"])
                    else:
                        with self.assertRaisesRegex(RuntimeError, "required-probe-command-failed"):
                            native.qualified_platform(execute=lane.execute, environment={"DEVELOPER_DIR": str(developer)})
                    calls = [tuple(call.args[0]) for call in capture.call_args_list]
                    self.assertEqual(calls.count(runtime), 1)
                    self.assertEqual(calls.count(types), 0 if failed == runtime else 1)
                    if failed is not None:
                        self.assertEqual(calls[-1], failed)
                    default.assert_not_called()
                    launch.assert_not_called()


class ProbePrivacyAndCustodyTest(unittest.TestCase):
    def test_numeric_summary_cannot_export_argv_or_promote_a_timed_out_partial_table(self):
        raw = f"{os.getpid()} 1 1 Tue Sep 8 10:00:00 2026 /private/SECRET --token=PRIVATE\n12345 1 1 Tue Sep 8 10:00:00 2026 ps\n".encode()
        row = dict(status="EXITED", exit_code=0, owned_pid=12345, stderr_bytes=0, direct_child_reaped=True)
        value = probe.ps_summary(raw, row)
        self.assertTrue(value["complete"])
        self.assertNotIn("PRIVATE", json.dumps(value))
        self.assertNotIn("SECRET", json.dumps(value))
        self.assertFalse(probe.ps_summary(raw, {**row, "status": "TIMEOUT"})["complete"])
        self.assertFalse(probe.ps_summary(raw + b"bad\n", row)["complete"])
        self.assertFalse(probe.ps_summary(raw, {**row, "stderr_bytes": 1})["complete"])
        self.assertFalse(probe.ps_summary(raw, {**row, "direct_child_reaped": False})["complete"])

    def test_stack_filter_drops_headers_private_paths_and_unbounded_or_unknown_lines(self):
        raw = b"Command: PRIVATE --token=SECRET\nCall graph:\n    10 sysctl (in libsystem_c.dylib) + 20 [0x123]\nPRIVATE /path\nBinary Images:\nSECRET /private/path\n"
        self.assertEqual(probe.stack_frames(raw), ["    10 sysctl (in libsystem_c.dylib) + 20 [0x123]"])
        self.assertEqual(probe.stack_frames(b"PRIVATE without graph"), [])
        self.assertEqual(probe.stack_frames(b"Call graph:\n" + b" " * 600 + b"10 sysctl (in libsystem_c.dylib)\n"), [])

    def test_confirmed_upload_allows_deletion_but_preserves_failed_probe_verdict(self):
        with TemporaryDirectory() as raw:
            lane = cleanup_fixture(Path(raw) / "owned")
            self.assertEqual(lane.cleanup(), 0)
            self.assertFalse(lane.base.exists())
            result = json.loads(lane.cleanup_path.read_text())
            self.assertEqual(result["probe_status"], "OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE")

    def test_no_upload_id_digest_or_success_preserves_last_evidence(self):
        for key, bad in (("PARLOR_PROBE_UPLOAD_OUTCOME", "failure"), ("PARLOR_PROBE_ARTIFACT_ID", "0"),
                         ("PARLOR_PROBE_ARTIFACT_DIGEST", "bad")):
            with self.subTest(key=key), TemporaryDirectory() as raw:
                lane = cleanup_fixture(Path(raw) / "owned")
                lane.env[key] = bad
                self.assertEqual(lane.cleanup(), 1)
                self.assertTrue((lane.bundle / "probe.json").is_file())

    def test_mutated_uploaded_bundle_or_unretired_resources_block_staging_deletion(self):
        for condition in ("mutation", "resource", "unexpected-child"):
            with self.subTest(condition=condition), TemporaryDirectory() as raw:
                lane = cleanup_fixture(Path(raw) / "owned")
                if condition == "mutation":
                    (lane.bundle / "probe.json").write_text("changed")
                elif condition == "resource":
                    lane.resources.mkdir()
                else:
                    (lane.base / "unrelated").write_text("preserve")
                self.assertEqual(lane.cleanup(), 1)
                self.assertTrue(lane.base.is_dir())

    def test_changed_source_controls_custody_or_preservation_refuses_evidence_deletion(self):
        for key, value in (("context", {"head_sha": "d" * 40}), ("control_manifest", {}),
                           ("base_custody", {}), ("preservation", {"failures": 1, "errors": []})):
            with self.subTest(key=key), TemporaryDirectory() as raw:
                lane = cleanup_fixture(Path(raw) / "owned")
                state = json.loads((lane.base / "state.json").read_text())
                (lane.base / "state.json").write_text(json.dumps({**state, key: value}))
                self.assertEqual(lane.cleanup(), 1)
                self.assertTrue((lane.bundle / "probe.json").exists())

    def test_every_lsof_warning_holder_or_invalid_empty_success_preserves_resources(self):
        cases = ((1, b"", b"WARNING PRIVATE"), (0, b"123\n", b""), (0, b"", b""), (2, b"", b""))
        for code, out, err in cases:
            with self.subTest(code=code, out=out), TemporaryDirectory() as raw:
                lane = object.__new__(probe.Probe)
                lane.resources = Path(raw) / "owned"
                lane.resources.mkdir()
                lane.state = {"resources_custody": native.custody(lane.resources)}
                lane.commands = SimpleNamespace(handles=[], capture=Mock(return_value=({"status": "EXITED", "exit_code": code,
                    "direct_child_reaped": True}, out, err)))
                with self.assertRaisesRegex(RuntimeError, "holders-or-inspection-error"):
                    lane.clean_resources()
                self.assertTrue(lane.resources.is_dir())

    def test_unreaped_or_cleanup_failed_lsof_cannot_authorize_resource_deletion(self):
        for change in ({"direct_child_reaped": False}, {"child_cleanup_error_type": "OSError"}):
            with self.subTest(change=change), TemporaryDirectory() as raw:
                lane = object.__new__(probe.Probe)
                lane.resources = Path(raw) / "owned"
                lane.resources.mkdir()
                lane.state = {"resources_custody": native.custody(lane.resources)}
                row = {"status": "EXITED", "exit_code": 1, "direct_child_reaped": True, **change}
                lane.commands = SimpleNamespace(handles=[], capture=Mock(return_value=(row, b"", b"")))
                with self.assertRaisesRegex(RuntimeError, "holders-or-inspection-error"):
                    lane.clean_resources()
                self.assertTrue(lane.resources.exists())


class ProbeLifecycleTest(unittest.TestCase):
    def synthetic_run(self, parent, *, save_failure=None, partial=False, qualification_failure=False):
        lane = object.__new__(probe.Probe)
        lane.root, home = parent / "repo", parent / "home"
        lane.root.mkdir()
        home.mkdir()
        lane.base = parent / "probe"
        lane.bundle, lane.resources = lane.base / "bundle", lane.base / "resources"
        lane.env, lane.context = {}, {"run_id": 100, "run_attempt": 1, "head_sha": "a" * 40}
        lane.control, lane.approval = {"control_sha256": "b" * 64}, "b" * 64
        lane.state, lane.claimed_base, lane.claimed_resources = None, None, None
        lane.commands = probe.Commands({"PATH": "/usr/bin:/bin", "HOME": str(home)}, float("inf"))
        lane.commands.execute = Mock(side_effect=lambda args, *_: b"15.7\n" if args[0] == "/usr/bin/sw_vers" else b"")
        events, original_save = [], lane.save
        def save():
            if save_failure == "initial" or (save_failure == "cleanup" and
                    any(row["label"] == "stop-immediate" and row["status"] == "COMPLETE" for row in lane.state["cleanup_stages"])):
                raise OSError("PRIVATE EVIDENCE PATH")
            original_save()
        def clean():
            events.append("resources")
            self.assertEqual(native.custody(lane.resources), lane.state["resources_custody"])
            probe.shutil.rmtree(lane.resources)
            return {"resources_removed": True}
        def observations(phase):
            lane.commands.rows.append(dict(label=phase, status="EXITED", exit_code=0,
                direct_child_reaped=True, observation={"complete": not partial}))
            return [phase]
        with ExitStack() as stack:
            for target, name, kwargs in (
                (probe.Path, "home", {"return_value": home}),
                (probe, "original_seam", {"return_value": {"timeout_seconds": 15}}),
                (native, "qualified_platform", {"side_effect": RuntimeError("synthetic-platform-failure")}
                    if qualification_failure else {"return_value": {"java_home": "/public/Home"}}),
                (native, "context", {"side_effect": lambda *a, **k: events.append("source") or lane.context}),
                (probe, "controls", {"return_value": lane.control}),
                (lane, "save", {"side_effect": save}),
                (lane, "ps_identity", {"return_value": {"synthetic": True}}),
                (lane, "metrics", {"return_value": {"synthetic": True}}),
                (lane, "observations", {"side_effect": observations}),
                (lane, "allocate_simulator", {"return_value": {"synthetic": True}}),
                (lane, "nonbuilding_overlap", {"return_value": {"synthetic": True}}),
                (lane, "stop_gradle", {"side_effect": lambda label: events.append(label) or {"exit_code": 0}}),
                (probe.simulator, "cleanup", {"side_effect": lambda *a: events.append("simulator") or {"result": "NOT_CREATED"}}),
                (lane, "clean_resources", {"side_effect": clean}),
            ):
                stack.enter_context(patch.object(target, name, **kwargs))
            launch = stack.enter_context(patch.object(probe.subprocess, "Popen", side_effect=AssertionError("no native commands in synthetic control")))
            result = lane.run()
            launch.assert_not_called()
        return lane, result, events

    def test_cleanup_receipt_write_failures_do_not_abort_later_independent_finalizers(self):
        with TemporaryDirectory() as raw:
            lane, code, events = self.synthetic_run(Path(raw), save_failure="cleanup")
            self.assertEqual(events, ["stop-immediate", "simulator", "stop-final", "resources", "source"])
            self.assertEqual(code, 2)
            self.assertFalse(lane.resources.exists())
            self.assertTrue(all(row["status"] == "COMPLETE" for row in lane.state["cleanup_stages"]))
            self.assertGreater(lane.commands.preservation["failures"], 0)
            evidence = json.loads((lane.bundle / "preservation-failure.json").read_text())
            self.assertEqual(evidence["status"], "OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE")
            self.assertNotIn("PRIVATE", json.dumps(evidence))

    def test_initial_checkpoint_failure_is_inside_resource_finalizer(self):
        with TemporaryDirectory() as raw:
            lane, code, events = self.synthetic_run(Path(raw), save_failure="initial")
            self.assertEqual(code, 2)
            self.assertEqual(lane.state["stages"], [])
            self.assertEqual(events, ["simulator", "resources", "source"])
            self.assertFalse(lane.resources.exists())

    def test_failed_qualification_is_named_and_blocks_all_observations_without_skipping_cleanup(self):
        with TemporaryDirectory() as raw:
            lane, code, events = self.synthetic_run(Path(raw), qualification_failure=True)
            self.assertEqual(code, 2)
            self.assertEqual(events, ["stop-immediate", "simulator", "stop-final", "resources", "source"])
            self.assertEqual([(row["label"], row["status"]) for row in lane.state["stages"]], [("qualified-platform", "FAILED")])
            self.assertNotIn("toolchain", lane.state)
            self.assertEqual(lane.commands.rows, [])
            self.assertTrue(lane.state["cleanup_safe"])
            self.assertEqual(lane.state["status"], "OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE")

    def test_partial_metadata_deliberately_fails_even_with_all_commands_and_cleanup_complete(self):
        with TemporaryDirectory() as raw:
            lane, code, _ = self.synthetic_run(Path(raw), partial=True)
            self.assertEqual(code, 2)
            self.assertTrue(lane.state["cleanup_safe"])
            self.assertTrue(all(row["status"] == "COMPLETE" for row in lane.state["stages"]))
            self.assertEqual(lane.state["status"], "OBSERVATIONS_WITH_FAILURES_NOT_RUNTIME_EVIDENCE")

    def test_complete_synthetic_glue_is_observation_only_not_an_always_fail_fixture(self):
        with TemporaryDirectory() as raw:
            lane, code, events = self.synthetic_run(Path(raw))
            self.assertEqual(code, 0)
            self.assertEqual(events, ["stop-immediate", "simulator", "stop-final", "resources", "source"])
            self.assertEqual(lane.state["status"], "OBSERVATIONS_COLLECTED_NOT_RUNTIME_EVIDENCE")
            self.assertEqual(lane.state["stages"][0]["label"], "qualified-platform")
            self.assertEqual(lane.commands.preservation, {"failures": 0, "errors": []})
            self.assertFalse(lane.resources.exists())

    def test_constructor_failure_is_signal_protected_and_retires_owned_handle(self):
        commands, child = probe.Commands({}, float("inf")), Child()
        handlers = {probe.signal.SIGINT: object(), probe.signal.SIGTERM: object()}
        previous = handlers.copy()
        def signal_set(number, handler):
            old, handlers[number] = handlers[number], handler
            return old
        def constructor(_env, *, commands):
            self.assertEqual(handlers[probe.signal.SIGTERM], commands.interrupted)
            commands.handles.append((child, {"label": "synthetic-context"}))
            commands.interrupted(probe.signal.SIGTERM, None)
            raise RuntimeError("synthetic-constructor-failure")
        with patch.object(probe, "child_environment", return_value={}), patch.object(probe, "Commands", return_value=commands), \
                patch.object(probe.signal, "signal", side_effect=signal_set), patch.object(probe, "Probe", side_effect=constructor), \
                patch.object(probe, "preserve_entry_failure", return_value=True) as preserve:
            with self.assertRaisesRegex(RuntimeError, "synthetic-constructor-failure"):
                probe.main(["run"])
        self.assertEqual(handlers, previous)
        self.assertEqual(child.signals, ["TERM"])
        self.assertTrue(commands.handles[0][1]["direct_child_reaped"])
        preserve.assert_called_once()

    def test_settings_tail_wait_notices_signal_without_waiting_sixty_seconds(self):
        with TemporaryDirectory() as raw:
            lane, child = object.__new__(probe.Probe), Child()
            lane.root = lane.resources = Path(raw)
            lane.state = {}
            lane.commands = probe.Commands({}, float("inf"))
            original_wait = child.wait
            def wait(timeout):
                if not lane.commands.signals:
                    self.assertLessEqual(timeout, 0.1)
                    lane.commands.interrupted(probe.signal.SIGTERM, None)
                    raise probe.subprocess.TimeoutExpired("synthetic-settings", timeout)
                return original_wait(timeout)
            child.wait = wait
            with patch.object(probe.subprocess, "Popen", return_value=child), patch.object(lane, "observations", return_value=[]):
                lane.nonbuilding_overlap()
            self.assertEqual(lane.commands.rows[0]["status"], "INTERRUPTED")
            self.assertTrue(lane.commands.rows[0]["direct_child_reaped"])
            self.assertEqual(child.signals, ["TERM"])


class ProbeSimulatorGlueTest(unittest.TestCase):
    def fixture(self, parent, interrupted=False):
        lane = object.__new__(probe.Probe)
        lane.prefix = parent / "owned"
        lane.claim = dict(task={"run_id": 100}, cycle=probe.SCOPE, nonce="e" * 32)
        lane.platform = dict(runtime="com.apple.CoreSimulator.SimRuntime.iOS-26-2",
                             device_type="com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro")
        lane.state, lane.commands = {}, probe.Commands({}, float("inf"))
        rows, calls = [], []
        udid = "11111111-2222-3333-4444-555555555555"
        def run(*args):
            calls.append(args)
            if args[0] == "create":
                plan = json.loads(probe.simulator.paths(lane.prefix)[0].read_text())
                self.assertEqual(args[1:], (plan["name"], lane.platform["device_type"], lane.platform["runtime"]))
                rows.append(dict(udid=udid, name=plan["name"], deviceTypeIdentifier=plan["device_type"], state="Shutdown"))
                if interrupted:
                    raise RuntimeError("synthetic-interrupted-create")
                return 0, udid
            self.assertEqual(args[1], udid)
            if args[0] in {"boot", "shutdown"}:
                rows[0]["state"] = "Booted" if args[0] == "boot" else "Shutdown"
            if args[0] == "delete":
                rows.clear()
            return 0, ""
        lane.backend = SimpleNamespace(inventory=lambda: {"devices": {lane.platform["runtime"]: rows}}, run=Mock(side_effect=run))
        return lane, calls

    def test_exact_qualified_simulator_is_journaled_once_before_boot_and_retired_by_uuid(self):
        with TemporaryDirectory() as raw:
            lane, calls = self.fixture(Path(raw))
            self.assertTrue(lane.allocate_simulator()["boot_completed"])
            self.assertEqual([row[0] for row in calls], ["create", "boot", "bootstatus"])
            self.assertEqual(probe.simulator.cleanup(lane.prefix, lane.claim, lane.backend)["result"], "PASS")
            self.assertEqual([row[0] for row in calls], ["create", "boot", "bootstatus", "shutdown", "delete"])

    def test_failed_plan_write_never_creates_and_interrupted_create_reuses_only_attested_intent(self):
        with TemporaryDirectory() as raw:
            lane, calls = self.fixture(Path(raw))
            with patch.object(native, "write_new", side_effect=OSError("synthetic-plan-write")):
                with self.assertRaises(OSError):
                    lane.allocate_simulator()
            self.assertEqual(calls, [])
        with TemporaryDirectory() as raw:
            lane, calls = self.fixture(Path(raw), interrupted=True)
            with self.assertRaisesRegex(RuntimeError, "interrupted-create"):
                lane.allocate_simulator()
            self.assertFalse(probe.simulator.paths(lane.prefix)[1].exists())
            result = probe.simulator.cleanup(lane.prefix, lane.claim, lane.backend)
            self.assertTrue(result["recovered_from_intent"])
            self.assertEqual([row[0] for row in calls], ["create", "delete"])

    def test_bootstatus_uses_evidence_based_bound_without_changing_original_ps(self):
        commands = SimpleNamespace(capture=Mock(return_value=({"status": "EXITED", "exit_code": 0, "direct_child_reaped": True}, b"", b"")))
        probe.SimBackend(commands).run("bootstatus", "11111111-2222-3333-4444-555555555555", "-b")
        self.assertEqual(commands.capture.call_args.args[2], 120)
        self.assertEqual(probe.ORIGINAL_PS_SECONDS, 15)


class NativeMetadataObservationTest(unittest.TestCase):
    def backend(self, denied=False, short=False):
        own, other = os.getpid(), os.getpid() + 10000
        class Backend:
            def proc_listpids(self, selector, uid, values, size):
                assert (selector, uid) == (1, 0)
                values[0], values[1] = own, other
                return 2 * ctypes.sizeof(ctypes.c_int)

            def proc_pidinfo(self, pid, flavor, arg, pointer, size):
                assert flavor == 3 and arg == 0
                if pid == other and denied:
                    ctypes.set_errno(errno.EPERM)
                    return 0
                if short:
                    return size - 1
                value = ctypes.cast(pointer, ctypes.POINTER(metadata.ProcBsdInfo)).contents
                value.pid, value.ppid, value.pgid = pid, os.getppid(), os.getpgid(0)
                value.uid, value.start_sec, value.start_usec = os.geteuid(), 100, 123
                return size
        return Backend()

    def test_actual_struct_layout_and_self_fields_are_observed_without_task_or_argv_api(self):
        value = metadata.collect(self.backend())
        self.assertEqual(value["struct_size"], 136)
        self.assertEqual(value["field_offsets"]["pgid"], 100)
        self.assertEqual(value["own_process"]["pid"], os.getpid())
        self.assertTrue(value["complete"])
        self.assertEqual(value["observed"], 2)

    def test_permission_denial_is_explicit_partial_not_a_missing_or_cleaned_worker(self):
        value = metadata.collect(self.backend(denied=True))
        self.assertFalse(value["complete"])
        self.assertEqual(value["error_counts"], {"denied:" + str(errno.EPERM): 1})
        self.assertEqual(value["observed"], 1)

    def test_short_self_metadata_cannot_be_a_successful_observation(self):
        with self.assertRaisesRegex(RuntimeError, "self-bsd-metadata"):
            metadata.collect(self.backend(short=True))

    def test_pid_enumeration_truncation_or_duplicate_rows_fail_closed(self):
        backend = self.backend()
        backend.proc_listpids = lambda *args: (metadata.MAX_PROCESSES + 1) * ctypes.sizeof(ctypes.c_int)
        with self.assertRaisesRegex(RuntimeError, "unbounded-global"):
            metadata.collect(backend)
        def duplicate(_selector, _uid, values, _size):
            values[0] = values[1] = os.getpid()
            return 2 * ctypes.sizeof(ctypes.c_int)
        backend.proc_listpids = duplicate
        with self.assertRaisesRegex(RuntimeError, "invalid-global"):
            metadata.collect(backend)


if __name__ == "__main__":
    unittest.main()
