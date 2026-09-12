# Android managed-device isolation preflight — source/binary verification

Reviewer: `/root/whodunit_cont`. Access date: 2026-09-05 (UTC). Audit-only; **no Gradle, adb, emulator, signing, or device command was executed by this reviewer**. These are execution-safety requirements, not additional Parlor application defects. Final audit-helper code approval is recorded separately.

## Baseline and applicable path

Repository `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked diff empty. Existing untracked user/audit/design files retained.

- `build.gradle.kts:53–57` selects `:composeApp:pixel2Api35ReleaseAndroidTest`, not a connected-device test.
- `composeApp/build.gradle.kts:289–303` selects release, Pixel 2, API35, google image, 64-bit. On ARM host AGP selects `arm64-v8a`.
- `MainActivityColdStartTest.kt:24–36,128–135` deletes/replaces release-app preferences and creates a FIFO. **Never run this fixture against an existing user device/AVD.**
- Checked-in `scripts/android/run_release_managed_device_smoke.sh:29–58` creates a two-day synthetic key in its own temporary directory. Its trap stops Gradle/removes the key, but the task-owned outer wrapper must also clean generated outputs and emulator/native processes on every exit.
- Pinned AGP is 8.13.2. Its published/cache POM selects Android tools/common/ddmlib 31.13.2. Installed SDK metadata: adb/platform-tools37.0.0; emulator36.6.11, build15507667. No SDK executables were run to identify versions.

## Independently established behavior

### AVD and UTP isolation

AGP8.13.2 `ManagedDeviceUtils.getManagedDeviceAvdFolder` uses `AndroidLocationsProvider.gradleAvdLocation`. Android tools31.13.2 `AbstractAndroidLocations:97–114,141–173,464–493` resolves:

`ANDROID_AVD_HOME/gradle-managed` (only when the AVD directory already exists), else `ANDROID_USER_HOME/avd/gradle-managed`.

`ANDROID_USER_HOME` is direct, not a parent requiring another `.android` leaf. Conflicting deprecated env/JVM properties can throw. Use a sanitized environment with fresh, precreated, non-symlink directories. No additional project AVD-directory property is needed.

`AvdManager:73–108,128–179` constructs sdklib with that explicit folder and creates/retrieves AVDs only there. `AvdSnapshotHandler:308–446` launches that AVD name with a per-launch random managed-device `-id`; passes the exact owned AVD folder in subprocess `ANDROID_AVD_HOME`; resolves the matching serial with `AdbHelper`; only then invokes the test callback. `ManagedDeviceTestRunner:104–158` passes that serial into `UtpConfigFactory`; `DdmlibAndroidDeviceProvider:119–131` requests exactly that serial. No generic connected-device fallback was found on this path.

`RunUtpWorkAction:35–54` uses an ordinary inherited-environment `ProcessBuilder`. ddmlib31.13.2 `AndroidDebugBridgeImpl:1628–1698` reads `ANDROID_ADB_SERVER_PORT`; `1454–1462` forwards `-P` when launching a nondefault server. The helper must provide the same port in CLI/UTP/emulator settings and unset contradictory inherited inputs. UTP temporary files live under the isolated Android preferences `utp/` directory (`UtpTestUtils:253–283`).

### Isolating the ADB server is more than choosing a port

A new server on another port can still scan USB, mDNS, and other emulators. Exact installed **adb37 ARM64 disassembly** verifies:

- `ADB_USB=0` skips both USB initializers.
- `ADB_MDNS=0` skips mDNS discovery.
- `ADB_EMU=0` skips the arbitrary emulator scanner.

Use literal `0`, not guessed values like `false`. These are tested by byte-level inspection, not a runtime assertion. Exact binary function/caller excerpts and hashes are retained. Public AOSP commit `1cf2f017d312f73b3dc53bda85ef2610e35a80e9` corroborates the behavior but is **not asserted to be the source revision of SDK37**; the requested `platform-tools-37.0.0` tag returned404.

Exact emulator36.6.11 disassembly traces `_android_adb_server_notify` → `AdbHostServer.getClientPort` → `AdbHostListener.notifyServer` → `AdbHostServer.notify`. It obtains `ANDROID_ADB_SERVER_PORT`, opens TCP4 loopback to that port (TCP6 fallback), and sends length-prefixed `host:emulator:<its-port>`. Thus disable the scanner and start/check the dedicated foreground server before Gradle launches the new emulator. Device registration/runtime success remains to be observed during the actual test cycle.

### Critical HOME/authentication distinction

**ANDROID_USER_HOME alone does not isolate adb37 authentication files.** Exact adb37 `adb_get_android_dir_path` calls `adb_get_homedir_path` then appends `/.android`; the latter uses `HOME`, falling back to `getpwuid_r`, and never consults `ANDROID_USER_HOME`. Do not leave real `HOME` in this child chain. Do not inherit `ADB_VENDOR_KEYS`, pairing/serial overrides, or copy/read real adb keys.

Exact emulator36.6.11 `ConfigDirs.getUserDirectory` returns nonempty `ANDROID_EMULATOR_HOME` first. On this macOS binary `getDiscoveryDirectory` constants resolve to `HOME/Library/Caches/TemporaryItems`, then `avd/running`. This is not guessed from generic Linux behavior. `emulator-discovery-constants.json` records read-only Mach-O pointer decoding.

## Required environment shape

Let `T` be a new task-owned directory and `P` a newly reserved private loopback port. Precreate directories with restrictive modes. Suggested values:

```text
HOME=T/home
ANDROID_USER_HOME=T/home/.android
ANDROID_EMULATOR_HOME=T/home/.android
ANDROID_AVD_HOME=T/avd
ANDROID_HOME=/Users/abdelrahman/Library/Android/sdk
ANDROID_SDK_ROOT=<same SDK, if retained>
ANDROID_ADB_SERVER_PORT=P
ADB_SERVER_SOCKET=tcp:127.0.0.1:P
ADB_USB=0
ADB_MDNS=0
ADB_EMU=0
TMPDIR=T/tmp/
GRADLE_USER_HOME=<explicit original dependency-cache directory>
```

Use JDK21; preserve strict dependency verification. Explicit `GRADLE_USER_HOME` prevents HOME isolation accidentally creating a duplicate dependency cache. Do not delete that original cache. Suppress inherited Java/Gradle option injections; retain only intentional flags. AGP `android.builder.sdkDownload=false` is a supported option (default true) wired into `SdkLibDataFactory` download enablement; set it so missing SDK components fail instead of modifying the shared SDK. This does not weaken artifact verification.

## Lifecycle and cleanup prerequisites

- Use a foreground task-owned ADB process, explicit port and PID/start identity; confirm the listener belongs to it before starting Gradle. No default `adb devices`, default-port `kill-server`, or broad `pkill`.
- PGID plus a temporary-path substring in argv is **insufficient** by itself. AGP puts the AVD folder in the environment, not argv; detached emulator/native workers may survive their parent. Match new PIDs using ancestry/start identity and open files under `T` (including the isolated discovery directory); do not dump unrelated process environments.
- AGP success attempts serial-specific `emu kill`; `AvdSnapshotHandler` finally only calls `Process.destroy()`. The outer wrapper must wait/verify native processes have exited before deleting their AVD/home/temp paths. Never delete open AVD state first, then report a clean run.
- Preserve compact sanitized test reports and necessary hashes, stop Gradle immediately, clean only task-owned output directories, stop again if cleanup invoked Gradle, verify no task-owned processes/generated outputs remain. Failed/timed-out cycles require the same finalizer. Stop neither unrelated Gradle daemons nor unrelated Android devices.
- A failure of proof/cleanup must remain explicit and not be labeled PASS. The fixture’s success would establish emulator release-runtime evidence only—not physical-device LAN, real signing, Store identity ownership, or Store submission readiness.

## Research evidence

- `https://developer.android.com/tools/variables` — official env semantics; retained selected table text/hash. Generic documentation alone is not enough for adb auth behavior.
- Exact versioned Google Maven sources: `com.android.tools.build:gradle:8.13.2`, `com.android.tools:common:31.13.2`, `com.android.tools.ddms:ddmlib:31.13.2`, `com.android.tools.utp:android-device-provider-ddmlib:31.13.2`. Full URLs, access times, byte counts and SHA256 in `source-download-receipts.json`.
- `source-excerpts.jsonl` — original source paths/hashes, one-based ranges, numbered exact excerpts.
- `binary-inspection-receipts.json` and `*-arm64.txt` — read-only disassembly commands, binary hashes/version metadata, focused proof. No private material inspected.
- `coverage/reviews-android-managed-preflight-whodunit_cont.jsonl` — 564 first-party lines across5files, additive reread only; no claim of new repository-wide completion.

