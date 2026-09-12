"""Source/copy contract tests only; root executes in the shared lane, never native proof."""
import ast
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest

import copied_sources as copies
import l08_copy as strict
import l08_functional_copy as companion
import l08_functional_receipts as functional_receipts

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CANONICAL = ROOT / 'scripts/verification/ios-readiness'
CLONE_MANIFEST = HERE.parent / 'reviews/l08-functional-companion-clone-manifest-01.json'
CLONE_MANIFEST_SHA256 = '3036231a1bcfc9d567b8381ee33d89cd381377bea2b10790ddde0f90baf682fb'
SITES = ('save', 'load', 'dual-before-legacy-seed', 'dual-after-tag-damage', 'legacy-load-dual', 'retained-dual')
MAFIA_STAGES = (
    'continue-mafia-attached', 'continue-mafia-session', 'continue-mafia-canonical',
    'continue-mafia-initial-identity-value', 'continue-mafia-night-outcome', 'continue-mafia-ballot-eligibility',
    'continue-mafia-terminal-phase-winner', 'continue-mafia-terminal-doctor-cleanup',
    'continue-mafia-action-await', 'continue-mafia-action-receipt', 'continue-mafia-action-validity',
    'continue-mafia-night-phase', 'continue-mafia-night-unsubmitted', 'continue-mafia-night-choice')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def section(text, first, last=None):
    require(text.count(first) == 1 and (last is None or text.count(last) == 1), 'Ambiguous source section')
    start = text.index(first)
    end = len(text) if last is None else text.index(last, start)
    return text[start:end]


def strip_mafia_continuation_stages(candidate):
    for stage in MAFIA_STAGES:
        lines = re.findall(r'(?m)^ +onStage\("' + stage + r'"\)\n', candidate)
        require(len(lines) == 1, 'Missing/duplicated closed Mafia stage')
        candidate = strict.once(candidate, lines[0], '')
    for before, after in (
        ('suspend fun continueActualResumedController(onStage: (String) -> Unit = {})',
         'suspend fun continueActualResumedController()'),
        ('val driver = Driver(session, onStage)', 'val driver = Driver(session)'),
        ('private class Driver(val session: SessionController<MafiaState, MafiaAction, MafiaEvent>,\n'
         '                         val onStage: (String) -> Unit = {})',
         'private class Driver(val session: SessionController<MafiaState, MafiaAction, MafiaEvent>)'),
    ):
        candidate = strict.once(candidate, before, after)
    return candidate


def strip_functional_continuation_stages(candidate):
    candidate = strict.once(candidate, 'stage = "continue-home-counters"', 'stage = "actual-home-controller"')
    for stage in ('continue-whodunit-attach-wait', 'continue-whodunit-controller', 'continue-mafia-attach-wait'):
        candidate = strict.once(candidate, '                    stage = "' + stage + '"\n', '')
    return strict.once(candidate, 'L08MafiaSnapshots.continueActualResumedController { stage = it }',
                       'L08MafiaSnapshots.continueActualResumedController()')


def assert_continuation_failure_payload(probe):
    # Metadata comparisons legitimately name the fixed directory/file target.
    # Constrain the actual failure payload, including values, not words elsewhere.
    failure = section(probe, '    private fun failed(', '\n}\n\ninternal fun requireContext(')
    require(failure.strip() == '''private fun failed(token: String, boot: Int, action: String, reason: String) = buildJsonObject {
        put("schema_version", 1); put("kind", "l08-storage-functional"); put("functional_status", "FAIL")
        put("run_token", token); put("boot_ordinal", boot); put("command_ordinal", commandIndex + 1)
        put("action", action); put("stage", stage); put("reason", reason)
        put("complete_comparisons", JsonArray(completeComparisons.toList()))
        put("physical_or_store_evidence", false)
    }.toString()''', 'Continuation failure payload must remain exact closed metadata')


