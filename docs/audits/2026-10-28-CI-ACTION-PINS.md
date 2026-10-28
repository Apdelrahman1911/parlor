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

PR #277 is now normally merged as `aea0e47f610d30cce0adbb3294c9428ac3015648`.
Its fresh PR run40624827444 independently qualifies all eight producers, final
join,20 Swift methods,677 KMP Simulator cases and source/artifact/native/output
custody. Fresh main40634650927 now independently qualifies the same complete
engineering gates and native case counts; its separate qualification receipt
SHA256 is`7720f919e013d81489a775267e428596fa5c404f3a7d5ab9c4814b63245911c9`.
The action-update candidate incorporates this actual qualified merge locally.

At previous clean action candidate `7d27d1e2`, complete strict
`productionCheck allTests --dependency-verification=strict --continue --no-daemon`
passes in3m14s including owned stop:1,817 Desktop cases (three existing LAN
skips),651 Android Debug +651 Release +112 GitHub-test cases,zero failures/errors;
749 release/Python controls (one known Linux environment skip),Detekt/lint/R8,
unsigned AAB and pinned shell tools pass. Valid reviewed caches are reused.
Log SHA256 `1937e33deb99710d144c6f02e5ea2413b8ef80bf1a20894689caa76924c8d57d`.
This is local engineering evidence, not Apple/device execution.

At clean candidate `a22efd93`, the same complete strict aggregate passes in
139.2s with the same1,817/651/651/112 platform counts,749 release controls and
disclosed skips. Source is unchanged and owned Gradle stop exits0. Log SHA256
`6f66d50ba3033a87e67dafe471d5295a08b9f36748c820bd5c8e8148c6273476`.
Valid caches are reused; this remains Linux engineering evidence, not Apple.

The same-source Intel comparison40639850885 is now independently fully
qualified,20 Swift/677 Simulator cases, all eight producers plus join and custody;
its receipt SHA256 is`e41a20cd8845c64530c7b4f0839446308fb9c9154b4c4bb4b1309f96c5ec91a5`.
It takes107.72min versus ARM52.55min, including all queues. Retain the already
qualified ARM default; no runner or exact runner-selection guard changes.
[The complete comparison](2026-10-27-FREE-HOSTED-CI.md#qualified-same-source-selection)
retains failed attempts, measured stages/cache hits and limitations.

The documented acceptance source33db4374 also passes the complete strict local
aggregate in181.2s with the same counts/skips, source unchanged and stop0.
Log SHA256`2799d44da67f14e83e718336dfee77be8c983fd07be4f761ee232c5452db9de7`.
These receipts are historical exact-source engineering evidence, not an assertion
that every later update has executed. Each final changed source must pass affected
strict/full checks, fresh hosted qualification and protected/post-merge verification.
Follow [PR275's live checks and retained merge/main receipts](https://github.com/Apdelrahman1911/parlor/pull/275)
for current integration, rather than inferring it from this source document.
No security-policy approval, physical-device result or Store release is claimed.
