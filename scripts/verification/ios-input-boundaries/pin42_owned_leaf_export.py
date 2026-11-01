"""Isolated failure-export capability with ONLY seven original owned-leaf methods.

The inherited setter is untouched. No claim that its prior hook caused/masked
natural failures. Actual ABI/installation/export proof precedes natural sampling.
"""
import hashlib
import json
from pathlib import Path
import re

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin33_touch_decoder as original_decoder
import pin39_native_only_export as native
import pin41_original_input_only as prior
import run_pin03

SOURCE, TREE = native.SOURCE, native.TREE
pair = native.pair
PROFILE = 'isolated-owned-leaf-export'
ARMS = ('compose',)
CASE = 'IOSAppLaunchUITests/testPIN42OwnedLeafExportCapability()'
CASES = (CASE,)
RESULT = 'ISOLATED_OWNED_LEAF_EXPORT_CAPABILITY_NOT_QUALIFICATION'
SCOPE = ('One deliberately absent-destination failure with seven original owned touch/arbitration overrides; '
         'inherited state setter untouched; original Kotlin/Compose/SwiftUI/helpers/10s deadlines; '
         'failure-only private-free ACK/native custody, not natural causality, neutrality or qualification')
HERE = Path(__file__).resolve().parent
TOUCH = 'PIN42OwnedLeafTouch.swift.in'
OWNER = 'PIN42OwnedLeafOnly.swift.in'
PINS = {'PIN42OwnedLeafTouch.swift.in': 'f7c1657bcae823b61623ae1556fbb00567869d244ccdebd3a62160c9dc037049', 'PIN42OwnedLeafOnly.swift.in': '8c2390dad12d9829f9bbee924e42e7fff6fd341057c1c4e775e6455b645d27e5', 'PIN02Capture.swift.in': '4a3814846d15f854f2e7ad2510fa04795a220ed55f0d65793ee185e8d5716433', 'PIN02FailureRequest.swift.in': '07a613781ea793d323f7a54aede4dba8c94281bbc0ff94b18b437e1d71391740'}
METHOD_KINDS = [1,2,3,4,6,7,8]
require = native.require
MAX_BYTES = original_decoder.MAX_BYTES
SENTINEL = native.SENTINEL.replace('testPIN39NativeOnlyExportCapability','testPIN42OwnedLeafExportCapability').replace('PIN39Configure','PIN42Configure')
CONFIGURE = native.CONFIGURE.replace('PIN39Configure','PIN42Configure').replace('PARLOR_PIN39_','PARLOR_PIN42_').replace(native.PROFILE,PROFILE)


def renderer_for_profile(profile,source,runtime,device):
    require((profile,source,runtime,device)==(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),'Exact owned-leaf capability identity')
    def render(sources,digests,*,arm):
        require(arm=='compose' and set(sources)==set(digests)==set(pair.MODIFIED),'Only exact original seven inputs')
        require(digests==pair.INPUTS and all(hashlib.sha256(v.encode()).hexdigest()==digests[k] for k,v in sources.items()),
                'Every original input byte required')
        templates={}
        for name,digest in PINS.items():
            raw=frozen_sources._read(HERE/name,frozen_sources.MAX_SOURCE_BYTES)
            require(hashlib.sha256(raw).hexdigest()==digest,'Exact reviewed owned-leaf source: '+name)
            templates[name]=raw.decode()
        container=pin01_copy.once(sources[pin02_capture.CONTAINER],
            '    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n#if DEBUG && targetEnvironment(simulator)\n'
            '    private var pin42Input: PIN42OwnedLeafObservation?\n#endif\n')
        container=pin01_copy.once(container,'        contentController.didMove(toParent: self)\n',
            '        contentController.didMove(toParent: self)\n#if DEBUG && targetEnvironment(simulator)\n'
            '        pin42Input = PIN42OwnedLeafObservation()\n#endif\n')
        container=pin01_copy.once(container,'    override var childForStatusBarStyle:',
            '#if DEBUG && targetEnvironment(simulator)\n'
            '    override func viewDidAppear(_ animated: Bool) {\n'
            '        super.viewDidAppear(animated)\n'
            '        pin42Input?.bindOriginalInputOnce(view: contentController.view!)\n'
            '    }\n#endif\n\n    override var childForStatusBarStyle:')
        container+='\n'+'\n'.join(templates[name] for name in ('PIN02Capture.swift.in',TOUCH,OWNER))
        driver=pin01_copy.once(sources[pin02_capture.DRIVER],'    private struct KeyboardCopy {',
            SENTINEL+'\n    private struct KeyboardCopy {')
        driver+='\n'+templates['PIN02FailureRequest.swift.in']+CONFIGURE
        return sources|{pin02_capture.CONTAINER:container,pin02_capture.DRIVER:driver}
    return render


