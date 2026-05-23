package com.parlor.content.validation

import com.parlor.content.schema.CaseEnvelope

/**
 * Opaque token that proves a [CaseEnvelope] has passed validation. Downstream
 * consumers (the session controller, the Whodunit reducer) accept only
 * `ValidatedCase` — there is no way to construct one without going through
 * [CaseValidator].
 *
 * The generic `TPayload` lets the per-module payload validator parse the
 * `payload: JsonElement` into a typed structure and return that here.
 */
class ValidatedCase<TPayload> internal constructor(
    val envelope: CaseEnvelope,
    val payload: TPayload,
)
