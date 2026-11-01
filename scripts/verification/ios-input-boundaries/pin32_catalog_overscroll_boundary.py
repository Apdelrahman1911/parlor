"""Record actual original catalog overscroll getter returns, not another input/query.

An idle LazyListState can coexist with busy overscroll and down consumption.
This copy-only observation delegates the original node/factory/callbacks/results;
it does not cancel bounce, change scrolling or certify native timing neutrality.
"""
import hashlib
import json
import math
from pathlib import Path
import re

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin31_catalog_input_boundary as previous
import run_pin03

SOURCE, TREE, ARMS = previous.SOURCE, previous.TREE, previous.ARMS
PROFILE = 'original-catalog-overscroll-getter-boundaries'
CASES, TESTS, TEST_TIMEOUT_SECONDS = previous.CASES, previous.TESTS, previous.TEST_TIMEOUT_SECONDS
RESULT = 'ORIGINAL_OVERSCROLL_GETTER_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Five fixed EN/AR pairs once with original actions/deadlines; record only actual original '
         'Home overscroll getter returns, delegate original effect/node/factory/callbacks/results; '
         'no added pointer observer, recognizer, polling, input repair, bounce cancellation, '
         'native timing neutrality, causality, shipping qualification or physical acceptance')
HERE = Path(__file__).resolve().parent
TEMPLATES = {'PIN32CatalogOverscroll.kt.in': 'c5fc5549f695f22660f0156c1e1bd8d90737483872c964b3caa0294fdb42d090', 'PIN32CatalogOverscroll.swift.in': 'efa38e8cbce7ced4fd2f079f347fee226c2922561fd365b31f4e009e54f2aab7'}
ADDITION = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN32CatalogOverscroll.kt'
MAX_BYTES, MAX_ROWS = 65_536, 512
BRIDGE = '\nfun PIN32CatalogOverscrollSnapshot(): String = com.parlor.app.inputcopy.PIN32CatalogOverscroll.snapshot()\n'
EXPORT = '            pin32Overscroll.exportAfterFailureOnce { MainViewControllerKt.PIN32CatalogOverscrollSnapshot() }\n'
PROPERTY = '    private let pin32Overscroll = PIN32CatalogOverscrollExport()\n'
PERMISSION_PROPERTY = '    @MainActor private var pin32PermissionControl: XCUIElement?\n'
PERMISSION_REFERENCE = '                if pin11PublicApp === app && ["Continue and retry the real local-network operation.",\n                    "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية."].contains(prefix) {\n                    pin32PermissionControl = button // Original reference, no extra query.\n                }\n'
HOST_NAME_WAIT = '            XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10))\n'
HOST_NAME_CAPTURE = '            let pin32HostNameAppeared = app.textViews.firstMatch.waitForExistence(timeout: 10)\n            if !pin32HostNameAppeared {\n                let screenshot = XCTAttachment(screenshot: app.screenshot())\n                screenshot.name = "Failed public Permission Continue after original single tap"\n                screenshot.lifetime = .keepAlways\n                add(screenshot)\n                pin11CapturePublicFailureOnce(app) // Native rows before either new query.\n                if let source = pin32PermissionControl {\n                    PIN29EquivalentQuery.capture(source, equivalent: self.setupButton(app, prefix: copy.permission),\n                                                 app: app, kind: 3)\n                }\n            }\n            XCTAssertTrue(pin32HostNameAppeared)\n'
PERMISSION_CALLBACK = '{ step = ShellStep.HostName }'
OBSERVED_PERMISSION_CALLBACK = '{ com.parlor.app.inputcopy.PIN32CatalogOverscroll.permissionContinuation(); step = ShellStep.HostName }'


def extend_permission_driver(driver):
    driver = pin01_copy.once(driver, previous.PROPERTY, previous.PROPERTY+PERMISSION_PROPERTY)
    driver = pin01_copy.once(driver, '            pin31CatalogControl = nil\n',
        '            pin31CatalogControl = nil\n            pin32PermissionControl = nil\n')
    driver = pin01_copy.once(driver, previous.REFERENCE, previous.REFERENCE+PERMISSION_REFERENCE)
    # Only the first (Host) Name waiter: preserve the separate original Join waiter.
    require = previous.observed.original.text.require
    require(driver.count(HOST_NAME_WAIT) == 2, 'Exact original Host and Join Name waiters')
    return driver.replace(HOST_NAME_WAIT,HOST_NAME_CAPTURE,1)


