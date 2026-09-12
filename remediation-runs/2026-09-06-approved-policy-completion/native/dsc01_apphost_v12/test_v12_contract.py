"""Synthetic source/receipt controls; root executes, never native evidence.

The actual OS action may leave a PRESENT or ABSENT app-domain preference.
Neither branch may invent or rebase it, or stand in for the other branch.
"""
import ast
import copy
import hashlib
import json
from pathlib import Path
import unittest
from unittest import mock

import probe_validation as legacy
import run_dsc01_apphost_cycle as runner
import v10_receipts
import v10_sources
import v11_receipts
import v12_receipts as receipts
import v12_sources as source
from test_copy_observation_contract import base_composition, local_report, native_observation, settings_report
from test_harness_contract import preferences
from test_v10_contract import pick, reordinal
from test_v11_contract import V10_REFERENCES, enriched, log_with_contract

HERE = Path(__file__).resolve().parent
V11 = HERE.parent / 'dsc01_apphost_v11'
V11_REFERENCES = V10_REFERENCES + ('v11_receipts.py', 'test_v11_contract.py')
TOKEN = '00000000-0000-4000-8000-000000000001'
BOOTS = ('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
         '30000000-0000-4000-8000-000000000001')
STAGE_PREFIX = 'DSC01_OS_PREFERENCE_BASELINE '
CONTRACT_PREFIX = 'DSC01_OS_PREFERENCE_DECISION_CONTRACT '


def fixture(present=False, languages=None):
    actions, _ = enriched(property_name='identifier')
    languages = (['en-US', 'ar'] if present else []) if languages is None else list(languages)
    baseline = preferences(app=languages)
    report = dict(schemaVersion=6, scenario='os', runToken=TOKEN, completed=True, observations=[])

    def add(boot_index, phase, preference, mounted=False, status='opened'):
        before = phase == 'before_main'
        direction = 'rtl' if preference['preferredLanguage'] == 'ar' else 'ltr'
        composition = {} if before else base_composition()
        if mounted:
            composition.update(settingsMounted=True, settingsDirection=direction)
        native = native_observation(before)
        if not before:
            native['outerDirection'] = 'force_' + direction
        report['observations'].append(dict(ordinal=len(report['observations']) + 1, phase=phase,
            fixture='continue' if boot_index == 2 else 'existing', boot=BOOTS[boot_index],
            preferences=copy.deepcopy(preference), nativeDirection='unavailable' if before else 'force_' + direction,
            controllerCreations=0 if before else 1, native=native, composition=json.dumps(composition),
            osDisposition=status, samplingWindow=0, samplingIndex=0, samplingElapsedMilliseconds=0,
            samplingContext='none'))

    initial = preferences(app=['ar-EG', 'en'], language='ar')
    add(0, 'before_main', initial, status='not-investigated')
    add(0, 'sample', initial, status='not-investigated')  # Original synthetic System, before opening OS Settings.
    add(1, 'before_main', baseline)
    add(1, 'sample', baseline)  # Post-real-OS-action baseline, before any in-app override.
    add(1, 'sample', baseline, mounted=True)
    add(1, 'sample', preferences('ar', ['ar'], 'ar', languages, 'ar'), mounted=True)
    add(1, 'sample', baseline, mounted=True)
    add(2, 'before_main', baseline)
    add(2, 'sample', baseline, mounted=True)
    add(2, 'sample', baseline, mounted=True, status='verified-english')
    add(2, 'complete', baseline, mounted=True, status='verified-english')
    stages = [dict(schemaVersion=1, stage=name, beforeChoiceSequence=2, beforeChoiceBoot=BOOTS[0],
        baselineSequence=4, baselineBoot=BOOTS[1], sequence=sequence,
        boot=BOOTS[2] if name == 'restart' else BOOTS[1], hasAppOverride=present,
        appLanguages=copy.deepcopy(languages)) for name, sequence in zip(receipts.STAGES, (4, 6, 7, 9))]
    pick(actions, 'marker', 'PASS')['fields']['sequence'] = 10
    pick(actions, 'complete', 'PASS')['fields']['sequence'] = 11
    return actions, report, stages


