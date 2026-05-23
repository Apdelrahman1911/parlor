package com.parlor.engine.state

import com.parlor.core.ids.PlayerId
import kotlinx.serialization.Serializable

/**
 * An engine-level player at the table. Game modules attach module-specific
 * private state (roles, dossiers) via the per-player private bucket.
 */
@Serializable
data class Player(
    val id: PlayerId,
    val displayName: String,
    val seat: Int,
)
