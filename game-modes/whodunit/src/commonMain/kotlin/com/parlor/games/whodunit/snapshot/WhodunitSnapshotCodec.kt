package com.parlor.games.whodunit.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * kotlinx.serialization-based codec for [WhodunitState]. The payload format is
 * game-owned, so adding fields to this state does not silently redefine the
 * engine's generic snapshot envelope.
 *
 * Decoding enforces the state's structural invariants only. A codec does not
 * have the loaded case payload needed to bind case ids, character references,
 * or clue history, so callers must run
 * [WhodunitStateValidator.requireValidForCase] before a restored state reaches
 * a reducer.
 */
class WhodunitSnapshotCodec(
    json: Json,
) : SnapshotCodec<WhodunitState> {

    /**
     * Snapshot decoding is the structural persistence trust boundary. A
     * caller's permissive application Json configuration must not weaken it.
     * Case-bound validation is a separate gate once the expected case has been
     * loaded; see [WhodunitStateValidator.requireValidForCase].
     */
    private val strictJson = Json(json) {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    override fun encode(state: WhodunitState): ByteArray {
        WhodunitStateValidator.requireValid(state)
        val stateObject = strictJson
            .encodeToJsonElement(WhodunitState.serializer(), state)
            .jsonObject
        val payload = WhodunitSnapshotPayload(
            kind = WHODUNIT_SNAPSHOT_KIND,
            schemaVersion = WHODUNIT_SNAPSHOT_SCHEMA_VERSION,
            state = stateObject,
        )
        return strictJson.encodeToString(WhodunitSnapshotPayload.serializer(), payload)
            .encodeToByteArray()
            .also(::requireValidPayloadSize)
    }

    override fun decode(payload: ByteArray): WhodunitState {
        requireValidPayloadSize(payload)
        val root = strictJson
            .parseToJsonElement(payload.decodeToString(throwOnInvalidSequence = true))
            .jsonObject
        return if (root.keys.any(RESERVED_WRAPPER_KEYS::contains)) {
            decodeVersioned(root)
        } else {
            decodeLegacyBare(root)
        }
    }

    private fun decodeVersioned(root: JsonObject): WhodunitState {
        val envelope = strictJson.decodeFromJsonElement(
            WhodunitSnapshotPayload.serializer(),
            root,
        )
        require(envelope.kind == WHODUNIT_SNAPSHOT_KIND) {
            "Unsupported Whodunit snapshot kind"
        }
        require(envelope.schemaVersion > 0) {
            "Whodunit snapshot schema version must be positive"
        }

        return when (envelope.schemaVersion) {
            1 -> decodeCurrentState(envelope.state)
            else -> throw IllegalArgumentException(
                "Unsupported Whodunit snapshot schema version",
            )
        }
    }

    /**
     * Current schemas are canonical and receive no compatibility repair. This
     * prevents a malformed current payload from being silently reinterpreted
     * as an older shape merely because a field has a Kotlin default.
     */
    private fun decodeCurrentState(root: JsonObject): WhodunitState {
        val decoded = strictJson.decodeFromJsonElement(WhodunitState.serializer(), root)
        val canonical = strictJson
            .encodeToJsonElement(WhodunitState.serializer(), decoded)
            .jsonObject
        require(root == canonical) { "Whodunit snapshot state is not canonical" }
        WhodunitStateValidator.requireValid(decoded)
        return decoded
    }

    /**
     * Compatibility for snapshots emitted before the module-owned envelope.
     * Field presence is inspected before deserialization so only absent legacy
     * fields are reconstructed; explicit invalid values remain invalid.
     */
    private fun decodeLegacyBare(root: JsonObject): WhodunitState {
        val generationWasPersisted = root["public"]
            ?.jsonObject
            ?.containsKey("roleAssignmentGeneration") == true
        val decoded = strictJson.decodeFromJsonElement(WhodunitState.serializer(), root)
        val killerDeflectionTargetsWerePersisted = root["privatePerPlayer"]
            ?.jsonObject
            ?.get(decoded.hostOnly.killerId.raw)
            ?.jsonObject
            ?.containsKey("deflectionTargets") == true
        val normalized = decoded
            .normalizeLegacyUntimedRevote()
            .normalizeLegacyDeflectionTargets(killerDeflectionTargetsWerePersisted)
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
        val RESERVED_WRAPPER_KEYS = setOf("kind", "schemaVersion", "state")
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
    private fun WhodunitState.normalizeLegacyDeflectionTargets(
        killerTargetsWerePersisted: Boolean,
    ): WhodunitState {
        val killerId = hostOnly.killerId
        val killerPrivate = privatePerPlayer[killerId] ?: return this
        if (
            killerTargetsWerePersisted ||
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
