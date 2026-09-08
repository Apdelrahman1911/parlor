"""Strict functional/metadata separation. This parser can never satisfy original L08."""
import json
from pathlib import Path

from artifact_inventory import FRAMEWORK_PATH, bind_loaded_images, safe_relative
from l08_receipts import (STORAGE_PLAN, STORAGE_CHECKS, STORAGE_FAILURE_STAGES, UUID,
                          _context, _framework_inventory_keys, _validate_protection_read,
                          read_owned_result, unique)
from probe_validation import common, geometry_fills_window, require

SCENARIO = 'l08-storage-functional'
KIND = SCENARIO
BOOT_PREFIX = 'PARLOR_L08_STORAGE_FUNCTIONAL_BOOT '
INNER_MAXIMUM = 4096
OUTER_MAXIMUM = 16384
FUNCTIONAL_CHECKS = {
    action: {('owned_metadata_and_header' if check == 'protected_metadata' else check) for check in checks}
    for action, checks in STORAGE_CHECKS.items()
}


def comparison_plan(boot, action):
    require(type(boot) is int and boot in STORAGE_PLAN and action in STORAGE_PLAN[boot],
            'Unplanned functional metadata observation')
    sites = []
    if action in {'save', 'load'}:
        sites = [(action, ('wc', 'we', 'mon', 'moff')[(boot - 1) // 2])]
    elif action == 'legacy-seed':
        sites = [('dual-before-legacy-seed', 'dual'), ('dual-after-tag-damage', 'dual')]
    elif action == 'legacy-load':
        sites = [('load', 'legacy'), ('legacy-load-dual', 'dual')]
    elif action == 'dual-preserved':
        sites = [('retained-dual', 'dual')]
    return [dict(ordinal=index + 1, site=site, alias=alias, target=target)
            for index, (site, alias, target) in enumerate(
                (site, alias, target) for site, alias in sites for target in ('directory', 'file'))]


def functional_result_names():
    return ['parlor-%s-%d-%s.json' % (SCENARIO, boot, action)
            for boot, actions in STORAGE_PLAN.items() for action in actions]


def validate_comparisons(rows, boot, action, complete):
    expected = comparison_plan(boot, action)
    require(isinstance(rows, list) and len(rows) <= len(expected) and len(rows) <= 4 and
            (not complete or len(rows) == len(expected)), 'Missing/extra functional Complete comparisons')
    for row, planned in zip(rows, expected):
        require(isinstance(row, dict) and set(row) == set(planned) | {'attributes', 'comparison'} and
                type(row['ordinal']) is int and all(row[key] == value for key, value in planned.items()),
                'Reordered, duplicated or wrongly scoped Complete comparison')
        _validate_protection_read(row['attributes'])
        require(row['attributes']['query'] == 'returned',
                'Only an actually returned primary getter can have a Complete comparison')
        require(row['comparison'] == ('PASS' if row['attributes']['value'] == 'complete' else 'FAIL'),
                'Required Complete equality disagrees with actual observed value')
    return rows


def validate_preservable_functional_operation(record):
    require(isinstance(record, dict), 'Invalid functional operation envelope')
    token, mode = record.get('run_token'), record.get('signing_mode')
    require(isinstance(token, str) and UUID.fullmatch(token) and isinstance(mode, str) and
            mode in {'disabled', 'adhoc'}, 'Invalid functional operation token or signing mode')
    _context(record, token, mode, SCENARIO)
    boot, action = record['boot_ordinal'], record['action']
    require(type(boot) is int and boot in STORAGE_PLAN and isinstance(action, str) and
            action in STORAGE_PLAN[boot], 'Unplanned functional operation identity')
    images = record['loaded_app_images']
    require(isinstance(images, list) and 1 <= len(images) <= 128 and all(safe_relative(item) for item in images) and
            images == sorted(set(images)), 'Unsafe functional image provenance')
    value = record['observation']
    require(isinstance(value, dict) and type(value.get('schema_version')) is int and value['schema_version'] == 1 and
            value.get('kind') == KIND and value.get('run_token') == token and
            type(value.get('boot_ordinal')) is int and value['boot_ordinal'] == boot and
            value.get('action') == action and type(value.get('command_ordinal')) is int and
            value['command_ordinal'] == STORAGE_PLAN[boot].index(action) + 1 and
            value.get('physical_or_store_evidence') is False,
            'Functional observation kind/context disagrees or overclaims external proof')
    fields = {'schema_version', 'kind', 'functional_status', 'run_token', 'boot_ordinal', 'command_ordinal',
              'action', 'complete_comparisons', 'physical_or_store_evidence'}
    if value.get('functional_status') == 'FAIL':
        require(set(value) == fields | {'stage', 'reason'} and isinstance(value['stage'], str) and
                value['stage'] in STORAGE_FAILURE_STAGES and
                isinstance(value['reason'], str) and value['reason'] in {'cancelled', 'fixture_or_boundary_failure'},
                'Unknown functional failure metadata')
        validate_comparisons(value['complete_comparisons'], boot, action, complete=False)
    else:
        require(set(value) == fields | {'functional_checks'} and value.get('functional_status') == 'PASS',
                'Unknown functional observation schema')
        checks = value['functional_checks']
        require(isinstance(checks, dict) and set(checks) == FUNCTIONAL_CHECKS[action] and
                all(type(flag) is bool for flag in checks.values()), 'Unsafe functional invariant metadata')
        validate_comparisons(value['complete_comparisons'], boot, action, complete=True)
    require(len(json.dumps(value, separators=(',', ':'), ensure_ascii=False).encode()) <= INNER_MAXIMUM,
            'Functional observation exceeds unchanged Kotlin-to-Swift bound')


def preserve_functional_evidence(container, evidence):
    parent = Path(container).absolute() / 'tmp'
    destination = Path(evidence).absolute()
    require(destination.is_dir() and not destination.is_symlink() and destination.resolve() == destination,
            'Functional evidence destination is not an exact owned directory')
    require(parent.is_dir() and not parent.is_symlink() and parent.resolve() == parent,
            'Functional app result directory escaped owned simulator container')
    preserved = []
    for name in functional_result_names():
        path = parent / name
        if not path.exists() and not path.is_symlink():
            continue  # Missing/unexecuted actions never become fabricated observations.
        value = read_owned_result(path, parent, OUTER_MAXIMUM)
        validate_preservable_functional_operation(value)
        require(name == 'parlor-%s-%d-%s.json' % (SCENARIO, value['boot_ordinal'], value['action']),
                'Functional receipt does not belong to its exact filename')
        with (destination / name).open('x') as stream:
            stream.write(json.dumps(value, sort_keys=True) + '\n')
        preserved.append(name)
    return preserved


def verify_storage_functional(records, scenario, token, log, mode, built, installed, framework_inventories):
    require(isinstance(token, str) and UUID.fullmatch(token), 'Invalid functional task token')
    require(isinstance(records, list) and len(records) == sum(map(len, STORAGE_PLAN.values())),
            'Every functional save/load/Home/continue/discard action needs durable evidence')
    rows, starts = common(scenario, SCENARIO)
    require(scenario['runToken'] == token and len(starts) == 13 and
            all(row['fixture'] == 'existing' for row in starts), 'Missing thirteen functional cold starts')
    for event, _ in rows:
        require(type(event['ordinal']) is int and type(event['controllerCreations']) is int,
                'Functional counters must be actual integers')
        if event['phase'] != 'before_main':
            require(geometry_fills_window(event['native']['geometry'], 'portrait'),
                    'Functional native container geometry failed')
    inventories = _framework_inventory_keys(records, framework_inventories)
    index, comparisons = 0, []
    per_boot = []
    for ordinal, actions in STORAGE_PLAN.items():
        current = []
        for command, action in enumerate(actions, 1):
            record = records[index]
            index += 1
            validate_preservable_functional_operation(record)
            _context(record, token, mode, SCENARIO)
            require(record['boot_ordinal'] == ordinal and record['action'] == action and
                    record['process_boot'] == starts[ordinal - 1]['boot'],
                    'Missing/reordered/cross-process functional operation')
            observation = record['observation']
            require(observation['functional_status'] == 'PASS' and observation['command_ordinal'] == command and
                    all(flag is True for flag in observation['functional_checks'].values()),
                    'A failed or incomplete functional boundary cannot pass')
            comparisons += [dict(boot_ordinal=ordinal, action=action, **row)
                            for row in observation['complete_comparisons']]
            current.append(record)
        require(all(row['loaded_app_images'] == current[0]['loaded_app_images'] and
                    row['loaded_compose_framework'] == current[0]['loaded_compose_framework'] and
                    row['loader_environment'] == current[0]['loader_environment'] for row in current),
                'One functional process changed loaded-artifact binding')
        origin = current[0]['loaded_compose_framework']
        keys = {('installed-app-bundle', FRAMEWORK_PATH), (origin['origin'], origin['path'])}
        bind_loaded_images(built, installed, [current[0]], [inventories[key] for key in sorted(keys)])
        per_boot.append(current[0])
    launches = []
    for line in log.splitlines():
        if line.startswith(BOOT_PREFIX):
            require(len(line.encode()) <= 2048 and len(launches) < 13, 'Unbounded functional XCTest receipt')
            launches.append(json.loads(line.removeprefix(BOOT_PREFIX), object_pairs_hook=unique))
    require(len(launches) == 13, 'Every functional cold boot needs its executed XCTest receipt')
    for ordinal, row in enumerate(launches, 1):
        require(isinstance(row, dict) and set(row) == {
            'schema_version', 'boot_ordinal', 'process_boot', 'actions', 'foreground', 'alert_present', 'run_token'},
            'Unexpected functional XCTest receipt schema')
        require(type(row['schema_version']) is int and row['schema_version'] == 1 and
                type(row['boot_ordinal']) is int and row['boot_ordinal'] == ordinal and
                row['process_boot'] == per_boot[ordinal - 1]['process_boot'] and row['run_token'] == token and
                row['actions'] == list(STORAGE_PLAN[ordinal]) and row['foreground'] is True and row['alert_present'] is False,
                'Functional XCTest and durable app observations disagree')
    require(len(comparisons) == 26, 'All thirteen metadata checkpoints must execute both actual comparisons')
    failed = [dict(boot_ordinal=row['boot_ordinal'], action=row['action'], ordinal=row['ordinal'],
                   site=row['site'], alias=row['alias'], target=row['target'],
                   observed_value=row['attributes']['value']) for row in comparisons if row['comparison'] == 'FAIL']
    return dict(kind=KIND, status='FUNCTIONAL_MATRIX_VERIFIED', functional_gate='PASS',
                complete_comparison_gate='FAIL' if failed else 'PASS', complete_comparison_count=26,
                successful_complete_comparisons=26 - len(failed), unsuccessful_complete_comparisons=failed,
                original_strict_l08='NOT_SATISFIED_BY_COMPANION',
                process_restarts=13, operations=20, real_home_resume_and_terminal_controller_traces=5,
                game_variants=['whodunit-classic', 'whodunit-elimination', 'mafia-doctor-on', 'mafia-doctor-off'],
                mafia_on_consecutive_nights=4,
                mafia_off_trace='alternate, explicit skip, pre-skip target; illegal adjacent repeat rejected',
                valid_legacy_migration_and_bad_neighbor='PASS', explicit_retry_discard='PASS',
                recognized_damaged_protected_with_valid_legacy='Fail closed; retain excluded legacy; actual Retry stays failed and Discard deletes both',
                owned_path_type_existence_backup_and_header='PASS',
                physical_or_store_evidence=False,
                limits='Synthetic legal drivers in the real copied Debug simulator app, not full UI playthrough, physical lock/backup, power loss, LAN or Store proof. Missing Complete metadata remains an unsuccessful required comparison.')
