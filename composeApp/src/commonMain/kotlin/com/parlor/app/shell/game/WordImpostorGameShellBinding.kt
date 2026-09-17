package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.games.wordimpostor.WordImpostorIds
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorAuthority
import com.parlor.games.wordimpostor.domain.WordImpostorChanged
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorRoster
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_open
import com.parlor.games.wordimpostor.resources.wi_open_description
import com.parlor.games.wordimpostor.resources.wi_subtitle
import com.parlor.games.wordimpostor.resources.wi_tagline
import com.parlor.games.wordimpostor.resources.wi_title
import com.parlor.games.wordimpostor.ui.WordImpostorSettingsContent
import com.parlor.games.wordimpostor.ui.WordImpostorTable
import com.parlor.session.multidevice.PlayerSnapshotPayload
import org.jetbrains.compose.resources.stringResource

/** Composition-root binding; the room, rejoin and authority machinery remains game-neutral. */
internal class WordImpostorGameShellBinding(override val definition: WordImpostorDefinition) :
    MultiplayerOnlyGameShellBinding<WordImpostorState, WordImpostorAction, WordImpostorChanged>() {
    override val version = WordImpostorIds.VERSION
    override val modeId = WordImpostorIds.Standard
    override val defaultCaseId = WordImpostorSettings().caseId
    override val abortAction = WordImpostorAction.Abort

    override fun acceptsSettings(caseId: String, players: Int): Boolean =
        players in definition.supportedPlayerCounts && WordImpostorSettings.fromCaseId(caseId)?.supports(players) == true
    override fun validRoster(players: List<Player>) = WordImpostorRoster.isValidRoster(players)
    override fun isPlayerAction(action: WordImpostorAction) = WordImpostorAuthority.isPlayerAction(action)
    override fun isAllowed(action: WordImpostorAction, actor: PlayerId, host: PlayerId, state: WordImpostorState) =
        WordImpostorAuthority.allowed(action, actor, host, state)
    override fun disconnected(state: WordImpostorState) = state.public.disconnected
    override fun isAborted(state: WordImpostorState) = state.phase == WordImpostorPhase.Aborted
    override fun disconnectedAction(id: PlayerId) = WordImpostorAction.Disconnected(id)
    override fun reconnectedAction(id: PlayerId) = WordImpostorAction.Reconnected(id)
    override fun encodeAction(action: WordImpostorAction) = WordImpostorCodec.encodeAction(action)
    override fun decodeAction(bytes: ByteArray) = WordImpostorCodec.decodeAction(bytes)
    override fun snapshotFor(state: WordImpostorState, id: PlayerId) =
        PlayerSnapshotPayload(WordImpostorCodec.encodePublic(state), WordImpostorCodec.encodePrivate(state, id))
    override fun decodePublic(bytes: ByteArray) = WordImpostorCodec.decodePublic(bytes)
    override fun decodePlayer(public: WordImpostorState, bytes: ByteArray, id: PlayerId) =
        WordImpostorCodec.decodePlayer(public, bytes, id)

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.wi_title), subtitle = stringResource(Res.string.wi_subtitle),
        tagline = stringResource(Res.string.wi_tagline), openLabel = stringResource(Res.string.wi_open),
        openContentDescription = stringResource(Res.string.wi_open_description),
    )

    @Composable
    override fun Settings(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit) {
        WordImpostorSettingsContent(caseId, playerCount, enabled, onChange)
    }

    @Composable
    override fun Table(
        state: WordImpostorState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
        onAction: (WordImpostorAction) -> Unit, onLeave: () -> Unit, modifier: Modifier,
    ) {
        WordImpostorTable(state, self, isHost, enabled, privacyEpoch, onAction, onLeave, modifier)
    }
}
