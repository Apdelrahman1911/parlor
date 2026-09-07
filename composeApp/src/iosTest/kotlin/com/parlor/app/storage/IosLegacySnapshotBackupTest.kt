@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Native Foundation checks using only UUID-named synthetic test records; no Keychain dependency. */
class IosLegacySnapshotBackupTest {
    @Test
    fun coldInventoryProtectsMalformedLegacyWithoutDestroyingItsLastCopy(): Unit = runBlocking {
        val plaintext = malformedLegacy()
        withSyntheticRecord(plaintext) { record ->
            val filesystem = IosSnapshotFileSystem()
            assertTrue(record.name in filesystem.list())
            assertTrue(isExcluded(record.legacyDirectory))
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
        }
    }

    @Test
    fun directReadProtectsMalformedLegacyWithoutPriorInventory(): Unit = runBlocking {
        val plaintext = malformedLegacy()
        withSyntheticRecord(plaintext) { record ->
            assertFailsWith<SnapshotProtectionException> {
                IosSnapshotFileSystem().read(record.name)
            }
            assertTrue(isExcluded(record.legacyDirectory))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
        }
    }

    @Test
    fun damagedCurrentHeaderKeepsPrecedenceAndExcludesRetainedLegacy(): Unit = runBlocking {
        val plaintext = "{\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()
        val damagedCurrent = "DAMAGED-PROTECTED-HEADER".encodeToByteArray()
        withSyntheticRecord(plaintext, damagedCurrent) { record ->
            val filesystem = IosSnapshotFileSystem()
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertTrue(record.name in filesystem.list())
            assertTrue(isExcluded(record.legacyDirectory))
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertContentEquals(damagedCurrent, readBoundedSnapshotBytes(record.protectedPath, 1024))
        }
    }

    @Test
    fun oversizedLegacyIsExcludedBeforeBoundedReadRejectsIt(): Unit = runBlocking {
        val plaintext = ByteArray(MAX_PLAINTEXT_SNAPSHOT_BYTES + 1) { '!'.code.toByte() }
        withSyntheticRecord(plaintext) { record ->
            assertFailsWith<SnapshotProtectionException> {
                IosSnapshotFileSystem().read(record.name)
            }
            assertTrue(isExcluded(record.legacyDirectory))
            assertTrue(
                plaintext.contentEquals(readBoundedSnapshotBytes(record.legacyPath, plaintext.size)),
                "Oversized synthetic legacy bytes must remain unchanged",
            )
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
        }
    }

    @Test
    fun exclusionFailureIsNotSwallowedByInventoryAndDoesNotPreventExplicitDiscard(): Unit = runBlocking {
        val plaintext = "{\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()
        withSyntheticRecord(plaintext) { record ->
            val failure = IllegalStateException("synthetic backup-exclusion failure")
            var legacyAttempts = 0
            val filesystem = IosSnapshotFileSystem(
                backupExcluder = { url ->
                    if (url.path == record.legacyDirectory) {
                        legacyAttempts++
                        throw failure
                    }
                    excludeSnapshotFromBackup(url)
                },
            )
            assertSame(failure, assertFailsWith<IllegalStateException> { filesystem.list() })
            assertSame(failure, assertFailsWith<IllegalStateException> { filesystem.read(record.name) })
            assertEquals(2, legacyAttempts)
            assertContentEquals(plaintext, readBoundedSnapshotBytes(record.legacyPath, 1024))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))

