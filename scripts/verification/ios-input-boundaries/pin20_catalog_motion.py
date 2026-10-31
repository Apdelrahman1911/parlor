"""Missing actual Compose scroll-state boundary beside sequential XCTest samples.

Catalog AX frames are not an atomic app-state observation. A long resolver query
could straddle scrolling, disposal or late restoration. Read only actual public
catalog scroll/index/offset invalidations, not names, values or private state.
One isolated suppressed-Host control proves this new export; original EN/AR
journeys remain unchanged and never run with that suppression flag.
"""
import hashlib
import json
import math

import current_boundary_adapter as current
import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin03_catalog_copy as catalog
import pin06_public_setup_pair as pair
import pin07_failure_control as fault
import pin09_native_boundary as boundary
import pin10_root_boundary as root
import pin12_public_boundaries as public
import run_pin03

SOURCE, TREE = public.SOURCE, public.TREE
PROFILE = 'public-catalog-motion-boundaries'
ARMS = public.ARMS
CONTROL = 'IOSAppLaunchUITests/testPIN20IsolatedMotionExportControl()'
CASES = (*tuple(t.removeprefix('iosAppUITests/')+'()' for t in run_pin03.TESTS), CONTROL)
RESULT = 'PUBLIC_CATALOG_MOTION_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Actual public Compose scroll/index/offset invalidations plus original native/root returns; '
         'original EN/AR journeys and one separately flagged forced control; not source repair, '
         'atomic target identity, observer neutrality, qualification or physical acceptance')
ADDITION = 'composeApp/src/commonMain/kotlin/com/parlor/app/inputcopy/PIN20CatalogMotion.kt'
KOTLIN = 'PIN20CatalogMotion.kt.in'
SWIFT = 'PIN20CatalogMotion.swift.in'
MAX_BYTES, MAX_ROWS = 1_048_576, 4096
TEMPLATES = {'PIN20CatalogMotion.kt.in': '31db821fa6f9a8a17261ecc17f96187422c415921eef54f17d5bb82edee7f2c7', 'PIN20CatalogMotion.swift.in': 'a413db43931aa80cce8ad6c6f3bbacbceb25978f757e5e69cde9b99c5c3f65a7'}
BRIDGE = '\nfun PIN20CatalogMotionSnapshot(): String = com.parlor.app.inputcopy.PIN20CatalogMotion.snapshot()\n'
CONFIGURE = '''        com.parlor.app.inputcopy.PIN20CatalogMotion.syntheticHostSuppression =
            platform.Foundation.NSProcessInfo.processInfo.environment["PARLOR_PIN20_SYNTHETIC"] == "1"
'''
HOST = 'onHost = { com.parlor.app.inputcopy.PIN01Observation.callback(1); step = ShellStep.HostPermission }'
FLAGGED_HOST = ('onHost = { com.parlor.app.inputcopy.PIN01Observation.callback(1); '
                'if (!com.parlor.app.inputcopy.PIN20CatalogMotion.syntheticHostSuppression) step = ShellStep.HostPermission }')


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
                    'Exact public catalog motion source and runtime required')
    base = public.renderer_for_profile(public.PROFILE, SOURCE, runtime, device)
    def render(sources, digests, *, arm):
        for name,digest in TEMPLATES.items():
            current.require(hashlib.sha256(frozen_sources._read(pair.HERE/name, frozen_sources.MAX_SOURCE_BYTES)).hexdigest()==digest,
                            "Exact reviewed motion templates required")
        output = base(sources, digests, arm=arm)
        output[ADDITION] = frozen_sources._read(pair.HERE/KOTLIN, frozen_sources.MAX_SOURCE_BYTES).decode()
        output[catalog.HOME] = pin01_copy.once(output[catalog.HOME],
            '    com.parlor.app.inputcopy.PIN01PublicStage(6)\n',
            '    com.parlor.app.inputcopy.PIN01PublicStage(6)\n' +
            '    com.parlor.app.inputcopy.PIN20ObserveCatalogMotion(catalogScrollState.listState)\n')
        output[pin01_copy.MAIN] = pin01_copy.once(output[pin01_copy.MAIN], '        startKoinOnce()',
                                               CONFIGURE+'        startKoinOnce()') + BRIDGE
        output[pin01_copy.SHELL] = pin01_copy.once(output[pin01_copy.SHELL], HOST, FLAGGED_HOST)
        container = pin01_copy.once(output[pin02_capture.CONTAINER], '    private var pin10Root: PIN10RootObserver?',
            '    private var pin10Root: PIN10RootObserver?\n    private let pin20Motion = PIN20CatalogMotionExport()')
        container = pin01_copy.once(container, 'snapshot: { [weak observation, weak rootObservation] in',
            'snapshot: { [weak observation, weak rootObservation, weak motionExport] in')
        container = pin01_copy.once(container, '        pin02Capture = PIN02Capture.install(snapshot:',
            '        let motionExport = pin20Motion\n        pin02Capture = PIN02Capture.install(snapshot:')
        container = pin01_copy.once(container, '            rootObservation?.exportAfterFailureOnce()\n',
            '            rootObservation?.exportAfterFailureOnce()\n' +
            '            motionExport?.exportAfterFailureOnce(snapshot: { MainViewControllerKt.PIN20CatalogMotionSnapshot() })\n')
        output[pin02_capture.CONTAINER] = container+'\n'+frozen_sources._read(pair.HERE/SWIFT, frozen_sources.MAX_SOURCE_BYTES).decode()
        control = fault.CONTROL_METHOD.replace('testPIN07SyntheticHostFailureCapture', CONTROL.split('/')[1][:-2])
        control = pin01_copy.once(control, '        app.launch()\n',
            '        app.launchEnvironment["PARLOR_PIN20_SYNTHETIC"] = "1"\n        app.launch()\n')
        output[pin02_capture.DRIVER] = pin01_copy.once(output[pin02_capture.DRIVER], fault.DRIVER_ANCHOR,
                                                     control+fault.DRIVER_ANCHOR)
        return output
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:iosAppUITests/'+t.removesuffix('()') for t in CASES)]


