"""Pure receipt validators and disposable shell stubs; no real builds/devices."""
import ast
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid
import xml.etree.ElementTree as ET

import run_dsc01_apphost_cycle as runner


def preferences(setting='system', app=None, installed='none', previous=None, language='en'):
    return dict(setting=setting, appLanguages=app or [], hasAppOverride=bool(app),
                ownerPresent=installed!='none', ownerInstalled=installed,
                ownerPreviousPresent=bool(previous), ownerPrevious=previous or [], preferredLanguage=language)


def valid_report():
    rows=[]
    boots=[str(uuid.uuid4()) for _ in range(4)]
    baseline=preferences()
    ar=preferences('ar',['ar'],'ar',language='ar')
    os_ar=preferences(app=['ar-EG','en'],language='ar')
    en=preferences('en',['en'],'en',['ar-EG','en'])
    def add(index, phase, pref):
        rows.append(dict(ordinal=len(rows)+1, phase=phase, fixture=['fresh','continue','previous-ar','continue'][index],
                         boot=boots[index], preferences=copy.deepcopy(pref),
                         nativeDirection='unavailable' if phase=='before_main' else 'force_rtl' if pref['preferredLanguage']=='ar' else 'force_ltr',
                         controllerCreations=0 if phase=='before_main' else 1))
    for index, first, samples in [(0,baseline,[baseline,ar]), (1,ar,[ar,baseline]),
                                  (2,os_ar,[os_ar,en]), (3,en,[en,os_ar])]:
        add(index,'before_main',first)
        for value in samples: add(index,'sample',value)
    add(3,'complete',os_ar); add(3,'sample',os_ar)
    return dict(schemaVersion=1,runToken=str(uuid.uuid4()),completed=True,observations=rows)


class ReceiptContractTest(unittest.TestCase):
    def test_complete_synthetic_schema_is_accepted_not_runtime_evidence(self):
        proof=runner.verify_legacy_settings_probe(valid_report())
        self.assertEqual(4,proof['actual_process_boots'])
        self.assertFalse(proof['active_session_retention_test'])
        self.assertFalse(proof['physical_device_evidence'])

    def test_missing_no_disposal_precondition_is_rejected(self):
        report=valid_report(); report['observations'][3]['preferences']['ownerPresent']=False
        with self.assertRaises(RuntimeError): runner.verify_legacy_settings_probe(report)

    def test_changed_boot_identity_cannot_impersonate_restart(self):
        report=valid_report(); old=report['observations'][3]['boot']; initial=report['observations'][0]['boot']
        for event in report['observations']:
            if event['boot']==old: event['boot']=initial
        with self.assertRaises(RuntimeError): runner.verify_legacy_settings_probe(report)

    def test_unknown_data_is_not_exported_as_trusted_observation(self):
        report=valid_report(); report['observations'][0]['preferences']['roleMap']={}
        with self.assertRaises(RuntimeError): runner.verify_legacy_settings_probe(report)

    def test_missing_system_restoration_or_wrong_native_direction_fails(self):
        for mutation in ('system','direction'):
            report=valid_report()
            for event in report['observations']:
                if event['fixture']=='continue' and event['preferences']['setting']=='system':
                    if mutation=='system': event['preferences']['setting']='ar'
                    else: event['nativeDirection']='force_ltr'
            with self.assertRaises(RuntimeError): runner.verify_legacy_settings_probe(report)

    def test_test_summary_with_skip_cannot_pass(self):
        with self.assertRaises(RuntimeError):
            runner.verify_xctest(dict(result='Passed',totalTestCount=1,passedTests=0,
                                      failedTests=0,skippedTests=1,expectedFailures=0),{},'synthetic')


