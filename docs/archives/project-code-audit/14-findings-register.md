# Findings register

Canonical, deduplicated by root cause. Workstream IDs in parentheses.
Severity: Critical / High / Medium / Low / Informational.
Confidence: Confirmed / High / Needs runtime.

Release block = cannot ship a Store binary or would ship a known
game-breaking hole without an explicit product accept.

---

### F-001 — Store identity `com.parlor.app` is mandatory and unlistable
- **Severity:** Critical · **Confidence:** High · **Category:** Release / identity
- **Platforms:** Android, iOS · **Blocks release:** Yes
- **Files:** `composeApp/build.gradle.kts` (`applicationId`, `verifyApplicationIdentities`);
  `iosApp/Configuration/Config.xcconfig` (`BUNDLE_ID`); `config/release-policy.json`
  (`store_identity_ownership.status = blocked`); `scripts/release/release_tool.py`;
  `validate_android_artifact.sh`; CI string compares
- **Evidence:** Gate fails if ID changes; policy/tool fail if ID is used for Store.
- **Current:** Every executable pin requires a colliding ID; every Store path refuses it.
- **Expected:** Owner-controlled unique Store IDs, Debug derived with `.debug`.
- **Root cause:** Provisional ID locked into verification before collision discovered.
- **Impact:** First production upload is impossible.
- **Repro:** `verifyApplicationIdentities` vs `release_tool.py` identity assert.
- **Tests detect?** Yes — they *enforce* the deadlock (TB-013).
- **Fix:** Coordinated migration of Gradle, xcconfig, policy, schema, validators, CI.
- **Related:** F-002, TB-001, TB-013
- **Workstream:** TB-001, TB-013

### F-002 — Candidate/promotion workflows are live YAML, not stubs
- **Severity:** High · **Confidence:** High · **Category:** Release process
- **Platforms:** CI · **Blocks release:** Process risk (mutation currently fail-closed on F-001)
- **Files:** `.github/workflows/testing-candidate.yml` (`workflow_dispatch` + `publish`);
  `testing-external-promotion.yml`; `production-promotion.yml`
- **Evidence:** No `if: false`. `--execute` when `PUBLISH=true`.
- **Current:** Anyone with environment approval could publish after an identity change.
- **Expected:** Disabled or fail-closed until identity + reviewer gates exist.
- **Root cause:** Automation landed before Store identity was viable.
- **Impact:** Accidental publish surface; rehearsal still signs if secrets exist.
- **Tests detect?** Workflow contract requires the four files to exist.
- **Fix:** Keep identity lock; do not re-enable until F-001 + branch protection complete.
- **Related:** F-001 · **Workstream:** TB-002

### F-003 — Elimination `OpenVote` can skip discussion or create unsavable state
- **Severity:** High · **Confidence:** High · **Category:** Game correctness
- **Platforms:** All (reducer) · **Blocks release:** Yes if Elimination is shipping
- **Files:** `WhodunitReducer.openVote`; `advanceFromDiscussion`;
  `WhodunitStateValidator.validatePhaseShape`
- **Evidence:** Lead read `:614-626` vs `:587-609` vs validator `:671-675`.
  Pre-clue OpenVote → Collecting fails `requireValid`. Post-clue pre-timer
  OpenVote skips discussion and is persistable.
- **Current vs expected:** Discussion is mandatory on the timer path; OpenVote is wider.
- **Root cause:** Guard not aligned with discussion entry or validator.
- **Impact:** Host/UI shortcut or queued action can skip evidence or brick resume.
- **Repro:** 5p Elimination, AssignRoles → Round(1) no clue → OpenVote → requireValid throws.
- **Tests detect?** No.
- **Fix:** Gate OpenVote like `advanceFromDiscussion`. Tests: pre-clue no-op; decide
  post-clue pre-timer policy.
- **Related:** GR-010 · **Workstream:** GR-001

### F-004 — Mafia kdoc/tests imply disconnect pause; reducer does not pause
- **Severity:** Medium · **Confidence:** High · **Category:** Correctness / authority
- **Platforms:** Multi-device · **Blocks release:** Product decision
- **Files:** `MafiaState.kt` kdoc `:43-47`; `MafiaReducer.markDisconnected` `:827-843`
- **Evidence:** Only set-add. No paused flag. HostOnly actions still apply if other
  gates pass. Whodunit does pause.
- **Impact:** Host can end/advance while a living seat is gone if readiness already met.
- **Tests detect?** Membership only.
- **Fix:** Implement pause or delete kdoc and test stall-on-ack-only contract.
- **Workstream:** GR-003

