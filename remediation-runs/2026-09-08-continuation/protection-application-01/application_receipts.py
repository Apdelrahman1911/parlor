"""Small, pure validators. Collection never substitutes for protection PASS."""
import re

COLLECTED = 'CAPTURED_NOT_PROTECTION_PASS'
IDS = ('directory-before-backup', 'directory-after-backup',
       'write-1-before-backup', 'write-1-after-backup',
       'write-2-before-backup', 'write-2-after-backup')
READS = ('fm', 'url', 'fcntl', 'attrlist', 'filesystem')
OPTIONS = dict(atomic=1, complete=0x20000000, combined=0x20000001)
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}\Z')


def require(value, reason):
    if not value:
        raise RuntimeError('protection-application-' + reason)


def metadata_only(value, depth=0, budget=None):
    """Never retain arbitrary paths, error descriptions, bytes or unbounded data."""
    budget = [0] if budget is None else budget
    budget[0] += 1
    require(depth <= 24 and budget[0] <= 16000, 'metadata-budget')
    if isinstance(value, dict):
        require(len(value) <= 64 and all(isinstance(key, str) for key in value), 'metadata-object')
        for key, item in value.items():
            metadata_only(key, depth + 1, budget)
            metadata_only(item, depth + 1, budget)
    elif isinstance(value, list):
        require(len(value) <= 64, 'metadata-array')
        for item in value:
            metadata_only(item, depth + 1, budget)
    elif isinstance(value, str):
        require(len(value.encode()) <= 256 and not any(ord(c) < 32 or ord(c) == 127 for c in value)
                and '/' not in value and '\\' not in value, 'metadata-string')
    else:
        require(value is None or type(value) is bool or type(value) is int and -(2**63) <= value < 2**64,
                'metadata-scalar')


def identity(value):
    require(isinstance(value, dict) and set(value) == {'device', 'inode', 'uid', 'mode', 'links', 'size', 'type'}, 'native-identity')
    require(value['type'] in ('regular', 'directory') and
            all(type(value[key]) is int and value[key] >= 0 for key in set(value) - {'type'}) and
            value['device'] > 0 and value['inode'] > 0 and value['links'] >= 1 and value['size'] <= 16 * 1024 * 1024,
            'native-identity-values')
    require(value['mode'] & 0xf000 == (0x8000 if value['type'] == 'regular' else 0x4000) and
            (value['type'] != 'regular' or value['links'] == 1), 'native-file-type')
    return value


def image(value, platform, *, host_method=None):
    require(type(platform) is int and platform in (1, 7) and
            (host_method is None or platform == 1 and host_method in ('fm', 'url')), 'loaded-image-role')
    require(isinstance(value, dict) and set(value) == {'image_basename', 'uuid', 'platforms', 'cputype', 'cpusubtype', 'image_offset', 'dylib'} and
            isinstance(value['image_basename'], str) and bool(value['image_basename']) and
            isinstance(value['uuid'], str) and re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', value['uuid']) and
            type(value['platforms']) is list and all(type(item) is int for item in value['platforms']) and
            (value['platforms'] == [platform] or platform == 1 and host_method in ('fm', 'url') and
                value['image_basename'] == ('Foundation' if host_method == 'fm' else 'CoreFoundation') and
                value['platforms'] == [1, 6]) and value['cputype'] == 0x100000c and
            type(value['cpusubtype']) is int and type(value['image_offset']) is int and 0 <= value['image_offset'] < 2**40,
            'loaded-image')
    dylib = value['dylib']
    require(dylib is None or isinstance(dylib, dict) and set(dylib) == {'current_version', 'compatibility_version'} and
            all(type(item) is int and 0 <= item < 2**32 for item in dylib.values()), 'loaded-image-version')


