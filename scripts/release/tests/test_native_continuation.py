"""Linux-executable orchestration controls, NOT Apple/application runtime proof."""
from __future__ import annotations

import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import struct
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("native_continuation", ROOT / "scripts/ci/native_continuation.py")
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)


def source_fixture():
    return dict(commit="a" * 40, tree="b" * 40, branch=native.BRANCH, diff_sha256=native.sha(b""),
                source_manifest_sha256="c" * 64, tracked_status="")


def cleanup_fixture():
    source = source_fixture()
    return dict(status="PARTIALLY_VERIFIED", cleanup_status="PASS", cleanup_errors=[],
                temporary_directory_removed=True, source_unchanged=True, controls_unchanged=True,
                controls_after_sha256="d" * 64, approved_control_sha256="d" * 64,
                source_before=source, source_after=source, owned_uuid="test-owned-uuid", owned_device_absent=True,
                owned_processes_remaining=[], unknown_holders=[], secondary_attestation_errors=[],
                finalization_stages=[dict(status="PASS")], copied_sources_unchanged=True,
                xcodebuild_exit_code=65, commands=[dict(command=["xcodebuild", "test"])],
                gradle_stops=[dict(label=label, exit_code=0) for label in ("stop-xcode-immediate", "stop-final")])


def package_fixture(root=ROOT):
    current = dict(repository=native.REPOSITORY, branch=native.BRANCH, head_sha="a" * 40, tree="b" * 40,
                   workflow=native.WORKFLOW, root=str(root), run_id=200, run_attempt=1)
    expected = dict(head_sha="a" * 40, run_id=100, run_attempt=2, artifact_id=300, artifact_sha256="f" * 64)
    binding = dict(schema_version=3, repository=str(root), binding_status="REVIEW_REQUIRED_BOUND", source_identity=source_fixture())
    files = {native.BINDING_NAME: native.json_bytes(binding)}
    hashes = {}
    for label in native.RUNNERS:
        rows = [dict(path=native.CAMPAIGN + "/" + native.BINDING_NAME, sha256=native.sha(files[native.BINDING_NAME]))]
        value = dict(files=rows, control_sha256=native.sha(json.dumps(rows, separators=(",", ":")).encode()))
        if label == "l08":
            value["generated_runner_sha256"] = "e" * 64
        files[label + "-controls.json"] = native.json_bytes(value)
        hashes[label] = expected[label + "_sha256"] = value["control_sha256"]
    observation = {key: value for key, value in source_fixture().items() if key != "tracked_status"}
    preflight = dict(schema_version=1, kind="NATIVE_CONTINUATION_PREFLIGHT", status="REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE",
                     context={**current, "run_id": 100, "run_attempt": 2}, source_observation=observation,
                     binding_relative=native.CAMPAIGN + "/" + native.BINDING_NAME, control_sha256=hashes,
                     toolchain={"name": native.PROFILE, "sdk": "26.2"},
                     files={name: native.sha(raw) for name, raw in files.items()})
    files["preflight.json"] = native.json_bytes(preflight)
    return files, expected, current


def producer_fixture():
    _, expected, _ = package_fixture()
    repository = dict(id=99, full_name=native.REPOSITORY)
    run = dict(id=100, run_attempt=2, event="workflow_dispatch", status="completed", conclusion="success",
               head_sha="a" * 40, head_branch=native.BRANCH, path=native.WORKFLOW, workflow_id=88,
               repository=repository, head_repository=repository.copy())
    workflow = dict(id=88, path=native.WORKFLOW, state="active")
    artifact = dict(id=300, name="native-preflight-100-2", expired=False, digest="sha256:" + "f" * 64,
                    size_in_bytes=1024, workflow_run=dict(id=100, head_sha="a" * 40, head_branch=native.BRANCH,
                                                        repository_id=99, head_repository_id=99))
    return run, workflow, artifact, expected


def zipped(files, info=None):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, raw in files.items():
            archive.writestr(info if info and info.filename == name else name, raw)
    return output.getvalue()


