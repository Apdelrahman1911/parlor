"""Restore the omitted original KMP workload, not an application qualification.

Execute the frozen, uninstrumented aggregate once before adding PIN15 observers.
Do not replay the aggregate to warm caches or override its Simulator selection.
Inventory observations cannot establish what happened on the original runner.
"""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import current_boundary_adapter as current
import frozen_sources
import pin01_receipts
import pin06_public_setup_pair as pair
import pin15_combined_xcode as combined

SOURCE, TREE = combined.SOURCE, combined.TREE
PROFILE = 'native-original-aggregate-context'
ARMS = combined.ARMS
SCOPE = ('Original uninstrumented774-case KMP aggregate followed by PIN15 original23 native cases '
         'and failure-only observers; no original resource-causality, instrumentation-neutrality, '
         'shipping correction, application qualification or physical-device claim')
CASE_FILE = 'PIN16NativeCases.json'
CASE_SHA256 = '4cc25fd8e96df235b440d64d5554b836e20c9cfce617fabd67ef273e837d711f'
PRELUDE_COMMAND = ['./gradlew', 'productionIosSimulatorRuntimeTests', '--dependency-verification=strict',
                   '--no-daemon', '--stacktrace', '--console=plain']
PRELUDE_TIMEOUT = 2700
BOOT_STATES = {'Shutdown': 0, 'Booted': 1, 'Booting': 2, 'Shutting Down': 3}


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact original aggregate context required')
    return combined.renderer_for_profile(combined.PROFILE, source, runtime, device)


build_environment = combined.build_environment
observe_resources = combined.observe_resources
test_command = combined.test_command
verify_results = combined.verify_results
failed_method_exports = combined.failed_method_exports
retain_companion = combined.retain_companion
retain_root_companion = combined.retain_root_companion


def case_manifest():
    raw = frozen_sources._read(Path(__file__).with_name(CASE_FILE), 2*1024*1024)
    current.require(hashlib.sha256(raw).hexdigest() == CASE_SHA256, 'Exact reviewed original case bindings')
    data = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates)
    current.require((data['source'], data['tree'], data['original_run'], data['original_artifact']) ==
                    (SOURCE, TREE, 41180128325, 13035726724), 'Exact original case custody')
    current.require(len(data['classes']) == 149 and sum(len(c['methods']) for c in data['classes']) == 774,
                    'Every original native case required')
    return data


def verify_prelude_xml(raws, original_inputs):
    """All149 source-bound reports/774 actual methods; no count-only acceptance."""
    expected = case_manifest()['classes']
    current.require(type(raws) is dict and set(raws) == {c['path'] for c in expected},
                    'Exactly all original aggregate reports required')
    receipts = []
    for case in expected:
        current.require(original_inputs.get(case['source']) == case['source_sha256'],
                        'Original test source binding required')
        raw = raws[case['path']]
        current.require(type(raw) is bytes and 0 < len(raw) <= 2*1024*1024 and
                        b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw, 'Bounded literal native XML')
        suite = ET.fromstring(raw)
        nodes = suite.findall('testcase')
        current.require(suite.tag == 'testsuite' and suite.get('name') == case['classname'] and
            suite.get('tests') == str(len(case['methods'])) and
            all(suite.get(k) == '0' for k in ('failures', 'errors', 'skipped')),
            'Original suite identity and all unskipped passing outcomes required')
        current.require(not any(suite.findall('.//' + k) for k in ('failure', 'error', 'skipped')) and
            len(nodes) == len(case['methods']) and
            sorted(n.get('name', '') for n in nodes) == sorted(m+'[iosSimulatorArm64]' for m in case['methods']) and
            all(n.get('classname') == case['classname'] for n in nodes),
            'Every exact original native method must pass once')
        receipts.append(dict(path=case['path'], sha256=hashlib.sha256(raw).hexdigest(), methods=len(nodes)))
    return receipts


