package com.parlor.networking.transport

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundResumeDefaultTest {
    @Test
    fun unsupported_selected_resume_never_falls_back_to_unbound_resume_or_metadata_preflight() = runTest {
        val transport = object : RoomTransport {
            override val capability = TransportCapability(false, 1, 1024)
            override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = error("Unexpected host")
            override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = error("Unexpected join")
            override suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> = error("Unsafe metadata preflight")
            override suspend fun resumeLastSession(): Result<LocalRoom, NetError> = error("Unbound resume loses selection")
        }
        assertEquals(Result.Failure(NetError.IncompatibleProtocol), transport.resumeLastSession(
            HostedGameProtocol(GameId("fixture-game"), 1),
        ))
    }
}
