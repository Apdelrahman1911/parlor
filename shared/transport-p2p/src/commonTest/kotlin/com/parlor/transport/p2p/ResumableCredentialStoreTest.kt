package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.result.Result
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
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
