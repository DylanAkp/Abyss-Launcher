package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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

@Composable
internal fun SettingsSidebarActionButton(
    label: String,
    subLabel: String,
    icon: AbyssIconType,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "actionBtnScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val badgeBg = if (isFocused) Color(0xFFCBD5E1) else Color(0xFF1E2025)
        val iconColor = if (isFocused) Color(0xFF131314) else Color(0xFF8E918F)
        val textColor = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED)
        val subColor = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            AbyssIcon(
                type = icon,
                color = iconColor,
                size = 18.dp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = textColor
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = subColor
            )
        }

        AbyssIcon(
            type = AbyssIconType.EXTERNAL_LINK,
            color = iconColor,
            size = 14.dp
        )
    }
}

@Composable
internal fun SettingsHeaderGearButton(
    onClick: () -> Unit,
    contentDescription: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1E2025))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = contentDescription,
            tint = if (isFocused) Color(0xFF131314) else Color(0xFFBDC1C6),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun SettingsNavTab(
    label: String,
    subLabel: String,
    icon: AbyssIconType,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "navTabScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isSelected -> Color(0xFF282D37)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isSelected) {
                    onClick()
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val badgeBg = when {
            isFocused -> Color(0xFFCBD5E1)
            isSelected -> Color(0xFF282B32)
            else -> Color(0xFF181A1F)
        }
        val iconColor = when {
            isFocused -> Color(0xFF131314)
            isSelected -> Color(0xFF8AB4F8)
            else -> Color(0xFF8E918F)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            AbyssIcon(
                type = icon,
                color = iconColor,
                size = 18.dp
            )
        }

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = when {
                    isFocused -> Color(0xFF131314)
                    isSelected -> Color.White
                    else -> Color(0xFFBDC1C6)
                }
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = when {
                    isFocused -> Color(0xFF444746)
                    isSelected -> Color(0xFF8E918F)
                    else -> Color(0xFF5E6368)
                }
            )
        }
    }
}

@Composable
internal fun SettingsBackButton(
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1E2025))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AbyssIcon(
            type = AbyssIconType.ARROW_LEFT,
            color = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED),
            size = 14.dp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED)
        )
    }
}
