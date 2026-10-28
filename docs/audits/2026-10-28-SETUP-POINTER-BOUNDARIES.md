# Shared setup pointer boundaries — October 28, 2026

## Executed scope

[`MultiplayerSetupPointerTest`](../../composeApp/src/desktopTest/kotlin/com/parlor/app/shell/game/multiplayer/MultiplayerSetupPointerTest.kt)
executes the actual registered Egyptian Dominoes, Ghamza and Word Impostor
bindings, shared topology clickable, permission route and name destination.
Six Desktop tests cover **60 journeys** in English/LTR and Arabic/RTL at
320×740dp and 200% text. They use `performTouchInput`, not an accessibility
`OnClick` action or a replacement shell callback:

- One press/release with a **50ms Compose event-time interval** selects Host
  or Join and reaches the corresponding, initially disabled name confirmation.
- Cancel and scrolling from a pressed card leave setup unchanged.
- Two short inputs cannot bypass explicit name confirmation.
- A newly allocated but structurally equal launch preserves the destination
  and the entered synthetic name across root recomposition.
- A transport tripwire verifies no host, join or discovery operation starts.

Desktop's genuine `NotRequired` permission policy advances to name through
the existing effect; this is **not** Apple's `Unknown` rationale behavior.
Scroll positioning uses the test framework's scroll action. No UIKit event,
physical touch, network room, credential or private gameplay is exercised.

## Verification and negative controls

Strict focused execution passes **6/6**, with zero failures/errors/skips;
plain and type-aware Desktop Detekt pass. Removing the actual Host callback
causes three methods to fail; independently removing Join causes three to
fail. Each controlled mutation is restored byte-for-byte. All six tests and
both analysis gates pass again after restoration. No mutation is committed.
Local custody is retained under `.git/agent-custody/overhaul-2026-10-28/setup-pointer/`.

```bash
./gradlew :composeApp:desktopTest --tests '*MultiplayerSetupPointerTest*' \
  :composeApp:detekt :composeApp:detektDesktopTest \
  --dependency-verification=strict --no-daemon
```

## Interpretation and remaining gate

This adds regression coverage and narrows the shared input-to-shell boundary;
it does **not** reproduce or resolve [#267](https://github.com/Apdelrahman1911/parlor/issues/267)'s
original lost UIKit Host event. Shipping code, pointer observers, dependencies,
gesture durations, CI architectures and release gates are unchanged. Full
current-head CI and actual-main verification must qualify integration separately.
The original iOS26.2 input/recognizer/scene-to-Compose boundary and physical
touch/VoiceOver acceptance remain unverified. Later passes do not erase the
original failure; no slower or replayed native gesture is prescribed.
