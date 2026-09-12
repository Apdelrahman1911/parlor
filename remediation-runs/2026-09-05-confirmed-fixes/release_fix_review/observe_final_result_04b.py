#!/usr/bin/env python3
"""Independent read-only reconciliation of the actual final observation.

Does not import or execute author recorder/evaluator; no builds, signals or deletion.
"""
import collections
import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import unquote

ROOT = Path('/Users/abdelrahman/Projects/parlor')
RUN = ROOT/'remediation-runs/2026-09-05-confirmed-fixes'
FINAL = RUN/'final'
EXPECTED = '1dc184c09e6b5ee74387844cb1fd7273acc327684a96e812f499c0e7af566119'
LEGACY = '1a1c0d0f2d68cf0f4be5b93033918fdae373c7ca1cd07c8686e680b3d77a5772'


def need(value, message):
    if not value:
        raise RuntimeError(message)


def safe(path):
    need(path.is_absolute() and '..' not in path.parts, 'Noncanonical path')
    path.relative_to(ROOT)
    need(not any(p.is_symlink() for p in (path,*path.parents)), 'Symlink ancestry')
    need(path.is_file(), 'Missing evidence file: ' + str(path))
    return path


def digest(path):
    return hashlib.sha256(safe(path).read_bytes()).hexdigest()


def load(path):
    return json.loads(safe(path).read_text())


def ref(path):
    return {'path':str(path.relative_to(ROOT)), 'sha256':digest(path)}


refs=[]


def bind_ref(row):
    rel=Path(row['path'])
    need(not rel.is_absolute() and '..' not in rel.parts, 'Unsafe reference')
    need(digest(ROOT/rel)==row['sha256'], 'Evidence drift: ' + str(rel))
    refs.append(str(rel))
    return ROOT/rel


def absent(path):
    return not path.exists() and not path.is_symlink()


