"""Synthetic control/signature tests only; root executes, never native evidence."""
import hashlib
import json
from pathlib import Path
import unittest
from unittest import mock

import run_dsc01_apphost_cycle as runner
import v8_receipts
import v9_receipts
from test_v8_contract import rect, start_log


HERE = Path(__file__).resolve().parent
V8 = HERE.parent / 'dsc01_apphost_v8'
V8_REFERENCES = (
    'run_dsc01_apphost_cycle.py', 'secondary_fifo.py', 'test_secondary_fifo.py', 'test_harness_contract.py',
    'DSC01Probe.swift.in', 'IOSAppLaunchUITests.swift.in', 'DSC01CatalogSelection.swift.in', 'test_catalog_contract.py',
    'DSC01PublicDiagnostics.swift.in', 'DSC01MafiaStartSelection.swift.in', 'DSC01OSPrerequisite.swift.in',
    'v8_receipts.py', 'test_v8_contract.py', 'copied-kotlin-phase.sh.in', 'source-bindings.json', 'copied_sources.py',
    'bind_source.py', 'DSC01ComposeObservation.kt.in', 'DSC01InvocationBridge.kt.in', 'DSC01UIKitObservation.kt.in',
    'DSC01NativeObservation.swift.in', 'probe_validation.py', 'test_copy_observation_contract.py',
)


def preferred():
    return dict(foreground=True, alertCountCapped16=0, keyboardPresent=False, searchFieldCountCapped16=0,
        languageRegionTitleObserved=True, languageRegionTitleHittable=True, englishPrimaryObserved=True,
        englishPrimaryHittable=True, addLanguageSuccessorHittableEnabled=True, englishPrimaryCellCountCapped16=1,
        mergedEnglishPrimaryCountCapped16=0, arabicObserved=True, arabicCellCountCapped16=1,
        arabicTextCountCapped16=2, arabicPreferredRowObserved=True, viewport=rect(0, 0, 402, 874),
        englishPrimaryFrame=rect(20, 171, 362, 68), arabicPreferredRowFrame=rect(20, 239, 362, 68),
        addLanguageSuccessorFrame=rect(20, 307, 362, 55))


def rows(added=False):
    initial = preferred()
    if added:
        initial.update(arabicObserved=False, arabicCellCountCapped16=0, arabicTextCountCapped16=0,
                       arabicPreferredRowObserved=False, arabicPreferredRowFrame=dict(present=False))
    initial['prerequisiteDisposition'] = 'requires-arabic' if added else 'already-satisfied'
    raw = [('ownership', 'PASS', dict(sameExpectedSimulator=True, ownedSyntheticDeviceName=True, debugSimulatorBuild=True)),
           ('settingsRoot', 'PASS', {})]
    for stage in ('general', 'languageRegion'):
        raw += [(stage, 'before-activation', {}), (stage, 'after-activation', {}), (stage, 'PASS', {})]
    raw.append(('initialEnglishPrimary', 'PASS', initial))
    if added:
        for stage in ('addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary'):
            raw += [(stage, 'before-activation', {}), (stage, 'after-activation', {})]
    final = preferred()
    final.update(prerequisiteDisposition='arabic-added' if added else 'already-satisfied', arabicAddedByThisPrerequisite=added)
    raw.append(('finalPreferredLanguages', 'PASS', final))
    return [dict(schemaVersion=1, ordinal=i, stage=stage, status=status, fields=value)
            for i, (stage, status, value) in enumerate(raw, 1)]


def log(selected=None, contract=True):
    result = ['DSC01_OS_PREREQUISITE ' + json.dumps(row) for row in (rows() if selected is None else selected)]
    if contract:
        result.insert(0, 'DSC01_OS_PREREQUISITE_DECISION_CONTRACT ' + json.dumps(dict(schemaVersion=1, cases=24, passed=True)))
    return '\n'.join(result)


def initial(selected):
    return next(row['fields'] for row in selected if row['stage'] == 'initialEnglishPrimary')


