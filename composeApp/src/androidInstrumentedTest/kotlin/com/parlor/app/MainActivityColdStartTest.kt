package com.parlor.app

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.system.Os
import android.system.OsConstants
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real release Activity without depending on an Android test framework artifact. */
@Suppress("DEPRECATION")
class MainActivityColdStartTest : InstrumentationTestCase() {
    fun testColdStartDisplaysContentWhileSettingsIoIsBlocked() {
        val instrumentation = instrumentation
        val application = instrumentation.targetContext.applicationContext
        val preferencesDirectory = File(application.applicationInfo.dataDir, "shared_prefs")
        check(preferencesDirectory.isDirectory || preferencesDirectory.mkdirs()) {
            "Could not create the settings directory"
        }
        val settingsFile = File(preferencesDirectory, "$SETTINGS_FILE_NAME.xml")
        val settingsBackup = File(preferencesDirectory, "$SETTINGS_FILE_NAME.xml.bak")
        check(!settingsFile.exists() || settingsFile.delete()) { "Could not reset the settings file" }
        check(!settingsBackup.exists() || settingsBackup.delete()) { "Could not reset the settings backup" }

        // SharedPreferences reads its XML on a worker and blocks callers until
        // that read completes. A FIFO lets this test hold the real disk read
        // without introducing a production-only injection hook.
        Os.mkfifo(settingsFile.path, OsConstants.S_IRUSR or OsConstants.S_IWUSR)
        val settingsReaderConnected = CountDownLatch(1)
        val releaseSettingsRead = CountDownLatch(1)
        val settingsWriterFinished = CountDownLatch(1)
        val settingsWriterFailure = AtomicReference<Throwable?>()
        val settingsWriter = Thread(
            {
                try {
                    val output = FileOutputStream(settingsFile)
                    try {
                        settingsReaderConnected.countDown()
                        check(releaseSettingsRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            "Timed out waiting for the startup assertion"
                        }
                        output.write(EMPTY_SETTINGS_XML)
                        output.flush()
                    } finally {
                        output.close()
                    }
                } catch (failure: Throwable) {
                    settingsWriterFailure.set(failure)
                } finally {
                    settingsWriterFinished.countDown()
                }
            },
            "parlor-settings-test-writer",
        ).also(Thread::start)

        var activity: Activity? = null
        var composeRoot: View? = null
        var drawListener: ViewTreeObserver.OnDrawListener? = null
        var cleanupReaderDescriptor: java.io.FileDescriptor? = null
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .setClassName(STORE_APPLICATION_ID, "$STORE_APPLICATION_ID.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val launchedActivity = instrumentation.startActivitySync(intent)
            activity = launchedActivity

            assertTrue(
                "MainActivity never attempted the controlled settings disk read",
                settingsReaderConnected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertEquals(
                "The settings read must still be blocked while the first frame is inspected",
                1L,
                settingsWriterFinished.count,
            )
            instrumentation.waitForIdleSync()

            val contentDrawn = CountDownLatch(1)
            val drawObservation = observeComposeDraw(launchedActivity, contentDrawn)
            composeRoot = drawObservation.root
            drawListener = drawObservation.listener
            assertTrue(
                "Compose content did not draw while settings I/O was blocked",
                contentDrawn.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertEquals(
                "Settings I/O completed before the observed first frame",
                1L,
                settingsWriterFinished.count,
            )
        } finally {
            val installedComposeRoot = composeRoot
            val installedDrawListener = drawListener
            if (installedComposeRoot != null && installedDrawListener != null) {
                instrumentation.runOnMainSync {
                    val observer = installedComposeRoot.viewTreeObserver
                    if (observer.isAlive) observer.removeOnDrawListener(installedDrawListener)
                }
            }

            // If startup failed before SharedPreferences opened the FIFO, add
            // a non-blocking reader so the writer thread can still terminate.
            if (settingsReaderConnected.count != 0L && settingsWriter.isAlive) {
                cleanupReaderDescriptor = Os.open(
                    settingsFile.path,
                    OsConstants.O_RDONLY or OsConstants.O_NONBLOCK,
                    0,
                )
            }
            releaseSettingsRead.countDown()
            check(settingsWriterFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Settings FIFO writer did not terminate"
            }
            if (cleanupReaderDescriptor != null) Os.close(cleanupReaderDescriptor)

            activity?.let { launchedActivity ->
                instrumentation.runOnMainSync { launchedActivity.finish() }
                instrumentation.waitForIdleSync()
            }
            check(!settingsFile.exists() || settingsFile.delete()) { "Could not remove the settings FIFO" }
            val restoredSettings = FileOutputStream(settingsFile)
            try {
                restoredSettings.write(EMPTY_SETTINGS_XML)
                restoredSettings.flush()
            } finally {
                restoredSettings.close()
            }
        }
        settingsWriterFailure.get()?.let { throw AssertionError("Settings FIFO writer failed", it) }
    }

    private fun observeComposeDraw(
        activity: Activity,
        contentDrawn: CountDownLatch,
    ): DrawObservation {
        var observation: DrawObservation? = null
        instrumentation.runOnMainSync {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertNotNull("The Activity must own an Android content container", content)
            checkNotNull(content)
            assertTrue(
                "Compose content was not installed before settings I/O completed",
                content.childCount > 0,
            )

            val composeRoot = content.getChildAt(0)
            assertEquals(View.VISIBLE, composeRoot.visibility)
            assertTrue("Compose content is not attached", composeRoot.isAttachedToWindow)
            assertTrue("Compose content is not shown", composeRoot.isShown)
            val visibleBounds = Rect()
            assertTrue(
                "Compose content has no visible bounds",
                composeRoot.getGlobalVisibleRect(visibleBounds),
            )
            assertTrue("Compose content has zero width", visibleBounds.width() > 0)
            assertTrue("Compose content has zero height", visibleBounds.height() > 0)

            val listener = ViewTreeObserver.OnDrawListener { contentDrawn.countDown() }
            composeRoot.viewTreeObserver.addOnDrawListener(listener)
            composeRoot.invalidate()
            observation = DrawObservation(composeRoot, listener)
        }
        return checkNotNull(observation)
    }

    private data class DrawObservation(
        val root: View,
        val listener: ViewTreeObserver.OnDrawListener,
    )

    private companion object {
        const val STORE_APPLICATION_ID = "com.parlor.app"
        const val SETTINGS_FILE_NAME = "parlor_settings_v1"
        const val TIMEOUT_SECONDS = 30L
        val EMPTY_SETTINGS_XML = (
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<map />\n"
            ).toByteArray(Charsets.UTF_8)
    }
}
