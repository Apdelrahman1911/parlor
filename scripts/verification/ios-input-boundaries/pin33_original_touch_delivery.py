"""Fill the missing original UIKit touch boundary, never add a recognizer/repair.

PIN31's Permission and catalog failures have no original native delivery ledger.
The existing source-provided input container selects the original recognizer;
its original touch/arbitration/state calls forward unchanged exactly once.
"""
import hashlib
import json
from pathlib import Path
import re

import frozen_sources
import pin01_copy
import pin02_capture
import pin27_attached_input_delivery as attached
import pin32_catalog_overscroll_boundary as previous
import pin33_touch_decoder as decoder
import run_pin03

SOURCE, TREE, ARMS = previous.SOURCE, previous.TREE, previous.ARMS
PROFILE = 'original-native-touch-delivery-boundaries'
CASES, TESTS, TEST_TIMEOUT_SECONDS = previous.CASES, previous.TESTS, previous.TEST_TIMEOUT_SECONDS
RESULT = 'ORIGINAL_NATIVE_TOUCH_DELIVERY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Ten fixed original EN/AR samples once; observe existing original Compose touch/arbitration/state '
         'methods with same original arguments/results, no added recognizer or pointer modifier, '
         'native timing neutrality, input repair, causality, qualification or physical acceptance')
HERE = Path(__file__).resolve().parent
TEMPLATE = 'PIN33OriginalTouchDelivery.swift.in'
TEMPLATE_SHA256 = 'f776602c71b96f75e5c695f769406de700e81cfbf64a83c45c0b94b0137a436f'
PROPERTY = '    private var pin33Delivery: PIN33OriginalTouchDelivery?\n'
EXPORT = '            self?.pin33Delivery?.exportAfterFailureOnce()\n'
REGISTER = '        _ = MainViewControllerKt.PIN27SetInputContainerObserver(observer: PIN27ContainerAdapter(attachment))\n'
OBSERVED_REGISTER = '''        let originalTouches = PIN33OriginalTouchDelivery(armed: { MainViewControllerKt.PIN11PublicAXArmed() })
        pin33Delivery = originalTouches
        _ = MainViewControllerKt.PIN27SetInputContainerObserver(observer: PIN27ContainerAdapter(attachment, originalTouches))
'''
ADAPTER = attached.ADAPTER
OBSERVED_ADAPTER = ADAPTER.replace('    private let attachment: PIN27InputAttachment\n',
    '    private let attachment: PIN27InputAttachment\n    private let originalTouches: PIN33OriginalTouchDelivery\n').replace(
    '    init(_ attachment: PIN27InputAttachment) { self.attachment = attachment; super.init() }\n',
    '    init(_ attachment: PIN27InputAttachment, _ originalTouches: PIN33OriginalTouchDelivery) {\n'
    '        self.attachment = attachment; self.originalTouches = originalTouches; super.init()\n    }\n').replace(
    '    func observeInputContainer(view: UIView) { _ = attachment.bindInputContainer(view) }\n',
    '    func observeInputContainer(view: UIView) {\n        _ = attachment.bindInputContainer(view)\n'
    '        _ = originalTouches.bindInputContainer(view)\n    }\n')


def template():
    raw = frozen_sources._read(HERE/TEMPLATE, decoder.MAX_BYTES)
    decoder.require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact original-call observer source')
    return raw.decode()


def renderer_for_profile(profile, source, runtime, device):
    pair = previous.previous.observed.original.motion.pair
    decoder.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
        'Exact original native-touch source/profile/device')
    base = previous.renderer_for_profile(previous.PROFILE, source, runtime, device)
    def render(sources, digests, *, arm):
        plan = base(sources, digests, arm=arm)
        native = pin01_copy.once(plan[pin02_capture.CONTAINER], attached.PROPERTY, attached.PROPERTY+PROPERTY)
        native = pin01_copy.once(native, REGISTER, OBSERVED_REGISTER)
        native = pin01_copy.once(native, ADAPTER, OBSERVED_ADAPTER)
        native = pin01_copy.once(native, attached.EXPORT, attached.EXPORT+EXPORT)
        plan[pin02_capture.CONTAINER] = native+'\n'+template()
        return plan
    return render


test_command, verify_results, failed_method_exports = previous.test_command, previous.verify_results, previous.failed_method_exports
retain_root_companion, retain_motion_companion = previous.retain_root_companion, previous.retain_motion_companion
verify_commands = previous.verify_commands


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    original = previous.retain_companion(sandbox, destination, token, simulator, bundle, legacy)
    path = sandbox/'tmp'/('parlor-pin33-'+token)/'touches.json'
    decoder.require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Owned original-touch file')
    raw = run_pin03.read_capture(path, maximum=decoder.MAX_BYTES)
    decoder.decode(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination/('native-touch-'+token+'.json')).open('xb') as f: f.write(raw)
    return original


def accept_native_export(out, state):
    req = decoder.require
    pair = previous.previous.observed.original.motion.pair
    observed = previous.previous.observed
    req(state['original_inputs'].get(observed.original.text.NAME) == observed.original.text.NAME_SHA256,
        'Exact original public Name source required')
    # Same generic original-source/product/outcome/commands/cleanup gate, with
    # this exact new renderer; do not relabel state as an older accepted profile.
    observed.original.motion.boundary.accept_observation_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
        result_verifier=verify_results, command_verifier=verify_commands)
    observed.original.verify_companion_files(out, state)
    observed.verify_failed_queries(out, state)
    dest = out/'compose'
    original_getters, original_touches = [], []
    for path in sorted(dest.glob('capture-*.json')):
        legacy = json.loads(frozen_sources._read(path, pin02_capture.MAX_BYTES))
        req(not any(row[1] in (1,2) for row in legacy['snapshot']['rows']), 'No invented added-pointer observations')
        args = dict(token=legacy['token'], simulator=state['arms'][0]['simulator'], bundle=state['debug_bundle'], legacy=legacy)
        original_getters.append(previous.decode_overscroll(frozen_sources._read(
            dest/('catalog-overscroll-'+legacy['token']+'.json'), previous.MAX_BYTES), **args))
        touch = decoder.decode(frozen_sources._read(dest/('native-touch-'+legacy['token']+'.json'), decoder.MAX_BYTES), **args)
        req(touch['complete'], 'Actual unique original recognizer and exact ABI installation, zero overflow required')
        original_touches.append(touch)
    req(len(list(dest.glob('catalog-overscroll-*.json'))) == len(original_getters) and
        len(list(dest.glob('native-touch-*.json'))) == len(original_touches), 'One original companion set per failure only')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(dest/'test.log',4*1024*1024))
    req(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'All ten fixed original cases once in order')
    receipt = dict(methods=state['arms'][0]['methods'], original_getters=original_getters,
        original_touches=original_touches, added_recognizer=False, compose_pointer_observers=False,
        application_root_cause=False, shipping_qualified=False, native_timing_neutrality=False, physical_verified=False)
    target = out/'original-touch-observations.json'
    if target.exists(): req(json.loads(target.read_bytes()) == receipt, 'Exact original saved native-touch receipt')
    else:
        with target.open('x') as f: json.dump(receipt, f, indent=2)
    return RESULT
