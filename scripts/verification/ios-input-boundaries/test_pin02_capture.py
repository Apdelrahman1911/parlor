"""Executed copy/retention controls; native app delivery and neutrality stay open."""
import copy
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import pin02_capture as captures

ROOT = Path(__file__).resolve().parents[3]
TOKEN = 'A541C4A1-1E33-4E19-9B7C-1A1B7F110132'
SIMULATOR = 'B541C4A1-1E33-4E19-9B7C-1A1B7F110132'
BUNDLE = 'com.parlor.app.debug'


def fixture():
    return {'schema': 1, 'token': TOKEN, 'simulator': SIMULATOR, 'bundle': BUNDLE,
            'boot': 'C541C4A1-1E33-4E19-9B7C-1A1B7F110132', 'process': 123,
            'installedMs': 100, 'capturedMs': 200,
            'snapshot': {'schema': 1, 'nativeMode': 0, 'overflow': 0, 'invalid': 0,
                         'rows': [[10, 4, 0, 0, 0, 0, 0, 0, 0, 0]]}}


def raw(value):
    return json.dumps(value).encode()


def decode(value):
    return captures.decode_capture(raw(value), token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)


class CopyControls(unittest.TestCase):
    def actual(self):
        sources = {p: (ROOT / p).read_text() for p in captures.MODIFIED}
        digests = {p: hashlib.sha256(v.encode()).hexdigest() for p, v in sources.items()}
        return sources, digests

    def test_actual_source_plan_only_adds_owned_setup_and_post_failed_transition_request(self):
        sources, digests = self.actual()
        output = captures.render(sources, digests)
        self.assertEqual(set(output), set(sources))
        self.assertEqual(self.actual(), (sources, digests))
        original = sources[captures.DRIVER]
        transformed = output[captures.DRIVER]
        self.assertEqual(transformed.count('PIN02FailureRequest.configure(app)'), 4)
        self.assertEqual(transformed.count('PIN02FailureRequest.captureAfterFailedTransition(app)'), 1)
        # Stripping precisely the additive statements/template restores ALL
        # original driver bytes, not merely a few handpicked gesture strings.
        restored = transformed[:-len('\n' + (captures.HERE / 'PIN02FailureRequest.swift.in').read_text())]
        restored = restored.replace('        PIN02FailureRequest.configure(app)\n', '')
        restored = restored.replace('            PIN02FailureRequest.captureAfterFailedTransition(app)\n', '')
        self.assertEqual(restored, original)
        self.assertIn('if result != .completed {\n            PIN02FailureRequest.captureAfterFailedTransition(app)', transformed)
        self.assertEqual(transformed.count('XCTWaiter.wait(for: [transitioned], timeout: 10)'), 1)
        self.assertEqual(transformed.count('control.tap()'), original.count('control.tap()'))

    def test_each_actual_source_and_anchor_mutation_rejects_without_writing(self):
        sources, digests = self.actual()
        for path in captures.MODIFIED:
            changed = sources | {path: sources[path] + '\n'}
            with self.subTest(path=path), self.assertRaises(ValueError): captures.render(changed, digests)
        anchors = [(captures.CONTAINER, '    private let contentController: UIViewController\n'),
                   (captures.CONTAINER, '        super.init(nibName: nil, bundle: nil)\n'),
                   (captures.DRIVER, '        let app = XCUIApplication()\n'),
                   (captures.DRIVER, '        if result != .completed {\n'
                                     '            // Capture after the single tap, not before: evidence collection')]
        for path, anchor in anchors:
            for text in (sources[path].replace(anchor, '// removed anchor'), sources[path] + anchor):
                changed = sources | {path: text}
                hashes = {p: hashlib.sha256(v.encode()).hexdigest() for p, v in changed.items()}
                with self.subTest(path=path), self.assertRaises((RuntimeError, ValueError)):
                    captures.render(changed, hashes)
        for changed in (sources | {'extra': 'bad'}, {captures.DRIVER: sources[captures.DRIVER]},
                        captures.render(sources, digests)):
            with self.assertRaises(ValueError): captures.render(changed, digests)
        self.assertEqual(self.actual(), (sources, digests))

    def test_observer_and_request_policies_are_one_shot_bounded_and_not_production(self):
        export = (captures.HERE / 'PIN02Capture.swift.in').read_text()
        request = (captures.HERE / 'PIN02FailureRequest.swift.in').read_text()
        for statement in ('guard Thread.isMainThread, !attempted', 'attempted = true',
                          'CFNotificationCenterRemoveObserver', 'O_EXCL | O_NOFOLLOW',
                          'fsync(fd)', 'encoded.count <= 135_168', 'Set(value.keys)',
                          'CFGetTypeID(number) != CFBooleanGetTypeID()'):
            self.assertIn(statement, export)
        for statement in ('timeout: 3', '"originalTransitionFailed": true',
                          '"ackObserved": result == .completed', 'not capture custody'):
            self.assertIn(statement, request)
        for forbidden in ('Timer(', 'DispatchQueue', 'Thread.sleep', '.tap(', '.activate(', '.launch(',
                          'UserDefaults', 'localizedDescription', 'userInfo[', '.screenshot(', 'removeItem('):
            self.assertNotIn(forbidden, export + request)