def observe_simulators(command, destination, stage):
    """Read boot states, not names, arbitrary profiles, processes or private data."""
    current.require(stage in ('before', 'after'), 'Exact aggregate inventory boundary')
    code, raw = command(['/usr/bin/xcrun', 'simctl', 'list', 'devices', '--json'], 'aggregate-inventory-'+stage)
    current.require(code == 0 and 0 < len(raw) <= 1024*1024, 'Bounded actual Simulator inventory')
    from scripts.ci import owned_ci_simulator as simulator
    entries = simulator.devices(json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates))
    values = []
    for e in entries:
        current.require(re.fullmatch(r'com\.apple\.CoreSimulator\.SimRuntime\.[A-Za-z0-9-]+', e['runtime']) and
                        e.get('state') in BOOT_STATES and type(e.get('isAvailable')) is bool,
                        'Known original runtime and boot state required')
        values.append([e['udid'], e['runtime'], BOOT_STATES[e['state']], int(e['isAvailable'])])
    data = json.dumps(sorted(values), separators=(',', ':')).encode()
    with (destination/('simulators-'+stage+'.json')).open('xb') as out:
        out.write(data)
    return hashlib.sha256(data).hexdigest()


def validate_simulator_observation(raw):
    current.require(type(raw) is bytes and 0 < len(raw) <= 65536, 'Bounded numeric Simulator observations')
    values = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates)
    current.require(type(values) is list and len(values) <= 256, 'Bounded Simulator collection')
    seen = set()
    for row in values:
        current.require(type(row) is list and len(row) == 4 and type(row[0]) is str and
            re.fullmatch(r'[0-9A-F]{8}(?:-[0-9A-F]{4}){3}-[0-9A-F]{12}', row[0]) and
            row[0] not in seen and type(row[1]) is str and
            re.fullmatch(r'com\.apple\.CoreSimulator\.SimRuntime\.[A-Za-z0-9-]+', row[1]) and
            type(row[2]) is int and row[2] in BOOT_STATES.values() and
            type(row[3]) is int and row[3] in (0, 1), 'Unknown/private or duplicated Simulator field')
        seen.add(row[0])
    return values


def execute_prelude(copy, out, state, command, checkpoint, root, task, config, environment, commands):
    """One unchanged wrapper aggregate, finalizing owned workers even on failure."""
    from scripts.ci import apple_verification_hygiene as apple
    destination = out/'aggregate'; destination.mkdir()
    prefix = destination/'resources'
    backend = apple.DarwinWorkerBackend()
    claim = apple.prepare(root, prefix, task, 'apple-aggregate', backend, config)
    row = dict(result='NOT_RUN', original_uninstrumented_inputs=True, reports=[], cleanup='NOT_RUN')
    state['aggregate_prelude'] = row; checkpoint()
    try:
        row['simulators_before'] = observe_simulators(command, destination, 'before')
        row['exit_code'], _ = command(PRELUDE_COMMAND, 'original-kmp-aggregate', PRELUDE_TIMEOUT,
                                      destination/'test.log')
        row['simulators_after'] = observe_simulators(command, destination, 'after')
        reports = sorted(copy.glob('**/build/test-results/iosSimulatorArm64Test/TEST-*.xml'))
        current.require(len(reports) <= 256, 'Bounded actual aggregate reports')
        raws = {}
        for p in reports:
            current.require(not p.is_symlink() and p.resolve() == p.absolute() and
                p.stat().st_nlink == 1 and 0 < p.stat().st_size <= 2*1024*1024, 'Owned bounded native XML')
            relative = str(p.relative_to(copy)); data = frozen_sources._read(p, 2*1024*1024)
            target = destination/'xml'/relative; target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as output: output.write(data)
            raws[relative] = data
        row['retained_reports'] = {p: hashlib.sha256(b).hexdigest() for p, b in raws.items()}
        current.require(row['exit_code'] == 0, 'Original aggregate must pass without a replay')
        row['reports'] = verify_prelude_xml(raws, state['original_inputs'])
        row['result'] = 'ORIGINAL774_EXECUTED_NOT_QUALIFICATION'
    except BaseException as error:
        row.update(result='AGGREGATE_FAILED', error_type=type(error).__name__)
        raise
    finally:
        # The original aggregate uses KGP's default standalone Simulator lifecycle.
        # Never adopt/delete a pre-existing device or invent owned-device cleanup.
        commands.finalizing = True
        try:
            row['stop_exit'], _ = command([str(copy/'gradlew'), '--stop',
                '-Dorg.gradle.daemon.registry.base='+str(out/'registry'), '--console=plain'],
                'original-kmp-stop', 120, destination/'stop.log')
            row['workers'] = apple.stop_workers(claim, backend, environment['RUNNER_TRACKING_ID'])
            row['cleanup'] = 'PASS' if row['stop_exit'] == 0 and row['workers']['result'] == 'PASS' else 'FAIL'
        except BaseException as error:
            row.update(cleanup='FAIL', cleanup_error_type=type(error).__name__)
            raise
        finally:
            checkpoint(); commands.finalizing = False
    current.require(row['cleanup'] == 'PASS', 'Aggregate worker cleanup required before native continuation')


