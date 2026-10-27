"""Current invocation evidence must not republish preserved historical reports."""
from __future__ import annotations

import glob
import re
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import workflow_contract  # noqa: E402


UPLOADS = {
    "desktop-android": "Upload verification reports, mappings, and unsigned AAB",
    "desktop-linux-arm64": "Upload Desktop verification descriptors",
    "desktop-macos-x64": "Upload Desktop verification descriptors",
    "desktop-windows-x64": "Upload Desktop verification descriptors",
    "ios": "Upload Apple runtime verification evidence",
    "ios-release": "Upload Apple release verification evidence",
}
EXCLUSIONS = ["!remediation-runs/**", "!docs/archives/**"]


def workflow() -> str:
    return (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text()


def upload(text: str, job: str) -> str:
    return workflow_contract.validation_step(workflow_contract.validation_job(text, job), UPLOADS[job])


def paths(text: str, job: str) -> list[str]:
    return re.findall(r"(?m)^            (\S.*)$", upload(text, job))


class CurrentVerificationArtifactTest(unittest.TestCase):
    def test_six_current_uploads_end_with_exact_historical_exclusions(self) -> None:
        for job in UPLOADS:
            with self.subTest(job=job):
                actual = paths(workflow(), job)
                self.assertEqual(actual[-2:], EXCLUSIONS)
                self.assertEqual([p for p in actual if p.startswith("!")], EXCLUSIONS)

    def test_current_workflow_passes_integrated_security_contract(self) -> None:
        workflow_contract.verify_current_verification_artifacts(workflow())
        workflow_contract.verify_verification_scopes(workflow())

    def test_exclusions_cannot_be_missing_redirected_duplicated_or_reordered(self) -> None:
        text = workflow()
        for job in UPLOADS:
            block = upload(text, job)
            for token in EXCLUSIONS:
                for replacement in ("", token[1:], token + "x", "!**/build/**", "!docs/**", token + "\n            " + token):
                    changed = text.replace(block, block.replace("            " + token, "            " + replacement, 1), 1)
                    with self.subTest(job=job, token=token, replacement=replacement), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                        workflow_contract.verify_current_verification_artifacts(changed)
            ordered = "\n".join("            " + p for p in EXCLUSIONS)
            moved = block.replace(ordered, "", 1).replace("          path: |", "          path: |\n" + ordered, 1)
            with self.subTest(job=job, mutation="early exclusions"), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                workflow_contract.verify_current_verification_artifacts(text.replace(block, moved, 1))

    def test_every_current_report_and_external_custody_path_is_required(self) -> None:
        text = workflow()
        for job in UPLOADS:
            block = upload(text, job)
            for token in paths(text, job)[:-2]:
                for replacement in ("", token + "x", "!" + token, "composeApp/build/test-results/**/*.xml", token + "\n            " + token):
                    changed = text.replace(block, block.replace("            " + token + "\n", "            " + replacement + "\n", 1), 1)
                    self.assertNotEqual(changed, text)
                    with self.subTest(job=job, token=token, replacement=replacement), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                        workflow_contract.verify_current_verification_artifacts(changed)

    def test_extra_current_exclusion_or_unknown_collection_path_is_rejected(self) -> None:
        text = workflow()
        for job in UPLOADS:
            block = upload(text, job)
            for token in ("!game-modes/**", "!shared/**", "!build/**", "!**/*.xml", "**/private/**", "../**", "**/build/reports/tests/"):
                changed = block.replace("          path: |\n", "          path: |\n            " + token + "\n", 1)
                with self.subTest(job=job, token=token), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                    workflow_contract.verify_current_verification_artifacts(text.replace(block, changed, 1))

    def test_duplicate_path_or_unreviewed_upload_options_are_rejected(self) -> None:
        text = workflow()
        for job in UPLOADS:
            block = upload(text, job)
            for token in ('          path: "**/build/**"',
                          '          "path": "**/build/**"',
                          '          <<: *unreviewed_options',
                          '          include-hidden-files: true',
                          '          compression-level: 0',
                          '          retention-days: 1',
                          '          if-no-files-found: ignore'):
                changed = block.replace("          path: |\n", token + "\n          path: |\n", 1)
                with self.subTest(job=job, token=token), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                    workflow_contract.verify_current_verification_artifacts(text.replace(block, changed, 1))

    def test_upload_identity_conditions_and_retention_cannot_change(self) -> None:
        text = workflow()
        for job in UPLOADS:
            block = upload(text, job)
            for original, replacement in (
                ('        id: verification_artifact', '        id: unrelated_artifact'),
                ('        if: ', '        if: false && '),
                ('        uses: actions/upload-artifact@', '        uses: actions/checkout@'),
                ('          retention-days: 14', '          retention-days: 1'),
                ('          name: ', '          name: unbound-'),
                ('          if-no-files-found: warn', '          if-no-files-found: ignore'),
                ('        with:', '        env:\n          GH_TOKEN: ${{ github.token }}\n        with:'),
            ):
                changed = block.replace(original, replacement, 1)
                self.assertNotEqual(changed, block)
                with self.subTest(job=job, token=original), self.assertRaisesRegex(RuntimeError, "current verification artifact"):
                    workflow_contract.verify_current_verification_artifacts(text.replace(block, changed, 1))

    def test_selection_keeps_current_and_future_modules_but_not_archived_reports(self) -> None:
        # Execute filesystem glob selection as a deterministic structural fixture.
        # Hosted @actions/glob execution is a separate platform receipt, not this model.
        reports = [
            "composeApp/build/test-results/desktopTest/TEST-App.xml",
            "shared/session/build/test-results/iosSimulatorArm64Test/TEST-Session.xml",
            "game-modes/future-game/build/test-results/desktopTest/TEST-Future.xml",
            "game-modes/future-game/build/reports/tests/desktopTest/index.html",
            "shared/session/build/reports/detekt/report.html",
            "composeApp/build/reports/lint-results-release.html",
            "composeApp/build/reports/androidTests/managedDevice/release/index.html",
            "composeApp/build/outputs/androidTest-results/managedDevice/release/TEST-Device.xml",
            "composeApp/build/outputs/mapping/release/mapping.txt",
            "composeApp/build/outputs/bundle/release/Parlor.aab",
            "build/ci-evidence/ios-ui-tests.xcresult/Info.plist",
            "build/ci-evidence/ios-readiness-control-tests.log",
            "build/xcode-derived-data/Build/Products/Release-iphonesimulator/Parlor.app.dSYM/Contents/Info.plist",
        ]
        with TemporaryDirectory() as temporary:
            root = Path(temporary) / "checkout"
            external = Path(temporary) / "runner temp"
            root.mkdir()
            external.mkdir()
            for name in reports:
                for prefix in ("", "remediation-runs/preserved/", "docs/archives/history/preserved/"):
                    target = root / (prefix + name)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_text("public synthetic report")
            for job in UPLOADS:
                binding = f"parlor-verification-123-1-{job}"
                receipts = [binding + suffix for suffix in ("-ownership.json", "-stop-desktop.json")]
                if job.startswith("ios"):
                    receipts += [binding + "-apple-aggregate-ownership.json"]
                if job == "ios":
                    receipts += [binding + "-apple-ui-simulator-1.json"]
                for name in receipts:
                    (external / name).write_text("public synthetic custody")
                positive = paths(workflow(), job)[:-2]
                selected = set()
                for pattern in positive:
                    expanded = pattern.replace("${{ runner.temp }}", str(external)).replace("${{ github.run_id }}", "123").replace("${{ github.run_attempt }}", "1").replace("${{ github.job }}", job)
                    for match in glob.glob(str(root / expanded), recursive=True):
                        p = Path(match)
                        selected.update(q for q in (p.rglob("*") if p.is_dir() else [p]) if q.is_file())
                historic = {p for p in selected if p.is_relative_to(root / "remediation-runs") or p.is_relative_to(root / "docs/archives")}
                self.assertTrue(historic, job)
                excluded = set()
                for pattern in paths(workflow(), job)[-2:]:
                    for match in glob.glob(str(root / pattern[1:]), recursive=True):
                        p = Path(match)
                        if p.is_file():
                            excluded.add(p)
                self.assertEqual(selected & excluded, historic)
                selected.difference_update(excluded)
                with self.subTest(job=job):
                    self.assertTrue(all(external / name in selected for name in receipts))
                    if job != "ios-release":
                        self.assertIn(root / reports[2], selected)
                        self.assertIn(root / reports[3], selected)
                    if job == "desktop-android":
                        self.assertTrue(all(root / name in selected for name in reports[:10]))
                    if job.startswith("ios"):
                        self.assertIn(root / reports[10], selected)
                    if job == "ios-release":
                        self.assertIn(root / reports[4], selected)
                        self.assertIn(root / reports[12], selected)
                    self.assertFalse(any(p.is_relative_to(root / "remediation-runs") or p.is_relative_to(root / "docs/archives") for p in selected))

    def test_diagnostics_cannot_acquire_normal_collection_policy(self) -> None:
        for job in ("ios-protection-probe", "native-preflight", "ios-other", ""):
            with self.subTest(job=job), self.assertRaisesRegex(RuntimeError, "Unknown current verification artifact job"):
                workflow_contract.current_verification_artifact_paths(job)


if __name__ == "__main__":
    unittest.main()
