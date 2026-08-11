# Independent production-review evidence

This directory contains mechanically reproducible evidence for the independent
review that began at baseline
`9cd4040a81c4f2f8fe6f5f161dabcd5351682c02`.

`INDEPENDENT_REVIEW_INVENTORY.csv` enumerates every tracked item plus the review
generator/report themselves. Each row records its module, source set,
classification, production reachability, primary consumers, review status,
finding linkage, and final disposition. A row may only use `REVIEWED` or an
explicit exclusion; the current generator intentionally reviews all tracked
items and emits no blanket exclusions.

Regenerate it from the repository root:

```text
python3 scripts/generate_review_inventory.py
python3 scripts/generate_review_inventory.py --check
```

The final review gate regenerates the CSV and requires `git diff --exit-code`
to remain empty. The inventory does not embed its own HEAD SHA, which would make
a tracked generated file self-referential. Exact baseline/final SHAs and command
receipts belong in the final review report.

Binary assets are reviewed for identity, dimensions, packaging, and dependency
reachability; perceptual quality, real screen-reader behavior, signed-store
delivery, and physical networking remain explicitly external gates.
