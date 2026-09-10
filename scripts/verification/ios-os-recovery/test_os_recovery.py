"""No-build control tests. Synthetic receipts/copies never constitute OS evidence."""
import ast
import copy
import importlib.util
import io
import json
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

HERE = Path(__file__).resolve().parent
ALIASES = ('copied_sources', 'artifact_inventory', 'run_ios_readiness', 'probe_validation',
           'simulator_signing', 'os_preference_receipts', 'public_interaction_receipts')
BEFORE_ALIASES = {name: sys.modules.get(name) for name in ALIASES}
BEFORE_PATH = list(sys.path)
with mock.patch('subprocess.Popen', side_effect=AssertionError('Import launched a worker')), \
        mock.patch('subprocess.run', side_effect=AssertionError('Import invoked a command')), \
        mock.patch('pathlib.Path.write_text', side_effect=AssertionError('Import wrote a file')):
    spec = importlib.util.spec_from_file_location('tested_os_recovery_probe', HERE / 'os_recovery_probe.py')
    driver = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(driver)
receipts = driver.receipts
TOKEN = 'deadbeef-1234-5678-9000-000000000001'
DEVICE = 'eeeeeeee-1234-5678-9000-000000000001'


def stage_marker(stage):
    return 'PARLOR_OS_RECOVERY_STAGE ' + json.dumps(dict(schemaVersion=1, stage=stage,
        simulator=DEVICE, runToken=TOKEN))


def xctest(stage='bootstrap', passed=True):
    device = dict(deviceId=DEVICE, architecture='arm64', platform='iOS Simulator')
    counts = dict(passedTests=int(passed), failedTests=int(not passed), skippedTests=0, expectedFailures=0)
    result = 'Passed' if passed else 'Failed'
    failure = dict(failureText='Test crashed with signal kill.', targetName='iosAppUITests',
        testIdentifierString=receipts.METHODS[stage], testName=receipts.METHODS[stage].split('/')[1])
    summary = dict(result=result, totalTestCount=1, **counts, testFailures=[] if passed else [failure],
                   devicesAndConfigurations=[dict(device=copy.deepcopy(device), **counts)])
    case = dict(nodeType='Test Case', nodeIdentifier=receipts.METHODS[stage], result=result, children=[])
    tests = dict(devices=[device], testNodes=[dict(nodeType='UI test bundle', result=result, children=[case])])
    return summary, tests


def completed_stage(code=0):
    return dict(attempted=True, status='PASS' if code == 0 else 'FAIL', exit_code=code,
                command_completed=True, preserved=True, stop_exit_code=0, errors=[],
                extraction_exit_codes=dict(summary=0, tests=0))


def lane(root):
    value = object.__new__(driver.RecoveryLane)  # No native preparation or ownership is impersonated.
    value.temporary, value.destination = root / 'temporary', root / 'evidence'
    value.temporary.mkdir(); value.destination.mkdir()
    value.uuid, value.mode = DEVICE, 'adhoc'
    value.environment, value.signing = dict(SDK_NAME='iphonesimulator26.2'), dict(CODE_SIGN_IDENTITY='-')
    value.binding, value.approved = root / 'binding.json', 'a' * 64
    value.receipt = dict(bootstrap_status='NOT_RUN', postbuild_evidence_preserved=False,
        os_recovery_stages={k: dict(attempted=False, status='NOT_RUN') for k in driver.STAGES},
        source_before=dict(source='frozen'), runtime_evidence_status='NOT_RUN', provenance_status='NOT_RUN',
        notice_package_status='NOT_RUN')
    value.watched_outputs, value.errors, value.copy_manifest = [], [], []
    value.owner = SimpleNamespace(stop=mock.Mock(), secondary_errors={})
    value.lifecycle, value.save, value.stop_gradle = mock.Mock(), mock.Mock(), mock.Mock()
    return value


