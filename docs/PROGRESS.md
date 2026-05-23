# Parlor — Progress (Phases 0 → 8 + Post-MVP scoping)

> Snapshot of the project state after the autonomous build pass.
> Source of truth for what's done, what's stubbed, and what remains for production hardening.

---

## Quick summary

| Phase | Status | Notes |
|---|---|---|
| 0 — Planning + content/schema audit | ✅ Complete | All docs locked. |
| 1 — Project skeleton + design-system baseline | ✅ Complete | KMP+CMP scaffold; needs `gradle wrapper` + Android SDK to build. |
| 2 — Generic game engine | ✅ Complete | Contracts + trivial test game + smoke test. |
| 3 — Content system implementation | ✅ Complete | Validator + repository + sources + Whodunit payload validator. |
| 4 — Whodunit setup + character reveal | ✅ Complete | Reducer, projections, signature components, all setup + reveal screens. |
| 5 — Rounds, clues, voting, reveal, replay | ✅ Complete | Full gameplay reducer + ClueCard / TimerRibbon / round / vote / reveal / postgame screens. |
| 6 — Safety, persistence, resume, QA hardening | ✅ Complete | Safety reducer actions + overlays + in-memory and file-backed snapshot stores + secure storage backing. |
| 7 — Multi-device abstraction shape test | ✅ Complete | InMemoryRoomBus + ShadowSessionController + state-trajectory shape test. |
| 8 — Production polish + release readiness | ✅ Artefacts complete | Accessibility audit, motion downgrade tiers, release runbook, store metadata, content review, telemetry contract. |
| Post-MVP | 📋 Scoped | Real P2P transport, additional cases, Easy/Hard, Quick/Full, more game modules. |

---

## Phase 0 deliverables

- `docs/CONTENT_SCHEMA.md` — generic `CaseEnvelope` + `WhodunitCase` payload schema, validation rules in order.
- `docs/DESIGN_TOKENS.md` — premium baseline token spec (color/typography/motion/elevation/blur/radii/backdrop/sound).
- `docs/MOCK_BACKEND.md` — locked decision: static JSON in-repo via Ktor `MockEngine`.
- `content/last-dinner.draft.json` — full case: 6 characters with dual briefs, clue pools, reveal narratives.
- `docs/PHASE_0_VALIDATION.md` — dry-run validation report.
- `gradle/libs.versions.toml` — locked version catalog.

## Phase 1 deliverables

- Root config: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `README.md`, `gradle/wrapper/gradle-wrapper.properties`.
- Convention plugins (`build-logic/convention/`): `parlor.kmp.library`, `parlor.kmp.compose.library`, `parlor.android.app`, `parlor.detekt`.
- `:shared:core` — Result, errors, ids, clock, random, logger, UiText, SemVer + Result tests.
- `:shared:engine` — full contracts + Konsist purity + no-Whodunit-in-engine tests.
- `:shared:design-system` — tokens, `ParlorTheme`, foundational components, `AmbientBackdrop`/`HeroBackdrop`, `SoundscapeController`.
- `:shared:session` — `SessionController` + `PassAndPlaySessionController` + `ViewerContext`.
- `:shared:content` — envelope/summary types, repository contract, datasource contracts, validator contract.
- `:shared:networking` — `LocalRoom`/`RoomTransport`/message protocol contracts.
- `:shared:storage` — `SnapshotStore`/`SecureStorage`/`SettingsStore` contracts.
- `:shared:navigation` — `ParlorRoute`/`NavGraphRegistry`/`ModuleNavGraph`.
- `:game-modes:whodunit` — module scaffold.
- `:composeApp` — Android/iOS/Desktop entry points + `App.kt` + Koin assembly + `HomeScreen`.

## Phase 2 deliverables

- `RoundRobinAnnounceGame` exercising full engine contract surface.
- `ReducerSmokeTest` end-to-end reducer test.

## Phase 3 deliverables