### F-005 — Pass-and-play snapshot filenames embed the CSPRNG gameplay seed
- **Severity:** Medium · **Confidence:** Confirmed · **Category:** Privacy
- **Platforms:** Android, iOS, Desktop (PaP) · **Blocks release:** No (defense-in-depth)
- **Files:** `WhodunitGameFlow.kt` `SessionId("local-${seed.toString(16)}")`;
  `MafiaGameFlow.kt` `mafia-local-…`; `FileBackedSnapshotStore.fileName`
- **Evidence:** Same seed is `hostOnly.randomSeed` and determines hidden roles.
  Filename is not AEAD-protected.
- **Impact:** Directory listing (adb/forensics/backup regression) yields the seed.
- **Fix:** Random `id128()` session ids; keep seed inside ciphertext only.
- **Workstream:** SP-001

### F-006 — Host coordinator mailbox capacity 8 with blocking inbound collect
- **Severity:** Medium · **Confidence:** High · **Category:** Concurrency
- **Platforms:** Multi-device · **Blocks release:** Needs runtime for user impact
- **Files:** `AuthoritativeSessionCoordinator` `HOST_MAILBOX_CAPACITY = 8`;
  `mailbox.send` in incoming collect
- **Evidence:** Lead read `:173`, `:212-216`, `:1268`.
- **Impact:** Bursty peer or slow snapshot encode can stall heartbeats/start deadlines.
- **Fix:** Larger mailbox and/or `trySend` + timeout; isolate control vs game work.
- **Workstream:** SN-001, SN-007, PR-001

### F-007 — Physical P2pKit join/actor-stamp/broadcast never run in CI
- **Severity:** High · **Confidence:** Confirmed · **Category:** Test / networking
- **Platforms:** Android, iOS · **Blocks release:** External gate (device)
- **Files:** `P2pKitRoomTransportLoopbackTest` three `@Ignore`
- **Impact:** Product invariant (LAN multiplayer) is unproven on radio.
- **Fix:** Device lab / manual matrix; do not treat desktopTest as LAN proof.
- **Workstream:** SN-003, TB-006, SP-008

### F-008 — Zero instrumented Android tests; empty Xcode Testables
- **Severity:** High · **Confidence:** Confirmed · **Category:** Test matrix
- **Platforms:** Android, iOS · **Blocks release:** External (device UX/permissions)
- **Evidence:** glob 0 instrumented; `iosApp.xcscheme` Testables empty; 1 iosTest file.
- **Workstream:** TB-005

### F-009 — `productionDesktopCheck` description claims desktop compile; it only runs desktopTest
- **Severity:** Low · **Confidence:** High · **Category:** Build honesty
- **Files:** `build.gradle.kts` `:27-30` vs `:193-195`
- **Workstream:** TB-003

### F-010 — Public Ktor remote case client on shipping classpath
- **Severity:** Medium · **Confidence:** High · **Category:** Architecture / supply
- **Files:** `KtorRemoteCaseDataSource`; `content/build.gradle.kts` ktor-client-core;
  `ContentModule` binds `OfflineRemoteCaseDataSource`
- **Impact:** One Koin edit enables unreviewed HTTPS. Not reachable today.
- **Fix:** Move adapter off commonMain or `internal` + no ktor on shipping until needed.
- **Workstream:** AR-003, ST-002, SP-002

### F-011 — Whodunit lobby/case-picker lives in composeApp
- **Severity:** Medium · **Confidence:** High · **Category:** Architecture
- **Files:** `composeApp/.../shell/game/whodunit/*` vs Mafia module lobby
- **Impact:** Third game copying Whodunit grows the shell. Gate does not scan `shell/game/**`.
- **Workstream:** AR-005

### F-012 — Case picker mixes EN/AR with no language filter
- **Severity:** Medium · **Confidence:** High · **Category:** UX / loc
- **Files:** `WhodunitCasePickerScreen`; case JSON `language` field unused in UI
- **Workstream:** UI-004

### F-013 — iOS/Desktop platform Back is a no-op
- **Severity:** Medium · **Confidence:** High · **Category:** UX / navigation
- **Files:** `PlatformBackHandler.ios.kt`, `.desktop.kt`
- **Workstream:** UI-008

### F-014 — Unlabeled pressables and pass-and-play covers
- **Severity:** Medium · **Confidence:** High (AT wording Needs runtime)
- **Files:** `Pressable.kt`; `CandlelitCover.kt`; `MafiaCover.kt`
- **Workstream:** UI-005, UI-006

