package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.AppUpdate
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlassElevated
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay

@Composable
fun AppUpdateModal(
    update: AppUpdate?,
    isDownloading: Boolean,
    downloadProgress: Float,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AnimatedVisibility(
        visible = update != null,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f)
    ) {
        if (update == null) return@AnimatedVisibility

        val primaryButtonRequester = remember { FocusRequester() }

        LaunchedEffect(update) {
            delay(120)
            try {
                primaryButtonRequester.requestFocus()
            } catch (_: Exception) {}
        }

        BackHandler(enabled = true) {
            if (!isDownloading) {
                onDismiss()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD905060A))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(540.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AbyssSurfaceGlassElevated)
                    .padding(28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header with Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x337C5CFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            AbyssIcon(
                                type = AbyssIconType.INFO,
                                color = Color(0xFFC4B5FD),
                                size = 18.dp
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.update_available_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = AbyssTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.update_available_version,
                                    update.versionName,
                                    BuildConfig.VERSION_NAME
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AbyssTextSecondary
                            )
                        }
                    }

                    // Changelog Section
                    if (update.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.update_changelog_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFC4B5FD)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AFFFFFF))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = update.releaseNotes,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = AbyssTextSecondary
                            )
                        }
                    }

                    // Download Progress Indicator
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF7C5CFF),
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.update_downloading,
                                (downloadProgress * 100).toInt().coerceIn(0, 100)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC4B5FD)
                        )
                    }

                    // Error Message Banner
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33EF4444))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val primaryLabel = when {
                            isDownloading -> stringResource(
                                R.string.update_downloading,
                                (downloadProgress * 100).toInt().coerceIn(0, 100)
                            )
                            errorMessage != null -> stringResource(R.string.update_action_retry)
                            else -> stringResource(R.string.update_action_now)
                        }

                        UpdateModalButton(
                            label = primaryLabel,
                            icon = if (errorMessage != null) AbyssIconType.RESTART else AbyssIconType.CHECK,
                            isPrimary = true,
                            enabled = !isDownloading,
                            focusRequester = primaryButtonRequester,
                            onClick = onUpdate,
                            modifier = Modifier.weight(1f)
                        )

                        if (!isDownloading) {
                            UpdateModalButton(
                                label = stringResource(R.string.update_action_later),
                                icon = AbyssIconType.CLOSE,
                                isPrimary = false,
                                enabled = true,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateModalButton(
    label: String,
    icon: AbyssIconType,
    isPrimary: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(if (isFocused) 1.04f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isPrimary -> Color(0x447C5CFF)
                    else -> Color(0x18FFFFFF)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isSelectKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (enabled && isSelectKey && native.action == KeyEvent.ACTION_UP) {
                    onClick()
                    true
                } else false
            }
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AbyssIcon(
                type = icon,
                color = when {
                    isFocused -> Color.Black
                    isPrimary -> Color(0xFFC4B5FD)
                    else -> AbyssTextPrimary
                },
                size = 14.dp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = when {
                    isFocused -> Color.Black
                    isPrimary -> Color(0xFFEDE9FE)
                    else -> AbyssTextPrimary
                }
            )
        }
    }
}
