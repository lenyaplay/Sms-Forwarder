package com.smsforwarder.gateway.ui.thread

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPositioningTest {

    @Test
    fun opensDownwardWhenPlentyOfRoomBelow() {
        assertFalse(menuOpensUpward(tapY = 200f, screenHeight = 2000f, estimatedMenuHeight = 400f))
    }

    @Test
    fun opensUpwardWhenNotEnoughRoomBelow() {
        assertTrue(menuOpensUpward(tapY = 1800f, screenHeight = 2000f, estimatedMenuHeight = 400f))
    }

    @Test
    fun exactlyEnoughRoomOpensDownward() {
        assertFalse(menuOpensUpward(tapY = 1600f, screenHeight = 2000f, estimatedMenuHeight = 400f))
    }
}
