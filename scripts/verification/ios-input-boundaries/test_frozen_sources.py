"""Portable original-source bindings; no Git history or native execution."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import frozen_sources as frozen
import pin04_original_host as original
import review_frozen_result as review


class FrozenSourceFixtureControls(unittest.TestCase):
    def load_original(self, directory=frozen.DIRECTORY):
        path, digest = next(iter(original.FROZEN_INPUTS.items()))
        return frozen.load(original.FROZEN_SOURCE, path, digest, directory=directory)

    def test_all_eight_original_full_files_match_existing_renderer_and_decoder_pins(self):
        inputs = {original.FROZEN_SOURCE: original.FROZEN_INPUTS,
                  review.SOURCE: {review.DRIVER: review.DRIVER_SHA256}}
        manifest = json.loads((frozen.DIRECTORY / 'manifest.json').read_bytes())
        self.assertEqual(set(manifest['sources']), set(inputs))
        self.assertEqual(sum(len(v['inputs']) for v in manifest['sources'].values()), 8)
        for source, bindings in inputs.items():
            self.assertEqual(manifest['sources'][source]['inputs'], bindings)
            for path, digest in bindings.items():
                with self.subTest(source=source, path=path):
                    raw = frozen.load(source, path, digest)
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), digest)
                    raw.decode('utf-8')

    def test_unknown_source_path_and_digest_cannot_select_another_fixture(self):
        path, digest = next(iter(original.FROZEN_INPUTS.items()))
        for source, name, expected in ((None, path, digest), (True, path, digest),
                                     ('0' * 40, path, digest), (original.FROZEN_SOURCE, '../' + path, digest),
                                     (original.FROZEN_SOURCE, '/' + path, digest),
                                     (original.FROZEN_SOURCE, path, '0' * 64),
                                     (original.FROZEN_SOURCE, path, None)):
            with self.subTest(source=source, name=name), self.assertRaises(ValueError):
                frozen.load(source, name, expected)

    def test_changed_manifest_is_rejected_before_source_selection(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw).resolve(strict=True) / 'fixtures'
            shutil.copytree(frozen.DIRECTORY, directory)
            (directory / 'manifest.json').write_bytes((frozen.DIRECTORY / 'manifest.json').read_bytes() + b'\n')
            with self.assertRaisesRegex(ValueError, 'manifest changed'):
                self.load_original(directory)

    def test_changed_missing_and_oversized_full_file_never_falls_back_to_git_or_current_source(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw).resolve(strict=True) / 'fixtures'
            shutil.copytree(frozen.DIRECTORY, directory)
            path = directory / original.FROZEN_SOURCE / (next(iter(original.FROZEN_INPUTS)) + '.source')
            path.write_bytes(path.read_bytes() + b'\n')
            with self.assertRaisesRegex(ValueError, 'source bytes changed'):
                self.load_original(directory)
            path.unlink()
            with self.assertRaises(FileNotFoundError):
                self.load_original(directory)
            path.write_bytes(b'x' * (frozen.MAX_SOURCE_BYTES + 1))
            with self.assertRaisesRegex(ValueError, 'Invalid'):
                self.load_original(directory)

    def test_file_and_parent_symlinks_and_hardlinks_are_not_frozen_inputs(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve(strict=True)
            directory = root / 'fixtures'
            shutil.copytree(frozen.DIRECTORY, directory)
            path = directory / original.FROZEN_SOURCE / (next(iter(original.FROZEN_INPUTS)) + '.source')
            target = root / 'target'
            path.rename(target)
            path.symlink_to(target)
            with self.assertRaisesRegex(ValueError, 'Redirected'):
                self.load_original(directory)
            path.unlink()
            os.link(target, path)
            with self.assertRaisesRegex(ValueError, 'Invalid'):
                self.load_original(directory)
            redirected = root / 'redirected'
            redirected.symlink_to(directory, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'Redirected'):
                self.load_original(redirected)

    def test_owned_temporary_alias_reaches_original_corruption_and_link_guards(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve(strict=True)
            real = root / 'real'
            real.mkdir()
            alias = root / 'alias'
            alias.symlink_to(real, target_is_directory=True)
            self.assertNotEqual(alias.absolute(), alias.resolve(strict=True))
            with patch.object(tempfile, 'tempdir', str(alias)):
                for name in (
                    'test_changed_manifest_is_rejected_before_source_selection',
                    'test_changed_missing_and_oversized_full_file_never_falls_back_to_git_or_current_source',
                    'test_file_and_parent_symlinks_and_hardlinks_are_not_frozen_inputs',
                ):
                    with self.subTest(control=name):
                        getattr(self, name)()
            self.assertEqual(list(real.iterdir()), [])
            self.assertTrue(alias.is_symlink())

    def test_changing_descriptor_metadata_rejects_even_hash_matching_bytes(self):
        actual = os.fstat
        calls = []
        def changed(fd):
            value = actual(fd)
            calls.append(value)
            if len(calls) == 2:
                return type('Changed', (), {name: getattr(value, name) + (1 if name == 'st_ctime_ns' else 0)
                    for name in ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')})()
            return value
        with patch.object(frozen.os, 'fstat', side_effect=changed), self.assertRaisesRegex(ValueError, 'Changing'):
            self.load_original()

    def test_loading_needs_no_git_network_cwd_or_historical_object_availability(self):
        previous = Path.cwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                with patch.object(subprocess, 'check_output', side_effect=AssertionError('No Git/subprocess fallback')), \
                     patch.object(subprocess, 'run', side_effect=AssertionError('No network/subprocess fallback')):
                    self.assertGreater(len(self.load_original()), 0)
            finally:
                os.chdir(previous)


if __name__ == '__main__':
    unittest.main()
