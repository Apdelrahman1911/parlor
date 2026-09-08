# Retained-host language continuity — draft verification

**Author:** `/root/factory_review`. **State:** source draft, NOT RUN. No source
file has been installed into a shipping/test source set. Root owns the only
Gradle/Xcode lane and must finish the frozen native witness before deciding when
to import these files. An independent reviewer must approve test validity first.

## Proposed destinations

| Draft | Destination relative to repository |
|---|---|
| `ControlledStartRoom.kt` | `shared/networking-testing/src/commonMain/kotlin/com/parlor/networking/testing/ControlledStartRoom.kt` |
| `WhodunitHostLanguageContinuityTest.kt` | `game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostLanguageContinuityTest.kt` |
| `MafiaHostLanguageContinuityTest.kt` | `game-modes/mafia/src/desktopTest/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaHostLanguageContinuityTest.kt` |

The fixture stays in the existing **non-shipping** networking-testing module.
Both games already depend on it for tests and have the needed Desktop Compose,
Koin and coroutine test dependencies; no proposed Gradle/dependency change is
needed. Generic fixture code imports no shipping game or session implementation.

## What the four tests actually exercise

- A real `ProcessMultiplayerSessionOwner.acquire` and `freezeAdmissions` over a
  fixed, valid six-seat synthetic pre-admitted roster. Creation happens once.
- The actual game host **Composable** creates its actual internal
  `WhodunitHostRuntime` / `MafiaHostRuntime`; tests never instantiate a substitute
  runtime or write a canonical phase, role map, seed or `StateFlow`.
- Actual host bridges retain `requireStartHandshake=true` and roster
  reconciliation. Readiness is deliberately withheld: both runtimes must still
  be Starting/Setup. The synthetic peers then validate against the **actual
  bridge protocol tuple**, immutable roster and game prerequisites before
  sending Ready and a matching commit ACK through `InMemoryPeerRoom`'s actor
  stamping. Each requests an initial snapshot after commit, matching the real
  eager-snapshot race rather than incorrectly forbidding pre-commit frames.
- Whodunit loads the real bundled `last-dinner` through `TestingStoryFixtures`'s
  strict production validators. Start offers must match its exact version/digest.
  Actual assignment reaches PublicIntro and actual host UI records its own ACK.
- Mafia renders actual setup; the actual Start button sends atomic
  `ConfigureAndStart`. One test selects Doctor repeat protection ON, the other
  preserves the OFF default. Both check chosen settings in the canonical game.
- Real `ProvideAppLanguage`: EN → AR → EN → System → AR → System. Direction,
  Desktop default-locale application, presentation lifetime and actual runtime,
  controller, canonical-flow, state reference/value and assignments are checked.
  Explicit presentation unmount/remount must retain the same owned runtime.
- A second case per game applies synthetic Suspended → Resuming → Active. Real
  host submission must return `SessionSuspended` in the first two states. In
  PublicIntro, Whodunit intentionally does **not** create a Round-only pause;
  active own-intro acknowledgement is an accepted no-op. Mafia accepts a legal
  state-changing own-role ACK after activation but cannot advance until the
  other five seats acknowledge.

Assertions involving canonical/host/private state and seeds are Boolean and use
constant failure text. No role, seed, private payload, state serialization/hash,
player data from disk, screenshot or semantic-tree dump is intentionally emitted.
The fixtures hold only synthetic names and bounded per-seat protocol observations.
Snapshot envelopes and direct delivery are checked, **not** their game payloads;
existing projection/codec/peer-boundary tests remain necessary.

## Scheduling and cleanup

Each game test owns one `StandardTestDispatcher` and `TestCoroutineScheduler` for
the process/session/peer jobs. `runCurrent()` drains current work without skipping
protocol deadlines. Compose has its normal test scheduler; bounded `waitUntil`
pumps the owned game scheduler and observes actual UI readiness. No sleeps,
unconfined coroutine launch, real sockets or application workers are added.

Finally, actual `owner.finalLeave` is awaited and checked, then the task scope is
cancelled and its completion/peer-worker count checked. Koin and the Desktop
provider are disposed. Any JVM locale changed by this test is restored even on
failure. No OS preferences, keychain, real app data or personal profile is read
or reset. Root must additionally preserve XML/logs, stop Gradle immediately,
clean cycle-owned outputs, stop again as needed and verify worker absence.

## Required execution after review/import

Run both focused selectors with strict dependency verification in the root lane:

```sh
./gradlew :game-modes:whodunit:desktopTest --tests '*WhodunitHostLanguageContinuityTest*' \
  :game-modes:mafia:desktopTest --tests '*MafiaHostLanguageContinuityTest*' \
  --dependency-verification=strict --no-build-cache --no-parallel --max-workers=1
```

Inspect actual JUnit XML: four tests discovered/executed, no failures or skips.
Both `@Test` methods per class explicitly return `Unit`. Re-run affected static
analysis and the combined desktop gate after any import/refinement. Compilation
and source inspection alone do not make these tests PASS.

## Deliberate limitations

- Synthetic language-provider input, **not** SettingsStore/Settings navigation.
- Desktop Compose/JVM locale, **not** UIKit semantics, native gestures, OS
  per-app language, true restart, iOS app-host or Android device execution.
- Synthetic admitted transport and lifecycle, **not** P2pKit admission,
  credential durability, physical disconnect/rejoin, socket ordering or LAN.
- Initial active phases, **not** whole-game/timed-discussion progression,
  snapshot save/load, process recreation, app-switcher privacy or leak proof.
- The ON case verifies actual setup → host canonical propagation/retention,
  **not** three-night Doctor behavior or peer UI; existing MF-C1 tests cover that.

Original source baseline and draft hashes are in `draft-manifest-01.json`.
No result in this directory supersedes a failed native receipt or claims DS-C01
complete, Store readiness, a new game rule, or additional confirmed defect.
