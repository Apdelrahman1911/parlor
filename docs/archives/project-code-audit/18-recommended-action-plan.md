# Recommended action plan

Do not implement from this file unless explicitly authorized. Verification
methods assume later implementation work.

---

## 1. Immediate critical fixes

### A1 — Unlock Store identity (F-001)
- **Priority:** P0 · **Scope:** Gradle, xcconfig, pbxproj, release-policy,
  schema, validate_*.sh, CI string compares, `verifyApplicationIdentities`
- **Rationale:** Nothing can be uploaded.
- **Deps:** Owner-controlled Play package + Apple bundle IDs (external).
- **Verify:** Identities task + artifact validators accept the new IDs;
  `release_tool` no longer special-cases the old collision; Debug stays
  `${store}.debug`.
- **Accept:** Authenticated Store readback shows the IDs are unused/owned.

### A2 — Align Elimination `OpenVote` with validator (F-003)
- **Priority:** P0 for shipping Elimination · **Scope:** `WhodunitReducer.openVote`
- **Rationale:** Unsavable Collecting or skipped discussion.
- **Verify:** desktopTest: pre-clue OpenVote no-op + `requireValid`; post-clue
  pre-timer either no-op or documented + persistable.
- **Accept:** No reducer step in Elimination can produce a state
  `requireValid` rejects.

---

## 2. Release blockers (process / evidence)

### A3 — Keep promotion workflows fail-closed until A1 (F-002)
- **Priority:** P0 process · **Scope:** GitHub environments / disable switch
- **Verify:** `workflow_dispatch` cannot `--execute` without identity approval.
- **Accept:** Documented owner control; YAML or GitHub disable is explicit.

### A4 — Physical LAN matrix (F-007)
- **Priority:** P0 if MP ships · **Scope:** 2–3 devices, both host directions,
  Android↔iOS, hotspot, background, resume, leave
- **Verify:** Dated receipts; optionally un-Ignore loopback tests in a device job.
- **Accept:** Join + actor stamp + private isolation observed on radio.

### A5 — Signing + store forms
- **Priority:** P0 after A1 · **Scope:** keystore, Apple team/profile, privacy
- **Verify:** `productionAndroidSigningCheck`; signed IPA validator.
- **Accept:** Artifacts install from internal tracks.

---

## 3. High-priority correctness and security

| ID | Action | Verify |
|---|---|---|
| A6 | Implement or un-claim Mafia disconnect pause (F-004) | Reducer test: HostOnly gameplay rejected while disconnected **or** kdoc+tests match stall-only |
| A7 | Random session ids; seed not in filename (F-005) | Snapshot list has no hex seed; resume still works |
| A8 | Fix `killerWins(SurvivedToFinalTwo)` shape (F-019) | `requireValid` after helper path |
| A9 | After every rule-test step call `requireValid` (F-018) | Illegal fixtures fail |
| A10 | Host mailbox isolation/timeout (F-006) | Unit: control work progresses when apply is slow |
| A11 | Relocate or internalize `KtorRemoteCaseDataSource` (F-010) | No ktor-client-core on shipping content unless product requires HTTPS |

---

## 4. Architecture

| ID | Action | Accept |
|---|---|---|
| A12 | Move Whodunit host/join/case-picker into `:game-modes:whodunit` (F-011) | composeApp `shell/game/whodunit` gone; verifyGameShellDispatch still green |
| A13 | Delete or implement `GameSession` / `TimerService`; drop unused transport→session dep (F-022) | Grep clean |
| A14 | Remove leftover `shared/navigation/` (F-023) | Directory gone |
| A15 | Split `P2pKitRoomTransport` along discovery / admission / session / diagnostics (F-021) | Files reviewable; behavior tests unchanged |

---

## 5. Performance

| ID | Action | Accept |
|---|---|---|
| A16 | Profile snapshot encode + 16-player Mafia on mid-range phones | Budget documented; no main-thread I/O |
| A17 | Measure discovery battery | Idle discovery not spinning |

Needs devices. Do not “optimize” without traces.

---

## 6. Test gaps

| ID | Action | Accept |
|---|---|---|
| A18 | Android instrumented + at least one Xcode testable (F-008) | CI or documented device job |
| A19 | Mafia `commonTest` or Native execution of reducer suite | Not desktop-only |
| A20 | Exhaustive `MafiaActionAuthority` matrix | Matches Whodunit table |
| A21 | Stop treating doc-grep as product tests (F-028) | Move to a `docsContract` task or drop |
| A22 | Add `compileKotlinDesktop` to `productionDesktopCheck` if the description stays (F-009) | Description matches deps |

---

## 7. Platform / device validation

Still required and **not** substitutable by this audit:

- TalkBack + VoiceOver EN/AR, 200% text, reduced motion, RTL (F-014/015/013)
- iOS Local Network deny → Settings recovery
- Backup/restore / Quick Start (SP-010)
- Hotspot topologies
- Signed-artifact dependency/network inspection (no unexpected upload)

---

## 8. Maintainability

- Replace lying kdocs (Mafia pause, droppedPlayers).
- Drop dead `PrivacyConcernRaised` / always-null `rejoinToken` (F-025).
- Drop or implement Mafia timer fields (F-024).
- Hide commonMain test fakes (F-035).
- Reconcile dirty MOBILE_RELEASE signing aliases with HEAD (F-032) — commit or revert, don’t leave half-migrated.

---

## 9. Optional future

- Language filter/chip on case picker (F-012) — should be default if mixed
  EN/AR cases ship.
- Remove Solo card instead of showing it disabled (F-020).
- iOS/Desktop predictive/Escape back (F-013).
- Enable Gradle signature verification (F-027) when the team will maintain it.
- Third game using Mafia’s “lobby in game module” pattern only.

---

## Suggested sequence

1. A2 (correctness, no external ID needed)
2. A6–A9 (same)
3. A4 device LAN (can start now)
4. A1 + A3 + A5 when owner has Store IDs
5. A12–A15 as follow-up, not on the ship path
