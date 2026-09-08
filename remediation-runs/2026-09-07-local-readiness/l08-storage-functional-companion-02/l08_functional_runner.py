"""Pure companion selection/classification; imports never launch processes or write files."""
from pathlib import Path

from artifact_inventory import FRAMEWORK_PATH, bind_loaded_images
from l08_functional_receipts import (SCENARIO, KIND, functional_result_names,
                                    validate_preservable_functional_operation)
from l08_receipts import (UUID, _framework_inventory_keys, host_result_names, read_owned_result,
                          validate_preservable_operation)
from native_readiness_receipts import verify_boot_result
from probe_validation import require


def available_run_records(evidence, mode):
    """Validate every available bounded row without pretending missing steps ran."""
    require(isinstance(mode, str) and mode in {'disabled', 'adhoc'}, 'Invalid available observation signing mode')
    parent = Path(evidence).absolute()
    result = []
    for ordinal in range(1, 9):
        path = parent / ('parlor-native-readiness-boot-%d.json' % ordinal)
        if path.exists() or path.is_symlink():
            row = read_owned_result(path, parent, 49152)
            token = row.get('run_token')
            require(isinstance(token, str) and UUID.fullmatch(token), 'Invalid available native task token')
            verify_boot_result(row, ordinal, token, mode)
            result.append(row)
    for names, maximum, validate in (
        (functional_result_names(), 16384, validate_preservable_functional_operation),
        (host_result_names(), 24576, lambda row: validate_preservable_operation(row, 'l08-host')),
    ):
        for name in names:
            path = parent / name
            if path.exists() or path.is_symlink():
                row = read_owned_result(path, parent, maximum)
                validate(row)
                require(row['signing_mode'] == mode and
                        name == 'parlor-%s-%d-%s.json' % (row['scenario'], row['boot_ordinal'], row['action']),
                        'Available operation has wrong sign mode or filename identity')
                result.append(row)
    require(len(result) <= 70 and len({row['run_token'] for row in result}) <= 1,
            'Available image rows cross tasks or exceed this exact matrix')
    return result


def bind_available_images(records, built, installed, framework_inventories):
    """Only source/image binding for available rows, never matrix or XCTest success."""
    require(isinstance(records, list) and 1 <= len(records) <= 70,
            'No bounded available rows for image binding')
    for row in records:
        require(isinstance(row, dict), 'Invalid available image row')
        scenario = row.get('scenario', 'readiness')
        if scenario == 'readiness':
            token, mode, ordinal = row.get('run_token'), row.get('signing_mode'), row.get('boot_ordinal')
            require(isinstance(token, str) and UUID.fullmatch(token) and
                    isinstance(mode, str) and mode in {'disabled', 'adhoc'} and
                    type(ordinal) is int and 1 <= ordinal <= 8, 'Invalid available native row context')
            verify_boot_result(row, ordinal, token, mode)
        elif scenario == SCENARIO:
            validate_preservable_functional_operation(row)
        elif scenario == 'l08-host':
            validate_preservable_operation(row, 'l08-host')
        else:
            raise RuntimeError('Unknown available image row scenario')
    require(len({row['run_token'] for row in records}) == 1 and
            len({row['signing_mode'] for row in records}) == 1,
            'Available image rows cross task tokens or signing modes')
    require(len({(row.get('scenario', 'readiness'), row['boot_ordinal'], row.get('action'))
                 for row in records}) == len(records), 'Repeated available operation identity')
    inventories = _framework_inventory_keys(records, framework_inventories)
    grouped = {}
    for row in records:
        key = (row.get('scenario', 'readiness'), row['boot_ordinal'])
        require(key[0] in {'readiness', SCENARIO, 'l08-host'}, 'Unknown available image row scenario')
        grouped.setdefault(key, []).append(row)
    processes = set()
    bindings = []
    for (scenario, ordinal), rows in grouped.items():
        first = rows[0]
        require(first['process_boot'] not in processes, 'Available groups reused a cold-start process UUID')
        processes.add(first['process_boot'])
        require(all(all(row[key] == first[key] for key in (
            'process_boot', 'run_token', 'signing_mode', 'loaded_app_images',
            'loaded_compose_framework', 'loader_environment')) for row in rows),
            'Available process observations disagree about loaded-image ownership')
        origin = first['loaded_compose_framework']
        keys = {('installed-app-bundle', FRAMEWORK_PATH), (origin['origin'], origin['path'])}
        bound = bind_loaded_images(built, installed, [first], [inventories[key] for key in sorted(keys)])
        bindings.append(dict(scenario=scenario, boot_ordinal=ordinal, operation_rows=len(rows), binding=bound))
    return dict(status='AVAILABLE_OBSERVATIONS_IMAGE_BOUND', matrix_verified=False,
                observation_rows=len(records), process_groups=len(grouped), bindings=bindings,
                limits='Image provenance for available closed observations only. This does not accept incomplete scenarios, unsuccessful metadata, missing XCTest evidence or missing operations.')


