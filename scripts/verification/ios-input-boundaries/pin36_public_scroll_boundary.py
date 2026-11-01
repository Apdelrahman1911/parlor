"""Failure-only actual public ScrollState/getter observation, not an input repair.

Only the existing isolated touch sentinel executes first. Its original failure,
all prior companions and the new actual Setup scope/export remain mandatory.
Permission/Name linkage is not evidence of their executed state transitions.
"""
import hashlib
import json
import math
from pathlib import Path

import frozen_sources
import pin01_copy
import pin02_capture
import pin34_touch_export_control as previous
import run_pin03

SOURCE, TREE, ARMS = previous.SOURCE, previous.TREE, previous.ARMS
PROFILE = 'isolated-public-scroll-export-control'
CASES, TEST_TIMEOUT_SECONDS = previous.CASES, previous.TEST_TIMEOUT_SECONDS
RESULT = 'ISOLATED_PUBLIC_SCROLL_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only existing isolated failed touch sentinel with new public Setup/Permission/Name '
         'ScrollState invalidations and original overscroll getter returns; no original-cohort '
         'replay, new input/query/polling/scroll/timeout, causality or qualification')
pair, require = previous.pair, previous.require
HERE = Path(__file__).resolve().parent
PERMISSION = 'composeApp/src/commonMain/kotlin/com/parlor/app/permissions/P2pPermissionRationaleScreen.kt'
PERMISSION_SHA256 = 'a6f6a21eccb2a7c7798ea84c0aba8d84c7bb825f84e77001992a03fdc4633552'
ADDITION = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN36PublicScroll.kt'
TEMPLATES = {
    'PIN36PublicScroll.kt.in': 'c186541ee658acd6f24c424b4a334723759734dfd840905d9078a04e62f7b0a9',
    'PIN36PublicScroll.swift.in': 'df40b9d44aa1004f68b15efe13a15926d93304e206d2d060db45e323a66caf38',
    'fixtures/PIN36PermissionScreen.kt.source': PERMISSION_SHA256,
}
MAX_BYTES, MAX_ROWS = 1_048_576, 8192
PROPERTY = '    private let pin36PublicScroll = PIN36PublicScrollExport()\n'
EXPORT = '            self?.pin36PublicScroll.exportAfterFailureOnce { MainViewControllerKt.PIN36PublicScrollSnapshot() }\n'
BRIDGE = '''
fun PIN36PublicScrollSnapshot(): String {
    check(platform.Foundation.NSThread.isMainThread)
    return com.parlor.app.inputcopy.PublicScrollBoundary.snapshot()
}
'''
OPEN = '    HeroBackdrop(modifier = modifier.fillMaxSize()) {\n'
PICKER_CLOSE = '    }\n}\n\n@Composable\nprivate fun LocalPlayOptions'


def template(name):
    raw = frozen_sources._read(HERE/name, MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest() == TEMPLATES[name], 'Exact public-scroll reviewed template')
    return raw.decode()


def extend(base):
    plan = dict(base)
    require(ADDITION not in plan and PERMISSION not in plan, 'New public boundary applied once only')
    plan[ADDITION] = template('PIN36PublicScroll.kt.in')
    for path, kind, source in (
        (pin01_copy.PICKER, 0, plan[pin01_copy.PICKER]),
        (PERMISSION, 1, template('fixtures/PIN36PermissionScreen.kt.source')),
        (previous.original.text.NAME, 2, plan[previous.original.text.NAME]),
    ):
        source = pin01_copy.once(source, 'import androidx.compose.foundation.rememberScrollState\n',
            'import com.parlor.app.inputcopy.rememberObservedPublicScrollState\n')
        source = pin01_copy.once(source, '.verticalScroll(rememberScrollState())',
            f'.verticalScroll(rememberObservedPublicScrollState({kind}))')
        source = pin01_copy.once(source, OPEN,
            f'    com.parlor.app.inputcopy.ObservePublicOverscroll({kind}) {{\n'+OPEN)
        if path == pin01_copy.PICKER:
            source = pin01_copy.once(source, PICKER_CLOSE, '    }\n    }\n}\n\n@Composable\nprivate fun LocalPlayOptions')
        else:
            require(source.endswith('    }\n}\n'), 'Exact end of public screen only')
            source = source[:-len('    }\n}\n')]+'    }\n    }\n}\n'
        plan[path] = source
    plan[pin01_copy.MAIN] += BRIDGE
    native = pin01_copy.once(plan[pin02_capture.CONTAINER], previous.delivery.PROPERTY,
        previous.delivery.PROPERTY+PROPERTY)
    native = pin01_copy.once(native, previous.delivery.EXPORT, previous.delivery.EXPORT+EXPORT)
    plan[pin02_capture.CONTAINER] = native+'\n'+template('PIN36PublicScroll.swift.in')
    return plan


