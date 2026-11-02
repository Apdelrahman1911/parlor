"""Original focus return and weak editor lifetime after admitted natural AR failure."""
import hashlib
import json
import math
import re
from pathlib import Path

import frozen_sources
import pin02_capture
import pin53_root_lifecycle as root
import pin54_original_root_pair as previous
base = root.base
import pin47_failure_runloop as original
import run_pin03

SOURCE, TREE, RUNTIME, DEVICE = base.SOURCE, base.TREE, base.RUNTIME, base.DEVICE
PROFILE = 'original-first-responder-boundary'
ARMS = ('compose',)
CONTROL_CASE = 'IOSAppLaunchUITests/testAPIN55FirstResponderCapability()'
CASES = (CONTROL_CASE, *original.QUERY_CASES)
FAILURE = 'PIN55 intentional failure after original public name input; responder exporter control only'
TEST_TIMEOUT_SECONDS = 900
RESULT = 'ORIGINAL_FIRST_RESPONDER_BOUNDARY_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('One new responder exporter control then original AR/EN methods once; original helpers/actions/assertions/10s budgets, '
         'passive original focus result and weak editor lifetime; no focus repair, query/tap retry, Kotlin/Compose change or qualification')
FOCUS_FILE = Path(__file__).resolve().parent/'PIN55FirstResponder.swift.in'
FOCUS_SHA256 = '4055c2046487655a512b9ed0e6a0bb2db940a31adec9dc1219cc50fcba29448a'
PREREQUISITE_FILE = Path(__file__).resolve().parents[3]/'docs/audits/2026-11-02-IOS-ORIGINAL-ROOT-FAILURE.json'
PREREQUISITE_SHA256 = 'ad6e2a10e5cc6eee3f43982ce89bdbe2b5875a3b3923949f7a276899bdba8ecc'
require, load = root.require, root.load
runtime, MAX_BYTES = base.runtime, base.MAX_BYTES

def require_capability():
    previous.require_capability()
    raw=frozen_sources._read(PREREQUISITE_FILE,65536)
    require(hashlib.sha256(raw).hexdigest()==PREREQUISITE_SHA256,'Exact independently admitted natural failure')
    v=load(PREREQUISITE_FILE)
    require(v['run']==41502134475 and v['harness']=='5a0d35cfbe19dd559246ec09ca4dddd465c77991' and
        v['artifact_sha256']=='f9b1d5967877903d20fc6af27b365cd501ff96352e711ab745382c026622da50' and
        v['actual_methods']=={original.QUERY_CASES[0]:'Failed',original.QUERY_CASES[1]:'Passed'} and
        v['actual_mac_controls']==9 and v['skipped_controls']==0 and v['actual_xctest_exit']==65 and
        v['collection_admitted'] is True and v['owned_cleanup']=='PASS' and v['root_queries_added']==0 and
        all(v[k] is False for k in ('application_root_cause','shipping_qualified','physical_verified')),
        'Actual previous failure, not application qualification')


def focus_source():
    raw=frozen_sources._read(FOCUS_FILE,524288)
    require(hashlib.sha256(raw).hexdigest()==FOCUS_SHA256,'Exact new original focus forwarding source')
    return raw.decode()


def delivery_source():
    # Preserve all original mutator/attachment calls and pre-focus installation
    # checks. Its inheritedResponderUntouched records that earlier installation,
    # not current inheritance after our explicitly declared added leaf method.
    s=base.template('PIN51OwnedTextDelivery.swift.in')
    s=base.pin01_copy.once(s,'    private let armed: () -> Bool',
        '    var onOwnedEditor: ((AnyObject, AnyClass) -> Void)?\n    private let armed: () -> Bool')
    s=base.pin01_copy.once(s,'        append(kind: 7, phase: 0)',
        '        onOwnedEditor?(child, target) // After original delivery installation; before original attachment returns.\n        append(kind: 7, phase: 0)')
    return s.replace('PIN51OwnedTextDelivery','PIN55FocusTextDelivery')


