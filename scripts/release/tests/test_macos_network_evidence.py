from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract as contract


ENVIRONMENT = dict(
    GITHUB_ACTIONS="true", RUNNER_ENVIRONMENT="github-hosted", RUNNER_OS="macOS",
    RUNNER_ARCH="X64", GITHUB_REPOSITORY="Apdelrahman1911/parlor",
    GITHUB_JOB="desktop-macos-x64", GITHUB_RUN_ID="42704248797", GITHUB_RUN_ATTEMPT="1",
    PARLOR_FAILED_STEP_OUTCOME="failure",
)


class MacosNetworkWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.workflow = (contract.ROOT / ".github/workflows/production-verification.yml").read_text()
        self.job = contract.validation_job(self.workflow, "desktop-macos-x64")

    def test_original_gradle_command_and_failure_only_evidence_custody(self):
        self.assertIn("        id: macos_x64_verification\n", self.job)
        self.assertIn("        if: failure() && steps.macos_x64_verification.outcome == 'failure'\n", self.job)
        self.assertEqual(self.job.count("run: ./gradlew productionDesktopCheck :composeApp:downloadKotlinNativeDistribution --dependency-verification=strict --no-daemon --stacktrace --console=plain"), 1)
        self.assertIn("-network.json\n", contract.validation_step(self.job, "Upload Desktop verification descriptors"))
        order = ["Resolve and test the macOS x64 host-selected graphs", "Collect failure-only macOS network evidence",
                 "Stop Gradle after macos-x64", "Upload Desktop verification descriptors", "Clean only attested verification outputs"]
        self.assertEqual([self.job.index("- name: " + name + "\n") for name in order],
                         sorted(self.job.index("- name: " + name + "\n") for name in order))

    def test_contract_rejects_success_path_retry_unbounded_or_unretained_capture(self):
        contract.verify_macos_network_evidence(self.job)
        mutations = (
            ("failure() && steps.macos_x64_verification.outcome == 'failure'", "always()"),
            ("timeout-minutes: 1\n", "timeout-minutes: 2\n"),
            ("PARLOR_FAILED_STEP_OUTCOME: ${{ steps.macos_x64_verification.outcome }}", "PARLOR_FAILED_STEP_OUTCOME: failure"),
            ("run: python3 -B scripts/ci/macos_network_evidence.py", "run: ./gradlew productionDesktopCheck"),
            ("        id: macos_x64_verification\n", ""),
            ("-network.json\n", "-unretained.json\n"),
            ("Collect failure-only macOS network evidence\n", "Collect failure-only macOS network evidence\n        continue-on-error: true\n"),
            ("      - name: Stop Gradle after macos-x64\n", "      - name: Retry Gradle\n        run: ./gradlew productionDesktopCheck\n\n      - name: Stop Gradle after macos-x64\n"),
        )
        for original, replacement in mutations:
            with self.subTest(original=original), self.assertRaises(RuntimeError):
                contract.verify_macos_network_evidence(self.job.replace(original, replacement, 1))

    def test_only_macos_x64_artifact_adds_network_snapshot(self):
        for name in contract.VERIFICATION_JOBS:
            if name in {"desktop-android", "desktop-linux-arm64", "desktop-macos-x64", "desktop-windows-x64",
                        "ios-kmp", "ios", "ios-release-simulator", "ios-release-device", "ios-release-x64"}:
                with self.subTest(job=name):
                    self.assertEqual(sum(path.endswith("-network.json") for path in contract.current_verification_artifact_paths(name)),
                                     int(name == "desktop-macos-x64"))