def test_command(base,result):
    return [*(a for a in base if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
        '-only-testing:iosAppUITests/'+CASE.removesuffix('()')]


def verify_results(summary,tests,simulator,code):
    # Reuse the STRICT original unexpected assertion/device/duration checks,
    # mapping only this separately named isolated method, never its outcome.
    mapped_summary=json.loads(json.dumps(summary))
    mapped_tests=json.loads(json.dumps(tests))
    require(len(mapped_summary.get('testFailures',[]))==1 and
        mapped_summary['testFailures'][0].get('testIdentifierString')==CASE,'Exact new actual sentinel failure')
    def map_nodes(nodes):
        for n in nodes:
            if n.get('nodeType')=='Test Case':
                require(n.get('nodeIdentifier')==CASE,'No replacement method')
                n['nodeIdentifier']=native.CASE
            map_nodes(n.get('children',[]))
    map_nodes(mapped_tests.get('testNodes',[]))
    mapped_summary['testFailures'][0]['testIdentifierString']=native.CASE
    require(native.verify_results(mapped_summary,mapped_tests,simulator,code)=={native.CASE:'Failed'},'Strict isolated assertion')
    return {CASE:'Failed'}


def failed_method_exports(result,destination,outcomes):
    require(outcomes=={CASE:'Failed'},'Only actual new sentinel failure exports')
    return [(destination/'failed-01',['export','attachments','--path',str(result),'--output-path',str(destination/'failed-01'),'--test-id',CASE])]


def verify_commands(state,row):
    mapped=json.loads(json.dumps(state))
    calls=[c for c in mapped['commands'] if c.get('label')=='compose-tests']
    require(len(calls)==1 and [a for a in calls[0]['command'] if a.startswith('-only-testing:')]==
        ['-only-testing:iosAppUITests/'+CASE.removesuffix('()')],'Only actual owned-leaf sentinel command')
    calls[0]['command']=[a.replace(CASE.removesuffix('()'),native.CASE.removesuffix('()')) for a in calls[0]['command']]
    native.verify_commands(mapped,row)


def decode_touch(raw,**args):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES,'Bounded owned-leaf raw evidence')
    v=json.loads(raw,object_pairs_hook=original_decoder.unique,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite owned-leaf metadata')))
    require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs','installed',
        'installation','overflow','rows','observedMethodKinds','inheritedStateSetterUntouched'},'Exact owned-leaf schema; no private input')
    require(type(v['schema']) is int and v['schema']==2 and type(v['observedMethodKinds']) is list and
        all(type(n) is int for n in v['observedMethodKinds']) and v['observedMethodKinds']==METHOD_KINDS and
        v['inheritedStateSetterUntouched'] is True,'Explicit seven owned overrides/inherited setter untouched required')
    require(type(v['rows']) is list and all(type(r) is list and len(r)==5 and r[1]!=5 for r in v['rows']),
        'No setter observation or invented state row')
    # Shared numeric owner/ABI/ordering/stack/overflow checks do not accept schema2
    # directly; other existing consumers still reject this narrower evidence.
    legacy={k:value for k,value in v.items() if k not in ('observedMethodKinds','inheritedStateSetterUntouched')}
    legacy['schema']=1
    report=original_decoder.decode(json.dumps(legacy).encode(),**args)
    report.update(sha256=hashlib.sha256(raw).hexdigest(),observed_method_kinds=METHOD_KINDS,
        inherited_state_setter_untouched=True)
    return report


