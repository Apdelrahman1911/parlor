#!/usr/bin/env python3
"""One remediation-owned build lane with mandatory evidence/stop/cleanup finalization."""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent

def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
FINALIZING = False
DEFERRED_SIGNALS = []
OWNED_PROCESSES = {}
OWNED_GROUPS = set()
CYCLE_MARKER = ''

def processes():
    completed = subprocess.run(['ps','-axo','pid=,ppid=,pgid=,lstart=,command='], text=True, capture_output=True, check=True)
    records = {}
    for line in completed.stdout.splitlines():
        fields = line.strip().split(None, 8)
        if len(fields) == 9:
            pid, parent, group = map(int, fields[:3])
            records[pid] = {'pid':pid, 'parent':parent, 'group':group, 'started':' '.join(fields[3:8]), 'command':fields[8]}
    return records

def track_processes():
    current = processes()
    for _ in range(4):
        added = False
        for pid, record in current.items():
            if pid == os.getpid(): continue
            if record['group'] in OWNED_GROUPS or (record['parent'] in OWNED_PROCESSES and current.get(record['parent'],{}).get('started')==OWNED_PROCESSES[record['parent']]['started']) or (CYCLE_MARKER and CYCLE_MARKER in record['command']):
                if pid not in OWNED_PROCESSES:
                    OWNED_PROCESSES[pid] = record
                    added = True
        if not added: break
    return current

def invoke(command, logfile, env, timeout=None):
    with logfile.open('w') as log:
        p=subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        OWNED_GROUPS.add(p.pid)
        started=time.monotonic()
        try:
            while True:
                track_processes()
                if timeout is not None and time.monotonic()-started >= timeout:
                    raise subprocess.TimeoutExpired(command,timeout)
                try: return p.wait(timeout=0.5)
                except subprocess.TimeoutExpired: pass
        except BaseException:
            # Only this invocation's owned group, never another task's PID.
            if p.poll() is None:
                os.killpg(p.pid, signal.SIGTERM)
                try: p.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    os.killpg(p.pid, signal.SIGKILL); p.wait()
            raise

def stop_owned_workers():
    def survivors():
        current=track_processes()
        return [record for pid,record in current.items() if pid in OWNED_PROCESSES and record['started']==OWNED_PROCESSES[pid]['started']]
    remaining=survivors()
    for _ in range(20):
        if not remaining: break
        time.sleep(0.25); remaining=survivors()
    terminated=[]
    for sig in (signal.SIGTERM, signal.SIGKILL):
        for record in remaining:
            current=processes().get(record['pid'])
            if current and current['started']==record['started'] and current['command']==record['command']:
                os.kill(record['pid'],sig); terminated.append({'pid':record['pid'],'signal':int(sig),'started':record['started']})
        if remaining: time.sleep(1)
        remaining=survivors()
    return {'terminated_owned_workers':terminated,'remaining_owned_workers':remaining}

