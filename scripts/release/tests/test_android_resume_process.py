"""Portable protocol/ownership controls, not an emulator or process-death pass."""
from __future__ import annotations

from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.android import resume_process_contract as contract
from scripts.android import run_resume_process_acceptance as driver

ID = "12345678-1234-4234-8234-123456789abc"
OLD = "22345678-1234-4234-8234-123456789abc"
NEW = "32345678-1234-4234-8234-123456789abc"
APP = "me.parlor.android.debug"
COMPONENT = APP + ".test/android.test.InstrumentationTestRunner"
FIXTURE = contract.Fixture(ID, "Whodunit", "ar")


def output(method="prepare", nonce=OLD, fixture=FIXTURE):
    def event(fields, code):
        return "".join(f"INSTRUMENTATION_STATUS: {key}={value}\n" for key, value in fields.items()) + \
            f"INSTRUMENTATION_STATUS_CODE: {code}\n"
    junit = {"id": "InstrumentationTestRunner", "numtests": "1", "class": contract.CLASS,
             "test": method, "current": "1", "stream": "."}
    marker = {"parlorPhase": "prepared" if method == "prepare" else "verified", "parlorFixtureId": fixture.id,
              "parlorLanguage": fixture.language, "parlorGame": fixture.game, "parlorProcessNonce": nonce,
              "parlorEntryKind": fixture.entry_kind, "parlorPid": "1234"}
    return (event(junit, 1) + event(marker, 2) + event(junit, 0) +
            "INSTRUMENTATION_RESULT: stream=\nTest results for InstrumentationTestRunner=.\n"
            "Time: 1.234\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n").encode()


def manifests():
    namespace = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    return (f'<manifest package="{APP}" {namespace}><application android:debuggable="true"/></manifest>',
            f'<manifest package="{APP}.test" {namespace}><instrumentation '
            f'android:name="android.test.InstrumentationTestRunner" android:targetPackage="{APP}"/></manifest>')


