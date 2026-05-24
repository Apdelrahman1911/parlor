package com.parlor.games.whodunit.ui.flow.party

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Pure local state for the role-recall overlay. Lives entirely in
 * Compose memory — **never** dispatches a domain action, **never**
 * broadcasts, **never** mutates `WhodunitPhase`.
 *
 * The host has no visibility into how often a peer recalls their role.
 * Other peers can't observe it either. The local player can recall as
 * many times as they want and the canonical reducer is unaware.
 *
 * Stage progression matches the existing handoff cover-screen pattern
 * for symmetry: Closed → Cover (acknowledge "this is me") →
 * Gate (confirm "no one else is looking") → Dossier (the actual role) →
 * Hide (cover-back so the next reader sees a neutral screen) → Closed.
 */
class LocalRolePeekState {

    /** Stages of the modal. */
    enum class Stage { Closed, Cover, Gate, Dossier, Hide }

    /** Current stage. Compose state — observe to render. */
    var stage: Stage by mutableStateOf(Stage.Closed)
        private set

    /** Open the overlay from any other screen. */
    fun open() {
        stage = Stage.Cover
    }

    /** Advance through stages one click at a time. */
    fun advance() {
        stage = when (stage) {
            Stage.Closed -> Stage.Cover
            Stage.Cover -> Stage.Gate
            Stage.Gate -> Stage.Dossier
            Stage.Dossier -> Stage.Hide
            Stage.Hide -> Stage.Closed
        }
    }

    /** Force-close from any stage (e.g. system back, or the dialog's × button). */
    fun close() {
        stage = Stage.Closed
    }

    /** True when something other than [Stage.Closed] is showing — render the overlay. */
    val isOpen: Boolean get() = stage != Stage.Closed
}

/** Compose-friendly factory: scoped to the enclosing call site. */
@Composable
fun rememberLocalRolePeekState(): LocalRolePeekState = remember { LocalRolePeekState() }
