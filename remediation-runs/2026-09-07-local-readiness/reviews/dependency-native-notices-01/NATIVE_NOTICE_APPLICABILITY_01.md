# Exact-input native dependency notice investigation

Reviewer: `/root/native_fix_review`. Completed 2026-09-07 (UTC).

## Scope and result

This is a **read-only, source- and archive-backed notice-input investigation**, not
a complete upstream code audit, final-link map, publisher legal approval, or new
application-defect register. It supplies locally actionable notice material; it
does **not** move all license/declaration work into an external-gates bucket.

Delivered **25 verbatim draft notice inputs, 94,484 bytes**: the original
`draft-notice-inputs-01/` (21 files) and additive
`draft-notice-inputs-supplement-02/` (4 files). Each directory has a provenance,
hash, classification, and applicability manifest. These are evidence, not yet
production assets. The second directory does not overwrite the first manifest.

The most important corrections to a POM-only inventory are:

* Skiko's Apache-2.0 POM does not classify all native code inside its KLIB.
* Actual iOS input archives contain Skia, ICU, HarfBuzz, Expat, WebP, JPEG, DNG,
  Piex, zlib, PNG, and Wuffs, with different attribution or other terms.
* HarfBuzz root `COPYING` is insufficient to capture the observed `hb-ucd.cc`
  ISC copyright. The generated table identifies Unicode 16.0.0 data.
* Kotlin/Native runtime/stdlib has relevant ThreeTenBP, Harmony, and bounded-queue
  notices; blindly copying the entire compiler's license inventory is also wrong.
* Android Bouncy Castle and SLF4J license bytes already exist in their exact
  resolved input JARs. An earlier AAB already included Bouncy Castle's text.

## Reviewed baseline and provenance

The input observations use the root-owned strict graph export 01:

* Commit: `2085367999a1b6e998567e83e978785f02607b3d`
* Tree: `887a4e26d8f43198d1bd8c4e4e6727fc126c4f8c`
* Working diff SHA-256:
  `dc3d56de309162dd68564942c916a18fb90ede396700e2d6b35f4f1e6da4c6c1`
* Graphs: `../../evidence/dependency-graph-export-01/raw-graphs/` relative to
  this report's directory, or the explicit absolute paths in methodology.

Final HEAD observation: `11210dc6e34811bd1dd36d6a34339851601e5112` on
`fix/local-readiness-2026-09-07`. I independently compared the existing export 02
against export 01: all four graph component/edge records and unique exact-byte
artifact sets match. Android's 130-to-129 record change deduplicates the same-byte
`androidx.core:core:1.16.0` alias. This is a metadata comparison, **not a new build
or resolution by this reviewer**. Root's separate export-02 integrity review is
`../dependency-export02-independent-integrity-review-03.json`.

### Exact upstream versions

| Input | Version / source commit |
| --- | --- |
| Skiko | `0.9.37.4`, `84b34aa8bf3eea606464aba0cc9b12f94bc3554e` |
| Skia package | `m138-80d088a-1`, package commit `9481c9b3b8e740d240c7f300cf3a0398abbdb052` |
| Skia | `80d088ad6ee869e6b511763b5238cff5de72684d` |
| Kotlin/Native | `2.4.10`, `5687445832cd835b4509b9fbc264cdf1a8201093` |
| Bouncy Castle | `org.bouncycastle:bcprov-jdk18on:1.85` |
| SLF4J | `org.slf4j:slf4j-api:2.0.16` |

`fetch-receipts-01.json` through `fetch-receipts-22.json` retain authoritative
URLs, access timestamps, response/error status, sizes, and hashes. Source is from
the applicable JetBrains tags/commits, exact Skia DEPS revisions on upstream
Gitiles servers, Unicode's version-16 directories/current terms, or the exact
resolved cached JARs. Failed candidate URLs are recorded, not silently passed.

## Actual input inspection, not assumed packaging

1. **243 KLIB publications**: 81 each for iOS arm64, simulator arm64, and x64.
   Every cache match agrees with the exported size and SHA-256. ZIP directories,
   manifests, native binary member names, and legal-named entries were inspected.
   Fifteen inputs per platform already contain AndroidX license text. Nine per
   platform contain native binary members. This does not mean those legal files
   are automatically copied into the final framework or app.
2. **Skiko arm64 native archives**: a streaming ZIP/universal-Mach-O/BSD-ar parser
   inspected 21 archive inputs. Most have arm64 and arm64e slices. The bridge
   archive is thin. `libskia.a` itself contains copies of other libraries' object
   units, so a list of only separate archive names undercounts embedded inputs.
   The initial ar-only parsing attempt failed safely; the corrected result
   explicitly handles fat envelopes. No extracted binaries remain on disk.
3. **Kotlin/Native distribution**: hashes for 504 stdlib files and 80 iOS-target
   runtime bitcode files; ten retained upstream license files byte-match the
   installed 2.4.10 distribution. Manifest producer compiler strings are not
   substituted for the actual app compiler version.
