"""Adversarial helper controls, not Apple/native runtime or readiness evidence.

All resources are fresh test-owned temporary paths. Popen is denied by default;
synthetic controls use in-memory streams/handles. Exactly five harness subprocess
controls replace ONLY the Popen factory, validate the original closed request,
and launch ``sys.executable -B -c`` stand-ins. They use real draining/journaling
and exact-handle retirement, never simctl, a numeric-PID/group signal, adoption,
an application, or a build. The coordinator alone executes these controls.
"""
from contextlib import contextmanager, ExitStack
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import simulator_lifecycle as lifecycle


REAL_POPEN = subprocess.Popen
REAL_FSYNC = os.fsync
REAL_WRITE = os.write
OWNED = '11111111-2222-3333-4444-555555555555'
FOREIGN = 'AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE'
OTHER = '99999999-2222-3333-4444-555555555555'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
DEVICE_TYPE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
PRIVATE = 'SYNTHETIC-RAW-DO-NOT-RETAIN'


def device(name='unrelated-synthetic-device', udid=FOREIGN, state='Shutdown', **changes):
    return dict(udid=udid, name=name, state=state, isAvailable=True,
                deviceTypeIdentifier=DEVICE_TYPE, **changes)


def inventory_bytes(*rows, runtime=RUNTIME):
    return lifecycle.encoded({'devices': {runtime: list(rows)}})


def response(operation, stdout=b'', **options):
    return dict(operation=operation, stdout=stdout, **options)


class SyntheticPipe:
    """No OS descriptor is allocated; only the injected read adapter sees fd."""
    def __init__(self, fd, raw, close_error=None):
        self.fd, self.raw, self.closed = fd, bytearray(raw), False
        self.close_error = close_error

    def fileno(self):
        return self.fd

    def close(self):
        self.closed = True
        if self.close_error is not None:
            raise self.close_error


class SyntheticChild:
    def __init__(self, ordinal, step):
        self.pid = step.get('pid', 900000 + ordinal)  # A token, never signalled.
        self.stdout = SyntheticPipe(ordinal * 2, step.get('stdout', b''), step.get('pipe_error'))
        self.stderr = SyntheticPipe(ordinal * 2 + 1, step.get('stderr', b''))
        self.wait_results = list(step.get('wait_results', []))
        self.default_wait = step.get('exit_code', 0)
        self.action_errors = step.get('action_errors', {})
        self.actions = []

    def wait(self, timeout):
        self.actions.append(('wait', timeout))
        value = self.wait_results.pop(0) if self.wait_results else self.default_wait
        if isinstance(value, BaseException):
            raise value
        return value

    def terminate(self):
        self.actions.append(('terminate',))
        if 'terminate' in self.action_errors:
            raise self.action_errors['terminate']

    def kill(self):
        self.actions.append(('kill',))
        if 'kill' in self.action_errors:
            raise self.action_errors['kill']


class SyntheticSelector:
    def __init__(self, step):
        self.step, self.registered = step, {}

    def register(self, pipe, events, key):
        self.registered[pipe] = SimpleNamespace(fileobj=pipe, data=key)

    def get_map(self):
        return self.registered

    def select(self, timeout):
        if 'select_error' in self.step:
            raise self.step['select_error']
        return [(key, lifecycle.selectors.EVENT_READ) for key in self.registered.values()]

    def unregister(self, pipe):
        del self.registered[pipe]

    def close(self):
        if 'selector_close_error' in self.step:
            raise self.step['selector_close_error']


class LifecycleFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='parlor-lifecycle-unit-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.serial = 0
        self.denied_popen = Mock(side_effect=AssertionError('Unapproved subprocess request'))
        guard = patch.object(lifecycle.subprocess, 'Popen', self.denied_popen)
        guard.start()
        self.addCleanup(guard.stop)
        self.fresh()

    def fresh(self):
        self.serial += 1
        base = self.base / str(self.serial)
        self.root = base / 'repository'
        self.temporary = base / 'parlor-audit-ios-readiness-98-unit'
        self.evidence = base / 'evidence/ios-readiness-98'
        self.home = base / 'home'
        self.device_set = self.home / 'Library/Developer/CoreSimulator/Devices'
        for path in (self.root, self.temporary, self.evidence, self.device_set):
            path.mkdir(parents=True)
        self.temporary.chmod(0o700)
        self.name = 'Parlor-Audit-' + self.temporary.name
        self.device_path = self.device_set / OWNED
        self.source, self.approved = {'tree': 'synthetic-source'}, 'a' * 64
        self.receipt, self.saved = {'commands': []}, []
        self.save = Mock(side_effect=lambda: self.saved.append(deepcopy(self.receipt)))
        self.kwargs = dict(root=self.root, temporary=self.temporary, evidence=self.evidence,
                           cycle='ios-readiness-98', name=self.name,
                           toolchain='qualified-xcode-26.3', source=self.source, approved=self.approved,
                           receipt=self.receipt, save=self.save,
                           current_bindings=lambda: (self.source, self.approved),
                           environment=dict(HOME=str(self.home), DEVELOPER_DIR=DEVELOPER,
                                            DYLD_INSERT_LIBRARIES=PRIVATE, TOKEN=PRIVATE, PATH=PRIVATE))
        with patch.object(Path, 'home', return_value=self.home):
            self.life = lifecycle.OwnedSimulatorLifecycle(**self.kwargs)
        return self.life

    def expected_arguments(self, operation):
        tail = {'inventory': ['list', 'devices', '-j'],
                'create': ['create', self.name, DEVICE_TYPE, RUNTIME],
                'boot': ['boot', OWNED], 'bootstatus': ['bootstatus', OWNED, '-b'],
                'shutdown': ['shutdown', OWNED], 'delete': ['delete', OWNED]}[operation]
        return ['/usr/bin/xcrun', 'simctl', *tail]

    def assert_launch_request(self, operation, arguments, options):
        self.assertEqual(self.expected_arguments(operation), arguments)
        self.assertEqual(dict(cwd=self.root, env=dict(HOME=str(self.home),
            PATH='/usr/bin:/bin:/usr/sbin:/sbin', LANG='en_US.UTF-8', LC_ALL='C', DEVELOPER_DIR=DEVELOPER),
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            start_new_session=True), options)

    @contextmanager
    def scripted(self, steps):
        """A finite response queue at the actual Popen seam, not a simulator."""
        queue, calls, children, pipes = list(steps), [], [], {}
        state = {'step': {}, 'pending': None}

        @contextmanager
        def no_signals():
            pending = []
            state['pending'] = pending
            try:
                yield pending
            finally:
                state['pending'] = None

        def launch(arguments, **options):
            self.assertTrue(queue, 'Unexpected extra lifecycle subprocess')
            step = queue.pop(0)
            operation = step['operation']
            self.assert_launch_request(operation, arguments, options)
            calls.append(operation)
            state['step'] = step
            if 'before' in step:
                step['before']()
            if 'launch_error' in step:
                raise step['launch_error']
            child = SyntheticChild(len(children) + 1, step)
            children.append(child)
            pipes.update({pipe.fd: pipe for pipe in (child.stdout, child.stderr)})
            if step.get('pending_cancellation'):
                state['pending'].append(signal.SIGTERM)  # Data only; no signal delivery.
            return child

        def selector():
            if 'selector_error' in state['step']:
                raise state['step']['selector_error']
            return SyntheticSelector(state['step'])

        def read(fd, count):
            self.assertEqual(65536, count)
            if 'read_error' in state['step']:
                raise state['step']['read_error']
            pipe = pipes[fd]
            raw = bytes(pipe.raw[:count])
            del pipe.raw[:count]
            return raw

        def nonblocking(fd, value):
            self.assertIn(fd, pipes)
            self.assertIs(value, False)

        with ExitStack() as stack:
            for target, attribute, replacement in (
                (lifecycle, 'defer_signals', no_signals),
                (lifecycle.subprocess, 'Popen', launch),
                (lifecycle.selectors, 'DefaultSelector', selector),
                (lifecycle.os, 'read', read), (lifecycle.os, 'set_blocking', nonblocking),
            ):
                stack.enter_context(patch.object(target, attribute, replacement))
            yield SimpleNamespace(calls=calls, children=children, remaining=queue)

    def create_arguments(self):
        return ['xcrun', 'simctl', 'create', self.name, DEVICE_TYPE, RUNTIME]

    def owned(self, state='Shutdown', **changes):
        return {**device(name=self.name, udid=OWNED, state=state), **changes}

    def create(self, state='Shutdown'):
        with self.scripted([
            response('inventory', inventory_bytes(device(name=PRIVATE))),
            response('create', (OWNED.lower() + '\n').encode(), before=self.device_path.mkdir),
            response('inventory', inventory_bytes(self.owned(state), device(name=PRIVATE))),
        ]) as script:
            self.assertEqual(0, self.life.command(self.create_arguments(), 'create.log'))
            self.assertFalse(script.remaining)
        return self.life

    def capture(self, *, cleanup=False):
        return self.life._capture('inventory', self.expected_arguments('inventory'), 45, cleanup=cleanup)

    def owner(self, *, live=None, unknown=None, errors=None, handles=None):
        events = []
        owner = SimpleNamespace(unknown_holders=[] if unknown is None else unknown,
                                secondary_errors=[] if errors is None else errors,
                                handles=[] if handles is None else handles, events=events)
        owner.stop = Mock(side_effect=lambda: events.append('stop'))
        owner.refresh = Mock(side_effect=lambda **kwargs: events.append(('refresh', kwargs)) or
                             ([] if live is None else live))
        return owner

    def assert_retained(self):
        self.assertTrue(self.device_path.exists() or self.device_path.is_symlink())
        self.assertFalse(self.life.absent)
        self.assertNotEqual('PASS', self.life.summary()['cleanup_status'])

    def assert_final_failure(self):
        with self.assertRaises((RuntimeError, OSError)):
            self.life.finish()
        self.assertEqual('FAIL', self.life.summary()['cleanup_status'])