def restore_permission_driver(driver):
    driver = pin01_copy.once(driver, HOST_NAME_CAPTURE, HOST_NAME_WAIT)
    driver = pin01_copy.once(driver, previous.REFERENCE+PERMISSION_REFERENCE, previous.REFERENCE)
    driver = pin01_copy.once(driver, '            pin31CatalogControl = nil\n            pin32PermissionControl = nil\n',
        '            pin31CatalogControl = nil\n')
    return pin01_copy.once(driver,previous.PROPERTY+PERMISSION_PROPERTY,previous.PROPERTY)

HOME_OPEN = '    HeroBackdrop(modifier = modifier.fillMaxSize()) {\n'
OBSERVED_OPEN = '    com.parlor.app.inputcopy.PIN32ObserveCatalogOverscroll {\n'+HOME_OPEN
HOME_CLOSE = '        }\n    }\n}\n\n@Composable\nprivate fun catalogGameCount'
OBSERVED_CLOSE = '        }\n    }\n    }\n}\n\n@Composable\nprivate fun catalogGameCount'


def template(name):
    raw = frozen_sources._read(HERE/name, frozen_sources.MAX_SOURCE_BYTES)
    previous.observed.original.text.require(hashlib.sha256(raw).hexdigest() == TEMPLATES[name],
        'Exact reviewed original-getter template required')
    return raw.decode()


def renderer_for_profile(profile, source, runtime, device):
    pair = previous.observed.original.motion.pair
    previous.observed.original.text.require((profile,source,runtime,device) ==
        (PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE), 'Exact original overscroll source/profile/runtime/device')
    base = previous.renderer_for_profile(previous.PROFILE,source,runtime,device)
    def render(sources, digests, *, arm):
        plan = base(sources,digests,arm=arm)
        plan[ADDITION] = template('PIN32CatalogOverscroll.kt.in')
        plan[pin02_capture.DRIVER] = extend_permission_driver(plan[pin02_capture.DRIVER])
        plan[pin01_copy.SHELL] = pin01_copy.once(plan[pin01_copy.SHELL], PERMISSION_CALLBACK, OBSERVED_PERMISSION_CALLBACK)
        plan[pin01_copy.MAIN] += BRIDGE
        home = pin01_copy.once(plan[previous.catalog.HOME], HOME_OPEN, OBSERVED_OPEN)
        plan[previous.catalog.HOME] = pin01_copy.once(home, HOME_CLOSE, OBSERVED_CLOSE)
        native = pin01_copy.once(plan[pin02_capture.CONTAINER], previous.observed.original.PROPERTY,
            previous.observed.original.PROPERTY+PROPERTY)
        native = pin01_copy.once(native, previous.observed.original.EXPORT, EXPORT+previous.observed.original.EXPORT)
        plan[pin02_capture.CONTAINER] = native+'\n'+template('PIN32CatalogOverscroll.swift.in')
        return plan
    return render


