"""Narrow original-native-return discriminator; no new XCTest queries or app patch."""
from contextlib import contextmanager
import hashlib
import json
import math
from pathlib import Path

import pin57_input_intermittency as prior

SOURCE, TREE, RUNTIME, DEVICE, ARMS = prior.SOURCE, prior.TREE, prior.RUNTIME, prior.DEVICE, prior.ARMS
CASES, TESTS, TEST_TIMEOUT_SECONDS = prior.CASES, prior.TESTS, prior.TEST_TIMEOUT_SECONDS
PROFILE = 'original-native-ax-traits-element-returns'
RESULT = 'ORIGINAL_NATIVE_AX_RETURN_COLLECTION_COMPLETE_NOT_QUALIFICATION'
SCOPE = 'Original native traits/element returns and public catalog label classification, no proactive AX query or input repair'
require, load, runtime, MAX_BYTES = prior.require, prior.load, prior.runtime, prior.MAX_BYTES
TEMPLATE = Path(__file__).with_name('PIN58NativeAXReturns.swift.in')
TEMPLATE_SHA256 = '04d055e8d7c2db99925997d587e5140ee81f1409cc1f39c4727e844246a3a4a2'
PRIOR_RENDERER = prior.renderer_for_profile
PRIOR_DECODE = prior.decode
PRIOR_ACCEPT = prior.accept_native_export
PRIOR_PROFILE = prior.PROFILE


def observation_source():
    raw = prior.frozen_sources._read(TEMPLATE, 65536)
    require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Frozen native-return observer')
    return raw.decode()


def require_capability():
    # Previously verified adapters unchanged except the explicitly new descendant-return observer.
    prior.require_capability()


def renderer_for_profile(profile, source, os, device):
    require((profile, source, os, device) == (PROFILE, SOURCE, RUNTIME, DEVICE), 'Exact native-return profile')
    base = PRIOR_RENDERER(PRIOR_PROFILE, source, os, device)
    once = prior.p.prior.base.pin01_copy.once
    def render(sources, digests, *, arm):
        plan = base(sources, digests, arm=arm)
        owner = plan[prior.pin02_capture.CONTAINER]
        owner = once(owner, '    private let getter = NSSelectorFromString("accessibilityElements")',
            '    private let leafReturns: PIN58NativeAXReturns\n'
            '    private let getter = NSSelectorFromString("accessibilityElements")')
        owner = once(owner, '        self.armed = armed\n        rows.reserveCapacity(limit)',
            '        self.armed = armed\n        leafReturns = PIN58NativeAXReturns(armed: armed)\n        rows.reserveCapacity(limit)')
        owner = once(owner, '            originalSet(object, setSelector, value)',
            '            if observed { self?.leafReturns.observeOriginalRootArray(value) }\n'
            '            originalSet(object, setSelector, value)')
        owner = once(owner, '        ["schema": 53, "installation": installation',
            '        ["leafReturns": leafReturns.freeze(), "schema": 53, "installation": installation')
        plan[prior.pin02_capture.CONTAINER] = owner + '\n' + observation_source()
        return plan
    return render


def decode(raw, *, token, simulator, bundle, legacy, require_control=False):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded actual native-return report')
    value = json.loads(raw, object_pairs_hook=prior.p.prior.base.pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite native return')))
    require(type(value) is dict and type(value.get('root')) is dict and 'leafReturns' in value['root'],
        'New original native return report required')
    leaf = value['root'].pop('leafReturns')
    native = PRIOR_DECODE(json.dumps(value).encode(), token=token, simulator=simulator,
        bundle=bundle, legacy=legacy, require_control=require_control)
    require(type(leaf) is dict and set(leaf) == {'schema','installed','capturedMs','rows','evicted','rejected','nodesAlive','scope'}
        and type(leaf['schema']) is int and leaf['schema'] == 58 and type(leaf['installed']) is bool,
        'Exact private-free native return schema')
    require(type(leaf['capturedMs']) in (int,float) and math.isfinite(leaf['capturedMs']) and
        legacy['installedMs'] <= leaf['capturedMs'] <= value['capturedMs'], 'Actual capture clock')
    require(all(type(leaf[k]) is int and 0 <= leaf[k] <= 1000000 for k in ('evicted','rejected')) and
        type(leaf['rows']) is list and len(leaf['rows']) <= 4096 and type(leaf['nodesAlive']) is list and
        len(leaf['nodesAlive']) <= 512 and all(type(v) is bool for v in leaf['nodesAlive']), 'Bounded weak rolling ledger')
    require(leaf['scope'] == 'Original native NotEnabled and isAccessibilityElement returns with original public catalog prefix classification; weak ordinals, rolling window; no text retained, not full semantics, CPU, causality or qualification', 'Limits retained')
    at = legacy['installedMs']
    for row in leaf['rows']:
        require(type(row) is list and len(row) == 6 and all(type(n) in (int,float) and math.isfinite(n) for n in row), 'Numeric returns only')
        now,main,kind,phase,ordinal,bit = row
        require(at <= now <= leaf['capturedMs'] and main in (0,1) and kind in (1,2,3) and phase in (0,1)
            and ordinal == int(ordinal) and 1 <= ordinal <= len(leaf['nodesAlive']) and bit == int(bit) and 0 <= bit <= (3 if kind == 3 else 1)
            and (phase == 1 or bit == 0), 'Ordered original return bits')
        at = now
    require(leaf['installed'] or (not leaf['rows'] and not leaf['nodesAlive'] and leaf['evicted'] == leaf['rejected'] == 0),
        'No invented uninstalled observations')
    require((leaf['evicted'] == 0 or len(leaf['rows']) == 4096) and
        (leaf['rejected'] == 0 or len(leaf['nodesAlive']) == 512), 'Actual lifetime and ring bounds')
    # Old decoder admits the old fields. Bind the outer digest to actual bytes, not stripped serialization.
    return native | {'sha256': hashlib.sha256(raw).hexdigest(), 'leafReturns': leaf,
        'application_root_cause': False, 'shipping_qualified': False}


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    path = sandbox/'tmp'/('parlor-pin56-'+token)/'delivery.json'
    require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Owned unchanged companion path')
    raw = prior.run_pin03.read_capture(path, maximum=MAX_BYTES)
    result = decode(raw, token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    with (destination/('native-text-'+token+'.json')).open('xb') as stream:
        stream.write(raw)
    return result


def test_command(base, result):
    return prior.test_command(base, result)


def verify_results(summary, tests, simulator, code):
    return prior.verify_results(summary, tests, simulator, code)


def failed_method_exports(result, destination, outcomes):
    return prior.failed_method_exports(result, destination, outcomes)


@contextmanager
def admission_scope(renderer):
    # Single-threaded reader only. Preserve all source/product/command/outcome/cleanup gates;
    # substitute only this declared source plan, decoder, profile and non-qualification label.
    names = ('PROFILE','RESULT','renderer_for_profile','decode')
    saved = {key:getattr(prior,key) for key in names}
    try:
        prior.PROFILE, prior.RESULT, prior.renderer_for_profile, prior.decode = PROFILE, RESULT, renderer, decode
        yield
    finally:
        for key,value in saved.items():
            setattr(prior,key,value)


def accept_native_export(out, state):
    prepared = renderer_for_profile(PROFILE, SOURCE, RUNTIME, DEVICE)
    def exact(profile, source, os, device):
        require((profile,source,os,device) == (PROFILE,SOURCE,RUNTIME,DEVICE), 'Declared plan only')
        return prepared
    with admission_scope(exact):
        return PRIOR_ACCEPT(out, state)
