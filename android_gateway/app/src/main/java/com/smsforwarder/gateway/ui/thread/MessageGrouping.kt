package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.db.MessageEntity
import java.util.Calendar

private const val GROUP_GAP_MILLIS = 5 * 60 * 1000L

/** Telegram-style grouping (spec 0029): a message starts a new visual group when the sender/direction changes or the gap since the previous message is >= 5 minutes. */
fun List<MessageEntity>.isFirstInGroup(index: Int): Boolean {
    if (index == 0) return true
    val current = this[index]
    val previous = this[index - 1]
    return current.sender != previous.sender ||
        current.direction != previous.direction ||
        current.createdAt - previous.createdAt >= GROUP_GAP_MILLIS
}

/** Spec 0032: a message starts a new calendar day relative to the previous one - independent of [isFirstInGroup]'s 5-minute gap, this is purely a date-boundary check (device default timezone). */
fun List<MessageEntity>.isFirstOnNewDay(index: Int): Boolean {
    if (index == 0) return true
    val current = this[index]
    val previous = this[index - 1]
    return !isSameCalendarDay(current.createdAt, previous.createdAt)
}

private fun isSameCalendarDay(a: Long, b: Long): Boolean {
    val calendarA = Calendar.getInstance().apply { timeInMillis = a }
    val calendarB = Calendar.getInstance().apply { timeInMillis = b }
    return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
        calendarA.get(Calendar.DAY_OF_YEAR) == calendarB.get(Calendar.DAY_OF_YEAR)
}
