package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class AndroidSnapshotDirectoryListingTest {
    @Test
    fun null_directory_listing_is_reported_as_storage_failure() {
        assertFailsWith<SnapshotProtectionException> {
            requireSnapshotDirectoryListing(null)
        }
    }

    @Test
    fun readable_directory_listing_is_preserved() {
        val files = arrayOf(File("one"), File("two"))

        assertContentEquals(files, requireSnapshotDirectoryListing(files))
    }
}
