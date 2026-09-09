"""Pure actual-handler controls; no runner imports, native process or build execution."""
import ast
from contextlib import nullcontext
import copy
import json
from pathlib import Path
import re
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


def isolated_xcode_boundary(path, namespace):
    """Execute the actual A build try/finally, never main or its allocation path."""
    tree = ast.parse(path.read_text())
    matches = [node for node in ast.walk(tree) if isinstance(node, ast.Try) and node.finalbody and
               any(isinstance(part, ast.Constant) and part.value == 'stop-xcode-immediate'
                   for part in ast.walk(ast.Module(body=node.finalbody, type_ignores=[])))]
    if len(matches) != 1:
        raise RuntimeError('Ambiguous actual Xcode finalizer')
    target = matches[0]
    blocks = [value for node in ast.walk(tree) for _, value in ast.iter_fields(node)
              if isinstance(value, list) and any(item is target for item in value)]
    if len(blocks) != 1:
        raise RuntimeError('Ambiguous actual Xcode statement block')
    block = blocks[0]
    stop = next(index for index, item in enumerate(block) if item is target)
    starts = [index for index, node in enumerate(block[:stop]) if isinstance(node, ast.Assign) and
              isinstance(node.value, ast.Constant) and node.value.value is True and any(
                  isinstance(left, ast.Name) and left.id == 'gradle_attempted' for left in node.targets)]
    if not starts:
        raise RuntimeError('Actual build marker missing')
    exec(compile(ast.Module(body=block[starts[-1]:stop + 1], type_ignores=[]),
                 '<isolated-xcode-finally>', 'exec'), namespace)


