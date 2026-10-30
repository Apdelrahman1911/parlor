"""Portable copy-plan/fixture/parser controls. Not native execution or causality."""
import copy
import hashlib
import inspect
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import pin05_name_input as name
import run_pin03 as runner


def originals():
    sources = {p: name.fixture(p) for p in name.MODIFIED}
    return sources, {p: hashlib.sha256(t.encode()).hexdigest() for p, t in sources.items()}


def sample(mode=0):
    rows = [[1, 1, 0, 0, 0, 0, 0, 0, 0, 0],
            [2, 1, 2, 1, 0, 0, 0, 0, 0, 0],
            [3, 2, 0, 0, 0, 0, 0, 0, 0, 0],
            [4, 3, 0, 0, 0, 0, 0, 0, 0, 0],
            [5, 4, 0, 0, 0, 0, 0, 0, 0, 0],
            [6, 4, 1, 0, 0, 0, 0, 0, 0, 0],
            [7, 3, 1, 0, 0, 0, 0, 0, 0, 0]]
    if mode: rows.append([8, 5, 0, 1, 1, 0, 0, 0, 0, 0])
    return dict(schema=5, nativeMode=mode, overflow=0, invalid=0, rows=rows)


class NameInputPlanControls(unittest.TestCase):
    def test_original_arm_is_byte_exact_and_allocates_no_observer(self):
        sources, digests = originals()
        self.assertEqual(name.render(sources, digests, arm='original'), sources)
        self.assertEqual(digests, name.INPUTS)

    def test_copied_driver_has_only_failed_text_capture_and_original_successful_path(self):
        sources, digests = originals()
        for arm in ('compose', 'native'):
            with self.subTest(arm=arm):
                driver = name.render(sources, digests, arm=arm)[name.DRIVER]
                suffix = '\n' + (name.HERE / 'PIN02FailureRequest.swift.in').read_text()
                self.assertTrue(driver.endswith(suffix))
                restored = driver[:-len(suffix)].replace('        PIN02FailureRequest.configure(app)\n', '')
                restored = restored.replace('            PIN02FailureRequest.captureAfterFailedTransition(app)\n', '')
                self.assertEqual(restored, sources[name.DRIVER])
                self.assertEqual(driver.count('captureAfterFailedTransition(app)'), 1)
                for action in ('name.typeText("Table Test")', 'control.tap()', 'timeout: 10',
                               'assertTextValue(name, in: app, equals: "Table Test"', 'app.swipeUp'):
                    self.assertEqual(driver.count(action), sources[name.DRIVER].count(action))
                self.assertIn('if result != .completed {\n            PIN02FailureRequest', driver)

    def test_shared_name_callback_and_confirmation_are_not_replaced_or_replayed(self):
        sources, digests = originals()
        plan = name.render(sources, digests, arm='compose')
        original = sources[name.NAME]
        changed = plan[name.NAME]
        for action in ('name = RoomInputPolicy.sanitizeDisplayNameInput(input)',
                       'onClick = { onConfirm(sanitized) }', 'onBack = onBack',
                       'focusManager.clearFocus()', 'keyboardController?.hide()'):
            self.assertEqual(changed.count(action), original.count(action))
        self.assertIn('callback(pin05Owner, 0, input.isEmpty())', changed)
        self.assertIn('callback(pin05Owner, 1, name.isEmpty())', changed)
        self.assertIn('onDispose { PIN05Observation.unmount(pin05Owner) }', changed)
        self.assertNotIn('.onFocusEvent', changed)
        self.assertNotIn('.pointerInput', changed)
        interceptor = plan[name.INTERCEPTOR]
        self.assertIn('if (owner == null) next.startInputMethod(request)', interceptor)
        self.assertEqual(interceptor.count('request.onEditCommand(commands)'), 1)
        self.assertIn('PlatformTextInputMethodRequest by request', interceptor)
        self.assertIn('finally {', interceptor)
        for forbidden in ('launch {', 'delay(', 'TextInputService(', 'println(', 'commands.toList', 'catch ('):
            self.assertNotIn(forbidden, interceptor)

    def test_native_queries_are_post_failure_bounded_public_booleans_only(self):
        sources, digests = originals()
        compose = name.render(sources, digests, arm='compose')[name.CONTAINER]
        native = name.render(sources, digests, arm='native')[name.CONTAINER]
        self.assertNotIn('view.isFirstResponder', compose)
        self.assertIn('[weak self]', native)
        self.assertIn('visited < 64, pending.count + view.subviews.count <= 64', native)
        self.assertIn('view.isFirstResponder', native)
        self.assertIn('integer(value["schema"], range: 5...5)', native)
        self.assertIn('guard Thread.isMainThread', native)
        for forbidden in ('accessibilityLabel', 'value(forKey', 'String(describing: view', 'insertText(',
                          'becomeFirstResponder(', 'endEditing(', 'UIPasteboard', 'Timer(', '.swizzle'):
            self.assertNotIn(forbidden, native)

    def test_wrong_source_rehashed_changes_implicit_and_repeated_plans_fail_closed(self):
        sources, digests = originals()
        for path in sources:
            changed = sources | {path: sources[path] + '\n'}
            changed_hashes = digests | {path: hashlib.sha256(changed[path].encode()).hexdigest()}
            for bindings in (digests, changed_hashes):
                with self.subTest(path=path), self.assertRaises(ValueError):
                    name.render(changed, bindings, arm='compose')
        for arm in ('', None, True, 'all'):
            with self.assertRaises(ValueError): name.render(sources, digests, arm=arm)
        plan = name.render(sources, digests, arm='native')
        with self.assertRaises(ValueError): name.render(plan, digests, arm='compose')
        with self.assertRaises(ValueError): name.render(sources | {'extra': ''}, digests, arm='original')


