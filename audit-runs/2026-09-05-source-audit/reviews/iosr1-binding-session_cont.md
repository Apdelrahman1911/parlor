# IOS-R1 fixture — pinned export and copied-wrapper source check

Reviewer: `/root/session_cont` (fixture author; this is a source/API follow-up, not self-approval). Existing independent fixture approval: `reviews/iosr1-apphost-fixture-mafia-cont.md`, plus `evidence/iosr1-fixture-independent-mafia-cont/readme-delta-approval.json`. Root owns build/runtime decisions and the single build lane; `/root/whodunit_cont` owns the separate runner-safety review.

Baseline: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Repository prefix for all first-party paths below: `/Users/abdelrahman/Projects/parlor/`. Exact hashes and honest reopened ranges are in `iosr1-binding-session_cont.sources.json`.

## Outcome

**No additional deterministic fixture compile/run blocker established by source inspection. Actual generated-header inspection and compilation remain mandatory.** No Gradle, Kotlin/Swift/compiler, Xcode, device, simulator or native command ran in this task. No fixture code or production file was edited. No generated app header existed in `composeApp/build/**/Headers/*.h` or the bounded audit-evidence header search at task start. New root-owned outputs, if subsequently generated, are outside that observation.

An initial concern that the callback must return `KotlinUnit` was investigated rather than assumed. Exact Kotlin2.4.10 compiler source provides counter-evidence: this direct `(String) -> Unit` parameter is expected to export as a void-returning Objective-C block. This is not a new application finding and not a runtime PASS.

## Exact pinned export path

- `gradle/libs.versions.toml:8` pins Kotlin2.4.10. The cached compiler's `META-INF/MANIFEST.MF` independently reports Implementation-Version2.4.10; only ZIP metadata/class-entry inspection and streaming SHA-256 occurred. Cached artifact path/hash are in the source manifest; no compiler was executed or cache modified.
- GitHub's authoritative `v2.4.10` tag resolves to commit `5687445832cd835b4509b9fbc264cdf1a8201093`. Initial requests at the old Kotlin source layout returned404 and are retained as failed research attempts, not absence-of-feature evidence. The actual source moved under `native/objcexport-header-generator/`.
- Compiler `kotlin-native/backend.native/.../objcexport/ObjCExport.kt:37–92` constructs the K1 `ObjCExportMapper`, `ObjCExportNamerImpl`, and header generator for Apple framework output. This establishes applicability rather than assuming an unused alternate exporter.
- `ObjCExportMapper.kt:311–359`: a direct function-typed parameter uses `bridgeFunctionType`; its actual return type satisfies `isUnit()`, so `BlockPointerBridge(..., returnsVoid=true)` is selected.
- `ObjCExportTranslator.kt:635–705,1029–1086`: the mapped parameter becomes `ObjCBlockPointerType(ObjCVoidType, ...)`. This disproves the requirement to blindly add `KotlinUnit()` to the copied Swift callback. The analysis-API translator has matching `returnsVoid` handling, but the framework compiler path above is the primary evidence.
- `ObjCExportNamer.kt:112–120,493–511,551–556,560–645` and translator `268–287,373–389,1128–1168` support the expected Swift object class, `shared` class property, and `start(onJson:)` / `cancel()` names. Ordinary attributes precede the Swift-name attribute. Name collision/mangling remains a reason to inspect the actual header, not substitute source reasoning for it.
- `composeApp/build.gradle.kts:103–119` builds a dynamic framework named `ComposeApp` for iOS; the additive fixture is public in that same compilation. Neither common app build configuration nor `gradle.properties` requests Swift export or changes the direct block mapping. The `internal` helper/native function is not the Swift entry.

Expected, **not yet observed**, Swift use is `IOSR1AppHostProbe.shared.start { json in ... }` and `.cancel()`, with a String/Void callback. Do not remove the template's `#error` or bind tokens solely from this report. Preserve and inspect the actual generated interface, its property/method declarations, block return type, NSString/nullability, and any mangled names first. The root's newer draft header guard explicitly checks a void block and preserves the complete header before parsing; a guard mismatch is a harness binding question, not a Parlor application failure.

## Copied Xcode path and test wiring

