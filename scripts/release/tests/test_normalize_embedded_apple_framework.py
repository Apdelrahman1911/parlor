from __future__ import annotations

import subprocess
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[3]
NORMALIZER = ROOT / "scripts/release/normalize_embedded_apple_framework.sh"


class EmbeddedAppleFrameworkNormalizationTest(unittest.TestCase):
    def test_case_only_framework_rename_matches_macho_identity(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            frameworks = Path(temporary_directory) / "Frameworks"
            embedded = frameworks / "composeApp.framework"
            embedded.mkdir(parents=True)
            (embedded / "ComposeApp").touch()

            completed = self.run_normalizer(frameworks)

            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertEqual(
                ["ComposeApp.framework"],
                [path.name for path in frameworks.iterdir()],
            )
            self.assertTrue((frameworks / "ComposeApp.framework/ComposeApp").is_file())

    def test_canonical_framework_is_an_idempotent_success(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            frameworks = Path(temporary_directory) / "Frameworks"
            embedded = frameworks / "ComposeApp.framework"
            embedded.mkdir(parents=True)
            (embedded / "ComposeApp").touch()

            first = self.run_normalizer(frameworks)
            second = self.run_normalizer(frameworks)

            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(second.returncode, 0, second.stderr)

    def test_framework_without_canonical_executable_fails_closed(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            frameworks = Path(temporary_directory) / "Frameworks"
            (frameworks / "composeApp.framework").mkdir(parents=True)

            completed = self.run_normalizer(frameworks)

            self.assertEqual(completed.returncode, 2)
            self.assertIn("executable does not match", completed.stderr)

    @staticmethod
    def run_normalizer(frameworks: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(NORMALIZER), str(frameworks), "ComposeApp"],
            check=False,
            capture_output=True,
            text=True,
        )


if __name__ == "__main__":
    unittest.main()
