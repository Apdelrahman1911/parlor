"""Additive owned-copy hooks only. No CLI, builds, processes, device actions or deletion."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
COMPANION = CAMPAIGN / 'l08-storage-functional-companion-02'
PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
CONTENT = 'iosApp/iosApp/ContentView.swift'
TESTS = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
NATIVE = 'iosApp/iosApp/L08AppFoundation.m'
BRIDGE = 'iosApp/iosApp/L08AppFoundationBridge.h'
ADDITIONS = {NATIVE: 'L08AppFoundation.m.in', BRIDGE: 'L08AppFoundationBridge.h.in'}
MODIFIED = (PROJECT, CONTENT, TESTS)
HASH = re.compile(r'[0-9a-f]{64}\Z')
TEMP = re.compile(r'parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}\Z')
PINS = {
    'run_ios_readiness.py': 'b0f3794fe89537d5108a2c24c22c33ed94d9330ee220e7fa257bfb2c4a7b1e29',
    'copied_sources.py': 'ba1dbbd9fabc23e43060663ff6051617f1fe3119979e60df7a1db3c13fe950d6',
    'NativeReadinessUITests.swift.in': 'fa2dc7dbc97c576262ad14cd85b22f65aa908b1629ab1a205b22aa4873f5c0dc',
    'NativeReadinessLaunch.swift.in': 'df8f0cce3f45bedbfea47df3ca0b2e56c6a777383395fc170f0f07b83f8d143a',
    'DSC01Probe.swift.in': '1c4b113ec8695d8da2115e4f386fdaf8820411879af6ff9492109b62c7770746',
    'l08_functional_copy.py': '417865a86daca8c09e1d650abc2d46b20920a128d95d2021ad365e38e7a207a4',
}
TOOLCHAIN_HELPER = ROOT / 'scripts/verification/ios-readiness/toolchain_profiles.py'
if TOOLCHAIN_HELPER.is_symlink() or TOOLCHAIN_HELPER.resolve(strict=True) != TOOLCHAIN_HELPER:
    raise RuntimeError('Redirected native toolchain profile helper')
_toolchain_spec = importlib.util.spec_from_file_location('parlor_foundation_copy_toolchains', TOOLCHAIN_HELPER)
toolchains = importlib.util.module_from_spec(_toolchain_spec)
_toolchain_spec.loader.exec_module(toolchains)


def require(condition, code):
    if not condition:
        raise RuntimeError('App Foundation: ' + code)


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def context_digest(identity):
    require(isinstance(identity, dict) and isinstance(identity.get('source_manifest'), list), 'source-context-shape')
    entries = identity['source_manifest']
    require(1 <= len(entries) <= 50000 and all(isinstance(row, list) and len(row) == 2 and
            isinstance(row[0], str) and isinstance(row[1], str) and HASH.fullmatch(row[1]) for row in entries),
            'source-manifest-shape')
    require(entries == sorted(entries) and len({row[0] for row in entries}) == len(entries) and
            identity.get('source_manifest_sha256') == hashlib.sha256(
                json.dumps(entries, separators=(',', ':')).encode()).hexdigest(), 'source-manifest-identity')
    encoded = json.dumps(identity, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode()
    require(len(encoded) <= 4 * 1024 * 1024, 'source-context-bound')
    return hashlib.sha256(encoded).hexdigest()


def once(text, old, new):
    require(isinstance(text, str) and text.count(old) == 1, 'missing-or-ambiguous-copy-anchor')
    return text.replace(old, new)


def pin_dependencies():
    result = []
    for name, expected in PINS.items():
        path = COMPANION / name
        require(path.is_file() and not path.is_symlink() and path.resolve() == path and
                digest(path) == expected, 'companion02-source-drift')
        result.append(dict(path=str(path.relative_to(ROOT)), sha256=expected))
    return result


def transform_content(text, launch):
    require('L08AppFoundation' not in text and 'appFoundationDisplay' not in text, 'repeated-content-adapter')
    text = once(text, '    @Published private(set) var nativeReadinessDisplay = "pending"',
                '    @Published private(set) var nativeReadinessDisplay = "pending"\n'
                '    @Published private(set) var appFoundationDisplay = "pending"')
    text = once(text, '            if probe.isReadinessScenario {',
                '            if probe.isAppFoundationBoot { L08AppFoundationControls(probe: probe) }\n'
                '            if probe.isReadinessScenario {')
    return text + '\n' + launch


def transform_tests(text, helper):
    require('verifyL08AppFoundation' not in text, 'repeated-test-adapter')
    anchor = '            let run = app.buttons["parlor-native-readiness-run"]'
    require('func verifyActualNativeReadiness(_ app: XCUIApplication)' in text and
            'for ordinal in 1...8 {' in text and
            'app.launchEnvironment["PARLOR_READINESS_BOOT"] = String(ordinal)' in text,
            'original-readiness-loop-drift')
    return once(text, anchor,
                '            if ordinal == 1 { try verifyL08AppFoundation(app, boot: boot) }\n' + anchor) + '\n' + helper


def transform_project(text):
    require('L08AppFoundation' not in text and 'SWIFT_OBJC_BRIDGING_HEADER' not in text and
            'F08C01002C000001' not in text, 'existing-bridge-or-adapter')
    text = once(text, '/* Begin PBXBuildFile section */', '''/* Begin PBXBuildFile section */
		F08C01002C00000100000001 /* L08AppFoundation.m in Sources */ = {isa = PBXBuildFile; fileRef = F08C01002C00000100000002 /* L08AppFoundation.m */; settings = {COMPILER_FLAGS = "-Wall -Wextra -Werror"; }; };''')
    text = once(text, '/* Begin PBXFileReference section */', '''/* Begin PBXFileReference section */
		F08C01002C00000100000002 /* L08AppFoundation.m */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.objc; path = L08AppFoundation.m; sourceTree = "<group>"; };
		F08C01002C00000100000003 /* L08AppFoundationBridge.h */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.h; path = L08AppFoundationBridge.h; sourceTree = "<group>"; };''')
    text = once(text, '\t\t\t\tAB1D03C62B0E0FA8002A1234 /* ContentView.swift */,',
                '\t\t\t\tAB1D03C62B0E0FA8002A1234 /* ContentView.swift */,\n'
                '\t\t\t\tF08C01002C00000100000002 /* L08AppFoundation.m */,\n'
                '\t\t\t\tF08C01002C00000100000003 /* L08AppFoundationBridge.h */,')
    text = once(text, '\t\t\t\tAB1D03C72B0E0FA8002A1234 /* ContentView.swift in Sources */,',
                '\t\t\t\tAB1D03C72B0E0FA8002A1234 /* ContentView.swift in Sources */,\n'
                '\t\t\t\tF08C01002C00000100000001 /* L08AppFoundation.m in Sources */,')
    return once(text, '\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID).debug";',
                '\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID).debug";\n'
                '\t\t\t\tSWIFT_OBJC_BRIDGING_HEADER = "$(SRCROOT)/iosApp/L08AppFoundationBridge.h";')


def render_native(template, context, controls, mode, device_set, toolchain=toolchains.LOCAL):
    expected_runtime = toolchains.profile(toolchain)['runtime_version']
    require(all(isinstance(value, str) and HASH.fullmatch(value) for value in (context, controls)), 'compile-hash-binding')
    require(mode in {'disabled', 'adhoc'}, 'signing-mode')
    path = Path(device_set)
    require(path.is_absolute() and str(path).isascii() and
            path == Path.home() / 'Library/Developer/CoreSimulator/Devices', 'expected-device-set')
    require(path.is_dir() and not path.is_symlink() and path.resolve() == path, 'device-set-canonical')
    template_hash = hashlib.sha256(template.encode()).hexdigest()
    # One exact copy-only guard; never accept both runtimes or relax the patch version.
    template = once(template,
        'version.majorVersion == 26 && version.minorVersion == 5 && version.patchVersion == 0',
        'version.majorVersion == %d && version.minorVersion == %d && version.patchVersion == %d' %
        tuple(expected_runtime))
    for key, value in (('TEMPLATE_SHA256', template_hash), ('CONTEXT_SHA256', context),
                       ('CONTROLS_SHA256', controls), ('DEVICE_SET', str(path)), ('SIGNING_MODE', mode)):
        template = once(template, '__L08_' + key + '__', json.dumps(value))
    require('__L08_' not in template, 'unexpanded-native-token')
    return template, template_hash


def _copy_paths(copy_root, before, custody):
    root = Path(copy_root).absolute()
    temporary = root.parent
    require(root.name == 'copy' and TEMP.fullmatch(temporary.name) is not None and
            not temporary.is_relative_to(ROOT) and temporary.is_dir() and
            not temporary.is_symlink() and temporary.resolve() == temporary and
            root.is_dir() and not root.is_symlink() and root.resolve() == root, 'owned-copy-root')
    info = temporary.lstat()
    require(isinstance(custody, dict) and set(custody) == {'path', 'device', 'inode', 'uid'} and
            custody == dict(path=str(temporary), device=info.st_dev, inode=info.st_ino, uid=info.st_uid) and
            info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o700, 'allocated-copy-custody')
    copied = root.lstat()
    require(stat.S_ISDIR(copied.st_mode) and copied.st_uid == os.getuid() and
            copied.st_dev == info.st_dev and stat.S_IMODE(copied.st_mode) == 0o700, 'copy-directory-custody')
    require(isinstance(before, list) and 3 <= len(before) <= 50000, 'inherited-manifest-bound')
    entries = {}
    for row in before:
        require(isinstance(row, dict) and set(row) == {'path', 'original_sha256', 'copied_sha256'}, 'inherited-row-shape')
        relative = row['path']
        require(isinstance(relative, str) and relative and str(PurePosixPath(relative)) == relative and
                not PurePosixPath(relative).is_absolute() and
                all(part not in ('.', '..') for part in PurePosixPath(relative).parts) and
                relative not in entries and isinstance(row['copied_sha256'], str) and HASH.fullmatch(row['copied_sha256']),
                'inherited-path-or-hash')
        path = root / relative
        require(path.is_file() and not path.is_symlink() and path.resolve() == path and
                path.stat().st_uid == os.getuid() and path.stat().st_nlink == 1 and
                digest(path) == row['copied_sha256'], 'inherited-copy-changed')
        entries[relative] = row
    observed = set()
    for count, path in enumerate(root.rglob('*'), 1):
        require(count <= 100000 and not path.is_symlink(), 'copy-inventory-bound-or-link')
        if path.is_file():
            observed.add(str(path.relative_to(root)))
    require(observed == set(entries) and set(MODIFIED) <= observed, 'inherited-inventory-drift')
    require(not set(ADDITIONS) & observed and all(not (root / name).exists() for name in ADDITIONS), 'existing-adapter-output')
    return root, entries


def apply_owned_adapter(copy_root, before, custody, source_identity, controls, mode, device_set,
                        toolchain=toolchains.LOCAL):
    """Root must pass its creation-attested custody and freshly reviewed control binding.

    Call AFTER the inherited inspector, BEFORE writing copied-source.diff/manifest.
    Returned manifest replaces only that local output list, not companion code.
    All transformations are prepared before writes; interrupted partial copies are
    never compiled and remain exclusively covered by root's unchanged finalizer.
    """
    dependencies = pin_dependencies()
    root, entries = _copy_paths(copy_root, before, custody)
    context = context_digest(source_identity)
    rendered, template_hash = render_native((HERE / ADDITIONS[NATIVE]).read_text(), context, controls, mode,
                                            device_set, toolchain)
    originals = {name: (root / name).read_text() for name in MODIFIED}
    modifications = {
        PROJECT: transform_project(originals[PROJECT]),
        CONTENT: transform_content(originals[CONTENT], (HERE / 'L08AppFoundationLaunch.swift.in').read_text()),
        TESTS: transform_tests(originals[TESTS], (HERE / 'L08AppFoundationUITests.swift.in').read_text()),
    }
    additions = {NATIVE: rendered, BRIDGE: (HERE / ADDITIONS[BRIDGE]).read_text()}
    for name, content in additions.items():
        with (root / name).open('x') as target:
            target.write(content)
    for name, content in modifications.items():
        require(digest(root / name) == entries[name]['copied_sha256'], 'copy-changed-before-write')
        (root / name).write_text(content)
    result = [{**row, 'copied_sha256': digest(root / row['path'])} for row in before]
    result += [dict(path=name, original_sha256=None, copied_sha256=digest(root / name)) for name in additions]
    unchanged = set(entries) - set(MODIFIED)
    require(all(digest(root / name) == entries[name]['copied_sha256'] for name in unchanged), 'unrelated-copy-changed')
    return result, dict(schema_version=1, kind='L08_APP_FOUNDATION_COPY_BINDING',
        context_sha256=context, controls_sha256=controls, fixture_template_sha256=template_hash,
        native_copy_sha256=digest(root / NATIVE), bridge_copy_sha256=digest(root / BRIDGE),
        toolchain_profile=toolchain, expected_runtime_version=toolchains.profile(toolchain)['runtime_version'],
        companion_dependencies=dependencies, modified_paths=list(MODIFIED), added_paths=sorted(additions),
        mode=mode, execution='NOT_RUN', native_build='NOT_RUN', original_l08='UNCHANGED_NOT_SATISFIED')
