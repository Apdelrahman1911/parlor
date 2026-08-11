# P2pKit 0.7.0-rc3 Maven provenance receipt

Receipt date: 2026-08-11.

This is the current release-input receipt for the P2pKit artifacts executed by
Parlor. It distinguishes the published dependency from the sibling P2pKit
checkout, which is useful for source review but is not part of Parlor's build.

## Resolution contract

Parlor pins these roots in `gradle/libs.versions.toml`:

```text
io.github.apdelrahman1911:p2p-core:0.7.0-rc3
io.github.apdelrahman1911:p2p-transport-lan:0.7.0-rc3
```

`settings.gradle.kts` declares Google, Maven Central, and the JetBrains Compose
repository. It declares no `mavenLocal()`, sibling P2pKit composite build, or
developer-home repository. The only `includeBuild` is Parlor's own
`build-logic`. Dependency insight for Android, JVM, and iOS selects the exact
0.7.0-rc3 platform variants. The optional P2pKit provisioning/manual-connect
modules are not in the resolved graph or checksum allowlist.

Gradle's strict dependency verification is checked in at
`gradle/verification-metadata.xml`. It covers all artifacts exercised by
`productionCheck`, `productionAppleCheck`, and the repository-wide `allTests`
suite. A missing artifact or changed byte sequence fails resolution. Updating
the generated file is not routine formatting: it requires reviewing the graph,
the publisher, release notes, POM/license, and changed checksums, then updating
`P2pKitMavenProvenanceContractTest` deliberately.

## Published metadata and source reference

The root POMs currently served by Maven Central declare:

- project URL and SCM: `https://github.com/p2pKit/P2pKit`;
- license: Apache License 2.0; and
- publisher/developer: `Apdelrahman1911` / `Abdelrahman`.

The POM SCM block does not pin a tag or commit. The local repository has the
annotated tag object `v0.7.0-rc3` at
`e606bf0f0692a589e7106564353580354436f3ab`, peeling to commit
`deed9e77b81b6f1082519f44804de038eb5dcc0e` (tag date 2026-08-09). The tag is
annotated but not cryptographically signed. The local
checkout's current HEAD and uncommitted work are newer and are not executed by
Parlor. The tag is therefore the review reference, but the POM alone does not
cryptographically prove that every published binary was built from that tag.

RC3 states that it preserves RC2's public API and secure-v2 wire format. Its
release delta hardens Android/JVM routable interface binding and serialized
rebind cleanup, plus Apple browser-generation ownership, foreground recovery,
and write-ready cleanup. These are directly relevant to Parlor's pending LAN,
hotspot, lifecycle, and repeated-session device matrix. RC3 is built with
Kotlin 2.4.10 and kotlinx.serialization 1.11.0. Its Apple KLIB ABI cannot be
consumed by Parlor's former Kotlin 2.3.21 compiler, so the catalog pins the
matching Kotlin and serialization toolchain as part of the same reviewed
upgrade. RC3 still marks physical Android/Apple validation and hostile-network
testing as pending; Parlor does not treat this dependency upgrade as device
evidence.

Maven Central publishes detached signatures. The downloaded Android core and
LAN AARs both produced a cryptographically valid signature from:

```text
273D 83EA EDCC 24BA 90CA 4E78 6FD7 A2F6 DE03 19E7
```

The public key was retrieved from a public keyserver. Parlor does not yet have
an independently approved, out-of-band copy of this publisher fingerprint, so
the signature proves consistency with that key but not organizational trust in
the key owner. Release operations must obtain and approve that fingerprint
through a separate trusted channel before treating publisher identity as fully
verified.

## Selected runtime artifact checksums

Each SHA-256 below matched both the locally resolved artifact selected by
Gradle module metadata and Maven Central's published `.sha256` value on the
receipt date.