class NameInputFixtureControls(unittest.TestCase):
    def test_registered_templates_reject_unknown_changed_redirected_linked_and_oversized_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp).resolve(strict=True)
            for filename, digest in name.TEMPLATES.items():
                with self.subTest(filename=filename):
                    raw = (name.HERE / filename).read_bytes()
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), digest)
                    target = directory / filename
                    target.write_bytes(raw)
                    self.assertEqual(name.template(filename, directory=directory).encode(), raw)
                    for changed in (raw + b'\n', b'', b'x' * (name.MAX_BYTES + 1)):
                        target.write_bytes(changed)
                        with self.assertRaises(ValueError): name.template(filename, directory=directory)
                    target.unlink()
                    with self.assertRaises(FileNotFoundError): name.template(filename, directory=directory)
                    source = directory / 'original'
                    source.write_bytes(raw)
                    target.symlink_to(source)
                    with self.assertRaises(ValueError): name.template(filename, directory=directory)
                    target.unlink(); os.link(source, target)
                    with self.assertRaises(ValueError): name.template(filename, directory=directory)
                    target.unlink(); source.unlink()
            for unknown in ('', '../PIN05TextObservation.kt.in', None, True, 'other.kt.in'):
                with self.assertRaises(ValueError): name.template(unknown, directory=directory)

    def test_changed_template_fails_before_render_and_original_arm_needs_no_templates(self):
        sources, digests = originals()
        with patch.object(name, 'template', side_effect=ValueError('Changed template')):
            self.assertEqual(name.render(sources, digests, arm='original'), sources)
            for arm in ('compose', 'native'):
                with self.assertRaises(ValueError): name.render(sources, digests, arm=arm)
        inherited = name.pin02_capture.render_container
        with patch.object(name.pin02_capture, 'render_container',
                          side_effect=lambda text: inherited(text) + '\n'):
            with self.assertRaises(ValueError): name.render(sources, digests, arm='compose')

    def test_hermetic_full_original_bytes_need_no_git_network_or_current_directory(self):
        old = Path.cwd()
        with tempfile.TemporaryDirectory() as temp:
            try:
                os.chdir(temp)
                with patch.object(subprocess, 'run', side_effect=AssertionError('No fallback')), \
                     patch.object(subprocess, 'check_output', side_effect=AssertionError('No Git')):
                    sources, digests = originals()
                    self.assertEqual(digests, name.INPUTS)
                    self.assertEqual(set(sources), set(name.MODIFIED))
            finally:
                os.chdir(old)

    def test_corrupt_manifest_source_links_and_missing_fixtures_do_not_fall_back(self):
        path = name.MAIN
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp).resolve(strict=True) / 'fixtures'
            shutil.copytree(name.FIXTURES, directory)
            target = directory / (Path(path).name + '.source')
            original = target.read_bytes()
            target.write_bytes(original + b'\n')
            with self.assertRaises(ValueError): name.fixture(path, directory=directory)
            target.unlink()
            with self.assertRaises(FileNotFoundError): name.fixture(path, directory=directory)
            source = directory.parent / 'source'
            source.write_bytes(original)
            target.symlink_to(source)
            with self.assertRaises(ValueError): name.fixture(path, directory=directory)
            target.unlink(); os.link(source, target)
            with self.assertRaises(ValueError): name.fixture(path, directory=directory)
            target.unlink(); target.write_bytes(original)
            manifest = directory / 'manifest.json'
            manifest.write_bytes(manifest.read_bytes() + b'\n')
            with self.assertRaises(ValueError): name.fixture(path, directory=directory)
        for unknown in ('', '../MainViewController.kt', 'other', None, True):
            with self.assertRaises(ValueError): name.fixture(unknown)