class NativeScopeTest(unittest.TestCase):
    def test_push_and_pr_always_select_full_even_if_input_is_present(self):
        for event in ("push", "pull_request"):
            for value in (None, "full", "native-evidence", "bad"):
                self.assertEqual(native.effective_scope(event, value), "full")

    def test_only_explicit_supported_dispatch_scopes_are_accepted(self):
        for value in ("full", "native-preflight", "native-evidence", "native-process-probe"):
            self.assertEqual(native.effective_scope("workflow_dispatch", value), value)
        for value in (None, "", "FULL", "skip", "native"):
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, "unknown-verification-scope"):
                native.effective_scope("workflow_dispatch", value)

    def test_frozen_context_requires_source_branch_workflow_and_full_history(self):
        env = dict(GITHUB_ACTIONS="true", GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_JOB="ios",
                   GITHUB_REPOSITORY=native.REPOSITORY, GITHUB_REF="refs/heads/" + native.BRANCH,
                   GITHUB_WORKFLOW_REF=native.REPOSITORY + "/" + native.WORKFLOW + "@refs/heads/" + native.BRANCH,
                   PARLOR_FROZEN_SOURCE_SHA="a" * 40, GITHUB_SHA="a" * 40, GITHUB_WORKFLOW_SHA="a" * 40,
                   GITHUB_RUN_ID="100", GITHUB_RUN_ATTEMPT="1", GITHUB_WORKSPACE=str(ROOT))
        values = {("rev-parse", "--show-toplevel"): str(ROOT), ("rev-parse", "HEAD"): "a" * 40,
                  ("branch", "--show-current"): native.BRANCH, ("rev-parse", "--is-shallow-repository"): "false",
                  ("status", "--porcelain=v1", "--untracked-files=no"): "", ("rev-parse", "HEAD^{tree}"): "b" * 40}
        with patch.object(native, "command", side_effect=lambda args, root: values[tuple(args[1:])].encode()):
            self.assertEqual(native.context(env)["head_sha"], "a" * 40)
            explicit = Mock(side_effect=lambda args, root: values[tuple(args[1:])].encode())
            with patch.object(native, "command", side_effect=AssertionError("unexpected default executor")):
                self.assertEqual(native.context(env, execute=explicit)["head_sha"], "a" * 40)
            self.assertEqual(explicit.call_count, 6)
            for key, bad in (("GITHUB_SHA", "d" * 40), ("GITHUB_WORKFLOW_SHA", "d" * 40),
                             ("GITHUB_REF", "refs/heads/main"), ("GITHUB_REPOSITORY", "other/parlor"),
                             ("GITHUB_EVENT_NAME", "push"), ("GITHUB_RUN_ID", "0")):
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    native.context({**env, key: bad})
            values[("rev-parse", "--is-shallow-repository")] = "true"
            with self.assertRaisesRegex(RuntimeError, "full-history"):
                native.context(env)

    def test_probe_executor_is_injected_without_replacing_default_commands(self):
        with patch.object(native, "command") as default:
            explicit = Mock(side_effect=RuntimeError("explicit-probe-executor"))
            with self.assertRaisesRegex(RuntimeError, "qualified-arm64"), patch.object(native.platform, "system", return_value="Linux"):
                native.qualified_platform(execute=explicit, environment={})
            explicit.assert_not_called()
            default.assert_not_called()
            with patch.object(native.platform, "system", return_value="Darwin"), \
                    patch.object(native.platform, "machine", return_value="arm64"), \
                    self.assertRaisesRegex(RuntimeError, "explicit-probe-executor"):
                native.qualified_platform(execute=explicit, environment={
                    "DEVELOPER_DIR": "/Applications/Xcode_26.3.app/Contents/Developer"})
            explicit.assert_called_once_with(["/usr/bin/xcodebuild", "-version"])
            default.assert_not_called()


