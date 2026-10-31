"""Original 23-case native observations; never application qualification.

PIN12 selects the two affected methods. The original failed full run also had
four preceding container cases. This profile retains the exact copied product
and driver and restores the full original XCTest context, without a test retry.
"""
import re

import current_boundary_adapter as current
import pin06_public_setup_pair as pair
import pin09_native_boundary as boundary
import pin10_root_boundary as root
import pin12_public_boundaries as public
import run_pin03

SOURCE, TREE = public.SOURCE, public.TREE
PROFILE = 'native-original-full-cohort'
ARMS = public.ARMS
SCOPE = ('Exact original23 Swift case context with PIN12 public failure capture; original actions, '
         'assertions and deadlines preserved; not an unchanged two-method replay or app qualification')
# Original41180128325 case identities, independently checked against source/logs.
METHODS = {
    'ComposeContainerViewControllerTests': (
        'testChildUsesFullBoundsAcrossSizesAndOpposingSemanticDirections',
        'testDefaultAppearanceForwardingReachesTheSameChildExactlyOnce',
        'testOneStableChildKeepsItsExplicitSemanticsIndependentOfTheOuterView',
        'testSystemDecorationAndOrientationPoliciesStillBelongToTheChild'),
    'IOSAppLaunchUITests': (
        'testAdditionalGamesExposeOnlyMultiplayerSetupInArabic',
        'testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish',
        'testColdLaunchRendersComposeHomeWithoutUnexpectedAlert',
        'testKeyboardInsetsOnNameAndRoomCodeInArabic',
        'testKeyboardInsetsOnNameAndRoomCodeInEnglish',
        'testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone'),
    'KeyboardIntroductionDismissalTests': (
        'testGenericContinueWithoutIdentifiedTutorialNeverAdmitsATap',
        'testLateIntroductionAfterInitialAbsenceCanBeDismissedExactlyOnce',
        'testMissingInvalidOffscreenOrUpperScreenButtonCannotBeDismissed',
        'testNonhittableIntroductionDoesNotConsumeTheSingleDismissal'),
    'PublicControlFramesTests': (
        'testDisabledControlBreaksFrameAdjacency',
        'testFirstFrameNeverSettlesButTheExactAdjacentFrameDoes',
        'testHittabilityInterruptionRequiresTwoFreshFrames',
        'testInvalidOrEmptyGeometryCannotSettleAndBreaksAdjacency',
        'testMissingControlBreaksFrameAdjacency',
        'testMotionIsNotRoundedIntoASettledFrame'),
    'PublicTextValueTests': (
        'testMissingOrNonStringValueNeverMatchesEvenAnEmptyExpectation',
        'testOnlyTheCompleteExpectedStringMatches',
        'testWhitespaceCaseAndArabicCharactersAreNotRewritten'),
}
CASES = tuple(c + '/' + m + '()' for c, methods in METHODS.items() for m in methods)
PUBLIC = tuple(t.removeprefix('iosAppUITests/') + '()' for t in run_pin03.TESTS)


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact original full cohort source/profile/runtime/device required')
    return public.renderer_for_profile(public.PROFILE, SOURCE, runtime, device)


def test_command(base, result):
    # The current two-method runner's -quiet suppresses original case starts.
    # Retain ordinary XCTest output to prove the restored original order.
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:iosAppUITests/' + t.removesuffix('()') for t in CASES)]


def verify_results(summary, tests, simulator_id, code):
    current.require(type(code) is int and code in (0, 65), 'Actual native pass or retained failed exit65 required')
    for key in ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures'):
        current.require(type(summary.get(key)) is int and 0 <= summary[key] <= len(CASES),
                        'Exact bounded original native cohort counts required')
    current.require(summary['totalTestCount'] == len(CASES) == 23 and summary['skippedTests'] == 0
        and summary['expectedFailures'] == 0 and summary['passedTests'] + summary['failedTests'] == 23,
        'All23 original cases must execute, with no skips or expected failures')
    configurations, devices = summary.get('devicesAndConfigurations', []), tests.get('devices', [])
    current.require(len(configurations) == len(devices) == 1, 'One actual owned native configuration required')
    for device in (configurations[0].get('device', {}), devices[0]):
        current.require(device.get('deviceId') == simulator_id and device.get('architecture') == 'arm64'
            and device.get('platform') == 'iOS Simulator' and device.get('modelName') == 'iPhone 16 Pro'
            and device.get('osVersion') == '18.5', 'Exact actual owned ARM64 iOS18.5 iPhone16Pro required')
    cases = []
    def visit(nodes):
        for node in nodes:
            if node.get('nodeType') == 'Test Case': cases.append(node)
            visit(node.get('children', []))
    visit(tests.get('testNodes', []))
    current.require(len(cases) == 23 and {p.get('nodeIdentifier') for p in cases} == set(CASES)
        and all(p.get('result') in ('Passed', 'Failed') for p in cases),
        'Every original case identity and executed outcome required')
    outcomes = {p['nodeIdentifier']: p['result'] for p in cases}
    passed = sum(v == 'Passed' for v in outcomes.values())
    current.require(passed == summary['passedTests'] and (code == 0) == (passed == 23)
        and summary.get('result') == ('Passed' if passed == 23 else 'Failed'),
        'Original exit/summary/all case outcomes must agree')
    # Broader failures remain failures; never qualify from only target passes.
    current.require(all(v == 'Passed' for k,v in outcomes.items() if k not in PUBLIC),
                    'Every other original native case must pass; unrelated failures cannot be waived')
    return {k: outcomes[k] for k in PUBLIC}


failed_method_exports = public.failed_method_exports
retain_companion = public.retain_companion
retain_root_companion = public.retain_root_companion


def accept_native_export(out, state):
    return accept_cohort_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE))


def accept_cohort_export(out, state, *, profile, renderer):
    boundary.accept_observation_export(out, state, profile=profile,
        renderer=renderer, result_verifier=verify_results)
    root.accept_root_companions(out, state)
    # JSON case sets do not prove execution order. Bind the original four-case
    # prelude and both target methods directly to the retained original test log.
    log = (out/'compose/test.log').read_bytes()
    current.require(len(log) <= 4*1024*1024, 'Bounded original cohort execution log required')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.", log)
    current.require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES,
                    'Original complete23-case order, including the exact container prelude, required')
    return 'ORIGINAL_FULL_COHORT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
