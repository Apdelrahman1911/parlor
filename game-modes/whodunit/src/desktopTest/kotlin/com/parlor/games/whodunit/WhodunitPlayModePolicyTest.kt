package com.parlor.games.whodunit

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.PlayerId
import com.parlor.session.PlayMode
import kotlin.test.Test

class WhodunitPlayModePolicyTest {
    @Test
    fun only_pass_and_play_can_enter_the_local_game_flow() {
        assertThat(
            WhodunitPlayModePolicy.supportsLocalEntry(PlayMode.PassAndPlay),
        ).isTrue()
        assertThat(
            WhodunitPlayModePolicy.supportsLocalEntry(PlayMode.Solo),
        ).isFalse()
        assertThat(
            WhodunitPlayModePolicy.supportsLocalEntry(
                PlayMode.MultiDevice(PlayerId("host"), isHost = true),
            ),
        ).isFalse()
    }
}
