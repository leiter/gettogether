package com.gettogether.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Reusable avatar component that displays image or falls back to initials
 */
@Composable
fun AvatarImage(
    avatarUri: String?,
    displayName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val initial = displayName.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar for $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            // Fallback to initial
            Text(
                text = initial,
                style = when {
                    size >= 160.dp -> MaterialTheme.typography.displayLarge
                    size >= 120.dp -> MaterialTheme.typography.displayMedium
                    size >= 64.dp -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Variant for contacts with different color scheme
 */
@Composable
fun ContactAvatarImage(
    avatarUri: String?,
    displayName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val initial = displayName.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar for $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initial,
                style = when {
                    size >= 160.dp -> MaterialTheme.typography.displayLarge
                    size >= 120.dp -> MaterialTheme.typography.displayMedium
                    size >= 64.dp -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
