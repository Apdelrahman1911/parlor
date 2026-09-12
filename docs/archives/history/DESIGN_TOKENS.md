# Parlor — historical design-token proposal

Document status: historical visual-design reference. It preserves the original
Cozy Noir direction, but names, values, motion effects, automatic performance
tiers, and future overlays below are not current shipping contracts. The
production implementations under `shared/design-system/src/commonMain` and
their tests/resources are authoritative. The sound section is reserved design
work, not shipping behavior.

---

## 1. Color

### 1.1 Surfaces (warm near-blacks; pure black is banned)

| Token | Hex | Use |
|---|---|---|
| `surface.canvas` | `#0B0807` | App background. The deepest layer. |
| `surface.elevated` | `#14100D` | Cards, dialogs, bottom sheets at default elevation. |
| `surface.higher` | `#1C1814` | Top-layer overlays (cover screens, focused dossier card). |
| `surface.inset` | `#070504` | Inset wells (text fields, recessed panels). |

### 1.2 Ember accent family

| Token | Hex | Use |
|---|---|---|
| `accent.ember` | `#D97A2A` | Primary ember accent — buttons, focused affordances, the wax-seal core. |
| `accent.emberGlow` | `#F2A04D` | Highlight layer — outer rings, soft glow. Lower-alpha layering. |
| `accent.emberDeep` | `#8C4015` | Deep ember — pressed states, inset rim. Used sparingly. |

### 1.3 Brass and parchment

| Token | Hex | Use |
|---|---|---|
| `accent.brass` | `#B89968` | Subtle gilt — dividers on hero cards, frame edges. |
| `accent.parchment` | `#E8DAB6` | Aged paper — used as a secondary surface tint inside the dossier card. |

### 1.4 Text

| Token | Hex | Use |
|---|---|---|
| `text.primary` | `#F2E6CD` | Warm cream. Headings, body, dossier prose. |
| `text.secondary` | `#B8A98A` | Muted parchment. Captions, meta. |
| `text.tertiary` | `#6B5B45` | Subdued. Disabled labels, placeholder. |
| `text.onAccent` | `#0B0807` | Text on ember buttons. |
| `text.narration` | `#E8DAB6` | Reveal narrative italics. |

### 1.5 Semantic (used sparingly)

| Token | Hex | Use |
|---|---|---|
| `semantic.success` | `#7A8C5E` | Muted moss. Vote confirmation, "Innocent" reveal accent. |
| `semantic.danger` | `#A33D2A` | Dimmed crimson. "Killer" reveal accent, destructive confirmation. |
| `semantic.muted` | `#5C4B3A` | Disabled or paused states. |

### 1.6 Borders and rims

| Token | Hex | Use |
|---|---|---|
| `border.subtle` | `#26201A` | Default divider. |
| `border.elevated` | `#3A312A` | Card rim. |
| `border.glow` | `#D97A2A @ 0.18` | Warm rim glow on hero cards (alpha applied). |

### 1.7 Contrast targets

- `text.primary` on `surface.canvas` → 11.4:1 (AAA).
- `text.primary` on `surface.elevated` → 9.8:1 (AAA).
- `text.secondary` on `surface.elevated` → 5.7:1 (AA).
- `accent.ember` on `surface.elevated` → 4.6:1 (AA, large text only).
- `text.onAccent` on `accent.ember` → 5.1:1 (AA, normal text).

These are design targets, not proof of current device contrast. Automated UI
contracts and the physical accessibility matrix provide release evidence.

---

## 2. Typography

### 2.1 Font families

| Token | Family | Notes |
|---|---|---|
| `font.display` | **Cormorant Garamond** | Transitional/Didone serif. Used for case titles, dossier names, reveal narrative. Weights: Light (300), Regular (400), Medium (500), Italic. |
| `font.body` | **Inter** | Humanist sans-serif. Used for body and UI affordances. Weights: Regular (400), Medium (500), Semibold (600). |
| `font.timer` | **JetBrains Mono** | Tabular figures for timers and counts. Weights: Light (300), Regular (400). |

Fallbacks: system serif / system sans / system monospace.

### 2.2 Type scale

| Token | Family | Weight | Size (sp) | Line height (sp) | Tracking | Use |
|---|---|---|---|---|---|---|
| `type.display.hero` | display | Light | 48 | 56 | -0.5 | Case title on Game Details, Reveal stage hero. |
| `type.display.large` | display | Regular | 36 | 44 | 0 | Round title cards. |
| `type.display.medium` | display | Regular | 28 | 36 | 0 | Dossier name. |
| `type.heading.large` | display | Medium | 22 | 30 | 0.2 | Section headings. |
| `type.heading.medium` | body | Semibold | 18 | 26 | 0.2 | UI section headings. |
| `type.body.large` | body | Regular | 18 | 28 | 0.1 | Dossier prose. Read-from-table size. |
| `type.body.medium` | body | Regular | 16 | 24 | 0.1 | General UI body. |
| `type.body.small` | body | Regular | 14 | 20 | 0.1 | Secondary UI. |
| `type.label.large` | body | Medium | 16 | 22 | 0.4 | Button labels. |
| `type.label.medium` | body | Medium | 14 | 20 | 0.4 | Small button / chip labels. |
| `type.label.small` | body | Semibold | 12 | 16 | 1.2 (UPPERCASE) | Small caps accents, section eyebrows. |
| `type.timer.large` | timer | Light | 48 | 56 | 0 | Final monologue timer. |
| `type.timer.medium` | timer | Regular | 24 | 32 | 0 | Round discussion timer. |
| `type.narration` | display | Italic Regular | 20 | 30 | 0.1 | Reveal narrative passages. |

