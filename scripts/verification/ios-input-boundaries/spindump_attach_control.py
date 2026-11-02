"""Installed41420769370 interface: one owned host target, offline text analysis.

Only the runner's existing sudo policy for this exact target-only Apple tool.
No permission/configuration change, app/Simulator launch or opaque upload.
Actual permission/format failures stay failed; documentation is not capability.
"""
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

from profiler_attach_control import checkpoint, command as native_command, environment

TOOL = '/usr/sbin/spindump'
MAX_RAW = 16*1024*1024
MAX_TEXT = 4*1024*1024
PRIVATE_CAPTURE = 'record.opaque-output'


def capture_command(pid, raw):
    if type(pid) is not int or pid <= 0:raise ValueError('Exact owned positive PID required')
    # Actual41421565623 exit77 requires root for live sampling. GitHub standard
    # macOS runners document existing passwordless sudo; do not grant/alter it.
    # Never prompt or preserve caller credentials. Native5s limit bounds the
    # privileged tool itself inside the unchanged8s launcher deadline.
    return ['/usr/bin/sudo','-n',TOOL,str(pid),'1','10','-onlyTarget','-noText',
            '-noProcessingWhileSampling','-timelimit','5','-noFile']


def analysis_command(raw, text):
    return [TOOL,'-i',str(raw),'-timeline','-noBinary','-o',str(text)]


def public_log(raw):
    # Actual41422535117 output-write error falls back to stdout INCLUDING the
    # opaque format. Never upload raw CLI output, even on error. Keep only these
    # observed public stage/error lines; everything unknown/binary stays private.
    prefix=raw.split(b'Spindump binary format',1)[0]
    lines=[]
    for line in prefix.splitlines():
        known=line.startswith((b'Sampling ',b'Use spindump -i ',b'Spindump failed to save report to ',
                               b'Please enter a new output location ',b'spindump must be run as root '))
        if known and len(line)<=1024 and all(32<=v<=126 or v==9 for v in line):lines.append(line)
    return b'\n'.join(lines)+(b'\n' if lines else b'')


def command(directory,receipt,label,argv):
    try:native_command(directory,receipt,label,argv,opaque=True)
    finally:
        path=directory/(label+'.opaque-output')
        raw=path.read_bytes() if path.exists() and path.stat().st_size<=MAX_RAW else b''
        safe=public_log(raw)
        fd=os.open(directory/(label+'.log'),os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as output:output.write(safe)
        receipt.setdefault('private_output',{})[label]=dict(bytes=path.stat().st_size if path.exists() else 0,
            sha256=hashlib.sha256(raw).hexdigest() if raw else None,uploaded=False,bounded=len(raw)>0)
        checkpoint(directory,receipt)


def owned_file(path, maximum):
    if path.resolve()!=path.absolute() or not path.is_file() or path.is_symlink():
        raise ValueError('Owned unredirected native report required')
    if not 0<path.stat().st_size<=maximum:raise ValueError('Bounded actual native report required')
    return path.read_bytes()


def read_report(path, pid):
    raw=owned_file(path,MAX_TEXT);text=raw.decode('utf-8')
    processes=re.findall(r'^Process:\s+(.+?)\s+\[(\d+)\]\s*$',text,re.M)
    if processes!=[('yes',str(pid))]:raise ValueError('Only the exact owned target process report')
    if not re.search(r'^\s*Thread\s+0x[0-9a-fA-F]+',text,re.M) or not re.search(r'\[0x[0-9a-fA-F]+\]',text):
        raise ValueError('Actual native thread and stack frames required')
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def control(directory):
    if sys.platform!='darwin':raise RuntimeError('Actual macOS host required')
    if directory.resolve()!=directory.absolute() or directory.exists():raise ValueError('Fresh owned output required')
    directory.mkdir(mode=0o700)
    receipt=dict(schema=1,tool=TOOL,target_command=['/usr/bin/yes'],commands=[],
                 apps_launched=0,accessibility_queries=0,all_processes=False,
                 attachment_independently_verified=False,application_root_cause=False,
                 shipping_qualified=False,physical_verified=False)
    raw=directory/PRIVATE_CAPTURE;text=directory/'report.txt'
    target=subprocess.Popen(receipt['target_command'],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,env=environment())
    receipt['owned_target_pid']=target.pid;checkpoint(directory,receipt)
    try:
        try:command(directory,receipt,'record',capture_command(target.pid,raw))
        finally:
            if target.poll() is None:target.terminate()
            try:target.wait(timeout=3)
            except subprocess.TimeoutExpired:target.kill();target.wait(timeout=3)
            receipt['owned_target_reaped']=target.poll() is not None;checkpoint(directory,receipt)
        opaque=owned_file(raw,MAX_RAW)
        receipt['raw_metadata']=dict(bytes=len(opaque),sha256=hashlib.sha256(opaque).hexdigest(),uploaded=False)
        command(directory,receipt,'analysis',analysis_command(raw,text))
        receipt['report']=read_report(text,target.pid)
        receipt['result']='OWNED_TARGET_STACK_REPORT_REQUIRES_INDEPENDENT_ADMISSION'
    except BaseException as error:
        receipt['error']=type(error).__name__+': '+str(error);raise
    finally:checkpoint(directory,receipt)


if __name__=='__main__':control(Path(sys.argv[1]))
