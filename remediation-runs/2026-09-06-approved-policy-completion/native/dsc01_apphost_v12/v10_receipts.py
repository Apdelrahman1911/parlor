"""Strict public OS interaction receipts, ADDITIONAL to every original oracle.

A successful supported URL, a marker, or these synthetic contracts is never
actual OS-selection evidence. Root first validates the unchanged full probes.
"""
import v8_receipts
from v9_receipts import contained


STAGES = ('initialPane', 'apps', 'appsSuccessor', 'parlor', 'appPane', 'language', 'english',
          'markerControls', 'marker', 'complete')
TERMINALS = {'language-control-unavailable': 'noRow', 'selection-unavailable': 'noSelection',
             'verified-english': 'verified'}
ACTION_PANES = {'apps': 'settings', 'parlor': 'apps', 'language': 'parlor', 'english': 'language',
                'showMarkers': 'audit', 'noRow': 'audit', 'noSelection': 'audit',
                'verified': 'audit', 'complete': 'audit'}
ACTION_STAGES = dict(apps='apps', parlor='parlor', language='language', english='english',
                     showMarkers='markerControls', noRow='marker', noSelection='marker', verified='marker', complete='complete')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def pane_eligible(fields):
    require(isinstance(fields, dict), 'Pane metadata is not an observation object')
    require(all(fields.get(key) is True for key in ('foreground', 'paneHittable')) and
            fields.get('keyboardPresent') is False and
            type(fields.get('alertCountCapped16')) is int and fields['alertCountCapped16'] == 0 and
            type(fields.get('paneCountCapped16')) is int and fields['paneCountCapped16'] == 1,
            'Actual pane must be unique, visible, uncovered and foreground')
    viewport = v8_receipts.rectangle(fields.get('viewport'))
    require(contained(v8_receipts.rectangle(fields.get('paneFrame')), viewport), 'Pane is outside measured viewport')
    # knownNavigationTitles use identifier queries for DIAGNOSTICS ONLY. Actual
    # pane eligibility is separately measured with the exact known label union.
    return viewport


def row_eligible(fields, control):
    require(isinstance(fields, dict), 'Target metadata is not an observation object')
    require(fields.get('control') == control and fields.get('pane') == ACTION_PANES[control],
            'Wrong semantic control or source pane')
    viewport = pane_eligible(fields)
    require(type(fields.get('targetCountCapped16')) is int and fields['targetCountCapped16'] == 1 and
            fields.get('targetHittable') is True and fields.get('targetEnabled') is True,
            'Actual target must be unique, hittable and enabled')
    expected_kinds = {'audit-button'} if ACTION_PANES[control] == 'audit' else {'known-cell', 'known-button', 'known-text'}
    require(fields.get('selectorKind') in expected_kinds, 'Unreviewed target-selector class')
    target = v8_receipts.rectangle(fields.get('targetFrame'))
    require(contained(target, viewport), 'Target is outside measured viewport')
    return target


def exactly(rows, stage, status):
    selected = [row for row in rows if (row['stage'], row['status']) == (stage, status)]
    require(len(selected) == 1, 'Required unique interaction stage missing/repeated: ' + stage + '/' + status)
    return selected[0]


