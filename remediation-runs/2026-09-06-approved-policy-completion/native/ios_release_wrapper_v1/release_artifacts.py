"""Source-bound unsigned wrapper artifact inspection; no runtime/signing claim.

Only the root runner supplies native-tool callbacks after a successful build.
Every shipped regular file is streamed into a compact hash inventory. Native
headers discover all Mach-O files, including unexpected debug/helper dylibs.
"""
import hashlib
import os
from pathlib import Path
import plistlib
import re
import stat


MACHO_MAGICS = {bytes.fromhex(value) for value in (
    'feedface', 'cefaedfe', 'feedfacf', 'cffaedfe', 'cafebabe', 'bebafeca', 'cafebabf', 'bfbafeca')}
MAX_FILES = 8192
MAX_TOTAL_BYTES = 2 * 1024 * 1024 * 1024
MAX_PLIST_BYTES = 1024 * 1024
REQUIRED_NATIVE = {'Parlor', 'Frameworks/ComposeApp.framework/ComposeApp'}


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def require_completed_build(xcode_exit, embedded_stop):
    # Never let stale existing binaries turn an unsuccessful build into success.
    if type(xcode_exit) is not int or xcode_exit != 0:
        raise RuntimeError('Release Xcode build did not succeed; artifact existence is not proof')
    if embedded_stop != 'build_exit=0\nstop_exit=0\n':
        raise RuntimeError('No successful immediate nested Gradle build/stop receipt')


def read_plist(path):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > MAX_PLIST_BYTES:
        raise RuntimeError('Missing/unsafe/oversized public plist input')
    value = plistlib.loads(path.read_bytes())
    if not isinstance(value, dict):
        raise RuntimeError('Public plist must be a dictionary')
    return value


def versions(source):
    text = (source / 'config/parlor-version.xcconfig').read_text()
    result = {}
    for name in ('PARLOR_VERSION_NAME', 'PARLOR_BUILD_NUMBER'):
        values = re.findall(r'^' + name + r'\s*=\s*(\S+)\s*$', text, re.MULTILINE)
        if len(values) != 1:
            raise RuntimeError('Version must come from one source xcconfig assignment')
        result[name] = values[0]
    return result


def version_tuple(value):
    result = tuple(int(part) for part in value.split('.'))
    return result + (0,) * (3 - len(result))


def verify_native_probe(relative, probe):
    if set(probe) != {'file', 'archs', 'build'} or any(not isinstance(v, str) or len(v.encode()) > 65536 for v in probe.values()):
        raise RuntimeError('Native inspection must include bounded file/lipo/vtool outputs')
    if 'Mach-O' not in probe['file'] or probe['archs'].split() != ['arm64']:
        raise RuntimeError('Every shipped native binary must be an arm64 Mach-O')
    platforms = re.findall(r'^\s*platform\s+(\S+)\s*$', probe['build'], re.MULTILINE)
    minimum = re.findall(r'^\s*minos\s+([0-9]+(?:\.[0-9]+){1,2})\s*$', probe['build'], re.MULTILINE)
    if (not platforms or any(platform != 'IOSSIMULATOR' for platform in platforms) or
            not minimum or len(platforms) != len(minimum) or any(version_tuple(v) > (16, 0, 0) for v in minimum)):
        raise RuntimeError('Native code is not iOS Simulator-compatible at the app deployment floor')
    if relative == 'Parlor' and 'executable' not in probe['file']:
        raise RuntimeError('Main app artifact is not executable Mach-O code')
    return dict(architectures=['arm64'], platforms=platforms, minimum_os_versions=sorted(set(minimum)),
                file_description=probe['file'].strip(), evidence_kind='native-file-lipo-vtool-metadata-only')


