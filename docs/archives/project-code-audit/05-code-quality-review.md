# Code quality review

## Strengths

- Consistent Result / sealed error types in core.
- Exhaustive `when` on protocol and actions (fail-closed unknown).
- Strict JSON/CBOR (`ignoreUnknownKeys = false`).
- Detekt `maxIssues: 0`, no baseline.
- Shell types in composeApp are `internal`.
- Test names are generally behavior-focused; many suites actually drive reducers.

## Debt that will cause defects

- **God files:** `P2pKitRoomTransport.kt`, `AuthoritativeSessionCoordinator.kt`,
  `ProcessMultiplayerSessionOwner.kt`, large game flows. Review isolation is poor.
- **Spec drift in kdoc:** Mafia “paused while disconnected” (GR-003);
  Whodunit `droppedPlayers` “never written” (GR-004). Comments lie.
- **Dead API:** `WhodunitEvent.PrivacyConcernRaised` never emitted (GR-005);
  `LocalRoom.rejoinToken` always null on production peer (SN-011);
  `AdmissionAccepted` deprecated and rejected.
- **False-confidence tests (GR-010):** planted validator-illegal fixtures;
  tests pass without `requireValid`.
- **Public test fakes in commonMain (AR-004).**
- **Default-public KMP APIs** across shared modules.
- **@Suppress("LongMethod") / LargeClass** on real production types rather than
  splitting.

## Style (only where it matters)

- Magic constants duplicated (256 KiB snapshot cap in Whodunit codec vs
  `MAX_SNAPSHOT_PAYLOAD_BYTES` imported by Mafia — ST-008).
- `uppercase()` without locale on chrome (UI-003).

No TODO/FIXME in production Kotlin (xxxl spacing false positives only).
Three `@Ignore` tests are intentional LAN gaps, not forgotten stubs.