            filesystem.delete(record.name)
            assertFalse(fileManager.fileExistsAtPath(record.legacyPath))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
            assertEquals(2, legacyAttempts, "Discard must not retry failing backup protection")
        }
    }

    @Test
    fun retryReappliesLegacyExclusionInsteadOfCachingIt(): Unit = runBlocking {
        withSyntheticRecord(malformedLegacy()) { record ->
            var legacyAttempts = 0
            val filesystem = IosSnapshotFileSystem(
                backupExcluder = { url ->
                    if (url.path == record.legacyDirectory) legacyAttempts++
                    excludeSnapshotFromBackup(url)
                },
            )
            repeat(2) {
                assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            }
            assertEquals(2, legacyAttempts)
            assertTrue(isExcluded(record.legacyDirectory))
        }
    }

    @Test
    fun nativeExclusionRejectsMissingPathsRatherThanClaimingProtection() {
        val path = "${NSTemporaryDirectory()}parlor-absent-${NSUUID.UUID().UUIDString}"
        assertFalse(fileManager.fileExistsAtPath(path))
        assertFailsWith<IllegalStateException> {
            excludeSnapshotFromBackup(NSURL.fileURLWithPath(path, isDirectory = true))
        }
        assertFalse(fileManager.fileExistsAtPath(path))
    }

    @Test
    fun nativeExclusionIsReappliedAfterSyntheticDirectoryRecreation() {
        val path = "${NSTemporaryDirectory()}parlor-exclusion-${NSUUID.UUID().UUIDString}"
        val url = NSURL.fileURLWithPath(path, isDirectory = true)
        assertFalse(fileManager.fileExistsAtPath(path))
        try {
            repeat(2) {
                check(fileManager.createDirectoryAtPath(path, false, null, null))
                excludeSnapshotFromBackup(url)
                assertTrue(isExcluded(path))
                check(fileManager.removeItemAtPath(path, null))
            }
        } finally {
            removeOwnFileIfPresent(path)
        }
    }

    @Test
    fun recognizedDamagedCurrentRetainsValidLegacyDuringColdInventoryAndRetry(): Unit = runBlocking {
        withValidCopies(protect = { _, _ -> "PARSNAP".encodeToByteArray() + byteArrayOf(0, 16) }) { record, saved ->
            val filesystem = IosSnapshotFileSystem()
            val store = FileBackedSnapshotStore(filesystem, Json)
            assertTrue(saved.sessionId in (store.listUnfinished() as Result.Success).data)
            assertEquals(Result.Failure(DataError.CorruptedData), store.load(saved.sessionId))
            assertEquals(Result.Failure(DataError.CorruptedData), store.loadMetadata(saved.sessionId))
            assertValidRetainedCopy(record, saved)
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertValidRetainedCopy(record, saved)
        }
    }

    @Test
    fun missingKeyKeepsValidLegacyButNeverFallsBackToIt(): Unit = runBlocking {
        withValidCopies { record, saved ->
            val filesystem = IosSnapshotFileSystem(snapshotKeyReader = { null })
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertValidRetainedCopy(record, saved)
        }
    }

    @Test
    fun keyReadFailureKeepsValidLegacyAndRemainsAnError(): Unit = runBlocking {
        withValidCopies { record, saved ->
            val failure = IllegalStateException("synthetic key unavailable")
            val filesystem = IosSnapshotFileSystem(snapshotKeyReader = { throw failure })
            val actual = assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertSame(failure, actual.cause)
            assertValidRetainedCopy(record, saved)
        }
    }

    @Test
    fun failedAuthenticationRetainsValidLegacyAcrossInventoryAndExplicitDiscard(): Unit = runBlocking {
        withValidCopies(protect = { name, bytes ->
            protectSyntheticSnapshot(name, bytes).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        }) { record, saved ->
            val filesystem = IosSnapshotFileSystem(snapshotKeyReader = ::syntheticKey)
            assertFailsWith<SnapshotProtectionException> { filesystem.read(record.name) }
            assertTrue(record.name in filesystem.list())
            assertValidRetainedCopy(record, saved)
            assertEquals(Result.Success(Unit), FileBackedSnapshotStore(filesystem, Json).delete(saved.sessionId))
            assertFalse(fileManager.fileExistsAtPath(record.legacyPath))
            assertFalse(fileManager.fileExistsAtPath(record.protectedPath))
        }
    }

    @Test
    fun cancelledKeyReadPreservesBothCopiesAndOriginalCancellation(): Unit = runBlocking {
        withValidCopies { record, saved ->
            val cancelled = CancellationException("synthetic read cancellation")
            val filesystem = IosSnapshotFileSystem(snapshotKeyReader = { throw cancelled })
            assertSame(cancelled, assertFailsWith<CancellationException> { filesystem.read(record.name) })
            assertValidRetainedCopy(record, saved)
        }
    }

    @Test
    fun authenticatedCurrentLoadRemovesStaleLegacyOnlyAfterSuccessfulDecrypt(): Unit = runBlocking {
        withValidCopies { record, saved ->
            // Distinct but valid legacy bytes ensure the returned state really
            // comes from the authenticated current record, never stale fallback.
            writeBytes(record.legacyPath, encodeSnapshot(saved.copy(metadata = mapOf("copy" to "stale"))))
            val filesystem = IosSnapshotFileSystem(snapshotKeyReader = ::syntheticKey)
            assertEquals(Result.Success(saved), FileBackedSnapshotStore(filesystem, Json).load(saved.sessionId))
            assertFalse(fileManager.fileExistsAtPath(record.legacyPath))
            assertTrue(fileManager.fileExistsAtPath(record.protectedPath))
            assertContentEquals(record.originalProtectedBytes, readBoundedSnapshotBytes(record.protectedPath, 65_536))
        }
    }

    private data class Record(
        val name: String,
        val legacyDirectory: String,
        val protectedDirectory: String,
        val originalProtectedBytes: ByteArray? = null,
    ) {
        val legacyPath: String get() = "$legacyDirectory/$name"
        val protectedPath: String get() = "$protectedDirectory/$name"
    }

    private suspend fun withSyntheticRecord(
        legacyBytes: ByteArray,
        protectedBytes: ByteArray? = null,
        name: String = "backup-test-${NSUUID.UUID().UUIDString}${FileBackedSnapshotStore.SUFFIX}",
        block: suspend (Record) -> Unit,
    ) {
        val documents = requireNotNull(
            fileManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)?.path,
        )
        val support = requireNotNull(
            fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)?.path,
        )
        val record = Record(name, "$documents/snapshots", "$support/Parlor/snapshots", protectedBytes)
        check(fileManager.createDirectoryAtPath(record.legacyDirectory, true, null, null))
        check(fileManager.createDirectoryAtPath(record.protectedDirectory, true, null, null))
        check(!fileManager.fileExistsAtPath(record.legacyPath))
        check(!fileManager.fileExistsAtPath(record.protectedPath))
        try {
            writeBytes(record.legacyPath, legacyBytes)
            if (protectedBytes != null) writeBytes(record.protectedPath, protectedBytes)
            block(record)
        } finally {
            removeOwnFileIfPresent(record.legacyPath)
            removeOwnFileIfPresent(record.protectedPath)
            legacyBytes.fill(0)
            protectedBytes?.fill(0)
        }
    }

    private suspend fun withValidCopies(
        protect: (String, ByteArray) -> ByteArray = ::protectSyntheticSnapshot,
        block: suspend (Record, GameSnapshot) -> Unit,
    ) {
        val sessionId = SessionId("retention-test-${NSUUID.UUID().UUIDString}")
        val definition = MafiaDefinition(Json)
        val initial = definition.createInitialState(
            SessionConfig(
                sessionId = sessionId,
                caseId = CaseId("synthetic-mafia"),
                modeId = MafiaIds.ClassicModeId,
                players = (0 until 5).map { Player(PlayerId("p$it"), "Player $it", seat = it) },
                randomSeed = 42L,
            ),
        )
        val createdAt = Instant.fromEpochSeconds(1_700_000_000)
        val state = definition.reducer().reduce(
            initial,
            MafiaAction.StartGame,
            DefaultReducerContext(FakeClock(createdAt), RandomSource.seeded(42L)),
        ).newState
        assertEquals(MafiaPhase.RoleAssignment, state.phase)
        val saved = GameSnapshot(
            sessionId, MafiaIds.GameId, SemVer(1, 0, 0), createdAt, state.phase.id,
            definition.snapshotCodec().encode(state), mapOf("playMode" to "PassAndPlay"),
        )
        assertEquals(state, definition.snapshotCodec().decode(saved.payload))
        val name = "${sessionId.raw}${FileBackedSnapshotStore.SUFFIX}"
        val bytes = encodeSnapshot(saved)
        withSyntheticRecord(bytes, protect(name, bytes), name) { record -> block(record, saved) }
    }

    private fun assertValidRetainedCopy(record: Record, saved: GameSnapshot) {
        assertTrue(isExcluded(record.legacyDirectory))
        assertTrue(fileManager.fileExistsAtPath(record.legacyPath), "Failed authentication must retain the last legacy copy")
        val bytes = readBoundedSnapshotBytes(record.legacyPath, 65_536)
        assertContentEquals(encodeSnapshot(saved), bytes)
        assertEquals(saved, Json.decodeFromString(GameSnapshot.serializer(), bytes.decodeToString()))
        assertEquals(
            MafiaPhase.RoleAssignment,
            MafiaDefinition(Json).snapshotCodec().decode(saved.payload).phase,
        )
        assertContentEquals(record.originalProtectedBytes, readBoundedSnapshotBytes(record.protectedPath, 65_536))
    }

    private fun encodeSnapshot(snapshot: GameSnapshot): ByteArray =
        Json.encodeToString(GameSnapshot.serializer(), snapshot).encodeToByteArray()

    /** Known synthetic key only; no real Keychain or user data access. */
    private fun syntheticKey(): ByteArray = ByteArray(64) { (it + 1).toByte() }

    private fun protectSyntheticSnapshot(name: String, bytes: ByteArray): ByteArray {
        val header = "PARSNAP".encodeToByteArray() + byteArrayOf(1, 16)
        val iv = ByteArray(16) { (it + 5).toByte() }
        val key = syntheticKey()
        val encryptionKey = key.copyOfRange(0, 32)
        val macKey = key.copyOfRange(32, 64)
        try {
            val encrypted = iosAesCbc(kCCEncrypt, encryptionKey, iv, bytes)
            val authenticated = header + name.encodeToByteArray() + iv + encrypted
            val tag = ByteArray(32)
            macKey.usePinned { pinnedKey ->
                authenticated.usePinned { pinnedData ->
                    tag.usePinned { pinnedTag ->
                        CCHmac(
                            kCCHmacAlgSHA256, pinnedKey.addressOf(0), macKey.size.toULong(),
                            pinnedData.addressOf(0), authenticated.size.toULong(), pinnedTag.addressOf(0),
                        )
                    }
                }
            }
            return header + iv + encrypted + tag
        } finally {
            key.fill(0)
            encryptionKey.fill(0)
            macKey.fill(0)
        }
    }

    private fun isExcluded(path: String): Boolean {
        val key = requireNotNull(NSURLIsExcludedFromBackupKey)
        val values = requireNotNull(NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(key), null))
        return requireNotNull(values[key] as? NSNumber).boolValue
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        check(data.writeToFile(path, atomically = true))
    }

    private fun removeOwnFileIfPresent(path: String) {
        if (fileManager.fileExistsAtPath(path)) check(fileManager.removeItemAtPath(path, null))
    }

    private fun malformedLegacy(): ByteArray =
        "!\"hostOnly\":\"synthetic-fixture\"}".encodeToByteArray()

    private val fileManager = NSFileManager.defaultManager
}
