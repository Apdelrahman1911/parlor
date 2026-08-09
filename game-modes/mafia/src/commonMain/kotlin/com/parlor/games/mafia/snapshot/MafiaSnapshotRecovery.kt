package com.parlor.games.mafia.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.WinCheck
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaObservableStateValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.detectiveSeesAs
import com.parlor.games.mafia.domain.state.team
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CancellationException

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
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        Result.Failure(DataError.CorruptedData)
    }
}

/** Rejects structurally impossible or cross-player-contaminated local snapshots. */
@Suppress("CyclomaticComplexMethod", "ComplexCondition") // Independent fail-closed state invariants; mutation tests cover each clause.
internal fun MafiaState.isValidRecoveryState(): Boolean {
    val playerIds = players.map { it.id }
    val playerIdSet = playerIds.toSet()
    if (!MafiaObservableStateValidator.isValid(this)) return false

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

    if (hostOnly.fullRoleMap.isEmpty() || privatePerPlayer.isEmpty()) {
        if (hostOnly.fullRoleMap.isNotEmpty() || privatePerPlayer.isNotEmpty()) return false
        return (phase == MafiaPhase.Setup || phase == MafiaPhase.PostGame) &&
            public.day == 0 &&
            public.lastNight == null &&
            public.lastVote == null &&
            public.activeVote == null &&
            public.winner == null &&
            public.roster.all { it.alive && it.revealedRole == null } &&
            hostOnly.nightLog.isEmpty() &&
            hostOnly.voteLog.isEmpty()
    }
    if (phase == MafiaPhase.Setup) return false
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
                (private.detectiveResultAcknowledged && role != Role.Detective) ||
                (private.previousDoctorProtect != null && role != Role.Doctor) ||
                private.previousDoctorProtect?.let { it !in playerIdSet } == true ||
                (
                    private.previousDoctorProtect == playerId &&
                        !public.settings.doctorCanSelfHeal
                ) ||
                (private.lastSuspicion != null && role != Role.Civilian) ||
                (private.lastSuspicion == playerId) ||
                private.pendingNightChoice?.let { it !in playerIdSet } == true ||
                private.pendingDetectiveResult?.let {
                    it.target !in playerIdSet ||
                        it.day !in 1..public.day ||
                        it.seesAs != hostOnly.fullRoleMap[it.target]?.detectiveSeesAs()
                } == true
        }
    ) {
        return false
    }
    if (!hasValidPrivatePhaseFlags()) return false
    if (!hasValidPrivateActionState()) return false
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

private fun MafiaState.hasValidPrivatePhaseFlags(): Boolean =
    privatePerPlayer.all { (_, private) ->
        (phase == MafiaPhase.RoleAssignment || !private.roleAcknowledged) &&
            (phase is MafiaPhase.NightAnnouncement || !private.nightAcknowledged) &&
            (phase is MafiaPhase.VoteAnnouncement || !private.voteAcknowledged) &&
            (phase is MafiaPhase.Night || (!private.nightChoiceSubmitted && private.pendingNightChoice == null))
    }

@Suppress("CyclomaticComplexMethod") // Role-specific action invariants are intentionally evaluated independently.
private fun MafiaState.hasValidPrivateActionState(): Boolean {
    val activeAlive = public.roster
        .filter { it.alive && it.playerId !in public.droppedPlayers }
        .map { it.playerId }
        .toSet()
    val night = phase as? MafiaPhase.Night
    return privatePerPlayer.all { (playerId, private) ->
        if (private.detectiveResultAcknowledged && private.role != Role.Detective) return@all false
        if (private.lastSuspicion == playerId) return@all false
        if (private.nightChoiceSubmitted && (night == null || playerId !in activeAlive)) {
            return@all false
        }
        private.pendingNightChoice?.let { target ->
            if (night == null || playerId !in activeAlive || target !in activeAlive) return@all false
            when (private.role) {
                Role.Mafia -> if (
                    target == playerId ||
                    (!public.settings.mafiaCanTargetMafia && hostOnly.fullRoleMap[target] == Role.Mafia)
                ) {
                    return@all false
                }
                Role.Doctor -> if (
                    (!public.settings.doctorCanSelfHeal && target == playerId) ||
                    (
                        !public.settings.doctorCanProtectSamePlayerConsecutively &&
                            target == private.previousDoctorProtect
                    )
                ) {
                    return@all false
                }
                Role.Detective -> if (
                    !public.settings.detectiveCanInspectSelf && target == playerId
                ) {
                    return@all false
                }
                Role.Civilian -> return@all false
            }
        }
        private.pendingDetectiveResult?.let { result ->
            if (result.seesAs != hostOnly.fullRoleMap[result.target]?.detectiveSeesAs()) return@all false
            if (night != null) {
                if (
                    result.day != night.day ||
                    result.target != private.pendingNightChoice ||
                    !private.nightChoiceSubmitted
                ) {
                    return@all false
                }
            } else if (!private.detectiveResultAcknowledged) {
                return@all false
            }
        }
        if (
            night != null &&
            private.role == Role.Detective &&
            private.nightChoiceSubmitted &&
            private.pendingNightChoice == null &&
            !private.detectiveResultAcknowledged
        ) {
            return@all false
        }
        true
    }
}

@Suppress("ComplexCondition") // One atomic consistency check over the shared coordination projection.
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
        .mapNotNull { (playerId, private) ->
            private.mafiaCoordination?.let { playerId to it }
        }
        .toMap()
    if (snapshots.keys != expectedOwners) return false
    if (snapshots.isEmpty()) return expectedOwners.isEmpty()
    val canonical = snapshots.values.first()
    if (
        canonical.round != expectedRound ||
        snapshots.values.any { it != canonical } ||
        !expectedOwners.containsAll(canonical.submissions.keys) ||
        !aliveIds.containsAll(canonical.submissions.values) ||
        canonical.previousRoundTally?.any { (id, count) -> id !in aliveIds || count <= 0 } == true ||
        (canonical.round == 1 && canonical.previousRoundTally != null) ||
        (canonical.round == 2 && !canonical.previousRoundTally.hasTiedLead()) ||
        canonical.submissions.any { (by, target) ->
            by == target ||
                (!public.settings.mafiaCanTargetMafia && hostOnly.fullRoleMap[target] == Role.Mafia)
        }
    ) {
        return false
    }
    expectedOwners.forEach { mafiaId ->
        val private = privatePerPlayer.getValue(mafiaId)
        val submittedTarget = canonical.submissions[mafiaId]
        if (
            if (private.nightChoiceSubmitted && private.pendingNightChoice != null) {
                submittedTarget != private.pendingNightChoice
            } else {
                submittedTarget != null
            }
        ) {
            return false
        }
    }
    return true
}

private fun Map<com.parlor.core.ids.PlayerId, Int>?.hasTiedLead(): Boolean {
    val tally = this ?: return false
    val maximum = tally.values.maxOrNull() ?: return false
    return tally.values.count { it == maximum } >= 2
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
