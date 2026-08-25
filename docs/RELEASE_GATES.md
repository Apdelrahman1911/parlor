# Production release gates

Android and iOS are shipping targets. Desktop is a development and deterministic
test target; desktop packaging, signing, and notarization are not release gates.

No single Gradle task proves store readiness. Automated gates prove source and
unsigned artifact quality. Signing, devices, privacy forms, and store review
remain separate evidence.

## Automated gates

| Gate | Command | Passing evidence |
|---|---|---|
| Common/domain/desktop tests | `./gradlew productionDesktopCheck` | Every KMP module's `desktopTest` passes; the app's desktop main code compiles as a dependency. |
| Repository test aggregate | `./gradlew allTests` | The explicit root aggregate runs every KMP module's `allTests` task that exists; platform-host limitations are reported by Gradle rather than silently omitted. |
| iOS simulator runtime tests | `./gradlew productionIosSimulatorRuntimeTests` on Apple Silicon macOS | Every KMP module's executable `iosSimulatorArm64Test` task runs; new KMP modules join automatically. This is runtime-test evidence for the simulator only, not physical-device evidence. |
| iOS app launch UI test | The `xcodebuild test` command in `IOS_SETUP.md` | The real SwiftUI wrapper launches on an iOS Simulator, instantiates the exported Kotlin controller, renders the Compose home screen, remains foreground, and presents no alert during the observation window. This is simulator launch evidence, not Local Network permission evidence. |
| Android release | `./gradlew productionAndroidCheck` | Android debug and release unit tests, release Kotlin compilation, R8, unsigned release AAB, `lintRelease`, and the allowlist-enforcing `verifyReleaseLintWarnings` task all pass. |
| iOS KMP release | `./gradlew productionAppleCheck` on macOS | Release frameworks link serially for `iosArm64`, `iosSimulatorArm64`, and `iosX64` without concurrent-LTO heap pressure. |
| Unsigned Swift Release wrapper | The Release `xcodebuild` command in `IOS_SETUP.md`/`RELEASE_RUNBOOK.md` with `ARCHS=arm64`, `ONLY_ACTIVE_ARCH=YES`, and signing disabled | The arm64 simulator `.app` builds, its executable and plist/privacy inputs are inspected, and its checksum is recorded. Other Kotlin/Native architectures remain independently covered by `productionAppleCheck`; neither result is physical-device runtime evidence. |
| Host-independent aggregate | `./gradlew productionCheck` | Desktop/common, Android unit, repository-wide Detekt/static-analysis, shell-dispatch validation, and unsigned Android release gates (including lint warning verification) pass. Apple remains a separate macOS job. |
| Release automation security | `./gradlew productionReleaseAutomationCheck` | Candidate/provenance tampering tests, exact-tree/history tests, no-publication tests, workflow contracts, immutable Action pins, pinned ShellCheck/actionlint, and shell/YAML checks pass. |
| Exact-candidate aggregate | Linux runs `./gradlew productionCheck allTests`; macOS runs `./gradlew productionIosSimulatorRuntimeTests productionAppleCheck`, both with strict dependency verification | Every configured automated suite and unsigned Android/Apple release gate passes at the same recorded clean Git SHA without duplicating common/desktop/Android tests on the expensive Apple runner. |

The root tasks discover KMP modules through the multiplatform plugin. A newly
included game module therefore joins the desktop gate automatically.

Production dependency resolution uses the pinned P2pKit Maven Central
coordinates. Release CI must not use `mavenLocal()`, a sibling checkout, a
repository override, or a developer home repository. Gradle's checked-in
verification metadata is enforced in strict mode by default; generation mode
is prohibited in ordinary CI and release builds.

Automated evidence is attributable only when the command starts and finishes
at the same recorded commit and `git status --short` is empty. A later code or
repository-contract change invalidates that receipt and requires a new run.

## External gates

These are `UNVERIFIED` until the release evidence folder contains a dated
receipt:

| Gate | Required receipt |
|---|---|
| Android signing | `./gradlew productionAndroidSigningCheck --no-configuration-cache` with protected credentials, then a signed AAB receipt; Play App Signing enrollment confirmed; key fingerprints recorded out of band. |
| iOS archive/signing | Successful Release archive using Xcode `26.3` build `17C529` and physical iOS SDK major `26` or newer, with the distribution certificate and provisioning profile in the intended App Store Connect team. The IPA validator also proves deployment target `16.0` and device-platform Mach-O metadata. |
| Android devices | Canonical `P2P_MANUAL_TEST.md` rows on supported APIs: Android-to-Android in both host directions, three-device play, normal LAN, relevant hotspot topologies, background/foreground, network change, transient resume versus final Leave, host exit, rematch, and repeated sessions. Confirm no Nearby/Location runtime prompt appears. |
| Apple devices | Canonical rows on physical iPhone/iPad pairs and mixed Android/iOS pairs in both host directions, including Local Network denial/Settings recovery, three devices, normal LAN, applicable Personal Hotspot topologies, lifecycle/process death, and repeated sessions. |
| Cross-platform synchronization | Both games complete on Android host/iOS peer and iOS host/Android peer; simultaneous commands, snapshots, resume, terminal state, and private-state isolation have dated evidence. |
| Signed multiplayer artifacts | Required device rows rerun from Play internal and TestFlight-delivered candidates with artifact build IDs/checksums, not debug installs. |
| Accessibility | TalkBack and VoiceOver passes in EN and AR, 200% text, reduced motion, contrast, touch targets, and RTL. |
| Store/privacy | Final privacy policy/support URLs, Google Data safety, Apple privacy answers/manifest, age-rating questionnaires, export-compliance answer, and reviewer notes approved. |
| Legal | Product distribution license, third-party notices, content rights, trademarks, and dependency licenses approved. |
| Operations | Signed-artifact dependency/network inspection confirms no analytics or crash-upload provider; local `ParlorP2p` diagnostics remain bounded and redacted. Any future provider reopens privacy, consent, retention, and payload testing. |

Device or store evidence must never be inferred from a simulator, compiler, or
unit test.

## CI policy

`.github/workflows/production-verification.yml` runs two required jobs:

- Linux: strict dependency verification, the root `productionCheck` and
  `allTests` aggregates, Android debug/release unit tests, repository-wide
  Detekt, release compilation/R8/lint plus the enforced
  `verifyReleaseLintWarnings` contract, unsigned AAB, merged-manifest
  inspection, and artifact hashes.
- macOS: pinned Xcode `26.3` build `17C529` and iOS SDK-floor validation,
  every KMP `iosSimulatorArm64Test` through the dedicated
  `productionIosSimulatorRuntimeTests` aggregate, release framework linkage
  for all supported Apple targets (linkage-only for `iosArm64`/`iosX64` on
  this job), plist and privacy-manifest validation, an XCTest launch of the
  SwiftUI/Compose app on an iOS Simulator, and an unsigned Xcode Swift Release
  wrapper build. The wrapper invokes the real Gradle resource/embed task with
  strict dependency verification.

The workflow deliberately labels framework linkage separately from executable
simulator runtime tests. A successful link is not reported as a runtime test.
Both jobs record the checked-out SHA and fail if the checkout is dirty.

The workflow has read-only repository permission and receives no signing or
Store secrets on pull requests. Signed delivery and promotion use the separate
manual protected-environment workflows defined in
[`RELEASE_AUTOMATION.md`](RELEASE_AUTOMATION.md). Candidate creation builds once
on `testing`; external and production workflows promote recorded Store IDs and
contain no mobile compilation/signing command.

Action revisions and the actionlint/ShellCheck/bundletool release checksums are
locked in source and mechanically enforced. Review their official release pages
before updating:

- <https://github.com/actions/checkout/releases>
- <https://github.com/actions/setup-java/releases>
- <https://github.com/actions/upload-artifact/releases>
- <https://github.com/actions/attest-build-provenance/releases>
- <https://github.com/rhysd/actionlint/releases>
- <https://github.com/koalaman/shellcheck/releases>
- <https://github.com/google/bundletool/releases>

Dependabot proposes action updates. Updating an action still requires its
release/security notes and CI result to be reviewed.