def validate_native(value, target, platform=7):
    require(isinstance(value, dict) and set(value) == {'schema', 'collection_status', 'reads', 'identity',
        'fm', 'url', 'fcntl', 'attrlist', 'filesystem', 'descriptor_closed'}, 'native-sample-shape')
    require(type(value['schema']) is int and value['schema'] == 1 and value['collection_status'] == 'PASS' and
            value['descriptor_closed'] is True, 'native-collection')
    selected = identity(value['identity'])
    require(selected['type'] == ('directory' if target == 'directory' else 'regular'), 'native-target')
    require(isinstance(value['reads'], list) and len(value['reads']) == 5, 'native-read-count')
    for kind, row in zip(READS, value['reads']):
        require(set(row) == {'kind', 'before', 'after', 'descriptor_and_path_same_inode'} and row['kind'] == kind and
                row['before'] == selected == row['after'] and row['descriptor_and_path_same_inode'] is True,
                'native-per-api-inode')
    for kind in ('fm', 'url'):
        row = value[kind]
        keys = {'dictionary_present', 'key_present', 'protection', 'native_error', 'implementation_before', 'implementation_after'}
        if kind == 'url': keys |= {'volume_support', 'fresh_url', 'is_directory'}
        require(isinstance(row, dict) and set(row) == keys and row.get('implementation_before') == row.get('implementation_after') and
                type(row.get('dictionary_present')) is bool and type(row.get('key_present')) is bool and
                isinstance(row.get('native_error'), dict), 'foundation-observation')
        impl = row['implementation_before']
        require(isinstance(impl, dict) and set(impl) == {'receiver_class', 'selector', 'implementation'} and
                isinstance(impl['receiver_class'], str) and bool(impl['receiver_class']) and impl['selector'] ==
                ('attributesOfItemAtPath:error:' if kind == 'fm' else 'resourceValuesForKeys:error:'), 'foundation-implementation')
        # H01 observed only these native-host getter roles as macOS/Catalyst
        # images. The reader main and every simulator image stay singleton.
        image(impl['implementation'], platform, host_method=kind if platform == 1 else None)
        error = row['native_error']
        require(set(error) == {'present', 'code', 'domain'} and type(error['present']) is bool and type(error['code']) is int and
                error['domain'] in ('none', 'cocoa', 'posix', 'other') and
                (error['present'] and error['domain'] != 'none' or not error['present'] and error['code'] == 0 and error['domain'] == 'none'), 'foundation-error')
        require(row['protection'] in ('missing', 'null', 'non-string', 'complete', 'none', 'unless-open',
                'until-first-authentication', 'when-user-inactive', 'other-string', 'NOT_APPLICABLE_DIRECTORY'), 'foundation-protection-observation')
        require(not row['key_present'] or row['dictionary_present'], 'foundation-key-without-dictionary')
        if not (kind == 'url' and target == 'directory'):
            require(row['key_present'] is (row['protection'] != 'missing') and row['protection'] != 'NOT_APPLICABLE_DIRECTORY', 'foundation-missing-key')
    require(value['url'].get('fresh_url') is True and value['url'].get('is_directory') is (target == 'directory'), 'fresh-url')
    require(target != 'directory' or value['url']['protection'] == 'NOT_APPLICABLE_DIRECTORY', 'regular-file-url-only')
    require(target != 'directory' or value['url']['key_present'] is False, 'unrequested-directory-url-key')
    require(value['url']['volume_support'] in ('supported', 'unsupported', 'non-number', 'missing'), 'volume-observation')
    # API-unavailable and syscall errors are retained observations, never PASS
    # for the protection class. No class value is invented or translated here.
    for kind in ('fcntl', 'attrlist'):
        row = value[kind]
        require(isinstance(row, dict) and type(row.get('sdk_available')) is bool, 'sdk-availability')
        if not row['sdk_available']:
            require(row == {'sdk_available': False, 'status': 'PUBLIC_SDK_SYMBOL_UNAVAILABLE'}, 'unavailable-api')
        else:
            require(type(row.get('return_value')) is int and type(row.get('errno')) is int and row['errno'] >= 0, 'syscall-observation')
            result, error = row['return_value'], row['errno']
            require(result == -1 and error > 0 and row.get('class') is None or result >= 0 and error == 0, 'syscall-result-error')
            if kind == 'fcntl':
                require(set(row) == {'sdk_available', 'command', 'return_value', 'errno', 'class'} and
                        type(row['command']) is int and row['command'] > 0 and
                        (result == -1 or type(row['class']) is int and row['class'] == result), 'fcntl-class-result')
            else:
                require(set(row) == {'sdk_available', 'return_value', 'errno', 'length', 'returned_common_mask',
                    'requested_common_mask', 'returned_attributes_mask', 'data_protection_mask', 'attribute_set_bytes',
                    'protection_returned', 'class'} and result in (-1, 0), 'attrlist-layout')
                require(all(type(row[key]) is int and 0 <= row[key] < 2**32 for key in
                    ('length', 'returned_common_mask', 'requested_common_mask', 'returned_attributes_mask', 'data_protection_mask', 'attribute_set_bytes')) and
                    type(row['protection_returned']) is bool and row['attribute_set_bytes'] == 20, 'attrlist-values')
                returned, protection = row['returned_attributes_mask'], row['data_protection_mask']
                require(returned > 0 and protection > 0 and returned & (returned - 1) == 0 and protection & (protection - 1) == 0 and
                        returned != protection and row['requested_common_mask'] == returned | protection, 'attrlist-requested-mask')
                present = result == 0 and bool(row['returned_common_mask'] & protection)
                require(row['protection_returned'] is present and
                    (present and type(row['class']) is int and 0 <= row['class'] < 2**32 or not present and row['class'] is None), 'attrlist-absent-is-not-zero')
                require(result != 0 or row['length'] == 24 + (4 if present else 0) and
                    not row['returned_common_mask'] & ~row['requested_common_mask'], 'attrlist-returned-layout')
    fs = value['filesystem']
    require(isinstance(fs, dict) and type(fs.get('return_value')) is int and type(fs.get('errno')) is int, 'filesystem-observation')
    if fs['return_value'] == -1:
        require(set(fs) == {'return_value', 'errno'} and fs['errno'] > 0, 'filesystem-error')
    else:
        require(fs['return_value'] == 0 and fs['errno'] == 0 and isinstance(fs.get('type'), str) and bool(fs['type']) and
                isinstance(fs.get('fsid'), list) and len(fs['fsid']) == 2 and all(type(v) is int for v in fs['fsid']) and
                type(fs.get('flags')) is int, 'filesystem-success')
        capability = fs.get('content_protection_capability')
        keys = {'return_value', 'errno', 'type', 'fsid', 'flags', 'content_protection_capability'}
        if capability == 'PUBLIC_SDK_SYMBOL_UNAVAILABLE':
            require(set(fs) == keys, 'filesystem-capability-unavailable')
        else:
            require(set(fs) == keys | {'content_protection_flag'} and type(fs['content_protection_flag']) is int and
                    fs['content_protection_flag'] > 0 and capability ==
                    ('supported' if fs['flags'] & fs['content_protection_flag'] else 'unsupported'), 'filesystem-capability')
    return selected


