package com.parlor.app.storage

import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Real definitions/codecs and bundled content; never manufacture a role map or recovery phase. */
internal enum class AndroidResumeGame(val englishTitle: String, val arabicTitle: String) {
    LastLight("Last Light", "الضوء الأخير"),
    Mafia("Mafia", "حرامية وظباط"),
    Whodunit("Whodunit", "من القاتل"),
}

internal object AndroidLegalResumeFixtures {
    private const val SEED = 7L
    private const val ORIGINAL_GAME_PLAYERS = 6
    private val instant = Instant.fromEpochMilliseconds(0)

    suspend fun create(game: AndroidResumeGame, id: SessionId, repository: CaseRepository): GameSnapshot = when (game) {
        AndroidResumeGame.LastLight -> lastLight(id)
        AndroidResumeGame.Mafia -> mafia(id)
        AndroidResumeGame.Whodunit -> whodunit(id, repository)
    }

    private fun lastLight(id: SessionId): GameSnapshot {
        val definition = LastLightDefinition()
        val state = definition.createInitialState(SessionConfig(
            id, LastLightIds.CaseId, LastLightIds.StandardModeId,
            players(definition.supportedPlayerCounts.first), SEED,
        ))
        val payload = definition.snapshotCodec().encode(state)
        check(definition.snapshotCodec().decode(payload) == state)
        return GameSnapshot(id, definition.id, LastLightSnapshotRecovery.VERSION,
            instant, state.phase.id, payload, mapOf("playMode" to "PassAndPlay"))
    }

    private fun mafia(id: SessionId): GameSnapshot {
        val definition = MafiaDefinition(Json)
        val initial = definition.createInitialState(SessionConfig(
            id, CaseId("default"), MafiaIds.ClassicModeId, players(ORIGINAL_GAME_PLAYERS), SEED,
        ))
        val state = definition.reducer().reduce(initial, MafiaAction.ConfigureAndStart(initial.public.settings),
            DefaultReducerContext(FakeClock(instant), RandomSource.seeded(SEED))).newState
        check(state.phase == MafiaPhase.RoleAssignment)
        // The shipping codec validates both observable and private recovery invariants.
        val payload = definition.snapshotCodec().encode(state)
        check(definition.snapshotCodec().decode(payload) == state)
        // Matches the game-owned MAFIA_SNAPSHOT_VERSION; not its wire GAME_VERSION.
        return GameSnapshot(id, definition.id, SemVer(1, 0, 0),
            instant, state.phase.id, payload, mapOf("playMode" to "PassAndPlay"))
    }

    private suspend fun whodunit(id: SessionId, repository: CaseRepository): GameSnapshot {
        val definition = WhodunitDefinition(Json)
        val loaded = repository.loadCase(CaseId("last-dinner"), WhodunitPayloadValidator(Json))
        check(loaded is Result.Success) { "The shipping bundled case must validate" }
        val case = loaded.data
        val initial = definition.createInitialState(SessionConfig(
            id, CaseId(case.envelope.caseId), WhodunitIds.ClassicVoteModeId, players(ORIGINAL_GAME_PLAYERS), SEED,
        ))
        val state = definition.reducer().reduce(initial, WhodunitAction.AssignRoles(SEED),
            WhodunitReducerContext(FakeClock(instant), RandomSource.seeded(SEED), case)).newState
        check(state.privatePerPlayer.size == ORIGINAL_GAME_PLAYERS &&
            state.hostOnly.seatToCharacter.size == ORIGINAL_GAME_PLAYERS)
        val payload = definition.snapshotCodec().encode(state)
        check(definition.snapshotCodec().decode(payload) == state)
        val identity = case.envelope.contentIdentity()
        // Exact current bundled identity, not a fabricated/legacy unmarked investigation.
        // Schema matches WHODUNIT_SNAPSHOT_ENGINE_VERSION, not the multiplayer wire version.
        return GameSnapshot(id, definition.id, SemVer(1, 2, 0), instant, state.phase.id, payload,
            mapOf("playMode" to "PassAndPlay", "caseVersion" to identity.version, "caseDigest" to identity.digest))
    }

    private fun players(count: Int): List<Player> = List(count) { seat ->
        Player(PlayerId("native-seat-$seat"), "SyntheticSeat$seat", seat)
    }
}
