package com.parlor.app.storage

import java.io.File
import java.nio.file.Files

/** A real nonempty-directory delete failure, restricted to one exclusively owned synthetic save. */
internal class AndroidSelectedSnapshotDeleteFault(private val record: File) : AutoCloseable {
    private val original = record.also {
        check(it.name.startsWith("native-home-") && it.isFile && !Files.isSymbolicLink(it.toPath()))
    }.readBytes()
    private val blocker = File(record, "native-owned-blocker")
    private var installed = false

    fun install() {
        // Android's ordinary /data/user/0 parent may resolve through a platform
        // symlink. Reject a link at our record, not the legitimate sandbox path.
        check(!installed && record.isFile && !Files.isSymbolicLink(record.toPath()))
        check(record.delete())
        installed = true
        check(record.mkdir())
        blocker.writeText("synthetic delete-failure fixture")
    }

    fun restore() {
        if (!installed) return
        if (record.isDirectory) {
            check(record.listFiles()?.all { it == blocker } == true)
            if (blocker.exists()) check(blocker.delete())
            check(record.delete())
        } else {
            check(!record.exists()) { "Fault record changed ownership" }
        }
        // Only the original authenticated ciphertext is restored, never plaintext.
        record.writeBytes(original)
        check(record.readBytes().contentEquals(original))
        installed = false
    }

    override fun close() {
        try {
            restore()
        } finally {
            original.fill(0)
        }
    }
}
