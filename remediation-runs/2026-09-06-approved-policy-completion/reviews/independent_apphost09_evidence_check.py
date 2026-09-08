"""Read-only receipt arithmetic; no application tests/builds/process mutations."""
from pathlib import Path
import json, hashlib, re, subprocess, math, stat, datetime
from collections import Counter
ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'remediation-runs/2026-09-06-approved-policy-completion'
EV=BASE/'evidence/dsc01-apphost-09'
read=lambda p:json.loads(p.read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
r=read(EV/'receipt.json'); f=read(BASE/'source-freeze-02.json')['source']
cm=read(BASE/'native/dsc01_apphost_v11/author-frozen-control-manifest-01.json'); im=read(EV/'input-manifest.json')
assert r['source_before']==r['source_after']==f==im['source']
assert cm['control_sha256']==im['control_sha256']==r['approved_control_sha256']==r['controls_after_sha256']
for rel,h in f['source_manifest']: assert sha((ROOT/rel).read_bytes())==h,rel
assert len(f['source_manifest'])==669 and sha(subprocess.check_output(['git','diff','--binary','HEAD','--'],cwd=ROOT))==f['diff_sha256']
for row in cm['files']:assert sha((ROOT/row['path']).read_bytes())==row['sha256'],row['path']
assert len(cm['files'])==137
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
 assert d['schemaVersion']==6 and d['scenario']==name and d['completed'] is (name != 'os')
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
assert len(geo)==222
osrows=reports['os']['observations']
assert len(osrows)==12 and len({x['boot'] for x in osrows})==2
assert [x['osDisposition'] for x in osrows]==['not-investigated']*4+['opened']*8
oldboot=osrows[0]['boot'];newboot=osrows[5]['boot'];assert oldboot!=newboot
for x in osrows[:5]:
 assert x['boot']==oldboot and x['preferences']=={'hasAppOverride':True,'appLanguages':['ar-EG','en'],'setting':'system','preferredLanguage':'ar','ownerInstalled':'none','ownerPrevious':[],'ownerPreviousPresent':False,'ownerPresent':False}
for x in osrows[5:]:
 assert x['boot']==newboot and x['preferences']=={'hasAppOverride':False,'appLanguages':[],'setting':'system','preferredLanguage':'en','ownerInstalled':'none','ownerPrevious':[],'ownerPreviousPresent':False,'ownerPresent':False}
 assert x['fixture']=='existing'
assert osrows[5]['phase']=='before_main' and osrows[5]['controllerCreations']==0 and json.loads(osrows[5]['composition'])=={}
for x in osrows[6:]:
 assert x['phase']=='sample' and x['controllerCreations']==1 and x['nativeDirection']=='force_ltr'
 c=json.loads(x['composition']);assert c['commandOrdinal']==0 and c['commandLanguage']==c['commandStatus']=='none'
 # Evaluate all four source predicates from the retained actual snapshot,
 # without calling the author observer or substituting another expectation.
 pref=x['preferences']
 clauses=[pref['setting']=='system',pref['ownerPresent'] is False,pref['preferredLanguage']=='en',pref['hasAppOverride'] is True]
 assert clauses==[True,True,True,False]
contracts=[(n,d) for n,k,d in meta if k=='DSC01_OS_INTERACTION_DECISION_CONTRACT']
identitycontracts=[(n,d) for n,k,d in meta if k=='DSC01_OS_PANE_IDENTITY_DECISION_CONTRACT']
assert len(contracts)==1 and contracts[0][1]=={'schemaVersion':1,'cases':24,'passed':True}
assert len(identitycontracts)==1 and identitycontracts[0][1]=={'schemaVersion':1,'attributeCases':12,'unionCases':2,'identityGuardCases':2,'passed':True}
osmeta=[(n,d) for n,k,d in meta if k=='DSC01_OS_APP_INTERACTION']
assert len(osmeta)==26 and [d['ordinal'] for _,d in osmeta]==list(range(1,27))
public_actions=[]
properties=['identifier','title','label','value','placeholderValue']
titles={'settings':['Settings'],'apps':['Apps'],'parlor':['Parlor','بارلور'],'language':['Language','Preferred Language']}
observed_panes=[]
for ln,row in osmeta:
 q=row['fields']
 if 'pane' not in q:continue
 assert q['pane'] in titles and q['paneSelectorMethod']=='known-public-identifying-properties-union'
 matches=q['paneKnownPropertyMatches'];names=[]
 for entry in matches:
  assert set(entry)=={'property','knownLiteral'} and entry['property'] in properties and entry['knownLiteral'] in titles[q['pane']]
  names.append(entry['property'])
 assert names==[name for name in properties if name in names]
 assert q['paneCountCapped16']==1 and q['paneIdentityMatchesKnown'] is True and matches==[{'property':'identifier','knownLiteral':titles[q['pane']][0]}]
 assert q['foreground'] and q['paneHittable'] and q['alertCountCapped16']==0 and q['keyboardPresent'] is False
 assert contains(rect(q,'viewport'),rect(q,'paneFrame'))
 observed_panes.append(dict(raw_line=ln,pane=q['pane'],known_property_matches=matches))
passes=[(n,d) for n,d in osmeta if d['status']=='PASS']
assert [(x['stage'],x['fields']['pane']) for _,x in passes]==[('initialPane','settings'),('appsSuccessor','apps'),('appPane','parlor'),('english','language')]
befores=[(n,d) for n,d in osmeta if d['status']=='before-activation'];afters=[(n,d) for n,d in osmeta if d['status']=='after-activation']
assert [d['fields']['control'] for _,d in befores]==['apps','parlor','language','english'] and len(afters)==4
previous=1
for (n,before),(an,after),(control,pane) in zip(befores,afters,[('apps','settings'),('parlor','apps'),('language','parlor'),('english','language')]):
 q=before['fields'];assert before['stage']==after['stage']==control and q['pane']==pane
 assert q['selectorKind']=='known-cell' and q['targetCountCapped16']==1 and q['targetHittable'] and q['targetEnabled']
 target=rect(q,'targetFrame');assert contains(rect(q,'viewport'),target)
 assert after['fields']=={'activationCount':1,'control':control,'expectedSourcePane':pane}
 observations=[(ln,d) for ln,d in osmeta if previous<d['ordinal']<before['ordinal'] and d['status']=='observe' and d['stage']==control]
 samples=observations[-3:];assert len(samples)==3
 frames=q['stableFrames'];assert len(frames)==3
 for (ln,d),frame in zip(samples,frames):
  v=d['fields'];assert v['control']==control and v['pane']==pane and v['targetCountCapped16']==1 and v['targetHittable'] and v['targetEnabled']
  assert contains(rect(v,'viewport'),rect(v,'targetFrame')) and v['paneIdentityMatchesKnown'] is True
  assert frame['present'] and frame['validFiniteRectangle'] and finite(frame['rectangle'])
  assert all(abs(a-b)<=0.5 for a,b in zip(frame['rectangle'],target))
  assert all(abs(a-b)<=0.5 for a,b in zip(rect(v,'targetFrame'),target))
 assert n<an and before['ordinal']<after['ordinal']
 public_actions.append(dict(control=control,source_pane=pane,target=target,observations=len(observations),stable_last_three_raw_lines=[ln for ln,_ in samples],before_raw_line=n,after_raw_line=an,activation_count=1))
 previous=after['ordinal']
assert not any(d['status']=='BLOCKED' for _,d in osmeta)
assert not any(d['stage'] in ['markerControls','marker','complete','showMarkers'] for _,d in osmeta)
failures=[(n,d) for n,k,d in meta if k=='DSC01_PUBLIC_OBSERVATION_FAILURE']
assert len(failures)==1
fn,failure=failures[0]
assert failure['schemaVersion']==1 and failure['predicate']=='afterOSSystemEnglish' and failure['reason']=='six-observations-without-match'
assert failure['observations']==[dict(attempt=i,publicPhase='none',scenario='os',surface='none') for i in range(1,7)]
assert fn>befores[-1][0] and not reports['os']['completed']
# Source/copy identity reconstructs actual instrumentation and location; no
# production/harness function is imported or executed.
import ast
H=BASE/'native/dsc01_apphost_v11';constants={}
for node in ast.parse((H/'v10_sources.py').read_text()).body:
 if isinstance(node,ast.Assign):constants[node.targets[0].id]=ast.literal_eval(node.value)
ui=(H/'IOSAppLaunchUITests.swift.in').read_text();start=ui.index(constants['OS_FUNCTION']);end=ui.index(constants['POST_SELECTION'])
rendered=(ui[:start]+constants['NEW_PREFIX']+ui[end:]).replace(constants['OLD_VERIFIED'],constants['NEW_VERIFIED'],1)
for name in ['DSC01PublicDiagnostics.swift.in','DSC01CatalogSelection.swift.in','DSC01MafiaStartSelection.swift.in','DSC01OSPrerequisite.swift.in','DSC01OSAppSettings.swift.in']:rendered+='\n'+(H/name).read_text()
assert rendered.encode()==patches['iosApp/iosAppUITests/IOSAppLaunchUITests.swift']
rendered_lines=rendered.splitlines();predicate_line=next(i for i,x in enumerate(rendered_lines,1) if 'let afterOS = try observeEventually' in x)
assert predicate_line==432
app_init=patches['iosApp/iosApp/iOSApp.swift'].decode()
assert app_init.count('init() { DSC01Probe.shared.prepare() }')==1 and app_init.index('prepare()')<app_init.index('var body')
probe=(H/'DSC01Probe.swift.in').read_text();owner=(ROOT/'shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/IosLanguageOverrideOwner.kt').read_text()
assert 'if selected == "previous-ar"' in probe and 'UserDefaults.standard.set(["ar-EG", "en"], forKey: "AppleLanguages")' in probe
assert 'val record = domain[OWNERSHIP_KEY] as? Map<*, *> ?: return' in owner
# Restrict cleanup inspection to exact completed-cycle PID/start identities and paths.
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
xc=[c for c in r['commands'] if c['command'][0]=='xcodebuild'][0];stops=[c for c in r['commands'] if c['command']==['./gradlew','--stop']]
assert len(stops)==2 and all(c['exit_code']==0 for c in stops)
dt=datetime.datetime.fromisoformat
summary=read(EV/'xcresult-summary.json');tests=[]
def walk(n):
 if n.get('nodeType')=='Test Case':tests.append({'id':n['nodeIdentifier'],'result':n['result'],'duration_seconds':n['durationInSeconds']})
 for c in n.get('children',[]):walk(c)
for n in read(EV/'xcresult-tests.json')['testNodes']:walk(n)
assert summary['failedTests']==1 and summary['passedTests']==4 and summary['skippedTests']==0 and len(tests)==5
expected={'ComposeContainerViewControllerTests/'+name+'()' for name in ('testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView','testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections','testSystemDecorationAndOrientationPoliciesStillBelongToTheChild','testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce')}
failed_id='IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSInvestigation()'
assert {t['id'] for t in tests if t['result']=='Passed'}==expected
assert [t['id'] for t in tests if t['result']=='Failed']==[failed_id]
assert summary['result']=='Failed' and summary['testFailures'][0]['failureText']=='failed - Bounded public observation failed: afterOSSystemEnglish'
assert r['xcodebuild_exit_code']==65 and r['status']=='FAIL' and r['runtime_evidence_status']=='FAIL'
assert r['error']=={'type':'RuntimeError','message':'Actual XCTest summary is not exactly five successful, unskipped tests'}
assert all(k not in r for k in ['dsc01_matrix','v10_os_interaction_proof','v11_pane_identity_proof','os_settings_gate'])
assert (EV/'embedded-gradle-stop.txt').read_text()=='build_exit=0\nstop_exit=0\n'
device_path=Path.home()/'Library/Developer/CoreSimulator/Devices'/r['owned_uuid'];assert not device_path.exists() and not device_path.is_symlink()
modules=[p.parent for p in ROOT.glob('shared/*/build.gradle.kts')]+[p.parent for p in ROOT.glob('game-modes/*/build.gradle.kts')]+[ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention']
assert len(modules)==16 and all(not (p/'build').exists() and not (p/'build').is_symlink() for p in modules)

def file_record(p, ranges=None):
 value={'path':str(p),'sha256':sha(p.read_bytes())}
 if ranges:value['reviewed_ranges']=ranges
 return value

record={'schema_version':1,'reviewer':'/root/release_fix_review','author':'/root/native_fix_review','recorded_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'classification':'FAILED VERIFICATION — harness requires a nonempty application-domain language representation absent in actual OS-selected-English observations before application initialization. No new application defect established; DS-C01 remains PARTIALLY_VERIFIED.',
 'source':{k:f[k] for k in ['branch','commit','tree','diff_sha256','source_manifest_sha256']},'source_input_count':669,'current_source_and_tracked_diff_rehashed_unchanged':True,
 'evidence_sha256':{str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in [EV/'receipt.json',EV/'xcodebuild.log',EV/'xcresult-summary.json',EV/'xcresult-tests.json',EV/'copied-source.diff',EV/'copied-source-manifest.json',EV/'copied-source-inputs-after-build.json',EV/'secondary-fifo-ownership-final.json',*[EV/f'probe-{n}-result.json' for n in reports],H/'author-frozen-control-manifest-01.json']},
 'controls':{'count':137,'all_rehashed_unchanged':True,'aggregate_sha256':cm['control_sha256']},
 'copy_provenance':{'original_inputs':613,'final_inputs':615,'transformed_or_added_paths':13,'hunks':18,'all_hunks_independently_applied_against_frozen_source':True,'all_copied_and_post_worker_hashes_matched':True,'literal_renderer_and_appended_controls_match_actual_copied_ui':True,'unexpected_missing_or_symlink_inputs':[],'limitation':'Required cleanup deleted the build copy; retained full diff reconstructs it. Artifact receipt omits Parlor.debug.dylib and is not complete binary provenance.'},
 'xctest':{'result':'FAIL','xcode_exit':65,'runner_exit':1,'tests':tests,'passed':4,'failed':1,'skipped':0,'actual_platform':'iOS Simulator26.5 arm64 owned iPhone17Pro','scope':'Four actual production UIKit wrapper tests passed; one matrix XCTest failed. Synthetic Swift contracts executed within the failed matrix, not separate tests. Root downstream matrix/receipt gates did not run after failed XCTest validation.'},
 'settings':{'row_reconciliation':'PASS_FOR_REACHED_SUBSET_NOT_WHOLE_XCTEST','observations':22,'boots':list(dict.fromkeys(x['boot'] for x in s)),'all_ownership_tuples_independently_checked':True,'zero_synthetic_store_commands':True,'sequence1':['fresh System EN','actual AR','restart AR','actual System EN'],'sequence2':['synthetic prior ar-EG/en','actual EN','restart EN','actual System restores ar-EG/en'],'limitation':'Prior ar-EG/en is synthetic app-domain fixture, not a real OS per-app write.'},
 'local_games':local,
 'native_geometry':{'rows':len(geo),'orientation_counts':dict(Counter(x[0] for x in geo)),'opposing_outer_and_actual_compose_direction_rows':sum(x[1] for x in geo),'all_eight_identity_booleans_true':True,'one_attachment_zero_disposals':True,'full_bounds_and_safe_areas_independently_recalculated':True},
 'public_activation':activation,
 'os_prerequisite':{'row_reconciliation':'PASS_FOR_REACHED_SUBSET_NOT_WHOLE_XCTEST','disposition':'already-satisfied','rows':16,'raw_lines':[prereq[0][0],prereq[-1][0]],'contract_cases':24,'contract_part_of_matrix_not_24_xctests':True,'three_final_rows_independently_checked':True,'ordered_unique_current_english_primary_arabic_add_language':True,'english_frame':en,'arabic_frame':ar,'add_language_frame':add,'arabicAddedByThisPrerequisite':False,'navigation_activations':['General','Language & Region'],'add_search_select_or_primary_language_mutations':False},
 'v11_observed_panes':{'public_pane_rows':len(observed_panes),'actual_property':'identifier in all inspected09pane rows; does not retroactively identify08property','rows':observed_panes,'real_native_contract':identitycontracts,'original24guard_contract':contracts,'no_label_or_title_or_value_fabrication':True},
 'os_actual_actions':{'route':['Settings','Apps','Parlor','Language','English'],'guarded_one_time_actions':public_actions,'four_stage_successor_or_source_contexts':[(n,d['stage'],d['fields']['pane']) for n,d in passes],'unknown_alert_dismissal':False,'coordinate_fallback_used':False,'synthetic_OS_defaults_write':False,'limited_result':'Actual public English action and resulting effective-English/domain-absence observed, not yet preservation through later app override/System/restart.'},
 'os_failed_gate':{'result':'FAIL','predicate':'afterOSSystemEnglish','raw_failure_line':fn,'source_template_lines':[437,445],'copied_ui_predicate_start_line':predicate_line,'actual_sample_ordinals':[7,12],'sample_count':6,'clause_truths':[True,True,True,False],'sole_false_clause':'hasAppOverride == true','already_absent_before_application':{'ordinal':6,'phase':'before_main','fixture':'existing','controllerCreations':0,'composition':{},'appLanguages':[],'hasAppOverride':False,'ownerPresent':False,'effectiveLanguage':'en'},'before_boot':oldboot,'after_boot':newboot,'subsequent_native_direction':'force_ltr','report_completed':False,'terminal_marker_actions_executed':False,'actual_AR_System_and_restart_oracles_after_OS_executed':False,'application_crash_established':False,'actual_OS_created_PRESENT_record_observed':False},
 'source_proof_and_counterevidence':[
  'Copied iOSApp.init calls prepare before scene ContentView/Compose/Koin. In recovered-existing fixture prepare104–124 reads report/preferences; only previous-ar branch119 writes AppleLanguages, not reached here.',
  'Both pre-OS and post-OS rows show no ownership record. IosLanguageOverrideOwner.release36–38 returns immediately with no owner; no application-domain mutation can explain the already-absent before-main row.',
  'Settings/system/effectiveEnglish/nativeLTR agree afterOS. Failure is solely harness storage-shape assumption, not wrong language or missing application view.',
  'Apple persistentDomain API returns only specified-domain keys, not resolved global/other domains. Official docs examined do not require OS English selection to create an app-domain AppleLanguages array.',
  'Original DS-C01contract is platform-following plus exact ownership restoration, including preserving absence rather than promoting fallback. Nonempty was a harness representation assumption, not an authorized product requirement.',
  'Production owner.apply16–33 and release36–54 explicitly support previous absent; original assertOwned/assertSystem630–653 likewise encode previous absence. verify_os319–325 currently over-restricts its baseline to nonempty English-first ownerPrevious.',
  'XCUIApplication.activate documentation explicitly reuses original launch arguments/environment, explaining fixture existing despite latest launchEnvironment assignment to continue. It can launch a not-running app; no crash cause inferred.'
 ],
 'reviewed_surrounding_source':[file_record(ROOT/p,ranges) for p,ranges in [
  ('iosApp/iosApp/iOSApp.swift',[[1,10]]),('iosApp/iosApp/ContentView.swift',[[1,62]]),
  ('composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt',[[1,56]]),
  ('shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/IosLanguageOverrideOwner.kt',[[1,61]]),
  ('shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt',[[1,83]]),
  ('shared/design-system/src/iosTest/kotlin/com/parlor/designsystem/localization/IosLanguageOverrideOwnerTest.kt',[[1,142]])]]+[
   file_record(H/'IOSAppLaunchUITests.swift.in',[[399,467],[583,662]]),file_record(H/'DSC01Probe.swift.in',[[1,178],[329,374]]),
   file_record(H/'v10_sources.py',[[1,113]]),file_record(H/'probe_validation.py',[[310,348]])],
 'representation_policy_review':{'conclusion':'ACCEPTABLE_TO_VERIFY_EXACT_OS_OBSERVED_ABSENCE_OR_VALID_PRESENT_ARRAY; no native rerun or patch approval granted here.',
  'requirements':[
   'Capture a typed/validated exact application-domain presence+array baseline only after guarded actual public English action and System/unowned/effectiveEN observation; never fabricate or copy resolved fallback into a domain.',
   'Require actual appArabic ownership to preserve that exact prior presence and array, actualSystem to restore same presence/array/nativeLTR/Englishstrings, then new beforeMain/process restart to retain it.',
   'Keep malformed/missing/ambiguous/provenance/type/route/action/order/geometry failures closed. Do not turn absent read/error into PASS or reclassify this failed09run as successful.',
   'For present baseline preserve exact array and positive ownership history. For absent baseline prove continued absence; report observed representation separately.',
   'Retain already-executed synthetic previous-present restart tests and raw native Settings evidence. They are not actual OS-created-present proof.',
   'If locally completed with actual absence, explicitly retain unobserved actualOS-created-present representation limitation; do not invent OS internal storage location, future system-change behavior, cross-platform or Store success.'
  ]},
 'cleanup':{'receipt':'PASS','stop_exits':[x['exit_code'] for x in stops],'immediate_stop_start_delay_seconds':(dt(stops[0]['started_at'])-dt(xc['finished_at'])).total_seconds(),'immediate_stop_finish_delay_seconds':(dt(stops[0]['finished_at'])-dt(xc['finished_at'])).total_seconds(),'owned_pid_start_identities_checked':len(r['ownership_observed']),'remaining_owned_identities':survivors,'owned_paths_checked':paths,'all_absent':True,'secondary_fifo_count':2,'secondary_empty_parent_count':2,'fifo_pid_start_parent_uid_birth_type_attestation_checked':True,'owned_device_absent':True,'generated_root_module_dirs_absent':16,'global_idle_claim':False,'unrelated_resources_not_touched':True},
 'research_record':file_record(BASE/'reviews/independent-apphost09-domain-research-01.json'),
 'remaining':['Draft-only reviewed representation-aware harness correction, focused controls, independent result review, separate nativeGO; no next execution authorized here.','Actual OS English->in-appAR->System->new-process restart remains unexecuted after the current predicate failure.','No complete-game/storage-resume/retained-multiplayer/physical-LAN or physical-accessibility proof from this local matrix.','Xcode26.5/17F42 simulator is not Store-qualified26.3, signing or Store evidence.'],
 'reviewer_execution':{'builds':0,'application_tests':0,'apps':0,'simulators':0,'background_workers':0,'source_edits':0,'git_store_signing_mutations':0,'process_stops':0,'build_outputs_created':0,'cleanup_required':False}}
record['arithmetic_script_sha256']=sha(Path(__file__).read_bytes())
out=BASE/'reviews/independent-dsc01-apphost09-outcome-01.json'
with out.open('x') as handle:handle.write(json.dumps(record,indent=2,ensure_ascii=False)+'\n')
print(json.dumps({'path':str(out),'sha256':sha(out.read_bytes()),'source':669,'controls':137,'copies':615,'settings':len(s),'native_geometry':len(geo),'owned_identities_checked':len(r['ownership_observed']),'actual_OS_gate':'FAIL','independent_status':record['classification']}))
