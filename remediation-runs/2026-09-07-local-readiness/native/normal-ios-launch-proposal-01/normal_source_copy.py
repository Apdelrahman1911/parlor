"""Exactly two copy-only changes; all production app sources remain identical."""
import json
from pathlib import Path
import re

UI_TEST = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
CHANGED = {UI_TEST, PROJECT}
KOTLIN_PHASE_ID = 'F7AC9D762B0E10F7008B8A48'
METADATA_PHASE_ID = 'D15000012C00000100000001'
APP_TARGET_ID = '7555FF76242A565900829871'
TEST_TARGET_ID = 'B14500012C0000010000000A'
QUOTED = r'"(?:[^"\\]|\\.)*"'
FLAT_OBJECT = re.compile(
    r'^[ \t]*([A-F0-9]{24}) /\* ([^\n*]+) \*/ = \{'
    r'((?:[^{}"]|' + QUOTED + r')*)\};[ \t]*$', re.MULTILINE)
PRODUCTION_KOTLIN_PHASE = (
    'set -e\ncd "$SRCROOT/.."\n'
    'if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then\n'
    '  echo "Skipping Gradle build task invocation due to '
    'OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \\"YES\\""\n'
    '  exit 0\nfi\n'
    './gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode '
    '--dependency-verification=strict --console=plain\n'
    'scripts/release/normalize_embedded_apple_framework.sh '
    '"${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}" ComposeApp\n'
)
PRODUCTION_METADATA_PHASE = '/bin/sh "$SRCROOT/scripts/copy_localized_metadata.sh"\n'


def _objects(original, section):
    # Deliberately accept only the current flat PBX sections, not arbitrary
    # OpenStep syntax. Quoted shell strings may contain braces/escaped quotes.
    begin, end = f'/* Begin {section} section */', f'/* End {section} section */'
    if original.count(begin) != 1 or original.count(end) != 1:
        raise RuntimeError('Missing or ambiguous copied-project section')
    start = original.index(begin) + len(begin)
    finish = original.index(end)
    if finish <= start:
        raise RuntimeError('Reversed copied-project section')
    body = original[start:finish]
    matches = list(FLAT_OBJECT.finditer(body))
    remainder = FLAT_OBJECT.sub('', body)
    if remainder.strip() or len({m[1] for m in matches}) != len(matches):
        raise RuntimeError('Unknown or duplicated copied-project object wiring')
    return start, {m[1]: m for m in matches}


def _field(body, key, quoted=False):
    value = QUOTED if quoted else r'[^;\n]+'
    matches = list(re.finditer(r'^[ \t]*' + key + r' = (' + value + r');[ \t]*$', body, re.MULTILINE))
    if len(matches) != 1:
        raise RuntimeError('Missing or ambiguous copied-project field: ' + key)
    match = matches[0]
    return (json.loads(match[1]) if quoted else match[1]), match


def _kotlin_phase_span(original):
    search = '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
    start, phases = _objects(original, 'PBXShellScriptBuildPhase')
    if (original.count(search) != 2 or set(phases) != {KOTLIN_PHASE_ID, METADATA_PHASE_ID}
            or len(re.findall(r'\bshellScript = ', original)) != 2
            or len(re.findall(r'\bisa = PBXShellScriptBuildPhase;', original)) != 2):
        raise RuntimeError('Unexpected actual framework-search or build-phase wiring')
    selected = None
    for phase_id, name, script in (
            (KOTLIN_PHASE_ID, 'Compile Kotlin Framework', PRODUCTION_KOTLIN_PHASE),
            (METADATA_PHASE_ID, 'Copy Localized App Metadata', PRODUCTION_METADATA_PHASE)):
        obj = phases[phase_id]
        body = obj[3]
        actual, field = _field(body, 'shellScript', quoted=True)
        if (obj[2] != name or _field(body, 'isa')[0] != 'PBXShellScriptBuildPhase'
                or _field(body, 'name', quoted=True)[0] != name
                or _field(body, 'shellPath')[0] != '/bin/sh' or actual != script):
            raise RuntimeError('Unexpected production shell phase: ' + name)
        if phase_id == KOTLIN_PHASE_ID:
            selected = (start + obj.start(3) + field.start(1), start + obj.start(3) + field.end(1))

    _, targets = _objects(original, 'PBXNativeTarget')
    if set(targets) != {APP_TARGET_ID, TEST_TARGET_ID}:
        raise RuntimeError('Unexpected copied-project native targets')
    for target_id, target in targets.items():
        if _field(target[3], 'isa')[0] != 'PBXNativeTarget':
            raise RuntimeError('Unexpected copied-project target type')
        lists = re.findall(r'\bbuildPhases = \(([^()]*)\);', target[3])
        if len(lists) != 1:
            raise RuntimeError('Missing or ambiguous target build phases')
        refs = re.findall(r'\b[A-F0-9]{24}\b', lists[0])
        for phase_id in phases:
            expected = 1 if target_id == APP_TARGET_ID else 0
            if refs.count(phase_id) != expected:
                raise RuntimeError('Shell phase must belong once to the application target only')
    for phase_id in phases:
        if len(re.findall(r'\b' + phase_id + r'\b', original)) != 2:
            raise RuntimeError('Unexpected shell-phase reference outside the application target')
    return selected


def transform_copy(copy_root, swift_template, phase):
    root = Path(copy_root)
    if root.is_symlink() or root.resolve(strict=True) != root:
        raise RuntimeError('Normal-source transformation requires an owned canonical copy')
    for relative in CHANGED:
        path = root / relative
        if path.is_symlink() or path.resolve(strict=True) != path or not path.is_file():
            raise RuntimeError('Normal-source transformation refuses a missing or redirected input')
    project = root / PROJECT
    original = project.read_text()
    start, end = _kotlin_phase_span(original)
    # Validate the full actual project before either copy-only write. Replace
    # only this phase's quoted value; metadata/target/search bytes stay intact.
    updated = original[:start] + json.dumps(phase) + original[end:]
    (root / UI_TEST).write_text(swift_template)
    project.write_text(updated)


def inventory_copy(copy_root, bindings, digest):
    root = Path(copy_root)
    expected = {entry['path']: entry['sha256'] for entry in bindings['copy_only']}
    if len(expected) != len(bindings['copy_only']) or not CHANGED <= set(expected):
        raise RuntimeError('Missing or duplicated source-bound copy inventory')
    actual = set()
    for index, path in enumerate(root.rglob('*')):
        if index >= 100000 or path.is_symlink() or path.resolve(strict=True) != path:
            raise RuntimeError('Unbounded or redirected source-only copy inventory')
        if path.is_file():
            actual.add(str(path.relative_to(root)))
    if actual != set(expected):
        raise RuntimeError('Unregistered normal-source copy addition or omission')
    rows, changed = [], set()
    for relative, original in sorted(expected.items()):
        copied = digest(root / relative)
        if copied != original:
            changed.add(relative)
        rows.append(dict(path=relative, original_sha256=original, copied_sha256=copied))
    if changed != CHANGED:
        raise RuntimeError('Only the UI-test source and isolated build phase may differ; no app probes')
    return rows
