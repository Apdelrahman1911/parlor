package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.secure.SecureStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ResumableCredentialStoreTest {
    private val storage = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
    private val store = ResumableCredentialStore(storage)

    @Test
    fun initial_offer_is_resumable_before_commit_and_becomes_active_on_commit() = runTest {
        val offered = credential(offerId = "offer-1", generation = 1)

        assertThat(store.stage(offered)).isInstanceOf(Result.Success::class)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(offered))
        assertThat(store.commit("offer-1", 1)).isInstanceOf(Result.Success::class)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(offered))
    }

    @Test
    fun pending_rotation_does_not_destroy_the_last_committed_generation() = runTest {
        val generationOne = credential(offerId = "offer-1", generation = 1)
        val generationTwo = credential(offerId = "offer-2", generation = 2)
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)

        store.stage(generationTwo)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationOne))

        store.commit(generationTwo.offerId, generationTwo.generation)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))
    }

    @Test
    fun discarding_a_failed_rotation_restores_the_committed_generation() = runTest {
        val generationOne = credential(offerId = "offer-1", generation = 1)
        val generationTwo = credential(offerId = "offer-2", generation = 2)
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        store.stage(generationTwo)

        assertThat(store.discardPending(generationTwo.offerId))
            .isInstanceOf(Result.Success::class)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationOne))
    }

    @Test
    fun mismatched_commit_cannot_promote_an_unconfirmed_offer() = runTest {
        val offered = credential(offerId = "offer-1", generation = 1)
        store.stage(offered)

        assertThat(store.commit("attacker-offer", 1)).isEqualTo(
            Result.Failure(CredentialStoreError.TransactionMismatch),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(offered))
    }

    @Test
    fun malformed_or_unknown_schema_record_is_rejected() = runTest {
        storage.put(
            "p2p-resumable-session-v1",
            """{"schemaVersion":99,"active":null,"pending":null}""".encodeToByteArray(),
        )

        assertThat(store.loadResumeCandidate()).isEqualTo(
            Result.Failure(CredentialStoreError.Corrupted),
        )
    }

    @Test
    fun explicit_clear_permanently_removes_all_generations() = runTest {
        val offered = credential(offerId = "offer-1", generation = 1)
        store.stage(offered)
        store.commit(offered.offerId, offered.generation)

        assertThat(store.clear()).isInstanceOf(Result.Success::class)
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun read_modify_write_transactions_are_serialized_without_losing_rotation_or_game_metadata() =
        runTest {
            val delegate = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
            val gated = GateSecureStorage(delegate)
            val transactionalStore = ResumableCredentialStore(gated)
            val generationOne = credential("offer-1", 1)
            val generationTwo = credential("offer-2", 2)
            transactionalStore.stage(generationOne)
            transactionalStore.commit(generationOne.offerId, generationOne.generation)
            gated.blockReads = true

            val stage = async { transactionalStore.stage(generationTwo) }
            gated.firstBlockedRead.await()
            val update = async { transactionalStore.updateGame("mafia", 2) }
            runCurrent()

            // The second transaction cannot read a stale copy while the first
            // transaction is paused between load and write.
            assertThat(gated.blockedReadCount).isEqualTo(1)
            gated.releaseReads.complete(Unit)
            assertThat(stage.await()).isInstanceOf(Result.Success::class)
            assertThat(update.await()).isInstanceOf(Result.Success::class)
            assertThat(transactionalStore.commit("offer-2", 2))
                .isInstanceOf(Result.Success::class)
            val resumed = transactionalStore.loadResumeCandidate() as Result.Success
            assertThat(resumed.data?.generation).isEqualTo(2L)
            assertThat(resumed.data?.gameId).isEqualTo("mafia")
            assertThat(resumed.data?.gameVersion).isEqualTo(2)
        }

    private class GateSecureStorage(
        private val delegate: SecureStorage,
    ) : SecureStorage {
        var blockReads: Boolean = false
        var blockedReadCount: Int = 0
        val firstBlockedRead = CompletableDeferred<Unit>()
        val releaseReads = CompletableDeferred<Unit>()

        override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> =
            delegate.put(key, value)

        override suspend fun get(key: String): Result<ByteArray?, DataError> {
            if (blockReads) {
                blockedReadCount += 1
                firstBlockedRead.complete(Unit)
                releaseReads.await()
            }
            return delegate.get(key)
        }

        override suspend fun remove(key: String): EmptyResult<DataError> = delegate.remove(key)
    }

    private fun credential(offerId: String, generation: Long) = ResumableSessionCredential(
        offerId = offerId,
        roomCode = "ABC234",
        displayName = "Alice",
        playerId = "alice-pid",
        hostPeerId = "host-pid",
        hostFingerprint = TEST_FINGERPRINT,
        secret = generation.toString(16).padStart(64, '0'),
        generation = generation,
        issuedAtEpochMillis = 1_000,
        expiresAtEpochMillis = 100_000,
        gameId = "whodunit",
        gameVersion = 1,
    )

    private companion object {
        const val TEST_FINGERPRINT =
            "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq"
    }
}
