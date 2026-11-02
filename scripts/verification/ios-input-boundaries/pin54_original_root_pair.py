"""Original AR/EN root-lifetime investigation after actual root capability admission."""
import hashlib
import json
import re
from pathlib import Path

import frozen_sources
import pin02_capture
import pin53_root_lifecycle as root
base = root.base
import pin47_failure_runloop as original
import run_pin03

SOURCE, TREE, RUNTIME, DEVICE = base.SOURCE, base.TREE, base.RUNTIME, base.DEVICE
PROFILE = 'original-owned-root-lifecycle-pair'
ARMS = ('compose',)
CASES = original.QUERY_CASES
TEST_TIMEOUT_SECONDS = 900
RESULT = 'ORIGINAL_ROOT_LIFECYCLE_PAIR_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original AR/EN identifiers once with newly admitted passive root observer; '
         'no proactive root getter, original actions/10s guards and native text bytes unchanged; '
         'failure-only numeric evidence, no successful-path export or causality/qualification claim')
CAPABILITY_FILE = Path(__file__).resolve().parents[3]/'docs/audits/2026-11-02-IOS-ROOT-LIFECYCLE-CAPABILITY.json'
CAPABILITY_SHA256 = '73d64d4426256b80e420cf0a689548fa537d69e9c6e52cc036118b10ea44932f'
CAPABILITY_ARTIFACT = '7311dd0be78544d83ef372a019195d00a5827fe6943327aac8e4c9a36da34e07'
require, load, decode, retain_companion = root.require, root.load, root.decode, root.retain_companion
runtime, MAX_BYTES = base.runtime, base.MAX_BYTES

def require_capability():
    root.prerequisite.require_capability()
    raw=frozen_sources._read(CAPABILITY_FILE,65536)
    require(hashlib.sha256(raw).hexdigest()==CAPABILITY_SHA256,'Exact actual independently admitted root capability')
    v=load(CAPABILITY_FILE)
    require(v['run']==41497962566 and v['harness']=='8fddc81c7b48f695438018821093eb6d8f02a076' and
            v['artifact_sha256']==CAPABILITY_ARTIFACT,'Exact actual root run/source/artifact')
    require(v['result']=='INDEPENDENT_ROOT_LIFECYCLE_CAPABILITY_ACCEPTED_NOT_APPLICATION_QUALIFICATION' and
            v['native'] is True and v['actual_mac_controls']==9 and v['skipped_controls']==0 and
            v['actual_xctest_exit']==65 and v['owned_cleanup']=='PASS' and v['workflow_conclusion']=='success' and
            v['original_pair_replayed']==0 and v['root_queries_added']==0 and v['admission_error'] is None and
            v['actual_methods']=={root.CASE:'Failed'} and all(v[k] is False for k in
                ('application_root_cause','shipping_qualified','physical_verified')),'Actual root capability, not application qualification')


def renderer_for_profile(profile, source, os, device):
    require((profile,source,os,device)==(PROFILE,SOURCE,RUNTIME,DEVICE),'Exact original root-pair source/runtime')
    # Reuse original PIN52 driver and PIN53 native owner byte-for-byte except
    # matching explicit scope. Existing original failure clocks are retained.
    import pin52_owned_text_pair as text
    old=text.renderer_for_profile(text.PROFILE,source,os,device)
    owned=root.renderer_for_profile(root.PROFILE,source,os,device)
    def render(sources,digests,*,arm):
        plan=old(sources,digests,arm=arm)
        driver=plan[pin02_capture.DRIVER].replace('PIN51Configure','PIN53Configure').replace('PARLOR_PIN51_','PARLOR_PIN53_')
        driver=base.pin01_copy.once(driver,text.PROFILE,PROFILE)
        owner=base.pin01_copy.once(owned(sources,digests,arm=arm)[pin02_capture.CONTAINER],root.PROFILE,PROFILE)
        return plan|{pin02_capture.DRIVER:driver,pin02_capture.CONTAINER:owner}
    return render


def test_command(command,result):
    return original.test_command(command,result,cases=CASES)


def verify_results(summary,tests,simulator,code):
    return original.verify_results(summary,tests,simulator,code,cases=CASES)


def failed_method_exports(result,destination,outcomes):
    return original.failed_method_exports(result,destination,outcomes,cases=CASES)


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
            simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,require_complete=False))
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
    receipt = dict(capability_run=41497962566,capability_sha256=CAPABILITY_SHA256,root_queries_added=False,methods=row['methods'],failed_sample_tokens=owners,native=reports,application_root_cause=False,
                   shipping_qualified=False,physical_verified=False,native_timing_neutrality=False)
    target = out/'root-lifecycle-pair.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved receipt')
    else:
        with target.open('x') as output: json.dump(receipt,output,indent=2)
    return RESULT
