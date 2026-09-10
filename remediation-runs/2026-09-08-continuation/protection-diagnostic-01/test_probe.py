"""Focused pure control tests. Synthetic records are NOT platform evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

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
        if name in {"directory-create", "complete-write", "none-write", "default-write", "complete-replace", "none-url-set"}:
            selector = "createDirectoryAtPath:withIntermediateDirectories:attributes:error:" if name == "directory-create" else (
                "setResourceValue:forKey:error:" if name == "none-url-set" else "writeToFile:options:error:")
            row = dict(kind="operation", id=name, returned=True, native_error=copy.deepcopy(no_error),
                implementation_before=implementation(selector), implementation_after=implementation(selector))
            if name in {"directory-create", "none-url-set"}:
                row["requested_protection"] = "complete"
            else:
                row.update(requested_options={"complete-write": 0x20000001, "none-write": 0x10000001,
                    "default-write": 1, "complete-replace": 0x20000001}[name], payload_bytes=48 if name == "complete-replace" else 43,
                    observer_descriptor_held_across_write=False)
        elif name == "none-kernel-set":
            row = dict(kind="operation", id=name, sdk_available=False, status="PUBLIC_SDK_COMMAND_OR_CLASS_UNAVAILABLE")
        else:
            directory = name == "directory-baseline"
            inode = 9 if directory else 10 if name == "complete-baseline" else 13 if name == "complete-after-replace" else 12 if name == "default-baseline" else 11
            identity = dict(device=7, inode=inode, uid=501, mode=0o40700 if directory else 0o100600,
                links=2 if directory else 1, size=64 if directory else 48 if name == "complete-after-replace" else 43,
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
        strict_synthetic_complete=dict(required_sample_ids=list(probe.STRICT), **{"pass": 0, "fail": 4}, status="FAIL"),
        replacement=dict(before=copy.deepcopy(samples["complete-baseline"]["identity"]),
            after=copy.deepcopy(samples["complete-after-replace"]["identity"]), observer_descriptor_held=False, named_inode_changed=True)))
    return rows, request, image_uuid


def validate(rows, request, image_uuid):
    return probe.validate_report(("\n".join(json.dumps(row) for row in rows) + "\n").encode(), request, image_uuid)


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
        self.assertEqual(result["strict_synthetic_complete"]["fail_count"], 4)
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
        execute = lambda args, _label, _timeout: replies[tuple(args[1:])].encode()
        self.assertEqual(probe.context(env, execute, root)["job"], probe.SCOPE)
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


if __name__ == "__main__":
    unittest.main()
