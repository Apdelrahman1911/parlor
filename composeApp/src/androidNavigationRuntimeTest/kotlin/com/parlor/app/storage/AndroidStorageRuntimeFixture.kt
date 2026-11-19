package com.parlor.app.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

/** Isolate only this fixture's records; never clear app data or delete shared Keystore aliases. */
internal class AndroidStorageRuntimeFixture(context: Context) : ContextWrapper(context), AutoCloseable {
    private val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "parlor-native-storage-").toFile()
    private val preferenceNames = mutableSetOf<String>()

    // AndroidSecureKeyValueBacking intentionally retains applicationContext.
    // Preserve the isolated paths when it resolves that context.
    override fun getApplicationContext(): Context = this
    override fun getNoBackupFilesDir(): File = directory("no-backup")
    override fun getFilesDir(): File = directory("legacy")

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val ownedName = "${root.name}-$name"
        preferenceNames += ownedName
        return baseContext.getSharedPreferences(ownedName, mode)
    }

    fun snapshots() = FileBackedSnapshotStore(AndroidSnapshotFileSystem(this), Json)
    fun credentials() = PlatformKeyedSecureStorage(AndroidSecureKeyValueBacking(this))

    fun snapshotFile(snapshot: GameSnapshot): File =
        File(File(noBackupFilesDir, "snapshots"), snapshot.sessionId.raw + FileBackedSnapshotStore.SUFFIX)

    fun legacyFile(snapshot: GameSnapshot): File =
        File(File(filesDir, "snapshots"), snapshot.sessionId.raw + FileBackedSnapshotStore.SUFFIX).also {
            check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory)
        }

    fun credentialFile(key: String): File = File(File(noBackupFilesDir, "secure-credentials"), "$key.bin")

    private fun directory(name: String): File = File(root, name).also { check(it.mkdirs() || it.isDirectory) }

    override fun close() {
        try {
            preferenceNames.forEach { check(baseContext.deleteSharedPreferences(it)) }
        } finally {
            check(root.deleteRecursively())
        }
    }
}
