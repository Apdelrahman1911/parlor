"""Pure owned-fixture contracts; no Gradle/Xcode/simulator/app/process operations."""
import ast
import copy
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location('ios_wrapper_smoke_controls', HERE / 'run_ios_wrapper_smoke_cycle.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class WrapperSmokeContractTest(unittest.TestCase):
    uuid = '11111111-1111-1111-1111-111111111111'

    def xctest(self):
        device = dict(deviceId=self.uuid, architecture='arm64', platform='iOS Simulator')
        summary = dict(result='Passed', totalTestCount=5, passedTests=5, failedTests=0,
                       skippedTests=0, expectedFailures=0, testFailures=[],
                       devicesAndConfigurations=[dict(device=device)])
        tests = dict(devices=[device], testNodes=[dict(nodeType='Test Case', nodeIdentifier=method,
                     result='Passed') for method in sorted(runner.EXPECTED_TESTS)])
        return summary, tests

    def test_five_exact_original_tests_on_owned_device_are_accepted(self):
        summary, tests = self.xctest()
        result = runner.verify_xctest(summary, tests, self.uuid)
        self.assertEqual(result['total'], 5)
        self.assertEqual(result['method'],
            'IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()')
        self.assertEqual(len(runner.EXPECTED_TESTS), 5)

    def test_skips_failures_omissions_duplicates_and_wrong_devices_fail_closed(self):
        summary, tests = self.xctest()
        for key, value in [('skippedTests', 1), ('failedTests', 1), ('expectedFailures', 1),
                           ('totalTestCount', 0), ('passedTests', 4), ('result', 'Failed')]:
            with self.subTest(key=key):
                changed = copy.deepcopy(summary); changed[key] = value
                with self.assertRaises(RuntimeError):
                    runner.verify_xctest(changed, tests, self.uuid)
        for edit in ('remove', 'duplicate', 'instrumented-test', 'wrong-device', 'wrong-architecture'):
            changed = copy.deepcopy(tests)
            if edit == 'remove': changed['testNodes'].pop()
            if edit == 'duplicate': changed['testNodes'][-1] = copy.deepcopy(changed['testNodes'][0])
            if edit == 'instrumented-test': changed['testNodes'][0]['nodeIdentifier'] = 'IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSInvestigation()'
            if edit == 'wrong-device': changed['devices'][0]['deviceId'] = '22222222-2222-2222-2222-222222222222'
            if edit == 'wrong-architecture': changed['devices'][0]['architecture'] = 'x86_64'
            with self.subTest(edit=edit), self.assertRaises(RuntimeError):
                runner.verify_xctest(summary, changed, self.uuid)

    def create_copy_fixture(self, root):
        inputs = {
            'iosApp/iosApp.xcodeproj/project.pbxproj': 'original phase\n',
            'iosApp/iosApp/ContentView.swift': 'unchanged Swift application\n',
            'iosApp/iosAppUITests/IOSAppLaunchUITests.swift': 'unchanged launch test\n',
            'shared/design-system/src/commonMain/kotlin/Provider.kt': 'unchanged Kotlin application\n',
        }
        entries = []
        for name, content in inputs.items():
            path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content)
            entries.append(dict(path=name, sha256=runner.digest(path)))
        phase = root / 'iosApp/iosApp.xcodeproj/project.pbxproj'
        phase.write_text('reviewed immediate-stop phase\n')
        return dict(copy_only=entries), inputs

    def test_only_phase_edit_preserves_every_application_and_test_hash(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, inputs = self.create_copy_fixture(root)
            rows = runner.inspect_smoke_manifest(root, binding)
            changed = [row['path'] for row in rows if row['original_sha256'] != row['copied_sha256']]
            self.assertEqual(changed, ['iosApp/iosApp.xcodeproj/project.pbxproj'])
            self.assertEqual(len(rows), len(inputs))

    def test_swift_kotlin_and_test_edits_are_each_rejected(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, inputs = self.create_copy_fixture(root)
            for name, content in inputs.items():
                if name.endswith('project.pbxproj'): continue
                path = root / name; path.write_text(content + '// observer\n')
                with self.subTest(path=name), self.assertRaises(RuntimeError):
                    runner.inspect_smoke_manifest(root, binding)
                path.write_text(content)

    def test_added_observer_source_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, _ = self.create_copy_fixture(root)
            (root / 'iosApp/iosApp/Observer.swift').write_text('observer')
            with self.assertRaises(RuntimeError): runner.inspect_smoke_manifest(root, binding)

    def test_symlink_and_duplicate_binding_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, _ = self.create_copy_fixture(root)
            duplicate = dict(copy_only=binding['copy_only'] + [binding['copy_only'][0]])
            with self.assertRaises(RuntimeError): runner.inspect_smoke_manifest(root, duplicate)
            link = root / 'iosApp/iosApp/alias.swift'
            link.symlink_to(root / 'iosApp/iosApp/ContentView.swift')
            with self.assertRaises(RuntimeError): runner.inspect_smoke_manifest(root, binding)

    def test_unmodified_phase_cannot_bypass_immediate_stop_copy_contract(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, inputs = self.create_copy_fixture(root)
            name = 'iosApp/iosApp.xcodeproj/project.pbxproj'; (root / name).write_text(inputs[name])
            with self.assertRaises(RuntimeError): runner.inspect_smoke_manifest(root, binding)

    def test_post_build_verification_rejects_new_or_changed_source(self):
        with tempfile.TemporaryDirectory(prefix='parlor-smoke-control-') as directory:
            root = Path(directory).resolve(); binding, _ = self.create_copy_fixture(root)
            before = runner.inspect_smoke_manifest(root, binding)
            self.assertTrue(runner.inspect_copied_inputs_after_build(root, before)['unchanged'])
            extra = root / 'iosApp/iosApp/NewSource.swift'; extra.write_text('new')
            result = runner.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertEqual(result['unexpected_build_inputs'], ['iosApp/iosApp/NewSource.swift'])
            extra.unlink(); original = root / 'iosApp/iosApp/ContentView.swift'; original.write_text('changed')
            self.assertFalse(runner.inspect_copied_inputs_after_build(root, before)['unchanged'])

    def test_runner_cannot_instrument_or_read_application_data(self):
        source = (HERE / 'run_ios_wrapper_smoke_cycle.py').read_text()
        tree = ast.parse(source)
        calls = {node.func.id for node in ast.walk(tree)
                 if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)}
        self.assertFalse(calls & {'instrument_kotlin', 'verify_probe', 'verify_legacy_settings_probe'})
        for forbidden in ('DSC01Overlay', 'DSC01Probe', 'DSC01InvocationBridge', 'get_app_container', 'probe-result'):
            self.assertNotIn(forbidden, source)
        self.assertIn("'-only-testing:iosAppUITests/' + method[:-2]", source)

    def test_previously_reviewed_ownership_and_cleanup_helpers_are_unchanged(self):
        base = ast.parse((HERE.parent / 'dsc01_apphost_v6/run_dsc01_apphost_cycle.py').read_text())
        smoke = ast.parse((HERE / 'run_ios_wrapper_smoke_cycle.py').read_text())
        def declarations(tree):
            return {node.name: ast.dump(node, include_attributes=False) for node in ast.walk(tree)
                    if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name != 'main'}
        prior, current = declarations(base), declarations(smoke)
        for name in ('AppHostOwnership', 'defer_parent_signals', 'process_uid', 'normalized_identity',
                     'save', 'stage', 'command', 'stop_gradle', 'own_simulator_metadata',
                     'shutdown_owned_device', 'delete_owned_device', 'verify_workers',
                     'verify_copied_inputs', 'remove', 'remove_temp'):
            with self.subTest(name=name): self.assertEqual(current[name], prior[name])


if __name__ == '__main__':
    unittest.main()
