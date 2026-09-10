"""Synthetic control regressions only; none of these tests execute Parlor."""
import copy
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

import application_receipts as receipts
from application_probe import ApplicationLane, HOST_SDK_MACRO_NAMES, parse_host_sdk_macros


TOKEN = 'application-control-fixture'
DEVICE = '01234567-89ab-cdef-0123-456789abcdef'
SOURCE = 'a' * 64
CONTROLS = 'b' * 64
BINDINGS = dict(token=TOKEN, device=DEVICE, source=SOURCE, controls=CONTROLS)


def image(platform=7):
    return dict(image_basename='Foundation', uuid=DEVICE, platforms=[platform],
                cputype=0x100000c, cpusubtype=0, image_offset=100, dylib=None)


def native(target, inode, platform=7):
    directory = target == 'directory'
    identity = dict(device=1, inode=inode, uid=501, mode=0o40700 if directory else 0o100600,
                    links=2 if directory else 1, size=64, type='directory' if directory else 'regular')
    value = dict(schema=1, collection_status='PASS', identity=identity, descriptor_closed=True)
    value['reads'] = [dict(kind=kind, before=copy.deepcopy(identity), after=copy.deepcopy(identity),
                           descriptor_and_path_same_inode=True) for kind in receipts.READS]
    for kind, selector in (('fm', 'attributesOfItemAtPath:error:'), ('url', 'resourceValuesForKeys:error:')):
        impl = dict(receiver_class='NSFileManager' if kind == 'fm' else 'NSURL',
                    selector=selector, implementation=image(platform))
        value[kind] = dict(dictionary_present=True, key_present=False, protection='missing',
                           native_error=dict(present=False, code=0, domain='none'),
                           implementation_before=impl, implementation_after=copy.deepcopy(impl))
    value['url'].update(fresh_url=True, is_directory=directory, volume_support='unsupported',
                        protection='NOT_APPLICABLE_DIRECTORY' if directory else 'missing')
    for kind in ('fcntl', 'attrlist'):
        value[kind] = dict(sdk_available=False, status='PUBLIC_SDK_SYMBOL_UNAVAILABLE')
    value['filesystem'] = dict(return_value=0, errno=0, type='apfs', fsid=[1, 2], flags=0,
                               content_protection_capability='PUBLIC_SDK_SYMBOL_UNAVAILABLE')
    return value


def application():
    final = dict(schema_version=1, kind='protection-application', collection_status=receipts.COLLECTED,
                 recorded_samples=6, run_token=TOKEN, simulator_udid=DEVICE,
                 source_manifest_sha256=SOURCE, controls_sha256=CONTROLS,
                 bundle_id='com.parlor.app.debug', runtime_version=[26, 2, 0],
                 process_boot=DEVICE, container_id=DEVICE, process_id=123, uid=501,
                 hardware_protection_verified=False, l08_requirements_waived=False,
                 snapshot_validation_tested=False)
    samples, observed = [], []
    for index, identifier in enumerate(receipts.IDS):
        target = 'directory' if index < 2 else 'file'
        sampled = native(target, 10 if index < 2 else 20 if index < 4 else 21)
        st = sampled['identity']
        identity = dict(device=st['device'], inode=st['inode'], uid=st['uid'], links=st['links'],
                        bytes=st['size'], file_type=st['mode'] & 0xf000)
        row = dict(ordinal=index + 1, id=identifier, target=target,
                   identity_before=identity, identity_after=copy.deepcopy(identity),
                   kotlin_options=dict(receipts.OPTIONS, protection_key='NSFileProtectionKey',
                                       complete_value='NSFileProtectionComplete'),
                   kotlin_fm=dict(value='missing', original_equality=False, comparison='FAIL',
                                  dictionary_present=True, key_present=False, error_present=False, error_code=0))
        observed.append(row)
        samples.append(dict(schema_version=1, kind='protection-application-sample', run_token=TOKEN,
                            simulator_udid=DEVICE, process_boot=DEVICE, process_id=123,
                            ordinal=index + 1, kotlin_native_same_inode=True, kotlin=row, native=sampled))
    final['kotlin'] = dict(kind='protection-application-kotlin', run_token=TOKEN,
                           status='OBSERVATIONS_COMPLETE', stage='complete', failure='none',
                           first_roundtrip=True, overwrite_roundtrip=True, samples=observed)
    return final, samples


