"""Synthetic public-tool formats only, not actual Binary Images/vmmap evidence."""
import copy
import inspect
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import external_image_provenance as provenance
import run_normal_ios_launch as runner

HOME = '/Users/synthetic-owner'
DEVICE = '11111111-2222-3333-4444-555555555555'
CONTAINER = '66666666-7777-8888-9999-aaaaaaaaaaaa'
APP = Path(HOME + '/Library/Developer/CoreSimulator/Devices/' + DEVICE +
           '/data/Containers/Bundle/Application/' + CONTAINER + '/Parlor.app')
EXE = APP / 'Parlor'
PID = 41001
RELATIVES = ('Parlor', 'Parlor.debug.dylib', 'Frameworks/ComposeApp.framework/ComposeApp')


def inventory(app=APP):
    return {str(app / relative): dict(origin='installed-app', relative_path=relative,
        sha256=str(index + 1) * 64, bytes=16384,
        uuid=f'{index + 1:08x}-2222-3333-4444-555555555555', kind=kind)
        for index, (relative, kind) in enumerate(zip(RELATIVES, ('launcher', 'debug-dylib', 'compose-framework')))}


def alias(path):
    return '/Users/USER' + str(path)[len(HOME):]


def header(path=EXE, pid=PID):
    return f'Process: Parlor [{pid}]\nPath: {path}\n'


def image_row(path, index, row):
    start = (index + 1) * 0x100000
    return f'0x{start:x} - 0x{start + 0x3fff:x} +image (1) <{row["uuid"]}> {path}\n'


def sample(artifacts=None, redacted=False):
    artifacts = inventory() if artifacts is None else artifacts
    spelling = alias if redacted else str
    return header(spelling(EXE)) + '\nBinary Images:\n' + ''.join(
        image_row(spelling(path), index, row) for index, (path, row) in enumerate(artifacts.items()))


def vmmap(artifacts=None, redacted=False):
    artifacts = inventory() if artifacts is None else artifacts
    spelling = alias if redacted else str
    return header(spelling(EXE)) + ''.join(
        f'__TEXT {(index + 1) * 0x100000:x}-{(index + 1) * 0x100000 + 0x2000:x} [8K] r-x/r-x SM=COW {spelling(path)}\n'
        for index, path in enumerate(artifacts))