def renderer_for_profile(profile, source, os, device):
    require((profile,source,os,device)==(PROFILE,SOURCE,RUNTIME,DEVICE),'Exact new original focus source/runtime')
    old=previous.renderer_for_profile(previous.PROFILE,source,os,device)
    def render(sources,digests,*,arm):
        plan=old(sources,digests,arm=arm)
        owner=base.pin01_copy.once(plan[pin02_capture.CONTAINER],base.template('PIN51OwnedTextDelivery.swift.in'),delivery_source())
        owner=owner.replace('PIN51OwnedTextDelivery','PIN55FocusTextDelivery')
        owner=base.pin01_copy.once(owner,'    private var rootLifecycle: PIN53RootLifecycle?',
            '    private var focus: PIN55FirstResponderObservation?\n    private var rootLifecycle: PIN53RootLifecycle?')
        owner=base.pin01_copy.once(owner,'        let delivery = PIN55FocusTextDelivery(armed: armed)',
            '        focus = PIN55FirstResponderObservation(armed: armed)\n'
            '        let delivery = PIN55FocusTextDelivery(armed: armed)\n'
            '        delivery.onOwnedEditor = { [weak self] object, target in self?.focus?.bindVerifiedEditor(object, target: target) }')
        owner=base.pin01_copy.once(owner,'        let frozenRoot = rootLifecycle?.freeze() ?? [:]',
            '        let frozenRoot = rootLifecycle?.freeze() ?? [:]\n        let frozenFocus = focus?.freeze() ?? [:]')
        owner=base.pin01_copy.once(owner,'["schema": 53, "token": token','["schema": 55, "token": token')
        owner=base.pin01_copy.once(owner,'"root": frozenRoot]','"root": frozenRoot, "focus": frozenFocus]')
        owner=owner.replace('PARLOR_PIN53_','PARLOR_PIN55_').replace('parlor-pin53-','parlor-pin55-')
        owner=base.pin01_copy.once(owner,previous.PROFILE,PROFILE)+'\n'+focus_source()
        driver=plan[pin02_capture.DRIVER].replace('PIN53Configure','PIN55Configure').replace('PARLOR_PIN53_','PARLOR_PIN55_')
        driver=base.pin01_copy.once(driver,previous.PROFILE,PROFILE)
        sentinel=base.SENTINEL.replace('PIN51Configure','PIN55Configure').replace('testPIN51OwnedTextExportCapability',CONTROL_CASE.split('/')[1].removesuffix('()')).replace(base.FAILURE,FAILURE)
        # New focused-editor control exports before Done, which legitimately retires
        # that editor. The original AR/EN methods retain their unchanged Done input.
        sentinel=base.pin01_copy.once(sentinel,'        name.typeText("\\n")\n','')
        driver=base.pin01_copy.once(driver,'    private struct KeyboardCopy {',sentinel+'    private struct KeyboardCopy {')
        return plan|{pin02_capture.CONTAINER:owner,pin02_capture.DRIVER:driver}
    return render


def test_command(command,result):
    return [*(a for a in command if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
        *('-only-testing:iosAppUITests/'+c.removesuffix('()') for c in CASES)]


def verify_results(summary,tests,simulator,code):
    require(type(code) is int and code==65,'Actual exporter and natural failures retain XCTest65')
    for k in ('totalTestCount','passedTests','failedTests','skippedTests','expectedFailures'):
        require(type(summary.get(k)) is int and 0<=summary[k]<=3,'Exact actual new three-method counts')
    require(summary['totalTestCount']==3 and summary['passedTests']+summary['failedTests']==3 and
        summary['skippedTests']==summary['expectedFailures']==0 and summary['result']=='Failed','All three executed; none skipped/expected')
    configs=summary.get('devicesAndConfigurations',[]);devices=tests.get('devices',[])
    require(len(configs)==len(devices)==len(tests.get('testPlanConfigurations',[]))==1,'One owned configuration')
    for d in (configs[0].get('device',{}),devices[0]):
        require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion'))==
            (simulator,'arm64','iOS Simulator','iPhone 17 Pro','26.2'),'Actual exact original runtime/model')
    nodes=original.visit(tests.get('testNodes',[]))
    require(len(nodes)==3 and {n.get('nodeIdentifier') for n in nodes}==set(CASES) and all(n.get('result') in ('Passed','Failed') and
        type(n.get('durationInSeconds')) in (int,float) and math.isfinite(n['durationInSeconds']) and
        0<n['durationInSeconds']<900 for n in nodes),'Exact methods and real finite positive durations')
    methods={n['nodeIdentifier']:n['result'] for n in nodes};failures=summary.get('testFailures',[])
    require(methods[CONTROL_CASE]=='Failed' and sum(v=='Passed' for v in methods.values())==summary['passedTests'] and
        all(f.get('testIdentifierString') in methods and methods[f['testIdentifierString']]=='Failed' for f in failures) and
        {f['testIdentifierString'] for f in failures}=={k for k,v in methods.items() if v=='Failed'},'Every actual failure retained')
    control=[f for f in failures if f['testIdentifierString']==CONTROL_CASE]
    require(len(control)==1 and FAILURE in control[0].get('failureText',''),'New control fails solely at exporter, not original input helpers')
    return methods


