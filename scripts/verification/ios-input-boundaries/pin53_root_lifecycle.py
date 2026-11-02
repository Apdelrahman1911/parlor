"""New native root getter/assignment capability, without proactive AX reads."""
import hashlib
import json
import math
from pathlib import Path

import pin51_owned_text as base
import pin52_owned_text_pair as prerequisite

SOURCE,TREE,RUNTIME,DEVICE=base.SOURCE,base.TREE,base.RUNTIME,base.DEVICE
PROFILE='isolated-owned-root-lifecycle-export'
CASE='IOSAppLaunchUITests/testPIN53RootLifecycleExportCapability()'
CASES=(CASE,)
ARMS=base.ARMS
TEST_TIMEOUT_SECONDS=base.TEST_TIMEOUT_SECONDS
RESULT='OWNED_ROOT_LIFECYCLE_CAPABILITY_COMPLETE_NOT_QUALIFICATION'
SCOPE='New original root getter/assignment arrival/presence capability; no proactive root AX getter, labels/values, original actions/assertions/budgets and native text collector unchanged; failure-only numeric export, not causality or qualification'
FAILURE='PIN53 intentional failure after original public name input; root exporter control only'
HERE=Path(__file__).resolve().parent
ROOT_TEMPLATE='PIN53RootLifecycle.swift.in'
ROOT_SHA256='81f0eb1ef7d9fb07271d6f5948cdd92749f42665d33927cfda5bd07fd669843b'
MAX_BYTES=base.MAX_BYTES
require,load=base.require,base.load
runtime,pin02_capture=base.runtime,base.pin02_capture
pin01_copy,frozen_sources=base.pin01_copy,base.frozen_sources


def root_source():
    raw=frozen_sources._read(HERE/ROOT_TEMPLATE,MAX_BYTES)
    require(hashlib.sha256(raw).hexdigest()==ROOT_SHA256,'Exact new root lifecycle source')
    return raw.decode()


def owner_source():
    s=base.template('PIN51OwnedTextObservation.swift.in')
    s=pin01_copy.once(s,'    private var delivery: PIN51OwnedTextDelivery?',
        '    private var rootLifecycle: PIN53RootLifecycle?\n    private var delivery: PIN51OwnedTextDelivery?')
    s=pin01_copy.once(s,'        let delivery = PIN51OwnedTextDelivery(armed: armed)',
        '        rootLifecycle = PIN53RootLifecycle(armed: armed)\n        let delivery = PIN51OwnedTextDelivery(armed: armed)')
    s=pin01_copy.once(s,'        if treeComplete && inputs.count == 1 { deliveryBound = delivery?.bindInputContainer(inputs[0]) == true }',
        '        if treeComplete && inputs.count == 1 {\n'
        '            deliveryBound = delivery?.bindInputContainer(inputs[0]) == true\n'
        '            if let target = object_getClass(inputs[0]) {\n'
        '                _ = rootLifecycle?.bindVerifiedOverlay(inputs[0], target: target)\n'
        '            }\n        }')
    s=pin01_copy.once(s,'        let frozenDelivery = delivery?.freeze() ?? [:]',
        '        let frozenDelivery = delivery?.freeze() ?? [:]\n        let frozenRoot = rootLifecycle?.freeze() ?? [:]')
    s=pin01_copy.once(s,'["schema": 51, "token": token','["schema": 53, "token": token')
    s=pin01_copy.once(s,'"delivery": frozenDelivery, "runloop": observation]',
        '"delivery": frozenDelivery, "runloop": observation, "root": frozenRoot]')
    return s.replace('PIN51OwnedTextObservation','PIN53CombinedObservation').replace('PARLOR_PIN51_','PARLOR_PIN53_').replace('parlor-pin51-','parlor-pin53-').replace(base.PROFILE,PROFILE)


def renderer_for_profile(profile,source,os,device):
    require((profile,source,os,device)==(PROFILE,SOURCE,RUNTIME,DEVICE),'Exact new capability source/runtime')
    old=base.renderer_for_profile(base.PROFILE,source,os,device)
    def render(sources,digests,*,arm):
        plan=old(sources,digests,arm=arm)
        container=pin01_copy.once(plan[pin02_capture.CONTAINER],base.template('PIN51OwnedTextObservation.swift.in'),owner_source())
        container=container.replace('PIN51OwnedTextObservation','PIN53CombinedObservation')+'\n'+root_source()
        driver=plan[pin02_capture.DRIVER].replace('PIN51Configure','PIN53Configure').replace('PARLOR_PIN51_','PARLOR_PIN53_').replace(base.PROFILE,PROFILE)
        driver=driver.replace(base.CASE.removesuffix('()').split('/')[1],CASE.removesuffix('()').split('/')[1]).replace(base.FAILURE,FAILURE)
        return plan|{pin02_capture.CONTAINER:container,pin02_capture.DRIVER:driver}
    return render