class NameInputRunnerControls(unittest.TestCase):
    def test_profile_pins_original_source_runtime_device_inputs_and_decoder_without_changing_defaults(self):
        selected = ('name-input', name.SOURCE, name.RUNTIME, name.DEVICE)
        self.assertIs(runner.renderer_for_profile(*selected), name.render)
        self.assertEqual(runner.inputs_for_profile('name-input'), name.MODIFIED)
        self.assertIs(runner.decoder_for_profile('name-input'), name.decode_snapshot)
        self.assertIs(runner.renderer_for_profile('catalog', *selected[1:]), runner.catalog.render)
        for profile in ('catalog', 'original-host'):
            self.assertEqual(runner.inputs_for_profile(profile), runner.catalog.MODIFIED)
            self.assertIs(runner.decoder_for_profile(profile), runner.catalog.decode_snapshot)
        for index, wrong in enumerate(('unknown', 'b95b5a70aea6b186deeb66d2fe1b4476f7bb7dfa',
                                       'com.apple.CoreSimulator.SimRuntime.iOS-26-2',
                                       'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro')):
            changed = list(selected); changed[index] = wrong
            with self.subTest(index=index), self.assertRaises(ValueError): runner.renderer_for_profile(*changed)
        for profile in ('', None, True, 'name-input-original'):
            with self.assertRaises(ValueError): runner.inputs_for_profile(profile)
            with self.assertRaises(ValueError): runner.decoder_for_profile(profile)

    def test_all_three_owned_copy_plans_preserve_original_and_cannot_reuse_observer_as_original(self):
        sources, digests = originals()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve(strict=True)
            for path, text in sources.items():
                target = root / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(text)
            previous = digests
            for arm in name.ARMS:
                previous = runner.apply_arm(root, sources, digests, arm,
                                            previous=previous, renderer=name.render)
                runner.verify_inputs(root, previous)
                self.assertEqual(previous, digests | {p: hashlib.sha256(t.encode()).hexdigest()
                                                     for p, t in name.render(sources, digests, arm=arm).items()})
            before = {p: (root / p).read_bytes() for p in previous}
            with self.assertRaises(ValueError):
                runner.apply_arm(root, sources, digests, 'original', previous=previous, renderer=name.render)
            self.assertEqual(before, {p: (root / p).read_bytes() for p in previous})
        self.assertEqual({p: hashlib.sha256(t.encode()).hexdigest() for p, t in sources.items()}, digests)

    def test_failed_request_capture_requires_envelope_ownership_and_closed_name_schema(self):
        token = 'A541C4A1-1E33-4E19-9B7C-1A1B7F110132'
        simulator = 'B541C4A1-1E33-4E19-9B7C-1A1B7F110132'
        value = dict(schema=1, token=token, simulator=simulator, bundle='me.parlor.ios.debug',
                     boot='C541C4A1-1E33-4E19-9B7C-1A1B7F110132', process=123,
                     installedMs=1, capturedMs=9, snapshot=sample(1))
        def decode(item, *, tokens=None, profile='name-input'):
            return runner.decode_capture(json.dumps(item).encode(), {token} if tokens is None else tokens,
                                         simulator, 'me.parlor.ios.debug', profile=profile)
        self.assertEqual(decode(value)['rows'], 8)
        self.assertEqual(decode(value)['capture_sha256'], hashlib.sha256(json.dumps(value).encode()).hexdigest())
        for key, changed in (('capturedMs', 7), ('simulator', token), ('bundle', 'me.parlor.ios'),
                             ('schema', True), ('process', 0), ('name', 'DO-NOT-EXPORT')):
            with self.subTest(key=key), self.assertRaises(ValueError): decode(value | {key: changed})
        with self.assertRaises(ValueError): decode(value, tokens=set())
        with self.assertRaises(ValueError): decode(value, profile='catalog')
        with self.assertRaises(ValueError): decode(value, profile='unregistered')
        for snapshot in (sample(1) | {'schema': 1}, sample(1) | {'overflow': 1}, sample(1) | {'role': 'private'}):
            with self.assertRaises(ValueError): decode(value | {'snapshot': snapshot})


