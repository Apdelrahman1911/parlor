"""Run the existing original23/native adapter on the original failed iOS18.5.

Only runtime/device admission changes. Rendered app/driver/native observers,
selection, assertions and budgets remain byte-identical to the accepted26.2 arm.
No new observer, retry, CPU probe, input fix or production qualification.
"""
from contextlib import contextmanager
import json
import math

import pin60_original_suite as prior

SOURCE, TREE, ARMS = prior.SOURCE, prior.TREE, prior.ARMS
PROFILE, RESULT, CASES, TESTS = prior.PROFILE, prior.RESULT, prior.CASES, prior.TESTS
TEST_TIMEOUT_SECONDS = prior.TEST_TIMEOUT_SECONDS
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-18-5'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'
SCOPE = 'Original source/23 identifiers and unchanged native adapter on iOS18.5/iPhone16Pro; not KMP-prelude restoration, causality, neutrality, shipping or physical qualification'
require, load, runtime, MAX_BYTES = prior.require, prior.load, prior.runtime, prior.MAX_BYTES
require_capability, decode, retain_companion = prior.require_capability, prior.decode, prior.retain_companion
ORIGINAL_RENDERER = prior.renderer_for_profile
ORIGINAL_RUNTIME, ORIGINAL_DEVICE = prior.RUNTIME, prior.DEVICE
native = prior.prior.native


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE),
            'Exact original failed18.5 source/runtime; no fallback')
    return ORIGINAL_RENDERER(PROFILE, source, ORIGINAL_RUNTIME, ORIGINAL_DEVICE)


def test_command(base, result):
    return prior.test_command(base, result)


def verify_results(summary, tests, simulator, code):
    count = len(CASES)
    require(type(code) is int and code in (0, 65), 'Actual XCTest pass or retained failure')
    for key in ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures'):
        require(type(summary.get(key)) is int and 0 <= summary[key] <= count, 'Exact executed integer counts')
    require(summary['totalTestCount'] == count and summary['skippedTests'] == summary['expectedFailures'] == 0
        and summary['passedTests'] + summary['failedTests'] == count, 'All23 original selected methods execute')
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(len(configs) == len(devices) == len(tests.get('testPlanConfigurations', [])) == 1, 'One owned configuration')
    for device in (configs[0].get('device', {}), devices[0]):
        require((device.get('deviceId'), device.get('architecture'), device.get('platform'),
                 device.get('modelName'), device.get('osVersion')) ==
                (simulator, 'arm64', 'iOS Simulator', 'iPhone 16 Pro', '18.5'), 'Exact original runtime and device')
    nodes = native.prior.p.original.visit(tests.get('testNodes', []))
    require(len(nodes) == count and {n.get('nodeIdentifier') for n in nodes} == set(CASES) and
        all(n.get('result') in ('Passed', 'Failed') and type(n.get('durationInSeconds')) in (int, float)
            and math.isfinite(n['durationInSeconds']) and 0 < n['durationInSeconds'] < TEST_TIMEOUT_SECONDS
            for n in nodes), 'Unique original methods, actual positive-duration outcomes')
    methods = {n['nodeIdentifier']: n['result'] for n in nodes}
    passed = sum(value == 'Passed' for value in methods.values())
    require(passed == summary['passedTests'] and (code == 0) == (passed == count) and
        summary.get('result') == ('Passed' if passed == count else 'Failed'), 'Actual outcomes agree with exit')
    failures = summary.get('testFailures', [])
    require(type(failures) is list and len(failures) == summary['failedTests'] and
        {f.get('testIdentifierString') for f in failures} == {k for k, v in methods.items() if v == 'Failed'} and
        all(f.get('targetName') == 'iosAppUITests' and type(f.get('failureText')) is str and
            f['failureText'] for f in failures), 'Every original failed assertion retained')
    return methods



def failed_method_exports(result, destination, outcomes):
    return prior.failed_method_exports(result, destination, outcomes)


@contextmanager
def runtime_admission(prepared):
    # Single-threaded declared consumer only; original26.2 readers remain intact.
    # Do not normalize/relabel observed runtime or device fields. The new strict
    # verifier admits real18.5/model16 fields and rejects the old26.2 producer.
    def exact(profile, source, os, device):
        require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Declared18.5 plan only')
        return prepared
    changes = ((prior, dict(RUNTIME=RUNTIME, DEVICE=DEVICE, renderer_for_profile=exact)),
               (prior.prior, dict(RUNTIME=RUNTIME, DEVICE=DEVICE, verify_results=verify_results)),
               (native.prior, dict(RUNTIME=RUNTIME, DEVICE=DEVICE)))
    saved = [(module, {key: getattr(module,key) for key in updates}) for module,updates in changes]
    try:
        for module, updates in changes:
            for key,value in updates.items(): setattr(module,key,value)
        yield
    finally:
        for module, values in reversed(saved):
            for key,value in values.items(): setattr(module,key,value)


def accept_native_export(out, state):
    require(state.get('runtime') == RUNTIME and state.get('device') == DEVICE,
            'Actual original18.5 runtime and model, not relabeled26.2 evidence')
    prepared = renderer_for_profile(PROFILE, SOURCE, RUNTIME, DEVICE)
    with runtime_admission(prepared):
        result = prior.accept_native_export(out,state)
    require(result == RESULT, 'Complete source/product/commands/failed captures/cleanup required')
    value = dict(runtime=RUNTIME, device=DEVICE, source=SOURCE, tree=TREE,
                 methods=state['arms'][0]['methods'], rendered_plan_unchanged=True,
                 app_fix=False, shipping_qualified=False, physical_verified=False,
                 cause_established=False, timing_neutrality=False)
    target = out/'original-185-binding.json'
    if target.exists(): require(load(target)==value, 'Immutable runtime consumer receipt')
    else:
        with target.open('x') as f: json.dump(value,f,indent=2)
    return result
