package com.parlor.games.whodunit.content

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.validation.PayloadValidator
import com.parlor.core.ids.ModeId
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Validates the Whodunit-specific payload per docs/CONTENT_SCHEMA.md §3.5.
 *
 * Rules in order:
 * 10. character count within [4, 8] and ≤ envelope.supportedPlayerCounts.max
 * 11. character ids unique, kebab-case
 * 12. publicIntro / bedrockClues non-empty
 * 13. every character has innocent + guilty briefs fully populated
 * 14. guiltyBrief.deflectionTargets references valid other-character ids
 * 15. revealNarratives has exactly one entry per character id
 * 16. cluePools structural rules per §3.3
 * 17. roundConfigByPlayerCount (if present) well-formed
 */
class WhodunitPayloadValidator(
    private val json: Json,
) : PayloadValidator<WhodunitCase> {

    override val gameId: String = WhodunitIds.GameId.raw

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Ordered schema contract; each independent validation returns a typed failure.
    override fun validate(envelope: CaseEnvelope): Result<WhodunitCase, ValidationError> {
        val case: WhodunitCase = try {
            json.decodeFromJsonElement(WhodunitCase.serializer(), envelope.payload)
        } catch (e: SerializationException) {
            return Result.Failure(ValidationError.PayloadInvalid(e.message ?: "decode"))
        }

        // 10. Character count.
        if (case.characters.size !in 4..8) {
            return Result.Failure(ValidationError.PayloadInvalid("character count out of [4,8]"))
        }
        if (case.characters.size < envelope.supportedPlayerCounts.max) {
            return Result.Failure(
                ValidationError.PayloadInvalid(
                    "supportedPlayerCounts.max (${envelope.supportedPlayerCounts.max}) " +
                        "exceeds character pool (${case.characters.size})",
                ),
            )
        }
        if (envelope.supportedModes.size != envelope.supportedModes.toSet().size) {
            return Result.Failure(ValidationError.PayloadInvalid("duplicate supported mode"))
        }
        if ((case.metadata?.toString()?.length ?: 0) > MAX_METADATA_LENGTH) {
            return Result.Failure(ValidationError.PayloadInvalid("payload metadata too large"))
        }

        // 11. Character ids unique, kebab-case.
        val ids = case.characters.map { it.id }
        if (ids.size != ids.toSet().size) {
            return Result.Failure(ValidationError.PayloadInvalid("duplicate character id"))
        }
        ids.firstOrNull { !isValidContentId(it) }?.let {
            return Result.Failure(ValidationError.PayloadInvalid("non-kebab character id '$it'"))
        }

        // 12. publicIntro + bedrockClues present.
        validateRequiredText("publicIntro", case.publicIntro, MAX_LONG_TEXT)?.let {
            return Result.Failure(it)
        }
        if (case.bedrockClues.size !in 1..MAX_BEDROCK_CLUES) {
            return Result.Failure(ValidationError.PayloadInvalid("bedrockClues count out of bounds"))
        }
        case.bedrockClues.forEachIndexed { index, clue ->
            validateRequiredText("bedrockClues[$index]", clue, MAX_CLUE_TEXT)?.let {
                return Result.Failure(it)
            }
        }

        // 13. Briefs fully populated and bounded. Content may be remote, so a
        // syntactically valid payload must not be able to create effectively
        // unbounded UI/state strings.
        case.characters.forEach { c ->
            val requiredFields = listOf(
                "displayName" to c.displayName,
                "relationshipToVictim" to c.relationshipToVictim,
                "publicIdentity" to c.publicIdentity,
                "publicMotive" to c.publicMotive,
                "privateSecret" to c.privateSecret,
                "innocent.verdictLine" to c.innocentBrief.verdictLine,
                "innocent.alibi" to c.innocentBrief.alibi,
                "innocent.goal" to c.innocentBrief.goal,
                "innocent.canSayFreely" to c.innocentBrief.canSayFreely,
                "innocent.mustHide" to c.innocentBrief.mustHide,
                "guilty.verdictLine" to c.guiltyBrief.verdictLine,
                "guilty.method" to c.guiltyBrief.method,
                "guilty.fakeAlibi" to c.guiltyBrief.fakeAlibi,
                "guilty.panicMove" to c.guiltyBrief.panicMove,
            )
            requiredFields.forEach { (field, value) ->
                validateRequiredText("character '${c.id}' $field", value, MAX_LONG_TEXT)?.let {
                    return Result.Failure(it)
                }
            }
            if (c.guiltyBrief.timeline.size !in 1..MAX_TIMELINE_ENTRIES) {
                return Result.Failure(
                    ValidationError.PayloadInvalid("timeline count out of bounds on '${c.id}'"),
                )
            }
            c.guiltyBrief.timeline.forEachIndexed { index, entry ->
                validateRequiredText(
                    "timeline[$index].time on '${c.id}'",
                    entry.time,
                    MAX_SHORT_TEXT,
                )?.let { return Result.Failure(it) }
                validateRequiredText(
                    "timeline[$index].action on '${c.id}'",
                    entry.action,
                    MAX_CLUE_TEXT,
                )?.let { return Result.Failure(it) }
            }
            validateOptionalText("guilty.actingTips on '${c.id}'", c.guiltyBrief.actingTips)?.let {
                return Result.Failure(it)
            }
            c.optionalDetails?.let { details ->
                listOf(
                    "backstory" to details.backstory,
                    "actingTips" to details.actingTips,
                    "emotionalMotivation" to details.emotionalMotivation,
                    "suggestedBehavior" to details.suggestedBehavior,
                    "extraNightDetails" to details.extraNightDetails,
                ).forEach { (field, value) ->
                    validateOptionalText("optional.$field on '${c.id}'", value)?.let {
                        return Result.Failure(it)
                    }
                }
            }
        }

        // 14. deflectionTargets valid + not self-reference.
        val idSet = ids.toSet()
        case.characters.forEach { c ->
            if (c.guiltyBrief.deflectionTargets.size != c.guiltyBrief.deflectionTargets.toSet().size) {
                return Result.Failure(
                    ValidationError.PayloadInvalid("duplicate deflection target on '${c.id}'"),
                )
            }
            c.guiltyBrief.deflectionTargets.forEach { target ->
                if (target == c.id) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid("self-deflection on '${c.id}'"),
                    )
                }
                if (target !in idSet) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid("unknown deflection target '$target'"),
                    )
                }
            }
        }

        // 15. revealNarratives — exactly one per character.
        if (case.revealNarratives.keys != idSet) {
            return Result.Failure(
                ValidationError.PayloadInvalid("revealNarratives keys must match character ids"),
            )
        }
        case.revealNarratives.forEach { (id, narrative) ->
            validateRequiredText("revealNarratives['$id']", narrative, MAX_LONG_TEXT)?.let {
                return Result.Failure(it)
            }
        }

        // 16. Clue pools.
        val poolError = validateCluePools(
            case = case,
            characterIds = idSet,
            declaredModes = envelope.supportedModes.toSet(),
            declaredPlayerCounts = envelope.supportedPlayerCounts.toIntRange(),
        )
        if (poolError != null) return Result.Failure(poolError)

        // 17. RoundConfig (if present).
        if (case.roundConfigByPlayerCount.size > MAX_ROUND_CONFIGS) {
            return Result.Failure(ValidationError.PayloadInvalid("too many RoundConfig entries"))
        }
        case.roundConfigByPlayerCount.forEach { (countKey, config) ->
            val count = countKey.toIntOrNull()
                ?: return Result.Failure(
                    ValidationError.PayloadInvalid("non-int RoundConfig key '$countKey'"),
                )
            if (count !in envelope.supportedPlayerCounts.toIntRange()) {
                return Result.Failure(
                    ValidationError.PayloadInvalid(
                        "RoundConfig for $count outside case's supportedPlayerCounts",
                    ),
                )
            }
            if (config.rounds.isEmpty() || config.rounds.size > MAX_ROUNDS) {
                return Result.Failure(
                    ValidationError.PayloadInvalid("RoundConfig for $count has invalid round count"),
                )
            }
            if (config.rounds.map { it.id }.toSet().size != config.rounds.size) {
                return Result.Failure(
                    ValidationError.PayloadInvalid("RoundConfig for $count has duplicate round ids"),
                )
            }
            config.rounds.forEach { round ->
                if (!isValidContentId(round.id)) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid("invalid round id '${round.id}'"),
                    )
                }
                validateRequiredText("round '${round.id}' title", round.titleCardText, MAX_CLUE_TEXT)?.let {
                    return Result.Failure(it)
                }
                validateRequiredText("round '${round.id}' tagline", round.taglineText, MAX_CLUE_TEXT)?.let {
                    return Result.Failure(it)
                }
                // Shipping reducer/UI support exactly one clue and no recorded
                // structured action. Reject declarations the engine would
                // otherwise silently ignore.
                if (round.cluesToReveal != 1) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid(
                            "round '${round.id}' cluesToReveal must be 1",
                        ),
                    )
                }
                if (round.structuredAction != StructuredAction.NONE) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid(
                            "round '${round.id}' structuredAction is unsupported",
                        ),
                    )
                }
                if (round.discussionSeconds !in
                    WhodunitRules.MIN_DISCUSSION_SECONDS..WhodunitRules.MAX_DISCUSSION_SECONDS
                ) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid(
                            "round '${round.id}' discussionSeconds out of " +
                                "[${WhodunitRules.MIN_DISCUSSION_SECONDS}," +
                                "${WhodunitRules.MAX_DISCUSSION_SECONDS}]",
                        ),
                    )
                }
            }
        }

        return Result.Success(case)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Ordered cross-pool/content-playability contract.
    private fun validateCluePools(
        case: WhodunitCase,
        characterIds: Set<String>,
        declaredModes: Set<String>,
        declaredPlayerCounts: IntRange,
    ): ValidationError? {
        val pools = case.cluePools

        val keyedPools = listOf(
            "killerPointing" to pools.killerPointing.keys,
            "redHerring" to pools.redHerring.keys,
            "contradiction" to pools.contradiction.keys,
            "finalStrong" to pools.finalStrong.keys,
        )
        keyedPools.forEach { (name, keys) ->
            val unknown = keys - characterIds
            if (unknown.isNotEmpty()) {
                return ValidationError.PayloadInvalid("$name contains unknown character keys")
            }
        }

        if (pools.publicUniversal.isEmpty() || pools.publicUniversal.size > MAX_PUBLIC_UNIVERSAL_CLUES) {
            return ValidationError.PayloadInvalid("publicUniversal must have 1..4 entries")
        }

        // Every character must have non-empty entries in killerPointing / contradiction / finalStrong.
        characterIds.forEach { id ->
            if ((pools.killerPointing[id]?.size ?: 0) < MIN_KILLER_POINTING_CLUES) {
                return ValidationError.PayloadInvalid(
                    "killerPointing['$id'] must have >= 3 clues",
                )
            }
            if (pools.contradiction[id].isNullOrEmpty()) {
                return ValidationError.PayloadInvalid("contradiction['$id'] empty")
            }
            if ((pools.finalStrong[id]?.size ?: 0) < 2) {
                return ValidationError.PayloadInvalid(
                    "finalStrong['$id'] must have >= 2 variants",
                )
            }
        }

        // Clue ids unique across all pools.
        val allClues = pools.publicUniversal +
            pools.killerPointing.values.flatten() +
            pools.redHerring.values.flatten() +
            pools.contradiction.values.flatten() +
            pools.finalStrong.values.flatten()
        if (allClues.size > MAX_TOTAL_CLUES) {
            return ValidationError.PayloadInvalid("total clue count exceeds $MAX_TOTAL_CLUES")
        }
        val seen = HashSet<String>(allClues.size)
        allClues.forEach { clue ->
            if (!isValidContentId(clue.id)) {
                return ValidationError.PayloadInvalid("invalid clue id '${clue.id}'")
            }
            if (!seen.add(clue.id)) {
                return ValidationError.PayloadInvalid("duplicate clue id '${clue.id}'")
            }
            validateRequiredText("clue '${clue.id}' text", clue.text, MAX_CLUE_TEXT)?.let {
                return it
            }
            // appliesToModes (if present) must be a subset of declaredModes.
            val applicableModes = clue.appliesToModes
            if (applicableModes != null) {
                val modes = applicableModes
                if (modes.isEmpty() || modes.size != modes.toSet().size) {
                    return ValidationError.PayloadInvalid(
                        "clue '${clue.id}' appliesToModes is empty or duplicated",
                    )
                }
                modes.forEach { mode ->
                    if (mode !in declaredModes) {
                        return ValidationError.PayloadInvalid(
                            "clue '${clue.id}' appliesToModes contains undeclared mode '$mode'",
                        )
                    }
                }
            }
            clue.tags?.let { tags ->
                if (tags.size > MAX_TAGS || tags.size != tags.toSet().size ||
                    tags.any { it.isBlank() || it.length > MAX_SHORT_TEXT }
                ) {
                    return ValidationError.PayloadInvalid("clue '${clue.id}' has invalid tags")
                }
            }
        }

        // Every declared mode must be playable for at least one declared
        // count, and every possible killer needs enough mode-eligible unique
        // evidence to reach that mode's finite terminal round.
        declaredModes.forEach { rawMode ->
            if (rawMode.isBlank()) {
                return ValidationError.PayloadInvalid("blank supported mode")
            }
            val modeId = ModeId(rawMode)
            val modeRange = WhodunitRules.supportedPlayerCounts(modeId)
                ?: return ValidationError.PayloadInvalid("unsupported mode '$rawMode'")
            val supportedCounts = modeRange.filter { count ->
                count in declaredPlayerCounts && count <= characterIds.size
            }
            if (supportedCounts.isEmpty()) {
                return ValidationError.PayloadInvalid(
                    "mode '$rawMode' has no supported player count in this case",
                )
            }
            val requiredEvidence = supportedCounts.maxOf { count ->
                WhodunitRules.maximumRoundCount(modeId, count) ?: 0
            }
            characterIds.forEach { characterId ->
                fun List<Clue>.eligibleForMode(): List<Clue> = filter { clue ->
                    clue.appliesToModes?.let { rawMode in it } != false
                }
                val eligible = (
                    pools.publicUniversal +
                        pools.killerPointing[characterId].orEmpty() +
                        pools.redHerring[characterId].orEmpty() +
                        pools.contradiction[characterId].orEmpty() +
                        pools.finalStrong[characterId].orEmpty()
                    ).eligibleForMode()
                if (eligible.size < requiredEvidence) {
                    return ValidationError.PayloadInvalid(
                        "character '$characterId' has fewer than $requiredEvidence clues for '$rawMode'",
                    )
                }
                val openingAndMiddlePool = (
                    pools.killerPointing[characterId].orEmpty() +
                        pools.redHerring[characterId].orEmpty() +
                        pools.contradiction[characterId].orEmpty()
                    ).eligibleForMode()
                // Round one may consume a killer-pointing clue. Reserve enough
                // additional non-final evidence for every middle round under
                // that worst-case deterministic draw.
                if (openingAndMiddlePool.size < requiredEvidence - 1) {
                    return ValidationError.PayloadInvalid(
                        "character '$characterId' can exhaust pre-final clues for '$rawMode'",
                    )
                }
                if (pools.finalStrong[characterId].orEmpty().none { clue ->
                        clue.appliesToModes?.let { rawMode in it } != false
                    }
                ) {
                    return ValidationError.PayloadInvalid(
                        "character '$characterId' has no finalStrong clue for '$rawMode'",
                    )
                }
            }
        }

        return null
    }

    private fun validateRequiredText(
        label: String,
        value: String,
        maximumLength: Int,
    ): ValidationError.PayloadInvalid? = when {
        value.isBlank() -> ValidationError.PayloadInvalid("$label blank")
        value.length > maximumLength -> ValidationError.PayloadInvalid("$label too long")
        else -> null
    }

    private fun validateOptionalText(
        label: String,
        value: String?,
    ): ValidationError.PayloadInvalid? = value?.let {
        validateRequiredText(label, it, MAX_LONG_TEXT)
    }

    private fun isValidContentId(value: String): Boolean =
        value.length <= MAX_CONTENT_ID_LENGTH && CONTENT_ID.matches(value)

    private companion object {
        // Lowercase alphanumeric segments separated by exactly one hyphen.
        // Length is checked separately so malformed separators cannot hide at
        // either edge of an otherwise bounded identifier.
        val CONTENT_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        const val MAX_CONTENT_ID_LENGTH = 64
        const val MAX_SHORT_TEXT = 128
        const val MAX_CLUE_TEXT = 2_048
        const val MAX_LONG_TEXT = 4_096
        const val MAX_BEDROCK_CLUES = 16
        const val MAX_TIMELINE_ENTRIES = 32
        const val MAX_TAGS = 16
        const val MAX_TOTAL_CLUES = 256
        const val MAX_ROUND_CONFIGS = 8
        const val MAX_ROUNDS = 8
        const val MAX_METADATA_LENGTH = 32 * 1024
        const val MAX_PUBLIC_UNIVERSAL_CLUES = 4
        const val MIN_KILLER_POINTING_CLUES = 3
    }
}
