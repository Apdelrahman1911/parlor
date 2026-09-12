# Content pipeline

## Production bind

`contentModule`:

- `RemoteCaseDataSource` = `OfflineRemoteCaseDataSource()`
- `CachedCaseDataSource` = `InMemoryCachedCaseDataSource()`
- `CaseValidator` = `DefaultCaseValidator(json, schema=1, installedAppVersion, registry)`
- `CaseRepository` = `DefaultCaseRepository(remote, cache, bundled, validator, json)`
- `bundled` comes from `whodunitModule` (`BundledWhodunitCases`)

Mafia registers no content types. `mafiaModule` comment: no case JSON.

## Offline vs Ktor

`OfflineRemoteCaseDataSource`: every `listCases` / `fetchCase` returns
`NetworkError.Unreachable`. Not a fake HTTP server. Production never
constructs `HttpClient`.

`KtorRemoteCaseDataSource` (public, commonMain):

- GET `{baseUrl}/games/{gameId}/cases` and `{baseUrl}/cases/{caseId}`
- Rejects non-kebab ids before request
- Status checked before decode (`expectSuccess` is off)
- Bounded body (`Content-Length` + stream `limit+1`)
- Strict UTF-8 then Json
- Maps timeout / 401 / 5xx / serialization
- `CancellationException` preserved
- **Not reachable from DI.** Tests inject `MockEngine`.
- `ktor-client-core` is a commonMain implementation dependency.

`refresh()` on the repository only talks to remote. In production that is
always `Unreachable`. Grep: only `DefaultCaseRepositoryTest` calls it.

## Repository order

`listCases`: remote (validate summaries) → cache summaries → bundled
summaries. Corrupt remote/cache is skipped, not terminal.

`loadCase`: cache → remote → bundled.

- Cache hit that fails validation is **invalidated**, then fallback continues
- Remote success is cached only after envelope **and** payload validator
- Remote identity must equal requested `CaseId`
- Corrupt remote still falls back to bundled
- Bundled load is **not** written to cache (prod cache stays empty)

Every expose path re-encodes the envelope and runs `CaseValidator`.
`ValidatedCase` has an `internal` constructor.

## Validation

`DefaultCaseValidator` order:

1. Parseable JSON
2. `1 <= schemaVersion <= knownSchemaVersion` (prod known = 1)
3. `signature != null` → fail closed (`unsupported by this app version`)
4. `minimumAppVersion <= installed`
5. `GameId` registered and matches payload validator
6. Same summary shape/bounds as the list endpoint
7. Game `PayloadValidator`

`CaseSummaryValidator`: max 128 summaries; kebab `caseId` ≤ 128; title 1..80;
subtitle ≤ 120; BCP-47 language; theme 1..64; players 3..16 and within
definition; duration 1..1440 min; coverArtUrl ≤ 2048; modes non-empty unique
and known to definition; no duplicate ids.

`WhodunitPayloadValidator`: character/clue/brief/reveal/pool rules
(§3.5 in comments). Not re-audited here beyond the persistence boundary.

## Bundled source

`BundledWhodunitCases`:

- Catalog ids must be unique (init)
- `loadJson` injected; prod: `Res.readBytes("files/cases/$id.json")`
  with invalid UTF-8 throwing; missing resource → `null` →
  `requireNotNull` crash (“declared but resource is missing”)
- Envelope `caseId` must equal catalog id
- Malformed JSON **throws** (build bug, not empty library)
- Loaded once under mutex

Catalog (`bundledWhodunitCaseIds`) **exactly** enumerates disk:

| Catalog id | File | Envelope `caseId` | `gameId` | lang | players |
|---|---|---|---|---|---|
| last-dinner | last-dinner.json | last-dinner | whodunit | en | [6,6] |
| layla-halabi | layla-halabi.json | layla-halabi | whodunit | ar | [6,6] |
| jasmine-ring | jasmine-ring.json | jasmine-ring | whodunit | ar | [6,6] |
| khan-el-khalili | khan-el-khalili.json | khan-el-khalili | whodunit | ar | [6,6] |
| iskenderia-corniche | iskenderia-corniche.json | iskenderia-corniche | whodunit | ar | [6,6] |
| zamalek-ramadan | zamalek-ramadan.json | zamalek-ramadan | whodunit | ar | [6,6] |
| saidi-inheritance | saidi-inheritance.json | saidi-inheritance | whodunit | ar | [6,6] |

All `schemaVersion=1`, `version=1.0.0`, `minimumAppVersion=1.0.0`,
modes `classic-vote` + `elimination`. No extra JSON files in that directory.
No catalog id without a file.

Identity for resume / room start: SHA-256 of canonical unsigned envelope
JSON (`WhodunitContentIdentity`). Signature field stripped (and rejected
if present). Persisted on Whodunit snapshots as `caseVersion` + `caseDigest`.

## Cache

`InMemoryCachedCaseDataSource`: `Mutex` + `Map<CaseId, CaseEnvelope>`.
Lost on process death. Production remote never succeeds, so this map is
never populated in the shipping bind. Not a persistence store.
