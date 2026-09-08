"""Pure disposable fixtures/source contracts; no Gradle/native tool/app execution."""
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import plistlib
import sys
import tempfile
import unittest
from unittest import mock

HERE = Path(__file__).resolve().parent
SMOKE = HERE.parent / 'ios_wrapper_smoke_v1'
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location('ios_release_wrapper_controls', HERE / 'run_ios_release_wrapper_cycle.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
import release_artifacts as artifacts


class ReleaseArtifactContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='parlor-release-wrapper-control-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.app = self.root / 'Parlor.app'; self.app.mkdir()
        self.source = self.root / 'source'; self.source.mkdir()
        self.source_paths = []
        self.probed = []
        self.info = dict(CFBundleIdentifier='com.parlor.app', CFBundleExecutable='Parlor',
            CFBundleShortVersionString='1.0.0', CFBundleVersion='1', MinimumOSVersion='16.0',
            CFBundleSupportedPlatforms=['iPhoneSimulator'], DTPlatformName='iphonesimulator',
            CFBundleLocalizations=['en', 'ar'], NSBonjourServices=['_p2pkit2._tcp'],
            NSLocalNetworkUsageDescription='Synthetic declared local network use')
        self.write(self.app, 'Info.plist', plistlib.dumps(self.info))
        self.write(self.source, 'iosApp/iosApp/Info.plist', plistlib.dumps(self.info))
        privacy = plistlib.dumps(dict(NSPrivacyTracking=False, NSPrivacyCollectedDataTypes=[]))
        self.write(self.app, 'PrivacyInfo.xcprivacy', privacy)
        self.write(self.source, 'iosApp/iosApp/PrivacyInfo.xcprivacy', privacy)
        self.write(self.source, 'config/parlor-version.xcconfig', b'PARLOR_VERSION_NAME = 1.0.0\nPARLOR_BUILD_NUMBER = 1\n')
        case = 'game-modes/whodunit/src/commonMain/composeResources/files/cases/synthetic.json'
        self.write(self.source, case, b'{"synthetic":true}\n'); self.source_paths.append(case)
        self.write(self.app, 'compose-resources/files/cases/synthetic.json', b'{"synthetic":true}\n')
        for relative in sorted(artifacts.REQUIRED_NATIVE): self.native(relative)

    def write(self, root, name, contents):
        path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(contents)
        return path

    def native(self, name):
        path = self.write(self.app, name, bytes.fromhex('cffaedfe') + b'synthetic header: not executable machine code')
        path.chmod(0o755)

    def probe(self, path):
        self.probed.append(str(path.relative_to(self.app)))
        return dict(file='Mach-O 64-bit executable arm64\n', archs='arm64\n',
                    build='Load command 1\n platform IOSSIMULATOR\n minos 15.0\n sdk 26.5\n')

    def inspect(self):
        return artifacts.inspect_release_artifacts(self.app, self.source, self.probe, self.source_paths)

    def test_all_native_files_including_unexpected_debug_dylib_are_inspected(self):
        self.native('Parlor.debug.dylib')
        result = self.inspect()
        self.assertEqual(set(self.probed), artifacts.REQUIRED_NATIVE | {'Parlor.debug.dylib'})
        self.assertEqual(3, len(result['native_binaries']))
        self.assertEqual('PASS', result['status'])
        self.assertTrue(result['privacy_structurally_matches_source'])
        self.assertEqual(1, len(result['raw_resource_matches']))
        self.assertIn('No runtime', result['limitation'])
        self.assertEqual(len(list(p for p in self.app.rglob('*') if p.is_file())), result['file_count'])
        self.assertTrue(all(len(entry['sha256']) == 64 for entry in result['files']))

    def test_failed_xcode_or_nested_gradle_stop_cannot_pass_with_stale_app(self):
        self.assertTrue((self.app / 'Parlor').exists())
        for code in (1, 65, -15, None, False):
            with self.assertRaises(RuntimeError): artifacts.require_completed_build(code, 'build_exit=0\nstop_exit=0\n')
        for receipt in (None, '', 'build_exit=1\nstop_exit=0\n', 'build_exit=0\nstop_exit=1\n',
                        'build_exit=0\nstop_exit=0\nextra\n'):
            with self.assertRaises(RuntimeError): artifacts.require_completed_build(0, receipt)
        artifacts.require_completed_build(0, 'build_exit=0\nstop_exit=0\n')

    def test_wrong_identity_version_deployment_platform_and_localization_fail(self):
        for key, value in (('CFBundleIdentifier', 'com.parlor.app.debug'), ('CFBundleVersion', '2'),
                           ('CFBundleShortVersionString', '1.0'), ('MinimumOSVersion', '15.0'),
                           ('CFBundleExecutable', 'Other'), ('CFBundleSupportedPlatforms', ['iPhoneOS']),
                           ('DTPlatformName', 'iphoneos'), ('CFBundleLocalizations', ['en']),
                           ('NSBonjourServices', ['_unknown._tcp'])):
            changed = dict(self.info); changed[key] = value
            self.write(self.app, 'Info.plist', plistlib.dumps(changed))
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.inspect()
        self.write(self.app, 'Info.plist', plistlib.dumps(self.info))

    def test_framework_floor_is_not_incorrectly_forced_to_app_floor(self):
        result = self.inspect()
        self.assertEqual(['15.0'], result['native_binaries'][0]['minimum_os_versions'])
        self.assertEqual('16.0', result['app_identity']['MinimumOSVersion'])

    def test_wrong_native_architecture_platform_floor_or_missing_metadata_fails(self):
        original = self.probe(self.app / 'Parlor')
        for key, value in (('file', 'data'), ('archs', 'x86_64'), ('archs', 'arm64 x86_64'),
                           ('build', 'platform IOS\n minos 16.0\n'), ('build', 'platform IOSSIMULATOR\n minos 17.0\n'),
                           ('build', 'platform IOSSIMULATOR\n'), ('build', 'minos 16.0\n')):
            changed = dict(original); changed[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                artifacts.verify_native_probe('Parlor', changed)
        changed = dict(original); changed['extra'] = 'unexpected'
        with self.assertRaises(RuntimeError): artifacts.verify_native_probe('Parlor', changed)

    def test_missing_or_unrecognized_expected_native_fails(self):
        path = self.app / 'Parlor'; path.unlink()
        with self.assertRaises(RuntimeError): self.inspect()
        self.write(self.app, 'Parlor', b'not executable'); (self.app / 'Parlor').chmod(0o755)
        with self.assertRaises(RuntimeError): self.inspect()

    def test_source_privacy_and_raw_content_are_bound(self):
        self.write(self.app, 'PrivacyInfo.xcprivacy', plistlib.dumps(dict(NSPrivacyTracking=True)))
        with self.assertRaises(RuntimeError): self.inspect()
        self.write(self.app, 'PrivacyInfo.xcprivacy', (self.source / 'iosApp/iosApp/PrivacyInfo.xcprivacy').read_bytes())
        self.write(self.app, 'compose-resources/files/cases/synthetic.json', b'{"changed":true}\n')
        with self.assertRaises(RuntimeError): self.inspect()

    def test_missing_raw_source_and_duplicate_version_assignment_fail(self):
        with self.assertRaises(RuntimeError): artifacts.inspect_release_artifacts(self.app, self.source, self.probe, [])
        path = self.source / 'config/parlor-version.xcconfig'
        path.write_text(path.read_text() + 'PARLOR_BUILD_NUMBER = 1\n')
        with self.assertRaises(RuntimeError): self.inspect()

    def test_generated_symlink_or_provisioning_profile_fails_closed(self):
        (self.app / 'alias').symlink_to(self.app / 'Info.plist')
        with self.assertRaises(RuntimeError): self.inspect()
        (self.app / 'alias').unlink()
        self.write(self.app, 'embedded.mobileprovision', b'synthetic forbidden profile fixture')
        with self.assertRaises(RuntimeError): self.inspect()

    def test_inventory_bounds_are_enforced_without_large_allocation(self):
        with mock.patch.object(artifacts, 'MAX_FILES', 1), self.assertRaises(RuntimeError): self.inspect()
        with mock.patch.object(artifacts, 'MAX_TOTAL_BYTES', 1), self.assertRaises(RuntimeError): self.inspect()
        with mock.patch.object(artifacts, 'MAX_PLIST_BYTES', 1), self.assertRaises(RuntimeError): self.inspect()


class ReleaseWrapperSourceContractTest(unittest.TestCase):
    def test_release_command_is_build_only_generic_arm64_unsigned(self):
        source = (HERE / 'run_ios_release_wrapper_cycle.py').read_text()
        tree = ast.parse(source)
        commands = [node.args[0] for node in ast.walk(tree) if isinstance(node, ast.Call) and
                    isinstance(node.func, ast.Name) and node.func.id == 'command' and node.args and isinstance(node.args[0], ast.List)]
        builds = [node for node in commands if any(isinstance(item, ast.Constant) and item.value == 'build' for item in node.elts)]
        self.assertEqual(1, len(builds))
        values = [node.value for node in builds[0].elts if isinstance(node, ast.Constant)]
        for needed in ('xcodebuild', 'Release', 'iphonesimulator', 'generic/platform=iOS Simulator',
                       'ARCHS=arm64', 'ONLY_ACTIVE_ARCH=YES', 'CODE_SIGNING_ALLOWED=NO', 'CODE_SIGNING_REQUIRED=NO'):
            self.assertIn(needed, values)
        self.assertEqual('build', values[-1])
        for forbidden in ('simctl', 'get_app_container', 'xcresulttool', '-only-testing:',
                          '-exportArchive', '-archivePath', '-allowProvisioningUpdates', 'verify_xctest(', 'instrument_kotlin('):
            self.assertNotIn(forbidden, source)
        self.assertIn("CONFIGURATION='Release'", source)
        self.assertIn("runtime", source)  # Explicit limitation, not a runtime gate.
        self.assertNotIn("runtime_evidence_status='PASS'", source)

    def test_build_failure_gate_precedes_artifact_inspection_and_immediate_stop_is_finally_owned(self):
        source = (HERE / 'run_ios_release_wrapper_cycle.py').read_text()
        self.assertIn("            finally:\n                stop_gradle('stop-xcode-immediate')", source)
        self.assertLess(source.index('require_completed_build(xcode,'), source.index('inspection = inspect_release_artifacts('))
        self.assertIn("receipt['artifact_inspection_status'] = 'PASS'", source)
        self.assertIn("receipt.get('build_evidence_status') == 'PASS'", source)

    def test_phase_copy_input_and_source_binding_helpers_are_unchanged(self):
        for name in ('secondary_fifo.py', 'test_secondary_fifo.py', 'copied-kotlin-phase.sh.in', 'copied_sources.py', 'bind_source.py'):
            self.assertEqual((SMOKE / name).read_bytes(), (HERE / name).read_bytes(), name)
        self.assertEqual(json.loads((SMOKE / 'source-bindings.json').read_text())['source_identity'],
                         json.loads((HERE / 'source-bindings.json').read_text())['source_identity'])
        marker = 'def inspect_smoke_manifest('
        old = (SMOKE / 'run_ios_wrapper_smoke_cycle.py').read_text()
        old = old[old.index(marker):old.index('\n\ndef verify_xctest(')]
        new = (HERE / 'run_ios_release_wrapper_cycle.py').read_text()
        new = new[new.index('def inspect_wrapper_manifest('):new.index('\n\nclass AppHostOwnership')]
        expected = old.replace('inspect_smoke_manifest', 'inspect_wrapper_manifest').replace('Smoke permits only', 'Wrapper permits only').replace('the smoke copy', 'the Release wrapper copy')
        self.assertEqual(expected.strip(), new.strip())

    def test_owned_worker_fifo_signal_and_file_cleanup_declarations_are_unchanged(self):
        def declarations(path):
            return {node.name: ast.dump(node, include_attributes=False) for node in ast.walk(ast.parse(path.read_text()))
                    if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name != 'main'}
        old, new = declarations(SMOKE / 'run_ios_wrapper_smoke_cycle.py'), declarations(HERE / 'run_ios_release_wrapper_cycle.py')
        for name in ('AppHostOwnership', 'defer_parent_signals', 'process_uid', 'normalized_identity',
                     'save', 'stage', 'command', 'stop_gradle', 'verify_workers', 'verify_copied_inputs', 'remove', 'remove_temp'):
            with self.subTest(name=name): self.assertEqual(old[name], new[name])

    def test_all_consumed_old_controls_are_explicitly_hashed(self):
        expected = {'run_ios_wrapper_smoke_cycle.py', 'secondary_fifo.py', 'test_secondary_fifo.py',
                    'copied-kotlin-phase.sh.in', 'source-bindings.json', 'copied_sources.py', 'bind_source.py'}
        controls = runner.control_files()
        self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual(expected, {path.name for path in controls if path.parent == SMOKE})
        manifest = {row['path']: row['sha256'] for row in runner.control_manifest()}
        for name in expected:
            path = SMOKE / name
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])


if __name__ == '__main__':
    unittest.main()
