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
 * Orchestrates cache + remote + bundled per ARCHITECTURE.md §8.3:
 *  - Serve a previously validated cache entry when available.
 *  - Otherwise try Remote → put in Cache → return.
 *  - Treat corrupt or unavailable sources independently and fall back to the
 *    validated bundled copy.
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
        // Cache → Remote → Bundled. A corrupt legacy cache entry is not a
        // terminal result: remove it and continue to the authoritative
        // sources so one bad record cannot permanently hide valid content.
        cache.get(id)?.let { cached ->
            when (val validated = validate(id, cached, payloadValidator)) {
                is Result.Success -> return validated
                is Result.Failure -> cache.invalidate(id)
            }
        }

        val remoteResult = remote.fetchCase(id).mapError { it.asDataError() }
        var remoteValidationFailure: DataError? = null
        if (remoteResult is Result.Success) {
            when (val validated = validate(id, remoteResult.data, payloadValidator)) {
                is Result.Success -> {
                    // A remote envelope is not cacheable until its game-owned
                    // payload has passed the same validator used by callers.
                    cache.put(validated.data.envelope)
                    cacheUpdates.tryEmit(CaseUpdate.CaseAdded(validated.data.envelope.toSummary()))
                    return validated
                }
                is Result.Failure -> remoteValidationFailure = validated.error
            }
        }

        bundled.loadBundled(id)?.let { return validate(id, it, payloadValidator) }
        return Result.Failure(remoteValidationFailure ?: DataError.NotFound)
    }

    override fun observeCacheUpdates(): Flow<CaseUpdate> = cacheUpdates.asSharedFlow()

    override suspend fun <TPayload> refresh(
        gameId: GameId,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<Unit, DataError> {
        require(payloadValidator.gameId == gameId.raw) {
            "Payload validator '${payloadValidator.gameId}' cannot refresh '${gameId.raw}'"
        }
        val r = remote.listCases(gameId).mapError { it.asDataError() }
        return when (r) {
            is Result.Success -> {
                val summaries = when (val validated = validator.validateSummaries(gameId, r.data)) {
                    is Result.Success -> validated.data
                    is Result.Failure -> return Result.Failure(DataError.CorruptedData)
                }
                // Pull each case through its game-owned validator before
                // caching. A partial refresh may retain earlier valid cases,
                // but its result remains a failure if any advertised case was
                // unavailable or invalid.
                var failure: DataError? = null
                for (summary in summaries) {
                    when (val warmed = warmCase(summary, payloadValidator)) {
                        is Result.Success -> Unit
                        is Result.Failure -> {
                            if (failure == null || warmed.error == DataError.CorruptedData) {
                                failure = warmed.error
                            }
                        }
                    }
                }
                failure?.let { Result.Failure(it) } ?: Result.Success(Unit)
            }
            is Result.Failure -> Result.Failure(r.error)
        }
    }

    private suspend fun <TPayload> warmCase(
        summary: CaseSummary,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<Unit, DataError> {
        val fetch = remote.fetchCase(CaseId(summary.caseId)).mapError { it.asDataError() }
        val envelope = when (fetch) {
            is Result.Success -> fetch.data
            is Result.Failure -> return fetch
        }
        return when (
            val validated = validate(
                expectedId = CaseId(summary.caseId),
                envelope = envelope,
                payloadValidator = payloadValidator,
            )
        ) {
            is Result.Failure -> validated
            is Result.Success -> {
                val envelope = validated.data.envelope
                if (envelope.toSummary() != summary.copy(coverArtUrl = null)) {
                    return Result.Failure(DataError.CorruptedData)
                }
                cache.put(envelope)
                cacheUpdates.tryEmit(CaseUpdate.CaseRevised(summary))
                Result.Success(Unit)
            }
        }
    }

    private fun <TPayload> validate(
        expectedId: CaseId,
        envelope: CaseEnvelope,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, DataError> {
        if (envelope.caseId != expectedId.raw) {
            return Result.Failure(DataError.CorruptedData)
        }
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
