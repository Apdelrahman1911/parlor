# ROOT-C1 — Rejected corrupt-directory crash candidate

Administrative mirror created by `/root/whodunit_cont` at `/root` request. **No new independent source adjudication is made here.** Original finder `/root`; independent validator `/root/mafia_cont`.

- Classification: **FALSE POSITIVE for the concrete corrupt-directory crash**. Separate post-open Objective-C I/O-failure behavior remains a **TEST/EVIDENCE GAP**, not a confirmed crash or second counted finding.
- Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; absolute root `/Users/abdelrahman/Projects/parlor/`.
- Primary finding/rejection record: `reviews/root-storage-shell-notes.md:25–29`.
- Full independent validation: `reviews/independent-ROOT-C1-mafia-cont.md:1–21`, including actual reopened source, source-level guards and authoritative API references. This report remains the decision authority; it is intentionally linked rather than reclassified.

## Candidate and contrary evidence

The proposed input was a directory whose safe name ends `.snapshot.json`. The hypothesis was that `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt` would open it and reach exceptional legacy `readDataOfLength`, bypassing Kotlin error handling. Home metadata loading and file-store callers were independently traced by the validator.

Root's isolated macOS Foundation witness (`reproducers/filehandle_directory.m`; `evidence/filehandle-api-01/receipt.json`) compiled successfully but returned `opened_directory=false` with run exit3. The opener returned nil, reaching the existing ordinary Kotlin error branch at the production helper's405–406, not an exceptional read. The independent validator rejected this concrete crash claim. macOS API behavior is counter-evidence, **not iOS application/device verification**.

Apple legacy post-open read/close APIs can throw Objective-C exceptions; the actual Kotlin2.4.10 Foundation binding lacks exception wrapping. No deterministic application-reachable post-open failing-I/O or lock-transition reproducer was established. Keep that remaining scenario visible as unverified, without turning it into a confirmed crash. Suggested future work is a synthetic iOS error-injection/device lifecycle test; no code remediation is approved by this mirror.

No tests/builds/source changes were made to create this administrative mirror. Original native cleanup evidence remains in its referenced receipt.
