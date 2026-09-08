"""Synthetic parser/ownership tests only; never native/device evidence."""
import copy
import hashlib
import json
from pathlib import Path
import plistlib
import tempfile
import unittest

import artifact_inventory as artifacts
import native_readiness_receipts as receipts
import probe_validation as legacy
from owned_lane import owns_java_tmp_argument

TOKEN = 'deadbeef-1234-5678-9000-000000000001'
IMAGES = ['Frameworks/ComposeApp.framework/ComposeApp', 'Parlor', 'Parlor.debug.dylib']

PRODUCT_PATH = 'bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/ComposeApp'


def framework_observation(origin='installed-app-bundle', path=artifacts.FRAMEWORK_PATH, payload=None):
    payload = bytes.fromhex('cffaedfe') + artifacts.FRAMEWORK_PATH.encode() if payload is None else payload
    return dict(origin=origin, path=path, image_uuid=TOKEN, file_bytes=len(payload),
                file_sha256=hashlib.sha256(payload).hexdigest())


def framework_inventory(value=None):
    value = framework_observation() if value is None else value
    return dict(origin=value['origin'], path=value['path'], bytes=value['file_bytes'], sha256=value['file_sha256'],
                architectures=[dict(architecture='arm64', uuid=value['image_uuid'])])


def loader_environment():
    return {key: dict(present=False, task_owned_paths=[], redacted_entry_count=0) for key in artifacts.LOADER_KEYS}


def preferences(language='en', previous=None, owned=False):
    previous = [] if previous is None else previous
    return dict(setting=language if owned else 'system', appLanguages=[language] if owned else previous,
                hasAppOverride=owned or bool(previous), ownerPresent=owned,
                ownerInstalled=language if owned else 'none', ownerPreviousPresent=owned and bool(previous),
                ownerPrevious=previous if owned else [], preferredLanguage=language)


def event(ordinal, boot, phase='sample', preference=None, os_status='not-investigated', settings=False):
    before = phase == 'before_main'
    pref = copy.deepcopy(preferences() if preference is None else preference)
    geometry = {key: [0, 0, 393, 852] for key in legacy.RECTANGLES}
    geometry.update({key: [59, 0, 34, 0] for key in legacy.SAFE_AREAS})
    geometry['orientation'] = 'portrait'
    native = {key: not before for key in legacy.NATIVE_BOOLEANS}
    native.update(attachments=0 if before else 1, disposals=0, outerDirection='unavailable' if before else 'unspecified')
    if not before:
        native['geometry'] = geometry
    composition = {key: False for key in legacy.COMPOSITION_BOOLEANS}
    composition.update({key: 0 for key in legacy.COMPOSITION_COUNTERS})
    composition.update(settingsMounted=settings,
                       settingsDirection=('rtl' if pref['preferredLanguage'] == 'ar' else 'ltr') if settings else 'absent',
                       surface='none', gameDirection='absent', publicPhase='none', commandLanguage='none', commandStatus='none')
    return dict(ordinal=ordinal, phase=phase, fixture='existing', boot=boot, preferences=pref,
                nativeDirection='unavailable' if before else 'force_rtl' if pref['preferredLanguage'] == 'ar' else 'force_ltr',
                native=native, controllerCreations=0 if before else 1,
                composition=json.dumps({} if before else composition), osDisposition=os_status,
                samplingWindow=0, samplingIndex=0, samplingElapsedMilliseconds=0, samplingContext='none')


def native_step(name, kind, events=()):
    return dict(step=name, outcome=dict(kind=kind), overflow=False,
                native_events=[dict(ordinal=index, operation=operation, status=status)
                               for index, (operation, status) in enumerate(events, 1)])


