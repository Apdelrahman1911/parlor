"""Controlled Android teardown schedules; not native-device or original-failure evidence."""
from __future__ import annotations

import shlex
import subprocess
import sys
import unittest
from unittest.mock import Mock, patch

from scripts.android import resume_process_contract as contract
from scripts.android import run_resume_process_acceptance as driver
from scripts.release.tests.test_android_resume_process import APP, COMPONENT, FIXTURE, NEW, OLD, output


@unittest.skipUnless(sys.platform == "linux", "Process acceptance uses a Linux-hosted shell")
class AndroidPidObservationShellTest(unittest.TestCase):
    def observe(self, stdout, exit_status):
        arguments = contract.pid_observation_command(APP)
        self.assertEqual(arguments[0], "shell")
        script = ("pidof() { printf '%s' " + shlex.quote(stdout) + "; return " + str(exit_status)
                  + "; }; " + arguments[1])
        result = subprocess.run(["/bin/sh", "-c", script], check=True, capture_output=True, text=True, timeout=5)
        self.assertEqual(result.stderr, "")
        return result.stdout

    def test_shell_pid_output_and_status_round_trip_without_extra_separator(self):
        for stdout in ("1234\n", "1234"):
            with self.subTest(stdout=stdout):
                observed = self.observe(stdout, 0)
                self.assertEqual(observed, "1234\nPARLOR_PIDOF_EXIT=0\n")
                self.assertEqual(contract.observed_pid(observed), 1234)
        observed = self.observe("", 1)
        self.assertEqual(observed, "\nPARLOR_PIDOF_EXIT=1\n")
        self.assertIsNone(contract.observed_pid(observed))
        contract.require_absent(observed)

    def test_shell_failed_and_ambiguous_pid_queries_remain_fail_closed(self):
        for stdout, exit_status in (("1234 5678\n", 0), ("1234\n5678\n", 0), ("", 0), ("1\n", 0),
                                    ("2147483648\n", 0), ("1234\n", 1), ("", 127),
                                    ("error: device offline\n", 1)):
            with self.subTest(stdout=stdout, exit_status=exit_status):
                observed = self.observe(stdout, exit_status)
                with self.assertRaises(RuntimeError):
                    contract.observed_pid(observed)
                with self.assertRaises(RuntimeError):
                    contract.require_absent(observed)


