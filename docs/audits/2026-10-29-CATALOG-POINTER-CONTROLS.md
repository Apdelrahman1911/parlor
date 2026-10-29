# Six-game shared catalog pointer controls

## Scope and executed evidence

`HomeCatalogPointerTest` uses actual Compose pointer dispatch into the production
`HomeScreen`, not semantics `OnClick`, a replacement callback, an observer or a
networking seam. Five methods execute **100 distinct public journeys**:

- 24 single-press selections: six games × EN/AR × 100%/200% text.
- 24 cancelled presses: no selection or navigation handoff.
- 24 card drags: scroll without selecting a game.
- 24 first presses after saved Home-entry disposal/remount.
- Four Word Impostor selections after ordinary slow swipes and framework idle.

The selected protocol ID must be exactly the requested game's ID, once, and
Home must disappear on selection. Cancellation/drag must leave Home visible.
The 50ms interval is **shared pointer event time**, not executed UIKit/physical
press duration. The focused construction-source run passes all five methods
with zero failures/errors/skips and 100 unique public trace rows; its retained
JUnit XML SHA256 is recorded in local custody. This does not qualify a later
checkout, the native handoff, game setup, gameplay or complete user journeys.

```sh
./gradlew :composeApp:desktopTest --tests '*HomeCatalogPointerTest*' \
  --dependency-verification=strict
```

## Sensitivity and preserved construction failure

Two isolated, restored changes to actual Home wiring are independently rejected:

1. Remove the card callback: the real single-press method fails because the
   selected ID list is empty instead of containing Whodunit.
2. Dispatch the first registry entry for every card: the same method fails
   when Mafia selection incorrectly returns Whodunit.

Each negative command executes exactly one failing method, zero errors/skips,
and stops only its private Gradle registry. Original Home bytes are restored.
The first coordination script incorrectly expected the method name without the
actual KMP `[desktop]` suffix and **failed** after preserving its result. That
coordination failure remains failed; independent comparison with the original
passing method and exact assertion establishes the retained negative execution.
Only the previously unexecuted wrong-binding experiment then runs separately.

## Native investigation and integration gates

[#267](https://github.com/Apdelrahman1911/parlor/issues/267) retains the original
lost Ghamza Host event and the related, not causally linked, Word Impostor catalog
loss in failed [PR#282](https://github.com/Apdelrahman1911/parlor/pull/282).
[The separate evidence-only native investigation](https://github.com/Apdelrahman1911/parlor/actions/runs/40804590789)
passes its corrected SDK/control step and is still running at this checkpoint.
Its original app source remains841a0b06; these new Desktop tests are not added
to that running source copy or used as application/observation acceptance.

Fresh committed-source full strict gates, affected hosted checks, protected
integration and actual-main verification remain. Keep #259/#267 and physical
accessibility/efficiency gates open. No shipping gesture change, replay, longer
wait, Release deoptimization, required-check bypass or Store release is added.
