# iOS setup

End-to-end recipe for opening Parlor on a Mac and running it on an iOS
Simulator or physical device. The minimum deployment target is iOS 16.

---

## What you need first

| Tool | Minimum version | Notes |
| --- | --- | --- |
| **Xcode** | 16.0 | Includes the iOS 16+ SDK and Simulator. Use the release toolchain recorded by the candidate. |
| **JDK** | 21 (Temurin / Zulu / Adoptium) | The Gradle toolchain auto-downloads if missing, but having one installed avoids first-build delay. |
| **Android Studio** | Koala (2024.1) or newer | Comes with the bundled JDK Parlor expects. |
| **Kotlin Multiplatform plugin** | bundled with current AS | Settings → Plugins → "Kotlin Multiplatform" must be enabled. Drives the **iOS App** run configuration. |
| **CocoaPods** | not required | Parlor uses the framework-link approach (`embedAndSignAppleFrameworkForXcode`) — no Podfile, no `pod install` step. |

First-time setup on a clean Mac, in order:

1. Install Xcode from the App Store; open it once so the licence prompt clears.
2. Install Android Studio; on first launch let it install the bundled SDK.
3. Open Settings → Plugins → search "Kotlin Multiplatform" → install + restart.
4. Clone the repo and open the Parlor repository root in Android Studio.
   Wait for Gradle sync — the first sync downloads the Kotlin/Native toolchain
   (~500 MB into `~/.konan/`); subsequent syncs are cached.

---

## Running the iOS app

### From Android Studio (recommended)

The repo ships a shareable run configuration at `.run/iOS App.run.xml`:

1. In the run-configuration dropdown (top toolbar), pick **iOS App**.
2. Pick a destination from the device dropdown next to it (a booted iOS Simulator,
   e.g. *iPhone 16 Pro*).
3. Click ▶ Run.

Under the hood, the configuration:

- Calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` to link the
  Kotlin framework for the selected SDK + architecture.
- Hands the build off to `xcodebuild` against `iosApp/iosApp.xcodeproj` and the
  `iosApp` scheme.
- Boots the simulator and installs the resulting `.app`.

### From Xcode (alternative)

If you want to use Xcode's debugger or signing UI directly:

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Select the **iosApp** scheme + an iPhone simulator.
3. Cmd+R.

The `Compile Kotlin Framework` build phase runs `./gradlew
:composeApp:embedAndSignAppleFrameworkForXcode` automatically; you do *not*
need to build the framework manually first.

### From the command line (CI smoke)

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj \
           -scheme iosApp \
           -configuration Debug \
           -sdk iphonesimulator \
           -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
           build
```

---

## Signing

`iosApp/Configuration/Config.xcconfig` ships with an empty `TEAM_ID`. Two options:

- **Simulator only** — leave `TEAM_ID` blank. Xcode uses an ad-hoc signature; the
  app installs on simulators without a developer account.
- **Real device** — set `TEAM_ID = ABCDE12345` (your Apple Developer team), or
  pick a team in Xcode's *Signing & Capabilities* tab. The latter writes to
  per-user data (`xcuserdata/`) and is gitignored, so it stays out of commits.

Bundle id is `com.parlor.app` (matches Android). Change it in `Config.xcconfig`
if you fork.

---

## What the project gives you

| File | Role |
| --- | --- |
| `iosApp/iosApp/iOSApp.swift` | `@main` SwiftUI app. One scene, one window. |
| `iosApp/iosApp/ContentView.swift` | Wraps `MainViewControllerKt.MainViewController()` (the Kotlin Compose entry) in a `UIViewControllerRepresentable`. |
| `iosApp/iosApp/Info.plist` | Bundle id, version, supported orientations, **`NSLocalNetworkUsageDescription`** for LAN play, Bonjour service names. |
| `iosApp/Configuration/Config.xcconfig` | TEAM_ID + BUNDLE_ID + APP_NAME — the only knobs you typically touch. |
| `iosApp/iosApp.xcodeproj/...` | Xcode project. Single app target, Debug + Release configurations, shared scheme so VCS picks it up. |
| `composeApp/src/iosMain/.../MainViewController.kt` | Kotlin side of the entry — produces the `UIViewController` that hosts `App()`. |

Linking flow:

```
Xcode build phase "Compile Kotlin Framework"
        ↓
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
        ↓
composeApp/build/xcode-frameworks/Debug/iphonesimulator/ComposeApp.framework
        ↓
Xcode picks it up via FRAMEWORK_SEARCH_PATHS in project.pbxproj
        ↓
Linker embeds ComposeApp.framework into iOSApp.app
```

No CocoaPods, no Swift Package Manager glue — just the framework path. Swap to
SPM/Pods later if a third-party iOS-only dependency lands.

---

## iOS-specific platform code