def runtime_complete(receipt, xcode):
    if not isinstance(receipt, dict) or any(not isinstance(receipt.get(key), dict)
            for key in ('l08_storage_functional', 'xctest', 'l08_host')):
        return False
    functional = receipt.get('l08_storage_functional', {})
    return (type(xcode) is int and xcode == 0 and receipt.get('xctest', {}).get('result') == 'Passed' and
            receipt.get('embedded_gradle_stop_status') == 'PASS' and
            receipt.get('probe_harness_status') == 'observation_complete' and
            functional.get('kind') == KIND and functional.get('status') == 'FUNCTIONAL_MATRIX_VERIFIED' and
            functional.get('functional_gate') == 'PASS' and
            functional.get('complete_comparison_gate') in ('PASS', 'FAIL') and
            functional.get('original_strict_l08') == 'NOT_SATISFIED_BY_COMPANION' and
            receipt.get('l08_host', {}).get('status') == 'PASS')


def classify_final_companion(receipt):
    """Never return overall PASS, including when every Complete comparison matches."""
    result = dict(status='FAIL', strict_l08_gate='NOT_SATISFIED_BY_COMPANION')
    if not isinstance(receipt, dict):
        return result
    safe = (runtime_complete(receipt, receipt.get('xcodebuild_exit_code')) and
            receipt.get('runtime_evidence_status') == 'FUNCTIONAL_COMPANION_VERIFIED' and
            not receipt.get('error') and receipt.get('cleanup_status') == 'PASS' and
            all(isinstance(receipt.get(key), dict) for key in ('native_readiness', 'os_settings_gate')) and
            all(receipt.get(key) is True for key in (
                'source_unchanged', 'controls_unchanged', 'copied_sources_unchanged')))
    if not safe:
        return result
    if receipt.get('native_readiness', {}).get('storage_health') == 'FAIL':
        result['readiness_gate_failure'] = 'Observed native storage inconsistency remains a failed gate; not automatically an application defect.'
        return result
    result['status'] = 'PARTIALLY_VERIFIED'
    result['remaining_obligations'] = ['Original strict L08 was not executed by this separate functional companion.']
    if receipt.get('l08_storage_functional', {}).get('complete_comparison_gate') != 'PASS':
        result['remaining_obligations'].append('Required NSFileProtectionComplete comparisons remain unsuccessful; inspect all retained rows.')
    if receipt.get('native_readiness', {}).get('storage_health') != 'PASS':
        result['remaining_obligations'].append('Actual native storage-health subgate is not PASS.')
    os_gate = receipt.get('os_settings_gate', {})
    if os_gate.get('status') != 'PASS' or os_gate.get('actual_present_os_preference_coverage') is not True:
        result['remaining_obligations'].append('Actual OS-managed per-app preference subgate is not fully verified.')
    return result
