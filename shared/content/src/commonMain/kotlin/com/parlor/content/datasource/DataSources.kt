package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result

/**
 * Three sources of cases, layered by the repository:
 * 1. Remote — the (mock or real) backend.
 * 2. Cache — local persisted copy.
 * 3. Bundled — the offline-safety fallback shipped inside the app.
 *
 * Per ARCHITECTURE.md §8: even the bundled fallback flows through the
 * validator before use. There is no "trusted" data path.
 */
interface RemoteCaseDataSource {
    suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError>
    suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError>
}

interface BundledFallbackCaseDataSource {
    fun availableCases(): List<CaseSummary>
    suspend fun loadBundled(id: CaseId): CaseEnvelope?
}

interface CachedCaseDataSource {
    suspend fun get(id: CaseId): CaseEnvelope?
    suspend fun put(envelope: CaseEnvelope)
    suspend fun invalidate(id: CaseId)
    suspend fun listSummaries(gameId: GameId): List<CaseSummary>
}

/** Promotes a [NetworkError] into the broader [DataError] taxonomy. */
fun NetworkError.asDataError(): DataError = when (this) {
    NetworkError.Timeout,
    NetworkError.Unreachable -> DataError.IoError("network")
    is NetworkError.Server -> DataError.IoError("server $httpStatus")
    NetworkError.Unauthorized -> DataError.PermissionDenied
    is NetworkError.Serialization -> DataError.CorruptedData
    is NetworkError.Unknown -> DataError.Unknown(this.message)
}
