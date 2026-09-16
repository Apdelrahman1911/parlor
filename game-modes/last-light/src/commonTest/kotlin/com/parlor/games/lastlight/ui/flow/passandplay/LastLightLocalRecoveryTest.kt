package com.parlor.games.lastlight.ui.flow.passandplay

import com.parlor.core.ids.SessionId
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.result.Result
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotWriteStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightLocalRecoveryTest {
    @Test
    fun restored_match_keeps_every_future_deal_penalty_and_winner_identical() = runTest {
        val original = LastLightLocalFixture(backgroundScope)
        original.submitNextLegalAction()
        runCurrent()
        val loaded = LastLightSnapshotRecovery.load(original.store, original.definition, original.config.sessionId)
        assertTrue(loaded is Result.Success)
        val recovered = LastLightLocalFixture(
            scope = backgroundScope,
            store = InMemorySnapshotStore(),
            config = createLocalLastLightSessionConfig(
                players = loaded.data.state.players,
                randomSeed = loaded.data.state.hostOnly.randomSeed,
                restoredSessionId = loaded.data.sessionId,
                sessionIdGenerator = { error("Recovery must retain its session identity") },
            ),
            restoredState = loaded.data.state,
        )
        assertTrue(recovered.runtime.presentation.value.game.yourHand.isEmpty())
        var actions = 0
        while (original.raw.currentState().phase != GamePhase.FINISHED) {
            assertEquals(original.raw.currentState(), recovered.raw.currentState())
            original.submitNextLegalAction()
            recovered.submitNextLegalAction()
            runCurrent()
            actions++
            assertTrue(actions < 150, "Immediate challenges must finish within the rules bound")
        }
        assertEquals(original.raw.currentState(), recovered.raw.currentState())
        assertEquals(SnapshotWriteStatus.Deleted, recovered.runtime.persistenceStatus.value)
        assertTrue(recovered.store.load(recovered.config.sessionId) is Result.Failure)
    }

    @Test
    fun failed_terminal_deletion_blocks_return_and_retry_cannot_resurrect_the_saved_match() = runTest {
        val store = FailingLocalSnapshotStore().also { it.failDelete = true }
        val fixture = LastLightLocalFixture(backgroundScope, store)
        var actions = 0
        while (fixture.raw.currentState().phase != GamePhase.FINISHED) {
            fixture.submitNextLegalAction()
            runCurrent()
            assertTrue(++actions < 150)
        }
        assertTrue(fixture.runtime.persistenceStatus.value is SnapshotWriteStatus.Failed)
        var returns = 0
        assertTrue(fixture.runtime.returnToSetup { returns++ })
        assertFalse(fixture.runtime.returnToSetup { returns++ })
        runCurrent()
        assertEquals(0, returns)
        assertTrue(store.load(fixture.config.sessionId) is Result.Success)

        store.failDelete = false
        assertTrue(fixture.runtime.returnToSetup { returns++ })
        runCurrent()
        assertEquals(1, returns)
        assertFalse(fixture.runtime.nextRound())
        fixture.runtime.dispose()
        runCurrent()
        assertTrue(store.load(fixture.config.sessionId) is Result.Failure)
        assertEquals(SnapshotWriteStatus.Deleted, fixture.runtime.persistenceStatus.value)
    }

    @Test
    fun rematch_uses_a_new_identity_and_secret_seed_and_old_session_cannot_modify_it() = runTest {
        val store = InMemorySnapshotStore()
        var seedCalls = 0
        val seeds = SessionSeedSource { 41L + seedCalls++ }
        var identityCalls = 0
        val identity = { "independent-session-${identityCalls++}" }
        val players = lastLightLocalPlayers(listOf("Ali", "Zoë 🎲"))
        val first = LastLightLocalFixture(
            backgroundScope,
            store,
            createLocalLastLightSessionConfig(players, seeds.nextSeed(), sessionIdGenerator = identity),
        )
        runCurrent()
        var actions = 0
        while (first.raw.currentState().phase != GamePhase.FINISHED) {
            first.submitNextLegalAction()
            runCurrent()
            assertTrue(++actions < 80)
        }
        var returnedToSetup = false
        assertTrue(first.runtime.returnToSetup { returnedToSetup = true })
        runCurrent()
        assertTrue(returnedToSetup)
        val second = LastLightLocalFixture(
            backgroundScope,
            store,
            createLocalLastLightSessionConfig(players, seeds.nextSeed(), sessionIdGenerator = identity),
        )
        val secondBefore = second.raw.currentState()
        assertNotEquals(first.config.sessionId, second.config.sessionId)
        assertNotEquals(first.config.randomSeed, second.config.randomSeed)
        assertEquals(2, seedCalls)
        assertFalse(first.runtime.takeDevice())
        assertFalse(first.runtime.nextRound())
        runCurrent()
        assertEquals(secondBefore, second.raw.currentState())
        assertTrue(second.store.load(second.config.sessionId) is Result.Success)
        assertTrue(second.store.load(first.config.sessionId) is Result.Failure)
    }

    @Test
    fun local_names_use_exact_canonical_parlor_labels_and_validate_player_count() {
        val players = lastLightLocalPlayers(listOf("  Alice  ", "alice", "عبد الرحمن", "Zoë 🎲"))
        assertEquals(listOf("Alice", "alice", "عبد الرحمن", "Zoë 🎲"), players.map { it.displayName })
        assertEquals(listOf(0, 1, 2, 3), players.map { it.seat })
        assertFailsWith<IllegalArgumentException> { lastLightLocalPlayers(listOf("Alice", " Alice ")) }
        assertFailsWith<IllegalArgumentException> { lastLightLocalPlayers(listOf("Alice")) }
        assertFailsWith<IllegalArgumentException> { lastLightLocalPlayers((1..7).map { "Player $it" }) }
        assertFailsWith<IllegalArgumentException> { lastLightLocalPlayers(listOf("Alice", "Bob\nAdmin")) }
    }

    @Test
    fun recovering_never_allocates_a_new_identity_or_embeds_entropy_in_the_id() {
        val id = SessionId("saved-session")
        val config = createLocalLastLightSessionConfig(
            players = lastLightLocalPlayers(listOf("A", "B")),
            randomSeed = 982_451_653L,
            restoredSessionId = id,
            sessionIdGenerator = { error("Must not allocate on recovery") },
        )
        assertEquals(id, config.sessionId)
        assertFalse(config.sessionId.raw.contains(config.randomSeed.toString()))
    }
}
