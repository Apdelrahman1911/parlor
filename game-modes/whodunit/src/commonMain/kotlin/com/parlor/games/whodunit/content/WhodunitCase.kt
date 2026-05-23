package com.parlor.games.whodunit.content

import kotlinx.serialization.Serializable

/**
 * Whodunit's payload schema (the `payload` field inside the CaseEnvelope).
 * Mirrors docs/CONTENT_SCHEMA.md §3.
 */
@Serializable
data class WhodunitCase(
    val publicIntro: String,
    val bedrockClues: List<String>,
    val characters: List<Character>,
    val cluePools: CluePools,
    val revealNarratives: Map<String, String>,
    val roundConfigByPlayerCount: Map<String, RoundConfig> = emptyMap(),
    val metadata: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class Character(
    val id: String,
    val displayName: String,
    val relationshipToVictim: String,
    val publicIdentity: String,
    val publicMotive: String,
    val privateSecret: String,
    val innocentBrief: InnocentBrief,
    val guiltyBrief: GuiltyBrief,
    val optionalDetails: OptionalDetails? = null,
)

@Serializable
data class InnocentBrief(
    val verdictLine: String,
    val alibi: String,
    val goal: String,
    val canSayFreely: String,
    val mustHide: String,
)

@Serializable
data class GuiltyBrief(
    val verdictLine: String,
    val method: String,
    val timeline: List<TimelineEntry>,
    val fakeAlibi: String,
    val deflectionTargets: List<String>,
    val panicMove: String,
    val actingTips: String? = null,
)

@Serializable
data class TimelineEntry(val time: String, val action: String)

@Serializable
data class OptionalDetails(
    val backstory: String? = null,
    val actingTips: String? = null,
    val emotionalMotivation: String? = null,
    val suggestedBehavior: String? = null,
    val extraNightDetails: String? = null,
)

@Serializable
data class CluePools(
    val publicUniversal: List<Clue>,
    val killerPointing: Map<String, List<Clue>>,
    val redHerring: Map<String, List<Clue>>,
    val contradiction: Map<String, List<Clue>>,
    val finalStrong: Map<String, List<Clue>>,
)

@Serializable
data class Clue(
    val id: String,
    val text: String,
    val appliesToModes: List<String>? = null,
    val tags: List<String>? = null,
)

@Serializable
data class RoundConfig(
    val rounds: List<Round>,
)

@Serializable
data class Round(
    val id: String,
    val titleCardText: String,
    val taglineText: String,
    val cluesToReveal: Int,
    val structuredAction: StructuredAction,
    val discussionSeconds: Int,
)

@Serializable
enum class StructuredAction {
    ALIBI_ROUND_ROBIN,
    DIRECTED_QUESTIONS,
    SILENT_ACCUSATION,
    MONOLOGUES,
    NONE,
}
