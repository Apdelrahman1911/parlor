"""Pure control/receipt checks; never compile Swift, run Gradle, or launch an app."""
import json
from pathlib import Path
import unittest

import run_dsc01_apphost_cycle as runner


HERE = Path(__file__).resolve().parent


def synthetic_receipt():
    lines = ['DSC01_CATALOG_GEOMETRY_CONTRACT ' + json.dumps(dict(schemaVersion=1, cases=14, passed=True))]
    for game in ('whodunit', 'mafia'):
        for index, kind in enumerate(['search'] * 6 + ['before-activation', 'after-activation', 'successor'], 1):
            fields = dict(window=1, index=index) if kind == 'search' else {}
            if kind == 'successor':
                fields.update(targetCountCapped16=0, tabCountsCapped16=[0, 0], successorCountCapped16=1,
                              foreground=True, alertPresent=False)
            lines.append('DSC01_CATALOG_METADATA ' + json.dumps(dict(schemaVersion=1, game=game, ordinal=index,
                kind=kind, elapsedMilliseconds=index * 250, fields=fields)))
    return '\n'.join(lines)


class CatalogControlTest(unittest.TestCase):
    def test_synthetic_receipt_is_only_a_parser_check(self):
        result = runner.verify_catalog_receipts(synthetic_receipt())
        self.assertEqual(2, result['actual_catalog_entries'])
        self.assertEqual(14, result['swift_geometry_contract_cases'])

    def test_missing_swift_contract_or_game_cannot_pass(self):
        lines = synthetic_receipt().splitlines()
        for changed in (lines[1:], lines[:10], [lines[0]] + lines):
            with self.assertRaises(RuntimeError): runner.verify_catalog_receipts('\n'.join(changed))

    def test_repeated_activation_or_missing_successor_cannot_pass(self):
        good = synthetic_receipt()
        for changed in (good.replace('"kind": "search"', '"kind": "before-activation"', 1),
                        good.replace('"successorCountCapped16": 1', '"successorCountCapped16": 0'),
                        good.replace('"tabCountsCapped16": [0, 0]', '"tabCountsCapped16": [1, 1]'),
                        good.replace('"kind": "successor"', '"kind": "failed"')):
            with self.assertRaises(RuntimeError): runner.verify_catalog_receipts(changed)

    def test_incomplete_train_unknown_game_and_oversized_row_fail(self):
        good = synthetic_receipt()
        lines = good.splitlines(); del lines[2]
        for changed in ('\n'.join(lines), good.replace('"game": "mafia"', '"game": "unknown"'),
                        good + '\nDSC01_CATALOG_METADATA ' + ' ' * 4097):
            with self.assertRaises(RuntimeError): runner.verify_catalog_receipts(changed)

    def test_only_catalog_entry_call_changes_original_matrix_and_geometry_checks_execute(self):
        source = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        self.assertEqual(1, source.count('try verifyCatalogGeometryContract()'))
        self.assertLess(source.index('try verifyCatalogGeometryContract()'), source.index('try launch(app, fixture: "fresh")'))
        self.assertEqual(1, source.count('try openActualCatalogGame(app, game: game)'))
        self.assertIn('try tapPublicSetupPrefix(app, prefix: "Start a pass-and-play session on this device.")', source)
        self.assertEqual(1, source.count('func testDSC01ActualSettingsLocalSessionsAndOSInvestigation()'))
        self.assertEqual(5, len(runner.EXPECTED_TESTS))

    def test_physical_activation_is_outside_all_readiness_retries(self):
        source = (HERE / 'DSC01CatalogSelection.swift.in').read_text()
        ready = source[source.index('private func catalogReadySample('):source.index('private func openActualCatalogGame(')]
        self.assertNotIn('.tap()', ready)
        self.assertIn('for window in 1...14', ready)
        self.assertIn('for index in 1...6', ready)
        self.assertEqual(1, source.count('coordinate.tap()'))
        self.assertNotIn('target.tap()', source)
        self.assertIn('dsc01CatalogNear(activationFrame, sampledFrame)', source)
        self.assertIn('abs(point.x - plan.point.x) <= 0.5', source)
        self.assertIn('final.tabCounts == [0, 0], final.successorCount == 1', source)

    def test_public_metadata_bounds_and_measured_exclusions_are_explicit(self):
        source = (HERE / 'DSC01CatalogSelection.swift.in').read_text()
        for needed in ('ordinal < 96', 'encoded.count <= 4096', 'totalBytes <= 196608',
                       'overlay.maxY', 'tabs.map(\\.minY)', 'target.intersection(usable)',
                       'rect.size.width > 0', 'rect.size.height > 0', 'frames.count == 6'):
            self.assertIn(needed, source)
        for forbidden in ('Thread.sleep(', 'usleep(', '.debugDescription', '.screenshot()', 'MainViewControllerKt',
                          'UserDefaults', 'openGame(', 'sendAction(', 'performSelector(', '.label', '.value'):
            self.assertNotIn(forbidden, source)
        overlay = (HERE / 'DSC01Probe.swift.in').read_text()
        self.assertIn('.accessibilityIdentifier("parlor-dsc01-overlay")', overlay)

    def test_old_native_matrix_and_cleanup_controls_remain_identical(self):
        v6 = HERE.parent / 'dsc01_apphost_v6'
        for name in ('secondary_fifo.py', 'test_secondary_fifo.py', 'copied_sources.py', 'bind_source.py',
                     'DSC01ComposeObservation.kt.in', 'DSC01InvocationBridge.kt.in', 'DSC01UIKitObservation.kt.in',
                     'DSC01NativeObservation.swift.in', 'probe_validation.py', 'copied-kotlin-phase.sh.in'):
            self.assertEqual((v6 / name).read_bytes(), (HERE / name).read_bytes(), name)
        old = (v6 / 'run_dsc01_apphost_cycle.py').read_text()
        new = (HERE / 'run_dsc01_apphost_cycle.py').read_text()
        marker = '        finally:\n            deferred = []'
        self.assertEqual(old[old.index(marker):], new[new.index(marker):])

    def test_os_prefix_queries_are_diagnostics_only_not_action_fallbacks(self):
        source = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        begin = source.index('private func recordOSSelectorDiagnostics(')
        end = source.index('private func optionalDictionary(', begin)
        diagnostic = source[begin:end]
        self.assertIn('diagnosticOnlyKnownPrefixes', diagnostic)
        self.assertIn('prefix(4)', diagnostic)
        self.assertNotIn('.tap()', diagnostic)
        self.assertNotIn('.label', diagnostic)
        self.assertNotIn('.value', diagnostic)
        v6 = (HERE.parent / 'dsc01_apphost_v6/IOSAppLaunchUITests.swift.in').read_text()
        start_marker = '    @MainActor private func investigateActualOSPerAppLanguage('
        stop_marker = '    @MainActor private func recordOSSelectorDiagnostics('
        self.assertEqual(v6[v6.index(start_marker):v6.index(stop_marker)],
                         source[source.index(start_marker):source.index(stop_marker)])

    def test_added_xctest_helper_and_contract_are_hashed_build_copy_inputs(self):
        controls = {path.name for path in runner.control_files()}
        self.assertIn('DSC01CatalogSelection.swift.in', controls)
        self.assertIn('test_catalog_contract.py', controls)
        source = Path(runner.__file__).read_text()
        self.assertIn("(FIXTURE / 'DSC01CatalogSelection.swift.in').read_text()", source)
        self.assertIn("receipt['catalog_entry_proof'] = verify_catalog_receipts", source)


if __name__ == '__main__':
    unittest.main()
