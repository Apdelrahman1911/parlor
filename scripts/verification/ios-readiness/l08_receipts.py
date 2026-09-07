"""Bounded L08 app-host evidence; mock parser fixtures never count as runtime."""
import json
from pathlib import Path
import re

from artifact_inventory import FRAMEWORK_PATH, bind_loaded_images, safe_relative, validate_framework_observation, validate_loader_environment
from probe_validation import common, geometry_fills_window, require

UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')
STORAGE_PLAN = {
    1: ('save',), 2: ('load', 'continue'), 3: ('save',), 4: ('load', 'continue'),
    5: ('save',), 6: ('load', 'continue'), 7: ('save',), 8: ('load', 'continue'),
    9: ('legacy-seed',), 10: ('legacy-load', 'continue'),
    11: ('legacy-neighbor', 'discard-observed'), 12: ('dual-preserved', 'discard-observed'), 13: ('final-absence',),
}
STORAGE_CHECKS = {
    'save': {'actual_bindings', 'initial_absence', 'reachable_checkpoint', 'real_store_save_roundtrip', 'protected_metadata'},
    'load': {'actual_bindings', 'restart_envelope_equal', 'production_recovery', 'exact_home_destination', 'protected_metadata'},
    'continue': {'actual_home_tap', 'actual_resumed_controller', 'restored_state_equal', 'legal_continuation',
                 'terminal_winner', 'actual_terminal_writer_delete'},
    'legacy-seed': {'actual_bindings', 'initial_absence', 'reachable_checkpoint', 'legacy_seed_exclusive',
                    'healthy_and_malformed_coexist', 'valid_dual_copy_seeded_recognized_tag_damaged'},
    'legacy-load': {'actual_bindings', 'restart_envelope_equal', 'production_recovery', 'exact_home_destination',
                    'protected_metadata', 'valid_legacy_migrated', 'malformed_retained_excluded',
                    'dual_copy_failed_closed_and_retained', 'independent_inventory'},
    'legacy-neighbor': {'healthy_terminal_absent_after_restart', 'malformed_still_addressable', 'malformed_still_excluded'},
    'dual-preserved': {'prior_records_absent_after_restart', 'recognized_damage_failed_closed',
                      'recoverable_legacy_still_present', 'retained_legacy_envelope_equal', 'legacy_still_excluded', 'protected_metadata'},
    'discard-observed': {'actual_home_tap', 'actual_retry_tap', 'actual_discard_tap', 'retry_remained_failed',
                         'explicit_discard_deleted'},
    'final-absence': {'all_synthetic_records_absent_after_restart', 'strict_absence_not_unavailable'},
}


# Closed failure-only diagnostics. Historical broad stages remain preservable,
# but none of these records can satisfy verify_storage's exact PASS schema.
STORAGE_FAILURE_STAGES = frozenset({
    'context', 'actual-bindings', 'initial-absence', 'reachable-checkpoint', 'real-store-save',
    'real-store-load', 'loaded-envelope-equality', 'owned-legacy-seed', 'restart-envelope',
    'production-recovery', 'protected-metadata', 'legacy-neighbor-isolation', 'actual-home-controller',
    'actual-terminal-writer-delete', 'retained-corrupt-neighbor', 'dual-copy-retained-after-restart',
    'explicit-retry-discard', 'final-restart-absence', 'metadata-path', 'metadata-existence',
    'metadata-directory-backup', 'metadata-file-backup', 'metadata-protection-constants',
    'metadata-directory-protection', 'metadata-file-protection', 'metadata-encrypted-read',
    'metadata-encrypted-header',
})


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate evidence field')
        result[key] = value
    return result


