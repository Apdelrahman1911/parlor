package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Desktop (JVM) `SnapshotFileSystem` — files live under
 * `~/.parlor/snapshots/` cross-platform. Phase 8 polish swaps this for the
 * proper per-OS user-config dir (`~/Library/Application Support/Parlor` on
 * macOS, `%APPDATA%/Parlor` on Windows, `$XDG_CONFIG_HOME/parlor` on Linux);
 * for the first cut, a single hidden directory in the user home is the
 * smallest reversible move and works identically everywhere.
 */
class DesktopSnapshotFileSystem(
    private val baseDir: Path = Path.of(System.getProperty("user.home"), ".parlor", "snapshots"),
) : SnapshotFileSystem {

    init {
        Files.createDirectories(baseDir)
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val path = baseDir.resolve(name)
        if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = baseDir.resolve(name)
            val tmp = baseDir.resolve("$name.tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(baseDir.resolve(name))
        }
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        if (!Files.isDirectory(baseDir)) return@withContext emptyList()
        Files.list(baseDir).use { stream ->
            stream
                .map { it.fileName.toString() }
                .filter { !it.endsWith(".tmp") }
                .toList()
        }
    }
}
