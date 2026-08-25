package com.parlor.app.storage

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DesktopSnapshotFileSystemTest {
    @Test
    fun emptyEncryptedSnapshotRoundTrips() = runTest {
        val root = Files.createTempDirectory("parlor-desktop-snapshot-test")
        try {
            val fileSystem = DesktopSnapshotFileSystem(
                baseDir = root.resolve("snapshots"),
                keyPath = root.resolve("snapshot-key.bin"),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )

            fileSystem.write("empty.snapshot.json", ByteArray(0))

            assertContentEquals(ByteArray(0), fileSystem.read("empty.snapshot.json"))
        } finally {
            assertTrue(root.toFile().deleteRecursively())
        }
    }
}
