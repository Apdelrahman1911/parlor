"""Execute only the existing failed-export sentinel with the PIN33 product plan.

PIN33 passed its ten samples without any failure capture. This isolated control
must retain the original unexpected XCTest failure and real app-leaf evidence;
it does not replay those successes or establish the intermittent app cause.
"""
import hashlib
import json
import math
import re

import frozen_sources
import pin02_capture
import pin20_catalog_motion as motion
import pin33_original_touch_delivery as delivery
import run_pin03

SOURCE, TREE, ARMS = delivery.SOURCE, delivery.TREE, delivery.ARMS
PROFILE = 'isolated-original-native-touch-export-control'
CASES = (motion.CONTROL,)
TEST_TIMEOUT_SECONDS = 900
RESULT = 'ISOLATED_ORIGINAL_NATIVE_TOUCH_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only existing isolated Host-suppression sentinel, unchanged PIN33 source/driver/adapter; '
         'original unexpected failure/exit65 and real native export required; no successful '
         'journey replay, input repair, causality, neutrality, shipping or physical qualification')
original = delivery.previous.previous.observed.original
pair = original.motion.pair
require = delivery.decoder.require


def renderer_for_profile(profile, source, runtime, device):
    require((profile,source,runtime,device)==(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
            'Exact isolated original-touch source/profile/device required')
    return delivery.renderer_for_profile(delivery.PROFILE,source,runtime,device)


def test_command(base,result):
    return [*(a for a in base if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
            '-only-testing:iosAppUITests/'+motion.CONTROL.removesuffix('()')]


def verify_results(summary,tests,simulator,code):
    methods=motion.verify_motion_results(summary,tests,simulator,code,cases=CASES)
    def visit(nodes):
        return [case for node in nodes for case in
                ([node] if node.get('nodeType')=='Test Case' else visit(node.get('children',[])))]
    nodes=visit(tests['testNodes'])
    require(len(tests.get('testPlanConfigurations',[]))==1 and
        all(type(n.get('durationInSeconds')) in (int,float) and math.isfinite(n['durationInSeconds']) and
            0<n['durationInSeconds']<TEST_TIMEOUT_SECONDS for n in nodes),
        'Actual single configuration and positive finite control duration required')
    return methods


def failed_method_exports(result,destination,outcomes):
    require(outcomes=={motion.CONTROL:'Failed'},'Only the original unexpected sentinel failure may export')
    return motion.motion_method_exports(result,destination,outcomes,cases=CASES)


def verify_commands(state,row):
    for suffix,code,deadline in (('build',0,2700),('tests',65,900),('gradle-stop',0,120)):
        rows=[c for c in state['commands'] if c.get('label')=='compose-'+suffix]
        require(len(rows)==1 and rows[0].get('exit_code')==code and rows[0].get('timeout_seconds')==deadline,
                'One actual bounded build/control/cleanup command required')
        if suffix=='tests':
            argv=rows[0]['command']
            require([a for a in argv if a.startswith('-only-testing:')]==
                ['-only-testing:iosAppUITests/'+motion.CONTROL.removesuffix('()')] and
                argv.count('test-without-building')==1 and '-quiet' not in argv and
                not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests')) for a in argv),
                'Only existing isolated control once; no successful replay, skips or retries')


retain_companion = delivery.retain_companion
retain_root_companion = delivery.retain_root_companion
retain_motion_companion = delivery.retain_motion_companion


def accept_native_export(out,state):
    original.motion.boundary.accept_observation_export(out,state,profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
        result_verifier=verify_results,command_verifier=verify_commands)
    dest=out/'compose';captures=list(dest.glob('capture-*.json'))
    require(len(captures)==1,'Exactly one original unexpected control failure export required')
    raw=frozen_sources._read(captures[0],pin02_capture.MAX_BYTES)
    legacy=json.loads(raw);token=legacy['token'];rows=legacy['snapshot']['rows']
    require([r[2] for r in rows if r[1]==3]==[3,1] and
        set(r[3] for r in rows if r[1]==4)=={0,6} and not any(r[1] in (1,2) for r in rows),
        'Actual catalog and suppressed Host callbacks, public-only stages, no added pointer observer')
    original.verify_companion_files(out,state,isolated_touch_token=token)
    delivery.previous.previous.observed.verify_failed_queries(out,state)
    queries=[json.loads(p.read_bytes()) for p in (dest/'attachments').rglob('*.json')
             if p.is_file() and not p.is_symlink() and p.stat().st_size<=16384]
    queries=[v for v in queries if type(v) is dict and 'originalWaiterFailed' in v]
    require(len(queries)==1 and queries[0]['kind']==3,
            'Existing original Host-transition query, not another earlier failed predicate')
    args=dict(token=token,simulator=state['arms'][0]['simulator'],bundle=state['debug_bundle'],legacy=legacy)
    touch_raw=frozen_sources._read(dest/('native-touch-'+token+'.json'),delivery.decoder.MAX_BYTES)
    touch=delivery.decoder.decode(touch_raw,**args);native=json.loads(touch_raw)['rows']
    require(touch['complete'] and sum(r[1:3]==[1,0] and r[4]==1 for r in native)>=2 and
        sum(r[1:3]==[3,0] and r[4]==1 for r in native)>=2 and
        any(r[1]==5 and r[2]==0 and r[4]==1 for r in native),
        'Real unique app-leaf ABI installation and original primary began/ended/state calls required')
    getters=delivery.previous.decode_overscroll(frozen_sources._read(
        dest/('catalog-overscroll-'+token+'.json'),delivery.previous.MAX_BYTES),**args)
    require(getters['permission_callbacks']==0,'Suppressed Host must not reach Permission or Name')
    for prefix in ('native-touch-','catalog-overscroll-'):
        require(len(list(dest.glob(prefix+'*.json')))==1,'One new companion for this control failure only')
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(dest/'test.log',4*1024*1024))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts)==CASES,'Only existing sentinel actually executes once')
    receipt=dict(methods=state['arms'][0]['methods'],touches=touch,getters=getters,
        control_cause='Explicit existing copied-app Host suppression after callback',
        application_root_cause=False,shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target=out/'isolated-touch-export.json'
    if target.exists():require(json.loads(target.read_bytes())==receipt,'Exact saved isolated-control receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