def failed_method_exports(result,destination,outcomes):
    require(set(outcomes)==set(CASES),'All actual new control and original outcomes')
    return [(destination/('failed-%02d'%i),['export','attachments','--path',str(result),'--output-path',str(destination/('failed-%02d'%i)),'--test-id',c])
        for i,c in enumerate(sorted(k for k,v in outcomes.items() if v=='Failed'),1)]


def decode(raw,*,token,simulator,bundle,legacy,require_control=False):
    require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded new native focus evidence')
    v=json.loads(raw,object_pairs_hook=base.pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite focus evidence')))
    require(type(v) is dict and type(v.get('schema')) is int and v['schema']==55 and 'focus' in v,'New actual focus schema')
    previous={k:value for k,value in v.items() if k!='focus'};previous['schema']=53
    native=root.decode(json.dumps(previous).encode(),token=token,simulator=simulator,bundle=bundle,legacy=legacy,require_complete=require_control)
    f=v['focus']
    require(type(f) is dict and set(f)=={'schema','attempted','installed','installedMs','parentUntouched','leafHookIntact','editorsAlive','invalid','overflow','rows','capturedMs'} and
        type(f['schema']) is int and f['schema']==55 and all(type(f[k]) is bool for k in ('attempted','installed','parentUntouched','leafHookIntact')) and
        type(f['editorsAlive']) is list and len(f['editorsAlive'])<=128 and all(type(b) is bool for b in f['editorsAlive']) and
        all(type(f[k]) is int and 0<=f[k]<=1000000 for k in ('invalid','overflow')) and type(f['rows']) is list and len(f['rows'])<=4096 and
        type(f['capturedMs']) in (int,float) and math.isfinite(f['capturedMs']) and legacy['installedMs']<=f['capturedMs']<=v['capturedMs'],'Exact private-free bounds and failure clock')
    if f['installed']:
        require(type(f['installedMs']) in (int,float) and math.isfinite(f['installedMs']) and legacy['installedMs']<=f['installedMs']<=f['capturedMs'],'Actual installed focus clock')
    else:require(f['installedMs'] is None,'Absent installation stays absent')
    stack=[];now=legacy['installedMs']
    for row in f['rows']:
        require(type(row) is list and len(row)==4 and all(type(n) in (int,float) and math.isfinite(n) for n in row),'Only numeric focus rows')
        t,phase,ordinal,result=row
        require(now<=t<=f['capturedMs'] and phase in (0,1) and ordinal==int(ordinal) and 1<=ordinal<=len(f['editorsAlive']) and result in (0,1) and
            (phase==1 or result==0),'Original ordered responder calls and result bits')
        now=t
        if require_control:
            if phase==0:stack.append(ordinal)
            else:require(stack and stack.pop()==ordinal,'Original focus call pairs must return')
    complete=f['attempted'] and f['installed'] and f['parentUntouched'] and f['leafHookIntact'] and f['invalid']==f['overflow']==0
    if require_control:
        require(complete and not stack and len(f['editorsAlive'])==native['text']['attachments'] and all(f['editorsAlive']) and
            any(r[1]==1 and r[3]==1 for r in f['rows']),'New positive original responder/live editor capability required')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),native=native,focus=f,focus_installation_complete=complete,
        text_installation_attempt_precedes_focus_hook=True,current_leaf_inherited_responder_untouched=False if f['installed'] else None,
        original_parent_responder_untouched=f['parentUntouched'],application_root_cause=False,shipping_qualified=False)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    p=sandbox/'tmp'/('parlor-pin55-'+token)/'delivery.json'
    require(not p.is_symlink() and p.resolve(strict=True)==p.absolute(),'Owned new focus evidence file')
    raw=base.run_pin03.read_capture(p,maximum=MAX_BYTES)
    report=decode(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('native-text-'+token+'.json')).open('xb') as f:f.write(raw)
    return report


