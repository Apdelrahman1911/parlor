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
        val englishBundles = shippingBundles()
        assertTrue(englishBundles.isNotEmpty(), "No shipping Compose string bundles were discovered")

        englishBundles.forEach { englishFile ->
            val resourceRoot = englishFile.parentFile.parentFile
            val arabicFile = File(resourceRoot, "values-ar/${englishFile.name}")
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

    @Test
    fun every_shipping_plural_has_all_arabic_quantities_and_compatible_indexed_arguments() {
        var checked = 0
        shippingBundles().forEach { file ->
            val translatedFile = File(file.parentFile.parentFile, "values-ar/${file.name}")
            assertTrue(translatedFile.isFile, "Missing Arabic bundle for $file")
            val english = parsePlurals(file)
            val arabic = parsePlurals(translatedFile)
            assertEquals(english.keys, arabic.keys, "Plural key/type drift in $file")
            english.forEach { (name, quantities) ->
                checked++
                assertTrue("other" in quantities, "$file:$name needs a fallback")
                assertTrue(ARABIC_QUANTITIES.containsAll(quantities.keys), "$file:$name has an invalid quantity")
                val translated = arabic.getValue(name)
                assertEquals(ARABIC_QUANTITIES, translated.keys, "$file:$name lacks Arabic plural forms")
                translated.forEach { (quantity, value) ->
                    assertEquals(
                        formatSignature(quantities[quantity] ?: quantities.getValue("other"), file.path, name),
                        formatSignature(value, translatedFile.path, name),
                        "$file:$name:$quantity has incompatible placeholders",
                    )
                    assertTrue(value.any { it in '\u0600'..'\u06ff' }, "$file:$name:$quantity is not translated")
                }
            }
            assertTrue(parseStrings(file).keys.intersect(english.keys).isEmpty(), "String/plural name collision in $file")
            assertTrue(parseStrings(translatedFile).keys.intersect(arabic.keys).isEmpty(),
                "String/plural name collision in $translatedFile")
        }
        assertTrue(checked > 0, "No shipping plural resources were checked")
    }

    private fun shippingBundles(): List<File> = root.walkTopDown()
        .onEnter { it.name !in IGNORED_DIRECTORIES }
        .filter { it.isFile && it.extension == "xml" &&
            it.parentFile.invariantSeparatorsPath.endsWith("/src/commonMain/composeResources/values") }
        .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        .toList()

    private fun parseStrings(file: File): Map<String, String> {
        val result = linkedMapOf<String, String>()
        resourceElements(file).filter { it.tagName == "string" }.forEach { element ->
            val name = element.getAttribute("name")
            assertTrue(name.isNotBlank(), "Unnamed string resource in ${file.relativeTo(root)}")
            assertFalse(result.containsKey(name), "Duplicate string '$name' in ${file.relativeTo(root)}")
            assertTrue(element.textContent.isNotBlank(), "Empty string '$name' in ${file.relativeTo(root)}")
            result[name] = element.textContent
        }
        return result
    }

    private fun parsePlurals(file: File): Map<String, Map<String, String>> = linkedMapOf<String, Map<String, String>>().apply {
        resourceElements(file).filter { it.tagName == "plurals" }.forEach { element ->
            val name = element.getAttribute("name")
            assertTrue(name.isNotBlank() && name !in this, "Unnamed/duplicate plural in $file")
            val variants = linkedMapOf<String, String>()
            val items = element.getElementsByTagName("item")
            repeat(items.length) { index ->
                val item = items.item(index) as Element
                val quantity = item.getAttribute("quantity")
                assertTrue(quantity !in variants, "Duplicate plural quantity $file:$name:$quantity")
                assertTrue(item.textContent.isNotBlank(), "Empty plural $file:$name:$quantity")
                variants[quantity] = item.textContent
            }
            put(name, variants)
        }
    }

    private fun resourceElements(file: File): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isExpandEntityReferences = false
            isXIncludeAware = false
        }
        val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
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
        val ARABIC_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")
    }
}
