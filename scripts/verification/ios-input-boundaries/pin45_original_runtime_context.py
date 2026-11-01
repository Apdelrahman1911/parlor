"""Original full workload/process context on the original failed OS, not qualification."""
import hashlib
import json
import math

import frozen_sources
import pin44_original_runtime as runtime
import pin16_aggregate_context as aggregate

SOURCE, TREE = runtime.SOURCE, runtime.TREE
RUNTIME, DEVICE = runtime.RUNTIME, runtime.DEVICE
PROFILE = 'original-runtime-full-workload-context'
ARMS = ('compose',)
CASES = aggregate.combined.cohort.CASES
TESTS = tuple('iosAppUITests/'+c.removesuffix('()') for c in CASES)
TEST_TIMEOUT_SECONDS = 900
RESULT = 'ORIGINAL_RUNTIME_FULL_CONTEXT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original uninstrumented774-method KMP aggregate, four container methods and all23 native identifiers '
         'on original iOS26.2/iPhone17Pro; original6GiB/default-parallel single combined Xcode command; '
         'seven owned methods/inherited setter untouched; all failures retained, not causality or qualification')
require = runtime.require
load = runtime.load
retain_companion = runtime.retain_companion
decode_companions = runtime.decode_companions
build_environment = aggregate.build_environment
observe_resources = aggregate.observe_resources
execute_prelude = aggregate.execute_prelude
CONTEXT_RUN = 41332569567
CONTEXT_HARNESS = '271e0a175484b50eef4d9e053b5f505144dd42ee'
CONTEXT_RECEIPT_SHA256 = '4953c4f83ac4b4cd30e1be165dc2f6be4ee6bd3f48d91e7396498f312c6e4683'


def require_context_binding():
    runtime.require_pair_binding()
    require(CONTEXT_RUN == 41332569567 and CONTEXT_HARNESS == '271e0a175484b50eef4d9e053b5f505144dd42ee'
            and CONTEXT_RECEIPT_SHA256 == '4953c4f83ac4b4cd30e1be165dc2f6be4ee6bd3f48d91e7396498f312c6e4683',
            'Exact independently accepted original-runtime precursor required')


def renderer_for_profile(profile, source, os, device):
    require((profile,source,os,device)==(PROFILE,SOURCE,RUNTIME,DEVICE), 'Exact original full-context source/runtime')
    base=runtime.renderer_for_profile(runtime.PROFILE,source,os,device)
    def render(sources,digests,*,arm):
        plan=base(sources,digests,arm=arm)
        for p in runtime.pair_control.pin02_capture.MODIFIED:
            plan[p]=plan[p].replace('PIN44Configure','PIN45Configure').replace('PARLOR_PIN44_','PARLOR_PIN45_')
            plan[p]=runtime.pair_control.pin01_copy.once(plan[p],runtime.PROFILE,PROFILE)
        return plan
    return render


def test_command(base,result):
    return [*(a for a in base if a!='-quiet'),'test','-resultBundlePath',str(result),
            *('-only-testing:'+t for t in TESTS)]


def verify_results(summary,tests,simulator,code):
    require(type(code) is int and code in (0,65), 'Original actual pass or failed exit65')
    for key in ('totalTestCount','passedTests','failedTests','skippedTests','expectedFailures'):
        require(type(summary.get(key)) is int and 0<=summary[key]<=23,'Exact finite original full counts')
    require(summary['totalTestCount']==23 and summary['skippedTests']==summary['expectedFailures']==0
            and summary['passedTests']+summary['failedTests']==23, 'All23 original methods execute with no skips')
    configurations,devices=summary.get('devicesAndConfigurations',[]),tests.get('devices',[])
    require(len(configurations)==len(devices)==len(tests.get('testPlanConfigurations',[]))==1,'One owned configuration')
    for d in (configurations[0].get('device',{}),devices[0]):
        require((d.get('deviceId'),d.get('architecture'),d.get('platform'),d.get('modelName'),d.get('osVersion'))==
                (simulator,'arm64','iOS Simulator','iPhone 17 Pro','26.2'),'Actual original failed OS/model')
    def visit(nodes):
        return [case for n in nodes for case in ([n] if n.get('nodeType')=='Test Case' else visit(n.get('children',[])))]
    nodes=visit(tests.get('testNodes',[]))
    require(len(nodes)==23 and {n.get('nodeIdentifier') for n in nodes}==set(CASES)
            and all(n.get('result') in ('Passed','Failed') and type(n.get('durationInSeconds')) in (int,float)
                    and math.isfinite(n['durationInSeconds']) and 0<n['durationInSeconds']<3600 for n in nodes),
            'All original identifiers and actual positive finite durations')
    methods={n['nodeIdentifier']:n['result'] for n in nodes};passed=sum(v=='Passed' for v in methods.values())
    require(passed==summary['passedTests'] and (code==0)==(passed==23)
            and summary.get('result')==('Passed' if passed==23 else 'Failed'), 'All original outcomes agree')
    require(all(v=='Passed' for k,v in methods.items() if k not in runtime.CASES),
            'Other original failures cannot be waived or admitted without their capture')
    return methods