class SelectionAndInventoryControls(unittest.TestCase):
    def test_default_legacy_and_only_exact_explicit_qualified_selection(self):
        keep = ['ios-readiness-98', '--toolchain=qualified-xcode-26.3', '--image-observer=libproc']
        for profile in ('qualified-xcode-26.3', 'local-xcode-26.5'):
            self.assertEqual(('legacy-apphost', keep), lifecycle.selected_lifecycle(keep, profile))
        self.assertEqual(('direct-owned-v1', keep), lifecycle.selected_lifecycle(
            [*keep, '--simulator-lifecycle=direct-owned-v1'], 'qualified-xcode-26.3'))
        lifecycle.validate_selection('legacy-apphost', 'local-xcode-26.5')
        lifecycle.validate_selection('direct-owned-v1', 'qualified-xcode-26.3')

    def test_selectors_reject_duplicates_malformed_unknown_and_local_direct(self):
        flag = '--simulator-lifecycle=direct-owned-v1'
        for flags in ([flag, flag], ['--simulator-lifecycle'], ['--simulator-lifecycle', 'direct-owned-v1'],
                      ['--simulator-lifecycle=legacy-apphost'], [flag + ' '], [flag + '=extra'],
                      ['--simulator-lifecycle-unknown=direct-owned-v1'], ['--simulator-lifecycle=all']):
            with self.subTest(flags=flags), self.assertRaises(RuntimeError):
                lifecycle.selected_lifecycle(flags, 'qualified-xcode-26.3')
        for mode, profile in (('direct-owned-v1', 'local-xcode-26.5'), ('direct-owned-v1', ''),
                              ('direct-owned-v1', 'qualified-xcode-26.3 '), ('direct', 'qualified-xcode-26.3')):
            with self.subTest(mode=mode, profile=profile), self.assertRaises(RuntimeError):
                lifecycle.validate_selection(mode, profile)
        with self.assertRaisesRegex(RuntimeError, 'qualified-profile-required'):
            lifecycle.selected_lifecycle([flag], 'local-xcode-26.5')

    def test_inventory_normalizes_uuid_only_and_rejects_ambiguous_or_invalid_schema(self):
        value = lifecycle.inventory(inventory_bytes(device(udid=FOREIGN.lower())))
        self.assertEqual({FOREIGN}, set(value))
        self.assertEqual(RUNTIME, value[FOREIGN]['runtime'])
        malformed = [b'', b'\xff', b'{', b'[]', b'{}', b'{"devices":[]}', b'{"devices":{}}',
                     b'{"devices":{},"devices":{}}', b'{"devices":{},"extra":true}',
                     inventory_bytes(device(), device(udid=FOREIGN.lower())),
                     inventory_bytes(device(), runtime='unqualified-runtime'),
                     inventory_bytes(*([device()] * 513))]
        for raw in malformed:
            with self.subTest(raw=raw[:100]), self.assertRaises(RuntimeError):
                lifecycle.inventory(raw)
        for key, replacement in (('udid', 'booted'), ('udid', OWNED + '\n'), ('udid', None),
            ('name', ''), ('name', 'x' * 257), ('name', 'bad\nname'), ('name', 'bad\x7fname'),
            ('state', 'Unknown'), ('state', None), ('isAvailable', 1), ('isAvailable', 'true'),
            ('deviceTypeIdentifier', 'iPhone'), ('deviceTypeIdentifier', None)):
            with self.subTest(field=key, replacement=replacement), self.assertRaises(RuntimeError):
                lifecycle.inventory(inventory_bytes({**device(), key: replacement}))
        duplicate_key = inventory_bytes(device()).replace(b'"state":"Shutdown"', b'"state":"Shutdown","state":"Booted"')
        with self.assertRaisesRegex(RuntimeError, 'inventory-duplicate-key'):
            lifecycle.inventory(duplicate_key)

    def test_inventory_has_independent_stream_runtime_and_total_device_bounds(self):
        for raw in ('{}', b'x' * (lifecycle.LIMIT + 1)):
            with self.subTest(type=type(raw).__name__), self.assertRaisesRegex(RuntimeError, 'inventory-bounds'):
                lifecycle.inventory(raw)
        many = {'devices': {RUNTIME + '-' + str(index): [] for index in range(65)}}
        with self.assertRaisesRegex(RuntimeError, 'inventory-shape'):
            lifecycle.inventory(lifecycle.encoded(many))
        groups = {}
        for index in range(2049):
            groups.setdefault(RUNTIME + '-' + str(index // 512), []).append(
                device(udid=f'{index:08X}-2222-3333-4444-555555555555'))
        with self.assertRaisesRegex(RuntimeError, 'inventory-duplicate-or-count'):
            lifecycle.inventory(lifecycle.encoded({'devices': groups}))

    def test_failure_diagnostics_are_closed_and_never_echo_exception_output(self):
        for error, code in ((RuntimeError('direct-simulator-diagnostic-stderr'), 'diagnostic-stderr'),
            (RuntimeError(PRIVATE), 'unexpected-exception'), (OSError(PRIVATE), 'unexpected-exception'),
            (subprocess.TimeoutExpired(PRIVATE, 45, output=PRIVATE, stderr=PRIVATE), 'command-timeout'),
            (KeyboardInterrupt(PRIVATE), 'cancelled'), (SystemExit(PRIVATE), 'cancelled')):
            with self.subTest(type=type(error).__name__):
                result = lifecycle.failure(error)
                self.assertEqual({'type': type(error).__name__, 'code': code}, result)
                self.assertNotIn(PRIVATE, json.dumps(result))


class ConstructionAndClosedCommandControls(LifecycleFixture):
    def test_constructor_is_inert_and_environment_is_a_closed_allowlist(self):
        self.denied_popen.assert_not_called()
        self.assertFalse(self.life.journal.exists())
        self.assertEqual([], list(self.evidence.iterdir()))
        self.save.assert_not_called()
        self.assertEqual({'HOME', 'PATH', 'LANG', 'LC_ALL', 'DEVELOPER_DIR'}, set(self.life.environment))
        self.assertNotIn(PRIVATE, json.dumps(self.life.environment))
        self.assertEqual((RUNTIME, DEVICE_TYPE, DEVELOPER),
                         (lifecycle.RUNTIME, lifecycle.DEVICE_TYPE, lifecycle.DEVELOPER))
        self.assertEqual('BLOCKED', self.life.summary()['cleanup_status'])

    def test_constructor_rejects_wrong_profile_name_paths_home_and_bindings(self):
        cases = [dict(toolchain='local-xcode-26.5'), dict(cycle='ios-readiness-9'),
                 dict(name=self.name + '\n'), dict(name='Parlor-Audit-arbitrary'),
                 dict(name=self.name.replace('-98-', '-97-')), dict(source=[]), dict(approved='A' * 64),
                 dict(environment=dict(HOME=str(self.home), DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer')),
                 dict(environment=dict(HOME=str(self.root), DEVELOPER_DIR=DEVELOPER)),
                 dict(evidence=self.evidence.parent), dict(root=Path('relative'))]
        for changes in cases:
            with self.subTest(changes=changes), patch.object(Path, 'home', return_value=self.home):
                with self.assertRaises((RuntimeError, FileNotFoundError)):
                    lifecycle.OwnedSimulatorLifecycle(**{**self.kwargs, **changes})
        self.temporary.chmod(0o755)
        with patch.object(Path, 'home', return_value=self.home), self.assertRaisesRegex(RuntimeError, 'directory-custody'):
            lifecycle.OwnedSimulatorLifecycle(**self.kwargs)
        self.denied_popen.assert_not_called()

    def test_no_generic_command_all_booted_name_or_optional_flag_route(self):
        self.life.uuid = OWNED  # Syntax guard only; no resource authority is constructed.
        for arguments, filename, timeout in (
            (['xcrun', 'simctl', 'list', 'devices', '-j'], 'inventory.log', 45),
            (['xcrun', 'simctl', 'delete', OWNED], 'delete.log', 120),
            (['xcrun', 'simctl', 'shutdown', OWNED], 'shutdown.log', 120),
            (['xcrun', 'simctl', 'spawn', OWNED, 'launchctl'], 'spawn.log', 120),
            (['xcrun', 'simctl', 'boot', 'all'], 'boot.log', 120),
            (['xcrun', 'simctl', 'boot', 'booted'], 'boot.log', 120),
            (['xcrun', 'simctl', 'boot', self.name], 'boot.log', 120),
            (['xcrun', 'simctl', 'boot', OTHER], 'boot.log', 120),
            (['xcrun', 'simctl', 'boot', OWNED, '--set', '/tmp/foreign'], 'boot.log', 120),
            (['/usr/bin/xcrun', 'simctl', 'boot', OWNED], 'boot.log', 120),
            (['xcrun', 'simctl', 'boot', OWNED], '../boot.log', 120),
            (['xcrun', 'simctl', 'boot', OWNED], 'boot.log', 121),
            (['xcrun', 'simctl', 'bootstatus', OWNED], 'bootstatus.log', 300),
            (['xcrun', 'simctl', 'bootstatus', OWNED, '-b'], 'bootstatus.log', 120),
        ):
            with self.subTest(arguments=arguments), self.assertRaises(RuntimeError):
                self.life.command(arguments, filename, timeout)
        self.life.uuid = None
        for position, replacement in ((3, self.name + '-other'), (4, DEVICE_TYPE + '-other'), (5, RUNTIME + '-other')):
            arguments = self.create_arguments()
            arguments[position] = replacement
            with self.subTest(arguments=arguments), self.assertRaises(RuntimeError):
                self.life.command(arguments, 'create.log')
        self.denied_popen.assert_not_called()
        self.assertFalse(self.life.journal.exists())

    def test_lowest_launch_seam_rechecks_argv_uuid_timeout_count_and_finished(self):
        for arguments, timeout in ((['/bin/sh', '-c', 'true'], 45),
                                   (self.expected_arguments('inventory') + ['--extra'], 45),
                                   (self.expected_arguments('inventory'), 44)):
            with self.subTest(arguments=arguments, timeout=timeout), self.assertRaises(RuntimeError):
                self.life._capture('inventory', arguments, timeout)
        for uuid in (None, 'all', 'booted', self.name, OWNED + '\n', OWNED + ';id'):
            self.life.uuid = uuid
            with self.subTest(uuid=uuid), self.assertRaises(RuntimeError):
                self.life._arguments('shutdown')
        with self.assertRaisesRegex(RuntimeError, 'create-without-intent'):
            self.life._arguments('create')
        for finished, rows in ((True, []), (False, [{}] * lifecycle.MAX_COMMANDS)):
            self.life.finished, self.life.rows = finished, rows
            with self.assertRaisesRegex(RuntimeError, 'command-count-or-finished'):
                self.capture()
        self.denied_popen.assert_not_called()


class DurableCreationAndRecoveryControls(LifecycleFixture):
    def test_readable_fsynced_intent_and_hashed_full_baseline_precede_create(self):
        checkpoints = []

        def fsync(fd):
            info = os.fstat(fd)
            checkpoints.append((stat.S_ISREG(info.st_mode), stat.S_ISDIR(info.st_mode)))
            REAL_FSYNC(fd)

        def before_create():
            self.life._check_journal()
            events = [json.loads(line) for line in self.life.journal.read_bytes().splitlines()]
            intent = next(event for event in events if event['event'] == 'pre-create-intent')
            self.assertEqual([lifecycle.sha(FOREIGN.encode())], intent['baseline_uuid_sha256'])
            self.assertEqual(self.name, intent['name'])
            self.assertTrue(self.life.creation_intent)
            self.assertFalse(self.life.creation_dispatched)
            self.assertIsNone(self.life.uuid)
            self.assertGreaterEqual(len(checkpoints), 12)
            self.assertEqual([(True, False), (False, True)], checkpoints[-2:])

        with patch.object(lifecycle.os, 'fsync', fsync), self.scripted([
            response('inventory', inventory_bytes(device(name=PRIVATE))),
            response('create', (OWNED.lower() + '\n').encode(), before=before_create),
            response('inventory', inventory_bytes(self.owned(), device(name=PRIVATE))),
        ]) as script:
            self.assertEqual(0, self.life.command(self.create_arguments(), 'create.log'))
            self.assertEqual(['inventory', 'create', 'inventory'], script.calls)
        self.assertEqual('EXITED0_AND_IDENTITY_VERIFIED', self.life.creation_outcome)
        self.assertEqual(OWNED + '\n', (self.evidence / 'create.log').read_text())
        self.assertNotIn(PRIVATE, self.life.journal.read_text())
        self.assertNotIn(FOREIGN, self.life.journal.read_text())
        self.assertEqual(0o600, stat.S_IMODE(self.life.journal.stat().st_mode))
        self.assertEqual(64, len(self.life.header['nonce']))
        self.assertEqual(dict(started=3, reaped=3, unreaped=0, status='PASS'), self.life.summary()['direct_children'])

    def test_preexisting_name_or_invalid_inventory_is_sticky_without_creation_or_adoption(self):
        for raw in (inventory_bytes(self.owned()), inventory_bytes(self.owned(name=self.name + '-clone')),
                    b'{', inventory_bytes({**device(), 'isAvailable': 'yes'})):
            self.fresh()
            with self.subTest(raw=raw[:100]), self.scripted([response('inventory', raw)]) as script:
                with self.assertRaises(RuntimeError):
                    self.life.command(self.create_arguments(), 'create.log')
                self.assertEqual(['inventory'], script.calls)
                self.assertTrue(self.life.failed)
                with self.assertRaises(RuntimeError):
                    self.life.command(self.create_arguments(), 'create.log')
                self.assertFalse(self.life.creation_intent)
                self.assertFalse(self.life.creation_dispatched)
                self.assertIsNone(self.life.uuid)

    def test_failed_intent_fsync_or_receipt_prevents_create_dispatch(self):
        for stage in ('allocation', 'pre-create-intent', 'intent-receipt'):
            self.fresh()

            def fsync(fd):
                if stage != 'intent-receipt' and stat.S_ISREG(os.fstat(fd).st_mode) and self.life.journal.exists():
                    events = [json.loads(line)['event'] for line in self.life.journal.read_bytes().splitlines()]
                    if stage in events:
                        raise OSError(PRIVATE)
                REAL_FSYNC(fd)

            def save():
                if stage == 'intent-receipt' and self.life.creation_intent:
                    raise OSError(PRIVATE)

            self.save.side_effect = save
            with self.subTest(stage=stage), patch.object(lifecycle.os, 'fsync', fsync), self.scripted([
                response('inventory', inventory_bytes()),
            ]) as script:
                with self.assertRaises(OSError):
                    self.life.command(self.create_arguments(), 'create.log')
                self.assertNotIn('create', script.calls)
                self.assertFalse(self.life.creation_dispatched)
                self.assertTrue(self.life.failed)
                self.assertTrue(self.life.evidence_failed)
                self.assertFalse((self.evidence / 'create.log').exists())

    def test_existing_journal_is_never_reopened_or_adopted(self):
        for kind in ('file', 'symlink', 'hardlink'):
            self.fresh()
            target = self.root / 'foreign-journal'
            target.write_bytes(b'foreign-evidence')
            if kind == 'file':
                self.life.journal.write_bytes(b'preexisting')
            elif kind == 'symlink':
                self.life.journal.symlink_to(target)
            else:
                os.link(target, self.life.journal)
            with self.subTest(kind=kind), self.assertRaises(OSError):
                self.life.command(self.create_arguments(), 'create.log')
            self.assertFalse(self.life.creation_dispatched)
            self.assertEqual(b'foreign-evidence', target.read_bytes())
        self.denied_popen.assert_not_called()

    def test_registered_interrupted_create_recovers_cleanup_only_without_a_success_receipt(self):
        for interruption in ('timeout', 'pending-cancellation', 'bad-stdout', 'diagnostic-stderr'):
            self.fresh()
            options = dict(before=self.device_path.mkdir)
            raw = (OWNED + '\n').encode()
            if interruption == 'timeout':
                options['wait_results'] = [subprocess.TimeoutExpired('synthetic-create', 120, output=PRIVATE)]
            elif interruption == 'pending-cancellation':
                options['pending_cancellation'] = True
            elif interruption == 'bad-stdout':
                raw = b'not-a-uuid\n'
            else:
                options['stderr'] = PRIVATE.encode()
            with self.subTest(interruption=interruption), self.scripted([
                response('inventory', inventory_bytes()), response('create', raw, **options),
                response('inventory', inventory_bytes(self.owned())),
            ]) as script:
                with self.assertRaises((RuntimeError, subprocess.TimeoutExpired, KeyboardInterrupt)):
                    self.life.command(self.create_arguments(), 'create.log')
                before = deepcopy(self.life.errors)
                self.assertTrue(self.life.creation_dispatched)
                self.assertEqual(OWNED, self.life.recover()['udid'])
                self.assertEqual(before, self.life.errors)
                self.assertEqual(['inventory', 'create', 'inventory'], script.calls)
                self.assertEqual(OWNED, self.receipt['owned_uuid'])
                self.assertTrue(self.life.failed)
                self.assertEqual('INTERRUPTED_OR_FAILED', self.life.creation_outcome)
                self.assertFalse((self.evidence / 'create.log').exists())
                self.assertNotIn('create_exit_code', self.receipt)
                self.assertNotIn(PRIVATE, json.dumps(self.receipt))
                create_row = next(row for row in self.life.rows if row['operation'] == 'create')
                self.assertEqual('EXITED0' if interruption == 'bad-stdout' else 'FAILED', create_row['status'])
                with self.assertRaises(RuntimeError):
                    self.life.command(['xcrun', 'simctl', 'boot', OWNED], 'boot.log')

    def test_interrupted_recovery_missing_cloned_or_wrong_identity_never_adopts_or_authorizes_delete(self):
        cases = [('missing', lambda: inventory_bytes(), 'unresolved-create-no-exact-resource'),
                 ('clone', lambda: inventory_bytes(self.owned(), self.owned(udid=OTHER)), 'ambiguous-or-cloned-name'),
                 ('name-suffix', lambda: inventory_bytes(self.owned(name=self.name + '-clone')), 'ambiguous-or-cloned-name'),
                 ('runtime', lambda: inventory_bytes(self.owned(), runtime=RUNTIME + '-other'), 'device-identity-changed'),
                 ('type', lambda: inventory_bytes(self.owned(deviceTypeIdentifier=DEVICE_TYPE + '-other')), 'device-identity-changed'),
                 ('unavailable', lambda: inventory_bytes(self.owned(isAvailable=False)), 'device-identity-changed')]
        for defect, raw, code in cases:
            self.fresh()
            with self.subTest(defect=defect), self.scripted([
                response('inventory', inventory_bytes()),
                response('create', b'unparseable-create-output', before=self.device_path.mkdir),
                response('inventory', raw()),
            ]) as script:
                with self.assertRaisesRegex(RuntimeError, 'create-stdout-uuid'):
                    self.life.command(self.create_arguments(), 'create.log')
                with self.assertRaisesRegex(RuntimeError, code):
                    self.life.recover()
                self.assertIsNone(self.life.uuid)
                self.assertTrue(self.life.cleanup_failed)
                self.life.prepare_cleanup(None, False)
                with self.assertRaisesRegex(RuntimeError, 'unjournaled-destructive-resource'):
                    self.life.delete()
                self.assertEqual(['inventory', 'create', 'inventory'], script.calls)
                self.assert_retained()
                self.assert_final_failure()

    def test_create_returning_baseline_uuid_cannot_adopt_or_destroy_that_foreign_resource(self):
        foreign_path = self.device_set / FOREIGN
        foreign_path.mkdir()
        with self.scripted([
            response('inventory', inventory_bytes(device(name=PRIVATE))),
            response('create', (FOREIGN + '\n').encode()),
            response('inventory', inventory_bytes(self.owned(udid=FOREIGN))),
        ]) as script:
            with self.assertRaisesRegex(RuntimeError, 'create-returned-preexisting-uuid'):
                self.life.command(self.create_arguments(), 'create.log')
            with self.assertRaisesRegex(RuntimeError, 'preexisting-or-unjournaled-device'):
                self.life.recover()
            self.assertIsNone(self.life.uuid)
            self.life.prepare_cleanup(None, False)
            with self.assertRaisesRegex(RuntimeError, 'unjournaled-destructive-resource'):
                self.life.delete()
            self.assertEqual(['inventory', 'create', 'inventory'], script.calls)
        self.assertTrue(foreign_path.is_dir())
        self.assertFalse(self.life.absent)
        self.assertFalse((self.evidence / 'create.log').exists())
        self.assertNotIn('create_exit_code', self.receipt)
        self.assert_final_failure()

    def test_create_stdout_rejects_multiple_uuid_lines_nonascii_and_nonuuid_tokens(self):
        for raw in (b'all\n', b'booted\n', (OWNED + '\n' + OTHER + '\n').encode(), b'\xff'):
            self.fresh()
            with self.subTest(raw=raw), self.scripted([
                response('inventory', inventory_bytes()), response('create', raw),
            ]) as script:
                with self.assertRaises((RuntimeError, UnicodeDecodeError)):
                    self.life.command(self.create_arguments(), 'create.log')
                self.assertEqual(['inventory', 'create'], script.calls)
                self.assertIsNone(self.life.uuid)
                self.assertTrue(self.life.failed)
                self.assertFalse((self.evidence / 'create.log').exists())

    def test_unissued_create_failure_has_no_resource_lookup_or_recovery_authority(self):
        error = OSError(PRIVATE)
        with self.scripted([response('inventory', inventory_bytes()),
                            response('create', launch_error=error)]) as script:
            with self.assertRaises(OSError) as caught:
                self.life.command(self.create_arguments(), 'create.log')
            self.assertIs(error, caught.exception)
            self.assertTrue(self.life.creation_started)
            self.assertFalse(self.life.creation_dispatched)
            with self.assertRaisesRegex(RuntimeError, 'create-not-issued-no-resource-authority'):
                self.life.recover()
            self.assertEqual(['inventory', 'create'], script.calls)
            self.assertIsNone(self.life.uuid)
            self.assertFalse(self.life.rows[-1]['handle_registered'])
            self.assertEqual(1, self.life.summary()['direct_children']['started'])

    def test_exact_owned_identity_rejects_name_uuid_runtime_type_availability_and_baseline_changes(self):
        self.create()
        candidates = [inventory_bytes(self.owned(name=self.name + '-clone')),
                      inventory_bytes(self.owned(), self.owned(udid=OTHER)),
                      inventory_bytes(self.owned(name='renamed-exact-uuid')),
                      inventory_bytes(self.owned(udid=OTHER)),
                      inventory_bytes(self.owned(), runtime=RUNTIME + '-other'),
                      inventory_bytes(self.owned(deviceTypeIdentifier=DEVICE_TYPE + '-other')),
                      inventory_bytes(self.owned(isAvailable=False)),
                      inventory_bytes(self.owned(udid=FOREIGN))]
        for raw in candidates:
            with self.subTest(raw=raw[:100]), self.assertRaises(RuntimeError):
                self.life._owned(lifecycle.inventory(raw))
        self.life.uuid = None
        with self.assertRaisesRegex(RuntimeError, 'preexisting-or-unjournaled-device'):
            self.life._owned(lifecycle.inventory(inventory_bytes(self.owned(udid=FOREIGN))))
        self.life.creation_intent = False
        with self.assertRaisesRegex(RuntimeError, 'preexisting-or-unjournaled-device'):
            self.life._owned(lifecycle.inventory(inventory_bytes(self.owned())))


class CaptureAndPersistenceControls(LifecycleFixture):
    def test_capture_keeps_counts_hashes_exact_command_and_reaped_handle_not_raw_output(self):
        raw, diagnostics = PRIVATE.encode(), b' \n\t'
        with self.scripted([response('inventory', raw, stderr=diagnostics)]) as script:
            self.assertEqual(raw, self.capture())
        row = self.life.rows[0]
        self.assertIs(row, self.receipt['commands'][0])
        self.assertEqual('EXITED0', row['status'])
        self.assertEqual(0, row['exit_code'])
        self.assertTrue(row['handle_registered'])
        self.assertTrue(row['reaped'])
        self.assertEqual((len(raw), len(diagnostics)), (row['stdout_bytes'], row['stderr_bytes']))
        self.assertEqual(hashlib.sha256(raw).hexdigest(), row['stdout_sha256'])
        self.assertEqual(hashlib.sha256(diagnostics).hexdigest(), row['stderr_sha256'])
        self.assertNotIn(PRIVATE, self.life.journal.read_text())
        self.assertNotIn(PRIVATE, json.dumps(self.receipt))
        self.assertTrue(all(pipe.closed for child in script.children for pipe in (child.stdout, child.stderr)))

    def test_capture_systematic_primary_failures_are_sticky_sanitized_and_reaped(self):
        cases = [('nonzero-command', dict(exit_code=7)), ('diagnostic-stderr', dict(stderr=PRIVATE.encode())),
                 ('stdout-overflow', dict(stdout=b'x' * (lifecycle.LIMIT + 1))),
                 ('stderr-overflow', dict(stderr=b'x' * (lifecycle.LIMIT + 1))),
                 ('command-timeout', dict(wait_results=[subprocess.TimeoutExpired(PRIVATE, 45, output=PRIVATE)])),
                 ('cancelled', dict(wait_results=[KeyboardInterrupt(PRIVATE)])),
                 ('cancelled', dict(wait_results=[SystemExit(PRIVATE)])),
                 ('unexpected-exception', dict(read_error=OSError(PRIVATE))),
                 ('unexpected-exception', dict(selector_error=OSError(PRIVATE))),
                 ('unexpected-exception', dict(select_error=OSError(PRIVATE)))]
        for code, options in cases:
            self.fresh()
            with self.subTest(code=code, seam=list(options)), self.scripted([response('inventory', **options)]) as script:
                with self.assertRaises((RuntimeError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt, SystemExit)):
                    self.capture()
                row = self.life.rows[0]
                self.assertEqual('FAILED', row['status'])
                self.assertEqual(code, row['primary_error']['code'])
                self.assertTrue(row['reaped'])
                self.assertTrue(self.life.failed)
                self.assertNotIn(PRIVATE, self.life.journal.read_text())
                with self.assertRaisesRegex(RuntimeError, 'sticky-failure-no-new-work'):
                    self.capture()
                self.assertEqual(['inventory'], script.calls)
                self.assertTrue(all(pipe.closed for pipe in (script.children[0].stdout, script.children[0].stderr)))

    def test_launch_failure_and_invalid_pid_never_escape_handle_accounting(self):
        for options in (dict(launch_error=OSError(PRIVATE)), dict(pid=True), dict(pid=-1)):
            self.fresh()
            with self.subTest(options=list(options)), self.scripted([response('inventory', **options)]) as script:
                with self.assertRaises((RuntimeError, OSError)):
                    self.capture()
                self.assertTrue(self.life.failed)
                self.assertEqual(len(script.children), self.life.summary()['direct_children']['started'])
                self.assertEqual(len(script.children), self.life.summary()['direct_children']['reaped'])
                self.assertEqual(bool(script.children), self.life.rows[0]['handle_registered'])
                self.assertEqual('FAILED', self.life.rows[0]['status'])

    def test_secondary_retirement_and_pipe_errors_cannot_replace_primary_cancellation(self):
        original = KeyboardInterrupt(PRIVATE)
        timeout = subprocess.TimeoutExpired('synthetic', 0)
        with self.scripted([response('inventory', wait_results=[original, timeout, timeout, -9],
            action_errors={'terminate': OSError(PRIVATE)}, pipe_error=OSError(PRIVATE),
            selector_close_error=OSError(PRIVATE))]) as script:
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.capture()
        self.assertIs(original, caught.exception)
        row = self.life.rows[0]
        self.assertEqual('cancelled', row['primary_error']['code'])
        self.assertTrue(row['reaped'])
        self.assertEqual(-9, row['exit_code'])
        self.assertEqual(['terminate', 'terminate-wait', 'selector-close', 'pipe-close'],
                         [error['stage'] for error in row['secondary_errors']])
        actions = script.children[0].actions
        self.assertEqual([('wait', 0), ('terminate',), ('wait', 5), ('kill',), ('wait', 5)], actions[1:])
        self.assertNotIn(PRIVATE, json.dumps(row))

    def test_unreaped_child_is_not_success_and_later_reaping_never_erases_original_failure(self):
        timeout = subprocess.TimeoutExpired('synthetic', 0)
        with self.scripted([response('inventory', exit_code=timeout,
                                    action_errors={'terminate': OSError(PRIVATE), 'kill': OSError(PRIVATE)})]) as script:
            with self.assertRaises(subprocess.TimeoutExpired):
                self.capture()
            row = self.life.rows[0]
            self.assertFalse(row['reaped'])
            self.assertEqual('FAIL', self.life.summary()['direct_children']['status'])
            self.assertIn('unreaped-direct-child', [error['stage'] for error in row['secondary_errors']])
            before = deepcopy(row['primary_error'])
            script.children[0].default_wait = -9
            self.life.retire_direct_children()
            self.assertTrue(row['reaped'])
            self.assertTrue(self.life.failed)
            self.assertEqual('FAILED', row['status'])
            self.assertEqual(before, row['primary_error'])

    def test_secondary_only_failure_turns_exit_zero_into_failed_capture(self):
        for options in (dict(pipe_error=OSError(PRIVATE)), dict(selector_close_error=OSError(PRIVATE)),
                        dict(wait_results=[0, True, 0])):
            self.fresh()
            with self.subTest(options=list(options)), self.scripted([response('inventory', **options)]):
                with self.assertRaisesRegex(RuntimeError, 'capture-retirement-incomplete'):
                    self.capture()
                self.assertEqual('FAILED', self.life.rows[0]['status'])
                self.assertTrue(self.life.rows[0]['secondary_errors'])

    def test_command_checkpoint_receipt_failure_prevents_allocation(self):
        def failed_command_checkpoint():
            if self.receipt['commands']:
                raise OSError(PRIVATE)
        self.save.side_effect = failed_command_checkpoint
        with self.assertRaises(OSError):
            self.capture()
        self.denied_popen.assert_not_called()
        self.assertTrue(self.life.failed)
        self.assertTrue(self.life.evidence_failed)
        self.assertFalse(self.life.rows[0]['handle_registered'])
        self.assertEqual(0, self.life.summary()['direct_children']['started'])

    def test_result_persistence_failure_is_sticky_and_preserves_an_existing_primary(self):
        for exit_code in (0, 7):
            self.fresh()

            def fsync(fd):
                if stat.S_ISREG(os.fstat(fd).st_mode) and b'"event":"command-result"' in self.life.journal.read_bytes():
                    raise OSError(PRIVATE)
                REAL_FSYNC(fd)

            with self.subTest(exit_code=exit_code), patch.object(lifecycle.os, 'fsync', fsync), self.scripted([
                response('inventory', exit_code=exit_code),
            ]):
                with self.assertRaises(OSError if exit_code == 0 else RuntimeError):
                    self.capture()
            row = self.life.rows[0]
            self.assertTrue(row['reaped'])
            self.assertEqual('FAILED', row['status'])
            self.assertTrue(self.life.evidence_failed)
            self.assertTrue(self.life.failed)
            self.assertEqual('unexpected-exception' if exit_code == 0 else 'nonzero-command', row['primary_error']['code'])
            if exit_code:
                self.assertIn('result-persistence', [error['stage'] for error in row['secondary_errors']])

    def test_journal_short_writes_are_completed_zero_writes_and_bounds_fail_closed(self):
        with patch.object(lifecycle.os, 'write', side_effect=lambda fd, raw: REAL_WRITE(fd, raw[:7])):
            self.life._initialize()
        self.assertEqual(self.life.journal_bytes, self.life.journal.read_bytes())
        self.life._check_journal()
        for mode in ('zero-write', 'journal-bound', 'directory-fsync'):
            self.fresh()
            with self.subTest(mode=mode), ExitStack() as stack:
                if mode == 'zero-write':
                    stack.enter_context(patch.object(lifecycle.os, 'write', return_value=0))
                elif mode == 'journal-bound':
                    stack.enter_context(patch.object(lifecycle, 'MAX_JOURNAL', 1))
                else:
                    def fsync(fd):
                        if stat.S_ISDIR(os.fstat(fd).st_mode):
                            raise OSError(PRIVATE)
                        REAL_FSYNC(fd)
                    stack.enter_context(patch.object(lifecycle.os, 'fsync', fsync))
                with self.assertRaises((RuntimeError, OSError)):
                    self.life._initialize()
                self.assertTrue(self.life.evidence_failed)
        self.denied_popen.assert_not_called()

    def test_deadline_includes_launch_checkpoint_and_cleanup_needs_full_command_plus_retirement(self):
        clock = [100.0]

        def advance_after_launch():
            clock[0] += 45

        with patch.object(lifecycle.time, 'monotonic', side_effect=lambda: clock[0]), self.scripted([
            response('inventory', before=advance_after_launch),
        ]):
            with self.assertRaisesRegex(RuntimeError, 'command-timeout'):
                self.capture()
        self.assertTrue(self.life.rows[0]['reaped'])
        self.assertEqual(45, self.life.rows[0]['timeout_seconds'])
        for late in (False, True):
            self.fresh()
            clock[0] = 100.0
            with self.subTest(late_checkpoint=late), patch.object(lifecycle.time, 'monotonic', side_effect=lambda: clock[0]):
                self.life._initialize()
                self.life._start_cleanup()
                self.life.cleanup_deadline = 155.0 if late else 154.9
                if late:
                    self.save.side_effect = lambda: clock.__setitem__(0, 101.0)
                with self.assertRaisesRegex(RuntimeError, 'cleanup-budget-no-full-command-allowance'):
                    self.capture(cleanup=True)
                self.denied_popen.assert_not_called()

    def test_deferred_cancellation_guard_has_bounded_pending_data_and_restores_handlers(self):
        registered = {}
        previous = object()

        def install(sig, handler):
            registered[sig] = handler
            return previous

        with patch.object(lifecycle.signal, 'pthread_sigmask', return_value=set()) as masks, \
                patch.object(lifecycle.signal, 'signal', side_effect=install) as handlers:
            with lifecycle.defer_signals() as pending:
                callback = registered[signal.SIGTERM]
                for _ in range(40):
                    callback(signal.SIGTERM, None)
                self.assertEqual([signal.SIGTERM] * 16, pending)
            self.assertEqual(4, handlers.call_count)
            self.assertEqual(4, masks.call_count)
            self.assertTrue(all(value is previous for value in registered.values()))
        with patch.object(lifecycle.signal, 'pthread_sigmask', return_value={signal.SIGTERM}), \
                patch.object(lifecycle.signal, 'signal') as handlers:
            with self.assertRaisesRegex(RuntimeError, 'inherited-blocked-cancellation'):
                with lifecycle.defer_signals():
                    self.fail('Inherited blocked cancellation admitted')
            handlers.assert_not_called()


class CustodyAndPostbuildBarrierControls(LifecycleFixture):
    def test_journal_custody_bytes_inode_mode_symlink_and_hardlink_substitutions_retain_resource(self):
        for mutation in ('append', 'same-size-bytes', 'inode', 'mode', 'symlink', 'hardlink'):
            self.fresh()
            self.create()
            self.life.prepare_cleanup(None, False)
            journal = self.life.journal
            raw = journal.read_bytes()
            held = self.evidence / 'held-journal'
            if mutation == 'append':
                with journal.open('ab') as stream:
                    stream.write(b'{}\n')
            elif mutation == 'same-size-bytes':
                journal.write_bytes(raw.replace(b'direct-owned-v1', b'direct-owned-v0'))
            elif mutation == 'mode':
                journal.chmod(0o644)
            elif mutation == 'hardlink':
                os.link(journal, held)
            else:
                journal.rename(held)
                if mutation == 'symlink':
                    journal.symlink_to(held)
                else:
                    journal.write_bytes(raw)
                    journal.chmod(0o600)
            with self.subTest(mutation=mutation):
                for operation in (self.life.shutdown, self.life.delete):
                    with self.assertRaises(RuntimeError):
                        operation()
                self.assert_retained()
                self.assert_final_failure()
        self.denied_popen.assert_not_called()

    def test_source_control_task_and_directory_custody_substitutions_refuse_destruction(self):
        for mutation in ('source', 'control', 'temporary-mode', 'temporary-inode', 'temporary-symlink',
                         'evidence-inode', 'evidence-symlink', 'mode', 'cycle', 'name', 'runtime', 'device_type'):
            self.fresh()
            self.create()
            self.life.prepare_cleanup(None, False)
            if mutation == 'source':
                self.source['tree'] = 'substituted-source'
            elif mutation == 'control':
                self.approved = 'b' * 64
            elif mutation == 'temporary-mode':
                self.temporary.chmod(0o755)
            elif mutation.startswith(('temporary-', 'evidence-')):
                path = self.temporary if mutation.startswith('temporary-') else self.evidence
                held = path.with_name(path.name + '-held')
                path.rename(held)
                if mutation.endswith('symlink'):
                    path.symlink_to(held, target_is_directory=True)
                else:
                    path.mkdir(mode=0o700)
            else:
                self.life.header[mutation] = 'substituted-intent'
            with self.subTest(mutation=mutation):
                with self.assertRaises((RuntimeError, FileNotFoundError)):
                    self.life.delete()
                self.assert_retained()
                self.assert_final_failure()
        self.denied_popen.assert_not_called()

    def test_mark_build_is_sticky_before_durable_write_or_receipt_failure(self):
        for stage in ('journal', 'receipt'):
            self.fresh()
            self.create()
            with self.subTest(stage=stage), ExitStack() as stack:
                if stage == 'journal':
                    stack.enter_context(patch.object(lifecycle.os, 'fsync', side_effect=OSError(PRIVATE)))
                else:
                    self.save.side_effect = OSError(PRIVATE)
                with self.assertRaises(OSError):
                    self.life.mark_build_attempted()
            self.assertTrue(self.life.build_attempted)
            self.assertIs(self.receipt['build_attempted'], True)
            self.assertTrue(self.life.failed)
            with self.assertRaises(RuntimeError):
                self.life.mark_build_attempted()
            self.assert_retained()

    def test_postbuild_requires_exact_preservation_ack_before_any_worker_stop(self):
        for acknowledgment in (None, False, 1, 'true'):
            self.fresh()
            self.create()
            self.life.mark_build_attempted()
            if acknowledgment is not None:
                self.receipt['postbuild_evidence_preserved'] = acknowledgment
            owner = self.owner()
            with self.subTest(acknowledgment=acknowledgment):
                with self.assertRaisesRegex(RuntimeError, 'postbuild-evidence-not-preserved'):
                    self.life.prepare_cleanup(owner, True)
                owner.stop.assert_not_called()
                owner.refresh.assert_not_called()
                self.assertEqual('FAIL', self.life.barrier['status'])
                self.assert_retained()
                with self.assertRaisesRegex(RuntimeError, 'destruction-not-authorized'):
                    self.life.shutdown()

    def test_postbuild_strict_stop_fresh_refresh_then_every_exact_handle_wait_precede_barrier(self):
        self.create()
        self.life.mark_build_attempted()
        self.receipt['postbuild_evidence_preserved'] = True
        owner = self.owner()
        child = SimpleNamespace(wait=Mock(side_effect=lambda timeout: owner.events.append(('wait', timeout)) or 0))
        owner.handles = [child]
        self.assertTrue(self.life.prepare_cleanup(owner, True))
        owner.stop.assert_called_once_with()
        owner.refresh.assert_called_once_with(inspect_files=True, include_outputs=True)
        child.wait.assert_called_once_with(timeout=0)
        self.assertEqual(['stop', ('refresh', dict(inspect_files=True, include_outputs=True)), ('wait', 0)], owner.events)
        self.assertEqual(dict(status='PASS', build_attempted=True, evidence_preserved=True, strict_stop=True,
            fresh_strict_refresh=True, direct_build_handles=1, direct_build_handles_reaped=1), self.life.barrier)
        self.assertIn(b'"event":"destructive-cleanup-barrier"', self.life.journal_bytes)

    def test_every_postbuild_barrier_negative_is_sticky_and_retains_resource(self):
        for defect in ('owner-missing', 'stop-error', 'refresh-error', 'live', 'unknown', 'secondary-error',
                       'handles-not-list', 'too-many-handles', 'wait-timeout', 'wait-cancelled',
                       'wait-noninteger', 'wait-boolean', 'marker-disagreement', 'nonboolean-marker'):
            self.fresh()
            self.create()
            self.life.mark_build_attempted()
            self.receipt['postbuild_evidence_preserved'] = True
            owner, marker = self.owner(), True
            if defect == 'owner-missing':
                owner = None
            elif defect == 'stop-error':
                owner.stop.side_effect = RuntimeError(PRIVATE)
            elif defect == 'refresh-error':
                owner.refresh.side_effect = RuntimeError(PRIVATE)
            elif defect == 'live':
                owner.refresh.side_effect = lambda **kwargs: [{'synthetic': 'worker'}]
            elif defect == 'unknown':
                owner.unknown_holders = ['synthetic-holder']
            elif defect == 'secondary-error':
                owner.secondary_errors = ['synthetic-stop-error']
            elif defect == 'handles-not-list':
                owner.handles = ()
            elif defect == 'too-many-handles':
                owner.handles = [object()] * 4097
            elif defect.startswith('wait-'):
                result = {'wait-timeout': subprocess.TimeoutExpired('synthetic-build', 0),
                          'wait-cancelled': KeyboardInterrupt(PRIVATE), 'wait-noninteger': None,
                          'wait-boolean': True}[defect]
                owner.handles = [SimpleNamespace(wait=Mock(side_effect=result) if isinstance(result, BaseException)
                                                else Mock(return_value=result))]
            else:
                marker = False if defect == 'marker-disagreement' else 1
            with self.subTest(defect=defect):
                with self.assertRaises((RuntimeError, subprocess.TimeoutExpired, KeyboardInterrupt)):
                    self.life.prepare_cleanup(owner, marker)
                self.assertEqual('FAIL', self.life.barrier['status'])
                self.assertTrue(self.life.cleanup_failed)
                with self.assertRaisesRegex(RuntimeError, 'cleanup-barrier-already-decided'):
                    self.life.prepare_cleanup(self.owner(), True)
                for operation in (self.life.shutdown, self.life.delete):
                    with self.assertRaisesRegex(RuntimeError, 'destruction-not-authorized'):
                        operation()
                self.assert_retained()
                self.assert_final_failure()
        self.denied_popen.assert_not_called()

    def test_unreaped_lifecycle_child_blocks_postbuild_owner_stop_and_cannot_repair_failed_barrier(self):
        self.create()
        self.life.mark_build_attempted()
        self.receipt['postbuild_evidence_preserved'] = True
        owner = self.owner()
        timeout = subprocess.TimeoutExpired('synthetic-owned-lifecycle', 0)
        with self.scripted([response('inventory', exit_code=timeout)]) as script:
            with self.assertRaises(subprocess.TimeoutExpired):
                self.capture()
            with self.assertRaisesRegex(RuntimeError, 'new-retirement-failure'):
                self.life.prepare_cleanup(owner, True)
            owner.stop.assert_not_called()
            owner.refresh.assert_not_called()
            self.assertEqual('FAIL', self.life.barrier['status'])
            self.assertEqual(1, self.life.summary()['direct_children']['unreaped'])
            script.children[0].default_wait = -9
            self.life.retire_direct_children()
            self.assertEqual(0, self.life.summary()['direct_children']['unreaped'])
            with self.assertRaisesRegex(RuntimeError, 'cleanup-barrier-already-decided'):
                self.life.prepare_cleanup(owner, True)
        self.assert_retained()
        self.assert_final_failure()

    def test_no_destructive_route_exists_without_a_decided_barrier(self):
        self.create()
        self.receipt['postbuild_evidence_preserved'] = True
        for operation in (self.life.shutdown, self.life.delete):
            with self.assertRaisesRegex(RuntimeError, 'destruction-not-authorized'):
                operation()
        self.assert_retained()
        self.denied_popen.assert_not_called()


class CleanupAndReceiptControls(LifecycleFixture):
    def clean(self, *, booted=False, postbuild=False):
        self.create('Booted' if booted else 'Shutdown')
        owner = None
        if postbuild:
            self.life.mark_build_attempted()
            self.receipt['postbuild_evidence_preserved'] = True
            owner = self.owner()
        self.life.prepare_cleanup(owner, postbuild)
        shutdown = [response('inventory', inventory_bytes(self.owned('Booted' if booted else 'Shutdown')))]
        if booted:
            shutdown += [response('shutdown'), response('inventory', inventory_bytes(self.owned()))]
        with self.scripted([*shutdown, response('inventory', inventory_bytes(self.owned())),
            response('delete', before=self.device_path.rmdir), response('inventory', inventory_bytes())]) as script:
            self.life.shutdown()
            self.life.delete()
            self.assertFalse(script.remaining)
        return owner

    def test_prebuild_already_shutdown_success_has_exact_absence_and_no_invented_shutdown_exit(self):
        self.clean()
        self.assertTrue(self.life.finish())
        summary = self.life.summary()
        self.assertEqual('PASS', summary['cleanup_status'])
        self.assertEqual('NOT_REQUIRED_PREBUILD', summary['postbuild_barrier']['status'])
        self.assertEqual(dict(started=7, reaped=7, unreaped=0, status='PASS'), summary['direct_children'])
        self.assertFalse(self.device_path.exists())
        self.assertIs(self.receipt['owned_device_absent'], True)
        self.assertEqual('already_shutdown', self.receipt['shutdown_disposition'])
        self.assertNotIn('shutdown_exit_code', self.receipt)
        self.assertFalse((self.evidence / 'shutdown.log').exists())
        self.assertEqual(0, self.receipt['delete_exit_code'])
        self.assertEqual(lifecycle.sha(self.life.journal.read_bytes()), summary['journal']['sha256'])
        self.assertEqual(summary, self.receipt['simulator_lifecycle'])
        self.assertEqual('lifecycle-finalized', json.loads(self.life.journal.read_bytes().splitlines()[-1])['event'])
        self.assertTrue(self.temporary.is_dir())  # Lifecycle never deletes the copy/build root.
        with self.assertRaisesRegex(RuntimeError, 'command-count-or-finished'):
            self.capture(cleanup=True)

    def test_postbuild_booted_success_runs_strict_barrier_before_shutdown_and_delete(self):
        owner = self.clean(booted=True, postbuild=True)
        self.assertTrue(self.life.finish())
        owner.stop.assert_called_once_with()
        self.assertEqual('PASS', self.life.barrier['status'])
        self.assertEqual(['inventory', 'create', 'inventory', 'inventory', 'shutdown', 'inventory',
                          'inventory', 'delete', 'inventory'], [row['operation'] for row in self.life.rows])
        self.assertEqual(0, self.receipt['shutdown_exit_code'])
        self.assertEqual(0, self.receipt['delete_exit_code'])
        self.assertEqual('PASS', self.receipt['simulator_lifecycle']['cleanup_status'])
        events = [json.loads(line) for line in self.life.journal_bytes.splitlines()]
        barrier = next(index for index, event in enumerate(events) if event['event'] == 'destructive-cleanup-barrier')
        destructive = [index for index, event in enumerate(events) if event['event'] == 'command-intent'
                       and event['row']['operation'] in {'shutdown', 'delete'}]
        self.assertTrue(all(index > barrier for index in destructive))

    def test_shutdown_or_delete_nonzero_diagnostic_timeout_cancel_retains_resource(self):
        for operation in ('shutdown', 'delete'):
            for kind in ('nonzero', 'stderr', 'timeout', 'cancel'):
                self.fresh()
                self.create('Booted' if operation == 'shutdown' else 'Shutdown')
                self.life.prepare_cleanup(None, False)
                options = {'nonzero': dict(exit_code=1), 'stderr': dict(stderr=PRIVATE.encode()),
                           'timeout': dict(wait_results=[subprocess.TimeoutExpired(PRIVATE, 120)]),
                           'cancel': dict(wait_results=[KeyboardInterrupt(PRIVATE)])}[kind]
                with self.subTest(operation=operation, failure=kind), self.scripted([
                    response('inventory', inventory_bytes(self.owned('Booted' if operation == 'shutdown' else 'Shutdown'))),
                    response(operation, **options),
                ]) as script:
                    with self.assertRaises((RuntimeError, subprocess.TimeoutExpired, KeyboardInterrupt)):
                        getattr(self.life, operation)()
                    self.assertEqual(['inventory', operation], script.calls)
                self.assertNotIn(operation + '_exit_code', self.receipt)
                self.assertTrue(self.life.cleanup_failed)
                self.assert_retained()
                self.assert_final_failure()

    def test_shutdown_requires_new_exact_shutdown_state_and_delete_never_accepts_booted_metadata(self):
        self.create('Booted')
        self.life.prepare_cleanup(None, False)
        with self.scripted([response('inventory', inventory_bytes(self.owned('Booted'))), response('shutdown'),
                            response('inventory', inventory_bytes(self.owned('Booted'))),
                            response('inventory', inventory_bytes(self.owned('Booted')))]) as script:
            with self.assertRaisesRegex(RuntimeError, 'shutdown-incomplete'):
                self.life.shutdown()
            with self.assertRaisesRegex(RuntimeError, 'delete-requires-fresh-shutdown'):
                self.life.delete()
            self.assertNotIn('delete', script.calls)
        self.assertNotIn('shutdown_exit_code', self.receipt)
        self.assertTrue(self.life.cleanup_failed)
        self.assert_retained()

    def test_actual_delete_metadata_substitutions_never_dispatch_a_destructive_command(self):
        for defect in ('renamed', 'wrong-uuid', 'clone', 'runtime'):
            self.fresh()
            self.create()
            self.life.prepare_cleanup(None, False)
            if defect == 'renamed':
                raw = inventory_bytes(self.owned(name='renamed-test-owned-uuid'))
            elif defect == 'wrong-uuid':
                raw = inventory_bytes(self.owned(udid=OTHER))
            elif defect == 'clone':
                raw = inventory_bytes(self.owned(), self.owned(udid=OTHER))
            else:
                raw = inventory_bytes(self.owned(), runtime=RUNTIME + '-other')
            with self.subTest(defect=defect), self.scripted([response('inventory', raw)]) as script:
                with self.assertRaises(RuntimeError):
                    self.life.delete()
                self.assertEqual(['inventory'], script.calls)
                self.assertNotIn('delete_exit_code', self.receipt)
                self.assert_retained()
                self.assert_final_failure()

    def test_delete_exit_zero_is_not_absence_when_metadata_directory_or_symlink_remains(self):
        for defect in ('metadata-remains', 'directory-remains', 'dangling-device-symlink', 'redirected-device-set'):
            self.fresh()
            self.create()
            self.life.prepare_cleanup(None, False)
            if defect == 'dangling-device-symlink':
                self.device_path.rmdir()
                self.device_path.symlink_to(self.home / 'missing-synthetic-target')
            elif defect == 'redirected-device-set':
                held = self.device_set.with_name('Devices-held')
                self.device_set.rename(held)
                self.device_set.symlink_to(held, target_is_directory=True)
            after = inventory_bytes(self.owned()) if defect == 'metadata-remains' else inventory_bytes()
            with self.subTest(defect=defect), self.scripted([
                response('inventory', inventory_bytes(self.owned())), response('delete'), response('inventory', after),
            ]):
                with self.assertRaises(RuntimeError):
                    self.life.delete()
            self.assertEqual(0, self.receipt['delete_exit_code'])  # The CLI exited zero; retirement did not succeed.
            self.assertNotIn('owned_device_absent', self.receipt)
            self.assert_retained()
            self.assert_final_failure()

    def test_finish_without_absence_or_with_late_journal_or_receipt_failure_cannot_report_pass(self):
        self.create()
        self.life.prepare_cleanup(None, False)
        self.assert_final_failure()
        for defect in ('journal-substitution', 'receipt-failure'):
            self.fresh()
            self.clean()
            if defect == 'journal-substitution':
                self.life.journal.write_bytes(b'{}\n')
            else:
                self.save.side_effect = OSError(PRIVATE)
            with self.subTest(defect=defect):
                self.assert_final_failure()
                self.assertEqual('FAIL', self.receipt['simulator_lifecycle']['cleanup_status'])

    def test_sanitized_log_refuses_existing_file_or_symlink_without_overwriting_foreign_data(self):
        for kind in ('file', 'symlink'):
            self.fresh()
            self.create()
            target = self.root / 'foreign-log'
            target.write_bytes(b'foreign-evidence')
            path = self.evidence / 'boot.log'
            if kind == 'file':
                path.write_bytes(b'preexisting-log')
            else:
                path.symlink_to(target)
            with self.subTest(kind=kind), self.scripted([response('boot', PRIVATE.encode())]):
                with self.assertRaises(OSError):
                    self.life.command(['xcrun', 'simctl', 'boot', OWNED], 'boot.log')
            self.assertTrue(self.life.evidence_failed)
            self.assertTrue(self.life.failed)
            self.assertEqual(b'foreign-evidence', target.read_bytes())
            self.assertEqual(b'preexisting-log' if kind == 'file' else b'foreign-evidence', path.read_bytes())
            self.assertNotIn(PRIVATE, self.life.journal.read_text())


class WaitOnceAdapter:
    """One injected wait outcome, all retirement delegated to the exact handle."""
    def __init__(self, child, error):
        self.child, self.error = child, error

    def __getattr__(self, name):
        return getattr(self.child, name)

    def wait(self, timeout):
        if self.error is not None and timeout > 0:
            error, self.error = self.error, None
            raise error
        return self.child.wait(timeout=timeout)


class HarnessSubprocessControls(LifecycleFixture):
    @contextmanager
    def standin(self, code, *, wait_error=None):
        handles = []

        def launch(arguments, **options):
            self.assert_launch_request('inventory', arguments, options)
            child = REAL_POPEN([sys.executable, '-B', '-c', code], **options)
            handles.append(child)  # Capture immediately; cleanup never adopts a PID.
            return child if wait_error is None else WaitOnceAdapter(child, wait_error)

        try:
            with patch.object(lifecycle.subprocess, 'Popen', side_effect=launch):
                yield handles
        finally:
            primary = sys.exc_info()[1]
            for child in handles:
                helper_reaped = any(row.get('owned_pid') == child.pid and row.get('reaped') is True
                                    for row in self.life.rows)
                fallback = child.returncode is None
                errors = []
                try:
                    # Every operation addresses this exact Popen. A failed
                    # terminate/wait still reaches kill/wait; no PID adoption.
                    for action, allowance in ((None, 0), ('terminate', 5), ('kill', 5)):
                        if action is not None and type(child.returncode) is int:
                            break
                        if action is not None:
                            try:
                                getattr(child, action)()
                            except BaseException as error:
                                errors.append(dict(stage=action, type=type(error).__name__))
                        try:
                            child.wait(timeout=allowance)
                        except subprocess.TimeoutExpired:
                            if action is not None:
                                errors.append(dict(stage=str(action) + '-wait', type='TimeoutExpired'))
                        except BaseException as error:
                            errors.append(dict(stage=str(action) + '-wait', type=type(error).__name__))
                finally:
                    for pipe in (child.stdout, child.stderr):
                        if pipe is not None:
                            try:
                                pipe.close()
                            except BaseException as error:
                                errors.append(dict(stage='pipe-close', type=type(error).__name__))
                    print('HARNESS_SUBPROCESS_CONTROL ' + json.dumps(dict(
                        test=self.id(), native_runtime_proof=False, pid=child.pid,
                        interpreter=sys.executable, arguments=['-B', '-c', '<fixed-stand-in>'],
                        temporary=str(self.base), helper_reaped=helper_reaped,
                        fallback_retirement=fallback, return_code=child.returncode,
                        cleanup_errors=errors, reaped=type(child.returncode) is int), sort_keys=True), flush=True)
                if errors or type(child.returncode) is not int:
                    message = 'Harness exact-handle retirement failed: ' + json.dumps(errors)
                    if primary is not None:
                        primary.add_note(message)
                    else:
                        self.fail(message)

    def assert_harness_reaped(self, handles):
        self.assertEqual(1, len(handles))
        self.assertIsInstance(handles[0].returncode, int)
        self.assertTrue(self.life.rows[0]['handle_registered'])
        self.assertTrue(self.life.rows[0]['reaped'])
        self.assertEqual(dict(started=1, reaped=1, unreaped=0, status='PASS'), self.life.summary()['direct_children'])

    def test_harness_real_success_drains_both_streams_and_reaps_exact_handle(self):
        raw = b'synthetic-harness-output\n'
        with self.standin("import os; os.write(1, b'synthetic-harness-output\\n'); os.write(2, b' \\n')") as handles:
            self.assertEqual(raw, self.capture())
            self.assert_harness_reaped(handles)
            self.assertEqual('EXITED0', self.life.rows[0]['status'])
            self.assertEqual(hashlib.sha256(raw).hexdigest(), self.life.rows[0]['stdout_sha256'])

    def test_harness_exit_zero_with_real_diagnostic_stderr_remains_failed(self):
        with self.standin(f'import os; os.write(2, {PRIVATE.encode()!r})') as handles:
            with self.assertRaisesRegex(RuntimeError, 'diagnostic-stderr'):
                self.capture()
            self.assert_harness_reaped(handles)
            self.assertEqual(0, self.life.rows[0]['exit_code'])
            self.assertEqual('FAILED', self.life.rows[0]['status'])
            self.assertNotIn(PRIVATE, self.life.journal.read_text())

    def test_harness_real_stdout_flood_is_bounded_and_the_exact_writer_is_reaped(self):
        code = f'import sys; sys.stdout.buffer.write(b"x" * {lifecycle.LIMIT + 65536}); sys.stdout.buffer.flush()'
        with self.standin(code) as handles:
            with self.assertRaisesRegex(RuntimeError, 'stdout-overflow'):
                self.capture()
            self.assert_harness_reaped(handles)
            self.assertGreater(self.life.rows[0]['stdout_bytes'], lifecycle.LIMIT)
            self.assertLessEqual(self.life.rows[0]['stdout_bytes'], lifecycle.LIMIT + 65536)
            self.assertLess(len(self.life.journal_bytes), 16384)

    def test_harness_injected_wait_deadline_retires_closed_pipe_sleeping_handle_without_long_wait(self):
        error = subprocess.TimeoutExpired('synthetic-owned-stand-in', 45, output=PRIVATE)
        with self.standin('import os,time; os.close(1); os.close(2); time.sleep(30)', wait_error=error) as handles:
            with self.assertRaises(subprocess.TimeoutExpired) as caught:
                self.capture()
            self.assertIs(error, caught.exception)
            self.assert_harness_reaped(handles)
            self.assertEqual('command-timeout', self.life.rows[0]['primary_error']['code'])
            self.assertLess(self.life.rows[0]['elapsed_seconds'], 20)

    def test_harness_injected_wait_cancellation_retires_closed_pipe_sleeping_handle_without_global_signal(self):
        error = KeyboardInterrupt(PRIVATE)
        with self.standin('import os,time; os.close(1); os.close(2); time.sleep(30)', wait_error=error) as handles:
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.capture()
            self.assertIs(error, caught.exception)
            self.assert_harness_reaped(handles)
            self.assertEqual('cancelled', self.life.rows[0]['primary_error']['code'])
            self.assertLess(self.life.rows[0]['elapsed_seconds'], 20)


if __name__ == '__main__':
    unittest.main()
