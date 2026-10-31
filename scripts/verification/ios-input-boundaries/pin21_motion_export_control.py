"""Repair PIN20 evidence intake without replaying its two successful journeys.

The same reviewed instrumented product runs only its existing isolated control.
This proves failure-only collection, not natural failure or shipping acceptance.
"""
import pin20_catalog_motion as motion

SOURCE, TREE, ARMS = motion.SOURCE, motion.TREE, motion.ARMS
PROFILE = 'isolated-catalog-motion-export-control'
CASES = (motion.CONTROL,)
RESULT = 'ISOLATED_CATALOG_MOTION_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only PIN20 existing isolated Host suppression/export control; original successful '
         'EN/AR journeys not replayed; not natural causality, shipping qualification or physical acceptance')


def renderer_for_profile(profile, source, runtime, device):
    motion.current.require((profile, source, runtime, device) ==
        (PROFILE, SOURCE, motion.pair.RUNTIME, motion.pair.DEVICE),
        'Exact isolated export control source/profile/runtime/device required')
    return motion.renderer_for_profile(motion.PROFILE, source, runtime, device)


def test_command(base, result):
    return [*(a for a in base if a != '-quiet'), 'test-without-building',
        '-resultBundlePath', str(result), '-only-testing:iosAppUITests/' + motion.CONTROL.removesuffix('()')]


def verify_results(summary, tests, simulator_id, code):
    return motion.verify_motion_results(summary, tests, simulator_id, code, cases=CASES)


def failed_method_exports(result, destination, outcomes):
    return motion.motion_method_exports(result, destination, outcomes, cases=CASES)


retain_companion = motion.retain_companion
retain_root_companion = motion.retain_root_companion
retain_motion_companion = motion.retain_motion_companion


def accept_native_export(out, state):
    return motion.accept_motion_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, motion.pair.RUNTIME, motion.pair.DEVICE),
        result_verifier=verify_results, cases=CASES, result=RESULT)
