# Native recovery and public UX acceptance — November 6, 2026

## Remaining CI gaps, not new physical-device evidence

The twelve-Issue audit identified two test/evidence gaps in #253 and #259.
Existing coordinator and rendered confirmation tests already cover cancellation,
busy state, failures and stale/repeated actions. Repeating those in another fake
store would not demonstrate the native persistence boundary. Separately,
`HomeCatalogVisualEvidenceTest` rendered sixteen public catalog/setup PNGs, but
wrote them to `build/reports/ux`; every Desktop artifact collected
`**/build/reports/tests/`. Cleanup therefore removed the unuploaded images.

## Android: exercise the actual persistence adapters

`AndroidResumeStorageRuntimeTest` adds seven methods to the existing API34 Debug
instrumentation lane, without dependencies or changes to the optimized API35
Release lane. It uses actual `AndroidSnapshotFileSystem`,
`AndroidSecureKeyValueBacking`, Android Keystore, SharedPreferences and shared
store adapters. No filesystem/crypto/preferences mock is substituted.

The methods verify:

- Selected local deletion for Last Light/Mafia/Whodunit envelope IDs, including
  legacy copies, repeated deletion, list refresh and unrelated saves/credentials.
- Cold adapter reconstruction, one-way legacy encryption before inventory
  returns, and authenticated readback of surviving records.
- Cross-session ciphertext swaps and tag tampering fail authentication while
  preserving evidence and healthy sibling records.
- Credential replacement uses fresh ciphertext; selected/current/temporary
  credential removal leaves other credentials and local snapshots untouched.
- Real nonempty-directory deletion failure remains an opaque storage error.
- English/Arabic/System language, dark appearance and reduced-motion settings
  survive adapter reconstruction without changing snapshots.

The fixture exclusively allocates a temporary no-backup namespace and unique
preference names. Its `applicationContext` keeps the isolated paths. Cleanup
deletes only those records; it never clears application data or shared Keystore
aliases. All payloads are synthetic. Envelope tests do not claim legal gameplay,
actual OS process termination, screen-reader operation or physical key protection.

Eight `AndroidResumeUiRuntimeTest` methods use the shipping `MainActivity`,
unchanged Koin graph and actual native accessibility actions in English/Arabic.
Six cover cancellation, selected deletion and Activity relaunch with legal
Last Light, Mafia and Whodunit fixtures; two require actual native deletion-failure
feedback and its polite live-region property. They verify the surviving game,
independent credential and language. [Fixture and fault details](2026-11-06-ORIGINAL-GAME-NATIVE-RECOVERY.md)
distinguish reducer-created state from the storage-only envelopes above.
The native tree is bounded and never logged; gameplay fixture names must not
appear on Home. Each action is sent once, without input retries or fake UI routes.
Only exclusively named fixture records are removed; an existing nonempty
inventory fails the precondition instead of being cleared. These are actual
app/Compose/storage **Home deletion** journeys, not OS process death, complete
in-game resume, physical taps or TalkBack execution. The API34 lane expects
twenty-three methods: navigation3, storage7, Home8 and five native
label/action-owner controls. Release remains three.

```bash
./gradlew productionAndroidNavigationRuntimeCheck \
  -Pparlor.androidRuntimeTestVariant=debug \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader \
  --dependency-verification=strict
```

The ordinary complete workflow still runs this **and** Release smoke, all unit
and integration gates, native Apple tests, supported architectures and optimized
Release builds. Passing a focused command alone does not qualify a PR.

## Public screenshots: retain what the test actually renders

The default is now `build/reports/tests/public-ux`, outside Gradle's `desktopTest`
HTML subtree but inside the existing upload selection. Local property/environment
overrides remain. No additional/private artifact glob is introduced.

The same gap affected fourteen existing renderer/helper files across settings,
room flows, shared artwork and all six games (`build/reports/ux` or
`build/ui-snapshots`). Their destinations are corrected together. The separate
room-code layout test now also captures its synthetic `WWWWWW` fixture by default.
These tests use fabricated players, public/covered views or a synthetic own-player
view, not actual saved sessions or a live room. “Public” here means artifact-safe
fixtures, not a change to a game's private-information policy.

Portable controls evaluate each Desktop producer's actual upload globs against
the default destination and reject the old uncollected renderer paths. Full-run
acceptance must additionally find/decode the sixteen Home/setup PNGs and existing
game/shell/design renders in the source-bound uploaded ZIP, then inspect languages,
themes and large-text layouts. Structural selection is not a screenshot or native
UI pass; preserving all existing rendering assertions remains mandatory.

