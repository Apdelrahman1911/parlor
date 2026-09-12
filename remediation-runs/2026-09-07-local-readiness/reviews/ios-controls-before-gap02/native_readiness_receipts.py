"""Separate diagnostic delivery from actual storage health; never false-green."""
import json
import re

import probe_validation as legacy
from artifact_inventory import safe_relative
from prerequisite_receipts import records

TOKEN = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')
require = legacy.require
NATIVE_OPERATIONS = {'credential-read', 'credential-update', 'credential-add', 'credential-retry', 'credential-delete'}


def verify_boot_result(value, ordinal, token):
    require(isinstance(value, dict) and set(value) == {
        'schema_version', 'boot_ordinal', 'process_boot', 'process_id', 'run_token', 'loaded_app_images', 'observation'},
        'Wrong app-host boot-result schema')
    require(type(value['schema_version']) is int and value['schema_version'] == 1 and
            type(value['boot_ordinal']) is int and value['boot_ordinal'] == ordinal and value['run_token'] == token and
            isinstance(value['process_boot'], str) and TOKEN.fullmatch(value['process_boot']) and
            type(value['process_id']) is int and 0 < value['process_id'] < 2**31, 'Wrong boot/context binding')
    images = value['loaded_app_images']
    require(isinstance(images, list) and 1 <= len(images) <= 128 and all(safe_relative(path) for path in images) and
            images == sorted(set(images)), 'Missing/unsafe/duplicated native image names')
    observation = value['observation']
    require(isinstance(observation, dict) and set(observation) == {
        'schema_version', 'kind', 'harness_status', 'boot_ordinal', 'run_token', 'steps',
        'credential_numeric_status_is_exact_invocation', 'initial_home_numeric_attribution',
        'snapshot_numeric_attribution', 'physical_or_signed_evidence'}, 'Wrong production-observation schema')
    require(type(observation['schema_version']) is int and observation['schema_version'] == 1 and
            observation['kind'] == 'actual_koin_signing_disabled_simulator' and
            observation['harness_status'] == 'observation_complete' and type(observation['boot_ordinal']) is int and
            observation['boot_ordinal'] == ordinal and
            observation['run_token'] == token and observation['credential_numeric_status_is_exact_invocation'] is True and
            all(observation[key] is False for key in ('initial_home_numeric_attribution', 'snapshot_numeric_attribution',
                                                      'physical_or_signed_evidence')), 'Wrong attribution or incomplete observation')
    plan = ['home-rerun', 'credential-read', 'snapshot-read'] + (
        ['credential-write', 'snapshot-write'] if ordinal in (1, 2) else
        ['credential-remove', 'snapshot-remove'] if ordinal == 3 else [])
    steps = observation['steps']
    require(isinstance(steps, list) and len(steps) == len(plan), 'Missing/extra native operations')
    result = {}
    for name, step in zip(plan, steps):
        require(isinstance(step, dict) and set(step) == {'step', 'outcome', 'native_events', 'overflow'} and
                step['step'] == name and step['overflow'] is False, 'Wrong/reordered/overflowed invocation receipt')
        events = step['native_events']
        require(isinstance(events, list) and len(events) <= 16, 'Unbounded native receipt')
        for index, event in enumerate(events, 1):
            require(isinstance(event, dict) and set(event) == {'ordinal', 'operation', 'status'} and
                    type(event['ordinal']) is int and event['ordinal'] == index and event['operation'] in NATIVE_OPERATIONS and
                    type(event['status']) is int and -(2**31) <= event['status'] < 2**31, 'Unsafe primitive native observation')
        outcome = step['outcome']
        require(isinstance(outcome, dict), 'Missing actual outcome')
        if name == 'home-rerun':
            require(set(outcome) == {'kind', 'local', 'multiplayer', 'has_unavailable_source', 'original_home_invocation'} and
                    outcome['kind'] == 'home_observed' and outcome['local'] in {'absent', 'storage_error'} and
                    outcome['multiplayer'] in {'absent', 'secure_storage_unavailable', 'other_net_error'} and
                    outcome['original_home_invocation'] is False and type(outcome['has_unavailable_source']) is bool and
                    outcome['has_unavailable_source'] == (outcome['local'] != 'absent' or outcome['multiplayer'] != 'absent'),
                    'Production Home rerun did not preserve actual source outcomes/fail-closed projection')
            require(len(events) == 1 and events[0]['operation'] == 'credential-read', 'Home rerun must own its exact credential query')
            if outcome['multiplayer'] == 'absent':
                require(events[0]['status'] == -25300, 'Fresh production missing-item outcome not backed by its actual query')
            else:
                require(outcome['multiplayer'] == 'secure_storage_unavailable' and events[0]['status'] != -25300,
                        'An absent native item cannot be mapped to a credential failure')
        else:
            require(set(outcome) == {'kind'} and outcome['kind'] in {'ok', 'absent', 'matches', 'mismatch', 'storage_error'},
                    'Unknown native step outcome')
            if name.startswith('snapshot-'):
                require(not events, 'Do not numerically attribute uninstrumented snapshot operations')
                permitted = {'absent', 'matches', 'mismatch', 'storage_error'} if name == 'snapshot-read' else {'ok', 'storage_error'}
                require(outcome['kind'] in permitted, 'Snapshot operation has an impossible outcome')
            elif name == 'credential-read':
                require(len(events) == 1 and events[0]['operation'] == 'credential-read', 'Credential read lacks exact primitive receipt')
                status = events[0]['status']
                require((outcome['kind'] == 'absent' and status == -25300) or
                        (outcome['kind'] in {'matches', 'mismatch'} and status == 0) or
                        (outcome['kind'] == 'storage_error' and status != -25300), 'Credential read/status contradiction')
            elif name == 'credential-remove':
                require(outcome['kind'] in {'ok', 'storage_error'} and len(events) == 1 and
                        events[0]['operation'] == 'credential-delete' and
                        (outcome['kind'] == 'ok') == (events[0]['status'] in (0, -25300)), 'Credential delete/status contradiction')
            else:
                operations = [event['operation'] for event in events]
                require(operations in (['credential-update'], ['credential-update', 'credential-add'],
                                       ['credential-update', 'credential-add', 'credential-retry']), 'Wrong update/add/retry transaction trace')
                if len(events) >= 2:
                    require(events[0]['status'] == -25300, 'Add attempted despite a non-missing update outcome')
                if len(events) == 3:
                    require(events[1]['status'] == -25299, 'Retry attempted without duplicate-item receipt')
                if len(events) == 1:
                    require(events[0]['status'] != -25300, 'Missing update cannot omit its actual add')
                if len(events) == 2:
                    require(events[1]['status'] != -25299, 'Duplicate add cannot omit its actual retry')
                require(outcome['kind'] in {'ok', 'storage_error'} and
                        (outcome['kind'] == 'ok') == (events[-1]['status'] == 0), 'Credential write/status contradiction')
            if name.endswith('-read'):
                require(ordinal != 1 or outcome['kind'] in {'absent', 'storage_error'},
                        'Unexpected first-boot values must abort before any synthetic overwrite')
                require(ordinal in (2, 3) or outcome['kind'] != 'matches',
                        'A post-delete/fresh read cannot match non-null expected bytes')
        result[name] = outcome
    return result


