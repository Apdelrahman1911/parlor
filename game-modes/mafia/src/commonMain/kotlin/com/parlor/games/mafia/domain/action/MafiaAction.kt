package com.parlor.games.mafia.domain.action

import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.games.mafia.domain.settings.MafiaSettings
import kotlinx.serialization.Serializable

/**
 * Mafia's full sealed input vocabulary.
 *
 * Annotated `@Serializable` so peer→host action submissions can ride the
 * `PeerMessage.ClientCommand(payload: ByteArray)` wire (see [MafiaActionCodec]).
 */
@Serializable
sealed interface MafiaAction : GameAction {

    // -------- Host lifecycle --------
    /** Host commits chosen settings before the game starts. */
    @Serializable data class ApplySettings(val settings: MafiaSettings) : MafiaAction

    /** Host validates, commits settings, assigns roles, and starts atomically. */
    @Serializable data class ConfigureAndStart(val settings: MafiaSettings) : MafiaAction

    /** Host begins the game — assigns roles, transitions to RoleAssignment. */
    @Serializable data object StartGame : MafiaAction

    /** Host advances from RoleAssignment to first Night (gated by every player ack'ing). */
    @Serializable data object AdvanceFromRoleAssignment : MafiaAction

    /** Host resolves the current night once all required night submissions are in. */
    @Serializable data object ResolveNight : MafiaAction

    /** Host advances from NightAnnouncement to Discussion (gated by everyone ack'ing). */
    @Serializable data object OpenDiscussion : MafiaAction

    /** Host advances from Discussion to Voting. */
    @Serializable data object OpenVote : MafiaAction

    /** Host closes voting — reducer tallies and either resolves or opens a revote. */
    @Serializable data object CloseVote : MafiaAction

    /** Host advances from VoteAnnouncement to the next Night or PostGame. */
    @Serializable data object AdvanceFromVoteAnnouncement : MafiaAction

    /** Host ends the game early. */
    @Serializable data object EndGame : MafiaAction

    // -------- Host connection / readiness chrome --------
    @Serializable data class MarkPlayerDisconnected(val playerId: PlayerId) : MafiaAction
    @Serializable data class MarkPlayerReconnected(val playerId: PlayerId) : MafiaAction
    @Serializable data class ContinueWithoutPlayer(val playerId: PlayerId) : MafiaAction

    // -------- Self-actor (player-submitted) --------
    @Serializable data class AcknowledgeRoleViewed(val by: PlayerId) : MafiaAction

    /** Mafia member submits a kill-vote target for the current coordination round. */
    @Serializable data class SubmitMafiaKillVote(val by: PlayerId, val target: PlayerId?) : MafiaAction

    @Serializable data class SubmitDoctorProtect(val by: PlayerId, val target: PlayerId?) : MafiaAction
    @Serializable data class SubmitDetectiveInspect(val by: PlayerId, val target: PlayerId?) : MafiaAction
    @Serializable data class SubmitCivilianSuspicion(val by: PlayerId, val target: PlayerId?) : MafiaAction

    @Serializable data class AcknowledgeNightAnnouncement(val by: PlayerId) : MafiaAction
    @Serializable data class AcknowledgeDetectiveResult(val by: PlayerId) : MafiaAction

    @Serializable data class CastVote(val by: PlayerId, val target: PlayerId) : MafiaAction
    @Serializable data class AbstainVote(val by: PlayerId) : MafiaAction

    @Serializable data class AcknowledgeVoteAnnouncement(val by: PlayerId) : MafiaAction
}
