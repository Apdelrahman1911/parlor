// Audit-only iosMain addition. Never registered by the checked-in build.
// A root-owned fresh simulator and copied Swift trigger are mandatory.
package com.parlor.app.audit

import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.home.loadHomeRecoveryAvailability
import com.parlor.app.storage.IosSecureKeyValueBacking
import com.parlor.app.storage.IosSnapshotFileSystem
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.secure.SecureKeyValueBacking
import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.SnapshotMetadata
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.transport.p2p.P2pKitRoomTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import org.koin.mp.KoinPlatform

/**
 * One probe per app process; start/cancel Swift names require generated-header verification.
 * The original MainViewController/App must already have started the real Koin graph.
 */
object IOSR1AppHostProbe {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false // Accessed only in the Main-dispatched coroutine.

    fun start(onJson: (String) -> Unit) {
        scope.launch {
            if (started) return@launch
            started = true
            try {
                var completedProductionObservation: JsonObject? = null
                val report = try {
                    withTimeout(20_000L) {
                        observeProductionRecovery { completedProductionObservation = it }
                    }
                } catch (_: TimeoutCancellationException) {
                    harnessFailure("timeout", completedProductionObservation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (aborted: AuditAbort) {
                    harnessFailure(aborted.code, completedProductionObservation)
                } catch (_: Exception) {
                    // Never serialize Throwable messages, causes, stacks or object descriptions.
                    harnessFailure("unexpected_exception", completedProductionObservation)
                }
                val json = report.toString() // Constructed exclusively from allowlisted primitives.
                if (json.length > 8_192) {
                    onJson(harnessFailure("result_too_large").toString())
                } else {
                    onJson(json)
                }
            } finally {
                // This is the audit job only. Never stop Koin or cancel an app/transport scope.
                scope.cancel()
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}

private suspend fun observeProductionRecovery(onProductionObserved: (JsonObject) -> Unit): JsonObject {
    val arguments = NSProcessInfo.processInfo.arguments
    if (
        "--parlor-audit-iosr1" !in arguments ||
        NSBundle.mainBundle.bundleIdentifier != "com.parlor.app.debug"
    ) throw AuditAbort("wrong_launch_context")

    // No new KoinApplication, modules, stores, native backing or RoomTransport is constructed.
    val koin = KoinPlatform.getKoin()
    val store = koin.get<SnapshotStore>()
    val transport = koin.get<RoomTransport>()
    val registry = koin.get<GameShellRegistry>()
    if (
        store !is FileBackedSnapshotStore || transport !is P2pKitRoomTransport ||
        koin.get<SnapshotFileSystem>() !is IosSnapshotFileSystem ||
        koin.get<SecureKeyValueBacking>() !is IosSecureKeyValueBacking
    ) throw AuditAbort("unexpected_di_binding")

    val observedStore = ObservedEmptySnapshotStore(store)
    var multiplayer: JsonObject? = null
    var multiplayerStorageFailed = false
    val router = GameShellRouter(registry)
    val ready = loadHomeRecoveryAvailability(
        store = observedStore,
        loadMultiplayer = {
            val result = transport.resumableSession()
            multiplayer = when (result) {
                is Result.Success -> {
                    if (result.data != null) throw AuditAbort("unexpected_multiplayer_record")
                    outcome("success_null")
                }
                is Result.Failure -> {
                    multiplayerStorageFailed = result.error == NetError.SecureStorageUnavailable
                    outcome("failure", when (result.error) {
                        NetError.SecureStorageUnavailable -> "secure_storage_unavailable"
                        NetError.IncompatibleProtocol -> "incompatible_protocol"
                        else -> "other_net_error"
                    })
                }
            }
            result // Original result returned unchanged; no synthetic success/fallback.
        },
        supportsLocalResume = { entry ->
            entry.gameId?.let { gameId -> router.resumeLocal(gameId, entry.sessionId) } != null
        },
        supportsMultiplayerResume = { info ->
            router.resumeMultiplayer(info.gameId, info.gameVersion, info.displayName) != null
        },
    )
    val localResult = observedStore.observation ?: throw AuditAbort("local_not_observed")
    val multiplayerResult = multiplayer ?: throw AuditAbort("multiplayer_not_observed")
    if (ready.unfinishedSessions.isNotEmpty() || ready.resumableMultiplayer != null) {
        throw AuditAbort("unexpected_combined_record")
    }

    val primary = buildJsonObject {
        put("schema_version", 1)
        put("probe_kind", "production_koin_rerun")
        put("harness_status", "observation_complete")
        put("original_home_invocation_intercepted", false)
        put("local", localResult)
        put("multiplayer", multiplayerResult)
        put("combined", buildJsonObject {
            put("has_unavailable_source", ready.hasUnavailableSource)
            put("local_count", 0)
            put("has_multiplayer", false)
        })
    }
    // Retain already-observed source outcomes even if optional corroboration fails/times out.
    onProductionObserved(primary)
    val native = when {
        "--parlor-audit-iosr1-native" !in arguments -> outcome("not_requested")
        !observedStore.emptyListObserved || !multiplayerStorageFailed -> outcome("not_applicable_to_result")
        else -> withContext(Dispatchers.Default) { subsequentSameAppKeychainRead() }
    }
    return buildJsonObject {
        primary.forEach { (key, value) -> put(key, value) }
        put("native_corroboration", native)
    }
}

/** Only the real list result is observed; nonempty data aborts before metadata is loaded. */
private class ObservedEmptySnapshotStore(private val delegate: SnapshotStore) : SnapshotStore by delegate {
    var observation: JsonObject? = null
        private set
    var emptyListObserved: Boolean = false
        private set

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> {
        if (observation != null) throw AuditAbort("unexpected_repeated_list")
        val result = delegate.listUnfinished()
        observation = when (result) {
            is Result.Success -> {
                if (result.data.isNotEmpty()) throw AuditAbort("unexpected_local_records")
                emptyListObserved = true
                buildJsonObject { put("kind", "success_empty"); put("entry_count", 0) }
            }
            is Result.Failure -> outcome("failure", when (result.error) {
                DataError.NotFound -> "not_found"
                DataError.CorruptedData -> "corrupted_data"
                is DataError.IoError -> "io_error"
                DataError.DiskFull -> "disk_full"
                DataError.PermissionDenied -> "permission_denied"
                is DataError.Unknown -> "unknown_data_error"
            })
        }
        return result
    }

    override suspend fun loadMetadata(sessionId: SessionId): Result<SnapshotMetadata, DataError> =
        throw AuditAbort("unexpected_metadata_read")
}

internal class AuditAbort(val code: String) : RuntimeException()

private fun outcome(kind: String, error: String? = null): JsonObject = buildJsonObject {
    put("kind", kind)
    if (error != null) put("error_category", error)
}

private fun harnessFailure(code: String, primary: JsonObject? = null): JsonObject = buildJsonObject {
    put("schema_version", 1)
    put("probe_kind", "production_koin_rerun")
    put("harness_status", "aborted")
    put("failure_category", code)
    if (primary != null) put("completed_production_observation", primary)
}
