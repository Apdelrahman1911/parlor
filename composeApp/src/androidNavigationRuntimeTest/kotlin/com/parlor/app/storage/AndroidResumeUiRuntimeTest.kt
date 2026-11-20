package com.parlor.app.storage

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.LocaleList
import android.test.InstrumentationTestCase
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.Result
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.snapshot.LastLightSnapshotRecovery
import com.parlor.storage.secure.SecureStorage
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import java.util.UUID
import java.util.Locale
import java.io.File

/**
 * The shipping Activity, Koin graph, Compose accessibility actions and native
 * stores. No replaced composable, fake store, reset-app-data or test-only route.
 * Activity relaunch is intentionally NOT described as process-death recovery,
 * physical touch, TalkBack execution or positive iOS protection evidence.
 */
@Suppress("DEPRECATION")
class AndroidResumeUiRuntimeTest : InstrumentationTestCase() {
    override fun setUp() {
        super.setUp()
        // The framework JUnit3 runner starts its test thread from onCreate,
        // before ActivityThread calls Application.onCreate on the main thread.
        // Join that startup turn before resolving the real application graph;
        // do not rely on preceding tests, sleep, retry Koin, or replace modules.
        instrumentation.waitForIdleSync()
    }

    fun testEnglishCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() = verifyJourney(arabic = false)

    fun testArabicCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() = verifyJourney(arabic = true)

    fun testEnglishMafiaCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() =
        verifyJourney(arabic = false, game = AndroidResumeGame.Mafia)

    fun testArabicMafiaCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() =
        verifyJourney(arabic = true, game = AndroidResumeGame.Mafia)

    fun testEnglishWhodunitCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() =
        verifyJourney(arabic = false, game = AndroidResumeGame.Whodunit)

    fun testArabicWhodunitCancelAndDiscardThroughNativeHomeSurviveActivityRelaunch() =
        verifyJourney(arabic = true, game = AndroidResumeGame.Whodunit)

    fun testEnglishNativeDeletionFailureIsAccessibleAndKeepsBothSaves() =
        verifyJourney(arabic = false, injectDeleteFailure = true)

    fun testArabicNativeDeletionFailureIsAccessibleAndKeepsBothSaves() =
        verifyJourney(arabic = true, injectDeleteFailure = true)

