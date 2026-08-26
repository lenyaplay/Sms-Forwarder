package com.smsforwarder.viewer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object SettingsTestTags {
    const val SERVER_URL_TEXT = "settings_server_url_text"
    const val CHANGE_SERVER_BUTTON = "settings_change_server_button"
    const val LOGOUT_BUTTON = "settings_logout_button"
}

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onServerChangeRequested: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var changingServer by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            if (changingServer) onServerChangeRequested() else onLoggedOut()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Text(text = "Server", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Text(
            text = uiState.serverUrl,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag(SettingsTestTags.SERVER_URL_TEXT),
        )
        OutlinedButton(
            onClick = {
                changingServer = true
                viewModel.logout()
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(SettingsTestTags.CHANGE_SERVER_BUTTON),
        ) {
            Text("Change server (requires re-login)")
        }

        Button(
            onClick = {
                changingServer = false
                viewModel.logout()
            },
            modifier = Modifier
                .padding(top = 32.dp)
                .testTag(SettingsTestTags.LOGOUT_BUTTON),
        ) {
            Text("Log out")
        }
    }
}
