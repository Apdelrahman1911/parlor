"""Pure paired-profile controls; never executes a native runner, compiler or device."""
import ast
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import signal
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
COMPOSITION = ROOT / 'remediation-runs/2026-09-07-local-readiness/native/l08-app-foundation-composition-01'
specification = importlib.util.spec_from_file_location(
    'foundation_toolchain_composition_controls', COMPOSITION / 'test_composition.py')
composition_controls = importlib.util.module_from_spec(specification)
specification.loader.exec_module(composition_controls)
composition = composition_controls.composition
fixtures = composition_controls.fixtures
adaptor, schema = fixtures.adaptor, fixtures.schema
profiles = schema.toolchains
EXPECTED_TESTS = 20
PROFILES = ((profiles.LOCAL, [26, 5, 0]), (profiles.QUALIFIED, [26, 2, 0]))
LOCAL_GUARD = 'version.majorVersion == 26 && version.minorVersion == 5 && version.patchVersion == 0'
QUALIFIED_GUARD = 'version.majorVersion == 26 && version.minorVersion == 2 && version.patchVersion == 0'


def selected_fixture(name, runtime):
    value, binding = fixtures.fixture(), fixtures.binding()
    value['runtime_version'] = list(runtime)
    binding.update(toolchain_profile=name, expected_runtime_version=list(runtime))
    return value, binding