def classify_storage_health(outcomes, results):
    """A failed write is not durability proof; a successful one cannot hide loss.

    FAIL here means a local verification gate failed, not an independently
    established application defect. Exact native error codes remain evidence;
    they are never converted into invented entitlement/signing explanations.
    """
    history = {store: {} for store in ('credential', 'snapshot')}
    boots = []
    for ordinal, (outcome, result) in enumerate(zip(outcomes, results), 1):
        native = {step['step']: step['native_events'] for step in result['observation']['steps']}
        checks = []
        home = outcome['home-rerun']
        home_status = ('FAIL' if home['multiplayer'] != 'absent' and native['home-rerun'][0]['status'] == 0 else
                       'PASS' if home['local'] == home['multiplayer'] == 'absent' else 'BLOCKED')
        checks.append(dict(step='home-rerun', status=home_status,
                           reason='observed_both_sources_absent' if home_status == 'PASS' else
                                  'native_success_followed_by_credential_failure' if home_status == 'FAIL' else
                                  'actual_source_unavailable_cause_not_inferred'))
        for store in ('credential', 'snapshot'):
            step = store + '-read'
            actual = outcome[step]['kind']
            desired = 'matches' if ordinal in (2, 3) else 'absent'
            predecessor = (history[store].get('write') if ordinal in (2, 3) else
                           history[store].get('remove') if ordinal >= 4 else None)
            prerequisite_ok = ordinal == 1 or (predecessor is not None and predecessor['outcome'] == 'ok')
            if actual == 'storage_error':
                status = 'FAIL' if store == 'credential' and native[step][0]['status'] == 0 else 'BLOCKED'
                reason = 'native_success_followed_by_secure_boundary_failure' if status == 'FAIL' else 'actual_read_unavailable'
            elif actual != desired:
                status = 'FAIL' if prerequisite_ok else 'BLOCKED'
                reason = 'value_differs_after_successful_predecessor' if prerequisite_ok else 'predecessor_success_not_proven'
            else:
                status = 'PASS'
                reason = 'expected_value_observed'
            checks.append(dict(step=step, status=status, expected=desired, actual=actual, reason=reason,
                               prerequisite=predecessor))
            action = 'write' if ordinal in (1, 2) else 'remove' if ordinal == 3 else None
            if action is not None:
                actual = outcome[store + '-' + action]['kind']
                history[store][action] = dict(boot_ordinal=ordinal, operation=action, outcome=actual)
                checks.append(dict(step=store + '-' + action, status='PASS' if actual == 'ok' else 'BLOCKED',
                                   reason='operation_reported_success' if actual == 'ok' else 'actual_operation_unavailable'))
        statuses = {check['status'] for check in checks}
        boots.append(dict(boot_ordinal=ordinal, status='FAIL' if 'FAIL' in statuses else
                          'BLOCKED' if 'BLOCKED' in statuses else 'PASS', checks=checks))
    statuses = {boot['status'] for boot in boots}
    return dict(storage_health='FAIL' if 'FAIL' in statuses else 'BLOCKED' if 'BLOCKED' in statuses else 'PASS',
                storage_boots=boots)


