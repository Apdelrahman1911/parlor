"""Finite original EN/AR journeys with the accepted native-only observer.

Only copied Swift wrapper/driver inputs change. Original application Kotlin,
queries, gestures, assertions and deadlines stay byte-identical. A real failed
sample remains failed; installation completeness is not inferred from calls.
"""
import hashlib
import json
import re

import frozen_sources
import pin01_copy
import pin01_receipts
import pin02_capture
import pin11_public_failure_coverage as failure
import pin30_public_session_stress as samples
import pin39_native_only_export as native
import run_pin03

SOURCE, TREE = native.SOURCE, native.TREE
PROFILE = 'fixed-native-only-public-samples'
ARMS = ('compose',)  # Runner label, not Kotlin/Compose instrumentation.
CASES, TESTS = samples.CASES, samples.TESTS
TEST_TIMEOUT_SECONDS = samples.TEST_TIMEOUT_SECONDS
RESULT = 'FIXED_NATIVE_ONLY_PUBLIC_SAMPLES_COMPLETE_NOT_QUALIFICATION'
SCOPE = ('Five predeclared AR/EN pairs using original public methods/actions/assertions/deadlines; '
         'accepted native-only templates, no Kotlin/Compose instrumentation; every failure retained; '
         'not causality, native timing neutrality, shipping qualification or physical acceptance')
require = native.require


def extend_driver(driver):
    # Apply only the verified pure-Swift ownership portion, never failure.extend:
    # that older transformation also changes Kotlin and Compose observers.
    start, end = driver.index(failure.METHOD), driver.index(failure.NEXT_METHOD)
    method = pin01_copy.once(driver[start:end], failure.OLD_DEFER, failure.NEW_DEFER)
    method = pin01_copy.once(method, failure.LAUNCH, '            PIN39Configure(app)\n' + failure.OWNED_LAUNCH)
    return samples.extend_driver(driver[:start] + failure.HELPER + method + driver[end:])


def restore_driver(driver):
    driver = samples.restore_driver(driver)
    driver = pin01_copy.once(driver, failure.HELPER, '')
    driver = pin01_copy.once(driver, failure.NEW_DEFER, failure.OLD_DEFER)
    return pin01_copy.once(driver, '            PIN39Configure(app)\n' + failure.OWNED_LAUNCH, failure.LAUNCH)


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, native.pair.RUNTIME, native.pair.DEVICE),
            'Exact original finite native-only source/profile/runtime/device')
    baseline = native.renderer_for_profile(native.PROFILE, source, runtime, device)
    def render(sources, digests, *, arm):
        plan = baseline(sources, digests, arm=arm)
        driver = pin01_copy.once(plan[pin02_capture.DRIVER], native.SENTINEL+'\n', '')
        plan[pin02_capture.DRIVER] = extend_driver(driver)
        return plan
    return render


test_command = samples.test_command
verify_results = samples.verify_results
failed_method_exports = samples.failed_method_exports
verify_commands = samples.verify_commands


def decode_companions(destination, token, simulator, bundle, legacy):
    args = dict(token=token, simulator=simulator, bundle=bundle, legacy=legacy)
    def read(prefix, maximum):
        return frozen_sources._read(destination/(prefix+token+'.json'), maximum)
    raw = read('native-accessibility-', native.attributes.MAX_BYTES)
    attributes = native.attributes.decode_native(raw, **args)
    frames = native.frames.decode_frames(read('native-frame-', native.frames.MAX_BYTES), attributes=json.loads(raw), **args)
    root = native.root.decode_root(read('native-root-', native.root.MAX_BYTES), **args)
    touches = native.touch.decoder.decode(read('native-touch-', native.touch.decoder.MAX_BYTES), **args)
    require(touches['complete'], 'Actual unique recognizer/exact ABI/complete buffer required even before a tap')
    status = native.decode_status(read('native-only-', native.MAX_BYTES), **args)
    # Natural failures may precede the first admitted tap. Zero calls are valid
    # ONLY with complete actual installation. The isolated sentinel retains its
    # separate positive catalog/Host call requirements, unchanged.
    return dict(attributes=attributes, frames=frames, root=root, touches=touches, status=status)


def retain_companion(sandbox, destination, token, simulator, bundle, legacy):
    specs = (('parlor-pin39-', 'status.json', 'native-only-', native.MAX_BYTES),
        ('parlor-pin08-', 'accessibility.json', 'native-accessibility-', native.attributes.MAX_BYTES),
        ('parlor-pin22-', 'frames.json', 'native-frame-', native.frames.MAX_BYTES),
        ('parlor-pin10-', 'root.json', 'native-root-', native.root.MAX_BYTES),
        ('parlor-pin33-', 'touches.json', 'native-touch-', native.touch.decoder.MAX_BYTES))
    missing = []
    for prefix, filename, retained, maximum in specs:
        path = sandbox/'tmp'/(prefix+token)/filename
        if not path.exists() and not path.is_symlink():
            missing.append(retained.removesuffix('-')); continue
        require(not path.is_symlink() and path.resolve(strict=True) == path.absolute(), 'Exact owned native-only companion')
        with (destination/(retained+token+'.json')).open('xb') as f:
            f.write(run_pin03.read_capture(path, maximum=maximum))
    require(not missing, 'Missing native-only companions after retaining available files: '+','.join(missing))
    return decode_companions(destination, token, simulator, bundle, legacy)


