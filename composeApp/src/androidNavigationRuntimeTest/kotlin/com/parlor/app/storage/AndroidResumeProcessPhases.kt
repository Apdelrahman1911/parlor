package com.parlor.app.storage

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Process
import android.test.InstrumentationTestCase
import android.test.InstrumentationTestRunner
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.storage.secure.SecureStorage
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import java.util.UUID

/**
 * Explicit two-invocation JUnit3 phases for the hosted-emulator process driver.
 * They deliberately do not use the discovery-only test* prefix: running both in
 * one Instrumentation invocation would NOT test process death. The driver must
 * select #prepare and #verify separately and prove a launched app is force-stopped
 * between them. The ordinary navigation/storage/Home methods remain mandatory.
 * No production route, substitute store, private log or global data reset.
 */
@Suppress("DEPRECATION")
class AndroidResumeProcessPhases : InstrumentationTestCase() {
    override fun setUp() {
        super.setUp()
        // Each phase is a cold, individually selected JUnit3 method. Join
        // Application.onCreate before touching the shipping Koin graph, rather
        // than depending on earlier tests or retrying partial registration.
        instrumentation.waitForIdleSync()
    }

    fun prepare() = runBlocking<Unit> {
        val fixture = fixture()
        val application = application()
        val koin = application.getKoin()
        val store = koin.get<SnapshotStore>()
        val settings = koin.get<SettingsStore>()
        val secure = koin.get<SecureStorage>()
        val marker = marker(application, fixture)
        check(marker.all.isEmpty()) { "Process fixture already exists; never adopt another invocation" }
        assertEquals(Result.Success(emptyList<SessionId>()), store.listUnfinished())
        assertEquals(Result.Success<ByteArray?>(null), secure.get(fixture.credentialKey))
        AndroidProcessCredentialFixture.requireNoMembership(secure)
        val previousLanguage = settings.languageOverride.first()
        val previousMotion = settings.reducedMotion.first()
        val issuedAt = System.currentTimeMillis()
        val repository = koin.get<CaseRepository>()
        val selected = AndroidLegalResumeFixtures.create(fixture.game, fixture.selected, repository)
        val sibling = AndroidLegalResumeFixtures.create(fixture.game, fixture.sibling, repository)
        val membership = credentials(fixture, issuedAt, sibling.gameId)
        settings.setLanguageOverride(fixture.language)
        settings.setReducedMotion(true)
        val activity = launchHome()
        try {
            val ui = AndroidPublicRecoveryUi(instrumentation)
            // A fresh process legitimately auto-navigates to a stored membership.
            // Make a real public navigation decision before staging this synthetic
            // storage-only fixture. Never override the startup gate or contact a
            // fabricated host just to get back to the Home deletion controls.
            ui.clickDescription(if (fixture.language == "ar") "افتح الإعدادات." else "Open settings.")
            // ACTION_CLICK only acknowledges dispatch. Both tabs already exist
            // on Home, so Settings -> Games can coalesce before a frame and
            // leave Home's recovery effect on the old, empty inventory. Observe
            // the real destination BEFORE writing external test fixtures.
            ui.awaitHeading(if (fixture.language == "ar") "الإعدادات" else "Settings")
            if (membership == null) assertEquals(EmptyOk, store.save(selected))
            assertEquals(EmptyOk, store.save(sibling))
            assertEquals(EmptyOk, secure.put(fixture.credentialKey, CREDENTIAL))
            membership?.seed()
            ui.clickDescription(if (fixture.language == "ar") "افتح مكتبة الألعاب." else "Open the game library.")
            // The local saved row proves Home has committed after Settings;
            // never scroll a stale Settings surface while navigation is pending.
            awaitPreparedHome(ui, fixture, sibling, store, settings)
            val selectedDescription = if (membership == null) fixture.discardDescription(total = 2)
                else fixture.multiplayerDescription
            ui.revealDescription(selectedDescription)
            ui.clickDescription(selectedDescription)
            ui.clickDescription(if (fixture.language == "ar") "الاحتفاظ بالجلسة" else "Keep session")
            ui.awaitDescription(selectedDescription)
            if (membership == null) assertEquals(Result.Success(selected), store.load(fixture.selected))
            membership?.assertSelectedPresent()
            assertEquals(Result.Success(sibling), store.load(fixture.sibling))
            ui.clickDescription(selectedDescription)
            ui.clickDescription(if (fixture.language == "ar") "حذف" else "Remove")
            if (membership == null) {
                ui.awaitDescription(fixture.discardDescription(total = 1))
            } else {
                // Wait for the destructive dialog to leave the active native
                // window before the single known backward page action.
                ui.awaitDescription(if (fixture.language == "ar") "افتح الإعدادات." else "Open settings.")
                ui.revealDescription(fixture.discardDescription(total = 1), forward = false)
            }
            assertEquals(Result.Failure(DataError.NotFound), store.load(fixture.selected))
            assertEquals(Result.Success(sibling), store.load(fixture.sibling))
            membership?.assertSelectedAbsent()
            // Public test bookkeeping only. Actual snapshots/credentials still
            // use the shipping encrypted stores and are never copied here.
            assertTrue(marker.edit()
                .putString("fixture", fixture.id)
                .putString("game", fixture.game.name)
                .putString("language", fixture.language)
                .putString("entryKind", fixture.kind.name)
                .putLong("issuedAt", issuedAt)
                .putString("preparedProcess", PROCESS_NONCE)
                .putBoolean("previousLanguageWasSystem", previousLanguage == null)
                .putString("previousLanguage", previousLanguage)
                .putBoolean("previousMotion", previousMotion)
                .commit())
            report(fixture, "prepared")
        } finally {
            finish(activity)
        }
    }

