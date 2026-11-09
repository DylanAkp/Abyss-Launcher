package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary

@Composable
fun AppContextMenu(
    app: TvAppInfo?,
    onDismiss: () -> Unit,
    onLaunch: (TvAppInfo) -> Unit,
    onTogglePin: (TvAppInfo) -> Unit,
    onMoveLeft: (TvAppInfo) -> Unit,
    onMoveRight: (TvAppInfo) -> Unit,
    onToggleHide: (TvAppInfo) -> Unit,
    onOpenSystemInfo: (TvAppInfo) -> Unit
) {
    AnimatedVisibility(
        visible = app != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120))
    ) {
        if (app == null) return@AnimatedVisibility
        val initialFocusRequester = remember { FocusRequester() }
        var hasSeenFreshDown by remember(app.packageName) { mutableStateOf(false) }

        LaunchedEffect(app) {
            kotlinx.coroutines.delay(60L)
            try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB305070C))
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isSelectKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

                    if (isSelectKey) {
                        when (native.action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (native.repeatCount == 0) {
                                    hasSeenFreshDown = true
                                    false
                                } else {
                                    !hasSeenFreshDown
                                }
                            }
                            KeyEvent.ACTION_UP -> {
                                !hasSeenFreshDown
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Modal Sheet (Zero Outline)
            Box(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E2330),
                                Color(0xFF131620)
                            )
                        )
                    )
                    .clickable(enabled = false) {}
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Card with App Icon & Label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x14FFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Squircle Icon Badge
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x28FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconBmp = remember(app.packageName, app.icon) {
                                app.icon?.toBitmapSafe(96, 96)?.asImageBitmap()
                            }
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                AbyssIcon(
                                    type = AbyssIconType.APPS_GRID,
                                    color = Color.White,
                                    size = 24.dp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = AbyssTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = AbyssTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Menu Options
                    ContextMenuItem(
                        label = if (app.isPinned) stringResource(R.string.app_action_unpin) else stringResource(R.string.app_action_pin),
                        icon = if (app.isPinned) AbyssIconType.UNPIN else AbyssIconType.PIN,
                        focusRequester = initialFocusRequester,
                        isPrimary = true,
                        onClick = { onTogglePin(app) }
                    )

                    if (app.isPinned) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ContextMenuItem(
                                    label = stringResource(R.string.app_action_move_left),
                                    icon = AbyssIconType.ARROW_LEFT,
                                    onClick = { onMoveLeft(app) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ContextMenuItem(
                                    label = stringResource(R.string.app_action_move_right),
                                    icon = AbyssIconType.ARROW_RIGHT,
                                    onClick = { onMoveRight(app) }
                                )
                            }
                        }
                    }

                    ContextMenuItem(
                        label = if (app.isHidden) stringResource(R.string.app_action_unhide) else stringResource(R.string.app_action_hide),
                        icon = if (app.isHidden) AbyssIconType.EYE else AbyssIconType.EYE_OFF,
                        onClick = { onToggleHide(app) }
                    )

                    ContextMenuItem(
                        label = stringResource(R.string.app_action_system_info),
                        icon = AbyssIconType.INFO,
                        onClick = { onOpenSystemInfo(app) }
                    )

                    ContextMenuItem(
                        label = stringResource(R.string.app_action_launch),
                        icon = AbyssIconType.PLAY,
                        onClick = { onLaunch(app) }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    ContextMenuItem(
                        label = stringResource(R.string.app_action_close),
                        icon = AbyssIconType.CLOSE,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    icon: AbyssIconType? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(120),
        label = "menuItemScale"
    )

    val contentColor = if (isFocused) Color(0xFF1E222B) else Color(0xFFF1F5F9)
    val badgeBg = if (isFocused) Color(0x1F000000) else Color(0x1FFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isPrimary -> Color(0x24FFFFFF)
                    else -> Color(0x12FFFFFF)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    AbyssIcon(
                        type = icon,
                        color = contentColor,
                        size = 15.dp
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp
                ),
                color = contentColor
            )
        }
    }
}
