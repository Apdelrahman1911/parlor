# Coverage assembler — independent binary-evidence delta review

Reviewer: `/root/mafia_cont`. **APPROVED** at280lines/SHA256 `09b2a9003e9c0c3cf0e78bd79ca495fcea7a7778b636d8f3a4d4257881619c29`. The old271-line`e8d37238` helper was previously fully reviewed; the full279-line`11d6236` proposal and exact corrected method-guard delta were reopened. Original and intermediate source snapshots remain retained.

## What changed

Only audit-material receipts outside the baseline source inventory gain explicit `BINARY_INSPECTED`/`BINARY_VISUAL_INSPECTION` handling. Such receipts must claim no line ranges, no total line count, no positive/negative reviewed-line count, and a nonblank string inspection method. Hash/current-path/historical-snapshot/containment checks still precede this branch. Audit binary evidence goes into the separate audit-material ledger; it never adds baseline source files or reviewed source lines. All later baseline accounting, source-preservation, output-summary and return AST is unchanged.

The initial proposal accepted whitespace-only or truthy non-string methods. Independent synthetic counterexamples demonstrated that gap; root tightened the guard. These are audit-helper corrections, not new Parlor application findings.

## Independent checks

36data-only assertions PASS. Only the original receipt-loop/accounting AST fragments were executed, with in-memory path/file objects; the full helper, inventory scan, Git/runpy/process commands and report writes were never invoked.

- The actual352,499-byte PNG (`1898984f1f4c2c56176577271b5e871f8ea607807833215db818d5b9d3f50371`) was hashed and its signature,1206×2622IHDR dimensions, complete chunk envelope and all chunk CRCs checked. The old helper raises UnicodeDecodeError for it; the corrected binary branch does not try UTF8 decoding.
- Both explicit binary statuses work. Fictitious line claims, missing/blank/non-string methods fail validation. The old malformed-method cases remain preserved.
- Ordinary UTF8 text still uses range validation. Invalid text ranges fail; mislabelled binary-as-text still fails decoding rather than silently receiving an exemption.
- Current and historical byte binding remains strict. Missing/mismatched snapshots, outside-audit paths and simulated escaping symlinks fail; valid historical binary/text receipts explicitly do not attest current bytes.
- The entire synthetic baseline-source output is unchanged with or without audit PNG evidence. The audit image never enters the source-receipt map.

This assembler validates recorded evidence metadata, **not arbitrary image format correctness or whether a human actually viewed an image**. A hash-bound receipt may intentionally describe malformed binary bytes. Actual visual/runtime evidence remains the separate independent reviewer’s responsibility. This reviewer did not decode/display pixels or infer Home, accessibility, privacy, or device correctness from the PNG.

## Execution and limits

One first test command contained an incorrect audit-directory spelling and exited2 before loading the script. The corrected explicit `/usr/bin/python3 -B …/synthetic_review.py` command exited0; all36assertions and source/PNG hashes are recorded in `evidence/coverage-binary-delta-mafia-cont/synthetic-receipt.json`. Launcher detail/interpreter version are retained in `execution-receipt.json`; no fabricated command-start timestamp is supplied.

No production, Git, configuration, signing or Store changes were made. No generated build output, Gradle/native/device/server process or daemon was created. Only compact audit source/test/JSON/report evidence remains. Root exclusively owns real aggregate generation and the final preservation check. Final generated-ledger review remains a separate pending step.
