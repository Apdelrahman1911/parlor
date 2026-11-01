#!/usr/bin/env python3
"""Explicit source-copy native investigation; never a production qualification.

One attempt per original/Compose/native arm, identical existing EN/AR journeys.
No retry, alternate selector, permission grant, release build or shipping edit.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import plistlib
import re
import shlex
import signal
import stat
import subprocess
import sys
import tarfile
import time
import uuid

import pin01_copy
import pin01_receipts
import pin02_capture
import pin03_catalog_copy as catalog
import pin04_original_host as original_host
import pin06_public_setup_pair as setup_pair
import public_attachments

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.ci import apple_verification_hygiene as apple
from scripts.ci import owned_ci_simulator as simulator
from scripts.ci.native_process_probe import Commands

PREFIXES = ('composeApp/', 'shared/', 'game-modes/', 'build-logic/', 'gradle/',
            'config/', 'iosApp/', 'scripts/')
ROOTS = ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')
TESTS = tuple('iosAppUITests/IOSAppLaunchUITests/' + name for name in (
    'testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish',
    'testAdditionalGamesExposeOnlyMultiplayerSetupInArabic'))


def diagnostic_environment(inherited, registry, temporary):
    """Uniform bounded copy-only build profile; retain runner ownership context."""
    options = [
        '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process',
        '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC',
        '-Dorg.gradle.workers.max=1', '-Dorg.gradle.parallel=false',
        '-Dorg.gradle.daemon.registry.base=' + str(registry),
        '-Djava.io.tmpdir=' + str(temporary),
    ]
    return dict(inherited, GRADLE_OPTS=shlex.join(options))


def build_command(base):
    """Retain actual compiler stages; preserve the original 45-minute build budget."""
    return [argument for argument in base if argument != '-quiet'] + [
        '-jobs', '1', '-showBuildTimingSummary', 'build-for-testing']


def failed_method_exports(result, destination, outcomes):
    """Select complete exact failed-test attachments, including pre-assertion ACKs."""
    allowed = {test.split('/', 1)[1] + '()' for test in TESTS}
    # Use the already verified case outcomes, not assertion records: one failed
    # case can legitimately contain several assertions with the same identifier.
    if not isinstance(outcomes, dict) or set(outcomes) != allowed or \
            any(value not in ('Passed', 'Failed') for value in outcomes.values()):
        raise ValueError('Exact declared test case outcomes required')
    methods = sorted(method for method, outcome in outcomes.items() if outcome == 'Failed')
    if not methods:
        raise ValueError('Only failed test cases are eligible')
    return [(destination / ('failed-%02d' % (index + 1)),
             ['export', 'attachments', '--path', str(result), '--output-path',
              str(destination / ('failed-%02d' % (index + 1))), '--test-id', method])
            for index, method in enumerate(methods)]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def selected(path):
    parsed = PurePosixPath(path)
    if parsed.is_absolute() or '..' in parsed.parts or str(parsed) != path:
        raise ValueError('Unsafe archive path')
    if path not in ROOTS and not path.startswith(PREFIXES):
        return False
    if any(p in ('.git', '.gradle', 'build') for p in parsed.parts) or \
            path.endswith(('.pem', '.p12', '.jks', '.keystore', '.mobileprovision', 'local.properties')):
        raise ValueError('Unapproved build/archive input')
    return True


def materialize(raw, destination):
    """Extract regular bound build inputs only into a fresh caller-owned directory."""
    if destination.is_symlink() or not destination.is_dir() or any(destination.iterdir()):
        raise ValueError('Fresh empty source-copy destination required')
    manifest = {}
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:') as archive:
        for entry in archive:
            if entry.isdir():
                continue
            if not selected(entry.name):
                continue
            if not entry.isfile() or entry.name in manifest or entry.size > 64 * 1024 * 1024:
                raise ValueError('Nonregular, duplicate or oversized archive input')
            target = destination / entry.name
            target.parent.mkdir(parents=True, exist_ok=True)
            content = archive.extractfile(entry).read()
            with target.open('xb') as output:
                output.write(content)
            target.chmod(0o700 if entry.mode & 0o111 else 0o600)
            manifest[entry.name] = sha(content)
    if not set((*ROOTS, *catalog.MODIFIED)).issubset(manifest):
        raise ValueError('Required original application/driver/build source missing')
    return manifest


def generated_directories(manifest):
    """Only frozen declared project outputs, never a name-based source exclusion."""
    outputs = set()
    for name in manifest:
        path = PurePosixPath(name)
        if path.name in ('build.gradle.kts', 'build.gradle', 'settings.gradle.kts', 'settings.gradle'):
            outputs.update(str(path.parent / leaf) for leaf in ('build', '.gradle', '.kotlin'))
        for parent in path.parents:
            if parent.name.endswith(('.xcodeproj', '.xcworkspace')):
                outputs.add(str(parent / 'xcuserdata'))
    return outputs


def verify_inputs(copy, manifest):
    for path, digest in manifest.items():
        source = copy / path
        if source.is_symlink() or source.resolve() != source.absolute() or sha(source.read_bytes()) != digest:
            raise ValueError('Copied build input changed or redirected')
    actual = set()
    outputs = generated_directories(manifest)
    def unreadable(error):
        raise error
    for directory, dirs, names in os.walk(copy, followlinks=False, onerror=unreadable):
        retained = []
        for name in dirs:
            path = Path(directory) / name
            if path.is_symlink():
                raise ValueError('Redirected source directory')
            if str(path.relative_to(copy)) in outputs:
                continue
            retained.append(name)
        dirs[:] = retained
        for name in names:
            path = Path(directory) / name
            relative = str(path.relative_to(copy))
            if selected(relative):
                if path.is_symlink():
                    raise ValueError('Redirected source input')
                actual.add(relative)
    if actual != set(manifest):
        raise ValueError('Unregistered or missing copied build input')


def apply_arm(copy, original, manifest, arm, *, previous, renderer=catalog.render):
    verify_inputs(copy, previous)
    plan = renderer(original, {p: manifest[p] for p in original}, arm=arm)
    if arm == 'original' and (copy / pin01_copy.ADDITION).exists():
        raise ValueError('Original arm cannot reuse an observed source copy')
    for path, content in plan.items():
        target = copy / path
        if path not in manifest and target.exists() and path not in previous:
            raise ValueError('Unexpected existing observer source')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
    return manifest | {p: sha(content.encode()) for p, content in plan.items()}


def read_capture(path, *, maximum=pin02_capture.MAX_BYTES):
    # Legacy PIN02 remains unchanged. Explicit public-only companions use the
    # reviewed 64KiB attachment, 512KiB input-delivery, 1MiB motion or 2MiB native ceilings;
    # arbitrary bounds reject. Each decoder keeps its own exact lower bound.
    if type(maximum) is not int or maximum not in (pin02_capture.MAX_BYTES, 65_536, 524_288, 1_048_576, 2_097_152):
        raise ValueError('Exact legacy, motion or native companion read ceiling required')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or before.st_uid != os.getuid() or \
                not 0 < before.st_size <= maximum:
            raise ValueError('Unsafe or oversized owned capture')
        chunks, size = [], 0
        while True:
            part = os.read(fd, min(8192, maximum + 1 - size))
            if not part:
                break
            chunks.append(part); size += len(part)
            if size > maximum:
                raise ValueError('Capture exceeded bound during retention')
        after = os.fstat(fd)
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != \
                (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns) or size != before.st_size:
            raise ValueError('Capture changed during retention')
        return b''.join(chunks)
    finally:
        os.close(fd)


def verify_results(summary, tests, simulator_id, code):
    if any(type(summary.get(k)) is not int or not 0 <= summary[k] <= 2 for k in
           ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures')):
        raise ValueError('Exact bounded XCTest counts required')
    if summary.get('totalTestCount') != 2 or summary.get('skippedTests') != 0 or \
            summary.get('expectedFailures') != 0 or \
            summary.get('passedTests', -1) + summary.get('failedTests', -1) != 2:
        raise ValueError('Exactly two original unskipped journeys required')
    configurations, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    if len(configurations) != 1 or len(devices) != 1 or any(
        p.get('deviceId') != simulator_id or p.get('architecture') != 'arm64' or
        p.get('platform') != 'iOS Simulator' for p in (configurations[0].get('device', {}), devices[0])):
        raise ValueError('Test result does not belong exclusively to the owned ARM64 Simulator')
    cases = []
    def visit(nodes):
        for node in nodes:
            if node.get('nodeType') == 'Test Case':
                cases.append(node)
            visit(node.get('children', []))
    visit(tests.get('testNodes', []))
    expected = {t.removeprefix('iosAppUITests/') + '()' for t in TESTS}
    if len(cases) != 2 or {p.get('nodeIdentifier') for p in cases} != expected or \
            any(p.get('result') not in ('Passed', 'Failed') for p in cases):
        raise ValueError('Original journey identifiers or executed results missing')
    passed = sum(p['result'] == 'Passed' for p in cases)
    if passed != summary['passedTests'] or (code == 0) != (passed == 2) or \
            summary.get('result') != ('Passed' if passed == 2 else 'Failed'):
        raise ValueError('Command, summary and actual case outcomes disagree')
    return {p['nodeIdentifier']: p['result'] for p in cases}


def test_command(base, result):
    """One ordinary run; Xcode rejects an explicit iteration count of one."""
    return [*base, 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:' + t for t in TESTS)]


def result_label(arm, kind):
    if arm not in catalog.ARMS or kind not in ('summary', 'tests'):
        raise ValueError('Explicit diagnostic arm and result decoder required')
    return arm + '-result-' + kind


def decode_capture(raw, tokens, simulator_id, bundle):
    if len(raw) > pin02_capture.MAX_BYTES:
        raise ValueError('Oversized capture')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite capture')))
    if type(value) is not dict or set(value) != pin02_capture.KEYS:
        raise ValueError('Unknown/private capture fields')
    if type(value['token']) is not str or value['token'] not in tokens or \
            not pin02_capture.UUID.fullmatch(value['token']):
        raise ValueError('Capture without a retained failed-request token')
    result = catalog.decode_snapshot(json.dumps(value['snapshot'], allow_nan=False).encode())
    # Validate original envelope independently; only the declared snapshot
    # catalog enums differ from PIN02. Never loosen its production/test decoder.
    translated = dict(value, snapshot={'schema': 1, 'nativeMode': value['snapshot']['nativeMode'],
        'overflow': 0, 'invalid': 0, 'rows': [[0, 4, 0, 0, 0, 0, 0, 0, 0, 0]]})
    pin02_capture.decode_capture(json.dumps(translated, allow_nan=False).encode(),
        token=value['token'], simulator=simulator_id, bundle=bundle)
    if any(row[0] > value['capturedMs'] for row in value['snapshot']['rows']):
        raise ValueError('Observation newer than capture')
    return dict(result, capture_sha256=sha(raw), token=value['token'])


class Backend:
    def __init__(self, commands, runtime, device):
        self.commands, self.runtime, self.device = commands, runtime, device

    def run(self, *args):
        timeout = 120 if args[0] in ('bootstatus', 'list') else 90
        row, output, _ = self.commands.capture(['/usr/bin/xcrun', 'simctl', *args],
                                               'simctl-' + args[0], timeout)
        if row['status'] != 'EXITED' or not row.get('direct_child_reaped'):
            raise RuntimeError('simctl command did not complete')
        return row['exit_code'], output.decode()

    def inventory(self):
        code, raw = self.run('list', 'devices', '--json')
        if code:
            raise RuntimeError('Simulator inventory failed')
        value = json.loads(raw)
        entries = simulator.devices(value)
        if not any(p['runtime'] == self.runtime and p.get('deviceTypeIdentifier') == self.device and
                   p.get('isAvailable') is True for p in entries):
            raise RuntimeError('Explicit runtime/device type is unavailable; no fallback')
        groups = value['devices']
        # Only order changes, never omit pre-existing identities from ownership.
        groups[self.runtime] = sorted(groups[self.runtime],
            key=lambda p: p.get('deviceTypeIdentifier') != self.device)
        value['devices'] = {self.runtime: groups[self.runtime],
                           **{k: v for k, v in groups.items() if k != self.runtime}}
        return value


def renderer_for_profile(profile, source, runtime, device):
    if profile == 'catalog':
        return catalog.render
    if profile == 'original-host' and (source, runtime, device) == (
            original_host.FROZEN_SOURCE, original_host.RUNTIME, original_host.DEVICE):
        return original_host.render
    if profile == 'public-setup-pair' and (source, runtime, device) == (
            setup_pair.SOURCE, setup_pair.RUNTIME, setup_pair.DEVICE):
        return setup_pair.render
    raise ValueError('Original Host investigation requires its exact frozen source/runtime/device; no fallback')


def completed_exit_code(result):
    """Only completely accepted diagnostic profiles may exit zero.

    This does not turn a retained failed application journey into qualification.
    Unknown, incomplete and production-qualification labels always fail closed.
    """
    accepted = (
        'SCOPED_INVESTIGATION_COMPLETE', 'SYNTHETIC_FAILED_EXPORT_CAPTURE_ONLY',
        'CURRENT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'SYNTHETIC_NATIVE_ACCESSIBILITY_CAPABILITY_ONLY',
        'NATIVE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'SYNTHETIC_NATIVE_ROOT_CAPABILITY_ONLY',
        'ROOT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'PUBLIC_FAILURE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_FULL_COHORT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_RESOURCE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'COMBINED_XCODE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_AGGREGATE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'PAIRED_OBSERVER_CONTEXT_COMPLETE_NOT_QUALIFICATION',
        'SYNTHETIC_ROOT_IDLE_STIMULUS_COMPLETE_NOT_QUALIFICATION',
        'SETTLED_CATALOG_IDLE_STIMULUS_COMPLETE_NOT_QUALIFICATION',
        'PUBLIC_CATALOG_MOTION_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_CATALOG_MOTION_EXPORT_COMPLETE_NOT_QUALIFICATION',
        'NATIVE_FRAME_RETURN_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'PUBLIC_NAME_TEXT_SESSION_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_PUBLIC_TEXT_SESSION_EXPORT_COMPLETE_NOT_QUALIFICATION',
        'PUBLIC_NAME_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_NATIVE_INPUT_DELIVERY_EXPORT_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_ATTACHED_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_INPUT_LIFECYCLE_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'FAILURE_ONLY_EQUIVALENT_QUERY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'FIXED_PUBLIC_SESSION_STRESS_COMPLETE_NOT_QUALIFICATION',
        'CATALOG_INPUT_BOUNDARY_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_OVERSCROLL_GETTER_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_NATIVE_TOUCH_DELIVERY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_ORIGINAL_NATIVE_TOUCH_EXPORT_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_TOUCH_FULL_COHORT_CONTEXT_COMPLETE_NOT_QUALIFICATION',
        'ISOLATED_PUBLIC_SCROLL_EXPORT_COMPLETE_NOT_QUALIFICATION',
        'ORIGINAL_PUBLIC_SCROLL_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION',
        'FIXED_PUBLIC_SCROLL_SAMPLES_COMPLETE_NOT_QUALIFICATION',
    )
    return 0 if type(result) is str and result in accepted else 1


def main(*, synthetic=False, current=False, accessibility=False, native_boundary=False,
         root_capability=False, root_boundary=False, public_boundary=False, original_cohort=False, original_resources=False, combined_execution=False, aggregate_context=False, observer_comparison=False, root_idle=False, settled_idle=False, public_motion=False, motion_control=False, frame_boundary=False, text_boundary=False, text_export_control=False, native_input_delivery=False, native_input_export_control=False, attached_input_delivery=False, original_input_lifecycle=False, equivalent_query=False, public_session_stress=False, catalog_input_boundary=False, overscroll_getter=False, original_touch_delivery=False, original_touch_export_control=False, original_touch_cohort=False, public_scroll_export=False, public_scroll_natural=False, public_scroll_samples=False):
    # The separate PIN07 entry point can select ONE known failed-export control.
    # Ordinary profiles, methods and success criteria are unchanged.
    if sum((synthetic, current, accessibility, native_boundary, root_capability, root_boundary, public_boundary, original_cohort, original_resources, combined_execution, aggregate_context, observer_comparison, root_idle, settled_idle, public_motion, motion_control, frame_boundary, text_boundary, text_export_control, native_input_delivery, native_input_export_control, attached_input_delivery, original_input_lifecycle, equivalent_query, public_session_stress, catalog_input_boundary, overscroll_getter, original_touch_delivery, original_touch_export_control, original_touch_cohort, public_scroll_export, public_scroll_natural, public_scroll_samples)) > 1:
        raise ValueError('Exactly one explicit copy-only entry point required')
    if synthetic:
        import pin07_native_adapter as control
    elif current:
        import current_boundary_adapter as control
    elif accessibility:
        import pin08_accessibility as control
    elif native_boundary:
        import pin09_native_boundary as control
    elif root_capability:
        import pin10_root_boundary as control
    elif root_boundary:
        import pin11_root_observations as control
    elif public_boundary:
        import pin12_public_boundaries as control
    elif original_cohort:
        import pin13_original_cohort as control
    elif original_resources:
        import pin14_original_resources as control
    elif combined_execution:
        import pin15_combined_xcode as control
    elif aggregate_context:
        import pin16_aggregate_context as control
        combined_execution = True  # Reuse the unchanged single native action.
    elif public_scroll_samples:
        import pin38_public_scroll_samples as control
        public_motion = root_capability = True
    elif public_scroll_natural:
        import pin37_public_scroll_natural as control
        public_motion = root_capability = True
    elif public_scroll_export:
        import pin36_public_scroll_boundary as control
        public_motion = root_capability = True
    elif original_touch_cohort:
        import pin35_original_touch_cohort as control
        combined_execution = public_motion = True
    elif original_touch_export_control:
        import pin34_touch_export_control as control
        public_motion = root_capability = True
    elif original_touch_delivery:
        import pin33_original_touch_delivery as control
        public_motion = root_capability = True
    elif overscroll_getter:
        import pin32_catalog_overscroll_boundary as control
        public_motion = root_capability = True
    elif catalog_input_boundary:
        import pin31_catalog_input_boundary as control
        public_motion = root_capability = True
    elif public_session_stress:
        import pin30_public_session_stress as control
        public_motion = root_capability = True
    elif equivalent_query:
        import pin29_equivalent_query_boundary as control
        public_motion = root_capability = True
    elif original_input_lifecycle:
        import pin28_original_lifecycle_boundaries as control
        public_motion = root_capability = True
    elif attached_input_delivery:
        import pin27_attached_input_delivery as control
        public_motion = root_capability = True
    elif native_input_export_control:
        import pin26_delivery_export_control as control
        public_motion = root_capability = True
    elif native_input_delivery:
        import pin25_native_input_delivery as control
        public_motion = root_capability = True
    elif text_export_control:
        import pin24_text_export_control as control
        public_motion = root_capability = True
    elif text_boundary:
        import pin23_text_session_boundary as control
        public_motion = root_capability = True
    elif frame_boundary:
        import pin22_native_frame_boundary as control
        public_motion = root_capability = True
    elif motion_control:
        import pin21_motion_export_control as control
        public_motion = root_capability = True
    elif public_motion:
        import pin20_catalog_motion as control
        root_capability = True
    elif settled_idle:
        import pin19_settled_catalog_idle as control
        root_capability = True
    elif root_idle:
        import pin18_root_idle_stimulus as control
        root_capability = True  # All existing native/root companion checks remain.
    elif observer_comparison:
        import pin17_observer_comparison as control
        aggregate_context = combined_execution = True
    else:
        control = None
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--source', required=True)
    parser.add_argument('--runtime', required=True)
    parser.add_argument('--device', required=True)
    parser.add_argument('--profile', choices=(control.PROFILE,) if control else
                        ('catalog', 'original-host', 'public-setup-pair'),
                        required=bool(control), default=None if control else 'catalog')
    parser.add_argument('--output', type=Path, required=True)
    if public_session_stress or catalog_input_boundary or overscroll_getter or original_touch_delivery or original_touch_export_control or public_scroll_samples:
        parser.add_argument('--arm', choices=control.ARMS, required=True)
    args = parser.parse_args()
    renderer = control.renderer_for_profile(args.profile, args.source, args.runtime, args.device) if control else \
        renderer_for_profile(args.profile, args.source, args.runtime, args.device)
    selected_test_command = control.test_command if control else test_command
    selected_verify_results = control.verify_results if control else verify_results
    selected_exports = control.failed_method_exports if control else failed_method_exports
    if platform.system() != 'Darwin' or platform.machine() != 'arm64' or \
            not re.fullmatch('[0-9a-f]{40}', args.source) or \
            not re.fullmatch(r'com\.apple\.CoreSimulator\.SimRuntime\.iOS-[0-9]+-[0-9]+', args.runtime) or \
            not re.fullmatch(r'com\.apple\.CoreSimulator\.SimDeviceType\.iPhone-[A-Za-z0-9-]+', args.device):
        raise RuntimeError('Explicit frozen-source ARM64 Apple investigation only')
    temp = Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    out = args.output.absolute()
    if out.parent != temp or out.exists() or out.is_symlink():
        raise ValueError('Fresh direct child of runner-owned temporary directory required')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip():
        raise RuntimeError('Harness checkout must be clean')
    xcode = subprocess.check_output(['/usr/bin/xcodebuild', '-version']).decode()
    if xcode != 'Xcode 26.3\nBuild version 17C529\n':
        raise RuntimeError('Exact Xcode26.3/17C529 required; no compiler fallback')
    sdk = subprocess.check_output(['/usr/bin/xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version']).decode().strip()
    if sdk != '26.2':
        raise RuntimeError('Actual Simulator SDK differs from qualified compiler profile')
    config = apple.configuration(ROOT)
    source_tree = subprocess.check_output(['git', 'rev-parse', args.source + '^{tree}'], cwd=ROOT).decode().strip()
    if (control and source_tree != control.TREE) or \
            (not control and args.profile == 'public-setup-pair' and source_tree != setup_pair.TREE):
        raise ValueError('Original failed public setup tree differs; no fallback')
    out.mkdir(mode=0o700)
    copy = out / 'source'; copy.mkdir(mode=0o700)
    archive = subprocess.check_output(['git', 'archive', args.source, *ROOTS,
                                      *(p.rstrip('/') for p in PREFIXES)], cwd=ROOT)
    manifest = materialize(archive, copy)
    original = {p: (copy / p).read_text() for p in catalog.MODIFIED}
    bundle = json.loads((copy / 'config/release-policy.json').read_text())['applications']['ios']['debug_bundle_id']
    if not bundle.endswith('.debug'):
        raise ValueError('Debug bundle only')
    state = dict(source=args.source, source_tree=source_tree, profile=args.profile,
                 harness=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                 archive_sha256=sha(archive), runtime=args.runtime, device=args.device, xcode=xcode, sdk=sdk,
                 debug_bundle=bundle,
                 original_inputs=manifest, arms=[], result='NOT_QUALIFIED',
                 scope='Existing EN/AR additional-game journeys only, not 60-journey neutrality, original iOS26.2 cause, production CI or physical acceptance')
    if control:
        state['scope'] = control.SCOPE
    registry = out / 'registry'; registry.mkdir(); jtmp = out / 'jvm-tmp'; jtmp.mkdir()
    environment = control.build_environment(os.environ, registry, jtmp) if original_resources or combined_execution else \
        diagnostic_environment(os.environ, registry, jtmp)
    state['build_profile'] = dict(gradle_heap_bytes=3 * 1024**3, gradle_workers=1,
        gradle_parallel=False, kotlin_compiler='in-process', xcode_jobs=1,
        verbose_build=True, boot_after_build=True, build_timeout_seconds=2700,
        test_timeout_seconds=900, scope='Uniform copy-only resource profile; '
        'not a shipping build change, timeout extension or device benchmark')
    if original_resources or combined_execution:
        state['build_profile'].update(gradle_heap_bytes=6 * 1024**3, gradle_workers='original-default',
            gradle_parallel=True, xcode_jobs='original-default', boot_after_build=False,
            scope=control.SCOPE)
    if combined_execution:
        state['build_profile'].update(combined_xcode=True, combined_timeout_seconds=3600,
            product_manifest_stage='after-combined-command')
    def checkpoint():
        partial = out / 'state.writing'
        partial.write_text(json.dumps(state | {'commands': commands.rows}, indent=2))
        partial.replace(out / 'state.json')
    if public_session_stress or catalog_input_boundary or overscroll_getter or original_touch_delivery or original_touch_export_control or public_scroll_samples:
        state['build_profile']['test_timeout_seconds'] = control.TEST_TIMEOUT_SECONDS
        state['build_profile']['scope'] = control.SCOPE
    commands = Commands(environment, time.monotonic() + 3 * 3600, checkpoint)
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, commands.interrupted)
    backend = Backend(commands, args.runtime, args.device)
    task = {key: os.environ[key] for key in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB')}
    def command(argv, label, timeout=120, log=None, env=None):
        actual_env = env or environment
        if log:
            actual_env = actual_env | {'PARLOR_INPUT_LOG': str(log)}
            argv = ['/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--', *map(str, argv)]
        row, stdout, _ = commands.capture(argv, label, timeout, root=copy, environment=actual_env)
        checkpoint()
        if log and log.exists() and log.stat().st_size > 64 * 1024 * 1024:
            raise RuntimeError('Build/test log exceeds investigation bound')
        if row['status'] != 'EXITED' or not row.get('direct_child_reaped'):
            raise RuntimeError('Investigation command did not finish')
        return row['exit_code'], stdout
    previous = manifest
    if aggregate_context:
        # Restore the omitted workload using the ORIGINAL sources before adding
        # observers. Never execute instrumented KMP tests or replay for caching.
        verify_inputs(copy, manifest)
        control.execute_prelude(copy, out, state, command, checkpoint, ROOT, task, config, environment, commands)
        verify_inputs(copy, manifest)
    arms = (args.arm,) if public_session_stress or catalog_input_boundary or overscroll_getter or original_touch_delivery or original_touch_export_control or public_scroll_samples else control.ARMS if control else catalog.ARMS
    for arm in arms:
        dest = out / arm; dest.mkdir()
        inputs = apply_arm(copy, original, manifest, arm, previous=previous, renderer=renderer)
        previous = inputs
        verify_inputs(copy, inputs)
        (dest / 'inputs.json').write_text(json.dumps(inputs, indent=2))
        prefix = dest / 'resources'
        claim = apple.prepare(ROOT, prefix, task, 'apple-ui', apple.DarwinWorkerBackend(), config)
        row = dict(arm=arm, result='NOT_RUN', cleanup='NOT_RUN'); state['arms'].append(row)
        checkpoint()
        simulator_id = None
        try:
            simulator_id = simulator.create(prefix, claim, backend)
            row['simulator'] = simulator_id
            base = ['/usr/bin/xcodebuild', '-quiet', '-project', 'iosApp/iosApp.xcodeproj',
                    '-scheme', 'iosApp', '-configuration', 'Debug', '-sdk', 'iphonesimulator',
                    '-destination', 'platform=iOS Simulator,id=' + simulator_id,
                    '-derivedDataPath', str(out / 'derived'), '-parallel-testing-enabled', 'NO',
                    '-maximum-concurrent-test-simulator-destinations', '1']
            if original_resources or combined_execution:
                for operation in (('boot', simulator_id), ('bootstatus', simulator_id, '-b')):
                    if backend.run(*operation)[0]:
                        raise RuntimeError('Original booted-build Simulator unavailable')
                row['kernel_resources'] = {'before-build': control.observe_resources(command, dest, 'before-build')}
            result = dest / 'tests.xcresult'
            if combined_execution:
                # A distinct causal profile uses the original single Xcode build/test
                # process. No first build, post-link pause, or second test invocation.
                base += ['-destination-timeout', '120']
                code, _ = command(selected_test_command(base, result),
                    arm + '-build-and-tests', 3600, dest / 'test.log',
                    environment | {'TEST_RUNNER_PARLOR_PIN02_BUNDLE': bundle})
                row.update(combined_exit=code, test_exit=code,
                    result='JOURNEYS_PASSED' if code == 0 else 'JOURNEYS_FAILED',
                    product_manifest_stage='after-combined-command')
                row['kernel_resources']['after-tests'] = control.observe_resources(command, dest, 'after-tests')
                verify_inputs(copy, inputs)
                if code not in (0, 65):
                    row['result'] = 'COMBINED_COMMAND_FAILED'
                    break
            else:
                selected_build = control.build_command(base) if original_resources else build_command(base)
                code, _ = command(selected_build, arm + '-build', 2700, dest / 'build.log')
                if original_resources:
                    row['kernel_resources']['after-build'] = control.observe_resources(command, dest, 'after-build')
                row['build_exit'] = code
                verify_inputs(copy, inputs)
                if code:
                    row['result'] = 'BUILD_FAILED'
                    break
            products = out / 'derived/Build/Products/Debug-iphonesimulator'
            apps = [p for p in products.glob('*.app') if (p / 'Info.plist').is_file() and
                    plistlib.loads((p / 'Info.plist').read_bytes()).get('CFBundleIdentifier') == bundle]
            if len(apps) != 1:
                raise RuntimeError('Exact built Debug application missing or ambiguous')
            product = {str(p.relative_to(apps[0])): sha(p.read_bytes()) for p in apps[0].rglob('*')
                       if p.is_file() and not p.is_symlink()}
            (dest / 'product.json').write_text(json.dumps(product, indent=2))
            info = plistlib.loads((apps[0] / 'Info.plist').read_bytes())
            executable = apps[0] / info['CFBundleExecutable']
            rc, archs = command(['/usr/bin/xcrun', 'lipo', '-archs', str(executable)], arm + '-binary-architecture')
            if rc or archs.decode().strip() != 'arm64':
                raise RuntimeError('Actual Debug wrapper is not the declared ARM64 image')
            row['product_manifest_sha256'] = sha((dest / 'product.json').read_bytes())
            # Keep the explicitly owned device shut down during linkage; Xcode
            # can build for that available destination without competing for RAM.
            if not (original_resources or combined_execution):
                for operation in (('boot', simulator_id), ('bootstatus', simulator_id, '-b')):
                    if backend.run(*operation)[0]:
                        raise RuntimeError('Owned Simulator boot failed')
            if not combined_execution:
                code, _ = command(selected_test_command(base, result),
                                  arm + '-tests', control.TEST_TIMEOUT_SECONDS if public_session_stress or catalog_input_boundary or overscroll_getter or original_touch_delivery or original_touch_export_control or public_scroll_samples else 900, dest / 'test.log',
                                  environment | {'TEST_RUNNER_PARLOR_PIN02_BUNDLE': bundle})
                row.update(test_exit=code, result='JOURNEYS_PASSED' if code == 0 else 'JOURNEYS_FAILED')
            if original_resources:
                row['kernel_resources']['after-tests'] = control.observe_resources(command, dest, 'after-tests')
            verify_inputs(copy, inputs)
            if any(not (apps[0] / p).is_file() or (apps[0] / p).is_symlink() or
                   sha((apps[0] / p).read_bytes()) != digest for p, digest in product.items()):
                raise ValueError('Original built application changed during native execution')
            for kind in ('summary', 'tests'):
                rc, raw = command(['/usr/bin/xcrun', 'xcresulttool', 'get', 'test-results', kind,
                                   '--path', str(result)], result_label(arm, kind))
                if rc:
                    raise RuntimeError('Original XCTest result decoding failed')
                (dest / (kind + '.json')).write_bytes(raw)
            row['methods'] = selected_verify_results(json.loads((dest / 'summary.json').read_bytes()),
                                            json.loads((dest / 'tests.json').read_bytes()), simulator_id, code)
            if combined_execution:
                # All23 actual executed native outcomes establish that compilation
                # succeeded. This is NOT a fabricated standalone build exit receipt.
                row['build_exit'] = 0
                row['build_success_evidence'] = 'all23-executed-native-outcomes'
            if public_session_stress and arm == 'original' and code:
                control.retain_baseline_failures(command, result, dest, row)
            if arm != 'original' and code:
                attachments = dest / 'attachments'; attachments.mkdir()
                rc, helptext = command(['/usr/bin/xcrun', 'xcresulttool', 'help', 'export', 'attachments'],
                                       arm + '-attachments-help')
                if rc:
                    raise RuntimeError('Installed attachment export help unavailable')
                public_attachments.verify_export_help(helptext)
                exports = selected_exports(result, attachments, row['methods'])
                row['attachment_test_ids'] = [argv[-1] for _, argv in exports]
                for index, (directory, argv) in enumerate(exports):
                    directory.mkdir()
                    rc, _ = command(['/usr/bin/xcrun', 'xcresulttool', *argv],
                                    arm + '-attachments-%02d' % (index + 1))
                    if rc:
                        raise RuntimeError('Exact failed-test attachments could not be retained')
                tokens = set()
                for path in attachments.rglob('*'):
                    if path.is_file() and not path.is_symlink() and path.stat().st_size <= 4096:
                        try:
                            request = json.loads(path.read_bytes())
                            if type(request) is dict and set(request) == {'schema', 'token', 'ackObserved', 'originalTransitionFailed'} and \
                                    type(request['schema']) is int and request['schema'] == 1 and \
                                    type(request['ackObserved']) is bool and request['originalTransitionFailed'] is True and \
                                    type(request['token']) is str and pin02_capture.UUID.fullmatch(request['token']):
                                tokens.add(request['token'])
                        except (ValueError, TypeError, UnicodeError):
                            pass
                rc, raw = command(['/usr/bin/xcrun', 'simctl', 'get_app_container', simulator_id, bundle, 'data'],
                                  arm + '-sandbox')
                if rc:
                    raise RuntimeError('Explicit owned Debug sandbox unavailable')
                sandbox = Path(raw.decode().strip()).resolve(strict=True)
                captures = []
                if accessibility or native_boundary or root_capability or root_boundary or public_boundary or original_cohort or original_resources or combined_execution:
                    row['companion_captures'] = []
                if root_capability or root_boundary or public_boundary or original_cohort or original_resources or combined_execution:
                    row['root_companion_captures'] = []
                if public_motion:
                    row['motion_companion_captures'] = []
                for token in sorted(tokens):
                    path = sandbox / 'tmp' / ('parlor-pin02-' + token) / 'capture.json'
                    if path.is_file():
                        if path.is_symlink() or path.resolve() != path.absolute() or \
                                path.stat().st_nlink != 1 or path.stat().st_size > pin02_capture.MAX_BYTES:
                            raise ValueError('Unsafe or oversized owned capture')
                        raw = read_capture(path)
                        decoded = decode_capture(raw, tokens, simulator_id, bundle)
                        (dest / ('capture-' + token + '.json')).write_bytes(raw)
                        captures.append(decoded)
                        if accessibility or native_boundary or root_capability or root_boundary or public_boundary or original_cohort or original_resources or combined_execution:
                            row['companion_captures'].append(control.retain_companion(
                                sandbox, dest, token, simulator_id, bundle, json.loads(raw)))
                        if root_capability or root_boundary or public_boundary or original_cohort or original_resources or combined_execution:
                            row['root_companion_captures'].append(control.retain_root_companion(
                                sandbox, dest, token, simulator_id, bundle, json.loads(raw)))
                        if public_motion:
                            row['motion_companion_captures'].append(control.retain_motion_companion(
                                sandbox, dest, token, simulator_id, bundle, json.loads(raw)))
                row['captures'] = captures
        except BaseException as error:
            row.update(result='INVESTIGATION_FAILED', error_type=type(error).__name__)
            raise
        finally:
            commands.finalizing = True
            cleanup = {}
            try:
                cleanup['gradle_exit'], _ = command([str(copy / 'gradlew'), '--stop',
                    '-Dorg.gradle.daemon.registry.base=' + str(registry), '--console=plain'],
                    arm + '-gradle-stop', 120, dest / 'stop.log')
                cleanup['workers_before'] = apple.stop_workers(claim, apple.DarwinWorkerBackend(),
                                                               environment['RUNNER_TRACKING_ID'])
                if cleanup['gradle_exit'] or cleanup['workers_before']['result'] != 'PASS':
                    raise RuntimeError('Workers not quiescent; retain owned device')
                cleanup['simulator'] = simulator.cleanup(prefix, claim, backend)
                cleanup['workers_after'] = apple.stop_workers(claim, apple.DarwinWorkerBackend(),
                                                              environment['RUNNER_TRACKING_ID'])
                row['cleanup'] = 'PASS' if cleanup['workers_after']['result'] == 'PASS' else 'FAIL'
            except BaseException as error:
                cleanup['error_type'] = type(error).__name__; row['cleanup'] = 'FAIL'
            (dest / 'cleanup.json').write_text(json.dumps(cleanup, indent=2))
            checkpoint(); commands.finalizing = False
        if row['cleanup'] != 'PASS' or commands.signals:
            break
    if control:
        try:
            state['result'] = control.accept_native_export(out, state | {'commands': commands.rows})
        except (ValueError, KeyError, TypeError) as error:
            state.update(result='INCOMPLETE', acceptance_error_type=type(error).__name__)
            checkpoint()
            raise
    else:
        state['result'] = 'SCOPED_INVESTIGATION_COMPLETE' if len(state['arms']) == 3 and \
            all(p['cleanup'] == 'PASS' and p['result'].startswith('JOURNEYS_') for p in state['arms']) else 'INCOMPLETE'
    checkpoint()
    return completed_exit_code(state['result'])


if __name__ == '__main__':
    sys.exit(main())
