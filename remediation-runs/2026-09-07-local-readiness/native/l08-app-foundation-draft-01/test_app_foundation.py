"""Unexecuted draft controls. No compiler, process action, Simulator or application launch.

Native/Swift source guards below are not compiled or device evidence. Only root's
shared lane may run these synthetic parser/copy tests after independent review.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import stat
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


adaptor = module('app_foundation_copy_control', 'app_foundation_copy.py')
schema = module('app_foundation_receipts_control', 'app_foundation_receipts.py')
TOKEN = '00000001-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
BOOT = '00000002-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
DEVICE = '00000003-cccc-cccc-cccc-cccccccccccc'
CONTAINER = '00000004-dddd-dddd-dddd-dddddddddddd'
MAIN = '00000005-eeee-eeee-eeee-eeeeeeeeeeee'
CODE = '00000006-ffff-ffff-ffff-ffffffffffff'
FOUNDATION = '00000007-aaaa-ffff-aaaa-ffffffffffff'


def query(value='missing'):
    return dict(returned=True, dictionary_present=True, key_present=value != 'missing',
                value=value, exception='none', native_error=dict(present=False, domain='none', code=0))


def fixture():
    result = dict(schema_version=1, kind='l08-app-foundation', run_token=TOKEN, process_boot=BOOT,
        process_id=40001, simulator_udid=DEVICE, scenario='readiness', boot_ordinal=1,
        fixture_template_sha256='a' * 64, context_sha256='b' * 64, controls_sha256='c' * 64,
        signing_mode='adhoc', simulator_only=True, app_container=True,
        hardware_protection_verified=False, l08_requirements_waived=False,
        container=dict(status='observed', id=CONTAINER, device=2, inode=30001, uid=os.getuid()),
        main_image=dict(status='observed', uuid=MAIN, platform=7),
        fixture_image=dict(status='observed', uuid=CODE, platform=7, app_relative='Parlor.debug.dylib'),
        foundation_image=dict(status='observed', uuid=FOUNDATION, platform=7),
        runtime_version=[26, 5, 0], rows=[], status='OBSERVATIONS_COMPLETE',
        fixture_cleanup=dict(status='REMOVED', created=True, root_pinned=True,
                             directory_pinned=True, root_removed=True, reason='none'))
    for name, kind in schema.base_validator().ROW_KINDS:
        if kind == 'operation':
            row = dict(id=name, kind='operation', returned=True, result=True, exception='none',
                       native_error=dict(present=False, domain='none', code=0))
        else:
            row = dict(id=name, kind='observation', fm=query(), volume=query('supported'), backup=query('excluded'))
            if kind == 'file':
                row['url'] = query('until-first-authentication')
        result['rows'].append(row)
    return result


def encoded(value):
    return json.dumps(value, separators=(',', ':')).encode()


def binding():
    return dict(fixture_template_sha256='a' * 64, context_sha256='b' * 64, controls_sha256='c' * 64, mode='adhoc',
                toolchain_profile=schema.toolchains.LOCAL, expected_runtime_version=[26, 5, 0])


def inventories():
    built = dict(bundle_identity=dict(CFBundleIdentifier='com.parlor.app.debug', CFBundleExecutable='Parlor'),
        images=[dict(path=name, resolved_path=name, sha256=digest, bytes=4096)
                for name, digest in (('Parlor', 'd' * 64), ('Parlor.debug.dylib', 'e' * 64))])
    uuids = [dict(path=name, sha256=digest, architectures=[dict(architecture='arm64', uuid=uuid)])
             for name, digest, uuid in (('Parlor', 'd' * 64, MAIN), ('Parlor.debug.dylib', 'e' * 64, CODE))]
    return built, copy.deepcopy(built), uuids


def log(value=None):
    value = value or fixture()
    return schema.PREFIX + json.dumps(dict(schema_version=1, kind='l08-app-foundation-xctest',
        run_token=value['run_token'], process_boot=value['process_boot'], process_id=value['process_id'],
        boot_ordinal=1, activations=1, foreground=True, alert_present=False,
        **{key: value[key] for key in ('fixture_template_sha256', 'context_sha256', 'controls_sha256')}))


def bind(value=None, *, bindings=None, built=None, installed=None, uuids=None, xctest=None, **extra):
    defaults = inventories()
    return schema.bind_collection(value or fixture(), bindings or binding(),
        defaults[0] if built is None else built, defaults[1] if installed is None else installed,
        defaults[2] if uuids is None else uuids, log() if xctest is None else xctest, DEVICE, TOKEN, **extra)


class SchemaControls(unittest.TestCase):
    def rejects(self, value):
        with self.assertRaises(RuntimeError):
            schema.parse_record(encoded(value))

    def test_missing_complete_metadata_never_satisfies_l08(self):
        value = schema.parse_record(encoded(fixture()))
        self.assertEqual(value['rows'][1]['fm']['value'], 'missing')
        self.assertEqual(value['rows'][5]['url']['value'], 'until-first-authentication')
        result = bind(value)
        self.assertEqual(result['status'], 'COLLECTION_VALIDATED_NOT_L08_PASS')
        self.assertEqual(result['strict_l08'], 'UNCHANGED_NOT_SATISFIED')
        self.assertFalse(result['physical_protection_verified'])

    def test_false_operations_errors_and_exceptions_remain_observed(self):
        value = fixture()
        value['rows'][4].update(result=False, native_error=dict(present=True, domain='posix', code=28))
        result = schema.parse_record(encoded(value))
        self.assertFalse(result['rows'][4]['result'])
        self.assertEqual(result['rows'][4]['native_error']['code'], 28)
        value['rows'][4].update(returned=False, exception='objc-exception',
                                native_error=dict(present=False, domain='none', code=0))
        self.assertFalse(schema.parse_record(encoded(value))['rows'][4]['returned'])
        value['rows'][4]['result'] = True
        self.rejects(value)

    def test_every_boolean_field_rejects_numeric_zero_and_one(self):
        base = fixture()
        slots = []
        def visit(value, path=()):
            if type(value) is bool:
                slots.append(path)
            elif isinstance(value, dict):
                for key, item in value.items(): visit(item, path + (key,))
            elif isinstance(value, list):
                for index, item in enumerate(value): visit(item, path + (index,))
        visit(base)
        self.assertGreater(len(slots), 144)
        for path in slots:
            for number in (0, 1):
                value = copy.deepcopy(base)
                current = value
                for part in path[:-1]: current = current[part]
                current[path[-1]] = number
                with self.subTest(path=path, number=number): self.rejects(value)

    def test_claims_context_runtime_and_unknown_fields_are_strict(self):
        for key, replacement in (('app_container', False), ('simulator_only', False),
            ('l08_requirements_waived', True), ('hardware_protection_verified', True), ('boot_ordinal', True),
            ('boot_ordinal', 2), ('scenario', 'l08-storage'), ('run_token', 'not-a-token'),
            ('runtime_version', [26, 4, 0]), ('runtime_version', [26, 5, False]),
            ('fixture_template_sha256', 'unknown'), ('status', 'PASS'), ('signing_mode', 'store')):
            value = fixture(); value[key] = replacement
            with self.subTest(key=key, replacement=replacement): self.rejects(value)
        value = fixture(); value['native_description'] = 'never allowed'
        self.rejects(value)

    def test_rows_are_complete_unique_ordered_and_cannot_query_directory_url_class(self):
        for change in ('missing', 'duplicate', 'reorder', 'directory-url'):
            value = fixture()
            if change == 'missing': value['rows'].pop()
            elif change == 'duplicate': value['rows'].append(value['rows'][0])
            elif change == 'reorder': value['rows'][0], value['rows'][1] = value['rows'][1], value['rows'][0]
            else: value['rows'][1]['url'] = query('complete')
            with self.subTest(change=change): self.rejects(value)

    def test_full_native_dicts_paths_payloads_and_error_descriptions_cannot_enter_receipt(self):
        for extra in ('path', 'attributes', 'payload', 'userInfo', 'localizedDescription'):
            value = fixture(); value['rows'][1]['fm'][extra] = 'UNRELATED'
            with self.subTest(field=extra): self.rejects(value)
        value = fixture(); value['fixture_image']['app_relative'] = '../another/Parlor.debug.dylib'
        self.rejects(value)

    def test_duplicate_nonfinite_malformed_and_oversized_json_is_rejected(self):
        for raw in (b'{"kind":"x","kind":"y"}', b'{"x":NaN}', b'\xff', b' ' * 32769):
            with self.subTest(raw_prefix=raw[:16]), self.assertRaises((RuntimeError, ValueError, UnicodeError)):
                schema.parse_record(raw)

    def test_partial_error_receipt_is_preservable_not_collection_success(self):
        value = fixture()
        value.update(status='CONTROL_ERROR', reason='entry-identity', rows=value['rows'][:5])
        value['fixture_cleanup'].update(status='FAILED', root_removed=False, reason='cleanup-file-identity')
        self.assertEqual(schema.parse_record(encoded(value))['status'], 'CONTROL_ERROR')
        with self.assertRaisesRegex(RuntimeError, 'incomplete-native-control'): bind(value)
        value['reason'] = '/private/not-allowed'
        self.rejects(value)

    def test_inner_cleanup_failure_cannot_be_collection_validated(self):
        value = fixture(); value['fixture_cleanup'].update(status='FAILED', reason='cleanup-descriptor-close')
        self.assertTrue(schema.parse_record(encoded(value))['fixture_cleanup']['root_removed'])
        with self.assertRaisesRegex(RuntimeError, 'incomplete-native-control'): bind(value)
        for key in ('created', 'root_pinned', 'root_removed'):
            bad = fixture(); bad['fixture_cleanup'][key] = False
            self.rejects(bad)

    def test_unavailable_image_preserves_closed_error_rows_without_collection_success(self):
        for field in ('main_image', 'fixture_image', 'foundation_image'):
            for image in ({'status': 'unavailable'}, {**fixture()[field], 'platform': 2}):
                value = fixture()
                value.update(status='CONTROL_ERROR', reason='image-context-unavailable')
                value[field] = image
                with self.subTest(field=field, image=image):
                    self.assertEqual(schema.parse_record(encoded(value))['rows'], fixture()['rows'])
                    with self.assertRaisesRegex(RuntimeError, 'incomplete-native-control'): bind(value)
                    value.pop('reason'); value['status'] = 'OBSERVATIONS_COMPLETE'
                    self.rejects(value)

    def test_cross_control_source_token_image_or_install_fails_binding(self):
        for field in ('fixture_template_sha256', 'context_sha256', 'controls_sha256'):
            bad = binding(); bad[field] = '0' * 64
            with self.subTest(field=field), self.assertRaises(RuntimeError): bind(bindings=bad)
        value = fixture(); value['run_token'] = BOOT
        with self.assertRaises(RuntimeError): bind(value)
        value = fixture(); value['simulator_udid'] = BOOT
        with self.assertRaises(RuntimeError): bind(value)
        value = fixture(); value['fixture_image']['uuid'] = MAIN
        with self.assertRaises(RuntimeError): bind(value)
        built, installed, uuids = inventories(); installed['images'][0]['sha256'] = 'f' * 64
        with self.assertRaises(RuntimeError): bind(built=built, installed=installed, uuids=uuids)

    def test_missing_duplicate_wrong_architecture_or_digest_uuid_inventory_rejected(self):
        for change in ('missing', 'duplicate', 'arch', 'hash'):
            built, installed, uuids = inventories()
            if change == 'missing': uuids.pop()
            elif change == 'duplicate': uuids.append(copy.deepcopy(uuids[0]))
            elif change == 'arch': uuids[0]['architectures'][0]['architecture'] = 'x86_64'
            else: uuids[0]['sha256'] = 'f' * 64
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                bind(built=built, installed=installed, uuids=uuids)

    def test_wrong_debug_identity_and_conflicting_native_aliases_fail_binding(self):
        for field, replacement in (('CFBundleIdentifier', 'com.parlor.app'), ('CFBundleExecutable', 'AnotherApp')):
            built, _, uuids = inventories()
            built['bundle_identity'][field] = replacement
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, 'built-debug-identity'):
                bind(built=built, installed=copy.deepcopy(built), uuids=uuids)
        built, _, uuids = inventories()
        built['images'].append({**built['images'][0], 'sha256': 'f' * 64})
        with self.assertRaisesRegex(RuntimeError, 'conflicting-native-alias'):
            bind(built=built, installed=copy.deepcopy(built), uuids=uuids)

    def test_xctest_receipt_must_execute_once_with_exact_primitive_types(self):
        for text in ('', log() + '\n' + log(), log().replace('"activations": 1', '"activations": 2'),
                     log().replace('"foreground": true', '"foreground": 1'),
                     log().replace('"boot_ordinal": 1', '"boot_ordinal": true')):
            with self.subTest(prefix=text[:120]), self.assertRaises(RuntimeError): bind(xctest=text)

    def test_first_health_cross_check_never_substitutes_a_different_boot_or_process(self):
        health = dict(boot_ordinal=1, run_token=TOKEN, process_boot=BOOT, process_id=40001, signing_mode='adhoc')
        self.assertTrue(bind(first_health=health)['first_health_process_cross_checked'])
        for key, wrong in (('process_id', 40002), ('process_boot', TOKEN), ('boot_ordinal', 2), ('signing_mode', 'disabled')):
            bad = {**health, key: wrong}
            with self.subTest(key=key), self.assertRaises(RuntimeError): bind(first_health=bad)

    def test_closed_delivery_failure_never_becomes_native_observation(self):
        failure = dict(schema_version=1, kind='l08-app-foundation-delivery-failure', stage='native-delivery',
            run_token=TOKEN, process_boot=BOOT, simulator_udid=DEVICE, process_id=40001, boot_ordinal=1,
            scenario='readiness', l08_requirements_waived=False, hardware_protection_verified=False)
        self.assertEqual(schema.parse_delivery_failure(encoded(failure)), failure)
        with self.assertRaises(RuntimeError): schema.parse_record(encoded(failure))
        failure['description'] = 'not allowed'
        with self.assertRaises(RuntimeError): schema.parse_delivery_failure(encoded(failure))


class SourceControls(unittest.TestCase):
    def test_main_image_selects_exact_bundle_executable_with_no_index_zero_fallback(self):
        # Executable source contracts, not native dyld/thread-safety evidence.
        source = (HERE / 'L08AppFoundation.m.in').read_text()
        selected = re.search(r'static NSDictionary \*mainImage\(void\) \{.*?\n\}', source, re.S).group()
        guards = ('[NSBundle mainBundle].executablePath',
                  'canonicalImagePath(supplied.fileSystemRepresentation)',
                  'requireControl(executable != nil, @"main-executable-unavailable")',
                  '[executable isEqual:[appBundlePath stringByAppendingPathComponent:@"Parlor"]]',
                  'count > 0 && count <= 4096', 'index < count',
                  '[canonicalImagePath(_dyld_get_image_name(index)) isEqual:executable]',
                  'requireControl(selected == NULL, @"main-image-ambiguous")',
                  'selected = _dyld_get_image_header(index)',
                  'requireControl(selected != NULL, @"main-image-header")',
                  'requireControl(selected != NULL, @"main-image-missing")',
                  '_dyld_image_count() == count', '_dyld_get_image_header(selectedIndex) == selected',
                  '[canonicalImagePath(_dyld_get_image_name(selectedIndex)) isEqual:executable]',
                  'dladdr(selected, &info) != 0 && info.dli_fbase == selected',
                  '[canonicalImagePath(info.dli_fname) isEqual:executable]',
                  'NSDictionary *identity = imageIdentity(selected)', 'return identity;')
        def check(value):
            for guard in guards: self.assertIn(guard, value)
            self.assertEqual(value.count('@"main-image-changed"'), 2)
            self.assertLess(value.index('dladdr(selected, &info)'), value.index('imageIdentity(selected)'))
        check(selected)
        for guard in guards:
            with self.subTest(guard=guard), self.assertRaises(AssertionError): check(selected.replace(guard, 'MUTATED'))
        self.assertNotIn('_dyld_get_image_header(0)', source)
        self.assertIn('receipt[@"main_image"] = mainImage();', source)
        self.assertIn('strnlen(path, PATH_MAX) >= PATH_MAX', source)
        self.assertIn('Bracketed checks are not an atomic loader reservation.', source)

    def test_main_selection_failures_are_preservable_only_as_closed_control_errors(self):
        reasons = {'main-executable-unavailable', 'main-executable-path', 'main-image-count',
                   'main-image-ambiguous', 'main-image-header', 'main-image-missing',
                   'main-image-changed', 'main-image-address'}
        for reason in reasons:
            value = fixture()
            value.update(status='CONTROL_ERROR', reason=reason, rows=[], main_image={'status': 'unavailable'},
                fixture_cleanup=dict(status='NOT_CREATED', created=False, root_pinned=False,
                                     directory_pinned=False, root_removed=False, reason='none'))
            with self.subTest(reason=reason):
                self.assertEqual(schema.parse_record(encoded(value))['reason'], reason)
                with self.assertRaisesRegex(RuntimeError, 'incomplete-native-control'): bind(value)
                value['reason'] = reason + '/PRIVATE_PATH'
                with self.assertRaises(RuntimeError): schema.parse_record(encoded(value))

    def test_wrong_main_uuid_still_fails_with_correct_fixture_and_foundation(self):
        value = fixture()
        value['main_image']['uuid'] = '7af2e14e-10e0-3a05-a5df-9775c2c6a88e'
        self.assertEqual(value['fixture_image'], fixture()['fixture_image'])
        self.assertEqual(value['foundation_image'], fixture()['foundation_image'])
        with self.assertRaisesRegex(RuntimeError, 'native-image-uuid-or-hash'): bind(value)
        self.assertEqual(bind()['status'], 'COLLECTION_VALIDATED_NOT_L08_PASS')

    def test_control03_operation_query_and_image_helpers_remain_exact(self):
        original_path = adaptor.CAMPAIGN / 'reviews/l08-native-foundation-control-03/FoundationProtectionControl.m'
        self.assertEqual(adaptor.digest(original_path), 'a6b025d7b77c3b1b1de57b93869d0beff6b4cc9e365eb5672ffd2be9d52013f2')
        original, adapted = original_path.read_text(), (HERE / 'L08AppFoundation.m.in').read_text()
        for name in ('nativeError', 'operation', 'protectionName', 'query', 'observe', 'imageIdentity', 'foundationIdentity'):
            pattern = r'static [^\n]*\b' + name + r'\([^\n]*\) \{.*?\n\}'
            before = re.search(pattern, original, re.S)
            after = re.search(pattern, adapted, re.S)
            self.assertIsNotNone(before, name); self.assertIsNotNone(after, name)
            self.assertEqual(before.group(), after.group(), name)
        self.assertEqual(re.findall(r'(?:operation|observe)\(@"([^"]+)"', original),
                         re.findall(r'(?:operation|observe)\(@"([^"]+)"', adapted))

    def test_native_guard_no_process_global_override_or_production_storage_api(self):
        native = (HERE / 'L08AppFoundation.m.in').read_text()
        for forbidden in ('umask(', 'alarm(', 'signal(', 'SecItem', 'NSUserDefaults', 'NSDocumentDirectory',
                          'Parlor/snapshots', 'Documents/snapshots', 'removeItemAtPath:', 'contentsOfDirectoryAtPath:'):
            self.assertNotIn(forbidden, native)
        self.assertIn('!defined(DEBUG) || !DEBUG || !TARGET_OS_SIMULATOR || !TARGET_OS_IOS || !defined(__arm64__)', native)
        self.assertIn('atomic_flag_test_and_set_explicit(&invocationUsed, memory_order_relaxed)', native)
        self.assertIn('requireControl(![NSThread isMainThread]', native)
        self.assertIn('[environment[@"PARLOR_READINESS_BOOT"] isEqual:@"1"]', native)
        self.assertIn('ParlorFoundationControl-%@-%@', native)

    def test_native_closed_reason_schema_covers_every_guard_without_raw_messages(self):
        source = (HERE / 'L08AppFoundation.m.in').read_text()
        reasons = set(re.findall(r'requireControl\(.*?,\s*@"([a-z0-9-]+)"\);', source, re.S))
        self.assertEqual(reasons | {'objc-exception', 'cleanup-descriptor-close'}, schema.REASONS)
        self.assertNotIn('exception.description', source)
        self.assertNotIn('error.description', source)
        self.assertLess(source.index('            collect();'), source.index('@"image-context-unavailable"'))
        self.assertLess(source.index('@"image-context-unavailable"'),
                        source.index('receipt[@"status"] = @"OBSERVATIONS_COMPLETE"'))

    def test_native_cleanup_static_guards_reject_individual_policy_mutations(self):
        # Source assertion, NOT native failure-injection or fd-lifecycle execution.
        source = (HERE / 'L08AppFoundation.m.in').read_text()
        guards = ('mkdirat(supportDescriptor, fixtureName.fileSystemRepresentation, 0700) == 0',
                  'rootCreated = YES;', 'rootPinned = YES;', 'value.st_ino == rootInode',
                  'value.st_ino == directoryInode', 'value.st_ino == fileIdentities[index].st_ino',
                  'value.st_nlink == 1', 'value.st_size <= 256',
                  'unlinkat(childFD, leaves[index], 0)', 'unlinkat(rootFD, "created", AT_REMOVEDIR)',
                  'unlinkat(supportDescriptor, fixtureName.fileSystemRepresentation, AT_REMOVEDIR)',
                  '@finally {', 'cleanupFixture();', 'descriptorCloseFailed')
        def check(value):
            for guard in guards: self.assertIn(guard, value)
        check(source)
        for guard in guards:
            with self.subTest(guard=guard), self.assertRaises(AssertionError): check(source.replace(guard, 'MUTATED'))

    def test_bridge_wires_only_app_debug_and_preserves_original_project_sections(self):
        project = (adaptor.ROOT / adaptor.PROJECT).read_text()
        rendered = adaptor.transform_project(project)
        self.assertEqual(rendered.count('SWIFT_OBJC_BRIDGING_HEADER = '), 1)
        app_debug = rendered.split('7555FF90242A565B00829871 /* Debug */ = {', 1)[1].split('\n\t\t};', 1)[0]
        self.assertIn('SWIFT_OBJC_BRIDGING_HEADER', app_debug)
        for header in ('PBXShellScriptBuildPhase', 'PBXFrameworksBuildPhase', 'PBXResourcesBuildPhase'):
            pattern = '/* Begin ' + header + ' section */'
            end = '/* End ' + header + ' section */'
            self.assertEqual(project.split(pattern)[1].split(end)[0], rendered.split(pattern)[1].split(end)[0])
        with self.assertRaises(RuntimeError): adaptor.transform_project(rendered)

    def test_hooks_preserve_existing_loop_method_and_one_activation_counts(self):
        content = (adaptor.COMPANION / 'DSC01Probe.swift.in').read_text()
        ui = '\n'.join((adaptor.COMPANION / name).read_text() for name in (
            'IOSAppLaunchUITests.swift.in', 'NativeReadinessUITests.swift.in',
            'L08StorageFunctionalUITests.swift.in', 'L08HostUITests.swift.in'))
        rendered = adaptor.transform_content(content, (HERE / 'L08AppFoundationLaunch.swift.in').read_text())
        tests = adaptor.transform_tests(ui, (HERE / 'L08AppFoundationUITests.swift.in').read_text())
        self.assertEqual(tests.count('if ordinal == 1 { try verifyL08AppFoundation(app, boot: boot) }'), 1)
        self.assertEqual(ui.count('for ordinal in 1...8'), tests.count('for ordinal in 1...8'))
        self.assertEqual(re.findall(r'func (test\w+)\(', ui), re.findall(r'func (test\w+)\(', tests))
        container_tests = (adaptor.ROOT / 'iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift').read_text()
        self.assertEqual(len(re.findall(r'func (test\w+)\(', tests + container_tests)), 5)
        self.assertLess(tests.index('try verifyL08AppFoundation(app'),
                        tests.index('let run = app.buttons["parlor-native-readiness-run"]'))
        self.assertEqual(tests.count('control.tap()') - ui.count('control.tap()'), 1)
        for unchanged in ('L08StorageFunctionalUITests.swift.in', 'L08HostUITests.swift.in'):
            self.assertIn((adaptor.COMPANION / unchanged).read_text(), tests)
        self.assertIn('DispatchQueue.global(qos: .utility).async', rendered)
        self.assertIn('options: [.withoutOverwriting]', rendered)
        with self.assertRaises(RuntimeError): adaptor.transform_content(rendered, '')
        with self.assertRaises(RuntimeError): adaptor.transform_tests(tests, '')


class OwnedCopyControls(unittest.TestCase):
    def setUp(self): self.owned = []

    def temporary(self):
        path = Path(tempfile.mkdtemp(prefix='parlor-audit-ios-readiness-99-synthetic-')).resolve()
        value = path.lstat()
        self.owned.append((path, value.st_dev, value.st_ino, value.st_uid))
        return path

    def tearDown(self):
        for path, device, inode, uid in reversed(self.owned):
            value = path.lstat()
            self.assertEqual((value.st_dev, value.st_ino, value.st_uid), (device, inode, uid))
            self.assertTrue(stat.S_ISDIR(value.st_mode) and not path.is_symlink())
            shutil.rmtree(path)  # Fresh synthetic root whose exact ownership was captured on creation.

    def setup_copy(self):
        temporary = self.temporary(); root = temporary / 'copy'; root.mkdir(mode=0o700)
        originals = {adaptor.PROJECT: (adaptor.ROOT / adaptor.PROJECT).read_text(),
                     adaptor.CONTENT: (adaptor.COMPANION / 'DSC01Probe.swift.in').read_text(),
                     adaptor.TESTS: (adaptor.COMPANION / 'NativeReadinessUITests.swift.in').read_text()}
        before = []
        for name, data in originals.items():
            target = root / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(data)
            digest = adaptor.digest(target)
            before.append(dict(path=name, original_sha256=digest, copied_sha256=digest))
        value = temporary.lstat()
        custody = dict(path=str(temporary), device=value.st_dev, inode=value.st_ino, uid=value.st_uid)
        home = self.temporary(); device_set = home / 'Library/Developer/CoreSimulator/Devices'; device_set.mkdir(parents=True)
        entries = [['synthetic-input.kt', 'a' * 64]]
        context = dict(source_manifest=entries, source_manifest_sha256=hashlib.sha256(
            json.dumps(entries, separators=(',', ':')).encode()).hexdigest())
        return root, before, custody, context, home, device_set

    def apply(self, item):
        root, before, custody, context, home, device_set = item
        with patch.object(adaptor.Path, 'home', return_value=home):
            return adaptor.apply_owned_adapter(root, before, custody, context, 'c' * 64, 'adhoc', device_set)

    def test_copy_adapter_adds_only_two_files_and_three_exact_changes(self):
        item = self.setup_copy()
        result, proof = self.apply(item)
        self.assertEqual(len(result), 5)
        self.assertEqual({row['path'] for row in result} - {row['path'] for row in item[1]}, set(adaptor.ADDITIONS))
        self.assertEqual(proof['execution'], 'NOT_RUN')
        self.assertEqual(proof['original_l08'], 'UNCHANGED_NOT_SATISFIED')
        self.assertEqual(proof['context_sha256'], adaptor.context_digest(item[3]))
        self.assertEqual(proof['native_copy_sha256'], adaptor.digest(item[0] / adaptor.NATIVE))
        self.assertNotIn('__L08_', (item[0] / adaptor.NATIVE).read_text())
        with self.assertRaises(RuntimeError): self.apply(item)

    def test_source_custody_hash_drift_and_extra_inputs_fail_before_any_addition(self):
        for scenario in ('inode', 'hash', 'extra', 'symlink'):
            item = list(self.setup_copy())
            if scenario == 'inode': item[2] = {**item[2], 'inode': item[2]['inode'] + 1}
            elif scenario == 'hash': (item[0] / adaptor.CONTENT).write_text('changed')
            elif scenario == 'extra': (item[0] / 'untracked.kt').write_text('source')
            else:
                target = item[0] / adaptor.CONTENT
                target.unlink(); target.symlink_to(item[0] / adaptor.TESTS)
            with self.subTest(scenario=scenario), self.assertRaises(RuntimeError): self.apply(item)
            self.assertFalse((item[0] / adaptor.NATIVE).exists())

    def test_hardlinked_inherited_source_fails_before_copy_mutation(self):
        item = self.setup_copy()
        source = item[0] / adaptor.CONTENT
        sentinel = item[0].parent / 'synthetic-hardlink-sentinel'
        os.link(source, sentinel)
        before = sentinel.read_bytes()
        with self.assertRaisesRegex(RuntimeError, 'inherited-copy-changed'): self.apply(item)
        self.assertEqual(sentinel.read_bytes(), before)
        self.assertEqual(source.read_bytes(), before)
        self.assertFalse((item[0] / adaptor.NATIVE).exists())

    def test_foreign_source_root_and_preexisting_adapter_are_never_overwritten(self):
        item = list(self.setup_copy())
        original = (item[0] / adaptor.PROJECT).read_bytes()
        target = item[0] / adaptor.NATIVE; target.write_text('pre-existing fixture sentinel')
        with self.assertRaises(RuntimeError): self.apply(item)
        self.assertEqual(target.read_text(), 'pre-existing fixture sentinel')
        self.assertEqual((item[0] / adaptor.PROJECT).read_bytes(), original)
        item[0] = adaptor.ROOT
        with self.assertRaises(RuntimeError): self.apply(item)

    def test_malformed_compile_hash_or_device_root_cannot_write_copy(self):
        item = self.setup_copy()
        template = (HERE / 'L08AppFoundation.m.in').read_text()
        with patch.object(adaptor.Path, 'home', return_value=item[4]):
            for context, controls, mode, device in (('bad', 'c' * 64, 'adhoc', item[5]),
                ('b' * 64, 'c' * 64, 'store', item[5]), ('b' * 64, 'c' * 64, 'adhoc', item[4])):
                with self.subTest(mode=mode, device=str(device)), self.assertRaises(RuntimeError):
                    adaptor.render_native(template, context, controls, mode, device)
        self.assertFalse((item[0] / adaptor.NATIVE).exists())

    def test_owned_receipt_rejects_symlink_hardlink_oversize_and_traversal(self):
        parent = self.temporary(); path = parent / schema.RESULT_NAME
        path.write_bytes(encoded(fixture()))
        self.assertEqual(schema.read_owned_receipt(path, parent, 32768), encoded(fixture()))
        other = parent / 'other'; os.link(path, other)
        with self.assertRaises(RuntimeError): schema.read_owned_receipt(path, parent, 32768)
        other.unlink(); path.unlink(); path.symlink_to(other)
        with self.assertRaises(RuntimeError): schema.read_owned_receipt(path, parent, 32768)
        path.unlink(); path.write_bytes(b' ' * 32769)
        with self.assertRaises(RuntimeError): schema.read_owned_receipt(path, parent, 32768)
        with self.assertRaises(RuntimeError): schema.read_owned_receipt(path, parent.parent, 32768)

    def test_partial_available_receipts_preserved_before_later_abort_without_overwrite(self):
        home = self.temporary(); container = home / 'Library/Developer/CoreSimulator/Devices' / DEVICE / 'data/Containers/Data/Application' / CONTAINER
        (container / 'tmp').mkdir(parents=True); evidence = self.temporary()
        value = fixture(); native = container.lstat()
        value['container'] = dict(status='observed', id=CONTAINER, device=native.st_dev, inode=native.st_ino, uid=native.st_uid)
        value.update(status='CONTROL_ERROR', reason='entry-identity', rows=value['rows'][:1])
        value['fixture_cleanup'] = dict(status='NOT_CREATED', created=False, root_pinned=False,
                                       directory_pinned=False, root_removed=False, reason='none')
        path = container / 'tmp' / schema.RESULT_NAME; path.write_bytes(encoded(value))
        with patch.object(schema.Path, 'home', return_value=home):
            names = schema.preserve_available(container, evidence, DEVICE, TOKEN)
            self.assertEqual(names, [schema.RESULT_NAME])
            self.assertEqual((evidence / schema.RESULT_NAME).read_bytes(), encoded(value))
            with self.assertRaises(FileExistsError): schema.preserve_available(container, evidence, DEVICE, TOKEN)
            with self.assertRaises(RuntimeError): schema.preserve_available(container, evidence, DEVICE, BOOT)

    def test_removed_synthetic_root_claim_is_checked_before_preserving(self):
        home = self.temporary()
        container = home / 'Library/Developer/CoreSimulator/Devices' / DEVICE / 'data/Containers/Data/Application' / CONTAINER
        (container / 'tmp').mkdir(parents=True); evidence = self.temporary()
        value = fixture(); native = container.lstat()
        value['container'] = dict(status='observed', id=CONTAINER, device=native.st_dev, inode=native.st_ino, uid=native.st_uid)
        synthetic = container / 'Library/Application Support' / ('ParlorFoundationControl-' + TOKEN.lower() + '-' + BOOT.lower())
        synthetic.mkdir(parents=True)
        (container / 'tmp' / schema.RESULT_NAME).write_bytes(encoded(value))
        with patch.object(schema.Path, 'home', return_value=home):
            with self.assertRaisesRegex(RuntimeError, 'synthetic-root-still-present'):
                schema.preserve_available(container, evidence, DEVICE, TOKEN)
        self.assertTrue(synthetic.is_dir())  # Parser cannot clean/adopt any fixture path.
        self.assertEqual(list(evidence.iterdir()), [])


def load_tests(_loader, tests, _pattern):
    # Fixed executable discovery guard. Updating tests requires a reviewed count update.
    if tests.countTestCases() != 33:
        raise RuntimeError('App Foundation synthetic discovery count differs from reviewed 33')
    return tests


if __name__ == '__main__':
    unittest.main()