class FoundationToolchainControls(unittest.TestCase):
    def copy_fixture(self):
        helper = fixtures.OwnedCopyControls(methodName='runTest')
        helper.setUp()
        self.addCleanup(helper.tearDown)
        return helper.setup_copy()

    def render(self, item, name=None, template=None):
        original = (adaptor.HERE / 'L08AppFoundation.m.in').read_text() if template is None else template
        with patch.object(adaptor.Path, 'home', return_value=item[4]):
            options = {} if name is None else {'toolchain': name}
            return adaptor.render_native(original, 'b' * 64, 'c' * 64, 'adhoc', item[5], **options)

    def test_closed_profiles_bind_exact_numeric_runtime_and_return_fresh_values(self):
        for name, runtime in PROFILES:
            with self.subTest(profile=name):
                actual = profiles.profile(name)
                self.assertEqual(actual['runtime_version'], runtime)
                self.assertTrue(all(type(part) is int for part in actual['runtime_version']))
                self.assertEqual(actual['runtime'], 'com.apple.CoreSimulator.SimRuntime.iOS-%d-%d' % tuple(runtime[:2]))
                actual['runtime_version'][2] = 99
                self.assertEqual(profiles.profile(name)['runtime_version'], runtime)

    def test_qualified_render_changes_only_exact_guard_from_local_render(self):
        item = self.copy_fixture()
        local, local_hash = self.render(item, profiles.LOCAL)
        qualified, qualified_hash = self.render(item, profiles.QUALIFIED)
        self.assertEqual(local.count(LOCAL_GUARD), 1)
        self.assertEqual(qualified.count(QUALIFIED_GUARD), 1)
        self.assertNotIn(LOCAL_GUARD, qualified)
        self.assertEqual(local.replace(LOCAL_GUARD, QUALIFIED_GUARD), qualified)
        self.assertEqual(local_hash, qualified_hash)
        self.assertEqual(local_hash, 'f7b0e515b580f4bd9c49857ad4a49a3ab925218e8e52c260eb14cc75e410cba7')
        self.assertNotIn('__L08_', qualified)

    def test_default_render_keeps_exact_legacy_local_expansion(self):
        item = self.copy_fixture()
        template = (adaptor.HERE / 'L08AppFoundation.m.in').read_text()
        original_hash = hashlib.sha256(template.encode()).hexdigest()
        expected = template
        for key, value in (('TEMPLATE_SHA256', original_hash), ('CONTEXT_SHA256', 'b' * 64),
                           ('CONTROLS_SHA256', 'c' * 64), ('DEVICE_SET', str(item[5])), ('SIGNING_MODE', 'adhoc')):
            self.assertEqual(expected.count('__L08_' + key + '__'), 1)
            expected = expected.replace('__L08_' + key + '__', json.dumps(value))
        self.assertEqual(self.render(item), (expected, original_hash))

    def test_native_guard_missing_ambiguous_or_changed_patch_fails_closed(self):
        item = self.copy_fixture()
        template = (adaptor.HERE / 'L08AppFoundation.m.in').read_text()
        self.assertIn(QUALIFIED_GUARD, self.render(item, profiles.QUALIFIED)[0])
        mutations = (template.replace(LOCAL_GUARD, 'true'), template + '\n' + LOCAL_GUARD,
                     template.replace(LOCAL_GUARD, LOCAL_GUARD.replace('patchVersion == 0', 'patchVersion >= 0')))
        for changed in mutations:
            with self.subTest(digest=hashlib.sha256(changed.encode()).hexdigest()), \
                    self.assertRaisesRegex(RuntimeError, 'missing-or-ambiguous-copy-anchor'):
                self.render(item, profiles.QUALIFIED, changed)

    def test_copy_binding_records_selected_profile_triple_and_generated_native_hash(self):
        for name, runtime in PROFILES:
            with self.subTest(profile=name):
                root, before, custody, context, home, device_set = self.copy_fixture()
                with patch.object(adaptor.Path, 'home', return_value=home):
                    after, binding = adaptor.apply_owned_adapter(root, before, custody, context,
                        'c' * 64, 'adhoc', device_set, toolchain=name)
                self.assertEqual(binding['toolchain_profile'], name)
                self.assertEqual(binding['expected_runtime_version'], runtime)
                self.assertEqual(binding['native_copy_sha256'], adaptor.digest(root / adaptor.NATIVE))
                self.assertEqual(binding['fixture_template_sha256'], adaptor.digest(adaptor.HERE / 'L08AppFoundation.m.in'))
                self.assertEqual(binding['execution'], 'NOT_RUN')
                self.assertEqual(binding['native_build'], 'NOT_RUN')
                self.assertEqual(binding['original_l08'], 'UNCHANGED_NOT_SATISFIED')
                self.assertEqual(len(after), 5)
                self.assertEqual(schema.binding_toolchain(binding, name), name)
                guard = LOCAL_GUARD if name == profiles.LOCAL else QUALIFIED_GUARD
                self.assertEqual((root / adaptor.NATIVE).read_text().count(guard), 1)

    def test_unknown_profiles_fail_before_generated_copy_writes(self):
        for name in (None, '', 'latest', 'qualified-xcode-26.2', [26, 2, 0], {'name': profiles.QUALIFIED}, True):
            with self.subTest(profile=name):
                root, before, custody, context, home, device_set = self.copy_fixture()
                original = {row['path']: (root / row['path']).read_bytes() for row in before}
                with patch.object(adaptor.Path, 'home', return_value=home), self.assertRaises(RuntimeError):
                    adaptor.apply_owned_adapter(root, before, custody, context, 'c' * 64,
                                                'adhoc', device_set, toolchain=name)
                self.assertEqual({path: (root / path).read_bytes() for path in original}, original)
                self.assertFalse((root / adaptor.NATIVE).exists())
                self.assertFalse((root / adaptor.BRIDGE).exists())

    def test_complete_parse_requires_selected_exact_runtime_and_strict_primitive_types(self):
        for name, runtime in PROFILES:
            value, _ = selected_fixture(name, runtime)
            self.assertEqual(schema.parse_record(fixtures.encoded(value), name), value)
            wrong = ([26, 2 if runtime[1] == 5 else 5, 0], [26, runtime[1], 1], [25, runtime[1], 0],
                     [26, runtime[1], False], [26, float(runtime[1]), 0], [26, str(runtime[1]), 0],
                     [], [26, runtime[1]], [26, runtime[1], 0, 0], '26.2.0', None)
            for actual in wrong:
                with self.subTest(profile=name, runtime=actual), self.assertRaisesRegex(RuntimeError, 'runtime'):
                    schema.parse_record(fixtures.encoded(dict(value, runtime_version=actual)), name)

    def test_no_explicit_parse_profile_preserves_local_default_only(self):
        local, _ = selected_fixture(profiles.LOCAL, [26, 5, 0])
        qualified, _ = selected_fixture(profiles.QUALIFIED, [26, 2, 0])
        self.assertEqual(schema.parse_record(fixtures.encoded(local)), local)
        with self.assertRaisesRegex(RuntimeError, 'runtime'):
            schema.parse_record(fixtures.encoded(qualified))

    def test_unknown_profile_cannot_parse_even_well_formed_raw_record(self):
        raw = fixtures.encoded(fixtures.fixture())
        for name in (None, '', 'latest', {'name': profiles.LOCAL}, [26, 5, 0], True):
            with self.subTest(profile=name), patch.object(schema, 'base_validator') as decode, \
                    self.assertRaisesRegex(RuntimeError, 'Unknown explicit native toolchain profile'):
                schema.parse_record(raw, name)
            decode.assert_not_called()

    def test_copy_binding_requires_profile_and_exact_integer_triple(self):
        for name, runtime in PROFILES:
            _, binding = selected_fixture(name, runtime)
            self.assertEqual(schema.binding_toolchain(binding, name), name)
            changes = []
            for key in ('toolchain_profile', 'expected_runtime_version'):
                changed = copy.deepcopy(binding); changed.pop(key); changes.append(changed)
            for profile in ('latest', None, True, profiles.QUALIFIED if name == profiles.LOCAL else profiles.LOCAL):
                changes.append(dict(binding, toolchain_profile=profile))
            for observed in ([], runtime[:2], [26, runtime[1], False], [26, runtime[1], 0.0],
                             [26, runtime[1], 1], [26, 2 if runtime[1] == 5 else 5, 0], tuple(runtime), None):
                changes.append(dict(binding, expected_runtime_version=observed))
            for changed in changes:
                with self.subTest(profile=name, binding=changed), self.assertRaisesRegex(RuntimeError, 'copy-toolchain-binding'):
                    schema.binding_toolchain(changed, name)

    def test_collection_requires_paired_profile_but_never_claims_strict_l08_pass(self):
        for name, runtime in PROFILES:
            value, binding = selected_fixture(name, runtime)
            actual = fixtures.bind(value, bindings=binding, xctest=fixtures.log(value), toolchain=name)
            self.assertEqual(actual['status'], composition.COLLECTION)
            self.assertEqual(actual['strict_l08'], 'UNCHANGED_NOT_SATISFIED')
            self.assertIs(actual['physical_protection_verified'], False)
            self.assertEqual(value['rows'][1]['fm']['value'], 'missing')
            other = profiles.QUALIFIED if name == profiles.LOCAL else profiles.LOCAL
            with self.assertRaisesRegex(RuntimeError, 'copy-toolchain-binding'):
                fixtures.bind(value, bindings=binding, xctest=fixtures.log(value), toolchain=other)
            malformed = dict(binding, expected_runtime_version=[26, runtime[1], False])
            with self.assertRaisesRegex(RuntimeError, 'copy-toolchain-binding'):
                fixtures.bind(value, bindings=malformed, xctest=fixtures.log(value), toolchain=name)

    def test_control_error_keeps_existing_bounded_runtime_shape_and_never_validates_collection(self):
        for name, runtime in PROFILES:
            for actual in ([], [26, 2 if runtime[1] == 5 else 5, 0], [26, runtime[1], 1]):
                value, binding = selected_fixture(name, runtime)
                value.update(status='CONTROL_ERROR', reason='runtime-version', runtime_version=actual)
                with self.subTest(profile=name, runtime=actual):
                    self.assertEqual(schema.parse_record(fixtures.encoded(value), name), value)
                    with self.assertRaisesRegex(RuntimeError, 'incomplete-native-control'):
                        fixtures.bind(value, bindings=binding, toolchain=name)
            for actual in ([26, runtime[1], False], [26, runtime[1], -1], [1001, runtime[1], 0], [26, runtime[1]]):
                value, _ = selected_fixture(name, runtime)
                value.update(status='CONTROL_ERROR', reason='runtime-version', runtime_version=actual)
                with self.subTest(profile=name, malformed=actual), self.assertRaisesRegex(RuntimeError, 'runtime'):
                    schema.parse_record(fixtures.encoded(value), name)

    def test_composed_copy_hook_passes_selected_runner_profile_without_default(self):
        source = composition_controls.rendered()
        calls = [node for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Call) and
                 isinstance(node.func, ast.Attribute) and node.func.attr == 'apply_owned_adapter']
        self.assertEqual(len(calls), 1)
        keywords = {keyword.arg: keyword.value for keyword in calls[0].keywords}
        self.assertEqual(set(keywords), {'toolchain'})
        self.assertIsInstance(keywords['toolchain'], ast.Name)
        self.assertEqual(keywords['toolchain'].id, 'toolchain_name')
        self.assertIn('toolchain_profile=toolchain_name', source)
        expected = {Path(__file__).resolve(), Path(__file__).with_name('test_native_command_failures.py'),
                    Path(__file__).with_name('test_toolchain_profiles.py')}
        self.assertEqual(set(composition.SUPPORT_CONTROLS), expected)
        # Execute only this script's entry block with inert signal/test/allocation
        # substitutes. The separate existing launcher tests cover interrupted cleanup.
        entry = [node for node in ast.parse(Path(__file__).read_text()).body if isinstance(node, ast.If) and
                 ast.unparse(node.test) == "__name__ == '__main__'"]
        self.assertEqual(len(entry), 1)
        events, handlers = [], []
        def register(number, handler):
            self.assertEqual(number, 15)
            events.append('handler-installed'); handlers.append(handler)
        def allocate(work):
            self.assertEqual(events, ['handler-installed'])
            events.append('allocation-stub')
            return work(None)
        result = SimpleNamespace(wasSuccessful=lambda: True, testsRun=EXPECTED_TESTS, skipped=[])
        def tests(**options):
            self.assertEqual(options, dict(exit=False, verbosity=2))
            self.assertEqual(events, ['handler-installed', 'allocation-stub'])
            events.append('tests-stub')
            return SimpleNamespace(result=result)
        namespace = dict(__name__='__main__', EXPECTED_TESTS=EXPECTED_TESTS,
            signal=SimpleNamespace(SIGTERM=15, signal=register), unittest=SimpleNamespace(main=tests),
            composition_controls=SimpleNamespace(launcher=SimpleNamespace(with_external_tmp=allocate)))
        with self.assertRaises(SystemExit) as ended:
            exec(compile(ast.Module(body=entry, type_ignores=[]), '<isolated-control-entry>', 'exec'), namespace)
        self.assertEqual(ended.exception.code, 0)
        self.assertEqual(events, ['handler-installed', 'allocation-stub', 'tests-stub'])
        self.assertEqual(len(handlers), 1)
        with self.assertRaisesRegex(KeyboardInterrupt, 'signal 15'):
            handlers[0](15, None)