### 2.3 Minimums (accessibility floor — design system enforces)

- Body text never smaller than 14sp.
- Dossier text never smaller than 18sp.
- Button label never smaller than 14sp.
- All hold-to-reveal targets have a minimum touch target of 48dp.

---

## 3. Spacing

Geometric scale based on 8dp grid (with 4dp half-step for fine adjustments).

| Token | Value | Common use |
|---|---|---|
| `spacing.xxs` | 2 dp | Hairline insets, icon-to-text gap on dense labels. |
| `spacing.xs` | 4 dp | Tight inline gaps. |
| `spacing.s` | 8 dp | Default small gap. |
| `spacing.m` | 16 dp | Default screen padding. |
| `spacing.l` | 24 dp | Section padding. |
| `spacing.xl` | 32 dp | Card padding for premium feel. |
| `spacing.xxl` | 48 dp | Major hero-screen padding (vertical). |
| `spacing.xxxl` | 64 dp | Reveal-stage breathing space. |

---

## 4. Elevation

Each level has a **shadow** plus an optional **warm rim** to suggest candlelight catching the card edge.

| Token | Y offset | Blur | Shadow alpha | Warm rim alpha | Use |
|---|---|---|---|---|---|
| `elevation.none` | 0 dp | 0 dp | — | — | Flat. |
| `elevation.low` | 2 dp | 8 dp | 0.30 | 0.04 | List rows, chips. |
| `elevation.medium` | 4 dp | 12 dp | 0.45 | 0.06 | Cards, dialogs. |
| `elevation.high` | 8 dp | 20 dp | 0.50 | 0.10 | Modals, bottom sheets. |
| `elevation.dramatic` | 16 dp | 40 dp | 0.55 | 0.18 | Reveal stage, hero cards. |

Shadow color: `#000000`. Warm rim color: `accent.ember`.

---

## 5. Corner radii

| Token | Value | Common use |
|---|---|---|
| `radius.none` | 0 dp | Edge-to-edge surfaces. |
| `radius.subtle` | 4 dp | Chips, inputs. |
| `radius.card` | 12 dp | Default cards. |
| `radius.elevated` | 20 dp | Large cards, dialogs, dossier card. |
| `radius.pill` | 9999 dp | Fully rounded badges/buttons (sparingly). |

---

## 6. Blur radii

| Token | Value | Common use |
|---|---|---|
| `blur.subtle` | 8 dp | Soft glass-effect overlays. |
| `blur.medium` | 16 dp | Pause overlay backdrop blur. |
| `blur.dramatic` | 32 dp | Cover screens behind reveal motion. |

Note: Parlor honors reduced-motion preference paths. It does not ship an
automatic device-performance motion tier.

---

## 7. Motion

### 7.1 Durations

| Token | Value | Use |
|---|---|---|
| `motion.duration.fast` | 180 ms | Small affordances (button press, toggle). |
| `motion.duration.medium` | 320 ms | UI state transitions. |
| `motion.duration.slow` | 480 ms | Screen-to-screen transitions. |
| `motion.duration.theatrical` | 800 ms | Reveal/cover transitions. |
| `motion.duration.ember` | 2400 ms | Candle-flicker cycle. |

### 7.2 Easings

| Token | Definition | Use |
|---|---|---|
| `motion.easing.standard` | `CubicBezier(0.4, 0.0, 0.2, 1.0)` | General. |
| `motion.easing.theatrical` | `CubicBezier(0.2, 0.6, 0.1, 1.0)` | Reveal moments — slow ease-out. |
| `motion.easing.deflate` | `CubicBezier(0.4, 0.0, 0.6, 1.0)` | Closing/hide transitions. |
| `motion.easing.emberPulse` | Sinusoidal (no bezier) | Candle flicker, ember glow breathing. |

### 7.3 Reusable transition templates

- `transition.reveal` — `Motion.theatrical` with `easing.theatrical`. Used by `WaxSealReveal`, `RevealStage`.
- `transition.cover` — `Motion.medium` with `easing.deflate`. Used by `CoverSurface`.
- `transition.cardRise` — `Motion.slow` with `easing.theatrical`. Used by `DossierCard` entrance.
- `transition.crossDissolve` — `Motion.medium` with `easing.standard`. Default screen transition.

