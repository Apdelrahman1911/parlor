"""Disposable data-only fixtures: no native command, signing, simulator or build."""
import ast
import io
import json
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest import mock

from artifact_inventory import digest
import simulator_entitlements as subject
from simulator_signing import signing_overrides


def thin(xml=None, der=None, wide=True, endian='<'):
    payloads = [(name, payload) for name, payload in ((b'__entitlements', xml), (b'__ents_der', der)) if payload is not None]
    header_size, segment_size, section_size = (32, 72, 80) if wide else (28, 56, 68)
    command_size = segment_size + len(payloads) * section_size
    cursor = header_size + command_size
    total = cursor + sum(len(payload) for _, payload in payloads)
    magic = 0xfeedfacf if wide else 0xfeedface
    cpu = 0x100000c if wide else 7
    header = struct.pack(endian + ('8I' if wide else '7I'), magic, cpu, 0, 2, 1, command_size, 0, *([0] if wide else []))
    segment = struct.pack(endian + ('II16s4Q4I' if wide else 'II16s8I'),
                          0x19 if wide else 1, command_size, b'__TEXT', 0, total, 0, total, 5, 5, len(payloads), 0)
    sections = []
    for name, payload in payloads:
        sections.append(struct.pack(endian + ('16s16sQQ8I' if wide else '16s16s9I'),
            name, b'__TEXT', cursor, len(payload), cursor, 0, 0, 0, 0, 0, 0, *([0] if wide else [])))
        cursor += len(payload)
    return header + segment + b''.join(sections) + b''.join(payload for _, payload in payloads)


def inventory(bundle):
    return dict(bundle_identity={'CFBundleIdentifier': subject.APP_ID},
                images=[dict(resolved_path='Parlor', sha256=digest(bundle / 'Parlor'))])


