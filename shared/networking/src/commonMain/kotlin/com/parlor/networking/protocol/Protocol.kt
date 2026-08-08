package com.parlor.networking.protocol

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import kotlinx.serialization.Serializable

/**
 * Transport-independent, host-authoritative room protocol.
 *
 * The transport authenticates the connection identity and overwrites every
 * peer-authored [actor] field before exposing a message to the host. The host
 * is the only state mutator. Peers submit commands and receive atomic
 * player-specific snapshots.
 */
@Serializable
sealed interface RoomMessage

const val PARLOR_PROTOCOL_MAJOR: Int = 2
const val PARLOR_PROTOCOL_MINOR: Int = 0
const val MAX_COMMAND_PAYLOAD_BYTES: Int = 32 * 1024
const val MAX_SNAPSHOT_PAYLOAD_BYTES: Int = 256 * 1024
const val MAX_CONTROL_PAYLOAD_BYTES: Int = 8 * 1024
const val MAX_ROOM_FRAME_BYTES: Int = 272 * 1024

@Serializable
data class ProtocolVersion(
    val major: Int = PARLOR_PROTOCOL_MAJOR,
    val minor: Int = PARLOR_PROTOCOL_MINOR,
) {
    /**
     * Parlor currently uses a strict schema. Minor versions are therefore
     * compatible only when they are equal; claiming major-only compatibility
     * would let a newer optional field fail an older strict decoder.
     */
    fun isCompatibleWith(other: ProtocolVersion): Boolean = this == other
}

@Serializable
data class SessionEnvelopeHeader(
    val protocol: ProtocolVersion,
    val sessionId: SessionId,
    val gameId: GameId,
    val gameVersion: Int,
    val messageId: String,
    /** Host message sequence. Peer messages use zero; commands have clientSequence. */
    val sequence: Long,
    /** Rejects delayed frames from a replaced physical connection. */
    val connectionEpoch: Long = 1L,
)

@Serializable
enum class AdmissionRejection {
    WrongCode,
    HostDeclined,
    RoomFull,
    SessionStarted,
    IncompatibleProtocol,
    InvalidRequest,
    RateLimited,
}

@Serializable
enum class CommandStatus {
    Applied,
    Duplicate,
    InvalidAction,
    Unauthorized,
    StaleRevision,
    SequenceGap,
    IncompatibleVersion,
    PayloadTooLarge,
    SessionEnded,
    UnknownCommand,
}

@Serializable
enum class SessionEndReason {
    Completed,
    HostLeft,
    HostBackgrounded,
    RejoinExpired,
    IncompatibleVersion,
    Cancelled,
}

@Serializable
sealed interface HostMessage : RoomMessage {
    /**
     * Transport admission completed after the room code was checked and the
     * host explicitly approved the authenticated peer.
     *
     * [rejoinToken] is a 256-bit opaque secret. It is sent only inside P2pKit's
     * authenticated encrypted channel and must never be logged or advertised.
     */
    @Serializable
    data class AdmissionAccepted(
        val playerId: PlayerId,
        val rejoinToken: String,
    ) : HostMessage

    @Serializable
    data class AdmissionRejected(val reason: AdmissionRejection) : HostMessage

    /** Atomic public + own-private view at one authoritative revision. */
    @Serializable
    data class PlayerSnapshot(
        val header: SessionEnvelopeHeader,
        val revision: Long,
        val publicPayload: ByteArray,
        val privatePayload: ByteArray,
    ) : HostMessage

    /** Idempotent response for a peer command. */
    @Serializable
    data class CommandResult(
        val header: SessionEnvelopeHeader,
        val commandId: String,
        val status: CommandStatus,
        val authoritativeRevision: Long,
        /** The next client sequence the host will accept for this actor. */
        val nextExpectedClientSequence: Long = 1L,
    ) : HostMessage

    @Serializable
    data class Heartbeat(
        val header: SessionEnvelopeHeader,
        val authoritativeRevision: Long,
    ) : HostMessage

