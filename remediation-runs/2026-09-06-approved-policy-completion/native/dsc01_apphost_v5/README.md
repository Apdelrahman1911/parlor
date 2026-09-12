# DS-C01 V5 — Passive foreground evidence, copy only

**Status at author freeze: controls authored, not executed by the author; independent
review and root execution are separate receipts.** Control SHA:
`94e8506ab4a4ed49398b7ae30dc704e3003bf0895af0507b920d346e6f145e70`.
`author-bound-control-manifest-01.json` binds all executable controls, and
`v4-to-v5-controls.diff` preserves their complete delta. The exact V4 source
binding is copied unchanged; V3/V4 controls and original receipts are untouched.

## Why another evidence version

V3 `evidence/dsc01-apphost-01` completed the real four-boot Settings matrix but
failed in XCTest while first typing the sixth Whodunit name. No application
crash was established. V4 used the existing IME Next/Done path, successfully
entered all names, and captured the actual Whodunit PublicIntro session.

V4 `evidence/dsc01-apphost-02` then failed a **real assertion**, not compilation:
after its second background/foreground cycle, English remained selected, direct
Compose direction was LTR, and actual controller/flow/canonical reference/value
identity remained unchanged, but original root UIKit semantic direction was RTL.
The preceding English observation was LTR. Only one post-foreground sample was
retained; whether the mismatch was transient, sustained, recurring, or affected
by instrumentation was unresolved. Mafia and OS scenarios were not reached.
V5 does not fix or reinterpret that result. It collects enough read-only samples
to investigate it without accepting a lucky matching final sample as PASS.

The original V4 cleanup receipt also remains FAIL. Its supplementary exact
PID/start/FIFO check and independent acceptance establish only that the recorded
workers and attested paths are now absent. The UID-query failure cause was not
observed directly; an exit race remains a hypothesis.

## Finite scenarios and expectations

One ordered XCTest runs on a newly created, task-owned iPhone17Pro/iOS26.5
simulator, unsigned Debug only:

1. The unchanged actual App Settings/restart matrix uses four real boots and
   directly observes Settings Compose direction. Its previous OS-preference
   fixture is explicitly synthetic; it is not actual iOS Settings execution.
2. Each local game starts in a fresh process. **Initial English is chosen through
   the actual Settings UI**, then the exact original Games tab returns Home.
   Whodunit uses original pass-and-play/story/Classic/six-name controls and stops
   at PublicIntro. Mafia uses original pass-and-play/five-name/default-settings
   controls and stops at the closed role-assignment handoff. The V4 original IME
   Next/Done interactions are unchanged. No private role is opened.
3. Exactly one actual `PartyAwareSession`, canonical `StateFlow`, immutable state
   reference/value and public phase checkpoint is captured per game process.
   **No synthetic language command precedes this capture.** A real-Settings EN
   Home/background/activate cycle is measured first. With the actual game still
   mounted, subsequent AR/EN/System changes are visibly labeled synthetic calls
   to the production Koin `SettingsStore`; there is no in-game Settings route.
   Each requested language has its own real lifecycle cycle: four per game.
4. The actual iOS Settings investigation remains unchanged. It opens only public
   `UIApplication.openSettingsURLString` and requires the exact Language/English
   controls. Their absence is BLOCKED, never PASS or proof of non-applicability.
   No global preference rewrite, private Settings URL or selector fallback is
   added. Missing OS controls require a separately reviewed evidence change.

## Passive sampling and its limitations

After actual lifecycle counters advance, retain the **first** resumed observation.
Then collect two consecutive six-sample windows before any direction assertion.
Each sample is scheduled on the main queue after 250ms, reads the original root
UIViewController semantic attribute and actual Compose/session observation, and
atomically appends a fresh numbered event. A window is bounded to ten seconds;
XCTest waits at most fifteen seconds. There is no blocking sleep or busy loop.

No `@Published display` assignment occurs between the six samples in a window.
Exactly one publication exposes its completed rows, then a second window is
measured after that publication. This reduces within-window SwiftUI invalidation
and makes a recurring mismatch observable. It **does not prove** the overlay,
XCTest interactions, checkpoint, file I/O, initial/final publications or copying
have zero timing/UI effect. Publication-related causation requires further
source/runtime evidence; it must not be guessed from correlation.

