package com.parlor.content.repository

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.datasource.CachedCaseDataSource
import com.parlor.content.datasource.RemoteCaseDataSource
import com.parlor.content.datasource.asDataError
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.content.validation.CaseValidator
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.result.flatMap
import com.parlor.core.result.mapError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

/**
 * Orchestrates remote + cache + bundled per ARCHITECTURE.md §8.3:
 *  - First open of a case: try Remote → put in Cache → return.
 *  - Failure: fall back to Cache; failure again: fall back to Bundled.
 *  - Subsequent opens: serve from Cache; refresh in background.
 *
 * Every path runs the result through the validator before exposing it. There
 * is no untrusted data path.
 */
class DefaultCaseRepository(
    private val remote: RemoteCaseDataSource,
    private val cache: CachedCaseDataSource,
    private val bundled: BundledFallbackCaseDataSource,
    private val validator: CaseValidator,
    private val json: Json,
) : CaseRepository {

    private val cacheUpdates = MutableSharedFlow<CaseUpdate>(extraBufferCapacity = 16)

    override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError> {
        // Prefer remote so the library reflects the latest catalog; fall back to
        // cache, then to bundled.
        val remoteResult = remote.listCases(gameId).mapError { it.asDataError() }
        if (remoteResult is Result.Success) {
            when (val validated = validator.validateSummaries(gameId, remoteResult.data)) {
                is Result.Success -> return validated
                is Result.Failure -> Unit // Treat a corrupt manifest as a source failure.
            }
        }

        val cached = cache.listSummaries(gameId)
        if (cached.isNotEmpty()) {
            when (val validated = validator.validateSummaries(gameId, cached)) {
                is Result.Success -> return validated
                is Result.Failure -> Unit
            }
        }

        val bundledSummaries = bundled.availableCases().filter { it.gameId == gameId.raw }
        return validator.validateSummaries(gameId, bundledSummaries)
            .mapError { DataError.CorruptedData }
    }

    override suspend fun <TPayload> loadCase(
        id: CaseId,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, DataError> {
        // Cache → Remote → Bundled.
        cache.get(id)?.let { return validate(it, payloadValidator) }

        val remoteResult = remote.fetchCase(id).mapError { it.asDataError() }
        if (remoteResult is Result.Success) {
            cache.put(remoteResult.data)
            cacheUpdates.tryEmit(CaseUpdate.CaseAdded(remoteResult.data.toSummary()))
            return validate(remoteResult.data, payloadValidator)
        }

        bundled.loadBundled(id)?.let { return validate(it, payloadValidator) }
        return Result.Failure(DataError.NotFound)
    }

    override fun observeCacheUpdates(): Flow<CaseUpdate> = cacheUpdates.asSharedFlow()

    override suspend fun refresh(gameId: GameId): Result<Unit, DataError> {
        val r = remote.listCases(gameId).mapError { it.asDataError() }
        return when (r) {
            is Result.Success -> {
                val summaries = when (val validated = validator.validateSummaries(gameId, r.data)) {
                    is Result.Success -> validated.data
                    is Result.Failure -> return Result.Failure(DataError.CorruptedData)
                }
                // Pull each case into cache to warm it.
                summaries.forEach { summary ->
                    val caseId = CaseId(summary.caseId)
                    val fetch = remote.fetchCase(caseId).mapError { it.asDataError() }
                    if (fetch is Result.Success) {
                        cache.put(fetch.data)
                        cacheUpdates.tryEmit(CaseUpdate.CaseRevised(summary))
                    }
                }
                Result.Success(Unit)
            }
            is Result.Failure -> r.mapError { it } as Result<Unit, DataError>
        }
    }

    private fun <TPayload> validate(
        envelope: CaseEnvelope,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, DataError> {
        val raw = json.encodeToString(CaseEnvelope.serializer(), envelope)
        return validator.validate(raw, payloadValidator).flatMap { Result.Success(it) }
            .mapError { DataError.CorruptedData }
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
    coverArtUrl = null,
)
