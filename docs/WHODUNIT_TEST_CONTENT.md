# Whodunit Testing Content

Document status: current pre-release testing-content decisions, not production
editorial, localization, balance or content-rights approval. These stories are
still reachable in the application catalog; “testing” describes their approval
status, not a test-only source set. See [Pre-release Compatibility Policy](PRE_RELEASE_COMPATIBILITY.md).

## WD-C2 chronology decisions

The resources are under
`game-modes/whodunit/src/commonMain/composeResources/files/cases/`.
The initial four WD-C2 corrections moved from **1.0.0 to 1.0.1**:

| Story | Factual chronology | Deliberate deception retained |
|---|---|---|
| `last-dinner` | James leaves at 8:55, returns and lights his cigar at 9:15. His method, timeline, objective cigar clues and final explanation agree. Final evidence compares 9:15 with his false 9:10 claim, instead of asserting an unsupported 25-minute gap. | His 9:10 smoking-room alibi remains false; the pantry detour and cufflink still implicate him. |
| `layla-halabi` | There is one electricity outage at 9:05: `التاسعة وخمس دقائق`. Setting notes and intro match the universal clue. Sami returns at 9:35 after the outage; Ghassan's library visit after his 9:20 encounter is also after it. | The hidden office/window detours and existing guilty cover stories remain. No second outage is invented. |
| `jasmine-ring` | In the selected Nadim variant, he enters the cellar at 10:50, is hidden by 11:00, waits for Abu Walid's 11:10 descent, and attacks at 11:12. The final account no longer invents a full hour of hiding. | The false 11:00–11:25 cigarette-shopping story and the shopkeeper's rebuttal remain. |
| `khan-el-khalili` | Karim poisons the cup at 9:40 and joins Refaat in records from 10:15 to 10:55. The final account correctly places poisoning 35 minutes before that corroborated interval. | His records alibi is truthful but deliberately omits the earlier poisoning, as its existing instruction requires. |

### Independently reviewed follow-up

Full seven-story reading found additional factual/reference discrepancies.
These are testing-content coherence choices, not production editorial approval:

| Story | Current version | Follow-up correction |
|---|---|---|
| `last-dinner` | **1.0.2** | Daniel's 9:00 claim versus 9:10 lamp is ten minutes, not fifteen. Vivienne's half-burned document is in the library fireplace, matching her own timeline, companion clue and final account. Her study document-fetching and Daniel's false alibi remain. |
| `layla-halabi` | **1.0.2** | Keep the public 8:45 speech. Amal's speech entry matches it; Rana's method and door witnesses agree with her existing 8:40 dosing/8:50 return and concealed ten-minute detour. Ghassan, Souad and Tarek's method prose now explicitly says 8:58, as their own timelines already did. Amal brings her father's note; Tarek keeps his genuine uncle relationship. |
| `jasmine-ring` | **1.0.3** | Walid returns at 11:22 and stages his search at 11:30: eight minutes. His lie and earlier stair-witness claims remain. Fadi's note now says the following morning, matching his brief and the Thursday-evening setting metadata. The metadata is bundled but not currently displayed, so that latter correction is not claimed as a reproduced visible weekday bug. The final testing choices below align the will's last-week history and Nadim's current shop closure. |
| `khan-el-khalili` | **1.0.2** | Refaat's dismissal note is dated last week, when his private history says he discovered it. His 7:00 a.m. entry explicitly rereads it; preparation two days earlier and Karim's corrected alibi chain remain. |
| `saidi-inheritance` | **1.0.1** | Preserve Zahra's age 68, elder-sister role, divorce at 18 and fifty-year history. The testing age choice makes Mahrous 62 and aligns the physician's age reference to his sixties, rather than rewriting that family history. |
| `iskenderia-corniche` | **1.0.2** | Preserve Atif's age 70 and Magda's elder-sister/caregiver history; the testing age choice makes Magda 75. Her having cared for him at 15 is then coherent. Fatma takes the pack at 7:00 a.m., prepares it in the pantry at 8:25 p.m. and carries the bottle in her apron for the 8:35 table detour. Her method now matches her factual timeline; the bottle evidence and deliberately incomplete 8:40 alibi remain. In the selected guilty Captain variant, discovery is at 9:15 and the shout at 9:17, after the camera's two-minute hesitation. |
| `zamalek-ramadan` | **1.0.1** | Mokhtar's final account now agrees with the two-year secret/audit history. The fictional delay is explicitly approximate, not a two-hour minimum contradicted by 6:48 dosing and 8:30–8:45 death. This is no validation of real-world pharmacology. |

The replacement ages are explicit pre-release editorial choices, not ages
independently recovered from an author. No characters are added or removed;
motives, killer variants, case/character/clue IDs, clue-pool ordering, supported
modes/counts and round settings are unchanged. All seven bundles still support
exactly six players in Classic Vote and Elimination; engine ranges remain broader.

### Final testing-chronology choices