def image_fixture():
    report = driver.functional('test_os_preference_receipts').fixture()[0]
    native = driver.functional('test_native_readiness')
    framework = native.framework_observation()
    built = dict(required_images=native.IMAGES[:], images=[dict(path=p, resolved_path=p,
        bytes=framework['file_bytes'] if p == native.artifacts.FRAMEWORK_PATH else 12,
        sha256=framework['file_sha256'] if p == native.artifacts.FRAMEWORK_PATH else 'b' * 64)
        for p in native.IMAGES])
    artifacts = {'installed': dict(kind='compose-framework', origin='installed-app',
        relative_path=framework['path'], bytes=framework['file_bytes'], sha256=framework['file_sha256'],
        uuid=framework['image_uuid'])}
    records = [dict(schema_version=1, kind='os-recovery-process-images', scenario='os', signing_mode='adhoc',
        boot_ordinal=n, process_boot=start['boot'], process_id=1000 + n, run_token=report['runToken'],
        simulator_uuid=DEVICE, loaded_app_images=native.IMAGES[:],
        loaded_compose_framework=copy.deepcopy(framework), loader_environment=native.loader_environment())
        for n, start in enumerate((e for e in report['observations'] if e['phase'] == 'before_main'), 1)]
    return report, records, built, artifacts


