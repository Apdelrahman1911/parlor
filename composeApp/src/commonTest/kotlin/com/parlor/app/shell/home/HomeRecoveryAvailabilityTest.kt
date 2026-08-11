package com.parlor.app.shell.home

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.ResumableSessionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeRecoveryAvailabilityTest {
    private val resumable = ResumableSessionInfo(
        gameId = GameId("whodunit"),
        gameVersion = 1,
        displayName = "Player",
        expiresAtEpochMillis = 10_000,
    )

    @Test
    fun failedStoresAreReportedInsteadOfMasqueradingAsEmpty() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Failure(DataError.PermissionDenied),
            multiplayerResult = Result.Failure(NetError.SecureStorageUnavailable),
            supportsMultiplayerResume = { true },
        )

        assertTrue(state.hasUnavailableSource)
        assertTrue(state.unfinishedSessions.isEmpty())
        assertNull(state.resumableMultiplayer)
    }

    @Test
    fun successfulSourceRemainsAvailableWhenTheOtherSourceFails() {
        val session = SessionId("local-session")
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(listOf(session)),
            multiplayerResult = Result.Failure(NetError.SecureStorageUnavailable),
            supportsMultiplayerResume = { true },
        )

        assertEquals(listOf(session), state.unfinishedSessions)
        assertNull(state.resumableMultiplayer)
        assertTrue(state.hasUnavailableSource)
    }

    @Test
    fun compatibleRecoveryRecordsAreExposedWithoutAnError() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(emptyList()),
            multiplayerResult = Result.Success(resumable),
            supportsMultiplayerResume = { it.gameId == GameId("whodunit") },
        )

        assertEquals(resumable, state.resumableMultiplayer)
        assertFalse(state.hasUnavailableSource)
    }

    @Test
    fun unsupportedSavedMultiplayerRecordIsReportedAndCannotBeOpened() {
        val state = resolveHomeRecoveryAvailability(
            localResult = Result.Success(emptyList()),
            multiplayerResult = Result.Success(resumable),
            supportsMultiplayerResume = { false },
        )

        assertNull(state.resumableMultiplayer)
        assertTrue(state.hasUnavailableSource)
    }
}
