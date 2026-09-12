#!/usr/bin/env python3
"""Three bounded shape fixtures; root alone executes these, never the author.

No network/builds/filesystem fixtures. Invalid-argument checks mock side-effect
entry points as a fail-safe. The fixed sibling collector is import-safe.
"""
import copy
import importlib.util
import unittest
from pathlib import Path
from unittest import mock

SPEC = importlib.util.spec_from_file_location("windows_targeted_collector", Path(__file__).with_name("collect_windows.py"))
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)


def synthetic_jobs():
    jobs = []
    for index, (key, name) in enumerate(COLLECTOR.JOB_NAMES.items(), 1):
        job = dict(id=index, name=name, status="completed", conclusion="skipped")
        if key == COLLECTOR.WINDOWS_JOB:
            job.update(conclusion="success", steps=[
                dict(name="Check out source", status="completed", conclusion="success"),
                dict(name="Claim fresh verification output ownership", status="completed", conclusion="success"),
            ])
        jobs.append(job)
    return jobs


class WindowsCollectorShapeTests(unittest.TestCase):
    def test_one_windows_job_accepts_four_skipped_jobs_without_checkout_steps(self):
        jobs = synthetic_jobs()
        contexts = COLLECTOR.windows_job_contexts(jobs)
        self.assertEqual(set(contexts), set(COLLECTOR.JOB_NAMES))
        self.assertTrue(contexts[COLLECTOR.WINDOWS_JOB]["live_path_extraction_admitted"])
        for key, context in contexts.items():
            if key != COLLECTOR.WINDOWS_JOB:
                self.assertEqual(context["conclusion"], "skipped")
                self.assertEqual(context["steps"], [])
                self.assertFalse(context["live_path_extraction_admitted"])
        self.assertEqual(COLLECTOR.ARTIFACT_JOBS, {
            "desktop-verification-desktop-windows-x64": COLLECTOR.WINDOWS_JOB,
            "verification-cleanup-desktop-windows-x64": COLLECTOR.WINDOWS_JOB,
        })
        jobs[0]["steps"] = [dict(name="Skipped unrelated step", status="completed", conclusion="skipped")]
        self.assertFalse(COLLECTOR.windows_job_contexts(jobs)["desktop-android"]["live_path_extraction_admitted"])

    def test_wrong_scope_or_active_job_shape_fails_before_side_effects(self):
        jobs = synthetic_jobs()
        windows = next(index for index, job in enumerate(jobs) if job["name"] == COLLECTOR.JOB_NAMES[COLLECTOR.WINDOWS_JOB])
        variants = [jobs[:-1], jobs + [copy.deepcopy(jobs[0])]]
        for index, conclusion in ((0, "success"), (0, "failure"), (windows, "skipped")):
            changed = copy.deepcopy(jobs)
            changed[index]["conclusion"] = conclusion
            variants.append(changed)
        unknown = copy.deepcopy(jobs)
        unknown[0]["name"] = "Unreviewed job"
        variants.append(unknown)
        executed_step = copy.deepcopy(jobs)
        executed_step[0]["steps"] = [dict(name="Unexpected execution", status="completed", conclusion="success")]
        variants.append(executed_step)
        for changed in variants:
            with self.subTest(jobs=changed), self.assertRaises(ValueError):
                COLLECTOR.windows_job_contexts(changed)
        with mock.patch.object(COLLECTOR.subprocess, "run", side_effect=AssertionError("No API operation allowed")), \
                mock.patch.object(COLLECTOR.subprocess, "check_output", side_effect=AssertionError("No Git operation allowed")), \
                mock.patch.object(COLLECTOR.Path, "mkdir", side_effect=AssertionError("No filesystem mutation allowed")):
            for scope in ("full", "WINDOWS-ONLY", ""):
                with self.subTest(scope=scope), self.assertRaises(ValueError):
                    COLLECTOR.main(["collect_windows.py", "1", "a" * 40, scope])
            with self.assertRaises(ValueError):
                COLLECTOR.main(["collect_windows.py", "1", "a" * 40])

    def test_failed_windows_never_turns_historical_or_unattested_xml_into_current(self):
        jobs = synthetic_jobs()
        windows = next(job for job in jobs if job["name"] == COLLECTOR.JOB_NAMES[COLLECTOR.WINDOWS_JOB])
        windows["conclusion"] = "failure"
        windows["steps"][0]["conclusion"] = "failure"
        windows["steps"][1]["conclusion"] = "skipped"
        context = COLLECTOR.windows_job_contexts(jobs)[COLLECTOR.WINDOWS_JOB]
        self.assertFalse(context["live_path_extraction_admitted"])
        artifact = "desktop-verification-desktop-windows-x64"
        tracked = {"remediation-runs/old/shared/core/build/test-results/desktopTest/TEST-old.xml"}
        roots = ["build", "shared/core/build"]
        classify = lambda name: COLLECTOR.classify_member(name, artifact, 1, COLLECTOR.WINDOWS_JOB, context, tracked, roots)
        live = "parlor/parlor/shared/core/build/test-results/desktopTest/TEST-new.xml"
        old = "parlor/parlor/" + next(iter(tracked))
        self.assertEqual(classify(live), ("UNATTESTED_LIVE_PATH_ARCHIVE_ONLY", False))
        self.assertEqual(classify(old), ("FROZEN_TRACKED_FILE_ARCHIVE_ONLY", False))
        self.assertEqual(classify(old.replace("/old/", "/untracked/")), ("HISTORICAL_SCOPE_ARCHIVE_ONLY", False))
        self.assertEqual(classify("_temp/parlor-verification-1-1-desktop-windows-x64-stop-windows-x64.json"),
                         ("CURRENT_NAMED_RECEIPT_CONTENT_UNREVIEWED", True))
        admitted = COLLECTOR.windows_job_contexts(synthetic_jobs())[COLLECTOR.WINDOWS_JOB]
        self.assertEqual(COLLECTOR.classify_member(live, artifact, 1, COLLECTOR.WINDOWS_JOB, admitted, tracked, roots),
                         ("CURRENT_PATH_CANDIDATE_NOT_EXECUTION", True))


if __name__ == "__main__":
    unittest.main(verbosity=2)
