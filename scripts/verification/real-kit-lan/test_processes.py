"""Synthetic OS processes only: no P2pKit, LAN, Gradle or user identities."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('process_probe', Path(__file__).with_name('processes.py'))
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)

FAKE_ENDPOINT = '''import sys,time
role,mode=sys.argv[1:]
if role=='host':
    if mode=='no-code': time.sleep(60)
    print('ROOM_CODE AAAAAA',flush=True)
    if mode=='duplicate': print('ROOM_CODE BBBBBB',flush=True)
    if mode=='failure': print('ENDPOINT_FAIL private-detail',flush=True);sys.exit(1)
    if mode=='missing-teardown': time.sleep(.4)
    else: time.sleep(.7)
    print('AUTHENTICATED_ACTOR_PASS',flush=True)
    print('PEER_DISCONNECT_PASS',flush=True)
    if mode=='resume':
        print('FROZEN_ROSTER_PASS',flush=True)
        print('PHYSICAL_CLOSE_PASS',flush=True)
        print('REJOIN_SAME_SEAT_PASS',flush=True)
        time.sleep(.5)
else:
    if sys.stdin.readline().strip()!='AAAAAA': sys.exit(2)
    if mode=='overflow': print('x'*2097152,flush=True)
    if mode=='stall': time.sleep(60)
    if mode=='resume':
        if sys.stdin.readline().strip()!='CLOSE_OBSERVED': sys.exit(3)
        for marker in ('SELECTED_GAME_REJECTION_PASS','SELECTED_VERSION_REJECTION_PASS',
                       'SAME_SEAT_RESUME_PASS','ROTATED_CREDENTIAL_PASS','REVOKED_MEMBERSHIP_PASS'):
            print(marker,flush=True)
    print('BIDIRECTIONAL_PASS',flush=True)
print('ENDPOINT_PASS',flush=True)
if mode!='missing-teardown': print('TEARDOWN_PASS',flush=True)
'''


class ProcessPreconditionTest(unittest.TestCase):
    def test_network_consent_is_required_before_preparation(self):
        with patch.object(probe, 'prepare') as prepare:
            with self.assertRaisesRegex(RuntimeError, 'allow-local-lan'):
                probe.main([])
            prepare.assert_not_called()

    def test_invalid_case_count_does_not_execute_tools(self):
        with patch.object(probe.probe, 'check_environment') as environment:
            for count in ('0', '4', '-1'):
                with self.subTest(count=count), self.assertRaises(SystemExit):
                    probe.main(['--allow-local-lan', '--iterations', count])
            environment.assert_not_called()

    def test_scenario_selects_only_allowlisted_compiled_endpoints(self):
        for scenario, endpoint in probe.SCENARIOS.items():
            command = probe.endpoint_command('peer', '0' * 32, 'classpath', scenario)
            self.assertEqual(command, ['java', '-Xmx512m', '-cp', 'classpath',
                                      'com.parlor.transport.p2p.' + endpoint, 'peer', '0' * 32])
        with self.assertRaisesRegex(RuntimeError, 'Unsupported'):
            probe.endpoint_command('peer', '0' * 32, 'classpath', 'untrusted-class')

    def test_invalid_scenario_never_prepares_or_spawns(self):
        with patch.object(probe, 'prepare') as prepare, patch.object(probe.subprocess, 'Popen') as spawn:
            with self.assertRaises(SystemExit):
                probe.main(['--allow-local-lan', '--scenario', 'untrusted-class'])
            with self.assertRaisesRegex(RuntimeError, 'Unsupported'):
                probe.run_pair('', Path('/unused'), time.monotonic() + 10, 'untrusted-class')
            prepare.assert_not_called()
            spawn.assert_not_called()

    def test_missing_or_ambiguous_advertisement_is_not_accepted(self):
        self.assertIsNone(probe.advertised_code(b'not a room code\n'))
        self.assertIsNone(probe.advertised_code(b'ROOM_CODE secret-and-invalid\n'))
        self.assertEqual(probe.advertised_code(b'ROOM_CODE AAAAAA\n'), b'AAAAAA')
        with self.assertRaisesRegex(RuntimeError, 'Ambiguous'):
            probe.advertised_code(b'ROOM_CODE AAAAAA\nROOM_CODE BBBBBB\n')

    def test_log_read_is_bounded_before_full_materialization(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / 'log'
            log.write_bytes(b'x' * (probe.LOG_LIMIT + 1))
            with self.assertRaisesRegex(RuntimeError, 'output bound'):
                probe.bounded_log(log)

    def test_helper_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'real').write_text('public fixture')
            (root / 'helper').symlink_to(root / 'real')
            with patch.object(probe, 'ROOT', root), patch.object(probe, 'HELPERS', ('helper',)):
                with self.assertRaisesRegex(RuntimeError, 'Redirected'):
                    probe.helper_hashes(time.monotonic() + 10)

    def test_expired_deadline_does_not_spawn_a_process(self):
        with patch.object(probe.subprocess, 'Popen') as spawn:
            with self.assertRaisesRegex(RuntimeError, 'Total probe'):
                probe.run_pair('', Path('/unused'), time.monotonic() - 1)
            spawn.assert_not_called()

    def test_preparation_failure_stops_only_its_private_registry(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary)
            with patch.object(probe.probe.owned, 'run_logged', side_effect=[1, 0]) as run:
                with self.assertRaisesRegex(RuntimeError, 'compilation failed'):
                    probe.prepare(out, time.monotonic() + 100)
            self.assertEqual(run.call_count, 2)
            prepare, stop = run.call_args_list
            self.assertIn('--dependency-verification=strict', prepare.args[0])
            self.assertIn('--no-configuration-cache', prepare.args[0])
            self.assertIn('-Dorg.gradle.daemon.registry.base=' + str(out / 'owned-gradle-daemons'), stop.args[0])
            self.assertIn('--stop', stop.args[0])
            self.assertEqual(stop.kwargs['timeout'], 60)


@unittest.skipUnless(os.name == 'posix', 'POSIX ownership fixtures; opt-in runtime is Linux-only')
class OwnedProcessPairTest(unittest.TestCase):
    def exercise(self, mode, *, budget=5, expected_error=None, interrupt=False, hash_error=False, scenario='admission'):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            endpoint = root / 'endpoint.py'
            endpoint.write_text(FAKE_ENDPOINT)
            processes = []
            original_spawn = subprocess.Popen
            def spawn(*args, **kwargs):
                result = original_spawn(*args, **kwargs)
                processes.append(result)
                return result
            command = lambda role, namespace, classpath, selected='admission': [sys.executable, str(endpoint), role, mode]
            with patch.object(probe, 'endpoint_command', side_effect=command), patch.object(probe.subprocess, 'Popen', side_effect=spawn):
                sampler = patch.object(probe.probe, 'sample_process', side_effect=KeyboardInterrupt()) if interrupt else patch.object(probe.probe, 'sample_process')
                with sampler:
                    hashing = patch.object(probe.probe, 'sha256', side_effect=OSError()) if hash_error else patch.object(probe.probe, 'sha256', wraps=probe.probe.sha256)
                    with hashing:
                        if expected_error:
                            with self.assertRaises(expected_error):
                                probe.run_pair('', root / 'pair', time.monotonic() + budget, scenario)
                        else:
                            receipt = probe.run_pair('', root / 'pair', time.monotonic() + budget, scenario)
                            self.assertEqual(receipt['status'], 'passed')
            self.assertTrue(processes)
            self.assertTrue(all(p.poll() is not None and p.stdin.closed for p in processes))
            retained = json.loads((root / 'pair/receipt.json').read_text())
            self.assertEqual(retained['status'], 'failed' if expected_error else 'passed')
            self.assertNotIn('private-detail', json.dumps(retained))
            return retained, processes

    def test_success_uses_two_distinct_naturally_exited_processes(self):
        receipt, children = self.exercise('success')
        self.assertEqual(len(children), 2)
        self.assertNotEqual(children[0].pid, children[1].pid)
        self.assertTrue(all(row['natural_exit'] and row['final_exit_code'] == 0 and row['cleanup_completed'] for row in receipt['roles'].values()))

    def test_resume_requires_host_observed_close_and_all_resume_assertions(self):
        receipt, children = self.exercise('resume', scenario='resume')
        self.assertEqual(receipt['scenario'], 'resume')
        self.assertEqual(len(children), 2)
        self.assertTrue(all(row['natural_exit'] and row['exit_code'] == 0 for row in receipt['roles'].values()))

    def test_admission_success_cannot_qualify_a_resume_pair(self):
        retained, children = self.exercise('success', scenario='resume', expected_error=RuntimeError)
        self.assertEqual(retained['scenario'], 'resume')
        self.assertEqual(len(children), 2)
        self.assertTrue(all(row['cleanup_completed'] for row in retained['roles'].values()))

    def test_missing_teardown_is_failure_not_a_pass(self):
        self.exercise('missing-teardown', expected_error=RuntimeError)

    def test_child_failure_is_retained_without_transcript_in_receipt(self):
        self.exercise('failure', expected_error=RuntimeError)

    def test_missing_advertisement_expires_without_launching_peer(self):
        _, children = self.exercise('no-code', budget=.3, expected_error=RuntimeError)
        self.assertEqual(len(children), 1)

    def test_pair_timeout_cleans_both_processes(self):
        _, children = self.exercise('stall', budget=.5, expected_error=RuntimeError)
        self.assertEqual(len(children), 2)

    def test_output_overflow_fails_and_retires_both_processes(self):
        self.exercise('overflow', expected_error=RuntimeError)

    def test_interruption_is_preserved_after_owned_cleanup(self):
        self.exercise('no-code', interrupt=True, expected_error=KeyboardInterrupt)

    def test_evidence_hash_failure_cannot_skip_the_other_process_cleanup(self):
        _, children = self.exercise('success', hash_error=True, expected_error=RuntimeError)
        self.assertEqual(len(children), 2)


if __name__ == '__main__':
    unittest.main()
