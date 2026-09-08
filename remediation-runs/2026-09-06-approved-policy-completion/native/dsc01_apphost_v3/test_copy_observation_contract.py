"""Synthetic controls/receipt tests only. They never build, launch, or claim iOS evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import uuid

import copied_sources as copies
import probe_validation as validation
import run_dsc01_apphost_cycle as runner
from test_harness_contract import preferences, valid_report


def base_composition():
    result = {key: False for key in validation.COMPOSITION_BOOLEANS}
    result.update({key: 0 for key in validation.COMPOSITION_COUNTERS})
    result.update(settingsDirection='absent', surface='none', gameDirection='absent', publicPhase='none',
                  commandLanguage='none', commandStatus='none')
    return result


def settings_report():
    report = valid_report()
    report.update(schemaVersion=3, scenario='settings')
    for event in report['observations']:
        value = {} if event['phase'] == 'before_main' else base_composition()
        if value:
            value.update(settingsMounted=True,
                         settingsDirection='rtl' if event['preferences']['preferredLanguage'] == 'ar' else 'ltr')
        event.update(composition=json.dumps(value), osDisposition='not-investigated')
    return report


def local_report(game):
    report = dict(schemaVersion=3, scenario=game, runToken=str(uuid.uuid4()), completed=True, observations=[])
    boot = str(uuid.uuid4())
    def add(phase, value, pref):
        report['observations'].append(dict(ordinal=len(report['observations']) + 1, phase=phase,
            fixture='existing', boot=boot, preferences=pref,
            nativeDirection='unavailable' if phase == 'before_main' else 'force_rtl' if pref['preferredLanguage'] == 'ar' else 'force_ltr',
            controllerCreations=0 if phase == 'before_main' else 1,
            composition=json.dumps(value), osDisposition='not-investigated'))
    add('before_main', {}, preferences(app=['ar-EG', 'en'], language='ar'))
    value = base_composition()
    value.update(surface=game + '-local', publicPhase='public-intro' if game == 'whodunit' else 'role-assignment',
                 gameDirection='ltr', commandOrdinal=1, commandLanguage='en', commandStatus='applied')
    en = preferences('en', ['en'], 'en', ['ar-EG', 'en'])
    add('sample', copy.deepcopy(value), en)
    value.update({key: True for key in validation.CONTINUITY_BOOLEANS})
    add('sample', copy.deepcopy(value), en)
    for ordinal, language in ((2, 'ar'), (3, 'en'), (4, 'system')):
        pref = (preferences(app=['ar-EG', 'en'], language='ar') if language == 'system' else
                preferences(language, [language], language, ['ar-EG', 'en'], language))
        value.update(commandOrdinal=ordinal, commandLanguage=language,
                     gameDirection='rtl' if pref['preferredLanguage'] == 'ar' else 'ltr')
        add('sample', copy.deepcopy(value), pref)
        value.update(backgroundSinceCapture=ordinal - 1, foregroundSinceCapture=ordinal - 1,
                     backgroundCallbacks=ordinal - 1, foregroundCallbacks=ordinal, inactiveCallbacks=ordinal)
        add('sample', copy.deepcopy(value), pref)
    add('complete', copy.deepcopy(value), pref)
    return report


def os_report(blocked=True):
    report = dict(schemaVersion=3, scenario='os', runToken=str(uuid.uuid4()), completed=True, observations=[])
    boot = str(uuid.uuid4())
    def add(phase, pref, value, status, fixture='existing'):
        report['observations'].append(dict(ordinal=len(report['observations']) + 1, phase=phase,
            fixture=fixture, boot=boot, preferences=pref,
            nativeDirection='unavailable' if phase == 'before_main' else 'force_rtl' if pref['preferredLanguage'] == 'ar' else 'force_ltr',
            controllerCreations=0 if phase == 'before_main' else 1,
            composition=json.dumps(value), osDisposition=status))
    initial = preferences(app=['ar-EG', 'en'], language='ar')
    add('before_main', initial, {}, 'not-investigated')
    add('sample', initial, base_composition(), 'opened')
    if blocked:
        add('complete', initial, base_composition(), 'language-control-unavailable')
    else:
        os_en = preferences(app=['en'], language='en')
        add('sample', os_en, base_composition(), 'opened')
        ar = base_composition(); ar.update(settingsMounted=True, settingsDirection='rtl')
        add('sample', preferences('ar', ['ar'], 'ar', ['en'], 'ar'), ar, 'opened')
        en = base_composition(); en.update(settingsMounted=True, settingsDirection='ltr')
        add('sample', os_en, en, 'opened')
        boot = str(uuid.uuid4())
        add('before_main', os_en, {}, 'opened', 'continue')
        add('sample', os_en, base_composition(), 'opened', 'continue')
        add('complete', os_en, base_composition(), 'verified-english', 'continue')
    return report


class DirectObservationReceiptTest(unittest.TestCase):
    def test_valid_synthetic_settings_direction_receipt_is_only_a_schema_check(self):
        result = validation.verify_settings(settings_report(), runner.verify_legacy_settings_probe)
        self.assertTrue(result['direct_compose_direction_measurement'])
        self.assertFalse(result['physical_device_evidence'])

    def test_correct_native_direction_does_not_replace_direct_compose_observation(self):
        report = settings_report()
        for event in report['observations']:
            if event['phase'] != 'before_main':
                value = json.loads(event['composition']); value['settingsDirection'] = 'ltr'
                event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError):
            validation.verify_settings(report, runner.verify_legacy_settings_probe)

    def test_both_actual_game_phase_schemas_are_exercised_without_runtime_claim(self):
        for game in ('whodunit', 'mafia'):
            result = validation.verify_local(local_report(game), game)
            self.assertEqual(3, result['actual_background_foreground_cycles'])
            self.assertFalse(result['physical_lan_claim'])

    def test_equal_value_cannot_hide_a_replaced_controller_or_flow_or_state_reference(self):
        for key in ('sameController', 'sameCanonicalFlow', 'sameCanonicalReference', 'sameCanonicalValue'):
            report = local_report('mafia')
            event = report['observations'][4]
            value = json.loads(event['composition']); value[key] = False
            event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_a_disposed_session_cannot_be_rebased_to_a_passing_checkpoint(self):
        report = local_report('whodunit')
        event = report['observations'][5]
        value = json.loads(event['composition']); value['disposalsSinceCapture'] = 1
        event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'whodunit')

    def test_root_controller_count_and_locale_do_not_prove_session_retention(self):
        report = local_report('mafia')
        for event in report['observations'][1:]:
            value = json.loads(event['composition']); value['checkpointCaptured'] = False
            event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_process_recreation_is_not_same_process_continuity(self):
        report = local_report('mafia')
        report['observations'][-1]['boot'] = str(uuid.uuid4())
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_a_language_command_must_have_its_own_lifecycle_witness(self):
        report = local_report('whodunit')
        for event in report['observations'][1:]:
            value = json.loads(event['composition'])
            if value['commandOrdinal'] == 3:
                value['backgroundSinceCapture'] = 1
            event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'whodunit')

    def test_explicit_language_cannot_pass_when_locale_and_direction_both_stay_stale(self):
        for ordinal, stale in ((2, 'en'), (3, 'ar')):
            report = local_report('mafia')
            for event in report['observations'][1:]:
                value = json.loads(event['composition'])
                if value['commandOrdinal'] == ordinal:
                    event['preferences']['preferredLanguage'] = stale
                    event['nativeDirection'] = 'force_rtl' if stale == 'ar' else 'force_ltr'
                    value['gameDirection'] = 'rtl' if stale == 'ar' else 'ltr'
                    event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_system_cannot_accept_a_plausible_but_replaced_os_preference(self):
        report = local_report('mafia')
        for event in report['observations'][1:]:
            value = json.loads(event['composition'])
            if value['commandOrdinal'] == 4:
                event['preferences'] = preferences(app=['en'], language='en')
                event['nativeDirection'] = 'force_ltr'
                value['gameDirection'] = 'ltr'
                event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_private_or_unknown_data_is_rejected(self):
        for key, data in (('seed', 123), ('roles', []), ('canonicalState', {}), ('playerId', 'test')):
            report = local_report('mafia')
            event = report['observations'][3]
            value = json.loads(event['composition']); value[key] = data
            event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_kotlin_observation_cannot_run_before_original_app_initialization(self):
        report = local_report('mafia')
        report['observations'][0]['composition'] = json.dumps(base_composition())
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_failed_and_cancelled_synthetic_commands_are_not_success(self):
        for status in ('cancelled', 'failed'):
            report = local_report('mafia')
            event = report['observations'][4]
            value = json.loads(event['composition']); value['commandStatus'] = status
            event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_missing_os_language_ui_is_blocked_never_pass_or_not_applicable(self):
        result = validation.verify_os(os_report())
        self.assertEqual('BLOCKED', result['status'])

    def test_os_owned_preference_requires_restoration_and_restart(self):
        self.assertEqual('PASS', validation.verify_os(os_report(False))['status'])
        report = os_report(False)
        report['observations'][-3]['preferences'] = preferences()
        with self.assertRaises(RuntimeError): validation.verify_os(report)

    def test_os_gate_requires_an_accepted_public_settings_url_invocation(self):
        report = os_report()
        for event in report['observations']:
            if event['osDisposition'] == 'opened': event['osDisposition'] = 'opening'
        with self.assertRaises(RuntimeError): validation.verify_os(report)


class CopyBoundaryContractTest(unittest.TestCase):
    def test_protected_traversal_hidden_generated_and_nonbuild_inputs_are_excluded(self):
        for path in ('../secret', '/tmp/private', 'shared/x/../../../outside', '.git/config',
                     'composeApp/local.properties', 'config/upload.jks', 'config/key.p12',
                     'composeApp/build/old.bin', 'build-logic/.gradle/caches/x',
                     'docs/PRE_RELEASE_COMPATIBILITY.md', 'remediation-runs/control.py', None):
            self.assertFalse(copies.allowed_path(path), path)
        for path in ('gradle/verification-metadata.xml', 'gradle/wrapper/gradle-wrapper.jar',
                     'composeApp/src/commonMain/new-untracked.kt', 'iosApp/iosApp/Info.plist'):
            self.assertTrue(copies.allowed_path(path), path)

    def test_all_actual_source_anchors_apply_only_to_owned_copies(self):
        before = {path: copies.sha256(runner.ROOT / path) for path in copies.MODIFIED_KOTLIN}
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-synthetic-copy-test-') as directory:
            root = Path(directory).resolve()
            for path in copies.MODIFIED_KOTLIN:
                target = root / path; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((runner.ROOT / path).read_bytes())
            copies.instrument_kotlin(root)
            for path in copies.MODIFIED_KOTLIN:
                self.assertNotEqual(before[path], copies.sha256(root / path))
            self.assertEqual((copies.HERE / 'DSC01ComposeObservation.kt.in').read_text(), (root / copies.ADDITION).read_text())
            for actual in ('notifyBackgrounded()', 'notifyActive()', 'notifyInactive()'):
                self.assertEqual(1, (root / copies.MAIN).read_text().count('lifecycleCoordinator().' + actual))
            for path in (copies.WD, copies.MF):
                current = (root / path).read_text()
                self.assertIn('controller = session,\n        canonical = canonicalState,', current)
                self.assertIn('publicPhase = { canonicalState.value.phase.id }', current)
        self.assertEqual(before, {path: copies.sha256(runner.ROOT / path) for path in before})

    def test_missing_or_duplicate_anchor_is_not_a_dynamic_fallback(self):
        for text in ('missing', 'anchor anchor'):
            self.assertNotEqual(1, text.count('anchor'), 'Fixture must be missing or ambiguous')
            with self.assertRaises(RuntimeError): copies.replace_once(text, 'anchor', 'changed')

    def test_copy_refuses_unbound_manifest_and_nonempty_destination(self):
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-synthetic-boundary-') as directory:
            root = Path(directory).resolve(); source = root / 'source'; source.mkdir()
            target = root / 'copy'; target.mkdir()
            with self.assertRaises(RuntimeError): copies.create_source_copy(source, target, {})
            (target / 'preexisting').write_text('preserve')
            with self.assertRaises(RuntimeError): copies.create_source_copy(source, target, {})
            self.assertEqual('preserve', (target / 'preexisting').read_text())

    def test_original_build_outputs_never_enter_copy_only_cleanup_set(self):
        source = Path(runner.__file__).read_text()
        self.assertIn('links, errors, outputs = [], [], []', source)
        self.assertIn("phase.replace('__PARLOR_SOURCE_ROOT__', str(temp / 'copy'))", source)
        self.assertIn('unexpected_original_outputs_preserved', source)
        self.assertNotIn('pbx = pbx.replace(old_search, str(ROOT', source)

    def test_final_copy_attestation_rejects_input_drift_but_not_owned_outputs(self):
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-synthetic-final-inputs-') as directory:
            root = Path(directory).resolve()
            path = 'composeApp/src/commonMain/kotlin/synthetic.kt'
            source = root / path
            source.parent.mkdir(parents=True)
            source.write_text('original instrumented input')
            before = [dict(path=path, copied_sha256=copies.sha256(source))]
            for output in ('composeApp/build/generated/anything.kt', 'build-logic/build/cache.bin',
                           '.gradle/cache.bin', 'shared/core/.kotlin/synthetic.bin'):
                target = root / output; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text('recreatable output')
            self.assertTrue(copies.inspect_copied_inputs_after_build(root, before)['unchanged'])
            source.write_text('unexpected build modification')
            result = copies.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertNotEqual(result['files'][0]['expected_sha256'], result['files'][0]['observed_sha256'])
            source.unlink()
            self.assertEqual([path], copies.inspect_copied_inputs_after_build(root, before)['missing_build_inputs'])

    def test_final_copy_attestation_rejects_new_inputs_and_source_symlinks(self):
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-synthetic-final-inventory-') as directory:
            root = Path(directory).resolve()
            path = 'shared/core/src/commonMain/kotlin/synthetic.kt'
            source = root / path; source.parent.mkdir(parents=True)
            source.write_text('bound synthetic input')
            before = [dict(path=path, copied_sha256=copies.sha256(source))]
            addition = root / 'shared/core/src/commonMain/kotlin/new.kt'; addition.write_text('new source')
            result = copies.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertEqual(['shared/core/src/commonMain/kotlin/new.kt'], result['unexpected_build_inputs'])
            addition.unlink()
            source.unlink(); source.symlink_to(root / 'missing')
            result = copies.inspect_copied_inputs_after_build(root, before)
            self.assertFalse(result['unchanged'])
            self.assertEqual([path], result['unexpected_source_symlinks'])

    def test_after_build_identity_precedes_owned_copy_deletion_and_gates_pass(self):
        source = Path(runner.__file__).read_text()
        stopped = source.index("safe = stage('verify-workers-before-file-removal'")
        verified = source.index("stage('verify-copied-input-identity-after-workers-stop'")
        deleted = source.index("stage('remove-owned-copy-derived-data-home-temp'")
        self.assertLess(stopped, verified)
        self.assertLess(verified, deleted)
        self.assertIn("and receipt['copied_sources_unchanged'] else 'FAIL'", source)

    def test_public_setup_selectors_are_source_whitelisted_and_bounded(self):
        source = (copies.HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func tapPublicSetupPrefix(')
        end = source.index('@MainActor private func investigateActualOSPerAppLanguage(', start)
        body = source[start:end]
        self.assertIn('allowed.contains(prefix)', body)
        self.assertIn('XCTAssertLessThan(query.count, 16)', body)
        self.assertIn('for _ in 0..<14', body)
        self.assertNotIn('debugDescription', source)
        self.assertNotIn('app.textFields', source)  # real fields have copy-only stable public-seat tags
        self.assertIn('actual.typeText("Audit Player \\(index)")', source)

    def test_actual_os_api_used_not_private_settings_urls_or_global_defaults(self):
        swift = (copies.HERE / 'DSC01Probe.swift.in').read_text()
        self.assertIn('UIApplication.openSettingsURLString', swift)
        self.assertNotIn('App-Prefs', swift)
        self.assertEqual(1, swift.count('UserDefaults.standard.set('))
        self.assertIn('guard scenario == "settings"', swift)
        self.assertNotIn('removePersistentDomain', swift)
        bridge = (copies.HERE / 'DSC01InvocationBridge.kt.in').read_text()
        self.assertIn('.get<com.parlor.storage.settings.SettingsStore>()', bridge)
        self.assertIn('store.setLanguageOverride(', bridge)
        self.assertNotIn('NSUserDefaults', bridge)


if __name__ == '__main__':
    unittest.main()
