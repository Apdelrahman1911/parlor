"""Executed transformation/schema controls, NOT UIKit input-delivery evidence."""
import copy
import hashlib
import itertools
import json
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import unittest

import pin01_copy as copies
import pin01_receipts as receipts

ROOT = Path(__file__).resolve().parents[3]


def actual_sources():
    return {name: (ROOT / name).read_text() for name in copies.MODIFIED}


def digest(sources):
    return {name: hashlib.sha256(text.encode()).hexdigest() for name, text in sources.items()}


def fixture(native=1):
    rows = [[0, 4, 0, 0, 0, 0, 0, 0, 0, 0],
            [1, 1, 0, 1, 1, 1, 0, 120, 320, 1],
            [2, 2, 1, 0, 1, 1, 0, 120, 30, 2],
            [3, 2, 1, 1, 1, 1, 1, 120, 30, 2],
            [4, 1, 0, 3, 1, 0, 0, 120, 320, 4],
            [5, 3, 1, 0, 0, 0, 0, 0, 0, 0],
            [6, 4, 0, 1, 0, 0, 0, 0, 0, 0],
            [7, 4, 0, 3, 0, 0, 0, 0, 0, 0]]
    if native == 0:
        rows = [row for row in rows if row[1] != 1]
    return dict(schema=1, nativeMode=native, overflow=0, invalid=0, rows=rows)


def outcomes():
    result = []
    for game, language, choice, gesture in itertools.product(range(3), range(2), (1, 2), range(5)):
        pressed = gesture in (0, 3, 4)
        result.append(dict(game=game, language=language, choice=choice, gesture=gesture,
            runtime='SYNTHETIC-NOT-NATIVE', callback_count=int(pressed), destination=choice + 2 if pressed else 0,
            network_operations=0, exit_count=0, frame=[24, 279, 354, 101], typed_name_preserved=gesture == 4))
    return {arm: copy.deepcopy(result) for arm in ('original', 'compose', 'native')}


