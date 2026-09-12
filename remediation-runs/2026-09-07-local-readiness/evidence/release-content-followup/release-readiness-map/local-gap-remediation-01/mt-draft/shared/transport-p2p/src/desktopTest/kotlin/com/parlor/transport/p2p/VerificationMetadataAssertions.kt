package com.parlor.transport.p2p

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Structural assertions for repository-owned verification metadata; desktop tests only. */
internal class VerificationMetadataAssertions(metadata: String) {
    private val components: List<Element> = parseRoot(metadata)
        .children("components")
        .singleEntry("components container")
        .children("component")

    fun assertArtifact(
        group: String,
        name: String,
        version: String,
        artifact: String,
        sha256: String,
    ) {
        assertTrue(SHA256_PATTERN.matches(sha256), "Expected checksum must be a lowercase SHA-256")
        val coordinates = "$group:$name:$version"
        val component = components.filter {
            it.getAttribute("group") == group &&
                it.getAttribute("name") == name &&
                it.getAttribute("version") == version
        }.singleEntry("component $coordinates")
        val artifactElement = component.children("artifact")
            .filter { it.getAttribute("name") == artifact }
            .singleEntry("artifact $coordinates:$artifact")
        val checksum = artifactElement.children("sha256")
            .singleEntry("sha256 for $coordinates:$artifact")
        assertEquals(sha256, checksum.getAttribute("value"), "Unexpected checksum for $coordinates:$artifact")
    }

    private companion object {
        const val VERIFICATION_NAMESPACE = "https://schema.gradle.org/dependency-verification"
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        fun parseRoot(metadata: String): Element {
            val factory = DocumentBuilderFactory.newDefaultInstance().apply {
                isNamespaceAware = true
                isValidating = false
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val root = factory.newDocumentBuilder().parse(InputSource(StringReader(metadata))).documentElement
            assertEquals(VERIFICATION_NAMESPACE, root.namespaceURI, "Unexpected verification namespace")
            assertEquals("verification-metadata", root.localName, "Unexpected verification root")
            return root
        }

        fun Element.children(name: String): List<Element> = buildList {
            for (index in 0 until childNodes.length) {
                val child = childNodes.item(index)
                if (child is Element && child.namespaceURI == VERIFICATION_NAMESPACE && child.localName == name) {
                    add(child)
                }
            }
        }

        fun List<Element>.singleEntry(label: String): Element {
            assertEquals(1, size, "Expected exactly one $label; missing or duplicate metadata is ambiguous")
            return single()
        }
    }
}
