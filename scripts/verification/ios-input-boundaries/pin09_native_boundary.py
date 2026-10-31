"""Observe original native getters, without synthetic failure or touch recognizer.

PIN08 separately proves failure-only capture capability. This distinct experiment
adds missing native getter observations to the exact original EN/AR journeys;
no original uninstrumented retry, extra input/query, shipping fix or qualification.
"""
import hashlib

import current_boundary_adapter as current
import frozen_sources
import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_native_adapter as prior
import pin08_accessibility as native

SOURCE, TREE = current.SOURCE, current.TREE
PROFILE = 'native-getter-public-boundaries'
# Compose-only pointer observation plus original native AX getters: importantly,
# no additional UIGestureRecognizer is installed in this experiment.
ARMS = ('compose',)
SCOPE = ('Single Compose-observed copy of actual failed5029 original EN/AR journeys with original '
         'native getter returns and failed-only capture; no added touch recognizer, synthetic fault, '
         'uninstrumented retry, shipping qualification, neutrality or physical acceptance')


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact native getter public-boundary profile required')
    def render(sources, digests, *, arm):
        current.require(arm == 'compose', 'Only Compose-only pointer/native getter observation allowed')
        output = current.renderer_for_profile(current.PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)(
            sources, digests, arm=arm)
        raw = frozen_sources._read(pair.HERE / native.TEMPLATE, frozen_sources.MAX_SOURCE_BYTES)
        current.require(hashlib.sha256(raw).hexdigest() == native.TEMPLATE_SHA256,
                        'Reviewed native getter template required')
        container = pin01_copy.once(output[pin02_capture.CONTAINER],
            '    private var pin02Capture: PIN02Capture?',
            '    private var pin02Capture: PIN02Capture?\n    private var pin08Accessibility: PIN08AccessibilityObserver?')
        container = pin01_copy.once(container,
            '        pin02Capture = PIN02Capture.install(snapshot: { MainViewControllerKt.PIN01InputSnapshot() })',
            native.INSTALL)
        output[pin02_capture.CONTAINER] = container + '\n' + raw.decode()
        return output
    return render


test_command = current.test_command
verify_results = current.verify_results
failed_method_exports = current.failed_method_exports
retain_companion = native.retain_companion


def accept_native_export(out, state):
    renderer = renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    current.accept_observations(out, state, profile=PROFILE, arms=ARMS, renderer=renderer)
    row = state['arms'][0]; dest = out / 'compose'
    failures = [method for method, outcome in row['methods'].items() if outcome == 'Failed']
    captures = sorted(dest.glob('capture-*.json'))
    companions = sorted(dest.glob('native-accessibility-*.json'))
    current.require(len(companions) == len(row.get('companion_captures', [])) == len(failures),
                    'Exactly one complete native companion per original failure, none on success')
    decoded = []
    for path in captures:
        legacy = prior.load(path, pin02_capture.MAX_BYTES)
        token = legacy['token']
        companion = dest / ('native-accessibility-' + token + '.json')
        current.require(companion in companions, 'Exact original failed token companion required')
        decoded.append(native.decode_native(frozen_sources._read(companion, native.MAX_BYTES), token=token,
            simulator=row['simulator'], bundle=state['debug_bundle'], legacy=legacy))
    current.require(row.get('companion_captures', []) == decoded,
                    'Native companion collection must match the original bytes in token order')
    return 'NATIVE_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
