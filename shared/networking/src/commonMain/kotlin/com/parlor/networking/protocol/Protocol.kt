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

// v4.0 replaced the one-shot game-start notification with an acknowledged,
// idempotent offer -> ready -> commit -> commit-ack barrier. A v3 peer could
// enter gameplay after seeing an unacknowledged SessionStarting frame and lose
// the first authoritative snapshot. v4.1 makes host display identity explicit
// and adds an actionable duplicate-name admission result. v4.2 carries the
// host's next expected client-command sequence in every player snapshot so a
// recreated peer runtime can resume without sacrificing its first action to a
// false duplicate rejection. Exact compatibility keeps any of these schemas
// from decoding another version's required fields or enum values.
const val PARLOR_PROTOCOL_MAJOR: Int = 4
const val PARLOR_PROTOCOL_MINOR: Int = 2
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
    InvalidCredential,
    ExpiredCredential,
    AlreadyConnected,
    DisplayNameInUse,
}

/** One generation of a device-protected resumable membership capability. */
@Serializable
data class ResumableCredentialOffer(
    val offerId: String,
    val playerId: PlayerId,
    val hostPeerId: String,
    val hostFingerprint: String,
    val secret: String,
    val generation: Long,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val gameId: String? = null,
    val gameVersion: Int? = null,
)

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
    SessionSuspended,
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
    @Deprecated("Use the transactional AdmissionOffered/AdmissionCommitted handshake.")
    data class AdmissionAccepted(
        val playerId: PlayerId,
        val rejoinToken: String,
    ) : HostMessage

    /** Initial host approval; the peer must durably stage [offer] before confirming. */
    @Serializable
    data class AdmissionOffered(
        val offer: ResumableCredentialOffer,
        val hostDisplayName: String,
    ) : HostMessage

    /** Valid room request is waiting for an explicit host decision. */
    @Serializable
    data class AdmissionPending(val playerId: PlayerId) : HostMessage

    /** Initial admission is authoritative and the staged credential may be promoted. */
    @Serializable
    data class AdmissionCommitted(
        val playerId: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : HostMessage

    /** Rotated credential offered after a valid pinned resume request. */
    @Serializable
    data class ResumeOffered(
        val offer: ResumableCredentialOffer,
        val hostDisplayName: String,
    ) : HostMessage

    /** Resume membership/session replacement committed on the host. */
    @Serializable
    data class ResumeCommitted(
        val playerId: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : HostMessage

    @Serializable
    data class AdmissionRejected(val reason: AdmissionRejection) : HostMessage

    /** Atomic public + own-private view at one authoritative revision. */
    @Serializable
    data class PlayerSnapshot(
        val header: SessionEnvelopeHeader,
        val revision: Long,
        /** The next command sequence the host will accept from this snapshot's recipient. */
        val nextExpectedClientSequence: Long,
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
    data class SessionEnded(
        val header: SessionEnvelopeHeader,
        val reason: SessionEndReason,
        val finalRevision: Long,
    ) : HostMessage

    /**
     * Idempotent first phase sent after the host taps *Start Game*. It carries
     * the canonical session shape so each peer can validate/load its game
     * prerequisites while remaining in the lobby. Only a matching
     * [SessionStartCommitted] authorizes that peer to enter gameplay.
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
        /** Stable, non-secret id reused by every retry of this start attempt. */
        val startId: String,
        val caseId: String,
        val modeId: String,
        val players: List<Player>,
        val sessionNonce: Long,
        val header: SessionEnvelopeHeader,
        /** Exact validated case revision; absent only for games without external case content. */
        val caseVersion: String? = null,
        /** Lowercase SHA-256 of canonical gameplay-visible case content. */
        val caseDigest: String? = null,
    ) : HostMessage

    /**
     * Authoritative second phase of [SessionStarting]. A peer must not leave
     * the lobby or construct a game controller until this frame validates
     * against the exact offer it acknowledged.
     */
    @Serializable
    data class SessionStartCommitted(
        val startId: String,
        val header: SessionEnvelopeHeader,
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

    /** Confirms that the initial credential offer is durably staged. */
    @Serializable
    data class AdmissionConfirmed(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : PeerMessage

    /** Best-effort acknowledgement that the peer promoted the initial credential. */
    @Serializable
    data class AdmissionCommitAck(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : PeerMessage

    /** Peer inbound collector is attached; host may now publish lobby/game frames. */
    @Serializable
    data class AdmissionReady(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : PeerMessage

    /** Requests replacement of a dead physical connection for a logical membership. */
    @Serializable
    data class ResumeRequested(
        val protocol: ProtocolVersion,
        val actor: PlayerId,
        val roomCode: String,
        val displayName: String,
        val secret: String,
        val generation: Long,
    ) : PeerMessage

    /** Confirms durable staging of the rotated resume offer. */
    @Serializable
    data class ResumeConfirmed(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : PeerMessage

    /** Best-effort acknowledgement that the rotated generation is active. */
    @Serializable
    data class ResumeCommitAck(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
    ) : PeerMessage

    /**
     * Sent only after the peer has attached its replacement-session inbound
     * collector. The host must not emit PeerReconnected/snapshots before this
     * handoff barrier, otherwise replay-zero transport flows can lose them.
     */
    @Serializable
    data class ResumeReady(
        val actor: PlayerId,
        val offerId: String,
        val generation: Long,
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

    /** Acknowledges a validated [HostMessage.SessionStarting] offer. */
    @Serializable
    data class SessionStartReady(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val startId: String,
    ) : PeerMessage

    /**
     * Acknowledges a validated authoritative commit. Duplicate commits are
     * answered with the same idempotent acknowledgement after game entry.
     */
    @Serializable
    data class SessionStartCommitAck(
        val header: SessionEnvelopeHeader,
        val actor: PlayerId,
        val startId: String,
    ) : PeerMessage

    @Serializable
    data object LeaveNotice : PeerMessage
}
