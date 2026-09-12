# Mobile Party App — Game Design Document (v2)
## Working titles: **Parlor** (the app) / **Whodunit** (the murder mystery mode)

> **Status: historical product/design vision.** It preserves phased roadmap
> language, including multi-device play as future work and unshipped QR/invite
> options. Current multiplayer architecture and release scope are defined by
> `docs/PRODUCTION_ARCHITECTURE.md`, accepted ADRs, and
> `docs/P2P_MANUAL_TEST.md`; reducer/tests remain executable rules truth.

---

## How to Read This Document

This document is split into two layers:

- **The MVP** — the simplest version of the game that proves it's fun. The first playable release. Everything in **Parts 1, 2, and 3** is part of the MVP unless explicitly marked as deferred.
- **The Full Vision** — the long-term product the MVP grows into. Anything marked *[Post-MVP]* is documented for direction but is **not** part of the first release.

The guiding principle: **build the simplest version that proves the game is fun. Then expand.**

---

# PART 1 — Core Game Identity

## 1. Overall Game Experience

**The moment a player opens the app**, the screen is dark — a slow ember-orange glow rises from black, like candlelight in a room with the curtains drawn. A faint sound of a clock ticking, a distant piano, the soft creak of a wooden floor. The home screen is intentionally quiet and theatrical. This is not a loud, neon party app. It is a parlor. A salon. A space where everyone leans in.

The phone, in this game, becomes a **host, a storyteller, and a secret-keeper**. When it's in your hand, it whispers to you. When it's not, you become an actor at the table.

**Tone:** Cozy noir. Dramatic but never grim. The murder happens off-screen and is treated more like an Agatha Christie puzzle than a true-crime thriller. There is no gore. There are no jump scares. The mood lives somewhere between *Knives Out*, *Clue*, and a candlelit dinner where everyone has something to hide.

**Mood arc within one session:**
- **Curiosity** at setup — *"Who am I going to be? Who's the killer?"*
- **Performance** during early rounds — *"I have a story to sell."*
- **Suspicion** mid-game — *"Wait, that doesn't add up."*
- **Tension** in the final rounds — *"Someone here is lying right now."*
- **Catharsis** at the reveal — *gasps, laughter, accusations across the table.*

**Session length:** Variable, picked at setup. See Section 6.

**The core promise to the player:** *Every game is a small dinner-theater play, and you are both the audience and the cast.*

---

## 2. The Dynamic Killer System — Core Mechanic

This is the soul of the game and the one mechanic the MVP must absolutely nail. **It is not optional and not deferred.**

### The core rule
**Every character is written as if they could be guilty.** No character is a "clearly innocent" witness. Each one carries a motive, a secret, and a viable opportunity. The app's randomizer decides, at the start of each game, which one *actually* did it this time.

### The dual-brief structure
Each character has two parallel versions of their dossier, sharing the same public identity but diverging in the private section:

- **Innocent brief.** You have your motive. You have your secret. You did not act on it. You probably did something *adjacent* and embarrassing that night — visited a room you shouldn't have, took something small, lied to a partner — but you did not kill anyone. Your alibi is technically true and uncomfortably weak.
- **Guilty brief.** Same public profile, same motive, same secret — but now you *did* it. You have a method. You have a fabricated alibi. You have a specific cover-up. You know what you left behind.

When the game randomizes, exactly one player receives the guilty brief. The others receive their innocent briefs.

### The three layers of the case
1. **The Bedrock** — facts true in every playthrough. The victim is dead, the dinner happened, the body was found in the same place. Stable shared reality.
2. **The Layer of Suspicion** — every character's motive and secret. Always present. *Everyone* always looks guilty for the same reasons.
3. **The Truth** — what *actually* happened: who, how, why. Generated fresh each game from a small set of pre-written alternates, one per possible killer.

### Why this works for replayability
Because every character is *already* suspicious by design, you cannot infer the killer just from the dossiers — the killer doesn't *look* different from the innocents. The killer just *is* different in a single hidden way.

### Avoiding the "obvious killer" problem
- **Equalize public motive.** Every character has a strong, specific reason. The lawyer's motive is as sharp as the wife's.
- **Spread clues evenly.** Across a game, every character has a similar number of suspicious clues land near them, regardless of guilt.
- **Red herrings shift with the killer.** When character A is guilty, suspicion is steered toward character C; when character B is guilty, toward character D. (See Section 11.)
- **Equalize screen time.** Each character is named by clues a similar number of times.

### How innocent characters still look suspicious
Every innocent player has:
- A **secret action** that night that looks bad if exposed.
- A **lie they must tell** in Round 1 about *something* (usually their secret, not the murder).
- A **moment of opportunity** the app surfaces later that *could* implicate them.

So in any round, when a clue lands, multiple players can plausibly squirm.

### How the killer's brief differs
Beyond "you are the killer," the guilty brief gives:
- The **method**.
- A **timeline** of the killer's movements.
- A **fake alibi** to rehearse.
- A **deflection target** — one or two suspects to gently steer blame toward.
- A **panic move** — what to say if directly accused.

The killer is essentially handed a small acting script. The fun, for the killer, is *performing* it.

---

## 3. Theme Flexibility

The structure of the game is theme-agnostic. The Dynamic Killer System works regardless of setting. The MVP ships with one case in a country-manor style (*The Last Dinner*), but the system is built so that future cases can use any of these settings — and the visual and narrative tone of the app should not be so locked to Victorian England that other themes feel out of place.

### Supported case shapes
A "case" in this system requires:
- A bounded location (a place no one can leave).
- A single victim.
- 3 to 6 suspects (the system scales).
- A method that's easy to describe in one sentence.
- A reason the police aren't here yet.

That shape can re-skin into:
- A family villa.
- A business dinner at a private club.
- A wedding night at a lakeside resort.
- A company retreat in the mountains.
- A private birthday party.
- A school or college reunion.
- A boutique hotel during a storm.

### Slot-based character archetypes
Future cases can fill the same six character "slots" with different roles, keeping the gameplay structure identical:

| Slot type | Country-manor flavor | Modern flavor | Alternative flavor |
|---|---|---|---|
| Intimate partner | Wife/husband | Spouse / fiancé | Long-time partner |
| Family rival | Estranged son | Sibling | Adopted child |
| Professional advisor | Family lawyer | Accountant | Personal manager |
| Business adversary | Business partner | Co-founder | Investor |
| Long-serving insider | Housekeeper | Personal assistant | Childhood friend |
| Trusted expert | Family doctor | Therapist | Spiritual advisor |

This is also where future cases can localize: an Egyptian family wedding, a Lebanese business dinner, a school reunion in any city. The system does not care.

For the MVP, we ship one case in one setting. The flexibility is documented so that the design choices we make today don't paint future cases into a corner.

---

# PART 2 — The MVP

## 4. MVP Scope

### What's in the MVP
- **One case only:** *The Last Dinner*, simplified.
- **Two game modes:** Classic Vote Mode and Elimination Mode. **Both modes share the same case, characters, dossiers, and clue pool** — Elimination Mode is a different *pacing* of the same content, not a separate game.
- **Classic Vote Mode supports 4–6 players.**
- **Elimination Mode supports 5–6 players** (4-player Elimination is deferred — the game resolves too quickly).
- **One session length:** Classic — 25–35 min for Classic Vote, 15–25 min for Elimination (which ends on killer elimination).
- **One difficulty:** Medium.
- **Random killer every game** (the Dynamic Killer System, in full).
- **Character reveal flow** with the dual-section dossier (Must Read + Optional Details).
- **Private Review Mode** so players can safely revisit their role mid-game.
- **Round structure adapted to player count** (3 rounds for 4 players, 4 rounds for 5–6 players).
- **Public clues only** in the MVP.
- **Voting and dramatic final reveal.**
- **Replay** with a new randomly assigned killer.
- **Basic UX safety rules:** pause, player-leaves-mid-game, refusal-to-vote, accidental role exposure.
- **API-driven case delivery.** *The Last Dinner* is fetched from a backend, not hardcoded — with one bundled fallback case shipped inside the app for offline safety. See Section 23 for the full architecture.

