"""Root-executed synthetic controls; no full runner execution or native tool call.

Failure-path tests execute only the exact inherited nested `stage` function in an
isolated namespace. Native/device commands are inert assertions, never subprocesses.
"""
import ast
import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def load(name, path):
    if name in sys.modules:
        value = sys.modules[name]
        if Path(value.__file__).resolve() != path.resolve():
            raise RuntimeError('Unexpected synthetic module binding')
        return value
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value


composition = load('l08_foundation_composition_under_test', HERE / 'compose_runner.py')
launcher = load('l08_foundation_control_launcher_under_test', HERE / 'run_controls.py')
fixtures = load('l08_app_foundation_draft_controls', composition.DRAFT / 'test_app_foundation.py')


def original():
    return composition.DRIVER.read_text()


def rendered():
    return composition.render_source(original())


def nested_stage(receipt, errors):
    """Execute ONLY the already hash-bound inherited stage; no main/imports/tools."""
    main = next(n for n in ast.parse(rendered()).body if isinstance(n, ast.FunctionDef) and n.name == 'main')
    function = next(n for n in ast.walk(main) if isinstance(n, ast.FunctionDef) and n.name == 'stage')
    namespace = dict(receipt=receipt, errors=errors, now=lambda: 'synthetic-control-time')
    exec(compile(ast.Module(body=[function], type_ignores=[]), '<isolated-inherited-stage>', 'exec'), namespace)
    return namespace['stage']


