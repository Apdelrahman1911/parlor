"""Distinct root-lifetime capability, not an application fix or qualification."""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin06_public_setup_pair as pair
import pin08_accessibility as native
import pin09_native_boundary as boundary
import run_pin03

SOURCE, TREE = native.SOURCE, native.TREE
PROFILE = 'synthetic-native-root-capability'
ARMS = native.ARMS
SCOPE = ('New root read/write capability beside independently verified PIN08 getters; exact intentional '
         'failed Host assertion, not natural causality, shipping qualification or physical evidence')
TEMPLATE = 'PIN10RootObserver.swift.in'
TEMPLATE_SHA256 = '1dc4ad1fb63788be0f7550ebb4c8dde82677ec74600343139d50729f8f682c32'
MAX_ROWS = 8192
MAX_BYTES = 1_048_576
INSTALL_PREFIX = '''        let rootObservation = PIN10RootObserver(armed: { MainViewControllerKt.PIN01InputArmed() })
        _ = rootObservation.install()
        pin10Root = rootObservation
'''


def add_root_observation(plan):
    raw = frozen_sources._read(pair.HERE / TEMPLATE, frozen_sources.MAX_SOURCE_BYTES)
    native.require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact root observer template required')
    container = pin01_copy.once(plan[pin02_capture.CONTAINER],
        '    private var pin08Accessibility: PIN08AccessibilityObserver?',
        '    private var pin08Accessibility: PIN08AccessibilityObserver?\n    private var pin10Root: PIN10RootObserver?')
    container = pin01_copy.once(container, native.INSTALL, INSTALL_PREFIX + native.INSTALL)
    container = pin01_copy.once(container, 'snapshot: { [weak observation] in',
                               'snapshot: { [weak observation, weak rootObservation] in')
    container = pin01_copy.once(container, '            observation?.exportAfterFailureOnce()\n',
        '            observation?.exportAfterFailureOnce()\n            rootObservation?.exportAfterFailureOnce()\n')
    return plan | {pin02_capture.CONTAINER: container + '\n' + raw.decode()}


def renderer_for_profile(profile, source, runtime, device):
    native.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                   'Exact distinct root capability required')
    base = native.renderer_for_profile(native.PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE)
    def render(sources, digests, *, arm):
        return add_root_observation(base(sources, digests, arm=arm))
    return render


test_command = native.test_command
verify_results = native.verify_results
failed_method_exports = native.failed_method_exports
retain_companion = native.retain_companion


def validate_root_envelope(raw, *, token, simulator, bundle, legacy):
    native.require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded original root report required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite root value')))
    native.require(type(value) is dict and set(value) == {'schema', 'token', 'simulator', 'bundle', 'process',
        'capturedMs', 'installed', 'overflow', 'installation', 'rows'}, 'Unknown/private root field')
    native.require(type(value['schema']) is int and value['schema'] == 1 and value['token'] == token and
        value['simulator'] == simulator and value['bundle'] == bundle and value['process'] == legacy['process'] and
        type(value['process']) is int and value['process'] > 0, 'Exact root token/device/product/process required')
    native.require(type(value['capturedMs']) in (int, float) and math.isfinite(value['capturedMs']) and
        0 <= value['capturedMs'] <= legacy['capturedMs'], 'Original root capture clock required')
    native.require(type(value['installed']) is bool and type(value['overflow']) is int and
        0 <= value['overflow'] <= 1_000_000, 'Bounded root status required')
    stages = value['installation']
    native.require(type(stages) is list and len(stages) == 5 and all(type(v) is int for v in stages) and
        0 <= stages[1] <= stages[0] <= 100_000 and all(0 <= v <= 127 for v in stages[2:4]) and
        0 <= stages[4] <= 5, 'Numeric root installation stages only')
    native.require(type(value['rows']) is list and len(value['rows']) <= MAX_ROWS, 'Bounded root rows required')
    finished = 0
    for row in value['rows']:
        native.require(type(row) is list and len(row) == 4 and
            all(type(v) in (int, float) and math.isfinite(v) for v in row) and
            0 <= row[0] <= row[1] <= value['capturedMs'] and row[1] >= finished and
            row[2] in (1, 2) and row[3] in (0, 1), 'Original numeric root intervals/type/presence only')
        finished = row[1]
    return value


def decode_root(raw, *, token, simulator, bundle, legacy):
    value = validate_root_envelope(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    native.require(value['installed'] and value['overflow'] == 0 and
        value['installation'][1:] == [1, 64, 118, 5], 'Unique exact root ABI and complete buffer required')
    native.require(any(r[2:] == [1, 1] for r in value['rows']) and
        any(r[2:] == [2, 1] for r in value['rows']), 'Actual populated original root getter/setter required')
    return dict(sha256=hashlib.sha256(raw).hexdigest(), token=token, rows=len(value['rows']), complete=True,
        reads=sum(r[2] == 1 for r in value['rows']), writes=sum(r[2] == 2 for r in value['rows']),
        nil_reads=sum(r[2:] == [1, 0] for r in value['rows']), nil_writes=sum(r[2:] == [2, 0] for r in value['rows']),
        scope='original root nil/non-nil only; not array contents, target lifetime or cause')


def retain_root_companion(sandbox, destination, token, simulator, bundle, legacy):
    path = sandbox / 'tmp' / ('parlor-pin10-' + token) / 'root.json'
    native.require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(),
                   'Exact nonredirected owned root file required')
    raw = run_pin03.read_capture(path, maximum=native.MAX_BYTES)
    validate_root_envelope(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination / ('native-root-' + token + '.json')).open('xb') as f:
        f.write(raw)
    return decode_root(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)


def accept_root_companions(out, state):
    for row in state['arms']:
        dest = out / row['arm']
        captures = sorted(dest.glob('capture-*.json'))
        roots = sorted(dest.glob('native-root-*.json'))
        native.require(len(roots) == len(captures) == len(row.get('root_companion_captures', [])),
                       'Exact once-only root file per failed capture; none on success')
        decoded = []
        for path in captures:
            legacy = json.loads(frozen_sources._read(path, pin02_capture.MAX_BYTES))
            token = legacy['token']; root = dest / ('native-root-' + token + '.json')
            native.require(root in roots, 'Exact failed-only root token required')
            decoded.append(decode_root(frozen_sources._read(root, MAX_BYTES), token=token,
                simulator=row['simulator'], bundle=state['debug_bundle'], legacy=legacy))
        native.require(decoded == row.get('root_companion_captures', []), 'Original root collection differs')


def accept_native_export(out, state):
    native.accept_capability_export(out, state, profile=PROFILE,
        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE))
    accept_root_companions(out, state)
    return 'SYNTHETIC_NATIVE_ROOT_CAPABILITY_ONLY'
