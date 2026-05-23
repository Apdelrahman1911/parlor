# Parlor — Content Schema

> Generic `CaseEnvelope` schema (used by every game module) plus the Whodunit-specific `WhodunitCase` payload.
> This is the source of truth for the content validator (Phase 3).

---

## 1. Conventions

- JSON, UTF-8, no BOM.
- Property names are `lowerCamelCase`.
- Versions use semver-like dotted integers (`"1.2.0"`); compared component-wise.
- Player counts are `[min, max]` inclusive integer pairs.
- IDs use kebab-case ASCII (`last-dinner`, `eleanor-hargrove`). Stable across versions.
- Localized strings are plain strings in the case's declared `language`.
- The validator rejects unknown fields (strict parsing) unless explicitly marked **extensible**.

## 2. `CaseEnvelope` — generic

Every case from the backend is a `CaseEnvelope` wrapping a game-module-specific payload.

### 2.1 Required fields

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | integer | The version of *this schema* the case was authored against. MVP value: `1`. Checked **first** by the validator; if newer than the installed app understands, the case is unplayable ("Update required"). |
| `caseId` | string | Stable kebab-case identifier. Unique across the case library. |
| `title` | string | Display title (≤80 chars). |
| `version` | string (semver) | Version of *this case's content*. Bumps on every approved edit. Used for cache invalidation. |
| `minimumAppVersion` | string (semver) | Minimum installed app version that can play this case. The validator rejects with "Update required" otherwise. |
| `gameId` | string | Must match a registered `GameDefinition.id`. MVP value: `"whodunit"`. |
| `supportedPlayerCounts` | `[min, max]` int pair | Player range this case supports. Must be a subset of the module's declared range, capped by the case's own roster. |
| `supportedModes` | array of mode-id strings | Must be a non-empty subset of the module's declared modes. MVP values for Whodunit: `["classic-vote"]`, `["elimination"]`, or both. |
| `language` | string (BCP-47) | E.g., `"en"`, `"en-GB"`, `"ar"`. |
| `theme` | string | Free-form metadata (`"country-manor"`, `"modern-villa"`). Used for filtering and display only; not behavioral. |
| `estimatedDuration` | `[min, max]` int pair (minutes) | E.g., `[25, 35]`. |
| `payload` | object | Game-module-specific content. For Whodunit, conforms to §3 below. |

### 2.2 Optional fields

| Field | Type | Notes |
|---|---|---|
| `subtitle` | string | Display subtitle (≤120 chars). |
| `signature` | string | Optional integrity check (HMAC or signature, base64). When present, validator checks it; when absent, transport-level integrity (HTTPS, bundle digest) is relied on. |
| `metadata` | object | Free-form extensibility for non-behavioral hints (cover art URLs, tags). Validator does not read it. |

### 2.3 Validation order

The validator enforces these checks **in this exact order**. Any failure aborts and produces a typed error.

