"""Telemetry/source rejections and isolated actual-Apple software capability."""
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import pin46_main_runloop as control


def report():
    return dict(schema=1,installed=True,capturedMs=500,phaseCounts=[1,3,2,2,1],completedActiveIntervals=2,
        completedWaitingIntervals=1,largestActiveMs=[100,200,100],largestWaitingMs=[300,400,100],
        longestActiveIntervalsMs=[[100,200,100],[400,450,50]],longestWaitingIntervalsMs=[[300,400,100]],
        partialActiveMs=[450,500,50],invalid=0,boundary=control.SCOPE,timerOrPolling=False,
        additionalThread=False,allIntervalsRetained=False,observerRemoved=True)


class MainRunLoopControls(unittest.TestCase):
    def test_exact_bounded_summary_reports_phase_intervals_not_dispatch_or_all_history(self):
        v=report();self.assertEqual(control.decode(json.dumps(v).encode()),v)
        for key in ('timerOrPolling','additionalThread','allIntervalsRetained'):
            v=report();v[key]=True
            with self.assertRaises(ValueError):control.decode(json.dumps(v).encode())

    def test_private_unknown_duplicate_nonfinite_oversized_and_impossible_intervals_reject(self):
        for change in (
            lambda v:v.update(label='Never export a UI label'),lambda v:v.update(schema=True),
            lambda v:v.update(invalid=1),lambda v:v.update(observerRemoved=False),lambda v:v.update(installed=False),lambda v:v.update(capturedMs=float('nan')),
            lambda v:v['phaseCounts'].__setitem__(0,True),lambda v:v.update(completedActiveIntervals=100),
            lambda v:v['longestActiveIntervalsMs'][0].__setitem__(2,101),lambda v:v['largestActiveMs'].__setitem__(0,-1),
            lambda v:v['partialActiveMs'].__setitem__(1,501),lambda v:v['longestWaitingIntervalsMs'].append([1,2,1]),
            lambda v:v['longestActiveIntervalsMs'].reverse(),lambda v:v.update(boundary='dispatch-and-AX-root-cause'),
        ):
            v=report();change(v)
            with self.subTest(change=change),self.assertRaises(ValueError):control.decode(json.dumps(v).encode())
        with self.assertRaises(ValueError):control.decode(b'{"schema":1,"schema":1}')
        with self.assertRaises(ValueError):control.decode(b' '*8193)

    def test_source_has_no_timer_thread_view_or_accessibility_interception_and_freezes_before_removal(self):
        source=control.source(control.TEMPLATE).decode()
        for forbidden in ('Timer(', 'Thread.sleep', 'DispatchQueue.', 'accessibilityElements', 'accessibilityLabel',
                          'accessibilityValue','method_setImplementation','addGestureRecognizer'):
            self.assertNotIn(forbidden,source)
        self.assertIn('CFRunLoopObserverCreateWithHandler',source)
        self.assertIn('CFRunLoopMode.commonModes',source)
        self.assertNotIn('kCFRunLoopCommonModes',source)
        self.assertLess(source.index('        frozen = value'),source.index('        CFRunLoopRemoveObserver'))
        self.assertIn('.prefix(8)',source)
        self.assertIn('duration > longestActive.last![2]',source)
        control.require_context_binding()
        with patch.object(control,'CONTEXT_RECEIPT_SHA256','a'*64),self.assertRaises(ValueError):control.require_context_binding()

    @unittest.skipUnless(platform.system()=='Darwin','Actual Apple event-driven run-loop compiler/runtime required')
    def test_actual_apple_control_measures_main_thread_stall_and_freezes_with_observer_removed(self):
        with tempfile.TemporaryDirectory() as temporary:
            out=Path(temporary).resolve()
            evidence=os.environ.get('PARLOR_RUNLOOP_CONTROL_OUTPUT')
            if evidence:
                out=Path(evidence).absolute();parent=Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
                self.assertEqual(out.parent,parent);self.assertFalse(out.exists());self.assertFalse(out.is_symlink())
                out.mkdir(mode=0o700)
            env=dict(os.environ,TMPDIR=str(out));source=out/'main.swift';binary=out/'control'
            control.require_context_binding()
            state=dict(result='NOT_EXECUTED',application_root_cause=False,ios_verified=False,shipping_qualified=False,
                       physical_verified=False,source_sha256={n:hashlib.sha256(control.source(n)).hexdigest() for n in (control.TEMPLATE,control.CONTROL)})
            if evidence:
                root=control.HERE.parents[2]
                self.assertEqual(subprocess.check_output(['git','status','--porcelain'],cwd=root),b'')
                state['harness']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
                state['xcode']=subprocess.check_output(['/usr/bin/xcodebuild','-version'],text=True)
                self.assertEqual(state['xcode'],'Xcode 26.3\nBuild version 17C529\n')
                state['platform']=dict(system=platform.system(),machine=platform.machine())
            def checkpoint():
                if evidence:(out/'state.json').write_text(json.dumps(state,indent=2))
            checkpoint()
            source.write_bytes(control.source(control.TEMPLATE)+b'\n'+control.source(control.CONTROL))
            argv=['/usr/bin/xcrun','swiftc','-parse-as-library','-D','PIN46_CONTROL',str(source),'-o',str(binary)]
            state['compiler_command']=argv;state['compiler_timeout_seconds']=120;checkpoint()
            r=subprocess.run(argv,capture_output=True,env=env,timeout=120)
            state['compiler_exit']=r.returncode
            if evidence:(out/'compiler.log').write_bytes(r.stdout+r.stderr)
            checkpoint()
            self.assertEqual(r.returncode,0,r.stderr.decode())
            state['binary_sha256']=hashlib.sha256(binary.read_bytes()).hexdigest()
            state['control_command']=[str(binary)];state['control_timeout_seconds']=10;checkpoint()
            r=subprocess.run([str(binary)],capture_output=True,env=env,timeout=10)
            state['control_exit']=r.returncode
            if evidence:
                (out/'control.stdout').write_bytes(r.stdout);(out/'control.stderr').write_bytes(r.stderr)
            checkpoint()
            self.assertEqual(r.returncode,0,r.stderr.decode())
            v=control.decode(r.stdout)
            self.assertGreaterEqual(v['largestActiveMs'][2],50)
            self.assertGreater(v['completedActiveIntervals'],0)
            self.assertGreater(sum(v['phaseCounts']),0)
            self.assertEqual(v['invalid'],0)
            state.update(result='ACTUAL_DARWIN_RUNLOOP_SOFTWARE_CAPABILITY_NOT_IOS_QUALIFICATION',
                         observation=v,stdout_sha256=hashlib.sha256(r.stdout).hexdigest(),
                         context_run=control.CONTEXT_RUN,context_harness=control.CONTEXT_HARNESS,
                         context_receipt_sha256=control.CONTEXT_RECEIPT_SHA256)
            checkpoint()