def host(final):
    return dict(schema_version=1, kind='protection-application-host-reader',
                collection_status=receipts.COLLECTED, controls_sha256=CONTROLS,
                source_manifest_sha256=SOURCE, run_token=TOKEN, simulator_udid=DEVICE,
                read_only=True, same_ios_process=False, hardware_protection_verified=False,
                l08_requirements_waived=False, reader_image=image(1), reader_image_after=image(1),
                process_id=456, uid=final['uid'], runtime_version=[15, 7, 0],
                samples=[dict(target='directory', native=native('directory', 10, 1)),
                         dict(target='file', native=native('file', 21, 1))])


def absent_attrlist():
    return dict(sdk_available=True, return_value=0, errno=0, returned_attributes_mask=0x80000000,
                data_protection_mask=0x40000000, requested_common_mask=0xc0000000,
                returned_common_mask=0x80000000, attribute_set_bytes=20, length=24,
                protection_returned=False, **{'class': None})


class HostSdkMacroTests(unittest.TestCase):
    def test_non_utf8_unrelated_macro_does_not_poison_ascii_definition(self):
        raw = b'#define UNRELATED_SDK_MACRO "\xa9"\n#define F_GETPROTECTIONCLASS 63\n'
        macros = parse_host_sdk_macros(raw)
        self.assertEqual(macros['F_GETPROTECTIONCLASS'], dict(available=True, definition='63'))
        self.assertEqual(set(macros), set(HOST_SDK_MACRO_NAMES))
        self.assertEqual(macros['PROTECTION_CLASS_A'], dict(available=False, definition=None))

    def test_definition_bytes_are_not_normalized_or_evaluated(self):
        macros = parse_host_sdk_macros(b'#define MNT_CPROTECT  (0x80U) /* SDK */\n')
        self.assertEqual(macros['MNT_CPROTECT'], dict(available=True, definition=' (0x80U) /* SDK */'))

    def test_non_ascii_or_control_byte_in_selected_definition_fails(self):
        for value in (b'6\xa93', b'\xff', b'63\r', b'63\x00'):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                parse_host_sdk_macros(b'#define F_GETPROTECTIONCLASS ' + value + b'\n')

    def test_duplicate_named_definition_fails_even_when_identical(self):
        with self.assertRaises(RuntimeError):
            parse_host_sdk_macros(b'#define F_GETPROTECTIONCLASS 63\n' * 2)

    def test_empty_function_like_and_oversized_named_definitions_fail(self):
        for suffix in (b'', b' ', b'(x) x', b'\t63', b' ' + b'1' * 257):
            with self.subTest(suffix=suffix), self.assertRaises(RuntimeError):
                parse_host_sdk_macros(b'#define F_GETPROTECTIONCLASS' + suffix + b'\n')

    def test_bounds_and_exact_name_absence_remain_explicit(self):
        macros = parse_host_sdk_macros(b'#define F_GETPROTECTIONCLASS_OTHER 63\n')
        self.assertTrue(all(row == dict(available=False, definition=None) for row in macros.values()))
        for raw in (b'', b'x' * (4 * 1024 * 1024 + 1)):
            with self.assertRaises(RuntimeError):
                parse_host_sdk_macros(raw)

    def test_raw_archive_survives_selected_definition_failure(self):
        raw = b'#define UNRELATED_SDK_MACRO "\xa9"\n#define F_GETPROTECTIONCLASS 6\xff\n'
        with tempfile.TemporaryDirectory(prefix='parlor-host-sdk-test-') as directory:
            work = Path(directory).resolve()
            destination, sdk, include = work / 'evidence', work / 'sdk', work / 'include'
            destination.mkdir()
            include.mkdir()
            (sdk / 'usr/include/sys').mkdir(parents=True)
            for name in ('fcntl.h', 'attr.h', 'mount.h'):
                (sdk / 'usr/include/sys' / name).write_bytes(b'// Synthetic SDK header; never compiled.\n')
            source = work / 'host.m'
            source.write_bytes(b'// Synthetic host source; never compiled.\n')
            (include / 'ProtectionSampler.m').write_bytes(b'// Synthetic sampler; never compiled.\n')
            calls = []

            def fake_command(arguments, label, **options):
                calls.append(label)
                if label == 'host-reader-sdk-path.log':
                    (destination / label).write_text(str(sdk) + '\n')
                else:
                    self.assertEqual(label, 'host-sdk-macros')
                    self.assertEqual(arguments[-1], include / 'ProtectionSampler.m')
                    self.assertEqual(options['limit'], 4 * 1024 * 1024)
                    options['output'].write_bytes(raw)

            lane = SimpleNamespace(temporary=work, destination=destination,
                                   environment={'DEVELOPER_DIR': str(work)}, require=fake_command)
            with self.assertRaisesRegex(RuntimeError, 'protection-application-host-sdk-macro'):
                ApplicationLane.preserve_host_sdk(lane, source, include)
            compressed = (destination / 'host-reader-sdk-macros.raw.gz').read_bytes()
            identity = json.loads((destination / 'host-reader-sdk-macros-identity.json').read_text())
            self.assertEqual(gzip.decompress(compressed), raw)
            self.assertEqual((identity['raw_sha256'], identity['raw_bytes']),
                             (hashlib.sha256(raw).hexdigest(), len(raw)))
            self.assertEqual((identity['artifact_sha256'], identity['artifact_bytes']),
                             (hashlib.sha256(compressed).hexdigest(), len(compressed)))
            self.assertEqual(calls, ['host-reader-sdk-path.log', 'host-sdk-macros'])
            self.assertFalse((destination / 'host-reader-sdk-bindings.json').exists())


