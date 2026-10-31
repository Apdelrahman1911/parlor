package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryRoomBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SnapshotPayloadOwnershipTest {
    private val first = PlayerId("peer-one")
    private val second = PlayerId("peer-two")

    @Test
    fun reused_private_serialization_buffer_cannot_cross_recipient_outboxes() = runTest {
        val room = SnapshotRoom()
        val privateBuffer = byteArrayOf(0)
        val coordinator = coordinator(room, this, setOf(first, second)) { player ->
            privateBuffer[0] = if (player == first) 11 else 22
            PlayerSnapshotPayload(byteArrayOf(7), privateBuffer)
        }
        try {
            coordinator.publishState(incrementRevision = false)
            runCurrent()

            assertEquals(2, room.deliveries.size)
            val byRecipient = room.deliveries.associateBy { it.target }
            assertContentEquals(byteArrayOf(11), byRecipient.getValue(SendTarget.Direct(first)).privatePayload)
            assertContentEquals(byteArrayOf(22), byRecipient.getValue(SendTarget.Direct(second)).privatePayload)
            room.deliveries.forEach { assertContentEquals(byteArrayOf(7), it.publicPayload) }
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun producer_buffer_mutation_cannot_change_an_inflight_snapshot_revision() = runTest {
        val release = CompletableDeferred<Unit>()
        val room = SnapshotRoom(release)
        val publicBuffer = byteArrayOf(1)
        val privateBuffer = byteArrayOf(2)
        val coordinator = coordinator(room, this, setOf(first)) {
            PlayerSnapshotPayload(publicBuffer, privateBuffer)
        }
        try {
            coordinator.publishState(incrementRevision = false)
            runCurrent()
            assertTrue(room.started.isCompleted)
            assertTrue(room.deliveries.isEmpty())

            publicBuffer[0] = 9
            privateBuffer[0] = 8
            release.complete(Unit)
            runCurrent()

            val delivered = room.deliveries.single()
            assertEquals(0L, delivered.revision)
            assertContentEquals(byteArrayOf(1), delivered.publicPayload)
            assertContentEquals(byteArrayOf(2), delivered.privatePayload)
        } finally {
            release.complete(Unit)
            coordinator.close()
        }
    }

    @Test
    fun exact_snapshot_byte_limit_and_empty_payloads_remain_accepted() = runTest {
        val room = SnapshotRoom()
        var empty = false
        val coordinator = coordinator(room, this, setOf(first)) {
            if (empty) PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) else
                PlayerSnapshotPayload(ByteArray(MAX_SNAPSHOT_PAYLOAD_BYTES - 1) { 1 }, byteArrayOf(2))
        }
        try {
            coordinator.publishState(incrementRevision = false)
            runCurrent()
            val full = room.deliveries.single()
            assertEquals(MAX_SNAPSHOT_PAYLOAD_BYTES - 1, full.publicPayload.size)
            assertTrue(full.publicPayload.all { it == 1.toByte() })
            assertContentEquals(byteArrayOf(2), full.privatePayload)

            empty = true
            coordinator.publishState(incrementRevision = false)
            runCurrent()
            assertEquals(2, room.deliveries.size)
            assertContentEquals(byteArrayOf(), room.deliveries.last().publicPayload)
            assertContentEquals(byteArrayOf(), room.deliveries.last().privatePayload)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun oversized_snapshot_is_not_queued_and_does_not_poison_the_host_worker() = runTest {
        val room = SnapshotRoom()
        var oversized = true
        val coordinator = coordinator(room, this, setOf(first)) {
            PlayerSnapshotPayload(
                ByteArray(if (oversized) MAX_SNAPSHOT_PAYLOAD_BYTES + 1 else 1),
                byteArrayOf(),
            )
        }
        try {
            coordinator.publishState(incrementRevision = false)
            runCurrent()
            assertTrue(room.deliveries.isEmpty())
            oversized = false
            coordinator.publishState(incrementRevision = false)
            runCurrent()
            assertEquals(1, room.deliveries.size)
            assertEquals(1, room.deliveries.single().publicPayload.size)
        } finally {
            coordinator.close()
        }
    }

    private fun coordinator(
        room: LocalRoom,
        scope: CoroutineScope,
        players: Set<PlayerId>,
        snapshotFor: suspend (PlayerId) -> PlayerSnapshotPayload,
    ) = HostAuthoritativeSessionCoordinator(
        room = room,
        protocol = SessionProtocol(SessionId("session-0123456789"), GameId("fixture-game"), 1),
        remotePlayers = players,
        scope = scope,
        applyCommand = { _, _ -> CommandApplication.InvalidAction },
        snapshotFor = snapshotFor,
        heartbeatIntervalMs = 0L,
        requireStartHandshake = false,
    )
}

private data class SnapshotDelivery(
    val target: SendTarget,
    val revision: Long,
    val publicPayload: ByteArray,
    val privatePayload: ByteArray,
)

private class SnapshotRoom(
    private val release: CompletableDeferred<Unit>? = null,
) : LocalRoom by InMemoryHostRoom(InMemoryRoomBus(), PlayerId("host"), "Fixture") {
    val started = CompletableDeferred<Unit>()
    val deliveries = mutableListOf<SnapshotDelivery>()

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        if (message is HostMessage.PlayerSnapshot) {
            started.complete(Unit)
            release?.await()
            // Copy at the transport boundary, not after producer mutation:
            // assertions examine the bytes this send actually received.
            deliveries += SnapshotDelivery(
                target, message.revision, message.publicPayload.copyOf(), message.privatePayload.copyOf(),
            )
        }
        return Result.Success(Unit)
    }
}
