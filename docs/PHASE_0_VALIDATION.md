# Phase 0 Validation Report — *The Last Dinner* against `CONTENT_SCHEMA.md`

> Dry-run validation of `content/last-dinner.draft.json` against the schema locked in `docs/CONTENT_SCHEMA.md`.
> This is a manual structural check performed before any code is written.

---

## 1. Envelope-level checks (§2.3)

| # | Check | Status | Notes |
|---|---|---|---|
| 1 | Parseable JSON | ✅ | Single root object, balanced braces, valid string escapes. |
| 2 | `schemaVersion` known | ✅ | `1`. |
| 3 | `minimumAppVersion` resolvable | ✅ | `"1.0.0"`. Effective against the first shipped app build. |
| 4 | `gameId` registered | ✅ | `"whodunit"`. Will resolve once `WhodunitDefinition` is wired in Phase 2. |
| 5 | Required fields present | ✅ | `schemaVersion`, `caseId`, `title`, `version`, `minimumAppVersion`, `gameId`, `supportedPlayerCounts`, `supportedModes`, `language`, `theme`, `estimatedDuration`, `payload`. |
| 6 | `supportedPlayerCounts` within engine `[3, 16]` | ✅ | `[4, 6]`. |
| 7 | `supportedPlayerCounts` ⊆ Whodunit's `[4, 8]` | ✅ | `[4, 6]` ⊂ `[4, 8]`. |
| 8 | `supportedModes` ⊆ Whodunit's declared modes | ✅ | `["classic-vote", "elimination"]` — both declared. |
| 9 | `supportedPlayerCounts` consistent with declared modes | ✅ | Classic Vote allows 4–6 (case bound), Elimination 5–6 (effective intersection of mode `[5,8]` and case `[4,6]`). The Player Count UI will surface this per `ARCHITECTURE.md §1.5`. |

---

## 2. Payload-level checks (§3.5)

| # | Check | Status | Notes |
|---|---|---|---|
| 10 | `characters.length` ∈ `[3, 8]` and ≤ `supportedPlayerCounts.max` | ✅ | 6 characters, max player count 6. |
| 11 | Character `id`s unique, kebab-case `[a-z0-9-]+` | ✅ | `eleanor-hargrove`, `daniel-hargrove`, `vivienne-cross`, `james-sutton`, `clara-bell`, `henry-vance`. |
| 12 | `publicIntro` and `bedrockClues` present and non-empty | ✅ | Intro ~750 chars (within ≤1200). 4 bedrock clues (within 1–4). |
| 13 | Every character has fully populated `innocentBrief` and `guiltyBrief` | ✅ | All 5 innocent + 7 guilty sub-fields present per character. |
| 14 | `guiltyBrief.deflectionTargets` references valid other-character `id`s (not self) | ✅ | All six deflection arrays point to other valid characters; no self-reference (verified individually below). |
| 15 | `revealNarratives` has one entry per character `id` | ✅ | 6 entries, exactly matching the character roster. |
| 16 | `cluePools` structural rules per §3.3 | ✅ | See §3 below. |
| 17 | `roundConfigByPlayerCount` (if present) well-formed | n/a | Omitted; module's built-in defaults apply (Alibis → Motives → Contradictions → [Final Evidence] → Vote). |

### Deflection target audit

| Killer | Deflection target(s) | Valid? |
|---|---|---|
| Eleanor | `vivienne-cross` | ✅ |
| Daniel | `vivienne-cross` | ✅ |
| Vivienne | `daniel-hargrove` | ✅ |
| James | `henry-vance` | ✅ |
| Clara | `eleanor-hargrove` | ✅ |
| Henry | `clara-bell` | ✅ |

No killer points at themselves. All targets are valid character ids.

---

## 3. Clue-pool structural audit (§3.3)

### 3.1 Required-presence

| Pool | Rule | Status |
|---|---|---|
| `publicUniversal` | 1–4 entries | ✅ 2 entries. |
| `killerPointing[killerId]` | non-empty for every character, ≥3 clues | ✅ 4 clues per character × 6 characters = 24 clues. |
| `redHerring[killerId]` | non-empty for every character, points to an innocent | ✅ 2 clues per character × 6 = 12 clues. Each set steers toward a non-killer (verified by reading the prose). |
| `contradiction[killerId]` | non-empty for every character | ✅ 1 clue per character × 6 = 6 clues. |
| `finalStrong[killerId]` | ≥2 variants for ≥4-player cases | ✅ 2 variants per character × 6 = 12 clues. |

### 3.2 Red-herring targets (per playthrough)

