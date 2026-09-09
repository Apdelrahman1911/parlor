"""Source-copy/runner tests. All shells use disposable fake wrappers, not Gradle."""
import contextlib
import ast
import hashlib
import importlib
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

import bind_source
import copied_sources as copies
import instrument_storage
import simulator_signing
from owned_lane import ROOT

HERE = Path(__file__).resolve().parent


class SourceCopyTests(unittest.TestCase):
    def test_credential_trace_retains_every_actual_native_call_and_guard(self):
        original = (ROOT / instrument_storage.PATH).read_text()
        transformed = instrument_storage.instrument_credentials(original)
        for name in ('SecItemCopyMatching', 'SecItemUpdate', 'SecItemAdd', 'SecItemDelete'):
            self.assertEqual(re.findall(r'\b' + name + r'\([^)]*\)', original),
                             re.findall(r'\b' + name + r'\([^)]*\)', transformed))
        for guard in ('requireValidKey(key)', 'require(value.size <= MAX_VALUE_BYTES)',
                      'errSecSuccess, errSecItemNotFound -> Unit', 'value?.let(::CFRelease)',
                      'check(retry == errSecSuccess)', 'check(CFGetTypeID(returned) == CFDataGetTypeID())'):
            self.assertIn(guard, transformed)
            self.assertEqual(transformed.count(guard), original.count(guard))
        for kind in ('read', 'update', 'add', 'retry', 'delete'):
            self.assertEqual(transformed.count('.record("credential-' + kind + '"'), 1)
        self.assertNotIn('NativeReadinessInvocation', original)
        self.assertEqual((ROOT / instrument_storage.PATH).read_text(), original)
        with self.assertRaises(RuntimeError): instrument_storage.instrument_credentials(transformed)
        with self.assertRaises(RuntimeError): instrument_storage.instrument_credentials(original + original)

    def test_all_copy_only_kotlin_transformations_use_current_source_and_preserve_the_root(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-copy-test-') as raw:
            root = Path(raw).resolve(); manifest = dict(copy_only=[])
            for relative in copies.MODIFIED_KOTLIN:
                target = root / relative; target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / relative, target)
                manifest['copy_only'].append(dict(path=relative, sha256=copies.sha256(target)))
            copies.instrument_kotlin(root)
            observed = copies.inspect_copied_manifest(root, manifest, [])
            self.assertEqual(len(observed), len(copies.MODIFIED_KOTLIN) + len(copies.ADDITIONS))
            for entry in manifest['copy_only']:
                self.assertEqual(copies.sha256(ROOT / entry['path']), entry['sha256'])
            self.assertTrue(copies.inspect_copied_inputs_after_build(root, observed)['unchanged'])
            (root / 'composeApp/build/generated').mkdir(parents=True)
            (root / 'composeApp/build/generated/Output.kt').write_text('generated disposable fixture')
            self.assertTrue(copies.inspect_copied_inputs_after_build(root, observed)['unchanged'])
            unexpected = root / 'composeApp/src/iosMain/kotlin/Unexpected.kt'; unexpected.write_text('unreviewed input')
            self.assertFalse(copies.inspect_copied_inputs_after_build(root, observed)['unchanged'])

    def test_build_copy_inventory_rejects_implicit_binding_symlinks_and_partial_inputs(self):
        required = {*copies.BUILD_ROOTS, *copies.MODIFIED_KOTLIN, 'gradle/wrapper/gradle-wrapper.jar',
                    'gradle/verification-metadata.xml', 'iosApp/iosApp/Info.plist',
                    'iosApp/iosApp/ComposeContainerViewController.swift',
                    'iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift',
                    'scripts/verification/ios-readiness/simulator_signing.py'}
        with tempfile.TemporaryDirectory(prefix='parlor-native-copy-test-') as raw:
            base = Path(raw).resolve(); source = base / 'source'; source.mkdir()
            entries = []
            for relative in sorted(required):
                path = source / relative; path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('synthetic input: ' + relative)
                entries.append(dict(path=relative, sha256=copies.sha256(path)))
            binding = dict(schema_version=3, binding_status='REVIEW_REQUIRED_BOUND', copy_only=entries,
                           source_identity=dict(source_manifest=[[entry['path'], entry['sha256']] for entry in entries]))
            target = base / 'target'; target.mkdir()
            copies.create_source_copy(source, target, binding)
            self.assertEqual({str(path.relative_to(target)) for path in target.rglob('*') if path.is_file()}, required)
            for name, altered in (
                ('implicit', {**binding, 'binding_status': 'AUTO_REFRESHED'}),
                ('partial', {**binding, 'copy_only': entries[:-1]}),
            ):
                destination = base / name; destination.mkdir()
                with self.subTest(name=name), self.assertRaises(RuntimeError):
                    copies.create_source_copy(source, destination, altered)
            existing = base / 'existing'; existing.mkdir(); (existing / 'user.txt').write_text('preserve')
            with self.assertRaises(RuntimeError): copies.create_source_copy(source, existing, binding)
            self.assertEqual((existing / 'user.txt').read_text(), 'preserve')
            link_input = source / 'gradlew'; link_input.unlink(); link_input.symlink_to(source / 'gradlew.bat')
            destination = base / 'symlink'; destination.mkdir()
            with self.assertRaises(RuntimeError): copies.create_source_copy(source, destination, binding)

    def test_protected_or_unreviewed_build_paths_cannot_enter_copy(self):
        for path in ('../composeApp/a.kt', '/composeApp/a.kt', 'composeApp/./a.kt',
                     'composeApp/local.properties', 'composeApp/key.p12', 'composeApp/.hidden/a.kt',
                     'composeApp/build/a.kt', 'audit-runs/foo.kt', 'design/mock.js'):
            with self.subTest(path=path): self.assertFalse(copies.allowed_path(path))
        self.assertTrue(copies.allowed_path('gradle/verification-metadata.xml'))

    def test_exact_simulator_name_is_shared_by_constructor_renderer_and_native_guard(self):
        template = (HERE / 'DSC01OSPrerequisite.swift.in').read_text()
        name = copies.owned_simulator_name(Path('/synthetic/parlor-audit-ios-readiness-07-owned_test'))
        self.assertEqual(name, 'Parlor-Audit-parlor-audit-ios-readiness-07-owned_test')
        rendered = copies.render_owned_os_prerequisite(template, name)
        expression = re.search(r'let ownedName = environment\["SIMULATOR_DEVICE_NAME"\] == ("[^"\n]+")', rendered)
        self.assertIsNotNone(expression)
        self.assertEqual(json.loads(expression[1]), name)
        self.assertNotIn(copies.SIMULATOR_NAME_TOKEN, rendered)
        self.assertNotIn('dsc01-apphost-', rendered)
        self.assertIn('environment["SIMULATOR_UDID"] == expectedSimulator && UUID(uuidString: expectedSimulator) != nil', rendered)
        self.assertIn('#if DEBUG && targetEnvironment(simulator)', rendered)
        self.assertIn('guard sameSimulator, ownedName else', rendered)
        runner = (HERE / 'run_ios_readiness.py').read_text()
        tree = ast.parse(runner)
        calls = [node for node in ast.walk(tree) if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)]
        constructors = [node for node in calls if node.func.id == 'owned_simulator_name']
        renderers = [node for node in calls if node.func.id == 'render_owned_os_prerequisite']
        self.assertEqual(len(constructors), 1)
        self.assertEqual(ast.dump(constructors[0].args[0]), ast.dump(ast.parse('temp', mode='eval').body))
        self.assertEqual(len(renderers), 1)
        self.assertEqual(ast.dump(renderers[0].args[1]),
                         ast.dump(ast.parse("receipt['owned_device_name']", mode='eval').body))
        self.assertIn("receipt['owned_device_name'] = owned_simulator_name(temp)", runner)
        self.assertIn("['xcrun', 'simctl', 'create', receipt['owned_device_name']", runner)
        self.assertIn("if d.get('name') == receipt['owned_device_name']", runner)

    def test_name_renderer_refuses_stale_ambiguous_or_unsafe_bindings(self):
        template = (HERE / 'DSC01OSPrerequisite.swift.in').read_text()
        name = copies.owned_simulator_name(Path('/synthetic/parlor-audit-ios-readiness-01-token'))
        for basename in ('parlor-audit-dsc01-apphost-01-token', 'parlor-audit-ios-readiness-1-token',
                         'user-phone', 'parlor-audit-ios-readiness-01-' + 'a' * 65):
            with self.subTest(basename=basename), self.assertRaises(RuntimeError):
                copies.owned_simulator_name(Path('/synthetic') / basename)
        for unsafe in ('Parlor-Audit-parlor-audit-dsc01-apphost-01-token', name + '"', name + '\n', None):
            with self.subTest(name=unsafe), self.assertRaises(RuntimeError):
                copies.render_owned_os_prerequisite(template, unsafe)
        for wrong in (template.replace(copies.SIMULATOR_NAME_TOKEN, '"stale"'), template + template):
            with self.assertRaises(RuntimeError): copies.render_owned_os_prerequisite(wrong, name)


