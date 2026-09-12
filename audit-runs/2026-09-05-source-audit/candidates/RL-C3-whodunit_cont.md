# RL-C3 — Play destination policy checked in an edit different from mutation

Status: **CONFIRMED DEFECT — independent source validation completed**, Medium, **latent release concurrency defect**. Finder: `/root/whodunit_cont`; independent validator: `/root/session_cont`, report `validations/RL-C3-session_cont.md`. Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked modifications. Current release jobs and Store identity remain deliberately blocked; no actual Store incident is claimed.

## Source and complete proposed path

Absolute base: `/Users/abdelrahman/Projects/parlor/`.

- `scripts/release/store_api.py:388–393`: `read_inventory` inserts an edit, lists tracks/bundles, and deletes that edit before returning.
- `scripts/release/store_api.py:475–497`: `require_replaceable_destination` rejects drafts/staged/halted/multiple/multi-version releases. The guard explicitly supports only an empty destination or one ordinary completed release, because track update replaces its releases.
- `scripts/release/store_api.py:641–699`: promotion reads inventory659, checks bundle/source/destination660–668, creates a **new** edit679, and unconditionally replaces the destination track683. It does not inspect tracks in the mutation edit. Validation/commit684–685 only validate the Store edit, not the earlier application-specific policy.
- `scripts/release/store_api.py:358–373`: the replacement body contains only the candidate version at `completed` status.
- `scripts/release/store_api.py:1760–1763`: current CLI execute path requires Store identity approval. Current promotion workflows also disable every job with a false condition.

### Deterministic interleaving proposed

1. Candidate100 exists completed on the required source with the expected AAB digest. Destination production is empty. The first inventory edit passes all policy checks and is deleted.
2. Before679 inserts the second edit, a permitted Console/operator or other API user stages **the same candidate100 at5%** on production. No edit owned by this operation remains open to be invalidated during this interval.
3. The second edit copies the now-current staged100 destination; this operation does not re-read or reject it. The PUT replaces it with completed100, and a Store-valid staged-to-completed transition can commit. This strengthened independent witness needs no downgrade or version-order assumption.
4. Readback confirms100 but cannot undo the discarded staged rollout. The guard's declared refusal policy was bypassed by checking stale inventory.

This interleaving requires an external actor between edits; no adversary, forged candidate, private-data access or API retry is required. A mock can exercise the production function with separate edit snapshots; it cannot establish genuine Store execution.

## Authoritative research and counter-evidence

Access2026-09-05, HTTP200. `research/google-edits-source-1.txt` preserves https://developers.google.com/android-publisher/edits: a newly-created edit copies current deployed state; Console changes or another edit commit invalidate **existing** edits; committing replaces live state. `research/google-edits-source-2.txt` preserves https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/update and `research/google-edits-source-3.txt` preserves https://developers.google.com/android-publisher/tracks. Relevant paragraphs read; no Store API called.

Counter-evidence: if the external change occurs **after** mutation edit679 is created, Google invalidates it and commit fails. That protection does not cover the between-edit interval above. Shared GitHub concurrency serializes these repository Store jobs but cannot lock a Console user. A separately established exclusive-owner prohibition could narrow prerequisites; no such technical lock has been proved. Current workflows and identity are deliberately blocked, so there is no currently reachable enabled Store mutation or observed real rollout loss. The independent validator explicitly classified this as latent code, not an authorization bypass or currently enabled production incident.

## Suggested remediation and tests

Validate bundle/source/destination and perform the guarded update in the **same** edit; handle already-present and failure cleanup within that ownership scope. Preserve no-blind-retry behavior. Synthetic test should change destination between the old preflight and mutation edit, assert refusal/no PUT, and cover source removal, unchanged destination, already-present idempotent receipt and Google invalidation after insertion. External controlled-Store rehearsal remains separate and owner-authorized.

The independent validator reopened the real helpers/callers/tests, corroborated Store edit semantics with official documentation, and constructed `reproducers/RL_C3PromotionEditRaceTest.py`. That reproducer was not run by finder or validator; execution receipts, if any, belong to root's sole verification lane. Confirmation here rests on the complete deterministic source-level proof and independently checked API semantics. No application modifications, builds, production-script/test execution, real signing or Store operations performed by finder.
