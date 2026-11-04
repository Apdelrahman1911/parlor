@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionUnavailableException
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeSupportsFileProtectionKey

/** Creation/write options do not repair an existing item. Verify after setting its class. */
internal fun repairCompleteSnapshotProtection(fileManager: NSFileManager, path: String) {
    val protectionKey = NSFileProtectionKey ?: throw SnapshotProtectionUnavailableException()
    val completeProtection = NSFileProtectionComplete ?: throw SnapshotProtectionUnavailableException()
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        val configured = fileManager.setAttributes(
            attributes = mapOf(protectionKey to completeProtection),
            ofItemAtPath = path,
            error = error.ptr,
        )
        if (!configured || error.value != null) throw SnapshotProtectionUnavailableException()
    }
    requireCompleteSnapshotProtection(fileManager, path)
}

/** Metadata verification is necessary, but does not prove physical device-lock enforcement. */
internal fun requireCompleteSnapshotProtection(fileManager: NSFileManager, path: String) {
    val protectionKey = NSFileProtectionKey ?: throw SnapshotProtectionUnavailableException()
    val volumeKey = NSURLVolumeSupportsFileProtectionKey ?: throw SnapshotProtectionUnavailableException()
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        // Use a fresh URL; never trust resource values cached by a preceding setter.
        val volumeValues = NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(volumeKey), error.ptr)
        val volumeSupportsProtection = if (error.value == null) {
            (volumeValues?.get(volumeKey) as? NSNumber)?.boolValue
        } else {
            null
        }
        error.value = null
        val attributes = fileManager.attributesOfItemAtPath(path, error.ptr)
        requireVerifiedCompleteSnapshotProtection(
            volumeSupportsProtection = volumeSupportsProtection,
            attributesReadSucceeded = error.value == null && attributes != null,
            observedProtection = attributes?.get(protectionKey) as? String,
            completeProtection = NSFileProtectionComplete,
        )
    }
}
