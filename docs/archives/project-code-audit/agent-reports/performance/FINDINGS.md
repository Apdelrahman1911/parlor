# Performance findings (lead)

### PR-001 — Host mailbox 8 is small vs 16-player command+heartbeat
- Severity: Medium / Confidence: High / Needs runtime for user impact
- Same root as SN-001. Code defect is the coupling; stall under load is unprofiled.

### PR-002 — Snapshot encode on every accepted command
- Severity: Low / Confidence: High / Needs runtime
- Host-authoritative design requires it. Case-sized JSON may dominate on
  low-end devices. Bound 256 KiB.

### PR-003 — Inter + JetBrains Mono always packaged (~1.0 MiB)
- Severity: Informational / Confidence: High
- `design-system` composeResources fonts. No subsetting observed.

### PR-004 — God-file size is a maintainability/perf-review risk
- Severity: Low / Confidence: High
- Same as AR-006. Not a measured jank finding.
