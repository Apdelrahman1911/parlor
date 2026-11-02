"""Prepared callback/owned-state checks. Fixtures are never application evidence."""
import copy
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin56_delivery_boundary as p
import run_pin56
import test_pin55_first_responder as previous


def plan():
    leaf = p.runtime.pair_control.leaf
    return p.renderer_for_profile(p.PROFILE, p.SOURCE, p.RUNTIME, p.DEVICE)(
        {s: leaf.pair.fixture(s) for s in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')


def report():
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp).resolve()
        previous.evidence(out)
        raw = p.load(next((out/'compose').glob('native-text-*.json')))
        legacy = p.load(next((out/'compose').glob('capture-*.json')))
    raw['schema'] = 56
    raw['deliveryState'] = dict(schema=56, capturedMs=499, editorsAlive=[True], threads=1,
        rows=[[5, 1, 1, 8, 0, 1, 0], [6, 1, 1, 8, 1, 1, 1],
              [7, 1, 1, 1, 0, 1, 0], [8, 1, 1, 1, 1, 1, 0]],
        current=[dict(available=True, firstResponder=True, ancestors=[[0, 0, 0, 1, 1, 0, 1]], truncated=False)],
        invalid=0, overflow=0,
        scope='Original call thread and post-failure owned-view state; not pre-failure ownership or text')
    return raw, legacy


def decode(raw, legacy, *, control=False):
    return p.decode(json.dumps(raw).encode(), token=legacy['token'], simulator=legacy['simulator'],
        bundle=legacy['bundle'], legacy=legacy, require_control=control)


def evidence(out, failed=()):
    state = previous.evidence(out, failed)
    row = state['arms'][0]
    state['profile'] = p.PROFILE
    row['methods'] = {k.replace(p.prior.CONTROL_CASE, p.CONTROL_CASE): v for k, v in row['methods'].items()}
    row['attachment_test_ids'] = sorted(k for k, v in row['methods'].items() if v == 'Failed')
    for name in ('summary.json', 'tests.json'):
        path = out/'compose'/name
        path.write_text(path.read_text().replace(p.prior.CONTROL_CASE, p.CONTROL_CASE).replace(p.prior.FAILURE, p.FAILURE))
    calls = next(c for c in state['commands'] if c['label'] == 'compose-tests')['command']
    calls[:] = [a.replace(p.prior.CONTROL_CASE.removesuffix('()'), p.CONTROL_CASE.removesuffix('()')) for a in calls]
    path = out/'compose/test.log'
    path.write_text(path.read_text().replace(p.prior.CONTROL_CASE.split('/')[1].removesuffix('()'),
        p.CONTROL_CASE.split('/')[1].removesuffix('()')))
    (out/'compose/inputs.json').write_text(json.dumps(state['original_inputs'] |
        {s: hashlib.sha256(v.encode()).hexdigest() for s, v in plan().items()}))
    row['companion_captures'] = []
    for path in sorted((out/'compose').glob('native-text-*.json')):
        raw = p.load(path)
        legacy = p.load(path.with_name(path.name.replace('native-text-', 'capture-')))
        raw['schema'] = 56
        extra, _ = report()
        raw['deliveryState'] = extra['deliveryState']
        if not all(raw['focus']['editorsAlive']):
            raw['deliveryState'].update(editorsAlive=[False], current=[dict(available=False, firstResponder=None, ancestors=[])],
                threads=0, rows=[])
        path.write_text(json.dumps(raw))
        row['companion_captures'].append(decode(raw, legacy))
    return state


CONTROL = r'''
import Foundation
import Dispatch
final class Editor: NSObject {}
let observer = PIN56DeliveryObservation()
var editor: Editor? = Editor()
observer.bindVerifiedEditor(editor!)
let unrelated = Editor()
observer.observeOriginalCall(unrelated, kind: 1, phase: 0)
// Concurrent callback storage only. The observer never creates these threads.
let owned = editor!
let completed = DispatchSemaphore(value: 0)
DispatchQueue.global().async {
    observer.observeOriginalCall(owned, kind: 5, phase: 0)
    observer.observeOriginalCall(owned, kind: 5, phase: 1)
    completed.signal()
}
precondition(completed.wait(timeout: .now() + 5) == .success)
DispatchQueue.concurrentPerform(iterations: 200) { _ in
    observer.observeOriginalCall(owned, kind: 1, phase: 0)
    observer.observeOriginalCall(owned, kind: 1, phase: 1)
}
observer.observeOriginalCall(owned, kind: 8, phase: 0)
observer.observeOriginalCall(owned, kind: 8, phase: 1, result: true)
let captured = observer.freeze()
let rows = captured["rows"] as! [[Double]]
precondition(rows.count == 404 && captured["invalid"] as! Int == 0 && captured["overflow"] as! Int == 0)
precondition(rows.allSatisfy { $0.count == 7 && $0[5] == 1 })
precondition(rows.contains { $0[1] == 0 } && rows.contains { $0[1] == 1 })
precondition(rows.map { $0[0] } == rows.map { $0[0] }.sorted())
observer.observeOriginalCall(owned, kind: 1, phase: 0)
precondition((observer.freeze()["rows"] as! [[Double]]).count == rows.count)
let weakObserver = PIN56DeliveryObservation()
do {
    let temporary = Editor()
    weakObserver.bindVerifiedEditor(temporary)
}
precondition(weakObserver.freeze()["editorsAlive"] as! [Bool] == [false])
let bounded = PIN56DeliveryObservation()
bounded.bindVerifiedEditor(owned)
bounded.observeOriginalCall(owned, kind: 7, phase: 0)
bounded.observeOriginalCall(owned, kind: 1, phase: 1, result: true)
for _ in 0..<4100 { bounded.observeOriginalCall(owned, kind: 1, phase: 0) }
let limit = bounded.freeze()
precondition((limit["rows"] as! [[Double]]).count == 4096 && limit["invalid"] as! Int == 2 && limit["overflow"] as! Int == 4)
print("ACTUAL_CALLBACK_CONCURRENCY_INSTANCE_WEAK_LIFETIME_FREEZE_AND_BOUNDS_PASS_NOT_UIKIT_RUNTIME")
'''


class DeliveryBoundaryTests(unittest.TestCase):
    def test_only_two_copied_swift_paths_no_new_hooks_or_inputs(self):
        current = plan()
        leaf = p.runtime.pair_control.leaf
        self.assertEqual(len(current), 7)
        self.assertEqual({s for s, v in current.items() if v != leaf.pair.fixture(s)}, set(p.prior.pin02_capture.MODIFIED))
        self.assertEqual(p.CASES[:-1], p.prior.CASES[:-1])
        self.assertEqual(p.TEST_TIMEOUT_SECONDS, 900)
        old_delivery = p.prior.delivery_source()
        enriched = p.delivery_source()
        for old in ('insert(object, selectors[0], text)', 'replace(object, selectors[1], range, text)',
                    'marked(object, selectors[2], text, range)', 'delete(object, selectors[3])',
                    'resign(object, selectors[6])', 'began(object, selectors[4], presses, event)',
                    'ended(object, selectors[5], presses, event)'):
            self.assertEqual(enriched.count(old), old_delivery.count(old))
        self.assertEqual(enriched.count('method_setImplementation'), old_delivery.count('method_setImplementation'))
        self.assertEqual(p.focus_source().count('call(object, originalSelector)'), 1)
        for source in (p.focus_source(), enriched):
            self.assertLess(source.index('onOriginalCall?(object'), source.index('guard Thread.isMainThread', source.index('onOriginalCall?(object')))

    def test_original_helpers_unchanged_except_already_returned_snapshot_categories(self):
        old = previous.plan()[p.prior.pin02_capture.DRIVER]
        current = plan()[p.prior.pin02_capture.DRIVER]
        current = current.replace('PIN56Configure', 'PIN55Configure').replace('PARLOR_PIN56_', 'PARLOR_PIN55_')
        current = current.replace(p.PROFILE, p.prior.PROFILE).replace(p.CONTROL_CASE.split('/')[1].removesuffix('()'),
            p.prior.CONTROL_CASE.split('/')[1].removesuffix('()')).replace(p.FAILURE, p.prior.FAILURE)
        current = current.replace('            let returnedValue = snapshot?.value\n'
            '            let value = returnedValue as? String\n'
            '            let snapshotIsTextView = snapshot.map { $0.elementType == .textView }\n'
            '            let valueCategory = returnedValue == nil ? 0 : (returnedValue is String ? 1 : (returnedValue is NSNumber ? 2 : 3))',
            '            let value = snapshot?.value as? String')
        current = current.replace('                    "snapshotIsTextView=\\(String(describing: snapshotIsTextView)), valueCategory=\\(valueCategory), " +\n', '')
        self.assertEqual(current, old)

    def test_private_values_ports_new_threads_queries_repairs_or_lock_around_forwarding_absent(self):
        source = p.observation_source()
        for forbidden in ('insertText(', 'becomeFirstResponder(', 'accessibilityValue', 'accessibilityLabel',
                          'selectedTextRange', 'inputDelegate', 'class_addMethod', 'method_setImplementation',
                          'DispatchQueue', 'Timer(', 'Thread(', 'setValue(', 'value(forKey'):
            self.assertNotIn(forbidden, source)
        self.assertIn('MainActor.assumeIsolated', source)
        self.assertNotIn('@MainActor', source)
        self.assertLess(source.index('lock.unlock()'), source.index('MainActor.assumeIsolated'))
        self.assertIn('Double(threadOrdinals[thread]!)', source)
        self.assertNotIn('Double(thread)', source)
        self.assertIn('!result || (phase == 1 && (kind == 8 || kind == 9))', source)
        with patch.object(p, 'TEMPLATE_SHA256', '0'*64), self.assertRaises(ValueError):
            p.observation_source()

    def test_actual_state_category_control_accepts_without_claiming_causality(self):
        raw, legacy = report()
        value = decode(raw, legacy, control=True)
        self.assertTrue(value['post_failure_state_only'])
        self.assertFalse(value['application_root_cause'])
        self.assertFalse(value['shipping_qualified'])

    def test_natural_absent_or_nonfocused_inflight_editor_is_not_control_or_cause(self):
        changes = (lambda s: s.update(editorsAlive=[False], current=[dict(available=False, firstResponder=None, ancestors=[])]),
                   lambda s: s['current'][0].update(firstResponder=False),
                   lambda s: s['rows'].pop(),
                   lambda s: s.update(overflow=1))
        for change in changes:
            with self.subTest(change=change):
                raw, legacy = report()
                change(raw['deliveryState'])
                self.assertFalse(decode(raw, legacy)['application_root_cause'])
                with self.assertRaises(ValueError):
                    decode(raw, legacy, control=True)

    def test_concurrent_callback_interleavings_and_off_main_are_not_discarded(self):
        raw, legacy = report()
        state = raw['deliveryState']
        state.update(threads=2, rows=[[5, 0, 1, 1, 0, 1, 0], [6, 1, 2, 1, 0, 1, 0],
                                   [7, 1, 2, 1, 1, 1, 0], [8, 0, 1, 1, 1, 1, 0]])
        self.assertEqual(decode(raw, legacy)['deliveryState']['rows'], state['rows'])

    def test_bad_shape_private_keys_ports_overflow_clock_flags_and_unowned_ordinals_reject(self):
        changes = (lambda r: r.update(schema=56.0), lambda r: r['deliveryState'].update(text='secret'),
                   lambda r: r['deliveryState'].update(threads=65), lambda r: r['deliveryState']['rows'][0].__setitem__(2, 9999),
                   lambda r: r['deliveryState']['rows'][0].__setitem__(0, float('nan')),
                   lambda r: r['deliveryState']['rows'][0].__setitem__(5, 2),
                   lambda r: r['deliveryState']['rows'][0].__setitem__(6, 1),
                   lambda r: r['deliveryState']['current'][0]['ancestors'][0].__setitem__(1, 'text'),
                   lambda r: r['deliveryState']['current'][0].update(label='secret'))
        for change in changes:
            raw, legacy = report()
            change(raw)
            with self.assertRaises(ValueError):
                decode(raw, legacy)

    def test_result_checks_preserve_both_originals_and_exact_exporter_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            state = previous.evidence(out)
            summary = p.load(out/'compose/summary.json')
            tests = p.load(out/'compose/tests.json')
            def convert(v):
                return json.loads(json.dumps(v).replace(p.prior.CONTROL_CASE, p.CONTROL_CASE).replace(p.prior.FAILURE, p.FAILURE))
            summary, tests = convert(summary), convert(tests)
            self.assertEqual(set(p.verify_results(summary, tests, state['arms'][0]['simulator'], 65)), set(p.CASES))
            summary['skippedTests'] = 1
            with self.assertRaises(ValueError):
                p.verify_results(summary, tests, state['arms'][0]['simulator'], 65)

    def test_end_to_end_admission_preserves_natural_failures_custody_and_cleanup(self):
        for failed in ((), p.CASES[:1], p.CASES[:-1]):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out, failed)
                self.assertEqual(p.accept_native_export(out, state), p.RESULT)
                receipt = p.load(out/'delivery-focus-boundary.json')
                self.assertEqual(receipt['methods'], state['arms'][0]['methods'])
                self.assertFalse(receipt['application_root_cause'])
                self.assertFalse(receipt['shipping_qualified'])
                self.assertEqual(receipt['original_focus_capability_run'], 41515928915)
        for change in (lambda o, s: s['arms'][0].update(cleanup='FAIL'),
                       lambda o, s: s['commands'].append(s['commands'][0]),
                       lambda o, s: next((o/'compose').glob('native-text-*.json')).unlink(),
                       lambda o, s: s['build_profile'].update(test_timeout_seconds=901),
                       lambda o, s: next(c for c in s['commands'] if c['label'] == 'compose-tests')['command'].append('-retry-tests-on-failure')):
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp).resolve()
                state = evidence(out)
                change(out, state)
                with self.assertRaises((ValueError, FileNotFoundError)):
                    p.accept_native_export(out, state)
        with patch.object(p, 'PREREQUISITE_SHA256', '0'*64), self.assertRaises(ValueError):
            p.require_capability()

    def test_external_runner_restores_dispatch_and_never_maps_failures_to_success(self):
        profiles, select = run_pin56.prior.PROFILES, run_pin56.prior.select_profile
        with patch.object(run_pin56.sys, 'argv', ['run', '--profile', p.PROFILE]), \
             patch.object(run_pin56.run_pin03, 'main', side_effect=RuntimeError('failure')):
            with self.assertRaises(RuntimeError):
                run_pin56.main()
        self.assertEqual(run_pin56.prior.PROFILES, profiles)
        self.assertIs(run_pin56.prior.select_profile, select)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple callbacks unavailable on Linux')
    def test_actual_apple_concurrent_callback_instance_lifetime_freeze_and_bounding(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            source = out/'main.swift'
            source.write_text(p.observation_source()+CONTROL)
            binary = out/'control'
            compiled = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-D', 'PIN56_CONTROL', '-module-cache-path',
                str(out/'cache'), str(source), '-o', str(binary)], capture_output=True, text=True, timeout=180)
            self.assertEqual(compiled.returncode, 0, compiled.stdout+compiled.stderr)
            ran = subprocess.run([str(binary)], capture_output=True, text=True, timeout=30)
            self.assertEqual(ran.returncode, 0, ran.stdout+ran.stderr)
            self.assertIn('ACTUAL_CALLBACK_CONCURRENCY_INSTANCE_WEAK_LIFETIME_FREEZE_AND_BOUNDS_PASS_NOT_UIKIT_RUNTIME', ran.stdout)

    @unittest.skipUnless(platform.system() == 'Darwin', 'Actual Apple Simulator SDK unavailable on Linux')
    def test_actual_apple_production_wrapper_actor_typecheck_and_original_driver_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp).resolve()
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                text=True, timeout=45).strip()
            source = out/'owner.swift'
            source.write_text(plan()[p.prior.pin02_capture.CONTAINER])
            compiled = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', 'arm64-apple-ios16.0-simulator', '-module-cache-path', str(out/'cache'), str(source)],
                capture_output=True, text=True, timeout=180)
            self.assertEqual(compiled.returncode, 0, compiled.stdout+compiled.stderr)
            source = out/'driver.swift'
            source.write_text(plan()[p.prior.pin02_capture.DRIVER])
            parsed = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-frontend', '-parse', '-D', 'DEBUG',
                '-target', 'arm64-apple-ios26.2-simulator', str(source)], capture_output=True, text=True, timeout=120)
            self.assertEqual(parsed.returncode, 0, parsed.stdout+parsed.stderr)


if __name__ == '__main__':
    unittest.main()
