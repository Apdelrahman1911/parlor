"""Pure source/transform controls; these are not Swift or application-runtime tests."""
import ast
import hashlib
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('tested_os_recovery_copy', HERE / 'os_recovery_copy.py')
copy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(copy)


class RecoveryCopyTests(unittest.TestCase):
    def setUp(self):
        self.prerequisite = (copy.FUNCTIONAL / 'DSC01OSPrerequisite.swift.in').read_text()
        self.probe = (copy.FUNCTIONAL / 'DSC01Probe.swift.in').read_text()
        self.ui = (HERE / 'OSRecoveryUITests.swift.in').read_text()
        self.images = (HERE / 'OSRecoveryProbe.swift.in').read_text()

    def test_original_per_app_swift_remains_input_not_a_reimplemented_oracle(self):
        source = (copy.FUNCTIONAL / 'IOSAppLaunchUITests.swift.in').read_text()
        body = source.split('    @MainActor private func investigateActualOSPerAppLanguage', 1)[1].split(
            '    @MainActor private func recordOSSelectorDiagnostics', 1)[0]
        self.assertEqual(hashlib.sha256(body.encode()).hexdigest(),
                         '2f91511d84aa505143a82c708465b0b01a16fe44e8516fe97c0da97046476a85')
        self.assertNotIn('func investigateActualOSPerAppLanguage', self.ui)
        self.assertIn('try investigateActualOSPerAppLanguage(app)', self.ui)

    def test_default_full_and_all_existing_posttap_guards_remain(self):
        rendered = copy.render_prerequisite(self.prerequisite)
        self.assertIn('mode: DSC01OSRecoveryMode = .full', rendered)
        tail = self.prerequisite.split('        try diagnostics.append(stage: .retainEnglishPrimary, status: "after-activation"', 1)[1]
        self.assertTrue(rendered.endswith(tail))
        self.assertEqual(rendered.count('keepEnglish.firstMatch.tap()'), 1)
        self.assertIn('keepEnglish.count == 1, (1...4).contains(modal.buttons.count)', rendered)
        self.assertEqual(rendered.count('["Use English", "Keep English", "Use English (US)"]'), 2)

    def test_proof_only_rejects_before_any_add_language(self):
        rendered = copy.render_prerequisite(self.prerequisite)
        guard = rendered.index('guard mode != .proofOnly || disposition == .alreadySatisfied else {')
        existing = rendered.index('        if disposition == .alreadySatisfied {')
        addition = rendered.index('try osPrerequisiteTapRow(settings, control: .addLanguage')
        self.assertLess(guard, existing)
        self.assertLess(existing, addition)
        self.assertIn('try osPrerequisiteBlock(.initialEnglishPrimary, diagnostics: diagnostics, fields: initial)',
                      rendered[guard:existing])
        expected = self.prerequisite.split('        if disposition == .alreadySatisfied {', 1)[1].split(
            '        try osPrerequisiteTapRow(settings, control: .addLanguage', 1)[0]
        self.assertEqual(rendered[existing:addition].split('        if disposition == .alreadySatisfied {', 1)[1].rstrip(),
                         expected.rstrip())

    def test_bootstrap_has_no_explicit_or_deferred_posttap_automation(self):
        rendered = copy.render_prerequisite(self.prerequisite)
        tap = rendered.index('        keepEnglish.firstMatch.tap()')
        self.assertEqual(rendered[tap:].splitlines()[1],
                         '        if mode == .bootstrap { return } // No post-tap query, marker, wait or cleanup call.')
        self.assertEqual(rendered.count('defer {'), 1)
        self.assertIn('defer { if mode != .bootstrap { settings.terminate() } }', rendered[:tap])
        bootstrap = self.ui.split('    @MainActor func testDSC01OSPublicBootstrap() throws {', 1)[1].split(
            '    @MainActor func testDSC01OSPerAppRecovery()', 1)[0]
        self.assertNotIn('defer {', bootstrap)
        self.assertEqual(bootstrap.split('mode: .bootstrap)', 1)[1].strip(), '}')

    def test_both_stage_methods_are_distinct_and_compiled_together(self):
        for method in (copy.BOOTSTRAP_METHOD, copy.PROOF_METHOD):
            self.assertEqual(self.ui.count('func ' + method.split('/')[1]), 1)
        self.assertEqual(self.ui.count('PARLOR_OS_RECOVERY_STAGE '), 1)
        self.assertEqual(self.ui.count('mode: .proofOnly'), 1)
        self.assertEqual(self.ui.count('mode: .bootstrap'), 1)
        self.assertIn('app.launchArguments = []', self.ui)
        self.assertNotIn('launchArguments = ["-Apple', self.ui)
        self.assertNotIn('UserDefaults', self.ui + self.images)

    def test_image_hook_follows_durable_before_main_report_without_kotlin(self):
        rendered = copy.render_probe(self.probe)
        hook = rendered.index('try recordOSRecoveryImages(report: value)')
        self.assertLess(rendered.index('try encoded.write(to: resultURL, options: .atomic)'), hook)
        self.assertLess(rendered.index('        report = value'), hook)
        self.assertIn('if scenario == "os" && phase == "before_main"', rendered[:hook])
        self.assertLess(hook, rendered.index('        if publish { try publishObservation(observation) }'))
        self.assertNotIn('MainViewControllerKt.', self.images)
        self.assertIn('try nativeReadinessLoadedImages()', self.images)
        self.assertIn('try nativeReadinessLoaderEnvironment()', self.images)
        self.assertIn('try encoded.write(to: url, options: [.withoutOverwriting])', self.images)
        self.assertNotIn('catch', self.images)

    def test_image_source_binds_every_start_and_exact_owned_context(self):
        for needle in ('(1...8).contains(starts.count)', 'starts.last?.boot == boot',
                       'report.observations.last?.phase == "before_main"',
                       'report.runToken == environment["PARLOR_DSC01_RUN_TOKEN"]',
                       'simulator == environment["PARLOR_DSC01_EXPECTED_UDID"]',
                       'encoded.count <= 49152', '"boot_ordinal": starts.count',
                       '"process_boot": boot', '"run_token": report.runToken',
                       '"simulator_uuid": simulator', '"kind": "os-recovery-process-images"',
                       '"parlor-os-recovery-images-\\(starts.count).json"'):
            self.assertIn(needle, self.images)

    def test_transforms_reject_missing_duplicate_or_pretransformed_anchors(self):
        for original, render in ((self.prerequisite, copy.render_prerequisite), (self.probe, copy.render_probe)):
            for invalid in ('', original + original, render(original)):
                with self.assertRaises(RuntimeError):
                    render(invalid)

    def test_wrong_same_name_module_origin_is_rejected(self):
        wrong = copy.ROOT / 'scripts/verification/ios-readiness/copied_sources.py'
        with self.assertRaises(RuntimeError):
            copy.require_module(SimpleNamespace(__file__=str(wrong)), 'copied_sources')
        copy.require_module(SimpleNamespace(__file__=str(copy.FUNCTIONAL / 'copied_sources.py')), 'copied_sources')

    def test_module_imports_are_stdlib_only(self):
        parsed = ast.parse((HERE / 'os_recovery_copy.py').read_text())
        imports = [name for node in ast.walk(parsed) for name in (
            [alias.name for alias in node.names] if isinstance(node, ast.Import) else
            [node.module] if isinstance(node, ast.ImportFrom) else [])]
        self.assertEqual(set(imports), {'difflib', 'json', 'pathlib', 're'})


if __name__ == '__main__':
    unittest.main()
