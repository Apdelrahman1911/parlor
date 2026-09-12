# RL-C2 — Independent remediation approval

- Reviewer: `/root/release_fix_review`; author: `/root` (different agents).
- **Conclusion: APPROVE — FIXED AND VERIFIED for the scoped local verifier defect.**
- Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, plus the preserved authorized worktree. Exact reviewed hashes and line ranges are in `RL-C2-independent-remediation-review-01.json`.

## Root cause and correction

The original strict verifier rejected an otherwise valid approved self-signed Android upload certificate (exit 4) before its fingerprint comparison. The new Java helper verifies every signed payload and checks every actual signer leaf against the approved SHA-256. It validates a self-signed leaf as a non-anchor PKIX path element, including the active JDK21 code-signing algorithm policy, before creating an ephemeral public-certificate-only trust store. The original successful `jarsigner -verify -strict -certs` remains mandatory. This does not waive exit 4 or infer trust from display text.

Timestamp trust is independently established against original JDK public roots with the TSA variant before its date can authorize the upload leaf; the newly trusted upload certificate cannot grant itself TSA authority. The isolated `user.home` prevents implicit private personal keystore reads. The original non-self-signed CA policy is not expanded. The helper's narrow internal API exports are validation-child-only and fail closed on an incompatible JDK.

I read all helper/shell/test lines, the original dossier, calling workflow boundaries, RL-C1/RL-C3 diffs/reviews, and relevant exact JDK21.0.11 implementations. Fresh official sources matched retained exact JDK bytes. Unsigned ordinary META-INF payload, weak algorithms, leaf expiry/usage, stale trust output, and metadata/directory counterexamples were examined. No blocking defect remains in this scoped diff.

## Executed evidence independently checked

`evidence/rl-c2-green-02`: **21 tests executed, 21 passed, zero skips** (20 synthetic signature/policy regressions and one actual owned-process timeout cleanup regression). The three current implementation/test hashes equal both recorded before/after manifests. The old positive approved-selfsigned failure is the real original witness; the additional red Java-stub failure is a new-plumbing oracle, not a second original defect.

The timeout regression starts a real redirected TERM-resistant descendant, and the finalizer now KILLs the owned group even after TERM caused the parent to exit. Synthetic stores/classes/artifacts are temporary and cleaned; green02 records Gradle stop exit 0, no running Gradle daemon, no remaining owned workers/outputs, and no cleanup errors. This reviewer ran no builds, tests, signing, Store operations, or device commands.

## Scope and evidence limits

The tests use disposable synthetic certificates, not Store credentials. Full positive trusted RFC3161 timestamped-JAR integration, PSS-specific fixtures, adversarial archive-limit runtime fixtures, a complete signed AAB/DEX validation and real Store signing remain separate evidence. The timestamp negative test is explicitly a synthetic CodeSigner policy-boundary test. No schema, application/game/session/protocol/content change occurs; approved identity and publication-disablement safeguards remain. Personal implicit custom trust is intentionally not part of the supported release validation policy. Broad repository/release-system checks remain root-owned.
