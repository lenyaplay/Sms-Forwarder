package com.smsforwarder.viewer.ui.devicedetail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto

object DeviceDetailTestTags {
    const val LOADING = "device_detail_loading"
    const val GENERATE_INVITE_BUTTON = "device_detail_generate_invite_button"
    const val INVITE_QR_IMAGE = "device_detail_invite_qr_image"
    const val INVITE_TOKEN_TEXT = "device_detail_invite_token_text"
    const val UPLOAD_TOKEN_TEXT = "device_detail_upload_token_text"
    const val REISSUE_UPLOAD_TOKEN_BUTTON = "device_detail_reissue_upload_token_button"
    const val ERROR_TEXT = "device_detail_error_text"
    const val CONFIRM_REVOKE_BUTTON = "device_detail_confirm_revoke_button"
    const val CONFIRM_REISSUE_BUTTON = "device_detail_confirm_reissue_button"
    fun downloadTokenItem(id: Long) = "device_detail_download_token_item_$id"
    fun revokeButton(id: Long) = "device_detail_revoke_button_$id"
}

private const val QR_SIZE_PX = 512

private fun generateQrBitmap(text: String, sizePx: Int = QR_SIZE_PX): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@Composable
fun DeviceDetailScreen(
    deviceName: String,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var tokenPendingRevoke by remember { mutableStateOf<Long?>(null) }
    var showReissueConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text(deviceName) })
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag(DeviceDetailTestTags.LOADING))
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text(text = "Invite a viewer", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = viewModel::generateInvite,
                    enabled = !uiState.isGeneratingInvite,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(DeviceDetailTestTags.GENERATE_INVITE_BUTTON),
                ) {
                    Text("Generate invite QR")
                }

                uiState.invitedToken?.let { invite ->
                    val qrBitmap = remember(invite.download_token) { generateQrBitmap(invite.download_token) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Download token QR code",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag(DeviceDetailTestTags.INVITE_QR_IMAGE),
                    )
                    Text(
                        text = invite.download_token,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(DeviceDetailTestTags.INVITE_TOKEN_TEXT),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(text = "Upload token", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uiState.uploadToken ?: "(unavailable)",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(DeviceDetailTestTags.UPLOAD_TOKEN_TEXT),
                )
                OutlinedButton(
                    onClick = { showReissueConfirm = true },
                    enabled = !uiState.isReissuing,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(DeviceDetailTestTags.REISSUE_UPLOAD_TOKEN_BUTTON),
                ) {
                    Text("Reissue")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(text = "Invited viewers (download tokens)", style = MaterialTheme.typography.titleMedium)

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(DeviceDetailTestTags.ERROR_TEXT),
                    )
                }
            }

            items(uiState.downloadTokens, key = { it.id }) { token ->
                DownloadTokenRow(token = token, onRevokeClick = { tokenPendingRevoke = token.id })
            }
        }
    }

    tokenPendingRevoke?.let { tokenId ->
        AlertDialog(
            onDismissRequest = { tokenPendingRevoke = null },
            title = { Text("Revoke invite?") },
            text = { Text("This download token will stop working immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeDownloadToken(tokenId)
                        tokenPendingRevoke = null
                    },
                    modifier = Modifier.testTag(DeviceDetailTestTags.CONFIRM_REVOKE_BUTTON),
                ) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { tokenPendingRevoke = null }) { Text("Cancel") }
            },
        )
    }

    if (showReissueConfirm) {
        AlertDialog(
            onDismissRequest = { showReissueConfirm = false },
            title = { Text("Reissue upload token?") },
            text = { Text("The current upload token will stop working - update it on the Gateway App too.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reissueUploadToken()
                        showReissueConfirm = false
                    },
                    modifier = Modifier.testTag(DeviceDetailTestTags.CONFIRM_REISSUE_BUTTON),
                ) { Text("Reissue") }
            },
            dismissButton = {
                TextButton(onClick = { showReissueConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadTokenRow(token: DownloadTokenDto, onRevokeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(DeviceDetailTestTags.downloadTokenItem(token.id)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = token.download_token, modifier = Modifier.padding(end = 8.dp))
        TextButton(
            onClick = onRevokeClick,
            modifier = Modifier.testTag(DeviceDetailTestTags.revokeButton(token.id)),
        ) {
            Text("Revoke")
        }
    }
}
