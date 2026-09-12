# Binary assets and wrapper — bounded integrity review

Reviewer `/root/mafia_cont`; `main` / commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. No tracked changes. Fresh `coverage/inventory.jsonl` enumerates six applicable binaries: three PNGs, wrapper JAR and two fonts. Local `.DS_Store` records remain excluded/preserved; no private configuration, signing material, credentials or player data inspected.

## PNGs and shipping reachability

- `assets/branding/parlor-app-icon-master.png`:1024×1024 RGB8 PNG, noninterlaced, no alpha/tRNS; SHA256 `6bdfc367b09566bf55d182ae595ca5222eb1fd800c76a2c107cba5e37f8312b5`.
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/ParlorAppIcon.png`:same dimensions/format and **byte-for-byte identical** to master. This shipping copy is referenced by AppIcon Contents.json and PBX app Resources phase208–218; both app configurations select AppIcon402/433. Master sits outside shipping source/resource roots, with no build/script source reference found; it is a branding reference rather than a second bundled icon.
- `composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_foreground.png`:432×432 RGB8, noninterlaced, no alpha/tRNS; SHA256 `1adbb0675ee2a01b9a76eff394552d3cbb86d00df33a5ac4604752d5d27ee551`. Its non-background pixels are entirely within inclusive72..359 on both axes; uniform exterior RGB16,5,14 matches `ic_launcher_background.xml:#10050E`. Manifest13/15 references both launcher definitions, each referencing this foreground, background and the monochrome vector.

Actual methods: `file`5.41, `sips`316 metadata, local image viewing of master and Android foreground, exact byte comparison of iOS/master, and a bounded stdlib parser checking every PNG chunk extent/CRC, terminal IEND/no trailing bytes, full zlib scanline decoding and row filters. All three passed. No transformed image was written. Ancillary structure was inspected separately: sRGB or gAMA/cHRM,68-byte EXIF with an ExifIFD pointer, and Android461-byte XML XMP carrying color-space/pixel-dimension element names. Personal attribute/payload values and nested EXIF values were not extracted or logged. This is not a claim of exhaustive semantic EXIF inspection.

Full text reads cover both adaptive icon XMLs6lines each, monochrome vector35, background4, Android manifest33, app-icon Contents14 and asset Contents6. Bitmap appearances are recognizable candle/group branding. No demonstrated binary-format defect discovered. Launcher masks/themed icons, small-size recognition, platform color rendering, copyright ownership and Store approval remain unverified; source/metadata inspection is not device or legal proof.

## Wrapper integrity and authoritative research

Local `gradle/wrapper/gradle-wrapper.jar` is43,705bytes; SHA256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. It **matches** Gradle's exact8.13 official wrapper checksum fetched over HTTPS on2026-09-05. ZIP inspection found33unique entries,71,847uncompressed bytes, valid entry CRCs,31class files with class version50.0, plus Apache license and a Gradle Wrapper manifest; no entries extracted to disk and no JAR execution performed.

Configured `gradle-wrapper.properties:3` distribution SHA256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` **matches** the official8.13-bin checksum. This checks the configured pin, **not** a locally downloaded/cached distribution ZIP. No global cache was inspected or removed. Properties8lines and complete wrapper launch scripts251/94lines read. Both scripts launch the checked-in JAR's `org.gradle.wrapper.GradleWrapperMain`; properties select HTTPS8.13-bin,10s timeout and URL validation.

Authoritative references, exact access times/final redirect URLs/response hashes: `evidence/binary-assets-wrapper/research-receipts.json`.

- https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256 → downloads.gradle.org, HTTP200. Exact pinned wrapper identity comparison.
- https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256 → downloads.gradle.org, HTTP200. Exact configured distribution-pin comparison.
- https://docs.gradle.org/8.13/userguide/gradle_wrapper.html — exact8.13 guide explains manually checking the wrapper JAR against that endpoint and configuring `distributionSha256Sum`. It explicitly warns distribution checksum verification occurs only when not already downloaded. Relevant source excerpt preserved; no blanket claim cached runtime integrity is made.

Official checksum equality is binary integrity evidence, not a proof all upstream Gradle logic is defect-free or that application builds pass. No Gradle, JVM, Xcode, app, server or device execution occurred.

## Fonts and coverage

Both font hashes were rechecked against this reviewer's existing `evidence/design-font-metadata.json` and prior `BINARY_INSPECTED` receipts: Inter `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`, JetBrains Mono `48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda`. Those earlier SFNT table/name/glyph-count/embedding metadata inspections remain applicable; this continuation did not claim new glyph rendering or license authentication.

## Execution qualifications and cleanup

Reproducible read-only helper: `reproducers/inspect_binary_assets_mafia_cont.py`; final command `/usr/bin/python3 -B ...`, Python3.9.6, exit0. `inspection.json` holds exact timestamps/hashes/results. A prior successful Python pass was followed by an audit-shell failure because zsh reserves `status`; corrected `audit_rc` harness exited0. A separate initial ancillary XML parse failed on the already-known Homebrew3.14.6/expat host incompatibility; retry with `/usr/bin/python3 -B` exited0. These failures are execution receipts, not application findings or ignored failures.

Retained evidence is compact JSON, official64-byte checksum responses and the relevant documentation excerpt. Unneeded task-created full HTML and flattened documentation were removed after preserving source hashes/excerpt. No build outputs, Python bytecode in this task's directories or temporary image files were created; no task processes remain active. Child execution sessions completed. Root owns Gradle's single lane and stop/clean receipts; this reviewer did not issue `--stop` against someone else's work. Working-tree status remains the same preserved untracked paths, with this audit's isolated evidence additions only.
