# Intermediate verification generator — independent source/data review

Reviewer: `/root/mafia_cont`. Frozen `assemble_verification.py`:195lines, SHA256 `293d0d50cbd02a248326f680109f31370efb8bd97d8a7db92848046657628f15`; completely read. This is an intermediate skeleton review, **not a review of executed final deliverables**.

Current receipts classify correctly as16direct Gradle cycles, one executed Android-harness cycle, one pre-Gradle Android attempt, and zero source-aware iOS app-host cycles at the snapshot time. Android's actual retained evidence and the independent validator's bound receipt hash agree. The new narrow Android PASS wording does not imply physical LAN, complete-game, Linux/x86_64 or Store-signing validation. Resource parity correctly states that no plural resources exercised that validator branch. No hardcoded16-cycle assertion remains.

29pure-data/extracted-AST assertions PASS. They cover existing categorization, both actual Android cleanup dispositions, missing final hygiene, failing/missing stops or cleanup fields, remaining output/worker/holder/device evidence, ambiguous prebuild phase, and Android runtime/validator statuses. No final generator, Gradle/native/device command, process scan or production code executed; no final report was rewritten. The frozen source, runnable checker and result JSON are in `evidence/final-verification-skeleton-mafia-cont/`.

Two bounded audit-helper observations were sent to root:

1. An iOS app-host attempt that fails before any Gradle invocation would be conservatively blocked by the nonempty `gradle_stops` condition, even with otherwise complete cleanup. If such a cycle occurs, explicitly qualified `stop_not_required` evidence should be handled rather than suggesting a daemon-stop failure occurred. No such cycle existed at this review time.
2. The Android runtime gate currently checks command/runtime/independent-verdict fields, not the validator's bound current receipt hash. A synthetic change to `verification.instrumented_cases` while retaining those status fields still qualifies. Actual current evidence is unchanged and hash-matched; this is a recommended stale-evidence safeguard, **not a current Android defect or invalid actual runtime result**. Final independent reconciliation must bind the exact bytes either way.

The final repository-wide source/output/process checker, post-iOS disposition, final gate counts and generated Markdown/JSON agreement remain pending. Intermediate review does not qualify cleanup as PASS merely because the synthetic all-clean case passes.