class V9PrerequisiteReceiptTest(unittest.TestCase):
    def test_preexisting_pair_passes_without_claiming_addition_or_origin(self):
        with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(log())
        result = v9_receipts.verify_v9_receipts(start_log() + '\n' + log())
        actual = result['os_multilingual_prerequisite']
        self.assertEqual('already-satisfied', actual['disposition'])
        self.assertFalse(actual['arabic_added'])
        self.assertTrue(actual['preexisting_configuration_verified'])
        self.assertIn('Original per-app Settings/AR-System/restart', actual['limitation'])
        self.assertIn('language origin not inferred', actual['limitation'])

    def test_missing_arabic_retains_every_original_addition_oracle(self):
        with mock.patch.object(v8_receipts, 'verify_os_prerequisite', wraps=v8_receipts.verify_os_prerequisite) as old:
            result = v9_receipts.verify_os_prerequisite(log(rows(added=True)))
        old.assert_called_once()
        self.assertEqual('arabic-added', result['disposition'])
        self.assertTrue(result['arabic_added'])
        self.assertFalse(result['preexisting_configuration_verified'])

    def test_executed_swift_decision_contract_is_mandatory_and_unique(self):
        for text in (log(contract=False), log().replace('"cases": 24', '"cases": 23'),
                     log() + '\n' + log().splitlines()[0]):
            with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(text)

    def test_present_language_boolean_alone_never_passes(self):
        for location in ('initial', 'final'):
            selected = rows()
            target = initial(selected) if location == 'initial' else selected[-1]['fields']
            for key in tuple(target):
                if key not in ('englishPrimaryObserved', 'arabicObserved', 'prerequisiteDisposition', 'arabicAddedByThisPrerequisite'):
                    target.pop(key)
            with self.subTest(location=location), self.assertRaises(RuntimeError):
                v9_receipts.verify_os_prerequisite(log(selected))

    def test_wrong_row_order_cannot_be_hidden_by_true_flags(self):
        for location in ('initial', 'final'):
            for key, y in (('arabicPreferredRowFrame', 100), ('arabicPreferredRowFrame', 380), ('englishPrimaryFrame', 380)):
                selected = rows(); target = initial(selected) if location == 'initial' else selected[-1]['fields']
                target[key]['rectangle'][1] = y
                with self.subTest(location=location, key=key, y=y), self.assertRaises(RuntimeError):
                    v9_receipts.verify_os_prerequisite(log(selected))

    def test_covered_picker_ambiguity_and_wrong_primary_block_both_observations(self):
        changes = (('keyboardPresent', True), ('searchFieldCountCapped16', 1), ('alertCountCapped16', 1),
                   ('languageRegionTitleHittable', False), ('englishPrimaryObserved', False),
                   ('englishPrimaryHittable', False), ('arabicPreferredRowObserved', False),
                   ('addLanguageSuccessorHittableEnabled', False), ('englishPrimaryCellCountCapped16', 2),
                   ('mergedEnglishPrimaryCountCapped16', 2), ('arabicCellCountCapped16', 2), ('foreground', False))
        for location in ('initial', 'final'):
            for key, value in changes:
                selected = rows(); target = initial(selected) if location == 'initial' else selected[-1]['fields']
                target[key] = value
                with self.subTest(location=location, key=key), self.assertRaises(RuntimeError):
                    v9_receipts.verify_os_prerequisite(log(selected))

    def test_invalid_missing_or_offscreen_rectangles_block(self):
        for key in ('englishPrimaryFrame', 'arabicPreferredRowFrame', 'addLanguageSuccessorFrame', 'viewport'):
            for value in (dict(present=False), rect(float('nan'), 171, 362, 68), rect(-500, 239, 362, 68)):
                selected = rows(); selected[-1]['fields'][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                    v9_receipts.verify_os_prerequisite(log(selected))

    def test_distinct_outcome_cannot_falsely_claim_language_addition(self):
        for added in (False, True):
            for key, value in (('arabicAddedByThisPrerequisite', not added),
                               ('prerequisiteDisposition', 'already-satisfied' if added else 'arabic-added')):
                selected = rows(added); selected[-1]['fields'][key] = value
                with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        selected = rows(); initial(selected)['prerequisiteDisposition'] = 'requires-arabic'
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))

    def test_incomplete_or_covered_missing_arabic_baseline_blocks(self):
        for key, value in (('arabicCellCountCapped16', 1), ('arabicTextCountCapped16', 1),
                           ('keyboardPresent', True), ('searchFieldCountCapped16', 1), ('englishPrimaryObserved', False)):
            selected = rows(added=True); initial(selected)[key] = value
            with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))

    def test_language_mutation_must_not_occur_on_already_satisfied_branch(self):
        selected = rows(added=True)
        initial(selected).update(preferred(), prerequisiteDisposition='already-satisfied')
        selected[-1]['fields'].update(prerequisiteDisposition='already-satisfied', arabicAddedByThisPrerequisite=False)
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))

    def test_navigation_and_required_addition_steps_cannot_repeat_or_disappear(self):
        for added in (False, True):
            for stage in ('general', 'languageRegion') + (('addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary') if added else ()):
                selected = rows(added)
                next(row for row in selected if row['stage'] == stage and row['status'] == 'after-activation')['status'] = 'observe'
                with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        selected = rows(); selected[2]['stage'] = 'languageRegion'
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))

    def test_blocked_unknown_wrong_owner_and_budget_are_not_success(self):
        for key in ('sameExpectedSimulator', 'ownedSyntheticDeviceName', 'debugSimulatorBuild'):
            selected = rows(); selected[0]['fields'][key] = False
            with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        selected = rows(); selected[-1]['status'] = 'BLOCKED'
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        selected = rows(); selected[1]['stage'] = 'unknown'
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        selected = rows(); selected.insert(-1, dict(selected[-2], status='before-activation'))
        for ordinal, row in enumerate(selected, 1): row['ordinal'] = ordinal
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log(selected))
        with self.assertRaises(RuntimeError): v9_receipts.verify_os_prerequisite(log() + '\nDSC01_OS_PREREQUISITE ' + ' ' * 8193)

    def test_unique_merged_english_primary_is_supported_without_duplicate_rows(self):
        selected = rows()
        for fields in (initial(selected), selected[-1]['fields']):
            fields['englishPrimaryCellCountCapped16'] = 0; fields['mergedEnglishPrimaryCountCapped16'] = 1
        self.assertEqual('already-satisfied', v9_receipts.verify_os_prerequisite(log(selected))['disposition'])


