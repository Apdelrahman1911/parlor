#!/usr/bin/env python3
"""Independently reparse retained synthetic test XML; never execute tests/builds."""
import collections
import datetime
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
AUDIT = HERE.parents[1]
EVIDENCE = AUDIT / 'evidence'
started = datetime.datetime.now(datetime.timezone.utc).isoformat()
inputs = []
problems = []
by_cycle = collections.defaultdict(collections.Counter)
by_platform = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
parsed = {}


def capture(path):
    data = path.read_bytes()
    inputs.append(dict(path=str(path.relative_to(AUDIT)), sha256=hashlib.sha256(data).hexdigest()))
    return data


for file in sorted(EVIDENCE.glob('*/reports/**/TEST-*.xml')):
    rel = file.relative_to(EVIDENCE)
    cycle = rel.parts[0]
    source = str(file.relative_to(EVIDENCE / cycle / 'reports'))
    root = ET.fromstring(capture(file))
    assert root.tag == 'testsuite', str(file)
    cases = [dict(name=c.get('name'), classname=c.get('classname'),
                  failed=any(n.tag in ('failure', 'error') for n in c),
                  skipped=any(n.tag == 'skipped' for n in c)) for c in root.findall('testcase')]
    entry = dict(file=source, suite=root.get('name'),
                 **{k: root.get(k, '0') for k in ('tests', 'failures', 'errors', 'skipped')}, cases=cases)
    if (int(entry['tests']) != len(cases) or
            int(entry['failures']) + int(entry['errors']) != sum(c['failed'] for c in cases) or
            int(entry['skipped']) != sum(c['skipped'] for c in cases)):
        problems.append(dict(file=str(rel), reason='suite counts disagree with actual testcase elements'))
    if any(c['failed'] and c['skipped'] for c in cases):
        problems.append(dict(file=str(rel), reason='case marked both failed and skipped'))
    parsed[(cycle, source)] = entry
    values = dict(xml_files=1, descriptors=len(cases), failed=sum(c['failed'] for c in cases),
                  skipped=sum(c['skipped'] for c in cases), passed=sum(not c['failed'] and not c['skipped'] for c in cases))
    by_cycle[cycle].update(values)
    platform = source.split('/build/test-results/')[1].split('/')[0] if '/build/test-results/' in source else 'managed-android-release'
    by_platform[cycle][platform].update(values)

expected_files = sorted(EVIDENCE.glob('*/test-receipts.json')) + sorted(EVIDENCE.glob('*/instrumented-test-receipts.json'))
compared = set()
for file in expected_files:
    cycle = file.parent.name
    entries = json.loads(capture(file))
    assert isinstance(entries, list), str(file)
    for item in entries:
        key = (cycle, item['file'])
        if key in compared:
            problems.append(dict(file=str(file.relative_to(AUDIT)), reason='duplicate manifest item', key=key))
        compared.add(key)
        if parsed.get(key) != item:
            problems.append(dict(file=str(file.relative_to(AUDIT)), reason='JSON/XML identity, suite counts, or complete case metadata mismatch', key=key))

unindexed = sorted(set(parsed) - compared)
allowed_unindexed = [('focused-storage-01', 'shared/storage/build/test-results/desktopTest/TEST-com.parlor.storage.snapshot.FileBackedSnapshotStoreTest.xml')]
if unindexed != allowed_unindexed:
    problems.append(dict(reason='unexpected retained XML without corresponding JSON manifest', actual=unindexed))
if len(parsed) != 760 or len(compared) != 759:
    problems.append(dict(reason='expected current fixed inventory differs', parsed=len(parsed), compared=len(compared)))

failing_or_skipped = []
for (cycle, source), entry in sorted(parsed.items()):
    for case in entry['cases']:
        if case['failed'] or case['skipped']:
            failing_or_skipped.append(dict(cycle=cycle, source=source, **case))

result = dict(started_at=started, finished_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              reviewer='/root/mafia_cont', status='FAIL' if problems else 'PASS',
              scope='Structured reparse/hash comparison of retained raw XML only; not test execution or line-by-line rereading of test implementations.',
              script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), xml_files=len(parsed),
              xml_files_compared_to_json=len(compared), comparison_problems=problems,
              unindexed_xml=unindexed, unindexed_reason='First storage cycle report collection failed; only one11-case XML survived. No complete first-cycle suite count is inferred.',
              per_cycle={k: dict(v) for k, v in sorted(by_cycle.items())},
              per_cycle_platform={k: {platform: dict(value) for platform, value in sorted(v.items())} for k, v in sorted(by_platform.items())},
              failed_or_skipped_cases=failing_or_skipped, inputs=inputs,
              limits=['Counts are per execution and include repeats; no sum is presented as unique test coverage.',
                      'Intentional failing reproducers remain failures of their asserted contract, not ordinary green-suite failures.',
                      'Retained XML cannot certify test discovery completeness, all source variants, physical networking, signed-device behavior or Store readiness.',
                      'XCTest, native shell and release-Python results use their own evidence schemas and are not included in JUnit counts.'],
              cleanup='Synchronous Python-B read-only reparse. No build outputs, daemon, app, device or server started. Only required script/receipt retained.')
with (HERE / 'retained-junit-reparse.json').open('x') as handle:
    json.dump(result, handle, indent=2)
    handle.write('\n')
print(json.dumps({k: result[k] for k in ['status', 'xml_files', 'xml_files_compared_to_json', 'comparison_problems', 'per_cycle']}, indent=2))
raise SystemExit(1 if problems else 0)