def owned_outputs():
    modules=[p.parent for p in ROOT.glob('shared/*/build.gradle.kts')]
    modules += [p.parent for p in ROOT.glob('game-modes/*/build.gradle.kts')]
    modules += [ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention']
    return [p/'build' for p in modules]

def identity():
    result = {k:subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip() for k,args in {'commit':['rev-parse','HEAD'],'tree':['rev-parse','HEAD^{tree}'],'branch':['branch','--show-current'],'tracked_status':['status','--porcelain=v1','--untracked-files=no']}.items()}
    diff = subprocess.check_output(['git','diff','--binary','HEAD','--'],cwd=ROOT)
    result['diff_sha256'] = hashlib.sha256(diff).hexdigest()
    paths = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0')
    records = []
    for rel in sorted(set(paths)):
        if not rel or rel.startswith(('audit-runs/', 'remediation-runs/', 'project-code-audit/', 'design/')):
            continue
        if any(s in rel.lower() for s in ('.keystore','.p12','.p8','.mobileprovision','credentials.json','service-account','local.properties')):
            continue
        path=ROOT/rel
        if path.is_file() and not path.is_symlink():
            records.append((rel,hashlib.sha256(path.read_bytes()).hexdigest()))
    result['source_manifest'] = records
    result['source_manifest_sha256'] = hashlib.sha256(json.dumps(records, separators=(',', ':')).encode()).hexdigest()
    return result

def runner_identity(native):
    # These task-owned controls are deliberately outside the application source
    # manifest, but the init script is build-consumed and selects the device.
    paths = [OUT/'run_gradle_cycle.py']
    if native:
        paths += [OUT/'owned_ios_simulator.py', OUT/'owned_ios_simulator.init.gradle']
    records = []
    for path in paths:
        if path.is_symlink() or not path.is_file():
            raise RuntimeError(f'Missing or symlinked build-lane control: {path.name}')
        records.append((str(path.relative_to(ROOT)), hashlib.sha256(path.read_bytes()).hexdigest()))
    return {'manifest': records,
            'manifest_sha256': hashlib.sha256(json.dumps(records, separators=(',', ':')).encode()).hexdigest()}

def collect_reports(dest, outputs):
    receipts=[]
    copied=[]
    for output in outputs:
        for p in output.glob('test-results/**/TEST-*.xml'):
            relative=p.relative_to(ROOT)
            target=dest/'reports'/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(p,target)
            copied.append((relative,target))
        for pattern in ['reports/detekt/*.xml','reports/lint-results-*.xml']:
            for p in output.glob(pattern):
                target=dest/'reports'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    # Preserve all evidence before parsing, so a host XML-library error cannot
    # lose subsequent reports when mandatory cleanup runs.
    for relative,target in copied:
            try:
                tree=ET.parse(target).getroot()
                receipts.append({'file':str(relative),'suite':tree.get('name'),'tests':tree.get('tests'),'failures':tree.get('failures'),'errors':tree.get('errors'),'skipped':tree.get('skipped'),'cases':[{'name':n.get('name'),'classname':n.get('classname'),'skipped':n.find('skipped') is not None,'failed':n.find('failure') is not None or n.find('error') is not None} for n in tree.findall('testcase')]})
            except Exception as e: receipts.append({'file':str(relative),'parse_error':str(e)})
    (dest/'test-receipts.json').write_text(json.dumps(receipts,indent=2)+'\n')

def inspect_generated_artifacts(dest):
    """Keep compact receipts, not large unsigned bundles or linked frameworks."""
    records=[]
    for p in (ROOT/'composeApp/build/outputs').glob('**/*.aab'):
        digest=hashlib.sha256()
        with p.open('rb') as f:
            for block in iter(lambda:f.read(1024*1024),b''): digest.update(block)
        with zipfile.ZipFile(p) as archive:
            entries=[{'path':i.filename,'bytes':i.file_size,'compressed_bytes':i.compress_size,'crc32':i.CRC} for i in archive.infolist()]
        records.append({'path':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'sha256':digest.hexdigest(),'entries':entries})
    for p in (ROOT/'composeApp/build/intermediates/merged_manifests').glob('release/**/AndroidManifest.xml'):
        target=dest/'artifacts'/p.relative_to(ROOT)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(p,target)
    for p in (ROOT/'composeApp/build/bin').glob('*/releaseFramework/ComposeApp.framework/Info.plist'):
        target=dest/'artifacts'/p.relative_to(ROOT)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(p,target)
        binary=p.parent/'ComposeApp'
        if binary.is_file():
            digest=hashlib.sha256()
            with binary.open('rb') as f:
                for block in iter(lambda:f.read(1024*1024),b''):digest.update(block)
            records.append({'path':str(binary.relative_to(ROOT)),'bytes':binary.stat().st_size,'sha256':digest.hexdigest(),'file':subprocess.run(['file',str(binary)],text=True,capture_output=True).stdout.strip()})
    (dest/'artifact-receipts.json').write_text(json.dumps(records,indent=2)+'\n')

def main():
    global FINALIZING, CYCLE_MARKER
    if len(sys.argv)<3: raise SystemExit('run_gradle_cycle.py NAME TASK [TASK/FLAG ...] or NAME --command COMMAND ...')
    name=sys.argv[1]
    if not name.replace('-','').isalnum(): raise SystemExit('Invalid cycle name')
    dest=OUT/'evidence'/name
    if dest.exists(): raise SystemExit('Refusing to overwrite cycle evidence')
    with (OUT/'build-lane.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        outputs=owned_outputs()
        existing=[str(p.relative_to(ROOT)) for p in outputs if p.exists() or p.is_symlink()]
        if existing: raise SystemExit(f'Pre-existing outputs require separate ownership review: {existing}')
        before=processes()
        conflicts=[v['pid'] for v in before.values() if 'org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.13' in v['command']]
        if conflicts: raise SystemExit(f'Unowned Gradle 8.13 daemon(s): {conflicts}; will not stop or compete with another task')
        dest.mkdir(parents=True)
        env=os.environ.copy()
        for key in list(env):
            if key.startswith(('PARLOR_ANDROID_', 'MOBILE_RELEASE_')): env.pop(key)
        env['JAVA_HOME']=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()
        env['PYTHONDONTWRITEBYTECODE']='1'
        env['PATH']=env['JAVA_HOME']+'/bin:/usr/bin:'+env.get('PATH','')
        scratch=dest/'scratch'; scratch.mkdir()
        (scratch/'tmp').mkdir()
        env['TMPDIR']=str(scratch/'tmp')
        env['PYTHONPYCACHEPREFIX']=str(scratch/'pycache')
        heap=env.get('PARLOR_REMEDIATION_GRADLE_HEAP','3g')
        if heap not in {'3g','6g'}: raise SystemExit('Unreviewed remediation heap limit')
        CYCLE_MARKER=f'-Dparlor.remediation.cycle={name}'
        common=['--no-daemon','--no-parallel','--max-workers=1','--no-configuration-cache','--dependency-verification=strict',f'-Dorg.gradle.jvmargs=-Xmx{heap} -Dfile.encoding=UTF-8 -XX:+UseParallelGC {CYCLE_MARKER}','-Pkotlin.compiler.execution.strategy=in-process','-Pparlor.android.signing.storeFile=','-Pparlor.android.signing.storePassword=','-Pparlor.android.signing.keyAlias=','-Pparlor.android.signing.keyPassword=']
        native = sys.argv[2]=='--ios-simulator'
        task_args=sys.argv[3:] if native else sys.argv[2:]
        command=sys.argv[3:] if sys.argv[2]=='--command' else ['./gradlew',*task_args,*common]
        if native:
            command.extend(['-I',str(OUT/'owned_ios_simulator.init.gradle')])
        if not command: raise SystemExit('Missing command')
        receipt={'cycle':name,'started_at':now(),'source_before':identity(),'runner_before':runner_identity(native),'command':command,'env_policy':'JDK21, system Python, strict verification, signing env removed/empty Gradle signing props, cycle-owned TMPDIR/pycache','outputs_before':existing,'status':'RUNNING'}
        def save(): (dest/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        save()
        error=None
        simulator=None
        try:
            if native:
                from owned_ios_simulator import OwnedIosSimulator
                simulator=OwnedIosSimulator(dest,name,env,invoke)
                simulator.create_and_boot()
            receipt['exit_code']=invoke(command,dest/'gradle.log',env)
        except BaseException as e:
            error=e; receipt['error']=type(e).__name__
        finally:
            FINALIZING=True  # Defer repeated cancellation until all finalizers have run.
            receipt['finished_at']=now()
            cleanup_errors=[]
            def attempt(label, operation):
                try: return operation()
                except BaseException as e:
                    cleanup_errors.append(f'{label}: {type(e).__name__}: {e}')
                    return None
            receipt['stop_exit_code']=attempt('Gradle stop',lambda:invoke(['./gradlew','--stop'],dest/'stop.log',env))
            receipt['stopped_at']=now()
            if simulator is not None:
                attempt('Owned simulator cleanup',simulator.cleanup)
            worker_result=attempt('Owned process termination',stop_owned_workers)
            receipt['workers']=worker_result
            safe_to_clean=worker_result is not None and not worker_result['remaining_owned_workers']
            # If evidence collection fails, retain outputs explicitly for another
            # inspection, instead of destroying the only reports and claiming PASS.
            capture_errors=len(cleanup_errors)
            attempt('Collect reports',lambda:collect_reports(dest,outputs))
            attempt('Inspect artifacts',lambda:inspect_generated_artifacts(dest))
            captured=len(cleanup_errors)==capture_errors
            removed=[]
            retained=[]
            for path in outputs:
                if not path.exists() and not path.is_symlink(): continue
                if not safe_to_clean or not captured:
                    retained.append({'path':str(path.relative_to(ROOT)),'reason':'Required evidence capture or owned-process shutdown incomplete; inspect before cleanup'})
                    continue
                def remove(p=path):
                    if p.is_symlink(): raise RuntimeError(f'Refusing symlink cleanup: {p}')
                    shutil.rmtree(p); removed.append(str(p.relative_to(ROOT)))
                attempt('Remove '+str(path.relative_to(ROOT)), remove)
            receipt['removed_outputs']=removed
            receipt['retained_outputs']=retained
            if safe_to_clean:
                attempt('Remove cycle scratch',lambda:shutil.rmtree(scratch))
            receipt['remaining_outputs']=[str(p.relative_to(ROOT)) for p in outputs if p.exists() or p.is_symlink()]
            receipt['cleanup_completed_at']=now()
            receipt['source_after']=attempt('Final source identity',identity)
            receipt['source_changed_during_cycle']=receipt['source_after'] is None or receipt['source_before']['source_manifest_sha256']!=receipt['source_after']['source_manifest_sha256']
            receipt['runner_after']=attempt('Final build-lane identity',lambda:runner_identity(native))
            receipt['runner_changed_during_cycle']=receipt['runner_after'] is None or receipt['runner_before']['manifest_sha256']!=receipt['runner_after']['manifest_sha256']
            receipt['cleanup_errors']=cleanup_errors
            receipt['deferred_signals']=DEFERRED_SIGNALS
            receipt['status']='PASS' if receipt.get('exit_code')==0 and not cleanup_errors and not retained and not receipt['remaining_outputs'] and safe_to_clean and receipt.get('stop_exit_code')==0 and not DEFERRED_SIGNALS and not receipt['source_changed_during_cycle'] and not receipt['runner_changed_during_cycle'] else 'FAIL'
            receipt['cleanup_method']='Exact task-owned generated dirs; no Gradle clean daemon or global cache deletion. Unrelated processes excluded by PID/start identity and cycle ownership.'
            save()
            print(json.dumps({k:v for k,v in receipt.items() if k not in ('source_before','source_after')},indent=2),flush=True)
            FINALIZING=False
        if error: raise error
        return 0 if receipt['status']=='PASS' else (receipt.get('exit_code') or 1)

if __name__=='__main__':
    def interrupted(signum, frame):
        if FINALIZING: DEFERRED_SIGNALS.append(signum)
        else: raise KeyboardInterrupt(f'signal {signum}')
    signal.signal(signal.SIGTERM,interrupted)
    signal.signal(signal.SIGINT,interrupted)
    sys.exit(main())
