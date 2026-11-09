# Android instrumentation retirement — November 6, 2026

PR336 run42314073678 later fails the separate Arabic handled-rotation method
before process acceptance starts. The retirement implementation below is
preserved but not freshly native-qualified by that failed run. See the
[locale resource-authority follow-up](2026-11-07-ANDROID-LOCALE-RESOURCE-AUTHORITY.md)
for the additional shipping correction and its distinct evidence boundaries.

## Confirmed boundary and limits

[Issue335](https://github.com/Apdelrahman1911/parlor/issues/335) tracks a
non-shipping process-driver synchronization gap, not a demonstrated application
crash. PR334 passed complete protected qualification and merged as `1e08bab4`.
Its distinct [main42307325714](https://github.com/Apdelrahman1911/parlor/actions/runs/42307325714)
fails the Common job's process-restart step: three cases pass; the fourth
LastLight/ar/Multiplayer preparation passes its original JUnit method and
`am start -W` satisfies the existing `Status: ok` assertion. The following
`adb shell pidof me.parlor.android.debug` exits1 before force-stop/verification.
Only **3 complete cases / 7 original phase methods** executed, not12/24.

The signing-oriented command wrapper withheld this query's output. The original
app-absence versus adb-transport cause is therefore **unproven**. Android's
API34 33/33 and optimized API35 3/3 methods, including EN/AR rotation, pass.
Emulator37.2.13.0/API34 booted with `swiftshader`; this is not a reproduced #312
GPU failure. Native descendant/ordinary cleanup pass; the failed-runtime AVD
is deliberately retained. The failed main cannot be qualified by passing jobs.

## Source-confirmed protocol gap and correction

Android14 [`finishInstrumentationLocked`](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r75/services/core/java/com/android/server/am/ActivityManagerService.java#16420)
queues `InstrumentationReporter.reportFinished()` before its ordinary
`finished inst` package force-stop. The separate
[reporter](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r75/services/core/java/com/android/server/am/InstrumentationReporter.java)
delivers asynchronously. `am instrument -w` completion is not a process-death
barrier. The original driver assumed it was, immediately starting the next
operation. This source ordering does **not** prove the uncaptured original exit.

`scripts/android/run_resume_process_acceptance.py` now observes retirement of
the exact attested instrumentation PID before the single launch or next phase.
Retirement consumes the **existing90-second phase budget**, with bounded passive
observations; no action/deadline retry, extra force-stop or native test change.
`resume_process_contract.py` requires both retirement receipts and distinguishes
explicit device-side pidof absence from transport, malformed or replacement-PID
failures. Original JUnit, process nonces, storage/privacy assertions, twelve
explicit stops, APK/source binding and owned cleanup remain mandatory.

## Verification and retained evidence

The deterministic report-before-stop schedule fails against the original driver
at the PID boundary. This is a **controlled synthetic regression**, not a native
reproduction. The corrected focused controls cover late teardown, single
actions, exact deadlines, cancellation, transport errors, wrong/malformed PIDs,
bounded observations and missing receipts:

```bash
python3 -B -m unittest \
  scripts.release.tests.test_android_instrumentation_retirement \
  scripts.release.tests.test_android_resume_process -v
```

Initial49 focused controls pass. Fresh final-source full protected qualification,
all12 native cases/24 phases with retirement receipts, normal merge and distinct
main remain required; later evidence belongs on Issue335 and its PR, not an
assumption of success in this source-authoring checkpoint.

Original log SHA256:
`68471dfff99fda23211db6ff0d30ecef73a51c2057aa2e160b874a25f4effbf4`.
Artifact13548661691 SHA256:
`2fa1bbc4b2dd414eb79dae3fb3987527eb5961a4f66131285f5d9d38fedea6a3`.
Independent failed-main readback SHA256:
`8d3bd0ac9ebe0d394cfd1049232e7344f2aaeff51a407e8672d0cbcd60ff63b3`.
Original source/logs and official AOSP files are retained in agent custody.
#332's whole-main gate, #253/#258 positive iOS storage, original #267/#276 native
causes, physical-device and Store acceptance are not satisfied by this change.
