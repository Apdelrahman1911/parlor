"""Inventory every Mach-O in this owned app bundle, not just its launch stub."""
import hashlib
import json
from pathlib import Path, PurePosixPath
from simulator_signing import signing_overrides
import plistlib
import re

MAGICS = {bytes.fromhex(value) for value in (
    'feedface', 'cefaedfe', 'feedfacf', 'cffaedfe',
    'cafebabe', 'bebafeca', 'cafebabf', 'bfbafeca',
)}


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def safe_relative(value):
    return (isinstance(value, str) and 0 < len(value.encode()) <= 512 and
            not any(ord(character) < 32 or ord(character) == 127 or character == '\\' for character in value) and
            not PurePosixPath(value).is_absolute() and
            str(PurePosixPath(value)) == value and
            all(part not in ('.', '..') for part in PurePosixPath(value).parts))


def inventory_bundle(bundle):
    supplied = Path(bundle)
    if supplied.is_symlink():
        raise RuntimeError('Application bundle must not be a symlink')
    root = supplied.resolve(strict=True)
    if not root.is_dir() or root.suffix != '.app':
        raise RuntimeError('Not an owned application bundle')
    info_path = root / 'Info.plist'
    if (info_path.is_symlink() or not info_path.is_file() or info_path.stat().st_size > 1024 * 1024):
        raise RuntimeError('Missing, symlinked or oversized bundle metadata')
    info = plistlib.loads(info_path.read_bytes())
    if info.get('CFBundleIdentifier') != 'com.parlor.app.debug':
        raise RuntimeError('Debug identity differs from source-bound expectation')
    executable = info.get('CFBundleExecutable')
    if not safe_relative(executable) or '/' in executable:
        raise RuntimeError('Invalid bundle executable')
    rows, links = [], []
    for index, path in enumerate(sorted(root.rglob('*'))):
        if index >= 50000:
            raise RuntimeError('Bundle inventory file-count limit exceeded')
        resolved = path.resolve(strict=True)
        try:
            resolved.relative_to(root)
        except ValueError as error:
            raise RuntimeError('Bundle link points outside owned app') from error
        if path.is_symlink():
            links.append(dict(path=str(path.relative_to(root)), resolved_path=str(resolved.relative_to(root)),
                              directory=resolved.is_dir()))
        if not resolved.is_file():
            continue
        if len(rows) >= 256:
            raise RuntimeError('Native bundle image bound exceeded')
        with resolved.open('rb') as stream:
            magic = stream.read(4)
        if magic not in MAGICS:
            continue
        relative = str(path.relative_to(root))
        if not safe_relative(relative):
            raise RuntimeError('Unsafe bundle-relative image name')
        rows.append(dict(path=relative, resolved_path=str(resolved.relative_to(root)),
                         bytes=resolved.stat().st_size, sha256=digest(resolved),
                         symbolic_link=path.is_symlink(), magic=magic.hex()))
    names = {row['path'] for row in rows}
    required = {executable, executable + '.debug.dylib', 'Frameworks/ComposeApp.framework/ComposeApp'}
    if not required <= names:
        raise RuntimeError('Current Debug stub, debug dylib and KMP framework must all be inventoried')
    return dict(schema_version=1, bundle_identity={key: info.get(key) for key in (
        'CFBundleIdentifier', 'CFBundleExecutable', 'CFBundleVersion', 'CFBundleShortVersionString', 'MinimumOSVersion')},
        method='Every owned bundle file inspected for thin/fat Mach-O magic; streaming SHA256; internal links explicit',
        images=rows, required_images=sorted(required), symbolic_links=links)


FRAMEWORK_PATH = 'Frameworks/ComposeApp.framework/ComposeApp'
LOADER_KEYS = {'DYLD_FRAMEWORK_PATH', 'DYLD_LIBRARY_PATH', 'DYLD_FALLBACK_FRAMEWORK_PATH', 'DYLD_FALLBACK_LIBRARY_PATH'}
UUID = re.compile(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}')
SHA256 = re.compile(r'[a-f0-9]{64}')


def render_owned_native_launch(template, temporary, mode='disabled'):
    signing_overrides(mode)
    root = Path(temporary)
    if (not root.is_absolute() or str(root.resolve()) != str(root) or not str(root).isascii() or
            not re.fullmatch(r'parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}', root.name)):
        raise RuntimeError('Native origin observer requires the exact owned canonical readiness allocation')
    for token, value in (('__PARLOR_NATIVE_TASK_ROOT__', root),
                         ('__PARLOR_NATIVE_PRODUCT_ROOT__', root / 'copy/composeApp/build'),
                         ('__PARLOR_NATIVE_SIGNING_MODE__', mode)):
        if template.count(token) != 1:
            raise RuntimeError('Missing/ambiguous exact native origin source anchor')
        template = template.replace(token, json.dumps(str(value)))
    return template


