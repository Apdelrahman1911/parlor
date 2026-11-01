"""Original full-suite context with the already-rendered native-touch observer.

PIN33's ten sampled wrappers omit the original four-case XCTest prelude and
use separate serial linkage. Restore the original23 identifiers/order and one
original combined action, with the ENTIRE PIN33 app/driver plan unchanged.
No KMP prelude, successful export sentinel or adaptive samples are replayed.
"""
import hashlib
import json
import math
import re

import frozen_sources
import pin02_capture
import pin13_original_cohort as cohort
import pin15_combined_xcode as combined
import pin33_original_touch_delivery as delivery

SOURCE, TREE, ARMS = delivery.SOURCE, delivery.TREE, delivery.ARMS
PROFILE = 'original-touch-full-cohort-context'
CASES = cohort.CASES
RESULT = 'ORIGINAL_TOUCH_FULL_COHORT_CONTEXT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original23 identifiers/order and combined Xcode process with byte-identical PIN33 '
         'source/driver/observer; original6GiB/default workers, no KMP prelude/sentinel/sample '
         'replay, retries, repair, changed assertion deadlines, causality or qualification')
pair = delivery.previous.previous.observed.original.motion.pair
original = delivery.previous.previous.observed.original
require = delivery.decoder.require
build_environment = combined.build_environment
observe_resources = combined.observe_resources
STAGES = combined.STAGES
test_command = combined.test_command
failed_method_exports = cohort.failed_method_exports
retain_companion = delivery.retain_companion
retain_root_companion = delivery.retain_root_companion
retain_motion_companion = delivery.retain_motion_companion


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device)==(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
            'Exact original full-cohort touch source/profile/runtime/device')
    return delivery.renderer_for_profile(delivery.PROFILE, source, runtime, device)


def verify_results(summary, tests, simulator, code):
    methods = cohort.verify_results(summary, tests, simulator, code)
    def visit(nodes):
        return [case for node in nodes for case in
                ([node] if node.get('nodeType')=='Test Case' else visit(node.get('children',[])))]
    require(len(tests.get('testPlanConfigurations',[]))==1 and
        all(type(n.get('durationInSeconds')) in (int,float) and
            math.isfinite(n['durationInSeconds']) and 0<n['durationInSeconds']<3600
            for n in visit(tests['testNodes'])), 'Actual single configuration and positive finite case durations')
    return methods


def verify_commands(state, row):
    require(state['profile']==PROFILE and row['arm']=='compose', 'Exact original-touch cohort only')
    commands=state['commands']
    require(not any(c.get('label') in ('compose-build','compose-tests') for c in commands),
            'One combined action, no separate test/build replay')
    selected=[c for c in commands if c.get('label')=='compose-build-and-tests']
    require(len(selected)==1 and selected[0].get('exit_code')==row['test_exit']==row['combined_exit'] and
        selected[0].get('timeout_seconds')==3600, 'Original summed build/test budget and actual exit required')
    argv=selected[0]['command']
    require(argv.count('/usr/bin/xcodebuild')==argv.count('test')==1 and
        not any(a in argv for a in ('build-for-testing','test-without-building','-jobs','-quiet')) and
        not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests','-run-tests-until-failure')) for a in argv),
        'Original single native action, no retries/skips/serial resource changes')
    require(tuple(a.removeprefix('-only-testing:iosAppUITests/')+'()' for a in argv
        if a.startswith('-only-testing:'))==CASES, 'All23 exact original identifiers/order; no sentinel or wrappers')
    for flag,value in (('-configuration','Debug'),('-scheme','iosApp'),('-sdk','iphonesimulator'),
        ('-destination','platform=iOS Simulator,id='+row['simulator']),('-parallel-testing-enabled','NO'),
        ('-maximum-concurrent-test-simulator-destinations','1'),('-destination-timeout','120')):
        require(argv.count(flag)==1 and argv[argv.index(flag)+1]==value, 'Original owned native arguments')
    stops=[c for c in commands if c.get('label')=='compose-gradle-stop']
    require(len(stops)==1 and stops[0].get('exit_code')==0 and stops[0].get('timeout_seconds')==120,
            'One actual owned cleanup command')
    require(row.get('product_manifest_stage')=='after-combined-command' and
        row.get('build_success_evidence')=='all23-executed-native-outcomes', 'Disclosed actual combined build evidence')


def accept_native_export(out, state):
    require(state['original_inputs'].get(original.text.NAME)==original.text.NAME_SHA256,
            'Exact original Name implementation, not a substituted input')
    original.motion.boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE),
        result_verifier=verify_results,command_verifier=verify_commands)
    original.verify_companion_files(out,state)
    delivery.previous.previous.observed.verify_failed_queries(out,state)
    profile=state['build_profile']
    require(profile.get('combined_xcode') is True and profile.get('combined_timeout_seconds')==3600 and
        profile.get('gradle_heap_bytes')==6*1024**3 and profile.get('gradle_workers')=='original-default' and
        profile.get('gradle_parallel') is True and profile.get('boot_after_build') is False and
        profile.get('xcode_jobs')=='original-default' and
        profile.get('product_manifest_stage')=='after-combined-command', 'Exact restored original process/resource context')
    row=state['arms'][0];dest=out/'compose'
    require(set(row.get('kernel_resources',{}))==set(STAGES), 'Two actual existing kernel boundaries')
    for stage in STAGES:
        raw=frozen_sources._read(dest/('kernel-'+stage+'.json'),4096)
        require(hashlib.sha256(raw).hexdigest()==row['kernel_resources'][stage], 'Actual bounded kernel custody')
        combined.resources.validate_resources(json.loads(raw,object_pairs_hook=combined.pin01_receipts.reject_duplicates))
    starts=re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(dest/'test.log',4*1024*1024))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts)==CASES, 'Original23 cases actually execute once in order')
    getters,touches=[],[]
    for path in sorted(dest.glob('capture-*.json')):
        legacy=json.loads(frozen_sources._read(path,pin02_capture.MAX_BYTES));token=legacy['token']
        require(not any(r[1] in (1,2) for r in legacy['snapshot']['rows']), 'No added pointer observer')
        args=dict(token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        getters.append(delivery.previous.decode_overscroll(frozen_sources._read(
            dest/('catalog-overscroll-'+token+'.json'),delivery.previous.MAX_BYTES),**args))
        touch=delivery.decoder.decode(frozen_sources._read(dest/('native-touch-'+token+'.json'),
            delivery.decoder.MAX_BYTES),**args)
        require(touch['complete'], 'Actual unique original touch installation, exact ABI and zero overflow')
        touches.append(touch)
    for prefix in ('catalog-overscroll-','native-touch-'):
        require(len(list(dest.glob(prefix+'*.json')))==len(touches), 'Exactly one actual companion per failure, none on success')
    receipt=dict(methods=row['methods'],original_case_count=23,original_getters=getters,original_touches=touches,
        original_kmp_prelude=False,application_root_cause=False,shipping_qualified=False,
        native_timing_neutrality=False,physical_verified=False)
    target=out/'original-touch-cohort.json'
    if target.exists():require(json.loads(target.read_bytes())==receipt, 'Exact original saved receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
