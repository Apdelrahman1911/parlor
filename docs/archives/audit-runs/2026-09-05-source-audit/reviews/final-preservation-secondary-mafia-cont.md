# Final preservation checker — secondary Apple temporary roots

Reviewer: `/root/mafia_cont`. **APPROVED for root-owned execution after the build lane is idle**, at exactly163lines/SHA256 `93d85c028fbb0050f2c53b2fdebe3b653aec4151356cbf5077b569ab95fd89dc`. This is a source/safety approval, not an executed final preservation or cleanup PASS. The complete160-line initial proposal and163-line corrected helper were reopened. Imported `assemble_coverage.py`271lines/`e8d37238…` and `audit_inventory.py`122lines/`6807d46…` are unchanged from earlier full independent reviews.

## Delta and counter-evidence

Relative to approved148-line`433f254`, the change collects explicitly recorded `owned_secondary_temporary_roots`, validates their ibtoold-root shape, adds them to existing path/process checks, and reports the checked set. No temporary-directory enumeration, FIFO reads, filesystem deletion or process termination was added. The helper still regenerates audit reports and writes its own receipt; “read-only” refers to application/source/process state, not an absence of audit-file writes.

The first160-line`41faf6` proposal had a concrete alias gap: the receipt recorded `/private/var/folders/…/ibtoold-67921`, whereas the actual attested child argv used `/var/folders/…/ibtoold-67921`. A synthetic late/unlisted PID with that alternate argv escaped literal marker detection and could produce cleanupPASS when paths were already absent. Exact previously attested PID/start matching still detected known67921/68144: no actual live process leak was inferred. Root corrected the helper to add both exact macOS aliases to path-existence and process-marker checks. The rejected proposal and its counterexample remain preserved, rather than silently replaced.

25prior mocked regressions plus46secondary-root/path/process/error/AST assertions passed against the frozen sources. New checks cover both input spellings, nested/deduplicated receipts, unlabelled unrelated paths, malformed path entries, the preserved old counterexample, detection of late/unlisted PIDs under both aliases, exact known-PID counter-evidence, existing or dangling aliases, failed metadata access, partially read invalid lists, and unchanged earlier output/device/source/cleanup expressions. Failure receipts remain BLOCKED/FAILED with sanitized exception types. These are **pure mocks**, not OS/process/device tests. Source snapshots, runnable tests and JSON receipts are under `evidence/final-preservation-secondary-mafia-cont/`.

The root-shape regex is not itself an ownership attestation or a general untrusted-path parser. Approval is for the explicitly attested audit receipt inputs reviewed here. No broader filesystem ownership, future-process guarantee, or absence of unrecorded secondary roots is inferred.

## Additive cleanup evidence

Read all94lines of root's secondary-cleanup receipt and all79/95lines of whodunit's before/after checks. Reconciled exact hashes and selected original1160-line receipt fields, including the byte-identical67921→68144 ownership entries, four metadata paths (two FIFOs/two directories), common UID, and birth times within the recorded original cycle.

Original runner-only cleanupPASS was incomplete. Independent14:26:02Z review correctly failed because the exact ibtoold root remained. Root's additive14:30:54Z correction removed the two attested FIFOs and empty directories after recorded metadata/PID/holder checks. The independent14:36:10Z postcheck records73attested PIDs absent, all17generated output paths absent, exact primary/secondary/simulator paths absent, and global cache targets preserved. Both earlier receipts and the failed check remain intact. Thus **combined current cleanup evidence passes after correction**, not “the original immediate cleanup was complete.” Earlier cycles with no attested secondary-root inventory retain that explicit limitation.

This reviewer issued no real process/path-existence query for these cleanup records. `secondary-cleanup-reconciliation.json` binds the original, failed, corrective and postcheck hashes; the separate final root checker must still execute.

## Historical review preservation

Independently verified archival binding for coverage line447: exact1947-line canonical snapshot SHA`cc8d768…` matches the old reviewed version. Before/after519-line ledgers differ only at447, adding `version_snapshot_ref` and its administrative note. Reviewer, timestamp, ranges, status, findings and evidence did not change. All five archive hashes match `history.json`. `archival-reconciliation.json` and the exact changed line preserve compact reconstruction evidence. This does not approve new canonical/report contents.

No production, Git, signing or Store changes were made. No build/native/device/server process or generated build outputs were created by this review. Final generated-ledger approval remains pending separately.
