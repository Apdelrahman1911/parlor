@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileNoSuchFileError
import platform.Foundation.NSFileReadNoSuchFileError

/** A false Boolean existence query also means inaccessible, not just absent. */
internal fun snapshotItemExists(fileManager: NSFileManager, path: String): Boolean = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val attributes = fileManager.attributesOfItemAtPath(path, error.ptr)
    checkedSnapshotItemPresence(attributes != null, error.value)
}

/** Success is an observation only; subsequent reads must still handle their own failures. */
internal fun checkedSnapshotItemPresence(attributesFound: Boolean, error: NSError?): Boolean {
    if (attributesFound && error == null) return true
    if (!attributesFound && isMissingSnapshotItem(error)) return false
    throw IllegalStateException("Couldn't inspect protected snapshot")
}

/** Discard must attempt removal, not infer success from an ambiguous existence preflight. */
internal fun removeSnapshotItemIfPresent(fileManager: NSFileManager, path: String) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    checkedSnapshotItemRemoval(fileManager.removeItemAtPath(path, error.ptr), error.value)
}

internal fun checkedSnapshotItemRemoval(removed: Boolean, error: NSError?) {
    if (removed && error == null) return
    if (!removed && isMissingSnapshotItem(error)) return
    throw IllegalStateException("Couldn't delete snapshot")
}

private fun isMissingSnapshotItem(error: NSError?): Boolean =
    error != null && error.domain == NSCocoaErrorDomain &&
        (error.code == NSFileNoSuchFileError || error.code == NSFileReadNoSuchFileError)