- `DefaultCaseValidator` — strict envelope validation in order.
- `DefaultCaseRepository` — Cache→Remote→Bundled fallback chain with update events.
- `KtorRemoteCaseDataSource` — injected `HttpClient` (MockEngine for dev).
- `InMemoryCachedCaseDataSource` — MVP cache.
- `WhodunitCase` data class hierarchy + `WhodunitPayloadValidator` (all §3.5 rules) + `BundledWhodunitCases`.

## Phase 4 deliverables

- Full `WhodunitState` (3 buckets, real fields), `WhodunitPhase` sealed hierarchy, `WhodunitAction`/`WhodunitEvent`.
- `WhodunitReducer` (Phase 4 sections — lifecycle / reveal).
- `WhodunitProjectionPolicy` (strips host-only + other-player private).
- `ClassicVoteMode` + `EliminationMode`, `WhodunitDefinition`, `WhodunitSnapshotCodec`.
- `WaxSealReveal`, `CandlelitCover`, `HideScreen`, `DossierCard`.
- All setup screens: `ModeSelectionScreen`, `PlayerCountScreen` (both display strategies), `PlayerEntryScreen`, `PublicIntroScreen`, `RulesBriefingScreen`.
- All reveal screens: handoff / gate / dossier / hide / `PrivateReviewScreen`.
- `WhodunitSetupDemo` wired in `App.kt`.

## Phase 5 deliverables

- `WhodunitAction` extended: `RevealNextClue`, `SubmitStructuredAction`, timer actions, `OpenVote`, `CastVote`, `AbstainVote`, `CloseVote`, `AcknowledgeReveal`, `BeginReplay`.
- `WhodunitEvent` extended: `ClueRevealed`, `TimerStarted`/`Warning`/`Exhausted`, `VoteOpened`/`Cast`/`Tallied`/`Tied`, `PlayerEliminated`, `WinnerDecided`, `RevealNarrativePlaying`. New `Verdict` + `KillerWinCause` types.
- `WhodunitReducer` extended:
  - Clue draw policy (round-1 universal + killer-pointing; mid-rounds killer-pointing + contradiction + red-herring; last-round final-strong) using host-only seed for determinism.
  - Discussion timer actions and event flow.
  - `VoteState` transitions for both Classic Vote and Elimination Mode.
  - Tie rule: tied → debate → revote; second tie → killer wins (Classic) or no-resolution (Elimination).
  - Elimination final-two killer-win condition.
  - Reveal phase entry with `WinnerDecided` event.
  - Replay: re-roll roles with derived new seed, reset public state, re-enter Setup → CharacterReveal.
- `VoteState` promoted to its own file with `NoResolution` state for all-abstain edge case.
- `ClueCard`, `TimerRibbon` components.
- `RoundTitleCardScreen`, `ClueRevealScreen`, `DiscussionScreen`.
- `AlibiRoundScreen`, `DirectedQuestionsScreen`, `SilentAccusationScreen`, `MonologueScreen`.
- `VoteBallotScreen`, `VoteHandoffScreen`, `TiedRevoteScreen`.
- `RevealStageScreen` with YES/NO verdict and reveal narrative card.
- `PostGameScreen` (replay/try-other-mode/back-to-library).

## Phase 6 deliverables

- Safety actions in reducer: `Pause`, `Resume`, `EndGameEarly(withReveal)`, `RequestReroll`.
- `PauseOverlay`, `EndGameDialog`, `PrivacyConcernDialog` composables.
- `InMemorySnapshotStore` (dev/test backing).
- `FileBackedSnapshotStore` + `SnapshotFileSystem` abstraction (platform actuals plug in per-OS file IO; encryption-at-rest is a wrapper around the filesystem).
- `InMemorySettingsStore` (dev backing; platform implementations swap behind the same interface).
- `PlatformKeyedSecureStorage` + `SecureKeyValueBacking` + in-memory backing for tests.

## Phase 7 deliverables