class RuntimeStatusContractTest(unittest.TestCase):
    """Exercise the real runner's exception statements with synthetic evidence.

    These tests do not launch main(), Gradle, Xcode, a simulator, or an app.
    AST checks bind the isolated exception replay to the actual protected call.
    """
    def outer_runtime_guard(self):
        tree = ast.parse(Path(runner.__file__).read_text())
        main = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'main')
        def writes_error(handler):
            return any(isinstance(node, ast.Assign) and any(
                isinstance(target, ast.Subscript) and isinstance(target.value, ast.Name) and
                target.value.id == 'receipt' and isinstance(target.slice, ast.Constant) and target.slice.value == 'error'
                for target in node.targets) for node in ast.walk(handler))
        pairs = [(node, handler) for node in ast.walk(main) if isinstance(node, ast.Try)
                 for handler in node.handlers if writes_error(handler)]
        self.assertEqual(1, len(pairs), 'Locate the actual unique runtime-error receipt handler')
        return pairs[0]

    def apply_actual_error_handler(self, status, error):
        _, handler = self.outer_runtime_guard()
        receipt = dict(runtime_evidence_status=status)
        fragment = ast.Module(body=copy.deepcopy(handler.body), type_ignores=[])
        context = {'receipt': receipt, 'error': error}
        exec(compile(ast.fix_missing_locations(fragment), 'actual-runner-error-handler', 'exec'), context)
        return receipt

    def test_executed_failed_xctest_summary_cannot_remain_not_run(self):
        # Synthetic failing summary reproduces v1's verifier-exception path;
        # it is NOT evidence that a real XCTest executed in this unit test.
        summary = dict(result='Failed', totalTestCount=1, passedTests=0,
                       failedTests=1, skippedTests=0, expectedFailures=0)
        try:
            runner.verify_xctest(summary, {}, 'synthetic')
        except RuntimeError as error:
            receipt = self.apply_actual_error_handler('RUNNING', error)
        else:
            self.fail('The original failed XCTest summary must still be rejected')
        self.assertEqual('FAIL', receipt['runtime_evidence_status'])
        self.assertEqual('RuntimeError', receipt['error']['type'])

    def test_preflight_failure_is_still_not_run(self):
        receipt = self.apply_actual_error_handler('NOT_RUN', RuntimeError('synthetic preflight failure'))
        self.assertEqual('NOT_RUN', receipt['runtime_evidence_status'])
        self.assertEqual('RuntimeError', receipt['error']['type'])

    def test_later_evidence_failure_cannot_leave_pass(self):
        receipt = self.apply_actual_error_handler('PASS', RuntimeError('synthetic late evidence failure'))
        self.assertEqual('FAIL', receipt['runtime_evidence_status'])

    def test_running_is_persisted_before_the_guarded_xcode_call(self):
        guard, _ = self.outer_runtime_guard()
        nodes = list(ast.walk(guard))
        builds = [node for node in nodes if isinstance(node, ast.Call) and
                  isinstance(node.func, ast.Name) and node.func.id == 'command' and node.args and
                  isinstance(node.args[0], ast.List) and node.args[0].elts and
                  isinstance(node.args[0].elts[0], ast.Constant) and node.args[0].elts[0].value == 'xcodebuild']
        self.assertEqual(1, len(builds), 'Bind test to the one actual Xcode invocation')
        starts = [node for node in nodes if isinstance(node, ast.Assign) and
                  isinstance(node.value, ast.Constant) and node.value.value == 'RUNNING' and any(
                      isinstance(target, ast.Subscript) and isinstance(target.value, ast.Name) and
                      target.value.id == 'receipt' and isinstance(target.slice, ast.Constant) and
                      target.slice.value == 'runtime_evidence_status' for target in node.targets)]
        self.assertEqual(1, len(starts))
        self.assertLess(starts[0].lineno, builds[0].lineno)
        saves = [node for node in nodes if isinstance(node, ast.Call) and
                 isinstance(node.func, ast.Name) and node.func.id == 'save' and
                 starts[0].lineno < node.lineno < builds[0].lineno]
        self.assertTrue(saves, 'Persist attempted/running status before any Xcode failure is possible')


