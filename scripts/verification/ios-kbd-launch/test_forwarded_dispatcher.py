"""Observed Xcode stub / CoreSimulator executable forwarding admission controls."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('query_probe', Path(__file__).with_name('launch.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class ForwardedDispatcherControls(unittest.TestCase):
    def test_exact_forwarder_requires_apple_signature_and_retains_actual_file_hashes(self):
        with TemporaryDirectory() as temp:
            out = Path(temp)
            tools = tuple(out / n for n in ['xcrun', 'selected-stub', 'native-simctl'])
            for index, tool in enumerate(tools):
                tool.write_bytes(bytes([index + 1]))
            mapping = {'/usr/bin/xcrun': tools[0], '/pinned/usr/bin/simctl': tools[1],
                       p.CORE_SIMCTL: tools[2], str(tools[2]): tools[2]}
            with patch.dict(os.environ, {'DEVELOPER_DIR': '/pinned'}), \
                 patch.object(p.Path, 'resolve', autospec=True, side_effect=lambda path, strict=True: mapping[str(path)]), \
                 patch.object(p, 'query', return_value=(0, b'')) as verified:
                allowed = p.attested_dispatchers(out)
            self.assertEqual(allowed, tuple(map(str, tools)))
            verified.assert_called_once_with(out, 'verify-native-dispatcher',
                ['/usr/bin/codesign', '--verify', '--strict', '-R', 'anchor apple', str(tools[2])], 10)
            record = json.loads((out / 'trusted-dispatcher-tools.json').read_text())
            self.assertEqual(record['native_signature_requirement'], 'anchor apple')
            self.assertEqual(record['matching'], 'samefile')
            self.assertEqual(record['files'][2]['sha256'], hashlib.sha256(b'\x03').hexdigest())
            self.assertNotEqual(record['files'][1]['sha256'], record['files'][2]['sha256'])

    def test_failed_or_timed_out_signature_never_admits_forwarding_executable(self):
        for code in (None, 1, 3):
            with self.subTest(code=code), TemporaryDirectory() as temp:
                out = Path(temp)
                tool = out / 'native-simctl'
                tool.write_bytes(b'fixture')
                with patch.object(p.Path, 'resolve', return_value=tool), \
                     patch.object(p, 'query', return_value=(code, b'')) as verified:
                    with self.assertRaisesRegex(RuntimeError, 'Apple signature verification'):
                        p.attested_dispatchers(out)
                self.assertEqual(verified.call_count, 1)
                self.assertFalse((out / 'trusted-dispatcher-tools.json').exists())

    def test_missing_native_binary_never_issues_a_query_or_profile_grant(self):
        with TemporaryDirectory() as temp:
            out = Path(temp)
            with patch.object(p.Path, 'resolve', side_effect=FileNotFoundError), patch.object(p, 'query') as verified:
                with self.assertRaises(FileNotFoundError):
                    p.attested_dispatchers(out)
            verified.assert_not_called()
            self.assertFalse((out / 'trusted-dispatcher-tools.json').exists())


if __name__ == '__main__':
    unittest.main()
