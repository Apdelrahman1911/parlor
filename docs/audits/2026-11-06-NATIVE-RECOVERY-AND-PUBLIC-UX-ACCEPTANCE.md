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

A portable control evaluates each Desktop producer's actual upload globs against
the default destination. Full-run acceptance must additionally find/decode all
sixteen PNGs in the source-bound uploaded ZIP and review both languages/themes at
1x/2x text; the structural selection test is not a screenshot or native UI pass.

## Qualification status and remaining acceptance

At preparation, ten artifact-selection controls pass locally. Native compilation,
API34 execution, retained-PNG inspection, full protected PR qualification, normal
merge and distinct main verification are **pending**, tracked on #253/#259.

Existing iOS native discard/path/error/backup-order controls are retained rather
than duplicated. Positive protected iOS persistence is still gated by #258's
unsupported observed hosted volume; actual locked-access/keybag behavior belongs
to the owner's Phase4. The held protection candidate is not imported here.
Public Home-to-confirmation tests are not actual app process-relaunch acceptance.
Do not close either broad Issue from these scoped additions alone.
