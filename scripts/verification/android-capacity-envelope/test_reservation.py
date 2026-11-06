import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import reservation as r


class ReservationSafetyTest(unittest.TestCase):
    def test_reservation_uses_actual_free_space_and_block_alignment(self):
        for extra in (0, 1, 4095, 4096, 80 * 1024**3):
            n = r.reserve_length(r.TARGET + extra)
            self.assertEqual(n % 4096, 0)
            self.assertLessEqual(n, extra)
            self.assertLess(extra - n, 4096)
        for free in (-1, r.MINIMUM, r.TARGET - 1, 'free'):
            with self.assertRaises(RuntimeError): r.reserve_length(free)

    def test_only_an_unchanged_single_link_regular_file_is_admitted(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'owned'; path.write_bytes(b'control')
            original = r.identity(path.stat())
            r.require_identity(path.lstat(), original)
            for key in original:
                with self.subTest(key=key), self.assertRaises(RuntimeError):
                    r.require_identity(path.lstat(), dict(original, **{key: original[key] + 1}))
            link = path.with_name('link'); link.symlink_to(path)
            with self.assertRaises(RuntimeError): r.require_identity(link.lstat(), original)
            os.link(path, path.with_name('hardlink'))
            with self.assertRaises(RuntimeError): r.require_identity(path.stat(), original)

    def test_receipts_are_private_exclusive_and_never_follow_links(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'evidence'
            r.new_receipt(path, {'result': 'synthetic'})
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            before = path.read_bytes()
            with self.assertRaises(FileExistsError): r.new_receipt(path, {})
            link = path.with_name('link'); link.symlink_to(path)
            with self.assertRaises(FileExistsError): r.new_receipt(link, {})
            self.assertEqual(path.read_bytes(), before)

    def test_local_context_is_rejected_before_reservation_or_source_access(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(r.subprocess, 'check_output') as call:
            with self.assertRaises(RuntimeError): r.context()
            call.assert_not_called()


if __name__ == '__main__': unittest.main()
