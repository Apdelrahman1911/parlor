"""Executed copy-only frozen setup controls; not iOS application qualification."""
import hashlib
import os
from pathlib import Path
import platform
import shlex
import subprocess
import sys
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin03_catalog_copy as catalog
import pin06_public_setup_pair as pair
import run_pin03 as runner


def actual():
    return {p: pair.fixture(p) for p in pair.MODIFIED}


class PublicSetupPairControls(unittest.TestCase):
    def test_exact_original_arm_and_three_plans_are_bound_to_frozen_source(self):
        sources = actual()
        self.assertEqual(pair.render(sources, pair.INPUTS, arm='original'), sources)
        for arm in ('compose', 'native'):
            plan = pair.render(sources, pair.INPUTS, arm=arm)
            self.assertEqual(set(plan), set(sources) | {pin01_copy.ADDITION})
            self.assertEqual(plan[pin01_copy.CONTENT].count('child.view.addGestureRecognizer(observer)'),
                             int(arm == 'native'))
        self.assertEqual(actual(), sources)

    def test_remove_only_failed_capture_additions_restores_complete_original_driver(self):
        sources = actual()
        for arm in ('compose', 'native'):
            driver = pair.render(sources, pair.INPUTS, arm=arm)[pin02_capture.DRIVER]
            suffix = '\n' + (pair.HERE / 'PIN02FailureRequest.swift.in').read_text()
            self.assertTrue(driver.endswith(suffix))
            driver = driver[:-len(suffix)].replace('        PIN02FailureRequest.configure(app)\n', '')
            driver = driver.replace('            PIN02FailureRequest.captureAfterFailedTransition(app)\n', '')
            driver = driver.replace(
                '            let catalogOpened = setupButton(app, prefix: copy.host).waitForExistence(timeout: 10)\n'
                '            if !catalogOpened { PIN02FailureRequest.captureAfterFailedTransition(app) }\n'
                '            XCTAssertTrue(catalogOpened)',
                '            XCTAssertTrue(setupButton(app, prefix: copy.host).waitForExistence(timeout: 10))')
            driver = driver.replace(pair.OWNER_CALL, '                try tapSettledControl(button)')
            driver = driver.replace(pair.SIGNATURE, '    private func tapSettledControl(_ control: XCUIElement) throws {')
            driver = driver.replace(pair.CAPTURE, '')
            self.assertEqual(driver, sources[pin02_capture.DRIVER])

    def test_original_gestures_queries_predicates_deadlines_and_tripwire_remain(self):
        sources = actual()
        original = sources[pin02_capture.DRIVER]
        driver = pair.render(sources, pair.INPUTS, arm='native')[pin02_capture.DRIVER]
        for anchor in ('control.tap()', 'app.swipeUp(velocity: .slow)', 'name.typeText("Table Test")',
                       'name.typeText("\\n")', 'destination.exists && !source.exists',
                       'XCTWaiter.wait(for: [settled], timeout: 10)',
                       'Public setup must not request network access', 'continueAfterFailure = false'):
            self.assertEqual(driver.count(anchor), original.count(anchor), anchor)
        self.assertLess(driver.index(pair.CAPTURE), driver.index('print("Public control settlement: "'))
        settled = driver[driver.index(pair.SIGNATURE):driver.index('    private func verifyKeyboardCycle')]
        self.assertLess(settled.index('let result = XCTWaiter.wait'), settled.index(pair.CAPTURE))
        self.assertIn('if result != .completed {\n' + pair.CAPTURE, settled)
        self.assertIn('XCTAssertEqual(result, .completed,', settled)
        self.assertIn('guard result == .completed else {', settled)

    def test_only_two_exact_permission_prefixes_admit_failure_owner(self):
        call = pair.OWNER_CALL
        self.assertEqual(call.count('prefix =='), 2)
        self.assertIn('prefix == "Continue and retry the real local-network operation."', call)
        self.assertIn('prefix == "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية."', call)
        self.assertTrue(call.endswith('? app : nil)'))
        self.assertIn('pin06FailureOwner: XCUIApplication? = nil', pair.SIGNATURE)
        self.assertNotIn('hasPrefix', call)

    def test_touches_stay_setup_scoped_and_scene_observation_has_no_private_payload(self):
        plan = pair.render(actual(), pair.INPUTS, arm='native')
        recorder = plan[pin01_copy.ADDITION]
        self.assertIn('armed = value == 0 || value == 6', recorder)
        self.assertIn('!(kind == 5 && stage in 0..4)', recorder)
        for forbidden in ('consume(', 'delay(', 'Thread(', 'println(', 'displayName', 'GameId',
                          'UserDefaults', 'Timer(', 'DispatchQueue', 'swizzle'):
            self.assertNotIn(forbidden, recorder)
        self.assertEqual(set(plan) - set(pair.MODIFIED), {pin01_copy.ADDITION})
        self.assertIs(runner.renderer_for_profile('public-setup-pair', pair.SOURCE, pair.RUNTIME, pair.DEVICE),
                      pair.render)

    def test_changed_source_digest_fixture_template_and_profile_reject(self):
        sources = actual()
        for path in pair.MODIFIED:
            changed = sources | {path: sources[path] + '\n'}
            digests = pair.INPUTS | {path: hashlib.sha256(changed[path].encode()).hexdigest()}
            with self.subTest(path=path), self.assertRaises(ValueError):
                pair.render(changed, digests, arm='native')
        for arm in ('all', '', None, False):
            with self.assertRaises(ValueError): pair.render(sources, pair.INPUTS, arm=arm)
        for field in range(3):
            args = [pair.SOURCE, pair.RUNTIME, pair.DEVICE]; args[field] += '-changed'
            with self.assertRaises(ValueError): runner.renderer_for_profile('public-setup-pair', *args)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'manifest.json').write_text('{}')
            with self.assertRaises(ValueError): pair.fixture(pair.MODIFIED[0], directory=root)
        from unittest.mock import patch
        with patch.object(pair.frozen_sources, '_read', return_value=b'changed'), self.assertRaises(ValueError):
            pair.render(sources, pair.INPUTS, arm='compose')

    def test_uniform_build_profile_preserves_tracking_strict_verification_and_test_command(self):
        inherited = {'RUNNER_TRACKING_ID': 'owned-runner', 'GRADLE_OPTS': '-Dunsafe=true'}
        env = runner.diagnostic_environment(inherited, Path('/owned/path space/registry'), Path('/owned/tmp'))
        self.assertEqual(env['RUNNER_TRACKING_ID'], inherited['RUNNER_TRACKING_ID'])
        options = shlex.split(env['GRADLE_OPTS'])
        self.assertIn('-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC', options)
        self.assertIn('-Dorg.gradle.workers.max=1', options)
        self.assertIn('-Dorg.gradle.parallel=false', options)
        self.assertIn('-Dorg.gradle.daemon.registry.base=/owned/path space/registry', options)
        self.assertNotIn('-Dunsafe=true', options)
        base = ['/usr/bin/xcodebuild', '-quiet', '-configuration', 'Debug', '-sdk', 'iphonesimulator']
        self.assertEqual(runner.build_command(base), [base[0], *base[2:], '-jobs', '1',
                                                     '-showBuildTimingSummary', 'build-for-testing'])
        test = runner.test_command(base, Path('/owned/tests.xcresult'))
        self.assertEqual(test.count('test-without-building'), 1)
        self.assertEqual(sum(p.startswith('-only-testing:') for p in test), 2)
        self.assertNotIn('dependency-verification=off', str(env))

    @unittest.skipUnless(sys.platform == 'darwin', 'Copied setup driver requires actual Apple SDK')
    def test_actual_generated_setup_driver_typechecks_with_simulator_sdk(self):
        with tempfile.TemporaryDirectory(prefix='parlor-pin06-swift-') as temporary:
            root = Path(temporary).resolve()
            environment = dict(os.environ, TMPDIR=str(root))
            driver = root / 'Driver.swift'
            driver.write_text(pair.render(actual(), pair.INPUTS, arm='native')[pin02_capture.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, timeout=45, env=environment).strip()
            platform_path = subprocess.check_output([
                '/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-platform-path'],
                text=True, timeout=45, env=environment).strip()
            architecture = {'arm64': 'arm64', 'x86_64': 'x86_64'}[platform.machine()]
            result = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', architecture + '-apple-ios16.0-simulator',
                '-F', platform_path + '/Developer/Library/Frameworks', '-I', platform_path + '/Developer/usr/lib',
                '-module-cache-path', str(root / 'cache'), str(driver)],
                capture_output=True, text=True, timeout=180, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
