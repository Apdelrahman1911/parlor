"""Observe only original EN/AR journeys with the capability-tested public-scroll boundary.

No isolated sentinel, successful full23 cohort or sampled-wrapper replay. Missing
natural failures are execution evidence only, not a shipping fix or causal proof.
"""
import hashlib
import json
import math
import re

import frozen_sources
import pin02_capture
import pin36_public_scroll_boundary as public
import run_pin03

SOURCE, TREE, ARMS, pair = public.SOURCE, public.TREE, public.ARMS, public.pair
PROFILE = 'original-public-scroll-boundaries'
CASES = tuple(t.removeprefix('iosAppUITests/') + '()' for t in run_pin03.TESTS)
RESULT = 'ORIGINAL_PUBLIC_SCROLL_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original EN/AR methods once with actual public ScrollState/getter observation; '
         'no successful full-cohort/sentinel/sampled-wrapper replay, retries, new gestures, '
         'queries, polling, assertion deadline change, input repair, causality or qualification')
require = public.require
original = public.previous.original


def renderer_for_profile(profile, source, runtime, device):
    require((profile,source,runtime,device) == (PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
        'Exact original natural public-scroll source/profile/device')
    base = public.previous.renderer_for_profile(public.previous.PROFILE,source,runtime,device)
    return lambda sources, digests, *, arm: public.extend(base(sources,digests,arm=arm))


def test_command(base,result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
        *('-only-testing:'+t for t in run_pin03.TESTS)]


def verify_results(summary,tests,simulator,code):
    methods = original.verify_results(summary,tests,simulator,code)
    def visit(nodes):
        return [case for node in nodes for case in
            ([node] if node.get('nodeType') == 'Test Case' else visit(node.get('children',[])))]
    require(all(type(n.get('durationInSeconds')) in (int,float) and math.isfinite(n['durationInSeconds']) and
        0 < n['durationInSeconds'] < 900 for n in visit(tests['testNodes'])),
        'Positive finite original natural-case durations')
    return methods


def verify_commands(state,row):
    for suffix,code,deadline in (('build',0,2700),('tests',row['test_exit'],900),('gradle-stop',0,120)):
        commands = [c for c in state['commands'] if c.get('label') == 'compose-'+suffix]
        require(len(commands) == 1 and commands[0].get('exit_code') == code and
            commands[0].get('timeout_seconds') == deadline, 'One original bounded build/test/cleanup command')
        if suffix == 'tests':
            argv=commands[0]['command']
            require([a for a in argv if a.startswith('-only-testing:')] ==
                ['-only-testing:'+t for t in run_pin03.TESTS] and argv.count('test-without-building') == 1 and
                '-quiet' not in argv and not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests')) for a in argv),
                'Only original EN/AR once; no sentinel, successful cohort or adaptive wrappers')


failed_method_exports = original.failed_method_exports
retain_companion = public.retain_companion
retain_root_companion = public.retain_root_companion
retain_motion_companion = public.retain_motion_companion


def accept_native_export(out,state):
    require(state['original_inputs'].get(public.PERMISSION) == public.PERMISSION_SHA256,
        'Exact entire original public Permission source')
    original.motion.boundary.accept_observation_export(out,state,profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
        result_verifier=verify_results,command_verifier=verify_commands)
    original.verify_original_companions(out,state)
    public.previous.delivery.previous.previous.observed.verify_failed_queries(out,state)
    d=out/'compose'; observations=[]
    for path in sorted(d.glob('capture-*.json')):
        legacy=json.loads(frozen_sources._read(path,pin02_capture.MAX_BYTES));token=legacy['token']
        require(not any(r[1] in (1,2) for r in legacy['snapshot']['rows']), 'No added pointer observer')
        args=dict(token=token,simulator=state['arms'][0]['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        getters=public.previous.delivery.previous.decode_overscroll(frozen_sources._read(
            d/('catalog-overscroll-'+token+'.json'),public.previous.delivery.previous.MAX_BYTES),**args)
        touch=public.previous.delivery.decoder.decode(frozen_sources._read(
            d/('native-touch-'+token+'.json'),public.previous.delivery.decoder.MAX_BYTES),**args)
        require(touch['complete'], 'Original recognizer exact ABI and complete installation')
        raw=frozen_sources._read(d/('public-scroll-'+token+'.json'),public.MAX_BYTES)
        scroll=public.decode(raw,**args)
        # A failure before Setup may legitimately have zero public-scroll rows.
        # Preserve this absence; never claim unentered Permission/Name execution.
        rows=json.loads(raw)['scroll']['rows']
        mounted=[any(r[2:]==[k,2,1,0] for r in rows) for k in range(3)]
        require(all(n==0 or mounted[k] for k,n in enumerate(scroll['created'])),
            'Every observed original public effect requires actual public scope mounting')
        observations.append(dict(token=token,scroll=scroll,mounted=mounted,getters=getters,touch=touch,
            application_root_cause=False,shipping_qualified=False))
    for prefix in ('public-scroll-','catalog-overscroll-','native-touch-'):
        require(len(list(d.glob(prefix+'*.json'))) == len(observations), 'One complete companion per natural failure only')
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(d/'test.log',4*1024*1024))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'Original two methods once in original order')
    receipt=dict(methods=state['arms'][0]['methods'],observations=observations,application_root_cause=False,
        shipping_qualified=False,native_timing_neutrality=False,physical_verified=False)
    target=out/'public-scroll-observations.json'
    if target.exists():require(json.loads(target.read_bytes())==receipt,'Exact saved original observation receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
