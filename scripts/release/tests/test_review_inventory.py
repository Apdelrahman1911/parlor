from __future__ import annotations

import csv
import io
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import generate_review_inventory as review_inventory  # noqa: E402


class ReviewInventoryTest(unittest.TestCase):
    def test_help_never_resolves_the_repository_or_writes_an_inventory(self) -> None:
        for flag in ("-h", "--help"):
            with self.subTest(flag=flag), patch.object(sys, "argv", ["inventory", flag]), \
                    patch.object(review_inventory, "repository_root") as root, \
                    patch("sys.stdout", new_callable=io.StringIO) as output:
                with self.assertRaises(SystemExit) as result:
                    review_inventory.main()
                self.assertEqual(0, result.exception.code)
                self.assertIn("usage:", output.getvalue())
                root.assert_not_called()

    def test_invalid_options_and_extra_outputs_fail_before_repository_access(self) -> None:
        for arguments in (["--chekc"], ["--chec"], ["--check", "--unknown"], ["one.csv", "two.csv"]):
            with self.subTest(arguments=arguments), patch.object(sys, "argv", ["inventory", *arguments]), \
                    patch.object(review_inventory, "repository_root") as root, \
                    patch("sys.stderr", new_callable=io.StringIO):
                with self.assertRaises(SystemExit) as result:
                    review_inventory.main()
                self.assertEqual(2, result.exception.code)
                root.assert_not_called()

    def test_output_and_check_arguments_preserve_existing_forms(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            for output_arg in (review_inventory.DEFAULT_OUTPUT, "custom.csv", "nested/custom.csv"):
                with self.subTest(output=output_arg), \
                        patch.object(review_inventory, "repository_root", return_value=root), \
                        patch.object(review_inventory, "inventory_content", return_value=([], "mechanical\n")) as content, \
                        patch("sys.stdout", new_callable=io.StringIO):
                    arguments = [] if output_arg == review_inventory.DEFAULT_OUTPUT else [output_arg]
                    with patch.object(sys, "argv", ["inventory", *arguments]):
                        self.assertEqual(0, review_inventory.main())
                    output = root / output_arg
                    self.assertEqual("mechanical\n", output.read_text(encoding="utf-8"))
                    for check_args in (["--check", *arguments], [*arguments, "--check"]):
                        with patch.object(sys, "argv", ["inventory", *check_args]):
                            self.assertEqual(0, review_inventory.main())
                    content.assert_called_with(root, output_arg)

    def test_stale_check_does_not_rewrite_the_output(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "custom.csv"
            output.write_text("stale\n", encoding="utf-8")
            with patch.object(sys, "argv", ["inventory", "--check", "custom.csv"]), \
                    patch.object(review_inventory, "repository_root", return_value=root), \
                    patch.object(review_inventory, "inventory_content", return_value=([], "new\n")), \
                    patch("sys.stderr", new_callable=io.StringIO):
                self.assertEqual(1, review_inventory.main())
            self.assertEqual("stale\n", output.read_text(encoding="utf-8"))
            self.assertFalse(output.with_suffix(".csv.tmp").exists())

    def test_explicit_separator_allows_a_literal_option_like_output(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(sys, "argv", ["inventory", "--", "--literal.csv"]), \
                    patch.object(review_inventory, "repository_root", return_value=root), \
                    patch.object(review_inventory, "inventory_content", return_value=([], "mechanical\n")), \
                    patch("sys.stdout", new_callable=io.StringIO):
                self.assertEqual(0, review_inventory.main())
            self.assertEqual("mechanical\n", (root / "--literal.csv").read_text(encoding="utf-8"))

    def test_mechanical_rows_never_attest_independent_review(self) -> None:
        commit = "a" * 40
        paths = [
            "new-source.kt",
            "changed-source.kt",
            "historical-fix.kt",
            "docs/archives/history/ARCHITECTURE.md",
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

    def test_input_investigation_is_not_inventoried_as_production_qualification(self) -> None:
        diagnostic = ".github/workflows/ios-input-investigation.yml"
        production = ".github/workflows/production-verification.yml"
        rows = {
            row["path"]: row
            for row in csv.DictReader(io.StringIO(review_inventory.render_inventory([diagnostic, production], {}, {})))
        }
        self.assertEqual("ci-diagnostic-workflow", rows[diagnostic]["classification"])
        self.assertEqual("CI-ONLY: explicit non-production investigation; not application qualification",
                         rows[diagnostic]["inferred_production_reachability"])
        self.assertEqual("read-only original-source input investigation and retained failure analysis",
                         rows[diagnostic]["inferred_main_callers_or_consumers"])
        self.assertEqual("RETAIN as non-publishing diagnostic tooling; never substitutes for production checks",
                         rows[diagnostic]["suggested_disposition"])
        self.assertEqual("ci-workflow", rows[production]["classification"])
        self.assertEqual("CI-ONLY: production qualification workflow",
                         rows[production]["inferred_production_reachability"])
        for row in rows.values():
            self.assertEqual("MECHANICALLY INVENTORIED; INDEPENDENT REVIEW NOT ATTESTED", row["inventory_status"])

    def test_archive_paths_never_become_shipping_sources_or_resources(self) -> None:
        source = "composeApp/src/commonMain/kotlin/Fixture.kt"
        resource = "composeApp/src/androidMain/res/values/strings.xml"
        paths = [
            "docs/archives/history/ARCHITECTURE.md",
            "docs/archives/audit-runs/before/" + source,
            "docs/archives/audit-runs/before/" + resource,
            "docs/archives/audit-runs/before/iosApp/iosApp/Info.plist",
            "docs/archives/design/web-ui-rework/app.js",
            "docs/PRODUCTION_ARCHITECTURE.md",
            "docs/archives-notes/current.md",
            source,
            resource,
        ]
        rows = {
            row["path"]: row
            for row in csv.DictReader(io.StringIO(review_inventory.render_inventory(paths, {}, {})))
        }
        for path in paths[:5]:
            with self.subTest(path=path):
                row = rows[path]
                self.assertEqual("repository", row["module"])
                self.assertEqual("documentation", row["source_set"])
                self.assertEqual(
                    "historical-document" if path.endswith(".md") else "historical-artifact",
                    row["classification"],
                )
                self.assertTrue(row["inferred_production_reachability"].startswith("NON-RUNTIME:"))
                self.assertTrue(row["suggested_disposition"].startswith("RETAIN AS HISTORICAL;"))
        for path in ("docs/PRODUCTION_ARCHITECTURE.md", "docs/archives-notes/current.md"):
            self.assertEqual("operational-document", rows[path]["classification"])
        self.assertEqual("production-source", rows[source]["classification"])
        self.assertEqual("production-resource", rows[resource]["classification"])

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
