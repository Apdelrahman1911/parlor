"""Portable intake controls only; no Apple command or application execution."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import intake


class IntakeTest(unittest.TestCase):
    def test_usage_exit_is_not_accepted_without_actual_matching_native_usage(self):
        for code in [0, 64]:
            intake.require_cache_usage(code, 'Usage: simctl runtime dyld_shared_cache <operation>')
        for code, text in [(1, 'dyld_shared_cache'), (64, ''), (64, 'Usage: another tool')]:
            with self.subTest(code=code, text=text), self.assertRaises(ValueError):
                intake.require_cache_usage(code, text)

    def test_only_native_advertised_read_only_print_is_selected(self):
        self.assertTrue(intake.cache_print_advertised('  print    Print cache information.\n'))
        for text in ['update Update caches', 'printable Print caches',
                     'Example: print Print caches', 'print Update caches', '', 'list List caches']:
            with self.subTest(text=text):
                self.assertFalse(intake.cache_print_advertised(text))

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
        self.assertEqual(len(intake.COMMANDS), 7)
        for args in intake.COMMANDS.values():
            self.assertFalse(set(args) & {'update', '--force', '--all', 'boot', 'spawn', 'launch', 'test'})


if __name__ == '__main__':
    unittest.main()
