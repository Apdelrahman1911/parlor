"""Read-only native10 evidence arithmetic; never imports/runs harness or tests."""
import ast
from collections import Counter
import datetime
import hashlib
import json
import math
from pathlib import Path
import re
import stat
import subprocess

ROOT = Path('/Users/abdelrahman/Projects/parlor')
BASE = ROOT/'remediation-runs/2026-09-06-approved-policy-completion'
EV = BASE/'evidence/dsc01-apphost-10'
H = BASE/'native/dsc01_apphost_v12'
REVIEW = BASE/'reviews'
read = lambda p: json.loads(p.read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()


def require(condition, reason):
    if not condition: raise RuntimeError(reason)


def file_record(path, ranges=None):
    value = dict(path=str(path), sha256=sha(path.read_bytes()))
    if ranges is not None: value['reviewed_ranges'] = ranges
    return value


r = read(EV/'receipt.json'); f = read(BASE/'source-freeze-02.json')['source']
cm = read(H/'author-frozen-control-manifest-01.json'); im = read(EV/'input-manifest.json')
approval = read(REVIEW/'independent-v12-focused-result-native-go-01.json')
require(sha((EV/'receipt.json').read_bytes()) == '030995c5e3ed5e9e387c51eb94beeddf46328e13e51097b4aec22bbf623f30ab', 'Native10receipt changed')
require(r['source_before'] == r['source_after'] == f == im['source'], 'Execution source binding differs')
require(cm['control_sha256'] == im['control_sha256'] == r['approved_control_sha256'] == r['controls_after_sha256'] ==
        approval['control_manifest_sha256'], 'Execution control binding differs')
require(approval['native_authorization']['cycle'] == 'dsc01-apphost-10', 'Wrong singleattempt approval')
for rel, value in f['source_manifest']:
    path = ROOT/rel
    require(path.is_file() and not path.is_symlink() and sha(path.read_bytes()) == value, 'Source changed: '+rel)
require(len(f['source_manifest']) == 669 and sha(subprocess.check_output(['git','diff','--binary','HEAD','--'],cwd=ROOT)) ==
        f['diff_sha256'], 'Source count/diff differs')
for key, args in [('branch',['branch','--show-current']),('commit',['rev-parse','HEAD']),('tree',['rev-parse','HEAD^{tree}'])]:
    require(subprocess.check_output(['git']+args,cwd=ROOT,text=True).strip() == f[key], 'Git '+key+' changed')
current_controls = []
for row in cm['files']:
    path = ROOT/row['path']
    require(path.is_file() and not path.is_symlink() and sha(path.read_bytes()) == row['sha256'], 'Control changed: '+row['path'])
    current_controls.append(dict(path=row['path'],sha256=sha(path.read_bytes())))
require(len(current_controls) == 171 and sha(json.dumps(current_controls,separators=(',',':')).encode()) ==
        cm['control_sha256'], 'Current171aggregate differs')

# Independently reconstruct every retained unified hunk against frozen source.
lines = (EV/'copied-source.diff').read_text().splitlines(keepends=True)
i = 0; patches = {}; hunks = 0
while i < len(lines):
    require(lines[i].startswith('--- ') and lines[i+1].startswith('+++ audit-copy/'), 'Unexpected copy header')
    old = lines[i][4:].strip(); rel = lines[i+1].removeprefix('+++ audit-copy/').strip(); i += 2
    original = [] if old == '/dev/null' else (ROOT/rel).read_text().splitlines(keepends=True)
    result = []; cursor = 0
    while i < len(lines) and not lines[i].startswith('--- '):
        match = re.fullmatch(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*\n?',lines[i])
        require(match is not None, 'Unexpected copy hunk header')
        start = max(int(match[1])-1,0); old_count = int(match[2] or 1); new_count = int(match[4] or 1)
        i += 1; hunks += 1; require(start >= cursor, 'Overlapping copy hunk')
        result += original[cursor:start]; cursor = start; old_used = new_used = 0
        while i < len(lines) and not lines[i].startswith(('--- ','@@ ')):
            sign, value = lines[i][0], lines[i][1:]; i += 1
            require(sign in ' +-', 'Unknown copy diff line')
            if sign in ' -':
                require(original[cursor] == value, 'Frozen hunk context mismatch: '+rel)
                cursor += 1; old_used += 1
            if sign in ' +': result.append(value); new_used += 1
        require((old_used,new_used) == (old_count,new_count), 'Copy hunk counts differ')
    result += original[cursor:]; patches[rel] = ''.join(result).encode()
require(len(patches) == 13 and hunks == 18, 'Unexpected transformed path/hunk count')
copied = read(EV/'copied-source-manifest.json'); after = read(EV/'copied-source-inputs-after-build.json'); copy_hash = {}
require(len(copied) == len(after['files']) == 615, 'Final copy input count differs')
require(not any(after[key] for key in ['unexpected_build_inputs','missing_build_inputs','unexpected_source_symlinks']), 'Unexpected copy inputs')
for row in copied:
    rel = row['path']; original = (ROOT/rel).read_bytes() if (ROOT/rel).exists() else None
    require((sha(original) if original is not None else None) == row['original_sha256'], 'Copy original hash differs')
    copy_hash[rel] = sha(patches.get(rel,original)); require(copy_hash[rel] == row['copied_sha256'], 'Copy reconstructed hash differs')
for row in after['files']:
    require(row['expected_sha256'] == row['observed_sha256'] == copy_hash[row['path']] and row['unchanged'], 'Postworker copy drift')
require(sha((EV/'copied-source.diff').read_bytes()) == r['copied_source_diff_sha256'] and
        sha((EV/'copied-source-manifest.json').read_bytes()) == r['copied_source_manifest_sha256'] and
        sha((EV/'copied-source-inputs-after-build.json').read_bytes()) == r['copied_source_inputs_after_build_sha256'], 'Receipt copy hash differs')


def literals(path):
    values = {}
    for node in ast.parse(path.read_text()).body:
        if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name):
            try: values[node.targets[0].id] = ast.literal_eval(node.value)
            except (ValueError,TypeError): pass
    return values


# Literal text composition only; no harness renderer imports/calls.
v10 = literals(H/'v10_sources.py'); v12 = literals(H/'v12_sources.py')
ui = (H/'IOSAppLaunchUITests.swift.in').read_text(); start = ui.index(v10['OS_FUNCTION']); end = ui.index(v10['POST_SELECTION'],start)
rendered = (ui[:start]+v10['NEW_PREFIX']+ui[end:]).replace(v10['OLD_VERIFIED'],v10['NEW_VERIFIED'],1)
require(sha(rendered.encode()) == v12['V11_RENDERED_UI_SHA'], 'Actual preceding UIrender differs')
start = rendered.index(v12['BEGIN']); end = rendered.index(v12['END'],start); region = rendered[start:end]
for old,new in v12['REPLACEMENTS']:
    require(region.count(old)==1,'Actual V12replacement ambiguous'); region = region.replace(old,new,1)
rendered = rendered[:start]+region+rendered[end:]
require(sha(rendered.encode()) == 'f1926d606d567eeccd43721898a05a8c9394949a95f31ca3c4b2bf642e5fa20f','Actual V12render differs')
for name in ['DSC01PublicDiagnostics.swift.in','DSC01CatalogSelection.swift.in','DSC01MafiaStartSelection.swift.in',
             'DSC01OSPrerequisite.swift.in','DSC01OSAppSettings.swift.in','DSC01OSPreferenceBaseline.swift.in']:
    rendered += '\n'+(H/name).read_text()
require(rendered.encode() == patches['iosApp/iosAppUITests/IOSAppLaunchUITests.swift'], 'Copied XCTest is not exact reviewed renderer/helpers')
entry = patches['iosApp/iosApp/iOSApp.swift'].decode()
require(entry.count('init() { DSC01Probe.shared.prepare() }')==1 and entry.index('prepare()')<entry.index('var body'), 'Before-App probe ordering differs')

raw = (EV/'xcodebuild.log').read_text().splitlines(); metadata = []
for number,line in enumerate(raw,1):
    match = re.match(r'(DSC01_[A-Z_0-9]+) (\{.*\})$',line)
    if match: metadata.append((number,match[1],json.loads(match[2])))
require(not any(kind=='DSC01_PUBLIC_OBSERVATION_FAILURE' for _,kind,_ in metadata),'A bounded observation failed')
contracts_expected = {
    'DSC01_CATALOG_GEOMETRY_CONTRACT':dict(schemaVersion=1,cases=14,passed=True),
    'DSC01_MAFIA_START_GEOMETRY_CONTRACT':dict(schemaVersion=1,cases=16,passed=True),
    'DSC01_OS_PREREQUISITE_DECISION_CONTRACT':dict(schemaVersion=1,cases=24,passed=True),
    'DSC01_OS_INTERACTION_DECISION_CONTRACT':dict(schemaVersion=1,cases=24,passed=True),
    'DSC01_OS_PANE_IDENTITY_DECISION_CONTRACT':dict(schemaVersion=1,attributeCases=12,unionCases=2,identityGuardCases=2,passed=True),
    'DSC01_OS_PREFERENCE_DECISION_CONTRACT':dict(schemaVersion=1,shapeCases=14,ownershipCases=6,restoreCases=4,passed=True),
}
contracts = {}
for kind, expected in contracts_expected.items():
    found = [(line,value) for line,name,value in metadata if name==kind]
    require(len(found)==1 and found[0][1]==expected and all(type(found[0][1][k]) is type(v) for k,v in expected.items()),'Native contract absent/wrong: '+kind)
    contracts[kind] = dict(raw_line=found[0][0],value=found[0][1])

finite = lambda value: len(value)==4 and all(type(x) in (int,float) and math.isfinite(x) and abs(x)<=16384 for x in value) and value[2]>0 and value[3]>0
contains = lambda outer,inner: outer[0]<=inner[0] and outer[1]<=inner[1] and outer[0]+outer[2]>=inner[0]+inner[2] and outer[1]+outer[3]>=inner[1]+inner[3]
near = lambda left,right: len(left)==len(right) and all(abs(x-y)<1e-5 for x,y in zip(left,right))


def rect(fields,key):
    value = fields[key]
    require(value['present'] is True and value['validFiniteRectangle'] is True and finite(value['rectangle']),'Invalid observed rectangle')
    return value['rectangle']


activation = {}
for kind,game in [('DSC01_CATALOG_METADATA','whodunit'),('DSC01_CATALOG_METADATA','mafia'),('DSC01_MAFIA_START_METADATA',None)]:
    rows = [(n,value) for n,name,value in metadata if name==kind and (game is None or value['game']==game)]
    require([value['ordinal'] for _,value in rows]==list(range(1,len(rows)+1)),'Activation ordinal gap')
    before = [(n,value) for n,value in rows if value['kind']=='before-activation']; require(len(before)==1,'Repeated/missing activation')
    n,before = before[0]; fields = before['fields']
    require(all(fields[key] is True for key in ['foreground','hittable','enabled']) and not fields['keyboardPresent'] and not fields['alertPresent'],'Unsafe actual activation')
    require(fields['targetCountCapped16']==fields['overlayCountCapped16']==1,'Ambiguous actual target')
    target,window,overlay = rect(fields,'target'),rect(fields,'viewport'),rect(fields,'overlay')
    require(contains(window,overlay),'Overlay outside viewport')
    if game:
        require(fields['tabCountsCapped16']==[1,1] and fields['successorCountCapped16']==0,'Catalog context differs')
        tabs = [value['rectangle'] for value in fields['tabs']]
        require(len(tabs)==2 and all(finite(value) and contains(window,value) for value in tabs),'Invalid catalog tabs')
        top = max(window[1],overlay[1]+overlay[3])+8; bottom = min(window[1]+window[3],min(t[1] for t in tabs))-24
    else:
        top = max(window[1]+48,overlay[1]+overlay[3]+8); bottom=window[1]+window[3]-48
    usable_x=window[0]+8; usable_width=window[2]-16; x=max(target[0],usable_x); y=max(target[1],top)
    width=min(target[0]+target[2],usable_x+usable_width)-x; height=min(target[1]+target[3],bottom)-y
    require(width>=48 and height>=48,'Insufficient safe hit interior')
    interior=[x+12,y+12,width-24,height-24]; point=[interior[0]+interior[2]/2,interior[1]+interior[3]/2]
    normalized=[(point[0]-target[0])/target[2],(point[1]-target[1])/target[3]]
    require(near(interior,rect(fields,'visibleInterior')) and near(point,fields['plannedPoint']) and near(normalized,fields['normalizedPoint']) and
            near(point,fields.get('actualCoordinatePoint',fields.get('sampledCoordinatePoint'))),'Independent safe coordinate differs')
    search=[(line,value) for line,value in rows if value['kind']=='search']; final_six=search[-6:]
    require([value['fields']['index'] for _,value in final_six]==list(range(1,7)) and len({value['fields']['window'] for _,value in final_six})==1,'Incomplete final readiness window')
    for _,value in final_six:
        observed=value['fields']; require(all(observed[key] for key in ['foreground','hittable','enabled']) and not observed['alertPresent'] and not observed['keyboardPresent'],'Unready sampled target')
        require(all(near(rect(observed,key),rect(fields,key)) for key in ['target','viewport','overlay']),'Unstable activation geometry')
    after_lines=[line for line,value in rows if value['kind']=='after-activation']; successors=[(line,value) for line,value in rows if value['kind']=='successor']
    require(len(after_lines)==len(successors)==1 and n<after_lines[0]<successors[0][0],'Missing activation successor')
    if game:
        successor=successors[0][1]['fields']; require(successor['targetCountCapped16']==0 and successor['tabCountsCapped16']==[0,0] and successor['successorCountCapped16']==1,'Catalog target did not transition')
    activation[game+'-catalog' if game else 'mafia-start']=dict(rows=len(rows),search_windows=len(search)//6,
        before_line=n,after_line=after_lines[0],successor_line=successors[0][0],stable_final_six_lines=[ln for ln,_ in final_six],
        target=target,viewport=window,interior=interior,planned_point=point,normalized_point=normalized)

prereq=[(line,value) for line,name,value in metadata if name=='DSC01_OS_PREREQUISITE']
require(len(prereq)==16 and [value['ordinal'] for _,value in prereq]==list(range(1,17)),'Prerequisite receipts differ')
expected_steps=[('ownership','PASS'),('settingsRoot','observe'),('settingsRoot','PASS'),('general','observe'),('general','before-activation'),('general','after-activation'),('general','observe'),('general','PASS'),('languageRegion','observe'),('languageRegion','before-activation'),('languageRegion','after-activation'),('languageRegion','observe'),('languageRegion','PASS'),('initialEnglishPrimary','observe'),('initialEnglishPrimary','PASS'),('finalPreferredLanguages','PASS')]
require([(value['stage'],value['status']) for _,value in prereq]==expected_steps,'Prerequisite actions differ')
for _,value in prereq[-3:]:
    observed=value['fields']
    require(observed['foreground'] and not observed['keyboardPresent'] and observed['alertCountCapped16']==observed['searchFieldCountCapped16']==0 and observed['prerequisiteDisposition']=='already-satisfied','Wrong prerequisite state')
    require(all(observed[key] for key in ['englishPrimaryObserved','englishPrimaryHittable','arabicPreferredRowObserved','languageRegionTitleObserved','languageRegionTitleHittable','addLanguageSuccessorHittableEnabled']),'Preferred language identities unavailable')
    require(observed['englishPrimaryCellCountCapped16']==observed['arabicCellCountCapped16']==1,'Ambiguous preferred languages')
    window,en,ar,add=[rect(observed,key) for key in ['viewport','englishPrimaryFrame','arabicPreferredRowFrame','addLanguageSuccessorFrame']]
    require(all(contains(window,v) for v in [en,ar,add]) and en[1]+en[3]<=ar[1]+1e-5 and ar[1]+ar[3]<=add[1]+1e-5,'Preferred language visible order differs')
require(prereq[-1][1]['fields']['arabicAddedByThisPrerequisite'] is False,'Prerequisite invented Arabic addition')

reports={name:read(EV/f'probe-{name}-result.json') for name in ['settings','whodunit','mafia','os']}
require(len({value['runToken'] for value in reports.values()})==1,'Crossscenario token differs')
for name,value in reports.items():
    require(value['schemaVersion']==6 and value['scenario']==name and value['completed'] is True,'Incomplete/wrong scenario')
    rows=value['observations']; require([event['ordinal'] for event in rows]==list(range(1,len(rows)+1)),'Scenario ordinal gap')
    starts=[event for event in rows if event['phase']=='before_main']
    require(len({event['boot'] for event in starts})==len(starts),'Reused beforeMain process identity')
    for event in starts:
        require(event['controllerCreations']==0 and event['nativeDirection']=='unavailable' and json.loads(event['composition'])=={},'Not a true BEFORE-App observation')
        require(event['native']['attachments']==event['native']['disposals']==0,'BeforeMain native observer initialized early')
    require(len([event for event in rows if event['phase']=='complete'])==1,'Missing/repeated completion')
s=reports['settings']['observations']; require(len(s)==22 and len({event['boot'] for event in s})==4,'Settings execution differs')
preference_keys=['setting','preferredLanguage','ownerPresent','ownerInstalled','ownerPreviousPresent','ownerPrevious','hasAppOverride','appLanguages']
for event in s:
    n=event['ordinal']; preference=event['preferences']; composition=json.loads(event['composition'])
    if n<=3 or 9<=n<=10: expected=('system','en',False,'none',False,[],False,[])
    elif 4<=n<=8: expected=('ar','ar',True,'ar',False,[],True,['ar'])
    elif 11<=n<=13 or n>=19: expected=('system','ar',False,'none',False,[],True,['ar-EG','en'])
    else: expected=('en','en',True,'en',True,['ar-EG','en'],True,['en'])
    require(tuple(preference[k] for k in preference_keys)==expected,'Actual Settings ownership tuple differs at '+str(n))
    if event['phase']!='before_main':
        require(composition['commandOrdinal']==0 and composition['commandLanguage']==composition['commandStatus']=='none','Settings flow used synthetic setter')
        require(event['controllerCreations']==1 and event['nativeDirection']==('force_rtl' if preference['preferredLanguage']=='ar' else 'force_ltr'),'Wrong Settings native language')
        if composition['settingsMounted']: require(composition['settingsDirection']==('rtl' if preference['preferredLanguage']=='ar' else 'ltr'),'Wrong actual Settings composition direction')

local={}; window_plan=[(0,'foreground'),(0,'foreground'),(0,'landscape'),(0,'portrait'),(1,'foreground'),(1,'foreground'),(1,'landscape'),(1,'portrait'),(2,'foreground'),(2,'foreground'),(3,'foreground'),(3,'foreground')]
for name,phase in [('whodunit','public-intro'),('mafia','role-assignment')]:
    rows=reports[name]['observations']; captured=[event for event in rows if json.loads(event['composition']).get('checkpointCaptured')]
    require(len(captured)==88 and len({event['boot'] for event in rows})==1,'Local continuous observation interval differs')
    deltas=[]; windows=[]; foreground_ordinals=[]; prior_foreground=0
    for event in captured:
        composition=json.loads(event['composition'])
        require(composition['surface']==name+'-local' and composition['publicPhase']==phase and composition['disposalsSinceCapture']==0,'Local phase/disposal changed')
        require(all(composition[k] is True for k in ['sameController','sameCanonicalFlow','sameCanonicalReference','sameCanonicalValue','samePublicPhase']),'Retained local canonical identity changed')
        require(composition['gameDirection']==('rtl' if event['preferences']['preferredLanguage']=='ar' else 'ltr'),'Local Compose language differs')
        if composition['foregroundSinceCapture']>prior_foreground: foreground_ordinals.append(event['ordinal']);prior_foreground=composition['foregroundSinceCapture']
    for number,(command,context) in enumerate(window_plan,1):
        wr=[event for event in captured if event['samplingWindow']==number]
        require(len(wr)==6 and [event['samplingIndex'] for event in wr]==list(range(1,7)),'Incomplete passive window')
        for event in wr:
            composition=json.loads(event['composition'])
            require(event['samplingContext']==context and composition['commandOrdinal']==command,'Passive window plan differs')
        ds=[b['samplingElapsedMilliseconds']-a['samplingElapsedMilliseconds'] for a,b in zip(wr,wr[1:])]
        require(min(ds)>=250,'Passive observations not separated'); deltas+=ds
        windows.append(dict(window=number,ordinals=[wr[0]['ordinal'],wr[-1]['ordinal']],context=context,command=command,interval_ms=[min(ds),max(ds)]))
    final=json.loads(captured[-1]['composition'])
    require(final['backgroundSinceCapture']==final['foregroundSinceCapture']==4,'Four lifecycle cycles not observed')
    require({json.loads(event['composition'])['commandLanguage'] for event in captured if json.loads(event['composition'])['commandOrdinal']>0}=={'ar','en','system'},'Actual synthetic setter sequence differs')
    local[name]=dict(observations=len(rows),continuous_captured_rows=88,interval=[captured[0]['ordinal'],captured[-1]['ordinal']],
        public_phase=phase,passive_samples=72,windows=windows,interval_ms=[min(deltas),max(deltas)],
        post_foreground_ordinals=foreground_ordinals,lifecycle_cycles=4,disposals=0,unchanged_actual_controller_flow_reference_value_phase=True)

geometries=[]
identity_keys=['childParentIsOuter','directSubview','sameComposeController','sameComposeView','sameOuterController','sameOuterView','sameWindow','singleComposeChild']
for name,value in reports.items():
    for event in value['observations']:
        if event['phase']=='before_main': continue
        native=event['native']; geometry=native['geometry']
        require(all(native[k] is True for k in identity_keys) and native['attachments']==1 and native['disposals']==0,'Actual native containment/identity differs')
        bounds=[0,0,402,874] if geometry['orientation']=='portrait' else [0,0,874,402]
        safe=[62,0,34,0] if geometry['orientation']=='portrait' else [0,62,20,62]
        require(geometry['orientation'] in {'portrait','landscape'},'Unknown native orientation')
        require(all(geometry[k]==bounds for k in ['composeBounds','composeFrameInOuter','composeInWindow','outerBounds','outerInWindow','windowBounds']), 'Native does not fill actual window')
        require(all(geometry[k]==safe for k in ['composeSafeArea','outerSafeArea','windowSafeArea']), 'Native safearea propagation differs')
        geometries.append((geometry['orientation'],native['outerDirection']!=event['nativeDirection']))
require(len(geometries)==229,'Actual native observation count differs')

osrows=reports['os']['observations']; require(len(osrows)==20,'OS observation count differs')
osmeta=[(line,value) for line,name,value in metadata if name=='DSC01_OS_APP_INTERACTION']
require(len(osmeta)==44 and [value['ordinal'] for _,value in osmeta]==list(range(1,45)),'OS action ordinal/count differs')
public_panes=[]; audit_panes=[]; pane_names={'settings':'Settings','apps':'Apps','parlor':'Parlor','language':'Language'}
for line,value in osmeta:
    fields=value['fields']; observed=[fields] if 'pane' in fields else fields.get('visibleMarkerRows',[])
    for q in observed:
        require(q['paneCountCapped16']==1 and q['paneIdentityMatchesKnown'] is True and q['foreground'] and q['paneHittable'] and
                q['alertCountCapped16']==0 and q['keyboardPresent'] is False and contains(rect(q,'viewport'),rect(q,'paneFrame')),'Unsafe pane identity/geometry')
        if q['pane']=='audit':
            require(q['paneSelectorMethod']=='unchanged-audit-control' and q['paneKnownPropertyMatches']==[],'Reporting pane impersonates public OS pane')
            audit_panes.append(line)
        else:
            require(q['pane'] in pane_names and q['paneSelectorMethod']=='known-public-identifying-properties-union' and
                    q['paneKnownPropertyMatches']==[dict(property='identifier',knownLiteral=pane_names[q['pane']])],'Actual OS pane provenance differs')
            public_panes.append(dict(raw_line=line,pane=q['pane'],actual_property='identifier',known_literal=pane_names[q['pane']]))
require(len(public_panes)==22 and len(audit_panes)==15,'Pane coverage arithmetic differs')
actions=[]; previous=0
for control,pane in [('apps','settings'),('parlor','apps'),('language','parlor'),('english','language'),('showMarkers','audit'),('verified','audit'),('complete','audit')]:
    before=[(line,value) for line,value in osmeta if value['status']=='before-activation' and value['fields'].get('control')==control]
    afters=[(line,value) for line,value in osmeta if value['status']=='after-activation' and value['fields'].get('control')==control]
    require(len(before)==len(afters)==1,'Repeated/missing OS/reporting action')
    line,before=before[0]; after_line,after_value=afters[0]; q=before['fields']; target=rect(q,'targetFrame')
    require(q['pane']==pane and q['targetCountCapped16']==1 and q['targetHittable'] and q['targetEnabled'] and
            q['selectorKind']==('audit-button' if pane=='audit' else 'known-cell') and contains(rect(q,'viewport'),target),'Invalid actual action eligibility')
    require(after_value['fields']==dict(activationCount=1,control=control,expectedSourcePane=pane),'Invalid action count/source')
    observed=[(n,value) for n,value in osmeta if previous<value['ordinal']<before['ordinal'] and value['status']=='observe' and value['fields'].get('control')==control]
    samples=observed[-3:]; require(len(samples)==3 and len(q['stableFrames'])==3,'Missing three stable action samples')
    for (_,value),frame in zip(samples,q['stableFrames']):
        v=value['fields'];require(v['targetCountCapped16']==1 and v['targetHittable'] and v['targetEnabled'] and v['pane']==pane and
            frame['present'] and frame['validFiniteRectangle'] and finite(frame['rectangle']) and
            all(abs(a-b)<=0.5 for a,b in zip(rect(v,'targetFrame'),target)) and all(abs(a-b)<=0.5 for a,b in zip(frame['rectangle'],target)), 'Action geometry unstable')
    require(line<after_line and previous<before['ordinal']<after_value['ordinal'],'Reordered actions')
    actions.append(dict(control=control,pane=pane,before_line=line,after_line=after_line,stable_last_three_lines=[n for n,_ in samples],target=target,activation_count=1))
    previous=after_value['ordinal']
marker_success=next((line,value) for line,value in osmeta if value['stage']=='markerControls' and value['status']=='PASS')
marker_fields=marker_success[1]['fields']['visibleMarkerRows']
require([v['control'] for v in marker_fields]==['noRow','noSelection','verified'],'Marker row ordering differs')
marker_rects=[rect(v,'targetFrame') for v in marker_fields]
require(all(a[3]>=44 for a in marker_rects) and all(a[1]+a[3]<=b[1] for a,b in zip(marker_rects,marker_rects[1:])),'Marker controls clipped/overlapping')
for stage,sequence,completed in [('marker',18,False),('complete',19,True)]:
    events=[(line,value) for line,value in osmeta if value['stage']==stage and value['status']=='PASS']
    require(len(events)==1 and events[0][1]['fields']==dict(completed=completed,osDisposition='verified-english',sequence=sequence),'Reporting not bound to durable state')
    require(osrows[sequence-1]['osDisposition']=='verified-english' and (osrows[sequence-1]['phase']=='complete') is completed,'Marker/completion contradicts durable source')

stages=[(line,value) for line,name,value in metadata if name=='DSC01_OS_PREFERENCE_BASELINE']
require(len(stages)==4 and [value['stage'] for _,value in stages]==['baseline','arabic','system','restart'],'V12stage order differs')
boot0,boot1,boot2=[osrows[n-1]['boot'] for n in [1,6,14]]
require(len({boot0,boot1,boot2})==3,'OS process recreation not observed')
unowned_absent=dict(setting='system',preferredLanguage='en',ownerPresent=False,ownerInstalled='none',ownerPreviousPresent=False,ownerPrevious=[],hasAppOverride=False,appLanguages=[])
owned_arabic=dict(setting='ar',preferredLanguage='ar',ownerPresent=True,ownerInstalled='ar',ownerPreviousPresent=False,ownerPrevious=[],hasAppOverride=True,appLanguages=['ar'])
previous_synthetic=dict(setting='system',preferredLanguage='ar',ownerPresent=False,ownerInstalled='none',ownerPreviousPresent=False,ownerPrevious=[],hasAppOverride=True,appLanguages=['ar-EG','en'])
for event in osrows:
    ordinal=event['ordinal']; composition=json.loads(event['composition'])
    expected=previous_synthetic if ordinal<=5 else owned_arabic if ordinal in [9,10] else unowned_absent
    require(event['preferences']==expected,'Exact OS ownership/preference row differs: '+str(ordinal))
    require(event['boot']==(boot0 if ordinal<=5 else boot1 if ordinal<=13 else boot2),'OS boot ordering differs')
    require(event['fixture']==('continue' if ordinal>=14 else 'existing'),'Unexpected synthetic reseeding during OSscenario')
    if ordinal>=6 and event['phase']!='before_main':
        require(composition['commandOrdinal']==0 and composition['commandLanguage']==composition['commandStatus']=='none','PostOS step used synthetic language setter')
        require(event['nativeDirection']==('force_rtl' if ordinal in [9,10] else 'force_ltr'),'Wrong postOS native language')
        if ordinal not in [7,15]:require(composition['settingsMounted'] and composition['settingsDirection']==('rtl' if ordinal in [9,10] else 'ltr'),'Actual localized Settings composition missing')
require(osrows[5]['phase']==osrows[13]['phase']=='before_main','Missing postchoice/restart BEFORE-App')
stage_proof=[]
for (line,value),sequence in zip(stages,[7,10,12,17]):
    require(value==dict(schemaVersion=1,stage=value['stage'],beforeChoiceSequence=4,beforeChoiceBoot=boot0,baselineSequence=7,baselineBoot=boot1,
            sequence=sequence,boot=boot2 if sequence==17 else boot1,hasAppOverride=False,appLanguages=[]),'Stage baseline silently rebased')
    event=osrows[sequence-1];require(event['phase']=='sample' and event['osDisposition']=='opened','Stage not actual preterminal sample')
    stage_proof.append(dict(raw_line=line,stage=value['stage'],sequence=sequence,boot=value['boot'],preferences=event['preferences'],native_direction=event['nativeDirection'],composition=json.loads(event['composition'])))
require(actions[3]['after_line']<stages[0][0]<stages[-1][0]<actions[4]['stable_last_three_lines'][0],'Actual action/stage/report ordering differs')
require(contracts['DSC01_OS_PREFERENCE_DECISION_CONTRACT']['raw_line']<actions[3]['before_line'],'Native V12contract did not precede actual action')
gate=r['os_settings_gate']
require(gate==r['dsc01_matrix']['os_settings_gate'] and gate['status']=='PASS' and gate['observed_os_preference_representation']=='ABSENT' and
        gate['actual_present_os_preference_coverage'] is False and gate['original_present_oracle_executed'] is False and
        gate['exact_presence_and_array_restored'] is True,'Receipt misreports measured OS representation')
require(r['v10_os_interaction_proof']['original_present_only_os_oracle_executed'] is False and
        r['v10_os_interaction_proof']['supplied_os_preference_oracle']=='v12-exact-post-choice-presence-array' and
        r['v10_os_interaction_proof']['supplied_v12_os_phase_oracles_required'] is True,'Historical oracle execution falsely claimed')
require(r['dsc01_matrix']['actual_settings_and_local_sessions_gate']=='PASS' and r['dsc01_matrix']['retained_multiplayer_host_gate']=='NOT_RUN','Matrix scope differs')

summary=read(EV/'xcresult-summary.json'); tests=[]


def walk_test(node):
    if node.get('nodeType')=='Test Case':tests.append(dict(id=node['nodeIdentifier'],result=node['result'],duration_seconds=node['durationInSeconds']))
    for child in node.get('children',[]):walk_test(child)


for node in read(EV/'xcresult-tests.json')['testNodes']:walk_test(node)
expected_methods={'ComposeContainerViewControllerTests/'+name+'()' for name in ['testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView','testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections','testSystemDecorationAndOrientationPoliciesStillBelongToTheChild','testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce']}
expected_methods.add('IOSAppLaunchUITests/testDSC01ActualSettingsLocalSessionsAndOSInvestigation()')
require(summary['result']=='Passed' and summary['totalTestCount']==summary['passedTests']==5 and summary['failedTests']==summary['skippedTests']==summary['expectedFailures']==0 and summary['testFailures']==[],'XCTest summary not exactly5passes')
require(len(tests)==5 and {test['id'] for test in tests}==expected_methods and all(test['result']=='Passed' for test in tests),'Actual XCTest descriptors differ')
device=summary['devicesAndConfigurations'][0]['device']
require(len(summary['devicesAndConfigurations'])==1 and device['deviceId']==r['owned_uuid'] and device['architecture']=='arm64' and device['platform']=='iOS Simulator' and device['osVersion']=='26.5','Actual native device binding differs')
raw_test_passes=[]
for line in raw:
    match=re.match(r"Test Case '-\[iosAppUITests\.([^ ]+) ([^]]+)\]' passed \(([0-9.]+) seconds\)\.",line)
    if match:raw_test_passes.append(match[1]+'/'+match[2]+'()')
success_lines=[index for index,line in enumerate(raw) if line.strip()=='** TEST SUCCEEDED **']
require(len(raw_test_passes)==5 and set(raw_test_passes)==expected_methods and len(success_lines)==1 and
        all(line.strip() in {'','Testing started'} for line in raw[success_lines[0]+1:]) and
        not any('** TEST FAILED **' in line for line in raw),'Raw XCTest pass lines disagree')
require(r['xcodebuild_exit_code']==0 and r['status']==r['runtime_evidence_status']==r['cleanup_status']=='PASS' and 'error' not in r,'Native execution/cleanup failed')

# Inspect only exact completed-cycle PID/start identities and ownership paths.
processes={}
for line in subprocess.check_output(['ps','-axo','pid=,lstart='],text=True).splitlines():
    pieces=line.split(None,1)
    if len(pieces)==2:processes[int(pieces[0])]=' '.join(pieces[1].split())
survivors=[value for value in r['ownership_observed'] if processes.get(value['pid'])==' '.join(value['start'].split())]
require(not survivors,'Exact owned process identity remains')
paths=[r['owned_temporary_directory'],r['copy_only_build_root'],str(Path(r['owned_temporary_directory'])/'DerivedData')]+r['secondary_cleanup']['removed']
require(len(paths)==7 and all(not Path(path).exists() and not Path(path).is_symlink() for path in paths),'Owned generated output remains')
fifo=read(EV/'secondary-fifo-ownership-final.json'); owned={(value['pid'],value['start']) for value in r['ownership_observed']}
require(len(fifo['records'])==2 and not fifo['pending'] and fifo['removed']==r['secondary_cleanup']['removed'],'Secondary FIFO cleanup differs')
for value in fifo['records']:
    require((value['process']['pid'],value['process']['start']) in owned and (value['parent']['pid'],value['parent']['start']) in owned,'Secondary FIFO lacks exact worker/parent identity')
    require(stat.S_ISFIFO(value['metadata'][-1]['mode']) and all(item['uid']==501 and item['birth_at']>=fifo['cycle_started'] for item in value['metadata']),'FIFO type/UID/creation attestation differs')
require(not any(r[key] for key in ['cleanup_errors','owned_processes_remaining','unknown_holders','remaining_outputs','secondary_attestation_errors','unexpected_original_outputs_preserved','deferred_signals']),'Cleanup incomplete/uncertain')
require(all(value['status']=='PASS' for value in r['finalization_stages']),'Finalizer stage did not pass')
require(read(EV/'owned-device-after-delete.json')=={'matches':[]},'Owned simulator remains registered')
device_path=Path.home()/'Library/Developer/CoreSimulator/Devices'/r['owned_uuid']
require(not device_path.exists() and not device_path.is_symlink(),'Owned simulator files remain')
stops=[value for value in r['commands'] if value['command']==['./gradlew','--stop']]
xcode=[value for value in r['commands'] if value['command'][0]=='xcodebuild']; require(len(xcode)==1,'Unexpected Xcode invocation')
require(len(stops)==2 and all(value['exit_code']==0 for value in stops),'Gradle stops incomplete')
require((EV/'embedded-gradle-stop.txt').read_text()=='build_exit=0\nstop_exit=0\n','Nested Gradle failure/stop propagation differs')
modules=[p.parent for pat in ['shared/*/build.gradle.kts','game-modes/*/build.gradle.kts'] for p in ROOT.glob(pat)]+[ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention']
require(len(modules)==16 and all(not (p/'build').exists() and not (p/'build').is_symlink() for p in modules),'Unnecessary module/root build output remains')
dt=datetime.datetime.fromisoformat

report=dict(
    schema_version=1,reviewer='/root/release_fix_review',author='/root/native_fix_review',recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    classification='PASS — source-bound complete owned-iOS-Simulator DS-C01 matrix, including measured ABSENT OS baseline restoration. Scoped DS-C01 local repair completion supported; actual OS-created PRESENT counterpart remains a named TEST/EVIDENCE GAP.',
    scoped_ds_c01_completion_approved=True,whole_project_or_store_readiness_approved=False,
    source={key:f[key] for key in ['branch','commit','tree','diff_sha256','source_manifest_sha256']},source_input_count=669,
    evidence_sha256={str(path.relative_to(ROOT)):sha(path.read_bytes()) for path in [EV/'receipt.json',EV/'xcodebuild.log',EV/'xcresult-summary.json',EV/'xcresult-tests.json',EV/'copied-source.diff',EV/'copied-source-manifest.json',EV/'copied-source-inputs-after-build.json',EV/'secondary-fifo-ownership-final.json',*[EV/f'probe-{name}-result.json' for name in reports],H/'author-frozen-control-manifest-01.json']},
    controls=dict(count=171,aggregate_sha256=cm['control_sha256'],all_rehashed_unchanged=True,source_inputs_rehashed_unchanged=669,tracked_diff_unchanged=True),
    copy_provenance=dict(original_inputs=613,final_inputs=615,paths=13,hunks=18,all_hunks_reconstructed_against_frozen_source=True,
        all615copied_and_post_worker_hashes_matched=True,actual_copied_ui_equals_literal_renderer_and_reviewed_helpers=True,
        unexpected_missing_or_symlink_inputs=[],limitation='Build copy was correctly deleted after results; retained complete diff reconstructs source. Artifact receipt excludes Parlor.debug.dylib and is not complete binary-artifact provenance.'),
    xctest=dict(result='PASS',xcode_exit=0,runner_exit=0,passed=5,failed=0,skipped=0,tests=tests,actual_device=device,
        raw_passes_match_structured_descriptors=True,scope='Four production UIKit-wrapper fixture tests and one659second fullmatrix XCTest in a source-bound instrumented Debug copy. Native helper contracts execute inside that matrix; they are not additional XCTest methods.'),
    native_contracts=contracts,
    settings=dict(status='PASS',observations=22,boots=list(dict.fromkeys(event['boot'] for event in s)),zero_synthetic_language_commands=True,
        first_sequence=['fresh SystemEN','actual appArabic','fresh restartArabic','actual SystemEN'],second_sequence=['synthetic prior ar-EG/en','actual appEnglish','fresh restartEnglish','actual System restores exact ar-EG/en'],
        limitation='Previous-present fixture is synthetic app-domain input; real Foundation/persisted settings and actual screen actions execute, but it is not OS-created PRESENT evidence.'),
    local_games=local,native_geometry=dict(rows=229,orientation_counts=dict(Counter(v[0] for v in geometries)),
        opposing_outer_and_compose_direction_rows=sum(v[1] for v in geometries),all_eight_identity_flags_true=True,attachments=1,disposals=0,
        full_window_bounds_and_safeareas_independently_recomputed=True),
    actual_catalog_and_start=activation,
    public_os_prerequisite=dict(status='PASS',disposition='already-satisfied',rows=16,raw_lines=[prereq[0][0],prereq[-1][0]],
        actual_english_primary_and_arabic_order_geometry_checked=True,arabic_added=False,source_of_existing_languages_not_inferred=True,
        only_navigation_actions=['General','Language & Region'],no_add_search_primary_language_mutation=True),
    public_os_actions=dict(status='PASS',rows=44,actual_four_route_actions=actions[:4],terminal_reporting_actions=actions[4:],
        known_public_pane_rows=public_panes,audit_reporting_pane_rows=15,one_activation_each=True,three_stable_samples_each=True,
        three44point_marker_rows_verified=True,marker_sequence=18,complete_sequence=19,coordinate_fallback=False),
    os_baseline_evidence=dict(status='PASS',observed_representation='ABSENT',baseline_pair=dict(hasAppOverride=False,appLanguages=[]),
        os_probe=file_record(EV/'probe-os-result.json'),actual_runtime_receipt=file_record(EV/'receipt.json'),
        boot_before_choice=boot0,boot_after_choice=boot1,boot_after_explicit_restart=boot2,
        before_choice_ordinal=4,before_app_after_choice_ordinal=6,before_app_restart_ordinal=14,ordered_proof=stage_proof,
        actual_english_settings_before_arabic_ordinal=8,actual_english_settings_after_restart_ordinals=[16,17],
        before_app_controller_creations=0,post_os_synthetic_language_setters=0,
        original_present_only_oracle_executed=False,unchanged_v10_v11_guards_executed=True,
        unobserved_counterpart=dict(id='ios-actual-os-language-baseline-counterpart',classification='TEST/EVIDENCE GAP',gate_status='BLOCKED',
            representation='Actual OS-created PRESENT app-domain preference and complete Arabic/System/restart restoration',
            reason='This genuine OS English action produced typed ABSENT before app initialization. Synthetic PRESENT fixtures are not real OS-created PRESENT evidence.',
            ownership='Platform verification owner; investigate explicitly owned local Simulator first, controlled devices if needed. Not automatically physical-only; no source defect established.')),
    source_reasoning=[
        'Copied iOSApp.init prepares before original ContentView/Koin/Compose; recovered existing and continue branches only read language preferences. Only previous-ar fixture writes the synthetic priorarray, during the separate Settings scenario.',
        'After genuine public English action, before-main6 already records System/unowned/ABSENT/effectiveEN. Production owner.release returns when no owner; no fallback is promoted to an app-domain preference.',
        'Actual appArabic creates its own override with ownerPreviousPresent=false and empty previousarray. Actual System releases only that owned override and returns ABSENT with English strings and native/ComposeLTR.',
        'Explicit fresh restart before-main14 plus actual EnglishSettings16/17 preserves that exact pair without synthetic reseeding; raw stageboots/sequences/action ordering and fullgeometry corroborate the result.',
        'Existing source ownership handler supports synthetic previousPRESENT restoration; direct Settings/restart branch again executes with ar-EG/en. Scope does not claim that those bytes were authored by iOSSettings.',
        'Whodunit/Mafia retained local controllers/canonical references/values/public phases remain stable across three realStore language setter invocations and four actual lifecycle cycles each; this is not a complete-game or persistence-resume test.'
    ],
    reviewed_surrounding_source=[file_record(ROOT/path,ranges) for path,ranges in [
        ('shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/IosLanguageOverrideOwner.kt',[[1,61]]),
        ('shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/LocalAppLocale.ios.kt',[[1,83]]),
        ('iosApp/iosApp/ContentView.swift',[[1,62]]),('iosApp/iosApp/ComposeContainerViewController.swift',[[1,48]])]],
    cleanup=dict(status='PASS',stop_exits=[value['exit_code'] for value in stops],nested_build_and_stop_exit=[0,0],
        immediate_stop_start_delay_seconds=(dt(stops[0]['started_at'])-dt(xcode[0]['finished_at'])).total_seconds(),
        immediate_stop_finish_delay_seconds=(dt(stops[0]['finished_at'])-dt(xcode[0]['finished_at'])).total_seconds(),
        owned_pid_start_identities_checked=len(r['ownership_observed']),remaining_owned_identities=survivors,
        owned_paths_checked=paths,all_absent=True,secondary_fifo_count=2,secondary_empty_parent_count=2,
        exact_worker_parent_uid_birth_filetype_attestation_checked=True,owned_simulator_absent=True,generated_module_root_dirs_absent=16,
        global_idle_claim=False,unrelated_user_resources_touched=False),
    prior_native09=dict(result='FAIL',review=file_record(REVIEW/'independent-dsc01-apphost09-outcome-01.json'),
        explanation='Prior mandatory-PRESENT predicate stopped the matrix. Its incomplete outcome is never retroactively changed by successful native10.'),
    research=file_record(REVIEW/'independent-apphost09-domain-research-01.json'),
    limitations=[
        'This supports scoped DS-C01 local correction, not whole-project clean/READY or Store-readiness approval.',
        'Actual OS-created PRESENT counterpart remains named and locally investigable. Current fullcycle proves only actual ABSENT; synthetic PRESENT runtime/unit/source coverage remains separately described.',
        'Native language proof uses current source-bound Debug simulatorcopy and observedcontroller/publicmetadata; not a signed/release app runtime, complete binary hashchain, physicalLAN, physicala11y or all navigation gestures.',
        'Artifact receipt omits Parlor.debug.dylib; binary completeness cannot be asserted after proper cleanup. Separate existing Release arm64Simulator build/artifact review is linkage/packaging evidence, not language runtime.',
        'Xcode26.5/17F42 and simulator26.5 are not Store-qualified26.3/17C529. No signing, Store, legal/identity owner actions authorized.',
        'Local continuity is public-intro Whodunit/role-assignment Mafia, not completegame/playback/snapshotrestore. Retained multiplayer gate is NOT_RUN in this nativecycle; other focused host JVM evidence must stay separately attributed.',
        'Two raw AppIntentsmetadata warnings state no AppIntents.framework dependency; no hidden-warning suppression used. IOS-R1evidencegap and WD-C4productdecision are not closed by these results.'
    ],
    reviewer_execution=dict(builds=0,application_tests=0,apps=0,simulators=0,background_workers=0,production_or_test_edits=0,
        git_store_signing_mutations=0,process_stops=0,generated_build_outputs=0,cleanup_required=False)
)
report['reviewer_arithmetic_correction']='Initial read-only metadata reconciliation halted before writing a report because it expected TEST SUCCEEDED to be the last raw line. Actual immutable Xcode log has one success sentinel followed only by a blank line and Testing started. All5raw descriptor passes and structured results matched. Reconciler now checks the unique success sentinel and permits exactly that observed trailing logger text; no test/harness/application code or evidence changed.'
report['arithmetic_script_sha256']=sha(Path(__file__).read_bytes())
out=REVIEW/'independent-dsc01-apphost10-outcome-01.json'
with out.open('x') as handle:handle.write(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(dict(path=str(out),sha256=sha(out.read_bytes()),classification=report['classification'],
    tests=5,source=669,controls=171,copies=615,native_geometry=229,owned_identities=len(r['ownership_observed']))))