def verify_results(summary, tests, simulator_id, code):
    return verify_motion_results(summary, tests, simulator_id, code, cases=CASES)


def verify_motion_results(summary, tests, simulator_id, code, *, cases):
    current.require(cases in (CASES, (CONTROL,)), 'Only original cohort or isolated export control allowed')
    current.require(type(code) is int and code == 65, 'Genuine isolated unexpected control failure must remain failed')
    observed_cases=[]
    def visit(nodes):
        for n in nodes:
            if n.get('nodeType')=='Test Case': observed_cases.append(n)
            visit(n.get('children',[]))
    visit(tests.get('testNodes',[]))
    current.require(len(observed_cases)==len(cases) and {n.get('nodeIdentifier') for n in observed_cases}==set(cases)
        and all(n.get('result') in ('Passed','Failed') for n in observed_cases), 'Exact complete actual outcomes, no skips required')
    methods={n['nodeIdentifier']:n['result'] for n in observed_cases}
    current.require(methods[CONTROL]=='Failed' and summary.get('result')=='Failed', 'Forced control never becomes a passing test')
    for k,v in dict(totalTestCount=len(cases),failedTests=sum(v=='Failed' for v in methods.values()),
                    passedTests=sum(v=='Passed' for v in methods.values()),skippedTests=0,expectedFailures=0).items():
        current.require(type(summary.get(k)) is int and summary[k]==v, 'Exact native counts required')
    config=summary.get('devicesAndConfigurations',[]);devices=tests.get('devices',[])
    current.require(len(config)==len(devices)==1, 'Single owned device required')
    for d in (config[0].get('device',{}),devices[0]):
        current.require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion'))==
            (simulator_id,'arm64','iOS Simulator','iPhone 16 Pro','18.5'),'Exact native source/device boundary required')
    failures=summary.get('testFailures',[])
    current.require(len(failures)==sum(v=='Failed' for v in methods.values()) and
        {f.get('testIdentifierString') for f in failures}=={t for t,v in methods.items() if v=='Failed'},
        'Every actual failed case must retain its original assertion evidence')
    forced=[f for f in failures if f.get('testIdentifierString')==CONTROL]
    current.require(len(forced)==1 and 'One public setup tap must remove the source and expose its destination' in
        forced[0].get('failureText',''),'Control must fail at original Host transition, not startup or settlement')
    return methods


def failed_method_exports(result, destination, outcomes):
    return motion_method_exports(result, destination, outcomes, cases=CASES)


def motion_method_exports(result, destination, outcomes, *, cases):
    current.require(cases in (CASES, (CONTROL,)) and set(outcomes)==set(cases), 'Exact motion case set required')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',
            str(destination/('failed-%02d'%i)),'--test-id',t])
            for i,t in enumerate(sorted(t for t,v in outcomes.items() if v=='Failed'),1)]


retain_companion = public.retain_companion
retain_root_companion = public.retain_root_companion


