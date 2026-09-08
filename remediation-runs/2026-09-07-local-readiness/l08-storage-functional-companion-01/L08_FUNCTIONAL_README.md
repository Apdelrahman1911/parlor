# L08 Storage Functional Companion

## Scope and evidence boundaries

This is a separate, copy-only diagnostic harness, **not a modification or
successful rerun of original strict L08**. The original 65 controls remain in
`scripts/verification/ios-readiness/`; this directory preserves 63 verbatim and
changes only its cloned `copied_sources.py` and `run_ios_readiness.py`. Ten new
companion files are allowlisted by
`../reviews/l08-functional-companion-clone-manifest-01.json` (SHA-256
`3036231a1bcfc9d567b8381ee33d89cd381377bea2b10790ddde0f90baf682fb`).
Original strict sources, assertions, parsers, tests and failed native09 evidence
are not overwritten, reclassified or bypassed.

The separately validated direct Foundation experiment retained dictionary/key
observations and unsuccessful Complete comparisons; it did not demonstrate
app-container, encryption, physical-lock, backup or Store correctness. This
companion collects the still-independent functional paths that strict L08's
first required directory comparison prevented from executing. It never repairs
the metadata or substitutes a different protection class for Complete.

## What runs, and what stays fatal

The owned copied Debug app performs the original matrix: 13 cold boots,
20 distinct operations, 13 metadata checkpoints and **26 actual required
directory/file Complete comparisons**. Every primary getter remains the same;
each successful return records its actual equality immediately. At most four
comparisons belong to one operation. A later failure preserves only the valid
completed prefix. Missing or failed comparisons cannot become metadata PASS.

| Boots | Actions | Metadata checkpoint sites |
| --- | --- | --- |
| 1, 3, 5, 7 | Save | `save`: Classic, Elimination, Doctor ON, Doctor OFF |
| 2, 4, 6, 8 | Load; actual Home resume; continue | `load`: same four variants |
| 9 | Seed legacy, malformed neighbor and dual copy | `dual-before-legacy-seed`; `dual-after-tag-damage` |
| 10 | Load/migrate legacy; Home resume; continue | `load`/legacy; `legacy-load-dual`/dual |
| 11 | Observe damaged neighbor; actual Retry/Discard | None |
| 12 | Observe retained dual copy; actual Retry/Discard | `retained-dual` |
| 13 | Confirm final absence after restart | None |

Unchanged fatal assertions include actual Koin store/filesystem/P2pKit bindings,
reachable deterministic snapshots, exact envelopes and recovery routes, real
Home/Retry/Discard callbacks, attached production controllers, Whodunit terminal
play, Mafia ON four consecutive same-target nights and OFF alternate/skip
semantics, terminal writer deletion, corrupt-record isolation, last-copy legacy
preservation, path/type/existence checks, backup exclusion, protection constants,
ciphertext header, bounded reads and cancellation propagation. Corruption or
unavailable storage never means NotFound. The probe never deletes directly to
simulate a successful terminal writer or discard.

Only the two Complete equalities have separate nonfatal **observations** in
this companion. Their values and unsuccessful outcomes remain required,
explicit obligations. Original strict L08 still throws on those checks.

The original eight native-storage boots, three controlled-host fixtures,
language/OS observations and four UIKit-container tests remain independent
requirements. These are not physical P2pKit LAN tests or full UI game traces.

## Separate schema and strict reporting

The new scenario, inner kind, boot environment and receipt names contain
`l08-storage-functional`; the XCTest method is
`testDSC01ActualSettingsLocalSessionsAndOSWithL08FunctionalObservation`.
Original strict parsers reject companion rows even if every comparison matches.
The original **4096-byte inner and 16384-byte wrapper limits remain unchanged**.
All observations are closed metadata: no seeds, role maps, game payloads, paths
or NSError descriptions are added to comparison records.

The functional validator requires all 20 operations, all checks true, 13 unique
boots, matching XCTest receipts, exact metadata sequence and real image binding.
It reports `FUNCTIONAL_MATRIX_VERIFIED` plus separate `functional_gate` and
`complete_comparison_gate` values. `original_strict_l08` always remains
`NOT_SATISFIED_BY_COMPANION`. Complete comparison FAIL does not prevent remaining
functional/host subgates, but functional failure remains fatal.

Available failure rows are image-bound before XCTest outcome rejection, so a
failed run can retain its actual executable provenance. This partial image
inventory always says `matrix_verified: false`; it cannot satisfy missing
operations, missing tests or failed native storage health.

The final companion result is **never PASS**. A fully validated functional run
with intact source, controls and cleanup is `PARTIALLY_VERIFIED` (exit 2), with
strict-L08 and other unmet obligations listed. Genuine runtime, storage-health,
source, control, signing, ownership or cleanup failure remains FAIL (exit 1).

## Review and execution

Implementation/source review is not test execution. All added controls require
root's executed regression receipt and independent review before native use.
From the repository root, the coordinated owner can run:

```sh
F=remediation-runs/2026-09-07-local-readiness/l08-storage-functional-companion-01
PYTHONDONTWRITEBYTECODE=1 /usr/bin/python3 -B -m unittest discover -s "$F" -p 'test_*.py' -v
```

These tests include the unchanged original controls, original strict parsers,
fake-wrapper stale-build cases, exact clone/source contract and new comparison,
namespace, failure-prefix, discovery, image-binding and classification tests.
They use disposable synthetic data and owned temporary directories. Inherited
controls compile and run bounded host Swift/Foundation path and encoder fixtures;
this is not Simulator/app execution. Source mutation guards compare complete
executable regions; green controls are not application Swift/Kotlin compilation.

After freezing the current source and approving the exact control/source hash,
the root can use the unchanged source-binding and `--control-manifest` procedure
in `README.md`, but invoke this directory's runner. Choose a fresh unused
`ios-readiness-NN` receipt name. The familiar name format is deliberately
retained for the already-reviewed ownership guards; the receipt execution kind
explicitly identifies this separate functional companion. No shared binding or
earlier evidence may be overwritten. Simulator ad-hoc mode, if approved, has
exactly the existing local-only signing/entitlement checks; no Store credentials.

## Ownership and cleanup

Root exclusively owns Gradle/Xcode/native/test execution. Review agents do not
start competing builds or stop another task's workers. The original build lane,
source-copy allowlist, immediate/final Gradle stops, fresh simulator identity,
PID/start attestation, secondary FIFO ownership, worker checks, cache-symlink
handling and exact copy/DerivedData cleanup are unchanged. Failures still enter
the finalizer. Unknown holders or ownership failures keep cleanup failed, not
falsely successful. Only task-owned outputs are removable; global caches and
pre-existing source, audits, credentials and user work are preserved.

Actual Simulator metadata, runtime limits, command receipts and cleanup must be
reconciled after execution. This harness neither waives strict L08 nor grants
physical-device, Store, release, publication or production-readiness approval.