class AndroidInstrumentationRetirementTest(unittest.TestCase):
    def test_public_pid_observation_preserves_absence_and_rejects_transport_or_ambiguous_output(self):
        self.assertIsNone(contract.observed_pid("\nPARLOR_PIDOF_EXIT=1\n"))
        self.assertEqual(contract.observed_pid("1234\nPARLOR_PIDOF_EXIT=0\n"), 1234)
        for text in ("", "1234", "PARLOR_PIDOF_EXIT=0", "error: device offline", "1\nPARLOR_PIDOF_EXIT=0",
                     "1234 5678\nPARLOR_PIDOF_EXIT=0", "1234\nPARLOR_PIDOF_EXIT=1", "PARLOR_PIDOF_EXIT=127",
                     "private output\nPARLOR_PIDOF_EXIT=1", "2147483648\nPARLOR_PIDOF_EXIT=0",
                     "1234\n\nPARLOR_PIDOF_EXIT=0"):
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                contract.observed_pid(text)
        for package in ("me.parlor.android", APP + "; echo injected", APP + "\nother", "-option.debug"):
            with self.assertRaises(RuntimeError):
                contract.pid_observation_command(package)

    def test_already_retired_phase_needs_one_observation_and_no_sleep(self):
        command = Mock(return_value="PARLOR_PIDOF_EXIT=1\n")
        with patch.object(driver.time, "monotonic", return_value=10.0), patch.object(driver.time, "sleep") as sleep:
            result = driver.await_instrumentation_exit(command, APP, 1234, 90.0)
        command.assert_called_once_with(*contract.pid_observation_command(APP), timeout=10)
        sleep.assert_not_called()
        self.assertEqual(result, {"result": "PASS", "pid": 1234, "observations": 1, "elapsed_seconds": 0.0})

    def test_only_the_original_pid_can_remain_while_waiting_for_retirement(self):
        for observed in ("4321\nPARLOR_PIDOF_EXIT=0\n", "1234 4321\nPARLOR_PIDOF_EXIT=0\n",
                         "transport text\nPARLOR_PIDOF_EXIT=1\n"):
            command = Mock(return_value=observed)
            with patch.object(driver.time, "monotonic", return_value=10.0), \
                 patch.object(driver.time, "sleep") as sleep, self.assertRaises(RuntimeError):
                driver.await_instrumentation_exit(command, APP, 1234, 90.0)
            command.assert_called_once()
            sleep.assert_not_called()

    def test_transport_failure_or_cancellation_is_not_retried_or_treated_as_absence(self):
        for failure in (RuntimeError("transport"), InterruptedError("cancelled"), KeyboardInterrupt()):
            command = Mock(side_effect=failure)
            with patch.object(driver.time, "monotonic", return_value=10.0), \
                 patch.object(driver.time, "sleep") as sleep, self.assertRaises(type(failure)):
                driver.await_instrumentation_exit(command, APP, 1234, 90.0)
            command.assert_called_once()
            sleep.assert_not_called()

    def test_native_phase_time_consumes_the_original_budget_and_is_not_restarted(self):
        now = [0.0]
        def phase(*_args):
            now[0] = 89.75
            return contract.phase_result(output(), FIXTURE, "prepare")
        def command(*args, **kwargs):
            self.assertEqual(args, contract.pid_observation_command(APP))
            self.assertEqual(kwargs, {"timeout": 0.25})
            now[0] = 90.0
            return "PARLOR_PIDOF_EXIT=1\n"
        action = Mock(side_effect=command)
        with patch.object(driver.time, "monotonic", side_effect=lambda: now[0]), \
             patch.object(driver.time, "sleep") as sleep, self.assertRaisesRegex(RuntimeError, "original phase budget"):
            driver.retired_phase(FIXTURE, "prepare", APP, COMPONENT, action, phase)
        action.assert_called_once()
        sleep.assert_not_called()

    def test_expired_or_invalid_original_deadline_never_queries_or_mutates_the_device(self):
        for deadline in (9.0, 10.0, 101.0, float("inf"), float("nan")):
            command = Mock()
            with patch.object(driver.time, "monotonic", return_value=10.0), self.assertRaises(RuntimeError):
                driver.await_instrumentation_exit(command, APP, 1234, deadline)
            command.assert_not_called()

    def test_observation_count_is_bounded_even_with_a_nonadvancing_clock(self):
        command = Mock(return_value="1234\nPARLOR_PIDOF_EXIT=0\n")
        with patch.object(driver.time, "monotonic", return_value=0.0), \
             patch.object(driver.time, "sleep"), self.assertRaisesRegex(RuntimeError, "observation budget"):
            driver.await_instrumentation_exit(command, APP, 1234, 90.0)
        self.assertEqual(command.call_count, contract.MAX_RETIREMENT_OBSERVATIONS)

    def test_failed_junit_phase_never_observes_retirement_or_starts_an_app(self):
        command = Mock()
        phase = Mock(side_effect=RuntimeError("failed original JUnit"))
        with self.assertRaisesRegex(RuntimeError, "failed original JUnit"):
            driver.retired_phase(FIXTURE, "prepare", APP, COMPONENT, command, phase)
        phase.assert_called_once_with(FIXTURE, "prepare", COMPONENT)
        command.assert_not_called()

    def test_junit_result_and_missing_or_mismatched_retirement_cannot_qualify_a_case(self):
        phase = contract.phase_result(output(), FIXTURE, "prepare")
        valid = {"result": "PASS", "pid": 1234, "observations": 1, "elapsed_seconds": 0.1}
        contract.require_retired_phase(dict(phase, instrumentation_retirement=valid))
        mutations = [None, {}, dict(valid, result="FAIL"), dict(valid, pid=4321), dict(valid, pid=1234.0),
                     dict(valid, pid=True),
                     dict(valid, observations=0), dict(valid, observations=True),
                     dict(valid, observations=contract.MAX_RETIREMENT_OBSERVATIONS + 1),
                     dict(valid, elapsed_seconds=-1), dict(valid, elapsed_seconds=90),
                     dict(valid, elapsed_seconds=float("nan")), dict(valid, elapsed_seconds=float("inf")),
                     dict(valid, elapsed_seconds=True), dict(valid, unrelated="not-admitted")]
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(RuntimeError):
                contract.require_retired_phase(dict(phase, instrumentation_retirement=mutation))
        with self.assertRaises(RuntimeError):
            contract.require_retired_phase(phase)

    def test_late_instrumentation_finish_cannot_kill_the_single_relaunch(self):
        # AOSP reports the result through InstrumentationReporter before its
        # "finished inst" package force-stop. Model that permitted ordering,
        # not a claim that this was the unrecorded original native exit cause.
        state = {"now": 0.0, "pid": None, "finish_at": None, "racy_launch": False}
        calls = []

        def advance(seconds):
            state["now"] += seconds
            if state["finish_at"] is not None and state["now"] >= state["finish_at"]:
                state["pid"] = None
                state["finish_at"] = None

        def phase(fixture, method, component):
            self.assertEqual(component, COMPONENT)
            calls.append(method)
            state["pid"] = 1234
            state["finish_at"] = state["now"] + 0.2
            return contract.phase_result(output(method, OLD if method == "prepare" else NEW, fixture),
                                         fixture, method)

        def command(*args, **_kwargs):
            if args[:3] == ("shell", "am", "start"):
                calls.append("launch")
                state["racy_launch"] = state["finish_at"] is not None
                state["pid"] = 4321
                return "Status: ok\n"
            if args[:3] == ("shell", "am", "force-stop"):
                calls.append("force-stop")
                self.assertEqual(args[3:], (APP,))
                state["pid"] = None
                return ""
            if args[:2] == ("shell", "pidof"):
                # The original driver queried immediately after am start -W.
                # Its old result watcher was not an app-retirement barrier.
                advance(0.2)
                if state["pid"] is None:
                    raise RuntimeError("Synthetic late finished-inst stop: pidof exit 1")
                return str(state["pid"]) + "\n"
            self.assertEqual(args[0], "shell")
            self.assertEqual(args, contract.pid_observation_command(APP))
            calls.append("observe")
            if state["racy_launch"]:
                advance(0.2)
            return ("PARLOR_PIDOF_EXIT=1\n" if state["pid"] is None else
                    f"{state['pid']}\nPARLOR_PIDOF_EXIT=0\n")

        with patch.object(driver.time, "monotonic", side_effect=lambda: state["now"]), \
             patch.object(driver.time, "sleep", side_effect=advance):
            result = driver.case_journey(FIXTURE, APP, COMPONENT, command, phase)
        self.assertEqual(result["result"], "PASS")
        self.assertFalse(state["racy_launch"])
        self.assertEqual(calls.count("prepare"), 1)
        self.assertEqual(calls.count("verify"), 1)
        self.assertEqual(calls.count("launch"), 1)
        self.assertEqual(calls.count("force-stop"), 1)
        self.assertIsNone(state["pid"])


if __name__ == "__main__":
    unittest.main()
