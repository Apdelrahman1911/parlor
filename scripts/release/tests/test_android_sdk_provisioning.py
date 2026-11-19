from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import textwrap
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts.release import workflow_contract


ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/production-verification.yml"


class AndroidSdkProvisioningTest(unittest.TestCase):
    """Execute the actual setup Bash with synthetic SDK tools; no emulator/network."""

    def setup_run(self, *, revision: str = "14", missing: bool = False,
                  installer_exit: int = 0) -> tuple[subprocess.CompletedProcess, list[str]]:
        block = workflow_contract.validation_step(workflow_contract.validation_job(WORKFLOW.read_text(), "desktop-android"), "Install pinned Android SDK packages")
        script = textwrap.dedent(block.split("        run: |\n", 1)[1])
        # Only hardware admission is simulated. Retain the real installer,
        # shell error handling, package selection and revision predicates.
        for command in ("test -c /dev/kvm", "sudo chmod 0666 /dev/kvm",
                        "test -r /dev/kvm", "test -w /dev/kvm"):
            self.assertEqual(script.count(command), 1)
            script = script.replace(command, "true # synthetic KVM admission", 1)
        with TemporaryDirectory(prefix="parlor-sdk-control-") as temporary:
            scratch = Path(temporary)
            sdk = scratch / "sdk"
            manager = sdk / "cmdline-tools/latest/bin/sdkmanager"
            manager.parent.mkdir(parents=True)
            manager.write_text("#!/usr/bin/env python3\n" + textwrap.dedent('''\
                import json, os, pathlib, sys
                pathlib.Path(os.environ['CALLS']).write_text(json.dumps(sys.argv[1:]))
                if int(os.environ['INSTALLER_EXIT']):
                    sys.exit(int(os.environ['INSTALLER_EXIT']))
                for package in sys.argv[1:]:
                    if package.startswith('system-images;'):
                        if package.startswith('system-images;android-34;') and os.environ['MISSING'] == '1':
                            continue
                        directory = pathlib.Path(os.environ['ANDROID_HOME']) / package.replace(';', '/')
                        directory.mkdir(parents=True, exist_ok=True)
                        revision = os.environ['REVISION'] if 'android-34;' in package else '9'
                        (directory / 'source.properties').write_text('Pkg.Revision=' + revision + '\\n')
                '''))
            manager.chmod(0o700)
            calls = scratch / "calls.json"
            env = dict(os.environ, ANDROID_HOME=str(sdk), CALLS=str(calls),
                       REVISION=revision, MISSING="1" if missing else "0",
                       INSTALLER_EXIT=str(installer_exit))
            result = subprocess.run(["bash", "-c", script], cwd=ROOT, env=env,
                                    capture_output=True, text=True, timeout=15)
            return result, json.loads(calls.read_text())

    def test_installs_both_required_images_before_build(self) -> None:
        result, calls = self.setup_run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([arg for arg in calls if arg.startswith("system-images;")], [
            "system-images;android-35;google_apis;x86_64",
            "system-images;android-34;google_apis;x86_64",
        ])
        self.assertTrue(any(arg.startswith("--sdk_root=") for arg in calls))

    def test_installer_error_is_not_retried_or_ignored(self) -> None:
        result, _ = self.setup_run(installer_exit=23)
        self.assertEqual(result.returncode, 23)

    def test_unreviewed_navigation_revision_fails_before_build(self) -> None:
        result, _ = self.setup_run(revision="15")
        self.assertNotEqual(result.returncode, 0)

    def test_missing_navigation_properties_fails_before_build(self) -> None:
        result, _ = self.setup_run(missing=True)
        self.assertNotEqual(result.returncode, 0)

    def test_duplicate_navigation_revision_fails_before_build(self) -> None:
        result, _ = self.setup_run(revision="14\nPkg.Revision=14")
        self.assertNotEqual(result.returncode, 0)

    def test_workflow_keeps_bounded_both_image_setup_and_revision_guards(self) -> None:
        workflow = WORKFLOW.read_text()
        block = workflow_contract.validation_step(workflow_contract.validation_job(workflow, "desktop-android"), "Install pinned Android SDK packages")
        workflow_contract.verify_android_sdk_provisioning(workflow)
        for original, replacement in (
            ('"$navigation_device_image"', '"platform-tools"'),
            ('--sdk_root="${ANDROID_HOME}"', '--sdk_root="/tmp/other-sdk"'),
            ("--kill-after=10s 600s", "--kill-after=10s 3600s"),
            ("timeout-minutes: 11", "timeout-minutes: 90"),
            (".toolchains.android_navigation_device.system_image_revision", ".toolchains.android_managed_device.system_image_revision"),
            ('test "$image_revision" =', 'echo "$image_revision" ='),
        ):
            with self.subTest(original=original):
                self.assertIn(original, block)
                changed = workflow.replace(block, block.replace(original, replacement, 1), 1)
                with self.assertRaisesRegex(RuntimeError, "SDK provisioning"):
                    workflow_contract.verify_android_sdk_provisioning(changed)

    def test_disabled_candidate_keeps_same_setup_without_enabling_publication(self) -> None:
        workflow = (ROOT / ".github/workflows/testing-candidate.yml").read_text()
        workflow_contract.verify_android_sdk_provisioning(workflow, "preflight")
        block = workflow_contract.validation_step(workflow_contract.validation_job(workflow, "preflight"),
                                                  "Install pinned Android SDK packages")
        production = workflow_contract.validation_step(workflow_contract.validation_job(WORKFLOW.read_text(), "desktop-android"),
                                                       "Install pinned Android SDK packages")
        self.assertEqual(block, production.split("\n", 1)[1])
        self.assertIn("    if: ${{ always() && false }}", workflow_contract.validation_job(workflow, "preflight"))
        changed = workflow.replace(block, block.replace('"$navigation_device_image"', '"platform-tools"', 1), 1)
        with self.assertRaisesRegex(RuntimeError, "SDK provisioning"):
            workflow_contract.verify_android_sdk_provisioning(changed, "preflight")

    def test_navigation_policy_rejects_unreviewed_api_revision_and_abi(self) -> None:
        policy = json.loads((ROOT / "config/release-policy.json").read_text())
        workflow_contract.verify_policy()
        for field, bad in (("api_level", 35), ("system_image_revision", "15"),
                           ("abi", "arm64-v8a"), ("system_image_package", "unreviewed")):
            candidate = json.loads(json.dumps(policy))
            candidate["toolchains"]["android_navigation_device"][field] = bad
            with self.subTest(field=field), patch.object(Path, "read_text", return_value=json.dumps(candidate)):
                with self.assertRaisesRegex(RuntimeError, "navigation managed device"):
                    workflow_contract.verify_policy()

    def test_gradle_explicitly_preserves_both_x64_device_lanes(self) -> None:
        source = (ROOT / "composeApp/build.gradle.kts").read_text()
        for name, api in (("pixel2Api35", 35), ("pixel2Api34", 34)):
            block = source.split(f'create("{name}") {{', 1)[1].split("}", 1)[0]
            self.assertIn(f"apiLevel = {api}", block)
            self.assertIn('testedAbi = "x86_64"', block)
            self.assertIn('systemImageSource = "google"', block)
            self.assertIn("require64Bit = true", block)