def read_owned_result(path, expected_parent, maximum=16384):
    path, parent = Path(path).absolute(), Path(expected_parent).absolute()
    require(parent.is_dir() and not parent.is_symlink() and parent.resolve() == parent,
            'Result parent must be the exact attested owned sandbox/evidence directory')
    require(path.parent == parent and not path.is_symlink() and path.is_file() and path.resolve() == path,
            'Result path must not escape its attested parent')
    with path.open('rb') as stream:
        raw = stream.read(maximum + 1)
    require(0 < len(raw) <= maximum, 'Unbounded/empty L08 result')
    try:
        result = json.loads(raw, object_pairs_hook=unique)
    except (UnicodeError, ValueError) as error:
        raise RuntimeError('Malformed L08 result') from error
    require(isinstance(result, dict), 'L08 result is not an object')
    return result


def storage_result_names():
    return ['parlor-l08-storage-%d-%s.json' % (boot, action)
            for boot, actions in STORAGE_PLAN.items() for action in actions]


def _context(record, token, mode, scenario):
    require(isinstance(record, dict) and set(record) == {
        'schema_version', 'scenario', 'signing_mode', 'run_token', 'process_boot', 'boot_ordinal',
        'action', 'loaded_app_images', 'loaded_compose_framework', 'loader_environment', 'observation'},
        'Unexpected L08 context schema')
    require(type(record['schema_version']) is int and record['schema_version'] == 1 and
            record['scenario'] == scenario and record['signing_mode'] == mode and mode in {'disabled', 'adhoc'} and
            record['run_token'] == token and isinstance(record['process_boot'], str) and
            UUID.fullmatch(record['process_boot']), 'Missing exact L08 task/signing/process binding')
    validate_framework_observation(record['loaded_compose_framework'])
    validate_loader_environment(record['loader_environment'])


def _framework_inventory_keys(records, framework_inventories):
    # The existing binder verifies every schema/byte/UUID below. Require a
    # closed union first so per-boot filtering cannot hide an unused product.
    require(isinstance(framework_inventories, list) and 1 <= len(framework_inventories) <= 9,
            'Missing/unbounded L08 framework inventories')
    expected = {('installed-app-bundle', FRAMEWORK_PATH)}
    for row in records:
        require(isinstance(row, dict), 'Invalid L08 operation record')
        framework = validate_framework_observation(row.get('loaded_compose_framework'))
        expected.add((framework['origin'], framework['path']))
    actual = {}
    for row in framework_inventories:
        require(isinstance(row, dict) and set(row) == {'origin', 'path', 'bytes', 'sha256', 'architectures'},
                'Unexpected framework artifact schema')
        require(isinstance(row['origin'], str) and isinstance(row['path'], str), 'Invalid framework artifact key')
        key = (row['origin'], row['path'])
        require(key not in actual, 'Duplicate framework artifact provenance')
        actual[key] = row
    require(set(actual) == expected, 'Missing/unobserved framework inventory')
    return actual


