package com.parlor.games.whodunit.content

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.validation.PayloadValidator
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.games.whodunit.WhodunitIds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Validates the Whodunit-specific payload per docs/CONTENT_SCHEMA.md §3.5.
 *
 * Rules in order:
 * 10. character count within [3, 8] and ≤ envelope.supportedPlayerCounts.max
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

    override fun validate(envelope: CaseEnvelope): Result<WhodunitCase, ValidationError> {
        val case: WhodunitCase = try {
            json.decodeFromJsonElement(WhodunitCase.serializer(), envelope.payload)
        } catch (e: SerializationException) {
            return Result.Failure(ValidationError.PayloadInvalid(e.message ?: "decode"))
        }

        // 10. Character count.
        if (case.characters.size !in 3..8) {
            return Result.Failure(ValidationError.PayloadInvalid("character count out of [3,8]"))
        }
        if (case.characters.size < envelope.supportedPlayerCounts.max) {
            return Result.Failure(
                ValidationError.PayloadInvalid(
                    "supportedPlayerCounts.max (${envelope.supportedPlayerCounts.max}) " +
                        "exceeds character pool (${case.characters.size})",
                ),
            )
        }

        // 11. Character ids unique, kebab-case.
        val ids = case.characters.map { it.id }
        if (ids.size != ids.toSet().size) {
            return Result.Failure(ValidationError.PayloadInvalid("duplicate character id"))
        }
        val kebab = Regex("[a-z0-9-]+")
        ids.firstOrNull { !kebab.matches(it) }?.let {
            return Result.Failure(ValidationError.PayloadInvalid("non-kebab character id '$it'"))
        }

        // 12. publicIntro + bedrockClues present.
        if (case.publicIntro.isBlank()) {
            return Result.Failure(ValidationError.PayloadInvalid("publicIntro blank"))
        }
        if (case.bedrockClues.isEmpty()) {
            return Result.Failure(ValidationError.PayloadInvalid("bedrockClues empty"))
        }

        // 13. Briefs fully populated (kotlinx.serialization enforces structural,
        // but check for non-blank.)
        case.characters.forEach { c ->
            listOf(
                c.innocentBrief.verdictLine,
                c.innocentBrief.alibi,
                c.innocentBrief.goal,
                c.innocentBrief.canSayFreely,
                c.innocentBrief.mustHide,
                c.guiltyBrief.verdictLine,
                c.guiltyBrief.method,
                c.guiltyBrief.fakeAlibi,
                c.guiltyBrief.panicMove,
            ).firstOrNull { it.isBlank() }?.let {
                return Result.Failure(
                    ValidationError.PayloadInvalid("blank brief field on '${c.id}'"),
                )
            }
            if (c.guiltyBrief.timeline.isEmpty()) {
                return Result.Failure(
                    ValidationError.PayloadInvalid("empty timeline on '${c.id}'"),
                )
            }
        }

        // 14. deflectionTargets valid + not self-reference.
        val idSet = ids.toSet()
        case.characters.forEach { c ->
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

        // 16. Clue pools.
        val poolError = validateCluePools(case, idSet, envelope.supportedModes.toSet())
        if (poolError != null) return Result.Failure(poolError)

        // 17. RoundConfig (if present).
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
            config.rounds.forEach { round ->
                if (round.cluesToReveal !in 0..5) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid(
                            "round '${round.id}' cluesToReveal out of [0,5]",
                        ),
                    )
                }
                if (round.discussionSeconds !in 0..600) {
                    return Result.Failure(
                        ValidationError.PayloadInvalid(
                            "round '${round.id}' discussionSeconds out of [0,600]",
                        ),
                    )
                }
            }
        }

        return Result.Success(case)
    }

    private fun validateCluePools(
        case: WhodunitCase,
        characterIds: Set<String>,
        declaredModes: Set<String>,
    ): ValidationError? {
        val pools = case.cluePools

        if (pools.publicUniversal.isEmpty() || pools.publicUniversal.size > 4) {
            return ValidationError.PayloadInvalid("publicUniversal must have 1..4 entries")
        }

        // Every character must have non-empty entries in killerPointing / contradiction / finalStrong.
        characterIds.forEach { id ->
            if ((pools.killerPointing[id]?.size ?: 0) < 3) {
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
        val seen = HashSet<String>(allClues.size)
        allClues.forEach { clue ->
            if (!seen.add(clue.id)) {
                return ValidationError.PayloadInvalid("duplicate clue id '${clue.id}'")
            }
            // appliesToModes (if present) must be a subset of declaredModes.
            clue.appliesToModes?.forEach { mode ->
                if (mode !in declaredModes) {
                    return ValidationError.PayloadInvalid(
                        "clue '${clue.id}' appliesToModes contains undeclared mode '$mode'",
                    )
                }
            }
        }

        return null
    }
}
