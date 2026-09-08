"""Synthetic boundary checks only; never invokes Gradle, simctl, or a real ps.

Run by the root-owned lane. All device inventories and command failures below
are fake, and all filesystem writes live in an automatically removed tempdir.
These checks are not evidence of native tests, CoreSimulator timing, or devices.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


CONTROLS = Path(__file__).resolve().parents[1]


def load_control(name, filename):
    spec = importlib.util.spec_from_file_location(name, CONTROLS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SIMULATOR = load_control('synthetic_owned_simulator', 'owned_ios_simulator.py')
RUNNER = load_control('synthetic_gradle_lane', 'run_gradle_cycle.py')
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'
USER_UUID = '11111111-1111-4111-8111-111111111111'
OWNED_UUID = '22222222-2222-4222-8222-222222222222'
OTHER_UUID = '33333333-3333-4333-8333-333333333333'
PRIVATE_MARKER = 'SYNTHETIC unrelated private simulator name'


class FakeSimctl:
    def __init__(self):
        self.rows = {RUNTIME: [{'udid': USER_UUID, 'name': PRIVATE_MARKER, 'state': 'Booted'}]}
        self.calls = []
        self.overrides = {}
        self.create_stdout = OWNED_UUID + '\n'

    def invoke(self, command, logfile, environment, timeout=None):
        label = Path(logfile).stem
        self.calls.append((label, list(command), timeout))
        override = self.overrides.get(label)
        if override is not None:
            return override(command, Path(logfile), environment, timeout)
        return self.default(command, Path(logfile), environment, timeout)

    def default(self, command, logfile, environment, timeout):
        if command == ['xcrun', 'simctl', 'list', 'devices', '-j']:
            logfile.write_text(json.dumps({'devices': self.rows}))
        elif command[:3] == ['xcrun', 'simctl', 'create']:
            self.rows.setdefault(command[5], []).append({
                'udid': OWNED_UUID, 'name': command[3], 'state': 'Shutdown',
            })
            logfile.write_text(self.create_stdout)
        elif command[:3] in (['xcrun', 'simctl', 'boot'], ['xcrun', 'simctl', 'shutdown']):
            if command[3] != OWNED_UUID:
                raise AssertionError('A pre-existing or unattested simulator was touched')
            for row in self.rows[RUNTIME]:
                if row['udid'] == OWNED_UUID:
                    row['state'] = 'Booted' if command[2] == 'boot' else 'Shutdown'
            logfile.write_text('synthetic command success\n')
        elif command[:3] == ['xcrun', 'simctl', 'bootstatus']:
            if command[3:] != [OWNED_UUID, '-b']:
                raise AssertionError('Unexpected bootstatus target')
            logfile.write_text('synthetic boot complete\n')
        elif command[:3] == ['xcrun', 'simctl', 'delete']:
            if command[3:] != [OWNED_UUID]:
                raise AssertionError('A pre-existing or unattested simulator was deleted')
            self.rows[RUNTIME] = [row for row in self.rows[RUNTIME] if row['udid'] != OWNED_UUID]
            logfile.write_text('synthetic delete complete\n')
        else:
            raise AssertionError(f'Unreviewed synthetic command: {command}')
        return 0

    def fail(self, label, failure, after_effect=False, once=False):
        def override(command, logfile, environment, timeout):
            if once:
                self.overrides.pop(label)
            if after_effect:
                self.default(command, logfile, environment, timeout)
            else:
                logfile.write_text(PRIVATE_MARKER)
            if isinstance(failure, BaseException):
                raise failure
            return failure
        self.overrides[label] = override

    def destructive_calls(self):
        return [command for _, command, _ in self.calls if command[2] in ('shutdown', 'delete')]


class OwnedSimulatorSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-synthetic-owner-')
        self.addCleanup(self.temporary.cleanup)
        self.destination = Path(self.temporary.name)
        self.fake = FakeSimctl()
        self.environment = {}
        # Any unexpected process launch is a hard failure, never a real command.
        self.popen = mock.patch.object(subprocess, 'Popen', side_effect=AssertionError('Real process forbidden'))
        self.popen.start()
        self.addCleanup(self.popen.stop)
        self.ps_patch = mock.patch.object(subprocess, 'check_output', return_value='')
        self.ps = self.ps_patch.start()
        self.addCleanup(self.ps_patch.stop)
        self.owner = SIMULATOR.OwnedIosSimulator(
            self.destination, 'synthetic', self.environment, self.fake.invoke,
        )

    def assert_no_inventory_logs(self):
        self.assertEqual(list((self.destination / 'simulator').glob('inventory-*.log')), [])

    def test_success_targets_only_new_uuid_and_preserves_user_state(self):
        before = copy.deepcopy(self.fake.rows[RUNTIME][0])
        self.owner.create_and_boot()
        self.assertEqual(self.environment['PARLOR_REMEDIATION_SIMULATOR_UDID'], OWNED_UUID)
        self.owner.cleanup()
        self.assertEqual(self.fake.rows[RUNTIME], [before])
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertTrue(self.owner.receipt['preexisting_devices_preserved'])
        self.assertEqual(self.owner.receipt['cleanup_errors'], [])
        self.assert_no_inventory_logs()
        for path in (self.destination / 'simulator').iterdir():
            self.assertNotIn(PRIVATE_MARKER, path.read_text())
        self.ps.assert_called_once_with(['ps', '-axo', 'pid=,command='], text=True, timeout=10)

    def test_stdout_cannot_select_a_preexisting_uuid(self):
        self.fake.create_stdout = USER_UUID + '\n'
        self.owner.create_and_boot()
        self.assertEqual(self.owner.device, OWNED_UUID)
        self.owner.cleanup()

    def test_preexisting_name_collision_refuses_creation(self):
        self.fake.rows[RUNTIME][0]['name'] = self.owner.name
        with self.assertRaisesRegex(RuntimeError, 'pre-existing simulator name'):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertFalse(self.owner.creation_attempted)
        self.assertEqual(self.fake.destructive_calls(), [])
        self.assertEqual([call[0] for call in self.fake.calls], ['inventory-before'])

    def test_nonzero_inventory_command_removes_partial_private_log(self):
        self.fake.fail('inventory-before', 7)
        with self.assertRaises(RuntimeError):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assert_no_inventory_logs()
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_timed_out_inventory_command_removes_partial_private_log(self):
        self.fake.fail('inventory-before', subprocess.TimeoutExpired(['synthetic-inventory'], 1))
        with self.assertRaises(subprocess.TimeoutExpired):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assert_no_inventory_logs()

    def test_inventory_failure_before_log_creation_preserves_original_error(self):
        def fail_before_log(*unused):
            raise FileNotFoundError('synthetic missing command')
        self.fake.overrides['inventory-before'] = fail_before_log
        with self.assertRaisesRegex(FileNotFoundError, 'synthetic missing command'):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assert_no_inventory_logs()

    def test_malformed_inventory_removes_partial_private_log(self):
        self.fake.fail('inventory-before', 0)
        with self.assertRaises(json.JSONDecodeError):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assert_no_inventory_logs()

    def test_create_timeout_after_allocation_is_recovered_and_deleted(self):
        self.fake.fail('create', subprocess.TimeoutExpired(['synthetic-create'], 1), after_effect=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertEqual(self.owner.device, OWNED_UUID)
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertEqual(len(self.fake.destructive_calls()), 1)  # Never booted; delete only.

    def test_create_failure_without_allocation_does_not_delete_anything(self):
        self.fake.fail('create', 1)
        with self.assertRaises(RuntimeError):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertIsNone(self.owner.device)
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_initial_attestation_failure_is_recovered_during_cleanup(self):
        self.fake.fail('inventory-created', 1)
        with self.assertRaises(RuntimeError):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertEqual(self.owner.device, OWNED_UUID)
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assert_no_inventory_logs()

    def test_boot_failure_still_deletes_attested_device(self):
        self.fake.fail('boot', 1)
        with self.assertRaises(RuntimeError):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertTrue(self.owner.receipt['owned_device_absent'])

    def test_bootstatus_cancellation_still_shuts_down_and_deletes(self):
        self.fake.fail('bootstatus', KeyboardInterrupt('synthetic cancellation'))
        with self.assertRaises(KeyboardInterrupt):
            self.owner.create_and_boot()
        self.owner.cleanup()
        self.assertEqual([command[2] for command in self.fake.destructive_calls()], ['shutdown', 'delete'])

    def test_ambiguous_new_name_never_selects_arbitrary_device(self):
        def duplicate(command, logfile, environment, timeout):
            self.fake.default(command, logfile, environment, timeout)
            self.fake.rows[RUNTIME].append({'udid': OTHER_UUID, 'name': self.owner.name, 'state': 'Shutdown'})
            return 0
        self.fake.overrides['create'] = duplicate
        with self.assertRaisesRegex(RuntimeError, 'Ambiguous'):
            self.owner.create_and_boot()
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertFalse(self.owner.receipt['owned_device_absent'])
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_invalid_inventory_uuid_never_reaches_destructive_command(self):
        def invalid(command, logfile, environment, timeout):
            self.fake.default(command, logfile, environment, timeout)
            self.fake.rows[RUNTIME][-1]['udid'] = 'not-a-uuid'
            return 0
        self.fake.overrides['create'] = invalid
        with self.assertRaisesRegex(RuntimeError, 'invalid UUID'):
            self.owner.create_and_boot()
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_preexisting_uuid_cannot_be_reclaimed_by_new_name(self):
        def reused(command, logfile, environment, timeout):
            self.fake.default(command, logfile, environment, timeout)
            self.fake.rows[RUNTIME][-1]['udid'] = USER_UUID
            return 0
        self.fake.overrides['create'] = reused
        with self.assertRaisesRegex(RuntimeError, 'not found by ownership identity'):
            self.owner.create_and_boot()
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertIsNone(self.owner.device)
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_matching_name_in_other_runtime_does_not_attest_ownership(self):
        def other_runtime(command, logfile, environment, timeout):
            self.fake.default(command, logfile, environment, timeout)
            created = self.fake.rows[RUNTIME].pop()
            self.fake.rows['synthetic-other-runtime'] = [created]
            return 0
        self.fake.overrides['create'] = other_runtime
        with self.assertRaisesRegex(RuntimeError, 'not found by ownership identity'):
            self.owner.create_and_boot()
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertIsNone(self.owner.device)
        self.assertEqual(self.fake.destructive_calls(), [])

    def test_changed_attested_uuid_refuses_deleting_replacement(self):
        self.owner.create_and_boot()
        self.fake.rows[RUNTIME][-1]['udid'] = OTHER_UUID
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertEqual(self.fake.destructive_calls(), [])
        self.assertFalse(self.owner.receipt['owned_device_absent'])

    def test_renamed_owned_device_is_not_silently_forgotten(self):
        self.owner.create_and_boot()
        self.fake.rows[RUNTIME][-1]['name'] = 'synthetic external rename'
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertEqual(self.fake.destructive_calls(), [])
        self.assertFalse(self.owner.receipt['owned_device_absent'])

    def test_failed_cleanup_attestation_does_not_guess_a_device(self):
        self.owner.create_and_boot()
        self.fake.fail('inventory-cleanup', 1)
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertEqual(self.fake.destructive_calls(), [])
        self.assertFalse(self.owner.receipt['owned_device_absent'])
        self.assert_no_inventory_logs()

    def test_failed_shutdown_does_not_skip_delete_or_hide_failure(self):
        self.owner.create_and_boot()
        self.fake.fail('shutdown', 1)
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertEqual([command[2] for command in self.fake.destructive_calls()], ['shutdown', 'delete'])

    def test_cancelled_shutdown_does_not_skip_later_finalizers(self):
        self.owner.create_and_boot()
        self.fake.fail('shutdown', KeyboardInterrupt('synthetic cleanup cancellation'))
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertTrue(self.owner.receipt['preexisting_devices_preserved'])
        self.assertEqual([command[2] for command in self.fake.destructive_calls()], ['shutdown', 'delete'])

    def test_failed_delete_preserves_explicit_cleanup_failure(self):
        self.owner.create_and_boot()
        self.fake.fail('delete', 1)
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertFalse(self.owner.receipt['owned_device_absent'])

    def test_failed_final_inventory_is_unverified_not_success(self):
        self.owner.create_and_boot()
        self.fake.fail('inventory-final', subprocess.TimeoutExpired(['synthetic-inventory'], 1))
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertFalse(self.owner.receipt['owned_device_absent'])
        self.assert_no_inventory_logs()

    def test_external_loss_of_preexisting_device_is_not_claimed_preserved(self):
        self.owner.create_and_boot()
        self.fake.rows[RUNTIME] = [row for row in self.fake.rows[RUNTIME] if row['udid'] != USER_UUID]
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertTrue(self.owner.receipt['owned_device_absent'])
        self.assertFalse(self.owner.receipt['preexisting_devices_preserved'])

    def test_uuid_process_leftovers_are_failure_without_broad_killing(self):
        self.owner.create_and_boot()
        self.ps.return_value = f'12345 synthetic-owned-worker --device {OWNED_UUID}\n'
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertEqual(len(self.owner.receipt['remaining_owned_uuid_processes']), 1)

    def test_process_scan_timeout_is_explicit_cleanup_failure(self):
        self.owner.create_and_boot()
        self.ps.side_effect = subprocess.TimeoutExpired(['synthetic-ps'], 10)
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertTrue(any('TimeoutExpired' in error for error in self.owner.receipt['cleanup_errors']))

    def test_device_appearing_only_at_final_inventory_is_failure_not_absence(self):
        # This models one adversarial boundary; it does not assert actual
        # CoreSimulator allocation timing or prove absence after the last sample.
        self.fake.fail('create', 1)
        with self.assertRaises(RuntimeError):
            self.owner.create_and_boot()
        def late(command, logfile, environment, timeout):
            self.fake.rows[RUNTIME].append({'udid': OWNED_UUID, 'name': self.owner.name, 'state': 'Shutdown'})
            return self.fake.default(command, logfile, environment, timeout)
        self.fake.overrides['inventory-final'] = late
        with self.assertRaises(RuntimeError):
            self.owner.cleanup()
        self.assertFalse(self.owner.receipt['owned_device_absent'])
        self.assertEqual(self.fake.destructive_calls(), [])


class RunnerIdentitySafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-synthetic-controls-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.controls = self.root / 'controls'
        self.controls.mkdir()
        self.names = ['run_gradle_cycle.py', 'owned_ios_simulator.py', 'owned_ios_simulator.init.gradle']
        for name in self.names:
            (self.controls / name).write_text('synthetic ' + name)
        for attribute, value in [('ROOT', self.root), ('OUT', self.controls)]:
            patch = mock.patch.object(RUNNER, attribute, value)
            patch.start()
            self.addCleanup(patch.stop)

    def test_native_manifest_binds_exact_three_control_files(self):
        result = RUNNER.runner_identity(True)
        self.assertEqual([path for path, _ in result['manifest']], ['controls/' + name for name in self.names])
        for path, digest in result['manifest']:
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_non_native_manifest_does_not_require_native_controls(self):
        for name in self.names[1:]:
            (self.controls / name).unlink()
        result = RUNNER.runner_identity(False)
        self.assertEqual([path for path, _ in result['manifest']], ['controls/run_gradle_cycle.py'])

    def test_changed_init_changes_manifest_identity(self):
        before = RUNNER.runner_identity(True)
        (self.controls / self.names[-1]).write_text('synthetic changed device binding')
        after = RUNNER.runner_identity(True)
        self.assertNotEqual(before['manifest_sha256'], after['manifest_sha256'])

    def test_missing_native_control_fails_closed(self):
        (self.controls / self.names[-1]).unlink()
        with self.assertRaisesRegex(RuntimeError, 'Missing or symlinked'):
            RUNNER.runner_identity(True)

    def test_symlinked_native_control_fails_closed(self):
        path = self.controls / self.names[-1]
        path.unlink()
        path.symlink_to(self.controls / self.names[0])
        with self.assertRaisesRegex(RuntimeError, 'Missing or symlinked'):
            RUNNER.runner_identity(True)


if __name__ == '__main__':
    unittest.main()