    private fun verifyJourney(
        arabic: Boolean,
        game: AndroidResumeGame = AndroidResumeGame.LastLight,
        injectDeleteFailure: Boolean = false,
    ) = runBlocking<Unit> {
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
        val repository = koin.get<CaseRepository>()
        val selected = AndroidLegalResumeFixtures.create(game, SessionId("native-home-$id-1"), repository)
        val sibling = AndroidLegalResumeFixtures.create(game, SessionId("native-home-$id-2"), repository)
        val credentialKey = "native-home-$id"
        assertEquals(Result.Success<ByteArray?>(null), secure.get(credentialKey))
        var activity: Activity? = null
        try {
            settings.setLanguageOverride(if (arabic) "ar" else "en")
            settings.setReducedMotion(true)
            assertEquals(EmptyOk, store.save(selected))
            assertEquals(EmptyOk, store.save(sibling))
            assertEquals(EmptyOk, secure.put(credentialKey, SYNTHETIC_CREDENTIAL))
            if (game == AndroidResumeGame.LastLight) {
                assertTrue(LastLightSnapshotRecovery.load(store, LastLightDefinition(), selected.sessionId) is Result.Success)
            }
            val ui = AndroidPublicRecoveryUi(instrumentation)
            activity = launchHome()
            ui.clickDescription(discardDescription(arabic, game, total = 2))
            ui.clickDescription(if (arabic) "الاحتفاظ بالجلسة" else "Keep session")
            ui.awaitDescription(discardDescription(arabic, game, total = 2))
            assertEquals(Result.Success(selected), store.load(selected.sessionId))
            assertEquals(Result.Success(sibling), store.load(sibling.sessionId))

            if (injectDeleteFailure) {
                val record = File(File(application.noBackupFilesDir, "snapshots"),
                    selected.sessionId.raw + FileBackedSnapshotStore.SUFFIX)
                AndroidSelectedSnapshotDeleteFault(record).use { fault ->
                    fault.install()
                    ui.clickDescription(discardDescription(arabic, game, total = 2))
                    ui.clickDescription(if (arabic) "حذف" else "Remove")
                    ui.awaitPoliteStatus(if (arabic) {
                        "تعذّر حذف الجلسة. ربما تغيّرت أو تعذّر الوصول إلى التخزين. احتفظ بها وحدّث القائمة قبل المحاولة مجددًا."
                    } else {
                        "Could not remove this session. It may have changed or storage may be unavailable. " +
                            "Keep it and refresh the list before trying again."
                    })
                    assertTrue(record.isDirectory)
                    assertEquals(Result.Success(sibling), store.load(sibling.sessionId))
                    // Remove the injected fault explicitly. Never replay a failed
                    // tap or increase a timeout to hide the observed error.
                    fault.restore()
                    assertEquals(Result.Success(selected), store.load(selected.sessionId))
                    ui.clickDescription(if (arabic) "الاحتفاظ بالجلسة" else "Keep session")
                    ui.awaitDescription(discardDescription(arabic, game, total = 2))
                }
            }

            ui.clickDescription(discardDescription(arabic, game, total = 2))
            ui.clickDescription(if (arabic) "حذف" else "Remove")
            // The refreshed, enabled sibling action is the app's completion
            // signal, not a delay/repeated activation of the destructive action.
            ui.awaitDescription(discardDescription(arabic, game, total = 1))
            assertEquals(Result.Failure(DataError.NotFound), store.load(selected.sessionId))
            assertEquals(Result.Success(sibling), store.load(sibling.sessionId))
            val previousActivity = checkNotNull(activity)
            finish(previousActivity)
            val destroyedBeforeRelaunch = previousActivity.isDestroyed
            val languageBeforeRelaunch = publicLanguage(Locale.getDefault().language)
            activity = null
            activity = launchHome()
            try {
                ui.awaitDescription(discardDescription(arabic, game, total = 1))
            } catch (failure: IllegalStateException) {
                // Exact public alternatives and booleans only. No tree, screenshot,
                // player, snapshot bytes, credential, or arbitrary exception export.
                val englishPresent = ui.descriptionPresentNow(discardDescription(false, game, total = 1))
                val arabicPresent = ui.descriptionPresentNow(discardDescription(true, game, total = 1))
                val preferenceMatches = settings.languageOverride.first() == if (arabic) "ar" else "en"
                val selectedAbsent = store.load(selected.sessionId) == Result.Failure(DataError.NotFound)
                val siblingIntact = store.load(sibling.sessionId) == Result.Success(sibling)
                var platform = "not-observed"
                val relaunched = checkNotNull(activity)
                instrumentation.runOnMainSync {
                    platform = "default=${publicLanguage(Locale.getDefault().language)}, " +
                        "defaultList=${publicLanguage(LocaleList.getDefault()[0].language)}, " +
                        "compose=${publicLanguage(androidx.compose.ui.text.intl.Locale.current.language)}, " +
                        "activity=${publicLanguage(relaunched.resources.configuration.locales[0].language)}, " +
                        "oldDestroyed=$destroyedBeforeRelaunch/${previousActivity.isDestroyed}, " +
                        "before=$languageBeforeRelaunch, " +
                        "focused=${relaunched.hasWindowFocus()}, destroyed=${relaunched.isDestroyed}"
                }
                throw AssertionError("Relaunch boundary: english=$englishPresent, arabic=$arabicPresent, " +
                    "preferenceMatches=$preferenceMatches, selectedAbsent=$selectedAbsent, siblingIntact=$siblingIntact; " +
                    platform, failure)
            }
            assertEquals(Result.Success(listOf(sibling.sessionId)), store.listUnfinished())
            assertEquals(Result.Success(sibling), store.load(sibling.sessionId))
            if (game == AndroidResumeGame.LastLight) {
                assertTrue(LastLightSnapshotRecovery.load(store, LastLightDefinition(), sibling.sessionId) is Result.Success)
            }
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

    private fun discardDescription(arabic: Boolean, game: AndroidResumeGame, total: Int): String = if (arabic) {
        "حذف: استئناف لعبة ${game.arabicTitle} المحلية، وهي اللعبة المحفوظة 1 من $total."
    } else {
        "Remove: Resume ${game.englishTitle} local game, saved game 1 of $total."
    }

    private fun publicLanguage(language: String): String = when (language) {
        "en", "ar" -> language
        else -> "other"
    }

    private companion object {
        val SYNTHETIC_CREDENTIAL = "synthetic unrelated native home credential".encodeToByteArray()
    }
}
