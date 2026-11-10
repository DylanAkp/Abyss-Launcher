package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.AbyssNavTab
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Google TV Frosted Glass Dark Translucent Pill (Zero Hard Borders)
private val GoogleTvPillTransparency = Color(0x66101420)

@Composable
fun AbyssTopBar(
    selectedTab: AbyssNavTab,
    isSeerConfigured: Boolean,
    onSelectTab: (AbyssNavTab) -> Unit,
    onToggleSearch: () -> Unit = {},
    onOpenSearch: () -> Unit = onToggleSearch,
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
        // Left Cluster: Google TV Dual Pill System (Navigation Dock + Action Cluster)
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pill 1: Navigation Pill Dock (Search + Tabs)
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GoogleTvPillTransparency)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Icon on Left (Acts as a direct page)
                TopBarSearchIconPill(
                    isSelected = isSearchActive,
                    focusRequester = if (isSearchActive) activeTabRequester else null,
                    onNavigateDown = onNavigateDown,
                    onClick = onOpenSearch
                )

                // Tabs
                val tabs = buildList {
                    add(AbyssNavTab.HOME to stringResource(R.string.nav_home))
                    add(AbyssNavTab.MOVIES to stringResource(R.string.nav_movies))
                    add(AbyssNavTab.SERIES to stringResource(R.string.nav_series))
                    add(AbyssNavTab.APPS to stringResource(R.string.nav_apps))
                }
                tabs.forEach { (tab, label) ->
                    val isSelected = !isSearchActive && selectedTab == tab
                    AbyssNavPillItem(
                        label = label,
                        isSelected = isSelected,
                        focusRequester = if (!isSearchActive && selectedTab == tab) activeTabRequester else null,
                        onNavigateDown = onNavigateDown,
                        onClick = { onSelectTab(tab) }
                    )
                }
            }

            // Pill 2: Actions Pill Dock (Discover + Remote + Settings)
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GoogleTvPillTransparency)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Discover Compass Icon near Settings (if Seer is configured)
                if (isSeerConfigured) {
                    val isDiscoverSelected = !isSearchActive && selectedTab == AbyssNavTab.DISCOVER
                    TopBarVectorActionPill(
                        icon = CompassExploreIcon,
                        contentDescription = stringResource(R.string.nav_discover),
                        isSelected = isDiscoverSelected,
                        autoSelectOnFocus = true,
                        focusRequester = if (isDiscoverSelected) activeTabRequester else null,
                        onNavigateDown = onNavigateDown,
                        onClick = { onSelectTab(AbyssNavTab.DISCOVER) }
                    )
                }

                // Shield Remote Finder (if supported)
                if (onFindRemote != null) {
                    TopBarActionPill(
                        icon = AbyssIconType.REMOTE,
                        contentDescription = stringResource(R.string.find_remote),
                        isSelected = false,
                        onNavigateDown = onNavigateDown,
                        onClick = onFindRemote
                    )
                }

                // Official Google Material Settings Gear
                TopBarVectorActionPill(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_header_title),
                    isSelected = false,
                    onNavigateDown = onNavigateDown,
                    onClick = onOpenLauncherSettings
                )
            }
        }

        // Right: Frosted Pill Time & Weather Dock (Clean legibility on any backdrop)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(42.dp)
                .clip(RoundedCornerShape(50))
                .background(GoogleTvPillTransparency)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!weatherText.isNullOrBlank()) {
                Text(
                    text = weatherText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color(0x80FFFFFF))
                )
            }

            Text(
                text = currentTime,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarSearchIconPill(
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(120),
        label = "searchPillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color.Transparent
    }
    val iconColor = when {
        isFocused -> Color(0xFF1E222B)
        isSelected -> Color.White
        else -> Color(0xFFE2E8F0)
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
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isSelectKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isSelectKey && native.action == KeyEvent.ACTION_UP) {
                    onClick()
                    true
                } else if (native.action == KeyEvent.ACTION_DOWN && native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    onNavigateDown?.invoke()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.search),
            tint = iconColor,
            modifier = Modifier.size(15.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarVectorActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean = false,
    autoSelectOnFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(120),
        label = "actionPillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color.Transparent
    }
    val iconColor = when {
        isFocused -> Color(0xFF1E222B)
        isSelected -> Color.White
        else -> Color(0xFFE2E8F0)
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(34.dp)
            .clip(CircleShape)
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isSelected && autoSelectOnFocus) {
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
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
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
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "navPillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color(0xFF1E222B)
        isSelected -> Color.White
        else -> Color(0xFFE2E8F0)
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
            .padding(
                horizontal = if (isSelected || isFocused) 16.dp else 14.dp,
                vertical = 7.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = when {
                    isFocused -> FontWeight.Bold
                    isSelected -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                fontSize = 13.sp
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
    isSelected: Boolean = false,
    onNavigateDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(120),
        label = "actionPillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0x38FFFFFF)
        else -> Color.Transparent
    }
    val iconColor = when {
        isFocused -> Color(0xFF1E222B)
        isSelected -> Color.White
        else -> Color(0xFFE2E8F0)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(34.dp)
            .clip(CircleShape)
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
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
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AbyssIcon(
            type = icon,
            color = iconColor,
            size = 15.dp
        )
    }
}

private fun formatTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}
