"""Synthetic ownership controls only; never launches a native worker or simulator."""
from __future__ import annotations

import ctypes
import hashlib
import json
import os
import struct
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple
from scripts.ci import darwin_worker_identity as native
from scripts.ci import owned_ci_simulator as sim
from scripts.ci import verification_hygiene as hygiene

UID = getattr(os, "getuid", lambda: 501)()
TRACKING = "github_01234567-89ab-cdef-0123-456789abcdef"
APPLICATION = Path("/Applications/Xcode_26.3.app")
WORKER = str(APPLICATION / apple.WORKER_PATHS[1])
TASK = {"GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_JOB": "ios"}
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-18-5"
DEVICE_TYPE = "com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro"
EXISTING = "11111111-1111-1111-1111-111111111111"
CREATED = "22222222-2222-2222-2222-222222222222"
OTHER = "33333333-3333-3333-3333-333333333333"


def identity(pid=100, started=(2, 0), command=WORKER, generation=1):
    return native.ProcessIdentity(pid, UID, started, command,
                                  (0, UID, 0, UID, 0, pid, 0, generation))


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class Workers:
    def __init__(self, values=(), marker=TRACKING, retire_on=15):
        self.values = {value.pid: value for value in values}
        self.marker, self.retire_on = marker, retire_on
        self.signals = []

    def lifetimes(self):
        return [value.lifetime for value in self.values.values()]

    def snapshot(self, application, baseline):
        return list(self.values.values())

    def read(self, pid):
        return self.values.get(pid)

    def tracking_id(self, value):
        return self.marker

    def signal(self, value, signum):
        if self.read(value.pid) != value:
            raise RuntimeError("Synthetic identity changed before signal")
        self.signals.append((value, signum))
        if signum == self.retire_on:
            self.values.pop(value.pid, None)
        return True


class WorkerOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.claim = {"application": str(APPLICATION), "workers": [WORKER], "baseline": [],
                      "not_before_us": 1000000, "uid": UID,
                      "tracking_sha256": apple.digest(TRACKING)}
        self.clock = Clock()

    def stop(self, backend):
        return apple.stop_workers(self.claim, backend, TRACKING, self.clock, self.clock.sleep)

    def test_exact_owned_worker_receives_token_bound_term_and_is_observed_absent(self):
        backend = Workers([identity()])
        receipt = self.stop(backend)
        self.assertEqual(receipt["result"], "PASS", receipt)
        self.assertEqual(backend.signals, [(identity(), 15)])
        self.assertFalse(backend.values)
        self.assertEqual(len(receipt["events"]), 1)

    def test_preexisting_lifetime_never_receives_signal_even_with_matching_marker(self):
        value = identity()
        self.claim["baseline"] = [value.lifetime]
        backend = Workers([value])
        self.assertEqual(self.stop(backend)["result"], "PASS")
        self.assertEqual(backend.signals, [])
        self.assertIn(value.pid, backend.values)

    def test_baseline_scan_race_does_not_claim_process_predating_cycle(self):
        backend = Workers([identity(started=(0, 999999))])
        self.assertEqual(self.stop(backend)["result"], "PASS")
        self.assertEqual(backend.signals, [])

    def test_different_marker_is_excluded_without_substring_matching(self):
        for marker in ("prefix" + TRACKING, TRACKING + "suffix", "other-job"):
            with self.subTest(marker=marker):
                backend = Workers([identity()], marker=marker)
                receipt = self.stop(backend)
                self.assertEqual(receipt["result"], "PASS")
                self.assertEqual(receipt["excluded_lifetimes"], 1)
                self.assertEqual(backend.signals, [])

    def test_missing_marker_is_explicit_failure_not_ownership(self):
        backend = Workers([identity()], marker=None)
        receipt = self.stop(backend)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertIn("no readable", receipt["errors"][0]["error"])
        self.assertEqual(backend.signals, [])

    def test_unknown_matching_xcode_executable_fails_without_signal(self):
        backend = Workers([identity(command=str(APPLICATION / "Contents/unknown-worker"))])
        receipt = self.stop(backend)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertIn("Unrecognized", receipt["errors"][0]["error"])
        self.assertEqual(backend.signals, [])

    def test_denied_identity_api_fails_without_numeric_pid_fallback(self):
        backend = Workers([identity()])
        backend.snapshot = Mock(side_effect=RuntimeError("Native API denied"))
        self.assertEqual(self.stop(backend)["result"], "FAIL")
        self.assertEqual(backend.signals, [])

    def test_generation_or_executable_change_revokes_admitted_grant(self):
        for changed in (identity(generation=2), identity(command="/unrelated/executable")):
            with self.subTest(changed=changed):
                backend = Workers([identity()], retire_on=None)
                original = backend.signal

                def change(value, signum):
                    original(value, signum)
                    backend.values[value.pid] = changed
                    return True

                backend.signal = change
                receipt = self.stop(backend)
                self.assertEqual(receipt["result"], "FAIL")
                self.assertEqual(backend.signals, [(identity(), 15)])

    def test_pid_reuse_before_first_signal_does_not_inherit_tracking_proof(self):
        backend = Workers([identity()])

        def recycled(value):
            backend.values[value.pid] = identity(started=(3, 0), generation=2)
            return TRACKING

        backend.tracking_id = recycled
        receipt = self.stop(backend)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(backend.signals, [])

    def test_matching_worker_can_require_kill_and_survivor_is_failure(self):
        for retire_on, expected in ((9, "PASS"), (None, "FAIL")):
            with self.subTest(retire_on=retire_on):
                backend = Workers([identity()], retire_on=retire_on)
                receipt = self.stop(backend)
                self.assertEqual(receipt["result"], expected, receipt)
                self.assertEqual([signum for _, signum in backend.signals], [15, 9])
                self.assertLessEqual(self.clock.now, 25)

    def test_flood_ceiling_fails_before_signaling(self):
        backend = Workers([identity(pid=100 + index) for index in range(apple.MAX_WORKERS + 1)])
        self.assertEqual(self.stop(backend)["result"], "FAIL")
        self.assertEqual(backend.signals, [])

    def test_changed_runner_marker_cannot_authorize_cleanup(self):
        backend = Workers([identity()])
        with self.assertRaisesRegex(RuntimeError, "tracking identity changed"):
            apple.stop_workers(self.claim, backend, TRACKING + "changed", self.clock, self.clock.sleep)
        self.assertEqual(backend.signals, [])


