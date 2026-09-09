"""Actual wrapper/composition glue with inert effects; never native runtime proof.

Only selected function definitions, constant assignments and exact statement
blocks are compiled. No runner module, main build lane, helper subprocess or
simulator is executed. The coordinator owns execution of these controls.
"""
import ast
from contextlib import nullcontext
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import tempfile
import types
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, Mock, patch


ROOT = Path(__file__).resolve().parents[3]
SUPPORT = ROOT / 'scripts/verification/ios-readiness'
CAMPAIGN = ROOT / 'remediation-runs/2026-09-07-local-readiness'
COMPANION = CAMPAIGN / 'l08-storage-functional-companion-02/run_ios_readiness.py'
NORMAL = CAMPAIGN / 'native/normal-ios-launch-proposal-01/run_normal_ios_launch.py'
COMPOSITION = CAMPAIGN / 'native/l08-app-foundation-composition-01/compose_runner.py'
HELPER = SUPPORT / 'simulator_lifecycle.py'
DIRECT = 'direct-owned-v1'
LEGACY = 'legacy-apphost'
QUALIFIED = 'qualified-xcode-26.3'
LOCAL = 'local-xcode-26.5'
APPROVED = 'd' * 64
DEVICE = '11111111-2222-3333-4444-555555555555'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'


def unique_function(source, name):
    matches = [node for node in ast.walk(ast.parse(source))
               if isinstance(node, ast.FunctionDef) and node.name == name]
    if len(matches) != 1:
        raise RuntimeError('Ambiguous actual integration function: ' + name)
    return copy.deepcopy(matches[0])


def execute_nodes(nodes, namespace):
    tree = ast.fix_missing_locations(ast.Module(body=copy.deepcopy(nodes), type_ignores=[]))
    exec(compile(tree, '<isolated-lifecycle-integration>', 'exec'), namespace)


def function(source, name, namespace):
    execute_nodes([unique_function(source, name)], namespace)
    return namespace[name]


def constants(path, names, namespace):
    selected = [node for node in ast.parse(path.read_text()).body if isinstance(node, ast.Assign) and
                len(node.targets) == 1 and isinstance(node.targets[0], ast.Name) and node.targets[0].id in names]
    if {node.targets[0].id for node in selected} != set(names):
        raise RuntimeError('Missing actual integration constants')
    execute_nodes(selected, namespace)
    return namespace


def composition_namespace():
    namespace = dict(__file__=str(COMPOSITION), Path=Path, PurePosixPath=PurePosixPath,
                     ast=ast, hashlib=hashlib, json=json, re=re, os=os, stat=stat)
    constants(COMPOSITION, {'HERE', 'ROOT', 'CAMPAIGN', 'COMPANION', 'DRAFT', 'DRIVER', 'DRIVER_SHA256',
        'DRAFT_FREEZE', 'DRAFT_FREEZE_SHA256', 'TOOLCHAIN_HELPER', 'SUPPORT_CONTROLS', 'REFERENCE_PINS',
        'OWN_FILES', 'TRANSFORMS'}, namespace)
    source = COMPOSITION.read_text()
    for name in ('require', 'digest', 'raw_file', 'render_source', 'frozen_draft_files', 'control_files',
                 'control_manifest'):
        function(source, name, namespace)
    return namespace


def source_for(flavor):
    if flavor == 'normal':
        return NORMAL.read_text()
    if flavor == 'companion':
        return COMPANION.read_text()
    if flavor != 'composed':
        raise RuntimeError('Unknown integration source flavor')
    return composition_namespace()['render_source'](COMPANION.read_text())


def has_literal(node, value):
    return any(isinstance(child, ast.Constant) and child.value == value for child in ast.walk(node))


def build_block(source):
    tree = ast.parse(source)
    matches = [node for node in ast.walk(tree) if isinstance(node, ast.Try) and node.finalbody and
               has_literal(ast.Module(body=node.finalbody, type_ignores=[]), 'stop-xcode-immediate')]
    if len(matches) != 1:
        raise RuntimeError('Ambiguous actual Xcode try/finally')
    target = matches[0]
    blocks = [value for node in ast.walk(tree) for _, value in ast.iter_fields(node)
              if isinstance(value, list) and any(item is target for item in value)]
    if len(blocks) != 1:
        raise RuntimeError('Ambiguous actual Xcode parent block')
    block = blocks[0]
    finish = next(index for index, item in enumerate(block) if item is target)
    starts = [index for index, item in enumerate(block[:finish]) if isinstance(item, ast.Assign) and
              isinstance(item.value, ast.Constant) and item.value.value is True and any(
                  isinstance(left, ast.Name) and left.id == 'gradle_attempted' or
                  isinstance(left, ast.Attribute) and left.attr == 'gradle_attempted' for left in item.targets)]
    if not starts:
        raise RuntimeError('Actual build-attempt boundary missing')
    return block[starts[-1]:finish + 1]


def companion_finalizer(source):
    matches = [node.body for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Try) and node.body and
               isinstance(node.body[0], ast.If) and has_literal(node.body[0], 'final-isolated-gradle-stop')]
    if len(matches) != 1:
        raise RuntimeError('Ambiguous actual companion finalization body')
    return matches[0]


def selector_namespace():
    namespace = dict(Path=Path, LOCAL=LOCAL, QUALIFIED=QUALIFIED, DIRECT=DIRECT, LEGACY=LEGACY)
    profile_source = (SUPPORT / 'toolchain_profiles.py').read_text()
    namespace['QUALIFIED_DEVELOPER'] = '/Applications/Xcode_26.3.app/Contents/Developer'
    for name in ('profile', 'selected_toolchain'):
        function(profile_source, name, namespace)
    for name in ('require', 'selected_lifecycle', 'validate_selection'):
        function(HELPER.read_text(), name, namespace)
    function((SUPPORT / 'simulator_signing.py').read_text(), 'selected_mode', namespace)
    function((NORMAL.parent / 'kernel_image_regions.py').read_text(), 'selected_observer', namespace)
    return namespace


