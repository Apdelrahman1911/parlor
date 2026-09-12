# Historical remediation plan and project handoff review

Reviewer `/root/mafia_cont`; baseline `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846` / tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. No tracked modifications on resumed identity check. Assignment is documentation-only full reading; implementation claims are not reused as correctness evidence. No builds, tests, applications, servers, simulators, signing or Store operations by this reviewer.

## Complete assigned reading coverage

- `docs/P2P_REMEDIATION_PLAN.md`: all lines 1–1769 read in numbered chunks. SHA256 `c4e799454ca97e152f0ca676fa02573fc2e06e8ff2dae8bbae808f90250c8569`.
- `docs/PARLOR_PROJECT_HANDOFF.md`: all lines 1–1184 read in numbered chunks. Pre-existing untracked documentation, preserved. SHA256 `994c9e0c2965adf794043d336ad9c3280e77684d4db3d5a605eb35d40147fbde`.

Total: two documentation files, 2,953 lines. Complete document reading is not a new complete source review of every linked implementation.

## Dispositions and limits

- Remediation plan explicitly identifies a historical pre-remediation P2pKit 0.7.0-rc2 baseline. Old failures, source line anchors, branch, local sibling-library observations, and proposed logical-state architecture do not establish current 0.7.0-rc3/protocol 4.2 behavior. No old issue is copied into this audit's confirmed findings. No sibling library repository was inspected.
- The plan correctly distinguishes transport writes from application acceptance and physical/network evidence from source/API assertions; actual current implementations remain primary. Current source already read elsewhere in this audit is used for bounded claim comparison, not old PASS/FIXED labels.
- Reading these two documents does not itself supply runtime, release, physical-device or toolchain evidence.

## Remediation-plan disposition

- Sections 4–8 and 11–13 describe a proposed protocol-v2/feature-negotiation envelope, exact proposed resource budgets, orchestrator ownership, secure credential transactions, discovery scheduling, tests and a dependency-aware implementation sequence. These are not current constants or unimplemented requirements merely because the final architecture chose different names/values. Actual exact protocol 4.2 and present bounded owners remain authoritative.
- Every detailed historical P2P-01 through P2P-15/REL-01 root-cause, edge-case, regression, acceptance and evidence block was read. No historical severity/confirmation label is accepted in place of reopening current reachable code. Structured cancellation, commit receipts, finite clocks, unknown-vs-operational permissions and first-contact identity are appropriate areas for current audit evidence, not proof supplied by this plan.
- Sections 9–10 and 14–15 explicitly require physical and owner evidence. PHY-11 is conditional on an owner-approved manual-connect feature; the header and later decision resolve it unsupported. No need to implement manual endpoints, timers, migration, publication, or speculative descriptors to satisfy an obsolete branch of this blueprint.
- This document retains old names, proposed deadlines and planned release claims intentionally, under its historical banner. No new documentation defect is inferred from those historical differences.

## Handoff disposition and bounded source comparison

- The baseline SHA/tree match the resumed checkout, but its GitHub counts, PR/CI receipts, preserved stash identifiers and previous verification counts remain dated claims. This reviewer did not refresh GitHub or inspect stash/private material. The new audit uses its own inventory/results rather than the handoff's 628-row map or prior successful CI.
- Module/registration description was compared with source already read in this audit: actual root/KMP wiring, GameRegistry, GameShellRegistry and test fixture, composition-root ContentModule, Mafia/core/engine/design-system builds. The handoff correctly calls the fixture nonshipping and admits package-level domain imports do not all satisfy the stronger core/engine-only prose ideal. No gameplay defect is manufactured from that stylistic discrepancy.
- Mafia rules/defaults, unsupported timers, local handoffs, retained LAN host progression, pure reducer authority and deliberate public final-role/day-ballot disclosure agree with this reviewer's complete Mafia source reading. Whodunit authored catalog/content descriptions agree with the previously read validators/catalog/resources cross-check; complete Whodunit behavior proof remains the assigned Whodunit reviewer's work.
- Content cache/offline/signature claims agree with freshly read repository/validators in the product-doc assignment. The normal load-vs-refresh advertised-summary distinction and IntRangePair scalar coercion caveats remain in `product-docs-mafia-cont.md`; neither was elevated to a shipping defect without a reachable harmful consequence.
- Design-system language/motion/safe-area claims match their implementation shape; device rendering, native RTL gestures, semantics behind app-switcher covers and platform display geometry still require their own evidence. The description of stable locale composition does not disprove DS-C01 preference ownership. The reference to shared toast/row controls does not prove their correctness (DS-C02/M-C03 are separate candidates/findings).
- The handoff's broad snapshot-validation/history claims cannot be read as exhaustive proof: M-C01 specifically demonstrates a missing later-Doctor-history consistency invariant despite many existing validators. Likewise, a source-coverage label or list of representative tests cannot establish all scenarios or physical LAN behavior.
- Permission, cryptography/platform persistence, wire admission/rejoin/concurrency, release provenance and exact native-toolchain claims rely on their separately assigned source reviews and root verification/research. This documentation-only continuation does not independently re-certify those implementation details.
- Navigation3 is described as already implemented, not a migration request; future-agent remediation instructions do not override the current audit-only authorization. Store identity migration, disabled publication and physical two-/three-participant proof are explicitly unresolved. No old “integrated” label closes a current audit candidate.

## Result and residual limits

No new candidate originated from these two documents. Their historical/date-bound framing and explicit evidence limits avoid treating prior results as current proof. Existing findings and unresolved implementation candidates remain in their separate registers. No private data, application source, tracked files, branches or release state changed. Final resumed `git status --short` still shows only preserved untracked AGENTS.md/audit-runs/design/handoff/project-code-audit paths. No task-owned build outputs or processes were created by this reading cycle; root owns the sole build lane and cleanup.