class OwnedToolPathTests(unittest.TestCase):
    def setUp(self):
        self.artifacts = inventory()
        self.policy = provenance.OwnedToolPaths(self.artifacts, HOME, DEVICE, APP)

    def parse(self, raw):
        return provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_default_remains_exact_and_rejects_user_only_presentation(self):
        with self.assertRaises(RuntimeError): provenance.verify_header(header(alias(EXE)), PID, EXE)
        with self.assertRaises(RuntimeError): provenance.parse_sample(sample(redacted=True), PID, EXE, self.artifacts)
        selected = provenance.parse_sample(sample(), PID, EXE, self.artifacts)
        with self.assertRaises(RuntimeError): provenance.bind_vmmap(vmmap(redacted=True), PID, EXE, selected)

    def test_raw_and_user_only_headers_rows_and_mappings_have_explicit_proof_limits(self):
        for redacted_sample in (False, True):
            for redacted_mapping in (False, True):
                with self.subTest(sample=redacted_sample, mapping=redacted_mapping):
                    result = provenance.bind_vmmap(vmmap(redacted=redacted_mapping), PID, EXE,
                        self.parse(sample(redacted=redacted_sample)), tool_paths=self.policy)
                    self.assertEqual('PASS', result['status'])
                    self.assertEqual(set(self.artifacts), {item['path'] for item in result['images']})
                    self.assertIn('lossy tool presentation alias', result['limitation'])
                    for item in result['images']:
                        self.assertEqual(self.artifacts[item['path']]['sha256'], item['sha256'])
                        self.assertEqual('owned-USER-only-presentation' if redacted_sample else 'exact', item['sample_path_kind'])
                        self.assertEqual('owned-USER-only-presentation' if redacted_mapping else 'exact', item['vmmap_path_kind'])

    def test_every_other_path_component_and_username_spelling_stays_exact(self):
        parts = alias(EXE).split('/')
        candidates = []
        for index in range(1, len(parts)):
            changed = list(parts); changed[index] = 'CHANGED'
            candidates.append('/'.join(changed))
        candidates += [alias(EXE).replace('/USER/', '/' + value + '/') for value in
                       ('*', '...', '~', '<redacted>', 'OTHER')]
        candidates += [alias(EXE).replace('/data/', '/*/'), alias(EXE).replace('/Parlor.app/', '/.../')]
        for path in candidates:
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                provenance.verify_header(header(path), PID, EXE, tool_paths=self.policy)

    def test_wrong_missing_and_duplicate_headers_are_rejected_with_opt_in(self):
        raw = header(alias(EXE))
        for value in (header(alias(EXE), PID + 1), raw + raw, raw + header(),
                      raw.replace('Path:', 'Other:'), raw.replace('Process:', 'Other:')):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                provenance.verify_header(value, PID, EXE, tool_paths=self.policy)

    def test_foreign_account_device_or_installed_container_context_fails(self):
        contexts = [('/Users/other', DEVICE, APP), (HOME, CONTAINER, APP),
                    (HOME, DEVICE, APP.parent / 'other.app'), (HOME, DEVICE, APP / 'nested')]
        for home, device, app in contexts:
            with self.subTest(context=(home, device, app)), self.assertRaises(RuntimeError):
                provenance.OwnedToolPaths(self.artifacts, home, device, app)

    def test_second_installed_container_or_false_relative_path_is_rejected(self):
        for variant in ('second-container', 'wrong-relative'):
            artifacts = copy.deepcopy(self.artifacts)
            path = str(APP / RELATIVES[1])
            if variant == 'second-container': artifacts[path.replace(CONTAINER, DEVICE)] = artifacts.pop(path)
            else: artifacts[path]['relative_path'] = 'Other.debug.dylib'
            with self.subTest(variant=variant), self.assertRaises(RuntimeError):
                provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)

    def test_build_and_derived_artifacts_do_not_gain_any_user_alias(self):
        artifacts = copy.deepcopy(self.artifacts)
        for origin, path in (('owned-copy-build', HOME + '/Projects/owned/build/ComposeApp'),
                             ('owned-derived-app', HOME + '/Library/Developer/Xcode/DerivedData/owned/Parlor.app/ComposeApp')):
            artifacts[path] = dict(artifacts[str(APP / RELATIVES[2])], origin=origin)
        policy = provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
        for path, row in artifacts.items():
            if row['origin'] != 'installed-app':
                self.assertEqual(path, policy.resolve(path))
                self.assertIsNone(policy.resolve(alias(path)))

    def test_account_home_outside_users_and_literal_user_account_are_raw_only(self):
        for home in ('/synthetic-home', '/Users/USER'):
            app = Path(str(APP).replace(HOME, home))
            artifacts = inventory(app)
            policy = provenance.OwnedToolPaths(artifacts, home, DEVICE, app)
            self.assertEqual({path: path for path in artifacts}, policy.aliases)

    def test_alias_collisions_cannot_redirect_an_existing_exact_artifact(self):
        artifacts = copy.deepcopy(self.artifacts)
        artifacts[alias(EXE)] = dict(artifacts[str(EXE)], origin='owned-derived-app')
        with self.assertRaises(RuntimeError): provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)

    def test_noncanonical_and_control_character_paths_cannot_gain_aliases(self):
        for value in (str(APP) + '/.', str(APP) + '/../Parlor.app', str(APP) + '/',
                      str(APP).replace('/data/', '//data/'), str(APP) + '\x00', str(APP) + '\n', 'relative/app'):
            with self.subTest(path=value), self.assertRaises(RuntimeError):
                provenance.OwnedToolPaths(self.artifacts, HOME, DEVICE, value)
        for bad in ('../', None, ''):
            artifacts = copy.deepcopy(self.artifacts)
            artifacts[str(EXE)]['relative_path'] = bad
            with self.assertRaises(RuntimeError): provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)

    def test_mutated_inventory_and_selected_artifact_are_not_accepted(self):
        artifacts = copy.deepcopy(self.artifacts)
        artifacts[str(EXE)]['sha256'] = 'f' * 64
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(sample(), PID, EXE, artifacts, tool_paths=self.policy)
        selected = self.parse(sample())
        selected[str(EXE)]['sha256'] = 'f' * 64
        with self.assertRaises(RuntimeError):
            provenance.bind_vmmap(vmmap(), PID, EXE, selected, tool_paths=self.policy)

    def test_raw_plus_alias_sample_duplicates_are_one_canonical_image_and_fail(self):
        for redacted in (False, True):
            extra = image_row(str(EXE) if redacted else alias(EXE), 0, self.artifacts[str(EXE)])
            with self.subTest(redacted=redacted), self.assertRaises(RuntimeError):
                self.parse(sample(redacted=redacted) + extra)

    def test_malformed_raw_and_alias_owned_rows_never_disappear(self):
        for spelling in (str(EXE), alias(EXE)):
            valid = image_row(spelling, 0, self.artifacts[str(EXE)])
            for bad in (valid.replace('<00000001-', '<INVALID-'), valid.replace('0x100000', 'missing'),
                        'malformed duplicate ' + spelling + '\n'):
                with self.subTest(spelling=spelling, bad=bad), self.assertRaises(RuntimeError):
                    self.parse(sample(redacted=True) + bad)

    def test_user_only_presentation_does_not_bypass_uuid_or_unbound_image_checks(self):
        for raw in (sample(redacted=True).replace('00000003-', 'ffffffff-'),
                    sample(redacted=True).replace('/' + CONTAINER + '/Parlor.app/Frameworks/',
                                                  '/' + DEVICE + '/Parlor.app/Frameworks/')):
            with self.subTest(raw=raw), self.assertRaises(RuntimeError): self.parse(raw)

    def test_vmmap_raw_alias_duplicates_unknown_paths_and_ranges_fail(self):
        selected = self.parse(sample(redacted=True))
        raw = vmmap(redacted=True)
        variants = [raw + vmmap().splitlines()[2] + '\n',
                    raw.replace('300000-302000', '301000-303000'),
                    raw.replace('300000-302000', '300000-309000'),
                    raw.replace('300000-302000', 'INVALID-RANGE'),
                    raw.replace('/' + CONTAINER + '/Parlor.app/Frameworks/', '/' + DEVICE + '/Parlor.app/Frameworks/'),
                    '\n'.join(raw.splitlines()[:-1]) + '\n']
        for value in variants:
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                provenance.bind_vmmap(value, PID, EXE, selected, tool_paths=self.policy)

    def test_copied_framework_keeps_distinct_bytes_and_must_have_an_exact_path(self):
        artifacts = copy.deepcopy(self.artifacts)
        installed = str(APP / RELATIVES[2])
        copied = HOME + '/Projects/owned/copy/composeApp/build/ComposeApp.framework/ComposeApp'
        artifacts[copied] = dict(artifacts[installed], origin='owned-copy-build', sha256='f' * 64)
        observed = {path: row for path, row in artifacts.items() if path != installed}
        policy = provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
        selected = provenance.parse_sample(sample(observed), PID, EXE, artifacts, tool_paths=policy)
        self.assertFalse(selected[copied]['same_file_bytes_as_embedded'])
        self.assertEqual('owned-copy-build', selected[copied]['origin'])
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(sample(observed).replace(copied, alias(copied)), PID, EXE, artifacts, tool_paths=policy)


