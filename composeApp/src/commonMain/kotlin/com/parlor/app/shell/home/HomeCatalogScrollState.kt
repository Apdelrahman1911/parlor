package com.parlor.app.shell.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import com.parlor.app.shell.game.GameShellBinding
import kotlinx.coroutines.flow.collect

private const val SAVED_ANCHOR_FIELDS = 2
private const val MAX_PUBLIC_ANCHOR_LENGTH = 128
private val publicGameAnchor = Regex("game:[a-z0-9]+(?:-[a-z0-9]+)*")
private val publicChromeAnchors = setOf("home-top-bar", "home-lead", "games-label", "catalog-empty")

/** Only public catalog keys and a pixel offset enter platform saved state. */
internal data class HomeCatalogAnchor(val key: String, val offset: Int) {
    fun target(itemKeys: List<String>): Pair<Int, Int> {
        val index = itemKeys.indexOf(key)
        return if (index >= 0) index to offset else itemKeys.indexOf("games-label").coerceAtLeast(0) to 0
    }
}

internal fun publicCatalogAnchor(key: String?, offset: Int): HomeCatalogAnchor =
    if (key == null) {
        HomeCatalogAnchor("home-top-bar", 0)
    } else if (
        key.length <= MAX_PUBLIC_ANCHOR_LENGTH &&
        (key in publicChromeAnchors || publicGameAnchor.matches(key))
    ) {
        HomeCatalogAnchor(key, offset.coerceAtLeast(0))
    } else {
        // Do not persist local session IDs, recovery identifiers or arbitrary UI keys.
        HomeCatalogAnchor("games-label", 0)
    }

/** A live list survives navigation disposal; its public key survives root recreation. */
internal class HomeCatalogScrollState(
    val listState: LazyListState = LazyListState(),
    private var pendingAnchor: HomeCatalogAnchor? = null,
) {
    fun anchorForSave(): HomeCatalogAnchor {
        // Another save during asynchronous recovery must retain the original intent.
        pendingAnchor?.let { return it }
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == listState.firstVisibleItemIndex
        }
        return publicCatalogAnchor(visible?.key as? String, listState.firstVisibleItemScrollOffset)
    }

    fun cancelPendingRestoration() {
        pendingAnchor = null
    }

    suspend fun restoreAfterRecovery(itemKeys: List<String>) {
        val anchor = pendingAnchor ?: return
        pendingAnchor = null
        if (listState.isScrollInProgress) return // An ongoing user scroll takes precedence.
        val (index, offset) = anchor.target(itemKeys)
        listState.scrollToItem(index, offset)
    }

    companion object {
        val saver = listSaver<HomeCatalogScrollState, Any>(
            save = { state -> state.anchorForSave().let { listOf(it.key, it.offset) } },
            restore = { saved ->
                val key = saved.getOrNull(0) as? String
                val offset = saved.getOrNull(1) as? Int
                val anchor = if (saved.size == SAVED_ANCHOR_FIELDS && key != null && offset != null) {
                    publicCatalogAnchor(key, offset)
                } else {
                    publicCatalogAnchor(null, 0)
                }
                HomeCatalogScrollState(pendingAnchor = anchor)
            },
        )
    }
}

@Composable
internal fun rememberHomeCatalogScrollState(): HomeCatalogScrollState {
    val state = rememberSaveable(saver = HomeCatalogScrollState.saver) { HomeCatalogScrollState() }
    LaunchedEffect(state) {
        // Observe scroll transitions, not a polling loop or per-frame animation.
        snapshotFlow { state.listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) state.cancelPendingRestoration()
        }
    }
    return state
}

/** Mirrors the LazyColumn's keys, including transient and recovered rows before games. */
internal fun homeCatalogItemKeys(
    filteredGames: List<GameShellBinding>,
    unfinishedSessions: List<LocalRecoveryEntry>,
    hasResumableMultiplayer: Boolean,
    recoveryLoading: Boolean,
    recoveryUnavailable: Boolean,
): List<String> = buildList {
    add("home-top-bar")
    add("home-lead")
    when {
        recoveryLoading -> add("recovery-loading")
        recoveryUnavailable -> add("recovery-unavailable")
    }
    if (unfinishedSessions.isNotEmpty() || hasResumableMultiplayer) {
        add("continue-label")
        unfinishedSessions.forEach { add("local:${it.sessionId.raw}") }
        if (hasResumableMultiplayer) add("multiplayer-resume")
    }
    add("games-label")
    if (filteredGames.isEmpty()) add("catalog-empty")
    filteredGames.forEach { add("game:${it.definition.id.raw}") }
}
