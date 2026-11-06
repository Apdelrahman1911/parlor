# Android saved-session deletion across process death

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

Prepared source passes31 portable controls and focused application Detekt with
JDK21, the wrapper and strict verification. These results do not compile or
execute the native phases. Fresh hosted native execution, complete protected
PR qualification, review/merge and distinct main verification remain required.
This work follows the separate #329 locale-ownership correction; no previously
failed locale or native-input run is retroactively declared successful.

iOS positive protected persistence remains subject to #258's supported-volume
and real keybag prerequisites. TalkBack/VoiceOver, physical lock/low-memory
behavior, real multiplayer recovery and energy measurements remain in the
owner's separate device campaign. This matrix does not close those gates.
