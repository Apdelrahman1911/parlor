"""Synthetic parser/source-control tests, not simulator or application evidence."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from unittest import mock

import run_dsc01_apphost_cycle as runner
import v8_receipts


HERE = Path(__file__).resolve().parent
V7 = HERE.parent / 'dsc01_apphost_v7'
V7_REFERENCES = (
    'secondary_fifo.py', 'test_secondary_fifo.py', 'copied_sources.py', 'bind_source.py',
    'DSC01ComposeObservation.kt.in', 'DSC01InvocationBridge.kt.in', 'DSC01UIKitObservation.kt.in',
    'DSC01NativeObservation.swift.in', 'probe_validation.py', 'copied-kotlin-phase.sh.in',
    'run_dsc01_apphost_cycle.py', 'IOSAppLaunchUITests.swift.in', 'DSC01CatalogSelection.swift.in',
    'DSC01Probe.swift.in', 'test_copy_observation_contract.py', 'test_harness_contract.py', 'test_catalog_contract.py',
)
PREDICATE_NAMES = (
    'settingsExplicitOwner', 'settingsSystemRelease', 'previousEnglishOwner', 'previousSystemRestore',
    'syntheticStoreApplied', 'localInitialPublicPhase', 'localLanguageDirection', 'foregroundCounters',
    'afterOSSystemEnglish', 'afterOSExplicitArabic', 'gamesSettingsUnmounted', 'actualSettingsDirection',
)


def rect(x, y, w, h):
    return dict(present=True, validFiniteRectangle=True, rectangle=[x, y, w, h])


def synthetic_start_rows():
    fields = dict(targetCountCapped16=1, target=rect(24, 720, 354, 52), enabled=True, hittable=True,
        viewport=rect(0, 0, 402, 874), overlayCountCapped16=1, overlay=rect(68, 134, 265, 49),
        foreground=True, alertPresent=False, keyboardPresent=False)
    raw = [('setup', dict(scenario='mafia', surface='mafia-local', publicPhase='setup'))]
    raw += [('search', dict(fields, window=1, index=i)) for i in range(1, 7)]
    raw += [('before-activation', dict(fields, normalizedPoint=[0.5, 0.5], plannedPoint=[201, 746],
                sampledCoordinatePoint=[201, 746], visibleInterior=rect(36, 732, 330, 28),
                activationTargetFrame=rect(24, 720, 354, 52))), ('after-activation', fields),
            ('successor', dict(targetCountCapped16=0, foreground=True, alertPresent=False, keyboardPresent=False,
                gameDirection='ltr', publicContext=dict(scenario='mafia', surface='mafia-local', publicPhase='role-assignment')))]
    return [dict(schemaVersion=1, ordinal=i, kind=kind, elapsedMilliseconds=i * 250, fields=copy.deepcopy(value))
            for i, (kind, value) in enumerate(raw, 1)]


def start_log(rows=None, contract=True):
    rows = synthetic_start_rows() if rows is None else rows
    lines = ['DSC01_MAFIA_START_METADATA ' + json.dumps(row) for row in rows]
    if contract:
        lines.insert(0, 'DSC01_MAFIA_START_GEOMETRY_CONTRACT ' + json.dumps(dict(schemaVersion=1, cases=16, passed=True)))
    return '\n'.join(lines)


def synthetic_os_rows():
    raw = [('ownership', 'PASS', dict(sameExpectedSimulator=True, ownedSyntheticDeviceName=True, debugSimulatorBuild=True))]
    raw += [('settingsRoot', 'PASS', {})]
    for stage in ('general', 'languageRegion'):
        raw += [(stage, 'before-activation', {}), (stage, 'after-activation', {}), (stage, 'PASS', {})]
    raw += [('initialEnglishPrimary', 'PASS', dict(englishPrimaryObserved=True, arabicObserved=False))]
    for stage in ('addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary'):
        raw += [(stage, 'before-activation', {}), (stage, 'after-activation', {})]
    raw += [('finalPreferredLanguages', 'PASS', dict(englishPrimaryObserved=True, arabicObserved=True,
        languageRegionTitleObserved=True, foreground=True, alertCountCapped16=0, keyboardPresent=False,
        languageRegionTitleHittable=True, englishPrimaryHittable=True, arabicPreferredRowObserved=True,
        addLanguageSuccessorHittableEnabled=True, searchFieldCountCapped16=0,
        englishPrimaryFrame=rect(20, 240, 362, 56), arabicPreferredRowFrame=rect(20, 296, 362, 56),
        addLanguageSuccessorFrame=rect(20, 352, 362, 52)))]
    return [dict(schemaVersion=1, ordinal=i, stage=stage, status=status, fields=fields)
            for i, (stage, status, fields) in enumerate(raw, 1)]


def os_log(rows=None):
    return '\n'.join('DSC01_OS_PREREQUISITE ' + json.dumps(row)
                     for row in (synthetic_os_rows() if rows is None else rows))


class V8ReceiptTest(unittest.TestCase):
    def test_complete_synthetic_receipts_only_exercise_parsers(self):
        result = v8_receipts.verify_v8_receipts(start_log() + '\n' + os_log())
        self.assertEqual(1, result['mafia_start']['physical_start_activations'])
        self.assertTrue(result['os_multilingual_prerequisite']['english_remains_primary'])
        self.assertIn('Original per-app', result['os_multilingual_prerequisite']['limitation'])

    def test_start_requires_executed_geometry_contract(self):
        for log in (start_log(contract=False), start_log().replace('"cases": 16', '"cases": 14'),
                    start_log() + '\n' + start_log().splitlines()[0]):
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(log)

    def test_start_activation_is_not_repeatable_or_an_incomplete_window(self):
        for change in ('repeat', 'partial', 'failed', 'out-of-order'):
            rows = synthetic_start_rows()
            if change == 'repeat': rows[2]['kind'] = 'before-activation'
            if change == 'partial': rows.pop(2)
            if change == 'failed': rows[-1]['kind'] = 'failed'
            if change == 'out-of-order': rows[2]['fields']['index'] = 6
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                v8_receipts.verify_start(start_log(rows))

    def test_start_setup_and_canonical_successor_are_both_mandatory(self):
        for location, key, value in ((0, 'publicPhase', 'role-assignment'), (-1, 'publicPhase', 'setup'),
                                     (-1, 'surface', 'whodunit-local'), (-1, 'scenario', 'os')):
            rows = synthetic_start_rows()
            target = rows[location]['fields'] if location == 0 else rows[location]['fields']['publicContext']
            target[key] = value
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))
        for key, value in (('targetCountCapped16', 1), ('gameDirection', 'rtl'),
                           ('foreground', False), ('alertPresent', True), ('keyboardPresent', True)):
            rows = synthetic_start_rows(); rows[-1]['fields'][key] = value
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))

    def test_start_disabled_hidden_occluded_and_nonfinite_fail(self):
        for key, value in (('enabled', False), ('hittable', False), ('foreground', False),
                           ('alertPresent', True), ('keyboardPresent', True), ('targetCountCapped16', 2)):
            rows = synthetic_start_rows(); rows[-3]['fields'][key] = value
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))
        for field in ('target', 'viewport', 'overlay', 'activationTargetFrame', 'visibleInterior'):
            rows = synthetic_start_rows(); rows[-3]['fields'][field]['rectangle'][0] = float('nan')
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))

    def test_start_last_window_and_dynamic_coordinate_drift_fail(self):
        rows = synthetic_start_rows(); rows[3]['fields']['target']['rectangle'][1] += 1
        with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))
        for field in ('sampledCoordinatePoint', 'plannedPoint', 'normalizedPoint'):
            rows = synthetic_start_rows(); rows[-3]['fields'][field][1] += 50
            with self.assertRaises(RuntimeError): v8_receipts.verify_start(start_log(rows))

    def test_public_diagnostic_bounds_are_strict(self):
        for prefix, parser in (('DSC01_MAFIA_START_METADATA ', v8_receipts.verify_start),
                                ('DSC01_OS_PREREQUISITE ', v8_receipts.verify_os_prerequisite)):
            with self.assertRaises(RuntimeError): parser(start_log() + '\n' + prefix + ' ' * 8193)

    def test_os_precondition_never_passes_from_missing_unknown_or_blocked_step(self):
        with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite('')
        for stage in ('ownership', 'initialEnglishPrimary', 'selectArabic', 'finalPreferredLanguages'):
            rows = synthetic_os_rows()
            next(row for row in rows if row['stage'] == stage)['status'] = 'BLOCKED'
            with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))
        rows = synthetic_os_rows(); rows[4]['stage'] = 'unknown'
        with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))

    def test_os_prerequisite_requires_owned_profile_and_english_primary_not_arabic_primary(self):
        for key in ('sameExpectedSimulator', 'ownedSyntheticDeviceName', 'debugSimulatorBuild'):
            rows = synthetic_os_rows(); rows[0]['fields'][key] = False
            with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))
        for key in ('englishPrimaryObserved', 'arabicObserved', 'languageRegionTitleObserved', 'foreground'):
            rows = synthetic_os_rows(); rows[-1]['fields'][key] = False
            with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))
        rows = synthetic_os_rows(); rows[-1]['fields']['alertCountCapped16'] = 1
        with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))

    def test_os_step_activation_cannot_repeat_or_disappear(self):
        for stage in ('general', 'languageRegion', 'addLanguage', 'searchArabic', 'selectArabic', 'retainEnglishPrimary'):
            rows = synthetic_os_rows()
            row = next(row for row in rows if row['stage'] == stage and row['status'] == 'after-activation')
            row['status'] = 'before-activation'
            with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))

    def test_arabic_picker_result_cannot_masquerade_as_exposed_preferred_list(self):
        for key, value in (('keyboardPresent', True), ('searchFieldCountCapped16', 1),
                           ('languageRegionTitleHittable', False), ('englishPrimaryHittable', False),
                           ('arabicPreferredRowObserved', False), ('addLanguageSuccessorHittableEnabled', False)):
            rows = synthetic_os_rows(); rows[-1]['fields'][key] = value
            with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))
        rows = synthetic_os_rows(); rows[-1]['fields']['arabicPreferredRowFrame']['rectangle'][1] = 600
        with self.assertRaises(RuntimeError): v8_receipts.verify_os_prerequisite(os_log(rows))


class V8SourceControlTest(unittest.TestCase):
    def test_start_keeps_one_activation_outside_readiness_and_proves_canonical_successor(self):
        source = (HERE / 'DSC01MafiaStartSelection.swift.in').read_text()
        ready = source[source.index('private func mafiaStartReadySample('):source.index('private func startActualMafiaOnce(')]
        self.assertNotIn('.tap()', ready)
        for text in ('for window in 1...14', 'for index in 1...6', 'if index > 1 { try catalogSamplingInterval() }'):
            self.assertIn(text, ready)
        self.assertEqual(1, source.count('coordinate.tap()'))
        self.assertNotIn('target.tap()', source)
        for text in ('dsc01CatalogNear(activationFrame, sampledFrame)', 'target.isHittable, target.isEnabled',
                     'abs(point.x - plan.point.x) <= 0.5', 'predicateName: .mafiaSetupPublic',
                     'predicateName: .mafiaStartSuccessor', 'value["publicPhase"] as? String == "role-assignment"',
                     'value["commandOrdinal"] as? Int == 0', 'target.targetCount == 0', 'viewport.maxY - 48',
                     'overlay.maxY + 8', 'ordinal < 96', 'encoded.count <= 4096', 'totalBytes <= 196608'):
            self.assertIn(text, source)
        for forbidden in ('Thread.sleep(', '.debugDescription', '.screenshot()', 'UserDefaults',
                          'MainViewControllerKt', 'session.submit(', 'sendAction(', 'ConfigureAndStart(', '.label', '.value'):
            self.assertNotIn(forbidden, source)

    def test_complete_original_matrix_changes_only_the_authorized_calls_and_wait_diagnostics(self):
        old = (V7 / 'IOSAppLaunchUITests.swift.in').read_text()
        new = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        for call in ('        try verifyMafiaStartGeometryContract()\n',
                     '        try prepareActualOSMultilingualPrerequisite(app, expectedSimulator: simulator)\n'):
            self.assertEqual(1, new.count(call)); new = new.replace(call, '')
        self.assertEqual(1, new.count('try startActualMafiaOnce(app)'))
        new = new.replace('try startActualMafiaOnce(app)',
                          'try tapPublicSetupPrefix(app, prefix: "Start the Mafia game with these settings")')
        for name in PREDICATE_NAMES:
            named = 'observeEventually(app, predicateName: .' + name + ')'
            self.assertEqual(1, new.count(named)); new = new.replace(named, 'observeEventually(app)')
        begin = '    @MainActor private func observeEventually('
        end = '    private func assertOwned('
        original_wait = old[old.index(begin):old.index(end)]
        current_wait = new[new.index(begin):new.index(end)]
        for needed in ('for attempt in 1...6', 'let snapshot = try observe(app)',
                       'if try predicate(snapshot) { return snapshot }', 'dsc01PublicContext(snapshot)',
                       'recordObservationFailure(predicateName', 'throw error', 'predicateName.rawValue'):
            self.assertIn(needed, current_wait)
        new = new.replace(current_wait, original_wait)
        self.assertEqual(old, new, 'No original oracle/predicate/action/timeout change may be hidden elsewhere')

    def test_original_app_observers_catalog_cleanup_and_oracle_controls_remain_byte_identical(self):
        changed = {'run_dsc01_apphost_cycle.py', 'IOSAppLaunchUITests.swift.in', 'test_catalog_contract.py'}
        for name in V7_REFERENCES:
            if name not in changed:
                self.assertEqual((V7 / name).read_bytes(), (HERE / name).read_bytes(), name)
        marker = '        finally:\n            deferred = []'
        original = (V7 / 'run_dsc01_apphost_cycle.py').read_text()
        current = (HERE / 'run_dsc01_apphost_cycle.py').read_text()
        self.assertEqual(original[original.index(marker):], current[current.index(marker):])

    def test_every_observation_predicate_is_named_without_logging_preferences_or_private_state(self):
        source = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        self.assertNotIn('observeEventually(app) {', source)
        public = (HERE / 'DSC01PublicDiagnostics.swift.in').read_text()
        for name in PREDICATE_NAMES + ('mafiaSetupPublic', 'mafiaStartSuccessor'):
            self.assertIn(name, public)
        for needed in ('contexts.count <= 6', 'encoded.count <= 4096', 'not-allowlisted',
                       '"scenario"', '"surface"', '"publicPhase"'):
            self.assertIn(needed, public)
        for forbidden in ('["now"]', '["preferences"]', 'randomSeed', 'fullRoleMap', '.debugDescription', '.screenshot()'):
            self.assertNotIn(forbidden, public)

    def test_os_prerequisite_is_supported_ui_only_fresh_owned_and_after_both_complete_games(self):
        source = (HERE / 'DSC01OSPrerequisite.swift.in').read_text()
        for required in ('#if DEBUG && targetEnvironment(simulator)', 'environment["SIMULATOR_UDID"] == expectedSimulator',
                         'environment["SIMULATOR_DEVICE_NAME"]?.hasPrefix("Parlor-Audit-parlor-audit-dsc01-apphost-")',
                         'XCUIApplication(bundleIdentifier: "com.apple.Preferences")', 'settings.launchArguments = []',
                         'defer { settings.terminate() }', 'settings.launch()', 'for attempt in 1...6',
                         'search.firstMatch.typeText("Arabic")', '["Use English", "Keep English"]',
                         '"English, iPhone Language"', 'osPrerequisiteBlock(', 'DSC01OSPrerequisiteBlocked.',
                         'osPreferredListIsUncovered(final)', '"arabicPreferredRowObserved"',
                         'englishFrame.maxY <= arabicFrame.minY + 1', 'arabicFrame.maxY <= addFrame.minY + 1',
                         'observed["searchFieldCountCapped16"] as? Int == 0',
                         'count == 1 else', 'ordinal < 96', 'encoded.count <= 8192', 'bytes <= 196608'):
            self.assertIn(required, source)
        for forbidden in ('UserDefaults', 'App-Prefs:', 'prefs:root', 'simctl', 'defaults write',
                          'setObject(', 'removePersistentDomain', '.debugDescription', '.screenshot()',
                          'performSelector(', 'Thread.sleep(', 'syntheticLanguage(', '"Use Arabic"'):
            self.assertNotIn(forbidden, source)
        matrix = (HERE / 'IOSAppLaunchUITests.swift.in').read_text()
        self.assertLess(matrix.index('try verifyActualLocalGame(app, game: "mafia")'),
                        matrix.index('try prepareActualOSMultilingualPrerequisite(app, expectedSimulator: simulator)'))
        self.assertLess(matrix.index('try prepareActualOSMultilingualPrerequisite(app, expectedSimulator: simulator)'),
                        matrix.index('try investigateActualOSPerAppLanguage(app)'))

    def test_new_controls_are_bound_and_appended_to_only_the_copied_xctest(self):
        controls = set(runner.control_files())
        source = Path(runner.__file__).read_text()
        for name in ('DSC01PublicDiagnostics.swift.in', 'DSC01MafiaStartSelection.swift.in',
                     'DSC01OSPrerequisite.swift.in', 'v8_receipts.py', 'test_v8_contract.py'):
            self.assertIn(HERE / name, controls)
            if name.endswith('.swift.in'): self.assertIn("(FIXTURE / '" + name + "').read_text()", source)
        self.assertIn("receipt['v8_public_interaction_proof'] = verify_v8_receipts", source)
        self.assertLess(source.index("receipt['xctest'] = verify_xctest"),
                        source.index("receipt['v8_public_interaction_proof'] = verify_v8_receipts"))
        self.assertIn("receipt['dsc01_matrix'] = verify_probe", source)

    def test_every_consumed_v7_reference_is_bound_by_full_path_and_bytes(self):
        controls = runner.control_files()
        self.assertEqual(len(controls), len(set(controls)))
        self.assertEqual({V7 / name for name in V7_REFERENCES}, {p for p in controls if p.parent == V7})
        manifest = {row['path']: row['sha256'] for row in runner.control_manifest()}
        for name in V7_REFERENCES:
            path = V7 / name
            self.assertIn(HERE / name, controls)
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), manifest[str(path.relative_to(runner.ROOT))])

    def test_each_v7_reference_changes_identity_without_changing_any_file(self):
        original = runner.digest
        before = runner.control_hash()
        for name in V7_REFERENCES:
            reference = V7 / name
            def changed_digest(path):
                value = original(path)
                return ('1' if value[0] == '0' else '0') + value[1:] if path == reference else value
            with self.subTest(reference=name), mock.patch.object(runner, 'digest', side_effect=changed_digest):
                self.assertNotEqual(before, runner.control_hash())


if __name__ == '__main__':
    unittest.main()
