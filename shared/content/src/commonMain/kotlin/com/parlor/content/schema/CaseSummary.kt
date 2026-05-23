package com.parlor.content.schema

import com.parlor.core.versioning.SemVer
import kotlinx.serialization.Serializable

/**
 * Strict subset of [CaseEnvelope] for library tiles — enough to render the
 * grid without downloading the full case content.
 */
@Serializable
data class CaseSummary(
    val caseId: String,
    val title: String,
    val subtitle: String? = null,
    val version: SemVer,
    val gameId: String,
    val supportedPlayerCounts: IntRangePair,
    val supportedModes: List<String>,
    val language: String,
    val theme: String,
    val estimatedDuration: IntRangePair,
    val minimumAppVersion: SemVer,
    val coverArtUrl: String? = null,
)
