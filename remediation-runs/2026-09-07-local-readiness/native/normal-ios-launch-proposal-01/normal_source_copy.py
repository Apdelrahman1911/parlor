"""Exactly two copy-only changes; all production app sources remain identical."""
import json
from pathlib import Path
import re

UI_TEST = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
CHANGED = {UI_TEST, PROJECT}


def transform_copy(copy_root, swift_template, phase):
    root = Path(copy_root)
    if root.is_symlink() or root.resolve(strict=True) != root:
        raise RuntimeError('Normal-source transformation requires an owned canonical copy')
    for relative in CHANGED:
        path = root / relative
        if path.is_symlink() or path.resolve(strict=True) != path or not path.is_file():
            raise RuntimeError('Normal-source transformation refuses a missing or redirected input')
    (root / UI_TEST).write_text(swift_template)
    project = root / PROJECT
    original = project.read_text()
    search = '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
    matches = list(re.finditer(r'(shellScript = )"(?:[^"\\]|\\.)*";', original))
    if original.count(search) != 2 or len(matches) != 1 or 'embedAndSignAppleFrameworkForXcode' not in matches[0][0]:
        raise RuntimeError('Unexpected actual framework-search or build-phase wiring')
    match = matches[0]
    project.write_text(original[:match.start()] + 'shellScript = ' + json.dumps(phase) + ';' + original[match.end():])


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
