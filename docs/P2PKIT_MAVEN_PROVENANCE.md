# P2pKit 0.7.0-rc2 Maven provenance receipt

Receipt date: 2026-08-09.

This is the current release-input receipt for the P2pKit artifacts executed by
Parlor. It distinguishes the published dependency from the sibling P2pKit
checkout, which is useful for source review but is not part of Parlor's build.

## Resolution contract

Parlor pins these roots in `gradle/libs.versions.toml`:

```text
io.github.apdelrahman1911:p2p-core:0.7.0-rc2
io.github.apdelrahman1911:p2p-transport-lan:0.7.0-rc2
```

`settings.gradle.kts` declares Google, Maven Central, and the JetBrains Compose
repository. It declares no `mavenLocal()`, sibling P2pKit composite build, or
developer-home repository. The only `includeBuild` is Parlor's own
`build-logic`. Dependency insight for Android, JVM, and iOS selects the exact
0.7.0-rc2 platform variants. The optional P2pKit provisioning/manual-connect
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

The POM SCM block does not pin a tag or commit. The local repository has tag
`v0.7.0-rc2` at commit
`90acb29583ea11d18685cf1315476756e7618245` (tag date 2026-08-06). The local
checkout's current HEAD and uncommitted work are newer and are not executed by
Parlor. The tag is therefore the review reference, but the POM alone does not
cryptographically prove that every published binary was built from that tag.

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
| `p2p-core-0.7.0-rc2.module` | `7a92e4d038e11ff6532467462b7b5c3a441f4381f8bfa513c303bfc5744d1717` |
| `p2p-transport-lan-0.7.0-rc2.module` | `ef52db9f0bb29854b47204e70ffc166ba3fccd7d7e506c234bd3e26bd55c8b8b` |
| Android `p2p-core.aar` | `84d8a1c40a25ccf3481a4b1fc618c49647fa4179132f50fc1521f9ad5e1c861e` |
| Android `p2p-transport-lan.aar` | `34afa9ef9e7aa1a5e71b1f4a5411ee7f14f77347eba3770c24304623d800f00d` |
| JVM `p2p-core` JAR | `95f9d4aa0150e241265c512bb7a52b7c98a7e594726d6c3efe5057eab5cd8a6a` |
| JVM `p2p-transport-lan` JAR | `410906c2e4b0b69db3fce27992d3891b324da2b9acf930e6dc8e0a294dba7be7` |
| iOS arm64 `p2p-core` KLIB | `2329f33c79788713d764ca3e0bfd89bd2193bb7393dd50ca32936bace2a89043` |
| iOS simulator arm64 `p2p-core` KLIB | `990f23bfd8e11b4e34cbc0f2ff504118cc49c90254f973c263f7826123213ba9` |
| iOS x64 `p2p-core` KLIB | `030da4114aedabd12d566069b6604f8d5e1a99fa89829363b8c0ae426e5f2d43` |
| iOS arm64 LAN KLIB | `1065e7f413d56f714c24f62ae05ec7c77f5cc41e62f06539b45499a9ad7c6004` |
| iOS arm64 LAN cinterop KLIB | `ae330cf0ede29d67b6a2f637c16891f02c5e798eca624c7fe69eba35ceb7b3be` |
| iOS simulator arm64 LAN KLIB | `ad6029bf3b4361ba10ab6636739cdae98e54f80aadf2e7fbfc754a97417f3d0a` |
| iOS simulator arm64 LAN cinterop KLIB | `3e6bcf04ccde50831853c908319700383c6ef85dd7eb4dd93698b50ced9dc543` |
| iOS x64 LAN KLIB | `b07344ece10b82240d0214364c2035da43be650e91574305a983e9b430e74f32` |
| iOS x64 LAN cinterop KLIB | `87713d61ea5b014a1225bf0abc89002e0189fd64fcfc76a03b47cc5db8b54a16` |

The Gradle cache also contains older, unselected files under some identical
0.7.0-rc2 coordinates with different hashes. A content-addressed cache retains
old downloads, but does not retain enough repository-origin evidence to prove
where each older byte sequence came from. Current Gradle module metadata points
only to the checksums above, and current Maven Central values match them. This
is still a material reproducibility warning: do not delete the checksum guard,
silently regenerate it, or infer that a version string alone identifies the
reviewed bytes.

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
  replace published bytes under `0.7.0-rc2` or silently accept a checksum
  change.

Until those release-owner/legal receipts exist, checksum enforcement is PASS,
but the complete dependency/legal production gate remains UNVERIFIED.
