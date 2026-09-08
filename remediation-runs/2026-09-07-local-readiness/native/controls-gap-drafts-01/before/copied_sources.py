"""Closed, source-bound transformations of task-owned copies; never root writes."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
from instrument_storage import PATH as CREDENTIALS, ADDITION as NATIVE_ADDITION, instrument_credentials

HERE = Path(__file__).resolve().parent
SETTINGS = 'composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt'
WD = 'game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt'
MF = 'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaGameFlow.kt'
MAIN = 'composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt'
LOCALE = 'shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt'
ADDITION = 'shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/auditcopy/DSC01ComposeObservation.kt'
UIKIT_ADDITION = 'shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/auditcopy/DSC01UIKitObservation.kt'
ADDITIONS = {ADDITION: 'DSC01ComposeObservation.kt.in', UIKIT_ADDITION: 'DSC01UIKitObservation.kt.in',
             NATIVE_ADDITION: 'NativeReadinessProbe.kt.in'}
NAME_FIELDS = (
    'game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/setup/PlayerEntryScreen.kt',
    'game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/setup/MafiaPlayerEntryScreen.kt',
)
MODIFIED_KOTLIN = (SETTINGS, WD, MF, MAIN, LOCALE, CREDENTIALS, *NAME_FIELDS)
BUILD_PREFIXES = ('composeApp/', 'shared/', 'game-modes/', 'build-logic/', 'gradle/', 'config/', 'iosApp/', 'scripts/')
BUILD_ROOTS = ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')
PROTECTED = ('.keystore', '.jks', '.p12', '.p8', '.pfx', '.mobileprovision', 'credentials.json',
             'service-account', 'local.properties', '.pem', '/.git/', '/build/', '/.gradle/')
OWNED_TEMP_NAME = r'parlor-audit-ios-readiness-[0-9]{2}-[A-Za-z0-9_-]{1,64}'
SIMULATOR_NAME_TOKEN = '__PARLOR_EXPECTED_SIMULATOR_NAME__'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def allowed_path(value):
    if not isinstance(value, str):
        return False
    path = PurePosixPath(value)
    if (path.is_absolute() or '..' in path.parts or
            str(path) != value or not path.parts or any(part.startswith('.') for part in path.parts) or
            any(token in value.lower() for token in PROTECTED)):
        return False
    return value in BUILD_ROOTS or value.startswith(BUILD_PREFIXES)


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('A reviewed exact copy-only source anchor is missing or ambiguous')
    return text.replace(old, new)


def owned_simulator_name(temporary):
    """Bind the simulator to this exact allocated cycle, not a historic prefix."""
    name = Path(temporary).name
    if not re.fullmatch(OWNED_TEMP_NAME, name):
        raise RuntimeError('Simulator name must derive from this owned readiness allocation')
    return 'Parlor-Audit-' + name


def render_owned_os_prerequisite(template, name):
    if not isinstance(name, str) or not re.fullmatch('Parlor-Audit-' + OWNED_TEMP_NAME, name):
        raise RuntimeError('Refuse an unbound/unsafe expected simulator name')
    return replace_once(template, SIMULATOR_NAME_TOKEN, json.dumps(name))


def create_source_copy(root, destination, bindings):
    if destination.is_symlink() or not destination.is_dir() or any(destination.iterdir()):
        raise RuntimeError('Only a newly allocated, empty, owned copy destination is accepted')
    if bindings.get('schema_version') != 3 or bindings.get('binding_status') != 'REVIEW_REQUIRED_BOUND':
        raise RuntimeError('Frozen source binding not supplied; no implicit refresh permitted')
    expected = dict(bindings['source_identity']['source_manifest'])
    entries = bindings['copy_only']
    selected = {path: digest for path, digest in expected.items() if allowed_path(path)}
    if {entry['path']: entry['sha256'] for entry in entries} != selected or len(entries) != len(selected):
        raise RuntimeError('Copy input inventory must match every applicable bound tracked/untracked input')
    for required in (*BUILD_ROOTS, *MODIFIED_KOTLIN, 'gradle/wrapper/gradle-wrapper.jar',
                     'gradle/verification-metadata.xml', 'iosApp/iosApp/Info.plist',
                     'iosApp/iosApp/ComposeContainerViewController.swift',
                     'iosApp/iosAppUITests/ComposeContainerViewControllerTests.swift'):
        if required not in selected:
            raise RuntimeError('A required actual build input is absent from the frozen source manifest')
    for entry in entries:
        source = root / entry['path']
        target = destination / entry['path']
        if source.is_symlink() or source.resolve() != source.absolute() or sha256(source) != entry['sha256']:
            raise RuntimeError('Bound source input changed or traverses a symbolic link')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        # Preserve only executable permission, not untrusted extended metadata.
        target.chmod(0o700 if source.stat().st_mode & 0o111 else 0o600)
    for path in ADDITIONS:
        if (destination / path).exists():
            raise RuntimeError('Refuse to overwrite an existing source at a synthetic observer path')


def instrument_kotlin(copy_root):
    def change(path, transform):
        target = copy_root / path
        if target.is_symlink() or target.resolve() != target.absolute():
            raise RuntimeError('Refuse instrumentation through a symlink')
        target.write_text(transform(target.read_text()))

    change(SETTINGS, lambda text: replace_once(text,
        '    val settings: SettingsStore = koinInject()',
        '    com.parlor.designsystem.auditcopy.DSC01ObserveActualSettingsComposition()\n'
        '    val settings: SettingsStore = koinInject()'))
    for path, game, public_name in ((WD, 'whodunit', 'Whodunit'), (MF, 'mafia', 'Mafia')):
        anchor = ('    val canonicalState = requireNotNull(session.canonicalState) {\n'
                  f'        "The local {public_name} flow requires an authoritative controller"\n    }}')
        addition = ('\n    com.parlor.designsystem.auditcopy.DSC01ObserveActualLocalSession(\n'
                    f'        surface = "{game}-local",\n'
                    '        controller = session,\n'
                    '        canonical = canonicalState,\n'
                    '        publicPhase = { canonicalState.value.phase.id },\n'
                    '    )')
        change(path, lambda text, anchor=anchor, addition=addition: replace_once(text, anchor, anchor + addition))

    def main_bridge(text):
        text = replace_once(text, 'package com.parlor.app\n',
                            '@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)\n\n'
                            'package com.parlor.app\n\nimport kotlinx.coroutines.launch\n')
        for actual, event in (('notifyBackgrounded', 'background'), ('notifyActive', 'foreground'),
                              ('notifyInactive', 'inactive')):
            original = f'    lifecycleCoordinator().{actual}()'
            text = replace_once(text, original, original +
                f'\n    com.parlor.designsystem.auditcopy.DSC01CopyOnlyObservation.lifecycle("{event}")')
        return text + '\n' + (HERE / 'DSC01InvocationBridge.kt.in').read_text() + '''

fun NativeReadinessStart(boot: Int, onJson: (String) -> Unit) {
    com.parlor.app.readiness.NativeReadinessProbe.start(boot, onJson)
}
'''

    change(MAIN, main_bridge)
    change(CREDENTIALS, instrument_credentials)
    change(LOCALE, lambda text: replace_once(text,
        '    val viewController = LocalUIViewController.current',
        '    val viewController = LocalUIViewController.current\n'
        '    com.parlor.designsystem.auditcopy.DSC01ObserveActualUIKitController(viewController)'))
    for path in NAME_FIELDS:
        def fields(text):
            text = replace_once(text, 'import androidx.compose.ui.Modifier\n',
                'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.testTag\n')
            return replace_once(text,
                'modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),',
                'modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("dsc01-player-${index + 1}"),')
        change(path, fields)
    for path, template in ADDITIONS.items():
        observer = copy_root / path
        observer.parent.mkdir(parents=True, exist_ok=True)
        if observer.exists() or observer.is_symlink():
            raise RuntimeError('Refuse an existing observer addition')
        observer.write_text((HERE / template).read_text())


def inspect_copied_manifest(copy_root, bindings, additional_modified):
    if any(path.is_symlink() for path in copy_root.rglob('*')):
        raise RuntimeError('No symbolic links are permitted in the source-only build copy')
    permitted = set(MODIFIED_KOTLIN) | set(additional_modified)
    result = []
    changed = set()
    for entry in bindings['copy_only']:
        current = sha256(copy_root / entry['path'])
        if current != entry['sha256']:
            changed.add(entry['path'])
            if entry['path'] not in permitted:
                raise RuntimeError('Unreviewed source-copy difference')
        result.append(dict(path=entry['path'], original_sha256=entry['sha256'], copied_sha256=current))
    if changed != permitted:
        raise RuntimeError('Every declared copy-only transformation must actually occur, and only those')
    for path in ADDITIONS:
        result.append(dict(path=path, original_sha256=None, copied_sha256=sha256(copy_root / path)))
    actual_files = {str(path.relative_to(copy_root)) for path in copy_root.rglob('*') if path.is_file()}
    if actual_files != {entry['path'] for entry in result}:
        raise RuntimeError('Unregistered source additions in the task-owned copy')
    return result


def inspect_copied_inputs_after_build(copy_root, before):
    """Compare the instrumented source inputs after workers stop, not outputs.

    Generated build/.gradle/.kotlin trees are pruned before traversal. Existing
    reviewed inputs must retain their exact post-instrumentation bytes; a build
    adding another applicable input must not disappear with temporary cleanup.
    Return compact mismatches for durable evidence before the caller fails.
    """
    root = copy_root.absolute()
    if root.is_symlink() or root.resolve() != root:
        raise RuntimeError('Unexpected copied-source root for final identity verification')
    expected = {entry['path']: entry['copied_sha256'] for entry in before}
    if len(expected) != len(before) or not all(allowed_path(path) for path in expected):
        raise RuntimeError('Invalid original copied-source input manifest')
    files = []
    for path, digest in expected.items():
        target = root / path
        safe = target.is_file() and not target.is_symlink() and target.resolve() == target.absolute()
        observed = sha256(target) if safe else None
        files.append(dict(path=path, expected_sha256=digest, observed_sha256=observed,
                          unchanged=safe and observed == digest))
    actual, symlinks = set(), []
    def unreadable(error):
        raise error
    for directory, dirs, names in os.walk(root, followlinks=False, onerror=unreadable):
        retained = []
        for name in dirs:
            target = Path(directory) / name
            if name == 'build' or name.startswith('.'):
                continue  # Owned recreatable outputs are not source inputs.
            if target.is_symlink():
                symlinks.append(str(target.relative_to(root)))
            else:
                retained.append(name)
        dirs[:] = retained
        for name in names:
            target = Path(directory) / name
            relative = str(target.relative_to(root))
            if allowed_path(relative):
                actual.add(relative)
                if target.is_symlink():
                    symlinks.append(relative)
    additions = sorted(actual - set(expected))
    missing = sorted(set(expected) - actual)
    return dict(schema_version=1, files=files, unexpected_build_inputs=additions,
                missing_build_inputs=missing, unexpected_source_symlinks=sorted(symlinks),
                unchanged=all(entry['unchanged'] for entry in files) and not additions and not missing and not symlinks,
                method='Exact post-instrumentation input hashes and build-input inventory after task workers stop; generated build/hidden directories excluded')
