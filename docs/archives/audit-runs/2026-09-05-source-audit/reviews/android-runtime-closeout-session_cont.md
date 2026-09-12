# Android managed Release runtime — closeout applicability check

**Reviewer:** `/root/session_cont`, 2026-09-05. **Source:** `/Users/abdelrahman/Projects/parlor`, unchanged `main@3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

This is a **pre-execution, source-only classification**, not a runtime result. The parent owns any subsequent execution and cleanup. No Gradle, Android SDK/device, Xcode or signing command was run by this reviewer.

## Conclusion

`productionAndroidRuntimeCheck` is **locally executable in principle and was unexecuted, not proven externally blocked**. It is not physical-device-only and does not require a Store account, production signing credentials, or publication authorization. A checked-in disposable test-signing harness already exists. Actual emulator/image/acceleration availability was not established by this bounded pass; the parent is separately checking it. Until an execution receipt exists, this is a missing local verification result, not PASS or proof of inability.

There is **no install-ready unsigned APK route**: Android requires installed APKs to be signed. The safe existing route is **ephemerally self-signed test installation on a disposable managed emulator**, distinct from real Store signing. Do not weaken release gates or reuse a personal device/profile to bypass that requirement.

## Exact task and signing path

All paths in this report are below the absolute repository prefix above; exact hashes/absolute paths/read ranges are in the companion source manifest.

1. `build.gradle.kts:53–57`: root runtime gate depends on `:composeApp:pixel2Api35ReleaseAndroidTest`. It is distinct from `productionAndroidCheck` (`37–50`), `productionAndroidSigningCheck` (`59–63`) and `productionCheck` (`139–149`). A successful unsigned AAB/unit-test aggregate does not execute this runtime task.
2. `composeApp/build.gradle.kts:214–220,238–262,289–302`: canonical application ID `com.parlor.app`; platform `android.test.InstrumentationTestRunner`; non-debuggable, R8/resource-shrunk Release; `testBuildType = "release"`; local managed `pixel2Api35`, Pixel 2/API35/Google image/64-bit. Debug's `.debug` suffix does not apply to this Release test.
3. Normal Release signing is conditional on complete external inputs (`70–101,223–235,259–261`). The separate Store gate requires real external inputs at `518–555`; runtime does not depend on that gate. Do not invoke it or inspect existing private material for this test.
4. `scripts/android/run_release_managed_device_smoke.sh:29–46` creates a mode-700 temporary directory and a disposable two-day RSA/PKCS12 test key. At `49–58` it calls the root runtime gate with strict dependency verification, `--no-daemon`, two workers and **`android.injected.signing.*`** test-key properties. This is the existing install-signing path, not a missing feature or a need for the owner's upload key.
5. Active `.github/workflows/production-verification.yml:45–64,77–79` provisions its Linux SDK/image and runs this script. The disabled candidate workflow has the same call (`testing-candidate.yml:29–32,78–111`), but its jobs must remain disabled; no release workflow needs enabling to run the local script.

## Prerequisites and bounded result scope

- Use checked-in Gradle 8.13 and JDK21. AGP8.13.2/Kotlin2.4.10 and analyzer pins stay unchanged (`gradle/wrapper/gradle-wrapper.properties:1–8`, `gradle/libs.versions.toml:7–25`, `gradle.properties:18–22`).
- Need executable emulator/ADB, a compatible API35 Google 64-bit system image, SDK platform36/build-tools36.0.0 and an acceleration/rendering environment that can run the selected image. CI's **Linux** package pin is `system-images;android-35;google_apis;x86_64`, revision9 (`config/release-policy.json:46–54`, workflow `45–64`). On this Darwin arm64 host, record the actual chosen compatible image/ABI; do not describe an ARM64 local run as the pinned Linux x86_64 CI image. This source review does not establish the installed SDK image or exact AGP host-selection result.
- Availability-only lookups found `java`, `keytool`, `adb`, `sdkmanager`, `avdmanager`, `xcodebuild`, `xcrun`. `emulator` was not on PATH. **PATH absence is not proof of a missing emulator**; it may exist under the SDK. No SDK contents, installed package/license files, `local.properties`, user Gradle configuration, credentials, signing files or real player data were read here. No device list/start/install/permission mutation was performed.
- The installed signing key must remain the newly generated disposable key, not inherited Store credentials. If a caller has `MOBILE_RELEASE_REQUIRE_SIGNING=true`, the project's early external-signing check is a separate configuration prerequisite (`composeApp/build.gradle.kts:95–101`); do not satisfy it with private material for this synthetic test.
- The user already requested applicable local checks. No source change, Store operation or production signature is needed for this route. Missing SDK downloads/licenses, unusable acceleration, unavailable disk/RAM or a real observed tool failure must be recorded if encountered, not presumed in advance. Parent controls the one build lane and any resource/setup decision.

### What the checked-in test bodies actually check

| Test source | Declared check | Limitation / safety |
|---|---|---|
| `composeApp/src/androidInstrumentedTest/java/com/parlor/app/ReleaseRuntimeSmokeTest.java:18–37` | Canonical package, not debuggable, `MainActivity` launches/remains alive through idle. | Not a full game, repeat-launch stress test, or Store signature check. |
| Same file `39–60` | Declared multicast permission granted and a `WifiManager.MulticastLock` can be acquired/released. | Not an actual advertisement/discovery/join or multicast packet transfer. |
| `composeApp/src/androidInstrumentedTest/kotlin/com/parlor/app/MainActivityColdStartTest.kt:21–138,140–171` | Block real preferences I/O with a FIFO, observe nonzero attached Compose content and a draw while I/O remains blocked. | Deletes preferences XML/backup and restores empty XML (`24–36,128–135`): **only run on disposable owned app data**. This intentionally destructive synthetic fixture must not touch a user's installed canonical app. |

There are three source-declared JUnit3 test methods across these files. This is an expected inventory, **not proof that all three were discovered/executed**; retain actual XML/proto/results and counts after the run.

The FIFO's target is genuine: `MainActivity.kt:42–69` loads Koin settings on `Dispatchers.IO`; `PlatformStorage.android.kt:14–19` binds `PersistentSettingsStore` to `AndroidSettingsKeyValueBacking`; that backing opens `parlor_settings_v1` (`19–21,62–63`). `ParlorApplication.kt:13–24` and the manifest (`7–8,20–29`) bind the actual application/activity. The test does not install an alternate production storage implementation.

## Execution/cleanup contract for the parent

Run the **existing script** in a cleanly scoped, owned emulator/configuration environment, not raw unsigned `productionAndroidRuntimeCheck` and not a physical personal device. Keep strict dependency verification and the Release selector. Record exact source identity, SDK/emulator/image/ABI, command, exit status and test discovery/results.

The script's `EXIT`/signal traps (`7–27`) stop Gradle and delete its temporary key directory; they **do not clean repository build outputs or all possible managed-device/AVD resources**. Parent must collect compact reports first, immediately stop Gradle, precisely clean all outputs owned by the cycle, remove task-owned emulator/configuration resources and verify no owned emulator/ADB/test processes remain. Never terminate an unrelated user's ADB server/emulator or remove global caches/SDK packages. Preserve reports before cleanup. The checked-in CI publishes managed reports from `**/build/reports/androidTests/managedDevice/` and `**/build/outputs/androidTest-results/managedDevice/` (`production-verification.yml:96–111`).

## Other “BLOCKED” rows: distinguish unrun local evidence from external requirements

This is a correction to classification language, not new app findings or authorization to edit gates.

- **`navigation-accessibility-and-complete-ui-journeys`: mixed, not globally external.** The existing `IOSAppLaunchUITests.swift:9–30` asserts only English launch/Home/no native alert. Local simulator/emulator/Desktop can cover many additional Back, gesture, keyboard, size, landscape, text-scale, RTL and local pass-and-play scenarios; the aggregate row's reason says they were not executed, not that a tool/device prerequisite was unavailable. Keep those as **unexecuted local scenario/driver gaps**, while physical cross-device play, real screen-reader/device behavior and owner release acceptance remain separate. There is no existing complete-journey Gradle task whose pass can substitute for this missing matrix.
- **`ios-recovery-warning-attribution`: unresolved attribution, not demonstrated dependence on Store credentials.** Existing source-aware report explicitly calls for an isolated same-source app-host probe. The exact original error was not retained; the independent native -34018 witness does not close attribution. Further local diagnostics may be possible. Do not label it “only fixable/testable with a physical device” or change it to a confirmed defect. Correct signed-device comparison is a separate gate.
- **`native-cross-host-test-equivalence`: split actual host limits from test-layout gaps.** A Darwin arm64 run is not Windows/Linux/x64 runner evidence. Host-disabled native tasks have a concrete host applicability condition; `desktopTest`-only game bodies are a source-set coverage gap, not missing Store/device credentials. Do not claim their absent Native/Android executions are impossible runtime tests or passing tests.
- **Genuine distinct external/product gates remain:** qualified Xcode/signing/profile evidence, real-device LAN and physical protection behavior, identity ownership/Store governance, legal rights, and the unspecified Whodunit modal-clock product decision. None is resolved by this managed startup test. The report does not invent an additional blocker for them.

## Research and hygiene

`research/android-runtime-closeout-session_cont/research-ledger.json` records official Android signing and managed-device documentation URLs/access times/hashes and exactly inspected portions. Android's signing rule confirms that an unsigned APK is not install-ready; managed-device documentation confirms local Gradle virtual-device execution. Neither establishes this host's image availability or a successful run.

This reviewer only read source/public references and wrote compact audit evidence; no build/task/device/signing process or output was created. The parent is separately evaluating/running the existing harness. Final tracked/index diff remains empty; pre-existing untracked user work is untouched.
