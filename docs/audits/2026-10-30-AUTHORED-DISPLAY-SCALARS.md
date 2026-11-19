# Authored-text scalar safety — October 30, 2026

## Confirmed finding and scope

Extend existing [#293](https://github.com/Apdelrahman1911/parlor/issues/293), not
a duplicate issue. `AuthoredTextPolicy` retains the same UTF-16-unit defect after
the admission/game-label correction: it checks well-formed surrogate pairs, then
asks each `Char` for its category. Supplementary Unicode FORMAT scalars have two
SURROGATE units, neither individually FORMAT.

At parent `b758f25d`, actual original Desktop policy bytecode accepts all127
Unicode17 supplementary Cf scalars. Real `CaseSummaryValidator.validateShape`
also accepts381 synthetic summaries with these scalars independently appended
to title, subtitle and theme. The normative UnicodeData17.0.0 oracle SHA256 is
`2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c`.

**P2 consistency/defense-in-depth defect.** Production content remains bundled
and offline. This does not demonstrate unsafe bundled files, active remote
content admission, private-player disclosure or physical rendering effects.
Payload examples use synthetic public introduction/clue-tag data, not player
secrets. Native Back/keyboard causality remains unrelated (#267/#276).

## Reproduction and implemented correction

Append LANGUAGE TAG U+E0001 (`\uDB40\uDC01`) to an otherwise valid case title,
subtitle, theme, Whodunit public introduction or clue tag. Original validation
accepts the otherwise canonical summary/encoded payload; the promised FORMAT
rejection should reject it.

`AuthoredTextPolicy.isSafeForDisplay` now delegates to
`DisplayTextScalars.containsOnlySafeScalars` in its existing core dependency.
Its public API is unchanged; the copied surrogate scan/category loop is removed.
Do **not** substitute `DisplayNamePolicy`: authored text retains its separate
field limits and accepts empty/space-only values at scalar-policy level, while
required-field validators still reject blanks. There is no name-length cap,
trimming, normalization, silent repair or identity rewrite here.

BMP controls/FORMAT and malformed surrogate rejection remain unchanged. Arabic,
supplementary letters, emoji and variation marks remain supported. Bundled data,
protocol/schema, dependencies, UI, gameplay, signing and CI settings are unchanged.

## Actual verification

- Original production policy plus nine new common regressions: **nine methods,
  six exact assertion failures, zero errors/skips**. Policy, each of three
  summary fields, genuine JSON-encoded Whodunit public introduction and clue
  tags fail independently. Three compatibility controls pass.
- Correction: **all30 content /382 Whodunit Desktop methods pass**, zero
  failures/errors/skips, including every prior method and all nine additions.
  Both modules' complete-source Detekt passes. Existing bundled-loading,
  playability, multiplayer and Compose suites are retained, not replaced by
  focused tests. Gradle reports2m53s with caches, not a cold/mobile benchmark.
- Every new rejection method exercises the frozen127-Cf set. Controls preserve
  valid international text exceeding32 units, empty scalar-policy values and
  malformed/BMP rejection. Tests never download Unicode data at runtime.

Corrected affected log SHA256:
`330b8718ee239f6e64939070b915c93d72a7182c85d30e6911f519a39242adf1`.
Original results, exact source/XML hashes and failed preparation receipts remain
in untracked custody. The standalone summary harness initially missed the
existing serialization runtime; correcting only its classpath produces381
accepted summaries. The first repository readback assumed the wrong assertion
subclass; preserved XML shows `org.opentest4j.AssertionFailedError`. Corrected
readback accepts the same original outputs without replaying baseline tests.

## Integration and remaining gates

The affected result is preparation evidence, not clean final-source/root or
Apple runtime qualification. Require full strict final-source release aggregates
and fresh required native/architecture/optimized Release CI before integration.
Keep this follow-up separate from the already-running parent PR294 source;
never update that branch merely to replay its original failing UI journey.
Do not merge stacked work to bypass checks. #293 stays open until the covered
boundaries integrate and qualify; physical rendering/accessibility, data
protection, background/energy and Store acceptance remain separate gates.
