#!/usr/bin/env python3
"""One audit-owned build lane with mandatory evidence/stop/cleanup finalization."""
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
def invoke(command, logfile, env):
    with logfile.open('w') as log:
        p=subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try: return p.wait()
        except BaseException:
            p.terminate()
            try: p.wait(timeout=30)
            except subprocess.TimeoutExpired:
                p.kill(); p.wait()
            raise

def owned_outputs():
    modules=[p.parent for p in ROOT.glob('shared/*/build.gradle.kts')]
    modules += [p.parent for p in ROOT.glob('game-modes/*/build.gradle.kts')]
    modules += [ROOT,ROOT/'composeApp',ROOT/'build-logic',ROOT/'build-logic/convention']
    return [p/'build' for p in modules]

def identity():
    return {k:subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip() for k,args in {'commit':['rev-parse','HEAD'],'tree':['rev-parse','HEAD^{tree}'],'branch':['branch','--show-current'],'tracked_status':['status','--porcelain=v1','--untracked-files=no']}.items()}

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
    if len(sys.argv)<3: raise SystemExit('run_gradle_cycle.py NAME TASK [TASK/FLAG ...]')
    name=sys.argv[1]
    if not name.replace('-','').isalnum(): raise SystemExit('Invalid cycle name')
    dest=OUT/'evidence'/name
    if dest.exists(): raise SystemExit('Refusing to overwrite cycle evidence')
    dest.mkdir(parents=True)
    with (OUT/'build-lane.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        outputs=owned_outputs()
        # Never remove a pre-existing build output/user artifact by accident.
        existing=[str(p.relative_to(ROOT)) for p in outputs if p.exists()]
        if existing: raise SystemExit(f'Pre-existing outputs require separate ownership review: {existing}')
        env=os.environ.copy()
        for key in list(env):
            if key.startswith(('PARLOR_ANDROID_', 'MOBILE_RELEASE_')): env.pop(key)
        env['JAVA_HOME']=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()
        env['PYTHONDONTWRITEBYTECODE']='1'
        common=['--no-daemon','--no-parallel','--max-workers=1','--no-configuration-cache','--dependency-verification=strict','-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC','-Pkotlin.compiler.execution.strategy=in-process','-Pparlor.android.signing.storeFile=','-Pparlor.android.signing.storePassword=','-Pparlor.android.signing.keyAlias=','-Pparlor.android.signing.keyPassword=']
        command=['./gradlew',*sys.argv[2:],*common]
        receipt={'cycle':name,'started_at':now(),'source_before':identity(),'command':command,'env_policy':'JDK21, signing environment removed, empty signing properties; do not inspect private inputs','outputs_before':existing,'status':'RUNNING'}
        (dest/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        error=None
        try:
            receipt['exit_code']=invoke(command,dest/'gradle.log',env)
            receipt['finished_at']=now()
        except BaseException as e:
            error=e;receipt['error']=type(e).__name__;receipt['finished_at']=now()
        finally:
            # Stop immediately; copying small XML evidence does not require a daemon.
            try: receipt['stop_exit_code']=invoke(['./gradlew','--stop'],dest/'stop.log',env)
            except Exception as e: receipt['stop_error']=type(e).__name__
            receipt['stopped_at']=now()
            try: collect_reports(dest,outputs)
            except Exception as e: receipt['report_collection_error']=str(e)
            try: inspect_generated_artifacts(dest)
            except Exception as e: receipt['artifact_inspection_error']=str(e)
            # Precise removal of only task-created generated module/build-logic dirs.
            removed=[]
            cleanup_errors=[]
            for p in outputs:
                try:
                    if p.is_symlink(): raise RuntimeError(f'Refusing to clean symlink: {p}')
                    if p.exists():
                        shutil.rmtree(p);removed.append(str(p.relative_to(ROOT)))
                except Exception as e: cleanup_errors.append(str(e))
            receipt['removed_outputs']=removed
            receipt['cleanup_errors']=cleanup_errors
            receipt['remaining_outputs']=[str(p.relative_to(ROOT)) for p in outputs if p.exists()]
            receipt['cleanup_completed_at']=now()
            receipt['source_after']=identity()
            receipt['status']='PASS' if receipt.get('exit_code')==0 else 'FAIL'
            # No cleanup Gradle invocation: no second daemon can have been started.
            receipt['cleanup_method']='Exact task-created build directories; no Gradle cleanup daemon, no global caches touched'
            ps=subprocess.run(['pgrep','-lf','GradleDaemon|KotlinCompileDaemon|xcodebuild|GradleWorkerMain'],text=True,capture_output=True)
            (dest/'processes-after.txt').write_text(ps.stdout)
            receipt['process_scan_exit_code']=ps.returncode
            (dest/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
            print(json.dumps(receipt,indent=2),flush=True)
        if error: raise error
        return receipt['exit_code']

if __name__=='__main__':
    def interrupted(signum, frame): raise KeyboardInterrupt(f'signal {signum}')
    signal.signal(signal.SIGTERM,interrupted)
    sys.exit(main())
