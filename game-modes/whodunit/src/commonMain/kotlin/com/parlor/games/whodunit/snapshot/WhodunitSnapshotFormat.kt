package com.parlor.games.whodunit.snapshot

import com.parlor.core.versioning.SemVer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Persisted Whodunit snapshots written by this build use an explicit,
 * game-owned envelope. The outer [com.parlor.engine.snapshot.GameSnapshot]
 * still routes the game and engine version; this schema version controls only
 * the bytes owned by the Whodunit codec.
 */
internal const val WHODUNIT_SNAPSHOT_KIND = "parlor.whodunit.snapshot"
internal const val WHODUNIT_SNAPSHOT_SCHEMA_VERSION = 1

/**
 * 1.2 is the first engine version whose Whodunit payload is self-identifying.
 * Versions 1.0 and 1.1 remain readable through the codec's bare-payload
 * migration path, but all new writes use the current envelope.
 */
internal val WHODUNIT_SNAPSHOT_ENGINE_VERSION = SemVer(1, 2, 0)

@Serializable
internal data class WhodunitSnapshotPayload(
    val kind: String,
    val schemaVersion: Int,
    val state: JsonObject,
)
