package com.parlor.content.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.IntRangePair
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
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

    @Test
    fun direct_envelope_validation_enforces_the_same_safe_shape_as_case_summaries() {
        val definition = RoundRobinAnnounceGame()
        val valid = CaseEnvelope(
            schemaVersion = 1,
            caseId = "round-robin-case",
            title = "Round Robin",
            version = SemVer(1, 0, 0),
            minimumAppVersion = SemVer(1, 0, 0),
            gameId = definition.id.raw,
            supportedPlayerCounts = IntRangePair(3, 3),
            supportedModes = listOf("round-robin"),
            language = "en",
            theme = "test",
            estimatedDuration = IntRangePair(1, 1),
            payload = JsonObject(emptyMap()),
        )
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(definition)),
        )
        val payloadValidator = object : PayloadValidator<Unit> {
            override val gameId: String = definition.id.raw
            override fun validate(envelope: CaseEnvelope) = Result.Success(Unit)
        }

        assertThat(
            validator.validate(
                json.encodeToString(CaseEnvelope.serializer(), valid.copy(caseId = "../escape")),
                payloadValidator,
            ),
        ).isEqualTo(
            Result.Failure(ValidationError.MalformedField("caseId", "invalid identifier")),
        )
        assertThat(
            validator.validate(
                json.encodeToString(CaseEnvelope.serializer(), valid.copy(title = "x".repeat(81))),
                payloadValidator,
            ),
        ).isEqualTo(
            Result.Failure(
                ValidationError.MalformedField("title", "must be 1..80 characters"),
            ),
        )
    }

    @Test
    fun unverified_delivery_signature_is_rejected_instead_of_being_treated_as_trusted() {
        val definition = RoundRobinAnnounceGame()
        val envelope = CaseEnvelope(
            schemaVersion = 1,
            caseId = "signed-case",
            title = "Signed Case",
            version = SemVer(1, 0, 0),
            minimumAppVersion = SemVer(1, 0, 0),
            gameId = definition.id.raw,
            supportedPlayerCounts = IntRangePair(3, 3),
            supportedModes = listOf("round-robin"),
            language = "en",
            theme = "test",
            estimatedDuration = IntRangePair(1, 1),
            payload = JsonObject(emptyMap()),
            signature = "not-a-verified-signature",
        )
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(definition)),
        )
        val payloadValidator = object : PayloadValidator<Unit> {
            override val gameId: String = definition.id.raw
            override fun validate(envelope: CaseEnvelope) = Result.Success(Unit)
        }

        assertThat(
            validator.validate(
                json.encodeToString(CaseEnvelope.serializer(), envelope),
                payloadValidator,
            ),
        ).isEqualTo(
            Result.Failure(
                ValidationError.MalformedField(
                    path = "signature",
                    reason = "unsupported by this app version",
                ),
            ),
        )
    }
}