def boot_result(ordinal, mode='disabled'):
    home = native_step('home-rerun', 'home_observed', [('credential-read', -25300)])
    home['outcome'].update(local='absent', multiplayer='absent', has_unavailable_source=False, original_home_invocation=False)
    expected = 'matches' if ordinal in (2, 3) else 'absent'
    steps = [home, native_step('credential-read', expected, [('credential-read', 0 if expected == 'matches' else -25300)]),
             native_step('snapshot-read', expected)]
    if ordinal in (1, 2):
        statuses = [('credential-update', -25300), ('credential-add', 0)] if ordinal == 1 else [('credential-update', 0)]
        steps += [native_step('credential-write', 'ok', statuses), native_step('snapshot-write', 'ok')]
    elif ordinal == 3:
        steps += [native_step('credential-remove', 'ok', [('credential-delete', 0)]), native_step('snapshot-remove', 'ok')]
    return dict(schema_version=2, boot_ordinal=ordinal, process_boot=f'{ordinal:08x}-1234-5678-9000-000000000001',
                process_id=1000 + ordinal, run_token=TOKEN, loaded_app_images=IMAGES[:],
                loaded_compose_framework=framework_observation(), loader_environment=loader_environment(), signing_mode=mode,
                observation=dict(schema_version=2, kind='actual_koin_simulator', harness_status='observation_complete',
                                 boot_ordinal=ordinal, run_token=TOKEN, steps=steps,
                                 credential_numeric_status_is_exact_invocation=True, initial_home_numeric_attribution=False,
                                 snapshot_numeric_attribution=False, physical_or_store_evidence=False))


def scenario_fixture(mode='disabled'):
    results = [boot_result(ordinal, mode) for ordinal in range(1, 9)]
    report = dict(schemaVersion=6, runToken=TOKEN, scenario='readiness', completed=True, observations=[])
    launches = []
    for result in results:
        boot = result['process_boot']
        report['observations'].append(event(len(report['observations']) + 1, boot, 'before_main'))
        samples = []
        for ordinal in range(1, 7):
            observation = event(len(report['observations']) + 1, boot)
            report['observations'].append(observation)
            samples.append(dict(ordinal=ordinal, elapsed_milliseconds=(ordinal - 1) * 2000,
                                observation_sequence=observation['ordinal']))
        launches.append(dict(schema_version=2, signing_mode=mode, boot_ordinal=result['boot_ordinal'], process_boot=boot,
                             run_token=TOKEN, loaded_app_images=IMAGES[:], loaded_compose_framework=framework_observation(), samples=samples,
                             app_foreground=True, native_alert_present=False, probe_activations=1))
    report['observations'].append(event(len(report['observations']) + 1, results[-1]['process_boot'], 'complete'))
    return results, report, launches


def logs(launches):
    return '\n'.join('PARLOR_NATIVE_COLD_LAUNCH ' + json.dumps(row) for row in launches)


def settings_fixture():
    # Minimal synthetic source for the cleanup parser, NOT the full Settings UI matrix.
    first = event(1, 'eeeeeeee-1234-5678-9000-000000000001', 'before_main')
    first['fixture'] = 'fresh'
    previous = preferences('ar', previous=['ar-EG', 'en'])
    seed = event(2, 'eeeeeeee-1234-5678-9000-000000000002', 'before_main', previous)
    seed['fixture'] = 'previous-ar'
    completed = event(3, seed['boot'], 'complete', previous)
    completed['fixture'] = 'previous-ar'
    return dict(schemaVersion=6, runToken=TOKEN, scenario='settings', completed=True,
                observations=[first, seed, completed])


def cleanup_fixture():
    return dict(schema_version=2, run_token=TOKEN, process_boot=boot_result(1)['process_boot'], original_present=False,
                synthetic_previous=['ar-EG', 'en'], restored_present=False,
                source='completed-token-bound-owned-settings-fixture-only')


def step(result, name):
    return next(value for value in result['observation']['steps'] if value['step'] == name)