class NativeArtifactTest(unittest.TestCase):
    def test_exact_preflight_zip_and_package_are_accepted_without_execution(self):
        files, expected, current = package_fixture()
        self.assertEqual(native.unpack_preflight(zipped(files)), files)
        self.assertEqual(native.validate_package(files, expected, current)["status"], "REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE")

    def test_package_rejects_root_source_attempt_binding_or_review_hash_drift(self):
        files, expected, current = package_fixture()
        for key, bad in (("root", "/different/checkout"), ("head_sha", "0" * 40), ("tree", "0" * 40), ("branch", "main")):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native.validate_package(files, expected, {**current, key: bad})
        for key, bad in (("run_attempt", 3), ("run_id", 101), ("l08_sha256", "0" * 64), ("normal_sha256", "0" * 64)):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native.validate_package(files, {**expected, key: bad}, current)
        for name in (native.BINDING_NAME, "l08-controls.json", "normal-controls.json"):
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                native.validate_package({**files, name: files[name] + b" "}, expected, current)

    def test_package_cannot_claim_runtime_pass_or_accept_duplicate_json_keys(self):
        files, expected, current = package_fixture()
        preflight = json.loads(files["preflight.json"])
        preflight["status"] = "PASS"
        with self.assertRaisesRegex(RuntimeError, "preflight-package"):
            native.validate_package({**files, "preflight.json": native.json_bytes(preflight)}, expected, current)
        with self.assertRaisesRegex(RuntimeError, "duplicate-json-key"):
            native.decode(b'{"status":"PASS","status":"FAIL"}')

    def test_zip_refuses_path_traversal_extras_duplicates_and_missing_entries(self):
        files, _, _ = package_fixture()
        for name in ("../binding.json", "/binding.json", "sub/file", "C:\\file", "extra.json"):
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                native.unpack_preflight(zipped({**files, name: b"x"}))
        missing = files.copy()
        missing.pop("normal-controls.json")
        with self.assertRaises(RuntimeError):
            native.unpack_preflight(zipped(missing))
        raw = io.BytesIO(zipped(files))
        with zipfile.ZipFile(raw, "a") as archive:
            # Suppress only zipfile's expected duplicate-name warning in this negative fixture.
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr("preflight.json", b"{}")
        with self.assertRaises(RuntimeError):
            native.unpack_preflight(raw.getvalue())

    def test_zip_refuses_symlink_fifo_and_encrypted_members(self):
        files, _, _ = package_fixture()
        for mode in (stat.S_IFLNK, stat.S_IFIFO, stat.S_IFDIR):
            info = zipfile.ZipInfo("preflight.json")
            info.create_system = 3
            info.external_attr = (mode | 0o600) << 16
            with self.subTest(mode=mode), self.assertRaisesRegex(RuntimeError, "unsafe-preflight"):
                native.unpack_preflight(zipped(files, info))
        raw = bytearray(zipped(files))
        for marker, offset in ((b"PK\x03\x04", 6), (b"PK\x01\x02", 8)):
            position = raw.index(marker) + offset
            struct.pack_into("<H", raw, position, struct.unpack_from("<H", raw, position)[0] | 1)
        with self.assertRaisesRegex(RuntimeError, "unsafe-preflight"):
            native.unpack_preflight(bytes(raw))

    def test_zip_has_independent_archive_and_member_size_limits(self):
        files, _, _ = package_fixture()
        raw = zipped(files)
        with patch.object(native, "MAX_ZIP", len(raw) - 1), self.assertRaisesRegex(RuntimeError, "unbounded-preflight-zip"):
            native.unpack_preflight(raw)
        with patch.object(native, "MAX_FILE", 8), self.assertRaisesRegex(RuntimeError, "unsafe-preflight"):
            native.unpack_preflight(raw)

    def test_producer_requires_exact_successful_attempt_source_workflow_and_artifact(self):
        run, workflow, artifact, expected = producer_fixture()
        native.validate_producer(run, workflow, artifact, expected)
        for key, bad in (("run_attempt", 1), ("head_sha", "b" * 40), ("head_branch", "main"),
                         ("event", "push"), ("conclusion", "failure"), ("status", "in_progress"),
                         ("path", ".github/workflows/testing-candidate.yml")):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native.validate_producer({**run, key: bad}, workflow, artifact, expected)
        for key, bad in (("id", 301), ("name", "native-preflight-100-1"), ("expired", True),
                         ("digest", "sha256:" + "0" * 64), ("size_in_bytes", native.MAX_ZIP + 1)):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native.validate_producer(run, workflow, {**artifact, key: bad}, expected)
        with self.assertRaises(RuntimeError):
            native.validate_producer(run, {**workflow, "id": 77}, artifact, expected)

    def test_artifact_cannot_come_from_another_repository_or_run(self):
        run, workflow, artifact, expected = producer_fixture()
        changed = copy.deepcopy(run)
        changed["head_repository"]["full_name"] = "fork/parlor"
        with self.assertRaises(RuntimeError):
            native.validate_producer(changed, workflow, artifact, expected)
        for key, bad in (("id", 101), ("head_sha", "b" * 40), ("head_branch", "main"),
                         ("repository_id", 98), ("head_repository_id", 98)):
            changed_artifact = copy.deepcopy(artifact)
            changed_artifact["workflow_run"][key] = bad
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native.validate_producer(run, workflow, changed_artifact, expected)

    def test_retained_api_observation_supports_offline_validation_without_urls_or_actor_data(self):
        run, workflow, artifact, expected = producer_fixture()
        run.update(url="private-or-signed-url-not-needed", actor={"unneeded": "identity"})
        artifact["archive_download_url"] = "signed-url-not-needed"
        observed = native.producer_evidence(run, workflow, artifact)
        native.validate_producer(observed["run"], observed["workflow"], observed["artifact"], expected)
        self.assertNotIn("url", json.dumps(observed))
        self.assertNotIn("actor", json.dumps(observed))

    def test_signed_download_never_forwards_the_api_token(self):
        signed = "https://example.blob.core.windows.net/owned/artifact.zip?sig=temporary"
        redirect = urllib.error.HTTPError("https://api.github.com/owned", 302, "redirect", {"Location": signed}, None)
        response = io.BytesIO(b"zip-bytes")
        response.status, response.headers = 200, {}
        opener = Mock()
        opener.open.return_value = response
        with patch.object(native, "api_request", side_effect=redirect), patch.object(native.urllib.request, "build_opener", return_value=opener):
            self.assertEqual(native.download_zip("/fixed/api/zip", "DO-NOT-FORWARD"), b"zip-bytes")
        request = opener.open.call_args.args[0]
        self.assertEqual(request.full_url, signed)
        self.assertEqual(request.header_items(), [])
        self.assertEqual(request.get_method(), "GET")

    def test_download_rejects_untrusted_or_plaintext_redirects(self):
        for url in ("http://example.blob.core.windows.net/x", "https://attacker.invalid/x",
                    "https://user:password@example.blob.core.windows.net/x", "https://example.blob.core.windows.net/x#fragment"):
            redirect = urllib.error.HTTPError("https://api.github.com/owned", 302, "redirect", {"Location": url}, None)
            with self.subTest(url=url), patch.object(native, "api_request", side_effect=redirect), self.assertRaises(RuntimeError):
                native.download_zip("/fixed/api/zip", "token")

    def test_authenticated_api_is_read_only_and_fixed_to_the_same_repository(self):
        opener = Mock()
        with patch.object(native.urllib.request, "build_opener", return_value=opener):
            native.api_request("/repos/" + native.REPOSITORY + "/actions/runs/100", "token")
            request = opener.open.call_args.args[0]
            self.assertEqual(request.get_method(), "GET")
            self.assertEqual(request.full_url, "https://api.github.com/repos/" + native.REPOSITORY + "/actions/runs/100")
            for endpoint in ("https://attacker.invalid", "/repos/fork/parlor/actions/runs/100", "/repos/" + native.REPOSITORY + "/issues"):
                with self.assertRaises(RuntimeError):
                    native.api_request(endpoint, "token")


