# WD-T1 — independent validation

**TEST/EVIDENCE GAP**, Low, not a demonstrated application defect. Finder `/root/whodunit_cont`; validator `/root`; main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

Reopened the entire WhodunitActionCodecTest202lines and production WhodunitActionCodec94lines, the projection test245–284 with its setup, projection decoder127–146, and snapshot-test/codec guard paths. Paths are beneath `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/`.

`desktopTest/kotlin/com/parlor/games/whodunit/action/WhodunitActionCodecTest.kt:182–190` searches for the first byte'p', not the p1 field. The default `type` polymorphic discriminator (also asserted155–163) precedes data. Lossy UTF8 leaves ty�e instead of the discriminator; strict polymorphic JSON deserialization still fails. Generic assertFails cannot show which boundary rejected it. The projection fixture at `multidevice/WhodunitPeerProjectionBoundaryTest.kt:271` becomes `{�}` with replacement, which remains invalid JSON; snapshot fixture at `snapshot/WhodunitSnapshotValidationTest.kt:135–138` becomes `�(`, also invalid JSON. Both remain rejected without the claimed encoding-specific check.

Counter-evidence: production action codec40, peer bridge133/140 and snapshot codec58 explicitly use throwOnInvalidSequence=true. Other assertions in these tests are meaningful. This is a grouped fixture/assertion weakness, not proof that any production decoder accepts malformed UTF8. No mutation test was executed for this candidate.

Use malformed bytes inside an otherwise-valid data string and assert the encoding-specific error, or mutation-test an isolated copy so later JSON/semantic guards cannot falsely satisfy the assertion. Keep strict production validation unchanged. Hash/range receipts accompany this review.
