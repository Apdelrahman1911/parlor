# DS-C02 — independent test-dispatcher follow-up

**APPROVED.** Reviewer `/root/native_fix_review`; author `/root`. Final `ParlorToastLifetimeTest.kt` SHA-256: `bac61913c5d79ace76748320f0b073669ced0bd0c12f3063c90be7bbec142adc`. This is test-only validation follow-up, not another application defect or a new production correction.

Reopened the complete lifetime, concurrency, state and production-host files, original DS-C02 candidate/independent validation, and prior additional-test approvals. Reversing only the dispatcher/import/opt-in edits reconstructs the entire previously approved hash `4bec7f140fa576210c1abc9c33decf1bac5b6ca24112d15d69dee2d0ad702cc9`: assertions, automatic expiry, frame steps, scope ownership and finally-cancel remain unchanged.

## Why the race witness is preserved

Fresh official coroutines **1.11.0 common and JVM** source confirms both dispatcher implementations return `isDispatchNeeded=false`; the registered StateFlow subscriber resumes through the same core unconfined continuation/event-loop code. `CoroutineStart.UNDISPATCHED` still registers the first-empty watcher before advancing time. The watcher has no delay, timeout, yield or frame operation: it consumes the host's automatic empty emission and immediately calls public `show`, without an intervening test-clock tick. CMP **1.10.3** scheduler/render-loop source establishes that the current event and unconfined callbacks finish before the next scheduled rendered frame. Its separate test scheduler never needs advancing for this watcher.

The official dispatcher docs do **not** guarantee arbitrary coroutine ordering. Approval is limited to this exact no-delay one-shot path; adding producer delays/yields would require rechecking scheduler sharing. The original broken-production negative run remains separate; it was not rerun with the new dispatcher. This approval relies on inspected dispatch-path equivalence plus fresh execution, not an invented negative result.

## Fresh execution and cleanup

`test-type-aware-green-02` freshly executed all **3/3 lifetime cases**, zero skips/failures/errors, plus design-system type-aware Detekt with **0 findings**. Each replacement case asserts production expiry occurred, replacement text is actually queued and displayed, then replacement independently expires. No manual dismissal, mocked visibility or removed assertion can hide the original defect. The unchanged owner-swap case also passes.

The same cycle's **9 Whodunit tests** pass (separately reviewed by factory reviewer); `typeAwareStaticAnalysis` aggregate passes, with some other child analyses cached. Source manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7` is stable before/after and includes current reviewed hashes. Prior `whodunit-type-aware-01` remains FAIL; its InjectDispatcher diagnostic is not rewritten as success. Root owns the later no-cache combined gate.

The terminal receipt records Gradle stop0 and no remaining owned outputs/workers or cleanup errors. Reviewer ran no builds/tests/processes and made no application edits. Only compact new evidence and necessary public source excerpts were retained; downloads were bounded in memory, not left as archives. No physical Android/iOS or Store inference.

Companion JSON contains full source/read-range/hash ledger, exact diff, official URLs/access times and XML/log/cleanup receipts.
