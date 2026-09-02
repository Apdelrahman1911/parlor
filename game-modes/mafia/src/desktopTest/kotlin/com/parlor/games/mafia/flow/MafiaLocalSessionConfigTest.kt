package com.parlor.games.mafia.flow

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.ui.flow.passandplay.createLocalMafiaSessionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class MafiaLocalSessionConfigTest {
    private val players = (1..5).map { index ->
        Player(PlayerId("p$index"), "Player $index", index - 1)
    }

    @Test
    fun freshIdsUseIndependentEntropyRatherThanTheGameplaySeed() {
        val generatedIds = ArrayDeque(listOf("c".repeat(32), "d".repeat(32)))
        val first = freshConfig { generatedIds.removeFirst() }
        val second = freshConfig { generatedIds.removeFirst() }

        assertEquals(SEED, first.randomSeed)
        assertEquals(SEED, second.randomSeed)
        assertEquals(SessionId("c".repeat(32)), first.sessionId)
        assertEquals(SessionId("d".repeat(32)), second.sessionId)
        assertNotEquals(first.sessionId, second.sessionId)
        assertFalse(first.sessionId.raw.contains(SEED.toString(16)))
    }

    @Test
    fun resumePreservesThePersistedIdWithoutConsumingFreshEntropy() {
        val restored = SessionId("mafia-local-legacy-seed")
        var generatorCalled = false

        val config = createLocalMafiaSessionConfig(
            players = players,
            randomSeed = SEED,
            restoredSessionId = restored,
            sessionIdGenerator = {
                generatorCalled = true
                "unused"
            },
        )

        assertEquals(restored, config.sessionId)
        assertFalse(generatorCalled)
    }

    private fun freshConfig(idGenerator: () -> String) = createLocalMafiaSessionConfig(
        players = players,
        randomSeed = SEED,
        restoredSessionId = null,
        sessionIdGenerator = idGenerator,
    )

    private companion object {
        const val SEED = 0x1234_5678L
    }
}
