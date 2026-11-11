package com.parlor.networking.transport

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundJoinDefaultTest {
    @Test
    fun unsupported_game_bound_join_never_falls_back_to_unbound_admission() = runTest {
        val transport = LegacyJoinTransport()
        val config = JoinConfig(
            code = "ABCDEF",
            displayName = "Fixture peer",
            expectedGameProtocol = HostedGameProtocol(GameId("fixture-game"), 1),
        )

        val result = transport.join(config)

        assertTrue(transport.joins.isEmpty())
        assertEquals(Result.Failure(NetError.IncompatibleProtocol), result)
    }

    @Test
    fun game_agnostic_join_preserves_legacy_arguments_and_result() = runTest {
        val transport = LegacyJoinTransport()
        val config = JoinConfig(code = "ABCDEF", displayName = "Fixture peer")

        val result = transport.join(config)

        assertEquals(listOf(config), transport.joins)
        assertEquals(Result.Failure(NetError.RoomFull), result)
    }

    @Test
    fun binding_aware_override_receives_the_complete_selected_game_config() = runTest {
        val legacy = LegacyJoinTransport()
        val configuredJoins = mutableListOf<JoinConfig>()
        val transport: RoomTransport = object : RoomTransport by legacy {
            override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> {
                configuredJoins += config
                return Result.Failure(NetError.RoomFull)
            }
        }
        val config = JoinConfig(
            code = "ABCDEF",
            displayName = "Fixture peer",
            expectedGameProtocol = HostedGameProtocol(GameId("fixture-game"), 2),
        )

        assertEquals(Result.Failure(NetError.RoomFull), transport.join(config))
        assertEquals(listOf(config), configuredJoins)
        assertTrue(legacy.joins.isEmpty())
    }
}

private class LegacyJoinTransport : RoomTransport {
    val joins = mutableListOf<JoinConfig>()
    override val capability = TransportCapability(false, 1, 1024)

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = error("Unexpected host")

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        joins += JoinConfig(code, displayName)
        return Result.Failure(NetError.RoomFull)
    }
}
