"""Portable sensitivity for the isolated native-cancellation control, not native success."""
import unittest
import tempfile
from pathlib import Path
from unittest.mock import Mock

import swiftbuild_worker_cancellation as control
from scripts.ci.darwin_worker_identity import ProcessIdentity


class SwiftBuildCancellationControls(unittest.TestCase):
    def boundary(self, *, startup=13.207428, acquisition=0.2, ended=False, oversized=False):
        now = [0.0]
        child = Mock(); child.poll.return_value = 1 if ended else None
        ready = Mock(); ready.is_file.side_effect = lambda: now[0] >= startup
        log = Mock(); log.stat.return_value.st_size = 1024 * 1024 + 1 if oversized else 0
        observed = []
        target = object()

        def observe():
            observed.append(now[0])
            return target if now[0] >= startup + acquisition else None

        def sleep(seconds):
            now[0] += seconds

        return child, ready, log, observe, lambda: now[0], sleep, target, observed

    def test_cold_startup_does_not_consume_holding_acquisition_budget(self):
        child, ready, log, observe, clock, sleep, target, observed = self.boundary()
        actual, timing = control.acquire_at_holding_boundary(
            child, ready, log, observe, clock=clock, sleep=sleep)
        self.assertIs(actual, target)
        self.assertGreater(timing['startup_seconds'], 13.2)
        self.assertLess(timing['acquisition_seconds'], 1)
        self.assertEqual(timing['acquisition_limit_seconds'], 10)
        self.assertTrue(all(t >= 13.207428 for t in observed))

    def test_missing_marker_late_service_exited_client_and_oversize_still_fail(self):
        for values, expected in ((dict(startup=30.1), 'startup'),
                                 (dict(acquisition=10.2), 'holding boundary'),
                                 (dict(ended=True), 'ended'), (dict(oversized=True), 'output exceeded')):
            with self.subTest(values=values):
                child, ready, log, observe, clock, sleep, _, observed = self.boundary(**values)
                with self.assertRaisesRegex(RuntimeError, expected):
                    control.acquire_at_holding_boundary(child, ready, log, observe, clock=clock, sleep=sleep)
                if 'acquisition' not in values:
                    self.assertEqual(observed, [])

    def test_a_slow_native_observation_cannot_escape_the_ten_second_limit(self):
        child, ready, log, _, clock, sleep, target, _ = self.boundary(startup=0)
        def slow():
            sleep(10)
            return target
        with self.assertRaisesRegex(RuntimeError, 'acquisition budget'):
            control.acquire_at_holding_boundary(child, ready, log, slow, clock=clock, sleep=sleep)

    def test_retirement_preserves_receipts_and_refuses_redirected_data(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp).resolve()
            (output / 'fixture').mkdir()
            (output / 'fixture' / 'raw').write_text('public fixture')
            (output / 'private-build-output.log').write_text('synthetic log')
            receipt = output / 'swiftbuild-cancellation-result.json'
            receipt.write_text('retain exact receipt')
            self.assertEqual(control.retire_fixture(output)['result'], 'PASS')
            self.assertEqual(receipt.read_text(), 'retain exact receipt')
            self.assertEqual(list(output.iterdir()), [receipt])
            (output / 'fixture').symlink_to(output, target_is_directory=True)
            (output / 'private-build-output.log').write_text('retain on failure')
            with self.assertRaisesRegex(RuntimeError, 'Redirected'):
                control.retire_fixture(output)
            self.assertEqual(receipt.read_text(), 'retain exact receipt')

    def environment(self):
        return dict(GITHUB_ACTIONS='true', RUNNER_ENVIRONMENT='github-hosted', GITHUB_REPOSITORY='Apdelrahman1911/parlor',
                    GITHUB_REF=control.BRANCH, GITHUB_EVENT_NAME='push', GITHUB_JOB='ios-release-simulator',
                    DEVELOPER_DIR=control.DEVELOPER, RUNNER_ARCH='ARM64', GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1',
                    GITHUB_SHA='a' * 40, GITHUB_WORKFLOW_SHA='a' * 40)

    def test_context_is_exact_and_supports_only_the_two_reviewed_host_architectures(self):
        for machine, arch in [('arm64', 'ARM64'), ('x86_64', 'X64')]:
            env = dict(self.environment(), RUNNER_ARCH=arch)
            self.assertEqual(control.validate_environment(env, 'Darwin', machine), arch)
        for key in self.environment():
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                control.validate_environment(dict(self.environment(), **{key: 'other'}), 'Darwin', 'arm64')
        with self.assertRaises(RuntimeError): control.validate_environment(self.environment(), 'Linux', 'arm64')

    def test_public_build_is_an_aggregate_fixture_not_application_or_private_service_argv(self):
        command = control.command(Path('/public/fixture'))
        self.assertEqual(command[0], '/usr/bin/xcodebuild')
        self.assertEqual(command[-1], 'build')
        self.assertIn('PBXAggregateTarget', control.PROJECT)
        for forbidden in ('PBXNativeTarget', 'ComposeApp', 'iosApp', 'simctl', '-destination', 'SWBBuildService'):
            self.assertNotIn(forbidden, control.PROJECT + ' '.join(command))
        self.assertIn('/bin/sleep 30', control.PROJECT)
        self.assertIn('$(OBJROOT)/parlor-worker-ready', control.PROJECT)

    def test_service_observation_requires_new_uid_origin_executable_marker_and_generation(self):
        app = Path('/Applications/Xcode_26.3.app')
        value = ProcessIdentity(100, 501, (2, 0), str(app / control.SERVICE), (0, 501, 0, 501, 0, 100, 0, 1))
        claim = dict(application=str(app), baseline=[], uid=501, not_before_us=1000000)
        backend = Mock(); backend.snapshot.return_value = [value]; backend.tracking_id.return_value = 'exact'; backend.read.return_value = value
        self.assertEqual(control.attest_service(claim, backend, 'exact'), value)
        for changed in (value._replace(uid=502), value._replace(started=(0, 999999)), value._replace(command=str(app / 'Contents/other'))):
            backend.snapshot.return_value = [changed]
            self.assertIsNone(control.attest_service(claim, backend, 'exact'))
        backend.snapshot.return_value = [value]
        self.assertIsNone(control.attest_service(dict(claim, baseline=[value.lifetime]), backend, 'exact'))
        backend.tracking_id.return_value = 'prefixexact'
        self.assertIsNone(control.attest_service(claim, backend, 'exact'))
        backend.tracking_id.return_value = 'exact'; backend.read.return_value = value._replace(token=value.token[:-1] + (2,))
        self.assertIsNone(control.attest_service(claim, backend, 'exact'))
        backend.signal.assert_not_called()


if __name__ == '__main__':
    unittest.main()