def decode_overscroll(raw, *, token, simulator, bundle, legacy):
    req = previous.observed.original.text.require
    req(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded original overscroll capture required')
    v = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite overscroll JSON')))
    req(type(v) is dict and set(v) == {'schema','token','simulator','bundle','process','capturedMs','overscroll'},
        'No labels, values, keys, pointer addresses, identities or private fields')
    req(type(v['schema']) is int and v['schema'] == 1 and type(v['process']) is int and v['process'] > 0 and
        (v['token'],v['simulator'],v['bundle'],v['process']) == (token,simulator,bundle,legacy['process']),
        'Exact original overscroll process/token/device/product')
    req(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0 <= v['capturedMs'] <= legacy['capturedMs'], 'Original same-process failure capture clock')
    s = v['overscroll']
    req(type(s) is dict and set(s) == {'created','factoryMissing','permissionCallbacks','overflow','invalid','rows'} and
        type(s['created']) is int and 0 <= s['created'] <= MAX_ROWS and type(s['factoryMissing']) is bool and
        type(s['permissionCallbacks']) is int and 0 <= s['permissionCallbacks'] <= MAX_ROWS and
        all(type(s[k]) is int and s[k] == 0 for k in ('overflow','invalid')), 'Complete primitive getter ledger')
    req(type(s['rows']) is list and len(s['rows']) <= MAX_ROWS, 'Bounded original getter rows')
    finished = 0
    for row in s['rows']:
        req(type(row) is list and len(row) == 3 and
            all(type(x) in (int,float) and math.isfinite(x) for x in row) and
            finished <= row[0] <= row[1] <= v['capturedMs'] and row[2] in (0,1),
            'Ordered original getter start/finish/returned busy bit only')
        finished = row[1]
    req(s['created'] > 0 or s['factoryMissing'], 'Actual factory creation or absent-factory observation required')
    req(not s['rows'] or s['created'] > 0, 'No invented getter when no original effect exists')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),token=token,rows=len(s['rows']),created=s['created'],
        factory_missing=s['factoryMissing'],permission_callbacks=s['permissionCallbacks'],busy_reads=sum(int(r[2]) for r in s['rows']),complete=True,
        scope='Actual original getter returns only; not original UIKit delivery, atomic target or causality')


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    original = previous.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    path = sandbox/'tmp'/('parlor-pin32-'+token)/'overscroll.json'
    previous.observed.original.text.require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(),
        'Owned original overscroll file required')
    raw = run_pin03.read_capture(path,maximum=MAX_BYTES)
    decode_overscroll(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('catalog-overscroll-'+token+'.json')).open('xb') as f: f.write(raw)
    return original  # Existing native companion receipt stays unchanged.


test_command, verify_results, failed_method_exports = previous.test_command, previous.verify_results, previous.failed_method_exports
retain_root_companion, retain_motion_companion = previous.retain_root_companion, previous.retain_motion_companion
verify_commands = previous.verify_commands


def accept_native_export(out, state):
    req = previous.observed.original.text.require
    pair = previous.observed.original.motion.pair
    req(state['original_inputs'].get(previous.observed.original.text.NAME) == previous.observed.original.text.NAME_SHA256,
        'Exact original public Name input, no substitution')
    renderer = renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE)
    previous.observed.original.motion.boundary.accept_observation_export(out,state,profile=PROFILE,
        renderer=renderer,result_verifier=verify_results,command_verifier=verify_commands)
    previous.observed.original.verify_companion_files(out,state)
    previous.observed.verify_failed_queries(out,state)
    dest = out/'compose'
    decoded = []
    for path in sorted(dest.glob('capture-*.json')):
        legacy = json.loads(frozen_sources._read(path,pin02_capture.MAX_BYTES))
        req(not any(row[1] in (1,2) for row in legacy['snapshot']['rows']),
            'No invented Compose or native gesture rows')
        raw = frozen_sources._read(dest/('catalog-overscroll-'+legacy['token']+'.json'),MAX_BYTES)
        decoded.append(decode_overscroll(raw,token=legacy['token'],simulator=state['arms'][0]['simulator'],
            bundle=state['debug_bundle'],legacy=legacy))
    req(len(list(dest.glob('catalog-overscroll-*.json'))) == len(decoded),
        'Exactly one original overscroll companion per failure; no success export')
    log = frozen_sources._read(dest/'test.log',4*1024*1024)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",log)
    req(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES,
        'Ten fixed cases execute exactly once in order')
    receipt = dict(methods=state['arms'][0]['methods'],observations=decoded,original_getter_only=True,
        compose_pointer_observers=False,native_touch_observer=False,application_root_cause=False,
        shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target = out/'catalog-overscroll-observations.json'
    if target.exists(): req(json.loads(target.read_bytes()) == receipt,'Exact original saved getter receipt')
    else:
        with target.open('x') as f: json.dump(receipt,f,indent=2)
    return RESULT
