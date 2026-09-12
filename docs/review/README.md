# Independent production-review evidence

This directory contains historical independent-review reports and a separate
mechanical tracked-file inventory, using baseline
`9cd4040a81c4f2f8fe6f5f161dabcd5351682c02`.

`INDEPENDENT_REVIEW_FINDINGS.md` records every confirmed independent finding,
its root cause, affected code boundary, dedicated regression evidence, exact
fix commit, and closure status. It also maps every remediation commit so no
quickly fixed defect disappears from the review history.

`INDEPENDENT_REVIEW_INVENTORY.csv` mechanically enumerates every tracked item
plus the output itself. Paths infer module, source set, classification,
reachability, consumers, and a suggested disposition; Git history supplies
historical change/finding references. These heuristics do not inspect source
contents or the build graph. Every row states `MECHANICALLY INVENTORIED;
INDEPENDENT REVIEW NOT ATTESTED`, even when a historical finding is linked.
No mapped change means no historical reference, not that no defect exists.
Untracked files are deliberately excluded. Stage new files before regeneration
when authorized; do not mistake the index-based inventory for the full dirty
working-tree scope.

Independent coverage must separately bind reviewed file hashes, line ranges,
reviewer identity, evidence, and unresolved gaps to the exact source snapshot.
Preserve original human findings and audit receipts; never regenerate them from
this inventory. Archive an old generated CSV before replacing it when it is
needed as historical evidence.

`INDEPENDENT_REVIEW_FINDING_OVERRIDES.csv` maps full remediation commit SHAs to
the exceptional finding text used by the inventory. This keeps historical
finding attribution data-driven without relying on collision-prone abbreviated
commit IDs in generator code.

Regenerate it from the repository root:

```text
python3 scripts/generate_review_inventory.py
python3 scripts/generate_review_inventory.py --check
```

`productionReleaseAutomationCheck` runs the generator's `--check` mode. It
checks CSV freshness against tracked filenames and history, not source review,
test execution, or absence of defects. Regeneration followed by an empty Git
diff is likewise only a freshness check. The inventory does not embed its own
HEAD SHA, which would make a tracked generated file self-referential. Exact
source identities and command receipts belong in independent review evidence.

Binary assets need separate identity, dimensions, packaging, and reachability
inspection. Perceptual quality, real screen-reader behavior, signed-store
delivery, and physical networking need their own applicable runtime evidence.
