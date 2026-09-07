"""Closed receipts for eight *executed* XCTest repetitions, not eight markers.

These pure parsers run no app, command, build, or test on import. Their positive
fixtures specify the accepted schema; only an actual XCResult can prove runtime
execution. Unknown result layouts fail closed for a later source-backed review.
"""
import json
import math
import re

PREFIX = 'PARLOR_NORMAL_SOURCE_LAUNCH '
METHOD = 'IOSAppLaunchUITests/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert()'
SELECTOR = 'iosAppUITests/' + METHOD[:-2]
REPETITIONS = 8
UUID = re.compile(r'[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}')
NODE_TYPES = {'Test Plan', 'Unit test bundle', 'UI test bundle', 'Test Suite', 'Test Case',
              'Device', 'Test Plan Configuration', 'Arguments', 'Repetition', 'Test Case Run',
              'Failure Message', 'Source Code Reference', 'Attachment', 'Expression',
              'Test Value', 'Runtime Warning'}


def unique_fields(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise RuntimeError('Duplicate JSON field in runtime evidence')
        value[key] = item
    return value


def read_json(raw, maximum=4 * 1024 * 1024):
    if not isinstance(raw, str) or not 0 < len(raw.encode()) <= maximum:
        raise RuntimeError('Empty or unbounded JSON runtime evidence')
    def invalid_constant(_value):
        raise RuntimeError('Nonfinite JSON runtime evidence')
    try:
        return json.loads(raw, object_pairs_hook=unique_fields, parse_constant=invalid_constant)
    except (ValueError, UnicodeError, RecursionError) as error:
        raise RuntimeError('Malformed JSON runtime evidence') from error


def seconds(value, minimum=0, maximum=240):
    if (type(value) not in (float, int) or not math.isfinite(value) or
            not minimum <= value <= maximum):
        raise RuntimeError('Missing, nonfinite or out-of-budget observation duration')
    return value


def verify_markers(lines):
    """Stream a bounded log; eight serial UUID trains, six observations each."""
    groups, current, total = [], [], 0
    seen = set()
    for line in lines:
        if not isinstance(line, str):
            raise RuntimeError('Invalid text log')
        total += len(line.encode())
        if total > 64 * 1024 * 1024 or len(line.encode()) > 1024 * 1024:
            raise RuntimeError('Xcode log budget exceeded')
        if not line.startswith(PREFIX):
            continue
        row = read_json(line[len(PREFIX):].rstrip('\n'), maximum=2048)
        if (not isinstance(row, dict) or not isinstance(row.get('observationID'), str) or
                UUID.fullmatch(row['observationID']) is None):
            raise RuntimeError('Missing exact launch-observation UUID')
        event = row.get('event')
        if event == 'begin':
            if set(row) != {'event', 'observationID'} or current or row['observationID'].lower() in seen:
                raise RuntimeError('Duplicate, nested or malformed launch beginning')
            if len(groups) == REPETITIONS:
                raise RuntimeError('More than eight attempted cold-launch observations')
            seen.add(row['observationID'].lower())
            current = [row]
        elif event == 'sample':
            if (set(row) != {'event', 'observationID', 'sample', 'elapsedSeconds',
                             'foreground', 'homeExists', 'alertVisible'} or not current or
                    row['observationID'] != current[0]['observationID'] or
                    type(row['sample']) is not int or row['sample'] != len(current) - 1 or
                    not 0 <= row['sample'] <= 5 or row['foreground'] is not True or
                    row['homeExists'] is not True or row['alertVisible'] is not False):
                raise RuntimeError('Incomplete, reordered or failing launch observation')
            elapsed = seconds(row['elapsedSeconds'])
            if len(current) > 1 and elapsed < current[-1]['elapsedSeconds']:
                raise RuntimeError('Launch observation clock moved backwards')
            if row['sample'] == 5:
                seconds(elapsed, minimum=10)
            current.append(row)
        elif event == 'complete':
            if (set(row) != {'event', 'observationID', 'elapsedSeconds'} or len(current) != 7 or
                    row['observationID'] != current[0]['observationID'] or
                    seconds(row['elapsedSeconds'], minimum=10) < current[-1]['elapsedSeconds']):
                raise RuntimeError('Unproven post-home observation completion')
            groups.append(dict(observation_id=current[0]['observationID'], samples=current[1:],
                               elapsed_seconds=row['elapsedSeconds']))
            current = []
        else:
            raise RuntimeError('Unknown launch-observation event')
    if current or len(groups) != REPETITIONS:
        raise RuntimeError('Exactly eight complete actual launch marker sequences are required')
    return dict(status='PASS', completed_observations=8, total_samples=48, observations=groups,
                limitation='XCTest-side periodic observations, not continuous liveness, PID identity, '
                           'mapped-image capture or proof against an intervening OS restart.')


def all_nodes(nodes):
    if not isinstance(nodes, list):
        raise RuntimeError('Missing typed XCResult node list')
    result, stack = [], [(item, 0) for item in reversed(nodes)]
    while stack:
        node, depth = stack.pop()
        if (not isinstance(node, dict) or node.get('nodeType') not in NODE_TYPES or
                not isinstance(node.get('name'), str) or depth > 16 or len(result) >= 1024):
            raise RuntimeError('Unknown or unbounded XCResult node structure')
        if 'result' in node and node['result'] != 'Passed':
            raise RuntimeError('Nonpassing actual XCTest node cannot be hidden by an aggregate')
        if node['nodeType'] == 'Failure Message':
            raise RuntimeError('Actual XCTest failure message is present')
        children = node.get('children', [])
        if not isinstance(children, list):
            raise RuntimeError('Invalid XCResult children')
        result.append(node)
        stack.extend((item, depth + 1) for item in reversed(children))
    return result


def verify_device(device, uuid):
    if (not isinstance(device, dict) or device.get('deviceId') != uuid or
            device.get('architecture') != 'arm64' or device.get('platform') != 'iOS Simulator'):
        raise RuntimeError('XCTest result did not use the one exact owned ARM64 simulator')


def executed_runs(nodes):
    all_nodes(nodes)  # Shared depth/node/type/failure bounds apply before recursion.
    rows = []
    def visit(current, parents):
        for node in current:
            identity = (node['nodeType'], node['name'], node.get('nodeIdentifier'), node.get('nodeIdentifierURL'))
            path = parents + (identity,)
            if node['nodeType'] == 'Test Case Run':
                if any(item['nodeType'] == 'Test Case Run' for item in all_nodes(node.get('children', []))):
                    raise RuntimeError('Nested Test Case Run records cannot inflate executed repetitions')
                rows.append((node, path))
            visit(node.get('children', []), path)
    visit(nodes, ())
    return rows


def verify_xctest(summary, tests, details, uuid):
    if not isinstance(uuid, str) or UUID.fullmatch(uuid) is None:
        raise RuntimeError('Unattested simulator identity')
    if not all(isinstance(value, dict) for value in (summary, tests, details)):
        raise RuntimeError('Missing actual XCResult objects')
    # XCResult can aggregate repetitions at method level. Neither allowed count
    # proves eight executions: the separate eight Test Case Run records do.
    if (summary.get('result') != 'Passed' or summary.get('testFailures') != [] or
            type(summary.get('totalTestCount')) is not int or
            summary['totalTestCount'] not in (1, 8) or
            type(summary.get('passedTests')) is not int or
            summary['passedTests'] != summary['totalTestCount'] or
            any(type(summary.get(key)) is not int or summary[key] != 0
                for key in ('failedTests', 'skippedTests', 'expectedFailures'))):
        raise RuntimeError('Actual XCTest aggregate contains failures, skips or unexplained counts')
    configurations = summary.get('devicesAndConfigurations')
    if not isinstance(configurations, list) or len(configurations) != 1:
        raise RuntimeError('One actual destination/configuration required')
    configuration = configurations[0]
    if (not isinstance(configuration, dict) or
            any(type(configuration.get(key)) is not int or configuration[key] != 0
                for key in ('failedTests', 'skippedTests', 'expectedFailures')) or
            type(configuration.get('passedTests')) is not int or
            configuration['passedTests'] != summary['passedTests']):
        raise RuntimeError('Per-destination XCTest counts disagree')
    verify_device(configuration.get('device'), uuid)
    for view in (tests, details):
        devices = view.get('devices')
        plans = view.get('testPlanConfigurations')
        if not isinstance(devices, list) or len(devices) != 1 or not isinstance(plans, list) or len(plans) != 1:
            raise RuntimeError('XCTest query did not retain the single configured destination')
        verify_device(devices[0], uuid)
    if tests['testPlanConfigurations'] != details['testPlanConfigurations']:
        raise RuntimeError('XCTest queries refer to different test plan configurations')
    if configuration.get('testPlanConfiguration') != tests['testPlanConfigurations'][0]:
        raise RuntimeError('XCTest summary configuration is not the executed test configuration')
    nodes = all_nodes(tests.get('testNodes'))
    cases = [node for node in nodes if node['nodeType'] == 'Test Case']
    if (len(cases) != 1 or cases[0].get('nodeIdentifier') != METHOD or cases[0].get('result') != 'Passed' or
            details.get('testIdentifier') != METHOD or details.get('testResult') != 'Passed'):
        raise RuntimeError('Exactly the selected normal-source method must execute')
    records = executed_runs(details.get('testRuns'))
    runs = [node for node, _path in records]
    if len(runs) != REPETITIONS or any(node.get('result') != 'Passed' for node in runs):
        raise RuntimeError('Exactly eight individually passing actual XCTest runs are required')
    # Include the real repetition hierarchy, not an invented array index: a
    # device-run name may legitimately repeat beneath eight distinct repetitions.
    identities = [path for _node, path in records]
    if len(set(identities)) != REPETITIONS:
        raise RuntimeError('Duplicate actual XCTest repetition records')
    durations = [seconds(node.get('durationInSeconds'), minimum=10) for node in runs]
    return dict(status='PASS', method=METHOD, executed_repetitions=8,
                actual_run_identities=identities, actual_run_durations_seconds=durations,
                reported_summary_total=summary['totalTestCount'], device_uuid=uuid,
                failures=0, skipped=0, expected_failures=0,
                limitation='One test method executed eight times, not eight distinct test methods. '
                           'Unknown XCResult repetition shapes are blocked, never inferred from markers.')
