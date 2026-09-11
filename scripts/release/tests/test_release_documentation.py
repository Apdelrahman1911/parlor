from __future__ import annotations

import re
import unittest
from pathlib import Path


class ReleaseDocumentationTest(unittest.TestCase):
    def test_protected_branches_require_every_current_full_verification_job(self) -> None:
        root = Path(__file__).resolve().parents[3]
        workflow = (root / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        jobs = workflow.split("\njobs:\n", 1)[1]
        job_ids = re.findall(r"(?m)^  ([A-Za-z_][A-Za-z0-9_-]*):$", jobs)
        job_names = re.findall(r"(?m)^    name: (.+)$", jobs)
        self.assertTrue(job_ids, "Verification workflow must declare jobs")
        self.assertEqual(len(job_ids), len(job_names), "Every job needs an explicit check name")
        self.assertEqual(len(job_names), len(set(job_names)), "Required check names must be unique")
        full_job_ids = (
            "desktop-android",
            "desktop-linux-arm64",
            "desktop-macos-x64",
            "desktop-windows-x64",
            "ios",
            "ios-release",
        )
        self.assertCountEqual(job_ids, (*full_job_ids, "ios-protection-probe"))
        names_by_id = dict(zip(job_ids, job_names))

        documentation = (root / "docs/RELEASE_AUTOMATION.md").read_text(encoding="utf-8")
        section = re.search(r"(?ms)^### Protected branches\n(.*?)(?=^#{1,3} |\Z)", documentation)
        self.assertIsNotNone(section, "Protected branch instructions must remain documented")
        assert section is not None
        instructions = section.group(1)
        self.assertIn("require all six mandatory full **Production verification** jobs", instructions)
        required_names = re.findall(r"(?m)^  - `([^`]+)`", instructions)
        self.assertCountEqual(required_names, [names_by_id[job_id] for job_id in full_job_ids])
        self.assertIn(
            f"The opt-in `{names_by_id['ios-protection-probe']}` is not a mandatory full check.",
            instructions,
        )


if __name__ == "__main__":
    unittest.main()
