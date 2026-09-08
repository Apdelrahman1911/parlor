"""Closed, credential-free simulator modes and actual copied-phase attestation.

Never invoke signing, enumerate identities, read profiles or open a keychain.
Only the runner may select the explicit ad-hoc comparison. This helper fails
before copied Gradle execution if Xcode supplies an unintended signing context.
"""
import json
import os
from pathlib import Path
import re
import sys

MODES = ('disabled', 'adhoc')
SDK = re.compile(r'iphonesimulator[0-9]+(?:\.[0-9]+)*')
OWNED_NAME = re.compile(r'parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}')
RECEIPT_NAME = 'effective-simulator-app-phase.json'
EMPTY_FIELDS = ('DEVELOPMENT_TEAM', 'PROVISIONING_PROFILE', 'PROVISIONING_PROFILE_SPECIFIER',
                'EXPANDED_PROVISIONING_PROFILE', 'CODE_SIGN_ENTITLEMENTS', 'OTHER_CODE_SIGN_FLAGS',
                'CODE_SIGN_KEYCHAIN')
CONTEXT = dict(ACTION='build', ARCHS='arm64', CONFIGURATION='Debug', PLATFORM_NAME='iphonesimulator',
               PRODUCT_BUNDLE_IDENTIFIER='com.parlor.app.debug', PRODUCT_NAME='Parlor', TARGET_NAME='iosApp',
               FRAMEWORKS_FOLDER_PATH='Parlor.app/Frameworks', DEPLOYMENT_LOCATION='NO')
OBSERVED_FLAGS = ('AD_HOC_CODE_SIGNING_ALLOWED', 'CODE_SIGN_INJECT_BASE_ENTITLEMENTS',
                  'ENTITLEMENTS_REQUIRED', 'ENABLE_DEBUG_DYLIB')
FIELDS = tuple(CONTEXT) + ('SDK_NAME', 'SRCROOT', 'PROJECT_DIR', 'TARGET_BUILD_DIR',
    'CODE_SIGNING_ALLOWED', 'CODE_SIGNING_REQUIRED', 'CODE_SIGN_IDENTITY', 'CODE_SIGN_STYLE',
    'EXPANDED_CODE_SIGN_IDENTITY', 'ENTITLEMENTS_DESTINATION') + EMPTY_FIELDS + OBSERVED_FLAGS
SCOPE = ('Actual copied app-target shell-phase environment before Gradle; only allowlisted public '
         'settings and canonical task-owned paths. Not signature validation, healthy storage, '
         'physical-device, Store-signing or publication evidence.')


