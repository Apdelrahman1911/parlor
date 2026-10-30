# Host coordinator input ownership — October 30, 2026

## Confirmed finding and scope

[Issue301](https://github.com/Apdelrahman1911/parlor/issues/301) is a **P2
in-process API/state-consistency defect**, not a demonstrated networking exploit.
At merged main `71c3f7cc`, `HostAuthoritativeSessionCoordinator` retains its caller's
peer `Set` and `startSession` retains the offered player `List`. Read-only Kotlin
interfaces do not prevent their owners mutating the underlying collections.

Removing a caller-owned peer suppresses its otherwise valid command/acknowledgement.
Adding another ID allows its injected command to reach the reducer callback, while
no outbound lane exists for that ID. Mutating a roster after its first offer changes
the retained/retried offer under the same start ID, potentially breaking peer
validation and start/rejoin consistency. These reproduce with synthetic `LocalRoom`
injections; current shipping bridges derive private sets. No remote actor's ability
to mutate those inputs, private wire delivery or real-user incident is established.

Open/closed mutable/immutable/roster/coordinator/membership searches and related
#299/PR300, closed #90/#153/#235/#91 and #251/#260 were reviewed before filing.
Game-initializer ownership (#299), valid commit/ack semantics (#90), bounded mailbox
behavior (#235), and physical recovery/privacy acceptance remain separate.

## Correction and regression coverage

Code/test commit `7518ad1c8d2dd3872b9dd3b4e43544919eb9f9cc` captures one owned
`remotePlayerIds` set before outboxes, authorization, start barriers and foreground
validation are constructed. Every later membership read uses that distinct property,
not a constructor parameter captured by an initializer. Each start offer copies
its roster before validation and mailbox publication. Player values are immutable.

Three common-coroutine methods exercise caller seat removal, caller seat addition
with projection/outbound consistency, and roster replacement/removal across actual
scheduled offer retries. All existing methods/assertions remain. This is isolation
from subsequent input mutation, **not** deep output immutability or atomic copying
while another thread modifies the input. Callers must not mutate during copying.
No rules, protocol4.2, identity, dependency, CI, Release optimization or recovery
ownership changes are introduced.

## Executed evidence

- Exact original main production plus regressions: **11 Desktop methods,3 exact
  assertion failures,8 existing passes,0 errors/skips**. The `*caller*` filter also
  selected eight older methods. The first reader expected three total and stopped;
  corrected source-derived readback accepts the same retained XML, not a rerun.
  A preliminary helper lookup stopped before writing results. Original failures
  remain failed. Baseline log SHA256:
  `cfb4fc35473b8f09c61cbc8e320c58fdd3177bb28bdce264d80930bff3b946bc`.
- Corrected complete affected suites: **166 Desktop /163 Android Debug /163 Android
  Release**, zero failures/errors/skips. Every prior method plus three additions per
  target is independently verified. Plain Detekt and explicit
  `detektMetadataCommonMain`/`detektDesktopTest` pass. Log SHA256:
  `b8813a30905a98f7cb90dd02d9c2a09b7b2632377ee1fdeb33d92f1a06d683c7`.
  The73.8-second cached local invocation is not a hosted/mobile benchmark.

## Remaining integration gates

Run fresh final-clean full strict root/security/release gates after documentation
and inventory commits. Require complete hosted/native/all-architecture verification,
source/artifact/cleanup readback, normal protected integration and distinct actual-main
acceptance before closing301. The719 native methods are a source expectation,
not execution. Current PR298 actual-main verification remains separate at this
preparation. Physical lifecycle/gameplay/energy/accessibility and Store acceptance
remain #251–253/#258–260/#230; #267/#276 input causality is not repaired here.