class SelectorSourceContractTest(unittest.TestCase):
    def expected_labels(self):
        labels = set()
        tabs = []
        for locale in ('values', 'values-ar'):
            path = runner.ROOT / 'composeApp/src/commonMain/composeResources' / locale / 'strings.xml'
            strings = {node.attrib['name']: node.text for node in ET.parse(path).getroot() if node.tag == 'string'}
            description = strings['navigation_settings_description']
            visible = strings['navigation_settings_label'].upper()
            merged = description + ', ' + visible
            tabs.append(merged)
            labels.update([merged, description, visible, strings['settings_language_label'].upper(),
                           strings['settings_language_system'], strings['settings_language_english'],
                           strings['settings_language_arabic'], strings['navigation_games_description'] + ', ' +
                           strings['navigation_games_label'].upper()])
        return tabs, labels

    def test_exact_tab_selector_matches_reviewed_pinned_merge_and_actual_resources(self):
        source = (Path(__file__).parent / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func openSettings(')
        end = source.index('@MainActor private func assertSettingsStrings(', start)
        for expected in self.expected_labels()[0]:
            self.assertIn('"' + expected + '"', source[start:end])
        self.assertIn('NSPredicate(format: "label == %@", label)', source)
        self.assertNotIn('label CONTAINS', source)
        # Public setup has a separately bounded, exact-whitelisted prefix helper.
        # Settings navigation itself must retain the original exact-only policy.
        self.assertNotIn('label BEGINSWITH', source[start:end])

    def test_games_return_uses_original_resources_and_the_same_actual_tab_bar(self):
        source = (Path(__file__).parent / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func openGames(')
        end = source.index('@MainActor private func assertSettingsStrings(', start)
        for locale in ('values', 'values-ar'):
            path = runner.ROOT / 'composeApp/src/commonMain/composeResources' / locale / 'strings.xml'
            strings = {node.attrib['name']: node.text for node in ET.parse(path).getroot() if node.tag == 'string'}
            expected = strings['navigation_games_description'] + ', ' + strings['navigation_games_label'].upper()
            self.assertIn('"' + expected + '"', source[start:end])
        navigation = (runner.ROOT / 'composeApp/src/commonMain/kotlin/com/parlor/app/AppNavigationHost.kt').read_text()
        self.assertIn('label = stringResource(Res.string.navigation_games_label)', navigation)
        self.assertIn('contentDescription = stringResource(Res.string.navigation_games_description)', navigation)
        self.assertIn('ParlorBottomTabBar(', navigation)

    def test_diagnostics_are_exact_whitelisted_bounded_and_never_a_fallback_tap(self):
        source = (Path(__file__).parent / 'IOSAppLaunchUITests.swift.in').read_text()
        start = source.index('@MainActor private func recordSelectorDiagnostics(')
        end = source.index('@MainActor private func observe(', start)
        body = source[start:end]
        literal = body.split('let labels = ', 1)[1].split('\n        let observations:', 1)[0].strip()
        self.assertEqual(self.expected_labels()[1], set(json.loads(literal)))
        self.assertIn('NSPredicate(format: "label == %@", known)', body)
        self.assertIn('count < 32', body)
        self.assertIn('.prefix(4)', body)
        self.assertIn('data.count <= 8192', body)
        self.assertNotIn('.tap(', body)
        self.assertNotIn('debugDescription', body)
        self.assertNotIn('.label', body)
        self.assertNotIn('.value', body)


class CopiedPhaseContractTest(unittest.TestCase):
    def run_fixture(self,build=0,stop=0,normalize=0,missing_root=False):
        with tempfile.TemporaryDirectory(prefix='parlor-dsc01-phase-test-') as temporary:
            path=Path(temporary).resolve(); commands=path/'commands.txt'; receipt=path/'stop-receipt.txt'
            wrapper=path/'gradlew'
            wrapper.write_text('#!/bin/sh\nif [ "$1" = "--stop" ]; then\n printf "stop\\n" >> "$COMMAND_LOG"\n exit "$STOP_EXIT"\nfi\nprintf "build\\n" >> "$COMMAND_LOG"\nexit "$BUILD_EXIT"\n')
            wrapper.chmod(0o700)
            normalizer=path/'scripts/release/normalize_embedded_apple_framework.sh'
            normalizer.parent.mkdir(parents=True)
            normalizer.write_text('#!/bin/sh\nprintf "normalize\\n" >> "$COMMAND_LOG"\nexit "$NORMALIZE_EXIT"\n')
            normalizer.chmod(0o700)
            # Stale output cannot turn a failed build into a successful phase.
            (path/'stale-framework').write_text('synthetic stale artifact')
            phase=(Path(__file__).parent/'copied-kotlin-phase.sh.in').read_text()
            phase=phase.replace('__PARLOR_SOURCE_ROOT__',str(path/'absent' if missing_root else path))
            phase=phase.replace('__PARLOR_STOP_RECEIPT__',str(receipt))
            env={'PATH':'/usr/bin:/bin','BUILD_EXIT':str(build),'STOP_EXIT':str(stop),
                 'NORMALIZE_EXIT':str(normalize),'COMMAND_LOG':str(commands),
                 'TARGET_BUILD_DIR':str(path),'FRAMEWORKS_FOLDER_PATH':'stale-framework'}
            result=subprocess.run(['/bin/sh','-c',phase],env=env,capture_output=True,text=True,timeout=10)
            return result.returncode, commands.read_text() if commands.exists() else '', receipt.read_text() if receipt.exists() else ''

    def test_failed_build_with_stale_output_stops_and_never_normalizes(self):
        self.assertEqual((47,'build\nstop\n','build_exit=47\nstop_exit=0\n'),self.run_fixture(build=47))

    def test_failed_directory_change_never_builds_or_normalizes(self):
        self.assertEqual((1,'',''),self.run_fixture(missing_root=True))

    def test_failed_stop_cannot_normalize_or_look_green(self):
        self.assertEqual((23,'build\nstop\n','build_exit=0\nstop_exit=23\n'),self.run_fixture(stop=23))

    def test_normalization_failure_propagates_after_immediate_stop(self):
        self.assertEqual((19,'build\nstop\nnormalize\n','build_exit=0\nstop_exit=0\n'),self.run_fixture(normalize=19))

    def test_successful_phase_stops_before_normalizing(self):
        self.assertEqual((0,'build\nstop\nnormalize\n','build_exit=0\nstop_exit=0\n'),self.run_fixture())


if __name__=='__main__':
    unittest.main()
