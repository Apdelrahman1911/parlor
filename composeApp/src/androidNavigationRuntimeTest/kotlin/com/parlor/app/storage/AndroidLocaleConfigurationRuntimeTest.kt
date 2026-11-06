package com.parlor.app.storage

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.test.InstrumentationTestCase
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import java.util.Locale

/**
 * A real, handled Android configuration transition, not a direct Locale mutation
 * or replaced Compose surface. Settings must keep its language and route in the
 * same Activity. This does not establish the earlier cold-start callback order.
 */
@Suppress("DEPRECATION")
class AndroidLocaleConfigurationRuntimeTest : InstrumentationTestCase() {
    override fun setUp() {
        super.setUp()
        instrumentation.waitForIdleSync()
    }

    fun testEnglishSettingsSurviveHandledOrientationChange() = verifyOrientation("en")

    fun testArabicSettingsSurviveHandledOrientationChange() = verifyOrientation("ar")

    private fun verifyOrientation(language: String) = runBlocking<Unit> {
        val application = instrumentation.targetContext.applicationContext as Application
        check(application.packageName.endsWith(".debug"))
        val settings = application.getKoin().get<SettingsStore>()
        val previousLanguage = settings.languageOverride.first()
        val previousMotion = settings.reducedMotion.first()
        var activity: Activity? = null
        try {
            settings.setLanguageOverride(language)
            settings.setReducedMotion(true)
            val launched = instrumentation.startActivitySync(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(application.packageName, "com.parlor.app.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity = launched
            instrumentation.waitForIdleSync()
            val evidence = AndroidRecoveryFailureEvidence(instrumentation, launched, AndroidRecoveryPublicLabels())
            val ui = AndroidPublicRecoveryUi(instrumentation, evidence::capture)
            ui.clickDescription(if (language == "ar") "افتح الإعدادات." else "Open settings.")
            val heading = if (language == "ar") "الإعدادات" else "Settings"
            ui.awaitHeading(heading)
            val initial = orientation(launched)
            check(initial == Configuration.ORIENTATION_PORTRAIT || initial == Configuration.ORIENTATION_LANDSCAPE)
            val target = if (initial == Configuration.ORIENTATION_PORTRAIT) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
            instrumentation.runOnMainSync {
                launched.requestedOrientation = if (target == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            // Observe this one platform request; never repeat a rotation or tap.
            val deadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MILLIS
            while (orientation(launched) != target && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(ORIENTATION_INTERVAL_MILLIS)
            }
            assertEquals(target, orientation(launched))
            assertFalse("Manifest-handled orientation must not destroy the Activity", launched.isDestroyed)
            assertEquals(language, settings.languageOverride.first())
            try {
                ui.awaitHeading(heading)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalStateException) {
                val english = ui.headingPresentNow("Settings")
                val arabic = ui.headingPresentNow("الإعدادات")
                var processLanguageMatches = false
                instrumentation.runOnMainSync { processLanguageMatches = Locale.getDefault().language == language }
                throw AssertionError("Handled configuration: english=$english, arabic=$arabic, " +
                    "processLanguageMatches=$processLanguageMatches, activityDestroyed=${launched.isDestroyed}", failure)
            }
        } finally {
            try {
                activity?.let { launched ->
                    instrumentation.runOnMainSync { launched.finish() }
                    instrumentation.waitForIdleSync()
                }
            } finally {
                settings.setLanguageOverride(previousLanguage)
                settings.setReducedMotion(previousMotion)
            }
        }
    }

    private fun orientation(activity: Activity): Int {
        var orientation = Configuration.ORIENTATION_UNDEFINED
        instrumentation.runOnMainSync { orientation = activity.resources.configuration.orientation }
        return orientation
    }

    private companion object {
        const val ORIENTATION_TIMEOUT_MILLIS = 10_000L
        const val ORIENTATION_INTERVAL_MILLIS = 100L
    }
}
