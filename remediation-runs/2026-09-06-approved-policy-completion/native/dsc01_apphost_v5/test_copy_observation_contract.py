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
    report.update(schemaVersion=5, scenario='settings')
    for event in report['observations']:
        value = {} if event['phase'] == 'before_main' else base_composition()
        if value:
            value.update(settingsMounted=True,
                         settingsDirection='rtl' if event['preferences']['preferredLanguage'] == 'ar' else 'ltr')
        event.update(composition=json.dumps(value), osDisposition='not-investigated',
                     samplingWindow=0, samplingIndex=0, samplingElapsedMilliseconds=0)
    return report


def local_report(game):
    report = dict(schemaVersion=5, scenario=game, runToken=str(uuid.uuid4()), completed=True, observations=[])
    boot = str(uuid.uuid4())
    def add(phase, value, pref, window=0, index=0):
        report['observations'].append(dict(ordinal=len(report['observations']) + 1, phase=phase,
            fixture='existing', boot=boot, preferences=copy.deepcopy(pref),
            nativeDirection='unavailable' if phase == 'before_main' else 'force_rtl' if pref['preferredLanguage'] == 'ar' else 'force_ltr',
            controllerCreations=0 if phase == 'before_main' else 1,
            composition=json.dumps(value), osDisposition='not-investigated',
            samplingWindow=window, samplingIndex=index, samplingElapsedMilliseconds=index * 250))
    add('before_main', {}, preferences(app=['ar-EG', 'en'], language='ar'))
    en = preferences('en', ['en'], 'en', ['ar-EG', 'en'])
    settings = base_composition(); settings.update(settingsMounted=True, settingsDirection='ltr')
    add('sample', settings, en)
    value = base_composition()
    value.update(surface=game + '-local', publicPhase='public-intro' if game == 'whodunit' else 'role-assignment',
                 gameDirection='ltr')
    add('sample', copy.deepcopy(value), en)
    value.update({key: True for key in validation.CONTINUITY_BOOLEANS})
    add('sample', copy.deepcopy(value), en)
    for ordinal, language in ((0, 'en'), (1, 'ar'), (2, 'en'), (3, 'system')):
        pref = (preferences(app=['ar-EG', 'en'], language='ar') if language == 'system' else
                preferences(language, [language], language, ['ar-EG', 'en'], language))
        value.update(commandOrdinal=ordinal, commandLanguage='none' if ordinal == 0 else language,
                     commandStatus='none' if ordinal == 0 else 'applied',
                     gameDirection='rtl' if pref['preferredLanguage'] == 'ar' else 'ltr')
        if ordinal > 0:
            add('sample', copy.deepcopy(value), pref)
        value.update(backgroundSinceCapture=ordinal + 1, foregroundSinceCapture=ordinal + 1,
                     backgroundCallbacks=ordinal + 1, foregroundCallbacks=ordinal + 2, inactiveCallbacks=ordinal + 2)
        add('sample', copy.deepcopy(value), pref)
        for window in (ordinal * 2 + 1, ordinal * 2 + 2):
            for index in range(1, 7):
                add('window_sample', copy.deepcopy(value), pref, window=window, index=index)
    add('complete', copy.deepcopy(value), pref)
    return report


