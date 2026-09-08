# Additive follow-up status — 2026-09-07

Reviewer: `/root/release_fix_review`. This supplements, rather than rewrites,
the earlier map and receipts. It is not a whole-project percentage or readiness
verdict. Root owns the final source freeze, execution ledger, and gate decisions.

## Completed since the original map

- **Android font packaging observation completed.** Root explicitly supplied
  the staged disposable-signed `release.apk`. Its SHA-256 is
  `2cbcd12b525d949cc2cd03c82560946d3e9b7216ef86c7e3e08beaa9bc5a7308`.
  Both font entries are byte-identical to the reviewed source assets, preserving
  the embedded copyright/OFL metadata. The earlier iOS simulator-bundle
  observation already established the same narrow result on that artifact.
  See `font-android-apk-inspection-01.json` and
  `font-ios-bundle-inspection-01.json`. This does not certify rendering,
  accessibility, all assets' licensing, or a Store package.
- **Android font observation/source receipt binding checked.**
  `font-android-source-binding-01.json` verifies the recorded manifest digest,
  before/after source equality, stage-to-outer source fields, package hash and
  root-reported successful packaging/cleanup evidence. This is mechanical
  receipt verification, not a new build or manual review of every manifest row.
- **MT-T1 exact draft applied after root's independent source review.** The
  namespace-aware, hardened DOM helper now checks primary SHA-256 values for
  unique exact component/artifact nodes rather than searching sibling text.
  Both provenance suites call it; 18 deterministic helper tests were authored.
  Root reviewed all helper/test lines and both caller diffs before explicitly
  authorizing application. `local-gap-remediation-01/mt-applied-freeze-01.json`
  binds the exact patch and four source hashes. The author did not execute the
  tests; root's focused/combined receipts must establish execution.

## Current limits and remaining work

- **DOC-C4 / INV-C01:** the source, tests and documentation corrections are
  authored and frozen for root's coordinated lane. The original generated CSV
  remains preserved. Do not label the inventory freshness check successful
  before coordinated regeneration and execution; do not relabel mechanical
  inventory as independent source review.
- **Android runtime attempt 01 was not successful.** It failed during process
  ownership inspection of `emulator -version`, before AVD creation or Android
  app launch. A successful NULL task-name port was incorrectly called a denied
  lookup. Exact-host XNU source permits this result for task teardown or copyout
  failure; it does not prove a particular observed process exited. Root's
  additive cleanup supplement records absence of recorded owned workers/groups
  and removal of exactly the attested generated files. It does not turn the
  original runtime failure into a passing test.
- **A bounded fail-closed control correction is drafted, not approved here.**
  `../android-arm64-runner/darwin-null-port-retry-01/` holds source research,
  the isolated helper patch and 20 new pure tests. The unchanged original 20
  process tests and 21 runner tests must also execute (61 expected total).
  Persistent live/unattestable processes and genuine native errors still fail;
  lifetime/audit-token ownership and the ban on numeric signaling are unchanged.
  Root/independent review and root-owned execution are required before retry.
- **iOS overall-runner status is separate from font packaging.** The narrow
  font observation remains valid; it does not override root's later app-host
  evidence/cleanup parser failure. Use root's newest explicit native receipts.
- **Linux VZ verification is not yet claimed.** An isolated, no-host-mount
  proposal exists; no VM was created by this reviewer. Lack of an executed
  attempt is not itself a proved platform blocker.
- **External IDs in the prior map remain owner gates or routing notes.** Do
  not silently mark physical LAN, signed Store qualification, identity/legal
  ownership, live branch protection or full accessibility journeys as passing.
  Root must reconcile later authorized WD-C4 policy and test-source moves with
  current source instead of preserving obsolete unresolved/desktop-only labels.

## Preservation and cleanup

Original failed-run receipts, original audit reports, the pre-fix inventory,
human finding overrides, dependency-verification metadata and all user work
remain untouched by this follow-up. This reviewer started no Gradle/Xcode,
emulator, VM, signing process, test worker or persistent background service.
Bounded public-source GETs finished; downloaded source was not retained beyond
compact excerpts. No source or APK was deleted. Root retains both staged APKs
only for its owned runtime retry and controls their final cleanup.
