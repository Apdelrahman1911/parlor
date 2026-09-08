"""Inventory every Mach-O in this owned app bundle, not just its launch stub."""
import hashlib
from pathlib import Path, PurePosixPath
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
        raise RuntimeError('Unsigned Debug identity differs from source-bound expectation')
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


def bind_loaded_images(built, installed, runs):
    if built != installed:
        raise RuntimeError('Installed binary inventory differs from the exact built app bundle')
    by_path = {row['resolved_path']: row for row in built['images']}
    required = {row['resolved_path'] for row in built['images'] if row['path'] in built['required_images']}
    bound = []
    for run in runs:
        images = run.get('loaded_app_images')
        if (not isinstance(images, list) or not images or len(images) > 128 or
                images != sorted(set(images)) or not all(safe_relative(value) for value in images)):
            raise RuntimeError('Missing, duplicated, unsafe or unbounded actual loaded-image list')
        if not required <= set(images) or not set(images) <= set(by_path):
            raise RuntimeError('A required executed image is missing or lacks installed/built hash binding')
        bound.append(dict(boot_ordinal=run['boot_ordinal'], process_boot=run['process_boot'],
                          loaded_images=[{key: by_path[path][key] for key in ('path', 'bytes', 'sha256')} for path in images]))
    return dict(status='PASS', method='Actual app dyld paths bound to identical installed and built Mach-O inventories',
                runs=bound, limitation='Observed app-image paths at one bounded, non-atomic dyld enumeration per boot; not every past/concurrent image. Signing-disabled instrumented Debug simulator only; not Store-signature or system-library attestation')


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
