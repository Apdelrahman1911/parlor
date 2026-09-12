package com.parlor.engine.snapshot

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.versioning.SemVer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class GameSnapshotTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    @Test
    fun equal_payload_bytes_use_content_equality_and_consistent_hash_codes(): Unit {
        val original = snapshot()
        val equal = original.copy(payload = original.payload.copyOf(), metadata = original.metadata.toMap())

        assertNotSame(original.payload, equal.payload)
        assertEquals(original, equal)
        assertEquals(equal, original)
        assertEquals(original.hashCode(), equal.hashCode())
        assertEquals(1, setOf(original, equal).size)
    }

    @Test
    fun each_envelope_field_and_payload_or_metadata_change_affects_equality(): Unit {
        val original = snapshot()
        val changedSnapshots = listOf(
            original.copy(sessionId = SessionId("other-session")),
            original.copy(gameId = GameId("other-fixture")),
            original.copy(engineVersion = SemVer(1, 2, 4)),
            original.copy(createdAt = Instant.parse("2026-09-07T00:00:01Z")),
            original.copy(phaseId = "finished"),
            original.copy(payload = byteArrayOf(0, -1, 127)),
            original.copy(payload = byteArrayOf(0, -1, 127, -127)),
            original.copy(metadata = mapOf("test-mode" to "other")),
            original.copy(metadata = emptyMap()),
        )

        changedSnapshots.forEach { changed ->
            assertNotEquals(original, changed)
            assertNotEquals(changed, original)
            assertEquals(2, setOf(original, changed).size)
        }
    }

    @Test
    fun json_round_trip_preserves_the_exact_envelope_byte_values_and_metadata(): Unit {
        val original = snapshot()
        val expected = """
            {
              "sessionId": "snapshot-contract-session",
              "gameId": "snapshot-contract",
              "engineVersion": "1.2.3",
              "createdAt": "2026-09-07T00:00:00Z",
              "phaseId": "active",
              "payload": [0, -1, 127, -128],
              "metadata": {"test-mode": "synthetic"}
            }
        """.trimIndent()

        val encoded = json.encodeToString(GameSnapshot.serializer(), original)
        assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(encoded))
        val decoded = json.decodeFromString(GameSnapshot.serializer(), encoded)
        assertEquals(original, decoded)
        assertContentEquals(original.payload, decoded.payload)
        assertNotSame(original.payload, decoded.payload)
        assertEquals(original.hashCode(), decoded.hashCode())
    }

    @Test
    fun legacy_envelope_without_metadata_restores_with_an_empty_map(): Unit {
        val legacy = """
            {
              "sessionId": "snapshot-contract-session",
              "gameId": "snapshot-contract",
              "engineVersion": "1.2.3",
              "createdAt": "2026-09-07T00:00:00Z",
              "phaseId": "active",
              "payload": [0, -1, 127, -128]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(GameSnapshot.serializer(), legacy)
        assertTrue(decoded.metadata.isEmpty())
        assertEquals(snapshot().copy(metadata = emptyMap()), decoded)
        val reencoded = json.encodeToString(GameSnapshot.serializer(), decoded)
        assertEquals(decoded, json.decodeFromString(GameSnapshot.serializer(), reencoded))
    }

    private fun snapshot() = GameSnapshot(
        sessionId = SessionId("snapshot-contract-session"),
        gameId = GameId("snapshot-contract"),
        engineVersion = SemVer(1, 2, 3),
        createdAt = Instant.parse("2026-09-07T00:00:00Z"),
        phaseId = "active",
        payload = byteArrayOf(0, -1, 127, -128),
        metadata = mapOf("test-mode" to "synthetic"),
    )
}
