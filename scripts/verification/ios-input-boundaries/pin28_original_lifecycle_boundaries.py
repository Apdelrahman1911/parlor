"""Original EN/AR journeys with working attachment and missing lifecycle evidence.

No sentinel, successful control replay, repaired input or application qualification.
Original native lifecycle notifications and failure-only owned-container status
distinguish interruptions from XCTest observation; absence is not causality.
"""
import hashlib
import json
import math

import frozen_sources
import pin01_copy
import pin02_capture
import pin27_attached_input_delivery as attached
import run_pin03

delivery = attached.delivery
text, motion, frame = delivery.text, delivery.text.motion, delivery.text.frame
SOURCE, TREE, ARMS = attached.SOURCE, attached.TREE, attached.ARMS
PROFILE = 'original-input-lifecycle-boundaries'
CASES = tuple(t.removeprefix('iosAppUITests/')+'()' for t in run_pin03.TESTS)
RESULT = 'ORIGINAL_INPUT_LIFECYCLE_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original EN/AR methods once with verified attachment-time native observation and original '
         'public lifecycle notifications; no sentinel replay, extra taps/typing, timeout change, '
         'shipping correction, causality or device qualification')
TEMPLATE = 'PIN28Lifecycle.swift.in'
TEMPLATE_SHA256 = '3390f3792e96530b715446e0079cecca3700d593b5b9143dc7bd75d10c529e53'
MAX_BYTES = 65_536
PROPERTY = '    private var pin28Lifecycle: PIN28Lifecycle?\n'
INSTALL = '''        let lifecycle = PIN28Lifecycle(armed: { MainViewControllerKt.PIN11PublicAXArmed() }, state: { [weak self] in
            let window = self?.contentController.viewIfLoaded?.window
            return [UIApplication.shared.applicationState.rawValue,
                    window?.windowScene?.activationState.rawValue ?? -2, window == nil ? 0 : 1]
        })
        _ = lifecycle.install()
        pin28Lifecycle = lifecycle
'''
EXPORT = '            self?.pin28Lifecycle?.exportAfterFailureOnce()\n'


def template():
    raw=frozen_sources._read(motion.pair.HERE/TEMPLATE,MAX_BYTES)
    text.require(hashlib.sha256(raw).hexdigest()==TEMPLATE_SHA256,'Exact reviewed lifecycle observer required')
    return raw.decode()


def renderer_for_profile(profile,source,runtime,device):
    text.require((profile,source,runtime,device)==(PROFILE,SOURCE,motion.pair.RUNTIME,motion.pair.DEVICE),
                 'Exact original lifecycle source/profile/runtime/device required')
    base=attached.renderer_for_profile(attached.PROFILE,source,runtime,device)
    def render(sources,digests,*,arm):
        plan=base(sources,digests,arm=arm)
        container=pin01_copy.once(plan[pin02_capture.CONTAINER],attached.PROPERTY,attached.PROPERTY+PROPERTY)
        container=pin01_copy.once(container,'        let rootObservation = PIN10RootObserver(',
                                 INSTALL+'        let rootObservation = PIN10RootObserver(')
        plan[pin02_capture.CONTAINER]=pin01_copy.once(container,attached.EXPORT,attached.EXPORT+EXPORT)+'\n'+template()
        return plan
    return render


# Exactly the original two methods: no synthetic control or iteration.
test_command = run_pin03.test_command
verify_results = motion.current.verify_results
failed_method_exports = run_pin03.failed_method_exports
retain_root_companion = attached.retain_root_companion
retain_motion_companion = attached.retain_motion_companion


def decode_lifecycle(raw,*,token,simulator,bundle,legacy):
    text.require(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'Bounded original lifecycle companion required')
    v=json.loads(raw,object_pairs_hook=text.pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite lifecycle field')))
    text.require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs',
                 'installed','overflow','rows','state'},'No notification contents, objects, labels or private fields')
    text.require(type(v['schema']) is int and v['schema']==1 and type(v['process']) is int and v['process']>0 and
        (v['token'],v['simulator'],v['bundle'],v['process'])==(token,simulator,bundle,legacy['process']),
        'Exact original lifecycle process/product/token/device required')
    text.require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0<=v['capturedMs']<=legacy['capturedMs'] and type(v['installed']) is bool and
        type(v['overflow']) is int and 0<=v['overflow']<=1000000,'Bounded original lifecycle installation/clock required')
    state=v['state']
    text.require(type(state) is list and len(state)==3 and all(type(n) is int for n in state) and
        state[0] in range(3) and state[1] in range(-2,3) and state[2] in (0,1),'Only owned application/scene/window status')
    rows=v['rows'];previous=0
    text.require(type(rows) is list and len(rows)<=128,'Bounded original lifecycle rows required')
    for row in rows:
        text.require(type(row) is list and len(row)==2 and all(type(n) in (int,float) and math.isfinite(n) for n in row) and
            previous<=row[0]<=v['capturedMs'] and row[1] in range(1,9),'Original ordered clock/event categories only')
        previous=row[0]
    return dict(sha256=hashlib.sha256(raw).hexdigest(),complete=v['installed'] and v['overflow']==0,rows=len(rows))


