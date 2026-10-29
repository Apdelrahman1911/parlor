# Project status — October 29, 2026

## Verified checkpoint

[PR #288](https://github.com/Apdelrahman1911/parlor/pull/288) normally merged as
`5958f9289e6de77273eedc459cd54a2869423fb5` at 16:53 UTC. Fresh
[PR40902449629](https://github.com/Apdelrahman1911/parlor/actions/runs/40902449629)
independently qualifies all eight producers/the original Apple Release join,
23 Swift methods, 708 source-derived KMP Simulator cases and all362 readiness
methods without skips. Exact source/artifact/native/output custody matches tree
`845c4313d08fe563a8bd9a24fbcfaea9513bf703`; qualification SHA256
`13682d4c3955add39e2e4c077c9b5aea47f9a2c281cdc6850788c21b6004666c`.
Actual UIKit runtime: **iOS18.5/iPhone16Pro**, not SDK26.2 or physical devices.

[Distinct automatic main40916055035](https://github.com/Apdelrahman1911/parlor/actions/runs/40916055035)
is still running at this preparation checkpoint. Three Desktop host lanes pass;
Apple/Android completion and independent post-merge qualification remain. Never
substitute PR success for actual-main acceptance. Protections remain active.

Previously accepted [recovery PR #287/main539f8073](https://github.com/Apdelrahman1911/parlor/actions/runs/40886687896)
qualifies all producers/Apple join,23Swift/708KMP Simulator/359readiness and
exact custody. PR #286 is accepted; scoped #283/#285 are closed. PR #256 already
merged as `81ee9b7b`; never replay those integrations or relabel earlier failures.

## Integrated application and verification repairs

- [Cold-peer recovery](audits/2026-10-29-COLD-RECOVERY-INTEGRATION.md) binds
  Resume/Leave to observed membership, attempts startup once in the foreground
  and retains explicit Retry.
- [Host-opening intent](audits/2026-10-29-HOST-OPENING-INTENT.md) prevents implicit
  failed Host replay after root recreation in all six games.
- [Continuous elapsed budgets](audits/2026-10-29-RECOVERY-ELAPSED-BUDGET.md) prevent
  clock rollback/delayed timers extending recovery.
- [Existing readiness copied-input repair](audits/2026-10-29-READINESS-COPIED-INPUTS.md)
  detects hidden/nested inputs and rejects symlinks before output pruning.

Exact PR #288 source passes strict local checks:1,889Desktop (three existing LAN
skips),681Android perDebug/Release,120GitHub-test,766release controls (one platform
skip),Detekt/lint/R8/unsigned AAB/inventory/shell. These Linux results do not execute
Apple APIs. Fresh actual main5958f928 also passes three real P2pKit Linux host/peer
JVM Resume pairs: six natural zero exits, selected seat, rotation/revocation,
game/version rejection and owned cleanup. Receipt SHA256
`daeb997b4249695c4218d6989ac9f4c9eca3d43013f3a0867dbd01efb02ba89c`.
Surviving same-machine JVMs with synthetic in-memory stores are not phones,
process-death restoration, gameplay or mobile energy measurements.

## Native-input investigation and separate diagnostic integration

[Diagnostic40889770560](https://github.com/Apdelrahman1911/parlor/actions/runs/40889770560)
has completed. Frozen original app9d4f369d/harness91eec0ec executes all six
original/Compose/native EN/AR journeys successfully on **actual iOS26.2/23C54,
iPhone17Pro, ARM64**, Xcode26.3/17C529. This does not identify the original input
cause, execute failed-only capture/export callbacks, prove full60-journey neutrality
or qualify current main/physical devices. PR #282 remains draft/unmerged.

#267 was accidentally closed by negated PR-description closing-keyword wording
at merge. [Its state/wording correction](https://github.com/Apdelrahman1911/parlor/issues/267#issuecomment-6503091495)
is read back; #267/#276 remain open and original failures remain failed.

The [separate diagnostic integration](audits/2026-10-29-INPUT-DIAGNOSTIC-INTEGRATION.md)
is prepared locally, not published/accepted. It retains the reviewed copy-only
harness, repairs independently reproduced output-symlink/suffix gaps and includes
all artifact/worker controls in ordinary readiness. Keep #284 open pending fresh
committed-source/native qualification, protected integration and actual-main checks.

## Outstanding acceptance gates

- **Release:** #230 Store identity/ownership; #258 Strict Complete Protection
  remains **0PASS/6FAIL**; signing, legal/security and external Store approvals.
- **Physical recovery:** #251/#252/#253 Android/iOS admission, selected-seat
  restoration/deletion, locked storage, calls/lock/Wi-Fi/process death,
  simultaneous recovery and energy. Host death ends the room; no host migration
  or indefinite iOS LAN hosting.
- **Six-game acceptance:** #259/#260 complete physical LAN/gameplay/accessibility,
  hidden-information isolation, large text/RTL and CPU/battery/thermal matrices.
- **Native/tooling:** #267/#276 original input causality; #284 diagnostic integration.
  Closed #6 covers separate-process transport automation, not physical phones.
- **RPC:** retain reviewed P2pKit0.7.0-rc3 single-connection messaging. Fresh correct
  Maven RPC metadata lists0.8.0-rc7 published before upstream #524 merged; no
  reproducible published implementation/mobile comparative benefit is established.
  See [the decision](audits/RPC_AND_LIFECYCLE_2026-10-25.md).

Only standard free public runners; no new agents, paid infrastructure, cache/billing
increase, weaker verification or Store release. Historical checkpoints remain
[preserved](archives/README.md), not certificates for a newer checkout.
