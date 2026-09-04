package com.parlor.designsystem.icons

import kotlin.test.Test
import kotlin.test.assertTrue

class ParlorIconsTest {
    @Test
    fun directional_icons_mirror_with_layout_direction() {
        assertTrue(ParlorIcons.Back.autoMirror)
        assertTrue(ParlorIcons.Forward.autoMirror)
    }
}
