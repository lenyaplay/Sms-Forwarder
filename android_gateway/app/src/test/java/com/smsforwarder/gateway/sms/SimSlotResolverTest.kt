package com.smsforwarder.gateway.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimSlotResolverTest {

    @Test
    fun prefersModernSlotIndexExtra() {
        assertEquals(1, SimSlotResolver.resolve(slotIndexExtra = 1, legacySlotExtra = 0))
    }

    @Test
    fun fallsBackToLegacySlotExtraWhenModernMissing() {
        assertEquals(0, SimSlotResolver.resolve(slotIndexExtra = null, legacySlotExtra = 0))
    }

    @Test
    fun ignoresNegativeExtrasAndFallsThroughToLegacy() {
        assertEquals(0, SimSlotResolver.resolve(slotIndexExtra = -1, legacySlotExtra = 0))
    }

    @Test
    fun returnsNullWhenNeitherExtraPresent() {
        assertNull(SimSlotResolver.resolve(slotIndexExtra = null, legacySlotExtra = null))
    }

    @Test
    fun returnsNullWhenBothExtrasNegative() {
        assertNull(SimSlotResolver.resolve(slotIndexExtra = -1, legacySlotExtra = -1))
    }
}
