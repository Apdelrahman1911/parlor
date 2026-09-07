"""Synthetic lifecycle checks; never invokes Gradle or signals a real worker."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

LANE = Path(__file__).resolve().parent.parent / 'run_gradle_cycle.py'
spec = importlib.util.spec_from_file_location('owned_gradle_lane', LANE)
lane = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lane)

class Process:
    pid = 41000
    def __init__(self, error=None): self.error = error
    def wait(self, timeout=None):
        if self.error: raise self.error
        return 0
    def poll(self): return 0

class GroupRetirementTests(unittest.TestCase):
    def setUp(self):
        lane.OWNED_GROUPS.clear(); lane.OWNED_PROCESSES.clear(); lane.CYCLE_MARKER = ''

    def exercise(self, error=None):
        p = Process(error)
        active = []
        with tempfile.TemporaryDirectory(prefix='parlor-lane-group-test-') as d:
            with patch.object(lane.subprocess, 'Popen', return_value=p), \
                    patch.object(lane, 'track_processes', side_effect=lambda: active.append(set(lane.OWNED_GROUPS))), \
                    patch.object(lane.os, 'killpg') as signals:
                if error:
                    with self.assertRaises(type(error)):
                        lane.invoke(['fake-wrapper'], Path(d) / 'log', {})
                else:
                    self.assertEqual(0, lane.invoke(['fake-wrapper'], Path(d) / 'log', {}))
                signals.assert_not_called()
        self.assertEqual([{41000}], active)
        self.assertEqual(set(), lane.OWNED_GROUPS)

    def test_success_retires_group_immediately(self): self.exercise()
    def test_failure_retires_group_without_signalling_reaped_leader(self): self.exercise(RuntimeError('synthetic'))
    def test_cancellation_retires_group_without_signalling_reaped_leader(self): self.exercise(KeyboardInterrupt())
    def test_reused_group_cannot_adopt_an_unrelated_process(self):
        self.exercise()
        row = dict(pid=41001, parent=1, group=41000, started='synthetic-later', command='unrelated-task')
        with patch.object(lane, 'processes', return_value={41001:row}): lane.track_processes()
        self.assertEqual({}, lane.OWNED_PROCESSES)
    def test_observed_descendants_remain_bound_to_their_original_start(self):
        self.exercise()
        row = dict(pid=41001, parent=1, group=41000, started='synthetic-earlier', command='owned-worker')
        lane.OWNED_PROCESSES[41001] = row
        with patch.object(lane, 'processes', return_value={41001:row}): lane.track_processes()
        self.assertEqual({41001:row}, lane.OWNED_PROCESSES)

if __name__ == '__main__': unittest.main(verbosity=2)
