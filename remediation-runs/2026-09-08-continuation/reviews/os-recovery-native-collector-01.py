#!/usr/bin/env python3
# OS-only custody collector: retained three capped raw XCResult ZIPs justify a
# 256MiB outer artifact ceiling. Existing archive expansion/member/CRC/path guards
# and exact source/attempt/job bindings remain unchanged; nested ZIPs are opaque.
import subprocess,pathlib,json,datetime,hashlib,importlib.util,sys
ROOT=pathlib.Path.cwd();N=ROOT/'remediation-runs/2026-09-08-continuation'
run_id=int(sys.argv[1]);source=sys.argv[2];P=N/'actions'/sys.argv[3];kind=sys.argv[4]
assert kind == 'native-evidence' and len(source)==40 and all(c in '0123456789abcdef' for c in source)
assert P.is_dir() and P.resolve()==P and P.parent==N/'actions'
request=json.loads((P/'request.json').read_bytes())
assert request['repository']=='Apdelrahman1911/parlor' and request['branch']=='fix/local-readiness-2026-09-07'
assert request['frozen_source_sha']==source and request['inputs']['verification_scope']==kind and request['inputs']['native_selection']=='os-recovery-only'
helper=N/'reviews/windows-targeted-collector-01/collect_windows.py'
assert hashlib.sha256(helper.read_bytes()).hexdigest()=='87afd5bc709a5bf83a672e6acc43983783cb38b878beac00722233c6b93777d1'
spec=importlib.util.spec_from_file_location('reviewed_archive_helpers',helper);h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def new(path,v):
 with path.open('x') as f:json.dump(v,f,indent=2);f.write('\n')