def accept_native_export(out, state):
    require_capability()
    require((state['source'],state['source_tree'],state['profile'],state['runtime'],state['device'],state['xcode'],state['sdk'],state['debug_bundle']) ==
            (SOURCE,TREE,PROFILE,RUNTIME,DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug') and
            len(state['arms']) == 1, 'Exact source/compiler/Debug ownership')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm']=='compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
            row['result'] == ('JOURNEYS_PASSED' if row['test_exit']==0 else 'JOURNEYS_FAILED'), 'Real build/failure/cleanup')
    require(verify_results(load(d/'summary.json'), load(d/'tests.json'), row['simulator'], row['test_exit']) == row['methods'] and
            row['attachment_test_ids'] == sorted(k for k,v in row['methods'].items() if v=='Failed'), 'Actual original control assertion/attachments')
    leaf = runtime.pair_control.leaf
    require(all(state['original_inputs'].get(p) == h for p,h in leaf.pair.INPUTS.items()), 'Original source binding')
    plan = renderer_for_profile(PROFILE,SOURCE,RUNTIME,DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED}, leaf.pair.INPUTS, arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
            'Exact copied inputs, no original changes')
    product = frozen_sources._read(d/'product.json',2097152)
    require(hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'] and load(d/'product.json'), 'Product custody')
    owners = runtime.pair_control.samples.requests_by_method(d,row['methods'])
    require(len(owners) == sum(v=='Failed' for v in row['methods'].values()), 'One actual acknowledged request per original failure')
    reports, captures = [], []
    for token in sorted(owners):
        raw = frozen_sources._read(d/('capture-'+token+'.json'),pin02_capture.MAX_BYTES)
        legacy = json.loads(raw)
        capture = run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(capture['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
                capture['numeric_buffer_complete'] is False, 'ACK only; no fabricated state')
        captures.append(capture)
        reports.append(decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES), token=token,
            simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_control=False))
        if owners[token]==CONTROL_CASE:
            decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES),token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_control=True)
    require(not row.get('root_companion_captures') and not row.get('motion_companion_captures'), 'No absent side-channel reports')
    require(captures == row.get('captures',[]) and reports == row.get('companion_captures',[]),
            'Exact retained same-PID evidence')
    for prefix in ('capture-','native-text-'):
        require({p.name for p in d.glob(prefix+'*.json')} == {prefix+t+'.json' for t in owners}, 'One file per real failure')
    for prefix in ('native-runloop-','native-touch-','native-only-','native-accessibility-','native-frame-','native-root-'):
        require(not any(d.glob(prefix+'*.json')), 'No invented absent observation')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
            cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup')
    require(all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']), 'Reaped children')
    for stage, code, budget in (('build',0,2700),('tests',row['test_exit'],900),('gradle-stop',0,120)):
        calls = [c for c in state['commands'] if c['label'] == 'compose-'+stage]
        require(len(calls) == 1 and calls[0]['exit_code'] == code and calls[0]['timeout_seconds'] == budget,
                'One actual command per original bounded stage')
        if stage != 'tests': continue
        argv = calls[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')] == ['-only-testing:iosAppUITests/'+case.removesuffix('()') for case in CASES] and
                argv.count('test-without-building') == argv.count('/usr/bin/xcodebuild') == 1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-retry-tests','-test-iterations','-run-tests-until-failure')) for a in argv),
                'One exact control, no retry/skip')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),
            ('-sdk','iphonesimulator'),('-destination','platform=iOS Simulator,id='+row['simulator']),
            ('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag) == 1 and argv[argv.index(flag)+1] == value, 'Exact owned Debug command flags')
    require(state['build_profile']['test_timeout_seconds'] == 900, 'Original process limit unchanged')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Only original AR/EN methods executed once')
    receipt = dict(root_capability_run=41497962566,prerequisite_failure_run=41502134475,root_queries_added=False,methods=row['methods'],failed_sample_tokens=owners,native=reports,application_root_cause=False,
                   shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target = out/'first-responder-boundary.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved receipt')
    else:
        with target.open('x') as output: json.dump(receipt,output,indent=2)
    return RESULT