class NativeCleanupTest(unittest.TestCase):
    def test_failed_strict_receipt_can_be_cleanup_safe_without_becoming_pass(self):
        value = cleanup_fixture()
        self.assertTrue(native.cleanup_is_safe(value, "d" * 64, source_fixture()))
        self.assertEqual(value["status"], "PARTIALLY_VERIFIED")

    def test_archived_a16_and_b15_actual_cleanup_shapes_remain_compatible(self):
        for cycle in ("ios-readiness-16", "ios-readiness-15"):
            value = json.loads((ROOT / native.OLD_CAMPAIGN / "evidence" / cycle / "receipt.json").read_bytes())
            with self.subTest(cycle=cycle):
                self.assertTrue(native.cleanup_is_safe(value, value["approved_control_sha256"], value["source_before"]))
        # These are old failed runs, read as schema controls only; no new native execution.

    def test_cleanup_refuses_contradictory_worker_holder_source_or_stop_receipts(self):
        for key, bad in (("cleanup_status", "FAIL"), ("cleanup_errors", ["failed"]),
                         ("owned_processes_remaining", [123]), ("unknown_holders", [123]),
                         ("secondary_attestation_errors", ["unattested"]), ("owned_device_absent", False),
                         ("temporary_directory_removed", False), ("source_unchanged", False),
                         ("controls_unchanged", False), ("copied_sources_unchanged", False),
                         ("gradle_stops", []), ("remaining_outputs", ["build"]),
                         ("finalization_stages", [dict(status="FAIL")])):
            value = {**cleanup_fixture(), key: bad}
            with self.subTest(key=key):
                self.assertFalse(native.cleanup_is_safe(value, "d" * 64, source_fixture()))
        value = cleanup_fixture()
        value["gradle_stops"][0]["exit_code"] = 1
        self.assertFalse(native.cleanup_is_safe(value, "d" * 64, source_fixture()))

    def test_prebuild_toolchain_failure_does_not_invent_a_gradle_attempt(self):
        value = cleanup_fixture()
        value.pop("xcodebuild_exit_code")
        value.update(commands=[dict(command=["xcodebuild", "-version"])], gradle_stops=[], copied_sources_unchanged=False)
        self.assertTrue(native.cleanup_is_safe(value, "d" * 64, source_fixture()))

    def test_unsafe_file_descriptor_or_symlink_is_rejected(self):
        with TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            one, two = root / "one", root / "two"
            one.write_bytes(b"expected")
            two.write_bytes(b"different")
            real_open = native.os.open
            with patch.object(native.os, "open", side_effect=lambda *args: real_open(two, os.O_RDONLY)):
                with self.assertRaisesRegex(RuntimeError, "file-replaced-before-open"):
                    native.file_bytes(one)
            (root / "link").symlink_to(one)
            with self.assertRaises(RuntimeError):
                native.file_bytes(root / "link")

    def test_post_descriptor_hardlink_or_mode_change_is_not_hidden_by_stable_path_inode(self):
        for mutation in ("hardlink", "mode"):
            with self.subTest(mutation=mutation), TemporaryDirectory() as raw:
                root = Path(raw).resolve()
                target = root / "evidence"
                target.write_bytes(b"same bytes and inode")
                original_lstat = Path.lstat
                calls = 0
                def changed(path):
                    nonlocal calls
                    if path == target:
                        calls += 1
                        if calls == 2:  # After the fd was read, checked and closed.
                            if mutation == "hardlink":
                                os.link(target, root / "new-hardlink")
                            else:
                                os.chmod(target, original_lstat(target).st_mode ^ stat.S_IXUSR)
                    return original_lstat(path)
                with patch.object(Path, "lstat", changed), self.assertRaisesRegex(RuntimeError, "file-changed-during-read"):
                    native.file_bytes(target)

    def test_malformed_native_receipt_is_preserved_before_parsing(self):
        with TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            lane = native.Continuation.__new__(native.Continuation)
            lane.root, lane.bundle = root, root / "bundle"
            lane.bundle.mkdir()
            lane.binding = root / native.CAMPAIGN / native.BINDING_NAME
            lane.binding.parent.mkdir(parents=True)
            lane.state = dict(runs={}, files={}, directories={}, cleanup_safe=True)
            lane.save = Mock()
            def invalid_run(arguments, log, entry):
                destination = root / native.CAMPAIGN / "evidence" / native.CYCLES["l08"]
                destination.mkdir(parents=True)
                (destination / "receipt.json").write_bytes(b"{malformed native failure")
                (destination / "xcodebuild.log").write_bytes(b"retained compiler/runtime failure")
                log.write_bytes(b"native exit failure")
                entry["exit_code"] = 1
            lane.invoke_native = invalid_run
            with self.assertRaises(json.JSONDecodeError):
                lane.run_native("l08", "d" * 64, source_fixture())
            saved = lane.bundle / native.CYCLES["l08"]
            self.assertEqual((saved / "receipt.json").read_bytes(), b"{malformed native failure")
            self.assertTrue((saved / "xcodebuild.log").is_file())
            self.assertFalse(lane.state["cleanup_safe"])

    def test_missing_receipt_and_partial_preservation_retain_available_raw_logs(self):
        with TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            source, target = root / "native", root / "bundle"
            source.mkdir()
            (source / "xcodebuild.log").write_bytes(b"failure before receipt")
            (source / "bad-link").symlink_to(source / "xcodebuild.log")
            result = native.preserve_native_evidence(source, target)
            self.assertEqual(result["status"], "INCOMPLETE")
            self.assertEqual((target / "xcodebuild.log").read_bytes(), b"failure before receipt")
            self.assertFalse((target / "bad-link").exists())

    def test_directory_read_failure_cannot_be_silently_omitted_from_preservation(self):
        with TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            source, target = root / "native", root / "bundle"
            source.mkdir()
            (source / "receipt.json").write_bytes(b"raw failed receipt retained")
            def unreadable_child(path, followlinks=False, onerror=None):
                yield str(path), [], ["receipt.json"]
                if onerror is not None:
                    onerror(PermissionError("synthetic scandir failure"))
                # Mirrors os.walk's old default: without onerror, silently omit it.
            with patch.object(native.os, "walk", side_effect=unreadable_child):
                with self.assertRaises(PermissionError):
                    native.tree_manifest(source)
                result = native.preserve_native_evidence(source, target)
            self.assertEqual(result["status"], "INCOMPLETE")
            self.assertEqual(result["errors"][-1]["reason"], "directory-read-failed")
            self.assertEqual((target / "receipt.json").read_bytes(), b"raw failed receipt retained")

    def test_native_a_partial_runs_b_but_unsafe_cleanup_does_not(self):
        lane = native.Continuation.__new__(native.Continuation)
        lane.state = {}
        lane.fetch_preflight = Mock(return_value=({"l08_sha256": "d" * 64, "normal_sha256": "e" * 64}, source_fixture()))
        lane.bootstrap_cache_directories = Mock()
        lane.run_native = Mock(side_effect=[(2, "PARTIALLY_VERIFIED"), (0, "PASS")])
        self.assertEqual(lane.evidence(), 2)
        self.assertEqual(lane.state["status"], "NOT_READY")
        self.assertEqual([call.args[0] for call in lane.run_native.call_args_list], ["l08", "normal"])
        lane.run_native = Mock(side_effect=RuntimeError("unsafe-cleanup"))
        with self.assertRaisesRegex(RuntimeError, "unsafe-cleanup"):
            lane.evidence()
        self.assertEqual(lane.run_native.call_count, 1)

    def test_native_timeout_sends_term_and_waits_for_finalizer_without_kill(self):
        with TemporaryDirectory() as raw:
            lane = native.Continuation.__new__(native.Continuation)
            lane.root = Path(raw).resolve()
            lane.env, lane.save = {}, Mock()
            child = Mock()
            child.wait.side_effect = [subprocess.TimeoutExpired("native", 6000), 1]
            entry = {}
            with patch.object(native.subprocess, "Popen", return_value=child), patch.object(native.signal, "signal", return_value=None) as handlers:
                lane.invoke_native(["not-executed"], Path(raw).resolve() / "runner.log", entry)
            self.assertTrue(entry["timed_out"])
            self.assertEqual(entry["exit_code"], 1)
            self.assertEqual(child.wait.call_args_list[-1].kwargs, {"timeout": 600})
            child.send_signal.assert_called_once_with(native.signal.SIGTERM)
            child.kill.assert_not_called()
            self.assertEqual(handlers.call_count, 4)

    def test_native_unfinished_finalizer_keeps_cleanup_unsafe_and_does_not_kill(self):
        with TemporaryDirectory() as raw:
            lane = native.Continuation.__new__(native.Continuation)
            lane.root = Path(raw).resolve()
            lane.env, lane.save = {}, Mock()
            child = Mock()
            child.wait.side_effect = [subprocess.TimeoutExpired("native", 6000), subprocess.TimeoutExpired("native", 600)]
            with patch.object(native.subprocess, "Popen", return_value=child), patch.object(native.signal, "signal", return_value=None):
                with self.assertRaisesRegex(RuntimeError, "finalizer-did-not-finish"):
                    lane.invoke_native(["not-executed"], Path(raw).resolve() / "runner.log", {})
            lane.save.assert_called_once()
            child.kill.assert_not_called()

    def test_signal_during_popen_assignment_is_forwarded_after_exact_child_is_known(self):
        with TemporaryDirectory() as raw:
            lane = native.Continuation.__new__(native.Continuation)
            lane.root, lane.env, lane.save = Path(raw).resolve(), {}, Mock()
            child = Mock()
            child.wait.return_value = 143
            callbacks = {}
            def register(number, callback):
                callbacks[number] = callback
                return None
            def launch(*args, **kwargs):
                callbacks[native.signal.SIGTERM](native.signal.SIGTERM, None)
                return child
            entry = {}
            with patch.object(native.subprocess, "Popen", side_effect=launch), patch.object(native.signal, "signal", side_effect=register):
                lane.invoke_native(["not-executed"], Path(raw).resolve() / "runner.log", entry)
            self.assertEqual(entry["interrupted"], native.signal.SIGTERM)
            child.send_signal.assert_called_once_with(native.signal.SIGTERM)
            child.wait.assert_called_once_with(timeout=600)
            child.kill.assert_not_called()
            self.assertEqual(callbacks, {native.signal.SIGINT: None, native.signal.SIGTERM: None})

    def prepare_cleanup_lane(self, temporary):
        root = temporary / "checkout"
        root.mkdir()
        lane = native.Continuation.__new__(native.Continuation)
        lane.root, lane.base = root, temporary / "staging"
        lane.bundle = lane.base / "bundle"
        lane.bundle.mkdir(parents=True)
        lane.cleanup_path = temporary / "cleanup.json"
        lane.binding = root / native.CAMPAIGN / native.BINDING_NAME
        lane.binding.parent.mkdir(parents=True)
        lane.binding.write_bytes(b"owned source binding")
        (lane.bundle / "retained-evidence.json").write_bytes(b"{}")
        lane.context = {"head_sha": "a" * 40, "root": str(root)}
        lane.env = dict(PARLOR_NATIVE_UPLOAD_OUTCOME="success", PARLOR_NATIVE_ARTIFACT_ID="300",
                        PARLOR_NATIVE_ARTIFACT_DIGEST="e" * 64)
        lane.state = dict(context=lane.context, base_custody=native.custody(lane.base), cleanup_safe=True,
                         directories={}, runs={}, files={}, bundle_manifest=native.tree_manifest(lane.bundle))
        lane.claim_file(lane.binding)
        return lane

    def test_cleanup_removes_only_owned_outputs_after_successful_upload(self):
        with TemporaryDirectory() as raw:
            temporary = Path(raw).resolve()
            lane = self.prepare_cleanup_lane(temporary)
            sentinel = temporary / "unrelated-user-file"
            sentinel.write_bytes(b"preserve")
            with patch.object(native, "command", return_value=b""), patch("builtins.print"):
                self.assertEqual(lane.cleanup(), 0)
            self.assertFalse(lane.base.exists())
            self.assertFalse(lane.binding.exists())
            self.assertEqual(sentinel.read_bytes(), b"preserve")
            self.assertEqual(json.loads(lane.cleanup_path.read_bytes())["status"], "PASS")

    def test_failed_missing_upload_or_unsafe_cleanup_preserves_last_local_custody(self):
        for mutation in ("failed-upload", "missing-id", "unsafe-native"):
            with self.subTest(mutation=mutation), TemporaryDirectory() as raw:
                lane = self.prepare_cleanup_lane(Path(raw).resolve())
                if mutation == "failed-upload":
                    lane.env["PARLOR_NATIVE_UPLOAD_OUTCOME"] = "failure"
                elif mutation == "missing-id":
                    lane.env.pop("PARLOR_NATIVE_ARTIFACT_ID")
                else:
                    lane.state["cleanup_safe"] = False
                    lane.save()
                with patch.object(native, "command", return_value=b""), patch("builtins.print"):
                    self.assertEqual(lane.cleanup(), 1)
                self.assertTrue(lane.binding.is_file())
                self.assertTrue(lane.bundle.is_dir())
                self.assertEqual(json.loads(lane.cleanup_path.read_bytes())["status"], "FAIL")

    def test_changed_hash_inode_or_symlink_prevents_any_cleanup_deletion(self):
        for mutation in ("hash", "inode", "symlink"):
            with self.subTest(mutation=mutation), TemporaryDirectory() as raw:
                temporary = Path(raw).resolve()
                lane = self.prepare_cleanup_lane(temporary)
                if mutation == "hash":
                    lane.binding.write_bytes(b"changed")
                else:
                    other = temporary / "prior-owned-inode"
                    lane.binding.rename(other)
                    if mutation == "inode":
                        lane.binding.write_bytes(other.read_bytes())
                    else:
                        lane.binding.symlink_to(other)
                with patch.object(native, "command", return_value=b""), patch("builtins.print"):
                    self.assertEqual(lane.cleanup(), 1)
                self.assertTrue(lane.base.is_dir())
                self.assertTrue(lane.binding.exists())
                self.assertEqual(json.loads(lane.cleanup_path.read_bytes())["removed"], [])

    def test_postqualification_deletion_failure_is_not_reported_as_pass(self):
        with TemporaryDirectory() as raw:
            lane = self.prepare_cleanup_lane(Path(raw).resolve())
            with patch.object(native, "command", return_value=b""), patch.object(native.shutil, "rmtree", side_effect=OSError("synthetic")), patch("builtins.print"):
                self.assertEqual(lane.cleanup(), 1)
            self.assertTrue(lane.bundle.is_dir())
            self.assertEqual(json.loads(lane.cleanup_path.read_bytes())["status"], "FAIL")


