# MT-C1 — independent advisory applicability validation

**Classification: FALSE POSITIVE for a reachable Parlor vulnerability at this checkout.** The upstream advisory is real, and the pinned Kotlin Gradle Plugin version is listed affected; this is not a claim that upstream2.4.10 is patched. No confirmed Parlor defect/severity added. Finder `/root/whodunit_cont`; independent validator `/root/session_cont`.

Source `main` `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; absolute root `/Users/abdelrahman/Projects/parlor/`; tracked source unchanged.

## Independent source path

I reopened complete rootbuild204, settings55, Gradleproperties29, catalog106, composition-appbuild615, all12shared/game module build files and all3convention plugin implementations plus conventionbuild62 and included-buildsettings18. Catalog8/87/99 pins2.4.10. Root4–14 declares and151–204 applies the actual plugins/aggregates; module plugins are only KMP/Android/Compose/serialization/Detekt. The convention KmpLibrary24–25 applies KMP and Android; KmpCompose15–17 adds Compose/compiler; Detekt19 applies Detekt. No KAPT plugin, kapt processing dependencies, dynamically loaded annotation-processing script, KSP plugin or processor was added by these paths. Ordinary Gradle caching at `gradle.properties:7` does not instantiate KAPT's incremental cache manager. This conclusion is based on plugin bodies and complete module build scripts, not just an absent search token.

Reopened exact official Kotlin `v2.4.10` source retained by the finder: `kotlin-v2.4.10-KaptContext.kt:1–172` and `kotlin-v2.4.10-kapt-cache.kt:1–228`. KaptContext58–60 constructs JavaClassCacheManager only when a KAPT incrementalCache option exists; cache13–17 eagerly reads `java-cache.bin`/`apt-cache.bin`;139–167 deserializes through raw ObjectInputStream before its cast/catch, so casting afterward is not a security defense. This is genuinely the upstream affected KAPT surface. It is not generic Kotlin/Gradle caching and not Parlor's snapshots/rejoin storage.

I read the complete347-line official fix patch at `bf51df665b458fda7c3eaf436c4d88dc119d7ec6`. Its title/body and seven changed paths are all KAPT; it replaces the raw reader with allowlisted class/proxy checks and adds callback-rejection tests for those two cache files. The actual current project has no traced production or test-build route that creates this KAPT context. Merely depending on a KGP coordinate listed by a vulnerability index is insufficient to execute the path.

## Counter-evidence/research

- Official OSV/GitHub-reviewed GHSA-r937-wjx7-w2jp / CVE-2026-53914 record was independently read (`research/GHSA-r937-wjx7-w2jp-whodunit_cont.json`). It includes2.4.10 and fixes2.4.20-Beta1; the broad wording “build cache metadata” warranted inspecting the actual vendor fix rather than rejecting the advisory by name.
- Official Kotlin documentation fetched2026-09-05: `https://kotlinlang.org/docs/kapt.html` explicitly shows applying `kotlin("kapt") version "2.4.10"` / `org.jetbrains.kotlin.kapt`, adding kapt dependencies, and treats Gradle caching as an optimization of those already-activated kapt tasks. Excerpts and fetch hash are retained in `research/MT-C1-session_cont/`.
- Exact upstream source/fix URLs and hashes are in `research/kotlin-cache-tag-fetches-whodunit_cont.json` and `research/kotlin-cache-official-fetches-whodunit_cont.json`; these retained public vendor bytes were reopened directly, not accepted from the finder summary. The dynamic vendor security landing page was not used as proof of a row it did not expose.
- Supporting repository search over tracked Kotlin/Gradle/workflow/script sources found no kapt/KSP/annotationProcessor references. Selected runtime graph receipts and metadata absence may support but do not alone prove this activation conclusion; runtime graphs are not the entire build-plugin graph or final shipped binaries.

## Boundaries and follow-up

Retain this advisory in the dependency register and re-open applicability if KAPT, annotation processors, a new convention/init plugin, or a Kotlin version change enters the reviewed build. No source/dependency upgrade, KAPT exploit, cache poisoning, private-cache inspection, build/test execution or Store operation was performed. No assurance is given about unrelated vulnerabilities, external user init scripts, malicious build infrastructure, unselected transitive/native code, or a future task graph. The claim rejected is only that this specific KAPT advisory is currently reachable merely because Parlor pins KGP2.4.10.

Hashes/ranges are in `validations/MT-C1-source-hashes-session_cont.json` and the coverage ledger. This advisory applicability determination is not an executable gate PASS or a proof of global dependency safety.
