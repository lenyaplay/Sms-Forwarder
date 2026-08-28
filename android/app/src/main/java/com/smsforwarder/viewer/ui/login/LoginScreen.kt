package com.smsforwarder.viewer.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object LoginTestTags {
    const val USERNAME_FIELD = "login_username_field"
    const val PASSWORD_FIELD = "login_password_field"
    const val TOGGLE_PASSWORD_VISIBILITY = "login_toggle_password_visibility"
    const val SUBMIT_BUTTON = "login_submit_button"
    const val ERROR_TEXT = "login_error_text"
    const val CREATE_ACCOUNT_LINK = "login_create_account_link"
}

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onCreateAccount: () -> Unit = {},
    initialUsername: String = "",
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loggedIn) {
        if (uiState.loggedIn) onLoggedIn()
    }

    LaunchedEffect(initialUsername) {
        if (initialUsername.isNotBlank()) viewModel.onUsernameChange(initialUsername)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "SMS Forwarder Viewer", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            // No capitalization/autocorrect - the IME otherwise mangles a
            // typed login (autocapitalizing the first letter, or swapping
            // it for a dictionary suggestion), which surfaces as a
            // confusing "invalid login or password" instead of a keyboard
            // problem.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
            ),
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag(LoginTestTags.USERNAME_FIELD),
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag(LoginTestTags.TOGGLE_PASSWORD_VISIBILITY),
                ) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(LoginTestTags.PASSWORD_FIELD),
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(LoginTestTags.ERROR_TEXT),
            )
        }

        Button(
            onClick = viewModel::login,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(LoginTestTags.SUBMIT_BUTTON),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Log in")
        }

        TextButton(
            onClick = onCreateAccount,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(LoginTestTags.CREATE_ACCOUNT_LINK),
        ) {
            Text("Create account")
        }
    }
}