def main():
    observation=load(FINAL/'final-observation.json')
    need(digest(FINAL/'final-observation.json')==EXPECTED,'Observation changed')
    note=FINAL/'FINALIZATION.md'
    need(digest(note)=='991056ced3962cc893b5b10f48622944a1db55699d1e641861814ba3d8e495bf','Finalization note changed')
    bind_ref(observation['baseline']);bind_ref(observation['recorder'])
    deliverables=[bind_ref(x) for x in observation['final_deliverables']]
    current_final={x for x in FINAL.iterdir() if x.is_file() or x.is_symlink()}
    need(len(deliverables)==len(set(deliverables))==16,'Deliverable duplicates/count drift')
    need(set(deliverables)==current_final-{FINAL/'final-observation.json'},'Final inventory drift')
    note_links=[]
    for markdown in (note,FINAL/'REPORT-CONTINUATION.md'):
        for target in re.findall(r'\[[^\]]+\]\(([^)\s]+)\)',markdown.read_text()):
            name,_,fragment=target.partition('#')
            path=(markdown.parent/unquote(name)).resolve(strict=True)
            safe(path)
            if fragment:
                headings=[]
                for line in path.read_text().splitlines():
                    if line.startswith('#'):
                        title=re.sub(r'[^\w\s-]','',line.lstrip('#').strip().lower())
                        headings.append(re.sub(r'\s+','-',title))
                need(fragment in headings,'Missing Markdown fragment: '+target)
            note_links.append({'from':str(markdown.relative_to(ROOT)),'target':target,'file':ref(path)})

    # Use this reviewer's earlier read-only source checker, not author controls.
    source_check=RUN/'release_fix_review/observe_continuation_binding_03.py'
    need(digest(source_check)=='6cc9c8f81dd838920b66143db1065af47a24bb2a58dd63cb4f3cafb4c01cce1e','Reviewer source checker changed')
    proc=subprocess.run(['/usr/bin/python3','-B',str(source_check)],cwd=ROOT,capture_output=True,text=True)
    need(proc.returncode==0 and not proc.stderr,'Fresh source/copy/path observation failed')
    current_source=json.loads(proc.stdout)
    need(current_source['pending_final_observation_exists'] is True,'Actual observation absent')
    for key in ('source_manifest_sha256','tracked_diff_sha256','baseline_files','unchanged_original_files'):
        need(current_source[key]==observation[key],'Source/preservation observation mismatch')
    need({x['path'] for x in observation['modified_original_files']}==set(current_source['modified_original_files']),'Modified baseline set drift')
    baseline=load(RUN/'baseline.json');frozen=load(FINAL/'source-identity.json')
    need(observation['new_source_files']==sorted(set(x[0] for x in frozen['source_manifest'])-{x['path'] for x in baseline['files']}),'New source set mismatch')
    for row in observation['modified_original_files']:bind_ref(row)
    need(observation['checks']=={key:True for key in ('head_unchanged','branch_unchanged','refs_unchanged','stash_unchanged','index_empty','all_original_files_present','all_preexisting_untracked_unchanged')},'Preservation check set drift')
    need(observation['missing_original_files']==observation['preexisting_untracked_changed']==[],'Lost original work')

    cycles=observation['cycles_including_post_materialization']
    receipts={p for p in (RUN/'evidence').glob('*/receipt.json')}
    need(len(cycles)==len(receipts)==49,'Cycle inventory count mismatch')
    need({ROOT/x['receipt']['path'] for x in cycles}==receipts,'Incomplete/duplicate cycle enumeration')
    independent=[]; stops=[]; owned_devices=[]; legacy=[]; child_receipts=[]
    for cycle in cycles:
        path=bind_ref(cycle['receipt']);raw=load(path)
        need(path.parent.name==cycle['cycle']==raw['cycle'],'Wrong cycle identity')
        need(all(raw.get(k)==v for k,v in cycle['recorded_fields'].items()),'Recorded field drift')
        need(raw['status'] in ('PASS','FAIL'),'Incomplete raw cycle')
        need(isinstance(raw.get('cleanup_completed_at'),str),'Missing cleanup time')
        need(raw.get('cleanup_errors')==[] and raw.get('remaining_outputs')==[],'Cleanup errors/survivors')
        need(datetime.datetime.fromisoformat(raw['cleanup_completed_at'])<=datetime.datetime.fromisoformat(observation['recorded_at']),'Cleanup happened after final observation')
        associated={x['kind']:x for x in cycle['associated']}
        need(len(associated)==len(cycle['associated']),'Duplicate associated evidence')
        for item in associated.values():bind_ref(item['reference'])
        if cycle['receipt']['sha256']==LEGACY:
            need(cycle['cycle']=='ios-b1-red' and raw['stop_exit_code']==0,'Legacy pin mismatch')
            need(raw.get('process_scan_exit_code')==1,'Legacy scan code mismatch')
            need((path.parent/'processes-after.txt').read_text()=='','Legacy scan output changed')
            need('workers' not in raw and 'retained_outputs' not in raw,'Legacy fields were silently added')
            legacy.append(cycle['cycle']);schema='legacy-shell-cycle'
            need(cycle['cleanup_evaluation']['historical_pid_evidence_complete'] is False and len(cycle['cleanup_evaluation']['limitations'])==1,'Legacy limit hidden')
        elif raw.get('execution_kind')=='source-original-kotlin-unsigned-ios-dsc01-apphost-matrix':
            schema='dsc01-apphost'
            need(raw['cleanup_status']=='PASS','Apphost cleanup incomplete')
            for key in ('owned_processes_remaining','unknown_holders','secondary_attestation_errors'):
                need(raw.get(key)==[],'Apphost cleanup missing/unresolved: '+key)
            need(raw['temporary_directory_removed'] is True and absent(Path(raw['owned_temporary_directory'])),'Apphost temp root remains')
            need(raw['owned_device_absent'] is True and raw['shutdown_exit_code']==raw['delete_exit_code']==0,'Apphost device cleanup failed')
            need(raw['secondary_cleanup']['status']=='PASS' and raw['secondary_cleanup']['remaining']==[],'Secondary cleanup unresolved')
            for name in raw['secondary_cleanup']['removed']:
                need(absent(Path(name)),'Task FIFO path still exists')
            need(raw['finalization_stages'] and all(x['status']=='PASS' for x in raw['finalization_stages']),'Apphost finalizer missing/failure')
            stopmap={x['label']:x for x in raw['gradle_stops']}
            need(set(stopmap)>={'stop-xcode-immediate','stop-final'} and all(type(x['exit_code']) is int and x['exit_code']==0 for x in stopmap.values()),'Apphost stop missing/failure')
            for label in ('stop-xcode-immediate','stop-final'):
                p=path.parent/(label+'.log');need(p.read_text()=='No Gradle daemons are running.\n','Unexpected stop log');stops.append(ref(p))
            p=path.parent/'embedded-gradle-stop.txt';need(p.read_text()=='build_exit=0\nstop_exit=0\n','Embedding stop/build failure');stops.append(ref(p))
            owned_devices.append({'cycle':cycle['cycle'],'uuid':raw['owned_uuid'],'source':'apphost receipt'})
        else:
            schema='gradle-cycle'
            need(type(raw.get('stop_exit_code')) is int and raw['stop_exit_code']==0,'Gradle stop absent/failure')
            need(raw.get('workers',{}).get('remaining_owned_workers')==[],'Worker cleanup absent/failure')
            need(raw.get('retained_outputs')==[],'Retained outputs unresolved')
        if schema!='dsc01-apphost':
            p=path.parent/'stop.log';need(p.read_text()=='No Gradle daemons are running.\n','Unexpected stop log');stops.append(ref(p))
        native=any('owned_ios_simulator.init.gradle' in str(x) for x in raw.get('command',[]))
        if native:
            need('owned-simulator' in associated,'Missing native device child receipt')
            childpath=ROOT/associated['owned-simulator']['reference']['path'];child=load(childpath)
            need(child==associated['owned-simulator']['record'],'Embedded native receipt not exact')
            need(child['cleanup_errors']==[] and child['owned_device_absent'] is True and child['preexisting_devices_preserved'] is True and child['remaining_owned_uuid_processes']==[],'Native cleanup field failure')
            need(len(child['commands'])==9 and all(x['exit_code']==0 for x in child['commands']),'Native command failure/missing')
            need((childpath.parent/'create.log').read_text().strip()==child['owned_uuid'],'Create result UUID mismatch')
            for name in ('shutdown.log','delete.log'):
                need((childpath.parent/name).read_text()=='','Unexpected owned device cleanup output')
            need(raw['runner_before']==raw['runner_after'],'Native controls changed during cycle')
            for name,dg in raw['runner_before']['manifest']:need(digest(ROOT/name)==dg,'Native control drift')
            owned_devices.append({'cycle':cycle['cycle'],'uuid':child['owned_uuid'],'source':'owned simulator child receipt'})
            child_receipts.append(ref(childpath))
        need(cycle['cleanup_evaluation']['schema']==schema and cycle['cleanup_evaluation']['status']=='PASS' and cycle['cleanup_evaluation']['problems']==[],'Evaluator result disagrees with raw facts')
        if schema!='legacy-shell-cycle':
            need(cycle['cleanup_evaluation']['historical_pid_evidence_complete'] is True and cycle['cleanup_evaluation']['limitations']==[],'Unexpected modern limitation')
        independent.append({'cycle':cycle['cycle'],'test_or_command_status':raw['status'],'cleanup_conclusion':'PASS_WITH_HISTORICAL_LIMIT' if schema=='legacy-shell-cycle' else 'PASS','schema':schema,'receipt':cycle['receipt']})
    need(legacy==['ios-b1-red'] and observation['historical_cleanup_evidence_complete'] is False and len(observation['historical_cleanup_limits'])==1,'Historical gap mismatch')
    for item in owned_devices:
        need(re.fullmatch(r'[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}',item['uuid']) is not None,'Invalid owned UUID')
        device=Path.home()/'Library/Developer/CoreSimulator/Devices'/item['uuid']
        need(absent(device),'Recorded task simulator still present')
        item['current_directory_absent']=True

    modules=[p.parent for p in ROOT.glob('shared/*/build.gradle.kts')]+[p.parent for p in ROOT.glob('game-modes/*/build.gradle.kts')]
    output_paths=[x/'build' for x in modules+[ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention',ROOT/'iosApp']]
    remaining=[str(p.relative_to(ROOT)) for p in output_paths if not absent(p)]
    scratch=[str(p.relative_to(ROOT)) for p in (RUN/'evidence').glob('*/scratch') if not absent(p)]
    need(remaining==scratch==observation['generated_outputs_remaining']==observation['cycle_scratch_remaining']==[],'Generated output/scratch survives')
    names=('org.gradle.launcher.daemon.bootstrap.GradleDaemon','GradleWorkerMain','org.jetbrains.kotlin.daemon.KotlinCompileDaemon')
    process_result=subprocess.run(['ps','-axo','pid=,lstart=,command='],capture_output=True,text=True)
    need(process_result.returncode==0 and not process_result.stderr,'Process query failed')
    workers=[];uuid_workers=[]
    for line in process_result.stdout.splitlines():
        parts=line.strip().split(None,6)
        if len(parts)!=7:continue
        pid=int(parts[0]);start=' '.join(parts[1:6]);command=parts[6]
        if 'java' in Path(command.split()[0]).name.lower():
            for token in names:
                if token in command:workers.append({'pid':pid,'start':start,'kind':token})
        for item in owned_devices:
            if item['uuid'] in command:uuid_workers.append({'pid':pid,'start':start,'cycle':item['cycle']})
    if workers or uuid_workers:
        print(json.dumps({'diagnostic_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'unclassified_build_workers':workers,'unclassified_uuid_references':uuid_workers}),flush=True)
    need(workers==uuid_workers==[],'Current build/native UUID worker needs classification; do not signal')
    need(observation['observed_gradle_kotlin_workers']==[] and observation['current_worker_classification']=='NONE_OBSERVED','Current worker observation mismatch')
    need(observation['observation_status']==observation['preservation_verdict']==observation['cleanup_verdict']=='PASS','Inconsistent final conclusion')
    result={'reviewer':'/root/release_fix_review','observed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Read-only independent actual-observation source/reference/cleanup reconciliation; no builds/tests/process cleanup',
            'observer':ref(Path(__file__).absolute()),'observation':ref(FINAL/'final-observation.json'),'finalization':ref(note),'source_reobservation':current_source,
            'reference_occurrences':len(refs),'distinct_reference_count':len(set(refs)),'final_deliverables_bound':len(deliverables),'markdown_links':note_links,
            'cycle_count':len(cycles),'command_result_counts':dict(collections.Counter(x['test_or_command_status'] for x in independent)),'cycle_dispositions':independent,
            'stop_evidence':stops,'native_child_receipts':child_receipts,'owned_devices':owned_devices,'generated_paths_checked':[str(p.relative_to(ROOT)) for p in output_paths],'remaining_outputs':remaining,'scratch_remaining':scratch,'process_query_exit':process_result.returncode,'build_workers':workers,'owned_uuid_workers':uuid_workers,'current_disk_free_bytes':shutil.disk_usage(ROOT).free,
            'historical_limit':'ios-b1-red lacks historical PID/start/command attestation. Its empty old scan is not converted into worker-absence evidence.',
            'native_inventory_limit':'Native-runner inventory logs are deliberately removed after parsing to avoid retaining unrelated personal simulator names (owned_ios_simulator.py49-59). Pre-existing-profile preservation is supported by executed pinned helper and compact receipt, not an independent reparse of deleted inventories. No user inventory was regenerated.',
            'reviewer_reader_diagnostic':'An exploratory metadata query expected removed inventory-before.log and exited1 after reading stop logs. Actual helper source explains intentional privacy deletion; no build/output was created and no success claimed for that query.'}
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    main()
