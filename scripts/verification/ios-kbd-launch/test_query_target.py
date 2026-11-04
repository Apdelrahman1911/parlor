import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
spec=importlib.util.spec_from_file_location('query_probe',Path(__file__).with_name('launch.py'))
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class NativeQueryTargetControls(unittest.TestCase):
    def test_sole_fresh_own_runtime_launchctl_is_selected_over_dispatcher(self):
        parent=SimpleNamespace(pid=2847,uid=501);root=Path('/runtime/RuntimeRoot')
        child=SimpleNamespace(pid=3000,ppid=2847,uid=501,start_sec=100,start_usec=0)
        direct=(SimpleNamespace(pid=2900,uid=501,start_sec=100),'/usr/bin/simctl')
        result,scope=p.select_query_target([(child,str(root/'bin/launchctl'))],direct,parent,99,root)
        self.assertEqual(result[0].pid,3000);self.assertEqual(scope,'owned-simulator-launchctl')
    def test_foreign_parent_uid_runtime_and_old_generation_do_not_grant_child_sampling(self):
        parent=SimpleNamespace(pid=2847,uid=501);root=Path('/runtime/RuntimeRoot')
        child=SimpleNamespace(pid=3000,ppid=2847,uid=501,start_sec=100,start_usec=0)
        direct=(SimpleNamespace(pid=2900,uid=501,start_sec=100),'/usr/bin/simctl')
        for key,value in (('ppid',1),('uid',502),('start_sec',90)):
            other=SimpleNamespace(**(vars(child)|{key:value}))
            result,scope=p.select_query_target([(other,str(root/'bin/launchctl'))],direct,parent,99,root)
            self.assertEqual(result,direct);self.assertEqual(scope,'direct-query-dispatcher-not-keyboard')
        result,_=p.select_query_target([(child,'/other/bin/launchctl')],direct,parent,99,root)
        self.assertEqual(result,direct)
    def test_ambiguous_children_or_missing_foreign_old_dispatcher_are_rejected(self):
        parent=SimpleNamespace(pid=2847,uid=501);root=Path('/runtime/RuntimeRoot')
        child=SimpleNamespace(pid=3000,ppid=2847,uid=501,start_sec=100,start_usec=0)
        with self.assertRaises(RuntimeError):p.select_query_target([(child,str(root/'bin/launchctl'))]*2,None,parent,99,root)
        for direct in (None,(SimpleNamespace(pid=2900,uid=502,start_sec=100),'/usr/bin/simctl'),(SimpleNamespace(pid=2900,uid=501,start_sec=90),'/usr/bin/simctl')):
            with self.assertRaises(RuntimeError):p.select_query_target([],direct,parent,99,root)
if __name__=='__main__':unittest.main()
