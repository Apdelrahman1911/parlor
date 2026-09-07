package com.parlor.games.whodunit.ui.flow

import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.OfflineRemoteCaseDataSource
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.PayloadValidator
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.TestingStoryFixtures
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.snapshot.WHODUNIT_SNAPSHOT_ENGINE_VERSION
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Real bundled content/reducer/codec; only the storage medium is synthetic. */
internal class WhodunitDiscussionFixture(val mode: ModeId = WhodunitIds.ClassicVoteModeId) {
    val stories = TestingStoryFixtures()
    val case = runBlocking { stories.loadCase("last-dinner") }
    val players = stories.players
    val definition = stories.definition
    val sessionId = SessionId("leave-confirmation-${mode.raw}")
    val store = RecordingDiscussionStore()
    private val payloadValidator = WhodunitPayloadValidator(stories.json)
    private val repository = DefaultCaseRepository(
        OfflineRemoteCaseDataSource(), InMemoryCachedCaseDataSource(),
        BundledWhodunitCases(
            listOf(case.envelope.caseId),
            { id -> Res.readBytes("files/cases/$id.json").decodeToString(throwOnInvalidSequence = true) },
            stories.json,
        ),
        DefaultCaseValidator(stories.json, 1, SemVer(1, 0, 0), DefaultGameRegistry(listOf(definition))),
        stories.json,
    )

    fun bindings(): Module = module {
        single<CaseRepository> { repository }
        single<PayloadValidator<WhodunitCase>>(named("whodunit")) { payloadValidator }
        single<SnapshotStore> { store }
        single<Clock> { stories.clock }
        single<SessionSeedSource> { SessionSeedSource { error("A restored game must retain its seed") } }
        single { definition }
    }

    suspend fun advanceToDiscussion(generation: Long, submit: suspend (WhodunitAction) -> Unit) {
        players.forEach { submit(WhodunitAction.AcknowledgeIntro(it.id)) }
        submit(WhodunitAction.AdvanceFromIntro)
        players.forEach { submit(WhodunitAction.AcknowledgeBriefing(it.id)) }
        (1..4).forEach { submit(WhodunitAction.AdvanceBriefingCard(it)) }
        players.forEach {
            submit(WhodunitAction.StartCharacterReveal(it.id, generation))
            submit(WhodunitAction.CompleteCharacterReveal(it.id, generation))
        }
        submit(WhodunitAction.AdvanceFromCharacterReveal)
        submit(WhodunitAction.RevealNextClue)
        submit(WhodunitAction.StartDiscussionTimer(180))
    }

    fun prepareLocal(finalActions: List<WhodunitAction> = emptyList()): WhodunitState = runBlocking {
        var state = stories.assignedState(case, mode, SEED)
        val context = stories.context(case, SEED)
        val reduce: suspend (WhodunitAction) -> Unit = { action ->
            state = definition.reducer().reduce(state, action, context).newState
        }
        advanceToDiscussion(state.public.roleAssignmentGeneration, reduce)
        assertIs<WhodunitPhase.Round>(state.phase)
        assertEquals(180, state.public.timer?.remainingSeconds)
        finalActions.forEach { reduce(it) }
        WhodunitStateValidator.requireValidForCase(state, case)
        val identity = case.envelope.contentIdentity()
        val snapshot = GameSnapshot(
            sessionId, WhodunitIds.GameId, WHODUNIT_SNAPSHOT_ENGINE_VERSION, stories.clock.now(),
            state.phase.id, definition.snapshotCodec().encode(state),
            mapOf("playMode" to "PassAndPlay", "caseVersion" to identity.version, "caseDigest" to identity.digest),
        )
        assertIs<Result.Success<Unit>>(store.save(snapshot))
        state
    }

    fun savedState(): WhodunitState {
        return decode(checkNotNull(store.latest.get()))
    }

    fun attemptedState(): WhodunitState = decode(checkNotNull(store.latestAttempt.get()))

    private fun decode(record: GameSnapshot): WhodunitState {
        assertEquals(sessionId, record.sessionId)
        return definition.snapshotCodec().decode(record.payload).also {
            WhodunitStateValidator.requireValidForCase(it, case)
        }
    }

    companion object { const val SEED = 91L }
}

internal class RecordingDiscussionStore(
    private val backing: SnapshotStore = InMemorySnapshotStore(),
) : SnapshotStore by backing {
    val latest = AtomicReference<GameSnapshot?>()
    val latestAttempt = AtomicReference<GameSnapshot?>()
    val failSaves = AtomicBoolean()
    val attempts = AtomicInteger()
    override suspend fun load(sessionId: SessionId) = backing.load(sessionId)
    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> {
        latestAttempt.set(snapshot)
        attempts.incrementAndGet()
        if (failSaves.get()) return Result.Failure(DataError.IoError("Synthetic save failure"))
        return backing.save(snapshot).also { result ->
            if (result is Result.Success) latest.set(snapshot)
        }
    }
}