Final code review of the rewritten audit helper and actual execution receipt are separate. Nothing in this preflight is a fabricated test/device PASS.

## Final runner refinement — 2026-09-05

The original `GRADLE_USER_HOME` suggestion above is retained as research history, **not the final execution instruction**. Independent helper review identified that sharing the original daemon registry could let the checked-in harness's unconditional `./gradlew --stop` stop a different task's daemon started during the cycle. The approved runner instead uses:

```text
GRADLE_USER_HOME=T/gradle-home
T/gradle-home/caches  -> <original Gradle user home>/caches
T/gradle-home/wrapper -> <original Gradle user home>/wrapper
```

Only those existing cache/distribution directories are linked. No original daemon registry, global `gradle.properties`, init script, or signing configuration is linked or inspected. Gradle8.13 source explicitly constructs `<gradleUserHome>/daemon` (`DaemonParameters:66–78`), `<base>/8.13/registry.bin` (`DaemonDir:32–36`), and enumerates that registry for `--stop` (`DefaultDaemonConnector:100–103`, `DaemonStopClient:86–123`). Exact tag URLs, access dates, hashes, and source excerpts are retained in `gradle-*-source.json`; one failed path lookup is recorded, not presented as proof.

Final approved audit helper: `run_android_managed_cycle.py`,646lines, SHA256 `74cf484d1e629fbe82ca0e91aa0b36691314044fdbe2d25c0af81f380afdcec1`. It explicitly unlinks its cache pointers before removing its temporary root. Installed `lsof` documentation confirms `+D` does **not** traverse internal symlinks without `-x`/`-x l`; the helper supplies neither. Python3.9.6 reports symlink-attack-resistant `shutil.rmtree` support. Selected local-man excerpts/hash and Python capability are in `installed-lsof-and-python-semantics.json`.