### What's deferred to Post-MVP
- **3-player support.**
- **Easy and Hard difficulty.**
- **Quick Mode** (the 15–20 min variant).
- **Full Mystery Mode** (the 35–45 min variant).
- **4-player Elimination Mode.**
- **Voiceover narration.**
- **More cases.**
- **Heavier private-clue mechanic.**
- **Unlockable case variations** ("What if the victim survived," accomplice mode, "killer doesn't know they're the killer").
- **Complex case branching.**
- **Additional game modes** beyond Classic Vote and Elimination.

### Why this scope
The MVP exists to answer one question: *is this game fun?* Everything that doesn't help answer that question is deferred. Holding Elimination Mode in scope is a deliberate exception — it's a core part of the product direction — but it's scoped tightly: same content as Classic Vote, just paced differently and locked to 5–6 players.

The shipping bar: learn the game in under two minutes, finish in under 35 minutes, want to play again immediately.

---

## 5. Game Modes — Two Choices

The MVP supports two voting modes. The player chooses one before each game. The app explains the difference clearly on a mode-selection screen.

### Classic Vote Mode
*"Investigate the full case, discuss every clue, then vote once at the end. Best for story and deduction."*

One investigation, one final vote, one dramatic reveal. The classic Agatha-Christie experience. See Section 12 for full detail.

### Elimination Mode
*"Vote after every round. Eliminate suspects one by one. Find the killer before they survive to the end. Best for fast and tense games."*

After every round, the room votes to eliminate one suspect, and the app immediately reveals whether they were innocent or the killer. The killer wins by surviving. See Section 13 for full detail.

### Mode selection screen
The player sees two large cards on the screen:

```
              CHOOSE GAME MODE

  ┌─────────────────────┐  ┌─────────────────────┐
  │  CLASSIC VOTE MODE  │  │  ELIMINATION MODE   │
  │                     │  │                     │
  │ Investigate the     │  │ Vote after every    │
  │ full case. One vote │  │ round. Find the     │
  │ at the end. Best    │  │ killer before they  │
  │ for story and       │  │ survive. Best for   │
  │ deduction.          │  │ fast, tense games.  │
  │                     │  │                     │
  │     25–35 min       │  │     15–25 min       │
  │     4–6 players     │  │     5–6 players     │
  └─────────────────────┘  └─────────────────────┘
```

Elimination Mode is **not** available for 3-player or 4-player games in the MVP. 3-player support is deferred entirely; 4-player Elimination is deferred until 5–6 player pacing is tuned (see Section 22).

---

## 6. Session Length

The MVP ships with one session length: **Classic Mode**. Other lengths are deferred to Post-MVP.

### Classic Mode (MVP)
- **Classic Vote Mode:** 25–35 minutes total.
- **Elimination Mode:** 15–25 minutes total. The game ends when the killer is eliminated, so timing varies by how quickly the room finds them.
- Full round structure for the chosen player count, balanced clue pacing.

### Quick Mode *[Post-MVP]*
- 15–20 minutes total.
- Fewer rounds, faster clue reveals, tighter discussion timers.
- Designed for: new players, casual groups, families, late-night games.

### Full Mystery Mode *[Post-MVP]*
- 35–45 minutes total.
- Longer dossiers, more clues, more dramatic pacing, more private information.
- Designed for: experienced groups, mystery enthusiasts, themed evenings.

Why ship only Classic Mode in the MVP: testing one length first lets the team learn how long the game actually feels with real players before fragmenting the experience. Quick Mode in particular has aggressive timer requirements (~2-minute discussion rounds) that need real-player data to tune safely.

---

## 7. Main Menu & Setup Flow (MVP)

### Main menu

The home screen has three elements:

- **Tonight's Game** — a featured card showing *Whodunit: The Last Dinner*.
- **All Games** — a small grid. *Whodunit* is the only playable tile in the MVP. Future tiles appear faded with a "Coming soon" label to telegraph the larger app vision.
- **How to Play / Settings** — a small icon, opens a 30-second illustrated walkthrough plus settings.

Tapping the featured card or the *Whodunit* tile opens the case dossier for *The Last Dinner*:
- One paragraph teaser.
- Supported player counts.
- Estimated time.
- One large button: **Begin Investigation**.

### Setup flow

The setup is a small ritual. Each step is a single screen, never crowded.

1. **Choose game mode.** Classic Vote or Elimination. (Section 5.)
2. **Choose number of players.** Available counts depend on mode:
   - **Classic Vote Mode:** 4, 5, or 6.
   - **Elimination Mode:** 5 or 6.
3. **Enter player names.** Each player types their name. The app uses these names throughout — *"Pass to Eleanor"* not *"Pass to Player 3."*
4. **Read the public case intro.** Phone is placed face-up on the table. Everyone reads together. Tap **Continue** when ready.
5. **20-second rules briefing.** A 4-card carousel.
   - *One of you is the killer. Even you don't know who, until you see your character.*
   - *You may lie. You should lie. Especially if you're guilty.*
   - *Each round, the app reveals a new clue. Discuss. Accuse. Suspect.*
   - *Vote according to your chosen mode. Get it wrong, the killer wins.*
6. **Character handoff.** App prompts: *"Pass the phone to [first player's name]."* See Section 8.
7. **Begin Round 1.** Once everyone has been briefed, a single button: **Begin the Investigation**.

Difficulty (Medium) and session length (Classic) are fixed in the MVP and do not appear as setup steps. They become selectable in Post-MVP releases.

The MVP aims for **setup to first round in under 4 minutes**, including character reveals.

---

## 8. Character Reveal Experience (Must Read + Optional Details)

The character dossier in the MVP is deliberately **short**. New players should be able to read theirs and start playing in under 60 seconds.

### The handoff
*"[Name], take the phone. Find a quiet angle. When you're ready, press and hold to read your dossier."*

A wax-seal icon pulses softly. The player must press and hold for 1.5 seconds. This both prevents accidents and feels ceremonial.

### The dossier is split into two sections

#### A. **Must Read** (always visible, short)

This is the only section new players need to play the game. About 8–10 short lines on one screen.

- **Your name.** *"You are Eleanor Hargrove."*
- **Public identity.** One short line. *"You are Maxwell's wife of 22 years. You hosted tonight's dinner."*
- **Your relationship to the victim.** *"He is your husband."*
- **Verdict.** A single, large, dramatic line: **"You are innocent."** or **"You are the killer."**
- **Your private secret.** One line. *"You are having an affair with the gardener."*
- **Your motive.** One line. *"Maxwell was about to cut you out of the will."*
- **Your alibi.** One short line. The killer's alibi is fabricated; the innocent's is true but inconvenient.
- **Your goal.** One line.
  - Innocent: *"Find the killer. Survive the vote. Don't get accused for your secret."*
  - Killer: *"Stay hidden. Steer suspicion. Survive."*
- **What you can say freely.** One line of permission.
- **What you must hide.** One line of warning.

That's it. The whole brief fits on one phone screen.

#### B. **Optional Details** (expandable, tap to open)

Below the Must Read section is a single button: **"More about your character"**. Tapping it expands further material for players who want depth:

