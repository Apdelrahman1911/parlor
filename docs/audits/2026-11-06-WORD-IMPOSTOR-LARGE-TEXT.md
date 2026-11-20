# Word Impostor: show the next voting action at large text

## Observed problem and scope

The retained English/Arabic voting frames from
[42159600926](https://github.com/Apdelrahman1911/parlor/actions/runs/42159600926)
showed no player choice inside the initial 320dp-wide, 200%-text viewport.
Existing tests could scroll to those choices: this was excessive introductory
layout, **not unreachable controls or broken voting rules**. The repeated phase
label/title, wrapped navigation labels and lengthy instruction consumed the
initial screen. This is an incremental #259/#260 improvement, not completion
of the entire design or physical-accessibility campaign.

## Implemented correction

- At the existing enlarged-text breakpoint, show concise Leave/Help/Back and
  خروج/شرح/رجوع captions, retaining the complete accessible action descriptions
  and at least48dp navigation targets.
- Keep the actual phase heading; move the duplicate progress label into the
  progress bar's accessible name/value rather than repeating another heading.
- Reduce decorative gaps/panel insets, never the user's font scale.
- Use a short, accurate private-vote instruction in both languages. Voting,
  confirmation, privacy, host authority and scoring are unchanged.

Existing complete EN/AR/light/dark journeys now require an entire first player
choice and unellipsized text inside the320×740dp viewport **before scrolling**.
They retain vote cancellation, privacy-epoch cancellation, explicit commitment,
private guessing, public results and host-only rematch assertions. The progress
description remains discoverable. The help journey uses its preserved accessible
description instead of the newly abbreviated visible caption.

## Executed verification, with limits

The first focused hosted preview
[42172554209](https://github.com/Apdelrahman1911/parlor/actions/runs/42172554209)
failed at test compilation: Compose's `DpRect` has no `height` property. No test
ran and no screenshot acceptance is inferred. The correction uses the existing
`bottom - top` expression without changing the48dp assertion. Original failure,
source/artifact bindings and successful owned cleanup are retained.

Fresh local JDK21/wrapper/strict execution then passes all **30 module methods**,
zero failures/skips, plus Detekt:

```bash
./gradlew :game-modes:word-impostor:desktopTest \
  :game-modes:word-impostor:detekt --dependency-verification=strict
```

All30 synthetic renders are retained. Actual English/Arabic voting images were
visually inspected: three complete choices now fit without scrolling. For each
language the light/dark/System voting images are byte-identical because this
game uses its established fixed palette. This does not certify native fonts,
insets, touch delivery, screen-reader operation or subjective device usability.

The corrected free hosted preview
[42174087342](https://github.com/Apdelrahman1911/parlor/actions/runs/42174087342)
at exact source `cd0debdd7f57568fa390e5b4a9abeaee435b1edd` is also terminal
**SUCCESS**. Independent original XML readback confirms all30 module methods,
zero failures/errors/skips, Detekt and all30 decoded PNGs, with source/archive
and owned-cleanup bindings. Receipt SHA256:
`6c056652faeda0214db4d956c584affaaab8c40fdc1edbc0b4c41c06106a7030`.
Actual hosted voting images were inspected: three complete English choices and
four Arabic choices are visible before scrolling. The original failed compile
remains failed; it is not substituted for this corrected result.

The subsequent focused game-layout integration reuses the exact verified Word
source, alongside the separately reproduced Ghamza first-role-action refinement
([report](2026-11-06-GHAMZA-LARGE-TEXT-REVEAL.md)). Source equivalence allows
scoped evidence reuse, not substitution for full qualification of the integrated
tree. Complete protected PR review/checks/merge and distinct main acceptance
remain **pending**. #259/#260 retain their broader native/device requirements.
