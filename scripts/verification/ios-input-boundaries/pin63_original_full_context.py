"""Final declared original execution comparison; no new native observation.

Reuse the accepted original23/18.5 adapter, restoring the uninstrumented KMP
prelude and a single combined Xcode action together. Pass-only evidence does
not establish the intermittent cause or qualify a shipping correction.
"""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import re

import pin61_original_185 as prior
import pin14_original_resources as resources
import pin16_aggregate_context as aggregate

SOURCE, TREE, PROFILE, RESULT = prior.SOURCE, prior.TREE, prior.PROFILE, prior.RESULT
RUNTIME, DEVICE, ARMS = prior.RUNTIME, prior.DEVICE, prior.ARMS
CASES, TESTS, TEST_TIMEOUT_SECONDS = prior.CASES, prior.TESTS, prior.TEST_TIMEOUT_SECONDS
# Existing combined-context process cap, not an increased assertion deadline.
COMBINED_TIMEOUT_SECONDS = 3600
require, load = prior.require, prior.load
require_capability, renderer_for_profile = prior.require_capability, prior.renderer_for_profile
verify_results, failed_method_exports = prior.verify_results, prior.failed_method_exports
retain_companion = prior.retain_companion
build_environment, observe_resources = resources.build_environment, resources.observe_resources
execute_prelude = aggregate.execute_prelude
STAGES = ('before-build', 'after-tests')
SCOPE = ('Original uninstrumented774-method/149-report KMP prelude then unchanged23-method/native adapter '
         'on18.5 in one combined Xcode action; original6GiB/default-parallel booted-build context; '
         'not causality, neutrality, shipping qualification or a mobile benchmark')
WRAPPER = ['/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--']


def test_command(base, result):
    command = prior.test_command(base, result)
    require(command.count('test-without-building') == 1, 'One unchanged original selection')
    return ['test' if item == 'test-without-building' else item for item in command]


def verify_commands(state, row):
    task = state['task']
    require(set(task) == {'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB'} and
        re.fullmatch('[1-9][0-9]*', task['GITHUB_RUN_ID']) and task['GITHUB_RUN_ATTEMPT'] == '1' and
        task['GITHUB_JOB'] == 'ios', 'Single run-owned standard Mac task')
    producer = Path('/Users/runner/work/_temp') / (
        'parlor-original-full-context-' + task['GITHUB_RUN_ID'] + '-1')
    require(state['producer_output'] == str(producer), 'Exact producer path, not relocated reader path')
    calls = state['commands']
    require(not any(c['label'] in ('compose-build', 'compose-tests') for c in calls),
            'No fabricated separate build/test receipts')
    selected = [c for c in calls if c['label'] == 'compose-build-and-tests']
    require(len(selected) == 1, 'Single combined command')
    call = selected[0]
    expected = test_command(['/usr/bin/xcodebuild', '-quiet', '-project', 'iosApp/iosApp.xcodeproj',
        '-scheme', 'iosApp', '-configuration', 'Debug', '-sdk', 'iphonesimulator',
        '-destination', 'platform=iOS Simulator,id=' + row['simulator'],
        '-derivedDataPath', str(producer/'derived'), '-parallel-testing-enabled', 'NO',
        '-maximum-concurrent-test-simulator-destinations', '1', '-destination-timeout', '120'],
        producer/'compose/tests.xcresult')
    require(call['command'] == WRAPPER + expected and
        call['exit_code'] == row['test_exit'] == row['combined_exit'] and
        call['timeout_seconds'] == COMBINED_TIMEOUT_SECONDS,
        'Exact combined source selection, owned paths, outcomes and bounded process; no retry/skip/job cap')
    stops = [c for c in calls if c['label'] == 'compose-gradle-stop']
    require(len(stops) == 1 and stops[0]['exit_code'] == 0 and stops[0]['timeout_seconds'] == 120 and
        calls.index(call) < calls.index(stops[0]), 'Owned cleanup after the combined command')
    boots = [c for c in calls if 'bootstatus' in c.get('command', [])]
    require(len(boots) == 1 and calls.index(boots[0]) < calls.index(call), 'Owned Simulator booted during build')
    require(row['build_exit'] == 0 and row['product_manifest_stage'] == 'after-combined-command' and
        row['build_success_evidence'] == 'all23-executed-native-outcomes',
        'Actual native outcomes establish compilation; product manifest taken after the command')
    expected_profile = dict(gradle_heap_bytes=6*1024**3, gradle_workers='original-default',
        gradle_parallel=True, kotlin_compiler='in-process', xcode_jobs='original-default',
        verbose_build=True, boot_after_build=False, build_timeout_seconds=2700,
        test_timeout_seconds=TEST_TIMEOUT_SECONDS, scope=SCOPE, combined_xcode=True,
        combined_timeout_seconds=COMBINED_TIMEOUT_SECONDS, product_manifest_stage='after-combined-command')
    require(state['build_profile'] == expected_profile, 'Exact declared execution profile')


