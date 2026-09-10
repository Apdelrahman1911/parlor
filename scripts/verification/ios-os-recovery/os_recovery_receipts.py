"""Closed OS-only receipts. A failed bootstrap never becomes expected success."""
import json
import re

METHODS = dict(bootstrap='IOSAppLaunchUITests/testDSC01OSPublicBootstrap()',
               proof='IOSAppLaunchUITests/testDSC01OSPerAppRecovery()')
SUCCESS = 'SCOPED_OS_RECOVERY_VERIFIED'
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')


def require(value, message):
    if not value:
        raise RuntimeError('OS recovery: ' + message)


def marker(log, stage, uuid, helpers):
    rows = helpers('prerequisite_receipts').records(log, 'PARLOR_OS_RECOVERY_STAGE ',
        maximum=1, row_bytes=2048, total_bytes=2048)
    require(len(rows) == 1, 'missing unique stage marker')
    row = rows[0]
    require(set(row) == {'schemaVersion', 'stage', 'simulator', 'runToken'} and
        type(row['schemaVersion']) is int and row['schemaVersion'] == 1 and
        row['stage'] == stage and row['simulator'] == uuid and
        isinstance(row['runToken'], str) and UUID.fullmatch(row['runToken']), 'stage binding')
    return row


def xctest(summary, tests, stage, uuid):
    require(isinstance(summary, dict) and isinstance(tests, dict), 'XCTest objects')
    passed = summary.get('result') == 'Passed'
    expected = dict(totalTestCount=1, passedTests=int(passed), failedTests=int(not passed),
                    skippedTests=0, expectedFailures=0)
    failures = summary.get('testFailures')
    require(summary.get('result') in {'Passed', 'Failed'} and
        all(type(summary.get(k)) is int and summary[k] == v for k, v in expected.items()) and
        isinstance(failures, list) and (failures == [] if passed else 1 <= len(failures) <= 8), 'exact one-test summary')
    require(all(isinstance(row, dict) and row.get('targetName') == 'iosAppUITests' and
        row.get('testIdentifierString') == METHODS[stage] and row.get('testName') == METHODS[stage].split('/')[1] and
        isinstance(row.get('failureText'), str) and 0 < len(row['failureText'].encode()) <= 8192
        for row in failures), 'actual selected XCTest failure identity')
    configs, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    require(isinstance(configs, list) and isinstance(devices, list) and len(configs) == len(devices) == 1 and
        isinstance(configs[0], dict) and all(type(configs[0].get(k)) is int and configs[0][k] == v
        for k, v in expected.items() if k != 'totalTestCount') and all(isinstance(d, dict) and d.get('deviceId') == uuid and
        d.get('architecture') == 'arm64' and d.get('platform') == 'iOS Simulator'
        for d in (configs[0].get('device', {}), devices[0])), 'actual XCTest device')
    require(isinstance(tests.get('testNodes'), list), 'XCTest nodes')
    pending, cases, visited = [(node, 0) for node in tests['testNodes']], [], 0
    while pending:
        node, depth = pending.pop(); visited += 1
        require(isinstance(node, dict) and visited <= 512 and depth <= 16 and
            isinstance(node.get('children', []), list) and
            ('result' not in node or node['result'] in ({'Passed'} if passed else {'Passed', 'Failed'})) and
            (not passed or node.get('nodeType') != 'Failure Message'), 'XCTest node/result budget')
        if node.get('nodeType') == 'Test Case':
            cases.append(node)
        pending.extend((child, depth + 1) for child in node.get('children', []))
    require(len(cases) == 1 and cases[0].get('nodeIdentifier') == METHODS[stage] and
        cases[0].get('result') == ('Passed' if passed else 'Failed'), 'exact selected XCTest')
    return passed


def ordinary_bootstrap(stage, summary, tests, log, uuid, helpers):
    require(stage.get('command_completed') is True and stage.get('exit_code') in (0, 65) and
        type(stage['exit_code']) is int and stage.get('preserved') is True and
        stage.get('stop_exit_code') == 0 and stage.get('extraction_exit_codes') == {'summary': 0, 'tests': 0} and
        not stage.get('errors'), 'bootstrap did not finish/preserve/stop ordinarily')
    text = log + '\n' + json.dumps(summary) + '\n' + json.dumps(tests)
    require(not re.search(r'timed\s+out|execution time allowance.*exceed|exceed.*execution time allowance|'
        r'\btest (?:runner|execution|case)\b[^\n]*\btimeout\b', text, re.I), 'test timeout cannot admit proof')
    marker(log, 'bootstrap', uuid, helpers)
    passed = xctest(summary, tests, 'bootstrap', uuid)
    require(passed == (stage['exit_code'] == 0), 'bootstrap exit and actual test disagree')
    return passed  # False permits diagnostics only; never a successful bootstrap.


