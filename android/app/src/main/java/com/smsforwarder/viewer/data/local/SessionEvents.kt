package com.smsforwarder.viewer.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcasts a forced-logout signal (e.g. refresh token rejected) so the nav
 * graph can pop back to the login screen regardless of which screen is
 * currently active.
 */
@Singleton
class SessionEvents @Inject constructor() {
    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut

    fun notifyLoggedOut() {
        _loggedOut.tryEmit(Unit)
    }
}
