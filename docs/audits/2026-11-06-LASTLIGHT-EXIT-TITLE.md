# Last Light: preserve the Arabic exit heading at large text

## Confirmed defect — #259 / #260

The original 320×740dp / 200%-text Arabic exit capture from
[42185129610](https://github.com/Apdelrahman1911/parlor/actions/runs/42185129610)
breaks **الرئيسية؟** inside the word. The complete localized title is present,
but the oversized editorial heading cannot fit its longest word within the
244dp panel content width. This is a text-layout defect, not missing translation,
failed navigation or an inaccessible scroll container.

An added line-boundary assertion fails against unchanged production source at
`ca6f7f0f`: the break is at index25, before the final Arabic letter. The original
XML, source diff and image are retained. A first `headlineSmall` attempt still
fails at index26, stranding the question mark. That unsuccessful correction is
retained separately and is **not** reported as a fix.

## Correction and regression

`LastLightExitConfirmation.kt` uses the existing readable `titleLarge` role at
the enlarged-text breakpoint, as the compact results heading already does.
Normal text retains `headlineMedium`. Font scaling is still applied in full;
there is no ellipsis, maximum-line limit, translation shortening, hidden warning
or density override. The title remains an accessibility heading and pane title.
The scrolling body, safe insets, local save, host/peer destructive warnings,
action ownership and replacement of private gameplay content are unchanged.

All four existing EN/AR normal/200%-text chrome journeys now assert complete
title length, no visual overflow, and line breaks only at word boundaries.
Existing reveal/privacy, options, cancellation, results, host/peer warning and
duplicate-action assertions remain.

```bash
./gradlew :game-modes:last-light:desktopTest :game-modes:last-light:detekt \
  --dependency-verification=strict
```

Fresh local JDK21/wrapper/strict execution passes all **204 methods**, zero
failures/errors/skips, and Detekt. All109 synthetic renders are retained. Actual
EN/AR normal/200% exit frames were inspected at original size: whole Arabic
words and punctuation fit; English and regular-size hierarchy remain readable.
The enlarged dialog still scrolls to its lower confirmation action. Test receipt
SHA256: `3b244d01c6c07a593e5fce700c60e56520323fd1e6a862bf7f2117d16f74e3cd`.

## Acceptance boundary

This also re-executes the [first-action correction](2026-11-06-LASTLIGHT-FIRST-ACTION.md)
on the combined Last Light source. It does not replace full hosted/protected PR
and distinct-main qualification. Four reviewed synthetic images do not certify
all109 frames, native fonts/gestures, screen readers or physical gameplay.
The broader #259/#260 requirements remain open.
