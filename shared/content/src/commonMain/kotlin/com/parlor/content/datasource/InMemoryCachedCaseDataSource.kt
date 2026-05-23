package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MVP cache implementation — in-memory, lost on process restart. Phase 6
 * upgrades this to disk-backed (SQLDelight or platform key-value) without
 * changing the call sites.
 */
class InMemoryCachedCaseDataSource : CachedCaseDataSource {
    private val mutex = Mutex()
    private val byId: MutableMap<CaseId, CaseEnvelope> = mutableMapOf()

    override suspend fun get(id: CaseId): CaseEnvelope? = mutex.withLock { byId[id] }

    override suspend fun put(envelope: CaseEnvelope) = mutex.withLock {
        byId[CaseId(envelope.caseId)] = envelope
    }

    override suspend fun invalidate(id: CaseId) = mutex.withLock {
        byId.remove(id)
        Unit
    }

    override suspend fun listSummaries(gameId: GameId): List<CaseSummary> = mutex.withLock {
        byId.values
            .filter { it.gameId == gameId.raw }
            .map { it.toSummary() }
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
