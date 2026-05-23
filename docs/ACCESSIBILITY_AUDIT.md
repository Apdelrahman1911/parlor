# Parlor — Accessibility Audit Checklist

> Phase 8 acceptance bar. Every checked item is validated on real devices, not just emulators.

## 1. Type and contrast

- [ ] Body text ≥ 16 sp throughout.
- [ ] Dossier text ≥ 18 sp throughout.
- [ ] Headings ≥ 28 sp.
- [ ] Text-on-surface contrast meets WCAG AA (4.5:1 minimum normal, 3:1 large).
- [ ] Ember-on-surface contrast verified for any non-decorative use of `accent.ember`.
- [ ] No reliance on color alone — every state has a text or icon cue.

## 2. Touch targets

- [ ] Every interactive element ≥ 48 dp × 48 dp.
- [ ] No two adjacent interactive elements within 8 dp of each other (avoids mis-taps).

## 3. Gestures

- [ ] **Wax-seal hold-to-reveal** has a tap-confirmation fallback (always; surfaced more prominently when `reduceMotion = true`).
- [ ] No gesture that requires multi-touch (single-finger interactions only).
- [ ] No gesture that requires precise timing other than the wax-seal hold (which has the explicit fallback).
- [ ] No gesture that requires a swipe direction the user cannot reverse.

## 4. Motion

- [ ] System-level "Reduce Motion" preference is read and honored.
- [ ] Cinematic reveals collapse to dignified cross-dissolves under reduced motion.
- [ ] Candle flicker on backdrop is disabled under reduced motion.
- [ ] No motion that lasts > 800 ms without a "skip" or "tap to continue" affordance.
- [ ] No flashing > 3 Hz (seizure safety).

## 5. Screen readers

- [ ] Every `ParlorButton` carries a `contentDescription`.
- [ ] Every clickable surface (cards, list rows, custom gestures) carries a `contentDescription`.
- [ ] Headings are marked as headings.
- [ ] Live regions (timer ribbon, vote results) announce updates without spamming.
- [ ] Decorative-only elements are marked `clearAndSetSemantics` or excluded from a11y tree.

## 6. Layout adaptability

- [ ] App supports system font scaling up to 200% without clipping or layout collapse.
- [ ] App handles portrait, landscape, and split-screen on tablets/Desktop.
- [ ] Reading order matches visual order under right-to-left locales.

## 7. Internationalization

- [ ] All chrome strings flow through `UiText` and a single resolver.
- [ ] Case prose flows through validated content; no inline literals.
- [ ] Text containers tolerate ~+40% length expansion (German/French translation slack).

## 8. Sound

- [ ] All sound cues are paired with a visual beat (never sound-only signals).
- [ ] System mute is respected.
- [ ] Settings expose a "Sound on/off" toggle separate from "Reduce motion".

## 9. Cognitive load

- [ ] No screen requires reading > 200 words of UI prose at once (case prose is exempt — it is the content).
- [ ] Every cinematic transition has a "tap to continue" affordance (no auto-advance for narrative beats).
- [ ] Time pressure is always soft (chime, not buzz) except in Elimination Mode revote.

## 10. Sign-off

- [ ] An external user with low vision completes a full game.
- [ ] An external user using TalkBack / VoiceOver completes a full game.
- [ ] An external user with motor-control sensitivity completes a full game with hold-to-reveal disabled.
