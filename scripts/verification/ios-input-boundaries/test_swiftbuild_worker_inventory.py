"""New isolated inventory controls; neither synthetic signatures nor absence authorize cleanup."""
import hashlib
import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import Mock

import swiftbuild_worker_inventory as inventory


class SwiftBuildInventoryControls(unittest.TestCase):
    def environment(self):
        return dict(GITHUB_ACTIONS='true', RUNNER_ENVIRONMENT='github-hosted',
                    GITHUB_REPOSITORY='Apdelrahman1911/parlor', GITHUB_REF=inventory.BRANCH,
                    GITHUB_EVENT_NAME='push', GITHUB_JOB='inventory', DEVELOPER_DIR=inventory.DEVELOPER,
                    GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1', GITHUB_SHA='a' * 40,
                    GITHUB_WORKFLOW_SHA='a' * 40, RUNNER_ARCH='ARM64')

    def test_both_explicit_standard_architectures_and_only_the_closed_context_pass(self):
        for machine, architecture in [('arm64', 'ARM64'), ('x86_64', 'X64')]:
            env = self.environment(); env['RUNNER_ARCH'] = architecture
            self.assertEqual(inventory.validate_environment(env, 'Darwin', machine), architecture)
        env = self.environment()
        for key in env:
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                inventory.validate_environment(dict(env, **{key: 'other'}), 'Darwin', 'arm64')
        for system, machine in [('Linux', 'arm64'), ('Darwin', 'other'), ('Darwin', 'x86_64')]:
            with self.subTest(system=system, machine=machine), self.assertRaises(RuntimeError):
                inventory.validate_environment(env, system, machine)

    def test_dirty_wrong_sha_and_workflow_source_mismatch_reject(self):
        for results in ([b'M source\n'], [b'', b'b' * 40]):
            with self.subTest(results=results), self.assertRaises(RuntimeError):
                inventory.source_binding(Path('.'), self.environment(), Mock(side_effect=results))
        run = Mock(side_effect=[b'', b'a' * 40, b'c' * 40])
        self.assertEqual(inventory.source_binding(Path('.'), self.environment(), run),
                         dict(head='a' * 40, tree='c' * 40))
        self.assertTrue(all(c.kwargs['timeout'] == 30 for c in run.call_args_list))

    def test_absent_helper_never_guesses_an_alternative_or_grants_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); inspect = Mock()
            result = inventory.collect(root, inspect)
            self.assertEqual(result['result'], 'COMPONENT_ABSENT')
            self.assertFalse(result['cleanup_authorized']); self.assertEqual(result['components'], [])
            inspect.assert_not_called()

    def test_exact_inspection_retains_bound_bytes_and_signature_failures(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); path = root / inventory.RELATIVE
            path.parent.mkdir(parents=True); path.write_bytes(b'public fixture'); path.chmod(0o700)
            for exit_code in (0, 1):
                run = Mock(return_value=CompletedProcess([], exit_code, b'', b'Identifier=com.apple.fixture\n'))
                inspect = lambda app, candidate: inventory.inspect_component(app, candidate, run)
                result = inventory.collect(root, inspect)
                self.assertEqual(result['result'], 'SIGNED_COMPONENT_OBSERVED' if exit_code == 0 else 'SIGNATURE_REJECTED')
                self.assertEqual(result['components'][0]['sha256'], hashlib.sha256(b'public fixture').hexdigest())
                self.assertFalse(result['cleanup_authorized'])

    def test_redirected_application_component_and_wrong_inspector_output_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve(); app = root / 'Xcode.app'; app.mkdir()
            alias = root / 'alias'; alias.symlink_to(app, target_is_directory=True)
            with self.assertRaises(RuntimeError): inventory.collect(alias)
            candidate = app / inventory.RELATIVE; candidate.parent.mkdir(parents=True)
            target = app / 'public'; target.write_bytes(b'public'); candidate.symlink_to(target)
            with self.assertRaises(ValueError): inventory.collect(app)
            candidate.unlink(); candidate.write_bytes(b'public')
            for row in [dict(relative_executable='Contents/other', cleanup_authorized=False),
                        dict(relative_executable=inventory.RELATIVE, cleanup_authorized=True)]:
                with self.assertRaises(RuntimeError): inventory.collect(app, Mock(return_value=row))


if __name__ == '__main__':
    unittest.main()
