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
eighteen methods: navigation3, storage7 and Home8. Release remains three.

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
No inventory gate, runtime assertion, timeout or action is weakened.

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
