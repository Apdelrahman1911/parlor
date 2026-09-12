# WD-T1 — malformed UTF-8 action test corrupts a key, so remains green if boundary is weakened

> **Final independent disposition (2026-09-05): TEST/EVIDENCE GAP — Low.** Validator `/root`; see `validations/WD-T1-root.md`. Narrow negative-test assertion coverage, not a confirmed application privacy/codec exploit. Original candidate text below is preserved; its pending language is superseded. Administrative banner added by `/root/whodunit_cont` at `/root` request, not a new source approval.

Finder `/root/whodunit_cont`; independent validator pending. Proposed classification **TEST/EVIDENCE GAP**, Low; **not** a production defect. main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

Absolute file `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/action/WhodunitActionCodecTest.kt:182–190`.

The test encodes `CastVote(p1,p2)` then replaces the *first byte `p` anywhere* with invalid UTF8. It names this `playerByte`, but JSON starts with `{"type":...}`: first `p` is in the `type` discriminator key, not the player ID. Its only assertion is generic `assertFails`. Even if `decodeToString(throwOnInvalidSequence=true)` regressed to lenient replacement, the resulting JSON has `ty�e` instead of `type`; strict polymorphic deserialization fails for missing discriminator/unknown key. This test cannot distinguish the intended UTF8 guard from incidental structural rejection.

Counter-evidence: current production `WhodunitActionCodec.kt:38–43` *does* decode with `throwOnInvalidSequence=true`; no accepted-malformed-action application exploit is asserted. Other protocol/remote tests may cover strict decoding independently, but cannot make this particular assertion meaningful. Stable discriminator expectation also asserted in same test file155–163.

Recommendation: corrupt a known byte offset *inside* a data-field string and assert the specific encoding exception (or mutation-test the UTF8 check with an isolated copy); preserve syntactically valid JSON/key/type on lossy decoding. Avoid merely assuming an ID string containing U+FFFD is legal: if identifier validation would reject it later too, the test must assert UTF8-specific failure rather than generic failure. Root owns execution lane; this reviewer has not run a mutation test.

## Sibling found during full test reading (independent validation also pending)

`/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/multidevice/WhodunitPeerProjectionBoundaryTest.kt:245–284`, especially271, supplies bytes `{`, invalidUTF8 `0xC3`, `}`. With lossy UTF8 the result is `{�}`, which remains invalid JSON. The unchanged-last-good-state assertion therefore cannot isolate strictUTF8 decoding either. This has the same wrong-corruption-location test weakness; production peer decoder is still strict. Recommend corruption inside a known otherwise-accepted unrestricted string plus an assertion that isolates the expected decode error, accounting for identity/content validators that could independently reject replacement characters.

Another sibling: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotValidationTest.kt:135–138` asserts only any failure from bytesC3,28 (`�(` with lossy decoding), which is also invalidJSON independently. Thus named beforeJsonInterpretation strictUTF8 guarantee is not isolated. No production decoder weakening found.
