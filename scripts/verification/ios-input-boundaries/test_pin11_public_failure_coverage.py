"""Prepared coverage controls; source checks are not native failure evidence."""
import os
from pathlib import Path
import plistlib
import platform
import re
import subprocess
import tempfile
import unittest

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin11_root_observations as observed
import pin11_public_failure_coverage as coverage


def original_plan():
    return observed.renderer_for_profile(observed.PROFILE, observed.SOURCE, pair.RUNTIME, pair.DEVICE)(
        {p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm='compose')


def failure_count_build_command(sdk_platform, root, file, executable):
    frameworks = sdk_platform + '/Developer/Library/Frameworks'
    libraries = sdk_platform + '/Developer/usr/lib'
    # XCTest's overlay needs both Swift import (-I) AND linker (-L) search.
    # It is outside MacOSX.sdk; a framework path alone does not resolve it.
    return ['/usr/bin/xcrun', '--sdk', 'macosx', 'swiftc', '-Onone',
        '-emit-library', '-module-name', 'PIN11PublicCoverage',
        '-F', frameworks, '-I', libraries, '-L', libraries,
        '-Xlinker', '-rpath', '-Xlinker', frameworks,
        '-Xlinker', '-rpath', '-Xlinker', libraries,
        '-module-cache-path', str(root / 'cache'), str(file), '-o', str(executable)]


class PublicFailureCoverageControls(unittest.TestCase):
    def test_actual_xctest_overlay_has_import_link_and_runtime_search_paths(self):
        command = failure_count_build_command('/Xcode/MacOSX.platform', Path('/owned'),
                                             Path('/owned/Count.swift'), Path('/owned/count'))
        libraries = '/Xcode/MacOSX.platform/Developer/usr/lib'
        self.assertEqual(command[command.index('-I') + 1], libraries)
        self.assertEqual(command[command.index('-L') + 1], libraries)
        self.assertIn(['-Xlinker', '-rpath', '-Xlinker', libraries],
                      [command[i:i+4] for i in range(len(command) - 3)])
        self.assertEqual(command[:5], ['/usr/bin/xcrun', '--sdk', 'macosx', 'swiftc', '-Onone'])
        self.assertIn('-emit-library', command)
        self.assertIn('PIN11PublicCoverage', command)
        self.assertIn('/owned/cache', command)

    def test_exact_inverse_retains_all_original_actions_assertions_waiters_and_templates(self):
        original = original_plan()
        expanded = coverage.extend(original)
        self.assertEqual(coverage.restore(expanded), original)
        self.assertEqual({p for p in original if original[p] != expanded[p]},
            {pin01_copy.ADDITION, pin01_copy.MAIN, pin02_capture.CONTAINER, pin02_capture.DRIVER})
        before, after = original[pin02_capture.DRIVER], expanded[pin02_capture.DRIVER]
        for token in ('XCTAssert', 'XCTFail(', '.tap()', '.typeText(', '.swipeUp(',
                      '.swipeDown(', 'timeout: ', 'ProcessInfo.processInfo.systemUptime'):
            self.assertEqual(before.count(token), after.count(token), token)
        for template in ('PIN08AccessibilityObserver.swift.in', 'PIN10RootObserver.swift.in'):
            self.assertIn((pair.HERE / template).read_text(), expanded[pin02_capture.CONTAINER])

    def test_ax_whitelist_does_not_arm_permission_keyboard_or_private_pointer_recording(self):
        original = original_plan()
        expanded = coverage.extend(original)
        recorder = expanded[pin01_copy.ADDITION]
        self.assertIn('val publicAXArmed: Boolean get() = stage in 0..4 || stage == 6', recorder)
        self.assertIn('armed = value == 0 || value == 6', recorder)
        self.assertEqual(recorder.replace(coverage.PROPERTY, ''), original[pin01_copy.ADDITION])
        self.assertEqual(expanded[pin02_capture.CONTAINER].count(coverage.AX_ARMED), 2)
        self.assertNotIn('PIN01TouchObserver', expanded[pin02_capture.CONTAINER])

    def test_failed_method_exit_observes_only_failure_count_before_original_termination(self):
        driver = coverage.extend(original_plan())[pin02_capture.DRIVER]
        self.assertIn(coverage.NEW_DEFER, driver)
        self.assertEqual(driver.count(coverage.NEW_DEFER), 1)
        scope = driver[driver.index(coverage.METHOD):driver.index(coverage.NEXT_METHOD)]
        self.assertLess(scope.index('run.failureCount > 0'), scope.index('app.terminate()'))
        self.assertEqual(scope.count('self.testRun'), 1)
        self.assertNotIn('hasSucceeded', scope)
        # Source preservation is not proof that XCTest's count is timely on Apple.
        # That remains a required native control before this preparation is wired.

    def test_all_original_failed_requests_share_one_bounded_attempt_before_delivery(self):
        driver = coverage.extend(original_plan())[pin02_capture.DRIVER]
        self.assertEqual(driver.count('PIN02FailureRequest.captureAfterFailedTransition('), 1)
        # Two existing app-owned requests plus the new public failure exit.
        # The helper definition has an underscored parameter, not another call.
        self.assertEqual(driver.count('pin11CapturePublicFailureOnce(app)'), 3)
        self.assertEqual(driver.count('pin11CapturePublicFailureOnce(pin06FailureOwner)'), 1)
        helper = coverage.HELPER
        self.assertIn('UUID(uuidString: token)?.uuidString == token', helper)
        self.assertIn('!pin11RequestedTokens.contains(token)', helper)
        self.assertIn('pin11RequestedTokens.count < 8', helper)
        self.assertLess(helper.index('pin11RequestedTokens.insert(token)'),
                        helper.index('PIN02FailureRequest.captureAfterFailedTransition(app)'))

    def test_repeated_or_structurally_changed_plans_reject_without_mutating_the_input(self):
        original = original_plan()
        for path, old in ((pin01_copy.ADDITION, '    var armed = false\n'),
                          (pin02_capture.CONTAINER, coverage.ARMED),
                          (pin02_capture.DRIVER, coverage.OLD_DEFER)):
            altered = original | {path: original[path].replace(old, old + '\nALTERED', 1)}
            # Removal/duplication must fail closed; no best-effort reanchoring.
            altered[path] = altered[path].replace(old, '', 1)
            frozen = dict(altered)
            with self.assertRaises((ValueError, RuntimeError)):
                coverage.extend(altered)
            self.assertEqual(altered, frozen)
        with self.assertRaises((ValueError, RuntimeError)):
            coverage.extend(coverage.extend(original))

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple XCTest SDK unavailable on Linux')
    def test_actual_simulator_sdk_typechecks_extended_original_xctest_driver(self):
        # Typechecking proves API/actor compatibility, not runtime failure-count
        # timing or capture neutrality. Full native failure control remains needed.
        with tempfile.TemporaryDirectory(prefix='parlor-public-failure-sdk-') as temporary:
            root = Path(temporary).resolve()
            environment = os.environ | {'TMPDIR': str(root)}
            driver = root / 'Driver.swift'
            driver.write_text(coverage.extend(original_plan())[pin02_capture.DRIVER])
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                text=True, timeout=45, env=environment).strip()
            sdk_platform = subprocess.check_output(
                ['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-platform-path'],
                text=True, timeout=45, env=environment).strip()
            result = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', 'arm64-apple-ios18.5-simulator', '-F', sdk_platform + '/Developer/Library/Frameworks',
                '-I', sdk_platform + '/Developer/usr/lib', '-module-cache-path', str(root / 'cache'), str(driver)],
                capture_output=True, text=True, timeout=180, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple XCTest runtime unavailable on Linux')
    def test_actual_xctest_failure_count_is_available_in_swift_defer_before_termination(self):
        # Real XCTest semantics, no stand-in counter or UIKit/application claim.
        # If XCTest aborts without running Swift defer, this control MUST fail.
        source = '''import Foundation
import XCTest
final class PIN11ExitCountCase: XCTestCase {
    static var exits: [String: Int] = [:]
    @objc func testPassingExit() {
        precondition(Thread.isMainThread)
        defer { Self.exits["pass"] = testRun?.failureCount }
        XCTAssertTrue(true)
    }
    @objc func testFailedExit() {
        precondition(Thread.isMainThread)
        continueAfterFailure = false
        defer { Self.exits["fail"] = testRun?.failureCount }
        XCTFail("Intentional native failure-count capability only")
    }
    @objc func testZVerifyExitCounts() {
        XCTAssertEqual(Self.exits, ["pass": 0, "fail": 1])
        if Self.exits == ["pass": 0, "fail": 1] {
            print("PIN11_ACTUAL_XCTEST_FAILURE_EXIT_COUNT_PASS")
        }
    }
}
'''
        with tempfile.TemporaryDirectory(prefix='parlor-public-failure-count-') as temporary:
            root = Path(temporary).resolve()
            environment = os.environ | {'TMPDIR': str(root)}
            file = root / 'Count.swift'
            bundle = root / 'Coverage.xctest'
            executable = bundle / 'Contents/MacOS/Coverage'
            executable.parent.mkdir(parents=True)
            (bundle / 'Contents/Info.plist').write_bytes(plistlib.dumps({
                'CFBundleExecutable': 'Coverage', 'CFBundlePackageType': 'BNDL',
                'CFBundleIdentifier': 'com.parlor.diagnostics.public-failure-coverage',
            }))
            file.write_text(source)
            sdk_platform = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'macosx', '--show-sdk-platform-path'],
                text=True, timeout=45, env=environment).strip()
            build = subprocess.run(failure_count_build_command(sdk_platform, root, file, executable),
                capture_output=True, text=True, timeout=180, env=environment)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            # Use Xcode's actual test host, which resolves XCTestCore/private
            # dependencies. A standalone executable is not the XCTest runtime.
            actual = subprocess.run(['/usr/bin/xcrun', '--sdk', 'macosx', 'xctest', str(bundle)],
                capture_output=True, text=True, timeout=30, env=environment)
            output = actual.stdout + actual.stderr
            print(output) # Public synthetic fixture only; retain original outcomes.
            self.assertEqual(actual.returncode, 1, output) # One intentional XCTest failure, never waived.
            self.assertRegex(output, r'Executed 3 tests, with 1 failure', output)
            for name, result in (('testFailedExit', 'failed'), ('testPassingExit', 'passed'),
                                 ('testZVerifyExitCounts', 'passed')):
                self.assertEqual(len(re.findall(r"Test Case [^\n]*" + name + r"[^\n]* " + result + r"\b", output)),
                                 1, output)
            self.assertIn('Intentional native failure-count capability only', output)
            self.assertIn('PIN11_ACTUAL_XCTEST_FAILURE_EXIT_COUNT_PASS', output)


if __name__ == '__main__':
    unittest.main()
