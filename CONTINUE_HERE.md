# Parlor — continue the existing work

Start with [the latest execution ledger](remediation-runs/2026-09-08-continuation/CONTINUE_LATEST.md).
It records completed repairs, actual platform evidence, preserved failures and
the remaining qualification gates. The [initial Linux continuation](docs/AGENT_CONTINUATION_2026-09-08_LINUX.md)
and [preserved transfer handoff](docs/AGENT_CONTINUATION_2026-09-08.md) are historical
checkpoints; their old pending statuses do not override the latest ledger.

**Not a readiness certificate.** The original repairs are **16/16 (100%)**;
those repairs and the Whodunit Leave-confirmation policy are committed.
Historical B30 normal Debug/libproc and C08 dependency export/render/consumer
evidence is reused only within its original source bindings and limits.

**D10 run34610163120/1 has independently accepted automated combined6/6 and
scoped cleanup6/6 PASS** at tested candidate
`25cd7f57b031bebdcfb4931615782b906610703f`. Any subsequent passive handoff/evidence
delivery HEAD is separate, not a new execution; see the ledger for exact-source
receipts and review evidence. The two latest technical gates are **1/2 (50%)**:
automated combined qualification passed, but strict iOS Complete protection is
still **FAILED: A37 0 PASS / 26 FAIL**, with no defensible strict-fix ETA.
Physical-device, Store/signing/publication, account/identity and owner/legal
requirements remain separate/out of scope.

Development branch after the reviewed integration is merged: `main`.
Retained diagnostic branch: `fix/local-readiness-2026-09-07`; focused native/Linux
verification controls still require that exact branch. Do not remove or retarget
those ownership guards merely to simplify branch cleanup.
See [the branch consolidation decision](docs/BRANCH_CONSOLIDATION_2026-09-12.md)
for which historical refs can be deleted and which operational branches remain.
Remote: `https://github.com/Apdelrahman1911/parlor.git`

Read `AGENTS.md` before executing anything. Source/build contracts take
precedence over historical prose. Obtain the current delivered SHA from Git and
compare the remote; never reset to an old handoff SHA. Preserve failed evidence,
user work and archived evidence `build/` directories. Do not repeat unchanged
successful checks or reuse a stale binding for a new execution. The original
delivery manifest remains under `handoffs/2026-09-08-agent-transfer/`.
