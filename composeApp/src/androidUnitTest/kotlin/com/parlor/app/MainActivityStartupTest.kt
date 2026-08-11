package com.parlor.app

import com.parlor.storage.settings.InMemorySettingsStore
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

class MainActivityStartupTest {
    @Test
    fun settingsAreResolvedOnTheOwnedInitializationDispatcher() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, SETTINGS_THREAD_NAME)
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val expected = InMemorySettingsStore()
            var resolutionThread: String? = null

            val actual = loadSettingsForFirstComposition(dispatcher = dispatcher) {
                resolutionThread = Thread.currentThread().name
                expected
            }

            assertSame(expected, actual)
            assertTrue(resolutionThread?.startsWith(SETTINGS_THREAD_NAME) == true)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private companion object {
        const val SETTINGS_THREAD_NAME = "parlor-settings-initialization-test"
    }
}
