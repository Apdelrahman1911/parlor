"""Additional bounded pane-query provenance, never an OS-selection oracle."""
import v8_receipts

PROPERTIES = ('identifier', 'title', 'label', 'value', 'placeholderValue')
TITLES = {'settings': ('Settings',), 'apps': ('Apps',), 'parlor': ('Parlor', 'بارلور'),
          'language': ('Language', 'Preferred Language'), 'audit': ()}
CONTRACT = dict(schemaVersion=1, attributeCases=12, unionCases=2, identityGuardCases=2, passed=True)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def inspect_pane(fields):
    require(isinstance(fields, dict) and isinstance(fields.get('pane'), str) and fields['pane'] in TITLES, 'Unknown pane provenance')
    pane = fields['pane']
    require(fields.get('paneSelectorMethod') == ('unchanged-audit-control' if pane == 'audit' else
            'known-public-identifying-properties-union'), 'Missing or unreviewed pane query method')
    matches = fields.get('paneKnownPropertyMatches')
    require(isinstance(matches, list) and len(matches) <= len(PROPERTIES), 'Unbounded or missing known-property provenance')
    properties = []
    for item in matches:
        require(isinstance(item, dict) and set(item) == {'property', 'knownLiteral'} and
                item['property'] in PROPERTIES and item['knownLiteral'] in TITLES[pane],
                'Unknown property, literal or wrong-pane provenance')
        properties.append(item['property'])
    require(properties == [name for name in PROPERTIES if name in properties], 'Duplicate or reordered property provenance')
    count = fields.get('paneCountCapped16')
    require(type(count) is int and 0 <= count <= 16, 'Invalid actual pane union count')
    require(count == 1 or not matches, 'Ambiguous/missing elements cannot report selected-element provenance')
    known = fields.get('paneIdentityMatchesKnown')
    require(type(known) is bool and known == (count == 1 if pane == 'audit' else bool(matches)),
            'Fresh identity guard and observed known-property provenance disagree')
    # Geometric/foreground/one-action/original app assertions are additionally
    # mandatory in the unchanged V10/XCTest gates. This flag alone is not PASS.
    return pane != 'audit' and known


def verify_v11_receipts(log):
    contracts = v8_receipts.records(log, 'DSC01_OS_PANE_IDENTITY_DECISION_CONTRACT ',
        maximum=1, row_bytes=256, total_bytes=256)
    require(contracts == [CONTRACT] and all(type(contracts[0][key]) is type(value) for key, value in CONTRACT.items()),
            'Executed public pane identity contract missing or invalid')
    rows = v8_receipts.records(log, 'DSC01_OS_APP_INTERACTION ', maximum=128, row_bytes=8192, total_bytes=262144)
    require(rows, 'Actual pane observations missing')
    observed = known = 0
    for row in rows:
        fields = row.get('fields')
        require(isinstance(fields, dict), 'Malformed pane observation envelope')
        selected = [fields] if 'pane' in fields else fields.get('knownPanes', fields.get('visibleMarkerRows', []))
        require(isinstance(selected, list) and len(selected) <= 3, 'Unexpected pane observation collection')
        for pane in selected:
            known += int(inspect_pane(pane)); observed += 1
            if row.get('status') in {'PASS', 'before-activation'}:
                require(pane['paneIdentityMatchesKnown'], 'A successful stage lacks fresh known pane provenance')
        if row.get('status') == 'before-activation':
            samples = [sample for sample in rows if sample['ordinal'] < row['ordinal'] and
                       sample.get('stage') == row.get('stage') and sample.get('status') == 'observe' and
                       sample.get('fields', {}).get('control') == fields.get('control')][-3:]
            require(len(samples) == 3 and all(sample['fields'].get('paneIdentityMatchesKnown') is True for sample in samples),
                    'A successful action reused identity-ineligible stable samples')
    require(observed, 'No actual public/audit pane provenance inspected')
    return dict(status='PASS', observed_panes=observed, known_public_pane_observations=known,
                scope='Additional query provenance only; unchanged V10, XCTest and original probe gates remain mandatory.',
                actual_os_selection_claim=False)
