@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.parlor.app.storage

import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileNoSuchFileError
import platform.Foundation.NSFileReadNoPermissionError
import platform.Foundation.NSFileReadNoSuchFileError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Foundation path operations and controlled error contracts; not physical lock/keybag evidence. */
class IosSnapshotPathOperationsTest {
    @Test
    fun realMissingItemIsAbsentAndDirectDiscardIsIdempotent() {
        val path = newPath()
        assertFalse(snapshotItemExists(fileManager, path))
        removeSnapshotItemIfPresent(fileManager, path)
        assertFalse(snapshotItemExists(fileManager, path))
    }

    @Test
    fun realExistingItemIsObservedAndDirectDiscardRemovesIt() {
        val path = newPath()
        try {
            assertTrue(fileManager.createDirectoryAtPath(path, false, null, null))
            assertTrue(snapshotItemExists(fileManager, path))
            removeSnapshotItemIfPresent(fileManager, path)
            assertFalse(snapshotItemExists(fileManager, path))
        } finally {
            removeSnapshotItemIfPresent(fileManager, path)
        }
    }

    @Test
    fun onlyCanonicalMissingErrorsPermitAbsenceOrCompletedDiscard() {
        for (code in listOf(NSFileNoSuchFileError, NSFileReadNoSuchFileError)) {
            val missing = error(NSCocoaErrorDomain, code)
            assertFalse(checkedSnapshotItemPresence(false, missing))
            checkedSnapshotItemRemoval(false, missing)
        }
        assertTrue(checkedSnapshotItemPresence(true, null))
        checkedSnapshotItemRemoval(true, null)
    }

    @Test
    fun unavailableOrUnspecifiedLookupNeverMeansMissing() {
        for (failure in listOf(null, error(NSCocoaErrorDomain, NSFileReadNoPermissionError),
            error("synthetic-foreign-domain", NSFileReadNoSuchFileError))) {
            assertEquals("Couldn't inspect protected snapshot", assertFailsWith<IllegalStateException> {
                checkedSnapshotItemPresence(false, failure)
            }.message)
            assertEquals("Couldn't delete snapshot", assertFailsWith<IllegalStateException> {
                checkedSnapshotItemRemoval(false, failure)
            }.message)
        }
    }

    @Test
    fun contradictorySuccessWithErrorFailsClosed() {
        for (code in listOf(NSFileReadNoPermissionError, NSFileReadNoSuchFileError)) {
            val failure = error(NSCocoaErrorDomain, code)
            assertFailsWith<IllegalStateException> { checkedSnapshotItemPresence(true, failure) }
            assertFailsWith<IllegalStateException> { checkedSnapshotItemRemoval(true, failure) }
        }
    }

    private fun newPath(): String = "${NSTemporaryDirectory()}parlor-path-${NSUUID.UUID().UUIDString}"

    private fun error(domain: String?, code: Long): NSError = NSError.errorWithDomain(requireNotNull(domain), code, null)

    private companion object {
        val fileManager: NSFileManager = NSFileManager.defaultManager
    }
}
