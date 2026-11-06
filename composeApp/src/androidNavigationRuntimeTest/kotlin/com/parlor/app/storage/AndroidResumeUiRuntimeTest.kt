package com.parlor.app.storage

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.test.InstrumentationTestCase
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.Result
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.storage.secure.SecureStorage
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import java.util.UUID
import kotlin.time.Instant

/**
 * The shipping Activity, Koin graph, Compose accessibility actions and native
 * stores. No replaced composable, fake store, reset-app-data or test-only route.
 * Activity relaunch is intentionally NOT described as process-death recovery,
 * physical touch, TalkBack execution or positive iOS protection evidence.
 */
@Suppress("DEPRECATION")
class AndroidResumeUiRuntimeTest : InstrumentationTestCase() {
    fun testEnglishCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() = verifyJourney(arabic = false)

    fun testArabicCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() = verifyJourney(arabic = true)

    private fun verifyJourney(arabic: Boolean) = runBlocking<Unit> {
        val application = instrumentation.targetContext.applicationContext as Application
        val koin = application.getKoin()
        val store = koin.get<SnapshotStore>()
        val settings = koin.get<SettingsStore>()
        val secure = koin.get<SecureStorage>()
        // Fail rather than inspect/delete an existing user's saves. The API34
        // managed-device lane installs this Debug app into a fresh test device.
        assertEquals(Result.Success(emptyList<SessionId>()), store.listUnfinished())
        val previousLanguage = settings.languageOverride.first()
        val previousMotion = settings.reducedMotion.first()
        val id = UUID.randomUUID().toString()
        val selected = legalSnapshot(SessionId("native-home-$id-1"))
        val sibling = legalSnapshot(SessionId("native-home-$id-2"))
        val credentialKey = "native-home-$id"
        assertEquals(Result.Success<ByteArray?>(null), secure.get(credentialKey))
        var activity: Activity? = null
        try {
            settings.setLanguageOverride(if (arabic) "ar" else "en")
            settings.setReducedMotion(true)
            assertEquals(EmptyOk, store.save(selected))
            assertEquals(EmptyOk, store.save(sibling))
            assertEquals(EmptyOk, secure.put(credentialKey, SYNTHETIC_CREDENTIAL))
            assertTrue(LastLightSnapshotRecovery.load(store, LastLightDefinition(), selected.sessionId) is Result.Success)
            val ui = AndroidPublicRecoveryUi(instrumentation)
            activity = launchHome()
            ui.clickDescription(discardDescription(arabic, total = 2))
            ui.clickDescription(if (arabic) "الاحتفاظ بالجلسة" else "Keep session")
            ui.awaitDescription(discardDescription(arabic, total = 2))
            assertEquals(Result.Success(selected), store.load(selected.sessionId))
            assertEquals(Result.Success(sibling), store.load(sibling.sessionId))

            ui.clickDescription(discardDescription(arabic, total = 2))
            ui.clickDescription(if (arabic) "حذف" else "Remove")
            // The refreshed, enabled sibling action is the app's completion
            // signal, not a delay/repeated activation of the destructive action.
            ui.awaitDescription(discardDescription(arabic, total = 1))
            assertEquals(Result.Failure(DataError.NotFound), store.load(selected.sessionId))
            assertEquals(Result.Success(sibling), store.load(sibling.sessionId))
            finish(checkNotNull(activity))
            activity = null
            activity = launchHome()
            ui.awaitDescription(discardDescription(arabic, total = 1))
            assertEquals(Result.Success(listOf(sibling.sessionId)), store.listUnfinished())
            assertTrue(LastLightSnapshotRecovery.load(store, LastLightDefinition(), sibling.sessionId) is Result.Success)
            val credential = secure.get(credentialKey)
            assertTrue(credential is Result.Success && credential.data?.contentEquals(SYNTHETIC_CREDENTIAL) == true)
            assertEquals(if (arabic) "ar" else "en", settings.languageOverride.first())
        } finally {
            try {
                activity?.let(::finish)
            } finally {
                // Only names allocated above are ours. Do not reset shared
                // preferences, all snapshots, transport state or Keystore keys.
                assertEquals(EmptyOk, store.delete(selected.sessionId))
                assertEquals(EmptyOk, store.delete(sibling.sessionId))
                assertEquals(EmptyOk, secure.remove(credentialKey))
                settings.setLanguageOverride(previousLanguage)
                settings.setReducedMotion(previousMotion)
            }
        }
    }

    private fun launchHome(): Activity {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(instrumentation.targetContext.packageName, "com.parlor.app.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return instrumentation.startActivitySync(intent).also { instrumentation.waitForIdleSync() }
    }

    private fun finish(activity: Activity) {
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun legalSnapshot(sessionId: SessionId): GameSnapshot {
        val definition = LastLightDefinition()
        val config = SessionConfig(
            sessionId = sessionId,
            caseId = LastLightIds.CaseId,
            modeId = LastLightIds.StandardModeId,
            players = List(definition.supportedPlayerCounts.first) { seat ->
                Player(PlayerId("native-seat-$seat"), "SyntheticSeat$seat", seat)
            },
            randomSeed = SYNTHETIC_SEED,
        )
        val state = definition.createInitialState(config)
        return GameSnapshot(
            sessionId = sessionId,
            gameId = definition.id,
            engineVersion = LastLightSnapshotRecovery.VERSION,
            createdAt = Instant.fromEpochMilliseconds(0),
            phaseId = state.phase.id,
            payload = definition.snapshotCodec().encode(state),
            metadata = mapOf(LastLightSnapshotRecovery.PLAY_MODE_KEY to LastLightSnapshotRecovery.PASS_AND_PLAY_MODE),
        )
    }

    private fun discardDescription(arabic: Boolean, total: Int): String = if (arabic) {
        "حذف: استئناف لعبة الضوء الأخير المحلية، وهي اللعبة المحفوظة 1 من $total."
    } else {
        "Remove: Resume Last Light local game, saved game 1 of $total."
    }

    private companion object {
        const val SYNTHETIC_SEED = 7L
        val SYNTHETIC_CREDENTIAL = "synthetic unrelated native home credential".encodeToByteArray()
    }
}
