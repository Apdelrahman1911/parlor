package com.parlor.games.whodunit.content

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The bundled-fallback data source for Whodunit. Reads case JSON from Compose
 * Multiplatform's shared resources (`composeResources/files/cases/<id>.json`)
 * — one source of truth, no per-platform duplication. The reader lambda is
 * injected so this class stays unit-testable.
 *
 * A bundled case is not an inline data class: its JSON flows through the same
 * strict envelope and game-specific validator as any future remote case.
 */
class BundledWhodunitCases(
    /** Case ids whose JSON files exist under `composeResources/files/cases/`. */
    private val knownCaseIds: List<String>,
    /**
     * Loads the raw JSON for a case id. Typically wired to
     * `Res.readBytes("files/cases/$id.json").decodeToString()`. Returns null
     * if the resource is missing.
     */
    private val loadJson: suspend (caseId: String) -> String?,
    private val json: Json,
) : BundledFallbackCaseDataSource {

    init {
        require(knownCaseIds.size == knownCaseIds.toSet().size) {
            "Bundled Whodunit case ids must be unique"
        }
    }

    private val mutex = Mutex()
    private var envelopes: Map<String, CaseEnvelope>? = null

    override suspend fun availableCases(): List<CaseSummary> =
        loadEnvelopesOnce().values.map { it.toSummary() }

    override suspend fun loadBundled(id: CaseId): CaseEnvelope? =
        loadEnvelopesOnce()[id.raw]

    private suspend fun loadEnvelopesOnce(): Map<String, CaseEnvelope> = mutex.withLock {
        envelopes?.let { return@withLock it }
        val loaded = mutableMapOf<String, CaseEnvelope>()
        for (id in knownCaseIds) {
            val raw = requireNotNull(loadJson(id)) {
                "Bundled Whodunit case '$id' is declared but its resource is missing"
            }
            // A malformed bundle is a build bug. Let the deserialization
            // exception propagate so it's caught at first access (in tests or
            // at app startup), not silently hidden behind a "no cases" UI.
            val envelope = json.decodeFromString(CaseEnvelope.serializer(), raw)
            require(envelope.caseId == id) {
                "Bundled case '$id' declares mismatched caseId '${envelope.caseId}'"
            }
            loaded[id] = envelope
        }
        envelopes = loaded
        loaded
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
