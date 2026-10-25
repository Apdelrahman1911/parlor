package com.parlor.games.whodunit.protocol

import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.snapshot.WhodunitSnapshotCodec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WhodunitJsonResourceLimitTest {
    @Test
    fun deeplyNestedCommandIsRejectedAsRecoverableInputFailure() {
        assertFailsWith<IllegalArgumentException> { WhodunitActionCodec.decode(hostilePayload()) }
    }

    @Test
    fun deeplyNestedSnapshotIsRejectedWithoutRecursiveParsing() {
        assertFailsWith<IllegalArgumentException> { WhodunitSnapshotCodec(Json).decode(hostilePayload()) }
    }

    private fun hostilePayload(): ByteArray =
        ("{\"unexpected\":" + "[".repeat(4_096) + "0" + "]".repeat(4_096) + "}").encodeToByteArray()
}
