"""Synthetic portability/ownership guards; no Java, Gradle or SDK is executed."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
LANE = ROOT / 'remediation-runs/2026-09-07-local-readiness/run_gradle_cycle.py'
SPEC = importlib.util.spec_from_file_location('parlor_portable_gradle_lane', LANE)
lane = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lane)


class GradleLanePortabilityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='lane-portability-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.home = self.root / 'jdk21'
        (self.home / 'bin').mkdir(parents=True)
        (self.home / 'release').write_text('JAVA_VERSION="21.0.8"\n')
        for name in ('java', 'javac'):
            binary = self.home / 'bin' / name
            binary.write_text('synthetic fixture; never execute\n')
            binary.chmod(0o700)
        self.env = {'JAVA_HOME': str(self.home)}

    def test_linux_requires_explicit_java_home_without_path_fallback(self):
        with mock.patch.object(lane.sys, 'platform', 'linux'), mock.patch.object(lane.subprocess, 'check_output') as spawn:
            with self.assertRaises(RuntimeError):
                lane.jdk21_home({'PATH': str(self.home / 'bin')})
            spawn.assert_not_called()

    def test_linux_static_preflight_spawns_nothing(self):
        with mock.patch.object(lane.sys, 'platform', 'linux'), mock.patch.object(lane.subprocess, 'check_output') as spawn:
            self.assertEqual(lane.jdk21_home(self.env), str(self.home))
            spawn.assert_not_called()

    def test_linux_java_home_must_be_absolute(self):
        with mock.patch.object(lane.sys, 'platform', 'linux'), self.assertRaises(RuntimeError):
            lane.jdk21_home({'JAVA_HOME': 'jdk21'})

    def test_explicit_java_home_alias_is_canonicalized(self):
        alias = self.root / 'alias'
        alias.symlink_to(self.home, target_is_directory=True)
        with mock.patch.object(lane.sys, 'platform', 'linux'):
            self.assertEqual(lane.jdk21_home({'JAVA_HOME': str(alias)}), str(self.home))

    def test_macos_retains_bounded_java_home_selector(self):
        with mock.patch.object(lane.sys, 'platform', 'darwin'), mock.patch.object(
                lane.subprocess, 'check_output', return_value=str(self.home) + '\n') as spawn:
            self.assertEqual(lane.jdk21_home({'JAVA_HOME': '/unreviewed/override'}), str(self.home))
            spawn.assert_called_once_with(['/usr/libexec/java_home', '-v', '21'], text=True, timeout=15)

    def test_other_hosts_are_rejected(self):
        with mock.patch.object(lane.sys, 'platform', 'win32'), self.assertRaises(RuntimeError):
            lane.jdk21_home(self.env)

    def test_release_requires_exact_major21_and_unique_declaration(self):
        for release in ('JAVA_VERSION="17.0.12"\n', 'JAVA_VERSION="210"\n',
                        'JAVA_VERSION="21garbage"\n', 'JAVA_VERSION="21.0.8"\nJAVA_VERSION="21.0.8"\n'):
            with self.subTest(release=release):
                (self.home / 'release').write_text(release)
                with mock.patch.object(lane.sys, 'platform', 'linux'), self.assertRaises(RuntimeError):
                    lane.jdk21_home(self.env)

    def test_missing_or_oversized_release_is_rejected(self):
        release = self.home / 'release'
        release.unlink()
        with mock.patch.object(lane.sys, 'platform', 'linux'), self.assertRaises(RuntimeError):
            lane.jdk21_home(self.env)
        release.write_text('x' * 65537)
        with mock.patch.object(lane.sys, 'platform', 'linux'), self.assertRaises(RuntimeError):
            lane.jdk21_home(self.env)

    def test_jdk_requires_executable_java_and_javac(self):
        for name in ('java', 'javac'):
            with self.subTest(name=name):
                binary = self.home / 'bin' / name
                binary.chmod(0o600)
                with mock.patch.object(lane.sys, 'platform', 'linux'), self.assertRaises(RuntimeError):
                    lane.jdk21_home(self.env)
                binary.chmod(0o700)

    def probe(self, java_output=None, compiler_output='javac 21.0.8\n', exit_code=0):
        if java_output is None:
            java_output = f'Property settings:\n    java.home = {self.home}\n    java.specification.version = 21\n'
        destination = self.root / 'probe'
        destination.mkdir(exist_ok=True)

        def invoke(command, logfile, env, timeout=None):
            self.assertEqual(env, self.env)
            self.assertEqual(timeout, 30)
            self.assertEqual(Path(command[0]).parent, self.home / 'bin')
            logfile.write_text(compiler_output if Path(command[0]).name == 'javac' else java_output)
            return exit_code

        with mock.patch.object(lane, 'invoke', side_effect=invoke) as invoked:
            result = lane.verify_jdk21(str(self.home), destination, self.env)
        return result, invoked.call_args_list

    def test_actual_runtime_and_compiler_are_owned_bounded_and_recorded(self):
        result, calls = self.probe()
        self.assertEqual(result['home'], str(self.home))
        self.assertEqual(result['specification_version'], '21')
        self.assertEqual(result['javac_version'], '21.0.8')
        self.assertEqual([row.args[0][1:] for row in calls], [['-XshowSettings:properties', '-version'], ['-version']])
        self.assertRegex(result['java_log_sha256'], r'^[a-f0-9]{64}$')
        self.assertRegex(result['javac_log_sha256'], r'^[a-f0-9]{64}$')

    def test_actual_runtime_rejects_wrong_duplicate_or_missing_major(self):
        for declaration in ('java.specification.version = 17', 'java.specification.version = 210',
                            'java.specification.version = 21\njava.specification.version = 21', 'absent'):
            with self.subTest(declaration=declaration), self.assertRaises(RuntimeError):
                self.probe(f'java.home = {self.home}\n{declaration}\n')

    def test_actual_runtime_requires_matching_unique_home(self):
        other = self.root / 'other'
        other.mkdir()
        for declaration in (f'java.home = {other}', '', f'java.home = {self.home}\njava.home = {self.home}'):
            with self.subTest(declaration=declaration), self.assertRaises(RuntimeError):
                self.probe(declaration + '\njava.specification.version = 21\n')

    def test_actual_compiler_rejects_wrong_duplicate_or_missing_major(self):
        for output in ('javac 17.0.12\n', 'javac 210\n', 'javac 21.0.8\njavac 21.0.8\n', 'absent\n'):
            with self.subTest(output=output), self.assertRaises(RuntimeError):
                self.probe(compiler_output=output)

    def test_actual_probe_failure_and_oversized_output_are_rejected(self):
        with self.assertRaises(RuntimeError):
            self.probe(exit_code=1)
        with self.assertRaises(RuntimeError):
            self.probe(java_output='x' * 65537)
        with self.assertRaises(RuntimeError):
            self.probe(compiler_output='x' * 65537)

    def sdk(self, name='sdk', version='36.0.0'):
        root = self.root / name
        tool = root / 'build-tools' / version / 'dexdump'
        tool.parent.mkdir(parents=True)
        tool.write_text('synthetic fixture; never execute\n')
        tool.chmod(0o700)
        return root, tool

    def test_sdk_roots_must_agree_after_canonicalization(self):
        root, tool = self.sdk()
        alias = self.root / 'sdk-alias'
        alias.symlink_to(root, target_is_directory=True)
        self.assertEqual(lane.android_dexdump({'ANDROID_HOME': str(root), 'ANDROID_SDK_ROOT': str(alias)}), str(tool))
        other, _ = self.sdk('other-sdk')
        with self.assertRaises(RuntimeError):
            lane.android_dexdump({'ANDROID_HOME': str(root), 'ANDROID_SDK_ROOT': str(other)})

    def test_linux_sdk_has_no_implicit_or_relative_fallback(self):
        with mock.patch.object(lane.sys, 'platform', 'linux'):
            with self.assertRaises(RuntimeError):
                lane.android_dexdump({})
            with self.assertRaises(RuntimeError):
                lane.android_dexdump({'ANDROID_HOME': 'sdk'})

    def test_sdk_never_falls_back_to_another_tool_version(self):
        root, _ = self.sdk(version='37.0.0')
        with self.assertRaises(RuntimeError):
            lane.android_dexdump({'ANDROID_HOME': str(root)})

    def test_sdk_tool_must_be_executable(self):
        root, tool = self.sdk()
        tool.chmod(0o600)
        with self.assertRaises(RuntimeError):
            lane.android_dexdump({'ANDROID_HOME': str(root)})

    def test_macos_retains_existing_sdk_location_without_env_override(self):
        root, tool = self.sdk('Library/Android/sdk')
        with mock.patch.object(lane.sys, 'platform', 'darwin'), mock.patch.object(lane.Path, 'home', return_value=self.root):
            self.assertEqual(lane.android_dexdump({}), str(tool))

    def exercise_failed_main(self, release='21.0.8', runtime_failure=False):
        (self.home / 'release').write_text(f'JAVA_VERSION="{release}"\n')
        out = self.root / 'campaign'
        out.mkdir()
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(lane, 'ROOT', self.root))
            stack.enter_context(mock.patch.object(lane, 'OUT', out))
            stack.enter_context(mock.patch.object(lane.sys, 'platform', 'linux'))
            stack.enter_context(mock.patch.object(lane.sys, 'argv', ['lane.py', 'failure', '--command', 'never-execute']))
            stack.enter_context(mock.patch.dict(os.environ, self.env, clear=True))
            stack.enter_context(mock.patch.object(lane, 'processes', return_value={}))
            stack.enter_context(mock.patch.object(lane, 'owned_outputs', return_value=[]))
            stack.enter_context(mock.patch.object(lane, 'identity', return_value={'source_manifest_sha256': '0' * 64}))
            stack.enter_context(mock.patch.object(lane, 'runner_identity', return_value={'manifest_sha256': '1' * 64}))
            stack.enter_context(mock.patch.object(lane, 'stop_owned_workers', return_value={'remaining_owned_workers': []}))
            stack.enter_context(mock.patch.object(lane, 'collect_reports'))
            stack.enter_context(mock.patch.object(lane, 'inspect_generated_artifacts'))
            invoked = stack.enter_context(mock.patch.object(lane, 'invoke', return_value=0))
            if runtime_failure:
                stack.enter_context(mock.patch.object(lane, 'verify_jdk21', side_effect=RuntimeError('Synthetic probe failure')))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            with self.assertRaises(RuntimeError):
                lane.main()
        return out, invoked.call_args_list

    def test_static_preflight_failure_allocates_no_evidence_or_scratch(self):
        out, calls = self.exercise_failed_main(release='17.0.12')
        self.assertFalse((out / 'evidence').exists())
        self.assertEqual(calls, [])

    def test_runtime_probe_failure_still_stops_and_cleans_owned_lane(self):
        out, calls = self.exercise_failed_main(runtime_failure=True)
        cycle = out / 'evidence/failure'
        receipt = json.loads((cycle / 'receipt.json').read_text())
        self.assertEqual(receipt['status'], 'FAIL')
        self.assertEqual(receipt['error'], 'RuntimeError')
        self.assertEqual(receipt['stop_exit_code'], 0)
        self.assertEqual(receipt['cleanup_errors'], [])
        self.assertEqual(receipt['remaining_outputs'], [])
        self.assertFalse((cycle / 'scratch').exists())
        self.assertEqual([row.args[0] for row in calls], [['./gradlew', '--stop']])


if __name__ == '__main__':
    unittest.main(verbosity=2)
