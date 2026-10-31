"""Observe the exact fresh failed5029 application; never qualify failed app CI.

No synthetic fault, uninstrumented retry or changed public action is selected.
Only the missing failed-only catalog export owner is added to the observed plans.
"""
import hashlib
import json

import pin01_copy
import pin02_capture
import pin06_public_setup_pair as pair
import pin07_native_adapter as synthetic
import run_pin03

SOURCE = '5029b3f48c2d012b34dd2748f809dc775276a622'
TREE = 'fcf9abf3dba3ee40c4c9aec7966260f8d848ff53'
PROFILE = 'current-failed-public-boundaries'
ARMS = ('compose', 'native')
SCOPE = ('Two observed copies of fresh failed5029 EN/AR journeys; failed-only catalog/Host/permission '
         'capture. No uninstrumented retry, synthetic fault, shipping repair, full neutrality or physical acceptance')
PREFIXES = ('Open Egyptian Dominoes', 'Open Ghamza', 'Open Word Impostor',
            'افتح الدومنة المصرية', 'افتح غمزة', 'افتح لعبة الإمبوستر')
OWNER_CALL = '''                try tapSettledControl(button, pin06FailureOwner:
                    (prefix == "Continue and retry the real local-network operation." ||
                     prefix == "تابع وأعد تنفيذ عملية الشبكة المحلية الفعلية." ||
                     prefix == "Open Egyptian Dominoes" || prefix == "Open Ghamza" ||
                     prefix == "Open Word Impostor" || prefix == "افتح الدومنة المصرية" ||
                     prefix == "افتح غمزة" || prefix == "افتح لعبة الإمبوستر") ? app : nil)'''


def require(condition, message):
    if not condition:
        raise ValueError(message)


def renderer_for_profile(profile, source, runtime, device):
    require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
            'Exact fresh failed source/profile/runtime/device required')
    def render(sources, digests, *, arm):
        require(arm in ARMS, 'Only explicit observed current arms, no original retry')
        output = pair.render(sources, digests, arm=arm)
        output[pin02_capture.DRIVER] = pin01_copy.once(
            output[pin02_capture.DRIVER], pair.OWNER_CALL, OWNER_CALL)
        return output
    return render


test_command = run_pin03.test_command
failed_method_exports = run_pin03.failed_method_exports


def verify_results(summary, tests, simulator_id, code):
    require(type(code) is int and code in (0, 65), 'Actual XCTest pass or original failed exit65 required')
    methods = run_pin03.verify_results(summary, tests, simulator_id, code)
    for device in (summary['devicesAndConfigurations'][0]['device'], tests['devices'][0]):
        require(device.get('modelName') == 'iPhone 16 Pro' and device.get('osVersion') == '18.5',
                'Actual current owned iOS18.5 iPhone16Pro required')
    return methods


def accept_observations(out, state, *, profile, arms, renderer):
    """Shared source/product/result/custody validation, not a qualification gate.

    Entry points still bind their own exact profile, arms and copied-source plan.
    No command, native-mode, failed-only export or cleanup checks are optional.
    """
    require((state['profile'], state['source'], state['source_tree'], state['runtime'], state['device']) ==
            (profile, SOURCE, TREE, pair.RUNTIME, pair.DEVICE), 'Current source/tree/device binding missing')
    require([row['arm'] for row in state['arms']] == list(arms), 'Exact ordered observed copies required')
    require(state['debug_bundle'] == 'me.parlor.ios.debug' and
            state['xcode'] == 'Xcode 26.3\nBuild version 17C529\n' and state['sdk'] == '26.2',
            'Actual compiler/SDK/Debug identity required')
    for path, digest in pair.INPUTS.items():
        require(state['original_inputs'].get(path) == digest, 'Current observed source changed')
    for row in state['arms']:
        arm = row['arm']; dest = out / arm
        require(row['build_exit'] == 0 and row['cleanup'] == 'PASS' and
                row['result'] in ('JOURNEYS_PASSED', 'JOURNEYS_FAILED'), 'Real build/results/cleanup required')
        methods = verify_results(synthetic.load(dest / 'summary.json'), synthetic.load(dest / 'tests.json'),
                                 row['simulator'], row['test_exit'])
        require(row['methods'] == methods, 'Original methods differ from collection')
        plan = renderer({p: pair.fixture(p) for p in pair.MODIFIED}, pair.INPUTS, arm=arm)
        expected = state['original_inputs'] | {p: hashlib.sha256(raw.encode()).hexdigest() for p, raw in plan.items()}
        require(synthetic.load(dest / 'inputs.json') == expected, 'Only the reviewed current observation plan allowed')
        product = synthetic.load(dest / 'product.json')
        require(product and hashlib.sha256((dest / 'product.json').read_bytes()).hexdigest() ==
                row['product_manifest_sha256'], 'Current built product custody missing')
        failures = [method for method, outcome in methods.items() if outcome == 'Failed']
        captures = list(dest.glob('capture-*.json'))
        require(len(captures) == len(row.get('captures', [])) == len(failures),
                'One retained failed-only export per failed method required')
        tokens = set()
        for path in captures:
            value = synthetic.load(path, pin02_capture.MAX_BYTES)
            token = value['token']; require(token not in tokens, 'Duplicate current token')
            tokens.add(token)
            decoded = run_pin03.decode_capture(path.read_bytes(), {token}, row['simulator'], state['debug_bundle'])
            require(decoded in row['captures'] and decoded['numeric_buffer_complete'] and
                    decoded['snapshot']['nativeMode'] == int(arm == 'native'), 'Actual bounded correct-arm buffer required')
        if failures:
            require(row.get('attachment_test_ids') == sorted(failures), 'Exact original failed-method attachments required')
            requests = []
            for path in (dest / 'attachments').rglob('*'):
                if path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= 4096:
                    try:
                        value = synthetic.load(path, 4096)
                    except (ValueError, UnicodeError):
                        continue
                    if type(value) is dict and 'originalTransitionFailed' in value:
                        requests.append(value)
            require(len(requests) == len(failures) and {r.get('token') for r in requests} == tokens and
                    all(set(r) == {'schema', 'token', 'ackObserved', 'originalTransitionFailed'} and
                        type(r['schema']) is int and r['schema'] == 1 and
                        r['ackObserved'] is True and r['originalTransitionFailed'] is True for r in requests),
                    'Actual once-only failed ACK/token attachments required')
        cleanup = synthetic.load(dest / 'cleanup.json')
        require(cleanup.get('gradle_exit') == 0 and cleanup.get('workers_before', {}).get('result') == 'PASS' and
                cleanup.get('workers_after', {}).get('result') == 'PASS' and
                cleanup.get('simulator', {}).get('result') == 'PASS' and
                cleanup['simulator'].get('udid') == row['simulator'], 'Actual owned cleanup required')
        for label, code in ((arm + '-build', 0), (arm + '-tests', row['test_exit']), (arm + '-gradle-stop', 0)):
            selected = [p for p in state['commands'] if p.get('label') == label]
            require(len(selected) == 1 and selected[0].get('exit_code') == code, 'Single original command required')
    require(state['commands'] and all(p.get('status') == 'EXITED' and p.get('direct_child_reaped') and
            not p.get('signals') for p in state['commands']), 'Every current child must exit and be reaped')
    return True


def accept_native_export(out, state):
    accept_observations(out, state, profile=PROFILE, arms=ARMS,
                        renderer=renderer_for_profile(PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE))
    return 'CURRENT_BOUNDARY_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