def verify_storage(records, scenario, token, log, mode, built, installed, framework_inventories):
    require(isinstance(token, str) and UUID.fullmatch(token), 'Invalid L08 run token')
    require(isinstance(records, list) and len(records) == sum(map(len, STORAGE_PLAN.values())),
            'Every explicit save/load/Home/continue/discard step needs separate durable evidence')
    rows, starts = common(scenario, 'l08-storage')
    require(scenario['runToken'] == token and len(starts) == 13 and
            all(row['fixture'] == 'existing' for row in starts), 'Missing thirteen distinct original-App cold starts')
    for event, _ in rows:
        require(type(event['ordinal']) is int and type(event['controllerCreations']) is int,
                'L08 counters must be integer observations')
        if event['phase'] != 'before_main':
            require(geometry_fills_window(event['native']['geometry'], 'portrait'), 'L08 native container geometry failed')
    inventories = _framework_inventory_keys(records, framework_inventories)
    index = 0
    per_boot = []
    for ordinal, actions in STORAGE_PLAN.items():
        current = []
        for command, action in enumerate(actions, 1):
            record = records[index]
            index += 1
            _context(record, token, mode, 'l08-storage')
            require(type(record['boot_ordinal']) is int and record['boot_ordinal'] == ordinal and
                    record['action'] == action and record['process_boot'] == starts[ordinal - 1]['boot'],
                    'Missing/reordered/cross-process L08 operation')
            observation = record['observation']
            require(isinstance(observation, dict) and set(observation) == {
                'schema_version', 'status', 'boot_ordinal', 'command_ordinal', 'run_token', 'action',
                'checks', 'physical_or_store_evidence'}, 'Unexpected/failed L08 boundary observation')
            require(type(observation['schema_version']) is int and observation['schema_version'] == 1 and
                    observation['status'] == 'PASS' and type(observation['boot_ordinal']) is int and
                    observation['boot_ordinal'] == ordinal and type(observation['command_ordinal']) is int and
                    observation['command_ordinal'] == command and observation['run_token'] == token and
                    observation['action'] == action and observation['physical_or_store_evidence'] is False,
                    'L08 operation did not execute in the bound process or overclaimed external proof')
            checks = observation['checks']
            require(isinstance(checks, dict) and set(checks) == STORAGE_CHECKS[action] and
                    all(value is True for value in checks.values()), 'Incomplete/false/unknown L08 invariant')
            current.append(record)
        require(all(row['loaded_app_images'] == current[0]['loaded_app_images'] and
                    row['loaded_compose_framework'] == current[0]['loaded_compose_framework'] and
                    row['loader_environment'] == current[0]['loader_environment'] for row in current),
                'One L08 process changed its loaded artifact binding')
        # Reuse the existing real built/installed/UUID/hash binding, one process
        # at a time; do not raise that validator's eight-boot resource ceiling.
        origin = current[0]['loaded_compose_framework']
        keys = {('installed-app-bundle', FRAMEWORK_PATH), (origin['origin'], origin['path'])}
        bind_loaded_images(built, installed, [current[0]], [inventories[key] for key in sorted(keys)])
        per_boot.append(current[0])
    launch_rows = []
    for line in log.splitlines():
        if line.startswith('PARLOR_L08_STORAGE_BOOT '):
            require(len(line.encode()) <= 2048 and len(launch_rows) < 13, 'Unbounded L08 launch receipt')
            launch_rows.append(json.loads(line.removeprefix('PARLOR_L08_STORAGE_BOOT '), object_pairs_hook=unique))
    require(len(launch_rows) == 13, 'Every L08 cold boot needs its executed XCTest receipt')
    for ordinal, row in enumerate(launch_rows, 1):
        require(isinstance(row, dict) and set(row) == {
            'schema_version', 'boot_ordinal', 'process_boot', 'actions', 'foreground', 'alert_present', 'run_token'},
            'Unexpected L08 XCTest receipt schema')
        require(type(row['schema_version']) is int and row['schema_version'] == 1 and
                type(row['boot_ordinal']) is int and row['boot_ordinal'] == ordinal and
                row['process_boot'] == per_boot[ordinal - 1]['process_boot'] and row['run_token'] == token and
                row['actions'] == list(STORAGE_PLAN[ordinal]) and row['foreground'] is True and row['alert_present'] is False,
                'L08 XCTest and durable app-host observations disagree')
    return dict(status='PASS', current_source_native_storage='PASS', process_restarts=13,
                game_variants=['whodunit-classic', 'whodunit-elimination', 'mafia-doctor-on', 'mafia-doctor-off'],
                real_home_resume_and_terminal_controller_traces=5, mafia_on_consecutive_nights=4,
                mafia_off_trace='alternate, explicit skip, then previous pre-skip target; illegal adjacent repeat rejected',
                valid_legacy_migration_and_bad_neighbor='PASS', explicit_retry_discard='PASS',
                recognized_damaged_protected_with_valid_legacy='Fail closed, retain excluded recoverable legacy; actual Retry remains failed and explicit Discard removes both',
                file_metadata='observed backup exclusion and complete protection; not physical backup/lock evidence',
                physical_or_store_evidence=False,
                limits='Synthetic legal action drivers in an actual copied Debug simulator app. Not a full UI playthrough, power-loss/iCloud/physical LAN/Store proof.')


