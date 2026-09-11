"""Host-only copied-source observation; the original image guard is unchanged."""
import hashlib
import re


SAMPLER_SHA256 = '3f4f49af81d396a07beabeed45be649314495fd286327baa931aa5eb2dcca137'
CAPTURED = 'CAPTURED_HOST_IMAGE_PREDICATES_NOT_APP_QUALIFICATION'
PREDICATES = ('command_bytes_complete', 'uuid_present', 'single_platform',
              'executable_address', 'address_not_before_header')
BINDING_KEYS = {'run_token', 'source_sha', 'control_sha256', 'run_id', 'run_attempt'}
GUARD = b'''    demand(offset == header->sizeofcmds && uuid && platforms.count == 1 && executableAddress &&
           (uintptr_t)address >= (uintptr_t)header, @"image-executable-address");'''
PREAMBLE = b'''
// Diagnostic declaration in the owned HOST copy only; no shipping change.
#if !TARGET_OS_OSX || TARGET_OS_SIMULATOR || TARGET_OS_MACCATALYST || !defined(__arm64__)
#error Host image predicate diagnostics require arm64 macOS.
#endif
extern void ParlorHostImageCapture(NSDictionary *observation);
'''
HOOK = b'''    // Observe the exact original values, then execute the unchanged guard.
    NSString *diagnosticName = [[NSString stringWithUTF8String:info.dli_fname] lastPathComponent];
    ParlorHostImageCapture(@{
        @"predicates": @{
            @"command_bytes_complete": offset == header->sizeofcmds ? @YES : @NO,
            @"uuid_present": uuid != nil ? @YES : @NO,
            @"single_platform": platforms.count == 1 ? @YES : @NO,
            @"executable_address": executableAddress ? @YES : @NO,
            @"address_not_before_header": (uintptr_t)address >= (uintptr_t)header ? @YES : @NO},
        @"platforms": [platforms copy], @"platform_count": @(platforms.count),
        @"uuid": uuid ? (id)uuid : NSNull.null,
        @"image_basename": diagnosticName ? (id)diagnosticName : NSNull.null,
        @"cputype": @(header->cputype), @"cpusubtype": @(header->cpusubtype),
        @"command_bytes_consumed": @(offset), @"command_bytes_declared": @(header->sizeofcmds),
        @"relative_offset": (uintptr_t)address >= (uintptr_t)header &&
            (uintptr_t)address - (uintptr_t)header < (1ULL << 40) ?
            @((uint64_t)((uintptr_t)address - (uintptr_t)header)) : NSNull.null});
'''


def require(condition, reason):
    if not condition:
        raise RuntimeError('host-image-' + reason)


def transform(source):
    """Return one pinned sampler copy with an additive pre-guard callback."""
    require(type(source) is bytes and hashlib.sha256(source).hexdigest() == SAMPLER_SHA256, 'sampler-pin')
    anchor = b'#include <unistd.h>\n'
    require(source.count(anchor) == source.count(GUARD) == 1, 'copy-anchor')
    copied = source.replace(anchor, anchor + PREAMBLE).replace(GUARD, HOOK + GUARD)
    require(copied.replace(PREAMBLE, b'').replace(HOOK, b'') == source and copied.count(GUARD) == 1,
            'original-guard-changed')
    return copied


def safe_string(value, *, empty=False):
    return (type(value) is str and (empty or bool(value)) and len(value.encode('utf-8')) <= 256 and
            all(32 <= ord(character) < 127 for character in value) and '/' not in value and '\\' not in value)