def owned_framework_relative(path, sdk=None):
    if not safe_relative(path): return False
    parts = PurePosixPath(path).parts
    if len(parts) != 5 or parts[-2].lower() != 'composeapp.framework' or parts[-1] != 'ComposeApp': return False
    if parts[:3] == ('bin', 'iosSimulatorArm64', 'debugFramework'): return True
    return (parts[:2] == ('xcode-frameworks', 'Debug') and
            re.fullmatch(r'iphonesimulator[0-9]+(?:\.[0-9]+)*', parts[2]) is not None and
            (sdk is None or parts[2] == sdk))


def validate_framework_observation(value):
    if (not isinstance(value, dict) or set(value) != {'origin', 'path', 'image_uuid', 'file_bytes', 'file_sha256'} or
            value['origin'] not in ('installed-app-bundle', 'owned-copy-build') or
            not isinstance(value['image_uuid'], str) or UUID.fullmatch(value['image_uuid']) is None or
            not isinstance(value['file_sha256'], str) or SHA256.fullmatch(value['file_sha256']) is None or
            type(value['file_bytes']) is not int or not 1 <= value['file_bytes'] <= 512 * 1024 * 1024 or
            (value['path'] != FRAMEWORK_PATH if value['origin'] == 'installed-app-bundle' else
             not owned_framework_relative(value['path']))):
        raise RuntimeError('Missing, unsafe or unbounded actual framework origin/fingerprint/UUID')
    return value


def validate_loader_environment(value):
    if not isinstance(value, dict) or set(value) != LOADER_KEYS:
        raise RuntimeError('Missing exact bounded loader-environment observation')
    for row in value.values():
        if (not isinstance(row, dict) or set(row) != {'present', 'task_owned_paths', 'redacted_entry_count'} or
                type(row['present']) is not bool or not isinstance(row['task_owned_paths'], list) or
                not all(safe_relative(path) for path in row['task_owned_paths']) or
                type(row['redacted_entry_count']) is not int or row['redacted_entry_count'] < 0 or
                len(row['task_owned_paths']) + row['redacted_entry_count'] > 32 or
                (row['present'] != (len(row['task_owned_paths']) + row['redacted_entry_count'] > 0))):
            raise RuntimeError('Unsafe loader-environment metadata or unrelated path disclosure')
    return value


def inventory_owned_framework(owned_build_root, value, sdk):
    validate_framework_observation(value)
    root = Path(owned_build_root).absolute()
    if (value['origin'] != 'owned-copy-build' or not owned_framework_relative(value['path'], sdk) or
            root.is_symlink() or root.resolve(strict=True) != root or not root.is_dir()):
        raise RuntimeError('Framework origin does not belong to this exact copied build root/SDK')
    path = root / value['path']
    # The app records a symlink-resolved path. Refuse path changes after runtime
    # instead of following a newly substituted link into another task or data.
    if path.is_symlink() or path.resolve(strict=True) != path or not path.is_file():
        raise RuntimeError('Actual framework product changed path or escaped the owned build')
    size = path.stat().st_size
    if not 1 <= size <= 512 * 1024 * 1024:
        raise RuntimeError('Owned framework product exceeds the bounded fingerprint budget')
    with path.open('rb') as stream:
        if stream.read(4) not in MAGICS:
            raise RuntimeError('Observed owned framework product is not Mach-O')
    fingerprint = digest(path)
    if size != value['file_bytes'] or fingerprint != value['file_sha256']:
        raise RuntimeError('Actual runtime framework fingerprint differs from the owned build product')
    return dict(origin=value['origin'], path=value['path'], bytes=size, sha256=fingerprint)