The owner authorized choosing coherent facts instead of leaving these testing
stories blocked on an unrecorded author decision:

- **Jasmine will:** Rasha persuades Abu Walid and he writes the new will last
  week; Walid discovers it that same week. Both dossiers already supplied
  last-week anchors. Copies/drafts and the next-morning signature remain;
  no earlier second will or revision history is invented.
- **Jasmine shop:** Nadim's shop officially closed last week, as its objective
  register clue states. His private current-status paragraph agrees. His
  desperation before closure, retrospective final account, Istanbul plan and
  false cigarette alibi remain.
- **Guilty Captain:** arrival/discovery at 9:15 agrees with the public intro;
  he waits two minutes and shouts at 9:17, matching the camera and final account.
  The other five guilty-only 9:15 shout rows belong to mutually exclusive
  variants, so they are not rewritten.

These choices advance Jasmine **1.0.2 → 1.0.3** and Iskenderia **1.0.1 → 1.0.2**.
They are approved testing corrections, not final production editorial approval.

## Content identity and recovery

`BundledWhodunitCases` derives catalog versions from each envelope;
`WhodunitContentIdentity` computes a canonical full-envelope SHA-256 digest,
excluding the delivery-signature field. Prose is part of that digest. There is
no separate pinned catalog digest to regenerate.

Current local saves record both `caseVersion` and `caseDigest`. Recovery must
match both to the installed case before launching it. A pre-correction 1.0.0
save is **not migrated** to a corrected version, even if character IDs or clue
history still look valid. Likewise, a prior 1.0.1 save cannot launch a current
1.0.2 story. Neither changing a version alone nor substituting a digest makes
incompatible content acceptable. The same rule rejects Jasmine's prior 1.0.2
save under 1.0.3. The compatibility fixtures retain the four original 1.0.0
digests, all seven pre-follow-up identities and the two pre-final-editorial
identities; none is updated to match new prose.

Older saves without either identity field may still be decoded/inspected by
the legacy loader and codec, but cannot launch a game: a pre-clue snapshot
cannot establish which original prose its players saw. Missing identity is
not inferred from the installed bundle. Recovery explains the incompatibility
and leaves the save intact until the player explicitly discards it and starts
a new game. Back/failed recovery is not permission to delete or rewrite it.
Retired Solo records remain unsupported and are rejected before payload
decoding, without an automatic delete or conversion to Pass-and-Play.

LAN admission likewise requires an exact case version/digest match; every
device must have matching content. Live rooms and same-host rejoin are not
local-save migration. Protocol 4.2, game version, content schema 1, minimum app
version 1.0.0 and snapshot formats are unchanged. Reducer authority, session
seeds and own-private projection boundaries are unchanged.

## Verification and remaining editorial work

- `TestingStoryChronologyTest`, `TestingStoryFollowupChronologyTest` and
  `TestingStoryRemainingChronologyTest` pin the
  selected factual chains, reference corrections and preserved lies against
  actual bundled resources and strict production validators.
- `TestingStoryCompatibilityTest` checks all thirteen retired canonical identities,
  current save/load, rejected old/crossed/missing identities, byte retention,
  explicit deletion and exact LAN-offer content matching. Its filesystem is
  synthetic; it is not physical-device or platform-durability evidence.
- `TestingStoryGameTraceTest` iterates the actual seven-case catalog, locates
  real seeds for all 42 killer variants and plays both modes: 84 combinations, each repeated
  deterministically. Every action-produced state round-trips through the
  strict codec and loaded-case recovery, including final evidence and replay.
- `WhodunitRecoveryInteractionTest` exercises the actual recovery UI's failure
  explanation, Retry, Back/remount and explicit discard in English and Arabic
  for incompatible, identity-less and retired-Solo records.

Run focused `:game-modes:whodunit:desktopTest` selectors and the existing
bundled/schema/catalog/recovery suites with strict dependency verification;
then run combined repository checks. Preserve required reports, immediately
stop Gradle, clean only owned generated outputs, and stop again if cleanup
started Gradle. Passing deterministic checks is not a physical LAN playtest,
VoiceOver/TalkBack review, signed-release proof or editorial certification.

Every line and killer variant of all seven files was read for this follow-up.
That does not establish that every narrative claim is coherent or that a
deterministic full-game test is a human playtest. Remaining editorial questions
are distinguished from the factual choices above:

- Overlapping innocent library accounts in Last Dinner, Souad/Rana alibis in
  Layla and cross-variant witness alibis: the shipping briefing permits lying;
  “innocent” alone does not certify every account as an objective timeline.

The apparent Daniel reconciliation-versus-8:30-toast conflict compares
mutually exclusive killer timelines. Jasmine's public 11:10 request does not
by itself forbid the selected Khaled variant's later 11:18 cellar arrival;
Nadim's 11:10 descent must not be imposed on every killer variant. Neither is
treated as a confirmed defect on that evidence. Full editorial and legal
review under [Content Review](CONTENT_REVIEW.md) remains required before
production approval.