class NameInputSDKControls(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'darwin', 'Name-input container typecheck requires actual Apple SDK')
    def test_copied_container_and_driver_typecheck_with_public_simulator_sdk_and_explicit_bridge_stub(self):
        # Stub only the three bridge signatures. This verifies Swift/UIKit syntax,
        # not exported Kotlin Native signatures, application linkage or input.
        stub = '''public enum MainViewControllerKt {
    public static func PIN05InputSnapshot() -> String { "{}" }
    public static func PIN05NativeStatus(firstResponder: Bool, textResponder: Bool) {}
    public static func PIN05RejectNativeStatus() {}
}
'''
        with tempfile.TemporaryDirectory(prefix='parlor-pin05-compile-') as temp:
            root = Path(temp).resolve(strict=True)
            environment = {**os.environ, 'TMPDIR': str(root)}
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, timeout=45, env=environment).strip()
            platform_path = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-platform-path'],
                                                    text=True, timeout=45, env=environment).strip()
            target = {'arm64': 'arm64', 'x86_64': 'x86_64'}[platform.machine()] + '-apple-ios16.0-simulator'
            bridge = root / 'BridgeStub.swift'; bridge.write_text(stub)
            base = ['/usr/bin/xcrun', 'swiftc', '-D', 'DEBUG', '-sdk', sdk, '-target', target,
                    '-F', platform_path + '/Developer/Library/Frameworks',
                    '-I', platform_path + '/Developer/usr/lib', '-I', str(root),
                    '-module-cache-path', str(root / 'cache')]
            result = subprocess.run([*base, '-emit-module', '-parse-as-library', '-module-name', 'ComposeApp',
                                     '-emit-module-path', str(root / 'ComposeApp.swiftmodule'), str(bridge)],
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout)
            sources, digests = originals()
            for arm in ('compose', 'native'):
                plan = name.render(sources, digests, arm=arm)
                container = root / 'Container.swift'; container.write_text(plan[name.CONTAINER])
                driver = root / 'Driver.swift'; driver.write_text(plan[name.DRIVER])
                result = subprocess.run([*base, '-typecheck', str(container), str(driver)],
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180, env=environment)
                self.assertEqual(result.returncode, 0, result.stdout)

    def test_sdk_control_keeps_scratch_owned_tests_both_arms_and_rejects_compiler_failure(self):
        body = inspect.unwrap(
            self.test_copied_container_and_driver_typecheck_with_public_simulator_sdk_and_explicit_bridge_stub.__func__)
        with patch.object(subprocess, 'check_output', return_value='/explicit/sdk'), \
             patch.object(platform, 'machine', return_value='arm64'), \
             patch.object(subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout='')) as run:
            body(self)
            self.assertEqual(run.call_count, 3)
            for call in run.call_args_list:
                command = call.args[0]
                scratch = Path(call.kwargs['env']['TMPDIR'])
                self.assertEqual(command[:6], ['/usr/bin/xcrun', 'swiftc', '-D', 'DEBUG', '-sdk', '/explicit/sdk'])
                self.assertEqual(command[command.index('-module-cache-path') + 1], str(scratch / 'cache'))
                self.assertIn('arm64-apple-ios16.0-simulator', command)
                self.assertNotIn('build', command)
            self.assertFalse(scratch.exists())
        for failed_stage in range(3):
            results = [SimpleNamespace(returncode=0, stdout='') for _ in range(failed_stage)]
            results.append(SimpleNamespace(returncode=1, stdout='Synthetic compiler failure'))
            with patch.object(subprocess, 'check_output', return_value='/explicit/sdk'), \
                 patch.object(platform, 'machine', return_value='arm64'), \
                 patch.object(subprocess, 'run', side_effect=results):
                with self.assertRaises(AssertionError): body(self)


