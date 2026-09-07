"""Bounded diagnostic failure metadata, never application/storage success."""
import json
import os
from pathlib import Path
import re
import stat

UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')
STAGES = {'observation-decode', 'image-observation', 'loader-environment',
          'receipt-encode', 'receipt-destination', 'receipt-write'}
# No NSError description/userInfo, unowned path, or raw observations may enter.
DOMAINS = {0: 'redacted', 1: 'NSCocoaErrorDomain', 2: 'NSPOSIXErrorDomain',
           3: 'NativeReadinessOwnedImage', 4: 'DSC01SyntheticHarness'}
KEYS = {'schema_version', 'kind', 'boot_ordinal', 'process_boot', 'run_token',
        'signing_mode', 'stage', 'error_domain_id', 'error_code', 'error_code_redacted'}


def require(value, message):
    if not value:
        raise RuntimeError(message)


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate diagnostic failure field')
        result[key] = value
    return result


def validate_failure(value, ordinal, context, mode):
    require(isinstance(value, dict) and set(value) == KEYS, 'Unexpected failure schema')
    require(type(value['schema_version']) is int and value['schema_version'] == 1 and
            value['kind'] == 'diagnostic_failure', 'Not a diagnostic failure')
    require(type(ordinal) is int and 1 <= ordinal <= 8 and
            type(value['boot_ordinal']) is int and value['boot_ordinal'] == ordinal and
            mode in {'disabled', 'adhoc'} and value['signing_mode'] == mode, 'Wrong failure context')
    require(isinstance(context, dict) and context.get('scenario') == 'readiness' and
            type(context.get('schemaVersion')) is int and context['schemaVersion'] == 6 and
            isinstance(context.get('observations'), list) and 1 <= len(context['observations']) <= 256,
            'Missing bounded readiness context')
    for key in ('run_token', 'process_boot'):
        require(isinstance(value[key], str) and UUID.fullmatch(value[key]), 'Unsafe failure UUID')
    require(value['run_token'] == context.get('runToken') and any(
        isinstance(row, dict) and row.get('phase') == 'before_main' and
        row.get('boot') == value['process_boot'] for row in context['observations']),
        'Failure is not bound to an observed owned launch')
    require(isinstance(value['stage'], str) and value['stage'] in STAGES, 'Unknown failure stage')
    domain, code, redacted = (value[key] for key in ('error_domain_id', 'error_code', 'error_code_redacted'))
    require(type(domain) is int and domain in DOMAINS and type(code) is int and
            -(2**31) <= code < 2**31 and type(redacted) is bool, 'Unsafe error metadata')
    require((not redacted and domain != 0) or (redacted and code == 0), 'Invalid error redaction')
    return value


def read_failure(path, ordinal, context, mode):
    path = Path(path)
    require(path.name == f'parlor-native-readiness-failure-{ordinal}.json' and
            not path.is_symlink() and path.resolve().parent == path.parent.resolve(), 'Unexpected failure path')
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= 1024, 'Unbounded failure file')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as stream:
        opened = os.fstat(stream.fileno())
        require((opened.st_dev, opened.st_ino, opened.st_uid) ==
                (before.st_dev, before.st_ino, before.st_uid), 'Failure file changed before reading')
        data = stream.read(1025)
        after = os.fstat(stream.fileno())
    require(0 < len(data) <= 1024 and opened.st_size == after.st_size == len(data) and
            opened.st_mtime_ns == after.st_mtime_ns, 'Failure file changed while reading')
    value = json.loads(data, object_pairs_hook=unique_pairs)
    return validate_failure(value, ordinal, context, mode)
