"""Failure-only retained/equivalent XCTest queries; not a shipping correction.

Native capture precedes both new reads. The original waiter result, assertion,
single gestures, selectors and deadlines remain unchanged. Query order can
itself refresh AX; a difference narrows the boundary, not proves causality.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin02_capture
import pin28_original_lifecycle_boundaries as original

SOURCE, TREE, ARMS, CASES = original.SOURCE, original.TREE, original.ARMS, original.CASES
PROFILE = 'failure-only-equivalent-query-boundaries'
RESULT = 'FAILURE_ONLY_EQUIVALENT_QUERY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = 'Original EN/AR once; new queries only after a failed waiter/native capture; no repair, retries or qualification'
TEMPLATE = 'PIN29EquivalentQuery.swift.in'
TEMPLATE_SHA256 = '559273425980e231f7bf17a5faf1b9283f979abf727b2abf47c4187784b24ddf'
MAX_BYTES = 16_384
SIGNATURE = '        pin06FailureOwner: XCUIApplication? = nil\n'
NEW_SIGNATURE = '''        pin06FailureOwner: XCUIApplication? = nil,
        pin29QueryApp: XCUIApplication? = nil,
        pin29Prefix: String? = nil
'''
CALL = 'prefix == "افتح غمزة" || prefix == "افتح لعبة الإمبوستر") ? app : nil)'
NEW_CALL = CALL[:-1]+', pin29QueryApp: app, pin29Prefix: prefix)'
OWNER = '''            let pin29Owner = pin29QueryApp.flatMap { candidate in
                (self.pin11PublicApp === candidate || pin06FailureOwner === candidate) ? candidate : nil
            }
'''
PRE_TAP = '''            if let app = pin29Owner {
                pin11CapturePublicFailureOnce(app) // Freeze original native rows before either new read.
                let equivalent = pin29Prefix.map { self.setupButton(app, prefix: $0) } ?? app.textViews.firstMatch
                PIN29EquivalentQuery.capture(control, equivalent: equivalent, app: app, kind: 1)
            }
'''
TEXT = '''            if pin11PublicApp === app {
                pin11CapturePublicFailureOnce(app)
                PIN29EquivalentQuery.capture(field, equivalent: app.textViews.firstMatch,
                                             app: app, kind: 2, expected: expected)
            }
'''
TRANSITION = '''            if let token = app.launchEnvironment["PARLOR_PIN02_TOKEN"], pin11RequestedTokens.contains(token) {
                PIN29EquivalentQuery.capture(source, equivalent: self.setupButton(app, prefix: from),
                                             app: app, kind: 3)
            }
'''


def template():
    raw = frozen_sources._read(original.motion.pair.HERE/TEMPLATE, MAX_BYTES)
    original.text.require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact reviewed failure-only query required')
    return raw.decode()


def extend_driver(driver):
    driver = pin01_copy.once(driver, SIGNATURE, NEW_SIGNATURE)
    driver = pin01_copy.once(driver, CALL, NEW_CALL)
    driver = pin01_copy.once(driver, '            try tapSettledControl(name)\n',
                            '            try tapSettledControl(name, pin29QueryApp: app)\n')
    driver = pin01_copy.once(driver, '            if let pin06FailureOwner {\n', OWNER+'            if let pin06FailureOwner {\n')
    for message, addition in (
        ('Public setup control must stop scrolling before it is tapped', PRE_TAP),
        ('The full typed value must reach the actual Compose field', TEXT),
        ('One public setup tap must remove the source and expose its destination', TRANSITION),
    ):
        end = driver.index('        XCTAssertEqual(result, .completed,\n                       "'+message+'"')
        close = driver.rfind('        }\n', 0, end)
        driver = driver[:close]+addition+driver[close:]
    return driver+'\n'+template()


def restore_driver(driver):
    original.text.require(driver.endswith('\n'+template()), 'Exact failure-only appended template required')
    driver = driver.removesuffix('\n'+template())
    for addition in (OWNER, PRE_TAP, TEXT, TRANSITION):
        driver = pin01_copy.once(driver, addition, '')
    driver = pin01_copy.once(driver, NEW_SIGNATURE, SIGNATURE)
    driver = pin01_copy.once(driver, NEW_CALL, CALL)
    return pin01_copy.once(driver, '            try tapSettledControl(name, pin29QueryApp: app)\n',
                          '            try tapSettledControl(name)\n')


def renderer_for_profile(profile, source, runtime, device):
    original.text.require((profile, source, runtime, device) ==
        (PROFILE, SOURCE, original.motion.pair.RUNTIME, original.motion.pair.DEVICE), 'Exact failure-only query source/profile/runtime/device required')
    base = original.renderer_for_profile(original.PROFILE, source, runtime, device)
    def render(sources, digests, *, arm):
        plan = base(sources, digests, arm=arm)
        plan[pin02_capture.DRIVER] = extend_driver(plan[pin02_capture.DRIVER])
        return plan
    return render


test_command = original.test_command
verify_results = original.verify_results
failed_method_exports = original.failed_method_exports
retain_companion = original.retain_companion
retain_root_companion = original.retain_root_companion
retain_motion_companion = original.retain_motion_companion


def decode_query(raw, *, token, legacy):
    req = original.text.require
    req(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded failure-only query report required')
    v = json.loads(raw, object_pairs_hook=original.text.pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite query field')))
    req(type(v) is dict and set(v) == {'schema', 'token', 'kind', 'originalWaiterFailed', 'order', 'rows'},
        'No query labels, values, hierarchies, identifiers or private fields')
    req(type(v['schema']) is int and v['schema'] == 1 and v['token'] == token and
        type(v['kind']) is int and v['kind'] in (1, 2, 3) and v['originalWaiterFailed'] is True and
        v['order'] == 'retained-then-equivalent', 'Exact original failed-method token and read order required')
    req(type(v['rows']) is list and len(v['rows']) == 2, 'Exactly two post-failure read-only snapshots')
    previous = legacy['capturedMs']
    for row in v['rows']:
        req(type(row) is dict and set(row) == {'startedMs', 'finishedMs', 'snapshotPresent', 'enabled', 'frame',
            'geometryValid', 'stringValue', 'empty', 'exact', 'errorCode'}, 'Only status/geometry/error code may enter query evidence')
        req(all(type(row[k]) in (int, float) and math.isfinite(row[k]) for k in ('startedMs', 'finishedMs')) and
            previous <= row['startedMs'] <= row['finishedMs'] <= 1e12, 'Both new reads must follow original native capture')
        previous = row['finishedMs']
        req(type(row['snapshotPresent']) is bool and type(row['geometryValid']) is bool and type(row['frame']) is list,
            'Exact snapshot/geometry status types required')
        if row['snapshotPresent']:
            req(type(row['enabled']) is bool and row['errorCode'] is None, 'Resolved snapshot status only')
            if row['geometryValid']:
                req(len(row['frame']) == 4 and all(type(n) in (int, float) and math.isfinite(n) and abs(n) <= 100000 for n in row['frame'])
                    and row['frame'][2] >= 0 and row['frame'][3] >= 0, 'Original complete finite geometry required')
            else:
                req(row['frame'] == [], 'Invalid geometry must remain explicitly invalid')
            if v['kind'] == 2:
                req(all(type(row[k]) is bool for k in ('stringValue', 'empty', 'exact')) and
                    (not row['empty'] or row['stringValue']) and
                    (not row['exact'] or row['stringValue'] and not row['empty']), 'Exact synthetic Name status; missing value is not empty')
            else:
                req(all(row[k] is None for k in ('stringValue', 'empty', 'exact')), 'Buttons must not expose any value observation')
        else:
            req(row['enabled'] is None and row['geometryValid'] is False and row['frame'] == [] and
                type(row['errorCode']) is int and -(2**31) <= row['errorCode'] < 2**31 and
                all(row[k] is None for k in ('stringValue', 'empty', 'exact')), 'Unresolved snapshot retains only its bounded original error code')
    return dict(sha256=hashlib.sha256(raw).hexdigest(), kind=v['kind'], rows=2,
                scope='Post-capture retained then equivalent query; may refresh AX, not original-failure causality')


def accept_native_export(out, state):
    renderer = renderer_for_profile(PROFILE, SOURCE, original.motion.pair.RUNTIME, original.motion.pair.DEVICE)
    original.text.require(state['original_inputs'].get(original.text.NAME) == original.text.NAME_SHA256, 'Exact original public Name source required')
    original.motion.boundary.accept_observation_export(out, state, profile=PROFILE, renderer=renderer)
    original.verify_original_companions(out, state)
    row = state['arms'][0]; dest = out/'compose'
    failures = sorted(k for k, value in row['methods'].items() if value == 'Failed')
    tokens = set()
    for index, _ in enumerate(failures, 1):
        found, requests = [], []
        for path in (dest/'attachments'/('failed-%02d' % index)).rglob('*.json'):
            raw = frozen_sources._read(path, original.motion.MAX_BYTES)
            v = json.loads(raw)
            if type(v) is dict and 'originalWaiterFailed' in v:
                found.append((raw, v))
            if type(v) is dict and 'originalTransitionFailed' in v:
                requests.append(v)
        original.text.require(len(found) == 1, 'One fresh-query diagnostic per failed boundary; an earlier failure remains incomplete')
        raw, v = found[0]
        original.text.require(len(requests) == 1 and requests[0]['token'] == v['token'] and v['token'] not in tokens,
            'Query must belong to this original failed method, not another capture')
        tokens.add(v['token'])
        legacy = json.loads(frozen_sources._read(dest/('capture-'+v['token']+'.json'), pin02_capture.MAX_BYTES))
        decode_query(raw, token=legacy['token'], legacy=legacy)
    return RESULT
