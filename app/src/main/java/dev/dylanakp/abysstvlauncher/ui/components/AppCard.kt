package dev.dylanakp.abysstvlauncher.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.compose.AsyncImage
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderFocus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import android.util.LruCache
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val appBannerBitmapCache = LruCache<String, ImageBitmap>(64)
private val appIconBitmapCache = LruCache<String, ImageBitmap>(64)

fun prewarmAppBitmaps(apps: List<TvAppInfo>) {
    for (app in apps) {
        if (app.banner != null && appBannerBitmapCache.get(app.packageName) == null) {
            app.banner.toBitmapSafe(340, 196)?.asImageBitmap()?.let {
                appBannerBitmapCache.put(app.packageName, it)
            }
        } else if (app.icon != null && appIconBitmapCache.get(app.packageName) == null) {
            app.icon.toBitmapSafe(108, 108)?.asImageBitmap()?.let {
                appIconBitmapCache.put(app.packageName, it)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: TvAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onFocus: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressConsumed by remember { mutableStateOf(false) }
    val cardShape = remember { RoundedCornerShape(12.dp) }

    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(180.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus?.invoke()
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
                        onNavigateUp?.invoke() ?: false
                    }
                    isSelectKey && native.action == KeyEvent.ACTION_DOWN -> {
                        if (native.repeatCount == 0) {
                            longPressConsumed = false
                            false
                        } else {
                            if (!longPressConsumed) {
                                longPressConsumed = true
                                onLongClick()
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
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 16:9 Glass Banner Box: 170dp x 98dp. Scales to 1.05x (178.5dp <= 180dp) inside the 180dp slot!
        Box(
            modifier = Modifier
                .width(170.dp)
                .height(98.dp)
                .graphicsLayer {
                    val s = if (isFocused) 1.05f else 1.0f
                    this.scaleX = s
                    this.scaleY = s
                    this.shape = cardShape
                    this.clip = true
                }
                .background(if (isFocused) Color(0x38FFFFFF) else AbyssSurfaceGlass)
                .border(1.5.dp, if (isFocused) Color.White else Color.Transparent, cardShape),
            contentAlignment = Alignment.Center
        ) {
            val isYouTubeVariant = app.packageName == "io.gh.reisxd.tizentube.cobalt" ||
                app.packageName == "app.morphe.android.youtube" ||
                app.packageName == "com.google.android.youtube.tv"

            val isRemoteLocator = app.packageName == "com.nvidia.remotelocator"

            val isWideBanner = app.isLeanback && app.banner != null && (
                app.banner.intrinsicWidth <= 0 || app.banner.intrinsicWidth >= app.banner.intrinsicHeight * 1.2f
            )

            val bannerBmp by produceState<ImageBitmap?>(
                initialValue = if (isWideBanner) appBannerBitmapCache.get(app.packageName) else null,
                key1 = app.packageName,
                key2 = isWideBanner
            ) {
                if (!isWideBanner) {
                    value = null
                    return@produceState
                }
                val cached = appBannerBitmapCache.get(app.packageName)
                if (cached != null) {
                    value = cached
                    return@produceState
                }
                val banner = app.banner ?: return@produceState
                value = withContext(Dispatchers.Default) {
                    banner.toBitmapSafe(340, 196)?.asImageBitmap()?.also {
                        appBannerBitmapCache.put(app.packageName, it)
                    }
                }
            }
            val iconBmp by produceState<ImageBitmap?>(
                initialValue = if (app.icon != null && !isWideBanner) appIconBitmapCache.get(app.packageName) else null,
                key1 = app.packageName,
                key2 = bannerBmp != null
            ) {
                if (app.icon == null || bannerBmp != null) {
                    value = null
                    return@produceState
                }
                val cached = appIconBitmapCache.get(app.packageName)
                if (cached != null) {
                    value = cached
                    return@produceState
                }
                val icon = app.icon
                value = withContext(Dispatchers.Default) {
                    icon.toBitmapSafe(108, 108)?.asImageBitmap()?.also {
                        appIconBitmapCache.put(app.packageName, it)
                    }
                }
            }

            if (isYouTubeVariant) {
                Image(
                    painter = painterResource(id = dev.dylanakp.abysstvlauncher.R.drawable.banner_youtube),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isRemoteLocator) {
                Image(
                    painter = painterResource(id = dev.dylanakp.abysstvlauncher.R.drawable.banner_remotelocator),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bannerBmp != null) {
                bannerBmp?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (iconBmp != null || app.icon != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val resolvedIcon = iconBmp
                        if (resolvedIcon != null) {
                            Image(
                                bitmap = resolvedIcon,
                                contentDescription = app.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            val context = LocalContext.current
                            val iconRequest = remember(app.icon) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(app.icon)
                                    .size(108, 108)
                                    .precision(coil.size.Precision.INEXACT)
                                    .allowHardware(true)
                                    .build()
                            }
                            val imageLoader = remember(context) { AbyssImagePipeline.getLoader(context) }
                            AsyncImage(
                                model = iconRequest,
                                imageLoader = imageLoader,
                                contentDescription = app.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (isFocused) Color.White else AbyssTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun Drawable.toBitmapSafe(maxWidth: Int, maxHeight: Int): Bitmap? {
    return try {
        if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
            val bmp = bitmap
            if (bmp.config != Bitmap.Config.HARDWARE && bmp.width <= maxWidth && bmp.height <= maxHeight) {
                return bmp
            }
            val w = bmp.width.coerceAtMost(maxWidth).coerceAtLeast(1)
            val h = bmp.height.coerceAtMost(maxHeight).coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bmp.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        }
        val rawW = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
        val rawH = intrinsicHeight.takeIf { it > 0 } ?: maxHeight
        val width = rawW.coerceAtMost(maxWidth).coerceAtLeast(1)
        val height = rawH.coerceAtMost(maxHeight).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}
