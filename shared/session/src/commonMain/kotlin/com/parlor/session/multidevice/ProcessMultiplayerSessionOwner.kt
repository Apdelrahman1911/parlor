package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The device's authority role in one process-owned multiplayer session. */
enum class MultiplayerSessionRole {
    Host,
    Peer,
}

/**
 * Non-persistent information needed to reconstruct the shell route after a root
 * Compose/controller recreation. Room codes and player names are intentionally
 * omitted from [toString] so diagnostics cannot leak them accidentally.
 */
class MultiplayerSessionRoute private constructor(
    val gameId: GameId,
    val role: MultiplayerSessionRole,
    val displayName: String,
    val roomCode: String?,
    val resumeExistingSession: Boolean,
    val contentId: String?,
    val modeId: String?,
) {
    init {
        require(gameId.raw.isNotBlank()) { "gameId must not be blank" }
        when (role) {
            MultiplayerSessionRole.Host -> require(displayName.isNotBlank()) {
                "A host route requires a display name"
            }

            MultiplayerSessionRole.Peer -> if (!resumeExistingSession) {
                require(displayName.isNotBlank()) { "A join route requires a display name" }
                require(!roomCode.isNullOrBlank()) { "A join route requires a room code" }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MultiplayerSessionRoute &&
            gameId == other.gameId &&
            role == other.role &&
            displayName == other.displayName &&
            roomCode == other.roomCode &&
            resumeExistingSession == other.resumeExistingSession &&
            contentId == other.contentId &&
            modeId == other.modeId

    override fun hashCode(): Int {
        var result = gameId.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (roomCode?.hashCode() ?: 0)
        result = 31 * result + resumeExistingSession.hashCode()
        result = 31 * result + (contentId?.hashCode() ?: 0)
        result = 31 * result + (modeId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "MultiplayerSessionRoute(" +
            "gameId=${gameId.raw}, " +
            "role=$role, " +
            "resumeExistingSession=$resumeExistingSession, " +
            "hasContentId=${contentId != null}, " +
            "hasModeId=${modeId != null}" +
            ")"

    companion object {
        fun host(
            gameId: GameId,
            displayName: String,
            contentId: String? = null,
            modeId: String? = null,
        ): MultiplayerSessionRoute = MultiplayerSessionRoute(
            gameId = gameId,
            role = MultiplayerSessionRole.Host,
            displayName = displayName,
            roomCode = null,
            resumeExistingSession = false,
            contentId = contentId,
            modeId = modeId,
        )

        fun peer(
            gameId: GameId,
            displayName: String,
            roomCode: String,
            resumeExistingSession: Boolean = false,
        ): MultiplayerSessionRoute = MultiplayerSessionRoute(
            gameId = gameId,
            role = MultiplayerSessionRole.Peer,
            displayName = displayName,
            roomCode = roomCode.takeUnless(String::isBlank),
            resumeExistingSession = resumeExistingSession,
            contentId = null,
            modeId = null,
        )
    }
}

/** How the owner must obtain the next physical room for a route. */
enum class MultiplayerOpenMode {
    Host,
    Join,
    Resume,
}

/** Immutable, game-owned start data retained after the start frame is consumed. */
interface RetainedMultiplayerCheckpoint {
    val checkpointKind: String
}

/**
 * A game runtime whose jobs and transport bridge outlive transient UI roots.
 * Implementations must make both methods idempotent.
 */
interface RetainedMultiplayerRuntime {
    val runtimeKind: String

    suspend fun terminate(reason: SessionEndReason)

    fun close()
}

sealed interface RetainedValueResult<out T> {
    data class Ready<T>(val value: T) : RetainedValueResult<T>
    data class KindConflict(
        val expectedKind: String,
        val installedKind: String,
    ) : RetainedValueResult<Nothing>
}

/** Public lifecycle of the one multiplayer route owned by this process. */
sealed interface ProcessMultiplayerState {
    data object Idle : ProcessMultiplayerState

    data class Opening(
        val route: MultiplayerSessionRoute,
        val mode: MultiplayerOpenMode,
    ) : ProcessMultiplayerState

    data class Active(val session: ProcessMultiplayerSession) : ProcessMultiplayerState

    data class Retryable(
        val route: MultiplayerSessionRoute,
        val lastError: NetError?,
    ) : ProcessMultiplayerState

    data class Failed(
        val route: MultiplayerSessionRoute,
        val error: NetError,
        val retryMode: MultiplayerOpenMode,
    ) : ProcessMultiplayerState

    data class Closing(val route: MultiplayerSessionRoute) : ProcessMultiplayerState
}

val ProcessMultiplayerState.routeOrNull: MultiplayerSessionRoute?
    get() = when (this) {
        ProcessMultiplayerState.Idle -> null
        is ProcessMultiplayerState.Opening -> route
        is ProcessMultiplayerState.Active -> session.route
        is ProcessMultiplayerState.Retryable -> route
        is ProcessMultiplayerState.Failed -> route
        is ProcessMultiplayerState.Closing -> route
    }

/**
 * One physical room plus every runtime object that must survive UI recreation.
 * Instances are created only by [ProcessMultiplayerSessionOwner].
 */
class ProcessMultiplayerSession internal constructor(
    val route: MultiplayerSessionRoute,
    val room: LocalRoom,
    val hostSeed: Long?,
    parentScope: CoroutineScope,
) {
    private val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
    val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + sessionJob)

    private val startMutex = Mutex()
    private var freezeAttempt: Deferred<Result<List<RoomMember>, NetError>>? = null
    private val _frozenRoster = MutableStateFlow<List<RoomMember>?>(null)
    val frozenRoster: StateFlow<List<RoomMember>?> = _frozenRoster.asStateFlow()

    private val retainedMutex = Mutex()
    private val _checkpoint = MutableStateFlow<RetainedMultiplayerCheckpoint?>(null)
    val checkpoint: StateFlow<RetainedMultiplayerCheckpoint?> = _checkpoint.asStateFlow()
    private val _runtime = MutableStateFlow<RetainedMultiplayerRuntime?>(null)
    val runtime: StateFlow<RetainedMultiplayerRuntime?> = _runtime.asStateFlow()

    /** Admission closure is process-owned and exactly-once, even across UI cancellation. */
    suspend fun freezeAdmissions(): Result<List<RoomMember>, NetError> {
        val attempt = startMutex.withLock {
            _frozenRoster.value?.let { return Result.Success(it) }
            freezeAttempt ?: scope.async {
                val result = room.closeAdmissions()
                startMutex.withLock {
                    if (result is Result.Success) _frozenRoster.value = result.data
                    freezeAttempt = null
                }
                result
            }.also { freezeAttempt = it }
        }
        return attempt.await()
    }

    suspend fun getOrCreateCheckpoint(
        kind: String,
        create: () -> RetainedMultiplayerCheckpoint,
    ): RetainedValueResult<RetainedMultiplayerCheckpoint> = retainedMutex.withLock {
        val installed = _checkpoint.value
        if (installed == null) {
            val candidate = create()
            require(candidate.checkpointKind == kind) {
                "Checkpoint factory returned kind ${candidate.checkpointKind}; expected $kind"
            }
            _checkpoint.value = candidate
            RetainedValueResult.Ready(candidate)
        } else if (installed.checkpointKind == kind) {
            RetainedValueResult.Ready(installed)
        } else {
            RetainedValueResult.KindConflict(kind, installed.checkpointKind)
        }
    }

    suspend fun getOrCreateRuntime(
        kind: String,
        create: (CoroutineScope) -> RetainedMultiplayerRuntime,
    ): RetainedValueResult<RetainedMultiplayerRuntime> = retainedMutex.withLock {
        val installed = _runtime.value
        if (installed == null) {
            val candidate = create(scope)
            require(candidate.runtimeKind == kind) {
                "Runtime factory returned kind ${candidate.runtimeKind}; expected $kind"
            }
            _runtime.value = candidate
            RetainedValueResult.Ready(candidate)
        } else if (installed.runtimeKind == kind) {
            RetainedValueResult.Ready(installed)
        } else {
            RetainedValueResult.KindConflict(kind, installed.runtimeKind)
        }
    }

    internal fun closeRuntimeAndScope() {
        try {
            _runtime.value?.close()
        } finally {
            sessionJob.cancel()
        }
    }
}

/**
 * Process-scoped owner for exactly one multiplayer room.
 *
 * UI code may await operations, but all room creation/cleanup is launched in
 * [processScope]. Cancelling a composition therefore cancels only its waiter,
 * never the room transaction that another root must reattach to.
 */
class ProcessMultiplayerSessionOwner(
    private val processScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ProcessMultiplayerState>(ProcessMultiplayerState.Idle)
    val state: StateFlow<ProcessMultiplayerState> = _state.asStateFlow()

    private var opening: OpeningAttempt? = null
    private var retainedRetryRoom: RetainedRetryRoom? = null
    private var closing: ClosingAttempt? = null

    suspend fun acquire(
        route: MultiplayerSessionRoute,
        hostSeed: Long? = null,
        openRoom: suspend (MultiplayerOpenMode) -> Result<LocalRoom, NetError>,
    ): Result<ProcessMultiplayerSession, NetError> {
        require((route.role == MultiplayerSessionRole.Host) == (hostSeed != null)) {
            "A host route requires one private seed and a peer route must not receive it"
        }
        val completion = mutex.withLock {
            val active = (_state.value as? ProcessMultiplayerState.Active)?.session
            if (active != null) {
                return if (active.route == route) {
                    Result.Success(active)
                } else {
                    Result.Failure(NetError.AlreadyConnected)
                }
            }
            opening?.let { attempt ->
                return@withLock if (attempt.route == route) {
                    attempt.completion
                } else {
                    completed(Result.Failure(NetError.CommandInFlight))
                }
            }
            closing?.let {
                return@withLock completed(Result.Failure(NetError.CommandInFlight))
            }

            val currentRoute = _state.value.routeOrNull
            if (currentRoute != null && currentRoute != route) {
                return@withLock completed(Result.Failure(NetError.AlreadyConnected))
            }

            val mode = nextOpenMode(route)
            startOpeningLocked(route, hostSeed, mode, openRoom)
        }
        return completion.await()
    }

    /**
     * Closes a post-admission physical room while preserving its protected
     * logical membership for a resume attempt.
     */
    suspend fun preparePeerRetry(
        session: ProcessMultiplayerSession,
        error: NetError,
    ): Result<Unit, NetError> {
        require(session.route.role == MultiplayerSessionRole.Peer)
        val completion = mutex.withLock {
            closing?.let { attempt ->
                return@withLock if (attempt.session === session) {
                    attempt.completion
                } else {
                    completed(Result.Failure(NetError.CommandInFlight))
                }
            }
            val active = (_state.value as? ProcessMultiplayerState.Active)?.session
            if (active !== session) return Result.Failure(NetError.NotConnected)

            val result = CompletableDeferred<Result<Unit, NetError>>()
            val attempt = ClosingAttempt(session.route, session, result)
            closing = attempt
            _state.value = ProcessMultiplayerState.Closing(session.route)
            processScope.launch {
                val closed = try {
                    session.room.closeForRetry()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Result.Failure(NetError.TransportFailure("room close failed"))
                }
                mutex.withLock {
                    if (closing === attempt) {
                        closing = null
                        if (closed is Result.Success) {
                            session.closeRuntimeAndScope()
                            retainedRetryRoom = RetainedRetryRoom(session.route, session.room)
                            _state.value = ProcessMultiplayerState.Retryable(session.route, error)
                        } else {
                            _state.value = ProcessMultiplayerState.Active(session)
                        }
                    }
                }
                result.complete(closed)
            }
            result
        }
        return completion.await()
    }

    /** Host start retry: terminate/close this room but retain the shell route. */
    suspend fun prepareHostRetry(
        session: ProcessMultiplayerSession,
        reason: SessionEndReason = SessionEndReason.Cancelled,
    ): Result<Unit, NetError> {
        require(session.route.role == MultiplayerSessionRole.Host)
        return closeActive(
            session = session,
            reason = reason,
            retryAfterClose = true,
        )
    }

    /** Explicit final Leave. A failed peer credential revocation keeps recovery state alive. */
    suspend fun finalLeave(
        session: ProcessMultiplayerSession,
        reason: SessionEndReason,
    ): Result<Unit, NetError> = closeActive(
        session = session,
        reason = reason,
        retryAfterClose = false,
    )

    /**
     * Final Leave while no physical room exists (for example after a failed
     * post-admission start followed by a failed resume).
     */
    suspend fun discardRetainedRoute(route: MultiplayerSessionRoute): Result<Unit, NetError> {
        val completion = mutex.withLock {
            val retained = retainedRetryRoom
            if (retained == null || retained.route != route) {
                if (_state.value.routeOrNull == route) _state.value = ProcessMultiplayerState.Idle
                return Result.Success(Unit)
            }
            closing?.let { attempt ->
                return@withLock if (attempt.route == route) {
                    attempt.completion
                } else {
                    completed(Result.Failure(NetError.CommandInFlight))
                }
            }
            val result = CompletableDeferred<Result<Unit, NetError>>()
            val attempt = ClosingAttempt(route, null, result)
            closing = attempt
            _state.value = ProcessMultiplayerState.Closing(route)
            processScope.launch {
                val discarded = try {
                    retained.room.discardRejoinCapability()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Result.Failure(NetError.TransportFailure("membership discard failed"))
                }
                mutex.withLock {
                    if (closing === attempt) {
                        closing = null
                        if (discarded is Result.Success) {
                            retainedRetryRoom = null
                            _state.value = ProcessMultiplayerState.Idle
                        } else {
                            _state.value = ProcessMultiplayerState.Failed(
                                route = route,
                                error = (discarded as Result.Failure).error,
                                retryMode = MultiplayerOpenMode.Resume,
                            )
                        }
                    }
                }
                result.complete(discarded)
            }
            result
        }
        return completion.await()
    }

    /** Removes a failed route that never acquired or retained a room. */
    suspend fun abandonFailedRoute(route: MultiplayerSessionRoute): Result<Unit, NetError> {
        val retained = mutex.withLock {
            val active = (_state.value as? ProcessMultiplayerState.Active)?.session
            if (active != null) return Result.Failure(NetError.AlreadyConnected)
            val current = _state.value.routeOrNull
            if (current != null && current != route) return Result.Failure(NetError.AlreadyConnected)
            retainedRetryRoom
        }
        return if (retained != null) {
            discardRetainedRoute(route)
        } else {
            mutex.withLock {
                if (_state.value.routeOrNull == route) _state.value = ProcessMultiplayerState.Idle
            }
            Result.Success(Unit)
        }
    }

    private suspend fun closeActive(
        session: ProcessMultiplayerSession,
        reason: SessionEndReason,
        retryAfterClose: Boolean,
    ): Result<Unit, NetError> {
        val completion = mutex.withLock {
            closing?.let { attempt ->
                return@withLock if (attempt.session === session) {
                    attempt.completion
                } else {
                    completed(Result.Failure(NetError.CommandInFlight))
                }
            }
            val active = (_state.value as? ProcessMultiplayerState.Active)?.session
            if (active !== session) return Result.Failure(NetError.NotConnected)

            val result = CompletableDeferred<Result<Unit, NetError>>()
            val attempt = ClosingAttempt(session.route, session, result)
            closing = attempt
            _state.value = ProcessMultiplayerState.Closing(session.route)
            processScope.launch {
                val closed = try {
                    withContext(NonCancellable) {
                        closePhysicalSession(session, reason)
                    }
                } catch (cancelled: CancellationException) {
                    mutex.withLock {
                        if (closing === attempt) {
                            closing = null
                            if (session.route.role == MultiplayerSessionRole.Host) {
                                session.closeRuntimeAndScope()
                                _state.value = if (retryAfterClose) {
                                    ProcessMultiplayerState.Retryable(session.route, null)
                                } else {
                                    ProcessMultiplayerState.Idle
                                }
                            } else {
                                _state.value = ProcessMultiplayerState.Active(session)
                            }
                        }
                    }
                    result.cancel(cancelled)
                    throw cancelled
                }
                mutex.withLock {
                    if (closing === attempt) {
                        closing = null
                        if (
                            closed is Result.Success ||
                            session.route.role == MultiplayerSessionRole.Host
                        ) {
                            session.closeRuntimeAndScope()
                            _state.value = if (retryAfterClose) {
                                ProcessMultiplayerState.Retryable(session.route, null)
                            } else {
                                ProcessMultiplayerState.Idle
                            }
                        } else {
                            _state.value = ProcessMultiplayerState.Active(session)
                        }
                    }
                }
                result.complete(closed)
            }
            result
        }
        return completion.await()
    }

    private suspend fun closePhysicalSession(
        session: ProcessMultiplayerSession,
        reason: SessionEndReason,
    ): Result<Unit, NetError> {
        return if (session.route.role == MultiplayerSessionRole.Peer) {
            try {
                session.room.finalLeave()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.Failure(NetError.TransportFailure("final leave failed"))
            }
        } else {
            var failure: NetError? = null
            var cancellation: CancellationException? = null
            try {
                session.runtime.value?.terminate(reason)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
            } catch (_: Exception) {
                failure = NetError.TransportFailure("session termination failed")
            }
            try {
                session.room.leave()
            } catch (cancelled: CancellationException) {
                if (cancellation == null) cancellation = cancelled
            } catch (_: Exception) {
                if (failure == null) failure = NetError.TransportFailure("room close failed")
            }
            cancellation?.let { throw it }
            failure?.let { Result.Failure(it) } ?: Result.Success(Unit)
        }
    }

    private fun nextOpenMode(route: MultiplayerSessionRoute): MultiplayerOpenMode = when {
        route.role == MultiplayerSessionRole.Host -> MultiplayerOpenMode.Host
        retainedRetryRoom?.route == route -> MultiplayerOpenMode.Resume
        route.resumeExistingSession -> MultiplayerOpenMode.Resume
        else -> MultiplayerOpenMode.Join
    }

    private fun startOpeningLocked(
        route: MultiplayerSessionRoute,
        hostSeed: Long?,
        mode: MultiplayerOpenMode,
        openRoom: suspend (MultiplayerOpenMode) -> Result<LocalRoom, NetError>,
    ): CompletableDeferred<Result<ProcessMultiplayerSession, NetError>> {
        val completion = CompletableDeferred<Result<ProcessMultiplayerSession, NetError>>()
        val attempt = OpeningAttempt(route, mode, completion)
        opening = attempt
        _state.value = ProcessMultiplayerState.Opening(route, mode)
        processScope.launch {
            val opened = try {
                openRoom(mode)
            } catch (cancelled: CancellationException) {
                completion.cancel(cancelled)
                throw cancelled
            } catch (_: Exception) {
                Result.Failure(NetError.TransportFailure("room creation failed"))
            }

            var orphan: LocalRoom? = null
            mutex.withLock {
                if (opening !== attempt) {
                    orphan = (opened as? Result.Success)?.data
                } else {
                    opening = null
                    when (opened) {
                        is Result.Success -> {
                            retainedRetryRoom = null
                            val session = ProcessMultiplayerSession(
                                route = route,
                                room = opened.data,
                                hostSeed = hostSeed,
                                parentScope = processScope,
                            )
                            _state.value = ProcessMultiplayerState.Active(session)
                            completion.complete(Result.Success(session))
                        }

                        is Result.Failure -> {
                            _state.value = ProcessMultiplayerState.Failed(route, opened.error, mode)
                            completion.complete(Result.Failure(opened.error))
                        }
                    }
                }
            }
            orphan?.let { room ->
                withContext(NonCancellable) { room.leave() }
            }
        }
        return completion
    }

    private data class OpeningAttempt(
        val route: MultiplayerSessionRoute,
        val mode: MultiplayerOpenMode,
        val completion: CompletableDeferred<Result<ProcessMultiplayerSession, NetError>>,
    )

    private data class ClosingAttempt(
        val route: MultiplayerSessionRoute,
        val session: ProcessMultiplayerSession?,
        val completion: CompletableDeferred<Result<Unit, NetError>>,
    )

    private data class RetainedRetryRoom(
        val route: MultiplayerSessionRoute,
        val room: LocalRoom,
    )
}

private fun <T> completed(value: T): CompletableDeferred<T> =
    CompletableDeferred<T>().also { it.complete(value) }
