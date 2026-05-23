package com.parlor.games.whodunit.content

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.CaseEnvelope
import com.parlor.core.ids.CaseId
import kotlinx.serialization.json.Json

/**
 * The bundled-fallback data source for Whodunit. Reads JSON content from a
 * map supplied at construction time — each platform (`androidMain` /
 * `iosMain` / `desktopMain`) populates the map by loading from its native
 * resource path.
 *
 * Per ARCHITECTURE.md §8: the bundled case is *not* an inline data class; the
 * JSON flows through the same validator as any remote case.
 */
class BundledWhodunitCases(
    private val rawJsonByCaseId: Map<String, String>,
    private val json: Json,
) : BundledFallbackCaseDataSource {

    private val envelopes: Map<String, CaseEnvelope> by lazy {
        rawJsonByCaseId.mapValues { (_, raw) ->
            json.decodeFromString(CaseEnvelope.serializer(), raw)
        }
    }

    override fun availableCases(): List<CaseSummary> = envelopes.values.map { env ->
        CaseSummary(
            caseId = env.caseId,
            title = env.title,
            subtitle = env.subtitle,
            version = env.version,
            gameId = env.gameId,
            supportedPlayerCounts = env.supportedPlayerCounts,
            supportedModes = env.supportedModes,
            language = env.language,
            theme = env.theme,
            estimatedDuration = env.estimatedDuration,
            minimumAppVersion = env.minimumAppVersion,
            coverArtUrl = null,
        )
    }

    override suspend fun loadBundled(id: CaseId): CaseEnvelope? = envelopes[id.raw]
}