    private suspend fun awaitPreparedHome(
        ui: AndroidPublicRecoveryUi,
        fixture: ProcessResumeFixture,
        sibling: GameSnapshot,
        store: SnapshotStore,
        settings: SettingsStore,
    ) {
        val total = if (fixture.kind == ResumeEntryKind.Local) 2 else 1
        try {
            ui.awaitDescription(fixture.discardDescription(total))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalStateException) {
            // Only whitelisted public alternatives and booleans after failure.
            // Never export an AX tree, screenshot, player, snapshot or credential.
            val englishLocal = ui.descriptionPresentNow(fixture.discardDescription(total, "en"))
            val arabicLocal = ui.descriptionPresentNow(fixture.discardDescription(total, "ar"))
            val englishSettings = ui.headingPresentNow("Settings")
            val arabicSettings = ui.headingPresentNow("الإعدادات")
            val languageMatches = settings.languageOverride.first() == fixture.language
            val siblingIntact = store.load(sibling.sessionId) == Result.Success(sibling)
            throw AssertionError("Prepared Home after acknowledged Settings: " +
                "englishLocal=$englishLocal, arabicLocal=$arabicLocal, " +
                "englishSettings=$englishSettings, arabicSettings=$arabicSettings, " +
                "languageMatches=$languageMatches, siblingIntact=$siblingIntact", failure)
        }
    }

    fun verify() = runBlocking<Unit> {
        val fixture = fixture()
        val application = application()
        val marker = marker(application, fixture)
        assertEquals(fixture.id, marker.getString("fixture", null))
        assertEquals(fixture.game.name, marker.getString("game", null))
        assertEquals(fixture.language, marker.getString("language", null))
        assertEquals(fixture.kind.name, marker.getString("entryKind", null))
        val issuedAt = marker.getLong("issuedAt", 0L)
        check(issuedAt > 0L)
        val originalProcess = checkNotNull(marker.getString("preparedProcess", null))
        check(UUID.fromString(originalProcess).toString() == originalProcess)
        check(originalProcess != PROCESS_NONCE) { "Both phases ran in one process; no process acceptance" }
        val koin = application.getKoin()
        val store = koin.get<SnapshotStore>()
        val settings = koin.get<SettingsStore>()
        val secure = koin.get<SecureStorage>()
        val sibling = AndroidLegalResumeFixtures.create(fixture.game, fixture.sibling, koin.get<CaseRepository>())
        val membership = credentials(fixture, issuedAt, sibling.gameId)
        assertEquals(Result.Success(listOf(fixture.sibling)), store.listUnfinished())
        assertEquals(Result.Failure(DataError.NotFound), store.load(fixture.selected))
        assertEquals(Result.Success(sibling), store.load(fixture.sibling))
        membership?.assertSelectedAbsent()
        val credential = secure.get(fixture.credentialKey)
        assertTrue(credential is Result.Success && credential.data?.contentEquals(CREDENTIAL) == true)
        assertEquals(fixture.language, settings.languageOverride.first())
        assertTrue(settings.reducedMotion.first())
        val activity = launchHome()
        try {
            val ui = AndroidPublicRecoveryUi(instrumentation)
            ui.awaitDescription(fixture.discardDescription(total = 1))
        } finally {
            finish(activity)
        }
        // Retire only this UUID's records after all acceptance assertions pass.
        // A failed verification leaves its synthetic evidence, never auto-deletes it.
        assertEquals(EmptyOk, store.delete(fixture.sibling))
        assertEquals(EmptyOk, secure.remove(fixture.credentialKey))
        settings.setLanguageOverride(if (marker.getBoolean("previousLanguageWasSystem", false)) {
            null
        } else {
            checkNotNull(marker.getString("previousLanguage", null))
        })
        settings.setReducedMotion(marker.getBoolean("previousMotion", false))
        assertEquals(Result.Success(emptyList<SessionId>()), store.listUnfinished())
        assertTrue(application.deleteSharedPreferences(fixture.markerName))
        report(fixture, "verified")
    }

