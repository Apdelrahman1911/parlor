"""Existing adapter/suite on18.5 with original build resource settings only.

No new observer or app/driver edits. Separate Xcode actions and omitted KMP
prelude remain explicit differences; this is not full-context reproduction.
"""
import hashlib
import json

import pin61_original_185 as prior
import pin14_original_resources as resources

SOURCE, TREE, PROFILE, RESULT = prior.SOURCE, prior.TREE, prior.PROFILE, prior.RESULT
RUNTIME, DEVICE, ARMS = prior.RUNTIME, prior.DEVICE, prior.ARMS
CASES, TESTS, TEST_TIMEOUT_SECONDS = prior.CASES, prior.TESTS, prior.TEST_TIMEOUT_SECONDS
require, load, runtime, MAX_BYTES = prior.require, prior.load, prior.runtime, prior.MAX_BYTES
decode, retain_companion = prior.decode, prior.retain_companion
require_capability = prior.require_capability
renderer_for_profile = prior.renderer_for_profile
verify_results, failed_method_exports = prior.verify_results, prior.failed_method_exports
test_command = prior.test_command
build_environment, build_command = resources.build_environment, resources.build_command
observe_resources = resources.observe_resources
SCOPE = 'Original6GiB/default parallel/booted-build resource context; unchanged23-method/native adapter on18.5; separate Xcode actions/no KMP prelude; not causality, qualification or mobile benchmark'


def verify_resources(out, state):
    expected = dict(gradle_heap_bytes=6*1024**3, gradle_workers='original-default',
                    gradle_parallel=True, kotlin_compiler='in-process',
                    xcode_jobs='original-default', verbose_build=True, boot_after_build=False,
                    build_timeout_seconds=2700, test_timeout_seconds=TEST_TIMEOUT_SECONDS,
                    scope=SCOPE)
    require(state['build_profile'] == expected, 'Exact declared original resources, unchanged deadlines')
    row = state['arms'][0]
    require(set(row.get('kernel_resources', {})) == set(resources.STAGES), 'All existing resource boundaries')
    for stage in resources.STAGES:
        p = out/'compose'/('kernel-'+stage+'.json')
        raw = prior.native.prior.frozen_sources._read(p, 4096)
        require(hashlib.sha256(raw).hexdigest() == row['kernel_resources'][stage], 'Exact numeric resource custody')
        resources.validate_resources(json.loads(raw))
    commands = state['commands']
    builds = [c for c in commands if c['label']=='compose-build']
    require(len(builds)==1 and builds[0]['command']==[
        '/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--',
        *build_command([
        '/usr/bin/xcodebuild', '-quiet', '-project', 'iosApp/iosApp.xcodeproj',
        '-scheme', 'iosApp', '-configuration', 'Debug', '-sdk', 'iphonesimulator',
        '-destination', 'platform=iOS Simulator,id='+row['simulator'],
        '-derivedDataPath', str(out/'derived'), '-parallel-testing-enabled', 'NO',
        '-maximum-concurrent-test-simulator-destinations', '1'])], 'Original default Xcode jobs, not diagnostic job cap')
    labels = [c['label'] for c in commands]
    boot = [i for i,c in enumerate(commands) if 'bootstatus' in c.get('command', [])]
    require(len(boot)==1 and boot[0]<labels.index('compose-build')<labels.index('compose-tests'),
            'Owned Simulator ready before build; still distinct build/test actions')
    require(not any(c['label']=='original-kmp-aggregate' or 'compose-build-and-tests'==c['label']
                    for c in commands), 'No claim of omitted prelude or combined-action parity')


def accept_native_export(out, state):
    verify_resources(out, state)
    result = prior.accept_native_export(out, state)
    require(result == RESULT, 'Unchanged source/product/native/outcome/cleanup admission')
    value = dict(context='original-build-resources', source=SOURCE, tree=TREE,
                 runtime=RUNTIME, device=DEVICE, methods=state['arms'][0]['methods'],
                 original_resources_verified=True, rendered_plan_unchanged=True,
                 kmp_prelude_matched=False, combined_xcode_matched=False,
                 new_native_observer=False, shipping_qualified=False, physical_verified=False)
    target = out/'original-resource-binding.json'
    if target.exists(): require(load(target)==value, 'Immutable resource-context receipt')
    else:
        with target.open('x') as f: json.dump(value,f,indent=2)
    return result