HOST_PLAN = ('prepare', 'capture', 'language-en', 'cycle-1', 'resume-1', 'language-ar',
             'cycle-2', 'resume-2', 'remount', 'language-system', 'cycle-3', 'resume-3', 'active-ack', 'leave')
HOST_CHECKS = {
    'prepare': {'real_owner_acquisition', 'admissions_frozen_once', 'strict_start_barrier',
                'real_host_flow_started', 'direct_peer_snapshots', 'test_transport_explicit'},
    'capture': {'canonical_capture_once', 'actual_setup_settings', 'retained_private_assignment'},
    'language': {'actual_koin_settings_store', 'actual_app_language_provider', 'same_session_private_state'},
    'cycle': {'actual_uikit_background_foreground', 'actual_koin_lifecycle_coordinator',
              'suspended_command_rejected', 'resuming_command_rejected', 'original_deadline_retained',
              'same_session_private_state'},
    'resume': {'synthetic_resume_explicit', 'same_session_private_state'},
    'remount': {'presentation_remounted_once', 'runtime_not_recreated', 'same_session_private_state'},
    'active-ack': {'active_authoritative_ack_accepted', 'own_player_only', 'no_readiness_bypass'},
    'leave': {'actual_final_leave', 'owner_idle_runtime_released', 'room_closed_once', 'all_fixture_workers_stopped'},
}
HOST_VARIANTS = ('whodunit-classic', 'mafia-doctor-on', 'mafia-doctor-off')
HOST_COUNTERS = {'mounts', 'disposals', 'background_cycles', 'foreground_cycles', 'suspended_checks', 'resuming_checks'}


def host_checks(action):
    kind = next((prefix for prefix in ('language', 'cycle', 'resume') if action.startswith(prefix + '-')), action)
    return HOST_CHECKS[kind]


def host_result_names():
    return ['parlor-l08-host-%d-%s.json' % (boot, action) for boot in range(1, 4) for action in HOST_PLAN]


def framework_subset(runs, all_runs, inventories):
    """Check the complete provenance union before selecting an old/new subgate."""
    complete = _framework_inventory_keys(all_runs, inventories)
    expected = {('installed-app-bundle', FRAMEWORK_PATH)}
    for run in runs:
        require(any(run is candidate for candidate in all_runs), 'Subgate run is not an actually inventoried observation')
        observed = validate_framework_observation(run.get('loaded_compose_framework'))
        expected.add((observed['origin'], observed['path']))
    return [complete[key] for key in sorted(expected)]


def _host_language(index):
    if index <= 2 or index >= 10:
        return 'system'
    if index <= 5:
        return 'en'
    return 'ar'


def _host_cycles(index):
    return 0 if index <= 3 else 1 if index <= 6 else 2 if index <= 10 else 3


