"""Same-runner original/seven-owned-leaf comparison; never application qualification.

PIN30's separate runners confounded observer comparison. This changed adapter
needs its own original control, not an unchanged eight-method successful replay.
"""
import hashlib
import json
import re

import frozen_sources
import pin01_copy
import pin02_capture
import pin30_public_session_stress as baseline
import pin40_native_only_samples as samples
import pin42_owned_leaf_export as leaf

SOURCE,TREE=leaf.SOURCE,leaf.TREE
PROFILE='fixed-original-owned-leaf-pair'
ARMS=('original','compose')
CASES,TESTS=baseline.CASES,baseline.TESTS
TEST_TIMEOUT_SECONDS=baseline.TEST_TIMEOUT_SECONDS
RESULT='PAIRED_ORIGINAL_OWNED_LEAF_SAMPLES_COMPLETE_NOT_QUALIFICATION'
SCOPE=('Same free runner, original then seven-owned-leaf observer, each five fixed AR/EN pairs; '
       'all original actions/assertions/10s budgets and failures retained; inherited setter untouched; '
       'no retries, natural cause, timing benchmark, neutrality or shipping qualification')
require=leaf.require
# Bind the independently accepted capability before publishing/executing this profile.
CAPABILITY_RUN=41310938343
CAPABILITY_HARNESS='46a1e1c35005ec731b966f1eff494cd3d8bae53f'
CAPABILITY_RECEIPT_SHA256='1c20d4adfa7baf426c479d5a4848e136709a7159921f970b7fad9176e5a3c676'


def require_capability_binding():
    require(CAPABILITY_RUN==41310938343 and CAPABILITY_HARNESS=='46a1e1c35005ec731b966f1eff494cd3d8bae53f' and
        CAPABILITY_RECEIPT_SHA256=='1c20d4adfa7baf426c479d5a4848e136709a7159921f970b7fad9176e5a3c676',
        'Actual independently accepted owned-leaf capability binding is required before execution')


def renderer_for_profile(profile,source,runtime,device):
    require((profile,source,runtime,device)==(PROFILE,SOURCE,leaf.pair.RUNTIME,leaf.pair.DEVICE),'Exact original paired identity')
    owned=leaf.renderer_for_profile(leaf.PROFILE,source,runtime,device)
    def render(sources,digests,*,arm):
        require(arm in ARMS and set(sources)==set(digests)==set(leaf.pair.MODIFIED) and digests==leaf.pair.INPUTS and
            all(hashlib.sha256(v.encode()).hexdigest()==digests[k] for k,v in sources.items()),'Exact original paired inputs')
        if arm=='original':
            return sources|{pin02_capture.DRIVER:baseline.extend_driver(sources[pin02_capture.DRIVER])}
        plan=owned(sources,digests,arm='compose')
        driver=pin01_copy.once(plan[pin02_capture.DRIVER],leaf.SENTINEL+'\n','')
        driver=samples.extend_driver(driver).replace('PIN39Configure','PIN42Configure')
        driver=driver.replace('PIN42Configure','PIN43Configure').replace('PARLOR_PIN42_','PARLOR_PIN43_')
        driver=pin01_copy.once(driver,leaf.PROFILE,PROFILE)
        # Owner/recognizer bytes are otherwise exact; no observed state setter.
        container=plan[pin02_capture.CONTAINER].replace('PARLOR_PIN42_','PARLOR_PIN43_')
        container=pin01_copy.once(container,leaf.PROFILE,PROFILE)
        return plan|{pin02_capture.CONTAINER:container,pin02_capture.DRIVER:driver}
    return render


test_command=baseline.test_command
verify_results=baseline.verify_results
failed_method_exports=baseline.failed_method_exports
verify_commands=baseline.verify_commands
retain_baseline_failures=baseline.retain_baseline_failures


