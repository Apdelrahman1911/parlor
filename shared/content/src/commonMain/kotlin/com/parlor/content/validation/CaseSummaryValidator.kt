package com.parlor.content.validation

import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.GameRegistry

/**
 * Validates the untrusted, reduced payload returned by the case-list
 * endpoint. A summary is not a trusted cache key or UI model merely because
 * it decoded successfully: it still has to agree with the requested game and
 * the installed definition before it can reach a picker or a fetch URL.
 */
class CaseSummaryValidator(
    private val gameRegistry: GameRegistry,
    private val installedAppVersion: SemVer,
) {
    fun validate(
        requestedGameId: GameId,
        summaries: List<CaseSummary>,
    ): Result<List<CaseSummary>, ValidationError> {
        if (summaries.size > MAX_SUMMARIES) {
            return Result.Failure(
                ValidationError.MalformedField("summaries", "too many entries"),
            )
        }
        val definition = gameRegistry.byId(requestedGameId)
            ?: return Result.Failure(ValidationError.UnknownGame(requestedGameId.raw))
        val seenIds = mutableSetOf<String>()
        summaries.forEach { summary ->
            val failure = validateOne(requestedGameId, definition, summary, seenIds)
            if (failure != null) return Result.Failure(failure)
        }
        return Result.Success(summaries)
    }

    companion object {
        const val MAX_SUMMARIES: Int = 128
        private const val MAX_CASE_ID_LENGTH: Int = 128
        private const val MAX_LANGUAGE_LENGTH: Int = 35
        private const val MAX_THEME_LENGTH: Int = 64
        private const val MAX_COVER_ART_URL_LENGTH: Int = 2_048
        private const val MAX_DURATION_MINUTES: Int = 24 * 60
        private val SAFE_CASE_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val BCP_47 = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")

        /** Shape-only fallback for custom CaseValidator implementations. */
        fun validateShape(
            requestedGameId: GameId,
            summaries: List<CaseSummary>,
        ): Result<List<CaseSummary>, ValidationError> {
            if (summaries.size > MAX_SUMMARIES) {
                return Result.Failure(
                    ValidationError.MalformedField("summaries", "too many entries"),
                )
            }
            val seenIds = mutableSetOf<String>()
            summaries.forEach { summary ->
                val failure = validateShapeOne(requestedGameId, summary, seenIds)
                if (failure != null) return Result.Failure(failure)
            }
            return Result.Success(summaries)
        }

        private fun validateShapeOne(
            requestedGameId: GameId,
            summary: CaseSummary,
            seenIds: MutableSet<String>,
        ): ValidationError? {
            if (summary.gameId != requestedGameId.raw) {
                return ValidationError.UnknownGame(summary.gameId)
            }
            return validateCommon(summary, seenIds)
        }

        private fun validateCommon(
            summary: CaseSummary,
            seenIds: MutableSet<String>,
        ): ValidationError? {
            if (
                summary.caseId.length > MAX_CASE_ID_LENGTH ||
                !SAFE_CASE_ID.matches(summary.caseId)
            ) {
                return ValidationError.MalformedField("caseId", "invalid identifier")
            }
            if (!seenIds.add(summary.caseId)) {
                return ValidationError.MalformedField("caseId", "duplicate identifier")
            }
            if (summary.title.isBlank() || summary.title.length > 80) {
                return ValidationError.MalformedField("title", "must be 1..80 characters")
            }
            if (summary.subtitle?.length ?: 0 > 120) {
                return ValidationError.MalformedField("subtitle", "must be <= 120 characters")
            }
            if (
                summary.language.length > MAX_LANGUAGE_LENGTH ||
                !BCP_47.matches(summary.language)
            ) {
                return ValidationError.MalformedField("language", "invalid BCP-47 tag")
            }
            if (summary.theme.isBlank() || summary.theme.length > MAX_THEME_LENGTH) {
                return ValidationError.MalformedField("theme", "must be 1..64 characters")
            }
            if (
                summary.supportedPlayerCounts.min < 3 ||
                summary.supportedPlayerCounts.max > 16
            ) {
                return ValidationError.PlayerCountOutOfRange(
                    summary.supportedPlayerCounts.toIntRange(),
                    3..16,
                )
            }
            if (
                summary.estimatedDuration.min < 1 ||
                summary.estimatedDuration.max > MAX_DURATION_MINUTES
            ) {
                return ValidationError.MalformedField(
                    "estimatedDuration",
                    "must be 1..$MAX_DURATION_MINUTES minutes",
                )
            }
            if (summary.coverArtUrl?.length ?: 0 > MAX_COVER_ART_URL_LENGTH) {
                return ValidationError.MalformedField("coverArtUrl", "too long")
            }
            if (summary.supportedModes.isEmpty()) {
                return ValidationError.MalformedField("supportedModes", "empty")
            }
            if (summary.supportedModes.distinct().size != summary.supportedModes.size) {
                return ValidationError.MalformedField("supportedModes", "duplicate mode")
            }
            return null
        }
    }

    private fun validateOne(
        requestedGameId: GameId,
        definition: com.parlor.engine.definition.GameDefinition<*, *, *>,
        summary: CaseSummary,
        seenIds: MutableSet<String>,
    ): ValidationError? {
        validateShapeOne(requestedGameId, summary, seenIds)?.let { return it }
        val supportedCounts = summary.supportedPlayerCounts.toIntRange()
        if (
            supportedCounts.first < definition.supportedPlayerCounts.first ||
            supportedCounts.last > definition.supportedPlayerCounts.last
        ) {
            return ValidationError.PlayerCountOutOfRange(
                supportedCounts,
                definition.supportedPlayerCounts,
            )
        }
        val declaredModes = definition.supportedModes.map { it.id.raw }.toSet()
        summary.supportedModes.firstOrNull { it !in declaredModes }?.let { mode ->
            return ValidationError.UnknownMode(mode)
        }
        if (summary.minimumAppVersion > installedAppVersion) {
            return ValidationError.AppUpdateRequired(summary.minimumAppVersion.toString())
        }
        return null
    }
}