def verify_host(records, scenario, token, log, mode, built, installed, framework_inventories):
    require(isinstance(token, str) and UUID.fullmatch(token), 'Invalid host run token')
    require(isinstance(records, list) and len(records) == 3 * len(HOST_PLAN), 'Missing/extra native host operations')
    rows, starts = common(scenario, 'l08-host')
    require(scenario['runToken'] == token and len(starts) == 3 and
            all(row['fixture'] == 'existing' for row in starts), 'Host matrix needs three independent original-App starts')
    inventories = _framework_inventory_keys(records, framework_inventories)
    by_sequence = {event['ordinal']: (event, value) for event, value in rows}
    for event, value in rows:
        require(type(event['ordinal']) is int and type(event['controllerCreations']) is int, 'Invalid host observation counter')
        if event['phase'] != 'before_main':
            require(geometry_fills_window(event['native']['geometry'], 'portrait'), 'Native host geometry diverged')
            require(value['surface'] == 'none' and not value['checkpointCaptured'] and value['commandOrdinal'] == 0,
                    'Host fixture must not impersonate existing local-session/Settings scenario')
    seen_sequences, per_boot = [], []
    for boot in range(1, 4):
        original = starts[boot - 1]['preferences']
        require(original['setting'] == 'system' and original['ownerPresent'] is False,
                'Host scenario must preserve initial real System ownership')
        current = records[(boot - 1) * len(HOST_PLAN):boot * len(HOST_PLAN)]
        capture = None
        for index, (action, record) in enumerate(zip(HOST_PLAN, current), 1):
            require(isinstance(record, dict) and type(record.get('observation_sequence')) is int,
                    'Native host operation needs its exact durable UIKit observation')
            _context({key: value for key, value in record.items() if key != 'observation_sequence'}, token, mode, 'l08-host')
            require(type(record['boot_ordinal']) is int and record['boot_ordinal'] == boot and
                    record['process_boot'] == starts[boot - 1]['boot'] and record['action'] == action,
                    'Reordered/cross-process host operation')
            sequence = record['observation_sequence']
            require(sequence in by_sequence and sequence not in seen_sequences and
                    (not seen_sequences or sequence > seen_sequences[-1]), 'Missing/reused/stale host native observation sequence')
            seen_sequences.append(sequence)
            event, composition = by_sequence[sequence]
            require(event['phase'] == 'sample' and event['boot'] == record['process_boot'],
                    'Host observation sequence refers to a different process/phase')
            value = record['observation']
            require(isinstance(value, dict) and set(value) == {
                'schema_version', 'status', 'run_token', 'boot_ordinal', 'command_ordinal', 'action', 'variant',
                'checks', 'host', 'physical_or_store_evidence'}, 'Unexpected/failed host boundary schema')
            require(type(value['schema_version']) is int and value['schema_version'] == 1 and value['status'] == 'PASS' and
                    value['run_token'] == token and type(value['boot_ordinal']) is int and value['boot_ordinal'] == boot and
                    type(value['command_ordinal']) is int and value['command_ordinal'] == index and value['action'] == action and
                    value['variant'] == HOST_VARIANTS[boot - 1] and value['physical_or_store_evidence'] is False,
                    'Host command binding/order/variant or honest evidence scope failed')
            checks = value['checks']
            require(isinstance(checks, dict) and set(checks) == host_checks(action) and
                    all(flag is True for flag in checks.values()), 'Missing/unknown/false host invariant')
            host = value['host']
            require(isinstance(host, dict) and set(host) == HOST_COUNTERS | {'direction', 'room_state', 'worker_failure'} and
                    all(type(host[key]) is int for key in HOST_COUNTERS) and host['worker_failure'] is False,
                    'Unexpected host state or unhandled owned-worker failure')
            cycles = _host_cycles(index)
            expected_room = 'none' if action == 'leave' else 'resuming' if action.startswith('cycle-') else 'active'
            require(host['mounts'] == (1 if index < 9 else 2) and
                    host['disposals'] == (2 if action == 'leave' else 1 if index >= 9 else 0) and
                    all(host[key] == cycles for key in HOST_COUNTERS - {'mounts', 'disposals'}) and
                    host['room_state'] == expected_room, 'Missing/extra lifecycle, remount, guard or final cleanup event')
            language = _host_language(index)
            expected_language = original['preferredLanguage'] if language == 'system' else language
            expected_direction = 'rtl' if expected_language == 'ar' else 'ltr'
            preference = event['preferences']
            require(preference['setting'] == language and preference['preferredLanguage'] == expected_language and
                    event['nativeDirection'] == 'force_' + expected_direction and host['direction'] == expected_direction,
                    'Real settings, native UIKit and actual host Compose direction disagree')
            if language == 'system':
                require(preference['ownerPresent'] is False and preference['appLanguages'] == original['appLanguages'],
                        'System did not restore original real preference ownership')
            else:
                require(preference['ownerInstalled'] == language and preference['appLanguages'] == [language] and
                        preference['ownerPrevious'] == original['appLanguages'], 'Explicit host language stole System ownership')
            if action == 'capture':
                capture = composition
            if cycles:
                require(capture is not None and
                        composition['backgroundCallbacks'] >= capture['backgroundCallbacks'] + cycles and
                        composition['foregroundCallbacks'] >= capture['foregroundCallbacks'] + cycles,
                        'Synthetic transport cycles lack actual production UIKit callback observations')
        require(all(row['loaded_app_images'] == current[0]['loaded_app_images'] and
                    row['loaded_compose_framework'] == current[0]['loaded_compose_framework'] and
                    row['loader_environment'] == current[0]['loader_environment'] for row in current),
                'One native host process changed loaded-artifact provenance')
        origin = current[0]['loaded_compose_framework']
        keys = {('installed-app-bundle', FRAMEWORK_PATH), (origin['origin'], origin['path'])}
        bind_loaded_images(built, installed, [current[0]], [inventories[key] for key in sorted(keys)])
        per_boot.append(current[0])
    launches = []
    for line in log.splitlines():
        if line.startswith('PARLOR_L08_HOST_BOOT '):
            require(len(line.encode()) <= 2048 and len(launches) < 3, 'Unbounded native host XCTest receipt')
            launches.append(json.loads(line.removeprefix('PARLOR_L08_HOST_BOOT '), object_pairs_hook=unique))
    require(len(launches) == 3, 'Missing executed native host XCTest receipt')
    for boot, row in enumerate(launches, 1):
        require(isinstance(row, dict) and set(row) == {
            'schema_version', 'boot_ordinal', 'process_boot', 'actions', 'foreground', 'alert_present', 'run_token'},
            'Unexpected native host XCTest receipt schema')
        require(type(row['schema_version']) is int and row['schema_version'] == 1 and
                type(row['boot_ordinal']) is int and row['boot_ordinal'] == boot and
                row['process_boot'] == per_boot[boot - 1]['process_boot'] and row['run_token'] == token and
                row['actions'] == list(HOST_PLAN) and row['foreground'] is True and row['alert_present'] is False,
                'Native host XCTest and durable observations disagree')
    return dict(status='PASS', current_source_native_started_host='PASS', variants=list(HOST_VARIANTS),
                native_processes=3, actual_background_foreground_cycles=9, language_matrix=['en', 'ar', 'system'],
                strict_start_barrier='Production session bridge plus synthetic pre-admitted transport-attested peers',
                mafia_configuration='Original setup switch and Start callbacks; ON/OFF selected before capture',
                canonical_owner_runtime_controller_flow_private_state='Write-once in-process comparisons; not secret receipts',
                remounts=3, suspension_and_resuming_guards=18, final_leave_worker_cleanup='PASS',
                physical_or_store_evidence=False, real_p2pkit_lan_evidence=False,
                limits='Original App language/theme/UIKit and production process owner/lifecycle coordinator/game flows; copy-only presentation seam and explicit ControlledStartRoom transport adapter. No lobby navigation, discovery, credential recovery, physical peers, full multiplayer game, device lock, or Store proof.')


