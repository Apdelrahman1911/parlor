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
corresponding update advisories. The current accepted inventory contains 37
warnings, all in the four
explicitly reviewed advisory classes below:

| Lint ID | Count | Disposition |
|---|---:|---|
| `OldTargetApi` | 1 | Target/compile SDK 36 is deliberately pinned for the current compatibility baseline. Store target-level compliance must be rechecked at release and can require a reviewed SDK migration. |
| `AndroidGradlePluginVersion` | 4 | Gradle 8.13 and AGP 8.13.2 are a verified pair. Moving to Gradle 8.14.5 or AGP 9.3.1 is a separate compatibility migration, not a lint-only edit. |
| `GradleDependency` | 1 | Lint 9.1.1 is the reviewed stable analyzer compatible with Kotlin 2.4 metadata; lint reports a moving, unrelated alpha preview whose latest version is normalized by the gate. |
| `DependencyUpdate` | 31 | These are update notifications for pinned Activity Compose, datetime, Kotlin, Compose, Navigation 3, Koin, Ktor, Konsist, Detekt, Turbine, and Serialization coordinates. AndroidX Navigation 3 runtime 1.0.0 stays aligned with the reviewed Compose Multiplatform UI port; that UI port stays at alpha06 because newer publications omit the project's `iosX64` target. They do not report a demonstrated correctness defect. Kotlin 2.4.10 remains the compatibility pin; the three Kotlin plugin updates are reviewed below. The Serialization 1.11.0 updates are reviewed below; P2pKit 0.7.0-rc3 did not produce an update advisory in this reviewed run, not a claim it will always be latest. |

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

## 2026-10-25 Serialization advisory review

[PR #256 verification run 40145248452](https://github.com/Apdelrahman1911/parlor/actions/runs/40145248452)
failed `verifyReleaseLintWarnings` with exactly two new
`NewerVersionAvailable` warnings: `kotlinx-serialization-json` and
`kotlinx-serialization-cbor`, both pinned at 1.11.0. The uploaded release XML
contains 37 warnings and no missing previously accepted rows. Accept only
these two exact coordinate/current-version rows; the verifier is unchanged.

Upstream [1.12.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0)
was published on October 19 and uses Kotlin 2.4.10. Its release notes describe
JSON exception wrapping and nesting-depth limits, parsing performance changes,
CBOR definite-collection memory optimization, and unsigned-integer encoding
changes. Compiler compatibility alone does not qualify these changes against
Parlor's persisted snapshots, exact multiplayer protocol, or Apple binaries.
Retain 1.11.0 for this integration; evaluate the upgrade separately with strict
artifact provenance, old/new codec fixtures, malformed/deep input tests, and
Android/iOS/Desktop execution. P2pKit's reviewed dependency alignment is
recorded in `P2PKIT_MAVEN_PROVENANCE.md`.

GitHub's public Maven advisory queries for the JSON and CBOR coordinates at
1.11.0 returned no matching advisories on October 25. This is a scoped lookup,
not proof of safety: upstream's depth/allocation hardening still warrants
malformed-input review. Reopen this triage for a relevant security advisory or
a reproduced decoder defect. No dependency, verification checksum, lint
severity, detector, or release gate is changed by this acceptance.

## 2026-10-25 Compose compiler coordinate advisory review

The fresh local `productionCheck allTests` report at `57be2cc6` contains 38
warnings: one additional `GradleDependency` row for
`org.jetbrains.kotlin:compose-compiler-gradle-plugin` 2.4.10, suggesting 2.4.21,
and no missing accepted rows. This is the concrete artifact behind the already
reviewed Compose plugin alias, not a new production dependency. Accept only
that exact coordinate/current-version row; leave the exact-multiset verifier,
Kotlin pin and strict artifact verification unchanged.

Upstream [2.4.21 release notes](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21)
(published October 8) describe Wasm, reflection, JavaScript, serialization,
Native build and scripting fixes. Kotlin/Compose compiler plugins must move
with the coordinated Kotlin toolchain, not as an isolated lint repair. The
public Maven advisory query for this artifact at 2.4.10 returned no matches
on October 25; that scoped lookup is not proof of general safety. The KAPT
advisory and no-KAPT gate above remain applicable and must not be removed.
Reopen the decision for relevant security evidence or a qualified toolchain
upgrade. Earlier 37-warning CI receipts remain historical, not substitutes
for this report's 38-row qualification.

## 2026-10-26 Redundant compiler classpath declaration removed

[PR #261 run 40152548602](https://github.com/Apdelrahman1911/parlor/actions/runs/40152548602)
and PR #262's matching run reported only 37 warnings: the additional concrete
compiler artifact advisory was absent, with no unexpected warnings. The
artifact and plugin-marker advisories use separate metadata lookups; their
appearance is not a reliable proxy for whether a dependency is resolved.
The reviewed [Lint 9.1.1 source archive](https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-checks/32.1.1/lint-checks-32.1.1-sources.jar)
has SHA-256 `864943a1c096806b77b1719d2016866b1f5baea8bdac405cd43d5f6ba76f71fa`.
`GradleDetector.checkDependency` takes the maximum of remote metadata and
`findCachedNewerVersion`; `getMavenMetadata` queries the plugin portal for
plugin markers and Central for concrete artifacts. This workstation already
has compiler artifacts 2.4.20/2.4.21 in its shared cache; hosted source did not
produce their library-alias warning. No shared cache was deleted or seeded to
alter verification.

Source review found the concrete catalog library was used only by a redundant
`compileOnly` declaration. The convention plugin applies Compose compiler by
ID and imports none of its implementation classes. Remove that unused
declaration and library alias, not the compiler: the root retains
`alias(libs.plugins.compose.compiler) apply false`, the same Kotlin 2.4.10
version reference, module plugin application, and artifact verification.
The plugin-ID advisory remains mandatory. Its removed library-alias advisory
is no longer an applicable source finding, so the exact inventory returns to
37 rows. The verifier, warning severities, and strict checks are unchanged;
there is no optional-warning list or allowance for missing findings.

Regression contracts enforce this classpath arrangement. Fresh convention
compilation and release lint plus the full cross-platform matrix are required
to qualify it; the preceding 37/38-warning receipts remain historical.

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

### Checkout-owned partial-analysis cache

AGP 8.13.2 marks `AndroidLintAnalysisTask` cacheable and omits its project
directory from analysis inputs. With pinned lint 9.1.1, root-level Gradle
findings can be serialized as `$HOME/.../gradle/libs.versions.toml` rather
than relocatable project variables. Restoring that analysis in another
worktree keeps the old root paths; `verifyReleaseLintWarnings` correctly
rejects them even when the final report was freshly produced.

The root build adds canonical `parlor.lintRepositoryRoot` to every Android
partial-analysis task's inputs (including test and vital variants). Same-root
cache reuse remains enabled; cross-root partial results are invalidated.
Compilation and other task caches are unaffected. This uses a pinned AGP
internal task type: re-review it on an AGP/lint upgrade. Do not rewrite stale
report paths, accept outside-root sources, or disable lint to restore a pass.

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