def assert_public_control_contract(text):
    tap = section(text, '    @MainActor private func l08TapExactPublicControl(',
                  '    @MainActor private func l08PublicControlObservation(')
    settling = section(text, '    @MainActor private func l08SettleHomeScrollDecision(',
                       '    @MainActor private func l08HomeGeometry(')
    geometry = section(text, '    @MainActor private func l08HomeGeometry(',
                       '    private func l08HomeGeometryNear(')
    require(tap.count('target.tap()') == 1 and tap.count('.tap()') == 1,
            'Exactly one public activation is required')
    require(tap.count('list.swipeUp(velocity: .slow)') == tap.count('.swipeUp(') == 1 and
            tap.count('list.swipeDown(velocity: .slow)') == tap.count('.swipeDown(') == 1 and
            'for attempt in 0...8 {' in tap and 'guard attempt < 8 else {' in tap and
            'if direction == .ready { break }' in tap and
            'if direction == .up { list.swipeUp(velocity: .slow) }' in tap and
            'else { list.swipeDown(velocity: .slow) }' in tap and
            'throw NSError(domain: "L08HomeScroll", code: 1)' in tap,
            'The only scrolls must be bounded slow gestures on the tagged list')
    require('let isHome = homeAliases.contains { identifier == "l08-home-" + $0 }' in tap and
            'if isHome {' in tap and 'matching(identifier: "l08-home-list")' in tap,
            'Scroll eligibility and destination must use exact identities')
    require(tap.count('XCTAssertEqual(lists.count, 1)') == 1 and
            tap.count('XCTAssertEqual(query.count, 1)') == 1 and
            all(settling.count('(0...1).contains(' + count + ')') == 1
                for count in ('targetCount', 'listCount', 'overlayCount')),
            'Ambiguous list or target identities must fail')
    flat_geometry = ' '.join(geometry.split())
    require(tap.count('target.exists && target.isEnabled && target.isHittable') == 1 and
            'if isHome { return self.l08HomeGeometry(app, identifier: identifier) != nil }' in tap and
            'XCTAssertTrue(list.exists && list.isEnabled && list.isHittable)' in tap and
            all(predicate in flat_geometry for predicate in (
                'guard targets.count == 1, lists.count == 1, overlays.count == 1, '
                'app.state == .runningForeground, !app.alerts.firstMatch.exists, '
                '!app.keyboards.firstMatch.exists else { return nil }',
                'guard target.exists, target.isEnabled, list.exists, list.isEnabled, '
                'overlay.exists else { return nil }',
                'guard frames.allSatisfy(dsc01CatalogFinite), frames[2].contains(frames[1]), '
                'frames[2].contains(frames[3]), frames[1].contains(frames[0]), '
                '!frames[0].intersects(frames[3].insetBy(dx: -8, dy: -8)) else { return nil }',
                'guard target.isHittable, list.isHittable else { return nil }')),
            'No activation or gesture may bypass required state checks')
    require(geometry.index('guard frames.allSatisfy') < geometry.index('target.isHittable'),
            'Full geometry must precede Home hittability')
    flat_settling = ' '.join(settling.split())
    require(all(predicate in flat_settling for predicate in (
                'for sample in 0..<24 {',
                'direction = l08HomeScrollDirection(frames)',
                'if let visible = l08HomeGeometry(app, identifier: identifier), '
                'l08HomeGeometryNear(frames, visible) { frames = visible } else { direction = nil }',
                'if targetCount == 1 { targetSeen = true }',
                'else if targetCount == 0 && !targetSeen {',
                'if let anchor, anchoredDirection == direction, anchor.count == frames.count, '
                'zip(anchor, frames).allSatisfy({ dsc01CatalogNear($0.0, $0.1) }) { consecutive += 1 }',
                'else { anchor = frames; anchoredDirection = direction; consecutive = 1 }',
                'else { anchor = nil; anchoredDirection = nil; consecutive = 0 }',
                'if consecutive == 6, let direction { return direction }',
                'if sample < 23 { try catalogSamplingInterval() }',
                'throw NSError(domain: "L08HomeScroll", code: 4)')) and
            not any(gesture in settling for gesture in ('.tap(', '.swipeUp(', '.swipeDown(')),
            'Scroll decisions must remain bounded settled read-only observations')
    stages = ('try l08SettleHomeScrollDecision(', 'if direction == .ready { break }',
              'guard attempt < 8 else {', 'list.swipeUp(velocity: .slow)', 'list.swipeDown(velocity: .slow)',
              'try l08PublicControlObservation(app, identifier: identifier, attempt: 8)',
              'XCTAssertEqual(result, .completed)', 'try l08RequireHomeZeroBeforeTap(',
              'try l08WaitStableHomeGeometry(', 'guard let current', 'target.tap()',
              'try l08WaitHomeOneAfterTap(')
    require(all(tap.count(stage) == 1 for stage in stages),
            'Observe failures before asserting; tap only after readiness succeeds')
    positions = [tap.index(stage) for stage in stages]
    require(positions == sorted(positions),
            'Observe failures before asserting; tap only after readiness succeeds')
    require(not any(forbidden in tap for forbidden in
                    ('coordinate(', 'onResume(', 'homeTapped(', 'performClick(', 'forceTap(', 'try?', 'catch {')),
            'No coordinates, callbacks or swallowed failures')
    observation = section(text, '    @MainActor private func l08PublicControlObservation(',
                          '    @MainActor private func l08WaitRecovery(')
    require('guard allowed.contains(identifier) else { return }' in observation and
            'XCTAssertTrue((0...8).contains(attempt))' in observation and
            'XCTAssertLessThanOrEqual(data.count, 2048)' in observation,
            'Diagnostic output is bounded to a closed public alias list')
    require('"proves_recovery": false' in observation and
            not any(field in observation for field in ('.label', '.value', '.debugDescription', '.screenshot()')),
            'Presence/geometry cannot claim recovery or disclose UI content')
    require('let hittabilityQueried = dsc01CatalogFinite(frame) && app.frame.contains(frame)' in observation and
            'row["hittability_queried"] = hittabilityQueried' in observation and
            'if hittabilityQueried { row["hittable"] = node.isHittable }' in observation and
            observation.count('node.isHittable') == 1 and
            observation.index('let frame = node.frame') < observation.index('node.isHittable'),
            'Offscreen diagnostic hittability must remain unqueried rather than fabricated')