def encoded(actions, stages, contract=None):
    lines = log_with_contract(actions).splitlines()
    prefix = 'DSC01_OS_APP_INTERACTION '
    # All extra durable stages occur after the actual one-tap English action,
    # before any terminal-panel observations. No action is added or retried.
    position = next((index for index, line in enumerate(lines) if line.startswith(prefix) and
                     json.loads(line[len(prefix):])['stage'] == 'markerControls'), len(lines))
    lines[position:position] = [STAGE_PREFIX + json.dumps(stage) for stage in stages]
    lines.insert(0, CONTRACT_PREFIX + json.dumps(receipts.CONTRACT if contract is None else contract))
    return '\n'.join(lines)


def alter_composition(event, **changes):
    composition = json.loads(event['composition']); composition.update(changes)
    event['composition'] = json.dumps(composition)


class V12ExactOSPreferenceContractTest(unittest.TestCase):
    def verify(self, actions, report, stages, log=None):
        log = encoded(actions, stages) if log is None else log
        gate = receipts.verify_os(report, log)
        action_proof = v10_receipts.verify_v10_receipts(log, report, gate)
        pane_proof = v11_receipts.verify_v11_receipts(log)
        self.assertEqual(gate['status'], action_proof['status'])
        self.assertEqual('PASS', pane_proof['status'])
        return gate

    def test_absent_is_exactly_preserved_not_claimed_as_present_or_old_oracle_execution(self):
        actions, report, stages = fixture()
        with self.assertRaisesRegex(RuntimeError, 'Actual OS preference was not retained'):
            legacy.verify_os(report)  # The superseded presence assumption rejects this valid synthetic branch.
        with mock.patch.object(legacy, 'verify_os', wraps=legacy.verify_os) as original:
            gate = self.verify(actions, report, stages)
            original.assert_not_called()
        self.assertEqual('ABSENT', gate['observed_os_preference_representation'])
        self.assertFalse(gate['actual_present_os_preference_coverage'])
        self.assertFalse(gate['original_present_oracle_executed'])
        self.assertTrue(gate['exact_presence_and_array_restored'])
        self.assertFalse(gate['physical_device_claim'])

    def test_present_preserves_exact_array_order_and_still_executes_original_os_oracle(self):
        for languages in (['en'], ['en-US', 'ar'], ['en-GB', 'ar-EG', 'en']):
            actions, report, stages = fixture(True, languages)
            with self.subTest(languages=languages), mock.patch.object(legacy, 'verify_os', wraps=legacy.verify_os) as original:
                gate = self.verify(actions, report, stages)
                original.assert_called_once_with(report)
            self.assertEqual('PRESENT', gate['observed_os_preference_representation'])
            self.assertTrue(gate['actual_present_os_preference_coverage'])
            self.assertTrue(gate['original_present_oracle_executed'])

    def test_missing_nonboolean_inconsistent_and_unbounded_representation_is_rejected(self):
        for key, value in (('hasAppOverride', 0), ('hasAppOverride', True), ('appLanguages', ['en']),
                           ('appLanguages', None), ('appLanguages', ['zz']), ('appLanguages', ['en'] * 9),
                           ('appLanguages', ['en-' + 'A' * 40])):
            actions, report, stages = fixture(); stages[0][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError): self.verify(actions, report, stages)
        for owner in ('stage', 'raw'):
            actions, report, stages = fixture()
            (stages[0] if owner == 'stage' else report['observations'][3]['preferences']).pop('hasAppOverride')
            with self.subTest(owner=owner), self.assertRaises(RuntimeError): self.verify(actions, report, stages)
        for key, value in (('baselineSequence', True), ('sequence', 257), ('boot', 'not-a-uuid'), ('schemaVersion', True)):
            actions, report, stages = fixture(); stages[0][key] = value
            with self.subTest(key=key), self.assertRaises(RuntimeError): self.verify(actions, report, stages)
        actions, report, stages = fixture(); report['observations'][0]['ordinal'] = True
        with self.assertRaises(RuntimeError): self.verify(actions, report, stages)

    def test_before_app_present_cannot_be_rebased_to_absent_after_app_initialization(self):
        actions, report, stages = fixture()
        report['observations'][2]['preferences'] = preferences(app=['en'])
        with self.assertRaisesRegex(RuntimeError, 'BEFORE-App representation differs'):
            self.verify(actions, report, stages)
        for present in (False, True):
            actions, report, stages = fixture(present)
            stages[2].update(hasAppOverride=not present, appLanguages=[] if present else ['en'])
            with self.subTest(present=present), self.assertRaisesRegex(RuntimeError, 'silently rebased'):
                self.verify(actions, report, stages)

    def test_actual_before_choice_and_fresh_post_choice_process_are_bound(self):
        for key, value in (('beforeChoiceSequence', 1), ('beforeChoiceSequence', 4),
                           ('beforeChoiceBoot', BOOTS[1]), ('baselineBoot', BOOTS[0])):
            actions, report, stages = fixture()
            for stage in stages: stage[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError): self.verify(actions, report, stages)
        actions, report, stages = fixture()
        report['observations'][1]['preferences'] = preferences('ar', ['ar'], 'ar', language='ar')
        with self.assertRaisesRegex(RuntimeError, 'Pre-selection observation'):
            self.verify(actions, report, stages)

    def test_actual_arabic_owner_must_preserve_previous_presence_and_exact_value(self):
        for present in (False, True):
            for mode in ('wrong-value', 'wrong-presence', 'not-owned', 'wrong-language'):
                actions, report, stages = fixture(present); pref = report['observations'][5]['preferences']
                if mode == 'wrong-value': pref.update(ownerPrevious=['en'], ownerPreviousPresent=True)
                elif mode == 'wrong-presence': pref['ownerPreviousPresent'] = not present
                elif mode == 'not-owned': pref.update(ownerPresent=False, ownerInstalled='none')
                else: pref['preferredLanguage'] = 'en'
                with self.subTest(present=present, mode=mode), self.assertRaises(RuntimeError):
                    self.verify(actions, report, stages)

    def test_direct_english_settings_between_os_baseline_and_arabic_is_required(self):
        for mode in ('missing', 'rtl', 'wrong-boot', 'wrong-preference'):
            actions, report, stages = fixture(); event = report['observations'][4]
            if mode == 'missing': alter_composition(event, settingsMounted=False, settingsDirection='absent')
            elif mode == 'rtl': alter_composition(event, settingsDirection='rtl')
            elif mode == 'wrong-boot': event['boot'] = BOOTS[0]
            else: event['preferences'] = preferences(app=['en'])
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(actions, report, stages)

    def test_system_restart_and_terminal_cannot_replace_the_exact_original_pair(self):
        for present in (False, True):
            for index in (6, 7, 8, 10):
                actions, report, stages = fixture(present)
                report['observations'][index]['preferences'] = preferences(app=[] if present else ['en'])
                with self.subTest(present=present, index=index), self.assertRaises(RuntimeError):
                    self.verify(actions, report, stages)
        actions, report, stages = fixture(True)
        report['observations'][6]['preferences']['appLanguages'].reverse()
        with self.assertRaises(RuntimeError): self.verify(actions, report, stages)

    def test_restart_requires_a_distinct_before_app_process_not_a_reused_observation(self):
        for mode in ('same-boot', 'foreign-boot', 'before-baseline', 'reused-stage'):
            actions, report, stages = fixture()
            if mode == 'same-boot':
                for event in report['observations'][7:]: event['boot'] = BOOTS[1]
                stages[-1]['boot'] = BOOTS[1]
            elif mode == 'foreign-boot': stages[-1]['boot'] = BOOTS[0]
            elif mode == 'before-baseline': stages[-1]['sequence'] = 3
            else: stages[-1].update(sequence=7, boot=BOOTS[1])
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(actions, report, stages)
        for missing_start in (2, 7):
            actions, report, stages = fixture()
            event = report['observations'][missing_start]
            event.update(phase='sample', native=native_observation(), nativeDirection='force_ltr',
                         controllerCreations=1, composition=json.dumps(base_composition()))
            with self.subTest(missing_start=missing_start), self.assertRaises(RuntimeError):
                self.verify(actions, report, stages)

    def test_native_compose_identity_and_full_geometry_guards_remain_mandatory(self):
        for index in (3, 5, 6, 8):
            for mode in ('native-direction', 'controller', 'window', 'geometry', 'compose-direction'):
                actions, report, stages = fixture(); event = report['observations'][index]
                if mode == 'native-direction': event['nativeDirection'] = 'force_ltr' if index == 5 else 'force_rtl'
                elif mode == 'controller': event['native']['sameComposeController'] = False
                elif mode == 'window': event['native']['sameWindow'] = False
                elif mode == 'geometry': event['native']['geometry']['composeBounds'][2] -= 10
                elif index == 3: continue  # Baseline is deliberately before actual Settings navigation.
                else: alter_composition(event, settingsDirection='ltr' if index == 5 else 'rtl')
                with self.subTest(index=index, mode=mode), self.assertRaises(RuntimeError):
                    self.verify(actions, report, stages)

    def test_four_ordered_stages_and_exact_executed_native_contract_are_required(self):
        for mode in ('missing', 'duplicate', 'reordered', 'after-reporting', 'before-english'):
            actions, report, stages = fixture()
            if mode == 'missing': stages.pop()
            elif mode == 'duplicate': stages.append(copy.deepcopy(stages[-1]))
            elif mode == 'reordered': stages[1], stages[2] = stages[2], stages[1]
            log = encoded(actions, stages)
            if mode in {'after-reporting', 'before-english'}:
                lines = log.splitlines(); stage_lines = [line for line in lines if line.startswith(STAGE_PREFIX)]
                lines = [line for line in lines if not line.startswith(STAGE_PREFIX)]
                at = len(lines) if mode == 'after-reporting' else 1
                lines[at:at] = stage_lines; log = '\n'.join(lines)
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(actions, report, stages, log)
        actions, report, stages = fixture(); original = encoded(actions, stages)
        changed = [original.split('\n', 1)[1], original + '\n' + original.splitlines()[0]]
        for key, value in (('shapeCases', 13), ('ownershipCases', 5), ('restoreCases', 3), ('schemaVersion', True), ('passed', 1)):
            changed.append(encoded(actions, stages, dict(receipts.CONTRACT, **{key: value})))
        for log in changed:
            with self.assertRaises(RuntimeError): self.verify(actions, report, stages, log)

    def test_real_one_action_and_public_pane_provenance_cannot_be_replaced_by_baseline_logs(self):
        for mode in ('no-english', 'duplicate-action', 'hidden', 'wrong-pane', 'missing-provenance'):
            actions, report, stages = fixture()
            if mode == 'no-english':
                actions = [row for row in actions if not (row['stage'] == 'english' and row['status'].endswith('activation'))]
                reordinal(actions)
            elif mode == 'duplicate-action': pick(actions, 'english', 'after-activation')['fields']['activationCount'] = 2
            elif mode == 'hidden': pick(actions, 'english', 'before-activation')['fields']['targetHittable'] = False
            elif mode == 'wrong-pane': pick(actions, 'english', 'before-activation')['fields']['pane'] = 'settings'
            else: pick(actions, 'language', 'before-activation')['fields']['paneKnownPropertyMatches'] = []
            with self.subTest(mode=mode), self.assertRaises(RuntimeError): self.verify(actions, report, stages)

    def test_unavailable_os_route_remains_blocked_with_no_invented_baseline(self):
        for start, reason in ((None, 'language-control-unavailable'), ('settings', 'language-control-unavailable'),
                              ('parlor', 'selection-unavailable')):
            actions, report = enriched(start, reason)
            gate = self.verify(actions, report, [])
            self.assertEqual('BLOCKED', gate['status']); self.assertEqual(reason, gate['reason'])
            self.assertNotIn('observed_os_preference_representation', gate)
            with self.assertRaises(RuntimeError): self.verify(actions, report, fixture()[2])

    def test_incomplete_after_os_observation_never_becomes_a_retroactive_pass(self):
        actions, report, stages = fixture(); report['completed'] = False
        report['observations'] = report['observations'][:5]
        with self.assertRaises(RuntimeError): self.verify(actions, report, [])
        actions, report, stages = fixture()
        report['observations'][-1]['osDisposition'] = 'opened'
        with self.assertRaises(RuntimeError): self.verify(actions, report, stages)

    def test_full_matrix_calls_unchanged_settings_and_both_local_oracles(self):
        actions, report, stages = fixture()
        reports = dict(settings=settings_report(), whodunit=local_report('whodunit'), mafia=local_report('mafia'), os=report)
        for value in reports.values(): value['runToken'] = TOKEN
        with mock.patch.object(legacy, 'verify_settings', wraps=legacy.verify_settings) as settings, \
             mock.patch.object(legacy, 'verify_local', wraps=legacy.verify_local) as local:
            result = receipts.verify_probe(reports, runner.verify_legacy_settings_probe, encoded(actions, stages))
            settings.assert_called_once_with(reports['settings'], runner.verify_legacy_settings_probe)
            self.assertEqual([mock.call(reports['whodunit'], 'whodunit'), mock.call(reports['mafia'], 'mafia')], local.call_args_list)
        self.assertEqual('PASS', result['actual_settings_and_local_sessions_gate'])
        self.assertFalse(result['physical_device_evidence']); self.assertFalse(result['store_or_signing_evidence'])
        self.assertEqual('NOT_RUN', result['retained_multiplayer_host_gate'])

    def test_invalid_settings_local_scope_or_token_is_not_hidden_by_a_valid_os_branch(self):
        for scenario in ('settings', 'whodunit', 'mafia', 'missing', 'token'):
            actions, report, stages = fixture()
            reports = dict(settings=settings_report(), whodunit=local_report('whodunit'), mafia=local_report('mafia'), os=report)
            for value in reports.values(): value['runToken'] = TOKEN
            if scenario == 'settings':
                for event in reports['settings']['observations']:
                    if event['phase'] != 'before_main': alter_composition(event, settingsMounted=False, settingsDirection='absent')
            elif scenario in {'whodunit', 'mafia'}:
                alter_composition(reports[scenario]['observations'][3], sameController=False)
            elif scenario == 'missing': reports.pop('mafia')
            else: reports['mafia']['runToken'] = BOOTS[0]
            with self.subTest(scenario=scenario), self.assertRaises(RuntimeError):
                receipts.verify_probe(reports, runner.verify_legacy_settings_probe, encoded(actions, stages))


