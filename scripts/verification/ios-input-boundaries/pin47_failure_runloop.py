"""Passive copied iOS run-loop boundary, original AR/EN methods and new export control."""
import hashlib,json,math,re
from functools import partial
from pathlib import Path
import frozen_sources,pin01_copy,pin02_capture,pin01_receipts
import pin44_original_runtime as runtime
import pin46_main_runloop as runloop

SOURCE,TREE,RUNTIME,DEVICE=runtime.SOURCE,runtime.TREE,runtime.RUNTIME,runtime.DEVICE
PROFILE='failure-owned-main-runloop'
ARMS=('compose',)
SENTINEL_CASE='IOSAppLaunchUITests/testZPIN47FailureRunLoopExport()'
CASES=runtime.CASES+(SENTINEL_CASE,)
EXPORT_PROFILE='failure-owned-main-runloop-export-control'
SAMPLE_PROFILE='failure-owned-main-runloop-fixed-samples'
QUERY_PROFILE='failure-owned-query-property-boundary'
PROFILES=(PROFILE,EXPORT_PROFILE,SAMPLE_PROFILE,QUERY_PROFILE)
EXPORT_CASES=(SENTINEL_CASE,)
SAMPLE_CASES=tuple('IOSAppLaunchUITests/testPIN47'+language+'Sample%02d()'%i
    for language in ('Arabic','English') for i in range(1,6))
QUERY_CASES=runtime.CASES
TESTS=tuple('iosAppUITests/'+c.removesuffix('()') for c in CASES)
TEST_TIMEOUT_SECONDS=900
RESULT='FAILURE_OWNED_MAIN_RUNLOOP_COMPLETE_NOT_QUALIFICATION'
SCOPE=('Original two AR/EN identifiers once on iOS26.2/17Pro, then one new isolated absent-destination export control; '
       'passive bounded run-loop phases only, no view/AX/input hooks, original application/actions/assertions/10s deadlines; '
       'all failures retained; no KMP/successful cohort replay, causality, observer neutrality or shipping qualification')
HERE=Path(__file__).resolve().parent
OWNER='PIN47FailureRunLoop.swift.in'
OWNER_SHA256='b1de08dd3e770418e5f040c08eb48c33dd35d9e19971212b4f679d2a2493f824'
CAPABILITY_RUN=41339575374
CAPABILITY_HARNESS='56d60caf10660ef9caf6dd312990a0ed4870153f'
CAPABILITY_STATE_SHA256='c4cc36ac727ef021c45cab13d56530f0b658a9916e1dd24355a6291d8267c952'
require=runtime.require
load=runtime.load
MAX_BYTES=16384

def require_capability_binding():
    runloop.require_context_binding()
    require(CAPABILITY_RUN==41339575374 and CAPABILITY_HARNESS=='56d60caf10660ef9caf6dd312990a0ed4870153f'
        and CAPABILITY_STATE_SHA256=='c4cc36ac727ef021c45cab13d56530f0b658a9916e1dd24355a6291d8267c952','Actual accepted corrected native software capability required')

def owner_source():
    raw=frozen_sources._read(HERE/OWNER,65536)
    require(hashlib.sha256(raw).hexdigest()==OWNER_SHA256,'Exact reviewed failure-only runloop owner')
    return raw.decode()

