# Partial FIFO attestation — isolated draft 01

Author: `/root/factory_review`. Canonical source is unchanged. Root owns all test/build/native/process/cleanup execution; this author ran only source/AST/hash analysis.

## Scope and root cause

`ios-readiness-09` retained one inode-attested FIFO and one pending path from an already live-identity-verified Apple worker pair. The old ledger re-queried process UIDs for every incomplete pair. A later snapshot/UID query mismatch failed attestation and the caller retained that failure. The precise mismatch category is not in the original receipt: do not call worker exit an observed fact.

This draft permits an **exact runtime-only partial pair proof** to avoid the redundant UID subprocess, while keeping live child/parent identity+argv, attested path state, each inode/type/UID/birthtime, and parent continuity checks. The cache is never reconstructed from a pending name or serialized JSON. A newly discovered inode additionally needs fresh post-metadata live identity. Promotions are staged until sibling validation and durable publication succeed. Interrupted observations retain only validated pending claims, never inode authority. Cache bound128; path bound256. The schema and cleanup algorithm remain unchanged.

## Verification for root

Expected controls: **39**, comprising27 unchanged original tests and12 new tests. This is a static discovery count, NOT execution evidence.

From repository root, the following direct-script command guarantees the draft directory is the first import path:

```sh
python3 -B remediation-runs/2026-09-07-local-readiness/reviews/partial-fifo-attestation-draft-01/test_secondary_fifo.py -v
```

For a red witness, load this same draft test module via `importlib.util.spec_from_file_location`, explicitly put `scripts/verification/ios-readiness` first in `sys.path`, assert `secondary_fifo.__file__` equals the canonical path, and run only:

`SecondaryFifoSafetyTest.test_partial_pair_reuses_its_completed_live_identity_without_requerying_uid`

Canonical should fail because the second observation re-queries the failing synthetic UID callback; draft should pass while still requiring a fresh post-metadata live identity. Preserve red and green receipts separately. Fixtures are Darwin birthtime-dependent; they use only owned `TemporaryDirectory` FIFOs and dictionary fake PIDs (no signals).

The original late-adoption negative is unchanged:
`test_missing_unattested_then_late_created_fifo_blocks_cleanup`.

## Limits and safety

This does not guarantee that polling observes every short-lived worker. If a FIFO is first seen after its attested process lifetime is gone, no ownership is granted and cleanup must remain blocked. Unexpected current identity, changed inode/parent, malformed mutated state, unknown sibling, holders, reused PID, and late pending creation remain failures. No AppHostOwnership failure suppression/retry, native09 result rewrite, global temp scan, late cleanup adoption, application-source edit, or Store action is proposed. Independent review and actual focused controls are mandatory before root adopts the two-file patch. Source08/native10 authorization must be replaced/rebound after any adoption.
