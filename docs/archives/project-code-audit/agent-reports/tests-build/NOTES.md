# Tests / build / release — NOTES

CODE-ONLY. Reconstructed from Gradle, workflows, scripts, iOS project
files, and test source-set layout. No `*.md` outside `project-code-audit/`
was read as evidence (some *tests* parse those docs; that is recorded as a
finding). Production sources were not modified. No commit.

HEAD: `3f825f9`. Working tree is dirty; those diffs change signing gates
(see TB-011). Python release tests were run on the dirty tree: **116 passed**.
`productionCheck` / `allTests` were not executed (too large).
`./gradlew help --task allTests` was run (read-only).

## What CI actually invokes

### `production-verification.yml` (push/PR/dispatch on `main`/`testing`/`release`)

Linux `ubuntu-24.04` (`desktop-android`):

```
./gradlew productionCheck productionStaticAnalysis productionReleaseAutomationCheck allTests
  --dependency-verification=strict
```

Then unzip-tests the unsigned `bundleRelease` AAB and copies the merged
release manifest. No keystore. No Play upload.

macOS `macos-15` (`ios`):

```
./gradlew productionIosSimulatorRuntimeTests productionAppleCheck
  --dependency-verification=strict
```

Then `plutil` on Info.plist + PrivacyInfo, `xcodebuild -showBuildSettings`
for Debug/Release bundle IDs, and an **unsigned simulator** Release
`Parlor.app` (`CODE_SIGNING_ALLOWED=NO`, `ARCHS=arm64`). No device IPA.
The Apple job is contract-forbidden from running `allTests`.

Xcode pin: `DEVELOPER_DIR=/Applications/Xcode_26.3.app/Contents/Developer`,
`Xcode 26.3` / `17C529`, iOS SDK major ≥ 26. Checked against
`config/release-policy.json`.

### Store workflows (all `workflow_dispatch` only — **not disabled**)

| Workflow | Branch lock | `publish=true` does |
|---|---|---|
| `testing-candidate.yml` | `refs/heads/testing` + SHA == `origin/testing` | Sign AAB + IPA, upload Play internal + TestFlight internal, seal promotable manifest |
| `testing-external-promotion.yml` | `refs/heads/testing` | Promote recorded version (no rebuild) to Play external + TestFlight external |
| `production-promotion.yml` | `refs/heads/release` + tree == candidate tree | Promote to Play production; attach/submit App Store version |

`publish` defaults to `false` (rehearsal). Execute paths call
`assert-store-identity-approved`, which **hard-fails** `com.parlor.app`.
So today they cannot complete a mutation. They are still live, credentialed,
and would publish if that identity were ever marked verified *and* the
hardcoded collision check were removed.

## Gradle aggregates (root `build.gradle.kts`)

| Task | Actually depends on |
|---|---|
| `productionDesktopCheck` | every KMP `desktopTest` only. Description claims “plus the desktop application compile” — **that compile is not wired** |
| `productionAndroidCheck` | composeApp debug/release unit tests, `compileReleaseKotlinAndroid`, R8 minify, **unsigned** `bundleRelease`, lint + lint inventory, merged-manifest policy, `verifyApplicationIdentities` |
| `productionAndroidSigningCheck` | `verifyStoreRelease` (external keystore + `bundleRelease`). Not in `productionCheck` |
| `productionStaticAnalysis` | plain `detekt` on every subproject + build-logic + type-aware host Detekt (common/desktop/Android). Not Apple |
| `productionAppleStaticAnalysis` | type-aware iOS Detekt only |
| `productionAppleCheck` | Apple Detekt + `linkReleaseFrameworkIos{Arm64,SimulatorArm64,X64}`. Linkage ≠ runtime |
| `productionIosSimulatorRuntimeTests` | every `iosSimulatorArm64Test` |
| `productionReleaseAutomationCheck` | `scripts/release/validate_release_system.sh` (py_compile, unittest, `workflow_contract.py`, pinned actionlint + shellcheck) |
| `productionCheck` | desktop + Android unsigned + release-automation + static + `verifyGameShellDispatch`. **Not Apple, not signed Android, not `allTests`** |
| `allTests` | every module `allTests` (KotlinTestReport: all targets that exist on the host) |

