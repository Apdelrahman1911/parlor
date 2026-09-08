"""Pure actual-handler controls; no runner imports, native process or build execution."""
import ast
from contextlib import nullcontext
import copy
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

ROOT = Path(__file__).resolve().parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
RUNNERS = {
    'normal': CAMPAIGN / 'native/normal-ios-launch-proposal-01/run_normal_ios_launch.py',
    'companion': CAMPAIGN / 'l08-storage-functional-companion-02/run_ios_readiness.py',
}


def isolated_command(path, namespace):
    """Compile only the real command function with an entirely inert namespace."""
    functions = [node for node in ast.walk(ast.parse(path.read_text()))
                 if isinstance(node, ast.FunctionDef) and node.name == 'command']
    if len(functions) != 1:
        raise RuntimeError('Ambiguous command extraction')
    exec(compile(ast.Module(body=functions, type_ignores=[]), '<isolated-owned-command>', 'exec'), namespace)
    return namespace['command']


class NativeCommandFailureControls(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-command-control-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def fixture(self, name, *, primary=None, secondary=None, failure_at='refresh', cleanup_at='stop',
                exit_code=-15):
        destination = self.root / name
        destination.mkdir()
        receipt, saved = {'commands': []}, []
        save = lambda: saved.append(copy.deepcopy(receipt))
        child = Mock(returncode=exit_code)
        child.poll.side_effect = [None, exit_code]
        owner = Mock()
        process = SimpleNamespace(Popen=Mock(return_value=child), STDOUT=-2,
                                  TimeoutExpired=subprocess.TimeoutExpired)
        clock = SimpleNamespace(monotonic=Mock(return_value=0), sleep=Mock())
        environment = {'SECRET_ENV': 'not-retained'}
        namespace = dict(ROOT=ROOT, subprocess=process, time=clock,
                         defer_parent_signals=nullcontext, now=lambda: 'synthetic-time',
                         receipt=receipt, save=save, dest=destination, owner=owner, env=environment)
        command = isolated_command(RUNNERS[name], namespace)
        receiver = SimpleNamespace(destination=destination, receipt=receipt, save=save, owner=owner,
                                   environment=environment, check_watched_outputs=Mock())
        if primary is not None:
            if failure_at == 'popen':
                process.Popen.side_effect = primary
            elif failure_at == 'register':
                owner.register.side_effect = primary
            elif failure_at == 'poll':
                child.poll.side_effect = [primary, exit_code]
            else:
                owner.refresh.side_effect = primary
        if secondary is not None:
            if cleanup_at == 'stop':
                owner.stop.side_effect = secondary
            else:
                child.poll.side_effect = [primary if failure_at == 'poll' else None, secondary]
        invoke = (lambda: command(receiver, ['owned-public-command'], 'command.log')) if name == 'normal' else (
                  lambda: command(['owned-public-command'], 'command.log'))
        return SimpleNamespace(invoke=invoke, receipt=receipt, saved=saved, child=child,
                               owner=owner, process=process, receiver=receiver)

    def assert_primary(self, fixture, error, *, stage=None, secondary=None):
        with self.assertRaises(type(error)) as caught:
            fixture.invoke()
        self.assertIs(caught.exception, error)
        self.assertEqual(len(fixture.receipt['commands']), 1)
        entry = fixture.receipt['commands'][0]
        self.assertIs(entry['interrupted_or_failed'], True)
        self.assertEqual(entry['primary_error'], {'type': type(error).__name__, 'message': str(error)[:800]})
        self.assertEqual(entry['finished_at'], 'synthetic-time')
        self.assertEqual(fixture.saved[-1]['commands'][0], entry)
        if secondary is None:
            self.assertNotIn('command_cleanup_error', entry)
        else:
            self.assertEqual(entry['command_cleanup_error'], dict(
                stage=stage, type=type(secondary).__name__, message=str(secondary)[:800]))
        return entry

    def assert_command_only_stop(self, fixture):
        fixture.owner.stop.assert_called_once()
        selected = fixture.owner.stop.call_args.args[0]
        for role in ('command', 'gradle', 'app', 'xcode', 'foreign', ''):
            self.assertEqual(selected({'role': role}), role == 'command')

    def test_refresh_primary_survives_stop_failure_as_same_exception(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = RuntimeError('primary refresh'), RuntimeError('secondary stop')
                fixture = self.fixture(name, primary=primary, secondary=secondary)
                entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
                self.assertNotIn('exit_code', entry)
                self.assert_command_only_stop(fixture)
                self.assertEqual(fixture.child.poll.call_count, 1)

    def test_refresh_primary_survives_post_stop_poll_failure(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = RuntimeError('primary refresh'), RuntimeError('secondary exit read')
                fixture = self.fixture(name, primary=primary, secondary=secondary, cleanup_at='poll')
                entry = self.assert_primary(fixture, primary, stage='read-owned-command-exit', secondary=secondary)
                self.assertNotIn('exit_code', entry)
                self.assert_command_only_stop(fixture)
                self.assertEqual(fixture.child.poll.call_count, 2)

    def test_popen_failure_has_no_child_and_never_stops(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary = OSError('owned spawn failed')
                fixture = self.fixture(name, primary=primary, failure_at='popen')
                entry = self.assert_primary(fixture, primary)
                self.assertNotIn('exit_code', entry)
                fixture.owner.stop.assert_not_called()
                fixture.owner.register.assert_not_called()
                fixture.child.poll.assert_not_called()

    def test_registration_failure_retains_primary_and_owned_stop_selection(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = RuntimeError('register'), RuntimeError('stop')
                fixture = self.fixture(name, primary=primary, secondary=secondary, failure_at='register')
                self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
                self.assert_command_only_stop(fixture)
                fixture.child.poll.assert_not_called()

    def test_initial_poll_failure_retains_primary(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary = RuntimeError('initial poll')
                fixture = self.fixture(name, primary=primary, failure_at='poll')
                entry = self.assert_primary(fixture, primary)
                self.assertEqual(entry['exit_code'], -15)
                fixture.owner.refresh.assert_not_called()
                self.assert_command_only_stop(fixture)

    def test_base_exception_primary_and_stop_interruption_are_not_swallowed_or_substituted(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = KeyboardInterrupt('primary interrupt'), SystemExit('cleanup interrupt')
                fixture = self.fixture(name, primary=primary, secondary=secondary)
                self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
                self.assert_command_only_stop(fixture)

    def test_base_exception_during_exit_read_does_not_mask_primary(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = SystemExit('primary exit'), KeyboardInterrupt('exit read interrupted')
                fixture = self.fixture(name, primary=primary, secondary=secondary, cleanup_at='poll')
                self.assert_primary(fixture, primary, stage='read-owned-command-exit', secondary=secondary)

    def test_primary_and_secondary_messages_are_bounded(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary, secondary = RuntimeError('p' * 4096), RuntimeError('s' * 4096)
                fixture = self.fixture(name, primary=primary, secondary=secondary)
                entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
                self.assertEqual(len(entry['primary_error']['message']), 800)
                self.assertEqual(len(entry['command_cleanup_error']['message']), 800)

    def test_timeout_diagnostics_do_not_capture_raw_output_stderr_or_environment(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary = subprocess.TimeoutExpired(['owned-public-command'], 15,
                    output=b'PRIVATE-RAW-STDOUT', stderr=b'PRIVATE-RAW-STDERR')
                fixture = self.fixture(name, primary=primary)
                self.assert_primary(fixture, primary)
                serialized = json.dumps(fixture.saved)
                for absent in ('PRIVATE-RAW-STDOUT', 'PRIVATE-RAW-STDERR', 'SECRET_ENV', 'not-retained'):
                    self.assertNotIn(absent, serialized)

    def test_successful_cleanup_preserves_primary_and_actual_negative_exit(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary = RuntimeError('primary')
                fixture = self.fixture(name, primary=primary)
                entry = self.assert_primary(fixture, primary)
                self.assertEqual(entry['exit_code'], -15)
                self.assert_command_only_stop(fixture)

    def test_unknown_exit_after_stop_is_not_invented_as_success(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                primary = RuntimeError('primary')
                fixture = self.fixture(name, primary=primary, exit_code=None)
                entry = self.assert_primary(fixture, primary)
                self.assertIsNone(entry['exit_code'])

    def test_normal_success_and_nonzero_results_keep_existing_fields_and_do_not_stop(self):
        for name in RUNNERS:
            with self.subTest(runner=name):
                fixture = self.fixture(name, exit_code=0)
                fixture.child.poll.side_effect = None
                fixture.child.poll.return_value = 0
                self.assertEqual(fixture.invoke(), 0)
                fixture.owner.stop.assert_not_called()
                self.assertEqual(set(fixture.receipt['commands'][0]),
                                 {'command', 'started_at', 'log', 'exit_code', 'finished_at'})
                self.assertEqual(fixture.saved[-1], fixture.receipt)
                fixture.child.returncode = 23
                fixture.child.poll.return_value = 23
                # The real normal command requires a new log; the companion also gets one.
                (self.root / name / 'command.log').unlink()
                self.assertEqual(fixture.invoke(), 23)
                fixture.owner.stop.assert_not_called()
                self.assertEqual(set(fixture.receipt['commands'][1]),
                                 {'command', 'started_at', 'log', 'exit_code', 'finished_at'})


if __name__ == '__main__':
    unittest.main()
