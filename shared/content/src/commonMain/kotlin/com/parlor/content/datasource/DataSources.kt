package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result

/**
 * Three source roles layered by the repository:
 * 1. Remote — optional network content; explicitly unavailable in this release.
 * 2. Cache — a local process-lifetime optimization.
 * 3. Bundled — the authoritative shipping content inside the app.
 *
 * Every source, including bundled resources, flows through the validator
 * before use. There is no validation bypass for trusted-looking data.
 */
interface RemoteCaseDataSource {
    suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError>
    suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError>
}

interface BundledFallbackCaseDataSource {
    /**
     * Suspending because the bundled JSON is held in Compose Multiplatform
     * resources (`Res.readBytes(...)` is a suspending API across all targets).
     */
    suspend fun availableCases(): List<CaseSummary>
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
