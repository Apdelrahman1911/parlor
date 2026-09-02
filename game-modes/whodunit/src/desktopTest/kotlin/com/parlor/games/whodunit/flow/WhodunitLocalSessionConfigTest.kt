package com.parlor.games.whodunit.flow

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.flow.createLocalWhodunitSessionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class WhodunitLocalSessionConfigTest {
    private val players = (1..4).map { index ->
        Player(PlayerId("p$index"), "Player $index", index - 1)
    }

    @Test
    fun freshIdsUseIndependentEntropyRatherThanTheGameplaySeed() {
        val generatedIds = ArrayDeque(listOf("a".repeat(32), "b".repeat(32)))
        val first = freshConfig { generatedIds.removeFirst() }
        val second = freshConfig { generatedIds.removeFirst() }

        assertEquals(SEED, first.randomSeed)
        assertEquals(SEED, second.randomSeed)
        assertEquals(SessionId("a".repeat(32)), first.sessionId)
        assertEquals(SessionId("b".repeat(32)), second.sessionId)
        assertNotEquals(first.sessionId, second.sessionId)
        assertFalse(first.sessionId.raw.contains(SEED.toString(16)))
    }

    @Test
    fun resumePreservesThePersistedIdWithoutConsumingFreshEntropy() {
        val restored = SessionId("legacy-seed-derived-id")
        var generatorCalled = false

        val config = createLocalWhodunitSessionConfig(
            caseId = CaseId("case"),
            modeId = WhodunitIds.ClassicVoteModeId,
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

    private fun freshConfig(idGenerator: () -> String) = createLocalWhodunitSessionConfig(
        caseId = CaseId("case"),
        modeId = WhodunitIds.ClassicVoteModeId,
        players = players,
        randomSeed = SEED,
        restoredSessionId = null,
        sessionIdGenerator = idGenerator,
    )

    private companion object {
        const val SEED = 0x1234_5678L
    }
}