class SourceControls(unittest.TestCase):
    def test_exact_companion_hash_and_composed_syntax(self):
        self.assertEqual(composition.digest(original().encode()), composition.DRIVER_SHA256)
        text = rendered()
        self.assertIsInstance(ast.parse(text), ast.Module)
        self.assertNotEqual(composition.digest(text.encode()), composition.DRIVER_SHA256)
        with self.assertRaisesRegex(RuntimeError, 'companion-driver-drift'):
            composition.render_source(original() + '\n')

    def test_each_missing_and_ambiguous_anchor_fails_closed(self):
        for before, _ in composition.TRANSFORMS:
            for changed in (original().replace(before, '', 1), original() + before):
                # Bypass only the initial digest guard on this synthetic string so
                # this test really reaches the independent exact-anchor guard.
                with self.subTest(anchor=composition.digest(before.encode())), patch.object(
                        composition, 'DRIVER_SHA256', composition.digest(changed.encode())):
                    with self.assertRaisesRegex(RuntimeError, 'missing-or-ambiguous-composition-anchor'):
                        composition.render_source(changed)

    def test_only_declared_regions_change_and_original_functions_remain(self):
        text = rendered()
        for before, after in reversed(composition.TRANSFORMS):
            self.assertEqual(text.count(after), 1)
            text = text.replace(after, before, 1)
        self.assertEqual(text, original())
        def definitions(value):
            return {n.name: ast.dump(n, include_attributes=False) for n in ast.parse(value).body
                    if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name not in {'main', 'control_files'}}
        self.assertEqual(definitions(original()), definitions(rendered()))

    def test_allocation_custody_is_inside_original_deferred_signal_boundary(self):
        text = rendered()
        blocks = [ast.get_source_segment(text, n) for n in ast.walk(ast.parse(text)) if isinstance(n, ast.With)]
        candidates = [block for block in blocks if block.startswith('with defer_parent_signals():') and
                      'raw_temp = tempfile.mkdtemp(' in block]
        self.assertEqual(len(candidates), 1)
        block = candidates[0]
        order = [block.index(fragment) for fragment in ('raw_temp = tempfile.mkdtemp(', 'temp = Path(raw_temp)',
                 "receipt['app_foundation_raw_custody'] = foundation.capture_custody(temp)",
                 "receipt['allocated_temporary_path'] = raw_temp", 'save()')]
        self.assertEqual(order, sorted(order))
        self.assertLess(text.index('foundation.canonical_custody('), text.index('owner = AppHostOwnership('))

    def test_inherited_inspector_precedes_adapter_and_extended_evidence(self):
        text = rendered()
        order = [text.index(fragment) for fragment in (
            'copied_source_manifest = inspect_copied_manifest(',
            'copied_source_manifest, app_foundation_binding = foundation.copy.apply_owned_adapter(',
            "receipt['app_foundation_copy_binding'] = app_foundation_binding",
            'for addition in (*ADDITIONS, *foundation.copy.ADDITIONS):',
            "(dest / 'copied-source.diff').write_text(diff)",
            "write_json(dest / 'copied-source-manifest.json', copied_source_manifest)")]
        self.assertEqual(order, sorted(order))
        self.assertIn("inspect_copied_inputs_after_build(temp / 'copy', copied_source_manifest)", text)

    def test_preservation_binding_and_required_gate_do_not_replace_original_gates(self):
        text = rendered()
        order = [text.index(fragment) for fragment in (
            'foundation.preserve(receipt, dest, uuid, container)',
            'preserve_l08_evidence(container, dest)',
            "receipt['native_uuid_inventory'] = uuids",
            "receipt['installed_app_linker_entitlements_sha256']",
            'foundation.bind_available(receipt, dest, uuid, built_inventory, installed_inventory)',
            'available_records = available_run_records(dest, mode)',
            "receipt['xctest'] = verify_xctest(",
            'foundation.require_collection(receipt)',
            "'FUNCTIONAL_COMPANION_VERIFIED' if runtime_complete(receipt, xcode) else 'FAIL'")]
        self.assertEqual(order, sorted(order))
        fallback = text.index("stage('preserve-app-foundation-before-device-cleanup'")
        self.assertLess(fallback, text.index("stage('shutdown-owned-simulator-and-app'"))
        self.assertLess(fallback, text.index("stage('delete-owned-simulator'"))
        self.assertIn("receipt.update(classify_final_companion(receipt))\n                foundation.restrict_final(receipt)", text)
        self.assertEqual(text.count('totalTestCount=5, passedTests=5'), 1)
        self.assertEqual(text.count('for ordinal in range(1, 9):'), original().count('for ordinal in range(1, 9):'))

    def test_control_union_keeps_every_inherited_and_new_input(self):
        binding = composition.CAMPAIGN / 'synthetic-nonexistent-source-binding.json'
        files = composition.control_files(binding)  # No binding read/native import or execution.
        inherited = {p for p in composition.COMPANION.iterdir() if p.is_file() and
                     (p.suffix in {'.py', '.in', '.md'} or p.name == 'inherited-controls.json')}
        frozen = {composition.DRAFT / row['path'] for row in json.loads(composition.DRAFT_FREEZE.read_text())['source_files']}
        references = {composition.CAMPAIGN / 'reviews/l08-native-foundation-control-03' / name
                      for name in composition.REFERENCE_PINS}
        own = {HERE / name for name in composition.OWN_FILES}
        self.assertEqual(set(files), inherited | frozen | references | own |
                         {binding, composition.DRAFT_FREEZE, composition.TOOLCHAIN_HELPER} |
                         set(composition.SUPPORT_CONTROLS))
        self.assertEqual(len(files), len(set(files)))
        self.assertTrue(all(p.is_file() for p in own))
        with self.assertRaisesRegex(RuntimeError, 'explicit-source-binding-required'):
            composition.control_files(None)

    def test_relative_freeze_preserves_exact_nine_owned_draft_inputs(self):
        freeze = json.loads(composition.DRAFT_FREEZE.read_text())
        paths = composition.frozen_draft_files(freeze)
        self.assertEqual(len(paths), 9)
        self.assertEqual({path.parent for path in paths}, {composition.DRAFT})
        self.assertEqual({row['path'] for row in freeze['source_files']}, {path.name for path in paths})
        self.assertEqual(freeze['previous_freeze']['sha256'],
                         composition.digest((composition.ROOT / freeze['previous_freeze']['path']).read_bytes()))

    def test_relative_freeze_rejects_wrong_root_escapes_duplicate_and_hash_changes(self):
        original_freeze = json.loads(composition.DRAFT_FREEZE.read_text())
        for name in ('/tmp/foreign', '../foreign', 'sub/file', './alias', 'sub\\file', '.', '..', ''):
            changed = copy.deepcopy(original_freeze)
            changed['source_files'][0]['path'] = name
            with self.subTest(path=name), self.assertRaisesRegex(RuntimeError, 'draft-freeze-relative-path'):
                composition.frozen_draft_files(changed)
        for change in ('root', 'duplicate', 'hash'):
            changed = copy.deepcopy(original_freeze)
            if change == 'root': changed['directory'] = 'other/native'
            elif change == 'duplicate': changed['source_files'][-1] = changed['source_files'][0]
            else: changed['source_files'][0]['sha256'] = '0' * 64
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                composition.frozen_draft_files(changed)