### 7.4 Reduce-motion behavior

When the user's `reduceMotion` preference is on:

- `transition.reveal` collapses to `transition.crossDissolve` at `motion.duration.medium`.
- `transition.cardRise` becomes a fade without translation.
- Candle flicker is disabled (the bloom layer is rendered statically).
- Vignette pulsing is disabled.
- Sound cues are unaffected (independent preference).

---

## 8. Backdrop — `CandlelitBackdrop`

The atmosphere behind every Whodunit screen. Five layers, bottom-to-top:

1. **Base** — solid `surface.canvas` (#0B0807).
2. **Bloom** — radial gradient from offset (40%, 60%) of the viewport. Inner `#D97A2A` at 0.10 alpha; outer transparent. Bloom radius = 80% of `min(viewport.width, viewport.height)`.
3. **Grain** — full-screen noise texture, alpha 0.05, blend mode `overlay`. Static (does not animate).
4. **Vignette** (hero screens only) — radial inset from edges, inner transparent, outer `#000000` at 0.45 alpha.
5. **Candle flicker** (default-on; disabled by `reduceMotion`) — sinusoidal brightness pulse on the bloom layer, ±5% amplitude, `motion.duration.ember` cycle.

The proposed automatic low-end performance tier was not implemented. Current
motion reduction is the system/in-app Boolean policy described in
`MOTION_DOWNGRADE.md`'s current-status section.

---

## 9. Reserved sound direction (not shipping)

Parlor currently ships no audio assets, playback implementation, ambient
layer, or sound setting. The following values are design candidates only. A
future audio feature must implement platform playback, lifecycle/audio-focus
handling, accessibility behavior, and tests before exposing a control.

### 9.1 Named cues

| Token | Description | Duration |
|---|---|---|
| `sound.cue.reveal` | Wax-seal crack; warm low-mid resonance. | ~600 ms |
| `sound.cue.cover` | Soft swipe + brief candle-glow hum. | ~400 ms |
| `sound.cue.chime` | Round transition; soft brass chime. | ~300 ms |
| `sound.cue.tick` | Ticking clock; ambient loop (low volume). | loop |
| `sound.cue.gasp` | Reveal punctuation (used on YES/NO outcomes). | ~500 ms |
| `sound.cue.wax-seal-pulse` | Hold-to-reveal pulse; loops while held. | loop, ~1500 ms cycle |
| `sound.cue.timer-warning` | Soft cue at last 10 seconds of a discussion timer. | ~250 ms |
| `sound.cue.vote-cast` | Quiet confirmation when a vote is recorded. | ~200 ms |

### 9.2 Ambient

| Token | Description | Loudness |
|---|---|---|
| `sound.ambient.parlor` | Looped: distant piano (low), soft fireplace crackle, faint clock tick. | -22 LUFS, headroom -12 dBFS |

### 9.3 Target behavior if implemented

- Sound must be controlled by a real persisted preference only after playback exists.
- Sound is independent from `reduceMotion`: motion may downgrade while sound continues.
- Sound is paired with visual beats — never play sound without a visual cue.
- Audio mixing uses ducking: ambient drops to -32 LUFS when a cue plays.

---

## 10. Accessibility tokens

| Token | Value | Notes |
|---|---|---|
| `a11y.minBodySize` | 16 sp | Body text floor. |
| `a11y.minDossierSize` | 18 sp | Dossier text floor. |
| `a11y.minTouchTarget` | 48 dp | All interactive elements. |
| `a11y.minContrastRatio` | 4.5 | WCAG AA. Validated at build time. |
| `a11y.holdToRevealMs` | 1500 | Hold-to-reveal duration. |
| `a11y.holdToRevealTapFallback` | true | Tap-confirmation fallback for motor accessibility. Always available; surfaced more prominently when `reduceMotion` is on. |
| `a11y.discussionTimerSoftWarningMs` | 10_000 | Soft warning when discussion timer reaches its final 10 s. |

---

## 11. Theme overlays

The Parlor base ships with one overlay: **`CozyNoirTheme`** for Whodunit, which is the spec above.

Future modules layer their own overlay on the same component library — same tokens, different palettes/typography/motion. Example future overlays:

- **`NeonPartyTheme`** — bright accent palette, geometric sans typography, snappier motion timings.
- **`DaylightSalonTheme`** — warm-light palette, refined sans throughout, slow motion.

Each overlay maps the same token *names* to different *values*. Component code does not change.

---

## 12. What this spec does NOT include

- Specific cover-art compositions (out of scope; case-by-case asset work).
- Per-screen layout behavior, which is implemented by each shipping screen.
- The current icon/resource set and Android launcher assets.
- Final store artwork and screenshots, which remain distribution gates.

---

*Historical token proposal version 1.0. Current code and verified resources are
the source of truth.*
