package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.touchClickable
import com.github.damontecres.wholphin.ui.util.StreamFormatting
import org.jellyfin.sdk.model.api.MediaSourceInfo

@Composable
fun VersionCard(
    source: MediaSourceInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
    fontSize: TextUnit = 11.5.sp,
    onLongClick: (() -> Unit)? = null,
    cardWidth: Dp = 300.dp,
    cardHeight: Dp = 175.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val title = remember(source, isLocal) {
        StreamFormatting.getDisplayTitle(source, isLocal)
    }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceColorAtAlpha(0.6f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
        border = CardDefaults.border(
            border = if (isSelected) {
                Border(
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            } else {
                Border.None
            },
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.border,
                ),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.04f,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = modifier
            .size(cardWidth, cardHeight)
            .touchClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = title,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.38f).sp,
                fontWeight = FontWeight.Normal,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.inverseOnSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun androidx.tv.material3.ColorScheme.surfaceColorAtAlpha(alpha: Float): Color {
    return surface.copy(alpha = alpha)
}
