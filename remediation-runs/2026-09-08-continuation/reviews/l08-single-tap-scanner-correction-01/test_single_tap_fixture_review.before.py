"""Independent, read-only L08 fixture source/mutation guards.

These controls do not execute Swift, XCTest, or the application. A successful
result is source-contract evidence only, never callback-delivery, recovery,
strict file-protection, simulator, physical-device, or Store evidence. The
coordinator runs this file in its ordinary evidence/cleanup lane using python -B.
Mutations are in-memory strings; no reviewed fixture is modified by this suite.
"""

import hashlib
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[3]
COMPANION = ROOT / "remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02"
SWIFT = COMPANION / "L08StorageFunctionalUITests.swift.in"


def without_comments(source):
    """Drop Swift comments, preserving strings so comments cannot satisfy guards."""
    output = []
    index = 0
    while index < len(source):
        if source.startswith("//", index):
            end = source.find("\n", index)
            if end < 0:
                break
            output.append("\n")
            index = end + 1
        elif source.startswith("/*", index):
            depth = 1
            index += 2
            while depth and index < len(source):
                if source.startswith("/*", index):
                    depth += 1
                    index += 2
                elif source.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            if depth:
                raise ValueError("Unterminated Swift comment")
            output.append(" ")
        elif source[index] == '"':
            start = index
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == '"':
                    index += 1
                    break
                else:
                    index += 1
            else:
                raise ValueError("Unterminated Swift string")
            output.append(source[start:index])
        else:
            output.append(source[index])
            index += 1
    return "".join(output)


TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|[A-Za-z_][A-Za-z_0-9]*|\d+(?:\.\d+)?|'
                   r'\.\.<|\.\.\.|===|!==|==|!=|<=|>=|&&|\|\||\?\?|\+=|-=|->|\S')


def tokens(source):
    return TOKEN.findall(without_comments(source))


def function(source, name):
    """Extract one ordinary Swift helper body, not an executable Swift parser."""
    code = without_comments(source)
    matches = list(re.finditer(r"\bfunc\s+" + re.escape(name) + r"\s*\(", code))
    if len(matches) != 1:
        raise ValueError("Missing or duplicated helper: " + name)
    start = code.index("{", matches[0].end())
    depth = 0
    for token in TOKEN.finditer(code, start):
        if token.group() == "{":
            depth += 1
        elif token.group() == "}":
            depth -= 1
            if depth == 0:
                return code[start + 1:token.start()]
    raise ValueError("Unterminated helper: " + name)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def fragment(source, expected):
    """Require an actual contiguous token sequence, never a comment substring."""
    haystack, needle = tokens(source), tokens(expected)
    return any(haystack[index:index + len(needle)] == needle
               for index in range(len(haystack) - len(needle) + 1))


HOME_HELPERS = (
    "l08HomeGeometry", "l08HomeGeometryNear", "l08WaitStableHomeGeometry",
    "l08RequireHomeZeroBeforeTap", "l08WaitHomeOneAfterTap", "l08ReadHomeCounters",
    "l08HomeActivationDiagnostic",
)


