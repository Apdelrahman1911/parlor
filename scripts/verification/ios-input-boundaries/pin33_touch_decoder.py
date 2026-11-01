"""Strict reader for original native call metadata, not causality."""
import hashlib
import json
import math

MAX_BYTES = 1048576
MAX_ROWS = 8192


def require(ok, message):
    if not ok:
        raise ValueError(message)


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate native-touch key')
        result[key] = value
    return result


def decode(raw, *, token, simulator, bundle, legacy):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded original native-touch bytes')
    v = json.loads(raw, object_pairs_hook=unique,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite native-touch field')))
    require(type(v) is dict and set(v) == {'schema', 'token', 'simulator', 'bundle', 'process',
        'capturedMs', 'installed', 'installation', 'overflow', 'rows'},
        'No input, touch/event contents, coordinates, text, labels or private fields')
    require(type(v['schema']) is int and v['schema'] == 1 and type(v['process']) is int and v['process'] > 0 and
        (v['token'], v['simulator'], v['bundle'], v['process']) == (token, simulator, bundle, legacy['process']),
        'Exact original native-touch process/token/device/product')
    require(type(v['capturedMs']) in (int, float) and math.isfinite(v['capturedMs']) and
        0 <= v['capturedMs'] <= legacy['capturedMs'], 'Same-process original failure capture clock')
    stages = v['installation']
    require(type(stages) is list and len(stages) == 3 and all(type(n) is int for n in stages) and
        0 <= stages[0] <= 32 and 0 <= stages[1] <= stages[0] and stages[2] in (0, 1) and
        type(v['installed']) is bool and type(v['overflow']) is int and 0 <= v['overflow'] <= 1000000,
        'Actual unique-existing-leaf ABI/installation status only')
    require(type(v['rows']) is list and len(v['rows']) <= MAX_ROWS, 'Bounded original native-touch rows')
    previous = 0
    stack = []
    for row in v['rows']:
        require(type(row) is list and len(row) == 5 and
            all(type(n) in (int, float) and math.isfinite(n) for n in row), 'Five finite primitive fields only')
        at, kind, phase, flag, primary = row
        require(previous <= at <= v['capturedMs'] and kind in range(1, 9) and phase in (0, 1) and primary in (0, 1),
            'Ordered original time/kind/phase')
        require(flag in range(6) if kind == 5 else flag in (0, 1) if kind >= 6 and phase == 1 else flag == 0,
            'Only original native state argument or arbitration return bit')
        if phase == 0:
            stack.append((kind, flag, primary))
        else:
            require(stack and stack[-1][0] == kind and stack[-1][2] == primary and (kind != 5 or stack[-1][1] == flag),
                'Every original completion matches its admitted invocation, including nested native setters')
            stack.pop()
        previous = at
    require(not stack or v['overflow'] > 0, 'No uncompleted original calls in a non-overflowed ledger')
    complete = v['installed'] and stages[0] > 0 and stages[1:] == [1, 1] and v['overflow'] == 0
    return dict(sha256=hashlib.sha256(raw).hexdigest(), complete=complete, rows=len(v['rows']),
        touch_entries=sum(r[1] < 5 and r[2] == 0 for r in v['rows']),
        cancellations=sum(r[1] == 4 and r[2] == 0 for r in v['rows']),
        primary_touch_entries=sum(r[1] < 5 and r[2] == 0 and r[4] == 1 for r in v['rows']),
        native_timing_neutrality=False, application_root_cause=False, shipping_qualified=False)
