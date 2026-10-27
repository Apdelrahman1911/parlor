package com.parlor.transport.p2p

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files

internal data class DeclaredContractInput(val kind: Char, val module: String, val file: File)

/** Fail-closed source/input framing; metadata contains paths, never player state. */
internal fun readDeclaredContractInputs(root: File, index: File): List<DeclaredContractInput> {
    val checkout = root.canonicalFile
    require(index.canonicalFile.toPath().startsWith(checkout.toPath()) && !Files.isSymbolicLink(index.toPath())) {
        "Architecture input index must belong to this checkout"
    }
    require(index.isFile && index.length() in 1..1_048_576) { "Architecture input index is missing or unbounded" }
    val bytes = index.inputStream().use { it.readNBytes(1_048_577) }
    require(bytes.size <= 1_048_576) { "Architecture input index grew beyond its bound" }
    return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
        require(stream.readInt() == 0x50535243 && stream.readInt() == 1) { "Unknown architecture input index" }
        val count = stream.readInt()
        require(count in 1..20_000) { "Architecture input count exceeds bound" }
        val keys = mutableSetOf<Triple<Char, String, String>>()
        val entries = List(count) {
            val kind = stream.readChar()
            val module = stream.readUTF()
            val relative = stream.readUTF()
            require(kind in setOf('B', 'S') && module.matches(Regex(":(?:[A-Za-z0-9_-]+:)*[A-Za-z0-9_-]*"))) {
                "Unknown architecture input kind or project"
            }
            require(relative.isNotEmpty() && !File(relative).isAbsolute && '\\' !in relative &&
                relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "Architecture input path must be an exact checkout-relative file"
            }
            val file = checkout.resolve(relative)
            require(file.isFile && file.canonicalFile.toPath().startsWith(checkout.toPath()) &&
                !Files.isSymbolicLink(file.toPath())) { "Architecture input is missing or redirected" }
            require(kind != 'S' || file.extension == "kt") { "Architecture Main source must be Kotlin" }
            require(keys.add(Triple(kind, module, relative))) { "Duplicated architecture input" }
            DeclaredContractInput(kind, module, file)
        }
        require(stream.available() == 0) { "Architecture input index has trailing bytes" }
        entries
    }
}
