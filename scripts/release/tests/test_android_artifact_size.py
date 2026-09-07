from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
VALIDATOR = ROOT / "scripts/release/validate_android_artifact.sh"


class AndroidArtifactSizeTest(unittest.TestCase):
    """Execute the real preflight, stopping before signing at the digest guard.

    A GNU-shaped stat deliberately emits output before failure on BSD flags.
    This reproduces the old mixed-stdout fallback even on a macOS test host.
    No signed artifact, Store identity approval, or network call is required.
    """

    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="parlor-size-fixture-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.aab = self.directory / "synthetic bundle.aab"
        self.report = self.directory / "dependency report.txt"
        self.bundletool = self.directory / "not bundletool.jar"
        self.aab.write_bytes(b"synthetic")
        self.report.write_text("synthetic release dependencies\n")
        self.bundletool.write_bytes(b"deliberately fails the pinned tool digest")
        bin_dir = self.directory / "bin"
        bin_dir.mkdir()
        stat = bin_dir / "stat"
        stat.write_text(
            "#!/usr/bin/env python3\n"
            "import os,sys\n"
            "if sys.argv[1:3] == ['-f', '%z']:\n"
            "    print('  File: synthetic filesystem statistics')\n"
            "    sys.exit(1)\n"
            "if sys.argv[1:3] == ['-c', '%s']:\n"
            "    print(os.stat(sys.argv[3]).st_size)\n"
            "else:\n"
            "    sys.exit(90)\n"
        )
        stat.chmod(0o755)
        self.environment = {**os.environ, "PATH": str(bin_dir) + os.pathsep + os.environ["PATH"]}

    def validate(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                "bash", str(VALIDATOR), str(self.aab), str(self.bundletool),
                "com.parlor.app", "1.0.0", "1", "a" * 64,
                str(self.report), str(self.directory / "must-not-exist.json"),
            ],
            env=self.environment, text=True, capture_output=True, timeout=20,
        )

    def assert_preflight_accepted(self) -> None:
        result = self.validate()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("bundletool digest mismatch", result.stderr)
        self.assertNotIn("exceeds", result.stderr)
        self.assertFalse((self.directory / "must-not-exist.json").exists())

    def test_both_small_files_with_spaces_pass_gnu_shaped_stat_preflight(self) -> None:
        self.assert_preflight_accepted()

    def test_exact_artifact_bound_is_accepted_without_allocating_512_mib(self) -> None:
        with self.aab.open("r+b") as stream:
            stream.truncate(536870912)
        self.assert_preflight_accepted()

    def test_oversized_artifact_is_rejected(self) -> None:
        with self.aab.open("r+b") as stream:
            stream.truncate(536870913)
        result = self.validate()
        self.assertEqual(result.returncode, 2)
        self.assertIn("AAB exceeds the reviewed 512 MiB bound", result.stderr)
        self.assertNotIn("bundletool digest", result.stderr)

    def test_exact_dependency_report_bound_is_accepted(self) -> None:
        with self.report.open("r+b") as stream:
            stream.truncate(10485760)
        self.assert_preflight_accepted()

    def test_oversized_dependency_report_is_rejected(self) -> None:
        with self.report.open("r+b") as stream:
            stream.truncate(10485761)
        result = self.validate()
        self.assertEqual(result.returncode, 2)
        self.assertIn("Android dependency report exceeds the reviewed 10 MiB bound", result.stderr)
        self.assertNotIn("bundletool digest", result.stderr)

    def test_missing_directory_and_symlink_inputs_are_rejected(self) -> None:
        for field in ("aab", "report"):
            for kind in ("missing", "directory", "symlink"):
                with self.subTest(field=field, kind=kind):
                    original = getattr(self, field)
                    path = self.directory / f"{field}-{kind}"
                    if kind == "directory":
                        path.mkdir()
                    elif kind == "symlink":
                        path.symlink_to(original)
                    setattr(self, field, path)
                    try:
                        result = self.validate()
                        self.assertNotEqual(result.returncode, 0)
                        self.assertNotIn("bundletool digest", result.stderr)
                    finally:
                        setattr(self, field, original)

    def test_empty_dependency_report_is_rejected(self) -> None:
        self.report.write_bytes(b"")
        result = self.validate()
        self.assertEqual(result.returncode, 2)
        self.assertIn("Android release dependency report is missing", result.stderr)
