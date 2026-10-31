"""Correct failed PIN25 capture intake without replaying the passing Arabic journey.

Only the existing sentinel executes. This supplies the native evidence discarded
by the rejected read ceiling; installation is still unverified until readback.
"""
import pin24_text_export_control as isolated
import pin25_native_input_delivery as delivery

SOURCE, TREE, ARMS = delivery.SOURCE, delivery.TREE, delivery.ARMS
PROFILE = 'isolated-native-input-delivery-export-control'
CASES = isolated.CASES
RESULT = 'ISOLATED_NATIVE_INPUT_DELIVERY_EXPORT_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only existing sentinel with corrected owned numeric capture intake; no replay of passing '
         'English/Arabic journeys, no repaired gestures, application cause, shipping or physical qualification')


def renderer_for_profile(profile,source,runtime,device):
    delivery.text.require((profile,source,runtime,device)==(PROFILE,SOURCE,delivery.text.motion.pair.RUNTIME,
        delivery.text.motion.pair.DEVICE),'Exact isolated native-delivery export source/profile/runtime/device required')
    return delivery.renderer_for_profile(delivery.PROFILE,source,runtime,device)


test_command = isolated.test_command
verify_results = isolated.verify_results
failed_method_exports = isolated.failed_method_exports
retain_companion = delivery.retain_companion
retain_root_companion = delivery.retain_root_companion
retain_motion_companion = delivery.retain_motion_companion


def accept_native_export(out,state):
    return delivery.accept_delivery_export(out,state,profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,delivery.text.motion.pair.RUNTIME,delivery.text.motion.pair.DEVICE),
        cases=CASES,result=RESULT)
