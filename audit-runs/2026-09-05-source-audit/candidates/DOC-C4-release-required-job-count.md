# DOC-C4 — Release branch-protection instruction names two checks instead of five

**DOCUMENTATION MISMATCH, Low — independently validated.** Finder `/root/whodunit_cont`; independent validator `/root/session_cont` (`validations/DOC-C4-session_cont.md`, full report read). No claim about the current live GitHub ruleset or a present release bypass.

Baseline: main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Absolute source root `/Users/abdelrahman/Projects/parlor/`.

## Exact mismatch and consequence

`docs/RELEASE_AUTOMATION.md:322–332` instructs the owner to configure protected branches and, at327, to require “the two **Production verification** jobs”. The actual `.github/workflows/production-verification.yml` declares five independent jobs:

-24–112 `desktop-android` — Common, desktop, and Android release;
-113–144 `desktop-linux-arm64`;
-145–176 `desktop-macos-x64`;
-177–218 `desktop-windows-x64`;
-219–347 `ios`.

`docs/RELEASE_GATES.md:62–83` also describes all five, including three host-selected native/dependency checks not subsumed by the original Linux-x64/Apple-arm64 pair. Thus the imperative numeric instruction is stale. An operator configuring protection literally from that subsection could omit host-specific verification checks. Source-level comparison is sufficient to prove the text inconsistency; no network or repository mutation is required.

## Counter-evidence and limits

Automation23,294–299 and the Gates document elsewhere correctly say every required check. These reduce the chance of operator error but do not make “two” current. The document's August16 live-configuration claims were not re-verified through an authenticated API in this audit. This candidate must not be presented as proof that a current ruleset permits an unsafe merge. Every Store workflow job is explicitly disabled and Store identity remains blocked. There is no reported signing or publication incident.

## Recommended remedy and coverage

After authorization, replace the stale count with a reference to all current named required workflow jobs, or list the actual five using a mechanically checked inventory. Cross-check branch-protection operator docs with the workflow job inventory. Re-verify actual protection independently through an authorized read-only API operation before enabling Store workflows. No code or workflow changes were made for this audit.

Independent conclusion: complete workflow347lines and Gates109lines agree on five jobs while Automation327 explicitly says two. Other all/every wording is counter-evidence reducing impact but does not resolve the numerical mismatch. No live branch-protection or Store defect inferred.
