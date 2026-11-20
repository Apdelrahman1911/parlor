package com.parlor.app.storage

import android.test.InstrumentationTestCase
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.storage.settings.PersistentSettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Instant

/**
 * Real Android Keystore/files/preferences, not JVM stubs. These run beside the
 * API34 Debug navigator tests; the optimized API35 Release smoke stays intact.
 * Every record is synthetic and lives in an exclusively allocated namespace.
 * Reconstructing stores is not evidence of process death, UI or physical locks.
 */
@Suppress("DEPRECATION")
class AndroidResumeStorageRuntimeTest : InstrumentationTestCase() {
    fun testSelectedDeletionSurvivesReconstructionForOriginalGames() = withFixture { fixture ->
        for (game in listOf("last-light", "mafia", "whodunit")) {
            val selected = snapshot("selected-$game", game)
            val sibling = snapshot("sibling-$game", game)
            val store = fixture.snapshots()
            assertEquals(EmptyOk, store.save(selected))
            assertEquals(EmptyOk, store.save(sibling))
            assertEquals(EmptyOk, fixture.credentials().put("unrelated", CREDENTIAL))
            fixture.legacyFile(selected).writeBytes(encode(selected))

            assertEquals(EmptyOk, store.delete(selected.sessionId))
            assertFalse(fixture.snapshotFile(selected).exists())
            assertFalse(fixture.legacyFile(selected).exists())
            val reconstructed = fixture.snapshots()
            assertEquals(Result.Failure(DataError.NotFound), reconstructed.load(selected.sessionId))
            assertEquals(Result.Success(sibling), reconstructed.load(sibling.sessionId))
            assertFalse(requireSuccess(reconstructed.listUnfinished()).contains(selected.sessionId))
            assertTrue(requireSuccess(fixture.credentials().get("unrelated"))!!.contentEquals(CREDENTIAL))
            assertEquals(EmptyOk, reconstructed.delete(selected.sessionId))
        }
    }

    fun testLegacyInventoryMigratesBeforeReturningHealthySaves() = withFixture { fixture ->
        val snapshots = listOf(snapshot("legacy-one"), snapshot("legacy-two"))
        snapshots.forEach { fixture.legacyFile(it).writeBytes(encode(it)) }

        assertEquals(snapshots.map { it.sessionId }, requireSuccess(fixture.snapshots().listUnfinished()))
        for (snapshot in snapshots) {
            assertFalse(fixture.legacyFile(snapshot).exists())
            val encrypted = fixture.snapshotFile(snapshot).readBytes()
            assertTrue(encrypted.copyOfRange(0, SNAPSHOT_MAGIC.length).contentEquals(SNAPSHOT_MAGIC.encodeToByteArray()))
            assertFalse(encrypted.contentEquals(encode(snapshot)))
            assertEquals(Result.Success(snapshot), fixture.snapshots().load(snapshot.sessionId))
        }
    }

    fun testSnapshotAuthenticationRejectsSwapsAndTamperingWithoutDeletingEvidence() = withFixture { fixture ->
        val selected = snapshot("selected")
        val sibling = snapshot("sibling")
        val store = fixture.snapshots()
        assertEquals(EmptyOk, store.save(selected))
        assertEquals(EmptyOk, store.save(sibling))
        val original = fixture.snapshotFile(selected).readBytes()
        fixture.snapshotFile(selected).writeBytes(fixture.snapshotFile(sibling).readBytes())
        assertEquals(Result.Failure(DataError.CorruptedData), fixture.snapshots().load(selected.sessionId))
        assertEquals(Result.Success(sibling), fixture.snapshots().load(sibling.sessionId))

        original[original.lastIndex] = (original.last().toInt() xor 1).toByte()
        fixture.snapshotFile(selected).writeBytes(original)
        assertEquals(Result.Failure(DataError.CorruptedData), fixture.snapshots().load(selected.sessionId))
        assertTrue(fixture.snapshotFile(selected).readBytes().contentEquals(original))
        assertEquals(EmptyOk, fixture.snapshots().delete(selected.sessionId))
        assertEquals(Result.Success(sibling), fixture.snapshots().load(sibling.sessionId))
    }

