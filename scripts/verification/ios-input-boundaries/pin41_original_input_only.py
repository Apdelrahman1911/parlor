"""Remove AX/frame/root interception from the original native-only samples.

This is a lower-intervention observation, not a claim that the removed hooks
caused or masked natural failures. Original touch template/ABI and failure-only
driver ownership stay exact. No successful cohort or sentinel is blindly retried.
"""
import hashlib
import json
import math
from pathlib import Path

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin40_native_only_samples as samples

native = samples.native
SOURCE, TREE = native.SOURCE, native.TREE
PROFILE = 'original-input-only-samples'
ARMS = samples.ARMS
CASES, TESTS = samples.CASES, samples.TESTS
TEST_TIMEOUT_SECONDS = samples.TEST_TIMEOUT_SECONDS
RESULT = 'ORIGINAL_INPUT_ONLY_SAMPLES_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Original finite AR/EN samples with only existing original recognizer interception; '
         'no AX/frame/root getter hooks, global class inventory, Kotlin/Compose changes, retries or deadline changes; '
         'all failures retained, not causality, timing neutrality, shipping qualification or physical acceptance')
HERE = Path(__file__).resolve().parent
TEMPLATE = 'PIN41OriginalInputOnly.swift.in'
TEMPLATE_SHA256 = 'a602d1d8d90f65c7f829401b892ca1f9e9b7c87ddfcd4bd517b40a37c9fc621d'
require = native.require


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE,SOURCE,native.pair.RUNTIME,native.pair.DEVICE),
            'Exact original input-only profile/source/runtime/device')
    baseline = native.renderer_for_profile(native.PROFILE,source,runtime,device)
    def render(sources, digests, *, arm):
        # Reuse exact original binding/Swift input validation, not its observer installation.
        previous = baseline(sources,digests,arm=arm)
        raw = frozen_sources._read(HERE/TEMPLATE, frozen_sources.MAX_SOURCE_BYTES)
        require(hashlib.sha256(raw).hexdigest() == TEMPLATE_SHA256, 'Exact reviewed original-input owner')
        templates = native.templates()
        container = pin01_copy.once(sources[pin02_capture.CONTAINER],
            '    private let contentController: UIViewController\n',
            '    private let contentController: UIViewController\n#if DEBUG && targetEnvironment(simulator)\n'
            '    private var pin41Input: PIN41OriginalInputOnlyObservation?\n#endif\n')
        container = pin01_copy.once(container, '        contentController.didMove(toParent: self)\n',
            '        contentController.didMove(toParent: self)\n#if DEBUG && targetEnvironment(simulator)\n'
            '        pin41Input = PIN41OriginalInputOnlyObservation()\n#endif\n')
        container = pin01_copy.once(container, '    override var childForStatusBarStyle:',
            '#if DEBUG && targetEnvironment(simulator)\n'
            '    override func viewDidAppear(_ animated: Bool) {\n'
            '        super.viewDidAppear(animated)\n'
            '        pin41Input?.bindOriginalInputOnce(view: contentController.view!)\n'
            '    }\n#endif\n\n    override var childForStatusBarStyle:')
        container += '\n'+'\n'.join((templates['PIN02Capture.swift.in'],templates[native.touch.TEMPLATE],raw.decode()))
        driver = pin01_copy.once(previous[pin02_capture.DRIVER],native.SENTINEL+'\n','')
        driver = samples.extend_driver(driver)
        driver = driver.replace('PIN39Configure','PIN41Configure').replace('PARLOR_PIN39_','PARLOR_PIN41_')
        driver = pin01_copy.once(driver,'isolated-native-only-export',PROFILE)
        return sources | {pin02_capture.CONTAINER:container,pin02_capture.DRIVER:driver}
    return render


test_command = samples.test_command
verify_results = samples.verify_results
failed_method_exports = samples.failed_method_exports
verify_commands = samples.verify_commands


def decode_status(raw, *, token, simulator, bundle, legacy):
    v = json.loads(raw, object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite input-only status')))
    require(type(v) is dict and set(v) == {'schema','token','simulator','bundle','process','capturedMs',
        'viewCount','eligibleInputViews','treeComplete','inputBindingAttempted','touchInstalled',
        'accessibilityGetterHooks','frameGetterHooks','rootGetterHooks','classListEnumeration',
        'kotlinInstrumentation','composePointerObservers','addedRecognizer','primaryEnvelopePurpose'},
        'Exact input-only status fields; no invented AX/frames/root or private evidence')
    require(type(v['schema']) is int and v['schema'] == 1 and type(v['process']) is int and
        (v['token'],v['simulator'],v['bundle'],v['process']) == (token,simulator,bundle,legacy['process']), 'Exact status owner')
    require(type(v['capturedMs']) in (int,float) and math.isfinite(v['capturedMs']) and
        0 <= v['capturedMs'] <= legacy['capturedMs'], 'Actual input-only failure clock')
    require(type(v['viewCount']) is int and 1 <= v['viewCount'] <= 256 and type(v['eligibleInputViews']) is int and
        v['eligibleInputViews'] == 1 and all(v[k] is True for k in ('treeComplete','inputBindingAttempted','touchInstalled')),
        'Complete once-bound original input tree/recognizer required')
    require(all(v[k] is False for k in ('accessibilityGetterHooks','frameGetterHooks','rootGetterHooks','classListEnumeration',
        'kotlinInstrumentation','composePointerObservers','addedRecognizer')) and
        v['primaryEnvelopePurpose'] == 'durable-failure-ack-only', 'No fabricated absent native/Kotlin observations')
    return v


def decode_companions(destination, token, simulator, bundle, legacy):
    args = dict(token=token,simulator=simulator,bundle=bundle,legacy=legacy)
    raw = frozen_sources._read(destination/('native-touch-'+token+'.json'),native.touch.decoder.MAX_BYTES)
    touches = native.touch.decoder.decode(raw,**args)
    require(touches['complete'], 'Actual original recognizer/exact ABI/complete ledger; zero calls alone cannot admit')
    status = decode_status(frozen_sources._read(destination/('native-only-'+token+'.json'),native.MAX_BYTES),**args)
    return dict(touches=touches,status=status)


def retain_companion(sandbox,destination,token,simulator,bundle,legacy):
    missing = []
    for prefix,filename,retained,maximum in (('parlor-pin41-','status.json','native-only-',native.MAX_BYTES),
        ('parlor-pin33-','touches.json','native-touch-',native.touch.decoder.MAX_BYTES)):
        path = sandbox/'tmp'/(prefix+token)/filename
        if not path.exists() and not path.is_symlink():
            missing.append(retained);continue
        require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Exact original input-only companion')
        with (destination/(retained+token+'.json')).open('xb') as f:
            f.write(samples.run_pin03.read_capture(path,maximum=maximum))
    require(not missing, 'Missing input-only companions after retaining all available files: '+','.join(missing))
    return decode_companions(destination,token,simulator,bundle,legacy)


def accept_native_export(out,state):
    d = out/'compose'
    require(not any(d.glob('native-accessibility-*.json')) and not any(d.glob('native-frame-*.json')) and
            not any(d.glob('native-root-*.json')), 'No invented absent AX/frame/root captures')
    return samples._accept_sampled_export(out,state,profile=PROFILE,renderer=renderer_for_profile,
        decoder=decode_companions,prefixes=('capture-','native-touch-','native-only-'),
        result=RESULT,filename='original-input-only-samples.json')