class NativeCommandFailureControls(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-command-control-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def fixture(self, name, *, primary=None, secondary=None, failure_at='refresh', cleanup_at='stop',
                exit_code=-15, tag=None):
        destination = self.root / (tag or name)
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
                               owner=owner, process=process, receiver=receiver, namespace=namespace)

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

    def test_actual_xcode_watchdog_and_interrupt_keep_primary_through_immediate_stop_and_preservation(self):
        for kind in ('watchdog', 'keyboard', 'system-exit'):
            for secondary in ('none', 'stop', 'preservation', 'both'):
                with self.subTest(primary=kind, secondary=secondary):
                    error = {'watchdog': None, 'keyboard': KeyboardInterrupt('synthetic build interruption'),
                             'system-exit': SystemExit('synthetic build cancellation')}[kind]
                    fixture = self.fixture('companion', primary=error, tag=kind + '-' + secondary)
                    namespace, trace = fixture.namespace, []
                    if kind == 'watchdog':
                        # Advance the actual command loop's injected clock; no
                        # 2700/5400-second wait and no Popen implementation runs.
                        namespace['time'].monotonic.side_effect = [0, 5401]
                    stop_error = RuntimeError('synthetic immediate stop secondary')
                    retention_error = OSError('synthetic raw preservation secondary')

                    def stop(label):
                        trace.append(label)
                        if secondary in {'stop', 'both'}:
                            raise stop_error

                    def preserve(*args, **kwargs):
                        trace.append('raw-preservation')
                        if secondary in {'preservation', 'both'}:
                            raise retention_error
                        return True

                    namespace.update(project=self.root / 'copy/iosApp/iosApp.xcodeproj/project.pbxproj',
                        temp=self.root / 'owned-copy', results=self.root / 'owned-copy/Results.xcresult',
                        uuid='11111111-2222-3333-4444-555555555555', signing_arguments=[],
                        toolchain_name='qualified-xcode-26.3',
                        toolchains=SimpleNamespace(QUALIFIED='qualified-xcode-26.3', LOCAL='local-xcode-26.5'),
                        lifecycle=SimpleNamespace(mark_build_attempted=Mock(side_effect=lambda: trace.append('mark'))),
                        stop_gradle=Mock(side_effect=stop), preserve_postbuild_raw=Mock(side_effect=preserve))
                    namespace['env']['SDK_NAME'] = 'iphonesimulator26.2'
                    helpers = [node for node in ast.parse(RUNNERS['companion'].read_text()).body
                               if isinstance(node, ast.FunctionDef) and
                               node.name in {'xcode_time_budget', 'finish_xcode_attempt'}]
                    self.assertEqual(2, len(helpers))
                    exec(compile(ast.Module(body=helpers, type_ignores=[]), '<actual-xcode-helpers>', 'exec'), namespace)
                    namespace['xcode_budget'] = namespace['xcode_time_budget'](namespace['toolchain_name'])
                    expected = subprocess.TimeoutExpired if kind == 'watchdog' else type(error)
                    with self.assertRaises(expected) as caught:
                        isolated_xcode_boundary(RUNNERS['companion'], namespace)
                    if error is not None:
                        self.assertIs(error, caught.exception)
                    else:
                        self.assertEqual(5400, caught.exception.timeout)
                    entry = fixture.receipt['commands'][0]
                    self.assertEqual(1, len(fixture.receipt['commands']))
                    self.assertEqual('xcodebuild.log', entry['log'])
                    self.assertEqual(dict(type=expected.__name__, message=str(caught.exception)[:800]), entry['primary_error'])
                    self.assertEqual(-15, entry['exit_code'])
                    self.assertEqual(5400, entry['timeout_seconds'])
                    self.assertNotIn('xcodebuild_exit_code', fixture.receipt)
                    self.assert_command_only_stop(fixture)
                    namespace['stop_gradle'].assert_called_once_with('stop-xcode-immediate')
                    namespace['preserve_postbuild_raw'].assert_called_once()
                    self.assertEqual(['mark', 'stop-xcode-immediate', 'raw-preservation'], trace)
                    expected_secondary = []
                    if secondary in {'stop', 'both'}:
                        expected_secondary.append(dict(stage='stop-xcode-immediate', type='RuntimeError',
                                                       message='postbuild-finalization-failed'))
                    if secondary in {'preservation', 'both'}:
                        expected_secondary.append(dict(stage='preserve-raw-postbuild', type='OSError',
                                                       message='postbuild-finalization-failed'))
                    self.assertEqual(expected_secondary, fixture.receipt.get('postbuild_secondary_errors', []))
                    for redacted in (str(stop_error), str(retention_error)):
                        self.assertNotIn(redacted, json.dumps(fixture.receipt))

    def test_companion_failed_receipt_save_cannot_replace_an_existing_command_primary(self):
        for primary in (subprocess.TimeoutExpired(['owned-public-command'], 120),
                        KeyboardInterrupt('original interrupt'), RuntimeError('original failure')):
            with self.subTest(primary=type(primary).__name__):
                fixture = self.fixture('companion', primary=primary, tag=type(primary).__name__)
                persistence = OSError('PRIVATE-PERSISTENCE-DIAGNOSTIC')
                fixture.namespace['save'] = Mock(side_effect=[None, persistence])
                with self.assertRaises(type(primary)) as caught:
                    fixture.invoke()
                self.assertIs(primary, caught.exception)
                entry = fixture.receipt['commands'][0]
                self.assertEqual(dict(type=type(primary).__name__, message=str(primary)[:800]), entry['primary_error'])
                self.assertEqual(dict(type='OSError', message='command-receipt-persistence-failed'),
                                 entry['command_persistence_error'])
                self.assertNotIn(str(persistence), json.dumps(fixture.receipt))
                self.assert_command_only_stop(fixture)

    def test_companion_receipt_save_failure_without_command_primary_is_not_swallowed(self):
        fixture = self.fixture('companion', exit_code=0)
        fixture.child.poll.side_effect = None
        fixture.child.poll.return_value = 0
        persistence = OSError('synthetic successful-command receipt save failed')
        fixture.namespace['save'] = Mock(side_effect=[None, persistence])
        with self.assertRaises(OSError) as caught:
            fixture.invoke()
        self.assertIs(persistence, caught.exception)
        self.assertNotIn('command_persistence_error', fixture.receipt['commands'][0])
        fixture.owner.stop.assert_not_called()


