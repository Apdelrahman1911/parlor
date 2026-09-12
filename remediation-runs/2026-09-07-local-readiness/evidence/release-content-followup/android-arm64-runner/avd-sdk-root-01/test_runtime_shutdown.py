"""Exercise the active runner's exact finalizer AST, never an emulator or ADB."""
import ast
import copy
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock

import owned_arm64_smoke_sdk as runner


class RuntimeShutdownTest(unittest.TestCase):
    def finalize(self, *, worker_error=False, worker_receipt=False, bad_ack=False,
                 survives=False, absent_socket=False, owned=True, commands=True):
        events = []
        state = dict(adb_live=not absent_socket, elapsed=0.0)
        report = dict(cleanup=[])

        def shutdown():
            events.append('workers')
            if worker_error:
                raise RuntimeError('synthetic worker retirement failure')
            # Mirrors the original05 shutdown: an emulator's late internal ADB
            # command can create a detached private server after an early kill.
            if not absent_socket:
                state['adb_live'] = True
            return [{'workers': 'synthetic survivor'}] if worker_receipt else []

        def stop_socket(_owned):
            events.append('socket')
            if bad_ack:
                raise RuntimeError('synthetic bad acknowledgement')
            existed = state['adb_live']
            if not survives:
                state['adb_live'] = False
            return existed

        def sleep(seconds):
            state['elapsed'] += seconds

        workers = SimpleNamespace(shutdown=shutdown, receipts=lambda: [], events=[])
        command_owner = SimpleNamespace(processes=workers, receipts=[])
        namespace = dict(vars(runner))
        namespace.update(commands=command_owner if commands else None,
                         owned=SimpleNamespace(path=Path('/synthetic-owned')) if owned else None,
                         adb_prefix=['synthetic-prefix'], report=report,
                         stop_owned_adb_socket=stop_socket,
                         socket_is_live=lambda _path: state['adb_live'],
                         time=SimpleNamespace(monotonic=lambda: state['elapsed'], sleep=sleep))
        source = ast.parse(Path(runner.__file__).read_text())
        execute = next(node for node in source.body if isinstance(node, ast.FunctionDef) and node.name == 'run')
        guarded = next(node for node in execute.body if isinstance(node, ast.Try)).finalbody[0]
        self.assertIsInstance(guarded, ast.If)
        self.assertEqual('commands is not None', ast.unparse(guarded.test))
        finalizer = ast.fix_missing_locations(ast.Module(body=[copy.deepcopy(guarded)], type_ignores=[]))
        with mock.patch.object(runner.subprocess, 'Popen') as process:
            exec(compile(finalizer, runner.__file__, 'exec'), namespace)
            process.assert_not_called()
        return report, events, state

    def test_late_private_adb_restart_is_stopped_after_workers_retire(self):
        report, events, state = self.finalize()
        self.assertEqual([], report['cleanup'])
        self.assertEqual(['workers', 'socket'], events)
        self.assertFalse(state['adb_live'])
        self.assertTrue(report['owned_adb_shutdown_acknowledged'])

    def test_worker_retirement_exception_is_retained_and_private_socket_still_stops(self):
        report, events, state = self.finalize(worker_error=True)
        self.assertEqual(['workers', 'socket'], events)
        self.assertFalse(state['adb_live'])
        self.assertEqual('error', report['cleanup'][0]['workers'])

    def test_reported_survivors_are_not_erased_by_successful_socket_shutdown(self):
        report, events, state = self.finalize(worker_receipt=True)
        self.assertEqual(['workers', 'socket'], events)
        self.assertEqual([{'workers': 'synthetic survivor'}], report['cleanup'])
        self.assertFalse(state['adb_live'])

    def test_bad_shutdown_acknowledgement_is_preserved_as_cleanup_failure(self):
        report, events, _ = self.finalize(bad_ack=True, absent_socket=True)
        self.assertEqual(['workers', 'socket'], events)
        self.assertEqual('error', report['cleanup'][0]['adb_shutdown'])

    def test_live_private_socket_times_out_instead_of_claiming_cleanup(self):
        report, _, state = self.finalize(survives=True)
        self.assertEqual('error', report['cleanup'][0]['adb_socket'])
        self.assertGreaterEqual(state['elapsed'], 5)
        self.assertLess(state['elapsed'], 6)

    def test_absent_socket_does_not_invent_a_shutdown_or_create_a_process(self):
        report, events, _ = self.finalize(absent_socket=True)
        self.assertEqual(['workers', 'socket'], events)
        self.assertEqual([], report['cleanup'])
        self.assertFalse(report['owned_adb_shutdown_acknowledged'])

    def test_partial_initialization_only_cleans_resources_that_were_created(self):
        report, events, _ = self.finalize(owned=False)
        self.assertEqual(['workers'], events)
        self.assertEqual([], report['cleanup'])
        report, events, _ = self.finalize(commands=False)
        self.assertEqual([], events)
        self.assertEqual({'cleanup': []}, report)
