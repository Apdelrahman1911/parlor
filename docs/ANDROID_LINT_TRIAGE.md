# Android release lint triage

The executable source of truth is `:composeApp:verifyReleaseLintWarnings` plus
`config/android-lint-accepted-warnings.txt`. The task runs `lintRelease`, parses
the generated XML report, and requires an exact multiset match on canonical
issue kind, repository-relative location, dependency/current-version message,
and count. Volatile latest-available-version suffixes are ignored, including
the alternate `Newer version of lint available: <latest>` wording. Lint 9.1.1
can emit the same version-catalog update as either `GradleDependency` or
`NewerVersionAvailable` across identical forced runs, so only that pair is
canonicalized to `DependencyUpdate`; every other ID remains exact. This
document records why the current advisories remain; it does not override the
build.

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
corresponding update advisories. The current accepted inventory contains 35
warnings, all in the four
explicitly reviewed advisory classes below:

| Lint ID | Count | Disposition |
|---|---:|---|
| `OldTargetApi` | 1 | Target/compile SDK 36 is deliberately pinned for the current compatibility baseline. Store target-level compliance must be rechecked at release and can require a reviewed SDK migration. |
| `AndroidGradlePluginVersion` | 4 | Gradle 8.13 and AGP 8.13.2 are a verified pair. Moving to Gradle 8.14.5 or AGP 9.3.1 is a separate compatibility migration, not a lint-only edit. |
| `GradleDependency` | 1 | Lint 9.1.1 is the reviewed stable analyzer compatible with Kotlin 2.4 metadata; lint reports a moving, unrelated alpha preview whose latest version is normalized by the gate. |
| `DependencyUpdate` | 29 | These are update notifications for pinned Activity Compose, datetime, Kotlin, Compose, Navigation 3, Koin, Ktor, Konsist, Detekt, and Turbine coordinates. AndroidX Navigation 3 runtime 1.0.0 stays aligned with the reviewed Compose Multiplatform UI port; that UI port stays at alpha06 because newer publications omit the project's `iosX64` target. They do not report a demonstrated correctness defect. Kotlin 2.4.10 remains the compatibility pin; the three Kotlin plugin updates are reviewed below. Serialization 1.11.0 and P2pKit 0.7.0-rc3 did not produce update advisories in this reviewed run; that is not a claim they will always be latest. |

The remaining advisories are not permanent waivers. Dependency/security review
and store policy can make a specific upgrade mandatory. Such an upgrade must
update the reviewed inventory, strict dependency-verification metadata, and
pass the complete release matrix. A new coordinate, current version, source
location, canonical issue kind, or warning count fails the gate. A newer
latest-version suffix for an already accepted advisory is normalized; the
first update advisory for a previously current coordinate still requires
explicit review, as the Kotlin publication below demonstrates.

## 2026-09-07 Kotlin plugin advisory review

[Verification run 34112913001](https://github.com/Apdelrahman1911/parlor/actions/runs/34112913001)
reported exactly three new advisories for Kotlin's `multiplatform`,
`plugin.compose`, and `plugin.serialization` plugin IDs, with no missing
accepted rows. Kotlin [2.4.20](https://github.com/JetBrains/kotlin/releases/tag/v2.4.20)
was published that day. Accept only those exact source/ID/current-version
rows; retain Kotlin 2.4.10 pending a separately reviewed compatibility update.
No dependency upgrade or analyzer suppression accompanies this triage.

There is relevant security counter-evidence:
[GHSA-r937-wjx7-w2jp / CVE-2026-53914](https://github.com/advisories/GHSA-r937-wjx7-w2jp)
lists `org.jetbrains.kotlin:kotlin-gradle-plugin < 2.4.20-Beta1` as affected.
The [upstream fix](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6)
restricts deserialization of **KAPT incremental** `apt-cache.bin` and
`java-cache.bin`; the vulnerable reader remains in the 2.4.10 source.
It is not a claim about every Gradle cache. Parlor's declared module and
convention plugins do not apply KAPT or configure its processor runtime;
strict verification metadata contains no `kotlin-annotation-processing`
component. Thus this specific reader is outside the configured build path,
not repaired or generally safe merely because version 2.4.10 is pinned.

The accompanying no-KAPT source/metadata contract requires this scoped
security decision to be reopened before introducing KAPT or its processing
runtime. Re-review if upstream changes the advisory's affected-path analysis.
A future Kotlin update must preserve the existing toolchain, dependency
verification, and Android/iOS/Desktop compatibility checks. These three lint
rows are not a permanent security waiver, and passing lint is not a security
assessment of all dependencies.

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
