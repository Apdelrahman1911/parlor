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


if __name__ == "__main__":
    unittest.main()
