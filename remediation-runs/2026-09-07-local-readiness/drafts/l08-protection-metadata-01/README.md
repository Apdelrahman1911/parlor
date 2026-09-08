# L08 protection metadata — diagnostic-only draft 01

This is a frozen, task-owned proposal, not an adopted change or successful test.
Author: `/root/native_fix_review` (also original L08 author; not an independent reviewer).

## Trigger and preserved failure

Actual `ios-readiness-07` reached `metadata-directory-protection`, then failed the
original Complete-equality assertion. Its real save, same-process load, envelope
equality, path/existence and backup-exclusion checks precede that stage. The
original file protection assertion, encrypted-header check and later L08 steps
were not reached. Nil attributes, missing key and another protection class are
not distinguished by that original receipt. This is not yet an application-defect
or Simulator-limitation conclusion. Native05 is a separate broad-stage failure.
The failed native07 cycle did not complete the full exact artifact verifier.

## Scope and invariants

Only three copy-only verification files are proposed; production storage,
settings, rules, transport, signing, entitlements and the native runner are unchanged.
The same original getter is executed once with an initialized NSError pointer;
`value == protection` remains the required predicate, with directory before file.
No assertion is skipped, retried, repaired or converted to PASS. No extra volume
or sibling getter executes on the normal PASS path. The complete PASS schema and
verifier, bounded payloads, original stage labels and historical FAIL parsing remain.

On directory failure, the exact already-attested regular file is queried once as
`diagnostic-only`: its original assertion remains unexecuted. On file failure,
the preceding directory observation is `required-passed` and file is
`required-failed`. Both paths receive read-only volume capability observations.
Kotlin Exceptions in extra queries are explicit `observer-exception` unknowns;
cancellation propagates. Unavailable/unknown does not mean unsupported or PASS.

## Sanitization

The optional `protection_metadata` field is accepted only on the two corresponding
FAIL storage stages with `fixture_or_boundary_failure`. Closed enums distinguish
nil/missing/public protection classes/redacted other type, capability support,
and NSError presence/category/signed64 code. No raw NSError domain, description,
userInfo, path, payload, key, secret or arbitrary native value enters new metadata.
The parser rejects unknown keys, type coercions, contradictions, incorrect roles,
and any attempt to attach this failure diagnostic to PASS, host or cancellation.

## Verification status and next lane

No tests, compilation, Kotlin/Native tool, native probe or simulator was run by
the author for this draft. Source-contract/parser tests are proposed, not executed.
SDK evidence is in `sdk-evidence.json`; independent review and pinned Kotlin
platform declaration inspection belong to `/root/release_fix_review`.
Root must run isolated control tests, inspect/adopt only if appropriate, establish
a new exact source freeze, and use the existing fresh full app-host matrix.
A native failure remains a failure; even if future evidence establishes a genuine
Simulator limitation, the full L08 gate must not be labeled PASS on that basis.

## Ownership and cleanup

Only `files/` draft copies and these deliverables were created/edited here. No
canonical tracked source was changed; no build workers or outputs were created,
so no Gradle stop or cleanup command was run against root's exclusive active lane.
Keep this freeze intact; any changes after independent feedback need a new version.
