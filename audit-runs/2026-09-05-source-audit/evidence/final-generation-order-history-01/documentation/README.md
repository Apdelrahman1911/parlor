# Independent source-first audit — 2026-09-05

Audit-only workspace. Application, configuration, branches, stashes, prior reports, and user work must remain unchanged. No prior report or successful build establishes correctness here.

## Deliverables

- [Final assessment and project model](FINAL_REPORT.md) — **NOT READY**; no fixes implemented.
- [File-level coverage](COVERAGE.md) and [machine ledger](coverage/FINAL_COVERAGE.jsonl).
- [Independently confirmed findings](FINDINGS.md).
- [Rejected, blocked, documentation and test candidates](OTHER_CANDIDATES.md).
- [Verification gates](VERIFICATION.md) and [machine ledger](verification-ledger.json).
- [Research and cleanup](RESEARCH_AND_CLEANUP.md).
- [Exact continuation checkpoint](CONTINUATION.md) for unfinished behavioral/device/owner-dependent verification.

Reading completion is not end-to-end runtime or Store certification. The linked
execution and preservation receipts, not this index, determine the final state.

## Execution phases

1. Pin checkout, dirty inputs, toolchain, safe exclusions, fresh file inventory, and executable build graph.
2. Read all applicable first-party implementation/test/build/resource text with per-file, per-range receipts; inspect non-text assets appropriately. Trace complete local/host/peer and platform flows.
3. Investigate candidates, seek counter-evidence and exact-version authoritative references, and obtain a separate reviewer's validation for every candidate before confirmation.
4. Use one root-owned build lane for focused and broader checks. Preserve compact evidence, stop Gradle immediately, clean only generated/task-owned outputs, stop cleanup daemons, and verify cleanup after every cycle (including failure).
5. Assemble confirmed findings, rejected/unconfirmed register, verification/research/cleanup ledgers, final source comparison, and an exact continuation checkpoint if any scope remains unfinished.

## Evidence rules

- `coverage/inventory.jsonl` is a fresh filesystem/Git inventory, **not** reviewed coverage.
- `coverage/reviews-*.jsonl` records only actual completed reads/ranges, hash, reviewer, cross-file paths, reasoning/evidence, and uncertainties. A read with unresolved important behavior is not a completed behavioral review.
- Agents may write only this task directory. They must not run Gradle/Xcode, launch apps, or change application files. Reproducers stay under this directory and must not be silently added to shipping/test source sets.
- Candidate files must name originator and independent validator, expected/actual behavior, reachable path, counter-evidence, and evidence. Findings without independent validation remain unconfirmed.
- Private release inputs, local credential/signing files, real player stores, dependencies/caches, and unrelated local IDE data are excluded. Their code-level handling remains in scope.
- Existing `project-code-audit/` and review/handoff documents are not reused as coverage or correctness evidence.

Initial checkout: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Pre-existing untracked paths: `AGENTS.md`, `design/`, `docs/PARLOR_PROJECT_HANDOFF.md`, `project-code-audit/`.
