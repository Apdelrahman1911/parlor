# Independent production-review evidence

This directory contains mechanically reproducible evidence for the independent
review that began at baseline
`9cd4040a81c4f2f8fe6f5f161dabcd5351682c02`.

`INDEPENDENT_REVIEW_FINDINGS.md` records every confirmed independent finding,
its root cause, affected code boundary, dedicated regression evidence, exact
fix commit, and closure status. It also maps every remediation commit so no
quickly fixed defect disappears from the review history.

`INDEPENDENT_REVIEW_INVENTORY.csv` enumerates every tracked item plus the review
generator/report themselves. Each row records its module, source set,
classification, production reachability, primary consumers, review status,
finding linkage, and final disposition. A row may only use `REVIEWED` or an
explicit exclusion; the current generator intentionally reviews all tracked
items and emits no blanket exclusions. Untracked working-tree files are
deliberately excluded so the result is reproducible from the Git index used to
form the next commit. Stage newly added files before regeneration.

`INDEPENDENT_REVIEW_FINDING_OVERRIDES.csv` maps full remediation commit SHAs to
the exceptional finding text used by the inventory. This keeps historical
finding attribution data-driven without relying on collision-prone abbreviated
commit IDs in generator code.

Regenerate it from the repository root:

```text
python3 scripts/generate_review_inventory.py
python3 scripts/generate_review_inventory.py --check
```

`productionReleaseAutomationCheck` runs the generator's `--check` mode, so CI
fails when the committed CSV differs from the tracked tree. The final review
gate also regenerates the CSV and requires `git diff --exit-code` to remain
empty. The inventory does not embed its own HEAD SHA, which would make a tracked
generated file self-referential. Exact baseline/final SHAs and command receipts
belong in the final review report.

Binary assets are reviewed for identity, dimensions, packaging, and dependency
reachability; perceptual quality, real screen-reader behavior, signed-store
delivery, and physical networking remain explicitly external gates.