def assert_probe_contract(candidate, original):
    """Compare complete executable regions, not just whether check names occur somewhere."""
    candidate = strip_functional_continuation_stages(candidate)
    for site in SITES:
        require(len(re.findall(r'protectedMetadata\((?:alias|"dual"), "' + site + r'"\)', candidate)) == 1,
                'Missing or duplicate metadata checkpoint: ' + site)
    restored = re.sub(r'protectedMetadata\((alias|"dual"), "(?:' + '|'.join(SITES) + r')"\)',
                      r'protectedMetadata(\1)', candidate)
    restored = restored.replace('"owned_metadata_and_header"', '"protected_metadata"')
    restored = restored.replace('recordProtectionComparison', 'recordProtectionDiagnostic')
    for first, last in (
        ('    private val aliases', '    fun command('),
        ('    private suspend fun execute(', '    private fun recordProtectionDiagnostic('),
        ('internal fun requireContext(', '/** Exact app-container/token paths only'),
        ('/** Exact app-container/token paths only', '    fun protectedMetadata('),
        ('    private fun excluded(', None),
    ):
        require(section(restored, first, last) == section(original, first, last),
                'A nonmetadata functional/ownership boundary changed: ' + first)
    cancellation = section(candidate, '            } catch (cancelled:', '    private suspend fun execute(')
    require(cancellation.replace('completeComparisons.clear()', 'protectionDiagnostic = null') ==
            section(original, '            } catch (cancelled:', '    private suspend fun execute('),
            'Cancellation, payload clearing or controller/scope cleanup changed')
    expected = section(original, '    fun protectedMetadata(', '    private class ProtectionRead(')
    expected = strict.once(expected, 'fun protectedMetadata(alias: String)', 'fun protectedMetadata(alias: String, site: String)')
    for target, args in (('directory', 'directory'), ('file', 'directory, file')):
        expected = strict.once(expected,
            '        if (!' + target + '.complete) protectionFailure("' + target + '", path, key, protection, ' + args + ')\n'
            '        check(' + target + '.complete)',
            '        recordComparison(site, alias, "' + target + '", ' + target + ')')
    require(section(candidate, '    fun protectedMetadata(', '    private class ProtectionRead(') == expected,
            'Path/existence/backup/header or one of the actual Complete comparisons changed')
    require(section(candidate, '    private class ProtectionRead(', '    private fun recordComparison(') ==
            section(original, '    private class ProtectionRead(', '    private fun protectionFailure('),
            'The actual getter, required equality or exact native values changed')
    require(section(candidate, '    private fun metadataRead(', '    private fun excluded(') ==
            section(original, '    private fun metadataRead(', '    private fun unknownMetadata('),
            'Native primitive metadata is not preserved exactly')
    require(section(candidate, '    private fun recordComparison(', '    private fun metadataRead(') == '''    private fun recordComparison(site: String, alias: String, target: String, reading: ProtectionRead) {
        recordProtectionComparison(buildJsonObject {
            put("site", site); put("alias", alias); put("target", target)
            put("attributes", reading.observation)
            put("comparison", if (reading.complete) "PASS" else "FAIL")
        })
    }

''', 'A comparison must record the actual equality, never repair or relabel it')
    require(section(candidate, '    private fun recordProtectionComparison(', '    private fun checks(') == '''    private fun recordProtectionComparison(value: JsonObject) {
        check(completeComparisons.size < 4)
        check(stage == "metadata-directory-protection" || stage == "metadata-file-protection")
        completeComparisons.add(buildJsonObject {
            put("ordinal", completeComparisons.size + 1)
            value.forEach { (key, item) -> put(key, item) }
        }) // Closed metadata only, including an unsuccessful required Complete comparison.
    }

''', 'Comparison recording must remain a bounded command-local append')
    require(candidate.count('completeComparisons.clear()') == 2 and
            candidate.count('JsonArray(completeComparisons.toList())') == 2,
            'Commands and failures must retain their own comparison prefix without cross-command reuse')