class AndroidResumeProcessContractTest(unittest.TestCase):
    def test_explicit_phase_selection_keeps_locale_game_and_fixture(self):
        for method in ("prepare", "verify"):
            args = FIXTURE.arguments(method, COMPONENT)
            self.assertEqual(args[:5], ["shell", "am", "instrument", "-w", "-r"])
            self.assertIn(contract.CLASS + "#" + method, args)
            self.assertEqual(args[-1], COMPONENT)
            self.assertIn(ID, args)
            self.assertIn("ar", args)
            self.assertIn("Whodunit", args)
            self.assertIn("Local", args)
            self.assertNotIn("delay_msec", args)
            self.assertNotIn("clearPackageData", args)

    def test_rejects_invalid_fixture_phase_and_runner(self):
        for values in (("../other", "Mafia", "en"), (ID.upper(), "Mafia", "en"),
                       (ID, "Unknown", "en"), (ID, "Mafia", "system"), (ID, "Mafia", "en", "Other")):
            with self.subTest(values=values), self.assertRaises((RuntimeError, ValueError)):
                contract.Fixture(*values)
        for method, component in (("retry", COMPONENT), ("prepare", "me.parlor.android/OtherRunner"),
                                  ("prepare", COMPONENT + "; echo bypass")):
            with self.assertRaises(RuntimeError):
                FIXTURE.arguments(method, component)

    def test_accepts_exact_prepared_and_verified_public_results(self):
        for method, nonce in (("prepare", OLD), ("verify", NEW)):
            result = contract.phase_result(output(method, nonce), FIXTURE, method)
            self.assertEqual(result["process_nonce"], nonce)
            self.assertEqual(result["junit_methods"], 1)
            self.assertEqual(result["pid"], 1234)
            self.assertEqual(result["language"], "ar")
            self.assertEqual(contract.phase_result(output(method, nonce).replace(b"\n", b"\r\n"), FIXTURE, method), result)

    def test_exit0_or_final_code_without_exact_junit_success_is_not_a_pass(self):
        for raw in (b"INSTRUMENTATION_CODE: -1\n", output().replace(b"OK (1 test)", b""),
                    output().replace(b"OK (1 test)", b"OK (0 tests)"), output() + b"OK (1 test)\n"):
            with self.assertRaises(RuntimeError):
                contract.phase_result(raw, FIXTURE, "prepare")

    def test_rejects_failed_ignored_unknown_and_duplicate_statuses(self):
        for replacement in (b"-2", b"-1", b"-3", b"3", b"0\nINSTRUMENTATION_STATUS_CODE: 0"):
            with self.subTest(replacement=replacement), self.assertRaises(RuntimeError):
                contract.phase_result(output().replace(b"INSTRUMENTATION_STATUS_CODE: 0",
                    b"INSTRUMENTATION_STATUS_CODE: " + replacement), FIXTURE, "prepare")

    def test_rejects_different_or_multiple_native_methods(self):
        for before, after in ((b"numtests=1", b"numtests=2"), (b"current=1", b"current=2"),
                              (contract.CLASS.encode(), b"other.Test"), (b"test=prepare", b"test=verify"),
                              (b"id=InstrumentationTestRunner", b"id=OtherRunner")):
            with self.assertRaises(RuntimeError):
                contract.phase_result(output().replace(before, after), FIXTURE, "prepare")

    def test_rejects_wrong_marker_fixture_locale_game_or_phase(self):
        for before, after in ((ID.encode(), NEW.encode()), (b"parlorLanguage=ar", b"parlorLanguage=en"),
                              (b"parlorGame=Whodunit", b"parlorGame=Mafia"),
                              (b"parlorEntryKind=Local", b"parlorEntryKind=Multiplayer"),
                              (b"parlorPhase=prepared", b"parlorPhase=verified")):
            with self.assertRaises(RuntimeError):
                contract.phase_result(output().replace(before, after), FIXTURE, "prepare")

    def test_missing_duplicate_extra_and_malformed_fields_fail_closed(self):
        marker = b"INSTRUMENTATION_STATUS: parlorLanguage=ar\n"
        for raw in (output().replace(marker, b""), output().replace(marker, marker * 2),
                    output().replace(marker, marker + b"INSTRUMENTATION_STATUS: privateState=not-allowed\n"),
                    output().replace(marker, b"INSTRUMENTATION_STATUS: missing-separator\n")):
            with self.assertRaises(RuntimeError):
                contract.phase_result(raw, FIXTURE, "prepare")

    def test_missing_duplicate_late_or_aborted_terminal_is_not_accepted(self):
        terminal = b"INSTRUMENTATION_CODE: -1\n"
        for raw in (output().replace(terminal, b""), output() + terminal,
                    output() + b"INSTRUMENTATION_STATUS: late=1\n",
                    output() + b"INSTRUMENTATION_ABORTED: app died\n"):
            with self.assertRaises(RuntimeError):
                contract.phase_result(raw, FIXTURE, "prepare")

    def test_empty_oversized_or_invalid_encoding_is_not_a_result(self):
        for raw in (b"", b" " * (contract.MAX_PHASE_OUTPUT + 1), b"\xff" + output()):
            with self.assertRaises((RuntimeError, UnicodeError)):
                contract.phase_result(raw, FIXTURE, "prepare")

    def test_pid_and_independent_absence_queries_reject_transport_errors(self):
        self.assertEqual(contract.live_pid("1234\n"), 1234)
        contract.require_absent("\nPARLOR_PIDOF_EXIT=1\n")
        for text in ("", "0", "1", "-1234", "1234 1235", "2147483648", "error: device offline"):
            with self.assertRaises(RuntimeError):
                contract.live_pid(text)
        for text in ("", "1", "PARLOR_PIDOF_EXIT=0", "1234\nPARLOR_PIDOF_EXIT=1", "error: device offline"):
            with self.assertRaises(RuntimeError):
                contract.require_absent(text)

    def test_same_process_or_mismatched_phases_are_not_process_death(self):
        prepared = contract.phase_result(output(), FIXTURE, "prepare")
        verified = contract.phase_result(output("verify", NEW), FIXTURE, "verify")
        result = contract.complete_case(FIXTURE, prepared, verified, 1234, "PARLOR_PIDOF_EXIT=1")
        self.assertTrue(result["explicit_force_stop_and_independent_absence"])
        self.assertFalse(result["physical_device_verified"])
        for key, value in (("process_nonce", OLD), ("fixture", NEW), ("result", "FAIL"), ("junit_methods", 0),
                           ("entry_kind", "Multiplayer")):
            altered = dict(verified, **{key: value})
            with self.assertRaises(RuntimeError):
                contract.complete_case(FIXTURE, prepared, altered, 1234, "PARLOR_PIDOF_EXIT=1")

    def test_process_correlation_values_and_pid_must_be_well_formed(self):
        for before, after in ((OLD.encode(), b"not-a-nonce"), (b"parlorPid=1234", b"parlorPid=0")):
            with self.assertRaises((RuntimeError, ValueError)):
                contract.phase_result(output().replace(before, after), FIXTURE, "prepare")


