#!/usr/bin/env python3
"""DRAFT authoring tool: output a reviewed promotion patch; never apply it."""
import ast
import difflib
import hashlib
import json
from pathlib import Path

from l08_copy import once

DRAFT = Path(__file__).resolve().parent
ROOT = DRAFT.parents[3]
CONTROLS = ROOT / 'scripts/verification/ios-readiness'


def revised_files():
    copied = (CONTROLS / 'copied_sources.py').read_text()
    copied = once(copied, 'from instrument_storage import PATH as CREDENTIALS, ADDITION as NATIVE_ADDITION, instrument_credentials',
        'from instrument_storage import PATH as CREDENTIALS, ADDITION as NATIVE_ADDITION, instrument_credentials\n'
        'from l08_copy import L08_ADDITIONS, L08_ADDITIONAL_MODIFIED, instrument_l08_kotlin')
    copied = once(copied, "             NATIVE_ADDITION: 'NativeReadinessProbe.kt.in'}",
                  "             NATIVE_ADDITION: 'NativeReadinessProbe.kt.in', **L08_ADDITIONS}")
    copied = once(copied, 'MODIFIED_KOTLIN = (SETTINGS, WD, MF, MAIN, LOCALE, CREDENTIALS, *NAME_FIELDS)',
                  'MODIFIED_KOTLIN = (SETTINGS, WD, MF, MAIN, LOCALE, CREDENTIALS, *NAME_FIELDS, *L08_ADDITIONAL_MODIFIED)')
    copied = once(copied, '        observer.write_text((HERE / template).read_text())',
                  '        observer.write_text((HERE / template).read_text())\n    instrument_l08_kotlin(copy_root)')

    runner = (CONTROLS / 'run_ios_readiness.py').read_text()
    runner = once(runner, 'from native_failure_receipts import read_failure',
        'from native_failure_receipts import read_failure\n'
        'from l08_copy import instrument_probe_swift, instrument_ui_test\n'
        'from l08_receipts import (preserve_l08_evidence, storage_result_names, host_result_names,\n'
        '                          read_owned_result, verify_storage, verify_host, framework_subset)')
    runner = once(runner, 'No source, dependency graph, navigation, reducer or live session is replaced.',
        'No production repository source, dependency graph, or reducer is replaced. L08\n'
        'adds an explicit copied-only controlled transport/presentation seam for host\n'
        'verification; storage resume still uses the real Home navigation.')
    runner = once(runner, "No physical LAN, full-game, signed-release, Store or leak-free claim.', approved_control_sha256=approved)",
        "Additive L08: thirteen full GameSnapshot/real Home resume and damaged-record boots and three ControlledStartRoom host locale/lifecycle fixtures; no physical LAN, full-UI-game, signed-release, Store or leak-free claim.', approved_control_sha256=approved)")
    runner = once(runner, "'\\n' + (FIXTURE / 'DSC01Probe.swift.in').read_text() + '\\n' +",
        "'\\n' + instrument_probe_swift((FIXTURE / 'DSC01Probe.swift.in').read_text()) + '\\n' +")
    runner = once(runner,
        "render_owned_native_launch((FIXTURE / 'NativeReadinessLaunch.swift.in').read_text(), temp, mode))",
        "render_owned_native_launch((FIXTURE / 'NativeReadinessLaunch.swift.in').read_text(), temp, mode) + '\\n' +\n"
        "                               (FIXTURE / 'L08StorageLaunch.swift.in').read_text() + '\\n' +\n"
        "                               (FIXTURE / 'L08HostLaunch.swift.in').read_text())")
    runner = once(runner, "ui_test.write_text((FIXTURE / 'IOSAppLaunchUITests.swift.in').read_text() + '\\n' +",
        "ui_test.write_text(instrument_ui_test((FIXTURE / 'IOSAppLaunchUITests.swift.in').read_text()) + '\\n' +")
    runner = once(runner, "(FIXTURE / 'NativeReadinessUITests.swift.in').read_text())",
        "(FIXTURE / 'NativeReadinessUITests.swift.in').read_text() + '\\n' +\n"
        "                               (FIXTURE / 'L08StorageUITests.swift.in').read_text() + '\\n' +\n"
        "                               (FIXTURE / 'L08HostUITests.swift.in').read_text())")
    runner = once(runner, "for scenario in ('settings', 'whodunit', 'mafia', 'os', 'readiness'):",
        "for scenario in ('settings', 'whodunit', 'mafia', 'os', 'readiness', 'l08-storage', 'l08-host'):")
    runner = once(runner, "                cleanup_result = container / 'tmp/parlor-native-synthetic-seed-cleanup.json'",
        "                receipt['l08_preserved_operation_files'] = preserve_l08_evidence(container, dest)\n"
        "                save()\n"
        "                cleanup_result = container / 'tmp/parlor-native-synthetic-seed-cleanup.json'")
    runner = once(runner, "            native_scenario = json.loads((dest / 'probe-readiness-result.json').read_text())",
        "            native_scenario = json.loads((dest / 'probe-readiness-result.json').read_text())\n"
        "            l08_storage_results = [read_owned_result(dest / name, dest, 16384) for name in storage_result_names()]\n"
        "            l08_host_results = [read_owned_result(dest / name, dest, 24576) for name in host_result_names()]\n"
        "            l08_storage_scenario = read_owned_result(dest / 'probe-l08-storage-result.json', dest, 262144)\n"
        "            l08_host_scenario = read_owned_result(dest / 'probe-l08-host-result.json', dest, 262144)\n"
        "            all_observed_runs = native_results + l08_storage_results + l08_host_results")
    runner = once(runner, '            for native in native_results:', '            for native in all_observed_runs:')
    runner = once(runner, '                built_inventory, installed_inventory, native_results, framework_inventory)',
        '                built_inventory, installed_inventory, native_results,\n'
        '                framework_subset(native_results, all_observed_runs, framework_inventory))\n'
        "            receipt['l08_storage'] = verify_storage(l08_storage_results, l08_storage_scenario,\n"
        "                native_scenario['runToken'], (dest / 'xcodebuild.log').read_text(), mode,\n"
        '                built_inventory, installed_inventory,\n'
        '                framework_subset(l08_storage_results, all_observed_runs, framework_inventory))\n'
        "            receipt['l08_host'] = verify_host(l08_host_results, l08_host_scenario,\n"
        "                native_scenario['runToken'], (dest / 'xcodebuild.log').read_text(), mode,\n"
        '                built_inventory, installed_inventory,\n'
        '                framework_subset(l08_host_results, all_observed_runs, framework_inventory))')
    runner = once(runner, "                receipt.get('probe_harness_status') == 'observation_complete') else 'FAIL'",
        "                receipt.get('probe_harness_status') == 'observation_complete' and\n"
        "                receipt.get('l08_storage', {}).get('status') == 'PASS' and\n"
        "                receipt.get('l08_host', {}).get('status') == 'PASS') else 'FAIL'")

    readme = (CONTROLS / 'README.md').read_text()
    readme += '''\n\n## Additive L08 native regression extension\n\nThe same owned-copy runner now requires the additional storage and retained-host\nscenarios described in `L08_README.md`. The earlier eight-launch arbitrary-byte\nhealth evidence and original local-controller matrices are unchanged, not\nretroactively relabeled as full snapshots or real multiplayer. New receipts bind\nall additional launches to their actual framework bytes/UUIDs. Both new subgates\nmust pass for a runtime PASS; their synthetic driver/transport limits remain\nexplicit. See the extension notes before creating a new source binding.\n'''
    readme = once(readme, '''  or Store verdict. Full games, save-envelope recovery, physical LAN, real
  signing, assistive technology, and past-launch root causes remain outside
  these controls' claims.''', '''  or Store verdict. Full UI games, physical LAN, real Store signing, assistive
  technology, and past-launch root causes remain outside these controls' claims.
  Save-envelope recovery belongs only to the additive L08 receipts below.''')
    return {'copied_sources.py': copied, 'run_ios_readiness.py': runner, 'README.md': readme}


def main():
    changed = revised_files()
    patch = []
    inputs = []
    for name, after in changed.items():
        path = CONTROLS / name
        before = path.read_text()
        if name.endswith('.py'):
            ast.parse(after, filename=name)
        relative = str(path.relative_to(ROOT))
        patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                          fromfile='a/' + relative, tofile='b/' + relative))
        inputs.append(dict(path=relative, original_sha256=hashlib.sha256(before.encode()).hexdigest(),
                           proposed_sha256=hashlib.sha256(after.encode()).hexdigest()))
    # Preserve initial draft patch/receipts; this bounded revision adds the
    # recognized-current/retained-legacy witness, not a retrospective PASS.
    with (DRAFT / 'promotion-02.patch').open('x') as stream:
        stream.writelines(patch)
    with (DRAFT / 'promotion-inputs-02.json').open('x') as stream:
        stream.write(json.dumps(dict(status='DRAFT_NOT_APPLIED_NOT_EXECUTED', inputs=inputs), indent=2) + '\n')
    print('Draft patch authored; Python AST parsed. No root/control/application changes or tests/builds executed.')


if __name__ == '__main__': main()
