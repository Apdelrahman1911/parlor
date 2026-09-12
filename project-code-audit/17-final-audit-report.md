# Final audit report

**Tree:** on-disk working tree at 2026-09-01, HEAD
`3f825f9a6ec19bbac4542edc67ccc8d1455013da` plus dirty local edits.
**Method:** code/tests/config only. Repo markdown was not used as truth.
**Verdict: NOT READY for Store production.**

Suitable as a serious internal/LAN beta **after** F-003 is fixed and
two-device LAN is actually run. Not uploadable until F-001 is migrated.

## Executive assessment

This is a real two-game party-game container with a host-authoritative
LAN stack, not a prototype folder of screens. Layering and privacy
encoding are production-minded. Release identity, device evidence, and a
handful of reducer/UX defects keep it off stores.

## Reconstructed product scope

See `16-final-project-summary.md`. Shipping intent: Android + iOS,
Whodunit + Mafia, pass-and-play + same-LAN. Desktop = run/tests.
No accounts, no internet play, no spectators, no host migration.

## Architecture

**HOLD / good bones.** Graph matches the intended inward dependencies.
Complexity is only partly justified (unused engine types, public Ktor
adapter, Whodunit lobby in composeApp, 4.5k-line transport file).
See `04-architecture-review.md`.

## Implementation maturity

Game state machines are implemented, not stubbed. Mafia timers are
explicitly rejected. Solo is a disabled card. Remote content is a
dead production path. Several kdocs lie (Mafia pause, droppedPlayers).

## Platform readiness

| Platform | Code complete enough to run? | Store ready? |
|---|---|---|
| Android | Yes (unsigned) | No — identity + device + signing |
| iOS | Yes (framework + Swift host) | No — identity + device + signing + empty Testables |
| Desktop | Yes as harness | N/A — not a ship target; weak secret storage |

## Feature completeness

Core loops exist. Missing for a coherent first release: language-aware
case picker, honest Solo omission, iOS/Desktop back, Elimination
OpenVote alignment, Mafia disconnect contract, physical LAN proof.

## Security and privacy

Wire path is careful (actor stamp, projections, fail-closed codecs,
backup deny, no cleartext). Residual: 30-bit room code + host tap,
filename seed, unproven radio, TalkBack after handoff. See `07`.

## Correctness risks

Highest: F-003 Elimination OpenVote. Latent: F-019 killerWins shape.
Contract: F-004 Mafia pause. Test false-confidence: F-018.

## Networking / concurrency

Designed well (exact 4.2, no peer reduce, bounded queues). Risk: 8-slot
host mailbox (F-006). Unproven: real mDNS/TCP (F-007).

## Performance

Bounds exist. No profiler data. Mailbox + per-command snapshot encode
are the plausible jank/liveness issues.

## Storage / recovery

No SQL. Fail-closed codecs. iOS settings durability unchecked (F-016).
AEAD not enforced by the SnapshotStore type (F-017). Catalog identity solid.

## UI / a11y / loc

EN/AR Compose keys match. Case languages mixed. Several unlabeled
controls and non-scroll cards. A11y “tests” are source greps. Device AT
unrun.

## Testing quality

Strong reducer/codec/privacy unit coverage on JVM. Weak: illegal
fixtures, ignored LAN tests, no instrumented/UI tests, doc-contract
tests, `allTests` name overclaim on Linux.

## Build and release

Strict verification, Detekt 0, unsigned AAB + lint allowlist in CI.
**Identity deadlock is a hard Store blocker.** Promotion YAML is live
and fail-closed. Dirty tree adds MOBILE_RELEASE signing aliases.

## Confirmed strengths

- Host-only reducer; peers install snapshots
- Exact protocol + strict codecs
- Projection/privacy architecture with peer validators
- Offline bundled content with catalog identity tests
- Module graph + Konsist engine purity
- Backup/cleartext/permission restraint on Android
- CancellationException discipline on inspected paths
- Composition-root game registration

## Confirmed defects

F-001 through F-035 in `14-findings-register.md`. Critical: F-001.
High: F-002, F-003, F-007, F-008.

## Unverified / external

- Physical 2–3 device LAN all topologies
- TalkBack / VoiceOver EN+AR, 200% text, RTL
- Signed Play/TestFlight candidates
- OEM backup behavior
- Profiling
- Store privacy forms / legal
- Whether GitHub has manually disabled promotion workflows (not in YAML)

## Release blockers

1. F-001 identity migration (or accept “cannot publish”)
2. F-003 Elimination OpenVote if that mode ships
3. F-007 device LAN evidence if multiplayer ships
4. Signing credentials + non-colliding IDs
5. F-002 process control before flipping identity to verified

## Overall production-readiness verdict

**NOT READY.**

The codebase is closer to a disciplined unreleased product than to a
hackathon demo. It is not closer to “press promote.”
