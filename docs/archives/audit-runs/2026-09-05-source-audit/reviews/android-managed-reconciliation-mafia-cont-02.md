# Android managed attempt02 — independent evidence reconciliation

Reviewer: `/root/mafia_cont`. All481receipt lines,693Gradle-log lines,43input-manifest lines,30case-receipt lines,12artifact-receipt lines,3ADB-log lines, one-line stop and empty desktop-test receipts, and11XML lines read. Independently reparsed the retained XML, reconciled suite totals and all named cases against both JSON summaries, and verified all8current input hashes. Exact evidence hashes/limits: `evidence/android-managed-reconciliation-mafia-cont-02.json`.

**PASS — narrowly scoped Android runtime evidence.** Root's task-owned API35 ARM64 managed emulator executed these existing cases,3tests/0failures/0errors/0skips:

- `MainActivityColdStartTest.testColdStartDisplaysContentWhileSettingsIoIsBlocked`: real SharedPreferences FIFO remains blocked while attached, visible, nonzero Compose root draws.
- `ReleaseRuntimeSmokeTest.testReleaseBuildCanAcquireDeclaredMulticastLock`: permission granted and multicast lock acquired/released.
- `ReleaseRuntimeSmokeTest.testReleaseBuildLaunchesCanonicalActivityWithoutDebuggableFlag`: canonical package, no debuggable flag, and Activity neither finishing nor destroyed after launch.

The actual two test files and checked-in harness were completely reopened. Root `productionAndroidRuntimeCheck` wires the managed release test at build.gradle.kts53–57; compose configuration supplies platform instrumentation, Java test sources, release variant and Pixel2/API35 fixture. Log confirms release R8,3started/finished cases and BUILD SUCCESSFUL (486tasks:420executed,66up-to-date). The test/APK hashes and sizes reconcile with the runner receipts; APK bytes were already cleaned, so this reviewer did not independently rehash or inspect them. Signing was synthetic/two-day only. Source identity before/after and8input hashes agree with main/`3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

**Cleanup receipt PASS.**24finalization stages PASS;16output paths checked/13actually removed; no owned workers, unknown holders, remaining outputs or cleanup errors reported;2cache-link pointers unlinked without target deletion. The root-owned wrapper's successful explicit Gradle stop completed0.336950seconds after checked-in harness return; the harness also stops Gradle in its own EXIT trap. Exact task temporary root is independently absent, including dangling-symlink check. Reviewer did not query or terminate processes; process absence is bounded by the source-reviewed runner's recorded observations. The final repository-wide preservation checker must still run after the whole build lane is idle.

**Do not extrapolate.** This is macOS/ARM64 emulator evidence, not physical-device or Linux/x86_64 CI evidence. Lock acquisition is not LAN/P2pKit connectivity. A drawn Compose root is not full Home/game health, repeated-launch stability, lifecycle/privacy/RTL/accessibility testing, or Store readiness. The ADB disconnect log entries coincide with managed-emulator lifecycle; no application crash or failed assertion is established by them. Gradle/Kotlin/SDK/tool warnings do not supply missing app-runtime evidence.

No application defect is added by this reconciliation. Attempt01 remains a separate pre-Gradle bootstrap failure;02does not erase its historical receipt. This review launched no build/native/device/server process and generated no build outputs.
