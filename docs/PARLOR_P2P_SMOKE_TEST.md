# Parlor — Multiplayer (P2P) Physical Smoke-Test Matrix

> **Purpose:** validate Parlor's multi-device gameplay on real hardware after the
> 2026-06-13 audit-fix pass. Every row carries an explicit **pass/fail** criterion
> and the **single piece of evidence** to capture, so a later audit can confirm the
> row was actually run, not just claimed. Rows tagged **[FIX]** validate a specific
> change from this pass — those are the highest priority.
>
> The pass-and-play (single-device) path is unaffected by these changes and is not
> covered here; this matrix is multi-device only.

## Prerequisites

1. **Build with P2P on.** P2pKit `0.7.0-rc2` resolves from Maven Central:
   ```bash
   ./gradlew :composeApp:installDebug  # Android; or :composeApp:run for desktop
   ```
   No local P2pKit checkout or `mavenLocal()` publication is required.
2. **Devices:** at minimum **3** (1 host + 2 peers). Mix Android + Desktop + iOS where
   possible. All on the **same Wi-Fi LAN** (or the host's LocalOnlyHotspot).
3. **Logging.** The transport logs to stdout with the `[ParlorP2p]` prefix:
   - Android: `adb logcat | grep -E "ParlorP2p|System.out"`
   - Desktop: stdout of the `:composeApp:run` process
   - iOS: Console.app filtered to the app process
   Capture each device's log for the whole session — most evidence below is a
   `[ParlorP2p]` line.
4. **Roster rule:** unless a row says otherwise, everyone joins the lobby *before* the
   host taps Start. Late joins after Start are out of scope.

Legend: **[FIX]** = validates a fix from this pass · **PASS/FAIL** = objective criterion · **Evidence** = capture this.

---

## Part A — Lobby, discovery, join

### A1 — Host opens a room
| | |
|---|---|
| Steps | On device H: Home → Multiplayer → Host. Pick a game (Whodunit or Mafia). |
| Expected | A 6-char room code appears within ~3 s. Members list shows only the host. |
| PASS | Code visible; `[ParlorP2p] host: startAdvertising() returned` in H's log. |
| FAIL | No code after 10 s, or `host: FAILED`. |
| Evidence | Screenshot of the code card + the `startAdvertising()` log line. |

### A2 — Two peers discover & join
| | |
|---|---|
| Steps | On P1 and P2: Multiplayer → Join → enter H's code → enter a name → Join. |
| Expected | Each peer reaches the lobby/"waiting for host" within ~10 s. H's member list grows to 3. |
| PASS | H shows both peers by name; each peer's log has `join: matched host peer … calling connect()` then `connect() returned sessionState=Connected`. |
| FAIL | A peer sits on "connecting" past the 10 s join budget (`join: TIMEOUT`), or H never lists it. |
| Evidence | Screenshot of H's 3-member lobby + each peer's `connect() returned` line. |

### A3 — Wrong code fails cleanly
| | |
|---|---|
| Steps | On a 4th device (or P2 after leaving): Join with a made-up code. |
| Expected | A typed "couldn't find that room" failure within the join budget (~10 s), not a hang or crash. |
| PASS | `join: TIMEOUT after 10000ms`; UI shows a join error, offers retry. |
| FAIL | Indefinite spinner, or crash. |
| Evidence | Screenshot of the error + the TIMEOUT log line. |

---

## Part B — Whodunit multi-device, full game

### B1 — Start & role reveal
| | |
|---|---|
| Steps | With H + 2 peers in the lobby, H taps **Start**. Each device views its own character dossier and confirms. |
| Expected | All three transition from lobby → character reveal. The **host sees its own dossier** (not a spectator view). |
| PASS | Every device shows a private character card; the host can confirm its own role. |
| FAIL | Any device stuck on lobby/"waiting", or the host sees no private role. |
| Evidence | Screenshot of the host's own dossier. |

### B2 — Public intro → rounds → clues advance
| | |
|---|---|
| Steps | Tap through intro + rules briefing on every device; play through each round (reveal clue, discussion). |
| Expected | The game advances only after **all active players** have acknowledged each gate; peers see "waiting for host" between gates; no device desyncs. |
| PASS | All devices move phase together; round clues appear on every device. |
| FAIL | A gate never opens though everyone acknowledged (deadlock), or a device shows a different round than the others. |
| Evidence | Screenshots of the same round index on all three devices. |

### B3 — Vote, tie, and revote **[FIX]**
| | |
|---|---|
| Steps | Reach the vote. (a) First cast a **tie** (split votes evenly between two non-killers). (b) On the revote, vote in an **innocent** (a clear majority, not the killer) in an Elimination game **or** the killer in Classic. |
| Expected | The tie produces a revote screen. The revote resolving to an innocent **advances to the next round / shows the "innocent eliminated" card** and the host can tap through — it must **not** leave a blank screen. |
| PASS | After the innocent revote, the host sees the elimination announcement and **Continue advances the round**. |
| FAIL | Blank/frozen screen after the revote, no way forward (the pre-fix deadlock). |
| Evidence | Screenshot of the post-revote announcement + the next round screen after Continue. |

### B4 — All-abstain vote resolves **[FIX]**
| | |
|---|---|
| Steps | At a vote, have **every** player tap Refuse/Abstain. |
| Expected | The vote resolves: Classic → reveal (killer escapes); Elimination → next round, nobody eliminated. **No blank screen.** |
| PASS | The game reaches a reveal or the next round. |
| FAIL | Blank/frozen screen (the pre-fix `NoResolution` deadlock). |
| Evidence | Screenshot of the resolved outcome. |

### B5 — Game completes
| | |
|---|---|
| Steps | Play to a verdict (killer caught or escapes). |
| Expected | All devices show the reveal + post-game screen with the same verdict. |
| PASS | Identical verdict on all devices; post-game reached. |
| FAIL | Devices disagree on the outcome, or any hangs before the reveal. |
| Evidence | Screenshot of the reveal on all devices. |

---

## Part C — Mafia multi-device, full game **[FIX — this whole flow was previously unplayable]**

> Before this pass, Mafia multi-device deadlocked at the very first transition
> (role assignment → night) because the host never submitted the gated advances.
> Part C is the most important validation in this matrix.

### C1 — Role assignment advances to Night **[FIX]**
| | |
|---|---|
| Steps | H + 4 peers (5 total) lobby → H taps Start → each device views its role card and confirms. |
| Expected | Once **all five** have confirmed their role, the game **advances to Night 1 on its own** (the host drives `AdvanceFromRoleAssignment`). |
| PASS | All devices move from the role card to the night screen within ~2 s of the last confirm. |
| FAIL | Devices stay on "waiting for others" after everyone confirmed (the pre-fix deadlock). |
| Evidence | Screenshot of the Night screen on the host + the `[ParlorP2p]` logs showing no stall. |

### C2 — Night resolves → announcement → discussion **[FIX]**
| | |
|---|---|
| Steps | Each role submits its night action (Mafia kill, Doctor protect, Detective inspect, Civilians suspect). |
| Expected | Once all living players submit, the night **resolves on its own** → night announcement → after all acknowledge, it **opens discussion on its own**. |
| PASS | Night announcement appears; after acks, the day/discussion screen opens without any device being stuck. |
| FAIL | Game stuck on the night-submitted "waiting" screen, or on the announcement after everyone acked. |
| Evidence | Screenshot of the night announcement + the discussion screen. |

### C3 — Day vote closes & resolves **[FIX]**
| | |
|---|---|
| Steps | Host taps **Open Vote**. Every player casts a vote (the accused can abstain). |
| Expected | Once all have voted/abstained, the vote **closes on its own** → vote announcement → after acks, the next night **starts on its own**. |
| PASS | Vote tally shown; after acks, Night 2 begins. |
| FAIL | Stuck in voting after everyone voted, or stuck on the vote announcement. |
| Evidence | Screenshot of the vote announcement + Night 2. |

### C4 — Eliminated host can still resolve the night **[FIX]**
| | |
|---|---|
| Steps | Engineer the **host player** to be killed (Mafia targets the host, or the host is voted out). Continue to the next Night. |
| Expected | The host device shows "you were eliminated" but the **game still resolves each subsequent night** once the living players submit (the host no longer needs a Resolve button). |
| PASS | Nights 2+ resolve and the game proceeds to a winner with the host dead. |
| FAIL | Game freezes on a Night once the host is dead (the pre-fix dead-host deadlock). |
| Evidence | Screenshot of the host's "eliminated" screen + a later phase the game reached after it. |

### C5 — Game completes with a winner
| | |
|---|---|
| Steps | Play to a Mafia or Town win. |
| Expected | All devices reach post-game with the same winner + role reveal. |
| PASS | Identical winner on all devices. |
| FAIL | Disagreement, or a hang before post-game. |
| Evidence | Screenshot of the post-game on all devices. |

---

## Part D — Resilience & recovery

### D1 — Peer transient drop auto-recovers **[FIX]**
| | |
|---|---|
| Steps | Mid-game, take a peer **off Wi-Fi for ~10 s**, then back on. Do **not** leave the room. |
| Expected | The peer shows a brief "Reconnecting…" overlay, then **rejoins the same game in progress on its own** (no manual rejoin). The host marks it disconnected then reconnected. |
| PASS | Peer returns to the live game; host log shows `session[...] state -> Reconnecting` then a `PeerReconnected`; peer log shows `HostRestored`. |
| FAIL | Peer stuck on "Reconnecting…" forever (the pre-fix terminal HostLost), or the game restarts. |
| Evidence | The host's `PeerReconnected` log line + a screenshot of the peer back in the live game. |
| Note | This validates `ReconnectPolicy.Enabled`. The retry budget is ~30 s (10 × 3 s) after a ~30 s keep-alive detection — a blip longer than ~60 s total may exhaust it; that's expected and falls through to D3. |

### D2 — Game state SURVIVES a peer drop/return **[FIX — CC-01]**
| | |
|---|---|
| Steps | Mid-game (e.g. after roles are assigned and a round or two in), drop a peer as in D1 and let it return — **observe the host and the OTHER peer the whole time**. |
| Expected | The host and the other peer **keep their current phase, roles, and votes** throughout. The drop must **not** reset anyone to the start of the game. |
| PASS | Host + other peer never lose progress; phase/round is unchanged across the drop. |
| FAIL | The whole game silently restarts (roles re-dealt / back to intro) when the peer drops or returns (the pre-fix host-rebuild bug). |
| Evidence | Screenshot of the host on the *same* round/phase immediately before and immediately after the peer's drop. |

### D3 — Manual rejoin returns to the game **[FIX]**
| | |
|---|---|
| Steps | Mid-game, on a peer tap **Leave**, then immediately Join again with the same code + same name. |
| Expected | The rejoining peer is **placed back into the running game** (not stranded on "waiting for the host to start"). |
| PASS | Peer lands on the current game screen; host log shows `PeerReconnected` + a re-sent `SessionStarting`. |
| FAIL | Peer stuck on "waiting for the host to start" while the host believes it reconnected (the pre-fix rejoin gap). |
| Evidence | Screenshot of the rejoined peer in the live game. |

### D4 — Host leaves cleanly (no crash, no ghost room)
| | |
|---|---|
| Steps | Host taps Leave/Back. Then on a fresh device, scan for rooms. |
| Expected | Peers see a "host lost" state; the dead room **does not linger** in a new device's discovery for more than a few seconds. No crash on the host (the Leave + dispose double-teardown must be safe). |
| PASS | No crash; `[ParlorP2p] host: leave() done`; a second Leave/dispose logs `leave() ignored (already left)` (idempotency). Room gone from discovery within ~5 s. |
| FAIL | Crash/`IllegalStateException` on leave, or the room lingers >30 s. |
| Evidence | The `leave() done` + `leave() ignored (already left)` log lines. |

### D5 — Backgrounding (mobile)
| | |
|---|---|
| Steps | Mid-game, background a peer app for ~10 s, then foreground it. |
| Expected | No crash; the session either survives or recovers as in D1. |
| PASS | App returns to the game (live or recovered); no crash. |
| FAIL | Crash, or a permanently broken session. |
| Evidence | Console/logcat across the background→foreground transition. |

### D6 — Two games don't cross-wire (informational)
| | |
|---|---|
| Steps | Concurrently, host a **Mafia** room and a **Whodunit** room on two different host devices on the same LAN. A peer joins the Whodunit room. |
| Expected | The peer joins the intended (Whodunit) room. (Both games currently share one P2pKit AppId, gated only by the room-code prefix — confirm a peer can't accidentally end up in the wrong game.) |
| PASS | Peer lands in the Whodunit game; if it joins the wrong code it fails at case-load, not silently. |
| FAIL | Peer silently joins the wrong game and renders garbage. |
| Evidence | Screenshot of the peer in the correct game. |

---

## How to report results

For each row, record one line:

```
| C1 | Pixel 7 (H) + iPhone 13 (P1) + Desktop (P2), 2026-06-XX | <steps> | PASS | screenshot + log line in evidence/parlor-2026-06-XX/ | None |
```

**Release gate for multi-device:** all **[FIX]** rows (B3, B4, C1–C4, D1, D2, D3) PASS,
plus A1–A2, B1, B5, C5, D4 PASS. If any **[FIX]** row fails, capture the full
`[ParlorP2p]` log for all devices and send it back for forensic analysis — the log
prefix + the phase/state lines are designed to pinpoint exactly where progression
stopped.

## What these rows validate (fix → row map)

| Fix (this pass) | Validated by |
|---|---|
| Mafia multi-device gated-advance driver | C1, C2, C3 |
| Mafia dead-host night resolution | C4 |
| Whodunit TiedRevote innocent-elimination deadlock | B3 |
| Whodunit all-abstain `NoResolution` deadlock | B4 |
| Host-rebuild-on-membership (CC-01) | D2 |
| `ReconnectPolicy.Enabled` peer recovery | D1 |
| Manual-rejoin `SessionStarting` resend | D3 |
| `leave()` idempotency / clean teardown | D4 |
