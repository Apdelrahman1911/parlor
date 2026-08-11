package com.parlor.games.mafia.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.rules.WinCheck
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.state.MafiaCoordinationSnapshot
import com.parlor.games.mafia.domain.state.MafiaHostOnly
import com.parlor.games.mafia.domain.state.MafiaObservableStateValidator
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.NightResolutionRecord
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.VoteRoundRecord
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
                (private.pendingNightChoice != null && !private.nightChoiceSubmitted) ||
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
    if (!hasValidResolutionHistory()) return false
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
    val activeAlive = public.roster
        .filter { it.alive && it.playerId !in public.droppedPlayers }
        .map { it.playerId }
        .toSet()
    if (WinCheck.evaluate(activeAlive, hostOnly.fullRoleMap) != public.winner) return false
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
        if (phase == MafiaPhase.RoleAssignment && private.hasGameplayProgress()) {
            return@all false
        }
        if (night?.day == 1 && private.previousDoctorProtect != null) return@all false
        if (
            night?.day == 1 &&
            private.role == Role.Civilian &&
            private.lastSuspicion != null &&
            !private.nightChoiceSubmitted
        ) {
            return@all false
        }
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

private fun MafiaState.hasValidMafiaCoordination(
    mafiaIds: Set<com.parlor.core.ids.PlayerId>,
): Boolean {
    val expectedRound = when (val current = phase) {
        MafiaPhase.RoleAssignment -> 1
        is MafiaPhase.Night -> current.mafiaCoordinationRound
        else -> null
    }
    val activeAliveIds = public.roster
        .filter { it.alive && it.playerId !in public.droppedPlayers }
        .map { it.playerId }
        .toSet()
    val expectedOwners = if (expectedRound == null) emptySet() else mafiaIds.intersect(activeAliveIds)
    val snapshots = privatePerPlayer
        .mapNotNull { (playerId, private) ->
            private.mafiaCoordination?.let { playerId to it }
        }
        .toMap()
    if (snapshots.keys != expectedOwners) return false
    if (snapshots.isEmpty()) return expectedOwners.isEmpty()
    val canonical = snapshots.values.first()
    if (canonical.round != expectedRound || snapshots.values.any { it != canonical }) return false
    if (!canonical.hasValidCoordinationTargets(expectedOwners, activeAliveIds)) return false
    if (!hasValidCoordinationRound(canonical, expectedOwners, mafiaIds, activeAliveIds)) return false
    if (!hasValidMafiaSubmissionTargets(canonical)) return false
    return expectedOwners.all { mafiaId ->
        val private = privatePerPlayer.getValue(mafiaId)
        val submittedTarget = canonical.submissions[mafiaId]
        if (private.nightChoiceSubmitted && private.pendingNightChoice != null) {
            submittedTarget == private.pendingNightChoice
        } else {
            submittedTarget == null
        }
    }
}

private fun MafiaPrivate.hasGameplayProgress(): Boolean =
    pendingDetectiveResult != null ||
        lastSuspicion != null ||
        previousDoctorProtect != null ||
        pendingNightChoice != null ||
        nightAcknowledged ||
        voteAcknowledged ||
        detectiveResultAcknowledged ||
        nightChoiceSubmitted

private fun MafiaCoordinationSnapshot.hasValidCoordinationTargets(
    expectedOwners: Set<com.parlor.core.ids.PlayerId>,
    activeAliveIds: Set<com.parlor.core.ids.PlayerId>,
): Boolean =
    expectedOwners.containsAll(submissions.keys) &&
        activeAliveIds.containsAll(submissions.values) &&
        previousRoundTally?.all { (id, count) -> id in activeAliveIds && count > 0 } != false &&
        (round != 1 || previousRoundTally == null)

private fun MafiaState.hasValidCoordinationRound(
    coordination: MafiaCoordinationSnapshot,
    expectedOwners: Set<com.parlor.core.ids.PlayerId>,
    mafiaIds: Set<com.parlor.core.ids.PlayerId>,
    activeAliveIds: Set<com.parlor.core.ids.PlayerId>,
): Boolean {
    if (coordination.round != 2) return true
    val previousTally = coordination.previousRoundTally.orEmpty()
    if (phase !is MafiaPhase.Night) return false
    if (public.settings.mafiaKillTieBehavior != MafiaKillTie.REVOTE) return false
    if (!coordination.previousRoundTally.hasTiedLead()) return false
    if (previousTally.values.sumOf { it.toLong() } > expectedOwners.size.toLong()) return false
    if (!public.settings.mafiaCanTargetMafia && previousTally.keys.any(mafiaIds::contains)) return false
    return privatePerPlayer.none { (playerId, private) ->
        playerId in activeAliveIds && private.team == Team.Town && !private.nightChoiceSubmitted
    }
}

