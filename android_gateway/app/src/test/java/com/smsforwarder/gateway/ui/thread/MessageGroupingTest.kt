package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MessageGroupingTest {

    private fun message(id: Long, sender: String, direction: MessageDirection, createdAt: Long) = MessageEntity(
        id = id,
        sender = sender,
        text = "text",
        sentStamp = createdAt,
        receivedStamp = createdAt,
        simSlot = null,
        deliveryStatus = DeliveryStatus.SENT,
        createdAt = createdAt,
        direction = direction,
    )

    @Test
    fun firstMessageInListIsAlwaysFirstInGroup() {
        val messages = listOf(message(1, "+1", MessageDirection.IN, 0L))
        assertTrue(messages.isFirstInGroup(0))
    }

    @Test
    fun sameSenderAndDirectionWithinFiveMinutesStaysInSameGroup() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, 0L),
            message(2, "+1", MessageDirection.IN, 4 * 60 * 1000L),
        )
        assertFalse(messages.isFirstInGroup(1))
    }

    @Test
    fun gapOfFiveMinutesOrMoreStartsNewGroup() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, 0L),
            message(2, "+1", MessageDirection.IN, 5 * 60 * 1000L),
        )
        assertTrue(messages.isFirstInGroup(1))
    }

    @Test
    fun senderChangeStartsNewGroupEvenWithinFiveMinutes() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, 0L),
            message(2, "+2", MessageDirection.IN, 1000L),
        )
        assertTrue(messages.isFirstInGroup(1))
    }

    @Test
    fun directionChangeStartsNewGroupEvenWithinFiveMinutes() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, 0L),
            message(2, "+1", MessageDirection.OUT, 1000L),
        )
        assertTrue(messages.isFirstInGroup(1))
    }

    private fun timestampFor(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun firstMessageInListIsAlwaysFirstOnNewDay() {
        val messages = listOf(message(1, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 10, 0)))
        assertTrue(messages.isFirstOnNewDay(0))
    }

    @Test
    fun sameCalendarDayIsNotFirstOnNewDay() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 9, 0)),
            message(2, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 23, 0)),
        )
        assertFalse(messages.isFirstOnNewDay(1))
    }

    @Test
    fun crossingMidnightIsFirstOnNewDayEvenTwoMinutesApart() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 23, 59)),
            message(2, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 14, 0, 1)),
        )
        assertTrue(messages.isFirstOnNewDay(1))
    }

    @Test
    fun twentyHoursApartSameDayIsNotFirstOnNewDay() {
        val messages = listOf(
            message(1, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 0, 30)),
            message(2, "+1", MessageDirection.IN, timestampFor(2026, Calendar.SEPTEMBER, 13, 20, 30)),
        )
        assertFalse(messages.isFirstOnNewDay(1))
    }
}