class BindingAndImportTests(unittest.TestCase):
    def test_runner_import_is_read_only_and_no_longer_depends_on_archived_modules(self):
        with mock.patch('subprocess.Popen', side_effect=AssertionError('Import must not launch any worker')):
            runner = importlib.import_module('run_ios_readiness')
            importlib.reload(runner)
        for function in ('verify_public_interaction_receipts', 'verify_os_action_receipts', 'verify_os_pane_receipts'):
            self.assertTrue(callable(getattr(runner, function)))
        with self.assertRaises(RuntimeError): runner.control_manifest()
        text = (HERE / 'run_ios_readiness.py').read_text()
        # Permit only the two reviewed source-root helper loaders, with their guards intact.
        owned_imports = """TOOLCHAIN_HELPER = ROOT / 'scripts/verification/ios-readiness/toolchain_profiles.py'
if TOOLCHAIN_HELPER.is_symlink() or TOOLCHAIN_HELPER.resolve(strict=True) != TOOLCHAIN_HELPER:
    raise RuntimeError('Redirected native toolchain profile helper')
_toolchain_spec = importlib.util.spec_from_file_location('parlor_l08_toolchain_profiles', TOOLCHAIN_HELPER)
toolchains = importlib.util.module_from_spec(_toolchain_spec)
_toolchain_spec.loader.exec_module(toolchains)
LIFECYCLE_HELPER = TOOLCHAIN_HELPER.parent / 'simulator_lifecycle.py'
LIFECYCLE_TESTS = [TOOLCHAIN_HELPER.parent / name for name in (
    'test_simulator_lifecycle.py', 'test_simulator_lifecycle_integration.py')]
if LIFECYCLE_HELPER.is_symlink() or LIFECYCLE_HELPER.resolve(strict=True) != LIFECYCLE_HELPER:
    raise RuntimeError('Redirected simulator lifecycle helper')
_lifecycle_spec = importlib.util.spec_from_file_location('parlor_l08_simulator_lifecycle', LIFECYCLE_HELPER)
simulator_lifecycle = importlib.util.module_from_spec(_lifecycle_spec)
_lifecycle_spec.loader.exec_module(simulator_lifecycle)
"""
        self.assertEqual(text.count(owned_imports), 1)
        for loader in ('spec_from_file_location', 'module_from_spec', 'exec_module'):
            self.assertNotIn(loader, text.replace(owned_imports, '', 1))
        self.assertNotIn('2026-09-06-approved-policy-completion', text)

    def test_binding_destination_never_overwrites_or_traverses_a_campaign_symlink(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-binding-test-') as raw:
            root = Path(raw).resolve(); campaign = root / 'remediation-runs/test-campaign'; campaign.mkdir(parents=True)
            output = campaign / 'source-01.json'
            self.assertEqual(bind_source.checked_destination(output, root), output)
            output.write_text('preserved earlier evidence')
            with self.assertRaises(RuntimeError): bind_source.checked_destination(output, root)
            self.assertEqual(output.read_text(), 'preserved earlier evidence')
            linked = campaign.parent / 'linked'; linked.symlink_to(campaign, target_is_directory=True)
            with self.assertRaises(RuntimeError): bind_source.checked_destination(linked / 'new.json', root)
            with self.assertRaises(RuntimeError): bind_source.checked_destination(root / 'not-campaign.json', root)

    def test_explicit_binding_refuses_drift_and_never_grants_execution_approval(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-binding-test-') as raw:
            output = Path(raw).resolve() / 'binding.json'
            identity = dict(source_manifest_sha256='a' * 64, diff_sha256='b' * 64,
                            source_manifest=[['composeApp/src/Test.kt', 'c' * 64], ['docs/Test.md', 'd' * 64]])
            with mock.patch.object(bind_source, 'checked_destination', return_value=output), \
                    mock.patch.object(bind_source, 'identity', return_value=identity), \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(RuntimeError): bind_source.main([str(output), 'e' * 64, 'b' * 64])
                self.assertFalse(output.exists())
                self.assertEqual(bind_source.main([str(output), 'a' * 64, 'b' * 64]), 0)
            value = json.loads(output.read_text())
            self.assertEqual(value['binding_status'], 'REVIEW_REQUIRED_BOUND')
            self.assertEqual(value['copy_only'], [dict(path='composeApp/src/Test.kt', sha256='c' * 64)])
            self.assertIn('No build', value['policy'])


class CopiedPhaseTests(unittest.TestCase):
    def run_phase(self, build=0, stop=0, normalize=0, cd_missing=False, mode='disabled', changed=None, stale_phase=False):
        with tempfile.TemporaryDirectory(prefix='parlor-native-phase-test-') as raw:
            root = Path(raw).resolve(); temporary = root / 'parlor-audit-ios-readiness-99-fixture'
            source = temporary / 'copy'; source.mkdir(parents=True)
            (source / 'iosApp').mkdir()
            products = temporary / 'DerivedData/Build/Products/Debug-iphonesimulator'
            products.mkdir(parents=True)
            evidence = root / 'evidence/ios-readiness-99'; evidence.mkdir(parents=True)
            calls = root / 'calls'
            stop_receipt = evidence / 'embedded-gradle-stop.txt'
            signing_receipt = evidence / simulator_signing.RECEIPT_NAME
            helper = source / 'scripts/verification/ios-readiness/simulator_signing.py'
            helper.parent.mkdir(parents=True); shutil.copyfile(HERE / 'simulator_signing.py', helper)
            if stale_phase: signing_receipt.write_text('pre-existing phase evidence must never authorize a later build')
            wrapper = source / 'gradlew'
            wrapper.write_text('#!/bin/sh\nif [ "$1" = "--stop" ]; then\n'
                               f'  echo stop >> "{calls}"; exit {stop}\n'
                               f'else echo build >> "{calls}"; exit {build}\nfi\n')
            wrapper.chmod(0o700)
            normalizer = source / 'scripts/release/normalize_embedded_apple_framework.sh'
            normalizer.parent.mkdir(parents=True)
            normalizer.write_text(f'#!/bin/sh\necho normalize >> "{calls}"\nexit {normalize}\n')
            normalizer.chmod(0o700)
            stale = products / 'Parlor.app/Frameworks/ComposeApp.framework/stale'
            stale.parent.mkdir(parents=True); stale.write_text('stale fake output must not green a failed build')
            script = simulator_signing.render_owned_kotlin_phase((HERE / 'copied-kotlin-phase.sh.in').read_text(),
                temporary, evidence, 'iphonesimulator26.5', mode)
            if cd_missing:
                script = script.replace('cd "' + str(source) + '"', 'cd "' + str(source / 'missing') + '"')
            phase = root / 'phase.sh'; phase.write_text(script)
            environment = {**simulator_signing.CONTEXT, **simulator_signing.signing_overrides(mode),
                'PATH': '/usr/bin:/bin', 'SDK_NAME': 'iphonesimulator26.5', 'CODE_SIGN_STYLE': 'Automatic' if mode == 'disabled' else 'Manual',
                'SRCROOT': str(source / 'iosApp'), 'PROJECT_DIR': str(source / 'iosApp'), 'TARGET_BUILD_DIR': str(products)}
            if mode == 'adhoc': environment['EXPANDED_CODE_SIGN_IDENTITY'] = '-'
            environment.update({} if changed is None else changed)
            result = subprocess.run(['/bin/sh', str(phase)], capture_output=True, text=True, timeout=10,
                                    env=environment)
            self.assertEqual(stale.read_text(), 'stale fake output must not green a failed build')
            if stale_phase:
                self.assertEqual(signing_receipt.read_text(), 'pre-existing phase evidence must never authorize a later build')
            elif result.returncode == 0:
                self.assertEqual(simulator_signing.read_phase_receipt(signing_receipt, mode, 'iphonesimulator26.5', source)['mode'], mode)
            return result.returncode, calls.read_text().splitlines() if calls.exists() else [], \
                stop_receipt.read_text() if stop_receipt.exists() else None

    def test_build_failure_stops_immediately_and_never_normalizes_stale_output(self):
        self.assertEqual(self.run_phase(build=47), (47, ['build', 'stop'], 'build_exit=47\nstop_exit=0\n'))

    def test_stop_failure_cd_failure_and_normalizer_failure_are_not_masked(self):
        self.assertEqual(self.run_phase(stop=48), (48, ['build', 'stop'], 'build_exit=0\nstop_exit=48\n'))
        self.assertEqual(self.run_phase(cd_missing=True), (1, [], None))
        self.assertEqual(self.run_phase(normalize=49), (49, ['build', 'stop', 'normalize'], 'build_exit=0\nstop_exit=0\n'))

    def test_success_requires_ordered_build_stop_normalize(self):
        self.assertEqual(self.run_phase(), (0, ['build', 'stop', 'normalize'], 'build_exit=0\nstop_exit=0\n'))

    def test_explicit_adhoc_retains_failure_stale_output_and_stop_semantics(self):
        self.assertEqual(self.run_phase(mode='adhoc'), (0, ['build', 'stop', 'normalize'], 'build_exit=0\nstop_exit=0\n'))
        self.assertEqual(self.run_phase(mode='adhoc', build=47), (47, ['build', 'stop'], 'build_exit=47\nstop_exit=0\n'))
        self.assertEqual(self.run_phase(mode='adhoc', stop=48), (48, ['build', 'stop'], 'build_exit=0\nstop_exit=48\n'))
        self.assertEqual(self.run_phase(mode='adhoc', normalize=49), (49, ['build', 'stop', 'normalize'], 'build_exit=0\nstop_exit=0\n'))

    def test_preflight_refusal_stops_without_build_or_normalizing_stale_framework(self):
        for mode, changed in (
            ('disabled', {'EXPANDED_CODE_SIGN_IDENTITY': ''}),
            ('disabled', {'EXPANDED_CODE_SIGN_IDENTITY': '-'}),
            ('adhoc', {'EXPANDED_CODE_SIGN_IDENTITY': ''}),
            ('adhoc', {'EXPANDED_CODE_SIGN_IDENTITY': 'not-authorized-synthetic-identity'}),
            ('adhoc', {'PROVISIONING_PROFILE': 'not-authorized-synthetic-profile'}),
            ('adhoc', {'PLATFORM_NAME': 'iphoneos'}),
        ):
            with self.subTest(mode=mode, changed=changed):
                self.assertEqual(self.run_phase(mode=mode, changed=changed), (65, ['stop'], 'build_exit=65\nstop_exit=0\n'))
        self.assertEqual(self.run_phase(stale_phase=True), (65, ['stop'], 'build_exit=65\nstop_exit=0\n'))


if __name__ == '__main__':
    unittest.main()