def signing_overrides(mode='disabled'):
    if mode not in MODES:
        raise RuntimeError('Unknown explicit simulator signing mode')
    if mode == 'disabled':
        # Preserve the original disabled invocation, including nullable KGP
        # expanded identity. An empty expanded identity is NOT absence.
        return dict(CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO', CODE_SIGN_IDENTITY='', DEVELOPMENT_TEAM='')
    return dict(CODE_SIGNING_ALLOWED='YES', CODE_SIGNING_REQUIRED='YES', CODE_SIGN_IDENTITY='-',
                CODE_SIGN_STYLE='Manual', DEVELOPMENT_TEAM='', PROVISIONING_PROFILE='',
                PROVISIONING_PROFILE_SPECIFIER='', CODE_SIGN_ENTITLEMENTS='', OTHER_CODE_SIGN_FLAGS='')


def selected_mode(extra_arguments):
    if not extra_arguments:
        return 'disabled'
    if extra_arguments == ['--simulator-signing=adhoc']:
        return 'adhoc'
    raise RuntimeError('Only the explicit simulator ad-hoc comparison option is supported')


def owned_source(source):
    source = Path(source).absolute()
    if (source.name != 'copy' or not OWNED_NAME.fullmatch(source.parent.name) or
            source.is_symlink() or source.resolve(strict=True) != source or not source.is_dir()):
        raise RuntimeError('Signing context requires this canonical owned readiness source copy')
    return source


def observed_phase(mode, sdk, source, environment):
    overrides = signing_overrides(mode)
    source = owned_source(source)
    if not isinstance(sdk, str) or SDK.fullmatch(sdk) is None:
        raise RuntimeError('Signing context requires an exact simulator SDK')
    # Never include supplied unexpected values in diagnostics or receipts.
    if any(not isinstance(environment.get(key, ''), str) for key in FIELDS):
        raise RuntimeError('Malformed allowlisted signing context')
    if any(environment.get(key) != value for key, value in {**CONTEXT, 'SDK_NAME': sdk}.items()):
        raise RuntimeError('Wrong target, architecture, SDK or Debug simulator context')
    if any(environment.get(key, '') != value for key, value in overrides.items()):
        raise RuntimeError('Actual signing flags differ from the selected explicit mode')
    if any(environment.get(key, '') != '' for key in EMPTY_FIELDS):
        raise RuntimeError('Credential, profile, custom entitlement or signing override is forbidden')
    if mode == 'disabled':
        if 'EXPANDED_CODE_SIGN_IDENTITY' in environment:
            raise RuntimeError('Disabled mode requires absent expanded signing identity, not empty')
        if environment.get('CODE_SIGN_STYLE') not in (None, '', 'Automatic', 'Manual'):
            raise RuntimeError('Unexpected disabled signing style')
    elif environment.get('EXPANDED_CODE_SIGN_IDENTITY') != '-':
        raise RuntimeError('Ad-hoc simulator mode requires exact dash expanded identity')
    for key in OBSERVED_FLAGS:
        if environment.get(key) not in (None, '', 'YES', 'NO'):
            raise RuntimeError('Unexpected public SDK signing flag')
    if environment.get('ENTITLEMENTS_DESTINATION') not in (None, '', 'Signature', '__entitlements'):
        raise RuntimeError('Unexpected public SDK entitlement destination')
    paths = dict(SRCROOT=source / 'iosApp', PROJECT_DIR=source / 'iosApp',
                 TARGET_BUILD_DIR=source.parent / 'DerivedData/Build/Products/Debug-iphonesimulator')
    for key, expected in paths.items():
        raw = environment.get(key, '')
        if not raw.startswith('/') or len(raw.encode()) > 4096:
            raise RuntimeError('Missing or unbounded owned phase path')
        # /var and /private/var may identify the same task allocation. Never
        # accept a path resolving to another copy, target or user's DerivedData.
        if (not expected.is_dir() or expected.is_symlink() or expected.resolve(strict=True) != expected or
                Path(raw).resolve(strict=True) != expected):
            raise RuntimeError('App phase path differs from the explicit owned copy or product')
    settings = {key: str(paths[key]) if key in paths else environment.get(key) for key in FIELDS}
    return dict(schema_version=1, mode=mode, source_root=str(source), settings=settings, scope=SCOPE)


def checked_receipt_path(path):
    path = Path(path).absolute()
    if (path.name != RECEIPT_NAME or path.is_symlink() or path.parent.is_symlink() or
            path.parent.resolve(strict=True) != path.parent or not path.parent.is_dir()):
        raise RuntimeError('Phase receipt must use the explicit canonical owned evidence destination')
    return path


def write_phase_receipt(path, value):
    path = checked_receipt_path(path)
    encoded = (json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n').encode()
    if len(encoded) > 8192:
        raise RuntimeError('Phase receipt exceeds its bounded public metadata budget')
    # Exclusive: a stale successful phase cannot attest a different execution.
    with path.open('xb') as stream:
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())


def read_phase_receipt(path, mode, sdk, source):
    path = checked_receipt_path(path)
    if not path.is_file():
        raise RuntimeError('Actual effective app-phase receipt is missing')
    with path.open('rb') as stream:
        raw = stream.read(8193)
    if not 0 < len(raw) <= 8192:
        raise RuntimeError('Unbounded or truncated actual app-phase receipt')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise RuntimeError('Duplicate app-phase receipt field')
            result[key] = value
        return result
    try:
        value = json.loads(raw, object_pairs_hook=unique)
    except (ValueError, UnicodeError) as error:
        raise RuntimeError('Malformed app-phase receipt') from error
    if (not isinstance(value, dict) or set(value) != {'schema_version', 'mode', 'source_root', 'settings', 'scope'} or
            type(value['schema_version']) is not int or value['schema_version'] != 1 or
            not isinstance(value['settings'], dict) or set(value['settings']) != set(FIELDS)):
        raise RuntimeError('Wrong app-phase receipt schema')
    environment = {key: item for key, item in value['settings'].items() if item is not None}
    if value != observed_phase(mode, sdk, source, environment):
        raise RuntimeError('App-phase receipt differs from the exact executed task and signing mode')
    return value


def render_owned_kotlin_phase(template, temporary, destination, sdk, mode):
    source = owned_source(Path(temporary) / 'copy')
    signing_overrides(mode)
    destination = Path(destination).absolute()
    if (destination.is_symlink() or destination.resolve(strict=True) != destination or not destination.is_dir() or
            destination.name != re.fullmatch(r'parlor-audit-(ios-readiness-[0-9]{2})-[A-Za-z0-9_-]{1,64}', source.parent.name)[1]):
        raise RuntimeError('Copied phase receipt destination is not the exact owned readiness cycle')
    if not isinstance(sdk, str) or SDK.fullmatch(sdk) is None:
        raise RuntimeError('Copied phase SDK must be exact iphonesimulator version')
    replacements = {'__PARLOR_SOURCE_ROOT__': str(source), '__PARLOR_STOP_RECEIPT__': str(destination / 'embedded-gradle-stop.txt'),
                    '__PARLOR_SIGNING_MODE__': mode, '__PARLOR_SIMULATOR_SDK__': sdk,
                    '__PARLOR_SIGNING_RECEIPT__': str(destination / RECEIPT_NAME)}
    counts = {'__PARLOR_SOURCE_ROOT__': 4, '__PARLOR_STOP_RECEIPT__': 1, '__PARLOR_SIGNING_MODE__': 1,
              '__PARLOR_SIMULATOR_SDK__': 1, '__PARLOR_SIGNING_RECEIPT__': 1}
    for token, value in replacements.items():
        if template.count(token) != counts[token] or not re.fullmatch(r'[A-Za-z0-9_./ -]+', value):
            raise RuntimeError('Missing/ambiguous copied phase source anchor or unsafe literal')
        template = template.replace(token, value)
    if '__PARLOR_' in template:
        raise RuntimeError('Unresolved copied phase token')
    return template


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if len(arguments) != 4:
        raise RuntimeError('Expected exact mode, simulator SDK, owned source copy and receipt destination')
    mode, sdk, source, destination = arguments
    value = observed_phase(mode, sdk, Path(source), os.environ)
    write_phase_receipt(destination, value)
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, ValueError):
        # Do not include supplied identity/profile/path values in error output.
        print('Copied simulator signing-context attestation failed; no Gradle build authorized.', file=sys.stderr)
        raise SystemExit(65)