def verify_native_readiness(results, scenario, settings, log):
    token = settings.get('runToken')
    require(isinstance(token, str) and TOKEN.fullmatch(token), 'Missing source-matrix run token')
    require(isinstance(results, list) and len(results) == 8, 'All eight cold launches require separate durable results')
    rows, starts = legacy.common(scenario, 'readiness')
    require(scenario['runToken'] == token and type(scenario['schemaVersion']) is int and
            all(type(event['ordinal']) is int and type(event['controllerCreations']) is int for event, _ in rows),
            'Cold-launch scenario/counters differ from actual task context')
    require(len(starts) == 8 and all(event['fixture'] == 'existing' for event in starts), 'Expected eight explicit cold starts')
    require(all(event['preferences']['setting'] == 'system' and not event['preferences']['ownerPresent'] and
                not event['preferences']['hasAppOverride'] for event in starts), 'Synthetic language fixture was not returned to its original absence')
    cleanup = records(log, 'PARLOR_NATIVE_SYNTHETIC_SEED_CLEANUP ', maximum=1, row_bytes=1024, total_bytes=1024)
    require(cleanup == [dict(schema_version=1, run_token=token, original_present=False,
                            synthetic_previous=['ar-EG', 'en'], restored_present=False,
                            source='completed-token-bound-owned-settings-fixture-only')], 'Missing attested synthetic seed cleanup')
    launch_rows = records(log, 'PARLOR_NATIVE_COLD_LAUNCH ', maximum=8, row_bytes=8192, total_bytes=65536)
    require(len(launch_rows) == 8, 'All cold-launch stability trains must execute')
    outcomes = []
    for ordinal, (result, launch, start) in enumerate(zip(results, launch_rows, starts), 1):
        outcomes.append(verify_boot_result(result, ordinal, token))
        require(set(launch) == {'schema_version', 'boot_ordinal', 'process_boot', 'run_token', 'loaded_app_images',
                              'samples', 'app_foreground', 'native_alert_present', 'probe_activations'} and
                type(launch['schema_version']) is int and launch['schema_version'] == 1 and
                type(launch['boot_ordinal']) is int and launch['boot_ordinal'] == ordinal and
                launch['process_boot'] == result['process_boot'] == start['boot'] and launch['run_token'] == token and
                launch['loaded_app_images'] == result['loaded_app_images'] and launch['app_foreground'] is True and
                launch['native_alert_present'] is False and type(launch['probe_activations']) is int and
                launch['probe_activations'] == 1, 'Launch and durable actual-app receipts disagree')
        samples = launch['samples']
        require(isinstance(samples, list) and len(samples) == 6, 'Missing launch stability train')
        last_time, last_sequence = -1, start['ordinal']
        for index, sample in enumerate(samples, 1):
            require(isinstance(sample, dict) and set(sample) == {'ordinal', 'elapsed_milliseconds', 'observation_sequence'} and
                    all(type(sample[key]) is int for key in sample) and sample['ordinal'] == index and
                    0 <= sample['elapsed_milliseconds'] <= 120000 and sample['observation_sequence'] > last_sequence and
                    (sample['elapsed_milliseconds'] >= 0 if index == 1 else sample['elapsed_milliseconds'] - last_time >= 1950),
                    'Invalid/reordered/unseparated launch sampling')
            observed = [(event, composition) for event, composition in rows if event['ordinal'] == sample['observation_sequence']]
            require(len(observed) == 1 and observed[0][0]['boot'] == start['boot'] and observed[0][0]['phase'] == 'sample' and
                    legacy.geometry_fills_window(observed[0][0]['native']['geometry'], 'portrait'),
                    'Launch sample not backed by real bound native geometry/identity')
            last_time, last_sequence = sample['elapsed_milliseconds'], sample['observation_sequence']
        require(last_time - samples[0]['elapsed_milliseconds'] >= 10000,
                'Each cold-launch train must span at least ten measured seconds')
    require(len({value['process_boot'] for value in results}) == 8, 'One process cannot impersonate eight launches')
    health = classify_storage_health(outcomes, results)
    return dict(diagnostic_delivery='PASS', current_build_cold_launch='PASS', cold_launches=8,
                stability_samples=48, minimum_observation_span_per_launch_seconds=10,
                exact_rerun_credential_attribution='PASS', **health, original_first_four_launch_failures_cause='UNRESOLVED',
                claims='Current instrumented signing-disabled Debug simulator only; not physical LAN, healthy signed Keychain, complete save/resume, Store, or prior-launch root-cause proof')
