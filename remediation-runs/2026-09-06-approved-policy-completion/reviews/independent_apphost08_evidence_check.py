"""Read-only receipt arithmetic; no application tests/builds/process mutations."""
from pathlib import Path
import json, hashlib, re, subprocess, math, stat, datetime
from collections import Counter
ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'remediation-runs/2026-09-06-approved-policy-completion'
EV=BASE/'evidence/dsc01-apphost-08'
read=lambda p:json.loads(p.read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
r=read(EV/'receipt.json'); f=read(BASE/'source-freeze-02.json')['source']
cm=read(BASE/'native/dsc01_apphost_v10/author-frozen-control-manifest-01.json'); im=read(EV/'input-manifest.json')
assert r['source_before']==r['source_after']==f==im['source']
assert cm['control_sha256']==im['control_sha256']==r['approved_control_sha256']==r['controls_after_sha256']
for rel,h in f['source_manifest']: assert sha((ROOT/rel).read_bytes())==h,rel
assert len(f['source_manifest'])==669 and sha(subprocess.check_output(['git','diff','--binary','HEAD','--'],cwd=ROOT))==f['diff_sha256']
for row in cm['files']:assert sha((ROOT/row['path']).read_bytes())==row['sha256'],row['path']
assert len(cm['files'])==107
# Independently reconstruct retained copy diff without importing the author copier.
ls=(EV/'copied-source.diff').read_text().splitlines(keepends=True);i=0;patches={};hunks=0
while i<len(ls):
 assert ls[i].startswith('--- ')
 old=ls[i][4:].strip();rel=ls[i+1].removeprefix('+++ audit-copy/').strip();i+=2
 original=[] if old=='/dev/null' else (ROOT/rel).read_text().splitlines(keepends=True)
 result=[];cur=0
 while i<len(ls) and not ls[i].startswith('--- '):
  m=re.fullmatch(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*\n?',ls[i]);assert m,ls[i]
  start=max(int(m[1])-1,0);oc=int(m[2] or 1);nc=int(m[4] or 1);i+=1;hunks+=1
  assert start>=cur;result+=original[cur:start];cur=start;ou=nu=0
  while i<len(ls) and not ls[i].startswith(('--- ','@@ ')):
   sign=ls[i][0];val=ls[i][1:];i+=1;assert sign in ' +-'
   if sign in ' -':assert original[cur]==val,(rel,cur);cur+=1;ou+=1
   if sign in ' +':result.append(val);nu+=1
  assert (ou,nu)==(oc,nc),(rel,ou,nu,oc,nc)
 result+=original[cur:];patches[rel]=''.join(result).encode()
assert len(patches)==13 and hunks==18
copied=read(EV/'copied-source-manifest.json');after=read(EV/'copied-source-inputs-after-build.json');copyhash={}
assert len(copied)==len(after['files'])==615
assert not any(after[k] for k in ['unexpected_build_inputs','missing_build_inputs','unexpected_source_symlinks'])
for row in copied:
 rel=row['path'];original=(ROOT/rel).read_bytes() if (ROOT/rel).exists() else None
 assert (sha(original) if original is not None else None)==row['original_sha256']
 copyhash[rel]=sha(patches.get(rel,original));assert copyhash[rel]==row['copied_sha256']
for row in after['files']:assert row['expected_sha256']==row['observed_sha256']==copyhash[row['path']] and row['unchanged']
# Parse raw receipts independently.
raw=(EV/'xcodebuild.log').read_text().splitlines();meta=[]
for n,line in enumerate(raw,1):
 m=re.search(r'(DSC01_[A-Z_0-9]+)\s+({.*})',line)
 if m:meta.append((n,m[1],json.loads(m[2])))
finite=lambda a:len(a)==4 and all(math.isfinite(x) and abs(x)<=16384 for x in a) and a[2]>0 and a[3]>0
contains=lambda a,b:a[0]<=b[0] and a[1]<=b[1] and a[0]+a[2]>=b[0]+b[2] and a[1]+a[3]>=b[1]+b[3]
near=lambda a,b:len(a)==len(b) and all(abs(x-y)<1e-5 for x,y in zip(a,b))
def rect(obj,key):
 v=obj[key];assert v['present'] and v['validFiniteRectangle'];a=v['rectangle'];assert finite(a);return a
activation={}
for kind,game in [('DSC01_CATALOG_METADATA','whodunit'),('DSC01_CATALOG_METADATA','mafia'),('DSC01_MAFIA_START_METADATA',None)]:
 rows=[(n,d) for n,k,d in meta if k==kind and (game is None or d['game']==game)]
 assert [d['ordinal'] for _,d in rows]==list(range(1,len(rows)+1))
 bs=[(n,d) for n,d in rows if d['kind']=='before-activation'];assert len(bs)==1;n,bef=bs[0];v=bef['fields']
 assert all(v[k] for k in ['foreground','hittable','enabled']) and not v['keyboardPresent'] and not v['alertPresent']
 assert v['targetCountCapped16']==v['overlayCountCapped16']==1
 t,w,o=rect(v,'target'),rect(v,'viewport'),rect(v,'overlay');assert contains(w,o)
 if game:
  assert v['tabCountsCapped16']==[1,1] and v['successorCountCapped16']==0
  tabs=[a['rectangle'] for a in v['tabs']];assert len(tabs)==2 and all(finite(a) and contains(w,a) for a in tabs)
  top=max(w[1],o[1]+o[3])+8;bot=min(w[1]+w[3],min(a[1] for a in tabs))-24
 else:top=max(w[1]+48,o[1]+o[3]+8);bot=w[1]+w[3]-48
 ux=w[0]+8;uw=w[2]-16;x=max(t[0],ux);y=max(t[1],top);cw=min(t[0]+t[2],ux+uw)-x;ch=min(t[1]+t[3],bot)-y
 assert cw>=48 and ch>=48
 inside=[x+12,y+12,cw-24,ch-24];point=[inside[0]+inside[2]/2,inside[1]+inside[3]/2];off=[(point[0]-t[0])/t[2],(point[1]-t[1])/t[3]]
 assert near(inside,rect(v,'visibleInterior')) and near(point,v['plannedPoint']) and near(off,v['normalizedPoint'])
 assert near(point,v.get('actualCoordinatePoint',v.get('sampledCoordinatePoint')))
 search=[(ln,d) for ln,d in rows if d['kind']=='search'];last=search[-6:]
 assert [d['fields']['index'] for _,d in last]==list(range(1,7)) and len({d['fields']['window'] for _,d in last})==1
 for _,d in last:
  q=d['fields'];assert all(q[k] for k in ['foreground','hittable','enabled']) and not q['alertPresent'] and not q['keyboardPresent']
  for k in ['target','viewport','overlay']:assert near(rect(q,k),rect(v,k))
 aa=[ln for ln,d in rows if d['kind']=='after-activation'];ss=[ln for ln,d in rows if d['kind']=='successor'];assert len(aa)==len(ss)==1 and n<aa[0]<ss[0]
 activation[(game+'-catalog') if game else 'mafia-start']={'rows':len(rows),'raw_range':[rows[0][0],rows[-1][0]],'search_windows':max(d['fields']['window'] for _,d in search),'stable_final_six_raw_lines':[ln for ln,_ in last],'before_activation_line':n,'after_activation_line':aa[0],'successor_line':ss[0],'target':t,'viewport':w,'overlay':o,'recalculated_interior':inside,'recalculated_point':point,'recalculated_normalized_point':off}
prereq=[(n,d) for n,k,d in meta if k=='DSC01_OS_PREREQUISITE'];assert len(prereq)==16 and [d['ordinal'] for _,d in prereq]==list(range(1,17))
assert [(d['stage'],d['status']) for _,d in prereq]==[('ownership','PASS'),('settingsRoot','observe'),('settingsRoot','PASS'),('general','observe'),('general','before-activation'),('general','after-activation'),('general','observe'),('general','PASS'),('languageRegion','observe'),('languageRegion','before-activation'),('languageRegion','after-activation'),('languageRegion','observe'),('languageRegion','PASS'),('initialEnglishPrimary','observe'),('initialEnglishPrimary','PASS'),('finalPreferredLanguages','PASS')]
for _,d in prereq[-3:]:
 q=d['fields'];assert q['foreground'] and not q['keyboardPresent'] and q['alertCountCapped16']==q['searchFieldCountCapped16']==0 and q['prerequisiteDisposition']=='already-satisfied'
 assert all(q[k] for k in ['englishPrimaryObserved','englishPrimaryHittable','arabicPreferredRowObserved','languageRegionTitleObserved','languageRegionTitleHittable','addLanguageSuccessorHittableEnabled'])
 assert q['englishPrimaryCellCountCapped16']==q['arabicCellCountCapped16']==1
 w,en,ar,add=rect(q,'viewport'),rect(q,'englishPrimaryFrame'),rect(q,'arabicPreferredRowFrame'),rect(q,'addLanguageSuccessorFrame')
 assert all(contains(w,a) for a in [en,ar,add]) and en[1]+en[3]<=ar[1]+1e-5 and ar[1]+ar[3]<=add[1]+1e-5
 titles={x['knownTitle']:x['countCapped16'] for x in q['knownNavigationTitles']};assert titles['Language & Region']==1 and all(n==0 for k,n in titles.items() if k!='Language & Region')
assert prereq[-1][1]['fields']['arabicAddedByThisPrerequisite'] is False
reports={n:read(EV/f'probe-{n}-result.json') for n in ['settings','whodunit','mafia','os']}
assert len({d['runToken'] for d in reports.values()})==1
for name,d in reports.items():
 assert d['schemaVersion']==6 and d['scenario']==name and d['completed'] is True
 assert [x['ordinal'] for x in d['observations']]==list(range(1,len(d['observations'])+1))
s=reports['settings']['observations'];assert len(s)==22 and len({x['boot'] for x in s})==4
for x in s:
 n=x['ordinal'];p=x['preferences'];c=json.loads(x['composition'])
 if n<=3 or 9<=n<=10:e=('system','en',False,'none',False,[],False,[])
 elif 4<=n<=8:e=('ar','ar',True,'ar',False,[],True,['ar'])
 elif 11<=n<=13 or n>=19:e=('system','ar',False,'none',False,[],True,['ar-EG','en'])
 else:e=('en','en',True,'en',True,['ar-EG','en'],True,['en'])
 assert tuple(p[k] for k in ['setting','preferredLanguage','ownerPresent','ownerInstalled','ownerPreviousPresent','ownerPrevious','hasAppOverride','appLanguages'])==e,n
 if x['phase']!='before_main':
  assert c['commandOrdinal']==0 and c['commandLanguage']==c['commandStatus']=='none' and x['controllerCreations']==1
  assert x['nativeDirection']==('force_rtl' if p['preferredLanguage']=='ar' else 'force_ltr')
  if c['settingsMounted']:assert c['settingsDirection']==('rtl' if p['preferredLanguage']=='ar' else 'ltr')
local={}
for name,phase in [('whodunit','public-intro'),('mafia','role-assignment')]:
 rows=reports[name]['observations'];captured=[x for x in rows if json.loads(x['composition']).get('checkpointCaptured')];assert len(captured)==88 and len({x['boot'] for x in rows})==1
 windows=[];deltas=[];fg=[];prev=0
 for x in captured:
  c=json.loads(x['composition']);assert c['surface']==name+'-local' and c['publicPhase']==phase and c['disposalsSinceCapture']==0
  assert all(c[k] for k in ['sameController','sameCanonicalFlow','sameCanonicalReference','sameCanonicalValue','samePublicPhase'])
  assert c['gameDirection']==('rtl' if x['preferences']['preferredLanguage']=='ar' else 'ltr')
  if c['foregroundSinceCapture']>prev:fg.append(x['ordinal']);prev=c['foregroundSinceCapture']
 for w in range(1,13):
  wr=[x for x in captured if x['samplingWindow']==w];assert len(wr)==6 and [x['samplingIndex'] for x in wr]==list(range(1,7))
  ds=[b['samplingElapsedMilliseconds']-a['samplingElapsedMilliseconds'] for a,b in zip(wr,wr[1:])];assert min(ds)>=250;deltas+=ds
  windows.append({'window':w,'ordinals':[wr[0]['ordinal'],wr[-1]['ordinal']],'context':wr[0]['samplingContext'],'command':json.loads(wr[0]['composition'])['commandOrdinal'],'interval_ms':[min(ds),max(ds)]})
 last=json.loads(captured[-1]['composition']);assert last['backgroundSinceCapture']==last['foregroundSinceCapture']==4
 cmds=[json.loads(x['composition']) for x in captured];assert {c['commandLanguage'] for c in cmds if c['commandOrdinal']>0}=={'ar','en','system'}
 local[name]={'observations':len(rows),'capture_interval':[captured[0]['ordinal'],captured[-1]['ordinal']],'continuous_rows':88,'phase':phase,'passive_samples':72,'windows':windows,'interval_ms':[min(deltas),max(deltas)],'first_post_foreground_ordinals':fg,'background_foreground_cycles':4,'disposals':0,'same_actual_controller_flow_reference_value_phase':True,'synthetic_actual_store_invocations':['ar','en','system'],'initial_english_before_capture_from_actual_settings_ui':True}
geo=[]
for name,d in reports.items():
 for x in d['observations']:
  if x['phase']=='before_main':continue
  n=x['native'];g=n['geometry'];assert all(n[k] for k in ['childParentIsOuter','directSubview','sameComposeController','sameComposeView','sameOuterController','sameOuterView','sameWindow','singleComposeChild'])
  assert n['attachments']==1 and n['disposals']==0
  bounds=[0,0,402,874] if g['orientation']=='portrait' else [0,0,874,402];safe=[62,0,34,0] if g['orientation']=='portrait' else [0,62,20,62]
  for k in ['composeBounds','composeFrameInOuter','composeInWindow','outerBounds','outerInWindow','windowBounds']:assert g[k]==bounds,(name,x['ordinal'],k)
  for k in ['composeSafeArea','outerSafeArea','windowSafeArea']:assert g[k]==safe,(name,x['ordinal'],k)
  geo.append((g['orientation'],n['outerDirection']!=x['nativeDirection']))
assert len(geo)==219
osrows=reports['os']['observations']
assert len(osrows)==8 and len({x['boot'] for x in osrows})==1
assert [x['osDisposition'] for x in osrows]==['not-investigated']*4+['opened']+['language-control-unavailable']*3
assert all(x['preferences']['setting']=='system' and x['preferences']['appLanguages']==['ar-EG','en'] and x['preferences']['ownerPresent'] is False for x in osrows)
contracts=[(n,d) for n,k,d in meta if k=='DSC01_OS_INTERACTION_DECISION_CONTRACT']
assert len(contracts)==1 and contracts[0][1]=={'schemaVersion':1,'cases':24,'passed':True}
osmeta=[(n,d) for n,k,d in meta if k=='DSC01_OS_APP_INTERACTION']
assert len(osmeta)==25 and [d['ordinal'] for _,d in osmeta]==list(range(1,26))
initial=osmeta[:6];assert [d['fields']['attempt'] for _,d in initial]==list(range(1,7))
for n,d in initial:
 assert d['stage']=='initialPane' and d['status']=='observe' and d['fields']['eligiblePaneCount']==0
 panes=d['fields']['knownPanes'];assert [p['pane'] for p in panes]==['settings','apps','parlor']
 for p in panes:
  assert p['foreground'] and p['alertCountCapped16']==0 and p['keyboardPresent'] is False
  assert p['paneCountCapped16']==0 and p['paneHittable'] is False and p['paneFrame']=={'present':False}
  known={x['knownTitle']:x['countCapped16'] for x in p['knownNavigationTitles']}
  assert known['Settings']==1 and all(v==0 for k,v in known.items() if k!='Settings')
  assert rect(p,'viewport')==[0,0,402,874]
assert osmeta[6][1]=={'fields':{'reason':'actual-known-pane-unavailable-or-ambiguous'},'ordinal':7,'schemaVersion':1,'stage':'initialPane','status':'BLOCKED'}
assert not any(d['status']=='PASS' and d['stage'] in ['initialPane','appsSuccessor','appPane','english'] for _,d in osmeta)
# Independently recompute every terminal target guard and three-observation train.
def os_eligible(v):
 assert v['pane']=='audit' and v['foreground'] and not v['keyboardPresent'] and v['alertCountCapped16']==0
 assert v['paneCountCapped16']==1 and v['paneHittable'] and v['selectorKind']=='audit-button'
 assert v['targetCountCapped16']==1 and v['targetHittable'] and v['targetEnabled']
 viewport,target=rect(v,'viewport'),rect(v,'targetFrame')
 assert contains(viewport,rect(v,'paneFrame')) and contains(viewport,target)
 return target
befores=[(n,d) for n,d in osmeta if d['status']=='before-activation']
afters=[(n,d) for n,d in osmeta if d['status']=='after-activation']
assert [d['fields']['control'] for _,d in befores]==['showMarkers','noRow','complete']
assert len(afters)==3
terminal_actions=[];previous=7
for (ln,pre),(aln,post) in zip(befores,afters):
 assert pre['stage']==post['stage'] and previous<pre['ordinal']<post['ordinal']
 control=pre['fields']['control'];assert post['fields']=={'activationCount':1,'control':control,'expectedSourcePane':'audit'}
 target=os_eligible(pre['fields']);stable=pre['fields']['stableFrames'];assert len(stable)==3
 for entry in stable:
  assert entry['present'] and entry['validFiniteRectangle'] and finite(entry['rectangle'])
  assert all(abs(a-b)<=0.5 for a,b in zip(entry['rectangle'],target))
 samples=[(n,d) for n,d in osmeta if previous<d['ordinal']<pre['ordinal'] and d['status']=='observe' and d['fields'].get('control')==control]
 assert len(samples)==3 and [d['fields']['attempt'] for _,d in samples]==[1,2,3]
 for _,sample in samples:assert all(abs(a-b)<=0.5 for a,b in zip(os_eligible(sample['fields']),target))
 terminal_actions.append({'control':control,'target':target,'sample_raw_lines':[n for n,_ in samples],'before_raw_line':ln,'after_raw_line':aln})
 previous=post['ordinal']
panel=[(n,d) for n,d in osmeta if d['stage']=='markerControls' and d['status']=='PASS'];assert len(panel)==1
panel_frames=[os_eligible(row) for row in panel[0][1]['fields']['visibleMarkerRows']]
assert panel_frames==[[4,183,394,44],[4,231,394,44],[4,279,394,44]]
assert [r['control'] for r in panel[0][1]['fields']['visibleMarkerRows']]==['noRow','noSelection','verified']
marker=[(n,d) for n,d in osmeta if d['stage']=='marker' and d['status']=='PASS'];completed=[(n,d) for n,d in osmeta if d['stage']=='complete' and d['status']=='PASS']
assert len(marker)==len(completed)==1
assert marker[0][1]['fields']=={'sequence':6,'osDisposition':'language-control-unavailable','completed':False}
assert completed[0][1]['fields']=={'sequence':7,'osDisposition':'language-control-unavailable','completed':True}
assert osrows[5]['ordinal']==6 and osrows[5]['phase']=='sample'
assert osrows[6]['ordinal']==7 and osrows[6]['phase']=='complete'
assert afters[0][1]['ordinal']<panel[0][1]['ordinal']<befores[1][1]['ordinal']<afters[1][1]['ordinal']<marker[0][1]['ordinal']<befores[2][1]['ordinal']<afters[2][1]['ordinal']<completed[0][1]['ordinal']
selector=[(n,d) for n,k,d in meta if k=='DSC01_OS_SELECTOR_DIAGNOSTICS'];assert len(selector)==1
for q in selector[0][1]['observations']:
 assert q['countCappedAt32']==q['hittableAmongAtMost4']==(1 if q['knownLabel']=='Settings' else 0)
assert all(q['countCappedAt32']==q['hittableAmongAtMost4']==0 and not q['framesAtMost4'] for q in selector[0][1]['diagnosticOnlyKnownPrefixes'])
# Restrict cleanup inspection to exact completed-cycle identities/paths.
procs={}
for line in subprocess.check_output(['ps','-axo','pid=,lstart='],text=True).splitlines():
 v=line.split(None,1)
 if len(v)==2:procs[int(v[0])]=' '.join(v[1].split())
survivors=[x for x in r['ownership_observed'] if procs.get(x['pid'])==' '.join(x['start'].split())];assert not survivors
paths=[r['owned_temporary_directory'],r['copy_only_build_root'],str(Path(r['owned_temporary_directory'])/'DerivedData')]+r['secondary_cleanup']['removed']
assert len(paths)==7 and all(not Path(p).exists() and not Path(p).is_symlink() for p in paths)
ff=read(EV/'secondary-fifo-ownership-final.json');assert len(ff['records'])==2 and not ff['pending'];owned={(x['pid'],x['start']) for x in r['ownership_observed']}
for x in ff['records']:
 assert (x['process']['pid'],x['process']['start']) in owned and (x['parent']['pid'],x['parent']['start']) in owned
 assert stat.S_ISFIFO(x['metadata'][-1]['mode']) and all(m['uid']==501 and m['birth_at']>=ff['cycle_started'] for m in x['metadata'])
assert r['cleanup_status']=='PASS' and all(not r[k] for k in ['cleanup_errors','owned_processes_remaining','unknown_holders','remaining_outputs'])
assert read(EV/'owned-device-after-delete.json')=={'matches':[]}
xc=[c for c in r['commands'] if c['command'][0]=='xcodebuild'][0];stops=[c for c in r['commands'] if c['command']==['./gradlew','--stop']];assert len(stops)==2 and all(c['exit_code']==0 for c in stops)
dt=datetime.datetime.fromisoformat
summary=read(EV/'xcresult-summary.json');tests=[]
def walk(n):
 if n.get('nodeType')=='Test Case':tests.append({'id':n['nodeIdentifier'],'result':n['result'],'duration_seconds':n['durationInSeconds']})
 for c in n.get('children',[]):walk(c)
for n in read(EV/'xcresult-tests.json')['testNodes']:walk(n)
assert summary['failedTests']==0 and summary['passedTests']==5 and summary['skippedTests']==0 and len(tests)==5
expected={'ComposeContainerViewControllerTests/'+name+'()' for name in ('testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView','testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections','testSystemDecorationAndOrientationPoliciesStillBelongToTheChild','testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce')}
expected.add('IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSInvestigation()')
assert {t['id'] for t in tests}==expected and all(t['result']=='Passed' for t in tests)
assert r['xcodebuild_exit_code']==0 and r['status']=='PARTIALLY_VERIFIED' and r['os_settings_gate']['status']=='BLOCKED'
assert r['v10_os_interaction_proof']['actual_public_actions']==[]
assert r['dsc01_matrix']['actual_settings_and_local_sessions_gate']=='PASS'
assert r['dsc01_matrix']['retained_multiplayer_host_gate']=='NOT_RUN'
assert (EV/'embedded-gradle-stop.txt').read_text()=='build_exit=0\nstop_exit=0\n'
device_path=Path.home()/'Library/Developer/CoreSimulator/Devices'/r['owned_uuid'];assert not device_path.exists() and not device_path.is_symlink()
# Exact copy composition is independently rebuilt from frozen literal renderer
# constants, not inferred from author parser success.
import ast
H=BASE/'native/dsc01_apphost_v10';constants={}
for node in ast.parse((H/'v10_sources.py').read_text()).body:
 if isinstance(node,ast.Assign):constants[node.targets[0].id]=ast.literal_eval(node.value)
ui=(H/'IOSAppLaunchUITests.swift.in').read_text();start=ui.index(constants['OS_FUNCTION']);end=ui.index(constants['POST_SELECTION'])
rendered=(ui[:start]+constants['NEW_PREFIX']+ui[end:]).replace(constants['OLD_VERIFIED'],constants['NEW_VERIFIED'],1)
for name in ['DSC01PublicDiagnostics.swift.in','DSC01CatalogSelection.swift.in','DSC01MafiaStartSelection.swift.in','DSC01OSPrerequisite.swift.in','DSC01OSAppSettings.swift.in']:rendered+='\n'+(H/name).read_text()
assert rendered.encode()==patches['iosApp/iosAppUITests/IOSAppLaunchUITests.swift']
record={'schema_version':1,'reviewer':'/root/release_fix_review','author':'/root/native_fix_review','recorded_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'classification':'PARTIALLY VERIFIED — actual OS per-app ownership flow remains BLOCKED. Five passing XCTest methods are not DS-C01 completion.',
 'source':{k:f[k] for k in ['branch','commit','tree','diff_sha256','source_manifest_sha256']},'source_input_count':669,'current_source_and_tracked_diff_rehashed_unchanged':True,
 'evidence_sha256':{str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in [EV/'receipt.json',EV/'xcodebuild.log',EV/'xcresult-summary.json',EV/'xcresult-tests.json',EV/'copied-source.diff',EV/'copied-source-manifest.json',EV/'copied-source-inputs-after-build.json',EV/'secondary-fifo-ownership-final.json',*[EV/f'probe-{n}-result.json' for n in reports],H/'author-frozen-control-manifest-01.json']},
 'controls':{'count':107,'all_rehashed_unchanged':True,'aggregate_sha256':cm['control_sha256']},
 'copy_provenance':{'original_inputs':613,'final_inputs':615,'transformed_or_added_paths':13,'hunks':18,'all_hunks_independently_applied_against_frozen_source':True,'all_copied_and_post_worker_hashes_matched':True,'literal_renderer_and_appended_controls_match_actual_copied_ui':True,'unexpected_missing_or_symlink_inputs':[],'limitation':'Required cleanup deleted the build copy; retained full diff reconstructs it. Artifact receipt omits Parlor.debug.dylib and is not complete binary provenance.'},
 'xctest':{'result':'PASS','xcode_exit':0,'runner_exit':2,'tests':tests,'passed':5,'failed':0,'skipped':0,'actual_platform':'iOS Simulator26.5 arm64 owned iPhone17Pro','scope':'Four actual production UIKit wrapper tests and one matrix XCTest. Synthetic Swift contracts execute within the matrix, not separate methods.'},
 'settings':{'observations':22,'boots':list(dict.fromkeys(x['boot'] for x in s)),'all_ownership_tuples_independently_checked':True,'zero_synthetic_store_commands':True,'sequence1':['fresh System EN','actual AR','restart AR','actual System EN'],'sequence2':['synthetic prior ar-EG/en','actual EN','restart EN','actual System restores ar-EG/en'],'limitation':'Prior ar-EG/en is synthetic app-domain fixture, not a real OS per-app write.'},
 'local_games':local,
 'native_geometry':{'rows':len(geo),'orientation_counts':dict(Counter(x[0] for x in geo)),'opposing_outer_and_actual_compose_direction_rows':sum(x[1] for x in geo),'all_eight_identity_booleans_true':True,'one_attachment_zero_disposals':True,'full_bounds_and_safe_areas_independently_recalculated':True},
 'public_activation':activation,
 'os_prerequisite':{'result':'PASS','disposition':'already-satisfied','rows':16,'raw_lines':[prereq[0][0],prereq[-1][0]],'contract_cases':24,'contract_part_of_matrix_not_24_xctests':True,'three_final_rows_independently_checked':True,'ordered_unique_current_english_primary_arabic_add_language':True,'english_frame':en,'arabic_frame':ar,'add_language_frame':add,'arabicAddedByThisPrerequisite':False,'navigation_activations':['General','Language & Region'],'add_search_select_or_primary_language_mutations':False},
 'os_original_gate':{'result':'BLOCKED','blocked_stage':'initialPane','selector_raw_line':selector[0][0],'all_exact_labels_absent_except_generic_Settings':True,'all_five_language_prefixes_absent':True,'actual_settings_app_pane_proven':False,'actual_os_english_selection_executed':False,'actual_public_route_actions':[],'observations':8,'boots':list({x['boot'] for x in osrows}),'dispositions':[x['osDisposition'] for x in osrows],'completed_flag_not_success_proof':True,'not_application_crash_evidence':True,'query_divergence_evidence':{'raw_lines':[n for n,_ in initial],'attempts':6,'observed_panes':['settings','apps','parlor'],'label_predicate_counts':[0,0,0],'multi_property_matching_Settings_count':1,'raw_identifier_value_not_observed':True},'source_proof':'Helper osPaneMetadata92–105 eligibility uses navigationBars label-IN; knownNavigationTitles uses matching(identifier:). awaitOSPane141–155 rejects all six zero-count eligible queries and reports BLOCKED before any public navigation. Apple documents multi-property matching; the positive diagnostic does not establish identifier==Settings or a Parlor pane.','application_defect_established':False},
 'v10_terminal_reporting':{'result':'PASS','raw_interaction_rows':25,'swift_contract_cases':24,'swift_contract_raw_line':contracts[0][0],'public_route_activations':0,'guarded_terminal_activations':terminal_actions,'expanded_panel_raw_line':panel[0][0],'expanded_marker_frames':panel_frames,'durable_marker':{'raw_line':marker[0][0],'probe_sequence':6,'status':'language-control-unavailable'},'completion':{'raw_line':completed[0][0],'probe_sequence':7},'ordered_panel_marker_complete_backed_by_original_probe':True,'does_not_prove_OS_English_selection':True},
 'cleanup':{'receipt':'PASS','stop_exits':[x['exit_code'] for x in stops],'immediate_stop_start_delay_seconds':(dt(stops[0]['started_at'])-dt(xc['finished_at'])).total_seconds(),'immediate_stop_finish_delay_seconds':(dt(stops[0]['finished_at'])-dt(xc['finished_at'])).total_seconds(),'owned_pid_start_identities_checked':len(r['ownership_observed']),'remaining_owned_identities':survivors,'owned_paths_checked':paths,'all_absent':True,'secondary_fifo_count':2,'secondary_empty_parent_count':2,'fifo_pid_start_parent_uid_birth_type_attestation_checked':True,'owned_device_absent':True,'global_idle_claim':False,'unrelated_resources_not_touched':True},
 'research_records':['reviews/independent-v11-exact-identifier-research-02.json','reviews/independent-v11-pinned-xctest-declarations-01.json'],
 'suggested_next_scope':['Investigate conservative exact identifier-or-label pane predicate with measured per-attribute provenance, union uniqueness, foreground/title/frame/keyboard/alert guards. Do not equate matching(identifier:) with exact identifier.','Diagnostic count from title/value/placeholder only must not invent identifier/label evidence. Zero/ambiguous/hidden panes remain BLOCKED.','Retain exact English->in-appAR->System->restart oracles, original148 controls and V10 terminal reporting. No application source change justified by this harness observation.','Review/freeze next copy-only delta, root focused controls, independent result review and separate native GO; no next native attempt authorized here.'],
 'limitations':['No actual OS English selection, post-OS Arabic/System restoration or post-OS restart proof.','No complete-game/storage-resume/retained-multiplayer/physical-LAN or physical-accessibility proof.','Xcode26.5/17F42 simulator is not Store-qualified26.3, signing or Store evidence.','Copied overlay is observation instrumentation and may perturb SwiftUI outside passive windows.'],
 'reviewer_execution':{'builds':0,'application_tests':0,'apps':0,'simulators':0,'background_workers':0,'source_edits':0,'git_store_signing_mutations':0,'process_stops':0,'build_outputs_created':0,'cleanup_required':False}}
record['arithmetic_script_sha256']=sha(Path(__file__).read_bytes())
out=BASE/'reviews/independent-dsc01-apphost08-outcome-01.json'
with out.open('x') as handle:handle.write(json.dumps(record,indent=2)+'\n')
print(json.dumps({'path':str(out),'sha256':sha(out.read_bytes()),'source':669,'controls':107,'copies':615,'settings':len(s),'native_geometry':len(geo),'owned_identities_checked':len(r['ownership_observed']),'actual_OS_gate':'BLOCKED','independent_status':record['classification']}))
