# Existing review-inventory tooling and reports

Reviewer `/root/mafia_cont`; baseline `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Audit-only. No production source, existing review artifact, Git branch/index, credential or release state changed. No generator invocation, test, Gradle, Xcode or application execution by this reviewer.

## Coverage and inspection method

Full text reads: generator408, review README42, findings register289, overrides CSV5, generator tests125. The first findings-register output truncated inside92–94;88–118 was then reread untruncated before completion was recorded.

Existing generated inventory: physical lines1–55 read raw. All628 data rows and every cell were then inspected using a lossless column dictionary plus source-line/path/reference table (`evidence/review-inventory-inspection/`). Every unique value and every row reference was read. This is **generated-CSV structured inspection**, not a claim to have read the referenced628 implementation files through that inventory or to have printed all629 CSV lines verbatim. Strict CSV parsing found exactly9 fields per row,628 unique paths, all628 matching the actual tracked path set, no missing/extra paths; all status cells are literal REVIEWED. Source hashes and inspection details are preserved.

Fresh source cross-checks: complete root build204; release validator104; Xcode project529; scheme103; XCTest32; Java release smoke62. Partial reads: workflow_contract475–545, workflow tests285–345, app Gradle268–304. These prove actual gate and native-test ownership rather than assuming the generated labels.

## Generator branch/dataflow review

- Git-root and Git-log subprocesses fail on nonzero exit; no shell interpolation. `last_changes` scans nonmerge commits since fixed baseline, keeping the first seen latest path attribution. It is history/subject metadata, not tree/blob hashing. `--no-merges` intentionally stabilizes a synthetic PR merge; nonconflicting synthetic merge test verifies that contract, not arbitrary conflict-resolution identity.
- Module/source-set/classification/reachability/consumer/disposition functions are deterministic path heuristics. Fixture modules explicitly override reachability as nonshipping. Most current module/source-set rows align with actual graph, but INV-C02 identifies exact native-test misclassifications; source compilation labels do not prove runtime binding or post-shrinker inclusion (e.g. optional Ktor adapter remains unbound).
- Historical document set and review-infrastructure set deliberately preserve reports without importing them into shipping runtime. Their generated REVIEWED/banner statuses are not independent confirmation of the text's correctness.
- Override header, full lowercase40-digit SHA syntax, nonempty description and duplicates are checked; malformed missing cells fail rather than silently accepting. Four existing override rows are syntactically valid. Overrides apply only to a path's latest matching commit; they are manually trusted attribution text, not proofs that a defect was fixed. They do not validate Git object existence/history by themselves. No security conclusion is inferred from that trusted-source limitation.
- Tracked paths come from `git ls-files --cached`; untracked files are intentionally ignored, and output is included. Current tracked names are ordinary ASCII paths. Exotic Git path quoting/newline behavior was not reproduced and is not a current defect claim.
- Rendering emits fixed nine-column CSV, unconditional REVIEWED and either latest remediation/override/no-finding text. It never reads application content or a reviewer attestation (INV-C01). New indexed names trigger structural inventory updates; same-path staged/working-tree byte changes do not.
- CLI allows check mode plus optional repository-contained output. Resolved output must remain under Git root. Check compares exact generated text; write mode creates parent, writes sibling temporary then replaces output. Current task did not call either mode or overwrite any old report.
- `validate_release_system.sh` invokes py_compile, all release tests, workflow contract and generator check before pinned tool downloads; root Gradle aggregate runs it. Its temp-tool trap preserves failed exit status and removes the owned temporary directory. Python bytecode caches are separate generated outputs that root's shared cleanup lane must remove if produced. Full downloader code was read: HTTPS/download bounds, digest verification, tar traversal/link checks, single bounded executable and failure propagation. No new downloader finding originated here.

## Test quality and counter-evidence

Generator tests assert repeated rendering, independence from an untracked file, one valid/full versus abbreviated override SHA, and equality across a synthetic nonconflicting merge. They use isolated temporary Git repositories and do not mutate Parlor. They do not test a changed existing source blob, newly indexed review attestation, duplicate/blank override cases, actual native test classification, real merge conflict resolution, or unusual file names. The workflow inventory test proves a command token is present exactly once and full history is required in the gate job; it does not elevate path equality to human review evidence.

README and findings register explicitly put exact-head receipts outside the generated CSV and retain Store/device blockers. No reviewed-status field consumer was found in Store authorization logic. Current publication is separately blocked. Thus INV-C01 is proposed as evidence/documentation limitation, not a signing bypass, nor an allegation that prior reviewers fabricated their work. Fresh audit coverage is wholly separate.

Every dated finding/closure/commit row in the prior register was read. Its CLOSED/PASS labels remain historical claims requiring new source/test evidence; they were not copied into this audit's confirmed register. Current M-C01 and other new candidates are not disproved by prior broad “complete invariant” language.

## Candidate status

- INV-C01: generated REVIEWED/status freshness is not source-identity or reviewer-attestation proof. Proposed TEST/EVIDENCE GAP / documentation overstatement, pending independent `/root/whodunit_cont` validation.
- INV-C02: current inventory calls the separate native XCTest shipping app source and Java instrumentation source a resource; actual build isolation is correct. Proposed Low DOCUMENTATION MISMATCH, pending independent `/root/whodunit_cont` validation.

Related generator freshness commit `033a56351c57fe382aa5d42ff0cb1ea94385d0da` was inspected: it intentionally added --check integration, full-SHA overrides, tracked-only enumeration and nonmerge stability, retaining unconditional REVIEWED and the existing classifiers. These are pre-existing tooling behaviors, not new app regressions.

No test result is claimed by this review; root owns executions and cleanup. Final working tree remains only the preserved untracked paths plus task-owned audit evidence, with no tracked modifications.

## Independent adjudication update

INV-C01 is independently validated Low TEST/EVIDENCE GAP (wording overstatement), and INV-C02 independently validated Low DOCUMENTATION MISMATCH by `/root/whodunit_cont`. See `validations/INV-C01-whodunit_cont.md`, `validations/INV-C02-whodunit_cont.md` and hash/range receipt JSON. Both full reports were reopened/read; neither belongs in the application-defect count. Earlier pending labels above describe the original checkpoint, not current status.
