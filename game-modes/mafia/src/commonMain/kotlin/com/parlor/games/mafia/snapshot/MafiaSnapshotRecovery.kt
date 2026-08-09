package com.parlor.games.mafia.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.WinCheck
import com.parlor.games.mafia.domain.settings.MafiaSettingsValidation
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.team
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.storage.snapshot.SnapshotStore

internal val MAFIA_SNAPSHOT_VERSION = SemVer(1, 0, 0)
internal const val MAFIA_PLAY_MODE_KEY = "playMode"
internal const val MAFIA_PASS_AND_PLAY_MODE = "PassAndPlay"

internal data class ResumedMafiaSession(
    val sessionId: SessionId,
    val state: MafiaState,
)

internal suspend fun loadMafiaResumedSession(
    store: SnapshotStore,
    definition: MafiaDefinition,
    sessionId: SessionId,
): Result<ResumedMafiaSession, DataError> = when (val loaded = store.load(sessionId)) {
    is Result.Failure -> loaded
    is Result.Success -> try {
        val snapshot = loaded.data
        if (
            snapshot.sessionId != sessionId ||
            snapshot.gameId != MafiaIds.GameId ||
            snapshot.engineVersion.major != MAFIA_SNAPSHOT_VERSION.major ||
            snapshot.engineVersion > MAFIA_SNAPSHOT_VERSION ||
            snapshot.metadata[MAFIA_PLAY_MODE_KEY] != MAFIA_PASS_AND_PLAY_MODE
        ) {
            Result.Failure(DataError.CorruptedData)
        } else {
            val state = definition.snapshotCodec().decode(snapshot.payload)
            if (snapshot.phaseId != state.phase.id || !state.isValidRecoveryState()) {
                Result.Failure(DataError.CorruptedData)
            } else {
                Result.Success(ResumedMafiaSession(sessionId, state))
            }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Result.Failure(DataError.CorruptedData)
    }
}

/** Rejects structurally impossible or cross-player-contaminated local snapshots. */
internal fun MafiaState.isValidRecoveryState(): Boolean {
    val playerIds = players.map { it.id }
    val playerIdSet = playerIds.toSet()
    if (playerIds.size != playerIdSet.size || players.map { it.seat }.toSet().size != players.size) {
        return false
    }
    if (players.map { it.seat }.sorted() != players.indices.toList()) return false
    if (players.any { it.displayName.isBlank() }) return false
    if (public.settings.validate(players.size) !is MafiaSettingsValidation.Valid) return false

    // Only local pass-and-play snapshots are resumable through this path.
    // Connection chrome is a multi-device concern and can never be produced by
    // the local controller; accepting it would restore an unresolvable overlay.
    if (public.disconnectedPlayers.isNotEmpty() || public.droppedPlayers.isNotEmpty()) {
        return false
    }
    if (
        hostOnly.nightLog.size > MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES ||
        hostOnly.voteLog.size > MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES
    ) {
        return false
    }

    val rosterById = public.roster.associateBy { it.playerId }
    if (rosterById.size != players.size || rosterById.keys != playerIdSet) return false
    if (players.any { player ->
            val slot = rosterById[player.id] ?: return@any true
            slot.displayName != player.displayName || slot.seat != player.seat
        }
    ) {
        return false
    }
    public.activeVote?.let { vote ->
        val voting = phase as? MafiaPhase.Voting ?: return false
        val alive = public.roster.filter { it.alive }.map { it.playerId }.toSet()
        if (
            vote.day != voting.day || vote.revoteRound != voting.revoteRound ||
            vote.revoteRound !in 0..public.settings.maxRevotes ||
            vote.candidates.isEmpty() || vote.ballot.isEmpty() ||
            vote.candidates.distinct().size != vote.candidates.size ||
            vote.ballot.distinct().size != vote.ballot.size ||
            !alive.containsAll(vote.candidates) ||
            !alive.containsAll(vote.ballot) ||
            !vote.ballot.containsAll(vote.castSoFar.keys) ||
            !vote.candidates.containsAll(vote.castSoFar.values) ||
            !vote.ballot.containsAll(vote.abstained) ||
            vote.castSoFar.keys.any(vote.abstained::contains) ||
            (!public.settings.allowSelfVote && vote.castSoFar.any { (voter, target) -> voter == target })
        ) {
            return false
        }
    }
    if (phase !is MafiaPhase.Voting && public.activeVote != null) return false

    if (!public.isValidAnnouncements(playerIdSet)) return false
    if (!isValidPhaseShape()) return false

    if (phase == MafiaPhase.Setup) {
        return hostOnly.fullRoleMap.isEmpty() &&
            privatePerPlayer.isEmpty() &&
            hostOnly.nightLog.isEmpty() &&
            hostOnly.voteLog.isEmpty()
    }
    if (
        phase == MafiaPhase.PostGame && public.winner == null &&
        hostOnly.fullRoleMap.isEmpty() && privatePerPlayer.isEmpty()
    ) {
        return true
    }
    if (hostOnly.fullRoleMap.keys != playerIdSet || privatePerPlayer.keys != playerIdSet) return false
    val configured = public.settings.roleCounts
    if (
        hostOnly.fullRoleMap.values.count { it == Role.Mafia } != configured.mafia ||
        hostOnly.fullRoleMap.values.count { it == Role.Detective } != configured.detective ||
        hostOnly.fullRoleMap.values.count { it == Role.Doctor } != configured.doctor ||
        hostOnly.fullRoleMap.values.count { it == Role.Civilian } != configured.civilians(players.size)
    ) {
        return false
    }
    val mafiaIds = hostOnly.fullRoleMap.filterValues { it == Role.Mafia }.keys
    if (privatePerPlayer.any { (playerId, private) ->
            val role = hostOnly.fullRoleMap[playerId] ?: return@any true
            private.role != role || private.team != role.team ||
                private.knownTeammates != (
                    if (role == Role.Mafia) mafiaIds - playerId
                    else emptySet<com.parlor.core.ids.PlayerId>()
                ) ||
                (private.pendingDetectiveResult != null && role != Role.Detective) ||
                (private.previousDoctorProtect != null && role != Role.Doctor) ||
                (private.lastSuspicion != null && role != Role.Civilian) ||
                private.pendingNightChoice?.let { it !in playerIdSet } == true ||
                private.pendingDetectiveResult?.let {
                    it.target !in playerIdSet || it.day !in 1..public.day
                } == true
        }
    ) {
        return false
    }
    if (!hasValidPrivatePhaseFlags()) return false
    if (!hasValidMafiaCoordination(mafiaIds)) return false
    if (!hasValidRoleVisibility()) return false
    if (
        phase == MafiaPhase.PostGame && privatePerPlayer.values.any { private ->
            private.mafiaCoordination != null ||
                private.pendingDetectiveResult != null ||
                private.lastSuspicion != null ||
                private.previousDoctorProtect != null ||
                private.pendingNightChoice != null ||
                private.roleAcknowledged ||
                private.nightAcknowledged ||
                private.voteAcknowledged ||
                private.detectiveResultAcknowledged ||
                private.nightChoiceSubmitted
        }
    ) {
        return false
    }
    if (public.winner != null && phase != MafiaPhase.PostGame) return false
    if (public.winner == Team.Mafia && hostOnly.fullRoleMap.values.none { it.team == Team.Mafia }) {
        return false
    }
    if (public.winner != null) {
        val alive = public.roster.filter { it.alive }.map { it.playerId }.toSet()
        if (WinCheck.evaluate(alive, hostOnly.fullRoleMap) != public.winner) return false
    }
    return true
}

private fun com.parlor.games.mafia.domain.state.MafiaPublic.isValidAnnouncements(
    playerIds: Set<com.parlor.core.ids.PlayerId>,
): Boolean {
    lastNight?.let { announcement ->
        if (
            announcement.day !in 1..day ||
            announcement.killedPlayerId?.let { it !in playerIds } == true ||
            announcement.killedPlayerId != null && announcement.wasSaved
        ) {
            return false
        }
    }
    lastVote?.let { announcement ->
        if (
            announcement.day !in 1..day ||
            announcement.tally.any { (id, count) -> id !in playerIds || count <= 0 } ||
            announcement.eliminatedPlayerId?.let { it !in playerIds } == true
        ) {
            return false
        }
    }
    return true
}

private fun MafiaState.isValidPhaseShape(): Boolean = when (val current = phase) {
    MafiaPhase.Setup,
    MafiaPhase.RoleAssignment,
    -> public.day == 0 &&
        public.lastNight == null &&
        public.lastVote == null &&
        public.activeVote == null &&
        public.winner == null &&
        public.roster.all { it.alive && it.revealedRole == null }

    is MafiaPhase.Night -> current.day >= 1 &&
        current.mafiaCoordinationRound in 1..2 &&
        public.day == current.day &&
        public.activeVote == null &&
        public.winner == null

    is MafiaPhase.NightAnnouncement -> current.day >= 1 &&
        public.day == current.day &&
        public.lastNight?.day == current.day &&
        public.activeVote == null &&
        public.winner == null

    is MafiaPhase.Discussion -> current.day >= 1 &&
        public.day == current.day &&
        public.lastNight?.day == current.day &&
        public.activeVote == null &&
        public.winner == null

    is MafiaPhase.Voting -> current.day >= 1 &&
        current.revoteRound in 0..public.settings.maxRevotes &&
        public.day == current.day &&
        public.lastNight?.day == current.day &&
        public.activeVote != null &&
        public.winner == null

    is MafiaPhase.VoteAnnouncement -> current.day >= 1 &&
        public.day == current.day &&
        public.lastNight?.day == current.day &&
        public.lastVote?.day == current.day &&
        public.activeVote == null &&
        public.winner == null

    MafiaPhase.PostGame -> public.activeVote == null
}

private fun MafiaState.hasValidPrivatePhaseFlags(): Boolean =
    privatePerPlayer.all { (_, private) ->
        (phase == MafiaPhase.RoleAssignment || !private.roleAcknowledged) &&
            (phase is MafiaPhase.NightAnnouncement || !private.nightAcknowledged) &&
            (phase is MafiaPhase.VoteAnnouncement || !private.voteAcknowledged) &&
            (phase is MafiaPhase.Night || (!private.nightChoiceSubmitted && private.pendingNightChoice == null))
    }

private fun MafiaState.hasValidMafiaCoordination(
    mafiaIds: Set<com.parlor.core.ids.PlayerId>,
): Boolean {
    val expectedRound = when (val current = phase) {
        MafiaPhase.RoleAssignment -> 1
        is MafiaPhase.Night -> current.mafiaCoordinationRound
        else -> null
    }
    val aliveIds = public.roster.filter { it.alive }.map { it.playerId }.toSet()
    val expectedOwners = if (expectedRound == null) emptySet() else mafiaIds.intersect(aliveIds)
    val snapshots = privatePerPlayer
        .filterValues { it.mafiaCoordination != null }
        .mapValues { it.value.mafiaCoordination!! }
    if (snapshots.keys != expectedOwners) return false
    if (snapshots.isEmpty()) return expectedOwners.isEmpty()
    val canonical = snapshots.values.first()
    if (
        canonical.round != expectedRound ||
        snapshots.values.any { it != canonical } ||
        !expectedOwners.containsAll(canonical.submissions.keys) ||
        !aliveIds.containsAll(canonical.submissions.values) ||
        canonical.previousRoundTally?.any { (id, count) -> id !in aliveIds || count <= 0 } == true
    ) {
        return false
    }
    return true
}

private fun MafiaState.hasValidRoleVisibility(): Boolean = public.roster.all { slot ->
    val authoritativeRole = hostOnly.fullRoleMap[slot.playerId] ?: return@all false
    when {
        phase == MafiaPhase.PostGame -> slot.revealedRole == authoritativeRole
        slot.alive -> slot.revealedRole == null
        public.settings.revealRoleOnDeath -> slot.revealedRole == authoritativeRole
        else -> slot.revealedRole == null
    }
}
