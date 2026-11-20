# Android saved-session deletion across process death

## Newer-main failure under investigation

The newer PR330 main `081a6f68` failed
[42208844307](https://github.com/Apdelrahman1911/parlor/actions/runs/42208844307)
at the twelfth case's **pre-delete preparation**, after11 complete cases.
See the [navigation-boundary correction](2026-11-06-ANDROID-RECOVERY-NAVIGATION-BOUNDARY.md)
for original evidence, confirmed fixture coalescing reproduction, its historical
causality limit and required fresh qualification. The accepted checkpoint below
does not certify that newer failed main. #253 remains open.

## Latest accepted result — integrated into verified main

The normal implementation is integrated through PR328 and protected merge
`6028009f`, without its auxiliary preview workflow. Fresh full
[PR42185129610](https://github.com/Apdelrahman1911/parlor/actions/runs/42185129610)
and distinct [main42193749434](https://github.com/Apdelrahman1911/parlor/actions/runs/42193749434)
pass all12 cases/24 original native phases and all other required producers/
optimized Apple Release join. Independent actual-main process receipt SHA256:
`4e6888c88487bfbc970b3a670dca472c1d675eae131eb92ed8b3b650df2925ab`.
Complete main receipt: `a481e50621ba1ab7370a372979175154b39fbab57228afbee5a789ba778a5bb8`.

Original logs attest distinct process nonces, explicit force-stop/PID absence,
selected deletion and preserved unrelated encrypted storage. This is synthetic
local/membership persistence acceptance, not authenticated multiplayer rejoin,
in-game restoration, physical-device enforcement or positive iOS protection.
Those #253/#258 gates remain open. Earlier failures and preparation checkpoints
below are preserved; they are not newer-source qualification.

## Acceptance boundary — #253

Activity recreation and reconstructing a storage adapter do not prove durable
deletion across an OS process boundary. The follow-on Android acceptance step
therefore uses two separately selected native methods, with an actual app
launch, explicit `am force-stop`, independent PID-absence observation and a
different in-process nonce between them. It is not a physical-device campaign,
authenticated room join, or a claim of complete in-game recovery.

The matrix is Last Light/Mafia/Whodunit × English/Arabic × local/multiplayer
Resume entry: twelve cases and twenty-four explicit JUnit3 invocations.
`AndroidResumeProcessPhases#prepare` and `#verify` intentionally do not have the
discovery-only `test*` prefix. Running them together in the ordinary navigation
suite would not exercise process death. The existing API34 navigation/storage/
Home methods and optimized API35 Release smoke remain mandatory.

## What the native journey checks

- Use the shipping MainActivity, Koin graph, encrypted snapshot/credential
  stores, Settings store and exact public native accessibility actions.
- Create legal local fixtures through real game definitions, reducers and
  codecs. Whodunit validates the shipped bundled case and content identity.
- Navigate to Settings before staging a synthetic persisted membership, then
  return using the actual public game-library action. This makes a real user
  navigation decision rather than bypassing automatic startup recovery.
- Cancel changes nothing; confirm removes only the selected entry. Preserve a
  legal local sibling and an unrelated credential throughout.
- After force-stop, a fresh instrumentation/application process must observe
  the deleted entry absent, the sibling byte-for-byte equivalent after decoding,
  the unrelated credential intact, and Arabic/English plus reduced-motion
  preferences unchanged. The actual Home row must be visible in that language.
- Retire only the UUID-owned fixture after verification succeeds. Failed
  assertions retain synthetic state; never use `pm clear`, reset the Keystore,
  or silently remove an existing inventory.

The multiplayer record is fabricated schema-valid storage input admitted by
the shipping parser and selected/deleted through `RoomTransport`. No fake host
is contacted. It does **not** establish authenticated admission, recovery of a
seat, secret rotation over the network, or Android↔iOS interoperability.

## Runner isolation and reproducibility

The normal full PR/main workflow reuses the already-built Debug app/test APKs
after both managed-device lanes. It allocates one fresh API34 emulator inside
the existing child-subreaper's private AVD namespace, uses `swiftshader`, and
owns its private ADB port5039/emulator port5662. The driver rejects local
environments, redirected/preexisting AVDs, occupied ports, mismatched APK IDs,
non-Debug targets, stale evidence and a checkout that differs from `GITHUB_SHA`.
It neither changes the required architectures nor introduces dependencies.

```bash
# Portable orchestration controls; not native acceptance:
python3 -B -m unittest scripts.release.tests.test_android_resume_process -v

# Only inside the reviewed disposable hosted-Linux full job, after APK build:
python3 -B scripts/android/supervise_managed_device.py \
  --receipt build/ci-evidence/android-process-device-supervision.json \
  --isolated-avd -- \
  python3 -B -m scripts.android.run_resume_process_acceptance \
    --output build/ci-evidence/android-process-acceptance
```

Each click, deletion, launch and force-stop executes once. Bounded read-only
observation is not an action retry. A known adjacent Home page may receive one
directional accessibility scroll when its exact target is offscreen. No
arbitrary accessibility tree or private stored content is exported.

The uploaded result binds source/tree/input hashes, app/test APK hashes, every
case's original phase log and exact JUnit outcome, process nonces, live PID and
post-stop absence. Exit0, a terminal instrumentation code, a mocked command or
same-process methods are insufficient. A failed case stops the matrix; owned
emulator/ADB retirement still runs, and the outer supervisor independently
checks descendants before retiring a successful AVD. Existing evidence upload
must succeed before checkout-output cleanup.

## Current status and remaining work

Prepared source passes33 portable controls and focused application Detekt with
JDK21, the wrapper and strict verification. These results do not compile or
execute the native phases. Fresh hosted native execution, complete protected
PR qualification, review/merge and distinct main verification remain required.
This work follows the separate #329 locale-ownership correction; no previously
failed locale or native-input run is retroactively declared successful.

The first hosted preview
[42177564699](https://github.com/Apdelrahman1911/parlor/actions/runs/42177564699)
failed test compilation before any process case ran. The test used the public
`InstrumentationTestRunner.getArguments()` API, but the existing test-only
compile classpath contained `android.test.base.jar`, not the separate SDK
`android.test.runner.jar`. API36 stub inspection confirms the runner and getter
are public; this is not a hidden API or missing production registration.
Both platform stubs are now `compileOnly` in `androidInstrumentedTest`; neither
enters a shipping/test APK or adds a Maven dependency. A new portable safeguard
requires that scope and binds the Gradle configuration into process receipts.
The corrected Gradle DSL evaluates successfully with JDK21 and strict verification;
a separate SDK compile probe fails with the base stub alone and succeeds with
both stubs. Neither probe is native test execution.
Original compilation failure and successful upload-bound cleanup remain retained;
corrected native execution and full qualification are still required.

The corrected compile preview
[42179735995](https://github.com/Apdelrahman1911/parlor/actions/runs/42179735995)
successfully builds both APKs and passes three complete Last Light process cases
(English local/multiplayer and Arabic local), with six original passing native
phases. The seventh phase, Arabic multiplayer preparation, errors after Remove:
`AndroidPublicRecoveryUi.revealDescription` line45 is waiting for an advertised
backward scroll, **not** its final sibling-label assertion. The original native
error, three successful process pairs and upload/descendant cleanup are retained.
The matrix remains failed; the fourth case's post-delete storage assertions did
not run.

The observer sampled the target once, then waited only for backward scrolling.
A top-of-list layout that settles without a backward action could therefore
never satisfy it, even after the target became available. The exact transient
reason for the original target's absence is not recorded; this is not evidence
of another locale reset or failed deletion. The correction reobserves **target
or available scroll** at each existing bounded read, prioritizes the target,
and still performs at most one scroll. All exact labels, store checks, process
boundaries and deadlines remain. Four native decision regressions supplement
the mandatory ordinary API34 suite (now27 methods); the12-case real Home matrix
remains separate and requires fresh corrected execution.
The actual Kotlin decision helper also passes a four-scenario local JVM probe,
compiled through JDK21/the repository wrapper and its existing strictly verified
compiler classpath. Focused Detekt and33 portable safeguards pass. This probe
does not execute native accessibility, the four native regression methods or
the process matrix; no production dependency/configuration override is added.

The corrected [42182051742](https://github.com/Apdelrahman1911/parlor/actions/runs/42182051742)
is terminal **SUCCESS**: twelve complete cases/twenty-four original JUnit phases,
with no omitted or duplicate case. Independent receipt SHA256
`ef829bb50e1c916dc2b2b5ae09201787a929899bade122f7634bb7fc4c25b236`
binds implementation `ec0eb95b`, executed preview `e5e3a60`, original native
outcomes, distinct process nonces, explicit force-stop/PID absence, exact source
and artifact identities, and uploaded/owned cleanup. App/test APK hashes were
attested before/after by the driver; preview did not upload those binaries.
The normal implementation is reused in PR328 without the auxiliary workflow.
Complete final-source checks must still execute ordinary API34's27 methods,
API35's3, and the separate24 process phases before protected/main acceptance.
The original compile and observer failures remain retained and failed.

iOS positive protected persistence remains subject to #258's supported-volume
and real keybag prerequisites. TalkBack/VoiceOver, physical lock/low-memory
behavior, real multiplayer recovery and energy measurements remain in the
owner's separate device campaign. This matrix does not close those gates.
