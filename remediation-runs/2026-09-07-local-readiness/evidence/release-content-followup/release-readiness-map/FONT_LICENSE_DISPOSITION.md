# Bundled-font notice-delivery investigation

Reviewer: `/root/release_fix_review`; 2026-09-07. Source observed at
`1f809b87c15a4deb079809bc14718a58cf0fe451`, tree
`6f6912d8612f71e07d2c338d860e906333ce8aad`. This is a bounded follow-up,
not an application-wide legal approval or a seventeenth confirmed defect.

## Conclusion for independent root review

**Do not report missing standalone/full OFL text as a confirmed defect.**
Official OFL FAQ 1.1-update7 §1.10 explicitly permits link-only font metadata
for a font bundled within a program, while strongly recommending full text.
That counter-evidence defeats the suspected requirement to add a new notice
file solely because no such filename exists. No production change is proposed
as a necessary repair. The root reviewer should independently check the
retained primary references before adopting this classification.

An optional full third-party-notices resource can improve offline access, but
is not demonstrated to be mandatory by this font finding. General dependency,
content-rights, product-license and trademark approval remain separate owner
requirements; this investigation does not approve them.

## What was inspected

- `inter.ttf`: 876,576 bytes; SHA-256
  `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`.
- `jetbrains_mono.ttf`: 187,208 bytes; SHA-256
  `48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda`.
- All 67 Inter and 50 JetBrains SFNT name records, table directories, copyright,
  trademark, author, version and license records. Both retain copyright and OFL
  declarations/links; neither contains the full OFL or a `meta` table.
- Actual app-to-theme-to-typography resource calls and Compose library wiring.
  The fonts are production assets, not merely design prototypes.
- The task-owned `ios-readiness-01` **Debug simulator app bundle** contained
  exactly these two fonts, byte-identical to source. This preserves the metadata
  in that artifact. See `font-ios-bundle-inspection-01.json`.

## Primary-source checks and counter-evidence

`font-official-references-01.json` through `-06.json` record URLs, access times,
hashes and unsuccessful requests. All retained exact license texts were read.

- Inter's metadata identifies `4.001;git-66647c0bb`; the author's license at
  that revision contains the matching copyright and OFL 1.1.
- Guessing a JetBrains `v2.211` tag returned 404. It was not treated as proof
  that the font was unlicensed. Google Fonts revision
  `6e4b84c976cadb3c49a40fd9a1c203e4f7fcf2da` supplies a **byte-identical** font
  plus the matching OFL/copyright file. Its upstream metadata points to a
  different-sized upstream font; no upstream byte-identity is claimed.
- JetBrains' legacy `https://scripts.sil.org/OFL` link successfully redirected
  to `https://openfontlicense.org/` on the access date.
- OFL condition 2 allows machine-readable metadata; official FAQ §1.10
  specifically clarifies that a link suffices in this situation. §§1.3–1.4
  explicitly allow commercial/mobile software bundling. Readable exact
  excerpts are in `font-ofl-faq-applicable-excerpts-02.json`.

## Limits and cleanup

Android artifact inspection is pending. No Store-signed IPA, final release
artifact set, glyph rendering, accessibility or visual output was verified by
this investigation. No license was stripped or font modified. Only compact
reports/public license references were retained; downloaded comparison-font
bytes were discarded from memory. No Gradle, Xcode, simulator or other worker
was started or stopped by this reviewer. Root owns cleanup of its app bundle.
