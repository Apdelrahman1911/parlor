"""Portable intake controls only; no Apple command or application execution."""
from pathlib import Path
import hashlib
import re
import subprocess
from tempfile import TemporaryDirectory
import unittest

import intake


class IntakeTest(unittest.TestCase):
    def test_agents_digest_is_complete_sha256_not_a_transcription_fragment(self):
        self.assertIsNotNone(re.fullmatch(r'[0-9a-f]{64}', intake.AGENTS_SHA))

    def test_agents_digest_binds_actual_unchanged_file_and_git_blob(self):
        root = Path(intake.__file__).resolve().parents[3]
        committed = subprocess.check_output(['git', 'show', 'HEAD:AGENTS.md'], cwd=root)
        self.assertEqual(hashlib.sha256(committed).hexdigest(), intake.AGENTS_SHA)
        self.assertEqual((root / 'AGENTS.md').read_bytes(), committed)

    def test_captured_xcode263_usage_advertises_only_mutating_cache_operations(self):
        # Exact relevant lines from run41880824238 runtime-help.stderr.
        # The failed bare helper returned the same usage, with exit117.
        usage = ('Usage: simctl runtime <operation> <arguments>\n'
                 '\tdyld_shared_cache update (<runtime> || --all) [--force]\n'
                 '\tdyld_shared_cache remove (<runtime> || --all)\n')
        self.assertEqual(intake.cache_operations(usage), ['update', 'remove'])
        self.assertNotIn('cache-help', intake.COMMANDS)
        self.assertNotIn('cache-print', intake.COMMANDS)

    def test_unrelated_or_example_usage_cannot_advertise_a_cache_operation(self):
        for text in ['', 'Usage: another tool', 'dyld_shared_cache print']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                intake.cache_operations(text)
        usage = ('Usage: simctl runtime <operation> <arguments>\n'
                 'Example: dyld_shared_cache print\nprint Print cache information\n')
        self.assertEqual(intake.cache_operations(usage), [])

    def test_toolchain_requires_exact_version_build_and_tool_path(self):
        path = intake.DEVELOPER + '/usr/bin/simctl'
        intake.require_toolchain('Xcode 26.3\nBuild version 17C529\n', path + '\n')
        for version, tool in [('Xcode 26.4\nBuild version 17E192', path),
                              ('Xcode 26.3\nBuild version other', path),
                              ('Xcode 26.3\nBuild version 17C529', '/usr/bin/simctl'),
                              ('Xcode 26.3\nBuild version 17C529', path + '/../simctl')]:
            with self.subTest(version=version, tool=tool), self.assertRaises(ValueError):
                intake.require_toolchain(version, tool)

    def test_new_output_is_owned_direct_child_and_never_overwritten(self):
        with TemporaryDirectory() as raw:
            root = Path(raw).resolve()
            target = root / 'intake'
            self.assertEqual(intake.output_path(str(target), str(root)), target)
            target.mkdir()
            for invalid in [target, root, root.parent / 'unowned', target / 'nested']:
                with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                    intake.output_path(str(invalid), str(root))

    def test_no_update_force_boot_launch_or_test_commands_exist(self):
        self.assertEqual(len(intake.COMMANDS), 5)
        for args in intake.COMMANDS.values():
            self.assertFalse(set(args) & {'update', '--force', '--all', 'boot', 'spawn', 'launch', 'test'})


if __name__ == '__main__':
    unittest.main()