private fun MafiaState.hasValidMafiaSubmissionTargets(
    coordination: MafiaCoordinationSnapshot,
): Boolean = coordination.submissions.none { (by, target) ->
    by == target ||
        (!public.settings.mafiaCanTargetMafia && hostOnly.fullRoleMap[target] == Role.Mafia)
}

private fun Map<com.parlor.core.ids.PlayerId, Int>?.hasTiedLead(): Boolean {
    val tally = this ?: return false
    val maximum = tally.values.maxOrNull() ?: return false
    return tally.values.count { it == maximum } >= 2
}

/**
 * The host audit records are reducer output, not a migration surface. Recovery
 * therefore validates their retained suffix against public announcements,
 * roles, mortality, and the exact order in which a legal day can resolve.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // Deliberate fail-closed reducer reachability checks.
private fun MafiaState.hasValidResolutionHistory(): Boolean {
    val nights = hostOnly.nightLog
    val votes = hostOnly.voteLog
    val latestNightDay = public.lastNight?.day
    val latestVoteDay = public.lastVote?.day
    if (!nights.hasExpectedNightDays(latestNightDay)) return false
    if (!votes.hasExpectedVoteDays(latestVoteDay)) return false

    val latestNight = nights.lastOrNull()
    val nightAnnouncement = public.lastNight
    if ((latestNight == null) != (nightAnnouncement == null)) return false
    if (latestNight != null && nightAnnouncement != null) {
        if (
            latestNight.day != nightAnnouncement.day ||
            latestNight.killedPlayerId != nightAnnouncement.killedPlayerId ||
            nightAnnouncement.wasSaved != (
                latestNight.mafiaTarget != null &&
                    latestNight.mafiaTarget == latestNight.doctorProtect
                )
        ) {
            return false
        }
    }

    val latestVote = votes.lastOrNull()
    val voteAnnouncement = public.lastVote
    if ((latestVote == null) != (voteAnnouncement == null)) return false
    if (latestVote != null && voteAnnouncement != null) {
        if (
            latestVote.day != voteAnnouncement.day ||
            latestVote.tally != voteAnnouncement.tally ||
            latestVote.eliminatedPlayerId != voteAnnouncement.eliminatedPlayerId
        ) {
            return false
        }
    }

    val playerIds = players.map { it.id }.toSet()
    val recordedDeaths = buildList {
        nights.mapNotNullTo(this) { it.killedPlayerId }
        votes.mapNotNullTo(this) { it.eliminatedPlayerId }
    }
    if (recordedDeaths.distinct().size != recordedDeaths.size) return false
    val recordedDeathSet = recordedDeaths.toSet()
    val currentDead = public.roster.filterNot { it.alive }.map { it.playerId }.toSet()
    if (!currentDead.containsAll(recordedDeathSet)) return false

    val completeHistory =
        (latestNightDay ?: 0) <= MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES &&
            (latestVoteDay ?: 0) <= MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES
    if (completeHistory && currentDead != recordedDeathSet) return false

    // Deaths outside a capped suffix happened before the first retained event.
    var alive = playerIds - (currentDead - recordedDeathSet)
    var winnerReached = WinCheck.evaluate(alive, hostOnly.fullRoleMap) != null
    var previousDoctorProtect: com.parlor.core.ids.PlayerId? = null
    var hasPreviousDoctorRecord = false
    val days = (nights.map { it.day } + votes.map { it.day }).distinct().sorted()
    for (day in days) {
        nights.firstOrNull { it.day == day }?.let { record ->
            if (winnerReached) return false
            if (!isValidNightRecord(record, alive, previousDoctorProtect, hasPreviousDoctorRecord)) {
                return false
            }
            previousDoctorProtect = record.doctorProtect
            hasPreviousDoctorRecord = true
            record.killedPlayerId?.let { alive -= it }
            winnerReached = WinCheck.evaluate(alive, hostOnly.fullRoleMap) != null
        }
        votes.firstOrNull { it.day == day }?.let { record ->
            if (winnerReached) return false
            if (!isValidVoteRecord(record, alive)) return false
            record.eliminatedPlayerId?.let { alive -= it }
            winnerReached = WinCheck.evaluate(alive, hostOnly.fullRoleMap) != null
        }
    }
    val currentAlive = public.roster.filter { it.alive }.map { it.playerId }.toSet()
    return alive == currentAlive
}

private fun List<NightResolutionRecord>.hasExpectedNightDays(latestDay: Int?): Boolean =
    hasExpectedRetainedDays(latestDay) { it.day }

private fun List<VoteRoundRecord>.hasExpectedVoteDays(latestDay: Int?): Boolean =
    hasExpectedRetainedDays(latestDay) { it.day }

private inline fun <T> List<T>.hasExpectedRetainedDays(
    latestDay: Int?,
    dayOf: (T) -> Int,
): Boolean {
    if (latestDay == null) return isEmpty()
    if (latestDay < 1) return false
    val expectedSize = minOf(latestDay, MafiaHostOnly.MAX_SERIALIZED_LOG_ENTRIES)
    if (size != expectedSize) return false
    val firstDay = latestDay - expectedSize + 1
    return withIndex().all { (index, record) -> dayOf(record) == firstDay + index }
}

@Suppress("CyclomaticComplexMethod", "ComplexCondition") // One reducer-produced night record boundary.
private fun MafiaState.isValidNightRecord(
    record: NightResolutionRecord,
    alive: Set<com.parlor.core.ids.PlayerId>,
    previousDoctorProtect: com.parlor.core.ids.PlayerId?,
    hasPreviousDoctorRecord: Boolean,
): Boolean {
    val roles = hostOnly.fullRoleMap
    val mafiaTarget = record.mafiaTarget
    if (mafiaTarget != null) {
        if (mafiaTarget !in alive) return false
        if (!public.settings.mafiaCanTargetMafia && roles[mafiaTarget] == Role.Mafia) return false
    }
    if (record.mafiaTargetTied) {
        if (alive.count { roles[it] == Role.Mafia } < 2) return false
        when (public.settings.mafiaKillTieBehavior) {
            MafiaKillTie.NO_KILL -> if (mafiaTarget != null) return false
            MafiaKillTie.REVOTE,
            MafiaKillTie.RANDOM_TIED,
            -> if (mafiaTarget == null) return false
        }
    }

    val doctorId = roles.entries.singleOrNull { it.value == Role.Doctor }?.key
    record.doctorProtect?.let { target ->
        if (doctorId == null || doctorId !in alive || target !in alive) return false
        if (!public.settings.doctorCanSelfHeal && target == doctorId) return false
        if (
            hasPreviousDoctorRecord &&
            !public.settings.doctorCanProtectSamePlayerConsecutively &&
            target == previousDoctorProtect
        ) {
            return false
        }
    }

    val detectiveId = roles.entries.singleOrNull { it.value == Role.Detective }?.key
    record.detectiveInspect?.let { target ->
        if (detectiveId == null || detectiveId !in alive || target !in alive) return false
        if (!public.settings.detectiveCanInspectSelf && target == detectiveId) return false
    }
    val expectedDetectiveResult = record.detectiveInspect?.let { roles[it]?.detectiveSeesAs() }
    if (record.detectiveResult != expectedDetectiveResult) return false

    val expectedKilled = mafiaTarget?.takeUnless { it == record.doctorProtect }
    return record.killedPlayerId == expectedKilled
}

private fun MafiaState.isValidVoteRecord(
    record: VoteRoundRecord,
    alive: Set<com.parlor.core.ids.PlayerId>,
): Boolean {
    if (record.revoteRound !in 0..public.settings.maxRevotes) return false
    if (record.tally.any { (target, count) -> target !in alive || count <= 0 }) return false
    if (record.tally.values.sumOf(Int::toLong) > alive.size.toLong()) return false

    val maximum = record.tally.values.maxOrNull()
    val leaders = if (maximum == null) emptySet() else record.tally.filterValues { it == maximum }.keys
    val eliminated = record.eliminatedPlayerId
    if (eliminated != null) {
        return eliminated in alive && leaders.size == 1 && leaders.single() == eliminated
    }
    if (record.tally.isEmpty()) return true
    if (leaders.size < 2) return false
    return when (public.settings.voteTieBehavior) {
        com.parlor.games.mafia.domain.settings.TieBehavior.SKIP_ELIMINATION -> record.revoteRound == 0
        com.parlor.games.mafia.domain.settings.TieBehavior.REVOTE_ALL,
        com.parlor.games.mafia.domain.settings.TieBehavior.REVOTE_TIED_ONLY,
        -> record.revoteRound == public.settings.maxRevotes
    }
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
