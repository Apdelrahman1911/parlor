package com.parlor.games.whodunit.flow

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.ui.flow.resolveLocalRevealActor
import kotlin.test.Test

/**
 * Regression coverage for the JVM peer crash at the character-reveal stage.
 *
 * Stacktrace summary: in multi-device, the peer's `ShadowSessionController`
 * intentionally throws when asked for another player's `privateStateFor` —
 * a peer only holds its own private bucket. The crash happened because
 * `CharacterRevealSegment` was driving the dossier-reveal UI off whichever
 * player `phase.playerIndex` pointed at, not off the local device's player.
 * On a peer device, when the host's index pointed at the host (or any
 * non-self player), the peer's UI asked the shadow for that player's
 * private slice → `IllegalArgumentException`.
 *
 * The fix introduced [resolveLocalRevealActor] — pure helper that the UI
 * uses to decide whether to render the dossier locally or show the
 * "waiting for X" screen. These tests pin that decision in every
 * meaningful combination so the bug can't come back silently.
 */
class CharacterRevealAuthorityTest {

    private val players = listOf(
        Player(PlayerId("alice"), "Alice", seat = 0),
        Player(PlayerId("bob"), "Bob", seat = 1),
        Player(PlayerId("cara"), "Cara", seat = 2),
        Player(PlayerId("diego"), "Diego", seat = 3),
    )

    @Test
    fun pass_and_play_renders_dossier_for_whoever_is_current() {
        val phase = WhodunitPhase.CharacterReveal(playerIndex = 2)
        // selfPlayerId == null is the pass-and-play marker — every device is
        // "the device," so the dossier is whoever the phase points at.
        val actor = resolveLocalRevealActor(phase, players, selfPlayerId = null)
        assertThat(actor).isNotNull()
        assertThat(actor!!.id).isEqualTo(PlayerId("cara"))
    }

    @Test
    fun multi_device_local_device_renders_when_self_is_current() {
        val phase = WhodunitPhase.CharacterReveal(playerIndex = 1)
        val actor = resolveLocalRevealActor(
            phase = phase,
            players = players,
            selfPlayerId = PlayerId("bob"),  // we are Bob, and Bob is current
        )
        assertThat(actor).isNotNull()
        assertThat(actor!!.id).isEqualTo(PlayerId("bob"))
    }

    @Test
    fun multi_device_local_device_returns_null_when_self_is_not_current() {
        // This is the exact condition that crashed: peer != current player.
        // resolveLocalRevealActor returns null so the UI renders the waiting
        // screen and never calls privateStateFor(otherPlayer.id).
        val phase = WhodunitPhase.CharacterReveal(playerIndex = 0)  // Alice's reveal
        val actor = resolveLocalRevealActor(
            phase = phase,
            players = players,
            selfPlayerId = PlayerId("diego"),  // we are Diego, not Alice
        )
        assertThat(actor).isNull()
    }

    @Test
    fun multi_device_peer_returns_null_when_self_id_is_not_in_player_list() {
        // Belt-and-suspenders: if the device's id somehow doesn't correspond
        // to any player (e.g. stale roster), the UI still must NOT render
        // a dossier — it has no business showing anyone's private slice.
        val phase = WhodunitPhase.CharacterReveal(playerIndex = 0)
        val actor = resolveLocalRevealActor(
            phase = phase,
            players = players,
            selfPlayerId = PlayerId("stranger"),
        )
        assertThat(actor).isNull()
    }

    @Test
    fun out_of_range_phase_index_returns_null_safely() {
        // If state is in flux and phase.playerIndex briefly exceeds
        // players.size, the UI shouldn't crash — it should render nothing
        // (or the waiting screen) and wait for the next snapshot.
        val phase = WhodunitPhase.CharacterReveal(playerIndex = 99)
        assertThat(resolveLocalRevealActor(phase, players, selfPlayerId = null)).isNull()
        assertThat(resolveLocalRevealActor(phase, players, PlayerId("alice"))).isNull()
    }

    @Test
    fun every_index_in_a_four_player_game_resolves_correctly_for_each_peer() {
        // For every player as "self", iterate every possible phase index:
        // exactly one index should return non-null (the one matching self),
        // the others should all return null.
        for (selfSeat in players.indices) {
            val selfId = players[selfSeat].id
            for (phaseIndex in players.indices) {
                val phase = WhodunitPhase.CharacterReveal(playerIndex = phaseIndex)
                val actor = resolveLocalRevealActor(phase, players, selfId)
                if (phaseIndex == selfSeat) {
                    assertThat(actor, "peer ${selfId.raw} at index $phaseIndex").isNotNull()
                    assertThat(actor!!.id).isEqualTo(selfId)
                } else {
                    assertThat(actor, "peer ${selfId.raw} at index $phaseIndex").isNull()
                }
            }
        }
    }
}
