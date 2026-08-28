package com.smsforwarder.viewer.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object RegisterTestTags {
    const val USERNAME_FIELD = "register_username_field"
    const val PASSWORD_FIELD = "register_password_field"
    const val TOGGLE_PASSWORD_VISIBILITY = "register_toggle_password_visibility"
    const val CONFIRM_PASSWORD_FIELD = "register_confirm_password_field"
    const val TOGGLE_CONFIRM_PASSWORD_VISIBILITY = "register_toggle_confirm_password_visibility"
    const val SUBMIT_BUTTON = "register_submit_button"
    const val ERROR_TEXT = "register_error_text"
    const val BACK_TO_LOGIN_LINK = "register_back_to_login_link"
}

@Composable
fun RegisterScreen(
    onRegistered: (username: String) -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.registeredUsername) {
        uiState.registeredUsername?.let(onRegistered)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Create account", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag(RegisterTestTags.USERNAME_FIELD),
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag(RegisterTestTags.TOGGLE_PASSWORD_VISIBILITY),
                ) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(RegisterTestTags.PASSWORD_FIELD),
        )

        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                    modifier = Modifier.testTag(RegisterTestTags.TOGGLE_CONFIRM_PASSWORD_VISIBILITY),
                ) {
                    Text(if (confirmPasswordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD),
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(RegisterTestTags.ERROR_TEXT),
            )
        }

        Button(
            onClick = viewModel::register,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(RegisterTestTags.SUBMIT_BUTTON),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Register")
        }

        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(RegisterTestTags.BACK_TO_LOGIN_LINK),
        ) {
            Text("Already have an account? Log in")
        }
    }
}