4. **Android MIT input archives**: exact resolved/cache-matched JARs were inspected
   before any online lookup. Their complete `META-INF` license texts are copied
   verbatim, including SLF4J's line endings.

The two principal archive evidence files are
`resolved-ios-klib-internal-inventory.json` and
`skiko-ios-arm64-static-archive-inventory.json`. The methodology ledger records
all retained reference hashes and honest full/partial/read-not-claimed ranges.
Neither archive inventory is a claim that every member survives dead stripping.

## Actionable notice applicability

### Skiko / Skia inputs

Skiko `publishing.kt:87–127` stamps Apache-2.0 on all publications.
`NativeTasksConfiguration.kt:190–215,289–296` includes the native archives.
`SkikoProjectContext.kt:30–69,154–161` chooses the Skia package;
`skiko/gradle.properties` selects `m138-80d088a-1`. The package checkout script
binds that short hash to the retained exact Skia commit and DEPS.

| Observed input | Draft material / classification |
| --- | --- |
| Skiko | Shared Apache-2.0 text plus exact 3-line Skiko NOTICE (AOSP adaptation). |
| Skia and associated Skia libraries | BSD-3-Clause Skia LICENSE. Observed `skcms` headers refer to this BSD family; do not relabel as Apache. |
| ICU / skunicode_icu | Full exact upstream combined LICENSE. It includes notices for runtime-derived code and data. GPL build-script sections do **not** make the application or ICU runtime GPL. |
| HarfBuzz | Exact root Old-MIT COPYING **plus** observed `hb-ucd.cc:1–15` ISC notice, Grigori Goronzy (2012). Root COPYING explicitly directs readers to per-file notices. Other per-file headers have not all been audited. |
| Unicode table | `hb-ucd-table.hh:1–8` identifies generated Unicode 16.0.0 XML. Version-16 XML/readme/index direct to official Terms of Use, which explicitly apply Unicode License v3 to Public data unless otherwise specified. The supplied text is the current, access-dated 1991–2026 grant, **not an invented immutable 2024 text**. XML copyright says 2023; accompanying version-16 readme says 2024; neither is rewritten. |
| Expat | Exact MIT COPYING with upstream copyright. |
| WebP | BSD-3-Clause COPYING plus companion PATENTS text. |
| DNG SDK | Exact Adobe custom NOTICE/license and PATENTS. **Not Apache-2.0.** Retain the statement: “This product includes DNG technology under license by Adobe Systems Incorporated.” Patent applicability/acceptance and commercial-product terms require the publisher's actual decision, not a manufactured approval. |
| libjpeg-turbo | Exact LICENSE.md and README.ijg; observed IJG/libjpeg API and SIMD objects, not TurboJPEG API objects. Include “This software is based in part on the work of the Independent JPEG Group.” |
| Piex | Its LICENSE and NOTICE repeat the Apache-2.0 text; common text may be deduplicated while component attribution remains explicit. |
| Wuffs | Actual `libwuffs.wuffs-v0.3.o` and Skia wrapper are present. Exact generated source identifies `0.3.3+3399.20230408` and Apache-2.0. Wrapper has separate Google 2018 BSD text, retained as a distinct input. |
| PNG, zlib | Inputs present; retain their exact licenses as useful documentation. Both say binary-product acknowledgement is appreciated, **not required**. Do not inflate a missing-notice defect from this alone. |

HarfBuzz `hb-ucd.cc` includes the generated table and exposes Unicode functions.
The matched Skia HarfBuzz BUILD and `config-override.h` do not define
`HB_NO_UCD`. Actual archives contain `libharfbuzz.hb-ucd.o`. This is stronger
input/source attribution than merely finding a dependency in an upstream tree,
but still not a final app-link survival proof.

### Kotlin/Native runtime and stdlib

| Candidate | Source-backed disposition |
| --- | --- |
| ThreeTenBP | Applicable stdlib attribution. Exact native/common `Instant.kt:7–9` names ThreeTenBP; first-party `shared/core/.../time/Clock.kt` uses `kotlin.time.Clock.System.now()`, and `GameSnapshot.createdAt` is `Instant`. BSD-3-Clause binary notice retained. |
| Apache Harmony | Applicable runtime/stdlib NOTICE: regex and floating-point parsing/conversion contain Harmony-derived code. Exact native dtoa README/source headers and runtime-main build wiring inspected. Retain Harmony NOTICE and common Apache text. |
| Dmitry Vyukov MPMC queue | Applicable default-runtime notice. Pinned 2.4.10 compiler defaults to **CMS**, not historical PMCS (`NativeSecondStageCompilationConfig.kt:159–161`). `ConcurrentMark.hpp:13,48` → `ParallelProcessor.hpp:13,279` → `BoundedQueue.hpp:6–24`. No tracked app GC override observed. BSD-2-Clause binary notice retained. |
| Boost / UTFCPP | Actual native utility/header attribution; exact BSL text explicitly exempts solely machine-executable object code from its notice-copy requirement. Useful optional text, not an independently mandatory missing binary notice. |
| libbacktrace | **Linkage unresolved.** Distribution contains its bitcode, but pinned compiler `sourceInfoType:229–232` defaults to NOOP or supported Debug CoreSymbolication. Do not infer app inclusion merely from distribution presence. Candidate text retained separately. |
| Legacy Kotlin Unicode notice | **Precise data applicability unresolved.** The distribution's 1991–2005 Unicode text is not proof that it licenses every current generated Unicode table. Do not substitute it for the observed HarfBuzz Unicode-16 data grant. |
| Compiler/LLVM/libffi tooling | Not automatically application dependencies; no blanket application obligation inferred from the compiler's complete NOTICE or toolchain installation. |