    fun testCredentialDeletionPreservesSnapshotsAndOtherCredentials() = withFixture { fixture ->
        val snapshot = snapshot("unrelated")
        val secure = fixture.credentials()
        assertEquals(EmptyOk, fixture.snapshots().save(snapshot))
        assertEquals(EmptyOk, secure.put("selected", CREDENTIAL))
        assertEquals(EmptyOk, secure.put("sibling", OTHER_CREDENTIAL))
        val firstCiphertext = fixture.credentialFile("selected").readBytes()
        assertFalse(firstCiphertext.contentEquals(CREDENTIAL))
        assertEquals(EmptyOk, secure.put("selected", CREDENTIAL))
        assertFalse(firstCiphertext.contentEquals(fixture.credentialFile("selected").readBytes()))
        assertTrue(requireSuccess(fixture.credentials().get("selected"))!!.contentEquals(CREDENTIAL))
        val pending = File(fixture.credentialFile("selected").parentFile, "selected.tmp")
        pending.writeBytes(SYNTHETIC_TEMP)

        assertEquals(EmptyOk, secure.remove("selected"))
        assertFalse(pending.exists())
        assertFalse(fixture.credentialFile("selected").exists())
        assertNull(requireSuccess(fixture.credentials().get("selected")))
        assertTrue(requireSuccess(fixture.credentials().get("sibling"))!!.contentEquals(OTHER_CREDENTIAL))
        assertEquals(Result.Success(snapshot), fixture.snapshots().load(snapshot.sessionId))
        assertEquals(EmptyOk, fixture.credentials().remove("selected"))
    }

    fun testCredentialAuthenticationFailureStaysOpaqueAndRetainsTheRecord() = withFixture { fixture ->
        assertEquals(EmptyOk, fixture.credentials().put("selected", CREDENTIAL))
        assertEquals(EmptyOk, fixture.credentials().put("sibling", OTHER_CREDENTIAL))
        val swapped = fixture.credentialFile("sibling").readBytes()
        fixture.credentialFile("selected").writeBytes(swapped)

        assertEquals(
            Result.Failure(DataError.IoError("secure_storage_io")),
            fixture.credentials().get("selected"),
        )
        assertTrue(fixture.credentialFile("selected").readBytes().contentEquals(swapped))
        assertTrue(requireSuccess(fixture.credentials().get("sibling"))!!.contentEquals(OTHER_CREDENTIAL))
        assertEquals(EmptyOk, fixture.credentials().remove("selected"))
    }

    fun testNativeDeleteFailurePreservesOtherSavesAndReportsUnavailableStorage() = withFixture { fixture ->
        val selected = snapshot("selected")
        val sibling = snapshot("sibling")
        assertEquals(EmptyOk, fixture.snapshots().save(selected))
        assertEquals(EmptyOk, fixture.snapshots().save(sibling))
        val blocked = fixture.snapshotFile(selected)
        assertTrue(blocked.delete())
        assertTrue(blocked.mkdir())
        File(blocked, "owned-child").writeBytes(SYNTHETIC_TEMP)

        assertEquals(Result.Failure(DataError.IoError("snapshot_io")), fixture.snapshots().delete(selected.sessionId))
        assertTrue(blocked.isDirectory)
        assertEquals(Result.Success(sibling), fixture.snapshots().load(sibling.sessionId))
    }

    fun testNativePreferencesRestoreEnglishArabicAndSystemWithoutTouchingSaves() = withFixture { fixture ->
        val snapshot = snapshot("settings-sibling")
        assertEquals(EmptyOk, fixture.snapshots().save(snapshot))
        val settings = PersistentSettingsStore(AndroidSettingsKeyValueBacking(fixture))
        for (language in listOf("en", "ar", null)) {
            settings.setLanguageOverride(language)
            settings.setThemeMode("dark")
            settings.setReducedMotion(true)
            val restored = PersistentSettingsStore(AndroidSettingsKeyValueBacking(fixture))
            assertEquals(language, restored.languageOverride.value)
            assertEquals("dark", restored.themeMode.value)
            assertTrue(restored.reducedMotion.value)
        }
        assertEquals(Result.Success(snapshot), fixture.snapshots().load(snapshot.sessionId))
    }

    private fun withFixture(block: suspend (AndroidStorageRuntimeFixture) -> Unit) = runBlocking {
        AndroidStorageRuntimeFixture(instrumentation.targetContext).use { fixture -> block(fixture) }
    }

    private fun snapshot(id: String, game: String = "whodunit") = GameSnapshot(
        sessionId = SessionId(id),
        gameId = GameId(game),
        engineVersion = SemVer.ZERO,
        createdAt = Instant.fromEpochMilliseconds(0),
        phaseId = "synthetic-runtime-fixture",
        payload = "synthetic non-player payload".encodeToByteArray(),
    )

    private fun encode(snapshot: GameSnapshot): ByteArray =
        Json.encodeToString(GameSnapshot.serializer(), snapshot).encodeToByteArray()

    private fun <T> requireSuccess(result: Result<T, DataError>): T {
        assertTrue("Expected a successful native storage operation", result is Result.Success)
        return (result as Result.Success).data
    }

    private companion object {
        const val SNAPSHOT_MAGIC = "PARSNAP"
        val CREDENTIAL = "synthetic selected credential".encodeToByteArray()
        val OTHER_CREDENTIAL = "synthetic unrelated credential".encodeToByteArray()
        val SYNTHETIC_TEMP = "synthetic temporary record".encodeToByteArray()
    }
}
