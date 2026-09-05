package com.smsforwarder.gateway.ui.thread

/** Spec 0031 B.1: the action menu opens near the tap point, downward by default; if there isn't enough room below the tap for the menu, it opens upward instead. */
fun menuOpensUpward(tapY: Float, screenHeight: Float, estimatedMenuHeight: Float): Boolean =
    screenHeight - tapY < estimatedMenuHeight