Kotlin's `FrameworkBuilder.kt` constructs headers/modules/plist without license
copying in that file. That narrow source observation alone does **not** prove
that no other packaging path supplies notices.

### Android Bouncy Castle and SLF4J

* Bouncy Castle JAR SHA-256:
  `20af26bf6060bb8005cc2389916812c1e0e998dc48d2ced7131b89461b54cff7`.
  `META-INF/LICENSE.md`: 1,171 bytes,
  `0e01f1549c9022f406392ac2947d32223b7c2e977d21ea2f8c182fdeb4dae5fd`.
  MIT, copyright 2000–2026 The Legion of the Bouncy Castle Inc.
* SLF4J JAR SHA-256:
  `a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a`.
  `META-INF/LICENSE.txt`: 1,178 bytes,
  `4e7f90c86ab51278228bce153122f1d8df30149d13ce9ef524c8444a84c32dcc`.
  MIT, copyright 2004–2022 QOS.ch Sarl.
* The Android graph binds Bouncy Castle via P2pKit's Android core and SLF4J via
  Ktor/coroutines-SLF4J/JmDNS paths. These are not iOS-native input claims.

## Counter-evidence and remaining work

**Preserve existing rejected candidates.** The earlier inspected AAB (hash
`f30780bf995741a372646dd9c6a27705cb4eb0368c1d5a4de7197386d3a61473`)
had 354 ZIP entries including Bouncy Castle's `base/root/META-INF/LICENSE.md`
and five AndroidX license files. “Android ships no licenses” is false. That
earlier AAB is not a current-source packaging receipt. Existing native04 binary
inventory is Mach-O-only and cannot prove absence of resource notices.

The existing font-license rejection is **unchanged**. SFNT name-table fields
13/14 in the two bundled fonts contain the observed OFL statement and link;
they are not the full OFL text. This observation does not reverse the earlier
independent font disposition or invent a new font defect.

Remaining locally actionable work for the root/publisher:

1. Independently review these inputs and the exact resolved POM/source inventory;
   include accurate component-specific notices in shipping materials rather than
   a blanket “all Apache” declaration. Keep optional/unproven inputs labelled.
2. Verify actual final Android AAB and complete iOS app/framework resource contents
   after any notice/resource fix; bind receipts to that exact changed source.
3. If excluding optional native components/notices, obtain a final linker-survival
   map rather than assuming unused app features remove every archive object.
4. This bounded task did not scan **every per-file license header** inside all
   vendored sources or every AndroidX/Compose notice variant. Do not call the
   25-file draft a mathematically exhaustive legal bundle. The additional
   HarfBuzz ISC notice demonstrates the limitation of relying on root COPYING.
5. Resolve libbacktrace and legacy Unicode precise runtime-data applicability if
   needed; neither is presently a confirmed missing-app-notice defect.
6. Publisher copyright/content ownership, custom/commercial terms, Store privacy
   declarations, signing identity, and legal approval remain owner decisions.
   Public license discovery, exact text collection, dependency classification,
   packaging inspection, and truthful declaration drafts are **not** external
   work and have been materially advanced here.

## Evidence and hygiene

* Original manifest SHA-256:
  `1a63fda721a571ee2db378b6a00372d0ec51b447b502ddeae0a3f6ccd4db2101`
* Additive manifest SHA-256:
  `147f3b9229dff8f06361f65f9df5fbb8bf2f6383cebed1fb4b59018ba496ebc0`
* `native-notice-review-methodology-01.json` records 148 retained references,
  actual review dispositions/ranges, graph comparison, hashes, and exclusions.
  “Fetched reference” and “structured inspection” do not mean line-by-line review.

No production/configuration edits, Git writes, Gradle/Xcode builds, private
credential access, or persistent workers were performed by this reviewer.
HTTP/ZIP/ar work ran synchronously; archive buffers ended with their Python
processes. Only compact required source/notice/metadata evidence remains. No
global cache, user material, other agent output, or unrelated process was touched.
Parent retains sole ownership of the shared build lane and its cleanup.