class NativeParsingAndSignalTest(unittest.TestCase):
    def record(self, args=(b"worker",), environment=()):
        return struct.pack("=i", len(args)) + b"/worker\0\0" + b"\0".join(args) + b"\0" + b"\0".join(environment) + b"\0"

    def full_argument_record(self):
        raw = self.record(environment=(b"RUNNER_TRACKING_ID=" + TRACKING.encode(), b"PAD="))
        return raw[:-1] + b"x" * (native.MAX_ARGUMENT_RECORD_BYTES - len(raw)) + b"\0"

    def test_argument_capacity_reserves_argc_beyond_the_payload_limit(self):
        for payload in (native.ARGC_BYTES, 256 * 1024, 1024 * 1024):
            with self.subTest(payload=payload):
                self.assertEqual(native.argument_record_capacity(payload), payload + struct.calcsize("=i"))
        self.assertEqual(native.argument_record_capacity(1024 * 1024), 1048580)

    def test_argument_capacity_rejects_invalid_or_excessive_payload_limits(self):
        for payload in (-1, 0, native.ARGC_BYTES - 1, native.MAX_ARGUMENT_BYTES + 1, 2 * native.MAX_ARGUMENT_BYTES):
            with self.subTest(payload=payload):
                with self.assertRaisesRegex(RuntimeError, "bounded process-argument capacity"):
                    native.argument_record_capacity(payload)

    def test_full_argmax_record_is_accepted_but_an_extra_payload_byte_is_not(self):
        raw = self.full_argument_record()
        self.assertEqual(len(raw), 1024 * 1024 + struct.calcsize("=i"))
        self.assertEqual(native.parse_tracking_id(raw), TRACKING)
        with self.assertRaisesRegex(RuntimeError, "bounded process-argument record"):
            native.parse_tracking_id(raw[:-1] + b"x\0")

    def test_tracking_read_uses_the_bounded_argc_prefixed_capacity(self):
        backend = object.__new__(native.DarwinWorkerBackend)
        backend.read = Mock(return_value=identity())
        backend.argument_capacity = native.argument_record_capacity(1024 * 1024)
        backend.lib = Mock()
        raw = self.full_argument_record()

        def sysctl(mib, count, buffer, size_pointer, new_data, new_size):
            self.assertEqual(tuple(mib), (1, 49, identity().pid))
            self.assertEqual(count, 3)
            self.assertEqual((new_data, new_size), (None, 0))
            size = ctypes.cast(size_pointer, ctypes.POINTER(ctypes.c_size_t)).contents
            self.assertEqual(size.value, 1048580)
            self.assertEqual(len(buffer), size.value)
            ctypes.memmove(buffer, raw, len(raw))
            size.value = len(raw)
            return 0

        backend.lib.sysctl.side_effect = sysctl
        self.assertEqual(backend.tracking_id(identity()), TRACKING)
        backend.lib.sysctl.assert_called_once()
        self.assertEqual(backend.read.call_count, 2)

    def test_only_exact_environment_entry_not_argv_or_suffix_is_a_marker(self):
        raw = self.record(args=(b"worker", b"RUNNER_TRACKING_ID=argv"), environment=(
            b"OTHER_RUNNER_TRACKING_ID=wrong", b"RUNNER_TRACKING_ID=" + TRACKING.encode()))
        self.assertEqual(native.parse_tracking_id(raw), TRACKING)
        self.assertIsNone(native.parse_tracking_id(self.record(args=(b"RUNNER_TRACKING_ID=argv",))))

    def test_empty_argument_does_not_shift_environment(self):
        raw = self.record(args=(b"worker", b"", b"third"), environment=(b"RUNNER_TRACKING_ID=" + TRACKING.encode(),))
        self.assertEqual(native.parse_tracking_id(raw), TRACKING)

    def test_duplicate_truncated_invalid_and_oversized_records_are_rejected(self):
        marker = b"RUNNER_TRACKING_ID=" + TRACKING.encode()
        malformed = (self.record(environment=(marker, marker)), self.record()[:-1] + b"x",
                     struct.pack("=i", -1) + b"x\0", struct.pack("=i", 2) + b"x\0y\0",
                     self.record(environment=(b"RUNNER_TRACKING_ID=\xff",)),
                     b"x" * (native.MAX_ARGUMENT_RECORD_BYTES + 1))
        for raw in malformed:
            with self.subTest(length=len(raw)):
                with self.assertRaises(RuntimeError):
                    native.parse_tracking_id(raw)

    def test_native_signal_rechecks_identity_before_kernel_call(self):
        backend = object.__new__(native.DarwinWorkerBackend)
        backend.proc = Mock()
        for current in (None, identity(generation=2), identity(command="/other")):
            with self.subTest(current=current):
                backend.read = Mock(return_value=current)
                if current is None:
                    self.assertFalse(backend.signal(identity(), 15))
                else:
                    with self.assertRaisesRegex(RuntimeError, "identity changed"):
                        backend.signal(identity(), 15)
                backend.proc.proc_signal_with_audittoken.assert_not_called()

    def test_successful_native_signal_passes_exact_audit_token_not_pid(self):
        backend = object.__new__(native.DarwinWorkerBackend)
        backend.read = Mock(return_value=identity())
        seen = []
        backend.proc = Mock()
        backend.proc.proc_signal_with_audittoken.side_effect = lambda token, sig: seen.append((tuple(token), sig)) or 0
        self.assertTrue(backend.signal(identity(), 15))
        self.assertEqual(seen, [(identity().token, 15)])

    def test_null_task_port_retries_boundedly_but_denial_does_not(self):
        backend = object.__new__(native.DarwinWorkerBackend)
        backend.self_port = 123
        backend._basic = Mock(return_value=object())
        backend.lib = Mock()
        calls = []

        def task_name(self_port, pid, output):
            calls.append(pid)
            ctypes.cast(output, ctypes.POINTER(ctypes.c_uint32)).contents.value = 0
            return 0

        backend.lib.task_name_for_pid.side_effect = task_name
        with patch.object(native.time, "sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "task-name port"):
                backend._token(100)
            self.assertEqual(len(calls), 3)
            self.assertEqual(sleep.call_count, 2)
        backend.lib.task_name_for_pid.side_effect = lambda *args: 5
        backend.lib.task_name_for_pid.reset_mock()
        with self.assertRaisesRegex(RuntimeError, "task-name port"):
            backend._token(100)
        self.assertEqual(backend.lib.task_name_for_pid.call_count, 1)


class NativeInventoryAndBasicInfoTest(unittest.TestCase):
    """Exercise actual ctypes-facing methods without loading native libraries."""

    def setUp(self):
        self.self_pid = 9000
        self.uid = 501
        self.pid_bytes = ctypes.sizeof(ctypes.c_int)
        self.capacity = (native.MAX_PROCESSES + 1) * self.pid_bytes
        self.backend = object.__new__(native.DarwinWorkerBackend)
        # No global-enumeration method exists on this synthetic native boundary.
        self.backend.proc = Mock(spec=("proc_listpids", "proc_pidinfo", "proc_signal_with_audittoken"))
        self.addCleanup(ctypes.set_errno, ctypes.get_errno())
        for name, value in (("geteuid", self.uid), ("getuid", self.uid), ("getpid", self.self_pid)):
            replacement = patch.object(native.os, name, return_value=value, create=True)
            replacement.start()
            self.addCleanup(replacement.stop)

    def inventory(self, pids, returned_bytes=None, error=0):
        def listpids(selector, uid, pointer, capacity):
            self.assertEqual(selector, 4)
            self.assertEqual(uid, self.uid)
            self.assertEqual(capacity, self.capacity)
            self.assertEqual(ctypes.sizeof(pointer), capacity)
            values = ctypes.cast(pointer, ctypes.POINTER(ctypes.c_int))
            for index, pid in enumerate(pids):
                values[index] = pid
            ctypes.set_errno(error)
            return len(pids) * self.pid_bytes if returned_bytes is None else returned_bytes
        self.backend.proc.proc_listpids.side_effect = listpids

    def basic(self, *, returned_pid=100, returned_size=None, error=0, status=2, uid=501):
        def pidinfo(pid, flavor, argument, pointer, capacity):
            self.assertEqual((pid, flavor, argument), (100, 3, 0))
            self.assertEqual(capacity, ctypes.sizeof(native.ProcBsdInfo))
            info = ctypes.cast(pointer, ctypes.POINTER(native.ProcBsdInfo)).contents
            info.pid, info.uid, info.status = returned_pid, uid, status
            info.start_sec, info.start_usec = 2, 3
            info.comm, info.name = b"NO_COMMAND_LOG", b"NO_ENVIRONMENT_OR_TRACKING_LOG"
            ctypes.set_errno(error)
            return capacity if returned_size is None else returned_size
        self.backend.proc.proc_pidinfo.side_effect = pidinfo

    def test_constructor_binds_uid_selector_and_byte_buffer_pointer_abi(self):
        proc = Mock(spec=("proc_pidinfo", "proc_pidpath", "proc_listpids", "proc_signal_with_audittoken"))
        lib = Mock()
        value = native.ProcessIdentity(self.self_pid, self.uid, (2, 0), "/synthetic/python", (0,) * 5 + (self.self_pid, 0, 1))
        with patch.object(native.platform, "system", return_value="Darwin"), \
                patch.object(native.platform, "machine", return_value="arm64"), \
                patch.object(native.ctypes, "CDLL", side_effect=[lib, proc]) as load, \
                patch.object(native.ctypes.c_uint32, "in_dll", return_value=ctypes.c_uint32(123)), \
                patch.object(native.os, "sysconf", return_value=1024 * 1024, create=True), \
                patch.object(native.DarwinWorkerBackend, "read", return_value=value):
            backend = native.DarwinWorkerBackend()
        self.assertIs(backend.proc, proc)
        self.assertEqual(load.call_count, 2)
        self.assertEqual(proc.proc_listpids.argtypes,
                         [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int])
        self.assertIs(proc.proc_listpids.restype, ctypes.c_int)
        proc.proc_listpids.assert_not_called()

    def test_inventory_uses_effective_uid_and_byte_count_not_global_scan(self):
        native.os.getuid.return_value = 777  # Selector is effective, not real UID.
        self.inventory([self.self_pid, 100, 101])
        self.assertEqual(self.backend._pids(), [100, 101])
        self.backend.proc.proc_listpids.assert_called_once()
        self.backend.proc.proc_pidinfo.assert_not_called()

    def test_self_only_inventory_is_a_valid_empty_worker_set(self):
        self.inventory([self.self_pid])
        self.assertEqual(self.backend._pids(), [])

    def test_failed_zero_negative_unaligned_and_over_capacity_counts_fail_closed(self):
        sizes = (-1, 0, 1, 3, 5, self.capacity - 1, self.capacity + self.pid_bytes)
        for size in sizes:
            with self.subTest(returned_bytes=size):
                self.inventory([self.self_pid], returned_bytes=size, error=native.errno.EPERM)
                with self.assertRaisesRegex(RuntimeError, "bounded native worker inventory"):
                    self.backend._pids()

    def test_exact_maximum_is_accepted_but_full_buffer_truncation_is_not(self):
        pids = list(range(1, native.MAX_PROCESSES + 1))
        self.inventory(pids)
        self.assertEqual(self.backend._pids(), pids)
        self.inventory(pids + [native.MAX_PROCESSES + 1])
        with self.assertRaisesRegex(RuntimeError, "bounded native worker inventory"):
            self.backend._pids()

    def test_negative_and_duplicate_pid_records_are_not_normalized_away(self):
        for pids in ([self.self_pid, -1], [self.self_pid, 100, 100]):
            with self.subTest(pids=pids):
                self.inventory(pids)
                with self.assertRaisesRegex(RuntimeError, "Invalid native process inventory"):
                    self.backend._pids()

    def test_inventory_diagnostic_retains_only_numeric_boundary_failure(self):
        self.inventory([self.self_pid], returned_bytes=0, error=native.errno.EPERM)
        with self.assertRaises(RuntimeError) as failure:
            self.backend._pids()
        self.assertEqual(str(failure.exception),
                         "Cannot obtain bounded native worker inventory "
                         f"(uid=501, bytes=0, capacity={self.capacity}, errno={native.errno.EPERM})")

    def test_lifetimes_reads_only_kernel_uid_selected_processes(self):
        self.inventory([self.self_pid, 100])
        self.basic()
        self.assertEqual(self.backend.lifetimes(), [(100, self.uid, (2, 3))])
        self.backend.proc.proc_pidinfo.assert_called_once()

    def test_snapshot_preserves_baseline_exclusion_and_full_identity_read(self):
        self.inventory([self.self_pid, 100, 101])
        def basic(pid):
            value = native.ProcBsdInfo()
            value.uid, value.pid, value.start_sec, value.start_usec = self.uid, pid, 2, 3
            return value
        self.backend._basic = Mock(side_effect=basic)
        self.backend._path = Mock(return_value=WORKER)
        current = native.ProcessIdentity(101, self.uid, (2, 3), WORKER, (0,) * 5 + (101, 0, 1))
        self.backend.read = Mock(return_value=current)
        self.assertEqual(self.backend.snapshot(APPLICATION, {(100, self.uid, (2, 3))}), [current])
        self.backend._path.assert_called_once_with(101)
        self.backend.read.assert_called_once_with(101)

    def test_uid_change_after_kernel_selection_is_still_excluded(self):
        self.inventory([self.self_pid, 100])
        self.basic(uid=0)
        self.backend._path = Mock(side_effect=AssertionError("must not inspect another UID's executable"))
        self.assertEqual(self.backend.lifetimes(), [])
        self.assertEqual(self.backend.snapshot(APPLICATION, set()), [])
        self.backend._path.assert_not_called()

    def test_only_actual_absence_or_zombie_can_be_no_basic_identity(self):
        for error in (native.errno.ESRCH, native.errno.ENOENT):
            with self.subTest(error=error):
                self.basic(returned_size=0, error=error)
                self.assertIsNone(self.backend._basic(100))
        self.basic(status=5)
        self.assertIsNone(self.backend._basic(100))
        self.basic()
        value = self.backend._basic(100)
        self.assertEqual((value.pid, value.uid, value.start_sec, value.start_usec), (100, self.uid, 2, 3))

    def test_denial_short_record_and_wrong_pid_remain_explicit_numeric_failures(self):
        variants = ((0, native.errno.EPERM, 100), (0, native.errno.EACCES, 100),
                    (0, 0, 100), (135, 0, 100), (136, 0, 101))
        for size, error, pid in variants:
            with self.subTest(size=size, error=error, pid=pid):
                self.basic(returned_size=size, error=error, returned_pid=pid)
                with self.assertRaises(RuntimeError) as failure:
                    self.backend._basic(100)
                self.assertEqual(str(failure.exception),
                                 "Cannot read complete native process identity "
                                 f"(pid=100, size={size}, expected=136, returned_pid={pid}, errno={error})")
                self.assertNotIn("NO_COMMAND_LOG", str(failure.exception))
                self.assertNotIn("NO_ENVIRONMENT_OR_TRACKING_LOG", str(failure.exception))

    def test_denied_selected_candidate_never_becomes_missing_or_cleanup_success(self):
        self.inventory([self.self_pid, 100])
        self.basic(returned_size=0, error=native.errno.EPERM)
        for operation in (self.backend.lifetimes, lambda: self.backend.snapshot(APPLICATION, set()),
                          lambda: self.backend.read(100)):
            with self.subTest(operation=operation):
                with self.assertRaisesRegex(RuntimeError, "Cannot read complete native process identity"):
                    operation()
        claim = {"application": str(APPLICATION), "workers": [WORKER], "baseline": [],
                 "not_before_us": 1000000, "uid": self.uid, "tracking_sha256": apple.digest(TRACKING)}
        clock = Clock()
        receipt = apple.stop_workers(claim, self.backend, TRACKING, clock, clock.sleep)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["events"], [])
        self.assertIn("errno=", receipt["errors"][0]["error"])
        self.backend.proc.proc_signal_with_audittoken.assert_not_called()


