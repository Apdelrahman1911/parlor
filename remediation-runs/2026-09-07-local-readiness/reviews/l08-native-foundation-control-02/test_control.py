"""Synthetic controls only. No compiler, Gradle, simctl, app or device launched.

Drafted but NOT executed by the author. Execution belongs to the shared lane.
"""
import copy
import importlib.util
import json
import os
from pathlib import Path
import signal
import tempfile
import unittest
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("foundation_control_draft", HERE / "run_control.py")
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
TOKEN = "c" * 32
SOURCE = "d" * 64
IMAGE = "11aaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
DEVICE = "22bbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"


def query(value="missing", *, volume=False, backup=False):
    return dict(returned=True, dictionary_present=True, key_present=value != "missing",
                value=value, exception="none", native_error=dict(present=False, domain="none", code=0))


def fixture():
    result = dict(schema_version=1, run_token=TOKEN, source_sha256=SOURCE, simulator_only=True,
                  app_container=False, hardware_protection_verified=False, l08_requirements_waived=False,
                  pid=40001, runtime_version=[26, 5, 0], status="OBSERVATIONS_COMPLETE",
                  main_image=dict(status="observed", uuid=IMAGE, platform=7),
                  foundation_image=dict(status="observed", uuid=DEVICE, platform=7), rows=[])
    for name, kind in control.ROW_KINDS:
        if kind == "operation":
            row = dict(id=name, kind="operation", returned=True, result=True, exception="none",
                       native_error=dict(present=False, domain="none", code=0))
        else:
            row = dict(id=name, kind="observation", fm=query(), volume=query("supported"), backup=query("excluded"))
            if kind == "file":
                row["url"] = query()
        result["rows"].append(row)
    return result


def encoded(value):
    return json.dumps(value).encode()


def device_data(**changes):
    item = dict(udid=DEVICE, name="ParlorFoundationControl-" + TOKEN,
                deviceTypeIdentifier=control.DEVICE_TYPE, isAvailable=True, state="Shutdown")
    item.update(changes)
    return {"devices": {control.RUNTIME: [item]}}


