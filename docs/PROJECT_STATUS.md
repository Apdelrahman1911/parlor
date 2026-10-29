# Project status — October 29, 2026

## Qualification checkpoint (14:05 UTC)

[PR #287](https://github.com/Apdelrahman1911/parlor/pull/287) normally merged at
13:57 UTC as `539f8073fde9e7f168c64890ff89a416ab5551f0`. Fresh
[PR40877009596](https://github.com/Apdelrahman1911/parlor/actions/runs/40877009596)
independently qualifies every required producer/the original Apple join,
23 Swift methods, **708 source-derived KMP Simulator methods**, all 359 ordinary
readiness methods and exact source/artifact/native/output custody. Qualification
SHA256 `1f1dd059ec5fd47523a4785743340a8ef158426ea3135686e78821044ffc8e08`.
Fresh [automatic main40886687896](https://github.com/Apdelrahman1911/parlor/actions/runs/40886687896)
is running, **not yet post-merge qualified**. Main/PR tree is exactly
`7ff23d909ed10e0807b3352f0227f7183abef211`; source identity does not replace fresh
post-merge verification. Required protections remain active; no bypass occurred.

Previous [PR #286](https://github.com/Apdelrahman1911/parlor/pull/286) and its
[automatic main40867943050](https://github.com/Apdelrahman1911/parlor/actions/runs/40867943050)
independently qualify `2f7594c9`: eight producers/Apple join, 23 Swift/685 KMP
Simulator methods and custody. Scoped worker issue #285 and coherent-observation
integration #283 are resolved, not original input causality. Older failed attempts
stay failed. PR #256 already merged as `81ee9b7b`; never replay that integration.

## Integrated recovery corrections; physical acceptance remains

[Selected cold-peer recovery](audits/2026-10-29-COLD-RECOVERY-INTEGRATION.md)
binds Resume/Leave to the observed membership, allows one foreground startup
attempt and retains explicit Retry. [Host intent](audits/2026-10-29-HOST-OPENING-INTENT.md)
prevents repeated failed Host operations after root recreation in all six games.
[Elapsed budgets](audits/2026-10-29-RECOVERY-ELAPSED-BUDGET.md) prevent clock
rollback or delayed timers extending recovery; Android elapsedRealtime, Desktop
nanoTime and Apple's exposed continuous RAW clock fail closed. #252 retains the
real-mobile acceptance gate; no new dependency, protocol, polling, wake lock,
thread or host migration is introduced.

Exact `b439cefa` strict local `productionCheck allTests` passes 1,889 Desktop
(three existing LAN skips), 681 Android per Debug/Release, 120 GitHub-test,
766 release controls (one platform skip), Detekt/lint/R8/unsigned AAB and inventory/
shell gates. All 295 changed Desktop methods in 15 files match actual XML.
Three current-source real P2pKit Linux Resume pairs verify six natural zero exits,
seat/rotation/revocation/version assertions and cleanup. Surviving same-machine
JVMs and in-memory stores are not physical phones or process-death verification.
Earlier OOM, unavailable Mach imports and Android JVM SDK-stub failures remain
retained; corrected real-clock tests execute on Desktop and iOS source sets.

## Source-copy repair prepared, not yet integrated

[Draft #288](https://github.com/Apdelrahman1911/parlor/pull/288) narrowly fixes the
existing [main readiness source-copy consumer](audits/2026-10-29-READINESS-COPIED-INPUTS.md).
Original inspection accepts hidden invalid Kotlin while the actual compiler
identifies it and fails. Exact recorded Gradle output paths replace hidden/basename
pruning; intake/hash/symlink checks remain strict. Original RED and four reader
mutations are retained. Combined `0e8341ea` passes all strict local gates above and
**362 ordinary readiness methods**, three Apple-only Linux skips. Reader bytes
remain unchanged through preparatory local alignment with merged recovery main.

The remote PR still targets the feature branch; **never merge there**. Actual-main
qualification, final-source rechecks, main retargeting, fresh full hosted/native/
readiness qualification, protected merge and automatic actual-main verification
remain. Keep #284 open: this is not dormant PIN03 harness integration or #267/#276
root-cause resolution. Local reuse is not a controlled cold-build speed benchmark.

## Remaining acceptance gates

- **Release blockers:** #230 Store identity/ownership; #258 iOS Strict Complete
  Protection (0 PASS/6 FAIL); signing, legal and external Store approvals.
- **Real mobile recovery:** #251/#252/#253 Android/iOS admission, selected-seat
  restoration/deletion, locked storage, calls/lock/Wi-Fi/process death and energy.
  Host process death still ends the room; no host migration/indefinite iOS hosting.
- **Six-game journeys:** #259/#260 physical accessibility, private-information
  isolation, complete UX/gameplay and CPU/battery/thermal acceptance.
- **Native/tooling investigation:** #267/#276 original input causality;
  #283/#284 separately scoped observer acceptance/source-copy integration;
  #285 worker custody is accepted, not proof of original input causality.
  PR #282 remains draft/unmerged; diagnostic green is not production qualification.
- **RPC:** retain reviewed P2pKit 0.7.0-rc3 single-connection messaging.
  Fresh core/LAN/RPC Maven metadata at 12:58 UTC still lists 0.8.0-rc7 published
  October 23, before upstream #524 merged October 25; mobile comparative benefits
  are unmeasured. See [the RPC/lifecycle decision](audits/RPC_AND_LIFECYCLE_2026-10-25.md).

Only standard free public-repository runners are used. No extra agents,
paid infrastructure, weakened verification or Store release. Historical results
are preserved [verbatim here](archives/history/PROJECT_STATUS_2026-10-29_5456d76a.md);
use live linked checks and exact-source reports, not those checkpoints, as evidence.