def validate(record, expectedbinding):
    """Validate collection only; a rejected original predicate remains rejected."""
    require(type(expectedbinding) is dict and set(expectedbinding) == BINDING_KEYS and
            all(type(value) is str for value in expectedbinding.values()), 'expected-binding-shape')
    for key, pattern in (('run_token', r'[a-f0-9]{32}'), ('source_sha', r'[a-f0-9]{40}'),
                         ('control_sha256', r'[a-f0-9]{64}'), ('run_id', r'[1-9][0-9]{0,19}'),
                         ('run_attempt', r'[1-9][0-9]{0,19}')):
        require(re.fullmatch(pattern, expectedbinding[key]) is not None, 'expected-binding-value')
    require(type(record) is dict and set(record) == {'schema', 'kind', 'collection_status', 'binding',
        'process_id', 'uid', 'runtime_version', 'production_snapshots_observed', 'protection_qualified',
        'original_guard_changed', 'results', 'failure'}, 'record-shape')
    require(type(record['schema']) is int and record['schema'] == 1 and
            record['kind'] == 'host-image-predicate-diagnostic' and record['collection_status'] == CAPTURED and
            record['binding'] == expectedbinding and type(record['binding']) is dict and
            all(type(value) is str for value in record['binding'].values()) and record['failure'] == 'none',
            'record-binding-or-status')
    require(all(record[key] is False for key in
            ('production_snapshots_observed', 'protection_qualified', 'original_guard_changed')), 'scope')
    require(type(record['process_id']) is int and 0 < record['process_id'] < 2**31 and
            type(record['uid']) is int and 0 < record['uid'] < 2**32 and
            type(record['runtime_version']) is list and len(record['runtime_version']) == 3 and
            all(type(item) is int and 0 <= item < 1000 for item in record['runtime_version']) and
            record['runtime_version'][0] == 15, 'host-context')
    require(type(record['results']) is list and len(record['results']) == 3, 'target-count')
    summaries = []
    for row, target, selector in zip(record['results'], ('main', 'filemanager', 'url'),
            ('not-applicable', 'attributesOfItemAtPath:error:', 'resourceValuesForKeys:error:')):
        require(type(row) is dict and set(row) == {'target', 'receiver_class', 'selector', 'original_result',
                'failure', 'hook_count', 'observation'} and row['target'] == target and row['selector'] == selector and
                safe_string(row['receiver_class']) and type(row['hook_count']) is int and row['hook_count'] == 1,
                'target-context')
        require(target != 'main' or row['receiver_class'] == 'not-applicable', 'main-not-receiver')
        observation = row['observation']
        require(type(observation) is dict and set(observation) == {'predicates', 'platforms', 'platform_count',
            'uuid', 'image_basename', 'cputype', 'cpusubtype', 'command_bytes_consumed', 'command_bytes_declared',
            'relative_offset'}, 'observation-shape')
        predicates = observation['predicates']
        require(type(predicates) is dict and set(predicates) == set(PREDICATES) and
                all(type(value) is bool for value in predicates.values()), 'predicate-booleans')
        platforms = observation['platforms']
        require(type(platforms) is list and len(platforms) <= 4096 and
                all(type(item) is int and 0 <= item < 2**32 for item in platforms) and
                type(observation['platform_count']) is int and observation['platform_count'] == len(platforms) and
                predicates['single_platform'] is (len(platforms) == 1), 'platform-observation')
        uuid = observation['uuid']
        require(uuid is None or type(uuid) is str and re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', uuid),
                'uuid-observation')
        require(predicates['uuid_present'] is (uuid is not None), 'uuid-predicate')
        name = observation['image_basename']
        require(name is None or safe_string(name, empty=True), 'image-basename')
        require(all(type(observation[key]) is int and -(2**31) <= observation[key] < 2**31
                    for key in ('cputype', 'cpusubtype')), 'cpu-observation')
        consumed, declared = observation['command_bytes_consumed'], observation['command_bytes_declared']
        require(type(consumed) is int and type(declared) is int and 0 <= consumed <= declared <= 1024 * 1024 and
                predicates['command_bytes_complete'] is (consumed == declared), 'command-byte-observation')
        offset = observation['relative_offset']
        require(offset is None or type(offset) is int and 0 <= offset < 2**40 and predicates['address_not_before_header'],
                'relative-offset')
        passed = all(predicates.values())
        require(row['original_result'] in ('PASS', 'REJECTED'), 'original-result')
        if row['original_result'] == 'PASS':
            require(passed and row['failure'] == 'none' and safe_string(name), 'masked-original-rejection')
        else:
            require(row['failure'] == 'image-executable-address' and not passed or
                    row['failure'] == 'image-name' and passed and not name, 'original-rejection-reason')
        if target == 'main':
            require(row['original_result'] == 'PASS' and platforms == [1] and observation['cputype'] == 0x100000c and
                    offset is not None, 'host-main-control')
        summaries.append(dict(target=target, original_result=row['original_result'], failure=row['failure'],
            failed_predicates=[key for key in PREDICATES if not predicates[key]], platforms=platforms))
    main = record['results'][0]['observation']
    return dict(schema=1, status=CAPTURED, binding=dict(expectedbinding),
        main_image={key: main[key] for key in ('uuid', 'image_basename', 'platforms', 'relative_offset')},
        results=summaries, production_snapshots_observed=False, protection_qualified=False,
        original_guard_changed=False)