class RecoveryControlsTests(unittest.TestCase):
    def setUp(self):
        self.addCleanup(mock.patch.stopall)
        mock.patch('subprocess.Popen', side_effect=AssertionError('No native commands in control tests')).start()
        mock.patch('subprocess.run', side_effect=AssertionError('No commands in control tests')).start()

    def test_real_private_imports_keep_exact_origins_and_do_not_rebind_shared_aliases(self):
        self.assertEqual(sys.path, BEFORE_PATH)
        self.assertEqual({name: sys.modules.get(name) for name in ALIASES}, BEFORE_ALIASES)
        self.assertEqual(Path(driver.normal.__file__), driver.NORMAL / 'run_normal_ios_launch.py')
        self.assertEqual(Path(driver.normal.create_source_copy.__globals__['__file__']), driver.SUPPORT / 'copied_sources.py')
        self.assertEqual(Path(driver.normal.inventory_bundle.__globals__['__file__']), driver.SUPPORT / 'artifact_inventory.py')
        self.assertEqual(Path(driver.fcopy.__file__), driver.FUNCTIONAL / 'copied_sources.py')
        self.assertEqual(Path(driver.fcopy.instrument_l08_kotlin.__globals__['__file__']),
                         driver.FUNCTIONAL / 'l08_functional_copy.py')
        self.assertEqual(Path(driver.functional('os_preference_receipts').legacy.__file__),
                         driver.FUNCTIONAL / 'probe_validation.py')
        self.assertIs(driver.normal.inspect_copied_inputs_after_build, driver.fcopy.inspect_copied_inputs_after_build)
        for method in ('command', 'allocate_temporary', 'stop_gradle', 'artifact_inventory', 'verify_workers', 'remove_temporary'):
            self.assertIs(getattr(driver.RecoveryLane, method), getattr(driver.normal.Lane, method))

    def test_real_no_build_copy_assembly_with_actual_private_helpers_and_inventory(self):
        fcopy = driver.fcopy
        required = set(fcopy.BUILD_ROOTS) | set(fcopy.MODIFIED_KOTLIN) | set(driver.copy_controls.SWIFT_CHANGED) | {
            'gradle/wrapper/gradle-wrapper.jar', 'gradle/verification-metadata.xml', 'iosApp/iosApp/Info.plist',
            'iosApp/iosApp/ComposeContainerViewController.swift',
            'iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift',
            'scripts/verification/ios-readiness/simulator_signing.py'}
        entries = [dict(path=p, sha256=driver.normal.digest(driver.ROOT / p)) for p in sorted(required)]
        binding = dict(schema_version=3, binding_status='REVIEW_REQUIRED_BOUND', copy_only=entries,
            source_identity=dict(source_manifest=[[e['path'], e['sha256']] for e in entries]))
        with tempfile.TemporaryDirectory(prefix='parlor-audit-ios-readiness-39-smoke-') as raw:
            temporary = Path(raw).resolve(); copied = temporary / 'copy'; copied.mkdir(mode=0o700)
            evidence = temporary / 'evidence/ios-readiness-39'; evidence.mkdir(parents=True)
            driver.normal.create_source_copy(driver.ROOT, copied, binding)
            phase = driver.normal.render_owned_kotlin_phase((driver.SUPPORT / 'copied-kotlin-phase.sh.in').read_text(),
                temporary, evidence, 'iphonesimulator26.2', 'adhoc')
            driver.normal.transform_copy(copied, 'unused normal fixture', phase)
            manifest = driver.normal.inventory_copy(copied, binding, driver.normal.digest)
            changed = {e['path'] for e in manifest if e['original_sha256'] and e['original_sha256'] != e['copied_sha256']}
            self.assertEqual(changed, driver.normal.CHANGED)
            self.assertEqual({e['path'] for e in manifest if e['original_sha256'] is None}, set(fcopy.ADDITIONS))
            self.assertTrue(driver.normal.inspect_copied_inputs_after_build(copied, manifest)['unchanged'])
            self.assertIn('recordOSRecoveryImages', (copied / driver.copy_controls.CONTENT).read_text())
            ui = (copied / driver.copy_controls.UI_TEST).read_text()
            for name in receipts.METHODS.values():
                self.assertEqual(ui.count('func ' + name.split('/')[1]), 1)
            diff = driver.copy_controls.source_diff(copied, fcopy)
            self.assertIn('--- /dev/null', diff)
            self.assertIn(json.dumps(phase), (copied / driver.copy_controls.PROJECT).read_text())
            self.assertTrue(all(driver.normal.digest(driver.ROOT / e['path']) == e['sha256'] for e in entries))
            (copied / driver.copy_controls.APP).write_text('changed after one build')
            self.assertFalse(driver.normal.inspect_copied_inputs_after_build(copied, manifest)['unchanged'])

    def test_one_build_and_only_two_distinct_selected_test_invocations(self):
        value = SimpleNamespace(temporary=Path('/tmp/owned'), environment=dict(SDK_NAME='iphonesimulator26.2'),
                                uuid=DEVICE, signing=dict(CODE_SIGN_IDENTITY='-'))
        commands = [driver.xcode_arguments(value, stage) for stage in driver.STAGES]
        self.assertEqual([c[-1] for c in commands], ['build-for-testing', 'test-without-building', 'test-without-building'])
        selectors = ['-only-testing:iosAppUITests/' + v.removesuffix('()') for v in receipts.METHODS.values()]
        self.assertEqual([[a for a in c if a.startswith('-only-testing:')] for c in commands],
                         [selectors, selectors[:1], selectors[1:]])
        self.assertTrue(all(c[c.index('-destination') + 1] == 'id=' + DEVICE for c in commands))
        self.assertTrue(all('-test-iterations' not in c and 'test' not in c for c in commands))

    def test_ordinary_exit65_is_diagnostic_only_not_a_bootstrap_pass(self):
        for code in (0, 65):
            self.assertIs(receipts.ordinary_bootstrap(completed_stage(code), *xctest(passed=code == 0),
                stage_marker('bootstrap'), DEVICE, driver.functional), code == 0)
        for update in (dict(command_completed=False), dict(exit_code=-15), dict(exit_code=True),
                       dict(preserved=False), dict(stop_exit_code=1), dict(errors=[dict(stage='stop')]),
                       dict(extraction_exit_codes=dict(summary=0, tests=1))):
            value = completed_stage(65); value.update(update)
            with self.subTest(update=update), self.assertRaises(RuntimeError):
                receipts.ordinary_bootstrap(value, *xctest(passed=False), stage_marker('bootstrap'), DEVICE, driver.functional)

    def test_timeout_in_any_retained_view_or_inconsistent_failure_identity_blocks_proof(self):
        for where in ('log', 'summary', 'tests'):
            summary, tests = xctest(passed=False); log = stage_marker('bootstrap')
            timeout = 'Test exceeded execution time allowance'
            if where == 'log': log += '\n' + timeout
            elif where == 'summary': summary['testFailures'][0]['failureText'] = timeout
            else: tests['testNodes'][0]['children'][0]['children'] = [dict(nodeType='Failure Message', name=timeout)]
            with self.subTest(where=where), self.assertRaises(RuntimeError):
                receipts.ordinary_bootstrap(completed_stage(65), summary, tests, log, DEVICE, driver.functional)
        for failures in (None, [], ['failure'], [dict(failureText='crash', testIdentifierString='wrong method')]):
            summary, tests = xctest(passed=False); summary['testFailures'] = failures
            with self.assertRaises(RuntimeError): receipts.xctest(summary, tests, 'bootstrap', DEVICE)

    def test_exact_xctest_and_stage_marker_reject_wrong_device_extra_case_skip_or_token_shape(self):
        for mutate in (lambda s, t: s.update(skippedTests=1),
                       lambda s, t: t['devices'][0].update(deviceId=TOKEN),
                       lambda s, t: t['testNodes'][0]['children'].append(copy.deepcopy(t['testNodes'][0]['children'][0])),
                       lambda s, t: t['testNodes'][0]['children'][0].update(result='Skipped')):
            summary, tests = xctest(); mutate(summary, tests)
            with self.assertRaises(RuntimeError): receipts.xctest(summary, tests, 'bootstrap', DEVICE)
        for log in ('', stage_marker('proof'), stage_marker('bootstrap') + '\n' + stage_marker('bootstrap'),
                    stage_marker('bootstrap').replace(TOKEN, 'not-a-token')):
            with self.assertRaises(RuntimeError): receipts.marker(log, 'bootstrap', DEVICE, driver.functional)

    def test_actual_stage_exception_stops_and_preserves_but_cannot_admit_next_stage(self):
        for fault in ('timeout', 'interrupt', 'stop', 'preserve'):
            with tempfile.TemporaryDirectory() as raw:
                value = lane(Path(raw).resolve()); value.receipt['postbuild_evidence_preserved'] = True
                primary = subprocess.TimeoutExpired(['xcodebuild'], 600) if fault == 'timeout' else KeyboardInterrupt()
                value.command = mock.Mock(side_effect=primary) if fault in ('timeout', 'interrupt') else mock.Mock(return_value=0)
                def preserve(stage):
                    self.assertIs(value.receipt['postbuild_evidence_preserved'], False)
                    if fault == 'preserve': raise RuntimeError('archive failed')
                    value.receipt['os_recovery_stages'][stage]['preserved'] = True
                value.preserve_stage = mock.Mock(side_effect=preserve)
                if fault == 'stop': value.stop_gradle.side_effect = RuntimeError('owned stop failed')
                expected = type(primary) if fault in ('timeout', 'interrupt') else RuntimeError
                with self.subTest(fault=fault), self.assertRaises(expected): value.run_stage('bootstrap')
                row = value.receipt['os_recovery_stages']['bootstrap']
                self.assertEqual((row['status'], value.receipt['bootstrap_status']), ('FAIL', 'FAIL'))
                self.assertIs(value.receipt['os_recovery_stages']['proof']['attempted'], False)
                self.assertIs(row['command_completed'], fault not in ('timeout', 'interrupt'))
                if not row['command_completed']: self.assertNotIn('exit_code', row)
                self.assertEqual(value.receipt['postbuild_evidence_preserved'], fault != 'preserve')
                value.stop_gradle.assert_called_once_with('stop-bootstrap-immediate')
                value.preserve_stage.assert_called_once_with('bootstrap')

    def test_raw_result_archive_and_both_test_views_are_preserved_before_acknowledgement(self):
        with tempfile.TemporaryDirectory() as raw:
            value = lane(Path(raw).resolve()); (value.temporary / 'bootstrap.xcresult').mkdir()
            def archive(arguments, *_args, **_kwargs): Path(arguments[-1]).write_bytes(b'bounded synthetic archive')
            def extract(arguments, name, **kwargs):
                (value.destination / name).write_text('{}'); return 0
            value.require, value.command = mock.Mock(side_effect=archive), mock.Mock(side_effect=extract)
            value.preserve_stage('bootstrap')
            row = value.receipt['os_recovery_stages']['bootstrap']
            self.assertIs(row['preserved'], True)
            self.assertEqual(row['extraction_exit_codes'], dict(summary=0, tests=0))
            self.assertEqual([call.args[0][4] for call in value.command.call_args_list], ['summary', 'tests'])
            self.assertEqual(row['result_archive_sha256'], driver.normal.digest(value.destination / 'bootstrap.xcresult.zip'))
            value.preserve_stage('build')
            self.assertIs(value.receipt['os_recovery_stages']['build']['result_absent'], True)

    def test_raw_non_json_os_bytes_survive_preservation_but_original_reader_rejects_them(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve(); value = lane(root)
            container = root / 'Library/Developer/CoreSimulator/Devices' / DEVICE / 'data/application'
            result = container / 'tmp'; result.mkdir(parents=True)
            name = 'parlor-os-recovery-images-1.json'; (result / name).write_bytes(b'{truncated')
            (value.destination / 'proof-container.log').write_text(str(container))
            value.command = mock.Mock(return_value=0)
            with mock.patch.object(Path, 'home', return_value=root): value.preserve_os()
            self.assertEqual((value.destination / name).read_bytes(), b'{truncated')
            with self.assertRaises(RuntimeError):
                driver.functional('l08_receipts').read_owned_result(value.destination / name, value.destination, 49152)

    def test_transition_fences_fail_closed_for_every_drift_and_ownership_axis(self):
        for fault in (None, 'source', 'controls', 'copy', 'products', 'device', 'ownership', 'secondary'):
            with tempfile.TemporaryDirectory() as raw:
                value = lane(Path(raw).resolve()); value.built_products = ['frozen']
                value.products = mock.Mock(return_value=['changed'] if fault == 'products' else ['frozen'])
                value.verify_workers = mock.Mock(side_effect=RuntimeError('unknown holder') if fault == 'ownership' else None)
                value.simulator_metadata = mock.Mock(return_value=dict(udid=TOKEN if fault == 'device' else DEVICE, state='Booted'))
                if fault == 'secondary': value.owner.secondary_errors = dict(unattested=True)
                with mock.patch.object(driver.normal, 'normalized_identity', return_value=dict(source='drift' if fault == 'source' else 'frozen')), \
                        mock.patch.object(driver, 'control_hash', return_value='b' * 64 if fault == 'controls' else value.approved), \
                        mock.patch.object(driver.fcopy, 'inspect_copied_inputs_after_build', return_value=dict(unchanged=fault != 'copy')):
                    if fault is None: value.fence('before-proof')
                    else:
                        with self.subTest(fault=fault), self.assertRaises(RuntimeError): value.fence('before-proof')
                self.assertEqual(len(value.receipt.get('transition_fences', [])), int(fault is None))
                value.owner.stop.assert_called_once()

    def test_real_product_hashes_change_and_redirected_products_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw).resolve(); value = lane(root)
            product = value.temporary / 'DerivedData/Build/Products/Debug-iphonesimulator/Parlor.app/Parlor'
            product.parent.mkdir(parents=True); product.write_bytes(b'first owned product')
            before = value.products()
            self.assertEqual(before[0]['sha256'], driver.normal.digest(product))
            self.assertEqual(before, value.products())
            product.write_bytes(b'mutated without a build')
            self.assertNotEqual(before, value.products())
            outside = root / 'not-an-owned-product'; outside.write_bytes(b'external')
            product.unlink(); product.symlink_to(outside)
            with self.assertRaises((RuntimeError, ValueError)): value.products()

    def test_one_build_drives_ordinary_failed_bootstrap_to_diagnostic_proof_only(self):
        with tempfile.TemporaryDirectory() as raw:
            value = lane(Path(raw).resolve()); (value.destination / 'embedded-gradle-stop.txt').write_text('build_exit=0\nstop_exit=0\n')
            order = []
            def command(arguments, name, **_kwargs):
                stage = Path(arguments[arguments.index('-resultBundlePath') + 1]).stem; order.append(stage)
                (value.destination / name).write_text(stage_marker(stage)); return 65 if stage == 'bootstrap' else 0
            def preserve(stage):
                value.receipt['os_recovery_stages'][stage].update(preserved=True, extraction_exit_codes=dict(summary=0, tests=0))
            value.command, value.preserve_stage = mock.Mock(side_effect=command), mock.Mock(side_effect=preserve)
            value.products, value.fence = mock.Mock(return_value=[]), mock.Mock()
            value.stage_views = mock.Mock(return_value=xctest(passed=False))
            with mock.patch.object(driver.normal, 'read_phase_receipt', return_value=dict(mode='adhoc')), \
                    mock.patch.object(driver.normal, 'digest', return_value='a' * 64): value.run_xctest()
            self.assertEqual(order, list(driver.STAGES))
            self.assertEqual(value.receipt['bootstrap_status'], 'FAIL')
            self.assertEqual(value.receipt['os_recovery_stages']['proof']['status'], 'FAIL')  # Await semantic proof, not exit0.
            value.lifecycle.mark_build_attempted.assert_called_once_with()
            self.assertEqual([call.args[0] for call in value.fence.call_args_list], ['before-bootstrap', 'before-proof', 'after-proof'])

    def test_real_common_and_image_binder_require_every_start_token_and_artifact(self):
        report, records, built, artifacts = image_fixture()
        result = receipts.process_images(report, records, TOKEN, DEVICE, 'adhoc', built, built, artifacts, driver.functional)
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(len(result['runs']), 3)
        for mutate in (lambda r, rows, b, a: r.update(completed=False),
                       lambda r, rows, b, a: rows.pop(),
                       lambda r, rows, b, a: rows.reverse(),
                       lambda r, rows, b, a: rows[1].update(run_token=DEVICE),
                       lambda r, rows, b, a: rows[1].update(process_boot=rows[0]['process_boot']),
                       lambda r, rows, b, a: rows[0].update(boot_ordinal=True),
                       lambda r, rows, b, a: rows[0].update(process_id=0),
                       lambda r, rows, b, a: rows[0]['loaded_app_images'].remove('Parlor.debug.dylib'),
                       lambda r, rows, b, a: rows[0]['loaded_compose_framework'].update(file_sha256='a' * 64),
                       lambda r, rows, b, a: a.clear()):
            r, rows, b, a = copy.deepcopy((report, records, built, artifacts)); mutate(r, rows, b, a)
            with self.assertRaises(RuntimeError): receipts.process_images(r, rows, TOKEN, DEVICE, 'adhoc', b, b, a, driver.functional)

    def test_proof_requires_every_original_gate_and_image_binding_without_add_language(self):
        # Existing validators own their complete UI matrices. This tests only the
        # new orchestration, with real preference/common/image validation below.
        fixture = driver.functional('test_os_preference_receipts')
        log = stage_marker('proof') + '\n' + fixture.log(fixture.fixture()[1])
        modules = [driver.functional(name) for name in ('public_interaction_receipts', 'os_preference_receipts',
                   'os_action_receipts', 'os_pane_receipts')]
        for failure in (None, 'prerequisite', 'preference', 'actions', 'panes', 'images'):
            report, images, built, artifacts = image_fixture()
            prerequisite = dict(status='PASS', disposition='already-satisfied', arabic_added=False,
                                preexisting_configuration_verified=True)
            if failure == 'prerequisite': prerequisite.update(disposition='arabic-added', arabic_added=True)
            if failure == 'images': images[0]['loaded_app_images'].remove('Parlor.debug.dylib')
            with mock.patch.object(modules[0], 'verify_os_prerequisite', return_value=prerequisite) as public, \
                    mock.patch.object(modules[1], 'verify_os', wraps=modules[1].verify_os,
                        side_effect=RuntimeError('original preference gate failed') if failure == 'preference' else None) as preference, \
                    mock.patch.object(modules[2], 'verify_os_action_receipts',
                        return_value=dict(status='FAIL' if failure == 'actions' else 'PASS')) as actions, \
                    mock.patch.object(modules[3], 'verify_os_pane_receipts',
                        return_value=dict(status='FAIL' if failure == 'panes' else 'PASS')) as panes:
                arguments = (*xctest('proof'), log, report, images, DEVICE, 'adhoc', built, built, artifacts, driver.functional)
                if failure is None:
                    result = receipts.verify_proof(*arguments)
                    self.assertEqual(result['status'], 'PASS')
                    self.assertEqual(result['os_gate']['observed_os_preference_representation'], 'PRESENT')
                    for validator in (public, preference, actions, panes): validator.assert_called_once()
                    actions.assert_called_once_with(log, report, result['os_gate'])
                else:
                    with self.subTest(failure=failure), self.assertRaises(RuntimeError): receipts.verify_proof(*arguments)

    def test_os_subgate_can_pass_failed_bootstrap_but_never_changes_overall_failure(self):
        value = dict(cleanup_status='PASS', cleanup_errors=[], original_outputs_preserved=[],
            source_unchanged=True, controls_unchanged=True, copied_sources_unchanged=True,
            postbuild_evidence_preserved=True, temporary_directory_removed=True,
            os_observation_validation=dict(status='PASS', os_gate=dict(observed_os_preference_representation='ABSENT',
                actual_present_os_preference_coverage=False)), os_provenance_status='PASS', os_notice_package_status='PASS',
            os_recovery_stages={k: dict(attempted=True, status='PASS') for k in driver.STAGES})
        self.assertEqual(receipts.classify(value)['status'], receipts.SUCCESS)
        value['os_recovery_stages']['bootstrap']['status'] = 'FAIL'
        self.assertEqual(receipts.classify(value), dict(status='FAIL', os_subgate_status='PASS'))
        self.assertFalse(value['os_observation_validation']['os_gate']['actual_present_os_preference_coverage'])
        for key in ('source_unchanged', 'controls_unchanged', 'copied_sources_unchanged', 'postbuild_evidence_preserved'):
            broken = copy.deepcopy(value); broken[key] = False
            self.assertEqual(receipts.classify(broken), dict(status='FAIL', os_subgate_status='FAIL'))

    def test_sigterm_entrypoint_and_interrupt_path_reach_finalizer_without_signal_masks(self):
        tree = ast.parse((HERE / 'os_recovery_probe.py').read_text())
        entry = tree.body[-1]
        self.assertIsInstance(entry, ast.If)
        signals, main = SimpleNamespace(SIGTERM=signal.SIGTERM, signal=mock.Mock()), mock.Mock(return_value=1)
        namespace = dict(__name__='__main__', signal=signals, main=main)
        with self.assertRaises(SystemExit): exec(compile(ast.Module(body=[entry], type_ignores=[]), '<entrypoint>', 'exec'), namespace)
        signals.signal.assert_called_once_with(signal.SIGTERM, namespace['interrupted'])
        with self.assertRaises(KeyboardInterrupt): namespace['interrupted'](signal.SIGTERM, None)
        with tempfile.TemporaryDirectory() as raw:
            value = lane(Path(raw).resolve()); value.prepare = mock.Mock(side_effect=KeyboardInterrupt('signal 15'))
            value.finalize = mock.Mock(side_effect=lambda: value.receipt.update(status='FAIL', cleanup_status='PASS'))
            with mock.patch('sys.stdout', new=io.StringIO()): self.assertEqual(value.run(), 1)
            value.finalize.assert_called_once_with()
            self.assertEqual(value.receipt['error']['type'], 'KeyboardInterrupt')


if __name__ == '__main__':
    unittest.main()
