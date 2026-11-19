# Android navigator image provisioning — October 30, 2026

## Confirmed finding and scope

[P2 CI issue #297](https://github.com/Apdelrahman1911/parlor/issues/297) records
fresh [PR294 run41079982618 /Linux job124146199195](https://github.com/Apdelrahman1911/parlor/actions/runs/41079982618/job/124146199195),
source `b758f25d`, tree `75e061ad636ad7c78a924b403bfb5c132d7d2b33`.
Full source gates succeed in20m19s and the API35 Release3-test invocation succeeds.
The subsequent Debug API34 setup fails with `Error on ZipFile unknown archive`
and `InstallFailedException`; navigator tests never start, and unsigned artifact
inspection is skipped. This whole run remains unqualified.

The existing workflow provisions/revision-checks only API35. Its mandatory
`productionAndroidNavigationRuntimeCheck` needs API34, but AGP downloads that
unpinned image late. The same gap exists in the still-disabled candidate
preflight. Closed #7 covers the previously resolved absence of emulator tests,
not this later dual-lane provisioning defect; it is not reopened.

## Implemented correction

- Pin the separate API34/google/x86_64 image at revision14 in release policy;
  retain the API35 Release image at revision9.
- Install both images with the official SDK manager before compilation. Bind
  the SDK root, bound installation at600 seconds with TERM/kill grace and an
  11-minute step limit, and fail closed on installation errors or missing,
  duplicate or unreviewed revision properties. No automatic workflow retry.
- Explicitly set both existing AGP managed devices' tested ABI to `x86_64`,
  retaining their APIs, image source and64-bit requirement. Shipping ABIs,
  Release optimizations and test tasks are unchanged.
- Keep the identical setup in candidate preflight without enabling any candidate
  or promotion job. Both runtime lanes, ephemeral signing and descendant
  cleanup remain mandatory and unchanged. Standard free runners only.

## Executed evidence and limits

The actual original setup Bash, with only hardware admission and SDK tools
simulated, runs two methods: **one exact missing-API34 assertion failure /one
installer-error propagation control pass /zero errors or skips**. The first
preparation reader accidentally selected duplicate step names across jobs;
that error is retained. The corrected reader scopes the same original step to
`desktop-android`; it does not simulate a network fault or execute an emulator.

The correction adds **nine software controls**: real setup Bash/package/error/
revision execution, exact workflow/policy mutations, both existing ABI lanes,
and disabled candidate consistency. All nine plus the complete88 existing
workflow methods pass: **97 methods, zero failures/errors/skips**. Original and
corrected log SHA256:
`94987537c8b850dd92b036d82f2fdd17b4e05427690011eba5c782a92b303f01` /
`48635f9618a7d47734601a9586e6acfc8aa5d64cb9d02e891dd94d811f2681dc`.
Intermediate contract-string escaping and candidate-predicate preparation
failures are retained; no existing assertion is weakened.

A separate bounded current official API34 revision14 download matches
1,563,721,130 bytes and advertised SHA1
`e0f6c9a0691aa27bd597d0deb1bcfdc943ac8ca7`; SHA256
`783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833`.
All24 ZIP entry CRCs pass. Only this owned downloaded ZIP is removed after
preserving readback/hashes. **The failed runner did not retain its archive**;
truncation, proxy/network and disk causes remain unconfirmed. A current valid
archive does not guarantee later availability or repair native #267/#276.

## Integration gates

These are preparation results, not final-source compilation or runtime acceptance.
Require complete strict final-source build/security/release gates, fresh full
protected CI, independent native/source/artifact/cleanup readback and distinct
actual-main verification before merge/closure. Do not replace API34 with35,
skip either lane, change Store identity/signing, relabel old successes or retry
failed41079982618 blindly. Physical gameplay/recovery/energy/accessibility and
Store acceptance remain separate. The overall overhaul is not complete.