class NativeReceiptTests(unittest.TestCase):
    def verify(self, results=None, scenario=None, launches=None, cleanup=True, settings=None):
        original_results, original_scenario, original_launches = scenario_fixture()
        return receipts.verify_native_readiness(original_results if results is None else results,
                                               original_scenario if scenario is None else scenario,
                                               settings_fixture() if settings is None else settings,
                                               logs(original_launches if launches is None else launches),
                                               cleanup_fixture() if cleanup is True else None if cleanup is False else cleanup)

    def test_complete_synthetic_eight_launch_matrix_is_parseable_not_runtime_proof(self):
        result = self.verify()
        self.assertEqual(result['diagnostic_delivery'], 'PASS')
        self.assertEqual(result['storage_health'], 'PASS')
        self.assertEqual(result['cold_launches'], 8)
        self.assertEqual(result['stability_samples'], 48)
        self.assertEqual(result['original_first_four_launch_failures_cause'], 'UNRESOLVED')

    def test_explicit_mode_binds_every_boot_and_xctest_row_without_claiming_store_signing(self):
        results, scenario, launches = scenario_fixture('adhoc')
        actual = receipts.verify_native_readiness(results, scenario, settings_fixture(), logs(launches), cleanup_fixture(), 'adhoc')
        self.assertEqual(actual['signing_mode'], 'adhoc')
        self.assertEqual(actual['storage_health'], 'PASS')  # Synthetic successful outcomes, not actual native evidence.
        with self.assertRaises(RuntimeError):
            receipts.verify_native_readiness(results, scenario, settings_fixture(), logs(launches), cleanup_fixture())
        results[3]['signing_mode'] = 'disabled'
        with self.assertRaises(RuntimeError):
            receipts.verify_native_readiness(results, scenario, settings_fixture(), logs(launches), cleanup_fixture(), 'adhoc')
        results, scenario, launches = scenario_fixture('adhoc'); launches[-1]['signing_mode'] = 'disabled'
        with self.assertRaises(RuntimeError):
            receipts.verify_native_readiness(results, scenario, settings_fixture(), logs(launches), cleanup_fixture(), 'adhoc')
        result = boot_result(1, 'adhoc'); result['observation']['physical_or_store_evidence'] = True
        with self.assertRaises(RuntimeError): receipts.verify_boot_result(result, 1, TOKEN, 'adhoc')

    def test_exact_native_missing_entitlement_is_not_healthy_storage_or_an_invented_root_cause(self):
        results, scenario, launches = scenario_fixture()
        for result in results:
            home = step(result, 'home-rerun')
            home['outcome'].update(multiplayer='secure_storage_unavailable', has_unavailable_source=True)
            home['native_events'][0]['status'] = -34018
            for item in result['observation']['steps']:
                if item['step'] == 'credential-read':
                    item['outcome']['kind'] = 'storage_error'; item['native_events'][0]['status'] = -34018
                if item['step'] == 'credential-write':
                    item['outcome']['kind'] = 'storage_error'
                    item['native_events'] = [dict(ordinal=1, operation='credential-update', status=-34018)]
                if item['step'] == 'credential-remove':
                    item['outcome']['kind'] = 'storage_error'; item['native_events'][0]['status'] = -34018
        result = self.verify(results, scenario, launches)
        self.assertEqual(result['diagnostic_delivery'], 'PASS')
        self.assertEqual(result['storage_health'], 'BLOCKED')
        self.assertTrue(all(boot['status'] == 'BLOCKED' for boot in result['storage_boots']))
        self.assertNotIn('cause', result)

    def test_loss_or_corruption_after_successful_write_fails_the_gate(self):
        for store in ('credential', 'snapshot'):
            for outcome in ('absent', 'mismatch'):
                with self.subTest(store=store, outcome=outcome):
                    results, _, _ = scenario_fixture()
                    item = step(results[1], store + '-read')
                    item['outcome']['kind'] = outcome
                    if store == 'credential': item['native_events'][0]['status'] = -25300 if outcome == 'absent' else 0
                    self.assertEqual(self.verify(results)['storage_health'], 'FAIL')

    def test_missing_value_after_failed_write_remains_blocked_not_durability_failure(self):
        for store in ('credential', 'snapshot'):
            with self.subTest(store=store):
                results, _, _ = scenario_fixture()
                write = step(results[0], store + '-write'); write['outcome']['kind'] = 'storage_error'
                if store == 'credential':
                    write['native_events'] = [dict(ordinal=1, operation='credential-update', status=-34018)]
                read = step(results[1], store + '-read'); read['outcome']['kind'] = 'absent'
                if store == 'credential': read['native_events'][0]['status'] = -25300
                self.assertEqual(self.verify(results)['storage_health'], 'BLOCKED')

    def test_retained_value_after_successful_remove_is_fail_and_after_failed_remove_is_blocked(self):
        for store in ('credential', 'snapshot'):
            for successful in (True, False):
                with self.subTest(store=store, successful=successful):
                    results, _, _ = scenario_fixture()
                    removal = step(results[2], store + '-remove')
                    if not successful:
                        removal['outcome']['kind'] = 'storage_error'
                        if store == 'credential': removal['native_events'][0]['status'] = -34018
                    read = step(results[3], store + '-read'); read['outcome']['kind'] = 'mismatch'
                    if store == 'credential': read['native_events'][0]['status'] = 0
                    self.assertEqual(self.verify(results)['storage_health'], 'FAIL' if successful else 'BLOCKED')

    def test_native_success_followed_by_secure_failure_is_fail_not_entitlement_attribution(self):
        for name in ('credential-read', 'home-rerun'):
            with self.subTest(name=name):
                results, _, _ = scenario_fixture(); item = step(results[0], name)
                if name == 'credential-read': item['outcome']['kind'] = 'storage_error'
                else: item['outcome'].update(multiplayer='secure_storage_unavailable', has_unavailable_source=True)
                item['native_events'][0]['status'] = 0
                self.assertEqual(self.verify(results)['storage_health'], 'FAIL')

    def test_write_duplicate_retry_trace_requires_all_actual_calls(self):
        result = boot_result(1); write = step(result, 'credential-write')
        write['native_events'][1]['status'] = -25299
        write['native_events'].append(dict(ordinal=3, operation='credential-retry', status=0))
        receipts.verify_boot_result(result, 1, TOKEN)
        for count in (1, 2):
            broken = copy.deepcopy(result); step(broken, 'credential-write')['native_events'] = write['native_events'][:count]
            step(broken, 'credential-write')['outcome']['kind'] = 'storage_error'
            with self.subTest(count=count), self.assertRaises(RuntimeError):
                receipts.verify_boot_result(broken, 1, TOKEN)

    def test_bound_types_privacy_and_exact_production_status_mapping(self):
        mutations = [
            lambda value: value.update(schema_version=True),
            lambda value: value['observation'].update(schema_version=True),
            lambda value: value['observation'].update(boot_ordinal=True),
            lambda value: value['observation'].update(initial_home_numeric_attribution=True),
            lambda value: value.update(seed='synthetic-sentinel'),
            lambda value: value.update(loaded_app_images=['/outside/Parlor']),
            lambda value: value.update(loaded_app_images=IMAGES + [IMAGES[0]]),
            lambda value: step(value, 'credential-read')['native_events'][0].update(status=True),
            lambda value: step(value, 'credential-read')['native_events'][0].update(status=0),
            lambda value: step(value, 'snapshot-read')['native_events'].append(dict(ordinal=1, operation='credential-read', status=0)),
            lambda value: step(value, 'home-rerun')['outcome'].update(has_unavailable_source=True),
            lambda value: step(value, 'home-rerun').update(overflow=True),
            lambda value: value['observation']['steps'].reverse(),
            lambda value: step(value, 'snapshot-write')['outcome'].update(kind='matches'),
        ]
        for index, change in enumerate(mutations):
            value = boot_result(1); change(value)
            with self.subTest(index=index), self.assertRaises(RuntimeError):
                receipts.verify_boot_result(value, 1, TOKEN)

    def test_unbound_launch_missing_cleanup_reuse_and_short_stability_train_fail(self):
        with self.assertRaises(RuntimeError): self.verify(cleanup=False)
        with self.assertRaises(RuntimeError): self.verify(results=[])
        with self.assertRaises(RuntimeError): self.verify(launches=[])
        for mutation in (
            lambda r, s, l: s.update(runToken='12345678-1234-5678-9000-000000000001'),
            lambda r, s, l: l[0].update(probe_activations=2),
            lambda r, s, l: l[0].update(app_foreground=False),
            lambda r, s, l: l[0]['samples'][1].update(elapsed_milliseconds=100),
            lambda r, s, l: l[0]['samples'][0].update(observation_sequence=1),
            lambda r, s, l: s['observations'][1]['native']['geometry']['composeBounds'].__setitem__(3, 700),
            lambda r, s, l: r[0].update(process_boot=r[1]['process_boot']),
            lambda r, s, l: s['observations'][0].update(ordinal=True),
        ):
            results, report, launches = scenario_fixture(); mutation(results, report, launches)
            with self.assertRaises(RuntimeError): self.verify(results, report, launches)


    def test_stdout_cleanup_claim_cannot_replace_a_durable_boot_bound_receipt(self):
        results, scenario, launches = scenario_fixture()
        forged_log = logs(launches) + '\nPARLOR_NATIVE_SYNTHETIC_SEED_CLEANUP ' + json.dumps(cleanup_fixture())
        with self.assertRaisesRegex(RuntimeError, 'attested synthetic seed cleanup'):
            receipts.verify_native_readiness(results, scenario, settings_fixture(), forged_log, None)

    def test_cleanup_exact_context_original_absence_and_source_completion_are_required(self):
        for field, value in [('schema_version', True), ('run_token', 'another-run'), ('process_boot', TOKEN),
                             ('original_present', 0), ('restored_present', 0), ('synthetic_previous', ['ar']),
                             ('source', 'inferred-from-start')]:
            with self.subTest(field=field):
                cleanup = cleanup_fixture(); cleanup[field] = value
                with self.assertRaises(RuntimeError): self.verify(cleanup=cleanup)
        settings = settings_fixture()
        settings['observations'][0]['preferences'] = preferences('ar', previous=['ar-EG', 'en'])
        with self.assertRaisesRegex(RuntimeError, 'pre-existing'): self.verify(settings=settings)
        settings = settings_fixture(); settings['observations'][1]['fixture'] = 'existing'
        with self.assertRaisesRegex(RuntimeError, 'earlier task-owned seed'): self.verify(settings=settings)
        settings = settings_fixture(); settings['completed'] = False
        with self.assertRaises(RuntimeError): self.verify(settings=settings)
        settings = settings_fixture()
        settings['observations'].append(event(4, settings['observations'][-1]['boot']))
        with self.assertRaises(RuntimeError): self.verify(settings=settings)

    def test_cleanup_reader_is_bounded_exclusive_path_safe_and_duplicate_key_strict(self):
        with tempfile.TemporaryDirectory(prefix='parlor-cleanup-receipt-test-') as raw:
            root = Path(raw).resolve(); path = root / 'cleanup.json'
            path.write_text(json.dumps(cleanup_fixture()))
            self.assertEqual(receipts.read_synthetic_seed_cleanup(path), cleanup_fixture())
            for data in (b'', b'{', b'[]', b'x' * 1025, b'{"schema_version":2,"schema_version":2}', b'\xff'):
                path.write_bytes(data)
                with self.subTest(data=data[:64]), self.assertRaises(RuntimeError): receipts.read_synthetic_seed_cleanup(path)
            target = root / 'other-synthetic.json'; target.write_text(json.dumps(cleanup_fixture()))
            path.unlink(); path.symlink_to(target)
            with self.assertRaises(RuntimeError): receipts.read_synthetic_seed_cleanup(path)
            self.assertEqual(json.loads(target.read_text()), cleanup_fixture())

    def test_boot_and_xctest_framework_observations_must_match(self):
        results, scenario, launches = scenario_fixture()
        launches[0]['loaded_compose_framework']['file_sha256'] = '0' * 64
        with self.assertRaisesRegex(RuntimeError, 'receipts disagree'): self.verify(results, scenario, launches)
        for change in (lambda result: result.pop('loaded_compose_framework'),
                       lambda result: result['loaded_compose_framework'].update(path='/another-task/ComposeApp'),
                       lambda result: result['loader_environment'].pop('DYLD_FRAMEWORK_PATH'),
                       lambda result: result['loader_environment']['DYLD_FRAMEWORK_PATH'].update(
                           present=True, task_owned_paths=['/private/unrelated'], redacted_entry_count=0)):
            result = boot_result(1); change(result)
            with self.assertRaises(RuntimeError): receipts.verify_boot_result(result, 1, TOKEN)


