package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result

/**
 * Explicit production policy for builds that ship bundled content only.
 *
 * This is intentionally not a fake HTTP server. It reports the remote source
 * as unavailable, allowing [com.parlor.content.repository.DefaultCaseRepository]
 * to exercise its normal cache/bundled fallback and validation path without
 * packaging a test transport or claiming that content can be refreshed.
 *
 * Replacing this binding with [KtorRemoteCaseDataSource] is a deliberate
 * release decision that also requires a real HTTPS endpoint, trust policy,
 * observability, and compatibility tests.
 */
class OfflineRemoteCaseDataSource : RemoteCaseDataSource {
    override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> =
        Result.Failure(NetworkError.Unreachable)

    override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> =
        Result.Failure(NetworkError.Unreachable)
}