def assert_fixture_contract(source):
    """Fence the independently reviewed actual Swift control path and its bounds."""
    main = function(source, "l08TapExactPublicControl")
    helpers = {name: function(source, name) for name in HOME_HELPERS}
    joined = main + "\n" + "\n".join(helpers.values())
    main_tokens = tokens(main)
    require(main_tokens.count("tap") == 1 and fragment(main, "target.tap()"),
            "Exactly one real target activation is required")
    require(not set(tokens(joined)).intersection({
        "coordinate", "coordinateWithNormalizedOffset", "onResume", "homeTapped",
        "performClick", "forceTap", "reduce", "submit", "navigate", "reset",
        "debugDescription", "screenshot", "label", "catch",
    }), "No alternate activation, application callback, private UI dump, or swallowed failure")
    ordered = ["list.swipeUp()", "try l08RequireHomeZeroBeforeTap(",
               "try l08WaitStableHomeGeometry(", "let current = l08HomeGeometry(",
               'phase: "before-tap"', "guard let current", "target.tap()",
               "try l08WaitHomeOneAfterTap("]
    locations = []
    for required in ordered:
        require(main.count(required) == 1, "Missing or repeated single-activation stage: " + required)
        locations.append(main.index(required))
    require(locations == sorted(locations),
            "All scrolling must precede zero-before; stable geometry must follow the observer")
    require(fragment(main, "for attempt in 0..<8 {"), "Scroll attempts must stay bounded")
    require(fragment(main, 'let isHome = homeAliases.contains { identifier == "l08-home-" + $0 }'),
            "Home specialization must use exact aliases")
    require(fragment(main, "guard let current, l08HomeGeometryNear(settled, current), "
                     "dsc01CatalogNear(target.frame, current[0]), target.isEnabled, target.isHittable else {"),
            "Final geometry and the actual activation element must agree")
    require(main_tokens.count("for") == 1 and "while" not in main_tokens and "repeat" not in main_tokens,
            "No hidden activation retry loop")
    require(fragment(main, "if isHome { try l08WaitHomeOneAfterTap(app, identifier: identifier) }"),
            "Home success requires actual callback acknowledgement")

    geometry = helpers["l08HomeGeometry"]
    for predicate in (
        'let targets = app.descendants(matching: .any).matching(identifier: identifier)',
        'let lists = app.descendants(matching: .any).matching(identifier: "l08-home-list")',
        'let overlays = app.descendants(matching: .any).matching(identifier: "parlor-dsc01-overlay")',
        'guard targets.count == 1, lists.count == 1, overlays.count == 1, '
        'app.state == .runningForeground, !app.alerts.firstMatch.exists, '
        '!app.keyboards.firstMatch.exists else { return nil }',
        'guard target.exists, target.isEnabled, target.isHittable, '
        'list.exists, list.isEnabled, list.isHittable, overlay.exists else { return nil }',
        'let frames = [target.frame, list.frame, app.frame, overlay.frame]',
        'guard frames.allSatisfy(dsc01CatalogFinite), frames[2].contains(frames[1]), '
        'frames[2].contains(frames[3]), frames[1].contains(frames[0]), '
        '!frames[0].intersects(frames[3].insetBy(dx: -8, dy: -8)) else { return nil }',
    ):
        require(fragment(geometry, predicate), "Unique, finite, full visible and uncovered geometry is required")
    require(tokens(helpers["l08HomeGeometryNear"]) == tokens(
        "first.count == 4 && second.count == 4 && "
        "zip(first, second).allSatisfy { dsc01CatalogNear($0.0, $0.1) }"),
        "All four measured public frames must remain near")

    stable = helpers["l08WaitStableHomeGeometry"]
    require(not set(tokens(stable)).intersection({"tap", "swipeUp", "press", "activate", "while", "repeat"}),
            "Readiness polling cannot interact with the app")
    require(fragment(stable, "for attempt in 0..<24 {"), "Geometry polling must be bounded")
    require(fragment(stable, "if let frames { "
                     "if let anchor, l08HomeGeometryNear(anchor, frames) { consecutive += 1 } "
                     "else { anchor = frames; consecutive = 1 } "
                     "} else { anchor = nil; consecutive = 0 }"),
            "Six consecutive samples must compare with their first anchor, not accumulate drift")
    require(fragment(stable, "if consecutive == 6, let frames { return frames }"),
            "Never accept fewer than six stable samples")
    require(fragment(stable, "if attempt < 23 { try catalogSamplingInterval() }"),
            "Distinct samples must run the bounded nonblocking interval")
    require(stable.index("try l08HomeActivationDiagnostic(") < stable.index("if consecutive == 6"),
            "Retain each geometry observation, including failed windows")
    require(fragment(stable, 'throw NSError(domain: "L08HomeActivation", code: 2)'),
            "Geometry timeout must fail, not fall through to activation")

    zero = helpers["l08RequireHomeZeroBeforeTap"]
    for predicate in (
        'let untouched = query.count == 1 && query.firstMatch.value as? String == "{}"',
        'guard untouched else {',
        'try l08TapExactPublicControl(app, identifier: "l08-ui-observe")',
        'guard query.count == 1, let text = query.firstMatch.value as? String else { return false }',
        'return text != "{}"',
        'let result = XCTWaiter.wait(for: [published], timeout: 5)',
        'guard result == .completed, counters.values.allSatisfy({ $0 == 0 }) else {',
    ):
        require(fragment(zero, predicate), "Fresh post-scroll zero counters must come from the actual observer")
    require(tokens(zero).count("l08TapExactPublicControl") == 1 and
            zero.index("guard untouched") < zero.index("try l08TapExactPublicControl(") <
            zero.index("let published") < zero.index("try l08ReadHomeCounters(") <
            zero.index("guard result == .completed"), "Zero-before observation ordering changed")
    require(fragment(zero, 'throw NSError(domain: "L08HomeActivation", code: 4)'),
            "Zero-counter failure cannot continue")

    after = helpers["l08WaitHomeOneAfterTap"]
    for predicate in (
        'for attempt in 0..<24 {',
        'try l08TapExactPublicControl(app, identifier: "l08-ui-observe")',
        'let counters = try l08ReadHomeCounters(app, identifier: identifier, phase: "after-tap", attempt: attempt)',
        'guard (0...1).contains(counters["resume_taps"]!), counters["retry_taps"] == 0, '
        'counters["discard_taps"] == 0, (0...1).contains(counters["recovery_shows"]!), '
        'app.state == .runningForeground, !app.alerts.firstMatch.exists else {',
        'if counters["resume_taps"] == 1 { return }',
        'if attempt < 23 { try catalogSamplingInterval() }',
        'throw NSError(domain: "L08HomeActivation", code: 6)',
    ):
        require(fragment(after, predicate), "After-tap acknowledgement cannot weaken exact counters or bounds")
    require(tokens(after).count("l08TapExactPublicControl") == 1 and tokens(after).count("return") == 1 and
            not set(tokens(after)).intersection({"tap", "swipeUp", "while", "repeat"}),
            "Only fresh observer reads may repeat after activation")
    require(after.index('identifier: "l08-ui-observe"') < after.index("let counters") <
            after.index("guard (0...1)") < after.index('if counters["resume_taps"] == 1'),
            "A stale result cannot bypass a fresh bounded observation and invariant check")

    read = helpers["l08ReadHomeCounters"]
    for predicate in (
        'if query.count == 1, let text = query.firstMatch.value as? String, '
        'let data = text.data(using: .utf8), data.count <= 1024, '
        'let decoded = try? JSONDecoder().decode([String: Int].self, from: data), '
        'Set(decoded.keys) == Set(["resume_taps", "retry_taps", "discard_taps", "recovery_shows"]), '
        'decoded.values.allSatisfy({ (0...2).contains($0) }) { counters = decoded }',
        'try l08HomeActivationDiagnostic(identifier: identifier, phase: phase, attempt: attempt, counters: counters)',
        'guard let counters else {',
        'throw NSError(domain: "L08HomeActivation", code: 7)',
        'return counters',
    ):
        require(fragment(read, predicate), "Counter schema must stay typed, closed, bounded and fail closed")
    require(read.index("try l08HomeActivationDiagnostic(") < read.index("guard let counters"),
            "Malformed counters require a closed diagnostic before failure")

    diagnostic = helpers["l08HomeActivationDiagnostic"]
    for predicate in (
        'guard allowed.contains(identifier), (0..<24).contains(attempt), (0...6).contains(stableSamples), '
        '["before-observer", "before-zero", "geometry", "before-tap", "after-tap"].contains(phase) else {',
        'let row: [String: Any] = ["schema_version": 1, "identifier": identifier, "phase": phase, "attempt": attempt, '
        '"geometry_valid": frames != nil, "frames": (frames ?? []).map { [$0.minX, $0.minY, $0.width, $0.height] }, '
        '"stable_samples": stableSamples, "counters_valid": counters != nil, "counters": counters ?? [:], '
        '"proves_recovery": false]',
        'guard data.count <= 2048 else { throw NSError(domain: "L08HomeDiagnostic", code: 2) }',
        'print("PARLOR_L08_HOME_ACTIVATION " + String(decoding: data, as: UTF8.self))',
    ):
        require(fragment(diagnostic, predicate), "Diagnostic schema, budgets and limited claim must remain closed")
    require(not set(tokens(diagnostic)).intersection({"value", "localizedDescription", "error", "seed", "role"}),
            "Diagnostics cannot print UI/private/error contents")


