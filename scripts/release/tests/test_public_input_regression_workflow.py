"""The focused native edit run cannot silently gain retries or wider authority."""
import unittest
from unittest.mock import patch
import copy
import textwrap

from scripts.release import workflow_contract as contract


class PublicInputRegressionWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (contract.WORKFLOWS / "ios-public-input-regressions.yml").read_text()

    def test_reviewed_workflow_passes_exact_and_common_contracts(self):
        contract.verify_public_input_regressions(self.workflow)
        contract.verify_common("ios-public-input-regressions.yml", self.workflow)

    def test_changed_runner_permissions_selection_and_cleanup_are_rejected(self):
        for old, new in (
            ("runs-on: macos-15", "runs-on: self-hosted"),
            ("contents: read", "contents: write"),
            ("cancel-in-progress: false", "cancel-in-progress: true"),
            ("testPostDoneNameAndRoomCodeEditsInArabic", "testColdLaunchRendersComposeHomeWithoutUnexpectedAlert"),
            ("-parallel-testing-enabled NO", "-parallel-testing-enabled YES"),
            ("finish apple-ui", "finish apple-aggregate"),
            ("if-no-files-found: error", "if-no-files-found: warn"),
            ("timeout-minutes: 45", "timeout-minutes: 180"),
        ):
            with self.subTest(change=old):
                self.assertIn(old, self.workflow)
                with self.assertRaises(RuntimeError):
                    contract.verify_public_input_regressions(self.workflow.replace(old, new, 1))

    def test_even_an_added_retry_is_not_an_accepted_workflow(self):
        with self.assertRaises(RuntimeError):
            contract.verify_public_input_regressions(self.workflow + "\n# retry changed code\n")

    def test_exact_simulator_template_never_falls_back_or_omits_inventory(self):
        # Define only the inline backend, not resource creation or UIKit work.
        source = self.workflow.split("          from scripts.ci import apple_verification_hygiene as apple\n", 1)[1]
        source = "          from scripts.ci import apple_verification_hygiene as apple\n" + source
        source = source.split("          root, prefix, task = common.context()", 1)[0]
        namespace = {}
        exec(compile(textwrap.dedent(source), "<reviewed simulator selection>", "exec"), namespace)
        runtime = "com.apple.CoreSimulator.SimRuntime.iOS-26-2"
        kind = "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
        def device(index, name, device_type):
            return dict(udid="%08X-0000-0000-0000-000000000000" % index,
                        name=name, deviceTypeIdentifier=device_type, isAvailable=True)
        inventory = {"devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-18-5": [device(1, "iPhone 16 Pro", kind)],
            runtime: [device(2, "iPhone 16 Pro", "com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro"),
                      device(3, "Parlor-ci-renamed", kind), device(4, "iPhone 17 Pro", kind)],
        }}
        backend = namespace["ExactBackend"]()
        simulator = namespace["simulator"]
        with patch.object(simulator.SimctlBackend, "inventory", return_value=copy.deepcopy(inventory)):
            ordered = backend.inventory()
        self.assertEqual(ordered["devices"][runtime][0]["udid"], inventory["devices"][runtime][2]["udid"])
        self.assertEqual({p["udid"] for p in simulator.devices(ordered)},
                         {p["udid"] for p in simulator.devices(inventory)})
        for missing in ("renamed", "unavailable", "wrong type"):
            rejected = copy.deepcopy(inventory)
            target = rejected["devices"][runtime][2]
            if missing == "renamed":
                target["name"] = "Parlor-ci-not-a-template"
            elif missing == "unavailable":
                target["isAvailable"] = False
            else:
                target["deviceTypeIdentifier"] = "com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro"
            with self.subTest(missing=missing), patch.object(simulator.SimctlBackend, "inventory", return_value=rejected):
                with self.assertRaises(RuntimeError):
                    backend.inventory()


if __name__ == "__main__":
    unittest.main()
