# Android locale reset authority — November 7, 2026

## Reproduced failure, not an observer workaround

[#332](https://github.com/Apdelrahman1911/parlor/issues/332) recurs in
[PR336 run42314073678](https://github.com/Apdelrahman1911/parlor/actions/runs/42314073678/job/128222305256),
authored `21b22216`, executed merge `3cccd5dc`, tree
`e979003eb0a8d63d71aa9e2b228cea04bc6f49ec`. API34 has **32 PASS / 1 FAIL**:
`AndroidLocaleConfigurationRuntimeTest.testArabicSettingsSurviveHandledOrientationChange`.
English rotation and optimized API35's three methods pass. The process-restart
step never starts; this run does not verify the separate #335 retirement fix.

One actual rotation leaves the persisted Arabic preference, Settings route and
same Activity alive, but renders English. Compose-before, cached native, freshly
cleared native and Compose-after observations all show English heading1/Arabic0.
Process/Compose/Activity locales are English; the Activity is focused with no
pending measure. This is not merely stale accessibility state or GPU startup.
Earlier successful PR333/334 samples do not establish a permanent correction.

Original job log SHA256:
`27ba21bb84ba8fe0681e3e7bc5f64d353acce8f84fdd3d29e5b944b3405e8fca`.
Digest-verified artifact13550822454:
`402438e50e12e405bae6cbf88bb57adcaa96ab0fd8c19423a91ab2686feea014`.
Independent source/XML/log/artifact readback:
`43ab104a38933bef46543acbfb3b57cc9411bb3f0ac71f4f9ad26a01624cadaa`.

## Why the callback-only correction is insufficient

Android14's [ConfigurationController](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/app/ConfigurationController.java#180)
resets defaults from **application resources** before returning for stale/equal
configuration sequences. Equivalent non-Activity configuration notifications
are also suppressed. A registered application callback therefore cannot repair
every reset. Parlor localized Compose's context but left Android's application
resource authority in the system language.

These are source-confirmed reset paths and a native-confirmed rendered-language
recurrence. The exact internal callback sequence of the failing run was **not
traced**; a specific stale-sequence ordering is not presented as captured fact.

## Correction

- `ParlorApplication` inherits `LocaleAwareApplication`. Each committed language
  effect owns an independent configuration context exposed by `getResources()`.
  Framework resets now derive their locale from the actual selected language.
- The existing exact-scope ownership mechanism also protects these contexts.
  Disposing an older Activity cannot replace its successor, even with an equal
  language. Contexts reference the application base, never an Activity.
- Only locale/layout direction is overridden. Android remains responsible for
  orientation, density, font scale, night mode and window updates. No shared
  `Resources` object is mutated. System mode exposes the live base context.
- Process defaults preserve the selected locale followed by all distinct
  platform fallbacks, matching Android's adjusted-default ordering. Existing
  callbacks still maintain the platform restoration baseline, but are no longer
  the sole defense against a framework reset.
- Resource reads use a volatile context reference; ownership changes stay on
  the UI thread. No locks, worker, polling, input retry or extra rotation is added.

## Regression and qualification requirements

Nine new common controls cover fallback ordering and nullable resource-context
ownership, including a **modeled**, not native, callback-free reset. A source
contract requires the shipping Application binding and scoped-resource adapter.
The original two native EN/AR rotation methods additionally assert application
resource authority and process language before/after rotation, and unchanged
base-context locales. A third actual System-mode journey also requires the
unmodified base Resources instance. Exact headings, single operations and
deadlines stay unchanged.
Process receipts bind the new production resource owner, defaults helper and
Application entry point as well as the existing driver and native fixtures.

At this source-authoring checkpoint the correction requires fresh local checks,
complete protected PR qualification, all twelve process cases/twenty-four phases,
and distinct main verification. Neither #332 nor #335 is closed by this report.
Original iOS input causes, #258 supported-volume protection, physical Phase4 and
Store/account gates remain separate. No physical or release acceptance is claimed.

## November 9 terminal readback and PID framing correction

The focused [run42320315818](https://github.com/Apdelrahman1911/parlor/actions/runs/42320315818)
finished **FAILURE** on November 7. Its application checkout is `ba00f075`, tree
`a006073bf6013f9ce0968970103f7cc524995a31`; workflow `721448b5` adds only the
focused harness. Original XML matches the exact34-method source roster with
**34 PASS / 0 FAIL / 0 SKIP**, including English, Arabic and System rotation.
The21 process-source bindings match the application commit. Output ownership,
artifact linkage and the owned cleanup receipt pass independent readback.

The first process case's preparation completes, but the post-launch observation
raises `Malformed or ambiguous public process observation`; the retained matrix
contains zero complete cases. The original live PID bytes were not retained.
This does not certify the12-case/24-phase retirement matrix or full PR/main.

A local real `/bin/sh` control reproduces a definite command/protocol mismatch:
`pidof` normally supplies a trailing newline, and the marker command adds a
leading newline. Thus a live PID becomes `1234\n\nPARLOR_PIDOF_EXIT=0\n`, which
the deliberately strict parser rejects. Capturing the PID output before one
`printf` produces exactly `1234\nPARLOR_PIDOF_EXIT=0\n` while retaining the device
query's status. Absence remains independently marked by status1; ambiguous PIDs,
unexpected statuses and malformed observations still fail closed. No parser
relaxation, extra action, retry or deadline change is introduced.

The pinned [Android14 Toybox implementation](https://android.googlesource.com/platform/external/toybox/+/android-14.0.0_r1/toys/lsb/pidof.c)
also explicitly appends a newline after successful PID output. This independently
supports fixing the producer's framing rather than accepting malformed messages;
it does not supply the missing original failed observation bytes.

Two real-shell regression methods cover newline/no-newline live output, absence,
multiple PIDs, invalid PIDs, unexpected status and malformed output. The new live
round-trip fails against the original command and passes with the correction;
all51 focused process/retirement controls then pass. This is a controlled shell
reproduction, not captured native PID bytes or proof of the older main's cause.

Retained original native artifact13553371928 SHA256:
`2c7752e04c374a2136215576dbbb4a8f5a2d6af3091e8cd85c0b1520da00f0e8`.
Cleanup artifact13553366995 SHA256:
`1bd72aa15e450316868e517f758a7b3e895af322b7b6a05be782e67965807437`.
Original XML SHA256:
`467c3be226a3103eb6f4e4f6f2d950312193555a613cd946304bed37fc627b01`.
Original job log SHA256:
`a5d4c5d0caaaff64eacb712b9644231036462181753900476569d04585824a97`.

At this source-authoring checkpoint, PR336's earlier `21b22216` head remains
failed. On November 9 the owner authorized committing and publishing the prepared
correction to the existing PR and continuing qualification without another
approval question. No Issue closes or release hold lifts. Fresh corrected-source
process acceptance and complete protected PR and distinct-main qualification
remain required, separately from all physical, supported-volume security and
Store/account requirements.
