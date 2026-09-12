"""Exact-input, OS-region-only correction of an unsupported presence assumption.

Original V11 controls remain immutable. This changes no preference writes,
production code, target selectors, timing, game scenario or cleanup operation.
"""
import hashlib

from v10_sources import replace_once


V11_RENDERED_UI_SHA = '0abafd9a83542a1794a2536eb23ddcc093ba1f963ed885c65d0ee486b0466333'
BEGIN = '    @MainActor private func investigateActualOSPerAppLanguage('
END = '    @MainActor private func recordOSSelectorDiagnostics('
REPLACEMENTS = (
    ('        try verifyOSInteractionDecisionContract()',
     '        try verifyOSPreferenceBaselineDecisionContract()\n        try verifyOSInteractionDecisionContract()'),
    ('        _ = try syntheticLanguage(app, language: "system")',
     '        let beforeOSChoice = try syntheticLanguage(app, language: "system")'),
    ('''                   preferences["preferredLanguage"] as? String == "en" &&
                   preferences["hasAppOverride"] as? Bool == true''',
     '''                   preferences["preferredLanguage"] as? String == "en"'''),
    ('''        let actualOSPreferences = try XCTUnwrap(try dictionary(afterOS, "now")["appLanguages"] as? [String])
        XCTAssertFalse(actualOSPreferences.isEmpty)''',
     '''        let osBaseline = try captureOSPreferenceBaseline(afterOS, beforeChoice: beforeOSChoice)
        let actualOSPreferences = osBaseline.value.languages'''),
    ('        try assertOwned(explicit, explicit: "ar", previous: actualOSPreferences)',
     '''        try assertOwned(explicit, explicit: "ar", previous: actualOSPreferences)
        try recordOSPreferenceStage("arabic", snapshot: explicit, baseline: osBaseline)'''),
    ('        try assertSystem(try observe(app), expected: "en", previous: actualOSPreferences)',
     '''        let restoredOS = try observe(app)
        try assertSystem(restoredOS, expected: "en", previous: actualOSPreferences)
        try recordOSPreferenceStage("system", snapshot: restoredOS, baseline: osBaseline)'''),
    ('        try assertSystem(restarted, expected: "en", previous: actualOSPreferences)',
     '''        try assertSystem(restarted, expected: "en", previous: actualOSPreferences)
        // Read actual localized Settings and Compose direction after the fresh
        // restart too, not merely a locale or root UIKit approximation.
        try openSettings(app, language: "en")
        let restartedSettings = try observe(app)
        try recordOSPreferenceStage("restart", snapshot: restartedSettings, baseline: osBaseline)'''),
)


def render_v12_ui(source):
    if hashlib.sha256(source.encode()).hexdigest() != V11_RENDERED_UI_SHA:
        raise RuntimeError('V12 requires exact independently executed V11 UI input')
    if source.count(BEGIN) != 1 or source.count(END) != 1:
        raise RuntimeError('V12 OS-only transformation boundaries changed')
    start = source.index(BEGIN); end = source.index(END, start)
    region = source[start:end]
    for old, new in REPLACEMENTS:
        region = replace_once(region, old, new)
    return source[:start] + region + source[end:]
