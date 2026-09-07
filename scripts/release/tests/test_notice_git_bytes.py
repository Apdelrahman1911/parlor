"""Prove Windows-style checkout conversion cannot alter the notice supplement."""
from pathlib import Path
import os
import subprocess
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[3]
LEGAL = "composeApp/src/commonMain/composeResources/files/legal/fixture.txt"


class NoticeGitBytesTest(unittest.TestCase):
    def test_notice_bytes_survive_crlf_checkout_conversion(self) -> None:
        with TemporaryDirectory(prefix="parlor-notice-git-bytes-") as directory:
            root = Path(directory)
            environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
            environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
            subprocess.run(["git", "init", "--quiet", str(root)], check=True, env=environment)
            subprocess.run(["git", "-C", str(root), "config", "core.autocrlf", "true"], check=True, env=environment)
            (root / ".gitattributes").write_bytes((ROOT / ".gitattributes").read_bytes())
            notice = root / LEGAL
            notice.parent.mkdir(parents=True)
            original = b"Exact upstream notice\r\nCopyright example\r\n"
            notice.write_bytes(original)
            subprocess.run(["git", "-C", str(root), "add", "--", ".gitattributes", LEGAL], check=True, env=environment)
            indexed = subprocess.check_output(["git", "-C", str(root), "show", ":" + LEGAL], env=environment)
            self.assertEqual(original, indexed)
            notice.unlink()  # Only this fixture's synthetic source is replaced.
            subprocess.run(["git", "-C", str(root), "checkout-index", "--", LEGAL], check=True, env=environment)
            self.assertEqual(original, notice.read_bytes())
            ordinary = "composeApp/src/commonMain/kotlin/Example.kt"
            attributes = subprocess.check_output(
                ["git", "-C", str(root), "check-attr", "text", "--", LEGAL, ordinary], text=True, env=environment
            ).splitlines()
            self.assertEqual([LEGAL + ": text: unset", ordinary + ": text: unspecified"], attributes)

    def test_only_verbatim_upstream_line_endings_and_final_blanks_are_exempt(self) -> None:
        with TemporaryDirectory(prefix="parlor-notice-git-whitespace-") as directory:
            root = Path(directory)
            environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
            environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)

            def git(*arguments):
                return subprocess.run(["git", "-C", str(root), *arguments], env=environment,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)

            self.assertEqual(0, git("init", "--quiet").returncode)
            self.assertEqual(0, git("config", "core.autocrlf", "true").returncode)
            (root / ".gitattributes").write_bytes((ROOT / ".gitattributes").read_bytes())
            originals = {}
            for name in ("SLF4J-2.0.16-LICENSE.txt", "WebP-COPYING.txt"):
                relative = str(Path(LEGAL).with_name(name))
                originals[relative] = (ROOT / relative).read_bytes()
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(originals[relative])
            self.assertEqual(0, git("add", "--", ".gitattributes", *originals).returncode)
            self.assertEqual(0, git("diff", "--cached", "--check").returncode)
            for relative, original in originals.items():
                with self.subTest(path=relative):
                    self.assertEqual(original, git("show", ":" + relative).stdout)
                    path = root / relative
                    path.unlink()  # Only a synthetic copy in this owned fixture.
                    self.assertEqual(0, git("checkout-index", "--", relative).returncode)
                    self.assertEqual(original, path.read_bytes())
                    ending = b"\r\n" if b"\r\n" in original else b"\n"
                    path.write_bytes(original.replace(ending, b" " + ending, 1))
                    self.assertEqual(0, git("add", "--", relative).returncode)
                    self.assertNotEqual(0, git("diff", "--cached", "--check").returncode)
                    path.write_bytes(original)
                    self.assertEqual(0, git("add", "--", relative).returncode)
            ordinary = "composeApp/src/commonMain/kotlin/Example.kt"
            path = root / ordinary
            path.parent.mkdir(parents=True)
            path.write_bytes(b"// synthetic Kotlin with trailing spaces  \n")
            self.assertEqual(0, git("add", "--", ordinary).returncode)
            self.assertNotEqual(0, git("diff", "--cached", "--check").returncode)