def verify_v10_receipts(log, os_report, original_os_gate):
    contracts = v8_receipts.records(log, 'DSC01_OS_INTERACTION_DECISION_CONTRACT ',
        maximum=1, row_bytes=256, total_bytes=256)
    require(contracts == [dict(schemaVersion=1, cases=24, passed=True)] and
            type(contracts[0]['schemaVersion']) is int and type(contracts[0]['cases']) is int and
            contracts[0]['passed'] is True, 'Executed Swift OS interaction guard contract absent')
    rows = v8_receipts.records(log, 'DSC01_OS_APP_INTERACTION ', maximum=128, row_bytes=8192, total_bytes=262144)
    require(rows, 'Actual OS interaction receipts absent')
    for ordinal, row in enumerate(rows, 1):
        require(set(row) == {'schemaVersion', 'ordinal', 'stage', 'status', 'fields'} and
                type(row['schemaVersion']) is int and row['schemaVersion'] == 1 and
                type(row['ordinal']) is int and row['ordinal'] == ordinal and row['stage'] in STAGES and
                row['status'] in {'observe', 'before-activation', 'after-activation', 'PASS', 'BLOCKED'} and
                isinstance(row['fields'], dict), 'Malformed, reordered or unknown OS interaction receipt')
    initial = [row for row in rows if (row['stage'], row['status']) == ('initialPane', 'PASS')]
    require(len(initial) <= 1, 'Initial pane repeated')
    if initial:
        start = initial[0]['fields']; pane_eligible(start)
        require(start.get('pane') in {'settings', 'apps', 'parlor'}, 'Unknown initial public pane')
        expected_public = {'settings': ['apps', 'parlor'], 'apps': ['parlor'], 'parlor': []}[start['pane']] + ['language', 'english']
    else:
        expected_public = []
    before = [row for row in rows if row['status'] == 'before-activation']
    after = [row for row in rows if row['status'] == 'after-activation']
    require(before and len(before) == len(after), 'Required activations are absent or an activation lacks its after receipt')
    actions = []
    previous_after = 0
    for pre, post in zip(before, after):
        control = pre['fields'].get('control')
        require(control in ACTION_PANES and pre['stage'] == post['stage'] == ACTION_STAGES[control] and
                previous_after < pre['ordinal'] < post['ordinal'] and
                type(post['fields'].get('activationCount')) is int and
                post['fields'] == dict(control=control, activationCount=1, expectedSourcePane=ACTION_PANES[control]),
                'Wrong, repeated or unpaired OS interaction action')
        target = row_eligible(pre['fields'], control)
        stable = pre['fields'].get('stableFrames')
        require(isinstance(stable, list) and len(stable) == 3 and
                all(v8_receipts.near(v8_receipts.rectangle(frame), target) for frame in stable),
                'No three stable measured target frames before activation')
        samples = [row for row in rows if row['ordinal'] < pre['ordinal'] and row['stage'] == pre['stage'] and
                   row['status'] == 'observe' and row['fields'].get('control') == control][-3:]
        require(len(samples) == 3, 'Measured stable target observation train incomplete')
        for sample in samples:
            require(previous_after < sample['ordinal'], 'Action reused samples from before a previous activation')
            require(v8_receipts.near(row_eligible(sample['fields'], control), target), 'Stable observation train moved or was ineligible')
        actions.append(control)
        previous_after = post['ordinal']
    require(len(actions) == len(set(actions)), 'A semantic action was retried')
    public = [control for control in actions if ACTION_PANES[control] != 'audit']
    require(public == expected_public[:len(public)], 'Public Settings route absent, reordered or guessed')
    require(not initial or initial[0]['ordinal'] < before[0]['ordinal'], 'An action preceded proven initial pane')
    require(actions[-3:] in (['showMarkers', 'noRow', 'complete'], ['showMarkers', 'noSelection', 'complete'],
                            ['showMarkers', 'verified', 'complete']) and actions == public + actions[-3:],
            'Reporting expansion, marker and completion must be unique and follow every public action')
    panel = exactly(rows, 'markerControls', 'PASS')
    panels = panel['fields'].get('visibleMarkerRows')
    require(isinstance(panels, list) and len(panels) == 3, 'Actual expanded reporting-panel successor missing')
    frames = [row_eligible(fields, control) for fields, control in zip(panels, ('noRow', 'noSelection', 'verified'))]
    require(all(frame[3] >= 44 for frame in frames) and frames[0][1] + frames[0][3] <= frames[1][1] + 0.5 and
            frames[1][1] + frames[1][3] <= frames[2][1] + 0.5, 'Expanded markers are clipped, undersized or overlapping')
    marker = exactly(rows, 'marker', 'PASS'); completed = exactly(rows, 'complete', 'PASS')
    require(exactly(rows, 'markerControls', 'after-activation')['ordinal'] < panel['ordinal'] <
            exactly(rows, 'marker', 'before-activation')['ordinal'] < exactly(rows, 'marker', 'after-activation')['ordinal'] <
            marker['ordinal'] < exactly(rows, 'complete', 'before-activation')['ordinal'] <
            exactly(rows, 'complete', 'after-activation')['ordinal'] < completed['ordinal'],
            'Completion occurred before visible expansion and durable marker')
    require(os_report.get('scenario') == 'os' and os_report.get('completed') is True and
            isinstance(os_report.get('observations'), list), 'No complete original durable OS report')
    events = os_report['observations']
    require(events and all(isinstance(event, dict) for event in events), 'Missing original OS observations')
    terminal = events[-1].get('osDisposition')
    require(terminal in TERMINALS and actions[-2] == TERMINALS[terminal], 'Marker and actual durable outcome disagree')
    for record, is_completed in ((marker, False), (completed, True)):
        value = record['fields']
        require(set(value) == {'sequence', 'osDisposition', 'completed'} and type(value['sequence']) is int and
                value['osDisposition'] == terminal and value['completed'] is is_completed,
                'Marker/completion receipt missing exact durable sequence/status')
        matching = [event for event in events if event.get('ordinal') == value['sequence']]
        require(len(matching) == 1 and matching[0].get('osDisposition') == terminal and
                matching[0].get('phase') == ('complete' if is_completed else 'sample'),
                'Printed marker is not backed by the original durable probe')
    require(marker['fields']['sequence'] < completed['fields']['sequence'], 'Durable completion precedes marker')
    require(any(event.get('osDisposition') == 'opened' and event.get('ordinal', 257) < marker['fields']['sequence']
                for event in events), 'Supported URL callback must be durably observed before terminal reporting')
    blocked = [row for row in rows if row['status'] == 'BLOCKED']
    if terminal != 'verified-english':
        require(original_os_gate.get('status') == 'BLOCKED' and original_os_gate.get('reason') == terminal and len(blocked) == 1,
                'Unavailable public route is not an actual OS selection PASS')
        require((terminal == 'language-control-unavailable' and blocked[0]['stage'] in
                    {'initialPane', 'apps', 'appsSuccessor', 'parlor', 'appPane', 'language'} and 'language' not in public) or
                (terminal == 'selection-unavailable' and blocked[0]['stage'] == 'english' and public[-1:] == ['language']),
                'Blocked classification does not match the last actual public interaction')
        require(all(pre['ordinal'] < blocked[0]['ordinal'] for pre in before if pre['fields']['control'] in public),
                'A public action ran after a blocked route without authorization')
        return dict(status='BLOCKED', terminal=terminal, actual_public_actions=public, durable_reporting=True,
                    reason='Actual public per-app pane/language flow was not completed; markers and URL acceptance are not OS proof.')
    require(original_os_gate.get('status') == 'PASS' and not blocked and public == expected_public and
            public[-2:] == ['language', 'english'], 'Actual OS English selection and unchanged original oracles did not all pass')
    app_pane = exactly(rows, 'appPane', 'PASS'); pane_eligible(app_pane['fields'])
    require(app_pane['fields'].get('pane') == 'parlor' and app_pane['ordinal'] < exactly(rows, 'language', 'before-activation')['ordinal'],
            'App-specific pane was not proven before selecting language')
    if 'parlor' in public:
        require(exactly(rows, 'parlor', 'after-activation')['ordinal'] < app_pane['ordinal'], 'Parlor successor preceded its route action')
    language_pane = exactly(rows, 'english', 'PASS'); pane_eligible(language_pane['fields'])
    require(language_pane['fields'].get('pane') == 'language' and
            exactly(rows, 'language', 'after-activation')['ordinal'] < language_pane['ordinal'] <
            exactly(rows, 'english', 'before-activation')['ordinal'], 'Actual language picker successor missing')
    if 'apps' in public:
        successor = exactly(rows, 'appsSuccessor', 'PASS'); pane_eligible(successor['fields'])
        require(successor['fields'].get('pane') == 'apps' and exactly(rows, 'apps', 'after-activation')['ordinal'] <
                successor['ordinal'] < exactly(rows, 'parlor', 'before-activation')['ordinal'], 'Actual Apps successor missing')
    expected_passes = {'initialPane', 'appPane', 'english', 'markerControls', 'marker', 'complete'}
    if 'apps' in public:
        expected_passes.add('appsSuccessor')
    require([row['stage'] for row in rows if row['status'] == 'PASS'] ==
            [stage for stage in STAGES if stage in expected_passes], 'Unexpected or reordered successful observation stage')
    return dict(status='PASS', actual_public_actions=public, actual_app_pane=True, actual_os_english_selection=True,
                original_os_ar_system_restart_oracles_required=True, guarded_durable_reporting=True,
                swift_decision_contract_cases=24, rows=len(rows), physical_device_or_store_claim=False)
