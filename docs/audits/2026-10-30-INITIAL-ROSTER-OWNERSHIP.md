# Initial roster ownership — October 30, 2026

## Confirmed finding and scope

[Issue299](https://github.com/Apdelrahman1911/parlor/issues/299) records a **P2
state-consistency/API defect** in Mafia and Whodunit. At original `9cd71966`,
`createInitialState` retains the caller's `SessionConfig.players` list. Kotlin's
read-only `List` interface does not prevent its owner mutating a `MutableList`.
Replacing/removing players after creation rewrites canonical state without a
reducer action. Mafia's separately mapped public roster then disagrees with
canonical players; Whodunit's public roster follows the same aliased input.

Reproduce using a valid mutable roster: create the initial state, clear the
input, and compare `state.players` with its original snapshot. An independent
JVM probe confirms all12 supported Mafia counts and five Classic Whodunit
counts. Four new common JUnit methods fail against the original initializers
with **four assertion failures, zero errors/skips**. Baseline log SHA256:
`e34c96fc4262633983e4728398a28e6f507c6e99f2b92285e504aa96fa11a45d`.
This is not evidence of a transport authorization bypass, secret leak, native
crash or observed real-user incident. Existing mutable-field issues were
reviewed before creating299; closed redaction observation57 remains separate.

## Correction and regression coverage

Code commit `4564ffe2b967fa11e5c9488811b3f3c416836b76` copies the roster once
at entry, validates that owned snapshot, and uses it consistently in canonical
and public initialization. No rules, protocol/schema, dependency, CI, Release
or identity changes. `Player` values are immutable; this does not promise deep
immutability of every output collection or an atomic concurrent-input snapshot.
Callers must not mutate input concurrently during initialization.

Each game's `src/commonTest/.../InitialRosterOwnershipTest.kt` covers caller
replacement/removal and reuse of a configuration without rewriting its earlier
state, for every supported count and both Whodunit modes. Synthetic public
players only; existing reducer/projection/privacy tests remain unchanged.

## Verification and remaining acceptance

Actual corrected-source preparation passes complete affected suites:
**687 Desktop /40 Android Debug /40 Android Release**, zero failures/errors/skips,
with every prior exact method retained and four common additions. Plain Detekt
and separately executed explicit `detektMetadataCommonMain`/`detektDesktopTest`
pass. Test log SHA256:
`1e0bf290981c1fefabc540bd2666b78efc795dca924b7a7d8777273bdb48f293`;
type-aware log SHA256:
`1724351e47de821eb59e71cee20bf7b19bba0bd75207637b81255a7305f09019`.
The263.7-second cached local run is not a hosted or mobile benchmark.

Full final-clean root gates, fresh hosted/native checks, protected integration
and distinct actual-main verification remain required. Keep299 open until that
scoped acceptance; physical multiplayer/lifecycle acceptance remains260/251–253.
Historical preparation does not qualify a later checkout.
