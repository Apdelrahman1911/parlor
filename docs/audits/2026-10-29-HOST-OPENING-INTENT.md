# Failed host restoration must preserve explicit creation intent

## Confirmed defect and impact

While PR#286's free hosted verification runs, actual Desktop `App` restoration
reproduces another operation without a tap. Seed one retained failed host
attempt, mount App with its registered game binding, then observe the transport:
**two host operations instead of one**. All six game methods fail that exact
assertion; no errors/skips. Original XML SHA256 is retained in local custody.

This is a confirmed Compose/ownership defect, not two observed active LAN rooms,
phone process-death reproduction, or #267's unrelated lost native Host event.
A restored error screen can unexpectedly create a room if connectivity improves.
Track it under existing #252's root-recovery/explicit-Retry follow-up; all-state
searches and #182's distinct pure double-tap review do not justify a duplicate.

## Root cause and correction

Host effects call `ProcessMultiplayerSessionOwner.acquire` with its intentional
caller default, `retryFailedOpen=true`. Retained Failed survives UI replacement,
but mounting the restored host implicitly requests another creation.

The shared host lobby (three games) and original Whodunit/Mafia/Last Light flows
now pass false on initial composition and true only after explicit Retry.
Idle still opens normally; matching Active/Opening still reattaches. Original
seed ownership, host authority, cleanup, rematch and error handling are unchanged.
No new networking layer, retry deadline, persistent state or dependency is added.

## Regression and verification boundaries

Six actual-App methods cover all registered host bindings in EN/AR, 320×740dp
and200% text. They restore Failed, recreate the root, deliberately Retry once,
then recreate after that failure: operation counts must remain1/1/2/2. Synthetic
host failures never create LAN sessions or private gameplay. Executors are owned,
cancelled/joined and closed. The completion predicate requires both the second
operation and Failed within the same5s deadline.

Original six-method RED is retained. The first corrected attempt has3PASS/3FAIL
after unscrolled Retry touches; it is not accepted. A reachability assertion's
missing import then fails compilation, not gameplay. Legitimate scroll-to,
visible-target assertion and the same touch produce6PASS/0FAIL/0SKIP. No larger
timeout or direct semantic callback substitutes for the touch. The earlier
unscrolled failure's exact origin is not independently established.

Final committed-source strict aggregate, free hosted Android/Apple architectures,
23native methods, normal protected integration and fresh actual-main qualification
remain required. Physical host/client/background/storage/energy and #258/Store
gates stay open. No paid runner, verification relaxation or Store release.