class NativeArtifactTests(unittest.TestCase):
    def make_app(self, root, name='Built.app'):
        app = root / name; app.mkdir()
        info = dict(CFBundleIdentifier='com.parlor.app.debug', CFBundleExecutable='Parlor',
                    CFBundleVersion='1', CFBundleShortVersionString='1.0', MinimumOSVersion='16.0')
        (app / 'Info.plist').write_bytes(plistlib.dumps(info))
        for image in IMAGES:
            target = app / image; target.parent.mkdir(parents=True, exist_ok=True)
            # Deliberately synthetic magic-bearing data, never a compiled image.
            target.write_bytes(bytes.fromhex('cffaedfe') + image.encode())
        return app

    def test_all_images_including_debug_dylib_bind_to_installed_and_executed_bytes(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
            root = Path(raw).resolve(); built = self.make_app(root); installed = self.make_app(root, 'Installed.app')
            a = artifacts.inventory_bundle(built); b = artifacts.inventory_bundle(installed)
            result = artifacts.bind_loaded_images(a, b, [boot_result(1)], [framework_inventory()])
            self.assertEqual(result['status'], 'PASS')
            self.assertEqual({row['path'] for row in result['runs'][0]['loaded_images']}, set(IMAGES))

    def test_missing_debug_image_tampered_install_or_unbound_executed_image_fail(self):
        for mutation in ('missing', 'modified', 'unbound'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
                root = Path(raw).resolve(); a = self.make_app(root); b = self.make_app(root, 'Installed.app')
                if mutation == 'missing': (a / 'Parlor.debug.dylib').unlink()
                if mutation == 'modified': (b / 'Parlor.debug.dylib').write_bytes(bytes.fromhex('cffaedfe') + b'changed')
                run = boot_result(1)
                if mutation == 'unbound': run['loaded_app_images'] = sorted(IMAGES + ['NoSuch.dylib'])
                with self.assertRaises(RuntimeError):
                    artifacts.bind_loaded_images(artifacts.inventory_bundle(a), artifacts.inventory_bundle(b), [run], [framework_inventory()])

    def test_external_links_and_symlinked_metadata_are_refused(self):
        for location in ('Info.plist', 'outside.dylib'):
            with self.subTest(location=location), tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
                root = Path(raw).resolve(); app = self.make_app(root); outside = root / 'synthetic-only.txt'
                outside.write_text('not an application metadata record')
                target = app / location
                if target.exists(): target.unlink()
                target.symlink_to(outside)
                with self.assertRaises(RuntimeError): artifacts.inventory_bundle(app)

    def test_internal_link_is_explicit_and_unsafe_relative_names_fail(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
            app = self.make_app(Path(raw).resolve()); (app / 'alias.dylib').symlink_to('Parlor.debug.dylib')
            value = artifacts.inventory_bundle(app)
            self.assertEqual(value['symbolic_links'], [dict(path='alias.dylib', resolved_path='Parlor.debug.dylib', directory=False)])
        for name in ('../x', '/x', './x', 'x//y', 'x\\y', 'x\x00y', '', 'x' * 513):
            with self.subTest(name=repr(name)): self.assertFalse(artifacts.safe_relative(name))

    def test_uuid_architecture_receipt_is_exact_and_not_a_signature_claim(self):
        path = Path('/owned/Parlor.app/Parlor.debug.dylib')
        text = 'UUID: DEADBEEF-1234-5678-9000-000000000001 (arm64) ' + str(path) + '\n'
        self.assertEqual(artifacts.parse_dwarfdump_uuids(text, path), [dict(architecture='arm64', uuid=TOKEN)])
        for bad in ('', text * 2, text.replace('arm64', 'unknown'), text.replace('/owned/', '/not-owned/')):
            with self.subTest(bad=bad), self.assertRaises(RuntimeError): artifacts.parse_dwarfdump_uuids(bad, path)


    def test_external_framework_is_distinct_runtime_product_not_relabelled_as_installed(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
            root = Path(raw).resolve(); built = self.make_app(root); installed = self.make_app(root, 'Installed.app')
            payload = bytes.fromhex('cffaedfe') + b'synthetic-build-product-before-embedded-signing'
            external = framework_observation('owned-copy-build', PRODUCT_PATH, payload)
            build_root = root / 'copy/composeApp/build'; path = build_root / PRODUCT_PATH
            path.parent.mkdir(parents=True); path.write_bytes(payload)
            actual = artifacts.inventory_owned_framework(build_root, external, 'iphonesimulator26.5')
            self.assertEqual(actual['sha256'], external['file_sha256'])
            run = boot_result(1); run['loaded_app_images'].remove(artifacts.FRAMEWORK_PATH)
            run['loaded_compose_framework'] = external
            run['loader_environment']['DYLD_FRAMEWORK_PATH'] = dict(present=True,
                task_owned_paths=['copy/composeApp/build/xcode-frameworks/Debug/iphonesimulator26.5'], redacted_entry_count=1)
            inventories = [framework_inventory(), framework_inventory(external)]
            bound = artifacts.bind_loaded_images(artifacts.inventory_bundle(built), artifacts.inventory_bundle(installed), [run], inventories)
            observed = bound['runs'][0]['loaded_compose_framework']
            self.assertEqual(bound['status'], 'PASS')
            self.assertEqual(observed['origin'], 'owned-copy-build')
            self.assertEqual(observed['path'], PRODUCT_PATH)
            self.assertFalse(observed['same_bytes_as_embedded'])
            self.assertNotEqual(observed['file_sha256'], observed['embedded_sha256'])
            self.assertEqual({image['path'] for image in bound['runs'][0]['loaded_images']}, {'Parlor', 'Parlor.debug.dylib'})

    def test_external_runtime_hash_uuid_origin_and_each_boot_must_be_bound(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
            root = Path(raw).resolve(); built = self.make_app(root); installed = self.make_app(root, 'Installed.app')
            a, b = artifacts.inventory_bundle(built), artifacts.inventory_bundle(installed)
            external = framework_observation('owned-copy-build', PRODUCT_PATH)
            run = boot_result(1); run['loaded_app_images'].remove(artifacts.FRAMEWORK_PATH)
            run['loaded_compose_framework'] = external
            expected = [framework_inventory(), framework_inventory(external)]
            for change in (lambda value: value['loaded_compose_framework'].update(file_sha256='0' * 64),
                           lambda value: value['loaded_compose_framework'].update(image_uuid='00000000-1234-5678-9000-000000000001'),
                           lambda value: value['loaded_compose_framework'].update(file_bytes=True),
                           lambda value: value['loaded_compose_framework'].update(origin='installed-app-bundle'),
                           lambda value: value['loaded_app_images'].insert(0, artifacts.FRAMEWORK_PATH)):
                broken = copy.deepcopy(run); change(broken)
                with self.assertRaises(RuntimeError): artifacts.bind_loaded_images(a, b, [run, broken], expected)
            for inventories in (expected[:1], expected[1:], expected + expected[:1]):
                with self.assertRaises(RuntimeError): artifacts.bind_loaded_images(a, b, [run], inventories)

    def test_external_product_path_sdk_symlink_bounds_and_observed_fingerprint_are_strict(self):
        with tempfile.TemporaryDirectory(prefix='parlor-native-inventory-test-') as raw:
            root = Path(raw).resolve(); build_root = root / 'copy/composeApp/build'
            target = build_root / PRODUCT_PATH; target.parent.mkdir(parents=True)
            payload = bytes.fromhex('cffaedfe') + artifacts.FRAMEWORK_PATH.encode(); target.write_bytes(payload)
            value = framework_observation('owned-copy-build', PRODUCT_PATH)
            self.assertEqual(artifacts.inventory_owned_framework(build_root, value, 'iphonesimulator26.5')['bytes'], len(payload))
            target.write_bytes(payload + b'changed-after-runtime')
            with self.assertRaisesRegex(RuntimeError, 'fingerprint'): artifacts.inventory_owned_framework(build_root, value, 'iphonesimulator26.5')
            target.unlink(); unrelated = root / 'synthetic-other-task'; unrelated.write_bytes(payload); target.symlink_to(unrelated)
            with self.assertRaises(RuntimeError): artifacts.inventory_owned_framework(build_root, value, 'iphonesimulator26.5')
            self.assertEqual(unrelated.read_bytes(), payload)
            for path in ('../outside/ComposeApp', '/outside/ComposeApp', 'bin/iosArm64/debugFramework/ComposeApp.framework/ComposeApp',
                         'bin/iosSimulatorArm64/releaseFramework/ComposeApp.framework/ComposeApp'):
                bad = dict(value, path=path)
                with self.assertRaises(RuntimeError): artifacts.inventory_owned_framework(build_root, bad, 'iphonesimulator26.5')
            wrong_sdk = dict(value, path='xcode-frameworks/Debug/iphonesimulator26.3/ComposeApp.framework/ComposeApp')
            with self.assertRaises(RuntimeError): artifacts.inventory_owned_framework(build_root, wrong_sdk, 'iphonesimulator26.5')

    def test_native_observer_uses_exact_owned_source_anchors_not_environment_inference(self):
        source = Path(__file__).with_name('NativeReadinessLaunch.swift.in').read_text()
        with tempfile.TemporaryDirectory(prefix='parlor-audit-ios-readiness-02-') as raw:
            root = Path(raw).resolve()
            rendered = artifacts.render_owned_native_launch(source, root)
            self.assertIn('private let nativeReadinessSigningMode = "disabled"', rendered)
            adhoc = artifacts.render_owned_native_launch(source, root, 'adhoc')
            self.assertIn('private let nativeReadinessSigningMode = "adhoc"', adhoc)
            with self.assertRaises(RuntimeError): artifacts.render_owned_native_launch(source, root, 'Store')
            self.assertIn('private let nativeReadinessTaskRoot = ' + json.dumps(str(root)), rendered)
            self.assertIn('private let nativeReadinessProductRoot = ' + json.dumps(str(root / 'copy/composeApp/build')), rendered)
            self.assertNotIn('__PARLOR_NATIVE_', rendered)
            self.assertIn('options: [.withoutOverwriting]', rendered)
            self.assertNotIn('print("PARLOR_NATIVE_SYNTHETIC_SEED_CLEANUP', rendered)
            for invalid in (source.replace('__PARLOR_NATIVE_TASK_ROOT__', '"/unbound"'),
                            source + '__PARLOR_NATIVE_PRODUCT_ROOT__'):
                with self.assertRaises(RuntimeError): artifacts.render_owned_native_launch(invalid, root)
            with self.assertRaises(RuntimeError): artifacts.render_owned_native_launch(source, root / 'unbound-child')

    def test_loader_environment_retains_only_bounded_task_relative_metadata(self):
        value = loader_environment()
        value['DYLD_FRAMEWORK_PATH'] = dict(present=True, task_owned_paths=['copy/composeApp/build'], redacted_entry_count=2)
        self.assertEqual(artifacts.validate_loader_environment(value), value)
        for row in (dict(present=True, task_owned_paths=['/outside'], redacted_entry_count=0),
                    dict(present=True, task_owned_paths=['../outside'], redacted_entry_count=0),
                    dict(present=False, task_owned_paths=['copy'], redacted_entry_count=0),
                    dict(present=True, task_owned_paths=[], redacted_entry_count=True),
                    dict(present=True, task_owned_paths=[], redacted_entry_count=33),
                    dict(present=True, task_owned_paths=['x' * 513], redacted_entry_count=0)):
            broken = loader_environment(); broken['DYLD_FRAMEWORK_PATH'] = row
            with self.assertRaises(RuntimeError): artifacts.validate_loader_environment(broken)


class ExactJavaOwnershipTests(unittest.TestCase):
    def test_only_one_exact_java_tmpdir_argument_can_adopt_a_detached_worker(self):
        root = Path('/owned/parlor-cycle'); argument = '-Djava.io.tmpdir=' + str(root / 'tmp')
        self.assertTrue(owns_java_tmp_argument('/jdk/bin/java ' + argument + ' Worker', root))
        for command in ('/jdk/bin/java ' + argument + '-sibling Worker',
                        '/bin/echo /jdk/bin/java ' + argument,
                        '/jdk/bin/java ' + argument + ' -Djava.io.tmpdir=/another/task',
                        '/jdk/bin/java ' + argument + ' ' + argument,
                        '/jdk/bin/java "' + argument,
                        '/jdk/bin/java --message=' + argument):
            with self.subTest(command=command): self.assertFalse(owns_java_tmp_argument(command, root))


if __name__ == '__main__':
    unittest.main()
