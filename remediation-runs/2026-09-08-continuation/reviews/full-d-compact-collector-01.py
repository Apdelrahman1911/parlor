
import subprocess,json,hashlib,stat,zipfile,io,sys,re
from pathlib import Path,PurePosixPath
from datetime import datetime,timezone
# One final full-verification collection; no dispatch/build/test/retry operation.
assert len(sys.argv)==3 and re.fullmatch(r"[1-9][0-9]*",sys.argv[1]) and re.fullmatch(r"[0-9a-f]{40}",sys.argv[2])
cfg=dict(run=int(sys.argv[1]),source=sys.argv[2])
EXPECTED_ARTIFACTS={name:name for name in (
 "desktop-android-verification",
 "desktop-verification-desktop-linux-arm64",
 "desktop-verification-desktop-macos-x64",
 "desktop-verification-desktop-windows-x64",
 "ios-verification",
 "verification-cleanup-desktop-android",
 "verification-cleanup-desktop-linux-arm64",
 "verification-cleanup-desktop-macos-x64",
 "verification-cleanup-desktop-windows-x64",
 "verification-cleanup-ios",
)}
repo='Apdelrahman1911/parlor'; branch='fix/local-readiness-2026-09-07'
run_id=cfg['run']; attempt=1; source=cfg['source']
dest=Path('remediation-runs/2026-09-08-continuation/actions')/str(run_id);dest.mkdir()
now=lambda:datetime.now(timezone.utc).isoformat()
sha=lambda raw:hashlib.sha256(raw).hexdigest()
receipt=dict(schema_version=1,kind='BOUNDED_ACTIONS_ARCHIVE_COLLECTION_NOT_RUNTIME_REVIEW',coordinator='/root',started_at=now(),run_id=run_id,run_attempt=attempt,source_commit=source,commands=[],archives=[],status='RUNNING')
def new_json(path,value):
 with path.open('x') as f:json.dump(value,f,indent=2);f.write('\n')
def download(endpoint,name,limit=8*1024*1024):
 args=['gh','api',endpoint];start=now();path=dest/name
 with path.open('xb') as out:
  proc=subprocess.run(args,stdout=out,stderr=subprocess.PIPE,timeout=180)
 row=dict(command=args,started_at=start,finished_at=now(),exit_code=proc.returncode,stdout_file=name,stderr=proc.stderr.decode('utf-8','replace'))
 receipt['commands'].append(row)
 assert proc.returncode==0 and path.stat().st_size<=limit,(name,proc.returncode,path.stat().st_size)
 row.update(bytes=path.stat().st_size,sha256=sha(path.read_bytes()))
 return path
def metadata(endpoint,name):
 p=download(endpoint,name,4*1024*1024)
 return json.loads(p.read_bytes())
def archive_members(path,output=None,max_members=1500,max_total=256*1024*1024,max_file=64*1024*1024):
 rows=[];names=set()
 with zipfile.ZipFile(path) as z:
  infos=z.infolist();assert len(infos)<=max_members
  assert sum(i.file_size for i in infos)<=max_total
  for i in infos:
   p=PurePosixPath(i.filename);mode=i.external_attr>>16
   assert i.filename and '\\' not in i.filename and '\x00' not in i.filename and not p.is_absolute() and all(s not in {'..','.'} and ':' not in s for s in p.parts)
   assert str(p)==i.filename.rstrip('/') and i.filename not in names and not i.flag_bits&1
   names.add(i.filename)
   assert not stat.S_ISLNK(mode) and stat.S_IFMT(mode) in {0,stat.S_IFREG,stat.S_IFDIR}
   if i.is_dir():
    assert i.file_size==0;continue
   assert 0<=i.file_size<=max_file
   raw=z.read(i);assert len(raw)==i.file_size
   rows.append(dict(path=i.filename,bytes=len(raw),sha256=sha(raw)))
   if output is not None:
    target=output/Path(*p.parts);target.parent.mkdir(parents=True,exist_ok=True)
    assert not target.exists() and target.parent.resolve()==target.parent.absolute()
    with target.open('xb') as f:f.write(raw)
  assert z.testzip() is None
 return rows
