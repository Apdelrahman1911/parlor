package com.parlor.games.mafia.protocol

import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.snapshot.MafiaSnapshotCodec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MafiaJsonResourceLimitTest {
    @Test
    fun deeplyNestedCommandIsRejectedAsRecoverableInputFailure() {
        assertFailsWith<IllegalArgumentException> { MafiaActionCodec.decode(hostilePayload()) }
    }

    @Test
    fun deeplyNestedSnapshotIsRejectedWithoutRecursiveParsing() {
        assertFailsWith<IllegalArgumentException> { MafiaSnapshotCodec(Json).decode(hostilePayload()) }
    }

    private fun hostilePayload(): ByteArray =
        ("{\"unexpected\":" + "[".repeat(4_096) + "0" + "]".repeat(4_096) + "}").encodeToByteArray()
}
