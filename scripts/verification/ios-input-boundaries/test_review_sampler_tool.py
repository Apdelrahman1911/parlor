"""Portable safeguards only; Linux never certifies native tool availability."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import review_sampler_tool as tool


class SamplerToolReviewTests(unittest.TestCase):
    def test_fixed_read_only_commands(self):
        self.assertEqual([c for _, c in tool.COMMANDS], [
            ['/usr/bin/sw_vers'], ['/usr/bin/sample', '-help'],
            ['/usr/bin/man', '-P', '/bin/cat', 'sample'],
            ['/usr/bin/strings', '-a', '/usr/bin/sample'],
        ])

    def test_option_filter_keeps_stages_not_unrelated_binary_strings(self):
        self.assertEqual(tool.selected_strings(b'noise\n -mayDie\nSymbolication\nUnwinding\n'),
                         b' -mayDie\nSymbolication\nUnwinding\n')

    def test_spindump_profile_reads_only_installed_documentation_never_executes_sampler(self):
        self.assertEqual(tool.SPINDUMP_COMMANDS, (
            ('spindump-man', ['/usr/bin/man', '-P', '/bin/cat', 'spindump']),))
        self.assertNotIn('/usr/sbin/spindump', tool.SPINDUMP_COMMANDS[0][1])

    def test_unknown_profile_never_runs_a_command_or_creates_output(self):
        with patch.object(tool.subprocess, 'run') as run, self.assertRaises(ValueError):
            tool.review(Path('/unavailable'), mode='all-processes')
        run.assert_not_called()

    def test_linux_cannot_attest_apple_capability(self):
        with patch.object(tool.sys, 'platform', 'linux'):
            with self.assertRaisesRegex(RuntimeError, 'actual macOS'):
                tool.review(Path('/unavailable'))

    def test_rejects_existing_or_redirected_output_before_command(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(tool.sys, 'platform', 'darwin'), \
                patch.object(tool.subprocess, 'run') as run:
            root = Path(temporary).resolve()
            with self.assertRaises(ValueError):
                tool.review(root)
            link = root/'link'
            link.symlink_to(root, target_is_directory=True)
            with self.assertRaises(ValueError):
                tool.review(link/'out')
            run.assert_not_called()
