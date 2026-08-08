package com.parlor.transport.p2p

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.io.File
import kotlin.test.Test

/**
 * Pins the contract between **P2pKit's service type** and the iOS app's
 * **`NSBonjourServices`** whitelist.
 *
 * iOS Local Network Privacy (iOS 14+) rejects any Bonjour browse/advertise
 * for a service type that is not declared in the app's `Info.plist`
 * `NSBonjourServices` array. P2pKit's LAN transport publishes/browses
 * `_p2pkit2._tcp` for P2pKit's authenticated-v2 LAN profile. If the iOS app's whitelist
 * doesn't include that, the OS denies the operation with
 * `DNSServiceBrowse failed: NoAuth(-65555)` — silently breaking every
 * cross-platform pairing where iOS is one end.
 *
 * This test reads the repo's `iosApp/iosApp/Info.plist` and asserts the
 * whitelist contains `_p2pkit2._tcp`. If a future P2pKit version changes
 * its service type, or someone clears the whitelist by mistake, this
 * fails with a clear diagnosis instead of a runtime regression that only
 * shows up on a real iPhone.
 */
class IosInfoPlistBonjourTest {

    private val expectedServiceType = "_p2pkit2._tcp"

    @Test
    fun ios_info_plist_whitelists_p2pkit_bonjour_service_type() {
        val plist = locateInfoPlist()
        val xml = plist.readText()

        // The plist is small; substring assertions on the XML are robust
        // enough without dragging in a full plist parser.
        val nsBonjourKey = "<key>NSBonjourServices</key>"
        assertThat(
            xml.contains(nsBonjourKey),
            "Info.plist must declare NSBonjourServices key (path=${plist.absolutePath})",
        ).isTrue()

        val keyIndex = xml.indexOf(nsBonjourKey)
        val arrayStart = xml.indexOf("<array>", startIndex = keyIndex)
        val arrayEnd = xml.indexOf("</array>", startIndex = arrayStart)
        assertThat(arrayStart >= 0 && arrayEnd > arrayStart).isTrue()
        val arrayBody = xml.substring(arrayStart, arrayEnd)

        assertThat(arrayBody).contains("<string>$expectedServiceType</string>")
    }

    /**
     * Walk up from the test's working directory to find the repository
     * root (the directory containing `iosApp/iosApp/Info.plist`). The
     * desktopTest cwd is typically a Gradle build subdirectory.
     */
    private fun locateInfoPlist(): File {
        val relative = "iosApp/iosApp/Info.plist"
        var dir: File? = File(".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        // Fall back to absolute repo path if discoverable via env / property.
        val fallback = System.getProperty("parlor.repoRoot")?.let { File(it, relative) }
        assertThat(
            fallback?.takeIf { it.exists() },
            "Could not locate $relative from $${File(".").absoluteFile}",
        ).isNotNull()
        return fallback!!
    }
}
