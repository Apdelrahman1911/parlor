from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[3]


class XcodeFrameworkPhaseTest(unittest.TestCase):
    """Execute the actual /bin/sh phase, not a reimplementation of its contract."""

    def setUp(self) -> None:
        self.temporary = TemporaryDirectory(prefix="parlor-xcode-phase-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source_root = self.root / "iosApp"
        self.source_root.mkdir()
        self.frameworks = self.root / "output/Frameworks"
        self.frameworks.mkdir(parents=True)
        scripts = self.root / "scripts/release"
        scripts.mkdir(parents=True)
        shutil.copyfile(
            ROOT / "scripts/release/normalize_embedded_apple_framework.sh",
            scripts / "real-normalizer.sh",
        )
        (scripts / "real-normalizer.sh").chmod(0o700)
        self.normalization_receipt = self.root / "normalization-called"
        normalizer = scripts / "normalize_embedded_apple_framework.sh"
        normalizer.write_text(
            '#!/bin/sh\nprintf called > "$NORMALIZATION_RECEIPT"\n'
            'exec "$(dirname "$0")/real-normalizer.sh" "$@"\n'
        )
        normalizer.chmod(0o700)
        project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text()
        phases = re.findall(r'\bshellScript = ("(?:[^"\\]|\\.)*");', project)
        matches = [json.loads(phase) for phase in phases if "embedAndSignAppleFrameworkForXcode" in phase]
        self.assertEqual(len(matches), 1, "Exactly one real Gradle embed phase must be tested")
        self.phase = matches[0]

    def stale_framework(self, name: str = "ComposeApp.framework") -> None:
        embedded = self.frameworks / name
        embedded.mkdir()
        (embedded / "ComposeApp").write_text("synthetic stale output, not an executable")

    def run_phase(self, gradle_status: int, source_root: Path | None = None, override: str = "NO"):
        gradle = self.root / "gradlew"
        gradle.write_text(f"#!/bin/sh\nexit {gradle_status}\n")
        gradle.chmod(0o700)
        env = os.environ | {
            "SRCROOT": str(source_root or self.source_root),
            "TARGET_BUILD_DIR": str(self.frameworks.parent),
            "FRAMEWORKS_FOLDER_PATH": self.frameworks.name,
            "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED": override,
            "NORMALIZATION_RECEIPT": str(self.normalization_receipt),
        }
        return subprocess.run(
            ["/bin/sh", "-c", self.phase], cwd=self.root, env=env,
            text=True, capture_output=True, check=False, timeout=30,
        )

    def test_failed_gradle_with_stale_framework_fails_without_normalizing(self) -> None:
        self.stale_framework()
        result = self.run_phase(42)
        self.assertEqual(result.returncode, 42, result.stdout + result.stderr)
        self.assertFalse(self.normalization_receipt.exists())

    def test_failed_directory_change_cannot_build_from_previous_directory(self) -> None:
        self.stale_framework()
        result = self.run_phase(0, source_root=self.root / "missing/iosApp")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.normalization_receipt.exists())

    def test_successful_embed_normalizes_case_only_framework_name(self) -> None:
        self.stale_framework("composeApp.framework")
        result = self.run_phase(0)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.normalization_receipt.exists())
        self.assertEqual(["ComposeApp.framework"], [p.name for p in self.frameworks.iterdir()])

    def test_successful_gradle_still_rejects_missing_framework(self) -> None:
        result = self.run_phase(0)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(self.normalization_receipt.exists())

    def test_intentional_ide_override_still_skips_gradle_and_normalization(self) -> None:
        result = self.run_phase(42, override="YES")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.normalization_receipt.exists())


if __name__ == "__main__":
    unittest.main()
