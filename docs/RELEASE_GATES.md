# Production release gates

Android and iOS are the mobile targets. Desktop supports development/tests and
the separately gated [GitHub distribution channel](GITHUB_DISTRIBUTION.md).
Desktop signing/notarization are mandatory for that production channel, not for
mobile Store validation or an ordinary `productionDesktopCheck`. The separate,
explicitly authorized [public testing prerelease channel](GITHUB_TEST_RELEASES.md)
uses unsigned/ad-hoc Desktop packages and an isolated test-signed Android APK;
it never qualifies those artifacts as signed production releases.

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
| Android device runtime | `scripts/android/run_release_managed_device_smoke.sh` | Both lanes are mandatory: black-box launch/settings-I/O/multicast checks on the R8-shrunk Release (API 35), then internal navigator assertions on Debug (API 34, below `List.removeLast` availability). Separate test source sets avoid retaining test-only APIs in the Store binary; no R8 keep-rule or optimization relaxation is used. Each Linux lane records its own descendant cleanup receipt. |
| iOS KMP release | `./gradlew productionAppleCheck` on macOS | Apple type-aware static analysis passes and Release frameworks link serially for `iosArm64`, `iosSimulatorArm64`, and `iosX64` without concurrent-LTO heap pressure. |
| Unsigned Swift Release wrapper | The Release `xcodebuild` command in `IOS_SETUP.md`/`RELEASE_RUNBOOK.md` with `ARCHS=arm64`, `ONLY_ACTIVE_ARCH=YES`, and signing disabled | The arm64 simulator `.app` builds, its executable and plist/privacy inputs are inspected, and its checksum is recorded. Other Kotlin/Native architectures remain independently covered by `productionAppleCheck`; neither result is physical-device runtime evidence. |
| Host-independent aggregate | `./gradlew productionCheck` | Desktop/common, Android unit, repository-wide Detekt/static-analysis, shell-dispatch validation, and unsigned Android release gates (including lint warning verification) pass. Apple remains separate macOS jobs. |
| Release automation security | `./gradlew productionReleaseAutomationCheck` | Candidate/provenance tampering tests, exact-tree/history tests, no-publication tests, workflow contracts, immutable Action pins, pinned ShellCheck/actionlint, and shell/YAML checks pass. |
| Desktop host compatibility | `./gradlew productionDesktopCheck` on Linux x64, Linux arm64, macOS arm64, macOS x64, and Windows x64; the x64 macOS/Windows jobs also run `:composeApp:downloadKotlinNativeDistribution`, and Windows runs `:composeApp:processDebugResources` | Each supported host resolves and executes its real host-selected dependency graph under strict verification. Linux arm64 support is Desktop-only because Kotlin Native does not publish a Linux arm64 host distribution. |
| Exact-candidate aggregate | Full verification: Linux runs `./gradlew productionCheck allTests`; independent macOS jobs run `./gradlew productionIosSimulatorRuntimeTests` and `./gradlew productionAppleCheck`, all with strict dependency verification | All six mandatory full jobs, including the Debug XCTest launch and unsigned Release wrapper/package inspection, pass in one full dispatch at the same recorded clean Git SHA. The focused protection diagnostic is skipped. No cross-run/SHA evidence stitching or duplicate common/desktop/Android aggregate on Apple. |
| GitHub distribution packaging | `.github/workflows/github-distribution.yml`, rehearsal | Android Release APK, macOS arm64/x64 DMGs, Windows x64 MSI and Linux x64 DEB; installed-image/native-graphics/crypto probes, notices, checksums and source-bound attestations. Unsigned rehearsal output is not publishable. |
| Public GitHub testing | `.github/workflows/github-test-release.yml` | Explicit build/publish dispatches; five native packages, isolated non-debuggable Android `.test` APK with disposable key and normal-install emulator smoke, exact-source six-job verification, closed test manifest and attestations. Public prereleases only, never Latest or signed-production acceptance. |

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
| Cross-platform synchronization | All registered games complete on Android host/iOS peer and iOS host/Android peer; simultaneous commands, snapshots, resume, terminal state, and private-state isolation have dated evidence. |
| Signed multiplayer artifacts | Required device rows rerun from Play internal and TestFlight-delivered candidates with artifact build IDs/checksums, not debug installs. |
| Accessibility | TalkBack and VoiceOver passes in EN and AR, 200% text, reduced motion, contrast, touch targets, and RTL. |
| Store/privacy | Final privacy policy/support URLs, Google Data safety, Apple privacy answers/manifest, age-rating questionnaires, export-compliance answer, and reviewer notes approved. |
| Legal | Product distribution license, third-party notices, content rights, trademarks, and dependency licenses approved. |
| Operations | Signed-artifact dependency/network inspection confirms no analytics or crash-upload provider; local `ParlorP2p` diagnostics remain bounded and redacted. Any future provider reopens privacy, consent, retention, and payload testing. |
| GitHub signing/publication | Protected exact-SHA acceptance; real Android key, Developer ID and Accepted/stapled notarization, timestamped Windows Code Signing identity; all five immutable platform variants and exact-source six-job qualification. Publication verifies independent certificate pins and downloads every final Release asset. No rebuild, Store upload or unsigned fallback. |