Only after the full resumed + twelve-sample train is durable does XCTest assert
all thirteen samples against the requested language. Every sample must preserve
the same boot, single root controller, actual session/controller/flow/state
identity, public phase, own callback progression, expected preferences, native
direction and direct Compose direction. The Python validator independently
requires eight ordered windows/48 samples, all post-foreground samples matching,
and zero synthetic commands before initial real Settings/capture. A transient
or recurring mismatch remains FAIL; it cannot disappear from the report when a
later sample matches. Raw failed/incomplete receipts remain necessary evidence.

The harness-only receipt schema is version5. It adds bounded window/index/elapsed
coordinates and retains the original 8KiB accessibility/256-event/256KiB report
ceilings. No game, protocol, save, credential or production content format changes.
No seed, player ID, private role, credential, state dump or state hash is exported.

## FIFO ownership refinement

`SecondaryFifoLedger` now reads current PID/start-attested argv rather than stale
membership argv. It may skip redundant live UID queries only when **both exact
FIFO records** were already fully attested for the same child and parent PID/start
identities with no pending claims. It still checks canonical shape, ancestry,
symlinks, inode/type/UID/creation metadata and replacements. New/incomplete/changed
pairs perform the full UID and post-query identity/argv checks. No cached record
can adopt another path. Cleanup still requires worker/PID absence, unchanged
metadata, no unknown directory entries or holders, exact unlink, and empty-parent
rmdir only. Previous errors and receipts are never erased or reclassified.

## Root-only execution and cleanup

Root first executes these synthetic controls in the single shared build lane:

```sh
/usr/bin/python3 -B -m unittest -v test_secondary_fifo test_harness_contract test_copy_observation_contract
```

Their PASS is not iOS runtime evidence. After independent exact-hash approval,
root executes `run_dsc01_apphost_cycle.py dsc01-apphost-NN APPROVED_CONTROL_SHA256`.
The source copy/strict dependency graph/original lifecycle path remain bound.
Before and after workers stop, input inventories/hashes must match the approved
copy transformations; original repository source/control equality also gates PASS.

Root owns all Gradle/Xcode/simulator execution. The existing finalizer stops its
isolated Gradle registry immediately, stops exact owned workers, cleans only
attested secondary FIFOs and empty parents, deletes owned copy/DerivedData/temp
outputs and the fresh simulator, and records every cleanup result. It never
deletes original-repository outputs or global caches. Unexpected originals are
preserved and fail the gate. No build/app/daemon/process was started by this author.

This remains **not** complete-game, save/resume, retained multiplayer host, physical
LAN, actual-device accessibility/gesture, signed-release, Store or leak-free proof.
Exit0 requires every applicable evidence and cleanup gate; exit2 means only the
actual OS Settings control remains BLOCKED after the required Settings/local gates
passed; all other failures/incomplete runs exit1. Compilation alone cannot pass.

## Source and research references

- Actual owners: `LocalAppLocale.ios.kt`, `ProvideAppLanguage.kt`, `App.kt`,
  `iosApp/iosApp/ContentView.swift`, and original local-game session composables.
  There is one shipping `ProvideAppLanguage` call, not a demonstrated duplicate.
- Exact EN/AR Games/Settings tab strings are bound to
  `composeApp/src/commonMain/composeResources/values*/strings.xml` and the original
  shared `ParlorBottomTabBar` merge policy already verified for CMP1.10.3.
- Pinned CMP1.10.3 UIKit source and checks are retained at sibling
  `dsc01_apphost_02_diagnosis/research/`; its initial global UIKit layout resolution
  does not by itself identify the external writer that later changed semantics.
- Original Next/Done source references remain at
  `dsc01_apphost_v3/research/keyboard-source/`; the Apple exact-element `typeText`
  reference remains at `dsc01_apphost_v4/research/apple-type-text.json`.
- Previous official UIApplication/WWDC19 localization and XCUIApplication-state
  references remain versioned in V3 material; no new private API is introduced.
- `research/passive-sampling-apis-01.json` retains official Apple docs accessed
  2026-09-06: `asyncAfter` schedules and returns immediately; `systemUptime`
  measures awake time since restart; `ObservedObject` invalidates dependent
  views on published changes. These semantics support the nonblocking sampler
  and observer caveat, not a claim about the unknown semantic-attribute writer.
  The source-bound privacy manifest already declares SystemBootTime/35F9.1;
  no production privacy declaration or Store artifact is changed by this probe.
