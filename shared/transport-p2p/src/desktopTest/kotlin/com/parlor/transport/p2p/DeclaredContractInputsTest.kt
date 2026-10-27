package com.parlor.transport.p2p

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeclaredContractInputsTest {
    private fun withCheckout(block: (File) -> Unit) {
        val root = Files.createTempDirectory("parlor-source-contract-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun source(root: File, path: String): File = root.resolve(path).apply {
        parentFile.mkdirs()
        writeText("public synthetic source")
    }

    private fun index(root: File, records: List<Triple<Char, String, String>>, version: Int = 1): File {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(0x50535243)
            stream.writeInt(version)
            stream.writeInt(records.size)
            records.forEach { (kind, module, path) ->
                stream.writeChar(kind.code)
                stream.writeUTF(module)
                stream.writeUTF(path)
            }
        }
        return root.resolve("index.bin").apply { writeBytes(bytes.toByteArray()) }
    }

    @Test
    fun declared_future_and_untracked_source_is_retained_without_scanning_ignored_copies() = withCheckout { root ->
        val live = "game-modes/future-game/src/commonMain/kotlin/New.kt"
        source(root, live)
        for (prefix in listOf(".git/copy", "build/generated-copy", "docs/archives/history", "remediation-runs/preserved")) {
            source(root, "$prefix/shared/networking-testing/src/commonMain/kotlin/Fake.kt")
            source(root, "$prefix/build.gradle.kts")
        }
        val inputs = readDeclaredContractInputs(root, index(root, listOf(Triple('S', ":game-modes:future-game", live))))
        assertEquals(listOf(source(root, live).canonicalFile), inputs.map { it.file })
        assertEquals(":game-modes:future-game", inputs.single().module)
    }

    @Test
    fun declared_nondefault_source_root_and_actual_build_file_are_retained() = withCheckout { root ->
        source(root, "custom/code/Native.kt")
        source(root, "module/custom-build.gradle.kts")
        val inputs = readDeclaredContractInputs(root, index(root, listOf(
            Triple('S', ":custom", "custom/code/Native.kt"),
            Triple('B', ":custom", "module/custom-build.gradle.kts"),
        )))
        assertEquals(listOf('S', 'B'), inputs.map { it.kind })
    }

    @Test
    fun genuine_declared_shipping_fixture_import_is_not_filtered() = withCheckout { root ->
        val path = "shared/core/src/commonMain/kotlin/Leak.kt"
        source(root, path).writeText("import com.parlor.networking.testing.InMemoryRoomBus")
        val inputs = readDeclaredContractInputs(root, index(root, listOf(Triple('S', ":shared:core", path))))
        assertTrue(inputs.filter { it.module != ":shared:networking-testing" }
            .any { "com.parlor.networking.testing" in it.file.readText() })
    }

    @Test
    fun nonexistent_unknown_duplicated_or_non_kotlin_inputs_fail_closed() = withCheckout { root ->
        source(root, "Live.kt")
        val valid = Triple('S', ":shared:core", "Live.kt")
        for (records in listOf(
            listOf(Triple('S', ":shared:core", "Missing.kt")),
            listOf(Triple('X', ":shared:core", "Live.kt")),
            listOf(Triple('S', "bad-project", "Live.kt")),
            listOf(valid, valid),
        )) {
            assertFailsWith<IllegalArgumentException> { readDeclaredContractInputs(root, index(root, records)) }
        }
        source(root, "data.json")
        assertFailsWith<IllegalArgumentException> {
            readDeclaredContractInputs(root, index(root, listOf(Triple('S', ":shared:core", "data.json"))))
        }
    }

    @Test
    fun absolute_parent_normalized_or_windows_redirect_paths_are_rejected() = withCheckout { root ->
        source(root, "Live.kt")
        for (path in listOf(root.resolve("Live.kt").absolutePath, "../Live.kt", "./Live.kt", "sub/../Live.kt", "sub//Live.kt", "sub\\Live.kt")) {
            assertFailsWith<IllegalArgumentException> {
                readDeclaredContractInputs(root, index(root, listOf(Triple('S', ":core", path))))
            }
        }
    }

    @Test
    fun missing_empty_oversized_unknown_truncated_and_trailing_metadata_is_not_normalized() = withCheckout { root ->
        assertFailsWith<IllegalArgumentException> { readDeclaredContractInputs(root, root.resolve("missing.bin")) }
        val path = root.resolve("index.bin")
        for (bytes in listOf(byteArrayOf(), ByteArray(1_048_577), byteArrayOf(1, 2))) {
            path.writeBytes(bytes)
            assertFails { readDeclaredContractInputs(root, path) }
        }
        source(root, "Live.kt")
        val records = listOf(Triple('S', ":core", "Live.kt"))
        assertFailsWith<IllegalArgumentException> { readDeclaredContractInputs(root, index(root, records, version = 2)) }
        val valid = index(root, records).readBytes()
        path.writeBytes(valid.copyOf(valid.size - 1))
        assertFails { readDeclaredContractInputs(root, path) }
        path.writeBytes(valid + byteArrayOf(0))
        assertFailsWith<IllegalArgumentException> { readDeclaredContractInputs(root, path) }
    }

    @Test
    fun record_count_is_bounded_before_materializing_entries() = withCheckout { root ->
        for (count in listOf(-1, 0, 20_001, Int.MAX_VALUE)) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { it.writeInt(0x50535243); it.writeInt(1); it.writeInt(count) }
            val path = root.resolve("index.bin").apply { writeBytes(bytes.toByteArray()) }
            assertFailsWith<IllegalArgumentException> { readDeclaredContractInputs(root, path) }
        }
    }

    @Test
    fun index_cannot_point_outside_the_checkout() = withCheckout { root ->
        withCheckout { other ->
            source(other, "Live.kt")
            assertFailsWith<IllegalArgumentException> {
                readDeclaredContractInputs(root, index(other, listOf(Triple('S', ":core", "Live.kt"))))
            }
        }
    }
}
