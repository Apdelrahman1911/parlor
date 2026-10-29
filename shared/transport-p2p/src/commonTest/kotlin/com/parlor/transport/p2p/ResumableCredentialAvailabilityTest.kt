package com.parlor.transport.p2p

import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.secure.SecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic storage failures, not OS Keychain, screen-lock or networking tests. */
class ResumableCredentialAvailabilityTest {
    @Test
    fun unavailable_reads_preserve_both_generations_without_mutating_any_transaction() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val first = credential("first", 1)
        val second = credential("second", 2)
        store.stage(first)
        store.commit(first.offerId, first.generation)
        store.stage(second)
        val selection = (store.loadSelectedResumeCandidate() as Result.Success).data!!.selection
        val before = storage.durableBytes()
        storage.resetMutations()
        storage.readUnavailable = true

        val results = listOf(
            "load" to store.loadResumeCandidate(),
            "select" to store.loadSelectedResumeCandidate(),
            "stage" to store.stage(credential("third", 3)),
            "commit" to store.commit(second.offerId, second.generation),
            "discard" to store.discardPending(second.offerId),
            "update" to store.updateGame("fixture-game", 2),
            "invalidate generation" to store.invalidateOwned(first),
            "invalidate membership" to store.invalidateMembershipOwned(first),
            "invalidate selection" to store.invalidateSelection(selection),
        )
        for ((operation, result) in results) {
            assertTrue(result == unavailable, "$operation must report unavailable storage")
        }
        assertEquals(0, storage.writes)
        assertEquals(0, storage.removes)
        storage.assertDurableBytes(before)

