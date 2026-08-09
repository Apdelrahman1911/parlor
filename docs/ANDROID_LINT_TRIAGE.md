# Android release lint triage

The executable source of truth is `:composeApp:verifyReleaseLintWarnings`.
That task runs `lintRelease`, parses the generated XML report, and fails the
production Android gate for every warning ID that is not in the reviewed
update-advisory allowlist. This document records why the current advisory
classes remain; it does not override the build.

## 2026-08-09 baseline and result

The forced baseline run at `37a249676fd8d6de109800cd136352bdd55e32ee`
reported 59 warnings:

| Lint ID | Count |
|---|---:|
| `AndroidGradlePluginVersion` | 4 |
| `GradleDependency` | 2 |
| `IconDuplicates` | 5 |
| `IconLauncherShape` | 10 |
| `MonochromeLauncherIcon` | 2 |
| `NewerVersionAvailable` | 32 |
| `ObsoleteSdkInt` | 1 |
| `OldTargetApi` | 1 |
| `UnusedAttribute` | 1 |
| `UseTomlInstead` | 1 |

The remediation removes all 20 correctness, packaging, resource, and catalog
warnings. A forced post-change run reports 39 warnings, all in the four
explicitly reviewed advisory classes below:

| Lint ID | Count | Disposition |
|---|---:|---|
| `OldTargetApi` | 1 | Target/compile SDK 36 is deliberately pinned for the current compatibility baseline. Store target-level compliance must be rechecked at release and can require a reviewed SDK migration. |
| `AndroidGradlePluginVersion` | 4 | Gradle 8.13 and AGP 8.13.2 are a verified pair. Moving to Gradle 8.14.5 or AGP 9.3.1 is a separate compatibility migration, not a lint-only edit. |
| `GradleDependency` | 2 | Kotlin/Compose compiler 2.3.21 and Activity Compose 1.9.3 are pinned. Their upgrades require source, binary, Android lifecycle, and release regression verification. |
| `NewerVersionAvailable` | 32 | These are update notifications for pinned Kotlin, serialization, datetime, Compose, Koin, Ktor, SQLDelight, Detekt, JUnit, and Turbine coordinates. They do not report a demonstrated correctness defect. |

The remaining advisories are not permanent waivers. Dependency/security review
and store policy can make a specific upgrade mandatory. Such an upgrade must
update strict dependency-verification metadata and pass the complete release
matrix; advisory counts may also change as repositories publish new versions.

## Fixed warning classes

- `UnusedAttribute`: removed `android:hasFragileUserData`, which has no effect
  at the app's minimum SDK.
- `UseTomlInstead`: moved Activity Compose to the version catalog without
  changing its pinned version.
- `ObsoleteSdkInt`: moved adaptive launcher definitions from an API-26
  qualified directory to `mipmap-anydpi`; minSdk 26 makes the qualifier
  redundant.
- `IconLauncherShape` and `IconDuplicates`: removed redundant legacy bitmap
  launcher/round-launcher copies. Every supported Android version uses the
  adaptive icon definitions.
- `MonochromeLauncherIcon`: added a dedicated vector monochrome layer to both
  launcher variants for themed icons.

## Enforcement and evidence

Run:

```text
./gradlew :composeApp:verifyReleaseLintWarnings \
  --dependency-verification=strict --rerun-tasks --no-daemon
```

A successful forced run must execute `lintRelease` and
`verifyReleaseLintWarnings`. `productionAndroidCheck` and therefore
`productionCheck` depend on the verifier. The generated evidence is
`composeApp/build/reports/lint-results-release.xml` plus its HTML companion.
CI uploads those reports even on failure.