def decode_text_observation(raw,*,token,simulator,bundle,legacy):
    # A pre-catalog/pre-Name failure legitimately has no Name owner. Preserve
    # those ORIGINAL zero rows, never add a fabricated callback for validation.
    text.require(type(raw) is bytes and 0<len(raw)<=text.MAX_BYTES,'Bounded original public text evidence required')
    v=json.loads(raw,object_pairs_hook=text.pin01_receipts.reject_duplicates,
        parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite text field')))
    text.require(type(v) is dict and set(v)=={'schema','token','simulator','bundle','process','capturedMs','text','syntheticControl'} and
        type(v['schema']) is int and v['schema']==1 and type(v['process']) is int and v['process']>0 and
        (v['token'],v['simulator'],v['bundle'],v['process'])==(token,simulator,bundle,legacy['process']) and
        type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and 0<=v['capturedMs']<=legacy['capturedMs'] and
        v['syntheticControl'] is False,'Exact original unaltered public text source/process/clock required')
    snapshot=v['text']
    text.require(type(snapshot) is dict and set(snapshot)=={'schema','nativeMode','overflow','invalid','rows'},
                 'Exact private-free original Name snapshot schema required')
    if snapshot['rows']:
        return text.decode_text(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    text.require(all(type(snapshot[k]) is int and snapshot[k]==n for k,n in
        dict(schema=5,nativeMode=1,overflow=0,invalid=0).items()) and snapshot['rows']==[],
        'Only actual zero-row unentered Name scope allowed; prior nonempty controls unchanged')
    return dict(sha256=hashlib.sha256(raw).hexdigest(),complete=True,rows=0,scope='No Name input rows observed; not delivery/lifetime causality')


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    report=frame.retain_companion(sandbox,destination,token,simulator,bundle,legacy)
    specifications=(('parlor-pin23-','text.json','public-text-',motion.MAX_BYTES,decode_text_observation),
        ('parlor-pin25-','delivery.json','native-input-',delivery.MAX_BYTES,delivery.validate_delivery),
        ('parlor-pin27-','attachment.json','input-attachment-',attached.MAX_BYTES,attached.validate_attachment),
        ('parlor-pin28-','lifecycle.json','public-lifecycle-',MAX_BYTES,decode_lifecycle))
    for directory,name,prefix,limit,decoder in specifications:
        path=sandbox/'tmp'/(directory+token)/name
        text.require(not path.is_symlink() and path.resolve(strict=True)==path.absolute(),'Owned original companion required')
        raw=run_pin03.read_capture(path,maximum=limit)
        decoder(raw,token=token,simulator=simulator,bundle=bundle,legacy=legacy)
        with (destination/(prefix+token+'.json')).open('xb') as f:f.write(raw)
    return report


def accept_native_export(out,state):
    renderer=renderer_for_profile(PROFILE,SOURCE,motion.pair.RUNTIME,motion.pair.DEVICE)
    text.require(state['original_inputs'].get(text.NAME)==text.NAME_SHA256,'Exact original public Name source required')
    motion.boundary.accept_observation_export(out,state,profile=PROFILE,renderer=renderer)
    motion.root.accept_root_companions(out,state)
    row=state['arms'][0];dest=out/'compose';captures=sorted(dest.glob('capture-*.json'));decoded_motion=[]
    commands=[c for c in state['commands'] if c.get('label')=='compose-tests']
    text.require(len(commands)==1 and commands[0].get('timeout_seconds')==900 and
        [a for a in commands[0].get('command',[]) if a.startswith('-only-testing:')]==
        ['-only-testing:'+t for t in run_pin03.TESTS] and
        not any(a.startswith(('-skip-testing','-test-iterations','-retry-tests')) for a in commands[0]['command']),
        'One original EN/AR command and unchanged deadline; no replayed sentinel or retries')
    for p in captures:
        legacy=json.loads(frozen_sources._read(p,pin02_capture.MAX_BYTES));token=legacy['token']
        args=dict(token=token,simulator=row['simulator'],bundle=state['debug_bundle'],legacy=legacy)
        attributes=json.loads(frozen_sources._read(dest/('native-accessibility-'+token+'.json'),frame.native.MAX_BYTES))
        frame.decode_frames(frozen_sources._read(dest/('native-frame-'+token+'.json'),frame.MAX_BYTES),attributes=attributes,**args)
        m=motion.decode_motion(frozen_sources._read(dest/('catalog-motion-'+token+'.json'),motion.MAX_BYTES),**args)
        text.require(m['synthetic_control'] is False,'No synthetic Host suppression in original journeys');decoded_motion.append(m)
        public=frozen_sources._read(dest/('public-text-'+token+'.json'),text.MAX_BYTES);decode_text_observation(public,**args)
        native=frozen_sources._read(dest/('native-input-'+token+'.json'),delivery.MAX_BYTES)
        native_report=delivery.validate_delivery(native,**args)
        attachment=frozen_sources._read(dest/('input-attachment-'+token+'.json'),attached.MAX_BYTES)
        a=attached.validate_attachment(attachment,**args);v=json.loads(attachment)
        text.require(v['installed'] and v['installation']==[1,1,1] and v['overflow']==0,'Actual supplied container hook required')
        if v['rows']:
            text.require(a['complete'] and native_report['complete'],'Actual attachment requires complete native installation')
            text.require(all(r[0]>=v['rows'][0][0] for r in json.loads(native)['rows']),'Native calls must follow actual installation')
        else:
            text.require(json.loads(native)['installation']==[0,0,0] and not native_report['complete'] and native_report['rows']==0 and
                not any(r[1] in (1,3,4) for r in json.loads(public)['text']['rows']),
                'No public input attachment is not evidence of absent delivery; no input callbacks may be asserted')
        lifecycle=decode_lifecycle(frozen_sources._read(dest/('public-lifecycle-'+token+'.json'),MAX_BYTES),**args)
        text.require(lifecycle['complete'],'Complete original lifecycle observer required')
    text.require(row.get('motion_companion_captures',[])==decoded_motion,'Actual original motion receipts required')
    for prefix in ('public-text-','native-frame-','catalog-motion-','native-input-','input-attachment-','public-lifecycle-'):
        text.require(len(list(dest.glob(prefix+'*.json')))==len(captures),'One original companion per failed method; none on success')
    return RESULT
