package com.parlor.app

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Repository-wide contract for every shipping Compose string bundle.
 *
 * Resource discovery is intentional: adding a new production module must not
 * require maintaining a second hard-coded module list before localization
 * drift is detected.
 */
class LocalizationResourceContractTest {
    private val root: File by lazy(::findProjectRoot)

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
