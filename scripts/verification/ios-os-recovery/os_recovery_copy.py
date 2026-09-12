"""Closed copy-only OS stages; imports never load a project module or run tools."""
import difflib
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FUNCTIONAL = ROOT / 'remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02'
APP = 'iosApp/iosApp/iOSApp.swift'
CONTENT = 'iosApp/iosApp/ContentView.swift'
UI_TEST = 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift'
PROJECT = 'iosApp/iosApp.xcodeproj/project.pbxproj'
SWIFT_CHANGED = (APP, CONTENT, UI_TEST, PROJECT)
BOOTSTRAP_METHOD = 'IOSAppLaunchUITests/testDSC01OSPublicBootstrap()'
PROOF_METHOD = 'IOSAppLaunchUITests/testDSC01OSPerAppRecovery()'


def once(text, before, after):
    if text.count(before) != 1:
        raise RuntimeError('OS recovery copy anchor absent or ambiguous')
    return text.replace(before, after)


def require_module(module, name):
    expected = FUNCTIONAL / (name + '.py')
    if Path(module.__file__) != expected or expected.is_symlink() or expected.resolve() != expected:
        raise RuntimeError('OS recovery helper is not the exact functional companion source')


def render_prerequisite(text):
    """Default/full still runs the original algorithm; neither new mode fakes its proof."""
    text = once(text, 'private enum DSC01OSPrerequisiteStage: String {',
                'private enum DSC01OSRecoveryMode: Equatable { case full, bootstrap, proofOnly }\n\n'
                'private enum DSC01OSPrerequisiteStage: String {')
    text = once(text, '        expectedSimulator: String) throws {',
                '        expectedSimulator: String, mode: DSC01OSRecoveryMode = .full) throws {')
    text = once(text, '        defer { settings.terminate() }',
                '        // Bootstrap must not issue explicit or deferred AX calls after its one tap.\n'
                '        // The owned lane retains responsibility for final simulator/app cleanup.\n'
                '        defer { if mode != .bootstrap { settings.terminate() } }')
    text = once(text, '        // Both complete local-game receipts precede this. Do not mutate a user',
                '        // Full mode follows both local games; the distinct OS-only stages do not run them.\n'
                '        // Every mode requires this owned disposable device. Do not mutate a user')
    text = once(text, '        try diagnostics.append(stage: .initialEnglishPrimary, status: "PASS", fields: initial)',
                '        // A separate invocation may only prove persisted state, never retry setup.\n'
                '        guard mode != .proofOnly || disposition == .alreadySatisfied else {\n'
                '            try osPrerequisiteBlock(.initialEnglishPrimary, diagnostics: diagnostics, fields: initial)\n'
                '        }\n'
                '        try diagnostics.append(stage: .initialEnglishPrimary, status: "PASS", fields: initial)')
    text = once(text, '        keepEnglish.firstMatch.tap() // Exactly once; final actual preferred list must prove the result.',
                '        keepEnglish.firstMatch.tap() // Exactly once; final actual preferred list must prove the result.\n'
                '        if mode == .bootstrap { return } // No post-tap query, marker, wait or cleanup call.')
    return text


def render_probe(text):
    return once(text, '        report = value\n        if publish { try publishObservation(observation) }',
                '        report = value\n'
                '        if scenario == "os" && phase == "before_main" {\n'
                '            try recordOSRecoveryImages(report: value)\n'
                '        }\n'
                '        if publish { try publishObservation(observation) }')


