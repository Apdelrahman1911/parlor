"""Confirm original Host/counterpart HID boundaries; never execute or repair input.

Reuse the qualified archive/Apple-command custody reader. Upload only closed
numeric facts: no raw messages, coordinates, labels, paths or private values.
"""
import json
from pathlib import Path
import re

import review_retained_keyboard as evidence

SPEC = dict(run=41135053853, artifact=13018521901, bytes=170017085,
    digest='3102267620e331fbaf0f0bde39b6a4943cde2550e0b5f2a010d87b7ae39998ad',
    head='40ae511df882cb29070a94ef3c25f95c1b4ba93e', conclusion='failure', event='pull_request',
    workflow='.github/workflows/production-verification.yml', artifact_name='ios-runtime-verification',
    prefix='parlor/parlor/build/ci-evidence/ios-ui-tests.xcresult/',
    start='2026-10-30 22:33:58', end='2026-10-30 22:34:05')
BACKBOARD = 38683
APP = 56435
WINDOW = 'B4D6A32C'
COHORTS = {
    'catalog': ('2026-10-30T22:33:58', '100E26B500011'),
    'host': ('2026-10-30T22:34:04', '100C27DD00012'),
}


def predicate(_spec):
    return ('(processIdentifier == 38683 AND category == "TouchEvents") OR '
            '(processIdentifier == 56435 AND subsystem == "com.apple.UIKit" '
            'AND category == "EventDispatch")')


def reduce_rows(raw, _spec):
    evidence.require(len(raw) <= 2 * 1024**2)
    rows = json.loads(raw)
    evidence.require(isinstance(rows, list) and len(rows) <= 2000)
    result = {}
    for name, (second, sender) in COHORTS.items():
        selected = []
        for row in rows:
            evidence.require(isinstance(row, dict))
            if row.get('processID') != BACKBOARD or row.get('category') != 'TouchEvents':
                continue
            time = evidence.timestamp_us(row['timestamp'])
            if not (evidence.timestamp_us(second + 'Z') <= time <
                    evidence.timestamp_us(second + 'Z') + 1000000):
                continue
            message = row.get('eventMessage')
            evidence.require(isinstance(message, str))
            selected.append((time, message))
        selected.sort()
        evidence.require(selected and len(selected) < 100)
        initialized = [(time, match.group(1)) for time, message in selected
            for match in [re.match(r'init: <BKDirectTouchState: (0x[0-9a-f]+);', message)]
            if match and ('senderID: 0x' + sender) in message]
        stats = [(time, message) for time, message in selected
            if message.startswith('touchstats ' + sender + '-<main>') and '\n    down:' in message]
        evidence.require(len(stats) == 1 and initialized and
                         len({state for _, state in initialized}) == len(initialized))
        stats_time, stats_message = stats[0]
        counts = {}
        for key in ('down', 'up', 'rangeIn', 'rangeOut', 'soft cancel'):
            matches = re.findall(r'\n    ' + re.escape(key) + r': (\d+),', stats_message)
            evidence.require(len(matches) <= 1)
            counts[key.replace(' ', '_')] = int(matches[0]) if matches else 0
        unknown = [time for time, message in selected
                   if message.startswith('*** unknown digitizer ') and
                   ('senderID: 0x' + sender) in message and '-- initializing now' in message]
        skipped = [time for time, message in selected
                   if message == "didn't see a previous touch down or range-in event for pathIndex:2; skipping event"]
        released = [time for time, message in selected if message == 'contact 2 presence: none']
        cancelled = [time for time, message in selected
                     if message.startswith('canceling paths contacts: [pathIndex: 2; touchIdentifier: 17; touching;')
                     and ('contextID: 0x' + WINDOW) in message]
        evidence.require(all(time <= stats_time for time in skipped + released + cancelled))
        result[name] = dict(initialized_states=len(initialized),
            initialization_times_us=[time for time, _ in initialized],
            unknown_initializations=len(unknown), skipped_path_events=len(skipped),
            presence_none=len(released), cancelled_active_paths=len(cancelled),
            cancellation_times_us=cancelled, stats_time_us=stats_time, stats=counts)
    catalog, host = result['catalog'], result['host']
    evidence.require((catalog['initialized_states'], catalog['unknown_initializations'],
        catalog['skipped_path_events'], catalog['presence_none'], catalog['cancelled_active_paths']) == (1, 0, 0, 1, 0))
    evidence.require((host['initialized_states'], host['unknown_initializations'],
        host['skipped_path_events'], host['presence_none'], host['cancelled_active_paths']) == (2, 1, 7, 0, 1))
    evidence.require(catalog['stats'] == dict(down=1, up=1, rangeIn=1, rangeOut=1, soft_cancel=0))
    evidence.require(host['stats'] == dict(down=1, up=1, rangeIn=1, rangeOut=1, soft_cancel=1))
    # The stats' "up" counter does not prove delivery of a normal UITouch end:
    # the failed path is still touching when cleanup explicitly cancels it.
    deliveries = []
    for row in rows:
        if (row.get('processID'), row.get('subsystem'), row.get('category')) != (
                APP, 'com.apple.UIKit', 'EventDispatch'):
            continue
        message = row.get('eventMessage', '')
        if re.fullmatch(r'Sending UIEvent type: 0; subtype: 0; to window: <UIWindow: [0-9A-F]+>; '
                       r'contextId: 0xFFFFFFFF' + WINDOW, message):
            time = evidence.timestamp_us(row['timestamp'])
            if evidence.timestamp_us('2026-10-30T22:34:04Z') <= time < evidence.timestamp_us('2026-10-30T22:34:05Z'):
                deliveries.append(time)
    evidence.require(len(deliveries) == 2 and len(set(deliveries)) == 2)
    return dict(cohorts=result, host_window_delivery_times_us=sorted(deliveries),
        normal_host_touch_end_established=False, compose_callback_established=False,
        simulator_hid_cancellation_confirmed=True, internal_hid_race_cause_established=False)


if __name__ == '__main__':
    raise SystemExit(evidence.main(
        branch='refs/heads/diagnostic/ios-retained-host-boundary-2026-11-03',
        specs=(SPEC,), manifest_path=Path(__file__).with_name('retained_host_manifest.json'),
        reducer=reduce_rows, selector=predicate))
