package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import dev.dylanakp.abysstvlauncher.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssGold
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary

@OptIn(ExperimentalFoundationApi::class)
private val tvHorizontalBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        // Leading margin: 64px (~32dp)
        // Trailing cushion: 296px (~148dp, 1 card width ahead) so upcoming card is pre-revealed
        val leadingMargin = 64f
        val trailingMargin = 296f
        return when {
            offset < leadingMargin -> offset - leadingMargin
            offset + size > containerSize - trailingMargin -> (offset + size) - (containerSize - trailingMargin)
            else -> 0f
        }
    }

    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = spring(
            dampingRatio = 1.0f,
            stiffness = 500f
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onSeeMoreClick: (() -> Unit)? = null,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    rowState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = AbyssTextPrimary
            )
            if (onSeeMoreClick != null) {
                Text(
                    text = stringResource(R.string.see_all),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = AbyssTextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeeMoreClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides tvHorizontalBringIntoViewSpec) {
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "media_poster" }
                ) { index, item ->
                    val clickLambda = remember(item.id) { { onItemClick(item) } }
                    val longClickLambda = remember(item.id) {
                        if (onItemLongClick != null) { { onItemLongClick(item) } } else null
                    }
                    val focusLambda = remember(item.id) { { onItemFocused(item) } }

                    MediaPosterCard(
                        item = item,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onNavigateUp = onNavigateUp,
                        onClick = clickLambda,
                        onLongClick = longClickLambda,
                        onFocus = focusLambda
                    )
                }

                if (onSeeMoreClick != null) {
                    item(key = "see_more_${title.hashCode()}", contentType = "see_more") {
                        SeeMoreCard(
                            isChannel = false,
                            onClick = onSeeMoreClick,
                            onNavigateUp = onNavigateUp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressConsumed by remember { mutableStateOf(false) }

    val cardScale = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "posterScale"
    )

    val cardShape = remember { RoundedCornerShape(10.dp) }
    val badgeShape = remember { RoundedCornerShape(3.dp) }
    val badgeBg = remember { Color(0x88000000) }

    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(148.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                } else {
                    longPressConsumed = false
                }
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isSelectKey = native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

                when {
                    native.action == KeyEvent.ACTION_DOWN && native.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (onNavigateUp != null) {
                            onNavigateUp.invoke()
                            true
                        } else {
                            false
                        }
                    }
                    isSelectKey && native.action == KeyEvent.ACTION_DOWN -> {
                        if (native.repeatCount == 0) {
                            longPressConsumed = false
                            false
                        } else {
                            if (!longPressConsumed && onLongClick != null) {
                                longPressConsumed = true
                                onLongClick.invoke()
                            }
                            true
                        }
                    }
                    isSelectKey && native.action == KeyEvent.ACTION_UP -> {
                        if (longPressConsumed) {
                            longPressConsumed = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Poster Box: scales up to 1.05x inside the 148dp slot with GPU hardware outline clipping
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(200.dp)
                .graphicsLayer {
                    this.scaleX = cardScale.value
                    this.scaleY = cardScale.value
                    this.shape = cardShape
                    this.clip = true
                }
                .background(if (isFocused) Color(0x38FFFFFF) else AbyssSurfaceGlass)
                .then(
                    if (isFocused) Modifier.border(1.5.dp, Color.White, cardShape)
                    else Modifier
                )
        ) {
            val isProcessing = item.seerStatus == dev.dylanakp.abysstvlauncher.model.SeerMediaStatus.PROCESSING
            AbyssMediaArtwork(
                imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                targetWidth = 240,
                targetHeight = 340,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isProcessing) {
                            alpha = 0.6f
                        }
                    }
            )

            // Top Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.qualityBadge.isNotBlank()) {
                    val isDownloading = item.downloadProgress != null || item.seerStatus == dev.dylanakp.abysstvlauncher.model.SeerMediaStatus.PROCESSING
                    val pillBg = if (isDownloading) Color(0xDD0891B2) else badgeBg
                    val pillText = if (isDownloading) Color(0xFFE0F2FE) else AbyssTextPrimary
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(pillBg)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.qualityBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = if (isDownloading) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = pillText
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (item.rating.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(badgeBg)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "★",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = AbyssGold
                        )
                        Text(
                            text = item.rating,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = AbyssGold
                        )
                    }
                }
            }

                // Bottom Progress Bar: Seer Download (Cyan) or Continue Watching (Gold)
                if (item.downloadProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color(0x99000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.downloadProgress.coerceIn(0.04f, 1f))
                                .background(Color(0xFF06B6D4))
                        )
                    }
                } else if (item.progressPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color(0x88000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progressPercent.coerceIn(0.02f, 1f))
                                .background(AbyssGold)
                        )
                    }
                }
            }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = item.title(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (isFocused) Color.White else AbyssTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveChannelRow(
    title: String,
    items: List<MediaItem>,
    onChannelClick: (MediaItem) -> Unit,
    onChannelFocused: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onSeeMoreClick: (() -> Unit)? = null,
    rowState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = AbyssTextPrimary
            )
            if (onSeeMoreClick != null) {
                Text(
                    text = stringResource(R.string.see_all),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = AbyssTextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeeMoreClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides tvHorizontalBringIntoViewSpec) {
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "live_channel" }
                ) { index, item ->
                    val clickLambda = remember(item.id) { { onChannelClick(item) } }
                    val focusLambda = remember(item.id) { { onChannelFocused(item) } }

                    LiveChannelCard(
                        item = item,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onNavigateUp = onNavigateUp,
                        onClick = clickLambda,
                        onFocus = focusLambda
                    )
                }

                if (onSeeMoreClick != null) {
                    item(key = "see_more_channel_${title.hashCode()}", contentType = "see_more") {
                        SeeMoreCard(
                            isChannel = true,
                            onClick = onSeeMoreClick,
                            onNavigateUp = onNavigateUp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val cardScale = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "liveScale"
    )

    val channelShape = remember { RoundedCornerShape(10.dp) }

    // Outer layout slot: fixed 180dp x 108dp. Bounds NEVER shift!
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(180.dp)
            .height(108.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (onNavigateUp != null) {
                        onNavigateUp.invoke()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner card box: 170dp x 98dp with GPU hardware outline clipping
        Box(
            modifier = Modifier
                .width(170.dp)
                .height(98.dp)
                .graphicsLayer {
                    this.scaleX = cardScale.value
                    this.scaleY = cardScale.value
                    this.shape = channelShape
                    this.clip = true
                }
                .background(if (isFocused) Color.White else Color(0x60181B26))
                .then(
                    if (isFocused) Modifier.border(1.5.dp, Color.White, channelShape)
                    else Modifier
                )
                .padding(8.dp)
        ) {
            Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live broadcast indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) Color(0x22000000) else Color(0x33FFFFFF))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isFocused) Color.Black else Color.White)
                    )
                    Text(
                        text = "DIRECT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isFocused) Color.Black else Color.White
                    )
                }

                if (item.qualityBadge.isNotBlank()) {
                    Text(
                        text = item.qualityBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isFocused) Color(0xFF444444) else AbyssTextSecondary
                    )
                }
            }

            // Channel Logo Centered
            if (item.posterUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AbyssMediaArtwork(
                        imageUrl = item.posterUrl,
                        targetWidth = 160,
                        targetHeight = 88,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                    )
                }
            }

            // Channel Name
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = if (isFocused) Color.Black else AbyssTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
}

@Composable
fun SeeMoreCard(
    isChannel: Boolean = false,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val cardScale = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "seeMoreScale"
    )

    val containerWidth = if (isChannel) 180.dp else 148.dp
    val containerHeight = if (isChannel) 108.dp else 240.dp
    val cardWidth = if (isChannel) 170.dp else 140.dp
    val cardHeight = if (isChannel) 98.dp else 200.dp

    Box(
        modifier = modifier
            .width(containerWidth)
            .height(containerHeight)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    if (onNavigateUp != null) {
                        onNavigateUp.invoke()
                        true
                    } else false
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    this.scaleX = cardScale.value
                    this.scaleY = cardScale.value
                }
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFocused) Color.White else Color(0x331C2233))
                .then(
                    if (isFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isFocused) Color(0x22000000) else Color(0x22FFFFFF))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = AbyssIconType.ARROW_RIGHT,
                    color = if (isFocused) Color.Black else Color.White,
                    size = 16.dp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.see_all_caps),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = if (isFocused) Color.Black else Color.White
            )
            if (!isChannel) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.browse_all),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = if (isFocused) Color(0xFF444444) else AbyssTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
}