def test_command(command,result):
    return [a.replace(base.CASE.removesuffix('()'),CASE.removesuffix('()')) for a in base.test_command(command,result)]


def verify_results(summary,tests,simulator,code,*,require_exporter=False):
    # Bind the separately named actual method first; map identities only, never its failure outcome.
    failures=summary.get('testFailures',[])
    require(failures and all(f.get('testIdentifierString')==CASE for f in failures),'Actual new capability failures only')
    def replace(value):
        if isinstance(value,str):return value.replace(CASE,base.CASE).replace(FAILURE,base.FAILURE)
        if isinstance(value,list):return [replace(v) for v in value]
        if isinstance(value,dict):return {k:replace(v) for k,v in value.items()}
        return value
    base.verify_results(replace(summary),replace(tests),simulator,code,require_exporter=require_exporter)
    return {CASE:'Failed'}


def failed_method_exports(result,destination,outcomes):
    require(outcomes=={CASE:'Failed'},'Only actual new control owns export')
    return [(destination/'failed-01',['export','attachments','--path',str(result),'--output-path',
        str(destination/'failed-01'),'--test-id',CASE])]


def decode(raw,*,token,simulator,bundle,legacy,require_complete=False):
    require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded native root evidence')
    v=json.loads(raw,object_pairs_hook=base.pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite data')))
    require(type(v) is dict and v.get('schema')==53 and type(v['schema']) is int and 'root' in v,'Actual new schema')
    prior={k:value for k,value in v.items() if k!='root'};prior['schema']=51
    text=base.decode(json.dumps(prior).encode(),token=token,simulator=simulator,bundle=bundle,legacy=legacy,
        require_complete=require_complete)
    r=v['root']
    require(type(r) is dict and set(r)=={'schema','installation','attemptedRoot','overflow','overlayAlive','rootAlive','rows','capturedMs'} and
        type(r['schema']) is int and r['schema']==53 and type(r['installation']) is list and len(r['installation'])==3 and
        all(type(n) is int and n in (0,1) for n in r['installation']) and
        all(type(r[k]) is bool for k in ('attemptedRoot','overlayAlive','rootAlive')) and
        type(r['overflow']) is int and 0<=r['overflow']<=1000000 and type(r['rows']) is list and len(r['rows'])<=4096 and
        type(r['capturedMs']) in (int,float) and math.isfinite(r['capturedMs']) and
        legacy['installedMs']<=r['capturedMs']<=v['capturedMs'],'Exact numeric root bounds/clocks')
    previous=legacy['installedMs'];stack=[]
    for row in r['rows']:
        require(type(row) is list and len(row)==4 and all(type(n) in (int,float) and math.isfinite(n) for n in row),'Numeric/private-free root rows')
        now,kind,phase,present=row
        require(previous<=now<=r['capturedMs'] and kind in (1,2,3) and phase in (0,1) and present in (0,1) and
            (present==0 or kind==2 and phase==0 or kind in (1,3) and phase==1),'Ordered real root calls/presence bits')
        previous=now
        if require_complete:
            if phase==0:stack.append(kind)
            else:require(stack and stack.pop()==kind,'Original root calls must return, no invented pairing')
    complete=r['installation']==[1,1,1] and r['attemptedRoot'] and r['rootAlive'] and r['overlayAlive'] and r['overflow']==0
    if require_complete:
        require(complete and not stack and all(any(row[1]==kind and row[2]==0 for row in r['rows']) for kind in (1,2,3)),
            'Actual root getter/assignment/overlay arrival and complete owned installation required')
        require(any(row[1:]==[2,0,1] for row in r['rows']) and any(row[1:]==[1,1,1] for row in r['rows']),
            'Actual nonempty original root assignment and getter return required for capability')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),text=text,root_complete=complete,root=r,
        application_root_cause=False,shipping_qualified=False)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    p=sandbox/'tmp'/('parlor-pin53-'+token)/'delivery.json'
    require(not p.is_symlink() and p.resolve(strict=True)==p.absolute(),'Owned new root file')
    raw=base.run_pin03.read_capture(p,maximum=MAX_BYTES)
    result=decode(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('native-text-'+token+'.json')).open('xb') as f:f.write(raw)
    return result


