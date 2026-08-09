package com.parlor.content.repository

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.datasource.CachedCaseDataSource
import com.parlor.content.datasource.RemoteCaseDataSource
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.IntRangePair
import com.parlor.content.validation.CaseValidator
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultCaseRepositoryTest {
    private val json = Json { encodeDefaults = true }
    private val gameId = GameId("whodunit")
    private val caseId = CaseId("last-dinner")
    private val summary = CaseSummary(
        caseId = caseId.raw,
        title = "The Last Dinner",
        version = SemVer(1, 0, 0),
        gameId = gameId.raw,
        supportedPlayerCounts = IntRangePair(4, 6),
        supportedModes = listOf("classic-vote"),
        language = "en",
        theme = "country-manor",
        estimatedDuration = IntRangePair(25, 35),
        minimumAppVersion = SemVer(1, 0, 0),
    )
    private val envelope = CaseEnvelope(
        schemaVersion = 1,
        caseId = caseId.raw,
        title = summary.title,
        version = summary.version,
        minimumAppVersion = summary.minimumAppVersion,
        gameId = gameId.raw,
        supportedPlayerCounts = summary.supportedPlayerCounts,
        supportedModes = summary.supportedModes,
        language = summary.language,
        theme = summary.theme,
        estimatedDuration = summary.estimatedDuration,
        payload = JsonObject(emptyMap()),
    )

    @Test
    fun invalid_remote_case_is_not_cached_before_typed_validation() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache, RejectingValidator)

        val result = repository.loadCase(caseId, UnitPayloadValidator)

        assertIs<Result.Failure<DataError>>(result)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun refresh_does_not_cache_a_failed_envelope_shape() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache, RejectingValidator)

        val result = repository.refresh(gameId)

        assertEquals(Result.Failure(DataError.CorruptedData), result)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun valid_remote_case_is_cached_only_after_validation() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache, AcceptingValidator())

        val result = repository.loadCase(caseId, UnitPayloadValidator)

        assertIs<Result.Success<ValidatedCase<Unit>>>(result)
        assertEquals(1, cache.putCount)
        assertEquals(envelope, cache.value)
    }

    private fun repository(cache: RecordingCache, validator: CaseValidator) = DefaultCaseRepository(
        remote = StubRemote(),
        cache = cache,
        bundled = EmptyBundled,
        validator = validator,
        json = json,
    )

    private inner class StubRemote : RemoteCaseDataSource {
        override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> =
            Result.Success(listOf(summary))

        override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> =
            Result.Success(envelope)
    }

    private object EmptyBundled : BundledFallbackCaseDataSource {
        override suspend fun availableCases(): List<CaseSummary> = emptyList()

        override suspend fun loadBundled(id: CaseId): CaseEnvelope? = null
    }

    private class RecordingCache : CachedCaseDataSource {
        var putCount: Int = 0
        var value: CaseEnvelope? = null

        override suspend fun get(id: CaseId): CaseEnvelope? = value

        override suspend fun put(envelope: CaseEnvelope) {
            putCount += 1
            value = envelope
        }

        override suspend fun invalidate(id: CaseId) = Unit

        override suspend fun listSummaries(gameId: GameId): List<CaseSummary> = emptyList()
    }

    private object UnitPayloadValidator : PayloadValidator<Unit> {
        override val gameId: String = "whodunit"

        override fun validate(envelope: CaseEnvelope): Result<Unit, ValidationError> =
            Result.Success(Unit)
    }

    private inner class AcceptingValidator : CaseValidator {
        override fun validateSummaries(
            requestedGameId: GameId,
            summaries: List<CaseSummary>,
        ): Result<List<CaseSummary>, ValidationError> = Result.Success(summaries)

        override fun <TPayload> validate(
            rawJson: String,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<ValidatedCase<TPayload>, ValidationError> {
            val decoded = json.decodeFromString(CaseEnvelope.serializer(), rawJson)
            return when (val payload = payloadValidator.validate(decoded)) {
                is Result.Success -> Result.Success(ValidatedCase(decoded, payload.data))
                is Result.Failure -> payload
            }
        }
    }

    private object RejectingValidator : CaseValidator {
        override fun validateSummaries(
            requestedGameId: GameId,
            summaries: List<CaseSummary>,
        ): Result<List<CaseSummary>, ValidationError> = Result.Success(summaries)

        override fun <TPayload> validate(
            rawJson: String,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<ValidatedCase<TPayload>, ValidationError> =
            Result.Failure(ValidationError.PayloadInvalid("rejected test payload"))
    }
}