    @Serializable
    data class PublicStateSnapshot(val payload: ByteArray) : HostMessage
    @Serializable
    data class PublicStateDelta(val patch: ByteArray) : HostMessage
    @Serializable
    data class PrivateStateForPlayer(val target: PlayerId, val payload: ByteArray) : HostMessage
    @Serializable
    data class EventBroadcast(val payload: ByteArray) : HostMessage
    @Serializable
    data class EventDirect(val target: PlayerId, val payload: ByteArray) : HostMessage
    @Serializable
    data class TimerSync(val timerId: String, val deadlineEpochMs: Long) : HostMessage
    @Serializable
    data object EndSession : HostMessage

    @Serializable
    data class SessionEnded(
        val header: SessionEnvelopeHeader,
        val reason: SessionEndReason,
        val finalRevision: Long,
    ) : HostMessage

    /**
     * Sent by the host once their lobby's *Start Game* is tapped. Tells every
     * peer "stop showing the lobby; load this case and switch to the game
     * flow." Carries the canonical session shape so peers can stand up their
     * shadow controller with the same `SessionConfig` the host uses.
     *
     * [sessionNonce] is a **public, non-secret** id nonce (derived from the room
     * code) used only for peer-side SessionId naming / debug. It is NOT the
     * role-assignment seed: the host runs the reducer authoritatively and peers
     * never reduce, so the role seed stays host-only. Shipping the real seed
     * would let any peer recompute the killer/role assignment offline via the
     * public, deterministic reducer — see PROBLEMS_PARLOR.md → NN-01.
     */
    @Serializable
    data class SessionStarting(
        val caseId: String,
        val modeId: String,
        val players: List<Player>,
        val sessionNonce: Long,
        val header: SessionEnvelopeHeader? = null,
    ) : HostMessage
}

@Serializable
sealed interface PeerMessage : RoomMessage {
    /**
     * First encrypted application frame on a newly connected transport.
     * [actor] is overwritten by the transport with the authenticated peer id.
     */
    @Serializable
    data class AdmissionRequest(
        val protocol: ProtocolVersion,
        val actor: PlayerId,
        val roomCode: String,
        val displayName: String,
        val rejoinToken: String? = null,
    ) : PeerMessage

    /**
     * A command against an exact host revision. The transport overwrites
     * [actor], so payload contents can never impersonate another seat.
     */
    @Serializable
    data class ClientCommand(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val commandId: String,
        val clientSequence: Long,
        val expectedRevision: Long,
        val payload: ByteArray,
    ) : PeerMessage

    @Serializable
    data class SnapshotRequest(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val lastAppliedRevision: Long,
    ) : PeerMessage

    @Serializable
    data class SessionHeartbeat(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val lastAppliedRevision: Long,
    ) : PeerMessage

    /** Queries an idempotency-ledger outcome without replaying the command. */
    @Serializable
    data class CommandOutcomeRequest(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val commandId: String,
    ) : PeerMessage

    @Serializable
    @Deprecated("Use AdmissionRequest; retained for protocol-0 compatibility only.")
    data class JoinRequest(val displayName: String) : PeerMessage
    /**
     * Peer-submitted action.
     *
     * [sender] is **transport-authenticated**: the receiving transport
     * overwrites it with the identity bound to the delivering session
     * (`P2pKitRoomTransport` stamps the session's peer id; `InMemoryPeerRoom`
     * stamps its own `selfPlayerId`) BEFORE the host bridge sees it. A peer
     * therefore cannot forge another player's action by lying in this field —
     * the value the host's `ActionAuthority.isAllowed` reads is always the real
     * owner of the connection. (Whatever a peer writes here on send is ignored;
     * the field is retained only as the carrier for the authenticated id.)
     * See PROBLEMS_PARLOR.md → wu-ui-01 / NN-03.
     */
    @Serializable
    data class ActionSubmit(val sender: PlayerId, val payload: ByteArray) : PeerMessage
    @Serializable
    data object Heartbeat : PeerMessage
    @Serializable
    data object LeaveNotice : PeerMessage
}