def process_images(report, records, token, uuid, mode, built, installed, artifacts, helpers):
    _, starts = helpers('probe_validation').common(report, 'os')
    require(report['runToken'] == token and 1 <= len(starts) == len(records) <= 8, 'every actual OS start needs an image row')
    keys = {'schema_version', 'kind', 'scenario', 'signing_mode', 'boot_ordinal', 'process_boot',
        'process_id', 'run_token', 'simulator_uuid', 'loaded_app_images', 'loaded_compose_framework', 'loader_environment'}
    for ordinal, (start, row) in enumerate(zip(starts, records), 1):
        require(isinstance(row, dict) and set(row) == keys and type(row['schema_version']) is int and
            row['schema_version'] == 1 and row['kind'] == 'os-recovery-process-images' and row['scenario'] == 'os' and
            row['signing_mode'] == mode == 'adhoc' and type(row['boot_ordinal']) is int and row['boot_ordinal'] == ordinal and
            row['process_boot'] == start['boot'] and row['run_token'] == token and row['simulator_uuid'] == uuid and
            type(row['process_id']) is int and 0 < row['process_id'] < 2**31, 'OS process image identity/order')
    helper = helpers('artifact_inventory')
    expected = {('installed-app-bundle', helper.FRAMEWORK_PATH)} | {
        (row['loaded_compose_framework']['origin'], row['loaded_compose_framework']['path']) for row in records}
    inventories = []
    for row in artifacts.values():
        if row['kind'] != 'compose-framework' or row['origin'] not in {'installed-app', 'owned-copy-build'}:
            continue
        origin = 'installed-app-bundle' if row['origin'] == 'installed-app' else row['origin']
        if (origin, row['relative_path']) in expected:
            inventories.append(dict(origin=origin, path=row['relative_path'], bytes=row['bytes'], sha256=row['sha256'],
                                    architectures=[dict(architecture='arm64', uuid=row['uuid'])]))
    return helper.bind_loaded_images(built, installed, records, inventories)


def verify_proof(summary, tests, log, report, images, uuid, mode, built, installed, artifacts, helpers):
    require(xctest(summary, tests, 'proof', uuid), 'proof XCTest failed')
    stage = marker(log, 'proof', uuid, helpers)
    prerequisite = helpers('public_interaction_receipts').verify_os_prerequisite(log)
    require(prerequisite['disposition'] == 'already-satisfied' and prerequisite['arabic_added'] is False and
        prerequisite['preexisting_configuration_verified'] is True, 'proof cannot add or retry a language')
    gate = helpers('os_preference_receipts').verify_os(report, log)
    actions = helpers('os_action_receipts').verify_os_action_receipts(log, report, gate)
    panes = helpers('os_pane_receipts').verify_os_pane_receipts(log)
    require(gate['status'] == actions['status'] == panes['status'] == 'PASS', 'original OS subgates')
    binding = process_images(report, images, stage['runToken'], uuid, mode, built, installed, artifacts, helpers)
    return dict(status='PASS', prerequisite=prerequisite, os_gate=gate, public_actions=actions,
                pane_provenance=panes, image_binding=binding, physical_or_store_evidence=False)


def classify(receipt):
    safe = (receipt.get('cleanup_status') == 'PASS' and not receipt.get('error') and
        not receipt.get('cleanup_errors') and not receipt.get('original_outputs_preserved') and
        all(receipt.get(k) is True for k in ('source_unchanged', 'controls_unchanged', 'copied_sources_unchanged',
                                            'postbuild_evidence_preserved', 'temporary_directory_removed')))
    os_pass = safe and receipt.get('os_observation_validation', {}).get('status') == 'PASS' and \
        receipt.get('os_provenance_status') == receipt.get('os_notice_package_status') == 'PASS'
    stages = receipt.get('os_recovery_stages', {})
    complete = os_pass and all(stages.get(k, {}).get('status') == 'PASS' for k in ('build', 'bootstrap', 'proof'))
    return dict(status=SUCCESS if complete else 'FAIL',
                os_subgate_status='PASS' if os_pass else 'FAIL' if stages.get('proof', {}).get('attempted') else 'NOT_RUN')