class NameInputSnapshotControls(unittest.TestCase):
    def decode(self, value):
        return name.decode_snapshot(json.dumps(value).encode())

    def test_closed_numeric_modes_count_categories_without_interpreting_cause(self):
        for mode in (0, 1):
            result = self.decode(sample(mode))
            self.assertEqual(result['rows'], 7 + mode)
            self.assertEqual(result['category_counts'][3], 2)
            self.assertIn('not causal', result['scope'])

    def test_private_duplicate_boolean_nonnumeric_nonfinite_and_unknown_schema_reject(self):
        value = sample()
        for key in ('name', 'commands', 'value', 'role', 'credential'):
            with self.assertRaises(ValueError): self.decode(value | {key: 'DO-NOT-EXPORT'})
        for key, item in (('schema', 1), ('schema', True), ('nativeMode', True),
                          ('overflow', 1), ('invalid', 1), ('invalid', False), ('rows', [])):
            with self.assertRaises(ValueError): self.decode(value | {key: item})
        for field, item in ((0, True), (0, float('inf')), (0, -1), (1, 6), (1, 1.5),
                            (2, 3), (3, 2), (4, 1), (5, 1), (9, 'secret')):
            mutated = copy.deepcopy(value); mutated['rows'][0][field] = item
            with self.subTest(field=field, item=item), self.assertRaises(ValueError): self.decode(mutated)
        duplicate = json.dumps(value).replace('"schema": 5', '"schema": 5, "schema": 5')
        with self.assertRaises(ValueError): name.decode_snapshot(duplicate.encode())
        with self.assertRaises(ValueError): name.decode_snapshot(b'x' * (name.MAX_BYTES + 1))

    def test_clock_reversal_overflow_reserved_columns_and_mode_mismatch_reject(self):
        value = sample(1)
        mutations = []
        for index, field, item in ((1, 0, 0), (0, 0, 1e12 + 1), (1, 5, 1),
                                  (2, 3, 1), (7, 2, 1)):
            changed = copy.deepcopy(value); changed['rows'][index][field] = item; mutations.append(changed)
        mutations.extend([value | {'nativeMode': 0}, value | {'rows': value['rows'] * 20}])
        for changed in mutations:
            with self.assertRaises(ValueError): self.decode(changed)


if __name__ == '__main__':
    unittest.main()
