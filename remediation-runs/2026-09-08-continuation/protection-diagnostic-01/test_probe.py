"""Focused pure control tests. Synthetic records are NOT platform evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

PATH = Path(__file__).with_name("run_probe.py")
SPEC = importlib.util.spec_from_file_location("protection_probe_under_test", PATH)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


def fixture():
    image_uuid = "11111111-1111-1111-1111-111111111111"
    image = dict(image_basename="SyntheticTestImage", uuid=image_uuid, platforms=[7], cputype=0x100000C,
                 cpusubtype=0, image_offset=128, dylib=None)
    def implementation(selector):
        return dict(receiver_class="SyntheticTestReceiver", selector=selector, implementation=copy.deepcopy(image))
    no_error = dict(present=False, code=0, domain="none")
    request = dict(fixtures=dict(device=7, inode=8, uid=501), native_context=dict(source_sha="1"*40,
        control_sha256="2"*64, nonce="3"*32, simulator_udid="AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
        fixture_root_device=7, fixture_root_inode=8))
    samples, rows = {}, []
    for name in probe.EVENTS:
        if name in {"directory-create", "complete-write", "none-write", "default-write", "complete-replace", "none-url-set", "nonatomic-complete-write"}:
            selector = "createDirectoryAtPath:withIntermediateDirectories:attributes:error:" if name == "directory-create" else (
                "setResourceValue:forKey:error:" if name == "none-url-set" else "writeToFile:options:error:")
            row = dict(kind="operation", id=name, returned=True, native_error=copy.deepcopy(no_error),
                implementation_before=implementation(selector), implementation_after=implementation(selector))
            if name in {"directory-create", "none-url-set"}:
                row["requested_protection"] = "complete"
            else:
                row.update(requested_options={"complete-write": 0x20000001, "none-write": 0x10000001,
                    "default-write": 1, "complete-replace": 0x20000001, "nonatomic-complete-write": 0x20000000}[name],
                    payload_bytes=48 if name == "complete-replace" else 43,
                    observer_descriptor_held_across_write=False)
        elif name == "none-kernel-set":
            row = dict(kind="operation", id=name, sdk_available=False, status="PUBLIC_SDK_COMMAND_OR_CLASS_UNAVAILABLE")
        else:
            directory = name in {"directory-baseline", "directory-final"}
            # Reuse of the replaced-away original inode is legitimate; current
            # four-file distinctness must select complete-after-replace instead.
            inode = 9 if directory else 10 if name in {"complete-baseline", "nonatomic-complete-baseline"} else 13 if name == "complete-after-replace" else 12 if name == "default-baseline" else 11
            identity = dict(device=7, inode=inode, uid=501, mode=0o40700 if directory else 0o100600,
                links=2 if directory else 1, size=128 if name == "directory-final" else 64 if directory else 48 if name == "complete-after-replace" else 43,
                type="directory" if directory else "regular")
            fm = dict(dictionary_present=True, key_present=False, protection="missing", native_error=copy.deepcopy(no_error),
                implementation_before=implementation("attributesOfItemAtPath:error:"), implementation_after=implementation("attributesOfItemAtPath:error:"))
            url = dict(dictionary_present=True, key_present=not directory, protection="NOT_APPLICABLE_DIRECTORY" if directory else "until-first-authentication",
                native_error=copy.deepcopy(no_error), implementation_before=implementation("resourceValuesForKeys:error:"),
                implementation_after=implementation("resourceValuesForKeys:error:"), fresh_url=True, is_directory=directory, volume_support="unsupported")
            value = dict(schema=1, collection_status="PASS", descriptor_closed=True, identity=identity, fm=fm, url=url,
                fcntl=dict(sdk_available=True, command=63, return_value=-1, errno=45, **{"class": None}),
                attrlist=dict(sdk_available=True, return_value=0, errno=0, length=24, returned_common_mask=0,
                    requested_common_mask=0xC0000000, data_protection_mask=0x40000000, returned_attributes_mask=0x80000000,
                    attribute_set_bytes=20, protection_returned=False, **{"class": None}),
                filesystem=dict(return_value=0, errno=0, type="apfs", fsid=[3, 4], flags=0,
                    content_protection_flag=128, content_protection_capability="unsupported"),
                reads=[dict(kind=kind, before=copy.deepcopy(identity), after=copy.deepcopy(identity), descriptor_and_path_same_inode=True) for kind in probe.READS])
            samples[name] = value
            row = dict(kind="sample", id=name, sample=value)
        rows.append(row)
    rows.append(dict(kind="final", schema=1, collection_status="PASS", scope="synthetic-native-metadata-only",
        production_snapshots_observed=False, historical_a37_strict_result_changed=False, runtime_version=[26, 2, 0],
        context=copy.deepcopy(request["native_context"]), main_image_before=copy.deepcopy(image), main_image_after=copy.deepcopy(image),
        sdk_options=dict(atomic=1, complete=0x20000000, none=0x10000000),
        strict_synthetic_complete=dict(required_sample_ids=list(probe.STRICT), **{"pass": 0, "fail": 5}, status="FAIL"),
        replacement=dict(before=copy.deepcopy(samples["complete-baseline"]["identity"]),
            after=copy.deepcopy(samples["complete-after-replace"]["identity"]), observer_descriptor_held=False, named_inode_changed=True)))
    return rows, request, image_uuid


def validate(rows, request, image_uuid):
    return probe.validate_report(("\n".join(json.dumps(row) for row in rows) + "\n").encode(), request, image_uuid)


def host_fixture(rows, request):
    main = copy.deepcopy(rows[-1]["main_image_before"])
    main.update(image_basename=probe.HOST_IMAGE, platforms=[1], uuid="22222222-2222-2222-2222-222222222222")
    samples = {row["id"]: row["sample"] for row in rows if row.get("kind") == "sample"}
    result = dict(schema=1, kind="synthetic-protection-host-reader", collection_status="PASS", context=copy.deepcopy(request["native_context"]),
        process_id=1234, uid=501, runtime_version=[15, 7, 9], read_only=True, same_simulator_process=False,
        production_snapshots_observed=False, historical_a37_strict_result_changed=False,
        main_image_before=main, main_image_after=copy.deepcopy(main), samples=[])
    for name, _ in probe.HOST_TARGETS:
        sample = copy.deepcopy(samples[name])
        sample["fm"].update(key_present=True, protection="complete")
        for kind, basename in (("fm", "Foundation"), ("url", "CoreFoundation")):
            for key in ("implementation_before", "implementation_after"):
                sample[kind][key]["implementation"].update(platforms=[1, 6], image_basename=basename)
        result["samples"].append(dict(id=name, native=sample))
    command = dict(label="host-observation", status="EXITED", exit_code=0, direct_child_reaped=True,
        ownership="direct-unreaped-Popen", owned_pid=1234)
    return result, command, main["uuid"]


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.rows, self.request, self.image_uuid = fixture()

    def reject(self, mutation):
        mutation(self.rows)
        with self.assertRaises(RuntimeError):
            validate(self.rows, self.request, self.image_uuid)

    def test_collection_is_not_strict_or_application_pass(self):
        result = validate(self.rows, self.request, self.image_uuid)
        self.assertEqual(result["collection_status"], "CAPTURED_SYNTHETIC_METADATA_NOT_APP_QUALIFICATION")
        self.assertEqual(probe.STRICT[:4], ("directory-baseline", "complete-baseline", "complete-after-replace", "none-after-url-set"))
        self.assertEqual(result["strict_synthetic_complete"]["fail_count"], 5)
        self.assertFalse(result["historical_a37_strict_result_changed"])

    def test_missing_event(self):
        self.reject(lambda rows: rows.pop(1))

    def test_reordered_events(self):
        self.reject(lambda rows: rows.reverse())

    def test_stale_source(self):
        self.reject(lambda rows: rows[-1]["context"].update(source_sha="f"*40))

    def test_wrong_runtime(self):
        self.reject(lambda rows: rows[-1].update(runtime_version=[26, 5, 0]))

    def test_wrong_main_uuid(self):
        with self.assertRaises(RuntimeError):
            validate(self.rows, self.request, "ffffffff-ffff-ffff-ffff-ffffffffffff")

    def test_strict_failure_cannot_be_promoted(self):
        self.reject(lambda rows: rows[-1]["strict_synthetic_complete"].update(status="PASS"))

    def test_changed_fd_witness(self):
        self.reject(lambda rows: rows[3]["sample"]["reads"][0]["after"].update(inode=999))

    def test_unclosed_fd(self):
        self.reject(lambda rows: rows[3]["sample"].update(descriptor_closed=False))

    def test_observer_must_not_hold_fd_across_write(self):
        self.reject(lambda rows: rows[8].update(observer_descriptor_held_across_write=True))

    def test_wrong_write_options(self):
        self.reject(lambda rows: rows[2].update(requested_options=1))

    def test_nonatomic_contrast_must_not_request_atomic(self):
        self.reject(lambda rows: rows[14].update(requested_options=0x20000001))

    def test_simulator_images_remain_single_platform(self):
        self.reject(lambda rows: [rows[3]["sample"]["fm"][key]["implementation"].update(platforms=[1, 6])
            for key in ("implementation_before", "implementation_after")])

    def test_getter_imp_changes(self):
        self.reject(lambda rows: rows[3]["sample"]["fm"]["implementation_after"]["implementation"].update(image_offset=999))

    def test_getter_selector_not_class_image(self):
        self.reject(lambda rows: [rows[3]["sample"]["fm"][key].update(selector="class") for key in ("implementation_before", "implementation_after")])

    def test_attr_missing_bit_is_not_class_zero(self):
        self.reject(lambda rows: rows[3]["sample"]["attrlist"].update(protection_returned=True, length=28, **{"class": 0}))

    def test_attr_unrequested_bit(self):
        self.reject(lambda rows: rows[3]["sample"]["attrlist"].update(returned_common_mask=1))

    def test_attr_positive_return_is_invalid(self):
        self.reject(lambda rows: rows[3]["sample"]["attrlist"].update(return_value=1))

    def test_available_attr_class_zero_remains_observation(self):
        self.rows[3]["sample"]["attrlist"].update(protection_returned=True, length=28, returned_common_mask=0x40000000, **{"class": 0})
        self.assertEqual(validate(self.rows, self.request, self.image_uuid)["strict_synthetic_complete"]["status"], "FAIL")

    def test_fcntl_errno_cannot_be_class(self):
        self.reject(lambda rows: rows[3]["sample"]["fcntl"].update(**{"class": 45}))

    def test_fcntl_bool_is_not_integer_class(self):
        self.reject(lambda rows: rows[3]["sample"]["fcntl"].update(return_value=0, errno=0, **{"class": False}))

    def test_unavailable_api_cannot_supply_class(self):
        self.reject(lambda rows: rows[3]["sample"].update(fcntl=dict(sdk_available=False, status="PUBLIC_SDK_SYMBOL_UNAVAILABLE", **{"class": 1})))

    def test_capability_claim_must_match_flag(self):
        self.reject(lambda rows: rows[3]["sample"]["filesystem"].update(content_protection_capability="supported"))

    def test_filesystem_bool_return_rejected(self):
        self.reject(lambda rows: rows[3]["sample"]["filesystem"].update(return_value=False))

    def test_duplicate_json_key(self):
        with self.assertRaises(RuntimeError):
            probe.validate_report(b'{"kind":"final","kind":"sample"}\n', self.request, self.image_uuid)


class AdmissionTests(unittest.TestCase):
    def test_upload_failure_and_missing_identity_rejected(self):
        good = dict(PARLOR_PROTECTION_UPLOAD_OUTCOME="success", PARLOR_PROTECTION_ARTIFACT_ID="17", PARLOR_PROTECTION_ARTIFACT_DIGEST="a"*64)
        self.assertEqual(probe.upload_binding(good)["artifact_id"], 17)
        for key in good:
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                probe.upload_binding({**good, key: ""})

    def test_controls_bind_each_owned_and_transitive_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in probe.CONTROL_PATHS:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"synthetic-control-input")
            first = probe.controls(root)
            (root / probe.HERE / "ProtectionSampler.m").write_bytes(b"changed-input")
            self.assertNotEqual(first["control_sha256"], probe.controls(root)["control_sha256"])
            self.assertEqual(len(first["files"]), len(set(probe.CONTROL_PATHS)))

    def test_exact_job_and_source_admission(self):
        root = Path("/synthetic-closed-checkout")
        env = dict(GITHUB_ACTIONS="true", GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_JOB=probe.SCOPE,
            PARLOR_DISPATCH_SCOPE=probe.SCOPE, GITHUB_REPOSITORY=probe.native.REPOSITORY,
            GITHUB_REF="refs/heads/"+probe.native.BRANCH,
            GITHUB_WORKFLOW_REF=probe.native.REPOSITORY+"/"+probe.native.WORKFLOW+"@refs/heads/"+probe.native.BRANCH,
            PARLOR_FROZEN_SOURCE_SHA="1"*40, GITHUB_SHA="1"*40, GITHUB_WORKFLOW_SHA="1"*40,
            GITHUB_RUN_ID="17", GITHUB_RUN_ATTEMPT="1", GITHUB_WORKSPACE=str(root))
        replies = {("rev-parse", "--show-toplevel"): str(root), ("rev-parse", "HEAD"): "1"*40,
            ("branch", "--show-current"): probe.native.BRANCH, ("rev-parse", "--is-shallow-repository"): "false",
            ("status", "--porcelain=v1", "--untracked-files=no"): "", ("rev-parse", "HEAD^{tree}"): "2"*40}
        calls = []
        def execute(args, label, timeout):
            calls.append((args, label, timeout))
            return replies[tuple(args[1:])].encode()
        self.assertEqual(probe.context(env, execute, root)["job"], probe.SCOPE)
        self.assertEqual([row for row in calls if row[2] != 20], [
            (["/usr/bin/git", "status", "--porcelain=v1", "--untracked-files=no"], "git-binding", 60)])
        self.assertEqual(len(calls), 6)  # No retry, omitted check, or cached result.
        for key, wrong in (("GITHUB_JOB", "ios"), ("PARLOR_DISPATCH_SCOPE", "full"), ("GITHUB_SHA", "9"*40),
                           ("GITHUB_EVENT_NAME", "push"), ("GITHUB_WORKFLOW_REF", "foreign/workflow@main")):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                probe.context({**env, key: wrong}, execute, root)
        replies[("rev-parse", "--is-shallow-repository")] = "true"
        with self.assertRaises(RuntimeError):
            probe.context(env, execute, root)

    def test_unknown_cli_cannot_allocate(self):
        for values in ([], ["full"], ["run", "--allow-unsupported"]):
            with self.subTest(values=values), self.assertRaises(RuntimeError):
                probe.main(values)

    def test_platform_runtime_listing_opts_into_sampling_but_timeout_still_fails(self):
        instance = object.__new__(probe.Probe)
        instance.state, instance.commands = None, mock.Mock()
        sdk = probe.DEVELOPER + "/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
        replies = {"macos-version": b"15.7.9\n", "macos-build": b"24G830\n", "host-kernel": b"24.6.0\n",
            "xcode-version": b"Xcode 26.3\nBuild version 17C529\n",
            "developer-selection": probe.DEVELOPER.encode(), "sdk-version": b"26.2\n",
            "sdk-path": sdk.encode(), "runtimes": b'{"runtimes": []}'}
        timed_out = dict(status="TIMEOUT", exit_code=0, direct_child_reaped=True)
        def captured(arguments, label, timeout, **options):
            if label == "runtimes":
                self.assertEqual(arguments, ["/usr/bin/xcrun", "simctl", "list", "runtimes", "--json"])
                self.assertEqual(timeout, 90)
                self.assertEqual(options, {"sample_runtime": True})
                return timed_out, replies[label], b""
            self.assertEqual(options, {})
            return dict(status="EXITED", exit_code=0, direct_child_reaped=True), replies[label], b""
        with mock.patch.object(instance.commands, "capture", side_effect=captured) as capture, \
                mock.patch.object(probe.Path, "resolve", lambda path, strict=False: path):
            with self.assertRaisesRegex(RuntimeError, "required-command-failed"):
                instance.platform_binding()
        self.assertEqual(capture.call_count, 8)
        self.assertEqual(capture.call_args.args[1], "runtimes")
        self.assertEqual(timed_out["status"], "TIMEOUT")

    def test_execute_default_preserves_borrowed_legacy_capture_signature(self):
        calls = []
        class LegacyHost:
            execute = probe.Probe.execute
            def capture(self, arguments, label, timeout=30, retain=True):
                calls.append((arguments, label, timeout, retain))
                return dict(status="EXITED", exit_code=0, direct_child_reaped=True), b"source-binding", b""
        instance = LegacyHost()
        arguments = ["/usr/bin/git", "rev-parse", "HEAD"]
        self.assertEqual(instance.execute(arguments, "git-binding", 20), b"source-binding")
        self.assertEqual(instance.execute(arguments, "git-binding", 20, sample_runtime=False), b"source-binding")
        self.assertEqual(calls, [(arguments, "git-binding", 20, True)] * 2)


class HostSdkSequencingTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.developer = str(self.root / "Xcode.app/Contents/Developer")
        self.sdk = Path(self.developer) / "Platforms/MacOSX.platform/Developer/SDKs/MacOSX26.2.sdk"
        self.sdk.mkdir(parents=True)
        selected = mock.patch.object(probe, "DEVELOPER", self.developer)
        selected.start()
        self.addCleanup(selected.stop)
        self.instance = object.__new__(probe.Probe)
        self.instance.state = dict(status="RUNNING", logs=[], errors=[])
        self.instance.source = dict(source_sha="1" * 40, tree="2" * 40, run_id=17, run_attempt=1)
        self.instance.approved = "3" * 64
        self.instance.request = dict(claim=dict(nonce="4" * 32))
        self.instance.commands = mock.Mock(environment={"JAVA_HOME": str(self.root / "jdk")},
            handles=[], signals=[], preservation=dict(failures=0))
        self.calls, self.events = [], []
        self.responses = {"host-sdk-version": b"26.2\n", "host-sdk-path": str(self.sdk).encode()}
        self.outcomes = {}
        def capture(arguments, label, timeout=30, retain=True, **options):
            self.calls.append((arguments, label, timeout, retain, options))
            self.events.append(label)
            return {"status": "EXITED", "exit_code": 0, "direct_child_reaped": True, **self.outcomes.get(label, {})}, \
                self.responses[label], b""
        self.instance.capture = capture
        self.instance.save = mock.Mock(side_effect=lambda: self.events.append("saved"))

    def test_real_platform_phase_retains_host_sdk_before_creation_and_later_reuses_it(self):
        sdk = Path(self.developer) / "Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.2.sdk"
        for relative in ("usr/include/sys/fcntl.h", "usr/include/sys/attr.h", "usr/include/sys/mount.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSData.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSFileManager.h",
            "System/Library/Frameworks/Foundation.framework/Headers/NSURL.h"):
            path = sdk / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"// Synthetic SDK header; not native platform evidence.\n")
        self.responses.update({"macos-version": b"15.7.9\n", "macos-build": b"24G830\n", "host-kernel": b"24.6.0\n",
            "xcode-version": b"Xcode 26.3\nBuild version 17C529\n", "developer-selection": self.developer.encode(),
            "sdk-version": b"26.2\n", "sdk-path": str(sdk).encode(), "jdk-version": b'openjdk version "21.0.12"\n',
            "runtimes": json.dumps(dict(runtimes=[dict(identifier=probe.RUNTIME, isAvailable=True,
                version="26.2", buildversion="23C54")])).encode()})
        self.instance.prepare = mock.Mock()
        self.instance.bindings = mock.Mock()
        self.instance.stop = mock.Mock()
        self.instance.create_simulator = mock.Mock(side_effect=lambda: self.events.append("create") or "synthetic-uuid")
        def compile_and_run(actual_sdk, identity):
            self.events.append("compile")
            self.assertEqual((actual_sdk, identity), (sdk, "synthetic-uuid"))
            self.assertEqual(self.instance.bound_host_sdk(), self.sdk)
        self.instance.compile_and_run = mock.Mock(side_effect=compile_and_run)
        with mock.patch.object(probe.signal, "signal"):
            self.assertEqual(self.instance.run(), 0)
        self.assertLess(self.events.index("host-sdk-version"), self.events.index("host-sdk-path"))
        self.assertLess(self.events.index("host-sdk-path"), self.events.index("saved"))
        self.assertLess(self.events.index("saved"), self.events.index("create"))
        self.assertLess(self.events.index("create"), self.events.index("compile"))
        host_calls = [row for row in self.calls if row[1].startswith("host-sdk-")]
        self.assertEqual(host_calls, [
            (["/usr/bin/xcrun", "--sdk", "macosx", "--show-sdk-version"], "host-sdk-version", 30, True, {}),
            (["/usr/bin/xcrun", "--sdk", "macosx", "--show-sdk-path"], "host-sdk-path", 30, True, {})])
        retained = self.instance.state["host_platform"]
        self.assertEqual(retained, dict(sdk="26.2", sdk_path=str(self.sdk), target="arm64-apple-macosx15.0",
            source=self.instance.source, control_sha256=self.instance.approved, nonce=self.instance.request["claim"]["nonce"]))
        self.assertIsNot(retained["source"], self.instance.source)

    def test_failed_or_wrong_host_sdk_qualification_cannot_supply_an_attestation(self):
        for label, output, outcome in (
            ("host-sdk-version", b"26.3\n", {}),
            ("host-sdk-version", b"26.2\n", {"status": "TIMEOUT"}),
            ("host-sdk-path", str(self.sdk).encode(), {"status": "TIMEOUT"}),
            ("host-sdk-path", str(self.root).encode(), {}),
        ):
            with self.subTest(label=label, output=output, outcome=outcome):
                self.responses.update({"host-sdk-version": b"26.2\n", "host-sdk-path": str(self.sdk).encode(), label: output})
                self.outcomes = {label: outcome}
                with self.assertRaises(RuntimeError):
                    self.instance.bind_host_sdk()
                self.assertNotIn("host_platform", self.instance.state)
                self.assertFalse(hasattr(self.instance, "host_sdk"))
        self.instance.save.assert_not_called()
        self.assertTrue(all(row[2] == 30 for row in self.calls))

    def test_source_run_control_nonce_or_retained_path_drift_rejects_without_queries(self):
        self.instance.bind_host_sdk()
        source, retained = copy.deepcopy(self.instance.source), copy.deepcopy(self.instance.state["host_platform"])
        mutations = [lambda: self.instance.source.update(source_sha="f" * 40),
            lambda: self.instance.source.update(run_id=18), lambda: self.instance.source.update(run_attempt=2),
            lambda: setattr(self.instance, "approved", "f" * 64),
            lambda: self.instance.request["claim"].update(nonce="f" * 32),
            lambda: self.instance.state["host_platform"].update(sdk_path=str(self.root)),
            lambda: self.instance.state["host_platform"].update(sdk="26.3"),
            lambda: self.instance.state["host_platform"].update(target="arm64-apple-ios16.0-simulator")]
        for index, mutate in enumerate(mutations):
            with self.subTest(index=index):
                mutate()
                with self.assertRaisesRegex(RuntimeError, "current-run-host-sdk-binding"):
                    self.instance.bound_host_sdk()
                self.instance.source = copy.deepcopy(source)
                self.instance.approved = "3" * 64
                self.instance.request["claim"]["nonce"] = "4" * 32
                self.instance.state["host_platform"] = copy.deepcopy(retained)
        self.assertEqual(len(self.calls), 2)

    def test_redirected_prequalified_sdk_path_is_not_silently_resolved_again(self):
        self.instance.bind_host_sdk()
        relocated = self.sdk.with_name("Redirected.sdk")
        self.sdk.rename(relocated)
        self.sdk.symlink_to(relocated, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "host-sdk-path-changed"):
            self.instance.bound_host_sdk()
        self.assertEqual(len(self.calls), 2)

    def test_missing_current_run_binding_and_duplicate_qualification_never_query_as_fallback(self):
        with self.assertRaisesRegex(RuntimeError, "current-run-host-sdk-binding"):
            self.instance.bound_host_sdk()
        self.assertEqual(self.calls, [])
        self.instance.bind_host_sdk()
        with self.assertRaisesRegex(RuntimeError, "host-sdk-already-qualified"):
            self.instance.bind_host_sdk()
        del self.instance.host_sdk  # A retained record alone is not a current-run qualification.
        with self.assertRaisesRegex(RuntimeError, "current-run-host-sdk-binding"):
            self.instance.bound_host_sdk()
        self.assertEqual(len(self.calls), 2)

    def test_host_read_stage_consumes_bound_sdk_before_sources_without_requery(self):
        self.instance.bind_host_sdk()
        self.instance.resources = self.root / "resources"
        self.instance.resources.mkdir(mode=0o700)
        (self.instance.resources / "fixtures").mkdir(mode=0o700)
        self.instance.request.update(resources=probe.owned_directory(self.instance.resources),
            fixtures=probe.owned_directory(self.instance.resources / "fixtures"), native_context={})
        with mock.patch.object(self.instance, "bound_host_sdk", wraps=self.instance.bound_host_sdk) as bound, \
                mock.patch.object(probe, "context_header", return_value=b"synthetic-header"), \
                mock.patch.object(probe, "host_sources", side_effect=RuntimeError("stop-before-host-source-generation")):
            with self.assertRaisesRegex(RuntimeError, "stop-before-host-source-generation"):
                self.instance.compile_and_read_host({})
        bound.assert_called_once_with()
        self.assertEqual(len(self.calls), 2)


class HostComparisonTests(unittest.TestCase):
    def setUp(self):
        self.rows, self.request, self.image_uuid = fixture()
        self.report = validate(self.rows, self.request, self.image_uuid)
        self.host, self.command, self.host_uuid = host_fixture(self.rows, self.request)

    def validate(self, value=None):
        return probe.validate_host_report(json.dumps(self.host if value is None else value).encode(), self.request,
            self.report, self.host_uuid, self.command, [15, 7, 9])

    def test_five_final_inodes_and_host_complete_never_promote_original_four(self):
        self.rows[15]["sample"]["fm"].update(key_present=True, protection="complete")
        self.rows[-1]["strict_synthetic_complete"].update({"pass": 1, "fail": 4})
        self.report = validate(self.rows, self.request, self.image_uuid)
        comparison = self.validate()
        self.assertEqual([row["id"] for row in comparison["comparisons"]], [name for name, _ in probe.HOST_TARGETS])
        self.assertEqual(comparison["comparisons"][0]["identity"]["size"], 128)
        self.assertTrue(all(row["host_fm"] == "complete" for row in comparison["comparisons"]))
        self.assertEqual(comparison["strict_synthetic_complete"], self.report["strict_synthetic_complete"])
        self.assertEqual(comparison["strict_synthetic_complete"]["fail_count"], 4)
        self.assertFalse(comparison["historical_a37_strict_result_changed"])

    def test_full_identity_map_rejects_stale_directory_old_file_and_aliases(self):
        replacements = [(0, self.rows[1]["sample"]["identity"]), (1, self.rows[3]["sample"]["identity"]),
            (4, self.host["samples"][3]["native"]["identity"])]
        for key in ("device", "inode", "uid", "mode", "links", "size"):
            changed = copy.deepcopy(self.host["samples"][0]["native"]["identity"])
            changed[key] += 1
            replacements.append((0, changed))
        for index, identity in replacements:
            with self.subTest(index=index, identity=identity):
                value = copy.deepcopy(self.host)
                sample = value["samples"][index]["native"]
                sample["identity"] = copy.deepcopy(identity)
                for witness in sample["reads"]:
                    witness.update(before=copy.deepcopy(identity), after=copy.deepcopy(identity))
                with self.assertRaises(RuntimeError):
                    self.validate(value)
        value = copy.deepcopy(self.host)
        value["samples"].reverse()
        with self.assertRaises(RuntimeError):
            self.validate(value)

    def test_host_context_pid_main_and_method_roles_fail_closed(self):
        mutations = [lambda value: value["context"].update(source_sha="f" * 40),
            lambda value: value.update(process_id=999), lambda value: value.update(runtime_version=[15, 7, 8]),
            lambda value: [value[key].update(platforms=[1, 6]) for key in ("main_image_before", "main_image_after")],
            lambda value: [value["samples"][0]["native"]["fm"][key]["implementation"].update(image_basename="CoreFoundation")
                for key in ("implementation_before", "implementation_after")]]
        for index, mutate in enumerate(mutations):
            with self.subTest(index=index):
                value = copy.deepcopy(self.host)
                mutate(value)
                with self.assertRaises(RuntimeError):
                    self.validate(value)

    def test_raw_host_failure_is_preserved_before_exit_check(self):
        class FailedCommand:
            preservation_error = probe.Commands.preservation_error
            def __init__(self):
                self.preservation = dict(failures=0, errors=[])
            def capture(self, arguments, label, timeout):
                return dict(status="EXITED", exit_code=1, direct_child_reaped=True), b'{"collection_status":"FAIL"}\n', b"synthetic-error"
        with tempfile.TemporaryDirectory() as temporary:
            instance = object.__new__(probe.Probe)
            instance.evidence = Path(temporary).resolve()
            instance.resources = instance.evidence / "resources"
            instance.commands = FailedCommand()
            with self.assertRaises(RuntimeError):
                instance.capture_host()
            self.assertEqual((instance.evidence / "host.stdout.json").read_bytes(), b'{"collection_status":"FAIL"}\n')
            self.assertEqual((instance.evidence / "host.stderr.txt").read_bytes(), b"synthetic-error")
            with mock.patch.object(probe.native, "write_new", side_effect=OSError("synthetic-write-failure")):
                with self.assertRaises(OSError):
                    instance.capture_host()
            self.assertEqual(instance.commands.preservation, dict(failures=1,
                errors=[dict(operation="host-raw-output", error_type="OSError")]))


if __name__ == "__main__":
    unittest.main()
