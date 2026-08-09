package com.parlor.content.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.IntRangePair
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

class DefaultCaseValidatorBoundaryTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun blank_untrusted_game_id_is_a_typed_failure_not_an_exception() {
        val envelope = CaseEnvelope(
            schemaVersion = 1,
            caseId = "case",
            title = "Case",
            version = SemVer(1, 0, 0),
            minimumAppVersion = SemVer(1, 0, 0),
            gameId = "",
            supportedPlayerCounts = IntRangePair(3, 3),
            supportedModes = listOf("mode"),
            language = "en",
            theme = "test",
            estimatedDuration = IntRangePair(1, 1),
            payload = JsonObject(emptyMap()),
        )
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(emptyList()),
        )
        val payloadValidator = object : PayloadValidator<Unit> {
            override val gameId: String = "test"
            override fun validate(envelope: CaseEnvelope) = Result.Success(Unit)
        }

        assertThat(
            validator.validate(json.encodeToString(CaseEnvelope.serializer(), envelope), payloadValidator),
        ).isEqualTo(Result.Failure(ValidationError.MalformedField("gameId", "blank")))
    }
}
