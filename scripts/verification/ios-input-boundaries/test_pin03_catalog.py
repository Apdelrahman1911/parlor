"""Executed source-copy/privacy/result controls, not native acceptance."""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import pin01_copy
import pin01_receipts
import pin02_capture
import pin03_catalog_copy as catalog
import run_pin03 as runner

ROOT = Path(__file__).resolve().parents[3]
TOKEN = 'A541C4A1-1E33-4E19-9B7C-1A1B7F110132'
SIMULATOR = 'B541C4A1-1E33-4E19-9B7C-1A1B7F110132'


def actual():
    source = {p: (ROOT / p).read_text() for p in catalog.MODIFIED}
    return source, {p: hashlib.sha256(v.encode()).hexdigest() for p, v in source.items()}


def snapshot():
    return dict(schema=1, nativeMode=0, overflow=0, invalid=0,
                rows=[[100, 4, 0, 6, 0, 0, 0, 0, 0, 0],
                      [101, 2, 3, 0, 1, 1, 0, 100, 20, 101],
                      [151, 2, 3, 1, 1, 0, 1, 100, 20, 151],
                      [152, 3, 3, 0, 0, 0, 0, 0, 0, 0]])


class CopyControls(unittest.TestCase):
    def test_failed_attachment_exports_select_exact_tests_not_failure_association(self):
        english = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish()'
        arabic = 'IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic()'
        outcomes = {english: 'Failed', arabic: 'Failed'}
        exports = runner.failed_method_exports(Path('/owned/tests.xcresult'), Path('/owned/exported'), outcomes)
        self.assertEqual(exports, [(Path('/owned/exported/failed-%02d' % i),
            ['export', 'attachments', '--path', '/owned/tests.xcresult', '--output-path',
             '/owned/exported/failed-%02d' % i, '--test-id', method])
            for i, method in enumerate((arabic, english), 1)])
        self.assertEqual(len(runner.failed_method_exports(Path('/r'), Path('/d'),
                         {english: 'Failed', arabic: 'Passed'})), 1)
        for changed in (None, {}, {english: 'Failed'}, {english: 'Passed', arabic: 'Passed'},
                        outcomes | {'IOSAppLaunchUITests/*': 'Failed'},
                        outcomes | {english: 'Skipped'}, outcomes | {arabic: True}):
            with self.assertRaises(ValueError):
                runner.failed_method_exports(Path('/r'), Path('/d'), changed)

    def test_result_decode_labels_cannot_overwrite_native_test_execution_timing(self):
        for arm in catalog.ARMS:
            labels = [runner.result_label(arm, kind) for kind in ('summary', 'tests')]
            self.assertEqual(len(set(labels)), 2)
            self.assertNotIn(arm + '-tests', labels)
        for arm, kind in (('unknown', 'tests'), ('original', 'execute')):
            with self.assertRaises(ValueError):
                runner.result_label(arm, kind)

    def test_native_test_command_runs_both_original_methods_once_without_repetition_options(self):
        base = ['/usr/bin/xcodebuild', '-project', 'iosApp/iosApp.xcodeproj',
                '-scheme', 'iosApp', '-configuration', 'Debug',
                '-destination', 'platform=iOS Simulator,id=' + SIMULATOR,
                '-parallel-testing-enabled', 'NO']
        result = Path('/owned/original/tests.xcresult')
        self.assertEqual(runner.test_command(base, result), [
            *base, 'test-without-building', '-resultBundlePath', str(result),
            '-only-testing:iosAppUITests/IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish',
            '-only-testing:iosAppUITests/IOSAppLaunchUITests/testAdditionalGamesExposeOnlyMultiplayerSetupInArabic'])

    def test_original_arm_is_byte_exact_and_both_observed_arms_never_write_root(self):
        sources, hashes = actual()
        self.assertEqual(catalog.render(sources, hashes, arm='original'), sources)
        for arm in ('compose', 'native'):
            plan = catalog.render(sources, hashes, arm=arm)
            self.assertEqual(set(plan), set(sources) | {pin01_copy.ADDITION})
            self.assertEqual(actual(), (sources, hashes))
            self.assertEqual(plan[pin01_copy.CONTENT].count('child.view.addGestureRecognizer(observer)'), int(arm == 'native'))
            for forbidden in ('consume(', 'delay(', 'Thread(', 'println(', 'displayName', 'GameId'):
                self.assertNotIn(forbidden, plan[pin01_copy.ADDITION])
            self.assertIn('armed = value == 0 || value == 6', plan[pin01_copy.ADDITION])

    def test_catalog_capture_keeps_original_wait_tap_and_successful_path(self):
        sources, hashes = actual()
        plan = catalog.render(sources, hashes, arm='compose')
        driver = plan[pin02_capture.DRIVER]
        self.assertIn('if !catalogOpened { PIN02FailureRequest.captureAfterFailedTransition(app) }', driver)
        self.assertEqual(driver.count('setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)'), 1)
        for gesture in ('control.tap()', 'app.swipeUp(velocity: .slow)', 'name.typeText("Table Test")'):
            self.assertEqual(sources[pin02_capture.DRIVER].count(gesture), driver.count(gesture))
        suffix = '\n' + (pin02_capture.HERE / 'PIN02FailureRequest.swift.in').read_text()
        driver = driver[:-len(suffix)].replace('        PIN02FailureRequest.configure(app)\n', '')
        driver = driver.replace('            PIN02FailureRequest.captureAfterFailedTransition(app)\n', '')
        driver = driver.replace(
            '            let catalogOpened = setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)\n'
            '            if !catalogOpened { PIN02FailureRequest.captureAfterFailedTransition(app) }\n'
            '            XCTAssertTrue(catalogOpened)',
            '            XCTAssertTrue(setupButton(app, prefix: copy.host).waitForExistence(timeout: 10))')
        self.assertEqual(driver, sources[pin02_capture.DRIVER])

    def test_stale_ambiguous_implicit_and_repeated_catalog_plans_reject(self):
        sources, hashes = actual()
        for p in sources:
            with self.subTest(path=p), self.assertRaises(ValueError):
                catalog.render(sources | {p: sources[p] + '\n'}, hashes, arm='native')
        for arm in ('', None, False, 'all'):
            with self.assertRaises(ValueError): catalog.render(sources, hashes, arm=arm)
        for anchor in ('    var selectedFilter by rememberSaveable', '.clickable(role = Role.Button, onClick = onOpen),'):
            changed = sources | {catalog.HOME: sources[catalog.HOME] + '\n' + anchor}
            digests = {p: hashlib.sha256(v.encode()).hexdigest() for p, v in changed.items()}
            with self.assertRaises(RuntimeError): catalog.render(changed, digests, arm='compose')

    def test_sequential_source_materialization_retains_original_then_validates_every_arm(self):
        sources, hashes = actual()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(strict=True)
            for p, text in sources.items():
                dest = root / p; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text(text)
            previous = hashes
            for arm in catalog.ARMS:
                previous = runner.apply_arm(root, sources, hashes, arm, previous=previous)
                runner.verify_inputs(root, previous)
            self.assertEqual(actual(), (sources, hashes))
            (root / catalog.HOME).write_text('unexpected change')
            with self.assertRaises(ValueError): runner.verify_inputs(root, previous)

    def test_archive_copy_rejects_traversal_links_duplicates_and_nonfresh_destinations(self):
        sources, _ = actual()
        sources.update({p: 'synthetic build input' for p in runner.ROOTS})
        def archive(extra=None):
            buffer = io.BytesIO()
            with tarfile.open(fileobj=buffer, mode='w') as tar:
                for path, text in sources.items():
                    entry = tarfile.TarInfo(path); content = text.encode(); entry.size = len(content)
                    tar.addfile(entry, io.BytesIO(content))
                if extra:
                    tar.addfile(extra, io.BytesIO(b'x') if extra.isfile() else None)
            return buffer.getvalue()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(strict=True)
            valid = root / 'valid'; valid.mkdir()
            manifest = runner.materialize(archive(), valid)
            runner.verify_inputs(valid, manifest)
            with self.assertRaises(ValueError): runner.materialize(archive(), valid)
            for index, (path, kind) in enumerate((('../config/a', tarfile.REGTYPE),
                ('/config/a', tarfile.REGTYPE), ('config/link', tarfile.SYMTYPE),
                ('config/hard', tarfile.LNKTYPE), ('config/fifo', tarfile.FIFOTYPE),
                ('gradlew', tarfile.REGTYPE))):
                entry = tarfile.TarInfo(path); entry.type = kind; entry.size = int(entry.isfile())
                entry.linkname = '/outside'
                dest = root / str(index); dest.mkdir()
                with self.subTest(path=path), self.assertRaises(ValueError): runner.materialize(archive(entry), dest)

    def test_hidden_source_additions_and_fake_nested_output_directories_reject(self):
        for relative in ('composeApp/.hidden.kt', 'composeApp/.hidden/Added.kt',
                         'composeApp/src/.gradle/Added.kt', 'composeApp/src/build/Added.kt',
                         'composeApp/src/.kotlin/Added.kt', 'composeApp/src/__pycache__/Added.kt'):
            with self.subTest(path=relative), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp).resolve(strict=True)
                sources = {'build.gradle.kts': b'root build', 'composeApp/build.gradle.kts': b'module build',
                           'composeApp/src/main.kt': b'public source'}
                for name, content in sources.items():
                    path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(content)
                manifest = {name: runner.sha(content) for name, content in sources.items()}
                runner.verify_inputs(root, manifest)
                added = root / relative; added.parent.mkdir(parents=True, exist_ok=True); added.write_bytes(b'unregistered source')
                with self.assertRaises(ValueError): runner.verify_inputs(root, manifest)

    def test_source_copy_requires_canonical_owned_root_without_admitting_redirects(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(strict=True)
            owned = root / 'owned'; owned.mkdir()
            source = owned / 'composeApp/source.kt'
            source.parent.mkdir(); source.write_bytes(b'public source')
            manifest = {'composeApp/source.kt': runner.sha(source.read_bytes())}
            runner.verify_inputs(owned, manifest)
            redirected = root / 'redirected'; redirected.symlink_to(owned, target_is_directory=True)
            with self.assertRaises(ValueError): runner.verify_inputs(redirected, manifest)
            # Resolve the freshly allocated owner before creating its source copy,
            # as RUNNER_TEMP and macOS /var -> /private/var require. Validation
            # remains strict; it never silently resolves a claimed input later.
            runner.verify_inputs(redirected.resolve(strict=True), manifest)

    def test_postbuild_input_verification_rejects_unknown_sources_but_not_owned_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(strict=True); file = root / 'composeApp/source.kt'
            file.parent.mkdir(); file.write_bytes(b'public test source')
            config = root / 'composeApp/build.gradle.kts'; config.write_bytes(b'declared build')
            manifest = {'composeApp/source.kt': runner.sha(file.read_bytes()),
                        'composeApp/build.gradle.kts': runner.sha(config.read_bytes())}
            generated = root / 'composeApp/build/generated.kt'; generated.parent.mkdir(); generated.write_bytes(b'generated')
            for name in ('composeApp/.gradle/state', 'composeApp/.kotlin/session'):
                cache = root / name; cache.parent.mkdir(parents=True); cache.write_bytes(b'owned output')
            runner.verify_inputs(root, manifest)
            unknown = root / 'composeApp/unregistered.kt'; unknown.write_bytes(b'unknown')
            with self.assertRaises(ValueError): runner.verify_inputs(root, manifest)


class ReceiptControls(unittest.TestCase):
    def test_catalog_numeric_extension_preserves_original_decoder_and_never_certifies_cause(self):
        raw = json.dumps(snapshot()).encode()
        result = catalog.decode_snapshot(raw)
        self.assertTrue(result['numeric_buffer_complete'])
        self.assertFalse(result['root_cause_proven'])
        self.assertFalse(result['native_input_neutrality'])
        self.assertEqual(result['snapshot'], snapshot())
        with self.assertRaises(ValueError): pin01_receipts.decode(raw)

    def test_private_invalid_duplicate_catalog_and_incomplete_receipts_fail_closed(self):
        for mutation in (
            lambda v: v.update(secret='private'),
            lambda v: v['rows'][0].__setitem__(1, 2),
            lambda v: v['rows'][1].__setitem__(1, 1),
            lambda v: v['rows'][2].__setitem__(6, 2),
            lambda v: v['rows'][2].__setitem__(0, 99),
            lambda v: v['rows'][1].__setitem__(2, 3.5),
            lambda v: v['rows'][1].__setitem__(7, True),
        ):
            value = snapshot(); mutation(value)
            with self.assertRaises(ValueError): catalog.decode_snapshot(json.dumps(value).encode())
        with self.assertRaises(ValueError): catalog.decode_snapshot(b'{"schema":1,"schema":1}')
        for key in ('overflow', 'invalid'):
            value = snapshot(); value[key] = 1
            self.assertFalse(catalog.decode_snapshot(json.dumps(value).encode())['numeric_buffer_complete'])
        value = snapshot(); value['rows'] = []
        self.assertFalse(catalog.decode_snapshot(json.dumps(value).encode())['numeric_buffer_complete'])

    def test_capture_requires_retained_request_owner_time_and_public_shape(self):
        value = dict(schema=1, token=TOKEN, simulator=SIMULATOR, bundle='me.parlor.ios.debug',
                     boot='C541C4A1-1E33-4E19-9B7C-1A1B7F110132', process=123,
                     installedMs=99, capturedMs=200, snapshot=snapshot())
        def decode(v, tokens={TOKEN}):
            return runner.decode_capture(json.dumps(v).encode(), tokens, SIMULATOR, 'me.parlor.ios.debug')
        self.assertTrue(decode(value)['numeric_buffer_complete'])
        with self.assertRaises(ValueError): decode(value, set())
        for key, changed in (('capturedMs', 150), ('simulator', TOKEN), ('bundle', 'me.parlor.ios'),
                             ('schema', True), ('process', 0), ('secret', 'private')):
            with self.subTest(key=key), self.assertRaises(ValueError): decode(value | {key: changed})

    def test_capture_retention_rejects_links_fifos_empty_and_oversized_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve(strict=True); data = root / 'capture'; data.write_bytes(b'public numeric test')
            self.assertEqual(runner.read_capture(data), data.read_bytes())
            link = root / 'link'; link.symlink_to(data)
            with self.assertRaises(OSError): runner.read_capture(link)
            hard = root / 'hard'; os.link(data, hard)
            with self.assertRaises(ValueError): runner.read_capture(hard)
            fifo = root / 'fifo'; os.mkfifo(fifo)
            with self.assertRaises(ValueError): runner.read_capture(fifo)
            for content in (b'', b'x' * (pin02_capture.MAX_BYTES + 1)):
                extra = root / 'extra'; extra.write_bytes(content)
                with self.assertRaises(ValueError): runner.read_capture(extra)

    def test_capture_mutation_during_read_is_rejected_without_qualifying_new_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'capture'; path.write_bytes(b'public numeric test')
            read = os.read; changed = False
            def mutate(fd, size):
                nonlocal changed
                value = read(fd, size)
                if not changed:
                    changed = True
                    with path.open('ab') as output: output.write(b'changed')
                return value
            with patch.object(os, 'read', side_effect=mutate), self.assertRaises(ValueError): runner.read_capture(path)

    def test_results_require_exact_two_actual_owned_journeys_including_failures(self):
        device = dict(deviceId=SIMULATOR, architecture='arm64', platform='iOS Simulator')
        summary = dict(result='Passed', totalTestCount=2, passedTests=2, failedTests=0,
                       skippedTests=0, expectedFailures=0, devicesAndConfigurations=[{'device': device}])
        nodes = [{'nodeType': 'Test Case', 'nodeIdentifier': t.removeprefix('iosAppUITests/') + '()',
                  'result': 'Passed'} for t in runner.TESTS]
        tests = dict(devices=[device], testNodes=nodes)
        self.assertEqual(len(runner.verify_results(summary, tests, SIMULATOR, 0)), 2)
        failed = copy.deepcopy(tests); failed['testNodes'][0]['result'] = 'Failed'
        runner.verify_results(summary | dict(result='Failed', passedTests=1, failedTests=1), failed, SIMULATOR, 65)
        for changed in (summary | {'totalTestCount': 0}, summary | {'skippedTests': 1}, summary | {'passedTests': 1},
                        summary | {'expectedFailures': False}):
            with self.assertRaises(ValueError): runner.verify_results(changed, tests, SIMULATOR, 0)
        with self.assertRaises(ValueError): runner.verify_results(summary, tests, TOKEN, 0)
        with self.assertRaises(ValueError): runner.verify_results(summary, tests | {'testNodes': nodes[:1]}, SIMULATOR, 0)
        with self.assertRaises(ValueError): runner.verify_results(summary, tests, SIMULATOR, 65)


if __name__ == '__main__':
    unittest.main()
