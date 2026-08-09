package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pins Parlor's Android application manifest to the LAN transport's least-privilege contract. */
class AndroidManifestLanPermissionTest {

    @Test
    fun android_manifest_declares_only_the_base_lan_permissions() {
        val manifest = locateRepositoryRoot()
            .resolve("composeApp/src/androidMain/AndroidManifest.xml")
            .readText()
        val declaredPermissions = Regex(
            pattern = """<uses-permission\s+android:name="([^"]+)"\s*/>""",
        ).findAll(manifest).map { match -> match.groupValues[1] }.toSet()

        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            ),
            declaredPermissions,
            "The base P2pKit LAN transport must not acquire provisioning, nearby, location, or Bluetooth access",
        )
        assertFalse("NEARBY_WIFI_DEVICES" in manifest)
        assertFalse("ACCESS_FINE_LOCATION" in manifest)
        assertFalse("ACCESS_COARSE_LOCATION" in manifest)
        assertFalse("BLUETOOTH" in manifest)
        assertTrue("android:usesCleartextTraffic=\"false\"" in manifest)
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertTrue("android:fullBackupContent=\"@xml/backup_rules\"" in manifest)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifest)
    }

    @Test
    fun android_backup_and_device_transfer_policies_exclude_all_app_data() {
        val repositoryRoot = locateRepositoryRoot()
        val legacyPolicy = repositoryRoot
            .resolve("composeApp/src/androidMain/res/xml/backup_rules.xml")
            .readText()
        val modernPolicy = repositoryRoot
            .resolve("composeApp/src/androidMain/res/xml/data_extraction_rules.xml")
            .readText()
        val allStorageDomains = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )

        assertFalse("<include" in legacyPolicy)
        assertEquals(allStorageDomains, excludedDomains(legacyPolicy))

        assertFalse("<include" in modernPolicy)
        val cloudBackup = requireRuleBlock(modernPolicy, "cloud-backup")
        val deviceTransfer = requireRuleBlock(modernPolicy, "device-transfer")
        assertEquals(allStorageDomains, excludedDomains(cloudBackup))
        assertEquals(allStorageDomains, excludedDomains(deviceTransfer))
    }

    private fun requireRuleBlock(xml: String, elementName: String): String =
        assertNotNull(
            Regex("<$elementName(?:\\s[^>]*)?>([\\s\\S]*?)</$elementName>")
                .find(xml)
                ?.groupValues
                ?.get(1),
            "Missing <$elementName> backup policy",
        )

    private fun excludedDomains(xml: String): Set<String> = Regex(
        pattern = """<exclude\s+domain="([^"]+)"\s+path="\."\s*/>""",
    ).findAll(xml).map { match -> match.groupValues[1] }.toSet()

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, "composeApp/src/androidMain/AndroidManifest.xml").isFile
            }
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }
}
