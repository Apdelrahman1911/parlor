# Parlor — Application Architecture

> Companion to `whodunit-game-design.md`.
> This document defines the application architecture **before any code is written**.
> It is the reference for every implementation decision that follows.

---

## 0. Overview

**Parlor** is a multi-platform party-games app. It is built as a generic platform that hosts multiple **game modules**. **Whodunit** (with its first case, *The Last Dinner*) is the first module shipped — it is not the whole product.

The architecture is driven by five non-negotiable constraints, in order:

1. **Whodunit is one of many.** Adding the second, third, and N-th game module must not require touching the platform, the engine, or any existing module.
2. **Two play topologies must coexist.** Pass-and-play (single device, MVP) and local multiplayer (multi-device, future) must share the same game logic. Only the I/O layer differs.
3. **Privacy is a first-class engine concern.** Public, per-player private, and host-only state are separated at the engine level — not bolted on by the UI.
4. **Content is API-driven from day one.** The app contains the engine; the backend delivers cases. No case content is hardcoded inline, ever.
5. **The UI bar is premium.** Not "MVP-grade." Cinematic, polished, atmospheric. The design-system module is built for this from the first commit.

These five constraints shape every section below.

---

## 1. Product Architecture

### 1.1 Two-layer product

```
┌──────────────────────────────────────────────────────────┐
│                  PARLOR — the app shell                  │
│  Home / Game Library  ·  Settings  ·  Content Library    │
│  Session orchestration  ·  Design system  ·  Engine      │
└──────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   ┌─────────┐         ┌─────────┐         ┌─────────┐
   │Whodunit │         │ Future  │         │ Future  │
   │ module  │         │ module  │         │ module  │
   │ (MVP)   │         │ (later) │         │ (later) │
   └─────────┘         └─────────┘         └─────────┘
```

- **Parlor** = the umbrella app: shell, library, design system, generic game engine, content delivery, session controllers, persistence, and platform glue.
- A **Game Module** = a self-contained gameplay package that plugs into the engine: a `GameDefinition`, one or more `GameMode`s, a content schema, presentation layer, UI screens, and a theme overlay.

### 1.2 Vocabulary (load-bearing)