class NativePreflightTest(unittest.TestCase):
    def prepare_lane(self, temporary):
        lane = native.Continuation.__new__(native.Continuation)
        lane.root = temporary / "checkout"
        lane.root.mkdir()
        lane.bundle = temporary / "bundle"
        lane.bundle.mkdir()
        lane.binding = lane.root / native.CAMPAIGN / native.BINDING_NAME
        lane.binding.parent.mkdir(parents=True)
        lane.state = dict(files={}, cleanup_safe=True, toolchain={"name": native.PROFILE, "sdk": "26.2"})
        lane.save = Mock()
        lane.invoke_native, lane.bootstrap_cache_directories = Mock(), Mock()
        return lane

    def test_preflight_only_binds_and_observes_exact_four_file_package(self):
        with TemporaryDirectory() as raw:
            lane = self.prepare_lane(Path(raw).resolve())
            files, _, lane.context = package_fixture(lane.root)
            observation = json.loads(files["preflight.json"])["source_observation"]
            manifests = {label: (files[label + "-controls.json"], json.loads(files[label + "-controls.json"])) for label in native.RUNNERS}
            def bind(arguments, root):
                self.assertEqual(arguments[:2], ["/usr/bin/python3", "-B"])
                self.assertEqual(Path(arguments[2]), root / native.SUPPORT / "bind_source.py")
                self.assertEqual(arguments[3:], [lane.binding, observation["source_manifest_sha256"], observation["diff_sha256"]])
                native.write_new(lane.binding, files[native.BINDING_NAME])
                return b"binding output not approval"
            with patch.object(native, "describe", return_value=observation), patch.object(native, "command", side_effect=bind) as binder, patch.object(native, "controls", return_value=manifests):
                self.assertEqual(lane.preflight(), 0)
            self.assertEqual({path.name for path in lane.bundle.iterdir()}, native.PREFLIGHT_FILES)
            self.assertEqual((lane.bundle / native.BINDING_NAME).read_bytes(), files[native.BINDING_NAME])
            self.assertEqual(json.loads((lane.bundle / "preflight.json").read_bytes())["status"], "REVIEW_REQUIRED_NOT_RUNTIME_EVIDENCE")
            self.assertEqual(binder.call_count, 1)
            lane.invoke_native.assert_not_called()
            lane.bootstrap_cache_directories.assert_not_called()

    def test_fetch_rejects_zip_digest_before_install_and_live_control_drift_before_build(self):
        for mutation in ("zip-digest", "live-control"):
            with self.subTest(mutation=mutation), TemporaryDirectory() as raw:
                lane = self.prepare_lane(Path(raw).resolve())
                files, expected, lane.context = package_fixture(lane.root)
                zip_bytes = zipped(files)
                digest = "0" * 64 if mutation == "zip-digest" else native.sha(zip_bytes)
                lane.env = dict(PARLOR_PREFLIGHT_RUN_ID="100", PARLOR_PREFLIGHT_RUN_ATTEMPT="2", PARLOR_PREFLIGHT_ARTIFACT_ID="300",
                                PARLOR_PREFLIGHT_ARTIFACT_SHA256=digest, PARLOR_APPROVED_L08_CONTROL_SHA256=expected["l08_sha256"],
                                PARLOR_APPROVED_NORMAL_CONTROL_SHA256=expected["normal_sha256"], PARLOR_ACTIONS_READ_TOKEN="synthetic-read-token")
                run, workflow, artifact, _ = producer_fixture()
                artifact["digest"] = "sha256:" + digest
                observation = json.loads(files["preflight.json"])["source_observation"]
                manifests = {label: (files[label + "-controls.json"] + b" ", json.loads(files[label + "-controls.json"])) for label in native.RUNNERS}
                with patch.object(native, "api_json", side_effect=[run, workflow, artifact]), patch.object(native, "download_zip", return_value=zip_bytes), patch.object(native, "describe", return_value=observation), patch.object(native, "controls", return_value=manifests):
                    with self.assertRaisesRegex(RuntimeError, "zip-digest-mismatch" if mutation == "zip-digest" else "live-control-manifest"):
                        lane.fetch_preflight()
                self.assertEqual(lane.binding.exists(), mutation == "live-control")
                lane.invoke_native.assert_not_called()
                lane.bootstrap_cache_directories.assert_not_called()


if __name__ == "__main__":
    unittest.main()
