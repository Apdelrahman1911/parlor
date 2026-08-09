package com.parlor.games.whodunit.ui.screens.safety

/**
 * Keeps the privacy-recovery UI aligned with the host-authoritative action
 * policy. Peers can report an exposure, but only the host can replace every
 * private assignment and publish the new canonical snapshots.
 */
internal enum class PrivacyConcernUiPolicy {
    HostMayReroll,
    PeerMustContactHost,
}

internal fun privacyConcernUiPolicy(isHost: Boolean): PrivacyConcernUiPolicy =
    if (isHost) PrivacyConcernUiPolicy.HostMayReroll
    else PrivacyConcernUiPolicy.PeerMustContactHost
