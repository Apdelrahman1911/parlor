package com.parlor.storage.secure

import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

    @Test
    fun adapter_maps_ordinary_backing_failures_without_exposing_details() = runTest {
        val storage = PlatformKeyedSecureStorage(FailingBacking(IllegalStateException("secret detail")))
        val expected = Result.Failure(DataError.IoError("secure_storage_io"))

        assertEquals(expected, storage.put("key", byteArrayOf(1)))
        assertEquals(expected, storage.get("key"))
        assertEquals(expected, storage.remove("key"))
    }

    @Test
    fun adapter_never_converts_fatal_errors_into_recoverable_io_failures() = runTest {
        val fatal = AssertionError("fatal")
        val storage = PlatformKeyedSecureStorage(FailingBacking(fatal))

        assertFailsWith<AssertionError> { storage.put("key", byteArrayOf(1)) }
        assertFailsWith<AssertionError> { storage.get("key") }
        assertFailsWith<AssertionError> { storage.remove("key") }
    }

    private class FailingBacking(
        private val failure: Throwable,
    ) : SecureKeyValueBacking {
        override suspend fun put(key: String, value: ByteArray): Nothing = throw failure

        override suspend fun get(key: String): Nothing = throw failure

        override suspend fun remove(key: String): Nothing = throw failure
    }
}
