# Independent validation — WD-C2 (authored narrative consistency)

## Identity and scope

- Finder: `/root/whodunit_cont`. Independent validator: `/root/mafia_cont`.
- Checkout: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Reviewed resource/source SHA-256 values: `validations/WD-C2-mafia_cont-source-hashes.json`; actual complete/partial reading ranges: `coverage/reviews-mafia_cont.jsonl`.
- Repository prefix for every path below: `/Users/abdelrahman/Projects/parlor/`. Abbreviated game Kotlin suffixes (`content/`, `domain/`, `ui/`, `di/`) are under `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/`; app `ContentModule.kt` is beside the fully specified `AppModule.kt`.
- No source changes, builds, runtime tests, devices, or Store operations performed by this validator. This is a reachable source/resource proof, not device evidence.

## Verdict

**CONFIRMED DEFECT — Low, shipped authored-content consistency.** Narrow the grouped finding to the four chronology manifestations below. The age-order and embezzlement-duration siblings remain unconfirmed editorial questions; they are not independently confirmed defects.

The impact is contradictory information in a social-deduction narrative: players cannot reconcile supposed factual dossier/events with later objective evidence/final explanation. No engine determinism, winner calculation, protocol, private-state isolation, or storage failure is established. Do not label this a security issue or invent an authoritative replacement time.

## Reopened production path and guards

1. `composeApp/build.gradle.kts:122–148` includes the game in `commonMain`. `composeApp/src/commonMain/kotlin/com/parlor/app/di/AppModule.kt:67–74` installs the shipping game module. `ContentModule.kt:39–79` registers its binding and routes production content through offline/bundled validation, not a test fixture or a remote override.
2. `game-modes/whodunit/build.gradle.kts:46–53`, `content/BundledWhodunitCatalog.kt:10–18`, and `di/WhodunitDiModule.kt:35–49` establish that the actual common Compose resources are loaded by ID. The catalog includes all seven candidate stories. Each candidate envelope declares six players and both shipping modes. This validator read the full primary Last Dinner resource; other resource reads are explicitly partial in the ledger.
3. `content/WhodunitPayloadValidator.kt:34–455` validates shape, required text, safe characters, IDs/references, supported modes, clue availability, and round configuration. Successful validation returns the unchanged decoded case at line254; there is no semantic-time reconciliation or story-specific substitution. Such a validator cannot establish editorial consistency by passing.
4. `domain/rules/WhodunitRules.kt:31–71` binds a valid six-seat session to the chosen case/mode. `domain/reducer/WhodunitReducer.kt:104–200` checks those guards, shuffles all six characters into six seats, chooses the killer with the seeded RNG, and stores each player's own character/role. No candidate character is excluded at this supported count.
5. `ui/flow/WhodunitPhaseRouter.kt:218–227,517–650,687–823` routes the actual case intro and gated own dossier, in pass-and-play or host/peer multiplayer. The dossier requires authoritative unlock; that delays disclosure but does not remove contradictory text. `ui/screens/reveal/CharacterRevealScreens.kt:148–175` passes the character and role into `DossierCard`.
6. `content/WhodunitCase.kt:43–54` separates factual `method`/`timeline` from `fakeAlibi`. `ui/components/DossierCard.kt:76–92,147–163,243–264` renders fake alibi separately, then method and all timeline entries for the killer. The English label is `YOUR TIMELINE.` (`composeResources/values/strings.xml:197`), not a warning that the supplied timeline is a lie.
7. `domain/rules/WhodunitCluePolicy.kt:44–87` makes first-round public-universal and killer-pointing clues eligible, and final evidence comes from the selected killer's final pool. Final narratives do not depend on clue sampling: `ui/flow/WhodunitPhaseRouter.kt:293–308,436–449,465–484` resolves the actual verdict's killer ID; `ui/screens/reveal/RevealStageScreen.kt:199–242` displays that narrative. Both shipping modes and Android/iOS/common Desktop UI use this path.

## Confirmed manifestations

Resource paths have prefix `game-modes/whodunit/src/commonMain/composeResources/files/cases/`.

