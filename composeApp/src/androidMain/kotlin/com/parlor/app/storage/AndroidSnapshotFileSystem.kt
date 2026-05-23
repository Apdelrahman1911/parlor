package com.parlor.app.storage

import android.content.Context
import com.parlor.storage.snapshot.SnapshotFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android `SnapshotFileSystem` — files live under
 * `Context.filesDir/snapshots/`. Internal app storage, never on the public
 * SD card. Encryption-at-rest (via Android Keystore) is Phase 8 polish; for
 * now snapshots are plain JSON on disk.
 */
class AndroidSnapshotFileSystem(private val context: Context) : SnapshotFileSystem {

    private val baseDir: File
        get() = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(baseDir, name)
        if (file.exists()) file.readBytes() else null
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val file = File(baseDir, name)
            val tmp = File(baseDir, "$name.tmp")
            tmp.writeBytes(bytes)
            // Atomic rename keeps reads from seeing a half-written file.
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            File(baseDir, name).delete()
        }
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        baseDir.listFiles()?.mapNotNull { it.name.takeUnless { n -> n.endsWith(".tmp") } } ?: emptyList()
    }

    private companion object {
        const val DIRECTORY = "snapshots"
    }
}