class MacosNetworkEvidenceTests(unittest.TestCase):
    def setUp(self):
        from scripts.ci import macos_network_evidence
        self.collector = macos_network_evidence
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.environment = ENVIRONMENT | {"RUNNER_TEMP": str(self.root)}

    def test_runner_admission_is_exact_and_failure_only(self):
        self.assertEqual(self.collector.runner_task(self.environment), {name: self.environment[name] for name in self.collector.TASK_KEYS})
        for name in self.collector.REQUIRED_ENVIRONMENT:
            for value in ("", "unexpected"):
                with self.subTest(name=name, value=value), self.assertRaises(RuntimeError):
                    self.collector.runner_task(self.environment | {name: value})
        for value in ("success", "cancelled", "skipped"):
            with self.subTest(outcome=value), self.assertRaises(RuntimeError):
                self.collector.runner_task(self.environment | {"PARLOR_FAILED_STEP_OUTCOME": value})

    def test_invalid_run_identifiers_cannot_select_output_paths(self):
        for name in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"):
            for value in ("", "0", "01", "../other", "1;echo", "١", "1" * 21):
                with self.subTest(name=name, value=value), self.assertRaises(RuntimeError):
                    self.collector.runner_task(self.environment | {name: value})

    def test_commands_are_read_only_fixed_public_hosts_without_shell_or_network_overrides(self):
        commands = self.collector.commands()
        self.assertEqual(len(commands), 3)
        self.assertEqual(commands[0], ("/usr/sbin/scutil", "--dns"))
        self.assertEqual(self.collector.PUBLIC_HOSTS, ("services.gradle.org", "github.com"))
        self.assertEqual(commands[1:], tuple((sys.executable, "-I", "-c", self.collector.RESOLVER_CODE, host)
                                            for host in self.collector.PUBLIC_HOSTS))
        for command in commands:
            self.assertNotIn("gradlew", " ".join(command))
            self.assertNotIn("sudo", command)

    def test_observation_preserves_failure_and_caps_each_output_stream(self):
        result = self.collector.observe((sys.executable, "-I", "-c", "import sys; print('x' * 40000); print('y' * 40000, file=sys.stderr); sys.exit(7)"), self.root, self.root)
        self.assertEqual(result["exit_code"], 7)
        self.assertEqual(result["status"], "COMPLETED")
        for stream in ("stdout", "stderr"):
            self.assertEqual(len(result[stream].encode()), self.collector.MAX_OUTPUT_BYTES)
            self.assertTrue(result[stream + "_truncated"])

    def test_actual_timeout_is_bounded_and_does_not_retry(self):
        with patch.object(self.collector, "OBSERVATION_TIMEOUT_SECONDS", 0.1):
            result = self.collector.observe((sys.executable, "-I", "-c", "import time; print('started', flush=True); time.sleep(10)"), self.root, self.root)
        self.assertEqual(result["status"], "TIMEOUT")
        self.assertIsNone(result["exit_code"])
        self.assertLess(result["elapsed_seconds"], 5)

    def test_os_error_is_explicit_not_a_dns_cause(self):
        result = self.collector.observe((str(self.root / "missing-command"),), self.root, self.root)
        self.assertEqual(result["status"], "UNAVAILABLE")
        self.assertEqual(result["error_type"], "FileNotFoundError")
        self.assertNotIn("cause", result)

    def test_resolver_output_is_deduplicated_and_bounded_without_real_dns(self):
        addresses = [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (f"192.0.2.{index}", 443)) for index in range(20)]
        output = io.StringIO()
        with patch.object(sys, "argv", ["resolver", "services.gradle.org"]), patch.object(socket, "getaddrinfo", return_value=addresses + addresses) as query, redirect_stdout(output):
            exec(self.collector.RESOLVER_CODE, {})
        query.assert_called_once_with("services.gradle.org", 443, type=socket.SOCK_STREAM)
        self.assertEqual(len(json.loads(output.getvalue())), 16)

    def test_source_claim_uses_bounded_git_and_rejects_dirty_or_invalid_source(self):
        completed = subprocess.CompletedProcess([], 0, stdout=b"a" * 40 + b"\n" + b"b" * 40 + b"\n")
        with patch.object(self.collector.subprocess, "run", return_value=completed) as command:
            self.assertEqual(self.collector.git_source(self.root), {"head": "a" * 40, "tree": "b" * 40})
        self.assertEqual(command.call_count, 2)
        self.assertTrue(all(call.kwargs["timeout"] == 5 and call.kwargs["check"] for call in command.call_args_list))
        with patch.object(self.collector.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout=b"bad\n")), self.assertRaises(RuntimeError):
            self.collector.git_source(self.root)
        with patch.object(self.collector.subprocess, "run", side_effect=subprocess.CalledProcessError(1, ["git", "diff", "--quiet"])), self.assertRaises(subprocess.CalledProcessError):
            self.collector.git_source(self.root)

    def test_receipt_binds_source_task_timing_and_observation_only_scope(self):
        source = {"head": "a" * 40, "tree": "b" * 40}
        for name in self.collector.SOURCE_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        with patch.object(self.collector, "git_source", return_value=source), patch.object(self.collector, "observe", return_value={"status": "TIMEOUT"}) as observation:
            target = self.collector.collect(self.environment, self.root)
            with self.assertRaises(FileExistsError):
                self.collector.collect(self.environment, self.root)
        receipt = json.loads(target.read_text())
        self.assertEqual(observation.call_count, 3)
        self.assertEqual(receipt["source"], source | {"files": {name: hashlib.sha256(name.encode()).hexdigest() for name in self.collector.SOURCE_FILES}})
        self.assertEqual(receipt["task"], self.collector.runner_task(self.environment))
        self.assertEqual(receipt["result"], "OBSERVATION_ONLY_NOT_QUALIFICATION")
        self.assertFalse(receipt["original_failed_lookup_captured"])
        self.assertEqual(receipt["original_step_outcome"], "failure")
        self.assertLessEqual(receipt["started_at"], receipt["finished_at"])
        self.assertNotIn("SECRET", target.read_text())

    def test_noncanonical_temp_or_untrusted_source_is_rejected_before_probing(self):
        for temporary in ("relative", str(self.root / "absent")):
            with patch.object(self.collector, "observe") as observation, self.assertRaises(RuntimeError):
                self.collector.collect(self.environment | {"RUNNER_TEMP": temporary}, self.root)
            observation.assert_not_called()
        for name in self.collector.SOURCE_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        source_path = self.root / self.collector.SOURCE_FILES[0]
        source_path.unlink()
        source_path.symlink_to(self.root / self.collector.SOURCE_FILES[1])
        with patch.object(self.collector, "git_source", return_value={"head": "a" * 40, "tree": "b" * 40}), patch.object(self.collector, "observe") as observation, self.assertRaises(RuntimeError):
            self.collector.collect(self.environment, self.root)
        observation.assert_not_called()

    def test_main_rejects_non_darwin_and_extra_arguments(self):
        with patch.object(self.collector.sys, "platform", "linux"), patch.object(self.collector.sys, "argv", ["collector"]), patch.object(self.collector, "collect") as collection, redirect_stdout(io.StringIO()):
            self.assertEqual(self.collector.main(), 1)
        collection.assert_not_called()
        with patch.object(self.collector.sys, "platform", "darwin"), patch.object(self.collector.sys, "argv", ["collector", "unexpected"]), patch.object(self.collector, "collect") as collection, redirect_stdout(io.StringIO()):
            self.assertEqual(self.collector.main(), 1)
        collection.assert_not_called()


if __name__ == "__main__":
    unittest.main()
