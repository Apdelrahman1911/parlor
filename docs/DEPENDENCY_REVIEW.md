# Dependency and license review

## Policy

Runtime and build dependencies are pinned in `gradle/libs.versions.toml`.
Production must resolve from declared repositories; `mavenLocal()` and
developer-home auto-detection are prohibited. Dependency upgrades require a
separate reviewed change with release notes, compatibility tests, and all
production gates.

Useful resolved-graph receipts:

```bash
./gradlew :composeApp:dependencies --configuration desktopRuntimeClasspath
./gradlew :composeApp:dependencies --configuration releaseRuntimeClasspath
./gradlew :shared:transport-p2p:dependencies
```

Attach these outputs to each release candidate. Review transitive as well as
direct dependencies for known advisories, abandoned components, duplicate
versions, unexpected repositories, and platform-specific native binaries.

## Current direct families

The version catalog currently declares Kotlin/Kotlinx, Compose Multiplatform,
Android Gradle Plugin, Koin, Ktor, P2pKit, SQLDelight, Detekt, Konsist,
JUnit, Turbine, and AssertK. Presence in the catalog does not prove a library
is packaged; the resolved release graphs are authoritative.

The published P2pKit 0.7.0-rc2 release declares the Apache-2.0 license.
The published Maven artifact's POM, checksums/signatures, sources, license
metadata, and actual resolved version still need to be checked in the release
pipeline.

## Open legal gates

The repository does not currently provide:

- a final product/source distribution license decision;
- a generated SBOM;
- an automated transitive-license report; or
- final third-party notices bundled into both store artifacts.

These are release `FAIL` gates, not assumptions. Before shipping:

1. choose and add the project distribution license with owner/legal approval;
2. generate an SBOM from the resolved Android and iOS release graphs;
3. review every transitive license and required attribution;
4. create the authoritative third-party notices from that reviewed report;
5. bundle notices in the app or support site as required; and
6. archive the report, tool/version, and reviewer with the release evidence.

Do not manually copy license names from memory into a release notice. The
notice must be generated from the exact resolved artifact set and then
reviewed.
