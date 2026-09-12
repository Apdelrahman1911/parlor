# ROOT-C1 independent validation

Original finder `/root`; independent reviewer `/root/mafia_cont`. Source SHA `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

**Classification: FALSE POSITIVE for the proposed corrupt-directory crash. Separate TEST/EVIDENCE GAP for exception-throwing I/O paths; no confirmed defect.**

Independently read complete `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt` (1–478), `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt` (1–246), `composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt` (1–146), and `composeApp/src/iosTest/kotlin/com/parlor/app/storage/IosStorageSafetyTest.kt` (1–195).

Reachability trace: Home scan calls store.listUnfinished then loadMetadata for the bounded first eight records. iOS lists names without regular-file checks and permits safe `.snapshot.json` names. Metadata load calls protected-or-legacy read, which calls readBoundedSnapshotBytes. That helper opens an NSFileHandle, throws Kotlin IllegalStateException on nil, then invokes legacy readDataOfLength in try/finally and closeFile. Store catches Kotlin Exception and returns sanitized DataError. No source-level Objective-C exception wrapper exists.

The proposed directory input **does not establish a crash**: independently inspected `reproducers/filehandle_directory.m` and `evidence/filehandle-api-01/receipt.json`. Root compiled against macOS Foundation and executed a synthetic task-only directory: compile exit0, run exit3, stdout `opened_directory=false`; the opener returned nil, so no exceptional read occurred. This matches the source safe error branch at iOS helper405–406. The reproducer is macOS-only and cannot prove iOS behavior; it is counter-evidence, not iOS success.

Authoritative references accessed 2026-09-05:
- https://developer.apple.com/documentation/foundation/filehandle/readdata(oflength:) (retrieved https://developer.apple.com/tutorials/data/documentation/foundation/filehandle/readdata(oflength:).json): readDataOfLength raises NSFileHandleOperationException if type detection or reading fails. Applies to the exact Objective-C selector Parlor imports. Apple current metadata says deprecatedAt27.0; not evidence it is already deprecated on iOS26.5.
- https://kotlinlang.org/docs/native-definition-file.html#handle-objective-c-exceptions: default ObjC-to-Kotlin boundary terminates on Objective-C exception; objc-wrap opt-in changes this.
- https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.10/kotlin-native/platformLibs/src/platform/ios/Foundation.def: full nine-line platform binding has no foreignExceptionMode, matching the pinned Kotlin2.4.10 default.
- Installed `/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS26.5.sdk/System/Library/Frameworks/Foundation.framework/Headers/NSFileHandle.h` read1–176:112–123 and158–161 warn legacy APIs can throw; NSError-returning readDataUpToLength/error and closeAndReturnError are available since iOS13 (27–28,48–49).

Counter-evidence: helper bounds allocation to maximum+1 and closes handles; nil open maps to safe Kotlin error; tests cover exact-size/oversize/crypto and legacy cleanup. They do not force a post-open I/O error. A file-protection or device I/O failure after open might raise an uncaught Objective-C exception, but no concrete application-reachable deterministic sequence or iOS runtime evidence was established. Keep that uncertainty as a coverage/evidence gap, not a confirmed application defect. Recommended future validation: synthetic iOS post-open failure seam/device lifecycle test, then consider NSError API if justified.

No builds or tests were launched by this validator; root-owned reproducer already cleaned task executable/directory according to its receipt.
