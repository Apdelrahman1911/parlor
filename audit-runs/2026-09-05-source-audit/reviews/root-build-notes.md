# Root review — build graph and scope

Reviewer: root. These notes document actual reads in this audit, not prior audit conclusions.

## Completed initial reads

- Root `settings.gradle.kts`: plugin-management R8 override from a property; only build-logic is included as a composite; Google/Maven Central/JetBrains Compose are dependency repositories. Thirteen included KMP modules (app, ten shared, two games); graph is declarative pending Gradle observation.
- Root `build.gradle.kts`: explicit allTests aggregate hooks registered KMP allTests tasks; desktop aggregate includes every desktopTest (app compilation transitively); Apple runtime aggregate only iosSimulatorArm64Test; Apple linkage covers arm64, simulator arm64, x64. Host-independent productionCheck does NOT include Apple or physical LAN. Type-aware Detekt tasks are selected by registered task names; commonTest lacks a metadata task but plain Detekt scans authored tests. Android managed runtime is a separate gate, not productionCheck. No executed checks claimed yet.
- Convention plugins: every library declares Android/desktop JVM 21 plus three iOS targets; static library frameworks; Compose convention applies standard KMP library and Compose/compiler plugins. Detekt excludes generated output and emits per-task reports. Runtime JDK target versus actual toolchain is recorded separately.
- Every module build file: engine/core are game-independent; networking publicly exposes engine/core; session depends on networking; transport alone declares both exact-version P2pKit coordinates and storage; games depend on shared modules, never vice versa. engine-testing/networking-testing are reached from commonTest, not the shipping app. Content commonMain includes Ktor core but MockEngine/JSON-client helpers are test-only. Production reachability still needs actual graph observation and source tracing.
- App build: version source parsed/validated once, generated runtime constants under build; commonMain includes both game bindings and all shared shipping modules, no fixture. Navigation 3 runtime 1.0.0 / JetBrains UI alpha06 declared. JUnit Platform on JVM test tasks. iOS ComposeApp dynamic framework, release links serialized by mustRunAfter. Release R8/resource shrinking enabled, Debug ID suffix `.debug`. Signing is external; no signed task authorized or run. App's declared managed Android test is Release API35 Pixel2; Kotlin and Java instrumentation source mappings need compile/runtime verification.
- App validators: exact permission/exported-component allowlist and backup/cleartext/debug/test checks; XML parser rejects DTD/external entities; accepted lint inventory checks multiplicity and source+stable message (normalizes only update advisories); shell dispatch forbids named-game tokens in selected shell files. These validators are themselves claims until executed and their coverage limits remain explicit.
- `config/release-policy.json` and `release/mobile-release.json`: both identities blocked in tracked configuration. This is independently observed local configuration, NOT fresh external verification of a Store collision. Policy-qualified Xcode 26.3 build 17C529 is absent on this host; installed Xcode 26.5 build 17F42. No replacement identifier chosen.
- `composeApp/proguard-rules.pro`: attributes only, not blanket implementation retention. Dependency consumer rules and final shrunk runtime still need examination.

## Baseline environment

2026-09-05T06:16:48Z: JDK Homebrew 21.0.11; wrapper Gradle 8.13; Kotlin 2.4.10; Compose 1.10.3; Material3 1.9.0-beta03; coroutines/serialization 1.11.0; Koin 4.0.0; Ktor 3.0.3; P2pKit 0.7.0-rc3; AGP 8.13.2; independent lint 9.1.1 and R8 9.1.41. Python 3.14.6; Git Apple 2.50.1. Required JDK21 available. Around 29 GiB free, physical RAM 16 GiB.

An interrupted approval-only process-list query executed no build. On continuation, no audit agent/process remained live; current-task read receipts were preserved and continuation reviewers assigned only unread scope. Two pre-existing Gradle 9.4.1 daemons and one Kotlin daemon were observed; they are not this Gradle-8.13 audit's processes and must not be terminated. No pre-existing module/build-logic `build/` directories were present at initial inspection.

## Outstanding

Actual configuration/source-set/task graph; complete dependency verification metadata; runtime test implementation/wiring; release scripts/workflows; build output execution; platform/native behavior. These initial reads do not establish audit completion.
