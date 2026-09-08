# WD-C2 — Blocked pending content-author decisions

Status: **confirmed grouped authored-content defect; no resource fix applied**.
Original finder `/root/whodunit_cont`, independent original validator
`/root/mafia_cont`. The original candidate/validation remain under
`audit-runs/2026-09-05-source-audit/`; they have not been rewritten.

The four currently confirmed contradictions were rechecked in the shipping
JSON. No source, documentation or inspected resource history establishes
which conflicting account should win. Frequency is not authorial authority.
Root has asked the owner the following questions; this child must wait for
the answer rather than inventing story events.

| Resource | Exact authorial choice required |
|---|---|
| `last-dinner.json:154–162,284,336,360,376` | Did James actually return/light his cigar at **9:00 or 9:15**? The factual timeline says9:00; objective clues/final say9:15. Preserve deliberately false9:10 alibi unless the owner separately directs otherwise. |
| `layla-halabi.json:18,85,219` | What is the single outage time: **9:05 or 9:45**, or another owner-specified time? Intro says9:45, universal evidence9:05, Sami's factual9:35 entry says it already happened. |
| `jasmine-ring.json:75–88,325` | Retain Nadim's **10:50 entry / 11:00 hiding / 11:10–11:12 descent** and shorten the final duration, or did he enter/hide a full hour earlier? |
| `khan-el-khalili.json:82–84,195,331` | What are Karim's intended **poisoning time and shared-records arrival time**? Timeline says9:40/10:00; Refaat/final corroborate10:15, with final poisoning the minute before. |

All resources above are under
`/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/files/cases/`.
Unconfirmed age-order/Zamalek-duration observations, fake alibis, toxicology,
motives and other variants are **not** authorized corrections.

## Exact unchanged identity and compatibility consequences

`wd-c2-unmodified-resources.json` records each resource's current raw byte
SHA-256, size, lines, version and equality with baseline HEAD. Those hashes
are source-file receipts, **not** claimed Kotlin content-identity digests.
All four envelopes currently advertise version **1.0.0** and remain unchanged.

`WhodunitContentIdentity.kt:42–66` hashes the canonical complete envelope
(signature excluded) and admission requires exact version/digest. Editing
prose changes that digest even without a version bump. Current content-bound
local saves must match their recorded case identity; mismatches fail closed.
Legacy saves without content identity still need matching deterministic clue
text/history at case-bound validation.

After author approval, explicitly decide the content-version/recovery policy
and review every affected factual path; do not bypass admission identity or
silently repair existing saves. C1/C3 code-only corrections do not affect
these resources, versions or content digests. Future checks should include
strict bundled/catalog validation, all supported killer/mode combinations,
content identity/resume tests, and editorial assertions for the approved
facts. No schema test can certify narrative coherence automatically.

No builds, devices, resource edits, branch operations or cleanup-producing
processes were run by this child for this blocked item.