- `project.pbxproj:29–40,60–118,132–168,250–275` resolves the copied ContentView and one XCTest source through preserved group-relative paths. The UI-test target depends on the original app target. The18-file allowlist preserves every referenced wrapper/version input relevant to this copied build without copying xcuserdata/private settings.
- `Configuration/Config.xcconfig:8` resolves `../../config/parlor-version.xcconfig` correctly only if the copy retains the prescribed root/config and root/iosApp layout. Its app name and public identifier remain unchanged; target Debug appends `.debug` (`project.pbxproj:398–427`). Info.plist continues to consume the original build variables rather than hardcoded substitutes.
- Both framework search occurrences (`project.pbxproj:411,441`) must point to the original repository's `composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` because Gradle remains rooted there. Copying the PBX project without these two replacements would search under the temporary copy's nonexistent composeApp directory. The approved copy manifest handles both occurrences.
- The copied phase changes its working directory to the real repository, uses the additive init only for the framework/embedding call, and invokes the original case-normalizer on `TARGET_BUILD_DIR/FRAMEWORKS_FOLDER_PATH`. This is consistent with the dynamic framework and app `@executable_path/Frameworks` runpath. It does not fix the production shell-phase finding IOS-B1.
- `iosApp.xcscheme:8–55` includes one non-skipped, non-parallel UI-test target under Debug. The copied patch leaves one XCTest class/method: `IOSAppLaunchUITests.testAuditProductionRecoverySourceAttribution`. The root's draft runs the copied scheme's `test` action, not an obsolete method selector. If an explicit selector is later used, it must be `iosAppUITests/IOSAppLaunchUITests/testAuditProductionRecoverySourceAttribution`.
- Original Home selector is source-backed (`HomeScreen.kt:176–191,555`), and the warning string exactly matches `composeResources/values/strings.xml:147`; the copied test forces English for the fresh process. Audit trigger/result accessibility identifiers are literal strings in the copy-only Swift patch. Actual iOS accessibility exposure still requires XCTest execution.

## Evidence interpretation and prerequisites

The original Home resolver runs in `App.kt:120–143`; the fixture reruns that resolver on the same Koin instances. The initial10-second warning observation and later production-backed result are different calls. Warning absence alone cannot distinguish healthy completion from a still-pending first scan or an inaccessible/offscreen element. Record it as non-observation, not a storage-success assertion.

The late empty-list guard is not a privacy sandbox: original filesystem list may perform legacy migration before returning and transport recovery may decode/invalidate credentials. A newly created, verified, task-owned simulator UUID before installation is therefore a strict prerequisite; never reuse a user simulator/container. This boundary is already explicit in the independently approved README clarification.

A green copied XCTest means Home/foreground/no-alert smoke plus named-receipt delivery. It can still carry a production source failure or harness-aborted JSON; parse and classify those separately. Optional numerical Security corroboration is a subsequent same-app equivalent query, never the original Home call's OSStatus. Physical-device, signed-release, same-LAN, durability and original historical simulator evidence remain unverified.

## Authoritative research

Access date: 2026-09-05. Full bounded responses, HTTP statuses, hashes and tag/source discovery are under `research/iosr1-binding-session_cont/`.

1. https://kotlinlang.org/docs/native-objc-interop.html#function-types — warns about function-type Unit boxing; useful as a concern, insufficient to decide this direct parameter on2.4.10. Also reviewed `#kotlin-singletons`, `#method-names-translation` and class-name collision limitations.
2. https://api.github.com/repos/JetBrains/kotlin/git/ref/tags/v2.4.10 — resolves the exact pinned tag commit above.
3. https://github.com/JetBrains/kotlin/blob/v2.4.10/kotlin-native/backend.native/compiler/ir/backend.native/src/org/jetbrains/kotlin/backend/konan/objcexport/ObjCExport.kt#L37-L92 — actual framework header construction path.
4. https://github.com/JetBrains/kotlin/blob/v2.4.10/native/objcexport-header-generator/impl/k1/src/org/jetbrains/kotlin/backend/konan/objcexport/ObjCExportMapper.kt#L311-L359 — direct callback bridge and Unit→void decision.
5. https://github.com/JetBrains/kotlin/blob/v2.4.10/native/objcexport-header-generator/impl/k1/src/org/jetbrains/kotlin/backend/konan/objcexport/ObjCExportTranslator.kt#L1029-L1086 — concrete block return translation.
6. https://github.com/JetBrains/kotlin/blob/v2.4.10/native/objcexport-header-generator/impl/k1/src/org/jetbrains/kotlin/backend/konan/objcexport/ObjCExportNamer.kt#L611-L645 — parameter-label-derived Swift method names; actual header still required.

Only public URLs were requested; no source excerpts/private data were sent externally. Required audit source/research evidence is retained. No build/bytecode/app/native output or persistent process was created, and parent processes/Gradle lane were untouched. The working tree remains tracked/index-clean; all pre-existing untracked material was preserved.