def decode_companions(destination,token,simulator,bundle,legacy):
    args=dict(token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    touch=leaf.decode_touch(frozen_sources._read(destination/('native-touch-'+token+'.json'),leaf.MAX_BYTES),**args)
    # Pre-tap failures can have no calls ONLY if actual installation is complete.
    # The isolated leaf capability's catalog/Host positive-call gates stay exact.
    require(touch['complete'],'Complete actual owned-leaf installation even for a pre-tap failure')
    status=leaf.prior.decode_status(frozen_sources._read(destination/('native-only-'+token+'.json'),leaf.native.MAX_BYTES),**args)
    return dict(touches=touch,status=status)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    missing=[]
    for prefix,filename,retained,maximum in (('parlor-pin42-','status.json','native-only-',leaf.native.MAX_BYTES),
        ('parlor-pin42-touch-','touches.json','native-touch-',leaf.MAX_BYTES)):
        path=sandbox/'tmp'/(prefix+token)/filename
        if not path.exists() and not path.is_symlink():missing.append(retained);continue
        require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Exact paired owned-leaf companion')
        with (destination/(retained+token+'.json')).open('xb') as f:f.write(leaf.run_pin03.read_capture(path,maximum=maximum))
    require(not missing,'Missing paired companions after retaining every available file: '+','.join(missing))
    return decode_companions(destination,token,simulator,bundle,legacy)


def accept_baseline(out,state,row):
    d=out/'original';load=samples.load
    require(row['build_exit']==0 and row['cleanup']=='PASS' and row['result'] in ('JOURNEYS_PASSED','JOURNEYS_FAILED'),
        'Actual original baseline build/outcomes/cleanup')
    plan=renderer_for_profile(PROFILE,SOURCE,leaf.pair.RUNTIME,leaf.pair.DEVICE)(
        {p:leaf.pair.fixture(p) for p in leaf.pair.MODIFIED},leaf.pair.INPUTS,arm='original')
    require(load(d/'inputs.json')==state['original_inputs']|{p:hashlib.sha256(b.encode()).hexdigest() for p,b in plan.items()},
        'Original baseline source plus only pure existing wrappers')
    require(verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit'])==row['methods'],
        'All original baseline outcomes, not relabeled failures')
    product=frozen_sources._read(d/'product.json',2_097_152)
    require(load(d/'product.json') and hashlib.sha256(product).hexdigest()==row['product_manifest_sha256'],'Actual baseline product custody')
    require(not any(row.get(k) for k in ('captures','companion_captures','root_companion_captures','motion_companion_captures')) and
        not any(p for prefix in ('capture-','native-','public-text-','public-lifecycle-','input-attachment-','catalog-motion-') for p in d.glob(prefix+'*.json')),
        'No fabricated baseline ACK/native observations')
    failed=sorted(k for k,v in row['methods'].items() if v=='Failed')
    require(row.get('attachment_test_ids',[])==failed and
        {p.name for p in (d/'attachments').glob('failed-*')}=={'failed-%02d'%i for i in range(1,len(failed)+1)},
        'Every actual original failed-method attachment retained')
    cleanup=load(d/'cleanup.json')
    require(cleanup['gradle_exit']==0 and cleanup['workers_before']['result']==cleanup['workers_after']['result']==cleanup['simulator']['result']=='PASS'
        and cleanup['simulator']['udid']==row['simulator'],'Exact owned original cleanup')
    verify_commands(state,row)
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",frozen_sources._read(d/'test.log',4*1024*1024))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts)==CASES,'Every original baseline case once, in declared order')
    return dict(methods=row['methods'],actual_xctest_exit=row['test_exit'],native_observations='absent',cleanup=row['cleanup'])


def accept_native_export(out,state):
    require_capability_binding()
    require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device'],state['debug_bundle'],state['xcode'],state['sdk'])==
        (PROFILE,SOURCE,TREE,leaf.pair.RUNTIME,leaf.pair.DEVICE,'me.parlor.ios.debug','Xcode 26.3\nBuild version 17C529\n','26.2'),
        'Exact original paired source/platform/product')
    require(len(state['arms'])==2 and tuple(r['arm'] for r in state['arms'])==ARMS and
        len({r['simulator'] for r in state['arms']})==2,'Exactly two ordered arms with distinct fresh owned devices')
    require(all(state['original_inputs'].get(p)==h for p,h in leaf.pair.INPUTS.items()),'All original paired binding bytes')
    require(state['build_profile']['test_timeout_seconds']==TEST_TIMEOUT_SECONDS and state['commands'] and
        all(c['status']=='EXITED' and c['direct_child_reaped'] and not c.get('signals') for c in state['commands']),
        'Unchanged finite budget/every child exited and reaped')
    phases=[arm+'-'+stage for arm in ARMS for stage in ('build','tests','gradle-stop')]
    labels=[c['label'] for c in state['commands']]
    require(all(labels.count(p)==1 for p in phases) and [labels.index(p) for p in phases]==sorted(labels.index(p) for p in phases),
        'Original arm/build/tests/cleanup precedes the observed arm; no interleaving or repeated command')
    original=accept_baseline(out,state,state['arms'][0])
    require(not any((out/'compose').glob('native-accessibility-*.json')) and not any((out/'compose').glob('native-frame-*.json')) and
        not any((out/'compose').glob('native-root-*.json')),'No fabricated absent getter captures')
    # The common finite native reader keeps its original strict schema and full-
    # native caller policies; use this explicitly narrower decoder/plan only.
    samples._accept_sampled_export(out,state|{'arms':[state['arms'][1]]},profile=PROFILE,renderer=renderer_for_profile,
        decoder=decode_companions,prefixes=('capture-','native-touch-','native-only-'),result=RESULT,filename='owned-leaf-observed-samples.json')
    observed=samples.load(out/'owned-leaf-observed-samples.json')
    report=dict(original=original,observed=observed,arm_order=list(ARMS),same_runner=True,capability_run=CAPABILITY_RUN,
        capability_harness=CAPABILITY_HARNESS,capability_receipt_sha256=CAPABILITY_RECEIPT_SHA256,
        application_root_cause=False,shipping_qualified=False,native_timing_neutrality=False,physical_verified=False,
        interpretation='Recorded discordance or all-pass cannot establish observer causality, neutrality or application correction')
    target=out/'owned-leaf-pair.json'
    if target.exists():require(samples.load(target)==report,'Exact saved paired observations')
    else:
        with target.open('x') as f:json.dump(report,f,indent=2)
    return RESULT
