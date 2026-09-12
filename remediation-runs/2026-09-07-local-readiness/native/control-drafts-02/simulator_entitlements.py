"""Read-only, bounded simulator provenance. Never sign or open a user keychain.

Simulator linker entitlements are independent of codesign display output.
Only explicitly owned app images/generated app xcent and installed SDK metadata
are read. Receipts expose a small public entitlement allowlist, never raw blobs.
"""
import hashlib
import json
from pathlib import Path
import plistlib
import re
import struct

from artifact_inventory import digest, safe_relative

MAX_PLIST_BYTES = 65536
MAX_COMMAND_BYTES = 4 * 1024 * 1024
APP_ID = 'com.parlor.app.debug'
PUBLIC_ENTITLEMENTS = {'application-identifier', 'com.apple.application-identifier',
                       'com.apple.developer.team-identifier', 'get-task-allow', 'keychain-access-groups'}
SIGNING_FLAGS = ('AD_HOC_CODE_SIGNING_ALLOWED', 'CODE_SIGN_IDENTITY', 'CODE_SIGNING_ALLOWED',
                 'CODE_SIGNING_REQUIRED', 'ENTITLEMENTS_DESTINATION', 'ENTITLEMENTS_REQUIRED',
                 'CODE_SIGN_ENTITLEMENTS')
THIN = {bytes.fromhex('feedface'): ('>', False), bytes.fromhex('cefaedfe'): ('<', False),
        bytes.fromhex('feedfacf'): ('>', True), bytes.fromhex('cffaedfe'): ('<', True)}
FAT = {bytes.fromhex('cafebabe'): ('>', False), bytes.fromhex('bebafeca'): ('<', False),
       bytes.fromhex('cafebabf'): ('>', True), bytes.fromhex('bfbafeca'): ('<', True)}


def owned_path(path, owner, directory=False):
    path, owner = Path(path).absolute(), Path(owner).absolute()
    if owner.is_symlink() or owner.resolve(strict=True) != owner or not owner.is_dir():
        raise RuntimeError('Unattested owner root for simulator entitlement inspection')
    try:
        path.relative_to(owner)
    except ValueError as error:
        raise RuntimeError('Entitlement path is outside its explicit owned root') from error
    if path.is_symlink() or path.resolve(strict=True) != path or (not path.is_dir() if directory else not path.is_file()):
        raise RuntimeError('Missing/symlinked/wrong-type owned entitlement path')
    return path


def bounded_file(path, limit):
    with path.open('rb') as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise RuntimeError('Owned public metadata exceeded its bounded read')
    return raw


class UniqueDictionary(dict):
    def __setitem__(self, key, value):
        if key in self:
            raise ValueError('Duplicate plist key')
        super().__setitem__(key, value)


def public_entitlements(raw):
    """Parse bounded XML/binary plist; never include unknown keys/values/errors."""
    if len(raw) > MAX_PLIST_BYTES:
        return dict(status='OVERSIZED_NOT_PARSED', bytes=len(raw))
    try:
        value = plistlib.loads(raw, dict_type=UniqueDictionary)
        if not isinstance(value, dict):
            raise ValueError('Non-dictionary entitlements')
        public = {}
        for key in sorted(PUBLIC_ENTITLEMENTS & set(value)):
            item = value[key]
            if key == 'get-task-allow':
                if type(item) is not bool: raise ValueError('Wrong public boolean')
            else:
                values = item if key == 'keychain-access-groups' else [item]
                if (not isinstance(values, list) or len(values) > 32 or
                        not all(isinstance(v, str) and re.fullmatch(r'[A-Za-z0-9_.:*+\-]{1,256}', v) for v in values)):
                    raise ValueError('Unsafe public identifier')
            public[key] = item
        return dict(status='PARSED_PUBLIC_FIELDS_ONLY', public=public,
                    unknown_key_count=len(set(value) - PUBLIC_ENTITLEMENTS))
    except Exception:
        return dict(status='MALFORMED_REDACTED')


def read_exact(stream, offset, size, total):
    if offset < 0 or size < 0 or offset + size > total:
        raise RuntimeError('Mach-O metadata extends outside its owned image')
    stream.seek(offset)
    result = stream.read(size)
    if len(result) != size:
        raise RuntimeError('Owned native image changed during bounded read')
    return result