def kotlin_matches(kotlin, native):
    require(isinstance(kotlin, dict) and set(kotlin) == {'device', 'inode', 'uid', 'file_type', 'links', 'bytes'}, 'kotlin-identity')
    return (all(kotlin[key] == native[key] for key in ('device', 'inode', 'uid', 'links')) and
            kotlin['bytes'] == native['size'] and kotlin['file_type'] == native['mode'] & 0xf000)


def validate_application(final, samples, *, token, device, source, controls):
    metadata_only(final)
    for sample in samples:
        metadata_only(sample)
    require(type(final.get('schema_version')) is int and final['schema_version'] == 1 and final.get('kind') == 'protection-application' and
            final.get('collection_status') == COLLECTED and final.get('recorded_samples') == 6 and
            final.get('run_token') == token and final.get('simulator_udid') == device and
            final.get('source_manifest_sha256') == source and final.get('controls_sha256') == controls and
            final.get('bundle_id') == 'com.parlor.app.debug' and final.get('runtime_version') == [26, 2, 0] and
            all(final.get(key) is False for key in ('hardware_protection_verified', 'l08_requirements_waived', 'snapshot_validation_tested')),
            'application-context')
    require(isinstance(final.get('process_boot'), str) and UUID.fullmatch(final['process_boot']) and
            isinstance(final.get('container_id'), str) and UUID.fullmatch(final['container_id']) and
            type(final.get('process_id')) is int and 0 < final['process_id'] < 2**31 and
            type(final.get('uid')) is int and 0 < final['uid'] < 2**32, 'application-process-identity')
    kotlin = final.get('kotlin', {})
    require(kotlin.get('kind') == 'protection-application-kotlin' and kotlin.get('run_token') == token and
            kotlin.get('status') == 'OBSERVATIONS_COMPLETE' and kotlin.get('stage') == 'complete' and
            kotlin.get('failure') == 'none' and kotlin.get('first_roundtrip') is True and
            kotlin.get('overwrite_roundtrip') is True and len(kotlin.get('samples', [])) == len(samples) == 6,
            'real-write-roundtrips')
    comparisons, identities = [], []
    for index, (row, expected_id) in enumerate(zip(samples, IDS)):
        require(row.get('schema_version') == 1 and row.get('kind') == 'protection-application-sample' and
                row.get('run_token') == token and row.get('simulator_udid') == device and
                row.get('process_boot') == final.get('process_boot') and row.get('process_id') == final.get('process_id') and
                row.get('ordinal') == index + 1 and row.get('kotlin_native_same_inode') is True,
                'sample-context')
        observed = row.get('kotlin', {})
        target = 'directory' if index < 2 else 'file'
        require(observed == kotlin['samples'][index] and observed.get('ordinal') == index + 1 and
                observed.get('id') == expected_id and observed.get('target') == target and
                observed.get('identity_before') == observed.get('identity_after'), 'sample-sequence')
        native = validate_native(row.get('native'), target)
        require(native['uid'] == final['uid'], 'application-file-uid')
        require(kotlin_matches(observed['identity_before'], native), 'kotlin-native-inode')
        options = observed.get('kotlin_options', {})
        require(all(type(options.get(key)) is int and options[key] == value for key, value in OPTIONS.items()) and
                options.get('protection_key') == 'NSFileProtectionKey' and
                options.get('complete_value') == 'NSFileProtectionComplete', 'production-writing-constants')
        fm = observed.get('kotlin_fm', {})
        equal = fm.get('value') == 'complete'
        require(fm.get('original_equality') is equal, 'original-equality')
        expected = 'PASS' if equal and fm.get('dictionary_present') is True and fm.get('key_present') is True and fm.get('error_present') is False else 'FAIL'
        require(fm.get('comparison') == expected and type(fm.get('dictionary_present')) is bool and
                type(fm.get('key_present')) is bool and type(fm.get('error_present')) is bool and
                type(fm.get('error_code')) is int, 'original-strict-comparison')
        require(fm['error_present'] or fm['error_code'] == 0, 'error-code-without-error')
        require(expected != 'PASS' or fm['dictionary_present'] and fm['key_present'], 'contradictory-comparison')
        comparisons.append(dict(id=expected_id, target=target, comparison=expected, value=fm['value']))
        identities.append(native)
    require(identities[0] == identities[1] and identities[2] == identities[3] and identities[4] == identities[5], 'backup-same-inode')
    return dict(schema_version=1, collection_status=COLLECTED, strict_status='PASS' if all(row['comparison'] == 'PASS' for row in comparisons) else 'FAIL',
        strict_comparisons=comparisons, strict_passed=sum(row['comparison'] == 'PASS' for row in comparisons), strict_total=6,
        first_roundtrip=True, overwrite_roundtrip=True,
        overwrite_inode_replaced=(identities[3]['device'], identities[3]['inode']) != (identities[4]['device'], identities[4]['inode']),
        original_l08_comparisons_reclassified=False, application_runtime_qualification=False, hardware_protection_verified=False)


