package com.parlor.games.lastlight.ui

import java.io.File
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LastLightResourceContractTest {
    private val resources: File by lazy {
        File(findProjectRoot(), "game-modes/last-light/src/commonMain/composeResources")
    }

    @Test
    fun every_bundle_has_matching_arabic_keys_types_and_indexed_format_arguments() {
        val english = readBundles("values")
        val arabic = readBundles("values-ar")
        assertTrue(english.isNotEmpty(), "No Last Light resource bundles found")
        assertEquals(english.keys, arabic.keys, "English and Arabic must contain the same bundle filenames")

        english.forEach { (filename, entries) ->
            val translated = arabic.getValue(filename)
            assertEquals(entries.keys, translated.keys, "$filename has different translated keys")
            entries.forEach { (name, entry) ->
                val translation = translated.getValue(name)
                assertEquals(entry.kind, translation.kind, "$filename:$name changed resource type")
                if (entry.kind == "plurals") {
                    assertTrue("other" in entry.variants, "$filename:$name needs an English fallback")
                    assertTrue(ARABIC_QUANTITIES.containsAll(entry.variants.keys), "$filename:$name has an invalid quantity")
                    assertEquals(ARABIC_QUANTITIES, translation.variants.keys, "$filename:$name lacks Arabic plural forms")
                }
                translation.variants.forEach { (quantity, value) ->
                    val original = entry.variants[quantity] ?: entry.variants.getValue("other")
                    assertEquals(
                        formatSignature(original),
                        formatSignature(value),
                        "$filename:$name:$quantity changed indexed format arguments",
                    )
                    val words = value.replace(FORMAT_TOKEN, "")
                    assertTrue(
                        words.any { it in '\u0600'..'\u06ff' } || words.none { it.isLetter() },
                        "$filename:$name:$quantity was not translated into Arabic",
                    )
                }
            }
        }
    }

    @Test
    fun bundles_cannot_shadow_each_others_strings_or_plurals() {
        for (directory in listOf("values", "values-ar")) {
            val owners = mutableMapOf<String, String>()
            readBundles(directory).forEach { (filename, entries) ->
                entries.keys.forEach { name ->
                    assertEquals(null, owners.put(name, filename), "$directory:$name is declared in multiple files")
                }
            }
        }
    }

    @Test
    fun copied_fonts_vectors_and_ofl_notices_match_the_distributed_provenance_record() {
        val manifest = Json.parseToJsonElement(File(resources, "files/licenses/asset_provenance.json").readText()).jsonObject
        val assets = manifest.getValue("assets").jsonArray
        assertEquals(11, assets.size, "Standard table uses five vectors, four fonts, and two OFL notices")
        assets.forEach { element ->
            val asset = element.jsonObject
            val path = asset.getValue("file").jsonPrimitive.content
            val file = File(resources, path)
            assertTrue(file.isFile, "Missing distributed asset: $path")
            val bytes = file.readBytes()
            assertEquals(asset.getValue("bytes").jsonPrimitive.content.toLong(), bytes.size.toLong(), path)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(asset.getValue("sha256").jsonPrimitive.content, digest, path)
        }
        for (font in listOf("fraunces", "manrope")) {
            val notice = File(resources, "files/licenses/${font}_ofl.txt").readText()
            assertTrue(notice.contains("SIL OPEN FONT LICENSE Version 1.1"), "$font is missing its full OFL notice")
            assertTrue(notice.contains("Copyright"), "$font is missing its copyright attribution")
        }
    }

    private fun readBundles(directory: String): Map<String, Map<String, ResourceEntry>> =
        File(resources, directory).listFiles().orEmpty()
            .filter { it.isFile && it.extension == "xml" }
            .sortedBy { it.name }
            .associate { it.name to parseBundle(it) }

    private fun parseBundle(file: File): Map<String, ResourceEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isExpandEntityReferences = false
            isXIncludeAware = false
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val entries = linkedMapOf<String, ResourceEntry>()
        root.childElements().forEach { element ->
            val name = element.getAttribute("name")
            assertTrue(name.isNotBlank(), "Unnamed resource in ${file.name}")
            assertFalse(entries.containsKey(name), "Duplicate resource $name in ${file.name}")
            val variants = when (element.tagName) {
                "string" -> mapOf("string" to element.textContent)
                "plurals" -> linkedMapOf<String, String>().apply {
                    element.childElements().forEach { item ->
                        assertEquals("item", item.tagName)
                        val quantity = item.getAttribute("quantity")
                        assertFalse(containsKey(quantity), "Duplicate quantity $name:$quantity")
                        put(quantity, item.textContent)
                    }
                }
                else -> error("Unsupported localized resource type ${element.tagName} in ${file.name}")
            }
            entries[name] = ResourceEntry(element.tagName, variants)
        }
        return entries
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun formatSignature(value: String): List<String> {
        val tokens = FORMAT_TOKEN.findAll(value).map { it.value }.sorted().toList()
        assertFalse(value.replace("%%", "").replace(FORMAT_TOKEN, "").contains('%'), "Unindexed format token: $value")
        return tokens
    }

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }

    private data class ResourceEntry(val kind: String, val variants: Map<String, String>)

    private companion object {
        val FORMAT_TOKEN = Regex("%(\\d+)\\\$([a-zA-Z])")
        val ARABIC_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")
    }
}