class OwnedFixtures(unittest.TestCase):
    def setUp(self):
        self.owned = []
        self.hooks = composition.Hooks(rendered(), fixtures.adaptor, fixtures.schema)

    def temporary(self):
        raw = Path(tempfile.mkdtemp(prefix='parlor-audit-ios-readiness-99-composition-'))
        value = raw.lstat()
        self.owned.append((raw, value.st_dev, value.st_ino, value.st_uid))
        self.assertEqual(raw.resolve(strict=True), raw)
        self.assertTrue(stat.S_ISDIR(value.st_mode) and stat.S_IMODE(value.st_mode) == 0o700)
        self.assertEqual(value.st_uid, os.getuid())
        self.assertNotIn(composition.ROOT, raw.parents)
        return raw

    def tearDown(self):
        for path, device, inode, uid in reversed(self.owned):
            value = path.lstat()
            self.assertEqual((value.st_dev, value.st_ino, value.st_uid), (device, inode, uid))
            self.assertTrue(stat.S_ISDIR(value.st_mode) and not path.is_symlink() and path.resolve() == path)
            shutil.rmtree(path)  # Exact synthetic allocation; never a copied/user/app profile.
            self.assertFalse(path.exists() or path.is_symlink())

    def system(self):
        home, evidence = self.temporary(), self.temporary()
        container = home / 'Library/Developer/CoreSimulator/Devices' / fixtures.DEVICE / 'data/Containers/Data/Application' / fixtures.CONTAINER
        (container / 'tmp').mkdir(mode=0o700, parents=True)
        value = fixtures.fixture(); identity = container.lstat()
        value['container'] = dict(status='observed', id=fixtures.CONTAINER, device=identity.st_dev,
                                  inode=identity.st_ino, uid=identity.st_uid)
        context = dict(schemaVersion=6, runToken=fixtures.TOKEN, scenario='readiness', completed=False,
                       observations=[dict(ordinal=1, phase='before_main', fixture='existing', boot=fixtures.BOOT)])
        built, installed, uuids = fixtures.inventories()
        receipt = dict(app_foundation_preservation=dict(status='NOT_RUN', files=[]),
                       app_foundation_collection=dict(status='NOT_RUN'),
                       toolchain_profile=fixtures.schema.toolchains.LOCAL,
                       app_foundation_copy_binding=fixtures.binding(), native_uuid_inventory=uuids)
        for name, record in ((fixtures.schema.RESULT_NAME, value), ('parlor-dsc01-readiness-result.json', context)):
            with (container / 'tmp' / name).open('xb') as output: output.write(fixtures.encoded(record))
        return types.SimpleNamespace(home=home, evidence=evidence, container=container, value=value, context=context,
                                     receipt=receipt, built=built, installed=installed)

    def preserve(self, item):
        with patch.object(composition.Path, 'home', return_value=item.home):
            self.hooks.preserve(item.receipt, item.evidence, fixtures.DEVICE, item.container)

    def query(self, item, events):
        def command(args, filename, timeout):
            self.assertEqual(args, ['xcrun', 'simctl', 'get_app_container', fixtures.DEVICE, 'com.parlor.app.debug', 'data'])
            self.assertEqual((filename, timeout), ('app-foundation-final-container.log', 45))
            events.append('query-only-stub')
            with (item.evidence / filename).open('x') as output: output.write(str(item.container) + '\n')
            return 0
        return command

    def bind(self, item):
        self.hooks.bind_available(item.receipt, item.evidence, fixtures.DEVICE, item.built, item.installed)
        return item.receipt['app_foundation_collection']


