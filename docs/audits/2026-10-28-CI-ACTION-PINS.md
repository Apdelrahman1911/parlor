# Reviewed CI action updates — PR #275

## Confirmed contract failure

Dependabot sourcea5fcc0b8/run40617936314 changes four published GitHub-owned
CI actions, but leaves the executable exact-pin contracts on old versions.
Windows, Linux ARM64 and Common/Android fail. Python reports pinned JDK21
mismatch and later mutation-error mismatches; Desktop guards also expect the
old setup/upload SHAs. This is a real update-integration defect, not permission,
runner cost, compilation or flaky gameplay. The now-unmergeable remaining jobs
were cancelled once; all eight cleanup receipt observations and original
failed/cancelled statuses are retained, never full workflow qualification.

## Provenance and compatibility review

Official GitHub repository tag references bind exactly to these immutable commits:

| Action | Published version / exact commit |
| --- | --- |
| setup-java |6.1.0 /`b843ebf9b913146b38da2eaa7aeddf76074ae1ad`|
| upload-artifact |7.0.2 /`cf430e030ddbb5b0abf93d22962f4752f3646cd9`|
| cache/restore and cache/save |6.1.0 /`55cc8345863c7cc4c66a329aec7e433d2d1c52a9`|

Both actual cache sub-action manifests, not just the root cache action, are
reviewed. All use Node24, as the current standard hosted runners already support.
JDK21 Temurin input and JAVA_HOME/cleanup behavior remain required. Restore's
`cache-hit` and upload's artifact ID/digest outputs remain available; save remains
a separate action with no hidden post-step. Exact reviewed manifests/commit/tag
receipts stay under untracked `.git/agent-custody/`.

## Implementation safeguards and verification status

Update the Python and Desktop source contracts to require the **new exact
reviewed SHA/version**, not any tag/SHA, prefix or permissive action owner.
Every upload-envelope, artifact/native/output custody, target/optimized Release,
full/diagnostic scope, strict dependency, cleanup and failed/skipped lane guard
remains. Existing fault/mutation suites must pass; no baseline or check removal.

No runner label, concurrency, dependency-cache key/path/fallback/write condition,
billing setting, cache cap or shipping dependency changes. Standard public-repo
runners remain free; no larger/paid infrastructure is introduced. Disabled Store
candidate/promotion gates remain disabled and no release is authorized.

The branch includes PR277's public-recovery candidate for testing, but277 is
still awaiting native qualification. Local focused/full verification, eventual
qualified277 integration, new exact-source hosted checks and protected merge
are required before accepting275. This report does not claim a successful run,
merge, security-policy decision, mobile-device result or benchmark winner.