class FoundationToolchainHookControls(composition_controls.OwnedFixtures):
    def selected_system(self, name=profiles.QUALIFIED, runtime=None):
        item = self.system()
        expected = [26, 2, 0] if name == profiles.QUALIFIED else [26, 5, 0]
        item.receipt['toolchain_profile'] = name
        item.receipt['app_foundation_copy_binding'].update(toolchain_profile=name, expected_runtime_version=expected)
        item.value['runtime_version'] = expected[:] if runtime is None else runtime
        self.write_native(item)
        return item

    def write_native(self, item):
        (item.container / 'tmp' / schema.RESULT_NAME).write_bytes(fixtures.encoded(item.value))

    def write_log(self, item):
        (item.evidence / 'xcodebuild.log').write_text(fixtures.log(item.value))

    def test_both_profiles_preserve_and_bind_through_all_actual_hooks(self):
        for name, runtime in PROFILES:
            with self.subTest(profile=name), patch.object(schema, 'parse_record', wraps=schema.parse_record) as parse:
                item = self.selected_system(name)
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'PRESERVED')
                self.assertEqual((item.evidence / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
                self.write_log(item)
                result = self.bind(item)
                self.assertEqual(result['status'], composition.COLLECTION)
                self.assertIs(result['physical_protection_verified'], False)
                self.assertEqual(result['strict_l08'], 'UNCHANGED_NOT_SATISFIED')
                self.assertEqual(parse.call_count, 3)
                self.assertEqual([call.args[1] for call in parse.call_args_list], [name, name, name])
                self.hooks.require_collection(item.receipt)
                item.receipt['status'] = 'PARTIALLY_VERIFIED'
                self.hooks.restrict_final(item.receipt)
                self.assertEqual(item.receipt['status'], 'PARTIALLY_VERIFIED')

    def test_missing_or_unknown_root_profile_cannot_preserve_or_bind(self):
        for name in (None, '', 'latest', {'name': profiles.QUALIFIED}, [26, 2, 0], True):
            with self.subTest(profile=name):
                item = self.selected_system()
                if name is None: item.receipt.pop('toolchain_profile')
                else: item.receipt['toolchain_profile'] = name
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'FAIL')
                self.assertFalse((item.evidence / schema.RESULT_NAME).exists())
                self.assertEqual((item.container / 'tmp' / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
                preserved = self.selected_system()
                self.preserve(preserved)
                self.assertEqual(preserved.receipt['app_foundation_preservation']['status'], 'PRESERVED')
                self.write_log(preserved)
                if name is None: preserved.receipt.pop('toolchain_profile')
                else: preserved.receipt['toolchain_profile'] = name
                self.assertEqual(self.bind(preserved)['status'], 'FAIL')
                with self.assertRaises(RuntimeError): self.hooks.require_collection(preserved.receipt)

    def test_wrong_copy_binding_cannot_validate_previously_preserved_record(self):
        for change in ('missing-profile', 'missing-runtime', 'wrong-profile', 'wrong-runtime', 'bool', 'patch'):
            with self.subTest(change=change):
                item = self.selected_system()
                self.preserve(item); self.write_log(item)
                self.assertEqual(self.bind(item)['status'], composition.COLLECTION)
                binding = item.receipt['app_foundation_copy_binding']
                if change == 'missing-profile': binding.pop('toolchain_profile')
                elif change == 'missing-runtime': binding.pop('expected_runtime_version')
                elif change == 'wrong-profile': binding['toolchain_profile'] = profiles.LOCAL
                else:
                    binding['expected_runtime_version'] = {'wrong-runtime': [26, 5, 0], 'bool': [26, 2, False],
                                                           'patch': [26, 2, 1]}[change]
                self.assertEqual(self.bind(item)['status'], 'FAIL')
                with self.assertRaises(RuntimeError): self.hooks.require_collection(item.receipt)
                self.assertEqual((item.evidence / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))

    def test_changed_root_selection_after_preservation_cannot_borrow_other_profile(self):
        for name, _ in PROFILES:
            with self.subTest(profile=name):
                item = self.selected_system(name)
                self.preserve(item); self.write_log(item)
                self.assertEqual(self.bind(item)['status'], composition.COLLECTION)
                item.receipt['toolchain_profile'] = profiles.LOCAL if name == profiles.QUALIFIED else profiles.QUALIFIED
                self.assertEqual(self.bind(item)['status'], 'FAIL')
                # Even a simultaneously altered copy binding cannot make the old native runtime match.
                replacement = profiles.profile(item.receipt['toolchain_profile'])
                item.receipt['app_foundation_copy_binding'].update(toolchain_profile=replacement['name'],
                    expected_runtime_version=replacement['runtime_version'])
                self.assertEqual(self.bind(item)['status'], 'FAIL')

    def test_wrong_runtime_complete_record_is_rejected_at_preservation_and_collection(self):
        for runtime in ([26, 5, 0], [26, 2, 1]):
            with self.subTest(runtime=runtime):
                item = self.selected_system(runtime=runtime)
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'FAIL')
                self.assertFalse((item.evidence / schema.RESULT_NAME).exists())
                self.assertEqual(self.bind(item)['status'], 'FAIL')
                self.assertEqual((item.container / 'tmp' / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))

    def test_runtime_control_error_is_retained_byte_for_byte_then_fails_collection(self):
        for runtime in ([], [26, 5, 0], [26, 2, 1]):
            with self.subTest(runtime=runtime):
                item = self.selected_system(runtime=runtime)
                item.value.update(status='CONTROL_ERROR', reason='runtime-version', rows=[], app_container=False,
                    container={'status': 'unavailable'}, main_image={'status': 'unavailable'},
                    fixture_image={'status': 'unavailable'}, foundation_image={'status': 'unavailable'},
                    fixture_cleanup=dict(status='NOT_CREATED', created=False, root_pinned=False,
                        directory_pinned=False, root_removed=False, reason='none'))
                self.write_native(item)
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'PRESERVED')
                self.assertEqual((item.evidence / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
                self.write_log(item)
                self.assertEqual(self.bind(item)['status'], 'FAIL')
                with self.assertRaises(RuntimeError): self.hooks.require_collection(item.receipt)
                item.receipt['status'] = 'PARTIALLY_VERIFIED'
                self.hooks.restrict_final(item.receipt)
                self.assertEqual(item.receipt['status'], 'FAIL')

    def test_malformed_runtime_control_error_keeps_rejection_and_source_evidence(self):
        for runtime in ([26, 2, False], [26, 2], [26, 2, -1], '26.2.0'):
            with self.subTest(runtime=runtime):
                item = self.selected_system(runtime=runtime)
                item.value.update(status='CONTROL_ERROR', reason='runtime-version')
                self.write_native(item)
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'FAIL')
                self.assertFalse((item.evidence / schema.RESULT_NAME).exists())
                self.assertEqual((item.container / 'tmp' / schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
                self.assertEqual(self.bind(item)['status'], 'FAIL')


def load_tests(_loader, tests, _pattern):
    if tests.countTestCases() != EXPECTED_TESTS:
        raise RuntimeError('Paired Foundation toolchain control discovery differs from reviewed 20')
    return tests


if __name__ == '__main__':
    # Reuse the reviewed external temporary-parent custody/finalizer; no new build lane.
    def interrupted(sig, _frame):
        raise KeyboardInterrupt('signal ' + str(sig))
    signal.signal(signal.SIGTERM, interrupted)
    program = composition_controls.launcher.with_external_tmp(lambda _: unittest.main(exit=False, verbosity=2))
    result = program.result
    raise SystemExit(0 if result.wasSuccessful() and result.testsRun == EXPECTED_TESTS and not result.skipped else 1)