| Variant/artifact | SHA-256 |
|---|---|
| `p2p-core-0.7.0-rc3.module` | `8dfc573fe79bde06286c1183794ae72f622f6001610f97bb11871cace66ba463` |
| `p2p-transport-lan-0.7.0-rc3.module` | `3676a7f90593f5c8958c233fddaf30fdf17778ef1be09206add50d2e85c3c3d9` |
| Android `p2p-core.aar` | `a24fd6ce11b5a59d65b001748d8a82747edea2491e80ae4e7814d03d855ff50f` |
| Android `p2p-transport-lan.aar` | `4b1363f54c35db6749909e92b1bf9bdd6126218f79f1edefb8809ef85d9020d4` |
| JVM `p2p-core` JAR | `4ffb18b77cf55900ab8210c0e382bef9a25421de9196caa326d79c2da1cb5593` |
| JVM `p2p-transport-lan` JAR | `571d8464b069f42e073244194fc338d3199ea5e9e63b0ba600a19e17058fcb1d` |
| iOS arm64 `p2p-core` KLIB | `e33731c2c8151888d7c1cf0d42b38288ddcda16ccb52726f7645543ef9f95c93` |
| iOS simulator arm64 `p2p-core` KLIB | `06b855509d5cd9f1cc27adbdf3f343beb053a781de499edf27c67abe53fb8492` |
| iOS x64 `p2p-core` KLIB | `b6ec8f13ce44bb02572373ca89b7cd70c62d3c344a7ae918fb75e07cbfb9fae6` |
| iOS arm64 LAN KLIB | `66dd89cf562c7729307b77fda2d9e584d6100b78d5a5868b2a726794000e8b4a` |
| iOS arm64 LAN cinterop KLIB | `bd9b60aca6f4dac875cccc61f3c5d3522ffaffade624e8342d9b49ac22e4c760` |
| iOS simulator arm64 LAN KLIB | `d6bb064670f34ff04d3b2d8daf4cbac5af5b9aab2e524ee8765bfc9f7d98d61f` |
| iOS simulator arm64 LAN cinterop KLIB | `7fccf58d3e6e6c920760333db831db5bbff04229c2f561c69433f41768da5e5d` |
| iOS x64 LAN KLIB | `13ff3e4b528bba102e64ca8f8589f6d42d939afc5bc9239957f9c794011b5543` |
| iOS x64 LAN cinterop KLIB | `7ba9a44b204461e26c28c45c58c755c8ee8ed179caa5e57b6ccf3d298dfa675d` |

The checked-in verification metadata retains previously reviewed RC2 entries,
but dependency resolution selects only RC3 for Parlor. Current Gradle module
metadata points to the RC3 checksums above, and the downloaded Central bytes
match them. Retained historical checksum entries are not a fallback or an
allowed version range: the catalog and executable contract require RC3. Do not
delete the checksum guard, silently regenerate it, or infer that a version
string alone identifies reviewed bytes.

## Commands in this receipt

```bash
./gradlew :shared:transport-p2p:dependencyInsight \
  --dependency p2p --configuration desktopRuntimeClasspath
./gradlew :composeApp:dependencyInsight \
  --dependency p2p --configuration releaseRuntimeClasspath
./gradlew :shared:transport-p2p:dependencyInsight \
  --dependency p2p --configuration iosArm64CompileKlibraries
./gradlew productionCheck productionAppleCheck \
  --write-verification-metadata sha256 --no-daemon --stacktrace --console=plain
./gradlew allTests --write-verification-metadata sha256 \
  --no-daemon --stacktrace --console=plain
```

Generation mode records reviewed bytes but does not itself prove enforcement.
The same gates must then pass without `--write-verification-metadata`, using
strict verification, on the final committed release candidate.

## Remaining provenance and legal gates

- Approve the publisher fingerprint through a trusted channel.
- Generate an Android and Apple release SBOM from the final resolved graphs.
- Review all transitive licenses and advisories and generate third-party
  notices from those exact graphs.
- Archive Central metadata/checksum/signature evidence and full dependency
  graphs with the signed release SHA.
- Prefer a new immutable P2pKit version for any upstream code change. Never
  replace published bytes under `0.7.0-rc3` or silently accept a checksum
  change.

Until those release-owner/legal receipts exist, checksum enforcement is PASS,
but the complete dependency/legal production gate remains UNVERIFIED.