class ApplicationReceiptTests(unittest.TestCase):
    def test_missing_is_collection_not_protection_pass(self):
        result = receipts.validate_application(*application(), **BINDINGS)
        self.assertEqual((result['collection_status'], result['strict_status'], result['strict_passed']),
                         (receipts.COLLECTED, 'FAIL', 0))
        self.assertTrue(result['overwrite_inode_replaced'])
        self.assertFalse(result['original_l08_comparisons_reclassified'])
        self.assertFalse(result['application_runtime_qualification'])

    def test_synthetic_complete_does_not_qualify_application(self):
        final, samples = application()
        for row in final['kotlin']['samples']:
            row['kotlin_fm'].update(value='complete', original_equality=True, key_present=True, comparison='PASS')
        result = receipts.validate_application(final, samples, **BINDINGS)
        self.assertEqual((result['strict_status'], result['strict_passed']), ('PASS', 6))
        self.assertFalse(result['application_runtime_qualification'])
        self.assertFalse(result['hardware_protection_verified'])

    def test_error_bearing_complete_preserves_equality_but_fails_strict(self):
        final, samples = application()
        fm = final['kotlin']['samples'][0]['kotlin_fm']
        fm.update(value='complete', original_equality=True, key_present=True, error_present=True, error_code=1)
        self.assertEqual(receipts.validate_application(final, samples, **BINDINGS)['strict_passed'], 0)
        fm['comparison'] = 'PASS'
        with self.assertRaises(RuntimeError):
            receipts.validate_application(final, samples, **BINDINGS)

    def test_process_boot_pid_uid_and_source_are_required(self):
        for key, value in (('process_boot', None), ('process_id', True), ('uid', 0),
                           ('uid', 502), ('source_manifest_sha256', 'c' * 64)):
            with self.subTest(key=key, value=value):
                final, samples = application()
                final[key] = value
                with self.assertRaises(RuntimeError):
                    receipts.validate_application(final, samples, **BINDINGS)

    def test_open_descriptor_and_changed_per_api_inode_fail(self):
        for mutation in ('open', 'changed', 'witness'):
            with self.subTest(mutation=mutation):
                value = native('file', 20)
                if mutation == 'open': value['descriptor_closed'] = False
                elif mutation == 'changed': value['reads'][2]['before']['inode'] += 1
                else: value['reads'][2]['descriptor_and_path_same_inode'] = False
                with self.assertRaises(RuntimeError):
                    receipts.validate_native(value, 'file')

    def test_directory_url_cannot_assert_regular_file_protection(self):
        value = native('directory', 10)
        value['url'].update(protection='complete', key_present=True)
        with self.assertRaises(RuntimeError):
            receipts.validate_native(value, 'directory')

    def test_missing_attrlist_protection_is_null_not_zero(self):
        value = native('file', 20)
        value['attrlist'] = absent_attrlist()
        receipts.validate_native(value, 'file')
        value['attrlist']['class'] = 0
        with self.assertRaises(RuntimeError):
            receipts.validate_native(value, 'file')

    def test_attrlist_masks_lengths_and_result_fail_closed(self):
        for key, item in (('data_protection_mask', 0), ('requested_common_mask', 0x80000000),
                          ('attribute_set_bytes', 16), ('length', 28), ('return_value', 1)):
            with self.subTest(key=key):
                value = native('file', 20)
                value['attrlist'] = absent_attrlist()
                value['attrlist'][key] = item
                with self.assertRaises(RuntimeError):
                    receipts.validate_native(value, 'file')

    def test_fcntl_raw_positive_class_is_success_not_errno(self):
        value = native('file', 20)
        value['fcntl'] = dict(sdk_available=True, command=63, return_value=1, errno=0, **{'class': 1})
        receipts.validate_native(value, 'file')
        value['fcntl']['class'] = 0
        with self.assertRaises(RuntimeError):
            receipts.validate_native(value, 'file')

    def test_unavailable_api_cannot_carry_invented_class(self):
        for kind in ('fcntl', 'attrlist'):
            value = native('file', 20)
            value[kind]['class'] = 0
            with self.assertRaises(RuntimeError):
                receipts.validate_native(value, 'file')

    def test_host_same_inode_is_not_same_ios_process_or_causal_proof(self):
        final, samples = application()
        result = receipts.validate_host(host(final), final, samples)
        self.assertTrue(all(row['same_inode'] for row in result['comparisons']))
        self.assertFalse(result['same_ios_process'])
        self.assertFalse(result['runtime_implementation_causality_proven'])

    def test_host_changed_inode_platform_uid_or_executable_fails(self):
        for mutation in ('inode', 'platform', 'uid', 'image'):
            with self.subTest(mutation=mutation):
                final, samples = application()
                value = host(final)
                if mutation == 'inode': value['samples'][1]['native'] = native('file', 22, 1)
                elif mutation == 'platform':
                    value['reader_image'] = image(7)
                    value['reader_image_after'] = image(7)
                elif mutation == 'uid': value['uid'] = 502
                else: value['reader_image_after']['image_offset'] += 1
                with self.assertRaises(RuntimeError):
                    receipts.validate_host(value, final, samples)

    def test_metadata_rejects_paths_and_raw_control_characters(self):
        for value in ({'path': '/private/secret'}, {'error': 'line\ncontents'}, {'data': b'secret'}):
            with self.assertRaises(RuntimeError):
                receipts.metadata_only(value)


if __name__ == '__main__':
    unittest.main()