class CopyControls(unittest.TestCase):
    def test_actual_source_plans_are_additive_and_never_write_root_or_driver(self):
        source = actual_sources()
        driver = (ROOT / 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift').read_bytes()
        for native in (False, True):
            transformed = copies.render(source, digest(source), native_observer=native)
            self.assertEqual(set(transformed), set(copies.MODIFIED) | {copies.ADDITION})
            self.assertEqual(source, actual_sources())
            self.assertEqual(driver, (ROOT / 'iosApp/iosAppUITests/IOSAppLaunchUITests.swift').read_bytes())
            for statement in ('onClick = onClick,', 'enabled = enabled,', 'role = Role.Button,',
                              '.verticalScroll(rememberScrollState())', 'clearAndSetSemantics'):
                self.assertEqual(source[copies.PICKER].count(statement), transformed[copies.PICKER].count(statement))
            for statement in ('step = ShellStep.HostPermission', 'step = ShellStep.JoinPermission',
                              'ShellStep.HostPermission -> P2pPermissionRoute(gate, { step = ShellStep.HostName }, onExit)',
                              'ShellStep.JoinPermission -> P2pPermissionRoute(gate, { step = ShellStep.JoinName }, onExit)'):
                self.assertEqual(source[copies.SHELL].count(statement), transformed[copies.SHELL].count(statement))
            for statement in ('lifecycleCoordinator().notifyActive()', 'lifecycleCoordinator().notifyInactive()',
                              'lifecycleCoordinator().notifyBackgrounded()', 'startKoin { modules(allModules) }'):
                self.assertEqual(source[copies.MAIN].count(statement), transformed[copies.MAIN].count(statement))
            self.assertEqual(transformed[copies.CONTENT].count('child.view.addGestureRecognizer(observer)'), int(native))
            # Native policies exist in both sources but are instantiated only in the explicit native arm.
            self.assertIn('delaysTouchesBegan = false', transformed[copies.CONTENT])

    def test_closed_plan_rejects_missing_extra_stale_repeated_and_implicit_modes(self):
        source = actual_sources()
        for path in copies.MODIFIED:
            altered = dict(source); altered[path] = altered[path] + '\n'
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                copies.render(altered, digest(source), native_observer=True)
        transformed = copies.render(source, digest(source), native_observer=True)
        repeated = {name: transformed[name] for name in copies.MODIFIED}
        for invalid in (source | {'unexpected.kt': 'extra'}, {k: v for k, v in source.items() if k != copies.MAIN}, repeated):
            with self.assertRaises(RuntimeError): copies.render(invalid, digest(invalid), native_observer=True)
        for mode in (None, 0, 1, 'native'):
            with self.assertRaises(RuntimeError): copies.render(source, digest(source), native_observer=mode)

    def test_each_actual_anchor_mutation_rejects_before_any_write(self):
        source = actual_sources()
        anchors = ((copies.PICKER, '                        onClick = onHost,\n                        modifier = Modifier.fillMaxWidth(),'),
                   (copies.PICKER, '                        onClick = onJoin,\n                        modifier = Modifier.fillMaxWidth(),'),
                   (copies.SHELL, 'onHost = { step = ShellStep.HostPermission }'),
                   (copies.SHELL, 'onJoin = { step = ShellStep.JoinPermission }'),
                   (copies.MAIN, '        startKoinOnce()'),
                   (copies.CONTENT, '        ComposeContainerViewController(contentController: MainViewControllerKt.MainViewController())'))
        for path, anchor in anchors:
            for changed in (source[path].replace(anchor, '// removed actual anchor'), source[path] + '\n' + anchor):
                altered = dict(source); altered[path] = changed
                with self.subTest(path=path, changed=changed[-45:]), self.assertRaises(RuntimeError):
                    copies.render(altered, digest(altered), native_observer=True)
        self.assertEqual(actual_sources(), source)

    def test_public_scope_lifecycle_and_buffer_controls_are_explicit(self):
        text = (copies.HERE / 'PIN01Observation.kt.in').read_text()
        for required in ('DoubleArray(CAPACITY * WIDTH)', 'const val CAPACITY = 512',
                         'if (!armed && kind != 4) return', 'armed = value == 0',
                         'DisposableEffect(owner)', 'PIN01Observation.mount(owner)',
                         'onDispose { PIN01Observation.unmount(owner) }',
                         'scopes.size == 8', 'it.owner === owner',
                         'if (scopes.lastOrNull() === scope) publicStage(value)',
                         'PointerEventPass.Initial', 'PointerEventPass.Final', 'changes.count { it.isConsumed }'):
            self.assertIn(required, text)
        for forbidden in ('.consume(', 'awaitFirstDown', 'waitForUpOrCancellation', 'delay(', 'println(',
                          'CoroutineScope(', 'Thread(', 'dispatchEvent', 'toString() /* state */', 'displayName'):
            self.assertNotIn(forbidden, text)
        swift = (copies.HERE / 'PIN01TouchObserver.swift.in').read_text()
        for required in ('#if DEBUG && targetEnvironment(simulator)', 'guard Thread.isMainThread, isArmed()',
                         'cancelsTouchesInView = false', 'delaysTouchesBegan = false', 'delaysTouchesEnded = false',
                         'state = .failed',
                         'override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }',
                         'override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }',
                         '-> Bool { true }'):
            self.assertIn(required, swift)
        for forbidden in ('.recognized', 'sendEvent(', 'hitTest(', 'swizzle', 'objc_', 'perform(', 'userInfo',
                          'accessibilityLabel', 'localizedDescription', 'print(', 'DispatchQueue', 'Timer('):
            self.assertNotIn(forbidden, swift)


class ReceiptControls(unittest.TestCase):
    def decode(self, value):
        return receipts.decode(json.dumps(value).encode())

    def test_numeric_rows_describe_only_observed_boundaries(self):
        value = fixture()
        self.assertEqual(self.decode(value), value)
        observed = receipts.public_boundaries(json.dumps(value).encode(), 1)
        self.assertEqual(observed['callback_count'], 1)
        self.assertTrue(all(v for k, v in observed.items() if k != 'callback_count'))
        # Absence is faithfully reported, not inferred event loss/cause.
        value['rows'] = value['rows'][:1]
        observed = receipts.public_boundaries(json.dumps(value).encode(), 1)
        self.assertEqual(observed['callback_count'], 0)
        self.assertFalse(observed['native_began_observed'])
        self.assertNotIn('root_cause', observed)
        self.decode(fixture(0))

    def test_action_selection_rejects_boolean_float_missing_and_unknown_controls(self):
        raw = json.dumps(fixture()).encode()
        for control in (True, False, 1.0, None, 0, 3, '1'):
            with self.subTest(control=control), self.assertRaises(ValueError):
                receipts.public_boundaries(raw, control)

    def test_rejects_unknown_private_duplicate_boolean_nonfinite_and_oversize_data(self):
        for raw in (b'', b'x' * (receipts.MAX_BYTES + 1), b'{"schema":1,"schema":1}',
                    json.dumps(fixture()).replace('"nativeMode": 1', '"nativeMode": true').encode(),
                    json.dumps(fixture()).replace('"overflow": 0', '"overflow": false').encode(),
                    json.dumps(fixture()).replace('120, 320', 'NaN, 320').encode()):
            with self.subTest(raw=raw[:50]), self.assertRaises((ValueError, TypeError)): receipts.decode(raw)
        for key in ('playerId', 'role', 'state', 'secret', 'NSError', 'hierarchy'):
            value = fixture(); value[key] = 'MUST-NOT-LEAVE-MEMORY'
            with self.subTest(key=key), self.assertRaises(ValueError): self.decode(value)
        value = fixture(); value['rows'][0][0] = False
        with self.assertRaises(ValueError): self.decode(value)

    def test_invalid_or_incomplete_observations_never_qualify(self):
        changes = [('schema', 2), ('nativeMode', 2), ('overflow', 1), ('invalid', 1), ('rows', []),
                   ('rows', fixture()['rows'] * 100)]
        for key, value in changes:
            bad = fixture(); bad[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): self.decode(bad)
        for row, field, value in ((0, 1, 6), (0, 3, 5), (0, 7, 1), (1, 0, -1), (1, 2, 1), (1, 4, 17),
                                  (1, 7, 100001), (2, 3, 3), (2, 5, 2), (3, 6, -1), (5, 9, 1)):
            bad = fixture(); bad['rows'][row][field] = value
            with self.subTest(row=row, field=field), self.assertRaises(ValueError): self.decode(bad)
        bad = fixture(); bad['nativeMode'] = 0
        with self.assertRaises(ValueError): self.decode(bad)

    def test_synthetic_three_arm_outcome_comparison_discloses_limits(self):
        value = receipts.compare_neutrality(outcomes())
        self.assertEqual(value['journeys_per_arm'], 60)
        self.assertFalse(value['native_delivery_or_timing_neutrality'])
        self.assertFalse(value['original_ios26_2_root_cause_proven'])

    def test_neutrality_rejects_removed_changed_duplicated_and_failed_original_journeys(self):
        for arm in ('original', 'compose', 'native'):
            value = outcomes(); value[arm].pop()
            with self.subTest(arm=arm), self.assertRaises(ValueError): receipts.compare_neutrality(value)
        for field, wrong in (('callback_count', 2), ('network_operations', 1), ('exit_count', 1),
                             ('destination', 4), ('frame', [24, 279, 355, 101]), ('runtime', 'OTHER-RUNTIME'),
                             ('typed_name_preserved', True), ('game', True)):
            value = outcomes(); value['native'][0][field] = wrong
            with self.subTest(field=field), self.assertRaises(ValueError): receipts.compare_neutrality(value)
        value = outcomes()
        for arm in value: value[arm][1] = copy.deepcopy(value[arm][0])
        with self.assertRaises(ValueError): receipts.compare_neutrality(value)
        value = outcomes()
        for arm in value: value[arm][0]['callback_count'] = 0
        with self.assertRaises(ValueError): receipts.compare_neutrality(value)


class AppleCompileControl(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'darwin', 'UIKit typecheck requires Apple SDK; existing macOS readiness CI executes it')
    def test_standalone_passive_recognizer_typechecks_with_actual_simulator_sdk(self):
        # Compile only; no Simulator allocation, app launch, signing or delivery claim.
        with tempfile.TemporaryDirectory(prefix='parlor-pin01-swift-') as raw:
            root = Path(raw).resolve()
            source = root / 'Observer.swift'
            source.write_text('import UIKit\n' + (copies.HERE / 'PIN01TouchObserver.swift.in').read_text())
            sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'],
                                          text=True, timeout=45).strip()
            architecture = {'arm64': 'arm64', 'x86_64': 'x86_64'}[platform.machine()]
            result = subprocess.run(['/usr/bin/xcrun', 'swiftc', '-typecheck', '-D', 'DEBUG', '-sdk', sdk,
                '-target', architecture + '-apple-ios16.0-simulator', '-module-cache-path', str(root / 'cache'),
                str(source)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
            self.assertEqual(result.returncode, 0, result.stdout)


if __name__ == '__main__':
    unittest.main()