class V9SourceControlTest(unittest.TestCase):
    def test_original_per_app_matrix_probes_start_and_cleanup_stay_byte_identical(self):
        changed = {'run_dsc01_apphost_cycle.py', 'DSC01OSPrerequisite.swift.in', 'test_v8_contract.py', 'source-bindings.json'}
        for name in V8_REFERENCES:
            if name not in changed:
                self.assertEqual((V8 / name).read_bytes(), (HERE / name).read_bytes(), name)
        old = (V8 / 'test_v8_contract.py').read_text()
        expected = old.replace("receipt['v8_public_interaction_proof'] = verify_v8_receipts",
                               "receipt['v9_public_interaction_proof'] = verify_v9_receipts")
        self.assertEqual(expected, (HERE / 'test_v8_contract.py').read_text())
        marker = '        finally:\n            deferred = []'
        old_runner = (V8 / 'run_dsc01_apphost_cycle.py').read_text()
        new_runner = Path(runner.__file__).read_text()
        self.assertEqual(old_runner[old_runner.index(marker):], new_runner[new_runner.index(marker):])
        self.assertEqual(json.loads((V8 / 'source-bindings.json').read_text())['source_identity'],
                         json.loads((HERE / 'source-bindings.json').read_text())['source_identity'])

    def test_missing_arabic_still_executes_unchanged_real_settings_actions(self):
        marker = '        try osPrerequisiteTapRow(settings, control: .addLanguage, stage: .addLanguage, diagnostics: diagnostics)'
        old = (V8 / 'DSC01OSPrerequisite.swift.in').read_text().split(marker)[1]
        new = (HERE / 'DSC01OSPrerequisite.swift.in').read_text().split(marker)[1]
        expected = old.replace('let final = osPreferredLanguagesMetadata(settings)', 'var final = osPreferredLanguagesMetadata(settings)')
        expected = expected.replace('        try diagnostics.append(stage: .finalPreferredLanguages, status: "PASS", fields: final)',
            '        final["prerequisiteDisposition"] = "arabic-added"\n        final["arabicAddedByThisPrerequisite"] = true\n'
            '        try diagnostics.append(stage: .finalPreferredLanguages, status: "PASS", fields: final)')
        self.assertEqual(expected, new)

    def test_satisfied_branch_reobserves_and_returns_without_mutating_languages(self):
        source = (HERE / 'DSC01OSPrerequisite.swift.in').read_text()
        branch = source.split('        if disposition == .alreadySatisfied {')[1].split('        try osPrerequisiteTapRow(settings, control: .addLanguage')[0]
        for required in ('var final = osPreferredLanguagesMetadata(settings)', 'guard osPreferredListIsUncovered(final)',
                         'final["prerequisiteDisposition"] = "already-satisfied"',
                         'final["arabicAddedByThisPrerequisite"] = false', 'return // Original per-app Settings/System/restart'):
            self.assertIn(required, branch)
        for forbidden in ('.tap()', 'typeText(', 'UserDefaults', 'osPrerequisiteTapRow(', 'syntheticLanguage('):
            self.assertNotIn(forbidden, branch)
        self.assertIn('try verifyOSPrerequisiteDecisionContract()', source)
        self.assertIn('XCTAssertEqual(cases.count, 24)', source)
        self.assertIn('XCTAssertEqual(osInitialDisposition(observed), expected', source)

    def test_v9_result_is_after_exact_xctest_and_before_original_probe_gate(self):
        source = Path(runner.__file__).read_text()
        self.assertLess(source.index("receipt['xctest'] = verify_xctest"), source.index("receipt['v9_public_interaction_proof'] = verify_v9_receipts"))
        self.assertLess(source.index("receipt['v9_public_interaction_proof'] = verify_v9_receipts"), source.index("receipt['dsc01_matrix'] = verify_probe"))
        for name in ('v9_receipts.py', 'test_v9_contract.py'):
            self.assertIn(HERE / name, runner.control_files())

    def test_every_consumed_v8_reference_and_before_after_binding_are_explicit(self):
        controls = runner.control_files()
        self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual({V8 / name for name in V8_REFERENCES}, {p for p in controls if p.parent == V8})
        manifest = {row['path']: row['sha256'] for row in runner.control_manifest()}
        for name in V8_REFERENCES:
            path = V8 / name
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])


if __name__ == '__main__':
    unittest.main()
