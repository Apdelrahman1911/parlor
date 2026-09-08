# CI run 34112913001 — independent diagnosis (draft-only)

Source: `f5ea8045bd903c255041b198f54aca67e893612f`, tree `b109c8ec6ec579b9197f449b36bda2c39105a652`. Reviewer: `/root/release_fix_review`. Production unchanged; draft patches have not been applied, compiled, or tested. Full hashes, references, limitations, and remaining verification are in `diagnosis-01.json`.

## Windows x64: test portability defect

The log identifies `ProductionUiAccessibilityContractTest` line221, with 95 composeApp tests/1failure. The common local reader returns raw text; line221 and222 require literal LF-separated source. A correct CRLF checkout fails both. The production iOS effect structure exists; this is not evidence that locale ownership is broken. Git2.55 semantics and the exact runner image's Git installer corroborate Windows CRLF defaults, but checkout bytes were not captured.

`drafts/windows-source-crlf-draft.patch` normalizes only CRLF at the source-reader boundary, leaves substantive assertions intact, and adds LF/CRLF/non-newline regression assertions. Actual Windows execution still required.

## Linux: strict lint triage worked as designed

The gate rejected exactly three new Kotlin plugin update advisories, with no missing rows. Kotlin2.4.20 published before this run. Proposed triage adds only those rows (35total/29DependencyUpdate), checks exact coordinates, and corrects documentation. No dependency upgrades or broad suppressions.

Security counter-evidence must accompany triage: **GHSA-r937-wjx7-w2jp / CVE-2026-53914 does include pinned KGP2.4.10.** The exact upstream fix restricts deserialization of KAPT incremental caches; the vulnerable reader remains in2.4.10. Source tracing shows that Parlor's configured build does not apply KAPT or its runtime. This is scoped non-reachability, not a repaired dependency or blanket safety claim. The companion draft guards current declared build sources and metadata; KAPT additions or changed advisory scope must reopen review. Its structural limits are documented.

## Existing verification evidence

- Linux arm64: strict `productionDesktopCheck`, all13 module desktopTest tasks executed,112actionable tasks/112executed; jobPASS.
- macOS x64: strict `productionDesktopCheck` plus Native distribution download, all13 module desktopTest tasks executed,113actionable tasks/113executed; jobPASS.
- These successful logs do not enumerate every passing test or skipped test. No invented totals.
- Linux common/Android failed at exact lint inventory; its release Python validator did execute174tests/OK. Managed-device smoke and artifact inspection steps were skipped, not passed.
- Apple job was still running at observation02; final qualified-toolchain/runtime/wrapper evidence is being collected separately. The whole run is notPASS.

## Next steps and cleanup

Root reviews draft and security applicability, then owns coordinated execution and cleanup. No production edit or build was performed by this child. No child-owned workers/build outputs remain. Only compact additive evidence/drafts were retained; prior receipts and all user work are preserved.
