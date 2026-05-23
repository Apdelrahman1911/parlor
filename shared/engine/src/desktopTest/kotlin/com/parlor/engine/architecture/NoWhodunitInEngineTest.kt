package com.parlor.engine.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertEmpty
import kotlin.test.Test

/**
 * Enforces ARCHITECTURE.md §1.2 — the engine is generic. No source file in
 * :shared:engine may *declare* an identifier (class, interface, object,
 * function, property, type alias) whose name contains a Whodunit-specific
 * term.
 *
 * Doc comments referencing Whodunit by example are allowed and useful — they
 * tell the reader what the abstract concept maps to in the first shipped
 * module. This test catches actual semantic drift (a `chooseKiller()`
 * function or a `dossier` field appearing in engine code), not documentation.
 */
class NoWhodunitInEngineTest {

    private val productionFiles = Konsist.scopeFromModule("shared/engine").files.filterNot { f ->
        val p = f.path.replace('\\', '/')
        p.contains("/src/commonTest/") ||
            p.contains("/src/desktopTest/") ||
            p.contains("/src/androidTest/") ||
            p.contains("/src/iosTest/")
    }

    private val bannedTokens = listOf(
        "killer", "Killer",
        "Dossier", "dossier",
        "vote", "Vote",
        "clue", "Clue",
        "whodunit", "Whodunit",
    )

    @Test
    fun engine_declares_no_whodunit_specific_identifiers() {
        val offendingNames: List<String> = productionFiles
            .flatMap { it.declarations(includeNested = true, includeLocal = true) }
            .mapNotNull { decl -> (decl as? com.lemonappdev.konsist.api.provider.KoNameProvider)?.name }
            .filter { name -> bannedTokens.any { it in name } }

        check(offendingNames.isEmpty()) {
            "Engine declares Whodunit-specific identifiers: $offendingNames"
        }
    }
}