def accept_native_export(out,state):
    prerequisite.require_capability()
    require((state['source'],state['source_tree'],state['profile'],state['runtime'],state['device'],state['xcode'],state['sdk'],state['debug_bundle'])==
        (SOURCE,TREE,PROFILE,RUNTIME,DEVICE,'Xcode 26.3\nBuild version 17C529\n','26.2','me.parlor.ios.debug') and len(state['arms'])==1,'Exact source/compiler/Debug ownership')
    row=state['arms'][0];d=out/'compose'
    require(row['arm']=='compose' and row['build_exit']==0 and row['cleanup']=='PASS' and row['result']=='JOURNEYS_FAILED','Real build/failure/cleanup')
    require(verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit'],require_exporter=True)==row['methods'] and
        row['attachment_test_ids']==[CASE],'Actual separately named sole exporter failure')
    leaf=runtime.pair_control.leaf
    plan=renderer_for_profile(PROFILE,SOURCE,RUNTIME,DEVICE)({p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='compose')
    require(all(state['original_inputs'].get(p)==h for p,h in leaf.pair.INPUTS.items()) and
        load(d/'inputs.json')==state['original_inputs']|{p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},'Exact original/copied source')
    product=frozen_sources._read(d/'product.json',2097152)
    require(load(d/'product.json') and hashlib.sha256(product).hexdigest()==row['product_manifest_sha256'],'Actual product bytes')
    owners=runtime.pair_control.samples.requests_by_method(d,row['methods']);require(len(owners)==1,'One actual acknowledged failed request')
    reports=[];captures=[]
    for token in sorted(owners):
        raw=frozen_sources._read(d/('capture-'+token+'.json'),pin02_capture.MAX_BYTES);legacy=json.loads(raw)
        capture=base.run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(capture['snapshot']==dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and not capture['numeric_buffer_complete'],'ACK only')
        captures.append(capture);reports.append(decode(frozen_sources._read(d/('native-text-'+token+'.json'),MAX_BYTES),
            token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_complete=True))
    require(row.get('captures',[])==captures and row.get('companion_captures',[])==reports,'Exact same-PID retained report')
    for prefix in ('capture-','native-text-'):
        require({p.name for p in d.glob(prefix+'*.json')}=={prefix+t+'.json' for t in owners},'One owned report per failure')
    for prefix in ('native-runloop-','native-touch-','native-only-','native-accessibility-','native-frame-','native-root-'):
        require(not any(d.glob(prefix+'*.json')),'No invented absent companion')
    cleanup=load(d/'cleanup.json')
    require(cleanup['gradle_exit']==0 and cleanup['workers_before']['result']==cleanup['workers_after']['result']==cleanup['simulator']['result']=='PASS' and
        cleanup['simulator']['udid']==row['simulator'],'Actual owned cleanup')
    # Reuse original command custody with this exact new case, not old outcomes or deadline remapping.
    require(all(c['status']=='EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']),'Reaped children')
    for stage,code,budget in (('build',0,2700),('tests',65,900),('gradle-stop',0,120)):
        calls=[c for c in state['commands'] if c['label']=='compose-'+stage]
        require(len(calls)==1 and calls[0]['exit_code']==code and calls[0]['timeout_seconds']==budget,'One original bounded command')
        if stage!='tests':continue
        argv=calls[0]['command']
        require([a for a in argv if a.startswith('-only-testing:')]==['-only-testing:iosAppUITests/'+CASE.removesuffix('()')] and
            argv.count('/usr/bin/xcodebuild')==argv.count('test-without-building')==1 and '-quiet' not in argv and
            not any(a.startswith(('-skip-testing','-retry-tests','-test-iterations','-run-tests-until-failure')) for a in argv),'Exact case once, no retry/skip')
        for flag,value in (('-project','iosApp/iosApp.xcodeproj'),('-scheme','iosApp'),('-configuration','Debug'),('-sdk','iphonesimulator'),
            ('-destination','platform=iOS Simulator,id='+row['simulator']),('-parallel-testing-enabled','NO'),('-maximum-concurrent-test-simulator-destinations','1')):
            require(argv.count(flag)==1 and argv[argv.index(flag)+1]==value,'Exact owned command flags')
    require(state['build_profile']['test_timeout_seconds']==900,'No deadline extension')
    import re
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4194304))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts)==CASES,'Only new separately named case once')
    receipt=dict(methods=row['methods'],native=reports,capability_run=41438650444,root_queries_added=False,
        application_root_cause=False,shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target=out/'root-lifecycle-export.json'
    if target.exists():require(load(target)==receipt,'Exact saved receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