    private fun application(): Application = (instrumentation.targetContext.applicationContext as Application).also {
        check(it.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(it.packageName.endsWith(".debug")) { "Process acceptance never targets a Store install" }
    }

    private fun fixture(): ProcessResumeFixture {
        val arguments = (instrumentation as InstrumentationTestRunner).arguments
        val id = checkNotNull(arguments.getString("parlorFixtureId"))
        check(UUID.fromString(id).toString() == id)
        val language = checkNotNull(arguments.getString("parlorLanguage"))
        check(language == "en" || language == "ar")
        val game = AndroidResumeGame.valueOf(checkNotNull(arguments.getString("parlorGame")))
        val kind = ResumeEntryKind.valueOf(checkNotNull(arguments.getString("parlorEntryKind")))
        return ProcessResumeFixture(id, language, game, kind)
    }

    private fun credentials(
        fixture: ProcessResumeFixture,
        issuedAt: Long,
        gameId: GameId,
    ): AndroidProcessCredentialFixture? {
        if (fixture.kind != ResumeEntryKind.Multiplayer) return null
        val koin = application().getKoin()
        val contract = checkNotNull(koin.get<GameShellRegistry>().byId(gameId)?.multiplayerContract)
        return AndroidProcessCredentialFixture(fixture.id, issuedAt, contract, koin.get<SecureStorage>(), koin.get<RoomTransport>())
    }

    private fun marker(context: Context, fixture: ProcessResumeFixture): SharedPreferences =
        context.getSharedPreferences(fixture.markerName, Context.MODE_PRIVATE)

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

    private fun report(fixture: ProcessResumeFixture, phase: String) {
        // Public correlation only; no snapshot, player, key alias or credential.
        instrumentation.sendStatus(PHASE_STATUS, Bundle().apply {
            putString("parlorPhase", phase)
            putString("parlorFixtureId", fixture.id)
            putString("parlorLanguage", fixture.language)
            putString("parlorGame", fixture.game.name)
            putString("parlorEntryKind", fixture.kind.name)
            putString("parlorProcessNonce", PROCESS_NONCE)
            putInt("parlorPid", Process.myPid())
        })
    }

    private companion object {
        const val PHASE_STATUS = 2
        val PROCESS_NONCE = UUID.randomUUID().toString()
        val CREDENTIAL = "synthetic unrelated process credential".encodeToByteArray()
    }
}

private enum class ResumeEntryKind { Local, Multiplayer }

private data class ProcessResumeFixture(
    val id: String,
    val language: String,
    val game: AndroidResumeGame,
    val kind: ResumeEntryKind,
) {
    val selected = SessionId("native-process-$id-1")
    val sibling = SessionId("native-process-$id-2")
    val credentialKey = "native-process-$id"
    val markerName = "native-process-$id"
    val multiplayerDescription = if (language == "ar") {
        "حذف: العودة إلى اللعبة الجماعية السابقة."
    } else {
        "Remove: Rejoin the previous multiplayer game."
    }

    fun discardDescription(total: Int, language: String = this.language): String = if (language == "ar") {
        "حذف: استئناف لعبة ${game.arabicTitle} المحلية، وهي اللعبة المحفوظة 1 من $total."
    } else {
        "Remove: Resume ${game.englishTitle} local game, saved game 1 of $total."
    }
}