| Term | Meaning |
|---|---|
| **GameModule** | A top-level game registered in the app (e.g., Whodunit). |
| **GameDefinition** | The engine-facing description of a module: id, metadata, supported modes, supported player counts, state/action/reducer wiring. |
| **GameMode** | A play variant *within* a module (e.g., Whodunit's Classic Vote and Elimination). |
| **GameSession** | A live instance of `GameDefinition × GameMode × player roster × content`. |
| **Case / Content** | The content payload a game consumes (e.g., the *Last Dinner* case for Whodunit). |
| **Play Topology** | How devices are arranged: pass-and-play or local multiplayer. |
| **Projection** | A filtered view of session state for a specific viewer (public, a player, the host). |

### 1.3 Extensibility contract

Every game module ships:

- A `GameDefinition` registered via the `GameRegistry`.
- A content schema extending the generic `CaseContent` base.
- A nav graph builder consumed by the root `NavHost`.
- A theme overlay on top of the Parlor base design system.
- A Koin DI module declaring its repositories, ViewModels, and use cases.

The shell knows nothing about Whodunit specifically. The home screen iterates the registry to render the *All Games* grid.

### 1.4 Player-count rules

The engine and modules treat player count as **declared configuration**, never a hardcoded number:

| Layer | Player-count constraint | Source |
|---|---|---|
| Engine (absolute) | `3..16` (generous; engine doesn't care) | Engine constant |
| `GameDefinition` (Whodunit) | `4..8` | Whodunit module |
| `GameMode.ClassicVote` | `4..8` | Whodunit module |
| `GameMode.Elimination` | `5..8` | Whodunit module |
| `Case` (e.g., *The Last Dinner*) | `4..N` where N = character pool size | Validated content |
| **Effective at runtime** | Intersection of all above | Computed |

*The Last Dinner* ships with 6 characters and so plays 4–6 (Classic) / 5–6 (Elimination). A future case with 8 characters will play 4–8 / 5–8 **without any engine, mode, or shell change**.

> **No layer of the codebase may hardcode `6` as a player ceiling.** Player counts always come from validated content or declared engine/mode capabilities.

### 1.5 Player Count screen — UI rule

The architecture commits to a **capability model** for player counts, not a display strategy.

**Capability model (architectural):**

- **Engine:** configurable, no hardcoded ceiling.
- **Whodunit module:** 4–8 (Classic Vote), 5–8 (Elimination).
- **Case:** capped by its validated character pool.
- ***The Last Dinner*:** 4–6 in MVP, unless the case is expanded with additional characters.

The Player Count selection screen computes its options from the **intersection** of these layers at runtime. The same source of truth is used in every release.

**Display strategy (product/UI decision, not architectural):**

The architecture supports either of two strategies without code or schema changes:

1. **Show-and-disable.** Render the module's full range; counts the selected case does not support are shown as disabled slots with an inline message (e.g., *"This case supports up to 6 players."*). Telegraphs future cases at the cost of slightly busier UX.
2. **Hide-unsupported.** Render only the counts the selected case supports. Cleaner at launch, especially while only one case ships.

Either strategy can be chosen per release without touching the engine, the Whodunit module, or the case schema. When a future 8-character case ships, the screen recomputes its options from the case's declared `supportedPlayerCounts` — **no code rework required** under either strategy.

For the *The Last Dinner* launch, the current product lean is **hide-unsupported** (cleaner, fewer disabled slots for players to interpret). This is recorded as a UI/product choice and can be revisited per release.

---

## 2. Platform Architecture

### 2.1 Stack

- **Kotlin Multiplatform** for all shared business logic.
- **Compose Multiplatform** for shared UI across Android, iOS, and Desktop.
- **Koin** for dependency injection (KMP-friendly, lightweight).
- **Ktor** for HTTP (multiplatform client).
- **kotlinx.serialization** for content JSON and snapshots.
- **kotlinx.coroutines + Flow** for async and reactive state.
- **SQLDelight** (or platform key-value where lighter) for local cache and persistence.

### 2.2 Targets

| Platform | Entry point | Notes |
|---|---|---|
| Android | `androidMain` + `MainActivity` | minSdk 26, target latest. Adaptive layout for phone + tablet. |
| iOS | `iosMain` + `MainViewController` + Xcode wrapper | iOS 16+. Compose Multiplatform iOS surface. |
| Desktop | `desktopMain` + `main()` | JVM, packaged for macOS, Windows, Linux. |

### 2.3 Source-set discipline

Code lives in **commonMain** by default. Platform source sets only host:

- Platform API shims behind `expect`/`actual` (storage roots, secure clipboard wipe, share sheet, vibration, status-bar insets, sound playback if needed).
- Platform-specific launchers (`MainActivity`, `MainViewController`, `Main.kt`).

If a feature lands in `androidMain` only, it is a bug unless it has a documented platform reason.

### 2.4 Single CMP app module

The app is a single `:composeApp` Gradle module with `commonMain`, `androidMain`, `iosMain`, `desktopMain` source sets. iOS additionally has a thin Xcode project (`:iosApp`) that embeds the framework. Desktop is packaged via Compose Multiplatform's Gradle packaging.

---

## 3. Module Structure

### 3.1 Module tree

```
parlor/
├── build-logic/                       # Gradle convention plugins
├── gradle/libs.versions.toml          # Version catalog
├── composeApp/                        # KMP+CMP app module
├── iosApp/                            # Xcode wrapper (not Gradle)
└── shared/
    ├── core/                          # pure Kotlin: Result, ids, time, logging contract
    ├── design-system/                 # Parlor base tokens, components, motion, theme
    ├── engine/                        # generic game engine (no Whodunit refs)
    ├── session/                       # SessionController contract + implementations
    ├── content/                       # case schema base, repository, validation, cache
    ├── networking/                    # LocalRoom + transport contracts (stub for MVP)
    ├── storage/                       # snapshot persistence, settings, secure storage
    └── navigation/                    # type-safe routes, NavGraphRegistry
└── game-modes/
    └── whodunit/                      # the Whodunit game module
```

### 3.2 Dependency rules

```
composeApp ──▶ shared/*  ──▶ game-modes/*
                  ▲              │
                  └──────────────┘   (game-modes depend on shared/*,
                                      never the reverse)
```

- `:composeApp` depends on all `:shared:*` and all `:game-modes:*`.
- Each `:game-modes:*` module depends only on `:shared:*`.
- `:shared:*` modules **never** depend on `:game-modes:*`. The shell and engine know nothing about specific games.
- Within `:shared`: `:core` has no internal deps; `:engine` depends only on `:core`; `:content` depends on `:core` + `:engine`; `:design-system` depends on `:core`; `:session` depends on `:core` + `:engine` + `:networking`; `:storage` depends on `:core`.

### 3.3 Purity rules (load-bearing)

The most important non-circular rule: **the engine is pure Kotlin and unaware of frameworks.**

**`:shared:engine` allowlist** — may depend on:

- Kotlin stdlib
- `kotlinx.coroutines` (for `Flow` types in the session API)
- `kotlinx.serialization` (for snapshot codec annotations; the `Json` instance is injected, not constructed in-engine)
- `:shared:core`

**`:shared:engine` denylist** — must **not** depend on:

- Koin or any DI framework. Collaborators enter via constructor parameters; DI assembly happens at the app boundary.
- Compose, Compose Multiplatform, or any UI library. No `Modifier`, `Color`, `UiText`, or other UI concepts in engine state.
- Ktor, SQLDelight, or any I/O library.
- Platform APIs. No `expect`/`actual` in engine; the engine has no idea what platform it runs on.
- `:shared:networking`, `:shared:storage`, `:shared:design-system`, or any `:game-modes:*`.

The same purity rule applies elsewhere:

- **`:shared:core`** — Kotlin stdlib only; kotlinx.coroutines and kotlinx.serialization permitted. No framework dependencies of any kind.
- **Game module `domain/` packages** (e.g., `:game-modes:whodunit/domain/`) — same rules as the engine. UI, presentation, and DI live in sibling packages (`ui/`, `presentation/`, `di/`), never inside `domain/`. A `domain/` file that needs to know what color something is, or who supplies it, is mis-layered.

Enforcement: a static-check rule (a Konsist architecture test, or a detekt custom rule) fails the build on violations. The rule lives next to the convention plugins.

### 3.4 Convention plugins

`build-logic/convention/` ships:

- `parlor.kmp.library` — base KMP library setup, common test deps, kotlinx.
- `parlor.kmp.compose.library` — KMP + Compose Multiplatform UI library.
- `parlor.android.app` — Android app module config.
- `parlor.detekt` — static analysis baseline.

All shared modules opt into one plugin. No raw KMP boilerplate is repeated across modules.

### 3.5 Game module shape

A game module follows a fixed internal layout (see §13 for the Whodunit example). This consistency makes a future second module mechanically obvious to scaffold.

---

## 4. Game Engine Design

The engine is generic. It contains **no Whodunit-specific terms** — no "killer," no "vote," no "clue," no "dossier." It is also **pure Kotlin**: no DI framework, no UI library, no I/O, no platform APIs. Time, randomness, persistence, and timer scheduling enter via interfaces from `:shared:core`. The reducer is a pure function. See §3.3 for the full allowlist and denylist.

### 4.1 Core abstractions

```kotlin
// shared/engine
@JvmInline value class GameId(val raw: String)
@JvmInline value class ModeId(val raw: String)
@JvmInline value class PlayerId(val raw: String)
@JvmInline value class SessionId(val raw: String)

interface GameDefinition<S : GameState, A : GameAction, E : GameEvent> {
    val id: GameId
    val metadata: GameMetadata
    val supportedModes: List<GameMode>
    val supportedPlayerCounts: IntRange
    fun createInitialState(config: SessionConfig): S
    fun reducer(): GameReducer<S, A, E>
    fun projectionPolicy(): ProjectionPolicy<S>
    fun snapshotCodec(): SnapshotCodec<S>
}

interface GameMode {
    val id: ModeId
    val displayName: LocalizedString
    val supportedPlayerCounts: IntRange
    val estimatedDuration: DurationRange
}

interface GameState {
    val phase: GamePhase
    val players: List<Player>
}

interface GamePhase             // marker; modules supply sealed phases
interface GameAction            // marker; modules supply sealed actions
interface GameEvent             // marker; modules supply sealed events

abstract class GameReducer<S : GameState, A : GameAction, E : GameEvent> {
    abstract fun reduce(state: S, action: A, ctx: ReducerContext): Reduction<S, E>
}

data class Reduction<S, E>(val newState: S, val events: List<E> = emptyList())
```

### 4.2 Session contract

```kotlin
interface GameSession<S : GameState, A : GameAction, E : GameEvent> {
    val publicState: StateFlow<PublicProjection<S>>
    fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>>
    val hostState: StateFlow<HostProjection<S>>?     // only on the host device
    val events: SharedFlow<E>
    suspend fun submit(by: PlayerId?, action: A): Result<Unit, SubmitError>
    suspend fun snapshot(): GameSnapshot
    suspend fun restore(snapshot: GameSnapshot)
    suspend fun close()
}
```

A `GameSession` is the running instance. It owns the canonical state on whichever device hosts it (pass-and-play: the only device; multi-device: the host).

### 4.3 State containers and projections

```kotlin
data class GameStateContainer<P, Pr, H>(
    val public: P,
    val privatePerPlayer: Map<PlayerId, Pr>,
    val hostOnly: H,
    val phase: GamePhase,
    val players: List<Player>,
) : GameState

interface ProjectionPolicy<S : GameState> {
    fun toPublic(state: S): PublicProjection<S>
    fun toPlayer(state: S, playerId: PlayerId): PrivateProjection<S>
    fun toHost(state: S): HostProjection<S>
}
```

The engine guarantees that any consumer of `PublicProjection` sees only public data; the type system prevents leaks. See §7 for the privacy model.

### 4.4 Phase / state-machine model

- Phases are sealed types defined by each game module.
- The reducer is a **pure** function `(State, Action) → Reduction(State, Events)`.
- No side effects in the reducer. Side effects (timers, sound, persistence) are driven by **events** emitted by the reducer, consumed by the session controller or UI.
- Time-based phase transitions (round timers, discussion countdowns) flow through `TimerService` — an engine-provided side-effect runner that emits `TimerTicked`/`TimerExpired` actions back into the reducer.

### 4.5 Snapshots and serialization

- Every `GameDefinition` ships a `SnapshotCodec<S>` (kotlinx.serialization).
- Snapshots are written by `:shared:storage` on every major phase change.
- Restore is exact-state, including pending timers and pending actions.
- Snapshots are versioned. Engine-version mismatches refuse restore and offer the user a clean restart.

### 4.6 Game registry

```kotlin
interface GameRegistry {
    val all: List<GameDefinition<*, *, *>>
    fun byId(id: GameId): GameDefinition<*, *, *>?
}
```

The registry is populated by Koin at startup. Each game module contributes its definition via its DI module. The shell uses the registry to render the library and route into the right module.

---

## 5. Whodunit Implementation Layer

Whodunit is the first module that fills the engine's slots. Nothing here lives in `:shared:*`.

### 5.1 Mapping engine concepts to Whodunit

| Engine concept | Whodunit binding |
|---|---|
| `GameDefinition` | `WhodunitDefinition` |
| `GameMode` | `ClassicVoteMode`, `EliminationMode` |
| `GameState` (container) | `WhodunitState` (public + per-player + host-only) |
| `GamePhase` | `WhodunitPhase` (sealed) |
| `GameAction` | `WhodunitAction` (sealed) |
| `GameEvent` | `WhodunitEvent` (sealed) |
| `Player` | `Player` (engine) — Whodunit attaches role via private state |
| Content (case) | `WhodunitCase`, validated by `WhodunitCaseValidator` |

### 5.2 Whodunit phases (sealed)

```
Setup
  ↓
PublicIntro
  ↓
RulesBriefing
  ↓
CharacterReveal (per-player loop)
  ↓
Round(n)            ← n ∈ 1..(roundCount(playerCount))
  ├── ClueReveal
  ├── StructuredAction (Alibi | QuestionRound | SilentAccusation | Monologue)
  ├── Discussion
  └── (Elimination mode) RoundVote → RoundElimination
  ↓
FinalVote           (Classic Vote) or skipped (Elimination)
  ↓
TiedRevote?         (conditional)
  ↓
Reveal
  ↓
PostGame
```

Pause and PrivateReview are **modal overlays**, not phases — they suspend the active phase without changing it. The reducer enforces "private review is unavailable during a vote or private clue delivery" via a guard inside the action.

### 5.3 Whodunit state (sketch)

```kotlin
// Public — visible to everyone in the room
data class WhodunitPublic(
    val caseId: CaseId,
    val playersAtTable: List<Player>,
    val eliminatedPlayers: List<PlayerId>,         // Elimination mode
    val currentRound: Int,
    val revealedClues: List<RevealedClue>,
    val voteState: VoteState,
    val timerState: TimerState,
)

// Per-player private — only visible to that player
data class WhodunitPrivate(
    val role: PlayerRole,                          // Innocent | Killer
    val dossier: DossierContent,                   // Must Read + Optional Details (filtered by role)
    val instructionsForRole: RoleInstructions,
)

// Host-only — never leaves the host device
data class WhodunitHostOnly(
    val killerId: PlayerId,
    val killerVariantSeed: Long,                   // determines clue trail
    val redHerringTarget: PlayerId,
    val cluePoolDraw: ClueDrawPlan,                // chosen clues per round, per killer
)
```

### 5.4 Whodunit actions and events

Actions (player or host inputs):

```
WhodunitAction:
  AssignRoles(seed: Long)                   // host-only, run once at session start
  AdvanceFromIntro
  AdvanceFromBriefingCard(index: Int)
  StartCharacterReveal(playerId)
  CompleteCharacterReveal(playerId)
  OpenPrivateReview(playerId)
  CloseHide(playerId)
  RevealNextClue
  SubmitStructuredAction(action: StructuredActionInput)
  StartDiscussionTimer / PauseTimer / ResumeTimer / ExtendTimer
  CastVote(voter: PlayerId, target: PlayerId)
  CloseVote
  AcknowledgeReveal
  RequestReroll                            // accidental exposure flow
  EndGameEarly(reason)
```

Events (engine → UI/audio side effects):

```
WhodunitEvent:
  RolesAssigned                            // host-only event
  PhaseEntered(phase)
  ClueRevealed(clue)
  PlayerEliminated(playerId, wasKiller: Boolean)   // Elimination mode
  VoteTied(tiedPlayers)
  WinnerDecided(winner: Verdict)
  RevealNarrativePlaying
  PrivacyConcernRaised
```

### 5.5 Content binding

Whodunit's content schema extends the generic `CaseContent` base with Whodunit-specific fields (characters, clue pools, reveal narratives — see §8). The reducer receives the validated case content via the session config and uses it for clue draws, dossier text, reveal narratives, and the dynamic killer trail.

**The reducer does not generate prose.** All text — public intro, dossiers, clues, reveal narratives — comes from validated content. The reducer chooses *which* content is shown, never *what it says*.

### 5.6 Dynamic Killer System placement

- Role assignment is a single `AssignRoles(seed)` action executed at session start before any character reveal.
- The seed is host-only state; clients see only their own resulting role.
- The killer's variant determines the killer-pointing clue pool, the red-herring target, and the final clue — all read from validated content.
- Re-roll (accidental exposure) issues a fresh `AssignRoles` with a new seed and re-enters `CharacterReveal` from the start.

---

## 6. Play Topology Abstraction

### 6.1 The contract

```kotlin
interface SessionController<S : GameState, A : GameAction, E : GameEvent> {
    val publicState: StateFlow<PublicProjection<S>>
    fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>>
    val hostState: StateFlow<HostProjection<S>>?
    val events: SharedFlow<E>
    val activeViewer: StateFlow<ViewerContext>     // see below

    suspend fun submit(action: A): Result<Unit, SubmitError>
    suspend fun setActiveViewer(viewer: ViewerContext)
    suspend fun pause()
    suspend fun resume()
    suspend fun close()
}

sealed interface ViewerContext {
    object Public : ViewerContext
    data class Player(val id: PlayerId) : ViewerContext
    object Host : ViewerContext
}
```

The `SessionController` is the I/O boundary. The game reducer is identical across topologies — only how state is *distributed* and how actions are *submitted* differs.

### 6.2 PassAndPlaySessionController

- Owns the full `GameSession` locally.
- All projections are computed locally; the UI chooses which projection to render based on the current `activeViewer`.
- `activeViewer` changes when the UI advances through the pass-and-play ceremony:
  - Cover screen → `Public`
  - Hold-to-reveal completes for Player X → `Player(X)`
  - Hide screen → `Public`
- Submissions are tagged with `activeViewer`'s player id (if applicable).
- Privacy enforcement is **UI ceremony**: cover screens, hold-to-reveal, hide-and-pass. State is never directly displayed without going through the right screen.

### 6.3 LocalMultiplayerSessionController (future)

- Hosted on one device. Peers connect via `LocalRoom`.
- The host runs the canonical session. The reducer lives only on the host.
- Each peer device runs a **shadow controller** that exposes `SessionController` to its local UI by:
  - Receiving filtered `PublicProjection` and that peer's `PrivateProjection` over the transport.
  - Forwarding local action submissions to the host.
  - Subscribing to broadcasted events.
- Privacy enforcement is **transport-level**: private data for player X is sent only to X's device; host-only data never leaves the host.
- No code in the Whodunit reducer or UI changes between topologies. The screens read from `SessionController` identically.

### 6.4 Why this boundary

Drawing the line at `SessionController` rather than at the reducer means:

- The reducer stays pure and topology-agnostic.
- The UI is topology-agnostic.
- Building a third topology later (e.g., remote play) is a new `SessionController` implementation, not a rewrite.

---

## 7. Privacy Model

This is the most important architectural commitment after extensibility.

### 7.1 Three state buckets

| Bucket | Visibility | Example (Whodunit) |
|---|---|---|
| **PublicState** | Everyone in the room | Case intro, phase, revealed public clues, vote counts after vote closes, eliminated players (Elimination), timer state |
| **PrivatePlayerState** | The owning player only | That player's role (Innocent/Killer), their dossier (Must Read + Optional), role-specific instructions |
| **HostOnlyState** | The host device only | Killer identity, killer variant seed, red-herring target, undrawn clue pool, future role-reroll seeds |

These buckets are **types**, not naming conventions. The compiler prevents `HostOnlyState` from ending up inside `PublicProjection`.

### 7.2 Projections

```kotlin
sealed interface Projection<S>

data class PublicProjection<S>(val state: S) : Projection<S>
data class PrivateProjection<S>(val state: S, val playerId: PlayerId) : Projection<S>
data class HostProjection<S>(val state: S) : Projection<S>
```

Each `GameDefinition` provides a `ProjectionPolicy` that:

- Strips host-only fields when projecting to public.
- Strips other players' private fields when projecting to a specific player.
- Strips nothing when projecting to host.

### 7.3 Pass-and-play enforcement

In pass-and-play, all three buckets sit in memory on one device. **Privacy is enforced by the UI ceremony**, gated by:

- `activeViewer` on the `SessionController` — only one ViewerContext is "live" at a time.
- Cover screens that fully occlude content during transitions.
- Hold-to-reveal gestures (1.5 s) that prevent accidental flashes.
- Hide screens after reveal that require an explicit dismissal.

The UI must never render a private projection without first transitioning through the cover/reveal/hide ceremony.

### 7.4 Local multiplayer enforcement (future)

In multi-device, privacy is enforced by the **transport** before data ever reaches a peer:

- Host computes `PublicProjection` → broadcasts to all.
- Host computes `PrivateProjection(playerId)` → sends only to that player's device.
- `HostOnlyState` is never transmitted.
- A peer device cannot request another peer's private state. The host refuses; the protocol has no message for it.

### 7.5 Sensitive lifecycle

- Snapshots written to disk include all buckets but are encrypted-at-rest via platform keystore wherever available (Android Keystore, iOS Keychain). Desktop uses a derived key from a per-install secret.
- On `EndGame` or app foreground change during a sensitive phase, the UI dims to a cover screen.
- Clipboard is never used to carry private text.
- Logs at any severity strip `PrivatePlayerState` and `HostOnlyState`. A linter rule enforces this.

### 7.6 The discipline

> **Adding a new piece of private information is a deliberate act.** It is declared in one of the three buckets; the projection policy is updated; the type system enforces the rest.

---

## 8. Content Architecture

### 8.1 The split (per design doc §23)

- **The app contains the engine.**
- **The backend contains the cases.**
- Backend may ship: text (intros, dossiers, clues, reveal narratives), character data, clue pool composition, killer variants, theme metadata, supported player counts per case, supported modes per case, localized variants.
- Backend may **not** ship: code, new game rules, timer values, voting logic, safety overrides, or anything the installed engine doesn't already understand.

### 8.2 Generic case content (base schema)

```kotlin
// shared/content
@Serializable
data class CaseEnvelope(
    val schemaVersion: Int,                  // checked first
    val caseId: String,
    val title: String,
    val version: Int,                        // content version, changes when patched
    val minimumAppVersion: SemVer,
    val gameId: String,                      // which GameDefinition this case targets
    val supportedPlayerCounts: IntRange,
    val supportedModes: List<String>,        // ModeId strings the engine must recognize
    val language: LanguageCode,
    val theme: String,
    val estimatedDuration: DurationRange,
    val payload: JsonElement,                // game-specific schema (e.g., WhodunitCase)
    val signature: String? = null,           // optional integrity check
)
```

Each game module defines its **payload** schema. Whodunit's payload is `WhodunitCase` (characters, dossier briefs, clue pools, reveal narratives).

### 8.3 Repository and data sources

```kotlin
interface CaseRepository {
    suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError>
    suspend fun loadCase(id: CaseId): Result<ValidatedCase<*>, DataError>
    fun observeCacheUpdates(): Flow<CaseUpdate>
    suspend fun refresh(gameId: GameId): Result<Unit, DataError>
}

interface RemoteCaseDataSource {
    suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError>
    suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError>
}

interface BundledFallbackCaseDataSource {
    fun availableCases(): List<CaseSummary>
    fun loadBundled(id: CaseId): CaseEnvelope?
}

interface CachedCaseDataSource {
    suspend fun get(id: CaseId): CaseEnvelope?
    suspend fun put(envelope: CaseEnvelope)
    suspend fun invalidate(id: CaseId)
}
```

The repository orchestrates:

1. **First open of a case:** try Remote → on success, put in Cache → return. If Remote fails, fall back to Cache → if Cache empty, fall back to Bundled (for the bundled fallback case only).
2. **Subsequent opens:** read from Cache. Refresh in the background; if the backend reports a newer version, invalidate and refresh.
3. **Offline:** Cache + Bundled only. The library reflects this state in the UI (a soft "Offline — playing cached cases" banner is acceptable).

### 8.4 Validation

`CaseValidator` is strict, in order:

1. **Schema version.** If `schemaVersion` is greater than the highest schema the installed app knows, the case is unplayable. UI shows "Update required."
2. **Minimum app version.** If `minimumAppVersion` > installed app version, case is unplayable.
3. **GameId resolves.** `gameId` must match a registered `GameDefinition`.
4. **Type validation.** Every field is type-checked via kotlinx.serialization plus explicit constraints.
5. **Structural validation.** `supportedPlayerCounts` ⊆ engine's GameDefinition range. `supportedModes` ⊆ definition's modes. Each declared killer variant matches a character. Clue pools align with declared modes. No dangling references.
6. **Payload validation.** Delegated to the game module's `PayloadValidator` (e.g., `WhodunitPayloadValidator`).

A validated case is wrapped in `ValidatedCase<TPayload>` — an opaque token signaling that everything downstream may trust it.

### 8.5 Failure modes

| Failure | Behavior |
|---|---|
| Schema version too new | Hide or label "Update required." Never start. |
| App version too old | Same. |
| Game id unknown | Same. |
| Type/structural error | Same. Reported to telemetry for backend triage. |
| Missing optional content | Hide the optional UI affordance. Case plays. |
| Missing required content | Case unplayable. |
| Backend unreachable | Cached cases playable. New cases unavailable. Soft banner. |
| Brand-new install + no network | Bundled fallback case (*The Last Dinner*) is playable. |

> **The app never crashes, freezes, or leaks information because of bad or missing case content.** Validation is strict; fallback is graceful.

### 8.6 Caching, versioning, rollback

- Cache key is `(caseId, version)`; updates invalidate older versions.
- Backend signals new versions either via a manifest endpoint or via `Cache-Control` + ETag.
- Rollback (per design doc): backend re-publishes an older approved version with a fresh timestamp; clients invalidate and refresh.

### 8.7 The bundled fallback case

- *The Last Dinner* ships as a bundled JSON asset in `:shared:content` (or in `:game-modes:whodunit` if it's Whodunit-specific — it is, so it lives there).
- The bundled version is refreshed at each app release to stay reasonably close to the live API version.
- Conflict policy: if both cache and bundle hold the same `caseId`, prefer the higher `version`.

### 8.8 The "fake backend from day one" rule

- The MVP ships with a **mock content source** that serves a real HTTP-shaped JSON response (either a local static file served via Ktor's mock engine, a CDN URL with a static JSON, or a tiny dev server).
- The `RemoteCaseDataSource` is the **only** code path for content. There is no shortcut "load embedded Whodunit data class" path. The bundled fallback also flows through the validator before use.
- Hardcoding case content inline anywhere in the source tree is a CI-flagged offense.

---

## 9. Networking Future-Proofing

The MVP does **not** implement networking. The MVP **does** define the contracts so that multi-device play is a drop-in later.

### 9.1 LocalRoom contract

```kotlin
// shared/networking
interface LocalRoom {
    val info: StateFlow<RoomInfo>                // code, room name, host id, status
    val members: StateFlow<List<RoomMember>>
    val isHost: Boolean
    val incoming: Flow<RoomMessage>
    suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError>
    suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError>
    suspend fun leave()
}

sealed interface SendTarget {
    object Broadcast : SendTarget
    data class Direct(val playerId: PlayerId) : SendTarget
}
```

### 9.2 Message protocol

```kotlin
sealed interface HostMessage {
    data class PublicStateSnapshot(val data: ByteArray) : HostMessage
    data class PublicStateDelta(val patch: ByteArray) : HostMessage
    data class PrivateStateForPlayer(val target: PlayerId, val data: ByteArray) : HostMessage
    data class EventBroadcast(val data: ByteArray) : HostMessage
    data class EventDirect(val target: PlayerId, val data: ByteArray) : HostMessage
    data class TimerSync(val timerId: String, val deadlineEpochMs: Long) : HostMessage
    object EndSession : HostMessage
}

sealed interface PeerMessage {
    data class JoinRequest(val displayName: String) : PeerMessage
    data class ActionSubmit(val data: ByteArray) : PeerMessage
    object Heartbeat : PeerMessage
    object LeaveNotice : PeerMessage
}
```

Action and state payloads are serialized with the same `SnapshotCodec` the engine uses for disk snapshots, so the format is shared.

### 9.3 Transport adapter

```kotlin
interface RoomTransport {
    val capability: TransportCapability         // discovery, latency hint, max payload
    suspend fun host(config: HostConfig): Result<TransportSession, NetError>
    suspend fun join(code: String): Result<TransportSession, NetError>
}
```

Future implementations (one per platform via `expect`/`actual` or via DI):

- **Android:** Nearby Connections (recommended for in-room play).
- **iOS:** MultipeerConnectivity.
- **Desktop:** mDNS + WebSocket on the local network.

The choice is below the `LocalRoom` line. Switching transports does not affect the session controller, reducer, UI, or content layers.

### 9.4 Connection lifecycle (future)

- **Host disconnect** during play: peers receive `EndSession` (best-effort). UI offers "Game ended — last screen preserved" with an option to end gracefully.
- **Peer disconnect:** host pauses the game; UI offers "Continue without [Name] (reveal now / end)" matching the existing leave-mid-game rules.
- **Re-join:** Post-MVP; out of scope for the contract.

### 9.5 What MVP ships

- All the **interfaces** above in `:shared:networking`.
- A no-op `RoomTransport` (or none at all — the module's API is present but not yet consumed by any UI screen).
- The production `LocalMultiplayerSessionController` is **not** implemented in MVP. A tiny in-memory **shape test** (~50–100 lines) exercises the abstraction with a stub multi-device controller in Phase 7 — enough to keep the abstraction honest, not a real implementation. Its production skeleton lives next to `PassAndPlaySessionController` so adding it later is mechanical.

---

## 10. UI Architecture

### 10.1 Stack

- **Compose Multiplatform** for all screens.
- Common composables and design tokens live in `:shared:design-system` (Parlor base) and `:game-modes:whodunit/ui` (Whodunit overlay).
- Per-platform tweaks (insets, status bar, IME) are isolated in platform source sets.

### 10.2 Premium UI Quality Bar (load-bearing)

> The Parlor UI is **not** basic. It is built to a premium product bar from the first commit. The architecture supports this — it does not retrofit it later.

**Mandatory qualities for every screen in every module:**

- **Professional**, **polished**, **clean** — no placeholder visuals, no default Material defaults bleeding through.
- **Cinematic** — screens compose like film frames; hero moments are paced like scenes.
- **Premium-feeling** — restrained palettes, refined typography, depth via shadow and bloom, careful negative space.
- **Highly atmospheric per game** — each module owns an atmosphere; the shell honors and amplifies it.

**Non-deferrable.** Whodunit's cinematic identity is not a "finalize-later" pass over functional placeholders. Every Whodunit screen ships at the premium bar from its **first commit**. There is no "ship functional, polish next sprint" path. If a screen cannot meet the bar in its first delivery, it is not delivered — but at no point does a placeholder visual reach a build that anyone outside the team plays. The same discipline applies to every future game module.

This bar is enforced architecturally by:

#### 10.2.1 A real design-system module

`:shared:design-system` is not a kitchen of helpers. It is a styled component library:

- **Tokens** — color, typography, spacing, elevation, motion timings, blur radii, corner radii. All themable per module.
- **Components** — `ParlorCard`, `ParlorButton`, `ParlorBottomSheet`, `ParlorDialog`, `ParlorCarousel`, `ParlorListItem`, `RevealSurface`, `CoverSurface`, `TimerRibbon`, `AmbientBackdrop`. All built with motion and atmosphere baked in.
- **Motion primitives** — named timings (`Motion.slow`, `Motion.theatrical`, `Motion.ember`) and easings. Reusable transition templates (`reveal`, `cover`, `cardRise`, `crossDissolve`).
- **Sound primitives** — a `SoundscapeController` with named cues (`reveal`, `cover`, `chime`, `tick`, `gasp`, `wax-seal`). Implementation is platform-shimmed.
- **Backdrop system** — every module supplies an `AmbientBackdrop` composable that lives behind navigation transitions; it's never visually flat.

#### 10.2.2 Whodunit's theme: Cozy Noir

Whodunit ships a `CozyNoirTheme` overlay on the Parlor base. Concrete direction:

- **Color** — warm near-blacks (`#0B0807`, `#14100D`), ember-orange accent (`#D97A2A` family with a layered glow at lower-alpha rings), brass and aged-parchment highlights. Pure black is banned; the palette is candlelit, not flat.
- **Typography** — a refined transitional or Didone serif for display (case titles, dossier names, reveal narrative). A humanist sans-serif for body and UI affordances. Generous tracking on small caps; italic accents for narration; light tabular figures for timers and counts.
- **Surface treatment** — layered cards with warm shadows, soft glow rims, subtle bevel and embossed edges. Generous padding. Breathing room around hero text. No card edge sits flush against another.
- **Texture and depth** — a low-opacity grain overlay sitting above background; a slow candle-flicker on key surfaces (no Halloween jitter; a real flame's rhythm); vignettes on hero screens; warm bloom around emissive accents.
- **Motion language** — fades and rises in the 350–600 ms range. **Reveal moments** are paced: wax seal pulses (1.5 s hold), then breaks; dossier rises into view from below the seal; cover screens cross-dissolve to candle-glow then to content. Never a snap.
- **Whodunit-specific components** — `WaxSealReveal`, `EmberPulse`, `CandlelitCover`, `DossierCard` (Must Read + Optional split), `ClueCard`, `PlayerRoster` (with dimmed seats for eliminated players), `RevealStage` (the final reveal), `VoteBallot`, `MonologueTimer`.
- **Sound design** — distant piano, ticking clock at low volume, soft creak on transitions, a wax-seal crack on reveal, a soft chime on phase change. Cooperate with motion; never play sound without a corresponding visual beat.

#### 10.2.3 Accessibility within the premium aesthetic

The premium bar is not in tension with accessibility — they reinforce each other:

- Type sizes are **large** so the table can read across it (body 16–18 sp, dossier 18–20 sp, headings 28–36 sp). The design system enforces minimums.
- Contrast on dark surfaces is checked against WCAG AA at minimum; ember accents on near-black meet contrast for non-decorative use.
- Every hold-to-reveal gesture has a tap-confirmation fallback for motor accessibility.
- Every interactive element ships `contentDescription`. The design-system `ParlorButton` etc. require it.
- Motion is respectful: a user-level "reduce motion" setting collapses theatrical reveals to dignified cross-dissolves without breaking the flow.

#### 10.2.4 Future modules

Every future game module brings its own theme overlay (a bright party-neon palette, a serene daylight palette, etc.). The Parlor base does not impose Cozy Noir — it imposes **quality**. The bar travels; the aesthetic per module does not.

### 10.3 Navigation

- **Type-safe Compose Navigation** with route objects, not strings.
- Root `ParlorNavHost` lives in `:composeApp` and assembles:
  - Shell graph: Home, GameDetails, Settings, How-to-Play.
  - One graph per registered game module, contributed via the `NavGraphRegistry`.
- Cross-module navigation uses well-typed callbacks at the graph boundary, never deep-links into another module's internals.

### 10.4 High-level screen list

The architecture supports the full Whodunit screen set, plus future modules' screens via their own graphs.

**Shell-level (Parlor):**

- Home / Game Library
- Game Details
- Settings
- How to Play (illustrated walkthrough)

**Setup (per module, Whodunit example):**

- Mode Selection (Classic Vote vs Elimination)
- Player Count
- Player Entry (names)
- Public Intro (read at the table)
- Rules Briefing Carousel

**Reveal (per module):**

- Character Reveal Handoff ("Pass to [Name]")
- Character Reveal Dossier (Must Read + Optional Details)
- Hide-and-Pass Cover

**Round (per module):**

- Round Title Card
- Clue Reveal
- Structured Action Prompt (Alibi / Question / Silent Accusation / Monologue)
- Discussion Timer

**Vote and Reveal (per module):**

- Voting Ballot (Classic — once; Elimination — each round)
- Tied Revote
- Round Elimination Reveal (Elimination only)
- Final Reveal Stage

**Post-Game (per module):**

- Replay menu (replay case / try other mode / back to library)

**Modal overlays (per module):**

- Pause
- Private Review (cover → reveal → hide)
- Privacy Concern / Reroll
- End Game Early

These are **architectural slots**, not screens to build today. Phase 4–5 builds them at the premium quality bar.

---

## 11. State Management

### 11.1 Pattern

- **MVI** — immutable state, sealed action types, sealed event types.
- One **state holder** per screen. Where the platform supports it, this is a KMP-shared ViewModel (via `androidx.lifecycle.viewmodel` multiplatform or an equivalent abstraction); otherwise per-platform thin wrappers around a shared presenter.
- State holders expose:
  - `StateFlow<UiState>` — the rendered screen state.
  - `SharedFlow<UiEvent>` — one-shot events (navigate, toast, sound).
  - `fun onAction(action: UiAction)` — input.

### 11.2 Boundary between screen state and game state

| Layer | Owns | Examples |
|---|---|---|
| **Game state** (engine) | Authoritative game truth | `WhodunitState`, phase, votes, clues |
| **Screen state** (presentation) | UI-only state | Text-field contents, dialog open/closed, loading flags, animation progress |

A screen ViewModel observes the relevant projection from `SessionController` and maps it to its `UiState`. It never owns gameplay truth.

### 11.3 Actions routing

```
UI → ViewModel.onAction(UiAction) → either:
   a) SessionController.submit(GameAction)        // for gameplay
   b) UseCase.invoke(...)                         // for non-gameplay
```

- Game actions go through the session controller (which routes to the host in multi-device).
- Non-gameplay actions (loading the library, fetching content, opening settings) go through dedicated use cases against `:shared:content`, `:shared:storage`, etc.

### 11.4 Save / restore

- **Engine-level snapshots** persist every meaningful phase change to `:shared:storage`. Restore is exact-state.
- **UI-level transient state** is persisted via the platform's saved-state mechanism (Android `SavedStateHandle`, equivalents on iOS/Desktop) for things like a half-typed player name during config-change.
- **Process death recovery:** on cold start, the storage layer surfaces "Resume your last game?" if an unfinished snapshot exists.

### 11.5 Side effects

- Reducers are pure. Side effects come out as **events**.
- A `SessionEffectRunner` consumes engine events and drives:
  - Timer scheduling (which feeds `TimerTicked` actions back in).
  - Sound playback via `SoundscapeController`.
  - Persistence (snapshot writes on phase changes).
  - UI motion cues via screen-level events.
- This isolation makes the reducer testable as a pure function.

### 11.6 Error handling

- Standard `Result<T, E>` wrapper across the codebase.
- Typed error hierarchies per layer (`DataError`, `NetworkError`, `ValidationError`, `SubmitError`).
- UI maps errors to `UiText` strings via a single resolver — no raw exception text reaches the user.

---

## 12. MVP Boundaries

### 12.1 In MVP

- One game module: **Whodunit**.
- One playable case: ***The Last Dinner*** (4–6 players in practice, capped by its 6-character pool).
- Two game modes: **Classic Vote (4–6 in MVP)** and **Elimination (5–6 in MVP)** — modes themselves accept up to 8 players.
- One difficulty: **Medium**, public clues only.
- One session length: **Classic** pacing.
- **Pass-and-play only** as the implemented play topology.
- **Local multiplayer contracts present.** `SessionController` interface, `LocalRoom`, `RoomTransport`, and the `HostMessage`/`PeerMessage` protocol ship in MVP. A small in-memory **shape test** (~50–100 lines) exercises the abstraction with a stub multi-device controller to keep it honest. The production `LocalMultiplayerSessionController` and real transports are Post-MVP.
- **API-driven content from day one**, with a mock backend and a bundled fallback.
- **Premium UI baseline** built into the design system from the first commit.
- **Architecture ready for Android, iOS, Desktop.** Android-first ship is acceptable; iOS and Desktop must build and launch a placeholder from day one.

### 12.2 Not in MVP

- No additional game modules beyond Whodunit.
- No real P2P networking and no production `LocalMultiplayerSessionController`. (A small in-memory shape test for the abstraction ships in MVP — see Phase 7.)
- No admin dashboard. Backend is a mock or a thin static-content endpoint.
- No Easy or Hard difficulty.
- No Quick or Full session lengths.
- No 3-player support; no 4-player Elimination.
- No voiceover, no unlockable variations, no themed case packs.
- No accomplice/victim-survives/killer-doesn't-know modes.
- No user-generated content.

### 12.3 Acceptance of the boundary

If a feature is "not in MVP" but **affects the architecture**, the architecture accommodates it now (interface, slot, schema field). If it's purely additive content or UI, it is not designed for in advance.

---

## 13. Recommended Folder Structure

```
parlor/
├── build-logic/
│   └── convention/
│       ├── parlor.kmp.library.gradle.kts
│       ├── parlor.kmp.compose.library.gradle.kts
│       ├── parlor.android.app.gradle.kts
│       └── parlor.detekt.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/parlor/app/
│       │   ├── App.kt
│       │   ├── di/                              # Koin root module assembly
│       │   ├── navigation/
│       │   │   ├── ParlorNavHost.kt
│       │   │   └── ShellRoutes.kt
│       │   └── shell/
│       │       ├── home/
│       │       ├── settings/
│       │       └── howtoplay/
│       ├── androidMain/kotlin/com/parlor/app/
│       │   ├── MainActivity.kt
│       │   └── ParlorApplication.kt
│       ├── iosMain/kotlin/com/parlor/app/
│       │   └── MainViewController.kt
│       └── desktopMain/kotlin/com/parlor/app/
│           └── Main.kt
├── iosApp/                                       # Xcode project
│   └── iosApp.xcodeproj/ …
├── shared/
│   ├── core/
│   │   └── src/commonMain/kotlin/com/parlor/core/
│   │       ├── result/                          # Result<T, E>, DataError, EmptyResult, helpers
│   │       ├── ids/                             # GameId, ModeId, PlayerId, etc.
│   │       ├── time/                            # Clock, DurationRange
│   │       ├── logging/                         # Logger contract
│   │       └── localization/                    # UiText, LocalizedString
│   ├── design-system/
│   │   └── src/commonMain/kotlin/com/parlor/designsystem/
│   │       ├── tokens/                          # color, typography, spacing, motion, elevation, blur
│   │       ├── theme/                           # ParlorTheme base
│   │       ├── components/                      # ParlorCard, ParlorButton, ParlorBottomSheet, …
│   │       ├── motion/                          # named timings, easings, transitions
│   │       ├── backdrop/                        # AmbientBackdrop system
│   │       └── sound/                           # SoundscapeController contract
│   ├── engine/
│   │   └── src/commonMain/kotlin/com/parlor/engine/
│   │       ├── definition/                      # GameDefinition, GameMode, SessionConfig
│   │       ├── state/                           # GameState, GameStateContainer, projections
│   │       ├── action/
│   │       ├── event/
│   │       ├── phase/
│   │       ├── reducer/                         # GameReducer, ReducerContext, Reduction
│   │       ├── session/                         # GameSession contract
│   │       ├── snapshot/                        # SnapshotCodec, GameSnapshot
│   │       ├── timer/                           # TimerService contract
│   │       └── registry/                        # GameRegistry
│   ├── session/
│   │   └── src/commonMain/kotlin/com/parlor/session/
│   │       ├── SessionController.kt
│   │       ├── ViewerContext.kt
│   │       ├── passandplay/
│   │       │   └── PassAndPlaySessionController.kt
│   │       └── multidevice/                     # skeleton only in MVP
│   │           ├── LocalMultiplayerSessionController.kt
│   │           └── ShadowController.kt
│   ├── content/
│   │   └── src/commonMain/kotlin/com/parlor/content/
│   │       ├── schema/                          # CaseEnvelope, CaseSummary, SemVer
│   │       ├── repository/                      # CaseRepository
│   │       ├── datasource/
│   │       │   ├── RemoteCaseDataSource.kt
│   │       │   ├── BundledFallbackCaseDataSource.kt
│   │       │   └── CachedCaseDataSource.kt
│   │       ├── cache/                           # cache implementation
│   │       └── validation/                      # CaseValidator, ValidatedCase, ValidationError
│   ├── networking/
│   │   └── src/commonMain/kotlin/com/parlor/networking/
│   │       ├── room/                            # LocalRoom, RoomInfo, RoomMember
│   │       ├── transport/                       # RoomTransport, TransportSession
│   │       └── protocol/                        # HostMessage, PeerMessage sealed types
│   ├── storage/
│   │   └── src/commonMain/kotlin/com/parlor/storage/
│   │       ├── settings/                        # user prefs (reduce motion, sound on/off, etc.)
│   │       ├── secure/                          # keystore-backed storage
│   │       └── snapshot/                        # game snapshot persistence
│   └── navigation/
│       └── src/commonMain/kotlin/com/parlor/navigation/
│           ├── ParlorRoute.kt
│           ├── NavGraphRegistry.kt
│           └── ModuleNavGraph.kt
└── game-modes/
    └── whodunit/
        └── src/commonMain/kotlin/com/parlor/games/whodunit/
            ├── WhodunitModule.kt                # registry entry + Koin module
            ├── WhodunitDefinition.kt
            ├── domain/
            │   ├── state/
            │   │   ├── WhodunitState.kt
            │   │   ├── WhodunitPublic.kt
            │   │   ├── WhodunitPrivate.kt
            │   │   └── WhodunitHostOnly.kt
            │   ├── action/WhodunitAction.kt
            │   ├── event/WhodunitEvent.kt
            │   ├── phase/WhodunitPhase.kt
            │   ├── reducer/WhodunitReducer.kt
            │   ├── projection/WhodunitProjectionPolicy.kt
            │   └── modes/
            │       ├── ClassicVoteMode.kt
            │       └── EliminationMode.kt
            ├── content/
            │   ├── WhodunitCase.kt              # payload schema
            │   ├── WhodunitPayloadValidator.kt
            │   └── bundled/                     # The Last Dinner JSON
            ├── ui/
            │   ├── theme/CozyNoirTheme.kt
            │   ├── components/                  # WaxSealReveal, DossierCard, ClueCard, …
            │   ├── backdrop/CandlelitBackdrop.kt
            │   ├── motion/                      # whodunit-specific motion templates
            │   └── screens/
            │       ├── setup/
            │       ├── intro/
            │       ├── briefing/
            │       ├── reveal/
            │       ├── round/
            │       ├── vote/
            │       ├── revealstage/
            │       └── postgame/
            ├── presentation/                    # screen state holders / ViewModels
            │   ├── setup/
            │   ├── reveal/
            │   ├── round/
            │   ├── vote/
            │   └── postgame/
            ├── navigation/WhodunitNavGraph.kt
            └── di/WhodunitDiModule.kt
```

---

## 14. Implementation Phases

Phases are ordered. Each phase has an acceptance bar; do not advance until met. This breakdown mirrors `docs/APP_PLAN.md` §5 — both documents use the same Phase 0–8 + Post-MVP structure.

### Phase 0 — Final planning and content/schema audit

**Scope**
- Draft the `WhodunitCase` JSON payload schema (extending the generic `CaseEnvelope`) and lock it.
- Draft *The Last Dinner* content against the schema (6 characters with Innocent + Guilty briefs, clue pools, reveal narratives, public intro).
- Decide mock-backend hosting. **Default to the simplest path: static JSON file(s) inside the repo, served via Ktor's mock engine or a tiny in-process source.** A CDN-hosted static JSON is also acceptable. Whatever the choice, content must flow through `RemoteCaseDataSource` and `CaseRepository` — never inline.
- Lock the design-system token spec for the premium baseline (color, typography, motion, elevation, blur, corner radii).
- Dependency lock in `gradle/libs.versions.toml` draft (KMP, CMP, Koin, Ktor, kotlinx.serialization, SQLDelight or chosen alternative).

**Acceptance**
- Schema validates *The Last Dinner* draft without errors.
- Token spec is reviewed and approved.
- Mock-backend hosting choice is recorded.
- No Kotlin source, no Gradle modules, no tests yet.

### Phase 1 — Project skeleton, shared modules, premium design-system baseline

**Scope**
- Multi-module Gradle project with convention plugins and version catalog.
- Empty modules with their package structure and dependency rules wired.
- Koin DI scaffolding per module; root module assembled in `:composeApp`.
- **Parlor base design system**: tokens (color, typography, spacing, elevation, motion), `ParlorTheme`, two or three foundational components (`ParlorCard`, `ParlorButton`, `AmbientBackdrop`), motion primitives, `SoundscapeController` contract.
- All three platforms launchable to a styled Home placeholder (no real games yet, just the *Tonight's Game* and *All Games* layout with a "Coming soon" placeholder).

**Acceptance**
- Project builds on Android, iOS (via Xcode), Desktop.
- Home placeholder renders at the premium bar on all three (real type, real palette, real motion, real backdrop).
- Architecture lint: no module violates the dependency rules in §3.2 or the purity rules in §3.3.

### Phase 2 — Generic game engine

**Scope**
- `GameDefinition`, `GameMode`, `GameSession`, `GameState`, projections, `GameReducer`, `GamePhase`, `GameAction`, `GameEvent`, snapshot codec contract, `TimerService` contract, `GameRegistry`.
- `PassAndPlaySessionController` against a trivial test `GameDefinition` (e.g., a "round-robin announce" tester).

**Acceptance**
- A trivial test game runs an end-to-end session in tests with phase transitions and timers.
- Engine module has zero references to Whodunit.
- Reducer is pure (verified by tests).

### Phase 3 — Content system, mock API, fallback case, validation

**Scope**
- `WhodunitCase` payload schema as Kotlin data classes + bundled *The Last Dinner* JSON.
- `RemoteCaseDataSource` (Ktor against the Phase-0-chosen mock source — typically static JSON in-repo via Ktor's mock engine or a tiny in-process source).
- `BundledFallbackCaseDataSource`, `CachedCaseDataSource`, `CaseRepository`.
- `CaseValidator` + `WhodunitPayloadValidator` with the full strictness from §8.4.
- Library screen wires through the repository (no inline content).

**Acceptance**
- App fetches, validates, caches, and loads *The Last Dinner* through the production code path.
- Five intentionally broken cases (wrong schema version, missing killer variant, mode mismatch, etc.) fail validation cleanly with typed errors.
- Brand-new install + airplane mode loads the bundled fallback.

### Phase 4 — Whodunit setup and character reveal flow

**Scope**
- `WhodunitDefinition`, `WhodunitState`, actions, events, phases, reducer, projection policy.
- `ClassicVoteMode` and `EliminationMode` registered (rules implemented through Phase 5).
- Screens for Mode Selection, Player Count, Player Entry, Public Intro, Rules Briefing, Character Reveal Handoff, Dossier (Must Read + Optional), Hide-and-Pass Cover.
- Whodunit `CozyNoirTheme` overlay, `WaxSealReveal`, `CandlelitBackdrop`, `DossierCard`, and `CoverScreen` ship **with the first commit of the screens that use them** — no functional-first / polish-later split. Every Whodunit screen in this phase is cinematic from day one.
- Private Review Mode.

**Acceptance**
- A group can complete setup and character reveal end-to-end on a single device, in the production aesthetic.
- Privacy ceremony works: no private dossier can be reached without the cover → hold-to-reveal → reveal → hide flow.
- All UI obeys the reduce-motion accessibility setting.

### Phase 5 — Rounds, clues, voting, reveal, replay

**Scope**
- Round phases adaptive to player count (3+Vote for 4 players, 4+Vote for 5–6, scaling slot present for 7–8 when a future case fills it).
- Clue reveal, structured action prompts (Alibi, Question, Silent Accusation, Monologue), discussion timers.
- Classic Vote and Elimination flows complete, including tie rules.
- Final reveal stage with reveal narratives from validated content.
- Replay loop.

**Acceptance**
- A full Classic Vote game (4, 5, and 6 players) and a full Elimination game (5 and 6 players) play end-to-end on a single device.
- Reveal screens earn audible reactions in internal playtests.

### Phase 6 — Safety rules, persistence, resume, QA hardening

**Scope**
- Safety rules (per design doc §15): pause, leave mid-game, refuse-to-vote (abstain), accidental exposure (reroll).
- Snapshot persistence on every meaningful phase change via `:shared:storage`. `SnapshotStore` interface stable; dev-grade backing acceptable behind the interface (see Appendix A.8).
- Process-death recovery: "Resume your last game?" on cold start.
- Encrypted-at-rest snapshots via platform keystore where available; documented fallback elsewhere.
- QA hardening pass: foreground/background transitions during private reveal, unicode/emoji player names, gesture-mash on hold-to-reveal, rapid screen rotation.
- External playtest gate: cohort completes ≥5 full games.

**Acceptance**
- All safety rules behave as specified in design doc §15.
- Process death mid-game offers correct resume.
- No private or host-only content appears in any log line or crash report.
- Playtest cohort meets the design doc's success criteria (`whodunit-game-design.md` §25).

### Phase 7 — Multi-device abstraction shape test (still MVP)

**Scope**
- A **minimal** in-memory verification (~50–100 lines of test code) that proves the abstraction holds: a stub multi-device controller drives the same `WhodunitReducer` through a host + N peers loop in-process, with `HostMessage`/`PeerMessage` serialization round-trips covered.
- **Not** a full `LocalMultiplayerSessionController`; not production-quality; not wired into any user-facing screen at runtime.
- The purpose is insurance against silent coupling between the pass-and-play controller, the UI, and engine assumptions that a future multi-device controller would have to satisfy.

**Acceptance**
- An automated test simulates host + N peers in-process, runs a full game, and shows identical state evolution to the single-device case.
- No screen, reducer, or content file required changes versus Phase 6.
- Effort is bounded to test code; no production-grade controller is shipped.

### Phase 8 — Production polish and release readiness

**Scope**
- Accessibility pass against WCAG AA on dark surfaces.
- Three-platform real-device QA on a representative device matrix (low-end Android, mid-range Android, iPhone, iPad, macOS, Windows desktop).
- Motion and backdrop performance tuning per platform; activate motion downgrade tiers where needed.
- Store packaging: Play Store assets (Android is the first-ship target); App Store assets and Desktop installers per per-platform release decision.
- Crash and error telemetry wiring; the `Logger` filter strips private and host-only content at source.
- Release runbook and rollback rehearsal for the mock backend.
- Final content review of *The Last Dinner* (reveal narratives, clue pools, dossier briefs proofread and theatrically tightened).

**Acceptance**
- Crash-free sessions over a sustained internal-dogfood window (≥ 95th-percentile target).
- All Phase 6 acceptance criteria hold on the device matrix.
- A rollback drill of *The Last Dinner* succeeds end-to-end in under five minutes.

### Post-MVP

The full Post-MVP roadmap lives in `docs/APP_PLAN.md` §5 (Post-MVP). The architecturally significant items:

- **Real multi-device play.** Production `LocalMultiplayerSessionController` — the real implementation of the abstraction that Phase 7 stub-tested. Concrete `RoomTransport` per platform: Android Nearby Connections, iOS MultipeerConnectivity, Desktop mDNS+WebSocket. Connection lifecycle handling (host disconnect, peer disconnect). Lobby and join flow UI (room code, QR, invite link). **Acceptance:** a group with 3–8 devices plays a full game in one room; mid-game peer disconnect triggers the documented behavior cleanly.
- **Additional cases.** The first 8-character case validates the architecture's 4–8 / 5–8 capability with content, not contrivance. The engine, module, and Player Count screen are already ready.
- **Additional game modules.** Each plugs into the same shell with its own theme overlay; no engine or shell changes required.
- Other Post-MVP items (Easy/Hard difficulty, Quick/Full session lengths, voiceover, unlockables, 3-player support, 4-player Elimination, localized cases) are content/UX work, not architectural changes.

---

## Appendix A — Cross-Cutting Concerns

### A.1 Dependency injection

- **Koin** as the DI framework, KMP-friendly.
- One module per layer (`coreModule`, `designSystemModule`, `engineModule`, `sessionModule`, `contentModule`, `storageModule`, `navigationModule`, `whodunitModule`).
- The `:composeApp` `Application` (or platform equivalent) calls `startKoin { modules(allModules) }`.
- Game modules register their `GameDefinition` into the `GameRegistry` from their Koin module.

### A.2 Error handling

- `Result<T, E>` generic wrapper in `:shared:core`.
- Typed error hierarchies:
  - `DataError` (cache, IO).
  - `NetworkError` (timeout, server, unauthorized, unreachable).
  - `ValidationError` (per validator).
  - `SubmitError` (illegal action for current phase, unknown player, etc.).
- A single `UiText` mapper turns errors into displayable strings.

### A.3 Logging and telemetry

- `Logger` contract in `:shared:core`; platform actuals.
- Logs strip private and host-only data at the source. A static check fails the build if a `Logger` call references a private/host type.
- Telemetry events (Post-MVP) flow through the same path; validation failures are first-class telemetry candidates so the backend can triage broken cases.

### A.4 Localization

- App chrome and design-system strings are standard CMP string resources.
- Case content is localized per-case via the content schema's `language` field. The repository selects the best-language case variant per user preference.
- All user-facing strings flow through `UiText` so they can be resolved against either resources or content payloads.

### A.5 Accessibility

- See §10.2.3.
- A "reduce motion" preference collapses theatrical motion without breaking flow.
- Every interactive composable carries `contentDescription`; the design system enforces this for its components.
- Hold-to-reveal gestures have a tap-confirmation fallback.

### A.6 Testing

- **Engine**: pure reducer tests against the trivial test game and against Whodunit's reducer. Snapshot round-trip tests.
- **Content**: validator tests with intentionally broken envelopes; repository tests with fake data sources.
- **Session**: pass-and-play controller tests, plus the Phase 7 in-memory shape test for the multi-device abstraction.
- **Presentation**: ViewModel tests with Turbine and `UnconfinedTestDispatcher`, fakes for the session controller.
- **UI**: Compose UI tests for the cover-reveal-hide ceremony, voting flow, and reveal stage.

### A.7 Performance and resources

- Snapshots write asynchronously off the main thread; reducer never blocks.
- Compose recomposition discipline: stable types for all `UiState` fields, no captured lambdas in hot composables, lazy lists for player rosters with stable keys.
- Sound assets are streamed where possible; ambient audio is decoded once and looped.

### A.8 Security

- Snapshots encrypted at rest via platform keystore.
- No private/host content in logs, crash reports, or analytics.
- Backend content is signature-verified where the transport supports it; at minimum, transported via HTTPS.
- A future P2P transport is treated as **untrusted** by default: peers may not request other peers' private state; the host validates every submitted action against the current phase before applying it.

---

## Appendix B — Open Questions and Risks

### B.1 Open questions

None block starting Phase 0.

1. **Shared ViewModel approach.** Confirm whether to use `androidx.lifecycle.viewmodel` multiplatform (now common) or a Compose-only state-holder pattern. Doesn't affect module structure; affects per-screen scaffolding.
2. **Cache storage.** SQLDelight for content cache vs platform key-value (DataStore / NSUserDefaults / Java Prefs) — TBD by content cardinality.
3. **Sound implementation.** Whether to ship Compose Multiplatform sound via a single library (e.g., Korge audio) or three platform actuals. Doesn't affect the design-system contract.
4. **Backdrop performance on Desktop.** The cozy-noir backdrop (grain + bloom + candle flicker) needs verification on low-end Windows GPUs. The motion system supports a downgrade level.
5. **iOS Compose Multiplatform stability for the reveal motion stack.** If a specific motion primitive doesn't render acceptably on iOS, the design system provides an iOS-tuned alternative under the same name.
6. **Backend shape for the mock.** Static JSON in a CDN bucket vs a tiny dev server. Either is acceptable for the MVP rule; pick on the basis of who maintains it.
7. **Bundled case refresh cadence.** When does the bundled snapshot of *The Last Dinner* get refreshed relative to live API patches? Operational, not architectural.

### B.2 Risks

1. **Multi-device drift if the Phase 7 shape test is skipped.** The Phase 7 in-memory shape test is deliberately small, but it is the only mechanism that keeps the pass-and-play controller, the UI, and the reducer honest about an abstraction nobody is using at runtime in MVP. If the shape test is deferred or omitted, silent coupling can accumulate for months and become expensive to unwind when the real multi-device work (Post-MVP) starts. Mitigation: do not skip Phase 7, even though it is small.
2. **Premium UI bar across three platforms.** Hitting the cinematic bar on Android + iOS + Desktop in parallel is the largest *quality* risk. Mitigation: the design system is Phase 1, motion has documented downgrade tiers per platform, and no Whodunit screen ships in a "polish later" state (see §10.2).
3. **Engine purity drift.** Under deadline pressure, the temptation will be to leak Whodunit specifics ("a vote count," "the killer") into `:shared:*`, or to leak framework deps (Koin, Compose, Ktor) into the engine. Mitigation: an architecture test (Konsist or detekt custom rule) fails the build on §3.3 violations.
4. **"Fake backend" discipline.** It is easier to write inline content than to set up a mock endpoint. Mitigation: Phase 3 puts the mock in place before any Whodunit screen is built, and the bundled fallback case also flows through the validator.
5. **Snapshot schema versioning at first migration.** The first app update that changes engine state shape will need a clean migration story. The architecture allocates a snapshot version field; the migration policy itself is unspecified. Flagged for closeout at the end of Phase 6, where persistence first lands.

---

*This document is the architectural source of truth. Implementation phases reference it by section number. When the architecture must change, the document changes first; implementation follows.*
