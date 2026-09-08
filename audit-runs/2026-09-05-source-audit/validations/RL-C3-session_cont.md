# RL-C3 — Independent validation: destination guard belongs to the wrong Play edit

- **Classification: CONFIRMED DEFECT (latent release mutation code), Medium.** An intervening operator change can bypass Parlor's explicit staged-rollout refusal policy. Current CLI/CI release-stop guards prevent enabled production mutation; no actual rollout/Store incident is claimed.
- Finder `/root/whodunit_cont`; independent validator `/root/session_cont`,2026-09-05.
- Source identity: `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; source SHA256 in `validations/RL-source-hashes-session_cont.json`.
- Absolute root for all paths below: `/Users/abdelrahman/Projects/parlor/`.

## Reachable implementation and expectation

`scripts/release/store_api.py:641–699` loads/validates the immutable manifest, exact package, distinct tracks, operation/production destination and candidate bundle digest. It reads inventory659, validates source availability/completed status and destination replacement policy660–668, then creates a **different** mutation edit679. The `set_track` call683 uses `GoogleClient.set_track:358–373`, a PUT with only the selected candidate at `completed` status; `validate_edit` and `commit_edit`375–379 do not run Parlor's earlier destination policy again.

`GoogleClient.read_inventory:388–393` creates a read edit, copies the tracks/bundle data through GET, and deletes that edit in finally before returning. `require_replaceable_destination:475–497` allows only an empty track or one single-version completed release. It rejects `inProgress`, draft, halted and multi-release shapes. The same explicit refusal is documented in `docs/RELEASE_AUTOMATION.md:474–478` and asserted in `scripts/release/tests/test_store_api.py:383–413`; this expectation is grounded in executable policy, not a new release-product assumption.

## Complete deterministic source proof

Use one valid immutable candidate already completed on `closed-testing`, with its exact AAB digest in bundle inventory. The destination `production` is empty at the first inventory snapshot.

1. The first inventory edit sees that empty destination and is deleted before659 returns. Digest/source/policy checks pass.
2. Between deletion of that edit and creation of the new mutation edit679, an authorized operator stages **the same candidate** on production at5%. This avoids any invented rollback or version-order premise.
3. Google's newly inserted edit copies this current `inProgress` destination. Parlor never reads that snapshot or invokes its replacement guard against it.
4. `set_track` replaces it with the same candidate at `completed`. Google's documented staged-rollout completion is precisely a track update setting `completed`, so this is a valid Store state transition, not one the API's generic validation must reject.
5. No outside change occurs after the mutation edit is created; it is not invalidated. Commit can succeed. Post-commit inventory693–698 observes exactly the expected candidate/digest/completed status and returns a successful receipt, although the staged-rollout policy was bypassed and the operator's5% restriction was removed.

Changing a different allowed destination shape or removing source eligibility in the same interval is the same stale-precondition root cause, not an additional finding count.

## Independent counter-evidence

- **Current operational reachability is deliberately blocked.** `main:1760–1763` invokes `assert_store_identity_approved` before a Google execute command. `release_tool.py:163–199` fails for known-collision `com.parlor.app` and for unverified ownership; policy `config/release-policy.json:7–28` remains blocked. External/production Android jobs are explicitly false (`testing-external-promotion.yml:148–155`, `production-promotion.yml:243–250`). No gate was changed or bypassed on a real boundary. This report identifies a latent algorithm defect to repair before authorized activation, not an authorization bypass.
- Validation-only CLI returns without constructing clients (`store_api.py:1794–1796`). No effect in dry-run, either game, pass-and-play, LAN runtime, navigation, or snapshots.
- Google invalidates **existing** edits when another user commits or Console changes the app. This correctly prevents the variant where the operator changes state *after* mutation edit679. It cannot invalidate an edit not yet created, which is the candidate's interval.
- Both workflow jobs share `parlor-google-play-production-identity` concurrency, preventing these repository jobs from racing each other. That lock cannot serialize a Console user or separately authorized API client. No source-enforced lock encompassing those actors was found; an operational exclusion would narrow prerequisites, not make two unrelated edit snapshots atomic.
- Immutable candidate/source/receipt/digest checks protect the promoted bytes; the same legitimate candidate is used here, so those guards do not contradict the proof.
- `validate_edit` checks Store validity, not the local refusal rule. Official documentation explicitly supports the staged→completed update used above.
- Existing tests reject unsafe destinations when directly supplied to the guard and prove wrong bundle digest/no-mutation and already-present idempotency (`test_store_api.py:242–314,383–413`). They do not model an edit snapshot change between guard and mutation.
- Sibling internal-upload path also has pre-read logic but server version uniqueness, upload digest checks and recovery semantics differ; no extra defect is asserted without its own proof.

## Reproduction material and limits

`reproducers/RL_C3PromotionEditRaceTest.py` was prepared but **not run by this validator**. It retains the real `google_promote_execute`, `read_inventory`, insert/delete/list/set/validate/commit helper implementations and schema-valid existing `manifest()` fixture. Only `GoogleClient.request` is overridden with a bounded synthetic edit-snapshot model; real network and signing entry points are forbidden. It models documented copy/invalidation behavior, not a genuine Google Store.

Proposed root-lane command:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 audit-runs/2026-09-05-source-audit/reproducers/RL_C3PromotionEditRaceTest.py
```

Expected current result: the between-edits test fails because a committed receipt replaces the newly staged5% release; the after-mutation-insert counter-evidence test passes because the synthetic edit is invalidated. Execution results must come from root's separate receipt. Confirmation here rests on complete reachable helper-level source proof plus the official API semantics, with the current operational gate exclusion stated above. No signed artifact, credentials or Store API was used.

## Recommended remediation/regression tests

Create one mutation edit first, read source/destination/bundle inventory **inside that edit**, apply all policy checks against those exact snapshots, then PUT/validate/commit that same edit. Clean up on rejected or already-present early return, preserve no-blind-retry rules and exact immutable digest/source binding. Do not weaken destination policy or enable workflows to make a test pass.

Add deterministic synthetic cases for a newly staged destination between old preflight and insertion; unavailable source; unchanged destination; already-present candidate; failed validation/cleanup; and outside change after insertion causing invalidation. Real controlled-Store rehearsal remains owner-authorized external validation and must not be claimed from mocks.

## Authoritative research and hygiene

The independently reread official documentation, retrieved2026-09-05 by this audit and retained with URL/access receipts:
- <https://developers.google.com/android-publisher/edits>, `research/google-edits-source-1.txt:93–107,111–151`: edit copies deployed state; all existing edits invalidated by external changes; valid commit publishes edits.
- <https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/update>, `research/google-edits-source-2.txt:301–338`: PUT is bound to package/edit/track.
- <https://developers.google.com/android-publisher/tracks>, `research/google-edits-source-3.txt:168–252`: staged rollouts, percentages and completion through completed status.

Public text was read locally; no authenticated API/Store operation, private input, build, test, app/server or Gradle daemon was started by this validator. Only isolated audit notes/reproducer/read receipts were written. Root owns execution/cleanup; generated audit evidence is retained intentionally.