def validate_host(host, final, samples):
    metadata_only(host)
    require(host.get('kind') == 'protection-application-host-reader' and host.get('collection_status') == COLLECTED and
        all(host.get(key) == final[key] for key in ('controls_sha256', 'source_manifest_sha256', 'run_token', 'simulator_udid')) and
        host.get('read_only') is True and host.get('same_ios_process') is False and
        host.get('hardware_protection_verified') is False and host.get('l08_requirements_waived') is False and
        host.get('reader_image') == host.get('reader_image_after') and len(host.get('samples', [])) == 2,
        'host-context')
    require(type(host.get('schema_version')) is int and host['schema_version'] == 1 and
            type(host.get('process_id')) is int and 0 < host['process_id'] < 2**31 and
            type(host.get('uid')) is int and host['uid'] == final['uid'] and
            isinstance(host.get('runtime_version'), list) and len(host['runtime_version']) == 3 and
            all(type(value) is int and 0 <= value < 1000 for value in host['runtime_version']), 'host-process-runtime')
    image(host.get('reader_image'), 1)
    rows = []
    for index, target in enumerate(('directory', 'file')):
        observed = host['samples'][index]
        require(observed.get('target') == target, 'host-target')
        native = validate_native(observed.get('native'), target, platform=1)
        require(native['uid'] == host['uid'], 'host-file-uid')
        app = samples[1 if index == 0 else 5]['native']
        keys = ('device', 'inode', 'uid', 'mode', 'links', 'type') + (('size',) if index == 1 else ())
        # Directory size legitimately changes when the first file is created;
        # inode identity does not. Each individual sample still pins full stat.
        require(all(native[key] == app['identity'][key] for key in keys), 'host-app-same-inode')
        rows.append(dict(target=target, same_inode=True,
            app_fm=app['fm']['protection'], host_fm=observed['native']['fm']['protection'],
            app_url=app['url']['protection'], host_url=observed['native']['url']['protection'],
            app_fcntl=app['fcntl'], host_fcntl=observed['native']['fcntl'],
            app_attrlist=app['attrlist'], host_attrlist=observed['native']['attrlist']))
    return dict(schema_version=1, collection_status=COLLECTED, comparisons=rows,
        same_ios_process=False, runtime_implementation_causality_proven=False,
        l08_requirements_waived=False, hardware_protection_verified=False)