| File | What it does |
| --- | --- |
| `composeApp/src/iosMain/.../MainViewController.kt` | Compose entry + Koin bootstrap. |
| `composeApp/src/iosMain/.../permissions/P2pPermissionGate.ios.kt` | Maps real P2pKit LAN evidence to Unknown, Requesting, Operational, actionable denial, or unclassified failure; opens the app's Settings page without inventing a preflight result. |
| `composeApp/src/iosMain/.../storage/PlatformStorage.ios.kt` | Wires settings, secure key/value storage, and the protected snapshot store. |
| `composeApp/src/iosMain/.../storage/IosSnapshotFileSystem.kt` | Authenticated snapshot encryption with device-only Keychain keys; files use complete Data Protection under Application Support and are excluded from backup. |

`Info.plist` declares `NSLocalNetworkUsageDescription` and
`_p2pkit2._tcp` under `NSBonjourServices`. P2pKit uses Network.framework
Bonjour discovery and TCP; Parlor does not use MultipeerConnectivity or
Bluetooth and declares no Bluetooth usage description.

iOS has no truthful Local Network permission preflight. The first real host or
join attempt may trigger the system prompt. Parlor reports Operational only
after advertising or an authenticated connection succeeds. A generic timeout
is not called a denial. If the transport supplies actionable denial evidence,
the UI offers the app-specific Settings page; returning from Settings resets
the state to Unknown and the next real transport attempt determines the result.

---

## KMP targets the build wires up

Each shared module + `:composeApp` declares the three iOS targets via the
`parlor.kmp.library` convention plugin (see
`build-logic/convention/.../KmpLibraryConventionPlugin.kt`):

- `iosX64` — Intel-Mac simulator (rare on modern hardware).
- `iosArm64` — physical iPhones / iPads.
- `iosSimulatorArm64` — Apple-silicon Mac simulator (the common case).

`composeApp` exposes a framework named `ComposeApp` (`baseName`,
`isStatic = true`). That's the name `import ComposeApp` resolves to in the
Swift sources.

---

## Validation commands

### From Windows (klib / metadata only — Apple toolchain unavailable)

These validate metadata only; they do not link or run an iOS binary:

```powershell
gradlew.bat :composeApp:compileCommonMainKotlinMetadata
gradlew.bat :composeApp:compileIosMainKotlinMetadata
gradlew.bat :shared:engine:compileCommonMainKotlinMetadata
gradlew.bat :shared:session:compileCommonMainKotlinMetadata
gradlew.bat :shared:networking:compileCommonMainKotlinMetadata
gradlew.bat :shared:design-system:compileCommonMainKotlinMetadata
gradlew.bat :game-modes:whodunit:compileCommonMainKotlinMetadata
```

Linking the actual framework is **macOS-only** — Kotlin/Native cannot produce
iOS binaries from Windows. The Gradle build prints a warning and skips iOS
targets on non-Mac hosts.

### From macOS (the real validation)

```bash
# Production release frameworks for iosArm64, iosSimulatorArm64, and iosX64:
./gradlew productionAppleCheck --no-daemon --stacktrace --console=plain

# Focused debug simulator framework:
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# What Xcode's build phase invokes:
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode

# End-to-end build via Xcode:
xcodebuild -project iosApp/iosApp.xcodeproj \
           -scheme iosApp \
           -configuration Debug \
           -sdk iphonesimulator \
           -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
           build
```

The first iOS link on a fresh Mac downloads the Kotlin/Native toolchain
(`~/.konan/`); budget ~5 minutes once.

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Xcode build fails with `module 'ComposeApp' not found` | The framework search path is wrong, or the `Compile Kotlin Framework` build phase didn't run. | Build → Clean Build Folder, then run again. Confirm `composeApp/build/xcode-frameworks/Debug/iphonesimulator/ComposeApp.framework` exists. |
| `xcodebuild: error: SDK iphonesimulator cannot be located` | Xcode command-line tools point at the wrong Xcode. | `sudo xcode-select -s /Applications/Xcode.app` |
| Android Studio shows no **iOS App** run config | Kotlin Multiplatform plugin missing or disabled. | Settings → Plugins → enable "Kotlin Multiplatform" → restart AS. |
| Build prints "Kotlin/Native targets cannot be built on this machine" | You're on Windows / Linux. | Expected — open the project on macOS to link iOS binaries. |
| `linkDebugFrameworkIosSimulatorArm64` fails with `KLIB resolver` errors | Stale or mismatched P2pKit variants in the Gradle cache. | Confirm the catalog pins both P2pKit modules to the same published version, then retry with `--refresh-dependencies`. |
| Local Network prompt was denied or LAN still fails | iOS does not expose a reliable preflight; denial, Wi-Fi/routing, Bonjour, and firewall failures can look similar. | Use Parlor's recovery screen. Open the app-specific Settings page when offered, enable Local Network, return, and retry a real host/join. Capture privacy-safe `ParlorP2p` events; do not infer permission from timeout alone. |
| Sim screen blank on launch | Framework loaded but app bootstrap failed. | Open the Xcode console and inspect the Kotlin stack trace. `ParlorP2p` covers multiplayer lifecycle only, not general startup. |

---

## TL;DR for "I just want to run it"

```
1. Open Parlor in Android Studio on macOS.
2. Wait for Gradle sync.
3. Run config dropdown → "iOS App".
4. Device dropdown → any booted simulator.
5. ▶ Run.
```

Everything else in this file is for when something goes sideways.
