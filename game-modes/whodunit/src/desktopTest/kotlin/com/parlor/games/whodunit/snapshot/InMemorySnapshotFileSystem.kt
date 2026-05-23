package com.parlor.games.whodunit.snapshot

import com.parlor.storage.snapshot.SnapshotFileSystem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test-only in-memory `SnapshotFileSystem` — same contract as the platform
 * actuals, no disk I/O. Shared by the snapshot round-trip and resume
 * reconstruction tests so they exercise an identical filesystem layer.
 */
internal class InMemorySnapshotFileSystem : SnapshotFileSystem {
    private val mutex = Mutex()
    private val files: MutableMap<String, ByteArray> = mutableMapOf()

    override suspend fun read(name: String): ByteArray? = mutex.withLock { files[name] }

    override suspend fun write(name: String, bytes: ByteArray) {
        mutex.withLock { files[name] = bytes }
    }

    override suspend fun delete(name: String) {
        mutex.withLock { files.remove(name) }
    }

    override suspend fun list(): List<String> = mutex.withLock { files.keys.toList() }
}
