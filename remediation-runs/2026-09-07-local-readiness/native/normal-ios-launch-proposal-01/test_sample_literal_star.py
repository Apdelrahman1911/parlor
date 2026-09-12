"""Synthetic sample-only literal-star controls; never native image evidence."""
import copy
import json
import unittest

import external_image_diagnostics as diagnostic
import external_image_provenance as provenance
from test_owned_tool_paths import APP, CONTAINER, DEVICE, EXE, HOME, PID, RELATIVES, alias, header, image_row, inventory, vmmap


def star(path):
    return '/Users/*' + str(path)[len(HOME):]


def star_sample(artifacts=None):
    artifacts = inventory() if artifacts is None else artifacts
    return header(alias(EXE)) + '\nBinary Images:\n' + ''.join(
        image_row(star(path) if row['origin'] == 'installed-app' else path, index, row)
        for index, (path, row) in enumerate(artifacts.items()))


def star_vmmap_rows():
    return header() + ''.join(vmmap().splitlines(True)[2:]).replace(HOME, '/Users/*')


class SampleLiteralStarTests(unittest.TestCase):
    def setUp(self):
        self.artifacts = inventory()
        self.policy = provenance.OwnedToolPaths(self.artifacts, HOME, DEVICE, APP)

    def parse(self, raw):
        return provenance.parse_sample(raw, PID, EXE, self.artifacts, tool_paths=self.policy)

    def test_observed_user_header_and_literal_star_rows_bind_with_distinct_lossy_labels(self):
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(star_sample(), PID, EXE, self.artifacts)
        selected = self.parse(star_sample())
        self.assertEqual(set(selected), set(self.artifacts))
        for path, row in selected.items():
            self.assertEqual('owned-literal-star-user-presentation', row['sample_path_kind'])
            self.assertEqual('owned-USER-only-presentation', row['sample_header_path_kind'])
            self.assertEqual(self.artifacts[path]['sha256'], row['sha256'])
            self.assertIsNone(self.policy.resolve(star(path)))
            self.assertEqual(path, self.policy.resolve(star(path), sample=True))
        self.assertLessEqual(len(self.policy.sample_aliases), 3 * len(self.artifacts))
        for redacted in (False, True):
            result = provenance.bind_vmmap(vmmap(redacted=redacted), PID, EXE, selected, tool_paths=self.policy)
            self.assertEqual('PASS', result['status'])
            self.assertIn('lossy tool presentation alias', result['limitation'])
            self.assertTrue(all(row['vmmap_path_kind'] == ('owned-USER-only-presentation' if redacted else 'exact')
                                for row in result['images']))

    def test_literal_star_is_not_authorized_in_headers_or_vmmap(self):
        with self.assertRaises(RuntimeError): provenance.verify_header(header(star(EXE)), PID, EXE, tool_paths=self.policy)
        with self.assertRaises(RuntimeError): self.parse(star_sample().replace(header(alias(EXE)), header(star(EXE))))
        with self.assertRaises(RuntimeError):
            provenance.bind_vmmap(star_vmmap_rows(), PID, EXE, self.parse(star_sample()), tool_paths=self.policy)

    def test_every_other_component_partial_star_and_foreign_username_remain_exact(self):
        parts = star(EXE).split('/')
        values = []
        for index in range(1, len(parts)):
            changed = list(parts); changed[index] = 'OTHER' if index == 2 else '*'
            values.append('/'.join(changed))
        values += [star(EXE).replace('/Users/*/', '/Users/' + marker + '/')
                   for marker in ('**', '*owner', 'owner*', '?', '...', '~', '<redacted>', 'OTHER')]
        for value in values:
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                self.parse(star_sample().replace(star(EXE), value))

    def test_copied_build_and_derived_origins_never_gain_star_aliases(self):
        for origin in ('owned-copy-build', 'owned-derived-app'):
            artifacts = copy.deepcopy(self.artifacts)
            embedded = str(APP / RELATIVES[2]); owned = HOME + '/Projects/owned/' + origin + '/ComposeApp.framework/ComposeApp'
            artifacts[owned] = dict(artifacts[embedded], origin=origin, sha256='f' * 64)
            observed = {path: row for path, row in artifacts.items() if path != embedded}
            policy = provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
            self.assertEqual((owned,), policy.forms(owned, sample=True))
            selected = provenance.parse_sample(star_sample(observed), PID, EXE, artifacts, tool_paths=policy)
            self.assertEqual(origin, selected[owned]['origin'])
            self.assertFalse(selected[owned]['same_file_bytes_as_embedded'])
            with self.subTest(origin=origin), self.assertRaises(RuntimeError):
                provenance.parse_sample(star_sample(observed).replace(owned, star(owned)), PID, EXE, artifacts, tool_paths=policy)

    def test_exact_star_collision_and_inventory_mutation_fail_closed(self):
        artifacts = copy.deepcopy(self.artifacts)
        artifacts[star(EXE)] = dict(artifacts[str(EXE)], origin='owned-derived-app')
        with self.assertRaises(RuntimeError): provenance.OwnedToolPaths(artifacts, HOME, DEVICE, APP)
        changed = copy.deepcopy(self.artifacts); changed[str(EXE)]['sha256'] = 'f' * 64
        with self.assertRaises(RuntimeError):
            provenance.parse_sample(star_sample(), PID, EXE, changed, tool_paths=self.policy)

    def test_literal_star_cannot_bypass_uuid_ranges_duplicates_or_container_binding(self):
        raw = star_sample()
        for value in (raw.replace('00000001-', 'ffffffff-'), raw.replace('0x100000', '0x0'),
                      raw.replace('0x100000', '0x104000'), raw.replace('0x100000', '0x10000000000000000'),
                      raw + image_row(str(EXE), 0, self.artifacts[str(EXE)]),
                      raw + image_row(alias(EXE), 0, self.artifacts[str(EXE)]),
                      raw + 'malformed ' + star(EXE) + '\n',
                      raw.replace(star(APP / RELATIVES[2]), star(APP / RELATIVES[2]).replace(CONTAINER, DEVICE))):
            with self.subTest(value=value), self.assertRaises(RuntimeError): self.parse(value)

    def test_failure_diagnostics_distinguish_star_without_granting_vmmap_authority(self):
        value = diagnostic.image_diagnostic(star_sample(), 'sample', self.artifacts, tool_paths=self.policy)
        self.assertFalse(value['proves_provenance'])
        self.assertNotIn(HOME, json.dumps(value))
        for row in value['candidates']:
            self.assertEqual('OWNED_LITERAL_STAR_USER_PRESENTATION', row['path']['classification'])
            self.assertEqual(1, len(row['path']['owned_presentation_artifact_indexes']))
            self.assertTrue(row['uuid']['matches_bound_path_uuid'])
        without_policy = diagnostic.image_diagnostic(star_sample(), 'sample', self.artifacts)
        mappings = diagnostic.image_diagnostic(star_vmmap_rows(), 'vmmap', self.artifacts, tool_paths=self.policy)
        for rejected in (without_policy, mappings):
            self.assertTrue(all(row['path']['classification'] == 'UNBOUND' for row in rejected['candidates']))


if __name__ == '__main__':
    unittest.main()
