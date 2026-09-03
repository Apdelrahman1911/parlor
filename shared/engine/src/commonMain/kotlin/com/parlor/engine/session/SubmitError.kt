package com.parlor.engine.session

/** Errors when a submitted action cannot be applied. */
sealed interface SubmitError {
    data object IllegalForPhase : SubmitError
    data object UnknownPlayer : SubmitError
    data class RejectedByReducer(val reason: String) : SubmitError

    /** Another host-authoritative mutation is awaiting an explicit outcome. */
    data object CommandPending : SubmitError

    /** The room is temporarily unable to accept gameplay mutations. */
    data object SessionSuspended : SubmitError

    data object SessionClosed : SubmitError
}
