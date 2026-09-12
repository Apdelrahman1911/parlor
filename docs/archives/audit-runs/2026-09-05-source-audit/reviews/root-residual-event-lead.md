# Residual ordered GameEvent lead — current-app impact not established

Finder `/root/session_cont`; separate reviewer `/root`. Exact unchanged main commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

Reopened complete `shared/session/src/commonMain/kotlin/com/parlor/session/passandplay/PassAndPlaySessionController.kt:1–159`, `SessionController.kt:1–58` and `src/desktopTest/kotlin/com/parlor/session/passandplay/OrderedEventEmissionTurnTest.kt:1–57`.

The generic helper's three-batch schedule is real: A waits during emission, B waits on A then is cancelled, B's finally completes its own barrier, C can proceed before A finishes. The cancellation test explicitly permits a successor to proceed when its predecessor's predecessor remains incomplete; it does not assert ordering across this three-batch scenario.

However the controller emits into a SharedFlow; shipping game UI, host and peer bridges consume state/projections and separate connectionEvents, not this GameEvent stream. Reopened consumer searches across composeApp, both game modules and shared/session find GameEvent collectors only in tests; PartyAwareSession forwards the stream, Shadow exposes it, neither is a shipping collector. Host commands are serialized by their coordinator. No app-reachable wrong state, display, protocol ordering or persistence consequence was established. A hypothetical future event subscriber is not an existing production failure.

**Disposition:** reject this as a confirmed current application defect; retain the generic extension/test-contract uncertainty. It remains an unallocated lead, not an additional numbered issue, and is not a claim that the helper has a universal cancellation-order guarantee. Reopen with an actual production consumer/required contract if one is added. No build/test was run for this source-level disposition.
