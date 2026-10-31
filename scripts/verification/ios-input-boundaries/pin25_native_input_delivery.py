"""Locate UIKit-to-session input delivery without another successful-journey replay.

New observations justify the unresolved original Arabic journey execution. The
separate sentinel checks actual native delivery and failure-only export. Neither
outcome qualifies a production repair or establishes original causality alone.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin02_capture
import pin23_text_session_boundary as text
import run_pin03

SOURCE, TREE, ARMS = text.SOURCE, text.TREE, text.ARMS
PROFILE = 'public-name-native-input-delivery'
CASES = text.CASES
RESULT = 'PUBLIC_NAME_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original Arabic journey with new original-call-only UIKit input/responder observations and '
         'all existing text/native/root/frame/motion gates; no English replay, input repair, '
         'changed assertions/deadlines, shipping qualification or physical acceptance')
TEMPLATE = 'PIN25NativeInputDelivery.swift.in'
TEMPLATE_SHA256 = 'cdc65db6af08d324bbbf47940a030c67998dce56745bd38d9409f9d690464a5a'
MAX_BYTES, MAX_ROWS = 524288, 4096
BRIDGE = '''
fun PIN25PublicInputArmed(): Boolean = com.parlor.app.inputcopy.PIN05Observation.owner() != null
'''
PROPERTY = '    private var pin25Delivery: PIN25NativeInputDelivery?\n'
INSTALL = '''        let delivery = PIN25NativeInputDelivery(armed: { MainViewControllerKt.PIN25PublicInputArmed() })
        _ = delivery.install()
        pin25Delivery = delivery
'''
EXPORT = '            self?.pin25Delivery?.exportAfterFailureOnce()\n'


def template():
    raw = frozen_sources._read(text.motion.pair.HERE/TEMPLATE, MAX_BYTES)
    text.require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact reviewed UIKit-delivery template required')
    return raw.decode()


def renderer_for_profile(profile, source, runtime, device):
    text.require((profile,source,runtime,device)==(PROFILE,SOURCE,text.motion.pair.RUNTIME,text.motion.pair.DEVICE),
                 'Exact new native-input source/profile/runtime/device required')
    base = text.renderer_for_profile(text.PROFILE,source,runtime,device)
    def render(sources,digests,*,arm):
        plan=base(sources,digests,arm=arm)
        plan[pin01_copy.MAIN] += BRIDGE
        container=pin01_copy.once(plan[pin02_capture.CONTAINER], '    private let pin23Text = PIN23TextInputExport()\n',
                                 '    private let pin23Text = PIN23TextInputExport()\n'+PROPERTY)
        container=pin01_copy.once(container,'        let rootObservation = PIN10RootObserver(',
                                 INSTALL+'        let rootObservation = PIN10RootObserver(')
        plan[pin02_capture.CONTAINER]=pin01_copy.once(container,'            return original\n',EXPORT+'            return original\n')+'\n'+template()
        return plan
    return render


test_command = text.test_command
verify_results = text.verify_results
failed_method_exports = text.failed_method_exports
retain_root_companion = text.retain_root_companion
retain_motion_companion = text.retain_motion_companion


def validate_delivery(raw, *, token, simulator, bundle, legacy):
    """Privacy/shape intake only: retain incomplete installation as failed evidence.

    Qualification below still requires the unique complete actual installation.
    Keeping safe failure evidence must not turn a failed adapter into a pass.
    """
    text.require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES, 'Bounded original delivery companion required')
    v=json.loads(raw,object_pairs_hook=text.pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite native delivery field')))
    text.require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs',
                 'installed','installation','overflow','rows'}, 'No text/ranges/objects/classes/private fields allowed')
    text.require(type(v['schema']) is int and v['schema']==1 and
        (v['token'],v['simulator'],v['bundle'],v['process'])==(token,simulator,bundle,legacy['process']) and
        type(v['process']) is int and v['process']>0, 'Exact original process/product/token/device required')
    text.require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0<=v['capturedMs']<=legacy['capturedMs'], 'Original delivery capture clock required')
    stages=v['installation']
    text.require(type(stages) is list and len(stages)==3 and all(type(n) is int for n in stages) and
        0<=stages[0]<=100000 and 0<=stages[1]<=100000 and 0<=stages[2]<=3 and
        type(v['installed']) is bool and type(v['overflow']) is int and 0<=v['overflow']<=1000000,
        'Only bounded numeric installation and buffer status allowed')
    rows=v['rows']
    text.require(type(rows) is list and len(rows)<=MAX_ROWS,'Bounded original method-call rows required')
    previous=0
    for row in rows:
        text.require(type(row) is list and len(row)==4 and
            all(type(n) in (int,float) and math.isfinite(n) for n in row), 'Four numeric fields only')
        timestamp,kind,phase,flag=row
        text.require(previous<=timestamp<=v['capturedMs'] and kind in range(1,9) and phase in (0,1) and flag in (0,1) and
            (flag==0 or kind in (5,6) and phase==1),'Original ordered call kinds/phases/public return flags only')
        previous=timestamp
    complete=v['installed'] and 1<=stages[0]<=100000 and stages[1:]==[1,3] and v['overflow']==0
    return dict(sha256=hashlib.sha256(raw).hexdigest(),complete=complete,rows=len(rows),
                mutation_entries=sum(r[1] in (1,2,3,4) and r[2]==0 for r in rows),
                press_entries=sum(r[1] in (7,8) and r[2]==0 for r in rows),
                responder_entries=sum(r[1] in (5,6) and r[2]==0 for r in rows),
                scope='Original public-name UIKit calls only; not text, ranges, identity or a cause verdict')


def decode_delivery(raw, *, token, simulator, bundle, legacy):
    report=validate_delivery(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    text.require(report['complete'], 'Unique actual Cocoa/protocol/ABI installation and complete buffer required')
    return report


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    report=text.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    path=sandbox/'tmp'/('parlor-pin25-'+token)/'delivery.json'
    text.require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Owned original native-delivery companion required')
    raw=run_pin03.read_capture(path,maximum=MAX_BYTES)
    validate_delivery(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('native-input-'+token+'.json')).open('xb') as f:f.write(raw)
    return report


def accept_native_export(out,state):
    renderer=renderer_for_profile(PROFILE,SOURCE,text.motion.pair.RUNTIME,text.motion.pair.DEVICE)
    return accept_delivery_export(out,state,profile=PROFILE,renderer=renderer,cases=CASES,result=RESULT)


def accept_delivery_export(out,state,*,profile,renderer,cases,result):
    text.accept_text_export(out,state,profile=profile,renderer=renderer,cases=cases,result=result)
    dest=out/'compose'
    captures=list(dest.glob('capture-*.json'))
    text.require(len(list(dest.glob('native-input-*.json')))==len(captures),'One exact original delivery companion per failed method')
    for p in captures:
        legacy=json.loads(frozen_sources._read(p,pin02_capture.MAX_BYTES));token=legacy['token']
        raw=frozen_sources._read(dest/('native-input-'+token+'.json'),MAX_BYTES)
        decode_delivery(raw,token=token,simulator=state['arms'][0]['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        public=json.loads(frozen_sources._read(dest/('public-text-'+token+'.json'),text.MAX_BYTES))
        if public['syntheticControl']:
            rows=json.loads(raw)['rows']
            text.require(any(r[1] in (1,2,3,7,8) and r[2]==0 for r in rows) and
                any(r[1]==5 and r[2:]==[1,1] for r in rows),
                'Sentinel requires actual UIKit text delivery and successful original focus return, not just a configured hook')
    return result
