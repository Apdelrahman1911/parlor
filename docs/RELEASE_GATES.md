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
| Android release | `./gradlew productionAndroidCheck` | Unsigned release AAB builds and `lintRelease` reports no blocking finding. |
| iOS KMP release | `./gradlew productionAppleCheck` on macOS | Release frameworks link for `iosArm64` and `iosSimulatorArm64`. |
| Host-independent aggregate | `./gradlew productionCheck` | Desktop/common and unsigned Android gates pass. Apple remains a separate macOS job. |

The root tasks discover KMP modules through the multiplatform plugin. A newly
included game module therefore joins the desktop gate automatically.

Production dependency resolution uses the pinned P2pKit Maven Central
coordinates. Release CI must not use `mavenLocal()`, a sibling checkout, a
repository override, or a developer home repository.

## External gates

These are `UNVERIFIED` until the release evidence folder contains a dated
receipt:

| Gate | Required receipt |
|---|---|
| Android signing | `./gradlew productionAndroidSigningCheck --no-configuration-cache` with protected credentials, then a signed AAB receipt; Play App Signing enrollment confirmed; key fingerprints recorded out of band. |
| iOS archive/signing | Successful Release archive using the distribution certificate and provisioning profile in the intended App Store Connect team. |
| Android devices | Full two-peer lifecycle on the supported API/device matrix, including denied permissions, background/foreground, network change, disconnect/rejoin, host exit, rematch, and repeated sessions. |
| Apple devices | The same lifecycle on physical iPhone/iPad pairs and mixed Android/iOS pairs on representative Wi-Fi networks. |
| Accessibility | TalkBack and VoiceOver passes in EN and AR, 200% text, reduced motion, contrast, touch targets, and RTL. |
| Store/privacy | Final privacy policy/support URLs, Google Data safety, Apple privacy answers/manifest, age-rating questionnaires, export-compliance answer, and reviewer notes approved. |
| Legal | Product distribution license, third-party notices, content rights, trademarks, and dependency licenses approved. |
| Operations | Opt-in analytics and crash reporting tested separately; both default off; payload/log redaction reviewed. |

Device or store evidence must never be inferred from a simulator, compiler, or
unit test.

## CI policy

`.github/workflows/production-verification.yml` runs two required jobs:

- Linux: common/desktop tests plus Android release bundle and lint.
- macOS: physical-device and simulator iOS release framework links.

The workflow has read-only repository permission and receives no signing or
store secrets on pull requests. Signed delivery belongs in a separately
approved protected-environment workflow after the unsigned gates pass.

The action versions were checked against the official GitHub release pages on
2026-07-28:

- <https://github.com/actions/checkout/releases>
- <https://github.com/actions/setup-java/releases>
- <https://github.com/actions/upload-artifact/releases>

Dependabot proposes action updates. Updating an action still requires its
release/security notes and CI result to be reviewed.
