package com.parlor.transport.p2p

import com.parlor.core.result.Result
import com.parlor.storage.secure.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Device-protected client capability for one logical room membership.
 *
 * The secret is never exposed through public room/UI state. Host code stores
 * only its SHA-256 digest. The record can hold two generations so a process
 * death between offer and commit never destroys the last committed winner.
 */
@Serializable
internal data class ResumableSessionCredential(
    val schemaVersion: Int = CREDENTIAL_SCHEMA_VERSION,
    val offerId: String,
    /**
     * Stable local identity for one logical membership across credential rotations.
     *
     * Older schema-v1 records did not persist this field. Defaulting it to the
     * loaded offer id gives those records a safe upgrade identity; every later
     * rotation is produced with `copy`, so it preserves that identity.
     */
    val membershipId: String = offerId,
    val roomCode: String,
    val displayName: String,
    val playerId: String,
    val hostPeerId: String,
    val hostFingerprint: String,
    val secret: String,
    val generation: Long,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val gameId: String? = null,
    val gameVersion: Int? = null,
) {
    fun requireValid(): ResumableSessionCredential = apply {
        require(schemaVersion == CREDENTIAL_SCHEMA_VERSION)
        require(offerId.isSafeIdentifier())
        require(membershipId.isSafeIdentifier())
        require(roomCode.length == ROOM_CODE_LENGTH && roomCode.all(Char::isLetterOrDigit))
        require(displayName.isNotBlank() && displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(playerId.isSafeIdentifier())
        require(hostPeerId.isSafeIdentifier())
        require(hostFingerprint.length == HOST_FINGERPRINT_LENGTH)
        require(hostFingerprint.startsWith(HOST_FINGERPRINT_PREFIX))
        require(secret.length == SECRET_HEX_LENGTH && secret.all(::isLowerHex))
        require(generation > 0L)
        require(issuedAtEpochMillis > 0L)
        require(expiresAtEpochMillis > issuedAtEpochMillis)
        require(gameId == null || gameId.isSafeIdentifier())
        require((gameId == null) == (gameVersion == null))
        require(gameVersion == null || gameVersion > 0)
    }
}

@Serializable
private data class StoredCredentialRecord(
    val schemaVersion: Int = CREDENTIAL_SCHEMA_VERSION,
    val active: ResumableSessionCredential? = null,
    val pending: ResumableSessionCredential? = null,
) {
    fun requireValid(): StoredCredentialRecord = apply {
        require(schemaVersion == CREDENTIAL_SCHEMA_VERSION)
        require(active != null || pending != null)
        active?.requireValid()
        pending?.requireValid()
        if (
            active != null &&
            pending != null &&
            active.ownsSameMembershipAs(pending)
        ) {
            require(pending.generation > active.generation)
        }
    }
}

internal sealed interface CredentialStoreError {
    data object Unavailable : CredentialStoreError
    data object Corrupted : CredentialStoreError
    data object TransactionMismatch : CredentialStoreError
}

/** Result of an ownership-checked credential invalidation transaction. */
internal enum class CredentialInvalidationResult {
    Invalidated,
    NotOwned,
}

/**
 * Matches one issued capability generation without comparing its secret.
 *
 * Game metadata may be attached after admission, so it is deliberately not
 * part of the ownership key. The logical membership and issued generation are
 * all required: a delayed collector from generation N must never invalidate a
 * rotated generation N+1, even when both belong to the same logical room.
 */
internal fun ResumableSessionCredential.ownsSameGenerationAs(
    other: ResumableSessionCredential,
): Boolean =
    offerId == other.offerId &&
        membershipId == other.membershipId &&
        generation == other.generation &&
        roomCode == other.roomCode &&
        playerId == other.playerId &&
        hostPeerId == other.hostPeerId &&
        hostFingerprint == other.hostFingerprint

/**
 * Matches every rotated generation of one logical membership.
 *
 * This broader ownership key is reserved for an active room's authoritative
 * terminal/final-leave transaction. Routine retry, expiry, and stale-operation
 * cleanup must continue to use [ownsSameGenerationAs].
 */
internal fun ResumableSessionCredential.ownsSameMembershipAs(
    other: ResumableSessionCredential,
): Boolean =
    membershipId == other.membershipId &&
        roomCode == other.roomCode &&
        playerId == other.playerId &&
        hostPeerId == other.hostPeerId &&
        hostFingerprint == other.hostFingerprint

internal class ResumableCredentialStore(
    private val storage: SecureStorage,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
    },
) {
    /** Serializes read/modify/write transactions across lifecycle and game flows. */
    private val mutex = Mutex()

    suspend fun loadResumeCandidate(): Result<ResumableSessionCredential?, CredentialStoreError> =
        mutex.withLock {
            when (val loaded = loadRecord()) {
                is Result.Failure -> loaded
                is Result.Success -> Result.Success(loaded.data?.active ?: loaded.data?.pending)
            }
        }

    suspend fun stage(
        credential: ResumableSessionCredential,
    ): Result<Unit, CredentialStoreError> = mutex.withLock {
        val valid = credential.validOrNull()
            ?: return Result.Failure(CredentialStoreError.Corrupted)
        val current = when (val loaded = loadRecord()) {
            is Result.Failure -> return loaded
            is Result.Success -> loaded.data
        }
        return writeRecord(
            StoredCredentialRecord(
                active = current?.active,
                pending = valid,
            ),
        )
    }

    suspend fun commit(offerId: String, generation: Long): Result<Unit, CredentialStoreError> =
        mutex.withLock {
            val current = when (val loaded = loadRecord()) {
                is Result.Failure -> return loaded
                is Result.Success -> loaded.data
            }
            val pending = current?.pending
                ?.takeIf { it.offerId == offerId && it.generation == generation }
                ?: return Result.Failure(CredentialStoreError.TransactionMismatch)
            return writeRecord(StoredCredentialRecord(active = pending))
        }

    suspend fun discardPending(offerId: String): Result<Unit, CredentialStoreError> = mutex.withLock {
        val current = when (val loaded = loadRecord()) {
            is Result.Failure -> return loaded
            is Result.Success -> loaded.data
        } ?: return Result.Success(Unit)
        if (current.pending?.offerId != offerId) {
            return Result.Failure(CredentialStoreError.TransactionMismatch)
        }
        val active = current.active
        return if (active == null) {
            removeRecord()
        } else {
            writeRecord(StoredCredentialRecord(active = active))
        }
    }

    suspend fun updateGame(
        gameId: String,
        gameVersion: Int,
    ): Result<Unit, CredentialStoreError> = mutex.withLock {
        require(gameId.isSafeIdentifier() && gameVersion > 0)
        val current = when (val loaded = loadRecord()) {
            is Result.Failure -> return loaded
            is Result.Success -> loaded.data
        } ?: return Result.Success(Unit)
        return writeRecord(
            current.copy(
                active = current.active?.copy(gameId = gameId, gameVersion = gameVersion),
                pending = current.pending?.copy(gameId = gameId, gameVersion = gameVersion),
            ),
        )
    }

    /**
     * Removes only the capability generation owned by [credential].
     *
     * The load/compare/write operation is serialized with staging, rotation,
     * and game-metadata updates. A stale room therefore cannot erase a newer
     * generation or a credential belonging to another logical room. If the
     * record contains both an old active generation and a new pending one,
     * invalidating the old generation preserves the pending replacement.
     */
    suspend fun invalidateOwned(
        credential: ResumableSessionCredential,
    ): Result<CredentialInvalidationResult, CredentialStoreError> = mutex.withLock {
        val valid = credential.validOrNull()
            ?: return Result.Failure(CredentialStoreError.Corrupted)
        val current = when (val loaded = loadRecord()) {
            is Result.Failure -> return loaded
            is Result.Success -> loaded.data
        } ?: return Result.Success(CredentialInvalidationResult.NotOwned)

        val activeMatches = current.active?.ownsSameGenerationAs(valid) == true
        val pendingMatches = current.pending?.ownsSameGenerationAs(valid) == true
        if (!activeMatches && !pendingMatches) {
            return Result.Success(CredentialInvalidationResult.NotOwned)
        }

        val remainingActive = current.active?.takeUnless { activeMatches }
        val remainingPending = current.pending?.takeUnless { pendingMatches }
        val persisted = if (remainingActive == null && remainingPending == null) {
            removeRecord()
        } else {
            writeRecord(
                StoredCredentialRecord(
                    active = remainingActive,
                    pending = remainingPending,
                ),
            )
        }
        return when (persisted) {
            is Result.Success -> Result.Success(CredentialInvalidationResult.Invalidated)
            is Result.Failure -> persisted
        }
    }

    /**
     * Revokes every stored rotation owned by [credential]'s logical membership.
     *
     * A resume connector durably commits generation N+1 before the room adopts
     * its replacement physical session. If an authenticated terminal frame or
     * explicit final Leave wins in that gap, exact-generation invalidation of N
     * would leave N+1 resumable. This serialized transaction removes N and any
     * pending/active descendant with the same stable
     * [ResumableSessionCredential.membershipId], while a
     * credential for another room/membership is preserved even if its player,
     * host, and room-code fields happen to match.
     *
     * Callers must be the room that currently owns [credential]. Other cleanup
     * paths use [invalidateOwned] so delayed work cannot revoke a live rotation.
     */
    suspend fun invalidateMembershipOwned(
        credential: ResumableSessionCredential,
    ): Result<CredentialInvalidationResult, CredentialStoreError> = mutex.withLock {
        val valid = credential.validOrNull()
            ?: return Result.Failure(CredentialStoreError.Corrupted)
        val current = when (val loaded = loadRecord()) {
            is Result.Failure -> return loaded
            is Result.Success -> loaded.data
        } ?: return Result.Success(CredentialInvalidationResult.NotOwned)

        val activeMatches = current.active?.ownsSameMembershipAs(valid) == true
        val pendingMatches = current.pending?.ownsSameMembershipAs(valid) == true
        if (!activeMatches && !pendingMatches) {
            return Result.Success(CredentialInvalidationResult.NotOwned)
        }

        val remainingActive = current.active?.takeUnless { activeMatches }
        val remainingPending = current.pending?.takeUnless { pendingMatches }
        val persisted = if (remainingActive == null && remainingPending == null) {
            removeRecord()
        } else {
            writeRecord(
                StoredCredentialRecord(
                    active = remainingActive,
                    pending = remainingPending,
                ),
            )
        }
        return when (persisted) {
            is Result.Success -> Result.Success(CredentialInvalidationResult.Invalidated)
            is Result.Failure -> persisted
        }
    }

    suspend fun clear(): Result<Unit, CredentialStoreError> = mutex.withLock { removeRecord() }

    private suspend fun removeRecord(): Result<Unit, CredentialStoreError> =
        when (storage.remove(STORAGE_KEY)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(CredentialStoreError.Unavailable)
        }

    private suspend fun loadRecord(): Result<StoredCredentialRecord?, CredentialStoreError> {
        val bytes = when (val loaded = storage.get(STORAGE_KEY)) {
            is Result.Failure -> return Result.Failure(CredentialStoreError.Unavailable)
            is Result.Success -> loaded.data ?: return Result.Success(null)
        }
        return try {
            if (bytes.size !in 1..MAX_RECORD_BYTES) {
                Result.Failure(CredentialStoreError.Corrupted)
            } else {
                val decoded = json.decodeFromString<StoredCredentialRecord>(
                    bytes.decodeToString(throwOnInvalidSequence = true),
                )
                    .requireValid()
                Result.Success(decoded)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(CredentialStoreError.Corrupted)
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun writeRecord(
        record: StoredCredentialRecord,
    ): Result<Unit, CredentialStoreError> {
        val encoded = try {
            json.encodeToString(record.requireValid()).encodeToByteArray()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            return Result.Failure(CredentialStoreError.Corrupted)
        }
        return try {
            if (encoded.size > MAX_RECORD_BYTES) {
                Result.Failure(CredentialStoreError.Corrupted)
            } else {
                when (storage.put(STORAGE_KEY, encoded)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Failure -> Result.Failure(CredentialStoreError.Unavailable)
                }
            }
        } finally {
            encoded.fill(0)
        }
    }

    private companion object {
        const val STORAGE_KEY = "p2p-resumable-session-v1"
        const val MAX_RECORD_BYTES = 8 * 1024
    }
}

/** Validation failures are corrupted input; VM/runtime failures remain fatal. */
private fun ResumableSessionCredential.validOrNull(): ResumableSessionCredential? =
    try {
        requireValid()
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        null
    }

private const val CREDENTIAL_SCHEMA_VERSION = 1
private const val ROOM_CODE_LENGTH = 6
private const val MAX_DISPLAY_NAME_LENGTH = 32
private const val SECRET_HEX_LENGTH = 64
private const val HOST_FINGERPRINT_PREFIX = "p2f1-"
private const val HOST_FINGERPRINT_LENGTH = 57

private fun String.isSafeIdentifier(): Boolean =
    length in 1..128 && all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

private fun isLowerHex(character: Char): Boolean = character in '0'..'9' || character in 'a'..'f'
