import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("kbd_observe", Path(__file__).with_name("observe.py"))
observer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observer)


class KeyboardPreExecControls(unittest.TestCase):
    def test_exact_original_launchd_message_selects_only_matching_native_job_pid(self):
        # Actual retained41502134475 message/subsystem; native ndjson schema is
        # a separate execution requirement, not claimed by this translated row.
        row = dict(processID=26826, subsystem="user/501/com.apple.TextInput.kbd [34648]",
                   eventMessage="xpcproxy_sim spawned with pid 34648")
        self.assertEqual(observer.target_event(row, 26826, 501), (34648, "spawn"))
        self.assertIsNone(observer.target_event(row, 26825, 501))
        self.assertIsNone(observer.target_event(row, 26826, 502))
        self.assertIsNone(observer.target_event(row | {"eventMessage": "xpcproxy_sim spawned with pid 34649"}, 26826, 501))

    def test_running_event_removes_pending_proxy_but_other_states_do_not_select(self):
        row = dict(processID=26826, subsystem="user/501/com.apple.TextInput.kbd [34648]",
                   eventMessage="service state: running")
        self.assertEqual(observer.target_event(row, 26826, 501), (34648, "running"))
        self.assertIsNone(observer.target_event(row | {"eventMessage": "service state: xpcproxy"}, 26826, 501))

    def test_decoder_aliases_malformed_and_foreign_services_do_not_grant_a_sample(self):
        self.assertIsNone(observer.target_event(dict(pid=26826, message="xpcproxy_sim spawned with pid 34648",
             subsystem="user/501/com.apple.TextInput.kbd [34648]"), 26826, 501))
        for row in (None, [], {}, {"processID": 26826, "subsystem": None},
                    dict(processID=26826, subsystem="user/501/com.apple.imagent [34648]",
                         eventMessage="xpcproxy_sim spawned with pid 34648")):
            self.assertIsNone(observer.target_event(row, 26826, 501))

    def identities(self):
        root = Path("/Library/Developer/CoreSimulator/Volumes/iOS_23C54/RuntimeRoot")
        parent = SimpleNamespace(pid=26826, uid=501)
        identity = SimpleNamespace(pid=34648, uid=501, started=(100, 50), command=str(root / "usr/libexec/xpcproxy_sim"))
        basic = SimpleNamespace(pid=34648, uid=501, ppid=26826, start_sec=100, start_usec=50)
        return identity, basic, parent, root

    def test_target_requires_full_elapsed_delay_without_modifying_test_deadlines(self):
        identity, basic, parent, root = self.identities()
        self.assertFalse(observer.eligible(identity, basic, parent, root, 90, 104.9))
        self.assertTrue(observer.eligible(identity, basic, parent, root, 90, 105.00005))

    def test_pid_reuse_foreign_parent_uid_runtime_and_exec_are_not_profiled(self):
        identity, basic, parent, root = self.identities()
        for key, value in (("ppid", 26827), ("uid", 502), ("start_usec", 51), ("pid", 34649)):
            changed = SimpleNamespace(**(vars(basic) | {key: value}))
            self.assertFalse(observer.eligible(identity, changed, parent, root, 90, 106))
        identity.command = str(root / "usr/libexec/kbd")
        self.assertFalse(observer.eligible(identity, basic, parent, root, 90, 106))
        identity.command = "/usr/libexec/xpcproxy_sim"
        self.assertFalse(observer.eligible(identity, basic, parent, root, 90, 106))
        self.assertFalse(observer.eligible(identity, basic, parent, root, 101, 106))

    def test_absent_lifetime_is_not_a_grant(self):
        identity, basic, parent, root = self.identities()
        self.assertFalse(observer.eligible(None, basic, parent, root, 90, 106))
        self.assertFalse(observer.eligible(identity, None, parent, root, 90, 106))

    def test_empty_wrong_duplicate_zero_duration_or_failed_tests_never_qualify(self):
        names = [test.rsplit("/", 1)[1] for test in observer.TESTS]
        def started(name):
            return f"Test Case '-[iosAppUITests.IOSAppLaunchUITests {name}]' started.\n"
        def passed(name, duration="12.34"):
            return f"Test Case '-[iosAppUITests.IOSAppLaunchUITests {name}]' passed ({duration} seconds).\n"
        good = "".join(started(name) + passed(name) for name in names)
        self.assertTrue(observer.exact_roster(good))
        for log in ("", good + started(names[0]) + passed(names[0]), good.replace(names[0], "testOther"),
                    good.replace("12.34", "0"), good.replace("passed", "failed", 1)):
            self.assertFalse(observer.exact_roster(log))


if __name__ == "__main__":
    unittest.main()
