"""Observe actual native target geometry alongside existing public-bit ordinals.

An XCTest frame and enabled result are sequential resolver observations, not
actual native target geometry. Only original CGRect returns are intercepted;
no extra property/query, gesture, timeout or private attribute is introduced.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin08_accessibility as native
import pin20_catalog_motion as motion
import run_pin03

SOURCE, TREE, ARMS = motion.SOURCE, motion.TREE, motion.ARMS
PROFILE = 'native-catalog-frame-return-boundaries'
CASES, CONTROL = motion.CASES, motion.CONTROL
RESULT = 'NATIVE_FRAME_RETURN_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Actual native CGRect returns with existing process-local public-bit ordinals, actual '
         'Compose motion/root returns, original EN/AR methods and isolated export control; '
         'not atomic XCTest identity, object lifetime, observer neutrality, natural cause or qualification')
TEMPLATE = 'PIN22NativeFrame.swift.in'
TEMPLATE_SHA256 = 'd9313b50e1079e96fd6f8328b3f9d85382ad17ff8f963a891512e92dfac4ac82'
MAX_BYTES, MAX_ROWS = 2097152, 8192
IDENTITY = '''        let identity = ObjectIdentifier(object)
        if elements[identity] == nil {
            guard elements.count < Self.capacity else { overflow = min(Self.overflowLimit, overflow + 1); return }
            elements[identity] = elements.count + 1
        }
'''
ORDINAL = '''    // COPY ONLY: shared with actual native CGRect returns. No object/address
    // is retained or exported; these ordinals are not object-lifetime proof.
    func pin22PublicOrdinal(_ object: AnyObject) -> Int {
        guard Thread.isMainThread else { return 0 }
        let identity = ObjectIdentifier(object)
        if elements[identity] == nil {
            guard elements.count < Self.capacity else { overflow = min(Self.overflowLimit, overflow + 1); return 0 }
            elements[identity] = elements.count + 1
        }
        return elements[identity]!
    }

'''
INSTALL = '''        let frames = PIN22NativeFrameObserver(armed: armed, ordinal: { [weak self] object in
            self?.pin22PublicOrdinal(object) ?? 0
        })
        guard frames.install(target: target) else { return false }
        pin22Frames = frames
'''


def native_template_extension(original):
    require = motion.current.require
    require(hashlib.sha256(original.encode()).hexdigest() == native.TEMPLATE_SHA256,
            'Exact original native getter template required')
    text = pin01_copy.once(original, '    private var attempted = false\n',
                          '    private var attempted = false\n    private var pin22Frames: PIN22NativeFrameObserver?\n')
    text = pin01_copy.once(text, IDENTITY,
                          '        let ordinal = pin22PublicOrdinal(object)\n        guard ordinal > 0 else { return }\n')
    text = pin01_copy.once(text, 'element: elements[identity]!, getter:', 'element: ordinal, getter:')
    text = pin01_copy.once(text, '    /// Invoked only INSIDE PIN02', ORDINAL+'    /// Invoked only INSIDE PIN02')
    text = pin01_copy.once(text, '        let replacementTraits = imp_implementationWithBlock(traitsBlock)\n',
                          INSTALL+'        let replacementTraits = imp_implementationWithBlock(traitsBlock)\n')
    text = pin01_copy.once(text, '        attempted = true\n',
                          '        attempted = true\n        pin22Frames?.exportAfterFailureOnce()\n')
    return text


def renderer_for_profile(profile, source, runtime, device):
    motion.current.require((profile, source, runtime, device) ==
        (PROFILE, SOURCE, motion.pair.RUNTIME, motion.pair.DEVICE), 'Exact native-frame source/profile/runtime/device required')
    base = motion.renderer_for_profile(motion.PROFILE, source, runtime, device)
    def render(sources, digests, *, arm):
        original = frozen_sources._read(motion.pair.HERE/native.TEMPLATE, frozen_sources.MAX_SOURCE_BYTES).decode()
        frame = frozen_sources._read(motion.pair.HERE/TEMPLATE, frozen_sources.MAX_SOURCE_BYTES)
        motion.current.require(hashlib.sha256(frame).hexdigest() == TEMPLATE_SHA256, 'Reviewed CGRect observer required')
        plan = base(sources, digests, arm=arm)
        container = pin01_copy.once(plan[pin02_capture.CONTAINER], original, native_template_extension(original))
        return plan | {pin02_capture.CONTAINER: container+'\n'+frame.decode()}
    return render


test_command = motion.test_command
verify_results = motion.verify_results
failed_method_exports = motion.failed_method_exports
retain_root_companion = motion.retain_root_companion
retain_motion_companion = motion.retain_motion_companion


def decode_frames(raw, *, token, simulator, bundle, legacy, attributes):
    req = motion.current.require
    req(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, 'Bounded original native frame file required')
    value = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite native frame')))
    req(type(value) is dict and set(value) == {'schema','token','simulator','bundle','process','capturedMs',
        'installed','overflow','invalid','rows'}, 'Only public native geometry fields allowed')
    req(type(value['schema']) is int and value['schema']==1 and
        (value['token'],value['simulator'],value['bundle'],value['process']) ==
        (token,simulator,bundle,legacy['process']) and type(value['process']) is int and value['process']>0,
        'Exact frame process/token/device/product required')
    req(type(value['capturedMs']) in (int,float) and math.isfinite(value['capturedMs']) and
        0 <= value['capturedMs'] <= attributes['capturedMs'] <= legacy['capturedMs'], 'Original frame capture clock required')
    req(value['installed'] is True and all(type(value[k]) is int and value[k]==0 for k in ('overflow','invalid')),
        'Actual CGRect ABI installation and complete valid buffer required')
    rows=value['rows'];req(type(rows) is list and 0<len(rows)<=MAX_ROWS,'Bounded actual native frame rows required')
    finished=0
    for r in rows:
        req(type(r) is list and len(r)==7 and all(type(x) in (int,float) and math.isfinite(x) for x in r) and
            0<=r[0]<=r[1]<=value['capturedMs'] and r[1]>=finished and
            r[2]==int(r[2]) and 1<=r[2]<=native.MAX_ROWS and all(abs(x)<=100000 for x in r[3:]) and
            r[5]>=0 and r[6]>=0,'Only original ordered numeric CGRect/ordinal rows allowed')
        finished=r[1]
    buttons={r[2] for r in attributes['rows'] if r[3]==1 and int(r[4])&1}
    matched={r[2] for r in rows if r[2] in buttons and r[5]>0 and r[6]>0}
    req(bool(matched),'Actual nonzero native public-button frames must share original attribute ordinals')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),token=token,rows=len(rows),matched_button_ordinals=len(matched),
                complete=True,scope='original native CGRect returns; local observation ordinals, not lifetime/XCTest identity')


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    report = motion.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    attributes = json.loads((destination/('native-accessibility-'+token+'.json')).read_bytes())
    path=sandbox/'tmp'/('parlor-pin22-'+token)/'frames.json'
    motion.current.require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(), 'Owned native frame file required')
    raw=run_pin03.read_capture(path,maximum=MAX_BYTES)
    decode_frames(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy,attributes=attributes)
    with (destination/('native-frame-'+token+'.json')).open('xb') as f:f.write(raw)
    return report # Preserve the existing native companion receipt/schema unchanged.


def accept_native_export(out,state):
    renderer=renderer_for_profile(PROFILE,SOURCE,motion.pair.RUNTIME,motion.pair.DEVICE)
    motion.accept_motion_export(out,state,profile=PROFILE,renderer=renderer,
                               result_verifier=verify_results,cases=CASES,result=RESULT)
    row=state['arms'][0];dest=out/'compose';captures=list(dest.glob('capture-*.json'))
    motion.current.require(len(list(dest.glob('native-frame-*.json')))==len(captures), 'Exactly one frame file per genuine failure')
    for p in captures:
        legacy=json.loads(frozen_sources._read(p,pin02_capture.MAX_BYTES));token=legacy['token']
        attributes=json.loads(frozen_sources._read(dest/('native-accessibility-'+token+'.json'),native.MAX_BYTES))
        native.decode_native(json.dumps(attributes).encode(),token=token,simulator=row['simulator'],
                             bundle=state['debug_bundle'],legacy=legacy)
        decode_frames(frozen_sources._read(dest/('native-frame-'+token+'.json'),MAX_BYTES),token=token,
                      simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy,attributes=attributes)
    return RESULT
