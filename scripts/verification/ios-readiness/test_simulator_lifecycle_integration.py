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
import tempfile
import types
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, Mock


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
                execute_nodes([main], namespace)
                values = namespace['main']()
                self.assertEqual(values['lifecycle_mode'], expected)
                self.assertEqual(values['toolchain_name'], profile)
                self.assertEqual(values['mode'], 'adhoc' if flags else 'disabled')
                receipt_assignments = [node for node in ast.walk(ast.parse(COMPANION.read_text()))
                    if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == 'receipt'
                                                          for target in node.targets)]
                self.assertEqual(len(receipt_assignments), 1)
                namespace.update(values, now=lambda: 'synthetic-time')
                execute_nodes(receipt_assignments, namespace)
                self.assertEqual(namespace['receipt']['simulator_lifecycle_mode'], expected)
                self.assertIs(namespace['receipt']['build_attempted'], False)

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
                    self.assertIn(target, ('command', 'require'))
                    generic = Mock(return_value=0)
                    lifecycle = SimpleNamespace(command=Mock(side_effect=AssertionError('Nonlifecycle direct route')))
                    namespace = dict(command=generic, uuid=DEVICE, APP_ID='com.parlor.app.debug',
                        stdout=Path('/synthetic-owned/app.stdout'), stderr=Path('/synthetic-owned/app.stderr'),
                        self=SimpleNamespace(uuid=DEVICE, require=generic, command=generic, lifecycle=lifecycle))
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
        receiver = SimpleNamespace(lifecycle=lifecycle, receipt=receipt, command=command, stop_gradle=stop,
            temporary=namespace['temp'], uuid=DEVICE, environment=namespace['env'], signing={}, save=namespace['save'])
        namespace['self'] = receiver
        with self.assertRaises(OSError) as caught:
            execute_nodes(build_block(source_for(flavor)), namespace)
        self.assertIs(caught.exception, primary)
        self.assertIs(receipt['build_attempted'], True)
        self.assertIs(receiver.gradle_attempted if flavor == 'normal' else namespace['gradle_attempted'], True)
        stop.assert_called_once_with('stop-xcode-immediate')
        if marker_error:
            command.assert_not_called()
            self.assertEqual(trace, ['save', 'mark', 'stop-xcode-immediate'])
        else:
            command.assert_called_once()
            self.assertEqual(trace, ['save', *(['mark'] if direct else []), 'builder', 'stop-xcode-immediate'])

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

    def companion_ack(self, flavor, *, exits=None, failure=None):
        nodes = [node for node in ast.walk(ast.parse(source_for(flavor))) if isinstance(node, ast.If) and
                 has_literal(node.test, 'owned-container.log')]
        self.assertEqual(len(nodes), 1)
        primary = OSError('synthetic-retention-or-ack-save-failure')
        receipt = dict(postbuild_evidence_preserved=False, runtime_evidence_status='RUNNING',
                       xcresult_extraction_exit_codes={'summary': 0, 'tests': 0} if exits is None else exits)

        def save():
            if failure == 'save' and receipt['postbuild_evidence_preserved'] is True:
                raise primary

        def copy_file(source, destination):
            if failure in {'raw-copy', 'native-copy'}:
                raise primary
            destination.write_bytes(source.read_bytes())

        with tempfile.TemporaryDirectory(prefix='parlor-lifecycle-retention-') as raw:
            root = Path(raw).resolve()
            home = root / 'owned-home'
            container = home / 'Library/Developer/CoreSimulator/Devices' / DEVICE / 'data'
            (container / 'tmp').mkdir(parents=True)
            evidence = root / 'owned-evidence'
            evidence.mkdir()
            (evidence / 'owned-container.log').write_text(str(container))
            if failure == 'raw-copy':
                (container / 'tmp/parlor-dsc01-settings-result.json').write_bytes(b'{}')
            if failure == 'native-copy':
                (container / 'tmp/parlor-native-readiness-boot-1.json').write_bytes(b'{}')

            class SyntheticPath:
                def __new__(cls, value):
                    return Path(value)

                @staticmethod
                def home():
                    return home

            namespace = dict(Path=SyntheticPath, command=Mock(return_value=1 if failure == 'container-command' else 0),
                uuid=DEVICE, APP_ID='com.parlor.app.debug', dest=evidence, receipt=receipt, save=Mock(side_effect=save),
                shutil=SimpleNamespace(copyfile=Mock(side_effect=copy_file)), defer_parent_signals=nullcontext,
                preserve_l08_evidence=Mock(side_effect=primary if failure == 'storage' else None, return_value=[]),
                preserve_functional_evidence=Mock(side_effect=primary if failure == 'functional' else None, return_value=[]),
                foundation=SimpleNamespace(preserve=Mock(side_effect=primary if failure == 'foundation' else None)))
            if failure is not None and failure != 'container-command':
                with self.assertRaises(OSError) as caught:
                    execute_nodes(nodes, namespace)
                self.assertIs(caught.exception, primary)
            else:
                execute_nodes(nodes, namespace)
        self.assertIs(receipt['postbuild_evidence_preserved'],
                      failure is None and receipt['xcresult_extraction_exit_codes'] == {'summary': 0, 'tests': 0})
        self.assertNotEqual(receipt['runtime_evidence_status'], 'PASS')

    def test_companion_ack_requires_closed_extraction_pair_and_completed_retention_path(self):
        for flavor in ('companion', 'composed'):
            for exits in ({'summary': 0, 'tests': 0}, {}, {'summary': 0}, {'summary': 0, 'tests': 1},
                          {'summary': 0, 'tests': 0, 'unreviewed': 0}):
                with self.subTest(flavor=flavor, exits=exits):
                    self.companion_ack(flavor, exits=exits)
            failures = ['container-command', 'raw-copy', 'storage', 'functional', 'native-copy', 'save']
            if flavor == 'composed':
                failures.append('foundation')
            for failure in failures:
                with self.subTest(flavor=flavor, failure=failure):
                    self.companion_ack(flavor, failure=failure)


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