def accept_native_export(out, state):
    # Reuse every PIN15 native, source, failure, product, order and owned cleanup
    # check. Only the explicit profile discriminant is adapted for that verifier.
    combined.accept_native_export(out, state | {'profile': combined.PROFILE})
    row = state.get('aggregate_prelude', {})
    current.require(state['profile'] == PROFILE and row.get('original_uninstrumented_inputs') is True and
                    row.get('result') == 'ORIGINAL774_EXECUTED_NOT_QUALIFICATION' and
                    row.get('exit_code') == row.get('stop_exit') == 0 and row.get('cleanup') == 'PASS' and
                    row.get('workers', {}).get('result') == 'PASS', 'Complete original aggregate evidence required')
    selected = [c for c in state['commands'] if c.get('label') == 'original-kmp-aggregate']
    stops = [c for c in state['commands'] if c.get('label') == 'original-kmp-stop']
    redirected = ['/bin/bash', '-c', 'exec "$@" > "$PARLOR_INPUT_LOG" 2>&1', '--', *PRELUDE_COMMAND]
    current.require(len(selected) == len(stops) == 1 and selected[0]['command'] == redirected and
                    selected[0]['exit_code'] == 0 and selected[0]['timeout_seconds'] == PRELUDE_TIMEOUT and
                    stops[0]['exit_code'] == 0, 'One exact unchanged aggregate invocation required')
    cmds = state['commands']
    native = next(c for c in cmds if c['label'] == 'compose-build-and-tests')
    current.require(cmds.index(selected[0]) < cmds.index(stops[0]) < cmds.index(native) and
                    len([c for c in cmds if 'productionIosSimulatorRuntimeTests' in c['command']]) == 1,
                    'Aggregate must run once before the native command, not after it or in parallel')
    raws = {c['path']: frozen_sources._read(out/'aggregate/xml'/c['path'], 2*1024*1024)
            for c in case_manifest()['classes']}
    current.require(verify_prelude_xml(raws, state['original_inputs']) == row['reports'] and
                    row.get('retained_reports') == {p: hashlib.sha256(b).hexdigest() for p, b in raws.items()},
                    'All original aggregate report bytes must match custody')
    for stage in ('before', 'after'):
        raw = frozen_sources._read(out/'aggregate'/('simulators-'+stage+'.json'), 65536)
        current.require(hashlib.sha256(raw).hexdigest() == row['simulators_'+stage], 'Original inventory byte custody')
        validate_simulator_observation(raw)
    return 'ORIGINAL_AGGREGATE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