class LifecycleRouteIntegration(unittest.TestCase):
    def test_normal_main_passes_default_legacy_and_exact_explicit_mode_without_native_execution(self):
        for flags, mode, profile, observer, signing in (
            ([], LEGACY, LOCAL, 'vmmap', 'disabled'),
            (['--toolchain=' + QUALIFIED, '--simulator-lifecycle=' + DIRECT,
              '--simulator-signing=adhoc', '--image-observer=libproc'], DIRECT, QUALIFIED, 'libproc', 'adhoc'),
        ):
            with self.subTest(mode=mode):
                binding = MagicMock()
                binding.parent.__truediv__.return_value = SimpleNamespace(open=lambda _mode: nullcontext('inert-lock'))
                lane = Mock()
                lane.return_value.run.return_value = 'inert-result'
                namespace = selector_namespace()
                namespace.update(re=re, sys=SimpleNamespace(argv=[]), checked_binding_path=Mock(return_value=binding),
                    control_hash=Mock(return_value=APPROVED), Lane=lane,
                    fcntl=SimpleNamespace(LOCK_EX=1, LOCK_NB=2, flock=Mock()))
                main = function(NORMAL.read_text(), 'main', namespace)
                self.assertEqual(main(['ios-readiness-98', 'synthetic-binding.json', APPROVED, *flags]), 'inert-result')
                lane.assert_called_once_with('ios-readiness-98', binding, APPROVED, signing, observer, profile, mode)
                lane.return_value.run.assert_called_once_with()

    def test_companion_main_parses_and_retains_mode_before_entering_its_build_lock(self):
        main = unique_function(COMPANION.read_text(), 'main')
        first_lock = next(index for index, node in enumerate(main.body) if isinstance(node, ast.With))
        main.body = main.body[:first_lock] + [ast.Return(value=ast.Call(func=ast.Name(id='locals', ctx=ast.Load()),
                                                                        args=[], keywords=[]))]
        for flags, expected, profile in (([], LEGACY, LOCAL),
            (['--toolchain=' + QUALIFIED, '--simulator-lifecycle=' + DIRECT, '--simulator-signing=adhoc'], DIRECT, QUALIFIED)):
            with self.subTest(mode=expected):
                selectors = selector_namespace()
                namespace = dict(re=re, sys=SimpleNamespace(argv=['runner', 'ios-readiness-98', 'binding', APPROVED, *flags]),
                    toolchains=SimpleNamespace(selected_toolchain=selectors['selected_toolchain'], profile=selectors['profile']),
                    simulator_lifecycle=SimpleNamespace(selected_lifecycle=selectors['selected_lifecycle']),
                    selected_mode=selectors['selected_mode'], control_hash=lambda: APPROVED,
                    checked_binding_path=lambda _: ROOT / 'synthetic-campaign/source.json')
                function(COMPANION.read_text(), 'xcode_time_budget', namespace)
                execute_nodes([main], namespace)
                values = namespace['main']()
                self.assertEqual(values['lifecycle_mode'], expected)
                self.assertEqual(values['toolchain_name'], profile)
                self.assertEqual(values['mode'], 'adhoc' if flags else 'disabled')
                budget_values = (5400, 3000, 3300) if profile == QUALIFIED else (2700, 1200, 1500)
                expected_budget = dict(toolchain_profile=profile, whole_command_seconds=budget_values[0],
                    default_test_execution_seconds=budget_values[1], maximum_test_execution_seconds=budget_values[2])
                self.assertEqual(expected_budget, values['xcode_budget'])
                receipt_assignments = [node for node in ast.walk(ast.parse(COMPANION.read_text()))
                    if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == 'receipt'
                                                          for target in node.targets)]
                self.assertEqual(len(receipt_assignments), 1)
                namespace.update(values, now=lambda: 'synthetic-time')
                execute_nodes(receipt_assignments, namespace)
                self.assertEqual(namespace['receipt']['simulator_lifecycle_mode'], expected)
                self.assertIs(namespace['receipt']['build_attempted'], False)
                self.assertEqual(expected_budget, namespace['receipt']['xcode_time_budget'])

    def test_actual_cli_rejects_direct_mode_on_local_profile_before_any_lock_or_lane(self):
        selectors = selector_namespace()
        lane, lock = Mock(), Mock()
        namespace = dict(selectors, re=re, sys=SimpleNamespace(argv=[]), Lane=lane,
                         checked_binding_path=Mock(), control_hash=lambda _binding: APPROVED, fcntl=lock)
        main = function(NORMAL.read_text(), 'main', namespace)
        with self.assertRaisesRegex(RuntimeError, 'qualified-profile-required'):
            main(['ios-readiness-98', 'binding', APPROVED, '--simulator-lifecycle=' + DIRECT])
        lane.assert_not_called()
        lock.flock.assert_not_called()

    def test_normal_constructor_records_exact_selected_mode_without_constructing_helper(self):
        for mode, profile in ((LEGACY, LOCAL), (DIRECT, QUALIFIED)):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(prefix='parlor-lifecycle-integration-') as raw:
                namespace = selector_namespace()
                namespace.update(ROOT=ROOT, LOCAL_TOOLCHAIN=LOCAL, LEGACY_LIFECYCLE=LEGACY,
                                 toolchain_profile=namespace['profile'], now=lambda: 'synthetic-time', owned_outputs=lambda: [])
                initialize = function(NORMAL.read_text(), '__init__', namespace)
                receiver = SimpleNamespace()
                initialize(receiver, 'ios-readiness-98', Path(raw).resolve() / 'source.json', APPROVED, 'adhoc',
                           toolchain=profile, lifecycle_mode=mode)
                self.assertIsNone(receiver.lifecycle)
                self.assertEqual(receiver.receipt['simulator_lifecycle_mode'], mode)
                self.assertIs(receiver.receipt['build_attempted'], False)

    def test_actual_command_routes_default_and_direct_result_with_no_fallback_on_error(self):
        arguments = ['xcrun', 'simctl', 'bootstatus', DEVICE, '-b']
        for flavor in ('companion', 'composed', 'normal'):
            for direct in (False, True):
                with self.subTest(flavor=flavor, direct=direct):
                    legacy = Mock(return_value=23)
                    lifecycle = SimpleNamespace(command=Mock(return_value=17)) if direct else None
                    namespace = dict(lifecycle=lifecycle, command=legacy)
                    receiver = SimpleNamespace(lifecycle=lifecycle, command=legacy)
                    command = function(source_for(flavor), 'simulator_command', namespace)
                    invoke = lambda: command(receiver, arguments, 'bootstatus.log', 300) if flavor == 'normal' else command(
                        arguments, 'bootstatus.log', 300)
                    self.assertEqual(invoke(), 17 if direct else 23)
                    (lifecycle.command if direct else legacy).assert_called_once_with(arguments, 'bootstatus.log', 300)
                    if direct:
                        legacy.assert_not_called()
                        error = RuntimeError('synthetic-direct-failure')
                        lifecycle.command.side_effect = error
                        with self.assertRaises(RuntimeError) as caught:
                            invoke()
                        self.assertIs(caught.exception, error)
                        legacy.assert_not_called()

    def test_direct_metadata_shutdown_delete_wrappers_never_enter_legacy_paths_on_failure(self):
        for flavor in ('companion', 'composed', 'normal'):
            for operation in ('metadata', 'shutdown', 'delete'):
                with self.subTest(flavor=flavor, operation=operation):
                    call = Mock(return_value={'owned': 'synthetic'})
                    lifecycle = SimpleNamespace(**{operation: call})
                    # No legacy globals are provided: falling through must fail this control.
                    namespace = dict(lifecycle=lifecycle)
                    receiver = SimpleNamespace(lifecycle=lifecycle)
                    names = {'metadata': 'simulator_metadata' if flavor == 'normal' else 'own_simulator_metadata',
                             'shutdown': 'shutdown_device' if flavor == 'normal' else 'shutdown_owned_device',
                             'delete': 'delete_device' if flavor == 'normal' else 'delete_owned_device'}
                    actual = function(source_for(flavor), names[operation], namespace)
                    args = ['owned-device-before-shutdown'] if operation == 'metadata' else []
                    invoke = lambda: actual(receiver, *args) if flavor == 'normal' else actual(*args)
                    self.assertEqual(invoke(), {'owned': 'synthetic'})
                    call.assert_called_once_with(*args)
                    primary = RuntimeError('synthetic-helper-rejection')
                    call.side_effect = primary
                    with self.assertRaises(RuntimeError) as caught:
                        invoke()
                    self.assertIs(caught.exception, primary)

    def test_selected_helper_constructor_receives_live_bindings_only_in_direct_mode(self):
        for flavor in ('companion', 'composed', 'normal'):
            source = source_for(flavor)
            matches = [node for node in ast.walk(ast.parse(source)) if isinstance(node, ast.If) and
                       ast.unparse(node.test) in ('lifecycle_mode == simulator_lifecycle.DIRECT',
                                                  'self.lifecycle_mode == DIRECT_LIFECYCLE')]
            self.assertEqual(len(matches), 1)
            for mode in (LEGACY, DIRECT):
                with self.subTest(flavor=flavor, mode=mode):
                    factory = Mock(return_value='inert-lifecycle-instance')
                    source_identity, controls = Mock(return_value={'source': 1}), Mock(return_value=APPROVED)
                    receipt = dict(owned_device_name='synthetic-owned-name', source_before={'source': 0})
                    namespace = dict(ROOT=ROOT, lifecycle_mode=mode, lifecycle=None, temp=Path('/owned-temp'),
                        dest=Path('/owned-evidence'), NAME='ios-readiness-98', receipt=receipt, approved=APPROVED,
                        toolchain_name=QUALIFIED, save=Mock(), env={}, normalized_identity=source_identity,
                        control_hash=controls, simulator_lifecycle=SimpleNamespace(DIRECT=DIRECT,
                            OwnedSimulatorLifecycle=factory), DIRECT_LIFECYCLE=DIRECT, OwnedSimulatorLifecycle=factory)
                    receiver = SimpleNamespace(lifecycle_mode=mode, lifecycle=None, temporary=namespace['temp'],
                        destination=namespace['dest'], name=namespace['NAME'], receipt=receipt, approved=APPROVED,
                        toolchain={'name': QUALIFIED}, save=namespace['save'], environment={}, binding='synthetic-binding')
                    namespace['self'] = receiver
                    execute_nodes(matches, namespace)
                    if mode == LEGACY:
                        factory.assert_not_called()
                    else:
                        factory.assert_called_once()
                        keywords = factory.call_args.kwargs
                        self.assertIs(keywords['receipt'], receipt)
                        self.assertEqual(keywords['source'], {'source': 0})
                        self.assertEqual(keywords['approved'], APPROVED)
                        self.assertEqual(keywords['current_bindings'](), ({'source': 1}, APPROVED))
                        source_identity.return_value = {'source': 2}
                        controls.return_value = 'e' * 64
                        self.assertEqual(keywords['current_bindings'](), ({'source': 2}, 'e' * 64))

    def test_non_lifecycle_app_and_provenance_simctl_calls_keep_generic_owner_routing(self):
        for flavor in ('companion', 'composed', 'normal'):
            selected = []
            for node in ast.walk(ast.parse(source_for(flavor))):
                if not isinstance(node, ast.Call) or not node.args or not isinstance(node.args[0], ast.List):
                    continue
                parts = node.args[0].elts
                if len(parts) < 3 or not all(isinstance(item, ast.Constant) for item in parts[:3]):
                    continue
                if [item.value for item in parts[:2]] != ['xcrun', 'simctl']:
                    continue
                verb = parts[2].value
                if verb not in {'get_app_container', 'launch', 'terminate', 'spawn'}:
                    continue
                selected.append(verb)
                target = node.func.id if isinstance(node.func, ast.Name) else node.func.attr
                with self.subTest(flavor=flavor, verb=verb):
                    self.assertIn(target, ('command', 'require', 'raw_command'))
                    generic = Mock(return_value=0)
                    lifecycle = SimpleNamespace(command=Mock(side_effect=AssertionError('Nonlifecycle direct route')))
                    namespace = dict(command=generic, uuid=DEVICE, APP_ID='com.parlor.app.debug',
                        stdout=Path('/synthetic-owned/app.stdout'), stderr=Path('/synthetic-owned/app.stderr'),
                        self=SimpleNamespace(uuid=DEVICE, require=generic, command=generic, lifecycle=lifecycle))
                    if target == 'raw_command':
                        namespace['within_budget'] = Mock()
                        function(source_for(flavor), 'raw_command', namespace)
                    execute_nodes([ast.Expr(value=node)], namespace)
                    generic.assert_called_once()
                    self.assertEqual(generic.call_args.args[0][:3], ['xcrun', 'simctl', verb])
                    lifecycle.command.assert_not_called()
            self.assertIn('get_app_container', selected)
            if flavor == 'normal':
                self.assertIn('launch', selected)

    def test_composer_forwards_explicit_lifecycle_arguments_without_executing_generated_runner(self):
        namespace = composition_namespace()
        manifest = [dict(path='synthetic-control', sha256=APPROVED)]
        approved = namespace['digest'](json.dumps(manifest, separators=(',', ':')).encode())
        arguments = ['compose_runner.py', 'ios-readiness-98', 'synthetic-binding', approved,
                     '--simulator-signing=adhoc', '--toolchain=' + QUALIFIED, '--simulator-lifecycle=' + DIRECT]
        system = SimpleNamespace(argv=arguments, path=['original-inert-import-path'])
        executed = []

        def inert_exec(_compiled, target):
            executed.append(target)
            target['main'] = lambda: list(system.argv)

        namespace.update(sys=system, types=types, checked_binding=Mock(return_value=ROOT / 'AGENTS.md'),
            control_manifest=Mock(return_value=manifest), load_frozen_module=Mock(return_value=SimpleNamespace()),
            Hooks=Mock(return_value='inert-foundation-hooks'), exec=inert_exec)
        main = function(COMPOSITION.read_text(), 'main', namespace)
        self.assertEqual(main(), arguments)
        self.assertEqual(len(executed), 1)
        self.assertEqual(executed[0]['foundation'], 'inert-foundation-hooks')
        self.assertEqual(system.path, ['original-inert-import-path'])