class SingleTapFixtureContractTests(unittest.TestCase):
    def setUp(self):
        self.source = SWIFT.read_text()

    def test_actual_swift_matches_independently_reviewed_contract(self):
        assert_fixture_contract(self.source)

    def test_all_thirteen_boot_callers_and_storage_assertions_are_unchanged(self):
        prefix = self.source.split("    @MainActor private func l08TapExactPublicControl(", 1)[0]
        self.assertEqual(hashlib.sha256(prefix.encode()).hexdigest(),
                         "b7788b8547813c0982a5f4da63833620fd08c6b7941ecab33cccee9706b7f7ef")

    def test_shared_geometry_helpers_have_strict_tolerance_and_runloop_wait(self):
        shared = (COMPANION / "DSC01CatalogSelection.swift.in").read_text()
        self.assertTrue(fragment(function(shared, "dsc01CatalogFinite"),
                                 "!rect.isNull && !rect.isInfinite && rect.size.width > 0 && rect.size.height > 0"))
        self.assertTrue(fragment(function(shared, "dsc01CatalogNear"),
                                 ".allSatisfy { abs($0.0 - $0.1) <= 0.5 }"))
        interval = function(shared, "catalogSamplingInterval")
        self.assertTrue(fragment(interval, "DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { interval.fulfill() }"))
        self.assertTrue(fragment(interval, "guard XCTWaiter.wait(for: [interval], timeout: 3) == .completed else {"))
        self.assertNotIn("Thread", tokens(interval))

    def assert_mutations_rejected(self, mutations):
        for old, new in mutations:
            with self.subTest(old=old, new=new):
                self.assertIn(old, self.source)
                altered = self.source.replace(old, new, 1)
                self.assertNotEqual(altered, self.source)
                with self.assertRaises(ValueError):
                    assert_fixture_contract(altered)

    def test_repeat_activation_and_direct_callback_mutations_rejected(self):
        self.assert_mutations_rejected([
            ("target.tap()", "target.tap(); target.tap()"),
            ("target.tap()", "target.coordinate(withNormalizedOffset: .zero).tap()"),
            ("target.tap()", "homeTapped(); target.tap()"),
            ("target.tap()", "onResume(); target.tap()"),
            ("target.tap()", "/* target.tap() */"),
            ("target.tap()", "for ignored in 0..<2 { target.tap() }"),
            ("if isHome { try l08WaitHomeOneAfterTap(app, identifier: identifier) }", ""),
        ])

    def test_pre_scroll_zero_and_missing_fresh_resample_mutations_rejected(self):
        call = "            try l08RequireHomeZeroBeforeTap(app, identifier: identifier)\n"
        self.assertEqual(self.source.count(call), 1)
        moved = self.source.replace(call, "").replace("        if isHome {\n", "        if isHome {\n" + call, 1)
        with self.assertRaises(ValueError):
            assert_fixture_contract(moved)
        self.assert_mutations_rejected([
            ("let current = l08HomeGeometry(app, identifier: identifier)", "let current = settled"),
            ("dsc01CatalogNear(target.frame, current[0])", "true"),
            ("guard let current, l08HomeGeometryNear(settled, current)", "guard let current, true"),
        ])

    def test_geometry_visibility_uniqueness_and_bounds_mutations_rejected(self):
        self.assert_mutations_rejected([
            ("guard targets.count == 1, lists.count == 1, overlays.count == 1", "guard targets.count >= 1, lists.count >= 1, overlays.count >= 1"),
            ("guard frames.allSatisfy(dsc01CatalogFinite)", "guard true"),
            ("frames[1].contains(frames[0])", "frames[1].intersects(frames[0])"),
            ("!frames[0].intersects(frames[3].insetBy(dx: -8, dy: -8))", "true"),
            ("first.count == 4 && second.count == 4", "first.count >= 1 && second.count >= 1"),
            ("if consecutive == 6, let frames", "if consecutive == 1, let frames"),
            ("if let anchor, l08HomeGeometryNear(anchor, frames) { consecutive += 1 }", "if let previous = anchor, l08HomeGeometryNear(previous, frames) { consecutive += 1; anchor = frames }"),
            ("} else { anchor = nil; consecutive = 0 }", "} else { consecutive += 1 }"),
            ("if attempt < 23 { try catalogSamplingInterval() }", "if attempt < 23 { /* no actual wait */ }"),
        ])

    def test_counter_freshness_and_schema_mutations_rejected(self):
        self.assert_mutations_rejected([
            ('let untouched = query.count == 1 && query.firstMatch.value as? String == "{}"', "let untouched = true"),
            ('return text != "{}"', "return true"),
            ('guard result == .completed, counters.values.allSatisfy({ $0 == 0 })', 'guard result == .completed, counters.values.allSatisfy({ $0 >= 0 })'),
            ('JSONDecoder().decode([String: Int].self, from: data)', 'JSONDecoder().decode([String: Bool].self, from: data)'),
            ('data.count <= 1024', 'data.count <= 1048576'),
            ('Set(decoded.keys) == Set(["resume_taps", "retry_taps", "discard_taps", "recovery_shows"])', 'decoded.keys.contains("resume_taps")'),
            ('if counters["resume_taps"] == 1 { return }', 'if counters["resume_taps"] >= 0 { return }'),
            ('counters["discard_taps"] == 0, (0...1).contains(counters["recovery_shows"]!)', 'true, (0...1).contains(counters["recovery_shows"]!)'),
        ])

    def test_after_tap_polling_cannot_reactivate_or_be_unbounded(self):
        clean = without_comments(self.source)
        original = function(clean, "l08WaitHomeOneAfterTap")
        for old, new in (
            ('identifier: "l08-ui-observe"', 'identifier: identifier'),
            ('for attempt in 0..<24 {', 'for attempt in 0..<1000 {'),
            ('for attempt in 0..<24 {', 'while true {'),
            ('throw NSError(domain: "L08HomeActivation", code: 6)', 'return'),
        ):
            with self.subTest(mutation=old):
                self.assertIn(old, original)
                altered = clean.replace(original, original.replace(old, new, 1), 1)
                self.assertNotEqual(altered, clean)
                with self.assertRaises(ValueError):
                    assert_fixture_contract(altered)

    def test_private_or_unbounded_or_overclaiming_diagnostic_mutations_rejected(self):
        clean = without_comments(self.source)
        original = function(clean, "l08HomeActivationDiagnostic")
        for old, new in (
            ('"proves_recovery": false', '"proves_recovery": true'),
            ('"counters": counters ?? [:]', '"counters": counters ?? [:], "role": "PRIVATE"'),
            ('guard data.count <= 2048', 'guard data.count <= 999999'),
            ('print("PARLOR_L08_HOME_ACTIVATION " + String(decoding: data, as: UTF8.self))', 'print(app.debugDescription)'),
        ):
            with self.subTest(mutation=old):
                self.assertIn(old, original)
                altered = clean.replace(original, original.replace(old, new, 1), 1)
                self.assertNotEqual(altered, clean)
                with self.assertRaises(ValueError):
                    assert_fixture_contract(altered)


