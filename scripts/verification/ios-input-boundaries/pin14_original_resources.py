"""Original source resource context; observation only, never app qualification.

PIN13 restored case order but retained its copy-only3GiB serial build profile.
This distinct experiment restores original6GiB/default parallel Gradle settings
and the booted-during-build Simulator, with bounded read-only kernel snapshots.
No app/driver changes, new input, test retry or assertion deadline changes.
"""
import hashlib
import json
import re
import shlex

import current_boundary_adapter as current
import pin01_receipts
import pin06_public_setup_pair as pair
import pin13_original_cohort as cohort

SOURCE, TREE = cohort.SOURCE, cohort.TREE
PROFILE = 'native-original-resource-context'
ARMS = cohort.ARMS
SCOPE = ('Original23 cases/product/driver under original source6GiB parallel Gradle and booted-build '
         'context with numeric kernel VM snapshots; not causal proof, qualification or physical evidence')
STAGES = ('before-build', 'after-build', 'after-tests')
VM_FIELDS = ('Pages free', 'Pages active', 'Pages inactive', 'Pages wired down',
             'Pages occupied by compressor', 'Pageouts', 'Swapins', 'Swapouts')


def renderer_for_profile(profile, source, runtime, device):
    current.require((profile, source, runtime, device) == (PROFILE, SOURCE, pair.RUNTIME, pair.DEVICE),
                    'Exact original resource context required')
    return cohort.renderer_for_profile(cohort.PROFILE, source, runtime, device)


def build_environment(inherited, registry, temporary):
    # Original frozen gradle.properties supplies Xmx6g/parallel=true/default
    # workers. Preserve the original CI in-process Kotlin compiler and owned dirs.
    options = ['-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process',
        '-Dorg.gradle.daemon.registry.base=' + str(registry), '-Djava.io.tmpdir=' + str(temporary)]
    return dict(inherited, GRADLE_OPTS=shlex.join(options))


def build_command(base):
    # Original xcodebuild used default available jobs, not the diagnostic -jobs1.
    return [a for a in base if a != '-quiet'] + ['-showBuildTimingSummary', 'build-for-testing']


def decode_resources(vm, swap, memory, cpus):
    for raw in (vm, swap, memory, cpus):
        current.require(type(raw) is bytes and 0 < len(raw) <= 16384, 'Bounded original kernel response')
    text = vm.decode('ascii'); lines = text.splitlines()
    header = re.fullmatch(r'Mach Virtual Memory Statistics: \(page size of ([0-9]+) bytes\)', lines[0])
    current.require(header is not None and 1 < len(lines) <= 64, 'Exact bounded Darwin VM response')
    fields = {}
    for line in lines[1:]:
        match = re.fullmatch(r'([A-Za-z0-9" .()-]+):\s+([0-9]+)\.', line)
        current.require(match is not None, 'Numeric kernel VM counters only')
        name, value = match.groups()
        current.require(name not in fields, 'Duplicate VM counter')
        fields[name] = int(value)
    current.require(all(k in fields for k in VM_FIELDS), 'Required VM counters missing')
    match = re.fullmatch(rb'total = ([0-9]+(?:\.[0-9]+)?)M  used = ([0-9]+(?:\.[0-9]+)?)M  free = ([0-9]+(?:\.[0-9]+)?)M  \(encrypted\)\s*', swap)
    current.require(match is not None, 'Exact numeric encrypted swap response')
    current.require(re.fullmatch(rb'[0-9]+\s*', memory) and re.fullmatch(rb'[0-9]+\s*', cpus),
                    'Numeric host memory/CPU response')
    value = dict(page_size=int(header[1]), pages={k:fields[k] for k in VM_FIELDS},
        swap_mib=[float(v) for v in match.groups()], physical_bytes=int(memory), logical_cpus=int(cpus))
    validate_resources(value)
    return value


def validate_resources(value):
    current.require(type(value) is dict and set(value)=={'page_size','pages','swap_mib','physical_bytes','logical_cpus'},
                    'Unknown/private resource field')
    current.require(type(value['page_size']) is int and value['page_size'] in (4096,16384), 'Darwin page size')
    current.require(type(value['pages']) is dict and set(value['pages'])==set(VM_FIELDS) and
        all(type(v) is int and 0<=v<=2**63-1 for v in value['pages'].values()), 'Bounded numeric VM counters')
    current.require(type(value['swap_mib']) is list and len(value['swap_mib'])==3 and
        all(type(v) in (int,float) and 0<=v<=2**40 for v in value['swap_mib']), 'Bounded numeric swap metrics')
    current.require(type(value['physical_bytes']) is int and 0<value['physical_bytes']<=2**50 and
        type(value['logical_cpus']) is int and 1<=value['logical_cpus']<=1024, 'Bounded host capacity')


def observe_resources(command, dest, stage):
    current.require(stage in STAGES, 'Exact resource boundary')
    values=[]
    for name,argv in [('vm',['/usr/bin/vm_stat']),('swap',['/usr/sbin/sysctl','-n','vm.swapusage']),
                     ('memory',['/usr/sbin/sysctl','-n','hw.memsize']),('cpus',['/usr/sbin/sysctl','-n','hw.logicalcpu'])]:
        code,raw=command(argv,'kernel-'+stage+'-'+name)
        current.require(code==0, 'Original kernel observation failed')
        values.append(raw)
    value=decode_resources(*values)
    raw=json.dumps(value,sort_keys=True,allow_nan=False).encode()
    current.require(0<len(raw)<=4096, 'Bounded numeric resource record')
    with (dest/('kernel-'+stage+'.json')).open('xb') as f:f.write(raw)
    return hashlib.sha256(raw).hexdigest()


test_command = cohort.test_command
verify_results = cohort.verify_results
failed_method_exports = cohort.failed_method_exports
retain_companion = cohort.retain_companion
retain_root_companion = cohort.retain_root_companion


def accept_native_export(out,state):
    cohort.accept_cohort_export(out,state,profile=PROFILE,
        renderer=renderer_for_profile(PROFILE,SOURCE,pair.RUNTIME,pair.DEVICE))
    current.require(state.get('build_profile',{}).get('gradle_heap_bytes')==6*1024**3 and
        state['build_profile'].get('gradle_workers')=='original-default' and
        state['build_profile'].get('gradle_parallel') is True and
        state['build_profile'].get('boot_after_build') is False and
        state['build_profile'].get('xcode_jobs')=='original-default', 'Exact original resource profile required')
    row=state['arms'][0]
    current.require(set(row.get('kernel_resources',{}))==set(STAGES), 'Every original VM boundary required')
    for stage in STAGES:
        raw=(out/'compose'/('kernel-'+stage+'.json')).read_bytes()
        current.require(0<len(raw)<=4096 and hashlib.sha256(raw).hexdigest()==row['kernel_resources'][stage],
                        'Original kernel record custody')
        validate_resources(json.loads(raw,object_pairs_hook=pin01_receipts.reject_duplicates))
    return 'ORIGINAL_RESOURCE_CONTEXT_OBSERVATIONS_COMPLETE_NOT_QUALIFICATION'
