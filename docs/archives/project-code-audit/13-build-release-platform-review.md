# Build, release, platform

## Toolchain (from executable config)

- JDK 21, Gradle 8.13 wrapper, AGP 8.13.2, Kotlin 2.4.10
- Lint 9.1.1 + R8 9.1.41 pins (Kotlin 2.4 metadata)
- P2pKit 0.7.0-rc3 Maven Central only; strict verification checksums
- Version: `config/parlor-version.xcconfig` → 1.0.0 / build 1
- iOS min 16; Store-qualified Xcode 26.3 / 17C529 in CI `DEVELOPER_DIR`

## Identities — release blocker

`applicationId` / `BUNDLE_ID` = `com.parlor.app`.
`verifyApplicationIdentities` **requires** that literal.
`config/release-policy.json` marks both stores `blocked` /
`public_store_collision`.
`release_tool.py` hard-fails that ID even if status flipped to verified.

**There is no in-repo identity that can be uploaded.** Changing the ID
without changing every pin fails CI (F-001 / TB-001 / TB-013).

Debug suffix `.debug` is real and lets local installs sit beside a Store
app — once a real Store ID exists.

## Workflows

- `production-verification.yml` — live on PR/push; read-only; no store secrets.
- `testing-candidate.yml`, `testing-external-promotion.yml`,
  `production-promotion.yml` — **live `workflow_dispatch`**, not `if: false`.
  Publish gated by identity lock + environment approval (F-002 / TB-002).
  Rehearsal can still **sign** when publish=false if credentials exist.

GitHub “manually disabled” state is **not** visible in YAML. Do not assume
the files being present means they cannot run.

## Dirty tree vs HEAD

Uncommitted `composeApp/build.gradle.kts` adds `MOBILE_RELEASE_ANDROID_*`
aliases and `MOBILE_RELEASE_REQUIRE_SIGNING`. iOS Release signing maps to
`MOBILE_RELEASE_IOS_*`. Local signed builds can diverge from origin/main
(TB-011). Audit is of the **on-disk** tree.

## Packaging

- Android release: minify+shrink, keepattributes-only proguard, unsigned AAB
  in `productionCheck`.
- Desktop nativeDistributions declared, **ungated**.
- iOS: embedAndSign framework from Xcode; no CocoaPods.
- `iosX64Test` cannot run on Apple Silicon host.

## Verdict

Build system is unusually strict for an indie KMP app. **Store ship is
impossible** until identity migration. CI green is unsigned-source quality
only.