class SimulatorEntitlementTests(unittest.TestCase):
    def test_bounded_file_refuses_growth_or_oversize_without_unbounded_read(self):
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            path = Path(raw) / 'metadata'; path.write_bytes(b'x' * 65)
            with self.assertRaises(RuntimeError): subject.bounded_file(path, 64)
            self.assertEqual(subject.bounded_file(path, 65), b'x' * 65)
        # A pre-read size observation may be stale. Enforce the actual I/O
        # bound, not merely an after-the-fact length check on an unlimited read.
        stream = io.BytesIO(b'x' * 4096)
        read = mock.Mock(wraps=stream.read)
        stream.read = read
        path = mock.Mock()
        path.open.return_value = stream
        with self.assertRaises(RuntimeError): subject.bounded_file(path, 64)
        path.open.assert_called_once_with('rb')
        read.assert_called_once_with(65)

    def test_public_plist_allowlist_preserves_only_expected_public_fields(self):
        values = {'application-identifier': 'SYNTHETIC.com.parlor.app.debug',
                  'com.apple.application-identifier': 'SYNTHETIC.com.parlor.app.debug',
                  'com.apple.developer.team-identifier': 'SYNTHETIC', 'get-task-allow': True,
                  'keychain-access-groups': ['SYNTHETIC.com.parlor.app.debug', 'com.apple.token'],
                  'unknown-private-fixture': 'must-not-enter-receipt'}
        for fmt in (plistlib.FMT_XML, plistlib.FMT_BINARY):
            observed = subject.public_entitlements(plistlib.dumps(values, fmt=fmt))
            self.assertEqual(observed['status'], 'PARSED_PUBLIC_FIELDS_ONLY')
            self.assertEqual(observed['unknown_key_count'], 1)
            self.assertEqual(observed['public'], {k: v for k, v in values.items() if k in subject.PUBLIC_ENTITLEMENTS})
            self.assertNotIn('must-not-enter-receipt', json.dumps(observed))

    def test_malformed_oversized_duplicate_and_unsafe_plists_are_redacted(self):
        samples = [b'not a plist', plistlib.dumps(['wrong root']),
                   plistlib.dumps({'get-task-allow': 1}), plistlib.dumps({'application-identifier': 'unsafe\nvalue'}),
                   plistlib.dumps({'keychain-access-groups': 'not an array'}),
                   b'<plist version="1.0"><dict><key>get-task-allow</key><true/><key>get-task-allow</key><false/></dict></plist>']
        for raw in samples:
            with self.subTest(length=len(raw)):
                self.assertEqual(subject.public_entitlements(raw), {'status': 'MALFORMED_REDACTED'})
        self.assertEqual(subject.public_entitlements(b'x' * (subject.MAX_PLIST_BYTES + 1))['status'], 'OVERSIZED_NOT_PARSED')

    def test_thin_macho_endianness_width_sections_and_absence(self):
        xml = plistlib.dumps({'get-task-allow': True})
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            path = Path(raw) / 'image'
            for wide in (False, True):
                for endian in ('<', '>'):
                    path.write_bytes(thin(xml, b'\x30\x00', wide=wide, endian=endian))
                    values = subject.macho_entitlement_sections(path)[0]['sections']
                    self.assertEqual(values['__entitlements']['payload']['public'], {'get-task-allow': True})
                    self.assertEqual(values['__ents_der']['bytes'], 2)
                    self.assertEqual(values['__ents_der']['payload'], {'status': 'DER_NOT_INTERPRETED'})
            path.write_bytes(thin())
            self.assertEqual(subject.macho_entitlement_sections(path)[0]['sections'],
                             {'__entitlements': {'present': False, 'bytes': 0}, '__ents_der': {'present': False, 'bytes': 0}})

    def test_oversized_section_is_not_read_or_claimed_as_parsed(self):
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            path = Path(raw) / 'image'; path.write_bytes(thin(b'x' * (subject.MAX_PLIST_BYTES + 1)))
            section = subject.macho_entitlement_sections(path)[0]['sections']['__entitlements']
            self.assertEqual(section, {'present': True, 'bytes': subject.MAX_PLIST_BYTES + 1,
                                      'payload': {'status': 'OVERSIZED_NOT_READ'}})

    def test_fat_macho_slices_and_architecture_binding(self):
        first, second = thin(wide=False), thin(wide=True)
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            path = Path(raw) / 'image'
            for wide in (False, True):
                for endian in ('<', '>'):
                    table_size = 8 + 2 * (32 if wide else 20)
                    first_start, second_start = table_size, table_size + len(first)
                    entry = 'IIQQII' if wide else 'IIIII'
                    table = struct.pack(endian + 'II', 0xcafebabf if wide else 0xcafebabe, 2)
                    for cpu, start, payload in ((7, first_start, first), (0x100000c, second_start, second)):
                        table += struct.pack(endian + entry, cpu, 0, start, len(payload), 0, *([0] if wide else []))
                    path.write_bytes(table + first + second)
                    self.assertEqual([row['cpu_type'] for row in subject.macho_entitlement_sections(path)], [7, 0x100000c])
                    alignment_offset = 8 + (24 if wide else 16)
                    for invalid_alignment in (32, 5):
                        # 32 exceeds the accepted exponent; the first slice's
                        # offset (48/72) is not aligned to 2**5 in either table.
                        changed = bytearray(table + first + second)
                        struct.pack_into(endian + 'I', changed, alignment_offset, invalid_alignment)
                        path.write_bytes(changed)
                        with self.subTest(wide=wide, endian=endian, alignment=invalid_alignment), self.assertRaises(RuntimeError):
                            subject.macho_entitlement_sections(path)
                    path.write_bytes(table + first + second)
            malformed = bytearray(path.read_bytes()); struct.pack_into('>I', malformed, 8, 99); path.write_bytes(malformed)
            with self.assertRaises(RuntimeError): subject.macho_entitlement_sections(path)

    def test_macho_malformed_command_bounds_and_section_bounds_refuse(self):
        valid = thin(plistlib.dumps({'get-task-allow': True}))
        changes = [(16, 4097), (20, subject.MAX_COMMAND_BYTES + 1), (32 + 4, 0), (32 + 72 + 48, 0)]
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            path = Path(raw) / 'image'
            for offset, value in changes:
                changed = bytearray(valid); struct.pack_into('<I', changed, offset, value); path.write_bytes(changed)
                with self.subTest(offset=offset), self.assertRaises(RuntimeError): subject.macho_entitlement_sections(path)
            for invalid in (b'', b'not a macho', valid[:10]):
                path.write_bytes(invalid)
                with self.assertRaises(RuntimeError): subject.macho_entitlement_sections(path)

    def test_bundle_requires_exact_owner_app_identity_paths_and_current_hash(self):
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            owner = Path(raw).resolve(); bundle = owner / 'Parlor.app'; bundle.mkdir()
            (bundle / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': subject.APP_ID}))
            (bundle / 'Parlor').write_bytes(thin(plistlib.dumps({'get-task-allow': True})))
            bound = inventory(bundle)
            self.assertEqual(len(subject.inspect_bundle_entitlements(bundle, bound, owner)['images']), 1)
            elsewhere = owner / 'elsewhere'; elsewhere.mkdir()
            with self.assertRaises(RuntimeError): subject.inspect_bundle_entitlements(bundle, bound, elsewhere)
            forged = {**bound, 'images': [{**bound['images'][0], 'resolved_path': '../user-file'}]}
            with self.assertRaises(RuntimeError): subject.inspect_bundle_entitlements(bundle, forged, owner)
            original = bundle / 'original'; (bundle / 'Parlor').rename(original); (bundle / 'Parlor').symlink_to(original)
            with self.assertRaises(RuntimeError): subject.inspect_bundle_entitlements(bundle, bound, owner)
            (bundle / 'Parlor').unlink(); original.rename(bundle / 'Parlor')
            (bundle / 'Parlor').write_bytes(thin())
            with self.assertRaises(RuntimeError): subject.inspect_bundle_entitlements(bundle, bound, owner)
            (bundle / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': 'unrelated.synthetic.app'}))
            with self.assertRaises(RuntimeError): subject.inspect_bundle_entitlements(bundle, inventory(bundle), owner)

    def test_generated_xcent_reads_only_exact_owned_app_paths(self):
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            owner = Path(raw).resolve(); derived = owner / 'DerivedData'; derived.mkdir()
            empty = subject.inspect_generated_app_entitlements(derived, owner)
            self.assertEqual([row['present'] for row in empty['files']], [False, False])
            exact = derived / empty['files'][1]['path']; exact.parent.mkdir(parents=True)
            exact.write_bytes(plistlib.dumps({'application-identifier': 'SYNTHETIC.com.parlor.app.debug'}))
            (exact.parent / 'not-allowlisted.xcent').write_bytes(b'private synthetic fixture must never be opened')
            rows = subject.inspect_generated_app_entitlements(derived, owner)['files']
            self.assertEqual(len(rows), 2)
            self.assertEqual(rows[1]['payload']['status'], 'PARSED_PUBLIC_FIELDS_ONLY')
            exact.write_bytes(b'bad plist')
            self.assertEqual(subject.inspect_generated_app_entitlements(derived, owner)['files'][1]['payload']['status'], 'MALFORMED_REDACTED')
            exact.write_bytes(b'x' * (subject.MAX_PLIST_BYTES + 1))
            self.assertEqual(subject.inspect_generated_app_entitlements(derived, owner)['files'][1]['payload']['status'], 'OVERSIZED_NOT_READ')
            exact.unlink(); exact.symlink_to(exact.parent / 'not-allowlisted.xcent')
            with self.assertRaises(RuntimeError): subject.inspect_generated_app_entitlements(derived, owner)

    def test_sdk_defaults_and_explicit_flags_never_claim_merged_settings(self):
        with tempfile.TemporaryDirectory(prefix='parlor-entitlements-test-') as raw:
            contents = Path(raw).resolve() / 'Xcode.app/Contents'; developer = contents / 'Developer'
            platform = developer / 'Platforms/iPhoneSimulator.platform'; sdk = platform / 'Developer/SDKs/iPhoneSimulator.sdk'
            sdk.mkdir(parents=True)
            (contents / 'version.plist').write_bytes(plistlib.dumps({'CFBundleShortVersionString': '26.5', 'ProductBuildVersion': '17F42'}))
            (platform / 'Info.plist').write_bytes(plistlib.dumps({'DefaultProperties': {'AD_HOC_CODE_SIGNING_ALLOWED': 'NO'}}))
            (sdk / 'SDKSettings.json').write_text(json.dumps({'DefaultProperties': {'AD_HOC_CODE_SIGNING_ALLOWED': 'YES', 'CODE_SIGN_IDENTITY': '-'}}))
            configured = dict(CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO', CODE_SIGN_IDENTITY='', DEVELOPMENT_TEAM='')
            observed = subject.inspect_sdk_signing_defaults(developer, sdk, configured)
            self.assertEqual(observed['observations']['platform_defaults']['values']['AD_HOC_CODE_SIGNING_ALLOWED'], 'NO')
            self.assertEqual(observed['observations']['sdk_defaults']['values']['AD_HOC_CODE_SIGNING_ALLOWED'], 'YES')
            self.assertIn('Not queried merged effective', observed['scope'])
            adhoc = subject.inspect_sdk_signing_defaults(developer, sdk, signing_overrides('adhoc'), 'adhoc')
            self.assertEqual(adhoc['mode'], 'adhoc')
            self.assertEqual(adhoc['observations']['explicit_environment_and_command_overrides'], signing_overrides('adhoc'))
            with self.assertRaises(RuntimeError): subject.inspect_sdk_signing_defaults(developer, sdk, signing_overrides('adhoc'))
            with self.assertRaises(RuntimeError): subject.inspect_sdk_signing_defaults(developer, sdk, configured, 'adhoc')
            with self.assertRaises(RuntimeError): subject.inspect_sdk_signing_defaults(developer, contents, configured)
            with self.assertRaises(RuntimeError): subject.inspect_sdk_signing_defaults(developer, sdk, {**configured, 'DEVELOPMENT_TEAM': 'not-authorized'})

    def test_runner_binds_each_inspection_to_the_explicit_owned_artifact(self):
        runner = (Path(__file__).resolve().parent / 'run_ios_readiness.py').read_text()
        tree = ast.parse(runner)
        calls = [node for node in ast.walk(tree) if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)]
        bundle_calls = [node for node in calls if node.func.id == 'inspect_bundle_entitlements']
        expected = ['inspect_bundle_entitlements(app, built_inventory, temp)',
                    'inspect_bundle_entitlements(installed, installed_inventory, owned_device)']
        self.assertEqual(sorted(ast.dump(node) for node in bundle_calls),
                         sorted(ast.dump(ast.parse(source, mode='eval').body) for source in expected))
        generated = [node for node in calls if node.func.id == 'inspect_generated_app_entitlements']
        self.assertEqual([ast.dump(node) for node in generated],
                         [ast.dump(ast.parse('inspect_generated_app_entitlements(derived, temp)', mode='eval').body)])
        self.assertEqual(sum(node.func.id == 'inspect_sdk_signing_defaults' for node in calls), 1)
        self.assertIn("signing_arguments = [key + '=' + value for key, value in configured_signing.items()]", runner)
        self.assertIn('*signing_arguments,', runner)


if __name__ == '__main__':
    unittest.main()
