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
MAX_XML = 4*1024*1024
XPATH = '/trace-toc/run[@number="1"]/data/table[@schema="time-profile"]'


def environment():
    return {k:os.environ[k] for k in ('HOME','TMPDIR') if k in os.environ} | dict(
        PATH='/usr/bin:/bin:/usr/sbin:/sbin', LC_ALL='C', DEVELOPER_DIR=DEVELOPER)


def record_command(pid,trace):
    if type(pid) is not int or pid<=0:raise ValueError('Exact owned positive PID required')
    return ['/usr/bin/xcrun','xctrace','record','--template','Time Profiler',
            '--attach',str(pid),'--time-limit','1s','--output',str(trace)]


def export_command(trace,target,*,toc=False):
    return ['/usr/bin/xcrun','xctrace','export','--input',str(trace),
            *(['--toc'] if toc else ['--xpath',XPATH]),'--output',str(target)]


def xml(path):
    if path.resolve()!=path.absolute() or not path.is_file() or path.is_symlink():
        raise ValueError('Owned unredirected XML export')
    raw=path.read_bytes()
    if not 0<len(raw)<=MAX_XML or b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
        raise ValueError('Bounded plain XML only')
    return raw,ET.fromstring(raw)


def checkpoint(directory,value):
    temp=directory/'receipt.writing';temp.write_text(json.dumps(value,indent=2));temp.replace(directory/'receipt.json')


def command(directory,receipt,label,argv):
    started=time.monotonic()
    row=dict(label=label,command=argv,started_unix=time.time(),status='STARTED')
    receipt['commands'].append(row);checkpoint(directory,receipt)
    with (directory/(label+'.log')).open('xb') as output:
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
    target=subprocess.Popen(receipt['target_command'],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,env=environment())
    receipt['owned_target_pid']=target.pid;checkpoint(directory,receipt)
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
        receipt['error']=type(error).__name__+': '+str(error);raise
    finally:checkpoint(directory,receipt)


if __name__=='__main__':control(Path(sys.argv[1]))