class ReceiptControls(unittest.TestCase):
    def test_early_compose_rows_and_incomplete_numeric_buffers_are_retained_not_certified(self):
        value = fixture()
        self.assertEqual(decode(value)['capture'], value)
        self.assertTrue(decode(value)['numeric_buffer_complete'])
        self.assertFalse(decode(value)['source_runtime_product_cleanup_verified'])
        self.assertFalse(decode(value)['native_input_neutrality'])
        for field, wrong in (('overflow', 1), ('invalid', 512), ('rows', [])):
            changed = copy.deepcopy(value); changed['snapshot'][field] = wrong
            reviewed = decode(changed)
            self.assertEqual(reviewed['capture'], changed)
            self.assertFalse(reviewed['numeric_buffer_complete'])
        changed = copy.deepcopy(value); changed['snapshot']['rows'][0][0] = 201
        with self.assertRaisesRegex(ValueError, 'newer than capture'): decode(changed)

    def test_foreign_private_missing_duplicate_and_non_numeric_receipts_reject(self):
        for field, wrong in (('token', SIMULATOR), ('simulator', TOKEN), ('bundle', 'com.parlor.app'),
                             ('schema', True), ('process', True), ('process', 0), ('boot', 'bad'),
                             ('installedMs', True), ('capturedMs', 99)):
            changed = fixture(); changed[field] = wrong
            with self.subTest(field=field), self.assertRaises(ValueError): decode(changed)
        for field in captures.KEYS:
            changed = fixture(); del changed[field]
            with self.subTest(field=field), self.assertRaises(ValueError): decode(changed)
        for level in ('envelope', 'snapshot'):
            for key in ('state', 'playerId', 'role', 'secret', 'NSError', 'hierarchy'):
                changed = fixture(); destination = changed if level == 'envelope' else changed['snapshot']
                destination[key] = 'MUST-NOT-BE-RETAINED'
                with self.subTest(level=level, key=key), self.assertRaises(ValueError): decode(changed)
        for data in (b'', b'x' * (captures.MAX_BYTES + 1),
                     raw(fixture()).replace(b'"schema": 1', b'"schema": 1, "schema": 1', 1),
                     raw(fixture()).replace(b'"capturedMs": 200', b'"capturedMs": NaN')):
            with self.assertRaises(ValueError):
                captures.decode_capture(data, token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
        for field, wrong in (('overflow', True), ('invalid', -1), ('invalid', 513), ('rows', 'bad'),
                             ('nativeMode', True), ('schema', 2)):
            changed = fixture(); changed['snapshot'][field] = wrong
            with self.subTest(field=field), self.assertRaises(ValueError): decode(changed)

    def test_exact_owned_file_retention_hashes_bytes_and_never_uses_ack_or_overwrites(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source = root / 'capture.json'; destination = root / 'retained.json'
            data = raw(fixture()); source.write_bytes(data)
            result = captures.retain_file(source, destination, token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
            self.assertEqual(destination.read_bytes(), data)
            self.assertEqual(source.read_bytes(), data)
            self.assertEqual(result['retained_sha256'], hashlib.sha256(data).hexdigest())
            self.assertFalse(result['source_runtime_product_cleanup_verified'])
            with self.assertRaises(FileExistsError):
                captures.retain_file(source, destination, token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
            source.unlink()
            with self.assertRaises(FileNotFoundError):
                captures.retain_file(source, root / 'missing.json', token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
            self.assertFalse((root / 'missing.json').exists())

    def test_symlink_hardlink_fifo_directory_oversize_and_private_files_never_export(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source = root / 'capture.json'; source.write_bytes(raw(fixture()))
            single = root / 'single'; single.write_bytes(raw(fixture()))
            link = root / 'symlink'; link.symlink_to(single)
            hard = root / 'hardlink'; os.link(source, hard)
            fifo = root / 'fifo'; os.mkfifo(fifo)
            large = root / 'oversize'; large.write_bytes(b'x' * (captures.MAX_BYTES + 1))
            private = root / 'private'; private.write_bytes(raw(fixture() | {'role': 'MUST-NOT-LEAVE'}))
            for index, unsafe in enumerate((source, link, hard, fifo, root, large, private)):
                destination = root / ('rejected-' + str(index))
                with self.subTest(unsafe=unsafe.name), self.assertRaises((OSError, ValueError)):
                    captures.retain_file(unsafe, destination, token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
                self.assertFalse(destination.exists())

    def test_actual_reader_rejects_a_file_changed_mid_read_and_keeps_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source = root / 'capture.json'; source.write_bytes(raw(fixture()))
            original = os.read; mutated = False
            def changed(fd, count):
                nonlocal mutated
                result = original(fd, count)
                if not mutated:
                    mutated = True
                    source.write_bytes(raw(fixture()) + b' ')
                return result
            with patch.object(captures.os, 'read', side_effect=changed), self.assertRaises(ValueError):
                captures.retain_file(source, root / 'rejected', token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
            self.assertTrue(source.exists())
            self.assertFalse((root / 'rejected').exists())


class AppleCompileControls(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'darwin', 'Capture typecheck requires actual Apple SDK')
    def test_exporter_and_actual_copied_xctest_driver_typecheck_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory(prefix='parlor-pin02-compile-') as temporary:
            root = Path(temporary)
            exporter = root / 'Capture.swift'; exporter.write_text((captures.HERE / 'PIN02Capture.swift.in').read_text())
            sources = {p: (ROOT / p).read_text() for p in captures.MODIFIED}
            digests = {p: hashlib.sha256(v.encode()).hexdigest() for p, v in sources.items()}
            driver = root / 'Driver.swift'; driver.write_text(captures.render(sources, digests)[captures.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, timeout=45).strip()
            platform_path = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-platform-path'],
                                                    text=True, timeout=45).strip()
            architecture = {'arm64': 'arm64', 'x86_64': 'x86_64'}[platform.machine()]
            result = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', architecture + '-apple-ios16.0-simulator', '-F', platform_path + '/Developer/Library/Frameworks',
                '-module-cache-path', str(root / 'cache'), str(exporter), str(driver)],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
            self.assertEqual(result.returncode, 0, result.stdout)

    @unittest.skipUnless(sys.platform == 'darwin', 'Actual Darwin IPC control requires macOS')
    def test_real_foundation_cross_process_export_one_shot_retention_and_observer_cleanup(self):
        with tempfile.TemporaryDirectory(prefix='parlor-pin02-ipc-') as temporary:
            root = Path(temporary)
            source = root / 'Capture.swift'; source.write_text((captures.HERE / 'PIN02Capture.swift.in').read_text())
            control = root / 'Control.swift'; control.write_text((captures.HERE / 'PIN02FoundationControl.swift.in').read_text())
            binary = root / 'control'
            result = subprocess.run(['/usr/bin/xcrun', '--sdk', 'macosx', 'swiftc', '-D', 'DEBUG',
                '-D', 'PIN02_CONTROL', '-module-cache-path', str(root / 'cache'), str(source), str(control),
                '-o', str(binary)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
            self.assertEqual(result.returncode, 0, result.stdout)
            parent = root / 'owned'; parent.mkdir()
            listener = subprocess.Popen([str(binary), 'listen', TOKEN, SIMULATOR, str(parent)],
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            try:
                # Bound even readiness reads; only the caller-owned listener is
                # terminated on failure. No unrelated process/Simulator cleanup.
                import select
                self.assertTrue(select.select([listener.stdout], [], [], 5)[0], 'Control readiness timeout')
                self.assertEqual(listener.stdout.readline().strip(), 'PIN02_CONTROL_READY')
                subprocess.run([str(binary), 'request', SIMULATOR], check=True, timeout=5)
                time.sleep(0.1) # Control harness only, never an app/touch event delay.
                directory = parent / ('parlor-pin02-' + TOKEN)
                self.assertFalse(directory.exists(), 'Foreign signal must not capture')
                subprocess.run([str(binary), 'request', TOKEN], check=True, timeout=5)
                output, _ = listener.communicate(timeout=10)
                self.assertEqual(listener.returncode, 0, output)
                self.assertIn('PIN02_CONTROL_ONE_EXPORT_REMOVED', output)
                reviewed = captures.retain_file(directory / 'capture.json', root / 'retained.json',
                    token=TOKEN, simulator=SIMULATOR, bundle=BUNDLE)
                self.assertEqual(reviewed['capture']['process'], listener.pid)
                self.assertTrue(reviewed['numeric_buffer_complete'])
                self.assertFalse(reviewed['source_runtime_product_cleanup_verified'])
                self.assertFalse(reviewed['native_input_neutrality'])
            finally:
                if listener.poll() is None:
                    listener.terminate()
                    listener.communicate(timeout=5)


if __name__ == '__main__':
    unittest.main()
