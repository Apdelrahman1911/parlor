"""Restore original Swift suite context with the already verified input adapter.

No new observer or sample loop. The earlier wrapper cohort changed the native
prelude and test order. Keep all original methods once, original assertions and
failure-only capture, including the all-thread receiver/first-responder boundary.
"""
from contextlib import contextmanager

import pin59_keyboard_sessions as prior

SOURCE, TREE, RUNTIME, DEVICE, ARMS = prior.SOURCE, prior.TREE, prior.RUNTIME, prior.DEVICE, prior.ARMS
PROFILE = 'original-suite-verified-native-input'
RESULT = 'ORIGINAL_SUITE_NATIVE_INPUT_COLLECTION_COMPLETE_NOT_QUALIFICATION'
SCOPE = 'Original23 Swift methods once in original order; unchanged native adapter; not causality, timing neutrality, shipping or physical qualification'
TEST_TIMEOUT_SECONDS = prior.TEST_TIMEOUT_SECONDS
require, load, runtime, MAX_BYTES = prior.require, prior.load, prior.runtime, prior.MAX_BYTES
decode, retain_companion, require_capability = prior.decode, prior.retain_companion, prior.require_capability
ROSTER = {
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
CASES = tuple(cls + '/' + method + '()' for cls, methods in ROSTER.items() for method in methods)
TESTS = tuple('iosAppUITests/' + case.removesuffix('()') for case in CASES)


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Exact original suite source/runtime')
    previous = prior.renderer_for_profile(prior.PROFILE, source, os, device)
    def render(sources, digests, *, arm):
        plan = previous(sources, digests, arm=arm)
        driver = prior.native.prior.pin02_capture.DRIVER
        plan[driver] = prior.native.prior.stress.restore_driver(plan[driver])
        return plan
    return render


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building', '-resultBundlePath', str(result),
            *('-only-testing:' + test for test in TESTS)]


@contextmanager
def original_selection(renderer=None):
    changes = dict(CASES=CASES, TESTS=TESTS)
    if renderer is not None:
        changes.update(PROFILE=PROFILE, RESULT=RESULT, renderer_for_profile=renderer)
    saved = {key: getattr(prior, key) for key in changes}
    try:
        for key, value in changes.items():
            setattr(prior, key, value)
        yield
    finally:
        for key, value in saved.items():
            setattr(prior, key, value)


def verify_results(summary, tests, simulator, code):
    with original_selection():
        return prior.verify_results(summary, tests, simulator, code)


def failed_method_exports(result, destination, outcomes):
    with original_selection():
        return prior.failed_method_exports(result, destination, outcomes)


def accept_native_export(out, state):
    prepared = renderer_for_profile(PROFILE, SOURCE, RUNTIME, DEVICE)
    def exact(profile, source, os, device):
        require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Declared original suite only')
        return prepared
    with original_selection(exact):
        return prior.accept_native_export(out, state)
