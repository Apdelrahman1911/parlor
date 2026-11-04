"""Portable declared-script/child and finite file-backed profiler controls."""
import importlib.util
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('query_probe', Path(__file__).with_name('launch.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


def info(pid=100, ppid=None, start_sec=1000, uid=None):
    return SimpleNamespace(pid=pid, ppid=os.getpid() if ppid is None else ppid,
                           uid=os.getuid() if uid is None else uid,
                           start_sec=start_sec, start_usec=0, comm=b'bash')


class QueryPipelineControls(unittest.TestCase):
    def test_only_exact_owned_script_and_original_query_arguments_are_admitted(self):
        origin = info(); args = [b'/bin/bash', b'/pinned/simctl', b'spawn', b'OWNED', b'launchctl', b'managerpid']
        argv = ['/usr/bin/xcrun', 'simctl', 'spawn', 'OWNED', 'launchctl', 'managerpid']
        tools = ('/usr/bin/xcrun', '/pinned/simctl', '/native/simctl')
        self.assertTrue(p.is_declared_script(origin, origin, '/bin/bash', args, argv, tools, os.getpid()))
        for wrong in [args[:1]+[b'/other/script']+args[2:], args+[b'extra'], args[:3]+[b'FOREIGN']+args[4:], [b'/bin/bash', b'-c', b'arbitrary']]:
            self.assertFalse(p.is_declared_script(origin, origin, '/bin/bash', wrong, argv, tools, os.getpid()))
        self.assertFalse(p.is_declared_script(info(ppid=1), origin, '/bin/bash', args, argv, tools, os.getpid()))

    def test_exact_fresh_native_query_child_is_selected_not_unrelated_script_helper(self):
        with TemporaryDirectory() as temp:
            native = Path(temp)/'native';native.write_bytes(b'fixture')
            other = Path(temp)/'other';other.write_bytes(b'fixture')
            tools=('/usr/bin/xcrun','/pinned/simctl',str(native))
            direct=(info(),'/bin/bash');child=info(pid=101,ppid=100,start_sec=1001)
            argv=['/usr/bin/xcrun','simctl','spawn','OWNED','launchctl','managerpid']
            args=[os.fsencode(native),b'spawn',b'OWNED',b'launchctl',b'managerpid']
            target,scope=p.select_script_query_child([(child,str(native),args)],direct,argv,tools)
            self.assertEqual(target[0].pid,101);self.assertEqual(scope,'owned-native-query-child-not-keyboard')
            for candidate in [(info(pid=101,ppid=1),str(native),args),(child,str(other),args),
                              (child,str(native),args[:2]+[b'FOREIGN']+args[3:])]:
                target,scope=p.select_script_query_child([candidate],direct,argv,tools)
                self.assertEqual(target,direct);self.assertEqual(scope,'owned-query-script-interpreter-not-keyboard')
            with self.assertRaisesRegex(RuntimeError,'Ambiguous'):
                p.select_script_query_child([(child,str(native),args)]*2,direct,argv,tools)

    def test_profiler_streams_are_files_and_timeout_terminates_only_owned_caller(self):
        with TemporaryDirectory() as temp:
            out=Path(temp);caller=SimpleNamespace(pid=99)
            caller.wait=unittest.mock.Mock(side_effect=[p.subprocess.TimeoutExpired('profile',12),-15])
            caller.terminate=unittest.mock.Mock();caller.kill=unittest.mock.Mock();caller.poll=lambda:-15
            with patch.object(p.subprocess,'Popen',return_value=caller) as launched:
                self.assertIsNone(p.profile_command(out,['sudo','exact-tool','100'],deadline=p.time.monotonic()+30))
                _,kw=launched.call_args
                self.assertNotEqual(kw['stdout'],p.subprocess.PIPE);self.assertNotEqual(kw['stderr'],p.subprocess.PIPE)
            caller.terminate.assert_called_once_with();caller.kill.assert_not_called()
            self.assertEqual(caller.wait.call_args_list,[unittest.mock.call(timeout=12),unittest.mock.call(timeout=1)])
            record=json.loads((out/'spindump.json').read_text())
            self.assertEqual(record['status'],'TIMED_OUT');self.assertIsNone(record['exit_code'])
            self.assertEqual(record['caller_pid'],99);self.assertFalse(record['sampled_pid_signalled'])
            self.assertEqual(record['descendant_termination'],'NOT_INDEPENDENTLY_VERIFIED')

    def test_successful_profiler_is_not_cancelled_and_retains_exit(self):
        with TemporaryDirectory() as temp:
            out=Path(temp);caller=SimpleNamespace(pid=99,wait=unittest.mock.Mock(return_value=0),
                 terminate=unittest.mock.Mock(),kill=unittest.mock.Mock(),poll=lambda:0)
            with patch.object(p.subprocess,'Popen',return_value=caller):
                self.assertEqual(p.profile_command(out,['sudo','exact-tool','100'],deadline=p.time.monotonic()+30),0)
            caller.terminate.assert_not_called();caller.kill.assert_not_called()
            self.assertEqual(json.loads((out/'spindump.json').read_text())['status'],'EXITED')

    def test_insufficient_query_budget_never_starts_profiler(self):
        with TemporaryDirectory() as temp, patch.object(p.time,'monotonic',return_value=100), \
             patch.object(p.subprocess,'Popen') as launched:
            out=Path(temp)
            self.assertIsNone(p.profile_command(out,['exact-tool'],deadline=109))
            launched.assert_not_called()
            self.assertEqual(json.loads((out/'spindump.json').read_text())['status'],
                             'NOT_STARTED_INSUFFICIENT_QUERY_BUDGET')

    def test_profiler_budget_is_shortened_not_query_deadline_extended(self):
        with TemporaryDirectory() as temp, patch.object(p.time,'monotonic',return_value=100):
            out=Path(temp);caller=SimpleNamespace(pid=99,wait=unittest.mock.Mock(return_value=0),poll=lambda:0)
            with patch.object(p.subprocess,'Popen',return_value=caller):
                self.assertEqual(p.profile_command(out,['exact-tool'],deadline=110),0)
            caller.wait.assert_called_once_with(timeout=8.75)

    def test_script_child_exit_is_skipped_but_live_argv_failure_is_not(self):
        parent=info();child=info(pid=101,ppid=100,start_sec=1001)
        for failure in ('path','args'):
            for exits in (True,False):
                with self.subTest(failure=failure,exits=exits):
                    backend=SimpleNamespace(_pids=lambda:[101],_basic=unittest.mock.Mock(side_effect=[child,None if exits else child]),
                        _path=unittest.mock.Mock(side_effect=RuntimeError('read failed') if failure=='path' else None,
                                                 return_value='/native/simctl'))
                    with patch.object(p,'native_args',side_effect=RuntimeError('read failed')), \
                         patch.object(p.time,'monotonic',return_value=100):
                        if exits:
                            self.assertEqual(p.script_children(backend,parent,1000,120),[])
                        else:
                            with self.assertRaisesRegex(RuntimeError,'read failed'):
                                p.script_children(backend,parent,1000,120)

    def test_script_selection_stops_at_original_query_deadline(self):
        backend=SimpleNamespace(_pids=lambda:[101],_basic=unittest.mock.Mock())
        with patch.object(p.time,'monotonic',return_value=120):
            self.assertEqual(p.script_children(backend,info(),1000,120),[])
        backend._basic.assert_not_called()


if __name__=='__main__':unittest.main()
