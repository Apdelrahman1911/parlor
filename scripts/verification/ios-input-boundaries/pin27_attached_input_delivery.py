"""Install the native observer at actual public input attachment, not startup.

PIN26 proves zero eligible startup classes. Use the source-provided LocalUIView
and original didAddSubview delivery to bind the exact Compose container leaf.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin02_capture
import pin26_delivery_export_control as isolated
import run_pin03

delivery = isolated.delivery
SOURCE, TREE, ARMS, CASES = isolated.SOURCE, isolated.TREE, isolated.ARMS, isolated.CASES
PROFILE = 'isolated-attached-native-input-delivery'
RESULT = 'ISOLATED_ATTACHED_NATIVE_INPUT_DELIVERY_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Only original sentinel with source-provided Compose input container and original public input attachment; '
         'no startup registration assumption, polling, input repair, passing journey replay or shipping qualification')
KOTLIN = 'composeApp/src/iosMain/kotlin/com/parlor/app/inputcopy/PIN27InputContainerBinding.kt'
TEMPLATES = {
    'PIN27InputContainer.kt.in': '9d4e2790adf89893820f11da221e26db3e9401509c5be92ecddb21c49fa6a39b',
    'PIN27InputAttachment.swift.in': '4892fff80f78157fc4e75a79bbf00aa562a26d8da620afcf58aae05fb0a30a1c',
}
MAX_BYTES = 65_536
BRIDGE = '''
interface PIN27InputContainerObserver {
    fun observeInputContainer(view: platform.UIKit.UIView)
}
fun PIN27SetInputContainerObserver(observer: PIN27InputContainerObserver): Boolean {
    check(platform.Foundation.NSThread.isMainThread)
    return com.parlor.app.inputcopy.PIN27InputContainerBinding.register(observer)
}
'''
PROPERTY = '    private var pin27Attachment: PIN27InputAttachment?\n'
INSTALL = '''        let delivery = PIN25NativeInputDelivery(armed: { MainViewControllerKt.PIN25PublicInputArmed() })
        pin25Delivery = delivery
        let attachment = PIN27InputAttachment(
            armed: { MainViewControllerKt.PIN25PublicInputArmed() },
            installDelivery: { [weak delivery] in delivery?.install() ?? false })
        pin27Attachment = attachment
        _ = MainViewControllerKt.PIN27SetInputContainerObserver(observer: PIN27ContainerAdapter(attachment))
'''
EXPORT = '            self?.pin27Attachment?.exportAfterFailureOnce()\n'
ADAPTER = '''
#if DEBUG && targetEnvironment(simulator)
private final class PIN27ContainerAdapter: NSObject, PIN27InputContainerObserver {
    private let attachment: PIN27InputAttachment
    init(_ attachment: PIN27InputAttachment) { self.attachment = attachment; super.init() }
    func observeInputContainer(view: UIView) { _ = attachment.bindInputContainer(view) }
}
#endif
'''


def template(name):
    raw=frozen_sources._read(delivery.text.motion.pair.HERE/name,MAX_BYTES)
    delivery.text.require(hashlib.sha256(raw).hexdigest()==TEMPLATES[name],'Exact reviewed attachment source required')
    return raw.decode()


def renderer_for_profile(profile,source,runtime,device):
    delivery.text.require((profile,source,runtime,device)==(PROFILE,SOURCE,delivery.text.motion.pair.RUNTIME,
        delivery.text.motion.pair.DEVICE),'Exact attached input source/profile/runtime/device required')
    base=isolated.renderer_for_profile(isolated.PROFILE,source,runtime,device)
    def render(sources,digests,*,arm):
        plan=base(sources,digests,arm=arm)
        main=pin01_copy.once(plan[pin01_copy.MAIN],
            '    com.parlor.app.inputcopy.PIN05ObserveInput { App() }\n',
            '    com.parlor.app.inputcopy.PIN05ObserveInput { com.parlor.app.inputcopy.PIN27InputContainerBinding.Observe { App() } }\n')
        plan[pin01_copy.MAIN]=main+BRIDGE
        container=pin01_copy.once(plan[pin02_capture.CONTAINER],delivery.PROPERTY,delivery.PROPERTY+PROPERTY)
        container=pin01_copy.once(container,delivery.INSTALL,INSTALL)
        container=pin01_copy.once(container,delivery.EXPORT,delivery.EXPORT+EXPORT)
        plan[pin02_capture.CONTAINER]=container+'\n'+template('PIN27InputAttachment.swift.in')+ADAPTER
        plan[KOTLIN]=template('PIN27InputContainer.kt.in')
        return plan
    return render


test_command = isolated.test_command
verify_results = isolated.verify_results
failed_method_exports = isolated.failed_method_exports
retain_root_companion = delivery.retain_root_companion
retain_motion_companion = delivery.retain_motion_companion


def validate_attachment(raw,*,token,simulator,bundle,legacy):
    delivery.text.require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded original attachment evidence required')
    v=json.loads(raw,object_pairs_hook=delivery.text.pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite attachment field')))
    delivery.text.require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs',
        'installed','installation','overflow','rows'},'No input/content/objects/addresses/private fields allowed')
    delivery.text.require(type(v['schema']) is int and v['schema']==1 and type(v['process']) is int and
        v['process']>0 and (v['token'],v['simulator'],v['bundle'],v['process'])==
        (token,simulator,bundle,legacy['process']),'Exact original attachment process/product/token/device required')
    delivery.text.require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0<=v['capturedMs']<=legacy['capturedMs'],'Original attachment capture clock required')
    stages=v['installation']
    delivery.text.require(type(stages) is list and len(stages)==3 and
        all(type(n) is int and n in (0,1) for n in stages) and type(v['installed']) is bool and
        type(v['overflow']) is int and 0<=v['overflow']<=1000000,'Bounded original attachment status only')
    rows=v['rows'];previous=0
    delivery.text.require(type(rows) is list and len(rows)<=128,'Bounded attachment rows required')
    for row in rows:
        delivery.text.require(type(row) is list and len(row)==3 and
            all(type(n) in (int,float) and math.isfinite(n) for n in row),'Three numeric fields only')
        now,phase,flag=row
        delivery.text.require(previous<=now<=v['capturedMs'] and phase in (0,1) and flag in (0,1) and
            (phase==0 or flag==0),'Original attachment clock/phase/delivery bit only')
        previous=now
    complete=(v['installed'] and stages==[1,1,1] and v['overflow']==0 and len(rows)==2 and
        rows[0][1:]==[0,1] and rows[1][1:]==[1,0])
    return dict(sha256=hashlib.sha256(raw).hexdigest(),complete=complete,rows=len(rows))


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    report=delivery.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    path=sandbox/'tmp'/('parlor-pin27-'+token)/'attachment.json'
    delivery.text.require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),
        'Owned original attachment companion required')
    raw=run_pin03.read_capture(path,maximum=MAX_BYTES)
    validate_attachment(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    with (destination/('input-attachment-'+token+'.json')).open('xb') as f:f.write(raw)
    return report


def accept_native_export(out,state):
    renderer=renderer_for_profile(PROFILE,SOURCE,delivery.text.motion.pair.RUNTIME,delivery.text.motion.pair.DEVICE)
    delivery.accept_delivery_export(out,state,profile=PROFILE,renderer=renderer,cases=CASES,result=RESULT)
    dest=out/'compose';captures=list(dest.glob('capture-*.json'))
    delivery.text.require(len(list(dest.glob('input-attachment-*.json')))==len(captures),
        'One original attachment companion per actual failed method required')
    for path in captures:
        legacy=json.loads(frozen_sources._read(path,pin02_capture.MAX_BYTES));token=legacy['token']
        raw=frozen_sources._read(dest/('input-attachment-'+token+'.json'),MAX_BYTES)
        report=validate_attachment(raw,token=token,simulator=state['arms'][0]['simulator'],
            bundle=state['debug_bundle'],legacy=legacy)
        delivery.text.require(report['complete'],'Actual original leaf attachment and successful native installation required')
        attach=json.loads(raw);native=json.loads(frozen_sources._read(dest/('native-input-'+token+'.json'),delivery.MAX_BYTES))
        delivery.text.require(native['rows'] and all(r[0]>=attach['rows'][0][0] for r in native['rows']),
            'Actual original native calls must follow the one successful attachment-time installation')
    return RESULT
