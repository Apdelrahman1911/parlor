# Parlor — Application Execution Plan

> **Status: historical pre-implementation plan.** This file preserves the
> original sequencing and may describe multi-device play as future work or
> name transports Parlor never shipped. It is not current implementation or
> release truth. Use `PRODUCTION_ARCHITECTURE.md`, `RELEASE_GATES.md`, the
> accepted ADRs, and `P2P_MANUAL_TEST.md`.

> Companion to `whodunit-game-design.md` (product design) and `ARCHITECTURE.md` (system architecture).
> This document is the **product-level execution plan** — what gets built, in what order, with what acceptance bars.
> It is the reference plan from product idea to MVP to Post-MVP roadmap.

---

## 0. How to read this document

- **§1–§4** are the product layer: vision, MVP, UX, visual direction.
- **§5** is the technical execution plan: Phase 0 through Phase 8 + Post-MVP.
- **§6–§8** are the cross-cutting plans: content, platforms, testing.
- **§9–§10** are the decision and risk surface: what we already decided, what could go wrong.
- **§11** is the immediate next step.

When this plan references *how* something is built, it links to `ARCHITECTURE.md` by section number rather than duplicating it. The architecture answers "how"; this plan answers "what, when, and why first."

---

## 1. Product Vision

### 1.1 What Parlor is

**Parlor is a party-games app for groups in the same room.** The phone is the host, the storyteller, and the secret-keeper. The atmosphere is cozy and theatrical — a candlelit salon, not a neon party. The product's leverage is that the social game happens *around* the phone; the phone's job is to be beautiful, theatrical, and quietly useful, then get out of the way.

Parlor is built as a **platform**, not a single game. The architecture (`ARCHITECTURE.md` §1) treats each game as a module plugged into a shared shell — engine, design system, content layer, session controller — so the catalog can grow without rewriting the foundation.

### 1.2 Why Whodunit is the first module (not the whole app)

Whodunit is the first game to ship in Parlor. It is *one* module in the *All Games* grid. Building Whodunit first inside a platform — instead of as a standalone Whodunit app — is a deliberate product bet:

- **Future-leverage.** The second, third, and N-th game are mechanically obvious to scaffold (`ARCHITECTURE.md` §3.5). No replatforming is required to ship party game #2.
- **Brand-leverage.** Parlor is a single product the audience can return to. Each new game is content delivery, not a new app install.
- **Architectural cleanliness.** Forcing the engine to be generic from day one prevents the most expensive form of technical debt — a "platform" retrofitted onto a single-game codebase.

Whodunit is also a particularly good first module: it carries the design doc's strongest IP (the **Dynamic Killer System**), it tests every load-bearing platform feature (private state, host-only state, content delivery, cinematic UI, ceremonial flow), and it has known replay levers (random killer, variant clues, two modes).

### 1.3 How the app grows into a party-games platform

The catalog grows on three axes:

1. **More cases inside Whodunit.** Same engine, new content via the API. *The Last Dinner* → a modern villa case → a hotel-storm case → seasonal cases.
2. **More game modules.** Different genres of party game (e.g., bluffing, drawing, narrative). Each module plugs into the same shell with its own theme overlay.
3. **More play topologies.** Pass-and-play first; local multi-device later (P2P in the same room); remote play remains an open question.

Phase 5+ in the design doc's roadmap (`whodunit-game-design.md` §22) treats this as a sequence: depth on Whodunit first, then breadth across cases, then breadth across modules. The architecture supports all three from day one; the product ships them in order.

### 1.4 What the first release must prove

The MVP exists to answer **one question**: *is this game fun?*

Concretely, success means:

- A group of 4–6 friends learns the rules in **under one minute**.
- They finish a game in **under 35 minutes** (Classic) or **15–25 minutes** (Elimination).
- They immediately replay with a new randomly assigned killer.
- The killer wins **30–50%** of games — earned, not lopsided.
- Quiet players speak as often as loud players, thanks to the structured spotlight moments.
- The reveal generates audible reactions — gasps, laughter, accusations.

These are the design doc's MVP success criteria (`whodunit-game-design.md` §25). The execution plan below is the path to them.

---

## 2. MVP Definition

### 2.1 What's IN the MVP

| Capability | Notes |
|---|---|
| **Parlor shell** | Home / Game Library, Game Details, Settings, How to Play. Reads from the `GameRegistry`. |
| **Whodunit module** | First and only game module in the MVP. |
| ***The Last Dinner*** | First and only case in the MVP. 4–6 players in practice (capped by 6-character pool). |
| **Two game modes** | Classic Vote (4–6 players in MVP) and Elimination (5–6 in MVP). Module supports 4–8 / 5–8; case is the cap. |
| **One difficulty** | Medium. Public clues only. |
| **One session length** | Classic pacing. |
| **Pass-and-play** | The only implemented play topology. |
| **API/mock content path** | API-driven from day one. Mock backend serves *The Last Dinner* JSON; bundled fallback inside the app for offline. No hardcoded inline content. |
| **Premium cinematic UI** | Non-deferrable. No "polish later" path. Whodunit ships cozy-noir from the first commit. |
| **Three-platform architecture ready** | Android, iOS, Desktop. Android is first-ship; iOS and Desktop must build and launch a styled placeholder from Phase 1 forward. |
| **Privacy model** | Three-bucket projections (public, per-player private, host-only). Pass-and-play ceremony enforces privacy. |
| **Safety rules** | Pause, leave mid-game, refuse-to-vote (abstain), accidental exposure (reroll). |
| **Persistence and resume** | Snapshot on phase changes; "Resume your last game?" on cold start. |
| **Multi-device contracts** | `SessionController`, `LocalRoom`, `RoomTransport`, and message protocols ship as interfaces. A small shape test exercises the abstraction. No real transport. |