def os_report(blocked=True):
    report = dict(schemaVersion=5, scenario='os', runToken=str(uuid.uuid4()), completed=True, observations=[])
    boot = str(uuid.uuid4())
    def add(phase, pref, value, status, fixture='existing'):
        report['observations'].append(dict(ordinal=len(report['observations']) + 1, phase=phase,
            fixture=fixture, boot=boot, preferences=pref,
            nativeDirection='unavailable' if phase == 'before_main' else 'force_rtl' if pref['preferredLanguage'] == 'ar' else 'force_ltr',
            controllerCreations=0 if phase == 'before_main' else 1,
            composition=json.dumps(value), osDisposition=status,
            samplingWindow=0, samplingIndex=0, samplingElapsedMilliseconds=0))
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
            self.assertEqual(4, result['actual_background_foreground_cycles'])
            self.assertEqual(48, result['passive_samples'])
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
            if value['commandOrdinal'] == 2:
                value['backgroundSinceCapture'] = 1
            event['composition'] = json.dumps(value)
        with self.assertRaises(RuntimeError): validation.verify_local(report, 'whodunit')

    def test_explicit_language_cannot_pass_when_locale_and_direction_both_stay_stale(self):
        for ordinal, stale in ((1, 'en'), (2, 'ar')):
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
            if value['commandOrdinal'] == 3:
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

    def test_initial_language_must_use_actual_settings_with_zero_synthetic_commands(self):
        for mutation in ('synthetic', 'settings-absent'):
            report = local_report('mafia')
            event = report['observations'][1]
            value = json.loads(event['composition'])
            if mutation == 'synthetic':
                value.update(commandOrdinal=1, commandLanguage='en', commandStatus='applied')
            else:
                value.update(settingsMounted=False, settingsDirection='absent')
            event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_first_post_foreground_mismatch_cannot_be_erased_by_good_passive_windows(self):
        report = local_report('whodunit')
        report['observations'][4]['nativeDirection'] = 'force_rtl'
        with self.assertRaisesRegex(RuntimeError, 'diverged'):
            validation.verify_local(report, 'whodunit')

    def test_any_early_middle_or_second_window_divergence_fails_despite_good_final_sample(self):
        for window, index in ((1, 1), (1, 3), (2, 3), (5, 1), (6, 5)):
            with self.subTest(window=window, index=index):
                report = local_report('whodunit')
                event = next(event for event in report['observations'] if
                             event['samplingWindow'] == window and event['samplingIndex'] == index)
                event['nativeDirection'] = 'force_rtl'
                with self.assertRaisesRegex(RuntimeError, 'diverged'):
                    validation.verify_local(report, 'whodunit')

    def test_passive_windows_require_all_six_samples_and_original_order(self):
        for mutation in ('missing', 'index', 'window-order', 'instantaneous', 'new-boot'):
            with self.subTest(mutation=mutation):
                report = local_report('mafia')
                event = next(event for event in report['observations'] if event['samplingWindow'] == 1)
                if mutation == 'missing':
                    report['observations'].remove(event)
                    for index, row in enumerate(report['observations']): row['ordinal'] = index + 1
                elif mutation == 'index': event['samplingIndex'] = 6
                elif mutation == 'window-order': event['samplingWindow'] = 2
                elif mutation == 'instantaneous': event['samplingElapsedMilliseconds'] = 1
                else: event['boot'] = str(uuid.uuid4())
                with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_every_passive_sample_requires_own_lifecycle_and_canonical_reference(self):
        for key, invalid in (('sameController', False), ('sameCanonicalFlow', False),
                             ('sameCanonicalReference', False), ('foregroundSinceCapture', 0)):
            report = local_report('mafia')
            event = next(event for event in report['observations'] if event['samplingWindow'] == 4)
            value = json.loads(event['composition']); value[key] = invalid
            event['composition'] = json.dumps(value)
            with self.assertRaises(RuntimeError): validation.verify_local(report, 'mafia')

    def test_window_only_schema_is_not_accepted_in_settings_or_nonwindow_events(self):
        for scenario in ('settings', 'nonwindow'):
            report = settings_report() if scenario == 'settings' else local_report('mafia')
            event = report['observations'][1]
            event.update(samplingWindow=1, samplingIndex=1, samplingElapsedMilliseconds=250)
            if scenario == 'settings': event['phase'] = 'window_sample'
            with self.assertRaises(RuntimeError): validation.common(report, report['scenario'])

    def test_six_full_public_snapshot_rows_fit_the_existing_accessibility_budget(self):
        report = local_report('mafia')
        events = [event for event in report['observations'] if event['samplingWindow'] == 8]
        snapshots = [dict(sequence=event['ordinal'], boot=event['boot'],
                          controllerCreations=event['controllerCreations'], nativeDirection=event['nativeDirection'],
                          now=event['preferences'], composition=json.loads(event['composition']),
                          index=event['samplingIndex'], elapsedMilliseconds=event['samplingElapsedMilliseconds'])
                     for event in events]
        last = events[-1]
        payload = dict(sequence=last['ordinal'], boot=last['boot'], completed=False,
                       beforeMain=report['observations'][0]['preferences'], now=last['preferences'],
                       nativeDirection=last['nativeDirection'], controllerCreations=1,
                       composition=json.loads(last['composition']), scenario='mafia', osDisposition='not-investigated',
                       sampling=dict(ordinal=8, status='complete', samples=snapshots))
        self.assertLessEqual(len(json.dumps(payload, separators=(',', ':')).encode()), 8192)
        self.assertEqual(6, len(snapshots))

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
    def test_local_checkpoint_uses_real_settings_then_home_before_synthetic_language_calls(self):
        source = (copies.HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func verifyActualLocalGame(')
        capture = source.index('app.buttons["parlor-dsc01-capture"].tap()', start)
        before = source[start:capture]
        self.assertIn('try openSettings(app, language: systemLanguage)', before)
        self.assertIn('try tap(app, label: "English")', before)
        self.assertIn('try openGames(app, language: "en")', before)
        self.assertNotIn('syntheticLanguage(', before)
        loop = source.index('for language in ["ar", "en", "system"]', capture)
        self.assertIn('try verifyActualForeground(', source[capture:loop])

    def test_foreground_collects_both_windows_before_any_direction_assertion(self):
        source = (copies.HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func verifyActualForeground(')
        end = source.index('@MainActor private func collectPassiveWindow(', start)
        body = source[start:end]
        self.assertLess(body.index('XCUIDevice.shared.press(.home)'), body.index('let resumed ='))
        self.assertLess(body.index('let resumed ='), body.index('for _ in 0..<2'))
        self.assertIn('var snapshots = [resumed]', body)
        self.assertIn('XCTAssertEqual(snapshots.count, 13)', body)
        self.assertLess(body.index('for _ in 0..<2'), body.index('try assertContinuity('))
        self.assertIn('XCTAssertGreaterThan(try XCTUnwrap(value["foregroundSinceCapture"]', body)

    def test_passive_samples_are_durable_nonblocking_and_do_not_publish_between_samples(self):
        swift = (copies.HERE / 'DSC01Probe.swift.in').read_text()
        start = swift.index('private func scheduleStabilitySample()')
        end = swift.index('func complete()', start)
        body = swift[start:end]
        self.assertIn('DispatchQueue.main.asyncAfter(deadline: .now() + 0.25)', body)
        self.assertIn('samplingRows.count < 6', body)
        self.assertIn('elapsed <= 10000', body)
        self.assertIn('appendObservation(phase: "window_sample", publish: false', body)
        self.assertEqual(1, body.count('try publishObservation(observation)'))
        self.assertLess(body.index('if samplingRows.count == 6'), body.index('try publishObservation(observation)'))
        for forbidden in ('Thread.sleep(', 'usleep(', 'UserDefaults', 'semanticContentAttribute ='):
            self.assertNotIn(forbidden, body)
        source = (copies.HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func collectPassiveWindow(')
        end = source.index('private func assertContinuity(', start)
        body = source[start:end]
        self.assertIn('XCTWaiter.wait(for: [finished], timeout: 15)', body)
        self.assertNotIn('try observe(', body)
        self.assertNotIn('"parlor-dsc01-observe"', body)
        self.assertIn('try readDisplayedObservation(app)', body)

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

    def test_name_entry_uses_real_ime_next_done_without_fling_or_backing_state_bypass(self):
        source = (copies.HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func enterActualPlayerNames(')
        end = source.index('@MainActor private func tapPublicSetupPrefix(', start)
        body = source[start:end]
        self.assertIn('if index == 1 { actual.tap() }', body)
        self.assertEqual(1, body.count('actual.typeText("Audit Player \\(index)")'))
        self.assertIn('actual.value as? String == "Audit Player \\(index)"', body)
        self.assertIn('actual.typeText("\\n")', body)
        self.assertIn('XCTWaiter.wait(for: [inserted], timeout: 10)', body)
        self.assertIn('!app.keyboards.firstMatch.exists', body)
        for forbidden in ('swipeUp', 'hasKeyboardFocus', 'value(forKey:', 'app.typeText(',
                          'onValueChange', 'setText', 'UserDefaults', 'sleep(', 'Thread.sleep'):
            self.assertNotIn(forbidden, body)

    def test_original_name_fields_still_own_next_and_final_done(self):
        for path in copies.NAME_FIELDS:
            source = (runner.ROOT / path).read_text()
            self.assertIn('imeAction = if (isLast) ImeAction.Done else ImeAction.Next', source)
            self.assertIn('focusManager.clearFocus()', source)
            self.assertIn('keyboardController?.hide()', source)
            self.assertNotIn('onNext =', source)

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
