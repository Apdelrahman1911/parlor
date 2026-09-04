package com.parlor.content.validation

import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.IntRangePair
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CaseSummaryValidatorTest {
    private val valid = CaseSummary(
        caseId = "last-dinner",
        title = "The Last Dinner",
        subtitle = "A mystery",
        version = SemVer(1, 0, 0),
        gameId = "whodunit",
        supportedPlayerCounts = IntRangePair(4, 6),
        supportedModes = listOf("classic-vote"),
        language = "en",
        theme = "country-manor",
        estimatedDuration = IntRangePair(25, 35),
        minimumAppVersion = SemVer(1, 0, 0),
    )

    @Test
    fun shape_validation_rejects_path_traversal_and_duplicate_ids() {
        val path = valid.copy(caseId = "../secrets")
        assertIs<Result.Failure<ValidationError>>(
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(path)),
        ).also { failure ->
            assertEquals(
                ValidationError.MalformedField("caseId", "invalid identifier"),
                failure.error,
            )
        }

        assertIs<Result.Failure<ValidationError>>(
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(valid, valid.copy(caseId = valid.caseId))),
        ).also { failure ->
            assertEquals(
                ValidationError.MalformedField("caseId", "duplicate identifier"),
                failure.error,
            )
        }
    }

    @Test
    fun shape_validation_rejects_wrong_game_and_oversized_display_data() {
        val wrongGame = valid.copy(gameId = "mafia")
        val oversizedTitle = valid.copy(title = "x".repeat(81))

        assertEquals(
            Result.Failure(ValidationError.UnknownGame("mafia")),
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(wrongGame)),
        )
        assertEquals(
            Result.Failure(ValidationError.MalformedField("title", "must be 1..80 characters")),
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(oversizedTitle)),
        )
    }

    @Test
    fun shape_validation_rejects_control_and_format_characters_in_display_data() {
        val unsafeFields = listOf(
            valid.copy(title = "The Last\nDinner") to "title",
            valid.copy(title = "Mystery\uD83C") to "title",
            valid.copy(subtitle = "A \u202Emystery") to "subtitle",
            valid.copy(theme = "country\u200Bmanor") to "theme",
        )

        unsafeFields.forEach { (summary, field) ->
            assertIs<Result.Failure<ValidationError>>(
                CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(summary)),
            ).also { failure ->
                assertEquals(field, (failure.error as ValidationError.MalformedField).path)
            }
        }

        assertIs<Result.Success<List<CaseSummary>>>(
            CaseSummaryValidator.validateShape(
                GameId("whodunit"),
                listOf(valid.copy(title = "Mystery 🎭")),
            ),
        )
    }
}
