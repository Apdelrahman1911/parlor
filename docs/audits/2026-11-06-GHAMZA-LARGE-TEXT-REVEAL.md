# Ghamza: keep the first role action visible at large text

## Confirmed UX gap — #259

Source-bound English screenshots from PR328 revision04
([42159600926](https://github.com/Apdelrahman1911/parlor/actions/runs/42159600926))
show a partially clipped **Reveal my role** button at 320×740dp / 200% text.
The phase-keyed scroll state resets at rematch; this is not a deliberately
scrolled roster frame. Existing `reachable()` checks scrolled before checking
the action, so they did not establish first-frame visibility.

A new assertion reproduces the defect locally against unchanged production
source `d168a8cb`: the complete button extends from y670 to y787 in a viewport
ending at y740. The single English dark journey fails before its first reveal,
not because of a missing role or network action. It remains reachable by
scrolling; this is a next-action visibility defect, not an inaccessible game.

## Correction and safeguards

At enlarged text, Leave/Help/Back use concise visible English/Arabic labels
while retaining their full accessibility descriptions and 48dp touch targets.
Role-card padding and decorative spacing decrease, not the user's font scale.
The English privacy reminder is shortened to “Keep your screen private.”
The Arabic reminder, role rules, action authorization, local concealment,
privacy epochs, Back handling and rematch mechanics are unchanged.

Existing EN/AR light/dark/default journeys now require a fully visible,
unellipsized, enabled reveal action **before scrolling**, both for initial
roles and rematches. All 28 module Desktop methods and Detekt pass locally
with JDK21, the wrapper and strict dependency verification. Actual corrected
EN/AR rematch images were inspected: the full action and readiness count fit;
no role is exposed. Eighteen synthetic game frames are retained.

```bash
./gradlew :game-modes:ghamza:desktopTest :game-modes:ghamza:detekt \
  --dependency-verification=strict
```

## Qualification boundary

These are scoped local Compose results, not hosted full qualification, native
gestures, screen-reader operation or physical-device acceptance. Fresh protected
PR qualification and distinct main verification remain required. #259's wider
six-game/native/device requirements remain open. This prepared improvement does
not qualify any failed historical Android or iOS workflow.
