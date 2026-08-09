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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun invalid_credential_is_reported_as_corrupted_before_storage_is_mutated() = runTest {
        val invalid = credential(offerId = "offer-1", generation = 1).copy(secret = "not-hex")

        assertThat(store.stage(invalid)).isEqualTo(
            Result.Failure(CredentialStoreError.Corrupted),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
    }

    @Test
    fun credential_read_propagates_cancellation_and_fatal_errors() = runTest {
        assertFailsWith<CancellationException> {
            ResumableCredentialStore(
                ThrowingSecureStorage(getFailure = CancellationException("cancel read")),
            ).loadResumeCandidate()
        }
        assertFailsWith<AssertionError> {
            ResumableCredentialStore(
                ThrowingSecureStorage(getFailure = AssertionError("fatal read")),
            ).loadResumeCandidate()
        }
    }

    @Test
    fun credential_write_propagates_cancellation_and_fatal_errors() = runTest {
        val offered = credential(offerId = "offer-1", generation = 1)

        assertFailsWith<CancellationException> {
            ResumableCredentialStore(
                ThrowingSecureStorage(putFailure = CancellationException("cancel write")),
            ).stage(offered)
        }
        assertFailsWith<AssertionError> {
            ResumableCredentialStore(
                ThrowingSecureStorage(putFailure = AssertionError("fatal write")),
            ).stage(offered)
        }
    }

    @Test
    fun schema_one_record_without_membership_id_upgrades_to_offer_identity() = runTest {
        val legacyCredential = credential(offerId = "legacy-offer", generation = 1)
        val encodedCredential = Json.encodeToJsonElement(
            ResumableSessionCredential.serializer(),
            legacyCredential,
        ).jsonObject
        val legacyRecord = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("active", JsonObject(encodedCredential - "membershipId"))
            put("pending", JsonNull)
        }
        storage.put(
            "p2p-resumable-session-v1",
            legacyRecord.toString().encodeToByteArray(),
        )

        val loaded = store.loadResumeCandidate()
        assertThat(loaded).isInstanceOf(Result.Success::class)
        val upgraded = (loaded as Result.Success).data
        assertThat(upgraded?.membershipId).isEqualTo(legacyCredential.offerId)
        assertThat(upgraded?.offerId).isEqualTo(legacyCredential.offerId)
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
    fun matching_generation_is_invalidated_transactionally_and_duplicate_is_idempotent() = runTest {
        val active = credential(offerId = "offer-1", generation = 1)
        store.stage(active)
        store.commit(active.offerId, active.generation)

        assertThat(store.invalidateOwned(active)).isEqualTo(
            Result.Success(CredentialInvalidationResult.Invalidated),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
        assertThat(store.invalidateOwned(active)).isEqualTo(
            Result.Success(CredentialInvalidationResult.NotOwned),
        )
    }

    @Test
    fun stale_generation_cannot_erase_a_committed_rotation_for_the_same_logical_room() = runTest {
        val generationOne = credential(offerId = "offer-1", generation = 1)
        val generationTwo = credential(offerId = "offer-2", generation = 2)
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        store.stage(generationTwo)
        store.commit(generationTwo.offerId, generationTwo.generation)

        assertThat(store.invalidateOwned(generationOne)).isEqualTo(
            Result.Success(CredentialInvalidationResult.NotOwned),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))
    }

    @Test
    fun final_membership_invalidation_revokes_a_rotation_committed_before_room_handoff() = runTest {
        val generationOne = credential(offerId = "offer-1", generation = 1)
        val generationTwo = generationOne.copy(
            offerId = "offer-2",
            secret = "2".repeat(64),
            generation = 2,
        )
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        store.stage(generationTwo)
        store.commit(generationTwo.offerId, generationTwo.generation)

        assertThat(store.invalidateMembershipOwned(generationOne)).isEqualTo(
            Result.Success(CredentialInvalidationResult.Invalidated),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(null))
    }

    @Test
    fun final_membership_invalidation_preserves_a_newer_unrelated_membership() = runTest {
        val oldMembership = credential(
            offerId = "offer-old",
            generation = 1,
            membershipId = "membership-old",
        )
        val replacementMembership = credential(
            offerId = "offer-new",
            generation = 2,
            membershipId = "membership-new",
        )
        store.stage(replacementMembership)
        store.commit(replacementMembership.offerId, replacementMembership.generation)

        assertThat(store.invalidateMembershipOwned(oldMembership)).isEqualTo(
            Result.Success(CredentialInvalidationResult.NotOwned),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(replacementMembership))
    }

    @Test
    fun final_membership_invalidation_propagates_storage_cancellation() = runTest {
        val delegate = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
        val cancelling = CancelRemoveSecureStorage(delegate)
        val transactionalStore = ResumableCredentialStore(cancelling)
        val active = credential(offerId = "offer-1", generation = 1)
        transactionalStore.stage(active)
        transactionalStore.commit(active.offerId, active.generation)
        cancelling.cancelRemove = true

        assertFailsWith<CancellationException> {
            transactionalStore.invalidateMembershipOwned(active)
        }
    }

    @Test
    fun old_room_cleanup_cannot_erase_a_different_logical_room_credential() = runTest {
        val oldRoom = credential(offerId = "offer-old", generation = 1)
        val newRoom = credential(
            offerId = "offer-new",
            generation = 1,
            roomCode = "XYZ789",
            playerId = "alice-new-room",
            hostPeerId = "other-host",
        )
        store.stage(newRoom)
        store.commit(newRoom.offerId, newRoom.generation)

        assertThat(store.invalidateOwned(oldRoom)).isEqualTo(
            Result.Success(CredentialInvalidationResult.NotOwned),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(newRoom))
    }

    @Test
    fun invalidating_active_generation_preserves_its_pending_rotation() = runTest {
        val generationOne = credential(offerId = "offer-1", generation = 1)
        val generationTwo = credential(offerId = "offer-2", generation = 2)
        store.stage(generationOne)
        store.commit(generationOne.offerId, generationOne.generation)
        store.stage(generationTwo)

        assertThat(store.invalidateOwned(generationOne)).isEqualTo(
            Result.Success(CredentialInvalidationResult.Invalidated),
        )
        assertThat(store.loadResumeCandidate()).isEqualTo(Result.Success(generationTwo))
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun invalidation_and_rotation_are_one_serialized_read_modify_write_transaction() = runTest {
        val delegate = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())
        val gated = GateSecureStorage(delegate)
        val transactionalStore = ResumableCredentialStore(gated)
        val generationOne = credential("offer-1", 1)
        val generationTwo = credential("offer-2", 2)
        transactionalStore.stage(generationOne)
        transactionalStore.commit(generationOne.offerId, generationOne.generation)
        gated.blockReads = true

        val invalidate = async { transactionalStore.invalidateOwned(generationOne) }
        gated.firstBlockedRead.await()
        val rotate = async { transactionalStore.stage(generationTwo) }
        runCurrent()

        // Rotation cannot read/write a stale record while invalidation is
        // paused between its ownership check and persistence transaction.
        assertThat(gated.blockedReadCount).isEqualTo(1)
        gated.releaseReads.complete(Unit)
        assertThat(invalidate.await()).isEqualTo(
            Result.Success(CredentialInvalidationResult.Invalidated),
        )
        assertThat(rotate.await()).isInstanceOf(Result.Success::class)
        assertThat(transactionalStore.loadResumeCandidate())
            .isEqualTo(Result.Success(generationTwo))
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

    private class CancelRemoveSecureStorage(
        private val delegate: SecureStorage,
    ) : SecureStorage {
        var cancelRemove: Boolean = false

        override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> =
            delegate.put(key, value)

        override suspend fun get(key: String): Result<ByteArray?, DataError> = delegate.get(key)

        override suspend fun remove(key: String): EmptyResult<DataError> =
            if (cancelRemove) {
                throw CancellationException("cancel membership invalidation")
            } else {
                delegate.remove(key)
            }
    }

    private class ThrowingSecureStorage(
        private val getFailure: Throwable? = null,
        private val putFailure: Throwable? = null,
        private val removeFailure: Throwable? = null,
    ) : SecureStorage {
        override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> {
            putFailure?.let { throw it }
            return Result.Success(Unit)
        }

        override suspend fun get(key: String): Result<ByteArray?, DataError> {
            getFailure?.let { throw it }
            return Result.Success(null)
        }

        override suspend fun remove(key: String): EmptyResult<DataError> {
            removeFailure?.let { throw it }
            return Result.Success(Unit)
        }
    }

    private fun credential(
        offerId: String,
        generation: Long,
        roomCode: String = "ABC234",
        playerId: String = "alice-pid",
        hostPeerId: String = "host-pid",
        membershipId: String = "membership-$roomCode-$playerId-$hostPeerId",
    ) = ResumableSessionCredential(
        offerId = offerId,
        membershipId = membershipId,
        roomCode = roomCode,
        displayName = "Alice",
        playerId = playerId,
        hostPeerId = hostPeerId,
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
