"""Copy-contract tests, not Kotlin/Swift compilation or native execution.

Root owns execution. Tests write exclusively to fresh TemporaryDirectory copies;
repository anchors/templates and retained prior audit material are read-only.
"""
import ast
import hashlib
from pathlib import Path
import tempfile
import unittest

import copied_sources as original
import l08_copy as extension

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2] if (HERE / 'copied_sources.py').is_file() else HERE.parents[3]
CONTROLS = ROOT / 'scripts/verification/ios-readiness'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class L08CopyContractTests(unittest.TestCase):
    def test_registry_is_closed_and_verbatim_fixtures_match_current_source(self):
        self.assertEqual(len(extension.L08_ADDITIONS), 9)
        self.assertEqual(len(extension.L08_MODIFIED), 7)
        self.assertEqual(set(extension.L08_ADDITIONAL_MODIFIED),
                         {extension.APP, extension.HOME, extension.RECOVERY, extension.MAFIA_SETUP})
        self.assertFalse(set(extension.L08_MODIFIED) & set(extension.L08_ADDITIONS))
        self.assertEqual(len(set(extension.L08_ADDITIONS.values())), 9)
        for destination, template in extension.L08_ADDITIONS.items():
            with self.subTest(template=template):
                self.assertFalse((ROOT / destination).exists(), 'Fixture must not enter the actual shipping source tree')
                self.assertTrue((HERE / template).is_file())
                self.assertFalse((HERE / template).is_symlink())
        for template, actual in extension.VERBATIM_FIXTURES.items():
            with self.subTest(template=template):
                self.assertEqual((HERE / template).read_bytes(), (ROOT / actual).read_bytes())

    def test_every_actual_source_anchor_transforms_without_changing_repository_bytes(self):
        before = {path: sha(ROOT / path) for path in extension.L08_MODIFIED}
        for path in extension.L08_MODIFIED:
            with self.subTest(path=path):
                source = (ROOT / path).read_text()
                changed = extension.transform(path, source)
                self.assertNotEqual(changed, source)
                self.assertIn('.readiness.L08', changed)
                with self.assertRaises(RuntimeError): extension.transform(path, changed)
        self.assertEqual(before, {path: sha(ROOT / path) for path in extension.L08_MODIFIED})

    def test_missing_ambiguous_unknown_and_repeated_anchors_fail_closed(self):
        with self.assertRaises(RuntimeError): extension.once('abc', 'absent', 'new')
        with self.assertRaises(RuntimeError): extension.once('aa', 'a', 'new')
        self.assertEqual(extension.once('before-MARK-after', 'MARK', 'OK'), 'before-OK-after')
        with self.assertRaises(RuntimeError): extension.transform('unregistered.kt', '')
        for path in extension.L08_MODIFIED:
            with self.subTest(path=path):
                source = (ROOT / path).read_text()
                with self.assertRaises(RuntimeError): extension.transform(path, '')
                with self.assertRaises(RuntimeError): extension.transform(path, source + source)

    def test_actual_resume_navigation_and_original_lifecycle_calls_remain(self):
        home = extension.transform(extension.HOME, (ROOT / extension.HOME).read_text())
        self.assertEqual(home.count('            onResume(entry.sessionId)'), 1)
        self.assertIn('L08UiSeam.homeTapped(entry.sessionId)', home)
        app = extension.transform(extension.APP, (ROOT / extension.APP).read_text())
        anchor = 'navigator.showLocalResumeFailure(destination.sessionId)'
        self.assertEqual(app.count(anchor), 1)
        self.assertIn(anchor + '\n                                    com.parlor.app.readiness.L08UiSeam.recoveryShown(destination.sessionId)', app)
        recovery = extension.transform(extension.RECOVERY, (ROOT / extension.RECOVERY).read_text())
        self.assertIn('L08UiSeam.retryTapped(); onRetry()', recovery)
        self.assertIn('L08UiSeam.discardTapped(); onDiscard()', recovery)
        main = extension.transform(extension.MAIN, (ROOT / extension.MAIN).read_text())
        for call in ('notifyActive', 'notifyInactive', 'notifyBackgrounded'):
            self.assertEqual(main.count('lifecycleCoordinator().' + call + '()'), 1)
        self.assertEqual(main.count('startKoin { modules(allModules + com.parlor.app.readiness.L08HostProbe.extraModules()) }'), 1)

    def test_mafia_settings_observation_preserves_original_updates_and_start(self):
        source = (ROOT / extension.MAFIA_SETUP).read_text()
        changed = extension.transform(extension.MAFIA_SETUP, source)
        self.assertEqual(changed.count('draft = draft.copy(doctorCanProtectSamePlayerConsecutively = it)'), 1)
        self.assertEqual(changed.count('                    onStart(settings)'), 1)
        self.assertIn('L08MafiaSetupObservation.repeatChanged(it)\n                    draft =', changed)
        self.assertIn('L08MafiaSetupObservation.start(settings)\n                    onStart(settings)', changed)
        self.assertIn('enabled = canStart,', changed)
        self.assertEqual(changed.count('.testTag("l08-mafia-repeat")'), 1)
        self.assertEqual(changed.count('.testTag("l08-mafia-start")'), 1)

    def test_old_and_l08_transforms_compose_in_only_an_owned_temporary_copy(self):
        paths = sorted(set(original.MODIFIED_KOTLIN) | set(extension.L08_MODIFIED))
        before = {path: sha(ROOT / path) for path in paths}
        with tempfile.TemporaryDirectory(prefix='parlor-l08-copy-contract-') as raw:
            copy_root = Path(raw).resolve()
            for path in paths:
                target = copy_root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / path).read_bytes())
            original.instrument_kotlin(copy_root)
            # Draft tests use unchanged original controls. After reviewed
            # promotion, original.instrument_kotlin calls L08 itself once.
            if '.readiness.L08' not in (copy_root / extension.WD).read_text():
                extension.instrument_l08_kotlin(copy_root)
            for path in extension.L08_MODIFIED:
                self.assertIn('.readiness.L08', (copy_root / path).read_text())
            for path in (extension.WD, extension.MF):
                text = (copy_root / path).read_text()
                self.assertEqual(text.count('DSC01ObserveActualLocalSession('), 1)
                self.assertEqual(text.count('Resume(restoredSessionId, session)'), 1)
            with self.assertRaises(RuntimeError): extension.instrument_l08_kotlin(copy_root)
        self.assertFalse(copy_root.exists())
        self.assertEqual(before, {path: sha(ROOT / path) for path in paths})

    def test_copy_transforms_refuse_symlinked_roots_and_inputs(self):
        with tempfile.TemporaryDirectory(prefix='parlor-l08-copy-link-') as raw:
            parent = Path(raw).resolve()
            actual = parent / 'actual'; actual.mkdir()
            link = parent / 'link'; link.symlink_to(actual, target_is_directory=True)
            with self.assertRaises(RuntimeError): extension.instrument_l08_kotlin(link)
            target = actual / extension.WD
            target.parent.mkdir(parents=True)
            target.symlink_to(ROOT / extension.WD)
            with self.assertRaises(RuntimeError): extension.instrument_l08_kotlin(actual)

    def test_swift_observers_are_additive_and_old_discovery_is_unchanged(self):
        source = (CONTROLS / 'DSC01Probe.swift.in').read_text()
        changed = extension.instrument_probe_swift(source)
        for name in ('l08Display', 'l08UiDisplay', 'l08HostDisplay'):
            self.assertEqual(changed.count('@Published private(set) var ' + name), 1)
        self.assertIn('"readiness", "l08-storage", "l08-host"].contains(requestedScenario)', changed)
        self.assertIn('if probe.isReadinessScenario {', changed)
        with self.assertRaises(RuntimeError): extension.instrument_probe_swift(changed)
        source = (CONTROLS / 'IOSAppLaunchUITests.swift.in').read_text()
        changed = extension.instrument_ui_test(source)
        for invocation in ('verifyActualNativeReadiness', 'verifyL08RealStoreResume', 'verifyL08StartedHosts'):
            self.assertEqual(changed.count('try ' + invocation + '(app)'), 1)
        def discovery(text):
            return [line.strip() for line in text.splitlines() if 'func test' in line]
        self.assertEqual(discovery(source), discovery(changed))
        self.assertLess(changed.index('try verifyActualNativeReadiness(app)'), changed.index('try verifyL08RealStoreResume(app)'))
        self.assertLess(changed.index('try verifyL08RealStoreResume(app)'), changed.index('try verifyL08StartedHosts(app)'))
        with self.assertRaises(RuntimeError): extension.instrument_ui_test(changed)

    def test_new_parser_sources_are_python_syntax_not_runtime_evidence(self):
        for path in ('l08_copy.py', 'l08_receipts.py', 'test_l08_copy.py',
                     'test_l08_receipts.py', 'test_l08_host_receipts.py'):
            ast.parse((HERE / path).read_text(), filename=path)


if __name__ == '__main__': unittest.main()
