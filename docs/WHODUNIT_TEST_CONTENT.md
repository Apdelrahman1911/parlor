# Whodunit Testing Content

Document status: current pre-release testing-content decisions, not production
editorial, localization, balance or content-rights approval. These stories are
still reachable in the application catalog; “testing” describes their approval
status, not a test-only source set. See [Pre-release Compatibility Policy](PRE_RELEASE_COMPATIBILITY.md).

## WD-C2 chronology decisions

The four corrected resources are under
`game-modes/whodunit/src/commonMain/composeResources/files/cases/`.
Each moves from content version **1.0.0 to 1.0.1**.

| Story | Factual chronology | Deliberate deception retained |
|---|---|---|
| `last-dinner` | James leaves at 8:55, returns and lights his cigar at 9:15. His method, timeline, objective cigar clues and final explanation agree. Final evidence compares 9:15 with his false 9:10 claim, instead of asserting an unsupported 25-minute gap. | His 9:10 smoking-room alibi remains false; the pantry detour and cufflink still implicate him. |
| `layla-halabi` | There is one electricity outage at 9:05: `التاسعة وخمس دقائق`. Setting notes and intro match the universal clue. Sami returns at 9:35 after the outage; Ghassan's library visit after his 9:20 encounter is also after it. | The hidden office/window detours and existing guilty cover stories remain. No second outage is invented. |
| `jasmine-ring` | Nadim enters the cellar at 10:50, is hidden by 11:00, waits for Abu Walid's 11:10 descent, and attacks at 11:12. The final account no longer invents a full hour of hiding. | The false 11:00–11:25 cigarette-shopping story and the shopkeeper's rebuttal remain. |
| `khan-el-khalili` | Karim poisons the cup at 9:40 and joins Refaat in records from 10:15 to 10:55. The final account correctly places poisoning 35 minutes before that corroborated interval. | His records alibi is truthful but deliberately omits the earlier poisoning, as its existing instruction requires. |

No character, motive, killer variant, case/character/clue ID, clue-pool order,
supported mode/count or round setting changes. All seven bundled stories still
support exactly six players in Classic Vote and Elimination. The other three
case versions remain 1.0.0. Engine ranges remain broader than these bundles.

## Content identity and recovery

`BundledWhodunitCases` derives catalog versions from each envelope;
`WhodunitContentIdentity` computes a canonical full-envelope SHA-256 digest,
excluding the delivery-signature field. Prose is part of that digest. There is
no separate pinned catalog digest to regenerate.

Current local saves record both `caseVersion` and `caseDigest`. Recovery must
match both to the installed case before launching it. A pre-correction 1.0.0
save is **not migrated** to 1.0.1, even if character IDs or clue history still
look valid. Neither changing a version alone nor substituting a digest makes
incompatible content acceptable.

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

- `TestingStoryChronologyTest` pins the selected factual chains and preserved
  lies against actual bundled resources and strict production validators.
- `TestingStoryCompatibilityTest` checks original canonical identities,
  current save/load, rejected old/crossed/missing identities, byte retention,
  explicit deletion and exact LAN-offer content matching. Its filesystem is
  synthetic; it is not physical-device or platform-durability evidence.
- `TestingStoryGameTraceTest` locates actual seeds for all 24 killer variants
  across the four stories and plays both modes: 48 combinations, each repeated
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

Every line and killer variant of the four files was read for this scoped
correction. That does not establish that every unrelated narrative claim is
coherent. Overlapping innocent library accounts in Last Dinner and Souad/Rana
alibi timing in Layla remain editorial questions, not silently rewritten
facts; the shipping briefing permits players to lie. The apparent Daniel
reconciliation-versus 8:30-toast conflict compares mutually exclusive killer
timelines and is not a reachable factual contradiction on that evidence.
Unresolved age/relationship and Zamalek-duration observations are also outside
these four confirmed manifestations. Full editorial and legal review under
[Content Review](CONTENT_REVIEW.md) remains required before production approval.
