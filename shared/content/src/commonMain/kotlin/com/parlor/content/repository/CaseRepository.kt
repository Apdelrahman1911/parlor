package com.parlor.content.repository

import com.parlor.content.schema.CaseSummary
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Facade over the content delivery pipeline. Hides cache + remote + bundled
 * coordination behind one interface (per ARCHITECTURE.md §8.3).
 */
interface CaseRepository {
    suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError>
    suspend fun <TPayload> loadCase(
        id: CaseId,
        payloadValidator: PayloadValidator<TPayload>,
    ): Result<ValidatedCase<TPayload>, DataError>
    fun observeCacheUpdates(): Flow<CaseUpdate>
    suspend fun refresh(gameId: GameId): Result<Unit, DataError>
}

sealed interface CaseUpdate {
    data class CaseAdded(val summary: CaseSummary) : CaseUpdate
    data class CaseRevised(val summary: CaseSummary) : CaseUpdate
    data class CaseRemoved(val caseId: CaseId) : CaseUpdate
}