class AndroidResumeProcessDriverTest(unittest.TestCase):
    def exercise(self, failure=None):
        calls = []
        def phase(fixture, method, component):
            calls.append(method)
            self.assertEqual(component, COMPONENT)
            if failure == method:
                raise RuntimeError("synthetic phase failure")
            return contract.phase_result(output(method, OLD if method == "prepare" else NEW, fixture), fixture, method)
        def command(*args):
            if args[:3] == ("shell", "am", "start"):
                calls.append("launch")
                return "Status: error\n" if failure == "launch" else "Status: ok\n"
            if args[:2] == ("shell", "pidof"):
                calls.append("pid")
                return "" if failure == "pid" else "4321\n"
            if args[:3] == ("shell", "am", "force-stop"):
                calls.append("force-stop")
                self.assertEqual(args[3:], (APP,))
                if failure == "force-stop":
                    raise RuntimeError("synthetic force-stop error")
                return ""
            self.assertEqual(args[0], "shell")
            self.assertTrue(args[1].startswith(f"pidof {APP}; result=$?; "))
            calls.append("absent")
            return "4321\nPARLOR_PIDOF_EXIT=0" if failure == "absent" else "PARLOR_PIDOF_EXIT=1\n"
        if failure:
            with self.assertRaises(RuntimeError):
                driver.case_journey(FIXTURE, APP, COMPONENT, command, phase)
        else:
            self.assertEqual(driver.case_journey(FIXTURE, APP, COMPONENT, command, phase)["result"], "PASS")
        return calls

    def test_actual_launch_force_stop_absence_and_distinct_phase_order(self):
        self.assertEqual(self.exercise(), ["prepare", "launch", "pid", "force-stop", "absent", "verify"])

    def test_each_boundary_failure_stops_without_replay_or_next_phase(self):
        expected = ["prepare", "launch", "pid", "force-stop", "absent", "verify"]
        for index, failure in enumerate(expected):
            with self.subTest(failure=failure):
                self.assertEqual(self.exercise(failure), expected[:index + 1])

    def test_only_normal_debug_app_and_existing_targeted_runner_are_admitted(self):
        app, test = manifests()
        self.assertEqual(driver.inspect_manifests(app, test, APP), COMPONENT)
        for candidate_app, candidate_test, identity in (
            (app, test, "me.parlor.android"), (app, test, "me.parlor.android.test"),
            (app.replace('debuggable="true"', 'debuggable="false"'), test, APP),
            (app.replace('debuggable="true"', 'debuggable="true" android:testOnly="true"'), test, APP),
            (app, test.replace('targetPackage="' + APP, 'targetPackage="other'), APP),
            (app, test.replace("android.test.InstrumentationTestRunner", "other.Runner"), APP),
            (app, test.replace("</manifest>", '<instrumentation/></manifest>'), APP),
        ):
            with self.assertRaises(RuntimeError):
                driver.inspect_manifests(candidate_app, candidate_test, identity)

    def test_local_environment_is_refused_before_any_tool_or_state_change(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(driver, "run") as run:
            target = Path(temporary) / "must-not-be-created"
            with self.assertRaises(RuntimeError):
                driver.preflight(target)
            self.assertFalse(target.exists())
            run.assert_not_called()

    def test_native_phases_have_no_default_single_process_discovery_or_private_export(self):
        source = (driver.ROOT / driver.SOURCE_PATHS[2]).read_text()
        self.assertIn("fun prepare()", source)
        self.assertIn("fun verify()", source)
        self.assertNotIn("fun test", source)
        self.assertIn("originalProcess != PROCESS_NONCE", source)
        self.assertIn("AndroidLegalResumeFixtures.create", source)
        self.assertIn("AndroidPublicRecoveryUi(instrumentation)", source)
        self.assertNotIn("pm clear", source)
        report = source.split("private fun report(", 1)[1].split("private companion object", 1)[0]
        self.assertNotIn("payload", report)
        self.assertNotIn("encodeTo", report)
        self.assertEqual(report.count("putString("), 6)
        self.assertEqual(report.count("putInt("), 1)


class AndroidResumeProcessExecutionTest(unittest.TestCase):
    def execute(self, failure=None):
        """Fake tools test fail-closed orchestration, not native acceptance."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "composeApp/build/outputs/apk/debug/app-debug.apk"
            test = root / "composeApp/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
            for path in (app, test):
                path.parent.mkdir(parents=True)
                path.write_bytes(path.name.encode())
            destination = root / "build/ci-evidence/process"
            phases, actions, lifecycle = [], [], []
            configuration = {"applications": {"android": {"debug_application_id": APP}},
                             "toolchains": {"android_navigation_device": {"api_level": 34}}}
            source = {"head": "1" * 40, "tree": "2" * 40, "files": {}}
            app_xml, test_xml = manifests()

            def inspect(command):
                self.assertEqual(command[1:3], ["manifest", "print"])
                return app_xml if command[-1] == str(app) else test_xml

            def command(*arguments, **kwargs):
                actions.append(arguments)
                if arguments[0] == "install":
                    if failure == "install":
                        return "Failure [INSTALL_FAILED_TEST_ONLY]"
                    return "Success"
                if arguments[:3] == ("shell", "am", "start"):
                    return "Status: ok\n"
                if arguments[:2] == ("shell", "pidof"):
                    return "4321\n"
                if arguments[:3] == ("shell", "am", "force-stop"):
                    return ""
                return "PARLOR_PIDOF_EXIT=1\n"

            def phase(fixture, method, component):
                self.assertEqual(component, COMPONENT)
                phases.append((fixture.id, fixture.game, fixture.language, fixture.entry_kind, method))
                if failure == "phase" and len(phases) == 4:
                    raise RuntimeError("injected native phase error")
                return contract.phase_result(output(method, OLD if method == "prepare" else NEW, fixture),
                                             fixture, method)

            @contextmanager
            def emulator(*args):
                lifecycle.append("allocated")
                try:
                    yield command, phase
                finally:
                    lifecycle.append("retired")
                    if failure == "cleanup":
                        raise RuntimeError("injected owned cleanup error")
                    if failure == "apk-changed":
                        app.write_bytes(b"changed after native execution")

            with patch.object(driver, "ROOT", root), \
                 patch.object(driver, "preflight", return_value=(root / "sdk", root / "avd", configuration, source)), \
                 patch.object(driver, "run", side_effect=inspect), \
                 patch.object(driver, "owned_emulator", side_effect=emulator):
                if failure:
                    with self.assertRaises(RuntimeError):
                        driver.execute(destination)
                else:
                    self.assertEqual(driver.execute(destination)["result"], "PASS")
            result = json.loads((destination / "result.json").read_text())
            self.assertEqual(result["source"], source)
            self.assertEqual(result["result"], "FAIL" if failure else "PASS")
            self.assertEqual(lifecycle, ["allocated", "retired"])
            self.assertFalse(result["physical_device_verified"])
            self.assertFalse(result["ios_protection_verified"])
            return result, phases, actions

    def test_twelve_game_language_entry_pairs_each_have_two_distinct_invocations(self):
        result, phases, actions = self.execute()
        expected = [(game, language, kind) for game in contract.GAMES for language in contract.LANGUAGES
                    for kind in contract.ENTRY_KINDS]
        self.assertEqual([(case["game"], case["language"], case["entry_kind"]) for case in result["cases"]], expected)
        self.assertEqual(len(phases), 24)
        self.assertEqual(len({value[0] for value in phases}), 12)
        self.assertEqual([row[1:] for row in phases],
                         [(game, language, kind, method) for game, language, kind in expected for method in ("prepare", "verify")])
        self.assertEqual(len([row for row in actions if row[:3] == ("shell", "am", "force-stop")]), 12)
        self.assertEqual([row[0] for row in actions].count("install"), 2)
        self.assertEqual(set(result["apks"]), {"app", "test"})

    def test_failed_phase_retains_partial_receipt_and_stops_the_matrix_without_replay(self):
        result, phases, _ = self.execute("phase")
        self.assertEqual(len(result["cases"]), 1)
        self.assertEqual(len(phases), 4)
        self.assertEqual([row[1:4] for row in phases], [("LastLight", "en", "Local")] * 2 +
                         [("LastLight", "en", "Multiplayer")] * 2)

    def test_failed_install_never_runs_native_phases(self):
        result, phases, actions = self.execute("install")
        self.assertEqual(phases, [])
        self.assertEqual(result["cases"], [])
        self.assertEqual(len(actions), 1)

    def test_owned_cleanup_or_apk_mutation_invalidates_passing_native_results(self):
        for failure in ("cleanup", "apk-changed"):
            with self.subTest(failure=failure):
                result, phases, _ = self.execute(failure)
                self.assertEqual(len(result["cases"]), 12)
                self.assertEqual(len(phases), 24)

    def test_existing_output_is_never_overwritten_or_replayed(self):
        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary) / "process"
            destination.mkdir()
            receipt = destination / "result.json"
            receipt.write_bytes(b"retained failure")
            with patch.object(driver, "preflight", return_value=(None, None, {}, {})), \
                 patch.object(driver, "owned_emulator") as emulator:
                with self.assertRaises(FileExistsError):
                    driver.execute(destination)
                emulator.assert_not_called()
            self.assertEqual(receipt.read_bytes(), b"retained failure")


class AndroidResumeProcessEmulatorOwnershipTest(unittest.TestCase):
    def exercise(self, failure=None):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            avd, evidence = root / "avd", root / "evidence"
            avd.mkdir()
            evidence.mkdir()
            calls = []
            process = Mock()
            process.poll.return_value = None
            if failure == "terminate-timeout":
                process.wait.side_effect = [subprocess.TimeoutExpired("emulator", 15), 0]

            def run(command, **kwargs):
                calls.append(command)
                environment = kwargs["env"]
                self.assertEqual(environment["ANDROID_ADB_SERVER_PORT"], str(driver.ADB_PORT))
                self.assertEqual(environment["ADB_SERVER_SOCKET"], f"tcp:localhost:{driver.ADB_PORT}")
                if command[-1] == "start-server":
                    return "" if failure == "existing-adb" else \
                        "* daemon not running; starting now at tcp:5039\n* daemon started successfully\n"
                if command[-1] == "sys.boot_completed":
                    return "1\n"
                if command[-1] == "ro.kernel.qemu":
                    return "1\n"
                if command[-1] == "ro.build.version.sdk":
                    return "35\n" if failure == "wrong-api" else "34\n"
                return ""

            def body():
                with driver.owned_emulator(root / "sdk", avd, evidence,
                                           {"system_image_package": "system-images;android-34;google_apis;x86_64",
                                            "api_level": 34}):
                    if failure == "native-body":
                        raise RuntimeError("injected acceptance body failure")

            with patch.object(driver, "unused_ports") as unused, \
                 patch.object(driver, "run", side_effect=run), \
                 patch.object(driver.subprocess, "Popen", return_value=process) as launched:
                if failure in ("existing-adb", "wrong-api", "native-body"):
                    with self.assertRaises(RuntimeError):
                        body()
                else:
                    body()
                unused.assert_called_once_with((driver.ADB_PORT, driver.EMULATOR_PORT, driver.EMULATOR_PORT + 1))
                if failure == "existing-adb":
                    launched.assert_not_called()
                    process.terminate.assert_not_called()
                    self.assertNotIn("kill-server", [command[-1] for command in calls])
                else:
                    launched.assert_called_once()
                    arguments = launched.call_args.args[0]
                    self.assertEqual(arguments[arguments.index("-gpu") + 1], "swiftshader")
                    self.assertEqual(arguments[arguments.index("-port") + 1], str(driver.EMULATOR_PORT))
                    process.terminate.assert_called_once()
                    self.assertEqual([command[-1] for command in calls].count("kill-server"), 1)
                    if failure == "terminate-timeout":
                        process.kill.assert_called_once()
                    else:
                        process.kill.assert_not_called()

    def test_success_retires_only_its_process_and_private_server(self):
        self.exercise()

    def test_boot_or_native_body_failure_still_retires_owned_resources(self):
        for failure in ("wrong-api", "native-body"):
            with self.subTest(failure=failure):
                self.exercise(failure)

    def test_server_that_was_not_freshly_started_is_never_adopted_or_stopped(self):
        self.exercise("existing-adb")

    def test_nonterminating_owned_emulator_gets_one_kill_and_wait(self):
        self.exercise("terminate-timeout")

    def test_port_collision_refuses_all_emulator_or_server_allocation(self):
        with patch.object(driver, "unused_ports", side_effect=OSError("occupied")), \
             patch.object(driver, "run") as run, patch.object(driver.subprocess, "Popen") as launched:
            with self.assertRaises(OSError):
                with driver.owned_emulator(Path("sdk"), Path("avd"), Path("output"), {}):
                    self.fail("A collision cannot allocate an emulator")
            run.assert_not_called()
            launched.assert_not_called()


class AndroidResumeProcessWorkflowTest(unittest.TestCase):
    def workflow(self):
        from scripts.release import workflow_contract
        source = (driver.ROOT / ".github/workflows/production-verification.yml").read_text()
        return workflow_contract, workflow_contract.validation_job(source, "desktop-android")

    def test_exact_mandatory_full_only_step_reuses_existing_apks_and_evidence_selection(self):
        rules, job = self.workflow()
        rules.verify_android_resume_process(job)
        # The integrated validator, rather than this test alone, owns the gate.
        self.assertIn("    verify_android_resume_process(job)\n",
                      (driver.ROOT / "scripts/release/workflow_contract.py").read_text())
        step = rules.validation_step(job, "Verify Android saved-session removal across process restart")
        self.assertNotIn("./gradlew", step)
        self.assertNotIn("workflow_dispatch' &&", step)

    def test_gate_cannot_be_disabled_replaced_retried_or_detached_from_owned_cleanup(self):
        rules, job = self.workflow()
        name = "Verify Android saved-session removal across process restart"
        step = rules.validation_step(job, name)
        mutations = (
            step.replace("'full'", "'android-cleanup-only'"),
            step.replace("--isolated-avd", ""),
            step.replace("supervise_managed_device.py", "other.py"),
            step.replace("scripts.android.run_resume_process_acceptance", "other"),
            step.replace("--output build/ci-evidence/", "--output build/uncollected/"),
            step.replace("set -euo pipefail", "set +e"),
            step + "          true\n",
            step.replace("        shell: bash", "        continue-on-error: true\n        shell: bash"),
            step.replace("python3 -B -m scripts.android.run_resume_process_acceptance",
                         "retry python3 -B -m scripts.android.run_resume_process_acceptance"),
        )
        for candidate in mutations:
            with self.subTest(candidate=candidate), self.assertRaises(RuntimeError):
                rules.verify_android_resume_process(job.replace(step, candidate, 1))
        for candidate in (job.replace(name, "Removed"),
                          job + "\n      - name: " + name + "\n" + step,
                          job.replace("            build/ci-evidence/\n", "")):
            with self.assertRaises(RuntimeError):
                rules.verify_android_resume_process(candidate)

    def test_native_execution_cannot_precede_apk_build_or_follow_artifact_upload(self):
        rules, job = self.workflow()
        name = "Verify Android saved-session removal across process restart"
        step = "\n      - name: " + name + "\n" + rules.validation_step(job, name)
        removed = job.replace(step, "", 1)
        for marker in ("\n      - name: Run release Android managed-device smoke tests\n",
                       "\n      - name: Clean only attested verification outputs\n"):
            candidate = removed.replace(marker, step + marker, 1)
            with self.assertRaises(RuntimeError):
                rules.verify_android_resume_process(candidate)


if __name__ == "__main__":
    unittest.main()
