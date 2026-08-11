package com.parlor.content.validation

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.GameRegistry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Strict envelope validator per docs/CONTENT_SCHEMA.md §2.3. Checks fire in
 * the documented order; first failure aborts and returns a typed error.
 */
class DefaultCaseValidator(
    private val json: Json,
    private val knownSchemaVersion: Int,
    private val installedAppVersion: SemVer,
    private val gameRegistry: GameRegistry,
) : CaseValidator {

    private val summaryValidator = CaseSummaryValidator(gameRegistry, installedAppVersion)

    override fun validateSummaries(
        requestedGameId: GameId,
        summaries: List<CaseSummary>,
    ): Result<List<CaseSummary>, ValidationError> =
        summaryValidator.validate(requestedGameId, summaries)

    override fun <TPayload> validate(
        rawJson: String,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, ValidationError> {
        // 1. Parseable JSON.
        val envelope: CaseEnvelope = try {
            json.decodeFromString(CaseEnvelope.serializer(), rawJson)
        } catch (_: SerializationException) {
            return Result.Failure(ValidationError.MalformedJson)
        } catch (e: IllegalArgumentException) {
            return Result.Failure(ValidationError.MalformedField("envelope", e.message ?: "invalid"))
        }

        // 2. Schema version known.
        if (envelope.schemaVersion > knownSchemaVersion) {
            return Result.Failure(ValidationError.UnsupportedSchema(envelope.schemaVersion))
        }
        if (envelope.schemaVersion < 1) {
            return Result.Failure(ValidationError.MalformedField("schemaVersion", "must be >= 1"))
        }

        // The compatibility field exists in schema v1, but this release has no
        // configured signing key or signature-verification algorithm. Treating
        // a non-null value as trusted metadata would create a false integrity
        // boundary for a future remote source. Fail closed until a versioned,
        // key-pinned verifier is deliberately introduced.
        if (envelope.signature != null) {
            return Result.Failure(
                ValidationError.MalformedField(
                    path = "signature",
                    reason = "unsupported by this app version",
                ),
            )
        }

        // 3. Minimum app version.
        if (envelope.minimumAppVersion > installedAppVersion) {
            return Result.Failure(
                ValidationError.AppUpdateRequired(envelope.minimumAppVersion.toString()),
            )
        }

        // 4. GameId registered. The envelope is untrusted; a blank value must
        // become a typed validation failure instead of escaping GameId's
        // constructor as an IllegalArgumentException.
        val gameId = try {
            GameId(envelope.gameId)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(ValidationError.MalformedField("gameId", "blank"))
        }
        if (gameRegistry.byId(gameId) == null) {
            return Result.Failure(ValidationError.UnknownGame(envelope.gameId))
        }
        if (envelope.gameId != payloadValidator.gameId) {
            return Result.Failure(ValidationError.UnknownGame(envelope.gameId))
        }

        // 5. The full common envelope projection must satisfy exactly the same
        // shape, bounds, installed-game, and compatibility rules as an item
        // received from the list endpoint. This prevents direct fetches from
        // bypassing safe identifier and display-field limits.
        when (val summary = summaryValidator.validate(gameId, listOf(envelope.toSummary()))) {
            is Result.Success -> Unit
            is Result.Failure -> return Result.Failure(summary.error)
        }

        // 6. Payload validation.
        return when (val payload = payloadValidator.validate(envelope)) {
            is Result.Success -> Result.Success(ValidatedCase(envelope, payload.data))
            is Result.Failure -> Result.Failure(payload.error)
        }
    }
}

private fun CaseEnvelope.toSummary() = CaseSummary(
    caseId = caseId,
    title = title,
    subtitle = subtitle,
    version = version,
    gameId = gameId,
    supportedPlayerCounts = supportedPlayerCounts,
    supportedModes = supportedModes,
    language = language,
    theme = theme,
    estimatedDuration = estimatedDuration,
    minimumAppVersion = minimumAppVersion,
)
