package dev.dylanakp.abysstvlauncher.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType

@Composable
fun OsdPillButton(
    label: String,
    icon: AbyssIconType? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(110),
        label = "osdBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> Color(0x38FFFFFF)
            else -> Color(0x66101420)
        },
        animationSpec = tween(110),
        label = "osdBtnBg"
    )
    val contentColor = when {
        isFocused -> Color(0xFF1E222B)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = if (label.isEmpty()) 12.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (icon != null) {
                AbyssIcon(
                    type = icon,
                    color = contentColor,
                    size = 13.dp
                )
            }
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