def decode_companions(destination,token,simulator,bundle,legacy):
    args=dict(token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    raw=frozen_sources._read(destination/('native-touch-'+token+'.json'),MAX_BYTES)
    report=decode_touch(raw,**args)
    require(report['complete'],'Actual complete original owned-leaf installation/no overflow')
    rows=json.loads(raw)['rows']
    require(sum(r[1:3]==[1,0] and r[4]==1 for r in rows)>=2 and
        sum(r[1:3]==[3,0] and r[4]==1 for r in rows)>=2,'Actual original catalog and Host delivery required')
    status=prior.decode_status(frozen_sources._read(destination/('native-only-'+token+'.json'),native.MAX_BYTES),**args)
    return dict(touches=report,status=status)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    missing=[]
    for prefix,filename,retained,maximum in (('parlor-pin42-','status.json','native-only-',native.MAX_BYTES),
        ('parlor-pin42-touch-','touches.json','native-touch-',MAX_BYTES)):
        path=sandbox/'tmp'/(prefix+token)/filename
        if not path.exists() and not path.is_symlink():missing.append(retained);continue
        require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Exact owned-leaf companion')
        with (destination/(retained+token+'.json')).open('xb') as f:f.write(run_pin03.read_capture(path,maximum=maximum))
    require(not missing,'Missing owned-leaf files after retaining available evidence: '+','.join(missing))
    return decode_companions(destination,token,simulator,bundle,legacy)


def accept_native_export(out, state):
    # Separate narrower evidence must never substitute for eight-method/native39 evidence.
    require(not any((out/'compose').glob('native-accessibility-*.json')) and
        not any((out/'compose').glob('native-frame-*.json')) and
        not any((out/'compose').glob('native-root-*.json')), 'No invented absent getter exports')
    require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device']) ==
        (PROFILE,SOURCE,TREE,pair.RUNTIME,pair.DEVICE), 'Exact native-only source identity')
    require(state['debug_bundle'] == 'me.parlor.ios.debug' and state['xcode'] == 'Xcode 26.3\nBuild version 17C529\n' and
        state['sdk'] == '26.2' and len(state['arms']) == 1, 'Actual native compiler/Debug product/single arm')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm'] == 'compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
        row['result'] == 'JOURNEYS_FAILED', 'Actual native build/failure/cleanup')
    load = lambda p: json.loads(frozen_sources._read(p, 2_097_152), object_pairs_hook=pin01_receipts.reject_duplicates)
    require(verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit']) == row['methods'] and
        row['attachment_test_ids'] == [CASE], 'Actual isolated failed assertion and attachments')
    require(all(state['original_inputs'].get(p) == h for p,h in pair.INPUTS.items()), 'All original binding bytes')
    plan = renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE)(
        {p:pair.fixture(p) for p in pair.MODIFIED},pair.INPUTS,arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
        'Only reviewed native-only source plan')
    product = frozen_sources._read(d/'product.json', 2_097_152)
    require(json.loads(product) and hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'], 'Actual product custody')
    requests = []
    for p in (d/'attachments').rglob('*'):
        if p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4096:
            try: v = load(p)
            except (ValueError, UnicodeError): continue
            if type(v) is dict and 'originalTransitionFailed' in v: requests.append(v)
    require(len(requests) == 1 and set(requests[0]) == {'schema','token','ackObserved','originalTransitionFailed'} and
        type(requests[0]['schema']) is int and requests[0]['schema'] == 1 and requests[0]['ackObserved'] is True and
        requests[0]['originalTransitionFailed'] is True, 'One actual post-assertion acknowledged request')
    token = requests[0]['token']
    require(type(token) is str and pin02_capture.UUID.fullmatch(token), 'Exact failed-only token')
    raw = frozen_sources._read(d/('capture-'+token+'.json'), pin02_capture.MAX_BYTES)
    legacy = json.loads(raw)
    decoded = run_pin03.decode_capture(raw,{token},row['simulator'],state['debug_bundle'])
    require(decoded['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
        decoded['numeric_buffer_complete'] is False and row['captures'] == [decoded], 'ACK-only envelope, never invented numeric evidence')
    for prefix in ('capture-','native-touch-','native-only-'):
        require({p.name for p in d.glob(prefix+'*.json')} == {prefix+token+'.json'}, 'Exactly one companion per actual failure')
    report = decode_companions(d,token,row['simulator'],state['debug_bundle'],legacy)
    require(row['companion_captures'] == [report], 'Native evidence matches retained originals')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
        cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned cleanup')
    require(state['commands'] and all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals')
        for c in state['commands']), 'Every child exited and reaped')
    verify_commands(state,row)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(d/'test.log', 2_097_152))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Only new sentinel executed once')
    receipt = dict(methods=row['methods'], native=report, primary_envelope_purpose='durable-failure-ack-only',
        control_cause='Deliberately absent destination in isolated XCTest, not application suppression',
        application_root_cause=False,shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target = out/'owned-leaf-export.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved native-only capability receipt')
    else:
        with target.open('x') as f: json.dump(receipt,f,indent=2)
    return RESULT