## Verified preview and a concrete Phase5 correction

Frozen preview source `f47447e7` passed free Ubuntu
[42146142306](https://github.com/Apdelrahman1911/parlor/actions/runs/42146142306):
2,022 Desktop methods (three established LAN skips) and the seven native storage
method signatures compiled. It uploaded **253 synthetic PNGs**, independently
hash-bound to the executed source and successful owned cleanup. Artifact
13471310005 SHA256:
`69ecdde5d351489259bb5d99c4f0ccd1e1b64377482d1fe3a30364d73e81a5ee`.
Preview admission receipt:
`f6f52fefaab482bec4ec0d4a9613a523bacbfd3b86e979869ba6b44d608c4d93`.
Neither the later native Home tests nor subsequent visual changes are
qualified by that earlier source. The preceding preview was cancelled for a
known cleanup-CLI argument defect; it is not a successful run.

Actual EN/AR light/dark catalog readback exposed a gap the old assertion missed:
at 200% text, a fragment of the first clickable card was visible, but its device
requirements and written Open action were below the viewport. The new shipping
layout drops decorative introductory copy at the existing large-text breakpoint,
retains meaningful section-heading semantics and stable restoration keys, and
puts full-width player/device labels and Open ahead of the complete description.
Titles use the full card width rather than competing with artwork; six registry
motifs and normal-text layout remain. No text scale, contrast token, privacy rule
or action gate is weakened. Shorter bilingual filter labels and reduced-motion
guidance remove redundant wording and long settings-control explanations.

The existing rendering test now additionally requires **unclipped, complete text
layouts** for the first game's counts, device requirements and Open at 320dp and
390dp, EN/AR and both themes, without scrolling. Four narrow-phone PNGs supplement
the original sixteen Home/setup images. This is an incremental implemented
usability improvement, not a claim of complete redesign or physical acceptance.

Actual retained Last Light large-text frames also showed wrapped player labels
crowded against their rounded nameplate edges. The prepared refinement adds
12dp horizontal/6dp vertical insets only at the existing large-text breakpoint;
ordinary density, text scale, game rules and recipient projection are unchanged.
Existing EN/AR tests now require those insets as well as full unellipsized text,
hand counts and private-view isolation. This is a spacing correction, not a
claim that a native font or glyph-rendering cause was established. Its new
rendered output still requires fresh verification and visual inspection.

## Qualification status and remaining acceptance

At preparation, eleven artifact-selection controls pass locally. Native compilation,
API34 execution, retained-PNG inspection, full protected PR qualification, normal
merge and distinct main verification are **pending**, tracked on #253/#259.

Draft PR328 revision01 stopped at a native-test compilation error: Koin's
`getKoin()` receiver requires `Application`/`ComponentCallbacks`, not the generic
`Context` type. Revision02 corrected that receiver but stopped earlier at stale
mechanical review metadata; its inventory had been generated before the source
commit it must reference. Neither run qualifies native acceptance. Revision03
consolidates the prepared eight Home methods and regenerates the inventory only
after source/document commits, then checks it again on the final committed HEAD.
Revision03 passed that inventory boundary but failed at `:composeApp:detekt`:
four uses of the six-player roster size in `AndroidLegalResumeFixtures.kt` were
magic-number findings. API34/API35 runtime steps had not executed; this was not
a native gameplay failure. The original ZIP, four-error Detekt XML, failed log
and successful owned cleanup are retained, with failure receipt SHA256
`6b2b9a27b7a84616c639d850837abb4355a7bb9beeb67c44e2aa2d651435c90e`.
Obsolete remaining work was cancelled rather than retried unchanged. Revision04
replaces the four expressions with `ORIGINAL_GAME_PLAYERS`; focused JDK21/wrapper
`:composeApp:detekt :game-modes:last-light:detekt` passes with strict verification
and the unchanged policy. Source/docs are again committed before regenerating
the inventory and checking the final commit. No inventory gate, lint rule,
runtime assertion, timeout or action is weakened.

Revision04 [42159600926](https://github.com/Apdelrahman1911/parlor/actions/runs/42159600926)
passed Release3 and API34 navigation3/storage7, but all eight Home journeys
failed **before their first Remove action**. No deletion or dialog action was
sent. Source-bound failure receipt SHA256:
`9bce376845601e5f0420d850ee34d5e32a23b135f89d6af6b798dbdd3af88517`.
Both owned process cleanups passed; the failed API34 AVD was correctly retained.
This is neither the old GPU failure nor evidence of a storage deletion defect.

Review found a concrete adapter/Compose contract mismatch. The uploaded AAB's
`META-INF/androidx.compose.ui_ui.version` identifies AndroidX UI **1.10.5**.
Its published `SemanticsNode.emitFakeNodes` puts a merged button's description
on a non-clickable child. `AndroidComposeViewAccessibilityDelegateCompat`
omits that description from the clickable parent (lines704–714), while mapping
`OnClick` only on the owner (lines790–805). The old test required an exact
description **and** clickability on the same node. No original native tree was
captured, so source evidence does not establish every transient window state.

Revision05 resolves an exact, visible, enabled label to its nearest native
click-action owner, then still requires one unique action and sends it once.
It cannot select a sibling or climb through a disabled, hidden or foreign
action. Five native node controls cover these boundaries. Fixed public-only
logging records actual compound-node resolution; failure evidence contains
only counts, never arbitrary labels or trees. There is no production UI change,
retry, scrolling workaround, longer timeout or relaxed outcome assertion.
Local strict-wrapper Detekt passes. A focused hosted native run must verify
the correction before the next complete PR qualification; merge and distinct
main verification remain pending.

The corrected preview [42165263589](https://github.com/Apdelrahman1911/parlor/actions/runs/42165263589),
at exact candidate `69b6ad17`, is terminal **FAILURE: 19 PASS / 4 FAIL**, with no
errors or skips. All five owner controls, navigation3, storage7 and all four
complete English Home journeys pass. Every Home method records actual compound
label/action resolution. All four Arabic methods also pass Cancel, selected
deletion, the refreshed sibling control and selected-absent/sibling-intact
storage assertions. Their failure is **only after Activity finish/relaunch**,
at line127, not the earlier post-deletion assertion at line121. A visible native
root has no exact expected Arabic label. This does not yet establish which
language it displays or whether application locale ownership or native
observation is responsible.

Both uploaded ZIPs, their SHA256/size/source bindings, actual23-method XML,
per-method fixed markers and successful owned cleanup were independently
inspected. Failure-intake SHA256:
`3063869279b6d76d52c13b0ca5af63edf4b949ef0ca8f8bc9ce3c4f618dfe541`.
The complete build log is the uploaded `_temp/parlor-android-recovery-preview.log`;
the GitHub job-log API copy is truncated and cannot replace it.
Single-method [42167369595](https://github.com/Apdelrahman1911/parlor/actions/runs/42167369595)
observes that new relaunch boundary on failure only: fixed EN/AR label presence,
storage/preference booleans and public locale categories. It retains the original
assertion/action bounds and changes no production application code. Its result
cannot qualify all23 methods or replace full PR/main acceptance.

That isolated run terminated before reaching the relaunch probe: its sole
method failed in **0.012s** resolving `SnapshotStore` from Koin. Source/artifact
and owned-cleanup readback passed; failure-intake SHA256:
`57a73d98494611641526007e0c18e836347db34e9a9eed8f9841863a56346c65`.
The legacy framework runner starts its test thread in `onCreate`, before
`ActivityThread` calls `Application.onCreate`. It does not join that main-thread
startup turn before running a selected method. Koin publishes its context before
the synchronous application module registration finishes; earlier tests had
incidentally supplied startup time. This is a native-test precondition defect,
not missing production storage registration or new locale evidence.
The fixture now calls `instrumentation.waitForIdleSync()` in `setUp` before any
application graph resolution. It neither sleeps nor retries Koin, starts a
replacement graph, warms up the UI or changes a gameplay assertion deadline.
The corrected isolated method subsequently reached its intended boundary in
[42169900414](https://github.com/Apdelrahman1911/parlor/actions/runs/42169900414),
at source `3318a36e`, and failed after relaunch. Independent source, archive,
exact XML and owned-cleanup readback receipt SHA256:
`017a5418569e814e8fa82c02ec39bb010aae2c96ac74e3560da957e866cc98cc`.

## Android locale lifetime correction — #329

The bounded failure evidence establishes an actual application language reset:
the English sibling control exists, the Arabic control does not, and the
persisted preference still matches Arabic. Selected deletion and surviving
storage are correct. JVM, Android default-list and Compose locales are English.
Immediately before relaunch the default was Arabic and the old Activity had
not yet been destroyed; at failure the old Activity is destroyed and the new
one is alive and focused. This is not merely a native query missing an Arabic
label. The failure does not trace every framework locale setter.

`LocalAppLocale.android.kt` used language equality to decide whether disposal
could restore previous process defaults. Two overlapping Activity compositions
can request the same language: the older one's disposal then overwrites a newer
owner. [#329](https://github.com/Apdelrahman1911/parlor/issues/329) tracks this
separate concrete ownership defect. The prepared correction uses an exact
per-effect acquisition and rewires a removed predecessor's restoration target
only when its successor captured that predecessor's value. Older disposal
cannot overwrite a live successor; independent platform changes are preserved.
System mode also acquires the complete configured locale list. Capture uses
`LocaleList.getAdjustedDefault()`: the selected default first, with every
remaining preferred locale retained. The public one-argument
`LocaleList.setDefault(list)` restores that effective priority and JVM default.
No shared Resources object is mutated, no navigation content is re-keyed, and
iOS/Desktop providers remain unchanged.

Seven common ownership tests pass with JDK21, the wrapper and strict dependency
verification: same-Arabic overlap, reverse/middle disposal, later/captured
external changes, System supersession and duplicate release. Focused design
system/application Detekt also passes. These deterministic checks expose the
ownership contract; **the correction still needs actual API34 execution and
full protected PR/main qualification**. Other independent framework-reset risks
are not ruled out by source reasoning. The existing23 native methods retain
their exact actions, deadlines, cancellation/deletion and surviving-state
assertions; no retry or weaker language lookup is added.

The next preview's inventory passed, but its temporary harness had copied a
pre-correction62-character checksum literal and stopped before native execution
([42172369603](https://github.com/Apdelrahman1911/parlor/actions/runs/42172369603)).
The actual instructions remained unchanged; the exact previously verified
64-character digest was restored. Subsequent
[42172856383](https://github.com/Apdelrahman1911/parlor/actions/runs/42172856383)
then found an Android compilation error, not a failing native journey: the
two-argument locale setter is `@hide` and absent from the public API36 SDK.
The adapter now uses only the public adjusted-list getter and one-argument
setter above. This preserves the effective selected locale/all preferences,
not Android's inaccessible internal nonzero-index representation. Both original
failures remain failures; neither executed or qualified the corrected app.
The public-SDK module compilation and both focused Detekt tasks pass locally
with JDK21/strict verification. A broader local test-APK preflight was killed
by the host's kernel OOM killer (PID31177, November6 06:24:44 UTC), after the
changed module compiled; it is not a successful application/test-APK build.
Hosted native compilation and all23 methods remain required.

## Corrected native acceptance — full qualification next

[42174962013](https://github.com/Apdelrahman1911/parlor/actions/runs/42174962013)
is terminal **SUCCESS** on the standard free Ubuntu runner. It compiled exact
application source `d168a8cb928a5fda6a66f9c254ff75b3cfcee4e4`, tree
`d779feae18bfdeca2fb20ce7614cf0ef52820a87`, then passed all23 actual API34
methods: navigation3, native action-owner5, storage7 and Home8, without
failures/errors/skips. All English and Arabic cancel/discard/relaunch/error
journeys pass at the unchanged action and assertion bounds.

Independent readback checked original native XML and identities against source,
both uploaded archive sizes/SHA256, focused Detekt, build log, source ownership,
successful descendant/AVD retirement and upload-bound output cleanup. Receipt
SHA256: `503d40a6851009ea8f5ded09aad61f89206628b10257d39dfe634aa4b52b7940`.
The actual fixture assertions confirm surviving storage and requested labels;
no earlier failure is reclassified by this newer result.

This is a **scoped native preview**, not complete PR/main qualification,
OS process-death acceptance, authenticated multiplayer recovery, physical taps
or screen-reader operation. PR328 must still pass fresh complete checks,
protected merge and distinct main verification before #329 closes. #253/#259
retain their broader acceptance requirements; original iOS input and strict
protection prerequisites remain separate.

Eight actual large-text Home renders from revision01 (EN/AR, 320/390dp, both
themes) were inspected: complete requirements/Open remain inside the viewport.
That scoped visual review can apply only while rendering inputs remain identical;
the final source still needs its own successful tests and uploaded-image readback.

Existing iOS native discard/path/error/backup-order controls are retained rather
than duplicated. Positive protected iOS persistence is still gated by #258's
unsupported observed hosted volume; actual locked-access/keybag behavior belongs
to the owner's Phase4. The held protection candidate is not imported here.
Activity recreation and public Home confirmation are not process-relaunch acceptance.
Do not close either broad Issue from these scoped additions alone.
