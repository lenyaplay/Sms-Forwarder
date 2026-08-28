package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.SimOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadUiStateTest {

    @Test
    fun showSimSelectorFalseWhenNoSims() {
        assertFalse(ThreadUiState(sender = "+1").showSimSelector)
    }

    @Test
    fun showSimSelectorFalseWithExactlyOneSim() {
        val state = ThreadUiState(sender = "+1", availableSims = listOf(SimOption(1, 0, "SIM 1")))
        assertFalse(state.showSimSelector)
    }

    @Test
    fun showSimSelectorTrueWithTwoOrMoreSims() {
        val state = ThreadUiState(
            sender = "+1",
            availableSims = listOf(SimOption(1, 0, "SIM 1"), SimOption(2, 1, "SIM 2")),
        )
        assertTrue(state.showSimSelector)
    }
}
