"""Closed command diagnostics and mocked real Lane.command; no native process."""
from contextlib import contextmanager, nullcontext
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from types import MethodType, SimpleNamespace
import unittest
from unittest.mock import patch

import external_image_diagnostics as diagnostic
import run_normal_ios_launch as runner
import test_image_diagnostic_orchestration as fixtures
from test_normal_launch_controls import PID


class VmmapCommandFailureTests(unittest.TestCase):
    def setUp(self):
        allocation = tempfile.TemporaryDirectory(prefix='parlor-vmmap-failure-control-')
        self.addCleanup(allocation.cleanup)
        self.base, self.ordinal = Path(allocation.name).resolve(), 0

    def fixture(self):
        context = fixtures.ImageDiagnosticOrchestrationTests.fixture(self)
        context['lane'].require = MethodType(runner.Lane.require, context['lane'])
        context['lane'].receipt['commands'] = []
        return context

    @contextmanager
    def execution(self, context, code=255, failure=None):
        def child(arguments, *, stdout, **_options):
            if arguments[:3] == ['xcrun', 'simctl', 'launch']:
                stdout.write(f'{runner.APP_ID}: {PID}\n'.encode()); result = 0
            elif arguments[0] == '/usr/bin/sample':
                Path(arguments[-1]).write_text(context['raw_sample']); result = 0
            elif arguments[:2] == ['/usr/bin/vmmap', '-w']:
                stdout.write(b'vmmap: could not obtain task port for process ' + str(PID).encode() +
                             b': permission denied /Users/PRIVATE_COMMAND_PATH 0xdeadbeef\n')
                stdout.flush()
                if failure is not None: raise failure
                result = code
            else:
                self.fail('Unexpected command in synthetic control')
            return SimpleNamespace(pid=51001, returncode=result, poll=lambda: result)
        with fixtures.ImageDiagnosticOrchestrationTests.mocked_native(self, context), \
             patch.object(runner.subprocess, 'Popen', side_effect=child), \
             patch.object(runner, 'defer_parent_signals', side_effect=nullcontext):
            yield

    def test_closed_vocabulary_unknown_bytes_and_requested_pid_never_expose_raw_output(self):
        raw = (f'vmmap: could not obtain task port for process {PID}: permission denied\n'.encode() +
               b'/Users/PRIVATE_NAME/map 0xdeadbeef OtherProcess [83] secret\xff\x00\n')
        value = diagnostic.vmmap_command_diagnostic(raw, PID, 255)
        self.assertEqual('FAILURE_ONLY_VMMAP_COMMAND', value['kind'])
        self.assertFalse(value['proves_provenance'])
        self.assertEqual(255, value['exit_code'])
        self.assertEqual(['vmmap', 'could', 'not', 'obtain', 'task', 'port', 'for', 'process', 'REQUESTED_PID',
                          'permission', 'denied'], value['lines'][0]['tokens'])
        encoded = json.dumps(value)
        for secret in ('PRIVATE_NAME', '0xdeadbeef', 'OtherProcess', '[83]', 'secret', str(PID)):
            self.assertNotIn(secret, encoded)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), value['sha256'])
        unknown = diagnostic.vmmap_command_diagnostic(b'new-unknown-format\n', PID, 255)
        self.assertNotIn('permission', json.dumps(unknown))
        self.assertEqual('TOKEN_DIAGNOSTIC_ONLY_NO_INFERRED_FAILURE_CAUSE', unknown['interpretation'])

    def test_empty_output_strict_context_and_line_token_byte_budgets(self):
        self.assertEqual(0, diagnostic.vmmap_command_diagnostic(b'', PID, 255)['bytes'])
        raw = (b'unknown ' * 100 + b'\n') * 10
        value = diagnostic.vmmap_command_diagnostic(raw, PID, 255)
        self.assertEqual(8, len(value['lines']))
        self.assertTrue(value['line_limit_reached'])
        self.assertTrue(all(row['truncated'] and len(row['tokens']) == 32 for row in value['lines']))
        self.assertLessEqual(len((json.dumps(value, indent=2) + '\n').encode()), 65536)
        for data, pid, code in ((None, PID, 255), ('text', PID, 255), (b'x', True, 255), (b'x', 1, 255),
                                (b'x', PID, True), (b'x', PID, 0), (b'x', PID, 256),
                                (b'x' * (diagnostic.MAX_INPUT_BYTES + 1), PID, 255)):
            with self.subTest(pid=pid, code=code), self.assertRaises(RuntimeError):
                diagnostic.vmmap_command_diagnostic(data, pid, code)

    def test_actual_command_nonzero_preserves_diagnostic_on_both_existing_paths(self):
        for failed_sample in (False, True):
            with self.subTest(failed_sample=failed_sample):
                context = self.fixture(); lane = context['lane']
                original = RuntimeError('original sample rejection')
                parse = patch.object(runner, 'parse_sample', side_effect=original) if failed_sample else nullcontext()
                with self.execution(context), parse, self.assertRaises(RuntimeError) as raised:
                    lane.observe_external_images()
                if failed_sample:
                    self.assertIs(original, raised.exception)
                    event = lane.receipt['failure_only_vmmap_after_sample']
                    self.assertEqual('FAILED_COMMAND_OR_LIFETIME', event['status'])
                    self.assertTrue(event['lifetime_after_verified'])
                else:
                    self.assertEqual('Required owned command failed; consult its retained receipt', str(raised.exception))
                path = lane.destination / 'vmmap-command-failure.json'; value = json.loads(path.read_text())
                self.assertEqual(255, value['exit_code'])
                self.assertFalse(value['proves_provenance'])
                self.assertEqual(runner.digest(path), lane.receipt['vmmap_command_failure_sha256'])
                self.assertNotIn('PRIVATE_COMMAND_PATH', path.read_text())
                self.assertNotIn('0xdeadbeef', path.read_text())
                commands = [row for row in lane.receipt['commands'] if row['command'][:2] == ['/usr/bin/vmmap', '-w']]
                self.assertEqual([255], [row['exit_code'] for row in commands])
                self.assertNotEqual('PASS', lane.receipt['provenance_status'])
                self.assertFalse((lane.destination / 'separate-external-image-provenance.json').exists())
                fixtures.ImageDiagnosticOrchestrationTests.assert_raw_removed(self, lane)

    def test_diagnostic_failure_or_interruption_cannot_mask_original_nonzero_command(self):
        for error in (ValueError('PRIVATE_COLLECTOR_ERROR'), KeyboardInterrupt()):
            context = self.fixture(); lane = context['lane']
            with self.execution(context), patch.object(runner, 'vmmap_command_diagnostic', side_effect=error), \
                 self.assertRaisesRegex(RuntimeError, '^Required owned command failed; consult its retained receipt$'):
                lane.observe_external_images()
            self.assertEqual(type(error).__name__, lane.receipt['vmmap_command_diagnostic_error_type'])
            self.assertNotIn('PRIVATE_COLLECTOR_ERROR', json.dumps(lane.receipt))
            self.assertNotIn('vmmap_command_failure_sha256', lane.receipt)
            fixtures.ImageDiagnosticOrchestrationTests.assert_raw_removed(self, lane)

    def test_interrupted_or_timed_out_command_does_not_gain_completed_failure_evidence(self):
        for error in (KeyboardInterrupt(), subprocess.TimeoutExpired('synthetic', 45)):
            context = self.fixture(); lane = context['lane']
            with self.execution(context, failure=error), patch.object(runner, 'vmmap_command_diagnostic') as collect, \
                 self.assertRaises(type(error)) as raised:
                lane.observe_external_images()
            self.assertIs(error, raised.exception)
            collect.assert_not_called()
            self.assertNotIn('vmmap_command_failure_sha256', lane.receipt)
            fixtures.ImageDiagnosticOrchestrationTests.assert_raw_removed(self, lane)

    def test_completed_success_and_other_commands_do_not_create_failure_diagnostics(self):
        for arguments, code in ((['/usr/bin/vmmap', '-w', str(PID)], 0), (['/bin/false'], 255)):
            context = self.fixture(); lane = context['lane']
            with patch.object(runner.subprocess, 'Popen', return_value=SimpleNamespace(
                    pid=51001, returncode=code, poll=lambda: code)), \
                 patch.object(runner, 'defer_parent_signals', side_effect=nullcontext), \
                 patch.object(runner, 'vmmap_command_diagnostic') as collect:
                self.assertEqual(code, lane.command(arguments, 'synthetic', output=lane.temporary / 'owned-app.vmmap.txt'))
            collect.assert_not_called()
            self.assertNotIn('vmmap_command_failure_sha256', lane.receipt)

    def test_command_diagnostic_only_reads_exact_owned_single_link_file_and_closes_fd(self):
        context = self.fixture(); lane = context['lane']; path = lane.temporary / 'owned-app.vmmap.txt'
        other = lane.temporary / 'unrelated-synthetic'; other.write_bytes(b'private synthetic bytes')
        with self.assertRaises(RuntimeError): lane.collect_vmmap_command_failure(other, PID, 255)
        path.symlink_to(other)
        with self.assertRaises(RuntimeError): lane.collect_vmmap_command_failure(path, PID, 255)
        path.unlink(); runner.os.link(other, path)
        with self.assertRaises(RuntimeError): lane.collect_vmmap_command_failure(path, PID, 255)
        path.unlink(); path.write_bytes(b'vmmap: failed\n')
        close = runner.os.close
        with patch.object(runner.os, 'close', wraps=close) as closed, \
             patch.object(runner, 'vmmap_command_diagnostic', side_effect=ValueError('synthetic')):
            with self.assertRaises(ValueError): lane.collect_vmmap_command_failure(path, PID, 255)
        self.assertEqual(1, closed.call_count)
        self.assertEqual(b'private synthetic bytes', other.read_bytes())
        self.assertFalse((lane.destination / 'vmmap-command-failure.json').exists())


if __name__ == '__main__':
    unittest.main()
