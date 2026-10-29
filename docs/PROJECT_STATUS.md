# Project status — October 29, 2026

## Qualification checkpoint (11:20 UTC)

Main is `7c87a872` ([PR #281](https://github.com/Apdelrahman1911/parlor/pull/281)).
Its [automatic main run](https://github.com/Apdelrahman1911/parlor/actions/runs/40759303257)
failed the Arabic iOS exact-input check; an older catalog-main pass does not
qualify it. [PR #256](https://github.com/Apdelrahman1911/parlor/pull/256) was
already merged as `81ee9b7b`; do not replay that integration.

[PR #286](https://github.com/Apdelrahman1911/parlor/pull/286), head `d4219ff4`,
is draft/open. [Fresh automatic verification](https://github.com/Apdelrahman1911/parlor/actions/runs/40856349281)
is in progress, not qualified: four Desktop/Android producers pass; four Apple
producers continue. Preserve all eight producers, optimized Release architectures,
23 Swift methods, 685 KMP Simulator cases and the original Apple join. The
previous PR run remains failed. Normal protected merge and fresh actual-main
verification are required; no blind rerun or approval/check bypass.

## Implemented, not yet integrated

The [selected cold-peer recovery candidate](audits/2026-10-29-COLD-RECOVERY-INTEGRATION.md)
combines membership-bound Resume/Leave, foreground-only one-shot startup and
explicit-Retry restoration. Its additional [host-intent correction](audits/2026-10-29-HOST-OPENING-INTENT.md)
prevents root recreation silently repeating a failed Host operation in all six
games. Track the confirmed reproduction and fix in [#252](https://github.com/Apdelrahman1911/parlor/issues/252#issuecomment-6494991375).

Exact local `5456d76a` passes strict `productionCheck allTests`: 1,880 Desktop
cases (three existing LAN skips), 677 Android per Debug/Release, 120 GitHub-test,
766 release controls (one platform skip), Detekt/lint/R8/unsigned AAB and shell/
inventory gates. All 143 annotated methods in 11 changed classes are independently
matched to actual XML. Ordinary readiness: 359 methods, three Apple-only Linux
skips. A kernel-OOM first attempt remains failed; successful local serial execution
keeps every gate. This is valid unaffected-input reuse, not a cold speed benchmark.

The candidate is unpushed. Accepted-base integration, final documentation-source
rechecks, fresh free hosted qualification, normal protected merge and actual-main
verification remain. Its source requires 703 KMP Simulator methods; this is not
an executed native result. Earlier three-pair Linux LAN evidence is scoped to
its recorded network bytes, not physical phones or complete gameplay acceptance.

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