def restore(plan):
    restored = dict(plan)
    require(restored.pop(ADDITION) == template('PIN36PublicScroll.kt.in'), 'Exact public-scroll addition')
    for path, kind in ((pin01_copy.PICKER, 0), (PERMISSION, 1), (previous.original.text.NAME, 2)):
        source = restored[path]
        source = pin01_copy.once(source, f'    com.parlor.app.inputcopy.ObservePublicOverscroll({kind}) {{\n'+OPEN, OPEN)
        source = pin01_copy.once(source,
            f'.verticalScroll(rememberObservedPublicScrollState({kind}))',
            '.verticalScroll(rememberScrollState())')
        source = pin01_copy.once(source, 'import com.parlor.app.inputcopy.rememberObservedPublicScrollState\n',
            'import androidx.compose.foundation.rememberScrollState\n')
        if path == pin01_copy.PICKER:
            source = pin01_copy.once(source, '    }\n    }\n}\n\n@Composable\nprivate fun LocalPlayOptions', PICKER_CLOSE)
        else:
            require(source.endswith('    }\n    }\n}\n'), 'Exact public wrapper close')
            source = source[:-len('    }\n    }\n}\n')]+'    }\n}\n'
        restored[path] = source
    require(restored.pop(PERMISSION) == template('fixtures/PIN36PermissionScreen.kt.source'), 'Entire original permission bytes')
    require(restored[pin01_copy.MAIN].endswith(BRIDGE), 'Exact public-scroll bridge')
    restored[pin01_copy.MAIN] = restored[pin01_copy.MAIN][:-len(BRIDGE)]
    native = restored[pin02_capture.CONTAINER]
    suffix = '\n'+template('PIN36PublicScroll.swift.in')
    require(native.endswith(suffix), 'Exact native public-scroll template')
    native = native[:-len(suffix)]
    native = pin01_copy.once(native, previous.delivery.EXPORT+EXPORT, previous.delivery.EXPORT)
    restored[pin02_capture.CONTAINER] = pin01_copy.once(native,
        previous.delivery.PROPERTY+PROPERTY, previous.delivery.PROPERTY)
    return restored


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
        'Exact isolated public-scroll source/profile/runtime/device')
    base = previous.renderer_for_profile(previous.PROFILE, source, runtime, device)
    return lambda sources, digests, *, arm: extend(base(sources, digests, arm=arm))


