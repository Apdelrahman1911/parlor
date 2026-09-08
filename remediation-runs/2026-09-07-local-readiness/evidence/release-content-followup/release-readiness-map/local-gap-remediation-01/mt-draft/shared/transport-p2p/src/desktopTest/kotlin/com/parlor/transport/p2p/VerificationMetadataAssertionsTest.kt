package com.parlor.transport.p2p

import org.xml.sax.SAXParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class VerificationMetadataAssertionsTest {
    @Test
    fun exact_component_artifact_and_checksum_binding_succeeds_with_siblings() {
        assertPin(metadata(component(artifact() + artifact(name = "other.jar", hash = OTHER_HASH))))
    }

    @Test
    fun swapped_sibling_checksums_are_rejected() {
        val swapped = metadata(component(artifact(hash = OTHER_HASH) + artifact(name = "other.jar")))
        assertFailsWith<AssertionError> { assertPin(swapped) }
    }

    @Test
    fun a_checksum_in_a_comment_does_not_satisfy_the_artifact_pin() {
        val xml = metadata(component(artifact(hash = OTHER_HASH) + "<!-- <sha256 value=\"$EXPECTED_HASH\"/> -->"))
        assertFailsWith<AssertionError> { assertPin(xml) }
    }

    @Test
    fun each_component_coordinate_is_bound() {
        val otherComponents = listOf(
            component(artifact(), group = "other-group"),
            component(artifact(), name = "other-component"),
            component(artifact(), version = "other-version"),
        )
        otherComponents.forEach { other ->
            val xml = metadata(component(artifact(hash = OTHER_HASH)) + other)
            assertFailsWith<AssertionError> { assertPin(xml) }
        }
    }

    @Test
    fun missing_component_is_rejected() {
        assertFailsWith<AssertionError> { assertPin(metadata("")) }
    }

    @Test
    fun missing_artifact_is_rejected_even_when_another_artifact_has_the_checksum() {
        assertFailsWith<AssertionError> { assertPin(metadata(component(artifact(name = "other.jar")))) }
    }

    @Test
    fun missing_checksum_is_rejected() {
        assertFailsWith<AssertionError> { assertPin(metadata(component("<artifact name=\"wanted.jar\"/>"))) }
    }

    @Test
    fun duplicate_components_are_rejected_even_when_the_first_matches() {
        val xml = metadata(component(artifact()) + component(artifact(hash = OTHER_HASH)))
        assertFailsWith<AssertionError> { assertPin(xml) }
    }

    @Test
    fun duplicate_artifacts_are_rejected_even_when_both_match() {
        assertFailsWith<AssertionError> { assertPin(metadata(component(artifact() + artifact()))) }
    }

    @Test
    fun duplicate_checksum_elements_are_rejected_even_when_both_match() {
        val duplicate = "<artifact name=\"wanted.jar\"><sha256 value=\"$EXPECTED_HASH\"/>" +
            "<sha256 value=\"$EXPECTED_HASH\"/></artifact>"
        assertFailsWith<AssertionError> { assertPin(metadata(component(duplicate))) }
    }

    @Test
    fun duplicate_component_containers_are_rejected() {
        val xml = metadata(component(artifact())).replace(
            "</verification-metadata>",
            "<components>${component(artifact())}</components></verification-metadata>",
        )
        assertFailsWith<AssertionError> { assertPin(xml) }
    }

    @Test
    fun nested_artifact_is_not_a_direct_component_artifact() {
        val nested = "<wrapper>${artifact()}</wrapper>"
        assertFailsWith<AssertionError> { assertPin(metadata(component(nested))) }
    }

    @Test
    fun nested_checksum_is_not_a_direct_artifact_checksum() {
        val nested = "<artifact name=\"wanted.jar\"><wrapper><sha256 value=\"$EXPECTED_HASH\"/></wrapper></artifact>"
        assertFailsWith<AssertionError> { assertPin(metadata(component(nested))) }
    }

    @Test
    fun another_trusted_checksum_does_not_replace_the_explicit_primary_pin() {
        val alternative = "<artifact name=\"wanted.jar\"><sha256 value=\"$OTHER_HASH\">" +
            "<also-trust value=\"$EXPECTED_HASH\"/></sha256></artifact>"
        assertFailsWith<AssertionError> { assertPin(metadata(component(alternative))) }
    }

    @Test
    fun namespace_prefixes_attribute_order_and_escaped_artifact_names_are_supported() {
        val xml = """
            <v:verification-metadata xmlns:v="$NAMESPACE">
              <v:components>
                <v:component version="v" name="n" group="g">
                  <v:artifact name="wanted&amp;.jar"><v:sha256 value="$EXPECTED_HASH"/></v:artifact>
                </v:component>
              </v:components>
            </v:verification-metadata>
        """.trimIndent()
        VerificationMetadataAssertions(xml).assertArtifact("g", "n", "v", "wanted&.jar", EXPECTED_HASH)
    }

    @Test
    fun a_different_xml_namespace_is_rejected() {
        assertFailsWith<AssertionError> { assertPin(metadata(component(artifact())).replace(NAMESPACE, "urn:other")) }
    }

    @Test
    fun dtds_are_rejected_instead_of_expanding_entities() {
        val xml = "<!DOCTYPE verification-metadata [<!ENTITY marker \"unused\">]>" + metadata(component(artifact()))
        assertFailsWith<SAXParseException> { assertPin(xml) }
    }

    @Test
    fun malformed_xml_is_rejected() {
        assertFailsWith<SAXParseException> { VerificationMetadataAssertions("<verification-metadata>") }
    }

    private fun assertPin(xml: String) {
        VerificationMetadataAssertions(xml).assertArtifact("g", "n", "v", "wanted.jar", EXPECTED_HASH)
    }

    private fun metadata(components: String): String =
        "<verification-metadata xmlns=\"$NAMESPACE\"><components>$components</components></verification-metadata>"

    private fun component(
        artifacts: String,
        group: String = "g",
        name: String = "n",
        version: String = "v",
    ): String = "<component group=\"$group\" name=\"$name\" version=\"$version\">$artifacts</component>"

    private fun artifact(name: String = "wanted.jar", hash: String = EXPECTED_HASH): String =
        "<artifact name=\"$name\"><sha256 value=\"$hash\"/></artifact>"

    private companion object {
        const val NAMESPACE = "https://schema.gradle.org/dependency-verification"
        val EXPECTED_HASH = "a".repeat(64)
        val OTHER_HASH = "b".repeat(64)
    }
}