### F-015 — Several fullscreen cards are not scrollable
- **Severity:** Medium · **Confidence:** High · **Category:** A11y / large text
- **Files:** `RoundTitleCardScreen`, `ClueRevealScreen`; incomplete contract allowlist
- **Workstream:** UI-007

### F-016 — iOS settings writes are not durability-checked
- **Severity:** Medium · **Confidence:** High · **Category:** Storage
- **Files:** `IosSettingsKeyValueBacking.kt`
- **Workstream:** ST-003

### F-017 — SnapshotStore AEAD is convention, not type-enforced
- **Severity:** Medium · **Confidence:** High · **Category:** Storage
- **Files:** `SnapshotStore.kt` kdoc vs `FileBackedSnapshotStore` plaintext JSON
- **Workstream:** ST-001

### F-018 — Tests prove rules on validator-illegal fixtures
- **Severity:** Medium · **Confidence:** High · **Category:** Test quality
- **Files:** listed in GR-010 (CluePolicyTest, ProductionGuards, Mafia edge winner plant)
- **Impact:** F-003/F-004 can survive CI.
- **Workstream:** GR-010

### F-019 — `killerWins(SurvivedToFinalTwo)` writes illegal Resolved
- **Severity:** Medium · **Confidence:** High · **Category:** Correctness (latent)
- **Files:** `WhodunitReducer.killerWins` vs `validateCanonicalKillerWin`
- **Blocks release:** No on current legal live paths
- **Workstream:** GR-002

### F-020 — Disabled Solo card always visible
- **Severity:** Medium · **Confidence:** High · **Category:** UX
- **Workstream:** UI-001, PF-001

### F-021 — God objects at host-authoritative boundary
- **Severity:** Medium · **Confidence:** High · **Category:** Maintainability
- **Files:** `P2pKitRoomTransport.kt` ~4589 loc; coordinator ~1936; process owner ~1034
- **Workstream:** AR-006

### F-022 — Unused engine `GameSession` / `TimerService`; unused transport→session Gradle edge
- **Severity:** Low · **Confidence:** High · **Category:** Architecture hygiene
- **Workstream:** AR-001, AR-002

### F-023 — Leftover `shared/navigation/` not in settings
- **Severity:** Low · **Confidence:** High
- **Workstream:** AR-007

### F-024 — Mafia timers modeled and rejected
- **Severity:** Low · **Confidence:** High
- **Workstream:** GR-006

### F-025 — Dead `PrivacyConcernRaised` event; null `rejoinToken` API
- **Severity:** Low · **Confidence:** High
- **Workstream:** GR-005, SN-011

### F-026 — Admission limiter before room-code; WrongCode oracle
- **Severity:** Low · **Confidence:** High · **Category:** Security (mitigated)
- **Workstream:** SN-006, SP-006

### F-027 — Dependency verification does not verify signatures
- **Severity:** Low · **Confidence:** High
- **Workstream:** SP-007, TB-008

### F-028 — Doc-parsing tests in desktopTest
- **Severity:** Low · **Confidence:** High
- **Workstream:** TB-007

### F-029 — Android launcher name not localized; `uppercase()` default locale
- **Severity:** Low · **Confidence:** High
- **Workstream:** UI-002, UI-003

### F-030 — Desktop snapshot key is same-uid file; credentials RAM-only
- **Severity:** Low · **Confidence:** High · Desktop non-shipping
- **Workstream:** SP-004, ST-005

### F-031 — `productionCheck` is not a ship gate (naming)
- **Severity:** Informational · **Confidence:** High
- **Workstream:** TB-009

### F-032 — Dirty working tree changes signing input aliases
- **Severity:** Low · **Confidence:** High · Local vs origin
- **Workstream:** TB-011

### F-033 — Peer inbound Channel(8) can stall host sends
- **Severity:** Low · **Confidence:** High · Needs runtime
- **Workstream:** SN-004

### F-034 — connectionEpoch never rotates
- **Severity:** Low · **Confidence:** High
- **Workstream:** SN-005

### F-035 — Public test fakes in commonMain
- **Severity:** Low · **Confidence:** High
- **Workstream:** AR-004

---

## Explicitly not findings (verified)

- Engine purity; shared ↛ games; only transport-p2p imports P2pKit.
- Production DI offline content; MockEngine tests-only.
- RoundRobin fixture cannot enter production catalog via current Koin.
- Peer HostOnly rejection (Whodunit unit matrix).
- Exact 4.2 compatibility.
- CancellationException preserved on inspected paths.
- Bundled catalog 1:1 with seven JSON files.
- No confirmed host-only / other-player leak on encode+validator path.
- No Nearby/Location permission; no cleartext traffic; backup denied in XML.