def verify_prelude(out, state):
    require((aggregate.SOURCE, aggregate.TREE) == (SOURCE, TREE), 'Original source-bound KMP prelude')
    row = state['aggregate_prelude']
    require(row.get('original_uninstrumented_inputs') is True and
        row.get('result') == 'ORIGINAL774_EXECUTED_NOT_QUALIFICATION' and
        row.get('exit_code') == row.get('stop_exit') == 0 and row.get('cleanup') == 'PASS' and
        row.get('workers', {}).get('result') == 'PASS', 'Executed original KMP outcomes and cleanup')
    commands = state['commands']
    calls = [c for c in commands if c['label'] == 'original-kmp-aggregate']
    stops = [c for c in commands if c['label'] == 'original-kmp-stop']
    require(len(calls) == len(stops) == 1 and calls[0]['command'] == WRAPPER + aggregate.PRELUDE_COMMAND and
        calls[0]['exit_code'] == 0 and calls[0]['timeout_seconds'] == aggregate.PRELUDE_TIMEOUT and
        stops[0]['exit_code'] == 0 and stops[0]['timeout_seconds'] == 120,
        'One exact unmodified wrapper aggregate, no replay or retargeting')
    native = next(c for c in commands if c['label'] == 'compose-build-and-tests')
    require(commands.index(calls[0]) < commands.index(stops[0]) < commands.index(native) and
        len([c for c in commands if 'productionIosSimulatorRuntimeTests' in c.get('command', [])]) == 1,
        'Original KMP prelude and cleanup precede native execution')
    expected = aggregate.case_manifest()['classes']
    raws = {c['path']: aggregate.frozen_sources._read(out/'aggregate/xml'/c['path'], 2*1024*1024) for c in expected}
    require({str(p.relative_to(out/'aggregate/xml')) for p in (out/'aggregate/xml').rglob('TEST-*.xml')} == set(raws),
        'Exactly149 retained original XML reports')
    require(aggregate.verify_prelude_xml(raws, state['original_inputs']) == row['reports'] and
        row['retained_reports'] == {p: hashlib.sha256(b).hexdigest() for p, b in raws.items()},
        'Every original774 method, source hash and report outcome')
    for stage in ('before', 'after'):
        raw = aggregate.frozen_sources._read(out/'aggregate'/('simulators-'+stage+'.json'), 65536)
        require(hashlib.sha256(raw).hexdigest() == row['simulators_'+stage], 'Original numeric inventory custody')
        aggregate.validate_simulator_observation(raw)


@contextmanager
def combined_admission():
    # Substitute only the command verifier, never transform actual command/state
    # into invented standalone receipts. All other native/source/outcome gates run.
    module = prior.native.prior
    original = module.verify_commands
    try:
        module.verify_commands = verify_commands
        yield
    finally:
        module.verify_commands = original


def accept_native_export(out, state):
    verify_commands(state, state['arms'][0])
    verify_prelude(out, state)
    row = state['arms'][0]
    require(set(row.get('kernel_resources', {})) == set(STAGES), 'Both actual combined kernel boundaries')
    for stage in STAGES:
        raw = aggregate.frozen_sources._read(out/'compose'/('kernel-'+stage+'.json'), 4096)
        require(hashlib.sha256(raw).hexdigest() == row['kernel_resources'][stage], 'Actual numeric resource custody')
        resources.validate_resources(json.loads(raw, object_pairs_hook=aggregate.pin01_receipts.reject_duplicates))
    with combined_admission():
        result = prior.accept_native_export(out, state)
    require(result == RESULT, 'All unchanged source/native/failure/cleanup gates')
    value = dict(source=SOURCE, tree=TREE, runtime=RUNTIME, device=DEVICE, methods=row['methods'],
        kmp_methods=774, kmp_reports=149, kmp_prelude_matched=True, combined_xcode_matched=True,
        rendered_plan_unchanged=True, new_native_observers=0, application_root_cause=False,
        shipping_qualified=False, physical_verified=False)
    target = out/'original-full-context-binding.json'
    if target.exists(): require(load(target) == value, 'Immutable full-context receipt')
    else:
        with target.open('x') as f: json.dump(value, f, indent=2)
    return result
