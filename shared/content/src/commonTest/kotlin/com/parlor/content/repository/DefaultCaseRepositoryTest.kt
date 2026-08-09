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

        val result = repository.refresh(gameId, UnitPayloadValidator)

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

    @Test
    fun corrupt_cached_case_is_invalidated_before_remote_fallback() = runTest {
        val cache = RecordingCache(initial = envelope.copy(title = ""))
        val repository = repository(cache, AcceptingValidator())

        val result = repository.loadCase(caseId, UnitPayloadValidator)

        assertIs<Result.Success<ValidatedCase<Unit>>>(result)
        assertEquals(1, cache.invalidateCount)
        assertEquals(1, cache.putCount)
        assertEquals(envelope, cache.value)
    }

    @Test
    fun remote_case_identity_must_match_the_requested_case_before_caching() = runTest {
        val cache = RecordingCache()
        val wrongEnvelope = envelope.copy(caseId = "different-case")
        val repository = repository(
            cache = cache,
            remote = StubRemote(fetchResult = Result.Success(wrongEnvelope)),
        )

        val result = repository.loadCase(caseId, UnitPayloadValidator)

        assertEquals(Result.Failure(DataError.CorruptedData), result)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun corrupt_remote_case_falls_back_to_a_valid_bundled_copy() = runTest {
        val cache = RecordingCache()
        val remoteEnvelope = envelope.copy(version = SemVer(2, 0, 0))
        val repository = repository(
            cache = cache,
            remote = StubRemote(fetchResult = Result.Success(remoteEnvelope)),
            bundled = FixedBundled(envelope),
        )

        val result = repository.loadCase(caseId, VersionOnePayloadValidator)

        val loaded = assertIs<Result.Success<ValidatedCase<Unit>>>(result)
        assertEquals(envelope, loaded.data.envelope)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun refresh_rejects_game_invalid_payload_before_cache_warming() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache = cache)

        val result = repository.refresh(gameId, RejectingPayloadValidator)

        assertEquals(Result.Failure(DataError.CorruptedData), result)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun refresh_rejects_a_fetched_case_that_does_not_match_its_manifest_identity() = runTest {
        val cache = RecordingCache()
        val repository = repository(
            cache = cache,
            remote = StubRemote(
                fetchResult = Result.Success(envelope.copy(caseId = "different-case")),
            ),
        )

        val result = repository.refresh(gameId, UnitPayloadValidator)

        assertEquals(Result.Failure(DataError.CorruptedData), result)
        assertEquals(0, cache.putCount)
    }

    @Test
    fun refresh_reports_an_advertised_case_that_cannot_be_fetched() = runTest {
        val cache = RecordingCache()
        val repository = repository(
            cache = cache,
            remote = StubRemote(fetchResult = Result.Failure(NetworkError.Unreachable)),
        )

        val result = repository.refresh(gameId, UnitPayloadValidator)

        assertEquals(Result.Failure(DataError.IoError("network")), result)
        assertEquals(0, cache.putCount)
    }

    private fun repository(
        cache: RecordingCache,
        validator: CaseValidator = AcceptingValidator(),
        remote: RemoteCaseDataSource = StubRemote(),
        bundled: BundledFallbackCaseDataSource = EmptyBundled,
    ) = DefaultCaseRepository(
        remote = remote,
        cache = cache,
        bundled = bundled,
        validator = validator,
        json = json,
    )

    private inner class StubRemote(
        private val listResult: Result<List<CaseSummary>, NetworkError> = Result.Success(listOf(summary)),
        private val fetchResult: Result<CaseEnvelope, NetworkError> = Result.Success(envelope),
    ) : RemoteCaseDataSource {
        override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> =
            listResult

        override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> =
            fetchResult
    }

    private object EmptyBundled : BundledFallbackCaseDataSource {
        override suspend fun availableCases(): List<CaseSummary> = emptyList()

        override suspend fun loadBundled(id: CaseId): CaseEnvelope? = null
    }

    private class FixedBundled(
        private val envelope: CaseEnvelope,
    ) : BundledFallbackCaseDataSource {
        override suspend fun availableCases(): List<CaseSummary> = emptyList()

        override suspend fun loadBundled(id: CaseId): CaseEnvelope = envelope
    }

    private class RecordingCache(initial: CaseEnvelope? = null) : CachedCaseDataSource {
        var putCount: Int = 0
        var invalidateCount: Int = 0
        var value: CaseEnvelope? = initial

        override suspend fun get(id: CaseId): CaseEnvelope? = value

        override suspend fun put(envelope: CaseEnvelope) {
            putCount += 1
            value = envelope
        }

        override suspend fun invalidate(id: CaseId) {
            invalidateCount += 1
            value = null
        }

        override suspend fun listSummaries(gameId: GameId): List<CaseSummary> = emptyList()
    }

    private object UnitPayloadValidator : PayloadValidator<Unit> {
        override val gameId: String = "whodunit"

        override fun validate(envelope: CaseEnvelope): Result<Unit, ValidationError> =
            Result.Success(Unit)
    }

    private object VersionOnePayloadValidator : PayloadValidator<Unit> {
        override val gameId: String = "whodunit"

        override fun validate(envelope: CaseEnvelope): Result<Unit, ValidationError> =
            if (envelope.version == SemVer(1, 0, 0)) {
                Result.Success(Unit)
            } else {
                Result.Failure(ValidationError.PayloadInvalid("unsupported fixture version"))
            }
    }

    private object RejectingPayloadValidator : PayloadValidator<Unit> {
        override val gameId: String = "whodunit"

        override fun validate(envelope: CaseEnvelope): Result<Unit, ValidationError> =
            Result.Failure(ValidationError.PayloadInvalid("rejected test payload"))
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
            if (decoded.title.isBlank()) {
                return Result.Failure(ValidationError.MalformedField("title", "blank"))
            }
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
