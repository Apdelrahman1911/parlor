"""Portable controls only. No real cache, Simulator, or input is touched."""
from copy import deepcopy
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import prepare


class PreparationTest(unittest.TestCase):
    def preflight(self):
        return dict(status='COLLECTED_READ_ONLY_NOT_RUNTIME_QUALIFICATION',
                    advertised_cache_operations=['update', 'remove'],
                    commands=[dict(label=k, argv=v, exit_code=0)
                              for k, v in prepare.intake.COMMANDS.items()])

    def runtimes(self):
        return dict(runtimes=[dict(identifier=prepare.RUNTIME, isAvailable=True,
                                   version='26.2', buildversion='23C54')])

    def test_supported_completion_never_forces_removes_boots_or_generates_input(self):
        self.assertEqual(prepare.COMMAND, ('xcrun', 'simctl', 'runtime',
                                           'dyld_shared_cache', 'update', '--all'))
        self.assertFalse(set(prepare.COMMAND) & {'--force', 'remove', 'boot', 'spawn', 'launch', 'test'})

    def test_exact_completed_preflight_is_required(self):
        prepare.require_preflight(self.preflight(), self.runtimes())
        for change in [dict(status='FAILED_INTAKE'), dict(commands=[]),
                       dict(advertised_cache_operations=['remove'])]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                prepare.require_preflight(self.preflight() | change, self.runtimes())

    def test_missing_duplicate_failed_redirected_or_boolean_native_outcomes_reject(self):
        for mutation in ['missing', 'duplicate', 'failed', 'redirected', 'boolean']:
            receipt = deepcopy(self.preflight())
            if mutation == 'missing': receipt['commands'].pop()
            if mutation == 'duplicate': receipt['commands'][-1] = receipt['commands'][0]
            if mutation == 'failed': receipt['commands'][0]['exit_code'] = 1
            if mutation == 'redirected': receipt['commands'][0]['argv'] = ['other-tool']
            if mutation == 'boolean': receipt['commands'][0]['exit_code'] = False
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                prepare.require_preflight(receipt, self.runtimes())

    def test_original_runtime_availability_version_and_build_are_not_inferred(self):
        for change in [dict(identifier='other'), dict(isAvailable=False),
                       dict(version='26.3'), dict(buildversion='other')]:
            runtimes = self.runtimes()
            runtimes['runtimes'][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                prepare.require_preflight(self.preflight(), runtimes)
        for rows in [[], self.runtimes()['runtimes'] * 2]:
            with self.assertRaises(ValueError):
                prepare.require_preflight(self.preflight(), dict(runtimes=rows))

    def run_fake(self, outcome):
        class Process:
            pid, returncode, waits = 123, None, 0
            def wait(self, timeout):
                self.waits += 1
                if outcome == 'timeout' and self.waits == 1:
                    raise subprocess.TimeoutExpired(prepare.COMMAND, timeout)
                self.returncode = 0 if outcome == 'success' else -15 if outcome == 'timeout' else 1
                return self.returncode
        process = Process()
        with TemporaryDirectory() as raw, patch.object(prepare.subprocess, 'Popen', return_value=process) as popen, \
                patch.object(prepare.os, 'killpg') as kill:
            result = prepare.complete_once(Path(raw))
            self.assertEqual(popen.call_count, 1)
            self.assertTrue(popen.call_args.kwargs['start_new_session'])
            self.assertEqual(popen.call_args.args[0], prepare.COMMAND)
            self.assertEqual(kill.call_count, 1 if outcome == 'timeout' else 0)
            if outcome == 'timeout': self.assertEqual(kill.call_args.args[0], process.pid)
            self.assertTrue(result['reaped'])
            return result

    def test_command_success_is_scoped_completion_not_causality(self):
        self.assertTrue(self.run_fake('success')['completed'])

    def test_nonzero_exit_is_preserved_without_retry(self):
        result = self.run_fake('failure')
        self.assertFalse(result['completed'])
        self.assertEqual(result['exit_code'], 1)
        self.assertEqual(result['retries'], 0)

    def test_timeout_is_preserved_reaped_and_never_retried(self):
        result = self.run_fake('timeout')
        self.assertFalse(result['completed'])
        self.assertTrue(result['timed_out'])
        self.assertEqual(result['retries'], 0)


if __name__ == '__main__':
    unittest.main()
