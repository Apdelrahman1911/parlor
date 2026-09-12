"""Pure synthetic source/receipt controls; root executes, never native proof."""
import copy
import hashlib
import json
from pathlib import Path
import re
import unittest

import probe_validation
import run_dsc01_apphost_cycle as runner
import v10_receipts as receipts
import v10_sources as source
from test_copy_observation_contract import os_report
from test_v8_contract import rect

HERE = Path(__file__).resolve().parent
V9 = HERE.parent / 'dsc01_apphost_v9'
V9_REFERENCES = (
    'run_dsc01_apphost_cycle.py', 'secondary_fifo.py', 'test_secondary_fifo.py', 'test_harness_contract.py',
    'DSC01Probe.swift.in', 'IOSAppLaunchUITests.swift.in', 'DSC01CatalogSelection.swift.in', 'test_catalog_contract.py',
    'DSC01PublicDiagnostics.swift.in', 'DSC01MafiaStartSelection.swift.in', 'DSC01OSPrerequisite.swift.in',
    'v8_receipts.py', 'test_v8_contract.py', 'v9_receipts.py', 'test_v9_contract.py', 'copied-kotlin-phase.sh.in',
    'copied_sources.py', 'bind_source.py', 'DSC01ComposeObservation.kt.in', 'DSC01InvocationBridge.kt.in',
    'DSC01UIKitObservation.kt.in', 'DSC01NativeObservation.swift.in', 'probe_validation.py', 'test_copy_observation_contract.py')


def pane(name):
    return dict(pane=name, foreground=True, viewport=rect(0, 0, 402, 874), alertCountCapped16=0,
                keyboardPresent=False, paneCountCapped16=1, paneHittable=True, paneFrame=rect(0, 44, 402, 54),
                knownNavigationTitles=[])  # Identifier diagnostics are not label-query proof.


def control(name, y=200):
    value = pane(receipts.ACTION_PANES[name])
    value.update(control=name, selectorKind='audit-button' if value['pane'] == 'audit' else 'known-cell',
                 targetCountCapped16=1, targetHittable=True, targetEnabled=True, targetFrame=rect(20, y, 362, 44))
    return value


def fixture(start='settings', blocked=None):
    rows = []
    def add(stage, status, fields):
        rows.append(dict(schemaVersion=1, ordinal=len(rows) + 1, stage=stage, status=status, fields=copy.deepcopy(fields)))
    def action(name):
        fields = control(name); stage = receipts.ACTION_STAGES[name]
        for attempt in range(1, 4): add(stage, 'observe', dict(fields, attempt=attempt))
        add(stage, 'before-activation', dict(fields, stableFrames=[copy.deepcopy(fields['targetFrame']) for _ in range(3)]))
        add(stage, 'after-activation', dict(control=name, activationCount=1, expectedSourcePane=fields['pane']))
    if start is None:
        add('initialPane', 'BLOCKED', dict(reason='actual-known-pane-unavailable-or-ambiguous'))
    else:
        add('initialPane', 'PASS', pane(start))
        if start == 'settings':
            action('apps'); add('appsSuccessor', 'PASS', pane('apps'))
        if start != 'parlor': action('parlor')
        add('appPane', 'PASS', pane('parlor'))
        if blocked == 'language-control-unavailable':
            add('language', 'BLOCKED', dict(reason='exact-language-unavailable'))
        else:
            action('language'); add('english', 'PASS', pane('language'))
            if blocked:
                add('english', 'BLOCKED', dict(reason='exact-selection-unavailable'))
            else: action('english')
    report = os_report(blocked=blocked is not None)
    terminal = blocked or 'verified-english'
    completed = report['observations'].pop()
    marked = copy.deepcopy(completed); marked.update(phase='sample', osDisposition=terminal)
    completed.update(ordinal=marked['ordinal'] + 1, osDisposition=terminal)
    report['observations'] += [marked, completed]
    action('showMarkers')
    add('markerControls', 'PASS', dict(visibleMarkerRows=[control(name, y) for name, y in
        zip(('noRow', 'noSelection', 'verified'), (200, 248, 296))]))
    action(receipts.TERMINALS[terminal])
    add('marker', 'PASS', dict(sequence=marked['ordinal'], osDisposition=terminal, completed=False))
    action('complete')
    add('complete', 'PASS', dict(sequence=completed['ordinal'], osDisposition=terminal, completed=True))
    return rows, report


