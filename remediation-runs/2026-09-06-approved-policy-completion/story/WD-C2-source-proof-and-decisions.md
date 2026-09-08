# WD-C2 — fresh source proof and scoped testing-content decisions

Author: `/root/factory_review`; implementation-independent reviewer:
`/root/release_fix_review` (review pending at creation). Source checkout is
`main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`, with pre-existing remediation/user
changes preserved. This is the newly authorized testing-content phase, not a
rewrite of the original audit or a production editorial/content-rights approval.

`input-review.json` records original resource hashes, complete reviewed line
ranges and the original versions. All 1,426 resource lines were reopened:
Last Dinner 1–391, Layla 1–345, Jasmine 1–342, Khan 1–348. That includes every
innocent/guilty variant, optional details, clue category, reveal and round rule.

## Executable reachability reopened

- `content/BundledWhodunitCatalog.kt` includes all four IDs;
  `di/WhodunitDiModule.kt` loads the corresponding common Compose resource with
  strict UTF-8 decoding. `BundledWhodunitCases.kt` fails missing/mismatched
  resources and derives summary versions from their envelopes. There are no
  separately pinned catalog digests or version strings to update.
- `content/WhodunitCase.kt` separates guilty factual `method`/`timeline` from
  `fakeAlibi`. `ui/components/DossierCard.kt:147–163,243–264` displays own
  killer method/timeline, with the alibi separately labeled. It does not
  rewrite contradictory resource prose.
- `WhodunitPhaseRouter.kt:293–308` selects the real verdict narrative;
  `RevealStageScreen.kt:199–242` displays it. Final narrative display does not
  depend on selecting a particular clue. The resources are actual catalog
  content, not test-only fixtures.
- `WhodunitRules.kt:43–71` intersects mode and envelope roster ranges;
  `WhodunitReducer.kt:104–200` deterministically assigns all six characters
  and one killer at the supported six-player count. Alternate guilty briefs
  are mutually exclusive: two different killer timelines cannot be assumed
  to occur together.

Kotlin paths above have prefix
`game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/`.

## Original failure and approved minimal decisions

Resource prefix:
`game-modes/whodunit/src/commonMain/composeResources/files/cases/`.

| Original proof | Selected correction | Intentional deception preserved |
|---|---|---|
| `last-dinner.json:159` factual lighting9:00; objective clues284/336/360 and final376 say9:15. `fs-james-2` additionally invents a25-minute gap. | James returns/lights at9:15. Tie the final cigar evidence to the actual9:10 false claim versus9:15 lighting, rather than another unsupported duration. | James's deliberately false9:10 smoking-room alibi; pantry poison8:55 and cufflink evidence. |
| `layla-halabi.json:15,18,85,132,219` metadata9:00, intro9:45, objective outage9:05; Sami returns9:35 after outage. Ghassan's innocent9:20 encounter and Tarek's9:30 library corroboration cannot precede a9:05 outage. | One outage9:05 in setting/intro/evidence. Ghassan enters library after, not before, that outage. | Guilty cover stories, hidden detours, affair/debts/role motivations. |
| `jasmine-ring.json:77–85,325` cellar entry10:50, hidden11:00, victim descent11:10, attack11:12, but final says a full hour hiding. | Keep detailed clocks, clarify descent11:10 before attack11:12, and replace the final full-hour claim with those explicit clocks. | False cigarette-shopping story11:00–11:25 and the shopkeeper rebuttal. |
| `khan-el-khalili.json:82–87,195,208,331` poison9:40; Karim records10:00 while Refaat/final say10:15; final calls poisoning the immediately preceding minute. | Poison9:40; records10:15–10:55, as corroborated by Refaat. Final states the35-minute interval. | The truthful records interval is still a cover omitting the earlier poisoning; no new motive or crime. |

Each corrected case becomes **1.0.1**. Resource schema1, minimum app1.0.0,
six-player range, case/character/clue IDs, modes, pool order, discussion rules,
seed algorithms, snapshot schema and exact protocol4.2 are unchanged.

## Compatibility boundaries reopened

`WhodunitContentIdentity.kt:42–66` computes canonical complete-envelope
SHA-256 (signature excluded). Prose edits therefore change the digest even
without a version bump. `BundledWhodunitCases` derives summaries at runtime.
Neither the catalog nor a dependency-verification artifact needs regeneration.

`WhodunitGameFlow.kt:813–839` writes `caseVersion` and `caseDigest` in local
snapshot metadata. `loadResumedSession:411–464` validates routing, supported
engine version, metadata shape and the strict game codec.
`validateResumedSessionForCase:474–496` rejects a different persisted identity
before case-bound validation. That validation does not rewrite/delete the save.
`WhodunitPeerSessionFlow.kt:638–695` prepares the loaded case only if roster,
mode and exact offered content identity match. These guards stay unchanged.

**Existing legacy exception:** saves lacking both identity fields still pass
through structural and loaded-case reference/deterministic-clue validation.
Those fields were not persisted, so this branch cannot prove equality of all
old narrative text. Do not advertise exact old/new digest enforcement for
identity-less saves; do not invent an absent identity or silently migrate it.
The current-build mismatch and retention regression covers actual persisted
identity metadata. The separate root-owned recovery UI work covers failure
explanation and explicit discard rather than automatic deletion.

## Regression design (execution owned by root)

- `TestingStoryChronologyTest`: five tests compare the four approved factual
  chains, preserve separately authored lies, and assert only those four
  content versions change. Tests load actual resources through both strict
  envelope and Whodunit validators.
- `TestingStoryCompatibilityTest`: three tests compare original canonical
  digests; exercise real snapshot codec/store loading with synthetic file I/O,
  rejected old/crossed identities and retained bytes; test exact LAN-offer
  content matching for every corrected case and both modes. These are not
  physical LAN, platform file-protection or process-rejoin evidence.
- `TestingStoryGameTraceTest`: one test locates seeds for each of24 killer
  variants, runs both modes (48 combinations), repeats each deterministically,
  follows real actions through four rounds/final evidence/verdict/replay, and
  checks strict snapshot and loaded-case recovery after every action. No
  forced killer, phase copy or widened test-only roster envelope is used.

At file creation the nine tests are **not yet executed** by this author. Root
owns the frozen red cycle and all following green/combined builds and cleanup.
No test success is claimed here. The original complete reachable proof remains
independently recorded in the immutable audit dossiers.

## Unrelated observations are not new confirmed defects

- Daniel/Vivienne both state they were alone in the library over overlapping
  intervals in Last Dinner. Whether this is deliberately imperfect witness
  testimony is not established. Separate reviewer adjudication requested.
- Layla's innocent Souad kitchen8:50–9:25 and Rana's shared parlor9:00–9:40
  are an additional witness-time concern, not silently corrected here.
- The apparent Daniel reconciliation8:15–8:40 versus8:30 toast conflict has
  counter-evidence: the explicit8:30 clocks occur in other killer timelines,
  which are not simultaneously rendered with Daniel's guilty dossier. The
  public intro does not fix that toast to8:30. Do not count this as an
  unavoidable reachable conflict without stronger source evidence.

These observations do not stall the four scoped repairs, do not increase the
confirmed finding count, and do not support a claim of full editorial approval.

## Hygiene

This author has not run Gradle, Xcode, app hosts, simulators, test servers or
background workers. Only allowed source/test/document/evidence files are
authored; no Git history/index/branch operations occur. Source inspection and
`git diff --check` create no build outputs. Root coordinates every heavy
execution, report capture, daemon stop and task-owned output cleanup.
