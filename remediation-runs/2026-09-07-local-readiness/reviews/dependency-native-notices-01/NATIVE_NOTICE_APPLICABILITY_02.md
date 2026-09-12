# Native notice applicability addendum

Reviewer: `/root/native_fix_review`, 2026-09-07. Exact Kotlin source tag
`2.4.10` / commit `5687445832cd835b4509b9fbc264cdf1a8201093`.

This addendum supersedes **only the two unresolved input-applicability entries**
in `NATIVE_NOTICE_APPLICABILITY_01.md`. It does not rewrite the original evidence,
claim every object survives final linking, or grant publisher legal approval.

## libbacktrace: confirmed default iOS runtime input

The exact compiler `CompilerOutput.kt:100–203` collects runtime modules.
Lines 164–170 add source-information modules based on **target support**, not
the selected `sourceInfoType`: `SOURCE_INFO_LIBBACKTRACE` and `LIBBACKTRACE`
are both added whenever `supportsLibBacktrace()` is true. Exact
`native/utils/.../KonanTargetExtenstions.kt:31–34` returns true for all Apple
targets. Thus this applies to Parlor's arm64, simulator-arm64, and x64 targets.

`CompilerOutput.kt:65–69,196–203` handles whether the current LLVM module owns
stdlib/runtime, including cached stdlib ownership. `206–225,240–251` links the
collected modules through runtime processing into the application module.
`RuntimeModulesConfig.kt:18–29` resolves their distribution paths. The previously
retained distribution inventory contains the corresponding exact bitcode files.

Counter-evidence considered: `NativeSecondStageCompilationConfig.kt:229–232`
defaults source-information dispatch to NOOP, or CoreSymbolication for supported
Debug targets. `IrToBitcode.kt:2712–2723` selects that dispatch pointer. This does
**not** prevent the earlier collection of libbacktrace inputs. Later optimization
or final linking may remove unreachable code; that remains artifact-specific.

**Action:** keep `Kotlin-libbacktrace-LICENSE.txt` as an exact default-runtime-input
notice, not an unsupported speculative dependency. Describe its input basis;
do not claim all of its code executes in normal app operation.

Source receipts: `fetch-receipts-26.json`, `fetch-receipts-28.json`; source files
`followup__backend__CompilerOutput.kt.txt` (all 285 lines read) and
`followup__native__utils__src__org__jetbrains__kotlin__konan__target__KonanTargetExtenstions.kt.txt`
(all 103 lines read). The installed compiler JAR's relevant class names and
constant strings independently directed the source lookup; no compiler was run.

## Legacy Unicode: confirmed native regular-expression attribution

Four files in the **installed 2.4.10 native stdlib source ZIP** contain the exact
1991–2005 Unicode permission notice plus the 1995–1999 database terms:

* `nativeWasmMain/kotlin/text/regex/sets/CompositeRangeSet.kt`
* `nativeWasmMain/kotlin/text/regex/sets/SupplementaryRangeSet.kt`
* `nativeWasmMain/kotlin/text/regex/sets/SurrogateCharSets.kt`
* `nativeWasmMain/kotlin/text/regex/sets/SurrogateRangeSet.kt`

All four byte-match exact-commit upstream files under
`libraries/stdlib/native-wasm/src/kotlin/text/regex/sets/`. Their lines 23–86
contain the attribution, alongside JetBrains/Apache Harmony headers. This is
licensed **software**, not a claim that all current Unicode data is from 2005.

`Pattern.kt:783–808` constructs these surrogate/supplementary implementations
when the compiled character class/pattern requires them. Parlor's content
validators use the native `Regex` implementation. No new app test or assertion
is offered that an existing ASCII-only authored pattern actually instantiates
every surrogate variant. Their source/input applicability is established;
individual final-link survival is not assumed from source ZIP presence alone.

**Action:** retain `Kotlin-Unicode-LICENSE.txt` with an accurate label such as
“Unicode portions of Kotlin/Native's Apache-Harmony-derived regex implementation.”
For the source-modification notice, state that these are upstream adaptations in
Kotlin/Native, rather than claiming Parlor authored or modified Unicode data.

The extracted source receipt is
`kotlin-native-legacy-unicode-extracted-source-receipt-01.json`; exact upstream
responses are in `fetch-receipts-27.json` and `fetch-receipts-29.json`.

## Modern generated character data is separate

`GenerateUnicodeData.kt:24–29,67–78,124–133` identifies **Unicode 13.0.0** inputs
for native-wasm character tables. Installed `_CharCategories.kt` and
`_WhitespaceChars.kt` byte-match the exact tagged upstream generated files.
The generator was read completely (202 lines); the whitespace implementation
was read completely, while the large category table was header/identity checked,
not exhaustively re-derived from all codepoints.

Unicode's official 13.0.0 `ucd/ReadMe.txt` confirms the version and links its terms
of use. The access-dated Unicode License v3 grant already retained applies to
Public data under the current official terms, subject to specific exceptions.
Keep that modern-data attribution distinct from the preserved legacy regex
notice and from HarfBuzz's separate generated Unicode 16.0.0 data.

## Objective package checks

1. Verify all copied notice/index files against a reviewed manifest of byte sizes
   and SHA-256 digests; duplicate names, missing files, path aliases, symlinks,
   unexpected files, and unbounded reads must fail closed.
2. Verify the same complete resource set in the actual unsigned Android AAB and
   the complete built and installed iOS `.app`, under the Compose app-resource
   namespace. Do not treat a Mach-O-only inventory as a resource inventory.
3. A match proves that these notice materials are packaged with those exact bytes.
   It does not prove UI readability, Store signing, legal sufficiency, or the
   final survival of every upstream object. A separate linker map is necessary
   if an omission is justified by dead stripping. Missing stripped symbol names
   alone are insufficient exclusion evidence.
4. Keep component/platform labels precise and retain optional PNG/zlib/Boost
   classifications. Do not turn the input supplement into “all dependencies are
   Apache” or “all legal work is complete.”

Research added only public source/notice evidence. No Gradle/Xcode work, app
execution, dependency-cache deletion, or background worker was started. Ordinary
synchronous metadata/source reads completed; no task-owned build outputs exist.
