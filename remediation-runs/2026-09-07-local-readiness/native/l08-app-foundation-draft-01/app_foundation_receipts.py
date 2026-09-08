"""Bounded observation-only schema; no metadata waiver, native tool or process action."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = ROOT / 'remediation-runs/2026-09-07-local-readiness/reviews/l08-native-foundation-control-03/run_control.py'
BASE_HASH = '58e8daf875eaf8061ed017e10cc8c8065eb49292866b6741227814282a1b8e9f'
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}\Z')
HASH = re.compile(r'[0-9a-f]{64}\Z')
RESULT_NAME = 'parlor-l08-app-foundation.json'
FAILURE_NAME = 'parlor-l08-app-foundation-failure.json'
PREFIX = 'PARLOR_L08_APP_FOUNDATION '
REASONS = set('''app-bundle-id app-bundle-prefix app-container-id app-container-prefix app-library-path
app-parent-descriptor app-parent-identity app-parents-not-pinned application-support-open
application-support-path application-support-unavailable cleanup-directory-descriptor
cleanup-directory-identity cleanup-directory-missing cleanup-directory-remove cleanup-directory-replaced
cleanup-file-identity cleanup-file-missing cleanup-file-remove cleanup-root-descriptor cleanup-root-open
cleanup-root-remains cleanup-root-remove cleanup-descriptor-close created-directory-identity debug-app-identity
directory-canonical directory-identity directory-owner directory-path entry-identity entry-stat file-identity
file-index first-readiness-context fixture-created-identity fixture-exclusive-create image-context-unavailable native-on-main
parent-identity root-identity root-not-pinned row-limit runtime-version simulator-identity unknown-leaf
main-executable-unavailable main-executable-path main-image-count main-image-ambiguous
main-image-header main-image-missing main-image-changed main-image-address
objc-exception'''.split())


def require(condition, code):
    if not condition:
        raise RuntimeError('App Foundation receipt: ' + code)


def base_validator():
    """Exact pre-reviewed CLI row validators only; its guarded main never executes."""
    require(BASE.is_file() and not BASE.is_symlink() and BASE.resolve() == BASE, 'validator-path')
    data = BASE.read_bytes()
    require(len(data) <= 65536 and hashlib.sha256(data).hexdigest() == BASE_HASH, 'validator-source')
    spec = importlib.util.spec_from_file_location('l08_control03_row_schema', BASE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _uuid(value):
    return isinstance(value, str) and UUID.fullmatch(value) is not None


def _integer(value, lower, upper):
    return type(value) is int and lower <= value <= upper


def _image(value, complete, code=False):
    require(isinstance(value, dict), 'image-shape')
    if value == {'status': 'unavailable'}:
        require(not complete, 'missing-image')
        return
    require(set(value) == ({'status', 'uuid', 'platform', 'app_relative'} if code else {'status', 'uuid', 'platform'}) and
            value['status'] == 'observed' and _uuid(value['uuid']) and
            _integer(value['platform'], 0, 255), 'image-fields')
    if code:
        require(value['app_relative'] in {'Parlor', 'Parlor.debug.dylib'}, 'foreign-code-image')
    if complete:
        require(value['platform'] == 7, 'non-simulator-image')


def _rows(value, complete, validator):
    require(isinstance(value, list) and len(value) <= len(validator.ROW_KINDS) and
            (not complete or len(value) == len(validator.ROW_KINDS)), 'row-count')
    for row, (identifier, kind) in zip(value, validator.ROW_KINDS):
        require(isinstance(row, dict) and row.get('id') == identifier, 'row-order')
        if kind == 'operation':
            require(set(row) == {'id', 'kind', 'returned', 'result', 'exception', 'native_error'} and
                    row['kind'] == 'operation' and type(row['returned']) is bool and type(row['result']) is bool,
                    'operation-fields')
            validator.validate_error(row['native_error'])
            require(row['exception'] == ('none' if row['returned'] else 'objc-exception') and
                    (row['returned'] or (row['result'] is False and not row['native_error']['present'])),
                    'operation-exception')
        else:
            require(set(row) == ({'id', 'kind', 'fm', 'volume', 'backup', 'url'} if kind == 'file' else
                                {'id', 'kind', 'fm', 'volume', 'backup'}) and row['kind'] == 'observation', 'query-fields')
            validator.validate_query(row['fm'])
            validator.validate_query(row['volume'], volume=True)
            validator.validate_query(row['backup'], backup=True)
            if kind == 'file':
                validator.validate_query(row['url'])


def parse_record(raw):
    validator = base_validator()
    value = validator.decode_json(raw, 32768)
    keys = {'schema_version', 'kind', 'run_token', 'process_boot', 'process_id', 'simulator_udid',
            'scenario', 'boot_ordinal', 'fixture_template_sha256', 'context_sha256', 'controls_sha256',
            'signing_mode', 'simulator_only', 'app_container', 'hardware_protection_verified',
            'l08_requirements_waived', 'container', 'main_image', 'fixture_image', 'foundation_image',
            'runtime_version', 'rows', 'status', 'fixture_cleanup'}
    require(isinstance(value, dict) and value.get('status') in {'OBSERVATIONS_COMPLETE', 'CONTROL_ERROR'}, 'status')
    complete = value['status'] == 'OBSERVATIONS_COMPLETE'
    require(set(value) == (keys if complete else keys | {'reason'}), 'native-fields')
    require(_integer(value['schema_version'], 1, 1) and value['kind'] == 'l08-app-foundation' and
            value['scenario'] == 'readiness' and _integer(value['boot_ordinal'], 1, 1), 'native-kind-context')
    require(all(_uuid(value[key]) for key in ('run_token', 'process_boot', 'simulator_udid')) and
            _integer(value['process_id'], 2, 2**31 - 1), 'process-context')
    require(all(isinstance(value[key], str) and HASH.fullmatch(value[key]) for key in (
        'fixture_template_sha256', 'context_sha256', 'controls_sha256')), 'source-control-context')
    require(value['signing_mode'] in {'disabled', 'adhoc'} and value['simulator_only'] is True and
            type(value['app_container']) is bool and (not complete or value['app_container'] is True) and
            value['hardware_protection_verified'] is False and value['l08_requirements_waived'] is False, 'claim-boundary')
    if not complete:
        require(isinstance(value['reason'], str) and value['reason'] in REASONS, 'unknown-native-reason')
    runtime = value['runtime_version']
    require(isinstance(runtime, list) and len(runtime) in (0, 3) and
            all(_integer(part, 0, 1000) for part in runtime) and (not complete or runtime == [26, 5, 0]), 'runtime')
    container = value['container']
    if value['app_container']:
        require(isinstance(container, dict) and set(container) == {'status', 'id', 'device', 'inode', 'uid'} and
                container['status'] == 'observed' and _uuid(container['id']) and
                _integer(container['device'], 0, 2**63 - 1) and _integer(container['inode'], 1, 2**64 - 1) and
                _integer(container['uid'], 0, 2**32 - 1), 'container-fields')
    else:
        require(container == {'status': 'unavailable'}, 'unobserved-container')
    for field in ('main_image', 'fixture_image', 'foundation_image'):
        _image(value[field], complete, code=field == 'fixture_image')
    _rows(value['rows'], complete, validator)
    cleanup = value['fixture_cleanup']
    require(isinstance(cleanup, dict) and set(cleanup) == {
        'status', 'created', 'root_pinned', 'directory_pinned', 'root_removed', 'reason'} and
        all(type(cleanup[key]) is bool for key in ('created', 'root_pinned', 'directory_pinned', 'root_removed')),
        'cleanup-shape')
    require(cleanup['status'] in {'REMOVED', 'NOT_CREATED', 'FAILED'} and
            (not cleanup['root_pinned'] or cleanup['created']) and
            (not cleanup['directory_pinned'] or cleanup['root_pinned']) and
            (not cleanup['root_removed'] or cleanup['root_pinned']), 'cleanup-identity')
    if cleanup['status'] == 'FAILED':
        require(isinstance(cleanup['reason'], str) and cleanup['reason'] in REASONS, 'cleanup-reason')
    else:
        require(cleanup['reason'] == 'none' and
                ((cleanup['created'] and cleanup['root_pinned'] and cleanup['root_removed']) if cleanup['status'] == 'REMOVED'
                 else not any(cleanup[key] for key in ('created', 'root_pinned', 'directory_pinned', 'root_removed'))),
                'cleanup-result')
    return value


def parse_delivery_failure(raw):
    value = base_validator().decode_json(raw, 1024)
    require(isinstance(value, dict) and set(value) == {'schema_version', 'kind', 'stage', 'run_token',
        'process_boot', 'simulator_udid', 'process_id', 'boot_ordinal', 'scenario',
        'l08_requirements_waived', 'hardware_protection_verified'}, 'delivery-failure-fields')
    require(_integer(value['schema_version'], 1, 1) and value['kind'] == 'l08-app-foundation-delivery-failure' and
            value['stage'] in {'native-delivery', 'receipt-write'} and
            all(_uuid(value[key]) for key in ('run_token', 'process_boot', 'simulator_udid')) and
            _integer(value['process_id'], 2, 2**31 - 1) and _integer(value['boot_ordinal'], 1, 1) and
            value['scenario'] == 'readiness' and value['l08_requirements_waived'] is False and
            value['hardware_protection_verified'] is False, 'delivery-failure-context')
    return value


def read_owned_receipt(path, parent, maximum):
    """Only exact receipt names under a caller-attested owned container/tmp or evidence directory."""
    path, parent = Path(path).absolute(), Path(parent).absolute()
    require(path.name in {RESULT_NAME, FAILURE_NAME} and path.parent == parent and
            not parent.is_symlink() and parent.resolve() == parent, 'receipt-parent')
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and before.st_uid == os.getuid() and before.st_nlink == 1 and
            0 < before.st_size <= maximum, 'receipt-file')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        opened = os.fstat(fd)
        require((opened.st_dev, opened.st_ino, opened.st_size) == (before.st_dev, before.st_ino, before.st_size), 'receipt-replaced')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            raw = stream.read(maximum + 1)
        after = os.fstat(fd)
        require(0 < len(raw) <= maximum and after.st_size == opened.st_size == len(raw) and
                after.st_mtime_ns == opened.st_mtime_ns, 'receipt-mutated')
    finally:
        os.close(fd)
    return raw


def preserve_available(container, evidence, expected_device, expected_token):
    """Root calls immediately after exact get_app_container, even on later test failure.

    No failed/missing record becomes successful. Source/image/XCTest validation is separate.
    No fixture or source removal occurs here; outer owned-device finalization stays mandatory.
    """
    container, evidence = Path(container).absolute(), Path(evidence).absolute()
    require(_uuid(expected_device) and _uuid(expected_token), 'preservation-context')
    prefix = Path.home() / 'Library/Developer/CoreSimulator/Devices' / expected_device / 'data/Containers/Data/Application'
    require(container.parent == prefix and _uuid(container.name) and container.is_dir() and
            container.resolve() == container and evidence.is_dir() and evidence.resolve() == evidence, 'owned-container-evidence')
    preserved = []
    for name, maximum, parse in ((RESULT_NAME, 32768, parse_record), (FAILURE_NAME, 1024, parse_delivery_failure)):
        path = container / 'tmp' / name
        if not path.exists() and not path.is_symlink():
            continue
        raw = read_owned_receipt(path, path.parent, maximum)
        value = parse(raw)
        require(value['run_token'] == expected_token and value['simulator_udid'] == expected_device, 'preserved-cross-task-record')
        if value.get('app_container'):
            observed = value['container']
            identity = container.lstat()
            require(observed == dict(status='observed', id=container.name, device=identity.st_dev,
                    inode=identity.st_ino, uid=identity.st_uid), 'container-inode-binding')
            if value['fixture_cleanup']['root_removed']:
                child = container / 'Library/Application Support' / (
                    'ParlorFoundationControl-' + value['run_token'].lower() + '-' + value['process_boot'].lower())
                require(not child.exists() and not child.is_symlink(), 'synthetic-root-still-present')
        with (evidence / name).open('xb') as output:
            output.write(raw)
        preserved.append(name)
    return preserved


def bind_collection(value, binding, built, installed, uuid_inventory, xctest_log, expected_device, expected_token,
                    first_health=None):
    """Complete observation receipt, never an app/storage/release PASS. Inputs already preserved."""
    value = parse_record(json.dumps(value, separators=(',', ':')).encode())
    require(value['status'] == 'OBSERVATIONS_COMPLETE' and value['fixture_cleanup']['status'] == 'REMOVED', 'incomplete-native-control')
    require(value['run_token'] == expected_token and value['simulator_udid'] == expected_device and
            all(value[key] == binding[key] for key in ('fixture_template_sha256', 'context_sha256', 'controls_sha256')) and
            value['signing_mode'] == binding['mode'], 'compiled-context-binding')
    require(isinstance(built, dict) and built == installed and isinstance(built.get('images'), list) and
            1 <= len(built['images']) <= 256 and isinstance(uuid_inventory, list) and 1 <= len(uuid_inventory) <= 256,
            'built-installed-inventory')
    require(isinstance(built.get('bundle_identity'), dict) and
            built['bundle_identity'].get('CFBundleIdentifier') == 'com.parlor.app.debug' and
            built['bundle_identity'].get('CFBundleExecutable') == 'Parlor', 'built-debug-identity')
    require(all(isinstance(row, dict) and isinstance(row.get('resolved_path'), str) and
                isinstance(row.get('sha256'), str) and HASH.fullmatch(row['sha256']) for row in built['images']) and
            all(isinstance(row, dict) and set(row) == {'path', 'sha256', 'architectures'} and
                isinstance(row['path'], str) and isinstance(row['sha256'], str) and HASH.fullmatch(row['sha256'])
                for row in uuid_inventory), 'artifact-inventory-fields')
    images = {row['resolved_path']: row for row in built['images']}
    require(all(row['sha256'] == images[row['resolved_path']]['sha256'] for row in built['images']), 'conflicting-native-alias')
    require(len({row['path'] for row in uuid_inventory}) == len(uuid_inventory), 'duplicate-uuid-inventory')
    uuids = {row['path']: row for row in uuid_inventory}
    for name, relative in (('main_image', 'Parlor'), ('fixture_image', value['fixture_image']['app_relative'])):
        require(relative in images and relative in uuids, 'missing-native-image-binding')
        require(uuids[relative]['sha256'] == images[relative]['sha256'] and
                uuids[relative]['architectures'] == [dict(architecture='arm64', uuid=value[name]['uuid'].lower())],
                'native-image-uuid-or-hash')
    if first_health is not None:
        require(first_health['boot_ordinal'] == 1 and all(first_health[key] == value[key] for key in (
            'run_token', 'process_boot', 'process_id', 'signing_mode')), 'first-health-process-mismatch')
    require(isinstance(xctest_log, str) and len(xctest_log.encode()) <= 64 * 1024 * 1024, 'xctest-log-bound')
    executed = [base_validator().decode_json(line[len(PREFIX):].encode(), 1024)
                for line in xctest_log.splitlines() if line.startswith(PREFIX)]
    require(len(executed) == 1 and executed[0] == dict(schema_version=1, kind='l08-app-foundation-xctest',
        run_token=value['run_token'], process_boot=value['process_boot'], process_id=value['process_id'],
        boot_ordinal=1, activations=1, foreground=True, alert_present=False,
        fixture_template_sha256=value['fixture_template_sha256'], context_sha256=value['context_sha256'],
        controls_sha256=value['controls_sha256']), 'missing-or-mismatched-executed-xctest')
    require(all(type(executed[0][key]) is int for key in ('schema_version', 'process_id', 'boot_ordinal', 'activations')) and
            executed[0]['foreground'] is True and executed[0]['alert_present'] is False, 'xctest-primitive-types')
    return dict(status='COLLECTION_VALIDATED_NOT_L08_PASS', operations=8, checkpoints=8,
        app_container=True, foundation_uuid=value['foundation_image']['uuid'],
        first_health_process_cross_checked=first_health is not None,
        strict_l08='UNCHANGED_NOT_SATISFIED', physical_protection_verified=False,
        limits='Direct app-container Foundation observations only. Metadata failure remains visible. '
               'Main/code memory UUIDs bind to inventoried built/installed bytes, not a hash of every mapped memory page. '
               'Foundation UUID is not public-source provenance or device enforcement proof.')
