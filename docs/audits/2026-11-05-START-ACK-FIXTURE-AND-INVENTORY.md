# Source-specific CI recovery: receiver readiness and inventory history

Related: [PR322](https://github.com/Apdelrahman1911/parlor/pull/322),
[#323](https://github.com/Apdelrahman1911/parlor/issues/323).
These are verification-fixture/publication defects, not original iOS causality.

## Actual Windows failure and deterministic boundary

[41994377043](https://github.com/Apdelrahman1911/parlor/actions/runs/41994377043),
authored source `6febf582`, executed merge `33bc1823`, Windows job127133904837,
fails `host_rejects_reliable_start_acknowledgements_before_admission` at its
unchanged2000ms Closed waiter. Original artifact13399898192, ZIP SHA256
`5e4eee00ce34c0e0cea9941dc4bb5c5b2c27b41f287e220f16a1ff01454e374d`,
XML SHA256 `0b9770a725ec259cf484ec04f4e6dc5f808fa88386c153077d5ed7eb64fbe0c0`.

The Desktop fixture emits a fake session, yields its sender and emits a frame
into a replay-zero SharedFlow. Yield cannot establish receiver subscription on
an independently scheduled dispatcher. At `e9f05533`, two real methods execute:
one baseline method passes; the new independent-scheduler fault test fails the
exact `sent.isCompleted.isFalse()` assertion with zero frame subscribers.
This establishes the fixture race, **not** the original Windows scheduler trace
or a shipping packet-loss/authentication defect.

Require the actual frame subscriber before the single injection, within the
existing2000ms budget. Keep admission rejection, Closed, empty membership and
cleanup checks. The deterministic fixture executes the production100ms rejection
flush using its independent virtual clock; it does not extend a real deadline.
At `88da99a4`, both focused methods and transport Detekt pass, zero failures,
errors/skips; corrected XML SHA256
`2f44b555fb563a7b0aed5a44697e4b58964e4ae9ca8c3b44d12278fc5efd5f2b`.
Production source remains unchanged. Intermediate cleanup/virtual-clock fixture
failures and their owned-process evidence remain preserved, not accepted.

```sh
./gradlew :shared:transport-p2p:desktopTest \
  --tests '*host_rejects_reliable_start_acknowledgements_before_admission*' \
  --tests '*unadmitted_reliable_start_fixture_waits*' \
  :shared:transport-p2p:detekt --dependency-verification=strict
```

## Common inventory failure and prevention

The same source's Common job127133904499 passes all804 Python controls, including
all73 worker ownership methods, then correctly rejects a stale review inventory.
Local `python3 -B scripts/generate_review_inventory.py --check` reproduces it.
The generator references the latest non-merge semantic-file commits. Committing
a semantic report together with its generated CSV changes that report's history
reference **after** generation; pre-commit freshness alone is insufficient.

Commit all semantic files first. Generate the CSV; commit **only the CSV**; then
run `--check` again on the committed source. Preserve the generator and gate:

```sh
python3 -B scripts/generate_review_inventory.py
git add docs/review/INDEPENDENT_REVIEW_INVENTORY.csv
git commit -m 'docs(review): refresh committed-source inventory'
python3 -B scripts/generate_review_inventory.py --check
```

Full affected transport-suite and inventory controls, fresh source-specific
protected checks/Windows/native journeys, all optimized Release architectures
and distinct resulting-main acceptance remain required. The old Common/Windows
failures stay unqualified: after all corrections were reviewed, the obsolete
remaining wrapper build was cancelled to avoid an unusable qualification. Its
source-bound finalizers, owned cleanup and uploads pass; the build is **cancelled**,
not qualified. All original failure/cancellation evidence remains preserved.
No unchanged rerun, skipped check, timeout increase, original iOS/security
closure, physical-device acceptance or Store release is inferred.
