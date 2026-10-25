package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.dominoes.DominoIds
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoAuthority
import com.parlor.games.dominoes.domain.DominoChanged
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoRoster
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_open
import com.parlor.games.dominoes.resources.dom_open_description
import com.parlor.games.dominoes.resources.dom_subtitle
import com.parlor.games.dominoes.resources.dom_tagline
import com.parlor.games.dominoes.resources.dom_title
import com.parlor.games.dominoes.ui.DominoSettingsContent
import com.parlor.games.dominoes.ui.DominoTable
import com.parlor.session.multidevice.PlayerSnapshotPayload
import org.jetbrains.compose.resources.stringResource

/** Composition-root binding; the room, rejoin and authority machinery remains game-neutral. */
internal class DominoGameShellBinding(override val definition: DominoDefinition) :
    MultiplayerOnlyGameShellBinding<DominoState, DominoAction, DominoChanged>() {
    override val version = DominoIds.VERSION
    override val modeId = DominoIds.Standard
    override val defaultCaseId = DominoSettings().caseId
    override val abortAction = DominoAction.Abort

    override fun acceptsSettings(caseId: String, players: Int): Boolean =
        players in definition.supportedPlayerCounts && DominoSettings.fromCaseId(caseId)?.supports(players) == true
    override fun validRoster(players: List<Player>) = DominoRoster.isValidRoster(players)
    override fun isPlayerAction(action: DominoAction) = DominoAuthority.isPlayerAction(action)
    override fun isAllowed(action: DominoAction, actor: PlayerId, host: PlayerId, state: DominoState) =
        DominoAuthority.allowed(action, actor, host, state)
    override fun disconnected(state: DominoState) = state.public.disconnected
    override fun isAborted(state: DominoState) = state.phase == DominoPhase.Aborted
    override fun disconnectedAction(id: PlayerId) = DominoAction.Disconnected(id)
    override fun reconnectedAction(id: PlayerId) = DominoAction.Reconnected(id)
    override fun encodeAction(action: DominoAction) = DominoCodec.encodeAction(action)
    override fun decodeAction(bytes: ByteArray) = DominoCodec.decodeAction(bytes)
    override fun snapshotFor(state: DominoState, id: PlayerId) =
        PlayerSnapshotPayload(DominoCodec.encodePublic(state), DominoCodec.encodePrivate(state, id))
    override fun decodePublic(bytes: ByteArray) = DominoCodec.decodePublic(bytes)
    override fun decodePlayer(public: DominoState, bytes: ByteArray, id: PlayerId) =
        DominoCodec.decodePlayer(public, bytes, id)

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.dom_title), subtitle = stringResource(Res.string.dom_subtitle),
        tagline = stringResource(Res.string.dom_tagline), openLabel = stringResource(Res.string.dom_open),
        openContentDescription = stringResource(Res.string.dom_open_description),
    )

    @Composable
    override fun Settings(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit) {
        DominoSettingsContent(caseId, playerCount, enabled, onChange)
    }

    @Composable
    override fun Table(
        state: DominoState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
        onAction: (DominoAction) -> Unit, onLeave: () -> Unit, modifier: Modifier,
    ) {
        DominoTable(state, self, isHost, enabled, privacyEpoch, onAction, onLeave, modifier)
    }
}
