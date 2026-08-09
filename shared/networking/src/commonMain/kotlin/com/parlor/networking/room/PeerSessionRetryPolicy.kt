package com.parlor.networking.room

/** The transport operation used for the next peer room attempt. */
enum class PeerSessionAttempt {
    Join,
    Resume,
}

/**
 * Immutable policy for selecting join versus resume around the authoritative
 * start transaction.
 *
 * A fresh peer normally uses room-code admission. Once that peer has been
 * admitted, a start-handshake/content-preparation failure does not make the
 * frozen seat fresh again: the physical room is closed through
 * [LocalRoom.closeForRetry] and every subsequent retry must resume it. A
 * successfully acquired replacement room clears that transient marker; if its
 * start transaction also fails, [afterPostAdmissionStartFailure] sets it again.
 */
class PeerSessionRetryPolicy private constructor(
    private val resumeWasExplicitlyRequested: Boolean,
    private val retainedMembership: Boolean,
) {
    val nextAttempt: PeerSessionAttempt
        get() = if (resumeWasExplicitlyRequested || retainedMembership) {
            PeerSessionAttempt.Resume
        } else {
            PeerSessionAttempt.Join
        }

    fun afterRoomAcquired(): PeerSessionRetryPolicy =
        PeerSessionRetryPolicy(
            resumeWasExplicitlyRequested = resumeWasExplicitlyRequested,
            retainedMembership = false,
        )

    fun afterPostAdmissionStartFailure(): PeerSessionRetryPolicy =
        PeerSessionRetryPolicy(
            resumeWasExplicitlyRequested = resumeWasExplicitlyRequested,
            retainedMembership = true,
        )

    companion object {
        fun initial(resumeExistingSession: Boolean): PeerSessionRetryPolicy =
            PeerSessionRetryPolicy(
                resumeWasExplicitlyRequested = resumeExistingSession,
                retainedMembership = false,
            )
    }
}
