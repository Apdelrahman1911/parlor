# Historical automatic motion-tier proposal — superseded

Document status: historical filename retained for old links. The automatic
GPU/FPS/device-class tier system described by the original design was never a
shipping capability.

Parlor has no `MotionCapabilityProbe`, `LocalMotionTier`, startup frame-rate
sampler, GPU-memory classifier, or automatic Tier A/B/C selection. Documentation
and release testing must not claim those features.

## Current behavior

Motion reduction is one explicit Boolean policy:

1. platform code reads the system reduced-motion preference through
   `rememberSystemReducedMotion()`;
2. the persisted in-app preference is read from `SettingsStore.reducedMotion`;
3. `shouldReduceMotion()` combines the two without overriding a system request;
4. `ParlorTheme(reducedMotion = ...)` exposes the effective value; and
5. motion-sensitive composables select their reduced path from
   `ParlorTheme.reducedMotion`.

The setting is available on Android and iOS. Device behavior, animation
smoothness, large-text interaction, and system setting changes still require
the physical-device accessibility matrix. There is no claim that Parlor
automatically detects every low-performance device.

Any future adaptive performance tier is a new feature. It must add observable
runtime policy, deterministic tests, truthful settings/diagnostics, battery and
performance evidence, and platform validation before this document can become
an active contract.
