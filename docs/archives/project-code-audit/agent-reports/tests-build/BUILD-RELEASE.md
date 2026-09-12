# Build / release — what is wired, what is blocked

## Convention / module graph

`includeBuild("build-logic")`. Three plugins: `parlor.kmp.library`,
`parlor.kmp.compose.library`, `parlor.detekt`. Root applies Detekt to
every subproject. New KMP modules auto-join Detekt + desktopTest +
iosSimulatorArm64Test + allTests.

`:composeApp` is the only Android application. Libraries use the
convention (JUnit 5 / Java 21 / three iOS targets). Desktop is a JVM
harness (`compose.desktop`), not a store artifact.

P2pKit is always on the graph (`:shared:transport-p2p` included
unconditionally). Coordinates:
`io.github.apdelrahman1911:{p2p-core,p2p-transport-lan}:0.7.0-rc3`.

## Verification policy

`gradle/verification-metadata.xml` configuration:

```
verify-metadata = true
verify-signatures = false
```

CI always passes `--dependency-verification=strict`. There is no
repo-level `org.gradle.dependency.verification=off`. Checksums are the
gate; PGP is not.

Extra repo: `https://maven.pkg.jetbrains.space/public/p/compose/dev`.
No `mavenLocal()`, no composite P2pKit.

Lint/R8 are independently pinned (`9.1.1` / `9.1.41`) so analyzers
understand Kotlin 2.4 metadata while AGP stays `8.13.2`.

## Android packaging / signing

`composeApp/build.gradle.kts` (working tree):

- `namespace` / `applicationId` = `com.parlor.app`
- Debug: `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`
- Release: minify + shrink + `proguard-android-optimize.txt` +
  `proguard-rules.pro` (keepattributes only). `debugSymbolLevel = FULL`.
- Signing: env `PARLOR_ANDROID_*` **then** `MOBILE_RELEASE_ANDROID_*`
  (dirty) **then** `parlor.android.signing.*` properties.
- `MOBILE_RELEASE_REQUIRE_SIGNING=true` fails configuration if signing
  is incomplete (dirty). Unsigned `bundleRelease` remains the
  verification artifact when unset.
- `verifyStoreRelease` / `productionAndroidSigningCheck` are the signed
  path. **Not** part of `productionCheck`. Linux CI never sees a
  keystore.

`verifyApplicationIdentities` **pins** Store ID to `com.parlor.app` and
Debug suffix to `.debug`. Changing the colliding ID fails the Android
gate.

`verifyMergedReleaseManifest` pins package, permission set, no backup,
no cleartext, not debuggable, exported set =
`MainActivity` + `ProfileInstallReceiver`.

`verifyReleaseLintWarnings` requires lint XML ==
`config/android-lint-accepted-warnings.txt` (29 version-advisory rows).
A new correctness warning fails CI.

`verifyGameShellDispatch` greps App.kt / AppBackPolicy /
LocalResumeRouter / HomeScreen / `shell/multiplayer/**` for
`whodunit`, `mafia`, `com.parlor.games.`.

## iOS identities / schemes

One scheme: `iosApp`. Archive action = Release. No test targets.

| Config | PRODUCT_BUNDLE_IDENTIFIER | Signing (working tree) |
|---|---|---|
| Debug | `$(BUNDLE_ID).debug` → `com.parlor.app.debug` | Automatic, `DEVELOPMENT_TEAM=$(TEAM_ID)` |
| Release | `$(BUNDLE_ID)` → `com.parlor.app` | `MOBILE_RELEASE_IOS_*` (defaults Automatic / empty / `$(TEAM_ID)`) |

`TEAM_ID` is empty in-repo. Kotlin phase:
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode --dependency-verification=strict`.

CI unsigned simulator build asserts Release bundle `com.parlor.app` and
version macros from `config/parlor-version.xcconfig`.

Store IPA path (`build_ios_candidate.sh`) re-asserts identity approved,
forces Manual + `Apple Distribution`, rejects development/device
profiles. Still requires bundle `com.parlor.app`.

## Candidate / promotion: disabled or would publish?

**Not disabled.** No `if: false`. Files are the four reviewed YAMLs
(`workflow_contract.py` forbids extra workflows).

They **would publish** when all of:

1. Dispatch from the locked branch (`testing` / `release`)
2. `publish: true`
3. Protected environment approvals + secrets present
4. `assert-store-identity-approved` returns

(4) is currently impossible: policy status is `blocked` **and**
`identity == "com.parlor.app"` is a hardcoded fail. Rehearsal
(`publish=false`) still builds/signs in the candidate Android/iOS jobs
when state is not recovered; it does not call `--execute` on store APIs.

Promotion workflows contain no `./gradlew` / `xcodebuild` (contract-
enforced no-rebuild).

## Store identity `com.parlor.app`

Hard-pinned in:

- Gradle applicationId + `verifyApplicationIdentities` + merged manifest
- iOS `BUNDLE_ID` + CI `xcodebuild -showBuildSettings` + unsigned app plist
- `config/release-policy.json` store IDs
- `config/candidate-manifest.schema.json` `const`
- `validate_android_artifact.sh` / `validate_ios_artifact.sh` /
  `build_ios_candidate.sh`
- candidate workflow `[[ "$PACKAGE_NAME" == com.parlor.app ]]` and
  `[[ "$BUNDLE_ID" == com.parlor.app ]]`

Simultaneously unlistable:

- policy `store_identity_ownership.status = blocked`
- `release_tool.py:181` fail on that exact string
- `workflow_contract.verify_policy` refuses to mark it `verified`

This is a **config-level ship blocker**, not a missing workflow.

## Release blockers in config (executable)

1. `com.parlor.app` collision / blocked ownership (both stores).
2. Version still `1.0.0` / build `1` — first real Store upload would
   consume that code forever; policy uniqueness checks would then
   require a bump.
3. Empty `TEAM_ID` — local Release Automatic signing cannot store-sign
   without overrides; candidate supplies team via env.
4. `productionCheck` does not include Apple or signed Android. A
   Linux-only green is not a ship gate.
5. Verification signatures disabled — checksums only.
6. Compose Material3 on `1.9.0-beta03` (accepted lint advisory).
7. P2pKit `0.7.0-rc3` (pre-release coordinate, checksum-pinned).
8. Dirty MOBILE_RELEASE iOS Release mapping is uncommitted; HEAD CI
   still uses Automatic on Release in pbxproj.

## What CI does **not** prove (release-relevant)

- Ownership of `com.parlor.app` (it proves the opposite: blocked)
- Signed Play AAB or App Store IPA on PR/main
- Device runtime, instrumented UI, real LAN multiplayer
- R8 keep-correctness beyond “minify task succeeded”
- Desktop package artifacts (Dmg/Msi/Deb configured, never gated)
- That candidate/promotion are “off” — they are on and fail-closed
