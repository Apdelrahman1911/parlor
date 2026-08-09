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
        val definition = gameRegistry.byId(gameId)
        if (definition == null) {
            return Result.Failure(ValidationError.UnknownGame(envelope.gameId))
        }
        if (envelope.gameId != payloadValidator.gameId) {
            return Result.Failure(ValidationError.UnknownGame(envelope.gameId))
        }

        // 5. Required-field sanity (most are enforced by kotlinx.serialization).
        if (envelope.caseId.isBlank()) {
            return Result.Failure(ValidationError.MalformedField("caseId", "blank"))
        }
        if (envelope.title.isBlank()) {
            return Result.Failure(ValidationError.MalformedField("title", "blank"))
        }
        if (envelope.supportedModes.isEmpty()) {
            return Result.Failure(ValidationError.MalformedField("supportedModes", "empty"))
        }

        // 6. Player counts within engine absolute range.
        val playerCounts = envelope.supportedPlayerCounts.toIntRange()
        val engineRange = ENGINE_PLAYER_COUNT_RANGE
        if (playerCounts.first < engineRange.first || playerCounts.last > engineRange.last) {
            return Result.Failure(
                ValidationError.PlayerCountOutOfRange(supplied = playerCounts, allowed = engineRange),
            )
        }

        // 7. Player counts within the resolved game definition's range.
        if (playerCounts.first < definition.supportedPlayerCounts.first ||
            playerCounts.last > definition.supportedPlayerCounts.last
        ) {
            return Result.Failure(
                ValidationError.PlayerCountOutOfRange(
                    supplied = playerCounts,
                    allowed = definition.supportedPlayerCounts,
                ),
            )
        }

        // 8. Modes recognized.
        val declaredModeIds = definition.supportedModes.map { it.id.raw }.toSet()
        envelope.supportedModes.forEach { mode ->
            if (mode !in declaredModeIds) {
                return Result.Failure(ValidationError.UnknownMode(mode))
            }
        }

        // 9. Payload validation.
        return when (val payload = payloadValidator.validate(envelope)) {
            is Result.Success -> Result.Success(ValidatedCase(envelope, payload.data))
            is Result.Failure -> Result.Failure(payload.error)
        }
    }

    companion object {
        /** Engine's absolute player-count range — generous, modules narrow further. */
        val ENGINE_PLAYER_COUNT_RANGE: IntRange = 3..16
    }
}
