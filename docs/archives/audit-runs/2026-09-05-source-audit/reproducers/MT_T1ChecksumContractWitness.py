"""Isolated source-assertion witness; does not modify metadata or run Gradle.

The safer expectation intentionally fails on the inspected component-wide
substring algorithm. This is not an execution of the Kotlin test suite.
"""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[3]
TEST = ROOT / (
    "shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/"
    "P2pKitMavenProvenanceContractTest.kt"
)
METADATA = ROOT / "gradle/verification-metadata.xml"
VERSION = "0.7.0-rc3"
MODULE = f"p2p-core-{VERSION}.module"
METADATA_JAR = f"p2p-core-metadata-{VERSION}.jar"
NS = {"d": "https://schema.gradle.org/dependency-verification"}


def expected_artifacts():
    source = TEST.read_text()
    matches = re.findall(
        r'ExpectedArtifact\(\s*component = "([^"]+)",\s*'
        r'artifact = "([^"]+)",\s*sha256 = "([0-9a-f]{64})",\s*\)',
        source,
    )
    assert len(matches) == 15, "Witness must be reviewed if source table changes"
    return [
        (component, artifact.replace("$P2PKIT_VERSION", VERSION), checksum)
        for component, artifact, checksum in matches
    ]


def component_wide_assertions_accept(metadata, expectations):
    # Direct transcription of source45-62, not a stronger XML implementation.
    for component, artifact, checksum in expectations:
        marker = (
            '<component group="io.github.apdelrahman1911" '
            f'name="{component}" version="{VERSION}">'
        )
        start = metadata.find(marker)
        if start < 0:
            return False
        end = metadata.find("</component>", start)
        if end <= start:
            return False
        block = metadata[start:end]
        if f'<artifact name="{artifact}">' not in block:
            return False
        if f'<sha256 value="{checksum}"' not in block:
            return False
    return True


class ChecksumContractWitness(unittest.TestCase):
    def test_artifact_specific_checksum_mutation_must_be_rejected(self):
        original = METADATA.read_text()
        expected = expected_artifacts()
        self.assertTrue(component_wide_assertions_accept(original, expected))
        checksum = next(
            digest
            for component, artifact, digest in expected
            if component == "p2p-core" and artifact == MODULE
        )
        tree = ET.fromstring(original)
        component = tree.find(
            f'd:components/d:component[@name="p2p-core"][@version="{VERSION}"]',
            NS,
        )
        sibling_hash = component.find(
            f'd:artifact[@name="{METADATA_JAR}"]/d:sha256', NS
        ).get("value")
        marker = (
            '<component group="io.github.apdelrahman1911" '
            f'name="p2p-core" version="{VERSION}">'
        )
        start = original.index(marker)
        end = original.index("</component>", start)
        block = original[start:end]
        self.assertEqual(block.count(checksum), 1)
        self.assertEqual(block.count(sibling_hash), 1)
        mutated_block = block.replace(checksum, "0" * 64).replace(
            sibling_hash, checksum
        )
        mutated = original[:start] + mutated_block + original[end:]
        actual_hash = ET.fromstring(mutated).find(
            f'd:components/d:component[@name="p2p-core"][@version="{VERSION}"]/'
            f'd:artifact[@name="{MODULE}"]/d:sha256',
            NS,
        ).get("value")
        self.assertEqual(actual_hash, "0" * 64)
        self.assertNotEqual(actual_hash, checksum)
        self.assertFalse(
            component_wide_assertions_accept(mutated, expected),
            "Existing assertions accept a module checksum moved to its sibling JAR",
        )


if __name__ == "__main__":
    unittest.main()
