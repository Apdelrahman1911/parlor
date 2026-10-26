package com.parlor.games.lastlight.protocol

import com.parlor.games.lastlight.protocol.LastLightActionCodec
import com.parlor.games.lastlight.snapshot.LastLightSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LastLightJsonResourceLimitTest {
    @Test
    fun deeplyNestedCommandIsRejectedAsRecoverableInputFailure() {
        assertFailsWith<IllegalArgumentException> { LastLightActionCodec.decode(hostilePayload()) }
    }

    @Test
    fun deeplyNestedSnapshotIsRejectedWithoutRecursiveParsing() {
        assertFailsWith<IllegalArgumentException> { LastLightSnapshotCodec().decode(hostilePayload()) }
    }

    private fun hostilePayload(): ByteArray =
        ("{\"unexpected\":" + "[".repeat(4_096) + "0" + "]".repeat(4_096) + "}").encodeToByteArray()
}