def load(path):
    return json.loads(frozen_sources._read(path, 2_097_152), object_pairs_hook=pin01_receipts.reject_duplicates,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def requests_by_method(destination, methods):
    failed = sorted(k for k,v in methods.items() if v == 'Failed')
    directories = list((destination/'attachments').glob('failed-*'))
    require({p.name for p in directories} == {'failed-%02d'%i for i in range(1,len(failed)+1)},
            'Exactly the actual failed-method attachment directories')
    result = {}
    for index, method in enumerate(failed,1):
        found = []
        for p in (destination/'attachments'/('failed-%02d'%index)).rglob('*'):
            if p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= 4096:
                try: v = load(p)
                except (ValueError, UnicodeError): continue
                if type(v) is dict and 'originalTransitionFailed' in v: found.append(v)
        require(len(found) == 1 and set(found[0]) == {'schema','token','ackObserved','originalTransitionFailed'} and
            type(found[0]['schema']) is int and found[0]['schema'] == 1 and found[0]['ackObserved'] is True and
            found[0]['originalTransitionFailed'] is True, 'Exactly one actual acknowledged post-failure request per failed sample')
        token = found[0]['token']
        require(type(token) is str and pin02_capture.UUID.fullmatch(token) and token not in result,
                'Distinct exact failed-sample token; no duplicate requests')
        result[token] = method
    return result


def accept_native_export(out, state):
    require((state['profile'],state['source'],state['source_tree'],state['runtime'],state['device'],
        state['debug_bundle'],state['xcode'],state['sdk']) ==
        (PROFILE,SOURCE,TREE,native.pair.RUNTIME,native.pair.DEVICE,'me.parlor.ios.debug',
         'Xcode 26.3\nBuild version 17C529\n','26.2') and len(state['arms']) == 1, 'Exact original native-only identity')
    row = state['arms'][0]; d = out/'compose'
    require(row['arm'] == 'compose' and row['build_exit'] == 0 and row['cleanup'] == 'PASS' and row['result'] in
            ('JOURNEYS_PASSED','JOURNEYS_FAILED'), 'Actual build/outcomes/cleanup, not cancelled/incomplete execution')
    methods = verify_results(load(d/'summary.json'),load(d/'tests.json'),row['simulator'],row['test_exit'])
    require(methods == row['methods'] and row.get('attachment_test_ids',[]) == sorted(k for k,v in methods.items() if v == 'Failed'),
            'All ten exact original outcomes and actual failed-method attachment ownership')
    require(all(state['original_inputs'].get(p) == h for p,h in native.pair.INPUTS.items()), 'All exact original binding bytes')
    plan = renderer_for_profile(PROFILE,SOURCE,native.pair.RUNTIME,native.pair.DEVICE)(
        {p:native.pair.fixture(p) for p in native.pair.MODIFIED},native.pair.INPUTS,arm='compose')
    require(load(d/'inputs.json') == state['original_inputs'] | {p:hashlib.sha256(v.encode()).hexdigest() for p,v in plan.items()},
            'All original inputs plus only reviewed copied Swift wrapper/driver')
    product = frozen_sources._read(d/'product.json', 2_097_152)
    require(load(d/'product.json') and hashlib.sha256(product).hexdigest() == row['product_manifest_sha256'], 'Actual built product custody')
    owners = requests_by_method(d, methods)
    captures, reports = [], []
    for token in sorted(owners):
        raw = frozen_sources._read(d/('capture-'+token+'.json'), pin02_capture.MAX_BYTES)
        decoded = run_pin03.decode_capture(raw,set(owners),row['simulator'],state['debug_bundle'])
        require(decoded['snapshot'] == dict(schema=1,nativeMode=0,overflow=0,invalid=0,rows=[]) and
                decoded['numeric_buffer_complete'] is False, 'ACK-only envelope, never invented Kotlin observations')
        captures.append(decoded)
        reports.append(decode_companions(d,token,row['simulator'],state['debug_bundle'],json.loads(raw)))
    require(row.get('captures',[]) == captures and row.get('companion_captures',[]) == reports and
        not row.get('root_companion_captures') and not row.get('motion_companion_captures'), 'All exact native-only captures')
    for prefix in ('capture-','native-accessibility-','native-frame-','native-root-','native-touch-','native-only-'):
        require({p.name for p in d.glob(prefix+'*.json')} == {prefix+t+'.json' for t in owners},
                'One exact companion per actual failure, zero exports on all-pass execution')
    cleanup = load(d/'cleanup.json')
    require(cleanup['gradle_exit'] == 0 and cleanup['workers_before']['result'] == cleanup['workers_after']['result'] ==
        cleanup['simulator']['result'] == 'PASS' and cleanup['simulator']['udid'] == row['simulator'], 'Owned workers/device retired')
    require(state['commands'] and all(c['status'] == 'EXITED' and c['direct_child_reaped'] and not c.get('signals')
        for c in state['commands']), 'Every child actually exited and reaped')
    verify_commands(state,row)
    require(state['build_profile']['test_timeout_seconds'] == TEST_TIMEOUT_SECONDS, 'Original finite per-pair process budget')
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.",
        frozen_sources._read(d/'test.log', 4*1024*1024))
    require(tuple(c.decode()+'/'+m.decode()+'()' for c,m in starts) == CASES, 'All predeclared original AR/EN samples once in order')
    receipt = dict(methods=methods,failed_sample_tokens=owners,native=reports,planned_pairs=5,maximum_public_flows=60,
        actual_completed_flows=60 if row['test_exit'] == 0 else None,all_failed_samples_retained=True,
        primary_envelope_purpose='durable-failure-ack-only',application_root_cause=False,shipping_qualified=False,
        native_timing_neutrality=False,physical_verified=False)
    target = out/'native-only-samples.json'
    if target.exists(): require(load(target) == receipt, 'Exact saved sampled observations')
    else:
        with target.open('x') as f: json.dump(receipt,f,indent=2)
    return RESULT
