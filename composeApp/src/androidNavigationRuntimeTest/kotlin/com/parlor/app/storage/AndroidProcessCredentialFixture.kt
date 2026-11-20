package com.parlor.app.storage

import com.parlor.app.shell.game.GameShellMultiplayerContract
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.Result
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.secure.SecureStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Synthetic persisted schema-v1 membership, admitted by the shipping parser and
 * selected/deleted through RoomTransport. This never joins a room, authenticates
 * to a host, replaces transport/store implementations or exposes internals.
 * One active membership is selected. Unrelated credentials and a local save
 * are asserted separately by the process phases; no fake host is contacted.
 */
internal class AndroidProcessCredentialFixture(
    private val id: String,
    private val issuedAt: Long,
    private val contract: GameShellMultiplayerContract,
    private val secure: SecureStorage,
    private val transport: RoomTransport,
) {
    private val selected = credential("selected", "XYZ789", "Selected test peer")

    suspend fun seed() {
        requireNoMembership(secure)
        check(secure.put(STORAGE_KEY, record().toString().encodeToByteArray()) == EmptyOk)
        assertSelectedPresent()
    }

    suspend fun assertSelectedPresent() {
        assertRecord(record())
        assertPublicCandidate("Selected test peer")
    }

    suspend fun assertSelectedAbsent() {
        requireNoMembership(secure)
        val public = transport.resumableSession()
        check(public is Result.Success && public.data == null)
    }

    private suspend fun assertPublicCandidate(name: String) {
        val info = candidate()
        check(info.gameId == contract.gameId && info.gameVersion == contract.gameVersion)
        check(info.displayName == name && info.expiresAtEpochMillis == issuedAt + LIFETIME_MILLIS)
        check(info.deletionSelection != null)
    }

    private suspend fun candidate(): ResumableSessionInfo {
        val result = transport.resumableSession()
        check(result is Result.Success) { "Shipping transport must validate the synthetic stored membership" }
        return checkNotNull(result.data) { "Expected a live synthetic stored membership" }
    }

    private suspend fun assertRecord(expected: JsonObject) {
        val loaded = secure.get(STORAGE_KEY)
        check(loaded is Result.Success) { "Native credential storage must be available" }
        val bytes = checkNotNull(loaded.data)
        try {
            // Compare internally; never emit a credential or whole record on failure.
            check(Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) == expected) {
                "Selected membership deletion changed unexpected durable fields"
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun record(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("active", selected)
        put("pending", JsonNull)
    }

    private fun credential(suffix: String, code: String, name: String): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("offerId", "$id-$suffix-offer")
        put("membershipId", "$id-$suffix-membership")
        put("roomCode", code)
        put("displayName", name)
        put("playerId", "$id-$suffix-player")
        put("hostPeerId", "$id-$suffix-host")
        put("hostFingerprint", "p2f1-" + "a".repeat(FINGERPRINT_BODY_LENGTH))
        put("secret", "b".repeat(SECRET_LENGTH))
        put("generation", 1)
        put("issuedAtEpochMillis", issuedAt)
        put("expiresAtEpochMillis", issuedAt + LIFETIME_MILLIS)
        put("gameId", contract.gameId.raw)
        put("gameVersion", contract.gameVersion)
    }

    companion object {
        private const val STORAGE_KEY = "p2p-resumable-session-v1"
        private const val SECRET_LENGTH = 64
        private const val FINGERPRINT_BODY_LENGTH = 52
        // A synthetic storage fixture, not a change to real membership deadlines.
        private const val LIFETIME_MILLIS = 600_000L

        suspend fun requireNoMembership(secure: SecureStorage) {
            val previous = secure.get(STORAGE_KEY)
            check(previous is Result.Success && previous.data == null) { "Never replace an existing membership" }
        }
    }
}
