"""One exact owned booted Simulator service condition, then unchanged profiler.

41420005706 failed read-only device enumeration before any owned target existed;
41425590946 failed Parlor spindump after sampling. Test the documented profiler
under an explicitly different, boot-ready CoreSimulator condition. No app/XCTest,
permission change, build, corrective input, sampler deadline increase or retry.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

import profiler_attach_control as profiler

SIMCTL = profiler.DEVELOPER+'/usr/bin/simctl'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
UUID = re.compile(r'[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}')
MAX_LOG = 256*1024


def operation(directory, receipt, label, args, timeout):
    argv = [SIMCTL, *args]
    row = dict(label=label,command=argv,timeout_seconds=timeout,started_unix=time.time(),status='STARTED')
    receipt['commands'].append(row);profiler.checkpoint(directory,receipt)
    start=time.monotonic()
    fd=os.open(directory/(label+'.log'),os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    with os.fdopen(fd,'wb') as output:
        child=subprocess.Popen(argv,stdin=subprocess.DEVNULL,stdout=output,stderr=output,env=profiler.environment())
        row['owned_command_pid']=child.pid;profiler.checkpoint(directory,receipt)
        try:
            code=child.wait(timeout=max(0,timeout-(time.monotonic()-start)))
            row.update(exit=code,seconds=time.monotonic()-start,ended_unix=time.time())
            if code!=0 or row['seconds']>=timeout:raise ValueError('Actual owned Simulator command failed')
            row['status']='COMPLETE'
        finally:
            if child.poll() is None:child.kill()
            child.wait(timeout=3);row['owned_child_reaped']=child.poll() is not None
            profiler.checkpoint(directory,receipt)
    raw=(directory/(label+'.log')).read_bytes()
    if len(raw)>MAX_LOG:raise ValueError('Bounded owned Simulator command output required')
    return raw


def cleanup(directory,receipt):
    simulator=receipt.get('owned_simulator')
    if simulator is None:return
    if not UUID.fullmatch(simulator):raise ValueError('Only exact created owned UUID may be retired')
    # Never shutdown/delete all devices, a guessed device, a pre-existing device,
    # or a different runtime. Re-read exact name + UUID before lifecycle cleanup.
    raw=operation(directory,receipt,'cleanup-ownership',['list','devices','--json'],120)
    value=json.loads(raw)
    candidates=[d for d in value['devices'].get(RUNTIME,[]) if d['udid']==simulator]
    if len(candidates)!=1 or candidates[0]['name']!=receipt['owned_name']:
        raise ValueError('Exact created Simulator ownership must survive until cleanup')
    if candidates[0]['state']=='Booted':
        operation(directory,receipt,'shutdown',['shutdown',simulator],90)
    elif candidates[0]['state']!='Shutdown':raise ValueError('Unknown actual owned Simulator lifecycle state')
    operation(directory,receipt,'delete',['delete',simulator],90)
    receipt['owned_simulator_retired']=True;profiler.checkpoint(directory,receipt)


def control(directory):
    if sys.platform!='darwin':raise RuntimeError('Actual standard macOS runner required')
    if directory.resolve()!=directory.absolute() or directory.exists():raise ValueError('Fresh owned output')
    directory.mkdir(mode=0o700)
    receipt=dict(schema=1,profile='owned-booted-profiler-readiness-v1',commands=[],runtime=RUNTIME,device=DEVICE,
        owned_name='Parlor profiler readiness '+str(uuid.uuid4()),apps_launched=0,original_tests_replayed=0,
        accessibility_queries=0,permission_changes=False,application_root_cause=False,shipping_qualified=False,physical_verified=False)
    profiler.checkpoint(directory,receipt)
    try:
        profiler.command(directory,receipt,'simctl-path',['/usr/bin/xcrun','--find','simctl'])
        if (directory/'simctl-path.log').read_bytes()!=SIMCTL.encode()+b'\n':raise ValueError('Exact selected Xcode26.3 tool')
        identifier=operation(directory,receipt,'create',['create',receipt['owned_name'],DEVICE,RUNTIME],90).decode().strip()
        if not UUID.fullmatch(identifier):raise ValueError('Actual single newly-created device UUID required')
        receipt['owned_simulator']=identifier;profiler.checkpoint(directory,receipt)
        operation(directory,receipt,'boot',['boot',identifier],90)
        operation(directory,receipt,'bootstatus',['bootstatus',identifier,'-b'],120)
        # These are the existing repository lifecycle-command budgets (PIN03),
        # not any larger profiler/capture/assertion limit. Every profiler command
        # remains8s, Time Profiler records1s and the busy owned target is reaped.
        receipt['actual_boot_ready_unix']=time.time();profiler.checkpoint(directory,receipt)
        profiler.control(directory/'profiler')
        receipt['result']='BOOT_READY_PROFILER_CONTROL_REQUIRES_INDEPENDENT_TARGET_ADMISSION'
    except BaseException as error:
        receipt['error']=type(error).__name__+': '+str(error);raise
    finally:
        try:cleanup(directory,receipt)
        except BaseException as error:
            receipt['cleanup_error']=type(error).__name__+': '+str(error);profiler.checkpoint(directory,receipt);raise
        profiler.checkpoint(directory,receipt)


if __name__=='__main__':control(Path(sys.argv[1]))
