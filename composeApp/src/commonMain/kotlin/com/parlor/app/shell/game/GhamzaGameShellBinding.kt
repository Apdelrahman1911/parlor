package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.GhamzaIds
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaAuthority
import com.parlor.games.ghamza.domain.GhamzaChanged
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaRoster
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_open
import com.parlor.games.ghamza.resources.gh_open_description
import com.parlor.games.ghamza.resources.gh_subtitle
import com.parlor.games.ghamza.resources.gh_tagline
import com.parlor.games.ghamza.resources.gh_title
import com.parlor.games.ghamza.ui.GhamzaSettingsContent
import com.parlor.games.ghamza.ui.GhamzaTable
import com.parlor.session.multidevice.PlayerSnapshotPayload
import org.jetbrains.compose.resources.stringResource

/** Composition-root binding; the room, rejoin and authority machinery remains game-neutral. */
internal class GhamzaGameShellBinding(override val definition: GhamzaDefinition) :
    MultiplayerOnlyGameShellBinding<GhamzaState, GhamzaAction, GhamzaChanged>() {
    override val version = GhamzaIds.VERSION
    override val modeId = GhamzaIds.Standard
    override val defaultCaseId = GhamzaSettings().caseId
    override val abortAction = GhamzaAction.Abort

    override fun acceptsSettings(caseId: String, players: Int): Boolean =
        players in definition.supportedPlayerCounts && GhamzaSettings.fromCaseId(caseId) != null
    override fun validRoster(players: List<Player>) = GhamzaRoster.isValidRoster(players)
    override fun isPlayerAction(action: GhamzaAction) = GhamzaAuthority.isPlayerAction(action)
    override fun isAllowed(action: GhamzaAction, actor: PlayerId, host: PlayerId, state: GhamzaState) =
        GhamzaAuthority.allowed(action, actor, host, state)
    override fun disconnected(state: GhamzaState) = state.public.disconnected
    override fun isAborted(state: GhamzaState) = state.phase == GhamzaPhase.Aborted
    override fun disconnectedAction(id: PlayerId) = GhamzaAction.Disconnected(id)
    override fun reconnectedAction(id: PlayerId) = GhamzaAction.Reconnected(id)
    override fun encodeAction(action: GhamzaAction) = GhamzaCodec.encodeAction(action)
    override fun decodeAction(bytes: ByteArray) = GhamzaCodec.decodeAction(bytes)
    override fun snapshotFor(state: GhamzaState, id: PlayerId) =
        PlayerSnapshotPayload(GhamzaCodec.encodePublic(state), GhamzaCodec.encodePrivate(state, id))
    override fun decodePublic(bytes: ByteArray) = GhamzaCodec.decodePublic(bytes)
    override fun decodePlayer(public: GhamzaState, bytes: ByteArray, id: PlayerId) =
        GhamzaCodec.decodePlayer(public, bytes, id)

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.gh_title), subtitle = stringResource(Res.string.gh_subtitle),
        tagline = stringResource(Res.string.gh_tagline), openLabel = stringResource(Res.string.gh_open),
        openContentDescription = stringResource(Res.string.gh_open_description),
    )

    @Composable
    override fun Settings(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit) {
        GhamzaSettingsContent(caseId, enabled, onChange)
    }

    @Composable
    override fun Table(
        state: GhamzaState, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
        onAction: (GhamzaAction) -> Unit, onLeave: () -> Unit, modifier: Modifier,
    ) {
        GhamzaTable(state, self, isHost, enabled, privacyEpoch, onAction, onLeave, modifier)
    }
}