class LifecycleBuildBoundaryIntegration(unittest.TestCase):
    def exercise(self, flavor, *, direct=True, marker_error=False):
        trace, receipt = [], {}
        primary = OSError('synthetic-marker-or-xcode-spawn-failure')

        def mark():
            self.assertIs(receipt['build_attempted'], True)
            trace.append('mark')
            if marker_error:
                raise primary

        def launch(*_args, **_kwargs):
            self.assertIs(receipt['build_attempted'], True)
            trace.append('builder')
            raise primary

        lifecycle = SimpleNamespace(mark_build_attempted=Mock(side_effect=mark)) if direct else None
        stop = Mock(side_effect=lambda label: trace.append(label))
        command = Mock(side_effect=launch)
        namespace = dict(lifecycle=lifecycle, receipt=receipt, command=command, stop_gradle=stop,
            project=Path('/owned-copy/iosApp/iosApp.xcodeproj/project.pbxproj'), temp=Path('/owned-temp'),
            results=Path('/owned-temp/Results.xcresult'), uuid=DEVICE, env={'SDK_NAME': 'iphonesimulator26.2'},
            signing_arguments=[], now=lambda: 'synthetic-time', save=Mock(side_effect=lambda: trace.append('save')),
            PROJECT='iosApp/iosApp.xcodeproj/project.pbxproj', xcode_arguments=Mock(return_value=['xcodebuild', 'test']))
        if flavor != 'normal':
            source = source_for(flavor)
            function(source, 'xcode_time_budget', namespace)
            function(source, 'finish_xcode_attempt', namespace)
            namespace.update(xcode_budget=namespace['xcode_time_budget'](QUALIFIED),
                dest=Path('/synthetic-owned-evidence'), preserve_postbuild_raw=Mock(
                    side_effect=lambda *_args, **_kwargs: trace.append('preserve-raw') or True))
        receiver = SimpleNamespace(lifecycle=lifecycle, receipt=receipt, command=command, stop_gradle=stop,
            temporary=namespace['temp'], uuid=DEVICE, environment=namespace['env'], signing={}, save=namespace['save'])
        namespace['self'] = receiver
        with self.assertRaises(OSError) as caught:
            execute_nodes(build_block(source_for(flavor)), namespace)
        self.assertIs(caught.exception, primary)
        self.assertIs(receipt['build_attempted'], True)
        self.assertIs(receiver.gradle_attempted if flavor == 'normal' else namespace['gradle_attempted'], True)
        stop.assert_called_once_with('stop-xcode-immediate')
        suffix = [] if flavor == 'normal' else ['preserve-raw']
        if flavor != 'normal':
            namespace['preserve_postbuild_raw'].assert_called_once()
        if marker_error:
            command.assert_not_called()
            self.assertEqual(trace, ['save', 'mark', 'stop-xcode-immediate', *suffix])
        else:
            command.assert_called_once()
            self.assertEqual(trace, ['save', *(['mark'] if direct else []), 'builder', 'stop-xcode-immediate', *suffix])

    def test_real_build_boundary_marks_before_launch_and_always_stops_after_spawn_failure(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                self.exercise(flavor)

    def test_durable_marker_failure_prevents_builder_but_keeps_immediate_gradle_stop(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                self.exercise(flavor, marker_error=True)

    def test_legacy_build_path_has_no_helper_marker_and_retains_immediate_stop(self):
        for flavor in ('companion', 'normal'):
            with self.subTest(flavor=flavor):
                self.exercise(flavor, direct=False)

    def test_actual_finish_requires_exact_true_and_preserves_first_finalization_failure(self):
        for flavor in ('companion', 'composed'):
            finish = function(source_for(flavor), 'finish_xcode_attempt', {})
            for ack in (True, False, None, 0, 1, 'true'):
                stop, preserve = Mock(), Mock(return_value=ack)
                with self.subTest(flavor=flavor, ack=repr(ack)):
                    if ack is True:
                        self.assertIsNone(finish({}, None, stop, preserve))
                    else:
                        with self.assertRaisesRegex(RuntimeError, 'did not acknowledge completion'):
                            finish({}, None, stop, preserve)
                    stop.assert_called_once_with()
                    preserve.assert_called_once_with()
            primary, secondary, trace, receipt = KeyboardInterrupt('synthetic first finalizer error'), OSError('PRIVATE-SECONDARY'), [], {}

            def stop():
                trace.append('stop')
                raise primary

            def preserve():
                trace.append('preserve')
                raise secondary

            with self.assertRaises(KeyboardInterrupt) as caught:
                finish(receipt, None, stop, preserve)
            self.assertIs(primary, caught.exception)
            self.assertEqual(['stop', 'preserve'], trace)
            self.assertEqual([dict(stage='preserve-raw-postbuild', type='OSError', message='postbuild-finalization-failed')],
                             receipt['postbuild_secondary_errors'])

    def normal_ack(self, *, bad_view=None, save_failure=False):
        node = unique_function(NORMAL.read_text(), 'run_xctest')
        start = next(index for index, statement in enumerate(node.body) if isinstance(statement, ast.Assign) and
                     any(isinstance(target, ast.Name) and target.id == 'results' for target in statement.targets))
        stop = next(index for index, statement in enumerate(node.body) if isinstance(statement, ast.Assign) and
                    any(isinstance(target, ast.Subscript) and isinstance(target.slice, ast.Constant) and
                        target.slice.value == 'xctest' for target in statement.targets))
        receipt = dict(postbuild_evidence_preserved=False, runtime_evidence_status='RUNNING')
        primary = OSError('synthetic-ack-persistence-failure')
        save = Mock(side_effect=primary if save_failure else None)
        command = Mock(side_effect=lambda _args, label: 1 if label == 'xcresult-' + str(bad_view) + '.json' else 0)
        with tempfile.TemporaryDirectory(prefix='parlor-lifecycle-ack-') as raw:
            root = Path(raw).resolve()
            (root / 'Results.xcresult').mkdir()
            receiver = SimpleNamespace(temporary=root, receipt=receipt, command=command, save=save)
            namespace = dict(self=receiver, METHOD='synthetic-executed-method')
            if save_failure:
                with self.assertRaises(OSError) as caught:
                    execute_nodes(node.body[start:stop], namespace)
                self.assertIs(caught.exception, primary)
            elif bad_view is not None:
                with self.assertRaisesRegex(RuntimeError, 'Actual XCTest extraction failed'):
                    execute_nodes(node.body[start:stop], namespace)
            else:
                execute_nodes(node.body[start:stop], namespace)
        self.assertEqual([call.args[1] for call in command.call_args_list],
                         ['xcresult-summary.json', 'xcresult-tests.json', 'xcresult-test-details.json'])
        self.assertIs(receipt['postbuild_evidence_preserved'], bad_view is None and not save_failure)
        self.assertNotEqual(receipt['runtime_evidence_status'], 'PASS')

    def test_normal_ack_requires_all_three_actual_extraction_results_not_runtime_success(self):
        for view in (None, 'summary', 'tests', 'test-details'):
            with self.subTest(failed_view=view):
                self.normal_ack(bad_view=view)

    def test_normal_failed_ack_save_revokes_authority_and_preserves_original_error(self):
        self.normal_ack(save_failure=True)


class XcodeBudgetIntegration(unittest.TestCase):
    def test_actual_a_budget_function_is_closed_and_qualified_only(self):
        for flavor in ('companion', 'composed'):
            budget = function(source_for(flavor), 'xcode_time_budget', {})
            for profile, expected in ((QUALIFIED, (5400, 3000, 3300)), (LOCAL, (2700, 1200, 1500))):
                with self.subTest(flavor=flavor, profile=profile):
                    self.assertEqual(dict(toolchain_profile=profile, whole_command_seconds=expected[0],
                        default_test_execution_seconds=expected[1], maximum_test_execution_seconds=expected[2]),
                        budget(profile))
            for invalid in (None, '', 'qualified', 'QUALIFIED-XCODE-26.3', 'qualified-xcode-26.3 ', True, 5400):
                with self.subTest(flavor=flavor, invalid=invalid), self.assertRaisesRegex(RuntimeError, 'Unknown explicit'):
                    budget(invalid)

    def test_actual_a_and_b_build_argv_budget_test_selection_and_serialism_are_unchanged_except_qualified_a(self):
        for flavor in ('companion', 'composed', 'normal'):
            for profile in (QUALIFIED, LOCAL):
                with self.subTest(flavor=flavor, profile=profile):
                    source, receipt = source_for(flavor), {}
                    root = Path('/synthetic-owned-copy')
                    command, stop, preserve = Mock(return_value=65), Mock(), Mock(return_value=True)
                    namespace = dict(receipt=receipt, project=root / 'copy/iosApp/iosApp.xcodeproj/project.pbxproj',
                        temp=root, dest=Path('/synthetic-evidence'), results=root / 'Results.xcresult',
                        uuid=DEVICE, env={'SDK_NAME': 'iphonesimulator26.2'}, signing_arguments=[], lifecycle=None,
                        save=Mock(), command=command, stop_gradle=stop, now=lambda: 'synthetic-time',
                        preserve_postbuild_raw=preserve, PROJECT='iosApp/iosApp.xcodeproj/project.pbxproj')
                    if flavor == 'normal':
                        constants(NORMAL.parent / 'normal_launch_receipts.py', {'METHOD', 'SELECTOR'}, namespace)
                        function(source, 'xcode_arguments', namespace)
                        namespace['self'] = SimpleNamespace(receipt=receipt, lifecycle=None, temporary=root,
                            uuid=DEVICE, environment=namespace['env'], signing={}, command=command,
                            stop_gradle=stop, save=namespace['save'])
                        expected_budget = (2700, 120, 240)
                    else:
                        function(source, 'xcode_time_budget', namespace)
                        function(source, 'finish_xcode_attempt', namespace)
                        # Execute main's real profile-to-budget wiring, not a
                        # fixture-written call that could hide a wrong-profile
                        # assignment in main while its pure function stays right.
                        namespace['toolchain_name'] = profile
                        wiring = [node for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Assign) and
                                  any(isinstance(target, ast.Name) and target.id == 'xcode_budget' for target in node.targets)]
                        self.assertEqual(1, len(wiring))
                        execute_nodes(wiring, namespace)
                        expected_budget = (5400, 3000, 3300) if profile == QUALIFIED else (2700, 1200, 1500)
                    execute_nodes(build_block(source), namespace)
                    command.assert_called_once()
                    args, label = command.call_args.args[:2]
                    self.assertEqual('xcodebuild.log', label)
                    timeout = command.call_args.kwargs['timeout'] if flavor == 'normal' else command.call_args.args[2]
                    self.assertEqual(expected_budget[0], timeout)
                    arguments = list(map(str, args))
                    prefix = ['xcodebuild', '-project', str(root / 'copy/iosApp/iosApp.xcodeproj'), '-scheme', 'iosApp',
                        '-configuration', 'Debug', '-sdk', 'iphonesimulator26.2', '-destination', 'id=' + DEVICE,
                        '-derivedDataPath', str(root / 'DerivedData'), '-resultBundlePath', str(root / 'Results.xcresult')]
                    selection = (['-only-testing:iosAppUITests/IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert',
                                  '-test-iterations', '8', '-test-repetition-relaunch-enabled', 'YES']
                                 if flavor == 'normal' else [])
                    expected = prefix + selection + ['-parallel-testing-enabled', 'NO',
                        '-maximum-concurrent-test-simulator-destinations', '1', '-disable-concurrent-destination-testing',
                        '-jobs', '1', '-test-timeouts-enabled', 'YES', '-default-test-execution-time-allowance',
                        str(expected_budget[1]), '-maximum-test-execution-time-allowance', str(expected_budget[2]),
                        'ONLY_ACTIVE_ARCH=YES', 'COMPILER_INDEX_STORE_ENABLE=NO', 'test']
                    self.assertEqual(expected, arguments)
                    self.assertEqual(65, receipt['xcodebuild_exit_code'])
                    stop.assert_called_once_with('stop-xcode-immediate')
                    if flavor == 'normal':
                        preserve.assert_not_called()
                        self.assertEqual(64 * 1024 * 1024, command.call_args.kwargs['limit'])
                    else:
                        preserve.assert_called_once()


def raw_preservation_namespace(source):
    """Only real pure record plans/validators and the actual collector are compiled."""
    namespace = dict(Path=Path, PurePosixPath=PurePosixPath, os=os, stat=stat, json=json, re=re,
                     APP_ID='com.parlor.app.debug', defer_parent_signals=nullcontext, now=lambda: 'synthetic-time')
    function((COMPANION.parent / 'probe_validation.py').read_text(), 'require', namespace)
    artifact = dict(Path=Path, PurePosixPath=PurePosixPath, re=re)
    artifact_file = COMPANION.parent / 'artifact_inventory.py'
    constants(artifact_file, {'FRAMEWORK_PATH', 'LOADER_KEYS', 'UUID', 'SHA256'}, artifact)
    for name in ('safe_relative', 'owned_framework_relative', 'validate_framework_observation', 'validate_loader_environment'):
        function(artifact_file.read_text(), name, artifact)
        namespace[name] = artifact[name]
    namespace.update(FRAMEWORK_PATH=artifact['FRAMEWORK_PATH'], LOADER_KEYS=artifact['LOADER_KEYS'])
    storage = COMPANION.parent / 'l08_receipts.py'
    constants(storage, {'STORAGE_PLAN', 'STORAGE_CHECKS', 'STORAGE_FAILURE_STAGES', 'HOST_PLAN', 'HOST_CHECKS',
                        'HOST_VARIANTS', 'HOST_COUNTERS', 'UUID', 'PROTECTION_ATTRIBUTE_VALUES',
                        'PROTECTION_VOLUME_VALUES'}, namespace)
    for name in ('_context', 'storage_result_names', 'host_result_names', 'host_checks', '_validate_protection_read',
                 'validate_protection_metadata', 'validate_preservable_operation'):
        function(storage.read_text(), name, namespace)
    functional = COMPANION.parent / 'l08_functional_receipts.py'
    constants(functional, {'SCENARIO', 'KIND', 'INNER_MAXIMUM', 'OUTER_MAXIMUM', 'FUNCTIONAL_CHECKS',
                           'CONTINUATION_STAGES'}, namespace)
    for name in ('comparison_plan', 'validate_comparisons', 'functional_result_names',
                 'validate_preservable_functional_operation'):
        function(functional.read_text(), name, namespace)
    failure, failures = {}, COMPANION.parent / 'native_failure_receipts.py'
    failure.update(re=re)
    constants(failures, {'UUID', 'STAGES', 'DOMAINS', 'KEYS'}, failure)
    for name in ('require', 'validate_failure'):
        function(failures.read_text(), name, failure)
    namespace['validate_failure'] = failure['validate_failure']
    for name in ('read_stable_owned_file', 'raw_json_object', 'write_raw_owned_file', 'preserve_postbuild_raw',
                 'finish_xcode_attempt', 'xcode_time_budget'):
        function(source, name, namespace)
    return namespace


class RawPostbuildPreservationIntegration(unittest.TestCase):
    """Real bounded filesystem retention with fake tools and real closed validators."""
    SCENARIOS = ('settings', 'whodunit', 'mafia', 'os', 'readiness', 'l08-storage-functional', 'l08-host')

    def fixture(self, flavor='companion', *, all_operations=False):
        temporary = tempfile.TemporaryDirectory(prefix='parlor-raw-postbuild-control-')
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name).resolve()
        home, dest, temp = root / 'owned-home', root / 'evidence', root / 'owned-copy'
        container = home / 'Library/Developer/CoreSimulator/Devices' / DEVICE / 'data/Containers/Data/Application' / DEVICE
        parent = container / 'tmp'
        parent.mkdir(parents=True)
        dest.mkdir()
        (temp / 'Results.xcresult').mkdir(parents=True)
        namespace = raw_preservation_namespace(source_for(flavor))
        clock = SimpleNamespace(value=0, increment=0)
        namespace['time'] = SimpleNamespace(monotonic=lambda: clock.value)
        receipt = dict(postbuild_evidence_preserved=False, runtime_evidence_status='FAIL', signing_mode='adhoc',
                       error=dict(type='TimeoutExpired', message='synthetic original build primary'))
        inputs, trace, saved, outputs, failures = {}, [], [], {
            'xcresult-summary.json': b'{"synthetic_summary_only":true}\n',
            'xcresult-tests.json': b'{"testNodes":[]}\n',
            'owned-container.log': (str(container) + '\n').encode()}, {}
        plans = {scenario: namespace[provider]() for scenario, provider in (
            ('l08-storage', 'storage_result_names'), ('l08-host', 'host_result_names'),
            ('l08-storage-functional', 'functional_result_names'))}
        for name in ('storage_result_names', 'host_result_names', 'functional_result_names',
                     'validate_preservable_operation', 'validate_preservable_functional_operation', 'validate_failure'):
            namespace[name] = Mock(wraps=namespace[name])

        def write_input(name, value):
            raw = (json.dumps(value) + '\n').encode() if not isinstance(value, bytes) else value
            (parent / name).write_bytes(raw)
            inputs[name] = raw

        for scenario in RawPostbuildPreservationIntegration.SCENARIOS:
            value = dict(scenario=scenario, synthetic_control_not_runtime=True)
            if scenario == 'readiness':
                value = dict(scenario='readiness', schemaVersion=6, runToken=DEVICE,
                             observations=[dict(phase='before_main', boot=DEVICE)])
            write_input('parlor-dsc01-' + scenario + '-result.json', value)
        for scenario, names in plans.items():
            for name in names if all_operations else names[:1]:
                match = re.fullmatch(r'parlor-' + scenario + r'-(\d+)-(.+)\.json', name)
                boot, action = int(match[1]), match[2]
                observation = dict(schema_version=1, status='FAIL', run_token=DEVICE, boot_ordinal=boot,
                                   action=action, stage='context', reason='fixture_or_boundary_failure')
                if scenario == 'l08-storage-functional':
                    observation.pop('status')
                    observation.update(kind=scenario, functional_status='FAIL', complete_comparisons=[],
                        physical_or_store_evidence=False, command_ordinal=namespace['STORAGE_PLAN'][boot].index(action) + 1)
                value = dict(schema_version=1, scenario=scenario, signing_mode='adhoc', run_token=DEVICE,
                    process_boot=DEVICE, boot_ordinal=boot, action=action, loaded_app_images=['Parlor'],
                    loaded_compose_framework=dict(origin='installed-app-bundle', path=namespace['FRAMEWORK_PATH'],
                        image_uuid=DEVICE, file_bytes=1, file_sha256='a' * 64),
                    loader_environment={key: dict(present=False, task_owned_paths=[], redacted_entry_count=0)
                                        for key in namespace['LOADER_KEYS']}, observation=observation)
                if scenario == 'l08-host':
                    value['observation_sequence'] = 1
                write_input(name, value)
        write_input('parlor-native-synthetic-seed-cleanup.json', dict(synthetic_control_not_runtime=True))
        for ordinal in (range(1, 9) if all_operations else (1, 8)):
            write_input('parlor-native-readiness-boot-' + str(ordinal) + '.json',
                        dict(synthetic_control_not_runtime=True, boot_ordinal=ordinal))
            write_input('parlor-native-readiness-failure-' + str(ordinal) + '.json',
                dict(schema_version=1, kind='diagnostic_failure', boot_ordinal=ordinal, process_boot=DEVICE,
                     run_token=DEVICE, signing_mode='adhoc', stage='receipt-write', error_domain_id=1,
                     error_code=1, error_code_redacted=False))

        def command(arguments, label, timeout):
            expected = (['xcrun', 'simctl', 'get_app_container', DEVICE, 'com.parlor.app.debug', 'data']
                if label == 'owned-container.log' else ['xcrun', 'xcresulttool', 'get', 'test-results',
                    label.removeprefix('xcresult-').removesuffix('.json'), '--path', temp / 'Results.xcresult'])
            self.assertEqual(expected, arguments)
            self.assertEqual(120, timeout)
            trace.append(label)
            clock.value += clock.increment
            if isinstance(failures.get(label), BaseException):
                raise failures[label]
            if outputs[label] is not None:
                (dest / label).write_bytes(outputs[label])
            return failures.get(label, 0)

        save_error = SimpleNamespace(value=None)

        def save():
            saved.append(copy.deepcopy(receipt))
            if save_error.value is not None:
                raise save_error.value

        collector, command_mock, save_mock = namespace['preserve_postbuild_raw'], Mock(side_effect=command), Mock(side_effect=save)

        def invoke(extra=None):
            with patch.object(Path, 'home', return_value=home):
                return collector(receipt, dest, temp, DEVICE, command_mock, save_mock, extra_container=extra)

        return SimpleNamespace(invoke=invoke, namespace=namespace, receipt=receipt, root=root, home=home,
            dest=dest, temp=temp, parent=parent, container=container, trace=trace, saved=saved, clock=clock,
            outputs=outputs, failures=failures, inputs=inputs, write_input=write_input, plans=plans,
            command=command_mock, save=save_mock, save_error=save_error)

    def assert_denied(self, fixture, *, expected=None):
        primary = copy.deepcopy(fixture.receipt['error'])
        with self.assertRaises((RuntimeError, OSError, ValueError, KeyboardInterrupt, SystemExit)) as caught:
            fixture.invoke()
        if expected is not None:
            self.assertIs(expected, caught.exception)
        self.assertIs(fixture.receipt['postbuild_evidence_preserved'], False)
        self.assertEqual('FAIL', fixture.receipt['raw_postbuild_preservation']['status'])
        self.assertEqual('FAIL', fixture.receipt['runtime_evidence_status'])
        self.assertEqual(primary, fixture.receipt['error'])
        for error in fixture.receipt['raw_postbuild_preservation']['errors']:
            self.assertEqual({'stage', 'type', 'message'}, set(error))
            self.assertEqual('raw-preservation-failed', error['message'])
        return caught.exception

    def test_actual_collector_retains_every_safe_plan_and_closed_failure_without_claiming_runtime_success(self):
        for flavor in ('companion', 'composed'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, all_operations=True)
                self.assertIs(fixture.invoke(), True)
                state = fixture.receipt['raw_postbuild_preservation']
                self.assertEqual('COMPLETE', state['status'])
                self.assertEqual((420, [], []), (state['budget_seconds'], state['errors'], state['missing']))
                self.assertEqual(['xcresult-summary.json', 'xcresult-tests.json', 'owned-container.log'], fixture.trace)
                self.assertIs(fixture.receipt['postbuild_evidence_preserved'], True)
                self.assertIs(fixture.saved[-1]['postbuild_evidence_preserved'], True)
                self.assertEqual('FAIL', fixture.receipt['runtime_evidence_status'])
                self.assertEqual('TimeoutExpired', fixture.receipt['error']['type'])
                for provider in ('storage_result_names', 'host_result_names', 'functional_result_names'):
                    fixture.namespace[provider].assert_called_once_with()
                self.assertEqual(fixture.plans['l08-storage'] + fixture.plans['l08-host'],
                                 fixture.receipt['l08_preserved_operation_files'])
                self.assertEqual(fixture.plans['l08-storage-functional'],
                                 fixture.receipt['l08_functional_preserved_operation_files'])
                self.assertEqual(len(fixture.plans['l08-storage']) + len(fixture.plans['l08-host']),
                                 fixture.namespace['validate_preservable_operation'].call_count)
                self.assertEqual(len(fixture.plans['l08-storage-functional']),
                                 fixture.namespace['validate_preservable_functional_operation'].call_count)
                self.assertEqual(8, fixture.namespace['validate_failure'].call_count)
                for name, raw in fixture.inputs.items():
                    target = name.replace('parlor-dsc01-', 'probe-', 1) if name.startswith('parlor-dsc01-') else name
                    actual = (fixture.dest / target).read_bytes()
                    self.assertEqual(json.loads(raw), json.loads(actual))
                    if not name.startswith(('parlor-l08-', 'parlor-native-readiness-failure-')):
                        self.assertEqual(raw, actual)
                    self.assertIn(target, state['files'])
                self.assertEqual(len(state['files']), len(set(state['files'])))

    def test_raw_stage_failures_are_independent_and_original_primary_is_never_replaced(self):
        for label in ('xcresult-summary.json', 'xcresult-tests.json', 'owned-container.log'):
            with self.subTest(label=label):
                fixture = self.fixture()
                primary = KeyboardInterrupt('PRIVATE-RAW-INTERRUPTION')
                fixture.failures[label] = primary
                self.assert_denied(fixture, expected=primary)
                self.assertEqual(['xcresult-summary.json', 'xcresult-tests.json', 'owned-container.log'], fixture.trace)
                if label != 'owned-container.log':
                    self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())
                self.assertNotIn(str(primary), json.dumps(fixture.receipt))

    def test_missing_required_results_views_or_container_log_never_grants_an_ack(self):
        for absent in ('Results.xcresult', 'xcresult-summary.json', 'xcresult-tests.json', 'owned-container.log'):
            with self.subTest(absent=absent):
                fixture = self.fixture()
                if absent == 'Results.xcresult':
                    (fixture.temp / absent).rmdir()
                else:
                    fixture.outputs[absent] = None
                self.assert_denied(fixture)
                self.assertIn('owned-container.log', fixture.trace)
                if absent != 'owned-container.log':
                    self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_extraction_exit_codes_are_exact_integers_and_each_failure_still_attempts_later_stages(self):
        for code in (1, -15, False, None, '0'):
            fixture = self.fixture()
            fixture.failures['xcresult-summary.json'] = code
            with self.subTest(code=repr(code)):
                self.assert_denied(fixture)
                self.assertEqual(['xcresult-summary.json', 'xcresult-tests.json', 'owned-container.log'], fixture.trace)
                self.assertEqual(code, fixture.receipt['xcresult_extraction_exit_codes']['summary'])
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_malformed_duplicate_nonfinite_nonobject_or_empty_json_is_not_preservable(self):
        for raw in (b'', b'{', b'[]', b'null', b'{"x":NaN}', b'{"x":Infinity}', b'{"x":1,"x":2}',
                    b'{"nested":{"x":1,"x":2}}'):
            for location in ('xcresult', 'probe'):
                fixture = self.fixture()
                if location == 'xcresult':
                    fixture.outputs['xcresult-summary.json'] = raw
                    missing = 'xcresult-summary.json'
                else:
                    fixture.write_input('parlor-dsc01-settings-result.json', raw)
                    missing = 'probe-settings-result.json'
                with self.subTest(raw=raw, location=location):
                    self.assert_denied(fixture)
                    self.assertNotIn(missing, fixture.receipt['raw_postbuild_preservation']['files'])
                    self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_missing_optional_operation_is_recorded_as_absent_not_fabricated_or_runtime_pass(self):
        fixture = self.fixture()
        name = fixture.plans['l08-storage'][0]
        (fixture.parent / name).unlink()
        self.assertIs(fixture.invoke(), True)
        self.assertIn(name, fixture.receipt['raw_postbuild_preservation']['missing'])
        self.assertNotIn(name, fixture.receipt['l08_preserved_operation_files'])
        self.assertFalse((fixture.dest / name).exists())
        self.assertEqual('FAIL', fixture.receipt['runtime_evidence_status'])

    def test_real_operation_validators_reject_unknown_private_fields_but_preserve_later_scenarios(self):
        for scenario in ('l08-storage', 'l08-host', 'l08-storage-functional'):
            fixture = self.fixture()
            name = fixture.plans[scenario][0]
            value = json.loads(fixture.inputs[name])
            value['PRIVATE-UNREVIEWED-FIELD'] = 'must-not-enter-evidence'
            fixture.write_input(name, value)
            with self.subTest(scenario=scenario):
                self.assert_denied(fixture)
                self.assertFalse((fixture.dest / name).exists())
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())
                validator = fixture.namespace['validate_preservable_functional_operation' if
                    scenario == 'l08-storage-functional' else 'validate_preservable_operation']
                if scenario == 'l08-storage-functional':
                    validator.assert_called_once_with(value)
                else:
                    validator.assert_any_call(value, scenario)
                self.assertNotIn('PRIVATE-UNREVIEWED-FIELD', json.dumps(fixture.receipt))

    def test_valid_operation_cannot_be_retained_under_another_planned_filename(self):
        for scenario in ('l08-storage', 'l08-host', 'l08-storage-functional'):
            fixture = self.fixture()
            name = fixture.plans[scenario][0]
            value = json.loads(fixture.inputs[name])
            value['boot_ordinal'] = value['observation']['boot_ordinal'] = 2 if scenario == 'l08-host' else 3
            fixture.write_input(name, value)
            with self.subTest(scenario=scenario):
                error = self.assert_denied(fixture)
                self.assertIn('exact planned filename', str(error))
                self.assertFalse((fixture.dest / name).exists())
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_native_failure_context_validator_is_real_and_failures_do_not_stop_later_retention(self):
        for key, wrong in (('signing_mode', 'disabled'), ('run_token', 'aaaaaaaa-2222-3333-4444-555555555555'),
                           ('process_boot', 'aaaaaaaa-2222-3333-4444-555555555555'), ('boot_ordinal', 2)):
            fixture = self.fixture()
            name = 'parlor-native-readiness-failure-1.json'
            value = json.loads(fixture.inputs[name]); value[key] = wrong
            fixture.write_input(name, value)
            with self.subTest(key=key):
                self.assert_denied(fixture)
                fixture.namespace['validate_failure'].assert_any_call(value, 1,
                    json.loads(fixture.inputs['parlor-dsc01-readiness-result.json']), 'adhoc')
                self.assertFalse((fixture.dest / name).exists())
                self.assertTrue((fixture.dest / 'parlor-native-readiness-failure-8.json').is_file())

    def test_symlink_hardlink_directory_and_oversize_source_records_cannot_ack_or_block_other_safe_files(self):
        for kind in ('symlink', 'hardlink', 'directory', 'oversize'):
            fixture = self.fixture()
            name = 'parlor-dsc01-settings-result.json'
            path = fixture.parent / name
            raw = path.read_bytes(); path.unlink()
            protected = fixture.root / 'unowned-control.json'; protected.write_bytes(raw)
            if kind == 'symlink':
                path.symlink_to(protected)
            elif kind == 'hardlink':
                os.link(protected, path)
            elif kind == 'directory':
                path.mkdir()
            else:
                oversized = raw + b' ' * (262145 - len(raw))
                self.assertEqual(262145, len(oversized))
                self.assertEqual(json.loads(raw), json.loads(oversized))
                path.write_bytes(oversized)
            with self.subTest(kind=kind):
                error = self.assert_denied(fixture)
                if kind == 'oversize':
                    self.assertIs(type(error), RuntimeError)
                    self.assertEqual('Raw evidence is not a bounded owned regular file', str(error))
                self.assertEqual(raw, protected.read_bytes())
                self.assertFalse((fixture.dest / 'probe-settings-result.json').exists())
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_required_extraction_symlink_hardlink_and_oversize_do_not_grant_ack(self):
        for kind in ('symlink', 'hardlink', 'oversize'):
            fixture = self.fixture()
            label = 'xcresult-summary.json'
            fixture.outputs[label] = None
            target = fixture.dest / label
            original = fixture.root / 'unowned-summary.json'; original.write_bytes(b'{}')
            if kind == 'symlink':
                target.symlink_to(original)
            elif kind == 'hardlink':
                os.link(original, target)
            else:
                raw = original.read_bytes()
                oversized = raw + b' ' * (4194305 - len(raw))
                self.assertEqual(4194305, len(oversized))
                self.assertEqual(json.loads(raw), json.loads(oversized))
                target.write_bytes(oversized)
            with self.subTest(kind=kind):
                error = self.assert_denied(fixture)
                if kind == 'oversize':
                    self.assertIs(type(error), RuntimeError)
                    self.assertEqual('Raw evidence is not a bounded owned regular file', str(error))
                self.assertNotIn(label, fixture.receipt['raw_postbuild_preservation']['files'])
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_changing_open_descriptor_or_postread_path_is_rejected_by_actual_identity_checks(self):
        for stage in ('before-open', 'after-read'):
            fixture = self.fixture()
            target = fixture.parent / 'parlor-dsc01-settings-result.json'
            original_open, original_lstat, original_fstat, calls = os.open, Path.lstat, os.fstat, []
            identity = target.stat()
            phase = dict(descriptor_checks=0, postread_mutated=False)

            def checked_descriptor(descriptor):
                info = original_fstat(descriptor)
                if (info.st_dev, info.st_ino) == (identity.st_dev, identity.st_ino):
                    phase['descriptor_checks'] += 1
                return info

            def replaced_open(path, flags, *args, **kwargs):
                if Path(path) == target and not calls:
                    calls.append('replace')
                    old = target.with_suffix('.original'); target.rename(old)
                    target.write_bytes(b'{"changed":true}')
                return original_open(path, flags, *args, **kwargs)

            def changed_lstat(path, *args, **kwargs):
                if path == target:
                    calls.append('stat')
                    # retain() does an earlier existence lstat. Arm only after
                    # the real reader's before/after fstat pair, not a guessed
                    # pathname-call count that could mutate before its snapshot.
                    if phase['descriptor_checks'] == 2 and not phase['postread_mutated']:
                        target.write_bytes(b'{"changed-after-read":true}')
                        phase['postread_mutated'] = True
                return original_lstat(path, *args, **kwargs)

            guard = patch.object(os, 'open', replaced_open) if stage == 'before-open' else patch.object(Path, 'lstat', changed_lstat)
            with self.subTest(stage=stage), guard, patch.object(os, 'fstat', checked_descriptor):
                error = self.assert_denied(fixture)
                self.assertIn('changed' if stage == 'before-open' else 'replaced', str(error))
            self.assertTrue(calls)
            if stage == 'after-read':
                self.assertEqual(2, phase['descriptor_checks'])
                self.assertIs(phase['postread_mutated'], True)
            self.assertFalse((fixture.dest / 'probe-settings-result.json').exists())
            self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_wrong_container_scope_or_redirected_result_directory_never_authorizes_copying(self):
        for kind in ('wrong-device', 'nonuuid-container', 'parent-only', 'multiline', 'tmp-symlink', 'results-symlink'):
            fixture = self.fixture()
            if kind in {'wrong-device', 'nonuuid-container', 'parent-only', 'multiline'}:
                value = str(fixture.container)
                if kind == 'wrong-device':
                    value = value.replace('/Devices/' + DEVICE, '/Devices/aaaaaaaa-2222-3333-4444-555555555555')
                elif kind == 'nonuuid-container':
                    value = str(fixture.container.parent / 'not-a-uuid')
                elif kind == 'parent-only':
                    value = str(fixture.container.parent)
                else:
                    value += '\n' + str(fixture.root)
                fixture.outputs['owned-container.log'] = value.encode()
            elif kind == 'tmp-symlink':
                owned = fixture.parent.with_name('held-original-tmp'); fixture.parent.rename(owned)
                fixture.parent.symlink_to(owned, target_is_directory=True)
            else:
                result = fixture.temp / 'Results.xcresult'; result.rmdir()
                result.symlink_to(fixture.root, target_is_directory=True)
            with self.subTest(kind=kind):
                self.assert_denied(fixture)
                if kind != 'results-symlink':
                    self.assertFalse((fixture.dest / 'probe-settings-result.json').exists())

    def test_existing_or_symlink_destination_is_never_overwritten(self):
        for symlink in (False, True):
            fixture = self.fixture()
            protected = fixture.root / 'protected-original'; protected.write_bytes(b'private original bytes')
            target = fixture.dest / 'probe-settings-result.json'
            if symlink:
                target.symlink_to(protected)
            else:
                target.write_bytes(protected.read_bytes())
            with self.subTest(symlink=symlink):
                self.assert_denied(fixture)
                self.assertEqual(b'private original bytes', target.read_bytes())
                self.assertEqual(b'private original bytes', protected.read_bytes())
                self.assertNotIn(target.name, fixture.receipt['raw_postbuild_preservation']['files'])
                self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())

    def test_failed_ack_persistence_revokes_authority_after_safe_files_were_retained(self):
        fixture = self.fixture()
        primary = OSError('PRIVATE-ACK-SAVE-DIAGNOSTIC')
        fixture.save_error.value = primary
        self.assert_denied(fixture, expected=primary)
        self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())
        self.assertNotIn(str(primary), json.dumps(fixture.receipt))
        self.assertEqual('persist-raw-preservation-ack', fixture.receipt['raw_postbuild_preservation']['errors'][-1]['stage'])

    def test_additional_composed_retention_is_independent_and_cannot_skip_remaining_records(self):
        fixture = self.fixture('composed')
        error = OSError('PRIVATE-FOUNDATION-RAW-FAILURE')
        extra = Mock(side_effect=error)
        with self.assertRaises(OSError) as caught:
            fixture.invoke(extra)
        self.assertIs(error, caught.exception)
        extra.assert_called_once_with(fixture.container)
        self.assertIs(fixture.receipt['postbuild_evidence_preserved'], False)
        self.assertTrue((fixture.dest / 'parlor-native-readiness-boot-8.json').is_file())
        self.assertEqual('FAIL', fixture.receipt['runtime_evidence_status'])
        self.assertNotIn(str(error), json.dumps(fixture.receipt))

    def test_aggregate_budget_keeps_all_120_second_commands_and_refuses_a_shortened_late_attempt(self):
        for increment, expected_calls in ((121, 3), (150, 2), (421, 1)):
            fixture = self.fixture()
            fixture.clock.increment = increment
            with self.subTest(increment=increment):
                if increment == 121:
                    self.assertIs(fixture.invoke(), True)
                else:
                    self.assertIsInstance(self.assert_denied(fixture), TimeoutError)
                self.assertEqual(expected_calls, fixture.command.call_count)
                self.assertEqual([120] * expected_calls, [call.args[2] for call in fixture.command.call_args_list])
                self.assertEqual(420, fixture.receipt['raw_postbuild_preservation']['budget_seconds'])

    def test_completed_or_failed_collector_is_one_shot_with_no_second_command_or_write(self):
        for failed in (False, True):
            fixture = self.fixture()
            if failed:
                fixture.failures['xcresult-summary.json'] = RuntimeError('synthetic extraction failure')
                self.assert_denied(fixture)
            else:
                fixture.invoke()
            calls, files = fixture.command.call_count, {path.name: path.read_bytes() for path in fixture.dest.iterdir()}
            before = copy.deepcopy(fixture.receipt)
            with self.subTest(failed=failed):
                if failed:
                    with self.assertRaisesRegex(RuntimeError, 'one-shot'):
                        fixture.invoke()
                else:
                    self.assertIs(fixture.invoke(), True)
                self.assertEqual(calls, fixture.command.call_count)
                self.assertEqual(files, {path.name: path.read_bytes() for path in fixture.dest.iterdir()})
                self.assertEqual(before, fixture.receipt)