def thin_sections(stream, start, size, total):
    magic = read_exact(stream, start, 4, total)
    if magic not in THIN:
        raise RuntimeError('Unexpected Mach-O slice type')
    endian, wide = THIN[magic]
    header_size = 32 if wide else 28
    if size < header_size: raise RuntimeError('Truncated Mach-O header')
    header = read_exact(stream, start, header_size, total)
    cpu, subtype, _, count, command_size, _ = struct.unpack_from(endian + '6I', header, 4)
    if count > 4096 or command_size > MAX_COMMAND_BYTES or command_size > size - header_size or count > command_size // 8:
        raise RuntimeError('Unbounded/inconsistent Mach-O command table')
    commands = read_exact(stream, start + header_size, command_size, total)
    cursor, sections = 0, {}
    for _ in range(count):
        if cursor + 8 > len(commands): raise RuntimeError('Truncated Mach-O load command')
        kind, length = struct.unpack_from(endian + 'II', commands, cursor)
        if length < 8 or length % (8 if wide else 4) or cursor + length > len(commands):
            raise RuntimeError('Invalid Mach-O load command size')
        if kind in (1, 0x19):
            segment_wide = kind == 0x19
            if segment_wide != wide: raise RuntimeError('Wrong Mach-O segment width')
            base, section_size = (72, 80) if wide else (56, 68)
            if length < base: raise RuntimeError('Truncated Mach-O segment')
            section_count = struct.unpack_from(endian + 'I', commands, cursor + (64 if wide else 48))[0]
            if section_count > 1024 or base + section_count * section_size != length:
                raise RuntimeError('Unbounded/inconsistent Mach-O sections')
            segment_name = commands[cursor + 8:cursor + 24].split(b'\0', 1)[0]
            segment_file, segment_bytes = struct.unpack_from(endian + ('QQ' if wide else 'II'), commands,
                                                           cursor + (40 if wide else 32))
            if segment_file + segment_bytes > size:
                raise RuntimeError('Segment extends outside Mach-O slice')
            for index in range(section_count):
                position = cursor + base + index * section_size
                name = commands[position:position + 16].split(b'\0', 1)[0]
                section_segment = commands[position + 16:position + 32].split(b'\0', 1)[0]
                if segment_name != b'__TEXT' or name not in (b'__entitlements', b'__ents_der'):
                    continue
                if section_segment != b'__TEXT' or name in sections:
                    raise RuntimeError('Ambiguous simulator entitlement section')
                section_bytes = struct.unpack_from(endian + ('Q' if wide else 'I'), commands,
                                                   position + (40 if wide else 36))[0]
                offset = struct.unpack_from(endian + 'I', commands, position + (48 if wide else 40))[0]
                if (offset < header_size + command_size or offset < segment_file or
                        offset + section_bytes > segment_file + segment_bytes):
                    raise RuntimeError('Entitlement section extends outside actual file-backed segment')
                row = dict(present=True, bytes=section_bytes)
                if section_bytes > MAX_PLIST_BYTES:
                    row['payload'] = dict(status='OVERSIZED_NOT_READ')
                else:
                    payload = read_exact(stream, start + offset, section_bytes, total)
                    row['sha256'] = hashlib.sha256(payload).hexdigest()
                    row['payload'] = public_entitlements(payload) if name == b'__entitlements' else dict(status='DER_NOT_INTERPRETED')
                sections[name] = row
        cursor += length
    if cursor != len(commands): raise RuntimeError('Unconsumed Mach-O load-command bytes')
    return dict(cpu_type=cpu, cpu_subtype=subtype,
                sections={name.decode(): sections.get(name, dict(present=False, bytes=0))
                          for name in (b'__entitlements', b'__ents_der')})


def macho_entitlement_sections(path):
    """Call only after explicit ownership+image hash checks; bounded streaming."""
    total = path.stat().st_size
    with path.open('rb') as stream:
        magic = read_exact(stream, 0, 4, total)
        if magic in THIN:
            return [thin_sections(stream, 0, total, total)]
        if magic not in FAT: raise RuntimeError('Not a native image')
        endian, wide = FAT[magic]
        count = struct.unpack(endian + 'I', read_exact(stream, 4, 4, total))[0]
        if not 1 <= count <= 16: raise RuntimeError('Unbounded Mach-O architecture table')
        entry_size = 32 if wide else 20
        table = read_exact(stream, 8, entry_size * count, total)
        spans, rows = [], []
        for index in range(count):
            fields = struct.unpack_from(endian + ('IIQQII' if wide else 'IIIII'), table, entry_size * index)
            cpu, subtype, start, size, alignment = fields[:5]
            if (start < 8 + entry_size * count or size < 28 or start + size > total or
                    alignment > 31 or start % (1 << alignment)):
                raise RuntimeError('Invalid Mach-O slice bounds')
            if any(start < other + length and other < start + size for other, length in spans):
                raise RuntimeError('Overlapping Mach-O slices')
            row = thin_sections(stream, start, size, total)
            if row['cpu_type'] != cpu or row['cpu_subtype'] != subtype:
                raise RuntimeError('Mach-O outer/inner architecture mismatch')
            spans.append((start, size)); rows.append(row)
        return rows