def renderer_for_profile(profile,source,os,device):
    require(profile in PROFILES and (source,os,device)==(SOURCE,RUNTIME,DEVICE),'Exact original failed source/runtime')
    baseline=runtime.renderer_for_profile(runtime.PROFILE,source,os,device)
    def render(sources,digests,*,arm):
        original=baseline(sources,digests,arm=arm) # Existing exact source binding and failure interceptor.
        container=pin01_copy.once(sources[pin02_capture.CONTAINER],
            '    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n#if DEBUG && targetEnvironment(simulator)\n'
            '    private var pin47RunLoop: PIN47FailureRunLoop?\n#endif\n')
        container=pin01_copy.once(container,'        contentController.didMove(toParent: self)\n',
            '        contentController.didMove(toParent: self)\n#if DEBUG && targetEnvironment(simulator)\n'
            '        pin47RunLoop = PIN47FailureRunLoop()\n#endif\n')
        container+='\n'+'\n'.join((
            (HERE/'PIN02Capture.swift.in').read_text(),runloop.source(runloop.TEMPLATE).decode(),owner_source()))
        driver=original[pin02_capture.DRIVER].replace('PIN44Configure','PIN47Configure').replace('PARLOR_PIN44_','PARLOR_PIN47_')
        driver=pin01_copy.once(driver,runtime.PROFILE,PROFILE)
        # Absolute clock origin is printed only after the original failed waiter.
        # No additional AX query/tap, predicate evaluation, wait or export on success.
        driver=pin01_copy.once(driver,'            print("Public control settlement: " + samples.joined(separator: "; "))',
            '            print("PIN47 failed-query clock origin ms: \\(started * 1000)")\n'
            '            print("Public control settlement: " + samples.joined(separator: "; "))')
        sentinel=runtime.pair_control.leaf.native.SENTINEL.replace('testPIN39NativeOnlyExportCapability',
            SENTINEL_CASE.split('/')[1].removesuffix('()')).replace('PIN39Configure','PIN47Configure')
        if profile!=QUERY_PROFILE:
            driver=pin01_copy.once(driver,'    private struct KeyboardCopy {',sentinel+'\n    private struct KeyboardCopy {')
        else:
            import pin48_property_timing
            driver=pin48_property_timing.expand(driver)
        if profile==SAMPLE_PROFILE:
            driver=pin01_copy.once(driver,'    private struct KeyboardCopy {',sample_wrappers()+'    private struct KeyboardCopy {')
            # Clock metadata only AFTER XCTest has decided a failure, with no
            # additional app/AX query or successful-path measurement.
            driver=pin01_copy.once(driver,'        // record is called only after XCTest decides to report a failure.',
                '        print("PIN47 failed-record clock ms: \\(ProcessInfo.processInfo.systemUptime * 1000)")\n'
                '        // record is called only after XCTest decides to report a failure.')
            driver=pin01_copy.once(driver,'            print("Public text commit: " + samples.joined(separator: "; "))',
                '            print("PIN47 failed-text-query clock origin ms: \\(started * 1000)")\n'
                '            print("Public text commit: " + samples.joined(separator: "; "))')
        return sources|{pin02_capture.CONTAINER:container,pin02_capture.DRIVER:driver}
    return render

def sample_wrappers():
    return ''.join('    @MainActor\n    func '+c.split('/')[1].removesuffix('()')+'() throws {\n'
        '        try testAdditionalGamesExposeOnlyMultiplayerSetupIn'+language+'()\n    }\n\n'
        for language in ('Arabic','English') for c in SAMPLE_CASES if language in c)

def selected_cases(cases):
    require(cases in (CASES,EXPORT_CASES,SAMPLE_CASES,QUERY_CASES),'Only original cohort, isolated correction, passive samples or property-boundary pair')
    return cases