def transform_copy(copy_root, phase, fcopy, functional_copy, artifact_inventory):
    """Called once by the private normal Lane, before its one build and both selectors."""
    for module, name in ((fcopy, 'copied_sources'), (functional_copy, 'l08_functional_copy'),
                         (artifact_inventory, 'artifact_inventory')):
        require_module(module, name)
    copy_root = Path(copy_root)
    if copy_root.is_symlink() or copy_root.resolve(strict=True) != copy_root:
        raise RuntimeError('OS recovery requires the canonical owned source copy')
    temporary = copy_root.parent
    name = fcopy.owned_simulator_name(temporary)
    fixture = lambda filename: (FUNCTIONAL / filename).read_text()
    fcopy.instrument_kotlin(copy_root)  # Unchanged actual Parlor/Kotlin graph and reviewed observation seams.
    content = once((copy_root / CONTENT).read_text(), '        .ignoresSafeArea(.container, edges: .all)',
                   '        .ignoresSafeArea(.container, edges: .all)\n'
                   '        .overlay(alignment: .top) { DSC01Overlay().padding(.top, 72) }')
    content = once(content, '        ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())',
                   '        let controller = ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())\n'
                   '        DSC01Probe.shared.attach(controller)\n        return controller')
    probe = render_probe(functional_copy.instrument_probe_swift(fixture('DSC01Probe.swift.in')))
    native = artifact_inventory.render_owned_native_launch(fixture('NativeReadinessLaunch.swift.in'), temporary, 'adhoc')
    (copy_root / CONTENT).write_text('\n'.join((content, fixture('DSC01NativeObservation.swift.in'), probe, native,
        fixture('L08StorageFunctionalLaunch.swift.in'), fixture('L08HostLaunch.swift.in'),
        (HERE / 'OSRecoveryProbe.swift.in').read_text())))
    app = copy_root / APP
    app.write_text(once(app.read_text(), 'struct iOSApp: App {',
                        'struct iOSApp: App {\n    init() { DSC01Probe.shared.prepare() }'))
    ui = functional_copy.instrument_ui_test(fixture('IOSAppLaunchUITests.swift.in'))
    prerequisite = fcopy.render_owned_os_prerequisite(render_prerequisite(fixture('DSC01OSPrerequisite.swift.in')), name)
    (copy_root / UI_TEST).write_text('\n'.join((ui, fixture('DSC01PublicDiagnostics.swift.in'),
        fixture('DSC01CatalogSelection.swift.in'), fixture('DSC01MafiaStartSelection.swift.in'), prerequisite,
        fixture('DSC01OSAppSettings.swift.in'), fixture('DSC01OSPreferenceBaseline.swift.in'),
        fixture('NativeReadinessUITests.swift.in'), fixture('L08StorageFunctionalUITests.swift.in'),
        fixture('L08HostUITests.swift.in'), (HERE / 'OSRecoveryUITests.swift.in').read_text())))
    # Same closed project anchors as F and normal; phase is already rendered by
    # the owned caller. Never change linker, application identity or graph settings.
    project = copy_root / PROJECT
    original = project.read_text()
    search = '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
    matches = list(re.finditer(r'(shellScript = )"(?:[^"\\]|\\.)*";', original))
    if original.count(search) != 2 or len(matches) != 1 or 'embedAndSignAppleFrameworkForXcode' not in matches[0][0]:
        raise RuntimeError('OS recovery project framework/build-phase anchors drifted')
    match = matches[0]
    project.write_text(original[:match.start()] + 'shellScript = ' + json.dumps(phase) + ';' + original[match.end():])


def source_diff(copy_root, fcopy):
    """Complete retained diff, including every existing F Kotlin addition."""
    require_module(fcopy, 'copied_sources')
    copy_root = Path(copy_root)
    result = ''.join(''.join(difflib.unified_diff((ROOT / path).read_text().splitlines(True),
        (copy_root / path).read_text().splitlines(True), fromfile='original/' + path,
        tofile='os-recovery-copy/' + path)) for path in sorted(set(SWIFT_CHANGED) | set(fcopy.MODIFIED_KOTLIN)))
    for path in sorted(fcopy.ADDITIONS):
        result += ''.join(difflib.unified_diff([], (copy_root / path).read_text().splitlines(True),
                          fromfile='/dev/null', tofile='os-recovery-copy/' + path))
    return result
