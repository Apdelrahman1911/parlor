# Parlor content format

Document status: current authoring guide. Production Kotlin types, validators,
reducers, and tests are authoritative. If this guide and executable behavior
disagree, the code must be reviewed and this guide corrected; documentation is
never permission to bypass validation.

## Executable contract

The primary implementation is:

- `shared/content/.../schema/CaseEnvelope.kt` and `CaseSummary.kt`;
- `shared/content/.../validation/DefaultCaseValidator.kt` and
  `CaseSummaryValidator.kt`;
- `game-modes/whodunit/.../content/WhodunitCase.kt` and
  `WhodunitPayloadValidator.kt`;
- `game-modes/whodunit/.../domain/rules/WhodunitRules.kt`; and
- the content tests under `game-modes/whodunit/src/desktopTest/.../content/`.

Production JSON is strict (`ignoreUnknownKeys = false`, non-lenient UTF-8), so
an unknown field or incompatible shape fails closed. The shipping app supports
schema version 1. A schema value below 1 is malformed; a newer value requires
an app update.

## Common envelope

Every case is a UTF-8 JSON object matching `CaseEnvelope`:

| Field | Type | Current rule |
|---|---|---|
| `schemaVersion` | integer | Exactly a schema understood by the build; currently `1`. |
| `caseId` | string | Unique lowercase kebab-case ID, at most 128 characters. |
| `title` | string | Non-blank, at most 80 characters. |
| `subtitle` | string or absent | At most 120 characters. |
| `version` | quoted semantic-version string | Parsed by `SemVer`; identifies this content revision. |
| `minimumAppVersion` | quoted semantic-version string | Must not exceed the installed app version. |
| `gameId` | string | Must resolve to the requested installed game and the selected payload validator. |
| `supportedPlayerCounts` | `[min,max]` | Inclusive, within 3–16 and within the installed game definition. |
| `supportedModes` | string array | Non-empty, unique, and every mode must be supported by the game. |
| `language` | string | Bounded BCP-47-shaped tag, at most 35 characters. |
| `theme` | string | Non-blank, at most 64 characters. |
| `estimatedDuration` | `[min,max]` | Inclusive minutes, within 1–1440. |
| `payload` | object | Decoded and validated by the resolved game module. |
| `signature` | string or absent | Must be absent. This app has no configured key-pinned content-signature verifier and rejects a non-null value. |
| `metadata` | JSON or absent | Non-behavioral envelope metadata; still bounded by source response/resource limits. |

`IntRangePair` encodes as a two-integer JSON array, not an object.

The list endpoint shape is `CaseSummary`, a strict subset of the envelope plus
an optional `coverArtUrl`. A manifest contains at most 128 summaries. Summary
IDs must be unique and must match the requested game. A fetched envelope must
match the exact requested case and its advertised summary before it can be
returned or cached.

Remote list and case bodies are bounded to 256 KiB and 512 KiB respectively.
The current production binding is offline and uses bundled resources; the same
validators still run on bundled data.

## Whodunit payload

`WhodunitCase` contains:

- non-blank `publicIntro` and 1–16 bounded `bedrockClues`;
- 4–8 `characters`, with unique bounded kebab-case IDs;
- a `cluePools` object;
- `revealNarratives` keyed exactly by every character ID;
- optional `roundConfigByPlayerCount`; and
- optional bounded payload metadata.

Each character contains a display name, relationship, public identity, public
motive, private secret, complete innocent brief, complete guilty brief, 1–32
timeline entries, unique non-self valid deflection targets, and optional
bounded details. Required text must be non-blank. The validator's constants are
the exact length limits; content authors must not infer a looser limit from UI
layout.

## Clue invariants

`CluePools` contains `publicUniversal`, `killerPointing`, `redHerring`,
`contradiction`, and `finalStrong` pools.

- `publicUniversal` has 1–4 clues.
- Every possible killer has at least three killer-pointing clues, one
  contradiction clue, and two final-strong variants.
- Keyed pools may not contain an unknown character key.
- Every clue ID is unique across every pool and is bounded kebab-case.
- Total clue count is at most 256.
- `appliesToModes`, when present, is non-empty, unique, and a subset of the
  envelope's modes.
- Tags are unique, bounded, and non-blank.
- For every declared mode and supported player count, every possible killer
  must have enough eligible opening/middle/final evidence to reach that mode's
  finite terminal round without exhausting the pool.

The last rule is a gameplay-reachability check, not merely schema validation.

## Modes, counts, and round configuration

The installed Whodunit definition supports 4–8 players. `classic-vote`
supports 4–8; `elimination` supports 5–8. A case further restricts that range
through its envelope and available character count. The current seven bundled
cases each declare exactly six players and both modes.

Custom round configurations are optional and keyed by a supported player
count. Each has 1–8 unique bounded round IDs. Current reducer/UI behavior
supports exactly:

- `cluesToReveal = 1`;
- `structuredAction = NONE`; and
- `discussionSeconds` from 1 through 600.

Other serialized `StructuredAction` enum values are legacy content vocabulary,
not shipping gameplay features. The validator rejects them so content cannot
silently request a reducer action that does not exist.

## Validation and release gate

Run the focused authoring checks:

```text
./gradlew :shared:content:desktopTest \
  :game-modes:whodunit:desktopTest \
  --dependency-verification=strict --rerun-tasks --no-daemon
```

`productionCheck` must also pass before release qualification. Structural
validation does not replace the creative, localization, balance, accessibility,
or physical playtest checklist in `CONTENT_REVIEW.md`.