class LifecycleFinalizerIntegration(unittest.TestCase):
    def fixture(self, flavor, *, direct=True, attempted=True, authorization=True,
                barrier_error=False, final_census_error=False, recovery_error=False):
        trace, errors = [], []
        source_identity = {'synthetic-source': 'stable'}
        receipt = dict(source_before=source_identity, runtime_evidence_status='FAIL', provenance_status='NOT_RUN',
                       notice_package_status='NOT_RUN', simulator_creation_attempted=True,
                       postbuild_evidence_preserved=attempted)
        owner = SimpleNamespace(unknown_holders=[], secondary_errors={},
            receipt_members=Mock(return_value=[]), handles=[],
            stop=Mock(side_effect=lambda: trace.append('owner.stop')),
            secondary=SimpleNamespace(cleanup=Mock(side_effect=lambda: trace.append('fifo')),
                                      dump=Mock(return_value={'synthetic': 'ledger'})))

        def census(**_kwargs):
            trace.append('final-census')
            if final_census_error:
                raise RuntimeError('synthetic-strict-ps-or-lsof-failure')
            return []

        owner.refresh = Mock(side_effect=census)

        def recover():
            trace.append('recover')
            if recovery_error:
                raise RuntimeError('synthetic-recovery-failure')
            return dict(udid=DEVICE)

        def barrier(actual_owner, build_attempted):
            self.assertIs(actual_owner, owner)
            self.assertIs(build_attempted, attempted)
            trace.append('barrier')
            if barrier_error:
                raise RuntimeError('synthetic-postbuild-barrier-failure')
            return authorization

        def finish():
            trace.append('finish')
            if (barrier_error or authorization is not True or recovery_error or
                    attempted and receipt.get('postbuild_evidence_preserved') is not True):
                raise RuntimeError('synthetic-helper-finalization-refuses-incomplete-barrier')
            return True

        def foundation_preservation(*_args):
            trace.append('foundation')
            return True

        lifecycle = SimpleNamespace(recover=Mock(side_effect=recover), prepare_cleanup=Mock(side_effect=barrier),
            shutdown=Mock(side_effect=lambda: trace.append('shutdown')), delete=Mock(side_effect=lambda: trace.append('delete')),
            finish=Mock(side_effect=finish)) if direct else None
        stop = Mock(side_effect=lambda label: trace.append(label))
        legacy_metadata = Mock(return_value=None)
        namespace = dict(ROOT=ROOT, Path=Path, re=re, lifecycle=lifecycle, receipt=receipt, owner=owner, errors=errors,
            gradle_attempted=attempted, uuid=None if recovery_error else DEVICE, dest=Path('/synthetic-evidence'),
            temp=None, copied_source_manifest=None, eligible=False, outputs=[], original_outputs=[], links=[], deferred=[],
            approved=APPROVED, control_hash=lambda *args: APPROVED, normalized_identity=lambda: source_identity,
            now=lambda: 'synthetic-time', stop_gradle=stop, write_json=Mock(), save=Mock(), digest=lambda _path: 'a' * 64,
            command=Mock(side_effect=AssertionError('No generic simulator command authorized by this fixture')),
            own_simulator_metadata=legacy_metadata, classify_final_companion=lambda _receipt: {'status': 'FAIL'},
            foundation=SimpleNamespace(preserve_before_cleanup=Mock(side_effect=foundation_preservation),
                restrict_final=Mock(side_effect=lambda value: self.assertEqual(value['status'], 'FAIL'))))
        source = source_for(flavor)
        if flavor == 'normal':
            receiver = SimpleNamespace(gradle_attempted=attempted, owner=owner, lifecycle=lifecycle,
                uuid=namespace['uuid'], receipt=receipt, errors=errors, destination=namespace['dest'],
                temporary=None, copy_manifest=None, binding='synthetic-binding', approved=APPROVED, original_outputs=[],
                stop_gradle=stop, simulator_metadata=legacy_metadata, compact_log=Mock(), verify_copy=Mock(),
                remove_temporary=Mock())
            stage = function(source, 'stage', namespace)
            receiver.stage = lambda label, action: stage(receiver, label, action)
            for name in ('shutdown_device', 'delete_device', 'verify_workers'):
                actual = function(source, name, namespace)
                setattr(receiver, name, lambda actual=actual: actual(receiver))
            finalize = function(source, 'finalize', namespace)
            invoke = lambda: finalize(receiver)
        else:
            receiver = None
            function(source, 'stage', namespace)
            function(source, 'shutdown_owned_device', namespace)
            function(source, 'delete_owned_device', namespace)
            invoke = lambda: execute_nodes(companion_finalizer(source), namespace)
        return SimpleNamespace(invoke=invoke, trace=trace, receipt=receipt, errors=errors, owner=owner,
                               lifecycle=lifecycle, stop=stop, legacy_metadata=legacy_metadata, namespace=namespace,
                               receiver=receiver)

    def attach_custody(self, fixture):
        class Allocation:
            present = True

            def exists(self):
                return self.present

            def is_symlink(self):
                return False

            def __truediv__(self, name):
                return Path('/synthetic-owned-copy') / name

        allocation = Allocation()

        def remove(*_args):
            self.assertTrue(allocation.present)
            fixture.trace.append('remove-temp')
            allocation.present = False

        removal = Mock(side_effect=remove)
        if fixture.receiver is None:
            fixture.namespace.update(temp=allocation, shutil=SimpleNamespace(rmtree=removal))
        else:
            fixture.receiver.temporary = allocation
            fixture.receiver.remove_temporary = removal
        previous_finish = fixture.lifecycle.finish.side_effect

        def finish_with_existing_custody():
            self.assertTrue(allocation.present)
            return previous_finish()

        fixture.lifecycle.finish.side_effect = finish_with_existing_custody
        return allocation, removal

    def attach_actual_barrier(self, fixture):
        # Execute the real helper acknowledgement/strict-stop decision. Only its
        # resource journal/direct process I/O is inert, not the decision itself.
        namespace = {}
        function(HELPER.read_text(), 'require', namespace)
        prepare = function(HELPER.read_text(), 'prepare_cleanup', namespace)
        state = SimpleNamespace(barrier={'status': 'NOT_RUN'}, build_attempted=True, receipt=fixture.receipt,
            _start_cleanup=Mock(), retire_direct_children=Mock(), _check_journal=Mock(),
            _append=Mock(), _publish=Mock(), _error=Mock())

        def actual(owner, attempted):
            fixture.trace.append('barrier')
            return prepare(state, owner, attempted)

        fixture.lifecycle.prepare_cleanup.side_effect = actual
        return state

    def test_only_exact_true_barrier_authorizes_real_wrapper_shutdown_and_delete_glue(self):
        for flavor in ('companion', 'composed', 'normal'):
            for value in (True, False, None, 1, 'PASS'):
                with self.subTest(flavor=flavor, authorization=repr(value)):
                    fixture = self.fixture(flavor, authorization=value)
                    fixture.invoke()
                    fixture.lifecycle.prepare_cleanup.assert_called_once_with(fixture.owner, True)
                    if value is True:
                        fixture.lifecycle.shutdown.assert_called_once_with()
                        fixture.lifecycle.delete.assert_called_once_with()
                        self.assertLess(fixture.trace.index('barrier'), fixture.trace.index('shutdown'))
                        self.assertLess(fixture.trace.index('shutdown'), fixture.trace.index('delete'))
                    else:
                        fixture.lifecycle.shutdown.assert_not_called()
                        fixture.lifecycle.delete.assert_not_called()
                        self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')
                    fixture.owner.stop.assert_called_once_with()
                    fixture.owner.secondary.cleanup.assert_called_once_with()
                    fixture.owner.refresh.assert_called_once()
                    fixture.stop.assert_called_once_with('stop-final')
                    fixture.legacy_metadata.assert_not_called()

    def test_failed_postbuild_barrier_cannot_be_reopened_by_later_successful_owner_cleanup(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, barrier_error=True)
                fixture.invoke()
                fixture.lifecycle.prepare_cleanup.assert_called_once_with(fixture.owner, True)
                fixture.lifecycle.shutdown.assert_not_called()
                fixture.lifecycle.delete.assert_not_called()
                self.assertLess(fixture.trace.index('barrier'), fixture.trace.index('owner.stop'))
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')
                failed = [row['stage'] for row in fixture.receipt['finalization_stages'] if row['status'] == 'FAIL']
                self.assertIn('authorize-journaled-device-cleanup', failed)
                self.assertIn('finalize-direct-simulator-lifecycle', failed)

    def test_prebuild_resource_cleanup_still_runs_final_strict_census_and_retains_its_failure(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, attempted=False, final_census_error=True)
                fixture.invoke()
                fixture.lifecycle.prepare_cleanup.assert_called_once_with(fixture.owner, False)
                fixture.lifecycle.shutdown.assert_called_once_with()
                fixture.lifecycle.delete.assert_called_once_with()
                fixture.stop.assert_not_called()
                fixture.owner.stop.assert_called_once_with()
                fixture.owner.secondary.cleanup.assert_called_once_with()
                fixture.owner.refresh.assert_called_once()
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')
                self.assertIn(dict(stage='verify-workers-before-file-removal', status='FAIL', at='synthetic-time'),
                              fixture.receipt['finalization_stages'])

    def test_direct_recovery_failure_never_falls_back_to_legacy_inventory_or_log_adoption(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, recovery_error=True)
                fixture.invoke()
                fixture.lifecycle.recover.assert_called_once_with()
                fixture.lifecycle.prepare_cleanup.assert_not_called()
                fixture.lifecycle.shutdown.assert_not_called()
                fixture.lifecycle.delete.assert_not_called()
                fixture.legacy_metadata.assert_not_called()
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')

    def test_missing_owned_uuid_never_authorizes_destructive_calls(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, recovery_error=True)
                fixture.lifecycle.recover.side_effect = None
                fixture.lifecycle.recover.return_value = None
                fixture.invoke()
                fixture.lifecycle.prepare_cleanup.assert_not_called()
                fixture.lifecycle.shutdown.assert_not_called()
                fixture.lifecycle.delete.assert_not_called()
                fixture.lifecycle.finish.assert_called_once_with()
                fixture.legacy_metadata.assert_not_called()
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')

    def test_foundation_retention_precedes_failed_barrier_and_does_not_bypass_it(self):
        fixture = self.fixture('composed', barrier_error=True)
        fixture.invoke()
        fixture.namespace['foundation'].preserve_before_cleanup.assert_called_once()
        self.assertLess(fixture.trace.index('foundation'), fixture.trace.index('barrier'))
        self.assertLess(fixture.trace.index('stop-final'), fixture.trace.index('foundation'))
        fixture.lifecycle.shutdown.assert_not_called()
        fixture.lifecycle.delete.assert_not_called()
        self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')
        fixture.namespace['foundation'].restrict_final.assert_called_once_with(fixture.receipt)

    def assert_preservation_failure_retains_resource_and_runs_worker_cleanup(self, fixture, allocation, removal, state):
        self.assertTrue(allocation.present)
        removal.assert_not_called()
        fixture.lifecycle.shutdown.assert_not_called()
        fixture.lifecycle.delete.assert_not_called()
        fixture.owner.stop.assert_called_once_with()  # Original final stop; real helper denied its earlier barrier stop.
        fixture.owner.secondary.cleanup.assert_called_once_with()
        fixture.owner.refresh.assert_called_once()
        fixture.stop.assert_called_once_with('stop-final')
        self.assertEqual(state.barrier['status'], 'FAIL')
        state._start_cleanup.assert_called_once_with()
        state._error.assert_called_once()
        self.assertEqual(state._error.call_args.args[0], 'postbuild-quiescence-barrier')
        self.assertIs(type(state._error.call_args.args[1]), RuntimeError)
        self.assertEqual(str(state._error.call_args.args[1]), 'direct-simulator-postbuild-evidence-not-preserved')
        self.assertEqual(state._error.call_args.kwargs, {'cleanup': True})
        self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')
        self.assertIs(fixture.receipt['temporary_directory_removed'], False)

    def test_real_barrier_with_true_ack_requires_strict_stop_census_and_reaping_before_removal(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor)
                allocation, removal = self.attach_custody(fixture)
                state = self.attach_actual_barrier(fixture)

                def reap(*, timeout):
                    self.assertEqual(timeout, 0)
                    fixture.trace.append('build-handle-reaped')
                    return 0

                child = SimpleNamespace(wait=Mock(side_effect=reap))
                fixture.owner.handles = [child]
                fixture.invoke()
                self.assertEqual(state.barrier, dict(status='PASS', build_attempted=True, evidence_preserved=True,
                    strict_stop=True, fresh_strict_refresh=True, direct_build_handles=1, direct_build_handles_reaped=1))
                state._start_cleanup.assert_called_once_with()
                state.retire_direct_children.assert_called_once_with()
                state._check_journal.assert_called_once_with()
                state._append.assert_called_once_with(dict(event='destructive-cleanup-barrier', result=state.barrier))
                state._publish.assert_called_once_with(cleanup=True)
                state._error.assert_not_called()
                self.assertEqual(fixture.owner.stop.call_count, 2)
                self.assertEqual(fixture.owner.refresh.call_count, 2)
                self.assertEqual(fixture.owner.refresh.call_args_list[0].kwargs,
                                 {'inspect_files': True, 'include_outputs': True})
                child.wait.assert_called_once_with(timeout=0)
                fixture.lifecycle.shutdown.assert_called_once_with()
                fixture.lifecycle.delete.assert_called_once_with()
                self.assertLess(fixture.trace.index('build-handle-reaped'), fixture.trace.index('shutdown'))
                self.assertFalse(allocation.present)
                removal.assert_called_once()
                self.assertLess(fixture.trace.index('finish'), fixture.trace.index('remove-temp'))
                self.assertIs(fixture.receipt['temporary_directory_removed'], True)
                self.assertEqual(fixture.receipt['cleanup_status'], 'PASS')
                self.assertEqual(fixture.receipt['status'], 'FAIL')  # Inert controls cannot credit runtime.

    def test_missing_or_malformed_ack_blocks_real_barrier_and_temporary_removal(self):
        for flavor in ('companion', 'composed', 'normal'):
            for present, value in ((False, None), (True, False), (True, None), (True, 1), (True, 'true')):
                with self.subTest(flavor=flavor, present=present, value=repr(value)):
                    fixture = self.fixture(flavor)
                    if present:
                        fixture.receipt['postbuild_evidence_preserved'] = value
                    else:
                        fixture.receipt.pop('postbuild_evidence_preserved')
                    allocation, removal = self.attach_custody(fixture)
                    state = self.attach_actual_barrier(fixture)
                    fixture.invoke()
                    self.assert_preservation_failure_retains_resource_and_runs_worker_cleanup(
                        fixture, allocation, removal, state)

    def test_failed_foundation_preservation_denies_device_and_temporary_cleanup(self):
        fixture = self.fixture('composed')
        allocation, removal = self.attach_custody(fixture)
        state = self.attach_actual_barrier(fixture)
        fixture.namespace['foundation'].preserve_before_cleanup.side_effect = OSError('synthetic-foundation-retention-failure')
        fixture.invoke()
        self.assertIs(fixture.receipt['postbuild_evidence_preserved'], False)
        self.assert_preservation_failure_retains_resource_and_runs_worker_cleanup(fixture, allocation, removal, state)

    def test_failed_composed_ack_save_revokes_authority_without_skipping_workers_or_fifo(self):
        fixture = self.fixture('composed')
        allocation, removal = self.attach_custody(fixture)
        state = self.attach_actual_barrier(fixture)
        fixture.namespace['save'].side_effect = OSError('synthetic-composed-ack-save-failure')
        fixture.invoke()
        self.assertIs(fixture.receipt['postbuild_evidence_preserved'], False)
        self.assert_preservation_failure_retains_resource_and_runs_worker_cleanup(fixture, allocation, removal, state)
        self.assertTrue(any(row.get('error_type') == 'OSError' and
                            row.get('message') == 'synthetic-composed-ack-save-failure' for row in fixture.errors))

    def test_failed_actual_xcode_boundary_preserves_raw_before_real_barrier_and_never_skips_worker_fifo_cleanup(self):
        for flavor in ('companion', 'composed'):
            for primary_kind in ('timeout', 'interrupt', 'immediate-stop'):
                for raw_failure in (False, True):
                    with self.subTest(flavor=flavor, primary=primary_kind, raw_failure=raw_failure):
                        # Same actual collector fixture, but its receipt is the
                        # actual wrapper finalizer's live receipt, not a copied ack.
                        raw = RawPostbuildPreservationIntegration.fixture(self, flavor)
                        fixture = self.fixture(flavor)
                        fixture.receipt.update(raw.receipt)
                        allocation, removal = self.attach_custody(fixture)
                        state = self.attach_actual_barrier(fixture)
                        if raw_failure:
                            raw.outputs['xcresult-summary.json'] = b'{corrupt synthetic raw view'
                        primary = (subprocess.TimeoutExpired(['xcodebuild', 'test'], 5400) if primary_kind == 'timeout'
                                   else KeyboardInterrupt('synthetic xcode interrupt') if primary_kind == 'interrupt'
                                   else RuntimeError('synthetic immediate stop primary'))

                        def stop(label):
                            fixture.trace.append(label)
                            if primary_kind == 'immediate-stop' and label == 'stop-xcode-immediate':
                                raise primary

                        fixture.stop.side_effect = stop
                        raw_command = raw.command.side_effect

                        def retained_command(arguments, label, timeout):
                            fixture.trace.append('raw:' + label)
                            return raw_command(arguments, label, timeout)

                        raw.command.side_effect = retained_command
                        actual_collector = raw.namespace['preserve_postbuild_raw']

                        def preserve(receipt, destination, temporary, uuid, _command, save, **kwargs):
                            self.assertIs(receipt, fixture.receipt)
                            self.assertEqual((raw.dest, raw.temp, DEVICE), (destination, temporary, uuid))
                            return actual_collector(receipt, destination, temporary, uuid, raw.command, save, **kwargs)

                        preserved = Mock(side_effect=preserve)
                        namespace = dict(raw.namespace, receipt=fixture.receipt, temp=raw.temp, dest=raw.dest,
                            results=raw.temp / 'Results.xcresult', project=raw.temp / 'copy/iosApp/iosApp.xcodeproj/project.pbxproj',
                            uuid=DEVICE, env={'SDK_NAME': 'iphonesimulator26.2'}, signing_arguments=[],
                            xcode_budget=raw.namespace['xcode_time_budget'](QUALIFIED), now=lambda: 'synthetic-time',
                            command=Mock(return_value=65, side_effect=None if primary_kind == 'immediate-stop' else primary),
                            stop_gradle=fixture.stop, save=fixture.namespace['save'], preserve_postbuild_raw=preserved,
                            lifecycle=SimpleNamespace(mark_build_attempted=Mock()),
                            foundation=SimpleNamespace(preserve=Mock(return_value=True)))
                        with patch.object(Path, 'home', return_value=raw.home), self.assertRaises(type(primary)) as caught:
                            execute_nodes(build_block(source_for(flavor)), namespace)
                        self.assertIs(primary, caught.exception)
                        preserved.assert_called_once()
                        self.assertTrue((raw.dest / 'parlor-native-readiness-boot-8.json').is_file())
                        self.assertEqual('FAIL' if raw_failure else 'COMPLETE',
                                         fixture.receipt['raw_postbuild_preservation']['status'])
                        fixture.invoke()
                        self.assertEqual(['stop-xcode-immediate', 'stop-final'],
                                         [call.args[0] for call in fixture.stop.call_args_list])
                        self.assertLess(fixture.trace.index('stop-xcode-immediate'), fixture.trace.index('raw:xcresult-summary.json'))
                        self.assertLess(fixture.trace.index('raw:owned-container.log'), fixture.trace.index('stop-final'))
                        self.assertLess(fixture.trace.index('stop-final'), fixture.trace.index('barrier'))
                        fixture.owner.secondary.cleanup.assert_called_once_with()
                        fixture.lifecycle.finish.assert_called_once_with()
                        if raw_failure:
                            self.assertEqual('FAIL', state.barrier['status'])
                            self.assertIs(fixture.receipt['postbuild_evidence_preserved'], False)
                            fixture.lifecycle.shutdown.assert_not_called()
                            fixture.lifecycle.delete.assert_not_called()
                            removal.assert_not_called()
                            self.assertTrue(allocation.present)
                            fixture.owner.stop.assert_called_once_with()
                        else:
                            self.assertEqual('PASS', state.barrier['status'])
                            fixture.lifecycle.shutdown.assert_called_once_with()
                            fixture.lifecycle.delete.assert_called_once_with()
                            removal.assert_called_once()
                            self.assertFalse(allocation.present)
                            self.assertEqual(2, fixture.owner.stop.call_count)
                        self.assertEqual('FAIL', fixture.receipt['status'])

    def test_raw_ack_does_not_bypass_actual_unknown_holder_barrier_or_delete_owned_copy(self):
        fixture = self.fixture('companion')
        allocation, removal = self.attach_custody(fixture)
        state = self.attach_actual_barrier(fixture)
        fixture.owner.unknown_holders = [dict(pid=987654, role='foreign')]
        fixture.invoke()
        self.assertIs(fixture.receipt['postbuild_evidence_preserved'], True)
        self.assertEqual('FAIL', state.barrier['status'])
        fixture.lifecycle.shutdown.assert_not_called()
        fixture.lifecycle.delete.assert_not_called()
        fixture.owner.secondary.cleanup.assert_called_once_with()
        removal.assert_not_called()
        self.assertTrue(allocation.present)
        self.assertEqual('FAIL', fixture.receipt['cleanup_status'])

    def test_lifecycle_finishes_before_actual_wrapper_owned_temporary_removal(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor)
                allocation, removal = self.attach_custody(fixture)
                fixture.invoke()
                self.assertFalse(allocation.present)
                removal.assert_called_once()
                self.assertLess(fixture.trace.index('finish'), fixture.trace.index('remove-temp'))
                self.assertLess(fixture.trace.index('final-census'), fixture.trace.index('remove-temp'))
                self.assertIs(fixture.receipt['temporary_directory_removed'], True)

    def test_failed_final_strict_census_prevents_owned_temporary_removal(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor, attempted=False, final_census_error=True)
                allocation, removal = self.attach_custody(fixture)
                fixture.invoke()
                self.assertTrue(allocation.present)
                removal.assert_not_called()
                self.assertIs(fixture.receipt['temporary_directory_removed'], False)
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')

    def test_actual_copied_input_check_failure_cannot_become_cleanup_pass(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor)
                allocation, _removal = self.attach_custody(fixture)
                manifest = {'synthetic-copy-manifest': True}
                inspect = Mock(return_value={'unchanged': False})
                fixture.namespace['inspect_copied_inputs_after_build'] = inspect
                if flavor == 'normal':
                    fixture.receiver.copy_manifest = manifest
                    verify = function(source_for(flavor), 'verify_copy', fixture.namespace)
                    fixture.receiver.verify_copy = lambda: verify(fixture.receiver)
                else:
                    fixture.namespace['copied_source_manifest'] = manifest
                fixture.invoke()
                inspect.assert_called_once_with(allocation / 'copy', manifest)
                self.assertIs(fixture.receipt['copied_sources_unchanged'], False)
                self.assertEqual(fixture.receipt['cleanup_status'], 'FAIL')

    def test_source_control_secondary_and_final_census_checks_are_retained_after_direct_cleanup(self):
        for flavor in ('companion', 'composed', 'normal'):
            with self.subTest(flavor=flavor):
                fixture = self.fixture(flavor)
                fixture.invoke()
                self.assertIs(fixture.receipt['source_unchanged'], True)
                self.assertIs(fixture.receipt['controls_unchanged'], True)
                self.assertEqual(fixture.receipt['controls_after_sha256'], APPROVED)
                self.assertEqual(fixture.receipt['owned_processes_remaining'], [])
                self.assertEqual(fixture.receipt['unknown_holders'], [])
                self.assertEqual(fixture.receipt['secondary_attestation_errors'], [])
                fixture.owner.secondary.dump.assert_called_once_with()
                self.assertLess(fixture.trace.index('finish'), fixture.trace.index('final-census'))
                # Synthetic wrapper behavior never upgrades the failed application scope.
                self.assertEqual(fixture.receipt['status'], 'FAIL')


