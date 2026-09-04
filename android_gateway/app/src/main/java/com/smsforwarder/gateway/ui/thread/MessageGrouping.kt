package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.db.MessageEntity

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
