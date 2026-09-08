"""Offline synthetic bootstrap guards, never actual wheel/package execution."""
from contextlib import contextmanager
import importlib.util
import io
import json
from pathlib import Path
import stat
import sys
import tempfile
import types
import unittest
from unittest import mock
import zipfile


PATH = Path(__file__).with_name('run_candidate_with_schema.py')
SPEC = importlib.util.spec_from_file_location('parlor_schema_bootstrap', PATH)
subject = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(subject)


class SchemaBaseBootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='bootstrap-controls-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def wheel(self, entries=None):
        if entries is None:
            entries = [('fixture/__init__.py', b'raise AssertionError("Must not import fixture")\n'),
                       ('fixture-1.dist-info/METADATA', b'Metadata-Version: 2.1\nName: fixture\nVersion: 1\n')]
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, raw in entries:
                archive.writestr(name, raw)
        raw = stream.getvalue()
        return raw, ('fixture', '1', ('fixture', 'fixture-1.dist-info'),
                     'https://files.pythonhosted.org/packages/fixed/fixture.whl', len(raw), subject.sha(raw))

    def extract(self, entries):
        raw, wheel = self.wheel(entries)
        return subject.extract_wheel(raw, wheel, self.root)

    def test_fixed_metadata_pins_match_retained_official_byte_research(self):
        research = PATH.parents[1] / 'reviews/schema-base-prerequisites-research-01.json'
        value = json.loads(research.read_text())
        self.assertEqual(subject.sha(research.read_bytes()), subject.CONTROL_PINS[research])
        recorded = {row['name']: row for row in value['packages']}
        self.assertEqual(len(subject.WHEELS), 6)
        for name, version, roots, url, size, digest in subject.WHEELS:
            row = recorded[name]
            self.assertEqual((version, url, size, digest), (row['version'], row['wheel']['url'], row['wheel']['size'], row['wheel']['digests']['sha256']))
            self.assertEqual(set(roots), {member['path'].split('/')[0] for member in row['wheel_inspection']['members']})
            self.assertFalse(row['wheel']['yanked'])

    def test_platform_rejects_wrong_host_python_libc_and_bytecode_policy(self):
        with mock.patch.object(subject.sys, 'platform', 'linux'), mock.patch.object(subject.platform, 'machine', return_value='x86_64'), \
                mock.patch.object(subject.sys, 'version_info', (3, 12)), mock.patch.object(subject.sys, 'dont_write_bytecode', True), \
                mock.patch.object(subject.platform, 'libc_ver', return_value=('glibc', '2.39')):
            subject.require_platform()
            for owner, field, value in ((subject.sys, 'platform', 'darwin'), (subject.sys, 'version_info', (3, 11)),
                                         (subject.sys, 'dont_write_bytecode', False),
                                         (subject.sys, 'implementation', types.SimpleNamespace(name='pypy'))):
                with self.subTest(field=field), mock.patch.object(owner, field, value), self.assertRaises(ValueError):
                    subject.require_platform()
            with mock.patch.object(subject.platform, 'machine', return_value='aarch64'), self.assertRaises(ValueError):
                subject.require_platform()
            for libc in (('musl', '1.2'), ('glibc', '2.16'), ('glibc', 'unknown')):
                with self.subTest(libc=libc), mock.patch.object(subject.platform, 'libc_ver', return_value=libc), self.assertRaises(ValueError):
                    subject.require_platform()

    def test_requires_canonical_outer_lane_tmpdir(self):
        campaign = self.root / 'campaign'
        tmp = campaign / 'evidence/cycle/scratch/tmp'
        tmp.mkdir(parents=True)
        with mock.patch.object(subject, 'CAMPAIGN', campaign):
            with mock.patch.dict(subject.os.environ, {'TMPDIR': str(tmp)}):
                self.assertEqual(subject.owned_destination(), (tmp, tmp.parents[1]))
            alias = self.root / 'alias'
            alias.symlink_to(tmp, target_is_directory=True)
            for path in (alias, self.root):
                with self.subTest(path=path), mock.patch.dict(subject.os.environ, {'TMPDIR': str(path)}), self.assertRaises(ValueError):
                    subject.owned_destination()

    def test_fetch_requires_exact_origin_status_length_and_digest(self):
        raw, wheel = self.wheel()
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.geturl.return_value = wheel[3]
        response.read.return_value = raw
        opener = mock.Mock()
        opener.open.return_value = response
        with mock.patch.object(subject.urllib.request, 'build_opener', return_value=opener):
            self.assertEqual(subject.fetch_wheel(wheel), raw)
            opener.open.assert_called_with(wheel[3], timeout=30)
            response.read.assert_called_with(wheel[4] + 1)
            for field, value in (('status', 404), ('origin', 'https://other.invalid/file'), ('bytes', raw + b'x'), ('bytes', bytes(len(raw)))):
                response.status, response.geturl.return_value, response.read.return_value = 200, wheel[3], raw
                if field == 'status': response.status = value
                elif field == 'origin': response.geturl.return_value = value
                else: response.read.return_value = value
                with self.subTest(field=field), self.assertRaises(ValueError):
                    subject.fetch_wheel(wheel)

    def test_redirect_handler_never_follows_other_origin(self):
        with self.assertRaises(ValueError):
            subject.NoRedirect().redirect_request(None, None, None, None, None, 'https://other.invalid/')

    def test_extraction_rejects_unverified_bytes(self):
        raw, wheel = self.wheel()
        with self.assertRaises(ValueError):
            subject.extract_wheel(raw + b'x', wheel, self.root)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_extraction_rejects_unsafe_foreign_or_pth_paths(self):
        for name in ('../escape', '/absolute', 'fixture/../escape', 'fixture//empty', 'fixture/./dot',
                     'fixture\\escape', 'fixture/drive:name', 'foreign/__init__.py', 'fixture/loader.pth', 'fixture/control\x01'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.extract([(name, b'x')])
        self.assertFalse((self.root.parent / 'escape').exists())

    def test_extraction_rejects_duplicate_directory_and_symlink_members(self):
        for mode, name in ((stat.S_IFDIR | 0o755, 'fixture/'), (stat.S_IFLNK | 0o777, 'fixture/link'),
                           (stat.S_IFIFO | 0o600, 'fixture/fifo')):
            info = zipfile.ZipInfo(name)
            info.external_attr = mode << 16
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.extract([(info, b'x')])
        raw, wheel = self.wheel()
        subject.extract_wheel(raw, wheel, self.root)
        with self.assertRaises(FileExistsError):
            subject.extract_wheel(raw, wheel, self.root)

    def test_extraction_member_size_and_count_are_bounded(self):
        with self.assertRaises(ValueError):
            self.extract([('fixture/too-big', b'x' * (2 * 1024 * 1024 + 1))])
        with self.assertRaises(ValueError):
            self.extract([(f'fixture/file{i}', b'x') for i in range(513)])

    def test_extraction_aggregate_expansion_is_bounded(self):
        with self.assertRaises(ValueError):
            self.extract([(f'fixture/large{i}', b'x' * (2 * 1024 * 1024)) for i in range(5)])

    def test_package_recheck_rejects_late_mutation_extra_missing_or_symlink(self):
        raw, wheel = self.wheel()
        package = subject.extract_wheel(raw, wheel, self.root)
        subject.recheck_packages(self.root, [package])
        path = self.root / 'fixture/__init__.py'
        before = path.read_bytes()
        path.write_bytes(b'x' * len(before))
        with self.assertRaises(ValueError):
            subject.recheck_packages(self.root, [package])
        path.write_bytes(before)
        extra = self.root / 'extra'
        extra.write_bytes(b'x')
        with self.assertRaises(ValueError):
            subject.recheck_packages(self.root, [package])
        extra.unlink()
        path.unlink()
        with self.assertRaises(ValueError):
            subject.recheck_packages(self.root, [package])
        path.symlink_to(self.root / 'fixture-1.dist-info/METADATA')
        with self.assertRaises(ValueError):
            subject.recheck_packages(self.root, [package])

    @contextmanager
    def fixture_packages(self, report):
        raw, wheel = self.wheel()
        with mock.patch.object(subject, 'WHEELS', (wheel,)), mock.patch.object(subject, 'MODULES', ('fixture',)), \
                mock.patch.object(subject, 'fetch_wheel', return_value=raw), mock.patch.object(subject, 'verify_imports'):
            with subject.base_packages(self.root, report) as owned:
                yield owned

    def test_context_defers_package_import_and_restores_path_and_cleans(self):
        before, report = sys.path[:], {}
        with self.fixture_packages(report) as owned:
            self.assertEqual(sys.path[0], str(owned))
            self.assertNotIn('fixture', sys.modules)
            self.assertEqual(report['versions'], {'fixture': '1'})
        self.assertEqual(sys.path, before)
        self.assertTrue(report['base_package_cleanup'])
        self.assertFalse(owned.exists())

    def test_context_cleans_after_exception_or_keyboard_interrupt(self):
        for failure in (ValueError('synthetic'), KeyboardInterrupt('synthetic')):
            before, report = sys.path[:], {}
            with self.subTest(failure=type(failure).__name__), self.assertRaises(type(failure)):
                with self.fixture_packages(report) as owned:
                    raise failure
            self.assertEqual(sys.path, before)
            self.assertTrue(report['base_package_cleanup'])
            self.assertFalse(owned.exists())

    def test_context_final_integrity_is_mandatory_before_success(self):
        report = {}
        with self.assertRaises(ValueError):
            with self.fixture_packages(report) as owned:
                path = owned / 'fixture/__init__.py'
                path.write_bytes(b'x' * path.stat().st_size)
        self.assertTrue(report['base_package_cleanup'])
        self.assertFalse(owned.exists())

    def test_context_refuses_cleanup_after_directory_identity_replacement(self):
        report = {}
        saved = None
        with self.assertRaises(ValueError):
            with self.fixture_packages(report) as owned:
                saved = owned.with_name(owned.name + '-original')
                owned.rename(saved)
                owned.mkdir()
                raise RuntimeError('Synthetic replacement')
        self.assertTrue(owned.exists())
        self.assertTrue(saved.exists())
        self.assertNotIn('base_package_cleanup', report)

    def test_context_rejects_preloaded_schema_library_without_allocating(self):
        with mock.patch.dict(subject.sys.modules, {'jsonschema': object()}), self.assertRaises(ValueError):
            with subject.base_packages(self.root, {}):
                self.fail('Preloaded package accepted')
        self.assertEqual(list(self.root.iterdir()), [])

    def test_context_rejects_wrong_or_ambient_distribution_and_still_cleans(self):
        for version in ('2', '1'):
            report = {}
            distribution = types.SimpleNamespace(version=version, locate_file=lambda _: self.root)
            with self.subTest(version=version), mock.patch.object(subject.importlib.metadata, 'distribution', return_value=distribution), \
                    self.assertRaises(ValueError):
                with self.fixture_packages(report):
                    self.fail('Wrong distribution accepted')
            self.assertTrue(report['base_package_cleanup'])
            self.assertEqual(list(self.root.iterdir()), [])

    def test_context_cleanup_failure_remains_failure_with_owned_tree_retained(self):
        report = {}
        with mock.patch.object(subject.shutil, 'rmtree', side_effect=OSError('Synthetic removal failure')), self.assertRaises(OSError):
            with self.fixture_packages(report) as owned:
                pass
        self.assertNotIn('base_package_cleanup', report)
        self.assertTrue(owned.is_dir())

    def test_import_verification_requires_actual_owned_modules_including_native_rpds(self):
        module_file = self.root / 'fixture.py'
        module_file.write_bytes(b'synthetic')
        modules = {'fixture': types.SimpleNamespace(__file__=str(module_file)),
                   'rpds.rpds': types.SimpleNamespace(__file__=str(module_file))}
        with mock.patch.object(subject, 'MODULES', ('fixture',)), mock.patch.dict(subject.sys.modules, modules):
            report = {}
            subject.verify_imports(self.root, report)
            self.assertEqual(set(report['imported_modules']), set(modules))
            with mock.patch.dict(subject.sys.modules, {'rpds.rpds': None}), self.assertRaises(ValueError):
                subject.verify_imports(self.root, {})
            with mock.patch.dict(subject.sys.modules, {'fixture': types.SimpleNamespace(__file__=str(PATH))}), self.assertRaises(ValueError):
                subject.verify_imports(self.root, {})

    def test_fixed_entrypoint_selects_only_pinned_adapter_or_control_driver_and_restores_argv(self):
        before = sys.argv[:]
        for mode, arguments, expected in (('controls', [], subject.DRIVER), ('candidate', ['binding', 'f' * 64], subject.ADAPTER)):
            seen = []
            module = types.SimpleNamespace(main=lambda: seen.append(sys.argv[:]) or 0)
            spec = types.SimpleNamespace(loader=mock.Mock())
            with mock.patch.object(subject.importlib.util, 'spec_from_file_location', return_value=spec) as load, \
                    mock.patch.object(subject.importlib.util, 'module_from_spec', return_value=module):
                self.assertEqual(subject.execute_fixed(mode, arguments), 0)
                self.assertEqual(load.call_args.args[1], expected)
                self.assertEqual(seen, [[str(expected), *arguments]])
                self.assertEqual(sys.argv, before)

    def test_fixed_entrypoint_restores_argv_on_interruption(self):
        before = sys.argv[:]
        module = types.SimpleNamespace(main=mock.Mock(side_effect=KeyboardInterrupt('Synthetic interrupt')))
        spec = types.SimpleNamespace(loader=mock.Mock())
        with mock.patch.object(subject.importlib.util, 'spec_from_file_location', return_value=spec), \
                mock.patch.object(subject.importlib.util, 'module_from_spec', return_value=module), self.assertRaises(KeyboardInterrupt):
            subject.execute_fixed('controls', [])
        self.assertEqual(sys.argv, before)

    def test_coupled_candidate_requires_both_results_and_ephemeral_cleanup(self):
        candidate = {'status': 'PASS_SCOPED_CANDIDATE_INPUTS'}
        prerequisites = {'status': 'PASS_SCOPED_CANDIDATE_INPUT_EXECUTION', 'consumer_exit_code': 0, 'ephemeral_parser_cleanup': True}
        (self.root / 'candidate-input-verification.json').write_text(json.dumps(candidate))
        path = self.root / 'candidate-prerequisites.json'
        path.write_text(json.dumps(prerequisites))
        subject.coupled_result('candidate', self.root)
        for update in ({'consumer_exit_code': 1}, {'ephemeral_parser_cleanup': False}, {'error_type': 'KeyboardInterrupt'}, {'status': 'FAIL'}):
            path.write_text(json.dumps({**prerequisites, **update}))
            with self.subTest(update=update), self.assertRaises(ValueError):
                subject.coupled_result('candidate', self.root)
        path.write_text(json.dumps(prerequisites))
        (self.root / 'candidate-input-verification.json').write_text('{"status":"FAIL"}')
        with self.assertRaises(ValueError):
            subject.coupled_result('candidate', self.root)

    def test_coupled_controls_reject_skips_errors_wrong_count_and_cleanup_failure(self):
        baseline = {'status': 'PASS_SYNTHETIC_CONTROLS', 'tests_run': 16, 'failures': [], 'errors': [],
                    'skipped': [], 'ephemeral_parser_cleanup': True}
        path = self.root / 'schema-consumer-controls.json'
        path.write_text(json.dumps(baseline))
        subject.coupled_result('controls', self.root)
        for update in ({'tests_run': 15}, {'skipped': ['synthetic']}, {'errors': ['synthetic']},
                       {'ephemeral_parser_cleanup': False}, {'error_type': 'KeyboardInterrupt'}):
            path.write_text(json.dumps({**baseline, **update}))
            with self.subTest(update=update), self.assertRaises(ValueError):
                subject.coupled_result('controls', self.root)

    def run_main(self, *, code=0, cleanup=True, drift=False):
        @contextmanager
        def base(_tmp, report):
            try:
                yield self.root
            finally:
                report['base_package_cleanup'] = cleanup
        hashes = [{'control': 'a'}, {'control': 'b' if drift else 'a'}]
        with mock.patch.object(subject, 'owned_destination', return_value=(self.root, self.root)), \
                mock.patch.object(subject, 'require_platform'), mock.patch.object(subject, 'control_hashes', side_effect=hashes), \
                mock.patch.object(subject, 'base_packages', side_effect=base), mock.patch.object(subject, 'execute_fixed', return_value=code), \
                mock.patch.object(subject, 'coupled_result'), mock.patch('sys.stdout', new=io.StringIO()):
            result = subject.main(['binding.json', 'f' * 64])
        return result, json.loads((self.root / 'schema-base-prerequisites.json').read_text())

    def test_main_cannot_convert_failed_inner_or_cleanup_or_drift_into_pass(self):
        for options in ({'code': 1}, {'code': False}, {'cleanup': False}, {'drift': True}):
            with self.subTest(options=options):
                code, report = self.run_main(**options)
                self.assertEqual(code, 1)
                self.assertEqual(report['status'], 'FAIL')
                self.assertIn('error_type', report)
                (self.root / 'schema-base-prerequisites.json').unlink()

    def test_main_retains_scoped_success_and_refuses_receipt_overwrite(self):
        code, report = self.run_main()
        self.assertEqual(code, 0)
        self.assertEqual(report['status'], 'PASS_OWNED_SCHEMA_PREREQUISITES')
        self.assertEqual(report['inner_exit_code'], 0)
        self.assertTrue(report['base_package_cleanup'])
        with self.assertRaises(ValueError):
            self.run_main()

    def test_cli_has_no_arbitrary_command_or_unpinned_binding_mode(self):
        for arguments in ([], ['python3', '-c', 'arbitrary'], ['binding.json', 'F' * 64], ['--controls', 'extra']):
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                subject.main(arguments)


if __name__ == '__main__':
    unittest.main(verbosity=2)