class Simulators:
    def __init__(self, prefix):
        self.prefix, self.calls = prefix, []
        self.items = [{"udid": EXISTING, "name": "iPhone 16 Pro", "deviceTypeIdentifier": DEVICE_TYPE,
                       "runtime": RUNTIME, "isAvailable": True, "state": "Shutdown"}]
        self.creation_code, self.returned_uuid, self.interrupt = 0, CREATED, False
        self.shutdown_works, self.delete_works = True, True

    def inventory(self):
        result = {}
        for item in self.items:
            result.setdefault(item["runtime"], []).append(dict(item))
        return {"devices": result}

    def run(self, *args):
        self.calls.append(args)
        if args[0] == "create":
            # Executable proof that intent is durable/readable before create.
            plan = sim.read_record(sim.paths(self.prefix)[0])
            if plan["name"] != args[1] or EXISTING not in plan["baseline"]:
                raise AssertionError("Create preceded journal or used wrong identity")
            if self.creation_code == 0:
                self.items.append({"udid": CREATED, "name": args[1], "deviceTypeIdentifier": args[2],
                                   "runtime": args[3], "isAvailable": True, "state": "Shutdown"})
            if self.interrupt:
                raise KeyboardInterrupt("Synthetic cancellation after create request")
            return self.creation_code, self.returned_uuid
        if args[0] == "shutdown" and self.shutdown_works:
            for item in self.items:
                if item["udid"] == args[1]:
                    item["state"] = "Shutdown"
            return 0, ""
        if args[0] == "delete" and self.delete_works:
            self.items = [item for item in self.items if item["udid"] != args[1]]
            return 0, ""
        return 1, ""