def test_command(base,result,*,cases=CASES):
    return [*(a for a in base if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
        *('-only-testing:iosAppUITests/'+c.removesuffix('()') for c in selected_cases(cases))]

def visit(nodes):
    return [c for n in nodes for c in ([n] if n.get('nodeType')=='Test Case' else visit(n.get('children',[])))]

def verify_results(summary,tests,simulator,code,*,cases=CASES):
    count=len(selected_cases(cases))
    control=SENTINEL_CASE in cases
    require(type(code) is int and code in ((65,) if control else (0,65)),'Actual XCTest exit; control must retain exit65')
    for k in ('totalTestCount','passedTests','failedTests','skippedTests','expectedFailures'):
        require(type(summary.get(k)) is int and 0<=summary[k]<=count,'Exact finite actual selected-method counts')
    require(summary['totalTestCount']==count and summary['skippedTests']==summary['expectedFailures']==0
        and summary['passedTests']+summary['failedTests']==count
        and summary['result']==('Passed' if code==0 else 'Failed'),'All selected methods execute without skips')
    config=summary.get('devicesAndConfigurations',[]);devices=tests.get('devices',[])
    require(len(config)==len(devices)==len(tests.get('testPlanConfigurations',[]))==1,'One actual owned configuration')
    for d in (config[0].get('device',{}),devices[0]):
        require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion'))==
            (simulator,'arm64','iOS Simulator','iPhone 17 Pro','26.2'),'Actual original failed runtime/model')
    nodes=visit(tests.get('testNodes',[]))
    require(len(nodes)==count and {n.get('nodeIdentifier') for n in nodes}==set(cases)
        and all(n.get('result') in ('Passed','Failed') and type(n.get('durationInSeconds')) in (int,float)
            and math.isfinite(n['durationInSeconds']) and 0<n['durationInSeconds']<900 for n in nodes),'Exact original identifiers/control outcomes/durations')
    methods={n['nodeIdentifier']:n['result'] for n in nodes}
    passed=sum(v=='Passed' for v in methods.values())
    require(passed==summary['passedTests'] and (code==0)==(passed==count),'Original natural outcomes/exit preserved')
    if control:require(methods[SENTINEL_CASE]=='Failed','Control remains failed')
    failures=summary.get('testFailures',[])
    if control:
        control_failures=[f for f in failures if f.get('testIdentifierString')==SENTINEL_CASE]
        require(len(control_failures)==1 and 'One public setup tap must remove the source and expose its destination' in control_failures[0].get('failureText',''),
            'New control fails at unchanged absent-destination assertion, not startup/input')
    require(all(f.get('testIdentifierString') in methods and methods[f['testIdentifierString']]=='Failed' for f in failures)
        and {f['testIdentifierString'] for f in failures}=={k for k,v in methods.items() if v=='Failed'},'No hidden extra or lost original failures')
    return methods

def failed_method_exports(result,destination,outcomes,*,cases=CASES):
    require(set(outcomes)==set(selected_cases(cases)) and all(v in ('Passed','Failed') for v in outcomes.values()),'All declared actual outcomes before failed-only export')
    if SENTINEL_CASE in cases:require(outcomes[SENTINEL_CASE]=='Failed','Control remains failed')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',str(destination/('failed-%02d'%i)),'--test-id',c])
        for i,c in enumerate(sorted(k for k,v in outcomes.items() if v=='Failed'),1)]

def decode_companions(destination,token,simulator,bundle,legacy):
    raw=frozen_sources._read(destination/('native-runloop-'+token+'.json'),MAX_BYTES)
    v=json.loads(raw,object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite runloop companion')))
    require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','observation'}
        and type(v['schema']) is int and v['schema']==1 and type(v['process']) is int and v['process']>0
        and (v['token'],v['simulator'],v['bundle'],v['process'])==(token,simulator,bundle,legacy['process']),'Exact private-free same-process failed-only companion')
    observation=runloop.decode(json.dumps(v['observation']).encode())
    require(legacy['installedMs']<=observation['capturedMs']<=legacy['capturedMs'] and sum(observation['phaseCounts'])>0,
        'Actual phases frozen in same installed process before durable ACK')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),observation=observation,application_root_cause=False,shipping_qualified=False)

def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    p=sandbox/'tmp'/('parlor-pin47-runloop-'+token)/'runloop.json'
    require(not p.is_symlink() and p.resolve(strict=True)==p.absolute(),'Owned unredirected failure-only runloop file')
    # Reuse the established secure 64KiB reader, then enforce this companion's
    # stricter 16KiB ceiling BEFORE creating a destination. Do not broaden the
    # reader's allowlist or leave an empty file that resembles retained evidence.
    raw=runtime.pair_control.leaf.run_pin03.read_capture(p,maximum=65536)
    require(len(raw)<=MAX_BYTES,'Exact lower run-loop companion ceiling')
    with (destination/('native-runloop-'+token+'.json')).open('xb') as f:f.write(raw)
    return decode_companions(destination,token,simulator,bundle,legacy)

