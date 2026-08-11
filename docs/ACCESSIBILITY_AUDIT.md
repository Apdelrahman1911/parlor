# Accessibility qualification checklist

Document status: current external release gate. Source, resource, layout, and
semantics tests provide pre-device evidence; only dated TalkBack/VoiceOver and
physical-device receipts may complete this checklist.

## Automated evidence that must be green first

- `ProductionUiAccessibilityContractTest`: minimum custom touch targets,
  headings/live-region conventions, sensitive semantics, and known screen
  contracts.
- `LocalizationResourceContractTest`: exact English/Arabic key parity and no
  missing production strings.
- Whodunit and Mafia responsive-layout suites: supported compact layouts do not
  use known unbounded structures.
- content and projection tests: one player's hidden role/private action never
  enters another player's UI model or accessibility tree.
- `productionCheck`: Android lint, resource packaging, release compilation,
  static analysis, and all registered UI contract tests.

Automation does not prove spoken order, clipping at device font scales,
contrast on a physical display, gesture usability, or assistive-technology
quality.

## Physical matrix prerequisites

Record app SHA/build, signed/debug origin, device model, OS version, language,
display size, font/display scale, light/dark appearance, reduced-motion state,
screen reader/version, game/mode, and host/peer role. Test both Android and iOS
with English LTR and Arabic RTL.

## Text, contrast, and layout

- [ ] Normal and large text meet the approved WCAG contrast target on every
  used background, including disabled, error, warning, success, and selected
  states.
- [ ] No state relies only on color, motion, or sound.
- [ ] System font scaling through the platform-supported release target causes
  no clipped controls, hidden actions, overlapping text, or unreachable scroll
  content.
- [ ] Portrait, landscape where supported, small phone, large phone, and tablet
  layouts retain correct reading and focus order.
- [ ] Arabic mirrors direction where appropriate without mirroring semantic
  media or changing number/room-code meaning.

## Touch and motor access

- [ ] Every actionable target is at least the platform/repository minimum and
  has sufficient separation for reliable activation.
- [ ] Double taps or rapid repeated activation cannot submit an authoritative
  action twice.
- [ ] Wax-seal hold-to-reveal has a clear tap-confirmation fallback and both
  paths expose the same private information only to the intended player.
- [ ] All game and recovery flows can be completed with one pointer and without
  precise directional gestures.
- [ ] Back, Leave, retry, Settings recovery, and destructive confirmations are
  reachable without time-critical gestures.

## Motion

- [ ] Android and iOS system reduced-motion settings are detected on launch and
  after the supported app lifecycle transition.
- [ ] The in-app reduced-motion preference persists and cannot override an
  active system request for less motion.
- [ ] Reveal, progress, transition, and reconnect UI remain understandable with
  reduced motion enabled.
- [ ] No flashing or rapidly oscillating visual exceeds the approved seizure
  safety threshold.

Parlor does not implement automatic GPU/FPS motion tiers; do not test or claim
that historical proposal as a shipping feature.

## TalkBack and VoiceOver

- [ ] Every screen announces one useful title/heading and an understandable
  current state.
- [ ] Controls expose concise names, roles, values, selected/disabled state,
  and destructive consequences without redundant speech.
- [ ] Dynamic timers, command results, disconnect/reconnect state, vote result,
  and terminal result are announced at useful frequency without repeated spam.
- [ ] Focus does not jump behind dialogs, overlays, reconnect UI, private-role
  covers, or navigation transitions.
- [ ] Reading order matches visual order in English and Arabic.
- [ ] Decorative graphics are absent from the semantics tree.
- [ ] A private role, target, dossier, vote target, or other hidden data is not
  announced from a covered/background screen, screen preview, or another
  player's projection.

## End-to-end completion

- [ ] A TalkBack user completes Whodunit local and multiplayer flows, including
  setup, role reveal, rounds, voting, result, replay, Leave, and recovery.
- [ ] A VoiceOver user completes the same Whodunit flows.
- [ ] TalkBack and VoiceOver users complete Mafia setup, role reveal, every
  role-specific night action, discussion, voting, result, replay, Leave, and
  recovery.
- [ ] A low-vision user completes both games at large text/display scale.
- [ ] A motor-accessibility tester completes both games using the reduced-motion
  and tap-fallback paths.

Each failed row is a release finding with reproduction steps and evidence. An
unchecked row remains an external gate; it must not be converted to PASS from a
simulator, screenshot, compile, or static test.