def inspect_bundle_entitlements(bundle, inventory, owner):
    bundle = owned_path(bundle, owner, directory=True)
    info_path = owned_path(bundle / 'Info.plist', bundle)
    if info_path.stat().st_size > MAX_PLIST_BYTES: raise RuntimeError('Oversized owned bundle metadata')
    if (plistlib.loads(bounded_file(info_path, MAX_PLIST_BYTES)).get('CFBundleIdentifier') != APP_ID or
            inventory.get('bundle_identity', {}).get('CFBundleIdentifier') != APP_ID):
        raise RuntimeError('Entitlement inspection requires the actual owned Debug application')
    images = inventory.get('images')
    if not isinstance(images, list) or not 1 <= len(images) <= 256:
        raise RuntimeError('Missing/unbounded native image inventory')
    result, observed = [], {}
    for image in images:
        relative = image['resolved_path']
        if not safe_relative(relative): raise RuntimeError('Unsafe native entitlement image path')
        if relative in observed:
            if observed[relative] != image['sha256']: raise RuntimeError('Conflicting native image alias hashes')
            continue  # Internal aliases bind the same exact bytes.
        observed[relative] = image['sha256']
        path = owned_path(bundle / relative, bundle)
        if digest(path) != image['sha256']: raise RuntimeError('Image changed before entitlement inspection')
        slices = macho_entitlement_sections(path)
        if digest(path) != image['sha256']: raise RuntimeError('Image changed during entitlement inspection')
        result.append(dict(path=relative, sha256=image['sha256'], architectures=slices))
    return dict(schema_version=1, images=result, scope='Owned current app Mach-O sections only; presence is not signature validation or healthy Keychain proof. DER presence/hash only, not decoded.')


def inspect_generated_app_entitlements(derived, owner):
    derived = owned_path(derived, owner, directory=True)
    directory = derived / 'Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build'
    if directory.is_symlink() or directory.resolve() != directory.absolute():
        raise RuntimeError('Generated entitlement directory traverses a symlink')
    if directory.exists() and not directory.is_dir(): raise RuntimeError('Wrong generated entitlement directory type')
    rows = []
    # No recursive scan or arbitrary keychain/provisioning/signing-file read.
    for name in ('Parlor.app.xcent', 'Parlor.app-Simulated.xcent'):
        path = directory / name
        relative = str(path.relative_to(derived))
        if not path.exists() and not path.is_symlink():
            rows.append(dict(path=relative, present=False)); continue
        path = owned_path(path, derived)
        size = path.stat().st_size
        row = dict(path=relative, present=True, bytes=size)
        if size > MAX_PLIST_BYTES:
            row['payload'] = dict(status='OVERSIZED_NOT_READ')
        else:
            raw = bounded_file(path, MAX_PLIST_BYTES)
            if len(raw) != size: raise RuntimeError('Generated entitlement changed during bounded read')
            row.update(sha256=hashlib.sha256(raw).hexdigest(), payload=public_entitlements(raw))
        rows.append(row)
    return dict(schema_version=1, files=rows, scope='Exact app-target generated xcent filenames only; unrelated targets and private signing material excluded. Unknown entitlement fields redacted.')


def inspect_sdk_signing_defaults(developer, sdk, configured):
    """Public installed-toolchain metadata, not merged/effective target settings."""
    developer = Path(developer).resolve(strict=True)
    if developer.name != 'Developer' or developer.parent.name != 'Contents':
        raise RuntimeError('Unrecognized selected Xcode Developer root')
    platform = developer / 'Platforms/iPhoneSimulator.platform'
    sdk = owned_path(Path(sdk).resolve(strict=True), platform / 'Developer/SDKs', directory=True)
    if sdk.suffix != '.sdk': raise RuntimeError('Unexpected selected simulator SDK')
    sources = ((developer.parent / 'version.plist', 'xcode_version'),
               (platform / 'Info.plist', 'platform_defaults'), (sdk / 'SDKSettings.json', 'sdk_defaults'))
    rows = {}
    for path, kind in sources:
        path = owned_path(path, developer.parent)
        if path.stat().st_size > 1024 * 1024: raise RuntimeError('Oversized public SDK metadata')
        raw = bounded_file(path, 1024 * 1024)
        data = json.loads(raw) if path.suffix == '.json' else plistlib.loads(raw)
        if kind == 'xcode_version':
            values = {key: data.get(key) for key in ('CFBundleShortVersionString', 'ProductBuildVersion')}
        else:
            values = {key: data.get('DefaultProperties', {}).get(key) for key in SIGNING_FLAGS}
        if not all(value is None or isinstance(value, str) and len(value) <= 256 and '\n' not in value for value in values.values()):
            raise RuntimeError('Unexpected public SDK metadata shape')
        rows[kind] = dict(path=str(path), sha256=hashlib.sha256(raw).hexdigest(), values=values)
    if configured != dict(CODE_SIGNING_ALLOWED='NO', CODE_SIGNING_REQUIRED='NO', CODE_SIGN_IDENTITY='', DEVELOPMENT_TEAM=''):
        raise RuntimeError('Unexpected explicit signing invocation fields')
    rows['explicit_environment_and_command_overrides'] = configured
    return dict(schema_version=1, observations=rows,
                scope='Installed Xcode/platform/SDK raw defaults and explicit matching runner overrides. Not queried merged effective target settings; do not infer signed entitlement state or healthy storage from flags.')
