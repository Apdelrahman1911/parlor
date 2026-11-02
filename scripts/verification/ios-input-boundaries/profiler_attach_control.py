"""One bounded host-only profiler control; never the application or all processes.

Installed interfaces in41416484173 establish syntax, not attach capability.
Use one exact owned /usr/bin/yes PID; stdout is discarded and no input is retained.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEVELOPER = '/Applications/Xcode_26.3.app/Contents/Developer'
PROFILER = DEVELOPER+'/usr/bin/xctrace'
MAX_XML = 4*1024*1024
XPATH = '/trace-toc/run[@number="1"]/data/table[@schema="time-profile"]'


def environment():
    return {k:os.environ[k] for k in ('HOME','TMPDIR') if k in os.environ} | dict(
        PATH='/usr/bin:/bin:/usr/sbin:/sbin', LC_ALL='C', DEVELOPER_DIR=DEVELOPER)


def record_command(pid,trace):
    if type(pid) is not int or pid<=0:raise ValueError('Exact owned positive PID required')
    return [PROFILER,'record','--template','Time Profiler',
            '--attach',str(pid),'--time-limit','1s','--output',str(trace)]


def export_command(trace,target,*,toc=False):
    return [PROFILER,'export','--input',str(trace),
            *(['--toc'] if toc else ['--xpath',XPATH]),'--output',str(target)]


def startup_log_command(pid):
    if type(pid) is not int or pid<=0:raise ValueError('Only the actual owned recorder PID')
    # Default unified-log privacy remains enabled. No --private, other process
    # predicate, global dump, argument/environment or player information query.
    return ['/usr/bin/log','show','--style','compact','--last','1m','--info',
            '--predicate','processID == '+str(pid)]


def xml(path):
    if path.resolve()!=path.absolute() or not path.is_file() or path.is_symlink():
        raise ValueError('Owned unredirected XML export')
    raw=path.read_bytes()
    if not 0<len(raw)<=MAX_XML or b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
        raise ValueError('Bounded plain XML only')
    return raw,ET.fromstring(raw)


def checkpoint(directory,value):
    temp=directory/'receipt.writing';temp.write_text(json.dumps(value,indent=2));temp.replace(directory/'receipt.json')


def command(directory,receipt,label,argv,*,opaque=False):
    started=time.monotonic()
    row=dict(label=label,command=argv,started_unix=time.time(),status='STARTED')
    receipt['commands'].append(row);checkpoint(directory,receipt)
    suffix='.opaque-output' if opaque else '.log'
    fd=os.open(directory/(label+suffix),os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    with os.fdopen(fd,'wb') as output:
        child=subprocess.Popen(argv,stdin=subprocess.DEVNULL,stdout=output,stderr=output,env=environment())
        row['owned_command_pid']=child.pid;checkpoint(directory,receipt)
        try:
            result=child.wait(timeout=max(0,8-(time.monotonic()-started)))
            row.update(exit=result,seconds=time.monotonic()-started,ended_unix=time.time())
            if result!=0 or row['seconds']>=8:raise ValueError('Actual profiler command failed or exceeded8s')
            row['status']='COMPLETE'
        finally:
            if child.poll() is None:child.kill()
            child.wait(timeout=3);row['owned_child_reaped']=child.poll() is not None
            checkpoint(directory,receipt)


def control(directory):
    if sys.platform!='darwin':raise RuntimeError('Actual macOS host required')
    if directory.resolve()!=directory.absolute() or directory.exists():raise ValueError('Fresh owned control output')
    directory.mkdir(mode=0o700)
    receipt=dict(schema=1,developer_dir=DEVELOPER,target_command=['/usr/bin/yes'],commands=[],
                 apps_launched=0,accessibility_queries=0,all_processes=False,actual_target_samples_verified=False,
                 application_root_cause=False,shipping_qualified=False,physical_verified=False)
    # Installed41416484173 observed3.912s cold xcrun resolution. Complete this
    # exact read-only readiness BEFORE creating the busy target; no retry or
    # extra sampling. Recording/export use the admitted absolute Xcode tool.
    try:
        command(directory,receipt,'ready-path',['/usr/bin/xcrun','--find','xctrace'])
        if (directory/'ready-path.log').read_bytes()!=PROFILER.encode()+b'\n':
            raise ValueError('Only the exact installed Xcode26.3 profiler path')
        command(directory,receipt,'ready-help',[PROFILER,'help','record'])
        if b'--attach' not in (directory/'ready-help.log').read_bytes():
            raise ValueError('Actual selected tool readiness required before target creation')
        command(directory,receipt,'developer-status',['/usr/sbin/DevToolsSecurity','-status'])
        command(directory,receipt,'first-launch-status',[DEVELOPER+'/usr/bin/xcodebuild','-checkFirstLaunchStatus'])
        # 41419388326 reached CoreSimulator service connections before its
        # silent recording timeout. Complete the installed, documented device
        # enumeration before capture, without booting/launching any Simulator.
        command(directory,receipt,'ready-devices',[PROFILER,'list','devices'])
        if not 0<len((directory/'ready-devices.log').read_bytes())<=256*1024:
            raise ValueError('Bounded actual device enumeration readiness required')
        receipt['ready_before_target']=True;checkpoint(directory,receipt)
    except BaseException as error:
        receipt['error']=type(error).__name__+': '+str(error)
        enumerated=[row for row in receipt['commands'] if row['label']=='ready-devices']
        if len(enumerated)==1 and 'owned_command_pid' in enumerated[0]:
            try:command(directory,receipt,'startup-log',startup_log_command(enumerated[0]['owned_command_pid']))
            except BaseException as log_error:receipt['startup_log_error']=type(log_error).__name__+': '+str(log_error)
        checkpoint(directory,receipt);raise
    target=subprocess.Popen(receipt['target_command'],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,env=environment())
    receipt['owned_target_pid']=target.pid;receipt['owned_target_started_unix']=time.time();checkpoint(directory,receipt)
    trace=directory/'owned.trace'
    try:
        try:command(directory,receipt,'record',record_command(target.pid,trace))
        finally:
            # No unnecessary CPU during offline exports, even after failure.
            if target.poll() is None:target.terminate()
            try:target.wait(timeout=3)
            except subprocess.TimeoutExpired:target.kill();target.wait(timeout=3)
            receipt['owned_target_reaped']=target.poll() is not None;checkpoint(directory,receipt)
        command(directory,receipt,'toc',export_command(trace,directory/'toc.xml',toc=True))
        _,toc=xml(directory/'toc.xml')
        if len([t for t in toc.iter('table') if t.get('schema')=='time-profile'])!=1:
            raise ValueError('Actual unique Time Profiler schema required; no guessed fallback')
        command(directory,receipt,'samples',export_command(trace,directory/'samples.xml'))
        raw,data=xml(directory/'samples.xml')
        if not list(data.iter('row')):raise ValueError('Actual nonempty samples required, not an empty recording')
        receipt['export_sha256']=hashlib.sha256(raw).hexdigest();receipt['export_bytes']=len(raw)
        receipt['result']='OWNED_HOST_PROFILER_EXPORT_COMPLETE_REQUIRES_INDEPENDENT_TARGET_ADMISSION'
    except BaseException as error:
        receipt['error']=type(error).__name__+': '+str(error)
        recorded=[row for row in receipt['commands'] if row['label']=='record']
        if len(recorded)==1 and 'owned_command_pid' in recorded[0]:
            try:
                command(directory,receipt,'startup-log',startup_log_command(recorded[0]['owned_command_pid']))
            except BaseException as log_error:
                # Do not replace, waive or retry the original capture failure.
                receipt['startup_log_error']=type(log_error).__name__+': '+str(log_error)
        raise
    finally:
        # Only numeric owned-output presence metadata, not opaque/unrelated
        # profile contents. This is not trace completeness or stack evidence.
        receipt['trace_presence']={'exists':trace.exists(),'entries':0,'regular_bytes':0,'bounded':True}
        if trace.exists() and trace.is_dir() and not trace.is_symlink():
            for entry in trace.rglob('*'):
                receipt['trace_presence']['entries']+=1
                if receipt['trace_presence']['entries']>512 or entry.is_symlink():
                    receipt['trace_presence']['bounded']=False;break
                if entry.is_file():receipt['trace_presence']['regular_bytes']+=entry.stat().st_size
                if receipt['trace_presence']['regular_bytes']>64*1024*1024:
                    receipt['trace_presence']['bounded']=False;break
        checkpoint(directory,receipt)


if __name__=='__main__':control(Path(sys.argv[1]))