- Extra backstory (a paragraph).
- Acting tips for this character (how they speak, what they fidget with).
- Emotional motivation (what they're feeling underneath).
- Suggested behavior during the game.
- A few extra details about the night of the crime.

Players who want a quick game ignore this section. Players who love theater dig in.

### Hide and pass
After reading, the player taps **I'm Done**. The screen turns black with one line:
*"Hide the phone. Pass to [next player's name] when no one is watching."*

A second tap clears the cover and prompts the next player.

### Time pressure
A soft 90-second timer runs while the dossier is open. It is not a hard cutoff in the MVP — it just visually counts down. If the player needs more time, they can tap **+30 seconds** once.

---

## 9. Private Review Mode

The original design treated the dossier as read-once. **The MVP removes this restriction.** Players forget things. Pressuring them to perfectly memorize their role in 90 seconds is a bad first experience.

### Private Review Mode flow
At any point during the game, a player can review their own dossier safely:

1. Player taps their own name in the player roster on the screen.
2. The app shows a **cover screen**: *"[Name], private review. Make sure no one can see."*
3. The player presses and holds a wax-seal icon for 1.5 seconds — same gesture as the original reveal.
4. The full dossier reappears (Must Read by default, with the Optional Details collapsed).
5. When the player is done, they tap **Hide**. The screen goes completely black with a single line: *"Pass back to the table."*
6. A second tap (or any movement) returns the app to the current round screen.

### Safeguards
- **Only one player's dossier per Private Review.** A player cannot view another player's dossier.
- **The cover screen always appears first.** Pressing the player's name never directly reveals private information.
- **The hide screen always appears after review.** A player cannot accidentally hand the phone back with their dossier showing.

### When Private Review is *not* available
- During a vote (Classic or Elimination). The voting screen owns the phone.
- During a private clue delivery (Hard difficulty only). The phone is being used for asymmetric information.

### Design intent
The dossier should still feel **private and protected** — the wax seal, the cover screen, the hide screen — but it should not be **permanently inaccessible**. The game is about acting and bluffing, not about memorization.

---

## 10. Round Structure (Player-Count Adaptive)

The number of rounds **scales with player count**. This is critical — a 3-player game with 4 rounds drags; a 6-player game with 3 rounds feels rushed.

### Round structure by player count (MVP)

| Players | Rounds | Round names |
|---|---|---|
| 4 | 3 + Vote | Alibis → Motives → Contradictions → Vote |
| 5–6 | 4 + Vote | Alibis → Motives → Contradictions → Final Evidence → Vote |

(3-player support is deferred to Post-MVP and will use the structure: Alibis → Motives → Final Evidence → Vote.)

In every configuration, **the last round always carries the strongest clue** — whether that round is called "Contradictions" or "Final Evidence."

### Each round follows the same skeleton
1. The app shows a round title card.
2. The app reveals one or more clues (public by default; see Section 11).
3. Discussion timer starts.
4. The app prompts a structured action (an alibi, a question, an accusation).
5. The round ends with a transition card.

### Round 1 — Alibis (every player count)
**Tagline:** *"Where were you when it happened?"*

- One **public clue** appears on screen, usually about the time or place of death.
- Each player, in turn, gives their alibi out loud. The app calls them by name.
- 60 seconds each (Classic Mode).
- After all alibis: **3 minutes** open discussion.

### Round 2 — Motives (every player count)
**Tagline:** *"Why would anyone want them dead?"*

- One **public motive clue** is revealed — a known grievance ("The victim had changed the will this week").
- On **Hard difficulty**, one private clue is also delivered to a single player. On Easy and Medium, no private clue this round.
- **The Round of Questions:** Each player asks one direct question to another player, going around the table. The asked player must answer (and can lie).
- Discussion: **4 minutes**.

### Round 3 — Contradictions (4-player and 5–6 player games)
**Tagline:** *"Someone's story doesn't fit."*

- A **contradiction clue** is revealed — something that publicly conflicts with what one or more players said.
- On Hard, a second private clue is delivered.
- **Silent Accusation:** On the app's count of three, every player simultaneously points at the player they currently suspect most. No talking. The app does not record the result — it is purely theatrical.
- Discussion: **3–4 minutes**.

### Round 4 — Final Evidence (5–6 player games only)
*(For 3-player games, this is Round 3.)*

**Tagline:** *"One last truth before the vote."*

- The **final clue** is revealed — the strongest piece of evidence in the case.
- Each player delivers a **30-second monologue.** Defend yourself. Make your case. Accuse someone.
- The app keeps a strict timer. When time is up, the speaker is cut off with a soft chime.

### After the final round
The flow diverges based on game mode:
- **Classic Vote Mode** → go to the single final vote (Section 12).
- **Elimination Mode** → the round-by-round vote already happened after each round (Section 13).

---

## 11. Clue System (Public-First)

Clues are the lifeblood of the game. They must feel earned, fair, and dramatic. **In the MVP, most clues are public** — this keeps every player involved and the game easy to learn.

### Five categories of clue
1. **Public Universal Clues** — true in every playthrough, shown to the whole table. Bedrock evidence. *"The brandy was poisoned."*
2. **Killer-Pointing Clues** — only appear when a specific character is the killer. They don't accuse — they suggest. *"A medicine bottle was found in the kitchen pantry, half empty."*
3. **Red Herring Clues** — point suspicion at an innocent player. There is always at least one strong red herring per game, and it shifts based on who the killer is.
4. **Contradiction Clues** — clues that conflict with someone's alibi. *"You said you were in the library. Three people remember you in the hallway."*
5. **The Final Strong Clue** — the last round's clue. Never a smoking gun. Always a field-narrower.

### How clues are revealed in the MVP
- **Public clues:** appear large on the phone screen, readable from across a small table. Short, museum-label style. Everyone sees them together.
- **Private clues:** **only appear on Hard difficulty.** When they do, the app calls a specific player by name to retrieve the phone, read silently, and return it.

### Clue rules
- At least one clue per round, never zero.
- The last round's clue is always the most decisive.
- Across a full game, every player has at least one clue land *near* them.
- The killer is never the *only* character with multiple suspicious clues.
- Every clue must be **falsifiable in discussion** — a player can respond to it, contextualize it, or deny it.
- Clues never name a player as the killer — they name actions, objects, places. Players do the connecting.

### Simple, evocative clue elements
For the MVP, clues are built from simple, concrete elements that anyone can imagine:
- A poisoned drink.
- A missing medicine bottle.
- A broken glass in the wrong room.
- A burned letter in the fireplace.
- A changed will.
- A missing key.
- A threatening note in a coat pocket.
- A locked room found unlocked.
- Someone seen where they said they weren't.

No technical jargon. No legal complexity. A clue should be readable and understandable in 5 seconds.

### Clues as discussion fuel
A good clue is a question, not an answer. It should make the table ask: *"Who would have access? Who knew? Who has reason to lie about that?"* One clue should spawn five minutes of conversation.

---

## 12. Classic Vote Mode — Full Detail

### What it is
The classic investigation experience. One full case, one final vote, one dramatic reveal.

### Flow
1. Select players.
2. Enter player names.
3. Choose difficulty.
4. Read public intro.
5. Reveal private characters (Must Read + Optional Details).
6. Play all rounds (Section 10).
7. Discuss all clues across rounds.
8. **Final vote** at the end.
9. **Dramatic reveal.**
10. Offer replay with a new killer.

### The vote ritual
- The app announces: *"It is time to vote."* Screen goes dark, then lights with all player names as cards.
- **Voting is secret.** The phone is passed to each player in turn. Each player taps the name of the person they accuse. The screen clears between voters.
- Once all votes are cast, the app does not yet show the result. It builds tension with a slow card flip — each player's name appears with their vote count, lowest first.

### Win conditions
- **Players vote for the killer → players win.**
- **Players vote for an innocent → killer wins.**

### Updated tie rule (new in this version)
- A tie does **not** immediately award the win to the killer.
- Instead, the app starts a **60-second final debate between the tied suspects only.** Each tied suspect gets to defend themselves; everyone else listens.
- After the debate, players **revote, but only between the tied suspects.**
- If the second vote is still tied, **then** the killer wins.

This change rewards the room for committing to a final argument and avoids the harshness of "indecisive room loses instantly."

### The reveal — when the room is right
The accused stands. The app shows: *"[Name], the room has chosen you. Are you the killer?"* The player presses and holds the wax seal. The screen reveals: **"YES."** Then the full reveal narrative plays — how it actually happened, blow by blow. The killer gets a moment to perform their crime aloud, like a stage villain.

### The reveal — when the room is wrong
The accused stands. The screen flips: **"NO."** The app: *"An innocent has been condemned. The killer is among you, free."* Then the real killer's identity and full reveal narrative play. The real killer stands and takes a bow.

### Design goal
Classic Vote Mode should feel like a **full investigation with one dramatic final decision.** Best for:
- Story-driven groups.
- Players who enjoy discussion.
- Slower deduction.
- Dramatic final reveals.

---

## 13. Elimination Mode — Full Detail

### What it is
A faster, more tense version of the murder mystery. After every round, the room votes to eliminate a suspect. The eliminated player's role is revealed immediately. Find the killer before they survive to the end.

### Recommended player count (MVP)
- **5–6 players only** in the MVP. This is the best experience — enough suspects to give the killer real room to hide, and enough rounds for the tension to build.
- **4 players is deferred to Post-MVP.** With only four suspects, the field narrows after a single elimination, and the game can resolve in two rounds. The mode needs real-player data from 5–6 player games before we tune 4-player pacing. (Documented in Section 22.)
- **Not available for 3 players** in any release planned today.

### Round structure
Each round is the same as Classic Vote Mode's round (Alibis, Motives, Contradictions, Final Evidence — adapted to player count), but with a **vote at the end of each round** instead of only at the very end.

### Per-round flow
1. The app reveals a clue.
2. **Surviving players discuss** (timers as in Section 10). Eliminated players are **audience with rules** — they may react audibly (laugh, gasp, smile) but may not contribute strategically, hint, give signals, or coordinate with surviving players.
3. Each **surviving** suspect gets a short defense moment — 30 seconds each.
4. **Elimination vote.** Only **surviving players** vote. Each surviving player taps the name of the person they accuse this round.
5. **Immediate reveal.** The app reveals whether the eliminated player was **innocent** or **the killer**, using one of two messages:
   - If an innocent is eliminated: *"[Name] was innocent. The killer is still among you."*
   - If the killer is eliminated: *"[Name] was the killer. The investigation is over. The players win."*

### Reveal on elimination
- **If the eliminated player is the killer:** the app displays *"[Name] was the killer. The investigation is over. The players win."* The game ends immediately. The full reveal narrative plays.
- **If the eliminated player is innocent:** the app displays *"[Name] was innocent. The killer is still among you."* The game continues. The eliminated player becomes **audience with rules** (see below) — they remain at the table and may react audibly, but may no longer participate in discussion or voting.

### What the eliminated innocent player sees
- The app reveals their innocence to the whole table.
- It does **not** automatically reveal their full secret (their secret stays buried for the final reveal at game end).
- The eliminated player becomes **audience with rules**. They remain at the table and may react audibly — laugh, gasp, sigh, smile, groan — but they may not:
  - Speak strategically about the case.
  - Give hints, signals, or coded messages to surviving players.
  - Reveal hidden information they learned from their dossier.
  - Vote.
  - Influence the discussion in any direction.
- The rules are enforced socially, not by the app. The framing is theatrical: you have stepped from the cast into the audience. You watch the play finish.
- *Optional design touch:* the eliminated player's seat at the table is dimmed on the player roster.

### Killer win condition
- The game continues until either the killer is eliminated **or** the field is reduced to the **final two surviving players**.
- **The killer wins when only two players remain: the killer and one innocent.** At that point, the killer has successfully survived the investigation — there is no longer a majority to outvote them, the suspect pool is exhausted, and the killer has won.
- In other words, **the killer wins by surviving to the final two.** The room must eliminate the killer before that happens.

### Tie rule in Elimination Mode
- If a round's vote ties, each tied suspect gets a **short defense** (about 30 seconds each).
- The room then **revotes, but only between the tied suspects.**
- If the second vote is also tied, **no one is eliminated that round**, and the game proceeds to the next round with a **stronger clue.** (The app skips ahead to the next round's clue, slightly amped up.)

### Design goal
Elimination Mode should feel like a **high-pressure survival version of the same mystery.** Every round should feel dangerous because one wrong vote brings the killer closer to winning. Best for:
- Faster party sessions.
- Players who like pressure.
- Groups who want more tension.
- Replayable short games.

### Eliminated players: audience with rules
A note on social design: eliminated players will want to participate. The "audience with rules" framing accepts that — they can react, laugh, gasp, and stay emotionally engaged — but they cannot strategize, hint, vote, or influence the investigation.

The app reminds the table at the start of each post-elimination round: *"[Eliminated player] has stepped back from the investigation. They are part of the audience now. They will speak again when the truth is revealed."*

The rules cannot be enforced by the app — only by the table. Frame the role-shift as theatrical: you've moved from the cast to the audience. Strategic silence is hard to police, but reactive presence is welcomed.

---

## 14. Comparing the Two Modes

| | Classic Vote Mode | Elimination Mode |
|---|---|---|
| **When you vote** | Once, at the end | After every round |
| **What happens on a wrong vote** | Killer wins | Game continues; eliminated player is out |
| **Reveal of eliminated player** | Only at the end | Immediately, on elimination |
| **Killer win condition** | Innocent gets accused | Killer survives to the final two players |
| **Pace** | Slower, more discussion | Faster, more pressure |
| **Best for** | Story, deduction, drama | Tension, party energy, replay |
| **Player counts (MVP)** | 4–6 | 5–6 |
| **Estimated time** | 25–35 min | 15–25 min (ends when killer is found) |
| **Reveal** | One climactic moment | Multiple smaller reveals + final |

The mode selection screen explains this trade-off plainly:
- *Classic Vote Mode — investigate the full case, discuss every clue, then vote once at the end. Best for story and deduction.*
- *Elimination Mode — vote after every round. Eliminate suspects one by one. Find the killer before they survive to the end. Best for fast and tense games.*

---

## 15. Player Interaction & Safety Rules

This is a social deduction game, so the *talking* matters more than the app. The app frames the rules of conversation, then gets out of the way.

### What players are encouraged to do
- **Lie freely.** All players, innocent or guilty, are told in their dossier that lying is part of the game.
- **Hide secrets.** Even innocent players have things to hide.
- **Ask hard questions.** The app actively prompts directed questions in Round 2.
- **Watch each other.** The app reminds players: *"Pay attention to who looks comfortable, and who looks cornered."*

### The lying rule, made explicit
Every dossier tells the player exactly what they can lie about:
- **Innocent players** lie to protect their secret, not to deny the murder.
- **Killers** lie about everything related to the murder.
- **Bedrock facts cannot be denied.** The victim is dead. The dinner happened. Don't deny what everyone saw.

### Timed discussion (Classic Mode)
- Round 1 discussion: **3 minutes.**
- Round 2 discussion: **4 minutes.**
- Round 3 discussion: **3–4 minutes.**
- Round 4 monologues: **30 seconds each.**

(Quick Mode timings are deferred to Post-MVP.)

Timers are visible but soft. The final 30 seconds tick audibly. End-of-discussion is a chime, not a buzzer.

### Keeping quiet players involved
Every player gets at least one structured spotlight moment:
- The **alibi round** gives every player a guaranteed solo moment.
- The **round of questions** ensures every player is addressed directly.
- The **silent accusation** (Round 3 in 5–6 player games) requires every player to point.
- The **final monologue** (5–6 player games) gives every player 30 seconds.

If a quiet player has not been called by name in early rounds, the app gently nudges: *"[Quiet player], anything to add before we move on?"*

### Safety rules and edge cases (MVP)

Real groups will hit edge cases the core flow doesn't account for. The MVP includes basic handling for the most common ones.

#### Pause
At any point during a round, any player can long-press a small pause icon at the top of the screen. The app freezes:
- All discussion timers.
- All round-progression buttons.
- All clue-reveal animations.

A "Paused" overlay covers the rest of the screen, preserving the game state without leaking information. The phone can be safely placed on the table. Tapping "Resume" returns to exactly where the game left off.

The pause icon is **never visible** during a private dossier reveal or a vote — pausing in those moments could expose information.

#### Player leaves mid-game
If a player has to leave permanently, any player can tap "End Game" from the pause overlay. The app does not redistribute the leaving player's role — that would corrupt the case. Instead, it offers two options:
1. **Reveal the case now.** The app immediately plays the full reveal — who the killer was, what actually happened, all dossiers — and ends the game. Useful when the group can't continue but still wants the payoff.
2. **End without reveal.** The game ends cleanly. The case is preserved for a future session with a fresh setup.

A mid-game departure is intentionally not handled "gracefully" — the game cannot continue without all original players because every dossier carries information the case depends on. A clean exit is better than a broken experience.

#### Player refuses to vote
If a player declines to tap a vote, the app shows a soft prompt: *"Every voice matters. [Name], please choose someone — even if you're unsure."*

If they still refuse after 30 seconds, their vote is recorded as **abstain**. Abstentions don't count toward any total; the vote proceeds among those who did vote. If all players abstain (vanishingly rare), the round ends with no result and the app advances to the next phase or the killer reveal, as appropriate.

#### Accidental role exposure
If a player believes their dossier was seen by another player, they can tap "Privacy concern" from the Private Review screen. The app offers two options:
1. **Continue anyway.** The game proceeds. The compromised player plays on, knowing their secret is partially blown.
2. **Reroll roles.** The app reshuffles all role assignments (a new random killer is selected, dossiers are redealt) and restarts character reveals. The case bedrock and public information are preserved; only the private layer resets.

Reroll is a heavy action — the app warns: *"This will restart the case with new roles. Anyone who saw their old role is responsible for forgetting it."* Use sparingly.

#### Eliminated players (Elimination Mode)
See Section 13 for full detail. Eliminated players are reframed as **audience with rules** — they may react audibly (laugh, gasp, smile) but may not speak strategically, hint, vote, or influence the investigation. The rules are enforced socially, not by the app.

---

## 16. Difficulty (MVP: Medium Only)

The MVP ships with **one difficulty: Medium.** Easy and Hard are deferred to Post-MVP.

### Medium — the MVP balance
- **Public clues only.** Private clues are deferred along with Hard difficulty.
- **Balanced red herrings.** A red herring's pull is similar to a real clue's pull until the final round.
- The killer's dossier instructs them to gently steer suspicion toward one designated innocent target.
- Innocent players carry substantial lies to maintain — usually about their secret, not about the murder.
- The final clue narrows the field but does not name the killer.

**Feel:** The classic murder-mystery experience. Roughly half of games end in a correct accusation, depending on the group. The killer winning feels earned, not unfair.

**Why one difficulty in the MVP:** each difficulty would require its own balanced content — different clue trails, different red herring distributions, different killer instruction sets. Shipping three difficulties before validating one is premature optimization. We ship Medium, learn the real balance from real players, then expand.

### Easy *[Post-MVP]*
- Simpler clue trail; clearer killer-pointing clues.
- Mild red herrings that sow doubt but don't overwhelm.
- Killer maintains alibi but does not actively redirect blame.
- Designed for: new players, families, beginners.

### Hard *[Post-MVP]*
- Public + private clues; information becomes asymmetric.
- Strong red herrings, sometimes carrying more weight than real clues.
- Killer's dossier is more elaborate: rehearsed timeline, two deflection targets, a panic script.
- Innocent players sometimes told to commit to misleading stories.
- Final clue is more ambiguous.
- Designed for: experienced groups, mystery enthusiasts.

---

## 17. Replayability (MVP)

Even with one case, the MVP supports many distinct play experiences.

### MVP replay levers
- **Random killer.** Six possible killers in *The Last Dinner*.
- **Different clue order.** Within a round, the app draws from small pools of variant clues.
- **Different red herring target.** Each killer has 1–2 designated red herring innocents who get extra suspicious clues attached.
- **Different final clue.** Each killer has multiple variants of the last round's clue.
- **Different character pools.** With 6 characters but only 4–5 at the table, the app picks which characters are present tonight.
- **Two game modes.** Classic Vote vs Elimination — the same case feels different at different pacing.

### What this means in practice
The same case supports: 6 killer variants × 2 game modes × several character pools = **enough permutations to deliver a strong 4–8 replays per group** before the meta-knowledge problem (knowing every character's motive and secret by heart) starts to flatten the experience.

A realistic expectation: most groups will get **5–7 satisfying plays** out of *The Last Dinner* before they're ready for a new case. This sets a clear runway for the Post-MVP roadmap — **more cases is the priority replay lever** for the second release, ahead of more difficulties or session lengths.

### Replay loop
After every game, the app offers:
- **Replay this case with a new killer** *(recommended on first replays).*
- **Try the other mode.**
- **Back to main menu.**

The replay button is the **largest** button on the post-game screen. The app makes replay frictionless.

### Deferred replayability features *[Post-MVP]*
- Multiple cases.
- Easy and Hard difficulty.
- Quick Mode session length.
- 3-player support.
- Unlockable "What if" variations (accomplice mode, victim-survived mode, killer-doesn't-know mode).
- Themed case packs.

---

# PART 3 — The First Case: *The Last Dinner* (Simplified for MVP)

## 18. Case Setting, Victim, and Public Intro

### Case title
**The Last Dinner**

### Setting
Hargrove Manor, a country estate. A formal dinner held to celebrate the patriarch's 70th birthday. Six people present besides the victim. The night is rainy. The road into the estate is blocked by a fallen tree at 9:00 p.m. By morning, the body is cold and the suspects are tired.

### Victim
**Maxwell Hargrove**, 70. Wealthy. Difficult. Generous in public, cruel in private. He had been planning to change his will. He was found in his study at 10:45 p.m., slumped over his desk, with an empty brandy glass beside him.

**Cause of death:** poison in his evening brandy. The poison was added sometime between 8:30 and 9:30 p.m., while the dinner and after-dinner mingling were happening.

That's all the medical detail the players need. **No technical jargon.**

### Public introduction (read aloud at the table)

*"It was meant to be a celebration. Seventy candles on the cake, six guests at the table, and the rain coming down outside in long gray sheets. Maxwell Hargrove sat at the head of his own table, raised his glass twice, gave a toast that insulted at least two of his guests, and excused himself at 9:15 to take his evening brandy alone in the study.*

*"He was not seen alive again. At 10:45, his wife found him slumped over his desk. The glass was empty. The brandy was poisoned.*

*"The road is blocked. The phones are down. The police will not arrive until morning.*

*"Six of you are in this house. One of you killed him. And by sunrise, you have to decide who."*

The whole intro can be read in **under one minute.**

---

## 19. The Six Characters (Simplified Dossiers)

Each character has both an **innocent** and **guilty** version. The Must Read sections below show what the player sees on their phone — short and clear. The Optional Details are summarized at the end of each character.

### 1. Eleanor Hargrove — the wife

**Public identity:** Maxwell's wife of 22 years. Hosted tonight's dinner. Found the body. Once a stage actress before she married him.

**Public motive (known to all):** Maxwell was about to change the will against her.

**Private secret:** She's having an affair with the gardener, Tomas. She left the dinner briefly at 8:50 — not to fetch wine, as she said, but to meet Tomas behind the greenhouse.

**Innocent alibi:** *"I was in the greenhouse with Tomas from 8:50 to 9:25. I can't say so without exposing him."* (Lies about being in the kitchen.)

**If she is the killer:** She slipped into the kitchen pantry at 8:50 on her way to the greenhouse, added a fatal dose of Maxwell's daily medication to the brandy decanter while Clara's back was turned, and continued out to meet Tomas. Tomas is her unwitting alibi witness.

**Optional Details:** Stage-actress poise, smiles through insults, fidgets with her wedding ring. Acting tip: be elegant, never angry. Her deflection target: the lawyer.

### 2. Daniel Hargrove — the estranged son

**Public identity:** Maxwell's only son. Was disowned six years ago after a public fight. Tonight, before dinner, the two had a long private conversation in the study and emerged appearing to have reconciled. At the toast, Maxwell said "the family has a great deal to discuss in the coming weeks" — and clinked his glass against Daniel's.

**Public motive:** Complicated. On the surface, his motive has been defused — father and son made peace in front of everyone. But no one witnessed what was actually said in the study, and Maxwell's word on inheritance was famously unreliable.

**Private secret:** The "reconciliation" was largely Maxwell's performance for the room. In private, Maxwell told Daniel that any future inheritance would come with humiliating public conditions — a confession of past failures, a probation period, an apology to specific relatives. Daniel left the study smiling for the audience and furious underneath.

**Innocent alibi:** *"I was in the library from 9:00 to 9:50, reading my mother's old letters."* True. Clara passed through the hallway at 9:25 and briefly saw him through the open door. He has a partial witness — not airtight, but not damning either.

**If he is the killer:** On his way from the study to the library at 8:45, he detoured through the kitchen pantry, added a fatal dose of his father's heart medication to the brandy decanter Clara was about to carry up, and settled into the library to be seen by Clara at 9:25. The "reconciliation" earlier in the evening is his social cover — the room saw him at peace with his father.

**Optional Details:** Composed in public, contained, surprises people when he speaks. Acting tip: be quietly relieved, not angry. Volunteer that the dinner went well; mourn his father openly. Deflection target: the lawyer, whose financial motive is sharp and whose movements during the critical window are easy to question.

### 3. Vivienne Cross — the family lawyer

**Public identity:** Maxwell's lawyer for 15 years. Keeper of his will and his contracts. Drinks more than she lets on.

**Public motive:** She had been stealing money from Maxwell, quietly, for years. He had recently begun asking questions. He summoned her tonight.

**Private secret:** Maxwell knew about the theft and told her two days ago that he would "decide her fate" tonight. She doesn't know what he meant.

**Innocent alibi:** *"I was in the library, alone, from 9:10 to 9:50."* (True. No witness.)

**If she is the killer:** Maxwell sent her into the study at 8:45 to fetch a document. On the way, she stopped in the kitchen pantry, added a fatal dose of Maxwell's medication to the brandy decanter, and continued upstairs to fetch the document as expected.

**Optional Details:** Sharp, ironic, talks fast. Acting tip: be the calmest person in the room. Volunteer evidence. Deflection target: the son.

### 4. James Sutton — the business partner

**Public identity:** Maxwell's business partner for 20 years. Built the company while Maxwell played figurehead.

**Public motive:** Maxwell had been quietly negotiating to sell the company behind James's back. The sale would have ruined James.

**Private secret:** He had already gone into the study earlier in the evening (7:30, before guests arrived) to confront Maxwell. Maxwell laughed in his face.

**Innocent alibi:** *"I was in the smoking room, alone, from 9:10 to 9:40."* (True. No witness. Has the earlier confrontation to hide.)

**If he is the killer:** He left the after-dinner gathering at 8:55 "for the bathroom," detoured through the kitchen pantry to add a fatal dose of Maxwell's medication to the brandy decanter, and returned to the smoking room as if nothing had happened.

**Optional Details:** Stiff, formal, hates being interrupted. Acting tip: stay cold and businesslike. Deflection target: the doctor.

### 5. Clara Bell — the housekeeper

**Public identity:** Has worked for the Hargroves for 30 years. Practically raised Daniel. Soft-spoken. Served the dinner.

**Public motive:** Maxwell had told her two weeks ago that she was being let go without a pension. She is working her final week.

**Private secret:** She has long believed Maxwell did something terrible decades ago. She has never told anyone.

**Innocent alibi:** *"I was moving between the dining room, the kitchen, and the pantry all evening."* (True. She had access to everything. Too much access.)

**If she is the killer:** She added a fatal dose of Maxwell's daily medication to the brandy decanter while preparing it in the kitchen pantry during dinner service — entirely within her normal routine.

**Optional Details:** Invisible servant; sees everything; speaks gently. Acting tip: be helpful, never volunteer. Deflection target: the wife.

### 6. Dr. Henry Vance — the family doctor

**Public identity:** Maxwell's personal doctor for 25 years. Close family friend. He prescribed Maxwell's daily heart medication years ago — a small evening dose, kept in the kitchen pantry where Clara prepared it, taken with the after-dinner brandy. **Everyone in the household knew the routine.**

**Public motive:** Maxwell had recently discovered that Henry was overprescribing medications to wealthy patients and pocketing the difference. Maxwell threatened, twice tonight, to "settle the matter after dinner."

**Private secret:** Henry has been overprescribing for years and recently began to fear that exposure would mean prison, not just professional ruin.

**Innocent alibi:** *"I was in the smoking room from 9:15, then the conservatory."* True. Mostly alone. He spent the evening avoiding Maxwell. He has the medical knowledge — but so does anyone who has lived in the house long enough to know where the medicine cabinet is. He does not carry medicine on his person tonight.

**If he is the killer:** He entered the kitchen at 8:50 on the pretext of getting a glass of water, measured a fatal extra dose from Maxwell's medication bottle in the pantry, and added it to the brandy decanter before Clara carried it up to the study.

**Optional Details:** Nervous, sycophantic, talks too much when drunk. Acting tip: confess the overprescription scheme openly when it surfaces — bet that the honesty makes you look honest. Deflection target: Clara, who handles the medicine cabinet daily and knows the dosages exactly.

---

## 20. Example Clues

A small selection of clues that can appear in *The Last Dinner*, depending on the killer and difficulty. All are simple, concrete, and easy to discuss.

**Universal (always shown):**
- *"The brandy in the study was poisoned."*
- *"The poison was added between 8:30 and 9:30 p.m."*

**Killer-pointing or red herring (varies by game):**
- *"A medicine bottle was found in the kitchen pantry, half empty. No one admits leaving it there."*
- *"A wine glass was broken in the library. It belonged to the study's brandy set."*
- *"A burned letter was found in the study fireplace. Only one corner survived."*
- *"The study key was missing from its hook in the hallway."*
- *"A threatening note was found in Maxwell's coat pocket. It was unsigned."*
- *"The greenhouse door was unlocked at 9:00 p.m., when it should have been locked."*
- *"The brandy decanter had been wiped clean of fingerprints."*
- *"The will, kept in the study desk, had been opened. The pages were out of order."*
- *"A glass was missing from the study's brandy set."*

**Final clue examples (last round):**
- *"The fingerprint on the rim of the brandy decanter belonged to someone with a small hand."*
- *"The kitchen door creaked. At least three people heard it at 8:50."*
- *"A second set of footprints in the wet greenhouse path led back inside."*

Every clue is a **question, not an answer.**

---

## 21. Multiple Playthrough Examples

How the same case feels with different killers. Three illustrative scenarios — there are six possible.

### Playthrough A — Eleanor is the killer
**Mood:** A long-resentment crime. The wife who waited.
- **Strategy:** Lean into being the obvious suspect. Cry. Reveal the affair as the "real" secret to make pity outweigh suspicion.
- **Trail:** Maxwell's medicine bottle in the kitchen pantry half-empty. The greenhouse door unlocked twice. Her wedding ring on the kitchen counter (she took it off briefly).
- **Red herring this game:** Vivienne. Her financial motive surfaces; her unwitnessed library window looks bad; her recent "fate-deciding" conversation with Maxwell hangs over the room.
- **Final clue:** A second set of footprints in the wet greenhouse path leading back inside.
- **Likely table dynamic:** The room splits between Eleanor and Vivienne. Daniel, with his earlier public reconciliation, plays the role of grieving son and pushes hard to find the killer.

### Playthrough B — James is the killer
**Mood:** A cold, calculated business crime.
- **Strategy:** Stay cold. Refuse to engage emotionally. Point at the doctor, whose threatening-to-be-exposed motive is the strongest in the room.
- **Trail:** He was seen leaving the smoking room briefly at 8:55 "for the bathroom." Maxwell's medicine bottle in the kitchen pantry is found uncapped and half-empty. A second cufflink turns up on the kitchen pantry floor.
- **Red herring this game:** Dr. Henry Vance. He has the medical knowledge of the household, the strongest motive (Maxwell's threat), and the worst behavior at dinner.
- **Final clue:** The cufflink found in the pantry matches the pair James wore tonight.
- **Likely table dynamic:** The room splits between James and Henry. Eleanor (innocent) plays the grieving widow; Vivienne (innocent) is surprisingly sharp.

### Playthrough C — Clara is the killer
**Mood:** A quiet, slow-burning revenge. The servant no one watches.
- **Strategy:** Be invisible. Be helpful. Volunteer evidence. Gently suggest Eleanor's affair.
- **Trail:** She had access to the brandy all evening; she washed the decanter at 11 p.m. before any investigation; she knows the household medicine cabinet.
- **Red herring this game:** Eleanor. Her affair gets exposed early; her nervousness reads as guilt.
- **Final clue:** The amount of poison was precisely measured — only someone who handled the household's medicines daily could measure it that exactly.
- **Likely table dynamic:** Eleanor spends the game defending herself. Daniel defends Clara loyally, then begins to doubt. Dr. Henry plants the seed: *"She handles the medicine cabinet. She's the only one who could measure a dose."*

### Why three playthroughs feel like three different games
- **Emotional flavor changes** — passion crime, business crime, revenge crime.
- **Villain archetype changes** — tragic woman, cold tycoon, invisible servant.
- **Red herring target changes** — the innocents under suspicion are different people each game.
- **Final clue changes** — the smoking gun is a different object.
- **Reveal narrative is a different short story.**

Players who replay *The Last Dinner* are not solving the same puzzle three times. They are watching three different murder mysteries set in the same house.

---

# PART 4 — Beyond the MVP (Full Vision Roadmap)

## 22. Post-MVP Features

Once the MVP proves the game is fun, the product expands in roughly this order:

### Phase 1 — Immediate post-launch (depth on the existing case)
The first set of additions completes the experience around the launched case:
- **Easy difficulty.** Simpler clue trails, clearer killer pointers, milder red herrings. For new players, families, beginners.
- **Hard difficulty.** Public + private clues, stronger red herrings, more elaborate killer dossiers. For experienced groups and mystery enthusiasts.
- **Quick Mode.** The 15–20 minute session length with tighter rounds and faster clue reveals.
- **3-player support.** For Classic Vote Mode only, with a tightened round structure (Alibis → Motives → Final Evidence → Vote).
- **4-player Elimination Mode.** Returns to support after Elimination pacing is tuned with real-player data from 5–6 player games.

### Phase 2 — Content depth
- **More cases.** A library of cases in different settings (modern villa, business retreat, wedding, school reunion, hotel).
- **Localized character archetypes.** Cases that fit different cultural contexts.
- **Full Mystery Mode.** The longer 35–45 minute session with richer dossiers and more clues.

### Phase 3 — Mechanical depth
- **More private clues.** Heavier asymmetric information; deeper deception layer.
- **Unlockable case variations:**
  - *What if the victim survived?* — attempted-murder variant.
  - *What if there's an accomplice?* — two players are secretly working together.
  - *What if the killer doesn't know they're the killer?* — rare advanced mode; the killer believes the death was accidental until the final reveal.
- **Voiceover narration** for cases — the app reads intros and reveals aloud.

### Phase 4 — Replay depth
- **Branching cases** with different bedrocks per playthrough.
- **Save game state** for interrupted sessions.
- **Group profiles** that remember player names, preferences, and play history.

### Phase 5 — Multi-device local play (convenience layer)

A future update that adds **multi-device local play** as a convenience layer on top of the existing pass-and-play model. The core game does not change — same case, same characters, same Dynamic Killer System, same dramatic reveal. Only the device topology changes.

- **How it works.** Each player uses their own phone while sitting together in the same room. One player creates a local room as the host. Others join via room code, QR code, invite link, or local network discovery.
- **Private dossier delivery.** Each player receives their character dossier on their own device. No passing the phone around. Private Review is always one tap away, without the cover-screen ceremony.
- **Voting.** Each player votes on their own phone. Results are tallied by the host device.
- **The host device.** Controls the shared game flow: round transitions, public clue reveals, timers, vote tallies, and the final reveal.
- **Information split.** Public clues sync to every device. Private dossier content lives only on each player's own device.

**Why this is a future update and not part of the MVP:**
- It adds networking, device pairing, and shared-state management — none of which exist in the MVP.
- It introduces a new threat model (wrong-room joiners, mid-game disconnects, host device leaving) that the MVP does not need to handle.
- It removes the small ceremony of passing the phone, which is part of the cozy noir feeling. Pass-and-play should be validated with real players first.
- Pass-and-play has standalone advantages: shared focus on one screen, the phone-as-ritual-object, zero per-player setup, works for groups where not everyone has a smartphone.

**What this update does NOT change:**
- The game itself — rounds, modes, case content, Dynamic Killer System.
- The social experience — players are still in the same physical room, talking, accusing, reading each other's faces.
- The API-driven content model — cases still come from the backend.

**Status:** Post-MVP only. **Not part of the prototype scope. Adds no networking requirements to the first version.** The MVP remains pass-and-play with one phone.

### Phase 6 — Beyond Whodunit
- **Additional party game modes** in the same app (the *All Games* grid fills out).
- **Themed seasonal cases** (Halloween, holidays).
- **User-generated case mode** — a creator toolkit.

---

## 23. API-Driven Case Content

A core architectural decision for the product: **the app ships with the game engine, not the cases.** Cases are delivered from a backend API as structured content. This lets the content library expand over time without forcing app updates, while keeping the gameplay system stable and safe.

### What lives in the app

The installed app contains everything that defines *how the game works*:
- The core game engine and round system.
- The UX flow for setup, character reveal, rounds, voting, and final reveal.
- Both game modes (Classic Vote and Elimination), including voting logic, win conditions, and tie rules.
- The Dynamic Killer System — random killer assignment from the case's character pool.
- The Must Read + Optional Details dossier structure.
- Private Review Mode.
- Timers, transitions, sound, animations, and visual atmosphere.
- The Player Interaction & Safety Rules (pause, leave mid-game, refuse to vote, accidental exposure).
- The replay loop and post-game flow.
- Validation logic for incoming case content.
- One bundled fallback case for offline safety (in the MVP, this is *The Last Dinner*).

These are the parts of the product that define what Whodunit *is*. They change only with app updates.

### What comes from the API

The backend delivers **case content** — the specific stories players investigate. Each case is a structured package containing:

- **Identity:** case ID, title, version, minimum supported app version.
- **Description and intro:** the public case introduction, supported player counts, supported game modes, supported difficulties, language, theme.
- **Characters:** for each character — public identity, motive, secret, innocent brief, guilty brief, killer-specific reveal narrative, deflection target, acting tips.
- **Clue pools:** public universal clues, killer-pointing clues (per killer variant), red herring clues, contradiction clues, final clues.
- **Reveal narratives** for each possible killer.
- **Metadata:** estimated duration, theme, language, content version number, validation metadata.

The same engine renders any case that conforms to the supported schema. *The Last Dinner* and any future case are interchangeable from the app's perspective.

### Why this helps

This separation enables product moves that would be impossible with hardcoded cases:

- **New cases without app updates.** A new murder mystery is added to the library by uploading a case to the backend. Players see it next time they open the app.
- **Content balancing after launch.** If playtest data shows a clue is too obvious or a red herring too weak, the case can be patched without a release.
- **Seasonal and limited-time content.** Halloween cases, holiday specials, and themed event cases can be released and retired on schedule.
- **Localization.** A Spanish-language case, an Arabic-language case, a Japanese case — each is just another case in the library, served based on language preference.
- **A/B testing.** Multiple variants of the same case can run in parallel to learn which version of a clue or reveal narrative plays better.
- **Continuous expansion.** The product grows by content velocity, not engineering velocity.

### What the backend is allowed to control

The backend has authority over **case content and structured configuration**:

- Adding, editing, and retiring cases.
- Tuning specific clues, red herrings, reveal narratives, and dossier text.
- Setting which cases are featured, recommended, or surfaced first.
- Defining supported player counts and modes *per case*, within the engine's capabilities.
- Localized text variants per language.
- Case metadata: theme, duration estimates, difficulty availability.

### What the backend is NOT allowed to control

The backend has **no authority over game logic, UX behavior, or safety**. Specifically, the backend must never:

- Send executable code of any kind.
- Define new game rules the installed app doesn't already implement.
- Change voting logic, win conditions, timer behavior, or round structure.
- Override or bypass safety rules (pause, accidental exposure, audience-with-rules).
- Introduce gameplay modes, character types, or clue types the app version doesn't recognize.
- Reach outside the case-content schema in any way.

The app is the source of truth for *how Whodunit plays*. The backend is the source of truth for *what cases exist*. These are firm boundaries — the security and stability of the product depends on them.

**Case content boundary discipline.** As a concrete engineering rule: case content may contain *text* (titles, intros, dossier prose, clue text, reveal narratives), *character data* (names, public identities, motives, secrets, alibis), *clue data* (clue text, type tags drawn from the supported set, association with killer variants), *reveal narratives*, and *structured references to supported engine features* (mode names, difficulty names, player counts that the engine already recognizes). It may **not** contain behavioral tunables — numeric timer values, voting-rule overrides, win-condition modifications, safety-rule overrides, custom round structures, or any field that changes how the engine behaves. If a future case needs different behavior, that behavior ships as a new engine feature in an app release, not as a field in case content.

### How unsupported or invalid cases are handled

When the app fetches a case, it validates the case against its known schema **before** allowing players to start it. If a case fails validation, the app responds safely.

- **Schema version not supported.** Every case declares a `schemaVersion`. The app checks this first, before any other validation. If the schema is newer than the app understands, the case is unplayable; the app shows "Update required."
- **Case requires a newer app version.** Each case also declares a minimum supported app version. If the installed app is older, the case is either hidden from the list or shown with an "Update required" label and a soft prompt to update.
- **Case references unknown fields, clue types, modes, or rules.** The case is **not playable** on this app version. It may still appear in the list with an "Update required" state, but it cannot be started.
- **Malformed or invalid fields.** Validation is not just "is the field present." Types are checked (a `caseId` must be a string; player counts must be valid integers within the supported range). Structural consistency is checked: killer variants must match the character roster, clue pools must align with the case's declared modes, supported player counts must fall within the engine's capabilities. Any type mismatch, out-of-range value, missing killer variant, or mode-mismatched clue pool fails validation and the case is unplayable.
- **Missing optional content.** The Must Read content displays normally; missing Optional Details are hidden gracefully. Missing acting tips are simply not shown. Optional content is *optional*.
- **Missing required content.** Validation fails. The case is unplayable and never starts. No partial game state is ever created.
- **Backend unreachable.** Cached cases are still playable offline. New cases are unavailable until connectivity returns.
- **Bundled fallback case.** *The Last Dinner* ships inside the app as a guaranteed offline-playable case. Even on a brand-new install with no network, a group can play one full game. **Note:** the bundled case is an offline safety snapshot, not the permanent source of truth. As the live API version of *The Last Dinner* is patched after launch, the bundled version drifts. App releases should periodically refresh the bundled snapshot so first-time-offline players see a recent, well-balanced version of the case rather than a stale build.

**The product principle:** the app never crashes, freezes, or leaks information because of bad or missing case content. Validation is strict; fallbacks are graceful.

### Caching

The app caches downloaded cases locally so that:

- A case a player has opened once is playable again offline.
- Cases load instantly on second view.
- The library stays usable on weak or intermittent connections.

The **first time** a case is opened, the app fetches the latest approved version from the backend. After that, the cached version is used until the backend signals a newer version is available.

### Rollback

The case-management process must support **reverting to a previous approved version** of any case. When a published case turns out to be broken (validation fails for some players), unbalanced (one killer wins 90% of games), or contains a content error (typo in a clue, broken reveal narrative), the team needs to roll back quickly — within minutes, not hours.

In the MVP, where the case-management surface is minimal or manual, rollback can be implemented as a simple version history: every approved case version is preserved, and the team can re-publish an older version. Cached client copies of the broken version are invalidated by the rolled-back version's new publish timestamp.

Rollback is an operational discipline, not just a feature. The team should rehearse rolling back a case before launch — the same way infrastructure teams rehearse failover — so that the first real rollback is not also the first practice run.

### Required fields on every case

At minimum, every case shipped from the backend must include:

- `schemaVersion` — the version of the case schema this case is built against (separate from the case's own version; controls whether the installed app understands the case's structure at all).
- `caseId`
- `title`
- `version` — the version of this specific case's content; changes when the case is patched.
- minimum supported app version
- supported player counts
- supported modes
- supported language
- case intro
- characters
- clue pools
- reveal narratives
- validation metadata

If any required field is missing, malformed, or structurally inconsistent, the case fails validation and is treated as unplayable (see *How unsupported or invalid cases are handled*).

### Prototype rule: fake backend from day one

Even before a real backend exists, the app's prototype must fetch case content through the same code path that will eventually call the production backend. The "backend" during prototyping can be:
- A static JSON file bundled with a dev build.
- A simple mock server serving JSON over HTTP.
- A CDN-hosted document fetched via the same client logic.

What it must **not** be: case content hardcoded inline in the app's source code, even temporarily, even "just to move faster." Hardcoding case content during prototyping creates a code pattern where validation is skipped, caching is untested, and offline fallback is theoretical. By the time the real backend ships, the team has accumulated assumptions that won't hold under real conditions.

The discipline: **the production code path is the only code path, from prototype day one.** The backend implementation can be a fake; the integration cannot be.

### How this affects the MVP and Post-MVP roadmap

#### MVP
- The MVP ships with **one playable case: *The Last Dinner***, delivered through the API.
- *The Last Dinner* is **also bundled** inside the app as the offline fallback case.
- The MVP's app contains the full game engine, both modes, and all validation logic.
- The MVP backend contains the case content for *The Last Dinner*, **a minimal internal case-management surface or manual backend process for updating approved case content** (not a full admin dashboard in the MVP), and the case-delivery API. The goal is to keep the app API-driven from day one without expanding MVP scope into a polished admin tool.
- **The MVP is fully API-driven from day one** — not because the content library is large, but because the architecture should be right from launch. Retrofitting API delivery later is harder than starting with it.

#### Post-MVP
- New cases ship via the backend, without app updates.
- Localized variants of *The Last Dinner* and future cases can be added per language.
- Seasonal cases and limited-time content can be scheduled and released.
- App updates ship only when the **engine itself** changes — new game modes, new round structures, new character schemas, new rules. Most product growth happens through case content, which does not require store reviews.

The split is the product's leverage: **the app is small and stable; the content is large and evolving.**

---

## 24. Long-Term Design Principles

Across the full product:

- **The phone is the host, not the game.** The real game is the conversation around the phone. The app's discipline is to be theatrical and *useful*, then quiet.
- **Pass-and-play is the MVP interaction model. Multi-device local play is a future update/convenience layer. The core social experience still happens around the same table.** The device topology is secondary to players reading faces, lying out loud, and accusing across plates.
- **Every screen is readable from across a small table.** Type is generous. Important text is short.
- **Pacing matters more than content.** A great clue at the wrong moment is wasted. The round structure is the actual product.
- **Players need an out.** A pause option that freezes timers without leaking information is essential for snack breaks, bathroom trips, and the inevitable "wait, who poured the wine."
- **The reveal is the product.** All other design serves the moment when the truth is finally read aloud.
- **Build the simplest version that proves the game is fun. Then expand.**

---

## 25. MVP Success Criteria

The MVP is successful when:

- A group of 4–6 friends can pick up the app, learn the rules in under a minute, and finish a game in under 30 minutes.
- After the first game, the group immediately wants to replay with a new killer.
- The killer wins about 30–50% of the time across all difficulties, balanced.
- Players who like deduction prefer Classic Vote Mode; players who like pressure prefer Elimination Mode — both are used.
- Quiet players speak as much as loud players, thanks to the structured spotlight moments.
- The reveal generates audible reactions: gasps, laughter, accusations across the table.

The promise to the player remains the same as the full vision:
**come for the mystery, stay for the lying, leave with a story.**
