"""Execute the real wrapper with synthetic tools, not an Android runtime."""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = "scripts/android/run_release_managed_device_smoke.sh"
GPU = "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader"
RELEASE = "productionAndroidRuntimeCheck"
NAVIGATION = "productionAndroidNavigationRuntimeCheck"


class AndroidManagedGpuTest(unittest.TestCase):
    def run_wrapper(self, failed_task: str = "") -> tuple[int, list[list[str]]]:
        with TemporaryDirectory(prefix="parlor-gpu-shell-control-") as temporary:
            root = Path(temporary)
            script = root / SCRIPT
            script.parent.mkdir(parents=True)
            script.write_bytes((ROOT / SCRIPT).read_bytes())
            tools = root / "tools"
            tools.mkdir()
            calls = root / "calls.jsonl"
            gradle = root / "gradlew"
            gradle.write_text(f"#!{sys.executable}\n" + textwrap.dedent('''\
                import json, os, pathlib, sys
                with pathlib.Path(os.environ['CALLS']).open('a') as out:
                    out.write(json.dumps(sys.argv[1:]) + '\\n')
                if sys.argv[1] == os.environ['FAILED_TASK']:
                    sys.exit(23)
                '''))
            gradle.chmod(0o700)
            # Exercise the Linux invocation shape without making a fake
            # subreaper, hardware, emulator or runtime-success claim.
            supervisor = tools / "python3"
            supervisor.write_text(f"#!{sys.executable}\n" + textwrap.dedent('''\
                import subprocess, sys
                assert sys.argv[1:4] == [
                    '-B', 'scripts/android/supervise_managed_device.py', '--receipt']
                assert sys.argv[4] in (
                    'build/ci-evidence/android-managed-device-supervision.json',
                    'build/ci-evidence/android-navigation-device-supervision.json')
                assert sys.argv[5:7] == ['--', './gradlew']
                sys.exit(subprocess.run(sys.argv[6:]).returncode)
                '''))
            supervisor.chmod(0o700)
            for name, content in (
                ("uname", "#!/bin/sh\nprintf 'Linux\\n'\n"),
                ("keytool", "#!/bin/sh\nexit 0\n"),
            ):
                tool = tools / name
                tool.write_text(content)
                tool.chmod(0o700)
            env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"],
                       CALLS=str(calls), FAILED_TASK=failed_task, TMPDIR=str(root))
            result = subprocess.run(["bash", str(script)], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=15)
            rows = [json.loads(line) for line in calls.read_text().splitlines()]
            return result.returncode, rows

    def test_both_real_commands_use_supported_software_gpu_and_keep_gates(self) -> None:
        status, calls = self.run_wrapper()
        self.assertEqual(status, 0)
        self.assertEqual([args[0] for args in calls], [RELEASE, NAVIGATION, "--stop"])
        for args, variant in zip(calls[:2], ("release", "debug"), strict=True):
            self.assertEqual(args.count(GPU), 1)
            self.assertEqual(args.count("--info"), 1)
            self.assertIn(f"-Pparlor.androidRuntimeTestVariant={variant}", args)
            self.assertIn("--dependency-verification=strict", args)
            self.assertIn("--no-daemon", args)
            self.assertIn("--max-workers=2", args)
        self.assertTrue(any(arg.startswith("-Pandroid.injected.signing.store.file=")
                            for arg in calls[0]))
        self.assertFalse(any("signing" in arg for arg in calls[1]))

    def test_failed_release_propagates_without_replay_or_navigation(self) -> None:
        status, calls = self.run_wrapper(RELEASE)
        self.assertEqual(status, 23)
        self.assertEqual([args[0] for args in calls], [RELEASE, "--stop"])
        self.assertEqual(calls[0].count(GPU), 1)
        self.assertEqual(calls[0].count("--info"), 1)

    def test_failed_navigation_propagates_without_replaying_either_lane(self) -> None:
        status, calls = self.run_wrapper(NAVIGATION)
        self.assertEqual(status, 23)
        self.assertEqual([args[0] for args in calls], [RELEASE, NAVIGATION, "--stop"])
        self.assertEqual(calls[1].count(GPU), 1)
        self.assertEqual(calls[1].count("--info"), 1)
