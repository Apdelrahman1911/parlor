"""Pure profile/runner controls; never starts Gradle, Xcode or a simulator."""
import ast
from contextlib import nullcontext
import json
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

import toolchain_profiles as profiles

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
NORMAL = CAMPAIGN / 'native/normal-ios-launch-proposal-01/run_normal_ios_launch.py'
COMPANION = CAMPAIGN / 'l08-storage-functional-companion-02/run_ios_readiness.py'
DEVICE = '11111111-2222-3333-4444-555555555555'


def isolated_function(path, name, namespace):
    """Compile only the named pure/stubbed function, never a runner or imports."""
    functions = [node for node in ast.walk(ast.parse(path.read_text()))
                 if isinstance(node, ast.FunctionDef) and node.name == name]
    if len(functions) != 1:
        raise RuntimeError('Ambiguous control extraction')
    exec(compile(ast.Module(body=functions, type_ignores=[]), '<isolated-native-control>', 'exec'), namespace)
    return namespace[name]


class ProfileControls(unittest.TestCase):
    def observation(self, name=profiles.QUALIFIED, **changes):
        values = dict(xcode_version='Xcode 26.3\nBuild version 17C529\n', sdk_version='26.2\n',
                      developer_dir=profiles.QUALIFIED_DEVELOPER + '\n', architecture='arm64\n')
        values.update(changes)
        return profiles.validate_observation(name, **values)

    def test_default_local_profile_never_auto_selects_qualified(self):
        self.assertEqual(profiles.selected_toolchain([]), (profiles.LOCAL, []))
        value = profiles.profile(profiles.LOCAL)
        self.assertEqual((value['xcode'], value['build'], value['sdk'], value['runtime']),
                         ('26.5', '17F42', '26.5', 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'))
        self.assertEqual(profiles.developer_environment(profiles.LOCAL,
            {'DEVELOPER_DIR': profiles.QUALIFIED_DEVELOPER}), {})
        with self.assertRaises(RuntimeError):
            self.observation(name=profiles.LOCAL)

    def test_qualified_profile_matches_reviewed_release_policy_but_actual_sdk_is_26_2(self):
        apple = json.loads((ROOT / 'config/release-policy.json').read_text())['toolchains']['apple']
        actual = self.observation()
        self.assertEqual(actual['developer_dir'], apple['developer_dir'])
        self.assertEqual(actual['build'], apple['xcode_build'])
        self.assertEqual((actual['xcode'], actual['sdk_name'], actual['runtime']),
                         ('26.3', 'iphonesimulator26.2', 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'))

    def test_exact_explicit_flag_preserves_other_strict_parser_arguments(self):
        remaining = ['--simulator-signing=adhoc', '--image-observer=libproc']
        self.assertEqual(profiles.selected_toolchain(remaining + ['--toolchain=' + profiles.QUALIFIED]),
                         (profiles.QUALIFIED, remaining))
        self.assertEqual(profiles.selected_toolchain(['--toolchain']), (profiles.LOCAL, ['--toolchain']))
        namespace = {}
        selected_mode = isolated_function(HERE / 'simulator_signing.py', 'selected_mode', namespace)
        with self.assertRaises(RuntimeError):
            selected_mode(['--toolchain'])

    def test_unknown_empty_and_duplicate_profiles_are_rejected(self):
        for arguments in (['--toolchain='], ['--toolchain=latest'], ['--toolchain=qualified-xcode-26.5'],
                          ['--toolchain=' + profiles.QUALIFIED] * 2,
                          ['--toolchain=' + profiles.LOCAL, '--toolchain=' + profiles.QUALIFIED]):
            with self.subTest(arguments=arguments), self.assertRaises(RuntimeError):
                profiles.selected_toolchain(arguments)

    def test_qualified_developer_must_be_explicit_exact_and_not_a_symlink_alias(self):
        for value in (None, '', '/Applications/Xcode.app/Contents/Developer',
                      profiles.QUALIFIED_DEVELOPER + '/', profiles.QUALIFIED_DEVELOPER + '\n'):
            inherited = {} if value is None else {'DEVELOPER_DIR': value}
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                profiles.developer_environment(profiles.QUALIFIED, inherited)
        self.assertEqual(profiles.developer_environment(profiles.QUALIFIED,
            {'DEVELOPER_DIR': profiles.QUALIFIED_DEVELOPER, 'DYLD_INSERT_LIBRARIES': 'untrusted'}),
            {'DEVELOPER_DIR': profiles.QUALIFIED_DEVELOPER})

    def test_exact_xcode_build_sdk_architecture_and_selected_path_fail_closed(self):
        changes = [('xcode_version', 'Xcode 26.5\nBuild version 17F42\n'),
                   ('xcode_version', 'Xcode 26.3\nBuild version unreviewed\n'),
                   ('xcode_version', 'Xcode 26.3\nBuild version 17C529\nwarning\n'),
                   ('sdk_version', '26.3\n'), ('sdk_version', '26.5\n'),
                   ('architecture', 'x86_64\n'),
                   ('developer_dir', '/Applications/Xcode.app/Contents/Developer\n'),
                   ('developer_dir', profiles.QUALIFIED_DEVELOPER + '\nforeign')]
        for key, value in changes:
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                self.observation(**{key: value})

    def test_local_observation_stays_exact_26_5(self):
        actual = self.observation(name=profiles.LOCAL, xcode_version='Xcode 26.5\nBuild version 17F42\n',
                                  sdk_version='26.5', developer_dir='/Applications/Xcode.app/Contents/Developer')
        self.assertEqual(actual['sdk_name'], 'iphonesimulator26.5')
        self.assertIsNone(actual['developer_dir'])

    def test_profiles_return_independent_data_not_mutable_global_authority(self):
        changed = profiles.profile(profiles.QUALIFIED)
        changed['sdk'] = '26.5'
        self.assertEqual(profiles.profile(profiles.QUALIFIED)['sdk'], '26.2')


class RunnerProfileControls(unittest.TestCase):
    def test_normal_isolated_environment_keeps_only_validated_developer(self):
        import shlex
        namespace = dict(Path=Path, shlex=shlex, LOCAL_TOOLCHAIN=profiles.LOCAL,
                         developer_environment=profiles.developer_environment,
                         signing_overrides=lambda _: {})
        environment = isolated_function(NORMAL, 'isolated_environment', namespace)
        inherited = {'HOME': '/Users/synthetic', 'DEVELOPER_DIR': profiles.QUALIFIED_DEVELOPER,
                     'DYLD_INSERT_LIBRARIES': 'untrusted', 'SDK_NAME': 'iphonesimulator99',
                     'GRADLE_OPTS': '-Dunsafe', 'CODE_SIGN_IDENTITY': 'private'}
        value = environment(inherited, '/public/jdk/Contents/Home', Path('/owned/tmp'),
                            Path('/public/sdk'), 'adhoc', profiles.QUALIFIED)
        self.assertEqual(value['DEVELOPER_DIR'], profiles.QUALIFIED_DEVELOPER)
        self.assertNotIn('DYLD_INSERT_LIBRARIES', value)
        self.assertNotIn('SDK_NAME', value)
        self.assertNotIn('CODE_SIGN_IDENTITY', value)
        self.assertNotIn('unsafe', value['GRADLE_OPTS'])
        with self.assertRaises(RuntimeError):
            environment({}, '/public/jdk/Contents/Home', Path('/owned/tmp'), Path('/public/sdk'),
                        'adhoc', profiles.QUALIFIED)

    def test_normal_runtime_metadata_accepts_only_selected_profile_and_keeps_owned_uuid(self):
        from owned_lane import write_json
        namespace = dict(read_json=json.loads, UUID=re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}\Z'),
                         write_json=write_json)
        # The real decoder's size bound is modeled without relaxing JSON parsing.
        namespace['read_json'] = lambda data, maximum: json.loads(data) if len(data.encode()) <= maximum else None
        inspect = isolated_function(NORMAL, 'simulator_metadata', namespace)
        with tempfile.TemporaryDirectory(prefix='parlor-profile-control-') as raw:
            root = Path(raw).resolve()
            temporary, evidence = root / 'temporary', root / 'evidence'
            temporary.mkdir(); evidence.mkdir()
            device = dict(udid=DEVICE, name='parlor-audit-owned-profile-control', state='Shutdown')
            for runtime, changed_uuid in ((profiles.profile(profiles.QUALIFIED)['runtime'], False),
                                           (profiles.profile(profiles.LOCAL)['runtime'], False),
                                           (profiles.profile(profiles.QUALIFIED)['runtime'], True)):
                selected = dict(device, udid='22222222-2222-3333-4444-555555555555') if changed_uuid else device
                def require(_args, _label, **options):
                    options['output'].write_text(json.dumps(dict(devices={runtime: [selected]})))
                lane = SimpleNamespace(temporary=temporary, destination=evidence, uuid=DEVICE,
                    receipt={'owned_device_name': device['name']}, toolchain=profiles.profile(profiles.QUALIFIED),
                    require=require)
                if runtime.endswith('26-2') and not changed_uuid:
                    self.assertEqual(inspect(lane, 'owned-profile')['runtime'], runtime)
                    (evidence / 'owned-profile.json').unlink()  # Preserved synthetic row, not device data.
                else:
                    with self.assertRaisesRegex(RuntimeError, 'simulator identity'):
                        inspect(lane, 'owned-profile')
                self.assertFalse((temporary / 'owned-profile.json').exists())

    def test_companion_runtime_metadata_rejects_wrong_profile_with_stubbed_commands_only(self):
        with tempfile.TemporaryDirectory(prefix='parlor-profile-control-') as raw:
            destination = Path(raw).resolve()
            for runtime in (profiles.profile(profiles.QUALIFIED)['runtime'], profiles.profile(profiles.LOCAL)['runtime']):
                child = Mock(returncode=0)
                child.communicate.return_value = (json.dumps(dict(devices={runtime: [dict(
                    name='owned-name', udid=DEVICE, state='Shutdown')]})).encode(), b'')
                process = SimpleNamespace(Popen=Mock(return_value=child), PIPE=-1)
                owner = Mock()
                namespace = dict(subprocess=process, json=json, re=re, ROOT=ROOT, env={}, uuid=DEVICE,
                    toolchain=profiles.profile(profiles.QUALIFIED), receipt={'commands': [], 'owned_device_name': 'owned-name'},
                    dest=destination, owner=owner, now=lambda: 'synthetic-time', save=lambda: None,
                    defer_parent_signals=nullcontext, write_json=lambda _path, _data: None)
                inspect = isolated_function(COMPANION, 'own_simulator_metadata', namespace)
                if runtime.endswith('26-2'):
                    self.assertEqual(inspect('synthetic-label')['runtime'], runtime)
                    owner.stop.assert_not_called()
                else:
                    with self.assertRaisesRegex(RuntimeError, 'runtime differs'):
                        inspect('synthetic-label')
                    owner.stop.assert_called_once()
                process.Popen.assert_called_once()

    def test_companion_and_normal_source_validate_profile_before_creating_simulator(self):
        normal, companion = NORMAL.read_text(), COMPANION.read_text()
        for text in (normal, companion):
            with self.subTest(source='normal' if text == normal else 'companion'):
                validation = text.index('validate_observation(', text.index('def prepare(') if text == normal else text.index('def main('))
                creation = text.index("'simctl', 'create'")
                self.assertLess(validation, creation)
                self.assertIn("toolchain['runtime']", text)
                self.assertIn('developer_environment(', text)
                self.assertIn('selected_toolchain(', text)
        self.assertIn('[TOOLCHAIN_HELPER, BINDING]', companion)


if __name__ == '__main__':
    unittest.main()
