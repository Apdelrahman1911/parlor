# MT-C1 — Kotlin build-cache advisory applicability

**FALSE POSITIVE for Parlor's current reachable paths — independently validated.** Finder `/root/whodunit_cont`; independent validator `/root/session_cont` (`validations/MT-C1-session_cont.md`, full report read). This is a retained advisory lead, not a confirmed application/build vulnerability.

## Source and external records

Parlor main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, no tracked changes. Source root `/Users/abdelrahman/Projects/parlor/`. `gradle/libs.versions.toml:8,85–103` pins and applies Kotlin Gradle Plugin2.4.10.

The2026-09-05 public OSV batch (314 exact coordinate queries:307 runtime graph union plus7 build pins) returns one hit: `GHSA-r937-wjx7-w2jp` / `CVE-2026-53914`, unsafe Kotlin build-cache metadata deserialization. The record lists2.4.10 as affected and2.4.20-Beta1 as fixed; CVSS3.1 `AV:L/AC:H/PR:H/UI:N/S:C/C:H/I:L/A:L`, GitHub severityMODERATE. Merely matching this broad package index is not reachability proof.

Authoritative Kotlin fix commit `bf51df665b458fda7c3eaf436c4d88dc119d7ec6` explicitly changes **KAPT** cache deserialization: `plugins/kapt/kapt-base/.../incremental/cache.kt` replaces raw `ObjectInputStream` with an allowlisting stream and rejects dynamic proxies. Its tests serialize synthetic objects into `java-cache.bin`/`apt-cache.bin` and prove callback rejection. The complete patch was read.

Exact upstream tag `v2.4.10` source was fetched and read completely (cache228lines;KaptContext172lines). The affected methods remain at `research/kotlin-v2.4.10-kapt-cache.kt:139–167`; object construction eagerly reads these files13–17. `research/kotlin-v2.4.10-KaptContext.kt:58–60` instantiates the manager only for a non-null KAPT incremental-cache option. Thus the advisory is real in the selected upstream version, but its published fix is not a change to generic Gradle caching or Parlor gameplay storage.

## Current project counter-evidence

- Actual root plugin list, all module plugin declarations, version catalog and convention plugin implementations contain no KAPT, annotation-processing plugin, or processor dependency. Koin is configured via runtime DSL, not annotation generation. Serialization/Compose compiler plugins are distinct.
- Full convention implementations and configuration were reopened: `build-logic/convention/build.gradle.kts:1–62`, `KmpLibraryConventionPlugin.kt:1–72`, `KmpComposeLibraryConventionPlugin.kt:1–19`, `DetektConventionPlugin.kt:1–59`; they do not apply KAPT indirectly. The only included build is this first-party build-logic project.
- `git grep -n -i -E 'kapt|annotationProcessor|com.google.devtools.ksp' -- '*.kts' '*.toml' '*.properties' '*.yml' '*.yaml' '*.kt' '*.sh' '*.py'` returned no tracked-source matches. This search is supporting evidence, not a replacement for the reopened plugin bodies.
- Current verification metadata and root's recorded selected runtime graphs have no Kotlin annotation-processing component; no KAPT task appears in the preserved executed-task logs. Graph receipts cover Android/Desktop applications and Apple transport only, not the entire Apple app or all plugin internals.
- `gradle.properties:7` enables ordinary Gradle build caching, but that alone cannot instantiate KAPT's `JavaClassCacheManager`. No attacker-writable KAPT cache is loaded by a traced Parlor path.

Recommended disposition: retain the advisory in the dependency register, distinguish inactive upstream surface from a reachable Parlor defect, and re-review if KAPT is added or toolchain pins change. Do not upgrade or weaken verification merely to quiet an index. No exploit, cache poisoning, private-cache inspection, Gradle build or toolchain modification was performed.

## Research receipts and limits

- `research/osv-selected-coordinates-whodunit_cont.json` and `GHSA-r937-wjx7-w2jp-whodunit_cont.json` bind public queries/record.
- `research/kotlin-cache-official-fetches-whodunit_cont.json` binds official commit patch, JetBrains security page and release metadata URLs, access dates, hashes/statuses. The vendor HTML is dynamically populated and did not itself expose this CVE row; it was **not** treated as additional proof.
- `research/kotlin-cache-tag-fetches-whodunit_cont.json` binds the exact upstream2.4.10 source URLs/hashes. These are third-party applicability evidence, not first-party file-coverage credits.
- No-hit runtime scan does not prove absence of vulnerabilities. Build/test transitive graphs, full Apple app graph, native binaries and signed release SBOMs remain separately incomplete.

Independent conclusion: the affected cache path is real in upstream2.4.10 but requires KAPT activation absent from complete reopened project plugin/convention/module paths. Retain the advisory and re-open applicability if that changes; no confirmed Parlor vulnerability added.