class NativeSimulatorMetadataFailureControls(unittest.TestCase):
    DEVICE = '11111111-2222-3333-4444-555555555555'
    RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
    NAME = 'parlor-audit-owned-metadata-control'
    ARGS = ['xcrun', 'simctl', 'list', 'devices', '-j']

    def fixture(self, *, primary=None, secondary=None, failure_at='communicate', cleanup_at='stop',
                observed_exit=0, stopped_exit=-15, stdout=None, stderr=b''):
        receipt, saved, at_stop = {'commands': [], 'owned_device_name': self.NAME}, [], []
        child = Mock(returncode=observed_exit)
        child.poll.return_value = stopped_exit
        device = dict(udid=self.DEVICE, name=self.NAME, state='Shutdown')
        if stdout is None:
            stdout = json.dumps(dict(devices={self.RUNTIME: [device, dict(
                udid='PRIVATE-UNRELATED-UUID', name='PRIVATE-UNRELATED-NAME', state='Booted',
                dataPath='PRIVATE-UNRELATED-CONTAINER')]})).encode()
        child.communicate.return_value = (stdout, stderr)
        owner = Mock()

        def stop(_selector):
            at_stop.append(copy.deepcopy(receipt['commands'][-1]))
            if secondary is not None and cleanup_at == 'stop':
                raise secondary

        owner.stop.side_effect = stop
        if secondary is not None and cleanup_at == 'poll':
            child.poll.side_effect = secondary
        process = SimpleNamespace(Popen=Mock(return_value=child), PIPE=-1)
        write = Mock()
        decoder = SimpleNamespace(loads=Mock(side_effect=json.loads))
        if primary is not None:
            if failure_at == 'popen':
                process.Popen.side_effect = primary
            elif failure_at == 'register':
                owner.register.side_effect = primary
            elif failure_at == 'parse':
                decoder.loads.side_effect = primary
            elif failure_at == 'write':
                write.side_effect = primary
            else:
                child.communicate.side_effect = primary
        environment = {'SECRET_ENV': 'PRIVATE-UNRETAINED-ENVIRONMENT'}
        destination = Path('/synthetic-owned-evidence')
        namespace = dict(ROOT=ROOT, subprocess=process, re=re, json=decoder, env=environment,
            uuid=self.DEVICE, toolchain={'runtime': self.RUNTIME}, receipt=receipt, dest=destination, lifecycle=None,
            owner=owner, now=lambda: 'synthetic-time', defer_parent_signals=nullcontext,
            save=lambda: saved.append(copy.deepcopy(receipt)), write_json=write)
        functions = [node for node in ast.walk(ast.parse(RUNNERS['companion'].read_text()))
                     if isinstance(node, ast.FunctionDef) and node.name == 'own_simulator_metadata']
        if len(functions) != 1:
            raise RuntimeError('Ambiguous simulator metadata extraction')
        exec(compile(ast.Module(body=functions, type_ignores=[]), '<isolated-owned-metadata>', 'exec'), namespace)
        return SimpleNamespace(invoke=lambda: namespace['own_simulator_metadata']('owned-metadata'),
            receipt=receipt, saved=saved, at_stop=at_stop, child=child, owner=owner, process=process,
            environment=environment, destination=destination, write=write, decoder=decoder, device=device)

    def assert_primary(self, fixture, error, *, stage=None, secondary=None):
        with self.assertRaises(type(error)) as caught:
            fixture.invoke()
        self.assertIs(caught.exception, error)
        self.assertEqual(len(fixture.receipt['commands']), 1)
        entry = fixture.receipt['commands'][0]
        expected = dict(type=type(error).__name__, message=str(error)[:800])
        self.assertIs(entry['interrupted_or_failed'], True)
        self.assertEqual(entry['primary_error'], expected)
        self.assertEqual(entry['finished_at'], 'synthetic-time')
        self.assertEqual(fixture.saved[-1]['commands'][0], entry)
        for before_stop in fixture.at_stop:
            self.assertIs(before_stop['interrupted_or_failed'], True)
            self.assertEqual(before_stop['primary_error'], expected)
            self.assertNotIn('command_cleanup_error', before_stop)
        if secondary is None:
            self.assertNotIn('command_cleanup_error', entry)
        else:
            self.assertEqual(entry['command_cleanup_error'], dict(stage=stage,
                type=type(secondary).__name__, message=str(secondary)[:800]))
        return entry

    def assert_command_only_stop(self, fixture):
        fixture.owner.stop.assert_called_once()
        self.assertEqual(len(fixture.at_stop), 1)
        select = fixture.owner.stop.call_args.args[0]
        for role in ('command', 'gradle', 'app', 'xcode', 'foreign', ''):
            self.assertEqual(select({'role': role}), role == 'command')

    def assert_no_raw_metadata(self, fixture):
        retained = json.dumps(fixture.saved)
        for private in ('PRIVATE-UNRELATED-UUID', 'PRIVATE-UNRELATED-NAME', 'PRIVATE-UNRELATED-CONTAINER',
                        'PRIVATE-RAW-STDOUT', 'PRIVATE-RAW-STDERR', 'SECRET_ENV',
                        'PRIVATE-UNRETAINED-ENVIRONMENT'):
            self.assertNotIn(private, retained)

    def test_success_keeps_existing_closed_metadata_and_command_contract(self):
        fixture = self.fixture()
        expected = dict(fixture.device, runtime=self.RUNTIME)
        self.assertEqual(fixture.invoke(), expected)
        fixture.write.assert_called_once_with(fixture.destination / 'owned-metadata.json', dict(matches=[expected]))
        fixture.process.Popen.assert_called_once_with(self.ARGS, cwd=ROOT, env=fixture.environment,
            stdout=-1, stderr=-1, start_new_session=True)
        fixture.owner.register.assert_called_once_with(fixture.child, 'command')
        fixture.child.communicate.assert_called_once_with(timeout=45)
        fixture.owner.stop.assert_not_called()
        fixture.child.poll.assert_not_called()
        self.assertEqual(set(fixture.receipt['commands'][0]),
                         {'command', 'started_at', 'log', 'retention', 'exit_code', 'finished_at'})
        self.assertEqual(fixture.receipt['commands'][0]['exit_code'], 0)
        self.assertEqual(fixture.saved[-1], fixture.receipt)
        self.assert_no_raw_metadata(fixture)

    def test_no_owned_match_is_still_absent_not_a_failure(self):
        fixture = self.fixture(stdout=json.dumps(dict(devices={self.RUNTIME: [dict(
            udid='PRIVATE-UNRELATED-UUID', name='PRIVATE-UNRELATED-NAME', state='Booted')]})).encode())
        self.assertIsNone(fixture.invoke())
        fixture.write.assert_called_once_with(fixture.destination / 'owned-metadata.json', dict(matches=[]))
        fixture.owner.stop.assert_not_called()
        fixture.child.poll.assert_not_called()
        self.assert_no_raw_metadata(fixture)

    def test_popen_failure_never_stops_or_invents_an_exit(self):
        primary = OSError('owned metadata spawn failed')
        fixture = self.fixture(primary=primary, failure_at='popen')
        entry = self.assert_primary(fixture, primary)
        self.assertNotIn('exit_code', entry)
        fixture.owner.stop.assert_not_called()
        fixture.owner.register.assert_not_called()
        fixture.child.communicate.assert_not_called()
        fixture.child.poll.assert_not_called()
        fixture.write.assert_not_called()

    def test_registration_primary_is_recorded_before_fallible_stop(self):
        primary = subprocess.TimeoutExpired(['ps', '-axo', 'pid=,ppid=,pgid=,lstart=,command='], 15)
        secondary = subprocess.TimeoutExpired(['/usr/sbin/lsof', '-nP', '-t', '+w', '+D', '/owned-copy'], 30)
        fixture = self.fixture(primary=primary, secondary=secondary, failure_at='register')
        entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
        self.assertNotIn('exit_code', entry)
        fixture.child.communicate.assert_not_called()
        fixture.child.poll.assert_not_called()
        self.assert_command_only_stop(fixture)

    def test_communicate_timeout_and_stop_error_do_not_retain_raw_output(self):
        primary = subprocess.TimeoutExpired(self.ARGS, 45, output=b'PRIVATE-RAW-STDOUT',
                                            stderr=b'PRIVATE-RAW-STDERR')
        secondary = subprocess.TimeoutExpired(['owned-stop-command'], 30, output=b'PRIVATE-RAW-STDOUT',
                                              stderr=b'PRIVATE-RAW-STDERR')
        fixture = self.fixture(primary=primary, secondary=secondary)
        entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
        self.assertNotIn('exit_code', entry)
        fixture.child.communicate.assert_called_once_with(timeout=45)
        fixture.child.poll.assert_not_called()
        self.assert_command_only_stop(fixture)
        self.assert_no_raw_metadata(fixture)

    def test_successful_stop_records_actual_negative_exit_without_swallowing_primary(self):
        primary = RuntimeError('metadata communicate failed')
        fixture = self.fixture(primary=primary)
        entry = self.assert_primary(fixture, primary)
        self.assertEqual(entry['exit_code'], -15)
        fixture.child.poll.assert_called_once_with()
        self.assert_command_only_stop(fixture)

    def test_unobserved_exit_after_stop_is_not_invented_as_zero(self):
        primary = RuntimeError('metadata communicate failed')
        fixture = self.fixture(primary=primary, stopped_exit=None)
        entry = self.assert_primary(fixture, primary)
        self.assertIsNone(entry['exit_code'])
        fixture.child.poll.assert_called_once_with()
        self.assert_command_only_stop(fixture)

    def test_post_stop_poll_error_is_secondary_and_keeps_same_primary(self):
        primary, secondary = RuntimeError('metadata communicate failed'), RuntimeError('exit read failed')
        fixture = self.fixture(primary=primary, secondary=secondary, cleanup_at='poll')
        entry = self.assert_primary(fixture, primary, stage='read-owned-command-exit', secondary=secondary)
        self.assertNotIn('exit_code', entry)
        fixture.child.poll.assert_called_once_with()
        self.assert_command_only_stop(fixture)

    def test_base_exception_primaries_and_secondaries_are_not_swallowed_or_substituted(self):
        for primary, secondary, cleanup_at in (
            (KeyboardInterrupt('metadata interrupt'), SystemExit('stop interrupt'), 'stop'),
            (SystemExit('metadata exit'), KeyboardInterrupt('poll interrupt'), 'poll'),
        ):
            with self.subTest(cleanup_at=cleanup_at):
                fixture = self.fixture(primary=primary, secondary=secondary, cleanup_at=cleanup_at)
                stage = 'stop-owned-command-workers' if cleanup_at == 'stop' else 'read-owned-command-exit'
                self.assert_primary(fixture, primary, stage=stage, secondary=secondary)
                self.assert_command_only_stop(fixture)

    def test_primary_and_secondary_messages_remain_bounded(self):
        primary, secondary = RuntimeError('p' * 4096), RuntimeError('s' * 4096)
        fixture = self.fixture(primary=primary, secondary=secondary)
        entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
        self.assertEqual(len(entry['primary_error']['message']), 800)
        self.assertEqual(len(entry['command_cleanup_error']['message']), 800)

    def test_raw_command_rejections_keep_observed_exit_despite_stop_failure(self):
        secondary = RuntimeError('stop failed')
        for options in (dict(observed_exit=23), dict(stderr=b'PRIVATE-RAW-STDERR'),
                        dict(stdout=b'PRIVATE-RAW-STDOUT' + b'x' * (2 * 1024 * 1024))):
            with self.subTest(options=list(options)):
                fixture = self.fixture(secondary=secondary, **options)
                with self.assertRaisesRegex(RuntimeError, 'raw unrelated data not retained') as caught:
                    fixture.invoke()
                entry = fixture.receipt['commands'][0]
                self.assertEqual(entry['primary_error'], dict(type='RuntimeError', message=str(caught.exception)))
                self.assertEqual(entry['exit_code'], options.get('observed_exit', 0))
                self.assertEqual(entry['command_cleanup_error'], dict(stage='stop-owned-command-workers',
                    type='RuntimeError', message='stop failed'))
                self.assertEqual(fixture.saved[-1]['commands'][0], entry)
                fixture.child.poll.assert_not_called()
                fixture.decoder.loads.assert_not_called()
                fixture.write.assert_not_called()
                self.assert_command_only_stop(fixture)
                self.assert_no_raw_metadata(fixture)

    def test_parser_primary_is_not_replaced_and_observed_zero_remains_command_only(self):
        primary = json.JSONDecodeError('malformed metadata', 'PRIVATE-RAW-STDOUT', 0)
        secondary = RuntimeError('stop failed')
        fixture = self.fixture(primary=primary, secondary=secondary, failure_at='parse')
        entry = self.assert_primary(fixture, primary, stage='stop-owned-command-workers', secondary=secondary)
        self.assertEqual(entry['exit_code'], 0)
        fixture.child.poll.assert_not_called()
        fixture.write.assert_not_called()
        self.assert_command_only_stop(fixture)
        self.assert_no_raw_metadata(fixture)

    def test_observed_exit_is_not_repolled_or_overwritten_after_successful_stop(self):
        primary = OSError('owned evidence write failed')
        fixture = self.fixture(primary=primary, failure_at='write', stopped_exit=None)
        entry = self.assert_primary(fixture, primary)
        self.assertEqual(entry['exit_code'], 0)
        fixture.child.poll.assert_not_called()
        self.assert_command_only_stop(fixture)

    def test_exact_metadata_identity_guards_still_fail_closed_without_raw_retention(self):
        device = dict(udid=self.DEVICE, name=self.NAME, state='Shutdown')
        wrong_uuid = dict(device, udid='22222222-2222-3333-4444-555555555555')
        secondary = RuntimeError('stop failed')
        for runtime, rows, message in (
            (self.RUNTIME, [device, device], 'Ambiguous synthetic simulator identity'),
            (self.RUNTIME, [dict(device, udid='PRIVATE-UNRELATED-UUID')], 'Unexpected synthetic simulator UUID'),
            ('com.apple.CoreSimulator.SimRuntime.iOS-26-5', [device], 'runtime differs'),
            (self.RUNTIME, [wrong_uuid], 'Synthetic simulator identity changed'),
        ):
            with self.subTest(message=message):
                fixture = self.fixture(stdout=json.dumps(dict(devices={runtime: rows})).encode(), secondary=secondary)
                with self.assertRaisesRegex(RuntimeError, message) as caught:
                    fixture.invoke()
                entry = fixture.receipt['commands'][0]
                self.assertEqual(entry['primary_error'], dict(type='RuntimeError', message=str(caught.exception)))
                self.assertEqual(entry['exit_code'], 0)
                self.assertEqual(entry['command_cleanup_error'], dict(stage='stop-owned-command-workers',
                    type='RuntimeError', message='stop failed'))
                self.assertEqual(fixture.saved[-1]['commands'][0], entry)
                fixture.child.poll.assert_not_called()
                fixture.write.assert_not_called()
                self.assert_command_only_stop(fixture)
                self.assert_no_raw_metadata(fixture)


if __name__ == '__main__':
    unittest.main()
