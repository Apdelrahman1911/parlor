"""Original combined Xcode process boundary; not production qualification.

PIN14 ran build-for-testing and test-without-building separately. This profile
changes only that observed process boundary, retaining original23 cases and all
application/driver/observer bytes. It does not claim to restore the prior KMP
workload or prove that instrumentation leaves scheduling unchanged.
"""
import hashlib
import json

import current_boundary_adapter as current
import pin01_receipts
import pin06_public_setup_pair as pair
import pin13_original_cohort as cohort
import pin14_original_resources as resources

SOURCE, TREE = resources.SOURCE, resources.TREE
PROFILE = 'native-combined-xcode-context'
ARMS = resources.ARMS
STAGES = ('before-build', 'after-tests')
SCOPE = ('Original23 public cases in one combined Xcode test process under original6GiB parallel '
         'source settings; post-command product manifest, no app fix, qualification, original KMP '
         'prelude, instrumentation-neutrality or physical claim')


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact combined Xcode context required')
    return resources.renderer_for_profile(resources.PROFILE, source, runtime, device)


build_environment = resources.build_environment
observe_resources = resources.observe_resources
verify_results = cohort.verify_results
failed_method_exports = cohort.failed_method_exports
retain_companion = cohort.retain_companion
retain_root_companion = cohort.retain_root_companion


def test_command(base, result):
    command = cohort.test_command(base, result)
    current.require(command.count('test-without-building') == 1, 'Exact original case command required')
    return ['test' if item == 'test-without-building' else item for item in command]


def verify_commands(state, row):
    current.require(state['profile'] == PROFILE and row['arm'] == 'compose', 'Combined profile only')
    commands = state['commands']
    current.require(not any(p.get('label') in ('compose-build', 'compose-tests') for p in commands),
                    'No separate build or test replay permitted')
    selected = [p for p in commands if p.get('label') == 'compose-build-and-tests']
    current.require(len(selected) == 1 and selected[0].get('exit_code') == row['test_exit'] == row['combined_exit']
        and type(selected[0].get('timeout_seconds')) is int and selected[0]['timeout_seconds'] == 3600,
        'One actual combined command within original summed build/test budgets required')
    argv = selected[0].get('command', [])
    current.require(type(argv) is list and argv.count('/usr/bin/xcodebuild') == argv.count('test') == 1
        and not any(p in argv for p in ('build-for-testing', 'test-without-building', '-retry-tests-on-failure',
                                       '-test-iterations', '-run-tests-until-failure', '-jobs', '-quiet')),
        'Exact single native build/test action without retries or job overrides required')
    current.require(tuple(p.removeprefix('-only-testing:iosAppUITests/') + '()' for p in argv
                          if p.startswith('-only-testing:')) == cohort.CASES,
                    'All23 unchanged original cases required')
    for flag, value in (('-configuration', 'Debug'), ('-scheme', 'iosApp'), ('-sdk', 'iphonesimulator'),
                        ('-destination', 'platform=iOS Simulator,id=' + row['simulator']),
                        ('-parallel-testing-enabled', 'NO'), ('-maximum-concurrent-test-simulator-destinations', '1'),
                        ('-destination-timeout', '120')):
        current.require(argv.count(flag) == 1 and argv[argv.index(flag) + 1] == value,
                        'Original owned Debug native arguments required')
    stops = [p for p in commands if p.get('label') == 'compose-gradle-stop']
    current.require(len(stops) == 1 and stops[0].get('exit_code') == 0, 'Single owned Gradle cleanup required')
    current.require(row.get('product_manifest_stage') == 'after-combined-command' and
        row.get('build_success_evidence') == 'all23-executed-native-outcomes',
        'Post-command product custody and actual compilation evidence must be disclosed')


def accept_native_export(out, state):
    cohort.accept_cohort_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE), command_verifier=verify_commands)
    profile = state.get('build_profile', {})
    current.require(profile.get('combined_xcode') is True and profile.get('combined_timeout_seconds') == 3600
        and profile.get('gradle_heap_bytes') == 6 * 1024**3 and profile.get('gradle_workers') == 'original-default'
        and profile.get('gradle_parallel') is True and profile.get('boot_after_build') is False
        and profile.get('xcode_jobs') == 'original-default'
        and profile.get('product_manifest_stage') == 'after-combined-command', 'Exact combined resource context')
    row = state['arms'][0]
    current.require(set(row.get('kernel_resources', {})) == set(STAGES), 'Two actual kernel boundaries required')
    for stage in STAGES:
        raw = (out / 'compose' / ('kernel-' + stage + '.json')).read_bytes()
        current.require(0 < len(raw) <= 4096 and hashlib.sha256(raw).hexdigest() == row['kernel_resources'][stage],
                        'Exact original kernel record custody')
        resources.validate_resources(json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates))
    return 'COMBINED_XCODE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