class SimulatorOwnershipTest(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory(prefix="parlor-ci-simulator-control-")
        self.addCleanup(temp.cleanup)
        self.prefix = Path(temp.name).resolve() / "owned"
        self.claim = {"task": TASK, "cycle": "apple-ui", "nonce": "a" * 32}
        self.backend = Simulators(self.prefix)

    def create(self):
        return sim.create(self.prefix, self.claim, self.backend)

    def cleanup(self):
        return sim.cleanup(self.prefix, self.claim, self.backend, sleep=lambda _: None)

    def test_fresh_device_reuses_only_type_runtime_not_profile_and_cleans_exact_uuid(self):
        self.assertEqual(self.create(), CREATED)
        self.assertEqual(self.backend.calls[0], ("create", "Parlor-ci-" + "a" * 32, DEVICE_TYPE, RUNTIME))
        self.backend.items[1]["state"] = "Booted"
        self.assertEqual(self.cleanup()["result"], "PASS")
        self.assertEqual(self.backend.calls[1:], [("shutdown", CREATED), ("delete", CREATED)])
        self.assertEqual([item["udid"] for item in self.backend.items], [EXISTING])
        self.assertTrue(sim.paths(self.prefix)[0].is_file())
        self.assertEqual(self.cleanup()["already_absent"], True)

    def test_failed_create_without_device_is_not_fabricated_cleanup_success(self):
        self.backend.creation_code = 1
        with self.assertRaisesRegex(RuntimeError, "create did not"):
            self.create()
        with self.assertRaisesRegex(RuntimeError, "Unsettled"):
            self.cleanup()
        self.assertFalse(any(call[0] in {"delete", "shutdown"} for call in self.backend.calls))

    def test_interrupted_create_after_commit_is_recovered_from_unique_intent(self):
        self.backend.interrupt = True
        with self.assertRaises(KeyboardInterrupt):
            self.create()
        self.assertFalse(sim.paths(self.prefix)[1].exists())
        receipt = self.cleanup()
        self.assertEqual(receipt["result"], "PASS")
        self.assertTrue(receipt["recovered_from_intent"])
        self.assertEqual([item["udid"] for item in self.backend.items], [EXISTING])
        self.assertEqual(self.cleanup()["already_absent"], True)

    def test_duplicate_name_or_changed_device_identity_is_never_deleted(self):
        self.create()
        original = dict(self.backend.items[1])
        for change in ({"runtime": RUNTIME + "-wrong"}, {"deviceTypeIdentifier": "wrong"}, {"name": "renamed"}):
            with self.subTest(change=change):
                self.backend.items[1] = {**original, **change}
                with self.assertRaises(RuntimeError):
                    self.cleanup()
        self.backend.items[1] = original
        self.backend.items.append({**original, "udid": OTHER})
        with self.assertRaisesRegex(RuntimeError, "Ambiguous"):
            self.cleanup()
        self.assertFalse(any(call[0] == "delete" for call in self.backend.calls))

    def test_existing_name_prevents_creation_and_existing_uuid_is_never_claimed(self):
        self.backend.items[0]["name"] = "Parlor-ci-" + self.claim["nonce"]
        self.backend.items.append({"udid": OTHER, "name": "iPhone fixture", "deviceTypeIdentifier": DEVICE_TYPE,
                                   "runtime": RUNTIME, "isAvailable": True, "state": "Shutdown"})
        with self.assertRaisesRegex(RuntimeError, "existing simulator"):
            self.create()
        self.assertEqual(self.backend.calls, [])
        self.backend.items[0]["name"] = "iPhone 16 Pro"
        self.backend.returned_uuid = EXISTING
        with self.assertRaisesRegex(RuntimeError, "predates"):
            self.create()
        with self.assertRaisesRegex(RuntimeError, "predates"):
            self.cleanup()
        self.assertFalse(any(call[0] == "delete" for call in self.backend.calls))

    def test_historical_journal_cannot_be_reused_for_creation_or_other_task(self):
        self.create()
        with self.assertRaisesRegex(RuntimeError, "historical"):
            self.create()
        self.claim = {**self.claim, "nonce": "b" * 32}
        with self.assertRaisesRegex(RuntimeError, "another cycle"):
            self.cleanup()

    def test_no_intent_is_not_created_but_orphan_receipt_fails(self):
        self.assertEqual(self.cleanup()["result"], "NOT_CREATED")
        sim.paths(self.prefix)[1].write_text("{}")
        with self.assertRaisesRegex(RuntimeError, "without pre-create"):
            self.cleanup()

    def test_shutdown_failure_retains_device_and_does_not_delete_active_profile(self):
        self.create()
        self.backend.items[1]["state"] = "Booted"
        self.backend.shutdown_works = False
        with self.assertRaisesRegex(RuntimeError, "Shutdown"):
            self.cleanup()
        self.assertFalse(any(call[0] == "delete" for call in self.backend.calls))

    def test_delete_survivor_fails_and_unexpected_clone_is_not_name_claimed(self):
        self.create()
        self.backend.delete_works = False
        with self.assertRaisesRegex(RuntimeError, "remains"):
            self.cleanup()
        self.backend.items.append({**self.backend.items[1], "udid": OTHER,
                                   "name": "Clone 1 of " + self.backend.items[1]["name"]})
        before = len(self.backend.calls)
        with self.assertRaisesRegex(RuntimeError, "unjournaled simulator clone"):
            self.cleanup()
        self.assertEqual(len(self.backend.calls), before)


class AppleCycleAndOutputGateTest(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory(prefix="parlor-apple-ci-cycle-control-")
        self.addCleanup(temp.cleanup)
        self.base = Path(temp.name).resolve()
        self.root = self.base / "checkout"
        self.root.mkdir()
        self.prefix = self.base / "ci"
        self.claim_path = self.base / "ci-ownership.json"
        self.source = {"root": str(self.root), "head": "a" * 40, "tree": "b" * 40}
        self.identity = {**self.source, "outputs": ["build"]}
        self.upload = {"outcome": "success", "artifact_id": "123", "artifact_digest": "c" * 64}
        self.config = {"application": str(APPLICATION), "developer_dir": str(APPLICATION / "Contents/Developer"),
                       "workers": [WORKER], "tracking_sha256": apple.digest(TRACKING), "uid": UID}
        for target, replacement in ((hygiene, {"source_identity": Mock(return_value=self.identity),
                                              "git": Mock(return_value=""),
                                              "stop_gradle": Mock(return_value={"exit_code": 0})}),
                                    (apple, {"configuration": Mock(return_value=self.config)})):
            current = patch.multiple(target, **replacement)
            current.start()
            self.addCleanup(current.stop)
        current = patch.dict(os.environ, {"RUNNER_TRACKING_ID": TRACKING})
        current.start()
        self.addCleanup(current.stop)
        hygiene.prepare(self.root, self.claim_path, TASK)
        (self.root / "build").mkdir()
        (self.root / "build/test.xml").write_text("preserve required evidence")

    def prepare_cycle(self, cycle):
        prefix = apple.cycle_prefix(self.prefix, cycle)
        apple.prepare(self.root, prefix, TASK, cycle, Workers(), self.config)
        return prefix

    def test_cancelled_build_finalizes_gradle_workers_device_workers_in_order(self):
        prefix = self.prepare_cycle("apple-ui")
        order = []
        hygiene.stop_gradle.side_effect = lambda root: order.append("gradle") or {"exit_code": 0}
        with patch.object(apple, "stop_workers", side_effect=lambda *args: order.append("workers") or {"result": "PASS"}), \
                patch.object(sim, "cleanup", side_effect=lambda *args: order.append("device") or {"result": "PASS"}):
            receipt = apple.finish(self.root, prefix, TASK, "apple-ui", "success", "cancelled",
                                   backend_factory=Workers, simulator_factory=Mock)
        self.assertEqual(receipt["result"], "PASS", receipt)
        self.assertEqual(receipt["run_outcome"], "cancelled")
        self.assertEqual(order, ["gradle", "workers", "device", "workers"])

    def test_unquiesced_workers_prevent_device_deletion(self):
        prefix = self.prepare_cycle("apple-ui")
        with patch.object(apple, "stop_workers", return_value={"result": "FAIL"}), patch.object(sim, "cleanup") as cleanup:
            receipt = apple.finish(self.root, prefix, TASK, "apple-ui", "success", "failure", backend_factory=Workers)
        self.assertEqual(receipt["result"], "FAIL")
        cleanup.assert_not_called()

    def test_failed_prepare_cannot_adopt_old_same_task_claim_and_skipped_is_not_pass(self):
        prefix = self.prepare_cycle("apple-aggregate")
        for preparation, run in (("failure", "skipped"), ("cancelled", "skipped"), ("skipped", "skipped")):
            receipt = apple.finish(self.root, prefix, TASK, "apple-aggregate", preparation, run,
                                   backend_factory=Mock(side_effect=AssertionError("must not instantiate native backend")))
            self.assertEqual(receipt["result"], "FAIL")
        fresh = apple.cycle_prefix(self.prefix, "apple-wrapper")
        receipt = apple.finish(self.root, fresh, TASK, "apple-wrapper", "skipped", "skipped")
        self.assertEqual(receipt["result"], "NOT_RUN")
        self.assertEqual(receipt["gradle_stop"]["exit_code"], 0)

    def receipts(self):
        outcomes = {}
        for cycle in apple.CYCLES:
            prefix = self.prepare_cycle(cycle)
            claim_hash = hashlib.sha256(apple.claim_path(prefix).read_bytes()).hexdigest()
            receipt = {"schema": 1, "task": TASK, "cycle": cycle, "source": self.source,
                       "prepare_outcome": "success", "run_outcome": "success", "result": "PASS",
                       "errors": [], "gradle_stop": {"exit_code": 0}, "claim_sha256": claim_hash,
                       "workers_before_simulator": {"result": "PASS"}, "workers": {"result": "PASS"},
                       "simulator": {"result": "PASS" if cycle == "apple-ui" else "NOT_APPLICABLE"}}
            hygiene.write_new(Path(str(self.prefix) + "-stop-" + cycle + ".json"), receipt)
            outcomes[cycle] = {"prepare": "success", "run": "success", "finish": "success"}
        return outcomes

    def cleanup_outputs(self, outcomes):
        return hygiene.cleanup(self.root, self.claim_path, TASK, self.upload, "success", outcomes)

    def test_complete_source_bound_native_receipts_allow_output_cleanup_after_upload(self):
        outcomes = self.receipts()
        receipt = self.cleanup_outputs(outcomes)
        self.assertEqual(receipt["result"], "PASS", receipt)
        self.assertFalse((self.root / "build").exists())
        self.assertEqual(set(receipt["apple_cleanup"]), set(apple.CYCLES))

    def test_absent_failed_stale_or_unexecuted_native_cleanup_retains_output_evidence(self):
        outcomes = self.receipts()
        self.assertEqual(self.cleanup_outputs(None)["result"], "FAIL")
        for outcome in ("failure", "cancelled", "skipped", ""):
            changed = {**outcomes, "apple-ui": {**outcomes["apple-ui"], "finish": outcome}}
            self.assertEqual(self.cleanup_outputs(changed)["result"], "FAIL")
        path = Path(str(self.prefix) + "-stop-apple-ui.json")
        original = json.loads(path.read_text())
        for change in ({"workers": {"result": "FAIL"}}, {"source": {**self.source, "head": "d" * 40}},
                       {"claim_sha256": "e" * 64}, {"simulator": {"result": "NOT_CREATED"}},
                       {"gradle_stop": {"exit_code": 1}}, {"result": "NOT_RUN"}):
            path.write_text(json.dumps({**original, **change}))
            receipt = self.cleanup_outputs(outcomes)
            self.assertEqual(receipt["result"], "FAIL", change)
            self.assertEqual(receipt["removed"], [])
            self.assertEqual((self.root / "build/test.xml").read_text(), "preserve required evidence")


class AppleWorkflowOwnershipTest(unittest.TestCase):
    def test_each_apple_cycle_has_preparation_always_finalizer_and_output_gate(self):
        workflow = (ROOT / ".github/workflows/production-verification.yml").read_text()
        ios = workflow.split("  ios:\n", 1)[1]
        for cycle in apple.CYCLES:
            self.assertEqual(ios.count("scripts.ci.apple_verification_hygiene prepare " + cycle), 1)
            self.assertEqual(ios.count("scripts.ci.apple_verification_hygiene finish " + cycle), 1)
            stem = cycle.replace("-", "_")
            self.assertIn(f"id: {stem}_finish\n        if: always()", ios)
            for role in ("prepare", "run", "finish"):
                self.assertIn(f"steps.{stem}_{role}.outcome", ios)
        self.assertIn("PARLOR_APPLE_CYCLE_OUTCOMES:", ios)
        self.assertIn("-apple-ui-simulator-*.json", ios)
        self.assertIn("create-simulator apple-ui", ios)
        self.assertIn("-parallel-testing-enabled NO", ios)
        self.assertNotIn("rm -rf build/ci-evidence/ios-ui-tests.xcresult", ios)
        self.assertNotIn("xcrun simctl list devices available", ios)
        for dangerous in ("RUNNER_TRACKING_ID:", "shutdown all", "delete unavailable", "killall", "pkill"):
            self.assertNotIn(dangerous, ios)


if __name__ == "__main__":
    unittest.main()
