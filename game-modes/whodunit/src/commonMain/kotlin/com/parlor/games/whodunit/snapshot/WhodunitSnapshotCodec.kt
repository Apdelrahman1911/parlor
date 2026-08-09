package com.parlor.games.whodunit.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * kotlinx.serialization-based codec for [WhodunitState]. The codec is module-
 * local so adding fields to the state only versions the Whodunit snapshot
 * format, not the engine's.
 */
class WhodunitSnapshotCodec(
    private val json: Json,
) : SnapshotCodec<WhodunitState> {

    override fun encode(state: WhodunitState): ByteArray {
        WhodunitStateValidator.requireValid(state)
        return json.encodeToString(WhodunitState.serializer(), state)
            .encodeToByteArray()
            .also(::requireValidPayloadSize)
    }

    override fun decode(payload: ByteArray): WhodunitState {
        requireValidPayloadSize(payload)
        val root = json.parseToJsonElement(payload.decodeToString()).jsonObject
        val generationWasPersisted = root["public"]
            ?.jsonObject
            ?.containsKey("roleAssignmentGeneration") == true
        val decoded = json.decodeFromJsonElement(WhodunitState.serializer(), root)
        val normalized = decoded
            .normalizeLegacyUntimedRevote()
            .normalizeLegacyDeflectionTargets()
            .normalizeLegacyRoleAssignmentGeneration(generationWasPersisted)
        WhodunitStateValidator.requireValid(normalized)
        return normalized
    }

    private fun requireValidPayloadSize(payload: ByteArray) {
        require(payload.size <= MAX_SNAPSHOT_PAYLOAD_BYTES) {
            "Whodunit snapshot exceeds $MAX_SNAPSHOT_PAYLOAD_BYTES bytes"
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_PAYLOAD_BYTES = 256 * 1024
        const val LEGACY_MAX_TIE_DEBATE_SECONDS = 60
    }

    private fun WhodunitState.normalizeLegacyUntimedRevote(): WhodunitState {
        val tied = public.voteState as? VoteState.Tied ?: return this
        require(tied.debateSecondsRemaining in 0..LEGACY_MAX_TIE_DEBATE_SECONDS) {
            "Invalid legacy tie debate duration"
        }
        if (tied.debateSecondsRemaining == 0) return this
        return copy(public = public.copy(voteState = tied.copy(debateSecondsRemaining = 0)))
    }

    /**
     * v1 snapshots predate the private filtered target field. The host-only
     * list is already authoritative and validated as assigned, so reconstruct
     * the killer's private slice before applying current invariants.
     */
    private fun WhodunitState.normalizeLegacyDeflectionTargets(): WhodunitState {
        val killerId = hostOnly.killerId
        val killerPrivate = privatePerPlayer[killerId] ?: return this
        if (
            killerPrivate.deflectionTargets.isNotEmpty() ||
            hostOnly.redHerringTargets.isEmpty()
        ) {
            return this
        }
        return copy(
            privatePerPlayer = privatePerPlayer + (
                killerId to killerPrivate.copy(
                    deflectionTargets = hostOnly.redHerringTargets,
                )
            ),
        )
    }

    /**
     * Snapshots written before reveal commands carried an assignment epoch
     * have no generation field. A complete assigned snapshot can be migrated
     * deterministically to the first generation; unassigned setup/cancelled
     * states remain at the reserved zero value.
     */
    private fun WhodunitState.normalizeLegacyRoleAssignmentGeneration(
        generationWasPersisted: Boolean,
    ): WhodunitState {
        if (
            generationWasPersisted ||
            public.roleAssignmentGeneration != 0L ||
            privatePerPlayer.isEmpty() ||
            hostOnly.seatToCharacter.isEmpty()
        ) {
            return this
        }
        return copy(public = public.copy(roleAssignmentGeneration = 1L))
    }
}