class MetadataTests(unittest.TestCase):
    def check_rejected(self, value):
        with self.assertRaises(control.BoundaryError):
            control.validate_observations(encoded(value), TOKEN, SOURCE, IMAGE)

    def test_missing_keys_supported_volume_is_collection_not_l08_pass(self):
        value = control.validate_observations(encoded(fixture()), TOKEN, SOURCE, IMAGE)
        self.assertEqual(value["status"], "OBSERVATIONS_COMPLETE")
        self.assertEqual(value["rows"][1]["fm"]["value"], "missing")
        self.assertFalse(value["hardware_protection_verified"])
        self.assertFalse(value["l08_requirements_waived"])

    def test_native_error_not_coerced_to_empty_or_success(self):
        value = fixture()
        value["rows"][1]["fm"] = dict(returned=True, dictionary_present=False, key_present=False,
            value="unavailable", exception="none", native_error=dict(present=True, domain="cocoa", code=257))
        accepted = control.validate_observations(encoded(value), TOKEN, SOURCE, IMAGE)
        self.assertEqual(accepted["rows"][1]["fm"]["native_error"]["code"], 257)
        value["rows"][1]["fm"]["value"] = "missing"
        self.check_rejected(value)

    def test_collection_keeps_false_write_and_exception_distinct(self):
        value = fixture()
        value["rows"][4].update(result=False, native_error=dict(present=True, domain="posix", code=28))
        control.validate_observations(encoded(value), TOKEN, SOURCE, IMAGE)
        value["rows"][4].update(returned=False, exception="objc-exception",
                                native_error=dict(present=False, domain="none", code=0))
        control.validate_observations(encoded(value), TOKEN, SOURCE, IMAGE)
        value["rows"][4]["result"] = True
        self.check_rejected(value)

    def test_query_exception_requires_unobserved_not_missing(self):
        value = fixture()
        value["rows"][1]["fm"] = dict(returned=False, dictionary_present=False, key_present=False,
            value="unobserved", exception="objc-exception", native_error=dict(present=False, domain="none", code=0))
        control.validate_observations(encoded(value), TOKEN, SOURCE, IMAGE)
        value["rows"][1]["fm"]["value"] = "missing"
        self.check_rejected(value)

    def test_bad_source_token_image_runtime_and_platform_rejected(self):
        for key, replacement in (("source_sha256", "0" * 64), ("run_token", "0" * 32),
                                 ("runtime_version", [26, 4, 0]), ("runtime_version", [26, 5, False]),
                                 ("schema_version", True), ("pid", True), ("status", "PASS")):
            with self.subTest(key=key, replacement=replacement):
                value = fixture()
                value[key] = replacement
                self.check_rejected(value)
        for image in ("main_image", "foundation_image"):
            value = fixture()
            value[image]["platform"] = 1
            self.check_rejected(value)
        value = fixture()
        value["main_image"]["uuid"] = DEVICE
        self.check_rejected(value)

    def test_no_expanded_claims_or_raw_native_details(self):
        for key in ("app_container", "hardware_protection_verified", "l08_requirements_waived"):
            value = fixture()
            value[key] = True
            self.check_rejected(value)
        for key in ("native_description", "path", "payload", "attributes_dictionary"):
            value = fixture()
            value["rows"][1]["fm"][key] = "unexpected"
            self.check_rejected(value)

    def test_rows_cannot_be_lost_duplicated_reordered_or_repurposed(self):
        cases = []
        value = fixture(); value["rows"].pop(); cases.append(value)
        value = fixture(); value["rows"].append(copy.deepcopy(value["rows"][0])); cases.append(value)
        value = fixture(); value["rows"][4], value["rows"][8] = value["rows"][8], value["rows"][4]; cases.append(value)
        value = fixture(); value["rows"][1]["url"] = query(); cases.append(value)
        for value in cases:
            self.check_rejected(value)

    def test_key_and_error_type_boundaries(self):
        for field, bad in (("dictionary_present", 1), ("key_present", 1), ("returned", 1),
                           ("value", "complete"), ("exception", "unavailable")):
            value = fixture()
            value["rows"][1]["fm"][field] = bad
            self.check_rejected(value)
        for bad in (dict(present=False, domain="cocoa", code=0),
                    dict(present=False, domain="none", code=257),
                    dict(present=True, domain="none", code=0),
                    dict(present=True, domain="cocoa", code=True)):
            value = fixture()
            value["rows"][1]["fm"]["native_error"] = bad
            self.check_rejected(value)

    def test_duplicate_unknown_oversized_and_nonfinite_json_rejected(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":NaN}', b" " * 32769):
            with self.assertRaises(control.BoundaryError):
                control.validate_observations(raw, TOKEN, SOURCE, IMAGE)
        value = fixture(); value["unknown"] = 1
        self.check_rejected(value)


class DevicePlanTests(unittest.TestCase):
    def test_exact_private_device_only(self):
        self.assertEqual(control.selected_device(device_data(), "ParlorFoundationControl-" + TOKEN, DEVICE)["udid"], DEVICE)
        for changes in (dict(name="UserPhone"), dict(udid="booted"), dict(isAvailable=False),
                        dict(deviceTypeIdentifier="another-type"), dict(state="unknown")):
            with self.subTest(changes=changes), self.assertRaises(control.BoundaryError):
                control.selected_device(device_data(**changes), "ParlorFoundationControl-" + TOKEN)

    def test_empty_runtime_keys_are_empty_but_unknown_devices_are_not_owned(self):
        self.assertEqual(control.device_entries({"devices": {}}), [])
        self.assertEqual(control.device_entries({"devices": {control.RUNTIME: []}}), [])
        value = device_data()
        value["devices"][control.RUNTIME].append(copy.deepcopy(value["devices"][control.RUNTIME][0]))
        with self.assertRaises(control.BoundaryError):
            control.selected_device(value, "ParlorFoundationControl-" + TOKEN)
        for bad in ({}, {"devices": []}, {"devices": {control.RUNTIME: "not-a-list"}}):
            with self.assertRaises(control.BoundaryError):
                control.device_entries(bad)

    def test_commands_never_use_wrapper_default_set_all_booted_or_standalone(self):
        private = Path("/private/tmp/parlor-foundation-control-example/devices")
        result = control.sim_command(private, "spawn", DEVICE, "/private/tmp/example/control")
        self.assertEqual(result[:3], [str(control.SIMCTL), "--set", str(private)])
        for args in (("shutdown", "all"), ("shutdown", "booted"), ("spawn", "--standalone", DEVICE), ("erase", DEVICE)):
            with self.subTest(args=args), self.assertRaises(control.BoundaryError):
                control.sim_command(private, *args)

    def test_probe_baseline_is_not_masked_by_later_directory_set(self):
        source = (HERE / "FoundationProtectionControl.m").read_text()
        self.assertLess(source.index('observe(@"atomic-after-backup"'), source.index('operation(@"directory-set"'))
        self.assertIn('if ([exception.name isEqual:@"ControlBoundary"]) @throw;', source)
        self.assertIn("parent.st_ino == directoryInode", source)
        self.assertIn("CONTROL_TEMP_INODE", source)
        self.assertIn("!TARGET_OS_SIMULATOR", source)
        # Source guard, not proof of compilation, loaded SDK identity, or runtime behavior.


class OwnedFixtures(unittest.TestCase):
    def setUp(self):
        self.owned = []

    def root(self):
        path = Path(tempfile.mkdtemp(prefix="parlor-foundation-control-synthetic-", dir="/private/tmp"))
        identity = control.fingerprint(path)
        self.owned.append((path, identity))
        return path, identity

    def tearDown(self):
        for path, identity in reversed(self.owned):
            if os.path.lexists(path):
                control.remove_owned_tree(path, identity)

    def test_symlink_unlinks_only_owned_link_not_target(self):
        root, identity = self.root()
        outside, _ = self.root()
        sentinel = outside / "public-sentinel"
        sentinel.write_text("synthetic only")
        (root / "elsewhere").symlink_to(outside, target_is_directory=True)
        control.remove_owned_tree(root, identity)
        self.assertEqual(sentinel.read_text(), "synthetic only")

    def test_replaced_root_cannot_be_deleted_with_old_identity(self):
        root, identity = self.root()
        moved = root.with_name(root.name + "-original")
        root.rename(moved)
        self.owned[-1] = (moved, identity)
        root.mkdir(mode=0o700)
        self.owned.append((root, control.fingerprint(root)))
        (root / "sentinel").write_text("replacement")
        with self.assertRaises(control.BoundaryError):
            control.remove_owned_tree(root, identity)
        self.assertEqual((root / "sentinel").read_text(), "replacement")

    def test_wrong_mode_or_symlink_root_refused(self):
        root, identity = self.root()
        root.chmod(0o755)
        try:
            with self.assertRaises(control.BoundaryError):
                control.remove_owned_tree(root, identity)
        finally:
            root.chmod(0o700)
        other, _ = self.root()
        link = other / "link"
        link.symlink_to(root, target_is_directory=True)
        with self.assertRaises(control.BoundaryError):
            control.fingerprint(link)

    def test_nested_files_fifo_and_owned_cache_removed(self):
        root, identity = self.root()
        (root / "cache").mkdir(mode=0o700)
        (root / "cache/item").write_text("recreatable")
        os.mkfifo(root / "synthetic-fifo", 0o600)
        control.remove_owned_tree(root, identity)
        self.assertFalse(os.path.lexists(root))

    def fake_child(self, output, events):
        class Fake:
            pid = 987654321  # Never signalled: tests patch killpg.
            returncode = None

            def __init__(self):
                self.stdout = self.pipe(output)
                self.stderr = self.pipe(b"")

            @staticmethod
            def pipe(data):
                reader, writer = os.pipe()
                os.write(writer, data)
                os.close(writer)
                return os.fdopen(reader, "rb")

            def wait(self, timeout):
                events.append("wait")
                self.returncode = 0
                return self.returncode
        return Fake()

    def test_no_reap_precedes_termination_on_bounded_output_failure(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        events = []
        child = self.fake_child(b"x" * 128, events)
        with patch.object(control.subprocess, "Popen", return_value=child), \
                patch.object(control.os, "killpg", side_effect=lambda _pid, sig: events.append(sig)), \
                patch.object(control.time, "sleep"), self.assertRaises(control.BoundaryError):
            lane.command("synthetic-overflow", ["NEVER_EXECUTED"], limit=16)
        self.assertEqual(events, [signal.SIGTERM, signal.SIGKILL, "wait"])
        self.assertEqual(lane.receipt["commands"][0]["error"], "command-output-limit")

    def test_normal_fake_child_never_signals_any_process(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        events = []
        child = self.fake_child(b"observed", events)
        with patch.object(control.subprocess, "Popen", return_value=child), \
                patch.object(control.os, "killpg") as kill:
            code, out, err = lane.command("synthetic-normal", ["NEVER_EXECUTED"])
        kill.assert_not_called()
        self.assertEqual((code, out, err), (0, b"observed", b""))
        self.assertEqual(events, ["wait"])

    def test_foreign_gradle_is_not_stopped(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        with patch.object(lane, "command", return_value=(0, b"1234\n", b"")) as commands, \
                self.assertRaises(control.BoundaryError):
            lane.stop_gradle("synthetic-stop")
        self.assertEqual(commands.call_count, 1)
        self.assertNotIn("--stop", commands.call_args.args[1])

    def native_lane(self):
        dest, _ = self.root()
        scratch, identity = self.root()
        (scratch / "devices").mkdir(mode=0o700)
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        lane.scratch, lane.scratch_identity = scratch, identity
        lane.device_set_identity = control.fingerprint(scratch / "devices")
        return lane, scratch

    def test_lsof_warnings_enabled_after_terse_option_and_stderr_is_fatal(self):
        for code, output, warning in ((1, b"", b"lsof: WARNING: stat unavailable\n"),
                                     (0, b"1234\n", b""), (2, b"", b"")):
            with self.subTest(code=code, warning=warning):
                lane, scratch = self.native_lane()
                with patch.object(lane, "command", return_value=(code, output, warning)) as commands, \
                        patch.object(control, "remove_owned_tree") as remove:
                    lane.cleanup()
                remove.assert_not_called()
                self.assertEqual(commands.call_args.args[1],
                    ["/usr/sbin/lsof", "-nP", "-t", "+w", "+D", scratch])
                self.assertEqual(lane.receipt["cleanup_status"], "FAIL")
                self.assertTrue(scratch.is_dir())

    def test_lsof_clean_empty_result_allows_only_owned_root_removal(self):
        lane, scratch = self.native_lane()
        with patch.object(lane, "command", return_value=(1, b"", b"")) as commands:
            lane.cleanup()
        self.assertEqual(commands.call_args.args[1], ["/usr/sbin/lsof", "-nP", "-t", "+w", "+D", scratch])
        self.assertFalse(os.path.lexists(scratch))
        self.assertEqual(lane.receipt["cleanup_status"], "PASS")

    def test_replaced_device_set_inode_is_not_forwarded_to_simctl(self):
        lane, scratch = self.native_lane()
        (scratch / "devices").rename(scratch / "original-devices")
        (scratch / "devices").mkdir(mode=0o700)
        with patch.object(lane, "command") as commands, self.assertRaises(control.BoundaryError):
            lane.sim("synthetic-device-replacement", "list", "-j", "devices")
        commands.assert_not_called()
        self.assertTrue(lane.device_set_ambiguous)
        self.assertFalse(lane.simulator_contacted)

    def test_symlink_device_set_is_not_forwarded_or_removed_by_lane(self):
        lane, scratch = self.native_lane()
        outside, _ = self.root()
        sentinel = outside / "sentinel"
        sentinel.write_text("not the lane's set")
        (scratch / "devices").rmdir()  # Exact empty synthetic directory, owned by this test.
        (scratch / "devices").symlink_to(outside, target_is_directory=True)
        with patch.object(lane, "command") as commands, self.assertRaises(control.BoundaryError):
            lane.sim("synthetic-device-symlink", "list", "-j", "devices")
        commands.assert_not_called()
        with patch.object(lane, "command") as commands, patch.object(control, "remove_owned_tree") as remove:
            lane.cleanup()
        commands.assert_not_called()
        remove.assert_not_called()
        self.assertEqual(sentinel.read_text(), "not the lane's set")
        self.assertTrue(scratch.is_dir())
        self.assertEqual(lane.receipt["cleanup_status"], "FAIL")

    def test_device_set_replaced_before_any_contact_is_also_retained(self):
        lane, scratch = self.native_lane()
        (scratch / "devices").rename(scratch / "original-devices")
        (scratch / "devices").mkdir(mode=0o700)
        with patch.object(lane, "sim") as sim, patch.object(lane, "command") as commands, \
                patch.object(control, "remove_owned_tree") as remove:
            lane.cleanup()
        sim.assert_not_called()
        commands.assert_not_called()
        remove.assert_not_called()
        self.assertTrue(lane.device_set_ambiguous)
        self.assertTrue(scratch.is_dir())

    def test_no_creation_attempt_is_not_evidence_of_empty_private_set(self):
        lane, scratch = self.native_lane()
        lane.simulator_contacted = True
        unexpected = device_data(name="unexpected-private-set-entry")
        with patch.object(lane, "sim", return_value=(0, encoded(unexpected), b"")) as sim, \
                patch.object(lane, "command") as commands, patch.object(control, "remove_owned_tree") as remove:
            lane.cleanup()
        self.assertEqual(sim.call_args.args, ("cleanup-device-identity", "list", "-j", "devices"))
        self.assertEqual(sim.call_count, 1)
        commands.assert_not_called()
        remove.assert_not_called()
        self.assertTrue(lane.device_set_ambiguous)
        self.assertTrue(scratch.is_dir())

    def test_precreate_ambiguity_cannot_be_cleared_by_later_empty_listing(self):
        lane, scratch = self.native_lane()
        lane.simulator_contacted = True
        lane.device_set_ambiguous = True
        with patch.object(lane, "sim", return_value=(0, encoded({"devices": {}}), b"")) as sim, \
                patch.object(control, "remove_owned_tree") as remove:
            lane.cleanup()
        sim.assert_not_called()
        remove.assert_not_called()
        self.assertTrue(scratch.is_dir())
        self.assertTrue(lane.receipt["device_set_quarantined"])

    def test_interrupted_partial_create_recovers_only_unique_token_device(self):
        lane, scratch = self.native_lane()
        lane.simulator_contacted = True
        lane.creation_attempted = True
        lane.request_cancellation(signal.SIGINT)
        data = encoded(device_data(name=lane.name))
        calls = []

        def sim(label, *args, **_kwargs):
            calls.append((label, args))
            return (0, encoded({"devices": {control.RUNTIME: []}}), b"") if label == "deleted-identity" else (0, data, b"")

        with patch.object(lane, "sim", side_effect=sim), \
                patch.object(lane, "command", return_value=(1, b"", b"")):
            lane.cleanup()
        self.assertIn(("delete", ("delete", DEVICE)), calls)
        self.assertEqual(lane.receipt["cleanup_device_uuid"], DEVICE)
        self.assertFalse(os.path.lexists(scratch))
        self.assertTrue(lane.receipt["cancellation_requested"])
        self.assertEqual(lane.receipt["cleanup_status"], "PASS")  # Mocked lifecycle, not Simulator evidence.

    def test_partial_create_wrong_token_never_grants_delete_authority(self):
        lane, scratch = self.native_lane()
        lane.simulator_contacted = True
        lane.creation_attempted = True
        with patch.object(lane, "sim", return_value=(0, encoded(device_data(name="someone-else")), b"")) as sim, \
                patch.object(lane, "command") as commands:
            lane.cleanup()
        self.assertEqual(sim.call_count, 1)
        commands.assert_not_called()
        self.assertTrue(scratch.is_dir())
        self.assertEqual(lane.receipt["cleanup_status"], "FAIL")

    def test_repeated_cancellation_cannot_unwind_kill_reap_close_or_logs(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        events = []
        child = self.fake_child(b"", events)

        def child_created(*_args, **_kwargs):
            lane.request_cancellation(signal.SIGINT)
            return child

        def second_interruption(_seconds):
            events.append("second-interruption")
            lane.request_cancellation(signal.SIGTERM)

        with patch.object(control.subprocess, "Popen", side_effect=child_created), \
                patch.object(control.os, "killpg", side_effect=lambda _pid, sig: events.append(sig)), \
                patch.object(control.time, "sleep", side_effect=second_interruption), \
                self.assertRaisesRegex(control.BoundaryError, "runner-interrupted"):
            lane.command("synthetic-repeated-interruption", ["NEVER_EXECUTED"])
        self.assertEqual(events, [signal.SIGTERM, "second-interruption", signal.SIGKILL, "wait"])
        self.assertEqual(lane.receipt["first_cancel_signal"], signal.SIGINT)
        self.assertEqual(lane.receipt["cancel_signals"], [signal.SIGINT, signal.SIGTERM])
        self.assertEqual(lane.receipt["commands"][0]["error"], "runner-interrupted")
        self.assertTrue(child.stdout.closed and child.stderr.closed)
        self.assertTrue((dest / "synthetic-repeated-interruption.stdout").is_file())
        self.assertTrue((dest / "synthetic-repeated-interruption.stderr").is_file())

    def test_cancellation_while_waiting_after_pipe_eof_still_finalizes_child(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        events = []
        child = self.fake_child(b"", events)
        original_wait = child.wait

        def waiting(timeout):
            if not events:
                events.append("bounded-wait")
                self.assertLessEqual(timeout, 0.2)
                lane.request_cancellation(signal.SIGINT)
                raise control.subprocess.TimeoutExpired("FAKE", timeout)
            return original_wait(timeout)

        with patch.object(control.subprocess, "Popen", return_value=child), \
                patch.object(child, "wait", side_effect=waiting), \
                patch.object(control.os, "killpg", side_effect=lambda _pid, sig: events.append(sig)), \
                patch.object(control.time, "sleep"), self.assertRaises(control.BoundaryError):
            lane.command("synthetic-eof-interruption", ["NEVER_EXECUTED"])
        self.assertEqual(events, ["bounded-wait", signal.SIGTERM, signal.SIGKILL, "wait"])
        self.assertTrue(child.stdout.closed and child.stderr.closed)

    def test_cancelled_lane_refuses_new_work_but_shields_gradle_stop(self):
        dest, _ = self.root()
        lane = control.Lane(dest, Path("/synthetic-jdk-never-executed"))
        lane.request_cancellation(signal.SIGINT)
        with patch.object(control.subprocess, "Popen") as launch, self.assertRaises(control.BoundaryError):
            lane.command("synthetic-no-new-work", ["NEVER_EXECUTED"])
        launch.assert_not_called()
        seen = []

        def command(label, args, **_kwargs):
            self.assertTrue(lane.finalizing)
            lane.check_cancellation()  # Stop path is deliberately shielded, not silently cancelled.
            seen.append(args)
            return (0, b"", b"") if "--stop" in args else (1, b"", b"")

        with patch.object(lane, "command", side_effect=command):
            lane.stop_gradle("synthetic-stop-shielded")
        self.assertEqual(len(seen), 3)
        self.assertTrue(any("--stop" in args for args in seen))
        self.assertFalse(lane.finalizing)
        self.assertTrue(lane.receipt["cancellation_requested"])

    def test_main_uses_nonthrowing_handlers_not_exception_raising_callbacks(self):
        source = (HERE / "run_control.py").read_text()
        self.assertIn("signal.signal(signal.SIGINT, lane.request_cancellation)", source)
        self.assertIn("signal.signal(signal.SIGTERM, lane.request_cancellation)", source)
        self.assertNotIn("signal.signal(signal.SIGINT, interrupted)", source)


if __name__ == "__main__":
    unittest.main()