def encoded(rows, contract=True):
    result = ['DSC01_OS_APP_INTERACTION ' + json.dumps(row) for row in rows]
    if contract: result.insert(0, 'DSC01_OS_INTERACTION_DECISION_CONTRACT ' + json.dumps(dict(schemaVersion=1, cases=24, passed=True)))
    return '\n'.join(result)


def reordinal(rows):
    for ordinal, row in enumerate(rows, 1): row['ordinal'] = ordinal


def pick(rows, stage, status):
    return next(row for row in rows if (row['stage'], row['status']) == (stage, status))


class V10OSReceiptTest(unittest.TestCase):
    def verify(self, rows, report):
        return receipts.verify_v10_receipts(encoded(rows), report, probe_validation.verify_os(report))

    def test_all_observed_public_entry_panes_keep_full_original_os_oracles(self):
        for start in ('settings', 'apps', 'parlor'):
            rows, report = fixture(start)
            result = self.verify(rows, report)
            self.assertEqual('PASS', result['status'])
            self.assertTrue(result['original_os_ar_system_restart_oracles_required'])
            self.assertEqual(['language', 'english'], result['actual_public_actions'][-2:])

    def test_unproven_pane_or_unavailable_language_remains_explicitly_blocked(self):
        for start, blocked in ((None, 'language-control-unavailable'), ('settings', 'language-control-unavailable'),
                               ('parlor', 'selection-unavailable')):
            rows, report = fixture(start, blocked)
            result = self.verify(rows, report)
            self.assertEqual('BLOCKED', result['status'])
            self.assertNotIn('actual_os_english_selection', result)

    def test_opened_or_verified_marker_alone_cannot_manufacture_os_selection(self):
        for stage in ('language', 'english'):
            rows, report = fixture()
            removed = [row for row in rows if not (row['stage'] == stage and row['status'].endswith('activation'))]
            reordinal(removed)
            with self.assertRaises(RuntimeError): self.verify(removed, report)
        rows, report = fixture()
        with self.assertRaises(RuntimeError): receipts.verify_v10_receipts(encoded(rows), report, dict(status='BLOCKED'))

    def test_contract_must_be_executed_once_with_all_twenty_four_cases(self):
        rows, report = fixture()
        for log in (encoded(rows, False), encoded(rows).replace('"cases": 24', '"cases": 23'),
                    encoded(rows).replace('"schemaVersion": 1, "cases": 24', '"schemaVersion": true, "cases": 24'),
                    encoded(rows) + '\n' + encoded(rows).splitlines()[0]):
            with self.assertRaises(RuntimeError): receipts.verify_v10_receipts(log, report, dict(status='PASS'))

    def test_missing_repeated_or_reordered_actions_fail_closed(self):
        for stage in ('apps', 'parlor', 'language', 'english', 'markerControls', 'marker', 'complete'):
            for mode in ('missing', 'duplicate', 'reorder'):
                rows, report = fixture(); target = pick(rows, stage, 'after-activation'); index = rows.index(target)
                if mode == 'missing': rows.remove(target)
                elif mode == 'duplicate': rows.insert(index, copy.deepcopy(target))
                else: rows.remove(target); rows.insert(0, target)
                reordinal(rows)
                with self.subTest(stage=stage, mode=mode), self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_page_and_target_guards_reject_hidden_ambiguous_offscreen_and_wrong_pane(self):
        changes = (('foreground', False), ('keyboardPresent', True), ('alertCountCapped16', 1),
                   ('paneCountCapped16', 2), ('paneHittable', False), ('targetCountCapped16', 2),
                   ('targetHittable', False), ('targetEnabled', False), ('pane', 'settings'),
                   ('selectorKind', 'prefix-fallback'), ('targetCountCapped16', True))
        for key, value in changes:
            rows, report = fixture(); pick(rows, 'language', 'before-activation')['fields'][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(rows, report)
        for key in ('targetFrame', 'viewport', 'paneFrame'):
            rows, report = fixture(); pick(rows, 'language', 'before-activation')['fields'][key] = rect(-500, -500, 10, 10)
            with self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_three_real_stable_samples_and_finite_frames_are_mandatory(self):
        for mode in ('missing', 'moved', 'nan', 'unhittable'):
            rows, report = fixture(); before = pick(rows, 'marker', 'before-activation')['fields']
            original_target = copy.deepcopy(before['targetFrame'])
            if mode == 'missing': before['stableFrames'].pop()
            elif mode == 'moved': before['stableFrames'][0]['rectangle'][0] += 2
            elif mode == 'nan': before['stableFrames'][0]['rectangle'][0] = float('nan')
            else: pick(rows, 'marker', 'observe')['fields']['targetHittable'] = False
            self.assertEqual(original_target, before['targetFrame'], 'A stable-sample mutation must not alias the final target')
            if mode in ('moved', 'nan'):
                self.assertEqual([original_target, original_target], before['stableFrames'][1:])
            expected = ('numeric bounds invalid' if mode == 'nan' else 'Actual target must' if mode == 'unhittable'
                        else 'No three stable measured target frames')
            with self.subTest(mode=mode), self.assertRaisesRegex(RuntimeError, expected): self.verify(rows, report)
        rows, report = fixture()
        sample = pick(rows, 'marker', 'observe'); rows.remove(sample)
        rows.insert(1, sample); reordinal(rows)
        with self.assertRaises(RuntimeError): self.verify(rows, report)
        for cumulative in (True, False):
            rows, report = fixture(); before = pick(rows, 'marker', 'before-activation')['fields']
            frames = [rect(20 + delta, 200, 362, 44) for delta in ((0, 0.4, 0.8) if cumulative else (0, 0.2, 0.4))]
            before['stableFrames'] = frames
            before['targetFrame'] = rect(21.2 if cumulative else 22, 200, 362, 44)
            samples = [row for row in rows if (row['stage'], row['status']) == ('marker', 'observe')]
            for sample, frame in zip(samples, frames): sample['fields']['targetFrame'] = frame
            with self.subTest(cumulative=cumulative), self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_expander_successor_requires_all_three_visible_ordered_full_height_markers(self):
        for mode in ('missing', 'covered', 'clipped', 'overlap'):
            rows, report = fixture(); panel = pick(rows, 'markerControls', 'PASS')['fields']['visibleMarkerRows']
            if mode == 'missing': panel.pop()
            elif mode == 'covered': panel[1]['targetHittable'] = False
            elif mode == 'clipped': panel[0]['targetFrame']['rectangle'][3] = 10
            else: panel[1]['targetFrame']['rectangle'][1] = 201
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_marker_requires_matching_durable_sample_before_completed_report(self):
        for mode in ('wrong-sequence', 'wrong-status', 'no-sample', 'already-complete'):
            rows, report = fixture(); marker = pick(rows, 'marker', 'PASS')['fields']
            if mode == 'wrong-sequence': marker['sequence'] += 10
            elif mode == 'wrong-status': marker['osDisposition'] = 'opened'
            elif mode == 'no-sample': report['observations'][-2]['phase'] = 'complete'
            else: marker['completed'] = True
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_report_expansion_and_complete_cannot_precede_their_receipts(self):
        for stage, before in (('markerControls', 'after-activation'), ('marker', 'after-activation'), ('complete', 'after-activation')):
            rows, report = fixture(); passed = pick(rows, stage, 'PASS'); anchor = pick(rows, stage, before)
            rows.remove(passed); rows.insert(rows.index(anchor), passed); reordinal(rows)
            with self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_original_os_preference_restoration_and_restart_assertions_are_not_bypassed(self):
        rows, report = fixture()
        explicit = next(event for event in report['observations'] if event['preferences']['setting'] == 'ar')
        explicit['preferences']['ownerPrevious'] = []
        with self.assertRaises(RuntimeError): self.verify(rows, report)
        rows, report = fixture()
        report['observations'] = [row for row in report['observations'] if row['fixture'] != 'continue']
        with self.assertRaises(RuntimeError): self.verify(rows, report)

    def test_unknown_stages_bool_counts_payload_bounds_and_missing_receipts_fail(self):
        rows, report = fixture(); pick(rows, 'language', 'after-activation')['fields']['activationCount'] = True
        with self.assertRaises(RuntimeError): self.verify(rows, report)
        rows, report = fixture(); rows[0]['stage'] = 'unknown'
        with self.assertRaises(RuntimeError): self.verify(rows, report)
        rows, report = fixture()
        for log in ('', encoded(rows) + '\nDSC01_OS_APP_INTERACTION ' + ' ' * 8193):
            with self.assertRaises(RuntimeError): receipts.verify_v10_receipts(log, report, dict(status='PASS'))
        missing = [row for row in rows if not row['status'].endswith('activation')]
        reordinal(missing)
        with self.assertRaises(RuntimeError): self.verify(missing, report)
        rows, report = fixture('settings', 'language-control-unavailable')
        pick(rows, 'language', 'BLOCKED')['stage'] = 'english'
        with self.assertRaises(RuntimeError): self.verify(rows, report)


class V10CopySourceContractTest(unittest.TestCase):
    def test_all_original_templates_tests_kotlin_probes_and_cleanup_remain_immutable(self):
        for name in V9_REFERENCES:
            if name != 'run_dsc01_apphost_cycle.py': self.assertEqual((V9 / name).read_bytes(), (HERE / name).read_bytes(), name)
        marker = '        finally:\n            deferred = []'
        old = (V9 / 'run_dsc01_apphost_cycle.py').read_text(); current = Path(runner.__file__).read_text()
        self.assertEqual(old[old.index(marker):], current[current.index(marker):])

    def test_renderer_preserves_every_original_postselection_assertion_and_non_os_byte(self):
        original = (HERE / 'IOSAppLaunchUITests.swift.in').read_text(); transformed = source.render_v10_ui(original)
        begin = original.index(source.OS_FUNCTION); end = original.index(source.POST_SELECTION, begin)
        restored = transformed.replace(source.NEW_PREFIX, original[begin:end], 1).replace(source.NEW_VERIFIED, source.OLD_VERIFIED, 1)
        self.assertEqual(original, restored)
        old_tail = original[end:].replace(source.OLD_VERIFIED, source.NEW_VERIFIED, 1)
        self.assertEqual(old_tail, transformed[transformed.index(source.POST_SELECTION):])
        self.assertEqual(original[:begin], transformed[:transformed.index(source.OS_FUNCTION)])

    def test_renderer_fails_closed_on_changed_inputs_missing_or_duplicate_boundaries(self):
        for renderer, filename in ((source.render_v10_ui, 'IOSAppLaunchUITests.swift.in'), (source.render_v10_probe, 'DSC01Probe.swift.in')):
            original = (HERE / filename).read_text()
            with self.assertRaises(RuntimeError): renderer(original + '\n')
            with self.assertRaises(RuntimeError): renderer(original.replace('osDisposition', 'changed'))
        for changed in ('none', 'needle needle'):
            with self.assertRaises(RuntimeError): source.replace_once(changed, 'needle', 'new')

    def test_probe_only_adds_terminal_report_visibility_not_preference_or_native_behavior(self):
        original = (HERE / 'DSC01Probe.swift.in').read_text(); transformed = source.render_v10_probe(original)
        restored = transformed.replace(source.MARKER_PROPERTY, '', 1).replace(source.MARKER_REVEAL, '', 1)
        restored = restored.replace(source.NEW_MARKER_LAYOUT, source.OLD_MARKER_LAYOUT, 1)
        self.assertEqual(original, restored)
        self.assertIn('osMarkerControlsVisible = false', source.MARKER_PROPERTY)
        self.assertIn('scenario == "os" && report?.completed == false', source.MARKER_REVEAL)
        self.assertIn('if probe.osMarkerControlsVisible', source.NEW_MARKER_LAYOUT)
        self.assertEqual(3, source.NEW_MARKER_LAYOUT.count('minHeight: 44'))
        for forbidden in ('UserDefaults', 'language_override', 'AppleLanguages', 'semanticContentAttribute', 'layoutIfNeeded'):
            self.assertNotIn(forbidden, source.MARKER_PROPERTY + source.MARKER_REVEAL + source.NEW_MARKER_LAYOUT)

    def test_expansion_only_occurs_inside_terminal_finish_after_original_restart_oracles(self):
        helper = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
        finish = helper[helper.index('private func finishOSInvestigation('):helper.index('private func verifyOSInteractionDecisionContract(')]
        self.assertEqual(1, helper.count('control: .showMarkers'))
        self.assertIn('control: .showMarkers', finish)
        self.assertLess(finish.index('awaitOSMarkerPanel('), finish.index('control: control'))
        self.assertLess(finish.index('diagnostics.append("marker", "PASS"'), finish.index('control: .complete'))
        transformed = source.render_v10_ui((HERE / 'IOSAppLaunchUITests.swift.in').read_text())
        tail = transformed[transformed.index(source.POST_SELECTION):transformed.index('private func recordOSSelectorDiagnostics')]
        self.assertLess(tail.index('try assertSystem(restarted'), tail.index('try finishOSInvestigation'))

    def test_source_whitelisted_english_and_arabic_app_names_match_shipped_resources(self):
        helper = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
        for locale in ('en', 'ar'):
            text = (runner.ROOT / ('iosApp/iosApp/' + locale + '.lproj/InfoPlist.strings')).read_text()
            label = re.search(r'"CFBundleDisplayName" = "([^"]+)";', text).group(1)
            self.assertIn('"' + label + '"', helper)
        for forbidden in ('BEGINSWITH', 'CONTAINS', 'debugDescription', 'typeText(', 'URL(', 'UserDefaults', '.coordinate('):
            self.assertNotIn(forbidden, helper)
        self.assertEqual(1, helper.count('current.tap()'))
        self.assertIn('if allowScroll && attempt < 6', helper)

    def test_new_real_swift_contract_and_complete_original_probe_gate_are_both_wired(self):
        code = Path(runner.__file__).read_text()
        self.assertIn('render_v10_ui(', code); self.assertIn('render_v10_probe(', code)
        self.assertIn("FIXTURE / 'DSC01OSAppSettings.swift.in'", code)
        self.assertLess(code.index("receipt['dsc01_matrix'] = verify_probe"), code.index("receipt['v10_os_interaction_proof'] = verify_v10_receipts"))
        self.assertIn("receipt['dsc01_matrix']['os_settings_gate']", code)
        helper = (HERE / 'DSC01OSAppSettings.swift.in').read_text()
        self.assertIn('XCTAssertEqual(cases.count, 18)', helper)
        self.assertIn('XCTAssertEqual(dsc01OSRowEligible(item.0), item.1', helper)
        self.assertIn('dsc01OSStableTrain(stable, final: currentFrame)', helper)
        self.assertIn('Cumulative drift is not stability', helper)
        self.assertIn('Final re-query cannot drift', helper)
        self.assertIn('try verifyOSInteractionDecisionContract()', source.NEW_PREFIX)
        self.assertEqual(5, len(runner.EXPECTED_TESTS))

    def test_every_consumed_old_and_new_control_is_bound_by_full_path(self):
        controls = runner.control_files()
        self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual({V9 / name for name in V9_REFERENCES}, {path for path in controls if path.parent == V9})
        for name in ('v10_sources.py', 'DSC01OSAppSettings.swift.in', 'v10_receipts.py', 'test_v10_contract.py'):
            self.assertIn(HERE / name, controls)
        manifest = {row['path']: row['sha256'] for row in runner.control_manifest()}
        for path in controls:
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])


if __name__ == '__main__':
    unittest.main()
