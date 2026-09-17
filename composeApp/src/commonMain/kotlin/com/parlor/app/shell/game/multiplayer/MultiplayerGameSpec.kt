package com.parlor.app.shell.game.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.action.GameAction
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.session.multidevice.PlayerSnapshotPayload

/** Only game-owned rules/codecs/UI vary. Room identity, admission, ordering and recovery do not. */
internal interface MultiplayerGameSpec<S : GameState, A : GameAction, E : GameEvent> {
    val definition: GameDefinition<S, A, E>
    val version: Int
    val modeId: ModeId
    val defaultCaseId: CaseId
    val abortAction: A
    fun acceptsSettings(caseId: String, players: Int): Boolean
    fun validRoster(players: List<Player>): Boolean
    fun isPlayerAction(action: A): Boolean
    fun isAllowed(action: A, actor: PlayerId, host: PlayerId, state: S): Boolean
    fun disconnected(state: S): Set<PlayerId>
    fun isAborted(state: S): Boolean
    fun disconnectedAction(id: PlayerId): A
    fun reconnectedAction(id: PlayerId): A
    fun encodeAction(action: A): ByteArray
    fun decodeAction(bytes: ByteArray): A
    fun snapshotFor(state: S, id: PlayerId): PlayerSnapshotPayload
    fun decodePublic(bytes: ByteArray): S
    fun decodePlayer(public: S, bytes: ByteArray, id: PlayerId): S

    @Composable
    fun Settings(caseId: CaseId, playerCount: Int, enabled: Boolean, onChange: (CaseId) -> Unit)

    /** The caller supplies only the seated projection, never a canonical host state. */
    @Composable
    fun Table(
        state: S, self: PlayerId, isHost: Boolean, enabled: Boolean, privacyEpoch: Long,
        onAction: (A) -> Unit, onLeave: () -> Unit, modifier: Modifier,
    )
}

internal fun <S : GameState, A : GameAction, E : GameEvent> MultiplayerGameSpec<S, A, E>.acceptsStart(
    offer: HostMessage.SessionStarting,
    room: LocalRoom,
): Boolean = offer.header.gameId == definition.id && offer.header.gameVersion == version &&
    offer.modeId == modeId.raw && validRoster(offer.players) && acceptsSettings(offer.caseId, offer.players.size) &&
    offer.players.firstOrNull()?.id == room.info.value.hostPlayerId && offer.players.any { it.id == room.selfPlayerId }

internal fun <S : GameState, A : GameAction, E : GameEvent> MultiplayerGameSpec<S, A, E>.kind(role: String): String =
    "${definition.id.raw}/$role/v$version"