class V12ExactSourceContractTest(unittest.TestCase):
    def test_every_inherited_control_and_all_177_tests_and_finalizer_are_unchanged(self):
        for name in V11_REFERENCES:
            if name != 'run_dsc01_apphost_cycle.py':
                self.assertEqual((V11 / name).read_bytes(), (HERE / name).read_bytes(), name)
        inherited = sorted(V11.glob('test_*.py'))
        count = 0
        for path in inherited:
            self.assertEqual(path.read_bytes(), (HERE / path.name).read_bytes())
            count += sum(isinstance(node, ast.FunctionDef) and node.name.startswith('test_') for node in ast.walk(ast.parse(path.read_text())))
        self.assertEqual(177, count)
        marker = '        finally:\n            deferred = []'
        before = (V11 / 'run_dsc01_apphost_cycle.py').read_text(); after = Path(runner.__file__).read_text()
        self.assertEqual(before[before.index(marker):], after[after.index(marker):])
        self.assertEqual('7b1c9a2faa58be765205acfae064d203b896069fd382c1f2bfb1923ceac032c0',
                         hashlib.sha256(after[after.index(marker):].encode()).hexdigest())

    def test_exact_renderer_changes_only_bounded_os_region_and_restores_all_old_assertions(self):
        before = v10_sources.render_v10_ui((HERE / 'IOSAppLaunchUITests.swift.in').read_text())
        self.assertEqual(source.V11_RENDERED_UI_SHA, hashlib.sha256(before.encode()).hexdigest())
        after = source.render_v12_ui(before)
        self.assertEqual(before[:before.index(source.BEGIN)], after[:after.index(source.BEGIN)])
        self.assertEqual(before[before.index(source.END):], after[after.index(source.END):])
        restored = after
        for old, new in reversed(source.REPLACEMENTS):
            self.assertEqual(1, restored.count(new)); restored = restored.replace(new, old, 1)
        self.assertEqual(before, restored)
        for original in ('try assertOwned(explicit, explicit: "ar", previous: actualOSPreferences)',
                         'try assertSystem(restarted, expected: "en", previous: actualOSPreferences)',
                         'XCTAssertNotEqual(try string(restarted, "boot"), beforeBoot)',
                         'XCTAssertFalse(settings.alerts.firstMatch.exists', 'try finishOSInvestigation'):
            self.assertIn(original, after)
        self.assertEqual(before.count('syntheticLanguage(app, language:'), after.count('syntheticLanguage(app, language:'))

    def test_renderer_rejects_unreviewed_bytes_missing_and_ambiguous_boundaries(self):
        before = v10_sources.render_v10_ui((HERE / 'IOSAppLaunchUITests.swift.in').read_text())
        for changed in (before + '\n', before.replace(source.BEGIN, 'missing'), before + source.END):
            with self.assertRaises(RuntimeError): source.render_v12_ui(changed)
        # Deliberately supply a matching digest to exercise boundary guards too,
        # not only the stronger exact immutable-input hash gate.
        for changed in (before.replace(source.BEGIN, 'missing'), before + source.END,
                        before.replace(source.REPLACEMENTS[0][0], ''), before.replace(source.REPLACEMENTS[0][0], source.REPLACEMENTS[0][0] * 2)):
            with mock.patch.object(source, 'V11_RENDERED_UI_SHA', hashlib.sha256(changed.encode()).hexdigest()):
                with self.assertRaises(RuntimeError): source.render_v12_ui(changed)

    def test_native_contract_and_direct_stages_are_invoked_without_preference_or_ui_model_writes(self):
        helper = (HERE / 'DSC01OSPreferenceBaseline.swift.in').read_text()
        for literal in ('XCTAssertEqual(cases.count, 14)', 'XCTAssertEqual(owners.count, 6)', 'XCTAssertEqual(restores.count, 4)',
                        'dsc01OSUnownedEnglish(beforeMain) == value', 'fields["ownerPreviousPresent"] as? Bool == value.present',
                        'composition["settingsDirection"] as? String == "ltr"', 'try assertNative(snapshot)', 'data.count <= 2048'):
            self.assertIn(literal, helper)
        transformed = source.render_v12_ui(v10_sources.render_v10_ui((HERE / 'IOSAppLaunchUITests.swift.in').read_text()))
        region = transformed[transformed.index(source.BEGIN):transformed.index(source.END)]
        self.assertLess(region.index('try verifyOSPreferenceBaselineDecisionContract()'), region.index('try startScenario'))
        for stage in ('arabic', 'system', 'restart'):
            self.assertEqual(1, region.count('try recordOSPreferenceStage("' + stage + '"'))
        self.assertLess(region.index('try openSettings(app, language: "en")', region.index('let restarted =')),
                        region.index('try recordOSPreferenceStage("restart"'))
        self.assertLess(region.index('try recordOSPreferenceStage("restart"'), region.rindex('try finishOSInvestigation'))
        for forbidden in ('UserDefaults.', 'setPersistentDomain(', 'setObject(', '.coordinate(', 'UINavigationController',
                          'semanticContentAttribute =', 'AppleLocale', 'setenv('):
            self.assertNotIn(forbidden, helper)

    def test_explicit_replacement_and_truthful_action_annotations_are_wired_before_completion(self):
        code = Path(runner.__file__).read_text()
        self.assertIn('from v12_receipts import verify_probe', code)
        self.assertNotIn('from probe_validation import verify_probe', code)
        self.assertIn('render_v12_ui(render_v10_ui(', code)
        self.assertIn("FIXTURE / 'DSC01OSPreferenceBaseline.swift.in'", code)
        positions = [code.index(value) for value in ("receipt['dsc01_matrix'] = verify_probe",
            "receipt['v10_os_interaction_proof'] = verify_v10_receipts", "receipt['v11_pane_identity_proof'] = verify_v11_receipts",
            "receipt['probe_harness_status'] = 'observation_complete'")]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("'supplied_os_preference_oracle'] = 'v12-exact-post-choice-presence-array'", code)
        self.assertIn("'original_present_only_os_oracle_executed'", code)
        self.assertIn("'supplied_v12_os_phase_oracles_required'] = action_proof.pop(", code)
        self.assertEqual(5, len(runner.EXPECTED_TESTS))

    def test_every_new_and_consumed_v11_reference_is_full_path_hash_bound(self):
        controls = runner.control_files()
        self.assertEqual(171, len(controls)); self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual({V11 / name for name in V11_REFERENCES}, {path for path in controls if path.parent == V11})
        for name in ('v12_sources.py', 'DSC01OSPreferenceBaseline.swift.in', 'v12_receipts.py', 'test_v12_contract.py'):
            self.assertIn(HERE / name, controls)
        manifest = {item['path']: item['sha256'] for item in runner.control_manifest()}
        for path in controls:
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])


if __name__ == '__main__':
    unittest.main()
