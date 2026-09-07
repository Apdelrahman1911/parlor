# Third-party notice supplement

## Scope and shipped materials

`composeApp/src/commonMain/composeResources/files/legal/` contains 25 verbatim
upstream notice/license texts and a Parlor-authored `INDEX.txt`. Common Compose
resources package the complete set on Android and iOS (also Desktop development
builds). The index identifies each component and the platform of the **inspected
dependency input**; it does not imply that every component ships on every platform.
No new Settings screen or navigation flow is introduced.

The supplement covers inspected Skiko 0.9.37.4 / Skia native inputs, Kotlin/Native
2.4.10 runtime/stdlib inputs, modern and legacy Unicode terms, and exact Android
Bouncy Castle 1.85 and SLF4J 2.0.16 input-license copies. The Apache text is shared
where appropriate, not applied to unrelated licenses. In particular:

- DNG SDK has custom Adobe terms; its full notice and patent grant are retained.
- The IJG and DNG acknowledgement sentences appear explicitly in the index.
- ICU's combined file includes build-script terms, not an application GPL claim.
- PNG/zlib acknowledgements are optional; Boost has an executable-object exception.
- Kotlin/Native's Apple runtime collects libbacktrace independently of the selected
  source-information dispatcher; this establishes an input, not final-link survival.
- Legacy Unicode regex notices concern upstream Kotlin/Native adaptations. They
  are separate from Unicode 13.0.0 character tables and HarfBuzz Unicode 16.0.0 data.
  The Unicode v3 text is the access-dated current grant, not a fabricated historical
  license. Parlor is not represented as having modified Unicode data.
- An earlier AAB already contained Bouncy Castle and some AndroidX notices. This
  supplement does not claim that Android previously shipped no license material.

`config/third-party-notices.json` records exact resource sizes, SHA-256 digests,
public source URLs, access dates, source ranges/archive members where applicable,
upstream pins and applicability. It contains no dependency-cache or signing paths.

## Source and package verification

Use Python 3.9+ on the supported macOS/Linux verification hosts:

```bash
/usr/bin/python3 -B scripts/verification/third_party_notices.py --root . --json
/usr/bin/python3 -B scripts/verification/third_party_notices.py \
  --root . --package composeApp/build/outputs/bundle/release/composeApp-release.aab --json
/usr/bin/python3 -B scripts/verification/third_party_notices.py \
  --root . --package /absolute/canonical/path/Parlor.app --json
/usr/bin/python3 -B -m unittest discover -s scripts/release/tests \
  -p 'test_third_party_notices.py' -v
```

Use the actual artifact path from the build receipt. The verifier does not build,
resolve dependencies, use the network, extract archives, write files, or inspect
signing contents. It works in a source-identical isolated build copy without Git.
Input paths and ancestors must not be symlinks; use canonical paths on macOS
(for example `/private/tmp/…`, not the `/tmp` alias).

Source checks reject missing/extra/aliased resources, nonregular files, symlinks,
invalid closed-schema metadata, hash/size changes and catalog pin drift. All
`[versions]` pins and the full catalog are bound. **Only the catalog hash** converts
CRLF to LF for Git checkout portability; the receipt also records its raw digest.
Notice bytes never normalize. `.gitattributes` marks the notice directory `-text`
so Git does not rewrite upstream line endings, including SLF4J's CRLF.
Only SLF4J and WebP-COPYING have path-specific whitespace parsing exceptions for
their verbatim CRLF/final blank lines. Ordinary trailing spaces remain errors in
these notices and in application source; fixture tests enforce that distinction.

Package checks require all 26 exact files, with no extra notice files, at:

- AAB: `base/assets/composeResources/com.parlor.app.resources/files/legal/`
- iOS `.app`: `compose-resources/composeResources/com.parlor.app.resources/files/legal/`

The namespaces correspond to the explicit Compose resource configuration and
observed platform package layout. A directory of similarly named files elsewhere
does not satisfy the check. Verify both the **built** and **installed** iOS app
when installation is part of the verification flow.

The bounded reader checks the ZIP central-directory count before allocation,
local/central consistency, duplicate and unsafe paths, CRC, complete bounded
DEFLATE output and direct byte equality. It rejects encrypted, multipart or ZIP64
inputs rather than silently skipping them. Current limits are 2 GiB per AAB,
20,000 package entries, 24 path components, 64 KiB per notice and 256 KiB total
notice content. These are verification ceilings, not Store size requirements.
The `.app` walk inspects metadata; only notice file contents are read.

`--json` emits one bounded object. Exit 0 with `status: PASS` means all **requested**
checks passed. Source-only success has `package: null`; it is not artifact proof.
A package receipt lists relative paths, byte lengths and hashes with the exact-byte
policy. The caller must separately bind the complete artifact and source/diff identity.
Failures return nonzero, `status: FAIL`, and a bounded non-secret diagnostic.

## Updating the supplement

1. Resolve the real release input graphs with strict dependency verification. Review
   changed platform/native inputs, not just Maven POM license declarations.
2. Inspect authoritative upstream sources at the actual revision and retain public,
   sanitized evidence. Do not infer final-link exclusion merely from stripped symbols.
3. Copy original notice bytes without rewriting terms or line endings. Update the
   index, manifest provenance, digests, pins and the verifier's reviewed filename set
   together. A hash update alone is not a license or applicability review.
4. Independently review applicability and the diff. Run the adversarial filesystem/
   ZIP regression suite, source verification and checks on actual platform packages.
5. Retain compact receipts bound to the exact source and artifact. After every build/
   test cycle stop Gradle, clean only owned generated outputs after final inspection,
   stop again if cleaning started Gradle, and verify no owned workers remain.

## What remains separate

Passing checks prove delivery of this reviewed resource set only. They are not an
exhaustive upstream per-file copyright scan, a final-binary SBOM, proof that each
native object survives linking, end-user UI readability, publisher legal acceptance,
content/trademark rights, signing, physical-device evidence, or Store approval.
The publisher must review custom/commercial terms and any remaining obligations;
see [dependency inventory](DEPENDENCY_INVENTORY.md) and
[dependency review](DEPENDENCY_REVIEW.md). Existing publication disablement and
application-identity safeguards are unchanged.
