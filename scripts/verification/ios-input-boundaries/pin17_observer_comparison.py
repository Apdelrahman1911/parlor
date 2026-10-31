"""Same-source uninstrumented/observed comparison, never shipping qualification.

Previous context probes contained observers in every application. This explicitly
paired experiment checks that omitted counterfactual, once per arm, with all23
original cases and the original uninstrumented KMP prelude. Different outcomes
are evidence for investigation, not proof of causality or permission to retry.
"""
import hashlib
import json

import current_boundary_adapter as current
import frozen_sources
import pin06_public_setup_pair as pair
import pin13_original_cohort as cohort
import pin15_combined_xcode as combined
import pin16_aggregate_context as aggregate

SOURCE, TREE = aggregate.SOURCE, aggregate.TREE
PROFILE = 'native-observer-comparison'
ARMS = ('original', 'compose')
SCOPE = ('One exact uninstrumented/observed23-case pair after original774-method KMP workload; '
         'same original actions/selectors/assertions/deadlines, not blind qualification replay, '
         'full60-journey neutrality, original causality, shipping repair or physical acceptance')
RESULT = 'PAIRED_OBSERVER_CONTEXT_COMPLETE_NOT_QUALIFICATION'


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact paired original source and runtime required')
    observed = aggregate.renderer_for_profile(aggregate.PROFILE, source, runtime, device)

    def render(sources, digests, *, arm):
        current.require(arm in ARMS, 'Only the declared original/observed pair required')
        if arm == 'original':
            # pair.render validates every frozen source hash even for this arm.
            return pair.render(sources, digests, arm='original')
        return observed(sources, digests, arm=arm)
    return render


build_environment = aggregate.build_environment
observe_resources = aggregate.observe_resources
test_command = aggregate.test_command
verify_results = aggregate.verify_results
failed_method_exports = aggregate.failed_method_exports
retain_companion = aggregate.retain_companion
retain_root_companion = aggregate.retain_root_companion
execute_prelude = aggregate.execute_prelude


def read(out, name):
    return json.loads(frozen_sources._read(out / name, 2*1024*1024))


def accept_original(out, state, row):
    """Uninstrumented failures retain their actual xcresult, never a fake ACK."""
    dest = out / 'original'
    current.require(row['arm'] == 'original' and row['cleanup'] == 'PASS' and row['build_exit'] == 0 and
                    row['result'] in ('JOURNEYS_PASSED', 'JOURNEYS_FAILED'), 'Complete original command required')
    methods = cohort.verify_results(read(dest, 'summary.json'), read(dest, 'tests.json'),
                                    row['simulator'], row['test_exit'])
    current.require(methods == row['methods'], 'Exact original public outcomes required')
    current.require(read(dest, 'inputs.json') == state['original_inputs'],
                    'Uninstrumented arm must contain only complete original source bytes')
    current.require(not any(dest.glob('capture-*.json')) and not row.get('captures') and
                    not row.get('companion_captures') and not row.get('root_companion_captures'),
                    'No original observer or fabricated original capture allowed')
    product = frozen_sources._read(dest / 'product.json', 2*1024*1024)
    current.require(json.loads(product) and hashlib.sha256(product).hexdigest() ==
                    row['product_manifest_sha256'], 'Actual original product hash required')
    cleanup = read(dest, 'cleanup.json')
    current.require(cleanup.get('gradle_exit') == 0 and
        all(cleanup.get(k, {}).get('result') == 'PASS' for k in ('workers_before', 'workers_after', 'simulator')) and
        cleanup['simulator'].get('udid') == row['simulator'], 'Owned original device and workers must retire')
    # Reuse the unchanged single-command verifier after a local label mapping.
    # This does not mutate the saved original command, source, result or device.
    commands = [dict(c, label='compose'+c['label'][len('original'):])
                for c in state['commands'] if c['label'].startswith('original-') and
                c['label'] not in ('original-kmp-aggregate', 'original-kmp-stop')]
    combined.verify_commands(state | {'profile': combined.PROFILE, 'commands': commands},
                             row | {'arm': 'compose'})
    for stage in combined.STAGES:
        raw = frozen_sources._read(dest / ('kernel-'+stage+'.json'), 4096)
        current.require(hashlib.sha256(raw).hexdigest() == row['kernel_resources'][stage],
                        'Actual original kernel snapshot custody')
        combined.resources.validate_resources(json.loads(raw))
    import re
    log = frozen_sources._read(dest / 'test.log', 4*1024*1024)
    starts = re.findall(rb"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.", log)
    current.require(tuple(c.decode()+'/'+m.decode()+'()' for c, m in starts) == cohort.CASES,
                    'All23 original cases/order must execute exactly once')
    return methods


def accept_native_export(out, state):
    current.require(state['profile'] == PROFILE and [r['arm'] for r in state['arms']] == list(ARMS),
                    'Complete ordered original/observed pair required')
    original, observed = state['arms']
    current.require(original['simulator'] != observed['simulator'], 'Fresh owned device for each arm required')
    # Every existing observed source/product/failure-only/774 XML/resource/order
    # check remains in force. Passing one arm cannot qualify the other.
    aggregate.accept_native_export(out, state | {'profile': aggregate.PROFILE, 'arms': [observed]})
    methods = accept_original(out, state, original)
    commands = state['commands']
    def position(label):
        found = [i for i, c in enumerate(commands) if c['label'] == label]
        current.require(len(found) == 1, 'One command at each paired boundary required')
        return found[0]
    current.require(position('original-kmp-aggregate') < position('original-kmp-stop') <
        position('original-build-and-tests') < position('original-gradle-stop') <
        position('compose-build-and-tests') < position('compose-gradle-stop'),
        'One KMP prelude then original and observed commands in declared order required')
    expected = {'original': methods, 'compose': observed['methods']}
    comparison = dict(methods=expected, public_outcomes_equal=methods == observed['methods'],
        native_timing_neutrality=False, application_root_cause=False, shipping_qualified=False,
        full60_journey_neutrality=False, physical_verified=False)
    target = out / 'observer-comparison.json'
    if target.exists():
        current.require(read(out, target.name) == comparison, 'Exact saved paired comparison required')
    else:
        with target.open('x') as output:
            json.dump(comparison, output, indent=2)
    return RESULT
