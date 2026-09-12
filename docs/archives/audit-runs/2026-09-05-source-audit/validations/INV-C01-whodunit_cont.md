# INV-C01 — independent validation of generated review status

**Classification: TEST/EVIDENCE GAP, Low, with DOCUMENTATION MISMATCH wording. Not a confirmed application defect or Store-authorization bypass.** Finder `/root/mafia_cont`; independent validator `/root/whodunit_cont`.

Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. All paths below are under `/Users/abdelrahman/Projects/parlor/`; independently computed current SHA256s/absolute paths and reopened ranges are in `validations/INV-C01-C02-source-hashes-whodunit_cont.json`.

## Complete reachable source-level proof

I reopened and read the complete generator408lines, its three tests125lines, README42lines, override CSV5lines and release validation script104lines, then traced actual callers/guards. `build.gradle.kts:65–70,139–148` wires `productionCheck -> productionReleaseAutomationCheck -> scripts/release/validate_release_system.sh:25–28 -> generate_review_inventory.py --check`. Workflow `production-verification.yml:29–36,66–75` fetches full history, requires a clean checkout and invokes these gates. `workflow_contract.py:517–532` verifies the command occurs once, not review-attestation semantics.

The generator derives each ordinary row solely from indexed **names** at316–319, commit names/subjects since the baseline at255–276, and the four-row override mapping279–296. Renderer322–359 never reads ordinary source bytes or reviewer attestations and emits literal `REVIEWED` at354 for every input path. A path without post-baseline history receives `None identified in the independent review` at308. Check mode388–396 compares only the regenerated CSV text.

Therefore, with identical indexed path names, HEAD/history and overrides, changing an ordinary tracked file's body (staged or unstaged) cannot change the renderer output. Adding a newly indexed path then regenerating gives that path `REVIEWED` without independent review input. This is a deterministic dataflow proof, not a claimed executed mutation. The pure renderer's inputs contain no blob hash, source bytes, reviewer or read ranges that could invalidate such a label. README15–17 explicitly says the generator “reviews all tracked items”; that claim exceeds path/history enumeration.

## Counter-evidence and scope limits

- Untracked exclusion is deliberate, documented and tested; it is **not** this issue. `--no-merges` and full40-character override SHAs are deliberate stability/attribution controls, not themselves defects.
- Tests14–52 prove repeatability and ignored untracked files;54–75 enforce override SHA format;77–110 prove a nonconflicting synthetic merge does not alter metadata. None claims or verifies semantic reading/source-blob identity. Full review of their assertions establishes this narrow evidence gap, not that the tests fail their stated purposes.
- README33–38 assigns exact final source SHA/receipts to a separate report. Findings register15–23 explicitly says it cannot replace an exact-HEAD receipt. CI66–72 additionally enforces a clean checkout. These are important counter-evidence: absence of hashes in this CSV does **not** prove prior reviewers skipped files, the whole historical report is false, or CI accepts dirty source.
- The literal status remains pre-existing across the inspected freshness commit033a563. No claim of a new app regression.
- Candidate preflight `testing-candidate.yml:28–69` is disabled and independently checks exact candidate/clean source and identity approval. `release_tool.py:163–199` rejects the known canonical Store collision separately. No traced consumer turns `reviewer_status` into Store permission. This finding must not be promoted to a signing/promotion/privacy bypass.

## Recommendation and regression coverage

Label generated-only rows `INDEXED` (or similarly truthful metadata) and explain that `--check` proves generator freshness only. If review attestation is desired, separately maintain source-hash/range/reviewer receipts with explicit self-reference exclusions rather than claiming the generator performs review. Test same-path body/staged changes, newly indexed source, and actual merge-resolution changes against that chosen contract. Separate authorization is needed before modifying tooling/docs.

## Evidence limits and hygiene

Source-level proof only; no generator/test/Gradle/Xcode command was run, no repository files/index/history changed, and no app/Store operation occurred. The proposed change-of-body scenario was **not** applied to user work. No generated build outputs or task-owned processes exist from this validation. Root retains sole build/cleanup authority. This gap is independently validated but is not counted as a confirmed production-code defect.
