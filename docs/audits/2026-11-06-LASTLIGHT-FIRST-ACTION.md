# Last Light: show the first hand action at large text

## Reproduced layout defect — #259 / #260

The complete game wrapper at 320×740dp / 200% text places **Show hand**
below the initial viewport. The existing English/Arabic journeys scroll before
checking control visibility, so they did not detect the excessive introduction.
The action remains reachable by scrolling; this is not broken gameplay.

Against unchanged production source at `2807c6fd`, strengthened assertions fail
in both languages before any scroll: English Reveal spans y767–843 and Arabic
y722–836, beyond the viewport's y740 boundary. The original XML, two screenshots,
source patch and log are retained. No compile or infrastructure failure is
counted as reproduction.

## Correction and regression coverage

`GameTable.kt` omits only the decorative opening heading (“Set the tone.” /
“ابدأ الجولة.”) at the existing enlarged-text breakpoint. The complete opening
instruction, turn, rank, round, privacy cover and reveal action remain. Regular
text, claims, game rules, host authority and action handling are unchanged.
The user's font scale is never reduced.

The existing complete English/Arabic 200%-text journeys now require the entire
initial Reveal target in view before scrolling, at least56dp high, with its
complete unellipsized label. Existing card-selection, play, result and rematch
assertions remain. This complements the earlier411dp game-content-only case;
it does not replace or weaken it.

```bash
./gradlew :game-modes:last-light:desktopTest :game-modes:last-light:detekt \
  --dependency-verification=strict
```

Fresh local JDK21/wrapper execution passes all **204 module methods**, zero
failures/errors/skips, and Detekt. All109 synthetic renders are retained.
Original corrected English/Arabic initial frames were inspected at full size:
the instruction and whole Show hand action fit, words remain complete, and no
private card is revealed. Scoped test receipt SHA256:
`85410796f3acbe6b6ae7f467f1b167defdbadbabbd86c3141fddc3a88f51cb5e`.

## Qualification boundary

This is a scoped local fix, prepared alongside the existing
[Word Impostor](2026-11-06-WORD-IMPOSTOR-LARGE-TEXT.md) and
[Ghamza](2026-11-06-GHAMZA-LARGE-TEXT-REVEAL.md) improvements. Full hosted
checks, protected PR review/merge and distinct main qualification remain
required. These two inspected frames do not certify all109 renders, native
gestures, physical screen readers or device gameplay. Wider #259/#260 acceptance
remains open; no previous failed workflow is reclassified as passing.
