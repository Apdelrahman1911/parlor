package com.parlor.app.storage

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformStorageMigrationTest {
    @Test
    fun one_corrupt_legacy_record_does_not_hide_or_skip_later_records() {
        val names = listOf("broken.snapshot.json", "healthy.snapshot.json")
        val attempted = mutableListOf<String>()

        val visible = migrateSnapshotRecordsIndependently(names) { name ->
            attempted += name
            if (name.startsWith("broken")) error("corrupt record")
        }

        assertEquals(names, visible)
        assertEquals(names, attempted)
    }

    @Test
    fun cancellation_is_not_downgraded_to_an_individual_migration_failure() {
        assertFailsWith<CancellationException> {
            migrateSnapshotRecordsIndependently(listOf("save.snapshot.json")) {
                throw CancellationException("cancel migration")
            }
        }
    }
}
