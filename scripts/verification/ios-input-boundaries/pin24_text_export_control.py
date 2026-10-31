"""Qualify the corrected PIN23 failure owner without replaying passing Arabic.

Same prepared input adapter and application; only the isolated sentinel control
executes. A genuine sentinel failure plus all failure-only exports is required.
"""
import pin23_text_session_boundary as text

SOURCE, TREE, ARMS = text.SOURCE, text.TREE, text.ARMS
PROFILE = 'isolated-public-text-session-export-control'
CASES = (text.CONTROL,)
RESULT = 'ISOLATED_PUBLIC_TEXT_SESSION_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only corrected PIN23 sentinel export control; no replay of passing English/Arabic '
         'journeys, no application cause, shipping qualification or physical acceptance')


def renderer_for_profile(profile,source,runtime,device):
    text.require((profile,source,runtime,device)==(PROFILE,SOURCE,text.motion.pair.RUNTIME,text.motion.pair.DEVICE),
        'Exact isolated public-text export source/profile/runtime/device required')
    return text.renderer_for_profile(text.PROFILE,source,runtime,device)


def test_command(base,result):
    return [*(a for a in base if a!='-quiet'),'test-without-building','-resultBundlePath',str(result),
        '-only-testing:iosAppUITests/'+text.CONTROL.removesuffix('()')]


def verify_results(summary,tests,simulator_id,code):
    return text.verify_text_results(summary,tests,simulator_id,code,cases=CASES)


def failed_method_exports(result,destination,outcomes):
    return text.text_method_exports(result,destination,outcomes,cases=CASES)


retain_companion = text.retain_companion
retain_root_companion = text.retain_root_companion
retain_motion_companion = text.retain_motion_companion


def accept_native_export(out,state):
    return text.accept_text_export(out,state,profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,text.motion.pair.RUNTIME,text.motion.pair.DEVICE),cases=CASES,result=RESULT)
