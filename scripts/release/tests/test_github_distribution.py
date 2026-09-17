from __future__ import annotations

import copy
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

from scripts.release import github_distribution as dist

SOURCE = {"commit": "a" * 40, "tree": "b" * 40, "dirty": False}
ACCEPTANCE = {"source": SOURCE["commit"], "reference": "https://github.com/Apdelrahman1911/parlor/issues/1"}
PIN = "c" * 64


def descriptor(directory, platform="android", mode="candidate"):
    binary = directory / dist.filename(platform, dist.version())
    binary.write_bytes(("synthetic-fixture-" + platform).encode())
    validation = {"passed": True, "artifact_sha256": dist.digest(binary), "package_inspection": True,
                  "signing": "unsigned-rehearsal"}
    if platform != "android":
        validation["image_sha256"] = "d" * 64
    if mode == "candidate":
        validation.update({"acceptance": copy.deepcopy(ACCEPTANCE)})
        if platform == "linux-x64":
            validation["signing"] = "checksum-and-provenance"
        else:
            validation["certificate_sha256"] = PIN
            if platform == "android":
                validation["signing"] = "android-apk"
            elif platform == "windows-x64":
                validation.update(signing="authenticode-timestamped", timestamped=True, nested_signatures=True)
            else:
                validation.update(signing="developer-id-notarized", notarization="Accepted", team_id="TEAM1234AA",
                                  stapled=True, gatekeeper=True, nested_signatures=True)
    return {"schema": 1, "repository": dist.REPOSITORY, "source": copy.deepcopy(SOURCE), "version": dist.version(),
            "platform": platform, "mode": mode, "run_id": 42, "run_attempt": 1, "created_at": "2026-09-17T12:00:00Z",
            "artifact": {"filename": binary.name, "sha256": dist.digest(binary), "bytes": binary.stat().st_size},
            "validation": validation}


def bundle(directory):
    manifest = {"schema": 1, "repository": dist.REPOSITORY, "source": copy.deepcopy(SOURCE), "version": dist.version(),
                "mode": "candidate", "candidate_run": 42, "artifacts": [descriptor(directory, p) for p in dist.PLATFORMS]}
    (directory / "release-manifest.json").write_bytes(dist.canonical(manifest))
    (directory / "SHA256SUMS").write_text("".join(f"{dist.digest(p)}  {p.name}\n" for p in sorted(directory.iterdir())), encoding="ascii")
    return manifest


class MemoryGitHub:
    """Synthetic API only: tests never contact GitHub or publish anything."""
    def __init__(self):
        self.release = None
        self.data = {}
        self.uploads = []
        self.mutations = []
        self.fail_upload = None

    def request(self, path, method="GET", body=None, missing=False):
        if path.startswith("/releases?per_page=100&page="):
            return [] if self.release is None else [copy.deepcopy(self.release)]
        if path.startswith("/releases/tags/"):
            return copy.deepcopy(self.release)
        if path == "/releases" and method == "POST":
            self.mutations.append((method, path))
            self.release = {**body, "id": 9, "assets": [], "html_url": f"https://github.com/{dist.REPOSITORY}/releases/tag/{body['tag_name']}"}
        elif path == "/releases/9" and method == "PATCH":
            self.mutations.append((method, path))
            self.release.update(body)
        elif path != "/releases/9":
            raise AssertionError((path, method))
        return copy.deepcopy(self.release)

    def upload(self, release_id, path):
        if len(self.uploads) == self.fail_upload:
            raise RuntimeError("synthetic partial upload failure")
        index = len(self.data) + 1
        self.data[index] = path.read_bytes()
        self.release["assets"].append({"name": path.name, "id": index, "size": path.stat().st_size})
        self.uploads.append(path.name)

    def download(self, path, destination, maximum=dist.MAX_FILE):
        destination.write_bytes(self.data[int(path.rsplit("/", 1)[1])])