class IntegrationControls(OwnedFixtures):
    def test_capture_and_canonicalization_keep_original_creation_identity(self):
        path = self.temporary()
        before = composition.capture_custody(path)
        self.assertEqual(composition.canonical_custody(path, before), before)
        self.assertEqual(before, dict(path=str(path), device=path.stat().st_dev, inode=path.stat().st_ino, uid=os.getuid()))

    def test_replaced_or_nonprivate_custody_is_rejected(self):
        path = self.temporary(); custody = composition.capture_custody(path)
        for field in ('device', 'inode', 'uid'):
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, 'canonical-allocation-replaced'):
                composition.canonical_custody(path, {**custody, field: custody[field] + 1})
        path.chmod(0o755)
        try:
            with self.assertRaisesRegex(RuntimeError, 'fresh-allocation-custody'): composition.capture_custody(path)
        finally: path.chmod(0o700)
        link = path / 'parlor-audit-ios-readiness-99-alias'; link.symlink_to(path, target_is_directory=True)
        with self.assertRaises(RuntimeError): composition.capture_custody(link)

    def test_normal_preservation_uses_existing_context_and_never_overwrites(self):
        item = self.system()
        target = item.evidence / 'probe-readiness-result.json'
        target.write_bytes(fixtures.encoded(item.context))  # Models the preceding inherited bounded copy.
        self.preserve(item)
        self.assertEqual(item.receipt['app_foundation_context'], dict(run_token=fixtures.TOKEN, process_boot=fixtures.BOOT))
        self.assertEqual(item.receipt['app_foundation_preservation'], dict(status='PRESERVED', files=[fixtures.schema.RESULT_NAME]))
        output = item.evidence / fixtures.schema.RESULT_NAME
        self.assertEqual(output.read_bytes(), fixtures.encoded(item.value))
        (item.container / 'tmp' / fixtures.schema.RESULT_NAME).write_text('subsequent-invalid-record')
        with patch.object(fixtures.schema, 'preserve_available', side_effect=AssertionError('must not repeat')):
            self.preserve(item)
        self.assertEqual(output.read_bytes(), fixtures.encoded(item.value))
        self.assertEqual(target.read_bytes(), fixtures.encoded(item.context))

    def test_timeout_and_cancellation_fallback_preserve_before_simulated_deletion(self):
        for error_type in (TimeoutError, KeyboardInterrupt):
            with self.subTest(error=error_type.__name__):
                item = self.system(); events, errors = [], []
                stage = nested_stage(item.receipt, errors)
                def preserve():
                    with patch.object(composition.Path, 'home', return_value=item.home):
                        self.hooks.preserve_before_cleanup(item.receipt, item.evidence, fixtures.DEVICE, self.query(item, events))
                    events.append('preserved')
                def delete():
                    self.assertEqual((item.evidence / fixtures.schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
                    events.append('simulated-delete')
                with self.assertRaises(error_type):
                    try: raise error_type('synthetic interruption; no signal/process')
                    finally:
                        stage('preserve-app-foundation-before-device-cleanup', preserve)
                        stage('delete-owned-simulator', delete)
                self.assertEqual(events, ['query-only-stub', 'preserved', 'simulated-delete'])
                self.assertEqual(errors, [])
                self.assertEqual([row['status'] for row in item.receipt['finalization_stages']], ['PASS', 'PASS'])

    def test_malformed_or_missing_context_cannot_produce_collection_success(self):
        for change in ('missing', 'json', 'schema', 'token', 'unknown-field', 'ordinal', 'phase', 'empty'):
            with self.subTest(change=change):
                item = self.system(); value = copy.deepcopy(item.context)
                if change == 'schema': value['schemaVersion'] = True
                elif change == 'token': value['runToken'] = 'not-a-uuid'
                elif change == 'unknown-field': value['other'] = True
                elif change == 'ordinal': value['observations'][0]['ordinal'] = 2
                elif change == 'phase': value['observations'][0]['phase'] = 'sample'
                elif change == 'empty': value['observations'] = []
                target = item.container / 'tmp/parlor-dsc01-readiness-result.json'
                if change == 'missing': target.unlink()
                else: target.write_bytes(b'not-json' if change == 'json' else fixtures.encoded(value))
                self.preserve(item)
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'UNAVAILABLE' if change == 'missing' else 'FAIL')
                self.assertFalse((item.evidence / fixtures.schema.RESULT_NAME).exists())
                self.assertEqual(self.bind(item)['status'], 'FAIL')
                with self.assertRaises(RuntimeError): self.hooks.require_collection(item.receipt)

    def test_partial_preservation_failure_retains_first_record_and_cleanup_still_runs(self):
        item = self.system(); (item.container / 'tmp' / fixtures.schema.FAILURE_NAME).write_text('{}')
        events, errors = [], []; stage = nested_stage(item.receipt, errors)
        with patch.object(composition.Path, 'home', return_value=item.home):
            stage('preserve-app-foundation-before-device-cleanup', lambda: self.hooks.preserve_before_cleanup(
                item.receipt, item.evidence, fixtures.DEVICE, self.query(item, events)))
        stage('delete-owned-simulator', lambda: events.append('simulated-delete'))
        self.assertEqual(events, ['query-only-stub', 'simulated-delete'])
        self.assertEqual([row['status'] for row in item.receipt['finalization_stages']], ['FAIL', 'PASS'])
        self.assertEqual(len(errors), 1)
        self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'FAIL')
        self.assertEqual(item.receipt['app_foundation_preservation']['files'], [fixtures.schema.RESULT_NAME])
        self.assertEqual((item.evidence / fixtures.schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))
        self.assertFalse((item.evidence / fixtures.schema.FAILURE_NAME).exists())
        self.assertEqual(self.bind(item)['status'], 'FAIL')

    def test_binding_requires_actual_inventories_executed_log_and_first_boot(self):
        for change in ('none', 'missing-built', 'installed-hash', 'missing-log', 'duplicate-log', 'wrong-boot'):
            with self.subTest(change=change):
                item = self.system()
                if change == 'wrong-boot':
                    item.value['process_boot'] = fixtures.TOKEN
                    (item.container / 'tmp' / fixtures.schema.RESULT_NAME).write_bytes(fixtures.encoded(item.value))
                self.preserve(item)
                if change != 'missing-log':
                    text = fixtures.log(item.value)
                    (item.evidence / 'xcodebuild.log').write_text(text + ('\n' + text if change == 'duplicate-log' else ''))
                if change == 'missing-built': item.built = None
                elif change == 'installed-hash': item.installed['images'][0]['sha256'] = 'f' * 64
                result = self.bind(item)
                self.assertEqual(result['status'], composition.COLLECTION if change == 'none' else 'FAIL')
                if change == 'none':
                    self.assertFalse(result['first_health_process_cross_checked'])
                    self.assertFalse(result['physical_protection_verified'])
                    self.assertEqual(result['strict_l08'], 'UNCHANGED_NOT_SATISFIED')

    def test_first_health_process_binding_is_enforced_when_available(self):
        for field in (None, 'process_id', 'process_boot', 'run_token', 'boot_ordinal', 'signing_mode'):
            with self.subTest(field=field):
                item = self.system(); self.preserve(item)
                (item.evidence / 'xcodebuild.log').write_text(fixtures.log(item.value))
                health = dict(boot_ordinal=1, process_id=40001, process_boot=fixtures.BOOT, run_token=fixtures.TOKEN, signing_mode='adhoc')
                if field is not None:
                    health[field] = {'process_id': 40002, 'process_boot': fixtures.TOKEN, 'run_token': fixtures.BOOT,
                                     'boot_ordinal': 2, 'signing_mode': 'disabled'}[field]
                (item.evidence / 'parlor-native-readiness-boot-1.json').write_bytes(fixtures.encoded(health))
                result = self.bind(item)
                self.assertEqual(result['status'], composition.COLLECTION if field is None else 'FAIL')
                if field is None: self.assertTrue(result['first_health_process_cross_checked'])

    def test_required_collection_never_upgrades_original_classification(self):
        for prior in ('FAIL', 'PARTIALLY_VERIFIED'):
            receipt = dict(status=prior, app_foundation_collection=dict(status=composition.COLLECTION))
            self.hooks.require_collection(receipt); self.hooks.restrict_final(receipt)
            self.assertEqual(receipt['status'], prior)
            for missing in ('NOT_RUN', 'FAIL', 'UNAVAILABLE'):
                receipt = dict(status=prior, app_foundation_collection=dict(status=missing))
                with self.assertRaises(RuntimeError): self.hooks.require_collection(receipt)
                self.hooks.restrict_final(receipt)
                self.assertEqual(receipt['status'], 'FAIL')

    def test_foreign_container_and_cross_run_token_are_not_read_or_adopted(self):
        for change in ('foreign-container', 'wrong-device', 'cross-token'):
            with self.subTest(change=change):
                item = self.system()
                if change == 'cross-token':
                    item.value['run_token'] = fixtures.BOOT
                    (item.container / 'tmp' / fixtures.schema.RESULT_NAME).write_bytes(fixtures.encoded(item.value))
                    self.preserve(item)
                else:
                    container = item.home if change == 'foreign-container' else item.container
                    device = fixtures.BOOT if change == 'wrong-device' else fixtures.DEVICE
                    with patch.object(composition.Path, 'home', return_value=item.home), patch.object(
                            composition, 'raw_file', side_effect=AssertionError('foreign path must not be read')) as read:
                        self.hooks.preserve(item.receipt, item.evidence, device, container)
                    read.assert_not_called()
                self.assertEqual(item.receipt['app_foundation_preservation']['status'], 'FAIL')
                self.assertFalse((item.evidence / fixtures.schema.RESULT_NAME).exists())
                self.assertEqual((item.container / 'tmp' / fixtures.schema.RESULT_NAME).read_bytes(), fixtures.encoded(item.value))


