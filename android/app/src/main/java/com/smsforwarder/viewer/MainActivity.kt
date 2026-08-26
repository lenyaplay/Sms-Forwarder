package com.smsforwarder.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.ui.nav.NavGraph
import com.smsforwarder.viewer.ui.theme.SmsForwarderViewerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var sessionEvents: SessionEvents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsForwarderViewerTheme {
                NavGraph(authRepository = authRepository, sessionEvents = sessionEvents)
            }
        }
    }
}
