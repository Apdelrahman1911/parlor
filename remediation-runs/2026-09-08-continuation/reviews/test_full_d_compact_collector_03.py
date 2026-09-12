"""Focused split-job custody guards; no network, build, or native execution."""
import copy
import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "full_d_collector_03", Path(__file__).with_name("full-d-compact-collector-03.py"))
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)
RUN = 123456
SOURCE = "a" * 40


def full_jobs():
    jobs = []
    for index, (key, name) in enumerate(
            {**COLLECTOR.JOB_NAMES, **COLLECTOR.FOCUSED_JOB_NAMES}.items()):
        focused = key in COLLECTOR.FOCUSED_JOB_NAMES
        steps = [] if focused else [
            {"name": name, "status": "completed", "conclusion": "success"}
            for name in ("Check out source", "Claim fresh verification output ownership")]
        jobs.append({"id": index + 1, "name": name, "run_id": RUN,
                     "run_attempt": 1, "head_sha": SOURCE, "status": "completed",
                     "conclusion": "skipped" if focused else "success", "steps": steps})
    return {"total_count": len(jobs), "jobs": jobs}


class SplitJobCustodyTests(unittest.TestCase):
    def test_exact_full_jobs_have_separate_apple_artifact_custody(self):
        contexts = COLLECTOR.full_job_contexts(full_jobs(), RUN, 1, SOURCE)
        self.assertEqual(set(contexts), set(COLLECTOR.JOB_NAMES))
        self.assertEqual(len(contexts), 6)
        self.assertEqual(len(COLLECTOR.ARTIFACT_JOBS), 12)
        self.assertEqual(COLLECTOR.ARTIFACT_JOBS["ios-runtime-verification"], "ios")
        self.assertEqual(COLLECTOR.ARTIFACT_JOBS["ios-release-verification"], "ios-release")
        self.assertNotIn("ios-protection-probe", COLLECTOR.ARTIFACT_JOBS.values())
        for job in ("ios", "ios-release"):
            name = f"_temp/parlor-verification-{RUN}-1-{job}-apple-aggregate-ownership.json"
            own = COLLECTOR.classify_member(name, job, RUN, job, contexts[job], set(), [])
            other = COLLECTOR.classify_member(
                name, job, RUN, "ios-release" if job == "ios" else "ios", {}, set(), [])
            self.assertTrue(own[1])
            self.assertFalse(other[1])

    def test_wrong_scope_and_missing_or_duplicate_lane_are_rejected(self):
        for mutation in ("probe-ran", "release-skipped", "missing", "duplicate"):
            jobs = full_jobs()
            if mutation == "probe-ran":
                jobs["jobs"][-1]["conclusion"] = "success"
            elif mutation == "release-skipped":
                jobs["jobs"][-2]["conclusion"] = "skipped"
            elif mutation == "missing":
                jobs["jobs"].pop()
                jobs["total_count"] -= 1
            else:
                jobs["jobs"][-1] = copy.deepcopy(jobs["jobs"][0])
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                COLLECTOR.full_job_contexts(jobs, RUN, 1, SOURCE)

    def test_every_job_source_attempt_and_completion_is_bound(self):
        for index in range(7):
            for field, value in (("head_sha", "b" * 40), ("run_id", RUN + 1),
                                 ("run_attempt", 2), ("status", "in_progress")):
                jobs = full_jobs()
                jobs["jobs"][index][field] = value
                with self.subTest(index=index, field=field), self.assertRaises(ValueError):
                    COLLECTOR.full_job_contexts(jobs, RUN, 1, SOURCE)

    def test_failed_full_lane_evidence_is_retained_without_live_path_admission(self):
        jobs = full_jobs()
        release = next(job for job in jobs["jobs"]
                       if job["name"] == COLLECTOR.JOB_NAMES["ios-release"])
        release["conclusion"] = "failure"
        release["steps"][0]["conclusion"] = "failure"
        release["steps"][1]["conclusion"] = "skipped"
        contexts = COLLECTOR.full_job_contexts(jobs, RUN, 1, SOURCE)
        self.assertFalse(contexts["ios-release"]["live_path_extraction_admitted"])
        self.assertTrue(contexts["ios"]["live_path_extraction_admitted"])
        name = f"parlor-verification-{RUN}-1-ios-release-cleanup.json"
        self.assertTrue(COLLECTOR.classify_member(
            name, "verification-cleanup-ios-release", RUN, "ios-release",
            contexts["ios-release"], set(), [])[1])


if __name__ == "__main__":
    unittest.main(verbosity=2)