def decode(raw, *, token, simulator, bundle, legacy):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded public-scroll companion')
    value = json.loads(raw, object_pairs_hook=previous.delivery.decoder.unique,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite public-scroll JSON')))
    require(type(value) is dict and set(value) == {'schema','token','simulator','bundle','process','capturedMs','scroll'},
        'No text/input contents, labels, identities, objects or private fields')
    require(type(value['schema']) is int and value['schema'] == 1 and type(value['process']) is int and
        value['process'] > 0 and (value['token'],value['simulator'],value['bundle'],value['process']) ==
        (token,simulator,bundle,legacy['process']), 'Exact same-process public failure owner')
    at = value['capturedMs']
    require(type(at) in (int,float) and math.isfinite(at) and 0 <= at <= legacy['capturedMs'], 'Original failure clock')
    scroll = value['scroll']
    require(type(scroll) is dict and set(scroll) == {'created','factoryMissing','overflow','invalid','rows'},
        'Closed primitive public-scroll ledger')
    require(type(scroll['created']) is list and len(scroll['created']) == 3 and
        all(type(n) is int and 0 <= n <= MAX_ROWS for n in scroll['created']) and
        type(scroll['factoryMissing']) is list and len(scroll['factoryMissing']) == 3 and
        all(type(n) is bool for n in scroll['factoryMissing']) and
        all(type(scroll[k]) is int and scroll[k] == 0 for k in ('overflow','invalid')), 'Complete bounded public metadata')
    rows = scroll['rows']; previous_finish = 0
    require(type(rows) is list and len(rows) <= MAX_ROWS, 'Bounded public invalidation/getter rows')
    for row in rows:
        require(type(row) is list and len(row) == 6 and all(type(n) in (int,float) and math.isfinite(n) for n in row),
            'Six finite primitive public-scroll fields')
        start, finish, kind, event, flag, offset = row
        require(0 <= start <= finish <= at and previous_finish <= finish and kind in range(3) and
            event in range(3) and flag in (0,1) and offset == int(offset), 'Public enum/flag/time order')
        require(0 <= offset <= 100000 and (event == 0 or offset == 0), 'Public offset only for actual ScrollState')
        require(event != 1 or scroll['created'][int(kind)] > 0, 'Original getter requires a real public effect')
        previous_finish = finish
    return dict(sha256=hashlib.sha256(raw).hexdigest(), rows=len(rows), created=scroll['created'],
        factory_missing=scroll['factoryMissing'], complete=True, application_root_cause=False, shipping_qualified=False)


test_command, verify_results, failed_method_exports = previous.test_command, previous.verify_results, previous.failed_method_exports
verify_commands = previous.verify_commands
retain_root_companion, retain_motion_companion = previous.retain_root_companion, previous.retain_motion_companion


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    report = previous.retain_companion(sandbox, destination, token, simulator, bundle, legacy)
    path = sandbox/'tmp'/('parlor-pin36-'+token)/'scroll.json'
    require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Owned public-scroll regular companion')
    raw = run_pin03.read_capture(path, maximum=MAX_BYTES)
    decode(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination/('public-scroll-'+token+'.json')).open('xb') as f: f.write(raw)
    return report


def accept_native_export(out, state):
    require(state['original_inputs'].get(PERMISSION) == PERMISSION_SHA256, 'Entire original permission source')
    previous.accept_touch_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE), result=RESULT)
    dest = out/'compose'; paths = list(dest.glob('capture-*.json'))
    require(len(paths) == len(list(dest.glob('public-scroll-*.json'))) == 1, 'One existing sentinel failure companion only')
    legacy = json.loads(paths[0].read_bytes()); token = legacy['token']
    raw = frozen_sources._read(dest/('public-scroll-'+token+'.json'), MAX_BYTES)
    report = decode(raw, token=token, simulator=state['arms'][0]['simulator'], bundle=state['debug_bundle'], legacy=legacy)
    rows = json.loads(raw)['scroll']['rows']
    require(report['created'][0] > 0 or report['factory_missing'][0], 'Actual Setup effect or absent factory')
    require(any(r[2:] == [0,2,1,0] for r in rows) and any(r[2:] == [0,0,0,0] for r in rows),
        'Actual Setup mounting and initial idle state across parent stage admission')
    require(report['created'][1:] == [0,0] and not any(r[2] != 0 for r in rows),
        'Suppressed Host does not execute Permission or Name; linkage is not their runtime proof')
    receipt = dict(methods=state['arms'][0]['methods'],scroll=report,
        control_cause='Existing explicit Host suppression after callback',permission_name_runtime=False,
        application_root_cause=False,shipping_qualified=False,physical_verified=False)
    target = out/'public-scroll-export.json'
    if target.exists(): require(json.loads(target.read_bytes()) == receipt, 'Exact saved public-scroll receipt')
    else:
        with target.open('x') as f: json.dump(receipt,f,indent=2)
    return RESULT
