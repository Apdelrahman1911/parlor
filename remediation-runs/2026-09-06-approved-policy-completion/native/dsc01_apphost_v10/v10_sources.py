"""Exact source-bound OS-only copy transformations; no application mutation.

V9 originals remain immutable and all old control assertions still execute.
The final copied-source diff must additionally expose these substitutions.
"""
import hashlib


ORIGINAL_UI_SHA = '82471590bf434cb0b174f6ba44f0d8c0b377e4fee3c4eb0dfe39bbfaa21946e5'
ORIGINAL_PROBE_SHA = 'b20b07d53feb3fd7d5aebeb8a71a5742957d9ddab8eaf6bf981736318c3cfa94'
OS_FUNCTION = '    @MainActor private func investigateActualOSPerAppLanguage('
POST_SELECTION = '        XCTAssertFalse(settings.alerts.firstMatch.exists, "Do not dismiss unknown OS alerts")'
ORIGINAL_PREFIX_SHA = '08efd9ddd785ba3c54fc6be0317c48db7b495c4cbf294cbb1e584680535f92b6'
OLD_VERIFIED = '''        app.buttons["parlor-dsc01-os-verified"].tap()
        app.buttons["parlor-dsc01-complete"].tap()'''
NEW_VERIFIED = '''        try finishOSInvestigation(app, status: "verified-english", diagnostics: osDiagnostics)'''
NEW_PREFIX = '''    @MainActor private func investigateActualOSPerAppLanguage(_ app: XCUIApplication) throws {
        try verifyOSInteractionDecisionContract()
        try startScenario(app, name: "os")
        _ = try syntheticLanguage(app, language: "system")
        app.buttons["parlor-dsc01-open-os"].tap()
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        let osDiagnostics = DSC01OSAppDiagnostics()
        defer { settings.terminate() }
        XCTAssertTrue(settings.wait(for: .runningForeground, timeout: 20),
                      "Supported UIApplication.openSettingsURLString must open actual Settings")
        // URL acceptance is not proof of the app-specific pane. Only observed
        // public Settings > Apps > Parlor routing may establish that context.
        guard try establishOSAppPane(settings, diagnostics: osDiagnostics),
              try activateOSRow(settings, control: .language, pane: .parlor,
                                diagnostics: osDiagnostics, allowScroll: false) else {
            recordOSSelectorDiagnostics(settings)
            app.activate(); XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
            try finishOSInvestigation(app, status: "language-control-unavailable", diagnostics: osDiagnostics)
            XCTAssertEqual(try observe(app)["osDisposition"] as? String, "language-control-unavailable")
            return // Explicit BLOCKED; an unobserved pane is not an absent OS feature.
        }
        guard try awaitOSPane(settings, allowed: [.language], diagnostics: osDiagnostics, stage: "english"),
              try activateOSRow(settings, control: .english, pane: .language,
                                diagnostics: osDiagnostics, allowScroll: false) else {
            recordOSSelectorDiagnostics(settings)
            app.activate(); XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
            try finishOSInvestigation(app, status: "selection-unavailable", diagnostics: osDiagnostics)
            XCTAssertEqual(try observe(app)["osDisposition"] as? String, "selection-unavailable")
            return
        }
'''


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise RuntimeError('OS-only copy transformation requires exactly one original source boundary')
    return source.replace(old, new, 1)


def render_v10_ui(source):
    if hashlib.sha256(source.encode()).hexdigest() != ORIGINAL_UI_SHA:
        raise RuntimeError('V9 original complete UI matrix changed; never transform an unreviewed input')
    start = source.index(OS_FUNCTION)
    end = source.index(POST_SELECTION, start)
    prefix = source[start:end]
    if hashlib.sha256(prefix.encode()).hexdigest() != ORIGINAL_PREFIX_SHA:
        raise RuntimeError('Original OS preselection boundary changed')
    result = replace_once(source, prefix, NEW_PREFIX)
    return replace_once(result, OLD_VERIFIED, NEW_VERIFIED)


OLD_MARKER_LAYOUT = '''            if probe.isOSScenario {
                HStack(spacing: 8) {
                    Button("OS row unavailable") { probe.markOS("language-control-unavailable") }.accessibilityIdentifier("parlor-dsc01-os-no-row")
                    Button("OS selection unavailable") { probe.markOS("selection-unavailable") }.accessibilityIdentifier("parlor-dsc01-os-no-selection")
                    Button("OS verified") { probe.markOS("verified-english") }.accessibilityIdentifier("parlor-dsc01-os-verified")
                }
            }'''

NEW_MARKER_LAYOUT = '''            if probe.isOSScenario {
                // Keep the audit overlay compact during all actual Settings
                // actions. Expand only after entering terminal reporting.
                if probe.osMarkerControlsVisible {
                    VStack(spacing: 4) {
                        Button { probe.markOS("language-control-unavailable") } label: {
                            Text("OS row unavailable").frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("parlor-dsc01-os-no-row")
                        Button { probe.markOS("selection-unavailable") } label: {
                            Text("OS selection unavailable").frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("parlor-dsc01-os-no-selection")
                        Button { probe.markOS("verified-english") } label: {
                            Text("OS verified").frame(maxWidth: .infinity, minHeight: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("parlor-dsc01-os-verified")
                    }
                } else {
                    Button("OS receipt controls") { probe.showOSMarkerControls() }
                        .accessibilityIdentifier("parlor-dsc01-os-show-markers")
                }
            }'''

MARKER_PROPERTY = '    @Published private(set) var osMarkerControlsVisible = false\n'
MARKER_REVEAL = '''    func showOSMarkerControls() {
        precondition(Thread.isMainThread && scenario == "os" && report?.completed == false)
        precondition(!osMarkerControlsVisible, "OS reporting controls must expand once")
        osMarkerControlsVisible = true
    }

'''


def render_v10_probe(source):
    if hashlib.sha256(source.encode()).hexdigest() != ORIGINAL_PROBE_SHA:
        raise RuntimeError('V9 original probe changed; never transform an unreviewed input')
    result = replace_once(source, '    private var osDisposition = "not-investigated"',
        MARKER_PROPERTY + '    private var osDisposition = "not-investigated"')
    result = replace_once(result, '    func markOS(_ status: String) {', MARKER_REVEAL + '    func markOS(_ status: String) {')
    return replace_once(result, OLD_MARKER_LAYOUT, NEW_MARKER_LAYOUT)
