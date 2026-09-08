# DOC-C4 — independent validation

- **Classification: DOCUMENTATION MISMATCH. Severity: Low.**
- Finder `/root/whodunit_cont`; independent validator `/root/session_cont`.
- Exact reviewed source: `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Root for all paths: `/Users/abdelrahman/Projects/parlor/`.

## Source-level proof

`docs/RELEASE_AUTOMATION.md:322–332`, specifically327, directly instructs branch-protection operators to require the **two** Production verification jobs. Reopened `.github/workflows/production-verification.yml:1–347` actually declares five separately named jobs: desktop-android24, desktop-linux-arm64113, desktop-macos-x64145, desktop-windows-x64177 and ios219. Each has its own real host selection and commands; no source aggregation makes three of these disappear as independent jobs. `docs/RELEASE_GATES.md:62–83` correctly enumerates the same five and their distinct host-selected checks. Thus the numerical operator instruction is deterministically stale, not a test-only or shipping gameplay issue.

## Counter-evidence and limits

Automation23,233–234 and294–299 correctly instruct all/every checks, and the policy is explicit that repository settings are external. These do not make the conflicting “two” instruction accurate, but reduce its impact. A reader following327 alone could omit host-specific checks. No authenticated live GitHub protection configuration was read, changed, or inferred from the document's historical2026-08-16 claims. This is **not evidence of a current weak ruleset, unauthorized merge, signing bypass or Store incident**. Checked-in disabled Store workflow guards and identity gates remain independent controls.

## Recommendation and verification

After separate authorization, remove the obsolete count or mechanically maintain a list matching the actual workflow job names; link the branch-protection instructions to that authoritative list. Test the documentation/workflow inventory consistency. Before any release enabling, separately read back live protection through an authorized read-only API operation. No application/configuration/workflow change, test/build, Store or Git mutation was performed by this reviewer.

Source hashes and actual reopened ranges: `validations/DOC-C4-source-hashes-session_cont.json` and coverage receipts. No runtime reproduction is needed to establish this narrow text/workflow mismatch.