class L08FunctionalCopyTests(unittest.TestCase):
    def test_home_list_tag_preserves_every_other_production_character(self):
        original = (ROOT / strict.HOME).read_text()
        expected = strict.transform(strict.HOME, original)
        with tempfile.TemporaryDirectory(prefix='parlor-functional-scroll-copy-') as raw:
            owned = Path(raw).resolve()
            for path in copies.MODIFIED_KOTLIN:
                target = owned / path; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / path).read_bytes())
            copies.instrument_kotlin(owned)
            home = (owned / strict.HOME).read_text()
            self.assertEqual(home.count('.testTag("l08-home-list")'), 1)
            self.assertEqual(home.replace('.testTag("l08-home-list")', ''), expected)
        self.assertFalse(owned.exists())
        self.assertEqual((ROOT / strict.HOME).read_text(), original)

    def test_home_scrolling_and_diagnostics_preserve_strict_public_activation(self):
        assert_public_control_contract((HERE / 'L08StorageFunctionalUITests.swift.in').read_text())

    def test_scroll_contract_rejects_bypass_ambiguity_unbounded_or_private_diagnostics(self):
        text = (HERE / 'L08StorageFunctionalUITests.swift.in').read_text()
        # A stale/broken baseline must not make unrelated negative mutations pass.
        assert_public_control_contract(text)
        regions = {
            'tap': ('    @MainActor private func l08TapExactPublicControl(',
                    '    @MainActor private func l08PublicControlObservation('),
            'settling': ('    @MainActor private func l08SettleHomeScrollDecision(',
                         '    @MainActor private func l08HomeGeometry('),
            'observation': ('    @MainActor private func l08PublicControlObservation(',
                            '    @MainActor private func l08WaitRecovery('),
        }
        activation = 'Exactly one public activation is required'
        scroll = 'The only scrolls must be bounded slow gestures on the tagged list'
        ambiguity = 'Ambiguous list or target identities must fail'
        state = 'No activation or gesture may bypass required state checks'
        settled = 'Scroll decisions must remain bounded settled read-only observations'
        privacy = 'Presence/geometry cannot claim recovery or disclose UI content'
        mutations = [
            # Original14 semantic witnesses, remapped only where the reviewed UI changed.
            ('tap', 'target.tap() // No alternate selector', 'target.tap(); target.tap() // No alternate selector', activation),
            ('tap', 'for attempt in 0...8 {', 'for attempt in 0...100 {', scroll),
            ('tap', 'list.swipeUp(velocity: .slow)', 'app.swipeUp(velocity: .slow)', scroll),
            ('tap', 'matching(identifier: "l08-home-list")', 'matching(identifier: "other-list")',
             'Scroll eligibility and destination must use exact identities'),
            ('tap', 'XCTAssertEqual(lists.count, 1)', 'XCTAssertGreaterThan(lists.count, 0)', ambiguity),
            ('tap', 'XCTAssertEqual(query.count, 1)', 'XCTAssertGreaterThan(query.count, 0)', ambiguity),
            ('settling', '(0...1).contains(targetCount)', '(0...2).contains(targetCount)', ambiguity),
            ('tap', 'target.exists && target.isEnabled && target.isHittable', 'target.exists', state),
            ('tap', 'XCTAssertTrue(list.exists && list.isEnabled && list.isHittable)', 'XCTAssertTrue(true)', state),
            ('tap', 'XCTAssertEqual(result, .completed)', 'XCTAssertTrue(true)',
             'Observe failures before asserting; tap only after readiness succeeds'),
            ('tap', 'target.tap() // No alternate selector', 'onResume(); target.tap() // No alternate selector',
             'No coordinates, callbacks or swallowed failures'),
            ('observation', '"proves_recovery": false', '"proves_recovery": true', privacy),
            ('observation', 'XCTAssertLessThanOrEqual(data.count, 2048)', 'XCTAssertTrue(true)',
             'Diagnostic output is bounded to a closed public alias list'),
            ('observation', 'row["exists"] = node.exists', 'row["exists"] = node.label', privacy),
            # Current bidirectional/pre-scroll guards also need actual negative witnesses.
            ('tap', 'list.swipeDown(velocity: .slow)', 'app.swipeDown(velocity: .slow)', scroll),
            ('tap', 'guard attempt < 8 else {', 'guard attempt < 9 else {', scroll),
            ('tap', 'if direction == .ready { break }', 'if direction == .ready { continue }', scroll),
            ('settling', 'for sample in 0..<24 {', 'for sample in 0..<25 {', settled),
            ('settling', 'if consecutive == 6, let direction', 'if consecutive == 1, let direction', settled),
            ('settling', 'targetCount == 0 && !targetSeen', 'targetCount == 0', settled),
            ('settling', 'else { direction = nil }', 'else { direction = .up }', settled),
            ('settling', 'consecutive += 1', 'consecutive += 1; anchor = frames', settled),
            ('observation', 'if hittabilityQueried { row["hittable"] = node.isHittable }',
             'row["hittable"] = node.isHittable',
             'Offscreen diagnostic hittability must remain unqueried rather than fabricated'),
            ('tap', 'try l08RequireHomeZeroBeforeTap(app, identifier: identifier)',
             'try l08RequireHomeZeroBeforeTap(app, identifier: identifier); app.swipeDown(velocity: .slow)', scroll),
        ]
        for region, before, after, reason in mutations:
            original = section(text, *regions[region])
            with self.subTest(region=region, mutation=before):
                self.assertEqual(original.count(before), 1)
                changed = original.replace(before, after, 1)
                self.assertNotEqual(changed, original)
                self.assertEqual(text.count(original), 1)
                altered = text.replace(original, changed, 1)
                self.assertNotEqual(altered, text)
                with self.assertRaisesRegex(RuntimeError, '^' + re.escape(reason) + '$'):
                    assert_public_control_contract(altered)

    def test_all_original_controls_and_exact_clone_allowlist_are_preserved(self):
        self.assertEqual(sha(CLONE_MANIFEST), CLONE_MANIFEST_SHA256)
        manifest = json.loads(CLONE_MANIFEST.read_text())
        self.assertEqual(len(manifest['files']), 65)
        changed = set(manifest['permitted_changed_clones'])
        self.assertEqual(changed, {'copied_sources.py', 'run_ios_readiness.py'})
        allowed = {row['path'] for row in manifest['files']} | set(manifest['permitted_new_files'])
        self.assertEqual(len(allowed), 75)
        self.assertEqual({path.name for path in HERE.iterdir()}, allowed)
        self.assertTrue(all(path.is_file() and not path.is_symlink() for path in HERE.iterdir()))
        for row in manifest['files']:
            with self.subTest(path=row['path']):
                self.assertEqual(sha(CANONICAL / row['path']), row['original_sha256'])
                if row['path'] == 'L08MafiaSnapshots.kt.in':
                    self.assertEqual(strip_mafia_continuation_stages((HERE / row['path']).read_text()),
                                     (CANONICAL / row['path']).read_text())
                elif row['path'] not in changed:
                    self.assertEqual(sha(HERE / row['path']), row['original_sha256'])
        original = (CANONICAL / 'copied_sources.py').read_text()
        self.assertEqual((HERE / 'copied_sources.py').read_text(), strict.once(original,
            'from l08_copy import L08_ADDITIONS, L08_ADDITIONAL_MODIFIED, instrument_l08_kotlin',
            'from l08_functional_copy import L08_ADDITIONS, L08_ADDITIONAL_MODIFIED, instrument_l08_kotlin'))

    def test_only_the_new_probe_registration_replaces_the_strict_probe_in_owned_copy(self):
        expected = dict(strict.L08_ADDITIONS)
        del expected[companion.OLD_PROBE]
        expected[companion.NEW_PROBE] = 'L08StorageFunctionalProbe.kt.in'
        self.assertEqual(companion.L08_ADDITIONS, expected)
        self.assertEqual(companion.L08_ADDITIONAL_MODIFIED, strict.L08_ADDITIONAL_MODIFIED)
        self.assertEqual(len(companion.L08_ADDITIONS), 9)
        self.assertNotIn(companion.OLD_PROBE, copies.ADDITIONS)
        self.assertIn(companion.NEW_PROBE, copies.ADDITIONS)
        for path in copies.ADDITIONS:
            self.assertFalse((ROOT / path).exists(), 'Copy-only seam must not enter a shipping source tree')

    def test_current_source_transforms_once_with_single_bridge_and_shared_context(self):
        before = {path: sha(ROOT / path) for path in copies.MODIFIED_KOTLIN}
        with tempfile.TemporaryDirectory(prefix='parlor-functional-copy-') as raw:
            owned = Path(raw).resolve()
            for path in copies.MODIFIED_KOTLIN:
                target = owned / path; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / path).read_bytes())
            copies.instrument_kotlin(owned)
            self.assertTrue((owned / companion.NEW_PROBE).is_file())
            self.assertFalse((owned / companion.OLD_PROBE).exists())
            main = (owned / strict.MAIN).read_text()
            self.assertEqual(main.count('fun L08StorageFunctionalCommand('), 1)
            self.assertEqual(main.count('L08StorageFunctionalProbe.command(action, onJson)'), 1)
            self.assertNotIn('fun L08StorageCommand(', main)
            self.assertIn('L08HostProbe.command(action, onJson)', main)
            self.assertEqual(sum((owned / path).read_text().count('fun requireContext(') for path in copies.ADDITIONS), 1)
            self.assertEqual((owned / companion.NEW_PROBE).read_bytes(), (HERE / 'L08StorageFunctionalProbe.kt.in').read_bytes())
            for path, template in companion.L08_ADDITIONS.items():
                self.assertEqual((owned / path).read_bytes(), (HERE / template).read_bytes())
            with self.assertRaises(RuntimeError): companion.instrument_l08_kotlin(owned)
        self.assertFalse(owned.exists())
        self.assertEqual(before, {path: sha(ROOT / path) for path in copies.MODIFIED_KOTLIN})

    def test_functional_probe_preserves_complete_nonmetadata_execution_regions(self):
        assert_probe_contract((HERE / 'L08StorageFunctionalProbe.kt.in').read_text(),
                              (CANONICAL / 'L08StorageProbe.kt.in').read_text())

    def test_continuation_stages_are_closed_and_all_mafia_guards_actions_remain_identical(self):
        mafia = (HERE / 'L08MafiaSnapshots.kt.in').read_text()
        probe = (HERE / 'L08StorageFunctionalProbe.kt.in').read_text()
        self.assertEqual(strip_mafia_continuation_stages(mafia), (CANONICAL / 'L08MafiaSnapshots.kt.in').read_text())
        observed = set(re.findall(r'onStage\("([a-z-]+)"\)', mafia))
        observed |= set(re.findall(r'stage = "(continue-[a-z-]+)"', probe))
        self.assertEqual(observed, functional_receipts.CONTINUATION_STAGES)
        self.assertEqual(observed, set(MAFIA_STAGES) | {'continue-home-counters', 'continue-whodunit-attach-wait',
                                                     'continue-whodunit-controller', 'continue-mafia-attach-wait'})
        self.assertIn('L08MafiaSnapshots.continueActualResumedController { stage = it }', probe)
        assert_continuation_failure_payload(probe)

    def test_continuation_diagnostics_cannot_hide_removed_guards_or_dynamic_private_stages(self):
        mafia = (HERE / 'L08MafiaSnapshots.kt.in').read_text()
        original = (CANONICAL / 'L08MafiaSnapshots.kt.in').read_text()
        for before, after in (
            ('check(canonical.value === attachedInitial && canonical.value == expected)', 'check(true)'),
            ('check(receipt is Result.Success && !receipt.data.awaitingAuthority)', 'check(true)'),
            ('check(state.isValidRecoveryState())', 'check(true)'),
            ('check(canonical.value.phase == MafiaPhase.PostGame && canonical.value.public.winner == Team.Town)', 'check(true)'),
            ('onStage("continue-mafia-action-receipt")', 'onStage(action.toString())'),
            ('onStage("continue-mafia-night-phase")', 'onStage("continue-mafia-night-phase"); onStage("PRIVATE")'),
        ):
            self.assertIn(before, mafia)
            with self.subTest(before=before), self.assertRaises((RuntimeError, AssertionError)):
                self.assertEqual(strip_mafia_continuation_stages(mafia.replace(before, after, 1)), original)
        probe = (HERE / 'L08StorageFunctionalProbe.kt.in').read_text()
        for replacement in (
            *['put("stage", stage); put("' + key + '", "synthetic-private")' for key in
              ('private_state', 'action_ordinal', 'role_map', 'target', 'seed', 'exception_message')],
            'put("stage", privateState.toString())', 'put("stage", action.toString())',
        ):
            altered = strict.once(probe, 'put("stage", stage)', replacement)
            with self.subTest(replacement=replacement), self.assertRaises(RuntimeError):
                assert_continuation_failure_payload(altered)

    def test_each_source_boundary_mutation_is_rejected_not_just_missing_check_names(self):
        original = (CANONICAL / 'L08StorageProbe.kt.in').read_text()
        candidate = (HERE / 'L08StorageFunctionalProbe.kt.in').read_text()
        changes = [
            ('check(store is FileBackedSnapshotStore && filesystem is IosSnapshotFileSystem)', 'check(true)'),
            ('check(koin.get<RoomTransport>() is P2pKitRoomTransport)', 'check(true)'),
            ('check(loaded is Result.Failure && loaded.error == DataError.NotFound)', 'check(loaded is Result.Failure)'),
            ('check(loaded.error == DataError.NotFound)', 'check(loaded.error == DataError.CorruptedData)'),
            ('awaitAbsent(store, id)', 'store.delete(id)'),
            ('check(excluded(protected))', 'check(true)'), ('check(excluded(path))', 'check(true)'),
            ('check(bytes.take(7).toByteArray().contentEquals("PARSNAP".encodeToByteArray()))', 'check(true)'),
            ('ProtectionRead(value == protection,', 'ProtectionRead(true,'),
            ('recordComparison(site, alias, "directory", directory)', 'Unit'),
            ('recordComparison(site, alias, "file", file)', 'Unit'),
            ('check(completeComparisons.size < 4)', 'check(completeComparisons.size < 5)'),
            ('put("comparison", if (reading.complete) "PASS" else "FAIL")', 'put("comparison", "PASS")'),
            ('throw cancelled', 'return@launch'), ('expected?.payload?.fill(0)', 'expected = null'),
        ]
        changes += [('protectedMetadata(' + ('alias' if site in ('save', 'load', 'dual-before-legacy-seed') else '"dual"') +
                     ', "' + site + '")', 'Unit') for site in SITES]
        for old, new in changes:
            self.assertIn(old, candidate)
            with self.subTest(change=old), self.assertRaises(RuntimeError):
                assert_probe_contract(candidate.replace(old, new, 1), original)

    def test_swift_namespace_preserves_shared_host_helpers_and_exact_distinct_test(self):
        probe = (CANONICAL / 'DSC01Probe.swift.in').read_text()
        transformed = companion.instrument_probe_swift(probe)
        self.assertIn('"readiness", "l08-storage-functional", "l08-host"', transformed)
        self.assertNotIn('"l08-storage"', transformed)
        self.assertEqual(transformed.count('if probe.isL08StorageFunctionalScenario { L08StorageFunctionalControls(probe: probe) }'), 1)
        for invalid in ('', probe + probe, transformed):
            with self.assertRaises(RuntimeError): companion.instrument_probe_swift(invalid)
        ui = (CANONICAL / 'IOSAppLaunchUITests.swift.in').read_text()
        changed = companion.instrument_ui_test(ui)
        self.assertEqual(changed.count('func ' + companion.NEW_TEST + '()'), 1)
        self.assertNotIn('func ' + companion.OLD_TEST + '()', changed)
        ordered = ['verifyActualNativeReadiness', 'verifyL08FunctionalStoreResume', 'verifyL08StartedHosts']
        locations = [changed.index('try ' + name + '(app)') for name in ordered]
        self.assertEqual(locations, sorted(locations))
        self.assertIn('continueAfterFailure = false', changed)
        for invalid in ('', ui + ui, changed):
            with self.assertRaises(RuntimeError): companion.instrument_ui_test(invalid)
        launch = (HERE / 'L08StorageFunctionalLaunch.swift.in').read_text()
        tests = (HERE / 'L08StorageFunctionalUITests.swift.in').read_text()
        self.assertIn('raw.count <= 4096', launch)
        self.assertIn('encoded.count <= 16384', launch)
        self.assertEqual(launch.count('func sampleL08Ui()'), 1)
        self.assertEqual(tests.count('private func l08TapExactPublicControl('), 1)
        self.assertEqual(tests.count('private func l08WaitRecovery('), 1)
        self.assertIn('PARLOR_L08_STORAGE_FUNCTIONAL_BOOT ', tests)
        self.assertNotIn('PARLOR_L08_STORAGE_BOOT ', tests)
        self.assertIn('XCTAssertEqual(result["functional_status"] as? String, "PASS")', tests)
        self.assertIn('["PASS", "FAIL"].contains', tests)

    def test_all_new_python_controls_parse_without_launching_any_worker(self):
        for name in ('l08_functional_copy.py', 'l08_functional_receipts.py', 'l08_functional_runner.py',
                     'test_l08_functional_copy.py', 'test_l08_functional_receipts.py', 'test_l08_functional_runner.py'):
            ast.parse((HERE / name).read_text(), filename=name)


if __name__ == '__main__': unittest.main()