class DriverContextTests(unittest.TestCase):
    def test_elevated_uid_is_rejected_before_account_lookup(self):
        with patch.object(runner.os, 'geteuid', return_value=os.getuid() + 1), \
             patch.object(runner.pwd, 'getpwuid') as lookup, self.assertRaises(RuntimeError):
            runner.owned_tool_paths(inventory(), EXE, DEVICE, SimpleNamespace(uid=os.getuid(), command=str(EXE)))
        lookup.assert_not_called()

    def test_os_account_database_not_environment_supplies_home(self):
        uid = os.getuid()
        attested = SimpleNamespace(uid=uid, command=str(EXE))
        with patch.object(runner.pwd, 'getpwuid', return_value=SimpleNamespace(pw_uid=uid, pw_dir=HOME)) as lookup, \
             patch.dict(os.environ, HOME='/Users/untrusted-environment'), \
             patch.object(Path, 'home', side_effect=AssertionError('HOME must not authorize an alias')):
            policy = runner.owned_tool_paths(inventory(), EXE, DEVICE, attested)
        lookup.assert_called_once_with(uid)
        self.assertEqual(str(EXE), policy.resolve(alias(EXE)))

    def test_foreign_account_or_kernel_record_is_rejected(self):
        uid = os.getuid()
        for account_uid, process_uid, path in ((uid + 1, uid, EXE), (uid, uid + 1, EXE),
                                               (uid, uid, APP / 'Other')):
            with patch.object(runner.pwd, 'getpwuid', return_value=SimpleNamespace(pw_uid=account_uid, pw_dir=HOME)), \
                 self.assertRaises(RuntimeError):
                runner.owned_tool_paths(inventory(), EXE, DEVICE, SimpleNamespace(uid=process_uid, command=str(path)))

    def test_missing_account_database_entry_is_not_replaced_with_environment(self):
        with patch.object(runner.pwd, 'getpwuid', side_effect=KeyError('synthetic missing uid')), self.assertRaises(KeyError):
            runner.owned_tool_paths(inventory(), EXE, DEVICE, SimpleNamespace(uid=os.getuid(), command=str(EXE)))

    def test_production_driver_opts_in_only_after_kernel_attestation_and_passes_one_policy(self):
        source = inspect.getsource(runner.Lane.observe_external_images)
        ordered = ('before = attest_target(', "self.receipt['external_launch_identity'] = unchanged_target(",
                   'tool_paths = owned_tool_paths(artifacts, executable, self.uuid, before)',
                   'parse_sample(raw, pid, executable, artifacts, tool_paths=tool_paths)',
                   'bind_vmmap(raw, pid, executable, selected, tool_paths=tool_paths)')
        positions = [source.index(text) for text in ordered]
        self.assertEqual(sorted(positions), positions)
        helper = inspect.getsource(runner.owned_tool_paths)
        self.assertIn('account = pwd.getpwuid(uid)', helper)
        self.assertNotIn('environ', helper)
        self.assertNotIn('Path.home', helper)


if __name__ == '__main__':
    unittest.main()