| Resource / one-based lines | Independent source proof and counter-evidence |
| --- | --- |
| `last-dinner.json:152–165,280–284,335–336,358–360,376` | James's factual guilty timeline tells him to light a cigar in the smoking room at **9:00 p.m.**; the same case says his smoking-room cigar was unlit until **9:15**, and the final explanation says he returned and lit it at9:15. The9:10 claim is separately marked `fakeAlibi`; intentional alibi deception does not explain the9:00-versus9:15 factual accounts. A second cigar/re-lighting is not authored. This contradicts the final explanation of the cigar evidence, not just an innocent witness's fallible observation. |
| `layla-halabi.json:18,79–87,215–219` | Public intro puts the electricity outage at **9:45** (`التاسعة وخمس وأربعين دقيقة`); public-universal clue `ld-bedrock-3` puts it at **9:05**; Sami's factual9:35 entry already says electricity was out. The public intro and universal clue are not fake alibis. No restoration/second outage is supplied to reconcile them; metadata says9:00 but is not needed for the rendered-path proof. |
| `jasmine-ring.json:75–88,323–325` | Nadim's factual method/timeline enters the cellar at **10:50**, hides by **11:00**, and attacks when the victim descends around **11:10–11:12**. Final account says he hid in the cellar for a **full hour** (`ساعة كاملة`) before the descent. At most22minutes fit between entry and attack; even the complete cellar visit through11:18 is under an hour. The fake cigarette-shopping story at line85 is separate and does not supply the missing interval. |
| `khan-el-khalili.json:76–90,193–211,329–331` | Karim's factual timeline poisons at **9:40** and leaves for records with Refaat at **10:00**. Final explanation calls the records alibi truthful from **10:15**, then locates poisoning in **the minute before that** (`أمّا الدقيقة قبل ذلك`). Refaat's innocent account at195 corroborates10:15, not a fictional fake-alibi time. The9:40 poisoning cannot be that preceding minute. The two records-arrival times are a related manifestation, not an additional counted defect. |

### Reproduction without changing source

For each affected story, start its supported six-seat game in either shipping mode. For character-specific contradictions, use a session whose seeded assignment gives James/Nadim/Karim the killer role (or an isolated deterministic test fixture selecting a matching seed). Open that player's authorized dossier, note the authored timeline, then progress normally to the verdict/reveal for that killer. Compare the displayed final narrative. For Layla, read the opening intro and a first-round draw of `ld-bedrock-3`, or compare the9:45 intro with Sami's9:35 killer timeline. No actual seed search or runtime reproduction was executed in this validation; the complete data-to-display path above is the evidence.

## Rejected as definite contradictions / retained unconfirmed

- **`iskenderia-corniche.json:20,29–30,57` and `saidi-inheritance.json:20,29–30,57`: UNCONFIRMED — editorial clarification.** The cited numbers are present (victims70/72, sisters65/68), but `شقيقته الكبرى` can mean eldest among sisters rather than necessarily older than that brother. Corniche's account of mothering him at15 implies he was20 and is odd; maternal rhetoric is still possible. Saidi gives no date for the mother's death. No stronger authored requirement establishes the intended age ordering. The finder agreed there is no stronger cited evidence. Recommend owner clarification, not a claimed unavoidable age defect.
- **`zamalek-ramadan.json:161,320,334`: UNCONFIRMED — editorial clarification.** The private secret literally identifies a large theft **two years ago**; an audit clue mentions two years of embezzlement; the final narrative mentions three. This may be drift, but a two-year finding could be a subset of longer conduct, and the private text does not explicitly say all conduct began then. Without a product/content decision, this is not as strong as the direct event-time clashes. Do not count it as independently confirmed.
- Alibis explicitly labeled fake, alternate-killer methods, NPCs beyond the six seated suspects, toxicology realism, and general stylistic awkwardness are outside this confirmed finding.

## Suggested remediation and regression coverage

Ask the content owner to choose canonical event facts, then align factual method/timeline/objective clues/reveal together while preserving intended lies. Review all killer variants because text is reused across innocent/guilty roles. Do not change reducers, random sampling order, rules, protocol, or peer payloads. Treat changes as content-version/recovery-identity-sensitive and review that migration/binding separately before implementation.

Recommended checks: targeted editorial assertions for the chosen facts, existing strict bundled/schema/catalog validators, all-killer/mode clue eligibility, and deterministic complete-game fixtures. A full narrative editorial pass remains useful; neither a JVM validator nor a visual screenshot can automatically establish that prose is coherent. No absence-of-other-content-defects claim is made.
