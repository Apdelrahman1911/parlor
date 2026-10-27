# Current CI artifact scope — 2026-10-27

## Confirmed finding and issue

[#268](https://github.com/Apdelrahman1911/parlor/issues/268) tracks broad report
uploads republishing preserved historical build trees. This affects evidence
clarity, archive size and collection overhead, not shipping gameplay or an
identified privacy/protection vulnerability.

Baseline source `3806921f4902f5d841da7239c70c6c358b374adf`,
[iOS runtime run40482354118](https://github.com/Apdelrahman1911/parlor/actions/runs/40482354118),
artifact12726150533: ZIP SHA256
`ac08488ef59aa0cda6214efcb3397714f810f3f0579e7b7d9876b314d7aaa0de`.
API/source/artifact/cleanup bindings were independently checked.

| Hash-bound ZIP composition | Members | Expanded bytes | Compressed member bytes |
|---|---:|---:|---:|
| Current reports/custody | 468 | 9,758,625 | 8,365,600 |
| Historical report trees | 20,450 | 49,738,574 | 14,581,362 |

The34,423,922-byte ZIP also includes member metadata/overhead. Historical XML is
excluded from all current test counts. The stale entries caused an administrative
10,000-member reader to reject before test assertions; that failed parser was
preserved and bounded exact-module reinspection succeeded. Hosted tests did not fail.

## Implemented correction

All six ordinary verification uploads append only `!remediation-runs/**` and
`!docs/archives/**`. Positive paths are unchanged: dynamic KMP modules, Android
managed-device reports, lint/Detekt, mappings/AAB, Swift xcresult/Release evidence
and external task-owned ownership/retirement receipts remain. Archived files,
diagnostic selections, Store workflows, analyzer/dependency pins and cleanup
logic are untouched. An exact integrated security contract rejects missing,
redirected, duplicated, narrowed or additional paths/exclusions.

## Executed evidence and qualification boundary

The baseline regression fails in all six lane subcases. After correction, seven
focused cases pass, including mutation loops and filesystem selection fixtures.
The immutable upload Action's locked `@actions/glob`0.6.1 executed separately
with its exact default options on public current/archive/future-module/external
fixtures: all six selections passed, only historical members disappeared, and
search roots/external custody were unchanged. Dependencies were installed solely
in private nonshipping custody with `npm ci --ignore-scripts`; no repository
Node dependency was introduced. The first CommonJS loader attempt failed before
selection; the corrected supported ESM import preserves that failed evidence.

This is local selection evidence, **not hosted upload qualification or measured
CI-time savings**. Affected strict local checks, committed inventory and all six
current-head hosted source/artifact/cleanup records remain required before
protected integration. Fresh hosted composition must confirm the reduction.
#258 strict protection, #267 native input causality and physical/Store gates
remain independent; no production release.