class LifecycleCompositionClosureIntegration(unittest.TestCase):
    def test_original_strict_runner_and_ownership_fifo_controls_are_immutable(self):
        expected = {
            SUPPORT / 'run_ios_readiness.py': '85283be9681be14c18b76ef1657b596e837b4f97f978917f25ac693ae72d0ca8',
            SUPPORT / 'owned_lane.py': '949091403ac669429f962120e9873a09897a87bdcae10702720ede0c68342f03',
            COMPANION.parent / 'owned_lane.py': '949091403ac669429f962120e9873a09897a87bdcae10702720ede0c68342f03',
            SUPPORT / 'secondary_fifo.py': '170586ec941b02866acfa4b8e54aa3dd0842dbc7bc9c4b50410bca4b1149440a',
            COMPANION.parent / 'secondary_fifo.py': '170586ec941b02866acfa4b8e54aa3dd0842dbc7bc9c4b50410bca4b1149440a',
        }
        for path, pinned in expected.items():
            with self.subTest(path=str(path.relative_to(ROOT))):
                self.assertFalse(path.is_symlink())
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), pinned)

    def test_companion_composed_and_normal_manifests_bind_helper_and_both_test_files(self):
        binding = ROOT / 'AGENTS.md'  # Existing bytes for manifest shape only; never an execution binding.
        digest_path = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
        required = {HELPER, SUPPORT / 'test_simulator_lifecycle.py', Path(__file__).resolve()}
        companion = dict(ROOT=ROOT, HERE=COMPANION.parent, BINDING=binding, digest=digest_path)
        constants(COMPANION, {'TOOLCHAIN_HELPER', 'LIFECYCLE_HELPER', 'LIFECYCLE_TESTS'}, companion)
        function(COMPANION.read_text(), 'control_files', companion)
        function(COMPANION.read_text(), 'control_manifest', companion)
        composition = composition_namespace()
        generated = composition['render_source'](COMPANION.read_text())
        composed = dict(BINDING=binding, ROOT=ROOT, digest=digest_path,
                        foundation=SimpleNamespace(control_files=composition['control_files']))
        function(generated, 'control_files', composed)
        function(generated, 'control_manifest', composed)
        normal = dict(ROOT=ROOT, HERE=NORMAL.parent, SUPPORT=SUPPORT, digest=digest_path)
        constants(NORMAL, {'DARWIN'}, normal)
        function(NORMAL.read_text(), 'control_manifest', normal)
        for name, rows in (('companion', companion['control_manifest']()),
                           ('composed', composed['control_manifest']()),
                           ('normal', normal['control_manifest'](binding))):
            with self.subTest(manifest=name):
                actual = {ROOT / row['path']: row['sha256'] for row in rows}
                self.assertEqual(len(actual), len(rows))
                self.assertTrue(required <= actual.keys())
                for path in required:
                    self.assertEqual(actual[path], digest_path(path))

    def test_composition_preserves_actual_lifecycle_and_primary_error_wrappers(self):
        companion, composed = COMPANION.read_text(), source_for('composed')
        for name in ('command', 'simulator_command', 'own_simulator_metadata', 'shutdown_owned_device',
                     'delete_owned_device', 'stop_gradle'):
            with self.subTest(function=name):
                self.assertEqual(ast.dump(unique_function(companion, name)), ast.dump(unique_function(composed, name)))
        # Foundation remains additive and cannot silently replace the new owned control closure.
        self.assertIn('foundation.control_files(BINDING)', composed)
        self.assertIn("original_strict_l08='UNCHANGED_NOT_SATISFIED'", COMPOSITION.read_text())


if __name__ == '__main__':
    unittest.main()
