package com.parlor.content.schema

import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IntRangePairSerializerTest {
    @Test
    fun descriptorMatchesTheJsonArrayWireShape() {
        assertEquals(StructureKind.LIST, IntRangePair.Serializer.descriptor.kind)
    }

    @Test
    fun correctedDescriptorPreservesTheExistingWireFormat() {
        val json = Json
        val value = IntRangePair(4, 8)

        assertEquals("[4,8]", json.encodeToString(IntRangePair.Serializer, value))
        assertEquals(value, json.decodeFromString(IntRangePair.Serializer, "[4,8]"))
    }
}
