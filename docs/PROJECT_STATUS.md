# Project status — October 29, 2026

## Qualification checkpoint (12:34 UTC)

[PR #286](https://github.com/Apdelrahman1911/parlor/pull/286) normally merged as
`2f7594c9b46ead62feb597c5211e9127164af4f8`. Its fresh
[PR run40856349281](https://github.com/Apdelrahman1911/parlor/actions/runs/40856349281)
independently qualifies all eight producers plus the original Apple join,
23 Swift methods, 685 KMP Simulator cases and exact source/artifact/native/output
custody. Fresh [automatic main40867943050](https://github.com/Apdelrahman1911/parlor/actions/runs/40867943050)
is running, **not yet post-merge qualified**. Two local merge preflight failures
preceded all GitHub mutation (receipt-schema mismatch, mistyped checksum);
reviewed distinct corrections preserved every protection check and AGENTS bytes.
Older main/PR exact-input failures stay failed; #267/#276 remain unresolved.
PR #256 was already merged as `81ee9b7b`; never replay that integration.

## Implemented, not yet integrated

The [selected cold-peer recovery candidate](audits/2026-10-29-COLD-RECOVERY-INTEGRATION.md)
combines membership-bound Resume/Leave, foreground-only one-shot startup and
explicit-Retry restoration. Its additional [host-intent correction](audits/2026-10-29-HOST-OPENING-INTENT.md)
prevents root recreation silently repeating a failed Host operation in all six
games. Track the confirmed reproduction and fix in [#252](https://github.com/Apdelrahman1911/parlor/issues/252#issuecomment-6494991375).

Exact local `0a8a04b2` passes strict `productionCheck allTests`: 1,888 Desktop
cases (three existing LAN skips), 681 Android per Debug/Release, 120 GitHub-test,
766 release controls (one platform skip), Detekt/lint/R8/unsigned AAB and shell/
inventory gates. All 294 annotated methods in 14 changed classes are independently
matched to actual XML. Ordinary readiness: 359 methods, three Apple-only Linux
skips. A kernel-OOM first attempt remains failed; successful local serial execution
keeps every gate. This is valid unaffected-input reuse, not a cold speed benchmark.

The candidate is unpushed. Accepted-base integration, final documentation-source
rechecks, fresh free hosted qualification, normal protected merge and actual-main
verification remain. Its source requires 707 KMP Simulator methods; this is not
an executed native result. Three current-source real P2pKit Linux Resume pairs
independently verify all six natural zero exits, exact assertions, 1,133 runtime
hashes and owned cleanup. These surviving same-machine processes/in-memory stores
are not physical phones, process death or complete gameplay acceptance.

The [continuous elapsed-budget correction](audits/2026-10-29-RECOVERY-ELAPSED-BUDGET.md)
additionally reproduces clock-rollback renewal and delayed-timer foreground work.
Final focused tests pass 19 methods without errors/skips; three restored mutation
controls reject missing elapsed spending. Full exact-source local checks pass;
final documentation-source and hosted qualification remain pending. Original
full checks rejected unavailable Mach imports; the
corrected exposed Apple RAW clock cross-compiles three iOS libraries and passes
the same 19 methods. Apple linkage/runtime and device sleep remain unverified.

## Remaining acceptance gates

- **Release blockers:** #230 Store identity/ownership; #258 iOS Strict Complete
  Protection (0 PASS/6 FAIL); signing, legal and external Store approvals.
- **Real mobile recovery:** #251/#252/#253 Android/iOS admission, selected-seat
  restoration/deletion, locked storage, calls/lock/Wi-Fi/process death and energy.
  Host process death still ends the room; no host migration/indefinite iOS hosting.
- **Six-game journeys:** #259/#260 physical accessibility, private-information
  isolation, complete UX/gameplay and CPU/battery/thermal acceptance.
- **Native/tooling investigation:** #267/#276 original input causality;
  #283–#285 separately scoped observation/source-copy/worker-custody integration.
  PR #282 remains draft/unmerged; diagnostic green is not production qualification.
- **RPC:** retain reviewed P2pKit 0.7.0-rc3 single-connection messaging.
  Published 0.8.0-rc7 still predates upstream #524; mobile comparative benefits
  are unmeasured. See [the RPC/lifecycle decision](audits/RPC_AND_LIFECYCLE_2026-10-25.md).

Only standard free public-repository runners are used. No extra agents,
paid infrastructure, weakened verification or Store release. Historical results
are preserved [verbatim here](archives/history/PROJECT_STATUS_2026-10-29_5456d76a.md);
use live linked checks and exact-source reports, not those checkpoints, as evidence.
