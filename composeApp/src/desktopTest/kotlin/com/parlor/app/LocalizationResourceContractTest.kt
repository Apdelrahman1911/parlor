package com.parlor.app

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Contracts for shipping Compose strings and configuration-specific native app names.
 *
 * Resource discovery is intentional: adding a new production module must not
 * require maintaining a second hard-coded module list before localization
 * drift is detected.
 */
class LocalizationResourceContractTest {
    private val root: File by lazy(::findProjectRoot)

    @Test
    fun android_debug_name_is_distinct_without_changing_the_store_name() {
        val debug = File(root, "composeApp/src/androidDebug/res/values/strings.xml")
        assertTrue(debug.isFile, "Debug needs its own launcher-name resource")
        assertEquals(mapOf("app_name" to "Parlor Debug"), parseStrings(debug))
        assertContains(debug.readText(), "translatable=\"false\"")
        assertEquals(
            "Parlor",
            parseStrings(File(root, "composeApp/src/androidMain/res/values/strings.xml"))["app_name"],
        )
        assertContains(
            read("composeApp/build.gradle.kts"),
            "getByName(\"debug\").res.srcDir(\"src/androidDebug/res\")",
        )
        assertContains(read("composeApp/src/androidMain/AndroidManifest.xml"), "@string/app_name")
    }

    @Test
    fun ios_debug_display_name_does_not_change_product_or_store_identity() {
        val project = read("iosApp/iosApp.xcodeproj/project.pbxproj")
        val settings = Regex(
            "/\\* (Debug|Release) \\*/ = \\{\\s*isa = XCBuildConfiguration;.*?" +
                "buildSettings = \\{(.*?)\\n\\s*};\\s*name = \\1;\\s*};",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(project).filter { match ->
            match.groupValues[2].contains("PRODUCT_NAME = \"\$(APP_NAME)\";")
        }.associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(setOf("Debug", "Release"), settings.keys)
        assertContains(settings.getValue("Debug"), "APP_DISPLAY_NAME = \"Parlor Debug\";")
        assertFalse(settings.getValue("Release").contains("Parlor Debug"))
        assertContains(settings.getValue("Debug"), "PRODUCT_BUNDLE_IDENTIFIER = \"\$(BUNDLE_ID).debug\";")
        assertContains(settings.getValue("Release"), "PRODUCT_BUNDLE_IDENTIFIER = \"\$(BUNDLE_ID)\";")
        assertContains(read("iosApp/Configuration/Config.xcconfig"), "APP_DISPLAY_NAME = \$(APP_NAME)")
        assertContains(read("iosApp/iosApp/Info.plist"), "<string>\$(APP_DISPLAY_NAME)</string>")
    }

    @Test
    fun ios_localized_names_have_one_configuration_aware_resource_producer() {
        val project = read("iosApp/iosApp.xcodeproj/project.pbxproj")
        assertContains(project, "Copy Localized App Metadata")
        assertContains(project, "$(SRCROOT)/scripts/copy_localized_metadata.sh")
        assertFalse(project.contains("InfoPlist.strings in Resources"))
        mapOf("en" to "Parlor", "ar" to "بارلور").forEach { (locale, storeName) ->
            val relative = "iosApp/$locale.lproj/InfoPlist.strings"
            assertContains(read("iosApp/$relative"), "\"CFBundleDisplayName\" = \"$storeName\";")
            assertContains(project, "$(SRCROOT)/$relative")
            assertContains(
                project,
                "$(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/$locale.lproj/InfoPlist.strings",
            )
        }
        val script = read("iosApp/scripts/copy_localized_metadata.sh")
        assertContains(script, "case \"\$CONFIGURATION\" in")
        assertContains(script, "Debug)")
        assertContains(script, "Release)")
        assertContains(script, "Set :CFBundleDisplayName \$APP_DISPLAY_NAME")
    }

    @Test
    fun every_shipping_string_bundle_has_arabic_key_and_format_parity() {
        val englishBundles = root.walkTopDown()
            .onEnter { directory -> directory.name !in IGNORED_DIRECTORIES }
            .filter { file ->
                file.isFile &&
                    file.invariantSeparatorsPath.endsWith(
                        "/src/commonMain/composeResources/values/strings.xml",
                    )
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        assertTrue(englishBundles.isNotEmpty(), "No shipping Compose string bundles were discovered")

        englishBundles.forEach { englishFile ->
            val resourceRoot = englishFile.parentFile.parentFile
            val arabicFile = File(resourceRoot, "values-ar/strings.xml")
            val bundle = englishFile.relativeTo(root).invariantSeparatorsPath
            assertTrue(arabicFile.isFile, "$bundle has no values-ar/strings.xml counterpart")

            val english = parseStrings(englishFile)
            val arabic = parseStrings(arabicFile)
            assertEquals(
                english.keys,
                arabic.keys,
                "$bundle and its Arabic counterpart expose different resource keys",
            )

            english.forEach { (name, englishValue) ->
                val arabicValue = checkNotNull(arabic[name])
                assertEquals(
                    formatSignature(englishValue, bundle, name),
                    formatSignature(
                        arabicValue,
                        arabicFile.relativeTo(root).invariantSeparatorsPath,
                        name,
                    ),
                    "$name uses incompatible English and Arabic format arguments",
                )
            }
        }
    }

    private fun parseStrings(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isExpandEntityReferences = false
            isXIncludeAware = false
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        repeat(nodes.length) { index ->
            val element = nodes.item(index) as Element
            val name = element.getAttribute("name")
            assertTrue(name.isNotBlank(), "Unnamed string resource in ${file.relativeTo(root)}")
            assertFalse(
                result.containsKey(name),
                "Duplicate string resource '$name' in ${file.relativeTo(root)}",
            )
            result[name] = element.textContent
        }
        return result
    }

    private fun read(path: String): String = File(root, path).readText().replace("\r\n", "\n")

    private fun formatSignature(value: String, file: String, name: String): List<String> {
        val indexedTokens = FORMAT_TOKEN.findAll(value).map { match ->
            "${match.groupValues[1]}\$${match.groupValues[2]}"
        }.sorted().toList()
        val unknownPercent = value.replace(ESCAPED_PERCENT, "").replace(FORMAT_TOKEN, "")
        assertFalse(
            unknownPercent.contains('%'),
            "$file:$name contains an unindexed or unsupported format token",
        )
        return indexedTokens
    }

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }

    private companion object {
        val IGNORED_DIRECTORIES: Set<String> = setOf(".git", ".gradle", "build")
        val FORMAT_TOKEN: Regex = Regex("%(\\d+)\\\$([a-zA-Z])")
        val ESCAPED_PERCENT: Regex = Regex("%%")
    }
}
