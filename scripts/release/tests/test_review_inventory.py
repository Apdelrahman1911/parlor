from __future__ import annotations

import csv
import io
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import generate_review_inventory as review_inventory  # noqa: E402


class ReviewInventoryTest(unittest.TestCase):
    def test_mechanical_rows_never_attest_independent_review(self) -> None:
        commit = "a" * 40
        paths = [
            "new-source.kt",
            "changed-source.kt",
            "historical-fix.kt",
            "ARCHITECTURE.md",
            "docs/review/INDEPENDENT_REVIEW_FINDINGS.md",
        ]
        historical_finding = "IR-TEST CLOSED: historical human-reviewed finding"
        content = review_inventory.render_inventory(
            paths,
            {
                "changed-source.kt": ("b" * 40, "fix: a commit is not a review"),
                "historical-fix.kt": (commit, "fix: historical finding"),
            },
            {commit: historical_finding},
        )
        rows = {row["path"]: row for row in csv.DictReader(io.StringIO(content))}
        self.assertEqual(set(paths), set(rows))
        for row in rows.values():
            self.assertEqual(
                "MECHANICALLY INVENTORIED; INDEPENDENT REVIEW NOT ATTESTED",
                row.get("inventory_status", row.get("reviewer_status")),
            )
            self.assertNotIn("reviewer_status", row)
        self.assertEqual(
            historical_finding,
            rows["historical-fix.kt"]["historical_change_or_finding_reference"],
        )
        self.assertIn(
            "No historical change mapped; independent review not attested",
            rows["new-source.kt"]["historical_change_or_finding_reference"],
        )
        self.assertIn(
            "Latest tracked change " + "b" * 40,
            rows["changed-source.kt"]["historical_change_or_finding_reference"],
        )
        for unsupported_claim in (
            "None identified in the independent review",
            "latest review remediation",
            "status banner verified",
            "no product finding",
        ):
            self.assertNotIn(unsupported_claim, content)

    def test_rendering_is_repeatable_and_independent_of_untracked_files(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self.run_git(root, "init", "--quiet")
            (root / "tracked.txt").write_text("tracked\n", encoding="utf-8")
            overrides = root / review_inventory.FINDING_OVERRIDES
            overrides.parent.mkdir(parents=True)
            overrides.write_text("commit_sha,finding\n", encoding="utf-8")
            self.run_git(root, "add", ".")
            self.run_git(root, "config", "user.name", "Inventory Test")
            self.run_git(root, "config", "user.email", "inventory@example.invalid")
            self.run_git(root, "commit", "--quiet", "-m", "test baseline")
            baseline = self.run_git(root, "rev-parse", "HEAD").strip()

            paths, first_text = review_inventory.inventory_content(
                root,
                review_inventory.DEFAULT_OUTPUT,
                baseline,
            )
            _, second_text = review_inventory.inventory_content(
                root,
                review_inventory.DEFAULT_OUTPUT,
                baseline,
            )
            first = first_text.encode("utf-8")
            second = second_text.encode("utf-8")
            self.assertEqual(first, second)

            (root / "untracked.txt").write_text("untracked\n", encoding="utf-8")
            paths_with_untracked_file, third_text = review_inventory.inventory_content(
                root,
                review_inventory.DEFAULT_OUTPUT,
                baseline,
            )
            third = third_text.encode("utf-8")

            self.assertEqual(paths, paths_with_untracked_file)
            self.assertEqual(first, third)
            self.assertNotIn(b"untracked.txt", third)

    def test_finding_overrides_require_full_commit_shas(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            overrides = root / review_inventory.FINDING_OVERRIDES
            overrides.parent.mkdir(parents=True)
            overrides.write_text(
                "commit_sha,finding\n"
                f"{'a' * 40},IR-TEST CLOSED: deterministic evidence\n",
                encoding="utf-8",
            )
            self.assertEqual(
                {"a" * 40: "IR-TEST CLOSED: deterministic evidence"},
                review_inventory.finding_overrides(root),
            )

            overrides.write_text(
                "commit_sha,finding\n"
                "abcdef0,IR-TEST CLOSED: abbreviated identity\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "full commit SHA"):
                review_inventory.finding_overrides(root)

    def test_rendering_ignores_a_synthetic_pull_request_merge(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self.run_git(root, "init", "--quiet")
            self.run_git(root, "config", "user.name", "Inventory Test")
            self.run_git(root, "config", "user.email", "inventory@example.invalid")
            (root / "tracked.txt").write_text("baseline\n", encoding="utf-8")
            overrides = root / review_inventory.FINDING_OVERRIDES
            overrides.parent.mkdir(parents=True)
            overrides.write_text("commit_sha,finding\n", encoding="utf-8")
            self.run_git(root, "add", ".")
            self.run_git(root, "commit", "--quiet", "-m", "test baseline")
            baseline = self.run_git(root, "rev-parse", "HEAD").strip()

            self.run_git(root, "checkout", "--quiet", "-b", "feature")
            (root / "tracked.txt").write_text("feature\n", encoding="utf-8")
            self.run_git(root, "add", "tracked.txt")
            self.run_git(root, "commit", "--quiet", "-m", "feature change")
            _, feature_text = review_inventory.inventory_content(
                root,
                review_inventory.DEFAULT_OUTPUT,
                baseline,
            )

            self.run_git(root, "checkout", "--quiet", "-b", "base", baseline)
            self.run_git(root, "commit", "--quiet", "--allow-empty", "-m", "base change")
            self.run_git(root, "merge", "--quiet", "--no-ff", "feature", "-m", "synthetic PR merge")
            _, merge_text = review_inventory.inventory_content(
                root,
                review_inventory.DEFAULT_OUTPUT,
                baseline,
            )

            self.assertEqual(feature_text, merge_text)

    @staticmethod
    def run_git(root: Path, *arguments: str) -> str:
        return subprocess.run(
            ["git", *arguments],
            cwd=root,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout


if __name__ == "__main__":
    unittest.main()
