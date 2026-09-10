from __future__ import annotations

import ast
import json
import re
import textwrap
from types import SimpleNamespace
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import Mock, patch

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import workflow_contract  # noqa: E402


class WorkflowContractTest(unittest.TestCase):
    def test_windows_only_selection_cannot_skip_full_or_run_other_jobs(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        workflow_contract.verify_verification_scopes(workflow)
        for original, replacement in (
            ("default: full", "default: windows-only"),
            ("[full, native-preflight, native-evidence, native-process-probe, windows-only]",
             "[full, native-preflight, native-evidence, native-process-probe]"),
            ("if: " + workflow_contract.WINDOWS_VERIFICATION_SCOPE, "if: " + workflow_contract.FULL_VERIFICATION_SCOPE),
            ("if: " + workflow_contract.IOS_VERIFICATION_SCOPE, "if: true"),
            ("    if: " + workflow_contract.FULL_VERIFICATION_SCOPE + "\n",
             "    if: " + workflow_contract.WINDOWS_VERIFICATION_SCOPE + "\n"),
        ):
            changed = workflow.replace(original, replacement, 1)
            self.assertNotEqual(changed, workflow)
            with self.subTest(original=original), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(changed)

    def test_windows_checkout_long_paths_and_exact_source_are_required_before_checkout(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        windows = workflow.split("\n  desktop-windows-x64:\n", 1)[1].split("\n  ios:\n", 1)[0]
        workflow_contract.verify_windows_checkout(windows)
        for original, replacement in (
            ('GIT_CONFIG_COUNT: "1"', 'GIT_CONFIG_COUNT: "0"'),
            ('GIT_CONFIG_KEY_0: core.longpaths', 'GIT_CONFIG_KEY_0: core.autocrlf'),
            ('GIT_CONFIG_VALUE_0: "true"', 'GIT_CONFIG_VALUE_0: "false"'),
            ('git config --bool --get core.longpaths', 'git config --global core.longpaths true'),
            ('$LASTEXITCODE -ne 0 -or $longPaths', '$false -or $longPaths'),
            ('$env:PARLOR_WINDOWS_SCOPE -cne "windows-only"', '$env:PARLOR_WINDOWS_SCOPE -ne "windows-only"'),
            ('${{ inputs.frozen_source_sha }}', '${{ github.sha }}'),
            ("'^[0-9a-f]{40}$'", "'.*'"),
            ('$source -cne $env:GITHUB_WORKFLOW_SHA', '$false'),
            ('$source -cne $env:GITHUB_SHA', '$false'),
            ('$env:PARLOR_WINDOWS_NATIVE_SELECTION -cnotin', '$env:PARLOR_WINDOWS_NATIVE_SELECTION -cin'),
            ('$LASTEXITCODE -ne 0 -or $head -cne $env:GITHUB_SHA', '$false'),
            ('$LASTEXITCODE -ne 0 -or $status', '$false'),
            ('fetch-depth: 1', 'fetch-depth: 1\n          sparse-checkout: scripts'),
        ):
            changed = windows.replace(original, replacement, 1)
            self.assertNotEqual(changed, windows)
            with self.subTest(original=original), self.assertRaisesRegex(RuntimeError, "Windows checkout"):
                workflow_contract.verify_windows_checkout(changed)
        gate = "\n      - name: Validate Windows checkout prerequisites\n" + workflow_contract.validation_step(
            windows, "Validate Windows checkout prerequisites")
        checkout = "\n      - name: Check out source\n" + workflow_contract.validation_step(windows, "Check out source")
        moved = windows.replace(gate, "", 1).replace(checkout, checkout + gate, 1)
        self.assertNotEqual(moved, windows)
        with self.assertRaisesRegex(RuntimeError, "before checkout"):
            workflow_contract.verify_windows_checkout(moved)

    def test_native_selection_is_closed_paired_default_and_cannot_be_duplicated(self) -> None:
        workflow = (workflow_contract.ROOT / '.github/workflows/production-verification.yml').read_text(encoding='utf-8')
        block = '      native_selection:\n' + workflow.split('      native_selection:\n', 1)[1].split('      frozen_source_sha:\n', 1)[0]
        workflow_contract.verify_verification_scopes(workflow)
        mutations = [block.replace('type: choice', 'type: string', 1),
                     block.replace('default: paired', 'default: l08-only', 1),
                     block.replace('[paired, l08-only]', '[paired, l08-only, l08]', 1),
                     block.replace('[paired, l08-only]', '[l08-only, paired]', 1),
                     block + block, '']
        for replacement in mutations:
            changed = workflow.replace(block, replacement, 1)
            self.assertNotEqual(changed, workflow)
            with self.subTest(replacement=replacement), self.assertRaisesRegex(RuntimeError, 'verification scope'):
                workflow_contract.verify_verification_scopes(changed)

    def test_native_selection_validation_execution_and_cleanup_use_identical_input(self) -> None:
        workflow = (workflow_contract.ROOT / '.github/workflows/production-verification.yml').read_text(encoding='utf-8')
        original = '          PARLOR_NATIVE_SELECTION: ${{ inputs.native_selection }}\n'
        for name in ('Validate verification scope', 'Run focused native continuation',
                     'Verify focused native cleanup and uploaded custody'):
            block = workflow_contract.validation_step(workflow, name)
            self.assertEqual(block.count(original), 1)
            for replacement in ('', original.replace('inputs.native_selection', 'inputs.verification_scope'),
                                original.replace('${{ inputs.native_selection }}', 'paired'), original + original):
                changed = workflow.replace(block, block.replace(original, replacement, 1), 1)
                self.assertNotEqual(changed, workflow)
                with self.subTest(name=name, replacement=replacement), self.assertRaisesRegex(RuntimeError, 'verification scope'):
                    workflow_contract.verify_verification_scopes(changed)
        changed = workflow.replace('env:\n', 'env:\n' + original, 1)
        with self.assertRaisesRegex(RuntimeError, 'verification scope'):
            workflow_contract.verify_verification_scopes(changed)

    def test_focused_native_modes_do_not_weaken_full_verification(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        workflow_contract.verify_verification_scopes(workflow)
        for original, replacement in (
            ("default: full", "default: native-preflight"),
            ("options: [full, native-preflight, native-evidence, native-process-probe, windows-only]", "options: [full, skip]"),
            ("  desktop-linux-arm64:\n", "  unreviewed-sixth-job:\n    runs-on: ubuntu-latest\n  desktop-linux-arm64:\n"),
            ("    if: " + workflow_contract.FULL_VERIFICATION_SCOPE, "    if: false"),
            ("scripts/ci/native_continuation.py validate-scope", "true"),
        ):
            with self.subTest(original=original), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(workflow.replace(original, replacement, 1))

    def test_non_app_probe_requires_explicit_review_no_token_and_uploaded_custody(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        for original, replacement in (
            ("run: /usr/bin/python3 -B scripts/ci/native_process_probe.py run", "run: /usr/bin/python3 -B scripts/ci/native_continuation.py run"),
            ("PARLOR_APPROVED_PROBE_CONTROL_SHA256: ${{ inputs.approved_probe_control_sha256 }}", "UNREVIEWED: yes"),
            ("PARLOR_PROBE_UPLOAD_OUTCOME: ${{ steps.process_probe_artifact.outcome }}", "PARLOR_PROBE_UPLOAD_OUTCOME: success"),
            ("PARLOR_PROBE_ARTIFACT_ID: ${{ steps.process_probe_artifact.outputs.artifact-id }}", "PARLOR_PROBE_ARTIFACT_ID: 1"),
            ("PARLOR_PROBE_ARTIFACT_DIGEST: ${{ steps.process_probe_artifact.outputs.artifact-digest }}", "PARLOR_PROBE_ARTIFACT_DIGEST: cached"),
            ("if: " + workflow_contract.PROCESS_PROBE_SCOPE, "if: always()"),
        ):
            with self.subTest(original=original), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(workflow.replace(original, replacement, 1))
        step = workflow_contract.validation_step(workflow, "Observe hosted native processes without an app build")
        changed = workflow.replace(step, step.replace("        env:\n", "        env:\n          GH_TOKEN: ${{ github.token }}\n"), 1)
        with self.assertRaisesRegex(RuntimeError, "verification scope"):
            workflow_contract.verify_verification_scopes(changed)

    def test_probe_cleanup_binding_timeout_and_artifact_paths_are_exact(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        for name, original, replacement in (
            ("Verify process-probe cleanup and uploaded custody", "PARLOR_FROZEN_SOURCE_SHA", "UNBOUND_SOURCE"),
            ("Verify process-probe cleanup and uploaded custody", "PARLOR_APPROVED_PROBE_CONTROL_SHA256", "UNREVIEWED"),
            ("Verify process-probe cleanup and uploaded custody", "PARLOR_DISPATCH_SCOPE", "UNSCOPED"),
            ("Verify process-probe cleanup and uploaded custody", "        env:\n", "        env:\n          GH_TOKEN: ${{ github.token }}\n"),
            ("Upload bounded process-probe evidence", "id: process_probe_artifact", "id: unrelated"),
            ("Upload bounded process-probe evidence", "/bundle/", "/resources/"),
            ("Upload bounded process-probe evidence", "name: native-process-probe-", "name: unrelated-"),
            ("Upload bounded process-probe evidence", "if-no-files-found: error", "if-no-files-found: warn"),
            ("Upload process-probe cleanup receipt", "-cleanup.json", "-unknown.json"),
            ("Upload process-probe cleanup receipt", "name: native-process-probe-cleanup-", "name: unrelated-"),
        ):
            block = workflow_contract.validation_step(workflow, name)
            self.assertIn(original, block)
            changed = workflow.replace(block, block.replace(original, replacement, 1), 1)
            with self.subTest(name=name, original=original), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(changed)

    def test_native_evidence_only_job_budget_and_all_other_scope_ceilings_are_exact(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        expression = ("${{ inputs.verification_scope == 'native-process-probe' && 10 || "
                      "inputs.verification_scope == 'native-evidence' && 240 || 120 }}")
        ios = workflow.split("\n  ios:\n", 1)[1]
        self.assertEqual([expression], re.findall(r"(?m)^    timeout-minutes: (.*)$", ios))
        workflow_contract.verify_verification_scopes(workflow)
        for replacement in (
            expression.replace("&& 10", "&& 30"),
            expression.replace("&& 240", "&& 120"),
            expression.replace("&& 240", "&& 241"),
            expression.replace("|| 120", "|| 240"),
            expression.replace("'native-evidence'", "'native-preflight'"),
            expression.replace("'native-evidence'", "'full'"),
            "${{ inputs.verification_scope == 'native-process-probe' && 10 || 120 }}",
            "${{ inputs.native_timeout_minutes || 240 }}",
        ):
            changed = workflow.replace(expression, replacement, 1)
            self.assertNotEqual(workflow, changed)  # No vacuous mutation witness after expression drift.
            with self.subTest(replacement=replacement), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(changed)

    def test_actual_first_step_clock_producer_emits_exact_shared_kernel_and_run_source_token(self) -> None:
        workflow = (workflow_contract.ROOT / '.github/workflows/production-verification.yml').read_text(encoding='utf-8')
        block = workflow_contract.validation_step(workflow, 'Start focused native job clock')
        body = block.split("<<'PY' >> \"$GITHUB_ENV\"\n", 1)[1].split('\n          PY', 1)[0]
        tree = ast.parse(textwrap.dedent(body))
        self.assertEqual(['json', 'os', 'time'], [node.names[0].name for node in tree.body if isinstance(node, ast.Import)])
        # Compile only the actual Python heredoc with inert clock/environment/output;
        # no workflow shell, real clock, runner, build, or GITHUB_ENV write executes.
        tree.body = [node for node in tree.body if not isinstance(node, ast.Import)]
        raw_clock = object()
        for observed in (123456789012345, True, 0, -1, None, 1.0, '1000'):
            output = Mock()
            clock = SimpleNamespace(CLOCK_MONOTONIC_RAW=raw_clock, clock_gettime_ns=Mock(return_value=observed))
            namespace = dict(json=json, time=clock, print=output, os=SimpleNamespace(environ=dict(
                GITHUB_RUN_ID='201', GITHUB_RUN_ATTEMPT='3', GITHUB_JOB='ios', GITHUB_SHA='a' * 40)))
            with self.subTest(observed=observed):
                if type(observed) is int and observed > 0:
                    exec(compile(tree, '<isolated-workflow-clock-producer>', 'exec'), namespace)
                    output.assert_called_once()
                    text = output.call_args.args[0]
                    self.assertTrue(text.startswith('PARLOR_NATIVE_JOB_CLOCK='))
                    self.assertEqual(dict(clock='CLOCK_MONOTONIC_RAW', start_ns=observed, run_id=201,
                                          run_attempt=3, job='ios', head_sha='a' * 40),
                                     json.loads(text.split('=', 1)[1]))
                else:
                    with self.assertRaisesRegex(RuntimeError, 'invalid-native-job-kernel-clock'):
                        exec(compile(tree, '<isolated-workflow-clock-producer>', 'exec'), namespace)
                    output.assert_not_called()
                clock.clock_gettime_ns.assert_called_once_with(raw_clock)

    def test_native_clock_step_rejects_unshared_clocks_extra_code_fields_and_context_weakening(self) -> None:
        workflow = (workflow_contract.ROOT / '.github/workflows/production-verification.yml').read_text(encoding='utf-8')
        name = 'Start focused native job clock'
        block = workflow_contract.validation_step(workflow, name)
        workflow_contract.verify_verification_scopes(workflow)
        mutations = [
            block.replace('time.clock_gettime_ns(time.CLOCK_MONOTONIC_RAW)', 'time.monotonic_ns()', 1),
            block.replace('time.CLOCK_MONOTONIC_RAW', 'time.CLOCK_MONOTONIC', 1),
            block.replace('start_ns=value', 'start_ns=1', 1),
            block.replace('run_id=int(os.environ["GITHUB_RUN_ID"])', 'run_id=1', 1),
            block.replace('run_attempt=int(os.environ["GITHUB_RUN_ATTEMPT"])', 'run_attempt=1', 1),
            block.replace('job=os.environ["GITHUB_JOB"]', 'job="ios"', 1),
            block.replace('head_sha=os.environ["GITHUB_SHA"]', 'head_sha="unbound"', 1),
            block.replace("inputs.verification_scope == 'native-evidence'", "inputs.verification_scope != 'full'", 1),
            block.replace('        shell: bash\n', '        shell: bash\n        continue-on-error: true\n', 1),
            block.replace('        shell: bash\n', '        shell: bash\n        env:\n          UNREVIEWED: yes\n', 1),
            # Preserve the entire old allowed heredoc then append executable code:
            # an inclusion-only checker would incorrectly accept these variants.
            block + '          printf unreviewed >> "$GITHUB_ENV"\n',
            block + "          /usr/bin/python3 -B -c 'print(1)' >> \"$GITHUB_ENV\"\n",
            block.replace('          PY\n', '          value = 0\n          PY\n', 1),
        ]
        for index, replacement in enumerate(mutations):
            changed = workflow.replace(block, replacement, 1)
            self.assertNotEqual(workflow, changed)
            with self.subTest(mutation=index), self.assertRaisesRegex(RuntimeError, 'clock'):
                workflow_contract.verify_verification_scopes(changed)

    def test_native_clock_cannot_be_restarted_later_or_moved_after_checkout(self) -> None:
        workflow = (workflow_contract.ROOT / '.github/workflows/production-verification.yml').read_text(encoding='utf-8')
        clock = '\n      - name: Start focused native job clock\n' + workflow_contract.validation_step(
            workflow, 'Start focused native job clock')
        checkout = '\n      - name: Check out source\n' + workflow_contract.validation_step(
            workflow.split('\n  ios:\n', 1)[1], 'Check out source')
        moved = workflow.replace(clock, '\nSYNTHETIC_CLOCK_HOLE\n', 1).replace(checkout, checkout + clock, 1).replace(
            '\nSYNTHETIC_CLOCK_HOLE\n', '', 1)
        duplicated = workflow.replace(clock, clock + clock, 1)
        for changed in (moved, duplicated):
            self.assertNotEqual(workflow, changed)
            with self.subTest(duplicate=changed == duplicated), self.assertRaisesRegex(RuntimeError, 'clock'):
                workflow_contract.verify_verification_scopes(changed)

    def test_probe_cannot_precede_scope_validation_or_upload(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        for first, second in (
            ("Validate verification scope", "Observe hosted native processes without an app build"),
            ("Upload bounded process-probe evidence", "Verify process-probe cleanup and uploaded custody"),
        ):
            def complete_step(name):
                return "\n      - name: " + name + "\n" + workflow_contract.validation_step(workflow, name)
            a, b = complete_step(first), complete_step(second)
            changed = workflow.replace(a, "SYNTHETIC_STEP_HOLE", 1).replace(b, a, 1).replace("SYNTHETIC_STEP_HOLE", b, 1)
            with self.subTest(first=first), self.assertRaisesRegex(RuntimeError, "verification scope"):
                workflow_contract.verify_verification_scopes(changed)

    def test_scoped_full_finalizers_reject_every_nonexact_predicate(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(encoding="utf-8")
        original = "if: " + workflow_contract.FULL_VERIFICATION_FINALIZER
        for name in ("Stop Gradle after apple-aggregate and retire owned Apple resources",
                     "Stop Gradle after apple-ui and retire owned Apple resources",
                     "Stop Gradle after apple-wrapper and retire owned Apple resources"):
            block = workflow_contract.validation_step(workflow, name)
            for replacement in ("if: always()", "if: success()", "if: always() && false",
                                "if: always() && inputs.verification_scope == 'full'"):
                broken = workflow.replace(block, block.replace(original, replacement), 1)
                with self.subTest(name=name, replacement=replacement), self.assertRaises(RuntimeError):
                    workflow_contract.verify_validation(broken)

    def test_repository_workflows_satisfy_release_contract(self) -> None:
        self.assertEqual(workflow_contract.main(), 0)

    def test_xcode_identity_guard_matches_the_exact_build_setting(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        self.assertEqual(workflow.count('key == "PRODUCT_BUNDLE_IDENTIFIER"'), 2)
        broken = workflow.replace(
            '{key=$1; gsub(/^[[:space:]]+|[[:space:]]+$/, "", key); if (key == "PRODUCT_BUNDLE_IDENTIFIER") {print $2; exit}}',
            '$1 ~ /PRODUCT_BUNDLE_IDENTIFIER$/ {print $2; exit}',
        )
        with self.assertRaisesRegex(RuntimeError, "Mac Catalyst"):
            workflow_contract.verify_validation(broken)

    def test_ios_app_launch_ui_test_cannot_be_replaced_with_a_build(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        broken = workflow.replace(
            "test | tee build/ci-evidence/xcode-ui-test.log",
            "build | tee build/ci-evidence/xcode-ui-test.log",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "app-launch test"):
            workflow_contract.verify_validation(broken)

    def test_ios_app_launch_requires_owned_simulator_and_nonparallel_execution(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        mutations = (
            ("create-simulator apple-ui", "list devices available --json"),
            ("-parallel-testing-enabled NO", "-parallel-testing-enabled YES"),
            ("-maximum-concurrent-test-simulator-destinations 1", "-maximum-concurrent-test-simulator-destinations 2"),
            ("test ! -e build/ci-evidence/ios-ui-tests.xcresult", "true"),
            ("-derivedDataPath build/xcode-derived-data", "-derivedDataPath /tmp/unowned"),
        )
        for original, replacement in mutations:
            with self.subTest(original=original):
                self.assertIn(original, workflow)
                with self.assertRaisesRegex(RuntimeError, "owned iOS app-launch"):
                    workflow_contract.verify_validation(workflow.replace(original, replacement, 1))

    def test_ios_app_launch_requires_its_own_preparation_and_finalization(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        for original, replacement in (
            ("prepare apple-ui", "prepare apple-wrapper"),
            ("finish apple-ui", "finish apple-wrapper"),
            ("id: apple_ui_prepare", "id: another_prepare"),
            ("steps.apple_ui_prepare.outcome", "steps.apple_wrapper_prepare.outcome"),
            ("steps.apple_ui_run.outcome", "steps.apple_wrapper_run.outcome"),
        ):
            with self.subTest(original=original):
                with self.assertRaisesRegex(RuntimeError, "owned iOS app-launch"):
                    workflow_contract.verify_validation(workflow.replace(original, replacement, 1))
        finish = workflow_contract.validation_step(
            workflow, "Stop Gradle after apple-ui and retire owned Apple resources"
        )
        for condition in ("if: success()", "if: failure()", "# if removed"):
            with self.subTest(condition=condition):
                broken = workflow.replace(finish, finish.replace("if: always()", condition), 1)
                with self.assertRaisesRegex(RuntimeError, "finalizer must always run"):
                    workflow_contract.verify_validation(broken)

    def test_ios_app_launch_cannot_delay_finalization_or_claim_ownership_after_launch(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        finish_marker = "      - name: Stop Gradle after apple-ui and retire owned Apple resources\n"
        delayed = workflow.replace(finish_marker, "      - name: Another build\n        run: ./gradlew build\n\n" + finish_marker, 1)
        with self.assertRaisesRegex(RuntimeError, "immediately after app-launch"):
            workflow_contract.verify_validation(delayed)
        prepare_name = "Claim apple-ui native resource ownership"
        prepare = f"      - name: {prepare_name}\n" + workflow_contract.validation_step(workflow, prepare_name)
        misplaced = workflow.replace(prepare, "", 1).replace(finish_marker, prepare + "\n" + finish_marker, 1)
        with self.assertRaisesRegex(RuntimeError, "ownership before launch"):
            workflow_contract.verify_validation(misplaced)

    def test_android_and_ios_release_packages_must_verify_notice_bytes(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        for artifact in ("$aab", "$app"):
            command = f'scripts/verification/third_party_notices.py --package "{artifact}" --json'
            with self.subTest(artifact=artifact):
                self.assertEqual(1, workflow.count(command))
                with self.assertRaisesRegex(RuntimeError, "packaged-notice verification"):
                    workflow_contract.verify_validation(workflow.replace(command, "removed-package-check", 1))

    def test_unsigned_android_inventory_is_required_and_tool_cleanup_is_scoped(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        for command in ('scripts/verification/android_release_artifacts.py --package "$aab"',
                        'trap \'rm -rf "$tools"\' EXIT'):
            with self.subTest(command=command), self.assertRaisesRegex(RuntimeError, "complete unsigned Android"):
                workflow_contract.verify_validation(workflow.replace(command, "removed-package-check", 1))

    def test_complete_ios_inventory_requires_successful_build_and_cleanup(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        for command in ('/usr/bin/python3 -B scripts/verification/ios_release_artifacts.py',
                        "&& steps.apple_wrapper_finish.outcome == 'success'",
                        '--source "$GITHUB_WORKSPACE" --json >build/ci-evidence/ios-release-artifact-inventory.json'):
            with self.subTest(command=command), self.assertRaisesRegex(RuntimeError, "complete iOS artifact"):
                workflow_contract.verify_validation(workflow.replace(command, "removed-package-check", 1))

    def test_mobile_release_kit_android_signing_fallbacks_remain_bounded(self) -> None:
        gradle = (workflow_contract.ROOT / "composeApp/build.gradle.kts").read_text(
            encoding="utf-8"
        )
        inputs = (
            ("PARLOR_ANDROID_KEYSTORE_PATH", "MOBILE_RELEASE_ANDROID_KEYSTORE_PATH", "storeFile"),
            (
                "PARLOR_ANDROID_KEYSTORE_PASSWORD",
                "MOBILE_RELEASE_ANDROID_KEYSTORE_PASSWORD",
                "storePassword",
            ),
            ("PARLOR_ANDROID_KEY_ALIAS", "MOBILE_RELEASE_ANDROID_KEY_ALIAS", "keyAlias"),
            (
                "PARLOR_ANDROID_KEY_PASSWORD",
                "MOBILE_RELEASE_ANDROID_KEY_PASSWORD",
                "keyPassword",
            ),
        )
        for legacy_name, shared_name, property_name in inputs:
            legacy = f'providers.environmentVariable("{legacy_name}")'
            shared = f'providers.environmentVariable("{shared_name}")'
            property_input = f'providers.gradleProperty("parlor.android.signing.{property_name}")'
            with self.subTest(input=shared_name):
                self.assertIn(legacy, gradle)
                self.assertIn(shared, gradle)
                self.assertIn(property_input, gradle)
                self.assertLess(gradle.index(legacy), gradle.index(shared))
                self.assertLess(gradle.index(shared), gradle.index(property_input))
        self.assertIn('providers.environmentVariable("MOBILE_RELEASE_REQUIRE_SIGNING")', gradle)
        self.assertIn("check(!releaseSigningRequired || releaseSigningConfigured)", gradle)

    def test_mobile_release_kit_ios_signing_mapping_is_release_target_only(self) -> None:
        project = (workflow_contract.ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text(
            encoding="utf-8"
        )
        configuration = (workflow_contract.ROOT / "iosApp/Configuration/Config.xcconfig").read_text(
            encoding="utf-8"
        )
        for default in (
            "MOBILE_RELEASE_IOS_CODE_SIGN_STYLE = Automatic",
            "MOBILE_RELEASE_IOS_CODE_SIGN_IDENTITY =",
            "MOBILE_RELEASE_IOS_PROVISIONING_PROFILE_SPECIFIER =",
            "MOBILE_RELEASE_IOS_DEVELOPMENT_TEAM = $(TEAM_ID)",
        ):
            self.assertIn(default, configuration)

        pattern = re.compile(
            r"/\* (?P<name>Debug|Release) \*/ = \{\n"
            r"\s+isa = XCBuildConfiguration;\n"
            r".*?\s+buildSettings = \{\n"
            r"(?P<settings>.*?)\n\s+\};\n"
            r"\s+name = (?P=name);\n"
            r"\s+\};",
            re.DOTALL,
        )
        app_configuration_matches = [
            (match.group("name"), match.group("settings"))
            for match in pattern.finditer(project)
            if re.search(
                r'^\s*PRODUCT_NAME = "\$\(APP_NAME\)";\s*$',
                match.group("settings"),
                re.MULTILINE,
            )
        ]
        self.assertEqual(len(app_configuration_matches), 2)
        app_configurations = dict(app_configuration_matches)
        self.assertEqual(set(app_configurations), {"Debug", "Release"})
        debug = app_configurations["Debug"]
        release = app_configurations["Release"]
        self.assertIn("CODE_SIGN_STYLE = Automatic;", debug)
        self.assertIn('DEVELOPMENT_TEAM = "$(TEAM_ID)";', debug)
        self.assertNotIn("MOBILE_RELEASE_IOS_", debug)
        for mapping in (
            'CODE_SIGN_IDENTITY = "$(MOBILE_RELEASE_IOS_CODE_SIGN_IDENTITY)";',
            'CODE_SIGN_STYLE = "$(MOBILE_RELEASE_IOS_CODE_SIGN_STYLE)";',
            'DEVELOPMENT_TEAM = "$(MOBILE_RELEASE_IOS_DEVELOPMENT_TEAM)";',
            'PROVISIONING_PROFILE_SPECIFIER = "$(MOBILE_RELEASE_IOS_PROVISIONING_PROFILE_SPECIFIER)";',
        ):
            self.assertIn(mapping, release)
            self.assertEqual(project.count(mapping), 1)

    def test_candidate_bundletool_download_must_be_bounded(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "bundletool download"):
            workflow_contract.verify_candidate(workflow.replace("--max-filesize 209715200", "", 1))

    def test_candidate_dependency_report_must_use_release_runtime(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "release runtime"):
            workflow_contract.verify_candidate(
                workflow.replace("--configuration releaseRuntimeClasspath", "", 1)
            )

    def test_candidate_dependency_and_validation_evidence_must_be_retained(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "retain Android dependency/validation"):
            workflow_contract.verify_candidate(
                workflow.replace("Retain Android dependency and deep-validation evidence", "Evidence removed", 1)
            )

    def test_validation_requires_release_managed_device_runtime_gate(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-verification.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "managed-device smoke gate"):
            workflow_contract.verify_validation(
                workflow.replace("scripts/android/run_release_managed_device_smoke.sh", "smoke-removed", 1)
            )

    def test_candidate_preflight_requires_reviewed_managed_device_image(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "managed-device smoke gate"):
            workflow_contract.verify_candidate(
                workflow.replace("system-images;android-35;google_apis;x86_64", "unreviewed-image", 1)
            )

    def test_managed_device_runner_cannot_use_production_signing_material(self) -> None:
        script_path = (
            workflow_contract.ROOT / "scripts/android/run_release_managed_device_smoke.sh"
        )
        original = script_path.read_text(encoding="utf-8")
        with patch.object(
            Path,
            "read_text",
            return_value=original + "\nPARLOR_ANDROID_KEYSTORE_PATH=forbidden\n",
        ):
            with self.assertRaisesRegex(RuntimeError, "production signing material"):
                workflow_contract.verify_android_runtime_script()

    def test_candidate_build_number_claim_cannot_be_removed(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "build-once recovery"):
            workflow_contract.verify_candidate(
                workflow.replace("assert-candidate-claim-exclusive", "claim-check-removed", 1)
            )

    def test_candidate_claim_transaction_cannot_be_scoped_per_sha(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        broken = workflow.replace(
            "group: parlor-store-candidate-claim",
            "group: parlor-store-candidate-${{ inputs.candidate_sha }}",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "claim check/create transaction"):
            workflow_contract.verify_candidate(broken)

    def test_candidate_claim_requires_a_secretless_protected_environment(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/testing-candidate.yml").read_text(
            encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "testing-candidate environment"):
            workflow_contract.verify_candidate(
                workflow.replace("    environment: testing-candidate\n", "", 1)
            )
        poisoned = workflow.replace(
            "    environment: testing-candidate\n",
            "    environment: testing-candidate\n    env:\n      BAD: ${{ secrets.STORE_KEY }}\n",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "must not receive or reference Store secrets"):
            workflow_contract.verify_candidate(poisoned)

    def test_release_policy_includes_the_candidate_control_environment(self) -> None:
        policy = json.loads(
            (workflow_contract.ROOT / "config/release-policy.json").read_text(encoding="utf-8")
        )
        self.assertEqual(
            policy["github"]["environments"]["candidate_control"],
            "testing-candidate",
        )

    def test_external_partial_rerun_evidence_cannot_be_removed(self) -> None:
        workflow = (
            workflow_contract.ROOT / ".github/workflows/testing-external-promotion.yml"
        ).read_text(encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "partial rerun"):
            workflow_contract.verify_external_receipt_attestations(
                workflow.replace("create-external-evidence", "evidence-removed", 1)
            )

    def test_mutable_action_reference_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_action_pins("bad.yml", "steps:\n  - uses: actions/checkout@v4\n")

    def test_unreviewed_workflow_file_is_rejected(self) -> None:
        with TemporaryDirectory() as directory:
            workflows = Path(directory)
            for name in workflow_contract.EXPECTED:
                (workflows / name).write_text("name: reviewed\n", encoding="utf-8")
            (workflows / "unreviewed.yml").write_text("name: bypass\n", encoding="utf-8")
            with patch.object(workflow_contract, "WORKFLOWS", workflows):
                with self.assertRaisesRegex(RuntimeError, "Unreviewed workflow"):
                    workflow_contract.load_files()

    def test_production_build_command_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_promotions(
                "bad.yml",
                "candidate_run_id candidate_run_attempt fetch-artifact verify-source xcodebuild",
            )

    def test_production_push_trigger_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_store_workflow(
                "bad.yml",
                "workflow_dispatch:\npush:\npermissions:\n contents: read\ntimeout-minutes: 1\nenvironment: production\n",
            )

    def test_missing_shared_store_lock_is_rejected(self) -> None:
        workflows = {
            name: "group: parlor-google-play-production-identity\n"
            "group: parlor-app-store-connect-production-identity"
            for name in workflow_contract.STORE_WORKFLOWS
        }
        workflows["testing-candidate.yml"] = "group: parlor-google-play-production-identity"
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_store_serialization(workflows)

    def test_store_workflow_without_identity_ownership_gate_is_rejected(self) -> None:
        workflow = (
            workflow_contract.ROOT / ".github/workflows/testing-candidate.yml"
        ).read_text(encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "identity ownership"):
            workflow_contract.verify_store_workflow(
                "testing-candidate.yml",
                workflow.replace("assert-store-identity-approved", "identity-check-removed", 1),
            )

    def test_every_store_workflow_job_is_repository_disabled(self) -> None:
        for name in sorted(workflow_contract.STORE_WORKFLOWS):
            with self.subTest(workflow=name):
                workflow = (workflow_contract.WORKFLOWS / name).read_text(encoding="utf-8")
                broken = workflow.replace(
                    workflow_contract.DISABLED_STORE_JOB_CONDITION,
                    "if: ${{ true }}",
                    1,
                )
                with self.assertRaisesRegex(RuntimeError, "must remain repository-disabled"):
                    workflow_contract.verify_store_jobs_disabled(name, broken)

    def test_new_store_workflow_job_cannot_omit_the_release_stop(self) -> None:
        workflow = (
            workflow_contract.ROOT / ".github/workflows/testing-candidate.yml"
        ).read_text(encoding="utf-8")
        broken = workflow + "\n  bypass:\n    runs-on: ubuntu-24.04\n"
        with self.assertRaisesRegex(RuntimeError, "Store job 'bypass'"):
            workflow_contract.verify_store_jobs_disabled("testing-candidate.yml", broken)

    def test_release_tool_download_failure_cannot_false_pass(self) -> None:
        script = (
            workflow_contract.ROOT / "scripts/release/validate_release_system.sh"
        ).read_text(encoding="utf-8")
        broken = script.replace("return 2", "return", 1)
        with self.assertRaisesRegex(RuntimeError, "lose the failing command status"):
            workflow_contract.verify_tool_downloader(broken)

    def test_release_system_enforces_review_inventory_freshness(self) -> None:
        script = (
            workflow_contract.ROOT / "scripts/release/validate_release_system.sh"
        ).read_text(encoding="utf-8")
        workflow_contract.verify_review_inventory_gate(script)
        broken = script.replace(
            "python3 scripts/generate_review_inventory.py --check",
            "",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "review-inventory freshness"):
            workflow_contract.verify_review_inventory_gate(broken)

    def test_review_inventory_gate_has_full_git_history(self) -> None:
        workflow = (
            workflow_contract.ROOT / ".github/workflows/production-verification.yml"
        ).read_text(encoding="utf-8")
        broken = workflow.replace("fetch-depth: 0", "fetch-depth: 1", 1)
        before_other_job, other_jobs = broken.split("\n  desktop-linux-arm64:", 1)
        broken = before_other_job + "\n  desktop-linux-arm64:" + other_jobs.replace(
            "fetch-depth: 1",
            "fetch-depth: 0",
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "full Git history"):
            workflow_contract.verify_validation(broken)

    def test_apple_signing_and_upload_scripts_require_identity_approval(self) -> None:
        for name in ("build_ios_candidate.sh", "upload_ios_candidate.sh"):
            with self.subTest(script=name):
                script = (
                    workflow_contract.ROOT / "scripts/release" / name
                ).read_text(encoding="utf-8")
                self.assertIn("assert-store-identity-approved --platform ios", script)

    def test_promotion_without_candidate_attestation_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_promotions(
                "bad.yml",
                "candidate_run_id candidate_run_attempt fetch-artifact verify-source",
            )

    def test_production_without_external_receipt_attestation_is_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_production(
                "platform: android ios both\nrefs/heads/release\nproduction-android\nproduction-ios\n"
                "external-receipt\nexternal_run_id\n",
            )

    def test_production_without_its_own_receipt_attestations_is_rejected(self) -> None:
        workflow = (workflow_contract.ROOT / ".github/workflows/production-promotion.yml").read_text(
            encoding="utf-8"
        )
        workflow = workflow.replace(
            "subject-path: build/release-promotion/production-ios-receipt.json",
            "subject-path: build/release-promotion/not-the-ios-production-receipt.json",
        )
        with self.assertRaises(RuntimeError):
            workflow_contract.verify_production(workflow)

    def test_ios_profile_refusal_cannot_delete_a_preexisting_profile(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        refusal = script.index('if [[ -e "$profile_destination" ]]')
        install = script.index('install -m 600 "$PARLOR_APPLE_PROFILE_PATH" "$profile_destination"')
        mark_owned = script.index("installed_profile=$profile_destination")
        self.assertLess(refusal, install)
        self.assertLess(install, mark_owned)

    def test_ios_signing_cleanup_is_fail_closed(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        self.assertIn("cleanup_status=0", script)
        self.assertIn('exit "$cleanup_status"', script)
        self.assertIn("trap 'cleanup $?' EXIT", script)
        self.assertNotIn("|| true", script)

    def test_ios_build_phases_do_not_inherit_source_signing_secrets(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/build_ios_candidate.sh").read_text(encoding="utf-8")
        xcode = script.index("xcodebuild \\")
        for token in (
            "unset PARLOR_APPLE_CERTIFICATE_PASSWORD",
            "unset PARLOR_APPLE_CERTIFICATE_P12_PATH",
            "unset PARLOR_APPLE_PROFILE_PATH",
        ):
            self.assertLess(script.index(token), xcode)

    def test_ios_artifact_validator_enforces_pinned_store_toolchain(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/validate_ios_artifact.sh").read_text(encoding="utf-8")
        for token in (
            "DTXcodeBuild",
            "DTSDKName",
            "MinimumOSVersion",
            '"xcrun", "vtool", "-show-build"',
            'platform != "IOS"',
            "macho_minimum_os_versions",
        ):
            self.assertIn(token, script)

    def test_all_release_temporary_directories_use_signal_safe_cleanup(self) -> None:
        for name in (
            "build_ios_candidate.sh",
            "upload_ios_candidate.sh",
            "validate_android_artifact.sh",
            "validate_ios_artifact.sh",
            "validate_release_system.sh",
        ):
            with self.subTest(script=name):
                script = (workflow_contract.ROOT / "scripts/release" / name).read_text(encoding="utf-8")
                self.assertIn("trap 'cleanup $?' EXIT", script)
                self.assertIn("trap 'exit 130' INT", script)
                self.assertIn("trap 'exit 143' TERM", script)

    def test_ios_upload_uses_scoped_key_directory_and_removes_raw_response(self) -> None:
        script = (workflow_contract.ROOT / "scripts/release/upload_ios_candidate.sh").read_text(encoding="utf-8")
        self.assertIn('export API_PRIVATE_KEYS_DIR="$temporary_dir/private_keys"', script)
        self.assertIn('rm -f "$raw_log"', script)
        self.assertNotIn("export HOME=", script)


if __name__ == "__main__":
    unittest.main()
