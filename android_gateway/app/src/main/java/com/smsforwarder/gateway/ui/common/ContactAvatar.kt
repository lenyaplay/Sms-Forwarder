package com.smsforwarder.gateway.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

object ContactAvatarTestTags {
    const val PHOTO = "contact_avatar_photo"
    const val INITIAL = "contact_avatar_initial"
    const val FALLBACK_ICON = "contact_avatar_fallback_icon"
}

// internal, not private: ConversationRowContent (ConversationsScreen.kt) reuses this
// to align its divider's start indent with the avatar's actual width, rather than
// duplicating the 40.dp as a second magic number that could silently drift out of sync.
internal val AVATAR_SIZE = 40.dp

// Fixed, deterministic palette (not MaterialTheme.colorScheme.primary etc. directly -
// a single shared hue for every sender would defeat the point of a color cue) so the
// same sender always gets the same background across recompositions/app restarts,
// like Telegram/Gmail initial-avatars.
private val initialAvatarColors = listOf(
    Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF5C6BC0), Color(0xFF29B6F6),
    Color(0xFF26A69A), Color(0xFF9CCC65), Color(0xFFFFA726), Color(0xFF8D6E63),
)

/**
 * Circular contact avatar with a 3-level fallback (spec 0027): contact photo, if
 * resolved -> first letter of [displayName], if a name actually resolved (not just
 * the raw [sender] echoed back) -> generic grey silhouette. The silhouette covers
 * every other case uniformly (no permission, no match, alphanumeric sender id) - no
 * special-cased visual per reason, by explicit product decision.
 */
@Composable
fun ContactAvatar(
    displayName: String?,
    photoUri: String?,
    sender: String,
    modifier: Modifier = Modifier,
) {
    when {
        photoUri != null -> AsyncImage(
            model = photoUri,
            contentDescription = null,
            modifier = modifier.size(AVATAR_SIZE).clip(CircleShape).testTag(ContactAvatarTestTags.PHOTO),
        )
        !displayName.isNullOrBlank() && displayName != sender -> Box(
            modifier = modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(initialAvatarColors[avatarColorIndex(sender)])
                .testTag(ContactAvatarTestTags.INITIAL),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.trim().take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        else -> Box(
            modifier = modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(ContactAvatarTestTags.FALLBACK_ICON),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AVATAR_SIZE),
            )
        }
    }
}

private fun avatarColorIndex(sender: String): Int =
    sender.hashCode().absoluteValue % initialAvatarColors.size
