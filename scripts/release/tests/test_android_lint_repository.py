import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.ci import android_lint_repository as subject

MASTER = b'<metadata><androidx.navigation3/><other/></metadata>'
GROUP = b'<androidx.navigation3><navigation3-runtime versions="1.0.0,1.0.1,1.2.0,1.3.0-alpha01"/></androidx.navigation3>'


class AndroidLintRepositoryTest(unittest.TestCase):
    def test_live_index_retains_current_version_and_requires_a_real_stable_update(self):
        self.assertEqual("1.2.0", subject.validate(MASTER, GROUP, "1.0.0"))
        with self.assertRaises(ValueError):
            subject.validate(MASTER, GROUP, "1.2.0")

    def test_bad_missing_duplicate_or_unsafe_indexes_fail_closed(self):
        fixtures = [
            (b'<metadata/>', GROUP),
            (b'<metadata><androidx.navigation3/><androidx.navigation3/></metadata>', GROUP),
            (MASTER, b'<wrong/>'),
            (MASTER, b'<androidx.navigation3/>'),
            (MASTER, GROUP.replace(b'1.0.0,', b'')),
            (MASTER, GROUP.replace(b'1.0.1', b'1.0.0')),
            (MASTER, GROUP.replace(b'1.0.1', b'BAD')),
            (MASTER, b'<!DOCTYPE x [<!ENTITY x "bad">]>' + GROUP),
            (b'x' * (subject.MAX_BYTES + 1), GROUP),
        ]
        for master, group in fixtures:
            with self.subTest(master=master[:50], group=group[:50]), self.assertRaises(ValueError):
                subject.validate(master, group, "1.0.0")

    def test_prepare_writes_only_live_inputs_and_retains_a_separate_receipt(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            calls = []
            def download(relative):
                calls.append(relative)
                return dict(zip(subject.FILES, (MASTER, GROUP)))[relative]
            with contextlib.redirect_stdout(io.StringIO()):
                result = subject.prepare(root, download)
            self.assertEqual(list(subject.FILES), calls)
            self.assertEqual("PASS_LIVE_REPOSITORY_INPUTS_NOT_LINT_QUALIFICATION", result["result"])
            self.assertEqual(GROUP, (root / "composeApp/build/lint-repository-inputs" / subject.FILES[1]).read_bytes())
            reports = list((root / "composeApp/build/reports").glob("lint-repository-inputs-*.json"))
            self.assertEqual(1, len(reports))
            self.assertEqual(result, json.loads(reports[0].read_text()))
            self.assertFalse((root / "composeApp/build/reports/lint-results-release.xml").exists())

    def test_failed_fetch_or_validation_cannot_create_inputs_or_a_pass_receipt(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            with self.assertRaises(OSError):
                subject.prepare(root, lambda _: self.failed())
            self.assertFalse((root / "composeApp/build").exists())
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda _: MASTER)
            self.assertFalse((root / "composeApp/build").exists())

    def test_symlinked_target_or_unsupported_pin_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            (root / "composeApp").mkdir()
            (root / "composeApp/build").symlink_to(root / "gradle", target_is_directory=True)
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda n: MASTER if n == subject.FILES[0] else GROUP)
            (root / "gradle/libs.versions.toml").write_text('[versions]\nnavigation3-runtime="1.0.0"\nagp="9.0.0"\n')
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda _: self.failed())

    def test_alternate_encoding_versions_section_and_receipt_symlinks_fail_closed(self):
        with self.assertRaises(ValueError):
            subject.document(('<?xml version="1.0" encoding="utf-16"?>'
                              '<!DOCTYPE x [<!ENTITY x "bad">]><metadata/>').encode("utf-16"))
        with tempfile.TemporaryDirectory() as name:
            root = self.fixture(Path(name))
            catalog = root / "gradle/libs.versions.toml"
            original = catalog.read_text()
            for malformed in (original.replace("[versions]", "[libraries]"), original + original):
                catalog.write_text(malformed)
                with self.assertRaises(ValueError):
                    subject.prepare(root, lambda _: self.failed())
            catalog.write_text(original)
            build = root / "composeApp/build"
            build.mkdir(parents=True)
            (build / "reports").symlink_to(root / "gradle", target_is_directory=True)
            with self.assertRaises(ValueError):
                subject.prepare(root, lambda n: MASTER if n == subject.FILES[0] else GROUP)
            self.assertFalse(list((root / "gradle").glob("lint-repository-inputs-*.json")))

    def test_fetch_is_single_bounded_official_request_without_redirect_or_retry(self):
        class Response:
            status = 200
            def __init__(self, url, raw=MASTER):
                self.url, self.raw = url, raw
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def geturl(self):
                return self.url
            def read(self, count):
                self.count = count
                return self.raw
        url = subject.BASE + subject.FILES[0]
        response = Response(url)
        with patch.object(subject.urllib.request, "urlopen", return_value=response) as call:
            self.assertEqual(MASTER, subject.fetch(subject.FILES[0]))
            call.assert_called_once_with(url, timeout=20)
            self.assertEqual(subject.MAX_BYTES + 1, response.count)
        for invalid in (Response("https://example.com/index"), Response(url, b''), Response(url, b'x' * (subject.MAX_BYTES + 1))):
            with patch.object(subject.urllib.request, "urlopen", return_value=invalid), self.assertRaises(ValueError):
                subject.fetch(subject.FILES[0])

    def fixture(self, root):
        (root / "gradle").mkdir()
        (root / "gradle/libs.versions.toml").write_text('[versions]\nnavigation3-runtime="1.0.0"\nagp="8.13.2"\n')
        (root / "gradle.properties").write_text('android.experimental.lint.version=9.1.1\n')
        return root

    def failed(self):
        raise OSError("Observed network failure")


if __name__ == "__main__":
    unittest.main()
