# Release documentation add-on review — /root/whodunit_cont

Baseline main3625d0663ba6eb51338cbd5f9dc45f859ec18846 / tree db7f3d2afe73a13628296daee2cce71165eebc8d; no tracked edits. Review date2026-09-05. Full paths are relative to `/Users/abdelrahman/Projects/parlor` below; hashes/ranges are in coverage/reviews-whodunit_cont.jsonl.

## Complete reads

- docs/RELEASE_AUTOMATION.md:1–618
- docs/RELEASE_GATES.md:1–109
- docs/RELEASE_RUNBOOK.md:1–203
- docs/STORE_METADATA.md:1–85
- docs/release/MOBILE_RELEASE_KIT_MIGRATION.md:1–70
- config/android-lint-accepted-warnings.txt:1–39

These1124lines were read completely. Documentation is not runtime, signer, public-Store, GitHub-protection or device evidence.

## Source reconciliation

The previously read four complete workflows, all assigned release scripts/tests/schema and exact-source helpers remain the executable authority (reviews/release-whodunit_cont-notes.md; coverage/release-whodunit_cont-closeout.json). All three Store workflows' jobs are guarded `always() && false`; release policy identity checks also fail closed. No commands in credential handoff examples were run. Authenticated Store/GitHub operations, private handoff contents, signing material, profiles and tester data were excluded.

The root build was reopened fully1–204: productionCheck aggregates desktop, Android, release-automation and static gates; Apple runtime/linkage is separate. productionAndroidRuntimeCheck is a separate managed-device gate, not part of productionCheck. Ordinary desktop compile, release framework linkage, simulator tests and actual signed delivery remain different evidence classes.

The shared version source still contains1.0.0/build1. The shadow release/mobile-release.json was reopened fully1–64 and agrees with the migration doc's blocked identities, Android/iOS only, version-file source, no Firebase, EN-US initial Store metadata and read-only/preflight test hooks. Existing scripts remain authoritative; absence of a shared-kit caller is intentional, not an implementation defect. Historical testing/release ref and live-protection claims in the docs were not adopted as current evidence.

The lint allowlist was read fully. Its32non-comment entries scope advisory type/path/message; it is not a blanket correctness-warning suppression. Actual composeApp/build.gradle.kts337–424 parses lint XML with entity/DTD protections, normalizes narrowly defined equivalent update IDs and volatile latest-version suffixes, checks exact sorted duplicate-preserving inventory and fails for added or removed warnings. Root owns current execution results; do not relabel past reports as fresh passes.

Store metadata is explicitly a draft: fees, audience, ratings, URLs, final screenshots and legal declarations require owner approval. It accurately distinguishes local-network play/pass-and-play and no account/backend/ads/IAP from Store adjudication. Current settings do offer language, appearance and reduced-motion controls, so the screenshot-plan feature list is not invented. Final runtime visual/accessibility evidence remains separate.

## Findings and remaining limitations

- DOC-C4 is independently validated Low DOCUMENTATION MISMATCH by session_cont (`validations/DOC-C4-session_cont.md`, full report read): RELEASE_AUTOMATION327's imperative “two Production verification jobs” conflicts with five actual jobs and RELEASE_GATES62–83. No claim about live ruleset safety.
- RL-C1/RL-C2/RL-C3 are already independently confirmed latent release-code defects, not new findings per each affected prose claim. The docs describe intended retry/track safety more strongly than those code paths currently guarantee; keep them grouped under their original causes.
- DOC-C1 already records the independent local-snapshot display-name retention inventory mismatch; Store metadata56 merely says names are used for local sessions, and61 explicitly defers final disclosure, so it is not a second defect count.
- Public identity collision, live environment protection, branch rules, signing, Store memberships/review, legal notices/export/privacy, source-to-binary publisher trust and physical accessibility/network matrices remain owner-dependent or unverified. Local docs can list these gates but cannot pass them.

No production/build/workflow/doc sources changed. No build, test, app, server, simulator, signing or Store process was started by this add-on review. Evidence-only writes; root owns all build-lane stop/cleanup records.
