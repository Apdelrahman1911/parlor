#!/usr/bin/env python3
"""Readable coverage/research indexes of retained evidence, not new review claims."""
import collections
import datetime
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
rows = [json.loads(s) for s in (HERE / 'coverage/FINAL_COVERAGE.jsonl').read_text().splitlines()]
summary = json.loads((HERE / 'coverage/summary.json').read_text())
groups = collections.defaultdict(lambda: [0, 0])
for row in rows:
    if row['kind'] != 'text':
        continue
    parts = row['path'].split('/')
    group = '/'.join(parts[:2]) if parts[0] in ['shared', 'game-modes'] else parts[0] if len(parts) > 1 else '(root files)'
    groups[group][0] += 1
    groups[group][1] += row['lines']
text = ['# Coverage inventory', '',
        'The machine ledger is [FINAL_COVERAGE.jsonl](coverage/FINAL_COVERAGE.jsonl). This page summarizes that ledger; it is not an extra reading attestation.', '',
        f"Recorded verbatim reading: **{summary['complete_verbatim_text_file_count']} text files / {summary['verbatim_read_line_count']:,} lines**. Six binary inspections. No unread applicable text ranges; no inferred behavioral-completeness percentage.",
        '', '| Area | Text files | Lines |', '|---|---:|---:|']
for group, (files, lines) in sorted(groups.items()):
    text.append(f'| `{group}` | {files} | {lines:,} |')
text += ['', '## How to interpret the ledger', '',
         '- Each baseline entry has a path, content SHA-256 when safe/applicable, text/binary/exclusion kind, current disposition, production/test/build role and source-set relevance.',
         '- Actual reading receipts supply reviewer identity, one-based reviewed ranges, cross-file paths and reasoning/evidence. `TEXT_READ_COMPLETE_WITH_LIMITS` does not claim all behavior is proven.',
         '- Generated historical CSV structural inspection and actual raw reading are separate. Only raw read ranges count toward verbatim totals; the final raw closure is preserved.',
         '- Six binaries were inspected with format/metadata/checksum/image/font tools; see [binary report](reviews/binary-assets-wrapper-mafia-cont.md) and [font metadata](evidence/design-font-metadata.json). No source-line count is assigned to binary bytes.',
         '- Historical audit-index reading is separately stored in [audit-material receipts](coverage/AUDIT_MATERIAL_REVIEW_RECEIPTS.jsonl). A matching archived historical snapshot does not attest a subsequently edited current index.',
         '- [Observed Gradle graph](coverage/observed-build-graph.txt) distinguishes shipping modules, development Desktop, test-fixture modules and the web prototype. Build membership is not runtime reachability proof.',
         '- Baseline scope excludes 72 prior-audit files, 28 generated/local-state entries and protected `local.properties`; additional pruned private/cache/IDE directories are listed in [baseline](baseline.json). These were preserved, not read or byte-attested.',
         '- All 634 fingerprinted baseline text/binary inputs are compared at finalization; refs, stash and non-audit untracked listing are separately checked. Protected/unfingerprinted exclusions cannot be claimed byte-identical.',
         '', '## Remaining uncertainty', '',
         'No applicable raw text ranges remain unread. Platform/device behavior, performance measurements, full UI/a11y journeys, unavailable cross-host tasks and candidate-specific uncertainty remain in [CONTINUATION.md](CONTINUATION.md), [VERIFICATION.md](VERIFICATION.md) and per-finding dossiers.',
         'The inventory is finite and source-bound; it cannot prove absence of undiscovered defects. Source changes require a new comparison and affected re-review, not reuse of an old completion percentage.']
(HERE / 'COVERAGE.md').write_text('\n'.join(text) + '\n')

# Extract only explicitly dated fetch/reference records, not every hyperlink in
# downloaded API documentation. Original records remain authoritative.
research = []
date_keys = ['accessed_utc', 'accessed_at', 'accessed', 'recorded_at', 'recorded_utc', 'timestamp', 'at']
def visit(value, ref, pointer='', inherited_date=None):
    if isinstance(value, dict):
        date = next((value[k] for k in date_keys if isinstance(value.get(k), str)), inherited_date)
        url = value.get('url')
        if isinstance(url, str) and url.startswith(('https://', 'http://')) and date:
            research.append(dict(source_record=ref, json_pointer=pointer or '/', url=url,
                                 access_or_record_timestamp=date, original_record=value))
        for k, v in value.items():
            visit(v, ref, pointer + '/' + k, date)
    elif isinstance(value, list):
        for i, v in enumerate(value):
            visit(v, ref, pointer + '/' + str(i), inherited_date)

files = list((HERE / 'research').rglob('*.json')) + list((HERE / 'research').rglob('*.jsonl'))
files += list((HERE / 'evidence').rglob('*research*.json')) + list((HERE / 'evidence').rglob('*research*.jsonl'))
for file in sorted(set(files)):
    ref = file.relative_to(HERE).as_posix()
    if file.suffix == '.jsonl':
        for number, line in enumerate(file.read_text().splitlines(), 1):
            visit(json.loads(line), ref + ':' + str(number))
    else:
        visit(json.loads(file.read_text()), ref)
(HERE / 'RESEARCH_INDEX.json').write_text(json.dumps(dict(
    generated_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), records=research,
    limitation='Index of explicit dated research records, not a new fetch or proof that every upstream file was exhaustively reviewed. Original claim/applicability/version/limitations and source dossiers control. Undated citations remain in original dossiers.'), indent=2) + '\n')

anchors = []
names = {'App.kt', 'AppNavigation.kt', 'AppNavigationHost.kt', 'AppBackPolicy.kt', 'AppNavigationTransitions.kt',
         'AppNavigationTransitions.ios.kt', 'MainViewController.kt', 'MainActivity.kt', 'Main.kt', 'ContentView.swift',
         'HomeRecoveryAvailability.kt', 'HomeScreen.kt', 'LocalAppLocale.ios.kt', 'SessionStartHandshake.kt',
         'P2pKitRoomTransport.kt', 'AppLifecycleRoomCoordinator.kt', 'IosSnapshotFileSystem.kt',
         'IosSnapshotKeychain.kt', 'IosSecureKeyValueBacking.kt', 'AndroidSnapshotFileSystem.kt',
         'ResumableCredentialStore.kt', 'WhodunitHostSessionFlow.kt', 'WhodunitPhaseRouter.kt',
         'ReleaseRuntimeSmokeTest.java', 'MainActivityColdStartTest.kt', 'run_release_managed_device_smoke.sh'}
for row in rows:
    path = row['path']
    if row['kind'] == 'text' and (Path(path).name in names or '/ui/screens/' in path):
        anchors.append(dict(absolute_path=str(ROOT / path), path=path, sha256=row['sha256'],
                            read_ranges=row['reviewed_ranges'], complete_file_lines=row['lines'],
                            remaining='Behavioral/scenario execution as described in CONTINUATION.md, not unread source.',
                            evidence=row['evidence'], receipt_refs=row['receipt_refs']))
(HERE / 'evidence/continuation-source-anchors.json').write_text(json.dumps(anchors, indent=2) + '\n')
print(json.dumps(dict(text_files=summary['complete_verbatim_text_file_count'], research_records=len(research), continuation_anchor_files=len(anchors))))
