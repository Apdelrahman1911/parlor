# DS-C01 V6 — actual child identity and containment controls

## Status and authority

Authored by `/root/native_fix_review`; execution belongs exclusively to `/root`.
This is a new versioned harness, not a rewrite of V5 or its failing evidence.
No production file is changed by these controls. They instrument a later owned
copy of every applicable tracked/untracked build input in the frozen manifest.

Target: `source-freeze-02.json`, manifest
`9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e`, diff
`148fb4583d8fa8341b3cbc48a7fc0bc7564bf4df5c27e9d80e32c6bfdc57b784`.
Main returns the original Compose child. The actual Swift production wrapper
constructs the new plain `ComposeContainerViewController`; there is no second
navigation stack. The four new UIKit tests compile that exact production file.

Binding/control hashes are preparation evidence, **not test results**. Independent
review must approve the final control hash before root executes the native lane.
At author freeze, V6 Python tests, Swift/Kotlin compilation, and runtime behavior
are **NOT RUN**. Any later compile, selector, geometry or runtime failure must
remain classified by its actual evidence, not as an assumed app crash.

## Retained V5 witness

`evidence/dsc01-apphost-03` observed the actual Settings EN choice, zero synthetic
language commands, then a retained Whodunit PublicIntro session across a real
foreground cycle. All 13 post-foreground observations were native RTL/direct
Compose LTR. The earlier report and independent diagnosis remain unchanged.
V6 does not replace that evidence with a claim about an unproven private setter.

## Actual native observation, not a child/type guess

The copied iOS locale provider calls a copy-only observer immediately after its
existing `LocalUIViewController.current` read. Its `DisposableEffect` is keyed
only by that controller, never by language, screen or sampling generation.
The bridge returns that actual controller, with bounded attach/dispose counts.

The Swift factory records the explicit production outer separately. Native
direction is read **only from the actual observed Compose child's loaded view**.
The outer semantic direction is recorded but is not the child-direction oracle.
Weak, write-once first references check unchanged outer/child controllers, their
views, and the same UIWindow. Parent, single-child and direct-subview relations
are checked independently. Replacements cannot silently rebase the identities.

No Kotlin bridge runs before original App initialization; no observation calls
`loadViewIfNeeded`, `layoutIfNeeded`, a semantic setter or a lifecycle callback.
Kotlin 2.4.10's native weak implementation uses the actual ObjC associated pointer
with `objc_storeWeak`/`objc_loadWeakRetained`, including Kotlin ObjC subclasses;
it does not weakly track an ephemeral wrapper. Exact official-tag source and
installed stdlib source receipts are in `research/`. No pointer is exported.

## Finite execution matrix

One unsigned Debug app on one newly created owned iPhone17Pro/iOS26.5 simulator:

1. Original four-process actual App Settings/restart/System ownership matrix.
   `previous-ar` remains an explicitly synthetic prior per-app preference;
   it is not evidence of the actual iOS Settings UI.
2. Whodunit: actual six-player pass-and-play Classic setup to PublicIntro.
3. Mafia: actual five-player pass-and-play default setup to closed role assignment.
   For both games, actual App Settings selects initial EN before game creation;
   the actual session controller, canonical StateFlow, immutable reference/value
   and public phase are captured exactly once. No private values are serialized.
4. Preserve the original four Home/background/activate cycles per game and the
   original AR/EN/System synthetic calls to the actual Koin SettingsStore. There
   is no invented in-game Settings route or repeated mutation to obtain green.
5. Attempt the real iOS per-app Language UI using `UIApplication.openSettingsURLString`.
   Missing controls remain an explicit **BLOCKED** OS gate. Never change global
   preferences, use private Settings URLs, dismiss unknown alerts, or claim pass.

Each game collects the following immutable window order; each window has six
samples on the main queue, 250ms apart, with a 10s ceiling:

| Global windows | Language command | Purpose |
|---|---|---|
| 1–2 | 0, real Settings EN | Original post-foreground pair |
| 3–4 | 0, real Settings EN | Landscape, then portrait |
| 5–6 | 1, synthetic AR | Original post-foreground pair |
| 7–8 | 1, synthetic AR | Landscape, then portrait |
| 9–10 | 2, synthetic EN | Original post-foreground pair |
| 11–12 | 3, synthetic System | Original post-foreground pair |

Every foreground train retains its first resumed observation plus both windows
before asserting any invariant. Orientation uses the public XCTest device API,
then retains the entire six-sample window before asserting. It does not poll
geometry until a lucky frame appears. Actual interface orientation comes from
the same UIWindowScene, not an inference from the device-orientation request.

There are **72 passive samples per game**, not 72 XCTest methods. All five exact
methods (four production-container UIKit tests plus the app matrix) must execute
and pass, with zero unexpected methods, duplicates, failures or skips.

## Geometry and evidence bounds

Every raw event retains window/outer/child bounds, converted window rectangles,
child frame in its outer view, all three safe-area inset vectors, interface
orientation, and native identity components. Coordinates must be finite and
bounded; active full-screen rectangles must be positive. Both child and outer
must fill the actual window. Insets must agree with the actual window within
0.5pt; matching zero child/outer insets do not pass on this notched iPhone fixture.
Unexpected zero/inset geometry is preserved for investigation, never excused
without source/runtime evidence. This is not a general all-device inset rule.

The Swift compact accessibility payload contains all six rows with derived
identity/geometry booleans. The independent Python validator rechecks **every
raw numeric sample**, not merely those booleans. Caps stay 8192 bytes per display,
4096 bytes per composition observation, 256 rows/262144 bytes per scenario.
Counters remain bounded. Tests cover replacement, disposal, offset-but-matching
rectangles, zero insets, nonfinite/invalid values, wrong orientation, dropped or
reordered windows, unwanted lifecycle callbacks, and five-test discovery.

There is no display publication inside a window. One publication follows its
six durable samples; the second foreground window observes after that boundary.
The overlay, initial/final publications, XCTest, synthetic prior preferences and
bounded evidence I/O can still perturb the instrumented app. Do not claim an
uninstrumented-device, gesture, performance, complete-game or leak-free result.

## Isolation, cleanup and remaining gates

The V5 ownership-attested secondary-FIFO logic is retained without change.
No child author/reviewer runs builds or deletes outputs. Root's exclusive runner
preserves compact evidence, immediately stops its isolated Gradle daemon, checks
exact worker ownership, verifies copied/source/control identities, and removes
only owned copy/build/DerivedData/simulator/temp paths. Unknown ownership blocks
cleanup approval. No global cache, source, credential or unrelated process is
deleted or terminated. Required finalization applies on failure and cancellation.

This matrix does not prove multiplayer/P2pKit physical LAN behavior, physical
iOS Back gestures, actual Store signing, release identities, Store readiness,
full-game completion, persistence resume, or native performance. Those gates
remain separate. OS Language UI unavailability is never reclassified as PASS.