def decode_motion(raw, *, token, simulator, bundle, legacy):
    current.require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded motion file required')
    v=json.loads(raw,object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite motion field')))
    current.require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs','motion'},
                    'No additional/private motion fields allowed')
    current.require(type(v['schema']) is int and v['schema']==1 and (v['token'],v['simulator'],v['bundle'],v['process'])==
        (token,simulator,bundle,legacy['process']) and type(v['process']) is int and v['process']>0,'Exact motion custody required')
    current.require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0<=v['capturedMs']<=legacy['capturedMs'],'Original motion capture clock required')
    m=v['motion']
    current.require(type(m) is dict and set(m)=={'overflow','invalid','syntheticControl','rows'} and
        all(type(m[k]) is int and 0<=m[k]<=1_000_000 for k in ('overflow','invalid')) and
        type(m['syntheticControl']) is bool and type(m['rows']) is list and 0<len(m['rows'])<=MAX_ROWS,'Exact bounded numeric motion ledger required')
    last=0
    for r in m['rows']:
        current.require(type(r) is list and len(r)==5 and all(type(x) in (int,float) and math.isfinite(x) for x in r) and
            last<=r[0]<=v['capturedMs'] and r[1] in (0,1) and r[2] in (0,1) and
            r[3]==int(r[3]) and 0<=r[3]<=128 and r[4]==int(r[4]) and 0<=r[4]<=100_000,
            'Only ordered public motion bits/index/pixel offset allowed')
        if r[1]==0: current.require(r[2:]==[0,0,0],'Unmount must not retain a private screen or stale scroll state')
        last=r[0]
    current.require(m['overflow']==m['invalid']==0 and any(r[1]==1 for r in m['rows']),
                    'Complete actually mounted public motion ledger required')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),token=token,rows=len(m['rows']),complete=True,
                synthetic_control=m['syntheticControl'],
                scope='actual public catalog scroll/index/offset only; not atomic native target or causality')


def retain_motion_companion(sandbox, destination, token, simulator, bundle, legacy):
    path=sandbox/'tmp'/('parlor-pin20-'+token)/'motion.json'
    current.require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Owned original motion file required')
    raw=run_pin03.read_capture(path, maximum=MAX_BYTES)
    decoded=decode_motion(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('catalog-motion-'+token+'.json')).open('xb') as f:f.write(raw)
    return decoded


def accept_native_export(out, state):
    renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE)
    return accept_motion_export(out, state, profile=PROFILE, renderer=renderer,
                               result_verifier=verify_results, cases=CASES, result=RESULT)


def accept_motion_export(out, state, *, profile, renderer, result_verifier, cases, result):
    current.require((profile, cases, result) in (
        (PROFILE, CASES, RESULT),
        ('isolated-catalog-motion-export-control', (CONTROL,),
         'ISOLATED_CATALOG_MOTION_EXPORT_COMPLETE_NOT_QUALIFICATION'),
        ('native-catalog-frame-return-boundaries', CASES,
         'NATIVE_FRAME_RETURN_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION')),
        'Exact original or isolated-control acceptance contract required')
    boundary.accept_observation_export(out,state,profile=profile,renderer=renderer,result_verifier=result_verifier)
    root.accept_root_companions(out,state)
    row=state['arms'][0];dest=out/'compose';decoded=[]
    commands=[c for c in state['commands'] if c.get('label')=='compose-tests']
    current.require(len(commands)==1 and commands[0].get('timeout_seconds')==900 and
        [a for a in commands[0].get('command',[]) if a.startswith('-only-testing:')]==
        ['-only-testing:iosAppUITests/'+t.removesuffix('()') for t in cases],
        'One exact native command and original 900-second budget required')
    failed=sorted(t for t,v in row['methods'].items() if v=='Failed')
    token_methods={}
    for i,method in enumerate(failed,1):
        requests=[]
        for p in (dest/'attachments'/('failed-%02d'%i)).rglob('*'):
            if p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=4096:
                try: value=json.loads(p.read_bytes())
                except (ValueError,UnicodeError): continue
                if type(value) is dict and 'originalTransitionFailed' in value: requests.append(value)
        current.require(len(requests)==1 and requests[0]['token'] not in token_methods,
                        'Exact failed-method ownership for the isolated flag required')
        token_methods[requests[0]['token']]=method
    for p in sorted(dest.glob('capture-*.json')):
        legacy=json.loads(p.read_bytes());q=dest/('catalog-motion-'+legacy['token']+'.json')
        report=decode_motion(frozen_sources._read(q,MAX_BYTES),token=legacy['token'],
            simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        current.require(report['synthetic_control']==(token_methods.get(legacy['token'])==CONTROL),
                        'Host suppression belongs only to the actual isolated control process')
        if report['synthetic_control']:
            value=json.loads(frozen_sources._read(q,MAX_BYTES))
            current.require(any(r[1:3]==[1,1] for r in value['motion']['rows']) and
                value['motion']['rows'][-1][1:]==[0,0,0,0],
                'Isolated control must actually observe scrolling and public Home disposal')
        decoded.append(report)
    current.require(len(list(dest.glob('catalog-motion-*.json')))==len(decoded) and
        row.get('motion_companion_captures')==decoded,'One complete motion companion per real failure, none on success')
    return result
