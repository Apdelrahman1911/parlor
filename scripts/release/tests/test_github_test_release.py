from __future__ import annotations

import copy
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.release import github_distribution as dist
from scripts.release import github_test_release as testing
from scripts.release.tests.test_github_distribution import MemoryGitHub, SOURCE


def descriptor(directory: Path, platform: str) -> dict:
    binary = directory / testing.filename(platform)
    binary.write_bytes((platform + " synthetic test binary").encode())
    validation = {"passed": True, "artifact_sha256": dist.digest(binary), "package_inspection": True,
                  "signing": testing.REQUIREMENTS[platform]}
    if platform == "android":
        validation.update(certificate_sha256="c" * 64, application_id="me.parlor.android.test", install_launch_smoke=True)
    else:
        validation["image_sha256"] = "d" * 64
    return {"schema": 1, "repository": dist.REPOSITORY, "source": copy.deepcopy(SOURCE), "version": dist.version(),
            "mode": testing.MODE, "platform": platform, "build_run": 42, "run_attempt": 1,
            "created_at": "2026-09-18T12:00:00Z",
            "artifact": {"filename": binary.name, "sha256": dist.digest(binary), "bytes": binary.stat().st_size},
            "validation": validation}


def bundle(directory: Path) -> dict:
    value = {"schema": 1, "repository": dist.REPOSITORY, "source": copy.deepcopy(SOURCE), "version": dist.version(),
             "mode": testing.MODE, "build_run": 42, "artifacts": [descriptor(directory, platform) for platform in dist.PLATFORMS]}
    (directory / testing.MANIFEST).write_bytes(dist.canonical(value))
    (directory / "SHA256SUMS").write_text(testing.checksums(directory), encoding="ascii")
    return value


class TestReleaseGitHub(MemoryGitHub):
    """In-memory publication boundary: no network, real keys, or GitHub mutations."""
    def __init__(self):
        super().__init__()
        self.tag = None

    def request(self, path, method="GET", body=None, missing=False):
        if path.startswith("/git/ref/tags/"):
            return copy.deepcopy(self.tag)
        if path == "/git/refs":
            if self.tag is not None:
                raise AssertionError("test never moves an existing tag")
            self.tag = {"object": {"type": "commit", "sha": body["sha"],
                        "url": f"https://api.github.com/repos/{dist.REPOSITORY}/git/commits/{body['sha']}"}}
            self.mutations.append((method, path))
            return copy.deepcopy(self.tag)
        return super().request(path, method, body, missing)


