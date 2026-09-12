"""Synthetic receipt/source controls. Root alone executes; not native proof."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

import probe_validation
import run_dsc01_apphost_cycle as runner
import v10_receipts
import v11_receipts
from test_v10_contract import V9_REFERENCES, encoded, fixture, pick, reordinal

HERE = Path(__file__).resolve().parent
V10 = HERE.parent / 'dsc01_apphost_v10'
V10_REFERENCES = V9_REFERENCES + ('v10_sources.py', 'DSC01OSAppSettings.swift.in', 'v10_receipts.py', 'test_v10_contract.py')


def enriched(start='settings', blocked=None, property_name='title'):
    rows, report = fixture(start, blocked)
    for row in rows:
        fields = row['fields']
        selected = [fields] if 'pane' in fields else fields.get('knownPanes', fields.get('visibleMarkerRows', []))
        for pane in selected:
            audit = pane['pane'] == 'audit'
            pane.update(paneSelectorMethod='unchanged-audit-control' if audit else 'known-public-identifying-properties-union',
                        paneKnownPropertyMatches=[] if audit else [dict(property=property_name, knownLiteral=v11_receipts.TITLES[pane['pane']][0])],
                        paneIdentityMatchesKnown=True)
    return rows, report


def log_with_contract(rows, contract=None):
    return encoded(rows) + '\nDSC01_OS_PANE_IDENTITY_DECISION_CONTRACT ' + json.dumps(v11_receipts.CONTRACT if contract is None else contract)


class V11PaneIdentityContractTest(unittest.TestCase):
    def verify(self, rows, report):
        log = log_with_contract(rows)
        original = v10_receipts.verify_v10_receipts(log, report, probe_validation.verify_os(report))
        additional = v11_receipts.verify_v11_receipts(log)
        return original, additional

    def test_every_old_control_except_targeted_helper_and_wiring_is_byte_identical(self):
        for name in V10_REFERENCES:
            if name not in {'run_dsc01_apphost_cycle.py', 'DSC01OSAppSettings.swift.in'}:
                self.assertEqual((V10 / name).read_bytes(), (HERE / name).read_bytes(), name)
        old = (V10 / 'run_dsc01_apphost_cycle.py').read_text(); new = Path(runner.__file__).read_text()
        marker = '        finally:\n            deferred = []'
        self.assertEqual(old[old.index(marker):], new[new.index(marker):])
        old = (V10 / 'DSC01OSAppSettings.swift.in').read_text(); new = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
        start = '    @MainActor private func osRowObservation('
        end = '    @MainActor private func verifyOSInteractionDecisionContract('
        self.assertEqual(old[old.index(start):old.index(end)], new[new.index(start):new.index(end)])

    def test_exact_public_predicate_and_allowlisted_provenance_are_executed_in_original_matrix(self):
        source = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
        self.assertIn('private let dsc01OSIdentifyingProperties = ["identifier", "title", "label", "value", "placeholderValue"]', source)
        self.assertIn('NSPredicate(format: "%K IN %@", $0, titles)', source)
        self.assertIn('app.navigationBars.matching(dsc01OSPaneNamePredicate(pane.titles))', source)
        self.assertIn('guard let value = attributes[property] as? String, titles.contains(value)', source)
        self.assertIn('fields["paneIdentityMatchesKnown"] as? Bool == true', source)
        self.assertIn('try verifyOSPaneIdentityDecisionContract(good: good)', source)
        self.assertIn('XCTAssertEqual(cases.count, 12)', source)
        self.assertIn('XCTAssertFalse(NSPredicate(format: "label IN %@", ["Settings"]).evaluate(with: cases[8].0))', source)
        for forbidden in ('BEGINSWITH', 'CONTAINS', 'identifiers IN', 'debugDescription', '.coordinate(', 'typeText(', 'UserDefaults'):
            self.assertNotIn(forbidden, source)

    def test_each_documented_property_can_provide_bounded_provenance_not_os_authority(self):
        for property_name in v11_receipts.PROPERTIES:
            rows, report = enriched(property_name=property_name)
            original, additional = self.verify(rows, report)
            self.assertEqual('PASS', original['status'])
            self.assertEqual('PASS', additional['status'])
            self.assertFalse(additional['actual_os_selection_claim'])
            with self.assertRaises(RuntimeError):
                v10_receipts.verify_v10_receipts(log_with_contract(rows), report, dict(status='BLOCKED'))

    def test_missing_unknown_duplicate_wrong_pane_and_bool_provenance_fail(self):
        for mode in ('missing', 'unknown-property', 'unknown-literal', 'wrong-pane', 'duplicate', 'method', 'bool'):
            rows, report = enriched(); fields = pick(rows, 'language', 'before-activation')['fields']
            matches = fields['paneKnownPropertyMatches']
            if mode == 'missing': fields.pop('paneKnownPropertyMatches')
            elif mode == 'unknown-property': matches[0]['property'] = 'private-identifier-key'
            elif mode == 'unknown-literal': matches[0]['knownLiteral'] = 'unrelated-synthetic-name'
            elif mode == 'wrong-pane': matches[0]['knownLiteral'] = 'Settings'
            elif mode == 'duplicate': matches.append(copy.deepcopy(matches[0]))
            elif mode == 'method': fields['paneSelectorMethod'] = 'diagnostic-count-transplant'
            else: fields['paneIdentityMatchesKnown'] = 1
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_aliases_on_one_element_do_not_double_count_but_two_elements_remain_ambiguous(self):
        rows, report = enriched()
        fields = pick(rows, 'appPane', 'PASS')['fields']
        fields['paneKnownPropertyMatches'] = [dict(property='title', knownLiteral='Parlor'), dict(property='label', knownLiteral='بارلور')]
        self.assertEqual(1, fields['paneCountCapped16'])
        self.assertEqual('PASS', self.verify(rows, report)[1]['status'])
        fields['paneCountCapped16'] = 2
        with self.assertRaises(RuntimeError): v11_receipts.verify_v11_receipts(log_with_contract(rows))
        with self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_successful_actions_need_actual_known_provenance_including_stable_samples(self):
        for stage, status in (('initialPane', 'PASS'), ('language', 'before-activation'), ('language', 'observe')):
            rows, report = enriched(); fields = pick(rows, stage, status)['fields']
            fields.update(paneKnownPropertyMatches=[], paneIdentityMatchesKnown=False)
            # Valid old geometry is intentionally retained: only the new
            # provenance guard should reject this otherwise plausible receipt.
            self.assertEqual('PASS', v10_receipts.verify_v10_receipts(log_with_contract(rows), report, probe_validation.verify_os(report))['status'])
            with self.assertRaises(RuntimeError): v11_receipts.verify_v11_receipts(log_with_contract(rows))

    def test_fresh_names_never_override_hidden_offscreen_ambiguous_or_stale_guards(self):
        for key, value in (('paneHittable', False), ('foreground', False), ('keyboardPresent', True),
                           ('paneCountCapped16', 2), ('targetHittable', False)):
            rows, report = enriched(); pick(rows, 'language', 'before-activation')['fields'][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(rows, report)
        rows, report = enriched(); pick(rows, 'language', 'before-activation')['fields']['paneFrame']['rectangle'][0] = -500
        with self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_absent_os_pane_stays_blocked_and_missing_contract_never_becomes_green(self):
        rows, report = enriched(None, 'language-control-unavailable')
        template = enriched()[0][0]['fields']
        panes = []
        for name in ('settings', 'apps', 'parlor'):
            pane = copy.deepcopy(template)
            pane.update(pane=name, paneCountCapped16=0, paneHittable=False, paneFrame=dict(present=False),
                        paneKnownPropertyMatches=[], paneIdentityMatchesKnown=False,
                        knownNavigationTitles=[dict(knownTitle='Settings', countCapped16=1)])
            panes.append(pane)
        rows.insert(0, dict(schemaVersion=1, ordinal=0, stage='initialPane', status='observe',
                            fields=dict(attempt=1, eligiblePaneCount=0, knownPanes=panes)))
        reordinal(rows)
        original, additional = self.verify(rows, report)
        self.assertEqual('BLOCKED', original['status']); self.assertFalse(additional['actual_os_selection_claim'])
        self.assertEqual(0, additional['known_public_pane_observations'])
        for log in (encoded(rows), log_with_contract(rows, dict(v11_receipts.CONTRACT, schemaVersion=True)),
                    log_with_contract(rows, dict(v11_receipts.CONTRACT, attributeCases=11))):
            with self.assertRaises(RuntimeError): v11_receipts.verify_v11_receipts(log)

    def test_additional_gate_is_after_original_gates_and_all_consumed_inputs_are_bound(self):
        source = Path(runner.__file__).read_text()
        self.assertLess(source.index("receipt['dsc01_matrix'] = verify_probe"), source.index("receipt['v10_os_interaction_proof'] = verify_v10_receipts"))
        self.assertLess(source.index("receipt['v10_os_interaction_proof'] = verify_v10_receipts"), source.index("receipt['v11_pane_identity_proof'] = verify_v11_receipts"))
        controls = runner.control_files()
        self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual({V10 / name for name in V10_REFERENCES}, {path for path in controls if path.parent == V10})
        for name in ('v11_receipts.py', 'test_v11_contract.py'):
            self.assertIn(HERE / name, controls)
        manifest = {entry['path']: entry['sha256'] for entry in runner.control_manifest()}
        for path in controls:
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])


if __name__ == '__main__':
    unittest.main()