Device or store evidence must never be inferred from a simulator, compiler, or
unit test.

## CI policy

`.github/workflows/production-verification.yml` configures seven jobs: six
mandatory jobs in every full qualification and one opt-in protection diagnostic.
The six full jobs are:

- Linux x64: strict dependency verification, the root `productionCheck` and
  `allTests` aggregates, Android debug/release unit tests, repository-wide
  Detekt, release compilation/R8/lint plus the enforced
  `verifyReleaseLintWarnings` contract, unsigned AAB, merged-manifest
  inspection, and artifact hashes.
- Linux arm64: the real host-selected Desktop graph and tests. This job does
  not claim Kotlin Native support.
- macOS x64: the real host-selected Desktop graph, tests, and Kotlin Native
  distribution download.
- Windows x64: the real host-selected Desktop graph, tests, Kotlin Native
  distribution download, and Android resource processing through Windows
  `aapt2`.
- macOS arm64 runtime (`ios`): pinned Xcode `26.3` build `17C529` and iOS SDK-floor validation,
  every KMP `iosSimulatorArm64Test` through the dedicated
  `productionIosSimulatorRuntimeTests` aggregate, plist/privacy-manifest and
  effective Debug/Release identity validation, and an XCTest launch of the
  Debug SwiftUI/Compose app on its own fresh iOS Simulator.
- macOS arm64 release (`ios-release`): an independent checkout with the same
  pinned Xcode/SDK-floor guards, the **complete** `productionAppleCheck`
  aggregate (Apple type-aware static analysis and all three serial Release
  framework links), plist/privacy validation, an unsigned Swift Release wrapper
  build, and complete package/notices/identity/version inspection. The wrapper
  invokes the real Gradle resource/embed task with strict dependency verification.

The Apple jobs have no dependency on each other and no per-architecture matrix;
Release LTO links remain serialized. Runtime evidence is
`ios-runtime-verification`, Release evidence is `ios-release-verification`, with
separate run/job-bound ownership and cleanup receipts. Each full Apple job owns
exactly two cycles: `ios` owns `apple-aggregate`/`apple-ui`, while `ios-release`
owns `apple-aggregate`/`apple-wrapper`. Every cycle stops Gradle and retires its
owned resources immediately; upload custody is required before output deletion.

`verification_scope=ios-protection-probe` selects only the seventh job. It runs
an independently reviewed, source/control-bound standalone diagnostic with
collection and cleanup receipts. Default `native_selection=paired` retains the
same-inode protection diagnostic on its own simulator. Only the explicit
`protection-host-only` selection instead observes macOS loaded-image attribution
without an app build or simulator; it is not an app/native-continuation selection.
Other selections fail before diagnostic resource allocation. Neither diagnostic
is Parlor runtime, a replacement full gate, or proof of physical-device protection;
both are excluded from full runs. Successful diagnostic collection must not be
relabeled as a passed strict protection observation or application readiness.

The workflow deliberately labels framework linkage separately from executable
simulator runtime tests. A successful link is not reported as a runtime test.
Every full job prints the checked-out SHA and fails if the checkout is dirty.
All six jobs and their cleanup must succeed against one frozen source in the
same full dispatch; focused or historical passes cannot fill a missing full job.

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
