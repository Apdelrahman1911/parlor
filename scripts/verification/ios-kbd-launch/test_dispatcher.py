"""New controls for the actual proc_pidpath / selected-tool mismatch."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location('query_probe', Path(__file__).with_name('launch.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class DirectDispatcherControls(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.tool = root / 'canonical' / 'simctl'
        self.tool.parent.mkdir()
        self.tool.write_bytes(b'exact pinned executable')
        self.selected = root / 'developer' / 'simctl'
        self.selected.parent.mkdir()
        self.selected.symlink_to(self.tool)
        self.tools = (str(self.selected),)
        self.original = SimpleNamespace(pid=123, uid=501, ppid=100,
                                        start_sec=1000, start_usec=1, comm=b'xcrun')
        self.current = SimpleNamespace(**(vars(self.original) | {'comm': b'simctl'}))

    def test_canonical_native_path_and_selected_symlink_match_same_executable(self):
        self.assertTrue(p.is_direct_dispatcher(self.current, self.original, str(self.tool), self.tools, 100))
        self.assertTrue(p.is_direct_dispatcher(self.current, self.original, str(self.selected), self.tools, 100))

    def test_same_basename_or_prefix_is_not_executable_identity(self):
        other = self.tool.with_name('simctl-copy')
        other.write_bytes(self.tool.read_bytes())
        self.assertFalse(p.is_direct_dispatcher(self.current, self.original, str(other), self.tools, 100))
        other = self.tool.parent.parent / 'simctl'
        other.write_bytes(self.tool.read_bytes())
        self.assertFalse(p.is_direct_dispatcher(self.current, self.original, str(other), self.tools, 100))

    def test_process_generation_uid_pid_and_parent_must_remain_exact(self):
        for key, value in [('pid', 124), ('uid', 502), ('ppid', 101),
                           ('start_sec', 1001), ('start_usec', 2)]:
            with self.subTest(key=key):
                changed = SimpleNamespace(**(vars(self.current) | {key: value}))
                self.assertFalse(p.is_direct_dispatcher(changed, self.original, str(self.tool), self.tools, 100))
        self.assertFalse(p.is_direct_dispatcher(self.current, self.original, str(self.tool), self.tools, 101))

    def test_missing_or_replaced_tool_never_grants_sampling(self):
        self.assertFalse(p.is_direct_dispatcher(None, self.original, str(self.tool), self.tools, 100))
        self.assertFalse(p.is_direct_dispatcher(self.current, None, str(self.tool), self.tools, 100))
        self.assertFalse(p.is_direct_dispatcher(self.current, self.original, '/nonexistent/query-tool', self.tools, 100))
        self.selected.unlink()
        self.selected.write_bytes(b'different executable')
        self.assertFalse(p.is_direct_dispatcher(self.current, self.original, str(self.tool), self.tools, 100))


if __name__ == '__main__':
    unittest.main()
