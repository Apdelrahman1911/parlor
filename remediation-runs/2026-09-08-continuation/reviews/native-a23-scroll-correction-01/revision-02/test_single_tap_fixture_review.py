"""Draft-only L08 scroll-correction source/mutation/geometry-model guards.

Not independently approved and NOT Swift, XCTest, or application execution.
The new geometry replay is an explicitly conditional Python reference model;
its policy body is token-bound to the draft Swift, not a native runtime oracle.
Draft paths must be independently reviewed on canonical promotion.

Inherited independently authored single-tap guards:

These controls do not execute Swift, XCTest, or the application. A successful
result is source-contract evidence only, never callback-delivery, recovery,
strict file-protection, simulator, physical-device, or Store evidence. The
coordinator runs this file in its ordinary evidence/cleanup lane using python -B.
Mutations are in-memory strings; no reviewed fixture is modified by this suite.
"""

from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[5]
COMPANION = ROOT / "remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-02"
SWIFT = Path(__file__).with_name("L08StorageFunctionalUITests.swift.in")
A23 = ROOT / "remediation-runs/2026-09-08-continuation/actions/34303850578/native-evidence/ios-readiness-23"


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
    "l08HomeActivationDiagnostic", "l08HomeScrollDirection", "l08SettleHomeScrollDecision",
    "l08PublicControlObservation", "l08WaitRecovery",
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
    ordered = ["try l08SettleHomeScrollDecision(", "if direction == .ready { break }",
               "guard attempt < 8 else {", "list.swipeUp(velocity: .slow)",
               "list.swipeDown(velocity: .slow)", "try l08RequireHomeZeroBeforeTap(",
               "try l08WaitStableHomeGeometry(", "let current = l08HomeGeometry(",
               'phase: "before-tap"', "guard let current", "target.tap()",
               "try l08WaitHomeOneAfterTap("]
    locations = []
    for required in ordered:
        require(main.count(required) == 1, "Missing or repeated single-activation stage: " + required)
        locations.append(main.index(required))
    require(locations == sorted(locations),
            "All scrolling must precede zero-before; stable geometry must follow the observer")
    require(fragment(main, "for attempt in 0...8 {"), "Nine decisions may allow at most eight gestures")
    require(fragment(main, 'throw NSError(domain: "L08HomeScroll", code: 1)'),
            "The ninth nonready decision must fail without a ninth gesture")
    require(fragment(main, "if direction == .up { list.swipeUp(velocity: .slow) } "
                     "else { list.swipeDown(velocity: .slow) }"),
            "Only exact-list slow directional gestures are permitted")
    require(fragment(main, "let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in "
                     "if isHome { return self.l08HomeGeometry(app, identifier: identifier) != nil } "
                     "return target.exists && target.isEnabled && target.isHittable }, object: nil)"),
            "Home readiness must gate hittability on full geometry, not the offscreen generic predicate")
    require(fragment(main, 'let isHome = homeAliases.contains { identifier == "l08-home-" + $0 }'),
            "Home specialization must use exact aliases")
    require(fragment(main, "guard let current, l08HomeGeometryNear(settled, current), "
                     "dsc01CatalogNear(target.frame, current[0]), target.isEnabled, target.isHittable else {"),
            "Final geometry and the actual activation element must agree")
    # Swift's XCTWaiter.wait(for: ...) argument label is not a for-loop.
    # An actual for-loop cannot have ':' immediately after its keyword.
    loops = [token for index, token in enumerate(main_tokens)
             if token in {"for", "while", "repeat"} and
             not (token == "for" and main_tokens[index + 1:index + 2] == [":"])]
    require(loops == ["for"], "No hidden activation retry loop")
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
        'guard target.exists, target.isEnabled, '
        'list.exists, list.isEnabled, overlay.exists else { return nil }',
        'let frames = [target.frame, list.frame, app.frame, overlay.frame]',
        'guard frames.allSatisfy(dsc01CatalogFinite), frames[2].contains(frames[1]), '
        'frames[2].contains(frames[3]), frames[1].contains(frames[0]), '
        '!frames[0].intersects(frames[3].insetBy(dx: -8, dy: -8)) else { return nil }',
        'guard target.isHittable, list.isHittable else { return nil }',
    ):
        require(fragment(geometry, predicate), "Unique, finite, full visible and uncovered geometry is required")
    require(geometry.index("let frames = ") < geometry.index("guard frames.allSatisfy") <
            geometry.index("target.isHittable") < geometry.index("return frames"),
            "Complete geometry must precede every readiness hittability query")
    observation = function(source, "l08PublicControlObservation")
    require(fragment(observation, "let frame = node.frame "
                     "let hittabilityQueried = dsc01CatalogFinite(frame) && app.frame.contains(frame) "
                     'row["hittability_queried"] = hittabilityQueried '
                     'if hittabilityQueried { row["hittable"] = node.isHittable }'),
            "Offscreen diagnostic hit points must remain unqueried, not fabricated false")
    require(tokens(observation).count("isHittable") == 1,
            "No diagnostic may query an offscreen hit point through another path")
    require(tokens(helpers["l08HomeScrollDirection"]) == tokens(DIRECTION_BODY),
            "The geometry-only scroll policy changed: review every predicate against the replay oracle")
    assert_scroll_settling_contract(helpers["l08SettleHomeScrollDecision"])
    require(tokens(helpers["l08HomeGeometryNear"]) == tokens(
        "first.count == 4 && second.count == 4 && "
        "zip(first, second).allSatisfy { dsc01CatalogNear($0.0, $0.1) }"),
        "All four measured public frames must remain near")

    stable = helpers["l08WaitStableHomeGeometry"]
    require(not set(tokens(stable)).intersection({"tap", "swipeUp", "swipeDown", "press", "activate", "while", "repeat"}),
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
            not set(tokens(after)).intersection({"tap", "swipeUp", "swipeDown", "while", "repeat"}),
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
    require(tokens(joined).count("swipeUp") == 1 and tokens(joined).count("swipeDown") == 1,
            "Only the two reviewed main scrolling sites may gesture")



A23_LOG_SHA256 = '80d4223592c3bb95690fe088adcaccdfddd13a54abd022883574a9b243bb77c9'

# Immutable source-only baseline: canonical fixture at08d1adfb, SHA256
# 5d81dfb0b5accc127a4f5510a23d610ba73efd2fc8b19a0ff12395912e2d0c59.
# These raw parameter/body slices include comments and whitespace; they never
# reread the future canonical fixture as their own expected result.
FROZEN_HELPER_SLICE_SHA256 = {
    "l08HomeGeometryNear": "218095d62d6f0d78ff892ccc782f95c63d8027d070421069f33d8bd3a1630252",
    "l08WaitStableHomeGeometry": "219c73596391ff7c270a2028d7fca05c8443fa83f3342d8bb1ccc26a156a4443",
    "l08RequireHomeZeroBeforeTap": "a56a66792ca42f52483e6b5c82a24b5292b47ae9af61596940444b50effa1105",
    "l08WaitHomeOneAfterTap": "234dbf02625cecd654381c6ef0fb3a68a99b1b9b8cb1a6028fbed76f529547e5",
    "l08ReadHomeCounters": "9c49648ab4c3ecaf1c6f55c8dc957f20e02e5b9ec78d34eb8573c708c86fa8ec",
    "l08HomeActivationDiagnostic": "c09ceefb51f2ee1bf44ab438b5732fc0da34328ac6203b53bed0823e88e73b44",
    "l08WaitRecovery": "3048d3f303d9c8da57f50552a2ee97de7a3e40d86ea346782e07ffb640b4ccd3"
}


DIRECTION_BODY = """
    guard frames.count == 4, frames.allSatisfy(dsc01CatalogFinite),
          frames[2].contains(frames[1]), frames[2].contains(frames[3]),
          frames[0].minX >= frames[1].minX, frames[0].maxX <= frames[1].maxX,
          frames[0].height <= frames[1].height else { return nil }
    let target = frames[0], list = frames[1], blocked = frames[3].insetBy(dx: -8, dy: -8)
    if list.contains(target) && !target.intersects(blocked) { return .ready }
    if target.minY < list.minY { return .down }
    if target.maxY > list.maxY { return .up }
    let below = list.maxY - max(list.minY, blocked.maxY)
    let above = min(list.maxY, blocked.minY) - list.minY
    if below >= target.height && below >= above { return .down }
    if above >= target.height { return .up }
    return nil
"""


def assert_scroll_settling_contract(scroll):
    for expected in (
        'let targets = app.descendants(matching: .any).matching(identifier: identifier)',
        'let lists = app.descendants(matching: .any).matching(identifier: "l08-home-list")',
        'let overlays = app.descendants(matching: .any).matching(identifier: "parlor-dsc01-overlay")',
        'for sample in 0..<24 {',
        'guard (0...1).contains(targetCount), (0...1).contains(listCount), (0...1).contains(overlayCount) else {',
        'if targetCount == 1 { targetSeen = true }',
        'if listCount == 1, overlayCount == 1, app.state == .runningForeground, '
        '!app.alerts.firstMatch.exists, !app.keyboards.firstMatch.exists, '
        'lists.firstMatch.exists, lists.firstMatch.isEnabled, overlays.firstMatch.exists {',
        'if targetCount == 1, targets.firstMatch.exists, targets.firstMatch.isEnabled { '
        'frames = [targets.firstMatch.frame, list.frame, app.frame, overlay.frame] '
        'direction = l08HomeScrollDirection(frames)',
        'if direction == .ready { '
        'if let visible = l08HomeGeometry(app, identifier: identifier), '
        'l08HomeGeometryNear(frames, visible) { frames = visible } else { direction = nil } }',
        '} else if targetCount == 0 && !targetSeen { '
        'frames = [list.frame, app.frame, overlay.frame] '
        'if frames.allSatisfy(dsc01CatalogFinite), frames[1].contains(frames[0]), '
        'frames[1].contains(frames[2]) { direction = .up } }',
        'if let direction { '
        'if let anchor, anchoredDirection == direction, anchor.count == frames.count, '
        'zip(anchor, frames).allSatisfy({ dsc01CatalogNear($0.0, $0.1) }) { consecutive += 1 } '
        'else { anchor = frames; anchoredDirection = direction; consecutive = 1 } '
        '} else { anchor = nil; anchoredDirection = nil; consecutive = 0 }',
        'let row: [String: Any] = ["schema_version": 1, "identifier": identifier, "attempt": attempt, '
        '"sample": sample, "target_count": targetCount, "target_seen": targetSeen, '
        '"list_count": listCount, "overlay_count": overlayCount, '
        '"direction": direction?.rawValue ?? "wait", "stable_samples": consecutive, '
        '"frames": frames.allSatisfy(dsc01CatalogFinite) ? frames.map { [$0.minX, $0.minY, $0.width, $0.height] } : [], '
        '"proves_recovery": false]',
        'guard data.count <= 2048 else { throw NSError(domain: "L08HomeScroll", code: 3) }',
        'print("PARLOR_L08_HOME_SCROLL " + String(decoding: data, as: UTF8.self))',
        'if consecutive == 6, let direction { return direction }',
        'if sample < 23 { try catalogSamplingInterval() }',
        'throw NSError(domain: "L08HomeScroll", code: 4)',
    ):
        require(fragment(scroll, expected), 'Bounded first-anchor scroll decision/source diagnostic changed: ' + expected)
    require(not set(tokens(scroll)).intersection({
        'tap', 'swipeUp', 'swipeDown', 'press', 'activate', 'while', 'repeat', 'isHittable',
        'l08TapExactPublicControl', 'label', 'value', 'debugDescription', 'screenshot',
    }), 'Settling must remain read-only, without direct hittability, activation or private state')
    require(tokens(scroll).count('for') == 1 and tokens(scroll).count('return') == 1,
            'Only the six-sample return and one bounded loop are permitted')
    require(scroll.index('if targetCount == 1 { targetSeen = true }') <
            scroll.index('else if targetCount == 0 && !targetSeen'),
            'Disappearance after any sighting cannot restart blind up-search')
    require(scroll.index('print("PARLOR_L08_HOME_SCROLL ') < scroll.index('if consecutive == 6'),
            'Every settling sample including the admitted sixth must be retained')


@dataclass(frozen=True)
class Rect:
    x: float
    y: float
    width: float
    height: float

    @property
    def max_x(self):
        return self.x + self.width

    @property
    def max_y(self):
        return self.y + self.height

    def finite(self):
        return self.width > 0 and self.height > 0 and all(
            math.isfinite(value) and abs(value) <= 16384 for value in self.fields())

    def fields(self):
        return self.x, self.y, self.width, self.height

    def contains(self, other):
        return self.x <= other.x and self.y <= other.y and self.max_x >= other.max_x and self.max_y >= other.max_y

    def intersects(self, other):
        return self.x < other.max_x and other.x < self.max_x and self.y < other.max_y and other.y < self.max_y

    def padded(self):
        return Rect(self.x - 8, self.y - 8, self.width + 16, self.height + 16)


def direction_model(frames):
    """Reference geometry only: caller binds DIRECTION_BODY, not actual Swift execution."""
    if len(frames) != 4 or not all(frame.finite() for frame in frames):
        return None
    target, viewport, app, overlay = frames
    if not (app.contains(viewport) and app.contains(overlay) and target.x >= viewport.x and
            target.max_x <= viewport.max_x and target.height <= viewport.height):
        return None
    blocked = overlay.padded()
    if viewport.contains(target) and not target.intersects(blocked):
        return 'ready'
    if target.y < viewport.y:
        return 'down'
    if target.max_y > viewport.max_y:
        return 'up'
    below = viewport.max_y - max(viewport.y, blocked.max_y)
    above = min(viewport.max_y, blocked.y) - viewport.y
    if below >= target.height and below >= above:
        return 'down'
    if above >= target.height:
        return 'up'
    return None


def near(first, second):
    return len(first) == len(second) and all(
        a.finite() and b.finite() and all(abs(x - y) <= 0.5 for x, y in zip(a.fields(), b.fields()))
        for a, b in zip(first, second))


def settle_model(samples):
    """Explicit reference-model admission only; does not fetch geometry or actuate UI."""
    anchor = None
    anchored_direction = None
    consecutive = 0
    for index, (direction, frames) in enumerate(samples[:24]):
        if direction is not None:
            if anchor is not None and anchored_direction == direction and near(anchor, frames):
                consecutive += 1
            else:
                anchor, anchored_direction, consecutive = frames, direction, 1
        else:
            anchor, anchored_direction, consecutive = None, None, 0
        if consecutive == 6:
            return direction, index
    return None


class ScrollCorrectionDraftTests(unittest.TestCase):
    def setUp(self):
        self.source = SWIFT.read_text()
        assert_fixture_contract(self.source)
        self.viewport = Rect(0, 0, 402, 769.66668701171875)
        self.overlay = Rect(68.666666666666657, 134, 265, 139)

    def frames(self, y=358.66665649414062, height=96):
        # App == viewport is a SYNTHETIC admitted envelope. A23's public marker
        # did not retain app.frame, so this does not reconstruct full readiness.
        return [Rect(24, y, 354, height), self.viewport, self.viewport, self.overlay]

    def reject_helper_mutation(self, name, old, new):
        clean = without_comments(self.source)
        original = function(clean, name)
        self.assertEqual(original.count(old), 1)
        altered = clean.replace(original, original.replace(old, new, 1), 1)
        self.assertNotEqual(altered, clean)
        with self.assertRaises(ValueError):
            assert_fixture_contract(altered)

    def test_actual_a23_trajectory_is_conditional_geometry_not_callback_proof(self):
        log = (A23 / 'xcodebuild.log').read_bytes()
        self.assertEqual(hashlib.sha256(log).hexdigest(), A23_LOG_SHA256)
        rows = [json.loads(line.split(' ', 1)[1]) for line in log.decode().splitlines()
                if line.startswith('PARLOR_L08_PUBLIC_CONTROL ') and '"l08-home-legacy"' in line]
        self.assertEqual([row['attempt'] for row in rows], list(range(8)))
        self.assertEqual([round(row['target']['frame']['y'], 2) for row in rows],
                         [911.67, 358.67, 95.67, -236.33, -236.33, -236.33, -236.33, -236.33])
        decisions = []
        for row in rows:
            rects = [Rect(**row[key]['frame']) for key in ('target', 'list', 'overlay')]
            # Synthetic containing app envelope; native full predicate NOT proved.
            decisions.append(direction_model([rects[0], rects[1], rects[1], rects[2]]))
        self.assertEqual(decisions, ['up', 'ready', 'down', 'down', 'down', 'down', 'down', 'down'])
        activations = [json.loads(line.split(' ', 1)[1]) for line in log.decode().splitlines()
                       if line.startswith('PARLOR_L08_HOME_ACTIVATION ')]
        self.assertFalse(any(row['identifier'] == 'l08-home-legacy' for row in activations))

    def test_transient_visibility_and_drift_cannot_trigger_next_gesture(self):
        transient = [('up', self.frames(911.67)), ('ready', self.frames()),
                     ('down', self.frames(95.67)), ('down', self.frames(-236.33))]
        self.assertIsNone(settle_model(transient))
        self.assertIsNone(settle_model([('ready', self.frames(358 + 0.4 * n)) for n in range(24)]))
        # Only a new six-sample stable direction can admit a counterfactual decision.
        self.assertEqual(settle_model(transient + [('down', self.frames(-236.33))] * 5), ('down', 8))
        self.assertIsNone(settle_model([('ready', self.frames())] * 5))

    def test_settling_nil_resets_and_complete_window_is_bounded(self):
        steady = ('ready', self.frames())
        self.assertIsNone(settle_model([steady] * 5 + [(None, self.frames())] + [steady] * 5))
        self.assertEqual(settle_model([steady] * 6), ('ready', 5))
        self.assertIsNone(settle_model([(None, self.frames())] * 24 + [steady] * 6))
        self.assertIsNone(settle_model([('up' if n % 2 else 'down', self.frames()) for n in range(24)]))

    def test_geometry_policy_rejects_nonfinite_overflow_and_malformed_containment(self):
        for changed in (
            Rect(24, float('nan'), 354, 96), Rect(24, float('inf'), 354, 96),
            Rect(24, -float('inf'), 354, 96), Rect(24, 16385, 354, 96),
            Rect(24, 358, 0, 96), Rect(24, 358, 354, 0), Rect(24, 358, 354, 770),
            Rect(-1, 358, 354, 96), Rect(24, 358, 403, 96),
        ):
            with self.subTest(changed=changed):
                frames = self.frames(); frames[0] = changed
                self.assertIsNone(direction_model(frames))
        for count in (0, 1, 2, 3, 5):
            self.assertIsNone(direction_model((self.frames() + self.frames())[:count]))
        for index in (1, 3):
            frames = self.frames(); frames[index] = Rect(-1, -1, 500, 900)
            self.assertIsNone(direction_model(frames))

    def test_overlay_requires_whole_card_band_and_never_chooses_tap_point(self):
        self.assertEqual(direction_model(self.frames(95.67)), 'down')
        frames = self.frames(550); frames[3] = Rect(50, 550, 300, 150)
        self.assertEqual(direction_model(frames), 'up')
        frames = self.frames(350); frames[3] = Rect(0, 0, 402, 769)
        self.assertIsNone(direction_model(frames))
        # Full containment rather than intersection admits ready.
        self.assertEqual(direction_model(self.frames(730)), 'up')
        self.assertEqual(direction_model(self.frames(-10)), 'down')

    def test_direction_policy_mutations_are_source_bound(self):
        for old, new in (
            ('frames.count == 4', 'frames.count >= 1'),
            ('frames.allSatisfy(dsc01CatalogFinite)', 'true'),
            ('frames[2].contains(frames[1])', 'true'),
            ('list.contains(target)', 'list.intersects(target)'),
            ('!target.intersects(blocked)', 'true'),
            ('if target.minY < list.minY { return .down }', 'if target.minY < list.minY { return .up }'),
            ('if target.maxY > list.maxY { return .up }', 'if target.maxY > list.maxY { return .down }'),
            ('below >= target.height && below >= above', 'below >= 0'),
            ('return nil', 'return .up'),
        ):
            # return nil occurs twice; mutate its uniquely delimited final site.
            if old == 'return nil':
                old, new = 'if above >= target.height { return .up }\n        return nil', 'return .up'
            with self.subTest(old=old):
                self.reject_helper_mutation('l08HomeScrollDirection', old, new)

    def test_scroll_bounds_and_single_activation_retries_remain_rejected(self):
        for old, new in (
            ('for attempt in 0...8 {', 'for attempt in 0...9 {'),
            ('guard attempt < 8 else {', 'guard attempt < 9 else {'),
            ('if direction == .ready { break }', 'if direction == .ready { continue }'),
            ('list.swipeDown(velocity: .slow)', 'list.swipeUp(velocity: .slow)'),
            ('list.swipeUp(velocity: .slow)', 'list.swipeUp()'),
            ('target.tap()', 'for ignored in 0..<2 { target.tap() }'),
            ('target.tap()', 'target.tap(); target.tap()'),
        ):
            with self.subTest(old=old):
                self.reject_helper_mutation('l08TapExactPublicControl', old, new)

    def test_settling_cannot_guess_ambiguous_invalid_or_disappeared_target(self):
        for old, new in (
            ('(0...1).contains(targetCount)', '(0...2).contains(targetCount)'),
            ('if targetCount == 1 { targetSeen = true }', 'if targetCount == 1 { targetSeen = false }'),
            ('targetCount == 0 && !targetSeen', 'targetCount == 0'),
            ('else { direction = nil }', 'else { direction = .up }'),
            ('if consecutive == 6, let direction', 'if consecutive == 1, let direction'),
            ('anchor.count == frames.count', 'true'),
            ('consecutive += 1', 'consecutive += 1; anchor = frames'),
            ('else { anchor = nil; anchoredDirection = nil; consecutive = 0 }', 'else { consecutive += 1 }'),
            ('for sample in 0..<24 {', 'for sample in 0..<25 {'),
            ('if sample < 23 { try catalogSamplingInterval() }', 'if sample < 23 { }'),
            ('"proves_recovery": false', '"proves_recovery": true'),
            ('guard data.count <= 2048', 'guard data.count <= 999999'),
        ):
            with self.subTest(old=old):
                self.reject_helper_mutation('l08SettleHomeScrollDecision', old, new)

    def test_every_home_hittability_site_follows_geometry_without_fabricated_false(self):
        original = function(without_comments(self.source), 'l08HomeGeometry')
        line = '        guard target.isHittable, list.isHittable else { return nil }\n'
        self.assertEqual(original.count(line), 1)
        altered = original.replace(line, '').replace('        let frames = ', line + '        let frames = ', 1)
        with self.assertRaisesRegex(ValueError, 'geometry must precede'):
            assert_fixture_contract(without_comments(self.source).replace(original, altered, 1))
        self.reject_helper_mutation('l08TapExactPublicControl',
            'if isHome { return self.l08HomeGeometry(app, identifier: identifier) != nil }', '')
        self.reject_helper_mutation('l08PublicControlObservation',
            'if hittabilityQueried { row["hittable"] = node.isHittable }',
            'row["hittable"] = node.isHittable')
        self.reject_helper_mutation('l08PublicControlObservation',
            'if hittabilityQueried { row["hittable"] = node.isHittable }',
            'row["hittable"] = false')

    def test_final_six_anchor_zero_one_recovery_parameter_body_bytes_match_frozen_baseline(self):
        for name, expected in FROZEN_HELPER_SLICE_SHA256.items():
            with self.subTest(name=name):
                # Structural/name checks are enforced independently by the
                # baseline contract; the literal digest is not a live reread.
                function(self.source, name)
                header = 'func ' + name + '('
                self.assertEqual(self.source.count(header), 1)
                raw_slice = self.source.split(header, 1)[1].split('\n    }\n', 1)[0]
                self.assertEqual(hashlib.sha256(raw_slice.encode()).hexdigest(), expected)

    def test_only_reviewed_main_scrolling_sites_allow_downward_gestures(self):
        clean = without_comments(self.source)
        for name, reason in (
            ('l08WaitStableHomeGeometry', 'Readiness polling cannot interact with the app'),
            ('l08WaitHomeOneAfterTap', 'Only fresh observer reads may repeat after activation'),
        ):
            with self.subTest(helper=name):
                original = function(clean, name)
                anchor = 'for attempt in 0..<24 {'
                self.assertEqual(original.count(anchor), 1)
                mutated = original.replace(anchor, anchor + ' app.swipeDown(velocity: .slow)', 1)
                altered = clean.replace(original, mutated, 1)
                self.assertNotEqual(altered, clean)
                with self.assertRaisesRegex(ValueError, reason):
                    assert_fixture_contract(altered)
        # Existing literal ordering would miss app.swipeDown outside the two
        # exact list gesture sites. These witnesses must hit the global census.
        for name, anchor in (
            ('l08TapExactPublicControl', 'try l08RequireHomeZeroBeforeTap(app, identifier: identifier)'),
            ('l08HomeGeometry', 'return frames'),
            ('l08PublicControlObservation', 'return row'),
        ):
            with self.subTest(helper=name):
                original = function(clean, name)
                self.assertEqual(original.count(anchor), 1)
                replacement = (anchor + '; app.swipeDown(velocity: .slow)' if name == 'l08TapExactPublicControl'
                               else 'app.swipeDown(velocity: .slow); ' + anchor)
                altered = clean.replace(original, original.replace(anchor, replacement, 1), 1)
                self.assertNotEqual(altered, clean)
                with self.assertRaisesRegex(ValueError, 'Only the two reviewed main scrolling sites may gesture'):
                    assert_fixture_contract(altered)

class SingleTapFixtureContractTests(unittest.TestCase):
    def setUp(self):
        self.source = SWIFT.read_text()
        # A broken baseline must not make every negative mutation appear valid.
        assert_fixture_contract(self.source)

    def test_actual_swift_matches_independently_reviewed_contract(self):
        assert_fixture_contract(self.source)

    def test_swift_for_argument_label_is_not_an_activation_loop(self):
        main = function(self.source, "l08TapExactPublicControl")
        self.assertEqual(tokens(main).count("for"), 2)
        self.assertTrue(fragment(main, "XCTWaiter.wait(for: [ready], timeout: 15)"))
        for loop in ("for ignored in 0..<2 { target.tap() }",
                     "while true { target.tap() }", "repeat { target.tap() } while true"):
            with self.subTest(loop=loop):
                altered = self.source.replace("target.tap()", loop, 1)
                with self.assertRaisesRegex(ValueError, "No hidden activation retry loop"):
                    assert_fixture_contract(altered)

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
        clean = without_comments(self.source)
        for helper, changes in {
            "l08HomeGeometry": [
                ("guard targets.count == 1, lists.count == 1, overlays.count == 1", "guard targets.count >= 1, lists.count >= 1, overlays.count >= 1"),
                ("guard frames.allSatisfy(dsc01CatalogFinite)", "guard true"),
                ("frames[1].contains(frames[0])", "frames[1].intersects(frames[0])"),
                ("!frames[0].intersects(frames[3].insetBy(dx: -8, dy: -8))", "true"),
            ],
            "l08HomeGeometryNear": [("first.count == 4 && second.count == 4", "first.count >= 1 && second.count >= 1")],
            "l08WaitStableHomeGeometry": [
                ("if consecutive == 6, let frames", "if consecutive == 1, let frames"),
                ("if let anchor, l08HomeGeometryNear(anchor, frames) { consecutive += 1 }", "if let previous = anchor, l08HomeGeometryNear(previous, frames) { consecutive += 1; anchor = frames }"),
                ("} else { anchor = nil; consecutive = 0 }", "} else { consecutive += 1 }"),
                ("if attempt < 23 { try catalogSamplingInterval() }", "if attempt < 23 { /* no actual wait */ }"),
            ],
        }.items():
            original = function(clean, helper)
            for old, new in changes:
                with self.subTest(helper=helper, old=old):
                    self.assertEqual(original.count(old), 1)
                    altered = clean.replace(original, original.replace(old, new, 1), 1)
                    with self.assertRaises(ValueError):
                        assert_fixture_contract(altered)

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
