# Motion Downgrade Tiers

> Performance-driven downgrade tiers for the cozy-noir motion stack.
> Activated automatically at app start based on a runtime capability probe.

## Tiers

### Tier A — Full cinematic
Default on:
- Modern Android (Pixel 6+ class, mid-range and up).
- iPhone 13 and newer.
- macOS with dedicated GPU; recent Apple Silicon.

Active effects:
- Candle-flicker bloom (full 2400 ms sinusoidal cycle).
- Grain overlay (alpha 0.05, blend `overlay`).
- Vignette inset on hero screens.
- Wax-seal reveal motion (theatrical easing, 800 ms).
- Card rise + dossier crossfade.

### Tier B — Reduced ambient
Triggered when:
- Frame rate during idle dips below 50 fps over a 3 s sample.
- Detected GPU is integrated and < 2 GB VRAM (Desktop).
- Older Android device (API < 30 or 2 GB RAM and below).

Active effects:
- Candle flicker disabled; bloom static.
- Grain rendered to a pre-baked raster, blended at lower cost.
- Vignette unchanged.
- Wax-seal reveal motion unchanged (visual focal point — never downgraded).
- Card rise replaced with a fade.

### Tier C — Minimal motion
Triggered when:
- Frame rate during the reveal motion dips below 30 fps.
- User has set system-level "Reduce Motion" preference.
- Tier B downgrades didn't recover frame rate.

Active effects:
- Backdrop is a static raster.
- All transitions are cross-dissolves (`motion.duration.medium`, `easing.standard`).
- Wax-seal reveal becomes a tap-to-reveal with an ember-flash (still cinematic, no hold).
- Sound cues unchanged (independent of motion tier).

## Selection logic

The tier is picked once at app start by `MotionCapabilityProbe`:

1. Read system `reduceMotion` setting → if true, lock Tier C.
2. Run a 2 s warm-up frame-rate sample on the Home backdrop:
   - p50 fps ≥ 55 → Tier A.
   - p50 fps 35–55 → Tier B.
   - p50 fps < 35 → Tier C.
3. Allow the user to override tier in Settings.

## Where this lives in code

- `MotionCapabilityProbe` — `:shared:design-system/motion/MotionCapabilityProbe.kt` (Phase 8).
- Tier exposed via `LocalMotionTier` CompositionLocal in `ParlorTheme`.
- Composables consult `ParlorTheme.motionTier` and choose effect variants.

## Validation

- [ ] Tier A verified on the device matrix.
- [ ] Tier B verified by simulating GPU pressure (Compose Multiplatform's `SamplingProfiler` or Android GPU Profiler).
- [ ] Tier C verified by toggling "Reduce Motion" in system settings.
- [ ] User-override path tested end-to-end.