class TestReleaseDescriptorTest(unittest.TestCase):
    def test_all_platforms_have_a_closed_test_policy_and_byte_bound_inspection(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for platform in dist.PLATFORMS:
                record = descriptor(directory, platform)
                testing.validate_descriptor(record, directory, SOURCE, 42)
                for key in record["validation"]:
                    changed = copy.deepcopy(record)
                    del changed["validation"][key]
                    with self.subTest(platform=platform, key=key), self.assertRaises(RuntimeError):
                        testing.validate_descriptor(changed, directory, SOURCE, 42)
                for extra in ("acceptance", "private_key", "room_secret"):
                    changed = copy.deepcopy(record)
                    changed["validation"][extra] = "must never appear in release metadata"
                    with self.assertRaises(RuntimeError):
                        testing.validate_descriptor(changed, directory, SOURCE, 42)

    def test_source_mode_version_identity_signature_and_bytes_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            record = descriptor(directory, "android")
            for key, value in (("schema", True), ("mode", "candidate"), ("mode", "rehearsal"), ("build_run", True),
                               ("build_run", 43), ("run_attempt", 0), ("source", {**SOURCE, "dirty": True}),
                               ("repository", "attacker/copy"), ("version", {"name": "1.0.1", "build": 1})):
                with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                    testing.validate_descriptor({**record, key: value}, directory, SOURCE, 42)
            for key, value in (("application_id", "me.parlor.android"), ("application_id", "me.parlor.android.debug"),
                               ("signing", "unsigned-rehearsal"), ("signing", "android-apk"), ("certificate_sha256", ""),
                               ("install_launch_smoke", False), ("artifact_sha256", "e" * 64)):
                changed = copy.deepcopy(record)
                changed["validation"][key] = value
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    testing.validate_descriptor(changed, directory, SOURCE, 42)
            for key, value in (("filename", "../escape.apk"), ("sha256", "f" * 64), ("bytes", True), ("bytes", 1)):
                changed = copy.deepcopy(record)
                changed["artifact"][key] = value
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    testing.validate_descriptor(changed, directory, SOURCE, 42)

    def test_bundle_needs_exactly_five_platforms_checksums_and_no_extra_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            value = bundle(directory)
            testing.validate_bundle(directory, SOURCE, 42)
            for key, changed in (("build_run", 43), ("mode", "rehearsal"), ("mode", "candidate"),
                                  ("artifacts", value["artifacts"][:-1]), ("artifacts", [value["artifacts"][0]] * 5)):
                (directory / testing.MANIFEST).write_bytes(dist.canonical({**value, key: changed}))
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    testing.validate_bundle(directory, SOURCE, 42)
            (directory / testing.MANIFEST).write_bytes(dist.canonical(value))
            secret = directory / "private.p12"
            secret.write_bytes(b"synthetic forbidden extra")
            with self.assertRaisesRegex(RuntimeError, "Unexpected"):
                testing.validate_bundle(directory, SOURCE, 42)
            secret.unlink()
            (directory / "SHA256SUMS").write_text("tampered\n")
            with self.assertRaisesRegex(RuntimeError, "checksums"):
                testing.validate_bundle(directory, SOURCE, 42)

    def test_public_test_bundle_never_qualifies_for_signed_production(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            value = bundle(directory)
            with self.assertRaises(RuntimeError):
                dist.validate_bundle(directory, SOURCE, 42)
            # Renaming the manifest and its run label cannot turn testing into production.
            value["candidate_run"] = value.pop("build_run")
            (directory / "release-manifest.json").write_bytes(dist.canonical(value))
            with self.assertRaisesRegex(RuntimeError, "Not a signed candidate"):
                dist.validate_bundle(directory, SOURCE, 42)


class TestReleaseAuthorityTest(unittest.TestCase):
    def test_only_explicit_maintainer_dispatch_and_acknowledged_publication_are_admitted(self):
        env = {"GH_TEST_INPUT_MODE": "publish", "GH_TEST_PUBLISH_ACK": "true", "GITHUB_EVENT_NAME": "workflow_dispatch",
               "GITHUB_REF": "refs/heads/feat/last-light",
               "GITHUB_WORKFLOW_REF": f"{dist.REPOSITORY}/{testing.WORKFLOW}@refs/heads/feat/last-light"}
        with patch.object(dist, "source", return_value=SOURCE), patch.dict(os.environ, env, clear=True):
            self.assertEqual(testing.require_dispatch("publish"), SOURCE)
            for key, value in (("GH_TEST_PUBLISH_ACK", "false"), ("GH_TEST_INPUT_MODE", "build"),
                               ("GITHUB_EVENT_NAME", "pull_request"), ("GITHUB_EVENT_NAME", "workflow_run"),
                               ("GITHUB_REF", "refs/heads/attacker"), ("GITHUB_WORKFLOW_REF", "attacker/workflow@main")):
                with patch.dict(os.environ, {key: value}), self.subTest(key=key), self.assertRaises(RuntimeError):
                    testing.require_dispatch("publish")

    def test_frozen_test_run_requires_the_exact_workflow_source_and_all_native_jobs(self):
        record = {"head_sha": SOURCE["commit"], "status": "completed", "conclusion": "success", "event": "workflow_dispatch",
                  "head_repository": {"full_name": dist.REPOSITORY}, "head_branch": "feat/last-light", "path": testing.WORKFLOW}
        jobs = [{"name": f"Build public test {p}", "conclusion": "success"} for p in dist.PLATFORMS]
        jobs.append({"name": "Seal public test bundle", "conclusion": "success"})
        api = Mock()
        api.request.side_effect = [record, {"total_count": len(jobs), "jobs": jobs}]
        testing.require_build_run(api, 42, SOURCE)
        for key, changed in (("path", dist.WORKFLOW), ("head_sha", "f" * 40), ("event", "pull_request"),
                             ("status", "in_progress"), ("conclusion", "failure"), ("head_branch", "attacker"),
                             ("head_repository", {"full_name": "attacker/copy"})):
            api.request.side_effect = [{**record, key: changed}]
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                testing.require_build_run(api, 42, SOURCE)
        for i in range(len(jobs)):
            missing = copy.deepcopy(jobs)
            missing[i]["conclusion"] = "skipped"
            api.request.side_effect = [record, {"total_count": len(jobs), "jobs": missing}]
            with self.assertRaises(RuntimeError):
                testing.require_build_run(api, 42, SOURCE)

    def test_a_frozen_platform_is_never_rebuilt_and_tags_are_test_only(self):
        api = Mock()
        api.artifacts.return_value = [{"name": testing.artifact_name("android")}]
        with patch.object(testing, "require_dispatch", return_value=SOURCE), patch.object(dist, "GitHub", return_value=api), \
             patch.dict(os.environ, {"GITHUB_RUN_ID": "42"}), self.assertRaises(RuntimeError):
            testing.assert_new("android")
        self.assertTrue(testing.tag_name(42).startswith("github-test-v"))
        self.assertFalse(testing.tag_name(42).startswith("github-v"))
        for run in (0, -1, True, "42"):
            with self.assertRaises(RuntimeError):
                testing.tag_name(run)

    def test_publication_verifies_all_attestations_before_calling_the_mutating_publisher(self):
        api = Mock()
        with tempfile.TemporaryDirectory() as temporary, patch.object(testing, "OUT", Path(temporary)), \
             patch.object(testing, "require_dispatch", return_value=SOURCE), patch.object(testing, "require_build_run"), \
             patch.object(dist, "require_verification", return_value=7), patch.object(dist, "GitHub", return_value=api), \
             patch.dict(os.environ, {"GH_TEST_INPUT_RUN": "42"}), patch.object(testing, "publish_files") as publish:
            def download(_api, _run, _name, destination, _allowed):
                destination.mkdir()
                bundle(destination)
            with patch.object(dist, "fetch_artifact", side_effect=download), \
                 patch.object(testing, "attest_verified", side_effect=RuntimeError("attestation rejected")), self.assertRaises(RuntimeError):
                testing.publish(42)
            publish.assert_not_called()


class TestReleasePublicationTest(unittest.TestCase):
    def setUp(self):
        self.verify = patch.object(dist, "require_verification", return_value=7)
        self.build = patch.object(testing, "require_build_run")
        self.verify.start()
        self.build.start()
        self.addCleanup(self.verify.stop)
        self.addCleanup(self.build.stop)
        source = patch.object(dist, "source", return_value=SOURCE)
        environment = patch.dict(os.environ, {
            "GH_TEST_INPUT_MODE": "publish", "GH_TEST_INPUT_RUN": "42", "GH_TEST_PUBLISH_ACK": "true",
            "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/feat/last-light",
            "GITHUB_WORKFLOW_REF": f"{dist.REPOSITORY}/{testing.WORKFLOW}@refs/heads/feat/last-light",
        }, clear=True)
        source.start()
        environment.start()
        self.addCleanup(source.stop)
        self.addCleanup(environment.stop)

    def test_upload_then_readback_then_prerelease_visibility_without_latest(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            bundle(directory)
            api = TestReleaseGitHub()
            result = testing.publish_files(api, directory, SOURCE, 42)
            self.assertTrue(result["prerelease"])
            self.assertFalse(result["draft"])
            self.assertEqual(result["make_latest"], "false")
            self.assertEqual(set(api.uploads), testing.bundle_files())
            self.assertIn("NOT a production release", result["body"])
            self.assertIn("disposable test key", result["body"])
            self.assertIn("me.parlor.android.test", result["body"])
            self.assertEqual(api.mutations[0], ("POST", "/git/refs"))
            before = copy.deepcopy(api.mutations)
            testing.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(api.mutations, before)
            self.assertEqual(len(api.uploads), 7)

    def test_partial_failure_stays_draft_and_identical_retry_only_fills_missing_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            bundle(directory)
            api = TestReleaseGitHub()
            api.fail_upload = 2
            with self.assertRaises(RuntimeError):
                testing.publish_files(api, directory, SOURCE, 42)
            self.assertTrue(api.release["draft"])
            api.fail_upload = None
            testing.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(len(api.uploads), 7)
            self.assertFalse(api.release["draft"])

    def test_conflicting_bytes_stable_release_changed_tag_and_extra_assets_never_overwrite(self):
        for mutation in ("bytes", "stable", "tag", "extra", "body"):
            with tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                bundle(directory)
                api = TestReleaseGitHub()
                testing.publish_files(api, directory, SOURCE, 42)
                mutations, uploads = len(api.mutations), len(api.uploads)
                if mutation == "bytes":
                    first = next(iter(api.data))
                    api.data[first] = b"x" * len(api.data[first])
                elif mutation == "stable":
                    api.release["prerelease"] = False
                elif mutation == "tag":
                    api.tag["object"]["sha"] = "f" * 40
                elif mutation == "extra":
                    api.release["assets"].append({"name": "private.p12", "id": 999, "size": 1})
                else:
                    api.release["body"] = "remove test warnings"
                with self.subTest(mutation=mutation), self.assertRaises(RuntimeError):
                    testing.publish_files(api, directory, SOURCE, 42)
                self.assertEqual((len(api.mutations), len(api.uploads)), (mutations, uploads))

    def test_failed_exact_source_verification_prevents_even_tag_creation(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            bundle(directory)
            api = TestReleaseGitHub()
            with patch.object(dist, "require_verification", side_effect=RuntimeError("full verification missing")), self.assertRaises(RuntimeError):
                testing.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(api.mutations, [])

    def test_mutating_boundary_rechecks_authority_source_and_selected_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            bundle(directory)
            api = TestReleaseGitHub()
            for key, value in (("GH_TEST_PUBLISH_ACK", "false"), ("GH_TEST_INPUT_MODE", "build"),
                               ("GH_TEST_INPUT_RUN", "43"), ("GITHUB_EVENT_NAME", "push")):
                with patch.dict(os.environ, {key: value}), self.subTest(key=key), self.assertRaises(RuntimeError):
                    testing.publish_files(api, directory, SOURCE, 42)
            with patch.object(dist, "source", return_value={**SOURCE, "commit": "f" * 40}), self.assertRaises(RuntimeError):
                testing.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(api.mutations, [])
            self.assertEqual(api.uploads, [])

    def test_existing_release_never_recreates_a_missing_tag_even_after_conflicting_edits(self):
        for changed_body in (False, True):
            with tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                bundle(directory)
                api = TestReleaseGitHub()
                testing.publish_files(api, directory, SOURCE, 42)
                api.tag = None
                if changed_body:
                    api.release["body"] = "removed testing limits"
                before = copy.deepcopy(api.mutations)
                with self.subTest(changed_body=changed_body), self.assertRaises(RuntimeError):
                    testing.publish_files(api, directory, SOURCE, 42)
                self.assertIsNone(api.tag)
                self.assertEqual(api.mutations, before)


if __name__ == "__main__":
    unittest.main()
