# INV-C01 — Generated inventory does not attest reviewed source identity

Finder: `/root/mafia_cont`. Independent validation: `/root/whodunit_cont`, complete. Classification: **TEST/EVIDENCE GAP / documentation overstatement**, not a confirmed application or release-authorization defect. Suggested severity: Low for misleading evidence wording; no game/security impact established.

Baseline: `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Source SHA256s in `evidence/review-inventory-inspection/source-hashes.json`.

## Exact locations and reachable path

- `/Users/abdelrahman/Projects/parlor/scripts/generate_review_inventory.py:255–276,316–359,362–369,388–403`.
- `/Users/abdelrahman/Projects/parlor/docs/review/README.md:12–19,33–38`.
- `/Users/abdelrahman/Projects/parlor/scripts/release/validate_release_system.sh:25–28`.
- `/Users/abdelrahman/Projects/parlor/build.gradle.kts:65–70,139–148`.

`productionCheck -> productionReleaseAutomationCheck -> validate_release_system.sh -> generator --check` checks byte equality against regenerated inventory. Generation consumes indexed **path names**, post-baseline non-merge Git commit subjects/names, four trusted override rows, and path-derived metadata. It never reads the contents/blob identity or a reviewer attestation for ordinary source files. Every row is unconditionally assigned `REVIEWED` at line354; a newly indexed path with no committed history receives `None identified in the independent review` at line308. The success message calls these “reviewed rows.”

## Source-level proof and impact limits

With the same indexed path set, HEAD history, overrides and generator, modifying an existing tracked source file without committing cannot change generated content. Staging new bytes at an existing path also cannot change it. Therefore `--check` can remain green while the built working-tree bytes differ from the purported review snapshot. Running the documented generator after adding any new tracked path produces `REVIEWED` without a separate review record. These are deterministic properties of the actual input dataflow; no repository mutation or test execution was performed for this candidate.

This is not proof that prior humans failed to read those files, nor a claim the generator was intended to perform semantic review. The actual existing628-row CSV accurately enumerates all628 tracked paths, with no duplicate/missing/extra paths. The gap is that generated `REVIEWED` plus a freshness PASS cannot serve as mechanically verified line-by-line/source-identity evidence. README's “generator intentionally reviews all tracked items” overstates its behavior.

## Counter-evidence

- Excluding untracked files is deliberate, documented and tested; that exclusion is **not** the candidate.
- Full history is deliberately fetched for the gate. `--no-merges` deliberately stabilizes synthetic PR-merge output. These policies are not defects merely because they do not bind file bytes.
- README36–38 puts exact final SHAs/receipts in a separate report; findings register15–23 explicitly says the register does not replace a final exact-HEAD receipt. Actual report gives dated code matrices and external blockers.
- No code path was found that converts this CSV's `REVIEWED` field into Store authorization. Release tools have separate candidate/approval/identity guards; publication remains disabled. Do not label this a signing/promotion bypass.
- Existing tests verify repeatability/untracked exclusion, full override SHA shape and synthetic nonconflicting merge stability; they do not assert change-of-blob invalidation or manual review attestation.

## Suggested remediation / regression coverage

Label generated-only rows `INDEXED` or an equally truthful status and explicitly scope `--check` to inventory metadata. If a reviewed-source gate is desired, consume a separately maintained reviewer/range/blob-hash ledger, excluding only unavoidable self-referential evidence fields. Test same-path working-tree and staged edits, newly indexed files, and real merge-resolution changes. Do not “fix” this by claiming a generator can prove human review.

Origin: pre-existing behavior, retained through freshness commit `033a56351c57fe382aa5d42ff0cb1ea94385d0da`; not a new app regression. This audit's own coverage ledger is independent and is not derived from this CSV.

## Independent adjudication

Independent validator `/root/whodunit_cont` reopened the generator, tests, actual callers/guards and native build-membership source. Its full report was read by the finder after delivery: `validations/INV-C01-whodunit_cont.md`; exact reopened source identities/ranges are in `validations/INV-C01-C02-source-hashes-whodunit_cont.json`. It confirms the narrow evidence/documentation classification, not an application defect, accidental shipping test inclusion, privacy issue or Store authorization bypass. Deterministic source proof only; no repository mutation/test execution is claimed.
