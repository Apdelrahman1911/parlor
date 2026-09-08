# Release documentation and cross-fix review — addendum 01

Reviewer `/root/release_fix_review` (not the author). Read-only source review; no builds or source edits. Exact paths/hashes/ranges are in the matching JSON.

**Two wording corrections requested before approving the new prose:**

1. `RELEASE_AUTOMATION.md:138–139`: code-signing purpose checks are retained by mandatory **subsequent** strict jarsigner, not performed before temporary trust creation by generic PKIX. Certificate validity, critical-extension and code-signing algorithm policy precede trust; purpose checks must still pass afterward. Production behavior is secure, but the prose assigns the check to the wrong stage.
2. `:149–150`: uncommitted edit deletion is best-effort. `store_api.py:691–696` reports deletion failure rather than guaranteeing cleanup. Say cleanup is attempted and failures reported.

**No new blocking code interaction found** across RL-C1 size gates, RL-C2 signer/certificate/strict verification and existing candidate metadata, RL-C3 same-edit preconditions/mutation/readback, updated mocks, or IOS-B1 phase/test integration. Publication/identity and no-blind-retry safeguards remain unchanged. All new Python test files enter the existing `test_*.py` discovery path; the source-mode Java helper remains release-test/tooling-only. Broader release-system execution is pending/root-owned, not claimed PASS here.

The historical generated review-inventory gate only examines tracked Git/index/history; it cannot establish coverage of untracked implementation files or modified bytes. Preserve task-specific before/after source hashes as the authority. This is an evidence limitation, not an authorization to rewrite prior audit records.