def verify_commands(state,row,*,cases=CASES):
    selected_cases(cases)
    test_budget=2700 if cases==SAMPLE_CASES else 900
    require(state['commands'] and all(c['status']=='EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']),
        'Every child exited/reaped with no retries/signals')
    for stage,code,budget in (('build',0,2700),('tests',row['test_exit'],test_budget),('gradle-stop',0,120)):
        selected=[c for c in state['commands'] if c['label']=='compose-'+stage]
        require(len(selected)==1 and selected[0]['exit_code']==code and selected[0]['timeout_seconds']==budget,'One bounded actual build/test/cleanup command')
        if stage!='tests':continue
        argv=selected[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')]==['-only-testing:iosAppUITests/'+c.removesuffix('()') for c in cases]
            and argv.count('/usr/bin/xcodebuild')==argv.count('test-without-building')==1 and '-quiet' not in argv
            and not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests','-run-tests-until-failure')) for a in argv),'Original two methods then control once, no retries or skips')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),
            ('-sdk','iphonesimulator'),('-destination','platform=iOS Simulator,id='+row['simulator']),
            ('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag)==1 and argv[argv.index(flag)+1]==value,'Exact declared original command/device flags')
    labels=[c['label'] for c in state['commands']]
    require(labels.index('compose-build')<labels.index('compose-tests')<labels.index('compose-gradle-stop'),'Build/test/cleanup ordering')
    require(state['build_profile']['test_timeout_seconds']==test_budget,'Exact declared cohort process budget; no assertion extension')

def accept_native_export(out,state,*,cases=CASES,profile=PROFILE):
    require((profile,cases) in ((PROFILE,CASES),(EXPORT_PROFILE,EXPORT_CASES),(SAMPLE_PROFILE,SAMPLE_CASES),
                              (QUERY_PROFILE,QUERY_CASES)),'Exact declared cohort/profile pair')
    require_capability_binding()
    require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device'],state['debug_bundle'],state['xcode'],state['sdk'])==
        (profile,SOURCE,TREE,RUNTIME,DEVICE,'me.parlor.ios.debug','Xcode 26.3\nBuild version 17C529\n','26.2') and len(state['arms'])==1,'Exact source/compiler/platform/single arm')
    row=state['arms'][0];d=out/'compose'
    require(row['arm']=='compose' and row['build_exit']==0 and row['test_exit'] in (0,65) and row['cleanup']=='PASS'
        and row['result']==('JOURNEYS_PASSED' if row['test_exit']==0 else 'JOURNEYS_FAILED'),'Actual outcome, real build and cleanup')
    methods=verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit'],cases=cases)
    require(methods==row['methods'] and row['attachment_test_ids']==sorted(k for k,v in methods.items() if v=='Failed'),'All actual natural/control failures retained')
    leaf=runtime.pair_control.leaf
    plan=renderer_for_profile(profile,SOURCE,RUNTIME,DEVICE)({p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')
    require(load(d/'inputs.json')==state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},'Exact original inputs plus only reviewed copied Swift owner/driver')
    product=frozen_sources._read(d/'product.json',2097152)
    require(load(d/'product.json') and hashlib.sha256(product).hexdigest()==row['product_manifest_sha256'],'Built product byte custody')
    owners=runtime.pair_control.samples.requests_by_method(d,methods);captures=[];reports=[]
    for token in sorted(owners):
        raw=frozen_sources._read(d/('capture-'+token+'.json'),pin02_capture.MAX_BYTES)
        decoded=leaf.run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(decoded['snapshot']==dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and decoded['numeric_buffer_complete'] is False,'ACK only, no invented app data')
        captures.append(decoded);reports.append(decode_companions(d,token,row['simulator'],state['debug_bundle'],json.loads(raw)))
    require(row.get('captures',[])==captures and row.get('companion_captures',[])==reports and not row.get('root_companion_captures') and not row.get('motion_companion_captures'),'Exact actual failed-only companions')
    for prefix in ('capture-','native-runloop-'):
        require({p.name for p in d.glob(prefix+'*.json')}=={prefix+t+'.json' for t in owners},'One companion per original/control failure, none on success')
    for prefix in ('native-touch-','native-only-','native-accessibility-','native-frame-','native-root-'):
        require(not any(d.glob(prefix+'*.json')),'No fabricated absent native/input hooks')
    cleanup=load(d/'cleanup.json')
    require(cleanup['gradle_exit']==0 and cleanup['workers_before']['result']==cleanup['workers_after']['result']==cleanup['simulator']['result']=='PASS' and cleanup['simulator']['udid']==row['simulator'],'Owned workers/device retired')
    verify_commands(state,row,cases=cases)
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts)==cases,'Exact selected methods execute once in actual order')
    receipt=dict(methods=methods,failed_sample_tokens=owners,native=reports,capability_run=CAPABILITY_RUN,capability_harness=CAPABILITY_HARNESS,
        capability_state_sha256=CAPABILITY_STATE_SHA256,all_failed_samples_retained=True,application_root_cause=False,shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target=out/'failure-runloop.json'
    if target.exists():require(load(target)==receipt,'Exact saved receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT


class ExportControlProfile:
    """Only the failed exporter is repeated; accepted natural journeys are not."""
    PROFILE=EXPORT_PROFILE
    ARMS=ARMS
    TREE=TREE
    TEST_TIMEOUT_SECONDS=TEST_TIMEOUT_SECONDS
    SCOPE='Correct failed PIN47 retention once; only its isolated absent-destination control, no successful natural/KMP/cohort replay or qualification'
    renderer_for_profile=staticmethod(renderer_for_profile)
    retain_companion=staticmethod(retain_companion)
    test_command=staticmethod(partial(test_command,cases=EXPORT_CASES))
    verify_results=staticmethod(partial(verify_results,cases=EXPORT_CASES))
    failed_method_exports=staticmethod(partial(failed_method_exports,cases=EXPORT_CASES))
    accept_native_export=staticmethod(partial(accept_native_export,cases=EXPORT_CASES,profile=EXPORT_PROFILE))


class FixedSampleProfile(ExportControlProfile):
    PROFILE=SAMPLE_PROFILE
    TEST_TIMEOUT_SECONDS=2700
    SCOPE='Ten predeclared passive samples on original26.2/17Pro; five AR then five EN, each original journey once, failure-only phases, no artificial exporter/KMP/full-cohort replay, action retry or qualification'
    test_command=staticmethod(partial(test_command,cases=SAMPLE_CASES))
    verify_results=staticmethod(partial(verify_results,cases=SAMPLE_CASES))
    failed_method_exports=staticmethod(partial(failed_method_exports,cases=SAMPLE_CASES))
    accept_native_export=staticmethod(partial(accept_native_export,cases=SAMPLE_CASES,profile=SAMPLE_PROFILE))


class QueryPropertyProfile(ExportControlProfile):
    PROFILE=QUERY_PROFILE
    SCOPE='Original AR/EN pair once with per-property XCTest clocks; same passive native owner, no added AX query, gesture, assertion extension, successful cohort/exporter replay or qualification'
    test_command=staticmethod(partial(test_command,cases=QUERY_CASES))
    verify_results=staticmethod(partial(verify_results,cases=QUERY_CASES))
    failed_method_exports=staticmethod(partial(failed_method_exports,cases=QUERY_CASES))

    @staticmethod
    def accept_native_export(out,state):
        result=accept_native_export(out,state,cases=QUERY_CASES,profile=QUERY_PROFILE)
        import pin48_property_timing
        pin48_property_timing.write_receipt(out,state)
        return result


def select_profile(profile):
    require(profile in PROFILES,'One explicit failure-runloop profile')
    if profile==EXPORT_PROFILE:return ExportControlProfile()
    if profile==SAMPLE_PROFILE:return FixedSampleProfile()
    if profile==QUERY_PROFILE:return QueryPropertyProfile()
    import sys
    return sys.modules[__name__]