The final log-FD ownership fallback requires writable FD1/2 plus matching device/inode; a readable log opened by a viewer does not prove task ownership. `/root/whodunit_cont` independently reviewed all602prior lines plus the44line correction and ran isolated AST/synthetic fixtures. All21 final assertions passed; an initial fixture-expectation error and its correction remain recorded. No ADB/emulator, signing, device, or Gradle command was executed by that reviewer. Review and exact constraints: `../../reviews/android-managed-helper-independent-whodunit-cont.md`. **SAFE TO ATTEMPT is not runtime PASS**; actual execution and cleanup evidence belong to the root-owned build cycle.

### Actual first attempt and server-listener syntax correction

Root's `android-managed-01` attempt failed before Gradle/keytool/emulator launch: installed adb37does not accept numeric `127.0.0.1` in a **server listen** socket spec, although that hostname is valid on the separate client-connect path. Its exact observed stderr and source/binary verification are preserved in `../android-managed-listener-whodunit-cont/LISTENER_RETRY.md`. This is an audit-harness argv failure, not a Parlor application defect or executed test result. Root's cleanup passed; the owned temporary root and identity-matched generated crash report were removed.

The independently reviewed retry changes only the cycle label/unique-temp label and the server invocation to `adb -L tcp:localhost:PORT server nodaemon`. Client `ADB_SERVER_SOCKET=tcp:127.0.0.1:PORT` and port isolation remain unchanged. Exact installed ARM64 control flow and sockaddr constructors confirm this literal selects loopback, not all interfaces. Retry646line SHA256 `b34ad4662ac906bff0a2f4e8ebe25c0757229098361e8e34c29b7c06681730c0` was **SAFE TO ATTEMPT**, not yet runtime-verified at approval. Earlier74cfcoverage remains bound to preserved historical bytes rather than being silently retargeted.

Subsequent result: root completed `android-managed-02`; `/root/whodunit_cont` independently verified its actual3passing instrumentation methods, source/evidence binding, and completed task-owned cleanup. The narrow ARM64/API35release-smoke verdict is **PASS**, with recorded warnings and explicit physical-device/Store/UI/cross-platform limits. See `../../reviews/android-managed-execution-independent-whodunit-cont.md`; the earlier first-attempt failure remains preserved.
