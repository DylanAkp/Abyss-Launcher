package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssTextMuted
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid

@Composable
fun HeroBackdropArtwork(
    item: MediaItem?,
    modifier: Modifier = Modifier
) {
    if (item == null) return

    AnimatedContent(
        targetState = item,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
        },
        label = "heroBackdropDissolve",
        modifier = modifier
    ) { targetItem ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AbyssVoid)
        ) {
            val artworkUrl = targetItem.backdropUrl.ifBlank { targetItem.posterUrl }
            if (artworkUrl.isNotBlank()) {
                // Real full-bleed 16:9 cinema banner spanning the entire screen width
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AbyssMediaArtwork(
                        imageUrl = artworkUrl,
                        primaryColor = Color(0xFF1E2433),
                        secondaryColor = Color(0xFF131722),
                        darkColor = AbyssVoid,
                        seedKey = "${targetItem.id}_banner",
                        isHeroBackdrop = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic left vignette ensuring title and badges are 100% crisp and readable
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to AbyssVoid,
                                    0.25f to AbyssVoid.copy(alpha = 0.90f),
                                    0.50f to AbyssVoid.copy(alpha = 0.55f),
                                    0.75f to AbyssVoid.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent
                                )
                            )
                    )
                }
            }

            // Silky bottom vignette fading smoothly into pure OLED black
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AbyssVoid.copy(alpha = 0.35f),
                                AbyssVoid.copy(alpha = 0.75f),
                                AbyssVoid.copy(alpha = 0.95f),
                                AbyssVoid
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun HeroSpotlightContent(
    item: MediaItem?,
    isInWatchlist: Boolean,
    onPlayClick: (MediaItem) -> Unit,
    onDetailsClick: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    playButtonFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onPlayFocused: (() -> Unit)? = null
) {
    if (item == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 270.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Curated Eyebrow Tag
        val isResume = item.progressPercent > 0f || item.qualityBadge.startsWith("S") || item.qualityBadge.contains("REPRENDRE", ignoreCase = true)
        val eyebrow = if (isResume) {
            stringResource(R.string.hero_eyebrow_resume)
        } else {
            stringResource(R.string.hero_eyebrow_trending)
        }
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 10.sp
            ),
            color = if (isResume) AbyssGold else AbyssTextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title(),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp
            ),
            color = AbyssTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Badges Row (Deduplicated, no duplicate "4K HDR")
        val seriesBadge = stringResource(R.string.badge_series)
        val badges = remember(item.id, item.year, item.duration(), item.qualityBadge, item.rating) {
            val list = mutableListOf<String>()
            if (item.year.isNotBlank()) list.add(item.year)
            if (item.rating.isNotBlank() && item.rating != "0" && item.rating != "0.0") {
                list.add("★ ${item.rating}")
            }
            if (item.category == MediaCategory.SERIES) {
                list.add(seriesBadge)
            }
            val dur = item.duration().trim()
            if (dur.isNotBlank() && !dur.contains("4K", ignoreCase = true) && !dur.contains("HDR", ignoreCase = true)) {
                list.add(dur)
            }
            if (item.qualityBadge.isNotBlank() && item.qualityBadge != "HD") {
                list.add(item.qualityBadge)
            }
            list.distinct()
        }
        if (badges.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                badges.forEach { badge ->
                    MetadataPill(text = badge)
                }
            }
        }

        // Synopsis (if present)
        if (item.synopsis().isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.synopsis(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = AbyssTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Button: Minimal "Regarder" / "Watch" or "Reprendre" / "Resume" CTA
        val playLabel = if (isResume) {
            stringResource(R.string.cta_resume)
        } else {
            stringResource(R.string.cta_watch)
        }
        HeroMinimalButton(
            text = "▶  $playLabel",
            isPrimary = true,
            focusRequester = playButtonFocusRequester,
            onNavigateUp = onNavigateUp,
            onNavigateDown = onNavigateDown,
            onFocused = onPlayFocused,
            onClick = { onPlayClick(item) }
        )
    }
}

@Composable
fun HeroSpotlightBanner(
    item: MediaItem?,
    isInWatchlist: Boolean,
    totalSpotlightCount: Int,
    currentSpotlightIndex: Int,
    onPlayClick: (MediaItem) -> Unit,
    onDetailsClick: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    onSelectSpotlightIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (item == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(370.dp)
            .background(AbyssVoid)
    ) {
        HeroBackdropArtwork(
            item = item,
            modifier = Modifier.fillMaxSize()
        )
        HeroSpotlightContent(
            item = item,
            isInWatchlist = isInWatchlist,
            onPlayClick = onPlayClick,
            onDetailsClick = onDetailsClick,
            onToggleWatchlist = onToggleWatchlist,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun MetadataPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = AbyssTextSecondary
        )
    }
}

@Composable
private fun HeroMinimalButton(
    text: String,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(120),
        label = "heroBtnScale"
    )

    val (bgColor, textColor) = when {
        isFocused -> Pair(Color.White, Color.Black)
        isPrimary -> Pair(Color(0x38FFFFFF), Color.White)
        else -> Pair(Color(0x18FFFFFF), AbyssTextPrimary)
    }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp.invoke()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onNavigateDown != null) {
                                onNavigateDown.invoke()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
