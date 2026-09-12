# Held Windows-only collector adaptation

**Source-only author packet; not yet tested, collected, or independently
approved by this packet.** Root alone owns execution, workflow dispatch, Git
writes and cleanup. `ci_review` reviews this author's correction separately.

`collect_windows.py` is an additive adaptation of the reviewed
`../full-d-compact-collector-02.py` (17,464 bytes; SHA256
`3af9603aff8e20aa1380f844897ed356426ba29c4dd5172cc5c60edb2dea10de`).
Original collectors and canonical source are unchanged. Exact held-file pins,
base-review pins and the author-only AST/source comparison are in
`AUTHOR_REPORT.json`; `collector.diff` is the complete collector delta.

## Narrow change and evidence scope

- Require the exact case-sensitive `windows-only` CLI scope, positive run ID
  and lowercase 40-hex source SHA. Run attempt remains exactly **1**.
- Retain the exact repository, continuation branch, workflow path, completed
  workflow-dispatch run/attempt/source and all-five-job API identity checks.
- Require one non-skipped Windows job and four skipped unrelated jobs. An
  unrelated job may omit `steps` or have an empty list; any listed step must
  itself be completed/skipped. Windows still requires the original unique
  checkout and ownership steps; successful steps admit candidate paths only.
- Select only the two exact Windows verification/cleanup artifact names.
  Missing or unexpected artifact identities yield a partial collection, not
  successful qualification. Unexpected artifacts are recorded, not downloaded.
- Reuse the unchanged ZIP/path/type/collision/CRC/hash/bounds and exclusive
  selective-extraction functions. Every member, including unselected history,
  is streamed and checked before any selective extraction. Keep original
  compressed artifact and log bytes, member tables, API identities and failed
  partials. Tracked and historical XML never becomes current execution proof.

**Scope binding limitation:** the CLI and exact API job shape establish the
coordinator's intended scope, not independently recovered dispatch inputs.
Before collection, root must retain and review the fresh request and
`run-association.json`, pinning the reviewed frozen workflow/source, collector,
intended scope, actual run ID and attempt. Review preserved raw job logs and
actual step/receipt contents separately. No log parser is added. The collector
never computes test-execution counts or upgrades candidate XML to runtime
evidence; its terminal states remain `*_NOT_RUNTIME_REVIEW` (or failure).

## Root-only usage after independent source review

From the repository root, run the three focused shape regressions in the
coordinated lane, recording exact input hashes, command/result and cleanup:

```sh
/usr/bin/python3 -B remediation-runs/2026-09-08-continuation/reviews/windows-targeted-collector-01/test_collect_windows.py
```

The three methods cover: four step-less skipped jobs with one Windows job;
wrong scope/active-job shapes and a skipped job containing an executed step;
and failed-Windows/current-looking versus tracked/historical XML classification.
They use synthetic dictionaries and mock invalid-CLI side-effect entry points;
they do not launch network operations or application builds. Existing v02's
14 actual fixtures are inherited-control evidence, not execution of these new
three methods. Root must review their new actual results before collection.

After reviewed source/control changes and the mechanical inventory are
committed, root pins the fresh frozen SHA and dispatch request. **No future
run or source identity is invented by this packet.** Wait for that associated
run to complete; confirm the destination is absent and its existing parent
is nonredirected. Substitute real reviewed values for both placeholders:

```sh
/usr/bin/python3 -B remediation-runs/2026-09-08-continuation/reviews/windows-targeted-collector-01/collect_windows.py RUN_ID NEW_FROZEN_SOURCE_SHA windows-only
```

The new destination is
`remediation-runs/2026-09-08-continuation/actions/RUN_ID/`.
Never remove or reuse an old successful/failed destination to make this
command pass. Invalid binding/shape fails closed, preserving anything already
written; inspect before considering a separately reviewed recovery. Preserve
required evidence before root's stop/owned-output cleanup cycle.

## Retained limitations

- Compressed artifact cap: 64 MiB each / 512 MiB aggregate; 50,000 members per
  artifact; 256 MiB expanded per archive / 128 MiB per member. Logs retain a
  128 MiB compressed cap, 1,500 members, 256 MiB expanded and 64 MiB per member.
- Declared artifact sizes are checked before downloading; actual download
  file sizes are checked **after** the subprocess exits (180-second timeout),
  not through an OS-enforced streaming disk quota. These are inherited limits.
- This is not a general-purpose collector. The reviewed workspace prefix,
  exact job/step/artifact names and attempt 1 are deliberate. An early
  cancelled Windows job lacking planned checkout/ownership steps fails closed.
- If validation fails before the log download, no complete log collection is
  claimed. Already-downloaded files and a failure receipt remain; no blind retry
  or reinterpretation of historical test reports is permitted.
- Current-named receipts are content-unreviewed. API success and successful
  checkout/ownership are not application-runtime, cleanup, Store, physical
  device or readiness evidence. Those conclusions need independent raw-evidence
  review; strict validation is not weakened by this adaptation.

The author performed only stdlib parsing/string/hash inspection and writes
inside this held packet. No imports/tests/builds/network, Gradle cleanup or
qualification were performed by the author.
