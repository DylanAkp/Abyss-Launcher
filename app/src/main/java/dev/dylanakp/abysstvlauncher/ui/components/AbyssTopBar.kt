package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.AbyssNavTab
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AbyssTopBar(
    selectedTab: AbyssNavTab,
    isSeerConfigured: Boolean,
    onSelectTab: (AbyssNavTab) -> Unit,
    onToggleSearch: () -> Unit,
    onFindRemote: (() -> Unit)? = null,
    onToggleLanguageQuick: () -> Unit = {},
    onOpenAndroidSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    modifier: Modifier = Modifier,
    weatherText: String? = null,
    isSearchActive: Boolean = false,
    activeTabRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    var currentTime by remember { mutableStateOf(formatTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatTime()
            delay(15_000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp)
    ) {
        // Left: Floating Frosted Pill Group for Clock & Weather
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(AbyssPillGlass)
                .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 13.sp
                    ),
                    color = AbyssTextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (!weatherText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = weatherText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = AbyssTextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Center: Floating Frosted Pill Group for Navigation
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(AbyssPillGlass)
                .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = buildList {
                add(AbyssNavTab.HOME to stringResource(R.string.nav_home))
                add(AbyssNavTab.MOVIES to stringResource(R.string.nav_movies))
                add(AbyssNavTab.SERIES to stringResource(R.string.nav_series))
                if (isSeerConfigured) {
                    add(AbyssNavTab.DISCOVER to stringResource(R.string.nav_discover))
                }
                add(AbyssNavTab.APPS to stringResource(R.string.nav_apps))
            }
            tabs.forEach { (tab, label) ->
                val isSelected = !isSearchActive && selectedTab == tab
                AbyssNavPillItem(
                    label = label,
                    isSelected = isSelected,
                    focusRequester = if (selectedTab == tab) activeTabRequester else null,
                    onNavigateDown = onNavigateDown,
                    onClick = { onSelectTab(tab) }
                )
            }
        }

        // Right: Floating Frosted Pill Group for Actions (Remote, Search, Settings)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(50))
                .background(AbyssPillGlass)
                .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onFindRemote != null) {
                TopBarActionPill(
                    icon = AbyssIconType.REMOTE,
                    contentDescription = stringResource(R.string.find_remote),
                    isSelected = false,
                    onNavigateDown = onNavigateDown,
                    onClick = onFindRemote
                )
            }
            TopBarActionPill(
                icon = AbyssIconType.SEARCH,
                contentDescription = stringResource(R.string.search),
                isSelected = isSearchActive,
                onNavigateDown = onNavigateDown,
                onClick = onToggleSearch
            )
            TopBarActionPill(
                icon = AbyssIconType.SETTINGS,
                contentDescription = stringResource(R.string.settings_header_title),
                isSelected = false,
                onNavigateDown = onNavigateDown,
                onClick = onOpenLauncherSettings
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AbyssNavPillItem(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(120),
        label = "navPillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> AbyssTextSecondary
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
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isSelected) {
                    onClick()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onNavigateDown?.invoke()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarActionPill(
    icon: AbyssIconType,
    contentDescription: String,
    isEmphasized: Boolean = false,
    isSelected: Boolean = false,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(120),
        label = "actionPillScale"
    )

    val (bgColor, borderColor, iconColor) = when {
        isFocused -> Triple(Color.White, Color.Transparent, Color.Black)
        isSelected -> Triple(Color(0x38FFFFFF), Color.Transparent, Color.White)
        isEmphasized -> Triple(Color(0x22FFFFFF), Color(0x1AFFFFFF), AbyssTextPrimary)
        else -> Triple(Color.Transparent, Color(0x1AFFFFFF), AbyssTextPrimary)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onNavigateDown?.invoke()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AbyssIcon(
            type = icon,
            color = iconColor,
            size = 13.dp
        )
    }
}

private fun formatTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}