class DistributionDescriptorTest(unittest.TestCase):
    def test_each_platform_requires_its_complete_final_validation(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for platform in dist.PLATFORMS:
                for mode in ("candidate", "rehearsal"):
                    value = descriptor(directory, platform, mode)
                    dist.validate_descriptor(value, directory, SOURCE, mode, 42)
                    for key in value["validation"]:
                        changed = copy.deepcopy(value)
                        del changed["validation"][key]
                        with self.subTest(platform=platform, mode=mode, key=key), self.assertRaises((RuntimeError, KeyError)):
                            dist.validate_descriptor(changed, directory, SOURCE, mode, 42)

    def test_wrong_source_version_signing_hash_size_name_and_attempt_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            valid = descriptor(directory)
            mutations = [("schema", True), ("repository", "attacker/copy"), ("run_id", 43), ("run_attempt", 0),
                         ("mode", "publish"), ("platform", "ios"), ("version", {"name": "9.9.9", "build": 1}),
                         ("source", {**SOURCE, "tree": "f" * 40}), ("source", {**SOURCE, "dirty": True})]
            for key, replacement in mutations:
                changed = {**valid, key: replacement}
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    dist.validate_descriptor(changed, directory, SOURCE, "candidate", 42)
            for key, replacement in (("filename", "../escape.apk"), ("sha256", "0" * 64), ("bytes", True), ("bytes", 0)):
                changed = copy.deepcopy(valid)
                changed["artifact"][key] = replacement
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    dist.validate_descriptor(changed, directory, SOURCE, "candidate", 42)
            changed = copy.deepcopy(valid)
            changed["validation"]["artifact_sha256"] = "1" * 64
            with self.assertRaisesRegex(RuntimeError, "final bytes"):
                dist.validate_descriptor(changed, directory, SOURCE, "candidate", 42)

    def test_publication_pins_are_independent_of_the_candidate_descriptor(self):
        with tempfile.TemporaryDirectory() as temporary:
            manifest = bundle(Path(temporary))
            pins = {"GH_DIST_ANDROID_CERT_SHA256": PIN, "GH_DIST_MACOS_CERT_SHA256": PIN,
                    "GH_DIST_WINDOWS_CERT_SHA256": PIN, "GH_DIST_MACOS_TEAM_ID": "TEAM1234AA"}
            with patch.dict(os.environ, pins, clear=True):
                dist.require_certificate_pins(manifest)
                for key in pins:
                    with patch.dict(os.environ, {key: "wrong"}), self.subTest(key=key), self.assertRaises(RuntimeError):
                        dist.require_certificate_pins(manifest)

    def test_bundle_requires_every_platform_and_exact_checksum_text(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifest = bundle(directory)
            dist.validate_bundle(directory, SOURCE, 42)
            for key, replacement in (("artifacts", manifest["artifacts"][:-1]), ("mode", "rehearsal"), ("candidate_run", 43)):
                (directory / "release-manifest.json").write_bytes(dist.canonical({**manifest, key: replacement}))
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    dist.validate_bundle(directory, SOURCE, 42)
            (directory / "release-manifest.json").write_bytes(dist.canonical(manifest))
            (directory / "SHA256SUMS").write_text("tampered\n")
            with self.assertRaisesRegex(RuntimeError, "checksum"):
                dist.validate_bundle(directory, SOURCE, 42)

    def test_canonical_json_and_bounded_regular_files_only(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "test.json"
            path.write_bytes(dist.canonical({"a": 1}))
            self.assertEqual(dist.load(path), {"a": 1})
            for content in (b'{"a":1}', b'{"a": 1, "a": 2}', b'[]', b'{'):
                path.write_bytes(content)
                with self.assertRaises((RuntimeError, ValueError)):
                    dist.load(path)
            path.write_bytes(b"large")
            with patch.object(dist, "MAX_FILE", 3), self.assertRaises(RuntimeError):
                dist.digest(path)

    def test_archive_rejects_duplicate_unsafe_symlink_and_unexpected_members(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for index, names in enumerate((["a"], ["../a"], ["a", "extra"], ["a", "a"], ["a\\x"], ["a/"])):
                archive = root / f"{index}.zip"
                with zipfile.ZipFile(archive, "w") as zipped:
                    for name in names:
                        zipped.writestr(name, b"fixture")
                if names == ["a"]:
                    dist.extract_archive(archive, root / "good", {"a"})
                else:
                    with self.subTest(names=names), self.assertRaises(RuntimeError):
                        dist.extract_archive(archive, root / f"bad-{index}", {"a"})
            info = zipfile.ZipInfo("a")
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive = root / "link.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr(info, "outside")
            with self.assertRaises(RuntimeError):
                dist.extract_archive(archive, root / "bad-link", {"a"})

    def test_native_tool_failure_and_timeout_never_print_private_arguments_or_output(self):
        marker = "NEVER_PRINT_PRIVATE_SIGNING_VALUE"
        for command, timeout in (([sys.executable, "-c", f"import sys; print('{marker}'); sys.exit(2)", marker], 10),
                                 ([sys.executable, "-c", "import time; time.sleep(20)", marker], 0.01)):
            with self.assertRaises(RuntimeError) as error:
                dist.run(command, timeout=timeout)
            self.assertNotIn(marker, str(error.exception))


class DistributionAuthorityTest(unittest.TestCase):
    def test_installer_versions_are_canonical_and_fit_windows_product_version(self):
        self.assertEqual(dist.installer_version("255.255.65535"), (255, 255, 65535))
        for name in ("01.0.0", "1.02.0", "1.0.00", "1.0", "1.0.0.1", "256.0.0", "1.256.0", "1.0.65536", "1.0.0-rc1"):
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                dist.installer_version(name)

    def test_new_publication_increases_both_msi_version_and_android_build(self):
        api = Mock()
        api.request.return_value = [{"tag_name": "github-v2.0.9-b16", "draft": False}]
        dist.require_new_release_version(api, {"name": "2.0.10", "build": 17})
        for value in ({"name": "2.0.9", "build": 17}, {"name": "2.0.8", "build": 17},
                      {"name": "2.1.0", "build": 16}, {"name": "2.1.0", "build": 15}):
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, "increase both"):
                dist.require_new_release_version(api, value)
        api.request.return_value = [{"tag_name": "github-v2.1.0-b17", "draft": True}]
        with self.assertRaisesRegex(RuntimeError, "increase both"):
            dist.require_new_release_version(api, {"name": "2.0.10", "build": 18})

    def test_version_inventory_is_bounded_and_does_not_confuse_other_release_channels(self):
        api = Mock()
        api.request.return_value = [{"tag_name": dist.tag_name(dist.version())}, {"tag_name": "store-candidate-fixture"}]
        dist.require_new_release_version(api, dist.version())
        api.request.return_value = [{"tag_name": "github-vbroken"}]
        with self.assertRaisesRegex(RuntimeError, "Unrecognized"):
            dist.require_new_release_version(api, dist.version())
        api.request.return_value = [{"tag_name": "store-candidate-fixture"}] * 100
        with self.assertRaisesRegex(RuntimeError, "bounded"):
            dist.require_new_release_version(api, dist.version())

    def test_all_six_checks_must_succeed_for_the_exact_source(self):
        record = {"id": 20, "head_sha": SOURCE["commit"], "conclusion": "success", "event": "workflow_dispatch",
                  "head_repository": {"full_name": dist.REPOSITORY}}
        api = Mock()
        jobs = [{"name": name, "conclusion": "success"} for name in dist.REQUIRED_CHECKS]
        api.request.side_effect = [{"workflow_runs": [record]}, {"total_count": len(jobs), "jobs": jobs}]
        self.assertEqual(dist.require_verification(api, SOURCE["commit"]), 20)
        for missing in range(len(jobs)):
            changed = copy.deepcopy(jobs)
            changed[missing]["conclusion"] = "skipped"
            api.request.side_effect = [{"workflow_runs": [record]}, {"total_count": len(changed), "jobs": changed}]
            with self.assertRaises(RuntimeError):
                dist.require_verification(api, SOURCE["commit"])
        for key, replacement in (("head_sha", "e" * 40), ("conclusion", "failure"), ("event", "pull_request")):
            api.request.side_effect = [{"workflow_runs": [{**record, key: replacement}]}]
            with self.assertRaises(RuntimeError):
                dist.require_verification(api, SOURCE["commit"])

    def test_reviewed_environments_prevent_self_review_bypass_and_wildcard_branch_signing(self):
        environment = {"can_admins_bypass": False, "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True},
                       "protection_rules": [{"type": "required_reviewers", "prevent_self_review": True, "reviewers": [{"id": 10}]}]}
        branches = {"total_count": 1, "branch_policies": [{"type": "branch", "name": "feat/last-light"}]}
        api = Mock()
        api.request.side_effect = [environment, branches]
        dist.require_environment(api, "github-sign-android")
        for changed in ({**environment, "can_admins_bypass": True}, {**environment, "protection_rules": []},
                        {**environment, "deployment_branch_policy": None}):
            api.request.side_effect = [changed, branches]
            with self.assertRaises(RuntimeError):
                dist.require_environment(api, "github-sign-android")
        for branch in ("*", "pull/*", "unreviewed"):
            api.request.side_effect = [environment, {"total_count": 1, "branch_policies": [{"type": "branch", "name": branch}]}]
            with self.assertRaises(RuntimeError):
                dist.require_environment(api, "github-sign-android")

    def test_stale_owner_acceptance_never_qualifies_a_new_commit(self):
        env = {"GH_DIST_ENVIRONMENT": "github-sign-android", "GH_DIST_APPROVED_SHA": SOURCE["commit"],
               "GH_DIST_ACCEPTANCE_SHA": SOURCE["commit"], "GH_DIST_ACCEPTANCE_REFERENCE": ACCEPTANCE["reference"]}
        with patch.object(dist, "require_environment"), patch.dict(os.environ, env, clear=True):
            self.assertEqual(dist.require_approval(Mock(), "github-sign-android", SOURCE), ACCEPTANCE)
            for key in env:
                with patch.dict(os.environ, {key: "old"}), self.subTest(key=key), self.assertRaises(RuntimeError):
                    dist.require_approval(Mock(), "github-sign-android", SOURCE)

    def test_tag_must_resolve_to_frozen_source_and_cannot_loop(self):
        env = {"GITHUB_REF": "refs/tags/" + dist.tag_name(dist.version())}
        api = Mock()
        with patch.dict(os.environ, env, clear=True):
            api.request.return_value = {"object": {"type": "commit", "sha": SOURCE["commit"]}}
            dist.require_tag(api, SOURCE["commit"])
            api.request.return_value = {"object": {"type": "commit", "sha": "e" * 40}}
            with self.assertRaises(RuntimeError):
                dist.require_tag(api, SOURCE["commit"])
            api.request.return_value = {"object": {"type": "tag", "sha": "e" * 40}}
            with self.assertRaisesRegex(RuntimeError, "nested"):
                dist.require_tag(api, SOURCE["commit"])

    def test_rerunning_a_frozen_platform_never_rebuilds_or_overwrites_it(self):
        api = Mock()
        api.artifacts.return_value = [{"name": dist.artifact_name("candidate", "android")}]
        with patch.object(dist, "source", return_value=SOURCE), patch.object(dist, "GitHub", return_value=api), \
                patch.dict(os.environ, {"GH_DIST_MODE": "candidate", "GITHUB_RUN_ID": "42"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "Rerun failed jobs only"):
                dist.assert_new("android")

    def test_missing_expired_ambiguous_or_wrong_archive_digest_is_not_recoverable(self):
        api = Mock()
        for artifacts in ([], [{"name": "wanted", "expired": True}],
                          [{"name": "wanted", "expired": False}, {"name": "wanted", "expired": False}]):
            api.artifacts.return_value = artifacts
            with tempfile.TemporaryDirectory() as temporary, self.assertRaises(RuntimeError):
                dist.fetch_artifact(api, 42, "wanted", Path(temporary) / "out", {"a"})
        api.artifacts.return_value = [{"name": "wanted", "expired": False, "size_in_bytes": 10, "id": 1, "digest": "sha256:" + "0" * 64}]
        api.download.side_effect = lambda path, dest, maximum: dest.write_bytes(b"wrong archive")
        with tempfile.TemporaryDirectory() as temporary, self.assertRaisesRegex(RuntimeError, "archive digest"):
            dist.fetch_artifact(api, 42, "wanted", Path(temporary) / "out", {"a"})


class DistributionPublicationTest(unittest.TestCase):
    def test_upgrade_conflict_prevents_creating_a_release_or_resuming_a_draft(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(dist, "require_tag"), \
                patch.object(dist, "require_new_release_version", side_effect=RuntimeError("upgrade conflict")):
            directory = Path(temporary)
            bundle(directory)
            api = MemoryGitHub()
            with self.assertRaisesRegex(RuntimeError, "upgrade conflict"):
                dist.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(api.mutations, [])
            self.assertEqual(api.uploads, [])

    def test_success_is_public_only_after_every_asset_is_read_back_and_rerun_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(dist, "require_tag") as tag:
            directory = Path(temporary)
            bundle(directory)
            api = MemoryGitHub()
            result = dist.publish_files(api, directory, SOURCE, 42)
            self.assertFalse(result["draft"])
            self.assertEqual(len(api.uploads), 7)
            self.assertEqual(tag.call_count, 2)
            again = dist.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(result, again)
            self.assertEqual(len(api.uploads), 7)
            self.assertEqual(api.mutations, [("POST", "/releases"), ("PATCH", "/releases/9")])

    def test_partial_upload_stays_draft_then_resumes_without_replacing_existing_bytes(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(dist, "require_tag"):
            directory = Path(temporary)
            bundle(directory)
            api = MemoryGitHub()
            api.fail_upload = 3
            with self.assertRaisesRegex(RuntimeError, "partial upload"):
                dist.publish_files(api, directory, SOURCE, 42)
            self.assertTrue(api.release["draft"])
            first = list(api.uploads)
            api.fail_upload = None
            dist.publish_files(api, directory, SOURCE, 42)
            self.assertEqual(api.uploads[:3], first)
            self.assertEqual(len(api.uploads), len(set(api.uploads)))
            self.assertFalse(api.release["draft"])

    def test_conflicting_remote_asset_or_changed_tag_never_becomes_public(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(dist, "require_tag") as tag:
            directory = Path(temporary)
            bundle(directory)
            api = MemoryGitHub()
            api.fail_upload = 1
            with self.assertRaises(RuntimeError):
                dist.publish_files(api, directory, SOURCE, 42)
            api.fail_upload = None
            api.data[1] = b"x" * len(api.data[1])
            with self.assertRaisesRegex(RuntimeError, "bytes conflict"):
                dist.publish_files(api, directory, SOURCE, 42)
            self.assertTrue(api.release["draft"])
            api = MemoryGitHub()
            tag.side_effect = [None, RuntimeError("tag moved")]
            with self.assertRaisesRegex(RuntimeError, "tag moved"):
                dist.publish_files(api, directory, SOURCE, 42)
            self.assertTrue(api.release["draft"])

    def test_visible_release_with_missing_asset_is_not_silently_changed(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(dist, "require_tag"):
            directory = Path(temporary)
            bundle(directory)
            api = MemoryGitHub()
            dist.publish_files(api, directory, SOURCE, 42)
            api.release["assets"].pop()
            with self.assertRaisesRegex(RuntimeError, "visible release is incomplete"):
                dist.publish_files(api, directory, SOURCE, 42)


if __name__ == "__main__":
    unittest.main()