outer={'kind':'NATIVE_WATCH_AND_BOUNDED_COLLECTION','run_id':run_id,'source':source,'scope':kind,'started_at':now()}
try:
 with (P/'watch.log').open('x') as f:q=subprocess.run(['gh','run','watch',str(run_id),'--repo',h.REPO,'--interval','30','--exit-status'],stdout=f,stderr=subprocess.STDOUT,timeout=15600)
 outer['watch_exit_code']=q.returncode
 dest=N/'actions'/str(run_id);dest.mkdir();r={'schema_version':1,'kind':'BOUNDED_ACTIONS_ARCHIVE_COLLECTION_NOT_RUNTIME_REVIEW','run_id':run_id,'run_attempt':1,'source_commit':source,'started_at':now(),'commands':[],'archives':[],'status':'COLLECTING','bounds':{'outer_artifact_bytes':256*h.MIB,'expanded_per_archive_bytes':256*h.MIB,'expanded_member_bytes':128*h.MIB,'members':50000,'nested_xcresult_zips_extracted':False},'archive_helper':{'path':str(helper.relative_to(ROOT)),'sha256':h.sha_file(helper),'functions':['archive_members','extract_selected']}}
 def download(endpoint,name,limit):
  path=dest/name;row={'command':['gh','api',endpoint],'at':now()};r['commands'].append(row)
  try:
   with path.open('xb') as f:q=subprocess.run(row['command'],stdout=f,stderr=subprocess.PIPE,timeout=180)
   row.update(exit_code=q.returncode,stderr=q.stderr.decode('utf-8','replace'))
   h.require(q.returncode==0 and path.stat().st_size<=limit,'failed-or-oversized-download')
  finally:
   row.update(finished_at=now(),file=name)
   if path.is_file():row.update(bytes=path.stat().st_size,sha256=h.sha_file(path))
  return path
 def meta(endpoint,name):return json.loads(download(endpoint,name,4*h.MIB).read_bytes())
 try:
  base=f'repos/{h.REPO}/actions';run=meta(f'{base}/runs/{run_id}','run.json');att=meta(f'{base}/runs/{run_id}/attempts/1','attempt.json');jobs=meta(f'{base}/runs/{run_id}/attempts/1/jobs?per_page=100','jobs.json');arts=meta(f'{base}/runs/{run_id}/artifacts?per_page=100','artifacts.json')
  for v in (run,att):h.require(v['id']==run_id and v['run_attempt']==1 and v['head_sha']==source and v['head_branch']==h.BRANCH and v['event']=='workflow_dispatch' and v['status']=='completed' and v['repository']['full_name']==h.REPO and v['path']=='.github/workflows/production-verification.yml','producer-identity')
  h.require(jobs['total_count']==len(jobs['jobs'])==5 and {j['name'] for j in jobs['jobs']}==set(h.JOB_NAMES.values()),'complete-job-set')
  for j in jobs['jobs']:h.require(j['run_id']==run_id and j['run_attempt']==1 and j['head_sha']==source and j['status']=='completed' and (j['conclusion']!='skipped' if j['name']==h.JOB_NAMES['ios'] else j['conclusion']=='skipped'),'job-source-scope')
  expected={f'{kind}-{run_id}-1':kind,f'native-cleanup-{run_id}-1':'native-cleanup'}
  h.require(arts['total_count']==len(arts['artifacts'])<=100 and len({a['name'] for a in arts['artifacts']})==len(arts['artifacts']),'artifact-page')
  r.update(run_conclusion=run['conclusion'],run_url=run['html_url'],missing_expected_artifacts=sorted(set(expected)-{a['name'] for a in arts['artifacts']}),unexpected_artifacts=[a['name'] for a in arts['artifacts'] if a['name'] not in expected])
  for a in arts['artifacts']:
   if a['name'] not in expected:continue
   h.require(not a['expired'] and a['workflow_run']['id']==run_id and a['workflow_run']['head_sha']==source and 0<a['size_in_bytes']<=256*h.MIB,'artifact-identity-size')
   new(dest/f"artifact-{a['id']}.json",a);label=expected[a['name']];path=download(f"{base}/artifacts/{a['id']}/zip",label+'.zip',256*h.MIB)
   h.require(path.stat().st_size==a['size_in_bytes'] and a['digest']=='sha256:'+h.sha_file(path),'artifact-digest')
   rows=h.archive_members(path,lambda name:('CURRENT_NATIVE_OUTPUT_CONTENT_UNREVIEWED',True))
   entry={'artifact_id':a['id'],'artifact_name':a['name'],'archive':path.name,'bytes':path.stat().st_size,'sha256':h.sha_file(path),'api_digest_matches':True,'crc_and_bounds_checked':True,'members':rows,'original_zip_retained':True};r['archives'].append(entry)
   h.extract_selected(path,dest/label,rows);entry['extraction_complete']=True
  if not r['missing_expected_artifacts']:
   continuation=json.loads((dest/'native-evidence'/'continuation.json').read_bytes())
   h.require(continuation.get('selection')=='os-recovery-only','actual-native-selection')
   r['observed_native_selection']=continuation['selection']
  path=download(f'{base}/runs/{run_id}/attempts/1/logs','run-attempt-1-logs.zip',128*h.MIB);rows=h.archive_members(path,max_members=1500,max_file=64*h.MIB)
  r['log_archive']={'path':path.name,'sha256':h.sha_file(path),'bytes':path.stat().st_size,'members':rows,'crc_and_bounds_checked':True,'extracted':False,'original_zip_retained':True}
  r['status']='COLLECTED_NOT_RUNTIME_REVIEW' if not r['missing_expected_artifacts'] and not r['unexpected_artifacts'] else 'PARTIAL_COLLECTION_NOT_RUNTIME_REVIEW'
 except BaseException as e:r.update(status='COLLECTION_FAILED',error_type=type(e).__name__,error=str(e));raise
 finally:r['finished_at']=now();new(dest/'download-receipt.json',r)
 outer['collection_status']=r['status'];outer['download_receipt_sha256']=h.sha_file(dest/'download-receipt.json')
except BaseException as e:outer.update(error_type=type(e).__name__,error=str(e));raise
finally:
 try:
  with (P/'post-collection-stop.log').open('x') as f:q=subprocess.run(['./gradlew','--stop'],stdout=f,stderr=subprocess.STDOUT,timeout=120)
  outer['stop_exit_code']=q.returncode
 except BaseException as e:
  outer.update(stop_error_type=type(e).__name__,stop_error=str(e));raise
 finally:
  outer.update(finished_at=now(),local_cleanup='Synchronous subprocess.run watch/GET/stop children; no separate process-census claim after interruption. No local build/fixture/output/cache or persistent workers intentionally created. Required archives/reports retained; unrelated/global caches untouched.')
  new(P/'watcher-collection-receipt.json',outer);print(json.dumps(outer,indent=2))
