package dev.dylanakp.abysstvlauncher.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import java.util.concurrent.ConcurrentHashMap

private val appBannerBitmapCache = ConcurrentHashMap<String, ImageBitmap>()
private val appIconBitmapCache = ConcurrentHashMap<String, ImageBitmap>()

fun prewarmAppBitmaps(apps: List<TvAppInfo>) {
    for (app in apps) {
        if (app.banner != null && !appBannerBitmapCache.containsKey(app.packageName)) {
            app.banner.toBitmapSafe(340, 196)?.asImageBitmap()?.let {
                appBannerBitmapCache[app.packageName] = it
            }
        } else if (app.icon != null && !appIconBitmapCache.containsKey(app.packageName)) {
            app.icon.toBitmapSafe(108, 108)?.asImageBitmap()?.let {
                appIconBitmapCache[app.packageName] = it
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
    onNavigateUp: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressConsumed by remember { mutableStateOf(false) }

    val cardScale = animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "appCardScale"
    )

    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(180.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) {
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
                    this.scaleX = cardScale.value
                    this.scaleY = cardScale.value
                }
                .clip(RoundedCornerShape(10.dp))
                .background(AbyssSurfaceGlass)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else AbyssBorderHairline,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val isYouTubeVariant = app.packageName == "io.gh.reisxd.tizentube.cobalt" ||
                app.packageName == "app.morphe.android.youtube" ||
                app.packageName == "com.google.android.youtube.tv"

            val isRemoteLocator = app.packageName == "com.nvidia.remotelocator"

            val bannerBmp = remember(app.packageName, app.banner) {
                if (app.banner == null) null
                else appBannerBitmapCache.getOrPut(app.packageName) {
                    app.banner.toBitmapSafe(340, 196)?.asImageBitmap() ?: return@remember null
                }
            }
            val iconBmp = remember(app.packageName, app.icon) {
                if (app.icon == null || bannerBmp != null) null
                else appIconBitmapCache.getOrPut(app.packageName) {
                    app.icon.toBitmapSafe(108, 108)?.asImageBitmap() ?: return@remember null
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
                Image(
                    bitmap = bannerBmp,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (iconBmp != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = iconBmp,
                            contentDescription = app.label,
                            modifier = Modifier.fillMaxSize()
                        )
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
        val rawW = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
        val rawH = intrinsicHeight.takeIf { it > 0 } ?: maxHeight
        val width = rawW.coerceAtMost(maxWidth)
        val height = rawH.coerceAtMost(maxHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}