def bind_loaded_images(built, installed, runs, framework_inventories):
    if built != installed:
        raise RuntimeError('Installed binary inventory differs from the exact built app bundle')
    if not isinstance(runs, list) or not 1 <= len(runs) <= 8:
        raise RuntimeError('Executed-image binding requires actual bounded boot observations')
    if not isinstance(framework_inventories, list) or not 1 <= len(framework_inventories) <= 9:
        raise RuntimeError('Missing/unbounded framework artifact inventories')
    by_path = {row['resolved_path']: row for row in built['images']}
    required = {row['resolved_path'] for row in built['images'] if row['path'] in built['required_images']}
    if FRAMEWORK_PATH not in required: raise RuntimeError('Embedded framework inventory is missing')
    required.remove(FRAMEWORK_PATH)
    inventories = {}
    for row in framework_inventories:
        if (not isinstance(row, dict) or set(row) != {'origin', 'path', 'bytes', 'sha256', 'architectures'} or
                not isinstance(row['architectures'], list) or len(row['architectures']) != 1 or
                not isinstance(row['architectures'][0], dict) or set(row['architectures'][0]) != {'architecture', 'uuid'} or
                row['architectures'][0]['architecture'] != 'arm64'):
            raise RuntimeError('Missing exact owned ARM64 framework artifact/UUID inventory')
        validate_framework_observation(dict(origin=row['origin'], path=row['path'], file_bytes=row['bytes'],
            file_sha256=row['sha256'], image_uuid=row['architectures'][0]['uuid']))
        key = (row['origin'], row['path'])
        if key in inventories: raise RuntimeError('Duplicate framework artifact provenance')
        inventories[key] = row
    embedded_key = ('installed-app-bundle', FRAMEWORK_PATH)
    embedded = inventories.get(embedded_key)
    if embedded is None or any(embedded[key] != by_path[FRAMEWORK_PATH][key] for key in ('bytes', 'sha256')):
        raise RuntimeError('Embedded framework UUID receipt is not bound to its exact built/installed bytes')
    used = {embedded_key}
    bound = []
    for run in runs:
        images = run.get('loaded_app_images')
        if (not isinstance(images, list) or not images or len(images) > 128 or
                images != sorted(set(images)) or not all(safe_relative(value) for value in images)):
            raise RuntimeError('Missing, duplicated, unsafe or unbounded actual loaded-image list')
        if not required <= set(images) or not set(images) <= set(by_path):
            raise RuntimeError('A required executed app image is missing or lacks installed/built hash binding')
        framework = validate_framework_observation(run.get('loaded_compose_framework'))
        environment = validate_loader_environment(run.get('loader_environment'))
        key = (framework['origin'], framework['path'])
        artifact = inventories.get(key)
        if (artifact is None or framework['file_sha256'] != artifact['sha256'] or framework['file_bytes'] != artifact['bytes'] or
                framework['image_uuid'] != artifact['architectures'][0]['uuid'] or
                framework['image_uuid'] != embedded['architectures'][0]['uuid'] or
                (FRAMEWORK_PATH in images) != (framework['origin'] == 'installed-app-bundle')):
            raise RuntimeError('Actual framework image lacks exact runtime/owned-product hash and UUID binding')
        used.add(key)
        bound.append(dict(boot_ordinal=run['boot_ordinal'], process_boot=run['process_boot'],
            loaded_images=[{key: by_path[path][key] for key in ('path', 'bytes', 'sha256')} for path in images],
            loaded_compose_framework=dict(**framework, same_bytes_as_embedded=artifact['sha256'] == embedded['sha256'],
                                          embedded_sha256=embedded['sha256']), loader_environment=environment))
    if set(inventories) != used: raise RuntimeError('Unobserved or extra framework product inventory')
    return dict(status='PASS', method='Actual bundle dyld paths plus framework memory UUID and runtime file SHA256 bound to exact owned artifact inventories',
        runs=bound, limitation='One non-atomic dyld observation per boot. An external owned build product is identified separately, never relabeled as installed. Equal UUIDs do not prove equal bytes; same_bytes_as_embedded reports that distinction. Runtime file fingerprint is not a hash of every mapped memory page or Store-signature evidence.')


def parse_dwarfdump_uuids(output, path):
    """Parse a successful, bounded tool receipt for the exact inventoried file."""
    if not isinstance(output, str) or len(output.encode()) > 65536:
        raise RuntimeError('Unbounded Mach-O UUID tool result')
    lines = output.splitlines()
    if not 1 <= len(lines) <= 32:
        raise RuntimeError('Missing/unbounded Mach-O architectures')
    rows = []
    for line in lines:
        match = re.fullmatch(r'UUID: ([A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}) '
                             r'\((arm64|arm64e|x86_64|x86_64h|armv7|armv7s|i386)\) ' + re.escape(str(path)), line)
        if match is None:
            raise RuntimeError('Unrecognized UUID result or wrong file binding')
        rows.append(dict(architecture=match[2], uuid=match[1].lower()))
    if len({row['architecture'] for row in rows}) != len(rows):
        raise RuntimeError('Duplicate Mach-O architecture result')
    return rows