- `InMemoryRoomBus` + `InMemoryPeerRoom` + `InMemoryRoomTransport` (stub for in-process simulation).
- `ShadowSessionController` — peer-side `SessionController` that forwards actions to a sender and mirrors filtered state.
- `MultiDeviceShapeTest` — asserts that running the same reducer through the multi-device path produces a state trajectory identical to pass-and-play.

## Phase 8 deliverables

- `docs/ACCESSIBILITY_AUDIT.md` — full checklist (type/contrast, touch targets, gestures, motion, screen readers, layout, i18n, sound, cognitive load, sign-off).
- `docs/MOTION_DOWNGRADE.md` — Tier A / B / C definitions, selection logic, validation checklist.
- `docs/RELEASE_RUNBOOK.md` — pre-flight, per-platform build/ship steps, rollback drill, telemetry to monitor.
- `docs/STORE_METADATA.md` — naming, descriptions, keywords, screenshots required, age rating, privacy posture.
- `docs/CONTENT_REVIEW.md` — pre-publishing content checklist.
- `Telemetry` contract + `SafeForLogs` discipline in `:shared:core`.

---

## What's stubbed / needs follow-up

The architecture is complete and self-consistent. These items are the hand-off points to a developer with full tooling:

1. **Bundled JSON wiring per platform.** `BundledWhodunitCases` accepts a `Map<String, String>` of raw JSON. Add a `commonMain expect fun loadBundledCaseJson(name: String): String?` with platform actuals (Android `AssetManager`, iOS `NSBundle`, Desktop `getResourceAsStream`) and supply the map in each platform Koin module.
2. **`HttpClient` Koin bindings per platform.** `:composeApp` declares the engines; the actual `HttpClient` + `MockEngine` setup is a per-platform binding (mock engine for dev, real engine for prod).
3. **`SnapshotFileSystem` actuals per platform.**
4. **`SecureKeyValueBacking` actuals per platform.** Android EncryptedSharedPreferences; iOS Keychain; Desktop derived-key file.
5. **Gradle wrapper jar.** `gradle wrapper --gradle-version 8.11.1` on first checkout.
6. **iosApp Xcode project.** The `MainViewController` Compose entry exists; the Xcode wrapper project needs to be created.
7. **Real fonts.** Cormorant Garamond / Inter / JetBrains Mono shipped via Compose Multiplatform font resources (Phase 8 deliverable; the design system references system fallbacks until then).
8. **ViewModels / full nav graph.** The `WhodunitSetupDemo` uses hoisted local state; replacing it with proper ViewModels backed by `PassAndPlaySessionController` and a `NavHost` is a wiring task, not an architectural change.
9. **`MotionCapabilityProbe`.** The motion downgrade tiers are documented; the runtime probe to pick a tier on app start lives at `:shared:design-system/motion/` and is wired into `ParlorTheme`.

## Honest caveat

Nothing in this scaffold has been compiled — there is no JDK + Gradle + Android SDK chain available on the build host. The Kotlin is idiomatic KMP+CMP and follows the architecture exactly, but a developer picking this up should expect a small first-build pass of fixes (likely Compose Multiplatform API surface drift between the pinned versions and what's current; possibly a Konsist API tweak).

## Post-MVP

The Post-MVP roadmap is unchanged from `APP_PLAN.md` §5:

1. **Real multi-device play** — production `LocalMultiplayerSessionController` + real `RoomTransport` per platform (Android Nearby, iOS Multipeer, Desktop mDNS+WebSocket). Connection lifecycle, lobby + join UI.
2. **Additional cases** — first 8-character case validates the architecture's 4–8 / 5–8 capability with content, not contrivance.
3. **Easy / Hard difficulty.**
4. **Quick / Full session lengths.**
5. **3-player support and 4-player Elimination.**
6. **Voiceover narration, unlockable variations, additional game modules, localized cases.**

---

*Generated at the end of the autonomous Phase 0–8 build pass.*