`subprojects` auto-joins any new KMP module to Detekt, `desktopTest`,
`iosSimulatorArm64Test`, and `allTests`.

## Identity

Pinned Store ID on both platforms: **`com.parlor.app`**.

- Android: `applicationId = "com.parlor.app"`; Debug suffix `.debug`;
  `verifyApplicationIdentities` **fails if the Store ID changes**.
- iOS: `BUNDLE_ID = com.parlor.app`; Debug `$(BUNDLE_ID).debug`; Release
  `$(BUNDLE_ID)`. CI asserts those exact strings.
- `config/release-policy.json`: both `store_identity_ownership.status = "blocked"`,
  reason `public_store_collision`.
- `release_tool.assert_store_identity_approved`: if identity == `com.parlor.app`,
  fail even if someone flips the flag to `verified`.
- Candidate/promotion scripts also require vars `GOOGLE_PLAY_PACKAGE_NAME` /
  `PARLOR_APPLE_BUNDLE_ID` == `com.parlor.app`.

Result: the ID is both mandatory and unlistable. That is the Store ship-blocker.

Debug IDs (`com.parlor.app.debug`) are isolated correctly.

## Dirty tree vs HEAD (gate-relevant)

Uncommitted, not in HEAD:

- `composeApp/build.gradle.kts` — accepts `MOBILE_RELEASE_ANDROID_*` env
  aliases after legacy `PARLOR_ANDROID_*`; adds configuration-time
  `MOBILE_RELEASE_REQUIRE_SIGNING=true` fail-closed check; error text now
  prefers the new names.
- `iosApp/Configuration/Config.xcconfig` + Release stanza of
  `project.pbxproj` — Release signing mapped through
  `MOBILE_RELEASE_IOS_*` (defaults: Automatic / empty identity / empty
  profile / `$(TEAM_ID)`). Debug stanza unchanged (Automatic + `$(TEAM_ID)`).
- `scripts/release/tests/test_workflow_contract.py` — asserts the above.

HEAD CI candidate still sets `PARLOR_ANDROID_*` (still first in the chain).
Unsigned verification does not set `MOBILE_RELEASE_REQUIRE_SIGNING`.
`build_ios_candidate.sh` still command-line-overrides
`CODE_SIGN_STYLE=Manual` etc. Dirty changes are local Mobile-Release-Kit
hooks, not a disable of Store workflows.

Also dirty/untracked (not treated as product gates): `.gitignore`,
`AGENTS.md`, `docs/release/`, `project-code-audit/`, `release/`
(`mobile-release.json` restates blocked `com.parlor.app`; `release/private/`
not read).

## Version / toolchain pins

- `config/parlor-version.xcconfig`: `1.0.0` / build `1` — single source
  parsed by Gradle and `#include`d by Xcode.
- AGP `8.13.2`, Gradle `8.13` (wrapper SHA pinned), Kotlin `2.4.10`,
  lint `9.1.1`, R8 `9.1.41`.
- P2pKit `0.7.0-rc3` from Maven Central only (`io.github.apdelrahman1911`).
- Compose Material3 `1.9.0-beta03`.
- JDK 21. minSdk 26, compile/target 36.
- Detekt `1.23.7`, `maxIssues: 0`, no baseline.
- Dependency verification: metadata SHA-256 on, **signatures off**.
  Repos: google, mavenCentral, `maven.pkg.jetbrains.space/.../compose/dev`.
  No `mavenLocal()`.

## Tests run as part of this workstream

```
python3 -m unittest discover -s scripts/release/tests -p 'test_*.py' -q
→ 116 tests, OK (dirty tree, including new MOBILE_RELEASE contract tests)
```
