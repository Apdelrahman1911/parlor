"""Fail-closed numeric evidence review. Missing rows do NOT identify a cause."""
import json
import math

MAX_BYTES = 131072
CAPACITY = 512
WIDTH = 10


def require(condition, message):
    if not condition:
        raise ValueError(message)


def reject_duplicates(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate observation key')
        result[key] = value
    return result


def decode(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded observation bytes required')
    report = json.loads(raw, object_pairs_hook=reject_duplicates,
                        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Non-finite JSON')))
    require(type(report) is dict and set(report) == {'schema', 'nativeMode', 'overflow', 'invalid', 'rows'},
            'Unknown/missing numeric observation field')
    require(type(report['schema']) is int and report['schema'] == 1, 'Unknown observation schema')
    require(type(report['nativeMode']) is int and report['nativeMode'] in (0, 1), 'Unknown native arm')
    require(all(type(report[key]) is int and report[key] == 0 for key in ('overflow', 'invalid')),
            'Incomplete/invalid observation never qualifies')
    rows = report['rows']
    require(type(rows) is list and 0 < len(rows) <= CAPACITY, 'No bounded executed samples')
    last = -1
    for row in rows:
        require(type(row) is list and len(row) == WIDTH, 'Numeric row shape changed')
        require(all(type(value) in (int, float) and math.isfinite(value) for value in row), 'Non-numeric/non-finite row')
        time, kind, control, phase, count, pressed, consumed, x, y, event_ms = row
        require(last <= time <= 1e12 and time >= 0, 'Observer clock moved backwards or out of bounds')
        last = time
        require(all(value == int(value) for value in row[1:7]), 'Non-integral categorical fields')
        require(kind in range(1, 6) and control in range(3) and phase in range(6), 'Unknown public event enum')
        require(0 <= count <= 16 and 0 <= pressed <= count and 0 <= consumed <= count, 'Bad pointer counts')
        require(abs(x) <= 100000 and abs(y) <= 100000 and 0 <= event_ms <= 1e12, 'Unbounded geometry/time')
        if kind == 1:
            require(report['nativeMode'] == 1 and control == consumed == 0 and phase in range(1, 5) and count > 0,
                    'Native event without explicit observer or native phase')
        elif kind == 2:
            require(control in (1, 2) and phase in (0, 1) and count > 0, 'Bad public Compose pointer event')
        else:
            require(count == pressed == consumed == x == y == event_ms == 0, 'Unexpected non-pointer payload')
            if kind == 3:
                require(control in (1, 2) and phase == 0, 'Bad Host/Join callback')
            elif kind == 4:
                require(control == 0 and phase in range(5), 'Non-public shell state')
            else:
                require(control == 0 and phase in range(3), 'Unknown scene phase')
    return report


def public_boundaries(raw, control):
    report = decode(raw)
    require(control in (1, 2), 'Exactly one public action required')
    # Caller must separately validate exact source/run/native custody. These
    # booleans are receipt observations, never a lost-event/root-cause diagnosis.
    rows = report['rows']
    return {
        'native_began_observed': any(row[1] == 1 and row[3] == 1 for row in rows),
        'native_ended_observed': any(row[1] == 1 and row[3] == 3 for row in rows),
        'compose_initial_observed': any(row[1:4] == [2, control, 0] for row in rows),
        'compose_final_observed': any(row[1:4] == [2, control, 1] for row in rows),
        'callback_count': sum(row[1:4] == [3, control, 0] for row in rows),
        'permission_stage_observed': any(row[1:4] == [4, 0, control] for row in rows),
        'name_stage_observed': any(row[1:4] == [4, 0, control + 2] for row in rows),
    }


def compare_neutrality(arms):
    """Compare frozen public *outcomes*, not timing equivalence or touch delivery.

    Each source/run-bound row comes from the same declared original gesture and
    runtime. A passing comparison only addresses those executed public outcomes.
    Separate exact tap/selector/timeout/permission/network-tripwire controls and
    native receipt custody remain mandatory; this function supplies none of them.
    """
    keys = {'game', 'language', 'choice', 'gesture', 'runtime', 'callback_count', 'destination',
            'network_operations', 'exit_count', 'frame', 'typed_name_preserved'}
    require(type(arms) is dict and set(arms) == {'original', 'compose', 'native'}, 'All three neutrality arms required')
    original = arms['original']
    require(type(original) is list and len(original) == 60, 'All original 60 declared journeys required')
    identities = set()
    for row in original:
        require(type(row) is dict and set(row) == keys, 'Unknown/missing public outcome field')
        require(row['game'] in (0, 1, 2) and type(row['game']) is int and
                row['language'] in (0, 1) and type(row['language']) is int and
                row['choice'] in (1, 2) and type(row['choice']) is int and
                row['gesture'] in range(5) and type(row['gesture']) is int, 'Unknown public journey enum')
        identity = tuple(row[key] for key in ('game', 'language', 'choice', 'gesture'))
        require(identity not in identities, 'Duplicated/missing journey')
        identities.add(identity)
        require(type(row['runtime']) is str and 0 < len(row['runtime']) <= 64 and
                row['runtime'].isascii() and all(c.isalnum() or c in '.-_' for c in row['runtime']),
                'Explicit bounded runtime ID required')
        for field in ('callback_count', 'network_operations', 'exit_count', 'destination'):
            require(type(row[field]) is int, 'Non-numeric outcome')
        pressed = row['gesture'] in (0, 3, 4)
        require(row['callback_count'] == int(pressed) and row['network_operations'] == row['exit_count'] == 0 and
                row['destination'] == (row['choice'] + 2 if pressed else 0), 'Original public journey failed')
        require(type(row['typed_name_preserved']) is bool and row['typed_name_preserved'] == (row['gesture'] == 4),
                'Recreated-launch typed-name outcome absent')
        frame = row['frame']
        require(type(frame) is list and len(frame) == 4 and
                all(type(v) in (int, float) and math.isfinite(v) and 0 <= v <= 100000 for v in frame) and
                frame[2] > 0 and frame[3] > 0, 'Invalid public frame')
    for arm in ('compose', 'native'):
        require(type(arms[arm]) is list and len(arms[arm]) == 60, 'Missing instrumented arm journeys')
        require(json.dumps(arms[arm], sort_keys=True) == json.dumps(original, sort_keys=True),
                'Observer changed an executed public outcome/runtime/frame')
    return {'result': 'PUBLIC_OUTCOMES_EQUAL_REQUIRES_SOURCE_AND_RUNTIME_CUSTODY', 'journeys_per_arm': 60,
            'native_delivery_or_timing_neutrality': False, 'original_ios26_2_root_cause_proven': False}
