# Tests / build / release — GAPS

## Not fully read

- `gradle/verification-metadata.xml` body (11075 lines). Header/policy +
  contract-test SHA samples only.
- `scripts/release/release_tool.py` / `store_api.py` beyond identity and
  execute-mode gates (~3k lines combined). Promotion recovery / digest
  readback not line-audited.
- `scripts/release/validate_{android,ios}_artifact.sh` after the identity
  / bound checks.
- `scripts/release/upload_ios_candidate.sh` after the identity assert.
- composeApp backup/data-extraction XML bodies.
- Every `*Test.kt` body outside the contract / Ignore / iosTest sample.
- Untracked `docs/release/` and `release/private/` (not opened).
- `release/mobile-release.json` after line 40.

## Commands not run

- `productionCheck`, `allTests`, `productionAppleCheck`,
  `productionIosSimulatorRuntimeTests`, `productionAndroidSigningCheck`
- any module `desktopTest` (python unittests were the only executed suite)
- `xcodebuild` (identities read from pbxproj/xcconfig, not `-showBuildSettings`)

## Questions this workstream did not close

1. Whether Linux `allTests` actually executes other modules’
   `androidUnitTest` compilations of commonTest, or only composeApp’s
   named `testDebugUnitTest` / `testReleaseUnitTest` plus desktop.
   `help --task allTests` lists module `allTests` reports; host-specific
   target inclusion was not dumped as a task graph.
2. Whether R8 drops `KtorRemoteCaseDataSource` (architecture workstream
   question). No mapping.txt inspected.
3. Whether GitHub environments `testing-candidate` / `production-*`
   actually have required reviewers (YAML names them; org settings are
   outside the tree).
4. Whether `origin/main` CI is currently green — local HEAD is
   `behind 35` per `git status`.
5. Whether dirty MOBILE_RELEASE iOS Automatic-on-Release default can
   produce a different signed IPA than `build_ios_candidate.sh` if
   someone archives from Xcode without the script’s overrides.

## Out of scope (other workstreams)

- Reducer / protocol / transport correctness
- Game-shell dispatch token false positives inside comments
- Store listing / privacy questionnaire content
