package com.parlor.content.validation

import com.parlor.content.schema.CaseEnvelope
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError

/**
 * Strict, ordered validator per docs/CONTENT_SCHEMA.md §2.3. Phase 3 wires
 * in the concrete envelope rules; this is the contract.
 *
 * Per-module payload validation is delegated to [PayloadValidator] — the
 * envelope validator calls it once envelope rules pass.
 */
interface CaseValidator {
    fun <TPayload> validate(
        rawJson: String,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, ValidationError>
}

/**
 * Module-supplied payload validator. Whodunit's implementation lives in
 * :game-modes:whodunit and parses `envelope.payload` into a `WhodunitCase`.
 */
interface PayloadValidator<TPayload> {
    /** The `gameId` this payload validator handles. */
    val gameId: String

    /**
     * Parse and validate the envelope's `payload` according to the module's
     * payload schema. Returns the typed payload on success, a `ValidationError`
     * on failure.
     */
    fun validate(envelope: CaseEnvelope): Result<TPayload, ValidationError>
}
