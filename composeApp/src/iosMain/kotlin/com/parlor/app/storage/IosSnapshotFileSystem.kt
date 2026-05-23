package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotFileSystem
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

/**
 * iOS `SnapshotFileSystem` — files live under
 * `<NSDocumentDirectory>/snapshots/`. Documents are app-private by default
 * on iOS; iCloud backup behavior is the system default. Phase 8 polish can
 * exclude the directory from backup if/when appropriate.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSnapshotFileSystem : SnapshotFileSystem {

    private val fileManager: NSFileManager get() = NSFileManager.defaultManager

    private val baseUrl: NSURL by lazy {
        val docs = fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("Couldn't resolve NSDocumentDirectory")
        val snapshots = docs.URLByAppendingPathComponent("snapshots")
            ?: error("Couldn't append snapshots component")
        fileManager.createDirectoryAtURL(
            url = snapshots,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        snapshots
    }

    private fun fileUrl(name: String): NSURL =
        baseUrl.URLByAppendingPathComponent(name)
            ?: error("Couldn't build URL for $name")

    override suspend fun read(name: String): ByteArray? {
        val url = fileUrl(name)
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return data.toByteArray()
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        data.writeToURL(fileUrl(name), atomically = true)
    }

    override suspend fun delete(name: String) {
        fileManager.removeItemAtURL(fileUrl(name), null)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun list(): List<String> {
        val contents = fileManager.contentsOfDirectoryAtURL(
            url = baseUrl,
            includingPropertiesForKeys = null,
            options = 0u,
            error = null,
        ) as? List<NSURL> ?: return emptyList()
        return contents.mapNotNull { it.lastPathComponent }.filter { !it.endsWith(".tmp") }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val result = ByteArray(length)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return result
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}
