"""Portable sensitivity for the isolated native-cancellation control, not native success."""
import unittest
from pathlib import Path
from unittest.mock import Mock

import swiftbuild_worker_cancellation as control
from scripts.ci.darwin_worker_identity import ProcessIdentity


class SwiftBuildCancellationControls(unittest.TestCase):
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
