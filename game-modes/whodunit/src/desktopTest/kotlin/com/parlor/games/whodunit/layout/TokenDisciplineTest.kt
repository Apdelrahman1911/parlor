package com.parlor.games.whodunit.layout

import assertk.assertThat
import assertk.assertions.isEmpty
import java.io.File
import kotlin.test.Test

/**
 * Discipline guard: feature/component Composables go through design-system
 * tokens, never raw `Color.X`, raw hex `Color(0x...)`, or hardcoded strings.
 *
 * Scope: scans the common-source code for the composeApp shell + the
 * whodunit game module. The design-system module itself is exempt — that's
 * where the tokens live, so raw colour + dp literals are intentional there.
 *
 * Rules enforced
 *  1. No `Color.Black`, `Color.White`, `Color.Transparent` — feature code
 *     uses `ParlorTheme.colors.coverScreen` / `transparent` / `overlayScrim`.
 *  2. No raw `Color(0x...)` literals — values belong in `ParlorColors.kt`.
 *  3. No literal English strings as Compose `text = "..."` or
 *     `label = "..."` arguments — must go through `stringResource(...)`.
 *
 */
class TokenDisciplineTest {

    private val scanRoots: List<String> = listOf(
        "composeApp/src/commonMain/kotlin",
        "game-modes/whodunit/src/commonMain/kotlin",
    )

    @Test
    fun no_raw_color_or_hex_literals_in_feature_composables() {
        val root = findProjectRoot()
        val violations = mutableListOf<String>()
        val colorXPattern = Regex("""Color\.(Black|White|Transparent)\b""")
        val colorHexPattern = Regex("""Color\(0x[0-9A-Fa-f]+\)""")
        scanFiles(root) { file, source ->
            colorXPattern.findAll(source).forEach { match ->
                violations += "${file.relPath(root)}: raw ${match.value}"
            }
            colorHexPattern.findAll(source).forEach { match ->
                violations += "${file.relPath(root)}: raw ${match.value}"
            }
        }
        assertThat(violations).isEmpty()
    }

    @Test
    fun no_hardcoded_user_facing_strings_in_feature_composables() {
        val root = findProjectRoot()
        val violations = mutableListOf<String>()
        // Loose detector — flags any literal "..." (>=4 char, contains a
        // space or starts with a capital) handed to a Compose `text = ` or
        // `label = ` or `contentDescription = ` argument. Skips lines that
        // already use stringResource. This is intentionally a heuristic;
        // false positives go on the exemption list.
        val argPattern = Regex(
            """\b(text|label|contentDescription)\s*=\s*"([A-Z][A-Za-z'’\s,.…!?\-]{3,}|[A-Za-z'’\-]+\s+[A-Za-z'’,.…!?\-\s]{2,})"""",
        )
        scanFiles(root) { file, source ->
            source.lineSequence().forEachIndexed { index, line ->
                if ("stringResource" in line) return@forEachIndexed
                argPattern.findAll(line).forEach { match ->
                    violations += "${file.relPath(root)}:${index + 1}: hardcoded ${match.value}"
                }
            }
        }
        assertThat(violations).isEmpty()
    }

    private fun scanFiles(root: File, block: (File, String) -> Unit) {
        for (rel in scanRoots) {
            val dir = File(root, rel)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file -> block(file, file.readText()) }
        }
    }

    private fun File.relPath(root: File): String =
        absolutePath.removePrefix(root.absolutePath).trimStart('\\', '/')

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate project root from ${System.getProperty("user.dir")}")
    }
}
