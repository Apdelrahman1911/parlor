# Coverage inventory

The machine ledger is [FINAL_COVERAGE.jsonl](coverage/FINAL_COVERAGE.jsonl). This page summarizes that ledger; it is not an extra reading attestation.

Recorded verbatim reading: **628 text files / 139,888 lines**. Six binary inspections. No unread applicable text ranges; no inferred behavioral-completeness percentage.

| Area | Text files | Lines |
|---|---:|---:|
| `(root files)` | 12 | 4,985 |
| `.github` | 5 | 2,497 |
| `.run` | 4 | 116 |
| `build-logic` | 5 | 230 |
| `composeApp` | 93 | 11,195 |
| `config` | 5 | 521 |
| `design` | 4 | 4,405 |
| `docs` | 36 | 9,419 |
| `game-modes/mafia` | 81 | 18,446 |
| `game-modes/whodunit` | 129 | 29,903 |
| `gradle` | 3 | 12,181 |
| `iosApp` | 16 | 911 |
| `release` | 1 | 64 |
| `scripts` | 16 | 7,756 |
| `shared/content` | 19 | 1,736 |
| `shared/core` | 12 | 480 |
| `shared/design-system` | 61 | 4,565 |
| `shared/engine` | 20 | 566 |
| `shared/engine-testing` | 5 | 384 |
| `shared/networking` | 27 | 2,514 |
| `shared/networking-testing` | 3 | 291 |
| `shared/session` | 26 | 9,814 |
| `shared/storage` | 14 | 1,710 |
| `shared/transport-p2p` | 31 | 15,199 |

## How to interpret the ledger

- Each baseline entry has a path, content SHA-256 when safe/applicable, text/binary/exclusion kind, current disposition, production/test/build role and source-set relevance.
- Actual reading receipts supply reviewer identity, one-based reviewed ranges, cross-file paths and reasoning/evidence. `TEXT_READ_COMPLETE_WITH_LIMITS` does not claim all behavior is proven.
- Generated historical CSV structural inspection and actual raw reading are separate. Only raw read ranges count toward verbatim totals; the final raw closure is preserved.
- Six binaries were inspected with format/metadata/checksum/image/font tools; see [binary report](reviews/binary-assets-wrapper-mafia-cont.md) and [font metadata](evidence/design-font-metadata.json). No source-line count is assigned to binary bytes.
- Historical audit-index reading is separately stored in [audit-material receipts](coverage/AUDIT_MATERIAL_REVIEW_RECEIPTS.jsonl). A matching archived historical snapshot does not attest a subsequently edited current index.
- [Observed Gradle graph](coverage/observed-build-graph.txt) distinguishes shipping modules, development Desktop, test-fixture modules and the web prototype. Build membership is not runtime reachability proof.
- Baseline scope excludes 72 prior-audit files, 28 generated/local-state entries and protected `local.properties`; additional pruned private/cache/IDE directories are listed in [baseline](baseline.json). These were preserved, not read or byte-attested.
- All 634 fingerprinted baseline text/binary inputs are compared at finalization; refs, stash and non-audit untracked listing are separately checked. Protected/unfingerprinted exclusions cannot be claimed byte-identical.

## Remaining uncertainty

No applicable raw text ranges remain unread. Platform/device behavior, performance measurements, full UI/a11y journeys, unavailable cross-host tasks and candidate-specific uncertainty remain in [CONTINUATION.md](CONTINUATION.md), [VERIFICATION.md](VERIFICATION.md) and per-finding dossiers.
The inventory is finite and source-bound; it cannot prove absence of undiscovered defects. Source changes require a new comparison and affected re-review, not reuse of an old completion percentage.
