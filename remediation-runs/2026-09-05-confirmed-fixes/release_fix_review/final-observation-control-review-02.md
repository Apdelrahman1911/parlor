# Final-observation v2 — independent review 02

Read all253recorder lines,119test lines, and the raw14-test execution/cleanup receipt. **14synthetic contracts passed; source unchanged; stop0/cleanup clear.** This is not recorder execution or app verification.

V2 addresses schema-specific cleanup, native child receipts, pinned baseline, safe paths, deduplication and explicit legacy limits. Two edges remain:

- `lane.identity()` hashes source before ancestry validation. Reject unsafe inventory paths before those reads, with a main-path regression.
- Current observed Gradle/Kotlin workers do not affect cleanup verdict. Task-owned or unclassified survivors must prevent a current cleanup PASS; unrelated processes must remain untouched.

These are residual reporting-control requests, not additional application defects or observed leaks/workers. Parent has been notified; no edits made by reviewer.

Apphost01schema is compatible with evaluation but its attempted XCTest did not complete the DS-C01 matrix. Preserve the raw `NOT_RUN` field with that explanation; do not report successful runtime verification. Final report/runtime approval remains pending.
