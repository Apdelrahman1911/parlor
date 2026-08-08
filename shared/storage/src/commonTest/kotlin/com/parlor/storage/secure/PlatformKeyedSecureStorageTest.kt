package com.parlor.storage.secure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class PlatformKeyedSecureStorageTest {

    @Test
    fun in_memory_test_backing_never_aliases_caller_arrays() = runTest {
        val backing = InMemorySecureKeyValueBacking()
        val original = byteArrayOf(1, 2, 3)
        backing.put("key", original)
        original.fill(9)

        val firstRead = requireNotNull(backing.get("key"))
        assertContentEquals(byteArrayOf(1, 2, 3), firstRead)
        firstRead.fill(8)
        assertContentEquals(byteArrayOf(1, 2, 3), backing.get("key"))
    }

    @Test
    fun adapter_preserves_lifecycle_cancellation() = runTest {
        val storage = PlatformKeyedSecureStorage(
            object : SecureKeyValueBacking {
                override suspend fun put(key: String, value: ByteArray) {
                    throw CancellationException("stopped")
                }

                override suspend fun get(key: String): ByteArray? {
                    throw CancellationException("stopped")
                }

                override suspend fun remove(key: String) {
                    throw CancellationException("stopped")
                }
            },
        )

        assertFailsWith<CancellationException> {
            storage.put("key", byteArrayOf(1))
        }
        assertFailsWith<CancellationException> {
            storage.get("key")
        }
        assertFailsWith<CancellationException> {
            storage.remove("key")
        }
    }
}
