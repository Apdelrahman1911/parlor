#!/usr/bin/env python3
"""Focused stdlib fixtures; root owns execution in the coordinated local lane.

No network, Gradle, project imports, or CI execution. The sibling reviewed
collector is import-safe. TemporaryDirectory removes only these ZIP fixtures.
"""
import hashlib
import importlib.util
import stat
import struct
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("full_d_collector_02", Path(__file__).with_name("full-d-compact-collector-02.py"))
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)
RUN = 34420745957
JOB = "desktop-windows-x64"
ARTIFACT = "desktop-verification-" + JOB
LIVE = "parlor/parlor/shared/core/build/test-results/desktopTest/TEST-real.xml"
OLD = "parlor/parlor/remediation-runs/old/parlor/parlor/shared/core/build/test-results/desktopTest/TEST-old.xml"
RECEIPT = f"_temp/parlor-verification-{RUN}-1-{JOB}-stop-windows-x64.json"


class CollectorFixtures(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="parlor-full-d-collector-02-fixture-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / "fixture.zip"

    def write_archive(self, entries):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)  # Deliberate duplicate ZIP fixtures.
            with zipfile.ZipFile(self.archive, "w", compression=zipfile.ZIP_STORED) as archive:
                for name, content in entries:
                    archive.writestr(name, content)

    def classifier(self, admitted=True, tracked=()):
        return lambda name: COLLECTOR.classify_member(name, ARTIFACT, RUN, JOB,
            {"live_path_extraction_admitted": admitted}, set(tracked), ["build", "shared/core/build"])

    def test_full_table_but_selective_extraction(self):
        opaque = "parlor/parlor/build/ci-evidence/ui.xcresult/Data/data.0"
        unknown = "other/shared/core/build/test-results/TEST-unknown.xml"
        self.write_archive([(LIVE, b"current"), (OLD, b"historical"), (RECEIPT, b"failed receipt"),
                            (opaque, b"opaque"), (unknown, b"unknown")])
        original_sha = COLLECTOR.sha_file(self.archive)
        rows = COLLECTOR.archive_members(self.archive, self.classifier())
        self.assertEqual(len(rows), 5)
        self.assertTrue(all(len(row["sha256"]) == 64 and len(row["crc32"]) == 8 for row in rows))
        COLLECTOR.extract_selected(self.archive, self.root / "selected", rows)
        self.assertEqual({row["path"] for row in rows if row["extracted"]}, {LIVE, RECEIPT})
        self.assertEqual((self.root / "selected" / LIVE).read_bytes(), b"current")
        self.assertFalse((self.root / "selected" / OLD).exists())
        self.assertEqual(COLLECTOR.sha_file(self.archive), original_sha)
        self.assertEqual(rows[1]["sha256"], hashlib.sha256(b"historical").hexdigest())

    def test_failed_windows_checkout_never_admits_reports(self):
        job = {"id": 1, "name": COLLECTOR.JOB_NAMES[JOB], "conclusion": "failure", "steps": [
            {"name": "Check out source", "status": "completed", "conclusion": "failure"},
            {"name": "Claim fresh verification output ownership", "status": "completed", "conclusion": "skipped"}]}
        context = COLLECTOR.job_context(job)
        self.assertFalse(context["live_path_extraction_admitted"])
        self.write_archive([(LIVE, b"not fresh"), (OLD, b"historical"), (RECEIPT, b"failure")])
        rows = COLLECTOR.archive_members(self.archive, self.classifier(context["live_path_extraction_admitted"]))
        self.assertEqual({row["path"] for row in rows if row["selected_for_extraction"]}, {RECEIPT})
        self.assertEqual(rows[0]["classification"], "UNATTESTED_LIVE_PATH_ARCHIVE_ONLY")

    def test_exact_unique_job_steps_required(self):
        check = {"name": "Check out source", "status": "completed", "conclusion": "success"}
        claim = {"name": "Claim fresh verification output ownership", "status": "completed", "conclusion": "success"}
        for steps in ([check], [check, claim, claim]):
            with self.subTest(steps=steps), self.assertRaises(ValueError):
                COLLECTOR.job_context({"id": 1, "name": "x", "conclusion": "success", "steps": steps})
        self.assertTrue(COLLECTOR.job_context({"id": 1, "name": "x", "conclusion": "success", "steps": [check, claim]})["live_path_extraction_admitted"])
        claim["conclusion"] = "failure"
        self.assertFalse(COLLECTOR.job_context({"id": 1, "name": "x", "conclusion": "failure", "steps": [check, claim]})["live_path_extraction_admitted"])

    def test_tracked_build_lookalike_is_not_current(self):
        relative = LIVE[len(COLLECTOR.WORKSPACE_PREFIX):]
        self.assertEqual(self.classifier(tracked=[relative])(LIVE), ("FROZEN_TRACKED_FILE_ARCHIVE_ONLY", False))
        with self.assertRaises(ValueError):
            COLLECTOR.live_build_roots({"shared/core/build.gradle.kts", relative})
        self.assertEqual(self.classifier()(LIVE[len(COLLECTOR.WORKSPACE_PREFIX):]), ("UNKNOWN_ARCHIVE_ONLY", False))

    def test_current_cleanup_name_only(self):
        name = f"parlor-verification-{RUN}-1-{JOB}-cleanup.json"
        classify = lambda value: COLLECTOR.classify_member(value, "verification-cleanup-" + JOB, RUN, JOB, {}, set(), [])
        self.assertEqual(classify(name), ("CURRENT_NAMED_RECEIPT_CONTENT_UNREVIEWED", True))
        self.assertEqual(classify("old/" + name), ("UNKNOWN_ARCHIVE_ONLY", False))
        self.assertFalse(self.classifier()(RECEIPT.replace(str(RUN), "1"))[1])

    def test_unsafe_paths(self):
        for name in (".", "../bad", "/bad", "a/../bad", "a/./bad", "a//bad", "a\\bad", "C:bad", "a//"):
            with self.subTest(name=name):
                self.write_archive([(name, b"")])
                with self.assertRaises(ValueError):
                    COLLECTOR.archive_members(self.archive)

    def test_nul_original_name_is_rejected(self):
        self.write_archive([("safe!", b"payload")])
        self.archive.write_bytes(self.archive.read_bytes().replace(b"safe!", b"safe\0"))
        with self.assertRaises(ValueError):
            COLLECTOR.archive_members(self.archive)

    def test_special_and_mismatched_types(self):
        for mode, name in ((stat.S_IFLNK, "link"), (stat.S_IFIFO, "fifo"), (stat.S_IFCHR, "device"),
                           (stat.S_IFDIR, "not-a-directory"), (stat.S_IFREG, "not-a-file/")):
            with self.subTest(mode=mode, name=name):
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (mode | 0o600) << 16
                self.write_archive([(info, b"")])
                with self.assertRaises(ValueError):
                    COLLECTOR.archive_members(self.archive)

    def test_duplicate_and_ancestor_collisions(self):
        for entries in (("same", "same"), ("same", "same/"), ("parent", "parent/child"), ("parent/child", "parent")):
            with self.subTest(entries=entries):
                self.write_archive([(name, b"") for name in entries])
                with self.assertRaises(ValueError):
                    COLLECTOR.archive_members(self.archive)

    def test_encryption_flags_rejected(self):
        for flag in (1, 64):
            with self.subTest(flag=flag):
                self.write_archive([("file", b"payload")])
                raw = bytearray(self.archive.read_bytes())
                struct.pack_into("<H", raw, raw.index(b"PK\x03\x04") + 6, flag)
                struct.pack_into("<H", raw, raw.index(b"PK\x01\x02") + 8, flag)
                self.archive.write_bytes(raw)
                with self.assertRaises(ValueError):
                    COLLECTOR.archive_members(self.archive)

    def test_crc_in_unselected_history_still_fails(self):
        self.write_archive([(LIVE, b"current"), (OLD, b"CRC_SENTINEL")])
        self.archive.write_bytes(self.archive.read_bytes().replace(b"CRC_SENTINEL", b"BAD_SENTINEL"))
        with self.assertRaises(zipfile.BadZipFile):
            COLLECTOR.archive_members(self.archive, self.classifier())
        self.assertFalse((self.root / "selected").exists())

    def test_member_total_and_file_bounds(self):
        self.write_archive([("first", b"123"), ("second", b"456")])
        for bounds in ({"max_members": 1}, {"max_total": 5}, {"max_file": 2}):
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                COLLECTOR.archive_members(self.archive, **bounds)

    def test_directory_is_present_in_complete_table(self):
        self.write_archive([("dir/", b""), ("dir/file", b"payload")])
        rows = COLLECTOR.archive_members(self.archive)
        self.assertTrue(rows[0]["directory"])
        self.assertEqual(rows[0]["bytes"], 0)
        self.assertEqual(len(rows), 2)

    def test_extraction_refuses_existing_or_redirected_root(self):
        self.write_archive([(LIVE, b"current")])
        rows = COLLECTOR.archive_members(self.archive, self.classifier())
        existing = self.root / "existing"
        existing.mkdir()
        with self.assertRaises(FileExistsError):
            COLLECTOR.extract_selected(self.archive, existing, rows)
        link = self.root / "link"
        link.symlink_to(existing, target_is_directory=True)
        with self.assertRaises(ValueError):
            COLLECTOR.extract_selected(self.archive, link / "child", rows)
        self.assertFalse((existing / "child").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
