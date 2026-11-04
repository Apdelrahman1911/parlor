"""Portable initialization policy fixtures, not Apple compatibility evidence."""
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


class InitializationControls(unittest.TestCase):
    def test_initialized_exact_declared_forwarder_does_not_require_unproven_version_equality(self):
        with TemporaryDirectory() as temp:
            out = Path(temp)
            stub = out / 'selected-stub'; stub.write_bytes(b'unit fixture')
            tools = ('/usr/bin/xcrun', str(stub), '/exact/attested/native')
            with patch.object(p, 'SDK_STUB_SHA256', hashlib.sha256(stub.read_bytes()).hexdigest()), \
                 patch.dict(os.environ, {'DEVELOPER_DIR': '/pinned/Xcode'}), \
                 patch.object(p, 'query', return_value=(0, b'')) as queried:
                self.assertEqual(p.initialized_native_query_tool(out, tools, '1051.55'), tools[2])
            queried.assert_called_once_with(out, 'xcode-first-launch-status',
                ['/pinned/Xcode/usr/bin/xcodebuild', '-checkFirstLaunchStatus'], 20)
            grant = json.loads((out / 'native-query-grant.json').read_text())
            self.assertFalse(grant['version_equality_required'])
            self.assertFalse(grant['mutating_first_launch_command_executed'])
            self.assertEqual(grant['executable'], tools[2])

    def test_matching_sdk_version_uses_declared_direct_exec_without_unnecessary_status_command(self):
        with TemporaryDirectory() as temp:
            out = Path(temp); stub = out / 'selected-stub'; stub.write_bytes(b'unit fixture')
            tools = ('xcrun', str(stub), '/exact/attested/native')
            with patch.object(p, 'SDK_STUB_SHA256', hashlib.sha256(stub.read_bytes()).hexdigest()), \
                 patch.object(p, 'query') as queried:
                self.assertEqual(p.initialized_native_query_tool(out, tools, '1051.17.8'), tools[2])
            queried.assert_not_called()
            grant = json.loads((out / 'native-query-grant.json').read_text())
            self.assertIsNone(grant['initialization_exit_code'])
            self.assertEqual(grant['strategy'], 'matching-version-direct-exec-declared-by-sdk-shim')
            self.assertFalse(grant['mutating_first_launch_command_executed'])

    def test_failed_or_timed_out_initialization_never_grants_a_native_query_or_repairs(self):
        for code in (None, 1, 3):
            with self.subTest(code=code), TemporaryDirectory() as temp:
                out = Path(temp); stub = out / 'selected-stub'; stub.write_bytes(b'unit fixture')
                with patch.object(p, 'SDK_STUB_SHA256', hashlib.sha256(stub.read_bytes()).hexdigest()), \
                     patch.dict(os.environ, {'DEVELOPER_DIR': '/pinned/Xcode'}), \
                     patch.object(p, 'query', return_value=(code, b'')) as queried:
                    with self.assertRaisesRegex(RuntimeError, 'UNKNOWN' if code is None else 'not initialized'):
                        p.initialized_native_query_tool(out, ('xcrun', str(stub), '/exact/native'), '1051.55')
                self.assertEqual(queried.call_count, 1)
                self.assertFalse((out / 'native-query-grant.json').exists())

    def test_changed_sdk_stub_never_issues_a_command_or_grants_direct_execution(self):
        with TemporaryDirectory() as temp:
            out = Path(temp); stub = out / 'selected-stub'; stub.write_bytes(b'other fixture')
            with patch.object(p, 'query') as queried:
                with self.assertRaisesRegex(RuntimeError, 'shim changed'):
                    p.initialized_native_query_tool(out, ('xcrun', str(stub), '/exact/native'), '1051.55')
            queried.assert_not_called()
            self.assertFalse((out / 'native-query-grant.json').exists())


if __name__ == '__main__':
    unittest.main()