def failed_method_exports(result,destination,outcomes):
    require(set(outcomes)==set(CASES), 'All23 outcomes retained before scoped failed export')
    return runtime.failed_method_exports(result,destination,{k:outcomes[k] for k in runtime.CASES})


def verify_commands(state,row):
    aggregate.combined.verify_commands(state|{'profile':aggregate.combined.PROFILE},row)
    require(row.get('build_exit')==0,'Actual23 executed outcomes establish successful combined compilation')
    commands=state['commands'];selected=[c for c in commands if c.get('label')=='original-kmp-aggregate']
    stops=[c for c in commands if c.get('label')=='original-kmp-stop']
    redirected=['/bin/bash','-c','exec "$@" > "$PARLOR_INPUT_LOG" 2>&1','--',*aggregate.PRELUDE_COMMAND]
    require(len(selected)==len(stops)==1 and selected[0]['command']==redirected and selected[0]['exit_code']==0
            and selected[0]['timeout_seconds']==aggregate.PRELUDE_TIMEOUT and stops[0]['exit_code']==0,
            'Exactly one unchanged original aggregate invocation, no cache warmup replay')
    native=next(c for c in commands if c['label']=='compose-build-and-tests')
    require(commands.index(selected[0])<commands.index(stops[0])<commands.index(native)
            and len([c for c in commands if 'productionIosSimulatorRuntimeTests' in c['command']])==1,
            'Original aggregate and cleanup precede the one native build/test process')
    profile=state['build_profile']
    require(profile.get('combined_xcode') is True and profile.get('combined_timeout_seconds')==3600
            and profile.get('gradle_heap_bytes')==6*1024**3 and profile.get('gradle_workers')=='original-default'
            and profile.get('gradle_parallel') is True and profile.get('boot_after_build') is False
            and profile.get('xcode_jobs')=='original-default' and profile.get('product_manifest_stage')=='after-combined-command',
            'Original6GiB/default parallel, booted-build, single combined process; no resource override')
    require(set(row.get('kernel_resources',{}))==set(aggregate.combined.STAGES),'Both actual kernel boundaries')
    for stage in aggregate.combined.STAGES:
        raw=frozen_sources._read(state['_reader_out']/'compose'/('kernel-'+stage+'.json'),4096)
        require(hashlib.sha256(raw).hexdigest()==row['kernel_resources'][stage],'Kernel record byte custody')
        aggregate.combined.resources.validate_resources(json.loads(raw,object_pairs_hook=aggregate.pin01_receipts.reject_duplicates))


def accept_native_export(out,state):
    require_context_binding()
    runtime.accept_original_runtime_export(out,state|{'_reader_out':out},profile=PROFILE,renderer=renderer_for_profile,
        result_verifier=verify_results,command_verifier=verify_commands,cases=CASES,result=RESULT,
        receipt_name='original-runtime-context-native.json')
    row=state.get('aggregate_prelude',{})
    require(row.get('original_uninstrumented_inputs') is True and row.get('result')=='ORIGINAL774_EXECUTED_NOT_QUALIFICATION'
            and row.get('exit_code')==row.get('stop_exit')==0 and row.get('cleanup')=='PASS'
            and row.get('workers',{}).get('result')=='PASS','Original aggregate outcomes/owned worker cleanup')
    raws={c['path']:frozen_sources._read(out/'aggregate/xml'/c['path'],2*1024*1024) for c in aggregate.case_manifest()['classes']}
    require(aggregate.verify_prelude_xml(raws,state['original_inputs'])==row['reports']
            and row.get('retained_reports')=={p:hashlib.sha256(b).hexdigest() for p,b in raws.items()},'All149 reports/774 original methods')
    for stage in ('before','after'):
        raw=frozen_sources._read(out/'aggregate'/('simulators-'+stage+'.json'),65536)
        require(hashlib.sha256(raw).hexdigest()==row['simulators_'+stage],'Original Simulator inventory byte custody')
        aggregate.validate_simulator_observation(raw)
    receipt=runtime.load(out/'original-runtime-context-native.json')|dict(context_run=CONTEXT_RUN,
        context_harness=CONTEXT_HARNESS,context_receipt_sha256=CONTEXT_RECEIPT_SHA256,
        original_uninstrumented_kmp_methods=774,original_kmp_reports=149,original_native_methods=23,
        original_full_context_verified=True,original_aggregate_cleanup=row['cleanup'])
    target=out/'original-runtime-context.json'
    if target.exists():require(load(target)==receipt,'Exact original full-context saved receipt')
    else:
        with target.open('x') as f:json.dump(receipt,f,indent=2)
    return RESULT