        storage.readUnavailable = false
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(first))
        assertTrue(recreated.commit(second.offerId, second.generation) is Result.Success)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(second))
        before.fill(0)
    }

    @Test
    fun unavailable_pending_only_record_is_not_reported_as_no_saved_session() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val offered = credential("offered", 1)
        store.stage(offered)
        val before = storage.durableBytes()
        storage.resetMutations()
        storage.readUnavailable = true

        assertTrue(store.loadResumeCandidate() == unavailable)
        assertTrue(store.loadSelectedResumeCandidate() == unavailable)
        assertEquals(0, storage.writes)
        assertEquals(0, storage.removes)
        storage.assertDurableBytes(before)

        storage.readUnavailable = false
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(offered))
        assertTrue(recreated.commit(offered.offerId, offered.generation) is Result.Success)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(offered))
        before.fill(0)
    }

    @Test
    fun failed_staging_and_commit_writes_preserve_the_last_durable_winner() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val first = credential("first", 1)
        val second = credential("second", 2)
        store.stage(first)
        store.commit(first.offerId, first.generation)
        val beforeStage = storage.durableBytes()
        storage.writeUnavailable = true

        assertTrue(store.stage(second) == unavailable)
        storage.assertDurableBytes(beforeStage)
        assertTrue(ResumableCredentialStore(storage).loadResumeCandidate() == Result.Success(first))

        storage.writeUnavailable = false
        store.stage(second)
        val beforeCommit = storage.durableBytes()
        storage.writeUnavailable = true
        assertTrue(store.commit(second.offerId, second.generation) == unavailable)
        storage.assertDurableBytes(beforeCommit)
        assertTrue(ResumableCredentialStore(storage).loadResumeCandidate() == Result.Success(first))

        storage.writeUnavailable = false
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.commit(second.offerId, second.generation) is Result.Success)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(second))
        beforeStage.fill(0)
        beforeCommit.fill(0)
    }

    @Test
    fun failed_pending_only_commit_remains_resumable_after_store_recreation() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val offered = credential("offered", 1)
        store.stage(offered)
        val before = storage.durableBytes()
        storage.writeUnavailable = true

        assertTrue(store.commit(offered.offerId, offered.generation) == unavailable)
        storage.assertDurableBytes(before)
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(offered))
        storage.writeUnavailable = false
        assertTrue(recreated.commit(offered.offerId, offered.generation) is Result.Success)
        assertTrue(ResumableCredentialStore(storage).loadResumeCandidate() == Result.Success(offered))
        before.fill(0)
    }

    @Test
    fun failed_selected_removal_is_not_success_and_preserves_ownership_for_explicit_retry() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val first = credential("first", 1)
        store.stage(first)
        store.commit(first.offerId, first.generation)
        val selection = (store.loadSelectedResumeCandidate() as Result.Success).data!!.selection
        val before = storage.durableBytes()
        storage.removeUnavailable = true

        assertTrue(store.invalidateSelection(selection) == unavailable)
        storage.assertDurableBytes(before)
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(first))
        storage.removeUnavailable = false
        assertTrue(recreated.invalidateSelection(selection) == Result.Success(CredentialInvalidationResult.Invalidated))
        assertTrue(recreated.loadResumeCandidate() == Result.Success(null))
        assertTrue(recreated.invalidateSelection(selection) == Result.Success(CredentialInvalidationResult.NotOwned))
        before.fill(0)
    }

    @Test
    fun failed_partial_deletion_write_preserves_the_selected_and_unrelated_memberships() = runTest {
        val storage = FaultStorage()
        val store = ResumableCredentialStore(storage)
        val active = credential("active", 1)
        val pending = credential("pending", 1).copy(membershipId = "other-membership", roomCode = "XYZ789")
        store.stage(active)
        store.commit(active.offerId, active.generation)
        store.stage(pending)
        val selection = (store.loadSelectedResumeCandidate() as Result.Success).data!!.selection
        val before = storage.durableBytes()
        storage.resetMutations()
        storage.writeUnavailable = true

        // Removing the selected pending membership must retain the unrelated
        // active record, so this path writes a replacement instead of removing.
        assertTrue(store.invalidateSelection(selection) == unavailable)
        assertEquals(1, storage.writes)
        assertEquals(0, storage.removes)
        storage.assertDurableBytes(before)
        val recreated = ResumableCredentialStore(storage)
        assertTrue(recreated.loadResumeCandidate() == Result.Success(pending))
        storage.writeUnavailable = false
        assertTrue(recreated.invalidateSelection(selection) == Result.Success(CredentialInvalidationResult.Invalidated))
        assertTrue(ResumableCredentialStore(storage).loadResumeCandidate() == Result.Success(active))
        assertTrue(recreated.commit(pending.offerId, pending.generation) ==
            Result.Failure(CredentialStoreError.TransactionMismatch))
        before.fill(0)
    }

    private class FaultStorage : SecureStorage {
        private val delegate = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
        var readUnavailable = false
        var writeUnavailable = false
        var removeUnavailable = false
        var writes = 0
        var removes = 0

        fun resetMutations() {
            writes = 0
            removes = 0
        }

        suspend fun durableBytes(): ByteArray = (delegate.get(STORAGE_KEY) as Result.Success).data!!

        suspend fun assertDurableBytes(expected: ByteArray) {
            val actual = durableBytes()
            assertTrue(expected.contentEquals(actual), "The durable credential record must remain unchanged")
            actual.fill(0)
        }

        override suspend fun get(key: String): Result<ByteArray?, DataError> =
            if (readUnavailable) Result.Failure(storageError) else delegate.get(key)

        // Inject before mutation. These controls do not claim atomicity of a
        // platform backing that partially persists bytes before reporting failure.
        override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> {
            writes++
            return if (writeUnavailable) Result.Failure(storageError) else delegate.put(key, value)
        }

        override suspend fun remove(key: String): EmptyResult<DataError> {
            removes++
            return if (removeUnavailable) Result.Failure(storageError) else delegate.remove(key)
        }
    }

    private fun credential(offer: String, generation: Long) = ResumableSessionCredential(
        offerId = offer,
        membershipId = "synthetic-membership",
        roomCode = "ABC234",
        displayName = "Synthetic",
        playerId = "synthetic-player",
        hostPeerId = "synthetic-host",
        hostFingerprint = "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq",
        secret = generation.toString(16).padStart(64, '0'),
        generation = generation,
        issuedAtEpochMillis = 1_000,
        expiresAtEpochMillis = 100_000,
        gameId = "fixture-game",
        gameVersion = 1,
    )

    private companion object {
        const val STORAGE_KEY = "p2p-resumable-session-v1"
        val unavailable = Result.Failure(CredentialStoreError.Unavailable)
        val storageError = DataError.IoError("synthetic_unavailable")
    }
}
