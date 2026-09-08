"""Pure holder-query contracts; no native command or user path is accessed."""
import subprocess
import unittest
from unittest.mock import patch

from owned_lane import lsof_pids


class NativeHolderQueryTest(unittest.TestCase):
    def test_warning_enable_follows_terse_option(self):
        result = subprocess.CompletedProcess([], 1, '', '')
        with patch('owned_lane.subprocess.run', return_value=result) as run:
            self.assertEqual(set(), lsof_pids(['+D', '/synthetic/owned']))
        run.assert_called_once_with(
            ['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', '/synthetic/owned'],
            text=True, capture_output=True, timeout=30,
        )

    def test_partial_selection_retains_every_reported_holder(self):
        for code in (0, 1):
            with self.subTest(code=code), patch('owned_lane.subprocess.run', return_value=
                    subprocess.CompletedProcess([], code, '123\n456\n123\n', '')):
                self.assertEqual({123, 456}, lsof_pids(['/synthetic/owned/fifo']))

    def test_no_matches_requires_no_successful_selection(self):
        with patch('owned_lane.subprocess.run', return_value=subprocess.CompletedProcess([], 1, '', '')):
            self.assertEqual(set(), lsof_pids(['/synthetic/owned/fifo']))
        with patch('owned_lane.subprocess.run', return_value=subprocess.CompletedProcess([], 0, '', '')):
            with self.assertRaises(RuntimeError):
                lsof_pids(['/synthetic/owned/fifo'])

    def test_warning_never_means_no_holders_and_does_not_expose_raw_paths(self):
        warning = 'lsof: WARNING: cannot stat /synthetic/unrelated/private-name\n'
        for code in (0, 1):
            for output in ('', '123\n'):
                with self.subTest(code=code, output=output), patch('owned_lane.subprocess.run', return_value=
                        subprocess.CompletedProcess([], code, output, warning)):
                    with self.assertRaises(RuntimeError) as raised:
                        lsof_pids(['+D', '/synthetic/owned'])
                    self.assertNotIn('/synthetic/unrelated', str(raised.exception))

    def test_errors_and_malformed_output_fail_closed(self):
        for code, output, diagnostic in ((2, '', ''), (-1, '', ''), (1, 'not-a-pid\n', ''),
                                         (1, '123\nwarning\n', ''), (0, '123\n', 'error')):
            with self.subTest(code=code, output=output), patch('owned_lane.subprocess.run', return_value=
                    subprocess.CompletedProcess([], code, output, diagnostic)):
                with self.assertRaises(RuntimeError):
                    lsof_pids(['/synthetic/owned/fifo'])

    def test_acceptance_bounds_are_enforced(self):
        for output, diagnostic in (('1\n' * 32769, ''), ('123\n', 'x' * 4097)):
            with patch('owned_lane.subprocess.run', return_value=
                    subprocess.CompletedProcess([], 1, output, diagnostic)):
                with self.assertRaises(RuntimeError):
                    lsof_pids(['+D', '/synthetic/owned'])

    def test_timeout_is_not_converted_to_an_empty_holder_set(self):
        with patch('owned_lane.subprocess.run', side_effect=subprocess.TimeoutExpired('synthetic', 30)):
            with self.assertRaises(subprocess.TimeoutExpired):
                lsof_pids(['/synthetic/owned/fifo'])


if __name__ == '__main__':
    unittest.main()
