package com.parlor.networking.room

/**
 * Typed errors a peer can encounter when joining a room. The Join UI
 * (9H-8) renders one localized message per variant; the controller never
 * surfaces a raw transport exception or class name. This is the public
 * vocabulary — internal diagnostic details stay in logs.
 *
 * Mapping from transport-level [NetError]:
 *  - [NetError.NotConnected] → [HostUnreachable]
 *  - [NetError.Timeout]      → [ConnectionTimeout]
 *  - [NetError.Unauthorized] → [Generic] (no richer protocol signal yet)
 *  - [NetError.TransportFailure] → [Generic] (message stripped)
 *
 * The protocol-level [RoomFull] and [GameAlreadyStarted] are reserved for
 * when the host explicitly rejects a join via a typed message. The
 * vocabulary is in place now so the controller, UI, and tests can be
 * written against it; the transport's NetError → JoinError table will
 * grow as the join handshake gains those rejection codes.
 */
sealed interface JoinError {

    /** The text the user typed isn't a well-formed room code. */
    data object WrongCode : JoinError

    /** No room with this code was discoverable or reachable. */
    data object RoomNotFound : JoinError

    /** Transport couldn't reach the host (NotConnected / no route). */
    data object HostUnreachable : JoinError

    /** The host rejected the join because the room is at capacity. */
    data object RoomFull : JoinError

    /** The host rejected the join because the session is already underway. */
    data object GameAlreadyStarted : JoinError

    /** Took too long to connect to the host. */
    data object ConnectionTimeout : JoinError

    /**
     * Anything we can't classify into the buckets above. Carries no
     * transport-level diagnostic text — the UI shows a generic message
     * so we never leak the underlying exception or class name through
     * the user-visible surface.
     */
    data object Generic : JoinError
}