class UnchangedApplicationBoundaryTests(unittest.TestCase):
    def test_actual_counter_seam_and_observer_stay_byte_identical(self):
        expected = {
            "L08UiSeam.kt.in": "22d9e352ba6c4b8e161b2e69f1aae208a0c9a654ed85284ef4a34c2ea63399e4",
            "L08StorageFunctionalLaunch.swift.in": "cc61c02ceae5b5144cdc66dd12927dd31475ac3fac76be7732151582a1d777a6",
            "L08StorageFunctionalProbe.kt.in": "55d9ccb998831b07a43429249fc335189227bcd344f252dbcd4dbdbda963463c",
            "l08_copy.py": "c2bf28bf0efd9449d646c451d7b8e665000e8d11a3a6eea76de95e1fb4a55a5f",
        }
        for name, digest in expected.items():
            with self.subTest(name=name):
                self.assertEqual(hashlib.sha256((COMPANION / name).read_bytes()).hexdigest(), digest)

    def test_shipping_home_callback_and_card_are_not_replaced(self):
        expected = {
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt":
                "ae11eb3dd97c28520205237b95c28766ccd6ce8dd0e005f82a0d63866323e152",
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorCard.kt":
                "e764e6c7e09dc72464ad3eb14b41328a609a7d47475941054fb7af292f7a9d51",
        }
        for name, digest in expected.items():
            with self.subTest(name=name):
                self.assertEqual(hashlib.sha256((ROOT / name).read_bytes()).hexdigest(), digest)

    def test_counter_guard_precedes_actual_controller_attachment(self):
        probe = (COMPANION / "L08StorageFunctionalProbe.kt.in").read_text()
        start = probe.index('"continue" -> {')
        end = probe.index('"legacy-neighbor" -> {', start)
        continuation = probe[start:end]
        self.assertLess(continuation.index("check(L08UiSeam.resumeTaps == 1 && L08UiSeam.discardTaps == 0)"),
                        continuation.index("withTimeout(10_000)"))
        self.assertIn("L08WhodunitSnapshots.continueActualResumedController()", continuation)
        self.assertIn("L08MafiaSnapshots.continueActualResumedController { stage = it }", continuation)
        self.assertIn("awaitAbsent(store, id)", continuation)
        self.assertNotIn("store.delete", continuation)

    def test_source_scanner_does_not_accept_comment_laundering(self):
        self.assertFalse(fragment('// target.tap()\n/* target.tap() */', 'target.tap()'))
        self.assertTrue(fragment('target /* ordinary /* nested */ comment */ . tap ()', 'target.tap()'))
        self.assertEqual(tokens('let key = "https://public.example/a/*b*/"'),
                         ['let', 'key', '=', '"https://public.example/a/*b*/"'])
        self.assertEqual(function('func target() { if ok { call() }; "}" }', 'target').strip(),
                         'if ok { call() }; "}"')


if __name__ == "__main__":
    unittest.main(verbosity=2)