def preserve_l08_evidence(container, evidence):
    """Only exact files inside the runner-attested task simulator are readable."""
    parent = Path(container).absolute() / 'tmp'
    destination = Path(evidence).absolute()
    require(destination.is_dir() and not destination.is_symlink() and destination.resolve() == destination,
            'L08 evidence destination is not an exact owned directory')
    require(parent.is_dir() and not parent.is_symlink() and parent.resolve() == parent,
            'L08 app result directory escaped attested simulator container')
    names = [(name, 16384, 'l08-storage') for name in storage_result_names()] + [
        (name, 24576, 'l08-host') for name in host_result_names()]
    preserved = []
    for name, maximum, scenario in names:
        result = parent / name
        if not result.exists() and not result.is_symlink():
            continue  # A failed/unexecuted step remains missing, never invented.
        value = read_owned_result(result, parent, maximum)
        validate_preservable_operation(value, scenario)
        require(name == 'parlor-%s-%d-%s.json' % (scenario, value['boot_ordinal'], value['action']),
                'L08 receipt does not belong to its exact planned filename')
        with (destination / name).open('x') as stream:
            stream.write(json.dumps(value, sort_keys=True) + '\n')
        preserved.append(name)
    return preserved


def validate_preservable_operation(record, scenario):
    """Reject unknown fields before durable copying, including failure receipts.

    This is a sanitization boundary, not acceptance of a successful native run;
    complete ordering, original source/native binding and counters are verified
    later by verify_storage/verify_host. Closed failed steps remain evidence.
    """
    require(isinstance(record, dict), 'Invalid L08 operation envelope')
    token, mode = record.get('run_token'), record.get('signing_mode')
    require(isinstance(token, str) and UUID.fullmatch(token), 'Invalid L08 operation token')
    _context({key: value for key, value in record.items() if scenario == 'l08-storage' or key != 'observation_sequence'},
             token, mode, scenario)
    boot, action = record['boot_ordinal'], record['action']
    require(type(boot) is int and isinstance(action, str), 'Invalid L08 operation identity')
    require((scenario == 'l08-storage' and boot in STORAGE_PLAN and action in STORAGE_PLAN[boot]) or
            (scenario == 'l08-host' and 1 <= boot <= 3 and action in HOST_PLAN and
             type(record.get('observation_sequence')) is int and record['observation_sequence'] > 0),
            'Unplanned L08 operation')
    images = record['loaded_app_images']
    require(isinstance(images, list) and 1 <= len(images) <= 128 and all(safe_relative(item) for item in images) and
            images == sorted(set(images)), 'Unsafe/unbounded L08 loaded-image provenance')
    value = record['observation']
    require(isinstance(value, dict), 'L08 operation lacks closed observation')
    require(type(value.get('schema_version')) is int and value['schema_version'] == 1 and
            value.get('run_token') == token and type(value.get('boot_ordinal')) is int and
            value['boot_ordinal'] == boot and value.get('action') == action, 'L08 observation context disagrees')
    if value.get('status') == 'FAIL':
        require(set(value) == {'schema_version', 'status', 'run_token', 'boot_ordinal', 'action', 'stage', 'reason'} and
                isinstance(value['reason'], str) and value['reason'] in {'cancelled', 'fixture_or_boundary_failure'} and
                isinstance(value['stage'], str) and
                value['stage'] in (STORAGE_FAILURE_STAGES if scenario == 'l08-storage' else {'context', *HOST_PLAN}),
                'Unknown failure metadata must not enter retained evidence')
        return
    keys = {'schema_version', 'status', 'run_token', 'boot_ordinal', 'command_ordinal', 'action',
            'checks', 'physical_or_store_evidence'} | ({'variant', 'host'} if scenario == 'l08-host' else set())
    require(set(value) == keys and value['status'] == 'PASS' and type(value['command_ordinal']) is int and
            1 <= value['command_ordinal'] <= 14 and value['physical_or_store_evidence'] is False,
            'Unknown observation metadata must not enter retained evidence')
    expected = STORAGE_CHECKS[action] if scenario == 'l08-storage' else host_checks(action)
    require(isinstance(value['checks'], dict) and set(value['checks']) == expected and
            all(type(flag) is bool for flag in value['checks'].values()), 'Unsafe invariant metadata')
    if scenario == 'l08-host':
        host = value['host']
        require(value['variant'] in HOST_VARIANTS and isinstance(host, dict) and
                set(host) == HOST_COUNTERS | {'direction', 'room_state', 'worker_failure'} and
                all(type(host[key]) is int for key in HOST_COUNTERS) and type(host['worker_failure']) is bool and
                host['direction'] in {'ltr', 'rtl', 'none'} and
                host['room_state'] in {'none', 'active', 'suspended', 'resuming', 'closed'},
                'Unsafe host observation metadata')