### 2.2 What's explicitly NOT in the MVP

- No real P2P networking.
- No production `LocalMultiplayerSessionController` (only the shape test).
- No additional game modules beyond Whodunit.
- No additional cases beyond *The Last Dinner*.
- No Easy or Hard difficulty.
- No Quick or Full Mystery session lengths.
- No 3-player support; no 4-player Elimination.
- No voiceover narration.
- No unlockable case variations (accomplice mode, victim survives, killer doesn't know).
- No themed seasonal cases.
- No user-generated content.
- No admin dashboard. Backend is a mock or a thin static endpoint.
- No app-level analytics dashboard (basic crash/error telemetry only).

### 2.3 The MVP acceptance bar

The MVP ships when **all** of the following are true:

1. A group can play a full Classic Vote game (4, 5, or 6 players) end-to-end on a single device.
2. A group can play a full Elimination game (5 or 6 players) end-to-end on a single device.
3. The UI is cinematic on every Whodunit screen on Android. iOS and Desktop launch and present the Home screen at the premium bar.
4. *The Last Dinner* is served by the mock backend, cached locally, and playable offline via the bundled fallback.
5. All safety rules behave as documented.
6. Process death mid-game offers a clean resume.
7. A playtest cohort completes at least 5 full games with no critical regressions and reports the design doc's success criteria (replay intent, time-to-completion, balanced killer win rate).

---

## 3. User Experience Plan

Written from the perspective of a group sitting around one phone in a dim room, on a Friday night.

### 3.1 The opening moment

The host taps the Parlor icon. The screen is dark. A slow ember-orange glow rises from black; a faint clock ticks somewhere; a piano hums. The Home screen settles in: a featured *Tonight's Game* card with *Whodunit: The Last Dinner*, a small grid of *All Games* (others greyed and labeled "Coming soon"), and a quiet icon for *How to Play / Settings*.

The room leans in. The host taps *The Last Dinner*. A short dossier slides up: a one-paragraph teaser, supported player counts, estimated time, a single large button — **Begin Investigation**.

### 3.2 Setup ritual

Setup is a small ceremony, one screen per step, never crowded:

1. **Choose game mode.** Two large cards — Classic Vote and Elimination. Each card shows time, player range, and a one-sentence feel: *"Investigate the full case. One vote at the end."* vs *"Vote after every round. Survive the suspicion."*
2. **Choose player count.** Slots for the supported range (4–6 for *The Last Dinner* in MVP; see `ARCHITECTURE.md` §1.5 for the display strategy). Tap one slot to confirm.
3. **Enter player names.** Each player types their name. Used throughout — *"Pass to Eleanor"* not *"Pass to Player 3."*
4. **Public case intro.** Phone placed face-up on the table. Everyone reads together: the dinner, the toast, the brandy, the body in the study. The road is blocked. The phones are down. One of you killed him.
5. **Rules briefing carousel.** Four cards: *one of you is the killer*, *you may lie*, *one new clue each round*, *vote according to your chosen mode*. ~20 seconds total.

The MVP aims for **setup to first round in under four minutes**, including character reveals.

### 3.3 Character reveal

The phone prompts: *"Pass the phone to [first player's name]."*

That player finds a quiet angle. A wax-seal icon pulses softly in the center of the screen. They **press and hold** for 1.5 seconds — both a safety against accidents and a ceremonial gesture. The seal breaks. The dossier rises from below.

The dossier has two sections:

- **Must Read** — one screen. Name, public identity, relationship to the victim, **verdict** ("You are innocent." or "You are the killer."), private secret, motive, alibi, goal, what you can say freely, what you must hide.
- **More about your character** — expandable. Backstory, acting tips, emotional motivation, suggested behavior.

A 90-second soft timer counts down (extendable once). When done, they tap **I'm Done**. The screen goes completely black with one line: *"Hide the phone. Pass to [next player] when no one is watching."*

A second tap clears the cover and prompts the next player. The ceremony repeats until everyone has been briefed.

### 3.4 Rounds

Each round follows the same skeleton:

1. The app shows a **round title card** with a tagline.
2. A **clue** appears, large and readable from across the table. Short, museum-label style. Public clues are visible to everyone; private clues are post-MVP.
3. A **discussion timer** starts (3–4 minutes, soft, ending with a chime).
4. A **structured action** is prompted — an alibi, a directed question, a silent accusation, a monologue — depending on the round.
5. A **transition card** ends the round.

Round counts adapt to player count:

| Players | Rounds (MVP) |
|---|---|
| 4 | Alibis → Motives → Contradictions → Vote |
| 5–6 | Alibis → Motives → Contradictions → Final Evidence → Vote |

Through all rounds, any player can:

- **Pause** (long-press the pause icon) — freezes timers without leaking information.
- **Private Review** — tap their own name in the roster to re-read their dossier behind a cover screen.
- Raise a **privacy concern** if they think someone saw their role — choose to continue, or to **reroll** all role assignments and restart character reveal.

### 3.5 Voting and reveal

**Classic Vote Mode.** After the final round, the screen darkens: *"It is time to vote."* The phone passes from player to player. Each player taps the name they accuse; the screen clears between voters. Once all votes are in, the app builds tension with a slow card flip, lowest count first.

- If the room votes for the killer → **players win.** The accused presses and holds the wax seal. The screen reveals **YES.** The full reveal narrative plays — how it happened, blow by blow. The killer gets to perform the crime aloud, like a stage villain.
- If the room votes for an innocent → **killer wins.** The screen flips to **NO.** *"An innocent has been condemned. The killer is among you, free."* The real killer's identity and full reveal play. They stand and take a bow.
- A tie triggers a 60-second debate between the tied suspects, then a revote. If still tied, killer wins.

**Elimination Mode.** Each round ends with an immediate elimination vote (only surviving players vote). The app reveals on the spot whether the eliminated player was innocent or the killer.

- If innocent → game continues. The eliminated player becomes *audience with rules* — they may react audibly (laugh, gasp, smile) but may not speak strategically, hint, or vote.
- If killer → game ends. Full reveal narrative plays.
- Killer wins by **surviving to the final two players.** A tie at any round revotes between the tied suspects; if still tied, no one is eliminated and the next round's clue is amped up.

### 3.6 Post-game

The reveal stage ends and a post-game screen appears with three options, the first largest:

1. **Replay with a new killer** (recommended on first replays).
2. **Try the other mode** (Classic ↔ Elimination).
3. **Back to main menu.**

Replay is frictionless: a single tap reshuffles roles and seats and starts a new game with the same case and players.

---

## 4. UI / Visual Direction Plan

### 4.1 UI as a core product feature

The UI is **not late-stage polish**. It is treated as a core product feature on par with the gameplay loop. The premium bar is non-deferrable (see `ARCHITECTURE.md` §10.2).

Concretely: a Whodunit screen lands at the premium bar from its first commit, or it is not delivered. There is no "ship functional, polish next sprint" path. A placeholder visual never reaches a build that anyone outside the team plays.

### 4.2 Cozy noir for Whodunit (cinematic identity)

The visual language for Whodunit is **cozy noir** — Knives Out / Clue / candlelit dinner. Specifically:

- **Color** — warm near-blacks (`#0B0807`, `#14100D`), ember-orange accent (`#D97A2A` family) with a layered glow, brass and aged-parchment highlights. Pure black is banned; the palette is candlelit, not flat.
- **Typography** — a refined transitional or Didone serif for display (case titles, dossier names, reveal narrative). A humanist sans-serif for body and UI affordances. Generous tracking on small caps; italic accents for narration; light tabular figures for timers.
- **Surfaces** — layered cards with warm shadows, soft glow rims, subtle bevels. Generous padding. Breathing room around hero text.
- **Texture and depth** — a low-opacity grain overlay; a slow candle-flicker on key surfaces (a real flame's rhythm, not a Halloween jitter); vignettes on hero screens; warm bloom around emissive accents.
- **Motion** — fades and rises in the 350–600 ms range. **Reveal moments** are paced: wax seal pulses on a 1.5 s hold, then breaks; dossier rises from below the seal; cover screens cross-dissolve to candle-glow then to content. Never a snap.

**Signature interactions** (must feel premium from day one):

- **Wax-seal reveal** — the gesture-and-motion combination at the heart of character reveal and Private Review.
- **Cover screen** — a candle-glow ambient surface that fully occludes content during transitions.
- **Final reveal stage** — a paced, theatrical climax that earns the audible reaction at the table.

**Sound** — distant piano, ticking clock at low volume, soft creak on transitions, a wax-seal crack on reveal, a soft chime on phase change. Sound is always paired with a visual beat.

### 4.3 What the Parlor base design system provides

The base design system in `:shared:design-system` is **reusable across all future game modules**. It provides:

| Layer | Provided |
|---|---|
| **Tokens** | Color, typography, spacing, elevation, motion timings, blur radii, corner radii. All themable per module. |
| **Components** | `ParlorCard`, `ParlorButton`, `ParlorBottomSheet`, `ParlorDialog`, `ParlorCarousel`, `ParlorListItem`, `RevealSurface`, `CoverSurface`, `TimerRibbon`, `AmbientBackdrop`. |
| **Motion primitives** | Named timings (`Motion.slow`, `Motion.theatrical`, `Motion.ember`) and easings. Transition templates: `reveal`, `cover`, `cardRise`, `crossDissolve`. |
| **Sound primitives** | `SoundscapeController` with named cues. Platform-shimmed implementation. |
| **Backdrop system** | Every module supplies an `AmbientBackdrop`; it's never visually flat. |
| **Theme overlays** | Each game module ships its own theme (Whodunit = `CozyNoirTheme`). The Parlor base does not impose Cozy Noir — it imposes quality. |

Future module example: a bright party-neon module brings its own palette and motion language but uses the same component quality bar. The aesthetic per module changes; the bar travels.

### 4.4 Accessibility within the premium aesthetic

- **Large readable type** from across a small table (body 16–18 sp, dossier 18–20 sp, headings 28–36 sp). Design system enforces minimums.
- **Contrast** checked against WCAG AA on dark surfaces; ember accents on near-black meet contrast for non-decorative use.
- **Tap fallbacks** for every hold-to-reveal gesture.
- **contentDescription** on every interactive composable, enforced by the component library.
- **Reduce motion** preference collapses theatrical reveals to dignified cross-dissolves without breaking the flow.

---

## 5. Technical Execution Plan

Phases are ordered. Each phase has acceptance criteria; do not advance until met. Files and modules referenced below come from `ARCHITECTURE.md` §13 (folder tree).

### Phase 0 — Final planning and content/schema audit

**Goal.** Lock the artefacts and decisions that Phase 1 depends on, before any code is written.

**Scope.**
- Draft `WhodunitCase` JSON schema (the payload schema for Whodunit, conforming to the generic `CaseEnvelope`).
- Draft *The Last Dinner* content against the schema: 6 characters (Innocent + Guilty briefs each), motives, secrets, alibis, killer-pointing trails, red-herring placement, reveal narratives per killer, clue pools, public intro. See §6 below.
- Decide mock-backend hosting strategy: static JSON in a CDN bucket vs tiny dev server. Either is acceptable; pick one.
- Lock design-system token spec for the premium baseline: color palette (named tokens), typography scale, motion timings, elevation levels, blur radii, corner radii.
- Pre-Phase-1 dependency lock: KMP + CMP versions, Koin, Ktor, kotlinx.serialization, SQLDelight (or chosen alternative).

**Expected output.**
- `docs/CONTENT_SCHEMA.md` (proposed) — Whodunit case schema and validation rules.
- `docs/DESIGN_TOKENS.md` (proposed) — token spec, named values.
- `content/last-dinner.draft.json` (proposed) — first content draft, schema-conformant.
- A short note on mock-backend hosting choice.

**Acceptance criteria.**
- The schema is precise enough that a static checker could reject malformed cases.
- *The Last Dinner* draft conforms to the schema and is internally consistent (every killer variant has a complete reveal trail; clue pools align with declared modes; red-herring targets are valid characters).
- Design tokens are named and unambiguous; a UI engineer can implement Phase 1 without further design clarification.

**Must NOT yet include.**
- Any Kotlin code.
- Any module scaffolding.
- Any test code.

### Phase 1 — Project skeleton, shared modules, premium design-system baseline

**Goal.** A multi-module project that builds on all three platforms and presents a styled, cinematic Home placeholder.

**Scope.**
- Multi-module Gradle project with convention plugins (`parlor.kmp.library`, `parlor.kmp.compose.library`, `parlor.android.app`, `parlor.detekt`) and version catalog.
- Empty modules with dependency rules wired per `ARCHITECTURE.md` §3.2.
- Koin DI scaffolding per module; root module assembled in `:composeApp`.
- Parlor base design system: tokens, `ParlorTheme`, foundational components (`ParlorCard`, `ParlorButton`, `AmbientBackdrop`), motion primitives, `SoundscapeController` contract.
- All three platforms launchable to a styled Home placeholder showing *Tonight's Game* and *All Games* layout, with a placeholder tile (no real games yet).
- Static architecture test (Konsist or detekt custom rule) enforcing `ARCHITECTURE.md` §3.3 purity.

**Expected output.**
- A working multi-module project. `:composeApp` runs on Android, iOS (via Xcode), Desktop.
- A premium Home placeholder visible on all three.

**Acceptance criteria.**
- All three platforms build green in CI.
- Home placeholder renders at the premium bar on all three (real type, real palette, real motion, real backdrop).
- Architecture lint passes — no module violates `§3.2` or `§3.3`.

**Must NOT yet include.**
- Any game engine code.
- Any Whodunit-specific code.
- Any networking code beyond stub interfaces.
- Any content beyond static placeholder copy.

### Phase 2 — Generic game engine

**Goal.** A pure-Kotlin game engine that can run a trivial test game end-to-end, with no Whodunit references.

**Scope.**
- `GameDefinition`, `GameMode`, `GameSession`, `GameState`, three-bucket state container, projections (`PublicProjection`, `PrivateProjection`, `HostProjection`).
- `GameReducer` (pure function), `GamePhase`, `GameAction`, `GameEvent` marker interfaces.
- `SnapshotCodec`, `GameSnapshot`, `TimerService` contract.
- `GameRegistry`.
- `PassAndPlaySessionController` (initial implementation).
- A trivial test `GameDefinition` (e.g., a "round-robin announce" tester) for engine-level tests.

**Expected output.**
- `:shared:engine` and `:shared:session` populated with the contracts and the pass-and-play controller.
- Engine-level test suite passes against the trivial test game.

**Acceptance criteria.**
- Engine module has zero references to Whodunit, Compose, Koin, Ktor, or platform APIs.
- Reducer is verified pure (no I/O, no globals).
- A trivial test game runs an end-to-end session with phase transitions and timer events.
- Architecture test confirms `§3.3` purity rules.

**Must NOT yet include.**
- Whodunit reducer, state, or actions.
- Real network transport.
- UI screens beyond the Home placeholder from Phase 1.

### Phase 3 — Content system, mock API, fallback case, validation

**Goal.** *The Last Dinner* flows from the (mock) backend, through validation, into a usable in-memory case, with cache and bundled fallback in place. No Whodunit screen yet.

**Scope.**
- `CaseEnvelope` schema; `WhodunitCase` payload schema as data classes with kotlinx.serialization.
- `CaseRepository`, `RemoteCaseDataSource` (Ktor against mock backend), `BundledFallbackCaseDataSource`, `CachedCaseDataSource`.
- `CaseValidator` + `WhodunitPayloadValidator`. Full strictness from `ARCHITECTURE.md` §8.4.
- Mock backend serving *The Last Dinner* JSON (static CDN or local dev server, per Phase 0 decision).
- Bundled *The Last Dinner* JSON shipped inside `:game-modes:whodunit/content/bundled/`.
- Library screen wires through the repository — no inline content.

**Expected output.**
- A Library screen that reads cases from `CaseRepository` and renders one case (*The Last Dinner*).
- The "Begin Investigation" button is wired but goes to a placeholder.

**Acceptance criteria.**
- App fetches, validates, caches, and loads *The Last Dinner* through the production code path.
- Five intentionally broken cases (wrong schema version, missing killer variant, mode mismatch, type error, payload-validator failure) fail validation cleanly with typed errors.
- Brand-new install + airplane mode loads the bundled fallback.
- No case content is hardcoded inline anywhere in source.

**Must NOT yet include.**
- Whodunit gameplay screens.
- Whodunit reducer or state (beyond what is needed to display the library entry).

### Phase 4 — Whodunit setup and character reveal flow (premium bar from first commit)

**Goal.** A group can complete setup and character reveal end-to-end on one device, in the production cinematic aesthetic.

**Scope.**
- `WhodunitDefinition`, `WhodunitState`, `WhodunitAction`, `WhodunitEvent`, `WhodunitPhase`, `WhodunitReducer`, `ProjectionPolicy`.
- `ClassicVoteMode` and `EliminationMode` registered (rules implemented through Phase 5).
- Screens for: Mode Selection, Player Count (per `§1.5` display strategy), Player Entry, Public Intro, Rules Briefing, Character Reveal Handoff, Dossier (Must Read + Optional Details), Hide-and-Pass Cover.
- Whodunit `CozyNoirTheme` overlay.
- Signature components: `WaxSealReveal`, `CandlelitBackdrop`, `DossierCard`, `CoverScreen`. **Shipped together with the screens that use them — no functional-first / polish-later split.**
- Private Review Mode (cover → reveal → hide), gated by `WhodunitReducer` to be unavailable during votes.

**Expected output.**
- Setup → character reveal flow plays as a coherent ceremony on Android.
- iOS and Desktop render the same screens at the premium bar (motion downgrade tiers acceptable per platform).

**Acceptance criteria.**
- Setup-to-first-character-reveal takes under four minutes for a 6-player group.
- Privacy ceremony works: no private dossier can be reached without the cover → hold-to-reveal → reveal → hide flow.
- The wax-seal reveal feels cinematic, not animated-for-the-sake-of-it.
- All UI obeys the reduce-motion accessibility setting.

**Must NOT yet include.**
- Round phases or clue reveal beyond placeholder.
- Voting.
- Final reveal narrative.

### Phase 5 — Rounds, clues, voting, reveal, replay

**Goal.** A full Classic Vote and a full Elimination game can be played end-to-end on a single device.

**Scope.**
- Round phases adaptive to player count (3+Vote for 4 players, 4+Vote for 5–6; the 7–8 slot is structurally present for a future expanded case).
- Clue reveal, structured action prompts (Alibi, Question, Silent Accusation, Monologue), discussion timers (3–4 min per round; 30-second monologues; soft chime on end).
- Classic Vote flow: end-of-game vote, secret per-voter passing, slow card-flip reveal, tie debate + revote, killer-wins-on-second-tie.
- Elimination Mode flow: per-round vote, immediate elimination reveal, *audience with rules* framing for eliminated innocents, killer wins by surviving to final two.
- Final reveal stage with reveal narratives from validated content.
- Replay loop: replay-with-new-killer, try-other-mode, back-to-library.

**Expected output.**
- A full Classic Vote game (4, 5, 6 players) plays end-to-end.
- A full Elimination game (5, 6 players) plays end-to-end.
- Reveal screens are dramatic and earn audible table reactions in playtests.

**Acceptance criteria.**
- Time-to-completion: 25–35 min Classic, 15–25 min Elimination, across a representative cohort.
- Every clue is one of the five categories (`whodunit-game-design.md` §11) and falsifiable in discussion.
- The dynamic killer system is verifiably random per session and produces a different feel across multiple plays.

**Must NOT yet include.**
- Safety rules beyond a stub pause (full safety in Phase 6).
- Persistence beyond in-memory state.
- Multi-device runtime.

### Phase 6 — Safety rules, persistence, resume, QA hardening

**Goal.** The app handles real-world group play — interruptions, mistakes, leaving players, app backgrounding — without losing state or leaking information.

**Scope.**
- Safety rules (from `whodunit-game-design.md` §15): full pause (freezes timers without leaking), leave mid-game (reveal-now or end), refuse-to-vote (abstain after 30 s), accidental role exposure (continue or reroll).
- Snapshot persistence: write on every meaningful phase change to `:shared:storage`. Snapshot codec versioned.
- Resume flow: on cold start, surface "Resume your last game?" if an unfinished snapshot exists.
- Encrypted-at-rest snapshots via platform keystore where available; documented dev-storage fallback elsewhere (interface stable; see Decision Log §10).
- QA hardening pass: edge cases (player names with unicode/emoji, very long names, double-tap mash on hold-to-reveal, rapid screen rotation, foreground/background transitions during private reveal).
- **Playtest gate.** At least one external playtest cohort completes 5+ full games. Outcomes inform Phase 8 polish.

**Expected output.**
- App survives interruptions and process death cleanly.
- Safety rules are exercised in QA scenarios and behave per spec.
- Playtest report with quantitative measures (time-to-completion, killer win rate) and qualitative notes (replay intent, audible reaction, quiet-player participation).

**Acceptance criteria.**
- Process death mid-game offers a correct resume on next launch.
- No private dossier text appears in any log line or crash report.
- All four safety rules behave per spec under adversarial QA.
- Playtest cohort meets the design doc's success criteria.

**Must NOT yet include.**
- Multi-device implementation.
- Store-ready packaging.

### Phase 7 — Multi-device abstraction shape test (still MVP)

**Goal.** Prove that the `SessionController` abstraction holds — that the same `WhodunitReducer` would drive a multi-device flow without changes to screens, content, or the engine.

**Scope.**
- A small (~50–100 lines) in-memory test stub: a fake multi-device controller that round-trips actions and projections through the same reducer in-process.
- Serialization round-trip tests for `HostMessage` and `PeerMessage` payloads.
- Session test simulating host + N peers in-process, running a full game and producing the same state trajectory as the single-device case.

**Expected output.**
- The architecture insurance: silent coupling between pass-and-play, the UI, and the reducer is caught early.

**Acceptance criteria.**
- The in-process simulation runs a full Classic Vote and a full Elimination game with identical state evolution to the single-device case.
- Zero changes required to screens, reducer, or content modules versus Phase 6.
- Effort bounded to test code; no production-grade controller is shipped.

**Must NOT yet include.**
- Any real network transport.
- Any UI flow for room creation or join.
- Production `LocalMultiplayerSessionController`.

### Phase 8 — Production polish and release readiness

**Goal.** Ship-ready MVP on Android. iOS and Desktop builds verified launchable and presentable; their first release schedule decided.

**Scope.**
- Accessibility pass against WCAG AA on dark surfaces.
- Three-platform real-device QA: a representative device matrix (low-end Android, mid-range Android, iPhone, iPad, macOS, Windows desktop).
- Motion and backdrop performance tuning per platform; activate motion downgrade tiers where needed.
- Store packaging: Play Store assets, App Store assets (if iOS ships in this release), Desktop installers (if Desktop ships).
- Crash and error telemetry wiring (no private/host content in payloads; enforced by `Logger` filter).
- Release runbook and rollback rehearsal for the case-management mock backend (test re-publishing an older case version; confirm cache invalidation).
- Final content review of *The Last Dinner* — all reveal narratives, clue pools, dossier briefs proofread and theatrically tightened.

**Expected output.**
- Submittable Android build. iOS and Desktop builds documented as launchable; ship decision per platform recorded.

**Acceptance criteria.**
- Crash-free sessions over a sustained internal-dogfood window (≥ 95th-percentile target).
- All Phase 6 acceptance criteria hold on the device matrix.
- A rollback drill of *The Last Dinner* succeeds end-to-end in under five minutes.

**Must NOT yet include.**
- Post-MVP features.

### Post-MVP — Roadmap

Ordered by leverage, not by engineering ease:

1. **Real local multi-device play.** Production `LocalMultiplayerSessionController`. Concrete `RoomTransport` per platform: Android Nearby Connections, iOS MultipeerConnectivity, Desktop mDNS+WebSocket. Lobby and join flow UI. Mid-game disconnect handling.
2. **More cases.** First post-launch case targets a different setting (modern villa, business retreat, wedding, school reunion, hotel storm). First **8-character** case validates the architecture's 4–8 / 5–8 capability with content, not contrivance.
3. **Easy and Hard difficulty.** Adds public + private clues (Hard) and stronger red herrings; simpler trails (Easy).
4. **Quick Mode and Full Mystery Mode.** New session lengths with tuned pacing.
5. **3-player support and 4-player Elimination.** Returns after 5–6 player pacing is tuned with real-player data.
6. **Voiceover narration** for case intros and reveals.
7. **Unlockable case variations** (accomplice mode, victim survives, killer doesn't know).
8. **Additional game modules.** Second module beyond Whodunit fills out the *All Games* grid.
9. **Localized cases** across languages.

---

## 6. Content Plan — *The Last Dinner*

### 6.1 The schema

*The Last Dinner* is delivered as a JSON payload conforming to the generic `CaseEnvelope` wrapping a `WhodunitCase` payload (`ARCHITECTURE.md` §8). At minimum:

- **Envelope fields:** `schemaVersion`, `caseId`, `title`, `version`, `minimumAppVersion`, `gameId` (= "whodunit"), `supportedPlayerCounts`, `supportedModes` (Classic, Elimination), `language`, `theme`, `estimatedDuration`, `payload`, optional `signature`.
- **Whodunit payload fields:** public intro prose, character roster (each with public identity + Innocent brief + Guilty brief), clue pools per category, reveal narratives per possible killer.

The schema is drafted in Phase 0 and locked before Phase 1 starts.

### 6.2 *The Last Dinner* content package

The first content package contains six characters (matching `whodunit-game-design.md` §19):

1. **Eleanor Hargrove** — the wife. Stage-actress poise. Affair with the gardener.
2. **Daniel Hargrove** — the estranged son. The "reconciliation" was performance.
3. **Vivienne Cross** — the family lawyer. Quietly stealing.
4. **James Sutton** — the business partner. The sale would have ruined him.
5. **Clara Bell** — the housekeeper. Working her final week without a pension.
6. **Dr. Henry Vance** — the family doctor. Overprescribing fears.

Each character has:

- **Public identity** prose (one paragraph).
- **Public motive** (one line).
- **Private secret** (one line).
- **Innocent brief**: alibi (true but uncomfortable), goal, what you can say freely, what you must hide.
- **Guilty brief**: method, fabricated alibi, killer's timeline of movements, deflection target, panic move, acting tips.
- **Reveal narrative** (when this character is the killer): how it actually happened, blow by blow.

### 6.3 Clue pools

Five clue categories (`whodunit-game-design.md` §11), each authored as a pool with variant text:

- **Public universal clues** (always shown): poisoned brandy, 8:30–9:30 window of opportunity.
- **Killer-pointing clues** (one set per possible killer): specific objects, missing items, eyewitness fragments.
- **Red-herring clues** (steer suspicion to a designated innocent, shifting with the killer).
- **Contradiction clues** (someone's story doesn't fit).
- **Final strong clue** (the last-round clue, variants per killer).

Every clue is **falsifiable in discussion**, **short** (museum-label length), and **does not name a player as the killer** — clues name actions, objects, places. Players do the connecting.

### 6.4 Validation rules

Validator behavior is defined in `ARCHITECTURE.md` §8.4. For *The Last Dinner* specifically:

- Killer variants must cover the full character roster.
- Every clue category has at least one entry per declared mode and player count.
- Red-herring targets must be valid character ids.
- Reveal narratives must reference the killer's actual method consistently with their Guilty brief.
- `supportedPlayerCounts` ≤ character roster size.

### 6.5 Bundled fallback

*The Last Dinner* ships as a JSON asset inside `:game-modes:whodunit/content/bundled/`. The bundled version is validated through the same validator at app startup; if validation fails, the case is unplayable (this would indicate a build bug, not a content bug).

The bundled snapshot is **refreshed at each app release** to stay reasonably close to the live API version.

### 6.6 Mock backend delivery

The mock backend serves the same JSON via HTTP. The choice between static CDN JSON and a tiny dev server is made in Phase 0. Either way, the `RemoteCaseDataSource` is the **only** content code path — no shortcut for "load embedded Whodunit data class" exists.

### 6.7 Expansion path to 4–8 players

***The Last Dinner* stays 4–6 players unless explicitly expanded.** Expanding to 4–8 means:

- Authoring two additional characters with full Innocent + Guilty briefs, motives, secrets, alibis, killer-pointing trails, red-herring placement, reveal narratives.
- Rebalancing the cast: each character must still have a strong motive, comparable screen time across clues, and similar suspicion exposure regardless of guilt.
- Reviewing the four-round structure for 7- and 8-player pacing (timer durations may need tuning).
- Re-running validation against the expanded schema instance.

This is **content work**, not engine work. The architecture, Whodunit module, and shell already support 4–8. When an expanded case is ready, the Player Count screen recomputes its options with no code rework (`ARCHITECTURE.md` §1.5).

---

## 7. Platform Plan

### 7.1 First-ship target

**Android is the first-ship target.** Reasoning: the social play context (groups handing a phone around) is most ubiquitous on Android in the target audience, and Android's Compose Multiplatform surface is the most mature.

### 7.2 What must build from day one

From Phase 1 forward, **all three platforms must build and launch** to a styled Home placeholder. No broken builds tolerated. iOS and Desktop don't have to ship in the first release, but their builds gate every PR.

### 7.3 What can be placeholder

- iOS-specific motion downgrades (if a Compose Multiplatform reveal motion doesn't perform on iOS, the design system provides a tuned alternative under the same name).
- Desktop-specific sound implementations (if a sound library lags on Linux, ambient audio degrades gracefully).
- Native share, push, and deep links — Post-MVP.

### 7.4 What must be shared in commonMain

Everything that defines *how the game plays*:

- `:shared:engine`, `:shared:session`, `:shared:content`, `:shared:design-system`, `:shared:navigation`, `:shared:storage`.
- `:game-modes:whodunit` — `domain/`, `content/`, `ui/`, `presentation/`, `navigation/`, `di/`.

If a feature lands in `androidMain` only, it is a bug unless it has a documented platform reason (`ARCHITECTURE.md` §2.3).

### 7.5 Platform-specific work expected later

- Store packaging: Play Store assets, App Store assets, Desktop installers (`.dmg`, `.msi`, `.deb`).
- iOS notarization and TestFlight pipeline.
- Native sharing for "Replay this case with the same group" links.
- Platform-specific haptics for the wax-seal reveal.

---

## 8. Testing and Quality Plan

### 8.1 Test categories

| Layer | Tests |
|---|---|
| **Engine** | Pure reducer tests against the trivial test game and Whodunit's reducer. Snapshot round-trip tests. Phase-transition coverage. |
| **Content** | Validator tests with ≥5 intentionally broken envelope categories. Payload-validator tests for Whodunit. |
| **Repository** | `CaseRepository` tests with fake data sources covering: remote success, remote failure → cache, cache empty → bundled, version newer on remote, malformed remote. |
| **Session** | Pass-and-play controller tests. Phase 7 multi-device shape test. |
| **Presentation** | ViewModel tests with Turbine and `UnconfinedTestDispatcher`. Fakes for the session controller and repositories. |
| **UI** | Compose UI tests for the cover→reveal→hide ceremony, the voting flow, the reveal stage. |
| **Privacy** | Explicit tests: no `PrivatePlayerState` content leaks through `PublicProjection`. No `HostOnlyState` content leaks through any non-host projection. Logger filter blocks private types. |
| **Persistence** | Snapshot/restore tests. Process-death simulation: kill mid-phase, restore, verify exact state. |
| **Architecture** | Konsist or detekt custom rule enforces `ARCHITECTURE.md` §3.2 and §3.3. Build fails on violations. |

### 8.2 Architecture enforcement

The §3.3 purity rules are enforced **at build time**, not by convention:

- `:shared:engine` may not import from disallowed packages.
- `:shared:core` may not import any framework.
- Game-module `domain/` may not import from `ui/`, `presentation/`, or `di/`.

Violations fail the build. The rule lives next to convention plugins.

### 8.3 Visual QA

Per phase, real-device checks on a representative matrix:

- Low-end Android phone (e.g., Pixel 6a class).
- Mid-range Android phone (e.g., Pixel 8 class).
- iPhone (latest minus one generation).
- iPad (recent).
- macOS (Intel or Apple Silicon).
- Windows desktop.
- Linux desktop (best-effort).

Visual QA covers: type rendering, contrast, motion smoothness, backdrop performance (grain + bloom + candle-flicker), sound playback.

### 8.4 Playtest gates

Two gates:

- **Phase 6 internal playtest gate.** Cohort of 4–6 players plays ≥5 full games. Outcomes feed Phase 8 polish.
- **Phase 8 external playtest gate.** Broader cohort (multiple groups, varying composition). Confirms the design doc's success criteria before release.

---

## 9. Risk Register

| Risk | Why it matters | Mitigation |
|---|---|---|
| **UI quality taking too long** | The premium bar is mandatory; underestimating it delays every phase that touches screens. | Design system is Phase 1, not late. Motion downgrade tiers documented per platform. No "polish later" path. |
| **Hardcoding case content by mistake** | The "fake backend from day one" rule is easy to violate under deadline pressure. | Mock backend lands in Phase 3 before any Whodunit screen exists. Bundled fallback also flows through the validator. CI scans source for inline case content. |
| **Engine becoming Whodunit-specific** | Leaks the most expensive form of architectural debt. | Konsist/detekt architecture test on §3.3 purity rules fails the build on violations. Engine module reviewed for Whodunit references at every PR. |
| **Privacy leaks** | A single rendered private projection without ceremony is a critical bug. | Typed projections at the engine level. `ViewerContext` gating in `SessionController`. Cover-screen UI contract. Logger filter against private types. Privacy tests are first-class. |
| **Local multiplayer future drift** | Without the Phase 7 shape test, the pass-and-play controller and UI quietly couple to assumptions a future multi-device controller cannot satisfy. | Phase 7 is mandatory and small. Do not skip. |
| **iOS/Desktop Compose polish/performance** | Cinematic motion + backdrop effects on weaker GPUs may stutter. | Motion downgrade tiers documented. Real-device QA per phase. Backdrop performance is an explicit open question in `ARCHITECTURE.md` Appendix B. |
| **Content balancing** | Killer-pointing trails, red-herring placement, and clue distribution determine whether the game *feels* fair. | Playtest gate at end of Phase 6 informs Phase 8 polish. Content is iterable post-launch via the mock backend (no app updates needed). |
| **Scope creep** | Every "while we're at it" feature delays the answer to "is the game fun?" | Explicit "must NOT yet include" lists per phase. PR review enforces. Post-MVP backlog is the place for new ideas. |
| **Schema versioning at first migration** | The first app update that changes engine state shape needs a clean migration story. | Snapshot version field is allocated; migration policy is documented at the end of Phase 5 before Phase 6 closes. |
| **Mock backend availability** | A flaky mock backend during development blocks all content work. | Use a static CDN (no service to maintain) or a small in-tree dev server. Bundled fallback always works offline. |

---

## 10. Decision Log

These decisions are locked. Future conversations can reference them rather than re-litigating.

1. **Parlor is a platform**, not a single game. Architecture supports multiple modules from day one.
2. **Whodunit is the first module.** Built inside the platform, not as a standalone codebase.
3. ***The Last Dinner* is the first case.** Shipped via the mock backend with a bundled fallback inside the app.
4. **Case content is API-driven from day one.** No inline content. The production code path is the only content path.
5. **Pass-and-play is the MVP topology.** Privacy enforced by UI ceremony (cover screens, hold-to-reveal, hide-and-pass).
6. **Local multi-device is Post-MVP.** Contracts ship in MVP; a small shape test keeps the abstraction honest. The production controller and real transports are Post-MVP.
7. **Premium UI is required from day one and is non-deferrable.** No temporary basic screens. Every Whodunit screen ships at the premium bar from its first commit.
8. **Whodunit supports 4–8 players at module level** (4–8 Classic, 5–8 Elimination). **Cases are capped by their character pool.** ***The Last Dinner* stays 4–6 unless expanded** with two new full characters and full re-balancing.
9. **Engine is pure Kotlin.** No DI, no Compose, no I/O, no platform APIs. Allowlist and denylist in `ARCHITECTURE.md` §3.3.
10. **Three platforms architecturally ready from Phase 1.** Android is first-ship; iOS and Desktop must launch a styled placeholder from Phase 1 forward.
11. **The Player Count display strategy is a product/UI choice**, not architectural. Current product lean for *The Last Dinner* launch is *hide-unsupported* (show only 4–6); the architecture supports either show-and-disable or hide-unsupported without rework (`ARCHITECTURE.md` §1.5).

---

## 11. Immediate Next Steps

The recommended next step is **Phase 0 — Final planning and content/schema audit**. No code is written in Phase 0.

### Phase 0 deliverables

1. **`WhodunitCase` JSON schema draft.** Locked, reviewed, and ready for validation tests in Phase 3.
2. **The Last Dinner content draft.** Six characters (Innocent + Guilty briefs), clue pools, reveal narratives, public intro. Conformant to the schema.
3. **Mock-backend hosting decision.** Static CDN JSON vs tiny dev server. Recorded as a one-paragraph note.
4. **Design-system token spec.** Named values for color palette, typography scale, motion timings, elevation levels, blur radii, corner radii. Ready for Phase 1 implementation without further design clarification.
5. **Dependency lock.** KMP + CMP versions, Koin, Ktor, kotlinx.serialization, SQLDelight (or chosen alternative). Captured in `gradle/libs.versions.toml` draft.

### Stopping conditions for Phase 0

Phase 0 ends — and Phase 1 may begin — when:

- The schema validates *The Last Dinner* draft without errors.
- The token spec is reviewed and approved.
- The mock-backend hosting decision is recorded.

### What Phase 0 does NOT include

- Any Kotlin source files.
- Any Gradle module definitions.
- Any test code.
- Any UI implementation.

### Open questions before coding starts

These are surfaced for the user to resolve before kicking off Phase 0 in earnest:

1. **Mock-backend hosting:** static CDN JSON (e.g., a GitHub-Pages-style host) vs a tiny in-repo dev server. Either is acceptable per `ARCHITECTURE.md` §8.8; preference?
2. **Content authorship:** who writes and reviews *The Last Dinner* prose and dossier briefs? The architecture treats content as a separate workstream; identifying the owner unblocks Phase 0.
3. **Design-system token review:** who signs off on the token spec? Premium UI quality depends on the spec being right; a single owner avoids drift.
4. **Phase numbering alignment in `ARCHITECTURE.md`:** this plan uses Phase 0–8 + Post-MVP; `ARCHITECTURE.md` §14 currently uses Phase 1–7. A short follow-up edit to `ARCHITECTURE.md` would re-align the numbering. Worth doing now, or defer until after Phase 0?
5. **iOS and Desktop release scope:** ship Android only in the MVP, or aim for all three at MVP? Architecture supports both; product decision.

---

*This document is the product-level execution plan. The architectural source of truth is `ARCHITECTURE.md`. The game design source of truth is `whodunit-game-design.md`. Implementation phases reference all three.*