| Killer | Red herring steers toward | Killer ≠ target? |
|---|---|---|
| Eleanor | Vivienne | ✅ |
| Daniel | Vivienne | ✅ |
| Vivienne | Daniel | ✅ |
| James | Henry | ✅ |
| Clara | Eleanor | ✅ |
| Henry | Clara | ✅ |

Note: this matches the deflection-target table above by design — the red-herring clues reinforce the deflection target the killer is told to steer suspicion toward.

### 3.3 Clue id uniqueness

Manual scan across all five pools confirms no duplicate clue ids:

- `pu-*` (2 ids): unique.
- `kp-{killer}-{1..4}` (24 ids): unique by killer-and-index.
- `rh-{killer}-{1..2}` (12 ids): unique.
- `co-{killer}-1` (6 ids): unique.
- `fs-{killer}-{1..2}` (12 ids): unique.

Total: 56 clue ids, all distinct.

### 3.4 Clue text length

Each clue line is well within the ≤180 char museum-label limit. Longest observed clue is the `fs-eleanor-1` (~150 chars). No clue is empty.

### 3.5 `appliesToModes` (optional)

No clues declare `appliesToModes` — i.e., every clue applies to both Classic Vote and Elimination. This is intentional for the MVP case; future cases may scope clues per mode.

---

## 4. Narrative-level review

These checks are quality-oriented rather than schema-strict, but they matter for whether the case plays well.

| Check | Status | Notes |
|---|---|---|
| Every character has a strong motive readable in one line | ✅ | Each motive is concrete and specific (will change, inheritance, theft, sale, pension, prescription scheme). |
| Every character has a private secret distinct from the murder | ✅ | Affair, fury at "reconciliation," theft, earlier confrontation, decades-old suspicion, prescription scheme. |
| Innocent alibi is true-but-uncomfortable | ✅ | Each innocent's alibi forces them to lie or omit something embarrassing-but-not-criminal. |
| Guilty timeline accounts for the 8:30–9:30 window | ✅ | Every guilty brief has a specific pantry visit within the bedrock window. |
| Reveal narratives are short stories, not exposition | ✅ | Each reveal is a ~6-sentence narrative pacing the crime, motive, and signature flourish. |
| Killer-pointing clues form a coherent trail | ✅ | Each killer's 4-clue set leaves a trail: object near the pantry, opportunity within window, witness fragment, and a story-specific marker (footprint, page, lamp, cufflink, ledger, handkerchief). |

---

## 5. Player-count consistency

Per `ARCHITECTURE.md §1.4` and §1.5:

- **Whodunit module:** Classic Vote 4–8, Elimination 5–8.
- **Case (`The Last Dinner`):** `[4, 6]` — capped by 6-character roster.
- **Effective at runtime:**
  - Classic Vote: 4, 5, 6 (intersection of `[4,8]` and `[4,6]`).
  - Elimination: 5, 6 (intersection of `[5,8]` and `[4,6]`).
- **Player Count UI** at launch will hide counts > 6 (per the documented "hide-unsupported" product lean), or disable them with a "This case supports up to 6 players" message — either is supported architecturally.

No inconsistency between the case's declared counts and the module's per-mode bounds.

---

## 6. What the validator would not yet check (and is fine for Phase 0)

- **Image asset existence.** No image fields in this draft; deferred until cover art is commissioned.
- **Audio asset existence.** No audio fields in this draft.
- **Localization completeness.** Single-language (`en`) only; future cases will declare additional `language` variants per case-id-version.
- **Signature.** `signature` field is omitted; transport-level integrity (HTTPS, bundle digest) covers MVP.

---

## 7. Verdict

✅ **`content/last-dinner.draft.json` passes all schema rules and structural checks.**

The draft is ready to be consumed by:

- The Phase 3 `WhodunitPayloadValidator` (which will encode the rules above as code).
- The Phase 3 bundled fallback path.
- The Phase 3 mock backend.

Open items (do not block Phase 0):

1. **Prose review.** The dossier text is a Phase 0 draft. The Phase 8 final-content review will edit for theatricality and consistency of voice.
2. **Playtest-driven balance.** Killer win rate per killer variant will be measured during the Phase 6 playtest gate. Clue pools may be tuned (more red-herring weight, weaker final clue, etc.) before Phase 8.
3. **Tomas the gardener** appears only in narration (Eleanor's brief and reveal). He is not a playable character. This is intentional for the MVP but should be flagged so future cases do not accidentally cast off-stage "ghost" suspects.

---

*Validation performed: end of Phase 0. The draft is locked for Phase 1; subsequent edits go through the same validator as a code change.*