try:
 run=metadata(f'repos/{repo}/actions/runs/{run_id}','run.json')
 att=metadata(f'repos/{repo}/actions/runs/{run_id}/attempts/{attempt}','attempt.json')
 jobs=metadata(f'repos/{repo}/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100','jobs.json')
 arts=metadata(f'repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100','artifacts.json')
 for value in (run,att):
  assert value['id']==run_id and value['run_attempt']==attempt and value['head_sha']==source and value['head_branch']==branch
  assert value['event']=='workflow_dispatch' and value['status']=='completed' and value['repository']['full_name']==repo
  assert value['path']=='.github/workflows/production-verification.yml'
 if cfg.get('required_conclusion'):assert run['conclusion']==cfg['required_conclusion']==att['conclusion']
 assert jobs['total_count']==len(jobs['jobs'])==5
 assert all(v['run_id']==run_id and v['run_attempt']==attempt and v['head_sha']==source and v['status']=='completed' for v in jobs['jobs'])
 expected=EXPECTED_ARTIFACTS
 assert arts['total_count']==len(arts['artifacts'])<=100
 assert len({v['name'] for v in arts['artifacts']})==len(arts['artifacts'])
 receipt['missing_expected_artifacts']=sorted(set(expected)-{v['name'] for v in arts['artifacts']})
 receipt['unexpected_artifacts']=[dict(id=v['id'],name=v['name']) for v in arts['artifacts'] if v['name'] not in expected]
 # Failed or skipped jobs can lack artifacts: preserve all available approved
 # names and expose absence, rather than discarding other jobs' real evidence.
 assert sum(v['size_in_bytes'] for v in arts['artifacts'] if v['name'] in expected)<=512*1024*1024
 for art in arts['artifacts']:
  if art['name'] not in expected:continue
  assert not art['expired'] and art['size_in_bytes']<=64*1024*1024 and art['workflow_run']['id']==run_id and art['workflow_run']['head_sha']==source
  label=expected[art['name']];new_json(dest/f"artifact-{art['id']}.json",art)
  p=download(f"repos/{repo}/actions/artifacts/{art['id']}/zip",label+'.zip',64*1024*1024);digest=sha(p.read_bytes())
  assert p.stat().st_size==art['size_in_bytes'] and art['digest']=='sha256:'+digest
  rows=archive_members(p,dest/label,max_members=5000,max_total=256*1024*1024,max_file=128*1024*1024)
  if label=='native-preflight':
   assert {v['path'] for v in rows}=={'preflight.json','ios-readiness-source-ci.json','l08-controls.json','normal-controls.json'}
  if label=='native-cleanup':assert len(rows)==1
  receipt['archives'].append(dict(artifact_id=art['id'],artifact_name=art['name'],archive=p.name,bytes=p.stat().st_size,sha256=digest,api_digest_matches=True,members=rows,crc_and_bounds_checked=True))
 p=download(f'repos/{repo}/actions/runs/{run_id}/attempts/{attempt}/logs','run-attempt-1-logs.zip',128*1024*1024)
 rows=archive_members(p)
 receipt.update(log_archive=dict(path=p.name,sha256=sha(p.read_bytes()),bytes=p.stat().st_size,members=rows,crc_and_bounds_checked=True,extracted=False,scope='Compact original ZIP retained; underlying per-step logs remain readable directly from this archive.'),
   status=('PARTIAL_COLLECTION_NOT_RUNTIME_REVIEW' if receipt['missing_expected_artifacts'] or receipt['unexpected_artifacts'] else 'COLLECTED_NOT_RUNTIME_REVIEW'),run_conclusion=run['conclusion'],run_url=run['html_url'],jobs=[dict(id=v['id'],name=v['name'],conclusion=v['conclusion']) for v in jobs['jobs']])
except BaseException as error:
 receipt.update(status='COLLECTION_FAILED',error_type=type(error).__name__)
 raise
finally:
 receipt['finished_at']=now();new_json(dest/'download-receipt.json',receipt)
print(json.dumps(dict(run_id=run_id,conclusion=receipt.get('run_conclusion'),status=receipt['status'],receipt=str(dest/'download-receipt.json'),receipt_sha256=sha((dest/'download-receipt.json').read_bytes()),archives=[{k:v[k] for k in ('artifact_id','artifact_name','sha256','bytes')} for v in receipt['archives']])))
