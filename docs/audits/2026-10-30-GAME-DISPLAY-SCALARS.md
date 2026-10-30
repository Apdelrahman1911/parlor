# Shared game-label validation — October 30, 2026

## Confirmed finding: the admission fix did not cover every game boundary

Follow existing [#293](https://github.com/Apdelrahman1911/parlor/issues/293), not
a duplicate issue. At `8e44800b`, admission rejects supplementary Unicode format
controls, but the independent Last Light, Egyptian Dominoes, Ghamza and Word
Impostor roster validators still inspect `Char.category` before accepting a
surrogate pair. A supplementary Cf scalar has two SURROGATE code units, neither
individually categorized FORMAT. Four copied loops therefore miss the same rule.

Reproduce with LANGUAGE TAG U+E0001 (`\uDB40\uDC01`): append it to the first
synthetic player's display name, or to a game-level player ID. The original
validators accept it. Initial game constructors and public/authority snapshot
decoders also accept the format-bearing name. The latter is demonstrated by
replacing that label in an otherwise canonical, genuinely encoded snapshot.
Mafia and Whodunit already use the corrected admission policy and are not claimed
to reproduce this gap.

**P2 defense-in-depth and maintenance defect.** Normal room admission already
blocks these names on the parent branch, so this does not establish remote
admission or authenticated-seat impersonation. Direct game construction and
snapshot trust boundaries nevertheless disagree. Physical rendering, VoiceOver
and TalkBack effects remain unverified. No private player information is used.

## Implementation and compatibility

- `shared/core/.../text/DisplayTextScalars.kt` owns well-formed UTF-16/scalar
  safety and the reviewed Unicode17 supplementary Cf set.
- `DisplayNamePolicy.kt` owns the existing 32-code-unit cap, trim-only input
  normalization, exact case-sensitive roster distinctness and UI sanitization.
- `RoomInputPolicy` keeps its existing public API and room-code implementation;
  name operations delegate to core. No new dependency or module is introduced.
- All six games use the same pure Kotlin name contract. Mafia/Whodunit no longer
  import networking just to validate labels; the other four delete copied loops.
- The four existing player-ID validators also enforce the complete scalar rule.
  Valid IDs are not trimmed, case-folded, renamed or regenerated. Unsafe stored
  data is rejected, never silently repaired into another identity or game.

Arabic, supplementary letters, emoji and variation marks remain supported.
Existing `Alice`/`alice` distinctness remains intentional (#169). No NFKC,
confusable filtering, protocol/schema change, gameplay rule or UI change is added.

## Actual verification and source boundaries

1. Original `8e44800b` production plus the first four regression files:
   **96 Desktop methods, seven expected assertion failures, zero errors/skips**.
   Three modules ran complete suites; Last Light was focused. This is retained
   as a failed baseline, not a qualified source.
2. Expanded regressions against the four **exact original** roster source files:
   **24 methods, twenty expected assertion failures, zero errors/skips**. Name,
   ID, constructor, public decoder and authority decoder each fail independently;
   the four valid-international-name controls pass. Other files are corrected
   preparation, so this is an actual original-function counterexample, not an
   original full-tree execution. Corrected source is hash-verified after restoration.
3. Restored correction: **60 focused Desktop methods and eight affected modules'
   all-source Detekt pass**, zero failures/errors/skips, 37 seconds with caches.
   This includes all existing eight `RoomInputPolicyTest` methods, 24 game-boundary
   methods and the full core suite. New core controls check every **1,048,576**
   supplementary scalar against an independent frozen127-Cf oracle, every BMP
   code unit and all2,048 lone surrogates. These loops are not extra JUnit methods.

Focused log SHA256:
`b66cb4c4cf9610f482e573bedabd4044f72404e27743af84fde4e75c5f45e9db`.
Local original/corrected source, XML and log receipts remain in the task's
untracked custody directory. No failed evidence is overwritten.

## Integration gates still required

Fresh final-source `productionCheck allTests`, full native/architecture/optimized
Release CI and protected-main acceptance remain separate gates. Focused JVM
execution cannot qualify Apple runtime behavior. PR294's original iOS Back-query
failure remains unresolved; this label correction is **not its causal repair**.
Do not relax the native predicates, retry CI blindly or merge a stacked branch
to avoid checks. Store and physical-device acceptance remain blocked separately.
