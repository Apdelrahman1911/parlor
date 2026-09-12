"""Pure synthetic fixtures only: no network, Java, Gradle, native process or real user profile."""
import hashlib
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import urllib.request

import run_rosetta_desktop as runner
import safe_jdk


class PublicJdkControlTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-rosetta-control-test-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def archive(self, specifications):
        path = self.root / 'fixture.tar.gz'
        with tarfile.open(path, 'w:gz') as output:
            for name, kind, value in specifications:
                entry = tarfile.TarInfo(name)
                entry.mode = 0o755
                entry.type = kind
                if kind == tarfile.REGTYPE:
                    entry.size = len(value)
                    output.addfile(entry, io.BytesIO(value))
                else:
                    entry.linkname = value
                    output.addfile(entry)
        return path

    def test_regular_executable_and_contained_links_extract(self):
        archive = self.archive([
            ('bundle', tarfile.DIRTYPE, ''), ('bundle/bin', tarfile.DIRTYPE, ''),
            ('bundle/bin/java', tarfile.REGTYPE, b'synthetic executable, never run'),
            ('bundle/java', tarfile.SYMTYPE, 'bin/java'),
            ('bundle/copy', tarfile.LNKTYPE, 'bundle/bin/java'),
        ])
        result = safe_jdk.extract(archive, self.root / 'out')
        self.assertEqual(5, result['entries'])
        self.assertEqual(b'synthetic executable, never run', (self.root / 'out/bundle/java').read_bytes())
        self.assertTrue((self.root / 'out/bundle/java').is_symlink())
        self.assertEqual((self.root / 'out/bundle/bin/java').stat().st_ino,
                         (self.root / 'out/bundle/copy').stat().st_ino)
        self.assertTrue((self.root / 'out/bundle/bin/java').stat().st_mode & 0o100)

    def test_unsafe_names_rejected(self):
        for name in ('', '/absolute', '../outside', 'a/../../b', 'a\\b', 'a\x00b', '.'):
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                safe_jdk.safe_name(name)

    def test_duplicate_normalized_paths_rejected(self):
        archive = self.archive([('file', tarfile.REGTYPE, b'a'), ('./file', tarfile.REGTYPE, b'b')])
        with self.assertRaisesRegex(RuntimeError, 'Duplicate'):
            safe_jdk.extract(archive, self.root / 'out')

    def test_fifo_special_entry_rejected(self):
        archive = self.archive([('fifo', tarfile.FIFOTYPE, '')])
        with self.assertRaisesRegex(RuntimeError, 'Special'):
            safe_jdk.extract(archive, self.root / 'out')

    def test_file_under_symlink_rejected_before_write(self):
        archive = self.archive([
            ('real', tarfile.DIRTYPE, ''), ('alias', tarfile.SYMTYPE, 'real'),
            ('alias/payload', tarfile.REGTYPE, b'not allowed'),
        ])
        with self.assertRaisesRegex(RuntimeError, 'ancestor'):
            safe_jdk.extract(archive, self.root / 'out')
        self.assertFalse((self.root / 'out/real/payload').exists())

    def test_escaping_and_absolute_links_rejected(self):
        for target in ('../../outside', '/tmp/outside'):
            with self.subTest(target=target), self.assertRaises(RuntimeError):
                safe_jdk.link_destination(Path('bundle/alias'), target, True)

    def test_noninventoried_target_rejected(self):
        archive = self.archive([('alias', tarfile.SYMTYPE, 'missing')])
        with self.assertRaisesRegex(RuntimeError, 'inventoried'):
            safe_jdk.extract(archive, self.root / 'out')

    def test_hardlink_to_symbolic_link_rejected(self):
        archive = self.archive([
            ('real', tarfile.REGTYPE, b'a'), ('alias', tarfile.SYMTYPE, 'real'),
            ('hard', tarfile.LNKTYPE, 'alias'),
        ])
        with self.assertRaisesRegex(RuntimeError, 'Hard link'):
            safe_jdk.extract(archive, self.root / 'out')

    def test_symbolic_link_cycle_rejected(self):
        archive = self.archive([('a', tarfile.SYMTYPE, 'b'), ('b', tarfile.SYMTYPE, 'a')])
        with self.assertRaises(RuntimeError):
            safe_jdk.extract(archive, self.root / 'out')

    def test_expansion_bound_rejected_before_payload_write(self):
        archive = self.archive([('payload', tarfile.REGTYPE, b'123')])
        with patch.object(safe_jdk, 'MAX_EXPANDED', 2), self.assertRaisesRegex(RuntimeError, 'expansion'):
            safe_jdk.extract(archive, self.root / 'out')
        self.assertFalse((self.root / 'out/payload').exists())

    def test_entry_bound_rejected(self):
        archive = self.archive([('one', tarfile.REGTYPE, b'1'), ('two', tarfile.REGTYPE, b'2')])
        with patch.object(safe_jdk, 'MAX_ENTRIES', 1), self.assertRaisesRegex(RuntimeError, 'excessive'):
            safe_jdk.extract(archive, self.root / 'out')

    def test_existing_destination_never_overwritten(self):
        archive = self.archive([('payload', tarfile.REGTYPE, b'new')])
        destination = self.root / 'out'
        destination.mkdir()
        (destination / 'payload').write_bytes(b'preserve')
        with self.assertRaises(FileExistsError):
            safe_jdk.extract(archive, destination)
        self.assertEqual(b'preserve', (destination / 'payload').read_bytes())

    def test_scratch_symlink_is_not_ownership(self):
        alias = self.root / 'alias'
        alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            safe_jdk.owned_identity(alias)
        self.assertEqual(self.root.stat().st_ino, safe_jdk.owned_identity(self.root)[1])

    def fake_download(self, data, expected=b'fixture', length=None):
        response = io.BytesIO(data)
        response.status = 200
        response.headers = {} if length is None else {'Content-Length': str(length)}
        with patch.object(safe_jdk, 'SIZE', len(expected)), \
                patch.object(safe_jdk, 'SHA256', hashlib.sha256(expected).hexdigest()), \
                patch.object(safe_jdk.urllib.request, 'build_opener') as factory:
            factory.return_value.open.return_value = response
            return safe_jdk.download(self.root / 'download.gz')

    def test_exact_public_digest_and_size_required(self):
        result = self.fake_download(b'fixture')
        self.assertEqual(7, result['bytes'])
        self.assertEqual(hashlib.sha256(b'fixture').hexdigest(), result['sha256'])

    def test_truncated_download_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'size/SHA256'):
            self.fake_download(b'fixtur')

    def test_excess_download_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'exceeded pinned length'):
            self.fake_download(b'fixtureX')

    def test_wrong_digest_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'size/SHA256'):
            self.fake_download(b'fixturX')

    def test_wrong_declared_length_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'Content-Length'):
            self.fake_download(b'fixture', length=8)

    def test_only_public_https_release_redirects_allowed(self):
        policy = safe_jdk.PublicAssetRedirect()
        request = urllib.request.Request(safe_jdk.URL)
        allowed = policy.redirect_request(request, None, 302, 'Found', {},
                                          'https://release-assets.githubusercontent.com/public/path?token=synthetic')
        self.assertEqual('release-assets.githubusercontent.com', allowed.host)
        for target in ('http://github.com/file', 'https://example.org/file',
                       'https://user:password@github.com/file', 'https://github.com:444/file'):
            with self.subTest(target=target), self.assertRaises(RuntimeError):
                policy.redirect_request(request, None, 302, 'Found', {}, target)

    def test_command_pins_runtime_compiler_test_toolchain_and_strict_graph(self):
        command = runner.gradle_command(Path('/synthetic-owned/jdk'), 'synthetic-cycle')
        for item in ('productionDesktopCheck', '--dependency-verification=strict', '--no-daemon',
                     '--no-build-cache', '--rerun-tasks', '--max-workers=1',
                     '-Dorg.gradle.java.home=/synthetic-owned/jdk',
                     '-Porg.gradle.java.installations.paths=/synthetic-owned/jdk',
                     '-Porg.gradle.java.installations.fromEnv=',
                     '-Porg.gradle.java.installations.auto-detect=false',
                     '-Porg.gradle.java.installations.auto-download=false',
                     '-Pkotlin.compiler.execution.strategy=in-process'):
            self.assertIn(item, command)
        self.assertEqual(1, command.count('productionDesktopCheck'))
        self.assertFalse(any('--tests' in value or 'write-verification' in value for value in command))


if __name__ == '__main__':
    unittest.main()
