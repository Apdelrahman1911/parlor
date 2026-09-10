# Parlor — continue the existing work

Start with [the latest execution ledger](remediation-runs/2026-09-08-continuation/CONTINUE_LATEST.md).
It records completed repairs, actual platform evidence, preserved failures and
the remaining qualification gates. The [initial Linux continuation](docs/AGENT_CONTINUATION_2026-09-08_LINUX.md)
and [preserved transfer handoff](docs/AGENT_CONTINUATION_2026-09-08.md) are historical
checkpoints; their old pending statuses do not override the latest ledger.

**Not a readiness certificate.** The 16 original repairs and Whodunit
Leave-confirmation policy are committed. Normal Debug libproc evidence and the
dependency export/render/consumer chain have executed; their original source
bindings and limits remain explicit. Strict iOS Complete file protection remains
unresolved, and no same-SHA combined PASS or Store readiness is claimed.

Branch: `fix/local-readiness-2026-09-07`
Remote: `https://github.com/Apdelrahman1911/parlor.git`

Read `AGENTS.md` before executing anything. Source/build contracts take
precedence over historical prose. Obtain the current delivered SHA from Git and
compare the remote; never reset to an old handoff SHA. Preserve failed evidence,
user work and archived evidence `build/` directories. Do not repeat unchanged
successful checks or reuse a stale binding for a new execution. The original
delivery manifest remains under `handoffs/2026-09-08-agent-transfer/`.
