# Android release lint triage

The executable source of truth is `:composeApp:verifyReleaseLintWarnings` plus
`config/android-lint-accepted-warnings.txt`. The task runs `lintRelease`, parses
the generated XML report, and requires an exact multiset match on lint ID,
repository-relative location, dependency/current-version message, and count.
Only volatile latest-available-version suffixes are ignored, including the
alternate `Newer version of lint available: <latest>` wording. This document
records why the current advisories remain; it does not override the build.

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
warnings. Removing eleven unconsumed dependency declarations then removed the
corresponding update advisories. The current accepted inventory contains 32
warnings, all in the four
explicitly reviewed advisory classes below:

| Lint ID | Count | Disposition |
|---|---:|---|
| `OldTargetApi` | 1 | Target/compile SDK 36 is deliberately pinned for the current compatibility baseline. Store target-level compliance must be rechecked at release and can require a reviewed SDK migration. |
| `AndroidGradlePluginVersion` | 4 | Gradle 8.13 and AGP 8.13.2 are a verified pair. Moving to Gradle 8.14.5 or AGP 9.3.1 is a separate compatibility migration, not a lint-only edit. |
| `GradleDependency` | 3 | Lint 9.1.1 is the reviewed stable analyzer compatible with Kotlin 2.4 metadata; lint reports a moving, unrelated alpha preview whose latest version is normalized by the gate. Activity Compose 1.9.3 remains pinned pending a lifecycle/source compatibility migration. AndroidX Navigation 3 runtime 1.0.0 stays aligned with the reviewed Compose Multiplatform UI port. |
| `NewerVersionAvailable` | 24 | These are update notifications for pinned datetime, Compose, Navigation 3, Koin, Ktor, Konsist, Detekt, and Turbine coordinates. The Navigation 3 UI port stays at alpha06 because newer publications omit the project's `iosX64` target. They do not report a demonstrated correctness defect. Kotlin 2.4.10, serialization 1.11.0, and P2pKit 0.7.0-rc3 are current and no longer appear in this class. |

The remaining advisories are not permanent waivers. Dependency/security review
and store policy can make a specific upgrade mandatory. Such an upgrade must
update the reviewed inventory, strict dependency-verification metadata, and
pass the complete release matrix. A new coordinate, current version, source
location, ID, or warning count fails the gate; a repository publishing a newer
latest version does not cause nondeterministic failure by itself.

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
