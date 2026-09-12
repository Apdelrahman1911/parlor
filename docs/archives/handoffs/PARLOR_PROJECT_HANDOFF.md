# Parlor — Project and Agent Handoff

Prepared **2026-09-05**. This is a source-grounded orientation and handoff, not
a new production certification. It describes the existing application, its
contracts, the completed review work, and the remaining work. Paths are
relative to the repository root unless stated otherwise.

**Read this before changing code. Do not start a new navigation migration,
change game rules, or close release blockers merely because an older report
requested those activities. Much of that work is already integrated.**

Quick navigation:

- [Current status](#1-exact-repository-state-and-status), [product](#2-what-parlor-is), [reading order](#3-authoritative-reading-order), [module map](#4-repository-and-module-map).
- [Architecture](#5-architecture-and-the-state-model), [Whodunit](#6-whodunit-content-rules-and-flows), [Mafia](#7-mafia-rules-and-flows), [execution paths](#8-end-to-end-execution-paths).
- [Protocol](#9-networking-authority-and-compatibility), [transport/rejoin](#10-p2pkit-lifecycle-disconnect-and-rejoin), [privacy](#11-privacy-boundaries-what-may-leave-the-host), [persistence](#12-persistence-and-content-validation).
- [Navigation/UI](#13-navigation-3-ui-localization-and-accessibility), [platforms](#14-platform-entry-and-configuration), [build commands/cleanup](#15-toolchain-dependencies-and-commands).
- [Tests/evidence](#16-tests-validation-evidence-and-coverage-limits), [completed work](#17-work-already-integrated-and-launch-history-context), [remaining work](#18-release-system-and-remaining-work), [next-agent instructions](#19-instructions-for-the-next-agent).

## 1. Exact repository state and status

| Item | Verified state when this report was prepared |
|---|---|
| Repository | `Apdelrahman1911/parlor` on GitHub |
| Local/default branch | `main`; local HEAD matches the GitHub default-branch HEAD |
| Commit | `3625d0663ba6eb51338cbd5f9dc45f859ec18846` |
| Git tree | `db7f3d2afe73a13628296daee2cce71165eebc8d` |
| Latest integrated review | [PR #247](https://github.com/Apdelrahman1911/parlor/pull/247), merged 2026-09-04 |
| Reviewed PR head | `47b4ddb9aaafcfdb4e714076b3fe4c29a54af72d`; its complete Git tree equals the merged `main` tree |
| CI | [Run 33880357679](https://github.com/Apdelrahman1911/parlor/actions/runs/33880357679): all five jobs completed successfully |
| GitHub issues | **202 closed, 2 open**, refreshed from GitHub on 2026-09-05 |
| Release verdict | **NOT READY for Store publication**; external gates and the identity migration remain |

The two open issues and all their comments were read for this handoff:

- [#6 — Real-LAN join/message coverage](https://github.com/Apdelrahman1911/parlor/issues/6):
  the actual multi-participant transport boundary still needs truthful evidence.
- [#230 — Store identity collision](https://github.com/Apdelrahman1911/parlor/issues/230):
  `com.parlor.app` is a known collision on both Stores. Owner-controlled
  identities and coordinated configuration/signing/Store changes are needed.

Closed-issue count is **not** a count of confirmed defects fixed: some reports
were already resolved, invalid, intended behavior, or documentation drift.
Likewise, two tracker issues do not mean only two individual release tasks remain.

### Preserve existing local work

Before this report was added, there were no tracked modifications, but the
working tree was **not globally clean**:

```text
?? AGENTS.md
?? design/
?? project-code-audit/
```

Those are pre-existing user files. `AGENTS.md` already exists and was not
overwritten. The older user stash
`7bb58597340521bd58c3fdfb76dbb80e5ca9f593` is also preserved. Historical local
issue/design/review branches remain; a branch name is not proof that its work
is missing from `main`. Do not reset, clean, drop stashes, delete branches, or
stage all files indiscriminately.

This handoff is a new documentation file outside the previously verified tree.
No app code, build configuration, issue state, or branch was changed to prepare
it. No Gradle build or device test was rerun for this documentation task.

## 2. What Parlor is

Parlor is an offline-first **Kotlin Multiplatform / Compose Multiplatform
party-game container**. Android and iOS are shipping targets; Desktop is a
development and deterministic-test target, not a Store product.

Players choose a game, then either share one device or use separate devices
on a reachable local network. In LAN play, one device hosts the authoritative
game; other devices submit requests and display host-approved projections.

| Capability | Whodunit | Mafia |
|---|---|---|
| Game | Authored murder mystery, assigned characters and one killer | Hidden roles, Mafia versus Town, day/night cycle |
| Shipping player count | **Exactly 6**, constrained by every bundled case | **5–16**, including the host in LAN play |
| Engine range | Classic Vote 4–8; Elimination 5–8 | Classic 5–16 |
| Variants | `classic-vote`, `elimination` | `classic` |
| Entry modes | Pass & Play, Host, Join | Pass & Play, Host, Join |
| Local recovery | Protected full-state snapshots | Protected full-state snapshots |
| LAN recovery | Same-host, same-seat credential-based rejoin | Same-host, same-seat credential-based rejoin |
| Timers | Implemented discussion timer | **Intentionally unsupported** |

The home catalog includes local unfinished-game recovery and resumable
multiplayer entry points. Settings provide system/English/Arabic language,
system/light/dark appearance, and reduced motion. English and Arabic UI
resources are shipped; each Whodunit story has its own authored language.
Switching UI language does not translate every story.

### Explicit non-features

- No public-internet matchmaking, rendezvous, relay, or NAT traversal.
- No raw-IP/manual-endpoint/QR joining; typing a room code still uses discovery.
- No browsable room-list UI, spectators, or host migration.
- No shipping Solo mode, despite compatibility vocabulary in some models.
- No account backend, authentication service, cloud save, database, or database migrations.
- No Firebase, APNs, OAuth, associated-domain, or deep-link production integration.
- No ads, in-app purchases, analytics provider, or remote crash-upload provider.
- No universal hotspot promise; support depends on the actual OS/network topology.

Free distribution and a 13+ intended audience are documented product policy;
final Store ratings, legal declarations, and publication are not established
by source code.

## 3. Authoritative reading order

1. Existing root `AGENTS.md`: repository instructions and invariants.
2. [`PRODUCTION_ARCHITECTURE.md`](PRODUCTION_ARCHITECTURE.md): current runtime contracts.
3. Actual definitions, reducers, projection policies, codecs, and their tests.
4. [`MAFIA_RULES.md`](MAFIA_RULES.md) and
   [`CONTENT_SCHEMA.md`](CONTENT_SCHEMA.md): rules and authored-content contracts.
5. [`HOW_TO_ADD_A_GAME.md`](HOW_TO_ADD_A_GAME.md) and accepted
   [`adr/0001-game-module-registration.md`](adr/0001-game-module-registration.md),
   [`adr/0002-manual-endpoint-connection.md`](adr/0002-manual-endpoint-connection.md).
6. [`RELEASE_GATES.md`](RELEASE_GATES.md),
   [`RELEASE_RUNBOOK.md`](RELEASE_RUNBOOK.md), and
   [`RELEASE_AUTOMATION.md`](RELEASE_AUTOMATION.md).
7. [`review/INDEPENDENT_REVIEW_FINDINGS.md`](review/INDEPENDENT_REVIEW_FINDINGS.md)
   and [`review/INDEPENDENT_REVIEW_INVENTORY.csv`](review/INDEPENDENT_REVIEW_INVENTORY.csv).

**Executable source, validators, tests, and Gradle task definitions win over
prose.** Historical references include root `ARCHITECTURE.md`,
`whodunit-game-design.md`, `PROBLEMS_PARLOR.md`, `docs/APP_PLAN.md`,
`docs/DESIGN_TOKENS.md`, and phase reports. Do not reimplement old plans as if
they described missing features today. The untracked `project-code-audit/`
reports are leads, not automatically current findings.

The checked-in independent-review inventory contains **628 rows** at the
baseline above. It provides the per-file map; this document supplies the
cross-file mental model. A recorded `REVIEWED` disposition is dated evidence,
not a permanent guarantee against future defects.

## 4. Repository and module map

There are **13 included application/library modules**, plus the included
`build-logic` convention build. `settings.gradle.kts` is the inclusion authority.

| Module | Responsibility and important entry points |
|---|---|
| `:composeApp` | Platform entry, Koin composition root, catalog, Navigation 3, game-shell bindings, platform storage and permission adapters. Start at `App.kt`, `AppNavigation.kt`, `di/`, `shell/game/`. |
| `:shared:core` | Typed IDs, result/error types, clock/time, seeded randomness, session-seed interface, localization/version primitives. |
| `:shared:design-system` | Theme, typography, colors, metrics, real vector icons, shared controls, insets, privacy/reconnect/exit chrome, locale and reduced-motion adapters. |
| `:shared:engine` | Generic game definitions, reducers, state/actions/events, projections, session configuration, snapshots, registry. No UI, DI, transport, storage, or shipping-game dependency. |
| `:shared:engine-testing` | Non-shipping RoundRobin game/registration fixture. Its `commonMain` is fixture code, not product functionality. |
| `:shared:session` | `SessionController`, local reducer owner, host/peer coordinators, start barrier, process-owned multiplayer runtime, readiness and connection tracking. |
| `:shared:networking` | Transport-independent protocol **4.2**, CBOR envelope codec, semantic validation, room/input/transport contracts, security primitives. |
| `:shared:networking-testing` | Non-shipping `InMemoryRoomBus` and transport fixtures. Never a shipping source-set dependency. |
| `:shared:transport-p2p` | The **only** P2pKit-importing module. Discovery, admission, physical sessions, lifecycle, secure rejoin credentials, resource limits, diagnostics. |
| `:shared:content` | Case envelope/summary types, repositories, cache and bundled/remote interfaces, strict validation. Production remote source is unavailable/offline. |
| `:shared:storage` | Settings contracts, secure-storage contracts, snapshot store, serialized snapshot writer. Platform backing is supplied by the app. |
| `:game-modes:whodunit` | Whodunit rules, codecs, projections, validators, seven bundled cases, local/LAN flows, UI and resources. |
| `:game-modes:mafia` | Mafia rules, settings/presets, codecs, projections, validators, local/LAN flows, UI and resources. |

Supporting directories:

- `build-logic/convention/`: KMP, Compose, Android-library and Detekt conventions.
- `gradle/`: wrapper, dependency catalog, verification metadata.
- `config/`: shared version, Detekt policy, release policy, reviewed lint warnings.
- `iosApp/`: thin SwiftUI/Xcode wrapper, resources, plist/privacy manifest, UI test.
- `assets/branding/`: branding source assets; platform resources live in app source sets.
- `.github/workflows/`: verification and disabled Store workflows.
- `scripts/android/`, `scripts/release/`: runtime-smoke and release validation/operations.
- `docs/review/`: findings, inventory, and commit-attribution evidence.
- `release/mobile-release.json`: public, non-publishing Mobile Release Kit shadow config.
- `design/web-ui-rework/`: preserved web concept, not the app runtime.
- `release/private/`: ignored private operational material; **do not inspect or copy it**.

Source-set conventions are `src/commonMain`, `commonTest`, `androidMain`,
`androidUnitTest`, `androidInstrumentedTest`, `iosMain`/`iosTest`, and
`desktopMain`/`desktopTest`, as applicable. Many game tests intentionally live
in `desktopTest`; not every domain test is an iOS runtime test.

There is no included `:shared:navigation` module. A leftover local directory
with that name is not a navigation architecture or a Gradle dependency.

## 5. Architecture and the state model

```text
Android Activity / iOS SwiftUI + ComposeUIViewController / Desktop Window
    -> Koin composition root
    -> AppNavigator + one Navigation 3 NavDisplay
    -> GameShellRegistry -> selected game binding -> game flow/screens
    -> SessionController
         local: authoritative reducer + protected local snapshot writer
         LAN host: authoritative reducer + generic host session coordinator
         LAN peer: command sender + validated shadow projection, NO reducer
    -> RoomTransport / LocalRoom contracts
    -> P2pKit adapter -> authenticated encrypted LAN transport
```

### Game registration

`GameDefinition<S, A, E>` supplies metadata, modes/counts, initial state,
reducer, projection policy, and snapshot codec. A composition-root
`GameShellBinding` adds catalog presentation and setup/lobby/resume UI.
`ContentModule.kt` registers both bindings and derives `GameRegistry` from the
same list. Duplicate game IDs fail instead of depending on registration order.

Game modules do not depend on app navigation or import P2pKit. Shared modules
do not depend on Whodunit or Mafia. Discovery, admission, ordering, recovery,
and framing must not acquire game-specific `when` branches.

`verifyGameShellDispatch` protects the neutral root, including `App.kt`,
`AppBackPolicy.kt`, `LocalResumeRouter.kt`, `HomeScreen.kt`, and shared
multiplayer shell helpers. Adding a game means module inclusion and
composition-root registration, not modifying the generic multiplayer engine.

### Core contracts

- `SessionConfig`: session ID, case ID, mode ID, fixed roster, hidden seed.
- Reducer: state + action + injected context -> new state + events. It performs
  no transport/storage/UI work and does not decide whether play is local or LAN.
- State buckets: **public**, **per-player private**, **host-only**.
- Projection policy: `toPublic`, `toPlayer(playerId)`, `toHost`.
- `SessionController`: observable projections, canonical state where authorized,
  events, active viewer, `submit`, and `close`.
- `SubmissionReceipt.stateChanged`: actual reducer-commit outcome, not a guess
  made by reading an asynchronously mapped UI flow.

`PassAndPlaySessionController` owns the canonical state under a mutex. The same
implementation is reused behind multiplayer host bridges; its name does not
mean the LAN host runs a different reducer. Event batches reserve commit-order
turns, then emit outside the reducer mutex. Closure is serialized with commits.

`ShadowSessionController` has **no host/canonical state** and can expose only
its own private projection. A player screen needing public and private data
must consume the complete player projection: two independent `StateFlow`
collectors are not a cross-flow transaction, even when the wire snapshot is atomic.

`PartyAwareSession` supplies game-defined missing acknowledgements for local
public/table advances. In multiplayer it is transparent: remote seats must
send their own acknowledgements. It must not bypass private handoff ceremonies.

### Determinism and identity

Production seeds come from `SecureSessionSeedSource` / `SecureIds.randomLong()`.
`RandomSource.seeded(seed)` currently wraps Kotlin's seeded `Random`; pinned
toolchains and golden tests matter. Preserve seed derivation, ordered candidate
lists, and sampling order. Do not replace seeded decisions with wall-clock or
global randomness, or assume a compiler/library upgrade preserves golden results.

`GameId` values are stable kebab-case protocol/persistence identifiers.
`PlayerId`, not a display name or payload-supplied actor, owns a seat. Roster IDs
are unique and seats are contiguous. Display names are trimmed, bounded to 32
UTF-16 code units, well-formed, and reject control/format characters. Canonical
name equality is intentionally exact and case-sensitive.

**Boundary precision:** the contribution guide's ideal “domain depends only
on core/engine” is stricter than some current imports. Mafia's
`domain/rules/MafiaSessionRules.kt` and Whodunit's `domain/rules/WhodunitRules.kt`
use shared `RoomInputPolicy`; Whodunit rules also consume `ValidatedCase` and authored
content. These are deterministic validation dependencies, not P2pKit imports
or reducer I/O. This source/document boundary mismatch remains something to
reconcile deliberately if that policy is audited; this reporting task did not
refactor it or establish a gameplay defect from it. Do not claim the stronger
package-purity rule is mechanically proven for every game file.

## 6. Whodunit: content, rules, and flows

Primary source prefix:
`game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/`.

### Bundled cases

All seven are content version `1.0.0`, require exactly six players, and support
both shipping variants:

| Case ID | Title | Authored language |
|---|---|---|
| `last-dinner` | The Last Dinner | English |
| `layla-halabi` | ليلة العاصفة في بيت الحلبي | Arabic |
| `jasmine-ring` | خاتم الياسمين | Arabic |
| `khan-el-khalili` | ليلة الخان | Arabic |
| `iskenderia-corniche` | صيف الإسكندرية | Arabic |
| `zamalek-ramadan` | ليلة رمضان في الزمالك | Arabic |
| `saidi-inheritance` | حصاد الصعيد | Arabic |

Resources live in
`game-modes/whodunit/src/commonMain/composeResources/files/cases/`.
`BundledWhodunitCatalog.kt` must exactly match those files. A case includes
public intro/bedrock clues, characters with innocent/guilty briefs and timelines,
clue pools, final narratives, and optional per-player-count round configuration.

### Canonical flow

```text
Setup -> PublicIntro -> RulesBriefing -> CharacterReveal
      -> Round 1 ... bounded final round
      -> FinalVote / elimination ballots / TiedRevote
      -> Reveal -> PostGame
```

- Seeded setup selects distinct characters and one killer; secrets stay in
  private/host-only buckets. Intro, briefing, and role-viewing readiness are
  reducer-enforced, not just disabled buttons.
- LAN role viewing is simultaneous per player. Local UI sequences private
  handoffs. The serialized `CharacterReveal.playerIndex` remains canonical at
  zero; it is not the LAN turn scheduler.
- `roleAssignmentGeneration` rejects delayed reveal commands from a previous
  reroll/rematch. Dossiers must be opened and then acknowledged/locked.
- Each round reveals one previously undrawn clue from a deterministic eligible
  pool. Final rounds prefer authored `finalStrong` evidence. The reducer,
  content validator, and recovery validator share clue/round policies.
- Classic uses three rounds for four players and four rounds otherwise; all
  shipped six-player Classic cases therefore use four rounds.
- Elimination permits at most `initialPlayerCount - 2` rounds. Unresolved
  ballots cannot extend the investigation indefinitely and exhaust clues.
- Discussion uses authored seconds, or the policy fallback of 180 seconds.
  Supported authored duration is 1–600 seconds. Only local/host authority drives
  `TimerTicked`/`TimerExpired`; peers display snapshots. Ticks cannot increase
  remaining time. Pause freezes progression; valid expiry advances the game.
- Ballots are **seat-ordered**, first-valid-submission wins, and self-votes are
  rejected. Abstain/refuse advance the same pointer without adding a tally.
  Close is a no-op until every eligible voter has acted.
- Whodunit ballot targets are **secret while collecting**. Projections retain
  who submitted but redact their target. Outcome disclosure is intentional.

### Outcomes

**Classic Vote:** one final accusation after the evidence rounds. Accusing the
killer wins for the players; accusing an innocent wins for the killer. A first
tie opens host-paced defense and one tied-candidate revote. A second unresolved
tie, or an entirely abstained ballot, favors the killer. The legacy tie-duration
field is not an active countdown.

**Elimination:** vote after every round. Eliminating the killer wins immediately;
an innocent elimination is announced before the next round. The killer wins
on surviving to the final two. An unresolved revote skips elimination, but the
finite round limit still applies and an unresolved investigation favors the killer.

Privacy reroll is restricted to character reveal and deterministically changes
the killer and every seat's character; bounded sampling has a deterministic
fallback. Replay resets readiness, clues, timers, ballots, and outcomes and
chooses a different killer when the roster remains eligible. Never implement
reroll by resetting only a visible UI card.

Early end can reveal or not reveal according to the existing action/flow.
Losing a required dossier seat ends the investigation rather than silently
removing a character. Already-eliminated audience seats are not new spectators
and must not pause active surviving players merely by disconnecting.

Key files: `WhodunitDefinition.kt`, `domain/reducer/WhodunitReducer.kt`,
`domain/rules/Whodunit{Rules,RoundPolicy,CluePolicy}.kt`,
`domain/state/WhodunitStateValidator.kt`, `domain/projection/WhodunitProjectionPolicy.kt`,
`ui/flow/WhodunitGameFlow.kt`, `WhodunitPhaseRouter.kt`,
`ui/flow/party/PartyFlowController.kt`, and `ui/timer/DiscussionTickerLoop.kt`.

## 7. Mafia: rules and flows

Primary source prefix:
`game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/`.
The shipping rules are also documented in [`MAFIA_RULES.md`](MAFIA_RULES.md).

### Roles and validated settings

| Role | Team | Night behavior |
|---|---|---|
| Mafia | Mafia | Kill-target vote or explicit skip; knows teammates and receives an anonymized coordination tally |
| Detective | Town | Inspect an active living player or skip; only that Detective receives alignment |
| Doctor | Town | Protect an active living player or skip; owns previous effective protection |
| Civilian | Town | Optional suspicion/skip; suspicion has no rules effect |

There must be at least one Mafia and one Civilian, at most one Detective and
one Doctor, and Mafia must begin as a strict minority. Civilians fill the
remaining seats. `MafiaSettingsPresets` supplies defaults; setup edits must pass
`MafiaSettings.validate` and reducer checks before committing.

Default restrictions: no Doctor self-protection or consecutive same-target
protection, no Detective self-inspection, no self-voting, no Mafia-on-Mafia
targeting. Only represented/validated settings may change these restrictions;
a Mafia player still cannot target themself. Maximum additional day revotes is 3;
the default is 1.

### Canonical flow

```text
Setup -> RoleAssignment -> Night -> NightAnnouncement -> Discussion
      -> Voting -> VoteAnnouncement -> next Night
      -> PostGame as soon as a winner/terminal end is established
```

- Role assignment is deterministic from the session seed. Every active seat
  must acknowledge its role before night one.
- Every active living player makes one night submission, including explicit
  skips. First valid submission is final; absent and skipped are different.
- Detective alignment is computed when the inspect action is submitted and
  must be acknowledged before night resolution, so a Detective killed that
  night does not lose an unread result.
- `ResolveNight` requires all submissions and Detective-result readiness.
  Mafia uses plurality; the default tied first round opens one Mafia-only
  revote. A tied final round chooses deterministically among canonically
  ordered tied targets. Other supported policies are seeded random-tied or no kill.
- Doctor protection can cancel the selected kill. Invalid consecutive
  protection is rejected at submission, not accepted and silently ignored later.
- Dawn communicates death/save and optional death-role reveal. Living active
  seats acknowledge before discussion. The host decides when to open voting.
- Day votes are **public/open by design**, unlike Whodunit's collecting ballot.
  Each living voter casts or abstains once. `CloseVote` rechecks completion.
- Day ties use tied-only revote, all-candidate revote, or skip elimination.
  Exhausting the configured revote allowance causes no elimination.
- Town wins when no active living Mafia remain. Mafia wins at parity or
  majority against active living Town. Natural wins enter PostGame immediately.
- PostGame deliberately reveals **all assigned roles**, even when
  `revealRoleOnDeath` was disabled. Early end with no normal winner displays
  “Game ended,” not an invented Town victory.

The duration fields in serialized settings are compatibility vocabulary:
**every non-null Mafia timer is rejected**. Do not “finish” timed rounds as an
incidental bug fix.

Local pass-and-play sequences covers, role/night actions, acknowledgements,
discussion, and votes. LAN players act on their own devices. Automatic
host-only ready transitions are driven by retained
`MafiaHostProgression.kt`, not a screen's lifetime; foreground reactivation
re-evaluates the current state. A required missing hidden-role seat ultimately
ends the game and reveals roles rather than making the absent role disappear.

Key files: `MafiaDefinition.kt`, `domain/reducer/MafiaReducer.kt`,
`domain/rules/{RoleAssignment,NightResolution,VoteResolution,WinCheck}.kt`,
`domain/settings/`, `domain/projection/MafiaProjectionPolicy.kt`,
`domain/state/{MafiaObservableStateValidator,MafiaPeerSnapshotValidator}.kt`,
and `ui/flow/{passandplay,multidevice}/`.

## 8. End-to-end execution paths

### Local new game and resume

1. Catalog resolves a binding; choose Pass & Play. Whodunit chooses a case and
   mode; Mafia chooses count/names and then validated role/settings setup.
2. Build `SessionConfig` with an independent session ID and secure hidden seed.
3. Construct the game's authoritative `PassAndPlaySessionController` and local
   readiness decorator.
4. UI submits actions; the reducer commits; projections select what the current
   holder may see. Covers/confirmations gate private transitions.
5. A conflated canonical-state collector feeds `SerializedSnapshotWriter`.
   Save, explicit flush, terminal delete, and discard share one serialized boundary.
6. Leave-for-later waits for the required write result. Failed save/delete stays
   actionable; it must not navigate as if successful. PostGame removes the save.
7. On a new process, start at Home. Read authenticated recovery metadata,
   resolve the game binding, then validate envelope, codec, state, and content
   before constructing a resumed controller. Corrupt/unavailable records get
   retry/discard/back recovery, not a guessed new game.

### LAN host/join/start

1. Binding selects Host or Join and applies the platform permission gate.
2. Host enters a canonical display name, selects applicable game setup, and
   creates a room. Whodunit chooses case/mode before the lobby.
3. Peer enters a name and six-character room code. The adapter discovers
   generic Parlor advertisements and dials candidates within bounded deadlines.
4. P2pKit establishes AuthenticatedV2 encrypted transport. The room code is
   proved inside that channel; the host explicitly approves admission.
5. Admission reserves capacity/name ownership atomically. A credential offer
   is durably staged by the peer, confirmed, committed, promoted, and acknowledged;
   a ready barrier prevents replay-zero collectors from losing initial messages.
6. The host freezes the roster and starts the game-specific retained runtime.
7. A separate game-start barrier validates game/version, roster, mode, and case
   identity before peers enter gameplay. Only then are player snapshots installed.
8. The host participates as a seated player while remaining the sole reducer
   authority. Ordinary peers cannot invoke host phase advances.

### One LAN gameplay action

```text
Peer UI -> game action codec -> peer coordinator ClientCommand
        -> encrypted RoomMessage -> transport-attested seat
        -> generic protocol/order/revision validation
        -> game action-authority policy -> host reducer
        -> commit receipt + authoritative revision
        -> CommandResult + recipient-specific PlayerSnapshot
        -> peer semantic/projection validation -> shadow state -> UI
```

Host UI mutations go through the same host coordination/revision boundary.
The UI must not optimistically alter canonical peer state or reinterpret
“transport accepted” as “host applied.”

## 9. Networking, authority, and compatibility

### Distinct version domains

| Version | Current value | Authority |
|---|---|---|
| App version/build | `1.0.0` / `1` | `config/parlor-version.xcconfig` |
| Shared room protocol | **4.2**, exact major/minor equality | `shared/networking/.../protocol/Protocol.kt` |
| Whodunit multiplayer game version | **6** | `WhodunitHostRoomBridge.GAME_VERSION` |
| Mafia multiplayer game version | **2** | `MafiaHostRoomBridge.GAME_VERSION` |
| Content envelope schema | **1** | Content DI and validators |
| Whodunit local snapshot | Engine version **1.2.0**, game-owned wrapper schema **1** | `WhodunitSnapshotFormat.kt` |
| Mafia local snapshot | **1.0.0** | `MafiaSnapshotRecovery.kt` |
| Protected rejoin record | Schema **1** | `ResumableCredentialStore.kt` |

These numbers are not interchangeable. Protocol 4.2 is not “any protocol 4.x.”
Unknown fields, unsupported action variants, invalid IDs, malformed bytes,
wrong session/game tuples, and invalid projection shapes fail closed. Some
legacy declarations still exist; a serializable declaration alone is not
permission to accept it in the current validator/authority path.

### Envelope and command guarantees

`RoomMessageCodec` encodes strict **CBOR**, including actual byte strings for
payloads, over `P2pMessage.Binary`. Game-owned payloads have their own codecs.
Transport text messages are not an alternate command channel.

`SessionEnvelopeHeader` binds protocol, session ID, game ID/version, message ID,
sequence, and logical wire epoch. Peer envelope sequence is zero; sequenced
host envelopes require positive sequence. `connectionEpoch` currently stays
one; actual stale physical callbacks are guarded by room/session generations.

`ClientCommand` additionally carries command ID, client sequence, expected
revision, and action payload. The transport attests the actor; game authorities
reject actions for another seat or host-only operations. Host results distinguish
applied/duplicate/stale/unauthorized/invalid/sequence-gap/suspended/terminal and
other explicit outcomes.

One mutation is in flight per peer. Outcome queries recover an ambiguous
result **without replaying the mutation**. A snapshot carries the host's next
expected client sequence so a recreated peer does not reuse old sequence space.
Never auto-retry a rejected non-idempotent action against a newer state.

### Start transaction

```text
SessionStarting(startId)
 -> SessionStartReady
 -> host commit / SessionStartCommitted(startId)
 -> SessionStartCommitAck
 -> validated initial player snapshot
```

The stable start ID is retained/replayed across rejoin. Readiness and commit
phases are bounded; defaults are 20-second phase budgets and 2-second outbound
control-send budgets. A delivery ACK is not permission to roll back committed
gameplay. Whodunit validates both exact content version and canonical digest;
the public start nonce is **not** the hidden game seed.

### Resource limits

| Boundary | Current bound |
|---|---|
| Command payload | 32 KiB |
| Snapshot payload | 256 KiB, with validation of the complete permitted shape |
| Control payload allowance | 8 KiB |
| Peer-to-host / host-to-peer frames | 40 KiB / 272 KiB |
| Application queues | Host 16, peer 8 |
| Inbound rate | 32-frame burst, 16 frames/second sustained |
| Admission bookkeeping | 17 pending, 21 physical sessions, 128 remembered attempt identities |
| Host command outcome ledger | 256 remembered commands per peer |
| Peer seen-host-message ledger | 2,048 entries |

Generic transport ceilings do not expand a game's supported roster. Slow/flooding
peers must not create unbounded channels or block healthy recipients. Keep
`BoundedPeerOutbox`, admission limits, timeout ownership, and violation isolation.

## 10. P2pKit, lifecycle, disconnect, and rejoin

Only `:shared:transport-p2p` imports `dev.p2pkit`. Platform factories supply
`lan(applicationContext)` on Android and `lan()` on JVM/iOS. The adapter is
always included: a missing dependency must not silently build a local-only app.

The application advertises a generic same-app Bonjour service, not the room
code or player name. `P2P_APP_ID` is currently `com.parlor.app`; the Bonjour
service is `_p2pkit2._tcp`. `supportsDiscovery = false` means the public
**browsable-list** contract is not implemented, not that room-code joining
avoids internal discovery. `supportsManualEndpointConnection = false` is intentional.

Discovery/connection defaults include a 30-second overall join budget,
5-second dial/handshake and first-response budgets, and a separate 60-second
host-approval budget. A wrong code at one candidate does not prematurely stop
searching other eligible candidates. Cancellation closes owned attempts.

### Ownership and lifetimes

- `ProcessMultiplayerSessionOwner`: one active logical route, retained roster,
  checkpoint and game runtime; states include Idle, Opening, Active, Retryable,
  Failed, Closing.
- Game-owned retained runtime: reducer/bridge/progression jobs live with the
  session, not transient Compose screens.
- `AppLifecycleCoordinator`: platform visibility policy.
- `AppLifecycleRoomCoordinator`: serialized room registration and lifecycle.
- `PeerConnectionTracker`: seat connectivity/grace ownership with generation checks.

On iOS, **inactive is not background**. Inactive covers secrets but must not
tear down a still-foreground LAN session. True background retires physical
transport resources while preserving bounded logical resume ownership. Repeated
callbacks/foreground retries cannot extend the original deadline indefinitely.

### What recovery means

- A disconnected required seat is reserved for **120 seconds**. Gameplay waits
  while a required participant is unavailable.
- Peer secure storage retains an opaque credential bound to original
  host peer/fingerprint, player/seat, room, game/version, generation, and expiry.
  The host retains a digest in memory, not the plaintext secret.
- Rejoin rotates the credential transactionally. Its 24-hour cryptographic
  maximum never extends room lifetime or the 120-second seat grace period.
- Transient disconnect/background and peer process death preserve the
  credential. Explicit Leave must durably delete it, including after failed
  cold resume when no `LocalRoom` was reconstructed.
- Host “continue without” is confirmation-gated. In active hidden-role games,
  expiry/continuation ends play according to the game rules; it is not a license
  to remove a secret-bearing seat and continue an invalid game.
- Host exit/process death or unrecoverable host loss is terminal. There is no
  canonical peer save from which to elect a replacement host.
- Authenticated terminal traffic is still staged and validated for protocol,
  revision, and ownership before credential revocation/terminal publication.
  Failed durable deletion must remain an actionable failure.

First-contact transport trust is not an account PKI or out-of-band host
verification system. Room-code proof and host approval protect the supported
same-LAN model; do not describe a display name as a verified human identity.

## 11. Privacy boundaries: what may leave the host

| Information | Permitted recipients |
|---|---|
| Public roster, phase, readiness, clues, announcements, public outcomes | Admitted session participants |
| One player's private role/dossier/action/result | That player only, plus the trusted authoritative host's internal state |
| Mafia teammates/coordination | Only the eligible Mafia private projections |
| Detective result | Owning Detective only during play |
| Whodunit collecting vote target | Host only until the intentional public outcome |
| Mafia day ballot target | Public by the existing Mafia rules |
| Seed, complete live role/character map, host resolution bookkeeping | Host only; never a peer snapshot |
| Room/rejoin secrets | Only their dedicated encrypted admission/resume exchange and protected storage; not gameplay projections, discovery, logs, or accessibility labels |
| Mafia final roles / Whodunit final killer narrative | Public only at the intentional reveal/terminal stages |

`PlayerSnapshot` is an atomic public projection plus the **recipient's private
slice** at one revision. Host bridges derive both from one synchronous
canonical read; peer bridges validate both before installation. Never serialize
`toHost()` for convenience and try to hide fields in the UI afterward.

Public/bundled story content is not a secret download. The secrets are which
roles/characters were assigned, whose private actions occurred, and unrevealed
runtime decisions. Do not infer privacy solely from whether an asset is bundled.

`ParlorP2p` diagnostics accept closed enums and coarse numeric metadata only:
256-record memory ring, one-record drop-oldest output backlog, at most ten lines
per second. No IDs, names, codes, IPs, fingerprints, credentials, raw packets,
private state, or exception text. There is no upload mechanism in Parlor.
OS console retention is outside the app's ring-buffer guarantee.

### App-switcher privacy

- Android 13+: disables Recents screenshots, retaining intentional screenshot
  capability. Older supported Android uses `FLAG_SECURE`, which also restricts
  screenshots/recording.
- iOS: SwiftUI covers the entire scene in black and hides underlying
  accessibility content whenever not active; only actual background signals
  suspend transport.

These implementations and automated checks are not a substitute for physical
app-switcher, screen-reader, lock/unlock, and interruption verification.

## 12. Persistence and content validation

There is **no Room/SQLDelight/SQLite database** and no KSP database generation.
Do not invent repository/use-case/DAO layers when following current execution paths.

### Local snapshots

`FileBackedSnapshotStore` serializes the generic envelope; platform
`SnapshotFileSystem` implementations encrypt/authenticate before disk. Although
filenames end in `.snapshot.json`, current on-disk records are protected bytes.

| Platform | Protection/backing |
|---|---|
| Android | AES-256-GCM, non-exportable Android Keystore key, filename authenticated as associated data, atomic replacement in `noBackupFilesDir` |
| iOS | AES-256-CBC + HMAC-SHA-256 encrypt-then-MAC with independent device-only Keychain keys; Application Support files, complete Data Protection, backup exclusion |
| Desktop | Owner-protected development key/files and authenticated encryption; not a shipping mobile security claim |

The platform plaintext ceiling is 8 MiB; game codecs/protocol impose smaller
payload limits. Safe filenames reject traversal. Legacy plaintext migration is
record-by-record and removes the original only after protected replacement.
One corrupt record must not hide all healthy saves. Failed directory listing
is unavailable storage, not “no saves.”

`SerializedSnapshotWriter` bounds work through conflated state collection and
serializes writes, terminal deletion, retry, and discard. Successful explicit
discard cannot be resurrected by a late collector. Expected failures become
typed results; coroutine cancellation is rethrown.

Whodunit current snapshots have a self-identifying game wrapper and canonical
shape validation. Explicit legacy bare-payload migrations support older 1.0/1.1
formats; current malformed data does not receive legacy “repair.” Recovery also
checks case version/digest and clue/character reachability. Retired Solo saves
have an explicit removal path; they are not silently converted to Pass & Play.

Mafia recovery validates roster/settings, role/private consistency, phase
flags, legal targets, readiness, terminal visibility, and bounded resolution
history. Local recovery rejects multiplayer disconnect/drop state. Both games
validate phase/envelope agreement and never reinterpret another game's bytes.

### Rejoin credentials and settings are separate

`ResumableCredentialStore` uses protected key/value storage and an 8 KiB record
limit. It stores a membership capability, **not the host's game state**.

Settings persist language override, theme, and reduced motion. Android uses its
platform preference backing, iOS uses UserDefaults, and Desktop its own local
backing. Settings mutation ordering is serialized. On iOS, immediate
in-process state publication does **not** promise synchronous durable disk write;
the backing performs ordered asynchronous eventual persistence.

### Case repository

`DefaultCaseRepository` implements cache/remote/bundled resolution and validates
source identity before returning/caching content. Production binds
`OfflineRemoteCaseDataSource` and an in-memory cache; bundled resources are the
actual content source. The Ktor implementation is not an enabled production
backend, and `MockEngine` belongs only in tests.

Validation includes strict JSON/UTF-8, known schema/app/game/mode versions,
bounded summaries/payloads, unique IDs, safe authored text, valid character
references, and enough eligible evidence for every declared killer/mode/count.
Non-null content signatures are rejected because there is no configured
key-pinned signature verifier. Legacy structured-action vocabulary is not an
implemented gameplay feature. See `CONTENT_SCHEMA.md` and `CONTENT_REVIEW.md`.

## 13. Navigation 3, UI, localization, and accessibility

### What is implemented now

**Navigation 3 is already integrated.** There is no Navigation 2 `NavController`
or second UIKit navigation stack to migrate away from.

- `AppNavigation.kt`: serializable typed `AppRoute` keys and the sole shell
  mutation owner, `AppNavigator`.
- Routes: `Home`, `Settings`, `Game(gameId, entryId)`,
  `LocalResumeFailure(sessionId)`.
- Two non-empty independent top-level stacks: **Games** and **Settings**.
- `AppNavigationHost.kt`: one `NavDisplay`; separate saveable entry decorators
  retain top-level UI entries.
- Bottom bar is shown only at top-level destinations. Tab selection cannot
  bypass a running game's save/leave transaction.
- Launch/session objects remain process-local; routes do not serialize room
  secrets, full game state, controllers, names, or live transport ownership.
- Shell stacks deliberately are **not restored across process death**. Recovery
  starts at Home using authenticated local saves or validated transport rejoin.
- Game bindings own their remembered setup/subflow state and delegate game
  phases to game routers. The shell does not create one Nav3 route per round,
  role, or night action.

There are no feature `ViewModel` subclasses in current production source.
Flows use remembered state/controllers, coroutine scopes, Koin dependencies,
and retained process-session ownership. The presence of a Koin ViewModel
dependency does not mean entry-scoped ViewModels are the current implementation.

### Back and stale operations

`AppBackPolicy.kt` lets Home defer to platform exit, returns Settings to Games,
lets NavDisplay handle recovery Back, and delegates active games to their
binding. While a game is active, the visible NavDisplay entries exclude the
previous catalog entry so predictive Back cannot reveal it before authorization.

Android uses `BackHandler`; iOS and Desktop register with Navigation Event for
edge Back/Escape. Game flows own confirmation and save/leave completion.
Navigator callbacks check the expected active route. `LocalResumeCoordinator`
uses cancellation plus generation/route checks so an older load cannot navigate
after a newer tap or after leaving Home.

iOS transition actuals mirror the pinned Nav3 UI's LTR-only regular motion for
RTL while retaining library-owned interactive progress. Reduced-motion policy
is shared. Do not assume a newer navigation library's defaults or skill example
versions are drop-in compatible with the pinned `iosX64` target.

### Design system and safe areas

The implemented design is editorial/cozy-noir: game-first catalog, warm amber
Whodunit accent, crimson Mafia accent, contextual authority/privacy ribbons,
shared cards/actions, and real vector icons from `ParlorIcons.kt` rather than
text glyphs. Typography uses bundled Inter and JetBrains Mono tokens; actual
Arabic fallback/rendering and large-text behavior still need device evidence.

Backgrounds and scrolling viewports fill the screen. **Interactive/readable
content still respects system bars, cutouts, keyboard, and sticky controls.**
“Full screen” must not be implemented by drawing buttons beneath the status bar
or adding the same safe-area padding twice.

Use `ParlorSafeArea.kt`, `StickyActionBar.kt`, `SessionExitControls.kt`,
`ParlorToastHost.kt`, `ScreenHeader.kt`, and existing screen conventions.
Insets should be content-aware; lazy containers have padding-value helpers,
and floating exit chrome reserves measured safe content space. Recheck short
screens, landscape, 200% text, IME, RTL, and bottom-bar presence after layout edits.

`ProvideAppLanguage` maintains a stable composition while updating resources
and layout direction. It must not key/recreate the entire app/session tree on
language changes. User and system reduced-motion settings are combined.
Accessible names, headings, roles, live regions, directional icons, and privacy
must be reviewed together. Hidden roles must not remain in semantics behind a cover.

### Web concept

`design/web-ui-rework/` contains dependency-free `index.html`, `styles.css`,
`app.js`, and a README. It is a preserved design prototype, not a WebView or
the production game's state machine.

```bash
# From repository root:
python3 -m http.server 4173
# Open http://localhost:4173/design/web-ui-rework/

# Alternatively, from design/web-ui-rework/:
python3 -m http.server 4173
# Open http://localhost:4173/ — not the nested repository path.
```

Stop the server after reviewing. Future UI work must preserve the existing
engine/session/privacy architecture rather than copy prototype behavior as rules.

## 14. Platform entry and configuration

### Android

`ParlorApplication.kt` starts DI and process lifecycle handling.
`MainActivity.kt` installs Compose synchronously with a first-frame surface,
loads initial settings off the UI dispatcher, enables edge-to-edge drawing,
and applies Recents privacy.

The manifest enables RTL, disables backup/cleartext traffic, and declares only
the LAN permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
and `CHANGE_WIFI_MULTICAST_STATE`. No dangerous Nearby/Location permission
is required for this implemented LAN path. Adding provisioning permissions
without adding/reviewing the corresponding feature is wrong.

### iOS

`iosApp/iosApp/iOSApp.swift` -> `ContentView.swift` -> exported
`MainViewController()` in `composeApp/src/iosMain/.../MainViewController.kt`.
The Swift wrapper provides the scene privacy cover and visibility bridge;
Kotlin owns application UI, state and navigation.

The app embeds the dynamic **`ComposeApp.framework`** through
`embedAndSignAppleFrameworkForXcode`. No CocoaPods or SPM glue is required.
`normalize_embedded_apple_framework.sh` validates/corrects framework casing for
case-sensitive packaging. iOS deployment target is 16.0.

`Info.plist` declares English/Arabic, orientations, the Local Network purpose,
and `_p2pkit2._tcp`. There is no truthful Local Network preflight grant API.
Operational status requires actual advertisement/connection evidence; generic
timeout is not asserted to be permission denial. Actionable denial exposes
Settings recovery, then another real attempt.

`PrivacyInfo.xcprivacy` declares no tracking/collected-data categories and lists
required-reason APIs. Source declarations must still be checked against the
final signed artifact and owner-approved Store answers.

### Desktop

`composeApp/src/desktopMain/.../Main.kt` creates one Compose window and DI.
Window close gives logical multiplayer Leave a bounded five-second opportunity,
then cancels session/transport scopes and exits. Do not leave LAN/native jobs
alive after a development window closes. Desktop distribution/signing is not a
mobile release gate.

## 15. Toolchain, dependencies, and commands

| Component | Pin |
|---|---|
| Java / Gradle | JDK 21 / checked-in Gradle wrapper 8.13 |
| Kotlin | 2.4.10 |
| Compose Multiplatform / Material3 | 1.10.3 / 1.9.0-beta03 |
| Navigation3 runtime / JetBrains UI | 1.0.0 / 1.0.0-alpha06 |
| Coroutines / Serialization / Datetime | 1.11.0 / 1.11.0 / 0.7.1 |
| Koin / Ktor | 4.0.0 / 3.0.3 |
| P2pKit core and LAN | Both **0.7.0-rc3**, Maven Central |
| AGP | **8.13.2** |
| Independent Android analyzers | Lint **9.1.1**, R8 **9.1.41** |
| Android | minSdk 26, compileSdk/targetSdk 36 |
| Store-qualified Apple toolchain | Xcode **26.3 / 17C529**, policy SDK floor 26 |
| Static/architecture checks | Detekt 1.23.7, Konsist 0.16.1 |

The lint/R8 pins understand Kotlin 2.4 metadata and are not reasons to downgrade
AGP/analyzers. Navigation UI alpha06 retains `iosX64`; review published target
coverage before upgrading. Runtime app version is generated from
`config/parlor-version.xcconfig`, the same source Xcode/Android use.

Use **`./gradlew`**, never system Gradle. Strict dependency verification is
the default. P2pKit must not resolve through `mavenLocal()`, a sibling checkout,
or repository override. Review the dependency graph and
[`P2PKIT_MAVEN_PROVENANCE.md`](P2PKIT_MAVEN_PROVENANCE.md) before touching
`gradle/verification-metadata.xml`; do not regenerate it to silence a failure.

### Development and focused checks

```bash
./gradlew :composeApp:run
./gradlew :composeApp:installDebug
./gradlew :composeApp:desktopTest --tests '*AppNavigationTest*'
./gradlew :game-modes:whodunit:desktopTest --tests '*WhodunitSnapshotValidationTest*'
./gradlew :game-modes:mafia:desktopTest --tests '*MafiaSnapshotRecoveryTest*'
./gradlew :shared:session:desktopTest
./gradlew :shared:transport-p2p:desktopTest
```

Use focused module tests during an issue cycle, not an expensive full release
matrix after every small edit. Add `--dependency-verification=strict` when
recording evidence. Open `iosApp/iosApp.xcodeproj` or the `.run/iOS App` run
configuration for iOS; follow [`IOS_SETUP.md`](IOS_SETUP.md) for exact commands.
Device deployment requires authorized signing and the intended identity.

### Repository gates and their actual scope

| Command | Meaning |
|---|---|
| `./gradlew productionDesktopCheck` | Every included KMP module's Desktop tests and app Desktop compilation |
| `./gradlew allTests --dependency-verification=strict` | Explicit root aggregate of registered module `allTests`; host limitations/skips must be interpreted |
| `./gradlew productionCheck --dependency-verification=strict` | Host-independent Desktop/Android tests, unsigned Android release/R8/lint/manifest/identity gates, plain/type-aware static analysis, release validator, shell dispatch |
| `./gradlew productionAndroidCheck` | Android unit variants, release compile/R8/AAB, lint warning inventory, merged manifest and identity verification |
| `./gradlew productionAndroidRuntimeCheck` | R8-release managed Android device smoke task; requires its emulator/image/signing setup, not production signing |
| `./gradlew productionAppleCheck --dependency-verification=strict` | Apple type-aware static analysis and serial Release framework linkage for device arm64, simulator arm64 and simulator x64 |
| `./gradlew productionIosSimulatorRuntimeTests --dependency-verification=strict` | Every registered executable arm64 iOS simulator test task; not physical-device tests |
| `./gradlew staticAnalysis` | Repository and build-logic Detekt source scan |
| `./gradlew productionStaticAnalysis` | Plain plus host-independent type-aware analysis; Apple analysis is separately wired |
| `./gradlew productionAndroidSigningCheck --no-configuration-cache` | Protected real signing-material verification; not usable as proof without credentials |
| `scripts/release/validate_release_system.sh` | Release-policy/provenance/security/workflow tests, contract and inventory validation |

`productionCheck` is **not** Apple verification, `allTests`, or physical
multiplayer verification. Release framework linkage is **not** runtime launch.
The unsigned Swift Release-wrapper check uses `ARCHS=arm64`,
`ONLY_ACTIVE_ARCH=YES`, signing disabled, and a controlled DerivedData location;
copy the full maintained command from `IOS_SETUP.md`.

### Mandatory disk and memory workflow

This applies after **every build/test/check cycle**, including failed ones and
Xcode phases that invoke Gradle:

1. Collect exit status and needed reports/logs; preserve required artifacts and
   sanitized evidence outside directories about to be cleaned.
2. Immediately run `./gradlew --stop`.
3. Run `./gradlew clean --no-daemon` when safe, or remove only the specific
   generated module `build/` outputs no longer needed.
4. Run `./gradlew --stop` again if cleanup started a daemon; inspect leftovers,
   including build-logic/DerivedData generated by this cycle.
5. Stop only the app/test/server/simulator processes owned by the task once done.

Never remove source, configuration, signing material, dependency verification
metadata, needed signed artifacts, preserved user work, or global Gradle caches.
Do not run broad destructive `git clean` or blanket filesystem cleanup.

Gradle is configured for a 6 GiB heap with parallelism/caching. Avoid multiple
competing Gradle/Xcode builders. Apple release links are intentionally
serialized to control LTO memory. Do not remove that ordering or keep daemons
and large outputs alive between issue branches.

## 16. Tests, validation evidence, and coverage limits

Tests use Kotlin Test, JVM JUnit Platform, coroutines virtual-time tests,
AssertK/Turbine where appropriate, Compose UI tests, Konsist architecture
checks, Android runtime smoke tests, Apple simulator tests, XCTest launch
tests, and Python/shell release-system contracts. There is no declared numeric
coverage percentage that proves readiness.

High-value regression entry points:

| Area | Representative suites |
|---|---|
| Navigation/root/recovery | `AppNavigationTest`, `AppBackPolicyTest`, `LocalResumeRouterTest`, `MultiplayerRouteRestorationTest`, `HomeRecoveryAvailabilityTest` |
| Extensibility/isolation | `GameShellRegistryExtensibilityTest`, `GameShellRegistryCompositionTest`, engine `PurityTest`, `TestTransportIsolationContractTest` |
| Session ordering/lifecycle | `AuthoritativeSessionCoordinatorTest`, `SessionStartHandshakeTest`, `ProcessMultiplayerSessionOwnerTest`, `PeerConnectionTrackerTest`, `OrderedEventEmissionTurnTest` |
| Protocol/transport | `ProtocolValidationTest`, `RoomMessageCodecTest`, `P2pKitRoomTransportLifecycleTest`, `DiscoveryCandidateSchedulerTest`, `P2pTrafficPolicyTest` |
| Whodunit | `FullGameDriveTest`, `TiedRevoteTest`, `TickerAndRerollTest`, `WhodunitPolicyGoldenTest`, `WhodunitSnapshotValidationTest`, `MultiDevicePartyPlayContractTest`, `VoteRedactionTest` |
| Mafia | `FullGameDriveTest`, `MafiaReducerEdgeCasesTest`, `NightResolutionTest`, `MafiaSnapshotRecoveryTest`, `MafiaPeerSnapshotValidatorTest`, `MafiaAuthoritativeLifecycleTest`, `MafiaProjectionLeakTest` |
| Persistence | `FileBackedSnapshotStoreTest`, `SerializedSnapshotWriterTest`, `ResumableCredentialStoreTest`, platform storage safety/migration tests |
| UI/resources | `ProductionUiAccessibilityContractTest`, `LocalizationResourceContractTest`, Whodunit accessibility/layout tests, design-system safe-area/toast/sticky/action tests |
| Release | `ProductionVerificationWorkflowContractTest`, `AndroidReleaseLintContractTest`, provenance/workflow/tamper tests under `scripts/release/tests/` |

Detekt is repository-wide with `maxIssues: 0`; no baseline/blanket suppression
shortcut. Existing targeted suppressions need their specific justification,
not wholesale removal or expansion. Reviewed Android lint warnings live in
`config/android-lint-accepted-warnings.txt`; a successful lint gate does not
mean there are zero informational dependency advisories.

`shared/transport-p2p` Desktop tests also bind documentation, manifests,
plists, workflows, and verification metadata. Non-Kotlin edits can invalidate
their cached result. The review generator checks the **tracked** inventory:

```bash
python3 scripts/generate_review_inventory.py --check
```

When deliberately tracking a new file, stage only the intended change,
regenerate the inventory per `docs/review/README.md`, and review the resulting
diff. Untracked user files are intentionally excluded.

### Latest evidence, not newly rerun results

The live-checked CI run listed in section 1 passed these five jobs at the
reviewed PR head whose tree equals merged `main`:

1. Linux x64 Common/Desktop/Android release, including managed-device smoke.
2. Linux arm64 strict Desktop.
3. macOS x64 strict Desktop and Kotlin/Native distribution resolution.
4. Windows x64 strict Desktop/Native and Android resource processing.
5. macOS arm64 iOS simulator tests, Apple Release frameworks, plist/identity
   checks, actual SwiftUI/Compose simulator launch, and unsigned Swift Release wrapper.

The findings register separately records local full matrices and the
post-integration `productionCheck` receipt: **903 Gradle tasks**, with **130
release-system tests and 628 inventory rows**. Task counts are not test-case
counts. These are dated receipts, not new tests performed for this handoff.

### Known unexecuted boundaries

Three `@Ignore` tests in `P2pKitRoomTransportLoopbackTest` remain intentional:
join/membership and bidirectional delivery require two LAN participants;
broadcast requires three. Single-JVM loopback/JmDNS multicast is not reliable
proof of that boundary. The runnable real-kit advertisement test does not prove
joining or gameplay. Do not un-ignore them and report success without the
required environment or a proven independent-participant harness.

Host-incompatible runtime tasks are also not passes. Linux arm64 is a supported
Desktop verification host, not a Kotlin/Native host distribution. Apple x64
linkage on arm64 is not x64 simulator execution. Unit/semantic UI tests do not
establish physical accessibility, frame-time, battery, thermal, or long-session
network behavior.

## 17. Work already integrated and launch-history context

The detailed fix-to-test-to-commit mapping is in the findings register and
GitHub history; do not duplicate it by reopening every historical ticket.
Integrated work includes:

- Registry-driven game shell and non-shipping fixture isolation.
- Whodunit deterministic clues, finite rounds, ballots/ties, reroll/rematch,
  reveal generations, readiness and reachable snapshots.
- Mafia rules/authority, night readiness and Detective results, role privacy,
  terminal outcomes, validated settings and snapshot history.
- Protocol 4.2 command ordering/deduplication, revision/result ownership,
  start barrier, atomic projections, and restored peer command sequence.
- P2pKit lifecycle/discovery/admission limits, cancellation, transactional
  credential rotation/revocation and failed-cold-resume Leave.
- Protected local snapshots, corruption/migration handling, serialized
  persistence and explicit recovery errors.
- UI rework, real icons, full-screen backgrounds with corrected safe areas,
  Navigation 3 and Games/Settings bottom bar, cross-platform Back, locale
  state preservation, accessible semantics and reveal completion gates.
- Strict dependency provenance, complete module test aggregates, static/lint
  gates, cross-host CI, unsigned Android/Apple validation, and release safeguards.
- Non-publishing Mobile Release Kit shadow configuration and app-target-only
  signing contract fix.

### Do not confuse a failed launch probe with a proven app crash

One recent iOS CI failure rendered the app and kept it foreground but searched
for obsolete display text `PARLOR`. Commit `994ff43` added the stable
`parlor-home-brand` Compose test tag and updated XCTest; the focused test and
subsequent five-job CI passed. That particular failure was a UI-test selector
regression, **not an app crash**.

Two earlier Apple linkage attempts lost a shared Gradle daemon; an isolated,
serial rerun passed. That is separate from runtime termination on an iPhone.
Neither observation proves why any future device launch fails. If the
reported repeated-first-launch problem returns, capture the exact build,
device/OS, Xcode/device crash or termination report, and reproduction before
assigning a cause. A successful compile or fourth retry is not a root-cause fix.

## 18. Release system and remaining work

### Current publishing stop

Release Android/iOS identities are pinned to `com.parlor.app`, while Debug
uses `com.parlor.app.debug`. The Store identity is known to belong to unrelated
apps. `verifyApplicationIdentities` pins the present contract, but Store
validators explicitly reject publishing that identity. This deliberate
fail-closed stop must be migrated coherently, not bypassed.

`config/release-policy.json` marks both identities blocked. All jobs in
`testing-candidate.yml`, `testing-external-promotion.yml`, and
`production-promotion.yml` have unconditional disabled guards. Documentation
also records GitHub-level disabling; refresh live controls before any future
release operation. `production-verification.yml` is the active secretless path.

### Intended workflow after owner approval

```text
main: reviewed development + verification
  -> testing: exact-SHA candidate, build/sign final AAB and IPA once
  -> internal testing -> separately approved external testing
  -> release: promote the SAME recorded Store builds, no rebuild
  -> review/approval/publication as separate, explicitly authorized states
```

Candidate receipts bind commit/tree, version, artifact hashes/signers,
permissions/entitlements, provenance, and Store IDs/readback. Same version or
branch name alone is insufficient. Equivalent-tree promotion is restricted to
the validated repository/history contract. Retries use recorded upload intent
and receipts, not blindly repeated uploads.

`publish=false` is a non-promotable rehearsal, not the later published
candidate. Upload, processing, tester availability, review, approval, and live
publication are distinct results. Never rebuild during promotion or claim
“released” after an upload.

### Mobile Release Kit state

`release/mobile-release.json` is **shadow configuration only**. Existing Parlor
release validators/workflows remain authoritative and disabled from publication.
Android accepts reviewed `MOBILE_RELEASE_ANDROID_*` fallbacks after existing
`PARLOR_ANDROID_*` variables; `MOBILE_RELEASE_REQUIRE_SIGNING=true` fails closed
if incomplete. iOS Release mappings are app-target-only. No secret is embedded
in public configuration.

There is no authorized shared-kit caller rollout. Before one is added, obtain
a reviewed immutable kit revision, inspect the actual `testing`/`release`
branch trees, approve identities/policy/metadata, and prove a no-publication
rehearsal and receipt-preserving candidate/promotion cycle. See
[`release/MOBILE_RELEASE_KIT_MIGRATION.md`](release/MOBILE_RELEASE_KIT_MIGRATION.md).

### Remaining work and ownership

| Priority / owner | Work | Required evidence or dependency |
|---|---|---|
| P0 — Product owner + release engineer | Resolve #230: select/register genuinely owner-controlled Android/iOS identities | Authenticated Store readback; coordinated Gradle/Xcode/policy/schema/validator/workflow/Debug/Keychain/service migration and tests |
| P0 — Device QA + engineer | Resolve #6: real independent-participant join, bidirectional delivery and broadcast | Exact-candidate dated receipts or genuine automation; two/three devices, real P2pKit, no mock substitution |
| P0 — Owner + signing/Store operators | Configure upload/distribution signing, app records, teams, tracks/groups, protected credentials | Authorized Play/App Store Connect setup, fingerprints, certificates/profiles, unused version/build numbers |
| P1 — Release owner | Review protected branch trees and independent approval controls | Do not enable stale branch workflows; select the independent trusted reviewer described in the release runbook |
| P1 — Device QA | Both games on Android↔Android, iOS↔iOS, and both mixed host directions | Normal LAN, supported hotspots, three participants, simultaneous actions, rejoin, lock/background, network switches, process death, host loss, repeated sessions |
| P1 — Release QA | Repeat applicable device checks from actual Play Internal/TestFlight artifacts | Store build IDs and exact artifact/source binding; Debug/simulator receipts are insufficient |
| P1 — Accessibility/design QA | TalkBack/VoiceOver, EN/AR, 200% text, RTL gestures, contrast, motion, keyboard and small/landscape layouts | Physical recordings/checklists; structural semantics tests alone are insufficient |
| P1 — Owner/legal/content | Privacy/support URLs, Data Safety, Apple privacy/export, ratings, content rights, licenses/SBOM, metadata and fictional screenshots | Approved answers and signed-artifact inspection, not agent-invented declarations |
| P2 — Maintainer | Keep handoff/contracts/inventory accurate; reconcile the domain-import wording noted in section 5 if changing that boundary | Source-and-test-backed decision; no large refactor merely to match a slogan |

The source-level identity migration in #230 is still real engineering work,
but cannot safely begin with an invented identifier. It is not accurate to say
“everything left is just clicking Store buttons.” Similarly, #6 is a missing
real-transport proof, not proof that LAN functionality is defective or proven.

Physical matrices are defined in [`P2P_MANUAL_TEST.md`](P2P_MANUAL_TEST.md), with
status in [`P2P_REMEDIATION_STATUS.md`](P2P_REMEDIATION_STATUS.md). Local prior
Apple evidence used Xcode 26.5 / 17F42; it does not replace a signed Store
archive under policy-qualified Xcode 26.3 / 17C529. CI's pinned-toolchain
simulator/linkage success still does not prove signed-device distribution.

There is no honest overall completion percentage without a defined gate
ledger. Code review/CI progress is strong; Store readiness remains blocked.

## 19. Instructions for the next agent

1. Read root `AGENTS.md`, this handoff, active architecture/rules, and the
   relevant implementation/tests before proposing changes. Refresh Git/GitHub
   state; all status in this report is date-bound.
2. Preserve user modifications, untracked folders, stashes, and unrelated
   historical branches. Do not read private signing material or real player data.
3. Scope work to the user's current request. A handoff does not authorize Store
   publication, broad refactoring, a new backend, or speculative bug fixes.
4. For an issue, read its entire body/comments and related commits; reproduce
   or independently verify. Classify confirmed/already-fixed/partial/intended/
   obsolete/doc-drift/release-only/false-positive with evidence.
5. Use one focused issue branch from the latest correct reviewed base, or an
   isolated worktree when preserving unrelated work requires it. Never combine
   unrelated fixes or silently incorporate user changes.
6. Preserve host-only reducer authority, exact protocol 4.2 compatibility,
   deterministic rules, identity ownership, and public/private projections.
   Changes to game rules/content/versions require explicit compatibility review.
7. Add deterministic reducer/session/codec/projection/snapshot tests. Include
   malformed input, boundary counts, rapid/repeated actions, cancellation,
   stale callbacks, disconnect/rejoin and both game/topology implications.
8. Follow Kotlin official style, four-space indentation, existing naming and
   module conventions. Prefer existing components/icons/tokens; update EN/AR
   resources together. Conventional Commits (`fix(session): ...`, etc.).
9. Run focused checks, preserve results, **stop Gradle and clean outputs after
   every cycle**. Do not weaken tests, verification, privacy, or release gates.
10. Before integration, independently review the full diff again; reread the
    issue/comments, root cause, sibling paths, privacy, seed handling, protocol,
    save/rejoin compatibility, race conditions and both games. Rerun relevant tests.
11. Integrate only after that review, rerun combined checks, verify the tree
    differs only by intended/pre-existing work, and delete only your completed
    temporary branch when safe. Close an issue only with adequate evidence.
12. Report PASS/FAIL/BLOCKED/NOT APPLICABLE truthfully. Unexecuted physical,
    signing, legal, and Store gates must remain explicit. Do not declare READY
    solely because builds/tests pass.

**Shortest useful mental model:** Parlor is a registry-driven Compose shell
around two deterministic game modules. The local device or LAN host owns the
canonical reducer. Peers own only validated public-plus-own-private projections.
Local game saves, live room ownership, navigation state, and peer rejoin
credentials are four different lifetimes. Preserve those distinctions, and
keep every release claim tied to actual evidence.