1. **Parseable JSON.** Malformed → `ValidationError.MalformedJson`.
2. **`schemaVersion` known.** If unknown → `ValidationError.UnsupportedSchema`.
3. **`minimumAppVersion` ≤ installed app version.** If not → `ValidationError.AppUpdateRequired`.
4. **`gameId` registered.** If not in `GameRegistry` → `ValidationError.UnknownGame`.
5. **Required fields present and well-typed.** If not → `ValidationError.MalformedField`.
6. **`supportedPlayerCounts` is a non-empty range within the engine's absolute range `[3, 16]`.** If not → `ValidationError.PlayerCountOutOfRange`.
7. **`supportedPlayerCounts` ⊆ module's declared range** (for the resolved `gameId`). If not → `ValidationError.PlayerCountOutOfRange`.
8. **`supportedModes` is non-empty and each mode is a declared mode of the module.** If not → `ValidationError.UnknownMode`.
9. **Payload validation** (delegated to the module's `PayloadValidator`). Whodunit-specific rules in §3.

---

## 3. `WhodunitCase` payload

The `payload` field of a Whodunit `CaseEnvelope` is a `WhodunitCase` object.

### 3.1 Top-level fields

| Field | Type | Notes |
|---|---|---|
| `publicIntro` | string | Read aloud at the table. ≤1200 chars. |
| `characters` | array of `Character` (length 3–8) | At least 3, at most 8 (engine ceiling). Each character is a possible killer. Order is **stable**; it determines the canonical seat order when the case is presented. |
| `cluePools` | `CluePools` object | See §3.3. |
| `revealNarratives` | object | Map from `characterId` → reveal narrative string (≤2000 chars). Must have exactly one entry per character. |
| `roundConfigByPlayerCount` | object (optional) | Map from player count (as string key) → `RoundConfig`. When absent, defaults are inferred from the module's built-in tables (see §3.4). |
| `bedrockClues` | array of `bedrockClueText` (1–4 entries) | The "Bedrock" universal facts shown alongside the public intro. Plain strings, museum-label length (≤140 chars). |
| `metadata` | object (optional) | Free-form (case authors, illustration notes). Not validated. |

### 3.2 `Character`

| Field | Type | Notes |
|---|---|---|
| `id` | string (kebab-case) | Stable. Used as the key for `revealNarratives`. |
| `displayName` | string | E.g., `"Eleanor Hargrove"`. ≤60 chars. |
| `publicIdentity` | string | One paragraph (≤300 chars). |
| `publicMotive` | string | One line (≤200 chars). |
| `privateSecret` | string | One line (≤200 chars). |
| `relationshipToVictim` | string | One line (≤120 chars). |
| `innocentBrief` | `Brief` | See §3.2.1. |
| `guiltyBrief` | `GuiltyBrief` | See §3.2.2. |
| `optionalDetails` | object (optional) | `backstory`, `actingTips`, `emotionalMotivation`, `suggestedBehavior`, `extraNightDetails`. All plain strings (≤500 chars each). Missing values are hidden gracefully. |

#### 3.2.1 `Brief` (innocent)

| Field | Type | Notes |
|---|---|---|
| `verdictLine` | string | E.g., `"You are innocent."` — short, dramatic. |
| `alibi` | string | One short line (≤220 chars). |
| `goal` | string | One line. |
| `canSayFreely` | string | One line of permission. |
| `mustHide` | string | One line of warning. |

#### 3.2.2 `GuiltyBrief`

| Field | Type | Notes |
|---|---|---|
| `verdictLine` | string | E.g., `"You are the killer."` |
| `method` | string | One line — how the murder was committed. |
| `timeline` | array of `{ time: string, action: string }` | The killer's actual movements. 2–6 entries. |
| `fakeAlibi` | string | The alibi the killer should rehearse. |
| `deflectionTargets` | array of `characterId` (length 1–2) | Who to gently steer suspicion toward. Must be other valid `Character.id`s. |
| `panicMove` | string | What to say if directly accused. |
| `actingTips` | string (optional) | Performance notes. |

### 3.3 `CluePools`

```
CluePools = {
  publicUniversal: [Clue],          // 1–4 entries
  killerPointing:  { characterId: [Clue] },  // one array per character
  redHerring:      { characterId: [Clue] },  // one array per character (steers suspicion toward NON-killer)
  contradiction:   { characterId: [Clue] },  // contradiction clues that surface when this character is the killer
  finalStrong:     { characterId: [Clue] },  // last-round clue per killer variant
}
```

Each `Clue` is:

| Field | Type | Notes |
|---|---|---|
| `id` | string (kebab-case) | Stable. |
| `text` | string | Museum-label length. ≤180 chars. |
| `appliesToModes` | array of mode-id strings (optional) | If absent, applies to all modes the case declares. If present, must be a subset of `supportedModes`. |
| `tags` | array of strings (optional) | Free-form (`"object"`, `"timeline"`, `"witness"`). Not validated. |

**Structural rules** (enforced by the validator):

- Every character listed in `characters` must have a non-empty entry in `killerPointing`, `contradiction`, `finalStrong`.
- `redHerring[killerId]` must reference at least one distinct red-herring target — i.e., the array's clues are about *innocent* characters when `killerId` is guilty. (Per design doc: the red-herring target shifts with the killer.)
- Clue `id`s are unique across the whole `CluePools`.
- `appliesToModes` (if present) must be a subset of the envelope's `supportedModes`.
- Each `killerPointing[killerId]` array has ≥3 clues (enough for a multi-round drip per game).
- `finalStrong[killerId]` has ≥1 clue, and at least two variants if the case declares ≥4 players.

### 3.4 `RoundConfig` (per player count)

Round structure adapts to player count. The module ships defaults; cases may override via `roundConfigByPlayerCount`.

```
RoundConfig = {
  rounds: [Round],
}

Round = {
  id: string,                 // e.g., "alibis", "motives", "contradictions", "final-evidence"
  titleCardText: string,      // shown at the round card
  taglineText: string,        // shorter framing
  cluesToReveal: int,         // how many clues this round draws from the pool
  structuredAction: enum,     // ALIBI_ROUND_ROBIN | DIRECTED_QUESTIONS | SILENT_ACCUSATION | MONOLOGUES | NONE
  discussionSeconds: int,     // 0 disables discussion timer
}
```

**Default round structures (built into the module; overridable by case):**

| Players | Rounds |
|---|---|
| 3 | Alibis → Motives → Final Evidence → Vote *(Post-MVP)* |
| 4 | Alibis → Motives → Contradictions → Vote |
| 5–6 | Alibis → Motives → Contradictions → Final Evidence → Vote |
| 7–8 | Alibis → Motives → Contradictions → Final Evidence → Vote *(future, when a case ships ≥7 characters)* |

Round names are case-overridable (`titleCardText`, `taglineText`); the structural skeleton (clues, structured action, vote timing) is engine-level and cannot be overridden by content.

### 3.5 Whodunit payload validation rules

In addition to the generic envelope rules (§2.3), the Whodunit payload validator checks:

10. **Character count.** `characters.length` ∈ `[3, 8]` and within `supportedPlayerCounts.max`.
11. **Character `id`s are unique** and match `[a-z0-9-]+`.
12. **`publicIntro`, `bedrockClues`** present and non-empty.
13. **Every character has `innocentBrief` and `guiltyBrief`** fully populated (all required sub-fields).
14. **`guiltyBrief.deflectionTargets`** references only valid other-character `id`s (not self).
15. **`revealNarratives`** has exactly one entry per character `id`.
16. **`cluePools`** structural rules per §3.3.
17. **`roundConfigByPlayerCount` (if present)** has keys in `supportedPlayerCounts`; each `Round.structuredAction` is a known enum; integers within sane ranges (`cluesToReveal` 0–5, `discussionSeconds` 0–600).

Any failure produces a typed `WhodunitValidationError` and the case is unplayable.

---

## 4. `CaseSummary` (list payload)

The manifest endpoint returns a list of `CaseSummary` for the library:

```
CaseSummary = {
  caseId: string,
  title: string,
  subtitle: string?,
  version: string,
  gameId: string,
  supportedPlayerCounts: [int, int],
  supportedModes: [string],
  language: string,
  theme: string,
  estimatedDuration: [int, int],
  minimumAppVersion: string,
  coverArtUrl: string?,
}
```

This is a strict subset of the envelope — enough to render the library tile without downloading the full case.

---

## 5. Versioning

- **`schemaVersion`** changes only when the engine adds new schema features. MVP ships with `1`. Bumping requires an app release.
- **`version`** (per case) changes on every approved edit. Cache key is `(caseId, version)`. The repository invalidates older versions when the backend signals a newer one.
- **`minimumAppVersion`** declares the lowest installed app version that can play this case. Cases that need newer engine features bump it.

## 6. Rollback

Rollback is operational, not schema-level. The case-management process re-publishes an older approved `version` with a fresh timestamp. Clients invalidate the broken version's cache entry on next manifest fetch and pull the rolled-back version.

## 7. Examples

A minimum-viable Whodunit case skeleton is shown below. The full *The Last Dinner* draft (Phase 0 Task #2) instantiates this schema.

```json
{
  "schemaVersion": 1,
  "caseId": "last-dinner",
  "title": "The Last Dinner",
  "subtitle": "A country-manor murder mystery.",
  "version": "1.0.0",
  "minimumAppVersion": "1.0.0",
  "gameId": "whodunit",
  "supportedPlayerCounts": [4, 6],
  "supportedModes": ["classic-vote", "elimination"],
  "language": "en",
  "theme": "country-manor",
  "estimatedDuration": [25, 35],
  "payload": {
    "publicIntro": "It was meant to be a celebration...",
    "bedrockClues": [
      "The brandy in the study was poisoned.",
      "The poison was added between 8:30 and 9:30 p.m."
    ],
    "characters": [ /* … */ ],
    "cluePools": { /* … */ },
    "revealNarratives": { /* … */ }
  }
}
```

---

## 8. What the schema deliberately does NOT include

Per `ARCHITECTURE.md` §8.1: the backend cannot ship behavior. The schema therefore has **no** fields for:

- Timer durations (round, discussion, vote).
- Voting rule overrides.
- Tie-rule overrides.
- Safety rule overrides (pause behavior, exposure handling).
- New game modes, phases, or round structures the engine doesn't already know.
- Executable code, expressions, or templates.

If a future case needs different behavior, that behavior ships in a new engine version. Content stays content.

---

*Schema version 1.0 — Phase 0 lock. Subsequent edits to this document are tracked alongside the engine versions that consume them.*
