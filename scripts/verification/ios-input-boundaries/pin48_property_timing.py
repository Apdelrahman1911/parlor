"""Copy-only XCTest property clocks: no extra query, changed guard or native hook."""
import json
import math
import re

import frozen_sources
import pin01_copy

# Replace only original statements with those SAME statements bracketed by clocks.
# Their query order and original exists/stable guards are preserved verbatim.
QUERIES = (
    ('exists', '            let exists = control.exists\n'),
    ('frame', '            let frame = exists ? control.frame : nil\n'),
    ('enabled', '            let enabled = exists && control.isEnabled\n'),
    ('hittable', '            let hittable = stable ? control.isHittable : nil\n'),
)
APPEND = '                    "stable=\\(stable), hittable=\\(String(describing: hittable))")'
TIMED_APPEND = APPEND[:-1] + ' +\n' + ''.join(
    '                    ", '+name+'Seconds=\\(pin48'+name+'End - pin48'+name+'Start)"'+
    (')' if i == 3 else ' +\n') for i,(name,_) in enumerate(QUERIES))


def bracket(name, statement):
    return ('            let pin48'+name+'Start = ProcessInfo.processInfo.systemUptime\n'+statement+
            '            let pin48'+name+'End = ProcessInfo.processInfo.systemUptime\n')


def expand(driver):
    for name, statement in QUERIES:
        driver = pin01_copy.once(driver, statement, bracket(name, statement))
    return pin01_copy.once(driver, APPEND, TIMED_APPEND)


def restore(driver):
    for name, statement in QUERIES:
        driver = pin01_copy.once(driver, bracket(name, statement), statement)
    return pin01_copy.once(driver, TIMED_APPEND, APPEND)


def decode_failed_samples(log):
    """Only fixed numeric timings, no label/value/hierarchy retention in receipt."""
    reports = []
    for line in log.splitlines():
        if not line.startswith('Public control settlement: '):
            continue
        samples = line.removeprefix('Public control settlement: ').split('; ')
        if not 1 <= len(samples) <= 12:
            raise ValueError('Bounded original failed settlement samples required')
        rows = []
        for sample in samples:
            values = {}
            for key in ('at', 'querySeconds', *(name+'Seconds' for name,_ in QUERIES)):
                matches = re.findall(r'(?:^|, )'+key+r'=([0-9.eE+-]+)(?=, |$)', sample)
                if len(matches) != 1:
                    raise ValueError('Every exact property-clock boundary required')
                value = float(matches[0])
                if not math.isfinite(value) or value < 0:
                    raise ValueError('Finite nonnegative actual property clocks')
                values[key] = value
            if sum(values[name+'Seconds'] for name,_ in QUERIES) > values['querySeconds']:
                raise ValueError('Property bodies cannot exceed their complete original query interval')
            rows.append(values)
        reports.append(rows)
    if len(reports) > 2:
        raise ValueError('Only the two original methods may fail')
    return reports


def write_receipt(out, state):
    log = frozen_sources._read(out/'compose/test.log', 4194304).decode()
    methods = state['arms'][0]['methods']
    summary = json.loads(frozen_sources._read(out/'compose/summary.json', 2097152))
    expected = {f['testIdentifierString'] for f in summary['testFailures']
                if 'Public setup control must stop scrolling before it is tapped' in f['failureText']}
    starts = list(re.finditer(r"Test Case '-\[iosAppUITests\.([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\]' started\.", log))
    reports = {}
    for i, start in enumerate(starts):
        method = start[1]+'/'+start[2]+'()'
        end = starts[i+1].start() if i+1 < len(starts) else len(log)
        samples = decode_failed_samples(log[start.end():end])
        if samples:
            if len(samples) != 1 or methods.get(method) != 'Failed' or method not in expected:
                raise ValueError('One original failed settlement must own each property report')
            reports[method] = samples[0]
    if set(reports) != expected or len(decode_failed_samples(log)) != len(reports):
        raise ValueError('All actual failed settlements retained with exact original method ownership')
    receipt = dict(methods=methods, failed_settlement_property_samples=reports,
                   additional_accessibility_queries=False, original_ten_second_assertion=True,
                   application_root_cause=False, shipping_qualified=False,
                   native_timing_neutrality=False, physical_verified=False)
    target = out/'query-property-boundaries.json'
    if target.exists():
        if json.loads(frozen_sources._read(target, 65536)) != receipt:
            raise ValueError('Saved property receipt must equal original evidence derivation')
    else:
        with target.open('x') as f:
            json.dump(receipt, f, indent=2)
    return receipt