def inspect_release_artifacts(app, source, binary_probe, source_paths):
    app, source = Path(app), Path(source)
    if not app.is_dir() or app.is_symlink() or app.resolve() != app.absolute():
        raise RuntimeError('Expected a fresh exact owned Release app directory')
    entries, native = [], []
    total = 0
    visited = 0
    for path in app.rglob('*'):
        visited += 1
        if visited > MAX_FILES * 2:
            raise RuntimeError('Artifact directory-entry bound exceeded')
        if path.is_symlink():
            raise RuntimeError('Refuse artifact inspection through a generated symlink')
        if path.is_dir():
            if path.suffix == '.appex':
                raise RuntimeError('Unreviewed app extension is outside the wrapper contract')
            continue
        info = path.stat()
        if not stat.S_ISREG(info.st_mode) or len(entries) >= MAX_FILES:
            raise RuntimeError('Unexpected artifact type or file-count bound exceeded')
        total += info.st_size
        if total > MAX_TOTAL_BYTES:
            raise RuntimeError('Release app exceeds bounded inspection size')
        relative = str(path.relative_to(app))
        entry = dict(path=relative, bytes=info.st_size, sha256=digest(path), executable=bool(info.st_mode & 0o111))
        with path.open('rb') as stream:
            magic = stream.read(4)
        if magic in MACHO_MAGICS:
            if len(native) >= 64:
                raise RuntimeError('Native artifact-count inspection bound exceeded')
            entry['kind'] = 'mach-o'
            metadata = verify_native_probe(relative, binary_probe(path))
            native.append(dict(entry, **metadata))
        else:
            entry['kind'] = 'resource-or-metadata'
            if relative in REQUIRED_NATIVE or path.suffix in ('.dylib', '.so') or entry['executable']:
                raise RuntimeError('Executable/native-named artifact lacks a recognized Mach-O header')
        entries.append(entry)
    entries.sort(key=lambda row: row['path'])
    native.sort(key=lambda row: row['path'])
    if not REQUIRED_NATIVE <= {row['path'] for row in native}:
        raise RuntimeError('Both main executable and exact embedded ComposeApp framework are required')
    if not os.access(app / 'Parlor', os.X_OK):
        raise RuntimeError('Main app executable mode is missing')
    expected_version = versions(source)
    info = read_plist(app / 'Info.plist')
    expected = dict(CFBundleIdentifier='com.parlor.app', CFBundleExecutable='Parlor',
        CFBundleShortVersionString=expected_version['PARLOR_VERSION_NAME'], CFBundleVersion=expected_version['PARLOR_BUILD_NUMBER'],
        MinimumOSVersion='16.0', CFBundleSupportedPlatforms=['iPhoneSimulator'], DTPlatformName='iphonesimulator',
        CFBundleLocalizations=['en', 'ar'])
    if any(info.get(key) != value for key, value in expected.items()):
        raise RuntimeError('Release app plist identity/version/platform/deployment/localizations do not match source')
    source_info = read_plist(source / 'iosApp/iosApp/Info.plist')
    for key in ('NSBonjourServices', 'NSLocalNetworkUsageDescription'):
        if info.get(key) != source_info.get(key):
            raise RuntimeError('Release plist network/privacy declaration differs from source')
    source_privacy = source / 'iosApp/iosApp/PrivacyInfo.xcprivacy'
    if read_plist(app / 'PrivacyInfo.xcprivacy') != read_plist(source_privacy):
        raise RuntimeError('Shipped privacy manifest differs structurally from bound source')
    if any(Path(row['path']).name == 'embedded.mobileprovision' for row in entries):
        raise RuntimeError('Unsigned simulator wrapper unexpectedly contains a provisioning profile')
    by_name = {}
    for row in entries:
        by_name.setdefault(Path(row['path']).name, []).append(row)
    raw_sources = sorted(source / name for name in source_paths if
        (name.startswith('game-modes/whodunit/src/commonMain/composeResources/files/cases/') and name.endswith('.json')) or
        ('/src/commonMain/composeResources/' in name and Path(name).suffix in ('.ttf', '.otf')))
    # Fonts and authored cases ship as raw Compose resources. Compiled resources
    # such as Assets.car/cvr are still fully hash-inventoried, not misrepresented
    # as byte-identical source PNG/XML files.
    if not raw_sources:
        raise RuntimeError('Expected shipping raw case/font inputs are absent from source binding')
    resource_matches = []
    for path in raw_sources:
        matched = [row['path'] for row in by_name.get(path.name, []) if row['sha256'] == digest(path)]
        if not matched:
            raise RuntimeError('Bound raw case/font resource absent or changed in Release app: ' + path.name)
        resource_matches.append(dict(source_path=str(path.relative_to(source)), source_sha256=digest(path), artifact_paths=matched))
    return dict(schema_version=1, status='PASS', execution_kind='unsigned-simulator-release-artifact-inspection',
        app_identity={key: info.get(key) for key in expected}, native_binaries=native,
        files=entries, file_count=len(entries), total_bytes=total,
        source_privacy_sha256=digest(source_privacy), shipped_privacy_sha256=digest(app / 'PrivacyInfo.xcprivacy'),
        privacy_structurally_matches_source=True, raw_resource_matches=resource_matches,
        limitation='Unsigned compiler/artifact evidence only. Every Mach-O inventoried, including unexpected debug dylibs. No runtime, signature integrity, physical-device or Store qualification.')
