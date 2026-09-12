# Final-observation control — independent review 01

**Changes requested before execution**, scoped to task-owned reporting controls, not another application defect. Reviewer: `/root/release_fix_review`. Reviewed all134lines of recorder SHA`d17a9578…7f5607e`.

1. Evaluate cleanup rather than copying fields and printing PASS. Include modern stop/worker/output failures, new apphost temporary/device/FIFO/unknown-holder fields, and the three native simulator child receipts. Failed tests may still have valid cleanup.
2. Pin the original baseline before opening its paths, reject symlink ancestry/final-file links, and deduplicate additional task-owned receipts. No actual unsafe link or changed baseline was found.
3. Preserve the exact legacy `ios-b1-red` limitation: its empty process scan has exit1 and no PID ledger. Do not synthesize historical no-worker proof.

Parent accepted these corrections; revised source and negative tests await separate review. Existing40cycles show stop0/empty cleanup errors and outputs;39modern receipts explicitly show no owned worker survivors. Allthree native child receipts attest deletion/preservation/empty UUID workers. This is not an observation of a later active apphost cycle.

Fresh baseline comparison:1618original files present,1584unchanged,34authorized tracked modifications, no changed pre-existing untracked work or symlink ancestry. Refs, stash and empty index unchanged.

No source edits, builds or background workers were started by this reviewer. Final report/runtime approval remains pending.