class LauncherControls(unittest.TestCase):
    def test_external_parent_and_environment_are_finalized_on_success_failure_and_cancellation(self):
        previous_env, previous_cache = os.environ.get('TMPDIR'), tempfile.tempdir
        try:
            for error_type in (None, ValueError, KeyboardInterrupt):
                with self.subTest(error=None if error_type is None else error_type.__name__):
                    if error_type is None: os.environ.pop('TMPDIR', None)
                    else: os.environ['TMPDIR'] = '/synthetic-prior-tmpdir-never-accessed'
                    tempfile.tempdir = None
                    expected_env = os.environ.get('TMPDIR'); allocations = []
                    def work(scratch):
                        parent = scratch.parent; identity = parent.lstat()
                        allocations.append(parent)
                        expected_parent = Path('/private/tmp' if sys.platform == 'darwin' else '/tmp').resolve(strict=True)
                        self.assertEqual(parent.parent, expected_parent)
                        self.assertEqual(identity.st_uid, os.getuid())
                        self.assertEqual(stat.S_IMODE(identity.st_mode), 0o700)
                        self.assertEqual(os.environ['TMPDIR'], str(scratch) + '/')
                        self.assertEqual(tempfile.tempdir, str(scratch))
                        (scratch / 'owned-synthetic-only').write_text('recreatable')
                        if error_type is not None: raise error_type('synthetic; no OS signal')
                        return 'callback-value'
                    if error_type is None: self.assertEqual(launcher.with_external_tmp(work), 'callback-value')
                    else:
                        with self.assertRaises(error_type): launcher.with_external_tmp(work)
                    self.assertEqual(os.environ.get('TMPDIR'), expected_env)
                    self.assertIsNone(tempfile.tempdir)
                    self.assertEqual(len(allocations), 1)
                    self.assertFalse(allocations[0].exists() or allocations[0].is_symlink())
        finally:
            if previous_env is None: os.environ.pop('TMPDIR', None)
            else: os.environ['TMPDIR'] = previous_env
            tempfile.tempdir = previous_cache

    def test_external_parent_rejects_unreviewed_platform_before_allocation(self):
        with patch.object(launcher.sys, 'platform', 'win32'), patch.object(launcher.tempfile, 'mkdtemp') as allocate:
            with self.assertRaisesRegex(RuntimeError, 'reviewed Darwin/Linux'):
                launcher.with_external_tmp(lambda _: self.fail('callback must not execute'))
            allocate.assert_not_called()

    def test_external_parent_canonicalizes_exact_platform_path_before_allocation(self):
        for platform, raw_path in (('darwin', '/private/tmp'), ('linux', '/tmp')):
            canonical = Path('/tmp').resolve(strict=True)
            with self.subTest(platform=platform), patch.object(launcher.sys, 'platform', platform), \
                    patch.object(launcher, 'Path') as constructor:
                constructor.return_value.resolve.return_value = canonical
                self.assertEqual(launcher.external_parent(), canonical)
                constructor.assert_called_once_with(raw_path)
                constructor.return_value.resolve.assert_called_once_with(strict=True)


def load_tests(_loader, tests, _pattern):
    if tests.countTestCases() != 22:
        raise RuntimeError('Composition synthetic discovery differs from reviewed 22')
    return tests


if __name__ == '__main__':
    raise SystemExit('Use run_controls.py so all synthetic roots are created outside the repository.')
